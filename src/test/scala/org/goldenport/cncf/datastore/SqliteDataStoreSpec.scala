package org.goldenport.cncf.datastore

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.Record
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 12, 2026
 * @version Mar. 12, 2026
 * @author  ASAMI, Tomoharu
 */
class SqliteDataStoreSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with TableDrivenPropertyChecks
  with ConsequenceMatchers {

  "Sqlite DataStore" should {
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
  }
}
