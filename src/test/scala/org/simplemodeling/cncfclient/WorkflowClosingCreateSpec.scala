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
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * Outside private[cncf]: the fixture uses only public host/capability APIs.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowClosingCreateSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given _context: ExecutionContext = ExecutionContext.create()
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Public closing fixture rejected: $failure")
  }
  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val _producer = _take(CandidateAdmissionProducerAbi.parseC(
    _resource("/workflow/candidate-admission-producer-abi.json")))
  private val _definition = _take(bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
    _resource("/workflow/candidate-admission-workflow-abi.json").trim, _producer,
    "modeler/candidate-admission-workflow.cml"))))
  private val _owner = ComponentId("org.simplemodeling.cncfclient.ClosingWorkflow")
  private def _collection(name: String) = DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "publicclosing", name))
  private val _instances = _collection("instances")
  private val _continuations = _collection("continuations")
  private val _records = _collection("enrichment")
  private val _configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("public-closing")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("public-closing")))
  private val _operation = StateMachineOperationIdentity("workflow", "submit")
  private val _codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "review.result.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("expected text")
  }
  private val _closing_type = StateMachineResultTypeReference("enrichment.closing.v1")
  private val _closing = StateMachineOperationResult(_closing_type, ContextReference("closing", "1"))
  private val _event_policy = _take(EventPersistencePolicy.createC("public-closing-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
  private val _declaration = _take(EventStorePersistenceDeclaration.createC("events", _collection("cursor"),
    _collection("events"), _collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(_owner, "workflow", _instances, _continuations))))
  private def _host(path: Path, grantowner: ComponentId = _owner, grantstore: String = "workflow") = {
    val records = _take(ComponentRecordStorageDeclaration.createC(Vector(
      ComponentRecordStorageDeclaration.CreateGrant(grantowner, grantstore, _records))))
    _take(SubsystemEventStoreBootstrap.createC(_event_policy, _declaration,
      ScopeContext(ScopeKind.Subsystem, "public-closing", None, _context.observability),
      ResolvedConfiguration(HostConfiguration(Map(
        "textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
        "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
        "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty),
      None, AliasResolver.empty, records))
  }
  private val _initial = InstanceRecord(InstanceIdentity("instance"), _definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("start"),
    None, None, None, Vector.empty)
  private val _identity = ContinuationIdentity("continuation")
  private val _intent = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "Review", StateMachineOperationIdentity("review", "change"), Some(StateMachineInputTypeReference(_codec.typeIdentity)),
      Some(StateMachineResultTypeReference(_codec.typeIdentity)), StateMachineRequiredOperationMetadata(
        ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty), EvidenceContract("review.evidence", Vector.empty), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("run"),
      _identity, StateMachineRevision("machine-1"), required,
      ContextBundle("context", Vector.empty, Vector.empty, ContextSnapshot(_definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), _initial.correlation, _initial.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(_identity.value), InstanceRevision(1L),
        ContextSnapshotReference(_definition.workflowRevision.value), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("resume"))))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      _configuration, _initial, initialRevision, entry, continuation)
  }
  private val _work = {
    val c = _intent.continuation.continuation
    WorkflowInteraction[String, Nothing](_take(WorkflowHandle.fromRecordC(_owner, _initial)),
      WorkflowContinuation.WorkOrder(ContinuationRequest(c.runId, c.continuationId, c.expectedRevision,
        c.requiredOperation.identity, c.requiredOperation.operation, Some(TypedValue(_codec.typeIdentity, "unknown work")),
        c.requiredOperation.resultType, c.context, c.requiredOperation.metadata.completionContract,
        c.requiredOperation.metadata.evidenceContract, _operation),
        ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"),
          ReasoningLevel.Deep, true), MinimalPresentation("Review", "Unknown only")))
  }
  private def _result(value: String): ContinuationResult[String] = {
    val work = _work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
    val r = work.request
    ContinuationResult(_work.handle, r.runId, r.continuationId, r.expectedRevision, r.context.snapshot,
      TypedValue(_codec.typeIdentity, value), ContextReference("semantic-result", "1"), Vector.empty,
      ExecutionEvidence(Vector.empty, Some("worker"), Some("model"),
        Some(SkillDispatchEvidence(work.requirement, "semantic-review", "mapping-v1"))))
  }

  private final class Factory(recordstore: String = "workflow") extends Component.PrimaryComponentFactory {
    var instances: WorkflowInstancePersistenceBinding = null
    var issued: WorkflowManagedIssuedWork[String] = null
    var completion: WorkflowManagedCompletion[String] = null
    var observed = Vector.empty[Option[Record]]
    var early = Vector.empty[Option[Record]]
    var escaped: ComponentRecordStorage = null
    protected def create_Component(params: ComponentCreate): Component = new Component() {}
    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
      val storage = _take(WorkflowComponentStorage.bindC(params, _owner, _configuration,
        Vector(_definition), _instances, _continuations))
      instances = _take(storage.bindInstancesC())
      issued = _take(storage.bindIssuedC(_operation, _codec))
      completion = _take(storage.bindCompletionC(_operation, _codec, _codec, _closing_type,
        (_, _) => Consequence.success(Vector.empty)))
      val submit = new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification("submit", spec.RequestDefinition(), spec.ResponseDefinition.void)
        def createOperationRequest(raw: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = raw
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = capsule
            private var _delegate = Option.empty[ActionCall]
            def execute(): Consequence[OperationResponse] = _completion_operation(core).flatMap(_.createOperationRequest(raw)).flatMap {
              case bound: CommandAction =>
                val call = bound.createCall(core.copy(action = bound))
                _delegate = Some(call)
                call.execute()
              case _ => Consequence.stateConflict("Completion is not a command")
            }
            override def afterCommitResponseC(response: OperationResponse) = {
              val selected = _delegate
              _delegate = None
              selected.map(_.afterCommitResponseC(response)).getOrElse(Consequence.stateConflict("No selected completion"))
            }
          }
        })
      }
      val inspect = _simple_operation("inspect") { core =>
        _read_records(core).map { rows => observed = rows; OperationResponse.Scalar("OBSERVED") }
      }
      val collision = _simple_operation("seedCollision") { core =>
        _storage(core, "second").flatMap(_.createC(Record.dataAuto("payload" -> "existing")))
          .map(_ => OperationResponse.Scalar("SEEDED"))
      }
      Component.Core.create(_owner.name, _owner, ComponentInstanceId.default(_owner), Protocol(services =
        spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition("workflow",
          spec.OperationDefinitionGroup(NonEmptyVector.of(submit, inspect, collision)))))))
    }
    private def _storage(core: ActionCall.Core, id: String) =
      ComponentRecordStorage.bindForCallC(core, recordstore, _records, DataStore.StringEntryId(id))
    private def _read_records(core: ActionCall.Core): Consequence[Vector[Option[Record]]] =
      Vector("first", "second").foldLeft(Consequence.success(Vector.empty[Option[Record]])) { (acc, id) =>
        acc.flatMap(rows => _storage(core, id).flatMap(_.loadC()).map(rows :+ _))
      }
    private def _simple_operation(operationname: String)(body: ActionCall.Core => Consequence[OperationResponse]): spec.OperationDefinition =
      new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(operationname, spec.RequestDefinition(), spec.ResponseDefinition.void)
        def createOperationRequest(raw: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = raw
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = capsule
            def execute() = body(core)
          }
        })
      }
    private def _completion_operation(core: ActionCall.Core): Consequence[spec.OperationDefinition] = {
      given ExecutionContext = core.executionContext
      val spi = new WorkflowInstancePersistence {
        def create(c: Configuration, record: InstanceRecord) = Consequence.stateConflict("No separate Create")
        def load(c: Configuration, identity: InstanceIdentity) = instances.loadC(identity)
        def append(c: Configuration, identity: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) =
          Consequence.stateConflict("No separate append")
      }
      val apioperation = GeneratedProvidedApiAbi.Operation("workflow", "submit", Some(_codec.typeIdentity),
        Some(_closing_type.value), GeneratedProvidedApiAbi.SourceIdentity(Some(1)))
      val api = GeneratedProvidedApiAbi.Definition(GeneratedProvidedApiAbi.schemaVersion,
        GeneratedProvidedApiAbi.generatorIdentity, _definition.workflowIdentity.value, _definition.workflowRevision.value,
        GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(Some(1)),
          GeneratedProvidedApiAbi.SourceIdentity(Some(1))), Vector(apioperation))
      val codec = new WorkflowCompletionServiceOperation.Codec[String] {
        val resultTypeIdentity = _codec.typeIdentity
        def decodeC(raw: Request) = raw.arguments match {
          case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, _codec)
          case _ => Consequence.stateConflict("Expected result JSON")
        }
        def encodeC(resume: ContinuationRuntime.Resume) = Consequence.success(OperationResponse.Scalar("COMPLETED"))
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[String] {
        def selectC(submitted: ContinuationResult[String], current: InstanceRecord) = {
          val entry = HistoryEntry(HistorySequence(current.history.last.sequence.value + 1L),
            InstanceRevision(current.revision.value + 1L), Lifecycle.Active, current.currentProgression,
            current.correlation, CausationReference(submitted.resultReference.identity), None, None, None)
          for {
            next <- current.appendC(current.revision, entry)
            rows <- _read_records(core)
            _ = early = rows
            stages <- if (submitted.result.value == "decision-needed") Consequence.success(Vector.empty[ExecUowM[Unit]])
              else Vector("first", "second").foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { (acc, id) =>
                acc.flatMap(programs => _storage(core, id).flatMap { storage =>
                  escaped = storage
                  storage.stageCreateC(Record.dataAuto("payload" -> id)).map(programs :+ _)
                })
              }
            program = stages.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())) {
              (acc, stage) => acc.flatMap(_ => stage)
            }.map(_ => ActionExecution.Completed(_closing): ActionExecution)
          } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
        }
      }
      WorkflowCompletionServiceOperation.bindC(api, apioperation, spec.RequestDefinition(), spec.ResponseDefinition.void,
        codec, ContinuationRuntime.empty, issued.issuedPersistence, spi, _configuration,
        () => throw new IllegalStateException("No legacy UoW"), Some(closing), Some(completion))
    }
  }

  private def _factory(host: SubsystemEventStoreBootstrap, recordstore: String = "workflow"): Factory = {
    // Public downstream test setup only; withDescriptor below clears controlled-test execution.
    RuntimeBindingAdmissionFixture.admit(host.subsystem)
    val factory = new Factory(recordstore)
    val component = _take(factory.createPrimaryC(ComponentCreate(host.subsystem, ComponentOrigin.Builtin)))
    host.subsystem.add(component)
    host.subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<public-closing-spec>"),
      subsystemName = "public-closing", componentBindings = Vector(GenericSubsystemComponentBinding(component.name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"), localSubject = Some(
          GenericSubsystemLocalSubjectBinding(id = "participant", roles = Vector("user"), capabilities = Vector("user"),
            attributes = Map.empty, securityLevel = Some("user")))))))))
    _take(host.subsystem.executionProfileC) shouldBe SubsystemExecutionProfile.Fixed
    factory
  }
  private def _invoke(host: SubsystemEventStoreBootstrap, operation: String, result: Option[String] = None) =
    host.subsystem.executeOperationResponseInContext(Request.of(component = _owner.name, service = "workflow",
      operation = operation, arguments = result.toList.map(value => Argument("arg1",
        _take(WorkflowResultJsonV1.encodeC(_result(value), _codec))))), _context)
  private def _seed(host: SubsystemEventStoreBootstrap, factory: Factory): InstanceRecord = {
    _take(host.initializeCursorC())
    _take(factory.instances.createC(_initial))
    _take(factory.issued.suspendC(_intent, _work)).instance
  }

  "Public fixed-target Creates in managed Workflow completion" should {
    "refuse foreign owner or logical-store grants rather than inferring Create authority" in {
      Vector((ComponentId("org.simplemodeling.cncfclient.Foreign"), "workflow"), (_owner, "foreign"))
        .foreach { case (grantowner, grantstore) =>
          Given("a canonical Workflow with a record grant for a different owner or logical store")
          val host = _host(Files.createTempDirectory("public-closing-foreign-").resolve("host.sqlite"), grantowner, grantstore)
          try {
            val factory = _factory(host, grantstore)
            val suspended = _seed(host, factory)
            When("the public completion requests local enrichment in its own store")
            val denied = _invoke(host, "submit", Some("enriched"))
            Then("the grant cannot publish a result or advance history for another scope")
            denied shouldBe a[Consequence.Failure[?]]
            _take(factory.completion.loadRetainedC(_identity)) shouldBe None
            _take(factory.instances.loadC(_initial.identity)) shouldBe Some(suspended)
            if (grantowner == _owner) {
              factory.early shouldBe Vector(None, None)
              factory.escaped should not be null
              _take(_invoke(host, "inspect"))
              factory.observed shouldBe Vector(None, None)
            } else factory.escaped shouldBe null
          } finally _take(host.shutdownC())
        }
    }

    "publish both enrichment records with the exact result and adjacent history and reopen the whole set" in {
      Given("a public factory under captured host grants and a canonically issued WorkOrder")
      val path = Files.createTempDirectory("public-closing-success-").resolve("host.sqlite")
      val first = _host(path)
      val factory = _factory(first)
      val suspended = _seed(first, factory)
      val expected = _take(suspended.appendC(suspended.revision, HistoryEntry(HistorySequence(2L),
        InstanceRevision(2L), Lifecycle.Active, suspended.currentProgression, suspended.correlation,
        CausationReference("semantic-result"), None, None, None)))
      try {
        When("the real public completion Operation stages and commits two Creates")
        _take(_invoke(first, "submit", Some("enriched"))) shouldBe OperationResponse.Scalar("COMPLETED")
        Then("neither target existed during selection and result/history are retained together")
        factory.early shouldBe Vector(None, None)
        val retained = _take(factory.completion.loadRetainedC(_identity)).get.completion
        retained.result shouldBe _result("enriched")
        retained.closingResult shouldBe Some(_closing)
        val actual = _take(factory.instances.loadC(_initial.identity)).get
        actual shouldBe expected
        actual.history.take(suspended.history.size) shouldBe suspended.history
        actual.revision shouldBe InstanceRevision(2L)
        actual.suspension shouldBe None
        _take(_invoke(first, "inspect"))
        factory.observed.map(_.flatMap(_.getString("payload"))) shouldBe Vector(Some("first"), Some("second"))
        factory.escaped.stageCreateC(Record.dataAuto("payload" -> "late")).isSuccess shouldBe false
        _invoke(first, "submit", Some("enriched")).isSuccess shouldBe false
      } finally _take(first.shutdownC())
      When("a fresh canonical host opens the physical pool again")
      val reopened = _host(path)
      try {
        val rebuilt = _factory(reopened)
        _take(_invoke(reopened, "inspect"))
        Then("exact record identities, semantic result, closing result and history survive without reset")
        rebuilt.observed.map(_.flatMap(_.getString("payload"))) shouldBe Vector(Some("first"), Some("second"))
        rebuilt.observed.map(_.flatMap(_.getString("id"))) shouldBe Vector(Some("first"), Some("second"))
        rebuilt.observed.flatten.foreach(_.fields.map(_.key).toSet shouldBe Set("id", "payload"))
        _take(rebuilt.completion.loadRetainedC(_identity)).get.completion.result shouldBe _result("enriched")
        _take(rebuilt.completion.loadRetainedC(_identity)).get.completion.closingResult shouldBe Some(_closing)
        _take(rebuilt.instances.loadC(_initial.identity)) shouldBe Some(expected)
      } finally _take(reopened.shutdownC())
    }

    "refuse a collided Create without publishing the other record, result or next history" in {
      Given("an issued WorkOrder whose second fixed target already exists")
      val path = Files.createTempDirectory("public-closing-collision-").resolve("host.sqlite")
      val host = _host(path)
      try {
        val factory = _factory(host)
        val suspended = _seed(host, factory)
        _take(_invoke(host, "seedCollision"))
        When("canonical completion attempts to publish the two-record set")
        val denied = _invoke(host, "submit", Some("enriched"))
        Then("no first record, completion result or adjacent history is published")
        denied shouldBe a[Consequence.Failure[?]]
        _take(_invoke(host, "inspect"))
        factory.observed.map(_.flatMap(_.getString("payload"))) shouldBe Vector(None, Some("existing"))
        _take(factory.completion.loadRetainedC(_identity)) shouldBe None
        _take(factory.instances.loadC(_initial.identity)) shouldBe Some(suspended)
      } finally _take(host.shutdownC())
      When("the host is reopened after the refused publication")
      val reopened = _host(path)
      try {
        val factory = _factory(reopened)
        _take(_invoke(reopened, "inspect"))
        Then("the existing record is unchanged and no partial publication appears")
        factory.observed.map(_.flatMap(_.getString("payload"))) shouldBe Vector(None, Some("existing"))
        _take(factory.completion.loadRetainedC(_identity)) shouldBe None
        _take(factory.instances.loadC(_initial.identity)).get.revision shouldBe InstanceRevision(1L)
      } finally _take(reopened.shutdownC())
    }

    "retain an unresolved semantic result without staging partial enrichment" in {
      Given("a canonically issued WorkOrder whose result requires a later human decision")
      val host = _host(Files.createTempDirectory("public-closing-decision-").resolve("host.sqlite"))
      try {
        val factory = _factory(host)
        _seed(host, factory)
        When("the public completion returns its typed unresolved result")
        _take(_invoke(host, "submit", Some("decision-needed")))
        Then("raw result and adjacent history persist, but both enrichment targets remain absent")
        _take(factory.completion.loadRetainedC(_identity)).get.completion.result shouldBe _result("decision-needed")
        _take(factory.instances.loadC(_initial.identity)).get.revision shouldBe InstanceRevision(2L)
        _take(_invoke(host, "inspect"))
        factory.observed shouldBe Vector(None, None)
        factory.escaped shouldBe null
      } finally _take(host.shutdownC())
    }
  }
}
