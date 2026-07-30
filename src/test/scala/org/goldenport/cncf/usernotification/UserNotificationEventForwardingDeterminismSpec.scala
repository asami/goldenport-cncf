package org.goldenport.cncf.usernotification

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.event.ReceptionDomainEvent
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class UserNotificationEventForwardingDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "User-notification forwarding diagnostics" should {
    "derive Event identity and time from the selected execution profile" in {
      Given("generated fixed execution instants and deterministic ID streams")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val instant = _instant(epochoffset)
        val left = _record(instant, "forwarding-seed", "job-forwarding-replay")
        val right = _record(instant, "forwarding-seed", "job-forwarding-replay")

        left.id == right.id &&
          left.createdAt == instant &&
          right.createdAt == instant &&
          left.id.timestamp.contains(instant) &&
          left.id.entropy.nonEmpty
      }

      When("equivalent forwarding diagnostics are replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(16), property)

      Then("their persistent Event records are replay-stable")
      checked.passed shouldBe true
    }

    "keep distinct forwarding outcomes distinct within one invocation" in {
      Given("one execution profile and one source Job event")
      given ExecutionContext = _execution_context(
        Instant.parse("2026-07-16T00:00:00Z"),
        "forwarding-distinct-seed"
      )
      val source = _source("job-forwarding-distinct")
      val request = Some(_request("job-forwarding-distinct"))

      When("sent and failed diagnostics are materialized")
      val sent = UserNotificationEventForwarder.diagnosticRecord(
        "user-notification.forwarding.sent",
        source,
        request,
        "notification-1"
      )
      val failed = UserNotificationEventForwarder.diagnosticRecord(
        "user-notification.forwarding.failed",
        source,
        request,
        "provider failed"
      )

      Then("each outcome receives a distinct profile-derived Event identity")
      sent.id should not be failed.id
      sent.createdAt shouldBe failed.createdAt
    }
  }

  private def _record(
    instant: Instant,
    seed: String,
    jobid: String
  ) = {
    given ExecutionContext = _execution_context(instant, seed)
    UserNotificationEventForwarder.diagnosticRecord(
      "user-notification.forwarding.sent",
      _source(jobid),
      Some(_request(jobid)),
      "notification-1"
    )
  }

  private def _source(jobid: String): ReceptionDomainEvent =
    ReceptionDomainEvent(
      name = "job.succeeded",
      kind = "job.succeeded",
      payload = Map("job-id" -> jobid),
      attributes = Map.empty,
      occurredAt = Instant.EPOCH
    )

  private def _request(jobid: String): UserNotificationRequest =
    UserNotificationRequest(
      recipientUserId = "alice",
      notificationType = "cncf.job",
      channel = "in-app",
      title = "Job succeeded",
      body = "done",
      dedupeKey = Some(s"cncf.job:$jobid:succeeded")
    )

  private def _execution_context(
    instant: Instant,
    seed: String
  ): ExecutionContext = {
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val base = ExecutionContext.create(clock)
    val idgeneration = IdGenerationContext.deterministic(
      IdGenerationContext.DEFAULT_NAMESPACE,
      clock,
      seed
    )
    ExecutionContext.withIdGenerationContext(base, idgeneration)
  }

  private def _instant(offset: Int): Instant = {
    val epoch = 1_700_000_000L + Math.floorMod(offset.toLong, 1_000_000L)
    Instant.ofEpochSecond(epoch)
  }
}
