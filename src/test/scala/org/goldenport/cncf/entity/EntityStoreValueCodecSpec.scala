package org.goldenport.cncf.entity

import java.math.MathContext
import scala.reflect.ClassTag
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.goldenport.Consequence
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification of docs/spec/datastore-logical-type-mapping.md.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreValueCodecSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import EntityStoreValueCodecSpec.*

  "EntityStore String values" should {
    "E1 retain exact text" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E1, rules:R2,R8, phase:100"
    ) {
      "preserve all characters independently on writes and physical reads" in {
        Given("docs/spec/datastore-logical-type-mapping.md R2,R8 E1: String declarations and opaque text")
        val codec: EntityStoreValueCodec[String] = EntityStoreValueCodec.string
        val values = Vector("{}", "[]", "\"text\"", "true", "12.3400", "null",
          "{malformed", "[1,]", "", " \t\n雪 é 🙂\r\n ")
        When("the declared String codec writes and reads each value")
        val encoded = values.map(codec.encode)
        val decoded = values.map(codec.decode)
        Then("JSON syntax, malformed syntax, emptiness and whitespace retain their exact text")
        encoded.map(_.get) shouldBe values.map(Some.apply)
        decoded.map(_.get) shouldBe values.map(Some.apply)
        codec.identity shouldBe "string"
      }
    }

    "E2 require actual Strings" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E2, rules:R2,R8,R10, phase:100"
    ) {
      "reject unsupported values without stringification" in {
        Given("docs/spec/datastore-logical-type-mapping.md R2,R8,R10 E2: non-String logical and physical values")
        val unknown = new Object {
          override def toString: String = throw new IllegalStateException("must not stringify")
        }
        val values = Vector[Any](null, Record.empty, Json.obj(), 1, unknown)
        When("String conversion receives each unsupported value")
        val results = values.map(EntityStoreValueCodec.string.encode) ++
          values.map(EntityStoreValueCodec.string.decode)
        Then("every value is invalid rather than guessed or rendered")
        results.foreach(_expect_invalid)
      }
    }

    "E3 satisfy the exact text roundtrip law" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E3, rules:R2,R6, phase:100"
    ) {
      "preserve generated Unicode text both directly and inside declared arrays" in {
        Given("docs/spec/datastore-logical-type-mapping.md R2,R6 E3: generated arbitrary-length escaped and Unicode text")
        val texts = Gen.listOf(Gen.oneOf("雪", "🙂", "é", "a", "\n", "\t", "\u0000", "\"", "\\"))
          .map(_.mkString)
        val codec = EntityStoreValueCodec.array(EntityStoreValueCodec.string)
        val property = Prop.forAll(texts) { text =>
          val direct = EntityStoreValueCodec.string.encode(text).flatMap(EntityStoreValueCodec.string.decode)
          val nested = codec.encode(Vector(text, "", text)).flatMap(codec.decode)
          direct.get.contains(text) && nested.get.contains(Vector(text, "", text))
        }
        When("the direct and array roundtrip laws interpret each generated text")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("both declarations preserve every generated character")
        checked.passed shouldBe true
      }
    }
  }

  "EntityStore JSON values" should {
    "E4 retain every JSON shape" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E4, rules:R3,R7,R8, phase:100"
    ) {
      "serialize compact text and reconstruct Json including present null" in {
        Given("docs/spec/datastore-logical-type-mapping.md R3,R7,R8 E4: all Json shapes and exact large numbers")
        val decimal = new BigDecimal(new java.math.BigDecimal(
          "123456789012345678901234567890.12345678901234567890123456789"), MathContext.UNLIMITED)
        val integer = BigInt("12345678901234567890123456789012345678901234567890")
        val values = Vector(Json.obj("value" -> Json.fromString("雪")), Json.arr(Json.True),
          Json.fromString(" text "), Json.False, Json.fromBigDecimal(decimal),
          Json.fromBigInt(integer), Json.Null)
        val codec: EntityStoreValueCodec[Json] = EntityStoreValueCodec.json
        When("the declared JSON codec encodes and reads each physical JSON text")
        val encoded = values.map(codec.encode)
        val decoded = values.map(value => codec.decode(" \n" + value.noSpaces + "\t "))
        val presentnull = codec.decode("null")
        Then("compact text and JSON meaning remain exact without Record or Double conversion")
        encoded.map(_.get) shouldBe values.map(value => Some(value.noSpaces))
        decoded.map(_.get) shouldBe values.map(Some.apply)
        presentnull.get shouldBe Some(Json.Null)
        codec.identity shouldBe "json"
      }
    }

    "E5 distinguish runtime, physical and format failures" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E5, rules:R3,R8,R10, phase:100"
    ) {
      "reject implicit encoding conversions and classify malformed storage text" in {
        Given("docs/spec/datastore-logical-type-mapping.md R3,R8,R10 E5: wrong values and malformed physical JSON")
        val values = Vector[Any]("{}", Record.empty, 1, new Object(), null)
        val malformed = Vector("", "{private-payload", "[1,]", "unquoted")
        When("the JSON codec interprets logical inputs and physical inputs")
        val encoded = values.map(EntityStoreValueCodec.json.encode)
        val physical = Vector[Any](Json.obj(), Record.empty, 1, null).map(EntityStoreValueCodec.json.decode)
        val parsed = malformed.map(EntityStoreValueCodec.json.decode)
        Then("wrong values are invalid and malformed stored text is a format error without a payload copy")
        (encoded ++ physical).foreach(_expect_invalid)
        parsed.foreach(_expect_format_error)
        _messages(parsed(1)).mkString should not include "private-payload"
      }
    }
  }

  "EntityStore semi-structured values" should {
    "E6 retain present empty objects" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E6, rules:R4,R7, phase:100"
    ) {
      "persist an empty Record as an object and reconstruct its presence" in {
        Given("docs/spec/datastore-logical-type-mapping.md R4,R7 E6: an empty canonical Record and empty object")
        val codec: EntityStoreValueCodec[Record] = EntityStoreValueCodec.semiStructuredData
        When("the semi-structured declaration encodes and decodes the empty object")
        val encoded = codec.encode(Record.empty)
        val decoded = codec.decode("{}")
        val normalized = codec.encode(Json.obj())
        Then("the value remains present rather than omitted")
        encoded.get shouldBe Some("{}")
        decoded.get shouldBe Some(Record.empty)
        normalized.get shouldBe Some("{}")
        codec.identity shouldBe "semiStructuredData"
      }
    }

    "E7 retain nested structure, numbers and member order" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E7, rules:R4,R7,R8, phase:100"
    ) {
      "omit null attributes recursively while preserving canonical object structure and rejecting null sequence positions" in {
        Given("docs/spec/datastore-logical-type-mapping.md R4,R7,R8 E7 and Phase 115: null attributes, nonnull ordered sequences, empties and exact numbers")
        val decimal = new BigDecimal(new java.math.BigDecimal(
          "123456789012345678901234567890.12345678901234567890123456789"), MathContext.UNLIMITED)
        val integer = BigInt("900719925474099312345678901234567890")
        val record = Record.createFull(Vector(
          "nullMember" -> null,
          "emptyObject" -> Record.empty,
          "emptyArray" -> Vector.empty,
          "nested" -> Record.createFull(Vector("nullMember" -> null, "items" -> Vector("雪", Record.empty))),
          "integer" -> integer, "decimal" -> decimal))
        val expected = Json.obj("emptyObject" -> Json.obj(),
          "emptyArray" -> Json.arr(), "nested" -> Json.obj(
            "items" -> Json.arr(Json.fromString("雪"), Json.obj())),
          "integer" -> Json.fromBigInt(integer), "decimal" -> Json.fromBigDecimal(decimal))
        val jsoninput = Json.obj("nullMember" -> Json.Null, "emptyObject" -> Json.obj(),
          "emptyArray" -> Json.arr(), "nested" -> Json.obj("nullMember" -> Json.Null,
            "items" -> Json.arr(Json.fromString("雪"), Json.obj())),
          "integer" -> Json.fromBigInt(integer), "decimal" -> Json.fromBigDecimal(decimal))
        val nullsequence = Json.obj("items" -> Json.arr(Json.fromString("雪"), Json.Null, Json.obj()))
        val codec = EntityStoreValueCodec.semiStructuredData
        When("the semi-structured codec normalizes Record and JSON-object inputs and rejects a null sequence position")
        val encoded = codec.encode(record)
        val decoded = codec.decode(jsoninput.noSpaces)
        val roundtrip = decoded.flatMap(codec.encode).flatMap(EntityStoreValueCodec.json.decode)
        val normalized = codec.encode(jsoninput)
        val invalidencoded = codec.encode(nullsequence)
        val invaliddecoded = codec.decode(nullsequence.noSpaces)
        Then("null attributes are omitted recursively, exact numbers, empties and member order remain, and null sequence positions fail")
        encoded.get shouldBe Some(expected.noSpaces)
        normalized.get shouldBe encoded.get
        decoded.get shouldBe Some(record)
        roundtrip.get shouldBe Some(expected)
        Vector(invalidencoded, invaliddecoded).foreach(_expect_invalid)
        decoded.map(_.fields.map(_.key)).get shouldBe Some(expected.asObject.get.keys.toVector)
        roundtrip.map(_.asObject.get.keys.toVector).get shouldBe Some(expected.asObject.get.keys.toVector)
      }
    }

    "E8 reject unsupported structure" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E8, rules:R4,R8,R10, phase:100"
    ) {
      "fail for nonobject roots and unknown members without omission" in {
        Given("docs/spec/datastore-logical-type-mapping.md R4,R8,R10 E8: nonobject roots and a full Record with an unknown member")
        val roots = Vector("[]", "\"text\"", "true", "123", "null")
        val unknown = new Object {
          override def toString: String = throw new IllegalStateException("must not stringify")
        }
        val record = Record.createFull(Vector("valid" -> "retained", "unknown" -> unknown))
        val codec = EntityStoreValueCodec.semiStructuredData
        When("the declared object codec reads those roots or writes the unsupported Record")
        val decoded = roots.map(codec.decode)
        val encoded = codec.encode(record)
        val wrongvalues = Vector[Any](null, "{}", 1, unknown).map(codec.encode) ++
          Vector[Any](null, Record.empty, Json.obj(), 1).map(codec.decode)
        val malformed = codec.decode("{")
        Then("every unsupported value fails and malformed storage has its own format classification")
        (decoded ++ wrongvalues :+ encoded).foreach(_expect_invalid)
        encoded.get shouldBe None
        _expect_format_error(malformed)
      }
    }
  }

  "EntityStore declared arrays" should {
    "E9 retain ordering and emptiness" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E9, rules:R2,R6, phase:100"
    ) {
      "support ordered Scala sequences and explicitly nested declarations" in {
        Given("docs/spec/datastore-logical-type-mapping.md R2,R6 E9: String elements and nested array declarations")
        val codec: EntityStoreValueCodec[Vector[String]] = EntityStoreValueCodec.array(EntityStoreValueCodec.string)
        val nested: EntityStoreValueCodec[Vector[Vector[String]]] = EntityStoreValueCodec.array(codec)
        val values = List("last", " {} ", "雪", "")
        val mutablevalues = scala.collection.mutable.ArrayBuffer.from(values)
        val nestedvalues = Vector(Vector.empty[String], values.toVector, Vector.empty[String])
        When("each declared array is encoded and reconstructed")
        val encoded = codec.encode(values)
        val decoded = encoded.flatMap(codec.decode)
        val nestedresult = nested.encode(nestedvalues).flatMap(nested.decode)
        val mutableencoded = codec.encode(mutablevalues)
        val emptyencoded = codec.encode(Vector.empty[String])
        val emptydecoded = codec.decode("[]")
        val nestedemptyencoded = nested.encode(Vector.empty[Vector[String]])
        Then("the result is an ordered Vector with every empty array retained")
        encoded.get shouldBe Some(Json.fromValues(values.map(Json.fromString)).noSpaces)
        mutableencoded.get shouldBe encoded.get
        decoded.get shouldBe Some(values.toVector)
        emptyencoded.get shouldBe Some("[]")
        emptydecoded.get shouldBe Some(Vector.empty[String])
        nestedresult.get shouldBe Some(nestedvalues)
        nestedemptyencoded.get shouldBe Some("[]")
        codec.identity shouldBe "array(string)"
        nested.identity shouldBe "array(array(string))"
      }
    }

    "E10 derive null semantics from the child declaration" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E10, rules:R3,R4,R6,R7, phase:100"
    ) {
      "retain Json.Null and reject null for other declared value spaces" in {
        Given("docs/spec/datastore-logical-type-mapping.md R3,R4,R6,R7 E10: JSON, String, object and nested-array children")
        val jsoncodec = EntityStoreValueCodec.array(EntityStoreValueCodec.json)
        val stringcodec = EntityStoreValueCodec.array(EntityStoreValueCodec.string)
        val recordcodec = EntityStoreValueCodec.array(EntityStoreValueCodec.semiStructuredData)
        val nestedcodec = EntityStoreValueCodec.array(stringcodec)
        When("the array declarations interpret present JSON null and native null elements")
        val jsonresult = jsoncodec.encode(Vector(Json.Null, Json.obj())).flatMap(jsoncodec.decode)
        val invalid = Vector(stringcodec.encode(Vector("valid", null)), stringcodec.decode("[null]"),
          recordcodec.encode(Vector(null)), recordcodec.decode("[null]"),
          nestedcodec.encode(Vector(null)), nestedcodec.decode("[null]"),
          jsoncodec.encode(Vector(null)))
        val presentnull = jsoncodec.decode("[null]")
        Then("only the JSON declaration preserves JSON null as a value")
        jsonresult.get shouldBe Some(Vector(Json.Null, Json.obj()))
        presentnull.get shouldBe Some(Vector(Json.Null))
        invalid.foreach(_expect_invalid)
      }
    }

    "E11 aggregate independent element failures" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E11, rules:R6,R10, phase:100"
    ) {
      "identify both invalid positions without returning a partial Vector" in {
        Given("docs/spec/datastore-logical-type-mapping.md R6,R10 E11: two invalid elements separated by a valid String")
        val codec = EntityStoreValueCodec.array(EntityStoreValueCodec.string)
        When("the complete array is validated on both write and read")
        val encoded = codec.encode(Vector[Any](1, "valid", null))
        val decoded = codec.decode("[1,\"valid\",null]")
        Then("the failure retains both indices and no partial success")
        Vector(encoded, decoded).foreach { result =>
          _expect_invalid(result)
          _messages(result) should have size 2
          _messages(result)(0) should include ("index[0]")
          _messages(result)(1) should include ("index[2]")
          result.get shouldBe None
        }
      }
    }

    "E12 require array roots and an explicit element binding" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E12, rules:R1,R6,R10, phase:100"
    ) {
      "reject wrong roots, physical values and missing children" in {
        Given("docs/spec/datastore-logical-type-mapping.md R1,R6,R10 E12: wrong roots and a missing element codec")
        val codec = EntityStoreValueCodec.array(EntityStoreValueCodec.string)
        val missing = EntityStoreValueCodec.array[String](null)
        When("the declarations interpret unsupported inputs")
        val decoded = Vector[Any]("{}", "\"text\"", "true", "1", "null", Json.arr(), Record.empty, null)
          .map(codec.decode)
        val encoded = Vector[Any]("[]", Json.arr(), Record.empty, Array("text"), 1, null).map(codec.encode)
        val binding = Vector(missing.encode(Vector.empty[String]), missing.decode("["))
        val malformed = codec.decode("[")
        Then("wrong values and bindings fail explicitly before malformed-text parsing for the missing binding")
        (decoded ++ encoded ++ binding).foreach(_expect_invalid)
        _messages(binding(1)).mkString should include ("binding")
        _expect_format_error(malformed)
      }
    }
  }

  "EntityStore explicit application codecs" should {
    "E13 retain the selected application class" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E13, rules:R1,R6,R12, phase:100"
    ) {
      "roundtrip a class and its declared array using typed consumer values" in {
        Given("docs/spec/datastore-logical-type-mapping.md R1,R6,R12 E13: a case class with explicitly captured Circe codecs")
        val codec: EntityStoreValueCodec[Proposal] = _proposal_codec
        val proposals: EntityStoreValueCodec[Vector[Proposal]] = EntityStoreValueCodec.array(codec)
        val value = Proposal("雪", 7)
        val values = Vector(value, Proposal("second", 2))
        When("the declared class and array are encoded and decoded")
        val encoded = codec.encode(value)
        val decoded = encoded.flatMap(codec.decode)
        val arrayresult = proposals.encode(values).flatMap(proposals.decode)
        Then("reads retain the actual application class and declared array order")
        encoded.get shouldBe Some("{\"label\":\"雪\",\"score\":7}")
        decoded.get shouldBe Some(value)
        arrayresult.get shouldBe Some(values)
        codec.identity shouldBe "application(proposal-v1)"
        proposals.identity shouldBe "array(application(proposal-v1))"
      }
    }

    "E14 distinguish class failure from malformed JSON" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E14, rules:R1,R6,R8,R10, phase:100"
    ) {
      "reject wrong runtime classes and valid JSON that the selected decoder cannot reconstruct" in {
        Given("docs/spec/datastore-logical-type-mapping.md R1,R6,R8,R10 E14: an object with an unbound toRecord method and incomplete JSON")
        val unknown = new Object {
          def toRecord: Record = throw new IllegalStateException("must not discover")
          override def toString: String = throw new IllegalStateException("must not stringify")
        }
        val codec = _proposal_codec
        When("the application declaration encodes wrong classes and reads incomplete or malformed text")
        val wrong = Vector[Any](unknown, "proposal", Record.empty, Json.obj(), 7, null).map(codec.encode)
        val rejected = codec.decode("{\"label\":\"private-label\"}")
        val malformed = codec.decode("{private-payload")
        val physical = Vector[Any](null, Json.obj(), Record.empty, 7).map(codec.decode)
        Then("class and decoder failures are invalid while malformed storage is a format error")
        (wrong ++ physical :+ rejected).foreach(_expect_invalid)
        _messages(rejected).mkString should include ("application codec rejected JSON")
        _messages(rejected).mkString should not include "private-label"
        _expect_format_error(malformed)
        _messages(malformed).mkString should not include "private-payload"
      }
    }

    "E15 reject missing bindings before invoking application code" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E15, rules:R1,R6,R10, phase:100"
    ) {
      "return invalid outcomes from codecs constructed with every missing binding" in {
        Given("docs/spec/datastore-logical-type-mapping.md R1,R6,R10 E15: null or blank identities and missing codec instances")
        val calls = new java.util.concurrent.atomic.AtomicInteger(0)
        val encoder: Encoder[Proposal] = Encoder.instance { _ =>
          calls.incrementAndGet()
          Json.obj()
        }
        val decoder: Decoder[Proposal] = Decoder.instance { _ =>
          calls.incrementAndGet()
          Right(Proposal("decoded", 1))
        }
        val classtag = summon[ClassTag[Proposal]]
        val codecs = Vector(
          EntityStoreValueCodec.application[Proposal](null)(using classtag, encoder, decoder),
          EntityStoreValueCodec.application[Proposal](" \t\n")(using classtag, encoder, decoder),
          EntityStoreValueCodec.application[Proposal]("proposal-v1")(using null, encoder, decoder),
          EntityStoreValueCodec.application[Proposal]("proposal-v1")(using classtag, null, decoder),
          EntityStoreValueCodec.application[Proposal]("proposal-v1")(using classtag, encoder, null))
        When("each explicitly constructed invalid binding receives logical and malformed physical input")
        val results = codecs.flatMap(codec => Vector(codec.encode(Proposal("input", 1)), codec.decode("{")))
        Then("binding failures precede parsing and all captured application code remains uninvoked")
        results.foreach(_expect_invalid)
        results.foreach(result => _messages(result).mkString should include ("binding"))
        calls.get() shouldBe 0
      }
    }

    "E16 contain thrown codec exceptions" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E16, rules:R6,R10, phase:100"
    ) {
      "convert NonFatal encoder and decoder exceptions into explicit application failures" in {
        Given("docs/spec/datastore-logical-type-mapping.md R6,R10 E16: explicitly bound application codecs that throw")
        val encoder: Encoder[Proposal] = Encoder.instance { _ => throw new IllegalArgumentException("private encoder payload") }
        val decoder: Decoder[Proposal] = Decoder.instance { _ => throw new IllegalArgumentException("private decoder payload") }
        val encodecodec = EntityStoreValueCodec.application[Proposal]("throwing-encoder")(
          using summon[ClassTag[Proposal]], encoder, _proposal_decoder)
        val decodecodec = EntityStoreValueCodec.application[Proposal]("throwing-decoder")(
          using summon[ClassTag[Proposal]], _proposal_encoder, decoder)
        When("the selected class codecs encounter the thrown exceptions")
        val encoded = encodecodec.encode(Proposal("input", 1))
        val decoded = decodecodec.decode("{}")
        val arrayresult = EntityStoreValueCodec.array(encodecodec).encode(Vector(Proposal("first", 1), Proposal("second", 2)))
        Then("the failures are invalid outcomes with stage and identity, without copying exception messages")
        Vector(encoded, decoded, arrayresult).foreach(_expect_invalid)
        _messages(encoded).mkString should include ("application(throwing-encoder) encode")
        _messages(decoded).mkString should include ("application(throwing-decoder) decode")
        _messages(encoded).mkString should not include "private encoder payload"
        _messages(decoded).mkString should not include "private decoder payload"
        _messages(arrayresult) should have size 2
      }
    }

    "E17 reject native null and invalid decoder successes" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E17, rules:R6,R7,R10, phase:100"
    ) {
      "require a nonnull compatible class even when the decoder reports success" in {
        Given("docs/spec/datastore-logical-type-mapping.md R6,R7,R10 E17: native-null encodings, invalid decoder successes and a Proposal decoder that rejects JSON null")
        val nullencoder: Encoder[Proposal] = Encoder.instance(_ => null)
        val nulldecoder: Decoder[Proposal] = Decoder.instance(_ => Right(null))
        val wrongdecoder: Decoder[Proposal] = Decoder.instance[Any](_ => Right("wrong class"))
          .asInstanceOf[Decoder[Proposal]]
        val classtag = summon[ClassTag[Proposal]]
        val encodecodec = EntityStoreValueCodec.application[Proposal]("null-encoding")(
          using classtag, nullencoder, _proposal_decoder)
        val decoders = Vector(nulldecoder, wrongdecoder).map(decoder =>
          EntityStoreValueCodec.application[Proposal]("invalid-decoding")(using classtag, _proposal_encoder, decoder))
        When("the declared codecs produce native null or incompatible classes, or Proposal reads JSON null")
        val results = Vector(encodecodec.encode(Proposal("input", 1))) ++ decoders.map(_.decode("{}")) ++
          Vector(_proposal_codec.decode("null"), EntityStoreValueCodec.array(_proposal_codec).decode("[null]"))
        Then("native null and incompatible classes remain invalid, and Proposal rejects its undeclared JSON-null shape")
        results.foreach(_expect_invalid)
      }
    }

    "E18 preserve an explicitly declared JSON-null representation" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E18, rules:R6,R7,R10, phase:100"
    ) {
      "roundtrip a nonnull application class at the root and within its declared array" in {
        Given("docs/spec/datastore-logical-type-mapping.md R6,R7,R10 E18: a nonnull EmptyPayload whose explicit encoder and decoder select JSON null")
        val codec: EntityStoreValueCodec[EmptyPayload] = _empty_payload_codec
        val arraycodec: EntityStoreValueCodec[Vector[EmptyPayload]] = EntityStoreValueCodec.array(codec)
        val value = EmptyPayload()
        val values = Vector(value, EmptyPayload())
        When("the selected class and array codecs encode and reconstruct their declared representation")
        val encoded = codec.encode(value)
        val decoded = encoded.flatMap(codec.decode)
        val presentnull = codec.decode("null")
        val arrayencoded = arraycodec.encode(values)
        val arraydecoded = arrayencoded.flatMap(arraycodec.decode)
        Then("present JSON null remains physical text and restores actual nonnull class values in order")
        encoded.get shouldBe Some("null")
        decoded.get shouldBe Some(value)
        presentnull.get shouldBe Some(value)
        arrayencoded.get shouldBe Some("[null,null]")
        arraydecoded.get shouldBe Some(values)
      }

      "reject native null and JSON shapes outside the selected class representation" in {
        Given("docs/spec/datastore-logical-type-mapping.md R6,R7,R10 E18: the explicit EmptyPayload codec, native null and JSON shapes other than null")
        val codec = _empty_payload_codec
        val arraycodec = EntityStoreValueCodec.array(codec)
        val shapes = Vector("{}", "[]", "\"text\"", "true", "1")
        When("the class and array declarations receive native-null values or decoder-rejected JSON shapes")
        val native = Vector(codec.encode(null), codec.decode(null),
          arraycodec.encode(null), arraycodec.decode(null), arraycodec.encode(Vector[EmptyPayload](null)))
        val rejected = shapes.map(codec.decode) ++ shapes.map(shape => arraycodec.decode(s"[$shape]"))
        Then("native null stays invalid and the selected decoder continues to govern admissible JSON shapes")
        (native ++ rejected).foreach(_expect_invalid)
      }
    }
  }

  "EntityStore recursive array bindings" should {
    "E19 validate every selected descendant before interpreting data" should afterWord(
      "in spec:datastore-logical-type-mapping, example:E19, rules:R1,R6,R10, phase:100"
    ) {
      "reject invalid application descendants for empty and populated arrays before parsing or invoking application code" in {
        Given("docs/spec/datastore-logical-type-mapping.md R1,R6,R10 E19: Proposal children with null or blank identity and separately missing ClassTag, Encoder or Decoder")
        val encodecalls = new java.util.concurrent.atomic.AtomicInteger(0)
        val decodecalls = new java.util.concurrent.atomic.AtomicInteger(0)
        val encoder: Encoder[Proposal] = Encoder.instance { value =>
          encodecalls.incrementAndGet()
          _proposal_encoder(value)
        }
        val decoder: Decoder[Proposal] = Decoder.instance { cursor =>
          decodecalls.incrementAndGet()
          _proposal_decoder(cursor)
        }
        val classtag = summon[ClassTag[Proposal]]
        val blankidentity = " \t\n"
        val missinginstances = "application(proposal-v1) binding: missing ClassTag, Encoder or Decoder"
        val bindings = Vector(
          EntityStoreValueCodec.application[Proposal](null)(using classtag, encoder, decoder) ->
            "application(<missing>) binding: missing codec identity",
          EntityStoreValueCodec.application[Proposal](blankidentity)(using classtag, encoder, decoder) ->
            s"application($blankidentity) binding: missing codec identity",
          EntityStoreValueCodec.application[Proposal]("proposal-v1")(using null, encoder, decoder) -> missinginstances,
          EntityStoreValueCodec.application[Proposal]("proposal-v1")(using classtag, null, decoder) -> missinginstances,
          EntityStoreValueCodec.application[Proposal]("proposal-v1")(using classtag, encoder, null) -> missinginstances)
        val arrays = bindings.map { case (codec, message) => (EntityStoreValueCodec.array(codec), message) }
        val nested = arrays.map { case (codec, message) => (EntityStoreValueCodec.array(codec), message) }
        val deep = nested.map { case (codec, message) => (EntityStoreValueCodec.array(codec), message) }
        val values = Vector(Proposal("first", 1), Proposal("second", 2))
        When("each invalid application binding receives empty and populated logical arrays and valid or malformed physical arrays through every wrapper")
        val directresults = arrays.flatMap { case (codec, message) =>
          Vector(codec.encode(Vector.empty[Proposal]), codec.encode(values),
            codec.decode("[]"), codec.decode("[{\"label\":\"first\",\"score\":1}]"), codec.decode("["))
            .map(result => (message, result))
        }
        val nestedresults = nested.flatMap { case (codec, message) =>
          Vector(codec.encode(Vector.empty[Vector[Proposal]]), codec.encode(Vector(Vector.empty[Proposal])),
            codec.encode(Vector(values)), codec.decode("[]"), codec.decode("[[]]"), codec.decode("["))
            .map(result => (message, result))
        }
        val deepresults = deep.flatMap { case (codec, message) =>
          Vector(codec.encode(Vector.empty[Vector[Vector[Proposal]]]),
            codec.encode(Vector(Vector.empty[Vector[Proposal]])), codec.encode(Vector(Vector(Vector.empty[Proposal]))),
            codec.encode(Vector(Vector(values))), codec.decode("[]"), codec.decode("[[]]"),
            codec.decode("[[[]]]"), codec.decode("["))
            .map(result => (message, result))
        }
        Then("every operation returns only the selected descendant's invalid binding diagnostic and captured application code remains uninvoked")
        (directresults ++ nestedresults ++ deepresults).foreach { case (message, result) =>
          _expect_invalid(result)
          result.get shouldBe None
          _messages(result) shouldBe Vector(message)
        }
        encodecalls.get() shouldBe 0
        decodecalls.get() shouldBe 0
      }

      "reject a missing String descendant through nested arrays even when no element would be traversed" in {
        Given("docs/spec/datastore-logical-type-mapping.md R1,R6,R10 E19: a missing String element binding wrapped by two further array declarations")
        val missing = EntityStoreValueCodec.array[String](null)
        val nested = EntityStoreValueCodec.array(missing)
        val deep = EntityStoreValueCodec.array(nested)
        val physical = Vector("[]", "[[]]", "[[[]]]", "[")
        When("each declaration writes empty or populated values and reads empty, nested-empty or malformed storage text")
        val missingresults = Vector(missing.encode(Vector.empty[String]), missing.encode(Vector("retained"))) ++
          physical.map(missing.decode)
        val nestedresults = Vector(nested.encode(Vector.empty[Vector[String]]),
          nested.encode(Vector(Vector.empty[String])), nested.encode(Vector(Vector("retained")))) ++
          physical.map(nested.decode)
        val deepresults = Vector(deep.encode(Vector.empty[Vector[Vector[String]]]),
          deep.encode(Vector(Vector.empty[Vector[String]])), deep.encode(Vector(Vector(Vector.empty[String]))),
          deep.encode(Vector(Vector(Vector("retained"))))) ++ physical.map(deep.decode)
        Then("all results are invalid binding failures from the missing descendant, with no partial success or format error")
        (missingresults ++ nestedresults ++ deepresults).foreach { result =>
          _expect_invalid(result)
          result.get shouldBe None
          _messages(result) shouldBe Vector("array(<missing>) binding: missing element codec")
        }
      }

      "retain empty roots, nested empty children and ordered values for valid application and String declarations" in {
        Given("docs/spec/datastore-logical-type-mapping.md R1,R6,R10 E19: an explicitly bound Proposal codec and nested String array declarations")
        val proposals = EntityStoreValueCodec.array(_proposal_codec)
        val nestedproposals = EntityStoreValueCodec.array(proposals)
        val strings = EntityStoreValueCodec.array(EntityStoreValueCodec.string)
        val nestedstrings = EntityStoreValueCodec.array(strings)
        val deepstrings = EntityStoreValueCodec.array(nestedstrings)
        val proposalvalues = Vector(Proposal("second", 2), Proposal("first", 1))
        val nestedproposalvalues = Vector(Vector.empty[Proposal], proposalvalues, Vector.empty[Proposal])
        val stringvalues = Vector(Vector.empty[String], Vector("last", "雪", ""), Vector.empty[String])
        val deepvalues = Vector(Vector.empty[Vector[String]], stringvalues, Vector(Vector.empty[String]))
        When("the valid declarations encode and reconstruct empty roots, nested emptiness and populated arrays")
        val emptyproposals = proposals.encode(Vector.empty[Proposal])
        val emptyproposalresult = emptyproposals.flatMap(proposals.decode)
        val proposalencoded = proposals.encode(proposalvalues)
        val proposalresult = proposalencoded.flatMap(proposals.decode)
        val emptynestedproposals = nestedproposals.encode(Vector.empty[Vector[Proposal]])
        val emptynestedproposalresult = emptynestedproposals.flatMap(nestedproposals.decode)
        val nestedproposalresult = nestedproposals.encode(nestedproposalvalues).flatMap(nestedproposals.decode)
        val emptynestedstrings = nestedstrings.encode(Vector.empty[Vector[String]])
        val emptynestedstringresult = emptynestedstrings.flatMap(nestedstrings.decode)
        val nestedemptystrings = nestedstrings.decode("[[]]")
        val stringresult = nestedstrings.encode(stringvalues).flatMap(nestedstrings.decode)
        val emptydeepstrings = deepstrings.encode(Vector.empty[Vector[Vector[String]]])
        val emptydeepstringresult = emptydeepstrings.flatMap(deepstrings.decode)
        val deepemptystrings = deepstrings.decode("[[[]]]")
        val deepresult = deepstrings.encode(deepvalues).flatMap(deepstrings.decode)
        Then("empty arrays remain present and every declared application value or String returns in its original order")
        emptyproposals.get shouldBe Some("[]")
        emptyproposalresult.get shouldBe Some(Vector.empty[Proposal])
        proposalencoded.get shouldBe Some("[{\"label\":\"second\",\"score\":2},{\"label\":\"first\",\"score\":1}]")
        proposalresult.get shouldBe Some(proposalvalues)
        emptynestedproposals.get shouldBe Some("[]")
        emptynestedproposalresult.get shouldBe Some(Vector.empty[Vector[Proposal]])
        nestedproposalresult.get shouldBe Some(nestedproposalvalues)
        emptynestedstrings.get shouldBe Some("[]")
        emptynestedstringresult.get shouldBe Some(Vector.empty[Vector[String]])
        nestedemptystrings.get shouldBe Some(Vector(Vector.empty[String]))
        stringresult.get shouldBe Some(stringvalues)
        emptydeepstrings.get shouldBe Some("[]")
        emptydeepstringresult.get shouldBe Some(Vector.empty[Vector[Vector[String]]])
        deepemptystrings.get shouldBe Some(Vector(Vector(Vector.empty[String])))
        deepresult.get shouldBe Some(deepvalues)
      }
    }
  }

  private val _empty_payload_encoder: Encoder[EmptyPayload] = {
    Encoder.instance(_ => Json.Null)
  }

  private val _empty_payload_decoder: Decoder[EmptyPayload] = {
    Decoder.instance { cursor =>
      if (cursor.value.isNull) {
        Right(EmptyPayload())
      } else {
        Left(DecodingFailure("Expected JSON null for EmptyPayload", cursor.history))
      }
    }
  }

  private def _empty_payload_codec: EntityStoreValueCodec[EmptyPayload] = {
    EntityStoreValueCodec.application[EmptyPayload]("empty-payload-v1")(
      using summon[ClassTag[EmptyPayload]], _empty_payload_encoder, _empty_payload_decoder)
  }

  private val _proposal_encoder: Encoder[Proposal] = {
    Encoder.forProduct2("label", "score")(value => (value.label, value.score))
  }

  private val _proposal_decoder: Decoder[Proposal] = {
    Decoder.forProduct2("label", "score")(Proposal.apply)
  }

  private def _proposal_codec: EntityStoreValueCodec[Proposal] = {
    EntityStoreValueCodec.application[Proposal]("proposal-v1")(
      using summon[ClassTag[Proposal]], _proposal_encoder, _proposal_decoder)
  }

  private def _expect_invalid(result: Consequence[?]): Unit = {
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.causes.foreach(_.observation.taxonomy shouldBe
          Taxonomy(Taxonomy.Category.Value, Taxonomy.Symptom.Invalid))
      case _ => fail("Expected an invalid value outcome")
    }
  }

  private def _expect_format_error(result: Consequence[?]): Unit = {
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.observation.taxonomy shouldBe
          Taxonomy(Taxonomy.Category.Value, Taxonomy.Symptom.FormatError)
      case _ => fail("Expected a malformed JSON format outcome")
    }
  }

  private def _messages(result: Consequence[?]): Vector[String] = {
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.causes.toVector.flatMap(_.observation.cause.descriptor.facets.collect {
          case Descriptor.Facet.Message(message) => message
        })
      case _ => Vector.empty
    }
  }
}

object EntityStoreValueCodecSpec {
  private final case class Proposal(label: String, score: Int)

  private final case class EmptyPayload()
}
