package org.goldenport.cncf.operationalobservation

import java.time.Instant
import io.circe.Json
import org.goldenport.cncf.servicecontainer.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 26, 2026
 */
final class DockerOperationalObservationProviderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _now = Instant.parse("2026-10-24T00:00:00Z")
  private val _source = ObservationSource.Docker("docker", "https://engine.test",
    DockerObservationSelector.Name("postgresql-ops"), 30000)
  private def _item(state: String): Json = Json.obj(
    "Names" -> Json.arr(Json.fromString("/postgresql-ops")), "State" -> Json.fromString(state))
  private def _decode(json: Json) = DockerOperationalObservationProvider.decode(_source, json, _now, _ => true)

  "Read-only Docker deployment observations" should {
    "retain all finite live states and prove absence only from a completed exact empty lookup" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R3,R5; exact provider responses")
      val property = Prop.forAll(Gen.oneOf(DockerOperationalObservationProvider.states.toVector)) { state =>
        _decode(Json.arr(_item(state))) == Right(AxisObservation.Observed(state, "docker", _now, _now.plusSeconds(30)))
      }
      When("the finite live states and an empty exact result are decoded")
      Then("running, transitional and stopped facts remain deployment facts only")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property).passed shouldBe true
      _decode(Json.arr()) shouldBe Right(AxisObservation.Observed("absent", "docker", _now, _now.plusSeconds(30)))
      _decode(Json.arr(_item("unknown"))) shouldBe Left(ObservationReason.Unsupported)
      _decode(Json.obj()) shouldBe Left(ObservationReason.InvalidSource)
      _decode(Json.arr(_item("running"), _item("running"))) shouldBe Left(ObservationReason.AmbiguousTarget)
      _decode(Json.arr(Json.obj("Names" -> Json.arr(Json.fromString("/foreign"))))) shouldBe Left(ObservationReason.InvalidSource)
    }

    "require both exact Compose labels without changing lifecycle state" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R5; an external Compose selector")
      val source = _source.copy(selector = DockerObservationSelector.Compose("ops", "postgresql"))
      val item = Json.obj("Labels" -> Json.obj("com.docker.compose.project" -> Json.fromString("ops"),
        "com.docker.compose.service" -> Json.fromString("postgresql")), "State" -> Json.fromString("paused"))
      When("the exact and a partial-label response are read")
      Then("only the full selector is accepted and the URI names the read-only Engine resource")
      DockerOperationalObservationProvider.decode(source, Json.arr(item), _now, _ => true).isRight shouldBe true
      DockerOperationalObservationProvider.decode(source, Json.arr(_item("running")), _now, _ => true) shouldBe
        Left(ObservationReason.InvalidSource)
      DockerOperationalObservationProvider.url(source) should startWith("https://engine.test/containers/json?all=1&filters=")
    }

    "reuse existing runtime ownership and compatibility without gateway or registry mutation" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R5; an existing registered owner definition")
      val owner = ServiceContainerOwner(ServiceContainerOwnerKind.ComponentRuntime, ServiceContainerOwnerId.parseC("owner").TAKE)
      val port = ServiceContainerPort.createC(ServiceContainerPortName.parseC("api").TAKE, 8080).TAKE
      val readiness = ServiceContainerReadinessPolicy.createC(ServiceContainerReadinessProbe.tcp(port.name), 30000L, 250L).TAKE
      val definition = ServiceContainerDefinition.runtimeOwnedC(ServiceContainerId.parseC("database").TAKE, owner,
        ServiceContainerImage.parseC("example/database:1").TAKE, Vector(port), readiness).TAKE
      val registry = ServiceContainerRegistry.inMemory()
      registry.registerC(definition).TAKE
      val before = registry.entries
      val gateway = FakeServiceContainerGateway.create()
      val runtime = ServiceContainerRuntime.create(registry, gateway)
      val labels = ServiceContainerOwnershipLabels.from(definition).values + ("org.goldenport.cncf.port.api" -> "8080")
      val item = Json.obj("Id" -> Json.fromString("fixture-container"), "Image" -> Json.fromString("example/database:1"),
        "State" -> Json.fromString("running"), "Labels" -> Json.obj(labels.toVector.map { case (k, v) => k -> Json.fromString(v) }*),
        "Ports" -> Json.arr(Json.obj("PrivatePort" -> Json.fromInt(8080), "Type" -> Json.fromString("tcp"))))
      val source = _source.copy(selector = DockerObservationSelector.RuntimeOwned(definition.registryKey))
      When("live JSON is checked against the existing definition, including an incompatible image")
      val accepted = DockerOperationalObservationProvider.decode(source, Json.arr(item), _now,
        runtime.validateDeploymentObservationC(definition.registryKey, _).isSuccess)
      val denied = item.mapObject(_.add("Image", Json.fromString("foreign/database:1")))
      Then("compatibility is required and no resolve, create, start, stop or cleanup is invoked")
      accepted.isRight shouldBe true
      runtime.validateDeploymentObservationC(definition.registryKey, denied).isFaillure shouldBe true
      registry.entries shouldBe before
      gateway.transitions shouldBe empty
    }

    "keep provider acquisition failures distinct from absence" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R3,R5; a failing admitted fixture transport")
      val effects = new OperationalObservationFixtures.Effects
      effects.failure = true
      When("the selected external deployment is read")
      val value = DockerOperationalObservationProvider.readC(_source,
        ObservationRead(OperationalObservationFixtures.context(), 1000, 65536), effects)
      Then("failure is unavailable, never absent or healthy")
      value shouldBe AxisObservation.Unavailable(ObservationReason.SourceUnavailable, Some("docker"))
      effects.paths.size shouldBe 1
    }
  }
}
