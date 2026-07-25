package org.goldenport.cncf.information

import org.simplemodeling.model.SimpleEntity
import org.simplemodeling.model.datatype.EntityRevision
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedInformationRevisionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Generated Information SimpleEntity family" should {
    "consume embedded revision on outputs without exposing it on inputs" in {
      Given("the CNCF Information CML model generated against revision-aware SimpleEntity")

      When("the generated Entity family contracts are inspected")
      val outputcontracts = _output_classes.map { klass =>
        val revisionmethods = klass.getDeclaredMethods.filter(_.getName == "revision")
        klass -> revisionmethods
      }
      val inputrevisionmethods =
        _input_classes.flatMap(_.getMethods.filter(_.getName == "revision"))

      Then("every output type implements one validated embedded revision")
      outputcontracts.foreach { case (klass, methods) =>
        classOf[SimpleEntity].isAssignableFrom(klass) shouldBe true
        methods.length shouldBe 1
        methods.head.getReturnType shouldBe classOf[EntityRevision]
      }

      And("create, update, and query contracts expose no revision input")
      inputrevisionmethods shouldBe empty
    }
  }

  private val _output_classes: Vector[Class[?]] = Vector(
    classOf[org.goldenport.cncf.information.entity.Information],
    classOf[org.goldenport.cncf.information.entity.read.Information],
    classOf[org.goldenport.cncf.information.entity.operation.Information],
    classOf[org.goldenport.cncf.information.entity.aggregate.Information],
    classOf[org.goldenport.cncf.information.entity.view.Information],
    classOf[org.goldenport.cncf.information.entity.view.summary.Information],
    classOf[org.goldenport.cncf.information.entity.view.detail.Information]
  )

  private val _input_classes: Vector[Class[?]] = Vector(
    classOf[org.goldenport.cncf.information.entity.create.Information],
    classOf[org.goldenport.cncf.information.entity.update.Information],
    classOf[org.goldenport.cncf.information.entity.query.Information]
  )
}
