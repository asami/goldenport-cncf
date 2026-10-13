package org.goldenport.cncf.entity

import scala.util.control.NonFatal
import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor

/*
 * Explicit physical scalar selection and stateless JSON bridges.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityStoreScalarRepresentation {
  case Text, Boolean, Number
}

object EntityStoreScalarRepresentation {
  private[entity] def _identity(representation: EntityStoreScalarRepresentation): String = {
    representation match {
      case EntityStoreScalarRepresentation.Text => "text"
      case EntityStoreScalarRepresentation.Boolean => "boolean"
      case EntityStoreScalarRepresentation.Number => "number"
      case null => "<missing>"
    }
  }

  private[entity] def _check_json(
    representation: EntityStoreScalarRepresentation,
    value: Json,
    identity: String,
    stage: String
  ): Consequence[Json] = {
    if (representation == null) {
      _invalid(identity, stage, "missing-codec-binding")
    } else if (value == null) {
      _invalid(identity, stage, "wrong-structured-shape")
    } else {
      representation match {
        case EntityStoreScalarRepresentation.Text if value.isString => Consequence.success(value)
        case EntityStoreScalarRepresentation.Boolean if value.isBoolean => Consequence.success(value)
        case EntityStoreScalarRepresentation.Number if value.isNumber =>
          _exact_number(value, identity, stage).map(_ => value)
        case _ => _invalid(identity, stage, "wrong-structured-shape")
      }
    }
  }

  private[entity] def _to_physical(
    representation: EntityStoreScalarRepresentation,
    value: Json,
    identity: String
  ): Consequence[Any] = {
    _check_json(representation, value, identity, "encode").flatMap { json =>
      representation match {
        case EntityStoreScalarRepresentation.Text => Consequence.success(json.asString.get)
        case EntityStoreScalarRepresentation.Boolean => Consequence.success(json.asBoolean.get)
        case EntityStoreScalarRepresentation.Number => _exact_number(json, identity, "encode").map(x => x: Any)
      }
    }
  }

  private[entity] def _from_physical(
    representation: EntityStoreScalarRepresentation,
    value: Any,
    identity: String
  ): Consequence[Json] = {
    representation match {
      case null => _invalid(identity, "decode", "missing-codec-binding")
      case EntityStoreScalarRepresentation.Text => value match {
        case text: String => Consequence.success(Json.fromString(text))
        case _ => _invalid(identity, "decode", "wrong-physical-value")
      }
      case EntityStoreScalarRepresentation.Number => value match {
        case text: String => parser.parse(text) match {
          case Right(json) if json.isNumber => _check_json(representation, json, identity, "decode")
          case _ => _invalid(identity, "decode", "wrong-physical-value")
        }
        case _ => _number(value, identity)
      }
      case EntityStoreScalarRepresentation.Boolean => value match {
        case boolean: scala.Boolean => Consequence.success(Json.fromBoolean(boolean))
        case "0" => Consequence.success(Json.False)
        case "1" => Consequence.success(Json.True)
        case _: String => _invalid(identity, "decode", "wrong-physical-value")
        case _ =>
          _number(value, identity).flatMap(_exact_number(_, identity, "decode")).flatMap { number =>
            if (number == BigDecimal(0)) Consequence.success(Json.False)
            else if (number == BigDecimal(1)) Consequence.success(Json.True)
            else _invalid(identity, "decode", "wrong-physical-value")
          }.recoverWith { _ => _invalid(identity, "decode", "wrong-physical-value") }
      }
    }
  }

  private def _number(value: Any, identity: String): Consequence[Json] = {
    value match {
      case number: Byte => Consequence.success(Json.fromInt(number.toInt))
      case number: Short => Consequence.success(Json.fromInt(number.toInt))
      case number: Int => Consequence.success(Json.fromInt(number))
      case number: Long => Consequence.success(Json.fromLong(number))
      case number: BigInt => Consequence.success(Json.fromBigInt(number))
      case number: BigDecimal => Consequence.success(Json.fromBigDecimal(number))
      case number: java.math.BigInteger => Consequence.success(Json.fromBigInt(BigInt(number)))
      case number: java.math.BigDecimal => Consequence.success(Json.fromBigDecimal(BigDecimal(number)))
      case number: Float => Json.fromFloat(number) match {
        case Some(json) => Consequence.success(json)
        case None => _invalid(identity, "decode", "unsupported-external-value")
      }
      case number: Double => Json.fromDouble(number) match {
        case Some(json) => Consequence.success(json)
        case None => _invalid(identity, "decode", "unsupported-external-value")
      }
      case _ => _invalid(identity, "decode", "wrong-physical-value")
    }
  }

  private def _exact_number(value: Json, identity: String, stage: String): Consequence[BigDecimal] = {
    try {
      value.asNumber.flatMap(_.toBigDecimal) match {
        case Some(number) => Consequence.success(number)
        case None => _invalid(identity, stage, "unsupported-external-value")
      }
    } catch {
      case NonFatal(_) => _invalid(identity, stage, "unsupported-external-value")
    }
  }

  private def _invalid[A](identity: String, stage: String, reason: String): Consequence[A] = {
    Consequence.valueInvalid[A](s"$identity $stage: $reason").leftMap { conclusion =>
      conclusion.causes.map { cause =>
        val observation = cause.observation
        cause.copy(observation = observation.copy(
          cause = observation.cause.addFacet(Descriptor.Facet.Reason(reason))))
      }.reduce(_ ++ _)
    }
  }
}
