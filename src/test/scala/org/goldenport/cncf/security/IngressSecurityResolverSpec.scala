package org.goldenport.cncf.security

import java.time.ZoneId
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.config.{
  CncfConfigurationParameterCatalog,
  CncfConfigurationResolutionContext,
  CncfConfigurationTarget,
  OperationMode,
  RuntimeConfig
}
import org.goldenport.cncf.context.{
  CorrelationId,
  Capability,
  ExecutionContext,
  GlobalRuntimeContext,
  PrincipalId,
  ScopeContext,
  ScopeKind,
  SecurityLevel,
  SubjectKind,
  TraceId
}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{
  GenericSubsystemAuthenticationBinding,
  GenericSubsystemAuthenticationProviderBinding,
  GenericSubsystemComponentBinding,
  GenericSubsystemDescriptor,
  GenericSubsystemLocalSubjectBinding,
  GenericSubsystemSecurityBinding,
  Subsystem,
  SubsystemExecutionProfile
}
import org.goldenport.cncf.event.EventReception
import org.goldenport.cncf.job.{ActionId, JobId, TaskId}
import org.goldenport.configuration.{
  Configuration,
  ConfigurationBindingCandidate,
  ConfigurationBindingCandidates,
  ConfigurationBindingCollection,
  ConfigurationBindingResolver,
  ConfigurationOrigin,
  ConfigurationParameter,
  ConfigurationProvenance,
  ConfigurationTrace,
  ConfigurationValue,
  ResolvedConfiguration
}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 *  version Apr. 28, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class IngressSecurityResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _fixed_profile_service_ingress_e1 = afterWord(
    "in spec:fixed-profile-service-ingress, example:E1, rules:R1,R2,R3, phase:M2"
  )

  "IngressSecurityResolver" should {
    "resolve direct privilege and context" which {
      "resolve anonymous privilege when no protocol security attributes are present" in {
        Given("a request without protocol security attributes")
        val request = Request.of(component = "domain", service = "entity", operation = "loadPerson")

        When("ingress security resolves the request")
        val result = IngressSecurityResolver.resolve(request)

        Then("the anonymous subject and capability are supplied")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "anonymous"
        resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
        SecuritySubject.from(resolved.executionContext.security).isAnonymous shouldBe true
        resolved.executionContext.security.hasCapability("anonymous") shouldBe true
      }

      "resolve content-manager privilege from request properties" in {
        Given("a request carrying the application content-manager privilege")
        val request = Request
          .of(component = "domain", service = "entity", operation = "loadPerson")
          .copy(
            properties = List(
              Property("privilege", "application_content_manager", None)
            )
          )

        When("ingress security resolves the request")
        val result = IngressSecurityResolver.resolve(request)
        Then("the content-manager subject and capability are resolved")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.level shouldBe SecurityLevel("content_manager")
        resolved.executionContext.security.hasCapability("content-manager") shouldBe true
      }

      "deny when requested capability is not granted by resolved privilege" in {
        Given("a user request requiring an ungranted content-manager capability")
        val request = Request
          .of(component = "domain", service = "entity", operation = "loadPerson")
          .copy(
            properties = List(
              Property("privilege", "user", None),
              Property("capability", "content_manager", None)
            )
          )

        When("ingress security resolves the request")
        val result = IngressSecurityResolver.resolve(request)
        Then("the capability requirement is rejected")
        result shouldBe a[Consequence.Failure[_]]
      }

      "treat raw request capability as a requirement, not a subject grant" in {
        Given("a user request whose capability value is not a subject grant")
        val request = Request
          .of(component = "domain", service = "entity", operation = "loadPerson")
          .copy(
            properties = List(
              Property("privilege", "user", None),
              Property("capability", "collection:blob:create", None)
            )
          )

        When("ingress security resolves the request")
        val result = IngressSecurityResolver.resolve(request)

        Then("the raw capability requirement is rejected")
        result shouldBe a[Consequence.Failure[_]]
      }

      "restore observability and job context from standard event attributes" in {
        Given("standard event security, observability, and job attributes")
        val traceid       = TraceId("subsystem_a", "runtime")
        val correlationid = CorrelationId("subsystem_a", "runtime")
        val jobid         = JobId.generate()
        val parentjobid   = JobId.generate()
        val taskid        = TaskId.generate()
        val actionid      = ActionId.generate()
        val attrs = Map(
          EventReception.StandardAttribute.SecurityLevel -> "content_manager",
          EventReception.StandardAttribute.TraceId       -> traceid.print,
          EventReception.StandardAttribute.CorrelationId -> correlationid.print,
          EventReception.StandardAttribute.JobId         -> jobid.print,
          EventReception.StandardAttribute.ParentJobId   -> parentjobid.print,
          EventReception.StandardAttribute.TaskId        -> taskid.print,
          EventReception.StandardAttribute.ActionId      -> actionid.print,
          EventReception.StandardAttribute.CausationId   -> "event-12345"
        )

        When("ingress security resolves the event attributes")
        val result = IngressSecurityResolver.resolve(attrs)
        Then("security, observability, and job context are restored")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.level shouldBe SecurityLevel("content_manager")
        resolved.executionContext.observability.traceId.subsystem shouldBe traceid.subsystem
        resolved.executionContext.observability.traceId.entry shouldBe traceid.entry
        resolved.executionContext.observability.correlationId.map(_.subsystem) shouldBe Some(
          correlationid.subsystem
        )
        resolved.executionContext.observability.correlationId.map(_.boundary) shouldBe Some(
          correlationid.boundary
        )
        resolved.executionContext.jobContext.jobId.map(_.major) shouldBe Some(jobid.major)
        resolved.executionContext.jobContext.jobId.map(_.minor) shouldBe Some(jobid.minor)
        resolved.executionContext.jobContext.parentJobId.map(_.major) shouldBe Some(
          parentjobid.major
        )
        resolved.executionContext.jobContext.parentJobId.map(_.minor) shouldBe Some(
          parentjobid.minor
        )
        resolved.executionContext.jobContext.taskId.map(_.major) shouldBe Some(taskid.major)
        resolved.executionContext.jobContext.taskId.map(_.minor) shouldBe Some(taskid.minor)
        resolved.executionContext.jobContext.currentTask.map(_.major) shouldBe Some(taskid.major)
        resolved.executionContext.jobContext.currentTask.map(_.minor) shouldBe Some(taskid.minor)
        resolved.executionContext.jobContext.actionId.map(_.major) shouldBe Some(actionid.major)
        resolved.executionContext.jobContext.actionId.map(_.minor) shouldBe Some(actionid.minor)
        resolved.executionContext.jobContext.causationId shouldBe Some("event-12345")
        resolved.executionContext.jobContext.taskStack shouldBe Vector.empty
        resolved.executionContext.jobContext.traceMetadata.get("traceId").nonEmpty shouldBe true
        resolved.executionContext.jobContext.traceMetadata.get("correlationId").nonEmpty shouldBe true
      }

      "bind subject attributes and principal id into fallback security context" in {
        Given("fallback subject identity and customer attributes")
        val attrs = Map(
          "principal.id"        -> "customer-a-user",
          "subject.customer_id" -> "customer-a"
        )

        When("ingress security resolves the fallback attributes")
        val result = IngressSecurityResolver.resolve(attrs)

        Then("the principal and customer subject attribute are bound")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "customer-a-user"
        SecuritySubject.from(resolved.executionContext.security).attributeValues(
          "customerId"
        ) should contain("customer-a")
      }

      "use the framework default operation mode on the direct global compatibility path" in {
        Given("a global runtime whose raw operation mode conflicts with the framework default")
        _with_global_runtime(
          Locale.JAPAN,
          ZoneId.of("Asia/Tokyo"),
          operationmode = Some(OperationMode.Production.name)
        ) {
          val rawoperationmode = GlobalRuntimeContext.current.get.config.operationMode
          rawoperationmode shouldBe OperationMode.Production
          rawoperationmode should not be RuntimeConfig.defaultOperationMode

          When("anonymous ingress creates its direct compatibility runtime")
          val result = IngressSecurityResolver.resolve(Map.empty[String, String])

          Then("the direct runtime uses the framework default operation mode")
          result shouldBe a[Consequence.Success[_]]
          result.toOption.get.executionContext.runtime.operationMode shouldBe RuntimeConfig.defaultOperationMode
        }
      }
    }

    "resolve provider and federation authentication" which {
      "deny when authentication providers do not resolve and privilege fallback is disabled by resolved wiring" in {
        Given("a subsystem with authentication providers and disabled privilege fallback")
        val subsystem = _subsystem(fallbackenabled = false)
        val base      = subsystem.components.head.logic.executionContext()

        When("an unmatched access token reaches ingress")
        val result = IngressSecurityResolver.resolve(
          base,
          Map("access_token" -> "missing-token", "capability" -> "content_manager")
        )

        Then("authentication is rejected without privilege fallback")
        result shouldBe a[Consequence.Failure[_]]
      }

      "try the next provider when the first provider does not match" in {
        Given("two providers where only the second accepts the access token")
        val subsystem = _subsystem(
          fallbackenabled = true,
          providers = Vector(
            _provider("first-provider", _ => Consequence.success(None)),
            _provider(
              "second-provider",
              _ =>
                Consequence.success(Some(AuthenticationResult(
                  PrincipalId("user-2"),
                  attributes = Map.empty
                )))
            )
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress checks the providers in order")
        val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "matched-token"))

        Then("the matching provider supplies an authenticated subject")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "user-2"
        SecuritySubject.from(resolved.executionContext.security).isAuthenticated shouldBe true
        SecuritySubject.from(resolved.executionContext.security).isProviderAuthenticated shouldBe true
      }

      "normalize a federation callback through the selected provider without retaining secrets" in {
        Given("a provider that accepts one normalized federation callback")
        val session = org.goldenport.cncf.context.SessionContext(
          sessionId = Some("access-session-1"),
          refreshSessionId = Some("refresh-session-1")
        )
        val subsystem = _subsystem(
          fallbackenabled = false,
          providers = Vector(
            _provider(
              "federation-provider",
              request =>
                if (
                  request.federationProvider.contains("example") &&
                  request.federationAssertion.contains("provider-assertion") &&
                  request.federationAuthorizationCode.contains("callback-code") &&
                  request.federationCallbackState.contains("callback-state")
                )
                  Consequence.success(Some(AuthenticationResult(
                    PrincipalId("federated-user"),
                    attributes = Map(
                      "email"                         -> "user@example.test",
                      "federation_provider"           -> "example",
                      "federation_assertion"          -> "provider-assertion",
                      "federation_authorization_code" -> "callback-code",
                      "federation_state"              -> "callback-state",
                      "oauth.id_token"                -> "id-token",
                      "provider_access_token"         -> "access-token",
                      "clientSecret"                  -> "client-secret",
                      "oauth.state"                   -> "oauth-state"
                    ),
                    session = Some(session)
                  )))
                else
                  Consequence.success(None)
            )
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("the matching callback reaches ingress security")
        val result = IngressSecurityResolver.resolve(
          base,
          Map(
            "federation_provider"           -> "example",
            "federation_assertion"          -> "provider-assertion",
            "federation_authorization_code" -> "callback-code",
            "federation_state"              -> "callback-state"
          )
        )

        Then("the normalized subject and session reach the UnitOfWork without callback secrets")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        val security = resolved.executionContext.security
        security.principal.id.value shouldBe "federated-user"
        security.principal.attributes.get("email") shouldBe Some("user@example.test")
        security.principal.attributes.get("federation_provider") shouldBe Some("example")
        security.principal.attributes should not contain key("federation.assertion")
        security.principal.attributes should not contain key("federation_assertion")
        security.principal.attributes should not contain key("federation_authorization_code")
        security.principal.attributes should not contain key("federation.authorization_code")
        security.principal.attributes should not contain key("federation_state")
        security.principal.attributes should not contain key("federation.state")
        security.principal.attributes should not contain key("oauth.id_token")
        security.principal.attributes should not contain key("provider_access_token")
        security.principal.attributes should not contain key("clientSecret")
        security.principal.attributes should not contain key("oauth.state")
        security.session.flatMap(_.sessionId) shouldBe Some("access-session-1")
        security.session.flatMap(_.refreshSessionId) shouldBe Some("refresh-session-1")
        resolved.executionContext.runtime.unitOfWork.executionContext.security.principal.id.value shouldBe "federated-user"
        SecuritySubject.from(security).isProviderAuthenticated shouldBe true
      }

      "reject each unmatched federation callback field without privilege fallback" in {
        Given("a subsystem whose ordinary privilege fallback is enabled")
        val subsystem = _subsystem(fallbackenabled = true)
        val base      = subsystem.components.head.logic.executionContext()
        val callbacks = Vector(
          "federation.assertion",
          "federation.authorization_code",
          "federation.state"
        )

        When("a federation callback does not match any provider")
        val results = callbacks.map { callback =>
          callback -> IngressSecurityResolver.resolve(
            base,
            Map(
              callback    -> "unmatched-callback",
              "privilege" -> "application_content_manager"
            )
          )
        }

        Then("the callback is rejected rather than converted to a privileged context")
        results.foreach { case (callback, result) =>
          withClue(s"callback=$callback: ") {
            result shouldBe a[Consequence.Failure[_]]
          }
        }
      }

      "propagate provider failure instead of falling back to privilege resolution" in {
        Given("a failing provider and an enabled privilege fallback")
        val subsystem = _subsystem(
          fallbackenabled = true,
          providers = Vector(
            _provider("failing-provider", _ => Consequence.argumentInvalid("invalid credentials"))
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("an access token and privilege fallback reach ingress")
        val result = IngressSecurityResolver.resolve(
          base,
          Map(
            "access_token" -> "bad-token",
            "privilege"    -> "application_content_manager"
          )
        )

        Then("the provider failure is propagated")
        result shouldBe a[Consequence.Failure[_]]
      }

      "allow anonymous resolution when providers are configured but no authentication material is present" in {
        Given("a subsystem with providers but no authentication material")
        val subsystem = _subsystem(fallbackenabled = false)
        val base      = subsystem.components.head.logic.executionContext()

        When("ingress resolves an empty attribute set")
        val result = IngressSecurityResolver.resolve(base, Map.empty[String, String])

        Then("anonymous resolution remains available")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "anonymous"
        resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
      }
    }

    "resolve fixed and controlled execution profiles" which {
      "install the trusted local subject when standalone ingress has no authentication material" in {
        Given("a standalone subsystem with a trusted local subject and no authentication material")
        val subsystem = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject)
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress resolves the empty attribute set")
        val result = IngressSecurityResolver.resolve(base, Map.empty[String, String])

        Then("the trusted local subject is installed")
        result shouldBe a[Consequence.Success[_]]
        val security = result.toOption.get.executionContext.security
        val subject  = SecuritySubject.from(security)
        security.principal.id.value shouldBe "standalone-local"
        security.level shouldBe SecurityLevel("user")
        security.hasCapability("notification:read") shouldBe true
        security.principal.attributes.get("installation") shouldBe Some("standalone")
        security.principal.attributes should not contain key("local_subject")
        security.principal.attributes should not contain key("subject_kind")
        subject.isAuthenticated shouldBe true
        subject.isProviderAuthenticated shouldBe false
        subject.hasRole("user") shouldBe true
      }

      "retain the trusted local subject when a stale Web session cookie is present" in {
        Given("a fixed subsystem with a trusted local subject and a stale ArtScene session cookie")
        val subsystem = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject),
          providers = Vector.empty
        )
        val base = subsystem.components.head.logic.executionContext()

        When("the fixed profile resolves the stale session cookie")
        val result = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.Fixed,
          base,
          Map("cookie" -> "textus-session-textus-art-scene=stale-session")
        )

        Then("the configured local user remains authoritative and provider authentication is absent")
        result shouldBe a[Consequence.Success[_]]
        val security = result.toOption.get.executionContext.security
        val subject  = SecuritySubject.from(security)
        security.principal.id.value shouldBe "standalone-local"
        security.subjectKind shouldBe SubjectKind.User
        security.hasCapability("user") shouldBe true
        security.hasCapability("notification:read") shouldBe true
        subject.isProviderAuthenticated shouldBe false
      }

      "require provider authentication for an explicit session header" in {
        Given("a fixed subsystem with a trusted local subject but no authentication provider")
        val subsystem = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject),
          providers = Vector.empty
        )
        val base = subsystem.components.head.logic.executionContext()

        When("the fixed profile receives an explicit session header")
        val result = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.Fixed,
          base,
          Map("x-textus-session" -> "explicit-session")
        )

        Then("the local subject does not bypass provider authentication")
        result shouldBe a[Consequence.Failure[_]]
      }

      "require explicit local-subject evidence for a fixed execution profile" in {
        Given("a subsystem with a configured local subject")
        val subsystem = _subsystem(
          fallbackenabled = true,
          providers = Vector.empty,
          localsubject = Some(_local_subject)
        )
        val base = subsystem.components.head.logic.executionContext()

        When("a fixed profile receives authentication ingress material")
        val result = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.Fixed,
          base,
          Map("access_token" -> "unexpected-token")
        )

        Then("the profile refuses to switch construction paths")
        result shouldBe a[Consequence.Failure[_]]
      }

      "E1 accept a provider-authenticated Service while rejecting user and unmatched ingress" must _fixed_profile_service_ingress_e1 {
        "admit only the provider-authenticated Service subject" in {
          Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R1,R2,R3; Example: E1")
          val subsystem = _subsystem(
            fallbackenabled = false,
            localsubject = Some(_local_subject),
            providers = Vector(
              _provider(
                "launcher-registration-provider",
                request =>
                  request.accessToken match {
                    case Some("launcher-service-token") =>
                      Consequence.success(Some(AuthenticationResult(
                        PrincipalId("launcher-service"),
                        capabilities = Set(Capability("launcher_registration")),
                        level = SecurityLevel("service"),
                        subjectKind = SubjectKind.Service
                      )))
                    case Some("ordinary-user-token") =>
                      Consequence.success(Some(AuthenticationResult(PrincipalId("ordinary-user"))))
                    case _ =>
                      Consequence.success(None)
                  }
              )
            )
          )
          val base = subsystem.components.head.logic.executionContext()

          When("the fixed profile resolves the service token and an ordinary user token")
          val service = IngressSecurityResolver.resolve(
            SubsystemExecutionProfile.Fixed,
            base,
            Map("access_token" -> "launcher-service-token")
          )
          val ordinaryuser = IngressSecurityResolver.resolve(
            SubsystemExecutionProfile.Fixed,
            base,
            Map("access_token" -> "ordinary-user-token")
          )
          val unmatched = IngressSecurityResolver.resolve(
            SubsystemExecutionProfile.Fixed,
            base,
            Map("access_token" -> "unmatched-token")
          )

          Then("only the service retains provider identity and canonical runtime bindings")
          service shouldBe a[Consequence.Success[_]]
          val resolved = service.toOption.get.executionContext
          resolved.security.principal.id.value shouldBe "launcher-service"
          resolved.security.subjectKind shouldBe SubjectKind.Service
          resolved.security.level shouldBe SecurityLevel("service")
          resolved.security.hasCapability("launcher_registration") shouldBe true
          SecuritySubject.from(resolved.security).isProviderAuthenticated shouldBe true
          resolved.runtime.dataStoreSpace should be theSameInstanceAs base.runtime.dataStoreSpace
          resolved.runtime.entityStoreSpace should be theSameInstanceAs base.runtime.entityStoreSpace
          resolved.runtime.unitOfWork.executionContext.security.principal.id shouldBe resolved.security.principal.id
          resolved.security.principal.id.value should not be "standalone-local"
          ordinaryuser shouldBe a[Consequence.Failure[_]]
          unmatched shouldBe a[Consequence.Failure[_]]
        }
      }

      "admit a typed fixed-user profile before ingress and keep it authoritative over hostile request values" in {
        Given("a fixed profile collection and a fixed-user Subsystem with a local subject")
        val collection = _fixed_user_collection
        val subsystem = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject)
        )
        val base = subsystem.components.head.logic.executionContext()

        When("the typed collection is admitted for fixed execution before ingress resolution")
        subsystem.admitRuntimeConfigurationBindingsC(collection, SubsystemExecutionProfile.Fixed)
          .isSuccess shouldBe true
        val result = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.Fixed,
          base,
          Map(
            "principal.id"        -> "hostile-user",
            "subject.displayName" -> "Hostile User",
            "locale"              -> "en-US",
            "timeZone"            -> "America/New_York"
          )
        )

        Then(
          "the admitted profile, rather than ingress strings, supplies the fixed principal and formatting"
        )
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get.executionContext
        resolved.security.principal.id.value shouldBe "fixed-user"
        resolved.security.principal.attributes.get("displayName") shouldBe Some("Fixed User")
        resolved.runtime.context.formatting.locale shouldBe Locale.forLanguageTag("ja-JP")
        resolved.runtime.context.formatting.timezone shouldBe ZoneId.of("Europe/Paris")
        resolved.runtime.unitOfWork.executionContext.security.principal.id.value shouldBe "fixed-user"
        resolved.runtime.unitOfWork.executionContext.runtime.context.formatting
          .locale shouldBe Locale.forLanguageTag("ja-JP")
        resolved.runtime.unitOfWork.executionContext.runtime.context.formatting
          .timezone shouldBe ZoneId.of("Europe/Paris")
      }

      "keep typed fixed-user admission isolated from authenticated and controlled ingress profiles" in {
        Given("the same fixed profile collection admitted under each explicit execution profile")
        val collection = _fixed_user_collection
        val authenticated = _subsystem(
          fallbackenabled = false,
          providers = Vector(_provider(
            "profile-isolation-provider",
            _ =>
              Consequence.success(Some(AuthenticationResult(
                PrincipalId("provider-user"),
                attributes = Map(
                  "displayName" -> "Provider User",
                  "locale"      -> "fr-FR",
                  "timeZone"    -> "America/Los_Angeles"
                )
              )))
          ))
        )
        val controlled        = _subsystem(fallbackenabled = false)
        val authenticatedbase = authenticated.components.head.logic.executionContext()
        val controlledbase    = controlled.components.head.logic.executionContext()

        When("authenticated and controlled profiles receive the collection")
        authenticated.admitRuntimeConfigurationBindingsC(
          collection,
          SubsystemExecutionProfile.Authenticated
        ).isSuccess shouldBe true
        controlled.admitRuntimeConfigurationBindingsC(
          collection,
          SubsystemExecutionProfile.ControlledTest
        ).isSuccess shouldBe true
        val authenticatedresult = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.Authenticated,
          authenticatedbase,
          Map("access_token" -> "provider-token", "locale" -> "en-US", "timeZone" -> "UTC")
        )
        val controlledresult = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.ControlledTest,
          controlledbase,
          Map(
            "principal.id"        -> "controlled-user",
            "subject.displayName" -> "Controlled User",
            "locale"              -> "en-US"
          )
        )

        Then("neither profile inherits the fixed identity or formatting")
        authenticated.resolvedStandaloneUserProfile shouldBe None
        controlled.resolvedStandaloneUserProfile shouldBe None
        authenticatedresult shouldBe a[Consequence.Success[_]]
        val authenticatedcontext = authenticatedresult.toOption.get.executionContext
        authenticatedcontext.security.principal.id.value shouldBe "provider-user"
        authenticatedcontext.security.principal.attributes.get("displayName") shouldBe Some(
          "Provider User"
        )
        authenticatedcontext.runtime.context.formatting.locale shouldBe Locale.forLanguageTag(
          "fr-FR"
        )
        authenticatedcontext.runtime.context.formatting.timezone shouldBe ZoneId.of(
          "America/Los_Angeles"
        )
        controlledresult shouldBe a[Consequence.Success[_]]
        val controlledcontext = controlledresult.toOption.get.executionContext
        controlledcontext.security.principal.id.value shouldBe "controlled-user"
        controlledcontext.security.principal.attributes.get("displayName") shouldBe None
        controlledcontext.security.principal.attributes.get("subject.displayName") shouldBe Some(
          "Controlled User"
        )
        controlledcontext.runtime.context.formatting.locale shouldBe Locale.forLanguageTag("en-US")
        controlledcontext.runtime.context.formatting.timezone shouldBe controlledbase.runtime
          .context.formatting.timezone
      }

      "reject duplicate and late typed fixed-user profile admission" in {
        Given("fixed-user Subsystems before and after user-mode evaluation")
        val collection = _fixed_user_collection
        val duplicate  = _subsystem(fallbackenabled = false, localsubject = Some(_local_subject))
        val late       = _subsystem(fallbackenabled = false, localsubject = Some(_local_subject))

        When("the fixed collection is admitted twice or after user-mode evaluation")
        duplicate.admitRuntimeConfigurationBindingsC(collection, SubsystemExecutionProfile.Fixed)
          .isSuccess shouldBe true
        val duplicateadmission =
          duplicate.admitRuntimeConfigurationBindingsC(collection, SubsystemExecutionProfile.Fixed)
        late.subsystemUserModeC
        val lateadmission =
          late.admitRuntimeConfigurationBindingsC(collection, SubsystemExecutionProfile.Fixed)

        Then("collection admission remains one-shot and must precede user-mode resolution")
        duplicateadmission shouldBe a[Consequence.Failure[_]]
        lateadmission shouldBe a[Consequence.Failure[_]]
      }

      "construct fixed and authenticated profiles through the same canonical runtime bindings" in {
        Given("fixed and provider-authenticated subsystem base contexts")
        val fixedbase = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject)
        ).components.head.logic.executionContext()
        val authenticatedbase = _subsystem(
          fallbackenabled = false,
          providers = Vector(_provider(
            "canonical-provider",
            _ =>
              Consequence.success(Some(AuthenticationResult(
                PrincipalId("canonical-user"),
                attributes = Map("locale" -> "ja-JP")
              )))
          ))
        ).components.head.logic.executionContext()

        When("both explicit profiles resolve their current users")
        val fixed =
          IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed, fixedbase, Map.empty)
        val authenticated = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.Authenticated,
          authenticatedbase,
          Map("access_token" -> "canonical-token")
        )

        Then("security, formatting, datastore, and UnitOfWork stay on canonical execution bindings")
        fixed shouldBe a[Consequence.Success[_]]
        authenticated shouldBe a[Consequence.Success[_]]
        Vector(fixed.toOption.get -> fixedbase, authenticated.toOption.get -> authenticatedbase).foreach {
          case (resolved, base) =>
            resolved.executionContext.runtime.dataStoreSpace should be theSameInstanceAs base
              .runtime.dataStoreSpace
            resolved.executionContext.runtime.entityStoreSpace should be theSameInstanceAs base
              .runtime.entityStoreSpace
            resolved.executionContext.runtime.unitOfWork.executionContext.security.principal
              .id shouldBe resolved.executionContext.security.principal.id
        }
        authenticated.toOption.get.executionContext.runtime.context.formatting
          .locale shouldBe Locale.forLanguageTag("ja-JP")
      }

      "rebind controlled-test request identity onto the supplied runtime context" in {
        Given("a subsystem base context with production identity wiring")
        val base = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject),
          providers = Vector(_provider("unused-provider", _ => Consequence.success(None)))
        ).components.head.logic.executionContext()

        When("a controlled-test profile resolves request-level identity")
        val result = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.ControlledTest,
          base,
          Map("principal.id" -> "controlled-user", "privilege" -> "user")
        )

        Then(
          "the request identity replaces bootstrap security without discarding canonical runtime bindings"
        )
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get.executionContext
        resolved.security.principal.id.value shouldBe "controlled-user"
        resolved.runtime.dataStoreSpace should be theSameInstanceAs base.runtime.dataStoreSpace
        resolved.runtime.entityStoreSpace should be theSameInstanceAs base.runtime.entityStoreSpace
        resolved.runtime.unitOfWork.executionContext.security.principal.id shouldBe resolved
          .security.principal.id
      }

      "never falls back to a local subject for an unauthenticated profile" in {
        Given("a subsystem with both a local subject and a provider that declines the request")
        val subsystem = _subsystem(
          fallbackenabled = true,
          localsubject = Some(_local_subject),
          providers = Vector(
            _provider("declining-provider", _ => Consequence.success(None))
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("an authenticated profile has token evidence but the provider declines it")
        val result = IngressSecurityResolver.resolve(
          SubsystemExecutionProfile.Authenticated,
          base,
          Map("access_token" -> "unaccepted-token")
        )

        Then("authentication failure does not use local or privilege fallback")
        result shouldBe a[Consequence.Failure[_]]
        result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include(
          "did not authenticate"
        )
      }
    }

    "preserve local-subject and privilege-fallback semantics" which {
      "prefer a provider-authenticated subject over the configured local subject" in {
        Given("a subsystem with a provider and a configured local subject")
        val subsystem = _subsystem(
          fallbackenabled = false,
          providers = Vector(
            _provider(
              "account-provider",
              _ =>
                Consequence.success(Some(AuthenticationResult(
                  PrincipalId("account-user"),
                  attributes = Map.empty
                )))
            )
          ),
          localsubject = Some(_local_subject)
        )
        val base = subsystem.components.head.logic.executionContext()

        When("the provider authenticates the access token")
        val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "account-token"))

        Then("the provider subject replaces the local subject")
        result shouldBe a[Consequence.Success[_]]
        val security = result.toOption.get.executionContext.security
        security.principal.id.value shouldBe "account-user"
        SecuritySubject.from(security).isProviderAuthenticated shouldBe true
        security.principal.attributes should not contain key("local_subject")
      }

      "reject unmatched credentials instead of substituting the configured local subject" in {
        Given("a subsystem with a local subject and an unmatched access token")
        val subsystem = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject)
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress resolves the unmatched credentials")
        val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "missing-token"))

        Then("the credentials are rejected without local-subject substitution")
        result shouldBe a[Consequence.Failure[_]]
      }

      "keep an unresolved session outside the configured local subject" in {
        Given("a subsystem with a local subject and an unresolved session id")
        val subsystem = _subsystem(
          fallbackenabled = false,
          localsubject = Some(_local_subject)
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress resolves the missing session")
        val result =
          IngressSecurityResolver.resolve(base, Map("x-textus-session" -> "missing-session"))

        Then("the unresolved session remains anonymous")
        result shouldBe a[Consequence.Success[_]]
        val security = result.toOption.get.executionContext.security
        security.principal.id.value shouldBe "anonymous"
        security.principal.attributes should not contain key("local_subject")
      }

      "allow anonymous resolution for public signup attributes when providers do not match and fallback privilege is disabled" in {
        Given("public signup attributes with no matching provider")
        val subsystem = _subsystem(fallbackenabled = false)
        val base      = subsystem.components.head.logic.executionContext()

        When("ingress resolves the signup attributes")
        val result = IngressSecurityResolver.resolve(
          base,
          Map(
            "loginName" -> "new-user",
            "email"     -> "new-user@example.com",
            "password"  -> "secret123"
          )
        )

        Then("the public signup request remains anonymous")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "anonymous"
        resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
      }

      "allow anonymous resolution when only a stale session id is present and fallback privilege is disabled" in {
        Given("public login attributes and a stale session id")
        val subsystem = _subsystem(fallbackenabled = false)
        val base      = subsystem.components.head.logic.executionContext()

        When("ingress resolves the stale session and login attributes")
        val result = IngressSecurityResolver.resolve(
          base,
          Map(
            "x-textus-session" -> "missing-session",
            "loginName"        -> "new-user",
            "email"            -> "new-user@example.com",
            "password"         -> "secret123"
          )
        )

        Then("the stale session does not prevent anonymous resolution")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "anonymous"
        resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
      }

      "allow anonymous resolution for login credentials when providers do not match and fallback privilege is disabled" in {
        Given("login credentials with no matching provider")
        val subsystem = _subsystem(fallbackenabled = false)
        val base      = subsystem.components.head.logic.executionContext()

        When("ingress resolves the login credentials")
        val result = IngressSecurityResolver.resolve(
          base,
          Map(
            "username" -> "existing-user",
            "password" -> "secret123"
          )
        )

        Then("the unmatched login remains anonymous")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "anonymous"
        resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
      }

      "fallback to privilege resolution when providers do not resolve and fallback remains enabled" in {
        Given("an enabled privilege fallback and an unmatched access token")
        val subsystem = _subsystem(fallbackenabled = true)
        val base      = subsystem.components.head.logic.executionContext()

        When("ingress resolves the token with a content-manager privilege")
        val result = IngressSecurityResolver.resolve(
          base,
          Map(
            "access_token" -> "missing-token",
            "privilege"    -> "application_content_manager"
          )
        )

        Then("privilege fallback supplies the content-manager context")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.level shouldBe SecurityLevel("content_manager")
      }
    }

    "preserve formatting and session metadata" which {
      "preserve authenticated session metadata in execution context security" in {
        Given("an authenticated provider returning session metadata")
        val session = org.goldenport.cncf.context.SessionContext(
          sessionId = Some("sess-1"),
          tokenId = Some("token-1")
        )
        val subsystem = _subsystem(
          fallbackenabled = true,
          providers = Vector(
            _provider(
              "session-provider",
              _ =>
                Consequence.success(Some(AuthenticationResult(
                  PrincipalId("user-3"),
                  attributes = Map.empty,
                  session = Some(session)
                )))
            )
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress resolves the provider access token")
        val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "session-token"))

        Then("session metadata is preserved in the resolved security context")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.session.map(_.sessionId) shouldBe Some(Some("sess-1"))
        resolved.executionContext.security.session.map(_.tokenId) shouldBe Some(Some("token-1"))
        SecuritySubject.from(resolved.executionContext.security).isAuthenticated shouldBe true
      }

      "restore locale and timezone from authenticated provider attributes" in {
        Given("an authenticated provider returning locale and timezone attributes")
        val subsystem = _subsystem(
          fallbackenabled = true,
          providers = Vector(
            _provider(
              "locale-provider",
              _ =>
                Consequence.success(Some(AuthenticationResult(
                  PrincipalId("user-locale"),
                  attributes = Map("locale" -> "ja-JP", "timeZone" -> "Asia/Tokyo")
                )))
            )
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress resolves the provider access token")
        val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "locale-token"))

        Then("provider formatting attributes are restored")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.attributes.get("locale") shouldBe Some("ja-JP")
        resolved.executionContext.security.principal.attributes.get("timeZone") shouldBe Some(
          "Asia/Tokyo"
        )
        resolved.executionContext.runtime.context.formatting.locale shouldBe Locale.forLanguageTag(
          "ja-JP"
        )
        resolved.executionContext.runtime.context.formatting.timezone shouldBe ZoneId.of(
          "Asia/Tokyo"
        )
      }

      "keep execution-profile formatting ahead of conflicting raw configuration aliases" in {
        Given(
          "a global runtime with resolved execution assumptions and conflicting raw locale/timezone aliases"
        )
        _with_global_runtime(Locale.JAPAN, ZoneId.of("Asia/Tokyo")) {
          When("anonymous ingress creates its resolved runtime")
          val result = IngressSecurityResolver.resolve(Map.empty[String, String])

          Then("the execution-profile locale and timezone remain authoritative")
          result shouldBe a[Consequence.Success[_]]
          val formatting = result.toOption.get.executionContext.runtime.context.formatting
          formatting.locale shouldBe Locale.JAPAN
          formatting.timezone shouldBe ZoneId.of("Asia/Tokyo")
        }
      }

      "leave locale unchanged for Accept-Language without explicit Web negotiation policy" in {
        Given("an ingress request whose preferred language is Japanese")
        val base = _subsystem(fallbackenabled = false).components.head.logic.executionContext()

        When("the standard Accept-Language header is resolved")
        val result = IngressSecurityResolver.resolve(
          base,
          Map("Accept-Language" -> "ja-JP,ja;q=0.9,en-US;q=0.8")
        )

        Then("shared ingress does not treat browser negotiation as execution-owned locale")
        result shouldBe a[Consequence.Success[_]]
        result.toOption.get.executionContext.runtime.context.formatting.locale shouldBe base.runtime
          .context.formatting.locale
      }

      "leave locale unchanged for weighted Accept-Language ranges" in {
        Given(
          "an ingress request that lists Japanese first with zero quality and English second with full quality"
        )
        val base = _subsystem(fallbackenabled = false).components.head.logic.executionContext()

        When("the weighted Accept-Language header is resolved")
        val result = IngressSecurityResolver.resolve(
          base,
          Map("Accept-Language" -> "ja-JP;q=0,en-US;q=1")
        )

        Then(
          "shared ingress leaves weighted browser language resolution to the Web policy resolver"
        )
        result shouldBe a[Consequence.Success[_]]
        result.toOption.get.executionContext.runtime.context.formatting.locale shouldBe base.runtime
          .context.formatting.locale
      }
    }

    "resolve session transport precedence" which {
      "restore authenticated security from x-textus-session header" in {
        Given("a provider that resolves the x-textus-session header")
        val subsystem = _subsystem(
          fallbackenabled = true,
          providers = Vector(
            _provider(
              "session-header-provider",
              req =>
                req.sessionId match {
                  case Some("sess-header") =>
                    Consequence.success(
                      Some(
                        AuthenticationResult(
                          PrincipalId("user-header"),
                          session = Some(org.goldenport.cncf.context.SessionContext(sessionId =
                            Some("sess-header")
                          ))
                        )
                      )
                    )
                  case _ =>
                    Consequence.success(None)
                }
            )
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress resolves the session header")
        val result = IngressSecurityResolver.resolve(base, Map("x-textus-session" -> "sess-header"))

        Then("the header session authenticates the subject")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "user-header"
        resolved.executionContext.security.session.flatMap(_.sessionId) shouldBe Some("sess-header")
      }

      "prefer explicit x-textus-session over cookie session value" in {
        Given("a provider resolving distinct header and cookie session ids")
        val subsystem = _subsystem(
          fallbackenabled = true,
          providers = Vector(
            _provider(
              "session-precedence-provider",
              req =>
                req.sessionId match {
                  case Some("sess-header") =>
                    Consequence.success(
                      Some(
                        AuthenticationResult(
                          PrincipalId("user-header"),
                          session = Some(org.goldenport.cncf.context.SessionContext(sessionId =
                            Some("sess-header")
                          ))
                        )
                      )
                    )
                  case Some("sess-cookie") =>
                    Consequence.success(
                      Some(
                        AuthenticationResult(
                          PrincipalId("user-cookie"),
                          session = Some(org.goldenport.cncf.context.SessionContext(sessionId =
                            Some("sess-cookie")
                          ))
                        )
                      )
                    )
                  case _ =>
                    Consequence.success(None)
                }
            )
          )
        )
        val base = subsystem.components.head.logic.executionContext()

        When("ingress resolves both explicit header and cookie sessions")
        val result = IngressSecurityResolver.resolve(
          base,
          Map(
            "x-textus-session" -> "sess-header",
            "cookie"           -> "textus-session-securitytest=sess-cookie"
          )
        )

        Then("the explicit header session takes precedence")
        result shouldBe a[Consequence.Success[_]]
        val resolved = result.toOption.get
        resolved.executionContext.security.principal.id.value shouldBe "user-header"
        resolved.executionContext.security.session.flatMap(_.sessionId) shouldBe Some("sess-header")
      }

    }
  }

  private def _with_global_runtime[A](
    locale: Locale,
    timezone: ZoneId,
    operationmode: Option[String] = None
  )(body: => A): A = {
    val resolvedconfiguration = ResolvedConfiguration(
      Configuration(
        Map(
          "textus.execution.locale"   -> ConfigurationValue.StringValue(locale.toLanguageTag),
          "textus.execution.timezone" -> ConfigurationValue.StringValue(timezone.getId),
          "textus.locale"             -> ConfigurationValue.StringValue("fr-FR"),
          "cncf.locale"               -> ConfigurationValue.StringValue("de-DE"),
          "textus.timeZone"           -> ConfigurationValue.StringValue("America/New_York"),
          "cncf.timezone"             -> ConfigurationValue.StringValue("Europe/Paris")
        ) ++ operationmode.map { value =>
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(value)
        }
      ),
      ConfigurationTrace.empty
    )
    val config = RuntimeConfig.from(resolvedconfiguration)
    val core = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "gcf-09o-ingress-spec",
      parent = None,
      observabilityContext = ExecutionContext.create().observability,
      httpDriverOption = Some(config.httpDriver)
    ).core
    val global = new GlobalRuntimeContext(
      core = core,
      config = config,
      aliasResolver = AliasResolver.empty,
      runtimeMode = org.goldenport.cncf.cli.RunMode.Command,
      commandExecutionMode = None,
      runtimeVersion = org.goldenport.cncf.CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = org.goldenport.cncf.CncfVersion.current,
      resolvedConfiguration = resolvedconfiguration
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(global)
    try body
    finally GlobalRuntimeContext.current = previous
  }

  private def _subsystem(
      fallbackenabled: Boolean,
      providers: Vector[AuthenticationProvider] =
        Vector(_provider("dummy-provider", _ => Consequence.success(None))),
      localsubject: Option[GenericSubsystemLocalSubjectBinding] = None
  ): Subsystem = {
    val subsystem = Subsystem(
      name = "security-test",
      scopeContext = Some(
        ScopeContext(
          kind = ScopeKind.Subsystem,
          name = "security-test",
          parent = None,
          observabilityContext = ExecutionContext.create().observability
        )
      ),
      configuration = org.goldenport.configuration.ResolvedConfiguration(
        org.goldenport.configuration.Configuration.empty,
        org.goldenport.configuration.ConfigurationTrace.empty
      )
    )
    val ownersubsystem = subsystem
    val componentid = ComponentId("org.goldenport.cncf.security.Dummy")
    val component = new Component() {
      override val core: Component.Core =
        Component.Core.create(
          componentid.name,
          componentid,
          ComponentInstanceId.default(componentid),
          Protocol.empty
        )
      override def subsystem: Option[Subsystem]                            = Some(ownersubsystem)
      override def authenticationProviders: Vector[AuthenticationProvider] = providers
    }.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "spec",
        name = "Dummy",
        version = "0.0.0",
        component = Some("dummy")
      )
    )
    val configured = subsystem.withDescriptor(
      GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<memory>"),
        subsystemName = "security-test",
        componentBindings = Vector(GenericSubsystemComponentBinding("dummy")),
        security = Some(
          GenericSubsystemSecurityBinding(
            authentication = Some(
              GenericSubsystemAuthenticationBinding(
                convention = Some("enabled"),
                fallbackPrivilege = Some(if (fallbackenabled) "enabled" else "disabled"),
                localSubject = localsubject,
                providers = providers.map { provider =>
                  GenericSubsystemAuthenticationProviderBinding(
                    name = provider.name,
                    component = "dummy",
                    enabled = Some(true)
                  )
                }
              )
            )
          )
        )
      )
    )
    configured.add(Vector(component))
    require(
      configured.resolvedSecurityWiring.authentication.enabledProviders.flatMap(_.provider).map(
        _.name
      ) == providers.map(_.name),
      s"resolved providers mismatch: ${configured.resolvedSecurityWiring.authentication.enabledProviders.flatMap(_.provider).map(_.name)} vs ${providers.map(_.name)}"
    )
    configured
  }

  private val _local_subject =
    GenericSubsystemLocalSubjectBinding(
      id = "standalone-local",
      roles = Vector("user"),
      capabilities = Vector("user", "notification:read"),
      attributes = Map("installation" -> "standalone"),
      securityLevel = Some("user")
    )

  private def _fixed_user_collection: ConfigurationBindingCollection[CncfConfigurationTarget] = {
    val candidates = Vector[ConfigurationBindingCandidate[?, CncfConfigurationTarget]](
      _candidate(CncfConfigurationParameterCatalog.fixedUserId, "fixed-user"),
      _candidate(CncfConfigurationParameterCatalog.fixedUserDisplayName, "Fixed User"),
      _candidate(CncfConfigurationParameterCatalog.fixedUserLocale, Locale.forLanguageTag("ja-JP")),
      _candidate(CncfConfigurationParameterCatalog.fixedUserTimezone, ZoneId.of("Europe/Paris"))
    )
    val bindings = ConfigurationBindingCandidates.from(candidates).getOrElse(
      fail("fixed-user candidates are required")
    )
    val context = CncfConfigurationResolutionContext.globalOnly.getOrElse(
      fail("fixed-user context is required")
    )
    ConfigurationBindingResolver.resolve(bindings, context.generic).getOrElse(
      fail("fixed-user collection is required")
    )
  }

  private def _candidate[A](
      parameter: ConfigurationParameter[A],
      value: A
  ): ConfigurationBindingCandidate[A, CncfConfigurationTarget] = {
    val provenance = ConfigurationProvenance.create(
      ConfigurationOrigin.Home,
      "textus",
      "gcf07g-fixed-user-profile",
      Some(parameter.id.value),
      Some(parameter.id.value),
      1,
      1,
      Vector("phase-55: gcf07g"),
      false,
      Some("spec")
    ).getOrElse(fail("fixed-user provenance is required"))
    ConfigurationBindingCandidate.create(
      parameter,
      CncfConfigurationTarget.Global,
      value,
      provenance
    ).getOrElse(fail("fixed-user candidate is required"))
  }

  private def _provider(
      providername: String,
      f: AuthenticationRequest => Consequence[Option[AuthenticationResult]]
  ): AuthenticationProvider =
    new AuthenticationProvider {
      override val name: String = providername
      def authenticate(request: AuthenticationRequest)(using
          ExecutionContext
      ): Consequence[Option[AuthenticationResult]] =
        f(request)
    }
}
