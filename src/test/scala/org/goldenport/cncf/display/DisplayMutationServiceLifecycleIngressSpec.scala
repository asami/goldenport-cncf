package org.goldenport.cncf.display

import io.circe.Json
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityRevisionTransport
import org.goldenport.cncf.spi.{ComponentSelector, SpiOperationSelector}
import org.goldenport.protocol.{Argument, Property, Switch}
import org.goldenport.protocol.handler.ingress.ArgsIngress
import org.goldenport.record.Record
import org.http4s.Method
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
object DisplayMutationServiceLifecycleIngressSpec {
  import DisplayMutationServiceFixture.*
  def arguments(session: Session, operation: DisplayMutation.Operation, payload: Json,
    authenticated: Boolean = true): Json = Json.fromFields(Vector(
    "resourceType" -> Json.fromString(resourceType), "screenId" -> Json.fromString(screenId), "payload" -> payload,
    "cncf.security.privilege" -> Json.fromString("user"), "principalId" -> Json.fromString("owner"),
    "cncf.security.authenticated" -> Json.fromString(authenticated.toString),
    "cncf.context.traceId" -> Json.fromString(observability.traceId.print),
    "cncf.context.correlationId" -> Json.fromString(observability.correlationId.get.print)) ++
    (if (operation == DisplayMutation.Operation.Create) Vector.empty else Vector(
      EntityRevisionTransport.observedRevisionPropertyName -> Json.fromString("7"))))

  trait AdapterVocabulary extends DisplayMutationServiceFixture.Vocabulary { self: org.scalatest.Informing =>
    def checkLifecycleIngressPreserved(session: Session, before: LifecycleSnapshot, execution: Option[Execution] = None): Unit = {
      session.log.conversions shouldBe Vector.empty
      session.log.validations shouldBe 0
      session.log.commands shouldBe 0
      session.log.idCalls shouldBe Vector.empty
      session.log.events.count(_.event == "find") shouldBe 0
      session.log.events.count(_.event == "capture") shouldBe 0
      val observers = execution.toVector.flatMap(_.lifecycleObservers) ++ session.lifecycleObservers
      observers.flatMap(_.creates) shouldBe Vector.empty
      observers.flatMap(_.deletes) shouldBe Vector.empty
      observers.flatMap(_.created) shouldBe Vector.empty
      observers.flatMap(_.deleted) shouldBe Vector.empty
      observers.map(_.writes).sum shouldBe 0
      checkLifecycleState(session, before)
    }
  }
}

final class DisplayMutationServiceLifecycleIngressSpec extends AnyWordSpec
    with DisplayMutationServiceLifecycleIngressSpec.AdapterVocabulary {
  import DisplayMutationServiceFixture.*
  import DisplayMutation.Operation
  import DisplayMutationServiceLifecycleIngressSpec.arguments
  private val _governed = afterWord("under docs/spec/display-mutation-service.md DLC-06/12, docs/design/display-mutation-service.md DLC-D01 and RULE.md")
  private def _bytes(text: String): Array[Byte] = text.getBytes(StandardCharsets.UTF_8)

  "Ordinary Display lifecycle ingress" should {
    "real HTTP and non-HTTP adapters" which _governed {
      "DLC-H1 carry actual POST create and DELETE delete plus GET typed descriptor through the UTF8 envelope" in {
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"a normal managed SQLite $target/$representation with supplied server identity and explicit authenticated owner/clock/full trace")
          val session = open(target, representation, backend = "sqlite", lifecycle = Some(LifecycleSetup()))
          try {
            val descriptorbefore = session.lifecycleSnapshot()
            When("actual Http4s GET resolves the immutable lifecycle descriptor")
            val descriptor = session.http(Array.emptyByteArray, Vector.empty, Method.GET, "describeMutation")
            Then("both lifecycle capabilities are inert supported metadata and no target or callback is exposed")
            descriptor.status shouldBe 200
            descriptor.data.hcursor.downField("capabilities").get[String]("create") shouldBe Right("supported")
            descriptor.data.hcursor.downField("capabilities").get[String]("delete") shouldBe Right("supported")
            descriptor.data.asObject.get.keys.toSet shouldBe Set("schema", "version", "componentId", "resourceType", "definition", "provenance", "capabilities", "availability", "fields")
            checkLifecycleState(session, descriptorbefore)
            for (create <- Vector(true, false)) {
              Given("a fully reset absent-create or revision-seven delete fixture and a complete independent pre-action state oracle")
              session.prepareLifecycle(create, 7L)
              val before = session.lifecycleSnapshot()
              val wanted = if (create) expectedLifecycleCreate(session, before, lifecycleFields)
                else expectedLifecycleDelete(session, before, 7L, true)
              val operation = if (create) Operation.Create else Operation.Delete
              When("the real Http4s app dispatches raw application/json UTF8 using POST without If-Match or DELETE with one strong observation")
              val result = lifecycleAdapter(session, operation, session.lifecyclePayload(operation), "http", 7L)
              Then("the ordinary commit and selected contextual Detail or reload-list share the complete independently expected business/native/default/context state")
              checkLifecycleAdapter(session, result, create, if (create) 1L else 7L, wanted)
              result.wire.get.hcursor.downField("execution").get[String]("interface-shape") shouldBe Right("json")
              session.lifecycleObservers.flatMap(_.creates) should have size (if (create) 1 else 0)
              session.lifecycleObservers.flatMap(_.deletes) should have size (if (create) 0 else 1)
              session.lifecycleObservers.map(_.writes).sum shouldBe 0
            }
          } finally session.close()
        }
      }

      "DLC-H2 reject physical observation, wrong methods, raw body smuggling, lexical and selector or provenance injection before effects" in {
        for (create <- Vector(true, false)) {
          Given("an actual SQLite HTTP lifecycle route with a real selected provider and raw body")
          val session = open(backend = "sqlite", lifecycle = Some(LifecycleSetup()))
          try {
            val operation = if (create) Operation.Create else Operation.Delete
            val name = if (create) "create" else "delete"
            val method = if (create) Method.POST else Method.DELETE
            val headers = if (create) Vector.empty[(String, String)] else Vector("If-Match" -> "\"revision-7\"")
            val raw = session.lifecyclePayload(operation)
            val invalidheaders = if (create) Vector(Vector("If-Match" -> ""), Vector("if-match" -> "\"revision-7\""),
              Vector("If-Match" -> "*"), Vector("If-Match" -> "\"revision-7\"", "IF-MATCH" -> "\"revision-7\""))
              else Vector(Vector.empty[(String, String)], Vector("If-Match" -> "\"revision-7\"", "If-Match" -> "\"revision-7\""),
                Vector("If-Match" -> "\"revision-7\"", "if-match" -> "\"revision-7\"")) ++
                Vector("", "W/\"revision-7\"", "*", "\"revision-7\", \"revision-8\"", "\"revision-07\"", "\"revision-0\"",
                  "\"revision-9223372036854775808\"").map(value => Vector("If-Match" -> value))
            val duplicates = Vector(raw.replace("\"schema\":", "\"schema\":\"cncf.display-mutation.v1\",\"schema\":"),
              raw.replace("\"screenId\":", "\"screenId\":\"capture-detail\",\"screenId\":"),
              raw.replace("\"producerId\":", "\"producerId\":\"duplicate\",\"producerId\":")) ++
              (if (create) Vector(raw.replace("\"sectionId\":", "\"sectionId\":\"main\",\"sectionId\":"),
                raw.replace("\"type\":\"text\"", "\"type\":\"text\",\"type\":\"text\""),
                raw.replace("\"text\":", "\"text\":\"duplicate\",\"text\":")) else Vector.empty)
            val lexemes = Vector("1.0", "1e0", "9223372036854775808", "\"1\"").map(value => raw.replace("\"version\":1", "\"version\":" + value)) ++
              (if (create) Vector("+1", "01", "1.0E+0").map(value => session.lifecyclePayload(operation,
                Vector(field("title", DisplayValue.Text("Valid")), field("number", DisplayValue.Number(BigDecimal("1")))))
                .replace("\"number\":\"1\"", "\"number\":\"" + value + "\"")) ++
                Vector("2026-10-15T02:03:04.000Z", "2026-10-15T02:03:04+00:00").map(value => session.lifecyclePayload(operation,
                  Vector(field("title", DisplayValue.Text("Valid")), field("instant", DisplayValue.Timestamp(java.time.Instant.parse("2026-10-15T02:03:04Z")))))
                  .replace("2026-10-15T02:03:04Z", value)) else Vector.empty)
            val injections = Vector("target", "callback", "commandName", "observedRevision", "expectedRevision", "revision").map(key =>
              raw.dropRight(1) + ",\"" + key + "\":\"attacker\"}") ++
              Vector(raw.replace("normal-update-reference", "foreign-resource"), raw.replace("typed-normal-component-", "foreign-source-")) ++
              (if (create) Vector(session.lifecyclePayload(operation, Vector(field("id", DisplayValue.Text(session.component.id.value)))),
                raw.dropRight(1) + ",\"resourceId\":\"" + session.component.id.value + "\"}")
                else Vector(raw.dropRight(1) + ",\"fields\":[]} "))
            val calls = invalidheaders.map(values => () => session.http(_bytes(raw), values, method, name)) ++
              Vector(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.PATCH, Method.HEAD, Method.OPTIONS)
                .filterNot(_ == method).map(value => () => session.http(_bytes(raw), headers, value, name)) ++
              Vector(() => session.http(Array.emptyByteArray, Vector.empty, Method.PATCH, "describeMutation"),
                () => session.http(_bytes(raw), headers, method, name, contentType = "text/plain"),
                () => session.http(_bytes(raw), headers :+ ("Content-Type" -> "application/json"), method, name),
                () => session.http(Array(0xc3.toByte, 0x28.toByte), headers, method, name),
                () => session.http(Array.emptyByteArray, headers, method, name)) ++
              Vector("resourceType" -> resourceType, "screenId" -> screenId, "payload" -> raw,
                EntityRevisionTransport.observedRevisionPropertyName -> "7",
                EntityRevisionTransport.observedRevisionPropertyName.toUpperCase(java.util.Locale.ROOT) -> "7").map(pair =>
                () => session.http(_bytes(raw), headers, method, name, extra = Vector(pair))) ++
              (duplicates ++ lexemes ++ injections).map(payload => () => session.http(_bytes(payload), headers, method, name))
            calls.foreach { call =>
              Given("the exact reset selected/unrelated logical/native/body/resident/View state before this rejected physical request")
              session.prepareLifecycle(create, 7L)
              val before = session.lifecycleSnapshot()
              When("the actual Http4s app dispatches the selected malformed raw request")
              val response = call()
              Then("structured HTTP rejection has no command receipt, converter, provider attempt, read or state change")
              response.status should be >= 400
              checkLifecycleIngressPreserved(session, before)
            }
          } finally session.close()
        }
      }

      "DLC-H4 reject actual authenticated HEAD descriptor before availability or lifecycle effects" in {
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"DLC-06/DLC-D01: a normal managed SQLite $target/$representation with authenticated descriptor selectors and independently reset full state")
          val session = open(target, representation, backend = "sqlite", lifecycle = Some(LifecycleSetup()))
          try {
            session.prepareLifecycle(false, 7L)
            val before = session.lifecycleSnapshot()
            When("actual Http4s HEAD requests the GET-only describeMutation operation")
            val response = session.http(Array.emptyByteArray, Vector.empty, Method.HEAD, "describeMutation")
            Then("HTTP 400 suppresses every response byte before descriptor availability, authorization, conversion or lifecycle effects and preserves complete state")
            response.status shouldBe 400
            response.text shouldBe ""
            response.bytes shouldBe Vector.empty
            session.log.events.count(_.event == "availability") shouldBe 0
            session.log.authorizations shouldBe Vector.empty
            checkLifecycleIngressPreserved(session, before)
          } finally session.close()
        }
      }

      "DLC-H3 preserve actual ArgsIngress MCP and installed SPI lifecycle correspondence and reject non-String duplicate or extra arguments" in {
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"an ordinary SQLite $target/$representation with server-supplied identity for every actual adapter")
          val session = open(target, representation, backend = "sqlite", lifecycle = Some(LifecycleSetup()))
          try {
            for (adapter <- Vector("cli", "mcp", "spi"); create <- Vector(true, false)) {
              Given(s"complete independently reset state for signed-in $adapter create=$create and then the same unauthenticated owner")
              val operation = if (create) Operation.Create else Operation.Delete
              session.prepareLifecycle(create, 7L)
              val before = session.lifecycleSnapshot()
              val wanted = if (create) expectedLifecycleCreate(session, before, lifecycleFields)
                else expectedLifecycleDelete(session, before, 7L, true)
              When("the actual adapter supplies exactly one raw payload String and operation-specific canonical observation")
              val positive = lifecycleAdapter(session, operation, session.lifecyclePayload(operation), adapter, 7L)
              Then("normal authorization, current commit, business/default/context and full state agree with the same independent oracle")
              checkLifecycleAdapter(session, positive, create, if (create) 1L else 7L, wanted)
              Given("the identical canonical owner and reset state without authentication")
              session.prepareLifecycle(create, 7L)
              val deniedbefore = session.lifecycleSnapshot()
              When("the same real adapter carries canonical false authentication")
              val rejected = lifecycleAdapter(session, operation, session.lifecyclePayload(operation), adapter, 7L, authenticated = false)
              Then("authentication rejects before canonical factory authorization or any downstream effect")
              rejected.status should be >= 400
              rejected.value shouldBe None
              session.log.authorizations shouldBe Vector.empty
              checkLifecycleIngressPreserved(session, deniedbefore, rejected.execution)
            }
            for (create <- Vector(true, false)) {
              val operation = if (create) Operation.Create else Operation.Delete
              val name = if (create) "create" else "delete"
              val raw = session.lifecyclePayload(operation)
              val args = Vector("display", name, "--resourceType=" + resourceType, "--screenId=" + screenId, "--payload=" + raw) ++
                (if (create) Vector.empty else Vector("--" + EntityRevisionTransport.observedRevisionPropertyName + "=7"))
              val request = ArgsIngress().encode(session.component.protocol.services, args.toArray).TAKE.copy(component = Some(session.component.name))
              val invalid = Vector(request.copy(arguments = List(Argument("extra", "x"))),
                request.copy(switches = List(Switch("extra", true, None))),
                request.copy(properties = request.properties :+ Property("payload", raw, None)),
                request.copy(properties = request.properties :+ Property("extra", "x", None))) ++
                Vector[Any](Record.empty, Vector("x"), 1L, false, null).map(value => request.copy(properties = request.properties.map { property =>
                  if (property.name == "payload") property.copy(value = value) else property
                })) ++ (if (create) Vector(EntityRevisionTransport.observedRevisionPropertyName,
                  EntityRevisionTransport.observedRevisionPropertyName.toUpperCase(java.util.Locale.ROOT)).map(key =>
                    request.copy(properties = request.properties :+ Property(key, "7", None)))
                else Vector("0", "07", "+7", "7.0", "9223372036854775808").map(value => request.copy(properties = request.properties.map { property =>
                  if (property.name == EntityRevisionTransport.observedRevisionPropertyName) property.copy(value = value) else property
                })) ++ Vector(request.copy(properties = request.properties.filterNot(_.name == EntityRevisionTransport.observedRevisionPropertyName)),
                  request.copy(properties = request.properties :+ Property(EntityRevisionTransport.observedRevisionPropertyName, "7", None))))
              invalid.foreach { candidate =>
                Given("a reset complete baseline and the actual ArgsIngress request with one admitted negative mutation")
                session.prepareLifecycle(create, 7L)
                val before = session.lifecycleSnapshot()
                When("the ordinary resolver receives duplicate, extra, native-kind or noncanonical lifecycle arguments")
                val result = session.execute(candidate)
                Then("structured request admission rejects with zero canonical provider or domain effects and complete preservation")
                checkLifecyclePreserved(session, before, result)
                session.log.validations shouldBe 0
              }
              val badkinds = Vector(Json.obj("x" -> Json.fromInt(1)), Json.arr(Json.fromInt(1)), Json.fromInt(7), Json.False, Json.Null)
              val names = Vector("resourceType", "screenId", "payload") ++ (if (create) Vector.empty else Vector(EntityRevisionTransport.observedRevisionPropertyName))
              for (member <- names; kind <- badkinds) {
                Given(s"a reset full state and original MCP JSON $kind kind for declared String input $member")
                session.prepareLifecycle(create, 7L)
                val before = session.lifecycleSnapshot()
                val candidate = arguments(session, operation, Json.fromString(raw)).mapObject(_.add(member, kind))
                When("the normally published actual lifecycle tools/call checks original JSON kinds before generic conversion")
                val response = lifecycleMcp(session, name, candidate)
                Then("invalid params names the String member and no operation or provider executes")
                response.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
                response.hcursor.downField("error").get[String]("message") shouldBe Right("invalid params: " + member + " must be a string")
                response.hcursor.downField("result").focus shouldBe None
                checkLifecycleIngressPreserved(session, before)
              }
              val spivals = Vector[(String, Any)]("resourceType" -> resourceType, "screenId" -> screenId, "payload" -> raw) ++
                (if (create) Vector.empty else Vector(EntityRevisionTransport.observedRevisionPropertyName -> "7"))
              val spicandidates = Vector[Any](Record.empty, Vector("x"), 7L, false).flatMap { value =>
                val payload = spivals.map { case (key, current) => key -> (if (key == "payload") value else current) }
                Vector(payload) ++ (if (create) Vector.empty else Vector(spivals.map { case (key, current) =>
                  key -> (if (key == EntityRevisionTransport.observedRevisionPropertyName) value else current)
                }))
              } ++ Vector(spivals :+ ("payload" -> raw), spivals :+ ("extra" -> "x")) ++
                (if (create) Vector(spivals :+ (EntityRevisionTransport.observedRevisionPropertyName -> "7"),
                  spivals :+ (EntityRevisionTransport.observedRevisionPropertyName.toUpperCase(java.util.Locale.ROOT) -> "7"))
                else Vector(spivals.filterNot(_._1 == EntityRevisionTransport.observedRevisionPropertyName),
                  spivals :+ (EntityRevisionTransport.observedRevisionPropertyName -> "7")))
              spicandidates.foreach { values =>
                Given("a reset full state and the installed SPI selector with native-kind duplicate missing or extra lifecycle metadata")
                session.prepareLifecycle(create, 7L)
                val spibefore = session.lifecycleSnapshot()
                val active = session.execution()
                given ExecutionContext = active.context
                val record = org.goldenport.record.Record(values.map { case (key, value) =>
                  org.goldenport.record.Field(key, org.goldenport.record.Field.Value.Single(value))
                })
                When("the real SPI invoker selects the installed ordinary lifecycle operation with the original typed fields")
                val spi = session.subsystem.spiInvoker.invoke(spiContract, SpiOperationSelector(name, Some("display")), record,
                  ComponentSelector(component = Some(session.component.name)))
                Then("strict native payload and observation admission rejects without String normalization or a canonical writer")
                spi shouldBe a[Consequence.Failure[?]]
                checkLifecycleIngressPreserved(session, spibefore)
                active.lifecycleObservers.flatMap(_.creates) shouldBe Vector.empty
                active.lifecycleObservers.flatMap(_.deletes) shouldBe Vector.empty
                active.lifecycleObservers.map(_.writes).sum shouldBe 0
              }
            }
          } finally session.close()
        }
      }
    }
  }
}
