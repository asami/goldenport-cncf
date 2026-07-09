package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityPersistable
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimePlan, PartitionStrategy, WorkingSetDefinition}
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiProviderComponent, SpiSelection}
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse, AiMessage, AiRecordRequest, AiRecordResponse, AiRunner, AiRunnerSocket}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 24, 2026
 *  version Apr. 24, 2026
 *  version May.  3, 2026
 * @version Jul.  9, 2026
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

    "bootstrap direct-added components when a subsystem receives an unbootstrapped instance" in {
      Given("a subsystem and a component created directly from a bundle factory")
      val subsystem = TestComponentFactory.emptySubsystem("runtime_plan_activation_direct_add")
      val component = _component_factory_bundle().createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Builtin)
      )

      When("the raw component is added to the subsystem")
      subsystem.add(component)
      val resolved = subsystem.findComponent("runtime_plan_activation_direct_add")
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
            Vector(_AiRunnerProvider("factory"))
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

    "resolve SPI sockets after runtime extra components are added" in {
      Given("a CNCF runtime receives an SPI provider and socket through runtime extra components")
      var consumeropt: Option[AiRunnerSocket] = None
      val extras = (subsystem: org.goldenport.cncf.subsystem.Subsystem) => {
        val provider = _initialized_component(
          subsystem,
          "runtime_extra_spi_provider",
          new Component() with SpiProviderComponent {
            def spiProviders: Vector[SpiProvider[?]] =
              Vector(_AiRunnerProvider("runtime-extra"))
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
        args = Array("--no-default-components")
      )
      val socket = consumeropt.getOrElse(fail("missing runtime extra consumer"))

      Then("the runtime extra socket receives the provider SPI")
      subsystem.components.toVector.map(_.name) should contain("runtime_extra_spi_consumer")
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

  private def _component_with_runtime_plan(): Component = {
    val component = new Component() with EntityRuntimePlanProvider {
      private val _cid = EntityCollectionId("sys", "sys", "person")
      private val _first = _Entity(EntityId("tokyo", "sales", _cid), "taro")
      private val _second = _Entity(EntityId("tokyo", "sales", _cid), "jiro")

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
      name = "runtime_plan_activation_spec",
      componentid = ComponentId("runtime_plan_activation_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("runtime_plan_activation_spec")),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("runtime_plan_activation_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private def _component_factory_bundle(): Component.SinglePrimaryBundleFactory =
    new Component.SinglePrimaryBundleFactory with EntityRuntimePlanProvider {
      private val _cid = EntityCollectionId("sys", "sys", "person")
      private val _first = _Entity(EntityId("tokyo", "sales", _cid), "taro")
      private val _second = _Entity(EntityId("tokyo", "sales", _cid), "jiro")

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
          name = "runtime_plan_activation_direct_add",
          componentid = ComponentId("runtime_plan_activation_direct_add"),
          instanceid = ComponentInstanceId.default(ComponentId("runtime_plan_activation_direct_add")),
          protocol = Protocol.empty,
          factory = this
        )
    }

  private def _initialized_component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    name: String,
    component: Component
  ): Component = {
    val componentid = ComponentId(name)
    val core = Component.Core.create(
      name = name,
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

  private final case class _AiRunnerProvider(
    name: String
  ) extends SpiProvider[AiRunner] {
    def supports(
      contract: SpiContract[AiRunner],
      selection: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == "ai-runner" &&
        contract.runtimeClass == classOf[AiRunner]

    def provide(
      contract: SpiContract[AiRunner],
      selection: SpiSelection
    )(using ExecutionContext): Consequence[AiRunner] =
      Consequence.success(_AiRunner(name))
  }

  private final case class _AiRunner(
    name: String
  ) extends AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.serviceUnavailable("generateRecord is not used by this spec")

    def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
      Consequence.success(AiChatResponse(AiMessage("assistant", name)))
  }

  private final case class _Entity(
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
