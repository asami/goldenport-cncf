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
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object NativeCbdReviewJobFixtures {
  def withFixture[A](body: Fixture => A): A =
    JobDurableRuntimeFixtures.withFixture(native => body(new Fixture(native)))

  final class Fixture private[NativeCbdReviewJobFixtures] (
    private val _native: JobDurableRuntimeFixtures.Fixture
  ) {
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
