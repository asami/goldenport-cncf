package org.goldenport.cncf.component.capability

import java.nio.charset.StandardCharsets
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityProjectionCodecSpec.*

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentCapabilityProjectionCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) = {
    afterWord(s"in spec:component-capability, example:$example, rules:$rules, phase:78")
  }

  private def _right[A](result: Either[Diagnostic, A]): A = {
    result.fold(error => fail(s"Unexpected ${error.kind} at ${error.location}: ${error.detail}"), value => value)
  }

  private def _left[A](result: Either[Diagnostic, A]): Diagnostic = {
    result.fold(error => error, _ => fail("Expected a typed admission failure."))
  }

  private def _present[A](value: Presence[A]): A = {
    value match {
      case Present(item) => item
      case _: Absent => fail("Expected explicitly present conformance data.")
    }
  }

  private def _json(text: String): Json = {
    parse(text).fold(_ => fail("The conformance JSON must be valid."), value => value)
  }

  private def _edit(value: Json, path: Vector[String], change: Json => Json): Json = {
    path match {
      case Vector() => change(value)
      case field +: rest =>
        value.asObject match {
          case Some(obj) => Json.fromJsonObject(obj.add(field, _edit(obj(field).get, rest, change)))
          case None =>
            val items = value.asArray.get
            val index = field.toInt
            Json.fromValues(items.updated(index, _edit(items(index), rest, change)))
        }
    }
  }

  private def _mutate(value: Json, path: String, change: Json => Json): Json = {
    _edit(value, path.split("/").filter(_.nonEmpty).toVector, change)
  }

  "External v1 metadata" should {
    "CPJ-01 retain the complete hand-authored contract through selected-scope admission" should _metadata("CPJ-01", "R1,R4,R5,R11,R12") {
      "preserve exact fields, independent revisions and ordered edges" in {
        Given("the explicit independent ready facts for the hand-authored conformance resource; no runtime Component")
        val scope = _fixture_scope
        When("the UTF-8 resource is decoded, admitted, reencoded, decoded and admitted again")
        val text = _read_fixture()
        val projection = _right(ComponentCapabilityProjectionCodec.decode(text))
        val admitted = _right(ComponentCapabilityAdmission.admitJson(SourceInput.Acquired(scope.scopeId, text), ScopeInput.Ready(scope)))
        val encoded = _right(ComponentCapabilityProjectionCodec.encode(admitted.projection))
        val repeated = _right(ComponentCapabilityAdmission.admitJson(SourceInput.Acquired(scope.scopeId, encoded), ScopeInput.Ready(scope)))
        Then("every semantic JSON field survives and the bound edge and target retain their independent sources")
        _json(encoded) shouldBe _json(text)
        repeated.projection shouldBe projection
        admitted.scopeId shouldBe scope.scopeId
        admitted.capabilities shouldBe projection.capabilities ++ scope.capabilities
        admitted.realizations.map(_.target) shouldBe scope.targetContracts
        repeated.realizations.map(r => r.edge -> r.target) shouldBe admitted.realizations.map(r => r.edge -> r.target)
        projection.component.componentId.namespace.value() shouldBe "org.example"
        projection.component.componentId.localId.value() shouldBe "Payments"
        projection.producer shouldBe Producer("hand-authored-conformance", "producer-9")
        projection.component.version shouldBe "3.7.0-SNAPSHOT"
        projection.packageCoordinate shouldBe PackageCoordinate("package:payments", "package-21")
        projection.capabilities.map(_.name) shouldBe Vector("Fulfil order", "Fulfil order")
        projection.capabilities.map(_.identity.elementId) shouldBe Vector("cap:pay", "cap:reserve")
        projection.declarations.map(_.direction) shouldBe Vector(Direction.Provided, Direction.Required, Direction.Provided)
        admitted.realizations.map(_.edge.source.sourceRevision) shouldBe Vector("operation-edge-r14", "workflow-edge-r15", "machine-edge-r17")
        admitted.realizations.map(_.target.source.sourceRevision) shouldBe Vector("target-operation-r101", "target-workflow-r102", "target-machine-r103")
      }
    }

    "CPJ-02 preserve literal qualified identity domains" should _metadata("CPJ-02", "R2,R11") {
      "roundtrip generated case, Unicode-composition and delimiter variations without normalization" in {
        Given("valid literal qualified IDs, including composed/decomposed Unicode and punctuation")
        val values = Gen.oneOf("Model:É/支払", "model:E\u0301/支払", "MODEL::Pay#1", "model|pay:v2")
        val ids = Gen.zip(values, Gen.oneOf("Element:É", "Element:E\u0301", "element::x/y", "ELEMENT#支払"))
        When("ScalaCheck samples complete metadata carriers and roundtrips the codec")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(80), Prop.forAll(ids) { (pair: (String, String)) =>
          val projection = _literal_projection(pair._1, pair._2)
          ComponentCapabilityProjectionCodec.encode(projection).flatMap(ComponentCapabilityProjectionCodec.decode) == Right(projection)
        })
        val distinct = Vector("É", "E\u0301", "é", "E:1", "E/1").map(id => _definition("model:literal", id, "contract-X"))
        val roundtrip = ComponentCapabilityProjectionCodec.encode(_empty_projection.copy(capabilities = distinct)).flatMap(ComponentCapabilityProjectionCodec.decode)
        Then("all four qualified domains remain literal and distinct identities with equal names stay distinct")
        checked.passed shouldBe true
        _right(roundtrip).capabilities.map(_.identity.elementId) shouldBe Vector("É", "E\u0301", "é", "E:1", "E/1")
        _right(roundtrip).capabilities.map(_.name).distinct shouldBe Vector("Same label")
      }
    }

    "CPJ-03 retain explicit Presence and nullable fields" should _metadata("CPJ-03", "R5,R6,R9,R11") {
      "keep all absence reasons, present-empty, null line and null language distinct" in {
        Given("the conformance resource and the four closed absence reasons")
        val reasons = AbsenceReason.values.toVector
        When("the resource and each explicit absent compatibility relationship are roundtripped")
        val projection = _right(ComponentCapabilityProjectionCodec.decode(_read_fixture()))
        val variants = reasons.map { reason =>
          val raw = _empty_projection.copy(compatibility = Absent(reason, s"Explicit $reason detail"))
          ComponentCapabilityProjectionCodec.encode(raw).flatMap(ComponentCapabilityProjectionCodec.decode)
        }
        val groundings = _present(projection.capabilities.head.grounding)
        Then("absence reasons/details and present-empty relationships remain explicit metadata")
        variants.map(_right(_).compatibility) shouldBe reasons.map(reason => Absent(reason, s"Explicit $reason detail"))
        projection.source.line shouldBe None
        projection.capabilities.head.source.line shouldBe Some(11)
        projection.capabilities(1).discoveryReferences shouldBe Present(Vector.empty)
        projection.declarations(1).realizations shouldBe Present(Vector.empty)
        projection.declarations(2).realizations shouldBe Absent(AbsenceReason.Unsupported, "No supported realization projection.")
        groundings.head.preferredLabel shouldBe Present(LocalizedLabel("支払い", None))
        groundings(2).preferredLabel shouldBe Present(LocalizedLabel("Payment", Some("en")))
        groundings.head.origin shouldBe Derived("rule:term-grounding", Vector(
          Source("vocab:commerce", "vocab/commerce.cml", "vocab-ref-r5", Some(5)),
          Source("model:domain", "models/domain.cml", "domain-input-r2", None)
        ))
      }
    }

    "CPJ-04 reject nonexact objects and wrong nested types" should _metadata("CPJ-04", "R11,R12") {
      "report each actual nested location with the known owning source and Component" in {
        Given("single-field missing, extra, null, wrong-type and mixed-branch mutation plans")
        val cases: Vector[(String, Json => Json, String)] = Vector(
          ("/capabilities/0", j => Json.fromJsonObject(j.asObject.get.remove("name")), "/capabilities/0/name"),
          ("/capabilities/0", j => j.mapObject(_.add("applicationState", Json.fromString("planned"))), "/capabilities/0/applicationState"),
          ("/capabilities/0/name", _ => Json.Null, "/capabilities/0/name"),
          ("/capabilities/0/identity/modelId", _ => Json.fromInt(2), "/capabilities/0/identity/modelId"),
          ("/capabilities/0/summary", j => j.mapObject(_.add("reason", Json.fromString("NotDeclared"))), "/capabilities/0/summary/reason"),
          ("/capabilities/0/description", j => j.mapObject(_.add("value", Json.fromString("mixed"))), "/capabilities/0/description/value"),
          ("/capabilities/0/grounding/value/0/origin", j => j.mapObject(_.add("extra", Json.True)), "/capabilities/0/grounding/value/0/origin/extra"),
          ("/capabilities/0/grounding/value/0/target/identity", j => j.mapObject(_.add("modelId", Json.fromString("mixed"))), "/capabilities/0/grounding/value/0/target/identity/modelId"),
          ("/capabilities/0/grounding/value/0/profile/value/profileId", _ => Json.False, "/capabilities/0/grounding/value/0/profile/value/profileId"),
          ("/capabilities/0/discoveryReferences/value", _ => Json.obj(), "/capabilities/0/discoveryReferences/value")
        )
        When("each semantic mutation is decoded independently")
        val json = _json(_read_fixture())
        val failures = cases.map { (path, change, expected) =>
          _left(ComponentCapabilityProjectionCodec.decode(_mutate(json, path, change).noSpaces)) -> expected
        }
        Then("closed shape failures remain Malformed at the changed nested pointer")
        failures.foreach { (error, expected) =>
          error.kind shouldBe DiagnosticKind.Malformed
          error.location shouldBe expected
          error.component shouldBe Some(ComponentId("org.example.Payments"))
          error.source.map(_.modelAuthority) shouldBe Some("model:shop")
          error.source.map(_.sourceRevision).exists(_.startsWith("snapshot")) shouldBe false
        }
      }
    }

    "CPJ-05 reject duplicate keys before collection conversion" should _metadata("CPJ-05", "R11,R12") {
      "retain a source-less parse diagnostic for root and nested duplication and syntax errors" in {
        Given("malformed JSON and root/nested duplicate-key JSON texts")
        val texts = Vector(
          "{\"schemaVersion\":1,\"schemaVersion\":1}",
          "{\"schemaVersion\":1,\"generatedAbiVersion\":\"cncf.component-capability.v1\",\"source\":{\"sourceRevision\":\"first\",\"sourceRevision\":\"second\"}}",
          "{broken", "null"
        )
        When("the strict parser receives these acquisition bytes")
        val failures = texts.map(text => _left(ComponentCapabilityProjectionCodec.decode(text)))
        Then("no first/last-wins or fabricated source attribution reaches metadata")
        failures.foreach { error =>
          error.kind shouldBe DiagnosticKind.Malformed
          error.location shouldBe "/"
          error.source shouldBe None
          error.component shouldBe None
        }
      }
    }

    "CPJ-06 keep schema and producer ABI precedence" should _metadata("CPJ-06", "R4,R11") {
      "return Unsupported for future contracts before decoding nested v1 fields" in {
        Given("future schema/ABI envelopes and invalid schema scalar forms")
        val unsupported = Vector(
          "{\"schemaVersion\":2,\"futureShape\":true}",
          "{\"schemaVersion\":1,\"generatedAbiVersion\":\"cncf.component-capability.v2\",\"futureShape\":true}",
          "{\"schemaVersion\":1,\"generatedAbiVersion\":\"1\"}",
          "{\"schemaVersion\":1,\"generatedAbiVersion\":\"0.5.3-SNAPSHOT\"}"
        )
        val malformed = Vector("true", "1.5", "1.0", "\"1\"", "null").map(value => s"{\"schemaVersion\":$value}")
        When("the codec interprets these headers")
        val future = unsupported.map(text => _left(ComponentCapabilityProjectionCodec.decode(text)))
        val bad = malformed.map(text => _left(ComponentCapabilityProjectionCodec.decode(text)))
        Then("ABI/version domains remain separate and malformed schema types cannot downgrade")
        future.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Unsupported)
        future.head.location shouldBe "/schemaVersion"
        future.tail.map(_.location).distinct shouldBe Vector("/generatedAbiVersion")
        bad.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
        bad.map(_.location).distinct shouldBe Vector("/schemaVersion")
      }
    }

    "CPJ-07 keep closed tags and Component family" should _metadata("CPJ-07", "R1,R9,R11") {
      "distinguish unsupported well-formed tags from malformed text and misspelled keys" in {
        Given("unknown role, boundary, origin, state, absence, direction, target and Application-family tags")
        val tags = Vector(
          "/capabilities/0/kind" -> "APPLICATION",
          "/declarations/0/capability/kind" -> "APPLICATION",
          "/declarations/0/direction" -> "OFFERS",
          "/declarations/0/realizations/value/0/targetKind" -> "ACTION",
          "/capabilities/0/discoveryReferences/value/0/role" -> "DEMAND",
          "/capabilities/0/traceability/value/0/role" -> "PROOF",
          "/capabilities/0/grounding/value/0/boundary" -> "GLOBAL",
          "/capabilities/0/grounding/value/0/target/kind" -> "IRI",
          "/capabilities/0/grounding/value/0/origin/kind" -> "INFERRED",
          "/capabilities/0/summary/state" -> "MISSING",
          "/capabilities/0/description/reason" -> "Unknown"
        )
        When("each exact semantic tag is replaced and a known field is misspelled")
        val json = _json(_read_fixture())
        val failures = tags.map { (path, tag) => _left(ComponentCapabilityProjectionCodec.decode(_mutate(json, path, _ => Json.fromString(tag)).noSpaces)) -> path }
        val misspelled = _mutate(json, "/capabilities/0", j => j.mapObject(obj => obj.remove("contractVersion").add("contractversion", Json.fromString("cap-new"))))
        val padded = _left(ComponentCapabilityProjectionCodec.decode(_mutate(json, "/capabilities/0/kind", _ => Json.fromString(" COMPONENT")).noSpaces))
        val spelling = _left(ComponentCapabilityProjectionCodec.decode(misspelled.noSpaces))
        Then("future tags are Unsupported without substitution, while broken lexical/field forms are Malformed")
        failures.foreach { (error, path) =>
          error.kind shouldBe DiagnosticKind.Unsupported
          error.location shouldBe path
        }
        padded.kind shouldBe DiagnosticKind.Malformed
        spelling.kind shouldBe DiagnosticKind.Malformed
        spelling.location shouldBe "/capabilities/0/contractVersion"
      }
    }

    "CPJ-08 require explicit revision, canonical paths and valid text" should _metadata("CPJ-08", "R2,R5,R11") {
      "reject legacy digest substitution and invalid scalar provenance without normalization" in {
        Given("invalid source paths/lines and whitespace/control text across the literal domains")
        val paths = Vector("", "/absolute.cml", "C:/drive.cml", "https:remote", "models\\shop.cml", "models//shop.cml", "./shop.cml", "models/../shop.cml", "models/", "models/\u0001.cml")
        val lines = Vector(Json.fromInt(0), Json.fromInt(-1), Json.fromDoubleOrNull(1.5), Json.True, Json.fromString("11"))
        val textpaths = Vector("/producer/id", "/producer/version", "/package/coordinate", "/package/version", "/source/modelAuthority", "/source/sourceRevision", "/capabilities/0/name", "/capabilities/0/identity/modelId", "/capabilities/0/identity/elementId", "/capabilities/0/summary/value", "/capabilities/0/description/detail", "/capabilities/0/grounding/value/0/origin/ruleId", "/capabilities/0/grounding/value/0/preferredLabel/value/text")
        When("each single semantic provenance/text mutation and a missing-revision legacy source are decoded")
        val json = _json(_read_fixture())
        val badpaths = paths.map(value => _left(ComponentCapabilityProjectionCodec.decode(_mutate(json, "/source/path", _ => Json.fromString(value)).noSpaces)))
        val badlines = lines.map(value => _left(ComponentCapabilityProjectionCodec.decode(_mutate(json, "/source/line", _ => value).noSpaces)))
        val badtexts = textpaths.flatMap { path =>
          Vector("", " padded", "padded\u00a0", "control\u007f").map { value =>
            _left(ComponentCapabilityProjectionCodec.decode(_mutate(json, path, _ => Json.fromString(value)).noSpaces)) -> path
          }
        }
        val legacy = _mutate(json, "/source", j => j.mapObject(_.remove("sourceRevision").add("sha256", Json.fromString("a" * 64))))
        val missing = _left(ComponentCapabilityProjectionCodec.decode(legacy.noSpaces))
        Then("every invalid field remains Malformed at its owning location; sha256 supplies no revision")
        badpaths.foreach { error => error.kind shouldBe DiagnosticKind.Malformed; error.location shouldBe "/source/path" }
        badlines.foreach { error => error.kind shouldBe DiagnosticKind.Malformed; error.location shouldBe "/source/line" }
        badtexts.foreach { (error, path) => error.kind shouldBe DiagnosticKind.Malformed; error.location shouldBe path }
        missing.kind shouldBe DiagnosticKind.Malformed
        missing.location shouldBe "/source/sourceRevision"
      }
    }

    "CPJ-09 enforce the raw typed contract before encoding or admission" should _metadata("CPJ-09", "R5,R11,R12") {
      "refuse equivalent invalid raw and JSON values and null mandatory branches" in {
        Given("an invalid raw source path, its equivalent semantic JSON mutation and null-bearing raw carriers")
        val raw = _empty_projection.copy(source = _empty_projection.source.copy(path = "../shop.cml"))
        val invalid = Vector(
          _empty_projection.copy(producer = null),
          _empty_projection.copy(capabilities = null),
          _empty_projection.copy(source = _empty_projection.source.copy(line = null)),
          _empty_projection.copy(compatibility = Present(null)),
          _empty_projection.copy(capabilities = Vector(_definition("model:test", "id", "v").copy(summary = Present(null))))
        )
        When("validate, encode, decode and admission enforce the same raw contract")
        val encoded = _right(ComponentCapabilityProjectionCodec.encode(_empty_projection))
        val json = _mutate(_json(encoded), "/source/path", _ => Json.fromString("../shop.cml"))
        val results = Vector(
          _left(ComponentCapabilityProjectionCodec.validate(raw)),
          _left(ComponentCapabilityProjectionCodec.encode(raw)),
          _left(ComponentCapabilityProjectionCodec.decode(json.noSpaces)),
          _left(ComponentCapabilityAdmission.admit(raw, ScopeInput.Ready(_fixture_scope)))
        )
        val nulls = invalid.map(value => _left(ComponentCapabilityProjectionCodec.encode(value)))
        Then("raw values cannot bypass validation or produce admitted metadata")
        results.distinct.size shouldBe 1
        results.head.kind shouldBe DiagnosticKind.Malformed
        results.head.location shouldBe "/source/path"
        results.head.source shouldBe Some(raw.source)
        nulls.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
      }
    }
  }
}

object ComponentCapabilityProjectionCodecSpec {
  private[capability] def _read_fixture(): String = {
    val stream = Option(getClass.getResourceAsStream("/component-capability/projection-v1.json"))
      .getOrElse(throw new IllegalStateException("Missing conformance resource."))
    try {
      new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    } finally {
      stream.close()
    }
  }

  private[capability] def _definition(authority: String, id: String, version: String): CapabilityDefinition = {
    CapabilityDefinition(
      ModelElementId(authority, id), version, "Same label",
      Absent(AbsenceReason.NotDeclared, "No summary declared."),
      Absent(AbsenceReason.NotRepresented, "No description represented."),
      Source(authority, "models/definition.cml", "definition-r1", None),
      Present(Vector.empty), Present(Vector.empty), Present(Vector.empty)
    )
  }

  private[capability] def _empty_projection: Projection = {
    Projection(1, "cncf.component-capability.v1", Producer("producer:test", "producer-v9"),
      ComponentCoordinate(ComponentId("org.example.Payments"), "3.7.0-SNAPSHOT"),
      PackageCoordinate("package:test", "package-v21"),
      Source("model:test", "models/test.cml", "snapshot-r1", None),
      Vector.empty, Vector.empty, Present(Vector.empty))
  }

  private[capability] def _literal_projection(authority: String, id: String): Projection = {
    val definition = _definition(authority, id, "contract-literal").copy(grounding = Present(Vector(
      Grounding(TermTarget(TermId(authority, id)), ReferenceBoundary.External,
        Present(RelationId(authority, id)), Present(ProfileId(authority, id)),
        Present("Literal context"), Present(LocalizedLabel("Literal label", Some("ja"))),
        Source(authority, "models/literal.cml", "edge-revision", Some(2)), Declared)
    )))
    _empty_projection.copy(capabilities = Vector(definition))
  }

  private[capability] def _fixture_scope: Scope = {
    Scope(
      "scope:hand-authored-conformance",
      Source("scope:authority", "scope/facts.cml", "scope-selection-r100", None),
      Vector(
        ModelFact(ModelElementId("model:domain", "concept:payment"), ModelKind.DomainConcept, Source("model:domain", "models/domain.cml", "domain-fact-r91", Some(4))),
        ModelFact(ModelElementId("model:proof", "spec:payment"), ModelKind.Specification, Source("model:proof", "models/proof.cml", "spec-fact-r92", Some(5))),
        ModelFact(ModelElementId("model:proof", "evidence:claim"), ModelKind.Evidence, Source("model:proof", "models/proof.cml", "evidence-fact-r93", None))
      ),
      Vector(TermFact(TermId("vocab:commerce", "term:Pay"), Source("vocab:commerce", "vocab/commerce.cml", "term-fact-r94", Some(6)))),
      Vector(RelationFact(RelationId("vocab:commerce", "relation:denotes"), Source("vocab:commerce", "vocab/commerce.cml", "relation-fact-r95", None))),
      Vector(ProfileFact(ProfileId("vocab:commerce", "profile:public"), Source("vocab:commerce", "vocab/commerce.cml", "profile-fact-r96", Some(8)))),
      Vector(_definition("model:shop", "cap:pay", "cap-old").copy(source = Source("model:shop", "models/previous.cml", "old-definition-r90", Some(3)))),
      Vector(
        TargetContract(TargetKind.Operation, ModelElementId("model:targets", "operation:charge"), ComponentId("org.example.Payments"), "operation-contract-2", "operation-abi-5", Source("model:targets", "models/targets.cml", "target-operation-r101", Some(10))),
        TargetContract(TargetKind.Workflow, ModelElementId("model:targets", "workflow:checkout"), ComponentId("org.example.Orchestrator"), "workflow-contract-6", "workflow-abi-8", Source("model:targets", "models/targets.cml", "target-workflow-r102", None)),
        TargetContract(TargetKind.StateMachine, ModelElementId("model:targets", "machine:payment"), ComponentId("org.example.Payments"), "machine-contract-3", "machine-abi-10", Source("model:targets", "models/targets.cml", "target-machine-r103", Some(12)))
      )
    )
  }
}
