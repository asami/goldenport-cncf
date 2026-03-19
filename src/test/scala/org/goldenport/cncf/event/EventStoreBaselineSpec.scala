package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventStoreBaselineSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "EventStore baseline" should {
    "append/load/query/replay with deterministic ordering" in {
      Given("an in-memory event store")
      val store = EventStore.inMemory
      val now = Instant.now()
      val r1 = EventRecord(
        id = EventId.generate(),
        name = "person.transition",
        kind = "before-transition",
        payload = Map("k" -> "v1"),
        attributes = Map("a" -> "1"),
        createdAt = now.minusSeconds(10),
        persistent = true,
        status = EventRecord.Status.Stored,
        lane = EventLane.Transactional
      )
      val r2 = EventRecord(
        id = EventId.generate(),
        name = "person.transition",
        kind = "after-transition",
        payload = Map("k" -> "v2"),
        attributes = Map("a" -> "2"),
        createdAt = now.minusSeconds(5),
        persistent = true,
        status = EventRecord.Status.Stored,
        lane = EventLane.Transactional
      )

      When("records are appended")
      val appended = store.append(Vector(r1, r2))

      Then("append returns sequenced records")
      appended shouldBe a[Consequence.Success[_]]
      val seq = appended.toOption.getOrElse(Vector.empty).map(_.sequence)
      seq shouldBe Vector(1L, 2L)

      And("load resolves by id")
      val loaded = store.load(r1.id)
      loaded shouldBe a[Consequence.Success[_]]
      loaded.toOption.flatten.map(_.id) shouldBe Some(r1.id)

      And("query respects deterministic order")
      val queried = store.query(EventStore.Query(name = Some("person.transition")))
      queried shouldBe a[Consequence.Success[_]]
      queried.toOption.getOrElse(Vector.empty).map(_.sequence) shouldBe Vector(1L, 2L)

      And("replay uses the same deterministic order")
      val replayed = store.replay(EventStore.Query(name = Some("person.transition")))
      replayed shouldBe queried
    }
  }
}
