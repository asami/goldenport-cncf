package org.goldenport.cncf.directive

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class QuerySqlCompletionSpec
  extends AnyWordSpec
  with Matchers {

  "Query.completeSql" should {
    "expand table aliases from entity-table pairs" in {
      val query = Query("dummy")
      val sql = query.completeSql(
        Vector(
          "person" -> "p",
          "order" -> "o"
        )
      ) { names =>
        s"select ${names.qualify("person", "name")} from person ${names.table("person")} join orders ${names.table("order")} on ${names.qualify("person", "id")} = ${names.qualify("order", "person_id")}"
      }

      sql shouldBe "select p.name from person p join orders o on p.id = o.person_id"
    }
  }

  "Query.fromRecord" should {
    "separate framework and query-prefixed paging controls from conditions" in {
      val query = Query.fromRecord(org.goldenport.record.Record.data(
        "name" -> "taro",
        "textus.format" -> "yaml",
        "subject.customer_id" -> "customer-a",
        "query.limit" -> "20",
        "query.offset" -> "5"
      ))

      query.limit shouldBe Some(20)
      query.offset shouldBe Some(5)
      Query.whereOf(query) shouldBe Query.Eq("name", "taro")
    }

    "separate nested framework and subject containers from conditions" in {
      val query = Query.fromRecord(org.goldenport.record.Record.data(
        "name" -> "taro",
        "textus" -> org.goldenport.record.Record.data("format" -> "yaml"),
        "subject" -> org.goldenport.record.Record.data("customer_id" -> "customer-a"),
        "query" -> org.goldenport.record.Record.data("limit" -> "20", "offset" -> "5")
      ))

      query.limit shouldBe Some(20)
      query.offset shouldBe Some(5)
      Query.whereOf(query) shouldBe Query.Eq("name", "taro")
    }

    "keep top-level limit and offset as ordinary condition fields" in {
      val query = Query.fromRecord(org.goldenport.record.Record.data(
        "limit" -> "daily",
        "offset" -> "tokyo"
      ))

      query.limit shouldBe None
      query.offset shouldBe None
      Query.whereOf(query) shouldBe Query.And(Vector(
        Query.Eq("limit", "daily"),
        Query.Eq("offset", "tokyo")
      ))
    }
  }
}
