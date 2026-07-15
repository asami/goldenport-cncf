package org.goldenport.cncf.entity.aggregate

import java.time.{Duration, Instant}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 14, 2026
 *  version Jun. 18, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateEditContextSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with AggregateSpaceSpecHelper {

  "AggregateEditContextSpace" should {
    "keep working aggregate changes local until save" in {
      Given("an aggregate edit context owned by one session")
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val owner = AggregateEditOwner(Some("user-1"), Some("session-1"))
      var persisted: Option[String] = None

      When("the working aggregate changes before save")
      val context = space.begin("edit-1", "sales_order", id, "v1", original, owner).toOption.get
      val updated = space.update[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate](context.contextId, owner) { current =>
        Consequence.success(current.copy(line = current.line.copy(sku = "edited-sku")))
      }.toOption.get

      Then("the edit remains local until the save callback succeeds")
      updated.workingAggregate.line.sku shouldBe "edited-sku"
      persisted shouldBe None

      val saved = space.save[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate, String](context.contextId, Some("v1"), owner) { current =>
        persisted = Some(current.line.sku)
        Consequence.success(current.line.sku)
      }

      saved shouldBe Consequence.success("edited-sku")
      persisted shouldBe Some("edited-sku")
      space.get[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate](context.contextId, owner) shouldBe a[Consequence.Failure[_]]
    }

    "reject another principal editing the same aggregate" in {
      Given("an aggregate lease acquired by one principal")
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)

      When("another principal begins editing the same aggregate")
      val first = space.begin("edit-user-1", "sales_order", id, "v1", original, AggregateEditOwner(Some("user-1")))
      val second = space.begin("edit-user-2", "sales_order", id, "v1", original, AggregateEditOwner(Some("user-2")))

      Then("the first lease succeeds and the competing lease is rejected")
      first shouldBe a[Consequence.Success[_]]
      second shouldBe a[Consequence.Failure[_]]
    }

    "allow only one owner to begin editing the same aggregate concurrently" in {
      Given("concurrent principals waiting to acquire one aggregate lease")
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val start = new CountDownLatch(1)
      val done = new CountDownLatch(16)
      val results = new ConcurrentLinkedQueue[Consequence[AggregateEditContext[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate]]]()

      When("all principals attempt to begin at the same time")
      (1 to 16).foreach { n =>
        val thread = new Thread(() => {
          start.await()
          results.add(space.begin(s"edit-$n", "sales_order", id, "v1", original, AggregateEditOwner(Some(s"user-$n"))))
          done.countDown()
        })
        thread.start()
      }
      start.countDown()
      val completed = done.await(5, TimeUnit.SECONDS)

      Then("exactly one context owns the lease")
      completed shouldBe true
      results.asScala.count {
        case Consequence.Success(_) => true
        case _ => false
      } shouldBe 1
      space.size shouldBe 1
    }

    "allow different sessions for the same principal when scoped by session" in {
      Given("a session-scoped aggregate edit lease")
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)

      When("the same principal uses two different sessions")
      val first = space.begin(
        "edit-session-1",
        "sales_order",
        id,
        "v1",
        original,
        AggregateEditOwner(Some("user-1"), Some("session-1")),
        AggregateEditLockScope.Session
      )

      val second = space.begin(
        "edit-session-2",
        "sales_order",
        id,
        "v1",
        original,
        AggregateEditOwner(Some("user-1"), Some("session-2")),
        AggregateEditLockScope.Session
      )

      Then("only the first session owns the aggregate")
      first shouldBe a[Consequence.Success[_]]
      second shouldBe a[Consequence.Failure[_]]
    }

    "reject a duplicate caller-provided context ID across aggregate leases" in {
      Given("one aggregate edit context with a caller-provided opaque ID")
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val first = space.begin("shared-edit-id", "sales_order", id, "v1", original)

      When("another aggregate lease receives the same context ID")
      val duplicate = space.begin("shared-edit-id", "archived_sales_order", id, "v1", original)

      Then("the original context remains and the duplicate ID is rejected")
      first shouldBe a[Consequence.Success[_]]
      duplicate shouldBe a[Consequence.Failure[_]]
      space.size shouldBe 1
    }

    "expire stale contexts and release aggregate lease" in {
      Given("an aggregate edit context with a ten-second lease")
      var current = Instant.parse("2026-06-14T00:00:00Z")
      val space = new AggregateEditContextSpace(Duration.ofSeconds(10), () => current)
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val owner1 = AggregateEditOwner(Some("user-1"))
      val owner2 = AggregateEditOwner(Some("user-2"))

      val context = space.begin("edit-expiring", "sales_order", id, "v1", original, owner1).toOption.get

      When("time advances beyond the lease duration")
      current = current.plusSeconds(11)
      val expired = space.get[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate](context.contextId, owner1)
      val replacement = space.begin("edit-after-expiry", "sales_order", id, "v2", original, owner2)

      Then("the stale context is unavailable and another owner can acquire the lease")
      expired shouldBe a[Consequence.Failure[_]]
      replacement shouldBe a[Consequence.Success[_]]
    }

    "reject save when the base token changed" in {
      Given("an aggregate edit context based on version v1")
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val owner = AggregateEditOwner(Some("user-1"))
      val context = space.begin("edit-conflict", "sales_order", id, "v1", original, owner, metadata = Record.data("channel" -> "cli")).toOption.get

      When("save observes version v2")
      val result = space.save[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate, String](context.contextId, Some("v2"), owner) { current =>
        Consequence.success(current.line.sku)
      }

      Then("save fails and the working context remains available")
      result shouldBe a[Consequence.Failure[_]]
      space.get[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate](context.contextId, owner) shouldBe a[Consequence.Success[_]]
    }
  }
}
