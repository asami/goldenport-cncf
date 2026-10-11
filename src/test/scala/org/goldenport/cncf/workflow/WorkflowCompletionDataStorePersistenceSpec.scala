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
 * @version Oct.  9, 2026
 */
final class WorkflowCompletionDataStorePersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private val instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_instance_v1"))
  private val continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_continuation_v1"))
  private val component = ComponentId("org.goldenport.cncf.test.CompletionWork")
  private val completionOperation = StateMachineOperationIdentity("workflow", "submitReview")
  private val owner = take(DataStoreComponentOwner.create("org.goldenport.cncf.test.CompletionWork"))
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("store-1")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("store-1")))
  private val codec = new PayloadCodec[String] {
    val typeIdentity = "review.input.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("review input must be text")
  }
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private def binding: DefinitionBinding = {
    val producer = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
    take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
      resource("/workflow/candidate-admission-workflow-abi.json").trim, producer,
      "modeler/candidate-admission-workflow.cml"))))
  }
  private def initial: InstanceRecord =
    InstanceRecord(InstanceIdentity("instance-1"), binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("correlation-1"), CausationReference("start-1"),
      None, None, None, Vector.empty)
  private def intent(current: InstanceRecord, identity: String = "continuation-1"):
      WorkflowInstanceAtomicTransitionV1.SuspensionIntent = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"),
      Some(StateMachineInputTypeReference(codec.typeIdentity)), Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector(ContextReference("accepted", "1"))), EvidenceContract("review.evidence", Vector(ContextReference("review-proof", "1"))), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("run-1"),
      ContinuationIdentity(identity), StateMachineRevision("machine-rev-1"), required,
      ContextBundle("review context", Vector.empty, Vector.empty, ContextSnapshot(current.definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(identity), InstanceRevision(1L),
        ContextSnapshotReference(current.definition.workflowRevision.value), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("resume-1"))))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      config, current, initialRevision, entry, continuation)
  }
  private def issue(request: WorkflowInstanceAtomicTransitionV1.SuspensionIntent): WorkflowInteraction[String, Nothing] = {
    val c = request.continuation.continuation
    WorkflowInteraction(take(WorkflowHandle.fromRecordC(component, request.current)),
      WorkflowContinuation.WorkOrder(ContinuationRequest(c.runId, c.continuationId, c.expectedRevision,
        c.requiredOperation.identity, c.requiredOperation.operation, Some(TypedValue(codec.typeIdentity, "exact review input")),
        c.requiredOperation.resultType, c.context, c.requiredOperation.metadata.completionContract,
        c.requiredOperation.metadata.evidenceContract, completionOperation),
        ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"),
          ReasoningLevel.Deep, true), MinimalPresentation("Review", "Awaiting semantic result")))
  }
  private def root(store: DataStore) = take(WorkflowInstanceDataStorePersistence.bindC(store, config, Vector(binding), instances))
  private def claims(store: DataStore) = take(WorkflowContinuationDataStorePersistence.bindC(store, config, continuations))
  private def issued(store: DataStore, inputCodec: PayloadCodec[String] = codec) =
    take(WorkflowIssuedWorkDataStorePersistence.bindC(store, config, component, completionOperation, Vector(binding), instances, continuations, inputCodec))
  private def path: Path = Files.createTempDirectory("cncf-phase88-issued-").resolve("workflow.sqlite")
  private val identity = ContinuationIdentity("continuation-1")
  private def key(id: String) = DataStore.StringEntryId(id)
  private def sqliteConfig: SQLiteConfig = {
    val result = new SQLiteConfig()
    result.setBusyTimeout(10000)
    result.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    result
  }


  private val resultCodec = new PayloadCodec[String] {
    val typeIdentity = "review.result.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("review result must be text")
  }
  private def completion(store: DataStore, c: PayloadCodec[String] = resultCodec) =
    take(WorkflowCompletionDataStorePersistence.bindC(store, config, component, completionOperation,
      Vector(binding), instances, continuations, codec, c))
  private def submitted(work: WorkflowInteraction[String, Nothing]): ContinuationResult[String] = {
    val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
    val request = order.request
    ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision,
      request.context.snapshot, TypedValue(resultCodec.typeIdentity, "accepted semantic result"),
      ContextReference("result-1", "1"), request.completion.requiredFacts,
      ExecutionEvidence(request.evidence.requiredEvidence, Some("worker-1"), Some("model-1"),
        Some(SkillDispatchEvidence(order.requirement, "semantic-review", "mapping-v1"))))
  }
  private final case class AvailableCompletion(current: InstanceRecord, entry: HistoryEntry,
      result: ContinuationResult[String])
  private def seedAvailable(store: DataStore): AvailableCompletion = {
    val current = initial
    val request = intent(current)
    val work = issue(request)
    take(root(store).create(config, current))
    val suspended = take(issued(store).suspendC(request, work, owner)).instance
    val entry = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Completed,
      None, current.correlation, CausationReference("complete-1"), None, None, None)
    AvailableCompletion(suspended, entry, submitted(work))
  }
  private def seed(store: DataStore): WorkflowCompletionDataStorePersistence.Intent[String] = {
    val available = seedAvailable(store)
    val token = take(claims(store).claimC(identity)).claimId.get
    WorkflowCompletionDataStorePersistence.Intent(available.current, available.current.revision,
      available.entry, token, available.result)
  }
  private def stored(store: DataStore) = (
    take(store.load(instances, key("instance-1"))).get,
    take(store.load(continuations, key(identity.value))).get)
  private def reason[A](value: Consequence[A], expected: String): Unit = value match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.cause.descriptor.facets.collect {
        case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
      } should contain (expected)
    case _ => fail("expected structured failure")
  }

  private val eventCursor = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_cursor_v1"))
  private val eventFacts = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_events_v1"))
  private val eventIndexes = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "completion_indexes_v1"))
  private val eventDeclaration = take(EventStorePersistenceDeclaration.createC("events", eventCursor, eventFacts, eventIndexes,
    Vector(EventStorePersistenceDeclaration.WorkflowGrant(component, "workflow", instances, continuations)), key("stream")))
  private val eventPolicy = take(EventPersistencePolicy.createC("completion-policy-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist))))
  private def ordinaryEvent(token: String, kind: String = "fact") = {
    val time = Instant.parse("2026-09-30T00:00:00Z")
    EventRecord(EventId("test", "completion", Some(time), Some(token)), kind, kind,
      Map("value" -> token), Map.empty, time, true, EventRecord.Status.Stored, EventLane.Transactional)
  }
  private def environment(s: Subsystem) = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(s.configuration))
  private def provisionEventSchema(s: Subsystem, store: DataStore): Unit = {
    // Native absence requires an existing canonical event schema, not a missing table.
    val selected = take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(ordinaryEvent("schema"))))
    val proposal = take(WorkflowCursorEventProposal.prepareV2C(selected, eventDeclaration, EventCursorRecordV1.initial))
    proposal.events.creates.foreach { c =>
      take(store.create(c.collection, c.entryId, c.record))
      take(store.delete(c.collection, c.entryId))
    }
  }
  private def withManagedHost(f: Subsystem => Unit): Unit = {
    val file = Files.createTempDirectory("cncf-managed-completion-").resolve("workflow.sqlite")
    withManagedHostAt(file)(f)
  }
  private def withManagedHostAt(file: Path)(f: Subsystem => Unit): Unit = {
    val configured = ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(file.toString))), ConfigurationTrace.empty)
    val scope = ScopeContext(ScopeKind.Subsystem, "completion-host", None, context.observability)
    val host = take(SubsystemEventStoreBootstrap.createC(eventPolicy, eventDeclaration, scope, configured))
    try f(host.subsystem)
    finally take(host.shutdownC())
  }

  "Phase 88 typed completion through the managed event host" should {
    "execute a declared Decision completion Service and retain committed answers despite response projection failure" in {
      Vector(false, true).foreach { rejectResponse =>
        Given("a native managed Decision and its separately declared public completion Operation; human evidence remains a fixture")
        Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R3; Example: E4; different Provided provenance and an irrelevant earlier projected history body")
        withManagedHost { s =>
          org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture.admit(s)
          take(s.initializeEventCursorC(environment(s)))
          val issueIntent = intent(initial)
          val work = issue(issueIntent)
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
          val decision = WorkflowInteraction[String, Nothing](work.handle,
            WorkflowContinuation.Decision(order.request, MinimalPresentation("Fixture choice", "No effect grant")))
          val diagnostic = submitted(work).evidence
          val answer = submitted(work).copy(evidence = diagnostic.copy(workerIdentity = Some(""),
            modelIdentity = Some(" "), skillDispatch = diagnostic.skillDispatch.map(value =>
              value.copy(requestedRequirement = value.requestedRequirement.copy(reviewRequired = false)))))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            space.dataStore(instances).flatMap(store => root(store).create(config, issueIntent.current))
          })
          val questions = take(WorkflowManagedIssuedDecision.bindC(s, component, config, completionOperation,
            Vector(binding), instances, continuations, codec))
          val suspended = take(questions.suspendC(issueIntent, decision)).instance
          val instanceBinding = take(WorkflowInstancePersistenceBinding.bindC(component, s, environment(s),
            WorkflowInstancePersistenceBinding.Declaration(config, Vector(binding), instances)))
          val closingType = StateMachineResultTypeReference("public.decision.closing.v1")
          val closingResult = StateMachineOperationResult(closingType, ContextReference("decision-closing", "1"))
          val completionBinding = take(WorkflowManagedDecisionCompletion.bindC(s, component, config,
            completionOperation, Vector(binding), instances, continuations, codec, resultCodec,
            closingType, (_, _) => Consequence.success(Vector.empty)))
          val apiOperation = GeneratedProvidedApiAbi.Operation(completionOperation.service, completionOperation.operation,
            Some(resultCodec.typeIdentity), Some("decision.response.v1"), GeneratedProvidedApiAbi.SourceIdentity(Some(1)))
          val api = GeneratedProvidedApiAbi.Definition(GeneratedProvidedApiAbi.schemaVersion,
            "different-diagnostic-producer", suspended.definition.workflowIdentity.value,
            suspended.definition.workflowRevision.value,
            GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(Some(1)),
              GeneratedProvidedApiAbi.SourceIdentity(Some(1))), Vector(apiOperation.copy(source = GeneratedProvidedApiAbi.SourceIdentity(Some(999)))))
          var selections = 0
          var interpretations = 0
          val closing = new WorkflowCompletionServiceOperation.ClosingProgram[String] {
            def selectC(result: ContinuationResult[String], current: InstanceRecord):
                Consequence[WorkflowCompletionServiceOperation.SelectedClosing] = {
              selections += 1
              current.appendC(current.revision, HistoryEntry(HistorySequence(2L), InstanceRevision(2L),
                Lifecycle.Completed, None, current.correlation, CausationReference("decision-complete"),
                None, None, None)).map { next =>
                val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
                  interpretations += 1
                  ActionExecution.Completed(closingResult): ActionExecution
                }
                val projected = next.copy(history = next.history.updated(0,
                  next.history.head.copy(causation = CausationReference("unselected-projection-history"))))
                WorkflowCompletionServiceOperation.SelectedClosing(program, Some(projected))
              }
            }
          }
          val publicCodec = new WorkflowCompletionServiceOperation.Codec[String] {
            val resultTypeIdentity = resultCodec.typeIdentity
            def decodeC(raw: Request): Consequence[ContinuationResult[String]] = raw.arguments match {
              case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, resultCodec)
              case _ => Consequence.stateConflict("missing Decision result JSON")
            }
            def encodeC(value: ContinuationRuntime.Resume): Consequence[OperationResponse] =
              Consequence.success(OperationResponse.Scalar("DECISION_COMPLETED"))
            override def encodeAfterCommitC(value: ContinuationRuntime.Resume, result: ContinuationResult[String],
                completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
              value.result shouldBe closingResult
              completed.get.revision shouldBe InstanceRevision(2L)
              completed.get.history.head.causation shouldBe suspended.history.head.causation
              take(completionBinding.loadRetainedC(identity)).get.completion.result shouldBe answer
              if (rejectResponse) Consequence.stateConflict("fixture Decision response failure") else encodeC(value)
            }
          }
          WorkflowDecisionCompletionServiceOperation.bindC(api, apiOperation.copy(inputType = Some("wrong")),
            spec.RequestDefinition(), spec.ResponseDefinition.void, publicCodec, questions, instanceBinding,
            closing, completionBinding).isSuccess shouldBe false
          Given("explicit test-only Decision descriptions, which provide no Human consent or generated producer evidence")
          val descriptions = DescriptiveAttributes.Builder()
            .summary("Submit the issued Decision answer")
            .description("Use the existing managed Decision completion; descriptive metadata supplies no permission.")
            .build()
          val requestDefinition = spec.RequestDefinition()
          When("the declared Decision completion is bound with its original omitted metadata")
          val defaultOperation = take(WorkflowDecisionCompletionServiceOperation.bindC(api, apiOperation,
            spec.RequestDefinition(), spec.ResponseDefinition.void, publicCodec, questions, instanceBinding,
            closing, completionBinding))
          Then("legacy callers retain empty descriptive metadata")
          defaultOperation.specification.content.descriptiveAttributes shouldBe DescriptiveAttributes.empty
          When("the same managed binding receives only descriptive metadata")
          val operation = take(WorkflowDecisionCompletionServiceOperation.bindC(api, apiOperation,
            requestDefinition, spec.ResponseDefinition.void, publicCodec, questions, instanceBinding,
            closing, completionBinding, descriptiveAttributes = descriptions))
          val invalidMetadata = WorkflowDecisionCompletionServiceOperation.bindC(api, apiOperation,
            requestDefinition, spec.ResponseDefinition.void, publicCodec, questions, instanceBinding,
            closing, completionBinding, descriptiveAttributes = null)
          Then("the description is retained without changing the canonical name, transport or managed effects")
          operation.name shouldBe apiOperation.name
          operation.specification.content.descriptiveAttributes shouldBe descriptions
          operation.specification.content.nameAttributes shouldBe BaseContent.simple(apiOperation.name).nameAttributes
          operation.specification.request should be theSameInstanceAs requestDefinition
          operation.specification.response should be theSameInstanceAs spec.ResponseDefinition.void
          invalidMetadata shouldBe a[Consequence.Failure[_]]
          selections shouldBe 0
          interpretations shouldBe 0
          val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
            name = completionOperation.service, operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))))
          s.add(TestComponentFactory.create(component.name, protocol, subsystem = s))
          def raw(result: ContinuationResult[String]) = Request.of(component = component.name,
            service = completionOperation.service, operation = completionOperation.operation,
            arguments = List(Argument("arg1", take(WorkflowResultJsonV1.encodeC(result, resultCodec)))))
          When("missing, stale or foreign-owner answers enter the actual Service before any permitted completion")
          s.executeOperationResponse(raw(answer).copy(arguments = Nil)).isSuccess shouldBe false
          Vector(answer.copy(expectedRevision = StateMachineRevision("foreign")),
            answer.copy(handle = answer.handle.copy(componentIdentity = ComponentId("org.goldenport.cncf.test.Foreign")))).foreach { invalid =>
            s.executeOperationResponse(raw(invalid)).isSuccess shouldBe false
          }
          Then("actual native mismatches do not reach closing selection or interpretation")
          selections shouldBe 0
          interpretations shouldBe 0
          When("the applicable answer carries copied dispatch diagnostics and response projection may reject after commit")
          val response = s.executeOperationResponse(raw(answer))
          if (rejectResponse) response.isSuccess shouldBe false
          else take(response) shouldBe OperationResponse.Scalar("DECISION_COMPLETED")
          Then("the managed completion is retained exactly once and retry never reinterprets")
          interpretations shouldBe 1
          take(completionBinding.loadRetainedC(identity)).get.completion.result shouldBe answer
          take(completionBinding.loadRetainedC(identity)).get.completion.closingResult shouldBe Some(closingResult)
          s.executeOperationResponse(raw(answer)).isSuccess shouldBe false
          interpretations shouldBe 1
          s.systemNode.activeLeaseCount shouldBe 0
          s.systemNode.managedDataStoreCount shouldBe 1
        }
      }
    }

    "complete only the host-bound Decision and recover its exact retained answer after physical reopen" in {
      Given("a native Decision published through the captured shared-store host; evidence is a fixture, not human proof")
      val file = Files.createTempDirectory("cncf-managed-decision-completion-").resolve("workflow.sqlite")
      val issueIntent = intent(initial)
      val work = issue(issueIntent)
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      val decision = WorkflowInteraction[String, Nothing](work.handle,
        WorkflowContinuation.Decision(order.request, MinimalPresentation("Fixture choice", "No command grant")))
      val result = submitted(work)
      val closingType = StateMachineResultTypeReference("decision.closing.v1")
      val closingResult = StateMachineOperationResult(closingType, ContextReference("decision-closing", "1"))
      var interpretations = 0
      def bound(s: Subsystem) = take(WorkflowManagedDecisionCompletion.bindC(s, component, config,
        completionOperation, Vector(binding), instances, continuations, codec, resultCodec,
        closingType, (_, _) => Consequence.success(Vector.empty)))
      withManagedHostAt(file) { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          space.dataStore(instances).flatMap(store => root(store).create(config, issueIntent.current))
        })
        val questions = take(WorkflowManagedIssuedDecision.bindC(s, component, config, completionOperation,
          Vector(binding), instances, continuations, codec))
        val suspended = take(questions.suspendC(issueIntent, decision)).instance
        val entry = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Completed,
          None, suspended.correlation, CausationReference("decision-complete"), None, None, None)
        val next = take(suspended.appendC(suspended.revision, entry))
        val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
          interpretations += 1
          ActionExecution.Completed(closingResult): ActionExecution
        }
        val selected = WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
        val completion = bound(s)
        When("stale or incomplete answers, a foreign owner or the WorkOrder completion route are offered before a valid result")
        Vector(result.copy(expectedRevision = StateMachineRevision("foreign")),
          result.copy(contextSnapshot = ContextSnapshot("foreign")),
          result.copy(completionFacts = Vector.empty), result.copy(evidence = ExecutionEvidence(Vector.empty)),
          result.copy(result = TypedValue("foreign", "answer"))).foreach { invalid =>
          completion.completeC(component, invalid, suspended, selected, context) shouldBe a[Consequence.Failure[?]]
        }
        completion.completeC(ComponentId("org.goldenport.cncf.test.Foreign"), result,
          suspended, selected, context) shouldBe a[Consequence.Failure[?]]
        val workCompletion = take(WorkflowManagedCompletion.bindC(s, component, config, completionOperation,
          Vector(binding), instances, continuations, codec, resultCodec, closingType,
          (_, _) => Consequence.success(Vector.empty)))
        workCompletion.completeC(component, result, suspended, selected, context) shouldBe a[Consequence.Failure[?]]
        WorkflowProtocolV1.admitResultC(decision, result) shouldBe a[Consequence.Failure[?]]
        val malformed = decision.copy(current = WorkflowContinuation.Decision(
          order.request.copy(resultType = Some(null)), order.presentation))
        WorkflowProtocolV1.admitDecisionResultC(malformed, result) shouldBe a[Consequence.Failure[?]]
        Then("no closing program ran or answer/history was retained")
        interpretations shouldBe 0
        take(completion.loadRetainedC(identity)) shouldBe None
        val unchanged = take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          space.dataStore(instances).flatMap(store => root(store).load(config, initial.identity))
        }).get
        take(WorkflowInstanceRecordJsonV1.encodeC(unchanged)) shouldBe
          take(WorkflowInstanceRecordJsonV1.encodeC(suspended))
        When("the applicable Decision result enters its managed binding with copied dispatch diagnostics, not Human consent")
        val completed = take(completion.completeC(component, result, suspended, selected, context))
        Then("claim completion, closing result, answer and adjacent history are retained together")
        take(WorkflowInstanceRecordJsonV1.encodeC(completed.instance)) shouldBe
          take(WorkflowInstanceRecordJsonV1.encodeC(next))
        completed.resume.result shouldBe closingResult
        val retained = take(completion.loadRetainedC(identity)).get.completion
        retained.result shouldBe result
        retained.closingResult shouldBe Some(closingResult)
        retained.instanceRevision shouldBe next.revision
        completion.completeC(component, result, suspended, selected, context) shouldBe a[Consequence.Failure[?]]
        interpretations shouldBe 1
        workCompletion.loadRetainedC(identity) shouldBe a[Consequence.Failure[?]]
        s.systemNode.managedDataStoreCount shouldBe 1
        s.systemNode.activeLeaseCount shouldBe 0
      }
      When("a fresh managed host reopens the same physical database")
      withManagedHostAt(file) { s =>
        Then("query recovers the exact answer/closing result without claiming or interpreting again")
        val retained = take(bound(s).loadRetainedC(identity)).get.completion
        retained.result shouldBe result
        retained.closingResult shouldBe Some(closingResult)
        interpretations shouldBe 1
        s.systemNode.managedDataStoreCount shouldBe 1
        s.systemNode.activeLeaseCount shouldBe 0
      }
    }

    "release its owned Decision claim after a pre-native closing failure and admit one later valid completion" in {
      Given("a managed pending Decision and a closing program with a wrong result contract")
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        val issueIntent = intent(initial)
        val work = issue(issueIntent)
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
        val decision = WorkflowInteraction[String, Nothing](work.handle,
          WorkflowContinuation.Decision(order.request, order.presentation))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          space.dataStore(instances).flatMap(store => root(store).create(config, issueIntent.current))
        })
        val questions = take(WorkflowManagedIssuedDecision.bindC(s, component, config, completionOperation,
          Vector(binding), instances, continuations, codec))
        val suspended = take(questions.suspendC(issueIntent, decision)).instance
        val result = submitted(work).copy(evidence = ExecutionEvidence(order.request.evidence.requiredEvidence))
        val closingType = StateMachineResultTypeReference("decision.closing.v1")
        val valid = StateMachineOperationResult(closingType, ContextReference("decision-closing", "1"))
        val next = take(suspended.appendC(suspended.revision, HistoryEntry(HistorySequence(2L),
          InstanceRevision(2L), Lifecycle.Completed, None, suspended.correlation,
          CausationReference("decision-complete"), None, None, None)))
        val completion = take(WorkflowManagedDecisionCompletion.bindC(s, component, config, completionOperation,
          Vector(binding), instances, continuations, codec, resultCodec, closingType,
          (_, _) => Consequence.success(Vector.empty)))
        def selected(actual: StateMachineOperationResult) = WorkflowCompletionServiceOperation.SelectedClosing(
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(actual)), Some(next))
        When("the actually prepared closing result has a different type")
        completion.completeC(component, result, suspended,
          selected(valid.copy(typeReference = StateMachineResultTypeReference("wrong"))), context) shouldBe a[Consequence.Failure[?]]
        Then("no completion exists and the unclaimed question remains Available")
        take(completion.loadRetainedC(identity)) shouldBe None
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          space.dataStore(continuations).flatMap(store => claims(store).loadC(identity))
        }).get.status shouldBe ContinuationRuntimePersistence.Status.Available
        When("the correct closing program completes the still-current question")
        val completed = take(completion.completeC(component, result, suspended, selected(valid), context))
        take(WorkflowInstanceRecordJsonV1.encodeC(completed.instance)) shouldBe
          take(WorkflowInstanceRecordJsonV1.encodeC(next))
        Then("one adjacent completion is stored and every managed lease is returned")
        take(completion.loadRetainedC(identity)).get.completion.result shouldBe result
        s.systemNode.activeLeaseCount shouldBe 0
        s.systemNode.managedDataStoreCount shouldBe 1
      }
    }

    "execute a declared public completion Operation through host-owned joint storage without legacy resume or separate append" in {
      Vector(false, true).foreach { rejectResponse =>
        Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R3; Example: E4; a declared WorkOrder Service with different Provided provenance and irrelevant projected history")
        withManagedHost { s =>
          org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture.admit(s)
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            provisionEventSchema(s, store)
            val request = seedAvailable(store)
            val closingType = StateMachineResultTypeReference("public.actual.closing.v1")
            val closingResult = StateMachineOperationResult(closingType, ContextReference("public-closing", "1"))
            var interpreted = 0
            var deliveries = 0
            var legacyCalls = 0
            val readOnly = new WorkflowInstancePersistence {
              def create(c: Configuration, value: InstanceRecord): Consequence[InstanceRecord] = {
                legacyCalls += 1; Consequence.stateConflict("unexpected separate create")
              }
              def load(c: Configuration, id: InstanceIdentity): Consequence[Option[InstanceRecord]] = root(store).load(c, id)
              def append(c: Configuration, id: InstanceIdentity, revision: InstanceRevision,
                  entry: HistoryEntry): Consequence[InstanceRecord] = {
                legacyCalls += 1; Consequence.stateConflict("unexpected separate append")
              }
            }
            val apiOperation = GeneratedProvidedApiAbi.Operation(completionOperation.service, completionOperation.operation,
              Some(resultCodec.typeIdentity), Some("completion.response.v1"), GeneratedProvidedApiAbi.SourceIdentity(Some(1)))
            val api = GeneratedProvidedApiAbi.Definition(GeneratedProvidedApiAbi.schemaVersion,
              "different-diagnostic-producer", request.current.definition.workflowIdentity.value,
              request.current.definition.workflowRevision.value,
              GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(Some(1)),
                GeneratedProvidedApiAbi.SourceIdentity(Some(1))), Vector(apiOperation.copy(source = GeneratedProvidedApiAbi.SourceIdentity(Some(999)))))
            val closing = new WorkflowCompletionServiceOperation.ClosingProgram[String] {
              def selectC(result: ContinuationResult[String], current: InstanceRecord):
                  Consequence[WorkflowCompletionServiceOperation.SelectedClosing] =
                current.appendC(current.revision, request.entry).map { next =>
                  val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
                    interpreted += 1
                    ActionExecution.Completed(closingResult): ActionExecution
                  }
                  val projected = next.copy(history = next.history.updated(0,
                    next.history.head.copy(causation = CausationReference("unselected-projection-history"))))
                  WorkflowCompletionServiceOperation.SelectedClosing(program, Some(projected))
                }
            }
            val publicCodec = new WorkflowCompletionServiceOperation.Codec[String] {
              val resultTypeIdentity = resultCodec.typeIdentity
              def decodeC(raw: Request): Consequence[ContinuationResult[String]] = raw.arguments match {
                case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, resultCodec)
                case _ => Consequence.stateConflict("missing completion JSON")
              }
              def encodeC(value: ContinuationRuntime.Resume): Consequence[OperationResponse] =
                Consequence.success(OperationResponse.Scalar("COMPLETED"))
              override def encodeAfterCommitC(value: ContinuationRuntime.Resume, result: ContinuationResult[String],
                  completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
                value.result shouldBe closingResult
                value.continuation.continuationId shouldBe identity
                completed.get.history shouldBe request.current.history :+ request.entry
                take(completion(store).loadC(identity)).get.result shouldBe request.result
                if (rejectResponse) Consequence.stateConflict("fixture response projection") else encodeC(value)
              }
            }
            val managed = take(WorkflowManagedCompletion.bindC(s, component, config, completionOperation, Vector(binding),
              instances, continuations, codec, resultCodec, closingType,
              (_, _) => Consequence.success(Vector(ordinaryEvent("public-managed")))))
            val operation = take(WorkflowCompletionServiceOperation.bindC(api, apiOperation, spec.RequestDefinition(),
              spec.ResponseDefinition.void, publicCodec, ContinuationRuntime.empty, issued(store), readOnly, config,
              () => { legacyCalls += 1; throw new IllegalStateException("unexpected legacy UoW factory") },
              Some(closing), Some(managed)))
            val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
              name = completionOperation.service, operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))))
            val publicComponent = TestComponentFactory.create(component.name, protocol, subsystem = s)
            s.add(publicComponent)
            s.eventBus.register(EventSubscription("public-managed", handler = new EventDispatchHandler {
              def dispatch(event: DomainEvent): Consequence[Unit] = {
                deliveries += 1
                take(completion(store).loadC(identity)).get.closingResult shouldBe Some(closingResult)
                Consequence.unit
              }
            }))
            val raw = Request.of(component = component.name, service = completionOperation.service,
              operation = completionOperation.operation,
              arguments = List(Argument("arg1", take(WorkflowResultJsonV1.encodeC(request.result, resultCodec)))))
            s.executeOperationResponse(raw.copy(arguments = Nil)).isSuccess shouldBe false
            interpreted shouldBe 0
            take(claims(store).loadC(identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Available
            val response = s.executeOperationResponse(raw)
            if (rejectResponse) response.isSuccess shouldBe false
            else take(response) shouldBe OperationResponse.Scalar("COMPLETED")
            interpreted shouldBe 1
            deliveries shouldBe 1
            legacyCalls shouldBe 0
            take(claims(store).loadC(identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Completed
            take(completion(store).loadC(identity)).get.closingResult shouldBe Some(closingResult)
            s.executeOperationResponse(raw).isSuccess shouldBe false
            interpreted shouldBe 1
            deliveries shouldBe 1
            legacyCalls shouldBe 0
            Consequence.unit
          })
        }
      }
    }

    "complete Available directly without claims and retain actual and semantic results in one native commit" in {
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          provisionEventSchema(s, store)
          val request = seedAvailable(store)
          val beforeRevision = take(claims(store).loadStoredC(identity)).get._2
          val closingType = StateMachineResultTypeReference("host.closing.v1")
          val actionResult = StateMachineOperationResult(closingType, ContextReference("actual-closing", "1"))
          var interpretations = 0
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
            interpretations += 1
            val during = take(claims(store).loadStoredC(identity)).get
            during._1.status shouldBe ContinuationRuntimePersistence.Status.Available
            during._1.claimId shouldBe None
            during._2 shouldBe beforeRevision
            ActionExecution.Completed(actionResult): ActionExecution
          }
          val input = Vector(ordinaryEvent("owned-native"), ordinaryEvent("owned-transient", "ephemeral"))
          var delivered = Vector.empty[EventRecord]
          val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
          bus.register(EventSubscription("owned-native", handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              take(completion(store).loadC(identity)).get.closingResult shouldBe Some(actionResult)
              take(claims(store).loadC(identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Completed
              event match {
                case FinalizedRecordDomainEvent(record) => delivered :+= record; Consequence.unit
                case _ => Consequence.stateConflict("expected finalized record")
              }
            }
          }))
          def execute() = completion(store).executeSelectedClosingHostC(request.current, request.entry,
            request.result, program, closingType, owner, space, input,
            batch => batch.foldLeft(Consequence.unit)((acc, event) =>
              acc.flatMap(_ => bus.dispatchCommittedRecordC(event).map(_ => ()))))
          val retained = take(execute())
          retained.retained.result shouldBe request.result
          retained.retained.closingResult shouldBe Some(actionResult)
          retained.instance.history shouldBe request.current.history :+ request.entry
          retained.continuation.claimId shouldBe None
          take(claims(store).loadStoredC(identity)).get._2 shouldBe take(beforeRevision.nextC)
          delivered.map(_.id) shouldBe input.map(_.id)
          interpretations shouldBe 1
          execute().isSuccess shouldBe false
          interpretations shouldBe 1
          delivered.size shouldBe 2
          Consequence.unit
        })
      }
    }

    "reject a stored Claimed source before closing instead of adapting or releasing it" in {
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          val request = seed(store) // Explicit legacy state is deliberately incompatible.
          val before = stored(store)
          var interpretations = 0
          var deliveries = 0
          val closingType = StateMachineResultTypeReference("host.single-user.v1")
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
            interpretations += 1
            ActionExecution.Completed(StateMachineOperationResult(closingType,
              ContextReference("closing", "1"))): ActionExecution
          }
          completion(store).executeSelectedClosingHostC(request.current, request.entry,
            request.result, program, closingType, owner, space, Vector.empty,
            _ => { deliveries += 1; Consequence.unit }).isSuccess shouldBe false
          interpretations shouldBe 0
          deliveries shouldBe 0
          stored(store) shouldBe before
          take(claims(store).loadC(identity)).get.claimId shouldBe Some(request.claimId)
          take(completion(store).loadC(identity)) shouldBe None
          Consequence.unit
        })
      }
    }

    "encode token-free Completed records and reject malformed status/token combinations" in {
      val available = intent(initial).continuation
      val completed = available.copy(status = ContinuationRuntimePersistence.Status.Completed)
      take(ContinuationRecordJsonV1.decodeC(take(ContinuationRecordJsonV1.encodeC(completed)))) shouldBe completed
      completed.claimId shouldBe None
      ContinuationRecordJsonV1.encodeC(available.copy(claimId = Some("unexpected"))).isSuccess shouldBe false
      ContinuationRecordJsonV1.encodeC(available.copy(status = ContinuationRuntimePersistence.Status.Claimed)).isSuccess shouldBe false
      ContinuationRecordJsonV1.encodeC(completed.copy(claimId = Some(" "))).isSuccess shouldBe false
      val claimedBytes = take(ContinuationRecordJsonV1.encodeC(available.copy(
        status = ContinuationRuntimePersistence.Status.Claimed, claimId = Some("legacy-token"))))
      val missingToken = io.circe.parser.parse(claimedBytes).toOption.get
        .mapObject(_.add("claimId", Json.Null)).noSpaces
      ContinuationRecordJsonV1.decodeC(missingToken).isSuccess shouldBe false
    }

    "reject invalid input and pre-native closing failures without changing the Available source" in {
      Vector("semantic", "owner", "closing-type", "closing-failure").foreach { mode =>
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            val request = seedAvailable(store)
            val before = stored(store)
            val closingType = StateMachineResultTypeReference("host.expected.v1")
            var interpretations = 0
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
              interpretations += 1
              if (mode == "closing-failure") ActionExecution.Failed(
                StateMachineOperationFailure("failed", "fixture closing failure", Vector.empty))
              else ActionExecution.Completed(StateMachineOperationResult(StateMachineResultTypeReference("wrong.closing.v1"),
                ContextReference("closing", "1")))
            }
            val result = if (mode == "semantic") request.result.copy(completionFacts = Vector.empty) else request.result
            val selectedOwner = if (mode == "owner") take(DataStoreComponentOwner.create("foreign.Component")) else owner
            var deliveries = 0
            completion(store).executeSelectedClosingHostC(request.current, request.entry, result, program,
              closingType, selectedOwner, space, Vector.empty, _ => { deliveries += 1; Consequence.unit }).isSuccess shouldBe false
            val rejectedBeforeClosing = mode == "semantic" || mode == "owner"
            interpretations shouldBe (if (rejectedBeforeClosing) 0 else 1)
            take(claims(store).loadC(identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Available
            take(claims(store).loadC(identity)).get.claimId shouldBe None
            stored(store) shouldBe before
            take(completion(store).loadC(identity)) shouldBe None
            deliveries shouldBe 0
            Consequence.unit
          })
        }
      }
    }

    "leave the source untouched on closing exceptions while preserving interruption" in {
      Vector("nonfatal", "interrupt").foreach { mode =>
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            val request = seedAvailable(store)
            val before = stored(store)
            val closingType = StateMachineResultTypeReference("host.control.v1")
            val interrupted = new InterruptedException("owned pre-native interruption")
            val program: ExecUowM[ActionExecution] =
              ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
                if (mode == "interrupt") throw interrupted
                else throw new IllegalStateException("owned pre-native failure")
              }
            var deliveries = 0
            def execute() = completion(store).executeSelectedClosingHostC(request.current, request.entry,
              request.result, program, closingType, owner, space, Vector.empty,
              _ => { deliveries += 1; Consequence.unit })
            if (mode == "interrupt") {
              try {
                intercept[InterruptedException](execute()) shouldBe interrupted
                Thread.currentThread().isInterrupted shouldBe true
              } finally Thread.interrupted()
            } else execute().isSuccess shouldBe false
            take(claims(store).loadC(identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Available
            take(claims(store).loadC(identity)).get.claimId shouldBe None
            take(completion(store).loadC(identity)) shouldBe None
            stored(store) shouldBe before
            val recovered = take(root(store).load(config, request.current.identity)).get
            take(WorkflowInstanceRecordJsonV1.encodeC(recovered)) shouldBe
              take(WorkflowInstanceRecordJsonV1.encodeC(request.current))
            deliveries shouldBe 0
            Consequence.unit
          })
        }
      }
    }

    "retain token-free completion after subscriber failure without replay or rollback" in {
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          provisionEventSchema(s, store)
          val request = seedAvailable(store)
          val closingType = StateMachineResultTypeReference("host.dispatch.v1")
          val actionResult = StateMachineOperationResult(closingType, ContextReference("closing", "1"))
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(actionResult))
          var calls = 0
          val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
          bus.register(EventSubscription("owned-failure", handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              calls += 1; Consequence.stateConflict("owned subscriber failure")
            }
          }))
          def execute() = completion(store).executeSelectedClosingHostC(request.current, request.entry,
            request.result, program, closingType, owner, space, Vector(ordinaryEvent("owned-failure")),
            batch => batch.foldLeft(Consequence.unit)((acc, event) =>
              acc.flatMap(_ => bus.dispatchCommittedRecordC(event).map(_ => ()))))
          execute().isSuccess shouldBe false
          take(claims(store).loadC(identity)).get.status shouldBe ContinuationRuntimePersistence.Status.Completed
          take(claims(store).loadC(identity)).get.claimId shouldBe None
          take(completion(store).loadC(identity)).get.closingResult shouldBe Some(actionResult)
          calls shouldBe 1
          execute().isSuccess shouldBe false
          calls shouldBe 1
          Consequence.unit
        })
      }
    }

    "retain native storage and suppress retry after actual EventBus handler failure" in {
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          provisionEventSchema(s, store)
          val request = seed(store)
          val actionResult = StateMachineOperationResult(StateMachineResultTypeReference("closing.dispatch.v1"), ContextReference("dispatch", "1"))
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](ActionExecution.Completed(actionResult))
          val closing = take(WorkflowClosingProgramPreparation.prepareC(program))
          val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
          var calls = 0
          bus.register(EventSubscription("failure", handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              calls += 1
              take(completion(store).loadC(identity)).get.closingResult shouldBe Some(actionResult)
              Consequence.stateConflict("actual subscriber fixture")
            }
          }))
          val input = Vector(ordinaryEvent("first-dispatch"), ordinaryEvent("second-dispatch"))
          val participant = take(completion(store).prepareClosingHostParticipantC(request, owner, space, input, closing,
            Vector.empty, () => Consequence.unit, records => records.foldLeft(Consequence.unit) { (acc, record) =>
              acc.flatMap(_ => bus.dispatchCommittedRecordC(record).map(_ => ()))
            }))
          val uow = new ManagedCompletionUnitOfWork(context, participant)
          uow.commit().isSuccess shouldBe false
          uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
          input.foreach(event => take(store.load(eventFacts, key(event.id.value))) should not be None)
          take(completion(store).loadC(identity)).get.result shouldBe request.result
          calls shouldBe 1
          uow.commit().isSuccess shouldBe false
          calls shouldBe 1
          take(s.eventStore.query(EventStore.Query())) shouldBe empty
          Consequence.unit
        })
      }
    }

    "recover both actual closing and submitted results after managed SQLite shutdown without interpreting the program again" in {
      val file = Files.createTempDirectory("cncf-closing-result-reopen-").resolve("workflow.sqlite")
      val actionResult = StateMachineOperationResult(StateMachineResultTypeReference("closing.reopen.v1"),
        ContextReference("closing-reopen", "3"))
      var interpreted = 0
      var expected: Option[ContinuationResult[String]] = None
      withManagedHostAt(file) { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          val request = seed(store)
          expected = Some(request.result)
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()).map { _ =>
            interpreted += 1
            ActionExecution.Completed(actionResult): ActionExecution
          }
          val closing = take(WorkflowClosingProgramPreparation.prepareC(program))
          val participant = take(completion(store).prepareClosingHostParticipantC(request, owner, space,
            Vector.empty, closing, Vector.empty, () => Consequence.unit, _ => Consequence.unit))
          val tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
          take(participant.prepareC(tx))
          take(participant.commitC(tx)) shouldBe a[ManagedCompletionParticipant.Outcome.Verified[?]]
          take(participant.finishC(tx))
          Consequence.unit
        })
      }
      val reopened = SqlDataStore.sqlite(file.toString)
      try {
        val recovered = take(completion(reopened).loadC(identity)).get
        Some(recovered.result) shouldBe expected
        recovered.closingResult shouldBe Some(actionResult)
        interpreted shouldBe 1
      } finally take(reopened.closeC())
    }

    "suppress both results and delivery when the live claim changes after joint preparation" in {
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          val request = seed(store)
          val result = StateMachineOperationResult(StateMachineResultTypeReference("closing.stale.v1"), ContextReference("stale", "1"))
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](ActionExecution.Completed(result))
          val closing = take(WorkflowClosingProgramPreparation.prepareC(program))
          var deliveries = 0
          val participant = take(completion(store).prepareClosingHostParticipantC(request, owner, space,
            Vector.empty, closing, Vector.empty, () => Consequence.unit, _ => { deliveries += 1; Consequence.unit }))
          take(claims(store).releaseC(identity, request.claimId))
          take(claims(store).claimC(identity)).claimId should not be Some(request.claimId)
          val before = stored(store)
          val tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
          take(participant.prepareC(tx))
          take(participant.commitC(tx)) should not be a[ManagedCompletionParticipant.Outcome.Verified[?]]
          participant.finishC(tx).isSuccess shouldBe false
          stored(store) shouldBe before
          take(completion(store).loadC(identity)) shouldBe None
          deliveries shouldBe 0
          Consequence.unit
        })
      }
    }

    "retain distinct closing results and read passive V1/V2 annotations while rejecting malformed frames" in {
      Given("a semantic completion and a distinct typed closing result; passive annotations are not progression authority")
      val semantic = WorkflowCompletionRecordJsonV1.Record(InstanceRevision(2L), submitted(issue(intent(initial))))
      When("both declared formats encode and decode their data and the V2 frame receives an unused annotation")
      val legacy = take(WorkflowCompletionRecordJsonV1.encodeC(semantic, resultCodec))
      val closing = StateMachineOperationResult(StateMachineResultTypeReference("actual.closing.v1"),
        ContextReference("actual-closing-context", "7"))
      val joint = semantic.copy(closingResult = Some(closing))
      val bytes = take(WorkflowCompletionRecordJsonV1.encodeC(joint, resultCodec))
      val decodedjoint = WorkflowCompletionRecordJsonV1.decodeC(bytes, resultCodec)
      val decodedlegacy = WorkflowCompletionRecordJsonV1.decodeC(legacy, resultCodec)
      val encodedlegacy = WorkflowCompletionRecordJsonV1.encodeC(semantic, resultCodec)
      val json = io.circe.parser.parse(bytes).toOption.get
      val annotated = WorkflowCompletionRecordJsonV1.decodeC(json.mapObject(_.add("extra", Json.True)).noSpaces, resultCodec)
      val malformed = Vector(
        json.mapObject(_.remove("closingResult")),
        json.mapObject(_.add("closingResult", Json.Null)),
        json.mapObject(_.add("closingResult", Json.obj("typeIdentity" -> Json.fromString(""),
          "contextIdentity" -> Json.fromString("actual-closing-context"), "contextRevision" -> Json.fromString("7")))),
        json.mapObject(_.add("semanticCompletion", json))
      ).map(bad => WorkflowCompletionRecordJsonV1.decodeC(bad.noSpaces, resultCodec))
      val missingclosing = WorkflowCompletionRecordJsonV1.encodeC(semantic.copy(closingResult = Some(null)), resultCodec)
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
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s._with_managed_datastore_lease_c { lease =>
            s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { originalSpace =>
              val store = take(originalSpace.dataStore(instances))
              val request = seed(store)
              provisionEventSchema(s, store)
              val local = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "completion", "local_guard"))
              val facts = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "completion", "local_facts"))
              val id = key("closing")
              take(store.create(local, id, Record.dataAuto("revision" -> 1L, "value" -> "open")))
              val guard = take(DataStoreConditionalRoot.create(owner, local, id, "revision",
                Some(org.simplemodeling.model.datatype.EntityRevision.INITIAL), Vector.empty,
                Record.dataAuto("value" -> "closed"), take(org.simplemodeling.model.datatype.EntityRevision.createC(2L))))
              val base = take(eventDeclaration.policyC(take(eventDeclaration.grantC(component))))
              val binding = DataStoreConditionalGuardBinding(owner, local)
              val policy = take(DataStoreConditionalMultiGuardHostPolicy.create(owner, instances, continuations,
                base.guardBindings :+ binding, base.sideCreateCollections :+ facts,
                base.guardTargets :+ DataStoreConditionalGuardTarget(binding, id)))
              val read = take(DataStoreObservationPolicy.createC(
                DataStoreObservationTarget(eventCursor, eventDeclaration.cursorEntryId), eventFacts, eventIndexes))
              val space = take(DataStoreSpace.multiGuardHostViewC(policy, store, lease, Some(read), Some(s.eventStore)))
              val retained = ordinaryEvent("retained")
              if (mode == "retained" || mode == "mixed") {
                val selected = take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(retained)))
                val proposal = take(WorkflowCursorEventProposal.prepareV2C(selected, eventDeclaration, EventCursorRecordV1.initial))
                proposal.events.creates.foreach(c => take(store.create(c.collection, c.entryId, c.record)))
                take(store.save(eventCursor, eventDeclaration.cursorEntryId, take(EventCursorRecordV1.createC(1L, 1L)).toRecord))
              }
              val transient = ordinaryEvent("ephemeral", "ephemeral")
              val fresh = ordinaryEvent("fresh")
              val retainedIncoming = retained.copy(name = "untrusted incoming name", payload = Map("unsupported" -> new Object))
              val input = mode match {
                case "empty" => Vector.empty
                case "transient" => Vector(transient)
                case "retained" => Vector(retainedIncoming, transient)
                case "new" => Vector(fresh)
                case "mixed" => Vector(retainedIncoming, transient, fresh)
              }
              val guardedIntent = take(WorkflowClosingLocalIntent.guardedC(guard))
              val createIntent = take(WorkflowClosingLocalIntent.createC(EntityVersionedSideEffect.Create(
                facts, id, Record.dataAuto("value" -> "completed"))))
              val actionResult = StateMachineOperationResult(StateMachineResultTypeReference("closing.result.v1"),
                ContextReference("closing-result", "1"))
              val closingProgram: ExecUowM[ActionExecution] = for {
                _ <- ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StageWorkflowClosingLocalIntent(guardedIntent)))
                _ <- ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StageWorkflowClosingLocalIntent(createIntent)))
              } yield ActionExecution.Completed(actionResult)
              val closing = take(WorkflowClosingProgramPreparation.prepareC(closingProgram))
              closing.result shouldBe actionResult
              take(store.load(local, id)).get.getString("value") shouldBe Some("open")
              take(store.load(facts, id)) shouldBe None
              var delivered = Vector.empty[EventRecord]
              val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
              bus.register(EventSubscription("native-finalized", handler = new EventDispatchHandler {
                def dispatch(event: DomainEvent): Consequence[Unit] = event match {
                  case FinalizedRecordDomainEvent(record) => delivered :+= record; Consequence.unit
                  case _ => Consequence.stateConflict("Expected finalized native record")
                }
              }))
              val participant = take(completion(store).prepareClosingHostParticipantC(request, owner, space, input, closing,
                Vector.empty, () => Consequence.unit, batch => batch.foldLeft(Consequence.unit) { (acc, record) =>
                  acc.flatMap(_ => bus.dispatchCommittedRecordC(record).map(_ => ()))
                }))
              val uow = new ManagedCompletionUnitOfWork(context, participant)
              When("the participant commits against the common host route, without separately executing local writes")
              take(uow.commit())
              uow.storageOutcome.get match {
                case ManagedCompletionParticipant.Outcome.Verified(value) =>
                  value.retained.result shouldBe request.result
                  value.retained.closingResult shouldBe Some(actionResult)
                case other => fail(s"unexpected local completion outcome: $other")
              }
              Then("typed completion, completed claim and both local post-images are retained before delivery")
              delivered.map(_.id) shouldBe input.map(_.id)
              if (mode == "retained" || mode == "mixed") delivered.head.name shouldBe retained.name
              delivered.filter(_.kind == "ephemeral").foreach { event =>
                event.persistent shouldBe false
                event.sequence shouldBe 0L
              }
              delivered.filter(_.kind == "fact").foreach { event =>
                event.persistent shouldBe true
                event.sequence should be > 0L
                val saved = take(store.load(eventFacts, key(event.id.value))).get.getString("event_json").get
                EventRecordJsonV2.encodeStorage(event) shouldBe saved
              }
              take(completion(store).loadC(identity)).get.result shouldBe request.result
              take(completion(store).loadC(identity)).get.closingResult shouldBe Some(actionResult)
              take(store.load(local, id)).get.getString("value") shouldBe Some("closed")
              take(store.load(facts, id)).get.getString("value") shouldBe Some("completed")
              take(store.load(eventFacts, key(transient.id.value))) shouldBe None
              delivered.map(_.id) shouldBe input.map(_.id)
              take(s.eventStore.query(EventStore.Query())) shouldBe empty
              uow.commit().isSuccess shouldBe false
              Consequence.unit
            }
          })
        }
      }
    }

    "reject foreign prepared providers and unenlisted closing effects before binding a participant" in {
      Vector("foreign-provider", "closing-effect").foreach { mode =>
        Given(s"a managed host with $mode in the prepared completion")
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            val request = seed(store)
            val original = take(completion(store).prepareCompletionC(request, owner))
            val prepared = if (mode == "foreign-provider") {
              val other = DataStore.inMemory()
              take(completion(other).prepareCompletionC(seed(other), owner))
            } else take(original.withSideEffectsC(Vector(EntityVersionedSideEffect.Save(
              DataStore.CollectionId("unenlisted_closing"), key("effect"), Record.dataAuto("value" -> "denied")))))
            var actions = 0
            val before = stored(store)
            When("the host attempts to bind the prepared completion lifecycle")
            prepared.hostParticipantC(space, Vector.empty,
              Vector(() => { actions += 1; Consequence.unit }),
              () => { actions += 1; Consequence.unit },
              _ => { actions += 1; Consequence.unit }).isSuccess shouldBe false
            Then("no participant, native commit, callback or delivery is admitted")
            actions shouldBe 0
            stored(store) shouldBe before
            take(completion(store).loadC(identity)) shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "deny a participant whose real host lease ended without retry or dispatch" in {
      Given("a prepared participant escaping its admitted host lease")
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        var actions = 0
        val tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
        val participant = take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          val request = seed(store)
          val prepared = take(completion(store).prepareCompletionC(request, owner))
          prepared.hostParticipantC(space, Vector.empty,
            Vector(() => { actions += 1; Consequence.unit }), () => Consequence.unit,
            _ => { actions += 1; Consequence.unit }).flatMap { bound => bound.prepareC(tx).map(_ => bound) }
        })
        When("the escaped participant attempts native commit after scope release")
        take(participant.commitC(tx)).isInstanceOf[ManagedCompletionParticipant.Outcome.Indeterminate] shouldBe true
        participant.finishC(tx).isSuccess shouldBe false
        Then("ordinary failure is not misclassified as proven rollback and no current claim is consumed")
        actions shouldBe 0
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          take(completion(store).loadC(identity)) shouldBe None
          take(claims(store).loadStoredC(identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Claimed
          Consequence.unit
        })
        participant.commitC(tx).isSuccess shouldBe false
      }
    }

    "bind real host storage to one participant and defer the original mixed batch until lifecycle completion" in {
      Given("a real managed claimed WorkOrder with retained, transient and new ordinary events")
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val store = take(space.dataStore(instances))
          val request = seed(store)
          provisionEventSchema(s, store)
          val retained = ordinaryEvent("retained")
          val selected = take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(retained)))
          val proposal = take(WorkflowCursorEventProposal.prepareV2C(selected, eventDeclaration, EventCursorRecordV1.initial))
          proposal.events.creates.foreach(c => take(store.create(c.collection, c.entryId, c.record)))
          take(store.save(eventCursor, eventDeclaration.cursorEntryId, take(EventCursorRecordV1.createC(1L, 1L)).toRecord))
          val batch = Vector(retained, ordinaryEvent("ephemeral", "ephemeral"), ordinaryEvent("fresh"))
          val log = scala.collection.mutable.ArrayBuffer.empty[String]
          val prepared = take(completion(store).prepareCompletionC(request, owner))
          val participant = take(prepared.hostParticipantC(space, batch,
            Vector(() => { log += "callback"; Consequence.unit }),
            () => { log += "cleanup"; Consequence.unit },
            records => { log ++= records.map(r => "deliver:" + r.id.value); Consequence.unit }))
          val tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
          take(participant.prepareC(tx))
          take(completion(store).loadC(identity)) shouldBe None
          log shouldBe empty
          When("the participant consumes its native commit against actual host storage")
          take(participant.commitC(tx)) match {
            case ManagedCompletionParticipant.Outcome.Verified(value) => value.retained.result shouldBe request.result
            case other => fail(s"unexpected native outcome: $other")
          }
          Then("completion and selected durable facts are stored before any callback or delivery")
          take(completion(store).loadC(identity)).get.result shouldBe request.result
          take(s.loadEventCursorC(environment(s))).lastSequence shouldBe 2L
          take(store.load(eventFacts, key(batch(1).id.value))) shouldBe None
          log shouldBe empty
          When("frozen callbacks and cleanup finish successfully")
          take(participant.finishC(tx))
          Then("the host delivery port receives the original batch once, not a durable-only replay")
          log.toVector shouldBe Vector("callback", "cleanup") ++ batch.map(r => "deliver:" + r.id.value)
          participant.finishC(tx).isSuccess shouldBe false
          participant.commitC(tx).isSuccess shouldBe false
          log.size shouldBe 5
          Consequence.unit
        })
      }
    }

    "preserve joint SQLite completion and suppress actual bus dispatch after callback, cleanup or UoW resource failure" in {
      Vector("callback", "cleanup", "resource").foreach { stage =>
        Given(s"actual managed storage followed by a failing $stage")
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            val request = seed(store)
            provisionEventSchema(s, store)
            var delivered = 0
            var cleaned = 0
            var released = Vector.empty[org.goldenport.cncf.unitofwork.UnitOfWorkTermination]
            val actionResult = StateMachineOperationResult(StateMachineResultTypeReference("closing.lifecycle.v1"),
              ContextReference("lifecycle", "1"))
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(actionResult))
            val closing = take(WorkflowClosingProgramPreparation.prepareC(program))
            val input = Vector(ordinaryEvent("durable-lifecycle"), ordinaryEvent("transient-lifecycle", "ephemeral"))
            val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
            bus.register(EventSubscription("lifecycle-failure", handler = new EventDispatchHandler {
              def dispatch(event: DomainEvent): Consequence[Unit] = { delivered += 1; Consequence.unit }
            }))
            val participant = take(completion(store).prepareClosingHostParticipantC(request, owner, space, input, closing,
              Vector(() => if (stage == "callback") Consequence.stateConflict("fixture callback") else Consequence.unit),
              () => { cleaned += 1; if (stage == "cleanup") Consequence.stateConflict("fixture cleanup") else Consequence.unit },
              batch => batch.foldLeft(Consequence.unit)((acc, event) =>
                acc.flatMap(_ => bus.dispatchCommittedRecordC(event).map(_ => ())))))
            val uow = new ManagedCompletionUnitOfWork(context, participant)
            take(uow.registerResourceC(new org.goldenport.cncf.unitofwork.UnitOfWorkResource {
              def releaseC(termination: org.goldenport.cncf.unitofwork.UnitOfWorkTermination): Consequence[Unit] = {
                released :+= termination
                if (stage == "resource") Consequence.stateConflict("fixture resource") else Consequence.unit
              }
            }))
            When("the active managed UoW commits and fails during lifecycle completion")
            uow.commit().isSuccess shouldBe false
            Then("stored result remains completed and neither failure authorizes delivery or compensation")
            uow.storageOutcome.get shouldBe a[ManagedCompletionParticipant.Outcome.Verified[?]]
            uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
            take(completion(store).loadC(identity)).get.result shouldBe request.result
            take(completion(store).loadC(identity)).get.closingResult shouldBe Some(actionResult)
            take(claims(store).loadStoredC(identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
            take(store.load(eventFacts, key(input.head.id.value))) should not be None
            take(store.load(eventFacts, key(input.last.id.value))) shouldBe None
            take(s.loadEventCursorC(environment(s))).lastSequence shouldBe 1L
            released shouldBe Vector(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
            delivered shouldBe 0
            cleaned shouldBe 1
            uow.commit().isSuccess shouldBe false
            cleaned shouldBe 1
            released.size shouldBe 1
            delivered shouldBe 0
            take(s.eventStore.query(EventStore.Query())) shouldBe empty
            Consequence.unit
          })
        }
      }
    }

    "retain proven stale-guard rejection separately from postcommit projection uncertainty in the native binding" in {
      Vector("stale-root", "projection").foreach { mode =>
        Given(s"a real managed prepared completion with $mode failure")
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances)).asInstanceOf[DataStore & EntityConditionalTransitionDataStore]
            val request = seed(store)
            provisionEventSchema(s, store)
            val original = take(completion(store).prepareCompletionC(request, owner))
            val prepared = if (mode == "projection") WorkflowPreparedDataStoreTransition.create(store, original.plan,
              _ => Consequence.stateConflict[WorkflowCompletionDataStorePersistence.PersistedCompletion[String]]("fixture projection"))
              else {
                val old = take(store.load(instances, key("instance-1"))).get
                val field = original.plan.root.revisionField
                take(store.save(instances, key("instance-1"), Record(old.fields.filterNot(_.key == field) ++
                  Record.dataAuto(field -> 9L).fields)))
                original
              }
            var callbacks = 0
            var deliveries = 0
            var cleanups = 0
            val event = ordinaryEvent("native-failure")
            val bus = EventBus.default(EventEngine.noop(store, eventstore = s.eventStore))
            bus.register(EventSubscription("native-failure", handler = new EventDispatchHandler {
              def dispatch(event: DomainEvent): Consequence[Unit] = { deliveries += 1; Consequence.unit }
            }))
            val participant = take(prepared.hostParticipantC(space, Vector(event),
              Vector(() => { callbacks += 1; Consequence.unit }),
              () => { cleanups += 1; Consequence.unit },
              batch => batch.foldLeft(Consequence.unit)((acc, record) =>
                acc.flatMap(_ => bus.dispatchCommittedRecordC(record).map(_ => ())))))
            val uow = new ManagedCompletionUnitOfWork(context, participant)
            When("native commit returns actual guard or committed-projection evidence")
            uow.commit().isSuccess shouldBe false
            val outcome = uow.storageOutcome.get
            Then("only proven NotMatched is rejected; projection failure retains uncertainty and actual completed storage")
            if (mode == "stale-root") {
              outcome.isInstanceOf[ManagedCompletionParticipant.Outcome.Rejected] shouldBe true
              uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Aborted)
              take(completion(store).loadC(identity)) shouldBe None
              take(claims(store).loadStoredC(identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Claimed
              take(store.load(eventFacts, key(event.id.value))) shouldBe None
              take(s.loadEventCursorC(environment(s))).lastSequence shouldBe 0L
            } else {
              outcome.isInstanceOf[ManagedCompletionParticipant.Outcome.Indeterminate] shouldBe true
              uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Indeterminate)
              take(completion(store).loadC(identity)).get.result shouldBe request.result
              take(claims(store).loadStoredC(identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
              take(store.load(eventFacts, key(event.id.value))) should not be None
              take(s.loadEventCursorC(environment(s))).lastSequence shouldBe 1L
            }
            callbacks shouldBe 0
            deliveries shouldBe 0
            cleanups shouldBe 1
            uow.commit().isSuccess shouldBe false
            deliveries shouldBe 0
            take(s.eventStore.query(EventStore.Query())) shouldBe empty
            Consequence.unit
          })
        }
      }
    }

    "retain exact semantic result, history and claim through every host-selected storage route" in {
      Vector("empty", "transient", "retained", "new", "mixed").foreach { mode =>
        Given(s"a real managed claimed WorkOrder and ordinary $mode input")
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          var expectedResult: Option[ContinuationResult[String]] = None
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            val request = seed(store)
            provisionEventSchema(s, store)
            expectedResult = Some(request.result)
            val retained = ordinaryEvent("retained")
            if (mode == "retained" || mode == "mixed") {
              val selected = take(WorkflowEventSelection.prepareFromStoreC(s.eventStore, Vector(retained)))
              val proposal = take(WorkflowCursorEventProposal.prepareV2C(selected, eventDeclaration, EventCursorRecordV1.initial))
              proposal.events.creates.foreach(c => take(store.create(c.collection, c.entryId, c.record)))
              take(store.save(eventCursor, eventDeclaration.cursorEntryId, take(EventCursorRecordV1.createC(1L, 1L)).toRecord))
            }
            val ephemeral = ordinaryEvent("ephemeral", "ephemeral").copy(payload = Map("unsupported" -> new Object))
            val fresh = ordinaryEvent("fresh")
            val input = mode match {
              case "empty" => Vector.empty
              case "transient" => Vector(ephemeral)
              case "retained" => Vector(retained, ephemeral)
              case "new" => Vector(fresh)
              case "mixed" => Vector(retained, ephemeral, fresh)
            }
            When("the typed completion adapter delegates ordinary events to the common host entry")
            val result = take(completion(store).completeHostEventsC(request, owner, space, input))
            Then("existing typed admission projects exact stored result, completed claim and adjacent history")
            result.retained.result shouldBe request.result
            result.instance.history.last shouldBe request.entry
            result.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
            take(completion(store).loadC(identity)).get.result shouldBe request.result
            input.foreach(_.sequence shouldBe 0L)
            Consequence.unit
          })
          Then("a fresh admitted scope recovers the exact completion and only selected durable facts")
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            take(completion(store).loadC(identity)).get.result shouldBe expectedResult.get
            val newDurable = mode == "new" || mode == "mixed"
            val hadRetained = mode == "retained" || mode == "mixed"
            val expectedSequence = (if (newDurable) 1L else 0L) + (if (hadRetained) 1L else 0L)
            take(s.loadEventCursorC(environment(s))).lastSequence shouldBe expectedSequence
            take(store.load(eventFacts, key(ordinaryEvent("fresh").id.value))).nonEmpty shouldBe newDurable
            take(store.load(eventFacts, key(ordinaryEvent("retained").id.value))).nonEmpty shouldBe hadRetained
            take(store.load(eventFacts, key(ordinaryEvent("ephemeral", "ephemeral").id.value))) shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "deny a prepared completion borrowed from another provider without changing the managed claim" in {
      Given("equivalent logical WorkOrders independently seeded in memory and the real managed host")
      withManagedHost { s =>
        take(s.initializeEventCursorC(environment(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
          val managed = take(space.dataStore(instances))
          seed(managed)
          val before = stored(managed)
          val foreign = DataStore.inMemory()
          val foreignRequest = seed(foreign)
          val prepared = take(completion(foreign).prepareCompletionC(foreignRequest, owner))
          When("the foreign prepared projection is submitted to this host")
          prepared.commitHostEventsC(space, Vector(ordinaryEvent("fresh"))).toOption shouldBe None
          Then("provider identity mismatch leaves both claims and guarded histories unchanged")
          stored(managed) shouldBe before
          take(completion(managed).loadC(identity)) shouldBe None
          take(completion(foreign).loadC(identity)) shouldBe None
          take(s.loadEventCursorC(environment(s))).lastSequence shouldBe 0L
          Consequence.unit
        })
      }
    }

    "preserve current records on re-claim, stale root or injected local effects without publishing a new event" in {
      Vector("reclaim", "stale-root", "local-effect").foreach { invalid =>
        Given(s"a managed completion prepared before $invalid")
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances))
            val request = seed(store)
            provisionEventSchema(s, store)
            val original = take(completion(store).prepareCompletionC(request, owner))
            val effects = DataStore.CollectionId("managed_completion_injected_effects")
            val prepared = invalid match {
              case "reclaim" =>
                take(claims(store).releaseC(identity, request.claimId))
                take(claims(store).claimC(identity)).claimId should not be Some(request.claimId)
                original
              case "stale-root" =>
                val old = take(store.load(instances, key("instance-1"))).get
                val revisionField = original.plan.root.revisionField
                take(store.save(instances, key("instance-1"), Record(old.fields.filterNot(_.key == revisionField) ++
                  Record.dataAuto(revisionField -> 9L).fields)))
                original
              case "local-effect" => take(original.withSideEffectsC(Vector(
                EntityVersionedSideEffect.Save(effects, key("injected"), Record.dataAuto("value" -> "denied")))))
            }
            val before = stored(store)
            When("the prepared completion attempts the common host path with one new ordinary event")
            prepared.commitHostEventsC(space, Vector(ordinaryEvent("fresh"))).toOption shouldBe None
            Then("guard rejection or effect admission changes neither current records nor cursor/event state")
            stored(store) shouldBe before
            take(completion(store).loadC(identity)) shouldBe None
            take(s.loadEventCursorC(environment(s))).lastSequence shouldBe 0L
            take(store.load(eventFacts, key(ordinaryEvent("fresh").id.value))) shouldBe None
            if (invalid == "local-effect") take(store.load(effects, key("injected"))) shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "return indeterminate on a failed or throwing projection after native commit without replay" in {
      Vector(false, true).foreach { throws =>
        Given("an admitted typed completion plan with a test-only faulty trusted projection")
        withManagedHost { s =>
          take(s.initializeEventCursorC(environment(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(component, environment(s)) { space =>
            val store = take(space.dataStore(instances)).asInstanceOf[DataStore & EntityConditionalTransitionDataStore]
            val request = seed(store)
            val original = take(completion(store).prepareCompletionC(request, owner))
            var projections = 0
            val faulty = WorkflowPreparedDataStoreTransition.create[String](store, original.plan, _ => {
              projections += 1
              if (throws) throw new IllegalStateException("fixture projection")
              else Consequence.stateConflict("fixture projection")
            })
            When("native storage verifies before the trusted projection fails")
            reason(faulty.commitHostEventsC(space, Vector.empty), "transaction-indeterminate")
            Then("the actual completion is retained, projection runs once and no compensation occurs")
            projections shouldBe 1
            take(completion(store).loadC(identity)).get.result shouldBe request.result
            take(claims(store).loadStoredC(identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
            take(s.loadEventCursorC(environment(s))).lastSequence shouldBe 0L
            Consequence.unit
          })
        }
      }
    }
  }

  "Phase 88 guarded retained completion" should {
    "commit both revision guards despite changed legacy policy columns and recover under current policy" in {
      Given("an actual SQLite claimed WorkOrder with no generated configuration copies")
      val db = path
      val first = SqlDataStore.sqlite(db.toString)
      val request = seed(first)
      val original = stored(first)
      val prepared = take(completion(first).prepareCompletionC(request, owner))
      When("both legacy policy columns change after preparation without consuming either indexed revision")
      val committed = try {
        take(first.update(instances, key("instance-1"), Record.dataAuto("configurationPayload" -> "old root policy")))
        take(first.update(continuations, key(identity.value), Record.dataAuto("configurationPayload" -> "different old continuation policy")))
        take(prepared.commitC())
      } finally take(first.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        val changed = config.copy(retentionPolicy = RetentionPolicy("changed"))
        val adapter = take(WorkflowCompletionDataStorePersistence.bindC(reopened, changed, component,
          completionOperation, Vector(binding), instances, continuations, codec, resultCodec))
        val recovered = take(adapter.loadC(identity)).get
        val retained = stored(reopened)
        val replay = adapter.completeC(request, owner)
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
      } finally take(reopened.closeC())
    }

    "prepare completion without completing the claim or exposing a retained result" in {
      Given("one claimed issued WorkOrder and a typed result")
      val store = DataStore.inMemory()
      val request = seed(store)
      val before = stored(store)
      When("the host prepares completion and adds an explicit local closing record")
      val prepared = take(completion(store).prepareCompletionC(request, owner))
      val effects = DataStore.CollectionId("completion_closing_records_v1")
      val composed = take(prepared.withSideEffectsC(Vector(EntityVersionedSideEffect.Save(
        effects, key("closed"), Record.dataAuto("meaning" -> "exact closing record")))))
      Then("preparation publishes neither history, claim completion, result nor local record")
      stored(store) shouldBe before
      take(completion(store).loadC(identity)) shouldBe None
      take(store.load(effects, key("closed"))) shouldBe None
      When("the host commits the same guarded plan once")
      val result = take(composed.commitC())
      Then("exact result, completing history, claim and explicit local record are retained together")
      result.retained.result shouldBe request.result
      take(completion(store).loadC(identity)).get.result shouldBe request.result
      take(store.load(effects, key("closed"))).get.getString("meaning") shouldBe Some("exact closing record")
      prepared.commitC().toOption shouldBe None
    }

    "reject a delayed prepared completion after release and re-claim without a local side effect" in {
      Given("one prepared completion retaining the selected Claimed storage revision and exact claim token")
      val store = DataStore.inMemory()
      val request = seed(store)
      val selected = take(claims(store).loadStoredC(identity)).get
      val effects = DataStore.CollectionId("completion_delayed_records_v1")
      val prepared = take(take(completion(store).prepareCompletionC(request, owner)).withSideEffectsC(
        Vector(EntityVersionedSideEffect.Save(effects, key("late"), Record.dataAuto("value" -> "not published")))))
      When("the current claim is released and claimed under a new token before commit")
      take(claims(store).releaseC(identity, request.claimId))
      val released = take(claims(store).loadStoredC(identity)).get
      val reclaimed = take(claims(store).claimC(identity))
      val current = take(claims(store).loadStoredC(identity)).get
      val before = stored(store)
      Then("the status returns to Claimed while release and reclaim each advance the storage revision")
      selected._1.status shouldBe ContinuationRuntimePersistence.Status.Claimed
      released._1.status shouldBe ContinuationRuntimePersistence.Status.Available
      released._2 shouldBe take(selected._2.nextC)
      current._1.status shouldBe selected._1.status
      current._2 shouldBe take(released._2.nextC)
      reclaimed.claimId should not be Some(request.claimId)
      When("the delayed completion attempts to consume its earlier selected storage revision")
      val result = prepared.commitC()
      Then("the restored Claimed state cannot admit the stale revision or publish a local closing record")
      result.toOption shouldBe None
      stored(store) shouldBe before
      take(completion(store).loadC(identity)) shouldBe None
      take(store.load(effects, key("late"))) shouldBe None
    }

    "roll back a prepared completion and explicit local effect at the publication checkpoint" in {
      Given("a borrowed provider that rejects publication after staging all record changes")
      var armed = false
      val store = new DataStore.InMemoryDataStore(org.goldenport.cncf.unitofwork.CommitRecorder.noop) {
        override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] =
          if (armed && stage == DataStoreConditionalTransitionCheckpoint.BeforePublish)
            Consequence.operationInvalid("injected prepared completion rejection")
          else Consequence.unit
      }
      val request = seed(store)
      val before = stored(store)
      val effects = DataStore.CollectionId("completion_rollback_records_v1")
      val prepared = take(take(completion(store).prepareCompletionC(request, owner)).withSideEffectsC(
        Vector(EntityVersionedSideEffect.Save(effects, key("closing"), Record.dataAuto("value" -> "rolled back")))))
      When("the host commits with publication rejection armed")
      armed = true
      val result = prepared.commitC()
      Then("history, claim, typed result and local effect stay unchanged")
      result.toOption shouldBe None
      stored(store) shouldBe before
      take(completion(store).loadC(identity)) shouldBe None
      take(store.load(effects, key("closing"))) shouldBe None
    }

    "commit host history, claimed Continuation and typed execution evidence together and recover after SQLite reopen" in {
      val db = path
      val first = SqlDataStore.sqlite(db.toString)
      val request = seed(first)
      val before = stored(first)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField)
      try {
        take(completion(first).loadC(identity)) shouldBe None
        val result = take(completion(first).completeC(request, owner))
        result.instance.lifecycle shouldBe Lifecycle.Completed
        result.instance.history shouldBe request.current.history :+ request.entry
        result.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
        result.continuation.claimId shouldBe Some(request.claimId)
        result.retained.result shouldBe request.result
        stored(first)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField) shouldBe before
      } finally take(first.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        take(completion(reopened).loadC(identity)) shouldBe Some(
          WorkflowCompletionRecordJsonV1.Record(InstanceRevision(2L), request.result))
        completion(reopened).completeC(request, owner).toOption shouldBe None
        claims(reopened).claimC(identity).toOption shouldBe None
        take(root(reopened).load(config, request.current.identity)).get.revision shouldBe InstanceRevision(2L)
      } finally take(reopened.closeC())
    }

    "recover an earlier completion after the host has advanced to later history" in {
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
        val active = request.entry.copy(lifecycle = Lifecycle.Active,
          currentProgression = Some(ProgressionReference("review.Accepted")))
        take(completion(store).completeC(request.copy(entry = active), owner))
        val later = request.entry.copy(sequence = HistorySequence(3L), revision = InstanceRevision(3L))
        take(root(store).append(config, request.current.identity, InstanceRevision(2L), later))
        take(completion(store).loadC(identity)).get.instanceRevision shouldBe InstanceRevision(2L)
        take(completion(store).loadC(identity)).get.result shouldBe request.result
        completion(store).completeC(request, owner).toOption shouldBe None
      } finally take(store.closeC())
    }

    "reject native facts, evidence and target/type errors before accepting unused dispatch diagnostics once" in {
      Given("one actual claimed issue and incompatible typed result boundaries")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
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
        val before = stored(store)
        When("each actual incompatible boundary is submitted")
        val outcomes = variants.map(value => completion(store).completeC(request.copy(result = value), owner))
        val retained = stored(store)
        Then("none consumes the current revision or claim")
        outcomes.foreach(_.toOption shouldBe None)
        retained shouldBe before
        When("only the unused copied dispatch review requirement changes on the still-live claim")
        val diagnostic = result.copy(evidence = result.evidence.copy(skillDispatch = result.evidence.skillDispatch.map(value =>
          value.copy(requestedRequirement = value.requestedRequirement.copy(reviewRequired = false)))))
        val accepted = completion(store).completeC(request.copy(result = diagnostic), owner)
        val committed = completion(store).loadC(identity)
        val replay = completion(store).completeC(request.copy(result = diagnostic), owner)
        Then("native admission completes once, retains the diagnostic data and replay cannot write again")
        accepted.isSuccess shouldBe true
        take(committed).map(_.result) shouldBe Some(diagnostic)
        replay shouldBe a[Consequence.Failure[?]]
      } finally take(store.closeC())
    }

    "require the current host suspension, expected instance revision and exact nonblank live claim token" in {
      Given("an actual stored suspension and one live claim token")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
        val before = stored(store)
        val variants = Vector(request.copy(claimId = ""), request.copy(claimId = null), request.copy(claimId = "foreign"),
          request.copy(expectedRevision = initialRevision),
          request.copy(entry = request.entry.copy(suspension = null)),
          request.copy(entry = request.entry.copy(suspension = request.current.suspension)))
        When("invalid live claim, revision or suspension facts are submitted")
        val outcomes = variants.map(value => completion(store).completeC(value, owner))
        val missing = completion(store).completeC(null, owner)
        val retained = stored(store)
        Then("invalid actual boundaries leave the stored issue unchanged")
        outcomes.foreach(_.toOption shouldBe None)
        missing.toOption shouldBe None
        retained shouldBe before
        When("release and re-claim replace the live token")
        take(claims(store).releaseC(identity, request.claimId))
        val released = completion(store).completeC(request, owner)
        val fresh = take(claims(store).claimC(identity)).claimId.get
        val stale = completion(store).completeC(request, owner)
        val completed = take(completion(store).completeC(request.copy(claimId = fresh), owner))
        Then("only the actual replacement token can consume completion")
        released.toOption shouldBe None
        stale.toOption shouldBe None
        completed.continuation.claimId shouldBe Some(fresh)
      } finally take(store.closeC())
    }

    "complete from stored current despite differing diagnostic causation and workspace copies" in {
      Given("a claimed SQLite issue with an actual owning suspension")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
        val diagnostic = request.copy(current = request.current.copy(causation = CausationReference("diagnostic")),
          result = request.result.copy(contextSnapshot = request.result.contextSnapshot.copy(
            workspaceRevision = Some("diagnostic workspace"))))
        When("the result targets the real stored state with only diagnostic copies differing")
        val completed = take(completion(store).completeC(diagnostic, owner))
        val retained = take(completion(store).loadC(identity)).get
        val stale = completion(store).completeC(diagnostic, owner)
        Then("actual history and typed result are consumed once without a whole-record or workspace proof")
        completed.instance.history shouldBe request.current.history :+ request.entry
        completed.instance.causation shouldBe request.entry.causation
        retained.result.contextSnapshot.workspaceRevision shouldBe Some("diagnostic workspace")
        stale.toOption shouldBe None
      } finally take(store.closeC())
    }

    "reject a release and reclaim between admission and commit without consuming the instance revision" in {
      val db = path
      val source = new SQLiteDataSource(sqliteConfig)
      source.setUrl(s"jdbc:sqlite:$db")
      var armed = false
      var fresh = ""
      var old = ""
      val store = new SqlDataStore(SqliteDialectDriver, source) {
        override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
            Consequence[DataStoreConditionalTransitionResult] = {
          if (armed && plan.successor.isInstanceOf[DataStoreConditionalSuccessor.Update]) {
            armed = false
            take(claims(this).releaseC(identity, old))
            fresh = take(claims(this).claimC(identity)).claimId.get
          }
          super.conditionalTransition(plan)
        }
      }
      try {
        val request = seed(store)
        val beforeRoot = stored(store)._1
        old = request.claimId
        armed = true
        completion(store).completeC(request, owner).toOption shouldBe None
        stored(store)._1 shouldBe beforeRoot
        take(claims(store).loadC(identity)).get.claimId shouldBe Some(fresh)
        take(completion(store).loadC(identity)) shouldBe None
        take(completion(store).completeC(request.copy(claimId = fresh), owner))
      } finally take(store.closeC())
    }

    "roll back result, Completed state and history at all pre-commit checkpoints" in {
      val stages = Vector(Some(DataStoreConditionalTransitionCheckpoint.GuardAdmitted),
        Some(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.RootPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.BeforePublish), None)
      stages.foreach { stage =>
        val db = path
        val source = new SQLiteDataSource(sqliteConfig)
        source.setUrl(s"jdbc:sqlite:$db")
        var armed = false
        val store = new SqlDataStore(SqliteDialectDriver, source) {
          override protected def conditional_transition_checkpoint(value: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] =
            if (armed && stage.contains(value)) Consequence.operationInvalid("injected completion checkpoint") else Consequence.unit
          override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] =
            if (armed && stage.isEmpty) DataStoreConditionalTransitionFailure.transactionFailure("injected completion commit")
            else super.conditional_transition_commit(connection)
        }
        val request = seed(store)
        val before = stored(store)
        try {
          armed = true
          completion(store).completeC(request, owner).toOption shouldBe None
        } finally take(store.closeC())
        val reopened = SqlDataStore.sqlite(db.toString)
        try {
          stored(reopened) shouldBe before
          take(completion(reopened).loadC(identity)) shouldBe None
        } finally take(reopened.closeC())
      }
    }

    "report malformed applied readback as indeterminate and recover the committed result without replay" in {
      val db = path
      val source = new SQLiteDataSource(sqliteConfig)
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
      val request = seed(store)
      try reason(completion(store).completeC(request, owner), "transaction-indeterminate")
      finally take(store.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        take(completion(reopened).loadC(identity)).get.result shouldBe request.result
        completion(reopened).completeC(request, owner).toOption shouldBe None
        take(root(reopened).load(config, request.current.identity)).get.history.size shouldBe 2
      } finally take(reopened.closeC())
    }

    "retain a single completion winner across independent SQLite providers and reject replay" in {
      val db = path
      val left = SqlDataStore.sqlite(db.toString)
      val right = SqlDataStore.sqlite(db.toString)
      val pool = Executors.newFixedThreadPool(2)
      val gate = new CountDownLatch(1)
      try {
        val request = seed(left)
        val adapters = Vector(completion(left), completion(right))
        val tasks = adapters.map(adapter => pool.submit(new java.util.concurrent.Callable[Boolean] {
          def call(): Boolean = {
            gate.await()
            adapter.completeC(request, owner).toOption.nonEmpty
          }
        }))
        gate.countDown()
        tasks.map(_.get(20L, TimeUnit.SECONDS)).count(_ == true) shouldBe 1
        take(completion(left).loadC(identity)).get.result shouldBe request.result
        completion(right).completeC(request, owner).toOption shouldBe None
        take(root(right).load(config, request.current.identity)).get.history.size shouldBe 2
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(20L, TimeUnit.SECONDS)
        take(left.closeC())
        take(right.closeC())
      }
    }

    "fail on malformed or foreign retained input without a canonical-body certificate" in {
      val baseline = seedBytes()
      Vector("broken",
        WorkflowInstanceDataStorePersistence.storageText(baseline.replace("cncf.workflow-completion-record.v1", "unsupported")),
        WorkflowInstanceDataStorePersistence.storageText(baseline.replace("instance-1", "foreign"))).foreach { payload =>
        val store = SqlDataStore.sqlite(path.toString)
        try {
          val request = seed(store)
          take(completion(store).completeC(request, owner))
          take(store.update(continuations, key(identity.value), Record.dataAuto(WorkflowCompletionDataStorePersistence.payloadField -> payload)))
          val before = stored(store)
          completion(store).loadC(identity).toOption shouldBe None
          stored(store) shouldBe before
        } finally take(store.closeC())
      }
    }

    "read permitted old formatting and diagnostic snapshots without rewriting completion or renewing a consumed claim" in {
      Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R2; Example: E3; a physically committed SQLite completion")
      val db = path
      val store = SqlDataStore.sqlite(db.toString)
      val request = seed(store)
      val saved = take(completion(store).completeC(request, owner)).retained
      val original = take(WorkflowCompletionRecordJsonV1.encodeC(saved, resultCodec))
      val diagnostic = io.circe.parser.parse(original).toOption.get.hcursor.downField("result").downField("contextSnapshot")
        .downField("workspaceRevision").withFocus(_ => Json.fromString("retained-workspace-description")).top.get
      take(store.update(continuations, key(identity.value), Record.dataAuto(WorkflowCompletionDataStorePersistence.payloadField ->
        WorkflowInstanceDataStorePersistence.storageText(" \n" + diagnostic.spaces2))))
      val before = stored(store)
      take(store.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        When("the completion is decoded after physical reopen, without a re-encoded-body comparison")
        val retained = take(completion(reopened).loadC(identity)).get
        Then("the real result/closing revision remain usable without row rewrite or another completion")
        retained.instanceRevision shouldBe saved.instanceRevision
        retained.result.result shouldBe saved.result.result
        retained.result.contextSnapshot.workspaceRevision shouldBe Some("retained-workspace-description")
        stored(reopened) shouldBe before
        completion(reopened).completeC(request, owner).isSuccess shouldBe false
      } finally take(reopened.closeC())
    }

    "read completed stored facts without a history certificate, but reject missing result or unissued execution" in {
      Given("docs/spec/workflow-completion-applicability.md R7: a claimed issued Continuation with a stored typed result but no completion")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
        val bytes = take(WorkflowCompletionRecordJsonV1.encodeC(
          WorkflowCompletionRecordJsonV1.Record(request.entry.revision, request.result), resultCodec))
        take(store.update(continuations, key(identity.value), Record.dataAuto(
          WorkflowCompletionDataStorePersistence.payloadField -> WorkflowInstanceDataStorePersistence.storageText(bytes))))
        When("the result is queried before its native Continuation becomes Completed")
        val premature = completion(store).loadC(identity)
        Then("non-Completed native state refuses the stored result")
        premature.toOption shouldBe None
        When("the native claim completes independently and the result is queried without adjacent history proof")
        take(claims(store).completeC(identity, request.claimId))
        val retained = take(completion(store).loadC(identity)).get
        val observed = take(root(store).load(config, request.current.identity)).get
        val replay = completion(store).completeC(request, owner)
        Then("the stored typed result is readable, but query does not advance the instance or authorize replay")
        retained.result shouldBe request.result
        observed.revision shouldBe InstanceRevision(1L)
        // Query grants no replay and does not certify a past joint transaction.
        replay.toOption shouldBe None
      } finally take(store.closeC())
      Given("a legacy native completion without a retained typed result")
      val legacy = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(legacy)
        take(claims(legacy).completeC(identity, request.claimId))
        When("Completion query reads the legacy native state")
        val missing = completion(legacy).loadC(identity)
        Then("missing retained result cannot be fabricated or retrofitted")
        missing.toOption shouldBe None
      } finally take(legacy.closeC())
      Given("a native suspension created without atomically issued work")
      val unissued = SqlDataStore.sqlite(path.toString)
      try {
        val current = initial
        val request = intent(current)
        take(root(unissued).create(config, current))
        val suspended = take(root(unissued).suspendC(request, owner, continuations)).instance
        val token = take(claims(unissued).claimC(identity)).claimId.get
        val entry = request.entry.copy(sequence = HistorySequence(2L), revision = InstanceRevision(2L),
          lifecycle = Lifecycle.Completed, currentProgression = None, suspension = None)
        When("execution attempts completion using a result for work that was never issued")
        val rejected = completion(unissued).completeC(WorkflowCompletionDataStorePersistence.Intent(suspended,
          suspended.revision, entry, token, submitted(issue(request))), owner)
        val observed = take(root(unissued).load(config, current.identity)).get
        Then("the actual writer refuses unissued execution and leaves the instance at its suspension")
        rejected.toOption shouldBe None
        observed.revision shouldBe InstanceRevision(1L)
      } finally take(unissued.closeC())
    }

    "reject unsupported capabilities and throwing result codecs before publication" in {
      Given("an actual claimed issue and an application writer that throws")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
        val before = stored(store)
        val throwing = new PayloadCodec[String] {
          val typeIdentity = resultCodec.typeIdentity
          def encode(value: String): Json = throw new IllegalArgumentException("injected")
          def decode(value: Json) = resultCodec.decode(value)
        }
        When("the throwing writer or a provider without required capability is used")
        val rejected = completion(store, throwing).completeC(request, owner)
        val unsupported = WorkflowCompletionDataStorePersistence.bindC(DataStore.noop(), config, component, completionOperation,
          Vector(binding), instances, continuations, codec, resultCodec)
        val retained = stored(store)
        Then("serialization failure or missing capability publishes no completion")
        rejected.toOption shouldBe None
        unsupported.toOption shouldBe None
        retained shouldBe before
      } finally take(store.closeC())
    }

    "consume trusted codec output once without producer self-certification" in {
      Given("an actual claimed issue and a trusted typed writer selecting its storage representation")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
        var writes = 0
        var reads = 0
        val transformed = new PayloadCodec[String] {
          val typeIdentity = resultCodec.typeIdentity
          def encode(value: String): Json = { writes += 1; Json.fromString("serialized representation") }
          def decode(value: Json) = { reads += 1; resultCodec.decode(value) }
        }
        val adapter = completion(store, transformed)
        When("completion serializes once, consumes committed readback and later queries retained meaning")
        val completed = take(adapter.completeC(request, owner))
        val retained = take(adapter.loadC(identity)).get
        val writecount = writes
        val readcount = reads
        Then("the writer owns its representation without an extra round-trip or payload-copy comparison")
        completed.retained.result.result.value shouldBe "serialized representation"
        retained.result.result.value shouldBe "serialized representation"
        completed.instance.lifecycle shouldBe Lifecycle.Completed
        writecount shouldBe 1
        readcount shouldBe 2
      } finally take(store.closeC())
    }

    "preserve the existing optional dispatch contract without manufacturing Skill evidence" in {
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val request = seed(store)
        val withoutDispatch = request.result.copy(evidence = request.result.evidence.copy(skillDispatch = None))
        take(completion(store).completeC(request.copy(result = withoutDispatch), owner))
        take(completion(store).loadC(identity)).get.result.evidence.skillDispatch shouldBe None
      } finally take(store.closeC())
    }

    "complete issued input beyond forty thousand characters under revision guards and recover it intact" in {
      Vector("memory", "sqlite").foreach { provider =>
        Given(s"docs/spec/entity-conflict-and-conditional-transition.md R7 with a large issued request on $provider")
        val db = path
        val store: DataStore = if (provider == "memory") DataStore.inMemory() else SqlDataStore.sqlite(db.toString)
        val current = initial
        val input = "日本語\"quoted\"\\line\n" * 4000
        val base = intent(current)
        val source = base.continuation.continuation
        val request = base.copy(continuation = base.continuation.copy(
          continuation = source.copy(context = source.context.copy(summary = input))))
        val original = issue(request)
        val order = original.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
        val large = original.copy(current = order.copy(request = order.request.copy(
          input = Some(TypedValue(codec.typeIdentity, input)))))
        val result = submitted(large)
        val entry = request.entry.copy(sequence = HistorySequence(2L), revision = InstanceRevision(2L),
          lifecycle = Lifecycle.Completed, currentProgression = None, suspension = None)
        try {
          take(root(store).create(config, current))
          val suspended = take(issued(store).suspendC(request, large, owner)).instance
          val token = take(claims(store).claimC(identity)).claimId.get
          val before = stored(store)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField)
          val selected = take(claims(store).loadStoredC(identity)).get
          When("the selected claim and instance revisions atomically complete the large request")
          val completed = take(completion(store).completeC(WorkflowCompletionDataStorePersistence.Intent(suspended,
            suspended.revision, entry, token, result), owner))
          Then("the result, completed status and next revisions are retained with the entire unchanged issued body")
          input.length should be >= 40000
          completed.instance.revision shouldBe entry.revision
          completed.instance.lifecycle shouldBe Lifecycle.Completed
          completed.instance.history shouldBe suspended.history :+ entry
          completed.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
          completed.continuation.claimId shouldBe Some(token)
          completed.continuation.continuation.context.summary shouldBe input
          completed.retained shouldBe WorkflowCompletionRecordJsonV1.Record(entry.revision, result)
          take(claims(store).loadStoredC(identity)).get._2 shouldBe take(selected._2.nextC)
          stored(store)._1.getInt(WorkflowInstanceDataStorePersistence.revisionField) shouldBe Some(3)
          stored(store)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField) shouldBe before
          take(issued(store).loadC(identity)) shouldBe Some(large)
          take(completion(store).loadC(identity)) shouldBe Some(completed.retained)
        } finally store match {
          case sql: SqlDataStore => take(sql.closeC())
          case _ => ()
        }
        if (provider == "sqlite") {
          When("the SQLite database is physically reopened after closing the provider")
          val reopened = SqlDataStore.sqlite(db.toString)
          try {
            Then("the full issued input and its completed result and revisions recover intact")
            take(issued(reopened).loadC(identity)) shouldBe Some(large)
            take(completion(reopened).loadC(identity)) shouldBe Some(
              WorkflowCompletionRecordJsonV1.Record(entry.revision, result))
            take(root(reopened).load(config, current.identity)).get.revision shouldBe entry.revision
            take(claims(reopened).loadStoredC(identity)).get._1.status shouldBe ContinuationRuntimePersistence.Status.Completed
            take(claims(reopened).loadStoredC(identity)).get._1.continuation.context.summary shouldBe input
            take(claims(reopened).loadStoredC(identity)).get._2.value shouldBe 3L
            stored(reopened)._1.getInt(WorkflowInstanceDataStorePersistence.revisionField) shouldBe Some(3)
          } finally take(reopened.closeC())
        }
      }
    }
  }

  private def seedBytes(): String = {
    val work = issue(intent(initial))
    take(WorkflowCompletionRecordJsonV1.encodeC(
      WorkflowCompletionRecordJsonV1.Record(InstanceRevision(2L), submitted(work)), resultCodec))
  }
}
