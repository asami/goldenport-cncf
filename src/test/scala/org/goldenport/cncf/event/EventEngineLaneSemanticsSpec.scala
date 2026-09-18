package org.goldenport.cncf.event

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.statemachine.{CmlStateMachineIdentity, CmlStateMachineStateIdentity, CmlStateMachineStatePath, CmlStateMachineTransitionIdentity, CmlStateMachineTransitionTarget, CmlStateMachineTriggerIdentity, CmlStateMachineVersion, CmlTransitionBinding}
import org.goldenport.cncf.unitofwork.{CommitRecorder, TransactionContext}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 *  version Mar. 20, 2026
 * @version Sep. 18, 2026
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

    "preserve the issued committed-transition identity and expose only its allowed serialized fields" in {
      Given("a deterministic execution context, an explicitly bound transition, and an in-memory event store")
      val instant = Instant.parse("2026-09-18T10:00:00Z")
      val clock = Clock.fixed(instant, ZoneOffset.UTC)
      val ids = IdGenerationContext.deterministic(
        IdGenerationContext.IdNamespace("test", "committed_transition"),
        clock,
        "committed-transition-record"
      )
      given ExecutionContext = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
      val collection = EntityCollectionId("test", "sm", "person")
      val entityid = ids.entityId(collection, "committed-transition-entity")
      val tx = TransactionContext.create(summon[ExecutionContext].transactionContext, clock, ids)
      val event = CommittedTransition.create(entityid, _binding(collection), "update", tx.id)
      val independentEvent = CommittedTransition.create(entityid, _binding(collection), "update", tx.id)
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(), eventstore = store)

      When("the committed transition is emitted through the non-transactional lane and replayed")
      engine.emit(Vector(event), EventRecordFactory.from(summon[ExecutionContext])).isSuccess shouldBe true
      val record = store.query(EventStore.Query()).toOption.getOrElse(Vector.empty).head
      val replayed = store.replay(EventStore.Query()).toOption.getOrElse(Vector.empty).head

      Then("record and replay retain the issued occurrence identity and contain identity/correlation fields only")
      record.id shouldBe event.id
      replayed.id shouldBe event.id
      record.lane shouldBe EventLane.NonTransactional
      event.correlation.executionContextId.major shouldBe "test"
      event.correlation.executionContextId.minor shouldBe "committed_transition"
      event.correlation.executionContextId.timestamp shouldBe Some(instant)
      event.correlation.executionContextId.entropy should not be empty
      independentEvent.correlation.executionContextId.timestamp shouldBe Some(instant)
      independentEvent.correlation.executionContextId.entropy should not be empty
      independentEvent.correlation.executionContextId should not equal event.correlation.executionContextId
      record.payload.keySet shouldBe Set(
        "entity.id",
        "component.id",
        "entity.type",
        "machine.name",
        "machine.version",
        "transition.declarationOrder",
        "transition.source",
        "transition.target.kind",
        "transition.target",
        "transition.target.fallback",
        "transition.trigger",
        "operation.id",
        "transaction.id"
      )
      record.attributes.keySet shouldBe Set("executionContextId", "traceId", "spanId", "correlationId")
      record.payload.keySet should not contain "currentRecord"
      record.payload.keySet should not contain "proposedRecord"
      record.payload.keySet should not contain "patch"
      record.payload.keySet should not contain "payload"
    }

    "issue distinct deterministic transaction identities from controlled clock and entropy" in {
      Given("one fixed clock and deterministic IdGenerationContext")
      val instant = Instant.parse("2026-09-18T11:00:00Z")
      val clock = Clock.fixed(instant, ZoneOffset.UTC)
      val ids = IdGenerationContext.deterministic(
        IdGenerationContext.IdNamespace("test", "transaction"),
        clock,
        "transaction-id"
      )
      val context = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)

      When("two TransactionContexts are issued from that context")
      val first = TransactionContext.create(context.transactionContext, clock, ids).id
      val second = TransactionContext.create(context.transactionContext, clock, ids).id

      Then("their identity entropy differs while their deterministic timestamp and namespace remain controlled")
      first should not equal second
      first.major shouldBe "test"
      first.minor shouldBe "transaction"
      first.timestamp shouldBe Some(instant)
      second.timestamp shouldBe Some(instant)
      first.entropy should not equal second.entropy
    }
  }

  private def _binding(
    collection: EntityCollectionId
  ): CmlTransitionBinding = {
    val machine = CmlStateMachineIdentity("person-lifecycle")
    val source = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Draft")))
    val target = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Approved")))
    CmlTransitionBinding(
      componentId = ComponentId("org.example.Person"),
      entityType = collection,
      machine = machine,
      version = CmlStateMachineVersion(1),
      transition = CmlStateMachineTransitionIdentity(machine, 0),
      source = source,
      target = CmlStateMachineTransitionTarget.State(target),
      trigger = CmlStateMachineTriggerIdentity(machine, "approve")
    )
  }

  private final case class _TestEvent(name: String) extends DomainEvent

  private final class _InMemoryCommitRecorder extends CommitRecorder {
    private val _entries = scala.collection.mutable.ArrayBuffer.empty[String]
    def record(message: String): Unit = _entries += message
    def entries: Vector[String] = _entries.toVector
  }
}
