package org.goldenport.cncf.job

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobOperationMetricsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 PM1 closed completed-result matrix" must _scenario("E1", "R1,R2,R4,R5") {
    "retain identical results and expose exactly eleven independent cells" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R2,R4,R5; E1/PM1: an empty actual recorder and independently spelled eleven-result oracle with private diagnostic markers")
      val recorder = new JobOperationMetrics
      recorder.snapshot() shouldBe Vector.empty
      When("each closed completed result is observed once, including successful None, then null page operation is ignored")
      _oracle.indices.foreach(index => _observe(recorder, index))
      val points = recorder.snapshot()
      val ignored = Consequence.success(Option.empty[String])
      (recorder.observePage(null, ignored) eq ignored) shouldBe true
      Then("every wrapper returns its supplied instance and the positive ordered cells contain only closed labels, failed errors and zero durations")
      points shouldBe _oracle.indices.map(index => _point(index, 1L)).toVector
      recorder.snapshot() shouldBe points
      points.size shouldBe 11
      points.foreach { point =>
        point.labels.keySet shouldBe Set("operation", "outcome")
        point.durationCount shouldBe 0L
        point.durationTotalMillis shouldBe 0L
        point.durationMinMillis shouldBe None
        point.durationMaxMillis shouldBe None
        point.durationAvgMillis shouldBe None
        Vector(point.scope, point.name).foreach(_ should not include "PRIVATE")
        point.labels.foreach { case (key, value) =>
          key should not include "PRIVATE"
          value should not include "PRIVATE"
        }
      }
    }
  }

  "E2 PM2 finite completed-result sequences" must _scenario("E2", "R1,R2") {
    "conserve an independent multiset with ordinary shrinking" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R2; E2/PM2: finite generated closed observations and ignored null-page observations, seed 697097202")
      val sequences = Gen.chooseNum(1, 200).flatMap(size => Gen.listOfN(size, Gen.chooseNum(0, 11)))
      val property = Prop.forAll(sequences) { observations =>
        val recorder = new JobOperationMetrics
        observations.foreach(index => _observe(recorder, index))
        val expected = _oracle.indices.flatMap { index =>
          val count = observations.count(_ == index).toLong
          Option.when(count > 0L)(_point(index, count))
        }.toVector
        val points = recorder.snapshot()
        points == expected && points.size <= 11 &&
          points.map(_.count).sum == observations.count(_ < 11).toLong && recorder.snapshot() == points
      }
      When("ScalaCheck runs at least twenty-four ordinary shrinking sequences against the actual recorder helpers")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
        .withInitialSeed(Seed(697097202L)), property)
      Then("all counts equal the independent closed-result multiset and snapshots are pure and bounded")
      withClue(s"PM2 seed 697097202: $checked ") {
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }
  }

  "E3 PM3 joined physical recorder actors" must _scenario("E3", "R2") {
    "conserve two thousand successful and two thousand failed submissions" in {
      Given("docs/spec/job-operation-metrics-contract.md R2; E3/PM3: one actual recorder and two physical actors with captured failures and bounded five-second cleanup")
      val recorder = new JobOperationMetrics
      val ready = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val failure = new AtomicReference[Throwable]()
      val threads = Vector(_actor(recorder, ready, release, failure, 0), _actor(recorder, ready, release, failure, 1))
      try {
        When("the joined actors each observe two thousand actual completed submission results")
        threads.foreach(_.start())
        val readyresult = ready.await(5L, TimeUnit.SECONDS)
        release.countDown()
        threads.foreach(_.join(5000L))
        val points = recorder.snapshot()
        Then("all actors terminate without exceptions and only the failed cell carries its two thousand errors")
        readyresult shouldBe true
        threads.foreach(_.isAlive shouldBe false)
        failure.get() shouldBe null
        points shouldBe Vector(_point(0, 2000L), _point(1, 2000L))
        recorder.snapshot() shouldBe points
      } finally {
        release.countDown()
        threads.filter(_.isAlive).foreach(_.interrupt())
        threads.foreach(_.join(5000L))
        threads.foreach(_.isAlive shouldBe false)
      }
    }
  }

  private val _oracle = Vector(
    ("submit", "accepted", false), ("submit", "failed", true),
    ("control", "applied", false), ("control", "replayed", false), ("control", "failed", true),
    ("page", "returned", false), ("page", "failed", true),
    ("tasks", "returned", false), ("tasks", "failed", true),
    ("timeline", "returned", false), ("timeline", "failed", true)
  )

  private def _point(index: Int, count: Long): RuntimeMetricPoint = {
    val (operation, outcome, error) = _oracle(index)
    RuntimeMetricPoint("job.operation", "calls", Map("operation" -> operation, "outcome" -> outcome),
      count, errorCount = if (error) count else 0L)
  }

  private def _observe(recorder: JobOperationMetrics, index: Int): Unit = index match {
    case 0 =>
      val result = Consequence.success(Option.empty[String])
      (recorder.observeSubmission(result) eq result) shouldBe true
    case 1 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET")
      (recorder.observeSubmission(result) eq result) shouldBe true
    case 2 | 3 =>
      val result = Consequence.success(JobControlResponse(JobId("private", "PRIVATE_JOB"),
        JobStatus.Cancelled, Some(OperationResponse.Scalar("PRIVATE-PAYLOAD")), false, changed = index == 2))
      (recorder.observeControl(result) eq result) shouldBe true
    case 4 =>
      val result = Consequence.operationInvalid[JobControlResponse]("PRIVATE-CONTROL")
      (recorder.observeControl(result) eq result) shouldBe true
    case 11 =>
      val result = Consequence.success(Option.empty[String])
      (recorder.observePage(null, result) eq result) shouldBe true
    case _ =>
      val operation = index match {
        case 5 | 6 => JobOperationMetrics.PageOperation.Page
        case 7 | 8 => JobOperationMetrics.PageOperation.Tasks
        case _ => JobOperationMetrics.PageOperation.Timeline
      }
      val result = if (index % 2 == 1) Consequence.success(Option.empty[String])
        else Consequence.operationInvalid[Option[String]]("PRIVATE-PAGE")
      (recorder.observePage(operation, result) eq result) shouldBe true
  }

  private def _actor(recorder: JobOperationMetrics, ready: CountDownLatch, release: CountDownLatch,
    failure: AtomicReference[Throwable], index: Int): Thread = new Thread(new Runnable {
    def run(): Unit = try {
      ready.countDown()
      if (!release.await(5L, TimeUnit.SECONDS)) throw new IllegalStateException("PM3 gate timeout")
      (0 until 2000).foreach(_ => _observe(recorder, index))
    } catch {
      case exception: InterruptedException =>
        Thread.currentThread().interrupt()
        failure.compareAndSet(null, exception)
        ()
      case NonFatal(exception) =>
        failure.compareAndSet(null, exception)
        ()
    }
  }, "job-operation-metrics-actor")

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operation-metrics, example:$example, rules:$rules, phase:69.7")
}
