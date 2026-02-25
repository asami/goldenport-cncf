package org.goldenport.cncf.repository

import cats.{Id, ~>}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ExecutionContextId, ObservabilityContext, RuntimeContext, TraceId}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.datastore.{DataStore, OrderDirection, Query, QueryDirective, QueryLimit, QueryOrder, QueryProjection, ResultRange}
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  6, 2026
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class RepositorySelectSupportSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "RepositorySupport select helpers" should {
    "return limited records with range metadata" in {
      // TODO Re-enable after in-memory searchable datastore behavior is implemented in production code.
      pending
    }

    "apply projection fields" in {
      // TODO Re-enable after in-memory searchable datastore behavior is implemented in production code.
      pending
    }

    "apply ordering by field" in {
      // TODO Re-enable after in-memory searchable datastore behavior is implemented in production code.
      pending
    }
  }

  private final class TestRepo(
    val executionContext: ExecutionContext
  ) extends RepositorySupport {
    def listForView(directive: QueryDirective) =
      searchForView(DataStore.CollectionId("record"), directive)
  }

  private def _context_(): (ExecutionContext, DataStore) = {
    val recorder = new InMemoryCommitRecorder
    val datastore = new InMemorySearchableDataStore(recorder)
    val eventengine = EventEngine.noop(datastore, recorder)
    val runtime = new TestRuntimeContext
    val base = ExecutionContext.create()
    val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
    val uow = new UnitOfWork(ctx, datastore, org.goldenport.cncf.entity.EntityStore.noop(), eventengine, recorder)
    runtime.bind(uow)
    (ctx, datastore)
  }

  private def _seed_(datastore: DataStore)(using ctx: ExecutionContext): Unit = {
    val collection = DataStore.CollectionId("record")
    datastore.create(collection, DataStore.StringEntryId("a"), Record.data("name" -> "b", "state" -> "s1"))
    datastore.create(collection, DataStore.StringEntryId("b"), Record.data("name" -> "a", "state" -> "s2"))
    datastore.create(collection, DataStore.StringEntryId("c"), Record.data("name" -> "c", "state" -> "s3"))
  }

  private final class TestRuntimeContext {
    private var _unit_of_work: Option[UnitOfWork] = None
    private val observability = _testObservabilityContext()
    private val driver = FakeHttpDriver.okText("nop")

    val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "repository-select-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver)
      ),
      unitOfWorkSupplier = () => _unit_of_work.getOrElse {
        throw new IllegalStateException("UnitOfWork has not been bound")
      },
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in repository spec")
      },
      unitOfWorkTryInterpreterFn = new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in repository spec")
      },
      unitOfWorkEitherInterpreterFn = new (UnitOfWorkOp ~> RuntimeContext.EitherThrowable) {
        def apply[A](op: UnitOfWorkOp[A]): Either[Throwable, A] =
          Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in repository spec"))
      },
      commitAction = uow => {
        val _ = uow.commit()
        ()
      },
      abortAction = abortUow => {
        val _ = abortUow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "repository-select-runtime-context"
    )

    def bind(uow: UnitOfWork): Unit =
      _unit_of_work = Some(uow)
  }

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message
  }

  private final class InMemorySearchableDataStore(
    recorder: CommitRecorder
  ) extends org.goldenport.cncf.datastore.SearchableDataStore {
    private var entries: Map[DataStore.CollectionId, Map[DataStore.EntryId, Record]] = Map.empty

    private def _collection(
      collection: DataStore.CollectionId
    ): Map[DataStore.EntryId, Record] =
      entries.getOrElse(collection, Map.empty)

    private def _update_collection(
      collection: DataStore.CollectionId,
      values: Map[DataStore.EntryId, Record]
    ): Unit =
      entries = entries.updated(collection, values)

    def create(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: ExecutionContext): org.goldenport.Consequence[Unit] = org.goldenport.Consequence {
      val c = _collection(collection)
      _update_collection(collection, c.updated(id, record))
      ()
    }

    def load(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: ExecutionContext): org.goldenport.Consequence[Option[Record]] =
      org.goldenport.Consequence(_collection(collection).get(id))

    def save(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: ExecutionContext): org.goldenport.Consequence[Unit] =
      create(collection, id, record)

    def update(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      changes: Record
    )(using ctx: ExecutionContext): org.goldenport.Consequence[Unit] = org.goldenport.Consequence {
      val c = _collection(collection)
      val next = c.get(id).map(_ ++ changes).getOrElse(changes)
      _update_collection(collection, c.updated(id, next))
      ()
    }

    def delete(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: ExecutionContext): org.goldenport.Consequence[Unit] = org.goldenport.Consequence {
      val c = _collection(collection)
      _update_collection(collection, c - id)
      ()
    }

    def search(
      collection: DataStore.CollectionId,
      directive: QueryDirective
    ): org.goldenport.Consequence[org.goldenport.cncf.datastore.SearchResult] =
      org.goldenport.Consequence {
        val base = _collection(collection).values.toVector
        val projected = directive.projection match {
          case QueryProjection.All =>
            base
          case QueryProjection.Fields(names) =>
            val keys = names.toSet
            base.map(_.filterKeys(keys))
        }
        val ordered = directive.order match {
          case QueryOrder.None =>
            projected
          case QueryOrder.By(field, OrderDirection.Asc) =>
            projected.sortBy(_.getString(field).getOrElse(""))
          case QueryOrder.By(field, OrderDirection.Desc) =>
            projected.sortBy(_.getString(field).getOrElse(""))(Ordering[String].reverse)
        }
        directive.limit match {
          case QueryLimit.Unbounded =>
            org.goldenport.cncf.datastore.SearchResult(ordered, ResultRange.Exact, None)
          case QueryLimit.Limit(n) =>
            org.goldenport.cncf.datastore.SearchResult(ordered.take(n), ResultRange.Limited(n), None)
        }
      }

    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): org.goldenport.cncf.unitofwork.PrepareResult = {
      recorder.record("DataStore.prepare")
      org.goldenport.cncf.unitofwork.PrepareResult.Prepared
    }

    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit =
      recorder.record("DataStore.commit")

    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit =
      recorder.record("DataStore.abort")
  }

  private def _testObservabilityContext(): ObservabilityContext =
    ObservabilityContext(
      traceId = TraceId("repository", "select"),
      spanId = None,
      correlationId = Some(CorrelationId("repository", "runtime"))
    )
}
