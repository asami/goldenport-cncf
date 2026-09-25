package org.goldenport.cncf.workflow

import io.circe.{HCursor, Json}
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.statemachine.{ExecutionPlan, ExecutionPlanExecutor, ResolvedAction}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}

/** Receives Cozy's Workflow and logical ActionProgram sidecars as one boundary.
  * The Workflow Action array is a declaration, never an execution sequence.
  */
object GeneratedWorkflowActionProgram {
  val workflowSchemaVersion = "cozy.cml.statemachine-workflow-abi.v1"
  val actionProgramSchemaVersion = "cozy.cml.logical-action-program.v1"

  final case class Operation(service: String, name: String, inputType: Option[String])
  final case class Action(
    identity: String,
    operation: Operation,
    inputBinding: Option[String],
    sourceLine: Int,
    metadata: Option[Metadata]
  )
  final case class Transition(role: String, from: String, to: String, on: String)
  enum Placement(val value: String, val rank: Int) {
    case Exit extends Placement("exit", 0)
    case Transition extends Placement("transition", 1)
    case Entry extends Placement("entry", 2)
  }
  final case class Metadata(
    effectClass: String,
    transactionRequirement: String,
    idempotencyRequired: Boolean,
    idempotencyKeyRef: Option[String]
  )
  final case class Occurrence(
    definitionIdentity: String,
    identity: String,
    ordinal: Int,
    actionIdentity: String,
    operation: Operation,
    inputBinding: Option[String],
    actionSourceLine: Int,
    occurrenceSourceLine: Int,
    placement: Placement,
    transition: Transition,
    metadata: Metadata
  )
  final case class Definition(
    workflowIdentity: String,
    workflowVersion: String,
    workflowAbiSha256: String,
    actionProgramSha256: String,
    actions: Vector[Action],
    requiredSpiActionIdentities: Vector[String],
    occurrences: Vector[Occurrence]
  ) {
    def orderedOccurrences: Vector[Occurrence] = occurrences.sortBy(_.ordinal)
  }
  final case class Binding[S, E](actionIdentity: String, action: ResolvedAction[S, E])

  def parseC(workflowAbi: String, actionProgramAbi: String): Consequence[Definition] =
    _decode(workflowAbi, actionProgramAbi).fold(
      error => Consequence.stateConflict(s"Generated Workflow ActionProgram: $error"),
      Consequence.success
    )

  /** Bind only admitted occurrence identities, in their explicit ordinal order. */
  def bindC[S, E](
    definition: Definition,
    bindings: Vector[Binding[S, E]]
  ): Consequence[ExecutionPlan[S, E]] = {
    val checked = _validate(definition)
    if (checked.nonEmpty)
      Consequence.stateConflict(s"Generated Workflow ActionProgram: ${checked.get}")
    else if (bindings == null || bindings.exists(x => x == null || x.action == null))
      Consequence.stateConflict("Generated Workflow ActionProgram: null binding")
    else {
      val ids = bindings.map(_.actionIdentity)
      val required = definition.occurrences.map(_.actionIdentity).distinct
      if (ids.distinct.size != ids.size || ids.toSet != required.toSet)
        Consequence.stateConflict("Generated Workflow ActionProgram: missing, duplicate, or unknown Action binding")
      else {
        val byid = bindings.map(x => x.actionIdentity -> x.action).toMap
        val ordered = definition.orderedOccurrences
        Consequence.success(ExecutionPlan(
          ordered.filter(_.placement == Placement.Exit).map(x => byid(x.actionIdentity)),
          ordered.filter(_.placement == Placement.Transition).map(x => byid(x.actionIdentity)),
          ordered.filter(_.placement == Placement.Entry).map(x => byid(x.actionIdentity))
        ))
      }
    }
  }

  /** Bind the generated transition to a declared public Provided Operation.
    * The caller still owns the Provided API declaration, commit, and persistence.
    */
  def bindProvidedApiProgramC(
    definition: Definition,
    providedOperation: StateMachineOperationIdentity,
    bindings: Vector[Binding[StateMachineProvidedApiRequest, Unit]]
  ): Consequence[StateMachineProvidedApiProgram] =
    if (providedOperation == null || providedOperation.service == null || providedOperation.operation == null ||
        providedOperation.service.trim.isEmpty || providedOperation.operation.trim.isEmpty)
      Consequence.stateConflict("Generated Workflow ActionProgram: missing Provided Operation")
    else bindC(definition, bindings).map { plan =>
      new StateMachineProvidedApiProgram {
        val workflowIdentity: String = definition.workflowIdentity
        val operation: StateMachineOperationIdentity = providedOperation

        def program(request: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] =
          if (request == null || request.workflowIdentity != definition.workflowIdentity ||
              request.workflowRevision != definition.workflowVersion ||
              request.operation != operation || request.context == null ||
              request.context.snapshot == null ||
              request.context.snapshot.workflowRevision != definition.workflowVersion)
            ConsequenceT.fromConsequence[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              Consequence.stateConflict("Generated Workflow ActionProgram: Provided request provenance differs")
            )
          else ExecutionPlanExecutor.program(plan, request, ())
      }
    }

  private def _decode(workflowabi: String, actionprogramabi: String): Either[String, Definition] =
    for {
      workflowjson <- parse(workflowabi).left.map(_ => "invalid Workflow ABI JSON")
      programjson <- parse(actionprogramabi).left.map(_ => "invalid ActionProgram JSON")
      workflowroot = workflowjson.hcursor
      programroot = programjson.hcursor
      workflowschema <- _get[String](workflowroot, "schemaVersion")
      programschema <- _get[String](programroot, "schemaVersion")
      _ <- _require(workflowschema == workflowSchemaVersion, "unsupported Workflow ABI schema")
      _ <- _require(programschema == actionProgramSchemaVersion, "unsupported ActionProgram schema")
      workflows <- _get[Vector[Json]](workflowroot, "workflows")
      programs <- _get[Vector[Json]](programroot, "definitions")
      _ <- _require(workflows.size == 1 && programs.size == 1, "exactly one Workflow and ActionProgram definition are required")
      workflow = workflows.head.hcursor
      program = programs.head.hcursor
      identity <- _get[String](workflow, "identity")
      version <- _get[String](workflow, "version")
      programidentity <- _get[String](program, "definitionIdentity")
      _ <- _require(identity == programidentity, "Workflow and ActionProgram identities differ")
      workflowactions <- _get[Vector[Json]](workflow, "actions")
      programactions <- _get[Vector[Json]](program, "actions")
      spi <- _get[Vector[Json]](workflow, "requiredSpi")
      occurrences <- _get[Vector[Json]](program, "occurrences")
      declared <- _traverse(workflowactions)(_workflow_action)
      produced <- _traverse(programactions)(_program_action)
      required <- _traverse(spi)(x => _get[String](x.hcursor, "actionIdentity"))
      placed <- _traverse(occurrences)(_occurrence)
      _ <- _require(declared == produced.map(_.copy(metadata = None)),
        "Workflow and ActionProgram Action descriptors differ")
      result = Definition(identity, version, _sha256(workflowabi), _sha256(actionprogramabi),
        produced, required, placed)
      _ <- _validate(result).toLeft(())
    } yield result

  private def _workflow_action(value: Json): Either[String, Action] = {
    val cursor = value.hcursor
    for {
      kind <- _get[String](cursor, "kind")
      _ <- _require(kind == "OPERATION", "unsupported Workflow Action kind")
      identity <- _get[String](cursor, "identity")
      operation <- _operation(cursor.downField("operation").success)
      binding <- _optional_string(cursor, "inputBinding")
      source <- _source_line(cursor, "source")
    } yield Action(identity, operation, binding, source, None)
  }

  private def _program_action(value: Json): Either[String, Action] = {
    val cursor = value.hcursor
    for {
      kind <- _get[String](cursor, "kind")
      _ <- _require(kind == "OPERATION", "unsupported ActionProgram Action kind")
      identity <- _get[String](cursor, "actionId")
      operation <- _operation(cursor.downField("operation").success)
      binding <- _optional_string(cursor, "inputBinding")
      source <- _source_line(cursor, "actionSource")
      metadata <- _metadata(cursor.downField("metadata").success)
    } yield Action(identity, operation, binding, source, Some(metadata))
  }

  private def _occurrence(value: Json): Either[String, Occurrence] = {
    val cursor = value.hcursor
    for {
      definition <- _get[String](cursor, "definitionIdentity")
      identity <- _get[String](cursor, "occurrenceId")
      ordinal <- _get[Int](cursor, "ordinal")
      action <- _get[String](cursor, "actionId")
      kind <- _get[String](cursor, "kind")
      _ <- _require(kind == "OPERATION", "unsupported occurrence kind")
      operation <- _operation(cursor.downField("operation").success)
      binding <- _optional_string(cursor, "inputBinding")
      source <- _source_line(cursor, "actionSource")
      occurrencesource <- _source_line(cursor, "occurrenceSource")
      placementtext <- _get[String](cursor, "placement")
      placement <- Placement.values.find(_.value == placementtext).toRight("unsupported placement")
      role <- _get[String](cursor, "role")
      from <- _get[String](cursor, "from")
      to <- _get[String](cursor, "to")
      on <- _get[String](cursor, "on")
      metadata <- _metadata(cursor.downField("metadata").success)
    } yield Occurrence(definition, identity, ordinal, action, operation, binding, source,
      occurrencesource, placement, Transition(role, from, to, on), metadata)
  }

  private def _operation(value: Option[HCursor]): Either[String, Operation] =
    value.toRight("missing Operation").flatMap { cursor =>
      for {
        service <- _get[String](cursor, "service")
        name <- _get[String](cursor, "name")
        input <- _optional_string(cursor, "inputType")
      } yield Operation(service, name, input)
    }

  private def _metadata(value: Option[HCursor]): Either[String, Metadata] =
    value.toRight("missing Action metadata").flatMap { cursor =>
      for {
        effect <- _get[String](cursor, "effectClass")
        transaction <- _get[String](cursor, "transactionRequirement")
        idempotency <- cursor.downField("idempotency").success.toRight("missing idempotency metadata")
        required <- _get[Boolean](idempotency, "required")
        key <- _optional_string(idempotency, "keyRef")
      } yield Metadata(effect, transaction, required, key)
    }

  private def _validate(value: Definition): Option[String] = {
    if (value == null || value.actions == null || value.occurrences == null ||
        value.requiredSpiActionIdentities == null)
      Some("missing definition")
    else if (value.workflowIdentity.isEmpty || value.workflowVersion.isEmpty ||
        value.actions.isEmpty || value.occurrences.isEmpty)
      Some("missing Workflow identity, version, Action, or occurrence")
    else if (value.actions.map(_.identity).distinct.size != value.actions.size)
      Some("duplicate Action identity")
    else if (value.actions.exists(x => x.metadata.isEmpty || !_valid_metadata(x.metadata.get)))
      Some("Action metadata is missing or invalid")
    else if (value.requiredSpiActionIdentities.distinct.size != value.requiredSpiActionIdentities.size ||
        !value.requiredSpiActionIdentities.forall(x => value.actions.exists(_.identity == x)))
      Some("invalid Required SPI Action identity")
    else if (value.occurrences.map(_.identity).distinct.size != value.occurrences.size)
      Some("duplicate occurrence identity")
    else if (value.occurrences.exists(_.definitionIdentity != value.workflowIdentity))
      Some("occurrence belongs to another definition")
    else if (value.orderedOccurrences.map(_.ordinal) != (1 to value.occurrences.size).toVector)
      Some("occurrence ordinals must be contiguous")
    else if (value.occurrences.map(_.transition).distinct.size != 1)
      Some("one admitted ActionProgram must select one constituent transition")
    else if (value.orderedOccurrences.map(_.placement.rank) !=
        value.orderedOccurrences.map(_.placement.rank).sorted)
      Some("occurrence placement order is invalid")
    else if (value.occurrences.exists { occurrence =>
      !value.actions.exists(action =>
        action.identity == occurrence.actionIdentity &&
        action.operation == occurrence.operation &&
        action.inputBinding == occurrence.inputBinding &&
        action.sourceLine == occurrence.actionSourceLine &&
        action.metadata.contains(occurrence.metadata))
    })
      Some("occurrence does not match a declared Action")
    else if (value.actions.exists(_.sourceLine <= 0) ||
        value.occurrences.exists(x => x.actionSourceLine <= 0 ||
          x.occurrenceSourceLine <= 0 || !_valid_metadata(x.metadata)))
      Some("occurrence source or metadata is invalid")
    else None
  }

  private def _valid_metadata(value: Metadata): Boolean =
    value != null && ((value.effectClass, value.transactionRequirement) match {
      case ("LOCAL", "REQUIRED") => !value.idempotencyRequired || value.idempotencyKeyRef.exists(_.nonEmpty)
      case ("EXTERNAL", "OUTSIDE_UNIT_OF_WORK") =>
        value.idempotencyRequired && value.idempotencyKeyRef.exists(_.nonEmpty)
      case _ => false
    })

  private def _source_line(cursor: HCursor, field: String): Either[String, Int] =
    cursor.downField(field).success.toRight(s"missing $field source")
      .flatMap(_get[Int](_, "line"))

  private def _optional_string(cursor: HCursor, field: String): Either[String, Option[String]] =
    cursor.get[Option[String]](field).left.map(_ => s"invalid $field")

  private def _get[A: io.circe.Decoder](cursor: HCursor, field: String): Either[String, A] =
    cursor.get[A](field).left.map(_ => s"missing or invalid $field")

  private def _require(condition: Boolean, message: String): Either[String, Unit] =
    if (condition) Right(()) else Left(message)

  private def _traverse[A, B](values: Vector[A])(f: A => Either[String, B]): Either[String, Vector[B]] =
    values.foldLeft[Either[String, Vector[B]]](Right(Vector.empty)) { (acc, value) =>
      for {
        previous <- acc
        next <- f(value)
      } yield previous :+ next
    }

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
