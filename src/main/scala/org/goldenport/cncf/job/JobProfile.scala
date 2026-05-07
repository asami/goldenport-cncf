package org.goldenport.cncf.job

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   May.  7, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
enum JobProfileOccurrence {
  case Required
  case Possible
  case Forbidden

  def print: String = this match {
    case Required => "required"
    case Possible => "possible"
    case Forbidden => "forbidden"
  }
}

object JobProfileOccurrence {
  def parse(value: String, path: String): Consequence[JobProfileOccurrence] =
    value.trim.toLowerCase(Locale.ROOT) match {
      case "required" => Consequence.success(JobProfileOccurrence.Required)
      case "possible" => Consequence.success(JobProfileOccurrence.Possible)
      case "forbidden" => Consequence.success(JobProfileOccurrence.Forbidden)
      case other => Consequence.argumentInvalid(s"$path must be required, possible, or forbidden: $other")
    }
}

final case class JobProfileReceiver(
  action: String,
  guard: Option[String] = None,
  occurrence: JobProfileOccurrence = JobProfileOccurrence.Required
) {
  def toRecord: Record =
    Record.dataAuto(
      "action" -> action,
      "guard" -> guard,
      "occurrence" -> occurrence.print
    )
}

final case class JobProfileEvent(
  event: String,
  occurrence: JobProfileOccurrence = JobProfileOccurrence.Required,
  receivers: Vector[JobProfileReceiver] = Vector.empty
) {
  def toRecord: Record =
    Record.dataAuto(
      "event" -> event,
      "occurrence" -> occurrence.print,
      "receivers" -> receivers.map(_.toRecord)
    )
}

final case class JobProfileChainNode(
  action: String,
  emits: Vector[JobProfileEvent] = Vector.empty
) {
  def toRecord: Record =
    Record.dataAuto(
      "action" -> action,
      "emits" -> emits.map(_.toRecord)
    )
}

final case class JobDeclaredProfile(
  expectedStatus: Option[JobStatus] = None,
  eventChain: Vector[JobProfileChainNode] = Vector.empty
) {
  def toRecord: Record =
    Record.dataAuto(
      "expectedStatus" -> expectedStatus.map(_.toString),
      "eventChain" -> eventChain.map(_.toRecord)
    )
}

final case class JobObservedProfile(
  status: JobStatus,
  actions: Vector[String],
  events: Vector[String],
  receivers: Vector[String],
  retryKind: String
) {
  def toRecord: Record =
    Record.dataAuto(
      "status" -> status.toString,
      "actions" -> actions,
      "events" -> events,
      "receivers" -> receivers,
      "retryKind" -> retryKind
    )
}

object JobObservedProfile {
  def from(model: JobQueryReadModel): JobObservedProfile = {
    val taskactions = model.tasks.tasks.flatMap(_task_action)
    val submittedaction = model.debug.parameters.get("jcl.target.action").toVector
    val events = model.lineage.eventName.toVector ++
      model.timeline.events.flatMap(e => Option(e.kind).filter(_.startsWith("event.")))
    val receivers = Vector(
      model.lineage.receptionRule.map(x => s"rule:$x"),
      model.lineage.receptionPolicy.map(x => s"policy:$x"),
      model.lineage.targetComponent.map(x => s"component:$x")
    ).flatten
    JobObservedProfile(
      status = model.status,
      actions = (submittedaction ++ taskactions).distinct,
      events = events.distinct,
      receivers = receivers.distinct,
      retryKind = model.retry.kind.print
    )
  }

  private def _task_action(task: JobTaskReadModel): Option[String] =
    Vector(
      for {
        c <- task.component
        s <- task.service
        o <- task.operation
      } yield s"$c.$s.$o",
      for {
        c <- task.component
        o <- task.operation
      } yield s"$c.$o",
      task.operation
    ).flatten.headOption
}

enum JobProfileDifferenceSeverity {
  case Info
  case Warning
  case Error

  def print: String = this match {
    case Info => "info"
    case Warning => "warning"
    case Error => "error"
  }
}

final case class JobProfileDifference(
  kind: String,
  severity: JobProfileDifferenceSeverity,
  message: String,
  declared: Option[String] = None,
  observed: Option[String] = None
) {
  def toRecord: Record =
    Record.dataAuto(
      "kind" -> kind,
      "severity" -> severity.print,
      "message" -> message,
      "declared" -> declared,
      "observed" -> observed
    )
}

final case class JobProfileComparison(
  jobId: JobId,
  declared: Option[JobDeclaredProfile],
  observed: JobObservedProfile,
  differences: Vector[JobProfileDifference]
) {
  def summarySeverity: String =
    if (differences.exists(_.severity == JobProfileDifferenceSeverity.Error))
      "error"
    else if (differences.exists(_.severity == JobProfileDifferenceSeverity.Warning))
      "warning"
    else
      "ok"

  def accepted: Boolean =
    !differences.exists(_.severity == JobProfileDifferenceSeverity.Error)

  def toRecord: Record =
    Record.data(
      "jobId" -> jobId.value,
      "summarySeverity" -> summarySeverity,
      "accepted" -> accepted,
      "declared" -> declared.map(_.toRecord).getOrElse(Record.empty),
      "observed" -> observed.toRecord,
      "differences" -> differences.map(_.toRecord)
    )
}

object JobProfileComparison {
  def compare(model: JobQueryReadModel): JobProfileComparison = {
    val declared = model.debug.declaredProfile
    val observed = JobObservedProfile.from(model)
    val diffs = declared.map(_compare(_, observed)).getOrElse(Vector(
      JobProfileDifference(
        kind = "missing-declaration",
        severity = JobProfileDifferenceSeverity.Info,
        message = "job has no declared JCL profile"
      )
    ))
    JobProfileComparison(model.jobId, declared, observed, diffs)
  }

  private def _compare(
    declared: JobDeclaredProfile,
    observed: JobObservedProfile
  ): Vector[JobProfileDifference] = {
    val statusdiff = declared.expectedStatus.toVector.flatMap { expected =>
      if (expected == observed.status)
        Vector.empty
      else
        Vector(JobProfileDifference(
          kind = "status-mismatch",
          severity = JobProfileDifferenceSeverity.Error,
          message = s"expected status ${expected.toString} but observed ${observed.status.toString}",
          declared = Some(expected.toString),
          observed = Some(observed.status.toString)
        ))
    }
    val actiondiffs = declared.eventChain.flatMap { node =>
      _required_action_difference(node.action, observed.actions)
    }
    val eventdiffs = declared.eventChain.flatMap { node =>
      node.emits.flatMap { event =>
        _event_difference(event, observed.events) ++
          event.receivers.flatMap(receiver => _receiver_difference(event, receiver, observed.actions, observed.receivers))
      }
    }
    val undeclared = observed.actions.filterNot(a => _declared_actions(declared).exists(_matches_action(_, a))).map { action =>
      JobProfileDifference(
        kind = "runtime-extension",
        severity = JobProfileDifferenceSeverity.Info,
        message = s"observed undeclared runtime action: $action",
        observed = Some(action)
      )
    }
    statusdiff ++ actiondiffs ++ eventdiffs ++ undeclared
  }

  private def _declared_actions(declared: JobDeclaredProfile): Vector[String] =
    declared.eventChain.flatMap(node => node.action +: node.emits.flatMap(_.receivers.map(_.action)))

  private def _required_action_difference(
    action: String,
    observed: Vector[String]
  ): Vector[JobProfileDifference] =
    if (observed.exists(_matches_action(action, _)))
      Vector.empty
    else
      Vector(JobProfileDifference(
        kind = "missing-action",
        severity = JobProfileDifferenceSeverity.Warning,
        message = s"declared action was not observed: $action",
        declared = Some(action)
      ))

  private def _event_difference(
    event: JobProfileEvent,
    observed: Vector[String]
  ): Vector[JobProfileDifference] = {
    val seen = observed.exists(_matches_name(event.event, _))
    event.occurrence match {
      case JobProfileOccurrence.Required if !seen =>
        Vector(JobProfileDifference(
          kind = "missing-event",
          severity = JobProfileDifferenceSeverity.Warning,
          message = s"required event was not observed: ${event.event}",
          declared = Some(event.event)
        ))
      case JobProfileOccurrence.Forbidden if seen =>
        Vector(JobProfileDifference(
          kind = "forbidden-event-observed",
          severity = JobProfileDifferenceSeverity.Error,
          message = s"forbidden event was observed: ${event.event}",
          declared = Some(event.event),
          observed = Some(event.event)
        ))
      case _ =>
        Vector.empty
    }
  }

  private def _receiver_difference(
    event: JobProfileEvent,
    receiver: JobProfileReceiver,
    observedActions: Vector[String],
    observedReceivers: Vector[String]
  ): Vector[JobProfileDifference] = {
    val seen = observedActions.exists(_matches_action(receiver.action, _)) ||
      observedReceivers.exists(_matches_name(receiver.action, _))
    receiver.occurrence match {
      case JobProfileOccurrence.Required if !seen =>
        Vector(JobProfileDifference(
          kind = "missing-receiver",
          severity = JobProfileDifferenceSeverity.Warning,
          message = s"required receiver was not observed for ${event.event}: ${receiver.action}",
          declared = Some(receiver.action)
        ))
      case JobProfileOccurrence.Forbidden if seen =>
        Vector(JobProfileDifference(
          kind = "forbidden-receiver-observed",
          severity = JobProfileDifferenceSeverity.Error,
          message = s"forbidden receiver was observed for ${event.event}: ${receiver.action}",
          declared = Some(receiver.action),
          observed = Some(receiver.action)
        ))
      case _ =>
        Vector.empty
    }
  }

  private def _matches_action(declared: String, observed: String): Boolean =
    declared == observed || observed.endsWith(s".$declared") || declared.endsWith(s".$observed")

  private def _matches_name(declared: String, observed: String): Boolean =
    declared == observed || observed.endsWith(s".$declared") || declared.endsWith(s".$observed")
}

object JobProfileReconstructor {
  def reconstruct(model: JobQueryReadModel): JobDefinition = {
    val observed = JobObservedProfile.from(model)
    val action = model.debug.parameters.get("jcl.target.action")
      .orElse(observed.actions.headOption)
      .getOrElse("unknown")
    val profile = JobDeclaredProfile(
      expectedStatus = Some(model.status),
      eventChain = Vector(
        JobProfileChainNode(
          action = action,
          emits = observed.events.map { event =>
            JobProfileEvent(
              event = event,
              occurrence = JobProfileOccurrence.Possible,
              receivers = observed.receivers.map { receiver =>
                JobProfileReceiver(
                  action = receiver,
                  occurrence = JobProfileOccurrence.Possible
                )
              }
            )
          }
        )
      )
    )
    JobDefinition(
      name = model.debug.requestSummary.getOrElse(model.jobId.value),
      target = JobTarget(action = Some(action)),
      parameters = model.debug.parameters.filterNot(_._1.startsWith("jcl.")),
      submit = JobSubmitSpec(
        persistence = model.persistence,
        requestSummary = model.debug.requestSummary
      ),
      profile = Some(profile)
    )
  }
}
