package org.goldenport.cncf.display

import java.time.Instant
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import scala.util.control.NonFatal

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Internal strict primitives; renderer preserves every UTF16 code unit. */
private[display] object DisplayMutationJson {
  import DisplayMutation.*
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _decimal_pattern = "(-?)([0-9]+)(?:\\.([0-9]+))?(?:E([+-]?[0-9]+))?".r

  def _parse(json: String): Consequence[Json] = {
    if (json == null) inputInvalidC("ELEMENT_INVALID", "json")
    else try {
      _parser.parse(json).fold(_ => inputInvalidC("ELEMENT_INVALID", "$"), Consequence.success)
    } catch { case NonFatal(_) => inputInvalidC("ELEMENT_INVALID", "$") }
  }
  def _object(json: Json, path: String, required: Vector[String], optional: Vector[String] = Vector.empty): Consequence[JsonObject] =
    json.asObject match {
      case Some(obj) if required.forall(obj.contains) &&
        obj.keys.forall(key => required.contains(key) || optional.contains(key)) => Consequence.success(obj)
      case _ => inputInvalidC("ELEMENT_INVALID", path)
    }
  def _member(obj: JsonObject, name: String): Json = obj(name).getOrElse(Json.Null)
  def _string(obj: JsonObject, name: String, path: String): Consequence[String] =
    _string_value(_member(obj, name), path + "." + name)
  def _string_value(json: Json, path: String): Consequence[String] =
    json.asString.map(Consequence.success).getOrElse(inputInvalidC("ELEMENT_INVALID", path))
  def _boolean_value(json: Json, path: String): Consequence[Boolean] =
    json.asBoolean.map(Consequence.success).getOrElse(inputInvalidC("ELEMENT_INVALID", path))
  def _expected(obj: JsonObject, name: String, expected: String, path: String, code: String): Consequence[Unit] =
    _string(obj, name, path).flatMap(value =>
      if (value == expected) Consequence.success(()) else inputInvalidC(code, path + "." + name))
  def _integer(json: Json, path: String, minimum: BigInt, maximum: BigInt): Consequence[BigInt] = {
    try {
      json.asNumber match {
        case Some(number) =>
          val spelling = number.toString
          if (!spelling.matches("0|[1-9][0-9]*")) inputInvalidC("VALUE_INVALID", path)
          else {
            val value = BigInt(spelling)
            if (value < minimum || value > maximum || value.toString != spelling) inputInvalidC("VALUE_INVALID", path)
            else Consequence.success(value)
          }
        case _ => inputInvalidC("ELEMENT_INVALID", path)
      }
    } catch { case NonFatal(_) => inputInvalidC("VALUE_INVALID", path) }
  }
  def _protocol(obj: JsonObject, schema: String): Consequence[Unit] = for {
    _ <- _expected(obj, "schema", schema, "$", "VALUE_INVALID")
    _ <- _integer(_member(obj, "version"), "$.version", BigInt(1), BigInt(1))
  } yield ()
  def _array[A](obj: JsonObject, name: String, path: String)(decode: (Json, String) => Consequence[A]): Consequence[Vector[A]] = {
    val arraypath = path + "." + name
    _member(obj, name).asArray match {
      case None => inputInvalidC("ELEMENT_INVALID", arraypath)
      case Some(values) => values.zipWithIndex.foldLeft(Consequence.success(Vector.empty[A])) { case (acc, (value, index)) =>
        acc.flatMap(result => decode(value, s"$arraypath[$index]").map(result :+ _))
      }
    }
  }
  def _optional[A](obj: JsonObject, name: String, path: String)(decode: (Json, String) => Consequence[A]): Consequence[Option[A]] =
    obj(name) match {
      case None => Consequence.success(None)
      case Some(value) => decode(value, path + "." + name).map(Some(_))
    }
  def _selection(obj: JsonObject, expected: Selection, hassource: Boolean): Consequence[Unit] = {
    val definition = expected.definition
    val provenance = definition.provenance
    for {
      _ <- if (hassource) _expected(obj, "componentId", expected.source.componentId.name, "$", "SELECTION_MISMATCH")
        else Consequence.success(())
      _ <- _expected(obj, "resourceType", expected.source.resourceType, "$", "SELECTION_MISMATCH")
      ref <- _object(_member(obj, "definition"), "$.definition", Vector("screenId", "revision", "mutationRevision"))
      _ <- _expected(ref, "screenId", definition.screenId, "$.definition", "DEFINITION_MISMATCH")
      _ <- _expected(ref, "revision", definition.definitionRevision, "$.definition", "DEFINITION_MISMATCH")
      _ <- _expected(ref, "mutationRevision", definition.mutationRevision, "$.definition", "DEFINITION_MISMATCH")
      prov <- _object(_member(obj, "provenance"), "$.provenance", Vector("producerId", "sourceId", "revision"))
      _ <- _expected(prov, "producerId", provenance.producerId, "$.provenance", "DEFINITION_MISMATCH")
      _ <- _expected(prov, "sourceId", provenance.sourceId, "$.provenance", "DEFINITION_MISMATCH")
      _ <- _expected(prov, "revision", provenance.revision, "$.provenance", "DEFINITION_MISMATCH")
    } yield ()
  }
  def _entity_id(text: String, path: String): Consequence[EntityId] = {
    try {
      EntityId.parse(text).toOption match {
        case Some(value) if value.value == text => Consequence.success(value)
        case _ => inputInvalidC("ID_INVALID", path)
      }
    } catch { case NonFatal(_) => inputInvalidC("ID_INVALID", path) }
  }
  def _decimal(text: String, path: String): Consequence[BigDecimal] = {
    try {
      text match {
        case _decimal_pattern(sign, whole, fraction, exponent) =>
          val digits = Option(fraction).getOrElse("")
          val scale = BigInt(digits.length) - Option(exponent).map(BigInt.apply).getOrElse(BigInt(0))
          if (!scale.isValidInt) inputInvalidC("VALUE_INVALID", path)
          else {
            val decimal = new java.math.BigDecimal(new java.math.BigInteger(sign + whole + digits), scale.toInt)
            if (decimal.toString != text) inputInvalidC("VALUE_INVALID", path)
            else Consequence.success(BigDecimal(decimal))
          }
        case _ => inputInvalidC("VALUE_INVALID", path)
      }
    } catch { case NonFatal(_) => inputInvalidC("VALUE_INVALID", path) }
  }
  def _instant(text: String, path: String): Consequence[Instant] = {
    try {
      val instant = Instant.parse(text)
      if (instant.toString == text) Consequence.success(instant) else inputInvalidC("VALUE_INVALID", path)
    } catch { case NonFatal(_) => inputInvalidC("VALUE_INVALID", path) }
  }
  def _value(json: Json, path: String): Consequence[DisplayValue] = for {
    head <- _object(json, path, Vector("type"), Vector("text", "number", "boolean", "instant"))
    tag <- _string(head, "type", path)
    value <- tag match {
      case "text" => _object(json, path, Vector("type", "text"))
        .flatMap(_string(_, "text", path)).map(DisplayValue.Text.apply)
      case "number" => _object(json, path, Vector("type", "number"))
        .flatMap(_string(_, "number", path)).flatMap(_decimal(_, path + ".number")).map(DisplayValue.Number.apply)
      case "boolean" => _object(json, path, Vector("type", "boolean"))
        .flatMap(obj => _boolean_value(_member(obj, "boolean"), path + ".boolean")).map(DisplayValue.Boolean.apply)
      case "timestamp" => _object(json, path, Vector("type", "instant"))
        .flatMap(_string(_, "instant", path)).flatMap(_instant(_, path + ".instant")).map(DisplayValue.Timestamp.apply)
      case "missing" => _object(json, path, Vector("type")).map(_ => DisplayValue.Missing)
      case _ => inputInvalidC("VALUE_INVALID", path + ".type")
    }
  } yield value
  def _value_valid(value: DisplayValue): Boolean = value != null && (value match {
    case DisplayValue.Text(text) => text != null
    case DisplayValue.Number(number) => number != null
    case DisplayValue.Timestamp(instant) => instant != null
    case DisplayValue.Boolean(_) | DisplayValue.Missing => true
  })
  def _value_json(value: DisplayValue): Json = value match {
    case DisplayValue.Text(text) => _obj("type" -> _text("text"), "text" -> _text(text))
    case DisplayValue.Number(number) => _obj("type" -> _text("number"), "number" -> _text(number.bigDecimal.toString))
    case DisplayValue.Boolean(flag) => _obj("type" -> _text("boolean"), "boolean" -> Json.fromBoolean(flag))
    case DisplayValue.Timestamp(instant) => _obj("type" -> _text("timestamp"), "instant" -> _text(instant.toString))
    case DisplayValue.Missing => _obj("type" -> _text("missing"))
  }
  def _selection_json(selection: Selection, hassource: Boolean): Vector[(String, Json)] = {
    val definition = selection.definition
    val provenance = definition.provenance
    (if (hassource) Vector("componentId" -> _text(selection.source.componentId.name)) else Vector.empty) ++
      Vector("resourceType" -> _text(selection.source.resourceType),
        "definition" -> _obj("screenId" -> _text(definition.screenId), "revision" -> _text(definition.definitionRevision),
          "mutationRevision" -> _text(definition.mutationRevision)),
        "provenance" -> _obj("producerId" -> _text(provenance.producerId), "sourceId" -> _text(provenance.sourceId),
          "revision" -> _text(provenance.revision)))
  }
  def _prefix(schema: String): Vector[(String, Json)] =
    Vector("schema" -> _text(schema), "version" -> Json.fromInt(DisplayMutationProtocol.version))
  def _obj(members: (String, Json)*): Json = Json.fromFields(members)
  def _text(text: String): Json = Json.fromString(text)
  def _render(json: Json): String = json.fold(
    "null", value => if (value) "true" else "false", _.toString, _quote,
    values => values.map(_render).mkString("[", ",", "]"),
    obj => obj.toVector.map { case (key, value) => _quote(key) + ":" + _render(value) }.mkString("{", ",", "}"))
  private def _quote(text: String): String = {
    val builder = new java.lang.StringBuilder().append('"')
    text.foreach { char => char match {
      case '"' => builder.append("\\\"")
      case '\\' => builder.append("\\\\")
      case '\b' => builder.append("\\b")
      case '\f' => builder.append("\\f")
      case '\n' => builder.append("\\n")
      case '\r' => builder.append("\\r")
      case '\t' => builder.append("\\t")
      case value if value < ' ' || value > '~' =>
        val hex = Integer.toHexString(value.toInt)
        builder.append("\\u").append("0" * (4 - hex.length)).append(hex)
      case value => builder.append(value)
    } }
    builder.append('"').toString
  }
}
