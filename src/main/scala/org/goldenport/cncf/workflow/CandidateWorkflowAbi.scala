package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.{Conclusion, Consequence}

/** Receiver for the StateMachine workflow metadata emitted alongside Cozy's
  * Candidate-Admission sidecar. This does not change the closed Phase 62.3 ABI.
  */
object CandidateWorkflowAbi {
  final case class Operation(service: String, name: String, inputType: Option[String], resultType: Option[String])
  final case class Action(identity: String, kind: String, operation: Operation, inputBinding: Option[String], line: Int)
  final case class RequiredSpi(capability: String, actionIdentity: String, operation: Operation, line: Int)
  final case class Workflow(identity: String, version: String, rootLine: Int, definitionLine: Int,
    states: Vector[String], actions: Vector[Action], requiredSpi: Vector[RequiredSpi])
  final case class Definition private[workflow] (rawJson: String,
    sidecar: CandidateAdmissionProducerAbi.Artifact, sourceResource: String,
    workflow: Workflow, sha256: String)

  val schemaVersion: String = "cozy.cml.statemachine-workflow-abi.v1"

  def parseC(rawJson: String, sidecar: CandidateAdmissionProducerAbi.Artifact,
    sourceResource: String): Consequence[Definition] =
    _parse(rawJson, sidecar, sourceResource).fold(
      message => _failure(s"CWF77-CANDIDATE-WORKFLOW-ABI: $message"),
      Consequence.success
    )

  def admitC(definition: Definition): Consequence[Definition] =
    if (definition == null)
      _failure("CWF77-CANDIDATE-WORKFLOW-ABI: missing definition")
    else
      parseC(definition.rawJson, definition.sidecar, definition.sourceResource).flatMap { admitted =>
        if (admitted == definition) Consequence.success(definition)
        else _failure("CWF77-CANDIDATE-WORKFLOW-ABI: definition facts diverge")
      }

  private def _parse(raw: String, sidecar: CandidateAdmissionProducerAbi.Artifact,
    resource: String): Either[String, Definition] =
    for {
      _ <- Either.cond(raw != null && raw.nonEmpty && resource != null && resource.trim.nonEmpty,
        (), "missing workflow JSON or source resource")
      _ <- if (sidecar == null) Left("candidate-admission sidecar missing")
      else CandidateAdmissionProducerAbi.admitC(sidecar) match {
        case Consequence.Success(_) => Right(())
        case _ => Left("candidate-admission sidecar rejected")
      }
      json <- parse(raw).left.map(_ => "invalid workflow JSON")
      root <- _object(json, "root")
      schema <- _string(root, "schemaVersion", "root")
      _ <- Either.cond(schema == schemaVersion, (), s"unsupported schema: $schema")
      workflows <- _array(root, "workflows", "root")
      _ <- Either.cond(workflows.size == 1, (), "exactly one workflow required")
      workflow <- _workflow(workflows.head)
      _ <- _match_sidecar(workflow, sidecar)
    } yield Definition(raw, sidecar, resource, workflow, _sha256(raw))

  private def _workflow(json: Json): Either[String, Workflow] =
    for {
      fields <- _object(json, "workflow")
      identity <- _string(fields, "identity", "workflow")
      version <- _string(fields, "version", "workflow")
      source <- _object_field(fields, "source", "workflow")
      root <- _line_field(source, "root", "workflow.source")
      definition <- _line_field(source, "definition", "workflow.source")
      stateJson <- _array(fields, "states", "workflow")
      states <- _traverse(stateJson) { state =>
        for {
          obj <- _object(state, "state")
          name <- _string(obj, "name", "state")
          _ <- _line_field(obj, "source", "state")
        } yield name
      }
      actionJson <- _array(fields, "actions", "workflow")
      actions <- _traverse(actionJson)(_action)
      requiredJson <- _array(fields, "requiredSpi", "workflow")
      required <- _traverse(requiredJson)(_required_spi)
      _ <- Either.cond(states.nonEmpty && states.distinct.size == states.size,
        (), "states missing or duplicated")
      _ <- Either.cond(actions.nonEmpty && actions.map(_.identity).distinct.size == actions.size,
        (), "actions missing or duplicated")
      _ <- Either.cond(required.map(_.capability).distinct.size == required.size,
        (), "Required SPI capability duplicated")
      _ <- Either.cond(required.forall(spi => actions.exists(action =>
        action.identity == spi.actionIdentity && action.operation == spi.operation)),
        (), "Required SPI action or operation mismatch")
    } yield Workflow(identity, version, root, definition, states, actions, required)

  private def _action(json: Json): Either[String, Action] =
    for {
      obj <- _object(json, "action")
      identity <- _string(obj, "identity", "action")
      kind <- _string(obj, "kind", "action")
      _ <- Either.cond(Set("OPERATION", "JUDGMENT", "ADMISSION").contains(kind),
        (), s"unsupported action kind: $kind")
      operation <- _object_field(obj, "operation", "action").flatMap(_operation)
      binding <- _optional_string(obj, "inputBinding", "action")
      line <- _line_field(obj, "source", "action")
    } yield Action(identity, kind, operation, binding, line)

  private def _required_spi(json: Json): Either[String, RequiredSpi] =
    for {
      obj <- _object(json, "requiredSpi")
      capability <- _string(obj, "capability", "requiredSpi")
      action <- _string(obj, "actionIdentity", "requiredSpi")
      operation <- _object_field(obj, "operation", "requiredSpi").flatMap(_operation)
      line <- _line_field(obj, "source", "requiredSpi")
    } yield RequiredSpi(capability, action, operation, line)

  private def _operation(obj: Map[String, Json]): Either[String, Operation] =
    for {
      service <- _string(obj, "service", "operation")
      name <- _string(obj, "name", "operation")
      input <- _optional_string(obj, "inputType", "operation")
      result <- _optional_string(obj, "resultType", "operation")
      _ <- _line_field(obj, "source", "operation")
    } yield Operation(service, name, input, result)

  private def _match_sidecar(workflow: Workflow,
    artifact: CandidateAdmissionProducerAbi.Artifact): Either[String, Unit] = {
    val models = artifact.models.filter(_.model.workflow.exists(w =>
      w.identity.value == workflow.identity && w.version.value == workflow.version))
    if (models.size != 1) Left("workflow identity/version does not match one sidecar model")
    else {
      val model = models.head
      val source = model.model.workflow.get
      val expectedActions = model.judgments.map(j =>
        Action(j.identity.value, "JUDGMENT", _operation(j.operation),
          j.inputBinding.map(_.value), j.actionSource.line)) ++
        model.admissions.map(a =>
          Action(a.identity.value, "ADMISSION", _operation(a.operation),
            a.inputBinding.map(_.value), a.actionSource.line))
      val expectedSpi = model.requiredSpi.map(s =>
        RequiredSpi(s.identity.value, s.actionIdentity.value,
          _operation(s.operation), s.capabilitySource.line))
      if (workflow.rootLine != source.rootSource.line ||
          workflow.definitionLine != source.definitionSource.line)
        Left("workflow source does not match sidecar")
      else if (workflow.actions.toSet != expectedActions.toSet ||
          workflow.actions.size != expectedActions.size)
        Left("workflow actions do not match sidecar")
      else if (workflow.requiredSpi.toSet != expectedSpi.toSet ||
          workflow.requiredSpi.size != expectedSpi.size)
        Left("workflow Required SPI does not match sidecar")
      else Right(())
    }
  }

  private def _operation(value: CandidateAdmissionProducerAbi.Operation): Operation =
    Operation(value.service.value, value.name.value,
      value.inputType.map(_.value), value.resultType.map(_.value))

  private def _object(json: Json, at: String): Either[String, Map[String, Json]] =
    json.asObject.map(_.toMap).toRight(s"$at must be an object")
  private def _object_field(obj: Map[String, Json], key: String,
    at: String): Either[String, Map[String, Json]] =
    obj.get(key).toRight(s"$at.$key missing").flatMap(_object(_, s"$at.$key"))
  private def _string(obj: Map[String, Json], key: String, at: String): Either[String, String] =
    obj.get(key).flatMap(_.asString).filter(_.trim.nonEmpty).toRight(s"$at.$key missing")
  private def _optional_string(obj: Map[String, Json], key: String,
    at: String): Either[String, Option[String]] =
    obj.get(key) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => value.asString.filter(_.trim.nonEmpty).map(Some(_))
        .toRight(s"$at.$key must be a nonblank string")
    }
  private def _array(obj: Map[String, Json], key: String,
    at: String): Either[String, Vector[Json]] =
    obj.get(key).flatMap(_.asArray).map(_.toVector).toRight(s"$at.$key must be an array")
  private def _line_field(obj: Map[String, Json], key: String, at: String): Either[String, Int] =
    for {
      source <- _object_field(obj, key, at)
      line <- source.get("line").flatMap(_.asNumber).flatMap(_.toInt)
        .filter(_ > 0).toRight(s"$at.$key.line missing")
    } yield line
  private def _traverse[A](values: Vector[Json])(f: Json => Either[String, A]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty))((acc, value) =>
      for { items <- acc; item <- f(value) } yield items :+ item)
  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString

  private def _failure[A](message: String): Consequence[A] =
    Consequence.Failure(Conclusion.from(new IllegalArgumentException(message)))
}
