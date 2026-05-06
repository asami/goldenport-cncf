package org.goldenport.cncf.search

import org.goldenport.Consequence
import org.goldenport.cncf.directive.Query
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  6, 2026
 * @version May.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebSearchQueryPlannerSpec extends AnyWordSpec with Matchers {
  "WebSearchQueryPlanner" should {
    "map q to full-text Contains clauses over configured searchable fields" in {
      val planned = WebSearchQueryPlanner.plan(
        Record.data("q" -> "phase", "status" -> "published", "sort" -> "updatedAt", "direction" -> "desc", "limit" -> "10", "offset" -> "20", "includeTotal" -> "true"),
        SearchPlanningProfile(
          searchableFields = Vector("title", "summary"),
          filterFields = Vector("status"),
          sortableFields = Vector("updatedAt")
        )
      ).TAKE

      Query.whereOf(planned.query) shouldBe Query.And(Vector(
        Query.Eq("status", "published"),
        Query.Or(Vector(
          Query.Contains("title", "phase", caseInsensitive = true),
          Query.Contains("summary", "phase", caseInsensitive = true)
        ))
      ))
      planned.query.sort shouldBe Vector(Query.SortKey("updatedAt", Query.SortDirection.Desc))
      planned.query.limit shouldBe Some(10)
      planned.query.offset shouldBe Some(20)
      planned.query.includeTotal shouldBe true
    }

    "accept text as a compatibility alias for q" in {
      val planned = WebSearchQueryPlanner.plan(
        Record.data("text" -> "legacy"),
        SearchPlanningProfile(searchableFields = Vector("title"))
      ).TAKE

      Query.whereOf(planned.query) shouldBe Query.Contains("title", "legacy", caseInsensitive = true)
      planned.text shouldBe Some("legacy")
    }

    "fail deterministically when text search has no searchable fields" in {
      val result = WebSearchQueryPlanner.plan(
        Record.data("q" -> "phase"),
        SearchPlanningProfile()
      )

      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("no searchable fields")
        case other =>
          fail(s"expected failure: ${other}")
      }
    }

    "fail deterministically when semantic search is requested without backend capability" in {
      val result = WebSearchQueryPlanner.plan(
        Record.data("q" -> "phase", "searchMode" -> "semantic"),
        SearchPlanningProfile(searchableFields = Vector("title"))
      )

      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("semantic search is not configured")
        case other =>
          fail(s"expected failure: ${other}")
      }
    }

    "fail deterministically when an explicit search mode is unknown" in {
      val result = WebSearchQueryPlanner.plan(
        Record.data("q" -> "phase", "searchMode" -> "semantc"),
        SearchPlanningProfile(searchableFields = Vector("title"))
      )

      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("unknown search mode")
        case other =>
          fail(s"expected failure: ${other}")
      }
    }
  }
}
