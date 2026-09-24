package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CandidateWorkflowAbiSpec extends AnyWordSpec with Matchers {
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(
      getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }

  private val sidecar = CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/candidate-admission-producer-abi.json")) match {
    case Consequence.Success(value) => value
    case failure => fail(s"sidecar fixture rejected: $failure")
  }
  // The checked-in fixture has a source-file newline; Cozy's emitted JSON does not.
  private val workflow = resource("/workflow/candidate-admission-workflow-abi.json").stripSuffix("\n")
  private val sourceResource = "modeler/candidate-admission-workflow.cml"

  "Candidate-Admission workflow receiver" should {
    "admit the real Cozy Phase 73 workflow and bind it to WorkflowInstance" in {
      val admitted = CandidateWorkflowAbi.parseC(workflow, sidecar, sourceResource) match {
        case Consequence.Success(value) => value
        case failure => fail(s"workflow fixture rejected: $failure")
      }
      admitted.workflow.identity shouldBe "OrderProgress"
      admitted.workflow.version shouldBe "workflow-v1"
      admitted.sha256 shouldBe "b56b3b5be5938277282c265e73a5a82678eedadd82724e17592ac60df0c5676a"
      admitted.workflow.actions.map(_.kind) shouldBe Vector("JUDGMENT", "ADMISSION")
      admitted.workflow.requiredSpi.map(_.capability) shouldBe Vector("capture-payment-capability")

      val binding = WorkflowInstancePersistence.bindCandidateDefinitionC(admitted) match {
        case Consequence.Success(value) => value
        case failure => fail(s"candidate binding rejected: $failure")
      }
      binding.workflowIdentity.value shouldBe "OrderProgress"
      binding.workflowRevision.value shouldBe "workflow-v1"
      binding.validateC shouldBe Consequence.success(binding)
    }

    "reject a workflow whose action or sidecar facts diverge" in {
      CandidateWorkflowAbi.parseC(workflow.replace("JUDGMENT", "OPERATION"),
        sidecar, sourceResource) shouldBe a[Consequence.Failure[_]]
      CandidateWorkflowAbi.parseC(workflow.replace("capture-payment-capability", "other-capability"),
        sidecar, sourceResource) shouldBe a[Consequence.Failure[_]]
      CandidateWorkflowAbi.parseC(workflow, sidecar.copy(models = Vector.empty),
        sourceResource) shouldBe a[Consequence.Failure[_]]
    }
  }
}
