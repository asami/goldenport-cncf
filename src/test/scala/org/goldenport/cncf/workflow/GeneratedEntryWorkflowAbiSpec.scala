package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 */
final class GeneratedEntryWorkflowAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val entry = GeneratedEntryWorkflowAbi.Definition(
    workflowSchema = GeneratedEntryWorkflowAbi.workflowSchemaVersion,
    providedApiSchema = GeneratedEntryWorkflowAbi.providedApiSchemaVersion,
    providedGenerator = GeneratedEntryWorkflowAbi.providedGeneratorIdentity,
    workflowIdentity = "SplitPhaseWorkflow",
    workflowRevision = "split-phase-entry-v1",
    providedWorkflowIdentity = "SplitPhaseWorkflow",
    providedWorkflowRevision = "split-phase-entry-v1",
    sourceResource = "src/main/cozy/sm-workflow.cml",
    rootLine = 273,
    definitionLine = 275,
    providedRootLine = 273,
    providedDefinitionLine = 275,
    states = Vector(GeneratedEntryWorkflowAbi.State("EntryPending", 291)),
    actionCount = 0,
    requiredOperationCount = 0,
    providedOperationCount = 1,
    startOperation = GeneratedEntryWorkflowAbi.StartOperation(
      "SplitPhaseService", "startSplitPhase", "SplitPhaseStartCommand",
      "SplitPhaseStartResult", 306)
  )

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Generated entry ABI failed: $failure")
  }

  "Generated actionless entry Workflow ABI" should {
    "bind one generated Provided Start to an independent durable definition" in {
      Given("the correlated generated Workflow and Provided API descriptors")
      val binding = take(WorkflowInstancePersistence.bindEntryDefinitionC(entry))

      When("the binding is validated and used in a fresh instance record")
      val record = InstanceRecord(
        InstanceIdentity("split-phase-1"), binding, initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("human-selection-1"),
        CausationReference("start:1"), None, None, None, Vector.empty)

      Then("the definition reference remains usable without a Candidate sidecar")
      take(binding.validateC) shouldBe binding
      take(record.validateC) shouldBe record
      binding.workflowIdentity shouldBe WorkflowDefinitionIdentity("SplitPhaseWorkflow")
      binding.workflowRevision shouldBe WorkflowDefinitionRevision("split-phase-entry-v1")
      binding.sourceCorrelation.root.resource shouldBe entry.sourceResource
    }

    "reject Action/SPI injection, a mismatched Provided API, and incomplete source" in {
      Given("an otherwise valid actionless entry descriptor")
      val actionful = entry.copy(actionCount = 1)
      val mismatched = entry.copy(providedWorkflowIdentity = "GoalPhaseWorkflow")
      val sourceless = entry.copy(startOperation = entry.startOperation.copy(sourceLine = 0))

      When("each variant is submitted for binding")
      val outcomes = Vector(actionful, mismatched, sourceless).map(
        WorkflowInstancePersistence.bindEntryDefinitionC)

      Then("none can become a durable Workflow definition")
      outcomes.map(_.toOption) shouldBe Vector(None, None, None)
    }

    "admit one correlated post-entry stage without requiring unchanged-content evidence" in {
      Given("an executable entry declaration with ordered stages or terminal branches")
      val staged = entry.copy(
        workflowRevision = "split-phase-entry-v2",
        providedWorkflowRevision = "split-phase-entry-v2",
        states = entry.states :+ GeneratedEntryWorkflowAbi.State("ProposalPending", 311),
        providedOperationCount = 2,
        postEntryOperation = Some(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "assessSplitProposal",
          "SplitPhaseProposalAssessmentCommand", "SplitPhaseProposalAssessmentResult", 324))
      )

      When("the declaration is bound without a source or content certificate")
      val binding = take(WorkflowInstancePersistence.bindEntryDefinitionC(staged))
      Then("its executable reference is available")
      binding.workflowIdentity.value shouldBe "SplitPhaseWorkflow"

      val missingState = staged.copy(states = entry.states)
      val missingOperation = staged.copy(postEntryOperation = None)
      val wrongService = staged.copy(postEntryOperation = staged.postEntryOperation.map(
        _.copy(service = "OtherService")))
      val fakeStart = staged.copy(postEntryOperation = staged.postEntryOperation.map(
        _.copy(operation = "startAnotherWorkflow")))
      Vector(missingState, missingOperation, wrongService, fakeStart).foreach { value =>
        When("an incompatible stage declaration is submitted")
        val result = WorkflowInstancePersistence.bindEntryDefinitionC(value)
        Then("actual executable incompatibility is rejected")
        result shouldBe a[Consequence.Failure[?]]
      }
    }

    "admit one terminal operation after assessment and reject an unbound extra stage" in {
      Given("an executable entry declaration with ordered stages or terminal branches")
      val staged = entry.copy(
        workflowRevision = "split-phase-entry-v2",
        providedWorkflowRevision = "split-phase-entry-v2",
        states = entry.states :+ GeneratedEntryWorkflowAbi.State("ProposalPending", 311),
        providedOperationCount = 2,
        postEntryOperation = Some(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "assessSplitProposal",
          "SplitPhaseProposalAssessmentCommand", "SplitPhaseProposalAssessmentResult", 324))
      )
      val preview = staged.copy(
        workflowRevision = "split-phase-preview-v1",
        providedWorkflowRevision = "split-phase-preview-v1",
        states = staged.states :+ GeneratedEntryWorkflowAbi.State("Previewed", 315),
        providedOperationCount = 3,
        terminalOperation = Some(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "previewSplitPhase",
          "SplitPhasePreviewCommand", "SplitPhasePreviewResult", 340))
      )

      When("the terminal declaration is bound")
      val binding = take(WorkflowInstancePersistence.bindEntryDefinitionC(preview))
      Then("the admitted execution definition remains usable")
      take(binding.validateC) shouldBe binding
      Vector(
        preview.copy(states = staged.states),
        preview.copy(providedOperationCount = 2),
        preview.copy(postEntryOperation = None),
        preview.copy(terminalOperation = preview.terminalOperation.map(
          _.copy(operation = "assessSplitProposal")))
      ).foreach { value =>
        When("an incompatible stage declaration is submitted")
        val result = WorkflowInstancePersistence.bindEntryDefinitionC(value)
        Then("actual executable incompatibility is rejected")
        result shouldBe a[Consequence.Failure[?]]
      }
    }

    "bind distinct terminal branches without requiring unchanged-content evidence" in {
      Given("an executable entry declaration with ordered stages or terminal branches")
      val staged = entry.copy(
        workflowRevision = "split-phase-apply-v1",
        providedWorkflowRevision = "split-phase-apply-v1",
        states = entry.states ++ Vector(
          GeneratedEntryWorkflowAbi.State("ProposalPending", 311),
          GeneratedEntryWorkflowAbi.State("Previewed", 315)),
        providedOperationCount = 3,
        postEntryOperation = Some(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "assessSplitProposal",
          "SplitPhaseProposalAssessmentCommand", "SplitPhaseProposalAssessmentResult", 324)),
        terminalOperation = Some(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "previewSplitPhase",
          "SplitPhasePreviewCommand", "SplitPhasePreviewResult", 340))
      )
      val branched = staged.copy(
        states = staged.states :+ GeneratedEntryWorkflowAbi.State("Applied", 317),
        providedOperationCount = 4,
        additionalTerminalOperations = Vector(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "applySplitPhase",
          "SplitPhaseApplyCommand", "SplitPhaseApplyResult", 350))
      )

      When("the declaration is bound without a source or content certificate")
      val binding = take(WorkflowInstancePersistence.bindEntryDefinitionC(branched))
      Then("its executable reference is available")
      binding.workflowIdentity.value shouldBe "SplitPhaseWorkflow"
      Vector(
        branched.copy(states = staged.states),
        branched.copy(providedOperationCount = 3),
        branched.copy(additionalTerminalOperations = branched.additionalTerminalOperations.map(
          _.copy(operation = "previewSplitPhase")))
      ).foreach { value =>
        When("an incompatible stage declaration is submitted")
        val result = WorkflowInstancePersistence.bindEntryDefinitionC(value)
        Then("actual executable incompatibility is rejected")
        result shouldBe a[Consequence.Failure[?]]
      }
    }

    "bind ordered intermediate stages without source-order certificates" in {
      Given("an executable entry declaration with ordered stages or terminal branches")
      val first = entry.copy(
        workflowRevision = "repository-sync-boundary-v1",
        providedWorkflowRevision = "repository-sync-boundary-v1",
        states = entry.states :+ GeneratedEntryWorkflowAbi.State("BoundaryCaptured", 312),
        providedOperationCount = 2,
        postEntryOperation = Some(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "assessRepositoryBoundary",
          "RepositorySyncBoundaryAssessmentCommand", "RepositorySyncBoundaryAssessmentResult",
          330)))
      val fetched = first.copy(
        workflowRevision = "repository-sync-fetch-v1",
        providedWorkflowRevision = "repository-sync-fetch-v1",
        states = first.states :+ GeneratedEntryWorkflowAbi.State("FetchAdmitted", 314),
        providedOperationCount = 3,
        additionalStageOperations = Vector(GeneratedEntryWorkflowAbi.StageOperation(
          "SplitPhaseService", "admitTrackingFetch",
          "RepositorySyncFetchAdmissionCommand", "RepositorySyncFetchAdmissionResult", 350)))

      When("the declaration is bound without a source or content certificate")
      val binding = take(WorkflowInstancePersistence.bindEntryDefinitionC(fetched))
      Then("its executable reference is available")
      binding.workflowIdentity.value shouldBe "SplitPhaseWorkflow"
      Vector(
        fetched.copy(states = first.states),
        fetched.copy(providedOperationCount = 2),
        fetched.copy(additionalStageOperations = fetched.additionalStageOperations.map(
          _.copy(operation = "assessRepositoryBoundary"))),
        fetched.copy(additionalStageOperations = fetched.additionalStageOperations.map(
          _.copy(service = "OtherService")))
      ).foreach { value =>
        When("an incompatible stage declaration is submitted")
        val result = WorkflowInstancePersistence.bindEntryDefinitionC(value)
        Then("actual executable incompatibility is rejected")
        result shouldBe a[Consequence.Failure[?]]
      }
    }

    "accept relocated sources and different generation metadata" in {
      Given("Spec: docs/spec/workflow-definition-reference-reopening.md; Rule: R1; Example: E3; an executable entry definition")
      val relocated = entry.copy(rootLine = 1, definitionLine = 2,
        providedRootLine = 500, providedDefinitionLine = 600,
        providedGenerator = "another-compatible-generator",
        sourceResource = "relocated/workflow.cml")
      When("the relocated definition is admitted and bound")
      val binding = take(WorkflowInstancePersistence.bindEntryDefinitionC(relocated))
      Then("source and generation metadata are diagnostic, not unchanged-content gates")
      binding.workflowIdentity shouldBe WorkflowDefinitionIdentity(entry.workflowIdentity)
      binding.workflowRevision shouldBe WorkflowDefinitionRevision(entry.workflowRevision)
    }
  }
}
