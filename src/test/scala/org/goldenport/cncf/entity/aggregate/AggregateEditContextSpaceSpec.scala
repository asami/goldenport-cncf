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
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateEditContextSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with AggregateSpaceSpecHelper {

  "AggregateEditContextSpace" should {
    "keep working aggregate changes local until save" in {
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val owner = AggregateEditOwner(Some("user-1"), Some("session-1"))
      var persisted: Option[String] = None

      val context = space.begin("sales_order", id, "v1", original, owner).toOption.get
      val updated = space.update[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate](context.contextId, owner) { current =>
        Consequence.success(current.copy(line = current.line.copy(sku = "edited-sku")))
      }.toOption.get

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
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)

      space.begin("sales_order", id, "v1", original, AggregateEditOwner(Some("user-1"))) shouldBe a[Consequence.Success[_]]
      space.begin("sales_order", id, "v1", original, AggregateEditOwner(Some("user-2"))) shouldBe a[Consequence.Failure[_]]
    }

    "allow only one owner to begin editing the same aggregate concurrently" in {
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val start = new CountDownLatch(1)
      val done = new CountDownLatch(16)
      val results = new ConcurrentLinkedQueue[Consequence[AggregateEditContext[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate]]]()

      (1 to 16).foreach { n =>
        val thread = new Thread(() => {
          start.await()
          results.add(space.begin("sales_order", id, "v1", original, AggregateEditOwner(Some(s"user-$n"))))
          done.countDown()
        })
        thread.start()
      }
      start.countDown()
      done.await(5, TimeUnit.SECONDS) shouldBe true

      results.asScala.count {
        case Consequence.Success(_) => true
        case _ => false
      } shouldBe 1
      space.size shouldBe 1
    }

    "allow different sessions for the same principal when scoped by session" in {
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)

      space.begin(
        "sales_order",
        id,
        "v1",
        original,
        AggregateEditOwner(Some("user-1"), Some("session-1")),
        AggregateEditLockScope.Session
      ) shouldBe a[Consequence.Success[_]]

      space.begin(
        "sales_order",
        id,
        "v1",
        original,
        AggregateEditOwner(Some("user-1"), Some("session-2")),
        AggregateEditLockScope.Session
      ) shouldBe a[Consequence.Failure[_]]
    }

    "expire stale contexts and release aggregate lease" in {
      var current = Instant.parse("2026-06-14T00:00:00Z")
      val space = new AggregateEditContextSpace(Duration.ofSeconds(10), () => current)
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val owner1 = AggregateEditOwner(Some("user-1"))
      val owner2 = AggregateEditOwner(Some("user-2"))

      val context = space.begin("sales_order", id, "v1", original, owner1).toOption.get
      current = current.plusSeconds(11)

      space.get[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate](context.contextId, owner1) shouldBe a[Consequence.Failure[_]]
      space.begin("sales_order", id, "v2", original, owner2) shouldBe a[Consequence.Success[_]]
    }

    "reject save when the base token changed" in {
      val space = new AggregateEditContextSpace()
      val id = sales_order_id()
      val original = expected_sales_order_aggregate(id)
      val owner = AggregateEditOwner(Some("user-1"))
      val context = space.begin("sales_order", id, "v1", original, owner, metadata = Record.data("channel" -> "cli")).toOption.get

      space.save[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate, String](context.contextId, Some("v2"), owner) { current =>
        Consequence.success(current.line.sku)
      } shouldBe a[Consequence.Failure[_]]

      space.get[org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate](context.contextId, owner) shouldBe a[Consequence.Success[_]]
    }
  }
}
