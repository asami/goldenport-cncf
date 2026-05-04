package org.goldenport.cncf.entity.view

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 17, 2026
 *  version Mar. 24, 2026
 *  version Apr. 10, 2026
 * @version May.  4, 2026
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
      val collectionid = EntityCollectionId("test", "a", "user")
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
      val collectionid = EntityCollectionId("test", "a", "user")
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

    "cache default view load results and small query results until invalidated" in {
      var buildCount = 0
      var queryCount = 0
      val viewspace = new ViewSpace
      val collectionid = EntityCollectionId("test", "a", "user")
      val targetid = EntityId("test", "u3", collectionid)
      val collection = new ViewCollection[String](
        new ViewBuilder[String] {
          def build(id: EntityId): Consequence[String] = {
            buildCount += 1
            Consequence.success(s"default-${id.minor}")
          }
        }
      )
      val browser = Browser.from(
        collection,
        _ => {
          queryCount += 1
          Consequence.success(Vector("default-search"))
        }
      )
      viewspace.register("user", collection, browser)

      viewspace.browser[String]("user").find(targetid)
      viewspace.browser[String]("user").find(targetid)
      viewspace.browser[String]("user").query(Query("any"))
      viewspace.browser[String]("user").query(Query("any"))

      buildCount shouldBe 1
      queryCount shouldBe 1

      viewspace.invalidateAll()
      viewspace.browser[String]("user").find(targetid)
      viewspace.browser[String]("user").query(Query("any"))

      buildCount shouldBe 2
      queryCount shouldBe 2
    }

    "cache paged query results by chunk for repeated list access" in {
      var queryCount = 0
      EntityAccessMetricsRegistry.shared.clear()
      val viewspace = new ViewSpace
      val collectionid = EntityCollectionId("test", "a", "user")
      val collection = new ViewCollection[String](
        new ViewBuilder[String] {
          def build(id: EntityId): Consequence[String] =
            Consequence.success(id.minor)
        },
        queryChunkSize = 3
      )
      val browser = Browser.from(
        collection,
        q => {
          queryCount += 1
          val offset = q.offset.getOrElse(0)
          val limit = q.limit.getOrElse(3)
          Consequence.success((offset until (offset + limit)).toVector.map(i => s"item-$i"))
        }
      )
      viewspace.register("user", collection, browser)

      val first = viewspace.browser[String]("user").query(Query.plan("tokyo", limit = Some(2), offset = Some(0)))
      val second = viewspace.browser[String]("user").query(Query.plan("tokyo", limit = Some(2), offset = Some(1)))
      val third = viewspace.browser[String]("user").query(Query.plan("tokyo", limit = Some(2), offset = Some(3)))

      first shouldBe Consequence.success(Vector("item-0", "item-1"))
      second shouldBe Consequence.success(Vector("item-1", "item-2"))
      third shouldBe Consequence.success(Vector("item-3", "item-4"))
      queryCount shouldBe 2
      val metrics = EntityAccessMetricsRegistry.shared.snapshot()
      metrics.find(_.name == "view.query.chunk.miss").map(_.count) shouldBe Some(2L)
      metrics.find(_.name == "view.query.chunk.hit").map(_.count) shouldBe Some(1L)
    }

    "avoid caching unbounded query results larger than the chunk size" in {
      var queryCount = 0
      EntityAccessMetricsRegistry.shared.clear()
      val viewspace = new ViewSpace
      val collectionid = EntityCollectionId("test", "a", "user")
      val collection = new ViewCollection[String](
        new ViewBuilder[String] {
          def build(id: EntityId): Consequence[String] =
            Consequence.success(id.minor)
        },
        queryChunkSize = 3
      )
      val browser = Browser.from(
        collection,
        _ => {
          queryCount += 1
          Consequence.success(Vector("item-0", "item-1", "item-2", "item-3"))
        }
      )
      viewspace.register("user", collection, browser)

      viewspace.browser[String]("user").query(Query("tokyo"))
      viewspace.browser[String]("user").query(Query("tokyo"))

      queryCount shouldBe 2
      val metrics = EntityAccessMetricsRegistry.shared.snapshot()
      metrics.find(_.name == "view.query.small.bypass").map(_.count) shouldBe Some(2L)
    }

    "bypass query cache when maxQueries is zero" in {
      var queryCount = 0
      EntityAccessMetricsRegistry.shared.clear()
      val viewspace = new ViewSpace
      val collection = new ViewCollection[String](
        new ViewBuilder[String] {
          def build(id: EntityId): Consequence[String] =
            Consequence.success(id.minor)
        },
        maxQueries = 0
      )
      val browser = Browser.from(
        collection,
        _ => {
          queryCount += 1
          Consequence.success(Vector(s"result-$queryCount"))
        }
      )
      viewspace.register("user", collection, browser)

      viewspace.browser[String]("user").query(Query("tokyo")) shouldBe Consequence.success(Vector("result-1"))
      viewspace.browser[String]("user").query(Query("tokyo")) shouldBe Consequence.success(Vector("result-2"))
      queryCount shouldBe 2
      val metrics = EntityAccessMetricsRegistry.shared.snapshot()
      metrics.find(_.name == "view.query.small.bypass").map(_.count) shouldBe Some(2L)
    }

    "read query controls from nested request records" in {
      val request = Record.create(Vector(
        "city" -> "Tokyo",
        "query" -> Record.create(Vector(
          "limit" -> 2,
          "offset" -> 1
        ))
      ))

      val resolved = Query.withControls(Query(Record.create(Vector("city" -> "Tokyo"))), request)

      resolved.limit shouldBe Some(2)
      resolved.offset shouldBe Some(1)
    }

    "drop nested query control container from record-based query conditions" in {
      val request = Record.create(Vector(
        "city" -> "Tokyo",
        "query" -> Record.create(Vector(
          "limit" -> 2,
          "offset" -> 1
        ))
      ))

      val resolved = Query.withControls(Query(request), request)

      resolved.limit shouldBe Some(2)
      resolved.offset shouldBe Some(1)
      Query.whereOf(resolved) shouldBe Query.Eq("city", "Tokyo")
    }

    "provide context-aware helper methods for application read-side code" in {
      given ExecutionContext = ExecutionContext.test()
      val viewspace = new ViewSpace
      val collectionid = EntityCollectionId("test", "a", "user")
      val targetid = EntityId("test", "u4", collectionid)
      val collection = new ViewCollection[String](
        new ContextualViewBuilder[String] {
          def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[String] =
            Consequence.success(s"${ctx.major}-${id.minor}")
        }
      )
      val browser = Browser.from(
        collection,
        new ContextualBrowserQuery[String] {
          def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[String]] =
            Consequence.success(Vector(s"${ctx.major}-query"))
        }
      )
      viewspace.register("user", collection, browser)

      viewspace.findWithContext[String]("user", targetid) shouldBe Consequence.success("sys-u4")
      viewspace.queryWithContext[String]("user", Query("active")) shouldBe Consequence.success(Vector("sys-query"))
    }
  }
}
