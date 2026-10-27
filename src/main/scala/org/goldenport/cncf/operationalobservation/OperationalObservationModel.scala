package org.goldenport.cncf.operationalobservation

import java.time.Instant
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.Consequence

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
enum ObservationAxis(val name: String) {
  case Deployment extends ObservationAxis("deployment")
  case Health extends ObservationAxis("health")
  case Readiness extends ObservationAxis("readiness")
  case Reachability extends ObservationAxis("reachability")
}

enum ObservationReason(val name: String) {
  case SourceNotConfigured extends ObservationReason("source-not-configured")
  case SourceUnavailable extends ObservationReason("source-unavailable")
  case PermissionDenied extends ObservationReason("permission-denied")
  case Timeout extends ObservationReason("timeout")
  case InvalidSource extends ObservationReason("invalid-source")
  case Stale extends ObservationReason("stale")
  case AmbiguousTarget extends ObservationReason("ambiguous-target")
  case Unsupported extends ObservationReason("unsupported")
  case BudgetExhausted extends ObservationReason("budget-exhausted")
}

sealed trait AxisObservation {
  def toRecord: Record
  def currentAt(now: Instant): AxisObservation = this match {
    case x: AxisObservation.Observed if x.observedAt.isAfter(now) ||
        !x.expiresAt.isAfter(now) || !x.expiresAt.isAfter(x.observedAt) =>
      AxisObservation.Unavailable(ObservationReason.Stale, Some(x.sourceId))
    case _ => this
  }
}
object AxisObservation {
  final case class Observed(
    value: String, sourceId: String, observedAt: Instant, expiresAt: Instant, scope: Option[String] = None
  ) extends AxisObservation {
    def toRecord: Record = Record.data(
      "availability" -> "observed", "value" -> value, "sourceId" -> sourceId,
      "observedAt" -> observedAt.toString, "expiresAt" -> expiresAt.toString
    ) ++ Record.create(scope.toVector.map("scope" -> _))
  }
  final case class Unavailable(reason: ObservationReason, sourceId: Option[String] = None) extends AxisObservation {
    def toRecord: Record = Record.data("availability" -> "unavailable", "reasonCode" -> reason.name) ++
      Record.create(sourceId.toVector.map("sourceId" -> _))
  }
  case object NotApplicable extends AxisObservation {
    def toRecord: Record = Record.data("availability" -> "not-applicable")
  }
}

final case class TargetObservation(target: ObservationTarget, axes: Map[ObservationAxis, AxisObservation]) {
  def toRecord: Record = Record.data(
    "targetId" -> target.targetId, "service" -> target.service, "instance" -> target.instance,
    "kind" -> target.kind, "enabled" -> target.enabled,
    "observations" -> Record.create(ObservationAxis.values.toVector.map(axis => axis.name -> axes(axis).toRecord))
  ) ++ Record.create(target.runtimeAssociation.toVector.map(id => "runtimeInstanceId" -> id.canonicalKey))
}

final case class HealthObservationContract(sourceId: String, scope: String, values: Vector[String]) {
  def toRecord: Record = Record.data("sourceId" -> sourceId, "scope" -> scope, "values" -> values)
}

final case class OperationalObservationSnapshot(
  observedAt: Instant, componentId: String, instanceId: String, profile: Option[String],
  total: Int, data: Vector[TargetObservation], healthContracts: Vector[HealthObservationContract] = Vector.empty
) {
  def toRecord: Record = Record.data(
    "protocolVersion" -> 1, "observedAt" -> observedAt.toString,
    "producer" -> Record.data("componentId" -> componentId, "instanceId" -> instanceId),
    "configuration" -> (Record.data("state" -> profile.fold("not-configured")(_ => "configured")) ++
      Record.create(profile.toVector.map("profile" -> _)) ++
      Record.create(profile.toVector.map(_ => "healthContracts" -> healthContracts.map(_.toRecord)))),
    "total" -> total, "returned" -> data.size, "truncated" -> (data.size < total),
    "data" -> data.map(_.toRecord)
  )
  def json: String = RecordEncoder.json(toRecord)
  def jsonC: Consequence[String] = RecordEncoder.jsonC(toRecord)
}
