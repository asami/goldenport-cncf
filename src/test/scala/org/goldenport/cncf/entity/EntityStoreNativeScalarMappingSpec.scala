package org.goldenport.cncf.entity

import java.nio.file.Files
import java.sql.DriverManager
import scala.reflect.ClassTag
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, parser}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.datatype.Name
import org.goldenport.convert.NumericCodeCodec
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XBoolean, XDecimal, XInt, XInteger, XLong, XShort, XString}
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
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreNativeScalarMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityStoreNativeScalarMappingSpec.*
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
        Then("typed values, One multiplicity and exact storage names use declared Int, exact BigDecimal and Boolean scalars")
        restored shouldBe Consequence.success(logical)
        mapping.fields.map(_.domain.datatype) shouldBe Vector(XInt, XLong, XBoolean, XDecimal)
        mapping.fields.map(_.domain.multiplicity) shouldBe Vector.fill(4)(Multiplicity.One)
        physical.get.get.fields.map(_.key) shouldBe names.map("stored_" + _)
        _value(physical.get.get, "stored_count") shouldBe 7
        _value(physical.get.get, "stored_count").isInstanceOf[Int] shouldBe true
        _value(physical.get.get, "stored_sequence") shouldBe BigDecimal(Long.MaxValue)
        _value(physical.get.get, "stored_approved") shouldBe true
        _value(physical.get.get, "stored_amount") shouldBe _value(logical, "amount")
        Vector("stored_sequence", "stored_amount").foreach { key =>
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
        val unsupportedrecords = unsupported.map(value => _record("stored" -> value))
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
        val rejected = unsupportedrecords.map(integral.decode(_collection, _))
        val finite = Vector[Any](1.25f, 1.25d).map(value => decimal.decode(_collection, _record("stored" -> value)))
        val nonfiniteresults = nonfinite.map(value => decimal.decode(_collection, _record("stored" -> value)))
        val unrepresentable = huge.encode(_collection, _record("value" -> BigDecimal(1)))
        Then("canonical native-null omission fails as missing required while numeric classes stay exact and invalid values never fall back to text")
        unsupportedrecords.head.fields.exists(_.key == "stored") shouldBe false
        admitted.foreach(_ shouldBe Consequence.success(_record("value" -> 7)))
        finite.foreach(_ shouldBe Consequence.success(_record("value" -> BigDecimal("1.25"))))
        fractional.foreach(_expect_reason(_, "wrong-physical-value"))
        _expect_reason(rejected.head, "missing-required-field")
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
        val storednull = _record("stored" -> null)
        When("row read decodes each physical value under the selected Boolean declaration after canonical null omission")
        val falses = zeros.map(value => mapping.decode(_collection, _record("stored" -> value)))
        val trues = ones.map(value => mapping.decode(_collection, _record("stored" -> value)))
        val rejected = others.map(value => mapping.decode(_collection, _record("stored" -> value)))
        val nullresult = mapping.decode(_collection, storednull)
        Then("canonical native-null omission fails as missing required and only exact zero and one become Boolean values")
        storednull.fields.exists(_.key == "stored") shouldBe false
        falses.foreach(_ shouldBe Consequence.success(_record("value" -> false)))
        trues.foreach(_ shouldBe Consequence.success(_record("value" -> true)))
        rejected.foreach(_expect_reason(_, "wrong-physical-value"))
        _expect_reason(nullresult, "missing-required-field")
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
        val wronglogical = _record("texts" -> Vector(Vector[Any](7, true)), "flags" -> Vector.empty[Boolean], "numbers" -> Vector.empty[BigDecimal])
        val wrongphysical = _record("stored_texts" -> "[[1,null]]", "stored_flags" -> "[]", "stored_numbers" -> "[]")
        val empty = _record("texts" -> Vector.empty[Vector[String]], "flags" -> Vector.empty[Boolean], "numbers" -> Vector.empty[BigDecimal])
        When("Core rejects native null sequence positions before mapping and strict stages interpret recursive native arrays and other invalid elements")
        val invalidsequence = Record.createC(Vector("texts" -> Vector(Vector[Any]("valid", null))))
        val encoded = persistent.toStoreRecordC(_collection, logical)
        val restored = encoded.flatMap(persistent.fromStoreRecordC(_collection, _))
        val emptyencoded = persistent.toStoreRecordC(_collection, empty)
        val emptyrestored = emptyencoded.flatMap(persistent.fromStoreRecordC(_collection, _))
        val writefailure = persistent.toStoreRecordC(_collection, wronglogical)
        val readfailure = persistent.fromStoreRecordC(_collection, wrongphysical)
        Then("Core rejects native null positions while mapped arrays retain order, empty children, scalar JSON kinds and both indexed failures")
        invalidsequence shouldBe a[Consequence.Failure[?]]
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
        Given("required and optional native scalar fields with canonical native-null omission, an explicit framework whitelist and explicit raw Update clear instructions")
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
        val barenullpatch = _record("optional" -> null)
        val invalidpatch = Vector(_record("required" -> Update.SetNull), _record("optional" -> Update.SetValue(null)),
          _record("required" -> Update.SetValue(Update.SetValue(9))), _record("unlisted" -> Update.Noop))
        When("strict full and raw Update stages preserve omission, distinguish explicit clear and reject invalid required or unknown fields")
        val encoded = fullrows.map(persistent.toStoreRecordC(_collection, _))
        val restored = encoded.map(_.flatMap(persistent.fromStoreRecordC(_collection, _)))
        val patches = raw.map(update.toStoreChangesRecordC(_collection, _))
        val fullfailures = invalidfull.map(persistent.toStoreRecordC(_collection, _))
        val patchfailures = invalidpatch.map(update.toStoreChangesRecordC(_collection, _))
        val barenullchanges = update.toStoreChangesRecordC(_collection, barenullpatch)
        val unknownfull = persistent.toStoreRecordC(_collection, _record("required" -> 7, "unlisted" -> "text"))
        val unknownread = persistent.fromStoreRecordC(_collection, _record("stored_required" -> BigDecimal(7), "unlisted" -> "text"))
        val readabsence = persistent.fromStoreRecordC(_collection, _record("stored_optional" -> "text"))
        Then("None and native null omit, text null stays present, only SetNull clears and SetValue converts exactly once")
        encoded.head.get.get.fields.map(_.key) shouldBe Vector("stored_required", "revision")
        restored(1).get.get.fields.exists(_.key == "optional") shouldBe false
        _value(restored(2).get.get, "optional") shouldBe "null"
        _value(restored(3).get.get, "optional") shouldBe ""
        restored.foreach(result => _value(result.get.get, "revision") shouldBe 3L)
        patches(0) shouldBe Consequence.success(_record("stored_optional" -> " {broken} ", "revision" -> 4L))
        patches(1) shouldBe Consequence.success(_record("stored_required" -> 8, "stored_optional" -> Update.SetNull))
        patches(2) shouldBe Consequence.success(Record.empty)
        barenullpatch shouldBe Record.empty
        barenullchanges shouldBe Consequence.success(Record.empty)
        encodes shouldBe 5
        fullfailures.zip(Vector("missing-required-field", "missing-required-field", "wrong-logical-value")).foreach {
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

    "declared integer physical mapping" which {
      "NATIVE-13 share exact Int storage across ordinary and code-bearing values for at least 100 samples" in {
        Given("ordinary Int and generic NumericCodeCodec bindings both declared as XInt with Number")
        val generator = Gen.listOfN(8, Gen.choose(Int.MinValue, Int.MaxValue)).map { generated =>
          Vector(Int.MinValue, -1, 0, 1, Int.MaxValue) ++ generated
        }
        val property = Prop.forAll(generator) { values =>
          var encodes = Vector(0, 0)
          var decodes = Vector(0, 0)
          val codecodec = _code_codec.jsonCodec
          val ordinary = EntityStoreFieldBinding.nativeScalar[Int](XInt, EntityStoreScalarRepresentation.Number,
            "ordinary-int")(using summon[ClassTag[Int]],
            Encoder.instance { value => encodes = encodes.updated(0, encodes(0) + 1); Json.fromInt(value) },
            Decoder.instance { cursor => decodes = decodes.updated(0, decodes(0) + 1); Decoder.decodeInt(cursor) })
          val coded = EntityStoreFieldBinding.nativeScalar[CodedValue](XInt, EntityStoreScalarRepresentation.Number,
            "generic-code")(using summon[ClassTag[CodedValue]],
            Encoder.instance { value => encodes = encodes.updated(1, encodes(1) + 1); codecodec(value) },
            Decoder.instance { cursor => decodes = decodes.updated(1, decodes(1) + 1); codecodec(cursor) })
          val mapping = EntityStoreRowMapping(Vector(_field("ordinary", "stored_ordinary", ordinary),
            _field("coded", "stored_code", coded)))
          val roundtrips = values.forall { value =>
            val logical = _record("ordinary" -> value, "coded" -> CodedValue(value))
            val encoded = mapping.encode(_collection, logical)
            val decoded = encoded.flatMap(mapping.decode(_collection, _))
            encoded.get.exists { row =>
              Vector("stored_ordinary", "stored_code").forall { key =>
                val stored = _value(row, key)
                stored.isInstanceOf[Int] && stored == value
              }
            } && decoded.get.contains(logical)
          }
          roundtrips && encodes == Vector.fill(2)(values.size) && decodes == Vector.fill(2)(values.size)
        }
        When("ScalaCheck interprets the common declared integer write/read contract including both Int boundaries and zero")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("every physical value is Scala Int and each captured encoder and decoder is invoked exactly once")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }

      "NATIVE-14 reject fractional and overflow XInt numbers before decoder admission for at least 100 samples" in {
        Given("a declared XInt field with a decimal encoder and an intentionally permissive captured decoder")
        val generator = for {
          whole <- Gen.choose(-100000, 100000)
          excess <- Gen.choose(1, 100000)
        } yield Vector(BigDecimal(whole) + BigDecimal("0.5"),
          BigDecimal(BigInt(Int.MaxValue) + excess), BigDecimal(BigInt(Int.MinValue) - excess))
        val property = Prop.forAll(generator) { values =>
          var encodes = 0
          var decodes = 0
          val binding = EntityStoreFieldBinding.nativeScalar[BigDecimal](XInt, EntityStoreScalarRepresentation.Number,
            "permissive-decimal")(using summon[ClassTag[BigDecimal]],
            Encoder.instance { value => encodes += 1; Json.fromBigDecimal(value) },
            Decoder.instance { _ => decodes += 1; Right(BigDecimal(0)) })
          val mapping = _single_mapping(binding)
          val writes = values.map(value => mapping.encode(_collection, _record("value" -> value)))
          val reads = values.flatMap(value => Vector[Any](value, value.toString)).map { value =>
            mapping.decode(_collection, _record("stored" -> value))
          }
          writes.foreach(_expect_reason(_, "wrong-structured-shape"))
          reads.foreach(_expect_reason(_, "wrong-physical-value"))
          encodes == values.size && decodes == 0
        }
        val coded = _single_mapping(_coded_binding)
        val notation = Vector("0.000", "1e2", "-2147483648.0", "2147483647.000")
        When("generated invalid numbers are written or read as native numbers and numeric text, and valid integral notation is decoded")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        val decoded = notation.map(text => coded.decode(_collection, _record("stored" -> text)))
        Then("no invalid number is truncated or admitted by the decoder while exact integral notation retains its code")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
        result.discarded shouldBe 0
        decoded shouldBe Vector(0, 100, Int.MinValue, Int.MaxValue).map(value =>
          Consequence.success(_record("value" -> CodedValue(value))))
      }

      "NATIVE-15 persist ordinary Int and captured integer codes as SQLite integers without changing arbitrary precision" in {
        Given("public SQLite storage and a row mapping with ordinary XInt, generic code XInt, XInteger and XDecimal fields")
        val path = Files.createTempFile("cncf-declared-integer-mapping", ".db")
        val datastore = SqlDataStore.sqlite(path.toString)
        val collection = DataStore.CollectionId("declared_integer_mapping")
        given ExecutionContext = ExecutionContext.create()
        val integer = BigInt("123456789012345678901234567890123456789012345678901234567890")
        val decimal = BigDecimal("987654321098765432109876543210.123456789012345678901234567890123456789")
        val mapping = EntityStoreRowMapping(Vector(
          _field("ordinary", "stored_ordinary", _native[Int](XInt, EntityStoreScalarRepresentation.Number)),
          _field("coded", "stored_code", _coded_binding),
          _field("integer", "stored_integer", _native[BigInt](XInteger, EntityStoreScalarRepresentation.Number)),
          _field("decimal", "stored_decimal", _native[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number))), Set("id"))
        val values = Vector(Int.MinValue, -1, 0, 1, Int.MaxValue)
        try {
          When("mapped rows cross public datastore save/load and are inspected with a read-only JDBC query")
          val roundtrips = values.zipWithIndex.map { case (value, index) =>
            val entryid = DataStore.StringEntryId(s"integer-$index")
            val logical = _record("ordinary" -> value, "coded" -> CodedValue(value),
              "integer" -> integer, "decimal" -> decimal)
            val encoded = mapping.encode(_collection, logical)
            val written = encoded.flatMap(datastore.save(collection, entryid, _))
            val loaded = written.flatMap(_ => datastore.load(collection, entryid))
            val decoded = loaded.flatMap {
              case Some(row) => mapping.decode(_collection, row)
              case None => fail("Expected the saved declared integer row")
            }
            value -> decoded
          }
          val connection = DriverManager.getConnection(s"jdbc:sqlite:$path")
          val stored = try {
            val statement = connection.createStatement()
            try {
              val result = statement.executeQuery("SELECT typeof(stored_ordinary), typeof(stored_code), " +
                "stored_ordinary, stored_code, typeof(stored_integer), typeof(stored_decimal), " +
                "stored_integer, stored_decimal FROM declared_integer_mapping ORDER BY id")
              try {
                val rows = Vector.newBuilder[(String, String, Int, Int, String, String, String, String)]
                while (result.next()) {
                  rows += ((result.getString(1), result.getString(2), result.getInt(3), result.getInt(4),
                    result.getString(5), result.getString(6), result.getString(7), result.getString(8)))
                }
                rows.result()
              } finally result.close()
            } finally statement.close()
          } finally connection.close()
          Then("both XInt fields have integer storage and exact codes while arbitrary precision retains exact text and logical values")
          stored shouldBe values.map(value => ("integer", "integer", value, value,
            "text", "text", integer.toString, decimal.toString))
          roundtrips.foreach { case (value, decoded) =>
            val row = decoded.get.get
            _value(row, "ordinary") shouldBe value
            _value(row, "ordinary").isInstanceOf[Int] shouldBe true
            _value(row, "coded") shouldBe CodedValue(value)
            _value(row, "integer") shouldBe integer
            _value(row, "decimal") shouldBe decimal
          }
        } finally {
          try datastore.closeC().get.get
          finally Files.deleteIfExists(path)
        }
      }
    }


      "NATIVE-16 share exact signed16 storage across ordinary Short and code-bearing values for at least 100 samples" in {
        Given("ordinary Short and generic integer-code codecs both selected by the same XShort Number declaration")
        val generator = Gen.listOfN(8, Gen.choose(Short.MinValue.toInt, Short.MaxValue.toInt)).map { generated =>
          Vector(Short.MinValue.toInt, -1, 0, 1, Short.MaxValue.toInt) ++ generated
        }
        val property = Prop.forAll(generator) { values =>
          var encodes = Vector(0, 0)
          var decodes = Vector(0, 0)
          val shortencoder = summon[Encoder[Short]]
          val shortdecoder = summon[Decoder[Short]]
          val codecodec = _code_codec.jsonCodec
          val ordinary = EntityStoreFieldBinding.nativeScalar[Short](XShort, EntityStoreScalarRepresentation.Number,
            "ordinary-short")(using summon[ClassTag[Short]],
            Encoder.instance { value => encodes = encodes.updated(0, encodes(0) + 1); shortencoder(value) },
            Decoder.instance { cursor => decodes = decodes.updated(0, decodes(0) + 1); shortdecoder(cursor) })
          val coded = EntityStoreFieldBinding.nativeScalar[CodedValue](XShort, EntityStoreScalarRepresentation.Number,
            "short-code")(using summon[ClassTag[CodedValue]],
            Encoder.instance { value => encodes = encodes.updated(1, encodes(1) + 1); codecodec(value) },
            Decoder.instance { cursor => decodes = decodes.updated(1, decodes(1) + 1); codecodec(cursor) })
          val mapping = EntityStoreRowMapping(Vector(_field("ordinary", "stored_ordinary", ordinary),
            _field("coded", "stored_code", coded)))
          val roundtrips = values.forall { value =>
            val logical = _record("ordinary" -> value.toShort, "coded" -> CodedValue(value))
            val encoded = mapping.encode(_collection, logical)
            val decoded = encoded.flatMap(mapping.decode(_collection, _))
            encoded.get.exists { row =>
              Vector("stored_ordinary", "stored_code").forall { key =>
                val stored = _value(row, key)
                stored.isInstanceOf[Short] && stored == value.toShort
              }
            } && decoded.get.contains(logical)
          }
          roundtrips && encodes == Vector.fill(2)(values.size) && decodes == Vector.fill(2)(values.size)
        }
        When("ScalaCheck exercises signed16 boundaries, negative values, zero and generated assignments")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("both declarations emit Short without widening and each captured conversion runs exactly once")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }

      "NATIVE-17 reject fractional and overflow XShort values before captured decoders and reject unknown codes" in {
        Given("an XShort Number binding with a permissive decimal decoder and finite code correspondence")
        val generator = for {
          whole <- Gen.choose(Short.MinValue.toInt, Short.MaxValue.toInt)
          excess <- Gen.choose(1, 100000)
        } yield Vector(BigDecimal(whole) + BigDecimal("0.5"),
          BigDecimal(Short.MaxValue.toInt + excess), BigDecimal(Short.MinValue.toInt - excess))
        val property = Prop.forAll(generator) { values =>
          var encodes = 0
          var decodes = 0
          val binding = EntityStoreFieldBinding.nativeScalar[BigDecimal](XShort, EntityStoreScalarRepresentation.Number,
            "permissive-short")(using summon[ClassTag[BigDecimal]],
            Encoder.instance { value => encodes += 1; Json.fromBigDecimal(value) },
            Decoder.instance { _ => decodes += 1; Right(BigDecimal(0)) })
          val mapping = _single_mapping(binding)
          val writes = values.map(value => mapping.encode(_collection, _record("value" -> value)))
          val reads = values.flatMap(value => Vector[Any](value, value.toString)).map { value =>
            mapping.decode(_collection, _record("stored" -> value))
          }
          writes.foreach(_expect_reason(_, "wrong-structured-shape"))
          reads.foreach(_expect_reason(_, "wrong-physical-value"))
          encodes == values.size && decodes == 0
        }
        val finitecodec = NumericCodeCodec.instance[CodedValue](
          value => Option.when(Set(1, 2).contains(value.code))(value.code),
          code => Option.when(Set(1, 2).contains(code))(CodedValue(code))).jsonCodec
        val finite = _single_mapping(EntityStoreFieldBinding.nativeScalar[CodedValue](XShort,
          EntityStoreScalarRepresentation.Number, "finite-short-code")(using summon[ClassTag[CodedValue]],
          finitecodec, finitecodec))
        val coded = _single_mapping(_short_coded_binding)
        When("invalid numbers cross the common bridge, exact integral notation is read and an unknown in-range code reaches its codec")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        val notation = Vector("-32768.0", "0.000", "1e2", "32767.000").map(text =>
          coded.decode(_collection, _record("stored" -> text)))
        val unknown = finite.decode(_collection, _record("stored" -> 99))
        Then("range violations never call the decoder, exact notation survives and unknown correspondence fails structurally")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
        result.discarded shouldBe 0
        notation shouldBe Vector(Short.MinValue.toInt, 0, 100, Short.MaxValue.toInt).map(value =>
          Consequence.success(_record("value" -> CodedValue(value))))
        _expect_reason(unknown, "application-codec-failure")
      }

      "NATIVE-18 save and reopen ordinary Short and captured codes through SQLite INTEGER storage" in {
        Given("an isolated SQLite provider with ordinary Short and code values sharing the XShort Number route")
        val path = Files.createTempFile("cncf-declared-short-mapping", ".db")
        val datastore = SqlDataStore.sqlite(path.toString)
        val collection = DataStore.CollectionId("declared_short_mapping")
        given ExecutionContext = ExecutionContext.create()
        val mapping = EntityStoreRowMapping(Vector(
          _field("ordinary", "stored_ordinary", _native[Short](XShort, EntityStoreScalarRepresentation.Number)),
          _field("coded", "stored_code", _short_coded_binding)), Set("id"))
        val values = Vector(Short.MinValue, (-1).toShort, 0.toShort, 1.toShort, Short.MaxValue)
        try {
          When("public save writes every boundary case, the provider closes and a fresh provider reads the same rows")
          values.zipWithIndex.foreach { case (value, index) =>
            val logical = _record("ordinary" -> value, "coded" -> CodedValue(value.toInt))
            mapping.encode(_collection, logical).flatMap(datastore.save(collection,
              DataStore.StringEntryId(s"short-$index"), _)).get.get
          }
          datastore.closeC().get.get
          val reopened = SqlDataStore.sqlite(path.toString)
          val roundtrips = try {
            values.zipWithIndex.map { case (value, index) =>
              val decoded = reopened.load(collection, DataStore.StringEntryId(s"short-$index")).flatMap {
                case Some(row) => mapping.decode(_collection, row)
                case None => fail("Expected saved XShort row after reopening")
              }
              value -> decoded
            }
          } finally reopened.closeC().get.get
          val connection = DriverManager.getConnection(s"jdbc:sqlite:$path")
          val stored = try {
            val statement = connection.createStatement()
            try {
              val result = statement.executeQuery("SELECT typeof(stored_ordinary), typeof(stored_code), " +
                "stored_ordinary, stored_code FROM declared_short_mapping ORDER BY id")
              try {
                val rows = Vector.newBuilder[(String, String, Int, Int)]
                while (result.next()) rows += ((result.getString(1), result.getString(2), result.getInt(3), result.getInt(4)))
                rows.result()
              } finally result.close()
            } finally statement.close()
          } finally connection.close()
          Then("both columns contain native integers and reopening restores Short and code domain values exactly")
          stored shouldBe values.map(value => ("integer", "integer", value.toInt, value.toInt))
          roundtrips.foreach { case (value, decoded) =>
            val row = decoded.get.get
            _value(row, "ordinary") shouldBe value
            _value(row, "ordinary").isInstanceOf[Short] shouldBe true
            _value(row, "coded") shouldBe CodedValue(value.toInt)
          }
        } finally {
          try datastore.closeC().get.get
          finally Files.deleteIfExists(path)
        }
      }

    "declared integer children at every array depth" which {
      "NATIVE-19 preserve valid signed boundaries zero order and emptiness with one captured conversion per element" in {
        Given("the same generic integer-code codec selected by scalar, array and nested-array XShort or XInt declarations")
        val domains = Vector[(DataType, Int, Int)](
          (XShort, Short.MinValue.toInt, Short.MaxValue.toInt), (XInt, Int.MinValue, Int.MaxValue))
        domains.foreach { case (datatype, minimum, maximum) =>
          val generator = Gen.listOfN(6, Gen.choose(minimum, maximum)).map(generated =>
            Vector(minimum, 0, maximum) ++ generated)
          val property = Prop.forAll(generator) { values =>
            var encodes = 0
            var decodes = 0
            val codec = _code_codec.jsonCodec
            val child = EntityStoreFieldBinding.nativeScalar[CodedValue](datatype,
              EntityStoreScalarRepresentation.Number, "integer-child")(using summon[ClassTag[CodedValue]],
              Encoder.instance { value => encodes += 1; codec(value) },
              Decoder.instance { cursor => decodes += 1; codec(cursor) })
            val scalar = _single_mapping(child)
            val array = EntityStoreRowMapping(Vector(_field("value", "stored",
              EntityStoreFieldBinding.array(child), Multiplicity.ZeroMore)))
            val nested = EntityStoreRowMapping(Vector(_field("value", "stored",
              EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(child)), Multiplicity.ZeroMore)))
            val members = values.map(CodedValue.apply)
            val children = Vector(Vector.empty[CodedValue], members, Vector.empty[CodedValue])
            val logical = _record("value" -> members)
            val nestedlogical = _record("value" -> children)
            val empty = _record("value" -> Vector.empty[CodedValue])
            val nestedempty = _record("value" -> Vector(Vector.empty[CodedValue]))

            When("the public scalar mapping encodes and restores each valid declared value")
            values.foreach { value =>
              val record = _record("value" -> CodedValue(value))
              val scalarrestored = scalar.encode(_collection, record).flatMap(scalar.decode(_collection, _))
              Then("the scalar mapping restores its original typed value")
              scalarrestored shouldBe Consequence.success(record)
            }
            When("array mappings encode and restore ordered and empty numeric children")
            val encoded = array.encode(_collection, logical)
            val nestedencoded = nested.encode(_collection, nestedlogical)
            val restored = encoded.flatMap(array.decode(_collection, _))
            val nestedrestored = nestedencoded.flatMap(nested.decode(_collection, _))
            val emptyrestored = array.encode(_collection, empty).flatMap(array.decode(_collection, _))
            val nestedemptyrestored = nested.encode(_collection, nestedempty).flatMap(nested.decode(_collection, _))

            Then("all numeric JSON children preserve order and empty arrays without extra captured conversions")
            restored shouldBe Consequence.success(logical)
            nestedrestored shouldBe Consequence.success(nestedlogical)
            emptyrestored shouldBe Consequence.success(empty)
            nestedemptyrestored shouldBe Consequence.success(nestedempty)
            val numbers = Json.fromValues(values.map(Json.fromInt))
            parser.parse(_value(encoded.get.get, "stored").asInstanceOf[String]).toOption.get shouldBe numbers
            parser.parse(_value(nestedencoded.get.get, "stored").asInstanceOf[String]).toOption.get shouldBe
              Json.arr(Json.arr(), numbers, Json.arr())
            encodes shouldBe 3 * values.size
            decodes shouldBe 3 * values.size
            true
          }
          When("ScalaCheck selects at least one hundred boundary-containing ordered samples for the declared width")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the scalar and both array depths satisfy the same declaration without discarded cases")
          withClue(checked.toString) { checked.passed shouldBe true }
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }

      "NATIVE-20 reject fractional and overflowing children before captured decoding at every depth" in {
        Given("permissive decimal callbacks under XShort and XInt declarations, including the code32768 regression")
        val domains = Vector[(DataType, BigInt, BigInt)](
          (XShort, BigInt(Short.MinValue.toInt), BigInt(Short.MaxValue.toInt)),
          (XInt, BigInt(Int.MinValue), BigInt(Int.MaxValue)))
        domains.foreach { case (datatype, minimum, maximum) =>
          val generator = for {
            whole <- Gen.choose(-100000, 100000)
            excess <- Gen.choose(1, 100000)
          } yield Vector(BigDecimal(whole) + BigDecimal("0.5"),
            BigDecimal(maximum + excess), BigDecimal(minimum - excess))
          val property = Prop.forAll(generator) { values =>
            var encodes = 0
            var decodes = 0
            val child = EntityStoreFieldBinding.nativeScalar[BigDecimal](datatype,
              EntityStoreScalarRepresentation.Number, "permissive-integer-child")(using summon[ClassTag[BigDecimal]],
              Encoder.instance { value => encodes += 1; Json.fromBigDecimal(value) },
              Decoder.instance { _ => decodes += 1; Right(BigDecimal(0)) })
            val scalar = _single_mapping(child)
            val array = EntityStoreRowMapping(Vector(_field("value", "stored",
              EntityStoreFieldBinding.array(child), Multiplicity.ZeroMore)))
            val nested = EntityStoreRowMapping(Vector(_field("value", "stored",
              EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(child)), Multiplicity.ZeroMore)))
            val numbers = Json.fromValues(values.map(Json.fromBigDecimal))

            When("invalid numbers cross scalar, array and nested-array write and physical read boundaries")
            val scalarwrites = values.map(value => scalar.encode(_collection, _record("value" -> value)))
            val scalarreads = values.map(value => scalar.decode(_collection, _record("stored" -> value)))
            val arraywrite = array.encode(_collection, _record("value" -> values))
            val arrayread = array.decode(_collection, _record("stored" -> numbers.noSpaces))
            val nestedwrite = nested.encode(_collection, _record("value" -> Vector(Vector.empty[BigDecimal], values)))
            val nestedread = nested.decode(_collection, _record("stored" -> Json.arr(Json.arr(), numbers).noSpaces))

            Then("all violations fail with the declared stage reason and no invalid child reaches the captured decoder")
            (scalarwrites ++ Vector(arraywrite, nestedwrite)).foreach(_expect_reason(_, "wrong-structured-shape"))
            (scalarreads ++ Vector(arrayread, nestedread)).foreach(_expect_reason(_, "wrong-physical-value"))
            Vector(arraywrite, arrayread, nestedwrite, nestedread).foreach { failure =>
              _causes(failure) should have size values.size
              _causes(failure).zipWithIndex.foreach { case (cause, index) =>
                cause.observation.cause.descriptor.facets should contain(Descriptor.Facet.FieldPath(s"index[$index]"))
              }
            }
            Vector(nestedwrite, nestedread).foreach { failure =>
              _causes(failure).foreach(_.observation.cause.descriptor.facets should contain(
                Descriptor.Facet.FieldPath("index[1]")))
            }
            encodes shouldBe 3 * values.size
            decodes shouldBe 0
            true
          }
          When("ScalaCheck exercises at least one hundred fractional and overflow sets at each declared width")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("all invalid children are rejected before decoder admission without discarded cases")
          withClue(checked.toString) { checked.passed shouldBe true }
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
        val coded = EntityStoreRowMapping(Vector(_field("value", "stored",
          EntityStoreFieldBinding.array(_short_coded_binding), Multiplicity.ZeroMore)))
        When("the shared generic Int correspondence encounters code32768 inside an XShort array")
        val write = coded.encode(_collection, _record("value" -> Vector(CodedValue(32768))))
        val read = coded.decode(_collection, _record("stored" -> "[32768]"))
        Then("the child's XShort declaration rejects the code in both directions")
        _expect_reason(write, "wrong-structured-shape")
        _expect_reason(read, "wrong-physical-value")
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

  private val _code_codec: NumericCodeCodec[CodedValue] =
    NumericCodeCodec.instance[CodedValue](value => Some(value.code), code => Some(CodedValue(code)))

  private def _coded_binding: EntityStoreFieldBinding = {
    val codec = _code_codec.jsonCodec
    EntityStoreFieldBinding.nativeScalar[CodedValue](XInt, EntityStoreScalarRepresentation.Number,
      "generic-code")(using summon[ClassTag[CodedValue]], codec, codec)
  }

  private def _short_coded_binding: EntityStoreFieldBinding = {
    val codec = _code_codec.jsonCodec
    EntityStoreFieldBinding.nativeScalar[CodedValue](XShort, EntityStoreScalarRepresentation.Number,
      "generic-short-code")(using summon[ClassTag[CodedValue]], codec, codec)
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

object EntityStoreNativeScalarMappingSpec {
  final case class CodedValue(code: Int)
}
