package org.goldenport.cncf.datastore

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.EntityStoreValueCodec
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 12, 2026
 *  version Apr. 26, 2026
 *  version May. 26, 2026
 *  version Jul. 15, 2026
 * @version Oct. 18, 2026
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
    "insert and replace one identity through the atomic save contract" in {
      Given("an empty sqlite datastore and one stable entry identity")
      val path = Files.createTempFile("cncf-sqlite-upsert", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("atomic_save")
      val entryid = DataStore.StringEntryId("stable-1")
      given ExecutionContext = ExecutionContext.create()

      When("save is called first for insertion and then for replacement")
      datastore.save(collection, entryid, Record.data("name" -> "before")) should be_success
      datastore.save(collection, entryid, Record.data("name" -> "after")) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("one row remains with the latest record")
      loaded should be_success
      loaded.toOption.flatten.flatMap(_.getString("name")) shouldBe Some("after")
    }

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

    "retain explicitly encoded single value objects as physical JSON text" in {
      Given("a caller explicitly encoding its owned address and selecting structured codecs independently of SQL storage")
      val path = Files.createTempFile("cncf-sqlite-value-object", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("value_object")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      val entryid = DataStore.StringEntryId("vo1")
      val objectfixture = """{"baselineReportId":null,"emptyObject":{},"emptyArray":[],"nested":{"value":null,"empty":{},"items":[]},"items":[null,{},[],{"value":null,"items":[],"number":7}],"matrix":[[null,{}],[]],"text":"東京・review","enabled":true,"count":7,"ratio":1.5,"precise":12345678901234567890.12345678901234567890}"""
      val record = Record.data(
        "id" -> "vo1",
        "name" -> "alice",
        "address" -> AddressValue("Tokyo", "100-0001").toRecord().toJsonString,
        "metadata" -> objectfixture,
        "emptyobject" -> "{}",
        "emptyarray" -> "[]",
        "plain" -> "plain text",
        "blank" -> " 	 ",
        "malformedobject" -> " {broken} ",
        "malformedarray" -> " [broken] ",
        "sqlnull" -> org.simplemodeling.model.directive.Update.SetNull
      )

      When("creating and loading the record")
      datastore.create(collection, entryid, record) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("SQL retains exact physical Strings and only the caller-selected codec reconstructs the address")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(r)) =>
          r.getString("name") shouldBe Some("alice")
          r.getAny("address") shouldBe Some("""{"city":"Tokyo","postal_code":"100-0001"}""")
          val address = EntityStoreValueCodec.semiStructuredData.decode(r.getAny("address").get)
            .toOption.getOrElse(fail("the explicitly selected address codec should decode"))
          address.getString("city") shouldBe Some("Tokyo")
          address.getString("postal_code") shouldBe Some("100-0001")

          And("JSON metadata preserves explicit null, empty structures, nested arrays and precise object numbers")
          r.getAny("metadata") shouldBe Some(objectfixture)
          val metadata = EntityStoreValueCodec.semiStructuredData.decode(r.getAny("metadata").get)
            .toOption.getOrElse(fail("the explicitly selected metadata codec should decode"))
          io.circe.parser.parse(metadata.toJsonString) shouldBe io.circe.parser.parse(objectfixture)
          metadata.getAny("count") shouldBe Some(BigDecimal(7))

          And("root empty JSON structures stay present, text fallbacks remain exact and SQL NULL is omitted")
          r.getAny("emptyobject") shouldBe Some("{}")
          r.getAny("emptyarray") shouldBe Some("[]")
          EntityStoreValueCodec.semiStructuredData.decode(r.getAny("emptyobject").get).toOption.map(_.fields) shouldBe Some(Vector.empty)
          EntityStoreValueCodec.json.decode(r.getAny("emptyarray").get).toOption.flatMap(_.asArray) shouldBe Some(Vector.empty)
          r.getAny("plain") shouldBe Some("plain text")
          r.getAny("blank") shouldBe Some(" 	 ")
          r.getAny("malformedobject") shouldBe Some(" {broken} ")
          r.getAny("malformedarray") shouldBe Some(" [broken] ")
          r.getAny("sqlnull") shouldBe None
          r.fields.exists(_.key == "sqlnull") shouldBe false
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "retain explicitly encoded repeated value objects as physical JSON array text" in {
      Given("a caller explicitly encoding its repeated owned lines and selecting their structured codec separately from SQL")
      val path = Files.createTempFile("cncf-sqlite-value-object-array", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("value_object_array")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      val entryid = DataStore.StringEntryId("order1")
      val arrayfixture = """[null,{},[],{"value":null,"empty":{},"items":[]},[null,{},[]],"東京",true,7,1.5]"""
      val record = Record.data(
        "id" -> "order1",
        "name" -> "order-a",
        "lines" -> Vector(
          LineValue("sku-1", 2),
          LineValue("sku-2", 1)
        ).map(_.toRecord().toJsonString).mkString("[", ",", "]"),
        "metadata" -> arrayfixture,
        "sqlnull" -> org.simplemodeling.model.directive.Update.SetNull
      )

      When("creating and loading the record")
      datastore.create(collection, entryid, record) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("SQL retains exact array text and the explicitly selected caller codec reconstructs the owned lines")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(r)) =>
          r.getString("name") shouldBe Some("order-a")
          r.getAny("lines") shouldBe Some("""[{"sku":"sku-1","quantity":2},{"sku":"sku-2","quantity":1}]""")
          val lines = EntityStoreValueCodec.array(EntityStoreValueCodec.semiStructuredData).decode(r.getAny("lines").get)
            .toOption.getOrElse(fail("the explicitly selected lines codec should decode"))
          lines.collect { case rec: Record => rec.getString("sku") } shouldBe Vector(Some("sku-1"), Some("sku-2"))
          lines.collect { case rec: Record => _int_value(rec, "quantity") } shouldBe Vector(Some(2), Some(1))

          And("JSON arrays preserve null, empty structures, nested arrays and scalar values while SQL NULL is omitted")
          r.getAny("metadata") shouldBe Some(arrayfixture)
          EntityStoreValueCodec.json.decode(r.getAny("metadata").get).toOption shouldBe
            io.circe.parser.parse(arrayfixture).toOption
          r.getAny("sqlnull") shouldBe None
          r.fields.exists(_.key == "sqlnull") shouldBe false
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "retain explicitly encoded repeated scalar arrays before caller-selected decoding" in {
      val path = Files.createTempFile("cncf-sqlite-scalar-array", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("scalar_array")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      Given("a caller explicitly encoding repeated string, numeric and boolean values as JSON array text")
      val entryid = DataStore.StringEntryId("array1")
      val record = Record.data(
        "id" -> "array1",
        "methods" -> io.circe.Json.fromValues(Vector("official_driver", "ai_web_tools", "museum_or_jp").map(io.circe.Json.fromString)).noSpaces,
        "priorities" -> io.circe.Json.fromValues(Vector(1, 2).map(io.circe.Json.fromInt)).noSpaces,
        "flags" -> io.circe.Json.fromValues(Vector(true, false).map(io.circe.Json.fromBoolean)).noSpaces
      )

      When("creating and loading the record")
      datastore.create(collection, entryid, record) should be_success
      val loaded = datastore.load(collection, entryid)

      Then("SQL preserves all three exact physical Strings and explicit caller decoding restores their scalar values")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(r)) =>
          r.getAny("methods") shouldBe Some("""["official_driver","ai_web_tools","museum_or_jp"]""")
          r.getAny("priorities") shouldBe Some("[1,2]")
          r.getAny("flags") shouldBe Some("[true,false]")
          EntityStoreValueCodec.array(EntityStoreValueCodec.string).decode(r.getAny("methods").get).toOption shouldBe
            Some(Vector("official_driver", "ai_web_tools", "museum_or_jp"))
          EntityStoreValueCodec.json.decode(r.getAny("priorities").get).toOption.flatMap(_.asArray)
            .map(_.map(_.asNumber.flatMap(_.toInt))) shouldBe Some(Vector(Some(1), Some(2)))
          EntityStoreValueCodec.json.decode(r.getAny("flags").get).toOption.flatMap(_.asArray)
            .map(_.map(_.asBoolean)) shouldBe Some(Vector(Some(true), Some(false)))
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

    "store physical snake columns and return canonical camel keys" in {
      Given("a SQLite provider with column-name normalization and one canonical camel-key record")
      val path = Files.createTempFile("cncf-sqlite-normalized", ".db").toString
      val datastore = SqlDataStore.sqlite(path, config = SqlDataStore.Config(normalizeColumnNames = true))
      val collection = DataStore.CollectionId("normalized")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      val entryid = DataStore.StringEntryId("u1")
      When("the canonical record is created and loaded through the normalized provider")
      datastore.create(
        collection,
        entryid,
        Record.data(
          "loginName" -> "alice",
          "passwordHash" -> "hashed"
        )
      ) should be_success

      val loaded = datastore.load(collection, entryid)

      Then("readback retains canonical camel keys and omits the physical snake aliases")
      loaded should be_success
      loaded match {
        case Consequence.Success(Some(record)) =>
          record.getString("loginName") shouldBe Some("alice")
          record.getString("passwordHash") shouldBe Some("hashed")
          record.getString("login_name") shouldBe None
          record.getString("password_hash") shouldBe None
        case other =>
          fail(s"unexpected result: $other")
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
      Given("three SQLite rows separating recipient equality from case-insensitive body matching")
      val path = Files.createTempFile("cncf-sqlite-query", ".db").toString
      val datastore = SqlDataStore.sqlite(path)
      val collection = DataStore.CollectionId("queryable")
      val ctx = ExecutionContext.create()
      given ExecutionContext = ctx

      datastore.create(collection, DataStore.StringEntryId("a1"), Record.data("id" -> "a1", "recipientName" -> "bob", "body" -> "Hello SQLite")) should be_success
      datastore.create(collection, DataStore.StringEntryId("b2"), Record.data("id" -> "b2", "recipientName" -> "bob", "body" -> "Other text")) should be_success
      datastore.create(collection, DataStore.StringEntryId("c3"), Record.data("id" -> "c3", "recipientName" -> "alice", "body" -> "Hello SQLite")) should be_success

      When("search combines recipient equality and case-insensitive body containment")
      val result = datastore.search(
        collection,
        QueryDirective(
          query = Query.Expr(EntityQuery.And(Vector(
            EntityQuery.Eq("recipientName", "bob"),
            EntityQuery.Contains("body", "sqlite", caseInsensitive = true)
          )))
        )
      )

      Then("the exact result contains only the row satisfying both query terms")
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
