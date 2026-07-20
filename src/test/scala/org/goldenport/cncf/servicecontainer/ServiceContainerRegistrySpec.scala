package org.goldenport.cncf.servicecontainer

import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the runtime-owned managed service registry.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServiceContainerRegistrySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed service-container registry" should {
    "register one exact definition idempotently while refusing incompatible reuse" in {
      Given("one owner/service key and two different admitted image definitions")
      val registry = ServiceContainerRegistry.inMemory()
      val original = _definition("textus-ai", "ollama", "ollama/ollama:latest")
      val incompatible = _definition("textus-ai", "ollama", "ollama/ollama:previous")

      When("the exact definition and then an incompatible definition are registered")
      val first = registry.registerC(original)
      val repeated = registry.registerC(original)
      val rejected = registry.registerC(incompatible)

      Then("the exact registration converges and incompatible ownership state is a conflict")
      first.toOption shouldBe repeated.toOption
      registry.entries.size shouldBe 1
      rejected.isFaillure shouldBe true
      _diagnostic_key(rejected) shouldBe Some("conflict")
    }

    "keep identical logical service names isolated by explicit owner" in {
      Given("generated component owners that request the same logical service")
      val owners = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(20)).suchThat(_.nonEmpty)
      val property = Prop.forAll(Gen.listOfN(12, owners)) { values =>
        val registry = ServiceContainerRegistry.inMemory()
        val definitions = values.distinct.map(x => _definition(x, "database", "example/database:1")).toVector
        definitions.foreach(registry.registerC)
        registry.entries.map(_.key.print) == definitions.map(_.registryKey.print).sorted
      }

      When("the registry is checked across generated owner sets")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("owner identity remains part of the key and enumeration is deterministic")
      checked.passed shouldBe true
    }

    "apply revision-checked status updates and require an endpoint for Ready" in {
      Given("one declared runtime-owned service")
      val registry = ServiceContainerRegistry.inMemory()
      val definition = _definition("textus-ai", "ollama", "ollama/ollama:latest")
      val registered = registry.registerC(definition).toOption.get
      val endpoint = ServiceContainerEndpoint.parseC("http://127.0.0.1:11434").toOption.get

      When("the service becomes Ready and stale or incomplete updates follow")
      val ready = registry.updateC(registered.key, registered.revision, ServiceContainerStatus.Ready, Some(endpoint))
      val stale = registry.updateC(registered.key, registered.revision, ServiceContainerStatus.Stopped, None)
      val missingendpoint = ready.toOption.flatMap { entry =>
        registry.updateC(entry.key, entry.revision, ServiceContainerStatus.Ready, None).toOption
      }

      Then("only the revision-matched endpoint-bearing Ready state is retained")
      ready.toOption.map(_.revision) shouldBe Some(1L)
      registry.get(registered.key).flatMap(_.endpoint).map(_.safeAuthority) shouldBe Some("http://127.0.0.1:11434")
      stale.isFaillure shouldBe true
      missingendpoint shouldBe None
    }

    "remove only the expected registry revision" in {
      Given("a declared service whose registry revision has advanced")
      val registry = ServiceContainerRegistry.inMemory()
      val definition = _definition("textus-sie", "fuseki", "example/fuseki:1")
      val registered = registry.registerC(definition).toOption.get
      val started = registry.updateC(registered.key, 0L, ServiceContainerStatus.Starting, None).toOption.get

      When("stale and current revisions request removal")
      val stale = registry.removeC(started.key, 0L)
      val removed = registry.removeC(started.key, started.revision)

      Then("only the current revision releases the registry key")
      stale.isFaillure shouldBe true
      removed.toOption.map(_.revision) shouldBe Some(1L)
      registry.get(started.key) shouldBe None
    }
  }

  private def _diagnostic_key[A](consequence: Consequence[A]): Option[String] =
    consequence match {
      case Consequence.Failure(conclusion) => Some(ConclusionDiagnostics.classify(conclusion).diagnosticKey)
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
    val port = ServiceContainerPort.createC(portname, 8080).toOption.get
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
