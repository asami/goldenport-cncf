package org.goldenport.cncf.event

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.{CommitRecorder, TransactionContext}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventEngineLaneSemanticsSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "EventEngine lanes" should {
    "persist transactional events only after commit" in {
      Given("event engine with in-memory event store")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val tx = TransactionContext.create(ExecutionContext.create().transactionContext)
      val e1 = _TestEvent("tx-1")

      When("event is staged and prepared")
      engine.stage(Vector(e1))
      engine.prepare(tx)

      Then("event is not yet persisted")
      store.query(EventStore.Query()).toOption.getOrElse(Vector.empty).size shouldBe 0

      When("transaction commits")
      engine.commit(tx)

      Then("event is persisted in transactional lane")
      val records = store.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
      records.size shouldBe 1
      records.head.lane shouldBe EventLane.Transactional
    }

    "persist non-transactional events independently" in {
      Given("event engine with in-memory event store")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val e1 = _TestEvent("ntx-1")

      When("event is emitted through non-transactional lane")
      val emitted = engine.emit(Vector(e1))

      Then("event is persisted without transaction commit")
      emitted.toOption.getOrElse(Vector.empty).size shouldBe 1
      val records = store.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
      records.size shouldBe 1
      records.head.lane shouldBe EventLane.NonTransactional
    }
  }

  private final case class _TestEvent(name: String) extends DomainEvent

  private final class _InMemoryCommitRecorder extends CommitRecorder {
    private val _entries = scala.collection.mutable.ArrayBuffer.empty[String]
    def record(message: String): Unit = _entries += message
    def entries: Vector[String] = _entries.toVector
  }
}
