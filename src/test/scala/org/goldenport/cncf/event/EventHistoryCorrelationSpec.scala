package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Generic producers share metadata without sharing payload semantics.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  */
final class EventHistoryCorrelationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _time = Instant.parse("2026-10-25T00:00:00Z")
  private val _collection = DataStore.CollectionId("history_events")
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected correlation rejection: $other")
  }
  private def _event(namespace: String, key: String, payload: Map[String, Any]): EventRecord =
    EventRecord(EventId("sample", "event", Some(_time), Some("issued")), "recorded", "fact",
      payload, Map("history.namespace" -> namespace, "history.key" -> key), _time, true,
      EventRecord.Status.Stored, EventLane.Transactional, 9007199254740993L)

  "Generic event correlation" should {
    "project two unrelated producers without changing either canonical encoding" in {
      Given("unrelated namespace values and payload algebras")
      val events = Vector(
        _event("sample.orders.v1", "order-1", Map("amount" -> BigDecimal("12.50"))),
        _event("sample.shipments.v1", "parcel-1", Map("route" -> Vector("Tokyo", "Osaka"))))
      When("the existing V1 and V2 Create helpers prepare the same events")
      val rows = events.map(event => (
        _take(EventRecordJsonV1.createSideEffectC(_collection, event)).record,
        _take(EventRecordJsonV2.createSideEffectC(_collection, event)).record))
      Then("only generic same-row fields are added and canonical bytes round-trip unchanged")
      events.zip(rows).foreach { case (event, (v1, v2)) =>
        v1.getString("event_json") shouldBe Some(EventRecordJsonV1.encodeStorage(event))
        v2.getString("event_json") shouldBe Some(EventRecordJsonV2.encodeStorage(event))
        _take(EventRecordJsonV1.decodeStorageC(v1.getString("event_json").get)) shouldBe event
        _take(EventRecordJsonV2.decodeStorageC(v2.getString("event_json").get)) shouldBe event
        Vector(v1, v2).foreach { row =>
          row.getString(EventHistoryCorrelation.namespaceField) shouldBe event.attributes.get("history.namespace")
          row.getString(EventHistoryCorrelation.keyField) shouldBe event.attributes.get("history.key")
          row.fields.find(_.key == EventHistoryCorrelation.sequenceField).get.value.single shouldBe event.sequence
          row.fields.size shouldBe 5
        }
      }
    }

    "keep legacy rows unchanged and reject malformed or one-sided association" in {
      Given("events with no correlation or malformed attribute pairs")
      val original = _event("sample.orders.v1", "one", Map.empty)
      val legacy = original.copy(attributes = Map("traceId" -> "original"))
      val invalid = Vector(Map("history.namespace" -> "one"), Map("history.key" -> "one"),
        Map("history.namespace" -> " one", "history.key" -> "key"),
        Map("history.namespace" -> "one", "history.key" -> "key\n"),
        Map("history.namespace" -> "one", "history.key" -> ""),
        Map("history.namespace" -> "one", "history.key" -> "\u2003"),
        Map("history.namespace" -> "one", "history.key" -> null),
        Map("history.namespace" -> ("あ" * 43), "history.key" -> "key"),
        Map("history.namespace" -> "one", "history.key" -> ("あ" * 86)),
        Map("history.namespace" -> "one", "history.key" -> "\uD800"))
      When("both existing Create paths prepare legacy and invalid inputs")
      val legacyrows = Vector(_take(EventRecordJsonV1.createSideEffectC(_collection, legacy)).record,
        _take(EventRecordJsonV2.createSideEffectC(_collection, legacy)).record)
      val rejected = invalid.flatMap(attributes => Vector(
        EventRecordJsonV1.createSideEffectC(_collection, original.copy(attributes = attributes)).isSuccess,
        EventRecordJsonV2.createSideEffectC(_collection, original.copy(attributes = attributes)).isSuccess))
      Then("legacy row shape stays canonical-only and invalid association is never persisted")
      legacyrows.foreach(_.fields.map(_.key).toVector shouldBe Vector("event_json"))
      rejected.foreach(_ shouldBe false)
      EventHistoryCorrelation.projectC(original.copy(sequence = 0L)).isSuccess shouldBe false
      EventHistoryCorrelation.projectC(original.copy(persistent = false)).isSuccess shouldBe false
    }

    "preserve arbitrary opaque correlations and arbitrary supported producer payloads" in {
      Given("bounded nonempty ASCII associations and varying typed payloads")
      val opaque = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(40).mkString)
      val property = Prop.forAll(opaque, opaque, Gen.choose(1L, Long.MaxValue)) { (namespace, key, number) =>
        val event = _event(namespace, key, Map("uninterpreted" -> Vector(number, key)))
        val result = for {
          correlation <- EventHistoryCorrelation.fromAttributesC(event.attributes)
          create <- EventRecordJsonV2.createSideEffectC(_collection, event)
          decoded <- EventRecordJsonV2.decodeStorageC(create.record.getString("event_json").get)
        } yield correlation.contains(EventHistoryCorrelation(namespace, key)) && decoded == event
        result.toOption.contains(true)
      }
      When("the generic path is exercised over generated producer values")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), property)
      Then("no namespace or payload-specific implementation is required")
      result.passed shouldBe true
    }
  }
}
