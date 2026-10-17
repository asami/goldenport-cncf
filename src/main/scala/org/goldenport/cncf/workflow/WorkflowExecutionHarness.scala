package org.goldenport.cncf.workflow

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Finite, pure host handoff. The host supplies actual observations and retains
  * the session; this library invokes no provider and grants no authority.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
object WorkflowExecutionHarness {
  val schemaVersion = "cncf.workflow-execution-harness.v1"

  final case class WorkReference(handle: WorkflowHandle, runId: StateMachineRunIdentity,
    continuationId: ContinuationIdentity, expectedRevision: StateMachineRevision) {
    def validateC: Consequence[WorkReference] =
      if (handle == null || runId == null || continuationId == null || expectedRevision == null ||
          !_name(runId.value) || !_name(continuationId.value) || !_name(expectedRevision.value))
        Consequence.stateConflict("Host execution requires its actual work reference")
      else handle.validateC.map(_ => this)
  }
  enum Status(val value: String) {
    case Completed extends Status("COMPLETED")
    case Unavailable extends Status("UNAVAILABLE")
    case Declined extends Status("DECLINED")
    case Blocked extends Status("BLOCKED")
    case Failed extends Status("FAILED")
  }
  enum Reason {
    case Completed, ProviderUnavailable, CapabilityDeclined, AuthorityBlocked, ResourceBlocked,
      SemanticFailure, UncertainExecution
  }
  final case class Resources(elapsedMillis: Option[Long] = None, inputTokens: Option[Long] = None,
    outputTokens: Option[Long] = None, monetaryCost: Option[BigDecimal] = None,
    currency: Option[String] = None, machineIdentity: Option[String] = None) {
    def validateC: Consequence[Resources] =
      if (elapsedMillis == null || inputTokens == null || outputTokens == null || monetaryCost == null ||
          currency == null || machineIdentity == null || Vector(elapsedMillis, inputTokens, outputTokens).exists(_.exists(_ < 0)) ||
          monetaryCost.exists(value => value == null || value < 0) || monetaryCost.isDefined != currency.isDefined ||
          currency.exists(x => !_name(x)) || machineIdentity.exists(x => !_name(x)))
        Consequence.stateConflict("Host resource observations must be nonnegative, with paired cost/currency")
      else Consequence.success(this)
  }
  final case class Selection(attempt: Int, profile: WorkflowConcreteExecutionProfile)
  final case class Outcome(status: Status, reason: Reason, detail: String,
    executor: Option[WorkflowExecutorDescriptor] = None, resources: Resources = Resources())
  final case class Attempt(selection: Selection, outcome: Outcome)

  final class Session private[WorkflowExecutionHarness] (
    val workReference: WorkReference, val requirement: ExecutionRequirement,
    val origin: WorkflowExecutorDescriptor, val primary: WorkflowConcreteExecutionProfile,
    val alternate: Option[WorkflowConcreteExecutionProfile], val mappingIdentity: String,
    val mappingRevision: String, val attempts: Vector[Attempt]
  ) {
    def nextSelection: Option[Selection] =
      if (attempts.isEmpty) Some(Selection(1, primary))
      else if (attempts.size == 1 && requirement.disposition.get.placement == ExecutionPlacement.Delegated &&
          Set(Status.Unavailable, Status.Declined)(attempts.head.outcome.status)) alternate.map(Selection(2, _))
      else None

    def recordC(selection: Selection, outcome: Outcome): Consequence[Session] =
      if (selection == null || !nextSelection.contains(selection))
        Consequence.stateConflict("Host observation does not consume the current finite selection")
      else _outcome_c(this, selection, outcome).map(_ => new Session(workReference, requirement, origin,
        primary, alternate, mappingIdentity, mappingRevision, attempts :+ Attempt(selection, outcome)))

    def completionObservationC: Consequence[ExecutionObservation] = attempts.lastOption match {
      case Some(Attempt(selection, outcome)) if outcome.status == Status.Completed =>
        WorkflowResolvedExecution(requirement, selection.profile, mappingIdentity, mappingRevision).observeC(outcome.executor.get)
      case _ => Consequence.stateConflict("Host execution has no final actual COMPLETED observation")
    }
  }

  def prepareIssuedJsonC(workReference: WorkReference, requirement: Json,
    origin: WorkflowExecutorDescriptor, mapping: WorkflowExecutionProfileMapping): Consequence[Session] =
    decodeIssuedRequirementC(requirement).flatMap(prepareIssuedC(workReference, _, origin, mapping))

  def decodeIssuedRequirementC(requirement: Json): Consequence[ExecutionRequirement] =
    Option(requirement).toRight("Host execution requires its issued requirement JSON")
      .flatMap(_issued_names).flatMap(WorkflowExecutionJsonV1.decodeRequirement)
      .fold(Consequence.stateConflict(_), _.validateC)

  private def _issued_names(value: Json): Either[String, Json] = for {
    fields <- _object(value)
    review <- _alias(fields, "reviewRequired", "review_required")
    reasoning <- _alias(review, "reasoningClass", "reasoning_class")
    normalized <- reasoning("disposition") match {
      case None => Right(reasoning)
      case Some(disposition) => for {
        nested <- _object(disposition)
        origin <- _alias(nested, "originContextIdentity", "origin_context_identity")
      } yield reasoning.add("disposition", Json.fromJsonObject(origin))
    }
  } yield Json.fromJsonObject(normalized)

  private def _alias(fields: JsonObject, declaration: String, wire: String): Either[String, JsonObject] =
    (fields(declaration), fields(wire)) match {
      case (Some(left), Some(right)) if left != right => Left("Contradictory issued names for " + declaration)
      case (Some(value), _) => Right(fields.remove(wire).add(declaration, value))
      case (None, Some(value)) => Right(fields.remove(wire).add(declaration, value))
      case _ => Right(fields)
    }

  def encodeSelectionC(selection: Selection): Consequence[String] =
    if (selection == null || !Set(1, 2)(selection.attempt) || selection.profile == null)
      Consequence.stateConflict("Host selection requires a finite ordinal and concrete profile")
    else selection.profile.validateC.map(_ => Json.obj("attempt" -> Json.fromInt(selection.attempt),
      "profile" -> _profile_json(selection.profile)).noSpaces)

  def decodeAttemptC(value: Json): Consequence[Attempt] =
    Option(value).toRight("Host attempt JSON is absent").flatMap(_decode_attempt)
      .fold(Consequence.stateConflict(_), Consequence.success)

  def prepareIssuedC(workReference: WorkReference, requirement: ExecutionRequirement,
    origin: WorkflowExecutorDescriptor, mapping: WorkflowExecutionProfileMapping): Consequence[Session] =
    if (mapping == null) Consequence.configurationInvalid("Host execution requires the original canonical mapping snapshot")
    else for {
      _ <- _issued_c(workReference, requirement, origin)
      semanticclass <- requirement.reasoningClass.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("Host execution requires the issued semantic class"))
      // Issued disposition is consumed unchanged: never resolveC issued work.
      selected <- if (requirement.disposition.get.placement == ExecutionPlacement.Inline)
        Consequence.success(origin.profile) else mapping.resolveClassC(semanticclass)
      alternate <- if (requirement.disposition.get.placement == ExecutionPlacement.Inline)
        Consequence.success(Option.empty[WorkflowConcreteExecutionProfile]) else mapping.fallbackClassC(semanticclass)
      session <- _session_c(workReference, requirement, origin, selected, alternate, mapping.identity, mapping.revision)
    } yield session

  private def _issued_c(work: WorkReference, requirement: ExecutionRequirement,
    origin: WorkflowExecutorDescriptor): Consequence[Unit] =
    if (work == null || requirement == null || origin == null)
      Consequence.stateConflict("Host execution is missing actual issued inputs")
    else for {
      _ <- work.validateC
      _ <- requirement.validateC
      _ <- origin.validateC
      disposition <- requirement.disposition.map(Consequence.success).getOrElse(
        Consequence.stateConflict("Host execution requires an already issued disposition"))
      _ <- if (disposition.originContextIdentity == origin.contextIdentity &&
          (disposition.placement != ExecutionPlacement.Inline || disposition.independence != ExecutionIndependence.Required) &&
          (!requirement.reviewRequired || (disposition.placement == ExecutionPlacement.Delegated &&
            disposition.independence == ExecutionIndependence.Required))) Consequence.unit
        else Consequence.stateConflict("Issued origin or independent review contradicts the actual host context")
      _ <- if (requirement.reasoningClass.nonEmpty) Consequence.unit
        else Consequence.stateConflict("Host execution requires an issued semantic class")
    } yield ()

  private def _session_c(work: WorkReference, requirement: ExecutionRequirement, origin: WorkflowExecutorDescriptor,
    primary: WorkflowConcreteExecutionProfile, alternate: Option[WorkflowConcreteExecutionProfile],
    mappingidentity: String, mappingrevision: String): Consequence[Session] = for {
    _ <- _issued_c(work, requirement, origin)
    _ <- if (primary != null && alternate != null && _name(mappingidentity) && _name(mappingrevision) &&
        primary.validateC.toOption.nonEmpty && alternate.forall(x => x != null && x.validateC.toOption.nonEmpty && !x.matches(primary)) &&
        (requirement.disposition.get.placement != ExecutionPlacement.Inline || (primary == origin.profile && alternate.isEmpty))) Consequence.unit
      else Consequence.stateConflict("Host session has contradictory selected profiles or mapping provenance")
  } yield new Session(work, requirement, origin, primary, alternate, mappingidentity, mappingrevision, Vector.empty)

  private def _outcome_c(session: Session, selection: Selection, outcome: Outcome): Consequence[Unit] =
    if (outcome == null || outcome.status == null || outcome.reason == null || outcome.detail == null || outcome.detail.trim.isEmpty ||
        outcome.executor == null || outcome.resources == null)
      Consequence.stateConflict("Host execution requires an honest actual outcome and detail")
    else {
      val legal = outcome.status match {
        case Status.Completed => outcome.reason == Reason.Completed && outcome.executor.nonEmpty
        case Status.Unavailable => outcome.reason == Reason.ProviderUnavailable && outcome.executor.isEmpty
        case Status.Declined => outcome.reason == Reason.CapabilityDeclined
        case Status.Blocked => Set(Reason.AuthorityBlocked, Reason.ResourceBlocked)(outcome.reason)
        case Status.Failed => Set(Reason.SemanticFailure, Reason.UncertainExecution)(outcome.reason)
      }
      for {
        _ <- if (legal) Consequence.unit else Consequence.stateConflict("Host status, reason and actual executor contradict")
        _ <- outcome.resources.validateC
        _ <- outcome.executor.fold(Consequence.unit)(actual =>
          WorkflowResolvedExecution(session.requirement, selection.profile, session.mappingIdentity, session.mappingRevision)
            .observeC(actual).map(_ => ()))
      } yield ()
    }

  def encodeC(session: Session): Consequence[String] =
    if (session == null) Consequence.stateConflict("Host execution session is absent")
    else Consequence.success(Json.obj(
      "schemaVersion" -> Json.fromString(schemaVersion),
      "workReference" -> _work_json(session.workReference),
      "requirement" -> WorkflowExecutionJsonV1.requirement(session.requirement),
      "origin" -> _executor_json(session.origin), "primary" -> _profile_json(session.primary),
      "alternate" -> session.alternate.fold(Json.Null)(_profile_json),
      "mappingIdentity" -> Json.fromString(session.mappingIdentity), "mappingRevision" -> Json.fromString(session.mappingRevision),
      "attempts" -> Json.fromValues(session.attempts.map { attempt =>
        val outcome = attempt.outcome
        val resources = outcome.resources
        Json.obj("attempt" -> Json.fromInt(attempt.selection.attempt), "profile" -> _profile_json(attempt.selection.profile),
          "status" -> Json.fromString(outcome.status.value), "reason" -> Json.fromString(outcome.reason.toString),
          "detail" -> Json.fromString(outcome.detail), "executor" -> outcome.executor.fold(Json.Null)(_executor_json),
          "resources" -> Json.obj("elapsedMillis" -> resources.elapsedMillis.fold(Json.Null)(Json.fromLong),
            "inputTokens" -> resources.inputTokens.fold(Json.Null)(Json.fromLong),
            "outputTokens" -> resources.outputTokens.fold(Json.Null)(Json.fromLong),
            "monetaryCost" -> resources.monetaryCost.fold(Json.Null)(Json.fromBigDecimal),
            "currency" -> resources.currency.fold(Json.Null)(Json.fromString),
            "machineIdentity" -> resources.machineIdentity.fold(Json.Null)(Json.fromString)))
      })).noSpaces)

  def decodeC(raw: String): Consequence[Session] =
    Option(raw).toRight("Host session JSON is absent").flatMap(text => io.circe.parser.parse(text).left.map(_.message))
      .flatMap(_decode).fold(Consequence.stateConflict(_), { decoded =>
        val (work, requirement, origin, primary, alternate, identity, revision, attempts) = decoded
        _session_c(work, requirement, origin, primary, alternate, identity, revision).flatMap { initial =>
          attempts.foldLeft(Consequence.success(initial)) { (prior, attempt) =>
            prior.flatMap(_.recordC(attempt.selection, attempt.outcome))
          }
        }
      })

  private def _decode(value: Json): Either[String, (WorkReference, ExecutionRequirement, WorkflowExecutorDescriptor,
    WorkflowConcreteExecutionProfile, Option[WorkflowConcreteExecutionProfile], String, String, Vector[Attempt])] = for {
    fields <- _object(value)
    schema <- _string(fields, "schemaVersion")
    _ <- Either.cond(schema == schemaVersion, (), "Unsupported host session schema")
    work <- _decode_work(fields("workReference").getOrElse(Json.Null))
    requirement <- WorkflowExecutionJsonV1.decodeRequirement(fields("requirement").getOrElse(Json.Null))
    origin <- _decode_executor(fields("origin").getOrElse(Json.Null))
    primary <- _decode_profile(fields("primary").getOrElse(Json.Null))
    alternate <- _optional(fields, "alternate", _decode_profile)
    identity <- _string(fields, "mappingIdentity")
    revision <- _string(fields, "mappingRevision")
    values <- fields("attempts").flatMap(_.asArray).toRight("Host attempts must be an array")
    _ <- Either.cond(values.size <= 2, (), "Host session exceeds two finite attempts")
    attempts <- values.foldLeft[Either[String, Vector[Attempt]]](Right(Vector.empty))((prior, value) =>
      for { accumulated <- prior; attempt <- _decode_attempt(value) } yield accumulated :+ attempt)
  } yield (work, requirement, origin, primary, alternate, identity, revision, attempts)

  private def _decode_attempt(value: Json): Either[String, Attempt] = for {
    fields <- _object(value)
    number <- fields("attempt").flatMap(_.asNumber).flatMap(_.toInt).toRight("Host attempt requires its ordinal")
    profile <- _decode_profile(fields("profile").getOrElse(Json.Null))
    statusname <- _string(fields, "status")
    status <- Status.values.find(_.value == statusname).toRight("Unknown host status")
    reasonname <- _string(fields, "reason")
    reason <- Reason.values.find(_.toString == reasonname).toRight("Unknown host reason")
    detail <- fields("detail").flatMap(_.asString).filter(_.trim.nonEmpty).toRight("Host outcome requires honest nonblank detail")
    executor <- _optional(fields, "executor", _decode_executor)
    resources <- _object(fields("resources").getOrElse(Json.Null))
    elapsed <- _optional(resources, "elapsedMillis", _long)
    input <- _optional(resources, "inputTokens", _long)
    output <- _optional(resources, "outputTokens", _long)
    cost <- _optional(resources, "monetaryCost", x => x.asNumber.flatMap(_.toBigDecimal).toRight("Host cost must be numeric"))
    currency <- _optional(resources, "currency", _text)
    machine <- _optional(resources, "machineIdentity", _text)
  } yield Attempt(Selection(number, profile), Outcome(status, reason, detail, executor, Resources(elapsed, input, output, cost, currency, machine)))

  private def _profile_json(value: WorkflowConcreteExecutionProfile): Json = Json.obj(
    "identity" -> Json.fromString(value.identity), "provider" -> Json.fromString(value.provider),
    "model" -> Json.fromString(value.model), "controls" -> Json.obj(value.controls.toVector.map((key, value) => key -> Json.fromString(value))*))
  private def _executor_json(value: WorkflowExecutorDescriptor): Json = Json.obj(
    "contextIdentity" -> Json.fromString(value.contextIdentity), "profile" -> _profile_json(value.profile))
  private def _work_json(value: WorkReference): Json = Json.obj("handle" -> Json.obj(
    "componentIdentity" -> Json.fromString(value.handle.componentIdentity.name),
    "workflowIdentity" -> Json.fromString(value.handle.workflowIdentity.value),
    "workflowRevision" -> Json.fromString(value.handle.workflowRevision.value),
    "instanceIdentity" -> Json.fromString(value.handle.instanceIdentity.value),
    "protocolVersion" -> Json.fromString(value.handle.protocolVersion)),
    "runId" -> Json.fromString(value.runId.value), "continuationId" -> Json.fromString(value.continuationId.value),
    "expectedRevision" -> Json.fromString(value.expectedRevision.value))
  private def _decode_work(value: Json): Either[String, WorkReference] = for {
    fields <- _object(value)
    handle <- _object(fields("handle").getOrElse(Json.Null))
    component <- _string(handle, "componentIdentity")
    workflow <- _string(handle, "workflowIdentity")
    version <- _string(handle, "workflowRevision")
    instance <- _string(handle, "instanceIdentity")
    protocol <- _string(handle, "protocolVersion")
    run <- _string(fields, "runId")
    continuation <- _string(fields, "continuationId")
    revision <- _string(fields, "expectedRevision")
  } yield WorkReference(WorkflowHandle(ComponentId(component), WorkflowDefinitionIdentity(workflow), WorkflowDefinitionRevision(version),
    InstanceIdentity(instance), protocol), StateMachineRunIdentity(run), ContinuationIdentity(continuation), StateMachineRevision(revision))
  private def _decode_profile(value: Json): Either[String, WorkflowConcreteExecutionProfile] = for {
    fields <- _object(value)
    identity <- _string(fields, "identity")
    provider <- _string(fields, "provider")
    model <- _string(fields, "model")
    controls <- fields("controls").toRight("Missing host profile controls").flatMap(_object)
    entries <- controls.toVector.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) { case (prior, (key, value)) =>
      for { accumulated <- prior; text <- _text(value) } yield accumulated + (key -> text)
    }
  } yield WorkflowConcreteExecutionProfile(identity, provider, model, entries)
  private def _decode_executor(value: Json): Either[String, WorkflowExecutorDescriptor] = for {
    fields <- _object(value)
    context <- _string(fields, "contextIdentity")
    profile <- _decode_profile(fields("profile").getOrElse(Json.Null))
  } yield WorkflowExecutorDescriptor(context, profile)
  private def _object(value: Json): Either[String, JsonObject] = value.asObject.toRight("Host artifact requires an object")
  private def _string(fields: JsonObject, key: String): Either[String, String] = fields(key).toRight("Missing host field: " + key).flatMap(_text)
  private def _text(value: Json): Either[String, String] = value.asString.filter(_name).toRight("Host field requires nonblank exact text")
  private def _long(value: Json): Either[String, Long] = value.asNumber.flatMap(_.toLong).toRight("Host metric must be an integer")
  private def _optional[A](fields: JsonObject, key: String, decode: Json => Either[String, A]): Either[String, Option[A]] =
    fields(key) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => decode(value).map(Some(_))
    }
  private def _name(value: String): Boolean = WorkflowExecutionProfileMapping.name(value)
}
