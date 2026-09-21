package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 21, 2026
 * @version Sep. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedWorkflowAbiSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  import GeneratedWorkflowAbi.*

  "Generated workflow ABI admission" should {
    "admit the complete pinned WorkflowProducer declaration without runtime adoption" in {
      Given("the Cozy 62.3 WorkflowProducer declaration with source-correlated structure")
      val subsystem = TestComponentFactory.emptySubsystem("generated_workflow_abi_success")
      val definition = _definition()
      val component = _initialized_component(
        subsystem,
        new Component() with GeneratedWorkflowMetadataProvider {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(definition)
        }
      )

      When("ComponentFactory admits component-provided generated metadata")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the complete typed metadata is retained without workflow runtime registration")
      result.toOption shouldBe Some(component)
      component.admittedGeneratedWorkflowMetadata shouldBe Vector(definition)
      definition.workflow.identity shouldBe WorkflowIdentity("WorkflowProducer")
      definition.workflow.revision shouldBe WorkflowRevision("workflow-producer-v1")
      definition.compositeStateMachine.states.map(_.identity.value) shouldBe Vector("Pending", "Approved")
      definition.actions.map(_.identity.value) shouldBe Vector(
        "BuildProject",
        "RunTests",
        "ReviewChange",
        "CommitChanges"
      )
      definition.actions.foreach(_.kind shouldBe ActionKind.Operation)
      definition.actions.map(_.operation.key) shouldBe Vector(
        "WorkflowService.buildProject",
        "WorkflowService.runTests",
        "WorkflowService.reviewChange",
        "WorkflowService.commitChanges"
      )
      definition.actions.map(_.operation.inputType) shouldBe Vector.fill(4)(Some(TypeIdentity("ReviewContext")))
      definition.actions.map(_.operation.resultType) shouldBe Vector.fill(4)(Some(TypeIdentity("ReviewResult")))
      definition.requiredSpis.map(_.identity.value) shouldBe Vector("review-change-capability")
      definition.schemaShapes.map(_.identity).toSet shouldBe requiredSchemaShapes.keySet
      component.eventReception shouldBe None
      subsystem.workflowEngine.definitions shouldBe empty
    }

    "discover valid generated metadata from the factory when the component has no provider" in {
      Given("a Component whose factory alone supplies the complete pinned declaration")
      val definition = _definition()
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_workflow_abi_factory_success"),
        new Component() {},
        Some(new GeneratedMetadataFactory(Vector(definition)))
      )

      When("ComponentFactory bootstraps the component")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the factory fallback admits and retains only its metadata")
      result.toOption shouldBe Some(component)
      component.admittedGeneratedWorkflowMetadata shouldBe Vector(definition)
    }

    "prefer component generated metadata over factory generated metadata" in {
      Given("different complete declarations on a component and its factory")
      val componentdefinition = _definition(workflow = "ComponentWorkflow")
      val factorydefinition = _definition(workflow = "FactoryWorkflow")
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_workflow_abi_precedence"),
        new Component() with GeneratedWorkflowMetadataProvider {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(componentdefinition)
        },
        Some(new GeneratedMetadataFactory(Vector(factorydefinition)))
      )

      When("ComponentFactory bootstraps the component")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the component provider retains precedence over the factory fallback")
      result.toOption shouldBe Some(component)
      component.admittedGeneratedWorkflowMetadata shouldBe Vector(componentdefinition)
    }

    "fail closed for empty and malformed factory-only metadata" in {
      Given("factory-only providers with no declaration and with an unknown schema shape")
      val emptycomponent = _factory_component(Vector.empty)
      val malformedcomponent = _factory_component(Vector(
        _definition().copy(schemaShapes = _definition().schemaShapes :+
          SchemaShapeDescriptor("UnknownShape", Set("value"), _abi_source))
      ))

      When("ComponentFactory bootstraps each component")
      val emptyresult = new ComponentFactory().bootstrapC(emptycomponent)
      val malformedresult = new ComponentFactory().bootstrapC(malformedcomponent)

      Then("both failures stop bootstrap before metadata retention")
      _diagnostic(emptyresult).code shouldBe DiagnosticCode.MissingDefinitions
      _diagnostic(malformedresult).code shouldBe DiagnosticCode.UnknownSchemaShape
      emptycomponent.admittedGeneratedWorkflowMetadata shouldBe empty
      malformedcomponent.admittedGeneratedWorkflowMetadata shouldBe empty
      emptycomponent.collectionsBootstrapped shouldBe false
      malformedcomponent.collectionsBootstrapped shouldBe false
    }

    "fail closed for missing unknown or incompatible structure and schema shapes" in {
      Given("complete declarations whose required structural or ABI-schema facts are changed")
      val missingstates = _definition().copy(
        compositeStateMachine = _definition().compositeStateMachine.copy(states = Vector.empty)
      )
      val missingshape = _definition().copy(
        schemaShapes = _definition().schemaShapes.filterNot(_.identity == "Continuation")
      )
      val unknownshape = _definition().copy(
        schemaShapes = _definition().schemaShapes :+
          SchemaShapeDescriptor("Unexpected", Set("value"), _abi_source)
      )
      val incompatibleshape = _definition().copy(
        schemaShapes = _definition().schemaShapes.map {
          case shape if shape.identity == "ActionExecution" =>
            shape.copy(members = Set("Completed", "Failed"))
          case shape => shape
        }
      )

      When("ComponentFactory admits the altered declarations")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(missingstates)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(missingshape)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(unknownshape)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(incompatibleshape)))).code
      )

      Then("the stable diagnostics identify the structural and version-bound shape violations")
      actual shouldBe Vector(
        DiagnosticCode.MissingRequiredStructure,
        DiagnosticCode.MissingRequiredSchemaShape,
        DiagnosticCode.UnknownSchemaShape,
        DiagnosticCode.IncompatibleSchemaShape
      )
    }

    "fail closed when a Required SPI does not bind a declared Action and its Operation" in {
      Given("complete declarations with a missing action reference and an incompatible operation")
      val missingaction = _definition().copy(
        requiredSpis = Vector(_definition().requiredSpis.head.copy(
          actionIdentity = ActionIdentity("NotDeclared")
        ))
      )
      val mismatchedoperation = _definition().copy(
        requiredSpis = Vector(_definition().requiredSpis.head.copy(
          operation = _operation("differentOperation", _review_change_source)
        ))
      )

      When("ComponentFactory admits each Required SPI descriptor")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(missingaction)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(mismatchedoperation)))).code
      )

      Then("each descriptor fails with its stable binding diagnostic")
      actual shouldBe Vector(
        DiagnosticCode.RequiredSpiActionMismatch,
        DiagnosticCode.RequiredSpiOperationMismatch
      )
    }

    "fail closed for incompatible producer identity and duplicate action identity" in {
      Given("an unsupported producer declaration and otherwise complete duplicate actions")
      val unsupported = _definition().copy(
        producerAbiIdentity = ProducerAbiIdentity("workflow-producer-v2")
      )
      val duplicateactions = Vector(
        _definition(workflow = "ActionWorkflowOne"),
        _definition(workflow = "ActionWorkflowTwo").copy(
          requiredSpis = Vector(_definition().requiredSpis.head.copy(
            identity = RequiredSpiIdentity("duplicate-action-capability")
          ))
        )
      )

      When("each declaration set crosses ComponentFactory")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(unsupported)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(duplicateactions))).code
      )

      Then("compatibility and duplicate checks preserve their stable diagnostics")
      actual shouldBe Vector(
        DiagnosticCode.UnsupportedProducerAbi,
        DiagnosticCode.DuplicateActionIdentity
      )
    }

    "fail closed for incompatible Workflow revision, duplicate State, and duplicate schema-shape identity" in {
      Given("complete declarations with one incompatible revision and per-Workflow duplicate descriptors")
      val revisiondefinition = _definition()
      val incompatiblerevision = revisiondefinition.copy(
        workflow = revisiondefinition.workflow.copy(
          revision = WorkflowRevision("workflow-producer-v2")
        )
      )
      val statedefinition = _definition()
      val duplicatestate = statedefinition.compositeStateMachine.states.head
      val duplicatestates = statedefinition.copy(
        compositeStateMachine = statedefinition.compositeStateMachine.copy(
          states = statedefinition.compositeStateMachine.states :+ duplicatestate
        )
      )
      val shapedefinition = _definition()
      val duplicateshapes = shapedefinition.copy(
        schemaShapes = shapedefinition.schemaShapes :+ shapedefinition.schemaShapes.head
      )

      When("ComponentFactory admits each altered generated declaration")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(incompatiblerevision)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(duplicatestates)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(duplicateshapes)))).code
      )

      Then("each version or duplicate descriptor violation returns its stable diagnostic")
      actual shouldBe Vector(
        DiagnosticCode.UnsupportedWorkflowRevision,
        DiagnosticCode.DuplicateStateIdentity,
        DiagnosticCode.DuplicateSchemaShapeIdentity
      )
    }

    "not adopt legacy handwritten workflow definitions as generated metadata" in {
      Given("a legacy workflow definition without a generated metadata provider")
      val legacy = WorkflowDefinition(
        name = "legacy-workflow",
        registrations = Vector.empty
      )
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_workflow_abi_legacy"),
        new Component() {
          override def workflowDefinitions: Vector[WorkflowDefinition] = Vector(legacy)
        }
      )

      When("the ordinary component bootstrap runs")
      val result = new ComponentFactory().bootstrapC(component)

      Then("legacy workflow behavior remains separate from generated ABI admission")
      result.toOption shouldBe Some(component)
      component.workflowDefinitions shouldBe Vector(legacy)
      component.admittedGeneratedWorkflowMetadata shouldBe empty
    }
  }

  private final class GeneratedMetadataFactory(
    definitions: Vector[Definition]
  ) extends Component.Factory
    with GeneratedWorkflowMetadataProvider {
    override def generatedWorkflowDefinitions: Vector[Definition] = definitions

    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("test fixture factory does not create components")

    override protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      throw new UnsupportedOperationException("test fixture factory does not create component cores")
  }

  private def _provider_component(
    definitions: Vector[Definition]
  ): Component =
    _initialized_component(
      TestComponentFactory.emptySubsystem("generated_workflow_abi_provider"),
      new Component() with GeneratedWorkflowMetadataProvider {
        override def generatedWorkflowDefinitions: Vector[Definition] = definitions
      }
    )

  private def _factory_component(
    definitions: Vector[Definition]
  ): Component =
    _initialized_component(
      TestComponentFactory.emptySubsystem("generated_workflow_abi_factory"),
      new Component() {},
      Some(new GeneratedMetadataFactory(definitions))
    )

  private def _initialized_component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component,
    componentfactory: Option[Component.Factory] = None
  ): Component = {
    val componentid = ComponentId("org.goldenport.cncf.test.GeneratedWorkflowAbiSpec")
    val core = componentfactory match {
      case Some(factory) =>
        Component.Core.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = ComponentInstanceId.default(componentid),
          protocol = Protocol.empty,
          factory = factory
        )
      case None =>
        Component.Core.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = ComponentInstanceId.default(componentid),
          protocol = Protocol.empty
        )
    }
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    ))
  }

  private def _definition(
    workflow: String = "WorkflowProducer"
  ): Definition = {
    val root = SourceLocation(
      "src/test/resources/modeler/skill-driven-workflow-producer.cml",
      1
    )
    val definition = root.copy(line = 20)
    val composite = root.copy(line = 23)
    val states = Vector(
      StateDescriptor(StateIdentity("Pending"), root.copy(line = 31)),
      StateDescriptor(StateIdentity("Approved"), root.copy(line = 33))
    )
    val actionnames = Vector(
      "BuildProject" -> "buildProject",
      "RunTests" -> "runTests",
      "ReviewChange" -> "reviewChange",
      "CommitChanges" -> "commitChanges"
    )
    val actions = actionnames.zipWithIndex.map { case ((name, operation), index) =>
      val source = root.copy(line = 46 + index * 5)
      ActionDescriptor(
        ActionIdentity(name),
        ActionKind.Operation,
        _operation(operation, source),
        Some("review.subject"),
        source
      )
    }
    val reviewaction = actions.find(_.identity == ActionIdentity("ReviewChange")).get
    Definition(
      producerAbiIdentity = acceptedProducerAbiIdentity,
      workflowAbiIdentity = acceptedWorkflowAbiIdentity,
      bootstrapSchemaIdentity = acceptedBootstrapSchemaIdentity,
      producerRevision = acceptedProducerRevision,
      fixtureSha256 = acceptedFixtureSha256,
      workflow = WorkflowDescriptor(
        WorkflowIdentity(workflow),
        WorkflowRevision("workflow-producer-v1"),
        WorkflowSourceCorrelation(root, definition)
      ),
      compositeStateMachine = CompositeStateMachineDescriptor(
        CompositeStateMachineIdentity("review"),
        StateMachineIdentity("ReviewLifecycle"),
        states,
        composite
      ),
      actions = actions,
      requiredSpis = Vector(RequiredSpiDescriptor(
        RequiredSpiIdentity("review-change-capability"),
        reviewaction.identity,
        reviewaction.operation,
        root.copy(line = 68),
        reviewaction.sourceLocation
      )),
      schemaShapes = requiredSchemaShapes.toVector.sortBy(_._1).map {
        case (identity, members) => SchemaShapeDescriptor(identity, members, _abi_source)
      }
    )
  }

  private def _operation(
    operation: String,
    source: SourceLocation
  ): OperationDescriptor =
    OperationDescriptor(
      ServiceIdentity("WorkflowService"),
      OperationIdentity(operation),
      Some(TypeIdentity("ReviewContext")),
      Some(TypeIdentity("ReviewResult")),
      source
    )

  private val _review_change_source = SourceLocation(
    "src/test/resources/modeler/skill-driven-workflow-producer.cml",
    56
  )

  private val _abi_source = SourceLocation(
    "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala",
    1
  )

  private def _diagnostic(
    result: Consequence[Component]
  ): Diagnostic =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.getException match {
          case Some(exception: GeneratedWorkflowAbiAdmissionException) =>
            exception.diagnostic
          case other =>
            fail(s"expected generated workflow admission diagnostic but got $other")
        }
      case _ =>
        fail("expected generated workflow admission to fail")
    }
}
