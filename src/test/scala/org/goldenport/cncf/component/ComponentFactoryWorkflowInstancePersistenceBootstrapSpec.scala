package org.goldenport.cncf.component

import java.nio.file.{Files, Path}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as RuntimeConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Source-level Factory + managed lifecycle acceptance, not a CLI/MCP or joint recovery spec.
 * @since   Sep. 29, 2026
 * @version Oct.  8, 2026
 */
final class ComponentFactoryWorkflowInstancePersistenceBootstrapSpec
  extends AnyWordSpec with Matchers with GivenWhenThen {

  private val owner = ComponentId("org.goldenport.cncf.test.WorkflowInstanceBootstrapSpec")
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("store-1")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("store-1")))
  private val collection = DataStore.CollectionId("workflow_instance_v1")
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }

  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }

  private def definition: DefinitionBinding = {
    val sidecar = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
    take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
      resource("/workflow/candidate-admission-workflow-abi.json").trim, sidecar,
      "modeler/candidate-admission-workflow.cml"))))
  }

  private def declaration = WorkflowInstancePersistenceBinding.Declaration(config, Vector(definition), collection)
  private def initial: InstanceRecord = InstanceRecord(InstanceIdentity("instance-1"),
    definition, initialRevision, Lifecycle.NotStarted, None, CorrelationReference("correlation-1"),
    CausationReference("start-1"), None, None, None, Vector.empty)
  private val active = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
    Some(ProgressionReference("review.Awaiting")), CorrelationReference("correlation-1"),
    CausationReference("start-1"), None, None, None)

  private def configuration(values: (String, String)*): ResolvedConfiguration =
    ResolvedConfiguration(RuntimeConfiguration(values.map { case (k, v) =>
      k -> ConfigurationValue.StringValue(v)
    }.toMap), ConfigurationTrace.empty)

  private def sqliteConfiguration(path: Path): ResolvedConfiguration = {
    val selector = ComponentDataStore.Request.forComponent(owner, "workflow").canonicalComponentSelector.get
    configuration(
      s"textus.component.$selector.datastores.workflow.kind" -> "sqlite",
      s"textus.component.$selector.datastores.workflow.path" -> path.toString)
  }

  private def initialize(component: Component, factory: Component.Factory,
    subsystem: Subsystem, componentId: ComponentId = owner): Component =
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = Component.Core.create(componentId.name, componentId,
        ComponentInstanceId.default(componentId), Protocol.empty, factory),
      origin = ComponentOrigin.Builtin))

  private class SourceFactory(value: Option[WorkflowInstancePersistenceBinding.Declaration])
    extends Component.Factory {
    var selections = 0
    override def workflowInstancePersistenceDeclarationOption = {
      selections += 1
      value
    }
    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("fixture does not create components")
    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      throw new UnsupportedOperationException("fixture does not create cores")
  }

  private class SourceComponent(value: Option[WorkflowInstancePersistenceBinding.Declaration])
    extends Component with WorkflowInstancePersistenceSource {
    override def workflowInstancePersistenceDeclarationOption = value
  }

  "ComponentFactory Workflow instance persistence" should {
    "keep unconfigured legacy components opt-out without creating an implicit instance store" in {
      Given("a legacy component with no persistence declaration")
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val component = initialize(new Component() {}, new SourceFactory(None), subsystem)
        When("normal Factory bootstrap completes")
        take(new ComponentFactory().bootstrapC(component))
        Then("legacy bootstrap succeeds but requiring persistence fails closed")
        component.collectionsBootstrapped shouldBe true
        component.workflowInstancePersistenceBindingOption shouldBe None
        component.requireWorkflowInstancePersistenceBindingC.toOption shouldBe None
        subsystem.systemNode.managedDataStoreCount shouldBe 0
      }
    }

    "inject the Factory declaration and borrow managed storage for every fresh execution context" in {
      Given("an explicit canonical SQLite declaration and a component without a source")
      val path = Files.createTempDirectory("cncf-phase88-bootstrap-").resolve("workflow.sqlite")
      TestComponentFactory.withEmptySubsystem(configuration = sqliteConfiguration(path)) { subsystem =>
        val source = new SourceFactory(Some(declaration))
        val component = initialize(new Component() {}, source, subsystem)
        val factory = new ComponentFactory()
        take(factory.bootstrapC(component))
        val bound = take(component.requireWorkflowInstancePersistenceBindingC)
        bound.componentId shouldBe owner
        bound.declaration.configuration shouldBe config
        val record = initial
        val firstContext = component.logic.executionContext()
        val sentinel = DataStore.inMemorySearchable()
        firstContext.dataStoreSpace.bindDataStore(sentinel)
        val priorScope = firstContext.dataStoreSpace.captureBinding()
        When("separate contexts create, append and query after bootstrap's lease has ended")
        take(bound.createC(record)(using firstContext))
        take(bound.appendC(record.identity, initialRevision, active)(using component.logic.executionContext()))
        val loaded = take(bound.loadC(record.identity)(using component.logic.executionContext())).get
        Then("one node-owned resource serves exact guarded SPI semantics without changing caller scope")
        loaded.history shouldBe Vector(active)
        bound.appendC(record.identity, initialRevision, active)(using component.logic.executionContext())
          .toOption shouldBe None
        firstContext.dataStoreSpace.captureBinding() shouldBe priorScope
        take(factory.bootstrapC(component)) shouldBe component
        take(component.requireWorkflowInstancePersistenceBindingC) shouldBe bound
        source.selections shouldBe 1
        subsystem.systemNode.managedDataStoreCount shouldBe 1
      }
    }

    "prefer the Component source and never evaluate a competing Factory declaration" in {
      Given("a valid Component declaration and an invalid Factory declaration")
      val path = Files.createTempDirectory("cncf-phase88-precedence-").resolve("workflow.sqlite")
      TestComponentFactory.withEmptySubsystem(configuration = sqliteConfiguration(path)) { subsystem =>
        val factory = new SourceFactory(Some(null))
        val component = initialize(new SourceComponent(Some(declaration)), factory, subsystem)
        When("the canonical bootstrap selects its source")
        take(new ComponentFactory().bootstrapC(component))
        Then("only the explicitly selected Component source binds")
        component.workflowInstancePersistenceBindingOption.nonEmpty shouldBe true
        factory.selections shouldBe 0
      }
    }

    "treat an explicit Component opt-out as authoritative rather than falling through to the Factory" in {
      Given("a Component source returning None and a Factory requesting persistence")
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val factory = new SourceFactory(Some(declaration))
        val component = initialize(new SourceComponent(None), factory, subsystem)
        When("the component is bootstrapped without any datastore configuration")
        take(new ComponentFactory().bootstrapC(component))
        Then("no Factory fallback, provider or resource is installed")
        component.workflowInstancePersistenceBindingOption shouldBe None
        factory.selections shouldBe 0
        subsystem.systemNode.managedDataStoreCount shouldBe 0
      }
    }

    "reject configured persistence when the canonical durable store is absent or explicitly memory-only" in {
      Given("an admitted declaration but no durable canonical store")
      val selector = ComponentDataStore.Request.forComponent(owner, "workflow").canonicalComponentSelector.get
      Vector(configuration(), configuration(s"textus.component.$selector.datastores.workflow.kind" -> "memory"))
        .foreach { runtimeConfiguration =>
          TestComponentFactory.withEmptySubsystem(configuration = runtimeConfiguration) { subsystem =>
            val component = initialize(new Component() {}, new SourceFactory(Some(declaration)), subsystem)
            When("normal bootstrap attempts the required binding")
            new ComponentFactory().bootstrapC(component).toOption shouldBe None
            Then("bootstrap cannot advertise a persistence binding or use an implicit fallback")
            component.collectionsBootstrapped shouldBe false
            component.workflowInstancePersistenceBindingOption shouldBe None
            subsystem.systemNode.managedDataStoreCount shouldBe 0
          }
        }
    }

    "reject malformed policies, catalog, collection and store names before opening a resource" in {
      Given("malformed declarations and an otherwise valid SQLite configuration")
      val valid = declaration
      val variants = Vector(null, valid.copy(definitions = Vector.empty),
        valid.copy(configuration = config.copy(retentionPolicy = RetentionPolicy(""))),
        valid.copy(configuration = config.copy(workflowStore = config.workflowStore.copy(name = "Work flow"))),
        valid.copy(collection = null))
      variants.foreach { value =>
        val path = Files.createTempDirectory("cncf-phase88-invalid-").resolve("workflow.sqlite")
        TestComponentFactory.withEmptySubsystem(configuration = sqliteConfiguration(path)) { subsystem =>
          val component = initialize(new Component() {}, new SourceFactory(Some(value)), subsystem)
          When("normal bootstrap preflights the declaration")
          new ComponentFactory().bootstrapC(component).toOption shouldBe None
          Then("invalid input creates neither a binding nor a physical database")
          component.workflowInstancePersistenceBindingOption shouldBe None
          component.collectionsBootstrapped shouldBe false
          subsystem.systemNode.managedDataStoreCount shouldBe 0
          Files.exists(path) shouldBe false
        }
      }
    }

    "admit one expected-revision winner through concurrent lease-scoped binding calls" in {
      Given("one bootstrapped binding shared by two fresh operation contexts")
      val path = Files.createTempDirectory("cncf-phase88-bootstrap-cas-").resolve("workflow.sqlite")
      TestComponentFactory.withEmptySubsystem(configuration = sqliteConfiguration(path)) { subsystem =>
        val component = initialize(new Component() {}, new SourceFactory(Some(declaration)), subsystem)
        take(new ComponentFactory().bootstrapC(component))
        val bound = take(component.requireWorkflowInstancePersistenceBindingC)
        val record = initial
        take(bound.createC(record)(using component.logic.executionContext()))
        val pool = Executors.newFixedThreadPool(2)
        try {
          val gate = new CountDownLatch(1)
          val futures = Vector.fill(2) {
            pool.submit(new java.util.concurrent.Callable[Consequence[InstanceRecord]] {
              def call(): Consequence[InstanceRecord] = {
                val context = component.logic.executionContext()
                if (!gate.await(10L, TimeUnit.SECONDS))
                  throw new IllegalStateException("bootstrap CAS gate timed out")
                bound.appendC(record.identity, initialRevision, active)(using context)
              }
            })
          }
          When("both guarded calls resolve and borrow the same node-owned resource")
          gate.countDown()
          val results = futures.map(_.get(20L, TimeUnit.SECONDS))
          Then("one revision is consumed and no released bootstrap lease or caller scope is shared")
          results.count(_.toOption.nonEmpty) shouldBe 1
          val loaded = take(bound.loadC(record.identity)(using component.logic.executionContext())).get
          loaded.revision shouldBe InstanceRevision(1L)
          loaded.history shouldBe Vector(active)
          subsystem.systemNode.managedDataStoreCount shouldBe 1
        } finally {
          pool.shutdownNow()
          pool.awaitTermination(20L, TimeUnit.SECONDS)
        }
      }
    }

    "reopen exact instance history through a new Subsystem and reject use after owner shutdown" in {
      Given("a bootstrapped component that created an instance in node-owned SQLite")
      val path = Files.createTempDirectory("cncf-phase88-node-reopen-").resolve("workflow.sqlite")
      val runtimeConfiguration = sqliteConfiguration(path)
      var oldBinding: Option[WorkflowInstancePersistenceBinding] = None
      var oldSubsystem: Option[Subsystem] = None
      val record = initial
      TestComponentFactory.withEmptySubsystem(configuration = runtimeConfiguration) { subsystem =>
        val component = initialize(new Component() {}, new SourceFactory(Some(declaration)), subsystem)
        take(new ComponentFactory().bootstrapC(component))
        val bound = take(component.requireWorkflowInstancePersistenceBindingC)
        take(bound.createC(record)(using component.logic.executionContext()))
        take(bound.appendC(record.identity, initialRevision, active)(using component.logic.executionContext()))
        oldBinding = Some(bound)
        oldSubsystem = Some(subsystem)
      }
      oldSubsystem.get.systemNode.managedDataStoreCount shouldBe 0
      oldBinding.get.loadC(record.identity)(using ExecutionContext.create()).toOption shouldBe None
      When("a separate SystemNode/Subsystem bootstraps a freshly admitted catalog against the same configured file")
      TestComponentFactory.withEmptySubsystem(configuration = runtimeConfiguration) { subsystem =>
        val component = initialize(new Component() {}, new SourceFactory(Some(declaration)), subsystem)
        take(new ComponentFactory().bootstrapC(component))
        val bound = take(component.requireWorkflowInstancePersistenceBindingC)
        val loaded = take(bound.loadC(record.identity)(using component.logic.executionContext())).get
        Then("the exact committed instance and history survive without a retained old provider or lease")
        loaded.identity shouldBe record.identity
        loaded.revision shouldBe InstanceRevision(1L)
        loaded.history shouldBe Vector(active)
        loaded.definition.validateC.toOption.nonEmpty shouldBe true
        (loaded.definition eq record.definition) shouldBe false
        bound.appendC(record.identity, initialRevision, active)(using component.logic.executionContext())
          .toOption shouldBe None
      }
    }
  }
}
