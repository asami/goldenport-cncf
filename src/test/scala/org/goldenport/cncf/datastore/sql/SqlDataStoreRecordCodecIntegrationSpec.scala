package org.goldenport.cncf.datastore.sql

import java.sql.{DriverManager, ResultSet}
import org.goldenport.Consequence
import org.goldenport.record.{Field, Record}
import org.goldenport.cncf.datastore.DataStore.CollectionId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class SqlDataStoreRecordCodecIntegrationSpec extends AnyWordSpec
    with Matchers with GivenWhenThen {
  private def _with_row[A](sql: String, parameters: Vector[String] = Vector.empty)(run: ResultSet => A): A = {
    val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    try {
      val statement = connection.prepareStatement(sql)
      try {
        parameters.zipWithIndex.foreach { case (value, index) => statement.setString(index + 1, value) }
        val result = statement.executeQuery()
        try {
          result.next() shouldBe true
          run(result)
        } finally result.close()
      } finally statement.close()
    } finally connection.close()
  }

  private def _collection(name: String): CollectionId =
    CollectionId.EntityStore(EntityCollectionId("cncf", "builtin", name))

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail("Expected a successful SQL codec read"))

  "The shared SQL Record codec" should {
    "preserve JSON-looking durable Job metadata under both dialect read policies" in {
      Given("bounded numeric generators for JSON-looking durable Job text")
      val property = Prop.forAll(Gen.choose(0, 100000)) { number =>
        Given("fixture-owned SQLite rows with Job metadata and ordinary JSON, interpreted by both shared codec policies")
        val canonical = s"{\"number\":$number}"
        val jobid = s"[$number]"
        val policies = Vector(false, true).map(value => new SqlDataStoreRecordCodec(SqlDataStore.Config(normalizeColumnNames = true), value))

        When("the same JDBC row is decoded in the durable Job collection")
        val records = policies.map { codec =>
          _with_row("SELECT ? AS canonical_json, ? AS job_id, ? AS payload", Vector(canonical, jobid, jobid)) { row =>
            _take(codec.recordFromResultSetC(_collection("durableJob"), row))
          }
        }

        Then("canonical JSON and Job ID remain native text while ordinary payload retains generic JSON decoding")
        records.foreach { record =>
          val values = record.fields.map(field => field.key -> field.value.single).toMap
          values("canonicalJson") shouldBe canonical
          values("jobId") shouldBe jobid
          values("payload") shouldBe Vector(number.toLong)
        }
        true
      }

      When("ScalaCheck executes the generated JDBC decoding scenarios")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

      Then("both codec policies preserve the metadata in at least one hundred cases without discards")
      withClue(checked.toString) { checked.passed shouldBe true }
      checked.succeeded should be >= 100
      checked.discarded shouldBe 0
    }

    "preserve quota canonical JSON without disabling ordinary JSON decoding" in {
      Given("all three durable Job quota collections with JSON-looking canonical text and ordinary JSON data")
      val collections = Vector("durableJobStorageQuota", "durableJobOwnedBlobStorageQuota", "durableJobLifetimeQuota")
      val codec = new SqlDataStoreRecordCodec(SqlDataStore.Config(normalizeColumnNames = true), true)
      val canonical = "{\"bytes\":42}"

      When("quota records pass through the shared codec")
      val records = collections.map { name =>
        _with_row("SELECT ? AS canonical_json, '[1,2]' AS payload", Vector(canonical)) { row =>
          _take(codec.recordFromResultSetC(_collection(name), row))
        }
      }

      Then("the schema-declared native text stays text and the unrelated payload becomes its decoded value")
      records.foreach { record =>
        val values = record.fields.map(field => field.key -> field.value.single).toMap
        values("canonicalJson") shouldBe canonical
        values("payload") shouldBe Vector(1L, 2L)
      }
    }

    "omit only SQL NULL while retaining empty values and PostgreSQL identity ordering" in {
      Given("a real JDBC row containing SQL NULL, empty text, empty JSON values, zero and out-of-order identity columns")
      val codec = new SqlDataStoreRecordCodec(SqlDataStore.Config(normalizeColumnNames = true), true)

      When("the shared codec applies the PostgreSQL read policy to that fixture-owned row")
      val record = _with_row("SELECT '' AS title, 1 AS cncf_revision, 'item-1' AS id, NULL AS absent, '{}' AS nested, '[]' AS items, 0 AS zero") { row =>
        _take(codec.recordFromResultSetC(CollectionId.Instance("codec_fixture"), row))
      }

      Then("NULL has no attribute, identity and revision lead, and every non-NULL empty or zero value remains present")
      record.fields.map(_.key) shouldBe Vector("id", "cncf_revision", "title", "nested", "items", "zero")
      val values = record.fields.map(field => field.key -> field.value.single).toMap
      values("title") shouldBe ""
      values("nested") shouldBe Record.empty
      values("items") shouldBe Vector.empty
      values("zero") shouldBe 0
    }

    "detect ambiguous PostgreSQL read keys before omitting SQL NULL" in {
      Given("two JDBC labels that normalize to the same key even though one physical value is SQL NULL")
      val codec = new SqlDataStoreRecordCodec(SqlDataStore.Config(normalizeColumnNames = true), true)

      When("the PostgreSQL read policy decodes the ambiguous fixture-owned row")
      val result = _with_row("SELECT NULL AS display_name, 'value' AS displayName") { row =>
        codec.recordFromResultSetC(CollectionId.Instance("codec_fixture"), row)
      }

      Then("the whole read fails as configurationInvalid instead of publishing a partial or collapsed Record")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.status shouldBe Consequence.configurationInvalid("ambiguous decoded Record keys").conclusion.status
        case _ => fail("Expected configurationInvalid for ambiguous decoded Record keys")
      }
    }

    "retain PostgreSQL write aliases for admission while preserving the legacy last-wins policy" in {
      Given("distinct logical fields whose normalized SQL column names collide")
      val record = Record(Vector(Field("displayName", Field.Value.Single("first")), Field("display_name", Field.Value.Single("second"))))
      val native = new SqlDataStoreRecordCodec(SqlDataStore.Config(normalizeColumnNames = true), true)
      val legacy = new SqlDataStoreRecordCodec(SqlDataStore.Config(normalizeColumnNames = true), false)

      When("the shared write codec builds the candidates consumed by each dialect's admission")
      val nativecolumns = native.recordColumns(record)
      val legacycolumns = legacy.recordColumns(record)

      Then("PostgreSQL receives both aliases to reject ambiguity and the legacy codec retains its existing last value")
      nativecolumns shouldBe Vector("display_name" -> "first", "display_name" -> "second")
      legacycolumns shouldBe Vector("display_name" -> "second")
    }
  }
}
