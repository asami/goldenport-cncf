package org.goldenport.cncf.event

import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.{CommitRecorder, TransactionContext}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class EventEnginePrepareSemanticsSpec extends AnyWordSpec with Matchers {
  "EventEngine.prepare" should {
    "fix the event set for the transaction" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val tx = TransactionContext.create()

      val e1 = TestEvent("e1")
      val e2 = TestEvent("e2")
      val e3 = TestEvent("e3")

      eventEngine.stage(Seq(e1, e2))
      eventEngine.prepare(tx)
      eventEngine.stage(Seq(e3))
      eventEngine.commit(tx)

      eventEngine.preparedEvents shouldBe Vector(e1, e2)
      eventEngine.committedEvents shouldBe Vector(e1, e2)
    }

    "discard prepared events on abort" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val tx = TransactionContext.create()

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
      val tx = TransactionContext.create()

      eventEngine.prepare(tx)

      recorder.entries shouldBe Vector("EventEngine.prepare")
      recorder.entries.contains("DataStore.commit") shouldBe false
    }
  }

  private final case class TestEvent(
    name: String
  ) extends DomainEvent

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message

    def entries: Vector[String] =
      buffer.toVector
  }
}
