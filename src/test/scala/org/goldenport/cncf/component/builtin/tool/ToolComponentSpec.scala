package org.goldenport.cncf.component.builtin.tool

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import java.nio.file.Path
import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.mcp.McpToolCatalog
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.resource.{InMemoryUrnResourceProvider, ResourceAccess, ResourceAccessTestProfile, ResourceContent, ResourceReference, ResourceUrlPolicy, StaticWebUrlResourceProvider}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.spi.web.search.{WebSearch, WebSearchItem, WebSearchRequest, WebSearchResponse}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemDescriptor, Subsystem}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 21, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class ToolComponentSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _time_metadata =
    afterWord("in spec:mcp-client-boundary, tool:tool.time.now, phase:45, stage:MC-07")
  private val _decimal_metadata =
    afterWord("in spec:mcp-client-boundary, tool:tool.decimal.calculate, phase:45, stage:MC-07")
  private val _resource_metadata =
    afterWord("in spec:mcp-client-boundary, tool:tool.resource.read, phase:45, stage:MC-07")
  private val _web_metadata =
    afterWord("in spec:mcp-client-boundary, tools:tool.web.fetch/tool.web.head, phase:45, stage:MC-07")
  private val _web_search_metadata =
    afterWord("in spec:mcp-client-boundary, tool:tool.web.search, phase:45, stage:MC-07")
  private val _framework_boundary_metadata =
    afterWord("in spec:mcp-client-boundary, component:tool, phase:45, stage:MC-07")

  "Builtin resource Operation" should {
    "read bounded text only through the execution-context ResourceAccess" must _resource_metadata {
      "when an admitted logical URN is supplied" in {
        Given("a runtime with one explicit in-memory URN provider")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val profile = ResourceAccessTestProfile(
          urnProviders = Vector(new InMemoryUrnResourceProvider(
            "example",
            Map("article:1" -> "bounded semantic content")
          ))
        )
        given ExecutionContext = ExecutionContext.withResourceAccessTestProfile(
          ExecutionContext.create(),
          profile
        )

        When("tool.resource.read resolves the logical reference")
        val record = subsystem.executeQueryOnlyWithMetadata(
          _request("resource", "read", "reference" -> "urn:example:article:1")
        ).map(_.response).flatMap(_record_response_c).toOption.get

        Then("the response exposes text and safe content metadata without provider identity")
        record.getString("text") shouldBe Some("bounded semantic content")
        record.getString("scheme") shouldBe Some("urn")
        record.getLong("byteSize") shouldBe Some(24L)
        record.asMap.keySet should not contain "provider"
        record.asMap.keySet should not contain "reference"
      }
    }

    "preserve reference parsing and provider admission as structured failures" must _resource_metadata {
      "when a relative reference and an unconfigured absolute reference are supplied" in {
        Given("the normal runtime without an arbitrary resource provider")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))

        When("both values cross the normal Operation boundary")
        val relative = subsystem.executeOperationResponse(
          _request("resource", "read", "reference" -> "relative/file.txt")
        )
        val unconfigured = subsystem.executeOperationResponse(
          _request("resource", "read", "reference" -> "urn:example:missing")
        )

        Then("neither request creates an independent filesystem or network path")
        relative.isSuccess shouldBe false
        unconfigured.isSuccess shouldBe false
      }
    }

    "reject content above the builtin tool projection limit" must _resource_metadata {
      "when an admitted provider returns more than one MiB" in {
        Given("a deterministic provider whose logical resource exceeds the tool response budget")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val access = new ResourceAccess {
          def read(reference: ResourceReference): Consequence[ResourceContent] =
            Consequence.success(ResourceContent(reference, Vector.fill(1024 * 1024 + 1)('a'.toByte)))
        }
        given ExecutionContext = ExecutionContext.withResourceAccess(ExecutionContext.create(), access)

        When("the resource crosses the builtin Operation projection boundary")
        val result = subsystem.executeQueryOnlyWithMetadata(
          _request("resource", "read", "reference" -> "urn:example:oversized")
        )

        Then("the normal structured limit failure is returned without text projection")
        result.isSuccess shouldBe false
      }
    }
  }

  "Builtin time Operation" should {
    "read one instant from the controlled runtime clock" must _time_metadata {
      "when the caller selects an admitted IANA timezone" in {
        Given("a controlled CNCF runtime clock and the normal builtin Operation route")
        val instant = Instant.parse("2026-07-21T01:02:03.456Z")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        given ExecutionContext = ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC))

        When("tool.time.now is executed for Asia/Tokyo")
        val record = subsystem.executeQueryOnlyWithMetadata(
          _request("time", "now", "timezone" -> "Asia/Tokyo")
        ).map(_.response).flatMap(_record_response_c) match {
          case Consequence.Success(value) => value
          case other => fail(s"expected record response but got $other")
        }

        Then("all time fields derive from the same injected instant")
        record.getString("instant") shouldBe Some("2026-07-21T01:02:03.456Z")
        record.getString("timezone") shouldBe Some("Asia/Tokyo")
        record.getString("zonedDateTime") shouldBe Some("2026-07-21T10:02:03.456+09:00[Asia/Tokyo]")
      }
    }

    "reject a timezone outside the bounded IANA contract" must _time_metadata {
      "when an offset-shaped timezone is supplied" in {
        Given("a time Operation request with a non-region timezone")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))

        When("the request is parsed through normal Operation dispatch")
        val result = subsystem.executeOperationResponse(
          _request("time", "now", "timezone" -> "+09:00")
        )

        Then("the request fails structurally before ActionCall execution")
        result.isSuccess shouldBe false
      }
    }
  }

  "Builtin static Web Operations" should {
    "fetch text and project HEAD metadata through one admitted read path" must _web_metadata {
      "when a public HTTPS target is explicitly configured" in {
        Given("a public literal target and deterministic configured HTTPS provider")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val access = ResourceAccess.url(
          ResourceUrlPolicy(httpsHosts = Vector("8.8.8.8")),
          Vector(new StaticWebUrlResourceProvider {
            val scheme = "https"
            def read(reference: ResourceReference.Url): Consequence[ResourceContent] =
              readStaticWeb(reference)
            def readStaticWeb(reference: ResourceReference.Url): Consequence[ResourceContent] =
              Consequence.success(ResourceContent(
                reference,
                "static web content".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector,
                mediaType = Some("text/plain"),
                declaredCharset = Some(java.nio.charset.StandardCharsets.UTF_8)
              ))
          })
        )
        given ExecutionContext = ExecutionContext.withResourceAccess(ExecutionContext.create(), access)

        When("fetch and GET-backed head execute through normal Query operations")
        val fetch = subsystem.executeQueryOnlyWithMetadata(
          _request("web", "fetch", "url" -> "https://8.8.8.8/article")
        ).map(_.response).flatMap(_record_response_c).toOption.get
        val head = subsystem.executeQueryOnlyWithMetadata(
          _request("web", "head", "url" -> "https://8.8.8.8/article")
        ).map(_.response).flatMap(_record_response_c).toOption.get

        Then("fetch contains text while head exposes only safe metadata")
        fetch.getString("text") shouldBe Some("static web content")
        fetch.getString("mode") shouldBe Some("fetch")
        head.getString("text") shouldBe None
        head.getString("mode") shouldBe Some("head")
        head.getString("mediaType") shouldBe Some("text/plain")
      }
    }

    "reject private-network targets before ResourceAccess" must _web_metadata {
      "when loopback and private literal targets are requested" in {
        Given("a provider that would reveal any request crossing admission")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        var reads = 0
        val access = new ResourceAccess {
          def read(reference: ResourceReference): Consequence[ResourceContent] = {
            reads += 1
            Consequence.success(ResourceContent(reference, Vector.empty, Some("text/plain")))
          }
        }
        given ExecutionContext = ExecutionContext.withResourceAccess(ExecutionContext.create(), access)

        When("the targets are dispatched through web.fetch")
        val results = Vector("127.0.0.1", "10.0.0.1", "192.168.1.1").map { host =>
          subsystem.executeQueryOnlyWithMetadata(
            _request("web", "fetch", "url" -> s"https://${host}/internal")
          )
        }

        Then("all fail before the provider boundary")
        results.forall(!_.isSuccess) shouldBe true
        reads shouldBe 0
      }
    }

    "reject non-textual content before projecting a Web result" must _web_metadata {
      "when an admitted provider returns image content" in {
        Given("a public target whose configured provider returns a binary media type")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val access = ResourceAccess.url(
          ResourceUrlPolicy(httpsHosts = Vector("8.8.8.8")),
          Vector(new StaticWebUrlResourceProvider {
            val scheme = "https"
            def read(reference: ResourceReference.Url): Consequence[ResourceContent] =
              readStaticWeb(reference)
            def readStaticWeb(reference: ResourceReference.Url): Consequence[ResourceContent] =
              Consequence.success(ResourceContent(reference, Vector(1, 2, 3), Some("image/png")))
          })
        )
        given ExecutionContext = ExecutionContext.withResourceAccess(ExecutionContext.create(), access)

        When("web.fetch validates the provider result")
        val result = subsystem.executeQueryOnlyWithMetadata(
          _request("web", "fetch", "url" -> "https://8.8.8.8/image.png")
        )

        Then("the media policy is a structured failure and no binary data is projected")
        result.isSuccess shouldBe false
      }
    }
  }

  "Builtin Web search Operation" should {
    "query only the runtime-installed provider and return bounded safe results" must _web_search_metadata {
      "when a provider-neutral WebSearch SPI is installed" in {
        Given("the builtin tool component with one deterministic runtime-owned search provider")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val tool = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL).get.asInstanceOf[ToolComponent]
        var observed: Option[WebSearchRequest] = None
        tool.withWebSearch(new WebSearch {
          def search(req: WebSearchRequest)(using ExecutionContext): Consequence[WebSearchResponse] = {
            observed = Some(req)
            Consequence.success(WebSearchResponse(Vector(
              WebSearchItem("CNCF design", "https://example.org/cncf", Some("Provider-neutral result"))
            )))
          }
        })
        given ExecutionContext = ExecutionContext.create()

        When("tool.web.search receives only a logical query and result limit")
        val record = subsystem.executeQueryOnlyWithMetadata(
          _request("web", "search", "query" -> "CNCF architecture", "limit" -> 2)
        ).map(_.response).flatMap(_record_response_c).toOption.get

        Then("the runtime provider receives no caller provider or credential selection")
        observed shouldBe Some(WebSearchRequest("CNCF architecture", 2))
        record.getLong("count") shouldBe Some(1L)
        record.getAny("items").map(_.toString).getOrElse("") should include ("https://example.org/cncf")
        record.asMap.keySet should not contain "provider"
      }
    }

    "preserve missing providers and invalid provider output as structured failures" must _web_search_metadata {
      "when providers emit unsafe duplicate or blank result fields" in {
        Given("subsystems for missing unsafe duplicate and blank provider outcomes")
        val missing = RuntimeBindingAdmissionFixture.default(Some("command"))
        val invalid = RuntimeBindingAdmissionFixture.default(Some("command"))
        val duplicate = RuntimeBindingAdmissionFixture.default(Some("command"))
        val blank = RuntimeBindingAdmissionFixture.default(Some("command"))
        invalid.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL).get.asInstanceOf[ToolComponent].withWebSearch(new WebSearch {
          def search(req: WebSearchRequest)(using ExecutionContext): Consequence[WebSearchResponse] =
            Consequence.success(WebSearchResponse(Vector(
              WebSearchItem("internal", "https://127.0.0.1/private")
            )))
        })
        duplicate.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL).get.asInstanceOf[ToolComponent].withWebSearch(new WebSearch {
          def search(req: WebSearchRequest)(using ExecutionContext): Consequence[WebSearchResponse] =
            Consequence.success(WebSearchResponse(Vector(
              WebSearchItem("first", "https://example.org/result"),
              WebSearchItem("second", " https://example.org/result ")
            )))
        })
        blank.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL).get.asInstanceOf[ToolComponent].withWebSearch(new WebSearch {
          def search(req: WebSearchRequest)(using ExecutionContext): Consequence[WebSearchResponse] =
            Consequence.success(WebSearchResponse(Vector(
              WebSearchItem("blank snippet", "https://example.org/blank", Some("   "))
            )))
        })
        given ExecutionContext = ExecutionContext.create()

        When("all requests execute through the normal Operation boundary")
        val absent = missing.executeOperationResponse(
          _request("web", "search", "query" -> "missing provider")
        )
        val unsafe = invalid.executeOperationResponse(
          _request("web", "search", "query" -> "unsafe result")
        )
        val repeated = duplicate.executeOperationResponse(
          _request("web", "search", "query" -> "duplicate result")
        )
        val empty = blank.executeOperationResponse(
          _request("web", "search", "query" -> "blank snippet")
        )

        Then("no missing or invalid provider state is projected as success")
        absent.isSuccess shouldBe false
        unsafe.isSuccess shouldBe false
        repeated.isSuccess shouldBe false
        empty.isSuccess shouldBe false
      }
    }

    "enforce query limit result-count bounds and the default result limit" must _web_search_metadata {
      "when request and provider values meet or cross their declared boundaries" in {
        Given("a provider that records admitted requests and returns two safe results")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val tool = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL).get.asInstanceOf[ToolComponent]
        var observed: Vector[WebSearchRequest] = Vector.empty
        tool.withWebSearch(new WebSearch {
          def search(req: WebSearchRequest)(using ExecutionContext): Consequence[WebSearchResponse] = {
            observed = observed :+ req
            Consequence.success(WebSearchResponse(Vector(
              WebSearchItem("first", "https://example.org/first"),
              WebSearchItem("second", "https://example.org/second")
            )))
          }
        })
        given ExecutionContext = ExecutionContext.create()

        When("default bounded and over-limit requests cross the normal Operation boundary")
        val defaulted = subsystem.executeOperationResponse(
          _request("web", "search", "query" -> "default limit")
        )
        val countoverflow = subsystem.executeOperationResponse(
          _request("web", "search", "query" -> "one result", "limit" -> 1)
        )
        val queryoverflow = subsystem.executeOperationResponse(
          _request("web", "search", "query" -> ("q" * 513))
        )
        val zero = subsystem.executeOperationResponse(
          _request("web", "search", "query" -> "zero", "limit" -> 0)
        )
        val eleven = subsystem.executeOperationResponse(
          _request("web", "search", "query" -> "eleven", "limit" -> 11)
        )
        val fractional = subsystem.executeOperationResponse(
          _request("web", "search", "query" -> "fractional", "limit" -> 1.5)
        )

        Then("only the valid default request succeeds and every boundary violation is structured")
        defaulted.isSuccess shouldBe true
        observed shouldBe Vector(
          WebSearchRequest("default limit", 10),
          WebSearchRequest("one result", 1)
        )
        countoverflow.isSuccess shouldBe false
        queryoverflow.isSuccess shouldBe false
        zero.isSuccess shouldBe false
        eleven.isSuccess shouldBe false
        fractional.isSuccess shouldBe false
      }
    }
  }

  "Builtin decimal Operation" should {
    "calculate exact bounded decimal values without floating-point conversion" must _decimal_metadata {
      "when bounded add, subtract, and multiply inputs are generated" in {
        Given("integer coefficients rendered as exact decimal strings")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val property = Prop.forAll(
          Gen.chooseNum(-1000000L, 1000000L),
          Gen.chooseNum(-1000000L, 1000000L),
          Gen.oneOf("add", "subtract", "multiply")
        ) { (left, right, operator) =>
          val expected = operator match {
            case "add" => BigDecimal(left) + BigDecimal(right)
            case "subtract" => BigDecimal(left) - BigDecimal(right)
            case "multiply" => BigDecimal(left) * BigDecimal(right)
          }
          val actual = _execute_record_c(
            subsystem,
            _request(
              "decimal",
              "calculate",
              "operator" -> operator,
              "left" -> s"${left}.00",
              "right" -> s"${right}.0"
            )
          ).toOption.flatMap(_.getString("value"))
          actual.contains(_decimal_text(expected))
        }

        When("the generated requests execute through ActionCall/UoW")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every result is the exact canonical plain-decimal value")
        checked.passed shouldBe true
      }
    }

    "reject expression and oversized decimal input" must _decimal_metadata {
      "when callers attempt an undeclared expression operator or oversized operand" in {
        Given("the closed deterministic decimal Operation contract")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))

        When("an expression-shaped operator and oversized decimal are dispatched")
        val expression = subsystem.executeOperationResponse(
          _request("decimal", "calculate", "operator" -> "left + right", "left" -> "1", "right" -> "2")
        )
        val oversized = subsystem.executeOperationResponse(
          _request("decimal", "calculate", "operator" -> "add", "left" -> ("1" * 129), "right" -> "2")
        )

        Then("both requests fail as structured argument policy violations")
        expression.isSuccess shouldBe false
        oversized.isSuccess shouldBe false
      }
    }
  }

  "Builtin tool MCP projection" should {
    "publish the same normal Operations with typed string inputs" must _decimal_metadata {
      "when the default subsystem MCP catalog is projected" in {
        Given("the builtin tool component with MCP-ready resource, Web, time, and decimal services")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val componentid = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL.name

        When("the existing MCP server catalog projects normal Operations")
        val tools = McpToolCatalog.toolsForSubsystem(subsystem)
        val time = tools.find(_.name == s"$componentid.time.now")
        val decimal = tools.find(_.name == s"$componentid.decimal.calculate")
        val resource = tools.find(_.name == s"$componentid.resource.read")
        val webfetch = tools.find(_.name == s"$componentid.web.fetch")
        val webhead = tools.find(_.name == s"$componentid.web.head")
        val websearch = tools.find(_.name == s"$componentid.web.search")

        Then("all identities are present without a separate MCP implementation")
        time should not be empty
        decimal should not be empty
        resource should not be empty
        webfetch should not be empty
        webhead should not be empty
        websearch should not be empty
        resource.get.inputSchema.hcursor.downField("properties")
          .downField("reference").get[String]("type") shouldBe Right("string")
        val properties = decimal.get.inputSchema.hcursor.downField("properties")
        val searchproperties = websearch.get.inputSchema.hcursor.downField("properties")
        searchproperties.downField("query").get[String]("type") shouldBe Right("string")
        searchproperties.downField("limit").get[String]("type") shouldBe Right("integer")
        properties.downField("left").get[String]("type") shouldBe Right("string")
        properties.downField("right").get[String]("type") shouldBe Right("string")
      }
    }
  }

  "Builtin tool framework boundary" should {
    "enforce descriptor authorization before invoking a runtime provider" must _framework_boundary_metadata {
      "when a normal operation authorization rule denies Web search" in {
        Given("the builtin tool component with a provider and a descriptor-level deny rule")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val tool = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL).get.asInstanceOf[ToolComponent]
        var providercalled = false
        tool.withWebSearch(new WebSearch {
          def search(req: WebSearchRequest)(using ExecutionContext): Consequence[WebSearchResponse] = {
            providercalled = true
            Consequence.success(WebSearchResponse(Vector.empty))
          }
        })
        subsystem.withDescriptor(GenericSubsystemDescriptor(
          path = Path.of("<tool-framework-boundary>"),
          subsystemName = subsystem.name,
          operationAuthorization = Map(
            s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL.name}.web.search" -> OperationAuthorizationRule(deny = true)
          )
        ))
        given ExecutionContext = ExecutionContext.create()

        When("the request crosses normal Subsystem dispatch")
        val result = subsystem.executeQueryOnlyWithMetadata(
          _request("web", "search", "query" -> "denied")
        )

        Then("authorization returns a structured Conclusion before the provider boundary")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.status.webCode.code shouldBe 403
            conclusion.display should include ("org.goldenport.cncf.Tool.web.search")
          case other =>
            fail(s"expected authorization failure but got $other")
        }
        providercalled shouldBe false
      }
    }

    "record normal ActionCall CallTree and dashboard metrics" must _framework_boundary_metadata {
      "when a builtin Query succeeds and another request fails during parameter validation" in {
        Given("an enabled framework CallTree and baseline ActionCall and validation metrics")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val context = ExecutionContext.withFrameworkCallTreeEnabled(
          ExecutionContext.create(),
          enabled = true
        )
        val beforeactions = RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total
        val beforevalidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot.summary.cumulative.total
        given ExecutionContext = context

        When("time.now succeeds and an invalid timezone fails through request construction")
        val success = subsystem.executeQueryOnlyWithMetadata(_request("time", "now"))
        val invalid = subsystem.executeQueryOnlyWithMetadata(
          _request("time", "now", "timezone" -> "+09:00")
        )
        val calltree = context.observability.callTreeContext.build()
          .map(_.toRecord.print)
          .getOrElse(fail("builtin tool CallTree missing"))

        Then("the generic ActionCall and validation observers expose both outcomes")
        success.isSuccess shouldBe true
        invalid match {
          case Consequence.Failure(conclusion) =>
            ConclusionDiagnostics.isValidation(conclusion) shouldBe true
          case other =>
            fail(s"expected validation failure but got $other")
        }
        calltree should include (s"action:${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL.name}.time.now")
        calltree should include ("calltree_kind")
        RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total should be > beforeactions
        RuntimeDashboardMetrics.operationRequestValidationSnapshot.summary.cumulative.total should be > beforevalidation
      }
    }

    "publish only the bounded safe builtin capability set" must _framework_boundary_metadata {
      "when the tool component MCP catalog is projected" in {
        Given("the default builtin tool component without optional automation Components")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val tool = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL).get
        val componentid = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL.name

        When("the existing MCP projection reads the component's admitted Operations")
        val identities = McpToolCatalog.toolsForComponent(tool).map(_.name)

        Then("dynamic browser filesystem process script and mutation tools remain absent")
        identities shouldBe Vector(
          s"$componentid.decimal.calculate",
          s"$componentid.resource.read",
          s"$componentid.time.now",
          s"$componentid.web.fetch",
          s"$componentid.web.head",
          s"$componentid.web.search"
        )
      }
    }
  }

  private def _request(
    service: String,
    operation: String,
    properties: (String, Any)*
  ): Request =
    Request.of(
      component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL.name,
      service = service,
      operation = operation,
      properties = properties.map { case (name, value) => Property(name, value, None) }.toList
    )

  private def _execute_record_c(subsystem: Subsystem, request: Request): Consequence[Record] =
    subsystem.executeOperationResponse(request).flatMap(_record_response_c)

  private def _record_response_c(response: OperationResponse): Consequence[Record] =
    response match {
      case OperationResponse.RecordResponse(record) => Consequence.success(record)
      case other => Consequence.operationInvalid(s"expected record response but got $other")
    }

  private def _decimal_text(value: BigDecimal): String =
    value.bigDecimal.stripTrailingZeros.toPlainString
}
