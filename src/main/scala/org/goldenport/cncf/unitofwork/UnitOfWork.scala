package org.goldenport.cncf.unitofwork

import cats.Applicative
import scala.collection.mutable
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal
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
import org.goldenport.cncf.event.{DomainEvent, EventEngine, EventRecordFactory}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.operation.evaluation.{
  OperationEvaluationAttemptId,
  OperationEvaluationSupplementalBuffer,
  OperationEvaluationSupplementalIntent
}

/*
 * @since   Apr. 11, 2025
 *  version Dec. 21, 2025
 *  version Jan. 18, 2026
 *  version Feb. 27, 2026
 *  version Mar. 24, 2026
 *  version Apr. 28, 2026
 *  version Aug. 12, 2026
 * @version Sep. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWork(
  context: ExecutionContext,
  eventengine: EventEngine = EventEngine.noop(DataStore.noop()),
  recorder: CommitRecorder = CommitRecorder.noop,
  operationevaluationsupplementalbuffer: OperationEvaluationSupplementalBuffer =
    new OperationEvaluationSupplementalBuffer
) {
  import UnitOfWork.*
//  private var _http_driver: Option[HttpDriver] = None
//  private var _shell_command_executor: Option[ShellCommandExecutor] = None
  private val _dirty_entities: mutable.Map[EntityId, Entity] = mutable.Map.empty
  private var _pending_events: Vector[DomainEvent] = Vector.empty
  private var _post_commit_callbacks: Vector[TransactionContext.TransactionContextId => Consequence[Unit]] = Vector.empty
  private var _post_abort_callbacks: Vector[TransactionContext.TransactionContextId => Consequence[Unit]] = Vector.empty
  private var _post_abort_outcome_callbacks: Vector[(UnitOfWork.PostAbortOutcome, TransactionContext.TransactionContextId) => Consequence[Unit]] = Vector.empty
  private val _operation_evaluation_supplemental_buffer = operationevaluationsupplementalbuffer
  private var _last_commit_result: Option[Consequence[CommitResult]] = None
  private var _last_commit_termination: Option[UnitOfWorkTermination] = None
  private var _last_abort_result: Option[Consequence[AbortResult]] = None
  private val _resource_registry = new UnitOfWorkResourceRegistry

  def transactionContext = context.transactionContext

  def withContext(ctx: ExecutionContext): UnitOfWork =
    new UnitOfWork(ctx, eventengine, recorder, _operation_evaluation_supplemental_buffer)

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
  ): Consequence[CommitResult] = {
    var termination = UnitOfWorkTermination.Aborted
    var postcommitcontrol: Option[Throwable] = None
    var transactionid: Option[TransactionContext.TransactionContextId] = None
    val result = try {
      val tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
      transactionid = Some(tx.id)
      val all = _pending_events ++ events.toVector
      eventengine.stage(all, EventRecordFactory.from(context))
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
          val callbacks = _post_abort_callbacks
          val outcomecallbacks = _post_abort_outcome_callbacks
          _clear_pending_commit_state()
          val aborted = _run_post_abort_callbacks_c(Consequence.stateConflict(reason), callbacks, tx.id)
          _run_post_abort_outcome_callbacks_c(
            aborted,
            outcomecallbacks,
            UnitOfWork.PostAbortOutcome.Persistence,
            tx.id
          )
        case None =>
          recorder.record("UnitOfWork.commit")
          tx.commit()
          eventengine.commit(tx) // TODO
          val callbacks = _post_commit_callbacks
          _clear_pending_commit_state()
          termination = UnitOfWorkTermination.Committed
          _run_post_commit_callbacks_c(callbacks, tx.id)
      }
    } catch {
      case e: Throwable =>
        if (termination == UnitOfWorkTermination.Committed) {
          postcommitcontrol = Some(e)
          Consequence.Failure(Conclusion.from(e))
        } else {
          val failed = Consequence.Failure[CommitResult](Conclusion.from(e))
          val outcomecallbacks = _post_abort_outcome_callbacks
          _clear_pending_commit_state()
          transactionid.map { id =>
            _run_post_abort_outcome_callbacks_c(
              failed,
              outcomecallbacks,
              UnitOfWork.PostAbortOutcome.Persistence,
              id
            )
          }.getOrElse(failed)
        }
    }
    val completed = try {
      _complete_c(result, termination)
    } catch {
      case cleanup: Throwable =>
        postcommitcontrol match {
          case Some(control) =>
            Consequence.Failure(Conclusion.from(cleanup) ++ Conclusion.from(control))
          case None =>
            throw cleanup
        }
    }
    _last_commit_termination = Some(termination)
    _last_commit_result = Some(completed)
    postcommitcontrol match {
      case Some(control) =>
        _attach_cleanup_diagnostic(control, completed)
        if (control.isInstanceOf[InterruptedException])
          Thread.currentThread.interrupt()
        throw control
      case None =>
        completed
    }
  }

  def abort(): Consequence[AbortResult] = {
    val result = try {
      val tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
      recorder.record("UnitOfWork.abort")
      eventengine.abort(tx) // TODO
      tx.abort()
      val callbacks = _post_abort_callbacks
      val outcomecallbacks = _post_abort_outcome_callbacks
      _clear_pending_commit_state()
      val aborted = _run_post_abort_callbacks_c(Consequence.success(()), callbacks, tx.id)
      _run_post_abort_outcome_callbacks_c(
        aborted,
        outcomecallbacks,
        UnitOfWork.PostAbortOutcome.Rollback,
        tx.id
      )
    } catch {
      case e: Throwable =>
        Consequence.Failure(Conclusion.from(e))
    }
    val completed = _complete_c(result, UnitOfWorkTermination.Aborted)
    _last_abort_result = Some(completed)
    completed
  }

  def rollback(): Consequence[AbortResult] =
    abort()

  /**
   * Releases runtime resources when a caller ends a UnitOfWork outside the
   * ordinary commit or abort path. It does not change transaction outcome.
   */
  def dispose(): Consequence[Unit] =
    _resource_registry.terminateC(UnitOfWorkTermination.Disposed)

  def registerResourceC(
    resource: UnitOfWorkResource
  ): Consequence[UnitOfWorkResourceRegistration] =
    _resource_registry.registerC(resource)

  def record(message: String): Unit =
    recorder.record(message)

  def stageEvent(event: DomainEvent): Unit =
    stageEvents(Vector(event))

  def stageEvents(events: Seq[DomainEvent]): Unit =
    _pending_events = _pending_events ++ events.toVector

  def pendingEvents: Vector[DomainEvent] = _pending_events

  private[unitofwork] def pendingEventsCheckpoint: Vector[DomainEvent] =
    _pending_events

  private[unitofwork] def restorePendingEvents(
    checkpoint: Vector[DomainEvent]
  ): Unit =
    _pending_events = checkpoint

  def stageOperationEvaluationSupplementalC(
    intent: OperationEvaluationSupplementalIntent
  ): Consequence[Unit] =
    _operation_evaluation_supplemental_buffer.stageC(intent)

  def markOperationEvaluationSupplementalCommitted(
    attemptid: OperationEvaluationAttemptId
  ): Unit =
    _operation_evaluation_supplemental_buffer.markCommitted(attemptid)

  def releaseCommittedOperationEvaluationSupplemental(
    attemptid: OperationEvaluationAttemptId
  ): Vector[OperationEvaluationSupplementalIntent] =
    _operation_evaluation_supplemental_buffer.releaseCommitted(attemptid)

  def discardOperationEvaluationSupplemental(
    attemptid: OperationEvaluationAttemptId
  ): Unit =
    _operation_evaluation_supplemental_buffer.discard(attemptid)

  def lastCommitResult: Option[Consequence[CommitResult]] =
    _last_commit_result

  def lastCommitTermination: Option[UnitOfWorkTermination] =
    _last_commit_termination

  def lastAbortResult: Option[Consequence[AbortResult]] =
    _last_abort_result

  def stagePostCommit(callback: => Unit): Unit =
    stagePostCommitC {
      try {
        callback
        Consequence.unit
      } catch {
        case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
      }
    }

  def stagePostCommitC(callback: => Consequence[Unit]): Unit =
    _stage_post_commit_with_transaction_c(_ => callback)

  private def _stage_post_commit_with_transaction_c(
    callback: TransactionContext.TransactionContextId => Consequence[Unit]
  ): Unit =
    _post_commit_callbacks = _post_commit_callbacks :+ callback

  def stagePostCommitEventC(
    event: TransactionContext.TransactionContextId => DomainEvent
  ): Unit =
    _stage_post_commit_with_transaction_c { transactionid =>
      eventengine.emit(Vector(event(transactionid)), EventRecordFactory.from(context)).map(_ => ())
    }

  def stagePostAbortEventC(
    event: TransactionContext.TransactionContextId => DomainEvent
  ): Unit =
    _post_abort_callbacks = _post_abort_callbacks :+ { transactionid =>
      eventengine.emit(Vector(event(transactionid)), EventRecordFactory.from(context)).map(_ => ())
    }

  private[cncf] def stagePostAbortEventC(
    event: (UnitOfWork.PostAbortOutcome, TransactionContext.TransactionContextId) => DomainEvent
  ): Unit =
    _post_abort_outcome_callbacks = _post_abort_outcome_callbacks :+ { (outcome, transactionid) =>
      eventengine.emit(Vector(event(outcome, transactionid)), EventRecordFactory.from(context)).map(_ => ())
    }

  def executionContext: ExecutionContext = context

  private def _complete_c[A](
    result: Consequence[A],
    termination: UnitOfWorkTermination
  ): Consequence[A] =
    _resource_registry.terminateC(termination) match {
      case Consequence.Failure(cleanup) =>
        result match {
          case Consequence.Success(_) => Consequence.Failure(cleanup)
          case Consequence.Failure(primary) =>
            // Keep the primary failure authoritative while retaining cleanup diagnostics.
            Consequence.Failure(cleanup ++ primary)
        }
      case _ => result
    }

  private def _clear_pending_commit_state(): Unit = {
    _pending_events = Vector.empty
    _post_commit_callbacks = Vector.empty
    _post_abort_callbacks = Vector.empty
    _post_abort_outcome_callbacks = Vector.empty
  }

  private def _run_post_commit_callbacks_c(
    callbacks: Vector[TransactionContext.TransactionContextId => Consequence[Unit]],
    transactionid: TransactionContext.TransactionContextId
  ): Consequence[Unit] = {
    var failures = Vector.empty[Conclusion]
    callbacks.foreach { callback =>
      _run_post_commit_callback_c(callback, transactionid) match {
        case Consequence.Failure(conclusion) => failures = failures :+ conclusion
        case Consequence.Success(_) => ()
      }
    }
    failures.reduceOption(_ ++ _).map(Consequence.Failure(_)).getOrElse(Consequence.unit)
  }

  private def _run_post_commit_callback_c(
    callback: TransactionContext.TransactionContextId => Consequence[Unit],
    transactionid: TransactionContext.TransactionContextId
  ): Consequence[Unit] =
    try {
      callback(transactionid)
    } catch {
      case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
    }

  private def _run_post_abort_callbacks_c[A](
    result: Consequence[A],
    callbacks: Vector[TransactionContext.TransactionContextId => Consequence[Unit]],
    transactionid: TransactionContext.TransactionContextId
  ): Consequence[A] =
    _run_post_commit_callbacks_c(callbacks, transactionid) match {
      case Consequence.Failure(cleanup) =>
        result match {
          case Consequence.Failure(primary) => Consequence.Failure(cleanup ++ primary)
          case Consequence.Success(_) => Consequence.Failure(cleanup)
        }
      case Consequence.Success(_) => result
    }

  private def _run_post_abort_outcome_callbacks_c[A](
    result: Consequence[A],
    callbacks: Vector[(UnitOfWork.PostAbortOutcome, TransactionContext.TransactionContextId) => Consequence[Unit]],
    outcome: UnitOfWork.PostAbortOutcome,
    transactionid: TransactionContext.TransactionContextId
  ): Consequence[A] =
    _run_post_abort_callbacks_c(
      result,
      callbacks.map(callback => transaction => callback(outcome, transaction)),
      transactionid
    )

  private def _attach_cleanup_diagnostic(
    control: Throwable,
    completed: Consequence[CommitResult]
  ): Unit =
    completed match {
      case Consequence.Failure(conclusion) =>
        conclusion.causes
          .flatMap(_.getException)
          .find(_ ne control)
          .foreach { cleanup =>
            try {
              control.addSuppressed(cleanup)
            } catch {
              case NonFatal(_) => ()
            }
          }
      case _ => ()
    }

}

object UnitOfWork {
  type CommitResult = Unit
  type AbortResult = Unit
  type Message = String
  type Entity = EntityPersistable

  private[cncf] enum PostAbortOutcome {
    case Persistence
    case Rollback
  }

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
