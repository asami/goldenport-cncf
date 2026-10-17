package org.goldenport.cncf.display

import java.nio.charset.StandardCharsets
import java.time.Instant
import io.circe.{Json, JsonObject, Printer}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import scala.util.control.NonFatal

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
/** Closed supported families with bounded, discarded optional extensions. */
object PresentationContextCodec {
  final case class Diagnostic(code: String, path: String, reason: String)
  final class CodecException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  private final case class Envelope(name: String, supported: Boolean, data: JsonObject, path: String)
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _printer = Printer.noSpaces
  private val _name_pattern = "[a-z][a-z0-9]*(?:-[a-z0-9]+)*(?:\\.[a-z][a-z0-9]*(?:-[a-z0-9]+)*)+".r
  private val _version_pattern = "(-?)([0-9]+)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?".r
  private val _limit = 8192

  def encodeC(context: PresentationContext): Consequence[String] = encodeJsonC(context).map(_printer.print)

  def encodeJsonC(context: PresentationContext): Consequence[Json] = {
    if (context == null) _failure("INPUT_INVALID", "context")
    else {
      val families = context.locale.toVector.map(value => _family("cncf.locale",
        Json.obj("languageTag" -> Json.fromString(value)))) ++
        context.variant.toVector.map(value => _family("cncf.presentation", Json.obj("variant" -> Json.fromString(value)))) ++
        context.flow.toVector.map(value => _family("cncf.display-flow", Json.obj(
          "displayInstance" -> Json.fromString(value.displayInstance),
          "correlation" -> Json.fromString(value.correlation), "expiresAt" -> Json.fromString(value.expiresAt.toString))))
      Consequence.success(Json.obj("version" -> Json.fromInt(1), "families" -> Json.fromValues(families)))
    }
  }

  def decodeC(json: String): Consequence[PresentationContext] = {
    if (json == null) _failure("INPUT_INVALID", "json")
    else _raw_bounds(json).flatMap { _ =>
      val parsed = try {
        _parser.parse(json).fold(_ => _failure[Json]("JSON_INVALID", "$"), Consequence.success)
      } catch { case NonFatal(_) => _failure[Json]("JSON_INVALID", "$") }
      parsed.flatMap(decodeJsonC)
    }
  }

  def decodeJsonC(json: Json): Consequence[PresentationContext] = {
    if (json == null) _failure("INPUT_INVALID", "json")
    else _json_bounds(json).flatMap { _ =>
      for {
        root <- _object(json, "$", Vector("version", "families"))
        version <- _version(root, "$", false)
        _ <- if (version) Consequence.success(()) else _failure("VERSION_INCOMPATIBLE", "$.version")
        families <- root("families").flatMap(_.asArray).map(Consequence.success)
          .getOrElse(_failure("MEMBER_INVALID", "$.families"))
        _ <- if (families.size <= 8) Consequence.success(()) else _failure("LIMIT_EXCEEDED", "$.families")
        envelopes <- families.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Envelope])) {
          case (acc, (entry, index)) => acc.flatMap { values =>
            _envelope(entry, s"$$.families[$index]").flatMap { envelope =>
              if (values.exists(_.name == envelope.name)) _failure("DUPLICATE_FAMILY", envelope.path + ".name")
              else Consequence.success(values :+ envelope)
            }
          }
        }
        empty <- PresentationContext.createC()
        context <- envelopes.foldLeft(Consequence.success(empty)) { (acc, envelope) =>
          acc.flatMap { current => _supported(envelope).flatMap { next =>
            PresentationContext.createC(current.locale.orElse(next.locale), current.variant.orElse(next.variant),
              current.flow.orElse(next.flow))
          } }
        }
      } yield context
    }
  }

  private def _family(name: String, data: Json): Json =
    Json.obj("name" -> Json.fromString(name), "version" -> Json.fromInt(1), "data" -> data)

  private def _object(json: Json, path: String, members: Vector[String]): Consequence[JsonObject] = {
    Option(json).flatMap(_.asObject) match {
      case Some(value) if value.size == members.size && members.forall(value.contains) => Consequence.success(value)
      case _ => _failure("MEMBER_INVALID", path)
    }
  }

  private def _string(obj: JsonObject, name: String, path: String): Consequence[String] =
    obj(name).flatMap(value => Option(value).flatMap(_.asString)).map(Consequence.success)
      .getOrElse(_failure("MEMBER_INVALID", path + "." + name))

  private def _version(obj: JsonObject, path: String, positive: Boolean = true): Consequence[Boolean] = {
    try {
      obj("version").flatMap(_.asNumber).map(_.toString) match {
        case Some(_version_pattern(sign, whole, fraction, exponent)) =>
          val digits = whole + Option(fraction).getOrElse("")
          val significant = digits.dropWhile(_ == '0')
          val scale = BigInt(Option(fraction).fold(0)(_.length)) - Option(exponent).map(BigInt.apply).getOrElse(BigInt(0))
          val zeros = significant.reverseIterator.takeWhile(_ == '0').length
          if ((significant.nonEmpty && scale > zeros) || (positive && (sign.nonEmpty || significant.isEmpty))) {
            _failure("MEMBER_INVALID", path + ".version")
          } else Consequence.success(sign.isEmpty && significant.dropRight(zeros) == "1" && scale == zeros)
        case _ => _failure("MEMBER_INVALID", path + ".version")
      }
    } catch { case NonFatal(_) => _failure("MEMBER_INVALID", path + ".version") }
  }

  private def _envelope(json: Json, path: String): Consequence[Envelope] = {
    for {
      obj <- _object(json, path, Vector("name", "version", "data"))
      name <- _string(obj, "name", path)
      _ <- if (name.length <= 64 && _name_pattern.matches(name)) Consequence.success(())
        else _failure("NAME_INVALID", path + ".name")
      version <- _version(obj, path)
      data <- obj("data").flatMap(_.asObject).map(Consequence.success)
        .getOrElse(_failure("MEMBER_INVALID", path + ".data"))
    } yield Envelope(name, version, data, path)
  }

  private def _supported(envelope: Envelope): Consequence[PresentationContext] = {
    val path = envelope.path + ".data"
    val json = Json.fromJsonObject(envelope.data)
    if (!envelope.supported) PresentationContext.createC()
    else envelope.name match {
      case "cncf.locale" => for {
        obj <- _object(json, path, Vector("languageTag"))
        text <- _string(obj, "languageTag", path)
        context <- _value(PresentationContext.createC(locale = Some(text)), path + ".languageTag")
      } yield context
      case "cncf.presentation" => for {
        obj <- _object(json, path, Vector("variant"))
        text <- _string(obj, "variant", path)
        context <- _value(PresentationContext.createC(variant = Some(text)), path + ".variant")
      } yield context
      case "cncf.display-flow" => for {
        obj <- _object(json, path, Vector("displayInstance", "correlation", "expiresAt"))
        instance <- _string(obj, "displayInstance", path)
        correlation <- _string(obj, "correlation", path)
        expirytext <- _string(obj, "expiresAt", path)
        expiry <- _instant(expirytext, path + ".expiresAt")
        flow <- _flow(instance, correlation, expiry, path)
        context <- PresentationContext.createC(flow = Some(flow))
      } yield context
      case _ => PresentationContext.createC()
    }
  }

  private def _value[A](result: Consequence[A], path: String): Consequence[A] =
    result.toOption.map(Consequence.success).getOrElse(_failure("VALUE_INVALID", path))

  private def _flow(instance: String, correlation: String, expiry: Instant, path: String): Consequence[PresentationContext.Flow] = {
    PresentationContext.Flow.createC(instance, correlation, expiry) match {
      case Consequence.Success(value) => Consequence.success(value)
      case failure: Consequence.Failure[?] =>
        val member = failure.conclusion.observation.cause.getException.collect {
          case value: PresentationContext.ValidationException => value.diagnostic.path
        }.getOrElse("displayInstance")
        _failure("VALUE_INVALID", path + "." + member)
    }
  }

  private def _instant(text: String, path: String): Consequence[Instant] = {
    try {
      val instant = Instant.parse(text)
      if (instant.toString == text) Consequence.success(instant) else _failure("VALUE_INVALID", path)
    } catch { case NonFatal(_) => _failure("VALUE_INVALID", path) }
  }

  // Scan containers before Jawn can allocate a tree; quoted or escaped brackets are inert.
  private def _raw_bounds(text: String): Consequence[Unit] = {
    if (text.length > _limit || text.getBytes(StandardCharsets.UTF_8).length > _limit) _failure("LIMIT_EXCEEDED", "$")
    else {
      var depth = 0
      var quoted = false
      var escaped = false
      var index = 0
      while (index < text.length) {
        val char = text.charAt(index)
        if (quoted) {
          if (escaped) escaped = false
          else if (char == '\\') escaped = true
          else if (char == '"') quoted = false
        } else if (char == '"') quoted = true
        else if (char == '{' || char == '[') {
          depth += 1
          if (depth > 8) return _failure("LIMIT_EXCEEDED", "$")
        } else if (char == '}' || char == ']') depth -= 1
        index += 1
      }
      Consequence.success(())
    }
  }

  // Iterative accounting avoids rendering or recursively traversing an untrusted Json tree.
  private def _json_bounds(json: Json): Consequence[Unit] = {
    var pending = List((json, 0))
    var bytes = 0L
    while (pending.nonEmpty && bytes <= _limit) {
      val (value, parentdepth) = pending.head
      pending = pending.tail
      if (parentdepth > 0) bytes -= 1
      if (value == null) return _failure("MEMBER_INVALID", "$")
      value.asObject match {
        case Some(obj) =>
          val depth = parentdepth + 1
          if (depth > 8) return _failure("LIMIT_EXCEEDED", "$")
          bytes += 2L + math.max(0, obj.size - 1)
          val entries = obj.toIterable.iterator
          while (entries.hasNext && bytes <= _limit) {
            val (key, child) = entries.next()
            if (key == null) return _failure("MEMBER_INVALID", "$")
            bytes += _quoted_bytes(key) + 1
            // Each child requires at least one byte, bounding the pending work too.
            bytes += 1
            pending = (child, depth) :: pending
          }
        case None => value.asArray match {
          case Some(values) =>
            val depth = parentdepth + 1
            if (depth > 8) return _failure("LIMIT_EXCEEDED", "$")
            bytes += 2L + math.max(0, values.size - 1)
            val entries = values.iterator
            while (entries.hasNext && bytes <= _limit) {
              bytes += 1
              pending = (entries.next(), depth) :: pending
            }
          case None =>
            bytes += value.asString.map(_quoted_bytes).getOrElse(value.noSpaces.length.toLong)
        }
      }
    }
    if (bytes > _limit) _failure("LIMIT_EXCEEDED", "$") else Consequence.success(())
  }

  private def _quoted_bytes(text: String): Long = {
    var bytes = 2L
    var index = 0
    while (index < text.length && bytes <= _limit) {
      val char = text.charAt(index)
      if (char == '"' || char == '\\' || char == '\b' || char == '\f' || char == '\n' || char == '\r' || char == '\t') bytes += 2
      else if (char < ' ') bytes += 6
      else if (char <= 0x7f) bytes += 1
      else if (char <= 0x7ff) bytes += 2
      else if (Character.isHighSurrogate(char) && index + 1 < text.length && Character.isLowSurrogate(text.charAt(index + 1))) {
        bytes += 4
        index += 1
      } else if (Character.isSurrogate(char)) bytes += 6
      else bytes += 3
      index += 1
    }
    bytes
  }

  private def _failure[A](code: String, path: String): Consequence[A] = {
    val reason = code match {
      case "INPUT_INVALID" => "expected nonnull input"
      case "JSON_INVALID" => "expected strict JSON without duplicate members"
      case "MEMBER_INVALID" => "expected closed members and declared nonnull primitive"
      case "VERSION_INCOMPATIBLE" => "expected supported context version"
      case "LIMIT_EXCEEDED" => "expected bounded context bytes depth and family count"
      case "NAME_INVALID" => "expected bounded namespaced lower-case ASCII family name"
      case "DUPLICATE_FAMILY" => "expected unique family names before optional filtering"
      case "VALUE_INVALID" => "expected canonical bounded presentation value"
    }
    Consequence.fail(Taxonomy.argumentInvalid, new CodecException(Diagnostic("DSP96_CONTEXT_" + code, path, reason)))
  }
}
