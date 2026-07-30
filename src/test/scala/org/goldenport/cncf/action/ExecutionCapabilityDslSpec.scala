package org.goldenport.cncf.action

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.context.RandomContext
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.observability.CallTreeContext
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionCapabilityDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase52_spec =
    afterWord("in spec:entity-collection-identity, example:execution-capability, rules:R1,R4, phase:52")
  private val _purpose =
    Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

  "Behavior internal execution capability DSL" must _in_phase52_spec {
    "derive repeatable random values from isolated purpose streams" in {
      Given("generated purposes and two equivalent seeded execution contexts")
      val property = Prop.forAll(_purpose) { purpose =>
        val left = new CapabilityBehavior(Behavior.Core(_context("random-seed", "id-seed"), None, None))
        val right = new CapabilityBehavior(Behavior.Core(_context("random-seed", "id-seed"), None, None))

        left.randomSnapshot(purpose) == right.randomSnapshot(purpose)
      }

      When("behavior reads random values through the purpose-based DSL")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("all generated purpose streams reproduce")
      checked.passed shouldBe true
    }

    "trace capability structure without exposing generated IDs or seed material" in {
      Given("a behavior with CallTree enabled and deterministic capabilities")
      val seed = "confidential-capability-seed"
      val context = _context(seed, seed, calltreeenabled = true)
      val behavior = new CapabilityBehavior(Behavior.Core(context, None, None))

      When("the behavior generates an Entity ID and an opaque ID")
      val generated = behavior.idSnapshot("order-number")
      val rendered = context.observability.callTreeContext.build().map(_.toRecord.toString).getOrElse("")

      Then("CallTree records only capability, operation, purpose, and collection metadata")
      rendered should include ("execution:id.entity-id")
      rendered should include ("execution:id.opaque-id")
      rendered should include ("execution-capability")
      rendered should include ("order-number")
      rendered should not include seed
      rendered should not include generated._1.value
      rendered should not include generated._2
    }
  }

  private def _context(
    randomseed: String,
    idseed: String,
    calltreeenabled: Boolean = false
  ): ExecutionContext = {
    val clock = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC)
    val base = ExecutionContext.create(clock).asInstanceOf[ExecutionContext.Instance]
    val calltree = if (calltreeenabled) CallTreeContext.enabled else CallTreeContext.Disabled
    base.copy(
      core = base.core.copy(random = RandomContext.seeded(randomseed)),
      cncfCore = base.cncfCore.copy(
        observability = base.observability.copy(callTreeContext = calltree),
        idGeneration = IdGenerationContext.deterministic(IdGenerationContext.DEFAULT_NAMESPACE, clock, idseed)
      )
    )
  }

  private final class CapabilityBehavior(
    val behaviorCore: Behavior.Core
  ) extends Behavior {
    private val _collection = EntityCollectionId("sample", "catalog", "order")

    def randomSnapshot(purpose: String): (Int, Long, Double, Boolean) =
      (
        random_int(purpose, 1000),
        random_long(purpose),
        random_double(purpose),
        random_boolean(purpose)
      )

    def idSnapshot(purpose: String): (EntityId, String) =
      (
        entity_id(_collection, purpose),
        opaque_id(purpose)
      )
  }
}
