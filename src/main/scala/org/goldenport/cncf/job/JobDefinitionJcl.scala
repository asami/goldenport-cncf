package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.record.Record

/** A bounded executable procedural step in a canonical single-job JCL. */
final case class JobFlowStep(
  id: String,
  action: String,
  parameters: Map[String, String] = Map.empty
) {
  def toRecord: Record =
    Record.dataAuto(
      "id" -> id,
      "action" -> action,
      "parameters" -> JobDefinitionRecordSupport.parameterRecords(parameters)
    )

  /** Stable source-compatible rendering used by JobDefinition snapshots. */
  def show: String = toRecord.show
}

final case class JobFlow(
  steps: Vector[JobFlowStep]
) {
  def toRecord: Record =
    Record.dataAuto("steps" -> steps.map(_.toRecord))

  /** Stable source-compatible rendering used by JobDefinition snapshots. */
  def show: String = toRecord.show
}

/** One deterministic Event emission declared by executable JCL. */
final case class JobEventEmission(
  id: String,
  after: String,
  name: String,
  kind: Option[String] = None,
  persistent: Option[Boolean] = None
) {
  def toRecord: Record =
    Record.dataAuto(
      "id" -> id,
      "after" -> after,
      "name" -> name,
      "kind" -> kind,
      "persistent" -> persistent
    )

  def show: String = toRecord.show
}

final case class JobEvents(
  emit: Vector[JobEventEmission]
) {
  def toRecord: Record =
    Record.dataAuto("emit" -> emit.map(_.toRecord))

  /** Stable source-compatible rendering used by JobDefinition snapshots. */
  def show: String = toRecord.show
}

/** A declared Event receiver. Compilation turns this into a same-Job continuation. */
final case class JobEventHandler(
  id: String,
  event: String,
  action: String,
  parameters: Map[String, String] = Map.empty
) {
  def toRecord: Record =
    Record.dataAuto(
      "id" -> id,
      "event" -> event,
      "action" -> action,
      "parameters" -> JobDefinitionRecordSupport.parameterRecords(parameters)
    )

  def show: String = toRecord.show
}

final case class JobOnEvent(
  handlers: Vector[JobEventHandler]
) {
  def toRecord: Record =
    Record.dataAuto("handlers" -> handlers.map(_.toRecord))

  /** Stable source-compatible rendering used by JobDefinition snapshots. */
  def show: String = toRecord.show
}

/** The explicit semantic value passed to a later runtime execution slice. */
final case class JobContinuation(
  id: String,
  event: String,
  action: String,
  parameters: Map[String, String] = Map.empty,
  sameJob: Boolean = true
) {
  def toRecord: Record =
    Record.dataAuto(
      "id" -> id,
      "event" -> event,
      "action" -> action,
      "parameters" -> JobDefinitionRecordSupport.parameterRecords(parameters),
      "sameJob" -> sameJob
    )
}

/** A fully accepted, but not yet executed, JCL semantic compilation. */
final case class JobSemanticPlan(
  rootAction: Option[String],
  flow: Option[JobFlow],
  events: Option[JobEvents],
  continuations: Vector[JobContinuation]
) {
  def steps: Vector[JobFlowStep] = flow.toVector.flatMap(_.steps)
  def emittedEvents: Vector[JobEventEmission] = events.toVector.flatMap(_.emit)
  def onEvent: Vector[JobContinuation] = continuations

  def toRecord: Record =
    Record.dataAuto(
      "rootAction" -> rootAction,
      "flow" -> flow.map(_.toRecord),
      "events" -> events.map(_.toRecord),
      "continuations" -> continuations.map(_.toRecord)
    )

  def show: String = toRecord.show
}

object JobSemanticCompiler {
  val MAX_FLOW_STEPS: Int = 32
  val MAX_EMITTED_EVENTS: Int = 32
  val MAX_EVENT_HANDLERS: Int = 32

  def compile(definition: JobDefinition): Consequence[JobSemanticPlan] = {
    val executable = definition.flow.nonEmpty || definition.events.nonEmpty || definition.onEvent.nonEmpty
    if (executable && definition.target.action.isEmpty)
      Consequence.argumentInvalid("executable JCL requires a canonical job target.action")
    else
      for {
        _ <- _validate_target(definition.target)
        _ <- _validate_flow(definition.flow)
        _ <- _validate_events(definition.flow, definition.events)
        _ <- _validate_handlers(definition.events, definition.onEvent)
      } yield JobSemanticPlan(
        rootAction = definition.target.action,
        flow = definition.flow,
        events = definition.events,
        continuations = definition.onEvent.toVector.flatMap(_.handlers).map { handler =>
          JobContinuation(
            id = handler.id,
            event = handler.event,
            action = handler.action,
            parameters = handler.parameters
          )
        }
      )
  }

  private def _validate_target(target: JobTarget): Consequence[Unit] =
    (target.action, target.workflow) match {
      case (Some(_), Some(_)) =>
        Consequence.argumentInvalid("JCL target must contain exactly one of action or workflow")
      case (None, None) =>
        Consequence.argumentInvalid("JCL target must contain exactly one of action or workflow")
      case (Some(action), None) if action.trim.nonEmpty =>
        Consequence.unit
      case (Some(_), None) =>
        Consequence.argumentInvalid("JCL target.action must be a non-empty string")
      case (None, Some(value))
          if value.definition.trim.nonEmpty && value.registration.trim.nonEmpty =>
        Consequence.unit
      case (None, Some(_)) =>
        Consequence.argumentInvalid("JCL workflow target requires non-empty definition and registration")
    }

  private def _validate_flow(flow: Option[JobFlow]): Consequence[Unit] =
    flow match {
      case None => Consequence.unit
      case Some(value) =>
        if (value.steps.exists(step => step.id.trim.isEmpty || step.action.trim.isEmpty))
          Consequence.argumentInvalid("flow steps require non-empty id and action")
        else if (value.steps.isEmpty)
          Consequence.argumentInvalid("flow.steps must not be empty")
        else if (value.steps.size > MAX_FLOW_STEPS)
          Consequence.argumentInvalid(s"flow.steps must contain at most $MAX_FLOW_STEPS steps")
        else if (value.steps.exists(_.id == "root"))
          Consequence.argumentInvalid("flow step id must not be root")
        else if (_duplicates(value.steps.map(_.id)))
          Consequence.argumentInvalid("flow step ids must be unique")
        else
          Consequence.unit
    }

  private def _validate_events(
    flow: Option[JobFlow],
    events: Option[JobEvents]
  ): Consequence[Unit] =
    events match {
      case None => Consequence.unit
      case Some(value) =>
        if (value.emit.exists(event => event.id.trim.isEmpty || event.after.trim.isEmpty || event.name.trim.isEmpty))
          Consequence.argumentInvalid("events.emit requires non-empty id, after, and name")
        else if (value.emit.isEmpty)
          Consequence.argumentInvalid("events.emit must not be empty")
        else if (value.emit.size > MAX_EMITTED_EVENTS)
          Consequence.argumentInvalid(s"events.emit must contain at most $MAX_EMITTED_EVENTS emissions")
        else if (_duplicates(value.emit.map(_.id)))
          Consequence.argumentInvalid("events.emit ids must be unique")
        else {
          val stepids = flow.toVector.flatMap(_.steps.map(_.id)).toSet
          val invalidafter = value.emit.filterNot(event => event.after == "root" || stepids.contains(event.after))
          if (invalidafter.nonEmpty)
            Consequence.argumentInvalid(
              s"events.emit.after references unknown flow step: ${invalidafter.head.after}"
            )
          else
            Consequence.unit
        }
    }

  private def _validate_handlers(
    events: Option[JobEvents],
    onEvent: Option[JobOnEvent]
  ): Consequence[Unit] =
    onEvent match {
      case None => Consequence.unit
      case Some(value) =>
        if (value.handlers.exists(handler => handler.id.trim.isEmpty || handler.event.trim.isEmpty || handler.action.trim.isEmpty))
          Consequence.argumentInvalid("onEvent.handlers requires non-empty id, event, and action")
        else if (value.handlers.isEmpty)
          Consequence.argumentInvalid("onEvent.handlers must not be empty")
        else if (value.handlers.size > MAX_EVENT_HANDLERS)
          Consequence.argumentInvalid(s"onEvent.handlers must contain at most $MAX_EVENT_HANDLERS handlers")
        else if (_duplicates(value.handlers.map(_.id)))
          Consequence.argumentInvalid("onEvent.handlers ids must be unique")
        else {
          val emitted = events.toVector.flatMap(_.emit)
          val invalidevents = value.handlers.filter(handler => emitted.count(_.name == handler.event) == 0)
          val ambiguousevents = value.handlers.filter(handler => emitted.count(_.name == handler.event) > 1)
          if (invalidevents.nonEmpty)
            Consequence.argumentInvalid(
              s"onEvent handler references unknown emitted event: ${invalidevents.head.event}"
            )
          else if (ambiguousevents.nonEmpty)
            Consequence.argumentInvalid(
              s"onEvent handler references ambiguous emitted event: ${ambiguousevents.head.event}"
            )
          else
            Consequence.unit
        }
    }

  private def _duplicates(values: Vector[String]): Boolean =
    values.distinct.size != values.size
}

private object JobDefinitionRecordSupport {
  def parameterRecords(parameters: Map[String, String]): Vector[Record] =
    parameters.toVector.sortBy(_._1).map { case (key, value) => Record.data(key -> value) }
}
