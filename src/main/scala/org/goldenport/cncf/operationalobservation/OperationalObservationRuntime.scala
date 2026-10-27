package org.goldenport.cncf.operationalobservation

import java.nio.charset.StandardCharsets
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import scala.util.control.NonFatal

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
/** Immutable host-bound capability. It owns no driver, polling store, timer or lifecycle. */
private[cncf] final class OperationalObservationRuntime private (
  val plan: Option[OperationalObservationPlan]
) {
  def readC(
    limit: Int, context: ExecutionContext, effects: UnitOfWorkOp ~> Consequence
  ): Consequence[OperationalObservationSnapshot] = {
    val started = System.nanoTime()
    for {
      _ <- OperationalObservationRuntime.authorizeC(context)
      _ <- if (limit >= 1 && limit <= 100) Consequence.unit
        else Consequence.operationInvalid("operationalObservations limit must be an integer from 1 to 100")
      owner <- OperationalObservationRuntime.component(context.cncfCore.scope)
        .fold[Consequence[Component]](Consequence.operationInvalid("operationalObservations requires an actual Component context"))(Consequence.success)
      _ <- plan.fold(Consequence.unit) { selected =>
        selected.validateC.flatMap { _ =>
          if (!selected.owners.contains(owner.componentId))
            Consequence.securityPermissionDenied("Operational observation capability is not granted to this Component")
          else {
            val instances = owner.subsystem.map(_.components.map(_.instanceId).toSet).getOrElse(Set(owner.instanceId))
            if (selected.targets.flatMap(_.runtimeAssociation).forall(instances.contains)) Consequence.unit
            else Consequence.configurationInvalid("Operational observation runtime association is not assembled")
          }
        }
      }
      snapshot <- _read_c(owner, limit, context, effects, started)
    } yield snapshot
  }

  private def _read_c(
    owner: Component, limit: Int, context: ExecutionContext,
    effects: UnitOfWorkOp ~> Consequence, started: Long
  ): Consequence[OperationalObservationSnapshot] = {
    val selectedtargets = plan.toVector.flatMap(_.targets).sortBy(_.targetId).take(limit)
    val selectedsources = selectedtargets.filter(_.enabled).flatMap(_.sources.values).distinct.sorted
    val bound = plan.map(_.limits).getOrElse(ObservationLimits())
    val sources = plan.toVector.flatMap(_.sources).map(x => x.sourceId -> x).toMap
    // Only selected sources are acquired, once each. A shared binding never discovers targets.
    val values = selectedsources.map { id =>
      val elapsed = (System.nanoTime() - started) / 1000000L
      val remaining = bound.operationMillis.toLong - elapsed
      val cancelled = Thread.currentThread().isInterrupted || context.jobContext.cancellationScope.exists(_.isCancelled)
      val value = if (remaining <= 0 || cancelled)
        AxisObservation.Unavailable(ObservationReason.BudgetExhausted, Some(id))
      else {
        val source = sources(id)
        val read = ObservationRead(context, math.min(bound.sourceMillis.toLong, remaining).toInt, bound.sourceBytes)
        try source match {
          case _: ObservationSource.Docker => DockerOperationalObservationProvider.readC(source, read, effects)
          case _: ObservationSource.Http => HttpOperationalObservationProvider.readC(source, read, effects)
        } catch {
          case NonFatal(_) => AxisObservation.Unavailable(ObservationReason.SourceUnavailable, Some(id))
        }
      }
      id -> value
    }.toMap
    val now = context.clock.instant()
    val data = selectedtargets.map { target =>
      val axes = ObservationAxis.values.toVector.map { axis =>
        val value = if (!target.enabled) AxisObservation.NotApplicable
          else target.sources.get(axis).fold[AxisObservation](
            AxisObservation.Unavailable(ObservationReason.SourceNotConfigured)
          )(id => values(id).currentAt(now))
        axis -> value
      }.toMap
      TargetObservation(target, axes)
    }
    val healthids = selectedtargets.filter(_.enabled).flatMap(_.sources.get(ObservationAxis.Health)).distinct.sorted
    val contracts = healthids.flatMap(id => sources.get(id).collect {
      case source: ObservationSource.Http if source.axis == ObservationAxis.Health =>
        HealthObservationContract(source.sourceId, source.scope, source.vocabulary.distinct.sorted)
    })
    val snapshot = OperationalObservationSnapshot(now, owner.componentId.name, owner.instanceId.canonicalKey,
      plan.map(_.profile), plan.fold(0)(_.targets.size), data, contracts)
    snapshot.jsonC match {
      case Consequence.Success(text) if text.getBytes(StandardCharsets.UTF_8).length <= bound.responseBytes =>
        Consequence.success(snapshot)
      case _ => Consequence.operationInvalid("Operational observation response cannot be encoded within its configured byte bound")
    }
  }
}

private[cncf] object OperationalObservationRuntime {
  val unconfigured: OperationalObservationRuntime = new OperationalObservationRuntime(None)
  def createC(plan: OperationalObservationPlan): Consequence[OperationalObservationRuntime] =
    plan.validateC.map(value => new OperationalObservationRuntime(Some(value)))

  def authorizeC(context: ExecutionContext): Consequence[Unit] = {
    given ExecutionContext = context
    val subject = SecuritySubject.current
    val administrative = Vector("application_content_manager", "content_manager", "content_admin", "operator", "system", "internal")
    val admitted = administrative.exists(x => subject.hasPrivilege(x) || subject.hasCapability(x) || subject.hasRole(x))
    if (!subject.isAuthenticated) Consequence.securityAuthenticationRequired("Operational observations require authentication")
    else if (!admitted) Consequence.securityPermissionDenied("Operational observations require administrative read access")
    else Consequence.unit
  }

  @annotation.tailrec
  def component(scope: ScopeContext): Option[Component] = scope match {
    case selected: Component.Context => Some(selected.component)
    case _ => scope.parent match {
      case Some(parent) => component(parent)
      case None => None
    }
  }
}
