package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import cats.free.Free
import cats.data.NonEmptyVector
import cats.syntax.all.*
import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.unitofwork.{ExecUowM, TransactionContext, UnitOfWorkOp}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.{Subsystem, SubsystemEventStoreBootstrap}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.record.Record
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.value.{BaseContent, DescriptiveAttributes}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Sep. 29, 2026
 * @version Oct. 12, 2026
 */
final class WorkflowCompletionDataStorePersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  private def _temporary_directory(prefix: String): java.nio.file.Path = {
    val directory = java.nio.file.Path.of("target", "phase105", "work", "WorkflowCompletionDataStorePersistenceSpec").toAbsolutePath
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, prefix)
  }
  private given _context: ExecutionContext = ExecutionContext.create()
  private val _instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_instance_v1"))
  private val _continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_continuation_v1"))
  private val _component = ComponentId("org.goldenport.cncf.test.CompletionWork")
  private val _completion_operation = StateMachineOperationIdentity("workflow", "submitReview")
  private val _owner = _take(DataStoreComponentOwner.create("org.goldenport.cncf.test.CompletionWork"))
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("store-1")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("store-1")))
  private val _codec = new PayloadCodec[String] {
    val typeIdentity = "review.input.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("review input must be text")
  }
  private def _take[A](result: Consequence[A]): A = {
     result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  }
  private def _resource(resourcepath: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(resourcepath), "UTF-8")
    try source.mkString finally source.close()
  }
  private def _binding: DefinitionBinding = {
    val producer = _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json")))
    _take(WorkflowInstancePersistence.bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
      _resource("/workflow/candidate-admission-workflow-abi.json").trim, producer,
      "modeler/candidate-admission-workflow.cml"))))
  }
  private def _initial: InstanceRecord =
    InstanceRecord(InstanceIdentity("instance-1"), _binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("correlation-1"), CausationReference("start-1"),
      None, None, None, Vector.empty)
  private def _intent(current: InstanceRecord, token: String = "continuation-1"):
      WorkflowInstanceAtomicTransitionV1.SuspensionIntent = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"),
      Some(StateMachineInputTypeReference(_codec.typeIdentity)), Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector(ContextReference("accepted", "1"))), EvidenceContract("review.evidence", Vector(ContextReference("review-proof", "1"))), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("run-1"),
      ContinuationIdentity(token), StateMachineRevision("machine-rev-1"), required,
      ContextBundle("review context", Vector.empty, Vector.empty, ContextSnapshot(current.definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(token), InstanceRevision(1L),
        ContextSnapshotReference(current.definition.workflowRevision.value), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("resume-1"))))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      _config, current, initialRevision, entry, continuation)
  }
  private def _issue(request: WorkflowInstanceAtomicTransitionV1.SuspensionIntent): WorkflowInteraction[String, Nothing] = {
    val c = request.continuation.continuation
    WorkflowInteraction(_take(WorkflowHandle.fromRecordC(_component, request.current)),
      WorkflowContinuation.WorkOrder(ContinuationRequest(c.runId, c.continuationId, c.expectedRevision,
        c.requiredOperation.identity, c.requiredOperation.operation, Some(TypedValue(_codec.typeIdentity, "exact review input")),
        c.requiredOperation.resultType, c.context, c.requiredOperation.metadata.completionContract,
        c.requiredOperation.metadata.evidenceContract, _completion_operation),
        ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"),
          ReasoningLevel.Deep, true), MinimalPresentation("Review", "Awaiting semantic result")))
  }
  private def _root(store: DataStore) = _take(WorkflowInstanceDataStorePersistence.bindC(store, _config, Vector(_binding), _instances))
  private def _claims(store: DataStore) = _take(WorkflowContinuationDataStorePersistence.bindC(store, _config, _continuations))
  private def _issued(store: DataStore, inputcodec: PayloadCodec[String] = _codec) =
    _take(WorkflowIssuedWorkDataStorePersistence.bindC(store, _config, _component, _completion_operation, Vector(_binding), _instances, _continuations, inputcodec))
  private def _path: Path = _temporary_directory("cncf-phase88-issued-").resolve("workflow.sqlite")
  private val _identity = ContinuationIdentity("continuation-1")
  private def _key(id: String) = DataStore.StringEntryId(id)
  private def _sqlite_config: SQLiteConfig = {
    val result = new SQLiteConfig()
    result.setBusyTimeout(10000)
    result.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    result
  }


  private val _result_codec = new PayloadCodec[String] {
    val typeIdentity = "review.result.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("review result must be text")
  }
  private def _completion(store: DataStore, c: PayloadCodec[String] = _result_codec) =
    _take(WorkflowCompletionDataStorePersistence.bindC(store, _config, _component, _completion_operation,
      Vector(_binding), _instances, _continuations, _codec, c))
  private def _submitted(work: WorkflowInteraction[String, Nothing]): ContinuationResult[String] = {
    val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
    val request = order.request
    ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision,
      request.context.snapshot, TypedValue(_result_codec.typeIdentity, "accepted semantic result"),
      ContextReference("result-1", "1"), request.completion.requiredFacts,
      ExecutionEvidence(request.evidence.requiredEvidence, Some("worker-1"), Some("model-1"),
        Some(SkillDispatchEvidence(order.requirement, "semantic-review", "mapping-v1"))))
  }
  private final case class AvailableCompletion(current: InstanceRecord, entry: HistoryEntry,
      result: ContinuationResult[String])
  private def _seed_available(store: DataStore): AvailableCompletion = {
    val current = _initial
    val request = _intent(current)
    val work = _issue(request)
    _take(_root(store).create(_config, current))
    val suspended = _take(_issued(store).suspendC(request, work, _owner)).instance
    val entry = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Completed,
      None, current.correlation, CausationReference("complete-1"), None, None, None)
    AvailableCompletion(suspended, entry, _submitted(work))
  }
  private def _seed(store: DataStore): WorkflowCompletionDataStorePersistence.Intent[String] = {
    val available = _seed_available(store)
    val token = _take(_claims(store).claimC(_identity)).claimId.get
    WorkflowCompletionDataStorePersistence.Intent(available.current, available.current.revision,
      available.entry, token, available.result)
  }
  private def _stored(store: DataStore) = (
    _take(store.load(_instances, _key("instance-1"))).get,
    _take(store.load(_continuations, _key(_identity.value))).get)
  private def _reason[A](value: Consequence[A], expected: String): Unit = {
     value match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.cause.descriptor.facets.collect {
        case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
      } should contain (expected)
    case _ => fail("expected structured failure")
  }
  }

  private val _event_cursor = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_cursor_v1"))
  private val _event_facts = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_events_v1"))
  private val _event_indexes = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_indexes_v1"))
  private val _event_declaration = _take(EventStorePersistenceDeclaration.createC("events", _event_cursor, _event_facts, _event_indexes,
    Vector(EventStorePersistenceDeclaration.WorkflowGrant(_component, "workflow", _instances, _continuations)), _key("stream")))
  private val _event_policy = _take(EventPersistencePolicy.createC("completion-policy-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist))))
  private def _ordinary_event(token: String, kind: String = "fact") = {
    val time = Instant.parse("2026-09-30T00:00:00Z")
    EventRecord(EventId("test", "completion", Some(time), Some(token)), kind, kind,
      Map("value" -> token), Map.empty, time, true, EventRecord.Status.Stored, EventLane.Transactional)
  }
  private def _environment(s: Subsystem) = ComponentDataStore.Environment(_context.runtime.resolvedParameters, Some(s.configuration))
  private def _provision_event_schema(s: Subsystem, store: DataStore): Unit = {
    // Native absence requires an existing canonical event schema, not a missing table.
    val selected = _take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(_ordinary_event("schema"))))
    val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _event_declaration, EventCursorRecordV1.initial))
    proposal.events.creates.foreach { c =>
      _take(store.create(c.collection, c.entryId, c.record))
      _take(store.delete(c.collection, c.entryId))
    }
  }
  private def _with_managed_host(f: Subsystem => Unit): Unit = {
    val file = _temporary_directory("cncf-managed-completion-").resolve("workflow.sqlite")
    _with_managed_host_at(file)(f)
  }
  private def _with_managed_host_at(file: Path)(f: Subsystem => Unit): Unit = {
    val configured = ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(file.toString))), ConfigurationTrace.empty)
    val scope = ScopeContext(ScopeKind.Subsystem, "completion-host", None, _context.observability)
    val host = _take(SubsystemEventStoreBootstrap.createC(_event_policy, _event_declaration, scope, configured))
    try f(host.subsystem)
    finally _take(host.shutdownC())
  }

  "Phase 88 typed completion through the managed event host" should {
    "execute a declared Decision completion Service and retain committed answers despite response projection failure" in {
      Vector(false, true).foreach { rejectresponse =>
        Given("a native managed Decision and its separately declared public completion Operation; human evidence remains a fixture")
        Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R3; Example: E4; different Provided provenance and an irrelevant earlier projected history body")
        _with_managed_host { s =>
          org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture.admit(s)
          _take(s.initializeEventCursorC(_environment(s)))
          val issueintent = _intent(_initial)
          val work = _issue(issueintent)
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
          val decision = WorkflowInteraction[String, Nothing](work.handle,
            WorkflowContinuation.Decision(order.request, MinimalPresentation("Fixture choice", "No effect grant")))
          val diagnostic = _submitted(work).evidence
          val answer = _submitted(work).copy(evidence = diagnostic.copy(workerIdentity = Some(""),
            modelIdentity = Some(" "), skillDispatch = diagnostic.skillDispatch.map(value =>
              value.copy(requestedRequirement = value.requestedRequirement.copy(reviewRequired = false)))))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            space.dataStore(_instances).flatMap(store => _root(store).create(_config, issueintent.current))
          })
          val questions = _take(WorkflowManagedIssuedDecision.bindC(s, _component, _config, _completion_operation,
            Vector(_binding), _instances, _continuations, _codec))
          val suspended = _take(questions.suspendC(issueintent, decision)).instance
          val instancebinding = _take(WorkflowInstancePersistenceBinding.bindC(_component, s, _environment(s),
            WorkflowInstancePersistenceBinding.Declaration(_config, Vector(_binding), _instances)))
          val closingtype = StateMachineResultTypeReference("public.decision.closing.v1")
          val closingresult = StateMachineOperationResult(closingtype, ContextReference("decision-closing", "1"))
          val completionbinding = _take(WorkflowManagedDecisionCompletion.bindC(s, _component, _config,
            _completion_operation, Vector(_binding), _instances, _continuations, _codec, _result_codec,
            closingtype, (_, _) => Consequence.success(Vector.empty)))
          val apioperation = GeneratedProvidedApiAbi.Operation(_completion_operation.service, _completion_operation.operation,
            Some("decision.carrier.v1"), Some("decision.response.v1"), GeneratedProvidedApiAbi.SourceIdentity(Some(1)))
          val api = GeneratedProvidedApiAbi.Definition(GeneratedProvidedApiAbi.schemaVersion,
            "different-diagnostic-producer", suspended.definition.workflowIdentity.value,
            suspended.definition.workflowRevision.value,
            GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(Some(1)),
              GeneratedProvidedApiAbi.SourceIdentity(Some(1))), Vector(apioperation.copy(source = GeneratedProvidedApiAbi.SourceIdentity(Some(999)))))
          var selections = 0
          var interpretations = 0
          var proposaldefect = "none"
          val closing = new WorkflowCompletionServiceOperation.ClosingProgram[String] {
            def selectC(result: ContinuationResult[String], current: InstanceRecord):
                Consequence[WorkflowCompletionServiceOperation.SelectedClosing] = {
              selections += 1
              current.appendC(current.revision, HistoryEntry(HistorySequence(2L), InstanceRevision(2L),
                Lifecycle.Completed, None, current.correlation, CausationReference("decision-complete"),
                None, None, None)).map { next =>
                val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
                  interpretations += 1
                  ActionExecution.Completed(closingresult): ActionExecution
                }
                val last = if (proposaldefect == "entry") next.history.last.copy(sequence = HistorySequence(9L))
                  else next.history.last
                val projected = next.copy(
                  identity = if (proposaldefect == "target") InstanceIdentity("foreign") else next.identity,
                  lifecycle = null, currentProgression = null, suspension = null,
                  history = Vector(null, null, last))
                WorkflowCompletionServiceOperation.SelectedClosing(program, Some(projected))
              }
            }
          }
          val publiccodec = new WorkflowCompletionServiceOperation.Codec[String] {
            val resultTypeIdentity = _result_codec.typeIdentity
            def decodeC(raw: Request): Consequence[ContinuationResult[String]] = {
               raw.arguments match {
              case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, _result_codec)
              case _ => Consequence.stateConflict("missing Decision result JSON")
            }
            }
            def encodeC(value: ContinuationRuntime.Resume): Consequence[OperationResponse] =
              Consequence.success(OperationResponse.Scalar("DECISION_COMPLETED"))
            override def encodeAfterCommitC(value: ContinuationRuntime.Resume, result: ContinuationResult[String],
                completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
              value.result shouldBe closingresult
              completed.get.revision shouldBe InstanceRevision(2L)
              completed.get.history.head.causation shouldBe suspended.history.head.causation
              _take(completionbinding.loadRetainedC(_identity)).get.completion.result shouldBe answer
              if (rejectresponse) Consequence.stateConflict("fixture Decision response failure") else encodeC(value)
            }
          }
          When("an undeclared carrier or an actually missing or blank declared carrier is bound")
          val undeclaredcarrier = WorkflowDecisionCompletionServiceOperation.bindC(api, apioperation.copy(inputType = Some("wrong")),
            spec.RequestDefinition(), spec.ResponseDefinition.void, publiccodec, questions, instancebinding,
            closing, completionbinding)
          val invalidcarriers = Vector(None, Some(" ")).map { input =>
            val declared = apioperation.copy(inputType = input)
            WorkflowDecisionCompletionServiceOperation.bindC(api.copy(providedOperations = Vector(declared)), declared,
              spec.RequestDefinition(), spec.ResponseDefinition.void, publiccodec, questions, instancebinding,
              closing, completionbinding)
          }
          Then("semantic Operation resolution and actual nonblank carrier presence remain required")
          undeclaredcarrier.isSuccess shouldBe false
          invalidcarriers.forall(!_.isSuccess) shouldBe true
          Given("explicit test-only Decision descriptions, which provide no Human consent or generated producer evidence")
          val descriptions = DescriptiveAttributes.Builder()
            .summary("Submit the issued Decision answer")
            .description("Use the existing managed Decision completion; descriptive metadata supplies no permission.")
            .build()
          val requestdefinition = spec.RequestDefinition()
          When("the declared Decision completion is bound with its original omitted metadata")
          val defaultoperation = _take(WorkflowDecisionCompletionServiceOperation.bindC(api, apioperation,
            spec.RequestDefinition(), spec.ResponseDefinition.void, publiccodec, questions, instancebinding,
            closing, completionbinding))
          Then("legacy callers retain empty descriptive metadata")
          defaultoperation.specification.content.descriptiveAttributes shouldBe DescriptiveAttributes.empty
          When("the same managed binding receives only descriptive metadata")
          val operation = _take(WorkflowDecisionCompletionServiceOperation.bindC(api, apioperation,
            requestdefinition, spec.ResponseDefinition.void, publiccodec, questions, instancebinding,
            closing, completionbinding, descriptiveAttributes = descriptions))
          val invalidmetadata = WorkflowDecisionCompletionServiceOperation.bindC(api, apioperation,
            requestdefinition, spec.ResponseDefinition.void, publiccodec, questions, instancebinding,
            closing, completionbinding, descriptiveAttributes = null)
          Then("the description is retained without changing the canonical name, transport or managed effects")
          operation.name shouldBe apioperation.name
          operation.specification.content.descriptiveAttributes shouldBe descriptions
          operation.specification.content.nameAttributes shouldBe BaseContent.simple(apioperation.name).nameAttributes
          operation.specification.request should be theSameInstanceAs requestdefinition
          operation.specification.response should be theSameInstanceAs spec.ResponseDefinition.void
          invalidmetadata shouldBe a[Consequence.Failure[_]]
          selections shouldBe 0
          interpretations shouldBe 0
          val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
            name = _completion_operation.service, operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))))
          s.add(TestComponentFactory.create(_component.name, protocol, subsystem = s))
          def _raw_(result: ContinuationResult[String]) = Request.of(component = _component.name,
            service = _completion_operation.service, operation = _completion_operation.operation,
            arguments = List(Argument("arg1", _take(WorkflowResultJsonV1.encodeC(result, _result_codec)))))
          When("missing, stale or foreign-owner answers enter the actual Service before any permitted completion")
          s.executeOperationResponse(_raw_(answer).copy(arguments = Nil)).isSuccess shouldBe false
          Vector(answer.copy(expectedRevision = StateMachineRevision("foreign")),
            answer.copy(handle = answer.handle.copy(componentIdentity = ComponentId("org.goldenport.cncf.test.Foreign")))).foreach { invalid =>
            s.executeOperationResponse(_raw_(invalid)).isSuccess shouldBe false
          }
          Then("pure selection may observe stale input but actual admission forbids interpretation")
          selections shouldBe 1
          interpretations shouldBe 0
          When("the selector supplies an invalid adjacent entry or actual target")
          val invalidproposals = Vector("entry", "target").map { defect =>
            proposaldefect = defect
            s.executeOperationResponse(_raw_(answer))
          }
          proposaldefect = "none"
          Then("neither invalid consumed proposal starts the closing program or changes the actual history")
          invalidproposals.forall(_.toOption.isEmpty) shouldBe true
          interpretations shouldBe 0
          _take(instancebinding.loadC(suspended.identity)).get.history shouldBe suspended.history
          When("the applicable answer carries copied dispatch diagnostics and response projection may reject after commit")
          val response = s.executeOperationResponse(_raw_(answer))
          Then("the managed completion is retained exactly once and retry never reinterprets")
          if (rejectresponse) response.isSuccess shouldBe false
          else _take(response) shouldBe OperationResponse.Scalar("DECISION_COMPLETED")
          interpretations shouldBe 1
          _take(completionbinding.loadRetainedC(_identity)).get.completion.result shouldBe answer
          _take(completionbinding.loadRetainedC(_identity)).get.completion.closingResult shouldBe Some(closingresult)
          When("the same answer is submitted after the retained completion")
          val replay = s.executeOperationResponse(_raw_(answer))
          Then("the committed closing is not interpreted again")
          replay.isSuccess shouldBe false
          interpretations shouldBe 1
          s.systemNode.activeLeaseCount shouldBe 0
          s.systemNode.managedDataStoreCount shouldBe 1
        }
      }
    }

    "complete only the host-bound Decision and recover its exact retained answer after physical reopen" in {
      Given("a native Decision published through the captured shared-store host; evidence is a fixture, not human proof")
      val file = _temporary_directory("cncf-managed-decision-completion-").resolve("workflow.sqlite")
      val issueintent = _intent(_initial)
      val work = _issue(issueintent)
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      val decision = WorkflowInteraction[String, Nothing](work.handle,
        WorkflowContinuation.Decision(order.request, MinimalPresentation("Fixture choice", "No command grant")))
      val result = _submitted(work)
      val closingtype = StateMachineResultTypeReference("decision.closing.v1")
      val closingresult = StateMachineOperationResult(closingtype, ContextReference("decision-closing", "1"))
      var interpretations = 0
      def _bound_(s: Subsystem) = _take(WorkflowManagedDecisionCompletion.bindC(s, _component, _config,
        _completion_operation, Vector(_binding), _instances, _continuations, _codec, _result_codec,
        closingtype, (_, _) => Consequence.success(Vector.empty)))
      _with_managed_host_at(file) { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          space.dataStore(_instances).flatMap(store => _root(store).create(_config, issueintent.current))
        })
        val questions = _take(WorkflowManagedIssuedDecision.bindC(s, _component, _config, _completion_operation,
          Vector(_binding), _instances, _continuations, _codec))
        val suspended = _take(questions.suspendC(issueintent, decision)).instance
        val entry = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Completed,
          None, suspended.correlation, CausationReference("decision-complete"), None, None, None)
        val next = _take(suspended.appendC(suspended.revision, entry))
        val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
          interpretations += 1
          ActionExecution.Completed(closingresult): ActionExecution
        }
        val selected = WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
        val completion = _bound_(s)
        When("stale or incomplete answers, a foreign owner or the WorkOrder completion route are offered before a valid result")
        Vector(result.copy(expectedRevision = StateMachineRevision("foreign")),
          result.copy(contextSnapshot = ContextSnapshot("foreign")),
          result.copy(completionFacts = Vector.empty), result.copy(evidence = ExecutionEvidence(Vector.empty)),
          result.copy(result = TypedValue("foreign", "answer"))).foreach { invalid =>
          completion.completeC(_component, invalid, suspended, selected, _context) shouldBe a[Consequence.Failure[?]]
        }
        completion.completeC(ComponentId("org.goldenport.cncf.test.Foreign"), result,
          suspended, selected, _context) shouldBe a[Consequence.Failure[?]]
        val workcompletion = _take(WorkflowManagedCompletion.bindC(s, _component, _config, _completion_operation,
          Vector(_binding), _instances, _continuations, _codec, _result_codec, closingtype,
          (_, _) => Consequence.success(Vector.empty)))
        workcompletion.completeC(_component, result, suspended, selected, _context) shouldBe a[Consequence.Failure[?]]
        WorkflowProtocolV1.admitResultC(decision, result) shouldBe a[Consequence.Failure[?]]
        val malformed = decision.copy(current = WorkflowContinuation.Decision(
          order.request.copy(resultType = Some(null)), order.presentation))
        WorkflowProtocolV1.admitDecisionResultC(malformed, result) shouldBe a[Consequence.Failure[?]]
        Then("no closing program ran or answer/history was retained")
        interpretations shouldBe 0
        _take(completion.loadRetainedC(_identity)) shouldBe None
        val unchanged = _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          space.dataStore(_instances).flatMap(store => _root(store).load(_config, _initial.identity))
        }).get
        _take(WorkflowInstanceRecordJsonV1.encodeC(unchanged)) shouldBe
          _take(WorkflowInstanceRecordJsonV1.encodeC(suspended))
        When("the applicable Decision result enters its managed binding with copied dispatch diagnostics, not Human consent")
        val completed = _take(completion.completeC(_component, result, suspended, selected, _context))
        Then("claim completion, closing result, answer and adjacent history are retained together")
        _take(WorkflowInstanceRecordJsonV1.encodeC(completed.instance)) shouldBe
          _take(WorkflowInstanceRecordJsonV1.encodeC(next))
        completed.resume.result shouldBe closingresult
        val retained = _take(completion.loadRetainedC(_identity)).get.completion
        retained.result shouldBe result
        retained.closingResult shouldBe Some(closingresult)
        retained.instanceRevision shouldBe next.revision
        completion.completeC(_component, result, suspended, selected, _context) shouldBe a[Consequence.Failure[?]]
        interpretations shouldBe 1
        workcompletion.loadRetainedC(_identity) shouldBe a[Consequence.Failure[?]]
        s.systemNode.managedDataStoreCount shouldBe 1
        s.systemNode.activeLeaseCount shouldBe 0
      }
      When("a fresh managed host reopens the same physical database")
      _with_managed_host_at(file) { s =>
        Then("query recovers the exact answer/closing result without claiming or interpreting again")
        val retained = _take(_bound_(s).loadRetainedC(_identity)).get.completion
        retained.result shouldBe result
        retained.closingResult shouldBe Some(closingresult)
        interpretations shouldBe 1
        s.systemNode.managedDataStoreCount shouldBe 1
        s.systemNode.activeLeaseCount shouldBe 0
      }
    }

    "release its owned Decision claim after a pre-native closing failure and admit one later valid completion" in {
      Given("a managed pending Decision and a closing program with a wrong result contract")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        val issueintent = _intent(_initial)
        val work = _issue(issueintent)
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
        val decision = WorkflowInteraction[String, Nothing](work.handle,
          WorkflowContinuation.Decision(order.request, order.presentation))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          space.dataStore(_instances).flatMap(store => _root(store).create(_config, issueintent.current))
        })
        val questions = _take(WorkflowManagedIssuedDecision.bindC(s, _component, _config, _completion_operation,
          Vector(_binding), _instances, _continuations, _codec))
        val suspended = _take(questions.suspendC(issueintent, decision)).instance
        val result = _submitted(work).copy(evidence = ExecutionEvidence(order.request.evidence.requiredEvidence))
        val closingtype = StateMachineResultTypeReference("decision.closing.v1")
        val valid = StateMachineOperationResult(closingtype, ContextReference("decision-closing", "1"))
        val next = _take(suspended.appendC(suspended.revision, HistoryEntry(HistorySequence(2L),
          InstanceRevision(2L), Lifecycle.Completed, None, suspended.correlation,
          CausationReference("decision-complete"), None, None, None)))
        val completion = _take(WorkflowManagedDecisionCompletion.bindC(s, _component, _config, _completion_operation,
          Vector(_binding), _instances, _continuations, _codec, _result_codec, closingtype,
          (_, _) => Consequence.success(Vector.empty)))
        def _selected_(actual: StateMachineOperationResult) = WorkflowCompletionServiceOperation.SelectedClosing(
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(actual)), Some(next))
        When("the actually prepared closing result has a different type")
        completion.completeC(_component, result, suspended,
          _selected_(valid.copy(typeReference = StateMachineResultTypeReference("wrong"))), _context) shouldBe a[Consequence.Failure[?]]
        Then("no completion exists and the unclaimed question remains Available")
        _take(completion.loadRetainedC(_identity)) shouldBe None
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          space.dataStore(_continuations).flatMap(store => _claims(store).loadC(_identity))
        }).get.status shouldBe ContinuationRuntimePersistence.Status.Available
        When("the correct closing program completes the still-current question")
        val completed = _take(completion.completeC(_component, result, suspended, _selected_(valid), _context))
        Then("one adjacent completion is stored and every managed lease is returned")
        _take(WorkflowInstanceRecordJsonV1.encodeC(completed.instance)) shouldBe
          _take(WorkflowInstanceRecordJsonV1.encodeC(next))
        _take(completion.loadRetainedC(_identity)).get.completion.result shouldBe result
        s.systemNode.activeLeaseCount shouldBe 0
        s.systemNode.managedDataStoreCount shouldBe 1
      }
    }

    "execute a declared public completion Operation through host-owned joint storage without legacy resume or separate append" in {
      Vector(false, true).foreach { rejectresponse =>
        Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R3; Example: E4; a declared WorkOrder Service with different Provided provenance and irrelevant projected history")
        _with_managed_host { s =>
          org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture.admit(s)
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            _provision_event_schema(s, store)
            val request = _seed_available(store)
            val closingtype = StateMachineResultTypeReference("public.actual.closing.v1")
            val closingresult = StateMachineOperationResult(closingtype, ContextReference("public-closing", "1"))
            var interpreted = 0
            var deliveries = 0
            var legacycalls = 0
            var eventpreparations = 0
            var proposaldefect = "none"
            val readonly = new WorkflowInstancePersistence {
              def create(c: Configuration, value: InstanceRecord): Consequence[InstanceRecord] = {
                legacycalls += 1; Consequence.stateConflict("unexpected separate create")
              }
              def load(c: Configuration, id: InstanceIdentity): Consequence[Option[InstanceRecord]] = _root(store).load(c, id)
              def append(c: Configuration, id: InstanceIdentity, revision: InstanceRevision,
                  entry: HistoryEntry): Consequence[InstanceRecord] = {
                legacycalls += 1; Consequence.stateConflict("unexpected separate append")
              }
            }
            val apioperation = GeneratedProvidedApiAbi.Operation(_completion_operation.service, _completion_operation.operation,
              Some("completion.carrier.v1"), Some("completion.response.v1"), GeneratedProvidedApiAbi.SourceIdentity(Some(1)))
            val api = GeneratedProvidedApiAbi.Definition(GeneratedProvidedApiAbi.schemaVersion,
              "different-diagnostic-producer", request.current.definition.workflowIdentity.value,
              request.current.definition.workflowRevision.value,
              GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(Some(1)),
                GeneratedProvidedApiAbi.SourceIdentity(Some(1))), Vector(apioperation.copy(source = GeneratedProvidedApiAbi.SourceIdentity(Some(999)))))
            val closing = new WorkflowCompletionServiceOperation.ClosingProgram[String] {
              def selectC(result: ContinuationResult[String], current: InstanceRecord):
                  Consequence[WorkflowCompletionServiceOperation.SelectedClosing] =
                current.appendC(current.revision, request.entry).map { next =>
                  val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
                    interpreted += 1
                    ActionExecution.Completed(closingresult): ActionExecution
                  }
                  val last = if (proposaldefect == "entry") next.history.last.copy(revision = InstanceRevision(9L))
                    else next.history.last
                  val projected = next.copy(
                    identity = if (proposaldefect == "target") InstanceIdentity("foreign") else next.identity,
                    lifecycle = null, currentProgression = null, suspension = null,
                    history = Vector(null, null, last))
                  WorkflowCompletionServiceOperation.SelectedClosing(program, Some(projected))
                }
            }
            val publiccodec = new WorkflowCompletionServiceOperation.Codec[String] {
              val resultTypeIdentity = _result_codec.typeIdentity
              def decodeC(raw: Request): Consequence[ContinuationResult[String]] = {
                 raw.arguments match {
                case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, _result_codec)
                case _ => Consequence.stateConflict("missing completion JSON")
              }
              }
              def encodeC(value: ContinuationRuntime.Resume): Consequence[OperationResponse] =
                Consequence.success(OperationResponse.Scalar("COMPLETED"))
              override def encodeAfterCommitC(value: ContinuationRuntime.Resume, result: ContinuationResult[String],
                  completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
                value.result shouldBe closingresult
                value.continuation.continuationId shouldBe _identity
                completed.get.history shouldBe request.current.history :+ request.entry
                _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
                if (rejectresponse) Consequence.stateConflict("fixture response projection") else encodeC(value)
              }
            }
            val managed = _take(WorkflowManagedCompletion.bindC(s, _component, _config, _completion_operation, Vector(_binding),
              _instances, _continuations, _codec, _result_codec, closingtype,
              (_, _) => { eventpreparations += 1; Consequence.success(Vector(_ordinary_event("public-managed"))) }))
            val preliminaryissued = new IssuedWorkOrderPersistence[String] {
              def putIfAbsentC(value: WorkflowInteraction[String, Nothing]): Consequence[WorkflowInteraction[String, Nothing]] = {
                legacycalls += 1
                Consequence.stateConflict("unexpected preliminary issued mutation")
              }
              def loadC(id: ContinuationIdentity): Consequence[Option[WorkflowInteraction[String, Nothing]]] = {
                legacycalls += 1
                Consequence.stateConflict("unexpected preliminary issued read")
              }
            }
            When("an undeclared WorkOrder carrier or a declared missing or blank carrier is bound")
            val undeclaredcarrier = WorkflowCompletionServiceOperation.bindC(api, apioperation.copy(inputType = Some("wrong")),
              spec.RequestDefinition(), spec.ResponseDefinition.void, publiccodec, ContinuationRuntime.empty,
              preliminaryissued, readonly, _config, () => throw new IllegalStateException("binding must not run UoW"),
              Some(closing), Some(managed))
            val invalidcarriers = Vector(None, Some(" ")).map { input =>
              val declared = apioperation.copy(inputType = input)
              WorkflowCompletionServiceOperation.bindC(api.copy(providedOperations = Vector(declared)), declared,
                spec.RequestDefinition(), spec.ResponseDefinition.void, publiccodec, ContinuationRuntime.empty,
                preliminaryissued, readonly, _config, () => throw new IllegalStateException("binding must not run UoW"),
                Some(closing), Some(managed))
            }
            Then("actual Operation/carrier admission rejects without preliminary reads or effects")
            undeclaredcarrier.isSuccess shouldBe false
            invalidcarriers.forall(!_.isSuccess) shouldBe true
            legacycalls shouldBe 0
            interpreted shouldBe 0
            When("the separately declared nonblank carrier is bound to its semantic result codec")
            val operation = _take(WorkflowCompletionServiceOperation.bindC(api, apioperation, spec.RequestDefinition(),
              spec.ResponseDefinition.void, publiccodec, ContinuationRuntime.empty, preliminaryissued, readonly, _config,
              () => { legacycalls += 1; throw new IllegalStateException("unexpected legacy UoW factory") },
              Some(closing), Some(managed)))
            Then("the declared public completion Operation binds without equating carrier and result identities")
            operation.name shouldBe apioperation.name
            val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
              name = _completion_operation.service, operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))))
            val publiccomponent = TestComponentFactory.create(_component.name, protocol, subsystem = s)
            s.add(publiccomponent)
            s.eventBus.register(EventSubscription("public-managed", handler = new EventDispatchHandler {
              def dispatch(event: DomainEvent): Consequence[Unit] = {
                deliveries += 1
                _take(_completion(store).loadC(_identity)).get.closingResult shouldBe Some(closingresult)
                Consequence.unit
              }
            }))
            val raw = Request.of(component = _component.name, service = _completion_operation.service,
              operation = _completion_operation.operation,
              arguments = List(Argument("arg1", _take(WorkflowResultJsonV1.encodeC(request.result, _result_codec)))))
            When("missing and stale semantic results enter the separately declared carrier")
            val rejectedinputs = Vector(raw.copy(arguments = Nil), raw.copy(arguments = List(Argument("arg1",
              _take(WorkflowResultJsonV1.encodeC(request.result.copy(expectedRevision = StateMachineRevision("foreign")), _result_codec))))))
              .map(value => s.executeOperationResponse(value))
            Then("request and native admission prevent interpretation or event preparation")
            rejectedinputs.forall(_.toOption.isEmpty) shouldBe true
            interpreted shouldBe 0
            eventpreparations shouldBe 0
            When("the pure selector changes a consumed adjacent revision or actual target")
            val rejectedproposals = Vector("entry", "target").map { defect =>
              proposaldefect = defect
              s.executeOperationResponse(raw)
            }
            proposaldefect = "none"
            Then("invalid consumed proposals leave the claim and actual history untouched")
            rejectedproposals.forall(_.toOption.isEmpty) shouldBe true
            interpreted shouldBe 0
            eventpreparations shouldBe 0
            _take(_claims(store).loadC(_identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Available
            When("the valid result selects a next record with a malformed unused copied prefix")
            val response = s.executeOperationResponse(raw)
            Then("actual retained history completes once before durable delivery and response projection")
            if (rejectresponse) response.isSuccess shouldBe false
            else _take(response) shouldBe OperationResponse.Scalar("COMPLETED")
            interpreted shouldBe 1
            eventpreparations shouldBe 1
            deliveries shouldBe 1
            legacycalls shouldBe 0
            _take(_claims(store).loadC(_identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Completed
            _take(_completion(store).loadC(_identity)).get.closingResult shouldBe Some(closingresult)
            When("the completed request is submitted again")
            val replay = s.executeOperationResponse(raw)
            Then("the original completion remains without replaying interpretation or delivery")
            replay.isSuccess shouldBe false
            interpreted shouldBe 1
            deliveries shouldBe 1
            legacycalls shouldBe 0
            Consequence.unit
          })
        }
      }
    }

    "complete Available directly without claims and retain actual and semantic results in one native commit" in {
      Given("an Available native source and one effect-free closing program with mixed selected events")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          _provision_event_schema(s, store)
          val request = _seed_available(store)
          val beforerevision = _take(_claims(store).loadStoredC(_identity)).get._2
          val closingtype = StateMachineResultTypeReference("host.closing.v1")
          val actionresult = StateMachineOperationResult(closingtype, ContextReference("actual-closing", "1"))
          var interpretations = 0
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
            interpretations += 1
            val during = _take(_claims(store).loadStoredC(_identity)).get
            during._1.status shouldBe ContinuationRuntimePersistence.Status.Available
            during._1.claimId shouldBe None
            during._2 shouldBe beforerevision
            ActionExecution.Completed(actionresult): ActionExecution
          }
          val input = Vector(_ordinary_event("owned-native"), _ordinary_event("owned-transient", "ephemeral"))
          var delivered = Vector.empty[EventRecord]
          val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
          bus.register(EventSubscription("owned-native", handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              _take(_completion(store).loadC(_identity)).get.closingResult shouldBe Some(actionresult)
              _take(_claims(store).loadC(_identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Completed
              event match {
                case FinalizedRecordDomainEvent(record) => delivered :+= record; Consequence.unit
                case _ => Consequence.stateConflict("expected finalized record")
              }
            }
          }))
          def _execute_() = _completion(store).executeSelectedClosingHostC(request.current, request.entry,
            request.result, program, closingtype, _owner, space, input,
            batch => batch.foldLeft(Consequence.unit)((acc, event) =>
              acc.flatMap(_ => bus.dispatchCommittedRecordC(event).map(_ => ()))))
          When("the host executes and commits the closing program")
          val retained = _take(_execute_())
          Then("actual and submitted results, adjacent history and mixed delivery belong to one native commit")
          retained.retained.result shouldBe request.result
          retained.retained.closingResult shouldBe Some(actionresult)
          retained.instance.history shouldBe request.current.history :+ request.entry
          retained.continuation.claimId shouldBe None
          _take(_claims(store).loadStoredC(_identity)).get._2 shouldBe _take(beforerevision.nextC)
          delivered.map(_.id) shouldBe input.map(_.id)
          interpretations shouldBe 1
          When("the already completed source is submitted again")
          val replay = _execute_()
          Then("the completed source rejects without repeating interpretation or delivery")
          replay.isSuccess shouldBe false
          interpretations shouldBe 1
          delivered.size shouldBe 2
          Consequence.unit
        })
      }
    }

    "reject a stored Claimed source before closing instead of adapting or releasing it" in {
      Given("a claimed legacy source presented to the token-free native host")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          val request = _seed(store) // Explicit legacy state is deliberately incompatible.
          val before = _stored(store)
          var interpretations = 0
          var deliveries = 0
          val closingtype = StateMachineResultTypeReference("host.single-user.v1")
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
            interpretations += 1
            ActionExecution.Completed(StateMachineOperationResult(closingtype,
              ContextReference("closing", "1"))): ActionExecution
          }
          When("the host selects the incompatible Claimed source")
          val rejected = _completion(store).executeSelectedClosingHostC(request.current, request.entry,
            request.result, program, closingtype, _owner, space, Vector.empty,
            _ => { deliveries += 1; Consequence.unit })
          Then("no closing program, delivery, claim release or completion occurs")
          rejected.isSuccess shouldBe false
          interpretations shouldBe 0
          deliveries shouldBe 0
          _stored(store) shouldBe before
          _take(_claims(store).loadC(_identity)).get.claimId shouldBe Some(request.claimId)
          _take(_completion(store).loadC(_identity)) shouldBe None
          Consequence.unit
        })
      }
    }

    "encode token-free Completed records and reject malformed status/token combinations" in {
      Given("Available, token-free Completed and malformed status/token combinations")
      val available = _intent(_initial).continuation
      val completed = available.copy(status = ContinuationRuntimePersistence.Status.Completed)
      val claimedbytes = _take(ContinuationRecordJsonV1.encodeC(available.copy(
        status = ContinuationRuntimePersistence.Status.Claimed, claimId = Some("legacy-token"))))
      val missingtoken = io.circe.parser.parse(claimedbytes).toOption.get
        .mapObject(_.add("claimId", Json.Null)).noSpaces
      When("the native codec consumes each status/token representation")
      val recovered = _take(ContinuationRecordJsonV1.decodeC(_take(ContinuationRecordJsonV1.encodeC(completed))))
      val invalid = Vector(
        ContinuationRecordJsonV1.encodeC(available.copy(claimId = Some("unexpected"))),
        ContinuationRecordJsonV1.encodeC(available.copy(status = ContinuationRuntimePersistence.Status.Claimed)),
        ContinuationRecordJsonV1.encodeC(completed.copy(claimId = Some(" "))))
      val absenttoken = ContinuationRecordJsonV1.decodeC(missingtoken)
      Then("token-free Completed roundtrips while actual inconsistent status/token facts reject")
      recovered shouldBe completed
      completed.claimId shouldBe None
      invalid.forall(!_.isSuccess) shouldBe true
      absenttoken.isSuccess shouldBe false
    }

    "reject invalid input and pre-native closing failures without changing the Available source" in {
      Vector("semantic", "owner", "closing-type", "closing-failure").foreach { mode =>
        Given(s"an Available native source with the $mode failure selected")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            val request = _seed_available(store)
            val before = _stored(store)
            val closingtype = StateMachineResultTypeReference("host.expected.v1")
            var interpretations = 0
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
              interpretations += 1
              if (mode == "closing-failure") ActionExecution.Failed(
                StateMachineOperationFailure("failed", "fixture closing failure", Vector.empty))
              else ActionExecution.Completed(StateMachineOperationResult(StateMachineResultTypeReference("wrong.closing.v1"),
                ContextReference("closing", "1")))
            }
            val result = if (mode == "semantic") request.result.copy(completionFacts = Vector.empty) else request.result
            val selectedowner = if (mode == "owner") _take(DataStoreComponentOwner.create("foreign.Component")) else _owner
            var deliveries = 0
            When("the host admits the input and, only when applicable, interprets closing")
            val rejected = _completion(store).executeSelectedClosingHostC(request.current, request.entry, result, program,
              closingtype, selectedowner, space, Vector.empty, _ => { deliveries += 1; Consequence.unit })
            val rejectedbeforeclosing = mode == "semantic" || mode == "owner"
            Then("the failure retains Available status and prior storage without any delivery")
            rejected.isSuccess shouldBe false
            interpretations shouldBe (if (rejectedbeforeclosing) 0 else 1)
            _take(_claims(store).loadC(_identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Available
            _take(_claims(store).loadC(_identity)).get.claimId shouldBe None
            _stored(store) shouldBe before
            _take(_completion(store).loadC(_identity)) shouldBe None
            deliveries shouldBe 0
            Consequence.unit
          })
        }
      }
    }

    "leave the source untouched on closing exceptions while preserving interruption" in {
      Vector("nonfatal", "interrupt").foreach { mode =>
        Given(s"an Available source whose closing program raises $mode")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            val request = _seed_available(store)
            val before = _stored(store)
            val closingtype = StateMachineResultTypeReference("host.control.v1")
            val interrupted = new InterruptedException("owned pre-native interruption")
            val program: ExecUowM[ActionExecution] =
              ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
                if (mode == "interrupt") throw interrupted
                else throw new IllegalStateException("owned pre-native failure")
              }
            var deliveries = 0
            def _execute_() = _completion(store).executeSelectedClosingHostC(request.current, request.entry,
              request.result, program, closingtype, _owner, space, Vector.empty,
              _ => { deliveries += 1; Consequence.unit })
            if (mode == "interrupt") {
              try {
                When("the closing program is interrupted before native commit")
                val raised = intercept[InterruptedException](_execute_())
                Then("the original interruption and interrupted-thread state are preserved")
                raised shouldBe interrupted
                Thread.currentThread().isInterrupted shouldBe true
              } finally Thread.interrupted()
            } else {
              When("the closing program raises a nonfatal pre-native exception")
              val rejected = _execute_()
              Then("the host returns the contained failure")
              rejected.isSuccess shouldBe false
            }
            Then("either exception leaves the original source and history without completion or delivery")
            _take(_claims(store).loadC(_identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Available
            _take(_claims(store).loadC(_identity)).get.claimId shouldBe None
            _take(_completion(store).loadC(_identity)) shouldBe None
            _stored(store) shouldBe before
            val recovered = _take(_root(store).load(_config, request.current.identity)).get
            _take(WorkflowInstanceRecordJsonV1.encodeC(recovered)) shouldBe
              _take(WorkflowInstanceRecordJsonV1.encodeC(request.current))
            deliveries shouldBe 0
            Consequence.unit
          })
        }
      }
    }

    "retain token-free completion after subscriber failure without replay or rollback" in {
      Given("a token-free native completion and a subscriber that rejects committed delivery")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          _provision_event_schema(s, store)
          val request = _seed_available(store)
          val closingtype = StateMachineResultTypeReference("host.dispatch.v1")
          val actionresult = StateMachineOperationResult(closingtype, ContextReference("closing", "1"))
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(actionresult))
          var calls = 0
          val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
          bus.register(EventSubscription("owned-failure", handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              calls += 1; Consequence.stateConflict("owned subscriber failure")
            }
          }))
          def _execute_() = _completion(store).executeSelectedClosingHostC(request.current, request.entry,
            request.result, program, closingtype, _owner, space, Vector(_ordinary_event("owned-failure")),
            batch => batch.foldLeft(Consequence.unit)((acc, event) =>
              acc.flatMap(_ => bus.dispatchCommittedRecordC(event).map(_ => ()))))
          When("the host commits and then invokes the rejecting subscriber")
          val rejected = _execute_()
          Then("delivery failure cannot undo the saved token-free completion")
          rejected.isSuccess shouldBe false
          _take(_claims(store).loadC(_identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Completed
          _take(_claims(store).loadC(_identity)).get.claimId shouldBe None
          _take(_completion(store).loadC(_identity)).get.closingResult shouldBe Some(actionresult)
          calls shouldBe 1
          When("the same completed request is submitted again")
          val replay = _execute_()
          Then("the subscriber is not invoked again")
          replay.isSuccess shouldBe false
          calls shouldBe 1
          Consequence.unit
        })
      }
    }

    "retain native storage and suppress retry after actual EventBus handler failure" in {
      Given("a managed completion participant and an actual EventBus handler that rejects delivery")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          _provision_event_schema(s, store)
          val request = _seed(store)
          val actionresult = StateMachineOperationResult(StateMachineResultTypeReference("closing.dispatch.v1"), ContextReference("dispatch", "1"))
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](ActionExecution.Completed(actionresult))
          val closing = _take(WorkflowClosingProgramPreparation.prepareC(program))
          val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
          var calls = 0
          bus.register(EventSubscription("failure", handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              calls += 1
              _take(_completion(store).loadC(_identity)).get.closingResult shouldBe Some(actionresult)
              Consequence.stateConflict("actual subscriber fixture")
            }
          }))
          val input = Vector(_ordinary_event("first-dispatch"), _ordinary_event("second-dispatch"))
          val participant = _take(_completion(store).prepareClosingHostParticipantC(request, _owner, space, input, closing,
            Vector.empty, () => Consequence.unit, records => records.foldLeft(Consequence.unit) { (acc, record) =>
              acc.flatMap(_ => bus.dispatchCommittedRecordC(record).map(_ => ()))
            }))
          val uow = new ManagedCompletionUnitOfWork(_context, participant)
          When("the UnitOfWork commits native storage and dispatches its committed batch")
          val committed = uow.commit()
          Then("handler failure retains native storage and records a committed termination")
          committed.isSuccess shouldBe false
          uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
          input.foreach(event => _take(store.load(_event_facts, _key(event.id.value))) should not be None)
          _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
          calls shouldBe 1
          When("the same UnitOfWork is asked to commit again")
          val repeated = uow.commit()
          Then("retry is suppressed without another handler call")
          repeated.isSuccess shouldBe false
          calls shouldBe 1
          _take(s.eventStore.query(EventStore.Query())) shouldBe empty
          Consequence.unit
        })
      }
    }

    "recover both actual closing and submitted results after managed SQLite shutdown without interpreting the program again" in {
      Given("a SQLite-backed managed completion with separate actual closing and submitted results")
      val file = _temporary_directory("cncf-closing-result-reopen-").resolve("workflow.sqlite")
      val actionresult = StateMachineOperationResult(StateMachineResultTypeReference("closing.reopen.v1"),
        ContextReference("closing-reopen", "3"))
      var interpreted = 0
      var expected: Option[ContinuationResult[String]] = None
      _with_managed_host_at(file) { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          val request = _seed(store)
          expected = Some(request.result)
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
            interpreted += 1
            ActionExecution.Completed(actionresult): ActionExecution
          }
          val closing = _take(WorkflowClosingProgramPreparation.prepareC(program))
          val participant = _take(_completion(store).prepareClosingHostParticipantC(request, _owner, space,
            Vector.empty, closing, Vector.empty, () => Consequence.unit, _ => Consequence.unit))
          val tx = TransactionContext.create(_context.transactionContext, _context.clock, _context.idGeneration)
          When("the participant prepares, commits and finishes before physical shutdown")
          _take(participant.prepareC(tx))
          val committed = _take(participant.commitC(tx))
          val finished = participant.finishC(tx)
          Then("the native completion is verified and finishing succeeds")
          committed shouldBe a[ManagedCompletionParticipant.Outcome.Verified[?]]
          finished.isSuccess shouldBe true
          Consequence.unit
        })
      }
      val reopened = SqlDataStore.sqlite(file.toString)
      try {
        When("a fresh physical provider loads the retained completion")
        val recovered = _take(_completion(reopened).loadC(_identity)).get
        Then("both results survive and the closing program has run exactly once")
        Some(recovered.result) shouldBe expected
        recovered.closingResult shouldBe Some(actionresult)
        interpreted shouldBe 1
      } finally _take(reopened.closeC())
    }

    "suppress both results and delivery when the live claim changes after joint preparation" in {
      Given("a prepared joint completion borrowed from an actual claimed native source")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          val request = _seed(store)
          val result = StateMachineOperationResult(StateMachineResultTypeReference("closing.stale.v1"), ContextReference("stale", "1"))
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](ActionExecution.Completed(result))
          val closing = _take(WorkflowClosingProgramPreparation.prepareC(program))
          var deliveries = 0
          val participant = _take(_completion(store).prepareClosingHostParticipantC(request, _owner, space,
            Vector.empty, closing, Vector.empty, () => Consequence.unit, _ => { deliveries += 1; Consequence.unit }))
          When("release and re-claim replace the live token before publication")
          _take(_claims(store).releaseC(_identity, request.claimId))
          val replacement = _take(_claims(store).claimC(_identity))
          val before = _stored(store)
          val tx = TransactionContext.create(_context.transactionContext, _context.clock, _context.idGeneration)
          _take(participant.prepareC(tx))
          val committed = _take(participant.commitC(tx))
          val finished = participant.finishC(tx)
          Then("the real stale claim guard withholds both results and delivery")
          replacement.claimId should not be Some(request.claimId)
          committed should not be a[ManagedCompletionParticipant.Outcome.Verified[?]]
          finished.isSuccess shouldBe false
          _stored(store) shouldBe before
          _take(_completion(store).loadC(_identity)) shouldBe None
          deliveries shouldBe 0
          Consequence.unit
        })
      }
    }

    "retain distinct closing results and read passive V1/V2 annotations while rejecting malformed frames" in {
      Given("a semantic completion and a distinct typed closing result; passive annotations are not progression authority")
      val semantic = WorkflowCompletionRecordJsonV1.Record(InstanceRevision(2L), _submitted(_issue(_intent(_initial))))
      When("both declared formats encode and decode their data and the V2 frame receives an unused annotation")
      val legacy = _take(WorkflowCompletionRecordJsonV1.encodeC(semantic, _result_codec))
      val closing = StateMachineOperationResult(StateMachineResultTypeReference("actual.closing.v1"),
        ContextReference("actual-closing-context", "7"))
      val joint = semantic.copy(closingResult = Some(closing))
      val bytes = _take(WorkflowCompletionRecordJsonV1.encodeC(joint, _result_codec))
      val decodedjoint = WorkflowCompletionRecordJsonV1.decodeC(bytes, _result_codec)
      val decodedlegacy = WorkflowCompletionRecordJsonV1.decodeC(legacy, _result_codec)
      val encodedlegacy = WorkflowCompletionRecordJsonV1.encodeC(semantic, _result_codec)
      val json = io.circe.parser.parse(bytes).toOption.get
      val annotated = WorkflowCompletionRecordJsonV1.decodeC(json.mapObject(_.add("extra", Json.True)).noSpaces, _result_codec)
      val malformed = Vector(
        json.mapObject(_.remove("closingResult")),
        json.mapObject(_.add("closingResult", Json.Null)),
        json.mapObject(_.add("closingResult", Json.obj("typeIdentity" -> Json.fromString(""),
          "contextIdentity" -> Json.fromString("actual-closing-context"), "contextRevision" -> Json.fromString("7")))),
        json.mapObject(_.add("semanticCompletion", json))
      ).map(bad => WorkflowCompletionRecordJsonV1.decodeC(bad.noSpaces, _result_codec))
      val missingclosing = WorkflowCompletionRecordJsonV1.encodeC(semantic.copy(closingResult = Some(null)), _result_codec)
      Then("declared meaning survives an annotation while missing, nested-invalid or unconvertible representation fails")
      decodedjoint shouldBe Consequence.success(joint)
      decodedlegacy shouldBe Consequence.success(semantic)
      encodedlegacy shouldBe Consequence.success(legacy)
      annotated shouldBe Consequence.success(joint)
      malformed.foreach(_ shouldBe a[Consequence.Failure[?]])
      missingclosing shouldBe a[Consequence.Failure[?]]
    }

    "commit exact typed completion and an actually prepared closing program through every ordinary event mode" in {
      Vector("empty", "transient", "retained", "new", "mixed").foreach { mode =>
        Given(s"a real claimed WorkOrder, explicitly admitted local intents and ordinary $mode events")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s._with_managed_datastore_lease_c { lease =>
            s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { originalspace =>
              val store = _take(originalspace.dataStore(_instances))
              val request = _seed(store)
              _provision_event_schema(s, store)
              val local = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "completion", "local_guard"))
              val facts = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "completion", "local_facts"))
              val id = _key("closing")
              _take(store.create(local, id, Record.dataAuto("revision" -> 1L, "value" -> "open")))
              val guard = _take(DataStoreConditionalRoot.create(_owner, local, id, "revision",
                Some(org.simplemodeling.model.datatype.EntityRevision.INITIAL), Vector.empty,
                Record.dataAuto("value" -> "closed"), _take(org.simplemodeling.model.datatype.EntityRevision.createC(2L))))
              val base = _take(_event_declaration.policyC(_take(_event_declaration.grantC(_component))))
              val guardbinding = DataStoreConditionalGuardBinding(_owner, local)
              val policy = _take(DataStoreConditionalMultiGuardHostPolicy.create(_owner, _instances, _continuations,
                base.guardBindings :+ guardbinding, base.sideCreateCollections :+ facts,
                base.guardTargets :+ DataStoreConditionalGuardTarget(guardbinding, id)))
              val read = _take(DataStoreObservationPolicy.createC(
                DataStoreObservationTarget(_event_cursor, _event_declaration.cursorEntryId), _event_facts, _event_indexes))
              val space = _take(DataStoreSpace.multiGuardHostViewC(policy, store, lease, Some(read), Some(s.eventStore)))
              val retained = _ordinary_event("retained")
              if (mode == "retained" || mode == "mixed") {
                val selected = _take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(retained)))
                val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _event_declaration, EventCursorRecordV1.initial))
                proposal.events.creates.foreach(c => _take(store.create(c.collection, c.entryId, c.record)))
                _take(store.save(_event_cursor, _event_declaration.cursorEntryId, _take(EventCursorRecordV1.createC(1L, 1L)).toRecord))
              }
              val transient = _ordinary_event("ephemeral", "ephemeral")
              val fresh = _ordinary_event("fresh")
              val retainedincoming = retained.copy(name = "untrusted incoming name", payload = Map("unsupported" -> new Object))
              val input = mode match {
                case "empty" => Vector.empty
                case "transient" => Vector(transient)
                case "retained" => Vector(retainedincoming, transient)
                case "new" => Vector(fresh)
                case "mixed" => Vector(retainedincoming, transient, fresh)
              }
              val guardedintent = _take(WorkflowClosingLocalIntent.guardedC(guard))
              val createintent = _take(WorkflowClosingLocalIntent.createC(EntityVersionedSideEffect.Create(
                facts, id, Record.dataAuto("value" -> "completed"))))
              val actionresult = StateMachineOperationResult(StateMachineResultTypeReference("closing.result.v1"),
                ContextReference("closing-result", "1"))
              val closingprogram: ExecUowM[ActionExecution] = for {
                _ <- ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StageWorkflowClosingLocalIntent(guardedintent)))
                _ <- ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StageWorkflowClosingLocalIntent(createintent)))
              } yield ActionExecution.Completed(actionresult)
              val closing = _take(WorkflowClosingProgramPreparation.prepareC(closingprogram))
              closing.result shouldBe actionresult
              _take(store.load(local, id)).get.getString("value") shouldBe Some("open")
              _take(store.load(facts, id)) shouldBe None
              var delivered = Vector.empty[EventRecord]
              val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
              bus.register(EventSubscription("native-finalized", handler = new EventDispatchHandler {
                def dispatch(event: DomainEvent): Consequence[Unit] = {
                   event match {
                  case FinalizedRecordDomainEvent(record) => delivered :+= record; Consequence.unit
                  case _ => Consequence.stateConflict("Expected finalized native record")
                }
                }
              }))
              val participant = _take(_completion(store).prepareClosingHostParticipantC(request, _owner, space, input, closing,
                Vector.empty, () => Consequence.unit, batch => batch.foldLeft(Consequence.unit) { (acc, record) =>
                  acc.flatMap(_ => bus.dispatchCommittedRecordC(record).map(_ => ()))
                }))
              val uow = new ManagedCompletionUnitOfWork(_context, participant)
              When("the participant commits against the common host route, without separately executing local writes")
              _take(uow.commit())
              Then("typed completion, completed claim and both local post-images are retained before delivery")
              uow.storageOutcome.get match {
                case ManagedCompletionParticipant.Outcome.Verified(value) =>
                  value.retained.result shouldBe request.result
                  value.retained.closingResult shouldBe Some(actionresult)
                case other => fail(s"unexpected local completion outcome: $other")
              }
              delivered.map(_.id) shouldBe input.map(_.id)
              if (mode == "retained" || mode == "mixed") delivered.head.name shouldBe retained.name
              delivered.filter(_.kind == "ephemeral").foreach { event =>
                event.persistent shouldBe false
                event.sequence shouldBe 0L
              }
              delivered.filter(_.kind == "fact").foreach { event =>
                event.persistent shouldBe true
                event.sequence should be > 0L
                val saved = _take(store.load(_event_facts, _key(event.id.value))).get.getString("event_json").get
                EventRecordJsonV2.encodeStorage(event) shouldBe saved
              }
              _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
              _take(_completion(store).loadC(_identity)).get.closingResult shouldBe Some(actionresult)
              _take(store.load(local, id)).get.getString("value") shouldBe Some("closed")
              _take(store.load(facts, id)).get.getString("value") shouldBe Some("completed")
              _take(store.load(_event_facts, _key(transient.id.value))) shouldBe None
              delivered.map(_.id) shouldBe input.map(_.id)
              _take(s.eventStore.query(EventStore.Query())) shouldBe empty
              When("the completed local-intent UnitOfWork receives another commit request")
              val replay = uow.commit()
              Then("the one-shot completion refuses another commit")
              replay.isSuccess shouldBe false
              Consequence.unit
            }
          })
        }
      }
    }

    "reject foreign prepared providers and unenlisted closing effects before binding a participant" in {
      Vector("foreign-provider", "closing-effect").foreach { mode =>
        Given(s"a managed host with $mode in the prepared completion")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            val request = _seed(store)
            val original = _take(_completion(store).prepareCompletionC(request, _owner))
            val prepared = if (mode == "foreign-provider") {
              val other = DataStore.inMemory()
              _take(_completion(other).prepareCompletionC(_seed(other), _owner))
            } else _take(original.withSideEffectsC(Vector(EntityVersionedSideEffect.Save(
              DataStore.CollectionId("unenlisted_closing"), _key("effect"), Record.dataAuto("value" -> "denied")))))
            var actions = 0
            val before = _stored(store)
            When("the host attempts to bind the prepared completion lifecycle")
            prepared.hostParticipantC(space, Vector.empty,
              Vector(() => { actions += 1; Consequence.unit }),
              () => { actions += 1; Consequence.unit },
              _ => { actions += 1; Consequence.unit }).isSuccess shouldBe false
            Then("no participant, native commit, callback or delivery is admitted")
            actions shouldBe 0
            _stored(store) shouldBe before
            _take(_completion(store).loadC(_identity)) shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "deny a participant whose real host lease ended without retry or dispatch" in {
      Given("a prepared participant escaping its admitted host lease")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        var actions = 0
        val tx = TransactionContext.create(_context.transactionContext, _context.clock, _context.idGeneration)
        val participant = _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          val request = _seed(store)
          val prepared = _take(_completion(store).prepareCompletionC(request, _owner))
          prepared.hostParticipantC(space, Vector.empty,
            Vector(() => { actions += 1; Consequence.unit }), () => Consequence.unit,
            _ => { actions += 1; Consequence.unit }).flatMap { bound => bound.prepareC(tx).map(_ => bound) }
        })
        When("the escaped participant attempts native commit after scope release")
        val committed = _take(participant.commitC(tx))
        Then("the expired lease produces an indeterminate native outcome")
        committed.isInstanceOf[ManagedCompletionParticipant.Outcome.Indeterminate] shouldBe true
        When("the escaped participant attempts lifecycle completion")
        val finished = participant.finishC(tx)
        Then("ordinary failure is not misclassified as proven rollback and no current claim is consumed")
        finished.isSuccess shouldBe false
        actions shouldBe 0
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          _take(_completion(store).loadC(_identity)) shouldBe None
          _take(_claims(store).loadStoredC(_identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Claimed
          Consequence.unit
        })
        When("the participant with an ended host lease receives another commit request")
        val replay = participant.commitC(tx)
        Then("the ended lease still refuses native commit")
        replay.isSuccess shouldBe false
      }
    }

    "bind real host storage to one participant and defer the original mixed batch until lifecycle completion" in {
      Given("a real managed claimed WorkOrder with retained, transient and new ordinary events")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val store = _take(space.dataStore(_instances))
          val request = _seed(store)
          _provision_event_schema(s, store)
          val retained = _ordinary_event("retained")
          val selected = _take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(retained)))
          val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _event_declaration, EventCursorRecordV1.initial))
          proposal.events.creates.foreach(c => _take(store.create(c.collection, c.entryId, c.record)))
          _take(store.save(_event_cursor, _event_declaration.cursorEntryId, _take(EventCursorRecordV1.createC(1L, 1L)).toRecord))
          val batch = Vector(retained, _ordinary_event("ephemeral", "ephemeral"), _ordinary_event("fresh"))
          val log = scala.collection.mutable.ArrayBuffer.empty[String]
          val prepared = _take(_completion(store).prepareCompletionC(request, _owner))
          val participant = _take(prepared.hostParticipantC(space, batch,
            Vector(() => { log += "callback"; Consequence.unit }),
            () => { log += "cleanup"; Consequence.unit },
            records => { log ++= records.map(r => "deliver:" + r.id.value); Consequence.unit }))
          val tx = TransactionContext.create(_context.transactionContext, _context.clock, _context.idGeneration)
          _take(participant.prepareC(tx))
          _take(_completion(store).loadC(_identity)) shouldBe None
          log shouldBe empty
          When("the participant consumes its native commit against actual host storage")
          val committed = _take(participant.commitC(tx))
          Then("completion and selected durable facts are stored before any callback or delivery")
          committed match {
            case ManagedCompletionParticipant.Outcome.Verified(value) => value.retained.result shouldBe request.result
            case other => fail(s"unexpected native outcome: $other")
          }
          _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
          _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe 2L
          _take(store.load(_event_facts, _key(batch(1).id.value))) shouldBe None
          log shouldBe empty
          When("frozen callbacks and cleanup finish successfully")
          _take(participant.finishC(tx))
          Then("the host delivery port receives the original batch once, not a durable-only replay")
          log.toVector shouldBe Vector("callback", "cleanup") ++ batch.map(r => "deliver:" + r.id.value)
          When("the finished participant receives another finish request")
          val repeatedfinish = participant.finishC(tx)
          Then("finishing is one-shot")
          repeatedfinish.isSuccess shouldBe false
          When("the finished participant receives another commit request")
          val repeatedcommit = participant.commitC(tx)
          Then("commit cannot repeat after lifecycle completion")
          repeatedcommit.isSuccess shouldBe false
          log.size shouldBe 5
          Consequence.unit
        })
      }
    }

    "preserve joint SQLite completion and suppress actual bus dispatch after callback, cleanup or UoW resource failure" in {
      Vector("callback", "cleanup", "resource").foreach { stage =>
        Given(s"actual managed storage followed by a failing $stage")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            val request = _seed(store)
            _provision_event_schema(s, store)
            var delivered = 0
            var cleaned = 0
            var released = Vector.empty[org.goldenport.cncf.unitofwork.UnitOfWorkTermination]
            val actionresult = StateMachineOperationResult(StateMachineResultTypeReference("closing.lifecycle.v1"),
              ContextReference("lifecycle", "1"))
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(actionresult))
            val closing = _take(WorkflowClosingProgramPreparation.prepareC(program))
            val input = Vector(_ordinary_event("durable-lifecycle"), _ordinary_event("transient-lifecycle", "ephemeral"))
            val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
            bus.register(EventSubscription("lifecycle-failure", handler = new EventDispatchHandler {
              def dispatch(event: DomainEvent): Consequence[Unit] = { delivered += 1; Consequence.unit }
            }))
            val participant = _take(_completion(store).prepareClosingHostParticipantC(request, _owner, space, input, closing,
              Vector(() => if (stage == "callback") Consequence.stateConflict("fixture callback") else Consequence.unit),
              () => { cleaned += 1; if (stage == "cleanup") Consequence.stateConflict("fixture cleanup") else Consequence.unit },
              batch => batch.foldLeft(Consequence.unit)((acc, event) =>
                acc.flatMap(_ => bus.dispatchCommittedRecordC(event).map(_ => ())))))
            val uow = new ManagedCompletionUnitOfWork(_context, participant)
            _take(uow.registerResourceC(new org.goldenport.cncf.unitofwork.UnitOfWorkResource {
              def releaseC(termination: org.goldenport.cncf.unitofwork.UnitOfWorkTermination): Consequence[Unit] = {
                released :+= termination
                if (stage == "resource") Consequence.stateConflict("fixture resource") else Consequence.unit
              }
            }))
            When("the active managed UoW commits and fails during lifecycle completion")
            val committed = uow.commit()
            Then("stored result remains completed and neither failure authorizes delivery or compensation")
            committed.isSuccess shouldBe false
            uow.storageOutcome.get shouldBe a[ManagedCompletionParticipant.Outcome.Verified[?]]
            uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
            _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
            _take(_completion(store).loadC(_identity)).get.closingResult shouldBe Some(actionresult)
            _take(_claims(store).loadStoredC(_identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
            _take(store.load(_event_facts, _key(input.head.id.value))) should not be None
            _take(store.load(_event_facts, _key(input.last.id.value))) shouldBe None
            _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe 1L
            released shouldBe Vector(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
            delivered shouldBe 0
            cleaned shouldBe 1
            When("the UnitOfWork retries after the retained lifecycle failure")
            val replay = uow.commit()
            Then("retry cannot repeat cleanup, resource release or delivery")
            replay.isSuccess shouldBe false
            cleaned shouldBe 1
            released.size shouldBe 1
            delivered shouldBe 0
            _take(s.eventStore.query(EventStore.Query())) shouldBe empty
            Consequence.unit
          })
        }
      }
    }

    "retain proven stale-guard rejection separately from postcommit projection uncertainty in the native binding" in {
      Vector("stale-root", "projection").foreach { mode =>
        Given(s"a real managed prepared completion with $mode failure")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances)).asInstanceOf[DataStore & EntityConditionalTransitionDataStore]
            val request = _seed(store)
            _provision_event_schema(s, store)
            val original = _take(_completion(store).prepareCompletionC(request, _owner))
            val prepared = if (mode == "projection") WorkflowPreparedDataStoreTransition.create(store, original.plan,
              _ => Consequence.stateConflict[WorkflowCompletionDataStorePersistence.PersistedCompletion[String]]("fixture projection"))
              else {
                val old = _take(store.load(_instances, _key("instance-1"))).get
                val field = original.plan.root.revisionField
                _take(store.save(_instances, _key("instance-1"), Record(old.fields.filterNot(_.key == field) ++
                  Record.dataAuto(field -> 9L).fields)))
                original
              }
            var callbacks = 0
            var deliveries = 0
            var cleanups = 0
            val event = _ordinary_event("native-failure")
            val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
            bus.register(EventSubscription("native-failure", handler = new EventDispatchHandler {
              def dispatch(event: DomainEvent): Consequence[Unit] = { deliveries += 1; Consequence.unit }
            }))
            val participant = _take(prepared.hostParticipantC(space, Vector(event),
              Vector(() => { callbacks += 1; Consequence.unit }),
              () => { cleanups += 1; Consequence.unit },
              batch => batch.foldLeft(Consequence.unit)((acc, record) =>
                acc.flatMap(_ => bus.dispatchCommittedRecordC(record).map(_ => ())))))
            val uow = new ManagedCompletionUnitOfWork(_context, participant)
            When("native commit returns actual guard or committed-projection evidence")
            val committed = uow.commit()
            Then("only proven NotMatched is rejected; projection failure retains uncertainty and actual completed storage")
            committed.isSuccess shouldBe false
            val outcome = uow.storageOutcome.get
            if (mode == "stale-root") {
              outcome.isInstanceOf[ManagedCompletionParticipant.Outcome.Rejected] shouldBe true
              uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Aborted)
              _take(_completion(store).loadC(_identity)) shouldBe None
              _take(_claims(store).loadStoredC(_identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Claimed
              _take(store.load(_event_facts, _key(event.id.value))) shouldBe None
              _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe 0L
            } else {
              outcome.isInstanceOf[ManagedCompletionParticipant.Outcome.Indeterminate] shouldBe true
              uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Indeterminate)
              _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
              _take(_claims(store).loadStoredC(_identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
              _take(store.load(_event_facts, _key(event.id.value))) should not be None
              _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe 1L
            }
            callbacks shouldBe 0
            deliveries shouldBe 0
            cleanups shouldBe 1
            When("the UnitOfWork retries after its native failure outcome")
            val replay = uow.commit()
            Then("retry cannot dispatch the committed or rejected batch")
            replay.isSuccess shouldBe false
            deliveries shouldBe 0
            _take(s.eventStore.query(EventStore.Query())) shouldBe empty
            Consequence.unit
          })
        }
      }
    }

    "retain exact semantic result, history and claim through every host-selected storage route" in {
      Vector("empty", "transient", "retained", "new", "mixed").foreach { mode =>
        Given(s"a real managed claimed WorkOrder and ordinary $mode input")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          var expectedresult: Option[ContinuationResult[String]] = None
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            val request = _seed(store)
            _provision_event_schema(s, store)
            expectedresult = Some(request.result)
            val retained = _ordinary_event("retained")
            if (mode == "retained" || mode == "mixed") {
              val selected = _take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(retained)))
              val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _event_declaration, EventCursorRecordV1.initial))
              proposal.events.creates.foreach(c => _take(store.create(c.collection, c.entryId, c.record)))
              _take(store.save(_event_cursor, _event_declaration.cursorEntryId, _take(EventCursorRecordV1.createC(1L, 1L)).toRecord))
            }
            val ephemeral = _ordinary_event("ephemeral", "ephemeral").copy(payload = Map("unsupported" -> new Object))
            val fresh = _ordinary_event("fresh")
            val input = mode match {
              case "empty" => Vector.empty
              case "transient" => Vector(ephemeral)
              case "retained" => Vector(retained, ephemeral)
              case "new" => Vector(fresh)
              case "mixed" => Vector(retained, ephemeral, fresh)
            }
            When("the typed completion adapter delegates ordinary events to the common host entry")
            val result = _take(_completion(store).completeHostEventsC(request, _owner, space, input))
            Then("existing typed admission projects exact stored result, completed claim and adjacent history")
            result.retained.result shouldBe request.result
            result.instance.history.last shouldBe request.entry
            result.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
            _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
            input.foreach(_.sequence shouldBe 0L)
            Consequence.unit
          })
          Then("a fresh admitted scope recovers the exact completion and only selected durable facts")
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            _take(_completion(store).loadC(_identity)).get.result shouldBe expectedresult.get
            val newdurable = mode == "new" || mode == "mixed"
            val hadretained = mode == "retained" || mode == "mixed"
            val expectedsequence = (if (newdurable) 1L else 0L) + (if (hadretained) 1L else 0L)
            _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe expectedsequence
            _take(store.load(_event_facts, _key(_ordinary_event("fresh").id.value))).nonEmpty shouldBe newdurable
            _take(store.load(_event_facts, _key(_ordinary_event("retained").id.value))).nonEmpty shouldBe hadretained
            _take(store.load(_event_facts, _key(_ordinary_event("ephemeral", "ephemeral").id.value))) shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "deny a prepared completion borrowed from another provider without changing the managed claim" in {
      Given("equivalent logical WorkOrders independently seeded in memory and the real managed host")
      _with_managed_host { s =>
        _take(s.initializeEventCursorC(_environment(s)))
        _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
          val managed = _take(space.dataStore(_instances))
          _seed(managed)
          val before = _stored(managed)
          val foreign = DataStore.inMemory()
          val foreignrequest = _seed(foreign)
          val prepared = _take(_completion(foreign).prepareCompletionC(foreignrequest, _owner))
          When("the foreign prepared projection is submitted to this host")
          prepared.commitHostEventsC(space, Vector(_ordinary_event("fresh"))).toOption shouldBe None
          Then("provider identity mismatch leaves both claims and guarded histories unchanged")
          _stored(managed) shouldBe before
          _take(_completion(managed).loadC(_identity)) shouldBe None
          _take(_completion(foreign).loadC(_identity)) shouldBe None
          _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe 0L
          Consequence.unit
        })
      }
    }

    "preserve current records on re-claim, stale root or injected local effects without publishing a new event" in {
      Vector("reclaim", "stale-root", "local-effect").foreach { invalid =>
        Given(s"a managed completion prepared before $invalid")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances))
            val request = _seed(store)
            _provision_event_schema(s, store)
            val original = _take(_completion(store).prepareCompletionC(request, _owner))
            val effects = DataStore.CollectionId("managed_completion_injected_effects")
            val prepared = invalid match {
              case "reclaim" =>
                _take(_claims(store).releaseC(_identity, request.claimId))
                _take(_claims(store).claimC(_identity)).claimId should not be Some(request.claimId)
                original
              case "stale-root" =>
                val old = _take(store.load(_instances, _key("instance-1"))).get
                val revisionfield = original.plan.root.revisionField
                _take(store.save(_instances, _key("instance-1"), Record(old.fields.filterNot(_.key == revisionfield) ++
                  Record.dataAuto(revisionfield -> 9L).fields)))
                original
              case "local-effect" => _take(original.withSideEffectsC(Vector(
                EntityVersionedSideEffect.Save(effects, _key("injected"), Record.dataAuto("value" -> "denied")))))
            }
            val before = _stored(store)
            When("the prepared completion attempts the common host path with one new ordinary event")
            prepared.commitHostEventsC(space, Vector(_ordinary_event("fresh"))).toOption shouldBe None
            Then("guard rejection or effect admission changes neither current records nor cursor/event state")
            _stored(store) shouldBe before
            _take(_completion(store).loadC(_identity)) shouldBe None
            _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe 0L
            _take(store.load(_event_facts, _key(_ordinary_event("fresh").id.value))) shouldBe None
            if (invalid == "local-effect") _take(store.load(effects, _key("injected"))) shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "return indeterminate on a failed or throwing projection after native commit without replay" in {
      Vector(false, true).foreach { throws =>
        Given("an admitted typed completion plan with a test-only faulty trusted projection")
        _with_managed_host { s =>
          _take(s.initializeEventCursorC(_environment(s)))
          _take(s.withWorkflowVerifiedMultiGuardSpaceC(_component, _environment(s)) { space =>
            val store = _take(space.dataStore(_instances)).asInstanceOf[DataStore & EntityConditionalTransitionDataStore]
            val request = _seed(store)
            val original = _take(_completion(store).prepareCompletionC(request, _owner))
            var projections = 0
            val faulty = WorkflowPreparedDataStoreTransition.create[String](store, original.plan, _ => {
              projections += 1
              if (throws) throw new IllegalStateException("fixture projection")
              else Consequence.stateConflict("fixture projection")
            })
            When("native storage verifies before the trusted projection fails")
            _reason(faulty.commitHostEventsC(space, Vector.empty), "transaction-indeterminate")
            Then("the actual completion is retained, projection runs once and no compensation occurs")
            projections shouldBe 1
            _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
            _take(_claims(store).loadStoredC(_identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
            _take(s.loadEventCursorC(_environment(s))).lastSequence shouldBe 0L
            Consequence.unit
          })
        }
      }
    }
  }

  "Phase 88 guarded retained completion" should {
    "commit both revision guards despite changed legacy policy columns and recover under current policy" in {
      Given("an actual SQLite claimed WorkOrder with no generated configuration copies")
      val db = _path
      val first = SqlDataStore.sqlite(db.toString)
      val request = _seed(first)
      val original = _stored(first)
      val prepared = _take(_completion(first).prepareCompletionC(request, _owner))
      When("both legacy policy columns change after preparation without consuming either indexed revision")
      val committed = try {
        _take(first.update(_instances, _key("instance-1"), Record.dataAuto("configurationPayload" -> "old root policy")))
        _take(first.update(_continuations, _key(_identity.value), Record.dataAuto("configurationPayload" -> "different old continuation policy")))
        _take(prepared.commitC())
      } finally _take(first.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        val changed = _config.copy(retentionPolicy = RetentionPolicy("changed"))
        val adapter = _take(WorkflowCompletionDataStorePersistence.bindC(reopened, changed, _component,
          _completion_operation, Vector(_binding), _instances, _continuations, _codec, _result_codec))
        val recovered = _take(adapter.loadC(_identity)).get
        val retained = _stored(reopened)
        val replay = adapter.completeC(request, _owner)
        Then("native revisions own completion, old material stays inert and recovery does not replay consumed work")
        original._1.fields.map(_.key) should not contain "configurationPayload"
        original._2.fields.map(_.key) should not contain "configurationPayload"
        committed.instance.lifecycle shouldBe Lifecycle.Completed
        committed.instance.revision shouldBe request.entry.revision
        committed.instance.history shouldBe request.current.history :+ request.entry
        committed.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
        recovered.instanceRevision shouldBe request.entry.revision
        recovered.result shouldBe request.result
        retained._1.getString("configurationPayload") shouldBe Some("old root policy")
        retained._2.getString("configurationPayload") shouldBe Some("different old continuation policy")
        replay.toOption shouldBe None
      } finally _take(reopened.closeC())
    }

    "prepare completion without completing the claim or exposing a retained result" in {
      Given("one claimed issued WorkOrder and a typed result")
      val store = DataStore.inMemory()
      val request = _seed(store)
      val before = _stored(store)
      When("the host prepares completion and adds an explicit local closing record")
      val prepared = _take(_completion(store).prepareCompletionC(request, _owner))
      val effects = DataStore.CollectionId("completion_closing_records_v1")
      val composed = _take(prepared.withSideEffectsC(Vector(EntityVersionedSideEffect.Save(
        effects, _key("closed"), Record.dataAuto("meaning" -> "exact closing record")))))
      Then("preparation publishes neither history, claim completion, result nor local record")
      _stored(store) shouldBe before
      _take(_completion(store).loadC(_identity)) shouldBe None
      _take(store.load(effects, _key("closed"))) shouldBe None
      When("the host commits the same guarded plan once")
      val result = _take(composed.commitC())
      Then("exact result, completing history, claim and explicit local record are retained together")
      result.retained.result shouldBe request.result
      _take(_completion(store).loadC(_identity)).get.result shouldBe request.result
      _take(store.load(effects, _key("closed"))).get.getString("meaning") shouldBe Some("exact closing record")
      When("the already consumed prepared completion attempts to commit again")
      val replay = prepared.commitC()
      Then("the consumed guarded plan refuses replay")
      replay.toOption shouldBe None
    }

    "reject a delayed prepared completion after release and re-claim without a local side effect" in {
      Given("one prepared completion retaining the selected Claimed storage revision and exact claim token")
      val store = DataStore.inMemory()
      val request = _seed(store)
      val selected = _take(_claims(store).loadStoredC(_identity)).get
      val effects = DataStore.CollectionId("completion_delayed_records_v1")
      val prepared = _take(_take(_completion(store).prepareCompletionC(request, _owner)).withSideEffectsC(
        Vector(EntityVersionedSideEffect.Save(effects, _key("late"), Record.dataAuto("value" -> "not published")))))
      When("the current claim is released and claimed under a new token before commit")
      _take(_claims(store).releaseC(_identity, request.claimId))
      val released = _take(_claims(store).loadStoredC(_identity)).get
      val reclaimed = _take(_claims(store).claimC(_identity))
      val current = _take(_claims(store).loadStoredC(_identity)).get
      val before = _stored(store)
      Then("the status returns to Claimed while release and reclaim each advance the storage revision")
      selected._1.status shouldBe ContinuationRuntimePersistence.Status.Claimed
      released._1.status shouldBe ContinuationRuntimePersistence.Status.Available
      released._2 shouldBe _take(selected._2.nextC)
      current._1.status shouldBe selected._1.status
      current._2 shouldBe _take(released._2.nextC)
      reclaimed.claimId should not be Some(request.claimId)
      When("the delayed completion attempts to consume its earlier selected storage revision")
      val result = prepared.commitC()
      Then("the restored Claimed state cannot admit the stale revision or publish a local closing record")
      result.toOption shouldBe None
      _stored(store) shouldBe before
      _take(_completion(store).loadC(_identity)) shouldBe None
      _take(store.load(effects, _key("late"))) shouldBe None
    }

    "roll back a prepared completion and explicit local effect at the publication checkpoint" in {
      Given("a borrowed provider that rejects publication after staging all record changes")
      var armed = false
      val store = new DataStore.InMemoryDataStore(org.goldenport.cncf.unitofwork.CommitRecorder.noop) {
        override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] = {
          if (armed && stage == DataStoreConditionalTransitionCheckpoint.BeforePublish)
            Consequence.operationInvalid("injected prepared completion rejection")
          else Consequence.unit
        }
      }
      val request = _seed(store)
      val before = _stored(store)
      val effects = DataStore.CollectionId("completion_rollback_records_v1")
      val prepared = _take(_take(_completion(store).prepareCompletionC(request, _owner)).withSideEffectsC(
        Vector(EntityVersionedSideEffect.Save(effects, _key("closing"), Record.dataAuto("value" -> "rolled back")))))
      When("the host commits with publication rejection armed")
      armed = true
      val result = prepared.commitC()
      Then("history, claim, typed result and local effect stay unchanged")
      result.toOption shouldBe None
      _stored(store) shouldBe before
      _take(_completion(store).loadC(_identity)) shouldBe None
      _take(store.load(effects, _key("closing"))) shouldBe None
    }

    "commit host history, claimed Continuation and typed execution evidence together and recover after SQLite reopen" in {
      Given("a claimed native SQLite issue with no completion record")
      val db = _path
      val first = SqlDataStore.sqlite(db.toString)
      val request = _seed(first)
      val before = _stored(first)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField)
      try {
        val prior = _take(_completion(first).loadC(_identity))
        When("the actual owner completes the current claimed issue")
        val result = _take(_completion(first).completeC(request, _owner))
        Then("one native commit retains the result, claim and adjacent history without rewriting the issue")
        prior shouldBe None
        result.instance.lifecycle shouldBe Lifecycle.Completed
        result.instance.history shouldBe request.current.history :+ request.entry
        result.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
        result.continuation.claimId shouldBe Some(request.claimId)
        result.retained.result shouldBe request.result
        _stored(first)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField) shouldBe before
      } finally _take(first.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        When("the closed database is reopened and completion and claim are attempted again")
        val recovered = _take(_completion(reopened).loadC(_identity))
        val replay = _completion(reopened).completeC(request, _owner)
        val claimed = _claims(reopened).claimC(_identity)
        Then("retained facts survive while consumed completion and claim refuse replay")
        recovered shouldBe Some(
          WorkflowCompletionRecordJsonV1.Record(InstanceRevision(2L), request.result))
        replay.toOption shouldBe None
        claimed.toOption shouldBe None
        _take(_root(reopened).load(_config, request.current.identity)).get.revision shouldBe InstanceRevision(2L)
      } finally _take(reopened.closeC())
    }

    "recover an earlier completion after the host has advanced to later history" in {
      Given("an owning claimed issue whose completion permits later active history")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        val active = request.entry.copy(lifecycle = Lifecycle.Active,
          currentProgression = Some(ProgressionReference("review.Accepted")))
        When("completion is saved and the host appends later history")
        _take(_completion(store).completeC(request.copy(entry = active), _owner))
        val later = request.entry.copy(sequence = HistorySequence(3L), revision = InstanceRevision(3L))
        _take(_root(store).append(_config, request.current.identity, InstanceRevision(2L), later))
        val recovered = _take(_completion(store).loadC(_identity)).get
        val replay = _completion(store).completeC(request, _owner)
        Then("the earlier actual completion remains readable and cannot be completed again")
        recovered.instanceRevision shouldBe InstanceRevision(2L)
        recovered.result shouldBe request.result
        replay.toOption shouldBe None
      } finally _take(store.closeC())
    }

    "reject native facts, evidence and target/type errors before accepting unused dispatch diagnostics once" in {
      Given("one actual claimed issue and incompatible typed result boundaries")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        val result = request.result
        val variants = Vector(result.copy(completionFacts = Vector.empty),
          result.copy(evidence = result.evidence.copy(references = Vector.empty)),
          result.copy(handle = result.handle.copy(instanceIdentity = InstanceIdentity("foreign"))),
          result.copy(handle = result.handle.copy(componentIdentity = ComponentId("org.goldenport.cncf.test.ForeignCompletion"))),
          result.copy(runId = StateMachineRunIdentity("foreign")),
          result.copy(continuationId = ContinuationIdentity("foreign")),
          result.copy(expectedRevision = StateMachineRevision("stale")),
          result.copy(contextSnapshot = result.contextSnapshot.copy(modelRevision = Some("foreign"))),
          result.copy(result = TypedValue("foreign.result", "result")),
          result.copy(evidence = null))
        val before = _stored(store)
        When("each actual incompatible boundary is submitted")
        val outcomes = variants.map(value => _completion(store).completeC(request.copy(result = value), _owner))
        val retained = _stored(store)
        Then("none consumes the current revision or claim")
        outcomes.foreach(_.toOption shouldBe None)
        retained shouldBe before
        When("only the unused copied dispatch review requirement changes on the still-live claim")
        val diagnostic = result.copy(evidence = result.evidence.copy(skillDispatch = result.evidence.skillDispatch.map(value =>
          value.copy(requestedRequirement = value.requestedRequirement.copy(reviewRequired = false)))))
        val accepted = _completion(store).completeC(request.copy(result = diagnostic), _owner)
        val committed = _completion(store).loadC(_identity)
        val replay = _completion(store).completeC(request.copy(result = diagnostic), _owner)
        Then("native admission completes once, retains the diagnostic data and replay cannot write again")
        accepted.isSuccess shouldBe true
        _take(committed).map(_.result) shouldBe Some(diagnostic)
        replay shouldBe a[Consequence.Failure[?]]
      } finally _take(store.closeC())
    }

    "require the current host suspension, expected instance revision and exact nonblank live claim token" in {
      Given("an actual stored suspension and one live claim token")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        val before = _stored(store)
        val variants = Vector(request.copy(claimId = ""), request.copy(claimId = null), request.copy(claimId = "foreign"),
          request.copy(expectedRevision = initialRevision),
          request.copy(entry = request.entry.copy(suspension = null)),
          request.copy(entry = request.entry.copy(suspension = request.current.suspension)))
        When("invalid live claim, revision or suspension facts are submitted")
        val outcomes = variants.map(value => _completion(store).completeC(value, _owner))
        val missing = _completion(store).completeC(null, _owner)
        val retained = _stored(store)
        Then("invalid actual boundaries leave the stored issue unchanged")
        outcomes.foreach(_.toOption shouldBe None)
        missing.toOption shouldBe None
        retained shouldBe before
        When("release and re-claim replace the live token")
        _take(_claims(store).releaseC(_identity, request.claimId))
        val released = _completion(store).completeC(request, _owner)
        val fresh = _take(_claims(store).claimC(_identity)).claimId.get
        val stale = _completion(store).completeC(request, _owner)
        val completed = _take(_completion(store).completeC(request.copy(claimId = fresh), _owner))
        Then("only the actual replacement token can consume completion")
        released.toOption shouldBe None
        stale.toOption shouldBe None
        completed.continuation.claimId shouldBe Some(fresh)
      } finally _take(store.closeC())
    }

    "complete from stored current despite differing diagnostic causation and workspace copies" in {
      Given("a claimed SQLite issue with an actual owning suspension")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        val diagnostic = request.copy(current = request.current.copy(causation = CausationReference("diagnostic")),
          result = request.result.copy(contextSnapshot = request.result.contextSnapshot.copy(
            workspaceRevision = Some("diagnostic workspace"))))
        When("the result targets the real stored state with only diagnostic copies differing")
        val completed = _take(_completion(store).completeC(diagnostic, _owner))
        val retained = _take(_completion(store).loadC(_identity)).get
        val stale = _completion(store).completeC(diagnostic, _owner)
        Then("actual history and typed result are consumed once without a whole-record or workspace proof")
        completed.instance.history shouldBe request.current.history :+ request.entry
        completed.instance.causation shouldBe request.entry.causation
        retained.result.contextSnapshot.workspaceRevision shouldBe Some("diagnostic workspace")
        stale.toOption shouldBe None
      } finally _take(store.closeC())
    }

    "reject a release and reclaim between admission and commit without consuming the instance revision" in {
      Given("a SQLite provider that replaces the live claim between admission and conditional commit")
      val db = _path
      val source = new SQLiteDataSource(_sqlite_config)
      source.setUrl(s"jdbc:sqlite:$db")
      var armed = false
      var fresh = ""
      var old = ""
      val store = new SqlDataStore(SqliteDialectDriver, source) {
        override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
            Consequence[DataStoreConditionalTransitionResult] = {
          if (armed && plan.successor.isInstanceOf[DataStoreConditionalSuccessor.Update]) {
            armed = false
            _take(_claims(this).releaseC(_identity, old))
            fresh = _take(_claims(this).claimC(_identity)).claimId.get
          }
          super.conditionalTransition(plan)
        }
      }
      try {
        val request = _seed(store)
        val beforeroot = _stored(store)._1
        old = request.claimId
        armed = true
        When("the admitted completion reaches the real stale-token commit boundary")
        val rejected = _completion(store).completeC(request, _owner)
        Then("the old token cannot consume the actual instance revision")
        rejected.toOption shouldBe None
        _stored(store)._1 shouldBe beforeroot
        _take(_claims(store).loadC(_identity)).get.claimId shouldBe Some(fresh)
        _take(_completion(store).loadC(_identity)) shouldBe None
        When("the host submits the actual replacement claim token")
        val completed = _take(_completion(store).completeC(request.copy(claimId = fresh), _owner))
        Then("the new live token completes the retained native source")
        completed.continuation.claimId shouldBe Some(fresh)
      } finally _take(store.closeC())
    }

    "roll back result, Completed state and history at all pre-commit checkpoints" in {
      val stages = Vector(Some(DataStoreConditionalTransitionCheckpoint.GuardAdmitted),
        Some(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.RootPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.BeforePublish), None)
      stages.foreach { stage =>
        Given(s"a claimed SQLite source with a refusal injected at $stage")
        val db = _path
        val source = new SQLiteDataSource(_sqlite_config)
        source.setUrl(s"jdbc:sqlite:$db")
        var armed = false
        val store = new SqlDataStore(SqliteDialectDriver, source) {
          override protected def conditional_transition_checkpoint(value: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] = {
            if (armed && stage.contains(value)) Consequence.operationInvalid("injected completion checkpoint") else Consequence.unit
          }
          override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] = {
            if (armed && stage.isEmpty) DataStoreConditionalTransitionFailure.transactionFailure("injected completion commit")
            else super.conditional_transition_commit(connection)
          }
        }
        val request = _seed(store)
        val before = _stored(store)
        try {
          armed = true
          When("completion reaches the selected pre-commit checkpoint")
          val rejected = _completion(store).completeC(request, _owner)
          Then("the checkpoint rejects publication")
          rejected.toOption shouldBe None
        } finally _take(store.closeC())
        val reopened = SqlDataStore.sqlite(db.toString)
        try {
          When("a fresh physical provider loads the database after refusal")
          val actual = _stored(reopened)
          val completion = _take(_completion(reopened).loadC(_identity))
          Then("actual source and history remain unchanged with no retained completion")
          actual shouldBe before
          completion shouldBe None
        } finally _take(reopened.closeC())
      }
    }

    "report malformed applied readback as indeterminate and recover the committed result without replay" in {
      Given("a native provider that corrupts only the applied completion readback")
      val db = _path
      val source = new SQLiteDataSource(_sqlite_config)
      source.setUrl(s"jdbc:sqlite:$db")
      val store = new SqlDataStore(SqliteDialectDriver, source) {
        override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
            Consequence[DataStoreConditionalTransitionResult] =
          super.conditionalTransition(plan).map {
            case DataStoreConditionalTransitionResult.Transitioned(a, b)
                if plan.successor.isInstanceOf[DataStoreConditionalSuccessor.Update] =>
              DataStoreConditionalTransitionResult.Transitioned(a, Record(
                b.fields.filterNot(_.key == WorkflowCompletionDataStorePersistence.payloadField)) ++
                Record.dataAuto(WorkflowCompletionDataStorePersistence.payloadField -> "broken"))
            case other => other
          }
      }
      val request = _seed(store)
      try {
        When("native commit succeeds but the actual applied readback is malformed")
        val attempted = _completion(store).completeC(request, _owner)
        Then("the result is transaction-indeterminate, not a safe retry")
        _reason(attempted, "transaction-indeterminate")
      }
      finally _take(store.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        When("the actual committed facts are loaded through a fresh provider and replay is attempted")
        val recovered = _take(_completion(reopened).loadC(_identity)).get
        val replay = _completion(reopened).completeC(request, _owner)
        val current = _take(_root(reopened).load(_config, request.current.identity)).get
        Then("committed completion and history survive and the source cannot be replayed")
        recovered.result shouldBe request.result
        replay.toOption shouldBe None
        current.history.size shouldBe 2
      } finally _take(reopened.closeC())
    }

    "retain a single completion winner across independent SQLite providers and reject replay" in {
      Given("two independent physical SQLite providers targeting the same expected native revision")
      val db = _path
      val left = SqlDataStore.sqlite(db.toString)
      val right = SqlDataStore.sqlite(db.toString)
      val pool = Executors.newFixedThreadPool(2)
      val gate = new CountDownLatch(1)
      try {
        val request = _seed(left)
        val adapters = Vector(_completion(left), _completion(right))
        When("both providers attempt completion at the shared gate")
        val tasks = adapters.map(adapter => pool.submit(new java.util.concurrent.Callable[Boolean] {
          def call(): Boolean = {
            gate.await()
            adapter.completeC(request, _owner).toOption.nonEmpty
          }
        }))
        gate.countDown()
        val winners = tasks.map(_.get(20L, TimeUnit.SECONDS)).count(_ == true)
        Then("only one real conditional commit wins and its retained result is readable")
        winners shouldBe 1
        _take(_completion(left).loadC(_identity)).get.result shouldBe request.result
        When("the other provider resubmits the completed source")
        val replay = _completion(right).completeC(request, _owner)
        Then("replay refuses without adding history")
        replay.toOption shouldBe None
        _take(_root(right).load(_config, request.current.identity)).get.history.size shouldBe 2
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(20L, TimeUnit.SECONDS)
        _take(left.closeC())
        _take(right.closeC())
      }
    }

    "fail on malformed or foreign retained input without a canonical-body certificate" in {
      Given("retained completion payloads with malformed native schema or foreign actual identity")
      val baseline = _seed_bytes()
      Vector("broken",
        WorkflowInstanceDataStorePersistence.storageText(baseline.replace("cncf.workflow-completion-record.v1", "unsupported")),
        WorkflowInstanceDataStorePersistence.storageText(baseline.replace("instance-1", "foreign"))).foreach { payload =>
        val store = SqlDataStore.sqlite(_path.toString)
        try {
          val request = _seed(store)
          _take(_completion(store).completeC(request, _owner))
          _take(store.update(_continuations, _key(_identity.value), Record.dataAuto(WorkflowCompletionDataStorePersistence.payloadField -> payload)))
          val before = _stored(store)
          When("the native completion reader consumes the malformed or foreign payload")
          val rejected = _completion(store).loadC(_identity)
          Then("actual invalid facts reject without rewriting stored data")
          rejected.toOption shouldBe None
          _stored(store) shouldBe before
        } finally _take(store.closeC())
      }
    }

    "read permitted old formatting and diagnostic snapshots without rewriting completion or renewing a consumed claim" in {
      Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R2; Example: E3; a physically committed SQLite completion")
      val db = _path
      val store = SqlDataStore.sqlite(db.toString)
      val request = _seed(store)
      val saved = _take(_completion(store).completeC(request, _owner)).retained
      val original = _take(WorkflowCompletionRecordJsonV1.encodeC(saved, _result_codec))
      val diagnostic = io.circe.parser.parse(original).toOption.get.hcursor.downField("result").downField("contextSnapshot")
        .downField("workspaceRevision").withFocus(_ => Json.fromString("retained-workspace-description")).top.get
      _take(store.update(_continuations, _key(_identity.value), Record.dataAuto(WorkflowCompletionDataStorePersistence.payloadField ->
        WorkflowInstanceDataStorePersistence.storageText(" \n" + diagnostic.spaces2))))
      val before = _stored(store)
      _take(store.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        When("the completion is decoded after physical reopen, without a re-encoded-body comparison")
        val retained = _take(_completion(reopened).loadC(_identity)).get
        Then("the real result/closing revision remain usable without row rewrite or another completion")
        retained.instanceRevision shouldBe saved.instanceRevision
        retained.result.result shouldBe saved.result.result
        retained.result.contextSnapshot.workspaceRevision shouldBe Some("retained-workspace-description")
        _stored(reopened) shouldBe before
        When("the reopened host receives the already completed request again")
        val replay = _completion(reopened).completeC(request, _owner)
        Then("the consumed claim refuses another completion")
        replay.isSuccess shouldBe false
      } finally _take(reopened.closeC())
    }

    "read completed stored facts without a history certificate, but reject missing result or unissued execution" in {
      Given("docs/spec/workflow-completion-applicability.md R7: a claimed issued Continuation with a stored typed result but no completion")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        val bytes = _take(WorkflowCompletionRecordJsonV1.encodeC(
          WorkflowCompletionRecordJsonV1.Record(request.entry.revision, request.result), _result_codec))
        _take(store.update(_continuations, _key(_identity.value), Record.dataAuto(
          WorkflowCompletionDataStorePersistence.payloadField -> WorkflowInstanceDataStorePersistence.storageText(bytes))))
        When("the result is queried before its native Continuation becomes Completed")
        val premature = _completion(store).loadC(_identity)
        Then("non-Completed native state refuses the stored result")
        premature.toOption shouldBe None
        When("the native claim completes independently and the result is queried without adjacent history proof")
        _take(_claims(store).completeC(_identity, request.claimId))
        val retained = _take(_completion(store).loadC(_identity)).get
        val observed = _take(_root(store).load(_config, request.current.identity)).get
        val replay = _completion(store).completeC(request, _owner)
        Then("the stored typed result is readable, but query does not advance the instance or authorize replay")
        retained.result shouldBe request.result
        observed.revision shouldBe InstanceRevision(1L)
        // Query grants no replay and does not certify a past joint transaction.
        replay.toOption shouldBe None
      } finally _take(store.closeC())
      Given("a legacy native completion without a retained typed result")
      val legacy = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(legacy)
        _take(_claims(legacy).completeC(_identity, request.claimId))
        When("Completion query reads the legacy native state")
        val missing = _completion(legacy).loadC(_identity)
        Then("missing retained result cannot be fabricated or retrofitted")
        missing.toOption shouldBe None
      } finally _take(legacy.closeC())
      Given("a native suspension created without atomically issued work")
      val unissued = SqlDataStore.sqlite(_path.toString)
      try {
        val current = _initial
        val request = _intent(current)
        _take(_root(unissued).create(_config, current))
        val suspended = _take(_root(unissued).suspendC(request, _owner, _continuations)).instance
        val token = _take(_claims(unissued).claimC(_identity)).claimId.get
        val entry = request.entry.copy(sequence = HistorySequence(2L), revision = InstanceRevision(2L),
          lifecycle = Lifecycle.Completed, currentProgression = None, suspension = None)
        When("execution attempts completion using a result for work that was never issued")
        val rejected = _completion(unissued).completeC(WorkflowCompletionDataStorePersistence.Intent(suspended,
          suspended.revision, entry, token, _submitted(_issue(request))), _owner)
        val observed = _take(_root(unissued).load(_config, current.identity)).get
        Then("the actual writer refuses unissued execution and leaves the instance at its suspension")
        rejected.toOption shouldBe None
        observed.revision shouldBe InstanceRevision(1L)
      } finally _take(unissued.closeC())
    }

    "reject unsupported capabilities and throwing result codecs before publication" in {
      Given("an actual claimed issue and an application writer that throws")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        val before = _stored(store)
        val throwing = new PayloadCodec[String] {
          val typeIdentity = _result_codec.typeIdentity
          def encode(value: String): Json = throw new IllegalArgumentException("injected")
          def decode(value: Json) = _result_codec.decode(value)
        }
        When("the throwing writer or a provider without required capability is used")
        val rejected = _completion(store, throwing).completeC(request, _owner)
        val unsupported = WorkflowCompletionDataStorePersistence.bindC(DataStore.noop(), _config, _component, _completion_operation,
          Vector(_binding), _instances, _continuations, _codec, _result_codec)
        val retained = _stored(store)
        Then("serialization failure or missing capability publishes no completion")
        rejected.toOption shouldBe None
        unsupported.toOption shouldBe None
        retained shouldBe before
      } finally _take(store.closeC())
    }

    "consume trusted codec output once without producer self-certification" in {
      Given("an actual claimed issue and a trusted typed writer selecting its storage representation")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        var writes = 0
        var reads = 0
        val transformed = new PayloadCodec[String] {
          val typeIdentity = _result_codec.typeIdentity
          def encode(value: String): Json = { writes += 1; Json.fromString("serialized representation") }
          def decode(value: Json) = { reads += 1; _result_codec.decode(value) }
        }
        val adapter = _completion(store, transformed)
        When("completion serializes once, consumes committed readback and later queries retained meaning")
        val completed = _take(adapter.completeC(request, _owner))
        val retained = _take(adapter.loadC(_identity)).get
        val writecount = writes
        val readcount = reads
        Then("the writer owns its representation without an extra round-trip or payload-copy comparison")
        completed.retained.result.result.value shouldBe "serialized representation"
        retained.result.result.value shouldBe "serialized representation"
        completed.instance.lifecycle shouldBe Lifecycle.Completed
        writecount shouldBe 1
        readcount shouldBe 2
      } finally _take(store.closeC())
    }

    "preserve the existing optional dispatch contract without manufacturing Skill evidence" in {
      Given("an otherwise applicable issued result with its optional Skill dispatch evidence absent")
      val store = SqlDataStore.sqlite(_path.toString)
      try {
        val request = _seed(store)
        val withoutdispatch = request.result.copy(evidence = request.result.evidence.copy(skillDispatch = None))
        When("the existing native completion consumes the result")
        val completed = _take(_completion(store).completeC(request.copy(result = withoutdispatch), _owner))
        Then("the result completes without inventing optional Skill evidence")
        completed.retained.result.evidence.skillDispatch shouldBe None
        _take(_completion(store).loadC(_identity)).get.result.evidence.skillDispatch shouldBe None
      } finally _take(store.closeC())
    }

    "complete issued input beyond forty thousand characters under revision guards and recover it intact" in {
      Vector("memory", "sqlite").foreach { provider =>
        Given(s"docs/spec/entity-conflict-and-conditional-transition.md R7 with a large issued request on $provider")
        val db = _path
        val store: DataStore = if (provider == "memory") DataStore.inMemory() else SqlDataStore.sqlite(db.toString)
        val current = _initial
        val input = "日本語\"quoted\"\\line\n" * 4000
        val base = _intent(current)
        val source = base.continuation.continuation
        val request = base.copy(continuation = base.continuation.copy(
          continuation = source.copy(context = source.context.copy(summary = input))))
        val original = _issue(request)
        val order = original.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
        val large = original.copy(current = order.copy(request = order.request.copy(
          input = Some(TypedValue(_codec.typeIdentity, input)))))
        val result = _submitted(large)
        val entry = request.entry.copy(sequence = HistorySequence(2L), revision = InstanceRevision(2L),
          lifecycle = Lifecycle.Completed, currentProgression = None, suspension = None)
        try {
          _take(_root(store).create(_config, current))
          val suspended = _take(_issued(store).suspendC(request, large, _owner)).instance
          val token = _take(_claims(store).claimC(_identity)).claimId.get
          val before = _stored(store)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField)
          val selected = _take(_claims(store).loadStoredC(_identity)).get
          When("the selected claim and instance revisions atomically complete the large request")
          val completed = _take(_completion(store).completeC(WorkflowCompletionDataStorePersistence.Intent(suspended,
            suspended.revision, entry, token, result), _owner))
          Then("the result, completed status and next revisions are retained with the entire unchanged issued body")
          input.length should be >= 40000
          completed.instance.revision shouldBe entry.revision
          completed.instance.lifecycle shouldBe Lifecycle.Completed
          completed.instance.history shouldBe suspended.history :+ entry
          completed.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
          completed.continuation.claimId shouldBe Some(token)
          completed.continuation.continuation.context.summary shouldBe input
          completed.retained shouldBe WorkflowCompletionRecordJsonV1.Record(entry.revision, result)
          _take(_claims(store).loadStoredC(_identity)).get._2 shouldBe _take(selected._2.nextC)
          _stored(store)._1.getInt(WorkflowInstanceDataStorePersistence.revisionField) shouldBe Some(3)
          _stored(store)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField) shouldBe before
          _take(_issued(store).loadC(_identity)) shouldBe Some(large)
          _take(_completion(store).loadC(_identity)) shouldBe Some(completed.retained)
        } finally store match {
          case sql: SqlDataStore => _take(sql.closeC())
          case _ => ()
        }
        if (provider == "sqlite") {
          When("the SQLite database is physically reopened after closing the provider")
          val reopened = SqlDataStore.sqlite(db.toString)
          try {
            val recoveredissue = _take(_issued(reopened).loadC(_identity))
            Then("the full issued input recovers intact from the physically reopened provider")
            recoveredissue shouldBe Some(large)
            When("the completed result is loaded through the physically reopened provider")
            val recoveredcompletion = _take(_completion(reopened).loadC(_identity))
            Then("the completed result and revisions recover intact")
            recoveredcompletion shouldBe Some(
              WorkflowCompletionRecordJsonV1.Record(entry.revision, result))
            _take(_root(reopened).load(_config, current.identity)).get.revision shouldBe entry.revision
            _take(_claims(reopened).loadStoredC(_identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
            _take(_claims(reopened).loadStoredC(_identity)).get._1.continuation.context.summary shouldBe input
            _take(_claims(reopened).loadStoredC(_identity)).get._2.value shouldBe 3L
            _stored(reopened)._1.getInt(WorkflowInstanceDataStorePersistence.revisionField) shouldBe Some(3)
          } finally _take(reopened.closeC())
        }
      }
    }
  }

  private def _seed_bytes(): String = {
    val work = _issue(_intent(_initial))
    _take(WorkflowCompletionRecordJsonV1.encodeC(
      WorkflowCompletionRecordJsonV1.Record(InstanceRevision(2L), _submitted(work)), _result_codec))
  }
}
