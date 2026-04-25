package org.goldenport.cncf.datastore

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 12, 2026
 *  version Mar. 12, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
class SqliteDataStoreSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with TableDrivenPropertyChecks
  with ConsequenceMatchers {
  import SqliteDataStoreSpec._

  "Sqlite DataStore" should {
    "round-trip powertype db values through ValueReader" in {
      val path = Files.createTempFile("cncf-sqlite-powertype", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("powertype")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      Given("a record storing a powertype as its db value")
      val entryid = DataStore.StringEntryId("p1")
      val record = Record.data("id" -> "p1", "country" -> CountryCode.JP.dbValue.get)

      When("creating and loading the record")
      datastore.create(collection, entryid, record) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("the loaded numeric value can be decoded as the powertype")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(r)) =>
          r.getInt("country") shouldBe Some(81)
          val decoded = summon[ValueReader[CountryCode]].readC(r.getInt("country").orNull)
          decoded match {
            case Consequence.Success(v) => v shouldBe CountryCode.JP
            case other => fail(s"unexpected decode result: $other")
          }
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "preserve numeric columns as numbers" in {
      val path = Files.createTempFile("cncf-sqlite", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("numbers")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      Given("a record with integer and boolean-like numeric values")
      val entryid = DataStore.StringEntryId("n1")
      val record = Record.data("id" -> "n1", "state" -> 2, "priority" -> 10L)

      When("creating and loading the record")
      datastore.create(collection, entryid, record) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("numeric values remain numeric")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(r)) =>
          r.getInt("state") shouldBe Some(2)
          r.getInt("priority") shouldBe Some(10)
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "encode parent-owned single value objects as JSON text" in {
      val path = Files.createTempFile("cncf-sqlite-value-object", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("value_object")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      Given("a record with scalar columns and an owned address value object")
      val entryid = DataStore.StringEntryId("vo1")
      val record = Record.data(
        "id" -> "vo1",
        "name" -> "alice",
        "address" -> AddressValue("Tokyo", "100-0001")
      )

      When("creating and loading the record")
      datastore.create(collection, entryid, record) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("scalar fields remain columns and the value object is decoded as record-like data")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(r)) =>
          r.getString("name") shouldBe Some("alice")
          val address = r.getRecord("address").getOrElse(fail("address should be decoded as Record"))
          address.getString("city") shouldBe Some("Tokyo")
          address.getString("postal_code") shouldBe Some("100-0001")
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "encode parent-owned repeated value objects as JSON array text" in {
      val path = Files.createTempFile("cncf-sqlite-value-object-array", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("value_object_array")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      Given("a record with scalar columns and repeated owned line values")
      val entryid = DataStore.StringEntryId("order1")
      val record = Record.data(
        "id" -> "order1",
        "name" -> "order-a",
        "lines" -> Vector(
          LineValue("sku-1", 2),
          LineValue("sku-2", 1)
        )
      )

      When("creating and loading the record")
      datastore.create(collection, entryid, record) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("scalar fields remain columns and repeated values are decoded as record-like sequence data")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(r)) =>
          r.getString("name") shouldBe Some("order-a")
          val lines = r.getVector("lines").getOrElse(fail("lines should be decoded as Vector"))
          lines.collect { case rec: Record => rec.getString("sku") } shouldBe Vector(Some("sku-1"), Some("sku-2"))
          lines.collect { case rec: Record => _int_value(rec, "quantity") } shouldBe Vector(Some(2), Some(1))
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "store record fields as columns" in {
      pending
      val table = Table(
        ("id", "name", "state"),
        ("a1", "alpha", "s1"),
        ("b2", "bravo", "s2"),
        ("c3", "charlie", "s3")
      )

      val path = Files.createTempFile("cncf-sqlite", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("record")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      forAll(table) { (id, name, state) =>
        Given("a sqlite datastore with a record table")
        val entryid = DataStore.StringEntryId(id)
        val record = Record.data("id" -> id, "name" -> name, "state" -> state)

        When("creating and loading a record")
        datastore.create(collection, entryid, record) should be_success
        val loaded = datastore.load(collection, entryid)

        Then("the record fields are returned")
        loaded should be_success
        loaded match {
          case Consequence.Success(Some(r)) =>
            r.getString("name") shouldBe Some(name)
            r.getString("state") shouldBe Some(state)
          case other =>
            fail(s"unexpected result: $other")
        }

        When("updating a subset of fields")
        val changes = Record.data("state" -> "updated")
        datastore.update(collection, entryid, changes) should be_success
        val updated = datastore.load(collection, entryid)

        Then("the updated field is visible")
        updated should be_success
        updated match {
          case Consequence.Success(Some(r)) =>
            r.getString("state") shouldBe Some("updated")
          case other =>
            fail(s"unexpected result: $other")
        }

        When("deleting the record")
        datastore.delete(collection, entryid) should be_success
        val deleted = datastore.load(collection, entryid)

        Then("the record is removed")
        deleted should be_success
        deleted shouldBe Consequence.success(None)
      }
    }

    "search records with order, limit, and projection" in {
      val path = Files.createTempFile("cncf-sqlite-search", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("searchable")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      Given("a sqlite datastore with multiple records")
      datastore.create(
        collection,
        DataStore.StringEntryId("a1"),
        Record.data("id" -> "a1", "name" -> "alpha", "priority" -> 2)
      ) should be_success
      datastore.create(
        collection,
        DataStore.StringEntryId("b2"),
        Record.data("id" -> "b2", "name" -> "bravo", "priority" -> 1)
      ) should be_success
      datastore.create(
        collection,
        DataStore.StringEntryId("c3"),
        Record.data("id" -> "c3", "name" -> "charlie", "priority" -> 3)
      ) should be_success

      When("searching with projection, order, and limit")
      val result = datastore.search(
        collection,
        QueryDirective(
          query = Query.Empty,
          projection = QueryProjection.Fields(Vector("name")),
          order = QueryOrder.By("priority", OrderDirection.Asc),
          limit = QueryLimit.Limit(2)
        )
      )

      Then("the requested page is returned from sqlite")
      result should be_success
      result match {
        case Consequence.Success(SearchResult(records, ResultRange.Limited(2), None)) =>
          records.map(_.getString("id")) shouldBe Vector(Some("b2"), Some("a1"))
          records.map(_.getString("name")) shouldBe Vector(Some("bravo"), Some("alpha"))
          records.foreach(_.getString("priority") shouldBe None)
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "push down query expressions into sqlite search" in {
      val path = Files.createTempFile("cncf-sqlite-query", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("queryable")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      datastore.create(collection, DataStore.StringEntryId("a1"), Record.data("id" -> "a1", "recipientName" -> "bob", "body" -> "Hello SQLite")) should be_success
      datastore.create(collection, DataStore.StringEntryId("b2"), Record.data("id" -> "b2", "recipientName" -> "bob", "body" -> "Other text")) should be_success
      datastore.create(collection, DataStore.StringEntryId("c3"), Record.data("id" -> "c3", "recipientName" -> "alice", "body" -> "Hello SQLite")) should be_success

      val result = datastore.search(
        collection,
        QueryDirective(
          query = Query.Expr(EntityQuery.And(Vector(
            EntityQuery.Eq("recipientName", "bob"),
            EntityQuery.Contains("body", "sqlite", caseInsensitive = true)
          )))
        )
      )

      result should be_success
      result match {
        case Consequence.Success(SearchResult(records, ResultRange.Exact, None)) =>
          records.map(_.getString("id")) shouldBe Vector(Some("a1"))
        case other =>
          fail(s"unexpected result: $other")
      }
    }
  }

  private def _int_value(record: Record, key: String): Option[Int] =
    record.getAny(key).flatMap {
      case n: java.lang.Number => Some(n.intValue)
      case s: String => scala.util.Try(s.toDouble.toInt).toOption
      case other => scala.util.Try(other.toString.toDouble.toInt).toOption
    }
}

object SqliteDataStoreSpec {
  final case class AddressValue(
    city: String,
    postalCode: String
  ) extends RecordPresentable {
    def toRecord(): Record =
      Record.data(
        "city" -> city,
        "postal_code" -> postalCode
      )
  }

  final case class LineValue(
    sku: String,
    quantity: Int
  ) extends RecordPresentable {
    def toRecord(): Record =
      Record.data(
        "sku" -> sku,
        "quantity" -> quantity
      )
  }

  sealed abstract class CountryCode(
    val value: String,
    val dbValueValue: Int
  ) {
    def dbValue: Option[Int] = Some(dbValueValue)
  }

  object CountryCode {
    case object JP extends CountryCode("JP", 81)
    case object US extends CountryCode("US", 1)

    private val _by_name = Vector(JP, US).map(x => x.value -> x).toMap
    private val _by_db_value = Vector(JP, US).map(x => x.dbValueValue -> x).toMap

    def from(value: String): Option[CountryCode] =
      _by_name.get(value)

    def fromDbValue(value: Int): Option[CountryCode] =
      _by_db_value.get(value)

    def dbValueOf(value: String): Option[Int] =
      from(value).map(_.dbValueValue)

    def labelOf(value: String): Option[String] =
      from(value).map(_.value)

    given ValueReader[CountryCode] with {
      def readC(v: Any): Consequence[CountryCode] = v match {
        case n: Int =>
          fromDbValue(n).map(Consequence.success).getOrElse(Consequence.valueInvalid(v, org.goldenport.schema.XInt))
        case n: Long if n.isValidInt =>
          fromDbValue(n.toInt).map(Consequence.success).getOrElse(Consequence.valueInvalid(v, org.goldenport.schema.XInt))
        case s: String =>
          from(s).orElse(s.trim.toIntOption.flatMap(fromDbValue)).map(Consequence.success).getOrElse(Consequence.valueInvalid(v, org.goldenport.schema.XString))
        case _ =>
          Consequence.valueInvalid(v, org.goldenport.schema.XString)
      }
    }
  }
}
