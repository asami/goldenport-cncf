package org.goldenport.cncf.validation.fixtures.phase103.ondemand

import org.scalatest.funsuite.AnyFunSuite
import org.goldenport.cncf.validation.ValidationDeclarationProvider
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}

/** Harmless registrations exercising inherited and mixed low-frequency metadata. */
class OnDemandDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(
    purposes = Some(Set(Purpose.ON_DEMAND, Purpose.ADMISSION, Purpose.FULL, Purpose.HEAVY, Purpose.FOCUSED)),
    features = Set(FeatureReference("component:fixture", "low-frequency")))
}
class InheritDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration()
}
@ValidationSpecification(identity = "on-demand:spec", declaration = classOf[OnDemandDeclarations])
abstract class OnDemandDefaults extends AnyFunSuite
class OnDemandFixtureSpec extends OnDemandDefaults {
  test("inherited low-frequency registration") { assert(1 + 1 == 2) }
  test("operation inherits low-frequency registration") { assert("fixture".nonEmpty) }
  @ValidationScenario(name = "operation inherits low-frequency registration", identity = "inherited-operation",
    revision = "operation-2", declaration = classOf[InheritDeclarations])
  def inheritedOperation(): Unit = ()
}
