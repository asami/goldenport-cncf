package org.goldenport.cncf.job

import java.math.{MathContext, RoundingMode}
import cats.data.NonEmptyVector
import io.circe.{Json, JsonNumber}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.scalar.ScalarValue
import org.goldenport.record.{Field, Record}
import scala.util.control.NonFatal
import DurableJobOperationResponseResourcePolicy.{RenderingBudget, refuse}

/*
 * @since   Oct.  4, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationResponseCodec {
  private val _message = "durable job operation response refused"
  private val _json_long_class = Json.fromLong(0L).asNumber.get.getClass
  private val _json_double_class = Json.fromDouble(0.0d).get.asNumber.get.getClass
  private val _json_float_class = Json.fromFloat(0.0f).get.asNumber.get.getClass
  private val _json_bigdecimal_class = Json.fromBigDecimal(BigDecimal(0)).asNumber.get.getClass
  private val _json_decimal_class = JsonNumber.fromDecimalStringUnsafe("0").getClass
  private val _json_biggerdecimal_class = JsonNumber.fromString("0").get.getClass

  def encode(response: OperationResponse): Consequence[String] =
    _admit(_render(response))

  def decode(text: String): Consequence[OperationResponse] = _admit {
    if (DurableJobOperationResponseResourcePolicy.validateText(text).isLeft) refuse()
    val root = parse(text).fold(_ => refuse(), identity)
    if (root.noSpaces != text) refuse()
    _members(root, "format", "response")
    val format = _get(root, "format")
    _members(format, "schemaId", "version")
    if (_string(_get(format, "schemaId")) != "cncf.job-operation-response" ||
        _integer(_get(format, "version"), 1, 1) != 1) refuse()
    _decode_response(_get(root, "response"))
  }

  private def _admit[A](value: => A): Consequence[A] =
    try Consequence.success(value)
    catch {
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        Consequence.argumentInvalid(_message)
      case NonFatal(_) => Consequence.argumentInvalid(_message)
    }

  private def _render(response: OperationResponse): String = {
    val budget = new RenderingBudget
    budget.begin('{')
    budget.key("format")
    budget.begin('{')
    budget.key("schemaId")
    budget.string("cncf.job-operation-response")
    budget.punctuation(",")
    budget.key("version")
    budget.literal("1")
    budget.end('}')
    budget.punctuation(",")
    budget.key("response")
    _response(response, budget)
    budget.end('}')
    budget.result
  }

  private def _response(response: OperationResponse, budget: RenderingBudget): Unit = {
    budget.begin('{')
    budget.key("kind")
    response match {
      case _: OperationResponse.Void => budget.string("void")
      case value: OperationResponse.Scalar[?] =>
        _scalar(value)
        budget.string("scalar")
        budget.punctuation(",")
        budget.key("value")
        _atom(value.value, budget)
      case value: OperationResponse.RecordResponse =>
        budget.string("record")
        budget.punctuation(",")
        budget.key("fields")
        _fields(value.record, budget)
      case value: OperationResponse.Json =>
        budget.string("json")
        budget.punctuation(",")
        budget.key("value")
        _json(value.json, budget)
      case value: OperationResponse.Yaml =>
        budget.string("yaml")
        budget.punctuation(",")
        budget.key("value")
        budget.string(value.yaml)
      case _ => refuse()
    }
    budget.end('}')
  }

  private def _scalar(value: OperationResponse.Scalar[?]): Unit = {
    val expected = value.value match {
      case _: Int => summon[ScalarValue[Int]]
      case _: Long => summon[ScalarValue[Long]]
      case _: Double => summon[ScalarValue[Double]]
      case _: Boolean => summon[ScalarValue[Boolean]]
      case _: String => summon[ScalarValue[String]]
      case _ => refuse()
    }
    if (!(value.scalarValue.asInstanceOf[AnyRef] eq expected)) refuse()
  }

  private def _fields(record: Record, budget: RenderingBudget): Unit = {
    if (record == null || record.getClass != classOf[Record]) refuse()
    val fields = record.fields
    budget.begin('[')
    val iterator = fields.iterator
    var first = true
    while (iterator.hasNext) {
      if (!first) budget.punctuation(",")
      first = false
      val field = iterator.next()
      if (field.getClass != classOf[Field]) refuse()
      val value = field.value
      if (value.getClass != classOf[Field.Value.Single]) refuse()
      budget.begin('{')
      budget.key("key")
      budget.string(field.key)
      budget.punctuation(",")
      budget.key("value")
      _atom(value.asInstanceOf[Field.Value.Single].single, budget)
      budget.end('}')
    }
    budget.end(']')
  }

  private def _atom(value: Any, budget: RenderingBudget): Unit = {
    budget.begin('{')
    budget.key("type")
    value match {
      case null => refuse()
      case value: String => _typed_string("string", value, budget)
      case value: Boolean => _typed_literal("boolean", if (value) "true" else "false", budget)
      case value: Byte => _typed_literal("byte", java.lang.Byte.toString(value), budget)
      case value: Short => _typed_literal("short", java.lang.Short.toString(value), budget)
      case value: Int => _typed_literal("int", java.lang.Integer.toString(value), budget)
      case value: Long => _typed_literal("long", java.lang.Long.toString(value), budget)
      case value: Float =>
        if (!java.lang.Float.isFinite(value)) refuse()
        _typed_string("float", java.lang.Float.toHexString(value), budget)
      case value: Double =>
        if (!java.lang.Double.isFinite(value)) refuse()
        _typed_string("double", java.lang.Double.toHexString(value), budget)
      case value: BigInt => _typed_string("bigint", _bigint(value), budget)
      case value: BigDecimal =>
        val raw = value.bigDecimal
        if (raw == null || raw.getClass != classOf[java.math.BigDecimal]) refuse()
        val context = value.mc
        if (raw.scale < -16384 || raw.scale > 16384 || context.getPrecision > 1024) refuse()
        val unscaled = _native_bigint(raw.unscaledValue)
        budget.string("bigdecimal")
        budget.punctuation(",")
        budget.key("unscaled")
        budget.string(unscaled)
        budget.punctuation(",")
        budget.key("scale")
        budget.literal(java.lang.Integer.toString(raw.scale))
        budget.punctuation(",")
        budget.key("precision")
        budget.literal(java.lang.Integer.toString(context.getPrecision))
        budget.punctuation(",")
        budget.key("rounding")
        budget.string(context.getRoundingMode.name())
      case value: Record =>
        budget.string("record")
        budget.punctuation(",")
        budget.key("fields")
        _fields(value, budget)
      case value: Vector[?] => _sequence("vector", value.iterator, budget)
      case value: NonEmptyVector[?] => _sequence("vector", value.toVector.iterator, budget)
      case value: List[?] => _sequence("list", value.iterator, budget)
      case value: Json =>
        budget.string("json")
        budget.punctuation(",")
        budget.key("value")
        _json(value, budget)
      case _ => refuse()
    }
    budget.end('}')
  }

  private def _typed_string(kind: String, value: String, budget: RenderingBudget): Unit = {
    budget.string(kind)
    budget.punctuation(",")
    budget.key("value")
    budget.string(value)
  }

  private def _typed_literal(kind: String, value: String, budget: RenderingBudget): Unit = {
    budget.string(kind)
    budget.punctuation(",")
    budget.key("value")
    budget.literal(value)
  }

  private def _sequence(kind: String, iterator: Iterator[?], budget: RenderingBudget): Unit = {
    budget.string(kind)
    budget.punctuation(",")
    budget.key("values")
    budget.begin('[')
    var first = true
    while (iterator.hasNext) {
      if (!first) budget.punctuation(",")
      first = false
      _atom(iterator.next(), budget)
    }
    budget.end(']')
  }

  private def _json(value: Json, budget: RenderingBudget): Unit = {
    if (value == null) refuse()
    value.asObject match {
      case Some(value) =>
        budget.begin('{')
        val iterator = value.toIterable.iterator
        var first = true
        while (iterator.hasNext) {
          if (!first) budget.punctuation(",")
          first = false
          val (key, child) = iterator.next()
          budget.key(key)
          _json(child, budget)
        }
        budget.end('}')
      case None => value.asArray match {
        case Some(value) =>
          budget.begin('[')
          val iterator = value.iterator
          var first = true
          while (iterator.hasNext) {
            if (!first) budget.punctuation(",")
            first = false
            _json(iterator.next(), budget)
          }
          budget.end(']')
        case None => value.asString match {
          case Some(value) => budget.string(value)
          case None => value.asNumber match {
            case Some(value) => budget.literal(_json_number(value, budget.remainingBytes))
            case None => value.asBoolean match {
              case Some(value) => budget.literal(if (value) "true" else "false")
              case None if value.isNull => budget.literal("null")
              case _ => refuse()
            }
          }
        }
      }
    }
  }

  private def _bigint(value: BigInt): String = {
    _native_bigint(value.bigInteger)
  }

  private def _native_bigint(value: java.math.BigInteger): String = {
    if (value == null || value.getClass != classOf[java.math.BigInteger]) refuse()
    if (value.bitLength > 3402) refuse()
    val text = value.toString
    _signed_decimal(text, 1024)
    text
  }

  /* Closed adapter to the six sealed final Circe 0.14.6 representations. */
  private def _json_number(value: JsonNumber, remaining: Int): String = {
    if (value == null) refuse()
    val identity = value.getClass
    val bigger = identity == _json_biggerdecimal_class
    if (identity != _json_long_class && identity != _json_double_class &&
        identity != _json_float_class && identity != _json_bigdecimal_class &&
        identity != _json_decimal_class && !bigger) refuse()
    val product = value.asInstanceOf[Product]
    if (product.productArity != (if (bigger) 2 else 1)) refuse()
    val member = product.productElement(if (bigger) 1 else 0)
    val text =
      if (identity == _json_long_class) member match {
        case number: Long => java.lang.Long.toString(number)
        case _ => refuse()
      } else if (identity == _json_double_class) member match {
        case number: Double if java.lang.Double.isFinite(number) => java.lang.Double.toString(number)
        case _ => refuse()
      } else if (identity == _json_float_class) member match {
        case number: Float if java.lang.Float.isFinite(number) => java.lang.Float.toString(number)
        case _ => refuse()
      } else if (identity == _json_bigdecimal_class) member match {
        case number: java.math.BigDecimal => _json_decimal(number, remaining)
        case _ => refuse()
      } else member match {
        case input: String => input
        case _ => refuse()
      }
    _numeric_lexeme(text, remaining)
  }

  /* No parsing, numeric coercion or copying of stored input lexemes. */
  private def _numeric_lexeme(text: String, remaining: Int): String = {
    if (text == null || text.isEmpty || text.length > remaining) refuse()
    var index = if (text.charAt(0) == '-') 1 else 0
    if (index == text.length) refuse()
    if (text.charAt(index) == '0') index += 1
    else {
      if (text.charAt(index) < '1' || text.charAt(index) > '9') refuse()
      index += 1
      while (index < text.length && _digit(text.charAt(index))) index += 1
    }
    if (index < text.length && text.charAt(index) == '.') {
      index += 1
      val start = index
      while (index < text.length && _digit(text.charAt(index))) index += 1
      if (index == start) refuse()
    }
    if (index < text.length && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
      index += 1
      if (index < text.length && (text.charAt(index) == '+' || text.charAt(index) == '-')) index += 1
      val start = index
      while (index < text.length && _digit(text.charAt(index))) index += 1
      if (index == start) refuse()
    }
    if (index != text.length) refuse()
    text
  }

  private def _digit(character: Char): Boolean = character >= '0' && character <= '9'

  /* Prove both coefficient conversion and final canonical layout fit first. */
  private def _json_decimal(raw: java.math.BigDecimal, remaining: Int): String = {
    if (raw == null || raw.getClass != classOf[java.math.BigDecimal]) refuse()
    val coefficient = raw.unscaledValue
    if (coefficient == null || coefficient.getClass != classOf[java.math.BigInteger]) refuse()
    val bits = coefficient.bitLength.toLong
    if (bits > 4L * remaining) refuse()
    val negative = coefficient.signum < 0
    val sign = if (negative) 1 else 0
    val capacity = remaining - sign
    if (capacity < 1) refuse()
    val lower = (math.max(bits - 1, 0L) * 301029995L) / 1000000000L + 1
    val upper = ((bits + 1) * 301029996L) / 1000000000L + 1
    if (lower > capacity) refuse()
    if (upper > capacity && coefficient.abs.compareTo(java.math.BigInteger.TEN.pow(capacity)) >= 0) refuse()
    val text = coefficient.toString
    val digits = text.length - sign
    val scale = raw.scale.toLong
    val adjusted = (digits - 1).toLong - scale
    val plain = scale >= 0 && adjusted >= -6
    val exponent = if (plain) "" else java.lang.Long.toString(adjusted)
    val length = sign.toLong + (if (plain) {
      if (scale == 0) digits.toLong
      else if (scale < digits) digits.toLong + 1
      else scale + 2
    } else digits.toLong + (if (digits > 1) 1 else 0) + 1 +
      (if (adjusted >= 0) 1 else 0) + exponent.length)
    if (length > remaining) refuse()
    val builder = new java.lang.StringBuilder(length.toInt)
    if (negative) builder.append('-')
    if (plain) {
      if (scale == 0) builder.append(text, sign, text.length)
      else if (scale < digits) {
        val point = sign + digits - scale.toInt
        builder.append(text, sign, point)
        builder.append('.')
        builder.append(text, point, text.length)
      } else {
        builder.append("0.")
        var zeros = scale - digits
        while (zeros > 0) { builder.append('0'); zeros -= 1 }
        builder.append(text, sign, text.length)
      }
    } else {
      builder.append(text.charAt(sign))
      if (digits > 1) {
        builder.append('.')
        builder.append(text, sign + 1, text.length)
      }
      builder.append('E')
      if (adjusted >= 0) builder.append('+')
      builder.append(exponent)
    }
    builder.toString
  }

  private def _signed_decimal(text: String, maximum: Int): Unit = {
    if (text == null || text.isEmpty) refuse()
    val start = if (text.charAt(0) == '-') 1 else 0
    val digits = text.length - start
    if (digits < 1 || digits > maximum ||
        (text.charAt(start) == '0' && (digits != 1 || start == 1))) refuse()
    var index = start
    while (index < text.length) {
      if (text.charAt(index) < '0' || text.charAt(index) > '9') refuse()
      index += 1
    }
  }

  private def _members(value: Json, keys: String*): Unit = {
    val objectvalue = value.asObject.getOrElse(refuse())
    if (objectvalue.keys.toVector != keys.toVector) refuse()
  }

  private def _get(value: Json, key: String): Json =
    value.asObject.flatMap(_(key)).getOrElse(refuse())

  private def _string(value: Json): String = value.asString.getOrElse(refuse())

  private def _integer(value: Json, minimum: Long, maximum: Long): Long = {
    val text = _json_number(value.asNumber.getOrElse(refuse()), 20)
    _signed_decimal(text, 19)
    val number = java.lang.Long.parseLong(text)
    if (number < minimum || number > maximum) refuse()
    number
  }

  private def _decode_response(value: Json): OperationResponse =
    _string(_get(value, "kind")) match {
      case "void" =>
        _members(value, "kind")
        OperationResponse.Void()
      case "scalar" =>
        _members(value, "kind", "value")
        _decode_atom(_get(value, "value")) match {
          case value: Int => OperationResponse.Scalar(value)
          case value: Long => OperationResponse.Scalar(value)
          case value: Double => OperationResponse.Scalar(value)
          case value: Boolean => OperationResponse.Scalar(value)
          case value: String => OperationResponse.Scalar(value)
          case _ => refuse()
        }
      case "record" =>
        _members(value, "kind", "fields")
        OperationResponse.RecordResponse(_decode_fields(_get(value, "fields")))
      case "json" =>
        _members(value, "kind", "value")
        OperationResponse.Json(_get(value, "value"))
      case "yaml" =>
        _members(value, "kind", "value")
        OperationResponse.Yaml(_string(_get(value, "value")))
      case _ => refuse()
    }

  private def _decode_fields(value: Json): Record = {
    val fields = value.asArray.getOrElse(refuse()).flatMap { field =>
      _members(field, "key", "value")
      val key = _string(_get(field, "key"))
      val atom = _get(field, "value")
      if (_string(_get(atom, "type")) == "null") {
        _members(atom, "type")
        None
      } else Some(Field(key, Field.Value.Single(_decode_atom(atom))))
    }
    Record(fields)
  }

  private def _decode_atom(value: Json): Any = {
    val kind = _string(_get(value, "type"))
    kind match {
      case "null" =>
        _members(value, "type")
        refuse()
      case "bigdecimal" =>
        _members(value, "type", "unscaled", "scale", "precision", "rounding")
        val text = _string(_get(value, "unscaled"))
        _signed_decimal(text, 1024)
        val scale = _integer(_get(value, "scale"), -16384, 16384).toInt
        val precision = _integer(_get(value, "precision"), 0, 1024).toInt
        val rounding = RoundingMode.valueOf(_string(_get(value, "rounding")))
        val raw = new java.math.BigDecimal(new java.math.BigInteger(text), scale)
        new BigDecimal(raw, new MathContext(precision, rounding))
      case "record" =>
        _members(value, "type", "fields")
        _decode_fields(_get(value, "fields"))
      case "vector" | "list" =>
        _members(value, "type", "values")
        _get(value, "values").asArray.getOrElse(refuse()).map(_decode_atom)
      case _ =>
        _members(value, "type", "value")
        val atom = _get(value, "value")
        kind match {
          case "string" => _string(atom)
          case "boolean" => atom.asBoolean.getOrElse(refuse())
          case "byte" => _integer(atom, Byte.MinValue, Byte.MaxValue).toByte
          case "short" => _integer(atom, Short.MinValue, Short.MaxValue).toShort
          case "int" => _integer(atom, Int.MinValue, Int.MaxValue).toInt
          case "long" => _integer(atom, Long.MinValue, Long.MaxValue)
          case "float" =>
            val text = _string(atom)
            if (text.length > 32) refuse()
            val number = java.lang.Float.parseFloat(text)
            if (!java.lang.Float.isFinite(number) || java.lang.Float.toHexString(number) != text) refuse()
            number
          case "double" =>
            val text = _string(atom)
            if (text.length > 32) refuse()
            val number = java.lang.Double.parseDouble(text)
            if (!java.lang.Double.isFinite(number) || java.lang.Double.toHexString(number) != text) refuse()
            number
          case "bigint" =>
            val text = _string(atom)
            _signed_decimal(text, 1024)
            BigInt(text)
          case "json" => atom
          case _ => refuse()
        }
    }
  }
}
