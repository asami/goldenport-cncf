package org.goldenport.cncf.resource

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 45 static Web target admission.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebTargetAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _octet = Gen.chooseNum(0, 255)
  private val _private_ipv4 = Gen.oneOf(
    for {
      b <- _octet
      c <- _octet
      d <- _octet
    } yield s"10.${b}.${c}.${d}",
    for {
      b <- Gen.chooseNum(16, 31)
      c <- _octet
      d <- _octet
    } yield s"172.${b}.${c}.${d}",
    for {
      c <- _octet
      d <- _octet
    } yield s"192.168.${c}.${d}"
  )

  "WebTargetAdmission" should {
    "admit one public HTTPS literal without broadening ResourceAccess host policy" in {
      Given("a public target using the default HTTPS port")
      val reference = ResourceReference.parseC("https://8.8.8.8/static/page").toOption.get

      When("static Web admission evaluates only transport safety")
      val result = WebTargetAdmission.admitC(reference)

      Then("the URL is admitted while provider selection remains outside this value")
      result.toOption.map(_.print) shouldBe Some("https://8.8.8.8/static/page")
    }

    "reject generated private IPv4 targets before provider execution" in {
      Given("generated addresses from every RFC1918 range")
      val property = Prop.forAll(_private_ipv4) { host =>
        val reference = ResourceReference.parseC(s"https://${host}/internal").toOption.get
        !WebTargetAdmission.admitC(reference).isSuccess
      }

      When("the targets cross static Web admission")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

      Then("every private target is denied deterministically")
      checked.passed shouldBe true
    }

    "reject scheme authority and URL forms outside the static contract" in {
      Given("HTTP, user-info, fragment, non-default-port, and reserved targets")
      val values = Vector(
        "http://8.8.8.8/page",
        "https://user@8.8.8.8/page",
        "https://8.8.8.8/page#fragment",
        "https://8.8.8.8:8443/page",
        "https://100.64.0.1/internal",
        "https://198.51.100.1/documentation",
        "https://[2001:db8::1]/documentation"
      ).map(ResourceReference.parseC(_).toOption.get)

      When("the values are admitted")
      val results = values.map(WebTargetAdmission.admitC)

      Then("none can influence transport authority")
      results.forall(!_.isSuccess) shouldBe true
    }
  }
}
