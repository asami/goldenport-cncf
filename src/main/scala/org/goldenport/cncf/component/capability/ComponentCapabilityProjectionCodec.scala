package org.goldenport.cncf.component.capability

import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityReference.Source
import ComponentCapabilityProjection.Projection
import ComponentCapabilityAdmissionScope.{Diagnostic, DiagnosticKind}

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityProjectionCodec {
  private val _strict_parser = JawnParser(allowDuplicateKeys = false)

  def decode(json: String): Either[Diagnostic, Projection] = {
    for {
      _ <- _nonnull(json, "/")
      parsed <- _strict_parser.parse(json).left.map { _ =>
        _diagnostic(DiagnosticKind.Malformed, "Invalid JSON or duplicate object key.", "/")
      }
      projection <- ComponentCapabilityProjectionJsonDecodingPart._decode(parsed)
      valid <- validate(projection)
    } yield valid
  }

  def validate(projection: Projection): Either[Diagnostic, Projection] = {
    ComponentCapabilityProjectionValidationPart._projection(projection).map(_ => projection)
  }

  def encode(projection: Projection): Either[Diagnostic, String] = {
    validate(projection).map { valid =>
      ComponentCapabilityProjectionJsonEncodingPart._encode(valid).noSpaces
    }
  }

  private[capability] def _diagnostic(
    kind: DiagnosticKind,
    detail: String,
    location: String
  ): Diagnostic = {
    Diagnostic(kind, detail, None, None, None, location)
  }

  private[capability] def _malformed(location: String, detail: String): Diagnostic = {
    _diagnostic(DiagnosticKind.Malformed, detail, location)
  }

  private[capability] def _child(location: String, field: String): String = {
    val escaped = field.replace("~", "~0").replace("/", "~1")
    if (location == "/") s"/$escaped" else s"$location/$escaped"
  }

  private[capability] def _nonnull[A](value: A, location: String): Either[Diagnostic, A] = {
    Option(value).toRight(_malformed(location, "A nonnull value is required."))
  }

  private[capability] def _sequence[A](values: Vector[Either[Diagnostic, A]]): Either[Diagnostic, Vector[A]] = {
    values.foldLeft[Either[Diagnostic, Vector[A]]](Right(Vector.empty)) { (acc, item) =>
      for {
        previous <- acc
        value <- item
      } yield previous :+ value
    }
  }

  private[capability] def _context[A](
    result: Either[Diagnostic, A],
    component: Option[ComponentId],
    source: Option[Source]
  ): Either[Diagnostic, A] = {
    result.left.map { error =>
      error.copy(component = error.component.orElse(component), source = error.source.orElse(source))
    }
  }

  private[capability] def _object(
    json: Json,
    fields: Vector[String],
    location: String
  ): Either[Diagnostic, JsonObject] = {
    for {
      obj <- json.asObject.toRight(_malformed(location, "A JSON object is required."))
      _ <- fields.find(field => !obj.contains(field)) match {
        case Some(field) => Left(_malformed(_child(location, field), "A mandatory field is missing."))
        case None => Right(())
      }
      _ <- obj.keys.find(field => !fields.contains(field)) match {
        case Some(field) => Left(_malformed(_child(location, field), "An unknown field is not allowed."))
        case None => Right(())
      }
    } yield obj
  }

  private[capability] def _read[A](
    obj: JsonObject,
    field: String,
    location: String
  )(decoder: (Json, String) => Either[Diagnostic, A]): Either[Diagnostic, A] = {
    val path = _child(location, field)
    obj(field).toRight(_malformed(path, "A mandatory field is missing.")).flatMap(decoder(_, path))
  }

  private[capability] def _string(json: Json, location: String): Either[Diagnostic, String] = {
    json.asString.toRight(_malformed(location, "A JSON string is required.")).flatMap { value =>
      ComponentCapabilityProjectionValidationPart._text(value, location).map(_ => value)
    }
  }

  private[capability] def _integer(json: Json, location: String): Either[Diagnostic, Int] = {
    json.asNumber.filter(_.toString.matches("-?(0|[1-9][0-9]*)")).flatMap(_.toInt)
      .toRight(_malformed(location, "An integer in the supported numeric range is required."))
  }

  private[capability] def _vector[A](
    json: Json,
    location: String
  )(decoder: (Json, String) => Either[Diagnostic, A]): Either[Diagnostic, Vector[A]] = {
    json.asArray.toRight(_malformed(location, "A JSON array is required.")).flatMap { values =>
      _sequence(values.zipWithIndex.map { (value, index) => decoder(value, _child(location, index.toString)) })
    }
  }

  private[capability] def _tag[A](
    json: Json,
    location: String,
    candidates: Vector[(String, A)]
  ): Either[Diagnostic, A] = {
    _string(json, location).flatMap { tag =>
      candidates.find(_._1 == tag).map(_._2).toRight(
        _diagnostic(DiagnosticKind.Unsupported, "This closed contract tag is unsupported.", location)
      )
    }
  }
}
