package org.goldenport.cncf.component

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.backend.collaborator.CollaboratorFactory
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, EntityRuntimePlan, PartitionStrategy, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Apr. 24, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryWorkingSetPolicySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentFactory working-set policy resolution" should {
    "prefer code override over config and declarative descriptor" in {
      Given("a component with descriptor, config, and programmatic working-set policies")
      val cid = EntityCollectionId("sys", "sys", "default")
      val component = _component_with_policy(
        descriptorPolicy = Some(WorkingSetPolicy.Disabled),
        codePolicy = Some(WorkingSetPolicy.ResidentAll)
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          "cncf" -> ConfigurationValue.ObjectValue(Map(
            "entity" -> ConfigurationValue.ObjectValue(Map(
              "default" -> ConfigurationValue.ObjectValue(Map(
                "working_set_policy" -> ConfigurationValue.ObjectValue(Map(
                  "kind" -> ConfigurationValue.StringValue("recent"),
                  "duration" -> ConfigurationValue.StringValue("24h"),
                  "timestamp_field" -> ConfigurationValue.StringValue("postedAt")
                ))
              ))
            ))
          ))
        )),
        ConfigurationTrace.empty
      )
      val factory = new ComponentFactory(ComponentRepositorySpace(), CollaboratorFactory.empty, Vector.empty, Some(configuration))
      ConfigurationAccess.getString(configuration, "cncf.entity.default.working_set_policy.kind") shouldBe Some("recent")
      ConfigurationAccess.getString(configuration, "cncf.entity.default.working_set_policy.duration") shouldBe Some("24h")
      ConfigurationAccess.getString(configuration, "cncf.entity.default.working_set_policy.timestamp_field") shouldBe Some("postedAt")

      When("bootstrap selects the effective entity runtime plan")
      val bootstrapped = _bootstrap_collections(factory, component)
      val plan = bootstrapped.entity[Any](cid.name).descriptor.plan
      val descriptor = bootstrapped.componentDescriptors.flatMap(_.entityRuntimeDescriptors)
        .find(_.entityName == "default").get

      Then("the code policy wins and is marked as code-sourced")
      plan.workingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      plan.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Code)
      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      descriptor.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Code)
    }

    "apply config override when no code policy exists" in {
      Given("a component with declarative descriptor and config override")
      val cid = EntityCollectionId("sys", "sys", "default")
      val component = _component_with_policy(
        descriptorPolicy = Some(WorkingSetPolicy.Disabled),
        codePolicy = None
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          "cncf" -> ConfigurationValue.ObjectValue(Map(
            "entity" -> ConfigurationValue.ObjectValue(Map(
              "default" -> ConfigurationValue.ObjectValue(Map(
                "working_set_policy" -> ConfigurationValue.ObjectValue(Map(
                  "kind" -> ConfigurationValue.StringValue("recent"),
                  "duration" -> ConfigurationValue.StringValue("24h"),
                  "timestamp_field" -> ConfigurationValue.StringValue("postedAt")
                ))
              ))
            ))
          ))
        )),
        ConfigurationTrace.empty
      )
      val factory = new ComponentFactory(ComponentRepositorySpace(), CollaboratorFactory.empty, Vector.empty, Some(configuration))

      When("bootstrap selects the effective entity runtime plan")
      val bootstrapped = _bootstrap_collections(factory, component)
      val plan = bootstrapped.entity[Any](cid.name).descriptor.plan
      val descriptor = bootstrapped.componentDescriptors.flatMap(_.entityRuntimeDescriptors)
        .find(_.entityName == "default").get

      Then("the config policy overrides the declarative descriptor")
      plan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Recent(java.time.Duration.ofHours(24), "postedAt"))
      plan.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Config)
      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.Recent(java.time.Duration.ofHours(24), "postedAt"))
      descriptor.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Config)
    }
  }

  private def _bootstrap_collections(
    factory: ComponentFactory,
    component: Component
  ): Component = {
    val method = classOf[ComponentFactory].getDeclaredMethod("_bootstrap_collections", classOf[Component])
    method.setAccessible(true)
    method.invoke(factory, component).asInstanceOf[Component]
  }

  private def _component_with_policy(
    descriptorPolicy: Option[WorkingSetPolicy],
    codePolicy: Option[WorkingSetPolicy]
  ): Component = {
    val descriptors = Vector(
      ComponentDescriptor(
        name = Some("policy_spec"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "default",
            collectionId = EntityCollectionId("sys", "sys", "default"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 64,
            maxEntitiesPerPartition = 10000,
            workingSetPolicy = descriptorPolicy,
            workingSetPolicySource = descriptorPolicy.map(_ => WorkingSetPolicySource.Cml)
          )
        )
      )
    )
    val component = new Component() with EntityRuntimePlanProvider {
      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        codePolicy.toVector.map { policy =>
          EntityRuntimePlan[Any](
            entityName = "default",
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = None,
            workingSetPolicy = Some(policy),
            workingSetPolicySource = Some(WorkingSetPolicySource.Code),
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 64,
            maxEntitiesPerPartition = 10000
          )
        }
    }.withComponentDescriptors(descriptors)
    val core = Component.Core.create(
      name = "policy_spec",
      componentid = ComponentId("policy_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("policy_spec")),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("policy_spec"),
      core = core,
      origin = ComponentOrigin.Builtin,
      componentDescriptors = descriptors
    )
    component.initialize(params)
  }
}
