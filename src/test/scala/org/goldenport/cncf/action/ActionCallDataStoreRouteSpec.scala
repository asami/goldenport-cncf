package org.goldenport.cncf.action

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext, SystemContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.ConsequenceMatchers

/*
 * @since   Jan.  6, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionCallDataStoreRouteSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with TableDrivenPropertyChecks
  with ConsequenceMatchers {

  "ActionCall datastore helpers" should {
    "route CRUD via UnitOfWork and DataStore" in {
      val table = Table(
        ("id", "value"),
        ("a1", "alpha"),
        ("b2", "bravo"),
        ("c3", "charlie")
      )

      forAll(table) { (id, value) =>
        Given("an ExecutionContext with an in-memory UnitOfWork/DataStore")
        val recorder = new InMemoryCommitRecorder
        val datastore = DataStore.inMemory(recorder)
        val eventengine = EventEngine.noop(datastore, recorder)
        val runtime = new TestRuntimeContext
        val base = ExecutionContext.create()
        val ctx = ExecutionContext.Instance(
          base.core,
          base.cncfCore.copy(runtime = runtime, system = SystemContext.empty)
        )
        val uow = new UnitOfWork(ctx, datastore, eventengine, recorder)
        runtime.bind(uow)

        When("an ActionCall uses the protected datastore helpers")
        val entityid = new UniversalId("cncf", "datastore", "record") {}
        val call = _build_action_call_(entityid, id, value, ctx)
        val result = call.execute()

        Then("the ActionCall receives the stored value via UnitOfWork")
        result should be_success
        result match {
          case Consequence.Success(OperationResponse.Scalar(v: String)) =>
            v shouldBe value
          case other =>
            fail(s"unexpected response: ${other}")
        }

        And("the datastore record is removed after delete")
        datastore.load(entityid) shouldBe None

        And("commit is invoked via UnitOfWork on success")
        val commitresult = call.commit()
        commitresult should be_success
        recorder.entries.headOption shouldBe Some("ActionCall.commit")
      }
    }
  }

  private def _build_action_call_(
    entityid: UniversalId,
    id: String,
    value: String,
    ctx: ExecutionContext
  ): ActionCall = {
    val action = new Command() {
      val name = "datastore-action"
      def createCall(core: ActionCall.Core): ActionCall =
        new DataStoreActionCall(core, entityid, id, value)
    }
    val core = ActionCall.Core(action, ctx, None)
    action.createCall(core)
  }

  private final class DataStoreActionCall(
    override val core: ActionCall.Core,
    entityid: UniversalId,
    id: String,
    value: String
  ) extends ActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val record = Record.data("id" -> id, "value" -> value)
      // DataStore ops in UnitOfWorkOp are not wired yet; use direct DataStore API.
      val datastore = executionContext.runtime.unitOfWork.datastore
      datastore.store(entityid, record)
      val v = datastore
        .load(entityid)
        .flatMap(_.getString("value"))
        .getOrElse("missing")
      datastore.delete(entityid)
      Consequence.success(OperationResponse.Scalar(v))
    }
  }

  private final class TestRuntimeContext extends RuntimeContext {
    private var _unit_of_work: UnitOfWork = null

    def bind(uow: UnitOfWork): Unit = {
      _unit_of_work = uow
    }

    def unitOfWork: UnitOfWork = _unit_of_work

    def unitOfWorkInterpreter[T]: (UnitOfWorkOp ~> Id) =
      new (UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in datastore route spec")
      }

    def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> scala.util.Try) =
      new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in datastore route spec")
      }

    def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] =
      Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in datastore route spec"))

    def commit(): Unit = {
      unitOfWork.commit()
      ()
    }

    def abort(): Unit = {
      unitOfWork.rollback()
      ()
    }

    def dispose(): Unit = {}

    def toToken: String = "datastore-route-runtime-context"
  }

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message

    def entries: Vector[String] =
      buffer.toVector
  }
}
