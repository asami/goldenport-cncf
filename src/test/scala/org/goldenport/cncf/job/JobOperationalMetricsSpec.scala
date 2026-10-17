package org.goldenport.cncf.job

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobOperationalMetricsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 OM1 closed lifecycle fact matrix" must _scenario("E1", "R1,R2") {
    "count every fact in each persistence and run mode" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R2; E1/OM1: an empty production recorder and the independent fourteen-fact oracle")
      val recorder = new JobOperationalMetrics
      val before = recorder.snapshot()
      When("each closed lifecycle fact is observed once in each of its four modes")
      for {
        fact <- _oracle
        persistence <- Vector(JobPersistencePolicy.Persistent, JobPersistencePolicy.Ephemeral)
        mode <- Vector(JobRunMode.Sync, JobRunMode.Async)
      } recorder.observe(fact._1, persistence, mode)
      val points = recorder.snapshot()
      Then("zero initial points become exactly the deterministic fifty-six independent expected cells")
      before shouldBe Vector.empty
      points shouldBe (for {
        fact <- _oracle
        persistence <- Vector("persistent", "ephemeral")
        mode <- Vector("sync", "async")
      } yield _expected(fact, persistence, mode, 1L))
      points.size shouldBe 56
      recorder.snapshot() shouldBe points
    }
  }

  "E2 OM2 bounded mixed observation sequences" must _scenario("E2", "R1,R2,R3") {
    "ignore unknown and null facets and preserve exact finite cumulative counts" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R2,R3; E2/OM2: finite generated operations and an independent fourteen-tuple oracle, seed 697097101")
      val operation = for {
        fact <- Gen.chooseNum(-3, 13)
        persistence <- Gen.chooseNum(-1, 1)
        mode <- Gen.chooseNum(-1, 1)
      } yield (fact, persistence, mode)
      val sequences = Gen.chooseNum(1, 200).flatMap(size => Gen.listOfN(size, operation))
      val property = Prop.forAll(sequences) { operations =>
        val recorder = new JobOperationalMetrics
        recorder.observe("job.private-tenant-subject-path", JobPersistencePolicy.Persistent, JobRunMode.Sync)
        recorder.observe(null, JobPersistencePolicy.Persistent, JobRunMode.Sync)
        recorder.observe("job.submitted", null, JobRunMode.Sync)
        recorder.observe("job.submitted", JobPersistencePolicy.Persistent, null)
        val invalidempty = recorder.snapshot().isEmpty
        val unknown = operations.zipWithIndex.map { case ((fact, persistence, mode), index) =>
          val name = if (fact >= 0) _oracle(fact)._1 else if (fact == -1) null else s"job.private-$index-$fact"
          val policy = persistence match {
            case 0 => JobPersistencePolicy.Persistent
            case 1 => JobPersistencePolicy.Ephemeral
            case _ => null
          }
          val runmode = mode match {
            case 0 => JobRunMode.Sync
            case 1 => JobRunMode.Async
            case _ => null
          }
          (name, policy, runmode)
        }
        unknown.foreach { case (name, policy, mode) => recorder.observe(name, policy, mode) }
        // Repeat legitimate observations without growing the closed cell space.
        unknown.foreach { case (name, policy, mode) => recorder.observe(name, policy, mode) }
        val expected = (for {
          factindex <- _oracle.indices
          persistence <- 0 to 1
          mode <- 0 to 1
          count = operations.count(_ == ((factindex, persistence, mode))).toLong * 2L
          if count > 0L
        } yield _expected(_oracle(factindex),
          if (persistence == 0) "persistent" else "ephemeral",
          if (mode == 0) "sync" else "async", count)).toVector
        val points = recorder.snapshot()
        invalidempty && points == expected && points.size <= 56 && recorder.snapshot() == points
      }
      When("ScalaCheck executes at least twenty-four finite mixed sequences with ordinary shrinking")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
        .withInitialSeed(Seed(697097101L)), property)
      Then("all expected cells and counts match while invalid observations retain nothing")
      withClue(s"OM2 seed 697097101: $checked ") {
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }
  }

  "E3 OM3 cumulative saturation" must _scenario("E3", "R2") {
    "use the production increment primitive at zero and the maximum boundary" in {
      Given("docs/spec/job-operational-metrics-contract.md R2; E3/OM3: zero, maximum-minus-one and maximum cumulative counts")
      val values = Vector(0L, Long.MaxValue - 1L, Long.MaxValue)
      When("the production saturation primitive increments the boundary values")
      val results = values.map(JobOperationalMetrics.saturatingIncrement)
      Then("the counter increments once or remains saturated without overflow or reset")
      results shouldBe Vector(1L, Long.MaxValue, Long.MaxValue)
    }
  }

  "E4 OM4 shared recorder concurrency" must _scenario("E4", "R2,R4") {
    "conserve same and disjoint cell observations from two joined physical threads" in {
      Given("docs/spec/job-operational-metrics-contract.md R2,R4; E4/OM4: two physical actors and one production recorder with bounded gates and cleanup")
      val recorder = new JobOperationalMetrics
      val ready = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val failure = new AtomicReference[Throwable]()
      val threads = Vector(
        _actor(recorder, ready, release, failure, "job.failed", JobRunMode.Async),
        _actor(recorder, ready, release, failure, "job.resumed", JobRunMode.Sync)
      )
      try {
        When("both actors concurrently emit one thousand common facts and one thousand disjoint facts each")
        threads.foreach(_.start())
        val readyresult = ready.await(5L, TimeUnit.SECONDS)
        release.countDown()
        threads.foreach(_.join(5000L))
        val points = recorder.snapshot()
        Then("all actors have joined and all four thousand observations have exact conserved counts")
        readyresult shouldBe true
        threads.foreach(_.isAlive shouldBe false)
        failure.get() shouldBe null
        points shouldBe Vector(
          _expected(_oracle(0), "persistent", "sync", 2000L),
          _expected(_oracle(3), "ephemeral", "async", 1000L),
          _expected(_oracle(13), "ephemeral", "sync", 1000L)
        )
      } finally {
        release.countDown()
        threads.filter(_.isAlive).foreach(_.interrupt())
        threads.foreach(_.join(5000L))
        threads.foreach(_.isAlive shouldBe false)
      }
    }
  }

  private val _oracle = Vector(
    ("job.submitted", "admission", "submit", "accepted", false),
    ("job.running", "execution", "run", "started", false),
    ("job.succeeded", "execution", "run", "succeeded", false),
    ("job.failed", "execution", "run", "failed", true),
    ("job.delayed.scheduled", "queue", "delay", "scheduled", false),
    ("job.delayed.enqueued", "queue", "delay", "enqueued", false),
    ("job.retry.immediate.submitted", "retry", "immediate", "submitted", false),
    ("job.retry.delayed.scheduled", "retry", "delayed", "scheduled", false),
    ("job.retry.delayed.submitted", "retry", "delayed", "submitted", false),
    ("job.retry.submitted", "retry", "control", "submitted", false),
    ("job.recovery-required", "recovery", "checkpoint", "required", true),
    ("job.cancelled", "control", "cancel", "applied", false),
    ("job.suspended", "control", "suspend", "applied", false),
    ("job.resumed", "control", "resume", "applied", false)
  )

  private def _expected(
    fact: (String, String, String, String, Boolean),
    persistence: String,
    mode: String,
    count: Long
  ): RuntimeMetricPoint = RuntimeMetricPoint("job.lifecycle", "facts", Map(
    "family" -> fact._2, "operation" -> fact._3, "outcome" -> fact._4,
    "persistence" -> persistence, "run_mode" -> mode
  ), count, if (fact._5) count else 0L)

  private def _actor(
    recorder: JobOperationalMetrics,
    ready: CountDownLatch,
    release: CountDownLatch,
    failure: AtomicReference[Throwable],
    name: String,
    mode: JobRunMode
  ): Thread = new Thread(new Runnable {
    def run(): Unit = try {
      ready.countDown()
      if (!release.await(5L, TimeUnit.SECONDS)) throw new IllegalStateException("OM4 gate timeout")
      (0 until 1000).foreach { _ =>
        recorder.observe("job.submitted", JobPersistencePolicy.Persistent, JobRunMode.Sync)
        recorder.observe(name, JobPersistencePolicy.Ephemeral, mode)
      }
    } catch {
      case exception: InterruptedException =>
        Thread.currentThread().interrupt()
        failure.compareAndSet(null, exception)
        ()
      case NonFatal(exception) =>
        failure.compareAndSet(null, exception)
        ()
    }
  }, "job-operational-metrics-actor")

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operational-metrics, example:$example, rules:$rules, phase:69.7")
}
