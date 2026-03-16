package org.goldenport.cncf.entity.runtime

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class PartitionedMemoryRealmConcurrentPutEvictSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "PartitionedMemoryRealm.put" should {
    "hold realm update inside lock so concurrent put cannot pass while first put is in critical section" in {
      Given("maxPartitions=1 and an idOf hook that blocks in the second idOf call for entity A")
      val cid = EntityCollectionId("test", "1", "sample")
      val ida = EntityId("m", "a", cid)
      val idb = EntityId("m", "b", cid)
      val entercritical = new CountDownLatch(1)
      val releasecritical = new CountDownLatch(1)
      val callcount = TrieMap.empty[EntityId, AtomicInteger]

      val idof: BlockingEntity => EntityId = e => {
        val n = callcount.getOrElseUpdate(e.id, new AtomicInteger(0)).incrementAndGet()
        if (e.id == ida && n == 2) {
          entercritical.countDown()
          val ok = releasecritical.await(10, TimeUnit.SECONDS)
          if (!ok)
            throw new IllegalStateException("timeout while waiting releasecritical")
        }
        e.id
      }

      val strategy = new PartitionStrategy {
        def partitionKey(id: EntityId): String =
          id.minor
        def partitionsForRange(
          major: String,
          minor: String,
          from: java.time.Instant,
          to: java.time.Instant
        ): Vector[String] =
          Vector.empty
      }

      val realm = new PartitionedMemoryRealm[BlockingEntity](
        strategy = strategy,
        idOf = idof,
        maxPartitions = 1,
        maxEntitiesPerPartition = 8
      )

      val executor = Executors.newFixedThreadPool(2)
      given ExecutionContext = ExecutionContext.fromExecutor(executor)
      try {
        val adone = Promise[Unit]()
        val bdone = Promise[Unit]()
        val bstarted = new CountDownLatch(1)

        When("A put enters critical section and B put starts before A is released")
        val fa = Future {
          realm.put(BlockingEntity(ida))
          adone.success(())
        }
        val entered = entercritical.await(2, TimeUnit.SECONDS)
        entered shouldBe true

        val fb = Future {
          bstarted.countDown()
          realm.put(BlockingEntity(idb))
          bdone.success(())
        }
        val started = bstarted.await(2, TimeUnit.SECONDS)
        started shouldBe true
        Thread.sleep(150)

        Then("B should still be waiting before A critical section is released")
        bdone.future.isCompleted shouldBe false

        releasecritical.countDown()

        And("both puts complete after release")
        Await.result(fa, Duration(2, "seconds"))
        Await.result(fb, Duration(2, "seconds"))
        Await.result(adone.future, Duration(2, "seconds"))
        Await.result(bdone.future, Duration(2, "seconds"))
      } finally {
        executor.shutdownNow()
      }
    }
  }
}

private final case class BlockingEntity(
  id: EntityId
)
