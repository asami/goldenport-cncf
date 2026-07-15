package org.goldenport.cncf.event

import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{CommitRecorder, TransactionContext}
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  6, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class EventEnginePrepareSemanticsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "EventEngine.prepare" should {
    "fix the event set for the transaction" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val tx = TransactionContext.create(ExecutionContext.create().transactionContext)

      val e1 = TestEvent("e1")
      val e2 = TestEvent("e2")
      val e3 = TestEvent("e3")

      eventEngine.stage(Seq(e1, e2))
      eventEngine.prepare(tx)
      eventEngine.stage(Seq(e3))
      eventEngine.commit(tx)

      eventEngine.preparedEvents shouldBe Vector.empty
      eventEngine.committedEvents shouldBe Vector(e1, e2)
    }

    "accept a new staged event set after commit" in {
      Given("an event engine and two sequential transactions")
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val ctx = ExecutionContext.create().transactionContext
      val first = TransactionContext.create(ctx)
      val second = TransactionContext.create(ctx)
      val e1 = TestEvent("e1")
      val e2 = TestEvent("e2")

      When("each transaction stages, prepares, and commits its own event")
      eventEngine.stage(Seq(e1))
      eventEngine.prepare(first)
      eventEngine.commit(first)
      eventEngine.stage(Seq(e2))
      eventEngine.prepare(second)
      eventEngine.commit(second)

      Then("prepared state is cleared and both events remain in the event store")
      eventEngine.preparedEvents shouldBe Vector.empty
      eventEngine.committedEvents shouldBe Vector(e2)
      eventEngine.eventStore.query(EventStore.Query()).toOption.get should have size 2
    }

    "discard prepared events on abort" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val tx = TransactionContext.create(ExecutionContext.create().transactionContext)

      val e1 = TestEvent("e1")
      eventEngine.stage(Seq(e1))
      eventEngine.prepare(tx)
      eventEngine.abort(tx)

      eventEngine.preparedEvents shouldBe Vector.empty
      eventEngine.committedEvents shouldBe Vector.empty
    }

    "perform no side effects in prepare" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val tx = TransactionContext.create(ExecutionContext.create().transactionContext)

      eventEngine.prepare(tx)

      recorder.entries shouldBe Vector("EventEngine.prepare")
      recorder.entries.contains("DataStore.commit") shouldBe false
    }
  }

  private final case class TestEvent(
    name: String
  ) extends DomainEvent

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val _buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      _buffer += message

    def entries: Vector[String] =
      _buffer.toVector
  }
}
