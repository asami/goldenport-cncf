package org.goldenport.cncf.entity

import org.goldenport.cncf.backend.collaborator.CollaboratorFactory
import org.goldenport.cncf.component.{
  Component,
  ComponentDescriptor,
  ComponentFactory,
  ComponentId,
  ComponentInit,
  ComponentInstanceId,
  ComponentOrigin,
  EntityRuntimePlanProvider
}
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.entity.runtime.{
  EntityMemoryPolicy,
  EntityRuntimeDescriptor,
  EntityRuntimePlan,
  PartitionStrategy
}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Jul. 25, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityConcurrencyPolicySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Entity concurrency policy assembly" should {
    "resolve collection override, Entity declaration, and default deterministically" in {
      Given(
        "Phase 50 ER-04; Entities with a collection override, an Entity declaration, and no declaration"
      )
      val overridecomponent = _component(
        "policy_override",
        Vector(
          _descriptor(
            "person",
            Some(EntityRevisionModelKind.SimpleEntity),
            Some(EntityConcurrencyPolicy.Optimistic)
          ),
          _descriptor(
            "person",
            None,
            Some(EntityConcurrencyPolicy.None)
          )
        )
      )
      val entitycomponent = _component(
        "policy_entity",
        Vector(
          _descriptor(
            "person",
            Some(EntityRevisionModelKind.SimpleEntity),
            Some(EntityConcurrencyPolicy.None)
          )
        )
      )
      val defaultcomponent = _component(
        "policy_default",
        Vector(
          _descriptor(
            "person",
            Some(EntityRevisionModelKind.SimpleEntity),
            None
          )
        )
      )

      When("ComponentFactory binds each effective runtime plan")
      val overrideresult =
        new ComponentFactory().bootstrapC(overridecomponent)
      val entityresult =
        new ComponentFactory().bootstrapC(entitycomponent)
      val defaultresult =
        new ComponentFactory().bootstrapC(defaultcomponent)

      Then("collection wins over Entity and the undeclared default is no OCC")
      overrideresult.toOption shouldBe Some(overridecomponent)
      entityresult.toOption shouldBe Some(entitycomponent)
      defaultresult.toOption shouldBe Some(defaultcomponent)
      _effective_policy(overridecomponent) shouldBe
        EntityConcurrencyPolicy.None
      _effective_policy(entitycomponent) shouldBe
        EntityConcurrencyPolicy.None
      _effective_policy(defaultcomponent) shouldBe
        EntityConcurrencyPolicy.None
    }

    "reject conflicting declarations at one precedence level" in {
      Given(
        "Phase 50 ER-04; two different collection-level policies for one Entity"
      )
      val component = _component(
        "policy_conflict",
        Vector(
          _descriptor(
            "person",
            Some(EntityRevisionModelKind.SimpleEntity),
            None
          ),
          _descriptor(
            "person",
            None,
            Some(EntityConcurrencyPolicy.None)
          ),
          _descriptor(
            "person",
            None,
            Some(EntityConcurrencyPolicy.Optimistic)
          )
        )
      )

      When("ComponentFactory resolves the collection policy")
      val result = new ComponentFactory().bootstrapC(component)

      Then("assembly fails without selecting an arbitrary winner")
      result.toOption shouldBe None
      component.entitySpace.entityNames shouldBe empty
      component.collectionsBootstrapped shouldBe false
    }

    "parse only explicit canonical or documented compatibility values" in {
      Given("Phase 50 ER-04; policy text at the descriptor boundary")

      When("known and unknown values are parsed")
      val none = EntityConcurrencyPolicy.parseC("none")
      val optimistic = EntityConcurrencyPolicy.parseC("OCC")
      val invalid = EntityConcurrencyPolicy.parseC("disabled")

      Then("None remains explicit and unknown policy fails structurally")
      EntityConcurrencyPolicy.default shouldBe
        EntityConcurrencyPolicy.None
      none.toOption shouldBe Some(EntityConcurrencyPolicy.None)
      optimistic.toOption shouldBe Some(
        EntityConcurrencyPolicy.Optimistic
      )
      invalid.toOption shouldBe None
    }

    "retain the strictest assembled and mutation-attempt policy" in {
      Given(
        "assembled and per-attempt concurrency policies in every strictness combination"
      )

      When("the provider derives the effective mutation policy")
      val none = EntityConcurrencyPolicy.effectivePolicy(
        EntityConcurrencyPolicy.None,
        EntityConcurrencyPolicy.None
      )
      val assembledoptimistic = EntityConcurrencyPolicy.effectivePolicy(
        EntityConcurrencyPolicy.Optimistic,
        EntityConcurrencyPolicy.None
      )
      val attemptoptimistic = EntityConcurrencyPolicy.effectivePolicy(
        EntityConcurrencyPolicy.None,
        EntityConcurrencyPolicy.Optimistic
      )

      Then("an attempt may strengthen but never weaken the assembled policy")
      none shouldBe EntityConcurrencyPolicy.None
      assembledoptimistic shouldBe EntityConcurrencyPolicy.Optimistic
      attemptoptimistic shouldBe EntityConcurrencyPolicy.Optimistic
    }

    "require revision representation only for explicit optimistic concurrency" in {
      Given(
        "a non-Simple Entity without revision representation and two concurrency declarations"
      )
      val ordinary = _component(
        "ordinary_non_simple",
        Vector(
          _descriptor(
            "person",
            None,
            Some(EntityConcurrencyPolicy.None)
          )
        )
      )
      val optimistic = _component(
        "optimistic_non_simple",
        Vector(
          _descriptor(
            "person",
            None,
            Some(EntityConcurrencyPolicy.Optimistic)
          )
        )
      )

      When("ComponentFactory admits each runtime contract")
      val ordinaryresult = new ComponentFactory().bootstrapC(ordinary)
      val optimisticresult = new ComponentFactory().bootstrapC(optimistic)

      Then("ordinary mutation is admitted and explicit OCC fails deterministically")
      ordinaryresult.toOption shouldBe Some(ordinary)
      optimisticresult.toOption shouldBe None
    }
  }

  private def _component(
    componentname: String,
    descriptors: Vector[EntityRuntimeDescriptor]
  ): Component = {
    val component = new Component() with EntityRuntimePlanProvider {
      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        Vector(_plan("person"))
    }
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(componentname)
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    component.initialize(
      ComponentInit(
        subsystem =
          TestComponentFactory.emptySubsystem(componentname),
        core = core,
        origin = ComponentOrigin.Builtin,
        componentDescriptors = Vector(
          ComponentDescriptor(
            componentName = Some(componentname),
            entityRuntimeDescriptors = descriptors
          )
        )
      )
    )
  }

  private def _descriptor(
    entityname: String,
    modelkind: Option[EntityRevisionModelKind],
    policy: Option[EntityConcurrencyPolicy]
  ): EntityRuntimeDescriptor =
    EntityRuntimeDescriptor(
      entityName = entityname,
      collectionId =
        EntityCollectionId("test", "entity_policy", entityname),
      memoryPolicy = EntityMemoryPolicy.StoreOnly,
      partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
      maxPartitions = 4,
      maxEntitiesPerPartition = 100,
      revisionModelKind = modelkind,
      revisionRepresentation =
        modelkind.map(_ => EntityRevisionRepresentation.Embedded),
      concurrencyPolicy = policy
    )

  private def _plan(
    entityname: String
  ): EntityRuntimePlan[Any] =
    EntityRuntimePlan[Any](
      entityName = entityname,
      memoryPolicy = EntityMemoryPolicy.StoreOnly,
      workingSet = None,
      partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
      maxPartitions = 4,
      maxEntitiesPerPartition = 100
    )

  private def _effective_policy(
    component: Component
  ): EntityConcurrencyPolicy =
    component
      .entity[Any]("person")
      .descriptor
      .plan
      .concurrencyPolicy
}
