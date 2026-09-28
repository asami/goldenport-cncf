package org.goldenport.cncf.job

import java.security.MessageDigest
import java.time.{Duration, Instant}
import java.util.Base64
import java.util.concurrent.{
  ConcurrentHashMap,
  Executors,
  PriorityBlockingQueue,
  ScheduledExecutorService,
  TimeUnit,
  ExecutorService
}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.concurrent.ExecutionContext as ScalaExecutionContext
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.consequence.Failures
import org.goldenport.id.UniversalId
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.conclusion.Disposition
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentLogic}
import org.goldenport.cncf.context.{
  ExecutionContext,
  ExecutionInvocationIdentity,
  ExecutionProfileRuntime,
  ExecutionSchedulerMode,
  ExecutionSchedulingRegistration,
  IdGenerationContext
}
import org.goldenport.cncf.entity.{
  EntityMutationExecutionPolicy,
  EntityPersistentCreate,
  EntityStore
}
import org.simplemodeling.model.datatype.EntityRevision
import org.goldenport.cncf.event.{
  EventBus,
  EventLane,
  EventPublishOption,
  EventRecordFactory,
  EventStore,
  ReceptionDomainEvent
}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizer, ObservabilityEngine}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineDurableLifecyclePart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  private[job] def _admit_durable_lifecycle(record: JobRecord): Consequence[Unit] =
    record.persistence match {
      case JobPersistencePolicy.Persistent =>
        _durable_lifecycle_write_bridge match {
          case Some(bridge) => bridge.admit(record)(using record.submittedContext)
          case None => Consequence.unit
        }
      case JobPersistencePolicy.Ephemeral =>
        Consequence.unit
    }

  private[job] def _establish_durable_start_intent(jobid: JobId): Boolean =
    _get_record(jobid).exists(_establish_durable_start_intent)

  private[job] def _establish_durable_start_intent(record: JobRecord): Boolean =
    record.persistence match {
      case JobPersistencePolicy.Persistent =>
        _durable_lifecycle_write_bridge match {
          case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
            bridge.establishStartIntent(record)(using record.submittedContext) match {
              case Consequence.Success(_) => true
              case Consequence.Failure(_) =>
                _record_durable_lifecycle_write_failure(
                  record.id,
                  DurableJobLifecycleWriteFailure.StartIntentRefused
                )
                false
            }
          case _ =>
            true
        }
      case JobPersistencePolicy.Ephemeral =>
        true
    }

  private[job] def _establish_durable_start_intent_if_required(
    record: JobRecord,
    note: Option[String]
  ): Boolean =
    if (note.contains("job-run"))
      _establish_durable_start_intent(record)
    else
      true

  private[job] def _establish_durable_running_intent(jobid: JobId): Boolean =
    _get_record(jobid).exists(_establish_durable_running_intent)

  private[job] def _establish_durable_running_intent(record: JobRecord): Boolean =
    record.persistence match {
      case JobPersistencePolicy.Persistent =>
        _durable_lifecycle_write_bridge match {
          case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
            bridge.establishRunningIntent(record)(using record.submittedContext) match {
              case Consequence.Success(_) => true
              case Consequence.Failure(_) =>
                _record_durable_lifecycle_write_failure(
                  record.id,
                  DurableJobLifecycleWriteFailure.RunningIntentRefused
                )
                false
            }
          case _ =>
            true
        }
      case JobPersistencePolicy.Ephemeral =>
        true
    }

  private[job] def _establish_durable_running_intent_if_required(
    record: JobRecord,
    note: Option[String]
  ): Boolean =
    if (note.contains("job-run"))
      _establish_durable_running_intent(record)
    else
      true

  private[job] def _establish_durable_task_start_intent(
    jobid: JobId,
    taskid: TaskId,
    parent: Option[TaskId]
  ): Consequence[Unit] =
    _get_record(jobid) match {
      case Some(record) =>
        record.persistence match {
          case JobPersistencePolicy.Persistent =>
            _durable_lifecycle_write_bridge match {
              case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
                val request = DurableJobLifecycleWriteRequest.TaskStartIntent(
                  record.id,
                  taskid,
                  parent,
                  record.taskReadModels
                )
                bridge.establishTaskStart(record, request)(using record.submittedContext).recoverWith {
                  conclusion =>
                    _record_durable_lifecycle_write_failure(
                      record.id,
                      DurableJobLifecycleWriteFailure.TaskStartIntentRefused
                    )
                    Consequence.Failure(conclusion)
                }
              case _ =>
                Consequence.unit
            }
          case JobPersistencePolicy.Ephemeral =>
            Consequence.unit
        }
      case None =>
        Consequence.operationNotFound(s"job:${jobid.value}")
    }

  private[job] def _establish_durable_task_outcome_checkpoint(
    jobid: JobId,
    taskid: TaskId,
    parent: Option[TaskId]
  ): Consequence[Unit] =
    _get_record(jobid) match {
      case Some(record) =>
        record.persistence match {
          case JobPersistencePolicy.Persistent =>
            _durable_lifecycle_write_bridge match {
              case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
                val request = DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(
                  record.id,
                  taskid,
                  parent,
                  record.taskReadModels
                )
                bridge.establishTaskOutcome(record, request)(using record.submittedContext).recoverWith {
                  conclusion =>
                    _record_durable_lifecycle_write_failure(
                      record.id,
                      DurableJobLifecycleWriteFailure.TaskOutcomeCheckpointRefused
                    )
                    Consequence.Failure(conclusion)
                }
              case _ =>
                Consequence.unit
            }
          case JobPersistencePolicy.Ephemeral =>
            Consequence.unit
        }
      case None =>
        Consequence.operationNotFound(s"job:${jobid.value}")
    }

  private def _establish_durable_terminal_outcome_checkpoint(
    jobid: JobId
  ): Consequence[Unit] =
    _get_record(jobid) match {
      case Some(record) =>
        record.persistence match {
          case JobPersistencePolicy.Persistent =>
            _durable_lifecycle_write_bridge match {
              case Some(bridge)
                  if bridge.hasAdmittedSnapshot(record.id) &&
                    record.taskReadModels.nonEmpty =>
                bridge.establishTerminalOutcome(
                  record,
                  DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(
                    record.id,
                    record.taskReadModels
                  )
                )(using record.submittedContext).recoverWith { conclusion =>
                  _record_durable_lifecycle_write_failure(
                    record.id,
                    DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
                  )
                  Consequence.Failure(conclusion)
                }
              case _ =>
                Consequence.unit
            }
          case JobPersistencePolicy.Ephemeral =>
            Consequence.unit
        }
      case None =>
        Consequence.operationNotFound(s"job:${jobid.value}")
    }

  private[job] def _terminal_checkpoint_succeeded(jobid: JobId): Boolean =
    _establish_durable_terminal_outcome_checkpoint(jobid) match {
      case Consequence.Success(_) => true
      case Consequence.Failure(_) => false
    }

  private[job] def _terminal_checkpoint_succeeded(
    jobid: JobId,
    taskbridgewrites: Boolean
  ): Boolean =
    if (taskbridgewrites)
      _terminal_checkpoint_succeeded(jobid)
    else
      true

  private[job] def _record_durable_lifecycle_write_failure(
    jobid: JobId,
    failure: DurableJobLifecycleWriteFailure
  ): Unit =
    _durable_lifecycle_write_failures.put(jobid, failure)

}
