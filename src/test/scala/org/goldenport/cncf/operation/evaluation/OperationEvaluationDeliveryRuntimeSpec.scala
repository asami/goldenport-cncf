package org.goldenport.cncf.operation.evaluation

import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.record.Record
import org.goldenport.schema.DataConfidentiality
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for finite automatic-fact delivery.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationDeliveryRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "OperationEvaluationDeliveryRuntime" should {
    "enforce finite delivery bounds" which {
    "bound a stalled provider without replacing it with an unbounded wait" in {
      Given("a runtime with a finite provider timeout")
      val runtime = new OperationEvaluationDeliveryRuntime(OperationEvaluationDeliveryPolicy(
        workerCount = 1,
        queueCapacity = 1,
        invocationTimeout = Duration.ofMillis(25)
      ))
      try {
        val fact = _start_fact()
        val sink = _sink()
        val context = ExecutionContext.create()

        When("the provider does not complete before the deadline")
        val started = System.nanoTime()
        val result = runtime.deliver(fact, sink, context) { _ =>
          Thread.sleep(500L)
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }
        val elapsedmillis = (System.nanoTime() - started) / 1000000L

        Then("delivery is limited by timeout and returns promptly")
        result.status shouldBe OperationEvaluationDeliveryStatus.Limited
        result.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.Timeout)
        elapsedmillis should be < 400L
      } finally {
        runtime.close()
      }
    }

    "reject an oversized fact before invoking its provider" in {
      Given("a byte budget smaller than the structural start fact")
      val runtime = new OperationEvaluationDeliveryRuntime(OperationEvaluationDeliveryPolicy(
        maximumItemBytes = 1,
        maximumQueuedBytes = 1
      ))
      try {
        val fact = _start_fact()
        val sink = _sink()
        var invoked = false

        When("the oversized fact reaches the delivery boundary")
        val result = runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          invoked = true
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }

        Then("overflow is explicit and provider code is not entered")
        result.status shouldBe OperationEvaluationDeliveryStatus.Limited
        result.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.Overflow)
        invoked shouldBe false
      } finally {
        runtime.close()
      }
    }

    "reject concurrent delivery when the aggregate byte budget is occupied" in {
      Given("one admitted stalled delivery that consumes the complete byte budget")
      val fact = _start_fact()
      val bytes = fact.toRecord.print.getBytes(StandardCharsets.UTF_8).length
      val runtime = new OperationEvaluationDeliveryRuntime(OperationEvaluationDeliveryPolicy(
        workerCount = 1,
        queueCapacity = 1,
        invocationTimeout = Duration.ofSeconds(1),
        maximumItemBytes = bytes,
        maximumQueuedBytes = bytes
      ))
      val release = new CountDownLatch(1)
      try {
        val sink = _sink()
        val entered = new CountDownLatch(1)
        val first = Future(runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          entered.countDown()
          release.await()
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        })
        entered.await()

        When("a second fact reaches the occupied aggregate budget")
        val second = runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }
        release.countDown()
        val firstresult = Await.result(first, 2.seconds)

        Then("the admitted call completes and the concurrent call is limited as saturated")
        firstresult.status shouldBe OperationEvaluationDeliveryStatus.Delivered
        second.status shouldBe OperationEvaluationDeliveryStatus.Limited
        second.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.Saturated)
      } finally {
        release.countDown()
        runtime.close()
      }
    }

    "release aggregate byte reservations when queued delivery is cancelled before execution" in {
      Given("one interrupt-resistant provider occupying the worker and one complete fact budget")
      val fact = _start_fact()
      val bytes = fact.toRecord.print.getBytes(StandardCharsets.UTF_8).length
      val runtime = new OperationEvaluationDeliveryRuntime(OperationEvaluationDeliveryPolicy(
        workerCount = 1,
        queueCapacity = 2,
        invocationTimeout = Duration.ofMillis(25),
        maximumItemBytes = bytes,
        maximumQueuedBytes = bytes
      ))
      val release = new AtomicBoolean(false)
      try {
        val sink = _sink()
        val first = runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          while (!release.get()) {
            try Thread.sleep(5L)
            catch {
              case _: InterruptedException => ()
            }
          }
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }
        first.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.Timeout)

        When("two queued deliveries time out before the worker can execute either one")
        val second = runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }
        val third = runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }

        Then("each cancelled queue reservation is released instead of saturating the next delivery")
        second.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.Timeout)
        third.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.Timeout)
        third.limitations.map(_.kind) should not contain OperationEvaluationLimitationKind.Saturated
      } finally {
        release.set(true)
        runtime.close()
      }
    }

    "convert structural serialization failure into an auxiliary delivery failure" in {
      Given("a structurally invalid fact whose serialization fails before provider submission")
      val runtime = new OperationEvaluationDeliveryRuntime()
      try {
        val start = _start_fact()
        val fact = new OperationEvaluationFact {
          val id = start.id
          val correlation = start.correlation
          val source = start.source
          val confidentiality = DataConfidentiality.Internal
          override val occurredAt = start.occurredAt
          override val factKind = OperationEvaluationFactKind.OperationStart
          def toRecord: Record = throw new IllegalStateException("planned serialization failure")
        }
        val sink = _sink()
        var invoked = false

        When("the fact reaches the delivery boundary")
        val result = runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          invoked = true
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }

        Then("the provider is not invoked and the failure stays auxiliary")
        result.status shouldBe OperationEvaluationDeliveryStatus.Failed
        result.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.ProviderFailure)
        invoked shouldBe false
      } finally {
        runtime.close()
      }
    }

    "contain interruption while awaiting an auxiliary provider" in {
      Given("a caller blocked at the finite provider wait boundary")
      val runtime = new OperationEvaluationDeliveryRuntime(OperationEvaluationDeliveryPolicy(
        workerCount = 1,
        queueCapacity = 1,
        invocationTimeout = Duration.ofSeconds(1)
      ))
      try {
        val fact = _start_fact()
        val sink = _sink()
        val entered = new CountDownLatch(1)
        val result = new AtomicReference[OperationEvaluationDeliveryResult]()
        val interrupted = new AtomicBoolean(false)
        val caller = new Thread(() => {
          val delivered = runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
            entered.countDown()
            Thread.sleep(500L)
            OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
          }
          result.set(delivered)
          interrupted.set(Thread.currentThread.isInterrupted)
        })
        caller.start()
        entered.await(1L, TimeUnit.SECONDS) shouldBe true

        When("the caller is interrupted while awaiting the auxiliary provider")
        caller.interrupt()
        caller.join(1000L)

        Then("delivery returns a failed auxiliary result and preserves the interrupt signal")
        caller.isAlive shouldBe false
        result.get().status shouldBe OperationEvaluationDeliveryStatus.Failed
        result.get().limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.ProviderFailure)
        interrupted.get() shouldBe true
      } finally {
        runtime.close()
      }
    }
    }

    "project auxiliary diagnostics" which {
    "project bounded delivery diagnostics without copying provider failure payloads" in {
      Given("a calltree-enabled delivery whose provider returns a confidential failure display")
      val runtime = new OperationEvaluationDeliveryRuntime()
      try {
        val fact = _start_fact()
        val sink = _sink()
        val secret = "private-provider-failure"
        val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
        val before = RuntimeDashboardMetrics.operationEvaluationDeliverySnapshot.summary.cumulative.total

        When("the provider failure crosses the auxiliary delivery boundary")
        val result = runtime.deliver(fact, sink, context) { _ =>
          Consequence.operationInvalid(secret)
        }

        Then("execution metadata, CallTree, and metrics retain only safe structural diagnostics")
        result.status shouldBe OperationEvaluationDeliveryStatus.Failed
        val report = context.runtime.executionMetadata.operationEvaluation.getOrElse(fail("delivery report missing"))
        report.deliveries should have length 1
        report.deliveries.head.status shouldBe OperationEvaluationDeliveryStatus.Failed
        report.deliveries.head.limitationKinds should contain (OperationEvaluationLimitationKind.ProviderFailure)
        val metadata = report.toRecord.print
        val calltree = context.observability.callTreeContext.build()
          .map(_.toRecord.print)
          .getOrElse(fail("delivery calltree missing"))
        val metrics = RuntimeDashboardMetrics
          .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
          .points
          .filter(_.scope == "operation-evaluation.delivery")
        metadata should not include secret
        metadata should not include fact.id.toString
        metadata should not include fact.correlation.executionId.toString
        calltree should include ("operation-evaluation:delivery")
        calltree should include ("delivery_status=failed")
        calltree should not include secret
        calltree should not include fact.id.toString
        calltree should not include fact.correlation.executionId.toString
        metrics should not be empty
        metrics.flatMap(_.labels.values).mkString(" ") should not include secret
        RuntimeDashboardMetrics.operationEvaluationDeliverySnapshot.summary.cumulative.total should be >= (before + 1)
      } finally {
        runtime.close()
      }
    }

    "keep provider delivery independent of CallTree collection" in {
      Given("equivalent enabled and disabled CallTree contexts")
      val runtime = new OperationEvaluationDeliveryRuntime()
      try {
        val fact = _start_fact()
        val sink = _sink()
        val enabled = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
        val disabled = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = false)
        val invocations = new AtomicReference(Vector.empty[String])
        def _deliver_(name: String, context: ExecutionContext): OperationEvaluationDeliveryResult =
          runtime.deliver(fact, sink, context) { _ =>
            invocations.updateAndGet(_ :+ name)
            OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
          }

        When("the same fact is delivered with telemetry enabled and disabled")
        val enabledresult = _deliver_("enabled", enabled)
        val disabledresult = _deliver_("disabled", disabled)

        Then("telemetry collection changes no provider invocation or delivery result")
        enabledresult.status shouldBe OperationEvaluationDeliveryStatus.Delivered
        disabledresult.status shouldBe OperationEvaluationDeliveryStatus.Delivered
        invocations.get() should contain theSameElementsAs Vector("enabled", "disabled")
        enabled.runtime.executionMetadata.operationEvaluation.map(_.deliveries.length) shouldBe Some(1)
        disabled.runtime.executionMetadata.operationEvaluation.map(_.deliveries.length) shouldBe Some(1)
        enabled.observability.callTreeContext.build() should not be empty
        disabled.observability.callTreeContext.build() shouldBe empty
      } finally {
        runtime.close()
      }
    }

    "retain every bounded limitation while rejecting untrusted diagnostic keys" in {
      Given("one provider result with multiple limitations and an unsafe diagnostic key")
      val runtime = new OperationEvaluationDeliveryRuntime()
      try {
        val fact = _start_fact("metric_multi_limit")
        val sink = _sink()
        val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
        val secret = "private-diagnostic-" + ("x" * 512)
        val safe = ConclusionDiagnostics.unknown.copy(diagnosticKey = "argument.limit")
        val unsafe = ConclusionDiagnostics.unknown.copy(diagnosticKey = secret)
        val metricoperation = fact.correlation.operation.print
        def _metric_count_(): Long =
          RuntimeDashboardMetrics
            .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
            .points
            .filter(_.scope == "operation-evaluation.delivery")
            .filter(_.labels.get("operation").contains(metricoperation))
            .map(_.count)
            .sum
        val before = _metric_count_()

        When("the result crosses the delivery observation boundary")
        val result = runtime.deliver(fact, sink, context) { _ =>
          OperationEvaluationDeliveryResult.createC(
            fact.id,
            sink,
            OperationEvaluationDeliveryStatus.Limited,
            Vector(
              OperationEvaluationLimitation(
                OperationEvaluationLimitationKind.Timeout,
                diagnostic = Some(safe)
              ),
              OperationEvaluationLimitation(
                OperationEvaluationLimitationKind.Saturated,
                diagnostic = Some(unsafe)
              )
            )
          )
        }

        Then("metadata, CallTree, and metrics retain all safe structural values exactly once")
        result.status shouldBe OperationEvaluationDeliveryStatus.Limited
        val report = context.runtime.executionMetadata.operationEvaluation.getOrElse(fail("delivery report missing"))
        report.deliveries.head.limitationKinds.map(_.token) should contain allOf ("timeout", "saturated")
        report.deliveries.head.diagnosticKeys.map(_.token) should contain allOf ("argument.limit", "unknown")
        report.toRecord.print should not include secret
        val calltree = context.observability.callTreeContext.build().map(_.toRecord.print)
          .getOrElse(fail("delivery calltree missing"))
        calltree should include ("timeout")
        calltree should include ("saturated")
        calltree should not include secret
        val metrics = RuntimeDashboardMetrics
          .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
          .points
          .filter(_.scope == "operation-evaluation.delivery")
          .filter(_.labels.get("operation").contains(metricoperation))
        metrics.exists { metric =>
          val limitations = metric.labels.getOrElse("limitation_kinds", "")
          val diagnostics = metric.labels.getOrElse("diagnostic_keys", "")
          limitations.contains("timeout") &&
          limitations.contains("saturated") &&
          diagnostics.contains("argument.limit")
        } shouldBe true
        metrics.flatMap(_.labels.values).mkString(" ") should not include secret
        _metric_count_() shouldBe before + 1
      } finally {
        runtime.close()
      }
    }

    "keep timed-out provider tracing detached from the caller CallTree stack" in {
      Given("an interrupt-resistant provider and a calltree-enabled caller")
      val runtime = new OperationEvaluationDeliveryRuntime(OperationEvaluationDeliveryPolicy(
        workerCount = 1,
        queueCapacity = 1,
        invocationTimeout = Duration.ofMillis(25)
      ))
      val release = new AtomicBoolean(false)
      val completed = new CountDownLatch(1)
      try {
        val fact = _start_fact()
        val sink = _sink()
        val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
        val workercalltreeenabled = new AtomicBoolean(true)

        When("the provider remains alive after the caller timeout")
        val result = runtime.deliver(fact, sink, context) { deliverycontext =>
          try {
            workercalltreeenabled.set(deliverycontext.observability.callTreeContext.isEnabled)
            while (!release.get()) {
              try Thread.sleep(5L)
              catch {
                case _: InterruptedException => ()
              }
            }
            OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
          } finally {
            completed.countDown()
          }
        }
        val beforerelease = context.observability.callTreeContext.build().map(_.toRecord.print)
          .getOrElse(fail("delivery calltree missing"))
        release.set(true)
        val providercompleted = completed.await(1L, TimeUnit.SECONDS)

        Then("the caller receives one completed timeout mark and the worker cannot mutate its stack")
        providercompleted shouldBe true
        val afterrelease = context.observability.callTreeContext.build().map(_.toRecord.print)
          .getOrElse(fail("delivery calltree missing"))
        result.limitations.map(_.kind) should contain (OperationEvaluationLimitationKind.Timeout)
        workercalltreeenabled.get() shouldBe false
        beforerelease should include ("operation-evaluation:delivery")
        beforerelease should include ("delivery_status=limited")
        afterrelease shouldBe beforerelease
      } finally {
        release.set(true)
        runtime.close()
      }
    }

    "keep provider-owned assignment state independent of telemetry sampling and failure" in {
      Given("equivalent providers observed by sampled-out and failing telemetry observers")
      val fact = _start_fact()
      val sink = _sink()
      val sampledout = new OperationEvaluationDeliveryRuntime(
        observer = OperationEvaluationDeliveryObserver.noop
      )
      val failing = new OperationEvaluationDeliveryRuntime(
        observer = new OperationEvaluationDeliveryObserver {
          def observe(
            diagnostic: OperationEvaluationDeliveryDiagnostic,
            elapsedmillis: Long,
            context: ExecutionContext
          ): Unit = throw new IllegalStateException("planned telemetry failure")
        }
      )
      val assignments = new AtomicReference(Vector.empty[String])
      def _deliver_(
        runtime: OperationEvaluationDeliveryRuntime,
        assignment: String
      ): OperationEvaluationDeliveryResult =
        runtime.deliver(fact, sink, ExecutionContext.create()) { _ =>
          assignments.updateAndGet(_ :+ assignment)
          OperationEvaluationDeliveryResult.createC(fact.id, sink, OperationEvaluationDeliveryStatus.Delivered)
        }

      try {
        When("delivery succeeds while telemetry is sampled out or fails")
        val sampledresult = _deliver_(sampledout, "sampled-out")
        val failedresult = _deliver_(failing, "observer-failed")

        Then("provider-owned assignments and canonical delivery results remain complete")
        sampledresult.status shouldBe OperationEvaluationDeliveryStatus.Delivered
        failedresult.status shouldBe OperationEvaluationDeliveryStatus.Delivered
        assignments.get() should contain theSameElementsAs Vector("sampled-out", "observer-failed")
      } finally {
        sampledout.close()
        failing.close()
      }
    }
    }
  }

  private def _start_fact(
    operationname: String = "operation"
  ): OperationEvaluationStartFact = {
    val instant = Instant.parse("2026-07-23T00:00:00Z")
    val context = ExecutionContext.create()
    val operation = _success(
      OperationEvaluationOperationIdentity.createC("component", "service", operationname)
    )
    val prepared = _success(ExecutionContext.prepareOperationEvaluation(context, operation))
    val attempted = _success(ExecutionContext.beginOperationEvaluationAttempt(prepared))
    OperationEvaluationStartFact.create(
      OperationEvaluationFactId.create("runtime-spec", instant, attempted.idGeneration),
      attempted.operationEvaluation.correlation.getOrElse(fail("correlation missing")),
      instant
    )
  }

  private def _sink(): OperationEvaluationSinkIdentity =
    _success(OperationEvaluationSinkIdentity.createC("corpus-evaluation-sink", "component", "provider"))

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(result.toString))
}
