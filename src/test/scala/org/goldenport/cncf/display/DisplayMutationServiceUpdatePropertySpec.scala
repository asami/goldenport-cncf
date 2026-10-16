package org.goldenport.cncf.display

import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{EntityRevisionRepresentation, EntityRevisionTransport}
import org.goldenport.cncf.mcp.{McpJsonRpcAdapter, McpProtocolRevision}
import org.goldenport.cncf.spi.{ComponentSelector, SpiOperationSelector}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Property
import org.goldenport.protocol.handler.ingress.ArgsIngress
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
object DisplayMutationServiceUpdatePropertySpec {
  import DisplayMutationServiceFixture.*
  enum Mode { case Current, Changed, StaleEqual, StaleChanged }
  enum Veto { case Factory, Readonly, Managed, Duplicate, Tampered }
  final case class Sample(text: String, coefficient: Long, scale: Int, nanos: Int, mask: Int,
    target: Target, representation: EntityRevisionRepresentation, mode: Mode, veto: Veto,
    clear: Boolean, maximum: Boolean, adapter: Int)
  final case class AdapterResult(value: Option[Json], error: Option[Conclusion], wire: Option[Json], status: Int)
  private val _characters = Vector('a', 'Z', '0', '日', '本', 'Ω', 'α', '\ud800', '\udfff')
  private val _text = for { length <- Gen.chooseNum(0, 18); characters <- Gen.listOfN(length, Gen.oneOf(_characters)) } yield characters.mkString
  val samples: Gen[Sample] = for {
    text <- _text; coefficient <- Gen.chooseNum(-1000000L, 1000000L); scale <- Gen.chooseNum(0, 12)
    nanos <- Gen.chooseNum(0, 999999999); mask <- Gen.chooseNum(1, 63)
    target <- Gen.oneOf(Target.values.toVector); representation <- Gen.oneOf(representations)
    mode <- Gen.oneOf(Mode.values.toVector); veto <- Gen.oneOf(Veto.values.toVector)
    clear <- Gen.oneOf(false, true); maximum <- Gen.oneOf(false, true); adapter <- Gen.chooseNum(0, 3)
  } yield Sample(text, coefficient, scale, nanos, mask, target, representation, mode, veto, clear, maximum, adapter)
  given Shrink[Sample] = Shrink { sample =>
    val text = if (sample.text.isEmpty) Stream.empty else Stream(sample.copy(text = sample.text.take(sample.text.length / 2)))
    val mask = if (Integer.bitCount(sample.mask) == 1) Stream.empty else Stream(sample.copy(mask = sample.mask & -sample.mask))
    val coefficient = if (sample.coefficient == 0L) Stream.empty else Stream(sample.copy(coefficient = sample.coefficient / 2L))
    val scale = if (sample.scale == 0) Stream.empty else Stream(sample.copy(scale = sample.scale / 2))
    val nanos = if (sample.nanos == 0) Stream.empty else Stream(sample.copy(nanos = sample.nanos / 2))
    val mode = if (sample.mode == Mode.Current) Stream.empty else Stream(sample.copy(mode = Mode.Current))
    val target = if (sample.target == Target.Entity) Stream.empty else Stream(sample.copy(target = Target.Entity))
    val representation = if (sample.representation == EntityRevisionRepresentation.Embedded) Stream.empty
      else Stream(sample.copy(representation = EntityRevisionRepresentation.Embedded))
    val flags = (if (sample.clear) Stream(sample.copy(clear = false)) else Stream.empty) #:::
      (if (sample.maximum) Stream(sample.copy(maximum = false)) else Stream.empty)
    val adapter = if (sample.adapter == 0) Stream.empty else Stream(sample.copy(adapter = sample.adapter / 2))
    val veto = if (sample.veto == Veto.Factory) Stream.empty else Stream(sample.copy(veto = Veto.Factory))
    text #::: mask #::: coefficient #::: scale #::: nanos #::: mode #::: target #::: representation #::: flags #::: adapter #::: veto
  }
}

final class DisplayMutationServiceUpdatePropertySpec extends AnyWordSpec with DisplayMutationServiceFixture.Vocabulary {
  import DisplayMutationServiceFixture.*
  import DisplayMutationServiceUpdatePropertySpec.*
  import DisplayMutationServiceUpdatePropertySpec.given
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
  private def _changes(sample: Sample): Vector[DisplayMutation.FieldInput] = {
    val values = Vector(field("title", DisplayValue.Text("Changed:" + sample.text)), field("description", DisplayValue.Text(sample.text)),
      field("number", DisplayValue.Number(BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(sample.coefficient), sample.scale)))),
      field("flag", DisplayValue.Boolean(sample.coefficient % 2L == 0L)),
      field("instant", DisplayValue.Timestamp(java.time.Instant.ofEpochSecond(1792022400L, sample.nanos.toLong))),
      field("nullable", if (sample.clear) DisplayValue.Missing else DisplayValue.Text(sample.text)))
    values.zipWithIndex.collect { case (value, index) if (sample.mask & (1 << index)) != 0 => value }.reverse
  }
  private def _changed(before: Rows, changes: Vector[DisplayMutation.FieldInput]): Boolean = changes.exists { input =>
    val expected: Option[Any] = input.value match {
      case DisplayValue.Text(value) => Some("utf16:" + value.iterator.map(character => f"${character.toInt}%04x").mkString)
      case DisplayValue.Number(value) => Some(value.bigDecimal.toString)
      case DisplayValue.Boolean(value) => Some(if (value) "true" else "false")
      case DisplayValue.Timestamp(value) => Some(value.toString)
      case DisplayValue.Missing => None
    }
    before.selected.get.getAny(input.location.fieldId) != expected
  }
  private def _with_sessions(backend: String)(body: Map[(Target, EntityRevisionRepresentation), Session] => Unit): Unit = {
    def _with_session_(remaining: List[(Target, EntityRevisionRepresentation)],
      sessions: Map[(Target, EntityRevisionRepresentation), Session]): Unit = remaining match {
      case Nil => body(sessions)
      case (target, representation) :: rest =>
        val session = open(target, representation, backend = backend)
        try _with_session_(rest, sessions.updated(target -> representation, session))
        finally session.close()
    }
    val pairs = (for (target <- Target.values.toVector; representation <- representations)
      yield target -> representation).toList
    _with_session_(pairs, Map.empty)
  }
  private def _adapter(session: Session, sample: Sample, changes: Vector[DisplayMutation.FieldInput], valid: Boolean,
    authenticated: Boolean = true): AdapterResult = {
    val raw = session.payload(changes)
    val payload = if (valid) raw else raw.replace("\"version\":1", "\"version\":1,\"version\":1")
    def _consequence_(value: Consequence[org.goldenport.protocol.operation.OperationResponse]): AdapterResult = value match {
      case Consequence.Success(org.goldenport.protocol.operation.OperationResponse.Json(value)) => AdapterResult(Some(value), None, None, 200)
      case Consequence.Failure(error) => AdapterResult(None, Some(error), None, 400)
      case _ => throw new IllegalStateException("normal adapter must return business JSON")
    }
    sample.adapter match {
      case 0 =>
        val response = session.http(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
          if (valid) Vector("If-Match" -> "\"revision-1\"") else Vector("If-Match" -> "\"revision-1\"", "if-match" -> "\"revision-1\""),
          authenticated = authenticated)
        AdapterResult(if (response.status == 200) Some(response.data) else None, None, Some(response.json), response.status)
      case 1 =>
        val request = ArgsIngress().encode(session.component.protocol.services, Array("display", "update", "--resourceType=" + resourceType,
          "--screenId=" + screenId, "--payload=" + payload, "--" + EntityRevisionTransport.observedRevisionPropertyName + "=1")).TAKE
          .copy(component = Some(session.component.name))
        _consequence_(session.execute(request).result)
      case 2 =>
        val arguments = Json.obj("resourceType" -> Json.fromString(resourceType), "screenId" -> Json.fromString(screenId),
          "payload" -> Json.fromString(payload), EntityRevisionTransport.observedRevisionPropertyName -> Json.fromString("1"),
          "cncf.security.privilege" -> Json.fromString("user"), "principalId" -> Json.fromString("owner"),
          "cncf.security.authenticated" -> Json.fromString(authenticated.toString),
          "cncf.context.traceId" -> Json.fromString(observability.traceId.print),
          "cncf.context.correlationId" -> Json.fromString(observability.correlationId.get.print))
        val rawrequest = Json.obj("jsonrpc" -> Json.fromString("2.0"), "id" -> Json.fromInt(1), "method" -> Json.fromString("tools/call"),
          "params" -> Json.obj("name" -> Json.fromString(session.component.name + ".display.update"), "arguments" -> arguments))
        val response = new McpJsonRpcAdapter(session.subsystem).handle(rawrequest.noSpaces, Some(McpProtocolRevision.PREFERRED.print)).responseBody.get
        val failed = response.hcursor.downField("result").get[Boolean]("isError").toOption.contains(true) || response.hcursor.downField("error").focus.nonEmpty
        val value = if (failed) None else {
          val text = response.hcursor.downField("result").downField("content").downN(0).get[String]("text").toOption.get
          val parsed = io.circe.parser.parse(text).toOption.get
          Some(parsed.hcursor.downField("data").focus.getOrElse(parsed))
        }
        AdapterResult(value, None, Some(response), if (failed) 400 else 200)
      case _ =>
        val active = session.execution()
        given ExecutionContext = active.context
        session.subsystem.spiInvoker.invoke(spiContract, SpiOperationSelector("update", Some("display")),
          Record.dataAuto("resourceType" -> resourceType, "screenId" -> screenId, "payload" -> payload,
            EntityRevisionTransport.observedRevisionPropertyName -> "1"), ComponentSelector(component = Some(session.component.name))) match {
          case Consequence.Success(value) => AdapterResult(Some(io.circe.parser.parse(value.toJsonString).toOption.get), None, None, 200)
          case Consequence.Failure(error) => AdapterResult(None, Some(error), None, 400)
        }
    }
  }

  "Normal Display mutation generated laws" should {
    "typed native patches and canonical outcomes" which {
      "C3-P1 preserve every selected native value and exact authoritative outcome/revision with no unselected change" in {
        Given("six real portable Entity/Aggregate/alias sessions and total nonempty subsets, Unicode, exact decimal scale, nanos and clear modes")
        _with_sessions("sqlite") { sessions =>
          val property = Prop.forAll(samples) { sample =>
            Given("canonical create-seeded state is reset before the selected generated patch and its independent oracle")
            val session = sessions(sample.target -> sample.representation)
            session.seed()
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            val changes = _changes(sample)
            val changed = _changed(before, changes)
            val wanted = session.expected(before, tables, changes, changed, if (changed) 2L else 1L)
            When("the ordinary resolver executes the typed selected patch on its actual active UnitOfWork")
            val attempt = session.execute(session.request(changes))
            val after = session.rows(); val physical = session.physical()
            Then("full ordered logical/physical state, the actual outcome/revision and unrelated resident data equal their pre-action expectations")
            checkResult(attempt, session, if (changed) "updated" else "unchanged", if (changed) 2L else 1L)
            checkRows(after, wanted.rows)
            physical shouldBe wanted.physical
            checkCompleteState(session, wanted)
            session.resident.unrelated shouldBe resident.unrelated
            session.resident.memoryunrelated shouldBe resident.memoryunrelated
            session.log.conversions shouldBe logicalui.DisplayMutationServiceDomain.fieldIds.filter(name => changes.exists(_.location.fieldId == name))
            session.log.validations shouldBe 1
            true
          }
          When("the named sequential law executes at least one hundred successful undiscarded samples")
          val result = Test.check(_parameters, property)
          Then("C3-P1 passed with at least one hundred successes and zero discards")
          withClue("C3-P1 ") { result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0 }
        }
      }

      "C3-P2 preserve current, maximum no-op and stale-before-equality state and forbid replay" in {
        Given("six actual memory sessions and a total mode generator including equal/changed stale input")
        _with_sessions("memory") { sessions =>
          val property = Prop.forAll(samples) { sample =>
            Given("a canonical source, resident and View baseline at its independently selected positive revision")
            val session = sessions(sample.target -> sample.representation)
            val initial = if (sample.maximum && sample.mode == Mode.Current) Long.MaxValue else 1L
            session.seed("Before:" + sample.text, initial)
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            val stale = sample.mode == Mode.StaleEqual || sample.mode == Mode.StaleChanged
            val changed = sample.mode == Mode.Changed || sample.mode == Mode.StaleChanged
            val changes = Vector(field("title", DisplayValue.Text((if (changed) "Changed:" else "Before:") + sample.text)))
            val wanted = session.expected(before, tables, changes, changed && !stale, if (changed && !stale) initial + 1L else initial)
            When("one normal command compares its external observation before equality or conversion")
            val attempt = session.execute(session.request(changes, if (stale) 2L else initial))
            val after = session.rows()
            Then("current modes use exact canonical outcomes while stale modes retain every field and invoke no converter/domain/writer")
            checkRows(after, wanted.rows)
            if (stale) {
              attempt.result shouldBe a[Consequence.Failure[?]]
              conclusion(attempt.result).observation.taxonomy.symptom shouldBe org.goldenport.observation.Taxonomy.Symptom.Conflict
              attempt.execution.attempts shouldBe 0
              checkPreserved(session, before, tables, resident)
            } else {
              checkResult(attempt, session, if (changed) "updated" else "unchanged", if (changed) initial + 1L else initial)
              checkCompleteState(session, wanted)
            }
            true
          }
          When("the named current/stale law executes sequentially with semantic shrinking")
          val result = Test.check(_parameters, property)
          Then("C3-P2 passed with at least one hundred successes and zero discards")
          withClue("C3-P2 ") { result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0 }
        }
      }
    }

    "zero effects and adapter correspondence" which {
      "C3-P3 reject denied, readonly, managed registration, duplicate and tampered input with complete state preservation" in {
        Given("actual registered portable sessions and a total generated veto dimension")
        _with_sessions("memory") { sessions =>
          val property = Prop.forAll(samples) { sample =>
            Given("an independent canonical baseline and an explicitly selected denial/admission boundary")
            val session = sessions(sample.target -> sample.representation)
            session.log.denyFactory = false
            session.log.availability = Consequence.success(DisplayMutation.Availability.enabled)
            session.seed()
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            val change = field("title", DisplayValue.Text("Changed:" + sample.text))
            session.log.denyFactory = sample.veto == Veto.Factory
            if (sample.veto == Veto.Readonly) session.log.availability = Consequence.success(DisplayMutation.Availability.enabled.copy(
              readonlyFields = Set(DisplayMutation.FieldLocator("main", "title"))))
            When("the actual normal operation or prepublication typed registration applies its selected veto")
            val result = if (sample.veto == Veto.Managed) {
              val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-managed-registration")
              val component = new NormalComponent("generatedmanaged", sample.target, sample.representation, new Log, Registration.ManagedStorage)
              initialize(component, subsystem)
              try new ComponentFactory().bootstrapC(component).map(_ => ()) finally Subsystem.shutdownOwned(subsystem)
            } else {
              val base = if (sample.veto == Veto.Duplicate) session.rawRequest(session.duplicateFieldPayload(change)) else session.request(Vector(change))
              val request = if (sample.veto == Veto.Tampered) base.copy(properties = base.properties.map { property =>
                if (property.name == "payload") Property("payload", session.payload(Vector(change)).replace("normal-update-1", "foreign-mutation"), None) else property
              }) else base
              session.execute(request).result.map(_ => ())
            }
            Then("the structured rejection retains complete root/body/security/audit/resident/View state with no domain, writer or read replay")
            result shouldBe a[Consequence.Failure[?]]
            conclusion(result).observation.taxonomy should not be null
            checkPreserved(session, before, tables, resident)
            true
          }
          When("the named zero-effect law executes sequentially with total shrinking")
          val result = Test.check(_parameters, property)
          Then("C3-P3 passed with at least one hundred successes and zero discards")
          withClue("C3-P3 ") { result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0 }
        }
      }

      "C3-P4 preserve ordinary strict raw JSON/header and CLI/MCP/SPI semantic correspondence" in {
        Given("real SQLite sessions, raw native values and all four ordinary adapters generated without filters or assumptions")
        _with_sessions("sqlite") { sessions =>
          val property = Prop.forAll(samples) { sample =>
            Given("the complete independently reset native portable state and a valid or duplicate raw input")
            val session = sessions(sample.target -> sample.representation)
            session.seed()
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            val changes = _changes(sample)
            val valid = sample.mode == Mode.Current || sample.mode == Mode.Changed
            val changed = _changed(before, changes)
            val wanted = session.expected(before, tables, changes, valid && changed, if (valid && changed) 2L else 1L)
            When("the selected real adapter carries the original raw String and external observation into ordinary operation execution")
            val attempt = _adapter(session, sample, changes, valid)
            val after = session.rows(); val physical = session.physical()
            Then("the actual adapter response and complete canonical storage match the same independent semantic law")
            checkRows(after, wanted.rows)
            physical shouldBe wanted.physical
            if (valid) {
              attempt.status shouldBe 200
              attempt.value.get.hcursor.get[String]("outcome") shouldBe Right(if (changed) "updated" else "unchanged")
              attempt.value.get.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
              session.log.validations shouldBe 1
              session.log.events.count(_.event == "find") shouldBe 1
              checkCompleteState(session, wanted)
            } else {
              attempt.status should be >= 400
              attempt.value shouldBe None
              (attempt.error.nonEmpty || attempt.wire.exists(_.isObject)) shouldBe true
              attempt.error.foreach(error => error.observation.taxonomy should not be null)
              checkPreserved(session, before, tables, resident)
            }
            true
          }
          When("the named raw-adapter law executes sequentially with reducing semantic shrinks")
          val result = Test.check(_parameters, property)
          Then("C3-P4 passed with at least one hundred successes and zero discards")
          withClue("C3-P4 ") { result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0 }
        }
      }
    }
  }
}
