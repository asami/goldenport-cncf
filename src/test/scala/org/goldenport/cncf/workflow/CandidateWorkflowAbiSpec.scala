package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen

/**
 * @since   Sep. 25, 2026
 *  version Sep. 28, 2026
 * @version Oct. 3, 2026
 */
final class CandidateWorkflowAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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
    "select one Workflow while preserving complete artifact bytes and validating its binding" in {
      Given("a complete artifact with two Workflow definitions")
      val original = parse(workflow).toOption.get.hcursor.downField("workflows").downArray.focus.get
      val sibling = original.mapObject(_.add("identity", Json.fromString("AnotherWorkflow")))
      val combined = Json.obj("schemaVersion" -> Json.fromString(CandidateWorkflowAbi.schemaVersion),
        "workflows" -> Json.arr(sibling, original)).spaces2 + "\n"
      When("the caller explicitly selects the known identity and version")
      val selected = CandidateWorkflowAbi.parseSelectedC(combined, sidecar, sourceResource,
        "OrderProgress", "workflow-v1") match {
        case Consequence.Success(value) => value
        case failure => fail(s"selection rejected: $failure")
      }
      Then("the complete original text and hash remain bound without per-Workflow serialization")
      selected.rawJson shouldBe combined
      selected.workflow.identity shouldBe "OrderProgress"
      val expected = java.security.MessageDigest.getInstance("SHA-256")
        .digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .map(byte => f"${byte & 0xff}%02x").mkString
      selected.sha256 shouldBe expected
      CandidateWorkflowAbi.parseC(combined, sidecar, sourceResource) shouldBe a[Consequence.Failure[_]]
      CandidateWorkflowAbi.admitC(selected) shouldBe Consequence.success(selected)
      val binding = WorkflowInstancePersistence.bindCandidateDefinitionC(selected) match {
        case Consequence.Success(value) => value
        case failure => fail(s"binding rejected: $failure")
      }
      binding.validateC shouldBe Consequence.success(binding)
      CandidateWorkflowAbi.admitC(selected.copy(sha256 = "forged")) shouldBe a[Consequence.Failure[_]]
      CandidateWorkflowAbi.admitC(selected.copy(workflow = selected.workflow.copy(identity = "AnotherWorkflow"))) shouldBe
        a[Consequence.Failure[_]]
    }

    "reject missing selection, duplicate definitions and malformed siblings" in {
      Given("an official-shaped artifact and its admitted sidecar")
      val original = parse(workflow).toOption.get.hcursor.downField("workflows").downArray.focus.get
      def wire(values: Json*): String = Json.obj("schemaVersion" -> Json.fromString(CandidateWorkflowAbi.schemaVersion),
        "workflows" -> Json.fromValues(values)).noSpaces
      When("explicit selection cannot establish one unambiguous valid definition")
      val rejected = Vector(
        CandidateWorkflowAbi.parseSelectedC(workflow, sidecar, sourceResource, null, "workflow-v1"),
        CandidateWorkflowAbi.parseSelectedC(workflow, sidecar, sourceResource, "OrderProgress", " "),
        CandidateWorkflowAbi.parseSelectedC(workflow, sidecar, sourceResource, "Missing", "workflow-v1"),
        CandidateWorkflowAbi.parseSelectedC(workflow, sidecar, sourceResource, "OrderProgress", "wrong-version"),
        CandidateWorkflowAbi.parseSelectedC(wire(original, original), sidecar, sourceResource, "OrderProgress", "workflow-v1"),
        CandidateWorkflowAbi.parseSelectedC(wire(original, Json.obj()), sidecar, sourceResource, "OrderProgress", "workflow-v1")
      )
      Then("none can fall back to the first Workflow or bypass sibling validation")
      rejected.foreach(_ shouldBe a[Consequence.Failure[_]])
    }

    "admit a required ordinary operation alongside exact CAM actions without weakening CAM matching" in {
      Given("a workflow and sidecar containing exact CAM actions and an ordinary required operation")
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
      When("the exact mixed workflow is parsed with its matching sidecar")
      val admitted = take(CandidateWorkflowAbi.parseC(wire(mixedWorkflow), mixedSidecar, sourceResource))
      Then("both CAM actions and the ordinary operation are admitted")
      admitted.workflow.actions.map(_.kind) shouldBe Vector("JUDGMENT", "ADMISSION", "OPERATION")
      When("the CAM judgment kind is replaced")
      val changedkind = CandidateWorkflowAbi.parseC(wire(mixedWorkflow).replace("JUDGMENT", "OPERATION"),
        mixedSidecar, sourceResource)
      Then("CAM matching still rejects the changed kind")
      changedkind shouldBe a[Consequence.Failure[_]]
      When("the ordinary operation source line is changed")
      val changedline = CandidateWorkflowAbi.parseC(wire(mixedWorkflow).replace("\"line\":100", "\"line\":101"),
        mixedSidecar, sourceResource)
      Then("the source mismatch is rejected")
      changedline shouldBe a[Consequence.Failure[_]]
      When("the ordinary service identity is changed")
      val changedservice = CandidateWorkflowAbi.parseC(wire(mixedWorkflow).replace("RepairService", "OtherService"),
        mixedSidecar, sourceResource)
      Then("the service mismatch is rejected")
      changedservice shouldBe a[Consequence.Failure[_]]
      When("the action is appended without its required sidecar capability")
      val missingcapability = CandidateWorkflowAbi.parseC(wire(appended(originalWorkflow, "actions", action)),
        sidecar, sourceResource)
      Then("the incomplete binding is rejected")
      missingcapability shouldBe a[Consequence.Failure[_]]
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
