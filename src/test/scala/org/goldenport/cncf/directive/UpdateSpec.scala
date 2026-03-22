package org.goldenport.cncf.directive

import org.goldenport.model.datatype.EntityId
import org.goldenport.model.datatype.EntityCollectionId
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class UpdateSpec
  extends AnyWordSpec
  with Matchers {

  "Update" should {
    "support noop / set / setNull states" in {
      val noop = Update.noop[String]
      val set = Update.set("taro")
      val setnull = Update.setNull[String]

      noop.isNoop shouldBe true
      set.isSet shouldBe true
      setnull.isSetNull shouldBe true

      noop.fold("noop", v => s"set:$v", "null") shouldBe "noop"
      set.fold("noop", v => s"set:$v", "null") shouldBe "set:taro"
      setnull.fold("noop", v => s"set:$v", "null") shouldBe "null"
    }

    "allow cozy-style update directive object" in {
      val id = EntityId("test", "1", EntityCollectionId("test", "1", "person"))
      val directive = domain.update.Person(
        name = Update.set(Name("hanako")),
        age = Update.noop[Age]
      )

      directive.name.isSet shouldBe true
      directive.age.isNoop shouldBe true

      val byid = UpdateDirective.ById(id, directive)
      val byquery = UpdateDirective.ByQuery(Query("name = 'hanako'"), directive)
      byid.id shouldBe id
      byquery.patch shouldBe directive
      Update.hasChange(directive) shouldBe true
    }

    "convert patch record to datastore changes" in {
      val patch = Record.data(
        "name" -> Update.set(Name("hanako")),
        "age" -> Update.noop[Age],
        "nickname" -> Update.setNull[String]
      )

      val changes = Update.toChangesRecord(patch)

      changes.asMap.get("name") shouldBe Some(Name("hanako"))
      changes.asMap.contains("age") shouldBe false
      changes.asMap.get("nickname") shouldBe Some(null)
    }
  }
}

private final case class Name(value: String)
private final case class Age(value: Int)

private object domain {
  object update {
    final case class Person(
      name: Update[Name],
      age: Update[Age]
    ) extends Update.PatchShape
  }
}
