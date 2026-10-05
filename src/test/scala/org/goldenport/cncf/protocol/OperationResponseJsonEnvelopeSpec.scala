package org.goldenport.cncf.protocol

import java.nio.charset.StandardCharsets
import io.circe.Json
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.{CommandExecutionMode, CommandInterfaceMode}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.datatype.ContentType
import org.goldenport.http.{HttpPath, HttpRequest, HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
final class OperationResponseJsonEnvelopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 Typed JSON envelope and existing selection" should afterWord("in spec:display-model-protocol, example:E1, rules:R7,R9, phase:96, slice:P96-05A") {
    "nest arbitrary business JSON without quoting it while retaining data-only and legacy response branches" in {
      Given("object array primitive and null business JSON plus ordinary direct execution metadata")
      val request = _request()
      val candidates = Vector(Json.obj("data" -> Json.fromString("business-owned"), "execution" -> Json.arr()),
        Json.arr(Json.fromString("item"), Json.fromBoolean(true)), Json.fromString("text"), Json.fromBoolean(false), Json.Null)
      When("the existing formatter receives each typed Json OperationResponse with explicit json/envelope selection")
      val results = candidates.map(value => _json(OperationResponseFormatter.toResponse(request,
        OperationResponse.Json(value), RunMode.Command, RuntimeContext.ExecutionMetadata.empty,
        Some(RuntimeContext.ExecutionResponseMetadata.direct))))
      Then("data retains its exact JSON kind and ordinary execution remains separate framework-owned metadata")
      results.zip(candidates).foreach { case (result, expected) =>
        result.hcursor.downField("data").focus shouldBe Some(expected)
        result.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("json")
        result.hcursor.downField("execution").get[String]("operation").toOption shouldBe Some("read")
        result.hcursor.downField("execution").get[String]("component").toOption shouldBe Some("org.goldenport.cncf.test.JsonEnvelope")
        result.hcursor.downField("execution").get[String]("service").toOption shouldBe Some("display")
        result.hcursor.downField("execution").get[String]("response-kind").toOption shouldBe Some("direct")
        Vector("job", "continuation", "debug", "result", "textus-execution").foreach(name => result.hcursor.downField(name).focus shouldBe None)
      }
      Given("the same business Json under raw data selection or a later explicit non-json format property")
      val business = OperationResponse.Json(candidates.head)
      When("data-only json and every existing non-json explicit selection reach the formatter")
      val raw = OperationResponseFormatter.toResponse(_request(shape = "data"), business, RunMode.Command)
      val alternate = Vector("yaml", "xml", "text", "unsupported").map(format =>
        OperationResponseFormatter.toResponse(_request(format = format), business, RunMode.Command))
      val overridden = OperationResponseFormatter.toResponse(request.copy(properties = request.properties :+
        Property("textus.format", "xml", None)), business, RunMode.Command)
      Then("existing Json.toResponse behavior remains intact and the adapter does not invent alternate-format envelopes")
      raw shouldBe business.toResponse
      alternate.foreach(_ shouldBe business.toResponse)
      overridden shouldBe business.toResponse
      Given("existing Record Scalar and explicit Http responses with canonical format and shape requests")
      val record = OperationResponse.RecordResponse(Record.data("title" -> "original"))
      val scalar = OperationResponse.Scalar("original")
      val http = HttpResponse.Text(HttpStatus.Ok, ContentType.parse("application/custom; charset=utf-8"), Bag.text("original body"))
      When("those original response branches format under the same selection mechanism")
      val recordjson = _json(OperationResponseFormatter.toResponse(request, record, RunMode.Command))
      val scalarjson = _json(OperationResponseFormatter.toResponse(request, scalar, RunMode.Command))
      val xml = OperationResponseFormatter.toResponse(_request(format = "xml"), record, RunMode.Command)
      val yaml = OperationResponseFormatter.toResponse(_request(format = "yaml"), record, RunMode.Command)
      val text = OperationResponseFormatter.toResponse(_request(shape = "data", format = "text"), scalar, RunMode.Command)
      val content = OperationResponseFormatter.toResponse(request, OperationResponse.Http(http), RunMode.Command)
      Then("original record/scalar metadata interfaces and format types are unchanged and Http bypass keeps its body/content type")
      recordjson.hcursor.downField("data").get[String]("title").toOption shouldBe Some("original")
      recordjson.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("record")
      scalarjson.hcursor.get[String]("data").toOption shouldBe Some("original")
      scalarjson.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("scalar")
      xml shouldBe a[Response.Xml]
      yaml shouldBe a[Response.Yaml]
      text shouldBe scalar.toResponse
      content shouldBe a[Response.Content]
      content.contentType.header shouldBe "application/custom; charset=UTF-8"
      content.print shouldBe "original body"
    }
  }

  "E2 Framework job continuation and debug ownership" should afterWord("in spec:display-model-protocol, example:E2, rules:R7,R9, phase:96, slice:P96-05A") {
    "suppress accepted-job data only and retain completed business JSON plus ordinary continuation/debug presence" in {
      Given("typed business data with explicit accepted-job job-result and continuation metadata, never inferred from payload strings")
      val data = Json.obj("id" -> Json.fromString("cncf-job-business"), "items" -> Json.arr())
      val request = _request().copy(properties = _request().properties :+
        Property("textus.command.execution-mode", "job-sync-with-async-cont", None))
      val metadata = RuntimeContext.ExecutionMetadata(responseJobId = Some("framework-job"))
      val accepted = RuntimeContext.ExecutionResponseMetadata(admittedMode = CommandExecutionMode.JobAsync.toString,
        effectiveMode = CommandExecutionMode.JobAsync, interfaceMode = CommandInterfaceMode.Async,
        managedByJob = true, responseKind = RuntimeContext.ExecutionResponseKind.AcceptedJob)
      val completed = RuntimeContext.ExecutionResponseMetadata(admittedMode = CommandExecutionMode.JobSyncWithAsyncCont.toString,
        effectiveMode = CommandExecutionMode.JobSyncWithAsyncCont, interfaceMode = CommandInterfaceMode.Sync,
        managedByJob = true, asyncContinuation = true, responseKind = RuntimeContext.ExecutionResponseKind.JobResult)
      When("ordinary typed Json formatting receives accepted completed direct and no-job-id metadata")
      val acceptance = _format(request, data, metadata, Some(accepted))
      val completion = _format(request, data, metadata, Some(completed))
      val direct = _format(request, data, metadata, Some(RuntimeContext.ExecutionResponseMetadata.direct))
      val nojob = _format(request, data, RuntimeContext.ExecutionMetadata.empty, Some(accepted))
      Then("only explicit accepted-job metadata produces data null and each existing optional root retains its normal meaning")
      acceptance.hcursor.downField("data").focus shouldBe Some(Json.Null)
      acceptance.hcursor.downField("job").get[String]("id").toOption shouldBe Some("framework-job")
      acceptance.hcursor.downField("job").get[String]("status").toOption shouldBe Some("accepted")
      acceptance.hcursor.downField("execution").get[String]("response-kind").toOption shouldBe Some("accepted-job")
      acceptance.hcursor.downField("continuation").focus shouldBe None
      completion.hcursor.downField("data").focus shouldBe Some(data)
      completion.hcursor.downField("job").get[String]("id").toOption shouldBe Some("framework-job")
      completion.hcursor.downField("job").downField("status").focus shouldBe None
      completion.hcursor.downField("execution").get[String]("response-kind").toOption shouldBe Some("job-result")
      completion.hcursor.downField("execution").get[String]("requested-mode").toOption shouldBe Some("job-sync-with-async-cont")
      completion.hcursor.downField("continuation").get[String]("mode").toOption shouldBe Some("event-async-same-job-task")
      completion.hcursor.downField("continuation").get[String]("policy").toOption shouldBe Some("async-same-job")
      direct.hcursor.downField("data").focus shouldBe Some(data)
      direct.hcursor.downField("job").focus shouldBe None
      nojob.hcursor.downField("data").focus shouldBe Some(Json.Null)
      nojob.hcursor.downField("job").focus shouldBe None
      Given("ordinary inline calltree metadata alongside either data-only direct or completed envelope JSON")
      val debug = metadata.copy(inlineCallTree = Some(Record.data("job_id" -> "debug-job",
        "nodes" -> Vector(Record.data("label" -> "root")))))
      When("inline debug triggers a JSON envelope even under data shape and combines with completed continuation metadata")
      val debugdirect = _format(_request(shape = "data"), data, debug, Some(RuntimeContext.ExecutionResponseMetadata.direct))
      val debugcompleted = _format(request, data, debug, Some(completed))
      Then("debug never enters business data and framework job/continuation presence remains independently determined")
      Vector(debugdirect, debugcompleted).foreach { result =>
        result.hcursor.downField("data").focus shouldBe Some(data)
        result.hcursor.downField("debug").downField("calltree").get[String]("job_id").toOption shouldBe Some("debug-job")
        result.hcursor.downField("debug").downField("calltree").downField("nodes").focus.get.asArray.get.size shouldBe 1
        result.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("json")
      }
      debugdirect.hcursor.downField("job").focus shouldBe None
      debugdirect.hcursor.downField("continuation").focus shouldBe None
      debugcompleted.hcursor.downField("job").get[String]("id").toOption shouldBe Some("framework-job")
      debugcompleted.hcursor.downField("continuation").focus shouldBe defined
    }
  }

  "E3 Exact typed JSON UTF8 boundary" should afterWord("in spec:display-model-protocol, example:E3, rules:R3,R4,R7,R9, phase:96, slice:P96-05A") {
    "preserve every UTF16 unit nested primitive empty container null and opaque numeric string without locale conversion" in {
      Given("nested JSON containing controls paired/lone surrogates Unicode exact numeric strings and empty values under Japanese/Tokyo assumptions")
      val text = " \t\n\r\b\f\u0000日本 Ω e\u0301\ud83d\ude00\ud800\udfff "
      val data = Json.obj("camelCaseOpaque" -> Json.fromString(text), "values" -> Json.arr(
        Json.fromBoolean(true), Json.arr(), Json.Null,
        Json.obj("number" -> Json.fromString("123456789012345678901234567890.00000"),
          "revision" -> Json.fromString(Long.MaxValue.toString),
          "timestamp" -> Json.fromString("1969-12-31T23:59:59.987654321Z"))))
      _with_locale {
        When("the existing formatter builds a typed envelope and its body is encoded then decoded with actual UTF8")
        val formatted = OperationResponseFormatter.toResponse(_request(), OperationResponse.Json(data), RunMode.Command)
        val body = _body(formatted)
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        val restored = io.circe.parser.parse(new String(bytes, StandardCharsets.UTF_8)).toOption.get
        Then("JSON semantics and original text code units remain exact without quoting normalization rounding or localization")
        bytes.forall(_ >= 0) shouldBe true
        body should include("\\ud800")
        body should include("\\udfff")
        body should include("\\u65e5")
        body.endsWith("\n") shouldBe false
        restored.hcursor.downField("data").focus shouldBe Some(data)
        restored.hcursor.downField("data").get[String]("camelCaseOpaque").toOption.get.toCharArray.toVector shouldBe text.toCharArray.toVector
        restored.hcursor.downField("data").downField("values").downN(1).focus shouldBe Some(Json.arr())
        restored.hcursor.downField("data").downField("values").downN(2).focus shouldBe Some(Json.Null)
        restored.hcursor.downField("data").downField("camel_case_opaque").focus shouldBe None
      }
    }
  }

  "E4 HTTP suffix format precedence" should afterWord("in spec:display-model-protocol, example:E4, rules:R7,R9, output-format:6/7, path-resolution:R4, phase:96, slice:P96-05A") {
    "honor explicit format then retained HTTP suffix before configured or mode fallback" in {
      Given("docs/spec/display-model-protocol.md R7/R9 E4 with configured XML and exact typed business JSON")
      val configuration = ResolvedConfiguration(Configuration(Map(
        "textus.output.format" -> ConfigurationValue.StringValue("xml"))), ConfigurationTrace.empty)
      val data = Json.obj("opaqueValue" -> Json.fromString("日本 Ω \ud800 12345678901234567890.00000"),
        "values" -> Json.arr(Json.Null, Json.arr(), Json.fromBoolean(true)))
      val business = OperationResponse.Json(data)
      val record = Record.data("title" -> "original", "count" -> 7)
      val recordresponse = OperationResponse.RecordResponse(record)
      _with_configuration(configuration) {
        RunMode.values.foreach { mode =>
          Vector("json", "JSON").foreach { suffix =>
            Given(s"canonical read and envelope-only properties with retained read.$suffix HTTP source in $mode")
            val request = _http_request(Vector("component", "display", s"read.$suffix"))
            val original = request.copy()
            When("the real formatter selects typed JSON from the final HTTP path segment")
            val response = OperationResponseFormatter.toResponse(request, business, mode)
            Then("the envelope retains exact business JSON and json metadata without altering the original request")
            val result = _json(response)
            result.hcursor.downField("data").focus shouldBe Some(data)
            result.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("json")
            request shouldBe original
            request.source shouldBe original.source
            request.operation shouldBe "read"
            request.properties shouldBe List(Property("textus.output.shape", "envelope", None))
          }
          Vector("json", "yaml", "xml", "text").foreach { suffix =>
            Given(s"ordinary Record data and read.$suffix HTTP source without explicit format in $mode")
            val request = _http_request(Vector("component", "display", s"read.$suffix"), shape = "data")
            When("the real formatter selects a supported suffix above configured XML and the mode default")
            val response = OperationResponseFormatter.toResponse(request, recordresponse, mode)
            Then("the selected response type and original record fields retain their existing encoding")
            _record_format(response, suffix, record)
          }
          Vector("textus.format", "textus.output.format", "cncf.format", "cncf.output.format", "TeXtUs.FoRmAt").foreach { alias =>
            Given(s"explicit JSON through $alias and conflicting read.yaml HTTP source in $mode")
            val request = _http_request(Vector("component", "display", "read.yaml"))
            val explicit = request.copy(properties = request.properties :+ Property(alias, "json", None))
            When("the real formatter resolves the existing case-insensitive explicit format alias")
            val response = OperationResponseFormatter.toResponse(explicit, business, mode)
            Then("explicit JSON wins and the business object remains nested in its typed envelope")
            val result = _json(response)
            result.hcursor.downField("data").focus shouldBe Some(data)
            result.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("json")
          }
          Given(s"read.json HTTP source with explicit XML, later XML after JSON, or unsupported explicit format in $mode")
          val request = _http_request(Vector("component", "display", "read.json"), shape = "data")
          val explicit = request.copy(properties = request.properties :+ Property("textus.format", "xml", None))
          val envelope = explicit.copy(properties = explicit.properties :+ Property("textus.output.shape", "envelope", None))
          val late = request.copy(properties = request.properties ++ List(
            Property("textus.format", "json", None), Property("cncf.output.format", "xml", None)))
          val invalid = request.copy(properties = request.properties :+ Property("textus.format", "unsupported", None))
          When("the formatter applies explicit selection, last-wins aliases and the existing invalid-format fallback")
          val xml = OperationResponseFormatter.toResponse(explicit, recordresponse, mode)
          val typed = OperationResponseFormatter.toResponse(envelope, business, mode)
          val last = OperationResponseFormatter.toResponse(late, recordresponse, mode)
          val unsupported = OperationResponseFormatter.toResponse(invalid, recordresponse, mode)
          Then("XML wins over suffix JSON, typed Json keeps its baseline response, and invalid explicit format selects YAML")
          _record_format(xml, "xml", record)
          typed shouldBe business.toResponse
          _record_format(last, "xml", record)
          _record_format(unsupported, "yaml", record)

          Vector(Vector("component", "display", "read"), Vector("component", "display", "read.unknown"),
            Vector("component", "display", "read.html"), Vector("component", "display", "read."),
            Vector("component", "display", ".json"), Vector.empty[String],
            Vector("component.json", "display", "read")).foreach { path =>
            Given(s"suffix-free or unsupported final HTTP segment $path with configured XML in $mode")
            val fallback = _http_request(path, shape = "data")
            When("the formatter preserves configuration for an absent or unsupported HTTP suffix")
            val response = OperationResponseFormatter.toResponse(fallback, recordresponse, mode)
            Then("the original record remains XML regardless of dots in an earlier path segment")
            _record_format(response, "xml", record)
          }
          Vector(None, Some(Request.Source.Args(Array("selector.json")))).foreach { source =>
            Given(s"a canonical read with absent or Args source and configured XML in $mode")
            val fallback = request.copy(source = source)
            When("the formatter applies existing non-HTTP format selection")
            val response = OperationResponseFormatter.toResponse(fallback, recordresponse, mode)
            Then("an Args selector suffix does not replace configured XML")
            _record_format(response, "xml", record)
          }
        }
      }
      Given("the same record with no configured format and no supported HTTP suffix")
      val unconfigured = ResolvedConfiguration(Configuration(Map.empty), ConfigurationTrace.empty)
      _with_configuration(unconfigured) {
        RunMode.values.foreach { mode =>
          Given(s"suffix-free read with envelope disabled in $mode")
          val request = _http_request(Vector("component", "display", "read"), shape = "data")
          val format = mode match {
            case RunMode.Server | RunMode.ServerEmulator => "json"
            case _ => "yaml"
          }
          When("the real formatter reaches its original mode fallback")
          val response = OperationResponseFormatter.toResponse(request, recordresponse, mode)
          Then("server modes retain JSON and command client and script modes retain YAML")
          _record_format(response, format, record)
        }
      }
    }
  }

  private def _http_request(path: Vector[String], shape: String = "envelope"): Request =
    Request.of(component = "org.goldenport.cncf.test.JsonEnvelope", service = "display", operation = "read",
      properties = List(Property("textus.output.shape", shape, None))).copy(
      source = Some(Request.Source.Http(HttpRequest(HttpPath(path), HttpRequest.GET,
        Record.empty, Record.empty, Record.empty))))
  private def _record_format(response: Response, format: String, record: Record): Unit = format match {
    case "json" => response shouldBe Response.Json(RecordEncoder.json(record))
    case "yaml" => response shouldBe Response.Yaml(RecordEncoder.yaml(record))
    case "xml" => response shouldBe Response.Xml(RecordEncoder.xml(record))
    case "text" => response shouldBe Response.Scalar(record.print)
    case _ => fail("expected an existing supported record format")
  }
  private def _request(shape: String = "envelope", format: String = "json"): Request =
    Request.of(component = "org.goldenport.cncf.test.JsonEnvelope", service = "display", operation = "read",
      properties = List(Property("textus.output.format", format, None), Property("textus.output.shape", shape, None)))
  private def _body(response: Response): String = response match {
    case Response.Json(value) => value
    case _ => fail("expected selected JSON response")
  }
  private def _json(response: Response): Json = io.circe.parser.parse(_body(response)).toOption.get
  private def _format(request: Request, data: Json, metadata: RuntimeContext.ExecutionMetadata,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]): Json =
    _json(OperationResponseFormatter.toResponse(request, OperationResponse.Json(data), RunMode.Command, metadata, executionresponse))
  private def _with_locale(body: => Unit): Unit = {
    val configuration = ResolvedConfiguration(Configuration(Map(
      "textus.execution.locale" -> ConfigurationValue.StringValue("ja-JP"),
      "textus.execution.timezone" -> ConfigurationValue.StringValue("Asia/Tokyo"))), ConfigurationTrace.empty)
    _with_configuration(configuration)(body)
  }
  private def _with_configuration(configuration: ResolvedConfiguration)(body: => Unit): Unit = {
    val config = RuntimeConfig.from(configuration)
    val core = ScopeContext(kind = ScopeKind.Runtime, name = "typed-json-envelope", parent = None,
      observabilitycontext = ExecutionContext.create().observability, httpdriveroption = Some(config.httpDriver)).core
    val runtime = new GlobalRuntimeContext(core = core, config = config, aliasResolver = AliasResolver.empty,
      runtimeMode = RunMode.Command, commandExecutionMode = None,
      runtimeVersion = org.goldenport.cncf.CncfVersion.current, subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = org.goldenport.cncf.CncfVersion.current, resolvedConfiguration = configuration)
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(runtime)
    try body finally GlobalRuntimeContext.current = previous
  }
}
