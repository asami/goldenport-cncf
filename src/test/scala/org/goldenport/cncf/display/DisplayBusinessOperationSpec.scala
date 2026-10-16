package org.goldenport.cncf.display

import logicalui.DisplayServiceReadFixture
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentFactory}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec.{ParameterDefinition, ServiceDefinitionGroup}
import org.goldenport.schema.{ValueDomain, XString}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class DisplayBusinessOperationSpec extends AnyWordSpec with DisplayBusinessOperationFixture.Vocabulary {
  import DisplayBusinessOperation.*
  import DisplayBusinessOperationFixture.*

  "Business descriptor admission" should {
    "exact scoped declaration and target metadata" which {
      "BA1 retain declaration order, shared keys, separate scopes and the actual request parameter contract without execution" in {
        Given("an initialized component with ordinary review/alternate Operations and independently declared parameter metadata")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = new SourceComponent()
          initialize(component, subsystem, protocol(Vector(target, new TargetOperation(operationName = "alternate"))))
          When("the complete scoped bindings are admitted in reverse registration order")
          val admitted = registry(component, selected = bindings().reverse).TAKE
          val list = admitted.describeC(resourceType, "collecting-resources").TAKE
          val detail = admitted.describeC(resourceType, "capture-detail").TAKE
          Then("screen actions precede item actions in declaration order with canonical target and independent ordered input metadata")
          list.actions.map(action => action.scope -> action.id) shouldBe Vector(
            ActionScope.Screen -> "review:0", ActionScope.Screen -> "review:1",
            ActionScope.Item -> "review:0", ActionScope.Item -> "review:1")
          list.actions.map(_.bindingId).distinct shouldBe Vector("business:shared")
          (list.actions ++ detail.actions).foreach { action =>
            action.target shouldBe OperationReference(component.componentId, "business", "review")
            action.parameters shouldBe expectedParameters
          }
          detail.actions.map(_.bindingId) shouldBe Vector("ess.request-review")
          list.actions.head.label shouldBe "Review 日本 Ω0"
          idle(component, target)
        }
      }
      "BA1-empty expose empty metadata for an unselected declared screen while preserving its exact source" in {
        Given("a complete active catalog with a Business binding only on Detail")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = new SourceComponent()
          initialize(component, subsystem, protocol(Vector(target)))
          When("the registry describes the declared List and rejects a foreign resource")
          val admitted = registry(component, selected = bindings().takeRight(1)).TAKE
          val descriptor = admitted.describeC(resourceType, "collecting-resources").TAKE
          val foreign = admitted.describeC("foreign-resource", "collecting-resources")
          Then("the List descriptor is empty and no source or callback is evaluated for either selection")
          descriptor.actions shouldBe empty
          descriptor.source.componentId shouldBe component.componentId
          descriptor.source.resourceType shouldBe resourceType
          expectFailure(foreign, "SELECTION_MISMATCH", "request.resourceType")
          idle(component, target)
        }
      }
    }
    "atomic closed registration" which {
      "BA2 reject malformed locator and target candidates as typed redacted failures" in {
        Given("one valid declared locator and a valid ordinary target before any publication")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = new SourceComponent()
          initialize(component, subsystem, protocol(Vector(target, new TargetOperation(operationName = "alternate"))))
          val originalprotocol = component.protocol
          val originalservices = component.services
          val first = bindings().head
          val candidates = Vector(
            (null, "ELEMENT_INVALID", "registration"),
            (Vector(null), "ELEMENT_INVALID", "bindings[0]"),
            (Vector(first.copy(scope = null)), "ELEMENT_INVALID", "bindings[0]"),
            (Vector(first.copy(screenId = "")), "ID_INVALID", "bindings[0]"),
            (Vector(first.copy(actionId = " review:0 ")), "ID_INVALID", "bindings[0]"),
            (Vector(first.copy(screenId = "unknown")), "SCREEN_UNDECLARED", "bindings[0].screenId"),
            (Vector(first.copy(actionId = "missing")), "ACTION_UNDECLARED", "bindings[0].actionId"),
            (Vector(bindings().last.copy(scope = ActionScope.Item)), "ACTION_UNDECLARED", "bindings[0].actionId"),
            (Vector(first, first), "BINDING_DUPLICATE", "bindings[1]"),
            (Vector(first.copy(serviceName = "display")), "TARGET_INVALID", "bindings[0].target"),
            (Vector(first.copy(serviceName = "business ")), "TARGET_INVALID", "bindings[0].target"),
            (Vector(first.copy(serviceName = "https://private.invalid/token")), "TARGET_INVALID", "bindings[0].target"),
            (Vector(first.copy(operationName = "foreign.business.review")), "TARGET_INVALID", "bindings[0].target.operation"),
            (Vector(first.copy(serviceName = "absent")), "TARGET_INVALID", "bindings[0].target.service"),
            (Vector(first.copy(operationName = "missing")), "TARGET_INVALID", "bindings[0].target.operation"),
            (Vector(first, bindings()(1).copy(operationName = "alternate")), "TARGET_CONFLICT", "bindings[1]"))
          candidates.foreach { case (selected, code, path) =>
            When("pure admission encounters the malformed complete candidate")
            val rejected = registry(component, selected = selected)
            Then("the whole registry fails with the stable typed path and no private input or executable effects")
            expectFailure(rejected, code, path)
            rejected.display should not include "private.invalid"
            component.displayRuntime shouldBe None
            component.collectionsBootstrapped shouldBe false
            (component.protocol eq originalprotocol) shouldBe true
            (component.services eq originalservices) shouldBe true
            idle(component, target)
          }
        }
      }
      "BA2-parameters reject every incomplete or duplicate canonical request parameter and ambiguous Operation without collapsing input" in {
        Given("canonical target definitions containing null names, kinds, domains, datatypes, multiplicities or repeated names")
        withSubsystem { subsystem =>
          val badparameters: Vector[List[ParameterDefinition]] = Vector(null, List(null),
            List(parameters.head.copy(content = null)), List(parameters.head.copy(kind = null)),
            List(parameters.head.copy(domain = null)), List(parameters.head.copy(domain = ValueDomain(null, org.goldenport.schema.Multiplicity.One))),
            List(parameters.head.copy(domain = ValueDomain(XString, null))), List(parameters.head, parameters.head))
          badparameters.zipWithIndex.foreach { case (values, index) =>
            val target = new TargetOperation(values)
            val component = new SourceComponent()
            initialize(component, subsystem, protocol(Vector(target)), name = "InvalidParameters" + index)
            When("the chosen Operation's actual request contract is admitted")
            val result = registry(component)
            Then("incomplete canonical input fails through a descriptor diagnostic before callbacks")
            result shouldBe a[Consequence.Failure[?]]
            result match {
              case failure: Consequence.Failure[?] =>
                failure.conclusion.observation.cause.getException.get shouldBe a[DescriptorException]
              case _ => fail("expected incomplete parameter rejection")
            }
            idle(component, target)
          }
          for (duplicate <- Vector("service", "operation")) {
            Given("two exact target candidates at one ordinary selector")
            val target = new TargetOperation()
            val base = protocol(Vector(target, new TargetOperation()))
            val ambiguous = if (duplicate == "service") base.copy(services = ServiceDefinitionGroup(
              Vector(base.services.services.head, base.services.services.head))) else base
            val component = new SourceComponent()
            initialize(component, subsystem, ambiguous, name = "Ambiguous" + duplicate)
            When("a selected Action references the ambiguous selector")
            val rejected = registry(component)
            Then("neither the first nor last candidate is selected and target callbacks remain idle")
            expectFailure(rejected, "TARGET_INVALID", "bindings[0].target." + duplicate)
            idle(component, target)
          }
        }
      }
      "BA2-resources reject null missing duplicate and foreign resource correspondence before admission" in {
        Given("admitted definitions and an initialized target component")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = new SourceComponent()
          initialize(component, subsystem, protocol(Vector(target)))
          val definitions = GeneratedLogicalUiAbi.admitC(DisplayBusinessOperationFixture.definitions()).TAKE
          val candidates = Vector(null, Vector(null), resources.take(1), resources :+ resources.head,
            resources.updated(1, resources(1).copy(resourceType = "foreign")),
            resources.updated(1, resources(1).copy(screenId = "foreign")),
            resources.updated(0, resources.head.copy(resourceType = " type ")))
          candidates.foreach { value =>
            When("the complete resource-to-screen join is admitted")
            val result = DisplayBusinessOperation._bind_c(component, definitions, value, bindings())
            Then("the whole candidate fails without publication or any target/Browser effect")
            result shouldBe a[Consequence.Failure[?]]
            idle(component, target)
          }
        }
      }
    }
    "provider selection and prepared publication" which {
      "BA3 choose a component provider once, use the factory only when absent and publish only a complete service" in {
        Given("a component provider competing with a throwing factory and a second component using factory fallback")
        withSubsystem { subsystem =>
          val fallback = new BindingFactory(_ => throw new IllegalStateException("unused fallback"))
          val selected = new BusinessComponent()
          val target = new TargetOperation()
          initialize(selected, subsystem, protocol(Vector(target)), Some(fallback))
          val factory = new BindingFactory(_ => bindings())
          val absent = new SourceComponent()
          initialize(absent, subsystem, protocol(Vector(new TargetOperation())), Some(factory), "FactoryBusinessDescriptor")
          When("ordinary ComponentFactory bootstrap prepares and publishes each complete registration")
          val results = Vector(selected, absent).map(new ComponentFactory().bootstrapC)
          Then("only the selected getter runs and metadata is additive to query/read with no target or Browser execution")
          results.foreach(_ shouldBe a[Consequence.Success[?]])
          selected.reads shouldBe 1
          fallback.reads shouldBe 0
          factory.reads shouldBe 1
          Vector(selected, absent).foreach { component =>
            component.protocol.services.services.find(_.name == "display").get.operations.operations.toVector.map(_.name) shouldBe
              Vector("query", "read", "describeBusiness")
          }
          idle(selected, target)
        }
      }
      "BA3-failure contain a selected null or throw and never retry a valid competing provider" in {
        for (read <- Vector[Component => Vector[Binding]](_ => null, _ => throw new IllegalStateException("private provider token"),
          _ => bindings() :+ bindings().head)) {
          Given("a selected malformed provider and a valid factory fallback before Display publication")
          withSubsystem { subsystem =>
            val fallback = new BindingFactory(_ => bindings())
            val component = new BusinessComponent(read)
            val target = new TargetOperation()
            initialize(component, subsystem, protocol(Vector(target)), Some(fallback))
            val original = component.protocol
            val services = component.services
            When("normal bootstrap reads the selected provider once")
            val rejected = new ComponentFactory().bootstrapC(component)
            Then("a redacted structured failure preserves original protocol/service references and publishes no Display runtime")
            rejected shouldBe a[Consequence.Failure[?]]
            rejected.display should not include "private provider token"
            component.reads shouldBe 1
            fallback.reads shouldBe 0
            (component.protocol eq original) shouldBe true
            (component.services eq services) shouldBe true
            component.displayRuntime shouldBe None
            component.collectionsBootstrapped shouldBe false
            idle(component, target)
          }
        }
      }
      "BA3-legacy preserve absent and empty-provider selectors and exact existing read payloads" in {
        Given("otherwise identical legacy and empty-provider components")
        withSubsystem { subsystem =>
          val legacy = new SourceComponent()
          val emptyprovider = new BusinessComponent(_ => Vector.empty)
          val target = new TargetOperation()
          initialize(legacy, subsystem, protocol(Vector(target)))
          initialize(emptyprovider, subsystem, protocol(Vector(new TargetOperation())), name = "EmptyBusinessDescriptor")
          When("both ordinary bootstraps complete without any Business bindings")
          Vector(legacy, emptyprovider).foreach(new ComponentFactory().bootstrapC(_).TAKE)
          Then("only the old query/read selectors exist and no target or source has executed")
          Vector(legacy, emptyprovider).foreach { component =>
            component.protocol.services.services.find(_.name == "display").get.operations.operations.toVector.map(_.name) shouldBe Vector("query", "read")
          }
          idle(legacy, target)
          emptyprovider.reads shouldBe 1
        }
        Given("the existing independent public read fixture and the equivalent new-provider fixture")
        val legacy = DisplayServiceReadFixture.open(Vector(logicalui.TypedDisplayProjectionFixture.captureC(1, 7).TAKE))
        val business = open()
        try {
          When("both read the same canonical Detail through their unchanged read Operations")
          val oldpayload = json(legacy.execute(legacy.request("read", "capture-detail", Some(business.component.row.resource.entityId))))
          val newpayload = json(business.execute(DisplayServiceReadFixture.request(business.component, "read", "capture-detail",
            Some(business.component.row.resource.entityId))))
          Then("their Display model payloads are identical and retain the existing closed wire schema")
          oldpayload.hcursor.downField("model").focus shouldBe newpayload.hcursor.downField("model").focus
          oldpayload.hcursor.get[String]("schema") shouldBe newpayload.hcursor.get[String]("schema")
          business.target.requests shouldBe 0
        } finally { legacy.close(); business.close() }
      }
    }
  }
}
