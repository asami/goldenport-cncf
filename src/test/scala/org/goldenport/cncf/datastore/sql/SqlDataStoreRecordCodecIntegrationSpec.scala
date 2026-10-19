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
 * @version Oct. 19, 2026
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
    "refuse implicit structured writers without calling application conversion methods" in {
      Given("physical rows containing structured values without a declaration or explicit application conversion")
      var calls = 0
      val presentable = new org.goldenport.record.RecordPresentable {
        def toRecord(): Record = {
          calls += 1
          Record.dataAuto("private" -> "not-an-encoded-column")
        }
      }
      val values = Vector[Any](Record.dataAuto("nested" -> 1), io.circe.Json.obj(),
        Vector(1, 2), presentable, Double.NaN, Double.PositiveInfinity)
      val policies = Vector(false, true).map(value => new SqlDataStoreRecordCodec(SqlDataStore.Config(), value))

      When("both dialect policies receive these unsupported physical values")
      val results = policies.flatMap(codec => values.map(value =>
        intercept[org.goldenport.ConsequenceException](codec.columnValue(value)).consequence))

      Then("each write fails with a structured physical-value reason and application methods are never inferred")
      results.foreach {
        case Consequence.Failure(conclusion) =>
          conclusion.observation.cause.descriptor.facets should contain(
            org.goldenport.observation.Descriptor.Facet.Reason("unsupported-physical-value"))
        case other => fail(s"Unsupported physical value was accepted: $other")
      }
      calls shouldBe 0
    }

    "retain native NULL and exact decimal text under both dialect write policies" in {
      Given("native SQL NULL, literal null text and an exact decimal outside Double precision")
      val decimal = BigDecimal("12345678901234567890.123456789")
      val policies = Vector(false, true).map(value => new SqlDataStoreRecordCodec(SqlDataStore.Config(), value))

      When("the physical write codec transports each value")
      val results = policies.map(codec =>
        (codec.columnValue(null), codec.columnValue("null"), codec.columnValue(decimal)))

      Then("NULL stays NULL, String stays String and decimal transport retains every digit")
      results.foreach { case (absent, literal, exact) =>
        java.util.Objects.isNull(absent) shouldBe true
        literal shouldBe "null"
        exact shouldBe decimal.toString
      }
    }

    "preserve Short width and exact numeric text under both dialect write policies" in {
      Given("signed16 boundary and generated values, Int and Long values and exact arbitrary precision scalars")
      val property = Prop.forAll(Gen.choose(Short.MinValue.toInt, Short.MaxValue.toInt)) { generated =>
        val values = Vector(Short.MinValue, generated.toShort, 0.toShort, Short.MaxValue)
        val codecs = Vector(false, true).map(value => new SqlDataStoreRecordCodec(SqlDataStore.Config(), value))
        val exactinteger = BigInt("12345678901234567890123456789012345678901234567890")
        val exactdecimal = BigDecimal("123456789012345678901234567890.12345678901234567890")
        When("both shared SQL policies prepare these physical scalars for DDL and JDBC")
        codecs.foreach { codec =>
          values.foreach { value =>
            val stored = codec.columnValue(value)
            stored shouldBe value
            stored.isInstanceOf[Short] shouldBe true
          }
          codec.columnValue(Int.MaxValue).isInstanceOf[Int] shouldBe true
          codec.columnValue(Long.MaxValue).isInstanceOf[Long] shouldBe true
          codec.columnValue(exactinteger) shouldBe exactinteger.toString
          codec.columnValue(exactdecimal) shouldBe exactdecimal.toString
          codec.columnValue(exactinteger.bigInteger) shouldBe exactinteger.toString
          codec.columnValue(exactdecimal.bigDecimal) shouldBe exactdecimal.toString
        }
        true
      }
      When("ScalaCheck executes at least one hundred width-preservation scenarios")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("Short remains Short while existing Int Long and arbitrary precision transports retain their contracts")
      withClue(checked.toString) { checked.passed shouldBe true }
      checked.succeeded should be >= 100
      checked.discarded shouldBe 0
    }

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

        Then("canonical JSON and Job ID remain native text while ordinary payload is also physical text")
        records.foreach { record =>
          val values = record.fields.map(field => field.key -> field.value.single).toMap
          values("canonicalJson") shouldBe canonical
          values("jobId") shouldBe jobid
          values("payload") shouldBe jobid
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

    "preserve physical TEXT for quota and unrelated fields alike" in {
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

      Then("both canonical and ordinary TEXT remain exact Strings")
      records.foreach { record =>
        val values = record.fields.map(field => field.key -> field.value.single).toMap
        values("canonicalJson") shouldBe canonical
        values("payload") shouldBe "[1,2]"
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
      values("nested") shouldBe "{}"
      values("items") shouldBe "[]"
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

    "reject distinct write aliases while preserving repeated identical Record keys" in {
      Given("distinct logical aliases and an ordinary repeated identical Record key")
      val aliases = Record(Vector(Field("displayName", Field.Value.Single("first")), Field("display_name", Field.Value.Single("second"))))
      val repeated = Record(Vector(Field("displayName", Field.Value.Single("first")), Field("displayName", Field.Value.Single("second"))))
      val codecs = Vector(true, false).map(postgresql =>
        new SqlDataStoreRecordCodec(SqlDataStore.Config(normalizeColumnNames = true), postgresql))

      When("each dialect projects distinct aliases and same-key overrides")
      val failures = codecs.map(codec => intercept[org.goldenport.ConsequenceException](codec.recordColumns(aliases)).consequence)
      val columns = codecs.map(_.recordColumns(repeated))

      Then("distinct names cannot silently collapse while the effective same-key value remains last")
      failures.foreach(_ shouldBe a[Consequence.Failure[?]])
      columns.foreach(_ shouldBe Vector("display_name" -> "second"))
    }
  }
}
