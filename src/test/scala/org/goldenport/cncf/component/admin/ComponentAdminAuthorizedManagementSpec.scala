package org.goldenport.cncf.component.admin

import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, SecurityContext}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for ADM-07A: the package-private, value-only
 * Component Admin authorized-management catalog and admission boundary.
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentAdminAuthorizedManagementSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord("in spec:component-admin-authorized-management, example:E1, rules:ADM07A-R1, phase:60.6, slice:ADM-07A")
  private val _e2 = afterWord("in spec:component-admin-authorized-management, example:E2, rules:ADM07A-R2, phase:60.6, slice:ADM-07A")
  private val _e3 = afterWord("in spec:component-admin-authorized-management, example:E3, rules:ADM07A-R3, phase:60.6, slice:ADM-07A")
  private val _e4 = afterWord("in spec:component-admin-authorized-management, example:E4, rules:ADM07A-R4, phase:60.6, slice:ADM-07A")
  private val _e5 = afterWord("in spec:component-admin-authorized-management, example:E5, rules:ADM07A-R5, phase:60.6, slice:ADM-07A")
  private val _e6 = afterWord("in spec:component-admin-authorized-management, example:E6, rules:ADM07A-R6, phase:60.6, slice:ADM-07A")
  private val _e7 = afterWord("in spec:component-admin-authorized-management, example:E7, rules:ADM07A-R7, phase:60.6, slice:ADM-07A")

  "ADM-07A authorized Component Admin management" should {
    "E1 retain exactly six Component-owned management actions" must _e1 {
      "when exact registrations and explicit ordinary queries are cataloged" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R1; Example: E1; six registered actions and visible ordinary queries")
        val catalog = _catalog()

        When("the catalog validates the Component-owned finite registration")
        val management = catalog.managementInventory
        val ordinary = catalog.ordinaryInventory

        Then("the six canonical actions are retained while ordinary queries remain distinct")
        management.map(_.selector.value) shouldBe _management_selectors
        management.forall(_.authorization != null) shouldBe true
        ordinary.map(_.selector.value) shouldBe _ordinary_selectors
        ordinary.forall(_.visible) shouldBe true
      }

      "when management registration is missing, duplicated, or unregistered" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R1; Example: E1; nonfinite registration candidates")
        val registrations = _registrations()

        When("catalog construction receives nonexact Component action evidence")
        val missing = ComponentAdminAuthorizedManagement.createC(_target(), registrations.drop(1), _ordinary_queries())
        val duplicate = ComponentAdminAuthorizedManagement.createC(_target(), registrations.updated(1, registrations.head), _ordinary_queries())
        val extra = ComponentAdminAuthorizedManagement.createC(_target(), registrations :+ _registration("entity/delete"), _ordinary_queries())

        Then("each candidate is rejected before admission")
        missing.toOption shouldBe None
        duplicate.toOption shouldBe None
        extra.toOption shouldBe None
      }
    }

    "E2 admit authorized management with attributable input evidence" must _e2 {
      "when an active selected instance supplies a hidden registered action" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R2; Example: E2; active exact runtime identity, validated entity/create input, and explicit rule")
        given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
        val request = _request("entity/create")

        When("the pure boundary validates the action input and consumes the stored rule")
        val outcome = ComponentAdminAuthorizedManagement.admit(_catalog(), request)

        Then("admission audit carries all exact target and input identity")
        outcome shouldBe a[Admitted]
        outcome.audit.componentid shouldBe _component
        outcome.audit.loadedinstanceid shouldBe _instance
        outcome.audit.identitybinding shouldBe _identity_binding
        outcome.audit.lifecycle shouldBe _active_lifecycle
        outcome.audit.selector shouldBe request.selector
        outcome.audit.input shouldBe request.input
        outcome.audit.principalid shouldBe PrincipalId("test-user-principal")
        outcome.audit.subjectkind should not be null
        outcome.audit.idempotencykey shouldBe "request-1"
        outcome.audit.disposition shouldBe ComponentAdminManagementDisposition.Admitted
      }
    }

    "E3 forbid anonymous management and visible ordinary queries" must _e3 {
      "when an anonymous caller requests a registered action" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R3; Example: E3; anonymous caller and exact entity/update evidence")
        val catalog = _catalog()

        When("the caller requests entity/update")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.Anonymous)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("entity/update"))
        }

        Then("authorization remains typed Forbidden")
        outcome shouldBe a[Forbidden]
      }

      "when a visible ordinary query is presented through management admission" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R3; Example: E3; visible entity/list outside the finite management action registration")
        val catalog = _catalog()

        When("a user requests the visible ordinary selector")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("entity/list"))
        }

        Then("visibility never grants management authority")
        outcome shouldBe a[Forbidden]
        outcome.audit.selector.value shouldBe "entity/list"
      }
    }

    "E4 require valid idempotency and active lifecycle" must _e4 {
      "when idempotency is malformed" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R4; Example: E4; otherwise exact data/create request with blank key")
        val catalog = _catalog()

        When("the action is requested with blank idempotency")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("data/create", key = "  "))
        }

        Then("the precondition produces RetryRequired")
        outcome shouldBe a[RetryRequired]
      }

      "when selected target lifecycle is stopped or failed" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R4; Example: E4; stopped and failed evidence bound to exact targets")
        val lifecycles = Vector(ComponentAdminLifecycleState.Stopped, ComponentAdminLifecycleState.Failed)

        When("management is requested against each exact lifecycle")
        val outcomes = lifecycles.map { state =>
          val lifecycle = _lifecycle(state)
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(lifecycle = lifecycle), _request("data/update", lifecycle = lifecycle))
        }

        Then("each nonactive lifecycle receives Conflict")
        outcomes.forall(_.isInstanceOf[Conflict]) shouldBe true
      }
    }

    "E5 isolate complete runtime target identity" must _e5 {
      "when a registered action is explicitly unavailable" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R5; Example: E5; exact action registration with unavailable association evidence")
        val registrations = _registrations().map { registration =>
          if (registration.selector.value == "association/admin_attach_association")
            registration.copy(availability = ComponentAdminManagementAvailability.Unavailable)
          else
            registration
        }
        val catalog = ComponentAdminAuthorizedManagement.createC(_target(), registrations, _ordinary_queries()).toOption.get

        When("the user requests that exact unavailable action")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("association/admin_attach_association"))
        }

        Then("availability remains a typed Unavailable outcome")
        outcome shouldBe a[Unavailable]
      }

      "when selected logical release or Subsystem binding differs" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R5; Example: E5; alternate release and Subsystem bindings for the selected Component")
        val catalog = _catalog()
        val release = _identity_binding.copy(selectedlogicalrelease = ComponentAdminLogicalRelease(_component_class, "2.0.0"))
        val subsystem = _identity_binding.copy(subsysteminstance = ComponentAdminSubsystemInstance(_subsystem_class, "green"))

        When("requests carry complete but nonselected runtime identity")
        val outcomes = Vector(release, subsystem).map { identity =>
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("association/admin_attach_association", identitybinding = identity))
        }

        Then("there is no fallback to another runtime identity")
        outcomes.forall(_.isInstanceOf[Conflict]) shouldBe true
      }

      "when complete runtime identity contains malformed logical values" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R5; Example: E5; malformed logical release, Subsystem class/instance, and implicit Component Subsystem names")
        val malformedclass = ComponentAdminSubsystemClass("invalid class")
        val identities = Vector(
          _identity_binding.copy(selectedlogicalrelease = ComponentAdminLogicalRelease(_component_class, "")),
          _identity_binding.copy(subsystemclass = malformedclass, subsysteminstance = ComponentAdminSubsystemInstance(malformedclass, "blue")),
          _identity_binding.copy(subsysteminstance = ComponentAdminSubsystemInstance(_subsystem_class, "invalid instance")),
          _identity_binding.copy(implicitcomponentsubsystem = ComponentAdminImplicitComponentSubsystem(_component_class, "invalid subsystem"))
        )

        When("the catalog or request supplies an otherwise exact malformed runtime identity")
        val catalogoutcomes = identities.map(identity => ComponentAdminAuthorizedManagement.createC(_target().copy(identitybinding = identity), _registrations(), _ordinary_queries()))
        val requestoutcomes = identities.map { identity =>
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), _request("association/admin_attach_association", identitybinding = identity))
        }

        Then("malformed logical identity cannot create a catalog or fall back during admission")
        catalogoutcomes.forall(_.toOption.isEmpty) shouldBe true
        requestoutcomes.forall(_.isInstanceOf[RetryRequired]) shouldBe true
      }

      "when the loaded Component instance differs" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R5; Example: E5; foreign loaded instance under same Component")
        val staleinstance = ComponentInstanceId(_component, "green")

        When("the request names that other loaded instance")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), _request("association/admin_detach_association", loadedinstance = staleinstance))
        }

        Then("the mismatch remains StaleInstance")
        outcome shouldBe a[StaleInstance]
        outcome.audit.loadedinstanceid shouldBe staleinstance
      }
    }

    "E6 authorize current policy before replay lookup" must _e6 {
      "when a stored denying rule is paired with an unavailable registered action" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R6; Example: E6; user caller, current denying rule, and unavailable entity/update action")
        val catalog = _catalog(
          unavailable = Some("entity/update"),
          rules = Map("entity/update" -> OperationAuthorizationRule(deny = true))
        )

        When("the caller requests the registered unavailable action")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("entity/update"))
        }

        Then("authorization returns Forbidden before availability")
        outcome shouldBe a[Forbidden]
        outcome should not be a[Unavailable]
      }

      "when a stored denying rule is paired with a nonactive selected lifecycle" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R6; Example: E6; user caller, current denying rule, and stopped entity/update target")
        val lifecycle = _lifecycle(ComponentAdminLifecycleState.Stopped)
        val catalog = _catalog(
          lifecycle = lifecycle,
          rules = Map("entity/update" -> OperationAuthorizationRule(deny = true))
        )

        When("the caller requests the registered nonactive action")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("entity/update", lifecycle = lifecycle))
        }

        Then("authorization returns Forbidden before lifecycle conflict")
        outcome shouldBe a[Forbidden]
        outcome should not be a[Conflict]
      }

      "when an accepted same-principal request repeats under unchanged authorization" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R6; Example: E6; accepted entity/update request and exact audit receipt")
        val catalog = _catalog()
        val request = _request("entity/update", key = "same-request")

        When("the same principal repeats the exact request with its receipt")
        val first = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, request)
        }
        val replay = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, request, Vector(first.audit))
        }

        Then("the authorized repeat is IdempotentReplay with exact input attribution")
        first shouldBe a[Admitted]
        replay shouldBe a[IdempotentReplay]
        replay.audit.input shouldBe request.input
      }

      "when a same-principal replay follows a stored rule revocation" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R6; Example: E6; first accepted request and entity/update rule changed to deny")
        val request = _request("entity/update", key = "revoked-request")
        val first = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), request)
        }
        val revoked = _catalog(rules = Map("entity/update" -> OperationAuthorizationRule(deny = true)))

        When("the same principal/key request replays against the current denying rule")
        val outcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(revoked, request, Vector(first.audit))
        }

        Then("the result is Forbidden and never IdempotentReplay")
        first shouldBe a[Admitted]
        outcome shouldBe a[Forbidden]
        outcome should not be a[IdempotentReplay]
      }
    }

    "E7 require validated action-input evidence" must _e7 {
      "when action input or lifecycle evidence carries incomplete provenance" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R7; Example: E7; input and lifecycle evidence with absent source kind and logical identity Option")
        val invalidprovenance = ComponentAdminSafeProvenance(null, null)
        val input = _input("entity/create").copy(provenance = invalidprovenance)
        val lifecycle = _active_lifecycle.copy(provenance = invalidprovenance)

        When("otherwise exact management evidence uses the incomplete provenance")
        val inputoutcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), _request("entity/create", input = input))
        }
        val lifecycleoutcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), _request("entity/create", lifecycle = lifecycle))
        }

        Then("neither input nor lifecycle evidence can establish admission")
        inputoutcome shouldBe a[RetryRequired]
        lifecycleoutcome shouldBe a[RetryRequired]
      }

      "when input is malformed, unvalidated, or bound to another action" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R7; Example: E7; malformed, unvalidated, and mismatched input evidence")
        val missing = _request("entity/create").copy(input = null)
        val malformed = _input("entity/create", representation = "input\u0000")
        val unvalidated = _input("entity/create", validated = false)
        val mismatched = _input("data/create")

        When("action input cannot establish a validated exact request identity")
        val missingoutcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), missing)
        }
        val outcomes = Vector(malformed, unvalidated, mismatched).map { input =>
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), _request("entity/create", input = input))
        }

        Then("each input precondition receives RetryRequired and remains audit-attributable")
        missingoutcome shouldBe a[RetryRequired]
        outcomes.forall(_.isInstanceOf[RetryRequired]) shouldBe true
        outcomes.map(_.audit.input) shouldBe Vector(malformed, unvalidated, mismatched)
      }

      "when fifty generated visible or hidden ordinary selectors are supplied" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R7; Example: E7; generated ordinary visibility and exact audit attribution")
        val numbers = Gen.choose(1, 100000)
        val visibilities = Gen.oneOf(true, false)
        val property = Prop.forAll(numbers, visibilities) { (number, visibility) =>
          val instance = ComponentInstanceId(_component, s"node$number")
          val selector = ComponentAdminManagementActionSelector(s"query/$number")
          val catalog = ComponentAdminAuthorizedManagement.createC(_target(loadedinstance = instance), _registrations(), Vector(ComponentAdminOrdinaryQuery(selector, visibility))).toOption
          val request = _request(selector.value, loadedinstance = instance)
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          catalog.exists { selectedcatalog =>
            val outcome = ComponentAdminAuthorizedManagement.admit(selectedcatalog, request)
            outcome.isInstanceOf[Forbidden] &&
              outcome.audit.componentid == _component &&
              outcome.audit.loadedinstanceid == instance &&
              outcome.audit.identitybinding == _identity_binding &&
              outcome.audit.lifecycle == _active_lifecycle &&
              outcome.audit.selector == selector &&
              outcome.audit.input == request.input &&
              outcome.audit.principalid == PrincipalId("test-user-principal")
          }
        }

        When("the pure boundary evaluates at least fifty independent generated values")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("visibility remains non-authoritative with exact audit attribution")
        checked.passed shouldBe true
      }
    }
  }

  private val _component = ComponentId("org.goldenport.cncf.admin.Sample")
  private val _component_class = ComponentAdminComponentClass(_component)
  private val _subsystem_class = ComponentAdminSubsystemClass("BuiltinAdmin")
  private val _identity_binding = ComponentAdminRuntimeIdentityBinding(
    ComponentAdminLogicalRelease(_component_class, "1.0.0"),
    _subsystem_class,
    ComponentAdminSubsystemInstance(_subsystem_class, "blue"),
    ComponentAdminImplicitComponentSubsystem(_component_class, "component-admin")
  )
  private val _provenance = ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None)
  private val _active_lifecycle = _lifecycle(ComponentAdminLifecycleState.Active)
  private val _instance = ComponentInstanceId(_component, "blue")
  private val _management_selectors = Vector("entity/create", "entity/update", "data/create", "data/update", "association/admin_attach_association", "association/admin_detach_association")
  private val _ordinary_selectors = Vector("entity/list", "data/list")

  private def _lifecycle(state: ComponentAdminLifecycleState): ComponentAdminLifecycleEvidence =
    ComponentAdminLifecycleEvidence(state, state.toString.toLowerCase, _provenance)

  private def _target(
    lifecycle: ComponentAdminLifecycleEvidence = _active_lifecycle,
    loadedinstance: ComponentInstanceId = _instance
  ): ComponentAdminManagementTarget =
    ComponentAdminManagementTarget(_component, loadedinstance, _identity_binding, lifecycle)

  private def _registration(
    selector: String,
    authorization: OperationAuthorizationRule = OperationAuthorizationRule(),
    availability: ComponentAdminManagementAvailability = ComponentAdminManagementAvailability.Available
  ): ComponentAdminManagementActionRegistration =
    ComponentAdminManagementActionRegistration(ComponentAdminManagementActionSelector(selector), authorization, availability, false)

  private def _registrations(
    unavailable: Option[String] = None,
    rules: Map[String, OperationAuthorizationRule] = Map.empty
  ): Vector[ComponentAdminManagementActionRegistration] =
    _management_selectors.map(selector =>
      _registration(
        selector,
        rules.getOrElse(selector, OperationAuthorizationRule()),
        if (unavailable.contains(selector)) ComponentAdminManagementAvailability.Unavailable else ComponentAdminManagementAvailability.Available
      )
    )

  private def _ordinary_queries(): Vector[ComponentAdminOrdinaryQuery] =
    _ordinary_selectors.map(selector => ComponentAdminOrdinaryQuery(ComponentAdminManagementActionSelector(selector), visible = true))

  private def _catalog(
    lifecycle: ComponentAdminLifecycleEvidence = _active_lifecycle,
    unavailable: Option[String] = None,
    rules: Map[String, OperationAuthorizationRule] = Map.empty
  ): ComponentAdminAuthorizedManagementCatalog =
    ComponentAdminAuthorizedManagement.createC(_target(lifecycle), _registrations(unavailable, rules), _ordinary_queries()).toOption.get

  private def _input(
    selector: String,
    representation: String = "validated-input",
    validated: Boolean = true
  ): ComponentAdminManagementActionInput =
    ComponentAdminManagementActionInput(ComponentAdminManagementActionSelector(selector), representation, validated, _provenance)

  private def _request(
    selector: String,
    lifecycle: ComponentAdminLifecycleEvidence = _active_lifecycle,
    loadedinstance: ComponentInstanceId = _instance,
    identitybinding: ComponentAdminRuntimeIdentityBinding = _identity_binding,
    input: ComponentAdminManagementActionInput = null,
    key: String = "request-1"
  ): ComponentAdminManagementRequest =
    ComponentAdminManagementRequest(
      _component,
      loadedinstance,
      identitybinding,
      lifecycle,
      ComponentAdminManagementActionSelector(selector),
      Option(input).getOrElse(_input(selector)),
      key
    )
}
