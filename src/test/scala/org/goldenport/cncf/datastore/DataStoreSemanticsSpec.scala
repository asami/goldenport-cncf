package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 19, 2026
 * @version Apr. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class DataStoreSemanticsSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "DataStore" should {
    "treat load from a missing collection as an empty result" in {
      Given("an in-memory datastore without the target collection")
      val store = DataStore.inMemorySearchable()
      val collection = DataStore.CollectionId("missing_collection")
      val entry = DataStore.StringEntryId("missing_record")
      given ExecutionContext = ExecutionContext.create()

      When("loading a record from the missing collection")
      val loaded = store.load(collection, entry)

      Then("the datastore reports an empty result instead of a missing collection failure")
      loaded shouldBe Consequence.success(None)
    }

    "allow save to create the target collection before writing the record" in {
      Given("an in-memory datastore without the target collection")
      val store = DataStore.inMemorySearchable()
      val collection = DataStore.CollectionId("save_created_collection")
      val entry = DataStore.StringEntryId("record_1")
      val record = Record.dataAuto("id" -> "record_1", "name" -> "taro")
      given ExecutionContext = ExecutionContext.create()

      When("saving a record into the missing collection")
      val saved = store.save(collection, entry, record)

      Then("the save succeeds and the record is loadable from the same collection")
      saved shouldBe Consequence.unit
      store.load(collection, entry) shouldBe Consequence.success(Some(record))
    }
  }
}
