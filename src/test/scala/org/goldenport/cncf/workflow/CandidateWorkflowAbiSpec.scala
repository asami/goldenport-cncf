package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 25, 2026
 * @version Sep. 28, 2026
 */
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
    "admit a required ordinary operation alongside exact CAM actions without weakening CAM matching" in {
      def take[A](value: Consequence[A]): A = value match {
        case Consequence.Success(result) => result
        case failure => fail(s"unexpected failure: $failure")
      }
      val operation = Json.obj("service" -> Json.fromString("RepairService"),
        "name" -> Json.fromString("repair"), "inputType" -> Json.fromString("PaymentCommand"),
        "resultType" -> Json.fromString("RepairResult"))
      def line(value: Int): Json = Json.obj("line" -> Json.fromInt(value))
      val action = Json.obj("identity" -> Json.fromString("repair-payment"),
        "kind" -> Json.fromString("OPERATION"),
        "operation" -> operation.deepMerge(Json.obj("source" -> line(100))),
        "inputBinding" -> Json.fromString("payment.subject"), "source" -> line(100))
      val spi = Json.obj("capability" -> Json.fromString("repair-payment-capability"),
        "actionIdentity" -> Json.fromString("repair-payment"), "operation" -> operation,
        "capabilitySource" -> line(110), "actionSource" -> line(100))
      val originalWorkflow = parse(workflow).toOption.get.hcursor.downField("workflows")
        .downArray.focus.get
      val originalModel = parse(resource("/workflow/candidate-admission-producer-abi.json"))
        .toOption.get.hcursor.downField("models").downArray.focus.get
      def appended(json: Json, key: String, item: Json): Json = json.mapObject(obj =>
        obj.add(key, Json.fromValues(obj(key).get.asArray.get :+ item)))
      val mixedWorkflow = appended(appended(originalWorkflow, "actions", action), "requiredSpi",
        Json.obj("capability" -> Json.fromString("repair-payment-capability"),
          "actionIdentity" -> Json.fromString("repair-payment"),
          "operation" -> operation.deepMerge(Json.obj("source" -> line(100))), "source" -> line(110)))
      val mixedSidecar = take(CandidateAdmissionProducerAbi.parseC(
        parse(resource("/workflow/candidate-admission-producer-abi.json")).toOption.get.mapObject(obj =>
          obj.add("models", Json.arr(appended(originalModel, "requiredSpi", spi)))).noSpaces))
      def wire(value: Json): String = Json.obj("schemaVersion" -> Json.fromString(CandidateWorkflowAbi.schemaVersion),
        "workflows" -> Json.arr(value)).noSpaces
      take(CandidateWorkflowAbi.parseC(wire(mixedWorkflow), mixedSidecar, sourceResource))
        .workflow.actions.map(_.kind) shouldBe Vector("JUDGMENT", "ADMISSION", "OPERATION")
      CandidateWorkflowAbi.parseC(wire(mixedWorkflow).replace("JUDGMENT", "OPERATION"),
        mixedSidecar, sourceResource) shouldBe a[Consequence.Failure[_]]
      CandidateWorkflowAbi.parseC(wire(mixedWorkflow).replace("\"line\":100", "\"line\":101"),
        mixedSidecar, sourceResource) shouldBe a[Consequence.Failure[_]]
      CandidateWorkflowAbi.parseC(wire(mixedWorkflow).replace("RepairService", "OtherService"),
        mixedSidecar, sourceResource) shouldBe a[Consequence.Failure[_]]
      CandidateWorkflowAbi.parseC(wire(appended(originalWorkflow, "actions", action)),
        sidecar, sourceResource) shouldBe a[Consequence.Failure[_]]
    }

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
