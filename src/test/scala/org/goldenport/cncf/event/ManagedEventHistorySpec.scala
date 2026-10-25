package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.datastore.{DataStore, OrderDirection, Query as StoreQuery, QueryLimit, QueryOrder, QueryProjection}
import org.goldenport.cncf.directive.Query as Predicate
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Pure bounded-page contract; not a substitute for managed provider evidence.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  */
final class ManagedEventHistorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _time = Instant.parse("2026-10-25T00:00:00Z")
  private val _collection = DataStore.CollectionId("history_events")
  private val _query = ManagedEventHistory.Query("sample.orders.v1", "one", Vector("recorded"), 2)
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected history rejection: $other")
  }
  private def _event(sequence: Long): EventRecord =
    EventRecord(EventId("sample", "event", Some(_time), Some(s"issued-$sequence")), "recorded", "fact",
      Map("arbitrary" -> sequence), Map("history.namespace" -> _query.correlationNamespace,
        "history.key" -> _query.correlationKey), _time, true,
      EventRecord.Status.Stored, EventLane.Transactional, sequence)
  private def _row(event: EventRecord, v2: Boolean = false): Record = {
    val create = if (v2) _take(EventRecordJsonV2.createSideEffectC(_collection, event))
      else _take(EventRecordJsonV1.createSideEffectC(_collection, event))
    Record(create.record.fields ++ Record.dataAuto("id" -> event.id.value).fields)
  }
  private def _replace(row: Record, field: String, value: Any): Record =
    Record(row.fields.filterNot(_.key == field) ++ Record.dataAuto(field -> value).fields)

  "Managed history page admission" should {
    "select exact generic scope before numeric ordering and a bounded look-ahead" in {
      Given("a typed exact pair, a finite event-name set and a default page size")
      val query = _query.copy(names = Vector("recorded", "observed"), limit = 20)
      When("the fixed provider directive is prepared")
      val directive = _take(ManagedEventHistory.directiveC(query))
      Then("the source predicate carries all filters and no offset or unbounded limit")
      directive.query shouldBe StoreQuery.Expr(Predicate.And(Vector(
        Predicate.Eq(EventHistoryCorrelation.namespaceField, query.correlationNamespace),
        Predicate.Eq(EventHistoryCorrelation.keyField, query.correlationKey),
        Predicate.Or(Vector(Predicate.Eq(EventHistoryCorrelation.nameField, "recorded"),
          Predicate.Eq(EventHistoryCorrelation.nameField, "observed"))),
        Predicate.Gt(EventHistoryCorrelation.sequenceField, 0L))))
      directive.order shouldBe QueryOrder.By(EventHistoryCorrelation.sequenceField, OrderDirection.Desc)
      directive.limit shouldBe QueryLimit.Limit(21)
      directive.offset shouldBe 0
      directive.projection shouldBe QueryProjection.Fields(Vector("id", "event_json",
        EventHistoryCorrelation.namespaceField, EventHistoryCorrelation.keyField,
        EventHistoryCorrelation.nameField, EventHistoryCorrelation.sequenceField))
      ManagedEventHistory.Query("sample", "key", Vector("recorded")).limit shouldBe 20
    }

    "retain exact Long ordering, mixed canonical versions and honest truncation" in {
      Given("three descending committed facts beyond JavaScript safe integer precision")
      val events = Vector(9007199254740995L, 9007199254740994L, 9007199254740993L).map(_event)
      val rows = events.zipWithIndex.map { case (event, index) => _row(event, index % 2 == 0) }
      When("the whole page including the extra row is validated")
      val snapshot = _take(ManagedEventHistory.acceptRowsC(_query, rows, _time))
      val exact = _take(ManagedEventHistory.acceptRowsC(_query, rows.take(2), _time))
      val empty = _take(ManagedEventHistory.acceptRowsC(_query, Vector.empty, _time))
      Then("the retained window is bounded without fabricated completeness or rounded sequence")
      snapshot.events shouldBe events.take(2)
      snapshot.truncated shouldBe true
      snapshot.observedAt shouldBe _time
      snapshot.coverage shouldBe "retained-correlated-window"
      exact.truncated shouldBe false
      empty.events shouldBe Vector.empty[EventRecord]
      empty.truncated shouldBe false
    }

    "reject corrupt, crossed, over-budget and unordered pages instead of repairing them" in {
      Given("valid canonical rows and independently tampered generic projections")
      val first = _row(_event(30))
      val second = _row(_event(20))
      val third = _row(_event(10))
      val invalid = Vector(
        Vector(second, first), Vector(first, first), Vector(first, second, third, _row(_event(5))),
        Vector(_replace(first, "event_history_namespace", "sample.shipments.v1")),
        Vector(_replace(first, "event_history_key", "other")),
        Vector(_replace(first, "event_history_name", "other")),
        Vector(_replace(first, "event_history_sequence", 31L)),
        Vector(_replace(first, "event_history_sequence", "30")),
        Vector(_replace(first, "id", "foreign")),
        Vector(_replace(first, "event_json", "cncf.event-record.v3:{}")),
        Vector(_replace(first, "event_json", "cncf.event-record.v1:{broken")),
        Vector(first, second, _replace(third, "event_history_key", "wrong-lookahead")),
        Vector(_row(_event(30).copy(attributes = Map("history.namespace" -> "foreign", "history.key" -> "one")))),
        Vector(_row(_event(30).copy(attributes = Map.empty))),
        Vector(Record(first.fields.filterNot(_.key == EventHistoryCorrelation.sequenceField))))
      When("each invalid source page reaches the admission boundary")
      val results = invalid.map(rows => ManagedEventHistory.acceptRowsC(_query, rows, _time).isSuccess)
      Then("no invalid row is dropped or sorted into a successful snapshot")
      results.foreach(_ shouldBe false)
    }

    "validate finite query bounds for generated limits and event-name sets" in {
      Given("valid names with arbitrary page-size requests")
      val property = Prop.forAll(Gen.choose(-10, 110), Gen.choose(0, 34)) { (limit, count) =>
        val query = _query.copy(limit = limit, names = Vector.tabulate(count)(index => s"name$index"))
        val expected = limit >= 1 && limit <= 100 && count >= 1 && count <= 32
        ManagedEventHistory.validateQueryC(query).isSuccess == expected &&
          ManagedEventHistory.directiveC(query).toOption.forall(_.limit == QueryLimit.Limit(limit + 1))
      }
      When("generated bounds and explicit malformed identity/name inputs are checked")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      val invalid = Vector(_query.copy(names = Vector("recorded", "recorded")),
        _query.copy(names = Vector("")), _query.copy(names = Vector(null)),
        _query.copy(names = Vector("bad\nname")), _query.copy(correlationKey = " "))
      Then("only bounded unique queries enter a provider")
      result.passed shouldBe true
      invalid.foreach(query => ManagedEventHistory.validateQueryC(query).isSuccess shouldBe false)
    }

    "check normal authority and lease validity before consulting any provider" in {
      Given("pure reader fixtures whose callbacks count attempted data reads")
      var reads = 0
      def _read_(query: ManagedEventHistory.Query): Consequence[Vector[Record]] = {
        reads += 1
        Consequence.success(Vector.empty)
      }
      val denied = new ManagedEventHistory(ExecutionContext.test(SecurityContext.Privilege.User),
        () => Consequence.unit, _read_)
      val expired = new ManagedEventHistory(ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager),
        () => Consequence.stateInvalid("Fixture lease has ended"), _read_)
      When("unauthorized and expired readers request the same otherwise valid page")
      val results = Vector(denied.readC(_query), expired.readC(_query))
      Then("the provider callback is never reached")
      results.foreach(_.isSuccess shouldBe false)
      reads shouldBe 0
    }

    "reject a lease ending during a read and retain arbitrary descending Long pages" in {
      Given("a reader fixture whose lease ends in its provider callback and generated descending pages")
      var live = true
      val reader = new ManagedEventHistory(ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager),
        () => if (live) Consequence.unit else Consequence.stateInvalid("Fixture lease ended during read"),
        _ => { live = false; Consequence.success(Vector(_row(_event(1L)))) })
      val property = Prop.forAll(Gen.choose(1L, Long.MaxValue - 101L), Gen.choose(1, 100)) { (base, limit) =>
        val query = _query.copy(limit = limit)
        val events = Vector.tabulate(limit + 1)(index => _event(base + limit - index))
        ManagedEventHistory.acceptRowsC(query, events.map(event => _row(event)), _time).toOption.exists(snapshot =>
          snapshot.events == events.take(limit) && snapshot.truncated)
      }
      When("the callback ends its lease and the independent pure page property is exercised")
      val expired = reader.readC(_query)
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)
      Then("no post-release snapshot succeeds and numeric pages preserve exact generated facts")
      expired.isSuccess shouldBe false
      result.passed shouldBe true
    }
  }
}
