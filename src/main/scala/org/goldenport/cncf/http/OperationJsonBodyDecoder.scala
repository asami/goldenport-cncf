package org.goldenport.cncf.http

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.observation.Descriptor
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.goldenport.schema.{Multiplicity, XJson}

/*
 * Foreign JSON syntax admission for the selected Operation's actual parameters.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] object OperationJsonBodyDecoder {
  def decode(
    text: String,
    parameters: Seq[ParameterDefinition],
    operation: Option[CmlOperationDefinition] = None
  ): Consequence[Option[Record]] =
    parse(text).toOption.flatMap(_.asObject) match {
      case None => Consequence.success(None)
      case Some(fields) => _decode_object(fields, parameters, operation).map(Some(_))
    }

  private def _decode_object(
    fields: JsonObject,
    parameters: Seq[ParameterDefinition],
    operation: Option[CmlOperationDefinition]
  ): Consequence[Record] = {
    val ordered = fields.toVector
    val selected = ordered.flatMap { case (key, value) =>
      _raw_parameter(key, parameters).map(parameter => (key, value, parameter))
    }
    val rawvalues = Consequence.zipN(selected.map { case (key, value, parameter) =>
      if (_is_collection(parameter, operation))
        value.asArray match {
          case Some(values) => Consequence.success(key -> (values: Any))
          case None => Consequence.argumentPolicyViolation[(String, Any)](
            key, "operation-json-body", "JSON array for a raw Json collection", "non-array JSON value")
        }
      else
        Consequence.success(key -> (value: Any))
    })
    rawvalues.flatMap { values =>
      // This index is lookup-only: the original JSON field order remains authoritative.
      val rawindex = values.toMap
      val nullpolicy =
        if (operation.exists(_.inputValueKind.trim.equalsIgnoreCase("ENTITY_UPDATE")))
          Consequence.zipN(ordered.collect {
            case (key, value) if value.isNull && !rawindex.contains(key) =>
              Consequence.argumentPolicyViolation[Unit](
                key, "operation-json-body", "explicit-clear-required: use the existing update command carrier", "JSON null").leftMap { conclusion =>
                val observation = conclusion.observation
                conclusion.copy(observation = observation.copy(
                  cause = observation.cause.addFacet(Descriptor.Facet.Reason("explicit-clear-required"))))
              }
          }).map(_ => ())
        else
          Consequence.unit
      nullpolicy.flatMap { _ =>
        val remainder = Json.fromJsonObject(JsonObject.fromIterable(ordered.filterNot(entry => rawindex.contains(entry._1))))
        new RecordDecoder().json(remainder.noSpaces).map { normalized =>
          Record.create(ordered.flatMap { case (key, _) =>
            rawindex.get(key).orElse(normalized.getAny(key)).map(key -> _)
          })
        }
      }
    }
  }

  private def _raw_parameter(
    key: String,
    parameters: Seq[ParameterDefinition]
  ): Option[ParameterDefinition] = {
    val carrier = OperationUpdateDirectiveNormalizer.isUpdateCarrier(key)
    val valuecarrier = OperationUpdateDirective.ExplicitValueAssignment.kinds.exists(kind => key.endsWith(kind.suffix)) ||
      OperationUpdateDirective.ValueOperation.kinds.exists(kind => key.endsWith(kind.suffix))
    if (carrier && !valuecarrier)
      None
    else {
      val basename = OperationUpdateDirectiveNormalizer.parameterName(key)
      parameters.find(parameter => (parameter.datatype eq XJson) &&
        parameter.names.exists(name => NamingConventions.equivalentByNormalized(name, basename)))
    }
  }

  private def _is_collection(
    parameter: ParameterDefinition,
    operation: Option[CmlOperationDefinition]
  ): Boolean =
    operation.flatMap(_.parameters.find(field =>
      parameter.names.exists(name => NamingConventions.equivalentByNormalized(name, field.name))))
      .flatMap(_.update)
      .map(update => _is_source_collection(update.sourceMultiplicity))
      .getOrElse(_is_protocol_collection(parameter.multiplicity))

  private def _is_source_collection(multiplicity: String): Boolean = {
    val bounded = """([0-9]+)\.\.([0-9]+|\*)""".r
    multiplicity.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "*" | "+" | "zeromore" | "zero-more" | "zero_more" | "onemore" | "one-more" | "one_more" => true
      case bounded(_, "*") => true
      case bounded(from, to) => from.toIntOption.exists(_ > 1) || to.toIntOption.exists(_ > 1)
      case _ => false
    }
  }

  private def _is_protocol_collection(multiplicity: Multiplicity): Boolean =
    multiplicity match {
      case Multiplicity.One | Multiplicity.ZeroOne => false
      case Multiplicity.ZeroMore | Multiplicity.OneMore => true
      case Multiplicity.Range(from, to) => from > 1 || to > 1
      case Multiplicity.Ranges(ranges) => ranges.exists(_.toList.exists(range => range.from > 1 || range.to > 1))
    }
}
