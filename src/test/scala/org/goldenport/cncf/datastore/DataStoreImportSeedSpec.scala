package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 27, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class DataStoreImportSeedSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "DataStoreSpace.importSeed" should {
    "import records into the target collections in order" in {
      Given("an in-memory datastore space and a seed with multiple records")
      val space = DataStoreSpace.default()
      val cid = DataStore.CollectionId("seed_people")
      val seed = DataStoreSeed(
        Vector(
          DataStoreSeedEntry(
            collection = cid,
            record = Record.dataAuto("id" -> "p1", "name" -> "taro")
          ),
          DataStoreSeedEntry(
            collection = cid,
            record = Record.dataAuto("id" -> "p2", "name" -> "jiro")
          )
        )
      )
      given ExecutionContext = ExecutionContext.create()

      When("importing the seed")
      val imported = space.importSeed(seed)

      Then("all records are available through the datastore route")
      imported shouldBe Consequence.unit
      space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p1"))) shouldBe
        Consequence.success(Some(Record.dataAuto("id" -> "p1", "name" -> "taro")))
      space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p2"))) shouldBe
        Consequence.success(Some(Record.dataAuto("id" -> "p2", "name" -> "jiro")))
    }
  }
}
