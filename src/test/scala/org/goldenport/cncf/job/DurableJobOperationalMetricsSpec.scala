package org.goldenport.cncf.job

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.metrics.RuntimeMetricPoint
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
final class DurableJobOperationalMetricsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 DU1 closed durable completed-result matrix" should _scenario("E1", "R1,R2,R4,R7,R8") {
    "retain result identities and expose all fifty-nine independent closed cells" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R2,R4,R7,R8; E1/DU1: an actual empty recorder and an independent explicit outcome/error oracle")
      val recorder = new DurableJobOperationalMetrics
      recorder.snapshot() shouldBe Vector.empty
      When("each of the fifty-nine native helper outcomes is observed, with null facet markers ignored")
      _oracle.indices.foreach(index => _observe(recorder, index))
      _observe(recorder, 59)
      val points = recorder.snapshot()
      Then("each helper retains the original object and ordered positive cells expose exactly two private-data-free labels and zero durations")
      points shouldBe _oracle.indices.map(index => _point(index, 1L)).toVector
      points.size shouldBe 59
      points.foreach { point =>
        point.scope shouldBe "job.durable.operation"
        point.name shouldBe "calls"
        point.labels.keySet shouldBe Set("operation", "outcome")
        point.durationCount shouldBe 0L
        point.durationTotalMillis shouldBe 0L
        point.durationMinMillis shouldBe None
        point.durationMaxMillis shouldBe None
        point.durationAvgMillis shouldBe None
        (Vector(point.scope, point.name) ++ point.labels.keys ++ point.labels.values).foreach(_ should not include "PRIVATE")
      }
      (0 until 20).foreach(_ => recorder.snapshot() shouldBe points)
      JobOperationalMetrics.saturatingIncrement(Long.MaxValue - 1L) shouldBe Long.MaxValue
      JobOperationalMetrics.saturatingIncrement(Long.MaxValue) shouldBe Long.MaxValue
      Given("E1/R1,R7: normally returned null and null-entry report markers contain no actual candidate facet")
      val absent = Consequence.success[DurableJobRetentionReport](null)
      val entries = Consequence.success(DurableJobRetentionReport(Vector(null, DurableJobRetentionReportEntry(0L, null))))
      When("the actual sweep helper observes both normally returned reports")
      val first = recorder.observeRetentionSweep(absent)
      val second = recorder.observeRetentionSweep(entries)
      Then("both original report results are returned, only the returned sweep cell advances and no candidate cell is invented")
      (first eq absent) shouldBe true
      (second eq entries) shouldBe true
      recorder.snapshot() shouldBe points.updated(21, _point(21, 3L))
    }
  }

  "E2 DU2 finite durable observation prefixes" should _scenario("E2", "R1,R2,R4") {
    "conserve the independent multiset at every shrinking sequence prefix" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R2,R4; E2/DU2: ordinary shrinking finite lists of 1..100 closed helper outcomes and ignored null facets, seed 697097302")
      val sequences = Gen.chooseNum(1, 100).flatMap(size => Gen.listOfN(size, Gen.chooseNum(0, 59)))
      val property = Prop.forAll(sequences) { observations =>
        val recorder = new DurableJobOperationalMetrics
        var prefix = Vector.empty[Int]
        observations.forall { index =>
          _observe(recorder, index)
          prefix :+= index
          val expected = _oracle.indices.flatMap { cell =>
            val count = prefix.count(_ == cell).toLong
            Option.when(count > 0L)(_point(cell, count))
          }.toVector
          val points = recorder.snapshot()
          points == expected && points.size <= 59 && points.map(_.count).sum == prefix.count(_ < 59).toLong &&
            recorder.snapshot() == points
        }
      }
      When("ScalaCheck runs at least twenty-four active ordinary shrinking sequences against the actual helpers")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(697097302L)), property)
      Then("all prefix counts, exact error subsets and snapshot bounds equal the independent closed-outcome multiset")
      withClue(s"DU2 seed 697097302: $checked ") {
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }
  }

  "E3 DU3 joined physical recorder actors" should _scenario("E3", "R2,R4") {
    "conserve two thousand successful and two thousand failed creates" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R2,R4; E3/DU3: one recorder and two physical actors with captured failures and bounded five-second joins")
      val recorder = new DurableJobOperationalMetrics
      val ready = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val failure = new AtomicReference[Throwable]()
      val actors = Vector(_actor(recorder, ready, release, failure, 0), _actor(recorder, ready, release, failure, 1))
      try {
        When("the joined physical actors each observe two thousand strict completed creates")
        actors.foreach(_.start())
        val admitted = ready.await(5L, TimeUnit.SECONDS)
        release.countDown()
        actors.foreach(_.join(5000L))
        val points = recorder.snapshot()
        Then("all actors terminate without hidden failures, only failed creates carry errors and repeated reads preserve counts")
        admitted shouldBe true
        actors.foreach(_.isAlive shouldBe false)
        failure.get() shouldBe null
        points shouldBe Vector(_point(0, 2000L), _point(1, 2000L))
        (0 until 20).foreach(_ => recorder.snapshot() shouldBe points)
      } finally {
        release.countDown()
        actors.filter(_.isAlive).foreach(_.interrupt())
        actors.foreach(_.join(5000L))
        actors.foreach(_.isAlive shouldBe false)
      }
    }
  }

  private val _oracle = Vector(
    ("create", "succeeded", false),
    ("create", "failed", true),
    ("load", "found", false),
    ("load", "missing", false),
    ("load", "failed", true),
    ("startup", "admitted", false),
    ("startup", "missing", false),
    ("startup", "corrupt", true),
    ("startup", "refused", true),
    ("checkpoint", "succeeded", false),
    ("checkpoint", "failed", true),
    ("storage_snapshot", "succeeded", false),
    ("storage_snapshot", "failed", true),
    ("payload_snapshot", "succeeded", false),
    ("payload_snapshot", "failed", true),
    ("payload_put", "succeeded", false),
    ("payload_put", "failed", true),
    ("payload_prepare", "succeeded", false),
    ("payload_prepare", "failed", true),
    ("payload_delete", "succeeded", false),
    ("payload_delete", "failed", true),
    ("retention_sweep", "returned", false),
    ("retention_sweep", "failed", true),
    ("retention", "kept", false),
    ("retention", "not_due", false),
    ("retention", "held", false),
    ("retention", "not_terminal", false),
    ("retention", "already_expired", false),
    ("retention", "already_deleted", false),
    ("retention", "tombstoned", false),
    ("retention", "expired", false),
    ("retention", "deleted", false),
    ("retention", "hold_applied", false),
    ("retention", "hold_released", false),
    ("retention", "missing", true),
    ("retention", "corrupt", true),
    ("retention", "refused", true),
    ("retention", "stale", true),
    ("retention", "invalid_transition", true),
    ("retention", "revision_exhausted", true),
    ("retention", "write_refused", true),
    ("cleanup", "tombstoned", false),
    ("cleanup", "already_tombstoned", false),
    ("cleanup", "not_deleted", false),
    ("cleanup", "held", false),
    ("cleanup", "not_due", false),
    ("cleanup", "not_terminal", false),
    ("cleanup", "missing", true),
    ("cleanup", "corrupt", true),
    ("cleanup", "refused", true),
    ("cleanup", "stale", true),
    ("cleanup", "revision_exhausted", true),
    ("cleanup", "invalid_time", true),
    ("cleanup", "ownership_refused", true),
    ("cleanup", "resource_bound_refused", true),
    ("cleanup", "payload_delete_refused", true),
    ("cleanup", "checkpoint_refused", true),
    ("maintenance_sweep", "returned", false),
    ("maintenance_sweep", "failed", true)
  )

  private def _point(index: Int, count: Long): RuntimeMetricPoint = {
    val (operation, outcome, error) = _oracle(index)
    RuntimeMetricPoint("job.durable.operation", "calls", Map("operation" -> operation, "outcome" -> outcome),
      count, errorCount = if (error) count else 0L)
  }

  private def _observe(recorder: DurableJobOperationalMetrics, index: Int): Unit = index match {
    case 0 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observeCreate(result) eq result) shouldBe true
    case 1 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observeCreate(result) eq result) shouldBe true
    case 2 =>
      val result = Consequence.success(Option("PRIVATE-ROW"))
      (recorder.observeLoad(result) eq result) shouldBe true
    case 3 =>
      val result = Consequence.success(Option.empty[String])
      (recorder.observeLoad(result) eq result) shouldBe true
    case 4 =>
      val result = Consequence.operationInvalid[Option[String]]("PRIVATE-LOAD")
      (recorder.observeLoad(result) eq result) shouldBe true
    case 5 =>
      val result = DurableJobStoreStartupRecoveryLoad.Admitted(null)
      (recorder.observeStartup(result) eq result) shouldBe true
    case 6 =>
      val result = DurableJobStoreStartupRecoveryLoad.Missing
      (recorder.observeStartup(result) eq result) shouldBe true
    case 7 =>
      val result = DurableJobStoreStartupRecoveryLoad.Corrupt
      (recorder.observeStartup(result) eq result) shouldBe true
    case 8 =>
      val result = DurableJobStoreStartupRecoveryLoad.Refused
      (recorder.observeStartup(result) eq result) shouldBe true
    case 9 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observeCheckpoint(result) eq result) shouldBe true
    case 10 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observeCheckpoint(result) eq result) shouldBe true
    case 11 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observeStorageSnapshot(result) eq result) shouldBe true
    case 12 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observeStorageSnapshot(result) eq result) shouldBe true
    case 13 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observePayloadSnapshot(result) eq result) shouldBe true
    case 14 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observePayloadSnapshot(result) eq result) shouldBe true
    case 15 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observePayloadPut(result) eq result) shouldBe true
    case 16 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observePayloadPut(result) eq result) shouldBe true
    case 17 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observePayloadPrepare(result) eq result) shouldBe true
    case 18 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observePayloadPrepare(result) eq result) shouldBe true
    case 19 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observePayloadDelete(result) eq result) shouldBe true
    case 20 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observePayloadDelete(result) eq result) shouldBe true
    case 21 =>
      val result = Consequence.success(DurableJobRetentionReport(Vector.empty))
      (recorder.observeRetentionSweep(result) eq result) shouldBe true
    case 22 =>
      val result = Consequence.operationInvalid[DurableJobRetentionReport]("PRIVATE-SOURCE")
      (recorder.observeRetentionSweep(result) eq result) shouldBe true
    case 23 =>
      val result = DurableJobRetentionFacet.Kept
      (recorder.observeRetention(result) eq result) shouldBe true
    case 24 =>
      val result = DurableJobRetentionFacet.NotDue
      (recorder.observeRetention(result) eq result) shouldBe true
    case 25 =>
      val result = DurableJobRetentionFacet.Held
      (recorder.observeRetention(result) eq result) shouldBe true
    case 26 =>
      val result = DurableJobRetentionFacet.NotTerminal
      (recorder.observeRetention(result) eq result) shouldBe true
    case 27 =>
      val result = DurableJobRetentionFacet.AlreadyExpired
      (recorder.observeRetention(result) eq result) shouldBe true
    case 28 =>
      val result = DurableJobRetentionFacet.AlreadyDeleted
      (recorder.observeRetention(result) eq result) shouldBe true
    case 29 =>
      val result = DurableJobRetentionFacet.Tombstoned
      (recorder.observeRetention(result) eq result) shouldBe true
    case 30 =>
      val result = DurableJobRetentionFacet.Expired
      (recorder.observeRetention(result) eq result) shouldBe true
    case 31 =>
      val result = DurableJobRetentionFacet.Deleted
      (recorder.observeRetention(result) eq result) shouldBe true
    case 32 =>
      val result = DurableJobRetentionFacet.HoldApplied
      (recorder.observeRetention(result) eq result) shouldBe true
    case 33 =>
      val result = DurableJobRetentionFacet.HoldReleased
      (recorder.observeRetention(result) eq result) shouldBe true
    case 34 =>
      val result = DurableJobRetentionFacet.Missing
      (recorder.observeRetention(result) eq result) shouldBe true
    case 35 =>
      val result = DurableJobRetentionFacet.Corrupt
      (recorder.observeRetention(result) eq result) shouldBe true
    case 36 =>
      val result = DurableJobRetentionFacet.Refused
      (recorder.observeRetention(result) eq result) shouldBe true
    case 37 =>
      val result = DurableJobRetentionFacet.Stale
      (recorder.observeRetention(result) eq result) shouldBe true
    case 38 =>
      val result = DurableJobRetentionFacet.InvalidTransition
      (recorder.observeRetention(result) eq result) shouldBe true
    case 39 =>
      val result = DurableJobRetentionFacet.RevisionExhausted
      (recorder.observeRetention(result) eq result) shouldBe true
    case 40 =>
      val result = DurableJobRetentionFacet.WriteRefused
      (recorder.observeRetention(result) eq result) shouldBe true
    case 41 =>
      val result = DurableJobCleanupFacet.Tombstoned
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 42 =>
      val result = DurableJobCleanupFacet.AlreadyTombstoned
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 43 =>
      val result = DurableJobCleanupFacet.NotDeleted
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 44 =>
      val result = DurableJobCleanupFacet.Held
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 45 =>
      val result = DurableJobCleanupFacet.NotDue
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 46 =>
      val result = DurableJobCleanupFacet.NotTerminal
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 47 =>
      val result = DurableJobCleanupFacet.Missing
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 48 =>
      val result = DurableJobCleanupFacet.Corrupt
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 49 =>
      val result = DurableJobCleanupFacet.Refused
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 50 =>
      val result = DurableJobCleanupFacet.Stale
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 51 =>
      val result = DurableJobCleanupFacet.RevisionExhausted
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 52 =>
      val result = DurableJobCleanupFacet.InvalidTime
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 53 =>
      val result = DurableJobCleanupFacet.OwnershipRefused
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 54 =>
      val result = DurableJobCleanupFacet.ResourceBoundRefused
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 55 =>
      val result = DurableJobCleanupFacet.PayloadDeleteRefused
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 56 =>
      val result = DurableJobCleanupFacet.CheckpointRefused
      (recorder.observeCleanup(result) eq result) shouldBe true
    case 57 =>
      val result = Consequence.success("PRIVATE-PAYLOAD")
      (recorder.observeMaintenanceSweep(result) eq result) shouldBe true
    case 58 =>
      val result = Consequence.operationInvalid[String]("PRIVATE-SECRET /PRIVATE/path")
      (recorder.observeMaintenanceSweep(result) eq result) shouldBe true
    case 59 =>
      recorder.observeRetention(null) shouldBe null
      recorder.observeCleanup(null) shouldBe null
      recorder.observeStartup(null) shouldBe null
    case other => fail(s"unregistered DU observation: $other")
  }

  private def _actor(recorder: DurableJobOperationalMetrics, ready: CountDownLatch, release: CountDownLatch,
    failure: AtomicReference[Throwable], index: Int): Thread = new Thread(new Runnable {
    def run(): Unit = try {
      ready.countDown()
      if (!release.await(5L, TimeUnit.SECONDS)) throw new IllegalStateException("DU3 bounded gate timeout")
      (0 until 2000).foreach(_ => _observe(recorder, index))
    } catch {
      case error: InterruptedException =>
        Thread.currentThread().interrupt()
        failure.compareAndSet(null, error)
        ()
      case NonFatal(error) =>
        failure.compareAndSet(null, error)
        ()
    }
  }, "durable-job-operation-metrics-actor")

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-operational-metrics, example:$example, rules:$rules, phase:69.7")
}
