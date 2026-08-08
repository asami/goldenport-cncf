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
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedComponentBundleFactorySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _domain_component_id = ComponentId("org.goldenport.fixture.Domain")
  private val _notice_admin_component_id = ComponentId("org.goldenport.fixture.NoticeAdmin")
  private val _duplicate_component_id = ComponentId("org.goldenport.fixture.Duplicate")

  private val _e1 = afterWord("in spec:generated-component-bundle-factory, example:E1, rules:CID05C-R12, phase:56, slice:CID-05C")
  private val _e2 = afterWord("in spec:generated-component-bundle-factory, example:E2, rules:CID05C-R12, phase:56, slice:CID-05C")
  private val _e3 = afterWord("in spec:generated-component-bundle-factory, example:E3, rules:CID05C-R12, phase:56, slice:CID-05C")
  private val _e4 = afterWord("in spec:generated-component-bundle-factory, example:E4, rules:CID05C-R12, phase:56, slice:CID-05C")
  private val _e5 = afterWord("in spec:generated-component-bundle-factory, example:E5, rules:CID05C-R12, phase:56, slice:CID-05C")
  private val _e6 = afterWord("in spec:generated-component-bundle-factory, example:E6, rules:CID05C-R12, phase:56, slice:CID-05C")
  private val _e7 = afterWord("in spec:generated-component-bundle-factory, example:E7, rules:CID05C-R12, phase:56, slice:CID-05C")

  "Generated-style bundle factory" should {
    "E1 apply named instance identity and local properties during construction" must _e1 {
      "when exercising: apply named instance identity and local properties during construction" in {
      Given("a generated component factory and named instance metadata")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-bundle")
      val metadata = ComponentInstanceMetadata(
        componentName = "textus-scraper",
        instance = "dynamic-playwright",
        config = Map("scraper.mode" -> "dynamic"),
        rules = Record.data("navigation" -> Record.data("max-pages" -> 8)),
        purposes = Vector("javascript-heavy-site"),
        tags = Vector("dynamic", "browser"),
        priority = 100,
        isDefault = true,
        componentId = Some(_domain_component_id)
      )
      val params = ComponentCreate(
        subsystem,
        ComponentOrigin.Repository("cozy-generated")
      ).withInstanceMetadata(metadata)

      When("the named instance is created")
      val component = GeneratedBundleFactory.PrimaryFactory.createPrimary(params)
      val resolved = component.logic.executionContext().runtime.resolvedParameters.get("scraper.mode")
      val packaged = component.logic.executionContext().runtime.resolvedParameters.get("scraper.timeout")

      Then("identity, rules, and ExecutionContext properties belong to that instance")
      component.componentId shouldBe _domain_component_id
      component.name shouldBe _domain_component_id.name
      component.instanceId shouldBe ComponentInstanceId(_domain_component_id, "dynamic-playwright")
      component.instanceMetadata shouldBe Some(metadata)
      resolved.map(_.value) shouldBe Some(ConfigurationValue.StringValue("dynamic"))
      resolved.map(_.source) shouldBe Some(org.goldenport.cncf.config.ResolvedParameter.Source.Component("dynamic-playwright"))
      packaged.map(_.value) shouldBe Some(ConfigurationValue.StringValue("30s"))
      }
    }

    "E2 resolve entity runtime descriptors declared by generated components" must _e2 {
      "when exercising: resolve entity runtime descriptors declared by generated components" in {
      Given("a generated component override supplies CML entity descriptors")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-descriptor")

      When("the component is created without separately injected descriptors")
      val component = GeneratedBundleFactory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
      )

      Then("runtime policy resolves the generated descriptor by entity and collection name")
      component.entityRuntimeDescriptor("SharedNotice").map(_.usageKind) should contain(
        org.goldenport.cncf.security.EntityUsageKind.SharedRecord
      )
      component.entityRuntimeDescriptor("shared_notice").map(_.usageKind) should contain(
        org.goldenport.cncf.security.EntityUsageKind.SharedRecord
      )
      }
    }

    "E3 keep named instances in component space and select the declared default by name" must _e3 {
      "when exercising: keep named instances in component space and select the declared default by name" in {
      Given("two instances created from one component factory")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-bundle")
      val static = GeneratedBundleFactory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata(
            "domain",
            "static",
            isDefault = true,
            componentId = Some(_domain_component_id)
          ))
      )
      val dynamic = GeneratedBundleFactory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata(
            "domain",
            "dynamic-playwright",
            componentId = Some(_domain_component_id)
          ))
      )

      When("both instances are added to component space")
      val space = ComponentSpace().add(Vector(dynamic, static))

      Then("exact labels preserve both while name lookup resolves the declared default")
      space.components.size shouldBe 2
      space.findInstance(ComponentInstanceId(_domain_component_id, "static")) shouldBe Some(static)
      space.findInstance(ComponentInstanceId(_domain_component_id, "dynamic-playwright")) shouldBe Some(dynamic)
      space.findInstance(ComponentInstanceId(_domain_component_id, "dynamic_playwright")) shouldBe None
      space.find(ComponentLocator.NameLocator("domain")) shouldBe Some(static)
      }
    }

    "E4 preserve exact component instance labels without punctuation normalization collisions" must _e4 {
      "when exercising: preserve exact component instance labels without punctuation normalization collisions" in {
      Given("two components whose exact instance labels differ by hyphen and underscore")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-bundle")
      val hyphenated = GeneratedBundleFactory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata(
            "domain",
            "dynamic-playwright",
            componentId = Some(_domain_component_id)
          ))
      )
      val underscored = GeneratedBundleFactory.PrimaryFactory.createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
          .withInstanceMetadata(ComponentInstanceMetadata(
            "domain",
            "dynamic_playwright",
            componentId = Some(_domain_component_id)
          ))
      )

      When("both exact identities are added to one component space")
      val space = ComponentSpace().add(Vector(hyphenated, underscored))

      Then("each exact label remains independently addressable")
      space.components.size shouldBe 2
      space.findInstance(ComponentInstanceId(_domain_component_id, "dynamic-playwright")) shouldBe Some(hyphenated)
      space.findInstance(ComponentInstanceId(_domain_component_id, "dynamic_playwright")) shouldBe Some(underscored)
      }
    }

    "E5 separate primary and componentlets at construction time" must _e5 {
      "when exercising: separate primary and componentlets at construction time" in {
      Given("generated-style bundle factory")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))

      When("bundle is created")
      val bundle = GeneratedBundleFactory.create(params)

      Then("primary and componentlets are explicit and initialized separately")
      bundle.primary.name shouldBe _domain_component_id.name
      bundle.primary.displayName shouldBe "domain"
      bundle.primary.isPrimaryParticipant shouldBe true
      bundle.componentlets.map(_.name) shouldBe Vector(_notice_admin_component_id.name)
      bundle.componentlets.map(_.displayName) shouldBe Vector("notice-admin")
      bundle.componentlets.forall(_.isComponentletParticipant) shouldBe true
      bundle.participants.size shouldBe 2
      bundle.primary.core.factory shouldBe Some(GeneratedBundleFactory.PrimaryFactory)
      bundle.componentlets.head.core.factory shouldBe Some(GeneratedBundleFactory.NoticeAdminFactory)
      }
    }

    "E6 dispatch same-subsystem sync reception on generated componentlet with runtime identity" must _e6 {
      "when exercising: dispatch same-subsystem sync reception on generated componentlet with runtime identity" in {
      Given("bootstrapped generated runtime participants")
      GeneratedBundleFactory.clearCalls()
      val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))
      val bundle = GeneratedBundleFactory.create(params)
      val factory = new ComponentFactory()
      val components = bundle.participants.map(factory.bootstrap)
      subsystem.add(components)
      val publisher = subsystem.components.find(_.displayName == "domain").getOrElse(fail("missing publisher component"))
      val target = subsystem.components.find(_.displayName == "notice-admin").getOrElse(fail("missing target componentlet"))

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
      publisher.componentId shouldBe _domain_component_id
      target.componentId shouldBe _notice_admin_component_id
      target.eventReception.isDefined shouldBe true
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      GeneratedBundleFactory.calls.toVector shouldBe Vector("notice-admin")
      }
    }

    "E7 reject malformed bundle outputs deterministically" must _e7 {
      "when exercising: reject malformed bundle outputs deterministically" in {
      Given("bundle factory with duplicate participant names")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("generated-bundle")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("cozy-generated"))

      When("bundle is created")
      val ex = intercept[IllegalArgumentException] {
        InvalidBundleFactory.create(params)
      }

      Then("construction fails before bootstrap")
      ex.getMessage should include ("duplicate participant name")
      }
    }
  }

  private object GeneratedBundleFactory extends Component.BundleFactory {
    private val _calls = ArrayBuffer.empty[String]

    def calls: ArrayBuffer[String] = _calls
    def clearCalls(): Unit = _calls.clear()

    object PrimaryFactory extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {
          override def displayName: String = "domain"

          override def componentDescriptors: Vector[ComponentDescriptor] =
            Vector(ComponentDescriptor(
              name = Some("domain"),
              componentName = Some("domain"),
              entityRuntimeDescriptors = Vector(
                org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor(
                  entityName = "SharedNotice",
                  collectionId = org.simplemodeling.model.datatype.EntityCollectionId("test", "domain", "shared_notice"),
                  memoryPolicy = org.goldenport.cncf.entity.runtime.EntityMemoryPolicy.LoadToMemory,
                  partitionStrategy = org.goldenport.cncf.entity.runtime.PartitionStrategy.byOrganizationMonthUTC,
                  maxPartitions = 4,
                  maxEntitiesPerPartition = 16,
                  usageKind = org.goldenport.cncf.security.EntityUsageKind.SharedRecord,
                  applicationDomain = org.goldenport.cncf.security.EntityApplicationDomain.Business
                )
              )
            ))

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
          GeneratedComponentBundleFactorySpec.this._domain_component_id.name,
          GeneratedComponentBundleFactorySpec.this._domain_component_id,
          ComponentInstanceId.default(GeneratedComponentBundleFactorySpec.this._domain_component_id),
          Protocol.empty,
          this
        )
    }

    object NoticeAdminFactory extends Component.ComponentletFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {
          override def displayName: String = "notice-admin"

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
            Consequence.success(SyncNoticeAction(req))
        }
        val service = spec.ServiceDefinition(
          name = "notice",
          operations = spec.OperationDefinitionGroup(
            operations = NonEmptyVector.of(operation)
          )
        )
        Component.Core.create(
          GeneratedComponentBundleFactorySpec.this._notice_admin_component_id.name,
          GeneratedComponentBundleFactorySpec.this._notice_admin_component_id,
          ComponentInstanceId.default(GeneratedComponentBundleFactorySpec.this._notice_admin_component_id),
          Protocol(
            services = spec.ServiceDefinitionGroup(Vector(service)),
            handler = ProtocolHandler.default
          ),
          this
        )
      }

      private final case class SyncNoticeAction(
        request: Request
      ) extends QueryAction {
        def createCall(core: ActionCall.Core): ActionCall =
          SyncNoticeActionCall(core)
      }

      private final case class SyncNoticeActionCall(
        core: ActionCall.Core
      ) extends ProcedureActionCall {
        def execute(): Consequence[OperationResponse] = {
          _calls += core.component.map(_.displayName).getOrElse("missing")
          Consequence.success(OperationResponse.Scalar("ok"))
        }
      }
    }

    def primaryFactory: Component.PrimaryComponentFactory =
      PrimaryFactory

    override def componentletFactories: Vector[Component.ComponentletFactory] =
      Vector(NoticeAdminFactory)
  }

  private object InvalidBundleFactory extends Component.BundleFactory {
    object PrimaryFactory extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {
          override def displayName: String = "duplicate"
        }

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          GeneratedComponentBundleFactorySpec.this._duplicate_component_id.name,
          GeneratedComponentBundleFactorySpec.this._duplicate_component_id,
          ComponentInstanceId.default(GeneratedComponentBundleFactorySpec.this._duplicate_component_id),
          Protocol.empty,
          this
        )
    }

    object DuplicateComponentletFactory extends Component.ComponentletFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {
          override def displayName: String = "duplicate"
        }

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          GeneratedComponentBundleFactorySpec.this._duplicate_component_id.name,
          GeneratedComponentBundleFactorySpec.this._duplicate_component_id,
          ComponentInstanceId.default(GeneratedComponentBundleFactorySpec.this._duplicate_component_id),
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
