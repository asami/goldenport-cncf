package org.goldenport.cncf.http

import com.comcast.ip4s.Host
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{
  Configuration,
  ConfigurationTrace,
  ConfigurationValue,
  ResolvedConfiguration
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 10, 2026
 * @version Aug. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServerEndpointPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ServerEndpointPolicy" should {
    "bind to loopback by default" in {
      Given("a runtime without an explicit endpoint")
      val subsystem = _subsystem(Map(Http4sHttpServer.PORT_PROPERTY_KEY -> "18001"))

      When("the endpoint is resolved")
      val endpoint = _without_endpoint_properties(ServerEndpointPolicy.resolve(subsystem, _availability).TAKE)

      Then("the ordinary runtime policy is loopback-only")
      endpoint.host.toString shouldBe "127.0.0.1"
      endpoint.port shouldBe 18001
    }

    "allow wildcard binding only when explicitly configured" in {
      Given("an operator-selected wildcard endpoint")
      val subsystem = _subsystem(
        Map(
          ServerEndpointPolicy.HOST_PROPERTY_KEY -> "0.0.0.0",
          Http4sHttpServer.PORT_PROPERTY_KEY -> "18002"
        )
      )

      When("the endpoint is resolved")
      val endpoint = _without_endpoint_properties(ServerEndpointPolicy.resolve(subsystem, _availability).TAKE)

      Then("the explicit exposure is retained")
      endpoint.host.toString shouldBe "0.0.0.0"
      endpoint.port shouldBe 18002
    }

    "prefer resolved endpoint configuration to JVM properties" in {
      Given("different resolved and JVM endpoints")
      val subsystem = _subsystem(
        Map(
          ServerEndpointPolicy.HOST_PROPERTY_KEY -> "127.0.0.2",
          Http4sHttpServer.PORT_PROPERTY_KEY -> "18003"
        )
      )

      When("the endpoint is resolved")
      val endpoint = _with_endpoint_properties("127.0.0.3", "18004") {
        ServerEndpointPolicy.resolve(subsystem, _availability).TAKE
      }

      Then("the resolved runtime configuration wins")
      endpoint.host.toString shouldBe "127.0.0.2"
      endpoint.port shouldBe 18003
    }

    "use a JVM host override when resolved configuration omits the host" in {
      Given("a JVM-only wildcard host and a resolved port")
      val subsystem = _subsystem(Map(Http4sHttpServer.PORT_PROPERTY_KEY -> "18005"))

      When("the endpoint is resolved")
      val endpoint = _with_endpoint_properties("0.0.0.0", "18006") {
        ServerEndpointPolicy.resolve(subsystem, _availability).TAKE
      }

      Then("the JVM host is used while the resolved port keeps precedence")
      endpoint.host.toString shouldBe "0.0.0.0"
      endpoint.port shouldBe 18005
    }

    "preserve the former Http4sHttpServer constructor call shapes" in {
      Given("a runtime HTTP execution engine")
      val engine = HttpExecutionEngine.Factory.engine()

      When("source clients use the former default, port, dispatcher, and combined constructors")
      val defaultserver = new Http4sHttpServer(engine)
      val portserver = new Http4sHttpServer(engine, 18007)
      val dispatcherserver = new Http4sHttpServer(engine, operationDispatcherOption = None)
      val combinedserver = new Http4sHttpServer(engine, 18008, None)

      Then("all former constructor shapes remain source-compatible")
      Vector(defaultserver, portserver, dispatcherserver, combinedserver) should have size 4
    }

    "reject an invalid configured host" in {
      Given("an invalid endpoint host")
      val subsystem = _subsystem(Map(ServerEndpointPolicy.HOST_PROPERTY_KEY -> "not a host"))

      When("the endpoint is resolved")
      val result = _without_endpoint_properties(ServerEndpointPolicy.resolve(subsystem, _availability))

      Then("startup fails closed")
      result.isFaillure shouldBe true
    }
  }

  private val _availability: Host => ServerPortPolicy.Availability = _ =>
    new ServerPortPolicy.Availability {
      def isAvailable(port: Int): Boolean = true
    }

  private def _subsystem(values: Map[String, String]): Subsystem =
    new Subsystem(
      name = "test",
      configuration = ResolvedConfiguration(
        Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
        ConfigurationTrace.empty
      )
    )

  private def _without_endpoint_properties[A](body: => A): A =
    _with_properties(None, None)(body)

  private def _with_endpoint_properties[A](host: String, port: String)(body: => A): A =
    _with_properties(Some(host), Some(port))(body)

  private def _with_properties[A](host: Option[String], port: Option[String])(body: => A): A = {
    val updates = Vector(
      ServerEndpointPolicy.HOST_PROPERTY_KEY -> host,
      Http4sHttpServer.PORT_PROPERTY_KEY -> port
    )
    val previous = updates.map { case (key, _) => key -> sys.props.get(key) }
    try {
      updates.foreach {
        case (key, Some(value)) => sys.props.update(key, value)
        case (key, None) => sys.props.remove(key)
      }
      body
    } finally
      previous.foreach {
        case (key, Some(value)) => sys.props.update(key, value)
        case (key, None) => sys.props.remove(key)
      }
  }
}
