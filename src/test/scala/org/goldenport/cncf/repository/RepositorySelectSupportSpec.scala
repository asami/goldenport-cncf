package org.goldenport.cncf.repository

import cats.{Id, ~>}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ExecutionContextId, ObservabilityContext, RuntimeContext, SystemContext, TraceId}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.datastore.{DataStore, OrderDirection, Query, QueryDirective, QueryLimit, QueryOrder, QueryProjection, ResultRange}
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  6, 2026
 * @version Jan. 10, 2026
 * @author  ASAMI, Tomoharu
 */
class RepositorySelectSupportSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "RepositorySupport select helpers" should {
    "return limited records with range metadata" in {
      val (ctx, datastore) = _context_()
      _seed_(datastore)
      val repo = new TestRepo(ctx)

      val result = repo.listForView(
        QueryDirective(
          query = Query.Empty,
          projection = QueryProjection.All,
          order = QueryOrder.None,
          limit = QueryLimit.Limit(2)
        )
      )

      result.records.size shouldBe 2
      result.range shouldBe ResultRange.Limited(2)
    }

    "apply projection fields" in {
      val (ctx, datastore) = _context_()
      _seed_(datastore)
      val repo = new TestRepo(ctx)

      val result = repo.listForView(
        QueryDirective(
          query = Query.Empty,
          projection = QueryProjection.Fields(Vector("state")),
          order = QueryOrder.None,
          limit = QueryLimit.Unbounded
        )
      )

      result.records.foreach { r =>
        r.keySet shouldBe Set("state")
      }
    }

    "apply ordering by field" in {
      val (ctx, datastore) = _context_()
      _seed_(datastore)
      val repo = new TestRepo(ctx)

      val asc = repo.listForView(
        QueryDirective(
          query = Query.Empty,
          projection = QueryProjection.All,
          order = QueryOrder.By("name", OrderDirection.Asc),
          limit = QueryLimit.Unbounded
        )
      )

      asc.records.map(_.getString("name").getOrElse(fail("missing name"))) shouldBe Vector("a", "b", "c")

      val desc = repo.listForView(
        QueryDirective(
          query = Query.Empty,
          projection = QueryProjection.All,
          order = QueryOrder.By("name", OrderDirection.Desc),
          limit = QueryLimit.Unbounded
        )
      )

      desc.records.map(_.getString("name").getOrElse(fail("missing name"))) shouldBe Vector("c", "b", "a")
    }
  }

  private final class TestRepo(
    val executionContext: ExecutionContext
  ) extends RepositorySupport {
    def listForView(directive: QueryDirective) =
      selectForView(directive)
  }

  private def _context_(): (ExecutionContext, DataStore) = {
    val recorder = new InMemoryCommitRecorder
    val datastore = DataStore.inMemorySelectable(recorder)
    val eventengine = EventEngine.noop(datastore, recorder)
    val runtime = new TestRuntimeContext
    val base = ExecutionContext.create()
    val ctx = ExecutionContext.Instance(
      base.core,
      base.cncfCore.copy(runtime = runtime.runtime, system = SystemContext.empty)
    )
    val uow = new UnitOfWork(ctx, datastore, eventengine, recorder)
    runtime.bind(uow)
    (ctx, datastore)
  }

  private def _seed_(datastore: DataStore): Unit = {
    val a = new UniversalId("cncf", "repo", "record") {}
    val b = new UniversalId("cncf", "repo", "record") {}
    val c = new UniversalId("cncf", "repo", "record") {}
    datastore.create(a, Record.data("name" -> "b", "state" -> "s1"))
    datastore.create(b, Record.data("name" -> "a", "state" -> "s2"))
    datastore.create(c, Record.data("name" -> "c", "state" -> "s3"))
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

  private def _testObservabilityContext(): ObservabilityContext = {
    val id = ExecutionContextId.generate()
    ObservabilityContext(
      traceId = TraceId(id),
      spanId = None,
      correlationId = Some(CorrelationId(id))
    )
  }
}
