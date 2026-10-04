package org.goldenport.cncf.job

import scala.collection.mutable
import org.goldenport.Consequence

/*
 * All access is owned by the existing State monitor. These pure counter operations
 * acquire no second monitor and invoke no clock, task, provider or engine.
 * Published reservations are never refunded. Unpublished roots are values.
 *
 * @since   Oct. 1, 2026
 * @version Oct. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class JobLifetimeBudget(val policy: JobLifetimePolicy) {
  import JobLifetimeBudget.*

  private val _ledgers = mutable.HashMap.empty[JobId, Ledger]

  def snapshot(jobId: JobId): Option[JobLifetimeSnapshot] = _ledgers.get(jobId).map(_.paid)

  def prepare(jobId: JobId, ticket: Ticket): Consequence[Ticket] =
    _ledgers.get(jobId) match {
      case Some(ledger) if _can_add(ledger.paid, ticket.paid) => Consequence.success(ticket)
      case _ => JobResourcePolicy.refused
    }

  def prepareRoot(taskCount: Int, noteCount: Long = 0L): Consequence[Ticket] = {
    val ticket = work(taskCount, noteCount)
    if (taskCount < 0 || noteCount < 0L || !policy._fits(ticket.paid)) JobResourcePolicy.refused
    else Consequence.success(ticket)
  }

  def publishRoot(jobId: JobId, ticket: Ticket): Unit = {
    require(!_ledgers.contains(jobId), "duplicate lifetime root")
    _ledgers.put(jobId, new Ledger(ticket.paid, ticket.normals, Set.empty, Set.empty))
    ()
  }

  def commit(jobId: JobId, ticket: Ticket): Unit = {
    val ledger = _ledgers(jobId)
    ledger.paid = _add(ledger.paid, ticket.paid)
    ledger.remaining += ticket.normals
  }

  def associate(jobId: JobId, taskId: TaskId): Unit = {
    val ledger = _ledgers(jobId)
    if (!ledger.normalids.contains(taskId.value)) {
      require(ledger.remaining > 0L, "unpaid normal dispatch")
      ledger.remaining -= 1L
      ledger.normalids += taskId.value
    }
  }

  def claimCompensation(jobId: JobId, taskId: TaskId): Boolean =
    _ledgers.get(jobId) match {
      case Some(ledger) if ledger.normalids.contains(taskId.value) && !ledger.claims.contains(taskId.value) =>
        ledger.claims += taskId.value
        true
      case _ => false
    }

  def cancel(jobId: JobId): Consequence[Unit] = _ledgers.get(jobId) match {
    case Some(ledger) if ledger.paid.cancelcredits > 0L =>
      ledger.paid = ledger.paid.copy(cancelcredits = ledger.paid.cancelcredits - 1L)
      Consequence.unit
    case _ => JobResourcePolicy.refused
  }

  def acceptsSeed(jobId: JobId, seed: Seed): Boolean =
    if (!policy._fits(seed.paid)) false
    else _ledgers.get(jobId).forall { ledger =>
      seed.paid.chargedtasks <= ledger.paid.chargedtasks &&
        seed.paid.historycredits <= ledger.paid.historycredits &&
        seed.paid.admissions <= ledger.paid.admissions &&
        seed.normalids.subsetOf(ledger.normalids) && seed.claims.subsetOf(ledger.claims)
    }

  def commitSeeds(seeds: Map[JobId, Seed]): Unit = seeds.foreach { case (jobid, seed) =>
    if (!_ledgers.contains(jobid))
      _ledgers.put(jobid, new Ledger(seed.paid, seed.remaining, seed.normalids, seed.claims))
  }

  def replaceNativeSeed(jobId: JobId, seed: Seed): Unit = {
    _ledgers.put(jobId, new Ledger(seed.paid, seed.remaining, seed.normalids, seed.claims))
    ()
  }

  private def _can_add(current: JobLifetimeSnapshot, delta: JobLifetimeSnapshot): Boolean =
    policy._fits(delta) && delta.chargedtasks <= policy.maxchargedtasks - current.chargedtasks &&
      delta.historycredits <= policy.maxhistorycredits - current.historycredits &&
      delta.admissions <= policy.maxadmissions - current.admissions

  private def _add(current: JobLifetimeSnapshot, delta: JobLifetimeSnapshot): JobLifetimeSnapshot =
    JobLifetimeSnapshot(current.chargedtasks + delta.chargedtasks,
      current.historycredits + delta.historycredits, current.admissions + delta.admissions,
      current.cancelcredits + delta.cancelcredits)
}

private[job] object JobLifetimeBudget {
  final case class Ticket(paid: JobLifetimeSnapshot, normals: Long)
  final case class Seed(paid: JobLifetimeSnapshot, remaining: Long,
    normalids: Set[String], claims: Set[String]) {
    def maximum(other: Seed): Seed = Seed(
      JobLifetimeSnapshot(math.max(paid.chargedtasks, other.paid.chargedtasks),
        math.max(paid.historycredits, other.paid.historycredits),
        math.max(paid.admissions, other.paid.admissions),
        math.max(paid.cancelcredits, other.paid.cancelcredits)),
      math.max(remaining, other.remaining), normalids ++ other.normalids, claims ++ other.claims)
  }

  private final class Ledger(var paid: JobLifetimeSnapshot, var remaining: Long,
    var normalids: Set[String], var claims: Set[String])

  def work(taskCount: Int, noteCount: Long = 0L): Ticket =
    Ticket(JobLifetimeSnapshot(2L * taskCount.toLong,
      32L + 64L * taskCount.toLong + noteCount, 1L, 1L), taskCount.toLong)

  val control: Ticket = Ticket(JobLifetimeSnapshot(0L, 32L, 1L, 0L), 0L)
  def annotation(noteCount: Long): Ticket =
    Ticket(JobLifetimeSnapshot(0L, 32L + noteCount, 1L, 0L), 0L)
}
