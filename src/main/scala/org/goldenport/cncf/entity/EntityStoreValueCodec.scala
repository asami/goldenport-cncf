package org.goldenport.cncf.entity

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import io.circe.{Decoder, Encoder, Json, parser}
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, XJson, XSemiStructuredData}

/*
 * Pure, explicitly selected logical-value / JSON-text storage conversion.
 *
 * @since   Oct.  5, 2026
 *  version Oct.  5, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait EntityStoreValueCodec[A] {
  def identity: String
  def encode(value: Any): Consequence[String]
  def decode(value: Any): Consequence[A]

  private[entity] def _validate_binding: Consequence[Unit]
  private[entity] def _to_json(value: Any): Consequence[Json]
  private[entity] def _from_json(value: Json): Consequence[A]
}

object EntityStoreValueCodec {
  val string: EntityStoreValueCodec[String] = new EntityStoreValueCodec[String] {
    val identity: String = "string"
    private[entity] def _validate_binding: Consequence[Unit] = Consequence.success(())

    def encode(value: Any): Consequence[String] = _string(value, "encode")
    def decode(value: Any): Consequence[String] = _string(value, "decode")

    private[entity] def _to_json(value: Any): Consequence[Json] = {
      _string(value, "encode").map(Json.fromString)
    }

    private[entity] def _from_json(value: Json): Consequence[String] = {
      if (value == null) {
        _invalid(identity, "decode", "native null", "wrong-structured-shape")
      } else {
        value.asString match {
          case Some(text) => Consequence.success(text)
          case None => _invalid(identity, "decode", "expected JSON string", "wrong-structured-shape")
        }
      }
    }

    private def _string(value: Any, stage: String): Consequence[String] = {
      value match {
        case text: String => Consequence.success(text)
        case _ => _invalid(identity, stage, "expected String",
          if (stage == "encode") "wrong-logical-value" else "wrong-physical-value")
      }
    }
  }

  val json: EntityStoreValueCodec[Json] = new JsonTextCodec[Json] {
    val identity: String = "json"

    private[entity] def _to_json(value: Any): Consequence[Json] = {
      XJson.normalizer.normalize(value).recoverWith { _ =>
        _invalid(identity, "encode", "expected Json", "wrong-logical-value")
      }
    }

    private[entity] def _from_json(value: Json): Consequence[Json] = {
      XJson.normalizer.normalize(value).recoverWith { _ =>
        _invalid(identity, "decode", "expected Json", "wrong-structured-shape")
      }
    }
  }

  val semiStructuredData: EntityStoreValueCodec[Record] = new JsonTextCodec[Record] {
    val identity: String = "semiStructuredData"

    private[entity] def _to_json(value: Any): Consequence[Json] = {
      XSemiStructuredData.normalizer.normalize(value)
        .flatMap(XSemiStructuredData.toJson)
        .recoverWith { _ =>
          _invalid(identity, "encode", "invalid canonical object", "wrong-logical-value")
        }
    }

    private[entity] def _from_json(value: Json): Consequence[Record] = {
      XSemiStructuredData.fromJson(value).recoverWith { _ =>
        _invalid(identity, "decode", "invalid canonical object", "wrong-structured-shape")
      }
    }
  }

  def application[A](codecIdentity: String)(using
    classTag: ClassTag[A],
    encoder: Encoder[A],
    decoder: Decoder[A]
  ): EntityStoreValueCodec[A] = {
    new ApplicationCodec[A](codecIdentity, classTag, encoder, decoder)
  }

  def array[A](element: EntityStoreValueCodec[A]): EntityStoreValueCodec[Vector[A]] = {
    new ArrayCodec[A](element)
  }

  private[entity] def _native_scalar[A](
    datatype: DataType,
    representation: EntityStoreScalarRepresentation,
    codecidentity: String
  )(using
    classtag: ClassTag[A],
    encoder: Encoder[A],
    decoder: Decoder[A]
  ): EntityStoreValueCodec[A] = {
    new NativeScalarCodec[A](datatype, representation,
      new ApplicationCodec[A](codecidentity, classtag, encoder, decoder, "wrong-structured-shape"),
      codecidentity)
  }

  private sealed trait JsonTextCodec[A] extends EntityStoreValueCodec[A] {
    private[entity] def _binding: Consequence[Unit] = Consequence.success(())
    final private[entity] def _validate_binding: Consequence[Unit] = _binding

    final def encode(value: Any): Consequence[String] = {
      _binding.flatMap { _ => _to_json(value).map(_.noSpaces) }
    }

    final def decode(value: Any): Consequence[A] = {
      _binding.flatMap { _ => _parse_json(value, identity).flatMap(_from_json) }
    }
  }

  private final class ApplicationCodec[A](
    codecidentity: String,
    classtag: ClassTag[A],
    encoder: Encoder[A],
    decoder: Decoder[A],
    nullrootreason: String = "application-codec-failure"
  ) extends JsonTextCodec[A] {
    val identity: String = s"application(${Option(codecidentity).getOrElse("<missing>")})"

    override private[entity] def _binding: Consequence[Unit] = {
      if (codecidentity == null || codecidentity.trim.isEmpty) {
        _invalid(identity, "binding", "missing codec identity", "missing-codec-binding")
      } else if (classtag == null || encoder == null || decoder == null) {
        _invalid(identity, "binding", "missing ClassTag, Encoder or Decoder", "missing-codec-binding")
      } else {
        Consequence.success(())
      }
    }

    private[entity] def _to_json(value: Any): Consequence[Json] = {
      _binding.flatMap { _ =>
        try {
          _runtime_value(value, "encode").flatMap { actual =>
            val encoded = encoder(actual)
            if (encoded == null) {
              _invalid(identity, "encode", "application codec returned null root", nullrootreason)
            } else {
              Consequence.success(encoded)
            }
          }
        } catch {
          case NonFatal(_) => _invalid(identity, "encode", "application codec threw", "application-codec-failure")
        }
      }
    }

    private[entity] def _from_json(value: Json): Consequence[A] = {
      _binding.flatMap { _ =>
        if (value == null) {
          _invalid(identity, "decode", "null application root", "application-codec-failure")
        } else {
          try {
            decoder.decodeJson(value) match {
              case Right(actual) => _runtime_value(actual, "decode")
              case Left(_) => _invalid(identity, "decode", "application codec rejected JSON", "application-codec-failure")
            }
          } catch {
            case NonFatal(_) => _invalid(identity, "decode", "application codec threw", "application-codec-failure")
          }
        }
      }
    }

    private def _runtime_value(value: Any, stage: String): Consequence[A] = {
      if (value == null) {
        _invalid(identity, stage, "null application value",
          if (stage == "decode") "application-codec-failure" else "wrong-logical-value")
      } else {
        classtag.unapply(value) match {
          case Some(actual) => Consequence.success(actual)
          case None => _invalid(identity, stage, "incompatible application class",
            if (stage == "decode") "application-codec-failure" else "wrong-logical-value")
        }
      }
    }
  }

  private final class NativeScalarCodec[A](
    datatype: DataType,
    representation: EntityStoreScalarRepresentation,
    application: ApplicationCodec[A],
    codecidentity: String
  ) extends JsonTextCodec[A] {
    val identity: String = s"nativeScalar(${EntityStoreScalarRepresentation._identity(representation)},${Option(codecidentity).getOrElse("<missing>")})"

    override private[entity] def _binding: Consequence[Unit] = {
      if (datatype == null) {
        _invalid(identity, "binding", "missing logical datatype", "missing-mapping")
      } else if (representation == null) {
        _invalid(identity, "binding", "missing scalar representation", "missing-codec-binding")
      } else {
        application._validate_binding
      }
    }

    private[entity] def _to_json(value: Any): Consequence[Json] = {
      _binding.flatMap { _ =>
        application._to_json(value).flatMap { json =>
          EntityStoreScalarRepresentation._check_json(datatype, representation, json, identity, "encode")
        }
      }
    }

    private[entity] def _from_json(value: Json): Consequence[A] = {
      _binding.flatMap { _ =>
        EntityStoreScalarRepresentation._check_json(datatype, representation, value, identity, "decode")
          .flatMap(application._from_json)
      }
    }
  }

  private final class ArrayCodec[A](element: EntityStoreValueCodec[A])
      extends JsonTextCodec[Vector[A]] {
    val identity: String = {
      if (element == null) "array(<missing>)" else s"array(${element.identity})"
    }

    override private[entity] def _binding: Consequence[Unit] = {
      if (element == null) {
        _invalid(identity, "binding", "missing element codec", "missing-codec-binding")
      } else {
        element._validate_binding
      }
    }

    private[entity] def _to_json(value: Any): Consequence[Json] = {
      _binding.flatMap { _ =>
        value match {
          case values: scala.collection.Seq[?] =>
            val encoded = values.toVector.zipWithIndex.map { case (member, index) =>
              _indexed(element._to_json(member), identity, "encode", index)
            }
            Consequence.zipN(encoded).map(Json.fromValues)
          case _ => _invalid(identity, "encode", "expected Scala Seq", "wrong-logical-value")
        }
      }
    }

    private[entity] def _from_json(value: Json): Consequence[Vector[A]] = {
      _binding.flatMap { _ =>
        if (value == null) {
          _invalid(identity, "decode", "native null", "wrong-structured-shape")
        } else {
          value.asArray match {
            case Some(values) =>
              val decoded = values.zipWithIndex.map { case (member, index) =>
                _indexed(element._from_json(member), identity, "decode", index)
              }
              Consequence.zipN(decoded).map(_.toVector)
            case None => _invalid(identity, "decode", "expected JSON array", "wrong-structured-shape")
          }
        }
      }
    }
  }

  private def _parse_json(value: Any, identity: String): Consequence[Json] = {
    value match {
      case text: String =>
        parser.parse(text) match {
          case Right(parsed) => Consequence.success(parsed)
          case Left(_) => _reason(
            Consequence.valueFormatError(s"$identity decode: malformed JSON text"),
            "malformed-stored-json")
        }
      case _ => _invalid(identity, "decode", "expected physical String", "wrong-physical-value")
    }
  }

  private def _indexed[A](
    result: Consequence[A],
    identity: String,
    stage: String,
    index: Int
  ): Consequence[A] = {
    result.leftMap { conclusion =>
      conclusion.causes.map { cause =>
        val observation = cause.observation
        val descriptor = observation.cause.descriptor
        val facets = descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.Message]) :+
          Descriptor.Facet.Message(s"$identity $stage index[$index]: ${cause.displayMessage}") :+
          Descriptor.Facet.FieldPath(s"index[$index]")
        cause.copy(observation = observation.copy(
          cause = observation.cause.copy(descriptor = descriptor.copy(facets = facets))))
      }.reduce(_ ++ _)
    }
  }

  private def _invalid[A](identity: String, stage: String, detail: String, reason: String): Consequence[A] = {
    _reason(Consequence.valueInvalid(s"$identity $stage: $detail"), reason)
  }

  private def _reason[A](result: Consequence[A], reason: String): Consequence[A] = {
    result.leftMap { conclusion =>
      conclusion.causes.map { cause =>
        val observation = cause.observation
        cause.copy(observation = observation.copy(
          cause = observation.cause.addFacet(Descriptor.Facet.Reason(reason))))
      }.reduce(_ ++ _)
    }
  }
}
