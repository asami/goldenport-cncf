package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore

/*
 * Native paid lifetime authority. Root publication and every mutation require
 * authenticated signed Job proof; this owner never writes or executes that Job.
 * Stale OCC alone permits retry. Ambiguous acknowledgements retain all charges.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobLifetimeQuota(
  entitystore: EntityStore,
  policy: DurableJobLifetimeQuotaPolicy = DurableJobLifetimeQuotaPolicy.default
) {
  private val _provider = new DurableJobLifetimeQuotaProvider(entitystore, policy)
  private val _store = new DurableJobStore(entitystore)

  def reserveRoot(record: DurableJobRecord, access: DurableJobRecordAccess,
    taskCount: Int, noteCount: Long = 0L)(using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaLease] =
    DurableJobLifetimeQuotaPolicy.protect {
      for {
        admitted <- _root_proposal(record, access, taskCount, noteCount)
        (canonical, ticket) = admitted
        jobid = record.body.identity.jobId
        token = UUID.randomUUID().toString.replace("-", "")
        entry = DurableJobLifetimeQuotaEntry(jobid, DurableJobOwnedBlobPayloads.ownerFingerprint(record),
          _hash(canonical), token, DurableJobLifetimeQuotaRootState.Pending, ticket.paid,
          ticket.normals, Vector.empty, Vector.empty, false)
        lease = DurableJobLifetimeQuotaLease(jobid, token, entry.rootsha256, entry.ownersha256)
        _ <- _provider.admit()
        result <- _change(DurableJobLifetimeQuotaPolicy.maximumAttempts, true) { current =>
          for {
            native <- _store.load(jobid, access).recoverWith(_ => DurableJobLifetimeQuotaPolicy.unavailable)
            _ <- if (native.isEmpty && !current.entries.exists(_.jobid == jobid)) Consequence.unit
              else DurableJobLifetimeQuotaPolicy.invalid
            changed <- _capacity(current.copy(entries = (current.entries :+ entry).sortBy(_.jobid)))
          } yield changed -> lease
        }
      } yield result
    }

  def publishRoot(lease: DurableJobLifetimeQuotaLease, access: DurableJobRecordAccess)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      (for {
        _ <- _validate_policy()
        _ <- if (_valid_lease(lease) && _valid_access(access)) Consequence.unit
          else DurableJobLifetimeQuotaPolicy.confirmation
        _ <- _provider.admit()
        result <- _change(DurableJobLifetimeQuotaPolicy.maximumAttempts, false) { current =>
          _entry(current, lease.jobid).flatMap { entry =>
            if (entry.roottoken != lease.roottoken || entry.rootsha256 != lease.rootsha256 ||
                entry.ownersha256 != lease.ownersha256) DurableJobLifetimeQuotaPolicy.confirmation
            else _publish(current, entry, access)
          }
        }
      } yield result).recoverWith(_ => DurableJobLifetimeQuotaPolicy.confirmation)
    }.recoverWith(_ => DurableJobLifetimeQuotaPolicy.confirmation)

  def recoverRoot(jobId: String, access: DurableJobRecordAccess)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      (for {
        _ <- _admit_request(jobId, access)
        _ <- _provider.admit()
        result <- _change(DurableJobLifetimeQuotaPolicy.maximumAttempts, false) { current =>
          _entry(current, jobId).flatMap(entry => _publish(current, entry, access))
        }
      } yield result).recoverWith(_ => DurableJobLifetimeQuotaPolicy.confirmation)
    }.recoverWith(_ => DurableJobLifetimeQuotaPolicy.confirmation)

  def snapshot(jobId: String, access: DurableJobRecordAccess)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      for {
        _ <- _admit_request(jobId, access)
        _ <- _provider.admit()
        loaded <- _provider.load()
        current <- loaded.map(value => Consequence.success(value._2)).getOrElse(DurableJobLifetimeQuotaPolicy.unavailable)
        entry <- _published(current, jobId, access)
      } yield entry.snapshot
    }

  def resourceSnapshot()(using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaResourceSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      for {
        _ <- _validate_policy()
        _ <- _provider.admit()
        loaded <- _provider.load()
        record <- loaded.map(value => Consequence.success(value._2)).getOrElse(DurableJobLifetimeQuotaPolicy.unavailable)
        result <- DurableJobLifetimeQuotaCodec.usage(record)
      } yield result
    }

  def recoverySnapshot(snapshot: DurableJobStoreSnapshot, access: DurableJobRecordAccess,
    store: DurableJobStore)(using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      for {
        _ <- if (snapshot != null && store != null && snapshot.record.format == DurableRecordFormat.V2)
          Consequence.unit else DurableJobLifetimeQuotaPolicy.unavailable
        jobid = snapshot.record.body.identity.jobId
        _ <- _admit_request(jobid, access)
        _ <- _provider.admit()
        loaded <- _provider.load()
        current <- loaded.map(value => Consequence.success(value._2))
          .getOrElse(DurableJobLifetimeQuotaPolicy.unavailable)
        entry <- _entry(current, jobid)
        _ <- if (entry.rootstate == DurableJobLifetimeQuotaRootState.Published) Consequence.unit
          else DurableJobLifetimeQuotaPolicy.unavailable
        native <- _authorized(entry, access, store)
        _ <- if (native == snapshot) Consequence.unit else DurableJobLifetimeQuotaPolicy.unavailable
      } yield entry.snapshot
    }

  def reserveWork(jobId: String, access: DurableJobRecordAccess, taskCount: Int)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    _work(jobId, access, taskCount, false)

  def reserveRetry(jobId: String, access: DurableJobRecordAccess, taskCount: Int)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    _work(jobId, access, taskCount, true)

  def reserveControl(jobId: String, access: DurableJobRecordAccess)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    _expand(jobId, access, JobLifetimeBudget.control, false, false)

  def reserveAnnotation(jobId: String, access: DurableJobRecordAccess, noteCount: Long)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      _validate_policy().flatMap { _ =>
        if (noteCount < 0L || noteCount > policy.lifetime.maxhistorycredits) JobResourcePolicy.refused
        else _expand(jobId, access, JobLifetimeBudget.annotation(noteCount), false, true)
      }
    }

  def associate(jobId: String, access: DurableJobRecordAccess, taskId: String)
      (using ctx: ExecutionContext): Consequence[Boolean] =
    _operate(jobId, access, Some(taskId)) { entry =>
      if (entry.normalids.contains(taskId)) Consequence.success(entry -> true)
      else if (entry.remaining <= 0L || entry.cancelclaimed) JobResourcePolicy.refused
      else Consequence.success(entry.copy(remaining = entry.remaining - 1L,
        normalids = (entry.normalids :+ taskId).sorted) -> true)
    }

  def claimCompensation(jobId: String, access: DurableJobRecordAccess, originalTaskId: String)
      (using ctx: ExecutionContext): Consequence[Boolean] =
    _operate(jobId, access, Some(originalTaskId)) { entry =>
      if (!entry.normalids.contains(originalTaskId) || entry.claims.contains(originalTaskId))
        Consequence.success(entry -> false)
      else Consequence.success(entry.copy(claims = (entry.claims :+ originalTaskId).sorted) -> true)
    }

  def cancel(jobId: String, access: DurableJobRecordAccess)(using ctx: ExecutionContext): Consequence[Boolean] =
    _operate(jobId, access, None) { entry =>
      if (entry.cancelclaimed) Consequence.success(entry -> false)
      else if (entry.paid.cancelcredits <= 0L) JobResourcePolicy.refused
      else Consequence.success(entry.copy(paid = entry.paid.copy(cancelcredits = entry.paid.cancelcredits - 1L),
        cancelclaimed = true) -> true)
    }

  private def _work(jobid: String, access: DurableJobRecordAccess, taskcount: Int, retry: Boolean)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    DurableJobLifetimeQuotaPolicy.protect {
      _validate_policy().flatMap { _ =>
        if (taskcount < 0 || taskcount > 1024) JobResourcePolicy.refused
        else _expand(jobid, access, JobLifetimeBudget.work(taskcount), retry, false)
      }
    }

  private def _expand(jobid: String, access: DurableJobRecordAccess, ticket: JobLifetimeBudget.Ticket,
    retry: Boolean, annotation: Boolean)(using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaSnapshot] =
    _operate(jobid, access, None) { entry =>
      val paid = entry.paid
      val delta = ticket.paid
      if ((entry.cancelclaimed && !retry && !annotation) || !policy.lifetime._fits(delta) ||
          delta.chargedtasks > policy.lifetime.maxchargedtasks - paid.chargedtasks ||
          delta.historycredits > policy.lifetime.maxhistorycredits - paid.historycredits ||
          delta.admissions > policy.lifetime.maxadmissions - paid.admissions)
        JobResourcePolicy.refused
      else {
        val changed = entry.copy(paid = JobLifetimeSnapshot(Math.addExact(paid.chargedtasks, delta.chargedtasks),
          Math.addExact(paid.historycredits, delta.historycredits), Math.addExact(paid.admissions, delta.admissions),
          Math.addExact(paid.cancelcredits, delta.cancelcredits)),
          remaining = Math.addExact(entry.remaining, ticket.normals),
          cancelclaimed = if (retry) false else entry.cancelclaimed)
        Consequence.success(changed -> changed.snapshot)
      }
    }

  private def _operate[A](jobid: String, access: DurableJobRecordAccess, taskid: Option[String])(
    change: DurableJobLifetimeQuotaEntry => Consequence[(DurableJobLifetimeQuotaEntry, A)]
  )(using ctx: ExecutionContext): Consequence[A] = DurableJobLifetimeQuotaPolicy.protect {
    for {
      _ <- _admit_request(jobid, access)
      _ <- if (taskid.forall(DurableJobLifetimeQuotaPolicy.validId)) Consequence.unit
        else DurableJobLifetimeQuotaPolicy.invalid
      _ <- _provider.admit()
      result <- _change(DurableJobLifetimeQuotaPolicy.maximumAttempts, false) { current =>
        for {
          entry <- _published(current, jobid, access)
          selected <- change(entry)
          (replacement, value) = selected
          changed <- _capacity(_replace(current, replacement))
        } yield changed -> value
      }
    } yield result
  }

  private def _publish(current: DurableJobLifetimeQuotaRecord, entry: DurableJobLifetimeQuotaEntry,
    access: DurableJobRecordAccess)(using ctx: ExecutionContext)
      : Consequence[(DurableJobLifetimeQuotaRecord, DurableJobLifetimeQuotaSnapshot)] =
    for {
      native <- _authorized(entry, access)
      canonical <- DurableJobRecordCodec.canonicalJson(native.record).recoverWith(_ => DurableJobLifetimeQuotaPolicy.confirmation)
      _ <- if (entry.rootstate == DurableJobLifetimeQuotaRootState.Published ||
          native.record.body.identity.revision == 1L && _hash(canonical) == entry.rootsha256)
        Consequence.unit else DurableJobLifetimeQuotaPolicy.confirmation
      changed = entry.copy(rootstate = DurableJobLifetimeQuotaRootState.Published)
    } yield _replace(current, changed) -> changed.snapshot

  private def _authorized(entry: DurableJobLifetimeQuotaEntry, access: DurableJobRecordAccess,
    store: DurableJobStore = _store)
      (using ctx: ExecutionContext): Consequence[DurableJobStoreSnapshot] =
    for {
      loaded <- store.load(entry.jobid, access).recoverWith(_ => DurableJobLifetimeQuotaPolicy.unavailable)
      native <- loaded.map(Consequence.success).getOrElse(DurableJobLifetimeQuotaPolicy.unavailable)
      _ <- if (DurableJobOwnedBlobPayloads.ownerFingerprint(native.record) == entry.ownersha256)
        Consequence.unit else DurableJobLifetimeQuotaPolicy.unavailable
    } yield native

  private def _published(current: DurableJobLifetimeQuotaRecord, jobid: String, access: DurableJobRecordAccess)
      (using ctx: ExecutionContext): Consequence[DurableJobLifetimeQuotaEntry] =
    for {
      entry <- _entry(current, jobid)
      _ <- if (entry.rootstate == DurableJobLifetimeQuotaRootState.Published) Consequence.unit
        else DurableJobLifetimeQuotaPolicy.unavailable
      _ <- _authorized(entry, access)
    } yield entry

  private def _entry(current: DurableJobLifetimeQuotaRecord, jobid: String): Consequence[DurableJobLifetimeQuotaEntry] =
    current.entries.find(_.jobid == jobid).map(Consequence.success).getOrElse(DurableJobLifetimeQuotaPolicy.unavailable)

  private def _replace(current: DurableJobLifetimeQuotaRecord, entry: DurableJobLifetimeQuotaEntry)
      : DurableJobLifetimeQuotaRecord = current.copy(entries = current.entries.map(value =>
    if (value.jobid == entry.jobid) entry else value))

  private def _change[A](attempts: Int, bootstrap: Boolean)(
    select: DurableJobLifetimeQuotaRecord => Consequence[(DurableJobLifetimeQuotaRecord, A)]
  )(using ctx: ExecutionContext): Consequence[A] =
    if (attempts <= 0) DurableJobLifetimeQuotaPolicy.unavailable
    else _provider.load().flatMap {
      case None if bootstrap =>
        for {
          _ <- _provider.proveEmpty()
          selected <- select(DurableJobLifetimeQuotaRecord(policy, Vector.empty))
          (record, value) = selected
          result <- _provider.create(record) match {
            case Consequence.Success(_) => Consequence.success(value)
            case Consequence.Failure(conclusion) if _provider.isDuplicate(conclusion) =>
              _change(attempts - 1, bootstrap)(select)
            case Consequence.Failure(_) => DurableJobLifetimeQuotaPolicy.unavailable
          }
        } yield result
      case None => DurableJobLifetimeQuotaPolicy.unavailable
      case Some((carrier, current)) => select(current).flatMap { case (record, value) =>
        if (record == current) Consequence.success(value)
        else _provider.update(carrier, record) match {
          case Consequence.Success(_) => Consequence.success(value)
          case Consequence.Failure(conclusion) if _provider.isStale(conclusion) =>
            _change(attempts - 1, bootstrap)(select)
          case Consequence.Failure(_) => DurableJobLifetimeQuotaPolicy.unavailable
        }
      }
    }

  private def _capacity(record: DurableJobLifetimeQuotaRecord): Consequence[DurableJobLifetimeQuotaRecord] = {
    val held = record.entries.foldLeft(0L)((total, entry) => Math.addExact(total, entry.heldBytes))
    if (record.entries.size > policy.maxledgerjobs || held > policy.maxledgerbytes) JobResourcePolicy.refused
    else DurableJobLifetimeQuotaCodec.encode(record).map(_ => record)
  }

  private def _validate_policy(): Consequence[Unit] =
    if (policy == null) DurableJobLifetimeQuotaPolicy.invalid else policy.validate

  private def _admit_request(jobid: String, access: DurableJobRecordAccess): Consequence[Unit] =
    _validate_policy().flatMap { _ =>
      if (DurableJobLifetimeQuotaPolicy.validId(jobid) && _valid_access(access)) Consequence.unit
      else DurableJobLifetimeQuotaPolicy.invalid
    }

  private def _valid_access(access: DurableJobRecordAccess): Boolean = access != null &&
    access.tenantId != null && !access.tenantId.isBlank && access.subjectId != null &&
    !access.subjectId.isBlank && access.scopes != null && access.scopes.forall(value => value != null && !value.isBlank)

  private def _valid_lease(lease: DurableJobLifetimeQuotaLease): Boolean = lease != null &&
    DurableJobLifetimeQuotaPolicy.validId(lease.jobid) && DurableJobLifetimeQuotaPolicy.hex(lease.roottoken, 32) &&
    DurableJobLifetimeQuotaPolicy.hex(lease.rootsha256, 64) && DurableJobLifetimeQuotaPolicy.hex(lease.ownersha256, 64)

  private def _root_proposal(record: DurableJobRecord, access: DurableJobRecordAccess, taskcount: Int, notecount: Long)
      : Consequence[(String, JobLifetimeBudget.Ticket)] = DurableJobLifetimeQuotaPolicy.pure {
    for {
      _ <- _validate_policy()
      _ <- if (taskcount >= 0 && taskcount <= 1024 && notecount >= 0L && notecount <= policy.lifetime.maxhistorycredits)
        Consequence.unit else JobResourcePolicy.refused
      _ <- _admit_request(record.body.identity.jobId, access)
      canonical <- DurableJobRecordCodec.canonicalJson(record).recoverWith(_ => DurableJobLifetimeQuotaPolicy.invalid)
      admitted <- DurableJobRecordCodec.decode(canonical, access).recoverWith(_ => DurableJobLifetimeQuotaPolicy.invalid)
      _ <- if (admitted == record && record.body.identity.revision == 1L &&
          record.body.lifecycle.status == DurableJobLifecycleStatus.Submitted &&
          record.body.retention.deletionState == DurableDeletionState.Active)
        Consequence.unit else DurableJobLifetimeQuotaPolicy.invalid
      ticket = JobLifetimeBudget.work(taskcount, notecount)
      _ <- if (policy.lifetime._fits(ticket.paid)) Consequence.unit else JobResourcePolicy.refused
    } yield canonical -> ticket
  }

  private def _hash(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.getBytes(StandardCharsets.UTF_8)).map(value => f"${value & 0xff}%02x").mkString
}
