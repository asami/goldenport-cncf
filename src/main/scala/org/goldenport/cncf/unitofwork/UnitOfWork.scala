package org.goldenport.cncf.unitofwork

import scala.util.{Try, Success, Failure}
import java.io.File
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.datastore.{DataStore, SearchableDataStore}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.entity.CreateResult
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.process.{LocalShellCommandExecutor, ShellCommandExecutor}

/*
 * @since   Apr. 11, 2025
 *  version Dec. 21, 2025
 *  version Jan. 18, 2026
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWork(
  context: ExecutionContext,
  val datastore: DataStore = DataStore.noop(),
  val entitystore: EntityStore = EntityStore.noop(),
  eventengine: EventEngine = EventEngine.noop(DataStore.noop()),
  recorder: CommitRecorder = CommitRecorder.noop
) {
  import UnitOfWork.*
//  private var _http_driver: Option[HttpDriver] = None
//  private var _shell_command_executor: Option[ShellCommandExecutor] = None

  def create[T](entity: T)(using instance: EntityPersistentCreate[T]): Consequence[CreateResult[T]] = ???

  def load[T](id: EntityId)(using instance: EntityPersistent[T]): Consequence[T] = ???

  def search[T](directive: Query[T])(using instance: EntityPersistent[T]): Consequence[SearchResult[T]] = ???

  def save[T](id: EntityId, data: Record)(using instance: EntityPersistent[T]): Consequence[Unit] = ???

  def update[T](id: EntityId, changes: Record)(using instance: EntityPersistent[T]): Consequence[Unit] = ???

  def delete[T](id: EntityId)(using instance: EntityPersistent[T]): Consequence[Unit] = ???

  def searchableDatastore: Option[SearchableDataStore] =
    datastore match {
      case s: SearchableDataStore => Some(s)
      case _ => None
    }

  def httpDriver: HttpDriver = context.runtime.httpDriver

//  def http_driver: Option[HttpDriver] =
//    _http_driver.orElse(Some(context.runtime.httpDriver))

  lazy val shellCommandExecutor: ShellCommandExecutor =
    new LocalShellCommandExecutor

  // def shellCommandExecutor: ShellCommandExecutor =
  //   _shell_command_executor.getOrElse {
  //     val executor = new LocalShellCommandExecutor
  //     _shell_command_executor = Some(executor)
  //     executor
  //   }

  // def withHttpDriver(driver: Option[HttpDriver]): UnitOfWork = {
  //   _http_driver = driver
  //   this
  // }

  // def withShellCommandExecutor(executor: ShellCommandExecutor): UnitOfWork = {
  //   _shell_command_executor = Some(executor)
  //   this
  // }

  // def execute[A](op: UnitOfWorkOp[A])(using http: org.goldenport.cncf.http.HttpDriver): A =
  //   withHttpDriver(Some(http))
  //   new UnitOfWorkInterpreter(this).execute(op).asInstanceOf[A]

  def execute[A](op: UnitOfWorkOp[A]): A =
    new UnitOfWorkInterpreter(this).execute(op).asInstanceOf[A]

  def createFile(file: File, data: String): Consequence[Unit] = ???

  def sendMessage(msg: Message): Consequence[Unit] = ???

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

  def executionContext: ExecutionContext = context
}

object UnitOfWork {
  type CommitResult = Unit
  type AbortResult = Unit
  type Message = String

  def simple(
    datastore: DataStore,
    entitystore: EntityStore
  ): UnitOfWork = {
    val base = ExecutionContext.create() // ExecutionContext.createWithSystem(SystemContext.empty)
    val eventengine = EventEngine.noop(datastore)
    new UnitOfWork(base, datastore, entitystore, eventengine)
  }
}
