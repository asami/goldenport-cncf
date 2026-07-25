package org.goldenport.cncf.component

import org.goldenport.cncf.entity.{
  EntityRevisionModelKind,
  EntityRevisionRepresentation
}
import org.goldenport.cncf.entity.runtime.{
  EntityMemoryPolicy,
  EntityRuntimeDescriptor,
  EntityRuntimePlan,
  PartitionStrategy
}
import org.goldenport.cncf.backend.collaborator.CollaboratorFactory
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryRevisionBindingSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "ComponentFactory revision binding preflight" should {
    "install Embedded binding for a generated SimpleEntity declaration" in {
      Given("one generated SimpleEntity descriptor with Embedded revision")
      val component = _component(
        Vector("person"),
        Vector(
          _descriptor(
            "person",
            Some(EntityRevisionModelKind.SimpleEntity),
            Some(EntityRevisionRepresentation.Embedded)
          )
        )
      )

      When("the component collections are bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the Entity collection exposes the resolved Embedded binding")
      result.toOption shouldBe Some(component)
      component
        .entity[Any]("person")
        .descriptor
        .revisionBinding
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Embedded)
    }

    "install Detached binding only for an explicit non-SimpleEntity declaration" in {
      Given("a non-SimpleEntity model and an explicit detached collection declaration")
      val component = _component(
        Vector("document"),
        Vector(
          _descriptor(
            "document",
            Some(EntityRevisionModelKind.NonSimpleEntity),
            None
          ),
          _descriptor(
            "document",
            None,
            Some(EntityRevisionRepresentation.Detached)
          )
        )
      )

      When("the component collections are bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the Entity collection exposes the resolved Detached binding")
      result.toOption shouldBe Some(component)
      component
        .entity[Any]("document")
        .descriptor
        .revisionBinding
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Detached)
    }

    "keep an undeclared non-SimpleEntity outside revision management" in {
      Given("a non-SimpleEntity model with no collection revision declaration")
      val component = _component(
        Vector("legacy"),
        Vector(
          _descriptor(
            "legacy",
            Some(EntityRevisionModelKind.NonSimpleEntity),
            None
          )
        )
      )

      When("the component collections are bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the Entity collection has no revision binding")
      result.toOption shouldBe Some(component)
      component.entity[Any]("legacy").descriptor.revisionBinding shouldBe None
    }

    "isolate same-named Entity declarations by owning component" in {
      Given("two components that own different revision models for the same Entity name")
      val alphaentity = _descriptor(
        "shared",
        Some(EntityRevisionModelKind.SimpleEntity),
        Some(EntityRevisionRepresentation.Embedded)
      ).copy(
        collectionId =
          EntityCollectionId("test", "alpha", "shared")
      )
      val betaentity = _descriptor(
        "shared",
        Some(EntityRevisionModelKind.NonSimpleEntity),
        Some(EntityRevisionRepresentation.Detached)
      ).copy(
        collectionId =
          EntityCollectionId("test", "beta", "shared")
      )
      val alpha = _component(
        Vector("shared"),
        Vector(alphaentity),
        "revision_binding_alpha"
      )
      val beta = _component(
        Vector("shared"),
        Vector(betaentity),
        "revision_binding_beta"
      )
      val componentdescriptors = Vector(
        ComponentDescriptor(
          componentName = Some("revision_binding_alpha"),
          entityRuntimeDescriptors = Vector(alphaentity)
        ),
        ComponentDescriptor(
          componentName = Some("revision_binding_beta"),
          entityRuntimeDescriptors = Vector(betaentity)
        )
      )
      val factory = new ComponentFactory(
        ComponentRepositorySpace(),
        CollaboratorFactory.empty,
        componentdescriptors.flatMap(_.entityRuntimeDescriptors),
        None,
        RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock,
        componentdescriptors
      )

      When("one factory bootstraps both components")
      val alpharesult = factory.bootstrapC(alpha)
      val betaresult = factory.bootstrapC(beta)

      Then("each Entity receives only its owning component revision binding")
      alpharesult.toOption shouldBe Some(alpha)
      betaresult.toOption shouldBe Some(beta)
      alpha
        .entity[Any]("shared")
        .descriptor
        .revisionBinding
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Embedded)
      beta
        .entity[Any]("shared")
        .descriptor
        .revisionBinding
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Detached)

      And("runtime plan discovery preserves each component-owned collection")
      alpha.entity[Any]("shared").descriptor.collectionId shouldBe
        alphaentity.collectionId
      beta.entity[Any]("shared").descriptor.collectionId shouldBe
        betaentity.collectionId
    }

    "apply assembly revision declarations owned through a componentlet" in {
      Given("a generated componentlet model and its bundle-owned detached declaration")
      val modeldescriptor = _descriptor(
        "componentlet_entity",
        Some(EntityRevisionModelKind.NonSimpleEntity),
        None
      )
      val collectiondescriptor = _descriptor(
        "componentlet_entity",
        None,
        Some(EntityRevisionRepresentation.Detached)
      )
      val componentname = "revision_binding_componentlet"
      val component = _component(
        Vector("componentlet_entity"),
        Vector(modeldescriptor),
        componentname
      )
      val bundledescriptor = ComponentDescriptor(
        componentName = Some("revision_binding_bundle"),
        componentlets = Vector(
          ComponentletDescriptor(name = componentname)
        ),
        entityRuntimeDescriptors = Vector(collectiondescriptor)
      )
      val factory = new ComponentFactory(
        ComponentRepositorySpace(),
        CollaboratorFactory.empty,
        bundledescriptor.entityRuntimeDescriptors,
        None,
        RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock,
        Vector(bundledescriptor)
      )

      When("the componentlet collection is bootstrapped")
      val result = factory.bootstrapC(component)

      Then("the bundle declaration installs the componentlet Detached binding")
      result.toOption shouldBe Some(component)
      component
        .entity[Any]("componentlet_entity")
        .descriptor
        .revisionBinding
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Detached)
    }

    "reject a revision representation without model-kind evidence" in {
      Given("a detached collection declaration without generated model evidence")
      val component = _component(
        Vector("unknown"),
        Vector(
          _descriptor(
            "unknown",
            None,
            Some(EntityRevisionRepresentation.Detached)
          )
        )
      )
      val descriptorsbefore = component.componentDescriptors

      When("the component collections are bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("bootstrap fails before collection registration")
      result.toOption shouldBe None
      component.entitySpace.entityNames shouldBe empty
      component.collectionsBootstrapped shouldBe false

      And("preflight failure leaves component descriptor metadata untouched")
      component.componentDescriptors should be theSameInstanceAs descriptorsbefore
    }

    "reject missing SimpleEntity representation even when another declaration is complete" in {
      Given("one incomplete and one complete SimpleEntity model declaration")
      val component = _component(
        Vector("incomplete"),
        Vector(
          _descriptor(
            "incomplete",
            Some(EntityRevisionModelKind.SimpleEntity),
            None
          ),
          _descriptor(
            "incomplete",
            Some(EntityRevisionModelKind.SimpleEntity),
            Some(EntityRevisionRepresentation.Embedded)
          )
        )
      )

      When("the component collections are bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the complete declaration does not mask missing generated metadata")
      result.toOption shouldBe None
      component.entitySpace.entityNames shouldBe empty
      component.collectionsBootstrapped shouldBe false
    }

    "reject conflicting model and collection declarations" in {
      Given("a SimpleEntity model with a detached collection declaration")
      val component = _component(
        Vector("conflict"),
        Vector(
          _descriptor(
            "conflict",
            Some(EntityRevisionModelKind.SimpleEntity),
            Some(EntityRevisionRepresentation.Embedded)
          ),
          _descriptor(
            "conflict",
            None,
            Some(EntityRevisionRepresentation.Detached)
          )
        )
      )

      When("the component collections are bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("bootstrap fails without selecting a precedence winner")
      result.toOption shouldBe None
      component.entitySpace.entityNames shouldBe empty
      component.collectionsBootstrapped shouldBe false
    }

    "resolve every Entity before registering the first collection" in {
      Given("a valid first Entity followed by an invalid second Entity")
      val component = _component(
        Vector("valid", "invalid"),
        Vector(
          _descriptor(
            "valid",
            Some(EntityRevisionModelKind.SimpleEntity),
            Some(EntityRevisionRepresentation.Embedded)
          ),
          _descriptor(
            "invalid",
            None,
            Some(EntityRevisionRepresentation.Detached)
          )
        )
      )

      When("the component collections are bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the invalid declaration prevents all collection registration")
      result.toOption shouldBe None
      component.entitySpace.entityNames shouldBe empty
      component.collectionsBootstrapped shouldBe false
    }
  }

  private def _component(
    entitynames: Vector[String],
    descriptors: Vector[EntityRuntimeDescriptor],
    componentname: String = "revision_binding_spec"
  ): Component = {
    val component = new Component() with EntityRuntimePlanProvider {
      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        entitynames.map(_plan)
    }
    val componentid = ComponentId(componentname)
    val core = Component.Core.create(
      name = componentname,
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
    representation: Option[EntityRevisionRepresentation]
  ): EntityRuntimeDescriptor =
    EntityRuntimeDescriptor(
      entityName = entityname,
      collectionId =
        EntityCollectionId("test", "revision_binding", entityname),
      memoryPolicy = EntityMemoryPolicy.StoreOnly,
      partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
      maxPartitions = 4,
      maxEntitiesPerPartition = 100,
      revisionModelKind = modelkind,
      revisionRepresentation = representation
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
}
