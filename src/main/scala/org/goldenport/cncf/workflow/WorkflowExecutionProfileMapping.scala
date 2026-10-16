package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Invocation-time operational mapping, never persisted Workflow semantics.
  * Executor descriptors must be observed by the owning runtime, not supplied
  * by an AI semantic result. Resolution is not execution or permission.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final case class WorkflowConcreteExecutionProfile(
  identity: String, provider: String, model: String, controls: Map[String, String]
) {
  def validateC: Consequence[WorkflowConcreteExecutionProfile] =
    if (!Vector(identity, provider, model).forall(WorkflowExecutionProfileMapping.name) || controls == null ||
        !controls.forall((key, value) => WorkflowExecutionProfileMapping.name(key) && WorkflowExecutionProfileMapping.name(value)))
      Consequence.configurationInvalid("Workflow concrete execution profile is incomplete")
    else Consequence.success(this)

  def matches(that: WorkflowConcreteExecutionProfile): Boolean =
    that != null && provider == that.provider && model == that.model && controls == that.controls
}

final case class WorkflowExecutorDescriptor(contextIdentity: String, profile: WorkflowConcreteExecutionProfile) {
  def validateC: Consequence[WorkflowExecutorDescriptor] =
    if (!WorkflowExecutionProfileMapping.name(contextIdentity) || profile == null)
      Consequence.stateConflict("Workflow executor descriptor is incomplete")
    else profile.validateC.map(_ => this)
}

final case class WorkflowExecutionPreference(forceInline: Boolean = false, isolateContext: Boolean = false)

final case class WorkflowResolvedExecution(
  requirement: ExecutionRequirement,
  profile: WorkflowConcreteExecutionProfile,
  mappingIdentity: String,
  mappingRevision: String
) {
  /** Called with an actual runtime descriptor after execution, not AI claims. */
  def observeC(executor: WorkflowExecutorDescriptor): Consequence[ExecutionObservation] =
    if (executor == null) Consequence.stateConflict("Workflow execution observation has no actual executor")
    else executor.validateC.flatMap { actual =>
      if (!profile.matches(actual.profile)) Consequence.stateConflict("Workflow executor differs from its resolved concrete profile")
      else requirement.disposition match {
        case Some(disposition) => ExecutionObservation(disposition.placement, disposition.originContextIdentity,
          actual.contextIdentity, actual.profile.identity).admitC(requirement)
        case None => Consequence.stateConflict("Resolved Workflow execution has no disposition")
      }
    }
}

final class WorkflowExecutionProfileMapping private (
  val identity: String,
  val revision: String,
  private val _profiles: Map[String, WorkflowConcreteExecutionProfile],
  private val _classes: Map[String, String]
) {
  def resolveClassC(reasoningClass: ReasoningClassReference): Consequence[WorkflowConcreteExecutionProfile] =
    if (reasoningClass == null || !WorkflowExecutionProfileMapping.name(reasoningClass.value))
      Consequence.configurationInvalid("Workflow execution requires a semantic ReasoningClass")
    else _classes.get(reasoningClass.value).flatMap(_profiles.get).map(Consequence.success).getOrElse(
      Consequence.configurationInvalid("Workflow ReasoningClass has no configured concrete execution profile: " + reasoningClass.value))

  /** Resolve before the owning runtime issues/binds the execution requirement.
    * A retained issued request/result must not be rerouted by a later mapping.
    */
  def resolveC(requirement: ExecutionRequirement, current: WorkflowExecutorDescriptor,
    preference: WorkflowExecutionPreference = WorkflowExecutionPreference()): Consequence[WorkflowResolvedExecution] =
    if (requirement == null || current == null || preference == null)
      Consequence.stateConflict("Workflow execution resolution is incomplete")
    else for {
      requested <- requirement.validateC
      actual <- current.validateC
      configured <- requested.reasoningClass.map(resolveClassC).getOrElse(
        Consequence.configurationInvalid("Workflow execution requires a semantic ReasoningClass"))
      _ <- if (requested.disposition.isEmpty) Consequence.unit else
        Consequence.stateConflict("Already issued Workflow disposition must not be resolved again")
      _ <- if (!(preference.forceInline && (requested.reviewRequired || preference.isolateContext))) Consequence.unit else
        Consequence.stateConflict("Forced inline execution contradicts independent review or context isolation")
      placement = if (!requested.reviewRequired && !preference.isolateContext &&
          (preference.forceInline || configured.matches(actual.profile))) ExecutionPlacement.Inline else ExecutionPlacement.Delegated
      independence = if (requested.reviewRequired) ExecutionIndependence.Required else ExecutionIndependence.Optional
      selected = if (placement == ExecutionPlacement.Inline) actual.profile else configured
      resolved = requested.copy(disposition = Some(ExecutionDisposition(placement, independence, actual.contextIdentity)))
    } yield WorkflowResolvedExecution(resolved, selected, identity, revision)
}

object WorkflowExecutionProfileMapping {
  final case class ClassBinding(reasoningClass: String, profile: String)

  def createC(identity: String, revision: String, profiles: Vector[WorkflowConcreteExecutionProfile],
    classes: Vector[ClassBinding]): Consequence[WorkflowExecutionProfileMapping] =
    if (!name(identity) || !name(revision) || profiles == null || profiles.isEmpty || classes == null || classes.isEmpty ||
        profiles.exists(value => value == null || value.validateC.toOption.isEmpty) ||
        classes.exists(value => value == null || !name(value.reasoningClass) || !name(value.profile)) ||
        profiles.map(_.identity).distinct.size != profiles.size ||
        classes.map(_.reasoningClass).distinct.size != classes.size ||
        !classes.forall(value => profiles.exists(_.identity == value.profile)))
      Consequence.configurationInvalid("Workflow execution profile mapping is missing, ambiguous or references an absent profile")
    else Consequence.success(new WorkflowExecutionProfileMapping(identity, revision,
      profiles.map(value => value.identity -> value).toMap, classes.map(value => value.reasoningClass -> value.profile).toMap))

  private[workflow] def name(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
