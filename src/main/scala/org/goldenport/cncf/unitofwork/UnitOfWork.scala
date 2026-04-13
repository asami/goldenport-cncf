package org.goldenport.cncf.unitofwork

import cats.Applicative
import scala.collection.mutable
import scala.util.{Try, Success, Failure}
import java.io.File
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.ConsequenceT
import org.goldenport.consequence.SourcePositionMacro
import org.goldenport.process.{LocalShellCommandExecutor, ShellCommandExecutor}
import org.goldenport.cncf.UowM
import org.goldenport.cncf.Program
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.datastore.{DataStore, SearchableDataStore}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.entity.CreateResult
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.entity.EntityPersistable
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.cncf.http.HttpDriver

/*
 * @since   Apr. 11, 2025
 *  version Dec. 21, 2025
 *  version Jan. 18, 2026
 *  version Feb. 27, 2026
 *  version Mar. 24, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWork(
  context: ExecutionContext,
  eventengine: EventEngine = EventEngine.noop(DataStore.noop()),
  recorder: CommitRecorder = CommitRecorder.noop
) {
  import UnitOfWork.*
//  private var _http_driver: Option[HttpDriver] = None
//  private var _shell_command_executor: Option[ShellCommandExecutor] = None
  private val _dirty_entities: mutable.Map[EntityId, Entity] = mutable.Map.empty
  private var _pending_events: Vector[DomainEvent] = Vector.empty

  def transactionContext = context.transactionContext

  def markDirty(entity: Entity): Unit =
    _dirty_entities.update(entity.id, entity)

  def dirtyEntities: Vector[Entity] =
    _dirty_entities.values.toVector

  def clear(): Unit =
    _dirty_entities.clear()

  def create[T](entity: T)(using instance: EntityPersistentCreate[T]): Consequence[CreateResult[T]] = ???

  def load[T](id: EntityId)(using instance: EntityPersistent[T]): Consequence[T] = ???

  def search[T](directive: Query[T])(using instance: EntityPersistent[T]): Consequence[SearchResult[T]] = ???

  def save[T](id: EntityId, data: Record)(using instance: EntityPersistent[T]): Consequence[Unit] = ???

  def update[T](id: EntityId, changes: Record)(using instance: EntityPersistent[T]): Consequence[Unit] = ???

  def delete[T](id: EntityId)(using instance: EntityPersistent[T]): Consequence[Unit] = ???

  // def searchableDatastore: Option[SearchableDataStore] =
  //   datastore match {
  //     case s: SearchableDataStore => Some(s)
  //     case _ => None
  //   }

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
      val tx = TransactionContext.create(context.transactionContext)
      val all = _pending_events ++ events.toVector
      eventengine.stage(all)
      recorder.record("UnitOfWork.prepare")
      val prepares = List(
        tx.prepare(),
        eventengine.prepare(tx)
      )
      prepares.collectFirst {
        case PrepareResult.Rejected(reason) => reason
      } match {
        case Some(reason) =>
          recorder.record("UnitOfWork.abort")
          eventengine.abort(tx) // TODO
          tx.abort()
          _pending_events = Vector.empty
          Consequence.stateConflict(reason)
        case None =>
          recorder.record("UnitOfWork.commit")
          tx.commit()
          eventengine.commit(tx) // TODO
          _pending_events = Vector.empty
          Consequence.success(())
      }
    } catch {
      case e: Throwable =>
        Consequence.Failure(Conclusion.from(e))
    }

  def abort(): Consequence[AbortResult] =
    try {
      val tx = TransactionContext.create(context.transactionContext)
      recorder.record("UnitOfWork.abort")
      eventengine.abort(tx) // TODO
      tx.abort()
      _pending_events = Vector.empty
      Consequence.success(())
    } catch {
      case e: Throwable =>
        Consequence.Failure(Conclusion.from(e))
    }

  def rollback(): Consequence[AbortResult] =
    abort()

  def record(message: String): Unit =
    recorder.record(message)

  def stageEvent(event: DomainEvent): Unit =
    stageEvents(Vector(event))

  def stageEvents(events: Seq[DomainEvent]): Unit =
    _pending_events = _pending_events ++ events.toVector

  def pendingEvents: Vector[DomainEvent] = _pending_events

  def executionContext: ExecutionContext = context
}

object UnitOfWork {
  type CommitResult = Unit
  type AbortResult = Unit
  type Message = String
  type Entity = EntityPersistable

  def simple(
    datastore: DataStore,
    entitystore: EntityStore
  ): UnitOfWork = {
    val base = ExecutionContext.create() // ExecutionContext.createWithSystem(SystemContext.empty)
    val eventengine = EventEngine.noop(datastore)
//    new UnitOfWork(base, datastore, entitystore, eventengine)
    new UnitOfWork(base, eventengine)
  }

  inline def uowmNotImplemented[F[_], A]: UowM[F, A] = {
    val pos = SourcePositionMacro.position()
    ConsequenceT.fromConsequence[[X] =>> Program[F, X], A](Consequence.notImplemented(pos))
  }

  inline def uowmNnotImplemented[F[_], A](message: String): UowM[F, A] = {
    val pos = SourcePositionMacro.position()
    ConsequenceT.fromConsequence[[X] =>> Program[F, X], A](Consequence.notImplemented(pos, message))
  }
}
