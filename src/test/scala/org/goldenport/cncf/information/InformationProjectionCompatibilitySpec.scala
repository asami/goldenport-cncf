package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.action.Behavior
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable compatibility specification for Phase 61.4 IC-06 E1 and E2.
 *
 * @since   Sep.  1, 2026
 * @version Sep.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationProjectionCompatibilitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Information projection contract" should {
    "E2 project canonical Information descriptors across output, input, and consumer surfaces" must afterWord(
      "in spec:phase-61.4-information-projection-compatibility, example:E2, rules:IC-06, phase:61.4"
    ) {
      "preserve generated output revision while excluding managed and provider fields from application input" in {
        Given("the canonical generated Information projection descriptors")
        val output = InformationProjectionContract.output
        val create = InformationProjectionContract.createApplicationInput

        When("output and application-create metadata are inspected")
        val revision = output.field("revision")

        Then("revision is system read-only output metadata rather than a create field")
        InformationProjectionContract.canonicalEntityClass shouldBe classOf[entity.Information]
        revision.exists(x => x.systemManaged && x.readOnly && x.required) shouldBe true
        create.field("revision") shouldBe empty
        create.excludedFields should contain ("revision")
        output.excludedFields should contain ("rawData")
      }

      "declare observed revision as a required transport precondition distinct from application data" in {
        Given("the conditional Information update projection")
        val update = InformationProjectionContract.conditionalUpdate

        When("its application input and conditional metadata are inspected")
        val precondition = update.observedRevision

        Then("only the separate precondition carries the observed revision requirement")
        precondition.name shouldBe "observedRevision"
        precondition.required shouldBe true
        precondition.transportPrecondition shouldBe true
        update.application.field("observedRevision") shouldBe empty
        update.application.field("revision") shouldBe empty
        update.conflictValues.map(_.valueCategory) should contain (
          InformationProjectionValueCategory.StructuredConflict
        )
      }

      "give every named consumer surface an identical canonical descriptor vocabulary" in {
        Given("the complete public projection-surface enumeration")
        val descriptors = InformationProjectionContract.descriptors
        val property = Prop.forAll(Gen.oneOf(InformationProjectionContract.surfaces)) { surface =>
          descriptors.forall(_.surfaces.contains(surface)) &&
            InformationProjectionContract.conditionalUpdate.surfaces.contains(surface)
        }

        When("each named surface is checked against every reusable descriptor")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

        Then("Help HTTP Form JSON YAML XML schema OpenAPI and MCP retain descriptor parity")
        InformationProjectionContract.surfaces.map(_.name) shouldBe Vector(
          "help", "http", "form", "json", "yaml", "xml", "schema", "openapi", "mcp"
        )
        checked.passed shouldBe true
      }
    }
  }

  "Behavior Information observed-update DSL" should {
    "E1 preserve observed-update revision and CallTree boundaries" must afterWord(
      "in spec:phase-61.4-information-projection-compatibility, example:E1, rules:IC-06, phase:61.4"
    ) {
      "delegate the supplied revision to InformationSpace and trace only safe metadata" in {
        Given("a component-owned Information root and a CallTree-enabled execution context")
        val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.test(), enabled = true)
        given ExecutionContext = context
        val component = _component("org.goldenport.cncf.information.ProjectionDslOwner")
        val registered = _success(component.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Initial title"))
        )).head
        val behavior = new InformationBehavior(Behavior.Core(context, Some(component), None))
        val secret = "confidential-working-payload"

        When("the protected DSL submits a conditional update with the observed revision")
        val updated = behavior.updateObserved(
          registered.id,
          Record.data("title" -> secret),
          registered.revision
        )
        val stale = behavior.updateObserved(
          registered.id,
          Record.data("title" -> "stale"),
          registered.revision
        )
        val rendered = context.observability.callTreeContext.build().map(_.toRecord.print).getOrElse("")

        Then("the observed update advances managed revision, rejects a stale retry, and omits working payload")
        updated.toOption.map(_.revision.value) shouldBe Some(2L)
        stale shouldBe a[Consequence.Failure[?]]
        rendered should include ("uow:information:update")
        rendered should include ("observed_revision")
        rendered should not include secret
      }
    }
  }

  private final class InformationBehavior(
    val behaviorCore: Behavior.Core
  ) extends Behavior {
    def updateObserved(
      informationId: InformationId,
      workingData: Record,
      observedRevision: org.simplemodeling.model.datatype.EntityRevision
    ): Consequence[Information] =
      new UnitOfWorkInterpreter(new UnitOfWork(executionContext)).run(
        information_update_observed(informationId, workingData, observedRevision)
      )
  }

  private def _component(componentid: String): Component = {
    val identity = ComponentId(componentid)
    Component.create(
      identity.name,
      identity,
      ComponentInstanceId.default(identity),
      Protocol.empty
    )
  }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) =>
      throw new AssertionError(conclusion.show)
  }
}
