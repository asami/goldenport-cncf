package org.goldenport.cncf.statemachine

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.{AdmittedJudgmentResult, AdmittedTypedJudgmentResultV1, AlternativeIdentity, JudgmentActionIdentity, TypedJudgmentResultV1}
import org.goldenport.cncf.workflow.{CandidateWorkflowProgression, ContinuationRuntime, IssuedWorkOrderPersistence, WorkflowInstancePersistence, WorkflowProtocolV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, WorkflowInteraction}

/*
 * @since   Sep. 23, 2026
 * @version Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
/** Pure StateMachine-owned selection of a transition target for an admitted result. */
final class CandidateAdmissionRouterException(
  val diagnostic: CandidateAdmissionRouter.Diagnostic
) extends IllegalArgumentException(diagnostic.render)

object CandidateAdmissionRouter {
  final case class Route(
    judgment: JudgmentActionIdentity,
    alternative: AlternativeIdentity,
    target: CmlStateMachineTransitionTarget
  )

  final case class RoutedResume(
    resumed: ContinuationRuntime.Resume,
    target: CmlStateMachineTransitionTarget
  )

  final case class AdvancedResume(
    resumed: ContinuationRuntime.Resume,
    target: CmlStateMachineTransitionTarget,
    instance: WorkflowInstancePersistence.InstanceRecord
  )

  enum DiagnosticCode(val value: String) {
    case MissingRoute extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-ROUTER-001")
    case AmbiguousRoute extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-ROUTER-002")
    case MissingTarget extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-ROUTER-003")
  }

  final case class Diagnostic(code: DiagnosticCode, data: Map[String, String]) {
    def render: String = {
      val details = data.toVector.sortBy(_._1).map { case (key, value) =>
        s"$key=$value"
      }.mkString(", ")
      s"${code.value}: $details"
    }
  }

  /**
   * Selects only an explicit StateMachine route.  No Provider, callback, or
   * effect is accepted by this API, so a worker cannot choose progression.
   */
  def routeC(
    result: AdmittedJudgmentResult,
    routes: Vector[Route]
  ): Consequence[CmlStateMachineTransitionTarget] = {
    val selected = routes.filter(route =>
      route.judgment == result.judgment &&
        route.alternative == result.selectedAlternative
    )
    selected match {
      case Vector(Route(_, _, null)) =>
        _failure(Diagnostic(DiagnosticCode.MissingTarget, _route_data(result)))
      case Vector(Route(_, _, target)) => Consequence.success(target)
      case Vector() => _failure(Diagnostic(DiagnosticCode.MissingRoute, _route_data(result)))
      case _ => _failure(Diagnostic(DiagnosticCode.AmbiguousRoute, _route_data(result)))
    }
  }

  def routeTypedC(
    result: AdmittedTypedJudgmentResultV1,
    routes: Vector[Route]
  ): Consequence[CmlStateMachineTransitionTarget] =
    routeC(result.result, routes)

  /** Bind a separate-turn result to its issued WorkOrder before selecting a StateMachine route.
    * This pure admission runs before one-shot Continuation resume; the worker supplies no target.
    */
  def routeSubmittedC[W](
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[W, Nothing],
    submitted: ContinuationResult[TypedJudgmentResultV1],
    routes: Vector[Route]
  ): Consequence[CmlStateMachineTransitionTarget] =
    if (artifact == null || submitted == null || submitted.result == null ||
        submitted.result.value == null || routes == null)
      Consequence.stateConflict("Judgment result routing requires a complete submitted result and routes")
    else for {
      _ <- org.goldenport.cncf.workflow.WorkflowProtocolV1.admitResultC(issued, submitted)
      admitted <- CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(artifact, submitted.result.value)
      _ <- if (submitted.result.typeIdentity == admitted.payloadType.value) Consequence.unit
           else Consequence.stateConflict("Judgment payload type differs from the submitted result type")
      _ <- if (submitted.resultReference == admitted.payload) Consequence.unit
           else Consequence.stateConflict("Judgment payload reference differs from the submitted result reference")
      _ <- _admit_work_order_binding(artifact, issued, admitted)
      target <- routeTypedC(admitted, routes)
    } yield target

  /** Read the exact issued WorkOrder, select its declared route, then consume its one-shot claim.
    * The returned target is not applied to WorkflowInstance progression by this method.
    */
  def resumePersistedSubmittedC[W](
    componentIdentity: ComponentId,
    artifact: CandidateAdmissionProducerAbi.Artifact,
    submitted: ContinuationResult[TypedJudgmentResultV1],
    routes: Vector[Route],
    issuedPersistence: IssuedWorkOrderPersistence[W],
    runtime: ContinuationRuntime,
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => UnitOfWork
  ): Consequence[RoutedResume] =
    if (submitted == null || submitted.continuationId == null || issuedPersistence == null)
      Consequence.stateConflict("Judgment result has no issued WorkOrder lookup")
    else for {
      loaded <- issuedPersistence.loadC(submitted.continuationId)
      issued <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("issued Judgment WorkOrder is unavailable for resume")
      }
      target <- routeSubmittedC(artifact, issued, submitted, routes)
      resumed <- WorkflowProtocolV1.resumeIssuedWithInstanceGuardC(
        componentIdentity, issued, submitted, runtime, instancePersistence, configuration, freshUnitOfWork
      )
    } yield RoutedResume(resumed, target)

  /** Loose post-resume progression for the real Candidate-Admission fixture.
    * Target/history admission precedes claim consumption. The continuation
    * resume and WorkflowInstance append are separate durable operations;
    * their shared atomicity is deliberately outside this Phase 77.1 path.
    */
  def resumeAndAdvancePersistedSubmittedC[W](
    componentIdentity: ComponentId,
    artifact: CandidateAdmissionProducerAbi.Artifact,
    submitted: ContinuationResult[TypedJudgmentResultV1],
    routes: Vector[Route],
    issuedPersistence: IssuedWorkOrderPersistence[W],
    runtime: ContinuationRuntime,
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => UnitOfWork
  ): Consequence[AdvancedResume] =
    if (componentIdentity == null || submitted == null || submitted.continuationId == null ||
        issuedPersistence == null || runtime == null || instancePersistence == null ||
        configuration == null || freshUnitOfWork == null)
      Consequence.stateConflict("Judgment progression requires complete persistence and runtime inputs")
    else for {
      loaded <- issuedPersistence.loadC(submitted.continuationId)
      issued <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("issued Judgment WorkOrder is unavailable for progression")
      }
      target <- routeSubmittedC(artifact, issued, submitted, routes)
      admittedConfiguration <- configuration.validateC
      stored <- instancePersistence.load(admittedConfiguration, issued.handle.instanceIdentity)
      current <- stored match {
        case Some(value) if value != null => value.validateC
        case _ => Consequence.stateConflict("WorkflowInstance is unavailable for Judgment progression")
      }
      next <- CandidateWorkflowProgression.nextC(current, submitted, target)
      resumed <- WorkflowProtocolV1.resumeIssuedWithInstanceGuardC(
        componentIdentity, issued, submitted, runtime, instancePersistence,
        admittedConfiguration, freshUnitOfWork
      )
      persisted <- instancePersistence.append(
        admittedConfiguration, current.identity, current.revision, next.history.last
      )
      _ <- if (persisted == next) Consequence.unit
           else Consequence.stateConflict("WorkflowInstance append returned divergent Judgment progression")
    } yield AdvancedResume(resumed, target, persisted)

  private def _admit_work_order_binding[W](
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[W, Nothing],
    admitted: AdmittedTypedJudgmentResultV1
  ): Consequence[Unit] = issued.current match {
    case work: org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[?] =>
      val request = work.request
      val declared = artifact.models.iterator.filter { producer =>
        producer.model.workflow.exists(workflow =>
          workflow.identity.value == issued.handle.workflowIdentity.value &&
            workflow.version.value == issued.handle.workflowRevision.value
        )
      }.flatMap { producer =>
        producer.judgments.iterator.filter(_.identity == admitted.result.judgment).map { judgment =>
          val required = producer.requiredSpi.exists(spi =>
            Option(request.requiredOperation).exists(identity => spi.identity.value == identity.capability) &&
              spi.actionIdentity == judgment.identity && spi.operation == judgment.operation
          )
          required && request.operation != null &&
            judgment.operation.service.value == request.operation.service &&
            judgment.operation.name.value == request.operation.operation
        }
      }.toVector
      if (declared == Vector(true)) Consequence.unit
      else Consequence.stateConflict("Judgment declaration does not match the issued WorkOrder")
    case _ => Consequence.stateConflict("Judgment result requires an issued WorkOrder")
  }

  private def _route_data(result: AdmittedJudgmentResult): Map[String, String] =
    Map(
      "alternative" -> result.selectedAlternative.value,
      "judgment" -> result.judgment.value
    )

  private def _failure[A](diagnostic: Diagnostic): Consequence[A] =
    Consequence.Failure(Conclusion.from(new CandidateAdmissionRouterException(diagnostic)))
}
