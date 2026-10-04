package org.goldenport.cncf.job

import java.util.concurrent.PriorityBlockingQueue
import scala.collection.mutable
import org.goldenport.Consequence

/*
 * The monitor owns only counters, leases and queue coordination. In particular,
 * it never enters an engine's state monitor or invokes external callbacks.
 *
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class JobResourceBudget(val policy: JobResourcePolicy) {
  import JobResourceBudget.*

  private val _monitor = new Object
  private val _identities = mutable.HashSet.empty[JobId]
  private val _leases = mutable.HashSet.empty[Lease]
  private val _wait_leases = mutable.HashSet.empty[WaitLease]
  private var _provisional = 0
  private var _pending = 0
  private var _executing = 0

  def newOwner(): Owner = new Owner

  def reserveWait(owner: Owner): Consequence[WaitLease] = _monitor.synchronized {
    if (owner._closed || _wait_leases.size >= 64) JobResourcePolicy.refused
    else {
      val lease = new WaitLease(this, owner)
      _wait_leases += lease
      Consequence.success(lease)
    }
  }

  def markWaitAmbiguous(lease: WaitLease): Unit = _monitor.synchronized {
    _require_wait_issued(lease)
    if (!lease._released) lease._ambiguous = true
  }

  def acknowledgeWait(lease: WaitLease): Unit = _monitor.synchronized {
    _require_wait_issued(lease)
    if (!lease._released) lease._ambiguous = false
  }

  def releaseWait(lease: WaitLease): Unit = _monitor.synchronized {
    _require_wait_issued(lease)
    if (!lease._released && !lease._ambiguous) {
      lease._released = true
      _wait_leases -= lease
    }
  }

  def waitCount: Int = _monitor.synchronized { _wait_leases.size }

  private def _require_wait_issued(lease: WaitLease): Unit = {
    require(lease != null && (lease._budget eq this), "foreign wait lease")
    require(lease._released || _wait_leases.contains(lease), "unissued wait lease")
  }

  def snapshot: JobResourceSnapshot = _monitor.synchronized {
    JobResourceSnapshot(_identities.size + _provisional, _pending, _executing, policy)
  }

  def seed(ids: Iterator[JobId]): Consequence[Unit] = _monitor.synchronized {
    val additions = mutable.HashSet.empty[JobId]
    var overflow = false
    while (ids.hasNext && !overflow) {
      val id = ids.next()
      if (!_identities.contains(id) && !additions.contains(id)) {
        if (_identities.size + _provisional + additions.size >= policy.maxretainedjobs)
          overflow = true
        else additions += id
      }
    }
    if (overflow) JobResourcePolicy.refused
    else {
      _identities ++= additions
      Consequence.unit
    }
  }

  def reserve(
    owner: Owner,
    retained: Boolean = false,
    pending: Boolean = false,
    executing: Boolean = false,
    jobid: Option[JobId] = None,
    root: Boolean = false
  ): Consequence[Lease] = _monitor.synchronized {
    val provisional = retained && !jobid.exists(_identities.contains)
    if (
      owner._closed || (pending && executing) ||
        (provisional && _identities.size + _provisional >= policy.maxretainedjobs) ||
        (pending && _pending >= policy.maxpendingwork) ||
        (executing && _executing >= policy.maxexecutingwork) ||
        (root && jobid.exists(_has_pending_root))
    ) JobResourcePolicy.refused
    else {
      val lease = new Lease(this, owner, provisional, pending, executing, jobid, root)
      _leases += lease
      if (provisional) _provisional += 1
      if (pending) _pending += 1
      if (executing) _executing += 1
      Consequence.success(lease)
    }
  }

  def reserveBatch(owner: Owner, ids: Vector[JobId]): Consequence[Vector[Lease]] =
    _monitor.synchronized {
      val eligible = ids.distinct.filterNot(id => _leases.exists(lease =>
        lease._root && (lease._pending || lease._executing) && lease._job_id.contains(id)
      ))
      if (owner._closed || eligible.size > policy.maxpendingwork - _pending)
        JobResourcePolicy.refused
      else {
        val leases = eligible.map(id => new Lease(this, owner, false, true, false, Some(id), true))
        leases.foreach(_leases += _)
        _pending += leases.size
        Consequence.success(leases)
      }
    }

  def commit(lease: Lease, jobid: JobId): Unit = _monitor.synchronized {
    _require_issued(lease)
    if (!lease._released) {
      if (lease._provisional) {
        _provisional -= 1
        lease._provisional = false
        _identities += jobid
      }
      lease._job_id = Some(jobid)
    }
  }

  def consumeGeneration(lease: Lease): Boolean = _monitor.synchronized {
    _require_issued(lease)
    if (lease._released || !lease._pending || lease._consumed || lease._owner._closed)
      false
    else {
      lease._consumed = true
      true
    }
  }

  def enqueue(
    lease: Lease,
    work: InMemoryJobEngine.SchedulerWorkItem,
    queue: PriorityBlockingQueue[JobReservedWork]
  ): Boolean = _monitor.synchronized {
    _require_issued(lease)
    if (lease._released || !lease._pending || lease._queued || lease._owner._closed)
      false
    else {
      lease._queued = true
      lease._consumed = true
      val envelope = JobReservedWork(work, lease)
      lease._queue = Some(queue)
      lease._envelope = Some(envelope)
      queue.put(envelope)
      _monitor.notifyAll()
      true
    }
  }

  def claim(
    owner: Owner,
    queue: PriorityBlockingQueue[JobReservedWork],
    blocking: Boolean
  ): Option[JobReservedWork] = _monitor.synchronized {
    while (blocking && !owner._closed && (queue.isEmpty || _executing >= policy.maxexecutingwork))
      _monitor.wait()
    if (owner._closed || queue.isEmpty || _executing >= policy.maxexecutingwork)
      None
    else {
      val envelope = queue.poll()
      val lease = envelope.lease
      _require_issued(lease)
      require(lease._owner eq owner)
      require(lease._pending && !lease._released)
      lease._pending = false
      lease._queued = false
      lease._queue = None
      lease._envelope = None
      lease._executing = true
      _pending -= 1
      _executing += 1
      _monitor.notifyAll()
      Some(envelope)
    }
  }

  def release(lease: Lease): Unit = _monitor.synchronized {
    _require_issued(lease)
    _release(lease)
    _monitor.notifyAll()
  }

  def removePending(
    owner: Owner,
    jobid: Option[JobId],
    queue: PriorityBlockingQueue[JobReservedWork],
    close: Boolean = false
  ): Vector[JobReservedWork] = _monitor.synchronized {
    if (close) owner._closed = true
    val removed = Vector.newBuilder[JobReservedWork]
    val entries = queue.iterator()
    while (entries.hasNext) {
      val envelope = entries.next()
      if ((envelope.lease._owner eq owner) && jobid.forall(id => envelope.work.jobId == id)) {
        entries.remove()
        removed += envelope
      }
    }
    val leases = _leases.iterator.filter(lease =>
      (lease._owner eq owner) && lease._pending && jobid.forall(id => lease._job_id.contains(id))
    ).toVector
    leases.foreach(_release)
    _monitor.notifyAll()
    removed.result()
  }

  def isPending(lease: Lease): Boolean = _monitor.synchronized {
    _require_issued(lease)
    lease._pending && !lease._released && !lease._owner._closed
  }

  def isTimerPending(lease: Lease): Boolean = _monitor.synchronized {
    _require_issued(lease)
    lease._pending && !lease._consumed && !lease._released && !lease._owner._closed
  }

  def jobIdentity(lease: Lease): Option[JobId] = _monitor.synchronized {
    _require_issued(lease)
    lease._job_id
  }

  private def _has_pending_root(jobid: JobId): Boolean =
    _leases.exists(lease => lease._root && lease._pending && lease._job_id.contains(jobid))

  private def _require_issued(lease: Lease): Unit =
    require(lease._budget eq this, "foreign resource lease")

  private def _release(lease: Lease): Unit =
    if (!lease._released) {
      lease._queue.foreach(queue => lease._envelope.foreach(queue.remove))
      lease._queue = None
      lease._envelope = None
      if (lease._provisional) _provisional -= 1
      if (lease._pending) _pending -= 1
      if (lease._executing) _executing -= 1
      lease._provisional = false
      lease._pending = false
      lease._executing = false
      lease._released = true
      _leases -= lease
    }
}

private[job] object JobResourceBudget {
  final class WaitLease private[JobResourceBudget] (
    private[JobResourceBudget] val _budget: JobResourceBudget,
    private[JobResourceBudget] val _owner: Owner
  ) {
    private[JobResourceBudget] var _ambiguous = false
    private[JobResourceBudget] var _released = false
  }

  final class Owner private[JobResourceBudget] () {
    private[JobResourceBudget] var _closed = false
  }

  final class Lease private[JobResourceBudget] (
    private[JobResourceBudget] val _budget: JobResourceBudget,
    private[JobResourceBudget] val _owner: Owner,
    private[JobResourceBudget] var _provisional: Boolean,
    private[JobResourceBudget] var _pending: Boolean,
    private[JobResourceBudget] var _executing: Boolean,
    private[JobResourceBudget] var _job_id: Option[JobId],
    private[JobResourceBudget] val _root: Boolean
  ) {
    private[JobResourceBudget] var _consumed = false
    private[JobResourceBudget] var _queued = false
    private[JobResourceBudget] var _released = false
    private[JobResourceBudget] var _queue = Option.empty[PriorityBlockingQueue[JobReservedWork]]
    private[JobResourceBudget] var _envelope = Option.empty[JobReservedWork]
  }
}

private[job] final case class JobReservedWork(
  work: InMemoryJobEngine.SchedulerWorkItem,
  lease: JobResourceBudget.Lease
)
