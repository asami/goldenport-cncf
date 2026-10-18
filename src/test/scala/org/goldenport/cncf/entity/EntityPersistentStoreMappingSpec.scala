package org.goldenport.cncf.entity

import scala.reflect.ClassTag
import io.circe.{Decoder, Encoder, Json}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.observation.Descriptor
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.{Multiplicity, ValueDomain, XSemiStructuredData}
import org.goldenport.value.ContentBody
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Update
import org.scalacheck.{Arbitrary, Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification of docs/spec/entity-store-persistence-stages.md.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityPersistentStoreMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityPersistentStoreMappingSpec.*

  "Strict EntityPersistent stages" should {
    "complete typed rows" which {
      "STAGE-01 retain five logical types and exact framework fields" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-01, rules:R1,R2,R3,R4,R5,R6,R9,R12, phase:100"
      ) {
        "roundtrip a typed entity with one captured class conversion in each direction" in {
          Given("five declared logical fields and explicitly whitelisted identity, revision, permission and ContentBody values")
          var encodes = 0
          var decodes = 0
          val binding = _summary_binding(
            Encoder.instance[Summary] { value => encodes += 1; _summary_encoder(value) },
            Decoder.instance[Summary] { cursor => decodes += 1; _summary_decoder(cursor) })
          val mapping = _five_mapping(binding)
          val entity = Proposal(_id, " \n{broken original} 雪🙂\t ", Json.obj("candidate" -> Json.True),
            Record.createFull(Vector("null" -> null, "empty" -> Record.empty)),
            Vector("first", "", "雪"), Summary("accepted", 7), 3L, "opaque:{}", ContentBody("<p>雪 {}</p>"))
          val persistent = new EntityPersistent[Proposal] {
            override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
            def id(e: Proposal): EntityId = e.id
            def toRecord(e: Proposal): Record = Record.createFull(Vector("id" -> e.id,
              "originalProposal" -> e.original, "payload" -> e.payload, "revision" -> e.revision,
              "evidence" -> e.evidence, "labels" -> e.labels, "summary" -> e.summary,
              "permission" -> e.permission, "contentBody" -> e.body))
            def fromRecord(r: Record): Consequence[Proposal] = Consequence.success(Proposal(
              _value(r, "id").asInstanceOf[EntityId], _value(r, "originalProposal").asInstanceOf[String],
              _value(r, "payload").asInstanceOf[Json], _value(r, "evidence").asInstanceOf[Record],
              _value(r, "labels").asInstanceOf[Vector[String]], _value(r, "summary").asInstanceOf[Summary],
              _value(r, "revision").asInstanceOf[Long], _value(r, "permission").asInstanceOf[String],
              _value(r, "contentBody").asInstanceOf[ContentBody]))
          }
          When("the public strict write and read stages interpret the typed entity")
          val encoded = persistent.toStoreRecordC(_collection, entity)
          val decoded = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
          Then("logical classes, exact text, directional names, field order and framework values survive")
          decoded shouldBe Consequence.success(entity)
          _values(encoded.get.get).map(_._1) shouldBe Vector("id", "original_proposal", "payload_json",
            "revision", "evidence_json", "labels_json", "summary_json", "permission", "contentBody")
          _value(encoded.get.get, "original_proposal") shouldBe entity.original
          Vector("id", "revision", "permission", "contentBody").foreach { key =>
            _value(encoded.get.get, key) shouldBe _value(persistent.toRecord(entity), key)
          }
          mapping.fields.foreach(x => _value(encoded.get.get, x.storageName).isInstanceOf[String] shouldBe true)
          encodes shouldBe 1
          decodes shouldBe 1
        }
      }

      "STAGE-03 separate logical hooks from legacy physical hooks" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-03, rules:R1,R9,R12, phase:100"
      ) {
        "expose logical classes to each hook in write admission decode order" in {
          Given("logical hooks observing a declared application class and incompatible throwing legacy physical hooks")
          var events = Vector.empty[(String, Any)]
          var encodes = 0
          var decodes = 0
          val binding = _summary_binding(
            Encoder.instance[Summary] { value => encodes += 1; _summary_encoder(value) },
            Decoder.instance[Summary] { cursor => decodes += 1; _summary_decoder(cursor) })
          val mapping = EntityStoreRowMapping(Vector(_field("summary", "stored", binding)))
          val logical = Record.createFull(Vector("summary" -> Summary("visible", 2)))
          val persistent = _persistent(Some(mapping),
            r => { events :+= "write" -> _value(r, "summary"); Consequence.success(r) },
            r => { events :+= "admit" -> _value(r, "summary"); Consequence.success(r) },
            r => { events :+= "decode" -> _value(r, "summary"); Consequence.success(r) })
          When("strict public methods traverse both directions")
          val encoded = persistent.toStoreRecordC(_collection, logical)
          val decoded = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
          Then("each hook sees an actual Summary once and old physical hooks never run")
          decoded shouldBe Consequence.success(logical)
          events shouldBe Vector("write" -> Summary("visible", 2), "admit" -> Summary("visible", 2),
            "decode" -> Summary("visible", 2))
          encodes shouldBe 1
          decodes shouldBe 1
        }
      }

      "STAGE-04 decode an admitted row as logical data" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-04, rules:R1,R3,R4,R5,R9, phase:100"
      ) {
        "make split read equal composed read without parsing admitted structures again" in {
          Given("admission replacing a logical Summary and a final decoder observing that admitted value")
          var classdecodes = 0
          var admissions = 0
          var finals = Vector.empty[Any]
          val binding = _summary_binding(_summary_encoder,
            Decoder.instance[Summary] { cursor => classdecodes += 1; _summary_decoder(cursor) })
          val mapping = _five_mapping(binding)
          val persistent = _persistent(Some(mapping), admission = r => {
            admissions += 1
            Consequence.success(Record(r.fields.map { field =>
              if (field.key == "summary") Field("summary", Field.Value.Single(Summary("admitted", 9))) else field
            }))
          }, decoding = r => { finals :+= _value(r, "summary"); Consequence.success(r) })
          val logical = _five_record
          When("the public read is split into admission and final decode beside a composed read")
          val physical = persistent.toStoreRecordC(_collection, logical)
          val admitted = physical.flatMap(persistent.admitStoreRecordC(_collection, _))
          val split = admitted.flatMap(persistent.decodeAdmittedStoreRecordC(_collection, _))
          val composed = physical.flatMap(persistent.fromStoreRecordC(_collection, _))
          Then("both reads expose the admitted logical values and convert the application class once per read")
          split shouldBe composed
          _value(split.get.get, "summary") shouldBe Summary("admitted", 9)
          _value(split.get.get, "payload") shouldBe _value(logical, "payload")
          _value(split.get.get, "evidence") shouldBe Record.empty
          classdecodes shouldBe 2
          admissions shouldBe 2
          finals shouldBe Vector(Summary("admitted", 9), Summary("admitted", 9))
        }
      }

      "STAGE-05 forward an explicit persistent declaration into create" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-05, rules:R1,R9,R12, phase:100"
      ) {
        "retain logical forwarding and the existing physical and field-name contracts" in {
          Given("a typed logical persistent with a distinct old physical hook and a derived undeclared create adapter")
          var logicalcalls = 0
          var physicalcalls = 0
          val mapping = _five_mapping(_summary_binding())
          val persistent = new EntityPersistent[Record] {
            override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
            def id(e: Record): EntityId = _id
            def toRecord(e: Record): Record = e
            def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
            override def toLogicalStoreRecord(e: Record): Consequence[Record] = {
              logicalcalls += 1
              Consequence.success(e)
            }
            override def toStoreRecord(e: Record): Record = { physicalcalls += 1; _legacy_record }
            override def storeFieldName(logicalName: String): String = "legacy_" + logicalName
          }
          val create = EntityPersistentCreate.fromPersistent(persistent)
          val derived = EntityPersistentCreate.derived[CreateFixture](_collection)
          When("canonical create crosses the strict boundary and the legacy create hook is called separately")
          val encoded = create.toStoreRecordC(_collection, _five_record)
          val old = create.toStoreRecord(_five_record)
          val undeclared = derived.toStoreRecordC(_collection, CreateFixture(None, _five_record))
          Then("strict create uses the forwarded declaration and logical hook while old forwarding stays physical")
          encoded.isSuccess shouldBe true
          _value(encoded.get.get, "summary_json").isInstanceOf[String] shouldBe true
          create.storeRowMapping shouldBe Some(mapping)
          logicalcalls shouldBe 1
          physicalcalls shouldBe 1
          old shouldBe _legacy_record
          create.storeFieldName("originalProposal") shouldBe "originalProposal"
          _expect_reason(undeclared, "missing-mapping")
        }
      }
    }

    "strict metadata and causal admission" which {
      "STAGE-02 require declarations before every logical hook" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-02, rules:R1,R6,R10, phase:100"
      ) {
        "reject absent null recursive and incompatible declarations without hook or fallback calls" in {
          Given("undeclared adapters, null metadata and an empty nested array missing its captured application decoder")
          var calls = 0
          val observe: Record => Consequence[Record] = r => { calls += 1; Consequence.success(r) }
          val missing = _summary_binding(_summary_encoder, null)
          val nested = EntityStoreRowMapping(Vector(_field("value", "stored",
            EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(missing)), Multiplicity.ZeroMore)))
          val field = _field("value", "stored", EntityStoreFieldBinding.string)
          val incompatible = EntityStoreRowMapping(Vector(field.copy(domain = ValueDomain(XSemiStructuredData))))
          val declarations = Vector[Option[EntityStoreRowMapping]](None, null, Some(null), Some(nested), Some(incompatible))
          val logical = Record.createFull(Vector("value" -> Vector(Vector.empty)))
          val physical = Record.createFull(Vector("stored" -> "[[]]"))
          val valid = EntityStoreRowMapping(Vector(field))
          val subjects = declarations.map(_persistent(_, observe, observe, observe)) :+
            _persistent(Some(valid), observe, observe, observe)
          val collections = Vector.fill(declarations.size)(_collection) :+ null
          When("write admission composed read final decode create and update request strict conversion")
          val results = subjects.zip(collections).map { case (persistent, collection) =>
            Vector(persistent.toStoreRecordC(collection, logical), persistent.admitStoreRecordC(collection, physical),
              persistent.fromStoreRecordC(collection, physical), persistent.decodeAdmittedStoreRecordC(collection, logical),
              EntityPersistentCreate.fromPersistent(persistent).toStoreRecordC(collection, logical),
              _update(persistent.storeRowMapping, observe).toStoreChangesRecordC(collection, logical))
          }
          Then("no logical hook is evaluated and each metadata failure retains its declared reason")
          calls shouldBe 0
          results.take(3).foreach(_.foreach(x => _expect_reason(x, "missing-mapping")))
          results(3).foreach(x => _expect_reason(x, "missing-codec-binding"))
          results(4).foreach(x => _expect_reason(x, "incompatible-mapping"))
          results.last.foreach(x => _expect_reason(x, "missing-mapping"))
          Vector("encode", "decode", "decode", "decode-admitted", "encode", "encode-patch").zip(results.head).foreach {
            case (stage, result) =>
              _facets(result) should contain(Descriptor.Facet.State(stage))
              _facets(result) should contain(Descriptor.Facet.FieldPath("<mapping>"))
              _facets(result) should contain(Descriptor.Facet.Key("<mapping>"))
          }
          results.last.foreach(x => _facets(x) should contain(Descriptor.Facet.Id("<missing-collection>")))
        }
      }

      "STAGE-08 reject malformed physical rows before logical admission" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-08, rules:R3,R4,R6,R10, phase:100"
      ) {
        "retain independent row failure taxonomy source facets and causal order" in {
          Given("four independent physical errors beside a valid field and logical hooks that count invocations")
          var admissions = 0
          var finals = 0
          val mapping = EntityStoreRowMapping(Vector(_field("json", "json_text", EntityStoreFieldBinding.json),
            _field("record", "record_text", EntityStoreFieldBinding.semiStructuredData),
            _field("members", "members_text", EntityStoreFieldBinding.array(EntityStoreFieldBinding.string), Multiplicity.ZeroMore),
            _field("valid", "valid_text", EntityStoreFieldBinding.string)))
          val persistent = _persistent(Some(mapping),
            admission = r => { admissions += 1; Consequence.success(r) },
            decoding = r => { finals += 1; Consequence.success(r) })
          val physical = Record.createFull(Vector("json_text" -> "{broken", "record_text" -> Json.obj(),
            "members_text" -> "[1,true,\"valid\"]", "valid_text" -> "exact"))
          When("strict composed read and the accepted row mapper interpret the same failing physical row")
          val result = persistent.fromStoreRecordC(_collection, physical)
          val row = mapping.decode(_collection, physical)
          Then("row failures remain identical and no partial entity or logical hook is reached")
          result.get shouldBe None
          admissions shouldBe 0
          finals shouldBe 0
          _causes(result).zip(_causes(row)).foreach { case (actual, original) =>
            actual.status shouldBe original.status
            actual.observation.taxonomy shouldBe original.observation.taxonomy
            actual.observation.cause shouldBe original.observation.cause
            actual.observation.occurrence shouldBe original.observation.occurrence
            actual.observation.origin shouldBe original.observation.origin
          }
          _causes(result).size shouldBe 4
          _causes(result).map(_.observation.cause.descriptor.facets.collect {
            case Descriptor.Facet.Reason(reason) => reason
          }.head) shouldBe List("malformed-stored-json", "wrong-physical-value", "wrong-structured-shape", "wrong-structured-shape")
          _facets(result) should contain(Descriptor.Facet.FieldPath("index[0]"))
          _facets(result) should contain(Descriptor.Facet.FieldPath("index[1]"))
          _facets(result) should contain(Descriptor.Facet.State("decode"))
        }
      }

      "STAGE-09 protect logical application hooks without losing returned failures" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-09, rules:R10, phase:100"
      ) {
        "sanitize exceptions and invalid successes while retaining structured multi-cause evidence" in {
          Given("throwing, null-result and null-success hooks plus a structured ordered two-cause application rejection")
          val mapping = EntityStoreRowMapping(Vector(_field("value", "stored", EntityStoreFieldBinding.string)))
          val logical = Record.createFull(Vector("value" -> "PRIVATE-PAYLOAD"))
          val physical = Record.createFull(Vector("stored" -> "PRIVATE-PAYLOAD"))
          val hooks = Vector[Record => Consequence[Record]](
            _ => throw new IllegalStateException("PRIVATE-EXCEPTION"), _ => null, _ => Consequence.success(null))
          val first = Consequence.argumentInvalid[Record]("application rejected first").leftMap { conclusion =>
            val observation = conclusion.observation
            conclusion.copy(observation = observation.copy(cause = observation.cause.addFacet(Descriptor.Facet.Reason("first"))))
          }
          val second = Consequence.valueFormatError[Record]("application rejected second").leftMap { conclusion =>
            val observation = conclusion.observation
            conclusion.copy(observation = observation.copy(cause = observation.cause.addFacet(Descriptor.Facet.Reason("second"))))
          }
          val rejection: Consequence[Record] = Consequence.zipN(Vector(first, second)).map(_ => logical)
          val rejected = _persistent(Some(mapping), _ => rejection, _ => rejection, _ => rejection)
          val fatal = _persistent(Some(mapping), _ => throw new LinkageError("fatal"))
          When("each new logical hook encounters its modeled failure and a fatal write remains fatal")
          val results = hooks.flatMap { hook =>
            val persistent = _persistent(Some(mapping), hook, hook, hook)
            Vector(persistent.toStoreRecordC(_collection, logical), persistent.admitStoreRecordC(_collection, physical),
              persistent.decodeAdmittedStoreRecordC(_collection, logical))
          }
          val returned = Vector(rejected.toStoreRecordC(_collection, logical), rejected.admitStoreRecordC(_collection, physical),
            rejected.decodeAdmittedStoreRecordC(_collection, logical))
          val fatalresult = intercept[LinkageError](fatal.toStoreRecordC(_collection, logical))
          Then("NonFatal details are absent and returned failures preserve all original causal evidence")
          results.zipWithIndex.foreach { case (result, index) =>
            _expect_reason(result, "application-hook-failure")
            _facets(result) should contain(Descriptor.Facet.Policy("entity.store.logical-type"))
            _facets(result) should contain(Descriptor.Facet.Id(_collection.print))
            _facets(result) should contain(Descriptor.Facet.FieldPath("<hook>"))
            _facets(result) should contain(Descriptor.Facet.Key("<hook>"))
            _facets(result) should contain(Descriptor.Facet.State(Vector("logical-write", "logical-admit", "logical-decode")(index % 3)))
            _causes(result).foreach { cause =>
              cause.displayMessage should not include "PRIVATE"
              cause.show should not include "PRIVATE"
              cause.observation.cause.descriptor.facets.exists {
                case _: Descriptor.Facet.Exception | _: Descriptor.Facet.Value | _: Descriptor.Facet.RecordData => true
                case _ => false
              } shouldBe false
            }
          }
          returned.foreach { result =>
            _causes(result).size shouldBe 2
            _causes(result).zip(_causes(rejection)).foreach { case (decorated, original) =>
              decorated.status shouldBe original.status
              decorated.observation.copy(cause = original.observation.cause) shouldBe original.observation
              decorated.observation.cause.descriptor.facets should contain allElementsOf original.observation.cause.descriptor.facets
              decorated.observation.cause.descriptor.facets should not contain Descriptor.Facet.Reason("application-hook-failure")
            }
          }
          fatalresult.getMessage shouldBe "fatal"
        }
      }
    }

    "raw logical update records" which {
      "STAGE-06 normalize native Update instructions once" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-06, rules:R2,R4,R7,R9, phase:100"
      ) {
        "omit required Noop encode SetValue and preserve nullable clear as the actual physical marker" in {
          Given("raw updates containing required omission, nullable clear, exact String, empty structures and optional None")
          var calls = 0
          val mapping = EntityStoreRowMapping(Vector(_field("required", "required_text", EntityStoreFieldBinding.string),
            _field("optional", "optional_text", EntityStoreFieldBinding.string, Multiplicity.ZeroOne),
            _field("original", "original_text", EntityStoreFieldBinding.string),
            _field("evidence", "evidence_text", EntityStoreFieldBinding.semiStructuredData),
            _field("labels", "labels_text", EntityStoreFieldBinding.array(EntityStoreFieldBinding.string), Multiplicity.ZeroMore),
            _field("absent", "absent_text", EntityStoreFieldBinding.string, Multiplicity.ZeroOne)))
          val update = _update(Some(mapping), r => { calls += 1; Consequence.success(r) })
          val original = " \n{broken} 雪\\\"\t "
          val raw = Record.createFull(Vector("required" -> Update.Noop, "optional" -> Update.SetNull,
            "original" -> Update.SetValue(original), "evidence" -> Update.SetValue(Record.empty),
            "labels" -> Update.SetValue(Vector.empty[String]), "absent" -> None))
          val requiredclear = Record.createFull(Vector("required" -> Update.SetNull))
          val barenull = Record.createFull(Vector("optional" -> null))
          val nested = Record.createFull(Vector("original" -> Update.SetValue(Update.SetValue("twice"))))
          When("public strict update produces physical changes for valid and invalid raw instructions")
          val encoded = update.toStoreChangesRecordC(_collection, raw)
          val clearresult = update.toStoreChangesRecordC(_collection, requiredclear)
          val nullresult = update.toStoreChangesRecordC(_collection, barenull)
          val nestedresult = update.toStoreChangesRecordC(_collection, nested)
          Then("one normalization preserves the clear marker and structured presence without concealing forbidden values")
          _values(encoded.get.get) shouldBe Vector("optional_text" -> Update.SetNull, "original_text" -> original,
            "evidence_text" -> "{}", "labels_text" -> "[]")
          _expect_reason(clearresult, "null-not-allowed")
          _expect_reason(nullresult, "explicit-clear-required")
          _expect_reason(nestedresult, "invalid-row-shape")
          calls shouldBe 4
        }
      }

      "STAGE-07 check raw structure before omission" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-07, rules:R1,R7,R10, phase:100"
      ) {
        "reject duplicate undeclared null and non-Single fields without normalization throws or partial conversion" in {
          Given("raw rows whose invalid fields would disappear or throw if native normalization ran first")
          var encodes = 0
          val binding = _summary_binding(Encoder.instance[Summary] { value => encodes += 1; _summary_encoder(value) }, _summary_decoder)
          val mapping = EntityStoreRowMapping(Vector(_field("summary", "summary_json", binding),
            _field("required", "required_text", EntityStoreFieldBinding.string)))
          val validfield = Field("summary", Field.Value.Single(Update.SetValue(Summary("kept", 1))))
          val nonsingle = new Field.Value { def single: Any = Update.Noop; def print: String = "fixture" }
          val rows = Vector[Record](null, Record(null), Record(Vector(null)), Record(Vector(Field("required", null))),
            Record(Vector(Field("required", nonsingle))),
            Record(Vector(validfield, Field("required", Field.Value.Single(Update.Noop)),
              Field("required", Field.Value.Single(Update.Noop)))))
          val unknown = Record(Vector(validfield, Field("undeclared", Field.Value.Single(Update.Noop))))
          val update = _update(Some(mapping))
          When("the public strict update validates every raw row before native normalization")
          val invalid = rows.map(update.toStoreChangesRecordC(_collection, _))
          val undeclared = update.toStoreChangesRecordC(_collection, unknown)
          Then("typed structure and declaration failures precede any application encoding or partial patch")
          _expect_reason(invalid.head, "application-hook-failure")
          invalid.tail.foreach(x => _expect_reason(x, "invalid-row-shape"))
          _expect_reason(undeclared, "missing-mapping")
          _facets(undeclared) should contain(Descriptor.Facet.FieldPath("undeclared"))
          (invalid :+ undeclared).foreach(_.get shouldBe None)
          encodes shouldBe 0
        }
      }
    }

    "compatibility and presence" which {
      "STAGE-10 keep the legacy physical boundary callable" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-10, rules:R1,R9, phase:100"
      ) {
        "preserve legacy hooks and exact collection helpers while strict methods require declarations" in {
          Given("a legacy physical adapter declaring no mapping and an explicitly different requested collection")
          var writes = 0
          var reads = 0
          val legacy = new EntityPersistent[Record] {
            def id(e: Record): EntityId = _id
            def toRecord(e: Record): Record = e
            def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
            override def toStoreRecord(e: Record): Record = { writes += 1; _legacy_record }
            override def fromStoreRecord(r: Record): Consequence[Record] = { reads += 1; Consequence.success(r) }
            override def storeFieldName(logicalName: String): String = "legacy_" + logicalName
          }
          val other = EntityCollectionId("test", "stage", "other")
          When("legacy physical write read admission final decode and exact collection helpers remain in use")
          val stored = legacy.toStoreRecord(Record.empty)
          val read = legacy.fromStoreRecord(stored)
          val admitted = legacy.admitStoreRecord(stored)
          val finalread = admitted.flatMap(legacy.decodeAdmittedStoreRecord)
          val exact = EntityPersistent._decode_store_record(legacy, _collection, stored)
          val mismatch = EntityPersistent._decode_admitted_store_record(legacy, other, stored)
          val strict = Vector(legacy.toStoreRecordC(_collection, stored), legacy.admitStoreRecordC(_collection, stored),
            legacy.fromStoreRecordC(_collection, stored), legacy.decodeAdmittedStoreRecordC(_collection, stored))
          Then("physical contracts and collection rejection survive and strict calls never infer payload metadata")
          stored shouldBe _legacy_record
          read shouldBe Consequence.success(stored)
          admitted shouldBe Consequence.success(stored)
          finalread shouldBe read
          exact shouldBe read
          _expect_reason(mismatch, "entity-codec-collection-mismatch")
          legacy.storeFieldName("value") shouldBe "legacy_value"
          writes shouldBe 1
          reads shouldBe 4
          strict.foreach(x => _expect_reason(x, "missing-mapping"))
        }
      }

      "STAGE-11 preserve declared null and empty presence" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-11, rules:R3,R4,R6,R7,R9, phase:100"
      ) {
        "distinguish required native null optional native null JSON null and present structured empties" in {
          Given("required and nullable declarations, nested arrays, exact framework values and an unlisted field")
          val mapping = EntityStoreRowMapping(Vector(_field("required", "required_text", EntityStoreFieldBinding.string),
            _field("optional", "optional_text", EntityStoreFieldBinding.string, Multiplicity.ZeroOne),
            _field("json", "json_text", EntityStoreFieldBinding.json),
            _field("record", "record_text", EntityStoreFieldBinding.semiStructuredData),
            _field("nested", "nested_text", EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(EntityStoreFieldBinding.json)), Multiplicity.ZeroMore)),
            Set("id", "revision", "permission", "contentBody"))
          val persistent = _persistent(Some(mapping))
          val body = ContentBody("<p>opaque 雪 {}</p>")
          val logical = Record.createFull(Vector("required" -> "", "optional" -> null, "json" -> Json.Null,
            "record" -> Record.empty, "nested" -> Vector(Vector.empty, Vector(Json.Null, Json.obj())),
            "id" -> _id, "revision" -> 0L, "permission" -> Json.obj(), "contentBody" -> body))
          val requirednull = Record(logical.fields.map { field =>
            if (field.key == "required") Field("required", Field.Value.Single(null)) else field
          })
          val missing = Record(logical.fields.filterNot(_.key == "required"))
          val unknown = Record(logical.fields :+ Field("unlisted", Field.Value.Single(Update.Noop)))
          When("strict full stages interpret null empty and unknown-key value spaces")
          val encoded = persistent.toStoreRecordC(_collection, logical)
          val decoded = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
          val nullwrite = persistent.toStoreRecordC(_collection, requirednull)
          val nullread = persistent.fromStoreRecordC(_collection,
            Record(encoded.get.get.fields.map { field =>
              if (field.key == "required_text") Field("required_text", Field.Value.Single(null)) else field
            }))
          val absent = persistent.toStoreRecordC(_collection, missing)
          val undeclared = persistent.toStoreRecordC(_collection, unknown)
          Then("native null remains a present nullable Field and structured null or empties retain their selected values")
          _values(decoded.get.get) shouldBe _values(logical)
          _value(encoded.get.get, "optional_text") shouldBe (null: Any)
          _value(encoded.get.get, "json_text") shouldBe "null"
          _value(encoded.get.get, "record_text") shouldBe "{}"
          _value(encoded.get.get, "nested_text") shouldBe "[[],[null,{}]]"
          Vector("id", "revision", "permission", "contentBody").foreach { key =>
            _value(encoded.get.get, key) shouldBe _value(logical, key)
          }
          _expect_reason(nullwrite, "null-not-allowed")
          _expect_reason(nullread, "null-not-allowed")
          _expect_reason(absent, "missing-required-field")
          _expect_reason(undeclared, "missing-mapping")
        }
      }

      "STAGE-12 retain exact public String roundtrips" should afterWord(
        "in spec:entity-store-persistence-stages, example:STAGE-12, rules:R1,R2,R8,R12, phase:100"
      ) {
        "preserve arbitrary Unicode escape whitespace and JSON-looking text under a renamed column" in {
          Given("a public persistent String stage, a renamed storage column and arbitrary or adversarial exact text")
          val mapping = EntityStoreRowMapping(Vector(_field("proposal", "proposal_text", EntityStoreFieldBinding.string)))
          val persistent = _persistent(Some(mapping))
          val texts = Gen.oneOf(Arbitrary.arbitrary[String],
            Gen.listOf(Gen.oneOf("雪", "🙂", "é", "\n", "\t", "\u0000", "\"", "\\", " ")).map(_.mkString),
            Gen.oneOf("", "{}", "[]", "null", "{broken", "[1,]", " 12.30 "))
          val property = Prop.forAll(texts) { text =>
            val logical = Record.createFull(Vector("proposal" -> text))
            val encoded = persistent.toStoreRecordC(_collection, logical)
            val decoded = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
            encoded.get.exists(x => _values(x) == Vector("proposal_text" -> text)) &&
              decoded.get.exists(x => _values(x) == Vector("proposal" -> text))
          }
          When("the public strict write and read roundtrip interprets at least two hundred generated cases")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(200), property)
          Then("every successful case preserves all characters without inferred parsing or formatting")
          checked.passed shouldBe true
          checked.succeeded should be >= 200
        }
      }
    }
  }

  private val _collection: EntityCollectionId = EntityCollectionId("test", "stage", "candidate")
  private lazy val _id: EntityId = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
    "entry", "candidate_1", _collection, entropy = "candidate_1")
  private val _summary_encoder: Encoder[Summary] = Encoder.forProduct2("label", "score")(x => (x.label, x.score))
  private val _summary_decoder: Decoder[Summary] = Decoder.forProduct2("label", "score")(Summary.apply)
  private val _legacy_record: Record = Record.createFull(Vector("legacy_physical" -> "already encoded"))

  private def _summary_binding(
    encoder: Encoder[Summary] = _summary_encoder,
    decoder: Decoder[Summary] = _summary_decoder
  ): EntityStoreFieldBinding = EntityStoreFieldBinding.application[Summary]("stage-summary-v1")(
    using summon[ClassTag[Summary]], encoder, decoder)

  private def _field(
    logicalname: String,
    storagename: String,
    binding: EntityStoreFieldBinding,
    multiplicity: Multiplicity = Multiplicity.One
  ): EntityStoreField = EntityStoreField(logicalname, storagename, ValueDomain(binding.datatype, multiplicity), binding)

  private def _five_mapping(binding: EntityStoreFieldBinding): EntityStoreRowMapping = EntityStoreRowMapping(Vector(
    _field("originalProposal", "original_proposal", EntityStoreFieldBinding.string),
    _field("payload", "payload_json", EntityStoreFieldBinding.json),
    _field("evidence", "evidence_json", EntityStoreFieldBinding.semiStructuredData, Multiplicity.ZeroOne),
    _field("labels", "labels_json", EntityStoreFieldBinding.array(EntityStoreFieldBinding.string), Multiplicity.ZeroMore),
    _field("summary", "summary_json", binding)), Set("id", "revision", "permission", "contentBody"))

  private def _five_record: Record = Record.createFull(Vector("originalProposal" -> " {broken} 雪 ",
    "payload" -> Json.Null, "evidence" -> Record.empty, "labels" -> Vector.empty[String], "summary" -> Summary("raw", 1)))

  private def _persistent(
    mapping: Option[EntityStoreRowMapping],
    writing: Record => Consequence[Record] = r => Consequence.success(r),
    admission: Record => Consequence[Record] = r => Consequence.success(r),
    decoding: Record => Consequence[Record] = r => Consequence.success(r)
  ): EntityPersistent[Record] = new EntityPersistent[Record] {
    override def storeRowMapping: Option[EntityStoreRowMapping] = mapping
    def id(e: Record): EntityId = _id
    def toRecord(e: Record): Record = e
    def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
    override def toLogicalStoreRecord(e: Record): Consequence[Record] = writing(e)
    override def admitLogicalStoreRecord(r: Record): Consequence[Record] = admission(r)
    override def decodeAdmittedLogicalStoreRecord(r: Record): Consequence[Record] = decoding(r)
    override def toStoreRecord(e: Record): Record = throw new IllegalStateException("PRIVATE-LEGACY-WRITE")
    override def fromStoreRecord(r: Record): Consequence[Record] = throw new IllegalStateException("PRIVATE-LEGACY-READ")
    override private[cncf] def admitStoreRecord(r: Record): Consequence[Record] =
      throw new IllegalStateException("PRIVATE-LEGACY-ADMIT")
    override private[cncf] def decodeAdmittedStoreRecord(r: Record): Consequence[Record] =
      throw new IllegalStateException("PRIVATE-LEGACY-DECODE")
  }

  private def _update(
    mapping: Option[EntityStoreRowMapping],
    writing: Record => Consequence[Record] = r => Consequence.success(r)
  ): EntityPersistentUpdate[Record] = new EntityPersistentUpdate[Record] {
    override def storeRowMapping: Option[EntityStoreRowMapping] = mapping
    def collection(e: Record): EntityCollectionId = _collection
    def toRecord(e: Record): Record = e
    def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
    override def toLogicalStoreRecord(e: Record): Consequence[Record] = writing(e)
    override def toStoreRecord(e: Record): Record = throw new IllegalStateException("PRIVATE-LEGACY-UPDATE")
  }

  private def _values(record: Record): Vector[(String, Any)] = record.fields.map(x => x.key -> x.value.single)
  private def _value(record: Record, key: String): Any = record.fields.find(_.key == key).get.value.single
  private def _causes(result: Consequence[?]): List[Conclusion] = result match {
    case Consequence.Failure(conclusion) => conclusion.causes
    case _ => fail("Expected a structured persistence-stage failure")
  }
  private def _facets(result: Consequence[?]): Vector[Descriptor.Facet] =
    _causes(result).toVector.flatMap(_.observation.cause.descriptor.facets)
  private def _expect_reason(result: Consequence[?], reason: String): Unit = {
    result.isSuccess shouldBe false
    _facets(result) should contain(Descriptor.Facet.Reason(reason))
  }
}

object EntityPersistentStoreMappingSpec {
  private final case class Summary(label: String, score: Int)
  private final case class Proposal(id: EntityId, original: String, payload: Json, evidence: Record,
    labels: Vector[String], summary: Summary, revision: Long, permission: String, body: ContentBody)
  private final case class CreateFixture(id: Option[EntityId], row: Record) extends EntityPersistableCreate {
    def toRecord(): Record = row
  }
}
