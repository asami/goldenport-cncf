package org.goldenport.cncf.datastore

import cats.{Id, ~>}
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  6, 2026
 * @version Jan. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class DataStackFactorySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "DataStackFactory" should {
    "create a UnitOfWork with selectable datastore for memory backend" in {
      pending
      Given("a config that selects memory backend")
      val config = Configuration(
        Map("datastore.backend" -> ConfigurationValue.StringValue("memory"))
      )

      When("creating the data stack")
      val uow = DataStackFactory.create(config)

      Then("UnitOfWork provides a selectable datastore")
      uow.searchableDatastore.isDefined shouldBe true

      And("select works for Query.Empty")
      val selectable = uow.searchableDatastore.get
      val result = selectable.search(
        DataStore.CollectionId("record"),
        QueryDirective(
          query = Query.Empty
        )
      ).TAKE
      result.records shouldBe Vector.empty
      result.range shouldBe ResultRange.Exact
    }
  }
}
