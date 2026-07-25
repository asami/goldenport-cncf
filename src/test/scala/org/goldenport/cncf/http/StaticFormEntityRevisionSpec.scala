package org.goldenport.cncf.http

import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityMutationAdapterDefaults,
  EntityMutationPolicyResolver,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticFormEntityRevisionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Static Form Entity revision policy" should {
    "select revision-preserving deduplication with a required observed revision" in {
      Given("the canonical Web Form mutation adapter profile and a hidden observed revision")
      val observed = EntityRevision.createC(7L).toOption

      When("the adapter profile is resolved into an Entity mutation execution policy")
      val selection = EntityMutationPolicyResolver.resolveC(
        EntityConcurrencyPolicy.Optimistic,
        adapterDefault = EntityMutationAdapterDefaults.forProfile(
          Some(EntityMutationAdapterDefaults.webFormProfile)
        )
      )
      val execution = selection.flatMap(
        _.executionPolicyC(
          EntityConcurrencyPolicy.Optimistic,
          observed
        )
      )

      Then("the form uses WriteIfChanged and requires the hidden observed revision")
      execution.toOption.map(_.writePolicy) shouldBe
        Some(EntityWritePolicy.WriteIfChanged)
      execution.toOption.map(_.preconditionPolicy) shouldBe
        Some(RevisionPreconditionPolicy.ObservedRequired)
      execution.toOption.flatMap(_.observedRevision) shouldBe observed
    }

    "reject a missing observed revision instead of exposing revision as business input" in {
      Given("the canonical Web Form mutation adapter profile without hidden revision metadata")
      val selection = EntityMutationPolicyResolver.resolveC(
        EntityConcurrencyPolicy.Optimistic,
        adapterDefault = EntityMutationAdapterDefaults.forProfile(
          Some(EntityMutationAdapterDefaults.webFormProfile)
        )
      )

      When("the adapter attempts to build its execution policy")
      val execution = selection.flatMap(
        _.executionPolicyC(
          EntityConcurrencyPolicy.Optimistic,
          None
        )
      )

      Then("the structured precondition validation rejects the request")
      execution.toOption shouldBe None
    }
  }
}
