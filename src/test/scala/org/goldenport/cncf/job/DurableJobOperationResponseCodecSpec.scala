package org.goldenport.cncf.job

import java.math.{MathContext, RoundingMode}
import java.nio.charset.StandardCharsets
import cats.data.NonEmptyVector
import io.circe.{Json, JsonNumber}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobOperationResponseCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _prefix = "{\"format\":{\"schemaId\":\"cncf.job-operation-response\",\"version\":1},\"response\":"
  private val _maximum_bytes = 8388608

  private def _scenario(example: String) =
    afterWord(s"in spec:durable-job-operation-response-codec phase:69.7 slice:JM69-10B1 example:$example")

  "Full completed Review domain values" should _scenario("ORC1") {
    "preserve independently specified ordered documents rather than a summary" in {
      Given("ORC1: a representative completed six-field Review Record and independently handwritten canonical wire, without an actual CBD runtime")
      val fields = Vector("reviewId" -> "review-1", "reportId" -> "report-1", "reportDigest" -> "sha256:abc",
        "reportDocument" -> "full report", "runDocument" -> "full run", "attestationDocument" -> "full attestation")
      val expected = OperationResponse.RecordResponse(Record(fields.map { case (key, value) => Field(key, Field.Value.Single(value)) }))
      val wire = """{"format":{"schemaId":"cncf.job-operation-response","version":1},"response":{"kind":"record","fields":[{"key":"reviewId","value":{"type":"string","value":"review-1"}},{"key":"reportId","value":{"type":"string","value":"report-1"}},{"key":"reportDigest","value":{"type":"string","value":"sha256:abc"}},{"key":"reportDocument","value":{"type":"string","value":"full report"}},{"key":"runDocument","value":{"type":"string","value":"full run"}},{"key":"attestationDocument","value":{"type":"string","value":"full attestation"}}]}}"""
      When("the domain response is encoded and the independent literal is decoded")
      val encoded = DurableJobOperationResponseCodec.encode(expected)
      val decoded = DurableJobOperationResponseCodec.decode(wire)
      Then("the exact wire and concrete ordered full fields equal the independent expected domain value")
      encoded.toOption shouldBe Some(wire)
      decoded.toOption shouldBe Some(expected)
      decoded.toOption.get.asInstanceOf[OperationResponse.RecordResponse].record.getClass shouldBe classOf[Record]
      _observe(decoded.toOption.get) shouldBe _observe(expected)
    }
  }

  "Closed response families" should _scenario("ORC2") {
    "retain all five native Scalar types, Void, JSON lexemes and verbatim YAML" in {
      Given("ORC2: native family values paired with independent canonical body literals and ordered JSON numeric lexemes")
      val json = parse("""{"z":1.00,"a":[null,true,"界😀",1e+02]}""").toOption.get
      val cases: Vector[(OperationResponse, String)] = Vector(
        OperationResponse.Void() -> """{"kind":"void"}""",
        OperationResponse.Scalar(7) -> """{"kind":"scalar","value":{"type":"int","value":7}}""",
        OperationResponse.Scalar(7L) -> """{"kind":"scalar","value":{"type":"long","value":7}}""",
        OperationResponse.Scalar(-0.0d) -> """{"kind":"scalar","value":{"type":"double","value":"-0x0.0p0"}}""",
        OperationResponse.Scalar(true) -> """{"kind":"scalar","value":{"type":"boolean","value":true}}""",
        OperationResponse.Scalar("界😀\n") -> """{"kind":"scalar","value":{"type":"string","value":"界😀\n"}}""",
        OperationResponse.Json(json) -> """{"kind":"json","value":{"z":1.00,"a":[null,true,"界😀",1e+02]}}""",
        OperationResponse.Yaml("a: &ref\n  - *ref\n") -> """{"kind":"yaml","value":"a: &ref\n  - *ref\n"}"""
      )
      When("each family is encoded and each independent wire is restored")
      val encoded = cases.map { case (response, _) => DurableJobOperationResponseCodec.encode(response) }
      val decoded = cases.map { case (_, body) => DurableJobOperationResponseCodec.decode(_prefix + body + "}") }
      Then("families, concrete scalar types, JSON insertion order and lexemes and verbatim YAML all remain exact")
      encoded.map(_.toOption) shouldBe cases.map { case (_, body) => Some(_prefix + body + "}") }
      decoded.map(_.toOption.map(_observe)) shouldBe cases.map { case (response, _) => Some(_observe(response)) }
      decoded(6).toOption.get.asInstanceOf[OperationResponse.Json].json.noSpaces shouldBe json.noSpaces
      decoded.last.toOption.get shouldBe cases.last._1
    }
  }

  "Native nested typed atoms" should _scenario("ORC3") {
    "retain widths, duplicates, collections, finite bits and raw decimal context at their boundaries" in {
      Given("ORC3: ordered duplicate/empty nested Records and Core-canonical collections, all numeric widths, finite extreme bits and a nondefault unrounded decimal context")
      val context = new MathContext(2, RoundingMode.DOWN)
      val decimal = new BigDecimal(new java.math.BigDecimal(new java.math.BigInteger("1234500"), 4), context)
      val numbers: Vector[Any] = Vector(Byte.MinValue, Byte.MaxValue, Short.MinValue, Short.MaxValue,
        Int.MinValue, Int.MaxValue, Long.MinValue, Long.MaxValue, -0.0f, Float.MinPositiveValue, Float.MaxValue,
        -0.0d, Double.MinPositiveValue, Double.MaxValue, BigInt("9" * 1024), decimal,
        new BigDecimal(new java.math.BigDecimal(java.math.BigInteger.ONE, -16384), new MathContext(1024, RoundingMode.UNNECESSARY)),
        new BigDecimal(new java.math.BigDecimal(java.math.BigInteger.ONE, 16384), MathContext.UNLIMITED))
      val record = Record(Vector(Field("dup", Field.Value.Single("first")), Field("dup", Field.Value.Single(Record())),
        Field("list", Field.Value.Single(List("x", Vector(1.toByte)))), Field("vector", Field.Value.Single(Vector("x", List(1.toByte))))) ++
        numbers.zipWithIndex.map { case (value, index) => Field(s"n$index", Field.Value.Single(value)) })
      val response = OperationResponse.RecordResponse(record)
      val refusedvalues: Vector[Any] = Vector(Float.NaN, Float.PositiveInfinity, Double.NaN, Double.NegativeInfinity,
        BigInt("1" + ("0" * 1024)), BigInt(1) << 100000,
        new BigDecimal(new java.math.BigDecimal(java.math.BigInteger.ONE, 16385), MathContext.UNLIMITED),
        new BigDecimal(java.math.BigDecimal.ONE, new MathContext(1025)))
      When("the exact typed value is round-tripped and numeric values outside the frozen domain are encoded")
      val encoded = DurableJobOperationResponseCodec.encode(response)
      val decoded = encoded.flatMap(DurableJobOperationResponseCodec.decode)
      val refused = refusedvalues.map(value => DurableJobOperationResponseCodec.encode(_record_response(value)))
      Then("all concrete nested observations including raw bits and decimal unscaled/scale/context survive and every out-of-domain numeric refuses")
      encoded.isSuccess shouldBe true
      decoded.toOption.map(_observe) shouldBe Some(_observe(response))
      refused.foreach(_refused)
    }
  }

  "Core-owned Record construction" should {
    "omit null attributes and canonicalize sequence containers before codec admission" in {
      Given("public Core inputs containing a null attribute, ordered duplicate keys, an empty child and nested List values")
      val fields = Vector("absent" -> null, "dup" -> "first", "dup" -> Record.empty,
        "items" -> List("x", Vector("x")))
      When("the Core C constructor admits those inputs")
      val constructed = Record.createC(fields)
      Then("the valid Record has no null property and keeps ordered duplicates, empty structures and Vector sequences")
      constructed.isSuccess shouldBe true
      val record = constructed.toOption.get
      record.fields.map(_.key) shouldBe Vector("dup", "dup", "items")
      record.getAny("absent") shouldBe None
      record.fields(1).value.single shouldBe Record.empty
      record.getAny("items") shouldBe Some(Vector("x", Vector("x")))
    }
  }

  "Hostile unsupported values" should _scenario("ORC4") {
    "refuse without invoking observation, getter, provider or conversion capabilities" in {
      Given("ORC4: genuine hostile response families and opaque hostile atoms held by valid public Record construction, with throwing counting methods")
      val counter = new Counter
      val unknown = new Object {
        override def toString: String = counter.touch()
      }
      val response = new OperationResponse {
        def toResponse: Response = counter.touch()
        override def print: String = counter.touch()
        override def display: String = counter.touch()
        override def show: String = counter.touch()
      }
      val closure = () => counter.touch()
      val values: Vector[OperationResponse] = Vector(null, OperationResponse.Opaque(unknown), OperationResponse.Http(null), response,
        OperationResponse.RecordResponse(null), OperationResponse.Json(null), OperationResponse.Yaml(null),
        OperationResponse.Scalar(null.asInstanceOf[String]), OperationResponse.Scalar(1)(using null)) ++
        Vector[Any](unknown, closure, Map("x" -> 1), Set(1), Some(1), (), 'x',
          new java.math.BigInteger("1"), java.math.BigDecimal.ONE).map(_record_response)
      When("each hostile value is offered to the closed producer")
      val results = values.map(DurableJobOperationResponseCodec.encode)
      Then("only the constant redacted refusal is returned and no capability is observed")
      results.foreach(_refused)
      counter.count shouldBe 0
    }
  }

  "Closed canonical consumer admission" should _scenario("ORC5") {
    "refuse malformed, noncanonical, numerically invalid and Unicode-invalid text" in {
      Given("ORC5: actual malformed field/wire shapes, hostile under-limit bodies, wrong descriptors, duplicate keys and bounded numeric or surrogate violations")
      val valid = _prefix + """{"kind":"void"}}"""
      val bodies = Vector("""{"kind":"other"}""", """{"kind":"void","extra":1}""", """{}""",
        """{"kind":1}""", """{"kind":"void","kind":"void"}""", """{"kind":"scalar"}""",
        """{"kind":"scalar","value":{"type":"byte","value":1}}""",
        """{"kind":"scalar","value":{"type":"int","value":2147483648}}""",
        """{"kind":"scalar","value":{"type":"long","value":9223372036854775808}}""",
        """{"kind":"scalar","value":{"type":"int","value":1.0}}""",
        """{"kind":"scalar","value":{"type":"double","value":"NaN"}}""",
        """{"kind":"scalar","value":{"type":"double","value":"0.0"}}""",
        """{"kind":"scalar","value":{"type":"int","value":"1"}}""",
        """{"kind":"scalar","value":{"type":"string","value":null}}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"unknown","value":1}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"byte","value":128}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"short","value":32768}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"float","value":"0x1.0p128"}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"bigint","value":"01"}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"bigint","value":"-0"}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"bigdecimal","unscaled":"1","scale":16385,"precision":0,"rounding":"UP"}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"bigdecimal","unscaled":"1","scale":0,"precision":1025,"rounding":"UP"}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"bigdecimal","unscaled":"1","scale":0,"precision":0,"rounding":"other"}}]}""",
        """{"kind":"record","fields":[{"key":"x"}]}""", """{"kind":"record","fields":null}""",
        """{"kind":"record","fields":[null]}""", """{"kind":"record","fields":[{"key":null,"value":{"type":"int","value":1}}]}""",
        """{"kind":"record","fields":[{"key":"x","value":null}]}""",
        """{"kind":"record","fields":[{"key":"x","value":{}}]}""",
        """{"kind":"json","value":{"k":"\ud800"}}""", """{"kind":"json","value":{"\udc00":0}}""",
        """{"kind":"yaml","value":"\ud800"}""", """{"kind":"yaml","value":"\u0061"}""",
        """{"kind":"yaml","value":"""" + '\ud800' + "\"}",
        """{"kind":"record","fields":[{"key":"x","value":{"type":"bigint","value":"""" + ("9" * 1025) + "\"}}]}")
      val texts = bodies.map(body => _prefix + body + "}") ++ Vector(null, "", valid.dropRight(1), " " + valid,
        valid.replace("\"version\":1", "\"version\":2"), valid.replace("\"schemaId\":", "\"unknown\":"),
        valid.replace("\"version\":1", "\"version\":1,\"version\":1"), valid.replace("\"version\":1", "\"version\":1.0"),
        valid.dropRight(1) + ",\"extra\":0}", "{\"response\":{\"kind\":\"void\"},\"format\":{\"schemaId\":\"cncf.job-operation-response\",\"version\":1}}")
      When("the consumer interprets every malformed or noncanonical text")
      val results = texts.map(DurableJobOperationResponseCodec.decode)
      Then("no domain value is returned and every diagnostic equals the fixed refusal")
      results.foreach(_refused)
    }
  }

  "Full wire byte and node accounting" should _scenario("ORC6") {
    "admit exactly 8 MiB and 32768 nodes while refusing the first excess" in {
      Given("ORC6: independently sized ASCII, multibyte and escaped YAML wires plus JSON arrays whose seven envelope/container nodes leave 32761 null elements")
      val yamlprefix = _prefix + """{"kind":"yaml","value":""""
      val yamlsuffix = "\"}}"
      val capacity = _maximum_bytes - _bytes(yamlprefix + yamlsuffix)
      val cases = Vector("x", "界😀", "\n\"\\\u0001").map { unit =>
        val escaped = Json.fromString(unit).noSpaces.drop(1).dropRight(1)
        val units = capacity / _bytes(escaped)
        val remainder = capacity % _bytes(escaped)
        val value = (unit * units) + ("x" * remainder)
        val wire = yamlprefix + (escaped * units) + ("x" * remainder) + yamlsuffix
        OperationResponse.Yaml(value) -> wire
      }
      val nodewire = _prefix + "{\"kind\":\"json\",\"value\":[" + Vector.fill(32761)("null").mkString(",") + "]}}"
      val nodeover = nodewire.dropRight(3) + ",null]}}"
      val nodevalue = OperationResponse.Json(Json.fromValues(Vector.fill(32761)(Json.Null)))
      val nodeextra = OperationResponse.Json(Json.fromValues(Vector.fill(32762)(Json.Null)))
      val malformed = "{" + ("界" * (_maximum_bytes / 3 + 1))
      When("bounded values are encoded, exact wires decoded, and one-over values/text and oversized malformed input admitted")
      val encoded = cases.map { case (value, _) => DurableJobOperationResponseCodec.encode(value) }
      val decoded = cases.map { case (_, wire) => DurableJobOperationResponseCodec.decode(wire) }
      val refused = cases.flatMap { case (value, wire) => Vector(
        DurableJobOperationResponseCodec.encode(OperationResponse.Yaml(value.yaml + "x")),
        DurableJobOperationResponseCodec.decode(wire.dropRight(3) + "x\"}}")) }
      val nodesencoded = DurableJobOperationResponseCodec.encode(nodevalue)
      val nodesdecoded = DurableJobOperationResponseCodec.decode(nodewire)
      val nodesrefused = Vector(DurableJobOperationResponseCodec.encode(nodeextra), DurableJobOperationResponseCodec.decode(nodeover))
      val preflight = DurableJobOperationResponseResourcePolicy.validateText(malformed)
      Then("inclusive actual UTF-8/full-wire budgets succeed exactly and every excess refuses without truncation")
      cases.foreach { case (_, wire) => _bytes(wire) shouldBe _maximum_bytes }
      encoded.map(_.toOption) shouldBe cases.map { case (_, wire) => Some(wire) }
      decoded.map(_.toOption) shouldBe cases.map { case (value, _) => Some(value) }
      refused.foreach(_refused)
      nodesencoded.toOption shouldBe Some(nodewire)
      nodesdecoded.toOption shouldBe Some(nodevalue)
      nodesrefused.foreach(_refused)
      preflight shouldBe Left("durable job operation response refused")
    }
  }

  "Container and quoted-token resource admission" should _scenario("ORC7") {
    "admit depth 64, refuse 65 and extreme input, and keep strict grammar beneath lexical preflight" in {
      Given("ORC7: a JSON response with 62 or 63 nested value arrays, escaped delimiter strings and very deep or huge malformed text")
      val exactjson = _nested_json(62)
      val overjson = _nested_json(63)
      val exactwire = _prefix + "{\"kind\":\"json\",\"value\":" + ("[" * 62) + "null" + ("]" * 62) + "}}"
      val overwire = _prefix + "{\"kind\":\"json\",\"value\":" + ("[" * 63) + "null" + ("]" * 63) + "}}"
      val quoted = OperationResponse.Yaml("\"\\{[}]" * 100)
      val hostile = Vector("[" * 100000, "{" + ("x" * _maximum_bytes))
      val grammar = "{\"not-a-value\":}"
      When("the producer and consumer admit nested values, resource preflight scans quoted and extreme text, and the consumer interprets malformed grammar")
      val encoded = DurableJobOperationResponseCodec.encode(OperationResponse.Json(exactjson))
      val decoded = DurableJobOperationResponseCodec.decode(exactwire)
      val refused = Vector(DurableJobOperationResponseCodec.encode(OperationResponse.Json(overjson)), DurableJobOperationResponseCodec.decode(overwire))
      val quotedwire = DurableJobOperationResponseCodec.encode(quoted)
      val quoteddecoded = quotedwire.flatMap(DurableJobOperationResponseCodec.decode)
      val preflights = hostile.map(DurableJobOperationResponseResourcePolicy.validateText)
      val grammarresource = DurableJobOperationResponseResourcePolicy.validateText(grammar)
      val grammarresult = DurableJobOperationResponseCodec.decode(grammar)
      Then("the actual root/response/container depth is inclusive, quoted delimiters never count, and resource admission never weakens grammar")
      encoded.toOption shouldBe Some(exactwire)
      decoded.toOption shouldBe Some(OperationResponse.Json(exactjson))
      refused.foreach(_refused)
      quoteddecoded.toOption shouldBe Some(quoted)
      preflights.foreach(_ shouldBe Left("durable job operation response refused"))
      grammarresource shouldBe Right(())
      _refused(grammarresult)
    }
  }

  "Generated supported domain preservation" should _scenario("ORC8") {
    "round-trip ordinary shrinking bounded trees with type-sensitive observations" in {
      Given("ORC8: depth-at-most-three declared response/Record/JSON generators, collections zero to six, Unicode/escape strings and selected finite raw bits")
      val generator = _response_generator
      val property = Prop.forAll(generator) { response =>
        Given("ORC8 generated case: one supported native response and its independent type-sensitive domain observations")
        val expected = _observe(response)
        When("that generated response is genuinely encoded then decoded")
        val encoded = DurableJobOperationResponseCodec.encode(response)
        val decoded = encoded.flatMap(DurableJobOperationResponseCodec.decode)
        Then("the generated response's concrete family, nested types, bits, decimal context and JSON semantics survive")
        encoded.isSuccess shouldBe true
        decoded.toOption.map(_observe) shouldBe Some(expected)
        true
      }
      When("ScalaCheck checks the ordinary shrinking property with seed 69710021L and at least 100 successful cases")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710021L)).withMinSuccessfulTests(100), property)
      Then("the checked property passes and reports at least 100 successful generated cases")
      checked.passed shouldBe true
      checked.succeeded should be >= 100
    }
  }

  "All six current JSON numeric forms" should _scenario("ORC9") {
    "preserve independent lexemes in direct responses and nested Record atoms" in {
      Given("ORC9: six public-factory numeric forms with independently handwritten spellings, including an unrounded raw decimal and a huge exponent")
      val raw = new BigDecimal(new java.math.BigDecimal(new java.math.BigInteger("1234500"), 4), new MathContext(2))
      val cases = Vector(
        Json.fromLong(Long.MinValue) -> "-9223372036854775808",
        Json.fromDouble(-0.0d).get -> "-0.0",
        Json.fromFloat(1.25f).get -> "1.25",
        Json.fromBigDecimal(raw) -> "123.4500",
        Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe("-0.00e+9999999999999999999999999999")) -> "-0.00e+9999999999999999999999999999",
        Json.fromJsonNumber(JsonNumber.fromString("1.00e+02").get) -> "1.00e+02")
      val fixtures = cases.flatMap { case (json, lexeme) => Vector(
        (OperationResponse.Json(json): OperationResponse) -> (_prefix + "{\"kind\":\"json\",\"value\":" + lexeme + "}}"),
        _record_response(json) -> (_prefix + "{\"kind\":\"record\",\"fields\":[{\"key\":\"value\",\"value\":{\"type\":\"json\",\"value\":" + lexeme + "}}]}}")) }
      When("both placements are encoded and independently literal canonical wires are decoded")
      val encoded = fixtures.map { case (response, _) => DurableJobOperationResponseCodec.encode(response) }
      val decoded = fixtures.map { case (_, wire) => DurableJobOperationResponseCodec.decode(wire) }
      Then("all six original numeric spellings and their exact response or ordered Record placement survive")
      encoded.map(_.toOption) shouldBe fixtures.map { case (_, wire) => Some(wire) }
      decoded.map(_.toOption.map(_observe)) shouldBe fixtures.map { case (response, _) => Some(_observe(response)) }
    }
  }

  "Native JSON decimal layouts" should _scenario("ORC10") {
    "retain canonical thresholds, signed coefficients, zeros and full Int scale" in {
      Given("ORC10: an independently handwritten coefficient/scale/layout table, negative mirrors and zero at both scale extremes")
      val positive = Vector(("123", 0, "123"), ("123", 1, "12.3"), ("123", 5, "0.00123"),
        ("123", 8, "0.00000123"), ("123", 9, "1.23E-7"), ("123", -1, "1.23E+3"))
      val layouts = positive ++ positive.map { case (coefficient, scale, text) => ("-" + coefficient, scale, "-" + text) } ++
        Vector(("0", 6, "0.000000"), ("0", 7, "0E-7"),
          ("1", Int.MaxValue, "1E-2147483647"), ("1", Int.MinValue, "1E+2147483648"),
          ("0", Int.MaxValue, "0E-2147483647"), ("0", Int.MinValue, "0E+2147483648"))
      val fixtures = layouts.map { case (coefficient, scale, text) =>
        val raw = new java.math.BigDecimal(new java.math.BigInteger(coefficient), scale)
        val response = OperationResponse.Json(Json.fromBigDecimal(new BigDecimal(raw, MathContext.UNLIMITED)))
        response -> (_prefix + "{\"kind\":\"json\",\"value\":" + text + "}}")
      }
      When("each native decimal is encoded and each independently specified canonical wire is decoded")
      val encoded = fixtures.map { case (response, _) => DurableJobOperationResponseCodec.encode(response) }
      val decoded = fixtures.map { case (_, wire) => DurableJobOperationResponseCodec.decode(wire) }
      Then("every exact plain or scientific spelling survives without scale, precision or magnitude coercion")
      encoded.map(_.toOption) shouldBe fixtures.map { case (_, wire) => Some(wire) }
      decoded.map(_.toOption.map(_observe)) shouldBe fixtures.map { case (response, _) => Some(_observe(response)) }
    }
  }

  "Numeric full-wire byte capacities" should _scenario("ORC11") {
    "admit exact native and stored capacities and refuse excess before unbounded conversion" in {
      Given("ORC11: independently sized padding/native-decimal and stored-digit wires exactly 8 MiB, one-over values and a raw coefficient beyond the four-bits-per-byte work bound")
      val arrayprefix = _prefix + "{\"kind\":\"json\",\"value\":[\""
      val arraysuffix = "\",1.23]}}"
      val padding = "x" * (_maximum_bytes - _bytes(arrayprefix + arraysuffix))
      val native = Json.fromBigDecimal(new BigDecimal(new java.math.BigDecimal(new java.math.BigInteger("123"), 2), MathContext.UNLIMITED))
      val arrayvalue = OperationResponse.Json(Json.arr(Json.fromString(padding), native))
      val arrayover = OperationResponse.Json(Json.arr(Json.fromString(padding + "x"), native))
      val arraywire = arrayprefix + padding + arraysuffix
      val numberprefix = _prefix + "{\"kind\":\"json\",\"value\":"
      val numbersuffix = "}}"
      val lexeme = "1" + ("0" * (_maximum_bytes - _bytes(numberprefix + numbersuffix) - 1))
      val stored = OperationResponse.Json(Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe(lexeme)))
      val storedover = OperationResponse.Json(Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe(lexeme + "0")))
      val storedwire = numberprefix + lexeme + numbersuffix
      val giant = java.math.BigInteger.ONE.shiftLeft(4 * _maximum_bytes)
      val giantresponse = OperationResponse.Json(Json.fromBigDecimal(new BigDecimal(new java.math.BigDecimal(giant), MathContext.UNLIMITED)))
      When("exact values are encoded, independently assembled exact wires decoded and each excess or giant coefficient offered to the producer")
      val encoded = Vector(DurableJobOperationResponseCodec.encode(arrayvalue), DurableJobOperationResponseCodec.encode(stored))
      val decoded = Vector(DurableJobOperationResponseCodec.decode(arraywire), DurableJobOperationResponseCodec.decode(storedwire))
      val refused = Vector(DurableJobOperationResponseCodec.encode(arrayover), DurableJobOperationResponseCodec.encode(storedover),
        DurableJobOperationResponseCodec.encode(giantresponse))
      Then("both complete wires meet the inclusive actual byte limit and retain exact values while every excess gives the constant refusal")
      _bytes(arraywire) shouldBe _maximum_bytes
      _bytes(storedwire) shouldBe _maximum_bytes
      encoded.map(_.toOption) shouldBe Vector(Some(arraywire), Some(storedwire))
      decoded.map(_.toOption.map(_observe)) shouldBe Vector(Some(_observe(arrayvalue)), Some(_observe(stored)))
      refused.foreach(_refused)
    }
  }

  "Closed numeric capability refusal" should _scenario("ORC12") {
    "reject malformed stored lexemes and raw JDK subclasses before codec-time callbacks" in {
      Given("ORC12: malformed unsafe-factory lexemes and hostile raw decimals and a positive 128-bit integer, with counters captured after Scala wrapper construction")
      val tokens = Vector("01", "+1", "-", "1.", "1e", "1e+", "1true", "NaN", "Infinity", "\ud800", null)
      val malformed = tokens.map(text => OperationResponse.Json(Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe(text))))
      val decimalcounter = new Counter
      val rawdecimal = new java.math.BigDecimal(java.math.BigInteger.ONE, 0) {
        override def unscaledValue(): java.math.BigInteger = decimalcounter.touch()
        override def scale(): Int = decimalcounter.touch()
        override def toString: String = decimalcounter.touch()
      }
      val decimal = new BigDecimal(rawdecimal, MathContext.UNLIMITED)
      val integercounter = new Counter
      val rawinteger = new java.math.BigInteger("170141183460469231731687303715884105728") {
        override def bitLength(): Int = { integercounter.increment(); super.bitLength() }
        override def toString: String = integercounter.touch()
      }
      val integer = BigInt(rawinteger)
      val hostile = Vector(OperationResponse.Json(Json.fromBigDecimal(decimal)), _record_response(decimal), _record_response(integer))
      val decimalbefore = decimalcounter.count
      val integerbefore = integercounter.count
      When("malformed tokens and fully constructed hostile numeric responses are offered to the codec")
      val malformedresults = malformed.map(DurableJobOperationResponseCodec.encode)
      val hostileresults = hostile.map(DurableJobOperationResponseCodec.encode)
      Then("all diagnostics are the fixed refusal and codec calls leave both native capability counters unchanged")
      malformedresults.foreach(_refused)
      hostileresults.foreach(_refused)
      decimalcounter.count shouldBe decimalbefore
      integercounter.count shouldBe integerbefore
    }
  }

  "Retained format1 Record input" should _scenario("ORC13") {
    "omit field nulls while preserving ordered duplicates, empty structures and raw JSON lexemes" in {
      Given("ORC13: independently handwritten full retained format1 text with nested null fields, old lists, duplicate Records and raw JSON documents")
      val wire = """{"format":{"schemaId":"cncf.job-operation-response","version":1},"response":{"kind":"record","fields":[{"key":"absent","value":{"type":"null"}},{"key":"dup","value":{"type":"string","value":"first"}},{"key":"child","value":{"type":"record","fields":[{"key":"gone","value":{"type":"null"}},{"key":"empty","value":{"type":"record","fields":[]}},{"key":"array","value":{"type":"list","values":[]}}]}},{"key":"dup","value":{"type":"record","fields":[]}},{"key":"items","value":{"type":"list","values":[{"type":"record","fields":[{"key":"gone","value":{"type":"null"}},{"key":"id","value":{"type":"int","value":2}}]},{"type":"record","fields":[]},{"type":"record","fields":[{"key":"id","value":{"type":"int","value":2}}]}]}},{"key":"mixed","value":{"type":"vector","values":[{"type":"list","values":[{"type":"int","value":2},{"type":"int","value":1},{"type":"int","value":2}]},{"type":"vector","values":[]}]}},{"key":"rawNull","value":{"type":"json","value":null}},{"key":"rawDocument","value":{"type":"json","value":{"z":1.2300,"a":null,"empty":{},"array":[]}}},{"key":"rawArray","value":{"type":"json","value":[null,1e+02,-0.00,{}]}}]}}"""
      val canonical = """{"format":{"schemaId":"cncf.job-operation-response","version":1},"response":{"kind":"record","fields":[{"key":"dup","value":{"type":"string","value":"first"}},{"key":"child","value":{"type":"record","fields":[{"key":"empty","value":{"type":"record","fields":[]}},{"key":"array","value":{"type":"vector","values":[]}}]}},{"key":"dup","value":{"type":"record","fields":[]}},{"key":"items","value":{"type":"vector","values":[{"type":"record","fields":[{"key":"id","value":{"type":"int","value":2}}]},{"type":"record","fields":[]},{"type":"record","fields":[{"key":"id","value":{"type":"int","value":2}}]}]}},{"key":"mixed","value":{"type":"vector","values":[{"type":"vector","values":[{"type":"int","value":2},{"type":"int","value":1},{"type":"int","value":2}]},{"type":"vector","values":[]}]}},{"key":"rawNull","value":{"type":"json","value":null}},{"key":"rawDocument","value":{"type":"json","value":{"z":1.2300,"a":null,"empty":{},"array":[]}}},{"key":"rawArray","value":{"type":"json","value":[null,1e+02,-0.00,{}]}}]}}"""
      val item = Record.data("id" -> 2)
      val rawdocument = Json.obj("z" -> Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe("1.2300")),
        "a" -> Json.Null, "empty" -> Json.obj(), "array" -> Json.arr())
      val rawarray = Json.arr(Json.Null, Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe("1e+02")),
        Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe("-0.00")), Json.obj())
      val expected = Record.create(Vector("dup" -> "first",
        "child" -> Record.data("empty" -> Record.empty, "array" -> Vector.empty), "dup" -> Record.empty,
        "items" -> Vector(item, Record.empty, item), "mixed" -> Vector(Vector(2, 1, 2), Vector.empty),
        "rawNull" -> Json.Null, "rawDocument" -> rawdocument, "rawArray" -> rawarray))
      When("the retained literal is decoded and its canonical semantic response is encoded again")
      val decoded = DurableJobOperationResponseCodec.decode(wire)
      val reencoded = decoded.flatMap(DurableJobOperationResponseCodec.encode)
      val canonicaldecoded = DurableJobOperationResponseCodec.decode(canonical)
      Then("the independently constructed normalized value keeps exact field and element order and the new wire differs only by declared omission and vector normalization")
      decoded.toOption.map(_observe) shouldBe Some(_observe(OperationResponse.RecordResponse(expected)))
      val record = decoded.toOption.get.asInstanceOf[OperationResponse.RecordResponse].record
      record.fields.map(_.key) shouldBe Vector("dup", "child", "dup", "items", "mixed", "rawNull", "rawDocument", "rawArray")
      record.getAny("absent") shouldBe None
      record.getRecord("child").get.fields.map(_.key) shouldBe Vector("empty", "array")
      record.getAny("items") shouldBe Some(Vector(item, Record.empty, item))
      record.getAny("mixed") shouldBe Some(Vector(Vector(2, 1, 2), Vector.empty))
      record.getAny("rawNull") shouldBe Some(Json.Null)
      record.getAny("rawDocument").get.asInstanceOf[Json].noSpaces shouldBe """{"z":1.2300,"a":null,"empty":{},"array":[]}"""
      record.getAny("rawArray").get.asInstanceOf[Json].noSpaces shouldBe """[null,1e+02,-0.00,{}]"""
      reencoded.toOption shouldBe Some(canonical)
      canonicaldecoded.toOption.map(_observe) shouldBe decoded.toOption.map(_observe)
    }
  }

  "Nonempty semantic collections" should _scenario("ORC14") {
    "encode actual NonEmptyVectors as vector atoms and reconstruct declared nonempty types through Core" in {
      Given("ORC14: actual nested Cats NonEmptyVectors retained by valid Core construction, with an empty ordinary collection")
      val numbers = NonEmptyVector.of(2, 1, 2)
      val item = Record.data("numbers" -> numbers, "empty" -> Record.empty)
      val items = NonEmptyVector.of(item, item)
      val source = Record.data("items" -> items, "empty" -> Vector.empty)
      val wire = """{"format":{"schemaId":"cncf.job-operation-response","version":1},"response":{"kind":"record","fields":[{"key":"items","value":{"type":"vector","values":[{"type":"record","fields":[{"key":"numbers","value":{"type":"vector","values":[{"type":"int","value":2},{"type":"int","value":1},{"type":"int","value":2}]}},{"key":"empty","value":{"type":"record","fields":[]}}]},{"type":"record","fields":[{"key":"numbers","value":{"type":"vector","values":[{"type":"int","value":2},{"type":"int","value":1},{"type":"int","value":2}]}},{"key":"empty","value":{"type":"record","fields":[]}}]}]}},{"key":"empty","value":{"type":"vector","values":[]}}]}}"""
      When("the actual nonempty response is encoded, the literal decoded and Core typed readers reconstruct its declared cardinalities")
      val encoded = DurableJobOperationResponseCodec.encode(OperationResponse.RecordResponse(source))
      val decoded = DurableJobOperationResponseCodec.decode(wire)
        .map(_.asInstanceOf[OperationResponse.RecordResponse].record)
      val typeditems = decoded.flatMap(_.asC[NonEmptyVector[Record]]("items"))
      val typednumbers = typeditems.flatMap(_.head.asC[NonEmptyVector[Int]]("numbers"))
      val empty = decoded.flatMap(_.asC[NonEmptyVector[Record]]("empty"))
      val reencoded = decoded.flatMap(record => DurableJobOperationResponseCodec.encode(OperationResponse.RecordResponse(record)))
      Then("wire atoms use vector, untyped input stays Vector and explicit Core nonempty reads retain duplicates and order while refusing empty input")
      source.getAny("items") shouldBe Some(items)
      item.getAny("numbers") shouldBe Some(numbers)
      encoded.toOption shouldBe Some(wire)
      decoded.toOption.get.getAny("items") shouldBe Some(Vector(
        Record.data("numbers" -> Vector(2, 1, 2), "empty" -> Record.empty),
        Record.data("numbers" -> Vector(2, 1, 2), "empty" -> Record.empty)))
      typednumbers.toOption shouldBe Some(numbers)
      typeditems.toOption.get.toVector.map(_.getRecord("empty")) shouldBe Vector(Some(Record.empty), Some(Record.empty))
      empty.isSuccess shouldBe false
      reencoded.toOption shouldBe Some(wire)
    }
  }

  "Retained null position and grammar admission" should _scenario("ORC15") {
    "refuse root and every sequence null without shifting positions or weakening closed syntax" in {
      Given("ORC15: root null atoms, first/middle/last list and vector nulls, nested null positions and malformed or reordered retained field atoms")
      val nullatom = """{"type":"null"}"""
      val integer = """{"type":"int","value":1}"""
      val sequences = Vector("list", "vector").flatMap { kind =>
        (0 to 2).map { position =>
          val values = Vector.fill(3)(integer).updated(position, nullatom).mkString(",")
          s"""{"type":"$kind","values":[$values]}"""
        }
      }
      val nested = Vector(
        """{"type":"list","values":[{"type":"int","value":1},{"type":"vector","values":[{"type":"int","value":1},{"type":"null"}]}]}""",
        """{"type":"vector","values":[{"type":"list","values":[{"type":"null"},{"type":"int","value":1}]}]}""",
        """{"type":"list","values":[{"type":"record","fields":[{"key":"child","value":{"type":"vector","values":[{"type":"null"}]}}]}]}""")
      val malformed = Vector("""{"type":"null","extra":0}""", """{"extra":0,"type":"null"}""",
        """{"type":"null","type":"null"}""", """{"type":"unknown","values":[]}""",
        """{"values":[],"type":"list"}""", """{"value":1,"type":"int"}""")
      val fields = (sequences ++ nested ++ malformed).map(atom =>
        _prefix + """{"kind":"record","fields":[{"key":"x","value":""" + atom + "}]}}")
      val controls = Vector(
        _prefix + nullatom + "}",
        _prefix + """{"kind":"scalar","value":{"type":"null"}}}""",
        _prefix + """{"kind":"record","fields":[{"value":{"type":"null"},"key":"x"}]}}""",
        _prefix + """{"fields":[],"kind":"record"}}""",
        """{"format":{"version":1,"schemaId":"cncf.job-operation-response"},"response":{"kind":"void"}}""")
      When("the consumer admits each retained null placement and malformed closed grammar")
      val results = (fields ++ controls).map(DurableJobOperationResponseCodec.decode)
      Then("every invalid input yields the unchanged constant redacted refusal instead of a filtered or partially reconstructed response")
      results.foreach(_refused)
    }
  }

  "Retained shape normalization laws" should _scenario("ORC16") {
    "connect shrinking retained list/null input to independent Vector semantics and canonical new output" in {
      Given("ORC16: an ordinary shrinking Vector[Int] generator of zero to six ordered values with duplicates allowed")
      val generator = Gen.choose(0, 6).flatMap(size => Gen.listOfN(size, Gen.choose(-9, 9))).map(_.toVector)
      val property = Prop.forAll(generator) { values =>
        Given("ORC16 generated case: independently assembled retained null fields and mixed old list/vector atoms for the generated ordered values")
        val atoms = values.map(value => s"""{"type":"int","value":$value}""").mkString(",")
        val wire = _prefix + """{"kind":"record","fields":[{"key":"gone","value":{"type":"null"}},{"key":"items","value":{"type":"list","values":[{"type":"record","fields":[{"key":"gone","value":{"type":"null"}},{"key":"values","value":{"type":"list","values":[""" +
          atoms + """]}}]},{"type":"record","fields":[]},{"type":"vector","values":[]}]}},{"key":"items","value":{"type":"list","values":[]}}]}}"""
        val canonical = _prefix + """{"kind":"record","fields":[{"key":"items","value":{"type":"vector","values":[{"type":"record","fields":[{"key":"values","value":{"type":"vector","values":[""" +
          atoms + """]}}]},{"type":"record","fields":[]},{"type":"vector","values":[]}]}},{"key":"items","value":{"type":"vector","values":[]}}]}}"""
        val expected = OperationResponse.RecordResponse(Record.create(Vector(
          "items" -> Vector(Record.data("values" -> values), Record.empty, Vector.empty), "items" -> Vector.empty)))
        When("the retained text is decoded and encoded through the real codec")
        val decoded = DurableJobOperationResponseCodec.decode(wire)
        val encoded = decoded.flatMap(DurableJobOperationResponseCodec.encode)
        val canonicaldecoded = DurableJobOperationResponseCodec.decode(canonical)
        Then("omission, duplicate properties, empty structures and generated element order agree with the independent semantic and wire expectations")
        decoded.toOption.map(_observe) shouldBe Some(_observe(expected))
        encoded.toOption shouldBe Some(canonical)
        canonicaldecoded.toOption.map(_observe) shouldBe Some(_observe(expected))
        true
      }
      When("ScalaCheck checks the connected retained-shape property with ordinary shrinking and at least 100 successful cases")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(11516001L)).withMinSuccessfulTests(100), property)
      Then("the property passes with at least 100 successes and zero discarded cases")
      checked.passed shouldBe true
      checked.succeeded should be >= 100
      checked.discarded shouldBe 0
    }
  }

  private final class Counter {
    private var _count = 0
    def count: Int = _count
    def increment(): Unit = { _count += 1 }
    def touch(): Nothing = { _count += 1; throw new IllegalStateException("private capability") }
  }

  private def _refused(actual: Consequence[?]): Unit = {
    actual.toOption shouldBe None
    val expected: Consequence[Nothing] = Consequence.argumentInvalid[Nothing]("durable job operation response refused")
    (actual, expected) match {
      case (Consequence.Failure(actualconclusion), Consequence.Failure(expectedconclusion)) =>
        actualconclusion.status shouldBe expectedconclusion.status
        actualconclusion.observation.taxonomy shouldBe expectedconclusion.observation.taxonomy
        actualconclusion.observation.phenomenon shouldBe expectedconclusion.observation.phenomenon
        actualconclusion.observation.cause.kind shouldBe expectedconclusion.observation.cause.kind
        actualconclusion.displayMessage shouldBe expectedconclusion.displayMessage
        actualconclusion.interpretation shouldBe expectedconclusion.interpretation
        actualconclusion.disposition shouldBe expectedconclusion.disposition
        actualconclusion.previous shouldBe expectedconclusion.previous
        actualconclusion.getException shouldBe None
      case (Consequence.Success(_), _) => fail("expected refusal")
      case (_, Consequence.Success(_)) => fail("expected refusal")
    }
  }

  private def _bytes(text: String): Int = text.getBytes(StandardCharsets.UTF_8).length

  private def _record_response(value: Any): OperationResponse =
    OperationResponse.RecordResponse(Record(Vector(Field("value", Field.Value.Single(value)))))

  private def _nested_json(depth: Int): Json =
    (0 until depth).foldLeft(Json.Null)((value, _) => Json.arr(value))

  private def _observe(response: OperationResponse): Any = response match {
    case _: OperationResponse.Void => "void"
    case value: OperationResponse.Scalar[?] => ("scalar", _atom_observation(value.value))
    case value: OperationResponse.RecordResponse => ("record-response", _atom_observation(value.record))
    case value: OperationResponse.Json => ("json-response", value.json.noSpaces)
    case value: OperationResponse.Yaml => ("yaml", value.yaml)
    case _ => fail("unsupported generated fixture")
  }

  private def _atom_observation(value: Any): Any = value match {
    case null => "null"
    case value: String => ("string", value)
    case value: Boolean => ("boolean", value)
    case value: Byte => ("byte", value)
    case value: Short => ("short", value)
    case value: Int => ("int", value)
    case value: Long => ("long", value)
    case value: Float => ("float", java.lang.Float.floatToRawIntBits(value))
    case value: Double => ("double", java.lang.Double.doubleToRawLongBits(value))
    case value: BigInt => ("bigint", value)
    case value: BigDecimal => ("bigdecimal", value.bigDecimal.unscaledValue, value.bigDecimal.scale, value.mc.getPrecision, value.mc.getRoundingMode)
    case value: Record => ("record", value.fields.map(field => (field.getClass, field.key, field.value.getClass,
      _atom_observation(field.value.asInstanceOf[Field.Value.Single].single))))
    case value: Vector[?] => ("vector", value.map(_atom_observation))
    case value: List[?] => ("list", value.map(_atom_observation))
    case value: Json => ("json", value.noSpaces)
    case _ => fail("unsupported atom fixture")
  }

  private def _string_generator: Gen[String] =
    Gen.choose(0, 6).flatMap(size => Gen.listOfN(size, Gen.oneOf("a", "界", "😀", "\n", "\"", "\\", "\u0001")).map(_.mkString))

  private def _atom_generator(depth: Int): Gen[Any] = {
    val primitive: Gen[Any] = Gen.oneOf(
      _string_generator.map(value => value: Any), Gen.oneOf(true, false).map(value => value: Any),
      Gen.choose(Byte.MinValue.toInt, Byte.MaxValue.toInt).map(value => value.toByte: Any),
      Gen.choose(Short.MinValue.toInt, Short.MaxValue.toInt).map(value => value.toShort: Any),
      Gen.choose(Int.MinValue, Int.MaxValue).map(value => value: Any),
      Gen.oneOf(Long.MinValue, -1L, 0L, 1L, Long.MaxValue).map(value => value: Any),
      Gen.oneOf(-0.0f, Float.MinPositiveValue, Float.MaxValue, 1.5f).map(value => value: Any),
      Gen.oneOf(-0.0d, Double.MinPositiveValue, Double.MaxValue, 1.5d).map(value => value: Any),
      Gen.choose(-100000, 100000).map(value => BigInt(value): Any),
      Gen.choose(-100000, 100000).map(value => new BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(value.toLong), 3), new MathContext(2, RoundingMode.HALF_DOWN)): Any),
      _json_generator(0).map(value => value: Any))
    if (depth == 0) primitive
    else Gen.oneOf(primitive, _record_generator(depth - 1).map(value => value: Any),
      Gen.choose(0, 6).flatMap(size => Gen.listOfN(size, _atom_generator(depth - 1))).map(value => value: Any),
      Gen.choose(0, 6).flatMap(size => Gen.listOfN(size, _atom_generator(depth - 1))).map(value => value.toVector: Any),
      _json_generator(depth - 1).map(value => value: Any))
  }

  private def _record_generator(depth: Int): Gen[Record] =
    Gen.choose(0, 6).flatMap(size => Gen.listOfN(size,
      for { key <- _string_generator; value <- _atom_generator(depth) } yield Field(key, Field.Value.Single(value))))
      .map(fields => Record(fields.toVector))

  private def _json_generator(depth: Int): Gen[Json] = {
    val primitive = Gen.oneOf(Gen.const(Json.Null), _string_generator.map(Json.fromString),
      Gen.oneOf(true, false).map(Json.fromBoolean),
      _json_number_generator,
      Gen.oneOf("1.00", "1e+02", "-0", "123456789012345678901234567890").map(text => parse(text).toOption.get))
    if (depth == 0) primitive
    else Gen.oneOf(primitive,
      Gen.choose(0, 6).flatMap(size => Gen.listOfN(size, _json_generator(depth - 1))).map(Json.fromValues),
      Gen.choose(0, 6).flatMap(size => Gen.listOfN(size,
        for { key <- _string_generator; value <- _json_generator(depth - 1) } yield key -> value)).map(Json.fromFields))
  }

  private def _json_number_generator: Gen[Json] = Gen.oneOf(
    Gen.oneOf(Long.MinValue, 0L, Long.MaxValue).map(Json.fromLong),
    Gen.oneOf(-0.0d, Double.MinPositiveValue, Double.MaxValue).map(value => Json.fromDouble(value).get),
    Gen.oneOf(-0.0f, 1.25f, Float.MaxValue).map(value => Json.fromFloat(value).get),
    Gen.choose(-100000, 100000).map(value => Json.fromBigDecimal(new BigDecimal(
      new java.math.BigDecimal(java.math.BigInteger.valueOf(value.toLong), 3), new MathContext(2)))),
    Gen.oneOf("-0.00e+9999999999999999999999999999", "1.00", "1E-02")
      .map(text => Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe(text))),
    Gen.oneOf("1.00e+02", "-0", "123.4500").map(text => Json.fromJsonNumber(JsonNumber.fromString(text).get)))

  private def _response_generator: Gen[OperationResponse] = Gen.oneOf(
    Gen.const(OperationResponse.Void(): OperationResponse),
    Gen.choose(Int.MinValue, Int.MaxValue).map(value => OperationResponse.Scalar(value): OperationResponse),
    Gen.oneOf(Long.MinValue, 0L, Long.MaxValue).map(value => OperationResponse.Scalar(value): OperationResponse),
    Gen.oneOf(-0.0d, Double.MinPositiveValue, Double.MaxValue).map(value => OperationResponse.Scalar(value): OperationResponse),
    Gen.oneOf(true, false).map(value => OperationResponse.Scalar(value): OperationResponse),
    _string_generator.map(value => OperationResponse.Scalar(value): OperationResponse),
    _record_generator(3).map(value => OperationResponse.RecordResponse(value): OperationResponse),
    _json_generator(3).map(value => OperationResponse.Json(value): OperationResponse),
    _string_generator.map(value => OperationResponse.Yaml(value): OperationResponse))

  /* Standard scalar and collection shrinkers keep failing domain cases small. */
  private given _response_shrink: Shrink[OperationResponse] = Shrink {
    case value: OperationResponse.Scalar[?] => value.value match {
      case value: Int => Shrink.shrink[Int](value).map((shrunk: Int) => OperationResponse.Scalar[Int](shrunk))
      case value: Long => Shrink.shrink[Long](value).map((shrunk: Long) => OperationResponse.Scalar[Long](shrunk))
      case value: Double => Shrink.shrink[Double](value).map((shrunk: Double) => OperationResponse.Scalar[Double](shrunk))
      case value: Boolean => Shrink.shrink[Boolean](value).map((shrunk: Boolean) => OperationResponse.Scalar[Boolean](shrunk))
      case value: String => Shrink.shrink[String](value).map((shrunk: String) => OperationResponse.Scalar[String](shrunk))
      case _ => Shrink.shrink(0).map(_ => OperationResponse.Void())
    }
    case value: OperationResponse.RecordResponse =>
      Shrink.shrink(value.record.fields).map(fields => OperationResponse.RecordResponse(Record(fields)))
    case value: OperationResponse.Yaml => Shrink.shrink(value.yaml).map(OperationResponse.Yaml(_))
    case value: OperationResponse.Json => value.json.asArray match {
      case Some(values) => Shrink.shrink(values).map(values => OperationResponse.Json(Json.fromValues(values)))
      case None => value.json.asObject match {
        case Some(fields) => Shrink.shrink(fields.toIterable.toVector).map(fields => OperationResponse.Json(Json.fromFields(fields)))
        case None => value.json.asString match {
          case Some(text) => Shrink.shrink(text).map(text => OperationResponse.Json(Json.fromString(text)))
          case None => Shrink.shrink(0).map(_ => OperationResponse.Json(Json.Null))
        }
      }
    }
    case _ => Shrink.shrink(0).map(_ => OperationResponse.Void())
  }
}
