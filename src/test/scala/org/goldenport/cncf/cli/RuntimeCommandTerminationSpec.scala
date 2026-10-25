package org.goldenport.cncf.cli

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeCommandTerminationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CLI command termination" should {
    "request stop once and settle only after command persistence and owned shutdown complete" in {
      Given("an invocation-scoped registered hook, a stop callback and persistence/shutdown observations")
      val hook = new AtomicReference[Thread]()
      val requested = new CountDownLatch(1)
      val stops = new AtomicInteger(0)
      val removed = new AtomicInteger(0)
      val persisted = new AtomicBoolean(false)
      val closed = new AtomicBoolean(false)
      val settled = new ConcurrentLinkedQueue[Boolean]()
      val events = new ConcurrentLinkedQueue[String]()
      val termination = RuntimeCommandTermination.create(2000L,
        () => { stops.incrementAndGet(); requested.countDown(); Consequence.unit },
        value => hook.set(value),
        _ => { removed.incrementAndGet(); throw new IllegalStateException("JVM shutdown already started") },
        state => { events.add(state); if (state == "settled") settled.add(persisted.get && closed.get); () })
      var before = Vector.empty[String]
      When("SIGTERM's registered hook runs while the command is still able to persist its response")
      val result = termination.run {
        hook.get.start()
        if (!requested.await(1000L, TimeUnit.MILLISECONDS)) throw new IllegalStateException("stop was not requested")
        before = events.iterator().asScala.toVector
        persisted.set(true)
        17
      } { closed.set(true) }
      hook.get.join(2000L)
      hook.get.run() // Duplicate hook delivery cannot repeat the stop request.
      Then("the response and normal shutdown survive cancellation and only their completion permits settled")
      result shouldBe 17
      before shouldBe Vector("runtime-started", "cancellation-requested")
      events.iterator().asScala.toVector shouldBe Vector("runtime-started", "cancellation-requested", "settled")
      settled.iterator().asScala.toVector shouldBe Vector(true)
      stops.get shouldBe 1
      removed.get shouldBe 1
      hook.get.isAlive shouldBe false
    }

    "retain uncertainty when runtime grace expires before command completion" in {
      Given("an invocation whose injected bounded wait observes no completed command")
      val hook = new AtomicReference[Thread]()
      val events = new ConcurrentLinkedQueue[String]()
      val waits = new ConcurrentLinkedQueue[Long]()
      val termination = RuntimeCommandTermination.create(31L, () => Consequence.unit,
        value => hook.set(value), _ => (), state => { events.add(state); () },
        (_, millis) => { waits.add(millis); false })
      When("the registered hook reaches its bounded wait before the command eventually returns")
      hook.get.run()
      val result = termination.run { "later persisted" } { () }
      Then("the hook reports uncertain once and later command completion cannot manufacture an earlier settlement")
      result shouldBe "later persisted"
      events.iterator().asScala.toVector shouldBe Vector("runtime-started", "cancellation-requested", "uncertain")
      waits.iterator().asScala.foreach { millis => millis should be >= 0L; millis should be <= 31L }
    }

    Vector("command", "shutdown").foreach { failure =>
      s"report uncertainty when $failure completion fails" in {
        Given("a registered hook and one failing completion boundary")
        val hook = new AtomicReference[Thread]()
        val requested = new CountDownLatch(1)
        val events = new ConcurrentLinkedQueue[String]()
        val shutdown = new AtomicInteger(0)
        val termination = RuntimeCommandTermination.create(2000L,
          () => { requested.countDown(); Consequence.unit }, value => hook.set(value), _ => (),
          state => { events.add(state); () })
        When("cancellation overlaps a command or owned shutdown exception")
        val result = Try(termination.run {
          hook.get.start()
          if (!requested.await(1000L, TimeUnit.MILLISECONDS)) throw new IllegalStateException("stop was not requested")
          if (failure == "command") throw new IllegalStateException("command persistence failed")
          0
        } {
          shutdown.incrementAndGet()
          if (failure == "shutdown") throw new IllegalStateException("owned shutdown failed")
        })
        hook.get.join(2000L)
        Then("the original exception remains visible, normal finally runs and settlement remains unconfirmed")
        result.isFailure shouldBe true
        shutdown.get shouldBe 1
        hook.get.isAlive shouldBe false
        events.iterator().asScala.toVector shouldBe Vector("runtime-started", "cancellation-requested", "uncertain")
      }
    }

    "preserve the primary command failure and suppress owned shutdown failure while reporting uncertainty" in {
      Given("a command and its owned shutdown that each fail with a distinct retained exception")
      val hook = new AtomicReference[Thread]()
      val requested = new CountDownLatch(1)
      val events = new ConcurrentLinkedQueue[String]()
      val commandfailure = new IllegalArgumentException("command failure")
      val shutdownfailure = new IllegalStateException("shutdown failure")
      val shutdowns = new AtomicInteger(0)
      val termination = RuntimeCommandTermination.create(2000L,
        () => { requested.countDown(); Consequence.unit }, value => hook.set(value), _ => (),
        state => { events.add(state); () })
      When("the command throws after cancellation and its normal shutdown also throws")
      val result = intercept[IllegalArgumentException] {
        termination.run {
          hook.get.start()
          if (!requested.await(1000L, TimeUnit.MILLISECONDS)) throw new IllegalStateException("stop was not requested")
          throw commandfailure
        } {
          shutdowns.incrementAndGet()
          throw shutdownfailure
        }
      }
      hook.get.join(2000L)
      Then("the original throwable identity survives with shutdown suppressed and no settled runtime observation")
      (result eq commandfailure) shouldBe true
      result.getSuppressed.toVector shouldBe Vector(shutdownfailure)
      shutdowns.get shouldBe 1
      hook.get.isAlive shouldBe false
      events.iterator().asScala.toVector shouldBe Vector("runtime-started", "cancellation-requested", "uncertain")
    }

    "retain uncertainty when stop cannot confirm termination even after persistence and shutdown complete" in {
      Given("a stop callback returning the existing structured unconfirmed-termination failure")
      val hook = new AtomicReference[Thread]()
      val requested = new CountDownLatch(1)
      val events = new ConcurrentLinkedQueue[String]()
      val termination = RuntimeCommandTermination.create(2000L,
        () => { requested.countDown(); Consequence.serviceUnavailable("termination unconfirmed") },
        value => hook.set(value), _ => (), state => { events.add(state); () })
      When("the main command persists and shuts down after the failed stop request")
      val result = termination.run {
        hook.get.start()
        if (!requested.await(1000L, TimeUnit.MILLISECONDS)) throw new IllegalStateException("stop was not requested")
        "incomplete receipt persisted"
      } { () }
      hook.get.join(2000L)
      Then("the persisted response remains available while runtime settlement remains uncertain")
      result shouldBe "incomplete receipt persisted"
      hook.get.isAlive shouldBe false
      events.iterator().asScala.toVector shouldBe Vector("runtime-started", "cancellation-requested", "uncertain")
    }

    "retain uncertainty when the final driver audit rejects an otherwise completed stop" in {
      Given("successful stop and completion callbacks with an in-flight or previously unconfirmed driver audit")
      val hook = new AtomicReference[Thread]()
      val requested = new CountDownLatch(1)
      val events = new ConcurrentLinkedQueue[String]()
      val audits = new AtomicInteger(0)
      val termination = RuntimeCommandTermination.create(2000L,
        () => { requested.countDown(); Consequence.unit }, value => hook.set(value), _ => (),
        state => { events.add(state); () },
        confirmTermination = () => { audits.incrementAndGet(); false })
      When("the command persists and owned shutdown completes before the final audit")
      val result = termination.run {
        hook.get.start()
        if (!requested.await(1000L, TimeUnit.MILLISECONDS)) throw new IllegalStateException("stop was not requested")
        "persisted"
      } { () }
      hook.get.join(2000L)
      Then("the final audit prevents a false settled observation despite the successful callbacks")
      result shouldBe "persisted"
      audits.get shouldBe 1
      hook.get.isAlive shouldBe false
      events.iterator().asScala.toVector shouldBe Vector("runtime-started", "cancellation-requested", "uncertain")
    }

    "isolate runtime observer failures from stop, persistence and normal shutdown" in {
      Given("a runtime event sink that throws on every report")
      val hook = new AtomicReference[Thread]()
      val requested = new CountDownLatch(1)
      val stops = new AtomicInteger(0)
      val closed = new AtomicBoolean(false)
      val termination = RuntimeCommandTermination.create(2000L,
        () => { stops.incrementAndGet(); requested.countDown(); Consequence.unit },
        value => hook.set(value), _ => (), _ => throw new IllegalStateException("observer failure"))
      When("the hook requests cancellation and the main command finishes normally")
      val result = termination.run {
        hook.get.start()
        if (!requested.await(1000L, TimeUnit.MILLISECONDS)) throw new IllegalStateException("stop was not requested")
        "persisted"
      } { closed.set(true) }
      hook.get.join(2000L)
      Then("observer exceptions change no result or cleanup effect")
      result shouldBe "persisted"
      stops.get shouldBe 1
      closed.get shouldBe true
      hook.get.isAlive shouldBe false
    }
  }
}
