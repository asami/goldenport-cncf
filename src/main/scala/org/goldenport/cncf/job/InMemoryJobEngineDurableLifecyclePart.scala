package org.goldenport.cncf.job

import org.goldenport.Consequence

/*
 * @since   Sep. 28, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineDurableLifecyclePart extends JobEngine { self: InMemoryJobEngine =>

  private[job] def _lifecycle_bridge(jobid: JobId): Option[DurableJobLifecycleWriteBridge] =
    _state_monitor.synchronized {
      _native_binding(jobid) match {
        case Some(binding) => binding.bridge
        case None => _durable_lifecycle_write_bridge
      }
    }

  private[job] def _lifecycle_failure(jobid: JobId): Option[DurableJobLifecycleWriteFailure] =
    _state_monitor.synchronized {
      _native_binding(jobid).flatMap(_.checkpointfailure)
        .orElse(Option(_durable_lifecycle_write_failures.get(jobid)))
    }

  private def _lifecycle_context(record: JobRecord): org.goldenport.cncf.context.ExecutionContext =
    _native_binding(record.id).map(_.context).getOrElse(record.submittedContext)

  private[job] def _admit_durable_lifecycle(record: JobRecord): Consequence[Unit] =
    record.persistence match {
      case JobPersistencePolicy.Persistent =>
        _durable_lifecycle_write_bridge match {
          case Some(bridge) =>
            bridge.admit(record, runtimeState._lifetime_budget.policy)(using _lifecycle_context(record))
              .flatMap { _ =>
                bridge.lifetimeBinding(record.id) match {
                  case Some(native) if runtimeState._native_lifetime_bindings.size <
                      _resource_budget.policy.maxretainedjobs =>
                    runtimeState._native_lifetime_bindings.put(record.id,
                      new JobDurableLifetimeRuntimeBinding(native, record.submittedContext,
                        Some(bridge), new JobCancellationScope))
                    Consequence.unit
                  case _ => DurableJobLifetimeQuotaPolicy.unavailable
                }
              }
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
        _lifecycle_bridge(record.id) match {
          case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
            bridge.establishStartIntent(record)(using _lifecycle_context(record)) match {
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
        _lifecycle_bridge(record.id) match {
          case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
            bridge.establishRunningIntent(record)(using _lifecycle_context(record)) match {
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
            _lifecycle_bridge(record.id) match {
              case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
                val request = DurableJobLifecycleWriteRequest.TaskStartIntent(
                  record.id,
                  taskid,
                  parent,
                  record.taskReadModels
                )
                bridge.establishTaskStart(record, request)(using _lifecycle_context(record)).recoverWith {
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
            _lifecycle_bridge(record.id) match {
              case Some(bridge) if bridge.hasAdmittedSnapshot(record.id) =>
                val request = DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(
                  record.id,
                  taskid,
                  parent,
                  record.taskReadModels
                )
                bridge.establishTaskOutcome(record, request)(using _lifecycle_context(record)).recoverWith {
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
            _lifecycle_bridge(record.id) match {
              case Some(bridge)
                  if bridge.hasAdmittedSnapshot(record.id) &&
                    (record.taskReadModels.nonEmpty || bridge.recoveredCancelled(record)) =>
                bridge.establishTerminalOutcome(
                  record,
                  DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(
                    record.id,
                    record.taskReadModels
                  )
                )(using _lifecycle_context(record)).recoverWith { conclusion =>
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
    _state_monitor.synchronized {
      _native_binding(jobid).foreach(_.checkpointfailure = Some(failure))
      if (_durable_lifecycle_write_failures.containsKey(jobid) ||
          _durable_lifecycle_write_failures.size() < _resource_budget.policy.maxretainedjobs)
        _durable_lifecycle_write_failures.put(jobid, failure)
    }

}
