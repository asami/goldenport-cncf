package org.goldenport.cncf.job

import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.*
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Bounded native sweeps and the explicitly configured production timer owner.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobMaintenanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import BlobRepository.given

  "Configured durable maintenance" should {
    "M1 expire, delete and dispose at the inclusive TTL with contiguous canonical V1/V2 checkpoints" in {
      Vector(false, true).foreach { v2 =>
        Given("a real signed TTL Job and owned local bytes with native sidecar and EntityStore ownership")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val active = _create(fixture, _record("ttl", v2 = v2))
        val (attached, metadata) = _attach(fixture, active)
        val source = new Source(Vector(_candidate(0, "ttl")))

        When("the coordinator runs immediately before and exactly at the TTL")
        val before = _success(fixture.maintenance.sweep(source, 1, _deadline.minusNanos(1)))
        val stillactive = _load(fixture, "ttl")
        val report = _success(fixture.maintenance.sweep(source, 1, _deadline))
        val current = _load(fixture, "ttl")

        Then("the inclusive run reports two closed retention stages plus cleanup and physically disposes native bytes")
        before.entries shouldBe Vector(DurableJobMaintenanceEntry(0L, Vector(DurableJobRetentionFacet.NotDue), None))
        stillactive shouldBe attached
        report.entries shouldBe Vector(DurableJobMaintenanceEntry(0L, Vector(DurableJobRetentionFacet.Expired, DurableJobRetentionFacet.Deleted), Some(DurableJobCleanupFacet.Tombstoned)))
        current.record.body.identity shouldBe attached.record.body.identity.copy(revision = attached.record.body.identity.revision + 3L, updatedAt = _deadline)
        current.providerrevision.value shouldBe attached.providerrevision.value + 3L
        current.record.format shouldBe attached.record.format
        current.record.body.retention.tombstone shouldBe Some(DurableTombstone(_deadline, "job-owned-payload-cleanup-complete", None))
        current.record.body.copy(identity = attached.record.body.identity, retention = attached.record.body.retention) shouldBe attached.record.body
        DurableJobRecordCodec.decode(_success(DurableJobRecordCodec.canonicalJson(current.record)), _access) shouldBe Consequence.Success(current.record)
        Files.exists(_path(fixture.root, metadata)) shouldBe false
        Files.exists(_sidecar(fixture.root, metadata)) shouldBe false
        _success(fixture.entity.load[Blob](metadata.id)).get shouldBe metadata
      }
    }

    "M1 resume Expired and partially cleaned Deleted values through fresh owners while protecting Keep, hold and active executions" in {
      Given("real signed Expired/Deleted candidates and Active Keep, hold, nonterminal and future-floor candidates")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val expiredactive = _create(fixture, _record("expired", v2 = true))
      val (expiredattached, expiredblob) = _attach(fixture, expiredactive)
      val retention = new DurableJobRetentionMaintenance(fixture.store)
      retention.maintainObserved(expiredattached, _access, DurableJobRetentionCommand.Expire, _deadline) shouldBe DurableJobRetentionFacet.Expired
      val deletedactive = _create(fixture, _record("deleted", v2 = true))
      val (deletedattached, deletedblob) = _attach(fixture, deletedactive)
      retention.maintainObserved(deletedattached, _access, DurableJobRetentionCommand.Delete, _deadline) shouldBe DurableJobRetentionFacet.Deleted
      _success(LocalBlobStore(fixture.root).delete(deletedblob.storageRef.get))
      val protectedrecords = Vector(
        _record("keep", retention = _ttl.copy(expiresAt = None)),
        _record("hold", retention = _ttl.copy(retainUntil = Some(Instant.MAX))),
        _record("running", status = DurableJobLifecycleStatus.Running, v2 = true),
        _record("floor", retention = _ttl.copy(retainUntil = Some(_deadline.plusSeconds(1))))
      ).map(_create(fixture, _))
      val context = ExecutionContext.withJobContext(fixture.context, JobContext.empty)
      val entity = EntityStore.standard()
      val store = new DurableJobStore(entity)
      val blob = new MeasuredBlobStore(LocalBlobStore(fixture.root))
      val fresh = new DurableJobMaintenance(store, new DurableJobRetentionMaintenance(store),
        new DurableJobPayloadCleanup(store, new DurableJobOwnedBlobPayloads(store, entity, blob)))
      val source = new Source(Vector("expired", "deleted", "keep", "hold", "running", "floor").zipWithIndex.map { case (id, ordinal) => _candidate(ordinal, id) })

      When("fresh coordinator/provider/context resumes each explicit signed state")
      val report = _success(fresh.sweep(source, 6, _deadline)(using context))
      val states = Vector("expired", "deleted").map(id => _success(store.load(id, _access)(using context)).get)

      Then("resumed payload disposal succeeds while Active protected candidates retain their exact records")
      report.entries.take(2) shouldBe Vector(
        DurableJobMaintenanceEntry(0L, Vector(DurableJobRetentionFacet.Deleted), Some(DurableJobCleanupFacet.Tombstoned)),
        DurableJobMaintenanceEntry(1L, Vector.empty, Some(DurableJobCleanupFacet.Tombstoned)))
      report.entries.drop(2).map(_.retention) shouldBe Vector(Vector(DurableJobRetentionFacet.Kept), Vector(DurableJobRetentionFacet.Held),
        Vector(DurableJobRetentionFacet.NotTerminal), Vector(DurableJobRetentionFacet.NotDue))
      report.entries.drop(2).map(_.cleanup) shouldBe Vector.fill(4)(None)
      protectedrecords.foreach(previous => _success(store.load(previous.record.body.identity.jobId, _access)(using context)).get shouldBe previous)
      states.map(_.record.body.retention.deletionState) shouldBe Vector.fill(2)(DurableDeletionState.Tombstoned)
      Files.exists(_path(fixture.root, expiredblob)) shouldBe false
      Files.exists(_path(fixture.root, deletedblob)) shouldBe false
      blob.deleted shouldBe Vector(expiredblob.storageRef.get, deletedblob.storageRef.get)
    }

    "M1 stop progression when a fresh stage load observes concurrent Active Keep authority" in {
      Given("a real due payload whose successful expiry races with a newer signed Active Keep checkpoint")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("stage-race", v2 = true))
      val (_, metadata) = _attach(fixture, active)
      var raced: Option[DurableJobStoreSnapshot] = None
      fixture.entity.armAfterUpdate(() => {
        val current = _load(fixture, "stage-race")
        val body = current.record.body.copy(identity = current.record.body.identity.copy(revision = current.record.body.identity.revision + 1L,
          updatedAt = _deadline), retention = _ttl.copy(expiresAt = None))
        raced = Some(_success(fixture.store.checkpoint(current, _success(DurableJobRecord.createV2(body)), _access)))
      })
      When("the coordinator reloads authority between expiry and logical deletion")
      val report = _success(fixture.maintenance.sweep(new Source(Vector(_candidate(0, "stage-race"))), 1, _deadline))
      val current = _load(fixture, "stage-race")
      Then("the changed stage stops with a closed stale facet and preserves the newer Keep record and native payload")
      report.entries shouldBe Vector(DurableJobMaintenanceEntry(0L, Vector(DurableJobRetentionFacet.Expired, DurableJobRetentionFacet.Stale), None))
      current shouldBe raced.get
      fixture.blob.deleted shouldBe Vector.empty
      Files.exists(_path(fixture.root, metadata)) shouldBe true
      Files.exists(_sidecar(fixture.root, metadata)) shouldBe true
    }

    "M2 reject every invalid whole batch, bound, source and time before Job or Blob effects" in {
      Given("a real TTL target whose first candidate would otherwise be due")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("due"))
      val (attached, metadata) = _attach(fixture, active)
      val batches = Vector(
        Vector(_candidate(0, "due"), _candidate(1, "oversupply")),
        Vector(_candidate(0, "due"), _candidate(1, "due")),
        Vector(_candidate(0, "due"), _candidate(2, "gap")),
        Vector(_candidate(1, "due")),
        Vector(_candidate(0, "due"), _candidate(1, " ")),
        Vector(_candidate(0, "due"), _candidate(1, null)),
        Vector(_candidate(0, "due"), null), null)
      val unused = new Source(Vector(_candidate(0, "due")))
      val failed = new Source(Vector.empty, mode = "failure")
      val thrown = new Source(Vector.empty, mode = "throw")
      fixture.entity.reset()

      When("all malformed vectors, out-of-range requests and invalid observations are submitted")
      val malformed = batches.zipWithIndex.map { case (entries, index) => fixture.maintenance.sweep(new Source(entries), if (index == 0) 1 else 2, _deadline) }
      val bounds = Vector(0, -1, 1025).map(bound => fixture.maintenance.sweep(unused, bound, _deadline))
      val sources = Vector(failed, thrown, null).map(source => fixture.maintenance.sweep(source, 1, _deadline))
      val times = Vector(null, Instant.MAX).map(now => fixture.maintenance.sweep(unused, 1, now))
      val loads = fixture.entity.loads
      val writes = fixture.entity.writes

      Then("constant bounded failures perform no Job/metadata lookup, write or payload delete and preserve the valid first target")
      val failures = malformed ++ bounds ++ sources ++ times
      failures.foreach(_ shouldBe a[Consequence.Failure[_]])
      failures.map(_message).distinct.size shouldBe 1
      failures.foreach { result => _message(result).length should be < 1024; _message(result) should not include "private-source" }
      unused.calls shouldBe 0
      loads shouldBe 0
      writes shouldBe 0
      fixture.blob.deleted shouldBe Vector.empty
      Files.exists(_path(fixture.root, metadata)) shouldBe true
      _load(fixture, "due") shouldBe attached
    }

    "M2 admit empty and exactly 1024 batches and retain earlier success when later candidates are hostile, missing or failing" in {
      Given("a real signed due candidate, hostile access and an injected Job provider failure")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("success"))
      val (_, metadata) = _attach(fixture, active)
      _create(fixture, _record("hostile"))
      _create(fixture, _record("provider-failure"))
      fixture.entity.failJobId = Some(_success(DurableJobStoreEntity.entityId("provider-failure")))
      val source = new Source(Vector(_candidate(0, "success"), _candidate(1, "hostile", _access.copy(subjectId = "other")),
        _candidate(2, "missing"), _candidate(3, "provider-failure")))
      When("the coordinator completes an empty batch, the inclusive maximum and the mixed-authority sequence")
      val empty = _success(fixture.maintenance.sweep(new Source(Vector.empty), 1, _deadline))
      val bound = _success(fixture.maintenance.sweep(new Source(Vector.tabulate(1024)(index => _candidate(index, s"missing-$index"))), 1024, _deadline))
      val mixed = _success(fixture.maintenance.sweep(source, 4, _deadline))
      Then("ordered reports carry ordinals and closed facets only, while a later refusal cannot reverse disposal")
      empty.entries shouldBe Vector.empty
      bound.entries.size shouldBe 1024
      bound.entries.map(_.ordinal) shouldBe (0L until 1024L).toVector
      bound.entries.map(_.retention).distinct shouldBe Vector(Vector(DurableJobRetentionFacet.Missing))
      mixed.entries.head shouldBe DurableJobMaintenanceEntry(0L, Vector(DurableJobRetentionFacet.Expired, DurableJobRetentionFacet.Deleted), Some(DurableJobCleanupFacet.Tombstoned))
      mixed.entries.tail.map(_.retention) shouldBe Vector(Vector(DurableJobRetentionFacet.Refused), Vector(DurableJobRetentionFacet.Missing), Vector(DurableJobRetentionFacet.Corrupt))
      mixed.entries.tail.map(_.cleanup) shouldBe Vector.fill(3)(None)
      _load(fixture, "success").record.body.retention.deletionState shouldBe DurableDeletionState.Tombstoned
      Files.exists(_path(fixture.root, metadata)) shouldBe false
    }

    "M3 use the production scheduler with manual clock/timer and fixed delay from callback completion" in {
      Given("a real TTL candidate, explicit source and the existing manual timer/clock")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("scheduled"))
      val (_, metadata) = _attach(fixture, active)
      val clock = new ManualJobTimeSource(_deadline.minusSeconds(1))
      val timer = new InMemoryJobEngine.ManualJobTimer(clock)
      val source = new Source(Vector(_candidate(0, "scheduled")), action = () => { clock.advanceBy(Duration.ofSeconds(5)); () })
      val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1), clock, timer)
      val constructorstate = owner.status
      val constructorpending = timer.pendingCount

      When("start is repeated, the future callback runs at TTL and completion advances the injected clock")
      val first = owner.start()
      val again = owner.start()
      val firstdue = owner.status.nextDueAt
      val pending = timer.pendingCount
      val early = timer.fireDue()
      val fired = timer.advanceTo(_deadline)
      val after = owner.status
      val pendingafter = timer.pendingCount
      owner.close()

      Then("construction has no effect, one registration disposes real bytes, and next due is completion time plus period")
      constructorstate shouldBe DurableJobMaintenanceSchedulerStatus(false, false, false, None, None)
      constructorpending shouldBe 0
      Vector(first, again) shouldBe Vector(Consequence.unit, Consequence.unit)
      firstdue shouldBe Some(_deadline)
      pending shouldBe 1
      early shouldBe 0
      fired shouldBe 1
      source.calls shouldBe 1
      after.started shouldBe true
      after.inFlight shouldBe false
      after.nextDueAt shouldBe Some(_deadline.plusSeconds(6))
      _success(after.lastResult.get).entries.head.cleanup shouldBe Some(DurableJobCleanupFacet.Tombstoned)
      pendingafter shouldBe 1
      Files.exists(_path(fixture.root, metadata)) shouldBe false
      timer.pendingCount shouldBe 0
    }

    "M3 accept inclusive one-second/day periods and rearm source failures without catch-up or spin" in {
      Vector(Duration.ofSeconds(1), Duration.ofHours(24)).foreach { period =>
        Given("an explicitly configured production owner with a failing source and injected manual time")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val clock = new ManualJobTimeSource(_instant)
        val timer = new InMemoryJobEngine.ManualJobTimer(clock)
        val source = new Source(Vector.empty, mode = "failure")
        val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, period, clock, timer)
        When("the owner starts and the clock jumps well beyond its first due time")
        val start = owner.start()
        val firstdue = owner.status.nextDueAt
        val fired = timer.advanceTo(_instant.plus(period.multipliedBy(10)))
        val status = owner.status
        val pending = timer.pendingCount
        owner.close()
        Then("one callback returns a bounded failure and schedules one future fixed-delay task from the current completion time")
        start shouldBe Consequence.unit
        firstdue shouldBe Some(_instant.plus(period))
        fired shouldBe 1
        source.calls shouldBe 1
        status.lastResult.get shouldBe a[Consequence.Failure[_]]
        _message(status.lastResult.get) should not include "private-source"
        status.nextDueAt shouldBe Some(_instant.plus(period.multipliedBy(11)))
        pending shouldBe 1
      }
    }

    "M3 finalize an interrupted admitted run with preserved interruption and one completion-clock rearm" in {
      Given("a production scheduler, native coordinator, manual clock and retained timer with an interruptible first source run")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val clock = new ManualJobTimeSource(_deadline)
      val timer = new RetainedTimer
      val admitted = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val sourcefailure = new AtomicReference[InterruptedException]()
      val threadfailure = new AtomicReference[Throwable]()
      val interrupted = new AtomicBoolean(false)
      val runs = new AtomicInteger(0)
      val source = new Source(Vector.empty, action = () => {
        if (runs.incrementAndGet() == 1) {
          admitted.countDown()
          try {
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("private-source-latch-timeout")
          } catch {
            case exception: InterruptedException => sourcefailure.set(exception); throw exception
          }
        }
      })
      val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1), clock, timer)
      val thread = new Thread(() => {
        try timer.fire(0) catch {
          case exception: InterruptedException => threadfailure.set(exception)
          case NonFatal(exception) => threadfailure.set(exception)
        } finally interrupted.set(Thread.currentThread().isInterrupted)
      })

      try {
        When("start admits the due callback on the owned thread and the source enters its interruptible wait")
        _success(owner.start())
        clock.advanceBy(Duration.ofSeconds(1))
        thread.start()
        val entered = admitted.await(5, TimeUnit.SECONDS)
        val during = owner.status
        Then("one admitted run is visible without a pending registration or any interruption on the caller thread")
        entered shouldBe true
        during.inFlight shouldBe true
        during.nextDueAt shouldBe None
        timer.registrations shouldBe 1
        Thread.currentThread().isInterrupted shouldBe false

        When("completion time advances and the blocked callback is actually interrupted and boundedly joined")
        clock.advanceBy(Duration.ofSeconds(5))
        thread.interrupt()
        thread.join(5000)
        val completed = owner.status
        Then("the same interruption escapes with its flag preserved while bounded failure bookkeeping rearms exactly once")
        thread.isAlive shouldBe false
        sourcefailure.get() shouldBe a[InterruptedException]
        threadfailure.get() should be theSameInstanceAs sourcefailure.get()
        interrupted.get() shouldBe true
        Thread.currentThread().isInterrupted shouldBe false
        completed.started shouldBe true
        completed.closed shouldBe false
        completed.inFlight shouldBe false
        completed.lastResult.get shouldBe a[Consequence.Failure[_]]
        _message(completed.lastResult.get).length should be < 1024
        _message(completed.lastResult.get) should not include "private-source"
        completed.nextDueAt shouldBe Some(_deadline.plusSeconds(7))
        timer.registrations shouldBe 2
        source.calls shouldBe 1

        When("the consumed generation is retained and triggered again")
        timer.fire(0)
        Then("the stale callback has no source or lifecycle effect")
        owner.status shouldBe completed
        source.calls shouldBe 1
        timer.registrations shouldBe 2

        When("the fresh generation becomes due and executes on the clear caller thread")
        clock.advanceBy(Duration.ofSeconds(1))
        timer.fire(1)
        val fresh = owner.status
        owner.close()
        Then("the next real run succeeds and schedules once while close owns only its registration and preserves the shared timer")
        source.calls shouldBe 2
        fresh.inFlight shouldBe false
        _success(fresh.lastResult.get).entries shouldBe Vector.empty
        fresh.nextDueAt shouldBe Some(_deadline.plusSeconds(8))
        timer.registrations shouldBe 3
        timer.cancelled shouldBe Vector(2)
        timer.shutdownCount shouldBe 0
        Thread.currentThread().isInterrupted shouldBe false
      } finally {
        release.countDown()
        owner.close()
        if (thread.isAlive) thread.interrupt()
        thread.join(5000)
      }
    }

    "M4 finalize interruption after close overlaps admitted work without rearm or native Job and payload effects" in {
      Given("a real signed due Job with owned bytes and a production scheduler blocked in an interruptible source")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("close-interrupted", v2 = true))
      val (attached, metadata) = _attach(fixture, active)
      val payloadbytes = Files.readAllBytes(_path(fixture.root, metadata)).toVector
      val sidecarbytes = Files.readAllBytes(_sidecar(fixture.root, metadata)).toVector
      fixture.entity.reset()
      val clock = new ManualJobTimeSource(_deadline)
      val timer = new RetainedTimer
      val admitted = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val sourcefailure = new AtomicReference[InterruptedException]()
      val threadfailure = new AtomicReference[Throwable]()
      val interrupted = new AtomicBoolean(false)
      val source = new Source(Vector(_candidate(0, "close-interrupted")), action = () => {
        admitted.countDown()
        try {
          if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("private-source-latch-timeout")
        } catch {
          case exception: InterruptedException => sourcefailure.set(exception); throw exception
        }
      })
      val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1), clock, timer)
      val thread = new Thread(() => {
        try timer.fire(0) catch {
          case exception: InterruptedException => threadfailure.set(exception)
          case NonFatal(exception) => threadfailure.set(exception)
        } finally interrupted.set(Thread.currentThread().isInterrupted)
      })

      try {
        When("the due callback is admitted and close overlaps its blocked source")
        _success(owner.start())
        clock.advanceBy(Duration.ofSeconds(1))
        thread.start()
        val entered = admitted.await(5, TimeUnit.SECONDS)
        owner.close()
        val closing = owner.status
        Then("the closed owner truthfully retains in-flight work until the callback physically completes")
        entered shouldBe true
        closing.closed shouldBe true
        closing.started shouldBe false
        closing.inFlight shouldBe true
        closing.nextDueAt shouldBe None
        closing.lastResult shouldBe None
        Thread.currentThread().isInterrupted shouldBe false

        When("the blocked callback is actually interrupted, boundedly joined and its retained generation is exercised")
        thread.interrupt()
        thread.join(5000)
        val completed = owner.status
        timer.fire(0)
        Then("interruption propagates with its flag while the completed closed owner retains a bounded failure without extra effects")
        thread.isAlive shouldBe false
        sourcefailure.get() shouldBe a[InterruptedException]
        threadfailure.get() should be theSameInstanceAs sourcefailure.get()
        interrupted.get() shouldBe true
        Thread.currentThread().isInterrupted shouldBe false
        completed.closed shouldBe true
        completed.started shouldBe false
        completed.inFlight shouldBe false
        completed.nextDueAt shouldBe None
        completed.lastResult.get shouldBe a[Consequence.Failure[_]]
        _message(completed.lastResult.get).length should be < 1024
        _message(completed.lastResult.get) should not include "private-source"
        owner.status shouldBe completed
        source.calls shouldBe 1
        timer.registrations shouldBe 1
        timer.cancelled shouldBe Vector.empty
        timer.shutdownCount shouldBe 0
        fixture.entity.loads shouldBe 0
        fixture.entity.writes shouldBe 0
        fixture.blob.deleted shouldBe Vector.empty
        _load(fixture, "close-interrupted") shouldBe attached
        _success(fixture.entity.load[Blob](metadata.id)).get shouldBe metadata
        Files.readAllBytes(_path(fixture.root, metadata)).toVector shouldBe payloadbytes
        Files.readAllBytes(_sidecar(fixture.root, metadata)).toVector shouldBe sidecarbytes
      } finally {
        release.countDown()
        owner.close()
        if (thread.isAlive) thread.interrupt()
        thread.join(5000)
      }
    }

    "M4 cancel only the owned registration before due and reject retained callbacks after close" in {
      Given("one unrelated manual timer registration and one explicitly configured maintenance owner")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val clock = new ManualJobTimeSource(_instant)
      val timer = new InMemoryJobEngine.ManualJobTimer(clock)
      val unrelated = new AtomicInteger(0)
      timer.schedule(_instant.plusSeconds(1)) { unrelated.incrementAndGet(); () }
      val source = new Source(Vector.empty)
      val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1), clock, timer)
      _success(owner.start())
      When("the owner closes before its due time and the shared timer advances")
      owner.close()
      owner.close()
      val pending = timer.pendingCount
      val fired = timer.advanceBy(Duration.ofSeconds(1))
      val restart = owner.start()
      Then("only the unrelated task remains, the source is untouched and a closed owner cannot restart")
      pending shouldBe 1
      fired shouldBe 1
      unrelated.get() shouldBe 1
      source.calls shouldBe 0
      restart shouldBe a[Consequence.Failure[_]]
      owner.status shouldBe DurableJobMaintenanceSchedulerStatus(false, true, false, None, None)
    }

    "M4 consume each generation once and prevent duplicate, stale and concurrent triggers from overlapping" in {
      Given("a real coordinator behind a latch-controlled source and a timer retaining every callback")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val clock = new ManualJobTimeSource(_deadline)
      val timer = new RetainedTimer
      val admitted = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val source = new Source(Vector.empty, action = () => {
        admitted.countDown()
        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout")
      })
      val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1), clock, timer)
      _success(owner.start())
      clock.advanceBy(Duration.ofSeconds(1))
      val threadfailure = new AtomicReference[Throwable]()
      val thread = new Thread(() => try timer.fire(0) catch { case failure: Throwable => threadfailure.set(failure) })

      When("one callback is admitted, concurrent/repeated callbacks trigger, then completion rearms a fresh generation")
      thread.start()
      val entered = admitted.await(5, TimeUnit.SECONDS)
      val during = owner.status
      timer.fire(0)
      timer.fire(0)
      release.countDown()
      thread.join(5000)
      val after = owner.status
      timer.fire(0)
      val calls = source.calls
      owner.close()
      timer.fire(1)

      Then("only one source run occurs, status observes one in-flight run and every stale/closed callback has zero effects")
      entered shouldBe true
      thread.isAlive shouldBe false
      threadfailure.get() shouldBe null
      during.inFlight shouldBe true
      during.nextDueAt shouldBe None
      after.inFlight shouldBe false
      after.nextDueAt shouldBe Some(clock.now().plusSeconds(1))
      timer.registrations shouldBe 2
      calls shouldBe 1
      source.calls shouldBe 1
      timer.shutdownCount shouldBe 0
      timer.cancelled shouldBe Vector(1)
    }

    "M4 permit an admitted native disposal to finish after close while preventing rearm and extra effects" in {
      Given("a signed due Job with real local bytes and a latch held immediately before source completion")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("close-running", v2 = true))
      val (_, metadata) = _attach(fixture, active)
      val clock = new ManualJobTimeSource(_deadline)
      val timer = new RetainedTimer
      val admitted = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val source = new Source(Vector(_candidate(0, "close-running")), action = () => {
        admitted.countDown()
        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout")
      })
      val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1), clock, timer)
      _success(owner.start())
      clock.advanceBy(Duration.ofSeconds(1))
      val threadfailure = new AtomicReference[Throwable]()
      val thread = new Thread(() => try timer.fire(0) catch { case failure: Throwable => threadfailure.set(failure) })

      When("close occurs during admitted work, the retained callback repeats and the in-flight source is released")
      thread.start()
      val entered = admitted.await(5, TimeUnit.SECONDS)
      owner.close()
      val closing = owner.status
      timer.fire(0)
      release.countDown()
      thread.join(5000)
      timer.fire(0)
      val completed = owner.status

      Then("the admitted run persists its tombstone and bounded result, while close prevents every later callback and registration")
      entered shouldBe true
      thread.isAlive shouldBe false
      threadfailure.get() shouldBe null
      closing.closed shouldBe true
      closing.inFlight shouldBe true
      completed.closed shouldBe true
      completed.started shouldBe false
      completed.inFlight shouldBe false
      completed.nextDueAt shouldBe None
      _success(completed.lastResult.get).entries.head.cleanup shouldBe Some(DurableJobCleanupFacet.Tombstoned)
      source.calls shouldBe 1
      timer.registrations shouldBe 1
      timer.shutdownCount shouldBe 0
      Files.exists(_path(fixture.root, metadata)) shouldBe false
      _load(fixture, "close-running").record.body.retention.deletionState shouldBe DurableDeletionState.Tombstoned
    }

    "M5 report invalid configuration, null/overflow clocks and failed registration with bounded stopped status" in {
      Given("explicit production owners with invalid bounds, periods, time sources and registration providers")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val periods = Vector(null, Duration.ZERO, Duration.ofMillis(999), Duration.ofHours(24).plusNanos(1))
      val periodcases = periods.map(period => (1, period, new ManualJobTimeSource(_instant): JobTimeSource))
      val boundcases = Vector(0, -1, 1025).map(bound => (bound, Duration.ofSeconds(1), new ManualJobTimeSource(_instant): JobTimeSource))
      val clockcases = Vector(null, Instant.MAX, Instant.MAX.minusMillis(500)).map(now => (1, Duration.ofSeconds(1), new ManualJobTimeSource(now): JobTimeSource))
      val throwingclock = new JobTimeSource { def now(): Instant = throw new IllegalStateException("private-clock") }
      val cases = periodcases ++ boundcases ++ clockcases :+ ((1, Duration.ofSeconds(1), throwingclock))
      When("each invalid owner attempts its first registration")
      val results = cases.map { case (bound, period, clock) =>
        val timer = new RetainedTimer
        val source = new Source(Vector.empty)
        val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, bound, period, clock, timer)
        (owner.start(), owner.status, timer.registrations, source.calls)
      }
      Then("no source/provider effect or extra registration occurs and each failure is visible in bounded lifecycle status")
      results.foreach { case (result, status, registrations, calls) =>
        result shouldBe a[Consequence.Failure[_]]
        _message(result).length should be < 1024
        _message(result) should not include "private-clock"
        status.started shouldBe false
        status.inFlight shouldBe false
        status.nextDueAt shouldBe None
        status.lastResult.get shouldBe a[Consequence.Failure[_]]
        registrations shouldBe 0
        calls shouldBe 0
      }

      Vector("throw", "null").foreach { mode =>
        Given("a timer whose nonblocking registration throws or returns no registration")
        val timer = new RetainedTimer(mode = mode)
        val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, new Source(Vector.empty), 1, Duration.ofSeconds(1), new ManualJobTimeSource(_instant), timer)
        When("start requests one future task")
        val result = owner.start()
        Then("a fixed bounded failure visibly stops scheduling")
        result shouldBe a[Consequence.Failure[_]]
        _message(result) should not include "private-timer"
        owner.status.started shouldBe false
        owner.status.nextDueAt shouldBe None
        timer.registrations shouldBe 0
      }
    }

    "M5 normalize callback exceptions and stop visibly if fixed-delay re-registration fails" in {
      Given("a throwing source, valid first timer registration and a deterministic failure on rearm")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val source = new Source(Vector.empty, mode = "throw")
      val clock = new ManualJobTimeSource(_instant)
      val timer = new RetainedTimer
      val owner = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1), clock, timer)
      _success(owner.start())
      timer.failRearm = true
      clock.advanceBy(Duration.ofSeconds(1))
      When("the callback refuses and its next registration also refuses")
      timer.fire(0)
      val result = owner.status
      timer.fire(0)
      Then("one bounded failure is retained, scheduling is visibly stopped and a stale callback cannot retry the source")
      result.started shouldBe false
      result.closed shouldBe false
      result.inFlight shouldBe false
      result.nextDueAt shouldBe None
      result.lastResult.get shouldBe a[Consequence.Failure[_]]
      _message(result.lastResult.get).length should be < 1024
      _message(result.lastResult.get) should not include "private-source"
      _message(result.lastResult.get) should not include "private-timer"
      timer.registrations shouldBe 1
      source.calls shouldBe 1
    }
  }

  private val _instant = Instant.parse("2026-10-01T01:02:03Z")
  private val _deadline = _instant.plusSeconds(60)
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read", DurableJobRetentionPolicy.manageScope))
  private val _ttl = DurableRetentionState(None, Some(_deadline), None, DurableDeletionState.Active, None)

  private final case class Fixture(root: Path, entity: MeasuredEntityStore, store: DurableJobStore,
    blob: MeasuredBlobStore, payloads: DurableJobOwnedBlobPayloads, maintenance: DurableJobMaintenance, context: ExecutionContext)

  private final class Source(entries: Vector[DurableJobRetentionCandidate], mode: String = "success", action: () => Unit = () => ()) extends DurableJobRetentionCandidateSource {
    private val _calls = new AtomicInteger(0)
    def calls: Int = _calls.get()
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]] = {
      _calls.incrementAndGet()
      action()
      mode match {
        case "failure" => Consequence.stateInvalid("private-source-failure")
        case "throw" => throw new IllegalStateException("private-source-exception")
        case _ => Consequence.success(entries)
      }
    }
  }

  private final class MeasuredEntityStore extends StandardEntityStore {
    private var _loads = 0
    private var _writes = 0
    private var _after_update: Option[() => Unit] = None
    var failJobId: Option[EntityId] = None
    def loads: Int = _loads
    def writes: Int = _writes
    def reset(): Unit = { _loads = 0; _writes = 0 }
    def armAfterUpdate(action: () => Unit): Unit = _after_update = Some(action)
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
      if (id.collection != DurableJobStorageQuotaEntity.Collection) _loads += 1
      super.load[T](id)
    }
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityRevisionCarrier[T]]] = {
      if (id.collection != DurableJobStorageQuotaEntity.Collection) _loads += 1
      if (failJobId.contains(id)) throw new IllegalStateException("private-job-provider-failure")
      super.loadDetached[T](id)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      val jobwrite = tc.id(changes).collection == DurableJobStoreEntity.Collection
      if (jobwrite) _writes += 1
      val result = super.updateDetached(changes, expectedRevision, executionPolicy)
      if (jobwrite) {
        val action = _after_update
        _after_update = None
        if (result.isSuccess) action.foreach(_())
      }
      result
    }
  }

  private final class MeasuredBlobStore(delegate: BlobStore) extends BlobStore {
    private var _deleted = Vector.empty[BlobStorageRef]
    def name: String = delegate.name
    def deleted: Vector[BlobStorageRef] = _deleted
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = delegate.put(request, payload)
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] = delegate.get(ref)
    def delete(ref: BlobStorageRef): Consequence[Unit] = { _deleted :+= ref; delegate.delete(ref) }
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = delegate.accessUrl(ref)
    def status(): Consequence[BlobStoreStatus] = delegate.status()
  }

  private final class RetainedTimer(mode: String = "success") extends JobTimer {
    private var _callbacks = Vector.empty[() => Unit]
    private var _cancelled = Vector.empty[Int]
    private var _shutdown_count = 0
    var failRearm: Boolean = false
    def registrations: Int = synchronized(_callbacks.size)
    def cancelled: Vector[Int] = synchronized(_cancelled)
    def shutdownCount: Int = synchronized(_shutdown_count)
    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration = synchronized {
      if (mode == "throw" || (failRearm && _callbacks.nonEmpty)) throw new IllegalStateException("private-timer-registration")
      else if (mode == "null") null
      else {
        val index = _callbacks.size
        _callbacks :+= (() => body)
        new JobTimerRegistration { def close(): Unit = RetainedTimer.this.synchronized { _cancelled :+= index } }
      }
    }
    def fire(index: Int): Unit = { val callback = synchronized(_callbacks(index)); callback() }
    override def shutdown(): Unit = synchronized { _shutdown_count += 1 }
  }

  private def _fixture(): Fixture = {
    val root = Files.createTempDirectory("job-maintenance")
    val entity = new MeasuredEntityStore
    val store = new DurableJobStore(entity)
    val context = ExecutionContext.test()
    val blob = new MeasuredBlobStore(LocalBlobStore(root))
    val payloads = new DurableJobOwnedBlobPayloads(store, entity, blob)
    Fixture(root, entity, store, blob, payloads, new DurableJobMaintenance(store, new DurableJobRetentionMaintenance(store),
      new DurableJobPayloadCleanup(store, payloads)), context)
  }

  private def _candidate(ordinal: Int, jobid: String, access: DurableJobRecordAccess = _access): DurableJobRetentionCandidate =
    DurableJobRetentionCandidate(ordinal.toLong, jobid, access)

  private def _create(fixture: Fixture, record: DurableJobRecord)(using ctx: ExecutionContext): DurableJobStoreSnapshot = _success(fixture.store.create(record, _access))
  private def _load(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): DurableJobStoreSnapshot = _success(fixture.store.load(jobid, _access)).get

  private def _attach(fixture: Fixture, active: DurableJobStoreSnapshot)(using ctx: ExecutionContext): (DurableJobStoreSnapshot, Blob) = {
    val ref = _success(fixture.payloads.putObserved(active, _access, Some("payload.bin"), ContentType.APPLICATION_OCTET_STREAM, Bag.binary(Array[Byte](1, 2, 3))))
    val metadata = _success(fixture.entity.load[Blob](_success(EntityId.parse(ref.reference)))).get
    val body = active.record.body.copy(identity = active.record.body.identity.copy(revision = 2L, updatedAt = _instant),
      inputs = Vector(DurableInputReference("payload", DurableValue.External(ref))))
    val record = _success(if (active.record.format == DurableRecordFormat.V2) DurableJobRecord.createV2(body) else DurableJobRecord.create(body))
    (_success(fixture.store.checkpoint(active, record, _access)), metadata)
  }

  private def _path(root: Path, blob: Blob): Path = root.resolve(blob.storageRef.get.container).resolve(blob.storageRef.get.key)
  private def _sidecar(root: Path, blob: Blob): Path = {
    val path = _path(root, blob)
    path.resolveSibling(path.getFileName.toString + ".blob-meta.properties")
  }

  private def _record(jobid: String, retention: DurableRetentionState = _ttl, status: DurableJobLifecycleStatus = DurableJobLifecycleStatus.Succeeded, v2: Boolean = false): DurableJobRecord = {
    val result = status match {
      case DurableJobLifecycleStatus.Succeeded => DurableResultOutcome.Succeeded(DurableValue.Absent)
      case _ => DurableResultOutcome.Pending
    }
    val body = DurableJobRecordBody(DurableJobIdentity(jobid, 1L, _instant, _instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(status, 1, DurableRunMode.Async, DurableRetryEvidence(Vector.empty, 1, None, false, false), DurableScheduleState(None, None, None)),
      Vector(DurableTaskDescriptor("root", None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component", None, "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.None, DurableTransactionScope.None, DurableTransactionOutcome.Pending), None)),
      Vector.empty, result, Vector.empty, Vector.empty, None,
      DurableDefinitionSnapshot("definition", "job", 1, 1L, "a" * 64, None, None, Map.empty), retention)
    _success(if (v2) DurableJobRecord.createV2(body) else DurableJobRecord.create(body))
  }

  private def _message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case _ => fail("expected bounded failure")
  }
  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
