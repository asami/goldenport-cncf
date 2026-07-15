package org.goldenport.cncf.entity.runtime

import java.time.Instant
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkingSetStatusDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Entity working-set lifecycle status" should {
    "retain explicit execution-profile instants across successful and failed transitions" in {
      Given("generated loading and completion instants supplied by a runtime boundary")
      val replayproperty = Prop.forAll(Gen.chooseNum(0L, 315576000L), Gen.chooseNum(0L, 3600L)) {
        (epochsecond, elapsedseconds) =>
          val startedat = Instant.ofEpochSecond(epochsecond)
          val completedat = startedat.plusSeconds(elapsedseconds)
          val readystatus = WorkingSetStatusRef()
          readystatus.markLoading(startedat)
          readystatus.markReady(completedat)
          val failedstatus = WorkingSetStatusRef()
          failedstatus.markLoading(startedat)
          failedstatus.markFailed("load failed", completedat)

          readystatus.get == WorkingSetStatus(
            WorkingSetLoadState.Ready,
            startedAt = Some(startedat),
            completedAt = Some(completedat)
          ) && failedstatus.get == WorkingSetStatus(
            WorkingSetLoadState.Failed,
            startedAt = Some(startedat),
            completedAt = Some(completedat),
            error = Some("load failed")
          )
      }

      When("ready and failed lifecycle sequences are replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)

      Then("status timestamps equal the supplied execution-profile instants")
      checked.passed shouldBe true
    }
  }
}
