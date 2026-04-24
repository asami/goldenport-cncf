package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, RuntimeContext, SecurityContext}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationAuthorizationSpec extends AnyWordSpec with Matchers {
  "OperationAuthorization" should {
    "allow an authenticated subject when no operation mode restriction is declared" in {
      given ExecutionContext = _context(OperationMode.Production, SecurityContext.Privilege.User)
      val rule = OperationAuthorizationRule()

      OperationAuthorization.authorize("admin.system.ping", rule) shouldBe Consequence.unit
    }

    "deny anonymous access unless the operation rule explicitly allows it" in {
      given ExecutionContext = _context(OperationMode.Develop, SecurityContext.Privilege.Anonymous)
      val rule = OperationAuthorizationRule()

      OperationAuthorization.authorize("admin.system.ping", rule) shouldBe a[Consequence.Failure[_]]
    }

    "allow anonymous access only in the declared anonymous operation modes" in {
      val rule = OperationAuthorizationRule(
        allowAnonymous = true,
        anonymousOperationModes = Vector(OperationMode.Develop, OperationMode.Test)
      )

      {
        given ExecutionContext = _context(OperationMode.Develop, SecurityContext.Privilege.Anonymous)
        OperationAuthorization.authorize("admin.system.ping", rule) shouldBe Consequence.unit
      }
      {
        given ExecutionContext = _context(OperationMode.Production, SecurityContext.Privilege.Anonymous)
        OperationAuthorization.authorize("admin.system.ping", rule) shouldBe a[Consequence.Failure[_]]
      }
    }

    "deny any subject outside the operation modes declared by the operation" in {
      given ExecutionContext = _context(OperationMode.Production, SecurityContext.Privilege.User)
      val rule = OperationAuthorizationRule(
        operationModes = Vector(OperationMode.Develop, OperationMode.Test)
      )

      OperationAuthorization.authorize("notice-board.notice.draft", rule) shouldBe a[Consequence.Failure[_]]
    }

    "require authenticated provenance when provider authentication is required" in {
      val rule = OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true
      )

      {
        given ExecutionContext = _context(
          OperationMode.Production,
          SecurityContext.Privilege.System,
          Map(
            "authenticated" -> "true",
            "role" -> "system_admin",
            "privilege" -> "system"
          )
        )
        OperationAuthorization.authorize("admin.system.ping", rule) shouldBe a[Consequence.Failure[_]]
      }
      {
        given ExecutionContext = _context(
          OperationMode.Production,
          SecurityContext.Privilege.System,
          Map(
            "authenticated" -> "true",
            SecuritySubject.AuthenticationProvenanceAttribute ->
              SecuritySubject.ProviderAuthenticationProvenance,
            "role" -> "system_admin",
            "privilege" -> "system"
          )
        )
        OperationAuthorization.authorize("admin.system.ping", rule) shouldBe Consequence.unit
      }
    }
  }

  private def _context(
    operationMode: OperationMode,
    privilege: SecurityContext.Privilege,
    extraAttributes: Map[String, String] = Map.empty
  ): ExecutionContext = {
    val base0 = ExecutionContext.create(privilege)
    val base =
      if (extraAttributes.isEmpty)
        base0
      else
        ExecutionContext.withSecurityContext(
          base0,
          base0.security.copy(principal = new Principal {
            val id: PrincipalId = base0.security.principal.id
            val attributes: Map[String, String] =
              base0.security.principal.attributes ++ extraAttributes
          })
        )
    val runtime = new RuntimeContext(
      core = base.runtime.core,
      unitOfWorkSupplier = () => base.unitOfWork,
      unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "operation-authorization-spec",
      operationMode = operationMode
    )
    ExecutionContext.withRuntimeContext(base, runtime)
  }
}
