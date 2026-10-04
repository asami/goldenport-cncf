package org.goldenport.cncf.job

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Complete read-only preflight precedes sequential native migration checkpoints.
 * Payment is pre-existing authority; a failed acknowledgement never permits retry.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobMigrationCheckpoint(
  store: DurableJobStore,
  payloads: DurableJobOwnedBlobPayloads,
  lifetimepolicy: DurableJobLifetimeQuotaPolicy,
  resources: JobResourcePolicy,
  maxPhysicalBytes: Long = 16777216L
) {
  private final case class Prepared(candidate: DurableJobStartupRecoveryCandidate,
    source: DurableJobStoreSnapshot, destination: Option[DurableJobRecord],
    footprint: DurableJobLifetimeRecovery.Footprint)

  def migrate(candidates: Vector[DurableJobStartupRecoveryCandidate], maxCandidates: Int)
      (using ctx: ExecutionContext): Consequence[Unit] =
    try {
      val result = new JobExperiencePolicy(JobExperienceScope.Operator).authorizeScope.flatMap { _ =>
        val budget = new DurableJobIntegrityByteBudget(maxPhysicalBytes)
        if (store == null || payloads == null || lifetimepolicy == null || resources == null ||
            !budget.isValid || maxCandidates < 1 || maxCandidates > 1024) _refused
        else {
          val discovered = candidates
          val cached = new DurableJobStartupRecoverySource {
            def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] =
              Consequence.success(discovered)
          }
          for {
            _ <- _uninterrupted_c
            _ <- lifetimepolicy.validate
            _ <- resources.validate
            admitted <- new DurableJobStartupRecoveryCoordinator(cached, store)._admitted_candidates_c(maxCandidates)
            _ <- if (admitted.forall(candidate => DurableJobStorageResourcePolicy.validId(candidate.jobId)))
              Consequence.unit else _refused
            prepared <- admitted.foldLeft(Consequence.success(Vector.empty[Prepared])) { (result, candidate) =>
              result.flatMap(values => _preflight_c(candidate, budget).map(values :+ _))
            }
            _ <- prepared.foldLeft(Consequence.unit)((result, value) => result.flatMap(_ => _checkpoint_c(value)))
            _ <- _uninterrupted_c
          } yield ()
        }
      }
      result.recoverWith(_ => _refused)
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); _refused
      case NonFatal(_) => _refused
    }

  private def _preflight_c(candidate: DurableJobStartupRecoveryCandidate,
    budget: DurableJobIntegrityByteBudget)(using ctx: ExecutionContext): Consequence[Prepared] =
    for {
      _ <- _uninterrupted_c
      source <- _load_c(candidate)
      _ <- if (source.record.body.retention.deletionState == DurableDeletionState.Active)
        Consequence.unit else _refused
      destination <- source.record.format match {
        case DurableRecordFormat.V1 => _destination_c(source).map(Some(_))
        case DurableRecordFormat.V2 => Consequence.success(None)
        case _ => _refused
      }
      snapshot = destination.map(record => source.copy(record = record)).getOrElse(source)
      _ <- destination match {
        case Some(_) => for {
          decision <- DurableJobRecovery.classify(snapshot.record, candidate.replayEvidence)
          projection <- DurableJobTerminalProjection.project(candidate, snapshot, decision)
          _ <- if (projection.decision == decision && _terminal(decision.outcome)) Consequence.unit else _refused
        } yield ()
        case None => Consequence.unit
      }
      footprint <- DurableJobLifetimeRecovery.footprint(snapshot, resources, lifetimepolicy.lifetime)
      _ <- destination match {
        case Some(record) => DurableJobLifetimeRecovery.prepareMigration(store, source, record,
          candidate.access, ctx, lifetimepolicy, footprint)
        case None => DurableJobLifetimeRecovery.prepare(store, source, candidate.access, ctx,
          lifetimepolicy, footprint, None).map(_ => ())
      }
      _ <- _uninterrupted_c
      physical <- payloads.inspect(source.record, budget)
      _ <- if (physical == DurableJobPayloadIntegrityOutcome.Verified) Consequence.unit else _refused
      _ <- _uninterrupted_c
    } yield Prepared(candidate, source, destination, footprint)

  private def _destination_c(source: DurableJobStoreSnapshot): Consequence[DurableJobRecord] = {
    val body = source.record.body
    if (body.identity.revision == Long.MaxValue) _refused
    else for {
      migrated <- DurableJobRecord.migrateV1ToV2(source.record)
      destination <- DurableJobRecord.createV2(migrated.body.copy(identity =
        migrated.body.identity.copy(revision = body.identity.revision + 1L)))
    } yield destination
  }

  private def _checkpoint_c(prepared: Prepared)(using ctx: ExecutionContext): Consequence[Unit] =
    _uninterrupted_c.flatMap { _ => prepared.destination match {
      case None => Consequence.unit
      case Some(destination) => for {
        current <- _load_c(prepared.candidate)
        _ <- if (current == prepared.source) Consequence.unit else _refused
        _ <- _uninterrupted_c
        acknowledged <- store.checkpoint(prepared.source, destination, prepared.candidate.access)
        _ <- if (acknowledged.record == destination) Consequence.unit else _refused
        _ <- _uninterrupted_c
        footprint <- DurableJobLifetimeRecovery.footprint(acknowledged, resources, lifetimepolicy.lifetime)
        _ <- DurableJobLifetimeRecovery.prepare(store, acknowledged, prepared.candidate.access,
          ctx, lifetimepolicy, footprint, None)
        _ <- _uninterrupted_c
        finalread <- _load_c(prepared.candidate)
        _ <- if (finalread == acknowledged) Consequence.unit else _refused
      } yield ()
    }}

  private def _load_c(candidate: DurableJobStartupRecoveryCandidate)
      (using ctx: ExecutionContext): Consequence[DurableJobStoreSnapshot] =
    store.loadStartupRecovery(candidate.jobId, candidate.access) match {
      case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) => Consequence.success(snapshot)
      case _ => _refused
    }

  private def _terminal(outcome: DurableJobRecoveryOutcome): Boolean = outcome match {
    case DurableJobRecoveryOutcome.TerminalSucceeded | DurableJobRecoveryOutcome.TerminalFailed |
        DurableJobRecoveryOutcome.TerminalCancelled => true
    case _ => false
  }

  private def _uninterrupted_c: Consequence[Unit] =
    if (Thread.currentThread().isInterrupted) _refused else Consequence.unit

  private def _refused[A]: Consequence[A] = Consequence.stateInvalid("durable job migration checkpoint refused")
}
