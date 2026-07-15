package org.goldenport.cncf.knowledge

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class KnowledgeWorkingSetDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Knowledge working-set lifecycle time" should {
    "derive successful and failed reload status from the caller execution clock" in {
      Given("generated fixed execution-clock instants")
      val replayproperty = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
        val instant = Instant.ofEpochSecond(epochsecond)
        given ExecutionContext = ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC))
        val node = KnowledgeNode(KnowledgeNodeId("deterministic-node"), "concept")
        val snapshot = KnowledgeWorkingSetSnapshot(nodes = Vector(node))
        val loaded = _success(KnowledgeWorkingSet.load(snapshot))
        val space = new KnowledgeSpace
        _success(space.replace(snapshot))
        val failed = space.replace(KnowledgeWorkingSetSnapshot(nodes = Vector(node, node)))

        loaded.status.startedAt.contains(instant) &&
          loaded.status.completedAt.contains(instant) &&
          failed.isInstanceOf[Consequence.Failure[?]] &&
          space.status.state == KnowledgeWorkingSetState.Failed &&
          space.status.startedAt.contains(instant) &&
          space.status.completedAt.contains(instant) &&
          space.nodeOption(node.id).contains(node)
      }

      When("successful load and failed replacement are replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)

      Then("both lifecycle outcomes retain the execution-capability instant")
      checked.passed shouldBe true
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.toString)
    }
}
