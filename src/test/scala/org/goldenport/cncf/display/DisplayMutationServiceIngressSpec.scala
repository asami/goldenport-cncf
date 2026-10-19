package org.goldenport.cncf.display

import io.circe.Json
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityRevisionTransport
import org.goldenport.cncf.mcp.{McpJsonRpcAdapter, McpJsonRpcOutcome, McpProtocolRevision}
import org.goldenport.cncf.spi.{ComponentSelector, SpiOperationSelector}
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.security.*
import org.goldenport.cncf.unitofwork.{UnitOfWorkAuthorization, UnitOfWorkTermination}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.handler.ingress.ArgsIngress
import org.goldenport.record.Record
import org.http4s.Method
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 19, 2026
 */
final class DisplayMutationServiceIngressSpec extends AnyWordSpec with DisplayMutationServiceFixture.Vocabulary {
  import DisplayMutationServiceFixture.*
  private val _changes = Vector(field("title", DisplayValue.Text("HTTP 日本 Ω")))
  private def _bytes(text: String): Array[Byte] = text.getBytes(StandardCharsets.UTF_8)
  private def _mcp_outcome(session: Session, payload: Json, observed: Json = Json.fromString("1"), authenticated: Boolean = true): McpJsonRpcOutcome = {
    val arguments = Json.obj("resourceType" -> Json.fromString(resourceType), "screenId" -> Json.fromString(screenId),
      "payload" -> payload, EntityRevisionTransport.observedRevisionPropertyName -> observed,
      "cncf.security.privilege" -> Json.fromString("user"), "principalId" -> Json.fromString("owner"),
      "cncf.security.authenticated" -> Json.fromString(authenticated.toString),
      "cncf.context.traceId" -> Json.fromString(observability.traceId.print),
      "cncf.context.correlationId" -> Json.fromString(observability.correlationId.get.print))
    _mcp_call(session, "update", arguments)
  }
  private def _mcp_call(session: Session, operation: String, arguments: Json): McpJsonRpcOutcome = {
    val request = Json.obj("jsonrpc" -> Json.fromString("2.0"), "id" -> Json.fromString(operation),
      "method" -> Json.fromString("tools/call"), "params" -> Json.obj("name" -> Json.fromString(session.component.name + ".display." + operation),
        "arguments" -> arguments))
    new McpJsonRpcAdapter(session.subsystem).handle(request.noSpaces, Some(McpProtocolRevision.PREFERRED.print))
  }
  private def _mcp_json(session: Session, payload: Json, observed: Json = Json.fromString("1"), authenticated: Boolean = true): Json =
    _mcp_outcome(session, payload, observed, authenticated).responseBody.get
  private def _mcp_payload(value: Json): Json = {
    val text = value.hcursor.downField("result").downField("content").downN(0).get[String]("text").toOption.get
    val parsed = io.circe.parser.parse(text).toOption.get
    parsed.hcursor.downField("data").focus.getOrElse(parsed)
  }

  "Ordinary Display mutation adapters" should {
    "actual Http4s raw ingress" which {
      "C3-H1 carry PUT raw JSON and GET descriptor through the existing UTF8 JSON envelope" in {
        Given("a normal SQLite component with canonical managed persistence and a typed native patch")
        val session = open(backend = "sqlite")
        try {
          val before = session.rows()
          val tables = session.physical()
          val wanted = session.expected(before, tables, _changes, true, 2L)
          When("the real in-process Http4s app receives GET describeMutation and PUT update.json with one strong physical If-Match")
          val descriptor = session.http(Array.emptyByteArray, headers = Vector.empty, method = Method.GET, operation = "describeMutation")
          session.log.reset()
          val result = session.http(_bytes(session.payload(_changes)))
          val after = session.rows()
          val physical = session.physical()
          Then("the framework-owned envelope contains authoritative committed business JSON and only the selected contextual Browser reloads")
          withClue(s"descriptor HTTP ${descriptor.status}: ${descriptor.text}; update HTTP ${result.status}: ${result.text}") {
            descriptor.status shouldBe 200
          }
          descriptor.data.hcursor.get[String]("schema") shouldBe Right("cncf.display-mutation-descriptor.v1")
          withClue(s"update HTTP ${result.status}: ${result.text}") {
            result.status shouldBe 200
          }
          result.contentType shouldBe Some("application/json")
          result.data.hcursor.get[String]("outcome") shouldBe Right("updated")
          result.data.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
          result.data.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
          result.json.hcursor.downField("execution").get[String]("interface-shape") shouldBe Right("json")
          new String(result.bytes.toArray, StandardCharsets.UTF_8) shouldBe result.text
          checkRows(after, wanted.rows)
          physical shouldBe wanted.physical
          checkCompleteState(session, wanted)
          session.log.events.count(_.event == "find") shouldBe 1
          val authuow = session.log.authorizations.head._2
          session.log.events.filter(_.event == "find").foreach(event => (event.uow eq authuow) shouldBe true)
        } finally session.close()
      }

      "C3-H2 reject duplicate members at every nested mutation object and noncanonical native lexical forms before effects" in {
        Given("a real HTTP route retaining raw payload bytes instead of parsing through a Record map")
        val session = open(backend = "sqlite")
        try {
          val raw = session.payload(_changes)
          val duplicate = Vector(
            raw.replace("\"schema\":", "\"schema\":\"cncf.display-mutation.v1\",\"schema\":"),
            raw.replace("\"screenId\":", "\"screenId\":\"capture-detail\",\"screenId\":"),
            raw.replace("\"producerId\":", "\"producerId\":\"duplicate\",\"producerId\":"),
            raw.replace("\"sectionId\":", "\"sectionId\":\"main\",\"sectionId\":"),
            raw.replace("\"type\":\"text\"", "\"type\":\"text\",\"type\":\"text\""),
            raw.replace("\"text\":", "\"text\":\"duplicate\",\"text\":"))
          val lexical = Vector("1.0", "1e0", "9223372036854775808", "\"1\"").map(value => raw.replace("\"version\":1", "\"version\":" + value)) ++
            Vector("+1", "01", "1.0E+0").map(value => session.payload(Vector(field("number", DisplayValue.Number(BigDecimal("1")))))
              .replace("\"number\":\"1\"", "\"number\":\"" + value + "\"")) ++
            Vector("2026-10-15T02:03:04.000Z", "2026-10-15T02:03:04+00:00").map(value =>
              session.payload(Vector(field("instant", DisplayValue.Timestamp(java.time.Instant.parse("2026-10-15T02:03:04Z")))))
                .replace("2026-10-15T02:03:04Z", value))
          (duplicate ++ lexical).foreach { payload =>
            Given("complete independent selected/unrelated state and a malformed raw nested or scalar candidate")
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            session.log.reset()
            When("actual Http4s PUT hands the unchanged candidate to the strict mutation decoder")
            val response = session.http(_bytes(payload))
            Then("the candidate fails before domain/write/read and every complete state observation remains unchanged")
            response.status should be >= 400
            checkPreserved(session, before, tables, resident)
          }
        } finally session.close()
      }

      "C3-H3 preserve ordered physical header evidence and reject header, method, body and query smuggling" in {
        Given("the real HTTP app and canonical raw update payload")
        val session = open(backend = "sqlite")
        try {
          val invalid = Vector(Vector.empty[(String, String)], Vector("If-Match" -> "\"revision-1\"", "If-Match" -> "\"revision-1\""),
            Vector("If-Match" -> "\"revision-1\"", "if-match" -> "\"revision-1\"")) ++
            Vector("", "W/\"revision-1\"", "*", "\"revision-1\", \"revision-2\"", "\"revision-01\"", "\"revision-0\"",
              "\"revision-9223372036854775808\"").map(value => Vector("If-Match" -> value))
          invalid.foreach { headers =>
            Given("a pre-action complete state snapshot and missing, duplicate or malformed physical If-Match")
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            session.log.reset()
            When("the actual PUT receives ordered physical headers")
            val response = session.http(_bytes(session.payload(_changes)), headers)
            Then("no normalized property can replace the physical observation and no effect occurs")
            response.status should be >= 400
            checkPreserved(session, before, tables, resident)
          }
          val badrequests = Vector(
            () => session.http(_bytes(session.payload(_changes)), Vector.empty, extra = Vector(EntityRevisionTransport.observedRevisionPropertyName -> "1")),
            () => session.http(_bytes(session.payload(_changes)), method = Method.POST),
            () => session.http(_bytes(session.payload(_changes)), contentType = "text/plain"),
            () => session.http(Array(0xc3.toByte, 0x28.toByte)),
            () => session.http(_bytes(session.payload(_changes)), extra = Vector("resourceType" -> resourceType)),
            () => session.http(_bytes(session.payload(_changes)), extra = Vector("screenId" -> screenId)),
            () => session.http(_bytes(session.payload(_changes)), extra = Vector("payload" -> "forged-query-body")))
          badrequests.foreach { request =>
            Given("a complete independent baseline before a method, content type, malformed UTF8 or duplicate-selector candidate")
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            session.log.reset()
            When("the actual Http4s app dispatches the invalid candidate")
            val response = request()
            Then("structured ingress rejection leaves domain, canonical persistence and contextual reads untouched")
            response.status should be >= 400
            checkPreserved(session, before, tables, resident)
          }
        } finally session.close()
      }
    }

    "authenticated Aggregate adapter correspondence" which {
      "C3-H7 commit signed-in root and alias updates and reject the unauthenticated exact owner without effects" in {
        for (adapter <- Vector("http", "mcp"); target <- Vector(Target.Aggregate, Target.Alias); representation <- representations) {
          Given(s"a real SQLite $target/$representation through $adapter with a signed-in ControlledTest owner and an independent full-state oracle")
          val session = open(target, representation, backend = "sqlite")
          try {
            val before = session.rows()
            val tables = session.physical()
            val wanted = session.expected(before, tables, _changes, true, 2L)
            val authorization = UnitOfWorkAuthorization("aggregate", Some(session.component.ownerName), Some(session.component.rootName),
              Some(session.component.id), "command:changeFields", Some(CmlOperationAccess("authenticated_only")),
              Some(session.component.name), Some(session.component.name), Vector(session.component.ownerName),
              EntityAccessMode.UserPermission, Some(ServiceOperationModel.BusinessService), Some(EntityOperationKind.Task), Some(EntityApplicationDomain.Business))
            val authentication = conclusion(Consequence.securityAuthenticationRequired[Unit](
              s"Authenticated user is required: ${session.component.name}.display.update"))
            session.log.reset()
            When("the actual raw Http4s PUT or published MCP tools/call carries the owner, true authentication and canonical full trace values")
            val result = if (adapter == "http") {
              val response = session.http(_bytes(session.payload(_changes)), authenticated = true)
              Then("the actual signed-in HTTP command is accepted")
              response.status shouldBe 200
              response.data
            } else _mcp_payload(_mcp_json(session, Json.fromString(session.payload(_changes)), authenticated = true))
            Then("one real captured command commits and reloads the selected Detail with exact authorization, clock and operational identity")
            result.hcursor.get[String]("outcome") shouldBe Right("updated")
            result.hcursor.downField("resource").get[String]("entityId") shouldBe Right(session.component.id.value)
            result.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
            result.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
            result.hcursor.downField("projection").get[String]("type") shouldBe Right("ready")
            session.log.authorizations.map(_._1) shouldBe Vector(authorization, authorization)
            val uow = session.log.authorizations.head._2
            uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
            uow.lastCommitResult shouldBe Some(Consequence.unit)
            session.log.events.count(_.event == "aggregate") shouldBe 1
            session.log.events.count(_.event == "find") shouldBe 1
            session.log.commands shouldBe 1
            session.log.validations shouldBe 1
            session.log.events.foreach { event =>
              (event.uow eq uow) shouldBe true
              event.context.clock.instant() shouldBe fixedInstant
              event.context.observability.traceId.value shouldBe observability.traceId.value
              event.context.observability.correlationId.map(_.value) shouldBe observability.correlationId.map(_.value)
              event.context.security.principal.id.value shouldBe "owner"
              SecuritySubject.from(event.context.security).isAuthenticated shouldBe true
            }
            checkCompleteState(session, wanted)

            Given("the complete committed state, exact same owner and current revision two, with authentication explicitly false")
            val preserved = session.rows()
            val physical = session.physical()
            val resident = session.resident
            session.log.reset()
            When("the actual adapter submits another update using false authentication and the current observation")
            if (adapter == "http") {
              val rejected = session.http(_bytes(session.payload(_changes)), headers = Vector("If-Match" -> "\"revision-2\""), authenticated = false)
              Then("the HTTP structured authentication rejection preserves its canonical status and message")
              rejected.status shouldBe authentication.status.webCode.code
              rejected.json.hcursor.downField("error").get[String]("message") shouldBe Right(authentication.displayMessage)
            } else {
              val rejected = _mcp_json(session, Json.fromString(session.payload(_changes)), Json.fromString("2"), authenticated = false)
              Then("the MCP tool failure retains the authentication Conclusion projection")
              rejected.hcursor.downField("result").get[Boolean]("isError") shouldBe Right(true)
              rejected.hcursor.downField("result").downField("content").downN(0).get[String]("text") shouldBe Right(authentication.show)
            }
            Then("the unauthenticated exact owner reaches no converter, domain command, provider mutation or contextual read and every state value is preserved")
            session.log.authorizations shouldBe Vector.empty
            session.log.events.count(_.event == "aggregate") shouldBe 0
            checkPreserved(session, preserved, physical, resident)
          } finally session.close()
        }
      }
    }

    "ordinary non-HTTP adapters" which {
      "C3-H4 preserve payload Strings and exact observation through actual ArgsIngress and published MCP tools/call" in {
        for (adapter <- Vector("cli", "mcp")) {
          Given(s"a normally installed SQLite component and the actual $adapter adapter")
          val session = open(backend = "sqlite")
          try {
            val payload = session.payload(_changes)
            val before = session.rows(); val tables = session.physical()
            val wanted = session.expected(before, tables, _changes, true, 2L)
            When("the ordinary adapter admits the raw JSON String and invokes the same registered command")
            val result = if (adapter == "cli") {
              val request = ArgsIngress().encode(session.component.protocol.services, Array("display", "update",
                "--resourceType=" + resourceType, "--screenId=" + screenId, "--payload=" + payload,
                "--" + EntityRevisionTransport.observedRevisionPropertyName + "=1")).TAKE.copy(component = Some(session.component.name))
              json(session.execute(request).result)
            } else {
              val catalog = new McpJsonRpcAdapter(session.subsystem).handle(
                "{\"jsonrpc\":\"2.0\",\"id\":\"catalog\",\"method\":\"tools/list\",\"params\":{}}",
                Some(McpProtocolRevision.PREFERRED.print)).responseBody.get
              catalog.hcursor.downField("result").downField("tools").focus.get.asArray.get.map(_.hcursor.get[String]("name").toOption.get) should contain(session.component.name + ".display.update")
              _mcp_payload(_mcp_json(session, Json.fromString(payload)))
            }
            val after = session.rows(); val physical = session.physical()
            Then("the real adapter returns the same authoritative result and complete prescribed persistence state")
            result.hcursor.get[String]("outcome") shouldBe Right("updated")
            result.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
            checkRows(after, wanted.rows)
            physical shouldBe wanted.physical
            checkCompleteState(session, wanted)
            session.log.validations shouldBe 1
            session.log.events.count(_.event == "find") shouldBe 1
          } finally session.close()
        }
        Given("a published MCP update tool and strict payload duplicated inside its String argument")
        val session = open(backend = "sqlite")
        try {
          val before = session.rows(); val tables = session.physical(); val resident = session.resident
          val raw = session.payload(_changes).replace("\"version\":1", "\"version\":1,\"version\":1")
          When("MCP tools/call sends the duplicate-preserving raw String")
          val duplicated = _mcp_json(session, Json.fromString(raw))
          Then("the strict String decoder returns a tool failure without changing the complete pre-action state")
          duplicated.hcursor.downField("error").succeeded shouldBe false
          duplicated.hcursor.downField("result").get[Boolean]("isError") shouldBe Right(true)
          checkPreserved(session, before, tables, resident)
          Given("the same independently declared complete baseline and a JSON object in place of the raw String")
          session.log.reset()
          When("the actual MCP adapter receives the object payload")
          val outcome = _mcp_outcome(session, io.circe.parser.parse(session.payload(_changes)).toOption.get)
          val objectpayload = outcome.responseBody.get
          Then("the adapter rejects the JSON kind as exact invalid params before normal execution")
          outcome shouldBe a[McpJsonRpcOutcome.ProtocolFailure]
          objectpayload.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
          objectpayload.hcursor.downField("error").get[String]("message") shouldBe Right("invalid params: payload must be a string")
          objectpayload.hcursor.downField("result").succeeded shouldBe false
          session.log.events shouldBe Vector.empty
          checkPreserved(session, before, tables, resident)
        } finally session.close()
      }

      "C3-H8 reject every non-String JSON kind for each declared update and descriptor String input before effects" in {
        Given("normally published MCP update and descriptor Operations with their server-declared String inputs")
        val session = open(backend = "sqlite")
        try {
          val adapter = new McpJsonRpcAdapter(session.subsystem)
          val catalog = adapter.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":\"catalog\",\"method\":\"tools/list\",\"params\":{}}",
            Some(McpProtocolRevision.PREFERRED.print)).responseBody.get
          val names = catalog.hcursor.downField("result").downField("tools").focus.get.asArray.get
            .map(_.hcursor.get[String]("name").toOption.get)
          names should contain(session.component.name + ".display.update")
          names should contain(session.component.name + ".display.describeMutation")
          val selectors = Vector("resourceType" -> Json.fromString(resourceType), "screenId" -> Json.fromString(screenId))
          val security = Vector("cncf.security.privilege" -> Json.fromString("user"), "principalId" -> Json.fromString("owner"),
            "cncf.security.authenticated" -> Json.fromString("true"))
          val kinds = Vector(Json.obj("value" -> Json.fromString("private-kind")), Json.arr(Json.fromString("private-kind")),
            Json.fromInt(1), Json.True, Json.Null)
          for (operation <- Vector("update", "describeMutation")) {
            val declared = selectors ++ (if (operation == "update") Vector("payload" -> Json.fromString(session.payload(_changes)),
              EntityRevisionTransport.observedRevisionPropertyName -> Json.fromString("1")) else Vector.empty)
            for ((member, _) <- declared; kind <- kinds) {
              Given(s"the complete independent pre-action state and only $operation.$member replaced by ${kind.noSpaces}")
              val before = session.rows(); val tables = session.physical(); val resident = session.resident
              val arguments = Json.obj((declared.map { case (name, value) => name -> (if (name == member) kind else value) } ++ security)*)
              session.log.reset()
              When("published tools/call inspects the original JSON argument kind before String normalization")
              val outcome = _mcp_call(session, operation, arguments)
              val response = outcome.responseBody.get
              Then("the exact member-specific invalid-params failure has no result or downstream effect")
              outcome shouldBe a[McpJsonRpcOutcome.ProtocolFailure]
              response.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
              response.hcursor.downField("error").get[String]("message") shouldBe Right(s"invalid params: $member must be a string")
              response.hcursor.downField("result").succeeded shouldBe false
              session.log.authorizations shouldBe Vector.empty
              session.log.events shouldBe Vector.empty
              checkPreserved(session, before, tables, resident)
            }
          }
          Given("the descriptor's unchanged valid String selectors and the full independent baseline")
          val before = session.rows(); val tables = session.physical(); val resident = session.resident
          session.log.reset()
          When("the actual MCP descriptor receives those Strings unchanged")
          val described = _mcp_call(session, "describeMutation", Json.obj((selectors ++ security)*))
          Then("normal descriptor execution succeeds and leaves persistence, domain commands and contextual reads untouched")
          described shouldBe a[McpJsonRpcOutcome.Response]
          _mcp_payload(described.responseBody.get).hcursor.get[String]("schema") shouldBe Right("cncf.display-mutation-descriptor.v1")
          checkPreserved(session, before, tables, resident)
        } finally session.close()
      }

      "C3-H5 invoke the same command through the real registered SpiInvoker contract" in {
        Given("a normal registered alias Aggregate, matching SPI contract and a complete independent pre-action state oracle")
        val session = open(Target.Alias)
        try {
          val before = session.rows(); val wanted = session.expected(before, session.physical(), _changes, true, 2L)
          val active = session.execution()
          given ExecutionContext = active.context
          val request = Record.dataAuto("resourceType" -> resourceType, "screenId" -> screenId,
            "payload" -> session.payload(_changes), EntityRevisionTransport.observedRevisionPropertyName -> "1")
          When("SpiInvoker resolves the registered provider and invokes display.update through ordinary Operation execution")
          val result = active.observeAction {
            session.subsystem.spiInvoker.invoke(spiContract, SpiOperationSelector("update", Some("display")), request,
              ComponentSelector(component = Some(session.component.name)))
          }
          val after = session.rows()
          Then("the ordinary SPI Record response retains the actual receipt and one canonical command, commit and contextual reload")
          result.toOption.get.getString("outcome") shouldBe Some("updated")
          result.toOption.get.getAny("retryCommand") shouldBe Some(false)
          active.attempts shouldBe 1
          session.log.commands shouldBe 1
          session.log.events.count(_.event == "find") shouldBe 1
          session.log.events.filter(_.event == "find").foreach(event => (event.uow eq active.uow) shouldBe true)
          (active.uow eq active.callerUnitOfWork) shouldBe false
          (active.uow.executionContext eq active.context) shouldBe false
          (active.uow.executionContext.unitOfWork eq active.uow) shouldBe true
          (active.context.unitOfWork eq active.callerUnitOfWork) shouldBe true
          (active.callerUnitOfWork.executionContext eq active.context) shouldBe true
          active.callerUnitOfWork.lastCommitTermination shouldBe active.callerCommitTerminationBeforeExecution
          active.callerUnitOfWork.lastCommitResult shouldBe active.callerCommitResultBeforeExecution
          active.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
          active.uow.lastCommitResult shouldBe Some(Consequence.unit)
          checkRows(after, wanted.rows)
          checkCompleteState(session, wanted)
        } finally session.close()
      }

      "C3-H6 reject foreign selections, raw targets and noncanonical non-HTTP observations before effects" in {
        Given("a selected normal route with exact String selector and payload requirements")
        val session = open()
        try {
          val valid = session.request(_changes)
          val candidates = Vector(
            valid.copy(component = Some("org.goldenport.cncf.test.Unknown")),
            valid.copy(properties = valid.properties :+ Property("target", "private-store", None)),
            valid.copy(properties = valid.properties :+ Property("screenId", screenId, None)),
            valid.copy(properties = valid.properties.filterNot(_.name == "payload"))) ++
            Vector("0", "01", "1.0", "1e0", "9223372036854775808").map(value => valid.copy(properties = valid.properties.map(property =>
              if (property.name == EntityRevisionTransport.observedRevisionPropertyName) Property(property.name, value, None) else property)))
          candidates.foreach { request =>
            Given("the full independent state immediately before an invalid non-HTTP request")
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            session.log.reset()
            When("normal subsystem resolution and bound input admission receive the candidate")
            val rejected = session.execute(request)
            Then("the structured rejection is redacted and has zero domain, provider or Browser effects")
            rejected.result shouldBe a[Consequence.Failure[?]]
            conclusion(rejected.result).show should not include "private-store"
            rejected.execution.attempts shouldBe 0
            checkPreserved(session, before, tables, resident)
          }
        } finally session.close()
      }
    }
  }
}
