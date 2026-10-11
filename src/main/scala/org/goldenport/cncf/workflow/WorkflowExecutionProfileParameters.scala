package org.goldenport.cncf.workflow

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import WorkflowExecutionProfileMapping.ClassBinding

/** Decode only a standard CNCF Component parameter snapshot. No filesystem
  * discovery, configuration precedence, profile defaults or provider invocation.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
object WorkflowExecutionProfileParameters {
  val schemaVersion = "cncf.workflow-execution-profile-mapping.v1"
  val declaration: ComponentParameterKey[WorkflowExecutionProfileMapping] =
    ComponentParameterKey.optional("workflow.execution.profiles", ComponentParameterDecoder { value =>
      value match {
        case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw)
          .fold(_ => Consequence.configurationInvalid("Workflow execution profile JSON is invalid"), decodeC)
        case structured => decodeC(_json(structured))
      }
    })

  /** Absence stays absence for legacy host composition, never a default profile.
    * A mapped invocation must explicitly require the returned mapping.
    */
  def resolveC(parameters: ComponentInitializationParameters): Consequence[Option[WorkflowExecutionProfileMapping]] =
    if (parameters == null) Consequence.configurationInvalid("Workflow execution requires Component initialization parameters")
    else parameters.resolve(declaration).map(_.value)

  def decodeC(value: Json): Consequence[WorkflowExecutionProfileMapping] = {
    val decoded = for {
      root <- _fields(value, Set("schemaVersion", "identity", "revision", "profiles", "classes"))
      schema <- _string(root, "schemaVersion")
      _ <- Either.cond(schema == schemaVersion, (), "Workflow execution profile mapping schema differs")
      identity <- _string(root, "identity")
      revision <- _string(root, "revision")
      profiles <- _vector(root("profiles").get, _profile)
      classes <- _vector(root("classes").get, _binding)
    } yield (identity, revision, profiles, classes)
    decoded.fold(Consequence.configurationInvalid(_), { case (identity, revision, profiles, classes) =>
      WorkflowExecutionProfileMapping.createC(identity, revision, profiles, classes)
    })
  }

  private def _profile(value: Json): Either[String, WorkflowConcreteExecutionProfile] = for {
    fields <- _fields(value, Set("identity", "provider", "model", "controls"))
    identity <- _string(fields, "identity")
    provider <- _string(fields, "provider")
    model <- _string(fields, "model")
    objectvalue <- fields("controls").flatMap(_.asObject).toRight("Execution controls must be an object")
    controls <- objectvalue.toVector.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
      case (prior, (key, value)) => for {
        values <- prior
        text <- value.asString.filter(WorkflowExecutionProfileMapping.name).toRight("Execution control must be a nonempty string")
      } yield values + (key -> text)
    }
  } yield WorkflowConcreteExecutionProfile(identity, provider, model, controls)

  private def _binding(value: Json): Either[String, ClassBinding] = for {
    fields <- _fields(value, Set("reasoningClass", "profile"))
    reasoningclass <- _string(fields, "reasoningClass")
    profile <- _string(fields, "profile")
  } yield ClassBinding(reasoningclass, profile)

  private def _fields(value: Json, keys: Set[String]): Either[String, JsonObject] =
    Option(value).flatMap(_.asObject).filter(_.keys.toSet == keys).toRight("Execution profile fields are missing or unknown")
  private def _string(fields: JsonObject, key: String): Either[String, String] =
    fields(key).flatMap(_.asString).filter(WorkflowExecutionProfileMapping.name).toRight("Execution profile string is missing: " + key)
  private def _vector[A](value: Json, decode: Json => Either[String, A]): Either[String, Vector[A]] =
    value.asArray.toRight("Execution profile collection must be an array").flatMap { values =>
      values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (prior, value) =>
        for { accumulated <- prior; decoded <- decode(value) } yield accumulated :+ decoded
      }
    }
  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(value) => Json.fromString(value)
    case ConfigurationValue.NumberValue(value) => Json.fromBigDecimal(value)
    case ConfigurationValue.BooleanValue(value) => Json.fromBoolean(value)
    case ConfigurationValue.ListValue(values) => Json.fromValues(values.map(_json))
    case ConfigurationValue.ObjectValue(values) => Json.obj(values.toVector.map((key, value) => key -> _json(value))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
