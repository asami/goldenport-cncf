package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.provisional.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventStorePolicySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "EventStore policy entry points" should {
    "deny queryVisible and replayAuthorized for user privilege" in {
      Given("event store with one record and user execution context")
      val store = EventStore.inMemory
      val _ = store.append(
        Vector(
          EventRecord(
            id = EventId.generate(),
            name = "x",
            kind = "k",
            payload = Map.empty,
            attributes = Map.empty,
            createdAt = Instant.now(),
            persistent = true,
            status = EventRecord.Status.Stored,
            lane = EventLane.Transactional
          )
        )
      )
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)

      When("using policy-protected introspection and replay APIs")
      val q = store.queryVisible(EventStore.Query())
      val r = store.replayAuthorized(EventStore.Query())

      Then("both are denied with deterministic taxonomy")
      q shouldBe a[Consequence.Failure[_]]
      r shouldBe a[Consequence.Failure[_]]
      q match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Illegal
        case _ =>
          fail("expected failure")
      }
    }

    "allow queryVisible and replayAuthorized for content-manager privilege" in {
      Given("event store with one record and content-manager execution context")
      val store = EventStore.inMemory
      val appended = store.append(
        Vector(
          EventRecord(
            id = EventId.generate(),
            name = "x",
            kind = "k",
            payload = Map.empty,
            attributes = Map.empty,
            createdAt = Instant.now(),
            persistent = true,
            status = EventRecord.Status.Stored,
            lane = EventLane.Transactional
          )
        )
      )
      appended shouldBe a[Consequence.Success[_]]
      given ExecutionContext =
        ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("using policy-protected introspection and replay APIs")
      val q = store.queryVisible(EventStore.Query())
      val r = store.replayAuthorized(EventStore.Query())

      Then("both succeed")
      q shouldBe a[Consequence.Success[_]]
      r shouldBe a[Consequence.Success[_]]
      q.toOption.getOrElse(Vector.empty).nonEmpty shouldBe true
      r.toOption.getOrElse(Vector.empty).nonEmpty shouldBe true
    }
  }
}
