package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class DataStoreSpaceInjectSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "DataStoreSpace.inject" should {
    "avoid ID collision when injecting multiple records without id" in {
      Given("an in-memory datastore space and two records without id")
      val space = DataStoreSpace.default()
      val cid = DataStore.CollectionId("inject_spec")
      val r1 = Record.dataAuto("name" -> "first")
      val r2 = Record.dataAuto("name" -> "second")
      given ExecutionContext = ExecutionContext.create()

      When("injecting both records into the same collection")
      val x1 = space.inject(cid, r1)
      val x2 = space.inject(cid, r2)

      Then("both injections succeed without duplicate conflict")
      x1 shouldBe Consequence.unit
      x2 shouldBe Consequence.unit
    }
  }
}
