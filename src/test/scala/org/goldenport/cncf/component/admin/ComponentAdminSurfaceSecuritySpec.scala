package org.goldenport.cncf.component.admin

import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.{ComponentResourceAuthorization, ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceLogicalIdentity, ComponentResourceSourceKind}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.http.WebDescriptor
import org.goldenport.cncf.knowledge.{ComponentKnowledgeAuthority, ComponentKnowledgeDisclosure, ComponentKnowledgeHelpManifestIdentity, ComponentKnowledgeHelpResourceRoute, ComponentKnowledgeManifestConsumerMetadataEvidence, ComponentKnowledgeManifestConsumerResourceEvidence, ComponentKnowledgeManifestConsumerSafeProvenanceEvidence, ComponentKnowledgeMediaType, ComponentKnowledgeResourceKind, ComponentKnowledgeResourceRole, ComponentKnowledgeSource, ComponentKnowledgeStability}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for ADM-08A: safe Component Admin descriptors
 * retain one validated identity while remaining presentation-only values.
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentAdminSurfaceSecuritySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "ADM-08A Component Admin surface security" should {
    "project one unchanged validated identity into four safe descriptors" in {
      Given("one validated Component, release, loaded instance, Subsystem, implicit Subsystem, restricted documentation route, accepted management catalog, and canonical Web page")
      val view = _view()
      val documentation = _documentation(
        view,
        logicalpath = "manual/hostile/../../source.md",
        helppath = "/help/org-goldenport-cncf-admin-surface/manual/%E6%97%A5%E6%9C%AC%E8%AA%9E",
        availability = ComponentResourceAvailability.Restricted,
        integrity = ComponentResourceIntegrity.Verified,
        authorization = ComponentResourceAuthorization.Denied,
        sourcekind = ComponentResourceSourceKind.ManagedCache
      )
      val catalog = _catalog(view, visible = Set("entity/create"))
      val componentpath = _component_path(view)
      val descriptor = WebDescriptor(adminPages = Vector(
        WebDescriptor.AdminPage("summary", "Summary", s"/web/${componentpath}/admin/summary", "Safe component summary.", Some("admin.entity.read"), Some(componentpath)),
        WebDescriptor.AdminPage("raw", "Raw", "javascript:alert(1)", component = Some(componentpath))
      ))

      When("the pure surface contract projects the supplied values for an authenticated user")
      val projected = {
        given ExecutionContext = _user_context
        ComponentAdminSurfaceSecurity.projectC(view, documentation, catalog, descriptor).toOption
      }

      Then("Web, HTTP, CLI, and machine values retain the exact identity while documentation remains a redacted canonical Help disclosure")
      projected should not be empty
      val surface = projected.get
      Vector(surface.web.identityView, surface.http.identityView, surface.cli.identityView, surface.machine.identityView) shouldBe Vector.fill(4)(view)
      ComponentAdminViewModelCodec.decodeC(surface.machine.json).toOption shouldBe Some(view)
      surface.web.pages.map(_.href) shouldBe Vector(s"/web/${componentpath}/admin/summary")
      surface.http.pages shouldBe surface.web.pages
      surface.cli.pages shouldBe surface.web.pages
      surface.web.documentation.map(_.helpPath) shouldBe Vector("/help/org-goldenport-cncf-admin-surface/manual/%E6%97%A5%E6%9C%AC%E8%AA%9E")
      surface.web.documentation.map(value => (value.availability, value.integrity, value.authorization, value.digest, value.provenance.sourceKind)) shouldBe Vector((ComponentResourceAvailability.Restricted, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied, _digest, ComponentResourceSourceKind.ManagedCache))
      surface.web.documentation.head.productElementNames.toVector should not contain "logicalPath"
      surface.web.documentation.head.productElementNames.toVector should not contain "content"
      surface.web.documentation.head.productElementNames.toVector should not contain "physicalSource"
      surface.web.documentation.head.productElementNames.toVector should not contain "repository"
      surface.web.documentation.head.productElementNames.toVector should not contain "credential"
      surface.web.management.find(_.selector == "entity/create").map(_.eligible) shouldBe Some(true)
    }

    "derive management display without granting management authority" in {
      Given("one exact active catalog whose registered actions vary by visibility, availability, and stored authorization alongside a visible ordinary query")
      val view = _view()
      val documentation = _documentation(view)
      val catalog = _catalog(
        view,
        visible = Set("entity/create", "entity/update", "data/create"),
        unavailable = Set("entity/update"),
        denied = Set("data/create")
      )
      val descriptor = _descriptor(view)
      val variants = Vector(
        _catalog(_view(componentid = ComponentId("org.goldenport.cncf.admin.Other"), release = "2.0.0", instance = "green", subsystem = "other-subsystem"), visible = Set("entity/create")),
        _catalog(_view(release = "2.0.0"), visible = Set("entity/create")),
        _catalog(_view(instance = "green"), visible = Set("entity/create")),
        _catalog(_view(subsystem = "other-subsystem"), visible = Set("entity/create"))
      )

      When("the same supplied values are projected for a user, an anonymous subject, and catalogs targeting another Component, release, instance, or Subsystem")
      val user = {
        given ExecutionContext = _user_context
        ComponentAdminSurfaceSecurity.projectC(view, documentation, catalog, descriptor).toOption.get
      }
      val anonymous = {
        given ExecutionContext = _anonymous_context
        ComponentAdminSurfaceSecurity.projectC(view, documentation, catalog, descriptor).toOption.get
      }
      val mismatched = variants.map { value =>
        given ExecutionContext = _user_context
        ComponentAdminSurfaceSecurity.projectC(view, documentation, value, descriptor).toOption.get
      }

      Then("only the current authorized visible available action is display-eligible, ordinary queries remain non-management, and no identity mismatch falls back")
      user.web.management.map(value => value.selector -> value.eligible).toMap should contain ("entity/create" -> true)
      user.web.management.map(value => value.selector -> value.eligible).toMap should contain ("entity/update" -> false)
      user.web.management.map(value => value.selector -> value.eligible).toMap should contain ("data/create" -> false)
      user.web.ordinaryQueries shouldBe Vector(ComponentAdminOrdinaryQueryDisplay("entity/list", visible = true))
      anonymous.web.management.forall(value => !value.eligible) shouldBe true
      mismatched.map(_.web.management.find(_.selector == "entity/create").map(_.eligible)) shouldBe Vector.fill(variants.size)(Some(false))
    }

    "retain deterministic ordering and encoded disclosure when hostile page segments are supplied" in {
      Given("canonical ordered pages plus generated non-ASCII, traversal, separator, query, fragment, and path-like page segments")
      val view = _view()
      val documentation = _documentation(view, logicalpath = "manual/unsafe/../../raw.md", helppath = "/help/org-goldenport-cncf-admin-surface/manual/%E6%97%A5%E6%9C%AC%E8%AA%9E")
      val catalog = _catalog(view, visible = Set("entity/create"))
      val hostile = Gen.oneOf("日本語", "../escape", "raw/path", "query?debug=true", "fragment#details", "%2f", "white space")
      val property = Prop.forAll(Gen.listOfN(4, hostile)) { segments =>
        val componentpath = _component_path(view)
        val canonical = Vector(
          WebDescriptor.AdminPage("first", "First", s"/web/${componentpath}/admin/first", component = Some(componentpath)),
          WebDescriptor.AdminPage("second", "Second", s"/web/${componentpath}/admin/second", component = Some(componentpath))
        )
        val descriptor = WebDescriptor(adminPages = canonical ++ segments.map { segment =>
          WebDescriptor.AdminPage(segment, segment, s"/web/${componentpath}/admin/${segment}", component = Some(componentpath))
        })
        given ExecutionContext = _user_context
        val first = ComponentAdminSurfaceSecurity.projectC(view, documentation, catalog, descriptor).toOption
        val second = ComponentAdminSurfaceSecurity.projectC(view, documentation, catalog, descriptor).toOption
        first.exists { value =>
          second.contains(value) &&
            value.web.pages.map(_.name) == Vector("first", "second") &&
            value.web.documentation.map(_.helpPath) == Vector("/help/org-goldenport-cncf-admin-surface/manual/%E6%97%A5%E6%9C%AC%E8%AA%9E") &&
            value.machine.json == ComponentAdminViewModelCodec.encode(view)
        }
      }

      When("ScalaCheck projects at least fifty hostile descriptor variations")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("canonical page order and existing encoded Help disclosure remain deterministic without admitting a hostile route")
      checked.passed shouldBe true
    }
  }

  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _management_selectors = Vector(
    "entity/create",
    "entity/update",
    "data/create",
    "data/update",
    "association/admin_attach_association",
    "association/admin_detach_association"
  )
  private val _user_context = ExecutionContext.create(SecurityContext.Privilege.User)
  private val _anonymous_context = ExecutionContext.create(SecurityContext.Privilege.Anonymous)

  private def _view(
    componentid: ComponentId = ComponentId("org.goldenport.cncf.admin.Surface"),
    release: String = "1.0.0",
    instance: String = "blue",
    subsystem: String = "component-subsystem"
  ): ComponentAdminViewModel = {
    val componentclass = ComponentAdminComponentClass(componentid)
    val logicalidentity = ComponentResourceLogicalIdentity(componentid, release, None, "documentation", "urn:cncf:resource:phase607:documentation")
    val resourceprovenance = ComponentAdminSafeProvenance(ComponentAdminSourceKind.ResolvedResource, Some(logicalidentity))
    val runtimeprovenance = ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None)
    val subsystemclass = ComponentAdminSubsystemClass(subsystem)
    val selectedrelease = ComponentAdminViewField(ComponentAdminLogicalRelease(componentclass, release), resourceprovenance)
    val selectedinstance = ComponentAdminViewField(ComponentAdminLoadedInstance(componentclass, ComponentInstanceId(componentid, instance)), runtimeprovenance)
    ComponentAdminViewModel.createC(
      ComponentAdminViewField(componentclass, runtimeprovenance),
      selectedrelease,
      Vector(selectedrelease),
      selectedinstance,
      Vector(selectedinstance),
      ComponentAdminViewField(subsystemclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.Descriptor, None)),
      ComponentAdminViewField(ComponentAdminSubsystemInstance(subsystemclass, "main"), runtimeprovenance),
      ComponentAdminViewField(ComponentAdminImplicitComponentSubsystem(componentclass, "implicit"), resourceprovenance),
      ComponentAdminViewField(ComponentAdminResourceState.Available, resourceprovenance)
    ).toOption.get
  }

  private def _documentation(
    view: ComponentAdminViewModel,
    logicalpath: String = "manual/user-guide.md",
    helppath: String = "/help/org-goldenport-cncf-admin-surface/manual/user-guide",
    availability: ComponentResourceAvailability = ComponentResourceAvailability.Available,
    integrity: ComponentResourceIntegrity = ComponentResourceIntegrity.Verified,
    authorization: ComponentResourceAuthorization = ComponentResourceAuthorization.Granted,
    sourcekind: ComponentResourceSourceKind = ComponentResourceSourceKind.ExpandedCar
  ): ComponentAdminDocumentationNavigationView = {
    val componentid = view.componentClass.value.componentId
    val release = view.selectedLogicalRelease.value.release
    val identity = ComponentResourceLogicalIdentity(componentid, release, None, "documentation", "urn:cncf:resource:phase607:user-guide")
    val resource = ComponentKnowledgeManifestConsumerResourceEvidence(
      identity,
      logicalpath,
      ComponentKnowledgeResourceKind.Documentation,
      ComponentKnowledgeResourceRole.Documentation,
      Some("en"),
      ComponentKnowledgeMediaType.TextMarkdown,
      42,
      _digest,
      ComponentKnowledgeManifestConsumerMetadataEvidence(
        ComponentKnowledgeAuthority.Component,
        ComponentKnowledgeStability.Stable,
        ComponentKnowledgeSource.SuppliedPhase58,
        "Apache-2.0",
        ComponentKnowledgeDisclosure.MetadataOnly
      ),
      availability,
      integrity,
      authorization,
      ComponentKnowledgeManifestConsumerSafeProvenanceEvidence(
        sourcekind,
        "org.goldenport.cncf:phase607-documentation:1.0.0",
        "urn:cncf:resource:phase607:user-guide",
        "expanded-car:2",
        externalDeploymentRequired = false,
        matchingDigest = _digest
      )
    )
    val navigation = ComponentAdminDocumentationResourceNavigation(
      resource,
      ComponentKnowledgeHelpResourceRoute(
        ComponentKnowledgeHelpManifestIdentity(componentid, release),
        logicalpath,
        helppath
      )
    )
    ComponentAdminDocumentationNavigationView(
      view,
      null,
      navigation,
      navigation,
      navigation,
      Vector.empty,
      Vector.empty,
      navigation,
      navigation,
      Vector(navigation)
    )
  }

  private def _catalog(
    view: ComponentAdminViewModel,
    visible: Set[String],
    unavailable: Set[String] = Set.empty,
    denied: Set[String] = Set.empty
  ): ComponentAdminAuthorizedManagementCatalog = {
    val binding = ComponentAdminRuntimeIdentityBinding(
      view.selectedLogicalRelease.value,
      view.subsystemClass.value,
      view.subsystemInstance.value,
      view.implicitComponentSubsystem.value
    )
    val lifecycle = ComponentAdminLifecycleEvidence(
      ComponentAdminLifecycleState.Active,
      "active",
      view.resourceState.provenance
    )
    val registrations = _management_selectors.map { selector =>
      ComponentAdminManagementActionRegistration(
        ComponentAdminManagementActionSelector(selector),
        if (denied.contains(selector)) OperationAuthorizationRule(deny = true) else OperationAuthorizationRule(),
        if (unavailable.contains(selector)) ComponentAdminManagementAvailability.Unavailable else ComponentAdminManagementAvailability.Available,
        visible.contains(selector)
      )
    }
    ComponentAdminAuthorizedManagement.createC(
      ComponentAdminManagementTarget(view.componentClass.value.componentId, view.selectedLoadedInstance.value.instanceId, binding, lifecycle),
      registrations,
      Vector(ComponentAdminOrdinaryQuery(ComponentAdminManagementActionSelector("entity/list"), visible = true))
    ).toOption.get
  }

  private def _descriptor(view: ComponentAdminViewModel): WebDescriptor = {
    val componentpath = _component_path(view)
    WebDescriptor(adminPages = Vector(
      WebDescriptor.AdminPage("summary", "Summary", s"/web/${componentpath}/admin/summary", component = Some(componentpath))
    ))
  }

  private def _component_path(view: ComponentAdminViewModel): String =
    org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(view.componentClass.value.componentId.name)
}
