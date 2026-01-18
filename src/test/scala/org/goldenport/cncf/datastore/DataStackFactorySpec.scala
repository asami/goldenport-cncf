package org.goldenport.cncf.datastore

import cats.{Id, ~>}
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class DataStackFactorySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "DataStackFactory" should {
    "create a UnitOfWork with selectable datastore for memory backend" in {
      Given("a config that selects memory backend")
      val config = Configuration(
        Map("datastore.backend" -> ConfigurationValue.StringValue("memory"))
      )

      When("creating the data stack")
      val uow = DataStackFactory.create(config)

      Then("UnitOfWork provides a selectable datastore")
      uow.selectableDatastore.isDefined shouldBe true

      And("select works for Query.Empty")
      val selectable = uow.selectableDatastore.get
      val result = selectable.select(
        QueryDirective(
          query = Query.Empty
        )
      )
      result.records shouldBe Vector.empty
      result.range shouldBe ResultRange.Exact
    }
  }
}
