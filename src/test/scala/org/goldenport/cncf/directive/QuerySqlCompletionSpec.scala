package org.goldenport.cncf.directive

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
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
}
