package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, WorkflowContinuation, WorkflowInteraction}

/** An application-owned Skill, Human, UI, or remote result adapter.
  * It translates one external submission into a typed result; it does not
  * own claim tokens, StateMachine routes, or UnitOfWork commits.
  */
trait ContinuationSpiAdapter[W, S, R] {
  def admitC(
    issued: WorkflowInteraction[W, Nothing],
    submission: S
  ): Consequence[ContinuationResult[R]]
}

object ContinuationSpiAdapter {
  /** Typed ComponentFactory assembly of one adapter and its durable guards. */
  final class Bound[W, S, R] private[workflow] (
    componentIdentity: ComponentId,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    adapter: ContinuationSpiAdapter[W, S, R]
  ) {
    /** A completed or unclaimed Continuation fails before the adapter runs. */
    def admitC(
      identity: ContinuationIdentity,
      submission: S
    ): Consequence[ContinuationResult[R]] =
      if (identity == null || submission == null)
        Consequence.stateConflict("Continuation SPI submission is incomplete")
      else for {
        loaded <- issuedPersistence.loadC(identity)
        issued <- loaded match {
          case Some(value) if value != null => Consequence.success(value)
          case _ => Consequence.stateConflict("issued WorkOrder is unavailable for external submission")
        }
        _ <- if (issued.handle != null && issued.handle.componentIdentity == componentIdentity)
               Consequence.unit
             else Consequence.stateConflict("external submission belongs to another Component")
        work <- issued.current match {
          case value: WorkflowContinuation.WorkOrder[?] if value.request != null &&
              value.request.continuationId == identity =>
            Consequence.success(value)
          case _ => Consequence.stateConflict("external submission does not match the issued WorkOrder")
        }
        claim <- runtime.recoverClaimC(identity)
        projected <- WorkflowProtocolV1.projectClaimedWorkOrderC(
          issued.handle, claim, work.request.input, work.request.completionOperation,
          work.requirement, work.presentation
        )
        _ <- if (projected == issued) Consequence.unit
             else Consequence.stateConflict("issued WorkOrder differs from the persisted Continuation")
        result <- adapter.admitC(issued, submission)
        _ <- WorkflowProtocolV1.admitResultC(issued, result)
      } yield result
  }

  def bindC[W, S, R](
    componentIdentity: ComponentId,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    adapter: ContinuationSpiAdapter[W, S, R]
  ): Consequence[Bound[W, S, R]] =
    if (componentIdentity == null || runtime == null || runtime == ContinuationRuntime.empty ||
        issuedPersistence == null || adapter == null)
      Consequence.configurationInvalid("Continuation SPI adapter binding is incomplete")
    else Consequence.success(new Bound(componentIdentity, runtime, issuedPersistence, adapter))
}
