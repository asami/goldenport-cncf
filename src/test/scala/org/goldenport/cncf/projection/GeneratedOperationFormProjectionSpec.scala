package org.goldenport.cncf.projection

import org.goldenport.cncf.http.StaticFormAppRenderer
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedOperationFormProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Generated operation form projection" should {
    "render canonical text input constraints from generated runtime metadata" in {
      Given("a generated-style operation with one required canonical text parameter")
      val component = GeneratedHelpProjectionFixture.component()
      val subsystem = component.subsystem.getOrElse(fail("generated fixture subsystem is missing"))

      When("the standard operation form renderer projects the operation")
      val html = StaticFormAppRenderer()
        .renderOperationForm(subsystem, component.name, "address", "lookupAddress")
        .map(_.body)
        .getOrElse(fail("generated operation form is missing"))

      Then("the form uses a required textarea with the canonical per-locale length range")
      html should include ("""<textarea""")
      html should include ("""name="description"""")
      html should include ("""required""")
      html should include ("""minlength="1"""")
      html should include ("""maxlength="8192"""")
    }
  }
}
