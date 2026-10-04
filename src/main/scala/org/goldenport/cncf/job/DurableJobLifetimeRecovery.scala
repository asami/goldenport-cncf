package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Pure observed obligations precede the read-only Published authority read.
 * This preparation never manufactures lifetime credit or parses historic ids.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobLifetimeRecovery {
  final case class Footprint(tasks: Long, rows: Long, executed: Set[String],
    claims: Set[String], eventids: Set[String])
  final case class Prepared(snapshot: DurableJobStoreSnapshot, native: DurableJobLifetimeBinding,
    quota: DurableJobLifetimeQuotaSnapshot, context: ExecutionContext)

  def footprint(snapshot: DurableJobStoreSnapshot, resources: JobResourcePolicy,
    policy: JobLifetimePolicy): Consequence[Footprint] = DurableJobLifetimeQuotaPolicy.pure {
    val body = snapshot.record.body
    val retry = body.lifecycle.retry
    if (snapshot.record.format != DurableRecordFormat.V2 || body.tasks == null ||
        body.timeline == null || body.diagnostics == null || retry.attempts == null ||
        body.tasks.size.toLong > policy.maxchargedtasks ||
        body.timeline.size.toLong > policy.maxhistorycredits ||
        body.diagnostics.size.toLong > policy.maxhistorycredits - body.timeline.size.toLong ||
        retry.attempts.size > resources.maxretryattempts || retry.maxAttempts < 0 ||
        retry.maxAttempts > resources.maxretryattempts ||
        body.tasks.exists(task => task == null || task.relation == null || task.compensation == null) ||
        body.timeline.exists(_ == null) || body.diagnostics.exists(_ == null) ||
        retry.attempts.exists(_ == null)) JobResourcePolicy.refused
    else {
      val ids = body.tasks.map(_.taskId).toSet
      val eventids = body.timeline.flatMap(_.taskId).toSet
      val normal = body.tasks.filterNot(_.relation.kind == DurableTaskRelationKind.Compensation)
      val executed = normal.filter(task => task.transaction.outcome != DurableTransactionOutcome.Pending ||
        eventids.contains(task.taskId)).map(_.taskId).toSet
      val claims = normal.filter(_.compensation.nonEmpty).map(_.taskId).toSet ++
        body.tasks.filter(_.relation.kind == DurableTaskRelationKind.Compensation)
          .flatMap(_.compensation.map(_.compensatesTaskId))
      val tasks = math.max(body.tasks.size.toLong, Math.multiplyExact(2L, executed.size.toLong))
      val rows = Math.addExact(body.timeline.size.toLong, body.diagnostics.size.toLong)
      if (!eventids.subsetOf(ids) || tasks > policy.maxchargedtasks) JobResourcePolicy.refused
      else Consequence.success(Footprint(tasks, rows, executed, claims, eventids))
    }
  }

  def prepare(store: DurableJobStore, snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess, context: ExecutionContext, policy: DurableJobLifetimeQuotaPolicy,
    footprint: Footprint, liveCount: Option[Int]): Consequence[Prepared] =
    DurableJobLifetimeQuotaPolicy.protect {
      val quota = store.lifetimeQuota(policy)
      for {
        _ <- policy.validate
        native <- quota.recoverySnapshot(snapshot, access, store)(using context)
        _ <- _paid_footprint_c(snapshot.record.body, native, policy, footprint, liveCount)
      } yield Prepared(snapshot, DurableJobLifetimeBinding(quota, access), native, context)
    }

  def prepareMigration(store: DurableJobStore, source: DurableJobStoreSnapshot,
    destination: DurableJobRecord, access: DurableJobRecordAccess, context: ExecutionContext,
    policy: DurableJobLifetimeQuotaPolicy, footprint: Footprint): Consequence[Unit] =
    DurableJobLifetimeQuotaPolicy.protect {
      for {
        _ <- if (store != null && source != null && source.record != null && destination != null &&
            policy != null && context != null && footprint != null) Consequence.unit
          else JobResourcePolicy.refused
        _ <- policy.validate
        body = source.record.body
        _ <- if (source.record.format == DurableRecordFormat.V1 &&
            body.retention.deletionState == DurableDeletionState.Active &&
            body.identity.revision < Long.MaxValue && destination.format == DurableRecordFormat.V2 &&
            destination.body == body.copy(identity = body.identity.copy(revision = body.identity.revision + 1L)))
          Consequence.unit else JobResourcePolicy.refused
        loaded <- store.load(body.identity.jobId, access)(using context)
        _ <- if (loaded.contains(source)) Consequence.unit else JobResourcePolicy.refused
        native <- store.lifetimeQuota(policy).snapshot(body.identity.jobId, access)(using context)
        _ <- _paid_footprint_c(destination.body, native, policy, footprint, None)
      } yield ()
    }

  private def _paid_footprint_c(body: DurableJobRecordBody, native: DurableJobLifetimeQuotaSnapshot,
    policy: DurableJobLifetimeQuotaPolicy, footprint: Footprint, livecount: Option[Int]): Consequence[Unit] = {
    val seed = native.seed
    val normal = body.tasks.filterNot(_.relation.kind == DurableTaskRelationKind.Compensation)
    val executed = footprint.executed ++ normal.filter(task => seed.normalids.contains(task.taskId)).map(_.taskId)
    val tasks = math.max(footprint.tasks, Math.multiplyExact(2L, executed.size.toLong))
    if (policy.lifetime._fits(seed.paid) && tasks <= seed.paid.chargedtasks &&
        footprint.rows <= seed.paid.historycredits && executed.subsetOf(seed.normalids) &&
        footprint.claims.subsetOf(seed.normalids) && footprint.claims.subsetOf(seed.claims) &&
        (native.cancelclaimed || livecount.forall(_.toLong <= seed.remaining)))
      Consequence.unit else JobResourcePolicy.refused
  }
}
