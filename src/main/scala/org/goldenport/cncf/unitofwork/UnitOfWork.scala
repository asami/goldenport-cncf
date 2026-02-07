package org.goldenport.cncf.unitofwork

import scala.util.{Try, Success, Failure}
import java.io.File
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, QueryDirective, SelectResult, SelectableDataStore}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.entity.EntityStore.*
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.process.{LocalShellCommandExecutor, ShellCommandExecutor}

/*
 * @since   Apr. 11, 2025
 *  version Dec. 21, 2025
 *  version Jan. 18, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWork(
  context: ExecutionContext,
  val datastore: DataStore = DataStore.noop(),
  eventengine: EventEngine = EventEngine.noop(DataStore.noop()),
  recorder: CommitRecorder = CommitRecorder.noop
) {
  import UnitOfWork.*
  private var _http_driver: Option[HttpDriver] = None
  private var _shell_command_executor: Option[ShellCommandExecutor] = None

  def create[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Try[CreateResult[T]] = ???

  def load[T](store: EntityStore[T])(using instance: EntityInstance[T]): Try[GetResult[T]] = ???

  def select[T](store: EntityStore[T], directive: QueryDirective)(using instance: EntityInstance[T]): Try[SelectResult] = ???

  def store[T](store: EntityStore[T], id: EntityId, data: Record)(using instance: EntityInstance[T]): Try[UpdateResult[T]] = ???

  def update[T](store: EntityStore[T], id: EntityId, changes: Record)(using instance: EntityInstance[T]): Try[UpdateResult[T]] = ???

  def delete[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Try[DeleteResult[T]] = ???

  def selectableDatastore: Option[SelectableDataStore] =
    datastore match {
      case s: SelectableDataStore => Some(s)
      case _ => None
    }

  def http_driver: Option[HttpDriver] =
    _http_driver.orElse(Some(context.runtime.httpDriver))

  def shellCommandExecutor: ShellCommandExecutor =
    _shell_command_executor.getOrElse {
      val executor = new LocalShellCommandExecutor
      _shell_command_executor = Some(executor)
      executor
    }

  def withHttpDriver(driver: Option[HttpDriver]): UnitOfWork = {
    _http_driver = driver
    this
  }

  def withShellCommandExecutor(executor: ShellCommandExecutor): UnitOfWork = {
    _shell_command_executor = Some(executor)
    this
  }

  def execute[A](op: UnitOfWorkOp[A])(using http: org.goldenport.cncf.http.HttpDriver): A =
    withHttpDriver(Some(http))
    new UnitOfWorkInterpreter(this).execute(op).asInstanceOf[A]

  def createFile(file: File, data: String): Try[Unit] = ???

  def sendMessage(msg: Message): Try[Unit] = ???

  def commit(): Consequence[CommitResult] =
    commit(Nil)

  def commit(
    events: Seq[DomainEvent]
  ): Consequence[CommitResult] =
    try {
      val tx = TransactionContext.create()
      eventengine.stage(events)
      recorder.record("UnitOfWork.prepare")
      val prepares = List(
        datastore.prepare(tx),
        eventengine.prepare(tx)
      )
      prepares.collectFirst {
        case PrepareResult.Rejected(reason) => reason
      } match {
        case Some(reason) =>
          recorder.record("UnitOfWork.abort")
          eventengine.abort(tx)
          datastore.abort(tx)
          Consequence.failure(reason)
        case None =>
          recorder.record("UnitOfWork.commit")
          datastore.commit(tx)
          eventengine.commit(tx)
          Consequence.success(())
      }
    } catch {
      case e: Throwable =>
        Consequence.Failure(Conclusion.from(e))
    }

  def abort(): Consequence[AbortResult] =
    try {
      val tx = TransactionContext.create()
      recorder.record("UnitOfWork.abort")
      eventengine.abort(tx)
      datastore.abort(tx)
      Consequence.success(())
    } catch {
      case e: Throwable =>
        Consequence.Failure(Conclusion.from(e))
    }

  def rollback(): Consequence[AbortResult] =
    abort()

  def record(message: String): Unit =
    recorder.record(message)
}

object UnitOfWork {
  type CommitResult = Unit
  type AbortResult = Unit
  type Message = String

  def simple(datastore: DataStore): UnitOfWork = {
    val base = ExecutionContext.create() // ExecutionContext.createWithSystem(SystemContext.empty)
    val eventengine = EventEngine.noop(datastore)
    new UnitOfWork(base, datastore, eventengine)
  }
}
