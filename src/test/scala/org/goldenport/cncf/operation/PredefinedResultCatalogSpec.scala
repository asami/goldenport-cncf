package org.goldenport.cncf.operation

import org.goldenport.cncf.service.{IntResult, OperationResult, UnitResult}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class PredefinedResultCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The CNCF predefined Result catalog" should {
    "own the minimum CML Result contract" in {
      Given("the CNCF-owned default Result catalog")
      val catalog = PredefinedResultCatalog.default

      When("the minimum Phase 16 Result definitions are resolved")
      val operationresult = catalog.get("OperationResult")
      val unitresult = catalog.get("UnitResult")
      val intresult = catalog.get("IntResult")

      Then("the catalog exposes stable runtime classes and payload schemas")
      catalog.schemaVersion shouldBe "cncf.predefined-result.v1"
      PredefinedResultCatalog.RESOURCE_PATH shouldBe "META-INF/cncf/predefined-results.json"
      operationresult.map(_.runtimeClassName) shouldBe Some(classOf[OperationResult].getName)
      operationresult.toVector.flatMap(_.resultFields) shouldBe empty
      unitresult.map(_.runtimeClassName) shouldBe Some(classOf[UnitResult].getName)
      unitresult.toVector.flatMap(_.resultFields) shouldBe empty
      intresult.map(_.runtimeClassName) shouldBe Some(classOf[IntResult].getName)
      intresult.toVector.flatMap(_.resultFields) shouldBe Vector(CmlOperationField("value", "int"))
    }

    "keep Result runtime values assignable to the common operation contract" in {
      Given("unit and integer predefined Result values")
      val values: Vector[OperationResult] = Vector(UnitResult(), IntResult(3))

      When("the values are handled through the common runtime Result contract")
      val names = values.map(_.getClass.getSimpleName)

      Then("both predefined values retain their concrete runtime identity")
      names shouldBe Vector("UnitResult", "IntResult")
      values.collectFirst { case IntResult(value) => value } shouldBe Some(3)
    }

    "resolve only exact catalog names" in {
      Given("the CNCF-owned default Result catalog")
      val catalog = PredefinedResultCatalog.default

      When("an unknown or case-mismatched Result name is queried")
      val unknown = catalog.get("StringResult")
      val mismatched = catalog.get("intresult")

      Then("the catalog does not invent or normalize unsupported Result types")
      unknown shouldBe None
      mismatched shouldBe None
      catalog.contains("IntResult") shouldBe true
    }
  }
}
