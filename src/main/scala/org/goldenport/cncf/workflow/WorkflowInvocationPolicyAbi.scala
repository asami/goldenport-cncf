package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import io.circe.Json
import io.circe.jawn.JawnParser
import org.goldenport.{Conclusion, Consequence}

/** Pure receiver for generated invocation metadata; execution belongs to Phase81.
  * @since   Oct.  2, 2026
  * @version Oct.  2, 2026
  */
object WorkflowInvocationPolicyAbi {
  val legacySchemaVersion: String = "cozy.cml.statemachine-workflow-abi.v1"
  val schemaVersion: String = "cozy.cml.statemachine-workflow-abi.v2"

  final case class SourceIdentity(line: Option[Int])
  final case class WorkflowSourceCorrelation(root: SourceIdentity, definition: SourceIdentity)
  final case class State(name: String, source: SourceIdentity)
  final case class Operation(service: String, name: String, inputType: Option[String],
    resultType: Option[String], source: SourceIdentity)
  enum ActionKind(val value: String) {
    case Operation extends ActionKind("OPERATION")
    case Judgment extends ActionKind("JUDGMENT")
    case Admission extends ActionKind("ADMISSION")
  }
  final case class Action(identity: String, kind: ActionKind, operation: Operation,
    inputBinding: Option[String], source: SourceIdentity)
  final case class RequiredSpi(capability: String, actionIdentity: String,
    operation: Operation, source: SourceIdentity)
  sealed abstract class InvocationTarget { def identity: String }
  object InvocationTarget {
    final case class Action(identity: String) extends InvocationTarget
    final case class Participant(identity: String) extends InvocationTarget
  }
  final case class RetryPolicy(maximumAttempts: Int, fixedRetryDelayMillis: Long)
  final case class InvocationPolicy(identity: String, target: InvocationTarget,
    actionIdentity: String, retry: Option[RetryPolicy],
    executionTimeoutMillis: Option[Long], source: SourceIdentity)
  final case class Workflow(identity: String, version: String, source: WorkflowSourceCorrelation,
    states: Vector[State], actions: Vector[Action], requiredSpi: Vector[RequiredSpi],
    invocationPolicies: Vector[InvocationPolicy] = Vector.empty)
  final case class Artifact private[workflow] (schemaVersion: String,
    sourceResource: String, rawJson: String, workflows: Vector[Workflow], sha256: String)

  enum DiagnosticCode(val value: String) {
    case InvalidJson extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-001")
    case InvalidShape extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-002")
    case UnsupportedSchema extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-003")
    case InvalidValue extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-004")
    case DuplicateIdentity extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-005")
    case TargetMismatch extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-006")
    case IneligibleAction extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-007")
    case DefinitionMismatch extends DiagnosticCode("CWF-INVOCATION-POLICY-ABI-008")
  }
  final case class Diagnostic(code: DiagnosticCode, data: Map[String, String]) {
    def render: String = (Vector(code.value) ++ data.toVector.sortBy(_._1)
      .map { case (key, value) => s"$key=$value" }).mkString(" ")
  }

  private val _parser = JawnParser(allowDuplicateKeys = false)
  private type Decoded[A] = Either[Diagnostic, A]

  def parseC(rawJson: String, sourceResource: String): Consequence[Artifact] = {
    _parse(rawJson, sourceResource).fold(_failure, Consequence.success)
  }

  def admitC(artifact: Artifact): Consequence[Artifact] = {
    if (artifact == null) {
      _failure(Diagnostic(DiagnosticCode.InvalidValue,
        Map("path" -> "artifact", "reason" -> "null")))
    } else {
      parseC(artifact.rawJson, artifact.sourceResource).flatMap { reparsed =>
        if (reparsed == artifact) Consequence.success(artifact)
        else _failure(Diagnostic(DiagnosticCode.DefinitionMismatch,
          Map("path" -> "artifact", "reason" -> "facts-diverge")))
      }
    }
  }

  private def _parse(raw: String, resource: String): Decoded[Artifact] = {
    for {
      _ <- _check(raw != null, DiagnosticCode.InvalidValue, "rawJson", "null")
      _ <- _check(resource != null && resource.trim.nonEmpty,
        DiagnosticCode.InvalidValue, "sourceResource", "null-or-blank")
      json <- _parser.parse(raw).left.map(_ =>
        Diagnostic(DiagnosticCode.InvalidJson, Map("path" -> "root", "reason" -> "invalid-json")))
      root <- _object(json, "root", Set("schemaVersion", "workflows"))
      schema <- _string(root("schemaVersion"), "root.schemaVersion")
      _ <- if (Set(legacySchemaVersion, schemaVersion)(schema)) Right(())
        else Left(Diagnostic(DiagnosticCode.UnsupportedSchema,
          Map("path" -> "root.schemaVersion", "reason" -> "unsupported-schema", "schema" -> schema)))
      values <- _array(root("workflows"), "root.workflows")
      workflows <- _traverse(values.zipWithIndex) { case (value, index) =>
        _workflow(value, s"root.workflows[$index]", schema == schemaVersion)
      }
      _ <- _check(schema == legacySchemaVersion || workflows.exists(_.invocationPolicies.nonEmpty),
        DiagnosticCode.InvalidValue, "root.workflows", "v2-requires-policy")
    } yield Artifact(schema, resource, raw, workflows, _sha256(raw))
  }

  private def _workflow(json: Json, path: String, policyversion: Boolean): Decoded[Workflow] = {
    val keys = Set("identity", "version", "source", "states", "actions", "requiredSpi") ++
      (if (policyversion) Set("invocationPolicies") else Set.empty[String])
    for {
      obj <- _object(json, path, keys)
      identity <- _string(obj("identity"), s"$path.identity")
      version <- _string(obj("version"), s"$path.version")
      sourceobj <- _object(obj("source"), s"$path.source", Set("root", "definition"))
      root <- _source(sourceobj("root"), s"$path.source.root")
      definition <- _source(sourceobj("definition"), s"$path.source.definition")
      states <- _items(obj("states"), s"$path.states")(_state)
      actions <- _items(obj("actions"), s"$path.actions")(_action)
      required <- _items(obj("requiredSpi"), s"$path.requiredSpi")(_required_spi)
      policies <- if (policyversion) _items(obj("invocationPolicies"), s"$path.invocationPolicies")(_policy)
        else Right(Vector.empty[InvocationPolicy])
      workflow = Workflow(identity, version, WorkflowSourceCorrelation(root, definition),
        states, actions, required, policies)
      _ <- _correlate(workflow, path)
    } yield workflow
  }

  private def _state(json: Json, path: String): Decoded[State] = {
    for {
      obj <- _object(json, path, Set("name", "source"))
      name <- _string(obj("name"), s"$path.name")
      source <- _source(obj("source"), s"$path.source")
    } yield State(name, source)
  }

  private def _operation(json: Json, path: String): Decoded[Operation] = {
    for {
      obj <- _object(json, path, Set("service", "name", "inputType", "resultType", "source"))
      service <- _string(obj("service"), s"$path.service")
      name <- _string(obj("name"), s"$path.name")
      input <- _optional_string(obj("inputType"), s"$path.inputType")
      result <- _optional_string(obj("resultType"), s"$path.resultType")
      source <- _source(obj("source"), s"$path.source")
    } yield Operation(service, name, input, result, source)
  }

  private def _action(json: Json, path: String): Decoded[Action] = {
    for {
      obj <- _object(json, path, Set("identity", "kind", "operation", "inputBinding", "source"))
      identity <- _string(obj("identity"), s"$path.identity")
      kindtext <- _string(obj("kind"), s"$path.kind")
      kind <- ActionKind.values.find(_.value == kindtext).toRight(
        Diagnostic(DiagnosticCode.InvalidValue, Map("path" -> s"$path.kind", "reason" -> "unknown-action-kind")))
      operation <- _operation(obj("operation"), s"$path.operation")
      binding <- _optional_string(obj("inputBinding"), s"$path.inputBinding")
      source <- _source(obj("source"), s"$path.source")
    } yield Action(identity, kind, operation, binding, source)
  }

  private def _required_spi(json: Json, path: String): Decoded[RequiredSpi] = {
    for {
      obj <- _object(json, path, Set("capability", "actionIdentity", "operation", "source"))
      capability <- _string(obj("capability"), s"$path.capability")
      action <- _string(obj("actionIdentity"), s"$path.actionIdentity")
      operation <- _operation(obj("operation"), s"$path.operation")
      source <- _source(obj("source"), s"$path.source")
    } yield RequiredSpi(capability, action, operation, source)
  }

  private def _policy(json: Json, path: String): Decoded[InvocationPolicy] = {
    for {
      obj <- _object(json, path, Set("identity", "target", "actionIdentity", "retry", "executionTimeoutMillis", "source"))
      identity <- _string(obj("identity"), s"$path.identity")
      targetobj <- _object(obj("target"), s"$path.target", Set("kind", "identity"))
      kind <- _string(targetobj("kind"), s"$path.target.kind")
      targetidentity <- _string(targetobj("identity"), s"$path.target.identity")
      target <- kind match {
        case "ACTION" => Right(InvocationTarget.Action(targetidentity))
        case "PARTICIPANT" => Right(InvocationTarget.Participant(targetidentity))
        case _ => Left(Diagnostic(DiagnosticCode.InvalidValue,
          Map("path" -> s"$path.target.kind", "reason" -> "unknown-target-kind")))
      }
      action <- _string(obj("actionIdentity"), s"$path.actionIdentity")
      retry <- if (obj("retry").isNull) Right(None) else _retry(obj("retry"), s"$path.retry").map(Some(_))
      timeout <- _optional_integer(obj("executionTimeoutMillis"), s"$path.executionTimeoutMillis", 1, Long.MaxValue)
      _ <- _check(retry.nonEmpty || timeout.nonEmpty, DiagnosticCode.InvalidValue, path, "controls-absent")
      source <- _source(obj("source"), s"$path.source")
    } yield InvocationPolicy(identity, target, action, retry, timeout, source)
  }

  private def _retry(json: Json, path: String): Decoded[RetryPolicy] = {
    for {
      obj <- _object(json, path, Set("maximumAttempts", "fixedRetryDelayMillis"))
      attempts <- _integer(obj("maximumAttempts"), s"$path.maximumAttempts", 1, Int.MaxValue)
      delay <- _integer(obj("fixedRetryDelayMillis"), s"$path.fixedRetryDelayMillis", 0, Long.MaxValue)
    } yield RetryPolicy(attempts.toInt, delay)
  }

  private def _source(json: Json, path: String): Decoded[SourceIdentity] = {
    for {
      obj <- _object(json, path, Set("line"))
      line <- _optional_integer(obj("line"), s"$path.line", 1, Int.MaxValue)
    } yield SourceIdentity(line.map(_.toInt))
  }

  private def _correlate(workflow: Workflow, path: String): Decoded[Unit] = {
    val context = Map("workflow" -> workflow.identity)
    for {
      _ <- _unique(workflow.states.map(_.name), s"$path.states", context)
      _ <- _unique(workflow.actions.map(_.identity), s"$path.actions", context)
      _ <- _unique(workflow.requiredSpi.map(_.capability), s"$path.requiredSpi", context)
      _ <- _unique(workflow.invocationPolicies.map(_.identity), s"$path.invocationPolicies", context)
      _ <- _traverse(workflow.actions.zipWithIndex) { case (action, index) =>
        _check(action.operation.source == action.source, DiagnosticCode.TargetMismatch,
          s"$path.actions[$index].operation.source", "action-source-mismatch", context ++ Map("action" -> action.identity))
      }
      _ <- _traverse(workflow.requiredSpi.zipWithIndex) { case (spi, index) =>
        _check(workflow.actions.exists(action => action.identity == spi.actionIdentity && action.operation == spi.operation),
          DiagnosticCode.TargetMismatch, s"$path.requiredSpi[$index]", "required-spi-action-operation-mismatch",
          context ++ Map("capability" -> spi.capability, "action" -> spi.actionIdentity))
      }
      _ <- _traverse(workflow.invocationPolicies.zipWithIndex) { case (policy, index) =>
        _bind_policy(workflow, policy, s"$path.invocationPolicies[$index]")
      }
      _ <- _unique(workflow.invocationPolicies.map(_.actionIdentity), s"$path.invocationPolicies",
        context ++ Map("reason" -> "duplicate-resolved-action"))
    } yield ()
  }

  private def _bind_policy(workflow: Workflow, policy: InvocationPolicy, path: String): Decoded[Unit] = {
    val context = Map("workflow" -> workflow.identity, "policy" -> policy.identity, "action" -> policy.actionIdentity)
    val candidates = policy.target match {
      case InvocationTarget.Action(identity) => workflow.actions.filter(action => _alias(action.identity) == _alias(identity))
      case InvocationTarget.Participant(identity) =>
        val bindings = workflow.requiredSpi.filter(spi => _alias(spi.capability) == _alias(identity))
        if (bindings.size == 1) workflow.actions.filter(_.identity == bindings.head.actionIdentity)
        else Vector.empty
    }
    for {
      _ <- _check(candidates.size == 1 && candidates.head.identity == policy.actionIdentity,
        DiagnosticCode.TargetMismatch, path, "target-action-mismatch", context)
      _ <- _check(candidates.head.kind != ActionKind.Admission,
        DiagnosticCode.IneligibleAction, path, "admission-action", context)
    } yield ()
  }

  private def _alias(value: String): String = {
    value.toLowerCase.filter(_.isLetterOrDigit)
  }

  private def _unique(values: Vector[String], path: String, context: Map[String, String]): Decoded[Unit] = {
    val duplicates = values.groupBy(_alias).values.filter(_.size > 1).toVector.flatten.sorted
    _check(duplicates.isEmpty, DiagnosticCode.DuplicateIdentity, path, "duplicate-identity",
      context ++ duplicates.headOption.map("identity" -> _).toMap)
  }

  private def _object(json: Json, path: String, keys: Set[String]): Decoded[Map[String, Json]] = {
    for {
      obj <- json.asObject.map(_.toMap).toRight(Diagnostic(DiagnosticCode.InvalidShape,
        Map("path" -> path, "reason" -> "expected-object")))
      _ <- _check(keys.subsetOf(obj.keySet), DiagnosticCode.InvalidShape, path,
        "missing-keys", Map("keys" -> (keys -- obj.keySet).toVector.sorted.mkString(",")))
      _ <- _check(obj.keySet.subsetOf(keys), DiagnosticCode.InvalidShape, path,
        "extra-keys", Map("keys" -> (obj.keySet -- keys).toVector.sorted.mkString(",")))
    } yield obj
  }

  private def _string(json: Json, path: String): Decoded[String] = {
    for {
      value <- json.asString.toRight(Diagnostic(DiagnosticCode.InvalidShape,
        Map("path" -> path, "reason" -> "expected-string")))
      _ <- _check(value.trim.nonEmpty, DiagnosticCode.InvalidValue, path, "blank-string")
    } yield value
  }

  private def _optional_string(json: Json, path: String): Decoded[Option[String]] = {
    if (json.isNull) Right(None) else _string(json, path).map(Some(_))
  }

  private def _integer(json: Json, path: String, minimum: Long, maximum: Long): Decoded[Long] = {
    val token = json.asNumber.map(_.toString)
    for {
      text <- token.filter(_.matches("[0-9]+")).toRight(Diagnostic(DiagnosticCode.InvalidValue,
        Map("path" -> path, "reason" -> "expected-unsigned-integer-token")))
      value = BigInt(text)
      _ <- _check(value >= minimum && value <= maximum, DiagnosticCode.InvalidValue, path, "integer-out-of-range")
    } yield value.toLong
  }

  private def _optional_integer(json: Json, path: String, minimum: Long, maximum: Long): Decoded[Option[Long]] = {
    if (json.isNull) Right(None) else _integer(json, path, minimum, maximum).map(Some(_))
  }

  private def _array(json: Json, path: String): Decoded[Vector[Json]] = {
    json.asArray.toRight(Diagnostic(DiagnosticCode.InvalidShape, Map("path" -> path, "reason" -> "expected-array")))
  }

  private def _items[A](json: Json, path: String)(decode: (Json, String) => Decoded[A]): Decoded[Vector[A]] = {
    _array(json, path).flatMap(values => _traverse(values.zipWithIndex) { case (value, index) =>
      decode(value, s"$path[$index]")
    })
  }

  private def _traverse[A, B](values: Vector[A])(decode: A => Decoded[B]): Decoded[Vector[B]] = {
    values.foldLeft[Decoded[Vector[B]]](Right(Vector.empty)) { (acc, value) =>
      for { items <- acc; item <- decode(value) } yield items :+ item
    }
  }

  private def _check(condition: Boolean, code: DiagnosticCode, path: String,
    reason: String, context: Map[String, String] = Map.empty): Decoded[Unit] = {
    Either.cond(condition, (), Diagnostic(code, Map("path" -> path, "reason" -> reason) ++ context))
  }

  private def _sha256(raw: String): String = {
    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _failure[A](diagnostic: Diagnostic): Consequence[A] = {
    Consequence.Failure(Conclusion.from(new WorkflowInvocationPolicyAbiException(diagnostic)))
  }
}

/** Typed failure transported through Consequence.
  * @since   Oct.  2, 2026
  * @version Oct.  2, 2026
  */
final class WorkflowInvocationPolicyAbiException(val diagnostic: WorkflowInvocationPolicyAbi.Diagnostic)
  extends IllegalArgumentException(diagnostic.render)
