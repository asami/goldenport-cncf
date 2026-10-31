package org.goldenport.cncf.job

import java.time.Instant
import java.util.concurrent.{CountDownLatch, PriorityBlockingQueue, TimeUnit}
import scala.collection.mutable
import scala.util.Random
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
class JobResourceBudgetSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Q1 finite policy and atomic admission" should afterWord(
    "in spec:job-operations-contract, example:Q1, rules:R1,R2, phase:69.7"
  ) {
    "admit inclusive ceilings and refuse each invalid field without partial reservation" in {
      Given("docs/spec/job-operations-contract.md; R1,R2; Q1; default ceilings and lowered finite policies")
      val default = JobResourcePolicy.default
      val setters: Vector[(JobResourcePolicy, Int) => JobResourcePolicy] = Vector(
        (p, n) => p.copy(maxretainedjobs = n), (p, n) => p.copy(maxpendingwork = n),
        (p, n) => p.copy(maxexecutingwork = n), (p, n) => p.copy(maxtaskspersubmission = n),
        (p, n) => p.copy(maxretryattempts = n)
      )
      val ceilings = Vector(4096, 1024, 64, 1024, 32)
      val budget = new JobResourceBudget(JobResourcePolicy(2, 1, 1, 1, 1))
      val owner = budget.newOwner()

      When("every policy boundary is admitted and a combined request exceeds pending capacity")
      default.validate.isSuccess shouldBe true
      setters.zip(ceilings).foreach { case (set, ceiling) =>
        set(default, 1).validate.isSuccess shouldBe true
        set(default, ceiling).validate.isSuccess shouldBe true
        Vector(-1, 0, ceiling + 1).foreach(n => set(default, n).validate.isSuccess shouldBe false)
      }
      val lease = _success(budget.reserve(owner, pending = true))
      val before = budget.snapshot
      val refused = budget.reserve(owner, retained = true, pending = true)

      Then("refusal preserves all counters and a repeated release returns capacity once")
      refused.isSuccess shouldBe false
      budget.snapshot shouldBe before
      budget.release(lease)
      budget.release(lease)
      budget.snapshot shouldBe JobResourceSnapshot(0, 0, 0, budget.policy)
    }
  }

  "Q2 retained identity and generated lease lifetimes" should afterWord(
    "in spec:job-operations-contract, example:Q2, rules:R2,R3, phase:69.7"
  ) {
    "retain committed identities and keep seeded reserve and release sequences bounded" in {
      Given("docs/spec/job-operations-contract.md; R2,R3; Q2; provisional reservations and distinct retained identities")
      val budget = new JobResourceBudget(JobResourcePolicy(4, 3, 2, 1, 1))
      val owner = budget.newOwner()
      val id = _id("committed")
      val provisional = _success(budget.reserve(owner, retained = true, pending = true))

      When("one identity commits twice and existing-identity work is released")
      budget.snapshot.retained shouldBe 1
      budget.commit(provisional, id)
      budget.commit(provisional, id)
      budget.release(provisional)
      val retry = _success(budget.reserve(owner, retained = true, pending = true, jobid = Some(id)))
      budget.commit(retry, id)
      budget.release(retry)
      _success(budget.seed(Iterator(id, id, _id("terminal"))))

      Then("terminal and committed identities survive release and every generated counter stays finite")
      budget.snapshot.retained shouldBe 2
      val random = new Random(697093L)
      val live = mutable.ArrayBuffer.empty[JobResourceBudget.Lease]
      (0 until 300).foreach { _ =>
        if (random.nextBoolean() && live.nonEmpty) {
          val lease = live.remove(random.nextInt(live.size))
          budget.release(lease)
          budget.release(lease)
        } else {
          val executing = random.nextBoolean()
          budget.reserve(owner, retained = random.nextBoolean(), pending = !executing,
            executing = executing).toOption.foreach(live += _)
        }
        val snapshot = budget.snapshot
        snapshot.retained should (be >= 2 and be <= 4)
        snapshot.pending should (be >= 0 and be <= 3)
        snapshot.executing should (be >= 0 and be <= 2)
      }
      live.foreach(budget.release)
      budget.snapshot shouldBe JobResourceSnapshot(2, 0, 0, budget.policy)
      val before = budget.snapshot
      budget.seed((0 until 5).iterator.map(n => _id(s"oversupply$n"))).isSuccess shouldBe false
      budget.snapshot shouldBe before
    }
  }

  "Q3 pending transfer and owner close" should afterWord(
    "in spec:job-operations-contract, example:Q3, rules:R3,R4,R8, phase:69.7"
  ) {
    "leave busy work queued and release only the closing owner's pending leases" in {
      Given("docs/spec/job-operations-contract.md; R3,R4,R8; Q3; two owners sharing one executing slot")
      val budget = new JobResourceBudget(JobResourcePolicy(4, 3, 1, 1, 1))
      val owner = budget.newOwner()
      val other = budget.newOwner()
      val queue = _queue()
      val otherqueue = _queue()
      val blocker = _success(budget.reserve(other, executing = true))
      val lease = _success(budget.reserve(owner, pending = true, jobid = Some(_id("queued")), root = true))
      val foreign = _success(budget.reserve(other, pending = true))
      budget.enqueue(lease, InMemoryJobEngine.SchedulerWorkItem.JobRun(1L, 0, _id("queued")), queue) shouldBe true

      When("a busy claim is followed by capacity release, a successful claim and owner close")
      val before = budget.snapshot
      budget.claim(owner, queue, blocking = false) shouldBe None
      budget.snapshot shouldBe before
      queue.size() shouldBe 1
      budget.release(blocker)
      budget.claim(owner, queue, blocking = false).map(_.lease) shouldBe Some(lease)
      budget.snapshot.pending shouldBe 1
      budget.snapshot.executing shouldBe 1
      budget.removePending(owner, None, queue, close = true) shouldBe empty

      Then("active physical work and the other owner survive close until their own release")
      budget.snapshot.executing shouldBe 1
      budget.isPending(foreign) shouldBe true
      budget.release(lease)
      budget.removePending(other, None, otherqueue, close = true) shouldBe empty
      budget.snapshot.pending shouldBe 0
      budget.snapshot.executing shouldBe 0
      budget.consumeGeneration(foreign) shouldBe false
    }
  }

  "Q4 concurrent admission" should afterWord(
    "in spec:job-operations-contract, example:Q4, rules:R2,R3,R4, phase:69.7"
  ) {
    "admit exactly the cap at a bounded barrier and return capacity through finally" in {
      Given("docs/spec/job-operations-contract.md; R2,R3,R4; Q4; eight callers competing for three provisional slots")
      val budget = new JobResourceBudget(JobResourcePolicy(3, 3, 1, 1, 1))
      val owner = budget.newOwner()
      val start = new CountDownLatch(1)
      val attempted = new CountDownLatch(8)
      val release = new CountDownLatch(1)
      val leases = new java.util.concurrent.ConcurrentLinkedQueue[JobResourceBudget.Lease]()
      val failures = new java.util.concurrent.ConcurrentLinkedQueue[Throwable]()
      val threads = Vector.fill(8)(new Thread(() => {
        var attemptedreservation = false
        try {
          if (!start.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout")
          val lease = budget.reserve(owner, retained = true, pending = true).toOption
          lease.foreach(leases.add)
          attemptedreservation = true
          attempted.countDown()
          try {
            if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout")
            throw new IllegalStateException("simulated dispatch failure")
          } catch {
            case _: IllegalStateException => ()
          } finally lease.foreach(budget.release)
        } catch {
          case e: Throwable => failures.add(e)
        } finally {
          if (!attemptedreservation) attempted.countDown()
        }
        ()
      }))

      When("all callers reserve before any caller is allowed to release")
      threads.foreach(_.start())
      try {
        start.countDown()
        attempted.await(3, TimeUnit.SECONDS) shouldBe true
        leases.size() shouldBe 3
        budget.snapshot shouldBe JobResourceSnapshot(3, 3, 0, budget.policy)
      } finally {
        release.countDown()
        threads.foreach(_.join(3000L))
      }

      Then("no caller overshoots or leaks unpublished capacity after exceptional completion")
      threads.exists(_.isAlive) shouldBe false
      failures.isEmpty shouldBe true
      budget.snapshot shouldBe JobResourceSnapshot(0, 0, 0, budget.policy)
    }
  }

  private def _queue(): PriorityBlockingQueue[JobReservedWork] =
    new PriorityBlockingQueue[JobReservedWork](11,
      java.util.Comparator.comparingInt[JobReservedWork](_.work.priority).thenComparingLong(_.work.sequence))

  private def _id(entropy: String): JobId =
    JobId("cncf", "quota", Some(Instant.EPOCH), Some(entropy))

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
