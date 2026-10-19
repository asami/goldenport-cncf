package org.goldenport.cncf.validation

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtScenarioParameters
import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value

/** Pure schema admission and exact JSON / ScalaTest ConfigMap input binding.
 * @since   Oct. 19, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object ValidationInputsV1 {
  enum Kind {
    case Text, BooleanValue, Integer
  }

  final case class Parameter(
    name: String,
    kind: Kind,
    required: Boolean = true,
    defaultValue: Option[Value] = None
  )

  private val _prefix = "cncf.validation.input."

  def validateSchemaC(schema: Vector[Parameter]): Consequence[Vector[Parameter]] =
    if (schema == null || schema.size > 16 || schema.exists { parameter =>
        parameter == null || parameter.name == null ||
          !parameter.name.matches("[a-z][a-z0-9-]{0,63}") || parameter.kind == null ||
          parameter.defaultValue == null || parameter.defaultValue.contains(null)
      }) {
      _failure("schema", "at most 16 nonnull named typed parameter declarations")
    } else if (schema.map(_.name).distinct.size != schema.size) {
      _failure("schema", "distinct parameter names")
    } else {
      schema.foldLeft(Consequence.unit) { (result, parameter) =>
        result.flatMap { _ => parameter.defaultValue match {
          case None => Consequence.unit
          case Some(value) => _validate_value(parameter, value).map(_ => ())
        }}
      }.map(_ => schema.sortBy(_.name))
    }

  def bindC(
    schema: Vector[Parameter],
    supplied: Map[String, Json]
  ): Consequence[Map[String, Value]] =
    validateSchemaC(schema).flatMap { canonical =>
      if (supplied == null || supplied.keys.exists(_ == null) ||
          supplied.keys.exists(name => !canonical.exists(_.name == name))) {
        _failure("supplied", "nonnull map of declared input names")
      } else {
        _bind(canonical, supplied) { (parameter, json) =>
          if (json == null || json.isNull) _failure(parameter.name, "explicit nonnull value")
          else {
            val decoded: Option[Value] = parameter.kind match {
              case Kind.Text => json.asString.map(Value.Text(_))
              case Kind.BooleanValue => json.asBoolean.map(Value.BooleanValue(_))
              case Kind.Integer => json.asNumber.flatMap(_.toBigDecimal)
                .filter(x => x.isWhole && x.isValidLong).map(x => Value.Integer(x.toLong))
            }
            decoded.map(_validate_value(parameter, _)).getOrElse(
              _failure(parameter.name, "exact declared JSON kind with integral Long numbers"))
          }
        }
      }
    }

  def fromConfigMapC(
    schema: Vector[Parameter],
    supplied: Map[String, Any]
  ): Consequence[Map[String, Value]] =
    validateSchemaC(schema).flatMap { canonical =>
      if (supplied == null || supplied.keys.exists(_ == null)) {
        _failure("configMap", "nonnull ConfigMap with nonnull keys")
      } else {
        val namespaced = supplied.toVector.collect {
          case (name, value) if name.startsWith(_prefix) => name.substring(_prefix.length) -> value
        }.toMap
        if (namespaced.keys.exists(name => !canonical.exists(_.name == name))) {
          _failure("configMap", "only declared names in the validation input namespace")
        } else {
          _bind(canonical, namespaced) { (parameter, value) => value match {
            case text: String =>
              val decoded: Option[Value] = parameter.kind match {
                case Kind.Text => Some(Value.Text(text))
                case Kind.BooleanValue => text match {
                  case "true" => Some(Value.BooleanValue(true))
                  case "false" => Some(Value.BooleanValue(false))
                  case _ => None
                }
                case Kind.Integer => text.toLongOption.filter(_.toString == text).map(Value.Integer(_))
              }
              decoded.map(_validate_value(parameter, _)).getOrElse(
                _failure(parameter.name, "canonical declared ConfigMap value"))
            case _ => _failure(parameter.name, "nonnull String ConfigMap value")
          }}
        }
      }
    }

  private def _bind[A](schema: Vector[Parameter], supplied: Map[String, A])(
    decode: (Parameter, A) => Consequence[Value]
  ): Consequence[Map[String, Value]] =
    schema.foldLeft(Consequence.success(Map.empty[String, Value])) { (result, parameter) =>
      result.flatMap { values =>
        if (supplied.contains(parameter.name)) {
          decode(parameter, supplied(parameter.name)).map(value => values.updated(parameter.name, value))
        } else parameter.defaultValue match {
          case Some(value) => Consequence.success(values.updated(parameter.name, value))
          case None if parameter.required => _failure(parameter.name, "required input or declared default")
          case None => Consequence.success(values)
        }
      }
    }

  private def _validate_value(parameter: Parameter, value: Value): Consequence[Value] = {
    val matches = (parameter.kind, value) match {
      case (Kind.Text, Value.Text(_)) => true
      case (Kind.BooleanValue, Value.BooleanValue(_)) => true
      case (Kind.Integer, Value.Integer(_)) => true
      case _ => false
    }
    if (!matches) _failure(parameter.name, "value of the exact declared kind")
    else SbtScenarioParameters.validateC(Map(parameter.name -> value)).map(_ => value).recoverWith { _ =>
      _failure(parameter.name, "bounded literal input value")
    }
  }

  private def _failure[A](field: String, expected: String): Consequence[A] =
    Consequence.argumentPolicyViolation(field, "validation.inputs", expected, "invalid")
}
