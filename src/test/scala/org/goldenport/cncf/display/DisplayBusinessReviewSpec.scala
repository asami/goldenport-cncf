package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.entity.EntityConcurrencyMetadata
import org.goldenport.protocol.Property
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class DisplayBusinessReviewSpec extends AnyWordSpec with DisplayBusinessReviewFixture.Vocabulary {
  import DisplayBusinessReviewFixture.*
  import DisplayMutationServiceFixture.{Target, representations, json, conclusion, revision, Reload, denied}

  "BR1 Inert registered review descriptor" should afterWord(
    "in spec:display-business-operation-descriptor, example:BR1, rules:BO1,BO2,BR1, phase:96") {
    "describe exactly the existing ordinary Operation and its ordered input without invoking it" in {
      Given("docs/spec/display-business-operation-descriptor.md BO1/BO2/BR1: a normally bootstrapped Business fixture with warmed Views")
      val session = open()
      try {
        val before = baseline(session)
        When("the ordinary metadata Operation describes the admitted request-review Action")
        val described = json(session.execute(descriptorRequest(session)).result)
        Then("the canonical target and ordered String Properties are inert and the full independent baseline is unchanged")
        val actions = described.hcursor.downField("actions").focus.get.asArray.get
        actions should have size 1
        val action = actions.head.hcursor
        action.get[String]("scope") shouldBe Right("screen")
        action.get[String]("id") shouldBe Right("request-review")
        action.get[String]("label") shouldBe Right("Request review")
        action.get[String]("bindingId") shouldBe Right("business:request-review")
        action.downField("operation").get[String]("componentId") shouldBe Right(session.component.componentId.name)
        action.downField("operation").get[String]("service") shouldBe Right("business")
        action.downField("operation").get[String]("operation") shouldBe Right("requestReview")
        val inputs = action.downField("input").focus.get.asArray.get
        inputs.map(_.hcursor.get[String]("name")) shouldBe Vector(Right("resourceId"), Right("revision"))
        inputs.foreach { input =>
          input.hcursor.get[String]("kind") shouldBe Right("property")
          input.hcursor.get[String]("datatype") shouldBe Right("string")
          input.hcursor.get[String]("multiplicity") shouldBe Right("1")
        }
        session.log.events.count(_.event == "business") shouldBe 0
        checkPreservedBusiness(session, before)
      } finally session.close()
    }
  }

  "BR2 Direct canonical Business then explicit Detail" should afterWord(
    "in spec:display-business-operation-descriptor, example:BR2, rules:BR1,BR2, phase:96") {
    "commit Entity, Aggregate and alias across both representations without an implicit Display read" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-business-operation-descriptor.md BR1/BR2: real SQLite $target/$representation and independent complete pre-state")
        val session = open(target, representation)
        try {
          val before = baseline(session)
          val wanted = expected(session, before, 2L)
          When("the ordinary synchronous business.requestReview command receives the canonical identity and revision one")
          val attempt = session.execute(request(session))
          Then("the actual enclosing commit publishes only stage and managed metadata, with a separate non-Display JSON response")
          val response = checkCommit(attempt, session, before, wanted, 2L)
          session.log.commands shouldBe (if (target == Target.Entity) 0 else 1)
          checkReload(session, before, response, 2L)
          checkState(session, wanted)
        } finally session.close()
      }
    }
  }

  "BR3 Displayed Enabled grants no Business authority" should afterWord(
    "in spec:display-business-operation-descriptor, example:BR3, rules:BO5,BR2, phase:96") {
    "retain canonical authentication, instance permission and factory denial across all targets" in {
      for (target <- Target.values.toVector; representation <- representations;
        mode <- Vector("anonymous", "foreign", "factory")) {
        Given(s"docs/spec/display-business-operation-descriptor.md BO5/BR2: Enabled action, $target/$representation and $mode subject")
        val session = open(target, representation)
        try {
          val before = baseline(session)
          session.log.denyFactory = mode == "factory"
          before.display.hcursor.downField("model").downField("actions").downN(0).get[String]("availability") shouldBe Right("enabled")
          val active = session.execution(principal = if (mode == "foreign") "foreign" else "owner",
            authenticated = mode != "anonymous")
          When("the separately published ordinary Business command applies canonical authorization")
          val attempt = session.execute(request(session), active)
          Then("denial publishes no mutation or Display and preserves the complete independent baseline")
          attempt.result shouldBe a[Consequence.Failure[?]]
          val found = conclusion(attempt.result)
          val wanted = if (mode == "anonymous") conclusion(Consequence.securityAuthenticationRequired("expected"))
            else conclusion(Consequence.securityPermissionDenied("expected"))
          found.observation.taxonomy shouldBe wanted.observation.taxonomy
          if (mode == "factory") (found eq denied.conclusion) shouldBe true
          if (mode == "foreign") session.log.authorizations.foreach { case (_, uow) =>
            uow.executionContext.security.principal.id.value shouldBe "foreign"
          }
          active.attempts shouldBe 0
          checkPreservedBusiness(session, before)
        } finally session.close()
      }
    }
  }

  "BR4 Ordinary Business input admission" should afterWord(
    "in spec:display-business-operation-descriptor, example:BR4, rules:BR1, phase:96") {
    "reject missing, malformed, repeated and non-String values before domain or provider effects" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-business-operation-descriptor.md BR1: a warmed $target/$representation and every malformed ordinary input class")
        val session = open(target, representation)
        try malformed(session).foreach { candidate =>
          Given("the complete independent baseline immediately before the malformed candidate")
          val before = baseline(session)
          When("the ordinary Operation admits required single String identity and canonical positive decimal revision")
          val attempt = session.execute(candidate)
          Then("structured input rejection precedes the Business ActionCall and all canonical writes or Browser reads")
          attempt.result shouldBe a[Consequence.Failure[?]]
          attempt.execution.attempts shouldBe 0
          session.log.events.count(_.event == "business") shouldBe 0
          checkPreservedBusiness(session, before)
        } finally session.close()
      }
    }
  }

  "BR5 Canonical stale observation" should afterWord(
    "in spec:display-business-operation-descriptor, example:BR5, rules:BR1,BR2, phase:96") {
    "preserve durable state and reject Aggregate stale input before its domain callback" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-business-operation-descriptor.md BR1/BR2: authoritative revision one in $target/$representation with warmed Views")
        val session = open(target, representation)
        try {
          val before = baseline(session)
          When("the ordinary Business command observes revision two instead of the canonical current one")
          val attempt = session.execute(request(session, "2"))
          Then("the canonical stale Conclusion leaves every root, body, table and Browser/build observation unchanged")
          checkLifecycleFailure(conclusion(attempt.result), conclusion(EntityConcurrencyMetadata.staleMutation(revision(2L), revision(1L))))
          attempt.execution.space.observers.flatMap(_.captured).flatMap(_.record) shouldBe empty
          checkPreservedBusiness(session, before, stale = true)
          if (target != Target.Entity) session.log.commands shouldBe 0
        } finally session.close()
      }
    }
  }

  "BR6 Presentation availability is independent" should afterWord(
    "in spec:display-business-operation-descriptor, example:BR6, rules:BO5,BR2, phase:96") {
    "allow authorized ordinary invocation from immutable Hidden and Disabled fixtures" in {
      for (availability <- Vector(DisplayModel.ActionAvailability.Hidden, DisplayModel.ActionAvailability.Disabled)) {
        Given(s"docs/spec/display-business-operation-descriptor.md BO5/BR2: an immutable $availability presentation and an authenticated canonical owner")
        val session = open(setup = Setup(availability = availability))
        try {
          val before = baseline(session)
          val wanted = expected(session, before, 2L)
          before.display.hcursor.downField("model").downField("actions").downN(0).get[String]("availability") shouldBe
            Right(availability.toString.toLowerCase(java.util.Locale.ROOT))
          When("the owner independently invokes the ordinary Business Operation")
          val attempt = session.execute(request(session))
          Then("presentation does not revoke the separate canonical authority; Enabled denial is specified by BR3")
          val response = checkCommit(attempt, session, before, wanted, 2L)
          checkReload(session, before, response, 2L)
        } finally session.close()
      }
    }
  }

  "BR7 Committed Business survives a later read failure" should afterWord(
    "in spec:display-business-operation-descriptor, example:BR7, rules:BR2, phase:96") {
    "preserve the Business JSON and durable state while explicit Detail read fails then recovers" in {
      Given("docs/spec/display-business-operation-descriptor.md BR2: a warmed canonical Aggregate and an independent complete commit oracle")
      val session = open(Target.Aggregate)
      try {
        val before = baseline(session)
        val wanted = expected(session, before, 2L)
        When("one ordinary Business command commits before the client attempts any Display reload")
        val attempt = session.execute(request(session))
        Then("the original ordinary response and canonical complete committed state are authoritative")
        val response = checkCommit(attempt, session, before, wanted, 2L)
        session.log.reload = Reload.Failure
        When("a later explicit Display read encounters an actual Browser failure")
        val failed = session.read()
        Then("that separate read fails without replacing the Business response or replaying its writer")
        failed.result shouldBe a[Consequence.Failure[?]]
        checkResponse(response, session, 2L)
        checkState(session, wanted)
        attempt.execution.attempts shouldBe 1
        session.log.events.count(_.event == "business") shouldBe 1
        session.log.commands shouldBe 1
        session.log.reload = Reload.Current
        When("the client explicitly retries only the Display read")
        val recovered = json(session.read().result)
        Then("fresh Detail recovers at the committed revision without another Business invocation")
        checkDetail(recovered, session, "review-requested", 2L)
        checkDetail(before.display, session, "draft", 1L)
        checkResponse(response, session, 2L)
        attempt.execution.attempts shouldBe 1
        session.log.events.count(_.event == "business") shouldBe 1
        session.log.events.count(_.event == "find") shouldBe 2
        checkState(session, wanted)
      } finally session.close()
    }
  }
}
