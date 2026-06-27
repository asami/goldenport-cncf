package org.goldenport.cncf

import java.io.ByteArrayOutputStream
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 27, 2026
 * @version Jun. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfMainSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CNCF main" should {
    "report the runtime version without bootstrapping component repositories" in {
      Given("the CNCF runtime is launched for version inspection")
      val output = new ByteArrayOutputStream

      When("the version command is executed")
      Console.withOut(output) {
        CncfMain.main(Array("version"))
      }

      Then("the runtime reports the CNCF build version")
      output.toString.trim shouldBe s"${CncfBuildInfo.name} ${CncfBuildInfo.version}"
    }
  }
}
