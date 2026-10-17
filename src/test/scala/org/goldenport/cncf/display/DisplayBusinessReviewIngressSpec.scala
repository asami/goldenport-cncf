package org.goldenport.cncf.display

import java.nio.charset.StandardCharsets
import org.goldenport.cncf.entity.EntityRevisionRepresentation
import org.http4s.Method
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class DisplayBusinessReviewIngressSpec extends AnyWordSpec with DisplayBusinessReviewFixture.Vocabulary {
  import DisplayBusinessReviewFixture.*
  import DisplayMutationServiceFixture.{Target, representations, field}

  "BRH1 Ordinary Business HTTP correspondence" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRH1, rules:BR1,BR2, phase:96") {
    "directly POST the real Business Operation then explicitly GET fresh Detail on SQLite" in {
      Given("docs/spec/display-business-operation-descriptor.md BR1/BR2: a real SQLite alias fixture and actual Http4s app; ControlledTest facts are test-only")
      val session = open(Target.Alias, EntityRevisionRepresentation.Detached)
      try {
        val before = baseline(session)
        val wanted = expected(session, before, 2L)
        When("actual Http4s directly POSTs /business/requestReview.json with ordinary form Properties")
        val response = http(session)
        Then("ordinary Business JSON follows a canonical commit with no implicit Display read or projection")
        withClue(response.text) { response.status shouldBe 200 }
        response.contentType shouldBe Some("application/json")
        checkResponse(response.data, session, 2L)
        checkAuthorization(session)
        checkIdle(session, before)
        checkState(session, wanted)
        session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 1
        session.lifecycleObservers.map(_.writes).sum shouldBe 0
        val businessresponse = response.data
        When("the client then explicitly GETs /display/read.json")
        val fresh = readHttp(session)
        Then("fresh selected Detail alone observes review-requested revision two while both earlier values retain their independent meanings")
        withClue(fresh.text) { fresh.status shouldBe 200 }
        checkDetail(fresh.data, session, "review-requested", 2L)
        checkDetail(before.display, session, "draft", 1L)
        checkResponse(businessresponse, session, 2L)
        session.log.events.count(_.event == "business") shouldBe 1
        session.log.events.count(_.event == "find") shouldBe 1
        session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 1
        checkState(session, wanted)
      } finally session.close()
    }
  }

  "BRH2 Production authenticated HTTP boundary" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRH2, rules:BO5,BR1,BR2, phase:96") {
    "deny untrusted claims and foreign sessions while admitting real owner commit and explicit reload" in {
      for (target <- Vector(Target.Entity, Target.Aggregate); representation <- representations) {
        Given(s"docs/spec/display-business-operation-descriptor.md BO5/BR1/BR2: SQLite $target/$representation in production standard/system multi-user mode")
        val session = open(target, representation, setup = Setup(production = true))
        try {
          When("the actual provider-backed HTTP route receives denial witnesses followed by its real owner positive control")
          checkProductionHttp(session)
          Then("every production observation is supplied by actual Http4s/provider/canonical execution")
          session.log.events.count(_.event == "business") shouldBe 1
        } finally session.close()
      }
    }
  }

  "BRH3 No Display action tunnel or readonly-stage mutation" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRH3, rules:BO1,BO5,BR1,BR2, phase:96") {
    "reject generic Display action invocation and stage editing while descriptor metadata stays inert" in {
      Given("docs/spec/display-business-operation-descriptor.md BO1/BO5/BR1/BR2: actual SQLite ordinary routes, a readonly stage and the registered Business descriptor")
      val session = open()
      try {
        val before = baseline(session)
        When("the actual app receives a guessed generic Display action route and target selectors")
        val tunnel = http(session, "display", "action", Method.POST, parameters = Some(Vector(
          "resourceType" -> DisplayMutationServiceFixture.resourceType, "screenId" -> DisplayMutationServiceFixture.screenId,
          "actionId" -> "request-review", "target" -> "business.requestReview")))
        Then("no action tunnel executes and the complete warmed baseline is preserved")
        tunnel.status should be >= 400
        session.log.events.count(_.event == "business") shouldBe 0
        checkPreservedBusiness(session, before)
        Given("the unchanged independent baseline and a standard Display mutation selecting readonly stage")
        val payload = session.payload(Vector(field("stage", DisplayValue.Text("review-requested"))))
        session.log.reset()
        When("actual Http4s PUT submits that standard Display payload with its normal observed revision")
        val mutation = session.http(payload.getBytes(StandardCharsets.UTF_8))
        Then("readonly-stage admission rejects before canonical effects or implicit source reads")
        mutation.status should be >= 400
        checkPreservedBusiness(session, before)
        Given("the same unchanged canonical state and the separate admitted metadata Operation")
        session.log.reset()
        When("actual Http4s GET describes the Business Action")
        val descriptor = http(session, "display", "describeBusiness", Method.GET)
        Then("metadata remains inert, and only the separately published ordinary Business route can perform its command")
        withClue(descriptor.text) { descriptor.status shouldBe 200 }
        descriptor.data.hcursor.downField("actions").downN(0).downField("operation").get[String]("operation") shouldBe Right("requestReview")
        session.log.events.count(_.event == "business") shouldBe 0
        checkPreservedBusiness(session, before)
      } finally session.close()
    }
  }
}
