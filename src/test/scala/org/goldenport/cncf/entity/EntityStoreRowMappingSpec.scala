package org.goldenport.cncf.entity

import java.math.MathContext
import scala.reflect.ClassTag
import cats.data.{NonEmptyList, NonEmptyVector}
import io.circe.{Decoder, Encoder, Json}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString, XJson, XSemiStructuredData}
import org.goldenport.value.ContentBody
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.model.directive.Update
import org.scalacheck.{Arbitrary, Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification of docs/spec/entity-store-row-mapping.md.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreRowMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityStoreRowMappingSpec.*

  "Declared EntityStore row values" should {
    "ROW-01 reconstruct the five-field declared consumer" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-01, rules:R1,R9,R12, phase:100"
    ) {
      "retain logical classes, exact text, row order and managed physical fields" in {
        Given("docs/spec/entity-store-row-mapping.md R1,R9,R12 ROW-01: five explicitly bound fields and an exact physical whitelist")
        val summary = Summary("accepted", 7)
        val evidence = Record.createFull(Vector("source" -> "TKL", "empty" -> Record.empty, "null" -> null))
        val payload = Json.obj("candidate" -> Json.fromString("雪"))
        val original = " \n{malformed proposal} 雪\t "
        val id = EntityCollectionId("test", "row", "proposal")
        val mapping = EntityStoreRowMapping(Vector(
          _field("originalProposal", "original_proposal", EntityStoreFieldBinding.string),
          _field("payload", "payload_json", EntityStoreFieldBinding.json),
          _field("evidence", "evidence_json", EntityStoreFieldBinding.semiStructuredData),
          _field("labels", "labels_json", EntityStoreFieldBinding.array(EntityStoreFieldBinding.string), Multiplicity.ZeroMore),
          _field("summary", "summary_json", _summary_binding)), Set("id", "revision", "permission"))
        val logical = Record.createFull(Vector("id" -> id, "originalProposal" -> original,
          "payload" -> payload, "revision" -> 3L, "evidence" -> evidence,
          "labels" -> Vector("first", "", "雪"), "summary" -> summary, "permission" -> "opaque:{}"))
        When("the declared row encodes to physical text and decodes with the same declaration")
        val encoded = mapping.encode(_collection, logical)
        val decoded = encoded.flatMap(mapping.decode(_collection, _))
        Then("every logical value and whitelisted field survives with exact names and order")
        encoded.isSuccess shouldBe true
        _values(encoded.get.get).map(_._1) shouldBe Vector("id", "original_proposal", "payload_json",
          "revision", "evidence_json", "labels_json", "summary_json", "permission")
        _value(encoded.get.get, "original_proposal") shouldBe original
        mapping.fields.foreach(x => _value(encoded.get.get, x.storageName).isInstanceOf[String] shouldBe true)
        _values(decoded.get.get) shouldBe _values(logical)
        mapping.fields.last.binding.datatype shouldBe DataType.Named(summon[ClassTag[Summary]].runtimeClass.getName)
        mapping.fields.last.binding.applicationClassName shouldBe Some(summon[ClassTag[Summary]].runtimeClass.getName)
      }
    }

    "ROW-02 retain the renamed String value space" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-02, rules:R1,R2,R8, phase:100"
    ) {
      "satisfy the public row roundtrip law for arbitrary and adversarial text" in {
        Given("docs/spec/entity-store-row-mapping.md R1,R2,R8 ROW-02: a renamed String field and arbitrary escaped Unicode text")
        val mapping = EntityStoreRowMapping(Vector(_field("proposal", "proposal_text", EntityStoreFieldBinding.string)))
        val texts = Gen.oneOf(Arbitrary.arbitrary[String],
          Gen.listOf(Gen.oneOf("雪", "🙂", "é", "\n", "\t", "\u0000", "\"", "\\", " ")).map(_.mkString),
          Gen.oneOf("", "{}", "[]", "null", "{broken", "[1,]", " 12.30 "))
        val property = Prop.forAll(texts) { text =>
          val logical = Record.createFull(Vector("proposal" -> text))
          val encoded = mapping.encode(_collection, logical)
          val decoded = encoded.flatMap(mapping.decode(_collection, _))
          encoded.get.exists(x => _values(x) == Vector("proposal_text" -> text)) &&
            decoded.get.exists(x => _values(x) == Vector("proposal" -> text))
        }
        When("the declared public row String roundtrip interprets generated text")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(200), property)
        Then("every character remains exact without JSON parsing or whitespace normalization")
        checked.passed shouldBe true
      }
    }

    "ROW-03 preserve present structured values" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-03, rules:R3,R4,R7,R8, phase:100"
    ) {
      "retain every Json shape, empty Records, nested nulls and exact numbers" in {
        Given("docs/spec/entity-store-row-mapping.md R3,R4,R7,R8 ROW-03: canonical Json shapes and empty or nested-null Records")
        val integer = BigInt("12345678901234567890123456789012345678901234567890")
        val decimal = new BigDecimal(new java.math.BigDecimal(
          "12345678901234567890.12345678901234567890123456789"), MathContext.UNLIMITED)
        val jsonvalues = Vector(Json.Null, Json.True, Json.fromString("text"), Json.obj(), Json.arr(),
          Json.fromBigInt(integer), Json.fromBigDecimal(decimal), Json.arr(Json.Null, Json.obj()))
        val records = Vector(Record.empty, Record.createFull(Vector("null" -> null,
          "empty" -> Record.empty, "array" -> Vector.empty, "integer" -> integer, "decimal" -> decimal)))
        val jsonmapping = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.json)))
        val recordmapping = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.semiStructuredData)))
        When("each declared structured row encodes and reconstructs its canonical logical value")
        val jsonencoded = jsonvalues.map(x => jsonmapping.encode(_collection, Record.createFull(Vector("value" -> x))))
        val jsondecoded = jsonencoded.map(_.flatMap(jsonmapping.decode(_collection, _)))
        val recordencoded = records.map(x => recordmapping.encode(_collection, Record.createFull(Vector("value" -> x))))
        val recorddecoded = recordencoded.map(_.flatMap(recordmapping.decode(_collection, _)))
        val canonicalrecords = recorddecoded.map(_.flatMap(x => XSemiStructuredData.toJson(_value(x, "value").asInstanceOf[Record])))
        val expectedrecords = records.map(XSemiStructuredData.toJson)
        Then("null is JSON text, empty objects remain present and numeric meaning remains exact")
        jsondecoded.map(x => _value(x.get.get, "value")) shouldBe jsonvalues
        _value(jsonencoded.head.get.get, "stored") shouldBe "null"
        _value(recordencoded.head.get.get, "stored") shouldBe "{}"
        recorddecoded.foreach(_.get.get.fields.size shouldBe 1)
        canonicalrecords.map(_.get) shouldBe expectedrecords.map(_.get)
        _values(_value(recorddecoded.head.get.get, "value").asInstanceOf[Record]) shouldBe Vector.empty
        _value(_value(recorddecoded.last.get.get, "value").asInstanceOf[Record], "null") shouldBe (null: Any)
        _value(_value(recorddecoded.last.get.get, "value").asInstanceOf[Record], "integer") shouldBe
          new BigDecimal(new java.math.BigDecimal(integer.bigInteger), MathContext.UNLIMITED)
        _value(_value(recorddecoded.last.get.get, "value").asInstanceOf[Record], "decimal") shouldBe decimal
      }
    }
  }

  "Declared metadata and directional names" should {
    "ROW-04 reject inconsistent declarations before application conversion" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-04, rules:R1,R10, phase:100"
    ) {
      "reject domain mismatch, duplicate names, physical overlap and undeclared keys without invoking a codec" in {
        Given("docs/spec/entity-store-row-mapping.md R1,R10 ROW-04: an observed explicit application codec and inconsistent maps")
        var calls = 0
        val encoder = Encoder.instance[Summary] { value => calls += 1; _summary_encoder(value) }
        val decoder = Decoder.instance[Summary] { cursor => calls += 1; _summary_decoder(cursor) }
        val binding = EntityStoreFieldBinding.application[Summary]("observed-v1")(
          using summon[ClassTag[Summary]], encoder, decoder)
        val field = _field("summary", "summary_json", binding)
        val other = _field("other", "other_text", EntityStoreFieldBinding.string, Multiplicity.ZeroOne)
        val maps = Vector(
          EntityStoreRowMapping(Vector(field.copy(domain = ValueDomain(XString)), other)),
          EntityStoreRowMapping(Vector(field, other.copy(logicalName = "summary"))),
          EntityStoreRowMapping(Vector(field, other.copy(storageName = "summary_json"))),
          EntityStoreRowMapping(Vector(field), Set("summary")),
          EntityStoreRowMapping(Vector(field), Set("summary_json")))
        val valid = EntityStoreRowMapping(Vector(field))
        val logical = Record.createFull(Vector("summary" -> Summary("private", 1)))
        val physical = Record.createFull(Vector("summary_json" -> "{}"))
        When("public validation and row operations receive inconsistent metadata or undeclared input keys")
        val metadataresults = maps.flatMap(x => Vector(x.validate(_collection), x.encode(_collection, logical),
          x.encodePatch(_collection, logical), x.decode(_collection, physical)))
        val undeclared = Vector(valid.encode(_collection, logical ++ Record.createFull(Vector("extra" -> 1))),
          valid.decode(_collection, physical ++ Record.createFull(Vector("extra" -> 1))))
        Then("metadata and row structure failures precede every captured application call")
        metadataresults.foreach(x => _expect_reason(x, "incompatible-mapping"))
        undeclared.foreach(x => _expect_reason(x, "missing-mapping"))
        calls shouldBe 0
      }
    }

    "ROW-05 require every captured binding" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-05, rules:R1,R6,R10, phase:100"
    ) {
      "reject missing application declarations recursively even for empty arrays" in {
        Given("docs/spec/entity-store-row-mapping.md R1,R6,R10 ROW-05: null bindings and missing ClassTag, Encoder, Decoder or identity")
        var calls = 0
        val encoder = Encoder.instance[Summary] { value => calls += 1; _summary_encoder(value) }
        val decoder = Decoder.instance[Summary] { cursor => calls += 1; _summary_decoder(cursor) }
        val bindings = Vector(
          EntityStoreFieldBinding.application[Summary]("missing-tag")(using null, encoder, decoder),
          EntityStoreFieldBinding.application[Summary]("missing-encoder")(using summon[ClassTag[Summary]], null, decoder),
          EntityStoreFieldBinding.application[Summary]("missing-decoder")(using summon[ClassTag[Summary]], encoder, null),
          EntityStoreFieldBinding.application[Summary](null)(using summon[ClassTag[Summary]], encoder, decoder),
          EntityStoreFieldBinding.application[Summary]("  ")(using summon[ClassTag[Summary]], encoder, decoder))
        val maps = bindings.map(x => EntityStoreRowMapping(Vector(_field("value", "stored", x))))
        val arrays = bindings.map(x => EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(x))) :+
          EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(null))
        val arraymaps = arrays.map(x => EntityStoreRowMapping(Vector(_field("value", "stored", x, Multiplicity.ZeroMore))))
        val nullbinding = EntityStoreRowMapping(Vector(EntityStoreField("value", "stored", ValueDomain(XString), null)))
        When("public operations validate missing scalar and recursive bindings before conversion")
        val results = maps.flatMap(x => Vector(x.encode(_collection, Record.createFull(Vector("value" -> Summary("x", 1)))),
          x.decode(_collection, Record.createFull(Vector("stored" -> "{}"))))) ++
          arraymaps.flatMap(x => Vector(x.encode(_collection, Record.createFull(Vector("value" -> Vector.empty))),
            x.encode(_collection, Record.createFull(Vector("value" -> Vector(Vector.empty)))),
            x.decode(_collection, Record.createFull(Vector("stored" -> "[]"))),
            x.decode(_collection, Record.createFull(Vector("stored" -> "[[]]")))))
        val nullresult = nullbinding.validate(_collection)
        Then("missing-codec-binding remains explicit and no application code runs for empty roots or children")
        results.foreach(x => _expect_reason(x, "missing-codec-binding"))
        _expect_reason(nullresult, "missing-mapping")
        calls shouldBe 0
      }
    }

    "ROW-14 select exact directional names" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-14, rules:R1,R9,R10, phase:100"
    ) {
      "permit cross-names without aliases and reject duplicate actual row keys" in {
        Given("docs/spec/entity-store-row-mapping.md R1,R9,R10 ROW-14: two crossed logical/storage names and duplicate input keys")
        val mapping = EntityStoreRowMapping(Vector(_field("first", "second", EntityStoreFieldBinding.string),
          _field("second", "first", EntityStoreFieldBinding.string)))
        val logical = Record.createFull(Vector("first" -> "A", "second" -> "B"))
        val duplicates = Record.createFull(Vector("first" -> "A", "first" -> "B", "second" -> "C"))
        val single = EntityStoreRowMapping(Vector(_field("logical", "stored", EntityStoreFieldBinding.string)))
        When("conversion uses its direction and encounters duplicate or opposite-direction keys")
        val encoded = mapping.encode(_collection, logical)
        val decoded = encoded.flatMap(mapping.decode(_collection, _))
        val results = Vector(mapping.encode(_collection, duplicates), mapping.decode(_collection, duplicates),
          mapping.encodePatch(_collection, duplicates))
        val aliasresults = Vector(single.encode(_collection, Record.createFull(Vector("stored" -> "x"))),
          single.decode(_collection, Record.createFull(Vector("logical" -> "x"))))
        Then("crossed declarations rename once while duplicates and alias fallback fail")
        _values(encoded.get.get) shouldBe Vector("second" -> "A", "first" -> "B")
        _values(decoded.get.get) shouldBe _values(logical)
        results.foreach(x => _expect_reason(x, "invalid-row-shape"))
        aliasresults.foreach(x => _expect_reason(x, "missing-mapping"))
      }
    }

    "ROW-15 represent invalid setup as structured outcomes" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-15, rules:R1,R10, phase:100"
    ) {
      "reject null metadata, non-Single values, unsupported multiplicities and invalid ranges without setup throws" in {
        Given("docs/spec/entity-store-row-mapping.md R1,R10 ROW-15: missing metadata and invalid scalar/array domain declarations")
        val field = _field("value", "stored", EntityStoreFieldBinding.string)
        val array = EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)
        val maps = Vector(EntityStoreRowMapping(null), EntityStoreRowMapping(Vector(field), null),
          EntityStoreRowMapping(Vector(null)), EntityStoreRowMapping(Vector(field.copy(domain = null))),
          EntityStoreRowMapping(Vector(field.copy(domain = ValueDomain(null)))),
          EntityStoreRowMapping(Vector(field.copy(domain = ValueDomain(XString, null)))),
          EntityStoreRowMapping(Vector(field.copy(domain = ValueDomain(XString, Multiplicity.One, null)))))
        val invalid = Vector(
          EntityStoreRowMapping(Vector(field.copy(domain = ValueDomain(XString, Multiplicity.ZeroMore)))),
          EntityStoreRowMapping(Vector(_field("value", "stored", array, Multiplicity.One))),
          EntityStoreRowMapping(Vector(_field("value", "stored", array, Multiplicity.ZeroOne))),
          EntityStoreRowMapping(Vector(_field("value", "stored", array, Multiplicity.Range(-1, 2)))),
          EntityStoreRowMapping(Vector(_field("value", "stored", array, Multiplicity.Range(3, 2)))),
          EntityStoreRowMapping(Vector(_field("value", "stored", array, Multiplicity.Ranges(Nil)))),
          EntityStoreRowMapping(Vector(_field("value", "stored", array,
            Multiplicity.Ranges(List(NonEmptyList.of(Multiplicity.Range(0, 1), Multiplicity.Range(-1, 3))))))),
          EntityStoreRowMapping(Vector(field.copy(logicalName = " value"))),
          EntityStoreRowMapping(Vector(field.copy(storageName = " "))))
        val mapping = EntityStoreRowMapping(Vector(field))
        val nonsingle = new Field.Value { def single: Any = "x"; def print: String = "fixture" }
        val badrows = Vector[Record](null, Record(null), Record(Vector(null)),
          Record(Vector(Field("value", null))), Record(Vector(Field("value", nonsingle))))
        When("public validation and row conversion interpret invalid setup")
        val missingresults = maps.map(_.validate(_collection)) :+ mapping.validate(null)
        val invalidresults = invalid.map(_.validate(_collection))
        val rowresults = badrows.flatMap(x => Vector(mapping.encode(_collection, x),
          mapping.encodePatch(_collection, x), mapping.decode(_collection, x)))
        val nullcollection = mapping.encode(null, Record.empty)
        Then("each invalid setup returns an existing typed failure rather than throwing from construction or operation")
        missingresults.foreach(x => _expect_reason(x, "missing-mapping"))
        invalidresults.foreach(x => _expect_reason(x, "incompatible-mapping"))
        rowresults.foreach(x => _expect_reason(x, "invalid-row-shape"))
        _expect_reason(nullcollection, "missing-mapping")
      }
    }
  }

  "Row presence and normalized changes" should {
    "ROW-06 retain absence and explicit native null according to multiplicity" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-06, rules:R7,R10, phase:100"
    ) {
      "reject required absence or SQL null and preserve optional absence or a present null Field" in {
        Given("docs/spec/entity-store-row-mapping.md R7,R10 ROW-06: required scalars, required arrays and an optional scalar")
        val required = Vector(
          EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.string))),
          EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.array(EntityStoreFieldBinding.string), Multiplicity.OneMore))))
        val optional = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.string, Multiplicity.ZeroOne)))
        When("full row conversion interprets missing or native-null fields")
        val absent = required.flatMap(x => Vector(x.encode(_collection, Record.empty), x.decode(_collection, Record.empty)))
        val nulls = required.flatMap(x => Vector(x.encode(_collection, Record.createFull(Vector("value" -> null))),
          x.decode(_collection, Record.createFull(Vector("stored" -> null)))))
        val optionalabsent = Vector(optional.encode(_collection, Record.empty), optional.decode(_collection, Record.empty))
        val optionalnulls = Vector(optional.encode(_collection, Record.createFull(Vector("value" -> null))),
          optional.decode(_collection, Record.createFull(Vector("stored" -> null))))
        Then("required presence failures stay distinct while optional nulls remain actual Fields")
        absent.foreach(x => _expect_reason(x, "missing-required-field"))
        nulls.foreach(x => _expect_reason(x, "null-not-allowed"))
        optionalabsent.foreach(_.get.get.fields shouldBe Vector.empty)
        _values(optionalnulls.head.get.get) shouldBe Vector("stored" -> null)
        _values(optionalnulls.last.get.get) shouldBe Vector("value" -> null)
      }
    }

    "ROW-07 unwrap only the declared optional scalar" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-07, rules:R4,R7, phase:100"
    ) {
      "keep Some(empty Record) present, omit None and reject required Option values" in {
        Given("docs/spec/entity-store-row-mapping.md R4,R7 ROW-07: optional and required semi-structured fields")
        val binding = EntityStoreFieldBinding.semiStructuredData
        val optional = EntityStoreRowMapping(Vector(_field("evidence", "stored", binding, Multiplicity.ZeroOne)))
        val required = EntityStoreRowMapping(Vector(_field("evidence", "stored", binding)))
        When("logical optional values cross the declared row boundary")
        val present = optional.encode(_collection, Record.createFull(Vector("evidence" -> Some(Record.empty))))
        val omitted = optional.encode(_collection, Record.createFull(Vector("evidence" -> None)))
        val decoded = present.flatMap(optional.decode(_collection, _))
        val rejected = Vector(Some(Record.empty), None).map(x => required.encode(_collection, Record.createFull(Vector("evidence" -> x))))
        Then("optional presence is preserved and readback remains plain Record for entity admission")
        _values(present.get.get) shouldBe Vector("stored" -> "{}")
        omitted.get.get.fields shouldBe Vector.empty
        _values(decoded.get.get) shouldBe Vector("evidence" -> Record.empty)
        rejected.foreach(x => _expect_reason(x, "wrong-logical-value"))
      }
    }

    "ROW-08 retain normalized patch instructions" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-08, rules:R7,R9,R10, phase:100"
    ) {
      "keep omission unchanged and explicit clears present until provider mutation" in {
        Given("docs/spec/entity-store-row-mapping.md R7,R9,R10 ROW-08: required String and nullable Record fields with native update directives")
        val mapping = EntityStoreRowMapping(Vector(_field("title", "title_text", EntityStoreFieldBinding.string),
          _field("evidence", "evidence_json", EntityStoreFieldBinding.semiStructuredData, Multiplicity.ZeroOne)))
        val patch = Record.createFull(Vector("title" -> Update.SetValue("  exact {}  "), "evidence" -> Update.SetNull))
        val noop = Record.createFull(Vector("title" -> Update.Noop, "evidence" -> Update.Noop))
        val badpatches = Vector(Record.createFull(Vector("title" -> Update.SetNull)),
          Record.createFull(Vector("evidence" -> null)), Record.createFull(Vector("evidence" -> Update.Noop)),
          Record.createFull(Vector("title" -> Update.SetValue("x"))))
        When("normalized changes pass through the declared patch mapping")
        val changed = mapping.encodePatch(_collection, Update.toChangesRecord(patch))
        val unchanged = Vector(mapping.encodePatch(_collection, Record.empty),
          mapping.encodePatch(_collection, Update.toChangesRecord(noop)),
          mapping.encodePatch(_collection, Record.createFull(Vector("evidence" -> None))))
        val failures = badpatches.map(mapping.encodePatch(_collection, _))
        val fullmarkers = Vector(mapping.encode(_collection, patch),
          mapping.decode(_collection, Record.createFull(Vector("title_text" -> "x", "evidence_json" -> Update.SetNull))))
        Then("changed text uses its codec and the nullable clear marker keeps its physical key")
        _values(changed.get.get) shouldBe Vector("title_text" -> "  exact {}  ", "evidence_json" -> Update.SetNull)
        changed.get.get.fields.exists(x => x.key == "evidence_json" && x.value.single == Update.SetNull) shouldBe true
        unchanged.foreach(_.get.get.fields shouldBe Vector.empty)
        _expect_reason(failures(0), "null-not-allowed")
        _expect_reason(failures(1), "explicit-clear-required")
        failures.drop(2).foreach(x => _expect_reason(x, "invalid-row-shape"))
        fullmarkers.foreach(x => _expect_reason(x, "invalid-row-shape"))
      }
    }

    "ROW-16 pass only explicitly classified framework fields" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-16, rules:R1,R9,R12, phase:100"
    ) {
      "retain opaque managed values and patch instructions without inventing domain passthrough" in {
        Given("docs/spec/entity-store-row-mapping.md R1,R9,R12 ROW-16: an exact framework whitelist and native managed values")
        val id = EntityCollectionId("test", "opaque", "proposal")
        val body = ContentBody("<p>opaque content {} 雪</p>")
        val mapping = EntityStoreRowMapping(Vector(_field("title", "title_text", EntityStoreFieldBinding.string)),
          Set("id", "revision", "permission", "content"))
        val managed = Vector[(String, Any)]("id" -> id, "revision" -> 9L, "permission" -> " \npermission:{} ", "content" -> body)
        val logical = Record.createFull(managed :+ ("title" -> "plain"))
        val patch = Record.createFull(Vector("content" -> Update.SetNull, "revision" -> Update.SetValue(10L)))
        When("declared conversion receives whitelisted fields and an unlisted native field")
        val encoded = mapping.encode(_collection, logical)
        val decoded = encoded.flatMap(mapping.decode(_collection, _))
        val encodedpatch = mapping.encodePatch(_collection, patch)
        val unlisted = mapping.encode(_collection, logical ++ Record.createFull(Vector("nativeDomain" -> Record.empty)))
        Then("managed values pass unchanged and unlisted keys fail missing-mapping")
        _values(encoded.get.get).take(4) shouldBe managed
        _values(decoded.get.get) shouldBe _values(logical)
        _values(encodedpatch.get.get) shouldBe _values(patch)
        _expect_reason(unlisted, "missing-mapping")
      }
    }
  }

  "Declared arrays and causal failures" should {
    "ROW-09 enforce root array cardinality" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-09, rules:R6,R7,R10, phase:100"
    ) {
      "admit only declared counts and adapt Seq or NonEmptyVector without guessing other collections" in {
        Given("docs/spec/entity-store-row-mapping.md R6,R7,R10 ROW-09: array cardinalities and declared Scala representations")
        val binding = EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)
        val domains = Vector[Multiplicity](Multiplicity.ZeroMore, Multiplicity.OneMore, Multiplicity.Range(0, 2),
          Multiplicity.Range(2, 3), Multiplicity.Ranges(List(NonEmptyList.of(Multiplicity.Range(0, 0), Multiplicity.Range(2, 3)))))
        val maps = domains.map(x => EntityStoreRowMapping(Vector(_field("values", "stored", binding, x))))
        val counts = Vector(0, 1, 2, 3, 4)
        val admitted = Vector(Set(0, 1, 2, 3, 4), Set(1, 2, 3, 4), Set(0, 1, 2), Set(2, 3), Set(0, 2, 3))
        val values = Vector("first", "雪", "last")
        val representations = Vector[Any](values, values.toList, NonEmptyVector.of("first", "雪", "last"))
        val unsupported = Vector[Any](Set("x"), Array("x"), Map("x" -> "y"))
        When("root arrays encode and decode under each declared multiplicity")
        val encoded = maps.map(x => counts.map(n => x.encode(_collection,
          Record.createFull(Vector("values" -> Vector.fill(n)("x"))))))
        val decoded = maps.map(x => counts.map(n => x.decode(_collection,
          Record.createFull(Vector("stored" -> Json.fromValues(Vector.fill(n)(Json.fromString("x"))).noSpaces)))))
        val equivalent = representations.map(x => maps.head.encode(_collection, Record.createFull(Vector("values" -> x))))
        val readback = equivalent.map(_.flatMap(maps.head.decode(_collection, _)))
        val rejected = unsupported.map(x => maps.head.encode(_collection, Record.createFull(Vector("values" -> x))))
        val absent = maps.map(_.encode(_collection, Record.empty))
        val nulls = maps.map(_.decode(_collection, Record.createFull(Vector("stored" -> null))))
        Then("only root cardinality is constrained and accepted representations share ordered Vector readback")
        maps.indices.foreach { i =>
          counts.indices.foreach { j =>
            encoded(i)(j).isSuccess shouldBe admitted(i).contains(counts(j))
            decoded(i)(j).isSuccess shouldBe admitted(i).contains(counts(j))
            if (!admitted(i).contains(counts(j))) {
              _expect_reason(encoded(i)(j), "cardinality-mismatch")
              _expect_reason(decoded(i)(j), "cardinality-mismatch")
            }
          }
          absent(i).isSuccess shouldBe admitted(i).contains(0)
          nulls(i).isSuccess shouldBe admitted(i).contains(0)
        }
        equivalent.map(x => _value(x.get.get, "stored")).distinct.size shouldBe 1
        readback.foreach(x => _value(x.get.get, "values") shouldBe values)
        rejected.foreach(x => _expect_reason(x, "wrong-logical-value"))
      }
    }

    "ROW-10 retain recursive element declarations" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-10, rules:R6,R7, phase:100"
    ) {
      "retain empty nested arrays, application classes and Json null while rejecting other null children with indices" in {
        Given("docs/spec/entity-store-row-mapping.md R6,R7 ROW-10: recursive String, application and Json array bindings")
        val nested = EntityStoreRowMapping(Vector(_field("value", "stored",
          EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)), Multiplicity.ZeroMore)))
        val summaries = EntityStoreRowMapping(Vector(_field("value", "stored",
          EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(_summary_binding)), Multiplicity.ZeroMore)))
        val json = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.array(EntityStoreFieldBinding.json), Multiplicity.ZeroMore)))
        val rejecting = Vector(EntityStoreFieldBinding.string, EntityStoreFieldBinding.semiStructuredData,
          EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)).map(x => EntityStoreRowMapping(Vector(
          _field("value", "stored", EntityStoreFieldBinding.array(x), Multiplicity.ZeroMore))))
        val nestedvalue = Vector[Any](Vector.empty[String], NonEmptyVector.of("first", "", "雪"), Vector("last"))
        val classvalue = NonEmptyVector.of(Vector.empty[Summary], NonEmptyVector.of(Summary("A", 1), Summary("B", 2)))
        When("recursive rows roundtrip and selected child codecs encounter null elements")
        val encoded = nested.encode(_collection, Record.createFull(Vector("value" -> nestedvalue)))
        val decoded = encoded.flatMap(nested.decode(_collection, _))
        val classes = summaries.encode(_collection, Record.createFull(Vector("value" -> classvalue))).flatMap(summaries.decode(_collection, _))
        val jsonnull = json.encode(_collection, Record.createFull(Vector("value" -> Vector(Json.Null, Json.True)))).flatMap(json.decode(_collection, _))
        val rejected = rejecting.flatMap(x => Vector(x.decode(_collection, Record.createFull(Vector("stored" -> "[null]"))),
          x.encode(_collection, Record.createFull(Vector("value" -> Vector(null))))))
        Then("recursive adaptation preserves order and class identity and null rejection retains index context")
        _value(decoded.get.get, "value") shouldBe Vector(Vector.empty, Vector("first", "", "雪"), Vector("last"))
        _value(classes.get.get, "value") shouldBe Vector(Vector.empty, Vector(Summary("A", 1), Summary("B", 2)))
        _value(jsonnull.get.get, "value") shouldBe Vector(Json.Null, Json.True)
        rejected.foreach { result =>
          result.isSuccess shouldBe false
          _causes(result).size shouldBe 1
          _facets(result) should contain(Descriptor.Facet.FieldPath("index[0]"))
        }
      }
    }

    "ROW-11 retain distinct physical conversion causes" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-11, rules:R10, phase:100"
    ) {
      "classify malformed text, physical kinds, structured roots and class rejection with field context" in {
        Given("docs/spec/entity-store-row-mapping.md R10 ROW-11: JSON-text mappings for Json, Record, array and application values")
        val json = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.json)))
        val record = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.semiStructuredData)))
        val array = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.array(EntityStoreFieldBinding.string), Multiplicity.ZeroMore)))
        val application = EntityStoreRowMapping(Vector(_field("value", "stored", _summary_binding)))
        When("physical rows decode malformed syntax, non-Strings, wrong roots and rejected application JSON")
        val malformed = json.decode(_collection, Record.createFull(Vector("stored" -> "{broken")))
        val wrongphysical = Vector[Any](Record.empty, Json.obj(), 3).map(x => json.decode(_collection, Record.createFull(Vector("stored" -> x))))
        val wrongshape = Vector(record.decode(_collection, Record.createFull(Vector("stored" -> "[]"))),
          array.decode(_collection, Record.createFull(Vector("stored" -> "{}"))))
        val rejected = application.decode(_collection, Record.createFull(Vector("stored" -> "{}")))
        Then("pure reasons and taxonomies remain distinct with collection, names, codec and decode stage")
        _expect_reason(malformed, "malformed-stored-json")
        _causes(malformed).head.observation.taxonomy shouldBe Taxonomy(Taxonomy.Category.Value, Taxonomy.Symptom.FormatError)
        wrongphysical.foreach(x => _expect_reason(x, "wrong-physical-value"))
        wrongshape.foreach(x => _expect_reason(x, "wrong-structured-shape"))
        _expect_reason(rejected, "application-codec-failure")
        (Vector(malformed, rejected) ++ wrongphysical ++ wrongshape).foreach { result =>
          _causes(result).foreach { cause =>
            val facets = cause.observation.cause.descriptor.facets
            facets should contain(Descriptor.Facet.Policy("entity.store.logical-type"))
            facets should contain(Descriptor.Facet.Id(_collection.print))
            facets should contain(Descriptor.Facet.FieldPath("value"))
            facets should contain(Descriptor.Facet.Key("stored"))
            facets should contain(Descriptor.Facet.State("decode"))
            facets.exists(_.isInstanceOf[Descriptor.Facet.Algorithm]) shouldBe true
          }
        }
      }
    }

    "ROW-12 aggregate independent causal failures" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-12, rules:R6,R10, phase:100"
    ) {
      "retain all fields, array indices and original causal facets without a successful partial row" in {
        Given("docs/spec/entity-store-row-mapping.md R6,R10 ROW-12: two invalid fields and two invalid array members beside a valid field")
        val mapping = EntityStoreRowMapping(Vector(_field("first", "first_json", EntityStoreFieldBinding.json),
          _field("second", "second_json", EntityStoreFieldBinding.semiStructuredData),
          _field("members", "members_json", EntityStoreFieldBinding.array(EntityStoreFieldBinding.string), Multiplicity.ZeroMore),
          _field("valid", "valid_text", EntityStoreFieldBinding.string)))
        val physical = Record.createFull(Vector("first_json" -> "{broken", "second_json" -> "[]",
          "members_json" -> "[1,true,\"valid\"]", "valid_text" -> "preserved if whole row succeeds"))
        val purecodec = EntityStoreValueCodec.array(EntityStoreValueCodec.string)
        When("the whole physical row and the declared pure array decode their independent failures")
        val result = mapping.decode(_collection, physical)
        val pure = purecodec.decode("[1,true,\"valid\"]")
        Then("all four causes remain ordered, the malformed taxonomy survives and no partial row is available")
        result.get shouldBe None
        _causes(result).size shouldBe 4
        _causes(result).map(_.observation.cause.descriptor.facets.collect {
          case Descriptor.Facet.Key(key) => key
        }.last) shouldBe List("first_json", "second_json", "members_json", "members_json")
        _causes(result).head.observation.taxonomy shouldBe Taxonomy(Taxonomy.Category.Value, Taxonomy.Symptom.FormatError)
        _facets(result) should contain(Descriptor.Facet.FieldPath("index[0]"))
        _facets(result) should contain(Descriptor.Facet.FieldPath("index[1]"))
        _causes(result).drop(2).zip(_causes(pure)).foreach { case (decorated, original) =>
          decorated.observation.taxonomy shouldBe original.observation.taxonomy
          decorated.status shouldBe original.status
          decorated.observation.cause.descriptor.facets should contain allElementsOf original.observation.cause.descriptor.facets
        }
      }
    }

    "ROW-13 reject unsupported objects without disclosure" should afterWord(
      "in spec:entity-store-row-mapping, example:ROW-13, rules:R8,R10, phase:100"
    ) {
      "avoid stringification and sanitize captured codec exceptions" in {
        Given("docs/spec/entity-store-row-mapping.md R8,R10 ROW-13: an unsupported object and explicit codecs throwing private messages")
        val unknown = new Object { override def toString: String = throw new IllegalStateException("PRIVATE-PAYLOAD") }
        val encoder = Encoder.instance[Summary](_ => throw new IllegalStateException("PRIVATE-ENCODER"))
        val decoder = Decoder.instance[Summary](_ => throw new IllegalStateException("PRIVATE-DECODER"))
        val binding = EntityStoreFieldBinding.application[Summary]("throwing-v1")(
          using summon[ClassTag[Summary]], encoder, decoder)
        val mapping = EntityStoreRowMapping(Vector(_field("summary", "stored", binding)))
        val invaliddecoder = Decoder.instance[Summary](_ => Right(null))
        val invalidbinding = EntityStoreFieldBinding.application[Summary]("null-success-v1")(
          using summon[ClassTag[Summary]], _summary_encoder, invaliddecoder)
        val invalidmapping = EntityStoreRowMapping(Vector(_field("summary", "stored", invalidbinding)))
        When("row conversion receives an incompatible object or a failed explicit application codec")
        val unsupported = mapping.encode(_collection, Record.createFull(Vector("summary" -> unknown)))
        val failed = Vector(mapping.encode(_collection, Record.createFull(Vector("summary" -> Summary("PRIVATE-PAYLOAD", 1)))),
          mapping.decode(_collection, Record.createFull(Vector("stored" -> "{\"private\":\"PRIVATE-PAYLOAD\"}"))),
          invalidmapping.decode(_collection, Record.createFull(Vector("stored" -> "{}"))))
        Then("the object fails its class check and exception or payload details never enter diagnostics")
        _expect_reason(unsupported, "wrong-logical-value")
        failed.foreach(x => _expect_reason(x, "application-codec-failure"))
        (failed :+ unsupported).foreach { result =>
          _causes(result).foreach { cause =>
            cause.displayMessage should not include "PRIVATE"
            cause.observation.cause.descriptor.facets.exists {
              case _: Descriptor.Facet.Exception | _: Descriptor.Facet.Value | _: Descriptor.Facet.RecordData => true
              case _ => false
            } shouldBe false
          }
        }
      }
    }
  }

  private val _collection: EntityCollectionId = EntityCollectionId("test", "row", "candidate")
  private val _summary_encoder: Encoder[Summary] = Encoder.forProduct2("label", "score")(x => (x.label, x.score))
  private val _summary_decoder: Decoder[Summary] = Decoder.forProduct2("label", "score")(Summary.apply)

  private def _summary_binding: EntityStoreFieldBinding = EntityStoreFieldBinding.application[Summary]("summary-v1")(
    using summon[ClassTag[Summary]], _summary_encoder, _summary_decoder)

  private def _field(
    logicalname: String,
    storagename: String,
    binding: EntityStoreFieldBinding,
    multiplicity: Multiplicity = Multiplicity.One
  ): EntityStoreField = EntityStoreField(logicalname, storagename, ValueDomain(binding.datatype, multiplicity), binding)

  private def _values(record: Record): Vector[(String, Any)] = record.fields.map(x => x.key -> x.value.single)

  private def _value(record: Record, key: String): Any = record.fields.find(_.key == key).get.value.single

  private def _causes(result: Consequence[?]): List[Conclusion] = result match {
    case Consequence.Failure(conclusion) => conclusion.causes
    case _ => fail("Expected a structured row conversion failure")
  }

  private def _facets(result: Consequence[?]): Vector[Descriptor.Facet] =
    _causes(result).toVector.flatMap(_.observation.cause.descriptor.facets)

  private def _expect_reason(result: Consequence[?], reason: String): Unit = {
    result.isSuccess shouldBe false
    _facets(result) should contain(Descriptor.Facet.Reason(reason))
  }
}

object EntityStoreRowMappingSpec {
  private final case class Summary(label: String, score: Int)
}
