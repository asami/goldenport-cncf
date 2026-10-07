package org.simplemodeling.cncfclient

import java.nio.file.{Files, Path}
import cats.data.NonEmptyVector
import cats.syntax.all.*
import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.subsystem.{SubsystemEventStoreBootstrap, GenericSubsystemDescriptor, GenericSubsystemComponentBinding,
  GenericSubsystemSecurityBinding, GenericSubsystemAuthenticationBinding, GenericSubsystemLocalSubjectBinding,
  SubsystemExecutionProfile}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * Outside private[cncf]: actual managed Service/CLI dispatch, not a provider stub.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowMechanicalTransitionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Public mechanical fixture failed: $other")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val producer = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
  private val definition = take(bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
    resource("/workflow/candidate-admission-workflow-abi.json").trim, producer, "modeler/candidate-admission-workflow.cml"))))
  private val owner = ComponentId("org.simplemodeling.cncfclient.MechanicalWorkflow")
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "mechanical", name))
  private val instancesCollection = collection("instances")
  private val checkpoints = collection("continuations")
  private val documents = collection("documents")
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("public-mechanical")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("public-mechanical")))
  private val operation = StateMachineOperationIdentity("workflow", "advance")
  private val reference = Reference("planning-checkpoint", "1")
  private val initial = InstanceRecord(InstanceIdentity("instance"), definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("human-start"),
    None, None, None, Vector.empty)
  private val active = take(initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L), InstanceRevision(1L),
    Lifecycle.Active, Some(ProgressionReference("planning")), initial.correlation, initial.causation, None, None, None)))
  private val nextEntry = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
    Some(ProgressionReference("planned")), active.correlation, CausationReference(reference.identity), None, None, None)
  private val expected = take(active.appendC(active.revision, nextEntry))
  private val policy = take(EventPersistencePolicy.createC("public-mechanical-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
  private def host(path: Path, recordGrant: Boolean = true, grantOwner: ComponentId = owner,
    workflowOwner: ComponentId = owner) = {
    val declaration = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"),
      collection("events"), collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
        workflowOwner, "workflow", instancesCollection, checkpoints))))
    val scope = ScopeContext(ScopeKind.Subsystem, "public-mechanical", None, context.observability)
    val resolved = ResolvedConfiguration(HostConfiguration(Map(
        "textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
        "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
        "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
    if (recordGrant) {
      val records = take(ComponentRecordStorageDeclaration.createC(Vector(
        ComponentRecordStorageDeclaration.CreateGrant(grantOwner, "workflow", documents))))
      take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, resolved,
        None, AliasResolver.empty, records))
    } else take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, resolved,
      None, AliasResolver.empty))
  }

  private final class Factory extends Component.PrimaryComponentFactory {
    var instances: WorkflowInstancePersistenceBinding = null
    var mechanical: WorkflowManagedMechanicalTransition[String] = null
    var completed: Option[WorkflowManagedMechanicalTransition.Completed[String]] = None
    var retained: Option[WorkflowManagedMechanicalTransition.Retained[String]] = None
    var observed = Vector.empty[Option[Record]]
    var escaped: ComponentRecordStorage = null
    var refuseDecode = false
    var task: FixtureCall => Consequence[Unit] = _ => Consequence.unit
    private val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
      val typeIdentity = "planning.v1"
      def encode(value: String) = Json.fromString(value)
      def decode(value: Json) = if (refuseDecode) Left("Injected postcommit receipt observation refusal")
        else value.asString.toRight("Expected planning text")
    }
    private val intent = Intent(configuration, reference, active, active.revision, nextEntry,
      TypedValue(codec.typeIdentity, "all-known planning"))
    protected def create_Component(params: ComponentCreate): Component = new Component() {}
    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
      val storage = take(WorkflowComponentStorage.bindC(params, owner, configuration,
        Vector(definition), instancesCollection, checkpoints))
      instances = take(storage.bindInstancesC())
      mechanical = take(storage.bindMechanicalC(operation, codec))
      val advance = simple("advance") { call =>
        given ExecutionContext = call.executionContext
        val programs = Vector("proposal", "ownership").foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { (acc, id) =>
          acc.flatMap(prior => ComponentRecordStorage.bindForCallC(call.core, "workflow", documents,
            DataStore.StringEntryId(id)).flatMap { storage =>
            escaped = storage
            storage.stageCreateC(Record.dataAuto("payload" -> id)).map(prior :+ _)
          })
        }
        programs.flatMap { stages =>
          val program = stages.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())) {
            (prior, stage) => prior.flatMap(_ => stage)
          }
          mechanical.executeC(intent, program).map { result => completed = Some(result); OperationResponse.Scalar("ADVANCED") }
        }
      }
      val recover = simple("recover") { call =>
        given ExecutionContext = call.executionContext
        mechanical.loadRetainedC(reference).map { result => retained = result; OperationResponse.Scalar("RECOVERED") }
      }
      val inspect = simple("inspect") { call =>
        Vector("proposal", "ownership").foldLeft(Consequence.success(Vector.empty[Option[Record]])) { (acc, id) =>
          acc.flatMap(prior => call.readC(documents, id).map(prior :+ _))
        }.map { rows => observed = rows; OperationResponse.Scalar("OBSERVED") }
      }
      val probe = simple("probe")(call => task(call).map(_ => OperationResponse.Scalar("PROBED")))
      Component.Core.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol(services =
        spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition("workflow",
          spec.OperationDefinitionGroup(NonEmptyVector.of(advance, recover, inspect, probe)))))))
    }
    final class FixtureCall(val core: ActionCall.Core, body: FixtureCall => Consequence[OperationResponse]) extends ProcedureActionCall {
      def execute() = body(this)
      def readC(c: DataStore.CollectionId, id: String): Consequence[Option[Record]] = {
        given ExecutionContext = executionContext
        val space = executionContext.dataStoreSpace
        val prior = space.captureBinding()
        try bind_component_application_datastore_c("workflow").flatMap(_ => space.dataStore(c).flatMap(_.load(c, DataStore.StringEntryId(id))))
        finally space.restoreBinding(prior)
      }
      def writeC(c: DataStore.CollectionId, id: String, record: Record, update: Boolean): Consequence[Unit] = {
        given ExecutionContext = executionContext
        val space = executionContext.dataStoreSpace
        val prior = space.captureBinding()
        try bind_component_application_datastore_c("workflow").flatMap(_ => space.dataStore(c).flatMap { store =>
          if (update) store.update(c, DataStore.StringEntryId(id), record)
          else store.create(c, DataStore.StringEntryId(id), record)
        }) finally space.restoreBinding(prior)
      }
    }
    private def simple(operationname: String)(body: FixtureCall => Consequence[OperationResponse]): spec.OperationDefinition =
      new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(operationname, spec.RequestDefinition(), spec.ResponseDefinition.void)
        def createOperationRequest(raw: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = raw
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(capsule: ActionCall.Core): ActionCall = new FixtureCall(capsule, body)
        })
      }
  }

  private def factory(host: SubsystemEventStoreBootstrap): Factory = {
    RuntimeBindingAdmissionFixture.admit(host.subsystem)
    val result = new Factory
    val component = take(result.createPrimaryC(ComponentCreate(host.subsystem, ComponentOrigin.Builtin)))
    host.subsystem.add(component)
    host.subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<public-mechanical-spec>"),
      subsystemName = "public-mechanical", componentBindings = Vector(GenericSubsystemComponentBinding(component.name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"), localSubject = Some(
          GenericSubsystemLocalSubjectBinding(id = "participant", roles = Vector("user"), capabilities = Vector("user"),
            attributes = Map.empty, securityLevel = Some("user")))))))))
    take(host.subsystem.executionProfileC) shouldBe SubsystemExecutionProfile.Fixed
    result
  }
  private def invoke(host: SubsystemEventStoreBootstrap, name: String) = host.subsystem.executeOperationResponseInContext(
    Request.of(component = owner.name, service = "workflow", operation = name), context)
  private def seed(host: SubsystemEventStoreBootstrap, factory: Factory): Unit = {
    take(host.initializeCursorC())
    take(factory.instances.createC(initial))
    take(factory.instances.appendC(initial.identity, initialRevision, active.history.head))
  }
  private def rows(factory: Factory) = factory.observed.map(_.flatMap(_.getString("payload")))

  "Public managed mechanical Workflow progress" should {
    "refuse composition without the owning Workflow declaration before accessing a provider" in {
      Given("a real host whose captured Workflow grant belongs to another component")
      val first = host(Files.createTempDirectory("public-mechanical-undeclared-").resolve("host.sqlite"),
        workflowOwner = ComponentId("org.test.Foreign"))
      try {
        When("the actual component tries to compose its mechanical storage capability")
        val denied = WorkflowComponentStorage.bindC(ComponentCreate(first.subsystem, ComponentOrigin.Builtin),
          owner, configuration, Vector(definition), instancesCollection, checkpoints)
        Then("no storage or execution authority is inferred from configuration")
        denied.isSuccess shouldBe false
      } finally take(first.shutdownC())
    }

    "atomically retain history, checkpoint and whole documents and recover after pool reopen and later history" in {
      Given("an ordinary active instance with no issued AI WorkOrder and exact host storage grants")
      val path = Files.createTempDirectory("public-mechanical-success-").resolve("host.sqlite")
      val first = host(path)
      try {
        val wired = factory(first)
        seed(first, wired)
        When("the real Service/CLI advance commits its Unit-valued mechanical plan")
        take(invoke(first, "advance")) shouldBe OperationResponse.Scalar("ADVANCED")
        Then("both complete document rows, one checkpoint and the exact next history are retained together")
        wired.completed.get.receipt.next shouldBe expected
        wired.completed.get.receipt.payload.value shouldBe "all-known planning"
        take(wired.instances.loadC(initial.identity)) shouldBe Some(expected)
        take(invoke(first, "inspect"))
        rows(wired) shouldBe Vector(Some("proposal"), Some("ownership"))
        wired.observed.flatten.foreach(_.fields.map(_.key).toSet shouldBe Set("id", "payload"))
        wired.escaped.stageCreateC(Record.dataAuto("payload" -> "expired")).isSuccess shouldBe false
        invoke(first, "advance").isSuccess shouldBe false
      } finally take(first.shutdownC())
      When("a fresh physical pool recovers the checkpoint after legitimate subsequent history")
      val reopened = host(path)
      try {
        val wired = factory(reopened)
        val later = take(wired.instances.appendC(initial.identity, expected.revision,
          expected.history.last.copy(sequence = HistorySequence(3L), revision = InstanceRevision(3L),
            currentProgression = Some(ProgressionReference("later")), causation = CausationReference("later-operation"))))
        take(invoke(reopened, "recover"))
        take(invoke(reopened, "inspect"))
        Then("recovery preserves the complete checkpoint prefix, original payload and later native state without allocation")
        wired.retained.get.receipt.next shouldBe expected
        wired.retained.get.current shouldBe later
        rows(wired) shouldBe Vector(Some("proposal"), Some("ownership"))
        wired.completed shouldBe None
      } finally take(reopened.shutdownC())
    }

    "refuse an occupied document or checkpoint without partial publication" in {
      Vector("document", "checkpoint").foreach { target =>
        Given(s"an active instance with an occupied $target target before mechanical execution")
        val path = Files.createTempDirectory("public-mechanical-collision-").resolve("host.sqlite")
        val first = host(path)
        try {
          val wired = factory(first)
          seed(first, wired)
          wired.task = call => if (target == "document")
            call.writeC(documents, "ownership", Record.dataAuto("payload" -> "existing"), false)
            else call.writeC(checkpoints, take(keyC(reference)), Record.dataAuto("payload" -> "existing"), false)
          take(invoke(first, "probe"))
          When("the real public advance attempts the guarded root and immutable Create set")
          invoke(first, "advance").isSuccess shouldBe false
          take(invoke(first, "inspect"))
          Then("no checkpoint/result success or next history is claimed and no first document is published")
          wired.completed shouldBe None
          take(wired.instances.loadC(initial.identity)) shouldBe Some(active)
          rows(wired) shouldBe (if (target == "document") Vector(None, Some("existing")) else Vector(None, None))
        } finally take(first.shutdownC())
        When("the same database is reopened after the refused transaction")
        val reopened = host(path)
        try {
          val wired = factory(reopened)
          take(invoke(reopened, "inspect"))
          Then("no deferred partial publication appears and the original active history remains")
          take(wired.instances.loadC(initial.identity)) shouldBe Some(active)
          rows(wired) shouldBe (if (target == "document") Vector(None, Some("existing")) else Vector(None, None))
        } finally take(reopened.shutdownC())
      }
    }

    "refuse missing and foreign Create grants without progressing the instance" in {
      Vector((false, owner), (true, ComponentId("org.test.Foreign"))).foreach { case (granted, grantOwner) =>
        Given("an actual host with an absent or foreign component document Create grant")
        val first = host(Files.createTempDirectory("public-mechanical-grant-").resolve("host.sqlite"), granted, grantOwner)
        try {
          val wired = factory(first)
          seed(first, wired)
          When("the owning public operation stages its document set")
          val denied = invoke(first, "advance")
          Then("no grant is inferred from payload and neither history nor checkpoint advances")
          denied.isSuccess shouldBe false
          wired.completed shouldBe None
          take(wired.instances.loadC(initial.identity)) shouldBe Some(active)
          take(invoke(first, "recover"))
          wired.retained shouldBe None
        } finally take(first.shutdownC())
      }
    }

    "retain uncertain postcommit data and recover it without resending the mechanical transition" in {
      Given("an actual SQLite host whose receipt codec refuses only postcommit decoding")
      val path = Files.createTempDirectory("public-mechanical-uncertain-").resolve("host.sqlite")
      val first = host(path)
      try {
        val wired = factory(first)
        seed(first, wired)
        wired.refuseDecode = true
        When("native commit succeeds but its complete receipt projection cannot be admitted")
        val uncertain = invoke(first, "advance")
        Then("the call reports failure rather than an invented verified outcome while native history is retained")
        uncertain.isSuccess shouldBe false
        wired.completed shouldBe None
        take(wired.instances.loadC(initial.identity)) shouldBe Some(expected)
      } finally take(first.shutdownC())
      When("a fresh host observes the exact checkpoint instead of retrying execution")
      val reopened = host(path)
      try {
        val wired = factory(reopened)
        take(invoke(reopened, "recover"))
        take(invoke(reopened, "inspect"))
        Then("checkpoint, payload and both documents are complete and the native revision has not advanced again")
        wired.retained.get.receipt.next shouldBe expected
        wired.retained.get.current shouldBe expected
        rows(wired) shouldBe Vector(Some("proposal"), Some("ownership"))
        wired.completed shouldBe None
      } finally take(reopened.shutdownC())
    }

    "refuse malformed stored checkpoint payload and recover again only after exact restoration" in {
      Given("a complete native mechanical checkpoint created through the real public operation")
      val first = host(Files.createTempDirectory("public-mechanical-tamper-").resolve("host.sqlite"))
      try {
        val wired = factory(first)
        seed(first, wired)
        take(invoke(first, "advance"))
        var original = ""
        wired.task = call => call.readC(checkpoints, take(keyC(reference))).flatMap { row =>
          original = row.get.getString("mechanicalPayload").get
          call.writeC(checkpoints, take(keyC(reference)), Record.dataAuto("mechanicalPayload" -> "malformed"), true)
        }
        take(invoke(first, "probe"))
        When("public recovery observes a malformed complete receipt row")
        val denied = invoke(first, "recover")
        Then("the row cannot produce usable retained evidence or rewrite the original native history")
        denied.isSuccess shouldBe false
        wired.retained shouldBe None
        take(wired.instances.loadC(initial.identity)) shouldBe Some(expected)
        When("the host fixture restores the exact original payload through a managed call")
        wired.task = call => call.writeC(checkpoints, take(keyC(reference)), Record.dataAuto("mechanicalPayload" -> original), true)
        take(invoke(first, "probe"))
        take(invoke(first, "recover"))
        Then("only the restored exact checkpoint is usable again")
        wired.retained.get.receipt.next shouldBe expected
      } finally take(first.shutdownC())
    }

    "refuse valid but divergent native history without rewriting it during recovery" in {
      Given("a genuine managed checkpoint followed by a valid but different stored history prefix")
      val first = host(Files.createTempDirectory("public-mechanical-prefix-").resolve("host.sqlite"))
      try {
        val wired = factory(first)
        seed(first, wired)
        take(invoke(first, "advance"))
        val divergent = expected.copy(history = expected.history.updated(0,
          expected.history.head.copy(causation = CausationReference("different-start"))))
        val changed = take(WorkflowInstanceRecordJsonV1.encodeC(divergent))
        wired.task = call => call.writeC(instancesCollection, initial.identity.value,
          Record.dataAuto("recordPayload" -> ("cncf.workflow-json.v1:" + changed)), true)
        take(invoke(first, "probe"))
        When("public recovery compares the entire checkpoint prefix with current native history")
        val denied = invoke(first, "recover")
        Then("the receipt is not usable and recovery does not repair or overwrite the divergent native record")
        denied.isSuccess shouldBe false
        wired.retained shouldBe None
        take(wired.instances.loadC(initial.identity)) shouldBe Some(divergent)
      } finally take(first.shutdownC())
    }
  }
}
