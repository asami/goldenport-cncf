package org.goldenport.cncf.job

import org.goldenport.Consequence

/*
 * Closed prefix plus validated current-invocation suffix. Historical task
 * strings remain closed values and are never converted into live TaskIds.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobRecoveryCheckpointProjection {
  def basis(record: JobRecord, initial: DurableJobStoreSnapshot,
    current: DurableJobStoreSnapshot): Consequence[JobRecord] = DurableJobLifetimeQuotaPolicy.pure {
    val prefix = initial.record.body.timeline
    val timeline = current.record.body.timeline
    if (!timeline.startsWith(prefix)) DurableJobLifetimeQuotaPolicy.invalid
    else timeline.drop(prefix.size).foldLeft(Consequence.success(Vector.empty[JobTimelineEvent])) {
      case (acc, event) => acc.flatMap { events =>
        val task = event.taskId.flatMap(id => record.taskReadModels.find(_.taskId.value == id))
        if (event.taskId.nonEmpty && task.isEmpty) DurableJobLifetimeQuotaPolicy.invalid
        else Consequence.success(events :+ JobTimelineEvent(event.sequence, event.occurredAt,
          event.kind, task.map(_.taskId), None, event.summary))
      }
    }.map(events => record.copy(timeline = events,
      updatedAt = if (record.updatedAt.isAfter(current.record.body.identity.updatedAt)) record.updatedAt
        else current.record.body.identity.updatedAt))
  }

  def project(record: JobRecord, supplied: DurableJobLifecycleWriteEvidence,
    prepared: DurableJobLifetimeRecovery.Prepared, current: DurableJobStoreSnapshot,
    cancelled: Boolean): Consequence[DurableJobRecord] = DurableJobLifetimeQuotaPolicy.pure {
    val initial = prepared.snapshot.record.body
    val evidence = supplied.projection
    val prior = current.record.body
    val protectedsource = supplied.access == prepared.native.access &&
      evidence.authorization == initial.authorization && evidence.inputs == initial.inputs &&
      evidence.definitionSnapshot == initial.definitionSnapshot && evidence.retention == initial.retention &&
      evidence.calltreeReference == initial.calltreeReference &&
      record.id.value == initial.identity.jobId && record.createdAt == initial.identity.createdAt
    for {
      _ <- if (protectedsource && record.timeline.nonEmpty &&
          evidence.semanticRevision == Math.addExact(prior.identity.revision, 1L))
        Consequence.unit else DurableJobLifetimeQuotaPolicy.invalid
      sequence = Math.addExact(prior.timeline.map(_.sequence).foldLeft(0L)(math.max), 1L)
      event = record.timeline.last.copy(sequence = sequence)
      _ <- if (event.occurredAt.isAfter(prior.identity.updatedAt)) Consequence.unit
        else DurableJobLifetimeQuotaPolicy.invalid
      candidate = record.copy(timeline = record.timeline.dropRight(1) :+ event)
      projected <- if (cancelled) _cancel(candidate, evidence, prior)
        else DurableJobProjection.projectV2(candidate, evidence)
      tasks <- _merge_tasks(initial, projected.body.tasks, prepared.quota.seed.normalids,
        candidate.taskReadModels.nonEmpty)
      suffix = projected.body.timeline
      _ <- if (suffix.dropRight(1) == prior.timeline.drop(initial.timeline.size) &&
          suffix.last.sequence == sequence) Consequence.unit else DurableJobLifetimeQuotaPolicy.invalid
      result <- DurableJobRecord.createV2(projected.body.copy(tasks = tasks,
        timeline = initial.timeline ++ suffix))
      canonical <- DurableJobRecordCodec.canonicalJson(result)
      admitted <- DurableJobRecordCodec.decode(canonical, supplied.access)
      _ <- if (admitted == result) Consequence.unit else DurableJobLifetimeQuotaPolicy.invalid
    } yield result
  }

  private def _cancel(record: JobRecord, evidence: DurableJobProjectionEvidence,
    prior: DurableJobRecordBody): Consequence[DurableJobRecord] =
    if (record.status != JobStatus.Cancelled || record.taskReadModels.nonEmpty ||
        !evidence.result.isInstanceOf[DurableResultOutcome.Cancelled] || evidence.tasks != prior.tasks)
      DurableJobLifetimeQuotaPolicy.invalid
    else DurableJobRecord.createV2(prior.copy(
      identity = prior.identity.copy(revision = evidence.semanticRevision, updatedAt = record.updatedAt),
      lifecycle = prior.lifecycle.copy(status = DurableJobLifecycleStatus.Cancelled,
        schedule = prior.lifecycle.schedule.copy(completedAt = Some(record.updatedAt))),
      result = evidence.result, timeline = record.timeline.map(event => DurableTimelineEvent(
        event.sequence, event.occurredAt, event.kind, event.taskId.map(_.value), event.note))))

  private def _merge_tasks(initial: DurableJobRecordBody, live: Vector[DurableTaskDescriptor],
    associated: Set[String], actual: Boolean): Consequence[Vector[DurableTaskDescriptor]] = {
    val events = initial.timeline.flatMap(_.taskId).toSet
    var needed = initial.tasks.filter(task => !actual || associated.contains(task.taskId) ||
      events.contains(task.taskId) || task.transaction.outcome != DurableTransactionOutcome.Pending ||
      task.compensation.nonEmpty).map(_.taskId).toSet
    var changed = true
    while (changed) {
      val references = (initial.tasks.filter(task => needed.contains(task.taskId)) ++ live)
        .flatMap(task => task.parentTaskId.toVector ++ task.relation.reference.toVector ++
          task.compensation.map(_.compensatesTaskId).toVector).toSet
      val next = needed ++ references
      changed = next != needed
      needed = next
    }
    val retained = initial.tasks.filter(task => needed.contains(task.taskId))
    val conflicts = retained.exists { old => live.find(_.taskId == old.taskId).exists { next =>
      old.kind != next.kind || old.target != next.target || old.relation != next.relation ||
        old.parentTaskId != next.parentTaskId
    }}
    if (conflicts) DurableJobLifetimeQuotaPolicy.invalid
    else Consequence.success(retained.filterNot(old => live.exists(_.taskId == old.taskId)) ++ live)
  }
}
