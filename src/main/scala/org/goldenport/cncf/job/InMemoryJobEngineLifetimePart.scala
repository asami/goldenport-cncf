package org.goldenport.cncf.job

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import org.goldenport.Consequence

/*
 * Lifetime preflight owns cardinality and paid capacity, never user metadata.
 * Work is charged once at admission; dispatch and settlement use that payment.
 *
 * @since   Oct. 1, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineLifetimePart { self: InMemoryJobEngine =>
  private[job] def _native_binding(jobid: JobId): Option[JobDurableLifetimeRuntimeBinding] =
    _state_monitor.synchronized { runtimeState._native_lifetime_bindings.get(jobid) }

  private[job] def _native_runtime_refused(jobid: JobId): Boolean =
    _state_monitor.synchronized { _native_binding(jobid).exists(_.refused) }

  private[job] def _native_snapshot(jobid: JobId, binding: JobDurableLifetimeRuntimeBinding)
      : Consequence[DurableJobLifetimeQuotaSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      binding.native.quota.snapshot(jobid.value, binding.native.access)(using binding.context)
    }

  private def _refresh_native_seed(jobid: JobId, binding: JobDurableLifetimeRuntimeBinding)
      : Consequence[DurableJobLifetimeQuotaSnapshot] =
    _native_snapshot(jobid, binding).map { snapshot =>
      runtimeState._lifetime_budget.replaceNativeSeed(jobid, snapshot.seed)
      snapshot
    }

  private[job] def lifetimeSnapshot(jobId: JobId): Option[JobLifetimeSnapshot] =
    _state_monitor.synchronized {
      _native_binding(jobId) match {
        case Some(binding) => _refresh_native_seed(jobId, binding).toOption.map(_.seed.paid)
        case None => runtimeState._lifetime_budget.snapshot(jobId)
      }
    }

  private[job] def _prepare_lifetime_root(
    tasks: List[JobTask],
    notes: Vector[String]
  ): Consequence[JobLifetimeBudget.Ticket] =
    JobLifetimeBootstrap.taskCount(tasks, _resource_budget.policy).flatMap { count =>
      JobLifetimeBootstrap.noteCount(notes, runtimeState._lifetime_budget.policy).flatMap { rows =>
        runtimeState._lifetime_budget.prepareRoot(count, rows)
      }
    }

  private[job] def _reserve_lifetime_work(jobid: JobId, tasks: List[JobTask])(
    reserve: => Consequence[JobResourceBudget.Lease]
  ): Consequence[JobResourceBudget.Lease] =
    _reserve_lifetime_expansion(jobid, tasks, retry = false)(reserve)

  private[job] def _reserve_lifetime_retry(jobid: JobId, tasks: List[JobTask])(
    reserve: => Consequence[JobResourceBudget.Lease]
  ): Consequence[JobResourceBudget.Lease] =
    _reserve_lifetime_expansion(jobid, tasks, retry = true)(reserve)

  private def _reserve_lifetime_expansion(jobid: JobId, tasks: List[JobTask], retry: Boolean)(
    reserve: => Consequence[JobResourceBudget.Lease]
  ): Consequence[JobResourceBudget.Lease] =
    _state_monitor.synchronized {
      JobLifetimeBootstrap.taskCount(tasks, _resource_budget.policy).flatMap { count =>
        val ticket = JobLifetimeBudget.work(count)
        _prepare_lifetime_ticket(jobid, ticket, normal = true).flatMap { _ =>
          reserve.flatMap { lease =>
            _pay_lifetime_work(jobid, count, retry) match {
              case Consequence.Success(_) => Consequence.success(lease)
              case Consequence.Failure(conclusion) =>
                _resource_budget.release(lease)
                Consequence.Failure(conclusion)
            }
          }
        }
      }
    }

  private[job] def _prepare_lifetime_ticket(
    jobid: JobId, ticket: JobLifetimeBudget.Ticket, normal: Boolean
  ): Consequence[Unit] =
    _native_binding(jobid) match {
      case Some(binding) if normal && binding.refused => DurableJobLifetimeQuotaPolicy.unavailable
      case Some(_) =>
        if (runtimeState._lifetime_budget.policy._fits(ticket.paid)) Consequence.unit
        else JobResourcePolicy.refused
      case None => runtimeState._lifetime_budget.prepare(jobid, ticket).map(_ => ())
    }

  private[job] def _pay_lifetime_work(jobid: JobId, count: Int, retry: Boolean): Consequence[Unit] =
    _native_binding(jobid) match {
      case Some(binding) =>
        DurableJobLifetimeQuotaPolicy.protect {
          val quota = binding.native.quota
          if (retry) quota.reserveRetry(jobid.value, binding.native.access, count)(using binding.context)
          else quota.reserveWork(jobid.value, binding.native.access, count)(using binding.context)
        }.map { snapshot =>
          runtimeState._lifetime_budget.replaceNativeSeed(jobid, snapshot.seed)
          if (retry) {
            binding.cancellationSettled = false
            _replace_cancellation_scope(jobid)
          }
          ()
        }
      case None =>
        runtimeState._lifetime_budget.commit(jobid, JobLifetimeBudget.work(count))
        if (retry) _replace_cancellation_scope(jobid)
        Consequence.unit
    }

  private[job] def _associate_lifetime_task(jobid: JobId, taskid: TaskId): Consequence[Unit] =
    _state_monitor.synchronized {
      _native_binding(jobid) match {
        case Some(binding) if binding.refused => DurableJobLifetimeQuotaPolicy.unavailable
        case Some(binding) =>
          val admitted = DurableJobLifetimeQuotaPolicy.protect {
            binding.native.quota.associate(jobid.value, binding.native.access, taskid.value)(using binding.context)
          }.flatMap { acknowledged =>
            if (!acknowledged) JobResourcePolicy.refused
            else _refresh_native_seed(jobid, binding).flatMap { snapshot =>
              if (snapshot.cancelclaimed) {
                _apply_safety_cancel(jobid)
                JobResourcePolicy.refused
              } else Consequence.unit
            }
          }
          admitted match {
            case Consequence.Failure(_) if !_get_record(jobid).exists(_.status == JobStatus.Cancelled) =>
              binding.refused = true
            case _ => ()
          }
          admitted
        case None =>
          runtimeState._lifetime_budget.associate(jobid, taskid)
          Consequence.unit
      }
    }

  private[job] def _claim_lifetime_compensation(jobid: JobId, taskid: TaskId): Consequence[Boolean] =
    _state_monitor.synchronized {
      _native_binding(jobid) match {
        case Some(binding) if binding._has_uncertain_claim(taskid) => DurableJobLifetimeQuotaPolicy.unavailable
        case Some(binding) =>
          val selected = DurableJobLifetimeQuotaPolicy.protect {
            binding.native.quota.claimCompensation(jobid.value, binding.native.access, taskid.value)(using binding.context)
          }.flatMap {
            case false => Consequence.success(false)
            case true => _refresh_native_seed(jobid, binding).map(_ => true)
          }
          selected match {
            case Consequence.Failure(_) =>
              binding._remember_uncertain_claim(taskid, runtimeState._lifetime_budget.policy.maxchargedtasks / 2L)
            case _ => ()
          }
          selected
        case None => Consequence.success(runtimeState._lifetime_budget.claimCompensation(jobid, taskid))
      }
    }

  private[job] def _pay_lifetime_annotation(jobid: JobId, count: Long): Consequence[Unit] = {
    val ticket = JobLifetimeBudget.annotation(count)
    _prepare_lifetime_ticket(jobid, ticket, normal = false).flatMap { _ =>
      _native_binding(jobid) match {
        case Some(binding) => DurableJobLifetimeQuotaPolicy.protect {
          binding.native.quota.reserveAnnotation(jobid.value, binding.native.access, count)(using binding.context)
        }.map(snapshot => runtimeState._lifetime_budget.replaceNativeSeed(jobid, snapshot.seed))
        case None =>
          runtimeState._lifetime_budget.commit(jobid, ticket)
          Consequence.unit
      }
    }
  }

  private[job] def _pay_lifetime_control(jobid: JobId, command: JobControlCommand): Consequence[Unit] =
    _native_binding(jobid) match {
      case Some(binding) if command == JobControlCommand.Cancel =>
        DurableJobLifetimeQuotaPolicy.protect {
          binding.native.quota.cancel(jobid.value, binding.native.access)(using binding.context)
        }.map(_ => ())
      case Some(binding) =>
        _prepare_lifetime_ticket(jobid, JobLifetimeBudget.control, normal = false).flatMap { _ =>
          DurableJobLifetimeQuotaPolicy.protect {
            binding.native.quota.reserveControl(jobid.value, binding.native.access)(using binding.context)
          }.map(snapshot => runtimeState._lifetime_budget.replaceNativeSeed(jobid, snapshot.seed))
        }
      case None if command == JobControlCommand.Cancel => runtimeState._lifetime_budget.cancel(jobid)
      case None => runtimeState._lifetime_budget.prepare(jobid, JobLifetimeBudget.control).map { ticket =>
        runtimeState._lifetime_budget.commit(jobid, ticket)
      }
    }

  private[job] def _native_dispatch_allowed(jobid: JobId): Boolean =
    _native_binding(jobid) match {
      case None => true
      case Some(binding) => _refresh_native_seed(jobid, binding) match {
        case Consequence.Success(snapshot) if snapshot.cancelclaimed =>
          _apply_safety_cancel(jobid)
          false
        case Consequence.Success(_) => !binding.refused
        case Consequence.Failure(_) => false
      }
    }

  private[job] def _finish_native_bootstrap_cancellation(): Unit =
    _state_monitor.synchronized {
      runtimeState._bootstrap_cancelled.foreach(_apply_safety_cancel)
    }
}

private[job] object JobLifetimeBootstrap {
  import JobLifetimeBudget.Seed

  def taskCount(tasks: List[JobTask], resources: JobResourcePolicy): Consequence[Int] =
    if (tasks == null || tasks.lengthCompare(resources.maxtaskspersubmission) > 0 || tasks.exists(_ == null))
      JobResourcePolicy.refused
    else Consequence.success(tasks.size)

  def noteCount(notes: Vector[String], policy: JobLifetimePolicy): Consequence[Long] =
    if (notes == null || notes.size.toLong > policy.maxhistorycredits || notes.exists(_ == null))
      JobResourcePolicy.refused
    else Consequence.success(notes.size.toLong)

  final case class Staged(local: Map[JobId, Seed], native: Map[JobId, Seed], cancelled: Set[JobId])

  def stage(
    durable: ConcurrentHashMap[JobId, JobRecord],
    runtime: ConcurrentHashMap[JobId, JobRecord],
    resources: JobResourcePolicy,
    budget: JobLifetimeBudget,
    bindings: Map[JobId, JobDurableLifetimeRuntimeBinding]
  ): Consequence[Staged] = {
    val records = mutable.HashMap.empty[JobId, Vector[(JobRecord, Seed)]]
    var result: Consequence[Unit] = Consequence.unit
    Vector(durable, runtime).foreach { map =>
      val entries = map.entrySet().iterator()
      var count = 0
      while (entries.hasNext && result.isSuccess) {
        val entry = entries.next()
        val record = entry.getValue
        val id = entry.getKey
        count += 1
        result = if (count > resources.maxretainedjobs) JobResourcePolicy.refused
        else runtimeFootprint(record, resources, budget).flatMap { footprint =>
          if (id == null || record.id != id) JobResourcePolicy.refused
          else {
            records.put(id, records.getOrElse(id, Vector.empty) :+ (record -> footprint))
            Consequence.unit
          }
        }
      }
    }
    if (records.size > resources.maxretainedjobs) result = JobResourcePolicy.refused
    val local = mutable.HashMap.empty[JobId, Seed]
    val native = mutable.HashMap.empty[JobId, Seed]
    var cancelled = Set.empty[JobId]
    // Complete pure validation of both maps precedes every provider access.
    records.iterator.filterNot(entry => bindings.contains(entry._1)).foreach { case (id, values) =>
      if (result.isSuccess) values.foreach { case (record, _) =>
        if (result.isSuccess) result = runtimeSeed(record, resources, budget).flatMap { seed =>
          val candidate = local.get(id).map(_.maximum(seed)).getOrElse(seed)
          if (!budget.acceptsSeed(id, candidate)) JobResourcePolicy.refused
          else { local.put(id, candidate); Consequence.unit }
        }
      }
    }
    records.iterator.filter(entry => bindings.contains(entry._1)).foreach { case (id, values) =>
      if (result.isSuccess) {
        val binding = bindings(id)
        result = DurableJobLifetimeQuotaPolicy.protect {
          binding.native.quota.snapshot(id.value, binding.native.access)(using binding.context)
        }.flatMap { snapshot =>
          val seed = snapshot.seed
          val valid = budget.policy._fits(seed.paid) && values.forall { case (_, footprint) =>
            footprint.normalids.subsetOf(seed.normalids) && footprint.claims.subsetOf(seed.claims) &&
              footprint.paid.chargedtasks <= seed.paid.chargedtasks &&
              footprint.paid.historycredits <= seed.paid.historycredits
          }
          if (!valid) JobResourcePolicy.refused
          else {
            native.put(id, seed)
            if (snapshot.cancelclaimed) cancelled += id
            Consequence.unit
          }
        }
      }
    }
    result.map(_ => Staged(local.toMap, native.toMap, cancelled))
  }

  def runtimeFootprint(record: JobRecord, resources: JobResourcePolicy,
    budget: JobLifetimeBudget): Consequence[Seed] = {
    val policy = budget.policy
    if (record == null || record.id == null || record.debug == null || record.status == null ||
        record.taskReadModels == null || record.taskDefinitions == null || record.timeline == null ||
        record.retry == null || record.retry.attemptCount < 0 ||
        record.retry.attemptCount > resources.maxretryattempts || record.retry.maxAttempts < 0 ||
        record.retry.maxAttempts > resources.maxretryattempts ||
        record.taskReadModels.size.toLong > policy.maxchargedtasks ||
        record.taskDefinitions.size.toLong > policy.maxchargedtasks ||
        record.timeline.size.toLong > policy.maxhistorycredits)
      JobResourcePolicy.refused
    else taskCount(record.tasks, resources).flatMap { count =>
      noteCount(record.debug.executionNotes, policy).flatMap { notes =>
        if (record.timeline.size.toLong > policy.maxhistorycredits - notes ||
            record.timeline.exists(_ == null) ||
            record.taskDefinitions.exists { case (id, task) => id == null || task == null } ||
            record.taskReadModels.exists(task => task == null || task.taskId == null ||
              task.relation == null || task.compensationStatus == null || task.compensatesTaskId == null ||
              task.compensatesTaskId.exists(_ == null)))
          JobResourcePolicy.refused
        else {
          val models = record.taskReadModels
          val normalmodels = models.filterNot(_.relation.contains("compensation"))
          val normalids = normalmodels.map(_.taskId.value).toSet
          val claims = normalmodels.filter(_.compensationStatus.nonEmpty).map(_.taskId.value).toSet ++
            models.flatMap(_.compensatesTaskId).map(_.value)
          val rows = record.timeline.size.toLong + notes
          Consequence.success(Seed(JobLifetimeSnapshot(
            math.max(models.size.toLong, record.taskDefinitions.size.toLong), rows, 0L, 0L),
            0L, normalids, claims))
        }
      }
    }
  }

  def runtimeSeed(record: JobRecord, resources: JobResourcePolicy,
    budget: JobLifetimeBudget): Consequence[Seed] =
    runtimeFootprint(record, resources, budget).flatMap { footprint =>
      val existing = budget.snapshot(record.id).nonEmpty
      val prospective = !existing && (record.status == JobStatus.Submitted || record.status == JobStatus.Running)
      val normals = record.taskReadModels.count(!_.relation.contains("compensation")).toLong
      val seed = _floor(normals, record.taskReadModels.size.toLong, record.taskDefinitions.size.toLong,
        footprint.paid.historycredits, if (prospective) Some(record.tasks.size) else None,
        footprint.normalids, footprint.claims, existing)
      if (budget.acceptsSeed(record.id, seed)) Consequence.success(seed) else JobResourcePolicy.refused
    }

  private def _floor(normals: Long, total: Long, definitions: Long, rows: Long,
    prospective: Option[Int], normalids: Set[String], claims: Set[String], existing: Boolean): Seed = {
    // Counts were bounded before traversal; all products are bounded Long values.
    val tasks = math.max(2L * normals, math.max(total, definitions))
    val history = math.max(rows, 64L * normals)
    val admissions = if (existing) 0L else math.max(1L, math.max((rows + 31L) / 32L, normals))
    val work = prospective.map(JobLifetimeBudget.work(_))
    Seed(JobLifetimeSnapshot(tasks + work.map(_.paid.chargedtasks).getOrElse(0L),
      history + work.map(_.paid.historycredits).getOrElse(0L),
      admissions + work.map(_.paid.admissions).getOrElse(0L), 1L),
      work.map(_.normals).getOrElse(0L), normalids, claims)
  }
}
