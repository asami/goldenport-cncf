package org.goldenport.cncf.http

import cats.data.{NonEmptyList, NonEmptyVector}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField, CmlOperationUpdateField}
import org.goldenport.convert.ValueReader
import org.goldenport.datatype.I18nLabel
import org.goldenport.observation.Descriptor
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XInt, XJson, XSemiStructuredData, XString}
import org.goldenport.value.BaseContent
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * JSON syntax, semantic Record ownership and explicit update instructions.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationJsonBodyDecoderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Operation JSON body admission" should {
    "delegate semantic structures to Core" which {
      "E1 omit nested null properties while preserving ordinary root presence, order and empty structures" in {
        Given("docs/spec/http-json-body-admission.md R1, R2, E1: an ordinary object interleaving semantic children and actual raw Json parameters")
        val text = """{"first":"one","gone":null,"payload":{"gone":null},"evidence":{"z":1,"gone":null,"empty":{},"array":[],"child":{"gone":null,"text":"null"}},"payloads":[null],"last":"two"}"""

        When("the selected Operation's body is decoded outside Entity update semantics")
        val record = _decode(text, operation = None)
        val evidence = record.getAny("evidence").get.asInstanceOf[Record]
        val child = evidence.getAny("child").get.asInstanceOf[Record]

        Then("root null remains present while Core nested omission retains empty structures and literal text")
        record.fields.map(_.key) shouldBe Vector("first", "gone", "payload", "evidence", "payloads", "last")
        record.getAny("gone") shouldBe Some(Json.Null)
        evidence.fields.map(_.key) shouldBe Vector("z", "empty", "array", "child")
        evidence.getAny("gone") shouldBe None
        evidence.getAny("empty") shouldBe Some(Record.empty)
        evidence.getAny("array") shouldBe Some(Vector.empty)
        child.fields.map(_.key) shouldBe Vector("text")
        child.getString("text") shouldBe Some("null")
        record.getAny("payload") shouldBe Some(_json("""{"gone":null}"""))
      }

      Vector(
        "root sequence" -> """{"evidence":[1,null,2]}""",
        "nested sequence" -> """{"evidence":{"values":[{},null,{}]}}""",
        "unknown semantic field" -> """{"other":[null]}"""
      ).foreach { case (label, text) =>
        s"reject a null position in a $label instead of falling back to an opaque body" in {
          Given("a valid JSON object with a null element owned by semantic Record conversion")
          val parameters = _parameters

          When("the object crosses the Core Record conversion boundary")
          val result = OperationJsonBodyDecoder.decode(text, parameters, None)

          Then("the conversion returns a structured failure and never a successful None fallback")
          result shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "retain ordinary root presence defined in docs/spec/http-json-body-admission.md" which {
      "E2 distinguish an absent ordinary parameter from an explicit root Json.Null" in {
        Given("docs/spec/http-json-body-admission.md R1, E2: an optional ordinary String parameter outside Entity update semantics")
        val parameters = Vector(_parameter("nickname", XString, Multiplicity.ZeroOne))

        When("one object omits the parameter and another explicitly supplies JSON null")
        val absent = _decode("{}", parameters, None)
        val present = _decode("""{"nickname":null}""", parameters, None)

        Then("only the explicit null produces a singular present field")
        absent.getAny("nickname") shouldBe None
        present.fields.map(_.key) shouldBe Vector("nickname")
        present.getAny("nickname") shouldBe Some(Json.Null)
      }

      "E3 distinguish absent unknown keys from explicit null keys for operation validation" in {
        Given("docs/spec/http-json-body-admission.md R1, R4, E3: a selected parameter list that does not declare other")
        val parameters = Vector(_parameter("nickname", XString, Multiplicity.ZeroOne))

        When("objects with and without the unknown null key cross the body admission boundary")
        val absent = _decode("""{"nickname":"one"}""", parameters, None)
        val present = _decode("""{"other":null,"nickname":"one"}""", parameters, None)

        Then("the operation receives the unknown key and its original position instead of losing rejection evidence")
        absent.getAny("other") shouldBe None
        present.fields.map(_.key) shouldBe Vector("other", "nickname")
        present.getAny("other") shouldBe Some(Json.Null)
        present.getString("nickname") shouldBe Some("one")
      }

      "E4 keep an invalid ordinary integer null visible to the actual typed reader" in {
        Given("docs/spec/http-json-body-admission.md R1, R4, E4: an optional integer parameter and the existing Int ValueReader")
        val parameters = Vector(_parameter("count", XInt, Multiplicity.ZeroOne))
        val reader = summon[ValueReader[Int]]

        When("omitted, null and valid numeric inputs are admitted and present values reach typed construction")
        val absent = _decode("{}", parameters, None)
        val present = _decode("""{"count":null}""", parameters, None)
        val ordinary = _decode("""{"count":7}""", parameters, None)
        val rejected = present.getAny("count").map(reader.readC)
        val accepted = ordinary.getAny("count").map(reader.readC)

        Then("null reaches the reader as an invalid present value while omission alone skips value reading")
        absent.getAny("count").map(reader.readC) shouldBe None
        present.getAny("count") shouldBe Some(Json.Null)
        rejected.get shouldBe a[Consequence.Failure[?]]
        accepted shouldBe Some(Consequence.success(7))
      }

      "E5 preserve ordinary presence and raw children for one hundred shrinking samples with zero discards" in {
        Given("docs/spec/http-json-body-admission.md R1, R2, R4, E5: independent nonempty names, disjoint key roles, null presence, ordinary values and root order")
        val name = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
        val samples = for {
          ordinaryname <- name
          unknownname <- name
          rawname <- name
          valuename <- name
          ordinarypresent <- Gen.oneOf(true, false)
          unknownpresent <- Gen.oneOf(true, false)
          text <- Gen.alphaNumStr
          number <- Gen.choose(-1000000, 1000000)
          reverse <- Gen.oneOf(true, false)
          offset <- Gen.choose(0, 3)
        } yield (ordinaryname, unknownname, rawname, valuename, ordinarypresent, unknownpresent, text, number, reverse, offset)
        val property = Prop.forAll(samples) { case (ordinaryname, unknownname, rawname, valuename, ordinarypresent, unknownpresent, text, number, reverse, offset) =>
          Given("independently generated ordinary, unknown, raw and value keys with optional null occurrences")
          val ordinarykey = "ordinary_" + ordinaryname
          val unknownkey = "unknown_" + unknownname
          val rawkey = "raw_" + rawname
          val valuekey = "value_" + valuename
          val raw = Json.obj("gone" -> Json.Null, "children" -> Json.arr(Json.Null, Json.fromInt(number)),
            "text" -> Json.fromString(text))
          val ordinary = Json.obj("text" -> Json.fromString(text), "enabled" -> Json.fromBoolean(ordinarypresent))
          val entries = Vector(ordinarykey -> Json.Null, unknownkey -> Json.Null, rawkey -> raw, valuekey -> ordinary)
          val direction = if (reverse) entries.reverse else entries
          val ordered = direction.drop(offset) ++ direction.take(offset)
          val selected = ordered.filter { case (key, _) =>
            (key != ordinarykey || ordinarypresent) && (key != unknownkey || unknownpresent)
          }
          val withoutnulls = ordered.filterNot { case (key, _) => key == ordinarykey || key == unknownkey }
          val parameters = Vector(_parameter(ordinarykey, XString, Multiplicity.ZeroOne),
            _parameter(rawkey, XJson), _parameter(valuekey, XSemiStructuredData))

          When("the selected body and matching objects with both null keys absent or present are admitted")
          val record = _decode(Json.fromFields(selected).noSpaces, parameters, None)
          val absent = _decode(Json.fromFields(withoutnulls).noSpaces, parameters, None)
          val present = _decode(Json.fromFields(ordered).noSpaces, parameters, None)

          Then("presence, original root order, singular null values, raw children and ordinary values remain observable without Scala null")
          record.fields.map(_.key) shouldBe selected.map(_._1)
          absent.fields.map(_.key) shouldBe withoutnulls.map(_._1)
          present.fields.map(_.key) shouldBe ordered.map(_._1)
          record.getAny(ordinarykey) shouldBe (if (ordinarypresent) Some(Json.Null) else None)
          record.getAny(unknownkey) shouldBe (if (unknownpresent) Some(Json.Null) else None)
          Vector(ordinarykey, unknownkey).foreach { key =>
            absent.getAny(key) shouldBe None
            present.getAny(key) shouldBe Some(Json.Null)
          }
          Vector(record, absent, present).foreach { decoded =>
            decoded.getAny(rawkey) shouldBe Some(raw)
            decoded.getAny(valuekey) shouldBe Some(Record.dataAuto("text" -> text, "enabled" -> ordinarypresent))
            _contains_scala_null(decoded) shouldBe false
          }
          true
        }

        When("ScalaCheck evaluates the connected shrinking presence property")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred successful samples and zero discarded cases are required")
        withClue(checked.toString) { checked.passed shouldBe true }
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }

    "retain repeated root occurrences for the selected Operation" which {
      Vector("resourceId" -> "resource\\u0049d", "registrationId" -> "registration\\u0049d").foreach { case (key, escaped) =>
        Vector(key, escaped).foreach { spelling =>
          s"retain both distinct $key values when the second spelling is $spelling" in {
            Given("R1 and R4: a repeated ordinary selector surrounding an independent root property")
            val parameters = Vector(_parameter(key, XString))
            val text = s"""{"$key":"invalid-selector","marker":"between","$spelling":"actual-selector"}"""

            When("the wire JSON object is admitted before the Operation's singular-field validation")
            val record = _decode(text, parameters, None)

            Then("both decoded names and their original values remain separate ordered Record fields")
            record.fields.map(_.key) shouldBe Vector(key, "marker", key)
            record.fields.map(_.value.single) shouldBe Vector("invalid-selector", "between", "actual-selector")
            record.fields.count(_.key == key) shouldBe 2
          }
        }
      }

      "retain each repeated raw document and collection with standard nested Circe semantics" in {
        Given("repeated declared raw roots with nested nulls, duplicate object keys, exact decimals and repeated sequence positions")
        val first = """{"same":1,"same":2,"gone":null,"values":[null,1.2300e+40,null]}"""
        val second = """[null,{"gone":null},1.2300e+40,null]"""
        val collection = s"""[$first,null,$first]"""
        val text = s"""{"payload":$first,"payloads":$collection,"payload":$second,"payloads":[null,null]}"""

        When("each original root occurrence crosses the existing raw-parameter decoder")
        val record = _decode(text)

        Then("each whole raw node and Vector retains its own value without combining repeated roots")
        record.fields.map(_.key) shouldBe Vector("payload", "payloads", "payload", "payloads")
        record.fields.map(_.value.single) shouldBe Vector(_json(first), _json(collection).asArray.get,
          _json(second), Vector(Json.Null, Json.Null))
        val document = record.fields.head.value.single.asInstanceOf[Json]
        document.hcursor.get[Int]("same").toOption shouldBe Some(2)
        document.hcursor.downField("values").focus.get.noSpaces shouldBe "[null,1.2300e+40,null]"
        _contains_scala_null(record) shouldBe false
      }

      "preserve repeated ordinary null presence and existing update refusal" in {
        Given("repeated ordinary null assignments and repeated scalar raw null documents")
        val ordinary = """{"nickname":null,"nickname":"later"}"""
        val raw = """{"payload":null,"payload":{"gone":null}}"""

        When("ordinary decoding, Entity update policy and raw admission receive the same occurrences")
        val present = _decode(ordinary, operation = None)
        val refused = OperationJsonBodyDecoder.decode(ordinary, _parameters, Some(_operation))
        val documents = _decode(raw)

        Then("ordinary null remains present, Entity update still refuses it and raw null remains document data")
        present.fields.map(_.value.single) shouldBe Vector(Json.Null, "later")
        refused shouldBe a[Consequence.Failure[?]]
        documents.fields.map(_.value.single) shouldBe Vector(Json.Null, _json("""{"gone":null}"""))
      }

      "preserve unique-root nested raw and semantic object behavior" in {
        Given("unique raw and ordinary roots whose nested objects contain repeated keys")
        val raw = """{"same":1,"same":2,"gone":null,"values":[null,1.2300e+40,null]}"""
        val text = s"""{"payload":$raw,"evidence":{"same":"first","same":"last","gone":null,"values":[1,1]}}"""

        When("the unique-root path delegates nested values to the existing Circe and Core decoders")
        val record = _decode(text)

        Then("raw nodes keep Circe semantics and ordinary nested null omission and sequence order stay unchanged")
        record.getAny("payload") shouldBe Some(_json(raw))
        record.getAny("payload").get.asInstanceOf[Json].hcursor.downField("values").focus.get.noSpaces shouldBe "[null,1.2300e+40,null]"
        val semantic = record.getAny("evidence").get.asInstanceOf[Record]
        semantic.getString("same") shouldBe Some("last")
        semantic.getAny("gone") shouldBe None
        semantic.getAny("values") shouldBe Some(Vector(1, 1))
      }

      "retain independent repeated names and values across one hundred escaped-key samples without discards" in {
        Given("independent nonempty ordinary root names and distinct first and last String values")
        val samples = for {
          name <- Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
          first <- Gen.alphaNumStr
          last <- Gen.alphaNumStr
          escaped <- Gen.oneOf(true, false)
        } yield (name, first, last, escaped)
        val property = Prop.forAll(samples) { case (name, first, last, escaped) =>
          Given("one ordinary root name with independently generated occurrence values and optional Unicode key escaping")
          val key = "field_" + name
          val spelling = if (escaped) "\\u0066ield_" + name else key
          val firstvalue = "first-" + first
          val lastvalue = "last-" + last
          val text = s"""{"$key":${Json.fromString(firstvalue).noSpaces},"middle":true,"$spelling":${Json.fromString(lastvalue).noSpaces}}"""
          val parameters = Vector(_parameter(key, XString))

          When("the original wire occurrences reach the selected Operation's Record boundary")
          val record = _decode(text, parameters, None)

          Then("count, decoded names, order and distinct values survive without Scala null or a global rejection")
          record.fields.map(_.key) shouldBe Vector(key, "middle", key)
          record.fields.map(_.value.single) shouldBe Vector(firstvalue, true, lastvalue)
          _contains_scala_null(record) shouldBe false
          true
        }

        When("ScalaCheck evaluates the connected repeated-root property")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred successful samples and zero discards are required")
        withClue(checked.toString) { checked.passed shouldBe true }
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }

    "retain exact raw Json values" which {
      Vector("null", "[null,{},null]", "9007199254740993.1234567890123456789", "1.2300e+40").foreach { literal =>
        s"preserve scalar raw Json syntax $literal" in {
          Given("an actual scalar XJson parameter and its original JSON number, array or null node")
          val text = s"""{"payload":$literal}"""
          val expected = _json(literal)

          When("the body adapter selects the scalar raw field")
          val record = _decode(text)

          Then("the original document value and number lexeme remain present")
          record.getAny("payload") shouldBe Some(expected)
          record.getAny("payload").get.asInstanceOf[Json].noSpaces shouldBe literal
        }
      }

      "retain raw collection order, duplicates, number lexemes and null positions" in {
        Given("a declared multiple XJson parameter with repeated document children")
        val literal = """[null,1.2300e+40,{"gone":null},null,1.2300e+40]"""
        val expected = _json(literal).asArray.get

        When("a syntax array is admitted as a raw collection")
        val record = _decode(s"""{"payloads":$literal}""")
        val values: Vector[Json] = _take(summon[ValueReader[Vector[Json]]].readC(record.getAny("payloads").get))

        Then("every position is a raw child and exact numeric syntax is retained")
        values shouldBe expected
        values.map(_.noSpaces) shouldBe Vector("null", "1.2300e+40", "{\"gone\":null}", "null", "1.2300e+40")
      }

      "distinguish an absent optional document from present Json.Null" in {
        Given("an optional actual XJson parameter")
        val parameters = Vector(_parameter("optionalPayload", XJson, Multiplicity.ZeroOne))

        When("one body omits the document and another assigns JSON null")
        val absent = _decode("{}", parameters)
        val present = _decode("""{"optionalPayload":null}""", parameters)

        Then("only the second body contains a real document assignment")
        absent.getAny("optionalPayload") shouldBe None
        present.getAny("optionalPayload") shouldBe Some(Json.Null)
      }

      "match declared aliases through the existing normalized naming contract" in {
        Given("a canonical raw parameter with a declared alias and generated source metadata")
        val content = BaseContent.simple("rawPayload")
        val aliased = _parameter("rawPayload", XJson).copy(content = content.copy(
          nameAttributes = content.nameAttributes.copy(alias = Some(NonEmptyVector.one(I18nLabel("external-document"))))))
        val metadata = _operation.copy(parameters = Vector(
          CmlOperationField("raw_payload", "untrusted-leaf", "?", update = Some(CmlOperationUpdateField("*", false)))))

        When("an aliased value carrier supplies ordered raw children")
        val record = _decode("""{"external_document__append":[null,{"gone":null},null]}""", Vector(aliased), Some(metadata))

        Then("the original alias/carrier key and its raw Vector are preserved")
        record.fields.map(_.key) shouldBe Vector("external_document__append")
        record.getAny("external_document__append") shouldBe Some(Vector(Json.Null, _json("""{"gone":null}"""), Json.Null))
      }

      Vector(DataType.Named("application.Json"), DataType.Named("json")).foreach { datatype =>
        s"keep custom datatype ${datatype.name} under semantic Record ownership" in {
          Given("a real custom protocol datatype and a CML leaf named Json, neither of which is canonical XJson")
          val parameters = Vector(_parameter("payload", datatype))
          val metadata = _operation.copy(parameters = Vector(CmlOperationField("payload", "Json")))

          When("a nested null object and a null sequence are separately admitted")
          val record = _decode("""{"payload":{"gone":null,"empty":{}}}""", parameters, Some(metadata))
          val rejected = OperationJsonBodyDecoder.decode("""{"payload":[null]}""", parameters, Some(metadata))

          Then("the object is a normalized Record and null sequence positions still fail")
          record.getAny("payload") shouldBe Some(Record.dataAuto("empty" -> Record.empty))
          rejected shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "select shape from actual multiplicity" which {
      Vector(Multiplicity.One, Multiplicity.ZeroOne, Multiplicity.Range(0, 1)).foreach { multiplicity =>
        s"keep ${multiplicity.label} raw documents scalar" in {
          Given("an actual scalar protocol multiplicity with no generated update metadata")
          val parameters = Vector(_parameter("payload", XJson, multiplicity))

          When("JSON null is assigned")
          val record = _decode("""{"payload":null}""", parameters, None)

          Then("Json.Null is the present scalar document")
          record.getAny("payload") shouldBe Some(Json.Null)
        }
      }
      Vector(Multiplicity.ZeroMore, Multiplicity.OneMore, Multiplicity.Range(0, 3),
        Multiplicity.Ranges(List(NonEmptyList.one(Multiplicity.Range(2, 4))))).foreach { multiplicity =>
        s"require a syntax array for ${multiplicity.label} raw collections" in {
          Given("a collection protocol multiplicity with no generated metadata")
          val parameters = Vector(_parameter("payload", XJson, multiplicity))

          When("a null root and an ordered array are independently decoded")
          val rejected = OperationJsonBodyDecoder.decode("""{"payload":null}""", parameters, None)
          val record = _decode("""{"payload":[null,1,null]}""", parameters, None)

          Then("null is never scalar-promoted and an array retains its exact children")
          rejected shouldBe a[Consequence.Failure[?]]
          record.getAny("payload") shouldBe Some(Vector(Json.Null, Json.fromInt(1), Json.Null))
        }
      }
      Vector("*", "+", "0..3").foreach { source =>
        s"prefer source multiplicity $source over an optional generated request field" in {
          Given("a generated optional request parameter with explicit collection source metadata")
          val parameters = Vector(_parameter("payload", XJson, Multiplicity.ZeroOne))
          val metadata = _operation.copy(parameters = Vector(CmlOperationField("payload", "Json", "?",
            update = Some(CmlOperationUpdateField(source, false)))))

          When("the raw body contains an array or a null collection root")
          val record = _decode("""{"payload":[null,null]}""", parameters, Some(metadata))
          val rejected = OperationJsonBodyDecoder.decode("""{"payload":null}""", parameters, Some(metadata))

          Then("source metadata selects the Vector shape without weakening array admission")
          record.getAny("payload") shouldBe Some(Vector(Json.Null, Json.Null))
          rejected shouldBe a[Consequence.Failure[?]]
        }
      }
      "prefer scalar source metadata and leave required cardinality to typed construction" in {
        Given("scalar source metadata over a multiple protocol field and a separately required collection")
        val parameters = Vector(_parameter("payload", XJson, Multiplicity.ZeroMore))
        val metadata = _operation.copy(parameters = Vector(CmlOperationField("payload", "Json", "?",
          update = Some(CmlOperationUpdateField("?", true)))))
        val required = Vector(_parameter("payloads", XJson, Multiplicity.OneMore))

        When("a scalar null and an empty required array are decoded before typed construction")
        val scalar = _decode("""{"payload":null}""", parameters, Some(metadata))
        val empty = _decode("""{"payloads":[]}""", required, None)
        val cardinality = summon[ValueReader[NonEmptyVector[Json]]].readC(empty.getAny("payloads").get)

        Then("the scalar is retained and the existing typed reader rejects the empty required collection")
        scalar.getAny("payload") shouldBe Some(Json.Null)
        empty.getAny("payloads") shouldBe Some(Vector.empty)
        cardinality shouldBe a[Consequence.Failure[?]]
      }
    }

    "preserve update carrier meaning" which {
      (OperationUpdateDirective.ExplicitValueAssignment.kinds.map(_.suffix) ++
        OperationUpdateDirective.ValueOperation.kinds.map(_.suffix)).foreach { suffix =>
        s"retain raw values and the original $suffix carrier key" in {
          Given("a scalar canonical XJson parameter with a supported value carrier")
          val key = "payload" + suffix
          val document = _json("""{"gone":null,"values":[null]}""")

          When("the raw document is decoded through that carrier")
          val record = _decode(Json.obj(key -> document).noSpaces)

          Then("no carrier is rewritten and no raw child is normalized away")
          record.fields.map(_.key) shouldBe Vector(key)
          record.getAny(key) shouldBe Some(document)
          record.getAny("payload") shouldBe None
        }
      }
      "leave the command carrier as ordinary text" in {
        Given("an actual raw Json parameter and the explicit SQL-null command spelling")
        val text = """{"payload__update_command":"null"}"""

        When("the body adapter decodes without extracting a command as Json")
        val record = _decode(text)
        val directives = _take(OperationUpdateDirectiveNormalizer.normalize(record))

        Then("the existing normalizer alone interprets the command")
        record.getAny("payload__update_command") shouldBe Some("null")
        directives.directive("payload") shouldBe OperationUpdateDirective.OperandlessCommand(
          "payload", OperationUpdateDirective.OperandlessCommand.Kind.Null)
      }

      (Vector("nickname", "nickname__update_command") ++
        OperationUpdateDirective.ExplicitValueAssignment.kinds.map(kind => "nickname" + kind.suffix) ++
        OperationUpdateDirective.ValueOperation.kinds.map(kind => "nickname" + kind.suffix)).foreach { key =>
        s"reject JSON syntax null at $key before Record omission" in {
          Given("an Entity update body with a root ordinary assignment or carrier set to JSON null")
          val text = Json.obj(key -> Json.Null).noSpaces

          When("syntax admission runs before semantic Record construction")
          val result = OperationJsonBodyDecoder.decode(text, _parameters, Some(_operation))

          Then("a structured client policy error requires an explicit clear instruction")
          result match {
            case Consequence.Failure(conclusion) =>
              conclusion.status.webCode.code shouldBe 400
              conclusion.observation.cause.descriptor.facets should contain(Descriptor.Facet.Reason("explicit-clear-required"))
              (conclusion.show +: conclusion.causes.map(_.display)).mkString("\n") should include ("explicit-clear-required")
            case _ => fail("JSON null became omission, a document or an adaptive blank command")
          }
        }
      }
      "preserve zero-length explicit values and let only blank strings select adaptive commands" in {
        Given("ordinary String carriers using explicit empty, adaptive empty and whitespace operands")
        val text = """{"nickname__value":"","tags__value_or_clear":"","optionalName__value_or_null":"","spaced__value_or_null":" "}"""

        When("JSON syntax admission is followed by the existing directive normalizer")
        val record = _decode(text)
        val directives = _take(OperationUpdateDirectiveNormalizer.normalize(record))

        Then("JSON strings retain the established explicit assignment and adaptive command rules")
        record.getString("nickname__value") shouldBe Some("")
        directives.directive("nickname") shouldBe OperationUpdateDirective.ExplicitValueAssignment(
          "nickname", OperationUpdateDirective.ExplicitValueAssignment.Kind.Value, Vector(""))
        directives.directive("tags") shouldBe OperationUpdateDirective.OperandlessCommand(
          "tags", OperationUpdateDirective.OperandlessCommand.Kind.Clear)
        directives.directive("optionalName") shouldBe OperationUpdateDirective.OperandlessCommand(
          "optionalName", OperationUpdateDirective.OperandlessCommand.Kind.Null)
        directives.directive("spaced") shouldBe OperationUpdateDirective.ExplicitValueAssignment(
          "spaced", OperationUpdateDirective.ExplicitValueAssignment.Kind.ValueOrNull, Vector(" "))
      }
      Vector(
        """{"payload":null,"payload__update_command":"null"}""",
        """{"payload__value":{"gone":null},"payload":[null]}""",
        """{"nickname__value":"","nickname__value_or_null":""}"""
      ).zipWithIndex.foreach { case (text, index) =>
        s"retain every conflicting key for existing directive validation case $index" in {
          Given("an object containing conflicting plain, value or command carriers")
          val keys = _json(text).asObject.get.keys.toVector

          When("the adapter decodes and the existing normalizer evaluates the complete Record")
          val record = _decode(text)
          val result = OperationUpdateDirectiveNormalizer.normalize(record)

          Then("original order and conflict information survive until structured directive rejection")
          record.fields.map(_.key) shouldBe keys
          result shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "retain opaque body compatibility" which {
      Vector("{", "", "[]", "null", "42", "\"text\"", """[{"key":1,"key":2}]""",
        """{"key":1,"key":2} trailing""").zipWithIndex.foreach { case (text, index) =>
        s"return None for malformed or non-object syntax case $index" in {
          Given("body syntax that the historical adapter could not decode as an object")
          val parameters = _parameters

          When("the body is offered to the foreign syntax adapter")
          val result = OperationJsonBodyDecoder.decode(text, parameters, Some(_operation))

          Then("the caller receives only the established opaque Bag fallback signal")
          result.toOption shouldBe Some(None)
        }
      }
    }

    "preserve independent raw and semantic shapes" which {
      "satisfy at least one hundred ordinary shrinking samples with zero discards" in {
        Given("independent raw text, semantic text, exact decimal and ordered duplicate generators")
        val samples = for {
          rawtext <- Gen.alphaNumStr
          semantictext <- Gen.alphaNumStr
          number <- Gen.choose(-1000000, 1000000)
          values <- Gen.listOf(Gen.alphaNumStr)
        } yield (rawtext, semantictext, number, values)
        val property = Prop.forAll(samples) { case (rawtext, semantictext, number, values) =>
          Given("one independently generated raw document and semantic child sequence with ordered duplicates")
          val decimal = Json.fromBigDecimal(BigDecimal(number) / BigDecimal(1000))
          val raw = Json.obj("null" -> Json.Null, "array" -> Json.arr(Json.Null, decimal), "text" -> Json.fromString(rawtext))
          val ordered = (values ++ values).toVector
          val semantic = Json.obj("gone" -> Json.Null, "empty" -> Json.obj(), "array" -> Json.arr(),
            "text" -> Json.fromString(semantictext), "values" -> Json.fromValues(ordered.map(value =>
              Json.obj("gone" -> Json.Null, "text" -> Json.fromString(value)))))
          val body = Json.obj("payload" -> raw, "evidence" -> semantic,
            "payloads" -> Json.arr(raw, Json.Null, raw))
          val expected = Record.dataAuto("empty" -> Record.empty, "array" -> Vector.empty[Any],
            "text" -> semantictext, "values" -> ordered.map(value => Record.dataAuto("text" -> value)))

          When("the selected Operation decodes the complete body through raw selection and Core normalization")
          val record = _decode(body.noSpaces)

          Then("each independent raw document and semantic shape retains its own exact contract")
          record.fields.map(_.key) shouldBe Vector("payload", "evidence", "payloads")
          record.getAny("payload") shouldBe Some(raw)
          record.getAny("payloads") shouldBe Some(Vector(raw, Json.Null, raw))
          record.getAny("evidence") shouldBe Some(expected)
          true
        }

        When("ScalaCheck executes the connected shrinking property")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred successful samples and no discarded cases are required")
        withClue(checked.toString) { checked.passed shouldBe true }
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }

  private val _parameters = Vector(
    _parameter("payload", XJson), _parameter("payloads", XJson, Multiplicity.ZeroMore),
    _parameter("evidence", XSemiStructuredData), _parameter("nickname", XString, Multiplicity.ZeroOne))
  private val _operation = CmlOperationDefinition(
    name = "update", kind = "COMMAND", inputType = "Policy115", outputType = "Unit", inputValueKind = "ENTITY_UPDATE",
    parameters = Vector(
      CmlOperationField("payload", "Json", "?", update = Some(CmlOperationUpdateField("?", true))),
      CmlOperationField("payloads", "Json", "?", update = Some(CmlOperationUpdateField("*", false))),
      CmlOperationField("nickname", "String", "?", update = Some(CmlOperationUpdateField("?", true)))))

  private def _parameter(name: String, datatype: DataType, multiplicity: Multiplicity = Multiplicity.One): ParameterDefinition =
    ParameterDefinition(content = BaseContent.simple(name), kind = ParameterDefinition.Kind.Argument,
      domain = ValueDomain(datatype, multiplicity))
  private def _decode(text: String, parameters: Seq[ParameterDefinition] = _parameters,
    operation: Option[CmlOperationDefinition] = Some(_operation)): Record =
    _take(OperationJsonBodyDecoder.decode(text, parameters, operation)).getOrElse(fail("expected an object Record"))
  private def _json(text: String): Json = io.circe.parser.parse(text).fold(error => fail(error.message), identity)
  private def _contains_scala_null(value: Any): Boolean = value match {
    case null => true
    case record: Record => record.fields.exists(field => _contains_scala_null(field.value.single))
    case values: Seq[?] => values.exists(_contains_scala_null)
    case _ => false
  }
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail((conclusion.show +: conclusion.causes.map(_.display)).mkString("\n"))
  }
}
