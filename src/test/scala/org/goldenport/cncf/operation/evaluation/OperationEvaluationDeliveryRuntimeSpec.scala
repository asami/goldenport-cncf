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
          val occurredAt = start.occurredAt
          val factKind = "broken-serialization"
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

  private def _start_fact(): OperationEvaluationStartFact = {
    val instant = Instant.parse("2026-07-23T00:00:00Z")
    val context = ExecutionContext.create()
    val operation = _success(OperationEvaluationOperationIdentity.createC("component", "service", "operation"))
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
