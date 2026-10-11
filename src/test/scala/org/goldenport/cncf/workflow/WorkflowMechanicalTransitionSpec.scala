package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  9, 2026
 */
final class WorkflowMechanicalTransitionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Mechanical fixture failed: $other")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val producer = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
  private val definition = take(bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
    resource("/workflow/candidate-admission-workflow-abi.json").trim, producer, "modeler/candidate-admission-workflow.cml"))))
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("mechanical")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("mechanical")))
  private val component = ComponentId("org.test.Mechanical")
  private val operation = StateMachineOperationIdentity("workflow", "advance")
  private val initial = InstanceRecord(InstanceIdentity("instance"), definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("start"),
    None, None, None, Vector.empty)
  private val active = take(initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L), InstanceRevision(1L),
    Lifecycle.Active, Some(ProgressionReference("planning")), initial.correlation, initial.causation, None, None, None)))
  private val entry = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
    Some(ProgressionReference("planned")), active.correlation, CausationReference("checkpoint"), None, None, None)
  private val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "planning.v1"
    def encode(value: String) = Json.fromString(value)
    def decode(value: Json) = value.asString.toRight("Expected planning text")
  }
  private val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(definition)))
  private val intent = Intent(config, Reference("checkpoint", "1"), active, active.revision, entry,
    TypedValue(codec.typeIdentity, "known planning"))

  "Mechanical transition data admission" should {
    "roundtrip one exact ordinary append without any issued interaction or AI result" in {
      Given("an active unsuspended instance, typed planning payload and one explicit next entry")
      val receipt = take(admitC(component, operation, intent))
      When("the separate closed mechanical codec writes and reads the complete receipt")
      val encoded = take(WorkflowMechanicalTransitionJsonV1.encodeC(receipt, codec))
      val recovered = take(WorkflowMechanicalTransitionJsonV1.decodeC(encoded, catalog, codec))
      Then("the original and exact appended instance and complete payload survive unchanged")
      recovered shouldBe receipt
      recovered.next.history shouldBe active.history :+ entry
      recovered.next.suspension shouldBe None
    }

    "encode unambiguous explicit keys and reject control, surrogate and excessive identities" in {
      Given("references containing delimiters, literal percent escapes and UTF-8 text")
      val refs = Vector(Reference("a@b", "1:2"), Reference("a%40b", "1:2"), Reference("日本語", "1"))
      When("checkpoint keys are derived without hashing or selecting another identity")
      val keys = refs.map(reference => take(keyC(reference)))
      Then("delimiter escaping is canonical and distinct while invalid or oversized references refuse")
      keys.head shouldBe "mechanical:a%40b@1%3A2"
      keys.distinct.size shouldBe refs.size
      Vector(Reference("", "1"), Reference(" leading", "1"), Reference("a\u0000b", "1"),
        Reference("\uD800", "1"), Reference("x" * 1024, "1"), null)
        .foreach(reference => keyC(reference).isSuccess shouldBe false)
    }

    "refuse initial, terminal, suspended, stale and noncontiguous transitions" in {
      Given("an ordinary mechanical intent with invalid lifecycle, revision or history variants")
      val variants = Vector(intent.copy(current = initial, expectedRevision = initialRevision),
        intent.copy(expectedRevision = InstanceRevision(0L)),
        intent.copy(entry = entry.copy(sequence = HistorySequence(3L))),
        intent.copy(entry = entry.copy(lifecycle = Lifecycle.Completed, currentProgression = None)),
        intent.copy(entry = entry.copy(correlation = CorrelationReference("foreign"))),
        intent.copy(entry = entry.copy(suspension = Some(SuspensionBoundary(ContinuationIdentity("work"),
          InstanceRevision(2L), ContextSnapshotReference("context"), CompletionReference("completion"),
          EvidenceReference("evidence"), CorrelationReference("resume"))))))
      When("the host admits only ordinary active unsuspended one-append progress")
      val admitted = variants.map(value => admitC(component, operation, value))
      Then("every invalid variant refuses without an admitted mechanical receipt")
      admitted.foreach(_.isSuccess shouldBe false)
    }

    "refuse unknown fields, foreign payload types and oversized data without recertifying an append" in {
      Given("a correctly encoded receipt and an independently admitted definition catalog")
      val receipt = take(admitC(component, operation, intent))
      val json = parse(take(WorkflowMechanicalTransitionJsonV1.encodeC(receipt, codec))).toOption.get
      val foreignPayload = json.hcursor.downField("payload").focus.get.mapObject(_.add("typeIdentity", Json.fromString("foreign")))
      When("closed-schema decoding sees unknown envelope/reference fields or a foreign payload type")
      val malformed = Vector(json.mapObject(_.add("claim", Json.fromString("none"))),
        json.mapObject(_.add("reference", json.hcursor.downField("reference").focus.get.mapObject(_.add("hash", Json.fromString("x"))))),
        json.mapObject(_.add("payload", foreignPayload)))
      val decoded = malformed.map(value => WorkflowMechanicalTransitionJsonV1.decodeC(value.noSpaces, catalog, codec))
      val oversized = WorkflowMechanicalTransitionJsonV1.encodeC(
        receipt.copy(payload = TypedValue(codec.typeIdentity, "x" * (1024 * 1024))), codec)
      Then("no malformed envelope or incompatible payload is accepted")
      decoded.foreach(_.isSuccess shouldBe false)
      oversized.isSuccess shouldBe false
    }

    "read historical records as inert data without reproducing or proving their transition" in {
      Given("a receipt carrying individually valid native records that are not a one-append pair")
      val admitted = take(admitC(component, operation, intent))
      val property = Prop.forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { cause =>
        val historic = active.copy(causation = CausationReference(cause),
          history = active.history.map(_.copy(causation = CausationReference(cause))))
        val saved = admitted.copy(next = historic)
        WorkflowMechanicalTransitionJsonV1.encodeC(saved, codec)
          .flatMap(WorkflowMechanicalTransitionJsonV1.decodeC(_, catalog, codec)).toOption.contains(saved)
      }
      When("the codec stores and reloads this historical data")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("raw record data survives; decoding neither executes nor certifies an append")
      checked.passed shouldBe true
    }
  }
}
