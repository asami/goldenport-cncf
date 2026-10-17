package org.goldenport.cncf.job

import java.time.Duration
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.config.JobDurableRuntimeConfiguration
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.operation.OperationResponse

/*
 * One native composition on the original engine and actual binding lease.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class DurableJobRuntimeOwner private[job] (
  engine: InMemoryJobEngine,
  inputs: DurableJobRuntimeInputs,
  configuration: JobDurableRuntimeConfiguration,
  lease: DurableJobRuntimeLease,
  subsystem: Subsystem
) {
  private given ExecutionContext = inputs.context
  private val _monitor = new Object
  private var _state = JobDurableOperationalState.Starting
  private var _degraded = false
  private var _storage_ready = false
  private var _startup_ready = false
  private var _stop_result = Option.empty[Consequence[Unit]]
  private[job] val _entity_store = new DurableJobLeasedEntityStore(inputs.entitystore, lease, () => _fault())
  private[job] val _blob_store = new DurableJobLeasedBlobStore(inputs.blobstore, lease, () => _fault())
  private[job] val _store = new DurableJobStore(_entity_store, inputs.storagepolicy)
  private[job] val _payloads = new DurableJobOwnedBlobPayloads(_store, _entity_store, _blob_store, inputs.blobpolicy)
  private[job] val _result_payloads = new DurableJobOperationResponsePayloads(_payloads)
  private[job] val _bridge = new DurableJobLifecycleWriteBridge(_store, inputs.evidence, inputs.lifetimepolicy,
    Some(_result_payloads))
  private[job] val _lifetime = _store.lifetimeQuota(inputs.lifetimepolicy)
  private[job] val _retention = new DurableJobRetentionMaintenance(_store)
  private[job] val _cleanup = new DurableJobPayloadCleanup(_store, _payloads)
  private[job] val _maintenance = new DurableJobMaintenance(_store, _retention, _cleanup)
  private val _maintenance_lease = DurableJobRuntimeLease.ready(lease, engine, _observe)
  private[job] val _scheduler = new DurableJobMaintenanceScheduler(_maintenance, inputs.retention,
    configuration.maxcandidates, Duration.ofMillis(configuration.periodmillis), engine.timeSource, engine._timer,
    Some(_maintenance_lease))

  private[job] def _start_c(onready: DurableJobRuntimeOwner => Consequence[Unit]): Consequence[Unit] =
    for {
      _ <- DurableJobStorageQuotaInventory.admitProvider()
      _ <- DurableJobOwnedBlobQuotaInventory.admitProvider()
      _ <- new DurableJobLifetimeQuotaProvider(_entity_store, inputs.lifetimepolicy).admit()
      status <- _blob_store.status()
      _ <- if (status != null && status.available && status.backend == "local") Consequence.unit else DurableJobRuntimeOwner.refused
      _ = _monitor.synchronized { _storage_ready = true }
      candidates <- inputs.startup.candidates(configuration.maxcandidates)
      _ <- if (inputs.migrateV1OnStartup)
        new DurableJobMigrationCheckpoint(_store, _payloads, inputs.lifetimepolicy,
          engine._resource_budget.policy).migrate(candidates, configuration.maxcandidates)
        else Consequence.unit
      _ = engine.bindDurableLifecycleWriteBridge(_bridge)
      _ <- _recover_c(candidates)
      _ = _monitor.synchronized { _startup_ready = true }
      _ <- engine._commit_durable_runtime_c(this, _scheduler, onready)
    } yield ()

  private def _recover_c(discovered: Vector[DurableJobStartupRecoveryCandidate]): Consequence[Unit] = {
    val cached = new DurableJobStartupRecoverySource {
      def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = Consequence.success(discovered)
    }
    // These entry points own the original authentication/registration monitors.
    for {
      terminal <- engine.recoverDurableTerminalFacts(cached, _store, configuration.maxcandidates)
      runtime <- engine.recoverDurableStartup(cached, _store, inputs.port, configuration.maxcandidates)
      _ <- if (terminal.candidates.size == discovered.size && runtime.candidates.size == discovered.size &&
        discovered.indices.forall { index =>
          val candidate = discovered(index)
          val completed = terminal.candidates(index)
          val restored = runtime.candidates(index)
          completed.ordinal == candidate.ordinal && restored.ordinal == candidate.ordinal &&
            completed.jobId == candidate.jobId && restored.jobId == candidate.jobId &&
            ((completed.fact == DurableJobTerminalProjectionFact.Registered) !=
              (restored.fact == DurableJobRuntimeRehydrationFact.Registered))
        }) Consequence.unit else DurableJobRuntimeOwner.refused
    } yield ()
  }

  private[job] def _ready(): Unit = _monitor.synchronized { _state = JobDurableOperationalState.Ready }
  private def _fault(): Unit = _monitor.synchronized { _degraded = true }
  private[job] def _execute_c[A](body: => Consequence[A]): Consequence[A] = lease.use(body)

  private[job] def _read_result_c(snapshot: DurableJobStoreSnapshot,
    caller: ExecutionContext): Consequence[OperationResponse] = {
    val admitted = _monitor.synchronized { _state == JobDurableOperationalState.Ready }
    if (!admitted || !engine._durable_runtime_ready ||
        !engine._durable_runtime_owner_option.exists(_ eq this)) DurableJobCallerAccessResolver.refused
    else try {
      lease.use {
        if (caller == null) DurableJobCallerAccessResolver.refused
        else if (Thread.currentThread().isInterrupted) throw new InterruptedException()
        else inputs.callerAccessResolver match {
          case Some(resolver) =>
            resolver.resolve(caller) match {
              case Consequence.Success(access) =>
                DurableJobCallerAccessResolver.admitAccess(access).flatMap { selected =>
                  _result_payloads.readObserved(snapshot, selected)(using caller)
                }
              case _ => DurableJobCallerAccessResolver.refused
            }
          case _ => DurableJobCallerAccessResolver.refused
        }
      } match {
        case success @ Consequence.Success(response) if response != null => success
        case _ => DurableJobCallerAccessResolver.refused
      }
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); DurableJobCallerAccessResolver.refused
      case NonFatal(_) => DurableJobCallerAccessResolver.refused
    }
  }

  private[job] def _prepare_backup_c(policy: DurableJobBackupResourcePolicy): Consequence[DurableJobBackupSource] = {
    val current = health
    if (current.state != JobDurableOperationalState.Ready || !current.storageReady || !current.startupReady ||
        !current.executionAvailable || !current.maintenanceHealthy || !current.maintenanceRegistered ||
        !(subsystem.jobEngine eq engine) || !engine._durable_runtime_owner_option.exists(_ eq this))
      DurableJobBackupOperations._backup_refused
    else lease.use { DurableJobBackupSource._capture_c(this, engine, subsystem, inputs, policy) }
  }

  private[job] def _backup_closed: Boolean = _monitor.synchronized {
    _state == JobDurableOperationalState.Closed && _stop_result.exists(_.isSuccess)
  }

  private[job] def _inspect_integrity_c(maxcandidates: Int): Consequence[JobDurableIntegrityReport] = {
    val admitted = _monitor.synchronized { _state == JobDurableOperationalState.Ready }
    if (!admitted) DurableJobIntegrityInspection.refused
    else {
      val result: Consequence[JobDurableIntegrityReport] = try lease.use {
        new DurableJobIntegrityInspection(inputs.startup, _store, _payloads, _lifetime).inspect(maxcandidates)
      }.recoverWith(_ => DurableJobIntegrityInspection.refused) catch {
        case _: InterruptedException => Thread.currentThread().interrupt(); DurableJobIntegrityInspection.refused
        case NonFatal(_) => DurableJobIntegrityInspection.refused
      }
      result match {
        case Consequence.Success(report) if report != null && report.entries.forall(entry =>
            entry.status == JobDurableIntegrityStatus.Healthy || entry.status == JobDurableIntegrityStatus.Inactive) => ()
        case _ => _fault()
      }
      result
    }
  }

  private def _observe(result: Consequence[?]): Unit = result match {
    case Consequence.Success(report: DurableJobMaintenanceReport) if !_healthy(report) => _fault()
    case Consequence.Failure(_) => _fault()
    case _ => ()
  }

  private def _healthy(report: DurableJobMaintenanceReport): Boolean = {
    val badretention = Set(DurableJobRetentionFacet.Missing, DurableJobRetentionFacet.Corrupt,
      DurableJobRetentionFacet.Refused, DurableJobRetentionFacet.Stale, DurableJobRetentionFacet.InvalidTransition,
      DurableJobRetentionFacet.RevisionExhausted, DurableJobRetentionFacet.WriteRefused)
    val badcleanup = Set(DurableJobCleanupFacet.Missing, DurableJobCleanupFacet.Corrupt,
      DurableJobCleanupFacet.Refused, DurableJobCleanupFacet.Stale, DurableJobCleanupFacet.RevisionExhausted,
      DurableJobCleanupFacet.InvalidTime, DurableJobCleanupFacet.OwnershipRefused,
      DurableJobCleanupFacet.ResourceBoundRefused, DurableJobCleanupFacet.PayloadDeleteRefused, DurableJobCleanupFacet.CheckpointRefused)
    report != null && report.entries != null && report.entries.forall(entry =>
      entry != null && !entry.retention.exists(badretention) && !entry.cleanup.exists(badcleanup))
  }

  def health: JobDurableOperationalHealth = {
    val scheduler = _scheduler.status
    _monitor.synchronized {
      val running = _state == JobDurableOperationalState.Ready
      val healthy = !_degraded && !scheduler.lastResult.exists {
        case Consequence.Failure(_) => true
        case Consequence.Success(report) => !_healthy(report)
      }
      JobDurableOperationalHealth(if (running && !healthy) JobDurableOperationalState.Degraded else _state,
        true, running && _storage_ready, running && _startup_ready,
        running && scheduler.started && !scheduler.closed, running && healthy, running && lease.available)
    }
  }

  private[cncf] def _stop_c(): Consequence[Unit] = {
    val admitted = _monitor.synchronized {
      if (_stop_result.nonEmpty || _state == JobDurableOperationalState.Closing) false
      else { _state = JobDurableOperationalState.Closing; true }
    }
    if (!admitted) _monitor.synchronized { _stop_result.getOrElse(Consequence.unit) }
    else {
      val result = try { _scheduler.close(); Consequence.unit } catch {
        case _: InterruptedException => Thread.currentThread().interrupt(); DurableJobRuntimeOwner.refused
        case NonFatal(_) => DurableJobRuntimeOwner.refused
      } finally lease.close()
      _monitor.synchronized { _state = JobDurableOperationalState.Closed; _stop_result = Some(result) }
      result
    }
  }
}

private[cncf] object DurableJobRuntimeOwner {
  private[job] def create(engine: InMemoryJobEngine, inputs: DurableJobRuntimeInputs,
    configuration: JobDurableRuntimeConfiguration, lease: DurableJobRuntimeLease,
    subsystem: Subsystem): Consequence[DurableJobRuntimeOwner] =
    if (inputs == null || inputs.entitystore == null || inputs.blobstore == null || inputs.evidence == null ||
        inputs.startup == null || inputs.port == null || inputs.retention == null || inputs.context == null ||
        inputs.storagepolicy == null || inputs.blobpolicy == null || inputs.lifetimepolicy == null ||
        inputs.callerAccessResolver == null || inputs.callerAccessResolver.exists(_ == null) ||
        !inputs.storagepolicy.isValid || !inputs.blobpolicy.isValid || !inputs.lifetimepolicy.isValid ||
        inputs.lifetimepolicy.lifetime != engine.runtimeState._lifetime_budget.policy) refused
    else Consequence.success(new DurableJobRuntimeOwner(engine, inputs, configuration, lease, subsystem))

  private[cncf] def refused[A]: Consequence[A] = Consequence.stateInvalid("durable runtime startup refused")
}
