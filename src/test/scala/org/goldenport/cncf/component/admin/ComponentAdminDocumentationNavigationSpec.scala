package org.goldenport.cncf.component.admin

import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.{ComponentResourceAuthorization, ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceLogicalIdentity, ComponentResourceProvenance, ComponentResourceSourceKind, ResolvedComponentResource}
import org.goldenport.cncf.knowledge.{ComponentDevelopmentContext, ComponentDevelopmentContextCandidate, ComponentDevelopmentContextOutcome, ComponentDevelopmentResourceAssignment, ComponentDevelopmentResourceCategory, ComponentKnowledgeAuthority, ComponentKnowledgeDisclosure, ComponentKnowledgeHelpContract, ComponentKnowledgeManifest, ComponentKnowledgeMetadata, ComponentKnowledgeResourceBinding, ComponentKnowledgeResourceEntry, ComponentKnowledgeResourceKind, ComponentKnowledgeResourceRole, ComponentKnowledgeSafeProvenance, ComponentKnowledgeSource, ComponentKnowledgeStability, ComponentKnowledgeMediaType, ResolvedComponentKnowledge, ResolvedComponentKnowledgeEntry}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN: the
 * package-private, value-only Component Admin documentation navigation.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentAdminDocumentationNavigationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private type ResourceState = (ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceAuthorization, ComponentResourceSourceKind)

  private final case class ResourceDefinition(
    key: String,
    logicalpath: String,
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    mediatype: ComponentKnowledgeMediaType,
    language: Option[String]
  )

  private val _e1 = afterWord("in spec:component-admin-documentation-navigation, example:E1, rules:ADM06-R1, phase:60.5, slice:ADM-06A")
  private val _e2 = afterWord("in spec:component-admin-documentation-navigation, example:E2, rules:ADM06-R2, phase:60.5, slice:ADM-06A")
  private val _e3 = afterWord("in spec:component-admin-documentation-navigation, example:E3, rules:ADM06-R3, phase:60.5, slice:ADM-06A")
  private val _e4 = afterWord("in spec:component-admin-documentation-navigation, example:E4, rules:ADM06-R4, phase:60.5, slice:ADM-06A")
  private val _e5 = afterWord("in spec:component-admin-documentation-navigation, example:E5, rules:ADM06-R5, phase:60.5, slice:ADM-06A")

  "ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN Component Admin projection" should {
    "E1 link explicit documentation selections to the exact existing Help navigation" must _e1 {
      "when all links come from one supplied Phase 59 manifest and Phase 58 resource projection" in {
        Given("Spec: ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN; Rules: ADM06-R1; Example: E1; one validated Admin identity, resolved knowledge, existing Help navigation, and explicit resource selections")
        val knowledge = _knowledge()
        val help = _help(knowledge)
        val facts = ComponentAdminDocumentationNavigationFacts(knowledge, help, _selection(knowledge))

        When("the pure Admin projection binds the selected resources to the existing Help routes")
        val projected = ComponentAdminDocumentationNavigationProjection.projectC(_identity_view, facts).toOption

        Then("the User Guide, Reference Manual, Help discovery, Scaladoc, model diagrams, examples, source availability, and troubleshooting links retain their exact identities and routes")
        projected should not be empty
        projected.map(_.identityview) shouldBe Some(_identity_view)
        projected.map(_.helpnavigation) shouldBe Some(help.humanNavigation)
        projected.map(_.helpnavigation.manifestIdentity) shouldBe Some(help.humanNavigation.manifestIdentity)
        projected.map(_.helpnavigation.discovery) shouldBe Some(help.humanNavigation.discovery)
        projected.map(_.userguide.resource.logicalIdentity.logicalResource) shouldBe Some("urn:cncf:resource:phase605:user-guide")
        projected.map(_.referencemanual.resource.logicalIdentity.logicalResource) shouldBe Some("urn:cncf:resource:phase605:reference-manual")
        projected.map(_.scaladoc.resource.logicalIdentity.logicalResource) shouldBe Some("urn:cncf:resource:phase605:scaladoc")
        projected.map(_.modeldiagrams.map(_.resource.logicalIdentity.logicalResource)) shouldBe Some(Vector(
          "urn:cncf:resource:phase605:class-diagram",
          "urn:cncf:resource:phase605:state-diagram"
        ))
        projected.map(_.examples.map(_.resource.logicalIdentity.logicalResource)) shouldBe Some(Vector("urn:cncf:resource:phase605:example"))
        projected.map(_.sourceavailability.resource.logicalIdentity.logicalResource) shouldBe Some("urn:cncf:resource:phase605:source")
        projected.map(_.troubleshooting.resource.logicalIdentity.logicalResource) shouldBe Some("urn:cncf:resource:phase605:troubleshooting")
        val selectedpaths = _selection(knowledge).identities.toSet
        projected.map(_.helpnavigation.resources.map(_.logicalPath)) shouldBe Some(help.humanNavigation.resources.map(_.logicalPath))
        projected.map(_.helpnavigation.resources.map(_.logicalPath).contains("configuration/reference.md")) shouldBe Some(true)
        projected.map(_.navigations.map(_.resource.logicalPath)) shouldBe Some(
          help.humanNavigation.resources
            .filter(resource => knowledge.entries.exists(pair => selectedpaths.contains(pair.resource.logicalIdentity) && pair.entry.logicalPath == resource.logicalPath))
            .map(_.logicalPath)
        )
      }
    }

    "E2 retain local, cached, remote, restricted, unavailable, and incompatible evidence without fallback" must _e2 {
      "when the Phase 58 facts carry independently supplied source and state values" in {
        Given("Spec: ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN; Rules: ADM06-R2; Example: E2; exact documentation resources whose states include local, managed-cache, remote, restricted, unavailable, and incompatible evidence")
        val states = Map(
          "user-guide" -> (ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted, ComponentResourceSourceKind.LocalRepository),
          "reference-manual" -> (ComponentResourceAvailability.Restricted, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied, ComponentResourceSourceKind.ManagedCache),
          "scaladoc" -> (ComponentResourceAvailability.Unavailable, ComponentResourceIntegrity.NotEvaluated, ComponentResourceAuthorization.NotEvaluated, ComponentResourceSourceKind.RemoteRepository),
          "class-diagram" -> (ComponentResourceAvailability.Incompatible, ComponentResourceIntegrity.Unverified, ComponentResourceAuthorization.Granted, ComponentResourceSourceKind.ExpandedCar)
        )
        val knowledge = _knowledge(states)
        val facts = ComponentAdminDocumentationNavigationFacts(knowledge, _help(knowledge), _selection(knowledge))

        When("the projection retains the caller-supplied evidence")
        val projected = ComponentAdminDocumentationNavigationProjection.projectC(_identity_view, facts).toOption.get

        Then("availability, integrity, authorization, and source kind remain separate exact values without selecting a replacement")
        Vector(
          projected.userguide,
          projected.referencemanual,
          projected.scaladoc,
          projected.modeldiagrams.head
        ).map(value => (value.resource.availability, value.resource.integrity, value.resource.authorization, value.resource.provenance.sourceKind)) shouldBe Vector(
          states("user-guide"),
          states("reference-manual"),
          states("scaladoc"),
          states("class-diagram")
        )
        projected.referencemanual.resource.availability shouldBe ComponentResourceAvailability.Restricted
        projected.scaladoc.resource.availability shouldBe ComponentResourceAvailability.Unavailable
        projected.modeldiagrams.head.resource.availability shouldBe ComponentResourceAvailability.Incompatible
      }
    }

    "E3 reject cross-identity Help, unadmitted selection, and category conflicts" must _e3 {
      "when independently supplied values do not refer to the same authoritative resource set" in {
        Given("Spec: ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN; Rules: ADM06-R3; Example: E3; a valid Admin identity plus a different-release Help contract, an unknown logical identity, and a SourceCode selection in a Documentation-only position")
        val knowledge = _knowledge()
        val otherknowledge = _knowledge(release = "2.0.0")
        val unknown = ComponentResourceLogicalIdentity(_component_id, _release, None, "unknown", "urn:cncf:resource:phase605:unknown")
        val selection = _selection(knowledge)

        When("the projection validates each independent mismatch")
        val differentrelease = ComponentAdminDocumentationNavigationProjection.projectC(
          _identity_view,
          ComponentAdminDocumentationNavigationFacts(knowledge, _help(otherknowledge), selection)
        )
        val unadmitted = ComponentAdminDocumentationNavigationProjection.projectC(
          _identity_view,
          ComponentAdminDocumentationNavigationFacts(knowledge, _help(knowledge), selection.copy(userguide = unknown))
        )
        val categoryconflict = ComponentAdminDocumentationNavigationProjection.projectC(
          _identity_view,
          ComponentAdminDocumentationNavigationFacts(knowledge, _help(knowledge), selection.copy(userguide = selection.sourceavailability))
        )

        Then("every cross-contract, unknown, or semantically incompatible selection fails closed")
        Vector(differentrelease, unadmitted, categoryconflict).map(_.isSuccess) shouldBe Vector(false, false, false)
        differentrelease.display should include("Help")
        unadmitted.display should include("admitted")
        categoryconflict.display should include("documentation")
      }
    }

    "E4 expose navigation metadata only and no resource bytes, physical locations, or operational authority" must _e4 {
      "when a restricted source-availability link is present in the supplied knowledge" in {
        Given("Spec: ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN; Rules: ADM06-R4; Example: E4; a source availability resource that remains restricted and denied")
        val restricted = Map(
          "source" -> (ComponentResourceAvailability.Restricted, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied, ComponentResourceSourceKind.ManagedCache)
        )
        val knowledge = _knowledge(restricted)
        val facts = ComponentAdminDocumentationNavigationFacts(knowledge, _help(knowledge), _selection(knowledge))

        When("the Admin projection links the existing Help route without making an access request")
        val projected = ComponentAdminDocumentationNavigationProjection.projectC(_identity_view, facts).toOption.get
        val names = projected.sourceavailability.resource.productElementNames.toVector ++ projected.sourceavailability.resource.provenance.productElementNames.toVector

        Then("the denied state remains visible while the navigation value exposes neither raw content nor resolver or management authority")
        projected.sourceavailability.resource.availability shouldBe ComponentResourceAvailability.Restricted
        projected.sourceavailability.resource.authorization shouldBe ComponentResourceAuthorization.Denied
        names should not contain "content"
        names should not contain "physicalSource"
        names should not contain "normalizedRelativePath"
        names should not contain "activationAuthority"
        names should not contain "operationAuthority"
        names should not contain "mcpAuthority"
        names should not contain "deploymentAuthority"
      }
    }

    "E5 preserve exact supplied state and source kind across fifty finite variations" must _e5 {
      "when the pure projection evaluates generated Phase 58 resource evidence" in {
        Given("Spec: ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN; Rules: ADM06-R5; Example: E5; a finite generator selecting an availability, integrity, authorization, and source kind for the User Guide")
        val states = for {
          availability <- ComponentResourceAvailability.values.toVector
          integrity <- ComponentResourceIntegrity.values.toVector
          authorization <- ComponentResourceAuthorization.values.toVector
          sourcekind <- ComponentResourceSourceKind.values.toVector
        } yield (availability, integrity, authorization, sourcekind)
        val property = Prop.forAll(Gen.oneOf(states)) { state =>
          val knowledge = _knowledge(Map("user-guide" -> state))
          ComponentAdminDocumentationNavigationProjection.projectC(
            _identity_view,
            ComponentAdminDocumentationNavigationFacts(knowledge, _help(knowledge), _selection(knowledge))
          ).toOption.exists { projected =>
            projected.identityview == _identity_view &&
              (projected.userguide.resource.availability, projected.userguide.resource.integrity, projected.userguide.resource.authorization, projected.userguide.resource.provenance.sourceKind) == state &&
              projected.userguide.route == projected.helpnavigation.resources.find(_.logicalPath == "manual/user-guide.md").map(_.route).get
          }
        }

        When("ScalaCheck evaluates the closed generated evidence space")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every successful case retains exact values and the existing Help route")
        checked.passed shouldBe true
      }
    }
  }

  private val _component_id = ComponentId("org.goldenport.cncf.admin.Documentation")
  private val _release = "1.0.0"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _default_state: ResourceState = (
    ComponentResourceAvailability.Available,
    ComponentResourceIntegrity.Verified,
    ComponentResourceAuthorization.Granted,
    ComponentResourceSourceKind.ExpandedCar
  )
  private val _definitions = Vector(
    ResourceDefinition("user-guide", "manual/user-guide.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en")),
    ResourceDefinition("configuration", "configuration/reference.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en")),
    ResourceDefinition("reference-manual", "reference/manual.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en")),
    ResourceDefinition("scaladoc", "scaladoc/Component.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en")),
    ResourceDefinition("class-diagram", "diagrams/class.svg", ComponentKnowledgeResourceKind.ClassDiagram, ComponentKnowledgeResourceRole.Diagram, ComponentKnowledgeMediaType.ImageSvgXml, None),
    ResourceDefinition("state-diagram", "diagrams/state.svg", ComponentKnowledgeResourceKind.StateDiagram, ComponentKnowledgeResourceRole.Diagram, ComponentKnowledgeMediaType.ImageSvgXml, None),
    ResourceDefinition("example", "examples/quickstart.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en")),
    ResourceDefinition("source", "src/Component.scala", ComponentKnowledgeResourceKind.SourceCode, ComponentKnowledgeResourceRole.SourceCode, ComponentKnowledgeMediaType.TextXScala, Some("scala")),
    ResourceDefinition("troubleshooting", "troubleshooting/known-issues.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"))
  )

  private def _identity_view: ComponentAdminViewModel = {
    val componentclass = ComponentAdminComponentClass(_component_id)
    val identity = ComponentResourceLogicalIdentity(_component_id, _release, None, "documentation", "urn:cncf:resource:phase605:admin-documentation")
    val provenance = ComponentAdminSafeProvenance(ComponentAdminSourceKind.KnowledgeManifest, Some(identity))
    val componentfield = ComponentAdminViewField(componentclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val release = ComponentAdminViewField(ComponentAdminLogicalRelease(componentclass, _release), provenance)
    val instance = ComponentAdminViewField(ComponentAdminLoadedInstance(componentclass, ComponentInstanceId(_component_id, "default")), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val subsystemclass = ComponentAdminSubsystemClass("component-subsystem")
    _take(ComponentAdminViewModel.createC(
      componentfield,
      release,
      Vector(release),
      instance,
      Vector(instance),
      ComponentAdminViewField(subsystemclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.Descriptor, None)),
      ComponentAdminViewField(ComponentAdminSubsystemInstance(subsystemclass, "main"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None)),
      ComponentAdminViewField(ComponentAdminImplicitComponentSubsystem(componentclass, "implicit"), provenance),
      ComponentAdminViewField(ComponentAdminResourceState.Available, provenance)
    ))
  }

  private def _knowledge(
    states: Map[String, ResourceState] = Map.empty,
    release: String = _release
  ): ResolvedComponentKnowledge = {
    val pairs = _definitions.map { definition =>
      val state = states.getOrElse(definition.key, _default_state)
      _pair(definition, release, state)
    }
    ResolvedComponentKnowledge(ComponentKnowledgeManifest(_component_id, release, pairs.map(_.entry)), pairs.reverse)
  }

  private def _pair(
    definition: ResourceDefinition,
    release: String,
    state: ResourceState
  ): ResolvedComponentKnowledgeEntry = {
    val (availability, integrity, authorization, sourcekind) = state
    val logicalresource = s"urn:cncf:resource:phase605:${definition.key}"
    val identity = ComponentResourceLogicalIdentity(_component_id, release, None, definition.key, logicalresource)
    val coordinate = "org.goldenport.cncf:phase605-documentation:1.0.0"
    val step = _resolution_step(sourcekind)
    val resource = ResolvedComponentResource(
      logicalIdentity = identity,
      provenance = ComponentResourceProvenance(
        sourceKind = sourcekind,
        repository = "https://repo.example.invalid/phase605",
        artifactCoordinate = coordinate,
        sha256 = _digest,
        normalizedRelativePath = s"private/phase58/${definition.logicalpath}",
        logicalSource = logicalresource,
        physicalSource = s"phase605:${definition.key}",
        resolutionStep = step,
        childRole = definition.key,
        logicalResource = logicalresource,
        access = "restricted-to-resolver",
        license = "Apache-2.0",
        externalDeploymentRequired = false
      ),
      availability = availability,
      integrity = integrity,
      authorization = authorization,
      activationAuthority = false,
      operationAuthority = false,
      mcpAuthority = false,
      disclosureAuthority = false,
      deploymentAuthority = false
    )
    val entry = ComponentKnowledgeResourceEntry(
      binding = ComponentKnowledgeResourceBinding(identity),
      logicalPath = definition.logicalpath,
      kind = definition.kind,
      role = definition.role,
      language = definition.language,
      mediaType = definition.mediatype,
      size = 42,
      sha256 = _digest,
      metadata = ComponentKnowledgeMetadata(ComponentKnowledgeAuthority.Component, ComponentKnowledgeStability.Stable, ComponentKnowledgeSource.SuppliedPhase58, "Apache-2.0", ComponentKnowledgeDisclosure.MetadataOnly),
      availability = availability,
      integrity = integrity,
      authorization = authorization,
      provenance = ComponentKnowledgeSafeProvenance(sourcekind, coordinate, logicalresource, step, false, _digest)
    )
    ResolvedComponentKnowledgeEntry(entry, resource)
  }

  private def _help(knowledge: ResolvedComponentKnowledge): ComponentKnowledgeHelpContract =
    _take(ComponentKnowledgeHelpContract.createC(knowledge, _development_outcome(knowledge)))

  private def _development_outcome(knowledge: ResolvedComponentKnowledge): ComponentDevelopmentContextOutcome = {
    val identities = knowledge.entries.map(_.resource.logicalIdentity)
    _take(ComponentDevelopmentContext.composeC(
      knowledge,
      ComponentDevelopmentContextCandidate(
        ComponentDevelopmentResourceCategory.values.toVector.zip(identities).map { case (category, identity) =>
          ComponentDevelopmentResourceAssignment(category, identity)
        },
        identities
      )
    ))
  }

  private def _selection(knowledge: ResolvedComponentKnowledge): ComponentAdminDocumentationNavigationSelection = {
    def _identity_(key: String): ComponentResourceLogicalIdentity =
      knowledge.entries.find(_.resource.logicalIdentity.childRole == key).get.resource.logicalIdentity
    ComponentAdminDocumentationNavigationSelection(
      userguide = _identity_("user-guide"),
      referencemanual = _identity_("reference-manual"),
      scaladoc = _identity_("scaladoc"),
      modeldiagrams = Vector(_identity_("class-diagram"), _identity_("state-diagram")),
      examples = Vector(_identity_("example")),
      sourceavailability = _identity_("source"),
      troubleshooting = _identity_("troubleshooting")
    )
  }

  private def _resolution_step(sourcekind: ComponentResourceSourceKind): String =
    sourcekind match {
      case ComponentResourceSourceKind.EmbeddedPrimary => "embedded-primary:0"
      case ComponentResourceSourceKind.DevelopmentDirectory => "development-directory:1"
      case ComponentResourceSourceKind.ExpandedCar => "expanded-car:2"
      case ComponentResourceSourceKind.LocalRepository => "local-repository:3"
      case ComponentResourceSourceKind.ManagedCache => "managed-cache:4"
      case ComponentResourceSourceKind.OfflineBundle => "offline-bundle:5"
      case ComponentResourceSourceKind.RemoteRepository => "remote-repository:6"
    }

  private def _take[A](value: org.goldenport.Consequence[A]): A = value.toOption.get
}
