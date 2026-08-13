package org.goldenport.cncf.component

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionEngine
import org.goldenport.cncf.entity.aggregate.AggregateCollection
import org.goldenport.cncf.job.InMemoryJobEngine
import org.goldenport.cncf.subsystem.{
  GenericSubsystemComponentBinding,
  GenericSubsystemDescriptor,
  SubsystemAssemblyAdmission
}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.logic.ProtocolLogic
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.{Failure, Try}

/*
 * @since   Aug.  7, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentIdentityContractSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E1, rules:CID01-R1,R2, phase:56, slice:CID-01D"
  )
  private val _e2 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E2, rules:CID01-R1,R3, phase:56, slice:CID-01D"
  )
  private val _e3 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E3, rules:CID01-R4,R5, phase:56, slice:CID-01D"
  )
  private val _e4 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E4, rules:CID01-R6,R7, phase:56, slice:CID-01D"
  )

  "Phase 56 Component identity" should {
    "ComponentId and Core behavior (E1-E2)" which {
      "E1 reject bare ComponentId compatibility input through the safe parser" must _e1 {
      "when the canonical UserAccount compatibility parser receives a bare spelling" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R2; Example: E1 UserAccount")
        val bareid = "UserAccount"
        When("the target parser is applied to bare UserAccount")
        val result = ComponentId.parseC(bareid)
        Then("bare UserAccount is rejected")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.show should include ("component.identity.id.qualified")
            conclusion.show should include ("component ID must be namespace-qualified: UserAccount")
          case Consequence.Success(_) =>
            fail("bare UserAccount was admitted")
        }
      }
    }

      "E1 reject bare ComponentId compatibility input through the throwing constructor" must _e1 {
      "when the throwing constructor receives a bare spelling" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R2; Example: E1 UserAccount")
        val bareid = "UserAccount"
        When("the throwing ComponentId constructor is applied to bare UserAccount")
        val result = Try(ComponentId(bareid))
        Then("the throwing constructor rejects bare UserAccount with IllegalArgumentException")
        _assert_try_illegal_argument(result)
      }
    }

      "E2 reject every invalid identity through each public Core.create overload" must _e2 {
      "when the create overloads receive missing, divergent, or foreign Core identities" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R3; Example: E2 org.simplemodeling.textus.UserAccount")
        val overloads = Vector(
          "four-argument" -> ((name: String, componentid: ComponentId, instanceid: ComponentInstanceId) =>
            Component.Core.create(name, componentid, instanceid, Protocol.empty)
          ),
          "five-argument job-engine" -> ((name: String, componentid: ComponentId, instanceid: ComponentInstanceId) =>
            Component.Core.create(
              name,
              componentid,
              instanceid,
              Protocol.empty,
              InMemoryJobEngine.create()
            )
          ),
          "five-argument factory" -> ((name: String, componentid: ComponentId, instanceid: ComponentInstanceId) =>
            Component.Core.create(
              name,
              componentid,
              instanceid,
              Protocol.empty,
              CoreAdmissionFactory
            )
          ),
          "eight-argument" -> ((name: String, componentid: ComponentId, instanceid: ComponentInstanceId) =>
            Component.Core.create(
              name,
              componentid,
              instanceid,
              Protocol.empty,
              ProtocolLogic(Protocol.empty),
              CoreAdmissionFactory,
              ActionEngine.create(),
              InMemoryJobEngine.create()
            )
          )
        )
        When("each public Core.create overload admits the invalid identity matrix")
        val results = overloads.flatMap { case (label, create) =>
          _core_admission_results(s"$label Core.create", create)
        }
        Then("every overload preserves the exact Core identity admission diagnostics")
        _assert_core_admission_results(results)
      }
    }

      "E2 retain an exact qualified Core identity" must _e2 {
      "when Core.create receives a matching qualified ComponentId and default instance" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R3; Example: E2 org.simplemodeling.textus.UserAccount")
        val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
        val instanceid = ComponentInstanceId.default(componentid)
        When("Core.create is given the exact qualified name and matching default instance")
        val core = Component.Core.create(
          componentid.name,
          componentid,
          instanceid,
          Protocol.empty
        )
        Then("the verified Core projections retain the exact component and instance identities")
        core.name shouldBe "org.simplemodeling.textus.UserAccount"
        core.componentId shouldBe componentid
        core.instanceId shouldBe instanceid
        core.instanceId.componentId shouldBe componentid
        core.instanceId.canonicalKey shouldBe "org.simplemodeling.textus.UserAccount@default"
      }
    }

      "E2 retain identity through canonical named component API arguments" must _e2 {
      "when Core and Component constructors use canonical identity labels" in {
        Given("a qualified ComponentId, matching instance ID, and canonical aggregate binding label")
        val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
        val instanceid = ComponentInstanceId.default(componentid)

        When("the public Component APIs receive componentId, instanceId, and aggregateName arguments")
        val core = Component.Core.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = instanceid,
          protocol = Protocol.empty
        )
        val compatiblecore = Component.Core.create(
          name = componentid.name,
          componentid = componentid,
          instanceid = instanceid,
          protocol = Protocol.empty
        )
        val component = Component.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = instanceid,
          protocol = Protocol.empty
        )
        val aggregate = Component.AggregateCollectionBinding(
          aggregateName = "user_account",
          collection = null.asInstanceOf[AggregateCollection[Any]]
        )

        Then("canonical names compile and preserve the exact component and aggregate identities")
        core.componentId shouldBe componentid
        core.instanceId shouldBe instanceid
        compatiblecore.componentId shouldBe componentid
        compatiblecore.instanceId shouldBe instanceid
        component.core.componentId shouldBe componentid
        component.core.instanceId shouldBe instanceid
        aggregate.aggregateName shouldBe "user_account"
        aggregate.aggregate_name shouldBe aggregate.aggregateName
      }
    }

      "E2 reject every invalid identity through direct Core construction" must _e2 {
      "when the case-class constructor receives the Core invalid identity matrix" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R3; Example: E2 org.simplemodeling.textus.UserAccount")
        val admitted = _admitted_core()
        When("direct Core construction admits the invalid identity matrix")
        val results = _core_admission_results("direct Core construction", { (name, componentid, instanceid) =>
          Component.Core(
            name,
            componentid,
            instanceid,
            admitted.protocol,
            admitted.protocolLogic,
            admitted.factory,
            admitted.actionEngine,
            admitted.jobEngine
          )
        })
        Then("the case-class constructor preserves the exact Core identity admission diagnostics")
        _assert_core_admission_results(results)
      }
    }

      "E2 reject every invalid identity through Core.copy" must _e2 {
      "when copy receives the Core invalid identity matrix" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R3; Example: E2 org.simplemodeling.textus.UserAccount")
        val admitted = _admitted_core()
        When("Core.copy admits the invalid identity matrix")
        val results = _core_admission_results("Core.copy", { (name, componentid, instanceid) =>
          admitted.copy(
            name = name,
            componentId = componentid,
            instanceId = instanceid
          )
        })
        Then("copy preserves the exact Core identity admission diagnostics")
        _assert_core_admission_results(results)
      }
    }

      "E2 create the Script Core with its canonical identity" must _e2 {
      "when the Script Core helper materializes its default runtime identity" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R3; Example: E2 org.goldenport.cncf.Script")
        When("createScriptCore creates the built-in Script Core")
        val core = Component.createScriptCore()
        Then("the Script Core retains the canonical qualified ID and matching default instance")
        core.name shouldBe "org.goldenport.cncf.Script"
        core.componentId.name shouldBe "org.goldenport.cncf.Script"
        core.instanceId.componentId shouldBe core.componentId
        core.instanceId.instance shouldBe "default"
      }
      }
    }

    "ComponentInstanceId behavior (E3)" which {
      "E3 preserve ComponentId value semantics" must _e3 {
      "when qualified ComponentId values are parsed independently" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        val distinctqualifiedid = "org.simplemodeling.textus.UserProfile"
        When("the target safe parser creates independent qualified ComponentId values")
        val componentresult = ComponentId.parseC(qualifiedid)
        val equivalentresult = ComponentId.parseC(qualifiedid)
        val distinctresult = ComponentId.parseC(distinctqualifiedid)
        Then("qualified ComponentId values retain exact fields and equality semantics")
        val componentid = componentresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        val equivalentcomponentid = equivalentresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        val distinctcomponentid = distinctresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        componentid.namespace.value() shouldBe "org.simplemodeling.textus"
        componentid.localId.value() shouldBe "UserAccount"
        componentid.name shouldBe qualifiedid
        componentid.toString shouldBe qualifiedid
        equivalentcomponentid shouldBe componentid
        equivalentcomponentid.hashCode shouldBe componentid.hashCode
        componentid should not equal distinctcomponentid
        Set(componentid, equivalentcomponentid, distinctcomponentid) should have size 2
      }
    }

      "E3 preserve ComponentInstanceId value semantics" must _e3 {
      "when default and named instances are created from a qualified ComponentId" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        When("the target constructors create default and blue ComponentInstanceId values")
        val componentid = ComponentId(qualifiedid)
        val instanceid = ComponentInstanceId.default(componentid)
        val equivalentresult = ComponentInstanceId.createC(componentid, "default")
        val blueresult = ComponentInstanceId.createC(componentid, "blue")
        Then("instance values retain exact component, label, canonical, and equality semantics")
        val equivalentinstanceid = equivalentresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        val blueinstanceid = blueresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        instanceid.componentId shouldBe componentid
        instanceid.name shouldBe qualifiedid
        instanceid.instance shouldBe "default"
        instanceid.canonicalKey shouldBe "org.simplemodeling.textus.UserAccount@default"
        instanceid.toString shouldBe "org.simplemodeling.textus.UserAccount@default"
        equivalentinstanceid.componentId shouldBe componentid
        equivalentinstanceid.name shouldBe qualifiedid
        equivalentinstanceid.instance shouldBe "default"
        equivalentinstanceid.canonicalKey shouldBe "org.simplemodeling.textus.UserAccount@default"
        equivalentinstanceid.toString shouldBe "org.simplemodeling.textus.UserAccount@default"
        blueinstanceid.componentId shouldBe componentid
        blueinstanceid.name shouldBe qualifiedid
        blueinstanceid.instance shouldBe "blue"
        blueinstanceid.canonicalKey shouldBe s"$qualifiedid@blue"
        blueinstanceid.toString shouldBe s"$qualifiedid@blue"
        equivalentinstanceid shouldBe instanceid
        equivalentinstanceid.hashCode shouldBe instanceid.hashCode
        Set(instanceid, equivalentinstanceid, blueinstanceid) should have size 2
      }
    }

      "E3 reject unsafe ComponentInstanceId values through the safe factory" must _e3 {
      "when null component and invalid labels are submitted" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        When("the safe ComponentInstanceId factory receives invalid component and label inputs")
        val componentid = ComponentId(qualifiedid)
        val nullcomponentresult = ComponentInstanceId.createC(
          null.asInstanceOf[ComponentId],
          "default"
        )
        val emptylabelresult = ComponentInstanceId.createC(componentid, "")
        val nulllabelresult = ComponentInstanceId.createC(componentid, null)
        Then("the safe factory returns each exact stable failure code and message")
        _assert_consequence_failure(
          nullcomponentresult,
          "component.identity.instance.component-id.required: component ID is required"
        )
        _assert_consequence_failure(
          emptylabelresult,
          "component.identity.instance.label.required: instance label is required"
        )
        _assert_consequence_failure(
          nulllabelresult,
          "component.identity.instance.label.required: instance label is required"
        )
      }
    }

      "E3 reject unsafe ComponentInstanceId values through throwing conveniences" must _e3 {
      "when throwing constructors receive null component and invalid labels" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        When("the throwing ComponentInstanceId constructors receive invalid component and label inputs")
        val componentid = ComponentId(qualifiedid)
        val nullcomponentresult = Try(
          ComponentInstanceId(null.asInstanceOf[ComponentId], "default")
        )
        val emptylabelresult = Try(ComponentInstanceId(componentid, ""))
        val nulllabelresult = Try(ComponentInstanceId(componentid, null))
        Then("each throwing convenience reports IllegalArgumentException")
        _assert_try_illegal_argument(nullcomponentresult)
        _assert_try_illegal_argument(emptylabelresult)
        _assert_try_illegal_argument(nulllabelresult)
      }
      }
    }

    "namespace isolation (E4)" which {
      "E4 isolate generated namespaces" must _e4 {
      "when subsystem admission resolves generated same-local identities" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R6,R7; Example: E4 org.alpha.UserAccount, org.beta.UserAccount, and UserAccount")
        val property = Prop.forAll(_distinct_namespaces) { namespaces =>
          val componentids = namespaces.map(namespace => ComponentId(s"$namespace.UserAccount"))
          val qualifiedids = componentids.map(_.name)
          val bindings = componentids.map(_typed_binding)
          SubsystemAssemblyAdmission.resolveC(_descriptor(bindings), Vector.empty) match {
            case Consequence.Success(resolved) =>
              val admittedbindings = resolved.componentBindings
              val admittedids = admittedbindings.flatMap(_.componentId)
              val admittedinstances = admittedbindings.flatMap(_.canonicalInstanceId)
              admittedbindings.map(_.componentName) == qualifiedids &&
                admittedids == componentids &&
                admittedinstances.map(_.componentId) == componentids &&
                admittedinstances.map(_.canonicalKey).distinct.size == admittedinstances.size
            case Consequence.Failure(_) =>
              false
          }
        }
        When("the namespace-isolation property is checked")
        val result = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(50),
          property
        )
        Then("qualified identities remain isolated")
        result.passed shouldBe true
      }
    }

      "E4 reject an ambiguous bare UserAccount" must _e4 {
      "when subsystem admission resolves a bare alias among qualified candidates" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R6,R7; Example: E4 org.alpha.UserAccount, org.beta.UserAccount, and UserAccount")
        val alpha = ComponentId("org.alpha.UserAccount")
        val beta = ComponentId("org.beta.UserAccount")
        val descriptor = _descriptor(Vector(
          _typed_binding(alpha),
          _typed_binding(beta),
          GenericSubsystemComponentBinding("UserAccount")
        ))
        When("SubsystemAssemblyAdmission.resolveC evaluates the bare alias")
        val result = SubsystemAssemblyAdmission.resolveC(descriptor, Vector.empty)
        Then("bare identity is rejected before any compatibility adaptation")
        result match {
          case Consequence.Failure(conclusion) =>
            val diagnostic = conclusion.show
            diagnostic should include ("component assembly binding requires canonical namespace/id/version")
          case Consequence.Success(_) =>
            fail("ambiguous bare UserAccount was admitted")
        }
      }
      }
    }
  }

  private val _distinct_namespaces = for {
    first <- Gen.oneOf("org.alpha", "org.beta", "org.gamma", "org.delta")
    second <- Gen.oneOf("org.alpha", "org.beta", "org.gamma", "org.delta").suchThat(_ != first)
  } yield Vector(first, second)

  private def _descriptor(bindings: Vector[GenericSubsystemComponentBinding]): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      Path.of("phase-56-cid01-e4"),
      "identity-runtime",
      componentBindings = bindings
    )

  private def _typed_binding(componentid: ComponentId): GenericSubsystemComponentBinding =
    GenericSubsystemComponentBinding(componentid.name, version = Some("1.0.0"), componentId = Some(componentid))

  private def _assert_consequence_failure(
    result: Consequence[ComponentInstanceId],
    expected: String
  ): Unit =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.show should include (expected)
      case Consequence.Success(_) =>
        fail(s"expected Consequence.Failure containing: $expected")
    }

  private def _assert_try_illegal_argument(result: Try[_]): Unit =
    result match {
      case Failure(_: IllegalArgumentException) => ()
      case Failure(exception) =>
        fail(s"expected IllegalArgumentException but received ${exception.getClass.getName}")
      case scala.util.Success(_) =>
        fail("expected IllegalArgumentException but operation succeeded")
    }

  private def _assert_try_illegal_argument_message(
    result: Try[_],
    expected: String
  ): Unit =
    result match {
      case Failure(exception: IllegalArgumentException) =>
        exception.getMessage shouldBe expected
      case Failure(exception) =>
        fail(s"expected IllegalArgumentException but received ${exception.getClass.getName}")
      case scala.util.Success(_) =>
        fail("expected IllegalArgumentException but operation succeeded")
    }

  private def _admitted_core(): Component.Core = {
    val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty
    )
  }

  private def _core_admission_results(
    label: String,
    create: (String, ComponentId, ComponentInstanceId) => Component.Core
  ): Vector[CoreAdmissionResult] = {
    val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
    val instanceid = ComponentInstanceId.default(componentid)
    val foreigncomponentid = ComponentId("org.simplemodeling.textus.UserProfile")
    _core_admission_invalid_inputs.map { invalid =>
      val (name, invalidcomponentid, invalidinstanceid) = invalid.input(componentid, instanceid, foreigncomponentid)
      CoreAdmissionResult(
        s"$label ${invalid.label}",
        invalid.expected,
        Try(create(name, invalidcomponentid, invalidinstanceid))
      )
    }
  }

  private def _assert_core_admission_results(
    results: Vector[CoreAdmissionResult]
  ): Unit =
    results.foreach { result =>
      withClue(s"${result.label}: ") {
        _assert_try_illegal_argument_message(result.result, result.expected)
      }
    }

  private val _core_admission_invalid_inputs = Vector(
    CoreAdmissionInvalid(
      "null ComponentId",
      "component.core.component-id.required: expected qualified component ID; actual qualified component ID: null",
      (componentid, instanceid, _) =>
        (componentid.name, null.asInstanceOf[ComponentId], instanceid)
    ),
    CoreAdmissionInvalid(
      "null ComponentInstanceId",
      "component.core.instance-id.required: expected ComponentInstanceId for qualified component ID 'org.simplemodeling.textus.UserAccount'; actual instance identity: null",
      (componentid, _, _) =>
        (componentid.name, componentid, null.asInstanceOf[ComponentInstanceId])
    ),
    CoreAdmissionInvalid(
      "divergent name",
      "component.core.name.component-id.mismatch: expected qualified component ID 'org.simplemodeling.textus.UserAccount'; actual supplied name 'Textus User Account'",
      (componentid, instanceid, _) =>
        ("Textus User Account", componentid, instanceid)
    ),
    CoreAdmissionInvalid(
      "foreign ComponentInstanceId",
      "component.core.instance.component-id.mismatch: expected qualified component ID 'org.simplemodeling.textus.UserAccount'; actual instance component ID 'org.simplemodeling.textus.UserProfile'",
      (componentid, _, foreigncomponentid) =>
        (componentid.name, componentid, ComponentInstanceId.default(foreigncomponentid))
    )
  )

  private final case class CoreAdmissionInvalid(
    label: String,
    expected: String,
    input: (ComponentId, ComponentInstanceId, ComponentId) => (String, ComponentId, ComponentInstanceId)
  )

  private final case class CoreAdmissionResult(
    label: String,
    expected: String,
    result: Try[Component.Core]
  )

  private object CoreAdmissionFactory extends Component.Factory {
    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      throw new UnsupportedOperationException("Core admission fixture does not create Components")

    protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("Core admission fixture does not create Components")
  }
}
