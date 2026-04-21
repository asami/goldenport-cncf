package org.goldenport.cncf.component

import scala.collection.mutable.ArrayBuffer
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.schema.DataType
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedComponentBundleFactorySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Generated-style bundle factory" should {
    "separate primary and componentlets at construction time" in {
      Given("generated-style bundle factory")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))

      When("bundle is created")
      val bundle = _GeneratedBundleFactory.create(params)

      Then("primary and componentlets are explicit and initialized separately")
      bundle.primary.name shouldBe "domain"
      bundle.primary.isPrimaryParticipant shouldBe true
      bundle.componentlets.map(_.name) shouldBe Vector("notice-admin")
      bundle.componentlets.forall(_.isComponentletParticipant) shouldBe true
      bundle.participants.size shouldBe 2
      bundle.primary.core.factory shouldBe Some(_GeneratedBundleFactory.PrimaryFactory)
      bundle.componentlets.head.core.factory shouldBe Some(_GeneratedBundleFactory.NoticeAdminFactory)
    }

    "dispatch same-subsystem sync reception on generated componentlet with runtime identity" in {
      Given("bootstrapped generated runtime participants")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
      val bundle = _GeneratedBundleFactory.create(params)
      val factory = new ComponentFactory()
      val components = bundle.participants.map(factory.bootstrap)
      subsystem.add(components)
      val target = subsystem.components.find(_.name == "notice-admin").getOrElse(fail("missing target componentlet"))

      When("componentlet receives same-subsystem event")
      val result = target.eventReception.getOrElse(fail("missing event reception")).receive(
        ReceptionInput(
          name = "notice.published",
          kind = "published",
          payload = Map("message" -> "hello"),
          attributes = Map(
            "targetId" -> "n1",
            EventReception.StandardAttribute.SourceSubsystem -> subsystem.name
          )
        )
      )

      Then("follow-up action executes with componentlet runtime identity")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      _GeneratedBundleFactory.calls.toVector shouldBe Vector("notice-admin")
    }

    "reject malformed bundle outputs deterministically" in {
      Given("bundle factory with duplicate participant names")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))

      When("bundle is created")
      val ex = intercept[IllegalArgumentException] {
        _InvalidBundleFactory.create(params)
      }

      Then("construction fails before bootstrap")
      ex.getMessage should include ("duplicate participant name")
    }
  }

  private object _GeneratedBundleFactory extends Component.BundleFactory {
    private val _calls = ArrayBuffer.empty[String]

    def calls: ArrayBuffer[String] = _calls

    object PrimaryFactory extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "domain",
          ComponentId("domain"),
          ComponentInstanceId.default(ComponentId("domain")),
          Protocol.empty,
          this
        )
    }

    object NoticeAdminFactory extends Component.ComponentletFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {
          override def eventReceptionDefinitions: Vector[CmlEventDefinition] =
            Vector(
              CmlEventDefinition(
                name = "notice.published",
                category = CmlEventCategory.NonActionEvent,
                kind = Some("published")
              )
            )

          override def eventSubscriptionDefinitions: Vector[CmlSubscriptionDefinition] =
            Vector(
              CmlSubscriptionDefinition(
                name = "notice-sync",
                eventName = "notice.published",
                route = DispatchRoute.Unicast,
                target = Some("targetId"),
                actionName = "notice.sync_notice",
                declaredTargetUpperBound = 1
              )
            )

          override def eventReceptionRuleDefinitions: Vector[EventReceptionRule] =
            Vector(
              EventReceptionRule(
                name = "notice-sync-default",
                condition = EventReceptionCondition(
                  originBoundary = Some(EventOriginBoundary.SameSubsystem),
                  eventName = Some("notice.published"),
                  eventKind = Some("published")
                ),
                policy = EventReceptionExecutionPolicy.SameSubsystemDefault
              )
            )

          override def operationDefinitions: Vector[CmlOperationDefinition] =
            Vector(
              CmlOperationDefinition(
                name = "sync_notice",
                kind = "COMMAND",
                execution = None,
                implementation = Some("generated-componentlet"),
                entityName = None,
                entityNames = Vector.empty,
                inputType = "SyncNoticeInput",
                inputSummary = None,
                inputDescription = None,
                outputType = "SyncNoticeOutput",
                outputSummary = None,
                outputDescription = None,
                inputValueKind = "COMMAND_VALUE",
                access = None,
                parameters = Vector.empty,
                operationAuthorization = None
              )
            )
        }

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core = {
        val operation = new spec.OperationDefinition {
          val specification: spec.OperationDefinition.Specification =
            spec.OperationDefinition.Specification(
              name = "sync_notice",
              request = spec.RequestDefinition(),
              response = spec.ResponseDefinition(result = List(DataType.Named("String")))
            )

          def createOperationRequest(req: Request): Consequence[OperationRequest] =
            Consequence.success(_SyncNoticeAction(req))
        }
        val service = spec.ServiceDefinition(
          name = "notice",
          operations = spec.OperationDefinitionGroup(
            operations = NonEmptyVector.of(operation)
          )
        )
        Component.Core.create(
          "notice-admin",
          ComponentId("notice_admin"),
          ComponentInstanceId.default(ComponentId("notice_admin")),
          Protocol(
            services = spec.ServiceDefinitionGroup(Vector(service)),
            handler = ProtocolHandler.default
          ),
          this
        )
      }

      private final case class _SyncNoticeAction(
        request: Request
      ) extends QueryAction {
        def createCall(core: ActionCall.Core): ActionCall =
          _SyncNoticeActionCall(core)
      }

      private final case class _SyncNoticeActionCall(
        core: ActionCall.Core
      ) extends ProcedureActionCall {
        def execute(): Consequence[OperationResponse] = {
          _calls += core.component.map(_.name).getOrElse("missing")
          Consequence.success(OperationResponse.Scalar("ok"))
        }
      }
    }

    def primaryFactory: Component.PrimaryComponentFactory =
      PrimaryFactory

    override def componentletFactories: Vector[Component.ComponentletFactory] =
      Vector(NoticeAdminFactory)
  }

  private object _InvalidBundleFactory extends Component.BundleFactory {
    object PrimaryFactory extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "duplicate",
          ComponentId("duplicate"),
          ComponentInstanceId.default(ComponentId("duplicate")),
          Protocol.empty,
          this
        )
    }

    object DuplicateComponentletFactory extends Component.ComponentletFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "duplicate",
          ComponentId("duplicate_componentlet"),
          ComponentInstanceId.default(ComponentId("duplicate_componentlet")),
          Protocol.empty,
          this
        )
    }

    def primaryFactory: Component.PrimaryComponentFactory =
      PrimaryFactory

    override def componentletFactories: Vector[Component.ComponentletFactory] =
      Vector(DuplicateComponentletFactory)
  }
}
