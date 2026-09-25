package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionEvaluation, AdmissionGap, RequirementKind}

/** Maps every unmet admission requirement to an application-declared existing
  * execution boundary. It does not select which gap to execute, issue a
  * Decision, invoke a Provider, or advance a StateMachine.
  * @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
object CandidateAdmissionGapRouting {
  sealed trait Target
  object Target {
    final case class SemanticAction(operation: StateMachineRequiredOperation) extends Target
    final case class DeterministicOperation(operation: StateMachineRequiredOperation) extends Target
    /** The application must issue the normal Workflow Decision after its durable boundary. */
    final case class AuthorityDecision(identity: String) extends Target
  }

  final case class Binding(requirementIdentity: String, target: Target)
  final case class Resolution[S](gap: AdmissionGap[S], target: Target)

  /** Execution-facing routing uses the definition bound to the active durable
    * instance; a separately supplied definition could belong to another run.
    */
  def resolveForInstanceC[S](
    evaluation: AdmissionEvaluation[S],
    bindings: Vector[Binding],
    instance: WorkflowInstancePersistence.InstanceRecord
  ): Consequence[Vector[Resolution[S]]] =
    if (instance == null)
      Consequence.stateConflict("Admission routing requires a Workflow instance")
    else instance.validateC.flatMap { admitted =>
      if (admitted.lifecycle != WorkflowInstancePersistence.Lifecycle.Active ||
          admitted.suspension.nonEmpty)
        Consequence.stateConflict("Admission routing requires an active, unsuspended Workflow instance")
      else resolveDeclaredC(evaluation, bindings, admitted.definition)
    }

  /** Shape and admitted-declaration check for focused tests. The caller's
    * operation object is not itself evidence of a Required SPI declaration.
    */
  private[workflow] def resolveDeclaredC[S](
    evaluation: AdmissionEvaluation[S],
    bindings: Vector[Binding],
    definition: WorkflowInstancePersistence.DefinitionBinding
  ): Consequence[Vector[Resolution[S]]] =
    if (definition == null)
      Consequence.stateConflict("Admission routing requires an admitted Workflow definition")
    else definition.validateC.flatMap { admitted =>
      resolveC(evaluation, bindings).flatMap { resolved =>
        if (resolved.forall(r => _declared(r.target, admitted))) Consequence.success(resolved)
        else Consequence.stateConflict("Admission gap operation is absent from the admitted Required SPI declaration")
      }
    }

  /** Shape-only planning helper; execution must use resolveDeclaredC. */
  private[workflow] def resolveC[S](
    evaluation: AdmissionEvaluation[S],
    bindings: Vector[Binding]
  ): Consequence[Vector[Resolution[S]]] =
    if (evaluation == null || evaluation.target == null ||
        evaluation.target.validateC.toOption.isEmpty || evaluation.acceptedEvidence == null ||
        evaluation.gaps == null ||
        evaluation.gaps.exists(gap => gap == null || gap.requirement == null ||
          gap.requirement.validateC.toOption.isEmpty || gap.defects == null ||
          gap.defects.isEmpty || gap.defects.exists(_ == null)) ||
        evaluation.gaps.map(_.requirement.identity).distinct.size != evaluation.gaps.size ||
        bindings == null || bindings.exists(binding => binding == null ||
          !_name(binding.requirementIdentity) || !_valid_target(binding.target)) ||
        bindings.map(_.requirementIdentity).distinct.size != bindings.size)
      Consequence.stateConflict("Admission gap routing is incomplete or has duplicate bindings")
    else evaluation.gaps.foldLeft[Consequence[Vector[Resolution[S]]]](Consequence.success(Vector.empty)) {
      (acc, gap) => acc.flatMap { resolved =>
        bindings.find(_.requirementIdentity == gap.requirement.identity) match {
          case Some(binding) if _valid(gap.requirement.kind, binding.target) =>
            Consequence.success(resolved :+ Resolution(gap, binding.target))
          case _ => Consequence.stateConflict(
            s"Admission gap has no compatible declared boundary: ${gap.requirement.identity}")
        }
      }
    }

  private def _declared(
    target: Target,
    binding: WorkflowInstancePersistence.DefinitionBinding
  ): Boolean = target match {
    case Target.SemanticAction(operation) => _declared_operation(operation, binding)
    case Target.DeterministicOperation(operation) => _declared_operation(operation, binding)
    case Target.AuthorityDecision(_) => true // Decision authority is application-owned.
  }

  private def _declared_operation(
    operation: StateMachineRequiredOperation,
    binding: WorkflowInstancePersistence.DefinitionBinding
  ): Boolean =
    binding.admittedCandidateDefinition match {
      case Some(candidate) => candidate.workflow.requiredSpi.exists { spi =>
        spi.capability == operation.identity.capability &&
        spi.actionIdentity == operation.actionIdentity &&
        spi.operation.service == operation.operation.service &&
        spi.operation.name == operation.operation.operation &&
        spi.operation.inputType == operation.inputType.map(_.value) &&
        spi.operation.resultType == operation.resultType.map(_.value)
      }
      case None => Option(binding.admittedDefinition).exists(_.requiredSpis.exists { spi =>
        spi.identity.value == operation.identity.capability &&
        spi.actionIdentity.value == operation.actionIdentity &&
        spi.operation.serviceIdentity.value == operation.operation.service &&
        spi.operation.operationIdentity.value == operation.operation.operation &&
        spi.operation.inputType.map(_.value) == operation.inputType.map(_.value) &&
        spi.operation.resultType.map(_.value) == operation.resultType.map(_.value)
      })
    }

  private def _valid(kind: RequirementKind, target: Target): Boolean = (kind, target) match {
    case (RequirementKind.Semantic, Target.SemanticAction(operation)) => _valid_operation(operation)
    case (RequirementKind.Deterministic, Target.DeterministicOperation(operation)) => _valid_operation(operation)
    case (RequirementKind.Authority, Target.AuthorityDecision(identity)) => _name(identity)
    case _ => false
  }

  private def _valid_target(target: Target): Boolean = target match {
    case Target.SemanticAction(operation) => _valid_operation(operation)
    case Target.DeterministicOperation(operation) => _valid_operation(operation)
    case Target.AuthorityDecision(identity) => _name(identity)
    case null => false
  }

  private def _valid_operation(operation: StateMachineRequiredOperation): Boolean =
    operation != null && operation.identity != null && _name(operation.identity.capability) &&
      _name(operation.actionIdentity) && operation.operation != null &&
      _name(operation.operation.service) && _name(operation.operation.operation) &&
      operation.inputType != null && operation.resultType != null &&
      operation.metadata != null && operation.metadata.contextContract != null &&
      _name(operation.metadata.contextContract.identity) &&
      operation.metadata.contextContract.requiredFacts != null &&
      operation.metadata.contextContract.requiredReferences != null &&
      operation.metadata.completionContract != null &&
      _name(operation.metadata.completionContract.identity) &&
      operation.metadata.completionContract.requiredFacts != null &&
      operation.metadata.evidenceContract != null &&
      _name(operation.metadata.evidenceContract.identity) &&
      operation.metadata.evidenceContract.requiredEvidence != null &&
      operation.metadata.constraints != null

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
