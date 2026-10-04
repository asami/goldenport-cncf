package org.goldenport.cncf.job

import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Target-owned SQLite and independently supplied immutable evidence recipes.
 * Request recording observes invocation order; requested revisions alone select evidence.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobKeyedEvidenceFixtures {
  import JobDurableLifetimeRecoveryFixtures.{Database, Owner, Port, descriptor, success}

  val boundaries: Vector[DurableJobLifecycleWriteBoundary] = Vector(
    DurableJobLifecycleWriteBoundary.Admission,
    DurableJobLifecycleWriteBoundary.StartIntent,
    DurableJobLifecycleWriteBoundary.RunningIntent,
    DurableJobLifecycleWriteBoundary.TaskStartIntent,
    DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
    DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)

  final case class Recipe(projection: DurableJobProjectionEvidence, access: DurableJobRecordAccess)

  def recipe(marker: String = "default"): Recipe = {
    val supplied = JobInputBoundsFixtures.evidence(1L)
    Recipe(supplied.projection.copy(definitionSnapshot = supplied.projection.definitionSnapshot.copy(
      definitionId = s"definition-$marker", key = s"key-$marker")), supplied.access)
  }

  def originalRecipe(record: DurableJobRecord): Recipe = {
    val body = record.body
    Recipe(DurableJobProjectionEvidence(body.identity.revision, body.authorization, body.tasks,
      body.inputs, body.result, body.lifecycle.retry, body.diagnostics, body.calltreeReference,
      body.definitionSnapshot, body.retention), JobInputBoundsFixtures.access)
  }

  def pair(request: DurableJobKeyedEvidenceRequest, recipe: Recipe): DurableJobLifecycleWriteEvidence = {
    val models = request.request match {
      case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
      case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
      case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
      case _ => Vector.empty
    }
    val terminal = request.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
    val retry = recipe.projection.retry.copy(nextRetryAt = None)
    val closedretry = if (terminal && models.nonEmpty) retry.copy(attempts = retry.attempts :+
      DurableAttemptEvidence(retry.attempts.size + 1,
        models.map(_.startedAt).reduce((left, right) => if (left.isBefore(right)) left else right),
        models.flatMap(_.finishedAt).reduceOption((left, right) => if (left.isAfter(right)) left else right),
        DurableAttemptOutcome.Succeeded, None)) else retry
    DurableJobLifecycleWriteEvidence(recipe.projection.copy(
      semanticRevision = request.expectedrevision,
      tasks = if (models.isEmpty) recipe.projection.tasks else models.map(descriptor),
      result = if (terminal) DurableResultOutcome.Succeeded(DurableValue.Absent) else DurableResultOutcome.Pending,
      retry = closedretry), recipe.access)
  }

  enum Fault {
    case Matching, WrongJob, WrongRevision, WrongProjection, MissingPair
  }

  final class Probe(result: DurableJobKeyedEvidenceRequest => Consequence[DurableJobKeyedEvidenceResponse])
      extends DurableJobKeyedEvidenceSource {
    private val _requests = new ConcurrentLinkedQueue[DurableJobKeyedEvidenceRequest]()
    def requests: Vector[DurableJobKeyedEvidenceRequest] = _requests.iterator().asScala.toVector
    def resolve(request: DurableJobKeyedEvidenceRequest): Consequence[DurableJobKeyedEvidenceResponse] = {
      _requests.add(request)
      result(request)
    }
  }

  final class Source(
    suppliedRecipe: JobId => Recipe,
    selectFault: DurableJobKeyedEvidenceRequest => Fault = _ => Fault.Matching,
    before: DurableJobKeyedEvidenceRequest => Unit = _ => ()
  ) extends DurableJobKeyedEvidenceSource {
    private val _requests = new ConcurrentLinkedQueue[DurableJobKeyedEvidenceRequest]()
    private val _pairs = new ConcurrentLinkedQueue[DurableJobLifecycleWriteEvidence]()
    def requests: Vector[DurableJobKeyedEvidenceRequest] = _requests.iterator().asScala.toVector
    def pairs: Vector[DurableJobLifecycleWriteEvidence] = _pairs.iterator().asScala.toVector
    def resolve(request: DurableJobKeyedEvidenceRequest): Consequence[DurableJobKeyedEvidenceResponse] = {
      _requests.add(request)
      before(request)
      val supplied = pair(request, suppliedRecipe(request.jobid))
      _pairs.add(supplied)
      val response = DurableJobKeyedEvidenceResponse(request.jobid, request.expectedrevision, supplied)
      Consequence.success(selectFault(request) match {
        case Fault.Matching => response
        case Fault.WrongJob => response.copy(jobid = JobInputBoundsFixtures.id("foreign-response"))
        case Fault.WrongRevision => response.copy(expectedrevision = request.expectedrevision + 1L)
        case Fault.WrongProjection => response.copy(evidence = supplied.copy(projection =
          supplied.projection.copy(semanticRevision = request.expectedrevision + 1L)))
        case Fault.MissingPair => response.copy(evidence = null)
      })
    }
  }

  def bind(owner: Owner, source: DurableJobKeyedEvidenceSource): DurableJobLifecycleWriteBridge = {
    val bridge = new DurableJobLifecycleWriteBridge(owner.store, source, owner.policy)
    owner.engine.bindDurableLifecycleWriteBridge(bridge)
    bridge
  }

  def withDatabase(body: Database => Unit): Unit = {
    val parent = Files.createDirectories(Paths.get("target/keyed-durable-evidence/work"))
    val database = new Database(Files.createTempDirectory(parent, "case-"))
    try body(database) finally database.close()
  }

  def contextFor(owner: Owner, marker: String): ExecutionContext =
    ExecutionContext.withIdGenerationContext(owner.context, new IdGenerationContext {
      def namespace: IdGenerationContext.IdNamespace = owner.ids.namespace
      def entityId(collection: EntityCollectionId, purpose: String): EntityId = owner.ids.entityId(collection, purpose)
      def opaqueId(purpose: String): String =
        if (purpose == "job.submit") marker else owner.ids.opaqueId(purpose)
    })

  def recover(owner: Owner, jobId: JobId, tasks: List[JobTask]): Consequence[DurableJobRuntimeRehydrationReport] =
    owner.engine.recoverDurableStartup(JobDurableLifetimeRecoveryFixtures.candidates(jobId), owner.store,
      new Port(_ => DurableJobRuntimeRehydration(jobId, tasks, owner.context, None, None)), 1)(using owner.context)

  def ledger(owner: Owner): Option[DurableJobLifetimeQuotaRecord] = {
    val raw = success(owner.provider.load(DataStore.CollectionId.EntityStore(DurableJobLifetimeQuotaEntity.Collection),
      DataStore.EntryId(success(DurableJobLifetimeQuotaEntity.entityId)))(using owner.context))
    raw.map(value => success(DurableJobLifetimeQuotaCodec.decode(value.getString("canonicalJson").get)))
  }

  def protectedFacts(body: DurableJobRecordBody) =
    (body.authorization, body.inputs, body.definitionSnapshot, body.retention, body.calltreeReference)

  def await(latch: CountDownLatch): Unit =
    if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("keyed evidence barrier timed out")

  def join(actor: Thread): Unit = {
    actor.join(5000)
    if (actor.isAlive) {
      actor.interrupt()
      actor.join(5000)
    }
    if (actor.isAlive) throw new IllegalStateException("keyed evidence worker did not stop")
  }

  def model(status: JobTaskStatus): JobTaskReadModel =
    JobTaskReadModel(TaskId("cncf", "input", Some(JobInputBoundsFixtures.instant), Some("closed-task")),
      None, status, JobInputBoundsFixtures.instant,
      if (status == JobTaskStatus.Running) None else Some(JobInputBoundsFixtures.instant.plusMillis(1)),
      JobTaskResultSummary(true, None), Some("component-a"), Some("service-a"), Some("run"),
      "operation", Some("operation"), Some("root"), Some("own"), Some("per-task"),
      Some(if (status == JobTaskStatus.Running) "running" else "committed"))

  def requests(jobId: JobId): Vector[DurableJobLifecycleWriteRequest] = {
    val running = model(JobTaskStatus.Running)
    val completed = model(JobTaskStatus.Succeeded)
    Vector(DurableJobLifecycleWriteRequest.Admission, DurableJobLifecycleWriteRequest.StartIntent,
      DurableJobLifecycleWriteRequest.RunningIntent,
      DurableJobLifecycleWriteRequest.TaskStartIntent(jobId, running.taskId, None, Vector(running)),
      DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(jobId, completed.taskId, None, Vector(completed)),
      DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(jobId, Vector(completed)))
  }
}
