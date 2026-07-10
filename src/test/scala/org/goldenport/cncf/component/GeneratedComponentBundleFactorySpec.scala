package org.goldenport.cncf.component

import scala.collection.mutable.ArrayBuffer
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.record.Record
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
 *  version May. 15, 2026
 * @version Jul. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedComponentBundleFactorySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Generated-style bundle factory" should {
    "apply named instance identity and local properties during construction" in {
      Given("a generated component factory and named instance metadata")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val metadata = ComponentInstanceMetadata(
        componentName = "textus-scraper",
        instance = "dynamic-playwright",
        config = Map("scraper.mode" -> "dynamic"),
        rules = Record.data("navigation" -> Record.data("max-pages" -> 8)),
        purposes = Vector("javascript-heavy-site"),
        tags = Vector("dynamic", "browser"),
        priority = 100,
        isDefault = true
      )
      val params = ComponentCreate(
        subsystem,
        ComponentOrigin.Repository("cozy-generated")
      ).withInstanceMetadata(metadata)

      When("the named instance is created")
      val component = _generated_bundle_factory.PrimaryFactory.createPrimary(params)
      val resolved = component.logic.executionContext().runtime.resolvedParameters.get("scraper.mode")
      val packaged = component.logic.executionContext().runtime.resolvedParameters.get("scraper.timeout")

      Then("identity, rules, and ExecutionContext properties belong to that instance")
      component.instanceId shouldBe ComponentInstanceId("textus-scraper", "dynamic-playwright")
      component.instanceMetadata shouldBe Some(metadata)
      resolved.map(_.value) shouldBe Some(ConfigurationValue.StringValue("dynamic"))
      resolved.map(_.source) shouldBe Some(org.goldenport.cncf.config.ResolvedParameter.Source.Component("dynamic-playwright"))
      packaged.map(_.value) shouldBe Some(ConfigurationValue.StringValue("30s"))
    }

    "keep named instances in component space and select the declared default by name" in {
      Given("two instances created from one component factory")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val static = _generated_bundle_factory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata("domain", "static", isDefault = true))
      )
      val dynamic = _generated_bundle_factory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata("domain", "dynamic-playwright"))
      )

      When("both instances are added to component space")
      val space = ComponentSpace().add(Vector(dynamic, static))

      Then("exact identity preserves both while name lookup resolves the declared default")
      space.components.size shouldBe 2
      space.findInstance(ComponentInstanceId("domain", "static")) shouldBe Some(static)
      space.findInstance(ComponentInstanceId("domain", "dynamic-playwright")) shouldBe Some(dynamic)
      space.findInstance(ComponentInstanceId("domain", "dynamic_playwright")) shouldBe Some(dynamic)
      space.find(ComponentLocator.NameLocator("domain")) shouldBe Some(static)
    }

    "reject canonical component instance identity collisions in component space" in {
      Given("two components whose raw instance names normalize to one stable identity")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val hyphenated = _generated_bundle_factory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata("domain", "dynamic-playwright"))
      )
      val underscored = _generated_bundle_factory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata("domain", "dynamic_playwright"))
      )

      When("both components are added to one component space")
      val result = intercept[IllegalArgumentException] {
        ComponentSpace().add(Vector(hyphenated, underscored))
      }

      Then("the stable identity collision fails before lookup")
      result.getMessage should include ("duplicate component instance id")
    }

    "separate primary and componentlets at construction time" in {
      Given("generated-style bundle factory")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))

      When("bundle is created")
      val bundle = _generated_bundle_factory.create(params)

      Then("primary and componentlets are explicit and initialized separately")
      bundle.primary.name shouldBe "domain"
      bundle.primary.isPrimaryParticipant shouldBe true
      bundle.componentlets.map(_.name) shouldBe Vector("notice-admin")
      bundle.componentlets.forall(_.isComponentletParticipant) shouldBe true
      bundle.participants.size shouldBe 2
      bundle.primary.core.factory shouldBe Some(_generated_bundle_factory.PrimaryFactory)
      bundle.componentlets.head.core.factory shouldBe Some(_generated_bundle_factory.NoticeAdminFactory)
    }

    "dispatch same-subsystem sync reception on generated componentlet with runtime identity" in {
      Given("bootstrapped generated runtime participants")
      _generated_bundle_factory.clearCalls()
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
      val bundle = _generated_bundle_factory.create(params)
      val factory = new ComponentFactory()
      val components = bundle.participants.map(factory.bootstrap)
      subsystem.add(components)
      val publisher = subsystem.components.find(_.name == "domain").getOrElse(fail("missing publisher component"))
      val target = subsystem.components.find(_.name == "notice-admin").getOrElse(fail("missing target componentlet"))

      When("publisher emits event through the shared subsystem event path")
      val result = publisher.eventReception.getOrElse(fail("missing publisher event reception")).receive(
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
      target.eventReception.isDefined shouldBe true
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      _generated_bundle_factory.calls.toVector shouldBe Vector("notice-admin")
    }

    "reject malformed bundle outputs deterministically" in {
      Given("bundle factory with duplicate participant names")
      val subsystem = TestComponentFactory.emptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))

      When("bundle is created")
      val ex = intercept[IllegalArgumentException] {
        _invalid_bundle_factory.create(params)
      }

      Then("construction fails before bootstrap")
      ex.getMessage should include ("duplicate participant name")
    }
  }

  private object _generated_bundle_factory extends Component.BundleFactory {
    private val _calls = ArrayBuffer.empty[String]

    def calls: ArrayBuffer[String] = _calls
    def clearCalls(): Unit = _calls.clear()

    object PrimaryFactory extends Component.PrimaryComponentFactory {
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
        }.withApplicationConfig(
          Component.ApplicationConfig(
            config = Some(Configuration(Map(
              "scraper.timeout" -> ConfigurationValue.StringValue("30s")
            )))
          )
        )

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

  private object _invalid_bundle_factory extends Component.BundleFactory {
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
