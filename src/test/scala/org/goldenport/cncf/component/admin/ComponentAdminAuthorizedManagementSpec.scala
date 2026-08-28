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
 *
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
    "E1 separate the finite management inventory from ordinary visible queries" must _e1 {
      "when six supplied Builtin Admin state-changing selectors and ordinary query selectors are cataloged" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R1; Example: E1; six exact management selectors and visible read-only query selectors supplied as values")
        val catalog = _catalog()

        When("the value-only catalog separates the explicitly classified inventory")
        val management = catalog.managementInventory
        val ordinary = catalog.ordinaryInventory

        Then("all six state-changing selectors are management and every ordinary query remains outside that catalog")
        management.map(_.selector.value) shouldBe _management_selectors
        management.forall(_.classification == ComponentAdminManagementClassification.Management) shouldBe true
        management.forall(_.readonly == false) shouldBe true
        management.forall(_.idempotency == ComponentAdminManagementIdempotency.Required) shouldBe true
        ordinary.map(_.selector.value) shouldBe _ordinary_selectors
        ordinary.forall(_.classification == ComponentAdminManagementClassification.Ordinary) shouldBe true
        ordinary.forall(_.visible) shouldBe true
        ordinary.forall(_.readonly) shouldBe true
        ordinary.map(_.idempotency) shouldBe Vector(ComponentAdminManagementIdempotency.NotRequired, ComponentAdminManagementIdempotency.NotRequired)
      }
    }

    "E2 admit an explicitly authorized management command without requiring visibility" must _e2 {
      "when the selected active instance supplies an authorized but hidden management action" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R2; Example: E2; an active selected Component instance, explicit rule, and hidden entity/create selector")
        given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
        val catalog = _catalog()
        val request = _request("entity/create")

        When("the pure admission boundary consumes the stored OperationAuthorizationRule")
        val outcome = ComponentAdminAuthorizedManagement.admit(catalog, request)

        Then("the command is admitted and its audit binds every supplied identity to the current principal")
        outcome shouldBe a[Admitted]
        catalog.managementInventory.find(_.selector.value == "entity/create").map(_.visible) shouldBe Some(false)
        outcome.audit.componentid shouldBe _component
        outcome.audit.loadedinstanceid shouldBe _instance
        outcome.audit.selector shouldBe request.selector
        outcome.audit.principalid shouldBe PrincipalId("test-user-principal")
        outcome.audit.lifecycle shouldBe request.lifecycle
        outcome.audit.idempotencykey shouldBe "request-1"
        outcome.audit.disposition shouldBe ComponentAdminManagementDisposition.Admitted
      }
    }

    "E3 forbid anonymous management and keep visible ordinary queries from gaining authority" must _e3 {
      "when caller identity or operation classification cannot satisfy management admission" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R3; Example: E3; a denied anonymous subject and a visible read-only ordinary selector")
        val catalog = _catalog()

        When("the anonymous caller requests an authorized management selector")
        val anonymousoutcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.Anonymous)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("entity/update"))
        }

        Then("the authorization failure is a typed Forbidden outcome with no raw authorization text")
        anonymousoutcome shouldBe a[Forbidden]
        anonymousoutcome.audit.disposition shouldBe ComponentAdminManagementDisposition.Forbidden
        anonymousoutcome.toString should not include "Operation access is denied"

        Given("the same selected Component facts and a visible ordinary query operation")
        val ordinaryoutcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("entity/list"))
        }

        Then("ordinary visibility remains read-only context and never receives management authority")
        ordinaryoutcome shouldBe a[Forbidden]
        ordinaryoutcome.audit.selector.value shouldBe "entity/list"
        ordinaryoutcome.audit.disposition shouldBe ComponentAdminManagementDisposition.Forbidden
      }
    }

    "E4 return deterministic retry and lifecycle conflicts" must _e4 {
      "when idempotency is malformed or the selected target is stopped or failed" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R4; Example: E4; one active action and stopped/failed lifecycle facts with blank keys")
        val catalog = _catalog()

        When("the action is requested with a blank idempotency key")
        val missingkey = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, _request("data/create", key = "  "))
        }

        Then("the boundary requests a retry without invoking or admitting the action")
        missingkey shouldBe a[RetryRequired]
        missingkey.audit.disposition shouldBe ComponentAdminManagementDisposition.RetryRequired

        val lifecycles = Vector(
          ComponentAdminManagementLifecycle.Stopped,
          ComponentAdminManagementLifecycle.Failed
        )
        val conflicts = lifecycles.map { targetlifecycle =>
          val stoppedcatalog = _catalog(targetlifecycle)
          val request = _request("data/update", lifecycle = targetlifecycle)
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(stoppedcatalog, request)
        }

        Then("both stopped and failed target lifecycles are deterministic Conflict outcomes")
        conflicts.forall(_.isInstanceOf[Conflict]) shouldBe true
        conflicts.map(_.audit.disposition) shouldBe Vector(
          ComponentAdminManagementDisposition.Conflict,
          ComponentAdminManagementDisposition.Conflict
        )
      }
    }

    "E5 distinguish unavailable actions from stale loaded instances" must _e5 {
      "when an admitted selector is unavailable or another exact instance is supplied" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R5; Example: E5; explicit availability evidence and a foreign loaded-instance identity")
        val unavailablecatalog = _catalog(unavailable = Some("association/admin_attach_association"))
        val staleinstance = ComponentInstanceId(_component, "green")

        When("the selected action is unavailable")
        val unavailableoutcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(unavailablecatalog, _request("association/admin_attach_association"))
        }

        Then("unavailable action evidence remains an Unavailable outcome")
        unavailableoutcome shouldBe a[Unavailable]
        unavailableoutcome.audit.disposition shouldBe ComponentAdminManagementDisposition.Unavailable

        When("the request names another loaded instance without fallback")
        val staleoutcome = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(_catalog(), _request("association/admin_detach_association", loadedinstance = staleinstance))
        }

        Then("the exact instance mismatch remains a StaleInstance outcome")
        staleoutcome shouldBe a[StaleInstance]
        staleoutcome.audit.loadedinstanceid shouldBe staleinstance
        staleoutcome.audit.disposition shouldBe ComponentAdminManagementDisposition.StaleInstance
      }
    }

    "E6 replay an accepted request idempotently" must _e6 {
      "when an attributable accepted audit receipt matches the exact request identity and key" in {
        Given("Spec: component-admin-authorized-management; Rules: ADM07A-R6; Example: E6; one admitted management request and its supplied audit receipt")
        val catalog = _catalog()
        val request = _request("entity/update", key = "same-request")

        When("the first admission is accepted and the same request is presented with that receipt")
        val first = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, request)
        }
        val replay = {
          given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
          ComponentAdminAuthorizedManagement.admit(catalog, request, Vector(first.audit))
        }

        Then("the matching receipt returns IdempotentReplay instead of another admission")
        first shouldBe a[Admitted]
        replay shouldBe a[IdempotentReplay]
        replay.audit.componentid shouldBe request.componentid
        replay.audit.loadedinstanceid shouldBe request.loadedinstanceid
        replay.audit.selector shouldBe request.selector
        replay.audit.principalid shouldBe PrincipalId("test-user-principal")
        replay.audit.lifecycle shouldBe request.lifecycle
        replay.audit.idempotencykey shouldBe "same-request"
        replay.audit.disposition shouldBe ComponentAdminManagementDisposition.IdempotentReplay
      }
    }

    "property-based visibility and attribution" should {
      "E7 preserve exact identity while visibility never grants ordinary management authority" must _e7 {
        "when fifty generated visible or hidden ordinary selectors are supplied" in {
          Given("Spec: component-admin-authorized-management; Rules: ADM07A-R7; Example: E7; generated ordinary query visibility and exact selected instances")
          val numbers = Gen.choose(1, 100000)
          val visibilities = Gen.oneOf(true, false)
          val property = Prop.forAll(numbers, visibilities) { (number, visibility) =>
            val instance = ComponentInstanceId(_component, s"node$number")
            val target = ComponentAdminManagementTarget(
              _component,
              instance,
              ComponentAdminManagementLifecycle.Active
            )
            val selector = ComponentAdminManagementActionSelector(s"query/$number")
            val ordinary = ComponentAdminManagementOperation(
              selector,
              ComponentAdminManagementClassification.Ordinary,
              OperationAuthorizationRule(),
              Set(ComponentAdminManagementLifecycle.Active),
              ComponentAdminManagementAvailability.Available,
              ComponentAdminManagementIdempotency.NotRequired,
              visibility,
              true
            )
            val catalog = ComponentAdminAuthorizedManagement.createC(target, Vector(ordinary)).toOption
            val request = ComponentAdminManagementRequest(
              _component,
              instance,
              ComponentAdminManagementLifecycle.Active,
              selector,
              s"key-$number"
            )
            given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)
            catalog.exists { selectedcatalog =>
              val outcome = ComponentAdminAuthorizedManagement.admit(selectedcatalog, request)
              outcome.isInstanceOf[Forbidden] &&
                outcome.audit.componentid == _component &&
                outcome.audit.loadedinstanceid == instance &&
                outcome.audit.selector == selector &&
                outcome.audit.principalid == PrincipalId("test-user-principal") &&
                outcome.audit.lifecycle == ComponentAdminManagementLifecycle.Active &&
                outcome.audit.idempotencykey == s"key-$number" &&
                outcome.audit.disposition == ComponentAdminManagementDisposition.Forbidden
            }
          }

          When("the pure boundary evaluates at least fifty independent generated values")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

          Then("visibility remains non-authoritative and all selected identity and audit fields remain exact")
          checked.passed shouldBe true
        }
      }
    }
  }

  private val _component = ComponentId("org.goldenport.cncf.admin.Sample")
  private val _instance = ComponentInstanceId(_component, "blue")
  private val _management_selectors = Vector(
    "entity/create",
    "entity/update",
    "data/create",
    "data/update",
    "association/admin_attach_association",
    "association/admin_detach_association"
  )
  private val _ordinary_selectors = Vector("entity/list", "data/list")

  private def _catalog(
    lifecycle: ComponentAdminManagementLifecycle = ComponentAdminManagementLifecycle.Active,
    unavailable: Option[String] = None
  ): ComponentAdminAuthorizedManagementCatalog = {
    val target = ComponentAdminManagementTarget(_component, _instance, lifecycle)
    val management = _management_selectors.map { selector =>
      val availability = if (unavailable.contains(selector))
        ComponentAdminManagementAvailability.Unavailable
      else
        ComponentAdminManagementAvailability.Available
      ComponentAdminManagementOperation(
        ComponentAdminManagementActionSelector(selector),
        ComponentAdminManagementClassification.Management,
        OperationAuthorizationRule(),
        Set(ComponentAdminManagementLifecycle.Active),
        availability,
        ComponentAdminManagementIdempotency.Required,
        false,
        false
      )
    }
    val ordinary = _ordinary_selectors.map { selector =>
      ComponentAdminManagementOperation(
        ComponentAdminManagementActionSelector(selector),
        ComponentAdminManagementClassification.Ordinary,
        OperationAuthorizationRule(),
        Set(ComponentAdminManagementLifecycle.Active),
        ComponentAdminManagementAvailability.Available,
        ComponentAdminManagementIdempotency.NotRequired,
        true,
        true
      )
    }
    ComponentAdminAuthorizedManagement
      .createC(target, management ++ ordinary)
      .toOption
      .getOrElse(fail("valid authorized-management catalog was rejected"))
  }

  private def _request(
    selector: String,
    lifecycle: ComponentAdminManagementLifecycle = ComponentAdminManagementLifecycle.Active,
    loadedinstance: ComponentInstanceId = _instance,
    key: String = "request-1"
  ): ComponentAdminManagementRequest =
    ComponentAdminManagementRequest(
      _component,
      loadedinstance,
      lifecycle,
      ComponentAdminManagementActionSelector(selector),
      key
    )
}
