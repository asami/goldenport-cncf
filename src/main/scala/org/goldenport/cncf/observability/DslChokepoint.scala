package org.goldenport.cncf.observability

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DslChokepointContext(
  domain: String,
  operation: String,
  componentName: Option[String] = None,
  resourceName: Option[String] = None,
  targetId: Option[String] = None,
  commandName: Option[String] = None,
  attributes: Record = Record.empty
) {
  def label: String =
    s"dsl:${domain}.${operation}"

  def phaseLabel(phase: DslChokepointPhase): String =
    s"${label}.${phase.name}"

  def toRecord: Record =
    Record.dataAuto(
      "dsl.domain" -> domain,
      "dsl.operation" -> operation,
      "component" -> componentName,
      "resource" -> resourceName,
      "target_id" -> targetId,
      "command" -> commandName
    ) ++ attributes
}

final case class DslChokepointPhase(
  name: String
)

object DslChokepointPhase {
  val Authorization: DslChokepointPhase = DslChokepointPhase("authorization")
  val Resolve: DslChokepointPhase = DslChokepointPhase("resolve")
  val Query: DslChokepointPhase = DslChokepointPhase("query")
  val Method: DslChokepointPhase = DslChokepointPhase("method")
  val Persistence: DslChokepointPhase = DslChokepointPhase("persistence")
}

sealed trait DslChokepointOutcome {
  def name: String
}

object DslChokepointOutcome {
  case object Success extends DslChokepointOutcome {
    val name: String = "success"
  }

  case object Failure extends DslChokepointOutcome {
    val name: String = "failure"
  }
}

trait DslChokepointHook {
  def enter(ctx: DslChokepointContext)(using ExecutionContext): Unit = ()

  def phaseEnter(
    ctx: DslChokepointContext,
    phase: DslChokepointPhase
  )(using ExecutionContext): Unit = ()

  def phaseLeave(
    ctx: DslChokepointContext,
    phase: DslChokepointPhase,
    outcome: DslChokepointOutcome
  )(using ExecutionContext): Unit = ()

  def leave(
    ctx: DslChokepointContext,
    outcome: DslChokepointOutcome
  )(using ExecutionContext): Unit = ()
}

object DslChokepointHook {
  object CallTree extends DslChokepointHook {
    override def enter(ctx: DslChokepointContext)(using ec: ExecutionContext): Unit =
      ec.observability.callTreeContext.enter(ctx.label)

    override def phaseEnter(
      ctx: DslChokepointContext,
      phase: DslChokepointPhase
    )(using ec: ExecutionContext): Unit =
      ec.observability.callTreeContext.enter(ctx.phaseLabel(phase))

    override def phaseLeave(
      ctx: DslChokepointContext,
      phase: DslChokepointPhase,
      outcome: DslChokepointOutcome
    )(using ec: ExecutionContext): Unit =
      ec.observability.callTreeContext.leave()

    override def leave(
      ctx: DslChokepointContext,
      outcome: DslChokepointOutcome
    )(using ec: ExecutionContext): Unit =
      ec.observability.callTreeContext.leave()
  }

  object Observability extends DslChokepointHook {
    override def enter(ctx: DslChokepointContext)(using ec: ExecutionContext): Unit =
      ec.observability.emitDebug(
        ec.cncfCore.scope,
        s"${ctx.label}.enter",
        ctx.toRecord
      )

    override def phaseLeave(
      ctx: DslChokepointContext,
      phase: DslChokepointPhase,
      outcome: DslChokepointOutcome
    )(using ec: ExecutionContext): Unit =
      ec.observability.emitDebug(
        ec.cncfCore.scope,
        s"${ctx.phaseLabel(phase)}.${outcome.name}",
        ctx.toRecord ++ Record.dataAuto(
          "dsl.phase" -> phase.name,
          "dsl.outcome" -> outcome.name
        )
      )

    override def leave(
      ctx: DslChokepointContext,
      outcome: DslChokepointOutcome
    )(using ec: ExecutionContext): Unit =
      ec.observability.emitDebug(
        ec.cncfCore.scope,
        s"${ctx.label}.${outcome.name}",
        ctx.toRecord ++ Record.dataAuto("dsl.outcome" -> outcome.name)
      )
  }

  object Audit extends DslChokepointHook {
    override def phaseLeave(
      ctx: DslChokepointContext,
      phase: DslChokepointPhase,
      outcome: DslChokepointOutcome
    )(using ec: ExecutionContext): Unit =
      if (outcome == DslChokepointOutcome.Failure || phase == DslChokepointPhase.Persistence)
        ec.observability.emitInfo(
          ec.cncfCore.scope,
          s"dsl.audit.${ctx.domain}.${ctx.operation}.${phase.name}.${outcome.name}",
          ctx.toRecord ++ Record.dataAuto(
            "dsl.phase" -> phase.name,
            "dsl.outcome" -> outcome.name,
            "audit.kind" -> "dsl-chokepoint"
          )
        )

    override def leave(
      ctx: DslChokepointContext,
      outcome: DslChokepointOutcome
    )(using ec: ExecutionContext): Unit =
      if (outcome == DslChokepointOutcome.Failure)
        ec.observability.emitInfo(
          ec.cncfCore.scope,
          s"dsl.audit.${ctx.domain}.${ctx.operation}.${outcome.name}",
          ctx.toRecord ++ Record.dataAuto(
            "dsl.outcome" -> outcome.name,
            "audit.kind" -> "dsl-chokepoint"
          )
        )
  }

  object Metrics extends DslChokepointHook {
    override def leave(
      ctx: DslChokepointContext,
      outcome: DslChokepointOutcome
    )(using ec: ExecutionContext): Unit =
      RuntimeDashboardMetrics.recordDslChokepoint(outcome == DslChokepointOutcome.Failure)
  }
}

object DslChokepointRunner {
  private val _default_hooks: Vector[DslChokepointHook] =
    Vector(
      DslChokepointHook.CallTree,
      DslChokepointHook.Observability,
      DslChokepointHook.Audit,
      DslChokepointHook.Metrics
    )

  def defaultHooks: Vector[DslChokepointHook] =
    _default_hooks

  def run[A](
    context: DslChokepointContext,
    hooks: Option[Vector[DslChokepointHook]] = None
  )(
    body: => Consequence[A]
  )(using ec: ExecutionContext): Consequence[A] = {
    val activeHooks = _hooks(hooks)
    activeHooks.foreach(_.enter(context))
    val result =
      try {
        body
      } catch {
        case e: Throwable =>
          val r = Consequence.Failure(Conclusion.from(e))
          activeHooks.reverse.foreach(_.leave(context, DslChokepointOutcome.Failure))
          return r
      }
    val outcome = _outcome(result)
    activeHooks.reverse.foreach(_.leave(context, outcome))
    result
  }

  def phase[A](
    context: DslChokepointContext,
    phase: DslChokepointPhase,
    hooks: Option[Vector[DslChokepointHook]] = None
  )(
    body: => Consequence[A]
  )(using ec: ExecutionContext): Consequence[A] = {
    val activeHooks = _hooks(hooks)
    activeHooks.foreach(_.phaseEnter(context, phase))
    val result =
      try {
        body
      } catch {
        case e: Throwable =>
          val r = Consequence.Failure(Conclusion.from(e))
          activeHooks.reverse.foreach(_.phaseLeave(context, phase, DslChokepointOutcome.Failure))
          return r
      }
    activeHooks.reverse.foreach(_.phaseLeave(context, phase, _outcome(result)))
    result
  }

  private def _hooks(
    explicit: Option[Vector[DslChokepointHook]]
  )(using ec: ExecutionContext): Vector[DslChokepointHook] =
    explicit
      .orElse(ec.framework.dslChokepointHooks)
      .getOrElse(_default_hooks)

  private def _outcome[A](
    result: Consequence[A]
  ): DslChokepointOutcome =
    result match {
      case Consequence.Success(_) => DslChokepointOutcome.Success
      case Consequence.Failure(_) => DslChokepointOutcome.Failure
    }
}
