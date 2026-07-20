package org.goldenport.cncf.servicecontainer

import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for constrained managed service-container gateway
 * ownership, compatibility, and deterministic fake transition behavior.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServiceContainerGatewaySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed service-container gateway" should {
    "derive stable runtime-owned labels from the admitted definition" in {
      Given("generated safe owner names and one admitted service contract")
      val owners = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(20)).suchThat(_.nonEmpty)
      val property = Prop.forAll(owners) { owner =>
        val definition = _definition(owner, "database", "example/database:1")
        val first = ServiceContainerOwnershipLabels.from(definition)
        val second = ServiceContainerOwnershipLabels.from(definition)
        first == second &&
          first.matches(definition) &&
          first.values.keySet == Set(
            ServiceContainerOwnershipLabels.MANAGED,
            ServiceContainerOwnershipLabels.OWNER_KIND,
            ServiceContainerOwnershipLabels.OWNER_ID,
            ServiceContainerOwnershipLabels.SERVICE_ID,
            ServiceContainerOwnershipLabels.CONTRACT_DIGEST
          )
      }

      When("ownership labels are generated repeatedly")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

      Then("the runtime, not component input, produces one deterministic safe label set")
      checked.passed shouldBe true
    }

    "refuse reuse without matching ownership and definition evidence" in {
      Given("one admitted definition and observed same-name containers with wrong ownership or image")
      val definition = _definition("textus-ai", "ollama", "ollama/ollama:latest")
      val labels = ServiceContainerOwnershipLabels.from(definition)
      val wrongowner = ServiceContainerOwnershipLabels.observed(
        labels.values
          .updated(ServiceContainerOwnershipLabels.OWNER_ID, "another-component")
          .updated("provider.secret", "must-not-survive")
      )
      val previous = _definition("textus-ai", "ollama", "ollama/ollama:previous")
      val instanceid = ServiceContainerInstanceId.parseC("container-1").toOption.get
      val ownershipinspection = ServiceContainerInspection(
        instanceid,
        definition.registryKey,
        definition.image,
        definition.ports,
        wrongowner,
        ServiceContainerStatus.Ready,
        None
      )
      val imageinspection = ownershipinspection.copy(
        image = previous.image,
        ownershipLabels = labels
      )
      val compatibleinspection = ownershipinspection.copy(
        image = definition.image,
        ownershipLabels = labels
      )

      When("the compatibility boundary evaluates reuse")
      val ownershipresult = ServiceContainerCompatibility.requireCompatibleC(definition, ownershipinspection)
      val imageresult = ServiceContainerCompatibility.requireCompatibleC(definition, imageinspection)
      val compatibleresult = ServiceContainerCompatibility.requireCompatibleC(definition, compatibleinspection)

      Then("ownership and definition conflicts remain distinct structured failures")
      _policy(ownershipresult) shouldBe Some("service-container.ownership-conflict")
      _policy(imageresult) shouldBe Some("service-container.incompatible-existing-service")
      compatibleresult.toOption shouldBe Some(compatibleinspection)
      wrongowner.values.contains("provider.secret") shouldBe false
    }

    "execute typed lifecycle transitions deterministically without arbitrary provider arguments" in {
      Given("a fake gateway with one readiness endpoint")
      val definition = _definition("textus-sie", "fuseki", "example/fuseki:1")
      val endpoint = ServiceContainerEndpoint.parseC("http://127.0.0.1:3030").toOption.get
      val gateway = FakeServiceContainerGateway.create(Map(definition.registryKey -> endpoint))

      When("the admitted service is created, started, checked, restarted, stopped, and removed")
      val absent = gateway.inspectC(definition.registryKey)
      val created = gateway.createC(definition).toOption.get
      val started = gateway.startC(created.instanceId)
      val ready = gateway.checkReadinessC(created.instanceId, definition.readiness)
      val restarted = gateway.restartC(created.instanceId)
      val stopped = gateway.stopC(created.instanceId)
      val removed = gateway.removeC(created.instanceId)

      Then("the fake exposes the same constrained transition order and ends empty")
      absent.toOption shouldBe Some(None)
      started.toOption.map(_.status) shouldBe Some(ServiceContainerStatus.Starting)
      ready.toOption.map(_.endpoint.safeAuthority) shouldBe Some("http://127.0.0.1:3030")
      restarted.toOption.map(_.status) shouldBe Some(ServiceContainerStatus.Starting)
      stopped.toOption.map(_.status) shouldBe Some(ServiceContainerStatus.Stopped)
      removed.toOption.map(_.instanceId) shouldBe Some(created.instanceId)
      gateway.inspections shouldBe empty
      gateway.transitions.map(_._1) shouldBe Vector(
        ServiceContainerTransition.Inspect,
        ServiceContainerTransition.Create,
        ServiceContainerTransition.Start,
        ServiceContainerTransition.CheckReadiness,
        ServiceContainerTransition.Restart,
        ServiceContainerTransition.Stop,
        ServiceContainerTransition.Remove
      )
    }

    "preserve deterministic unhealthy and missing-instance failures" in {
      Given("a fake gateway without a readiness endpoint and one admitted service")
      val definition = _definition("textus-ai", "ollama", "ollama/ollama:latest")
      val gateway = FakeServiceContainerGateway.create()
      val created = gateway.createC(definition).toOption.get
      val missing = ServiceContainerInstanceId.parseC("missing-container").toOption.get

      When("readiness and a transition for an unknown instance are requested")
      val unhealthy = gateway.checkReadinessC(created.instanceId, definition.readiness)
      val notfound = gateway.stopC(missing)

      Then("both failures remain normal Consequence failures and no state is invented")
      _policy(unhealthy) shouldBe Some("service-container.unhealthy")
      notfound.isFaillure shouldBe true
      gateway.inspections.map(_.status) shouldBe Vector(ServiceContainerStatus.Created)
    }
  }

  private def _policy[A](consequence: Consequence[A]): Option[String] =
    consequence match {
      case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
      case _ => None
    }

  private def _definition(
    owner: String,
    service: String,
    image: String
  ): ServiceContainerDefinition.RuntimeOwned = {
    val ownerid = ServiceContainerOwnerId.parseC(owner).toOption.get
    val serviceid = ServiceContainerId.parseC(service).toOption.get
    val imageid = ServiceContainerImage.parseC(image).toOption.get
    val portname = ServiceContainerPortName.parseC("api").toOption.get
    val port = ServiceContainerPort.createC(portname, 3030).toOption.get
    val readiness = ServiceContainerReadinessPolicy.createC(
      ServiceContainerReadinessProbe.tcp(portname),
      30000L,
      250L
    ).toOption.get
    ServiceContainerDefinition.runtimeOwnedC(
      serviceid,
      ServiceContainerOwner(ServiceContainerOwnerKind.ComponentRuntime, ownerid),
      imageid,
      Vector(port),
      readiness
    ).toOption.get
  }
}
