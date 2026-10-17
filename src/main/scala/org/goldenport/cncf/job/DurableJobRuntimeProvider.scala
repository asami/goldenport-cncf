package org.goldenport.cncf.job

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.blob.BlobStore
import org.goldenport.cncf.config.JobDurableRuntimeConfiguration
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.subsystem.Subsystem

/*
 * Trusted per-Subsystem composition supplies closed evidence independently.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobRuntimeInputs(
  entitystore: EntityStore,
  blobstore: BlobStore,
  evidence: DurableJobKeyedEvidenceSource,
  startup: DurableJobStartupRecoverySource,
  port: DurableJobRuntimeRehydrationPort,
  retention: DurableJobRetentionCandidateSource,
  context: ExecutionContext,
  storagepolicy: DurableJobStorageResourcePolicy,
  blobpolicy: DurableJobOwnedBlobResourcePolicy,
  lifetimepolicy: DurableJobLifetimeQuotaPolicy,
  migrateV1OnStartup: Boolean = false,
  callerAccessResolver: Option[DurableJobCallerAccessResolver] = None
)

private[cncf] abstract class DurableJobRuntimeProvider(val id: String) {
  private[job] def _inputs_c(subsystem: Subsystem): Consequence[DurableJobRuntimeInputs]

  final private[cncf] def _start_c(
    subsystem: Subsystem,
    configuration: JobDurableRuntimeConfiguration,
    onready: DurableJobRuntimeOwner => Consequence[Unit]
  ): Consequence[DurableJobRuntimeOwner] = subsystem.jobEngine match {
    case engine: InMemoryJobEngine =>
      engine._begin_durable_runtime_c().flatMap { _ =>
        val lease = DurableJobRuntimeLease.subsystem(subsystem)
        var owner = Option.empty[DurableJobRuntimeOwner]
        val result = try lease.use {
          _inputs_c(subsystem).flatMap { inputs =>
            DurableJobRuntimeOwner.create(engine, inputs, configuration, lease, subsystem).flatMap { created =>
              owner = Some(created)
              created._start_c(onready).map(_ => created)
            }
          }
        } catch {
          case _: InterruptedException => Thread.currentThread().interrupt(); DurableJobRuntimeOwner.refused
          case NonFatal(_) => DurableJobRuntimeOwner.refused
        }
        result match {
          case success @ Consequence.Success(_) => success
          case _ =>
            owner.foreach(_._stop_c())
            lease.close()
            engine._fail_durable_runtime()
            DurableJobRuntimeOwner.refused
        }
      }.recoverWith { _ =>
        engine._fail_durable_runtime()
        DurableJobRuntimeOwner.refused
      }
    case _ => DurableJobRuntimeOwner.refused
  }
}

private[job] final class ConfiguredDurableJobRuntimeProvider(
  id: String,
  build: Subsystem => Consequence[DurableJobRuntimeInputs]
) extends DurableJobRuntimeProvider(id) {
  private[job] def _inputs_c(subsystem: Subsystem): Consequence[DurableJobRuntimeInputs] = build(subsystem)
}
