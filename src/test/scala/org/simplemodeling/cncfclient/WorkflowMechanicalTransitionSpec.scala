package org.simplemodeling.cncfclient

import java.nio.file.{Files, Path}
import java.time.Instant
import cats.data.NonEmptyVector
import cats.syntax.all.*
import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.datastore.sql.SqlDataStore
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
 * @version Oct. 25, 2026
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
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient),
    EventPersistencePolicy.Rule(EventTypeId("terminal-fact"), EventPersistencePolicy.Mode.Persist))))
  private val _terminal_entry = nextEntry.copy(lifecycle = Lifecycle.Completed, currentProgression = None)
  private val _terminal_event = {
    val instant = Instant.parse("2026-10-25T00:00:00Z")
    EventRecord(EventId("test", "mechanical", Some(instant), Some("terminal")), "terminal-fact", "terminal-fact",
      Map("checkpoint" -> reference.identity), Map.empty, instant, true, EventRecord.Status.Stored, EventLane.Transactional)
  }
  private def _provision_terminal_event_schema(path: Path): Unit = {
    val store = SqlDataStore.sqlite(path.toString)
    try {
      val effect = take(EventRecordJsonV2.createSideEffectC(collection("events"), _terminal_event.copy(sequence = 1L)))
      take(store.create(effect.collection, effect.entryId, effect.record))
      take(store.delete(effect.collection, effect.entryId))
      take(store.load(effect.collection, effect.entryId)) shouldBe None
    } finally take(store.closeC())
  }
  private def _terminal_events(path: Path): Vector[EventRecord] = {
    val store = SqlDataStore.sqlite(path.toString)
    try {
      take(store.load(collection("events"), DataStore.StringEntryId(_terminal_event.id.value))).map { row =>
        val encoded = row.getString("event_json").getOrElse(fail("Retained terminal event lacks canonical event_json"))
        take(EventRecordJsonV2.decodeStorageC(encoded))
      }.toVector
    } finally take(store.closeC())
  }
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
    var suppliedCurrent = active
    var suppliedExpectedRevision = active.revision
    var suppliedConfiguration = configuration
    var suppliedEntry = nextEntry
    var suppliedRecords = Vector.empty[EventRecord]
    var task: FixtureCall => Consequence[Unit] = _ => Consequence.unit
    private val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
      val typeIdentity = "planning.v1"
      def encode(value: String) = Json.fromString(value)
      def decode(value: Json) = if (refuseDecode) Left("Injected postcommit receipt observation refusal")
        else value.asString.toRight("Expected planning text")
    }
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
          val intent = Intent(suppliedConfiguration, reference, suppliedCurrent, suppliedExpectedRevision, suppliedEntry,
            TypedValue(codec.typeIdentity, "all-known planning"))
          mechanical.executeC(intent, program, suppliedRecords).map { result => completed = Some(result); OperationResponse.Scalar("ADVANCED") }
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
    "atomically commit finite Completed history checkpoint documents and a real event then physically reopen them" in {
      Given("docs/spec/workflow-completion-applicability.md: actual public Service binding with native SQLite and a persistent terminal fact")
      val path = Files.createTempDirectory("public-mechanical-terminal-").resolve("host.sqlite")
      // Given an empty canonical events schema: native observation requires its
      // physical table before managed execution; no event or cursor is retained.
      _provision_terminal_event_schema(path)
      val completed = take(active.appendC(active.revision, _terminal_entry))
      val first = host(path)
      try {
        val wired = factory(first)
        seed(first, wired)
        wired.suppliedEntry = _terminal_entry
        wired.suppliedRecords = Vector(_terminal_event)
        When("the public Service executes the managed terminal UnitOfWork with real document and event writes")
        take(invoke(first, "advance")) shouldBe OperationResponse.Scalar("ADVANCED")
        take(invoke(first, "inspect"))
        val events = _terminal_events(path)
        Then("the matching terminal receipt instance two whole documents and one actual event are durable together")
        wired.completed.get.receipt.next shouldBe completed
        take(wired.instances.loadC(initial.identity)) shouldBe Some(completed)
        rows(wired) shouldBe Vector(Some("proposal"), Some("ownership"))
        events.map(_.id) shouldBe Vector(_terminal_event.id)
        events.head.payload shouldBe _terminal_event.payload
        events.head.sequence should be > 0L
      } finally take(first.shutdownC())
      When("a physically new host consumes the actual saved terminal checkpoint and facts")
      val reopened = host(path)
      try {
        val wired = factory(reopened)
        take(invoke(reopened, "recover"))
        take(invoke(reopened, "inspect"))
        val events = _terminal_events(path)
        Then("the retained Completed projection payload event and documents agree without any new execution")
        wired.retained.get.receipt.next shouldBe completed
        wired.retained.get.current shouldBe completed
        wired.retained.get.receipt.payload.value shouldBe "all-known planning"
        rows(wired) shouldBe Vector(Some("proposal"), Some("ownership"))
        events.map(_.id) shouldBe Vector(_terminal_event.id)
        events.head.payload shouldBe _terminal_event.payload
        wired.completed shouldBe None
        take(wired.instances.loadC(initial.identity)) shouldBe Some(completed)
      } finally take(reopened.shutdownC())
    }

    "refuse a terminal document collision without publishing its event checkpoint or Completed instance" in {
      Given("docs/spec/workflow-completion-applicability.md: an Active native source and an occupied document in the actual managed host")
      val path = Files.createTempDirectory("public-terminal-atomic-refusal-").resolve("host.sqlite")
      // Given the same empty canonical event schema, refusal must reach the
      // actual document-collision transaction rather than a missing table.
      _provision_terminal_event_schema(path)
      val first = host(path)
      try {
        val wired = factory(first)
        seed(first, wired)
        wired.suppliedEntry = _terminal_entry
        wired.suppliedRecords = Vector(_terminal_event)
        wired.task = call => call.writeC(documents, "ownership", Record.dataAuto("payload" -> "existing"), false)
        take(invoke(first, "probe"))
        When("terminal execution attempts the real colliding document event and native transition transaction")
        val rejected = invoke(first, "advance")
        take(invoke(first, "recover"))
        take(invoke(first, "inspect"))
        Then("the transaction refuses with the original Active row no checkpoint no event and no first document")
        rejected.isSuccess shouldBe false
        wired.retained shouldBe None
        take(wired.instances.loadC(initial.identity)) shouldBe Some(active)
        rows(wired) shouldBe Vector(None, Some("existing"))
        _terminal_events(path) shouldBe empty
      } finally take(first.shutdownC())
      When("a physical host reopen reads the refused transaction")
      val reopened = host(path)
      try {
        val wired = factory(reopened)
        take(invoke(reopened, "recover"))
        take(invoke(reopened, "inspect"))
        Then("no partial terminal event receipt or document becomes visible after reopen")
        wired.retained shouldBe None
        take(wired.instances.loadC(initial.identity)) shouldBe Some(active)
        rows(wired) shouldBe Vector(None, Some("existing"))
        _terminal_events(path) shouldBe empty
      } finally take(reopened.shutdownC())
    }

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

    "observe divergent historical facts without an unchanged-prefix certificate or rewrite" in {
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
        When("public recovery reads retained data and the actual current native instance")
        val recovered = invoke(first, "recover")
        val current = take(wired.instances.loadC(initial.identity))
        Then("historical result data is observable without granting execution or overwriting current facts")
        recovered.isSuccess shouldBe true
        wired.retained.get.receipt.next shouldBe expected
        wired.retained.get.current shouldBe divergent
        current shouldBe Some(divergent)
      } finally take(first.shutdownC())
    }

    "derive progress from the actual stored predecessor rather than a caller's historical copy" in {
      Vector(false, true).foreach { stale =>
        Given("an owned active instance and caller data whose historical facts and policy copy differ")
        val first = host(Files.createTempDirectory("public-mechanical-actual-").resolve("host.sqlite"))
        try {
          val wired = factory(first)
          seed(first, wired)
          wired.suppliedCurrent = active.copy(causation = CausationReference("diagnostic-copy"),
            history = active.history.map(_.copy(causation = CausationReference("diagnostic-copy"))))
          wired.suppliedConfiguration = configuration.copy(retentionPolicy = RetentionPolicy("different-copy"))
          if (stale) wired.suppliedExpectedRevision = initialRevision
          When("the real operation prepares and attempts its mechanical append")
          val result = invoke(first, "advance")
          val current = take(wired.instances.loadC(initial.identity))
          Then("actual native state supplies the predecessor and stale expected revision still refuses all writes")
          result.isSuccess shouldBe !stale
          current shouldBe Some(if (stale) active else expected)
          wired.completed.map(_.receipt.current) shouldBe (if (stale) None else Some(active))
        } finally take(first.shutdownC())
      }
    }

    "omit configuration copies on write and ignore them in legacy checkpoints after physical reopen" in {
      Given("a real managed checkpoint and a legacy configuration field unrelated to execution authority")
      val path = Files.createTempDirectory("public-mechanical-legacy-").resolve("host.sqlite")
      val first = host(path)
      var fields = Set.empty[String]
      try {
        val wired = factory(first)
        seed(first, wired)
        take(invoke(first, "advance"))
        wired.task = call => call.readC(checkpoints, take(keyC(reference))).flatMap { row =>
          fields = row.get.fields.map(_.key).toSet
          call.writeC(checkpoints, take(keyC(reference)), Record.dataAuto(
            "configurationPayload" -> "legacy diagnostic policy, not a matching certificate"), true)
        }
        When("the managed fixture observes the new envelope and adds ignored legacy material")
        val changed = invoke(first, "probe")
        Then("new storage has only its actual key, revision and payload, with no full policy copy")
        changed.isSuccess shouldBe true
        fields shouldBe Set("id", "cncf_revision", "mechanicalPayload")
      } finally take(first.shutdownC())
      When("an independently reopened host recovers the legacy envelope")
      val reopened = host(path)
      try {
        val wired = factory(reopened)
        val result = invoke(reopened, "recover")
        Then("the current owning host, not the legacy configuration copy, selects the actual retained result")
        result.isSuccess shouldBe true
        wired.retained.get.receipt.next shouldBe expected
        wired.retained.get.current shouldBe expected
      } finally take(reopened.shutdownC())
    }
  }
}
