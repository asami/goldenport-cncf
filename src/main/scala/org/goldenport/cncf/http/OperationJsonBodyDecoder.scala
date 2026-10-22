package org.goldenport.cncf.http

import io.circe.{Json, JsonObject}
import io.circe.jawn.CirceSupportParser
import org.typelevel.jawn.{Facade, FContext, Parser}
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
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] object OperationJsonBodyDecoder {
  def decode(
    text: String,
    parameters: Seq[ParameterDefinition],
    operation: Option[CmlOperationDefinition] = None
  ): Consequence[Option[Record]] =
    _parse_object(text) match {
      case None => Consequence.success(None)
      case Some(parsed) if parsed.occurrences.map(_._1).distinct.size == parsed.occurrences.size =>
        _decode_object(parsed.fields, parameters, operation).map(Some(_))
      case Some(parsed) =>
        Consequence.zipN(parsed.occurrences.map { case (key, value) =>
          _decode_object(JsonObject.singleton(key, value), parameters, operation)
        }).map(records => Some(Record(records.toVector.flatMap(_.fields))))
    }

  private case class ParsedObject(fields: JsonObject, occurrences: Vector[(String, Json)])

  private def _parse_object(text: String): Option[ParsedObject] = {
    var rootfields = Vector.empty[(String, Json)]
    given Facade[Json] = new RootOccurrenceFacade(fields => rootfields = fields)
    Parser.parseFromString[Json](text).toOption.flatMap(_.asObject.map(fields => ParsedObject(fields, rootfields)))
  }

  private final class RootOccurrenceFacade(capture: Vector[(String, Json)] => Unit) extends Facade[Json] {
    private val _delegate = new CirceSupportParser(None, true).facade
    private var _object_started = false

    override def singleContext(index: Int): FContext[Json] = _delegate.singleContext(index)
    override def arrayContext(index: Int): FContext[Json] = _delegate.arrayContext(index)
    override def objectContext(index: Int): FContext[Json] = {
      val context = _delegate.objectContext(index)
      if (_object_started) context
      else {
        _object_started = true
        new RootObjectContext(context, _delegate, capture)
      }
    }
    override def jnull(index: Int): Json = _delegate.jnull(index)
    override def jfalse(index: Int): Json = _delegate.jfalse(index)
    override def jtrue(index: Int): Json = _delegate.jtrue(index)
    override def jnum(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Json =
      _delegate.jnum(s, decIndex, expIndex, index)
    override def jstring(s: CharSequence, index: Int): Json = _delegate.jstring(s, index)
  }

  private final class RootObjectContext(delegate: FContext[Json], facade: Facade[Json],
      capture: Vector[(String, Json)] => Unit) extends FContext[Json] {
    private var _pending_key = Option.empty[String]
    private var _fields = Vector.empty[(String, Json)]

    override def add(s: CharSequence, index: Int): Unit = {
      delegate.add(s, index)
      _pending_key match {
        case None => _pending_key = Some(s.toString)
        case Some(key) =>
          _fields :+= key -> facade.jstring(s, index)
          _pending_key = None
      }
    }
    override def add(v: Json, index: Int): Unit = {
      delegate.add(v, index)
      _pending_key.foreach(key => _fields :+= key -> v)
      _pending_key = None
    }
    override def finish(index: Int): Json = {
      val result = delegate.finish(index)
      capture(_fields)
      result
    }
    override def isObj: Boolean = delegate.isObj
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
          Record.create(ordered.flatMap { case (key, value) =>
            rawindex.get(key).orElse(if (value.isNull) Some(Json.Null) else normalized.getAny(key)).map(key -> _)
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
