package org.goldenport.cncf.projection

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.component._
import org.goldenport.cncf.statemachine._
import org.goldenport.cncf.testutil.TestComponentFactory
import io.circe.parser.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 *  version Mar. 25, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "StateMachineProjection" should {
    "project deterministic transitions and guard shape for component target" in {
      Given("a component with structural and history transition metadata")
      val component = _component_with_rules()

      When("the component state-machine projection is requested")
      val rec = StateMachineProjection.project(component, Some(component.name))
      val map = rec.asMap

      Then("the projection includes deterministic transition and history metadata")
      map.get("type") shouldBe Some("statemachine")
      map.get("targetType") shouldBe Some("component")
      map.get("name") shouldBe Some(component.name)

      val transitions = _records(map("transitions"))
      transitions.size shouldBe 2

      val first = transitions(0).asMap
      val second = transitions(1).asMap

      first.get("event") shouldBe Some("save")
      first.get("priority") shouldBe Some(1)
      _record(first("guard")).asMap.get("kind") shouldBe Some("ref")

      second.get("event") shouldBe Some("update")
      second.get("priority") shouldBe Some(2)
      second.get("machine") shouldBe Some("lifecycle")
      second.get("stateField") shouldBe Some("status")
      second.get("fromState") shouldBe Some("Draft")
      second.get("toState") shouldBe Some("Published")
      _record(second("guard")).asMap.get("kind") shouldBe Some("expression")
      second.get("historyComposite") shouldBe Some("Review")
      second.get("historyField") shouldBe Some("lifecycleHistory")
      second.get("historyFallbackLeaf") shouldBe Some("Pending")
      _record(second("actions")).asMap.get("transition") shouldBe Some(2)
    }

    "project state machine definitions into states/events" in {
      Given("a component definition with a named persistent history field")
      val component = _component_with_definitions()

      When("the state-machine definition projection is requested")
      val rec = StateMachineProjection.project(component, Some(component.name))
      val map = rec.asMap

      Then("the definition surface retains named history metadata")
      map.get("states") shouldBe Some(Vector("Draft", "Published"))
      map.get("events") shouldBe Some(Vector("publish"))

      val definitions = _records(map("definitions"))
      definitions.size shouldBe 1
      definitions.head.asMap.get("name") shouldBe Some("lifecycle")
      definitions.head.asMap.get("states") shouldBe Some(Vector("Draft", "Published"))
      definitions.head.asMap.get("events") shouldBe Some(Vector("publish"))
      definitions.head.asMap.get("historyField") shouldBe Some("lifecycleHistory")
      _records(definitions.head.asMap("historyComposites")).head.asMap.get("fallbackLeaf") shouldBe Some("Pending")
    }

    "project typed normalized definitions in declaration order through Record JSON" in {
      Given("a component with a typed generated state-machine definition")
      val component = _component_with_normalized_definition()

      When("the state-machine definition is projected and rendered through the existing Record JSON conversion")
      val rec = StateMachineProjection.project(component, Some(component.name))
      val definition = _records(rec.asMap("definitions")).head.asMap
      val normalized = _record(definition("normalized")).asMap
      val transitions = _records(normalized("transitions"))
      val json = parse(RecordEncoder.json(rec)).toOption.getOrElse(fail("unable to render state-machine Record JSON"))

      Then("the typed metadata preserves declaration order, identities, topology, bindings, and terminal transitions")
      _record(normalized("machine")).asMap.get("name") shouldBe Some("lifecycle")
      normalized.get("version") shouldBe Some(1)
      _record(normalized("initialState")).asMap.get("value") shouldBe Some("Draft")

      transitions.map(_.asMap("declarationOrder")) shouldBe Vector(0, 1)
      transitions.head.asMap.get("priority") shouldBe Some(3)
      _record(transitions.head.asMap("source")).asMap.get("value") shouldBe Some("Draft")
      _record(transitions.head.asMap("target")).asMap.get("kind") shouldBe Some("shallow-history")
      _record(transitions.head.asMap("target")).asMap("composite") shouldBe a[Record]
      _record(transitions.head.asMap("trigger")).asMap("identity") shouldBe a[Record]
      _record(transitions.head.asMap("sourceLocation")).asMap.get("declarationPath") shouldBe Some(Vector("states", "Draft", "on", "submit"))
      _record(transitions.head.asMap("guard")).asMap.get("kind") shouldBe Some("predicate")
      _record(_record(transitions.head.asMap("guard")).asMap("identity")).asMap.get("name") shouldBe Some("predicate-0")
      _record(_record(transitions.head.asMap("guard")).asMap("identity")).asMap("transition") shouldBe
        _record(transitions.head.asMap("identity"))

      val actions = _records(transitions.head.asMap("actions"))
      actions.map(action => _record(action.asMap("identity")).asMap("phase")) shouldBe Vector("exit", "transition", "entry")
      actions.map(_.asMap("binding")) shouldBe Vector("clearDraft", "stampSubmitted", "notifyReview")

      _record(transitions(1).asMap("guard")).asMap.get("kind") shouldBe Some("named")
      _record(transitions(1).asMap("guard")).asMap.get("binding") shouldBe Some("canApprove")
      _record(_record(transitions(1).asMap("guard")).asMap("identity")).asMap("transition") shouldBe
        _record(transitions(1).asMap("identity"))
      _record(transitions(1).asMap("target")).asMap.get("kind") shouldBe Some("final")
      _records(transitions(1).asMap("historyWrites")).head.asMap("history") shouldBe a[Record]
      _records(normalized("terminalTransitions")).map(_.asMap("declarationOrder")) shouldBe Vector(1)
      normalized.get("finalStates") shouldBe None

      val topology = _record(normalized("topology")).asMap
      _records(_records(topology("composites")).head.asMap("directLeaves")).map(_.asMap("value")) shouldBe Vector("Review/Pending", "Review/Approved")
      json.hcursor.downField("definitions").downArray.downField("normalized").get[Int]("version").toOption shouldBe Some(1)
      json.hcursor.downField("definitions").downArray.downField("normalized").downField("transitions").downArray.get[Int]("declarationOrder").toOption shouldBe Some(0)
      json.hcursor.downField("definitions").downArray.downField("normalized").downField("transitions").downArray.downField("trigger").downField("context").downField("fields").downArray.downField("identity").downField("context").downField("trigger").get[String]("machine").toOption shouldBe Some("lifecycle")
      json.hcursor.downField("definitions").downArray.downField("normalized").downField("transitions").downArray.downField("trigger").downField("context").downField("fields").downArray.downField("identity").downField("context").downField("trigger").get[String]("name").toOption shouldBe Some("submit")
      json.hcursor.downField("definitions").downArray.downField("normalized").downField("transitions").downArray.downField("guard").downField("program").downField("predicate").downField("field").downField("context").downField("trigger").get[String]("machine").toOption shouldBe Some("lifecycle")
      json.hcursor.downField("definitions").downArray.downField("normalized").downField("transitions").downArray.downField("guard").downField("program").downField("predicate").downField("field").downField("context").downField("trigger").get[String]("name").toOption shouldBe Some("submit")
      json.hcursor.downField("definitions").downArray.downField("normalized").downField("terminalTransitions").downArray.get[Int]("declarationOrder").toOption shouldBe Some(1)
      json.hcursor.downField("definitions").downArray.downField("normalized").downField("transitions").downArray.downField("guard").downField("identity").downField("transition").get[Int]("declarationOrder").toOption shouldBe Some(0)
    }

    "reject malformed typed definitions at the generated ABI boundary" in {
      Given("a canonical typed definition and a foreign machine identity")
      val definition = _normalized_definition()
      val foreignmachine = CmlStateMachineIdentity("foreign")
      val localmachine = definition.identity
      val localstate = definition.initialState
      val trigger = _trigger(localmachine, "approve")

      When("cross-machine, malformed topology, and duplicate-order declarations are prepared")
      val crossmachinetransition = () => CmlStateMachineTransition(
        identity = CmlStateMachineTransitionIdentity(localmachine, 2),
        source = CmlStateMachineStateIdentity(foreignmachine, CmlStateMachineStatePath(Vector("Draft"))),
        target = CmlStateMachineTransitionTarget.State(localstate),
        trigger = trigger,
        priority = 0,
        guard = CmlStateMachineGuardProgram.Named(
          CmlStateMachineGuardIdentity(CmlStateMachineTransitionIdentity(localmachine, 2), "mayApprove"),
          "mayApprove"
        ),
        sourceLocation = CmlStateMachineSourceLocation(localmachine, Vector("transition", "approve"))
      )
      val duplicateorderdefinition = () => definition.copy(
        transitions = Vector(definition.transitions.head, definition.transitions.head)
      )
      val terminalmismatchdefinition = () => definition.copy(
        terminalTransitions = Vector.empty
      )
      val emptystatepath = () => CmlStateMachineStatePath(Vector.empty)
      val nestedcomposite = _state(localmachine, "Review", "Escalation")
      val nestedleaf = _state(localmachine, "Review", "Escalation", "Queued")
      val nestedcompositedefinition = () => definition.copy(
        states = definition.states ++ Vector(
          CmlStateMachineStateDefinition(nestedcomposite, CmlStateMachineStateKind.Composite, Some(_state(localmachine, "Review"))),
          CmlStateMachineStateDefinition(nestedleaf, CmlStateMachineStateKind.Leaf, Some(nestedcomposite))
        ),
        topology = CmlStateMachineTopology(
          definition.topology.composites :+ CmlStateMachineCompositeTopology(nestedcomposite, Vector(nestedleaf))
        )
      )
      val omitteddirectleafdefinition = () => definition.copy(
        topology = CmlStateMachineTopology(
          definition.topology.composites.map(topology => topology.copy(directLeaves = topology.directLeaves.take(1)))
        )
      )

      Then("the nominal ABI rejects malformed paths, foreign ownership, incomplete direct leaves, nested composites, duplicate explicit ordering, and mismatched terminal metadata")
      an[IllegalArgumentException] should be thrownBy {
        emptystatepath()
      }
      an[IllegalArgumentException] should be thrownBy {
        crossmachinetransition()
      }
      an[IllegalArgumentException] should be thrownBy {
        duplicateorderdefinition()
      }
      an[IllegalArgumentException] should be thrownBy {
        terminalmismatchdefinition()
      }
      an[IllegalArgumentException] should be thrownBy {
        nestedcompositedefinition()
      }
      an[IllegalArgumentException] should be thrownBy {
        omitteddirectleafdefinition()
      }
    }
  }

  private val _projection_action: ResolvedAction[Any, TransitionEvent] =
    new ResolvedAction[Any, TransitionEvent] {
      def program(
        state: Any,
        event: TransitionEvent
      ): org.goldenport.cncf.unitofwork.ExecUowM[org.goldenport.cncf.workflow.ActionExecution] = {
        val _ = (state, event)
        throw new UnsupportedOperationException("projection-only action must not be invoked")
      }
    }

  private def _component_with_rules(): Component = {
    val component = new Component() with CollectionTransitionRuleProvider {
      override def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] =
        Vector(
          CollectionTransitionRule[Any](
            collectionName = "person",
            trigger = TransitionTrigger.Update,
            eventName = "update",
            priority = 2,
            declarationOrder = 1,
            guard = Some(ExpressionGuard("event.name == 'update'", (_, _) => Map("event" -> Map("name" -> "update")))),
            plan = ExecutionPlan[Any, TransitionEvent](
              exitActions = Vector.empty,
              transitionActions = Vector(_projection_action, _projection_action),
              entryActions = Vector.empty
            ),
            machineName = Some("lifecycle"),
            stateFieldName = Some("status"),
            fromState = Some("Draft"),
            fromStateValue = Some(1),
            toState = Some("Published"),
            toStateValue = Some(2),
            historyCompositeName = Some("Review"),
            historyFieldName = Some("lifecycleHistory"),
            historyDirectLeaves = Vector("Pending", "Approved"),
            historyFallbackLeaf = Some("Pending"),
            expectedHistoryRecordWrites = Vector(HistoryRecordWrite("Review", "Published"))
          ),
          CollectionTransitionRule[Any](
            collectionName = "person",
            trigger = TransitionTrigger.Save,
            eventName = "save",
            priority = 1,
            declarationOrder = 0,
            guard = Some(RefGuard("canSave", new GuardBindingResolver[Any, TransitionEvent] {
              def resolve(name: String): Consequence[Guard[Any, TransitionEvent]] = {
                val _ = name
                Consequence.success(new Guard[Any, TransitionEvent] {
                  def eval(state: Any, event: TransitionEvent): Consequence[Boolean] = {
                    val _ = (state, event)
                    Consequence.success(true)
                  }
                })
              }
            })),
            plan = ExecutionPlan.empty[Any, TransitionEvent]
          )
        )
    }

    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.ProjectionStateMachineSpec",
      componentId = ComponentId("org.goldenport.cncf.test.ProjectionStateMachineSpec"),
      instanceId = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.ProjectionStateMachineSpec")),
      protocol = Protocol.empty
    )
    val subsystem = TestComponentFactory.emptySubsystem("projection_state_machine_spec")
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private def _component_with_definitions(): Component = {
    val component = new Component() {
      override def stateMachineDefinitions: Vector[CmlStateMachineDefinition] =
        Vector(
          CmlStateMachineDefinition(
            name = "lifecycle",
            states = Vector("Draft", "Published"),
            events = Vector("publish"),
            historyFieldName = Some("lifecycleHistory"),
            historyComposites = Vector(CmlHistoryCompositeDefinition("Review", Vector("Pending", "Approved"), Some("Pending")))
          )
        )
    }

    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.ProjectionStateMachineDefinitionSpec",
      componentId = ComponentId("org.goldenport.cncf.test.ProjectionStateMachineDefinitionSpec"),
      instanceId = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.ProjectionStateMachineDefinitionSpec")),
      protocol = Protocol.empty
    )
    val subsystem = TestComponentFactory.emptySubsystem("projection_state_machine_definition_spec")
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private def _component_with_normalized_definition(): Component = {
    val normalized = _normalized_definition()
    val component = new Component() {
      override def stateMachineDefinitions: Vector[CmlStateMachineDefinition] =
        Vector(
          CmlStateMachineDefinition(
            name = normalized.identity.name,
            states = Vector("Draft", "Published", "Review"),
            events = Vector("submit", "approve"),
            historyFieldName = Some("lifecycleHistory"),
            normalized = Some(normalized)
          )
        )
    }

    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.ProjectionNormalizedStateMachineSpec",
      componentId = ComponentId("org.goldenport.cncf.test.ProjectionNormalizedStateMachineSpec"),
      instanceId = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.ProjectionNormalizedStateMachineSpec")),
      protocol = Protocol.empty
    )
    val subsystem = TestComponentFactory.emptySubsystem("projection_normalized_state_machine_spec")
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private def _normalized_definition(): CmlNormalizedStateMachine = {
    val machine = CmlStateMachineIdentity("lifecycle")
    val draft = _state(machine, "Draft")
    val review = _state(machine, "Review")
    val pending = _state(machine, "Review", "Pending")
    val approved = _state(machine, "Review", "Approved")
    val published = _state(machine, "Published")
    val history = CmlStateMachineHistoryIdentity(machine, "lifecycleHistory")
    val historytarget = CmlStateMachineShallowHistoryTarget(
      composite = review,
      fallbackLeaf = pending
    )
    val submitidentity = CmlStateMachineTransitionIdentity(machine, 0)
    val submittrigger = _trigger(machine, "submit")
    val submit = CmlStateMachineTransition(
      identity = submitidentity,
      source = draft,
      target = CmlStateMachineTransitionTarget.ShallowHistory(historytarget),
      trigger = submittrigger,
      priority = 3,
      guard = CmlStateMachineGuardProgram.Predicate(
        CmlStateMachineGuardIdentity(submitidentity, "predicate-0"),
        CmlStateMachinePredicateProgram(
          CmlStateMachineVersion(1),
          CmlStateMachinePredicate.Present(
            CmlStateMachineTriggerContextFieldIdentity(submittrigger.context.identity, "currentState")
          )
        )
      ),
      actions = Vector(
        CmlStateMachineActionBinding(
          CmlStateMachineActionIdentity(submitidentity, CmlStateMachineActionPhase.Exit, 0),
          "clearDraft"
        ),
        CmlStateMachineActionBinding(
          CmlStateMachineActionIdentity(submitidentity, CmlStateMachineActionPhase.Transition, 0),
          "stampSubmitted"
        ),
        CmlStateMachineActionBinding(
          CmlStateMachineActionIdentity(submitidentity, CmlStateMachineActionPhase.Entry, 0),
          "notifyReview"
        )
      ),
      sourceLocation = CmlStateMachineSourceLocation(machine, Vector("states", "Draft", "on", "submit"))
    )
    val approveidentity = CmlStateMachineTransitionIdentity(machine, 1)
    val approve = CmlStateMachineTransition(
      identity = approveidentity,
      source = pending,
      target = CmlStateMachineTransitionTarget.Final,
      trigger = _trigger(machine, "approve"),
      priority = 1,
      guard = CmlStateMachineGuardProgram.Named(
        CmlStateMachineGuardIdentity(approveidentity, "canApprove"),
        "canApprove"
      ),
      historyWrites = Vector(CmlStateMachineHistoryWrite(history, review, approved)),
      sourceLocation = CmlStateMachineSourceLocation(machine, Vector("states", "Review", "Pending", "on", "approve"))
    )

    CmlNormalizedStateMachine(
      identity = machine,
      version = CmlStateMachineVersion(1),
      initialState = draft,
      states = Vector(
        CmlStateMachineStateDefinition(draft, CmlStateMachineStateKind.Leaf),
        CmlStateMachineStateDefinition(review, CmlStateMachineStateKind.Composite),
        CmlStateMachineStateDefinition(pending, CmlStateMachineStateKind.Leaf, Some(review)),
        CmlStateMachineStateDefinition(approved, CmlStateMachineStateKind.Leaf, Some(review)),
        CmlStateMachineStateDefinition(published, CmlStateMachineStateKind.Leaf)
      ),
      transitions = Vector(submit, approve),
      terminalTransitions = Vector(approveidentity),
      topology = CmlStateMachineTopology(Vector(
        CmlStateMachineCompositeTopology(review, Vector(pending, approved), Vector(historytarget))
      )),
      historyField = Some(history)
    )
  }

  private def _state(
    machine: CmlStateMachineIdentity,
    segments: String*
  ): CmlStateMachineStateIdentity =
    CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(segments.toVector))

  private def _trigger(
    machine: CmlStateMachineIdentity,
    name: String
  ): CmlStateMachineTrigger = {
    val identity = CmlStateMachineTriggerIdentity(machine, name)
    val contextidentity = CmlStateMachineTriggerContextIdentity(identity)
    CmlStateMachineTrigger(
      identity,
      CmlStateMachineTriggerContext(
        contextidentity,
        CmlStateMachineVersion(1),
        Vector(
          CmlStateMachineTriggerContextField(
            CmlStateMachineTriggerContextFieldIdentity(contextidentity, "currentState"),
            CmlStateMachineScalarType.StringValue
          ),
          CmlStateMachineTriggerContextField(
            CmlStateMachineTriggerContextFieldIdentity(contextidentity, "candidateState"),
            CmlStateMachineScalarType.StringValue
          )
        )
      )
    )
  }

  private def _records(value: Any): Vector[Record] =
    value match {
      case xs: Vector[?] =>
        xs.collect { case r: Record => r }
      case xs: Seq[?] =>
        xs.toVector.collect { case r: Record => r }
      case _ =>
        Vector.empty
    }

  private def _record(value: Any): Record =
    value.asInstanceOf[Record]
}
