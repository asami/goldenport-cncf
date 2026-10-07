package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowManagedIssuedWorkSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"managed issued-work fixture rejected: $failure")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val producer = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
  private val definition = take(bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
    resource("/workflow/candidate-admission-workflow-abi.json").trim, producer,
    "modeler/candidate-admission-workflow.cml"))))
  private val component = ComponentId("org.goldenport.cncf.test.ManagedIssuedWork")
  private val completion = StateMachineOperationIdentity("workflow", "submitReview")
  private def collection(name: String) = DataStore.CollectionId.EntityStore(
    EntityCollectionId("cncf", "managedissue", name))
  private val instances = collection("instances")
  private val continuations = collection("continuations")
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("managed-issue")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("managed-issue")))
  private val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "review.input.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("expected text")
  }
  private val policy = take(EventPersistencePolicy.createC("managed-issue-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(component, "workflow", instances, continuations))))
  private def host(path: Path) = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
    ScopeContext(ScopeKind.Subsystem, "managed-issue", None, context.observability),
    ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)))
  private def bind(host: SubsystemEventStoreBootstrap) = take(WorkflowManagedIssuedWork.bindC(
    host.subsystem, component, config, completion, Vector(definition), instances, continuations, codec))
  private def root(host: SubsystemEventStoreBootstrap) = take(WorkflowInstancePersistenceBinding.bindC(
    component, host.subsystem, ComponentDataStore.Environment(context.runtime.resolvedParameters,
      Some(host.subsystem.configuration)),
    WorkflowInstancePersistenceBinding.Declaration(config, Vector(definition), instances)))
  private val initial = InstanceRecord(InstanceIdentity("instance-1"), definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("correlation-1"), CausationReference("start-1"),
    None, None, None, Vector.empty)
  private val identity = ContinuationIdentity("continuation-1")
  private def intent = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"),
      Some(StateMachineInputTypeReference(codec.typeIdentity)), Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty), EvidenceContract("review.evidence", Vector.empty), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("run-1"),
      identity, StateMachineRevision("machine-rev-1"), required,
      ContextBundle("review context", Vector.empty, Vector.empty, ContextSnapshot(definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), initial.correlation, initial.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(identity.value), InstanceRevision(1L),
        ContextSnapshotReference(definition.workflowRevision.value), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("resume-1"))))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      config, initial, initialRevision, entry, continuation)
  }
  private def issue(request: WorkflowInstanceAtomicTransitionV1.SuspensionIntent) = {
    val c = request.continuation.continuation
    WorkflowInteraction(take(WorkflowHandle.fromRecordC(component, initial)),
      WorkflowContinuation.WorkOrder(ContinuationRequest(c.runId, c.continuationId, c.expectedRevision,
        c.requiredOperation.identity, c.requiredOperation.operation, Some(TypedValue(codec.typeIdentity, "exact input")),
        c.requiredOperation.resultType, c.context, c.requiredOperation.metadata.completionContract,
        c.requiredOperation.metadata.evidenceContract, completion),
        ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"),
          ReasoningLevel.Deep, true), MinimalPresentation("Review", "Awaiting result")))
  }

  "Managed issued-work host binding" should {
    "atomically publish a typed Decision and recover its exact kind after physical reopen" in {
      Given("a captured shared-store host and a separate native Decision binding")
      val path = Files.createTempDirectory("managed-issued-decision-").resolve("host.sqlite")
      val first = host(path)
      val request = intent
      val work = issue(request)
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      val decision = WorkflowInteraction[String, Nothing](work.handle,
        WorkflowContinuation.Decision(order.request, MinimalPresentation("Choose", "No command grant")))
      def decisions(current: SubsystemEventStoreBootstrap) = take(WorkflowManagedIssuedDecision.bindC(
        current.subsystem, component, config, completion, Vector(definition), instances, continuations, codec))
      val issued = decisions(first)
      try {
        take(first.initializeCursorC())
        val persistence = root(first)
        take(persistence.createC(initial))
        When("a Decision is submitted through the WorkOrder binding or retry-only path")
        bind(first).suspendC(request, decision) shouldBe a[Consequence.Failure[?]]
        issued.putIfAbsentC(decision) shouldBe a[Consequence.Failure[?]]
        Then("no instance revision or Continuation is published")
        take(persistence.loadC(initial.identity)) shouldBe Some(initial)
        take(issued.loadC(identity)) shouldBe None
        When("the Decision binding atomically publishes the exact issued question")
        val saved = take(issued.suspendC(request, decision)).instance
        Then("native suspension and issued Decision are read back together with no extra pool or retained lease")
        saved.history shouldBe Vector(request.entry)
        take(issued.loadC(identity)) shouldBe Some(decision)
        take(issued.putIfAbsentC(decision)) shouldBe decision
        bind(first).loadC(identity) shouldBe a[Consequence.Failure[?]]
        bind(first).putIfAbsentC(work) shouldBe a[Consequence.Failure[?]]
        issued.suspendC(request, decision) shouldBe a[Consequence.Failure[?]]
        val changed = decision.copy(current = WorkflowContinuation.Decision(order.request,
          MinimalPresentation("Changed", "Different visible question")))
        issued.putIfAbsentC(changed) shouldBe a[Consequence.Failure[?]]
        take(persistence.loadC(initial.identity)) shouldBe Some(saved)
        first.subsystem.systemNode.managedDataStoreCount shouldBe 1
        first.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(first.shutdownC())
      issued.loadC(identity) shouldBe a[Consequence.Failure[?]]
      When("a fresh host opens the same physical database")
      val reopened = host(path)
      try {
        Then("the exact Decision and one native suspension remain; WorkOrder readers still refuse its kind")
        take(decisions(reopened).loadC(identity)) shouldBe Some(decision)
        take(decisions(reopened).putIfAbsentC(decision)) shouldBe decision
        take(root(reopened).loadC(initial.identity)).get.history shouldBe Vector(request.entry)
        bind(reopened).loadC(identity) shouldBe a[Consequence.Failure[?]]
        reopened.subsystem.systemNode.managedDataStoreCount shouldBe 1
        reopened.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(reopened.shutdownC())
    }

    "refuse WorkOrder, stop-only Decision and foreign grants through the Decision binding before publication" in {
      Given("an initial instance under the captured Workflow grant")
      val current = host(Files.createTempDirectory("managed-decision-refusal-").resolve("host.sqlite"))
      try {
        def attempt(owner: ComponentId = component, roots: DataStore.CollectionId = instances) =
          WorkflowManagedIssuedDecision.bindC(current.subsystem, owner, config, completion,
            Vector(definition), roots, continuations, codec)
        val issued = take(attempt())
        val persistence = root(current)
        take(persistence.createC(initial))
        val request = intent
        val work = issue(request)
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
        val stop = WorkflowInteraction[String, Nothing](work.handle,
          WorkflowContinuation.Decision(order.request.copy(resultType = None), order.presentation))
        When("a different kind, a stop-only question, or an uncaptured owner/collection is offered")
        issued.suspendC(request, work) shouldBe a[Consequence.Failure[?]]
        issued.suspendC(request, stop) shouldBe a[Consequence.Failure[?]]
        attempt(roots = collection("foreign")) shouldBe a[Consequence.Failure[?]]
        attempt(owner = ComponentId("org.goldenport.cncf.test.Foreign")) shouldBe a[Consequence.Failure[?]]
        Then("the stored instance is unchanged, no issued question exists and the lease is returned")
        take(persistence.loadC(initial.identity)) shouldBe Some(initial)
        take(issued.loadC(identity)) shouldBe None
        current.subsystem.systemNode.managedDataStoreCount shouldBe 1
        current.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(current.shutdownC())
    }

    "query without claims or extra managed resources and reject a stopped host" in {
      Given("one host-owned managed resource and a bound read-only completion capability")
      val current = host(Files.createTempDirectory("managed-completion-query-").resolve("host.sqlite"))
      var eventCalls = 0
      val resultCodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "review.result.v1"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected result text")
      }
      val reader = take(WorkflowManagedCompletion.bindC(current.subsystem, component, config, completion,
        Vector(definition), instances, continuations, codec, resultCodec,
        StateMachineResultTypeReference(resultCodec.typeIdentity),
        (_, _) => { eventCalls += 1; Consequence.success(Vector.empty) }))
      try {
        When("the query observes an absent completion before and after managed work issuance")
        take(reader.loadRetainedC(identity)) shouldBe None
        val persistence = root(current)
        take(persistence.createC(initial))
        val request = intent
        val work = issue(request)
        val issued = bind(current)
        val suspended = take(issued.suspendC(request, work)).instance
        take(reader.loadRetainedC(identity)) shouldBe None
        reader.loadRetainedC(null) shouldBe a[Consequence.Failure[?]]
        Then("the query neither advances the instance nor retains leases, dispatches events or creates another pool")
        take(persistence.loadC(initial.identity)) shouldBe Some(suspended)
        take(issued.loadC(identity)) shouldBe Some(work)
        eventCalls shouldBe 0
        current.subsystem.systemNode.managedDataStoreCount shouldBe 1
        current.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(current.shutdownC())
      When("the physical owner has shut down")
      val stopped = reader.loadRetainedC(identity)
      Then("the retained binding cannot reopen a private resource or borrow another lease")
      stopped shouldBe a[Consequence.Failure[?]]
      current.subsystem.systemNode.activeLeaseCount shouldBe 0
      eventCalls shouldBe 0
    }

    "admit completion against the same captured grant without invoking effects" in {
      val current = host(Files.createTempDirectory("managed-completion-admission-").resolve("host.sqlite"))
      var eventsCalled = false
      try {
        def attempt(owner: ComponentId = component, configured: Configuration = config,
          roots: DataStore.CollectionId = instances, successors: DataStore.CollectionId = continuations,
          catalog: Vector[DefinitionBinding] = Vector(definition)) =
          WorkflowManagedCompletion.bindC(current.subsystem, owner, configured, completion,
            catalog, roots, successors, codec, codec, StateMachineResultTypeReference(codec.typeIdentity),
            (_, _) => { eventsCalled = true; Consequence.success(Vector.empty) })
        take(attempt())
        attempt(roots = collection("foreign")).toOption shouldBe None
        attempt(successors = collection("foreign")).toOption shouldBe None
        attempt(owner = ComponentId("org.goldenport.cncf.test.Foreign")).toOption shouldBe None
        attempt(configured = config.copy(workflowStore = NamedWorkflowStore("foreign", config.workflowStore.identity)))
          .toOption shouldBe None
        attempt(catalog = Vector(definition, definition)).toOption shouldBe None
        val crossStore = config.copy(delivery = DeliveryConfiguration.CrossStoreCommittedTransition(
          WorkflowStoreIdentity("other"), CommittedEntityTransitionReference("transition"),
          IdempotencyKey("key"), RecoverabilityReference("recovery")))
        attempt(configured = crossStore).toOption shouldBe None
        eventsCalled shouldBe false
        current.subsystem.systemNode.managedDataStoreCount shouldBe 1
        current.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(current.shutdownC())
    }

    "atomically issue and recover exact work after owned-host shutdown and reopen" in {
      val path = Files.createTempDirectory("managed-issued-").resolve("host.sqlite")
      val first = host(path)
      val request = intent
      val work = issue(request)
      val issued = bind(first)
      try {
        take(first.initializeCursorC())
        val persistence = root(first)
        take(persistence.createC(initial))
        issued.putIfAbsentC(work).toOption shouldBe None
        take(issued.loadC(identity)) shouldBe None
        take(issued.suspendC(request, work)).instance.history shouldBe Vector(request.entry)
        take(issued.issuedPersistence.loadC(identity)) shouldBe Some(work)
        take(issued.putIfAbsentC(work)) shouldBe work
        issued.suspendC(request, work).toOption shouldBe None
        first.subsystem.systemNode.managedDataStoreCount shouldBe 1
        first.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(first.shutdownC())
      issued.loadC(identity).toOption shouldBe None
      val reopened = host(path)
      try {
        take(bind(reopened).loadC(identity)) shouldBe Some(work)
        take(root(reopened).loadC(initial.identity)).get.history shouldBe Vector(request.entry)
        reopened.subsystem.systemNode.managedDataStoreCount shouldBe 1
        reopened.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(reopened.shutdownC())
    }

    "reject foreign collections, store and Component against captured declaration" in {
      val current = host(Files.createTempDirectory("managed-issued-admission-").resolve("host.sqlite"))
      try {
        def attempt(owner: ComponentId = component, configured: Configuration = config,
          roots: DataStore.CollectionId = instances, successors: DataStore.CollectionId = continuations) =
          WorkflowManagedIssuedWork.bindC(current.subsystem, owner, configured, completion,
            Vector(definition), roots, successors, codec)
        attempt(roots = collection("foreign")).toOption shouldBe None
        attempt(successors = collection("foreign")).toOption shouldBe None
        attempt(owner = ComponentId("org.goldenport.cncf.test.Foreign")).toOption shouldBe None
        attempt(configured = config.copy(workflowStore = NamedWorkflowStore("foreign", config.workflowStore.identity)))
          .toOption shouldBe None
        val issued = bind(current)
        issued.suspendC(intent.copy(configuration = config.copy(retentionPolicy = RetentionPolicy("foreign"))),
          issue(intent)).toOption shouldBe None
        take(issued.loadC(identity)) shouldBe None
        current.subsystem.systemNode.managedDataStoreCount shouldBe 1
        current.subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally take(current.shutdownC())
    }
  }
}
