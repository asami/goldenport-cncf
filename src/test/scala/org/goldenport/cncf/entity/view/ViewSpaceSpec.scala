package org.goldenport.cncf.entity.view

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.directive.Query
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 17, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class ViewSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ViewSpace" should {
    "resolve default and named browser separately" in {
      Given("a view space with default and named browser")
      val viewspace = new ViewSpace
      val collectionid = EntityCollectionId("test", "1", "user")
      val targetid = EntityId("test", "u1", collectionid)
      val collection = new ViewCollection[String](
        new ViewBuilder[String] {
          def build(id: EntityId): Consequence[String] =
            Consequence.success(s"default-${id.minor}")
        }
      )
      val defaultbrowser = Browser.from(
        collection,
        _ => Consequence.success(Vector("default-search"))
      )
      val namedbrowser = new Browser[String] {
        def find(id: EntityId): Consequence[String] =
          Consequence.success(s"summary-${id.minor}")

        def query(q: Query[_]): Consequence[Vector[String]] =
          Consequence.success(Vector("summary-search"))
      }
      viewspace.register("user", collection, defaultbrowser)
      viewspace.register("user", "summary", namedbrowser)

      When("resolving default and named browser")
      val defaultresult = viewspace.browser[String]("user").find(targetid)
      val namedresult = viewspace.browser[String]("user", "summary").find(targetid)

      Then("both browsers return their own values")
      defaultresult shouldBe Consequence.success("default-u1")
      namedresult shouldBe Consequence.success("summary-u1")
    }

    "treat empty view name as default browser" in {
      val viewspace = new ViewSpace
      val collectionid = EntityCollectionId("test", "1", "user")
      val targetid = EntityId("test", "u2", collectionid)
      val collection = new ViewCollection[String](
        new ViewBuilder[String] {
          def build(id: EntityId): Consequence[String] =
            Consequence.success(s"default-${id.minor}")
        }
      )
      viewspace.register("user", collection)

      val fromempty = viewspace.browser[String]("user", "").find(targetid)
      val fromnone = viewspace.browserOption[String]("user", "missing")

      fromempty shouldBe Consequence.success("default-u2")
      fromnone shouldBe None
    }
  }
}
