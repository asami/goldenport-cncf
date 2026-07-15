package org.goldenport.cncf.security

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityAbacConditionDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Entity ABAC temporal evaluation" should {
    "derive now from the bound execution clock across replay" in {
      Given("generated execution instants and a temporal authorization condition")
      val replayproperty = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
        val evaluatedat = Instant.ofEpochSecond(epochsecond)
        given ExecutionContext = ExecutionContext.create(Clock.fixed(evaluatedat, ZoneOffset.UTC))
        val condition = EntityAbacCondition.parse("publishAt<=now:read").get
        val record = Record.dataAuto("publishAt" -> evaluatedat.minusSeconds(1).toString)
        val authorization = UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("Article"),
          accessKind = "read"
        )

        val context = EntityAuthorizationContext(record, authorization)
        val evaluation = condition.evaluate(context)

        context.environment.evaluatedAt.contains(evaluatedat) &&
          evaluation.expected.contains(evaluatedat.toString) &&
          evaluation.matched
      }

      When("the authorization context is replayed with equivalent clocks")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)

      Then("the temporal operand remains the supplied execution instant")
      checked.passed shouldBe true
    }

    "fail closed when context-free compatibility evaluation has no instant" in {
      Given("a temporal condition without an authorization execution context")
      val condition = EntityAbacCondition.parse("publishAt<=now:read").get
      val record = Record.dataAuto("publishAt" -> "2000-01-01T00:00:00Z")
      val subject = SecuritySubject(
        subjectId = "anonymous",
        authenticationState = SecuritySubject.AuthenticationState.Anonymous,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      When("the compatibility evaluator cannot resolve now")
      val evaluation = condition.evaluate(record, subject)

      Then("the condition is rejected without consulting ambient wall time")
      evaluation.expected shouldBe None
      evaluation.matched shouldBe false
    }
  }
}
