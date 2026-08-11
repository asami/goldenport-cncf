package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityPersistable
import org.goldenport.cncf.entity.aggregate.AggregateDefinition
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimePlan, PartitionStrategy, WorkingSetDefinition}
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiProviderComponent, SpiSelection}
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse, AiMessage, AiRecordRequest, AiRecordResponse, AiRunner as AiRunnerSpi, AiRunnerSocket}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 24, 2026
 *  version Apr. 24, 2026
 *  version May.  3, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryRuntimePlanActivationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentFactory discover bootstrap" should {
    "activate runtime plans supplied by component provider" in {
      Given("a component that provides a non-empty EntityRuntimePlan")
      val component = _component_with_runtime_plan()
      val space = new ComponentRepositorySpace() {
        override def discover(): Vector[Component] = Vector(component)
      }
      val factory = new ComponentFactory(space)

      When("discover bootstrap is executed")
      val discovered = factory.discover()
      val bootstrapped = discovered.head
      val collection = bootstrapped.entity[Any]("person")
      val memory = collection.storage.memoryRealm
        .getOrElse(fail("memory realm should be enabled by plan"))

      Then("plan branch is applied and plan-driven settings are active")
      discovered.size shouldBe 1
      bootstrapped.workingSetEntityNames should contain("person")
      collection.descriptor.plan.maxPartitions shouldBe 2
      collection.descriptor.plan.maxEntitiesPerPartition shouldBe 1
      collection.storage.storeRealm.values.size shouldBe 2
      _eventually_int(memory.cachedEntityCount, 1)
    }

    "canonicalize generated runtime-plan entity names against aggregate metadata" in {
      Given("a generated-style runtime plan and aggregate definition that differ only by naming form")
      val component = _component_with_case_variant_runtime_plan()
      val factory = new ComponentFactory()

      When("the component collections are bootstrapped")
      val bootstrapped = factory.bootstrap(component)

      Then("the aggregate entity name is the canonical EntitySpace key")
      bootstrapped.entitySpace.entityOption[Any]("facility") should not be empty
      bootstrapped.entitySpace.entityOption[Any]("Facility") shouldBe empty
      bootstrapped.entity[Any]("facility").descriptor.plan.entityName shouldBe "facility"
    }

    "reject an ambiguous normalized runtime-plan entity name" in {
      Given("two Aggregate roots whose Entity names normalize to the same segment")
      val component = _component_with_ambiguous_runtime_plan()
      val factory = new ComponentFactory()

      When("a runtime plan uses only the shared normalized spelling")
      val result = factory.bootstrapC(component)

      Then("assembly fails instead of selecting the first Aggregate by declaration order")
      result shouldBe a[Consequence.Failure[?]]
      result.asInstanceOf[Consequence.Failure[?]]
        .conclusion.display should include("ambiguously matches collections")
    }

    "bootstrap direct-added components when a subsystem receives an unbootstrapped instance" in {
      Given("a subsystem and a component created directly from a bundle factory")
      val subsystem = TestComponentFactory.emptySubsystem("runtime_plan_activation_direct_add")
      val component = _component_factory_bundle().createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Builtin)
      )

      When("the raw component is added to the subsystem")
      subsystem.add(component)
      val componentid = TestComponentFactory.componentId("runtime_plan_activation_direct_add")
      val resolved = subsystem.findComponent(componentid)
        .getOrElse(fail("missing bootstrapped component"))
      val collection = resolved.entity[Any]("person")

      Then("subsystem add bootstraps collections and runtime plans")
      resolved.collectionsBootstrapped shouldBe true
      collection.descriptor.collectionId shouldBe EntityCollectionId("sys", "sys", "person")
      collection.storage.storeRealm.values.size shouldBe 2
      resolved.workingSetEntityNames should contain("person")
    }

    "resolve SPI sockets after repository discovery and bootstrap" in {
      Given("a discovered provider component and a discovered socket component")
      val subsystem = TestComponentFactory.emptySubsystem("spi_discover_bootstrap")
      val provider = _initialized_component(
        subsystem,
        "spi_provider",
        new Component() with SpiProviderComponent {
          def spiProviders: Vector[SpiProvider[?]] =
            Vector(AiRunnerProvider("factory"))
        }
      )
      val consumer = _initialized_component(
        subsystem,
        "spi_consumer",
        new Component() with AiRunnerSocket {}
      )
      val space = new ComponentRepositorySpace() {
        override def discover(): Vector[Component] = Vector(provider, consumer)
      }
      val factory = new ComponentFactory(space)

      When("ComponentFactory discover bootstraps and resolves loaded components")
      val discovered = factory.discover()
      val socket = discovered.collectFirst {
        case m: AiRunnerSocket => m
      }.getOrElse(fail("missing AI runner socket component"))

      Then("the socket receives the provider SPI")
      given ExecutionContext = ExecutionContext.create()
      socket.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "factory:hello"
    }

    "replace a packaged component when a preferred development component has the same instance identity" in {
      Given("a subsystem containing a packaged component without its current SPI publication")
      val subsystem = TestComponentFactory.emptySubsystem("runtime_component_override")
      val packaged = _initialized_component(
        subsystem,
        "runtime_component_override_provider",
        new Component() {}
      )
      subsystem.add(packaged)

      And("a development component for the same logical instance with the SPI provider")
      val development = _initialized_component(
        subsystem,
        "runtime_component_override_provider",
        new Component() with SpiProviderComponent {
          def spiProviders: Vector[SpiProvider[?]] =
            Vector(AiRunnerProvider("development"))
        }
      )

      When("runtime assembly upserts the preferred development component")
      subsystem.upsert(Vector(development))
      val componentid = TestComponentFactory.componentId("runtime_component_override_provider")
      val instanceid = ComponentInstanceId.default(componentid)
      val matching = subsystem.components.toVector.filter(_.instanceId == instanceid)

      Then("the obsolete packaged instance is replaced rather than retained beside it")
      matching should have size 1
      matching.head should be theSameInstanceAs development
      matching.head.asInstanceOf[SpiProviderComponent].spiProviders should not be empty
    }

    "resolve SPI sockets after runtime extra components are added" in {
      Given("a CNCF runtime receives an SPI provider and socket through runtime extra components")
      var consumeropt: Option[AiRunnerSocket] = None
      val extras = (subsystem: org.goldenport.cncf.subsystem.Subsystem) => {
        val provider = _initialized_component(
          subsystem,
          "runtime_extra_spi_provider",
          new Component() with SpiProviderComponent {
            def spiProviders: Vector[SpiProvider[?]] =
              Vector(AiRunnerProvider("runtime-extra"))
          }
        )
        val consumer = _initialized_component(
          subsystem,
          "runtime_extra_spi_consumer",
          new Component() with AiRunnerSocket {}
        ).asInstanceOf[Component & AiRunnerSocket]
        consumeropt = Some(consumer)
        Seq(provider, consumer)
      }

      When("the runtime builds a command subsystem and adds the extra components")
      val subsystem = CncfRuntime.buildSubsystem(
        extracomponents = extras,
        mode = Some(RunMode.Command),
        args = Array(
          s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
          "--no-default-components"
        )
      )
      val socket = consumeropt.getOrElse(fail("missing runtime extra consumer"))

      Then("the runtime extra socket receives the provider SPI")
      subsystem.components.toVector.map(_.name) should contain("org.goldenport.cncf.test.RuntimeExtraSpiConsumer")
      socket.isSpiInstalled shouldBe true
      given ExecutionContext = ExecutionContext.create()
      socket.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "runtime-extra:hello"
    }
  }

  private def _eventually_int(value: => Int, expected: Int): Unit = {
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
    while (value != expected && System.nanoTime() < deadline)
      Thread.sleep(10)
    value shouldBe expected
  }

  private lazy val _controlled_test_descriptor_path: Path = {
    val path = Files.createTempFile("cncf-component-factory-runtime-plan-", ".yaml")
    Files.writeString(
      path,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: component-factory-runtime-plan-activation-spec
        |  time:
        |    mode: manual
        |    start-at: 2026-08-11T00:00:00Z
        |  random:
        |    mode: seeded
        |    seed: component-factory-runtime-plan-activation-spec
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    path
  }

  private def _component_with_runtime_plan(): Component = {
    val component = new Component() with EntityRuntimePlanProvider {
      private val _cid = EntityCollectionId("sys", "sys", "person")
      private val _first = RuntimePlanEntity(EntityId("tokyo", "sales", _cid), "taro")
      private val _second = RuntimePlanEntity(EntityId("tokyo", "sales", _cid), "jiro")

      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        Vector(
          EntityRuntimePlan[Any](
            entityName = "person",
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = Some(WorkingSetDefinition[Any]("person", Vector(_first, _second))),
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 2,
            maxEntitiesPerPartition = 1
          )
        )
    }
    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.RuntimePlanActivationSpec",
      componentid = ComponentId("org.goldenport.cncf.test.RuntimePlanActivationSpec"),
      instanceid = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.RuntimePlanActivationSpec")),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("runtime_plan_activation_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private def _component_with_case_variant_runtime_plan(): Component = {
    val component = new Component() with EntityRuntimePlanProvider {
      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        Vector(
          EntityRuntimePlan[Any](
            entityName = "Facility",
            memoryPolicy = EntityMemoryPolicy.StoreOnly,
            workingSet = None,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 64,
            maxEntitiesPerPartition = 10000
          )
        )

      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(AggregateDefinition(name = "facility", entityName = "facility"))
    }
    val componentid = ComponentId("org.goldenport.cncf.test.RuntimePlanCanonicalNameSpec")
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("runtime_plan_canonical_name_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private def _component_with_ambiguous_runtime_plan(): Component = {
    val component = new Component() with EntityRuntimePlanProvider {
      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        Vector(
          EntityRuntimePlan[Any](
            entityName = "FACILITY",
            memoryPolicy = EntityMemoryPolicy.StoreOnly,
            workingSet = None,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 64,
            maxEntitiesPerPartition = 10000
          )
        )

      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(name = "facility-lower", entityName = "facility"),
          AggregateDefinition(name = "facility-title", entityName = "Facility")
        )
    }
    val componentid = ComponentId("org.goldenport.cncf.test.RuntimePlanAmbiguousNameSpec")
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem(
        "runtime_plan_ambiguous_name_spec"
      ),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private def _component_factory_bundle(): Component.SinglePrimaryBundleFactory =
    new Component.SinglePrimaryBundleFactory with EntityRuntimePlanProvider {
      private val _cid = EntityCollectionId("sys", "sys", "person")
      private val _first = RuntimePlanEntity(EntityId("tokyo", "sales", _cid), "taro")
      private val _second = RuntimePlanEntity(EntityId("tokyo", "sales", _cid), "jiro")

      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        Vector(
          EntityRuntimePlan[Any](
            entityName = "person",
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = Some(WorkingSetDefinition[Any]("person", Vector(_first, _second))),
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 2,
            maxEntitiesPerPartition = 1
          )
        )

      override protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      override protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          name = "org.goldenport.cncf.test.RuntimePlanActivationDirectAdd",
          componentid = ComponentId("org.goldenport.cncf.test.RuntimePlanActivationDirectAdd"),
          instanceid = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.RuntimePlanActivationDirectAdd")),
          protocol = Protocol.empty,
          factory = this
        )
    }

  private def _initialized_component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    name: String,
    component: Component
  ): Component = {
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private final case class AiRunnerProvider(
    name: String
  ) extends SpiProvider[AiRunnerSpi] {
    def supports(
      contract: SpiContract[AiRunnerSpi],
      selection: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == "ai-runner" &&
        contract.runtimeClass == classOf[AiRunnerSpi]

    def provide(
      contract: SpiContract[AiRunnerSpi],
      selection: SpiSelection
    )(using ExecutionContext): Consequence[AiRunnerSpi] =
      Consequence.success(AiRunner(name))
  }

  private final case class AiRunner(
    name: String
  ) extends AiRunnerSpi {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.serviceUnavailable("generateRecord is not used by this spec")

    def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
      Consequence.success(AiChatResponse(AiMessage("assistant", name)))
  }

  private final case class RuntimePlanEntity(
    id: EntityId,
    name: String
  ) extends EntityPersistable {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
        "name" -> name
      )
  }
}
