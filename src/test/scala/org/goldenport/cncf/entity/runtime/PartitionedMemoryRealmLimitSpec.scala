package org.goldenport.cncf.entity.runtime

import java.time.Instant
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class PartitionedMemoryRealmLimitSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _cid = EntityCollectionId("test", "a", "sample")

  "PartitionedMemoryRealm" should {
    "apply maxEntitiesPerPartition to each partition memory realm" in {
      Given("a partitioned realm with maxEntitiesPerPartition = 1")
      val strategy = new PartitionStrategy {
        def partitionKey(id: EntityId): String = "single"
        def partitionsForRange(
          major: String,
          minor: String,
          from: Instant,
          to: Instant
        ): Vector[String] = Vector("single")
      }
      val realm = new PartitionedMemoryRealm[TestEntityInPartition](
        strategy = strategy,
        idOf = _.id,
        maxPartitions = 2,
        maxEntitiesPerPartition = 1
      )
      val id1 = EntityId("m", "a", _cid)
      val id2 = EntityId("m", "b", _cid)

      When("putting two entities into the same partition")
      val e1 = TestEntityInPartition(id1)
      val e2 = TestEntityInPartition(id2)
      realm.put(e1)
      realm.put(e2)

      Then("the first one is evicted by per-partition LRU")
      realm.get(id1) shouldBe None
      realm.get(id2) shouldBe Some(e2)
    }
  }
}

private final case class TestEntityInPartition(
  id: EntityId
)
