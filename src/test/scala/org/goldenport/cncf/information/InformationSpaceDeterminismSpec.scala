package org.goldenport.cncf.information

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.information.value.{InformationFieldEvent, InformationFieldState}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpaceDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "InformationSpace semantic time" should {
    "derive lifecycle and materialization timestamps from the caller execution clock" in {
      Given("generated fixed execution-clock instants")
      val replayproperty = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
        val instant = Instant.ofEpochSecond(epochsecond)
        given ExecutionContext = ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC))
        val space = new InformationSpace
        val registered = _success(space.registerInformation("paper", Vector(Record.data(
          "title" -> "Deterministic Information",
          "authors" -> "Alice Example"
        )))).head
        val fieldevent = InformationFieldEvent(
          fieldPath = "title",
          state = InformationFieldState.stable,
          source = "executable-spec",
          operation = None,
          provider = None,
          transformation = None,
          valueBefore = None,
          valueAfter = None,
          evidence = None,
          note = None,
          occurredAt = summon[ExecutionContext].clock.instant(),
          actor = None
        )
        val withfield = _success(space.appendFieldEvent(registered.id, fieldevent))
        val validated = _success(space.validateInformation(registered.id))
        val confirmed = _success(space.confirmInformation(registered.id))
        val publication = _success(space.publishInformation(registered.id, "deterministic-target"))
        val published = space.getInformation(registered.id).getOrElse(
          throw new IllegalStateException("published Information is missing")
        )
        val materialized = InformationSpace.materializeInformation(published)

        registered.lifecycleAttributes.updatedAt == instant &&
          fieldevent.occurredAt == instant &&
          withfield.lifecycleAttributes.updatedAt == instant &&
          validated.lifecycleAttributes.updatedAt == instant &&
          confirmed.confirmedAt.contains(instant) &&
          confirmed.lifecycleAttributes.updatedAt == instant &&
          publication.publishedAt.contains(instant) &&
          published.lifecycleAttributes.updatedAt == instant &&
          materialized.frames.nonEmpty &&
          materialized.frames.forall(_.materializedAt.contains(instant))
      }

      When("the full Information lifecycle and Knowledge projection are replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)

      Then("every semantic timestamp equals the execution capability without ambient time access")
      checked.passed shouldBe true
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.toString)
    }
}
