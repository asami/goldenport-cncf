package org.goldenport.cncf.servicecontainer

import scala.collection.mutable

import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the constrained Docker command projection.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class DockerServiceContainerGatewaySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Docker managed service-container gateway" should {
    "project only framework-owned labels ports and admitted named-volume targets" in {
      Given("a runtime-owned service and a scripted Docker boundary")
      val definition = _definition()
      val runner = new ScriptedRunner(definition)
      val gateway = DockerServiceContainerGateway.create(runner)

      When("the typed service definition is created")
      val result = gateway.createC(definition)

      Then("the Docker command contains no shell host mount environment or arbitrary argument surface")
      result.toOption.map(_.registryKey) shouldBe Some(definition.registryKey)
      val create = runner.arguments.head
      create.head shouldBe "create"
      create should contain("--publish")
      create should contain("127.0.0.1::11434")
      create should contain("--mount")
      create should contain("type=volume,source=ollama-models,target=/root/.ollama")
      create.sliding(2).collect { case Vector("--label", value) => value }.toVector should contain allElementsOf
        ServiceContainerOwnershipLabels.from(definition).values.toVector.map { case (key, value) => s"$key=$value" }
      create should not contain "--env"
      create should not contain "--volume"
      create should not contain "sh"
      create.last shouldBe definition.image.print
    }

    "inspect only containers carrying the complete CNCF ownership key" in {
      Given("a registry key and one scripted matching Docker container")
      val definition = _definition()
      val runner = new ScriptedRunner(definition)
      val gateway = DockerServiceContainerGateway.create(runner)

      When("the logical service is inspected")
      val result = gateway.inspectC(definition.registryKey)

      Then("the provider query filters every framework ownership label before decoding details")
      result.toOption.flatten.map(_.registryKey) shouldBe Some(definition.registryKey)
      runner.arguments.head shouldBe Vector(
        "ps",
        "--all",
        "--filter", s"label=${ServiceContainerOwnershipLabels.MANAGED}=true",
        "--filter", s"label=${ServiceContainerOwnershipLabels.OWNER_KIND}=component-runtime",
        "--filter", s"label=${ServiceContainerOwnershipLabels.OWNER_ID}=textus-ai",
        "--filter", s"label=${ServiceContainerOwnershipLabels.SERVICE_ID}=ollama",
        "--format", "{{.ID}}"
      )
    }

    "report a deterministic ownership conflict when a foreign container occupies the derived name" in {
      Given("a runtime-owned service and Docker's name-conflict response")
      val definition = _definition()
      val conflict = DockerServiceContainerCommandResult(
        1,
        "",
        "Conflict. The container name is already in use by another container."
      )
      val gateway = DockerServiceContainerGateway.create(new ScriptedRunner(definition, Some(conflict)))

      When("the gateway attempts typed creation without adopting the foreign container")
      val result = gateway.createC(definition)

      Then("the lifecycle retains the structured ownership-conflict diagnostic")
      result match {
        case Consequence.Failure(conclusion) =>
          ConclusionDiagnostics.classify(conclusion).policy shouldBe Some("service-container.ownership-conflict")
        case _ => fail("A foreign deterministic container name must not be adopted.")
      }
    }
  }

  private def _definition(): ServiceContainerDefinition.RuntimeOwned = {
    val result = for {
      ownerid <- ServiceContainerOwnerId.parseC("textus-ai")
      serviceid <- ServiceContainerId.parseC("ollama")
      image <- ServiceContainerImage.parseC("ollama/ollama:latest")
      portname <- ServiceContainerPortName.parseC("api")
      port <- ServiceContainerPort.createC(portname, 11434)
      volumename <- ServiceContainerVolumeName.parseC("ollama-models")
      target <- ServiceContainerMountPath.parseC("/root/.ollama")
      volume <- ServiceContainerVolume.createC(volumename, target)
      persistence <- ServiceContainerPersistence.namedVolumesC(Vector(volume))
      readiness <- ServiceContainerReadinessPolicy.createC(
        ServiceContainerReadinessProbe.tcp(portname),
        30000L,
        250L
      )
      definition <- ServiceContainerDefinition.runtimeOwnedC(
        serviceid,
        ServiceContainerOwner(ServiceContainerOwnerKind.ComponentRuntime, ownerid),
        image,
        Vector(port),
        readiness,
        persistence = persistence
      )
    } yield definition
    result.toOption.getOrElse(fail("The executable specification fixture must be valid."))
  }

  private final class ScriptedRunner(
    definition: ServiceContainerDefinition.RuntimeOwned,
    createfailure: Option[DockerServiceContainerCommandResult] = None
  ) extends DockerServiceContainerCommandRunner {
    val arguments = mutable.ArrayBuffer.empty[Vector[String]]

    def runC(values: Vector[String]): Consequence[DockerServiceContainerCommandResult] = {
      arguments += values
      values.headOption match {
        case Some("ps") => Consequence.success(DockerServiceContainerCommandResult(0, "container-1", ""))
        case Some("create") => Consequence.success(
          createfailure.getOrElse(DockerServiceContainerCommandResult(0, "container-1", ""))
        )
        case Some("inspect") => Consequence.success(DockerServiceContainerCommandResult(0, _inspection_json, ""))
        case _ => Consequence.success(DockerServiceContainerCommandResult(1, "", "unsupported scripted command"))
      }
    }

    private def _inspection_json: String = {
      val labels = ServiceContainerOwnershipLabels.from(definition).values ++ Map(
        "org.goldenport.cncf.port.api" -> "11434"
      )
      val labeljson = labels.toVector.sortBy(_._1).map { case (key, value) =>
        s"\"$key\":\"$value\""
      }.mkString(",")
      s"""[{"Id":"container-1","Config":{"Image":"${definition.image.print}","Labels":{$labeljson}},"State":{"Status":"created"},"NetworkSettings":{"Ports":{"11434/tcp":[{"HostPort":"49152"}]}}}]"""
    }
  }
}
