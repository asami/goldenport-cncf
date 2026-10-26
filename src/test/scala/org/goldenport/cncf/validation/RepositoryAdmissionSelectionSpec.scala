package org.goldenport.cncf.validation

import java.nio.file.Path
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Actual metadata selection, without running the selected test bodies.
 * @since   Oct. 26, 2026
 * @version Oct. 26, 2026
 */
class RepositoryAdmissionSelectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Repository ADMISSION" should {
    "honor operation replacement without pulling HEAVY fixture cases into ADMISSION" in {
      Given("an existing metadata fixture with ADMISSION defaults and HEAVY overrides")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val fixture = classOf[fixtures.phase103.valid.FunMetadataFixtureSpec].getName
      When("the same selector resolves actual class and operation declarations")
      val selected = RepositoryAdmissionSelection.select(root, "test", Array(fixture))
      Then("only inherited ADMISSION operations are selected")
      selected.map(_(1)).toSet shouldBe Set("inherits defaults", "explicit empty provider")
    }

    "select declared operations and preserve their other purposes" in {
      Given("an ADMISSION suite and an unlabelled suite")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val admission = classOf[ValidationMetadataV1Spec].getName
      When("the build bridge selects through the canonical producer")
      val selected = RepositoryAdmissionSelection.select(root, "test", Array(admission, getClass.getName))
      Then("only labelled actual registrations run, without changing FULL or FOCUSED")
      selected.map(_(0)).toSet shouldBe Set(admission)
      selected.map(_(1)).toSet shouldBe new ValidationMetadataV1Spec().testNames
      new AdmissionTestDeclaration().declaration.purposes.get shouldBe Set(
        ValidationMetadataV1.Purpose.ADMISSION, ValidationMetadataV1.Purpose.FOCUSED,
        ValidationMetadataV1.Purpose.FULL)
    }

    "refuse to report an empty selection as acceptance" in {
      Given("no labelled suites")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      When("the ADMISSION selection is requested")
      val result = scala.util.Try(RepositoryAdmissionSelection.select(root, "test", Array(getClass.getName)))
      Then("no zero-test pass is produced")
      result.failed.get shouldBe a[IllegalArgumentException]
    }
  }
}
