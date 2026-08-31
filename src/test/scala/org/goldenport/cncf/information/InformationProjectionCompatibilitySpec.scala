package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.action.Behavior
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.observation.Descriptor
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
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
        output.excludedFields should contain ("providerPayload")
      }

      "project only the existing System Admin Information HTTP read surface" in {
        Given("the existing System Admin Information read-projection metadata")
        val surfaces = InformationProjectionContract.output.surfaces

        When("the named consumer surfaces are inspected")
        val http = surfaces.find(_.surface == InformationProjectionSurface.Http)
        val unavailable = surfaces.filterNot(_.surface == InformationProjectionSurface.Http)

        Then("only HTTP is applicable and every unavailable surface records the missing Information seam")
        http.map(_.status) shouldBe Some(InformationProjectionSurfaceStatus.Applicable)
        unavailable.map(_.surface.name) shouldBe Vector(
          "help", "form", "json", "yaml", "xml", "schema", "openapi", "mcp"
        )
        unavailable.foreach { surface =>
          surface.status shouldBe InformationProjectionSurfaceStatus.Inapplicable
          surface.reason shouldBe Some(
            "No existing Information access seam: only the System Admin Information Web/HTTP read projection exists."
          )
        }
      }

      "project bounded profile-selected application output without provider payloads" in {
        Given("Information working data containing a title, provider payload, and an unselected field")
        val providerpayload = "provider-secret-payload"
        val applicationdata = Record.data(
          "title" -> "Approved title",
          "providerPayload" -> providerpayload,
          "authors" -> "Alice Example"
        )
        val field = InformationProjectionContract.output.field("workingData")

        When("the System Admin output projection is applied")
        val projected = InformationProjectionContract.projectOutputApplicationData(applicationdata)

        Then("only the bounded profile-selected title is exposed")
        field.map(_.datatype) shouldBe Some("InformationProfileOutput")
        field.map(_.valueCategory) shouldBe Some(
          InformationProjectionValueCategory.SanitizedApplicationData
        )
        field.map(_.projectedFieldPaths) shouldBe Some(Vector("title"))
        field.map(_.excludedFieldPaths) shouldBe Some(Set("providerPayload"))
        projected.getString("title") shouldBe Some("Approved title")
        projected.getString("providerPayload") shouldBe empty
        projected.getString("authors") shouldBe empty
        projected.print should not include providerpayload
      }

      "declare observed revision and structured stale-entity failure metadata distinct from application data" in {
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
        update.staleFailure.reason shouldBe "stale-entity-revision"
        update.staleFailure.policy shouldBe "entity.optimistic-concurrency"
        update.staleFailure.expectedRevision.name shouldBe "expectedRevision"
        update.staleFailure.actualRevision.name shouldBe "actualRevision"
        update.staleFailure.actualRevision.systemManaged shouldBe true
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
        val winnerpayload = "winner-working-payload"
        val stalepayload = "stale-working-payload"

        When("the protected DSL submits a conditional update with the observed revision")
        val updated = behavior.updateObserved(
          registered.id,
          Record.data("title" -> winnerpayload),
          registered.revision
        )
        val stale = behavior.updateObserved(
          registered.id,
          Record.data("title" -> stalepayload),
          registered.revision
        )
        val rendered = context.observability.callTreeContext.build().map(_.toRecord.print).getOrElse("")
        val (diagnostic, facets, stalefailure) = stale match {
          case Consequence.Failure(conclusion) =>
            (
              ConclusionDiagnostics.classify(conclusion),
              conclusion.observation.cause.descriptor.facets,
              conclusion.show
            )
          case other =>
            fail(s"stale Information update failure expected: $other")
        }

        Then("the observed update reports structured Entity concurrency facets without leaking raw current or proposed values")
        updated.toOption.map(_.revision.value) shouldBe Some(2L)
        stale shouldBe a[Consequence.Failure[?]]
        diagnostic.reason shouldBe Some("stale-entity-revision")
        diagnostic.policy shouldBe Some("entity.optimistic-concurrency")
        facets should contain(Descriptor.Facet.Expected(1L))
        facets should contain(Descriptor.Facet.Actual(2L))
        stalefailure should not include winnerpayload
        stalefailure should not include stalepayload
        rendered should include ("uow:information:update")
        rendered should include ("observed_revision")
        rendered should not include winnerpayload
        rendered should not include stalepayload
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
