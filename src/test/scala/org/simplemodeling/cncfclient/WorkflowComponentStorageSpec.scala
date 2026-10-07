package org.simplemodeling.cncfclient

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.{Subsystem, SubsystemEventStoreBootstrap}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * Deliberately outside CNCF's private package: only public composition APIs.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 */
final class WorkflowComponentStorageSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Component storage fixture rejected: $failure")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val producer = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/candidate-admission-producer-abi.json")))
  private val definition = take(bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
    resource("/workflow/candidate-admission-workflow-abi.json").trim, producer,
    "modeler/candidate-admission-workflow.cml"))))
  private val owner = ComponentId("org.simplemodeling.cncfclient.FactoryWorkflow")
  private def collection(name: String) = DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "factoryworkflow", name))
  private val instances = collection("instances")
  private val continuations = collection("continuations")
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("factory-workflow")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("factory-workflow")))
  private val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "review.text.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("expected text")
  }
  private val operation = StateMachineOperationIdentity("review", "submit")
  private val policy = take(EventPersistencePolicy.createC("factory-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow", instances, continuations))))
  private def configuration(path: Path) = ResolvedConfiguration(HostConfiguration(Map(
    "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
    "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
  private def host(path: Path) = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
    ScopeContext(ScopeKind.Subsystem, "factory-workflow", None, context.observability), configuration(path)))
  private val initial = InstanceRecord(InstanceIdentity("factory-instance"), definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("start"),
    None, None, None, Vector.empty)

  private final class Factory extends Component.PrimaryComponentFactory {
    var binding: Option[WorkflowInstancePersistenceBinding] = None
    var issued: Option[WorkflowManagedIssuedWork[String]] = None
    var completion: Option[WorkflowManagedCompletion[String]] = None
    var eventsCalled = false
    protected def create_Component(params: ComponentCreate): Component = new Component() {}
    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
      comp.coreOption shouldBe None
      val storage = take(WorkflowComponentStorage.bindC(params, owner, config,
        Vector(definition), instances, continuations))
      binding = Some(take(storage.bindInstancesC()))
      issued = Some(take(storage.bindIssuedC(operation, codec)))
      completion = Some(take(storage.bindCompletionC(operation, codec, codec,
        StateMachineResultTypeReference(codec.typeIdentity),
        (_, _) => { eventsCalled = true; Consequence.success(Vector.empty) })))
      Component.Core.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol())
    }
  }

  "Public Workflow Component storage composition" should {
    "bind inside create_Core before initialization and reconstruct storage after reopen" in {
      Given("a declared SQLite host and a component factory that binds storage inside create_Core")
      val path = Files.createTempDirectory("workflow-component-core-").resolve("host.sqlite")
      val first = host(path)
      val factory = new Factory
      try {
        When("the primary component is constructed")
        val component = take(factory.createPrimaryC(ComponentCreate(first.subsystem, ComponentOrigin.Builtin)))
        Then("storage is bound before initialization without producing events")
        component.componentId shouldBe owner
        factory.eventsCalled shouldBe false
        factory.issued.nonEmpty shouldBe true
        factory.completion.nonEmpty shouldBe true
        When("the new instance identity is loaded")
        val absent = take(factory.binding.get.loadC(initial.identity))
        Then("no instance is present")
        absent shouldBe None
        When("the initial instance is created")
        val created = take(factory.binding.get.createC(initial))
        Then("the exact initial instance is retained")
        created shouldBe initial
      } finally take(first.shutdownC())
      When("the old binding is used after host shutdown")
      val released = factory.binding.get.loadC(initial.identity)
      Then("its expired authority cannot load the instance")
      released.toOption shouldBe None
      Given("the same SQLite destination reopened under a new host and factory")
      val reopened = host(path)
      try {
        val rebuilt = new Factory
        When("the component and its instance binding are reconstructed")
        take(rebuilt.createPrimaryC(ComponentCreate(reopened.subsystem, ComponentOrigin.Builtin)))
        val recovered = take(rebuilt.binding.get.loadC(initial.identity))
        Then("the retained instance is recovered without producing events")
        recovered shouldBe Some(initial)
        rebuilt.eventsCalled shouldBe false
      } finally take(reopened.shutdownC())
    }

    "reject foreign composition inputs against the captured host grant" in {
      Given("a live host grant and its exact component, stores and definition")
      val current = host(Files.createTempDirectory("workflow-component-admission-").resolve("host.sqlite"))
      try {
        val params = ComponentCreate(current.subsystem, ComponentOrigin.Builtin)
        def attempt(component: ComponentId = owner, configured: Configuration = config,
          roots: DataStore.CollectionId = instances, successors: DataStore.CollectionId = continuations,
          definitions: Vector[DefinitionBinding] = Vector(definition)) =
          WorkflowComponentStorage.bindC(params, component, configured, definitions, roots, successors)
        When("the exact granted composition is bound")
        take(attempt())
        When("a foreign component requests the same composition")
        val foreigncomponent = attempt(component = ComponentId("org.simplemodeling.cncfclient.Foreign"))
        Then("the foreign owner is rejected")
        foreigncomponent.toOption shouldBe None
        When("an undeclared root collection is supplied")
        val foreignroot = attempt(roots = collection("foreign"))
        Then("the root mismatch is rejected")
        foreignroot.toOption shouldBe None
        When("an undeclared successor collection is supplied")
        val foreignsuccessor = attempt(successors = collection("foreign"))
        Then("the successor mismatch is rejected")
        foreignsuccessor.toOption shouldBe None
        When("a foreign workflow store is supplied")
        val foreignstore = attempt(configured = config.copy(workflowStore = NamedWorkflowStore("foreign", config.workflowStore.identity)))
        Then("the store mismatch is rejected")
        foreignstore.toOption shouldBe None
        When("the definition is duplicated")
        val duplicated = attempt(definitions = Vector(definition, definition))
        Then("the ambiguous definition is rejected")
        duplicated.toOption shouldBe None
        When("the component construction parameters are absent")
        val absentparams = WorkflowComponentStorage.bindC(null, owner, config, Vector(definition), instances, continuations)
        Then("the missing authority is rejected")
        absentparams.toOption shouldBe None
      } finally take(current.shutdownC())
    }

    "reject missing declaration without acquiring a settings-only database" in {
      Given("a settings-only subsystem with no Workflow storage declaration")
      val path = Files.createTempDirectory("workflow-component-no-authority-").resolve("unopened.sqlite")
      val subsystem = Subsystem(name = "unconfigured-workflow", configuration = configuration(path))
      try {
        When("the component requests a Workflow storage binding")
        val result = WorkflowComponentStorage.bindC(ComponentCreate(subsystem, ComponentOrigin.Builtin),
          owner, config, Vector(definition), instances, continuations)
        Then("the missing declaration is rejected before acquiring SQLite")
        result.toOption shouldBe None
        Files.exists(path) shouldBe false
      } finally subsystem.shutdown()
    }
  }
}
