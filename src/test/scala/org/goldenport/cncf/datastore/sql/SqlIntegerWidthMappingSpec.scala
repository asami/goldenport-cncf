package org.goldenport.cncf.datastore.sql

import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification of declared integer width at the SQL boundary.
 * PostgreSQL and MySQL assertions cover generated DDL, not live servers.
 *
 * @since   Oct. 19, 2026
 * @version Oct. 19, 2026
 */
final class SqlIntegerWidthMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Standard SQL integer storage" should {
    "retain Short Int and Long widths through Record encoding into create and add-column DDL" in {
      Given("physical integer records encoded by the shared SQL codec for all three dialects")
      val generator = for {
        short <- Gen.choose(Short.MinValue.toInt, Short.MaxValue.toInt)
        integer <- Gen.choose(Int.MinValue, Int.MaxValue)
        long <- Gen.choose(Long.MinValue, Long.MaxValue)
      } yield (short.toShort, integer, long)
      val property = Prop.forAll(generator) { case (short, integer, long) =>
        val record = Record(Vector(
          Field("small", Field.Value.Single(short)),
          Field("medium", Field.Value.Single(integer)),
          Field("large", Field.Value.Single(long))))
        val dialects = Vector[(SqlDialectDriver, Boolean, Vector[String])](
          (PostgreSqlDialectDriver, true, Vector("SMALLINT", "INTEGER", "BIGINT")),
          (MySqlDialectDriver, false, Vector("SMALLINT", "INTEGER", "BIGINT")),
          (SqliteDialectDriver, false, Vector.fill(3)("INTEGER")))
        When("each dialect creates a new table and adds each encoded physical column")
        dialects.foreach { case (dialect, postgresql, types) =>
          val codec = new SqlDataStoreRecordCodec(SqlDataStore.Config(), postgresql)
          val columns = codec.recordColumns(record)
          val created = dialect.createTableSql("integer_width", columns)
          columns.zip(types).foreach { case (column, datatype) =>
            val definition = s"${dialect.quoteIdentifier(column._1)} $datatype"
            created should include(definition)
            dialect.addColumnSql("integer_width", column) should include(definition)
          }
          created should not include "UNSIGNED"
        }
        true
      }
      When("ScalaCheck exercises at least one hundred signed integer records")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("Short uses signed SMALLINT on PostgreSQL and MySQL and INTEGER on SQLite without changing wider integer widths")
      withClue(checked.toString) { checked.passed shouldBe true }
      checked.succeeded should be >= 100
      checked.discarded shouldBe 0
    }
  }
}
