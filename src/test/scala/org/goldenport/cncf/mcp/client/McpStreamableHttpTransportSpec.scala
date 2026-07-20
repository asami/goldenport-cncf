package org.goldenport.cncf.mcp.client

import scala.collection.mutable.{ArrayBuffer, Queue}

import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the MCP Streamable HTTP transport.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpStreamableHttpTransportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "MCP Streamable HTTP transport" should {
    "initialize one session and execute typed discovery and invocation over JSON and SSE" in {
      Given("a runtime-owned endpoint and scripted Streamable HTTP responses")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _json_response(
          200,
          """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","serverInfo":{"name":"catalog","version":"1"},"capabilities":{"tools":{}}}}""",
          Map("mcp-session-id" -> "session-1")
        ),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, None)),
        _sse_response(
          """event: message
            |data: {"jsonrpc":"2.0","method":"notifications/progress","params":{}}
            |
            |event: message
            |data: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"found"}],"structuredContent":{"count":1}}}
            |
            |""".stripMargin
        )
      ))
      val registry = _registry(fake)
      val service = registry.resolve(_server_set_id("research")).toOption.get

      When("the consumer discovers and invokes one admitted tool")
      val catalog = service.catalog
      val tool = catalog.toOption.get.tools.head
      val result = service.invoke(_call(tool.identity, "semantic runtime"))

      Then("the typed boundary hides HTTP and preserves negotiated session semantics")
      catalog.toOption.map(_.tools.map(_.identity.print)) shouldBe Some(Vector("catalog/paper.search"))
      tool.inputSchema.kind shouldBe McpValueKind.ObjectValue
      result.toOption.map(_.content) shouldBe Some(Vector(McpClientContent.Text("found")))
      result.toOption.flatMap(_.structuredContent) shouldBe Some(
        McpValue.objectC(Vector(_field_name("count") -> McpValue.IntegerValue(1))).toOption.get
      )
      fake.requests.map(_.method).toVector shouldBe Vector("POST", "POST", "POST", "POST")
      _method(fake.requests(0)) shouldBe Some("initialize")
      _method(fake.requests(1)) shouldBe Some("notifications/initialized")
      _method(fake.requests(2)) shouldBe Some("tools/list")
      _method(fake.requests(3)) shouldBe Some("tools/call")
      fake.requests(0).headers.keySet should not contain "Mcp-Session-Id"
      all(fake.requests.drop(1).map(_.headers.get("Mcp-Session-Id"))) shouldBe Some("session-1")
      all(fake.requests.drop(1).map(_.headers.get("MCP-Protocol-Version"))) shouldBe Some("2025-11-25")
      service.getClass.getMethods.map(_.getName).toSet should not contain "endpoint"
    }

    "follow tools/list cursors without reinitializing the session" in {
      Given("two catalog pages in one initialized session")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _initialize_response(1),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, Some("next-page"), "paper.search")),
        _json_response(200, _tools_list_response(3, None, "paper.read"))
      ))
      val service = _registry(fake).resolve(_server_set_id("research")).toOption.get

      When("the admitted catalog is discovered")
      val catalog = service.catalog

      Then("both pages are normalized in deterministic tool order")
      catalog.toOption.map(_.tools.map(_.identity.print)) shouldBe
        Some(Vector("catalog/paper.read", "catalog/paper.search"))
      fake.requests.count(x => _method(x).contains("initialize")) shouldBe 1
      val listrequests = fake.requests.filter(x => _method(x).contains("tools/list"))
      listrequests.size shouldBe 2
      listrequests(1).body.flatMap(parse(_).toOption)
        .flatMap(_.hcursor.downField("params").get[String]("cursor").toOption) shouldBe Some("next-page")
    }

    "discard unlisted tool metadata before decoding its schema" in {
      Given("a server response containing an unlisted malformed tool before one valid admitted tool")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _initialize_response(1),
        _response(202, "application/json", ""),
        _json_response(
          200,
          """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"paper.delete","description":"not admitted"},{"name":"paper.search","inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}]}}"""
        )
      ))
      val service = _registry(fake).resolve(_server_set_id("research")).toOption.get

      When("the catalog crosses the Streamable HTTP admission boundary")
      val catalog = service.catalog

      Then("malformed metadata outside the allowlist cannot deny the admitted catalog")
      catalog.toOption.map(_.tools.map(_.identity.print)) shouldBe Some(Vector("catalog/paper.search"))
    }

    "reinitialize one expired session and replay the interrupted logical request once" in {
      Given("a catalog session that expires before one admitted tool invocation")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _initialize_response(1, "session-1"),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, None)),
        _response(404, "application/json", ""),
        _initialize_response(4, "session-2"),
        _response(202, "application/json", ""),
        _json_response(200, """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"recovered"}]}}""")
      ))
      val service = _registry(fake).resolve(_server_set_id("research")).toOption.get
      val tool = service.catalog.toOption.get.tools.head

      When("the server reports the established session as expired")
      val result = service.invoke(_call(tool.identity, "paper"))

      Then("the transport negotiates a fresh session and retries without changing the logical request id")
      result.toOption.map(_.content) shouldBe Some(Vector(McpClientContent.Text("recovered")))
      fake.requests.count(x => _method(x).contains("initialize")) shouldBe 2
      fake.requests.filter(x => _method(x).contains("tools/call")).map(_.headers.get("Mcp-Session-Id")).toVector shouldBe
        Vector(Some("session-1"), Some("session-2"))
      fake.requests.filter(x => _method(x).contains("tools/call")).flatMap(_.body.flatMap(parse(_).toOption)
        .flatMap(_.hcursor.get[Long]("id").toOption)).toVector shouldBe Vector(3L, 3L)
    }

    "reject invalid session identifiers before sending initialized or catalog requests" in {
      Given("an initialize response containing a control character in the session header")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _initialize_response(1, "invalid\nsession")
      ))
      val service = _registry(fake).resolve(_server_set_id("research")).toOption.get

      When("the consumer asks for the catalog")
      val result = service.catalog

      Then("initialization fails without retaining or echoing the invalid header")
      result.isFaillure shouldBe true
      fake.requests.map(_method).toVector shouldBe Vector(Some("initialize"))
      fake.requests.flatMap(_.headers.values).exists(_.contains("invalid")) shouldBe false
    }

    "reject malformed JSON-RPC response envelopes before interpreting their result" in {
      Given("an initialize response with a matching id but no JSON-RPC version")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _json_response(200, """{"id":1,"result":{"protocolVersion":"2025-11-25"}}""")
      ))
      val service = _registry(fake).resolve(_server_set_id("research")).toOption.get

      When("the consumer asks for the catalog")
      val result = service.catalog

      Then("the response fails at the protocol boundary")
      result.isFaillure shouldBe true
      fake.requests.map(_method).toVector shouldBe Vector(Some("initialize"))
    }

    "require tool result content and object-shaped structured content" in {
      Given("two admitted tools returning incomplete and scalar result payloads")
      given ExecutionContext = ExecutionContext.create()
      val missingcontent = new _FakeExchange(Vector(
        _initialize_response(1),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, None)),
        _json_response(200, """{"jsonrpc":"2.0","id":3,"result":{"structuredContent":{"count":1}}}""")
      ))
      val scalarstructured = new _FakeExchange(Vector(
        _initialize_response(1),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, None)),
        _json_response(200, """{"jsonrpc":"2.0","id":3,"result":{"content":[],"structuredContent":"invalid"}}""")
      ))

      When("both result forms cross independent transport boundaries")
      val missingservice = _registry(missingcontent).resolve(_server_set_id("research")).toOption.get
      val missingtool = missingservice.catalog.toOption.get.tools.head
      val missingresult = missingservice.invoke(_call(missingtool.identity, "paper"))
      val scalarservice = _registry(scalarstructured).resolve(_server_set_id("research")).toOption.get
      val scalartool = scalarservice.catalog.toOption.get.tools.head
      val scalarresult = scalarservice.invoke(_call(scalartool.identity, "paper"))

      Then("both malformed results fail instead of being normalized into valid empty data")
      missingresult.isFaillure shouldBe true
      scalarresult.isFaillure shouldBe true
    }

    "return a structured failure for a remote tool error without retaining remote text" in {
      Given("an initialized catalog and a remote isError result containing sensitive text")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _initialize_response(1),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, None)),
        _json_response(
          200,
          """{"jsonrpc":"2.0","id":3,"result":{"isError":true,"content":[{"type":"text","text":"Authorization: secret-token"}]}}"""
        )
      ))
      val service = _registry(fake).resolve(_server_set_id("research")).toOption.get
      val tool = service.catalog.toOption.get.tools.head

      When("the admitted tool returns an MCP tool error")
      val result = service.invoke(_call(tool.identity, "paper"))

      Then("the normal Conclusion path carries classification but not raw remote payload")
      result.isFaillure shouldBe true
      val conclusion = result match {
        case Consequence.Failure(value) => value
        case _ => fail("remote MCP tool failure is required")
      }
      conclusion.display should not include "secret-token"
      conclusion.observation.cause.descriptor.facets
        .exists(_.print.contains("remote-tool-error")) shouldBe true
    }

    "project every standard MCP tool content block without retaining wire JSON" in {
      Given("an admitted tool returning image audio link and embedded resource content")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _initialize_response(1),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, None)),
        _json_response(
          200,
          """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"image","data":"aGVsbG8=","mimeType":"image/png","annotations":{"audience":["user"],"priority":0.8}},{"type":"audio","data":"AAAA","mimeType":"audio/wav"},{"type":"resource_link","uri":"file:///project/main.scala","name":"main.scala","mimeType":"text/x-scala","size":42},{"type":"resource","resource":{"uri":"file:///project/readme.txt","mimeType":"text/plain","text":"readme"}},{"type":"resource","resource":{"uri":"file:///project/icon.bin","mimeType":"application/octet-stream","blob":"AAAA"}}]}}"""
        )
      ))
      val service = _registry(fake).resolve(_server_set_id("research")).toOption.get
      val tool = service.catalog.toOption.get.tools.head

      When("the standard content blocks cross the provider-neutral Port")
      val result = service.invoke(_call(tool.identity, "paper")).toOption.get

      Then("each block has one typed representation and no Circe value")
      result.content.map(_.getClass.getSimpleName) shouldBe Vector(
        "Image",
        "Audio",
        "ResourceLink",
        "EmbeddedTextResource",
        "EmbeddedBlobResource"
      )
      val image = result.content.head.asInstanceOf[McpClientContent.Image]
      image.mimeType.print shouldBe "image/png"
      image.annotations.toVector.flatMap(_.audience).map(_.name) shouldBe Vector("user")
      val link = result.content(2).asInstanceOf[McpClientContent.ResourceLink]
      link.uri.print shouldBe "file:///project/main.scala"
      link.size shouldBe Some(42L)
    }

    "terminate an established server session when its registry closes" in {
      Given("one established HTTP session and a DELETE-capable server")
      given ExecutionContext = ExecutionContext.create()
      val fake = new _FakeExchange(Vector(
        _initialize_response(1),
        _response(202, "application/json", ""),
        _json_response(200, _tools_list_response(2, None)),
        _response(204, "application/json", "")
      ))
      val registry = _registry(fake)
      registry.resolve(_server_set_id("research")).toOption.get.catalog.toOption.get

      When("the runtime-owned registry is closed")
      registry.close()

      Then("the transport attempts session DELETE with the negotiated headers")
      fake.requests.last.method shouldBe "DELETE"
      fake.requests.last.headers.get("Mcp-Session-Id") shouldBe Some("session-1")
      fake.closed shouldBe true
    }

    "admit only absolute HTTP endpoint forms for generated runtime hosts" in {
      Given("generated safe hosts plus endpoint strings containing user-info or fragments")
      val hostgen = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(32))
      val property = Prop.forAll(hostgen) { host =>
        McpStreamableHttpServerConfig.createC(
          _server_id("catalog"),
          s"https://${host}.example/mcp"
        ).toOption.exists(_.endpoint.getHost == s"${host}.example")
      }

      When("runtime endpoint configuration is validated")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)
      val rejected = Vector(
        "file:///tmp/mcp",
        "https://user:secret@example.test/mcp",
        "https://example.test/mcp#fragment",
        "/relative/mcp"
      ).map(McpStreamableHttpServerConfig.createC(_server_id("catalog"), _))

      Then("only absolute HTTP transport endpoints without embedded credentials are admitted")
      checked.passed shouldBe true
      rejected.forall(_.isFaillure) shouldBe true
    }
  }

  private final class _FakeExchange(
    responses: Vector[McpStreamableHttpResponse]
  ) extends McpStreamableHttpExchange {
    private val _responses = Queue.from(responses)
    val requests = ArrayBuffer.empty[McpStreamableHttpRequest]
    var closed = false

    def execute(request: McpStreamableHttpRequest): Consequence[McpStreamableHttpResponse] = {
      requests += request
      _responses.dequeueFirst(_ => true) match {
        case Some(response) => Consequence.success(response)
        case None => Consequence.serviceUnavailable("fake MCP response is exhausted")
      }
    }

    override def close(): Unit =
      closed = true
  }

  private def _registry(fake: _FakeExchange)(using ExecutionContext): McpClientRuntimeRegistry = {
    val serversetid = _server_set_id("research")
    val serverid = _server_id("catalog")
    val transportconfig = McpStreamableHttpServerSetConfig.createC(
      serversetid,
      Vector(McpStreamableHttpServerConfig.createC(serverid, "https://mcp.example.test/service").toOption.get)
    ).toOption.get
    val provider = McpStreamableHttpTransportProvider.createC(
      Vector(transportconfig),
      () => fake
    ).toOption.get
    val serverset = McpClientServerSet.createC(
      serversetid,
      Vector(McpClientServer.createC(serverid, Set(_tool_name("paper.search"), _tool_name("paper.read"))).toOption.get)
    ).toOption.get
    McpClientRuntimeRegistry.createC(Vector(serverset), provider.binding).toOption.get
  }

  private def _initialize_response(
    id: Long,
    sessionid: String = "session-1"
  ): McpStreamableHttpResponse =
    _json_response(
      200,
      s"""{"jsonrpc":"2.0","id":${id},"result":{"protocolVersion":"2025-11-25","serverInfo":{"name":"catalog","version":"1"},"capabilities":{"tools":{}}}}""",
      Map("mcp-session-id" -> sessionid)
    )

  private def _tools_list_response(
    id: Long,
    nextcursor: Option[String],
    toolname: String = "paper.search"
  ): String = {
    val cursor = nextcursor.map(x => s""","nextCursor":"${x}"""").getOrElse("")
    s"""{"jsonrpc":"2.0","id":${id},"result":{"tools":[{"name":"${toolname}","title":"Paper search","description":"Search papers","inputSchema":{"type":"object","properties":{"query":{"type":"string","description":"Search query"}},"required":["query"],"additionalProperties":false}}]${cursor}}}"""
  }

  private def _json_response(
    status: Int,
    body: String,
    headers: Map[String, String] = Map.empty
  ): McpStreamableHttpResponse =
    _response(status, "application/json", body, headers)

  private def _sse_response(body: String): McpStreamableHttpResponse =
    _response(200, "text/event-stream", body)

  private def _response(
    status: Int,
    contenttype: String,
    body: String,
    headers: Map[String, String] = Map.empty
  ): McpStreamableHttpResponse =
    McpStreamableHttpResponse(
      status,
      (headers + ("content-type" -> contenttype)).map { case (name, value) => name.toLowerCase(java.util.Locale.ROOT) -> value },
      body
    )

  private def _method(request: McpStreamableHttpRequest): Option[String] =
    request.body.flatMap(parse(_).toOption).flatMap(_.hcursor.get[String]("method").toOption)

  private def _call(identity: McpToolIdentity, query: String): McpClientCall =
    McpClientCall.createC(
      identity,
      McpValue.objectC(Vector(_field_name("query") -> McpValue.StringValue(query))).toOption.get
    ).toOption.get

  private def _server_set_id(value: String): McpServerSetId =
    McpServerSetId.parseC(value).toOption.get

  private def _server_id(value: String): McpServerId =
    McpServerId.parseC(value).toOption.get

  private def _field_name(value: String): McpFieldName =
    McpFieldName.parseC(value).toOption.get

  private def _tool_name(value: String): McpToolName =
    McpToolName.parseC(value).toOption.get
}
