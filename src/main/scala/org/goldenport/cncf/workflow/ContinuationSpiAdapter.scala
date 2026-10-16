package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, WorkflowInteraction}

/** An application-owned Skill, Human, UI, or remote result adapter.
  * It translates one external submission into a typed result; it does not
  * own claim tokens, StateMachine routes, or UnitOfWork commits.
  * @since   Sep. 25, 2026
  * @version Oct.  9, 2026
  */
trait ContinuationSpiAdapter[W, S, R] {
  def admitC(
    issued: WorkflowInteraction[W, Nothing],
    submission: S
  ): Consequence[ContinuationResult[R]]
}

object ContinuationSpiAdapter {
  /** Component-scoped input access for translation, not runtime completion admission. */
  final class Bound[W, S, R] private[workflow] (
    componentIdentity: ComponentId,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    adapter: ContinuationSpiAdapter[W, S, R]
  ) {
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
        // The Component-bound reader must not disclose another Component's input
        // to the callback. Completion/claim checks do not protect that earlier read.
        _ <- if (issued.handle != null && issued.handle.componentIdentity == componentIdentity)
          Consequence.unit
        else Consequence.stateConflict("external submission belongs to another Component")
        result <- adapter.admitC(issued, submission)
      } yield result
  }

  def bindC[W, S, R](
    componentIdentity: ComponentId,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    adapter: ContinuationSpiAdapter[W, S, R]
  ): Consequence[Bound[W, S, R]] =
    if (componentIdentity == null || issuedPersistence == null || adapter == null)
      Consequence.configurationInvalid("Continuation SPI adapter binding is incomplete")
    else Consequence.success(new Bound(componentIdentity, issuedPersistence, adapter))

  /** Source-compatible assembly; the runtime is consumed by completion, not conversion. */
  def bindC[W, S, R](
    componentIdentity: ComponentId,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    adapter: ContinuationSpiAdapter[W, S, R]
  ): Consequence[Bound[W, S, R]] =
    bindC(componentIdentity, issuedPersistence, adapter)
}
