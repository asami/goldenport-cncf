package org.goldenport.cncf.component

import scala.collection.concurrent.TrieMap
import org.goldenport.Consequence
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryStoreSnapshotIsolationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "ComponentFactory store realm loader" should {
    "isolate lookup by snapshot instance" in {
      Given("two different snapshots with the same entity id and different payloads")
      val factory = new ComponentFactory()
      val cid = EntityCollectionId("test", "1", "sample")
      val id = EntityId("m", "1", cid)
      val left = SnapshotEntity(id, "left")
      val right = SnapshotEntity(id, "right")
      val leftsnapshot = TrieMap.empty[EntityId, Any]
      val rightsnapshot = TrieMap.empty[EntityId, Any]
      leftsnapshot.put(id, left)
      rightsnapshot.put(id, right)

      val realmleft = _create_store_realm(factory, "sample", leftsnapshot)
      val realmright = _create_store_realm(factory, "sample", rightsnapshot)

      When("resolving from each store realm")
      val leftresult = realmleft.resolve(id)
      val rightresult = realmright.resolve(id)

      Then("each realm resolves only from its own snapshot")
      leftresult shouldBe Consequence.success(left)
      rightresult shouldBe Consequence.success(right)
    }
  }

  private def _create_store_realm(
    factory: ComponentFactory,
    name: String,
    snapshot: TrieMap[EntityId, Any]
  ): org.goldenport.cncf.entity.runtime.EntityRealm[Any] = {
    val method = classOf[ComponentFactory].getDeclaredMethods
      .find(m =>
        m.getName == "_create_store_realm" &&
        m.getParameterCount == 2
      )
      .getOrElse(
        fail("private method _create_store_realm(name, snapshot) is not found")
      )
    method.setAccessible(true)
    method
      .invoke(factory, name, snapshot)
      .asInstanceOf[org.goldenport.cncf.entity.runtime.EntityRealm[Any]]
  }
}

private final case class SnapshotEntity(
  id: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord() = org.goldenport.record.Record.dataAuto(
    "id" -> id,
    "name" -> name
  )
}
