package org.goldenport.cncf.http

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.{Host, Port}
import io.circe.parser.parse
import org.goldenport.{Consequence}
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.mcp.McpProtocolRevision
import org.goldenport.cncf.mcp.client.*
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.syntax.all.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable interoperability specification for the production MCP server and
 * Streamable HTTP client boundaries.
 *
 * @since   Jul. 21, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpStreamableHttpInteroperabilitySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "CNCF MCP Streamable HTTP interoperability" should {
    "initialize list and invoke one admitted Operation through a real loopback server" in {
      Given("the production MCP route and production JDK Streamable HTTP client on a real loopback socket")
      val subsystem = DefaultSubsystemFactory.default(Some("mcp-loopback-interoperability"))
      subsystem.findComponent(BuiltinComponentIdentity.ADMIN).foreach(_.withMcpReadyServices(Set("system")))
      val route = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(
        ExecutionContext.create(),
        enabled = true
      )

      When("the client initializes, accepts the 202 notification acknowledgement, lists, and calls a tool")
      val result = EmberServerBuilder
        .default[IO]
        .withHost(Host.fromString("127.0.0.1").getOrElse(fail("loopback host is invalid")))
        .withPort(Port.fromInt(0).getOrElse(fail("ephemeral loopback port is invalid")))
        .withHttpWebSocketApp(wsb => route.routes(wsb).orNotFound)
        .build
        .use { server =>
          IO.blocking {
            val endpoint = s"http://127.0.0.1:${server.address.getPort}/mcp"
            val clientresult = _client_round_trip_c(endpoint)
            val unsupported = _post(
              endpoint,
              """{"jsonrpc":"2.0","id":"unsupported","method":"initialize","params":{"protocolVersion":"2026-03-19"}}""",
              None
            )
            val requestshapednotification = _post(
              endpoint,
              """{"jsonrpc":"2.0","id":"bad-notification","method":"notifications/initialized"}""",
              Some(McpProtocolRevision.PREFERRED.print)
            )
            val unknownnotification = _post(
              endpoint,
              """{"jsonrpc":"2.0","method":"notifications/unknown"}""",
              Some(McpProtocolRevision.PREFERRED.print)
            )
            (clientresult, unsupported, requestshapednotification, unknownnotification)
          }
        }
        .unsafeRunSync()

      Then("the complete production lifecycle succeeds and negative messages remain bounded")
      val (catalog, invocation) = _success(result._1)
      catalog.tools.map(_.identity.print) shouldBe Vector("loopback/admin.system.ping")
      val pingtext = invocation.content.collectFirst {
        case McpClientContent.Text(value, _) => value
      }.getOrElse(fail("admin.system.ping did not return text content"))
      pingtext should include("runtime: goldenport-cncf")
      pingtext should include("subsystem: goldenport-cncf")
      result._2.statusCode() shouldBe 200
      result._2.body() should not include "2026-03-19"
      parse(result._2.body()).toOption
        .flatMap(_.hcursor.downField("error").get[Int]("code").toOption) shouldBe Some(-32602)
      result._3.statusCode() shouldBe 200
      parse(result._3.body()).toOption
        .flatMap(_.hcursor.downField("error").get[Int]("code").toOption) shouldBe Some(-32600)
      result._4.statusCode() shouldBe 400
      result._4.body() shouldBe empty
    }
  }

  private def _client_round_trip_c(
    endpoint: String
  )(using ExecutionContext): Consequence[(McpClientCatalog, McpClientResult)] = {
    val serversetid = _success(McpServerSetId.parseC("loopback-tools"))
    val serverid = _success(McpServerId.parseC("loopback"))
    val toolname = _success(McpToolName.parseC("admin.system.ping"))
    val transportconfig = _success(McpStreamableHttpServerSetConfig.createC(
      serversetid,
      Vector(_success(McpStreamableHttpServerConfig.createC(serverid, endpoint)))
    ))
    val provider = _success(McpStreamableHttpTransportProvider.createC(Vector(transportconfig)))
    val serverset = _success(McpClientServerSet.createC(
      serversetid,
      Vector(_success(McpClientServer.createC(serverid, Set(toolname))))
    ))
    val registry = _success(McpClientRuntimeRegistry.createC(Vector(serverset), provider.binding))
    try {
      for {
        service <- registry.resolve(serversetid)
        catalog <- service.catalog
        tool <- catalog.tool(McpToolIdentity(serverid, toolname)) match {
          case Some(value) => Consequence.success(value)
          case None => Consequence.operationNotFound("loopback MCP tool is unavailable")
        }
        arguments <- McpValue.objectC(Vector.empty)
        call <- McpClientCall.createC(tool.identity, arguments)
        result <- service.withInvocation(_.invoke(call))
      } yield catalog -> result
    } finally {
      registry.close()
    }
  }

  private def _post(
    endpoint: String,
    body: String,
    protocolrevision: Option[String]
  ): HttpResponse[String] = {
    val builder = HttpRequest.newBuilder(URI.create(endpoint))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
    protocolrevision.foreach(builder.header("MCP-Protocol-Version", _))
    HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }

  private def _success[A](value: Consequence[A]): A =
    value match {
      case Consequence.Success(result) => result
      case Consequence.Failure(conclusion) => fail(conclusion.display)
    }
}
