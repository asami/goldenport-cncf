package org.goldenport.cncf.testutil

import java.time.{Clock, Instant, ZoneOffset}
import java.util.Locale
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, SecurityContext}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 11, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionContextTestFixtureSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Execution-context test fixture" should {
    "project clock, locale policy, and security through supported fixture helpers" in {
      Given("a downstream test context, fixed clock, locale policy, and authenticated subject")
      val base = ExecutionContext.create()
      val instant = Instant.parse("2026-08-06T00:00:00Z")
      val clock = Clock.fixed(instant, ZoneOffset.UTC)
      val security = SecurityContext(
        principal = new Principal {
          def id: PrincipalId = PrincipalId("fixture-user")
          def attributes: Map[String, String] = Map("authenticated" -> "true")
        },
        capabilities = SecurityContext.Privilege.User.capabilities,
        level = SecurityContext.Privilege.User.level,
        subjectKind = SecurityContext.Privilege.User.subjectKind
      )

      When("the fixture applies each controlled downstream test projection")
      val clocked = ExecutionContextTestFixture.withClock(base, clock)
      val localized = ExecutionContextTestFixture.withLocalePolicy(
        clocked,
        Locale.JAPANESE,
        Set(Locale.JAPANESE)
      )
      val secured = ExecutionContextTestFixture.withSecurityContext(
        localized,
        security,
        "runtime-binding-admission-fixture-spec"
      )

      Then("the projected context retains the exact downstream test evidence")
      secured.clock.instant() shouldBe instant
      secured.locale shouldBe Locale.JAPANESE
      secured.i18n.allowedLocales shouldBe Some(Set(Locale.JAPANESE))
      secured.security.principal.id shouldBe PrincipalId("fixture-user")
    }
  }
}
