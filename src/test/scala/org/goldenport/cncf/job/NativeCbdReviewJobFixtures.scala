package org.goldenport.cncf.job

import java.nio.file.Path
import java.time.Duration
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork}
import org.goldenport.protocol.operation.OperationResponse

/*
 * Test-only cross-project access to the original configured native fixture.
 *
 * @since   Oct.  6, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
object NativeCbdReviewJobFixtures {
  /** A complete CLI-bootstrap runtime, reopened only from persisted identifiers. */
  def openRuntime(
    root: Path,
    jobs: Vector[JobId],
    expectedSubmitter: JobSubmitter,
    components: Subsystem => Seq[org.goldenport.cncf.component.Component]
  ): Consequence[Runtime] = {
    val inputs = new JobDurableRuntimeFixtures.CliInputs(root, Some(expectedSubmitter))
    inputs.startup.entries = jobs.zipWithIndex.map { case (id, ordinal) =>
      JobDurableRuntimeFixtures.candidate(id, ordinal.toLong).copy(access = inputs.recordAccess, replayEvidence = DurableReplayEvidence())
    }
    new org.goldenport.cncf.cli.CncfRuntime().initializeHandle(
      org.goldenport.cncf.bootstrap.BootstrapConfig(cwd = root,
        args = JobDurableRuntimeFixtures.cliArguments(root),
        extraComponents = selected => inputs.extras(selected) ++ components(selected)))
      .map(handle => new Runtime(inputs, handle))
  }

  final case class StartupRefusal(message: String, executionAvailable: Boolean, drained: Int)

  /** Observe the actual full-bootstrap refusal without treating an unavailable handle as a live runtime. */
  def refusedStartup(
    root: Path,
    jobs: Vector[JobId],
    expectedSubmitter: JobSubmitter,
    components: Subsystem => Seq[org.goldenport.cncf.component.Component]
  ): Consequence[StartupRefusal] = {
    val inputs = new JobDurableRuntimeFixtures.CliInputs(root, Some(expectedSubmitter))
    inputs.startup.entries = jobs.zipWithIndex.map { case (id, ordinal) =>
      JobDurableRuntimeFixtures.candidate(id, ordinal.toLong).copy(access = inputs.recordAccess, replayEvidence = DurableReplayEvidence())
    }
    val runtime = new org.goldenport.cncf.cli.CncfRuntime()
    try runtime.initializeHandle(org.goldenport.cncf.bootstrap.BootstrapConfig(cwd = root,
      args = JobDurableRuntimeFixtures.cliArguments(root),
      extraComponents = selected => inputs.extras(selected) ++ components(selected))) match {
      case Consequence.Success(handle) =>
        handle.close()
        Consequence.stateInvalid("expected native startup refusal")
      case Consequence.Failure(conclusion) =>
        val engine = inputs.subsystem.get.jobEngine.asInstanceOf[InMemoryJobEngine]
        Consequence.success(StartupRefusal(conclusion.observation.getEffectiveMessage.getOrElse(conclusion.display),
          engine.durableOperationalHealth.exists(_.executionAvailable), engine.drainAll()))
    } finally runtime.closeEmbedding()
  }

  final class Runtime private[NativeCbdReviewJobFixtures](
    inputs: JobDurableRuntimeFixtures.CliInputs,
    handle: org.goldenport.cncf.bootstrap.CncfHandle
  ) {
    val subsystem: Subsystem = handle.subsystem
    val engine: JobEngine = subsystem.jobEngine
    val context: ExecutionContext = inputs.context
    def entityStoreSpace: org.goldenport.cncf.entity.EntityStoreSpace =
      new org.goldenport.cncf.entity.EntityStoreSpace().addEntityStore(inputs.entity)
    def managed[A](body: => Consequence[A]): Consequence[A] =
      subsystem.jobEngine.asInstanceOf[InMemoryJobEngine]._durable_runtime_owner_option.get._execute_c(body)
    def drain(): Consequence[Int] = managed(Consequence.success(engine.asInstanceOf[InMemoryJobEngine].drainAll()))
    def signedStatus(id: JobId): String = managed {
      val owner = subsystem.jobEngine.asInstanceOf[InMemoryJobEngine]._durable_runtime_owner_option.get
      owner._store.load(id.value, inputs.recordAccess)(using context).map(_.get.record.body.lifecycle.status.wire)
    }.TAKE
    def unitOfWork(context: ExecutionContext, events: EventEngine, recorder: CommitRecorder): UnitOfWork =
      new UnitOfWork(context, events, recorder)
    def close(): Unit = handle.close()
  }

  def withFixture[A](body: Fixture => A): A =
    JobDurableRuntimeFixtures.withFixture(native => body(new Fixture(native)))

  final class Fixture private[NativeCbdReviewJobFixtures] (
    private val _native: JobDurableRuntimeFixtures.Fixture
  ) {
    private var _terminal_failure = false
    _native.selectedEvidence = new DurableJobKeyedEvidenceSource {
      def resolve(request: DurableJobKeyedEvidenceRequest): Consequence[DurableJobKeyedEvidenceResponse] = {
        val supplied = DurableJobKeyedEvidenceFixtures.pair(request, DurableJobKeyedEvidenceFixtures.recipe())
        val evidence = if (_terminal_failure && request.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) {
          supplied.copy(projection = supplied.projection.copy(
            result = DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "failed", false)),
            retry = supplied.projection.retry.copy(recoveryRequired = true, attempts = supplied.projection.retry.attempts.map(
              _.copy(outcome = DurableAttemptOutcome.Failed,
                failure = Some(DurableFailureSummary("execution", "failed", "failed", false)))))))
        } else supplied
        Consequence.success(DurableJobKeyedEvidenceResponse(request.jobid, request.expectedrevision, evidence))
      }
    }
    /** Explicit scenario evidence, independent of the engine outcome being asserted. */
    def expectTerminalFailure(): Unit = { _terminal_failure = true }
    def root: Path = _native.root
    def engine: JobEngine = _native.engine
    def context: ExecutionContext = _native.context
    def subsystem: Subsystem = _native.subsystem
    def issuedIds: Int = _native.ids.calls.get()
    /** Numeric native paid/held evidence only; no private record or token escapes. */
    def budgetUsage: Consequence[Option[Vector[Long]]] = managed {
      val id = JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaEntity.entityId)
      val collection = DataStore.CollectionId.EntityStore(id.collection)
      _native.context.dataStoreSpace.dataStore(collection).flatMap(_.load(collection, DataStore.EntryId(id))(using context)).map {
        _.map { row =>
          val record = JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaCodec.decode(row.getString("canonicalJson").get))
          val usage = JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaCodec.usage(record))
          Vector(usage.ledgerjobs.toLong, usage.heldbytes, usage.chargedtasks, usage.historycredits,
            usage.admissions, usage.cancelcredits, usage.maxchargedtasks, usage.maxhistorycredits,
            usage.maxadmissions, usage.maxledgerjobs.toLong, usage.maxledgerbytes)
        }
      }
    }
    def observedContexts: Vector[ExecutionContext] = _native.entity.contexts
    def entityStoreSpace: org.goldenport.cncf.entity.EntityStoreSpace =
      new org.goldenport.cncf.entity.EntityStoreSpace().addEntityStore(_native.entity)
    def cancellationScope(jobId: JobId): Option[JobCancellationScope] =
      Option(_native.engine._cancellation_scopes.get(jobId))
    def start(): Consequence[Unit] = _native.start()
    def commit(context: ExecutionContext): Consequence[UnitOfWork.CommitResult] = context.runtime.commitC()
    def abort(context: ExecutionContext): Consequence[UnitOfWork.AbortResult] = context.runtime.abortC()
    def dispose(context: ExecutionContext): Unit = context.runtime.dispose()
    def managed[A](body: => Consequence[A]): Consequence[A] = _native.owner._execute_c(body)
    def advance(duration: Duration): Unit = _native.advance(duration)
    def drain(): Consequence[Int] = managed(Consequence.success(_native.engine.drainAll()))
    def signedStatus(jobId: JobId): String = _native.signed(jobId).record.body.lifecycle.status.wire
    def signedResult(jobId: JobId): Consequence[Option[OperationResponse]] = managed {
      val _ = _native.signed(jobId)
      Consequence.success(_native.engine.query(jobId).flatMap(_.result))
    }
    def unitOfWork(context: ExecutionContext, events: EventEngine, recorder: CommitRecorder): UnitOfWork =
      new UnitOfWork(context, events, recorder)
  }
}
