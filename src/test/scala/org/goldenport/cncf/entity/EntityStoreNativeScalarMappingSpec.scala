package org.goldenport.cncf.entity

import scala.reflect.ClassTag
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, parser}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.datatype.Name
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XBoolean, XDecimal, XInt, XInteger, XLong, XString}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Update
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification of docs/spec/entity-store-native-scalar-mapping.md.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreNativeScalarMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Declared native scalar storage" should {
    "canonical types and strict stages" which {
      "NATIVE-01 retain the Core Name domain and plaintext representation" in {
        Given("a canonical Named(name) declaration capturing the actual Core Name Codec and distinct logical and physical hooks")
        val corecodec = summon[Codec[Name]]
        var encodes = 0
        var decodes = 0
        var events = Vector.empty[(String, Any)]
        val binding = EntityStoreFieldBinding.nativeScalar[Name](DataType.Named("name"),
          EntityStoreScalarRepresentation.Text, "core-name-v1")(using summon[ClassTag[Name]],
          Encoder.instance { value => encodes += 1; corecodec(value) },
          Decoder.instance { cursor => decodes += 1; corecodec(cursor) })
        val mapping = EntityStoreRowMapping(Vector(_field("name", "candidate_name", binding)))
        val name = Name("  雪🙂 {\"name\":1}  ")
        val persistent = new EntityPersistent[Name] {
          override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
          def id(e: Name): EntityId = _id
          def toRecord(e: Name): Record = _record("name" -> e)
          def fromRecord(r: Record): Consequence[Name] = Consequence.success(_value(r, "name").asInstanceOf[Name])
          override def toLogicalStoreRecord(e: Name): Consequence[Record] = {
            events :+= "write" -> e
            Consequence.success(toRecord(e))
          }
          override def admitLogicalStoreRecord(r: Record): Consequence[Record] = {
            events :+= "admit" -> _value(r, "name")
            Consequence.success(r)
          }
          override def decodeAdmittedLogicalStoreRecord(r: Record): Consequence[Name] = {
            events :+= "decode" -> _value(r, "name")
            fromRecord(r)
          }
          override def toStoreRecord(e: Name): Record = {
            events :+= "physical-write" -> e.value
            _record("candidate_name" -> e.value)
          }
          override def fromStoreRecord(r: Record): Consequence[Name] = {
            val text = _value(r, "candidate_name").asInstanceOf[String]
            events :+= "physical-read" -> text
            Consequence.success(Name(text))
          }
        }
        When("strict write/read interpret Name, and the legacy physical hooks are invoked once separately")
        val physical = persistent.toStoreRecordC(_collection, name)
        val restored = physical.flatMap(persistent.fromStoreRecordC(_collection, _))
        val legacy = persistent.toStoreRecord(name)
        val legacyrestored = persistent.fromStoreRecord(legacy)
        Then("canonical metadata, actual Name and exact native text survive with one application conversion per direction")
        binding.datatype shouldBe DataType.Named("name")
        binding.applicationClassName shouldBe Some(classOf[Name].getName)
        binding.elementBinding shouldBe None
        binding.storageCodecIdentity shouldBe "nativeScalar(text,core-name-v1)"
        _value(physical.get.get, "candidate_name") shouldBe name.value
        restored shouldBe Consequence.success(name)
        legacyrestored shouldBe Consequence.success(name)
        events shouldBe Vector("write" -> name, "admit" -> name, "decode" -> name,
          "physical-write" -> name.value, "physical-read" -> name.value)
        encodes shouldBe 1
        decodes shouldBe 1
      }

      "NATIVE-02 retain primitive canonical types and renamed physical scalars" in {
        Given("explicit Int Long Boolean and BigDecimal codecs under their canonical Core domains")
        var encodes = Vector.fill(4)(0)
        var decodes = Vector.fill(4)(0)
        def _binding_[A: ClassTag](datatype: DataType, representation: EntityStoreScalarRepresentation,
          index: Int)(using encoder: Encoder[A], decoder: Decoder[A]): EntityStoreFieldBinding = {
          _native[A](datatype, representation)(using summon[ClassTag[A]],
            Encoder.instance { value => encodes = encodes.updated(index, encodes(index) + 1); encoder(value) },
            Decoder.instance { cursor => decodes = decodes.updated(index, decodes(index) + 1); decoder(cursor) })
        }
        val bindings = Vector(_binding_[Int](XInt, EntityStoreScalarRepresentation.Number, 0),
          _binding_[Long](XLong, EntityStoreScalarRepresentation.Number, 1),
          _binding_[Boolean](XBoolean, EntityStoreScalarRepresentation.Boolean, 2),
          _binding_[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number, 3))
        val names = Vector("count", "sequence", "approved", "amount")
        val mapping = EntityStoreRowMapping(names.zip(bindings).map { case (name, binding) =>
          _field(name, "stored_" + name, binding)
        })
        val logical = _record("count" -> 7, "sequence" -> Long.MaxValue, "approved" -> true,
          "amount" -> BigDecimal("12345678901234567890.01234567890123456789"))
        val persistent = _persistent(mapping)
        When("public strict stages write and reconstruct the primitive logical record")
        val physical = persistent.toStoreRecordC(_collection, logical)
        val restored = physical.flatMap(persistent.fromStoreRecordC(_collection, _))
        Then("typed values, One multiplicity and exact storage names use Number BigDecimal and Boolean scalars")
        restored shouldBe Consequence.success(logical)
        mapping.fields.map(_.domain.datatype) shouldBe Vector(XInt, XLong, XBoolean, XDecimal)
        mapping.fields.map(_.domain.multiplicity) shouldBe Vector.fill(4)(Multiplicity.One)
        physical.get.get.fields.map(_.key) shouldBe names.map("stored_" + _)
        _value(physical.get.get, "stored_count") shouldBe BigDecimal(7)
        _value(physical.get.get, "stored_sequence") shouldBe BigDecimal(Long.MaxValue)
        _value(physical.get.get, "stored_approved") shouldBe true
        _value(physical.get.get, "stored_amount") shouldBe _value(logical, "amount")
        Vector("stored_count", "stored_sequence", "stored_amount").foreach { key =>
          _value(physical.get.get, key).isInstanceOf[BigDecimal] shouldBe true
        }
        encodes shouldBe Vector.fill(4)(1)
        decodes shouldBe Vector.fill(4)(1)
      }

      "NATIVE-03 preserve huge integers and high precision decimals exactly" in {
        Given("a huge integral value and a decimal with more precision than Double under explicit Number bindings")
        val integer = BigInt("123456789012345678901234567890123456789012345678901234567890")
        val decimal = BigDecimal("987654321098765432109876543210.123456789012345678901234567890123456789")
        val mapping = EntityStoreRowMapping(Vector(
          _field("integer", "stored_integer", _native[BigInt](XInteger, EntityStoreScalarRepresentation.Number)),
          _field("decimal", "stored_decimal", _native[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number))))
        val logical = _record("integer" -> integer, "decimal" -> decimal)
        val persistent = _persistent(mapping)
        val numerictext = _record("stored_integer" -> integer.toString, "stored_decimal" -> decimal.toString)
        val javanumbers = _record("stored_integer" -> integer.bigInteger, "stored_decimal" -> decimal.bigDecimal)
        When("strict stages encode exact values and decode native exact numbers and explicitly selected numeric text")
        val encoded = persistent.toStoreRecordC(_collection, logical)
        val restored = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
        val textual = persistent.fromStoreRecordC(_collection, numerictext)
        val javaresult = persistent.fromStoreRecordC(_collection, javanumbers)
        Then("mathematical values and physical BigDecimals retain every supplied significant digit")
        _value(encoded.get.get, "stored_integer") shouldBe BigDecimal(integer)
        _value(encoded.get.get, "stored_decimal") shouldBe decimal
        Vector(restored, textual, javaresult).foreach(_ shouldBe Consequence.success(logical))
      }
    }

    "explicit physical representations" which {
      "NATIVE-04 admit numeric classes and reject fractional overflow unsupported and nonfinite values" in {
        Given("declared Int and BigDecimal Number readers with each admitted physical numeric class")
        val integral = EntityStoreRowMapping(Vector(_field("value", "stored",
          _native[Int](XInt, EntityStoreScalarRepresentation.Number))))
        val decimal = EntityStoreRowMapping(Vector(_field("value", "stored",
          _native[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number))))
        val classes = Vector[Any](7.toByte, 7.toShort, 7, 7L, BigInt(7), BigDecimal(7),
          new java.math.BigInteger("7"), new java.math.BigDecimal("7"))
        val invalidints = Vector[Any](BigDecimal("1.5"), BigInt(Int.MaxValue) + 1, Long.MaxValue)
        val unsupported = Vector[Any](null, Json.fromInt(7), Vector(7), new Object, true)
        val nonfinite = Vector[Any](Float.NaN, Float.PositiveInfinity, Float.NegativeInfinity,
          Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity)
        val enormous = parser.parse("1e2147483649").toOption.get
        val hugeencoder: Encoder[BigDecimal] = Encoder.instance(_ => enormous)
        val huge = EntityStoreRowMapping(Vector(_field("value", "stored",
          EntityStoreFieldBinding.nativeScalar[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number,
            "enormous-number")(using summon[ClassTag[BigDecimal]], hugeencoder, summon[Decoder[BigDecimal]]))))
        When("public row conversion interprets admitted classes and modeled numeric failures")
        val admitted = classes.map(value => integral.decode(_collection, _record("stored" -> value)))
        val fractional = invalidints.map(value => integral.decode(_collection, _record("stored" -> value)))
        val rejected = unsupported.map(value => integral.decode(_collection, _record("stored" -> value)))
        val finite = Vector[Any](1.25f, 1.25d).map(value => decimal.decode(_collection, _record("stored" -> value)))
        val nonfiniteresults = nonfinite.map(value => decimal.decode(_collection, _record("stored" -> value)))
        val unrepresentable = huge.encode(_collection, _record("value" -> BigDecimal(1)))
        Then("exact numeric classes return typed values and no invalid or nonfinite value falls back to text")
        admitted.foreach(_ shouldBe Consequence.success(_record("value" -> 7)))
        finite.foreach(_ shouldBe Consequence.success(_record("value" -> BigDecimal("1.25"))))
        fractional.foreach(_expect_reason(_, "application-codec-failure"))
        _expect_reason(rejected.head, "null-not-allowed")
        rejected.tail.foreach(_expect_reason(_, "wrong-physical-value"))
        (nonfiniteresults :+ unrepresentable).foreach(_expect_reason(_, "unsupported-external-value"))
      }

      "NATIVE-05 accept only Boolean and canonical numeric zero or one" in {
        Given("an explicitly Boolean-represented canonical Boolean field and its SQL zero/one physical convention")
        val mapping = EntityStoreRowMapping(Vector(_field("value", "stored",
          _native[Boolean](XBoolean, EntityStoreScalarRepresentation.Boolean))))
        val zeros = Vector[Any](false, 0.toByte, 0.toShort, 0, 0L, BigInt(0), BigDecimal(0),
          new java.math.BigInteger("0"), new java.math.BigDecimal("0.00"), 0.0f, 0.0d, "0")
        val ones = Vector[Any](true, 1.toByte, 1.toShort, 1, 1L, BigInt(1), BigDecimal(1),
          new java.math.BigInteger("1"), new java.math.BigDecimal("1.00"), 1.0f, 1.0d, "1")
        val others = Vector[Any](2, -1L, BigDecimal("0.5"), "true", "false", "01", " 1", "1.0",
          Json.True, Map("value" -> 1), Vector(1), Float.NaN, Double.PositiveInfinity)
        When("row read decodes each physical value under the selected Boolean declaration")
        val falses = zeros.map(value => mapping.decode(_collection, _record("stored" -> value)))
        val trues = ones.map(value => mapping.decode(_collection, _record("stored" -> value)))
        val rejected = others.map(value => mapping.decode(_collection, _record("stored" -> value)))
        val nullresult = mapping.decode(_collection, _record("stored" -> null))
        Then("only exact zero and one become Boolean values and no truthiness or String guessing succeeds")
        falses.foreach(_ shouldBe Consequence.success(_record("value" -> false)))
        trues.foreach(_ shouldBe Consequence.success(_record("value" -> true)))
        rejected.foreach(_expect_reason(_, "wrong-physical-value"))
        _expect_reason(nullresult, "null-not-allowed")
      }

      "NATIVE-06 parse only explicitly Number-selected text" in {
        Given("Text and Number bindings with numeric object array null quoted and malformed JSON-looking Strings")
        val textmapping = EntityStoreRowMapping(Vector(_field("value", "stored",
          _native[String](XString, EntityStoreScalarRepresentation.Text))))
        val numbermapping = EntityStoreRowMapping(Vector(_field("value", "stored",
          _native[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number))))
        val texts = Vector("", "  雪🙂\t\n", "1.25", " {\"n\":1} ", "[1,true]", "null", "{broken", "\"7\"", "true")
        val rejectedtexts = texts.filterNot(_ == "1.25")
        When("Text strict stages roundtrip the supplied Strings while Number row reads select numeric roots")
        val persistent = _persistent(textmapping)
        val textresults = texts.map { text =>
          val encoded = persistent.toStoreRecordC(_collection, _record("value" -> text))
          encoded -> encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
        }
        val numeric = numbermapping.decode(_collection, _record("stored" -> "  1.25 \n"))
        val failures = rejectedtexts.map(text => numbermapping.decode(_collection, _record("stored" -> text)))
        Then("Text preserves every character and Number admits a parsed number without admitting other JSON kinds")
        texts.zip(textresults).foreach { case (text, (encoded, decoded)) =>
          _value(encoded.get.get, "stored") shouldBe text
          decoded shouldBe Consequence.success(_record("value" -> text))
        }
        numeric shouldBe Consequence.success(_record("value" -> BigDecimal("1.25")))
        failures.foreach(_expect_reason(_, "wrong-physical-value"))
      }
    }

    "complete declarations and sanitized failures" which {
      "NATIVE-07 retain shape class and application failures without disclosing payloads" in {
        Given("wrong scalar JSON kinds, Json.Null/native-null encoder roots and rejecting or throwing captured hooks")
        val wrongroots = Vector[Json](Json.fromInt(1), Json.True, Json.Null, null, Json.obj(), Json.arr())
        var wrongdecodes = 0
        val shapebindings = wrongroots.map { root =>
          EntityStoreFieldBinding.nativeScalar[String](XString, EntityStoreScalarRepresentation.Text, "shape")(
            using summon[ClassTag[String]], Encoder.instance(_ => root),
            Decoder.instance { cursor => wrongdecodes += 1; Decoder.decodeString(cursor) })
        }
        val throwingencoder: Encoder[String] = Encoder.instance(_ => throw new IllegalStateException("PRIVATE-EXCEPTION"))
        val throwingdecoder: Decoder[String] = Decoder.instance(_ => throw new IllegalStateException("PRIVATE-EXCEPTION"))
        val rejectingdecoder: Decoder[String] = Decoder.instance(cursor => Left(DecodingFailure("PRIVATE-PAYLOAD", cursor.history)))
        val nulldecoder: Decoder[String] = Decoder.instance(_ => Right(null))
        val wrongdecoder: Decoder[String] = Decoder.instance[Any](_ => Right(7)).asInstanceOf[Decoder[String]]
        val good = _native[String](XString, EntityStoreScalarRepresentation.Text)
        val encodethrow = EntityStoreFieldBinding.nativeScalar[String](XString, EntityStoreScalarRepresentation.Text, "throwing")(
          using summon[ClassTag[String]], throwingencoder, Decoder.decodeString)
        val decodebindings = Vector(throwingdecoder, rejectingdecoder, nulldecoder, wrongdecoder).map { decoder =>
          EntityStoreFieldBinding.nativeScalar[String](XString, EntityStoreScalarRepresentation.Text, "decoder")(
            using summon[ClassTag[String]], Encoder.encodeString, decoder)
        }
        val shapes = Vector("[1,true,null,{},[]]")
        val arraymapping = EntityStoreRowMapping(Vector(_field("value", "stored",
          EntityStoreFieldBinding.array(shapebindings.head), Multiplicity.ZeroMore)))
        When("public row and array conversion encounters each invalid shape class and application hook")
        val shapeerrors = shapebindings.map(binding => _single_mapping(binding).encode(_collection, _record("value" -> "PRIVATE-PAYLOAD")))
        val readshape = shapes.map(text => arraymapping.decode(_collection, _record("stored" -> text)))
        val wrongclass = _single_mapping(good).encode(_collection, _record("value" -> 7))
        val thrown = _single_mapping(encodethrow).encode(_collection, _record("value" -> "PRIVATE-PAYLOAD"))
        val decodeerrors = decodebindings.map(binding => _single_mapping(binding).decode(_collection, _record("stored" -> "PRIVATE-PAYLOAD")))
        val original = EntityStoreValueCodec.application[String]("decoder")(
          using summon[ClassTag[String]], Encoder.encodeString, rejectingdecoder).decode("\"PRIVATE-PAYLOAD\"")
        Then("shape failures precede captured decoding and original application reasons and causal taxonomy survive")
        (shapeerrors ++ readshape).foreach(_expect_reason(_, "wrong-structured-shape"))
        wrongdecodes shouldBe 0
        _expect_reason(wrongclass, "wrong-logical-value")
        (decodeerrors :+ thrown).foreach(_expect_reason(_, "application-codec-failure"))
        val actualcause = _causes(decodeerrors(1)).head
        val originalcause = _causes(original).head
        actualcause.status shouldBe originalcause.status
        actualcause.observation.taxonomy shouldBe originalcause.observation.taxonomy
        actualcause.observation.origin shouldBe originalcause.observation.origin
        actualcause.observation.occurrence shouldBe originalcause.observation.occurrence
        originalcause.observation.cause.descriptor.facets.foreach { facet =>
          actualcause.observation.cause.descriptor.facets should contain(facet)
        }
        (shapeerrors ++ readshape ++ decodeerrors ++ Vector(wrongclass, thrown)).foreach { result =>
          _causes(result).foreach(_.observation.taxonomy shouldBe Taxonomy(Taxonomy.Category.Value, Taxonomy.Symptom.Invalid))
          _messages(result).mkString should not include "PRIVATE-PAYLOAD"
          _messages(result).mkString should not include "PRIVATE-EXCEPTION"
          result.get shouldBe None
        }
      }

      "NATIVE-08 validate missing captures before hooks even inside nested empty arrays" in {
        Given("null datatype representation ClassTag Encoder Decoder and blank identity native declarations")
        var encodes = 0
        var decodes = 0
        var hooks = 0
        val encoder: Encoder[String] = Encoder.instance { value => encodes += 1; Json.fromString(value) }
        val decoder: Decoder[String] = Decoder.instance { cursor => decodes += 1; Decoder.decodeString(cursor) }
        val classtag = summon[ClassTag[String]]
        val text = EntityStoreScalarRepresentation.Text
        val bindings = Vector(
          EntityStoreFieldBinding.nativeScalar[String](null, text, "text")(using classtag, encoder, decoder),
          EntityStoreFieldBinding.nativeScalar[String](XString, null, "text")(using classtag, encoder, decoder),
          EntityStoreFieldBinding.nativeScalar[String](XString, text, "text")(using null, encoder, decoder),
          EntityStoreFieldBinding.nativeScalar[String](XString, text, "text")(using classtag, null, decoder),
          EntityStoreFieldBinding.nativeScalar[String](XString, text, "text")(using classtag, encoder, null),
          EntityStoreFieldBinding.nativeScalar[String](XString, text, " \t\n")(using classtag, encoder, decoder),
          EntityStoreFieldBinding.nativeScalar[String](XString, text, null)(using classtag, encoder, decoder))
        val observe: Record => Consequence[Record] = record => { hooks += 1; Consequence.success(record) }
        val declarations = bindings.map { binding =>
          val direct = EntityStoreRowMapping(Vector(EntityStoreField("value", "stored", ValueDomain(XString), binding)))
          val nested = EntityStoreRowMapping(Vector(EntityStoreField("value", "stored", ValueDomain(XString, Multiplicity.ZeroMore),
            EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(binding)))))
          direct -> nested
        }
        When("strict write read final decode create and update request conversion for direct and nested-empty rows")
        val results = declarations.map { case (direct, nested) =>
          Vector(direct -> (_record("value" -> "text"), _record("stored" -> "text")),
            nested -> (_record("value" -> Vector(Vector.empty[String])), _record("stored" -> "[[]]"))).flatMap {
            case (mapping, (logical, physical)) =>
              val persistent = _persistent(mapping, observe, observe, observe)
              Vector(persistent.toStoreRecordC(_collection, logical), persistent.fromStoreRecordC(_collection, physical),
                persistent.decodeAdmittedStoreRecordC(_collection, logical),
                EntityPersistentCreate.fromPersistent(persistent).toStoreRecordC(_collection, logical),
                _update(mapping, observe).toStoreChangesRecordC(_collection, logical))
          }
        }
        Then("every incomplete declaration fails before logical or captured hooks regardless of element count")
        results.head.foreach(_expect_reason(_, "missing-mapping"))
        results.tail.foreach(_.foreach(_expect_reason(_, "missing-codec-binding")))
        encodes shouldBe 0
        decodes shouldBe 0
        hooks shouldBe 0
      }

      "NATIVE-11 retain ordered field index and stage causes and reject datatype mismatch" in {
        Given("two independently failing native arrays, one scalar field and a mismatched canonical datatype declaration")
        var admissions = 0
        var finals = 0
        val text = _native[String](XString, EntityStoreScalarRepresentation.Text)
        val boolean = _native[Boolean](XBoolean, EntityStoreScalarRepresentation.Boolean)
        val number = _native[Int](XInt, EntityStoreScalarRepresentation.Number)
        val mapping = EntityStoreRowMapping(Vector(
          _field("labels", "stored_labels", EntityStoreFieldBinding.array(text), Multiplicity.ZeroMore),
          _field("flags", "stored_flags", EntityStoreFieldBinding.array(boolean), Multiplicity.ZeroMore),
          _field("count", "stored_count", number)))
        val physical = _record("stored_labels" -> "[1,null]", "stored_flags" -> "[\"true\",{}]", "stored_count" -> "{}")
        val persistent = _persistent(mapping, admission = record => { admissions += 1; Consequence.success(record) },
          decoding = record => { finals += 1; Consequence.success(record) })
        val field = _field("value", "stored", number)
        val mismatch = EntityStoreRowMapping(Vector(field.copy(domain = ValueDomain(XLong))))
        When("strict read and public row decode interpret all failures and validate an inconsistent field declaration")
        val result = persistent.fromStoreRecordC(_collection, physical)
        val row = mapping.decode(_collection, physical)
        val incompatible = mismatch.encode(_collection, _record("value" -> 7))
        Then("all five causes remain ordered with collection field storage stage index and Reason without partial success")
        _causes(result).size shouldBe _causes(row).size
        _causes(result).zip(_causes(row)).foreach { case (actual, original) =>
          actual.status shouldBe original.status
          actual.observation.taxonomy shouldBe original.observation.taxonomy
          actual.observation.cause shouldBe original.observation.cause
          actual.observation.occurrence shouldBe original.observation.occurrence
          actual.observation.origin shouldBe original.observation.origin
        }
        result.get shouldBe None
        admissions shouldBe 0
        finals shouldBe 0
        val causes = _causes(result)
        causes should have size 5
        causes.map(_.observation.cause.descriptor.facets.collectFirst {
          case Descriptor.Facet.Key(key) => key
        }.get) shouldBe List("stored_labels", "stored_labels", "stored_flags", "stored_flags", "stored_count")
        causes.foreach { cause =>
          val facets = cause.observation.cause.descriptor.facets
          facets should contain(Descriptor.Facet.Id(_collection.print))
          facets should contain(Descriptor.Facet.State("decode"))
          facets should contain(Descriptor.Facet.Policy("entity.store.logical-type"))
        }
        causes.zip(Vector("labels", "labels", "flags", "flags", "count")).foreach { case (cause, name) =>
          cause.observation.cause.descriptor.facets should contain(Descriptor.Facet.FieldPath(name))
        }
        causes.take(4).zip(Vector(0, 1, 0, 1)).foreach { case (cause, index) =>
          cause.observation.cause.descriptor.facets should contain(Descriptor.Facet.FieldPath(s"index[$index]"))
          cause.observation.cause.descriptor.facets should contain(Descriptor.Facet.Reason("wrong-structured-shape"))
        }
        causes.last.observation.cause.descriptor.facets should contain(Descriptor.Facet.Reason("wrong-physical-value"))
        _expect_reason(incompatible, "incompatible-mapping")
      }
    }

    "recursive arrays and existing row presence policy" which {
      "NATIVE-09 preserve native JSON element kinds nested order emptiness and indexed failures" in {
        Given("explicit native Text Boolean and Number children with nested arrays and counted Text application conversion")
        var encodes = 0
        var decodes = 0
        val text = EntityStoreFieldBinding.nativeScalar[String](XString, EntityStoreScalarRepresentation.Text, "array-text")(
          using summon[ClassTag[String]], Encoder.instance { value => encodes += 1; Json.fromString(value) },
          Decoder.instance { cursor => decodes += 1; Decoder.decodeString(cursor) })
        val mapping = EntityStoreRowMapping(Vector(
          _field("texts", "stored_texts", EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(text)), Multiplicity.ZeroMore),
          _field("flags", "stored_flags", EntityStoreFieldBinding.array(_native[Boolean](XBoolean, EntityStoreScalarRepresentation.Boolean)), Multiplicity.ZeroMore),
          _field("numbers", "stored_numbers", EntityStoreFieldBinding.array(_native[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number)), Multiplicity.ZeroMore)))
        val texts = Vector(Vector.empty[String], Vector("last", "null", "{broken", "", "雪🙂"), Vector.empty[String])
        val numbers = Vector(BigDecimal("1.2345678901234567890123456789"), BigDecimal(7))
        val logical = _record("texts" -> texts, "flags" -> Vector(true, false), "numbers" -> numbers)
        val persistent = _persistent(mapping)
        val wronglogical = _record("texts" -> Vector(Vector[Any](7, null)), "flags" -> Vector.empty[Boolean], "numbers" -> Vector.empty[BigDecimal])
        val wrongphysical = _record("stored_texts" -> "[[1,null]]", "stored_flags" -> "[]", "stored_numbers" -> "[]")
        val empty = _record("texts" -> Vector.empty[Vector[String]], "flags" -> Vector.empty[Boolean], "numbers" -> Vector.empty[BigDecimal])
        When("strict stages roundtrip recursive native arrays and interpret invalid elements without partial rows")
        val encoded = persistent.toStoreRecordC(_collection, logical)
        val restored = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
        val emptyencoded = persistent.toStoreRecordC(_collection, empty)
        val emptyrestored = emptyencoded.flatMap(persistent.fromStoreRecordC(_collection, _))
        val writefailure = persistent.toStoreRecordC(_collection, wronglogical)
        val readfailure = persistent.fromStoreRecordC(_collection, wrongphysical)
        Then("arrays retain order and empty children, store scalar JSON kinds and aggregate both indexed failures")
        restored shouldBe Consequence.success(logical)
        emptyrestored shouldBe Consequence.success(empty)
        encodes shouldBe 5
        decodes shouldBe 5
        parser.parse(_value(encoded.get.get, "stored_texts").asInstanceOf[String]).toOption.get shouldBe
          Json.fromValues(texts.map(values => Json.fromValues(values.map(Json.fromString))))
        _value(encoded.get.get, "stored_flags") shouldBe "[true,false]"
        parser.parse(_value(encoded.get.get, "stored_numbers").asInstanceOf[String]).toOption.get shouldBe
          Json.fromValues(numbers.map(Json.fromBigDecimal))
        emptyencoded.get.get.fields.map(_.value.single) shouldBe Vector("[]", "[]", "[]")
        Vector(writefailure, readfailure).foreach { result =>
          result.get shouldBe None
          _causes(result) should have size 2
          _facets(result) should contain(Descriptor.Facet.FieldPath("index[0]"))
          _facets(result) should contain(Descriptor.Facet.FieldPath("index[1]"))
        }
        _expect_reason(writefailure, "wrong-logical-value")
        _expect_reason(readfailure, "wrong-structured-shape")
      }

      "NATIVE-10 retain optional required Update and framework field presence rules" in {
        Given("required and optional native scalar fields with an explicit framework whitelist and raw Update instructions")
        var encodes = 0
        val number = EntityStoreFieldBinding.nativeScalar[Int](XInt, EntityStoreScalarRepresentation.Number, "update-int")(
          using summon[ClassTag[Int]], Encoder.instance { value => encodes += 1; Json.fromInt(value) }, Decoder.decodeInt)
        val text = _native[String](XString, EntityStoreScalarRepresentation.Text)
        val mapping = EntityStoreRowMapping(Vector(_field("required", "stored_required", number),
          _field("optional", "stored_optional", text, Multiplicity.ZeroOne)), Set("id", "revision", "permission", "contentBody"))
        val persistent = _persistent(mapping)
        val update = _update(mapping)
        val optionalvalues = Vector[Any](None, null, Some("null"), Some(""))
        val fullrows = optionalvalues.map(value => _record("required" -> 7, "optional" -> value, "revision" -> 3L))
        val raw = Vector(_record("required" -> Update.Noop, "optional" -> Update.SetValue(" {broken} "), "revision" -> Update.SetValue(4L)),
          _record("required" -> Update.SetValue(8), "optional" -> Update.SetNull),
          _record("required" -> Update.Noop, "optional" -> None))
        val invalidfull = Vector(_record("optional" -> "text"), _record("required" -> null), _record("required" -> None))
        val invalidpatch = Vector(_record("required" -> Update.SetNull), _record("optional" -> null),
          _record("required" -> Update.SetValue(Update.SetValue(9))), _record("unlisted" -> Update.Noop))
        When("strict full and raw Update stages preserve declared presence and reject invalid required or unknown fields")
        val encoded = fullrows.map(persistent.toStoreRecordC(_collection, _))
        val restored = encoded.map(_.flatMap(persistent.fromStoreRecordC(_collection, _)))
        val patches = raw.map(update.toStoreChangesRecordC(_collection, _))
        val fullfailures = invalidfull.map(persistent.toStoreRecordC(_collection, _))
        val patchfailures = invalidpatch.map(update.toStoreChangesRecordC(_collection, _))
        val unknownfull = persistent.toStoreRecordC(_collection, _record("required" -> 7, "unlisted" -> "text"))
        val unknownread = persistent.fromStoreRecordC(_collection, _record("stored_required" -> BigDecimal(7), "unlisted" -> "text"))
        val readabsence = persistent.fromStoreRecordC(_collection, _record("stored_optional" -> "text"))
        Then("None omits once, outer null survives, text null stays present and SetValue converts exactly once")
        encoded.head.get.get.fields.map(_.key) shouldBe Vector("stored_required", "revision")
        _value(restored(1).get.get, "optional") shouldBe (null: Any)
        _value(restored(2).get.get, "optional") shouldBe "null"
        _value(restored(3).get.get, "optional") shouldBe ""
        restored.foreach(result => _value(result.get.get, "revision") shouldBe 3L)
        patches(0) shouldBe Consequence.success(_record("stored_optional" -> " {broken} ", "revision" -> 4L))
        patches(1) shouldBe Consequence.success(_record("stored_required" -> BigDecimal(8), "stored_optional" -> Update.SetNull))
        patches(2) shouldBe Consequence.success(Record.empty)
        encodes shouldBe 5
        fullfailures.zip(Vector("missing-required-field", "null-not-allowed", "wrong-logical-value")).foreach {
          case (result, reason) => _expect_reason(result, reason)
        }
        patchfailures.zip(Vector("null-not-allowed", "explicit-clear-required", "invalid-row-shape", "missing-mapping")).foreach {
          case (result, reason) => _expect_reason(result, reason)
        }
        Vector(unknownfull, unknownread).foreach(_expect_reason(_, "missing-mapping"))
        _expect_reason(readabsence, "missing-required-field")
        mapping.physicalFields shouldBe Set("id", "revision", "permission", "contentBody")
      }
    }

    "exact native Text property" which {
      "NATIVE-12 preserve at least 200 adversarial Unicode whitespace escaping and JSON-looking values" in {
        Given("a public nativeScalar[String] Text binding, strict persistent and adversarial generated exact text space")
        val mapping = _single_mapping(_native[String](XString, EntityStoreScalarRepresentation.Text))
        val persistent = _persistent(mapping)
        val fragments = Vector("", " ", "\t", "\n", "\r\n", "雪", "🙂", "e\u0301", "\u0000", "\"", "\\",
          "null", "true", "123.45", "{}", "[]", "{\"a\":null}", "[1,\"雪\"]", "{broken", "\"quoted\"")
        val generator = Gen.oneOf(Gen.oneOf(fragments), Gen.listOf(Gen.oneOf(fragments)).map(_.mkString),
          Gen.listOf(Gen.choose(0, 65535).map(_.toChar)).map(_.mkString))
        When("the generated values cross public strict write and read stages")
        val property = Prop.forAll(generator) { text =>
          val logical = _record("value" -> text)
          val encoded = persistent.toStoreRecordC(_collection, logical)
          val decoded = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
          encoded.get.exists(record => _value(record, "stored") == text) && decoded.get.contains(logical)
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(200), property)
        Then("all successful cases preserve exact physical and reconstructed String content")
        result.passed shouldBe true
        result.succeeded should be >= 200
      }
    }
  }

  private val _collection: EntityCollectionId = EntityCollectionId("test", "native", "candidate")
  private lazy val _id: EntityId = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
    "entry", "candidate_1", _collection, entropy = "candidate_1")

  private def _native[A](datatype: DataType, representation: EntityStoreScalarRepresentation)(using
    classtag: ClassTag[A], encoder: Encoder[A], decoder: Decoder[A]
  ): EntityStoreFieldBinding = EntityStoreFieldBinding.nativeScalar[A](datatype, representation, "explicit-v1")

  private def _field(logicalname: String, storagename: String, binding: EntityStoreFieldBinding,
    multiplicity: Multiplicity = Multiplicity.One): EntityStoreField = {
    EntityStoreField(logicalname, storagename, ValueDomain(binding.datatype, multiplicity), binding)
  }

  private def _single_mapping(binding: EntityStoreFieldBinding): EntityStoreRowMapping =
    EntityStoreRowMapping(Vector(_field("value", "stored", binding)))

  private def _record(values: (String, Any)*): Record =
    Record(values.toVector.map { case (key, value) => Field(key, Field.Value.Single(value)) })

  private def _value(record: Record, key: String): Any = record.fields.find(_.key == key).get.value.single

  private def _persistent(mapping: EntityStoreRowMapping,
    writing: Record => Consequence[Record] = record => Consequence.success(record),
    admission: Record => Consequence[Record] = record => Consequence.success(record),
    decoding: Record => Consequence[Record] = record => Consequence.success(record)
  ): EntityPersistent[Record] = new EntityPersistent[Record] {
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
    def id(e: Record): EntityId = _id
    def toRecord(e: Record): Record = e
    def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
    override def toLogicalStoreRecord(e: Record): Consequence[Record] = writing(e)
    override def admitLogicalStoreRecord(r: Record): Consequence[Record] = admission(r)
    override def decodeAdmittedLogicalStoreRecord(r: Record): Consequence[Record] = decoding(r)
    override def toStoreRecord(e: Record): Record = throw new IllegalStateException("PRIVATE-LEGACY-WRITE")
    override def fromStoreRecord(r: Record): Consequence[Record] = throw new IllegalStateException("PRIVATE-LEGACY-READ")
  }

  private def _update(mapping: EntityStoreRowMapping,
    writing: Record => Consequence[Record] = record => Consequence.success(record)
  ): EntityPersistentUpdate[Record] = new EntityPersistentUpdate[Record] {
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
    def collection(e: Record): EntityCollectionId = _collection
    def toRecord(e: Record): Record = e
    def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
    override def toLogicalStoreRecord(e: Record): Consequence[Record] = writing(e)
    override def toStoreRecord(e: Record): Record = throw new IllegalStateException("PRIVATE-LEGACY-UPDATE")
  }

  private def _causes(result: Consequence[?]): List[Conclusion] = result match {
    case Consequence.Failure(conclusion) => conclusion.causes
    case _ => fail("Expected structured native scalar failure")
  }

  private def _facets(result: Consequence[?]): Vector[Descriptor.Facet] =
    _causes(result).toVector.flatMap(_.observation.cause.descriptor.facets)

  private def _messages(result: Consequence[?]): Vector[String] =
    _facets(result).collect { case Descriptor.Facet.Message(message) => message }

  private def _expect_reason(result: Consequence[?], reason: String): Unit = {
    result.get shouldBe None
    _facets(result) should contain(Descriptor.Facet.Reason(reason))
  }
}
