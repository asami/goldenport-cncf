package org.goldenport.cncf.http

import java.nio.file.{Files, Paths}
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServerPortPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ServerPortPolicy" should {
    "use the CAR default and allocate an additional instance above 38000 when occupied" in {
      Given("a component CAR whose application default is already in use")
      val subsystem = _subsystem(Map("textus.component.file" -> "target/example.car"))
      val availability = _availability(Set(38002))
      val assignments = _assignment(18000)

      When("the default server port is resolved")
      val port = _without_port_properties(ServerPortPolicy.resolve(subsystem, availability, Some(assignments)).TAKE)

      Then("the first available additional-instance port is selected")
      ServerPortPolicy.artifactKind(subsystem) shouldBe ServerPortPolicy.ArtifactKind.Car
      port shouldBe 38002
    }

    "use the SAR default when it is available" in {
      Given("a subsystem SAR with its assigned default available")
      val subsystem = _subsystem(Map("textus.subsystem.file" -> "target/example.sar"))
      val availability = _availability(Set(28000))
      val assignments = _assignment(28000)

      When("the default server port is resolved")
      val port = _without_port_properties(ServerPortPolicy.resolve(subsystem, availability, Some(assignments)).TAKE)

      Then("the application default is selected")
      ServerPortPolicy.artifactKind(subsystem) shouldBe ServerPortPolicy.ArtifactKind.Sar
      port shouldBe 28000
    }

    "use an explicit configured port without automatic reassignment" in {
      Given("a CAR with an explicit server port")
      val subsystem = _subsystem(
        Map(
          "textus.component.file" -> "target/example.car",
          "textus.server.port" -> "17777"
        )
      )
      val unavailable = _availability(Set.empty)

      When("the server port is resolved")
      val port = _without_port_properties(ServerPortPolicy.resolve(subsystem, unavailable, Some(_assignment(18000))).TAKE)

      Then("the operator-selected port is retained")
      port shouldBe 17777
    }

    "reject an explicit port outside the TCP port range" in {
      Given("an invalid explicit server port")
      val subsystem = _subsystem(Map("textus.server.port" -> "70000"))

      When("the server port is resolved")
      val result = _without_port_properties(ServerPortPolicy.resolve(subsystem))

      Then("resolution fails deterministically")
      result.isFaillure shouldBe true
    }

    "infer archive kind from the loaded descriptor when activation config is absent" in {
      Given("CAR and SAR descriptors")
      val car = _subsystem().withDescriptor(GenericSubsystemDescriptor(Paths.get("example.car"), "example-car"))
      val sar = _subsystem().withDescriptor(GenericSubsystemDescriptor(Paths.get("example.sar"), "example-sar"))

      Then("their archive kinds are recognized")
      ServerPortPolicy.artifactKind(car) shouldBe ServerPortPolicy.ArtifactKind.Car
      ServerPortPolicy.artifactKind(sar) shouldBe ServerPortPolicy.ArtifactKind.Sar
    }

    "retain port 8080 for a runtime with no CAR or SAR activation" in {
      Given("a bare CNCF runtime")
      val subsystem = _subsystem()

      When("the default server port is resolved")
      val port = _without_port_properties(ServerPortPolicy.resolve(subsystem, _availability(Set.empty)).TAKE)

      Then("the legacy runtime default remains available")
      port shouldBe 8080
    }

    "persist a consecutive default port in each archive range" in {
      Given("an empty machine-local assignment registry")
      val directory = Files.createTempDirectory("cncf-server-port-assignments")
      val store = new ServerPortPolicy.FileAssignmentStore(directory.resolve("assignments.json"))
      val first = ServerPortPolicy.ArtifactIdentity(ServerPortPolicy.ArtifactKind.Car, "first-car")
      val second = ServerPortPolicy.ArtifactIdentity(ServerPortPolicy.ArtifactKind.Sar, "first-sar")

      When("a CAR, a SAR, and another instance of the first CAR request defaults")
      val firstport = store.defaultPort(first).TAKE
      val secondport = store.defaultPort(second).TAKE
      val firstagain = store.defaultPort(first).TAKE

      Then("each archive kind owns a stable application-number sequence")
      firstport shouldBe 18000
      secondport shouldBe 28000
      firstagain shouldBe 18000
    }

    "reject a declared default already assigned to another artifact" in {
      Given("a registry where one CAR owns declared default 18025")
      val directory = Files.createTempDirectory("cncf-server-port-conflict")
      val store = new ServerPortPolicy.FileAssignmentStore(directory.resolve("assignments.json"))
      val first = ServerPortPolicy.ArtifactIdentity(ServerPortPolicy.ArtifactKind.Car, "first-car")
      val second = ServerPortPolicy.ArtifactIdentity(ServerPortPolicy.ArtifactKind.Car, "second-car")
      store.defaultPort(first, Some(18025)).TAKE shouldBe 18025

      When("another CAR declares the same default")
      val result = store.defaultPort(second, Some(18025))

      Then("the duplicate application number is rejected")
      result.isFaillure shouldBe true
    }

    "publish the actual bound endpoint for launcher registration" in {
      Given("a runtime server that has selected an additional-instance port")
      Http4sHttpServer._clear_bound_base_url()
      try {
        When("the HTTP server reports that it has bound")
        Http4sHttpServer._publish_bound_base_url(38002)

        Then("the launcher handshake exposes the selected endpoint until server shutdown")
        sys.props.get(Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY) shouldBe Some("http://127.0.0.1:38002")

        When("the HTTP server shuts down")
        Http4sHttpServer._clear_bound_base_url()

        Then("the stale endpoint is removed")
        sys.props.get(Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY) shouldBe None
      } finally {
        Http4sHttpServer._clear_bound_base_url()
      }
    }
  }

  private def _subsystem(values: Map[String, String] = Map.empty): Subsystem =
    new Subsystem(
      name = "test",
      configuration = ResolvedConfiguration(
        Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
        ConfigurationTrace.empty
      )
    )

  private def _availability(available: Set[Int]): ServerPortPolicy.Availability =
    new ServerPortPolicy.Availability {
      def isAvailable(port: Int): Boolean = available.contains(port)
    }

  private def _assignment(expectedPort: Int): ServerPortPolicy.AssignmentStore =
    new ServerPortPolicy.AssignmentStore {
      def defaultPort(
        identity: ServerPortPolicy.ArtifactIdentity,
        requestedPort: Option[Int]
      ) =
        org.goldenport.Consequence.success(expectedPort)
    }

  private def _without_port_properties[A](body: => A): A = {
    val keys = Vector(Http4sHttpServer.PORT_PROPERTY_KEY, Http4sHttpServer.LEGACY_PORT_PROPERTY_KEY)
    val previous = keys.map(key => key -> sys.props.get(key))
    try {
      keys.foreach(sys.props.remove)
      body
    } finally {
      previous.foreach {
        case (key, Some(value)) => sys.props.update(key, value)
        case (key, None) => sys.props.remove(key)
      }
    }
  }
}
