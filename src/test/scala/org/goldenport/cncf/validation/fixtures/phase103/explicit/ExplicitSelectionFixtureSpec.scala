package org.goldenport.cncf.validation.fixtures.phase103.explicit

import org.scalatest.DoNotDiscover
import org.scalatest.funsuite.AnyFunSuite
import org.goldenport.cncf.validation.ValidationDeclarationProvider
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}

/** Harmless framework fixtures; no external environment or process is required.
 * @since   Oct. 31, 2026
 * @version Oct. 31, 2026
 */
class ExplicitDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.ADMISSION, Purpose.FULL)))
}
class InheritDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration()
}
@ValidationSpecification(identity = "explicit:spec", declaration = classOf[ExplicitDeclarations])
abstract class ExplicitDefaults extends AnyFunSuite
@DoNotDiscover
class ExplicitSelectionFixtureSpec extends ExplicitDefaults {
  test("explicit fixture first leaf") { assert(Vector(1, 2).sum == 3) }
  test("explicit fixture inherited leaf") { assert("fixture".startsWith("fix")) }
  @ValidationScenario(name = "explicit fixture inherited leaf", identity = "inherited-operation",
    revision = "operation-2", declaration = classOf[InheritDeclarations])
  def inheritedOperation(): Unit = ()
}
@ValidationSpecification(identity = "ordinary:spec", declaration = classOf[ExplicitDeclarations])
class OrdinarySelectionFixtureSpec extends AnyFunSuite {
  test("ordinary fixture control leaf") { assert(Vector(1, 2).size == 2) }
}
