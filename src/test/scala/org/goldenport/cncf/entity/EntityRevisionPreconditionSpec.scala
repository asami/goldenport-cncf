package org.goldenport.cncf.entity

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Jul. 25, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityRevisionPreconditionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Entity revision precondition policy" should {
    "admit the valid concurrency matrix and reject its contradictory row" in {
      Given(
        "Phase 50 ER-06; Managed and ObservedRequired selections under both concurrency policies"
      )
      val managed = EntityMutationPolicySelection(
        EntityWritePolicy.AlwaysWrite,
        RevisionPreconditionPolicy.Managed
      )
      val observed = EntityMutationPolicySelection(
        EntityWritePolicy.WriteIfChanged,
        RevisionPreconditionPolicy.ObservedRequired
      )

      When("each selection is validated against its concurrency policy")
      val optimisticmanaged =
        managed.validateC(EntityConcurrencyPolicy.Optimistic)
      val optimisticobserved =
        observed.validateC(EntityConcurrencyPolicy.Optimistic)
      val nonemanaged =
        managed.validateC(EntityConcurrencyPolicy.None)
      val noneobserved =
        observed.validateC(EntityConcurrencyPolicy.None)

      Then("only None plus ObservedRequired is rejected")
      optimisticmanaged.toOption shouldBe Some(managed)
      optimisticobserved.toOption shouldBe Some(observed)
      nonemanaged.toOption shouldBe Some(managed)
      noneobserved.toOption shouldBe None
    }

    "resolve route, operation, adapter, and framework defaults by dimension" in {
      Given(
        "Phase 50 ER-06; independent write and precondition declarations at each metadata layer"
      )
      val route = EntityMutationPolicyDeclaration(
        preconditionPolicy =
          Some(RevisionPreconditionPolicy.ObservedRequired)
      )
      val operation = EntityMutationPolicyDeclaration(
        writePolicy = Some(EntityWritePolicy.AlwaysWrite),
        preconditionPolicy = Some(RevisionPreconditionPolicy.Managed)
      )

      When("the resolver combines explicit route, operation, and Web/Form adapter metadata")
      val selected = EntityMutationPolicyResolver.resolveC(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
        routeBinding = route,
        operationDeclaration = operation,
        adapterDefault = EntityMutationAdapterDefaults.webFormUpdate
      )
      val webdefault = EntityMutationPolicyResolver.resolveC(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
        adapterDefault = EntityMutationAdapterDefaults.webFormUpdate
      )
      val restdefault = EntityMutationPolicyResolver.resolveC(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
        adapterDefault =
          EntityMutationAdapterDefaults.idempotentRestUpdate
      )
      val coredefault = EntityMutationPolicyResolver.resolveC(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
      )

      Then("route wins per field, followed by operation, adapter, and framework fallback")
      selected.toOption shouldBe Some(
        EntityMutationPolicySelection(
          EntityWritePolicy.AlwaysWrite,
          RevisionPreconditionPolicy.ObservedRequired
        )
      )
      webdefault.toOption shouldBe Some(
        EntityMutationPolicySelection(
          EntityWritePolicy.WriteIfChanged,
          RevisionPreconditionPolicy.ObservedRequired
        )
      )
      restdefault.toOption shouldBe Some(
        EntityMutationPolicySelection(
          EntityWritePolicy.WriteIfChanged,
          RevisionPreconditionPolicy.Managed
        )
      )
      coredefault.toOption shouldBe Some(
        EntityMutationPolicySelection(
          EntityWritePolicy.AlwaysWrite,
          RevisionPreconditionPolicy.Managed
        )
      )
    }

    "require observed metadata only when constructing one strict mutation attempt" in {
      Given(
        "Phase 50 ER-06; one resolved strict selection and one observed transport revision"
      )
      val selection = EntityMutationPolicySelection(
        EntityWritePolicy.WriteIfChanged,
        RevisionPreconditionPolicy.ObservedRequired
      )
      val observed = _revision(12L)

      When("execution policy is constructed with and without ingress metadata")
      val admitted = selection.executionPolicyC(
        EntityConcurrencyPolicy.Optimistic,
        Some(observed)
      )
      val missing = selection.executionPolicyC(
        EntityConcurrencyPolicy.Optimistic,
        None
      )

      Then("the admitted attempt retains its one observed base and missing metadata fails structurally")
      admitted.map(_.observedRevision).toOption shouldBe
        Some(Some(observed))
      missing.toOption shouldBe None
    }

    "select optimistic concurrency only for an observed adapter attempt" in {
      Given(
        "an ordinary None-policy collection and managed plus observed adapter preconditions"
      )

      When("each adapter resolves the concurrency policy for its mutation attempt")
      val managed =
        EntityMutationAdapterDefaults.effectiveConcurrencyPolicy(
          EntityConcurrencyPolicy.None,
          RevisionPreconditionPolicy.Managed
        )
      val observed =
        EntityMutationAdapterDefaults.effectiveConcurrencyPolicy(
          EntityConcurrencyPolicy.None,
          RevisionPreconditionPolicy.ObservedRequired
        )

      Then("ordinary managed mutation stays None while strict observed mutation opts into OCC")
      managed shouldBe EntityConcurrencyPolicy.None
      observed shouldBe EntityConcurrencyPolicy.Optimistic
    }

    "parse the canonical precondition vocabulary" in {
      Given("Phase 50 ER-06; precondition text at a metadata boundary")

      When("known and unknown values are parsed")
      val managed = RevisionPreconditionPolicy.parseC("managed")
      val observed =
        RevisionPreconditionPolicy.parseC("observed_required")
      val invalid = RevisionPreconditionPolicy.parseC("optional")

      Then("Managed remains default and unknown policy fails structurally")
      RevisionPreconditionPolicy.default shouldBe
        RevisionPreconditionPolicy.Managed
      managed.toOption shouldBe Some(RevisionPreconditionPolicy.Managed)
      observed.toOption shouldBe Some(
        RevisionPreconditionPolicy.ObservedRequired
      )
      invalid.toOption shouldBe None
    }
  }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision
      .createC(value)
      .toOption
      .getOrElse(fail(s"valid EntityRevision expected: $value"))
}
