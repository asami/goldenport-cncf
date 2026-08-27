package org.goldenport.cncf.knowledge

import io.circe.Json
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity,
  ComponentResourceSourceKind,
  ResolvedComponentResource
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable acceptance specification for P598-S1 representative
 * documentation/profile evidence. Every fixture is a typed value supplied to
 * an existing contract; this specification does not read resources, source
 * bytes, archives, repositories, or networks, and it grants no authority.
 *
 * @since   Aug. 27, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class RepresentativeDocumentationProfileSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _e1 = afterWord(
    "in spec:representative-documentation-profile, example:E1, rules:P598-DOC08-AC01, phase:59.8, step:P598, slice:P598-S1"
  )
  private val _e2 = afterWord(
    "in spec:representative-documentation-profile, example:E2, rules:P598-DOC08-AC02, phase:59.8, step:P598, slice:P598-S1"
  )
  private val _e3 = afterWord(
    "in spec:representative-documentation-profile, example:E3, rules:P598-DOC08-AC03, phase:59.8, step:P598, slice:P598-S1"
  )
  private val _e4 = afterWord(
    "in spec:representative-documentation-profile, example:E4, rules:P598-DOC08-AC04, phase:59.8, step:P598, slice:P598-S1"
  )

  private val _component_id = ComponentId("org.goldenport.cncf.phase598.Representative")
  private val _release = "0.1.0-SNAPSHOT"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _other_digest = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

  "P598-DOC08-AC01 framework profile identity and availability" should {
    "E1 preserve exact document, section, and publication identity across online, installed, and Hub views" must _e1 {
      "preserve exact document, section, and publication identity across online, installed, and Hub views" in {
        Given("the existing FrameworkDocumentationProfile and ClosedNetworkDocumentationHubSarProfile contracts with caller-supplied publication values")
        val online = _framework_profile(_component_id, _release, None)
        val installed = _framework_profile(
          ComponentId("org.goldenport.cncf.phase598.FrameworkSnapshot"),
          "0.1.0-SNAPSHOT",
          Some(FrameworkPublicationReferenceAvailability.Installed)
        )
        val hub = ClosedNetworkDocumentationHubSarProfile.createC(
          "simplemodeling-documentation-hub",
          "0.1.0-SNAPSHOT",
          _hub_constituents(installed)
        ).toOption

        When("the profiles retain the supplied online publication, installed snapshot, and closed-network Hub constituent values")
        val publications = Vector(
          online.frameworkPublication,
          installed.frameworkPublication,
          hub.get.constituents.head.profile.frameworkPublication
        )

        Then("canonical framework product/version, URL, generation, document, section, digest, and source identity remain exact while availability stays distinct")
        publications.map(_.productVersion) shouldBe Vector.fill(3)(FrameworkProductVersion("simplemodeling", "0.1.0"))
        publications.map(_.canonicalUrl) shouldBe Vector.fill(3)("https://www.simplemodeling.org/framework/0.1.0")
        publications.map(_.publicationGeneration) shouldBe Vector.fill(3)("2026-08-27")
        publications.map(_.documentId) shouldBe Vector.fill(3)("https://www.simplemodeling.org/framework/0.1.0/documentation")
        publications.map(_.sectionId) shouldBe Vector.fill(3)(Some("https://www.simplemodeling.org/framework/0.1.0/documentation#overview"))
        publications.map(_.sha256) shouldBe Vector.fill(3)(_digest)
        publications.map(_.generatedFrom) shouldBe Vector.fill(3)(FrameworkPublicationGeneratedFrom("urn:simplemodeling:source:framework:0.1.0", _digest))
        publications.map(_.availability) shouldBe Vector.fill(3)(FrameworkPublicationReferenceAvailability.Online)
        online.snapshot shouldBe FrameworkDocumentationSnapshotProfile.Absent
        installed.snapshot match {
          case FrameworkDocumentationSnapshotProfile.Present(_, _, _) => succeed
          case FrameworkDocumentationSnapshotProfile.Absent => fail("installed framework profile must retain a snapshot")
        }
        installed.frameworkPublication.documentationComponentSnapshot.map(_.availability) shouldBe Some(FrameworkPublicationReferenceAvailability.Installed)
        hub should not be empty
        hub.get.constituents.map(_.profile.frameworkPublication.documentationComponentSnapshot.map(_.availability)) shouldBe Vector(
          Some(FrameworkPublicationReferenceAvailability.Installed),
          Some(FrameworkPublicationReferenceAvailability.Local),
          Some(FrameworkPublicationReferenceAvailability.Cached),
          Some(FrameworkPublicationReferenceAvailability.Local),
          Some(FrameworkPublicationReferenceAvailability.Installed)
        )
        hub.get.constituents.map(_.profile.frameworkPublication.canonicalUrl).distinct shouldBe Vector("https://www.simplemodeling.org/framework/0.1.0")
        FrameworkPublicationContext.validateC(installed.frameworkPublication.copy(sha256 = _other_digest)).toOption shouldBe None

        And("closed-network composition rejects the online-only absent snapshot without changing the online identity")
        val rejected = ClosedNetworkDocumentationHubSarProfile.createC(
          "simplemodeling-documentation-hub",
          "0.1.0-SNAPSHOT",
          _hub_constituents(installed).updated(
            0,
            DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cncf, online)
          )
        ).toOption
        rejected shouldBe None
      }
    }
  }

  "P598-DOC08-AC02 representative Component manifest and context" should {
    "E2 cover every development classification and model/diagram kind through one Help profile" must _e2 {
      "cover every development classification and model/diagram kind through one Help profile" in {
        Given("a caller-supplied typed Component manifest with representative documentation, model, source, public metadata, and safe provenance entries")
        val knowledge = _knowledge
        val outcome = _development_outcome(knowledge)
        val contract = ComponentKnowledgeHelpContract.createC(knowledge, outcome).toOption

        When("the existing manifest, development-context, and Help contracts project the same admitted values")
        val manifest = knowledge.manifest
        val context = outcome match {
          case ComponentDevelopmentContextOutcome.Ready(value) => value
          case ComponentDevelopmentContextOutcome.Incomplete(failure) => fail(s"representative non-source resources must be ready: ${failure.code}")
        }

        Then("manifest validation succeeds, all eleven categories are attributed, and Help/Direct-AI preserve all required model and diagram kinds")
        ComponentKnowledgeManifest.validateC(manifest).toOption shouldBe Some(manifest)
        context.classifications.map(_.category).toSet shouldBe ComponentDevelopmentResourceCategory.values.toSet
        context.classifications.map(_.logicalIdentity).distinct.size shouldBe context.classifications.size
        context.requiredLogicalIdentities should not contain _identity(knowledge, "source")
        contract should not be empty
        val profile = contract.get
        profile.humanNavigation.resources shouldBe profile.directAiNavigation.resources
        profile.humanNavigation.resources.map(_.kind).toSet should contain allElementsOf Set(
          ComponentKnowledgeResourceKind.Documentation,
          ComponentKnowledgeResourceKind.SourceCode,
          ComponentKnowledgeResourceKind.Entity,
          ComponentKnowledgeResourceKind.Powertype,
          ComponentKnowledgeResourceKind.StateMachine,
          ComponentKnowledgeResourceKind.Value,
          ComponentKnowledgeResourceKind.Datatype,
          ComponentKnowledgeResourceKind.ClassDiagram,
          ComponentKnowledgeResourceKind.StateDiagram,
          ComponentKnowledgeResourceKind.Directive,
          ComponentKnowledgeResourceKind.SkillCatalog
        )
        val model = manifest.modelResources.get
        model.models.map(_.entry.kind).toSet shouldBe Set(
          ComponentKnowledgeResourceKind.Entity,
          ComponentKnowledgeResourceKind.Powertype,
          ComponentKnowledgeResourceKind.StateMachine,
          ComponentKnowledgeResourceKind.Value,
          ComponentKnowledgeResourceKind.Datatype
        )
        model.diagrams.map(_.entry.kind).toSet shouldBe Set(
          ComponentKnowledgeResourceKind.ClassDiagram,
          ComponentKnowledgeResourceKind.StateDiagram
        )
        profile.humanNavigation.manifestIdentity shouldBe ComponentKnowledgeHelpManifestIdentity(_component_id, _release)
      }
    }
  }

  "P598-DOC08-AC03 restricted SourceCode boundary" should {
    "E3 preserve denial facts without exposing raw or physical provenance" must _e3 {
      "preserve denial facts without exposing raw or physical provenance" in {
        Given("the same typed representative knowledge with a SourceCode entry whose availability is available but authorization is denied")
        val knowledge = _knowledge
        val outcome = _development_outcome(knowledge)
        val contract = ComponentKnowledgeHelpContract.createC(knowledge, outcome).toOption.get
        val human = contract.humanNavigation.resources.find(_.logicalPath == "src/Representative.scala").get
        val directai = contract.directAiNavigation.resources.find(_.logicalPath == "src/Representative.scala").get
        val consumer = contract.directAiNavigation.manifestConsumerContract.resources.find(_.logicalPath == "src/Representative.scala").get
        val source = knowledge.entries.find(_.entry.logicalPath == "src/Representative.scala").get.resource

        When("Help and Direct-AI project the already admitted SourceCode state without an access request or source read")
        val exposedfields = human.productElementNames.toVector ++ directai.productElementNames.toVector ++ consumer.productElementNames.toVector ++ consumer.provenance.productElementNames.toVector

        Then("availability, integrity, and denied authorization remain truthful while Help/Direct-AI expose no content, repository, physical source, or authority fields")
        Vector((human.availability, human.integrity, human.authorization), (directai.availability, directai.integrity, directai.authorization)) shouldBe Vector(
          (ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied),
          (ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied)
        )
        consumer.provenance.logicalSource shouldBe "urn:cncf:resource:phase598:representative:source"
        exposedfields should not contain "content"
        exposedfields should not contain "repository"
        exposedfields should not contain "physicalSource"
        exposedfields should not contain "physicalPath"
        exposedfields should not contain "activationAuthority"
        exposedfields should not contain "mcpAuthority"
        source.activationAuthority shouldBe false
        source.operationAuthority shouldBe false
        source.mcpAuthority shouldBe false
        source.disclosureAuthority shouldBe false
        source.deploymentAuthority shouldBe false
        contract.directAiNavigation.developmentContext shouldBe ComponentKnowledgeHelpDevelopmentContextEvidence.Ready
      }
    }
  }

  "P598-DOC08-AC04 descriptive Guide and Skill Catalog boundary" should {
    "E4 preserve public identity and digest while rejecting authority-bearing metadata" must _e4 {
      "preserve public identity and digest while rejecting authority-bearing metadata" in {
        Given("a representative manifest carrying existing typed public Guide and descriptive Skill Catalog projections")
        val knowledge = _knowledge
        val manifest = knowledge.manifest
        val consumer = ComponentKnowledgeManifestConsumerContract.fromManifestC(manifest).toOption.get
        val guide = consumer.publicDirective.get
        val catalog = consumer.skillCatalog.get

        When("the consumer projection exposes only public metadata and strict extension validation evaluates authority-bearing attempts")
        val rejectedguide = PublicDirectiveProjection.validateC(
          manifest.publicDirective.get.copy(extensions = Map("ruleText" -> Json.fromString("override"))),
          manifest.resources
        ).toOption
        val rejectedcatalog = PublicSkillCatalog.validateC(
          manifest.skillCatalog.get.copy(extensions = Map("mcpAuthority" -> Json.fromBoolean(true))),
          manifest.resources
        ).toOption

        Then("the mounted directive stays authoritative, identity/digest/visibility remain descriptive, and no SkillBundleManifest/install/activation/runtime/MCP authority is introduced")
        guide.logicalIdentity shouldBe manifest.publicDirective.get.entry.binding.logicalIdentity
        guide.directiveId shouldBe "phase598-public-ai-guide"
        guide.sourceSha256 shouldBe _digest
        guide.visibility shouldBe PublicMetadataVisibility.Public
        guide.authority shouldBe PublicDirectiveAuthority.MountedDirectiveRemainsAuthoritative
        catalog.logicalIdentity shouldBe manifest.skillCatalog.get.entry.binding.logicalIdentity
        catalog.catalogId shouldBe "phase598-descriptive-skill-catalog"
        catalog.sourceSha256 shouldBe _digest
        catalog.visibility shouldBe PublicMetadataVisibility.Ecosystem
        catalog.permissions shouldBe Vector("metadata visibility")
        catalog.sideEffects shouldBe Vector("none")
        catalog.mcpRequirements shouldBe Vector("descriptive only")
        guide.productElementNames.toVector should not contain "ruleText"
        catalog.productElementNames.toVector should not contain "skillBundleManifest"
        catalog.productElementNames.toVector should not contain "activationAuthority"
        catalog.productElementNames.toVector should not contain "mcpAuthority"
        PublicDirectiveAuthority.values.toVector shouldBe Vector(PublicDirectiveAuthority.MountedDirectiveRemainsAuthoritative)
        rejectedguide shouldBe None
        rejectedcatalog shouldBe None
        knowledge.entries.map(_.resource).flatMap(resource => Vector(
          resource.activationAuthority,
          resource.operationAuthority,
          resource.mcpAuthority,
          resource.disclosureAuthority,
          resource.deploymentAuthority
        )).forall(value => !value) shouldBe true
      }
    }
  }

  private final case class ResourceDefinition(
    key: String,
    logicalpath: String,
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    mediatype: ComponentKnowledgeMediaType,
    language: Option[String],
    state: (ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceAuthorization)
  )

  private val _ready_state = (
    ComponentResourceAvailability.Available,
    ComponentResourceIntegrity.Verified,
    ComponentResourceAuthorization.Granted
  )
  private val _restricted_source_state = (
    ComponentResourceAvailability.Available,
    ComponentResourceIntegrity.Verified,
    ComponentResourceAuthorization.Denied
  )

  private val _definitions = Vector(
    ResourceDefinition("manual", "manual/user-guide.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"), _ready_state),
    ResourceDefinition("entity", "model/entity.json", ComponentKnowledgeResourceKind.Entity, ComponentKnowledgeResourceRole.Model, ComponentKnowledgeMediaType.ApplicationJson, None, _ready_state),
    ResourceDefinition("api", "api/reference.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"), _ready_state),
    ResourceDefinition("configuration", "configuration/reference.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"), _ready_state),
    ResourceDefinition("example", "examples/quickstart.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"), _ready_state),
    ResourceDefinition("source", "src/Representative.scala", ComponentKnowledgeResourceKind.SourceCode, ComponentKnowledgeResourceRole.SourceCode, ComponentKnowledgeMediaType.TextXScala, Some("scala"), _restricted_source_state),
    ResourceDefinition("generated-source", "generated/Representative.scala", ComponentKnowledgeResourceKind.SourceCode, ComponentKnowledgeResourceRole.SourceCode, ComponentKnowledgeMediaType.TextXScala, Some("scala"), _ready_state),
    ResourceDefinition("scaladoc", "scaladoc/Representative.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"), _ready_state),
    ResourceDefinition("test", "test/RepresentativeSpec.scala", ComponentKnowledgeResourceKind.SourceCode, ComponentKnowledgeResourceRole.SourceCode, ComponentKnowledgeMediaType.TextXScala, Some("scala"), _ready_state),
    ResourceDefinition("provenance", "provenance/build.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"), _ready_state),
    ResourceDefinition("dependency-documentation", "dependencies/library.md", ComponentKnowledgeResourceKind.Documentation, ComponentKnowledgeResourceRole.Documentation, ComponentKnowledgeMediaType.TextMarkdown, Some("en"), _ready_state),
    ResourceDefinition("powertype", "model/powertype.json", ComponentKnowledgeResourceKind.Powertype, ComponentKnowledgeResourceRole.Model, ComponentKnowledgeMediaType.ApplicationJson, None, _ready_state),
    ResourceDefinition("state-machine", "model/state-machine.json", ComponentKnowledgeResourceKind.StateMachine, ComponentKnowledgeResourceRole.Model, ComponentKnowledgeMediaType.ApplicationJson, None, _ready_state),
    ResourceDefinition("value", "model/value.json", ComponentKnowledgeResourceKind.Value, ComponentKnowledgeResourceRole.Model, ComponentKnowledgeMediaType.ApplicationJson, None, _ready_state),
    ResourceDefinition("datatype", "model/datatype.json", ComponentKnowledgeResourceKind.Datatype, ComponentKnowledgeResourceRole.Model, ComponentKnowledgeMediaType.ApplicationJson, None, _ready_state),
    ResourceDefinition("class-diagram", "diagrams/class.svg", ComponentKnowledgeResourceKind.ClassDiagram, ComponentKnowledgeResourceRole.Diagram, ComponentKnowledgeMediaType.ImageSvgXml, None, _ready_state),
    ResourceDefinition("state-diagram", "diagrams/state.svg", ComponentKnowledgeResourceKind.StateDiagram, ComponentKnowledgeResourceRole.Diagram, ComponentKnowledgeMediaType.ImageSvgXml, None, _ready_state),
    ResourceDefinition("public-guide", "guide/public-ai.yaml", ComponentKnowledgeResourceKind.Directive, ComponentKnowledgeResourceRole.Directive, ComponentKnowledgeMediaType.ApplicationYaml, None, _ready_state),
    ResourceDefinition("skill-catalog", "skills/public-catalog.json", ComponentKnowledgeResourceKind.SkillCatalog, ComponentKnowledgeResourceRole.SkillCatalog, ComponentKnowledgeMediaType.ApplicationJson, None, _ready_state)
  )

  private def _knowledge: ResolvedComponentKnowledge = {
    val pairs = _definitions.map(_pair)
    val entries = pairs.map(_.entry)
    val models = entries.filter(entry => Set(
      ComponentKnowledgeResourceKind.Entity,
      ComponentKnowledgeResourceKind.Powertype,
      ComponentKnowledgeResourceKind.StateMachine,
      ComponentKnowledgeResourceKind.Value,
      ComponentKnowledgeResourceKind.Datatype
    ).contains(entry.kind)).map(PortableModelResource(_))
    val entity = entries.find(_.kind == ComponentKnowledgeResourceKind.Entity).get
    val statemachine = entries.find(_.kind == ComponentKnowledgeResourceKind.StateMachine).get
    val diagrams = Vector(
      PortableDiagramResource(
        entries.find(_.kind == ComponentKnowledgeResourceKind.ClassDiagram).get,
        Vector(PortableDiagramGeneratedFrom(entity.binding.logicalIdentity, entity.sha256))
      ),
      PortableDiagramResource(
        entries.find(_.kind == ComponentKnowledgeResourceKind.StateDiagram).get,
        Vector(PortableDiagramGeneratedFrom(statemachine.binding.logicalIdentity, statemachine.sha256))
      )
    )
    val manifest = ComponentKnowledgeManifest(
      componentId = _component_id,
      logicalRelease = _release,
      resources = entries,
      modelResources = Some(PortableModelResourceContext(models, diagrams)),
      publicDirective = Some(_public_guide(entries.find(_.kind == ComponentKnowledgeResourceKind.Directive).get)),
      skillCatalog = Some(_skill_catalog(entries.find(_.kind == ComponentKnowledgeResourceKind.SkillCatalog).get))
    )
    ResolvedComponentKnowledge(manifest, pairs)
  }

  private def _pair(definition: ResourceDefinition): ResolvedComponentKnowledgeEntry = {
    val logicalresource = s"urn:cncf:resource:phase598:representative:${definition.key}"
    val identity = ComponentResourceLogicalIdentity(_component_id, _release, None, definition.key, logicalresource)
    val (availability, integrity, authorization) = definition.state
    val resource = ResolvedComponentResource(
      logicalIdentity = identity,
      provenance = org.goldenport.cncf.component.repository.ComponentResourceProvenance(
        sourceKind = ComponentResourceSourceKind.ExpandedCar,
        repository = "https://repo.example.invalid/phase598",
        artifactCoordinate = "org.goldenport.cncf:phase598-representative:0.1.0",
        sha256 = _digest,
        normalizedRelativePath = s"private/phase598/${definition.logicalpath}",
        logicalSource = logicalresource,
        physicalSource = s"expanded-car:private-${definition.key}",
        resolutionStep = "expanded-car:2",
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
      metadata = ComponentKnowledgeMetadata(
        ComponentKnowledgeAuthority.Component,
        ComponentKnowledgeStability.Stable,
        ComponentKnowledgeSource.SuppliedPhase58,
        "Apache-2.0",
        ComponentKnowledgeDisclosure.MetadataOnly
      ),
      availability = availability,
      integrity = integrity,
      authorization = authorization,
      provenance = ComponentKnowledgeSafeProvenance(
        ComponentResourceSourceKind.ExpandedCar,
        "org.goldenport.cncf:phase598-representative:0.1.0",
        logicalresource,
        "expanded-car:2",
        false,
        _digest
      )
    )
    ResolvedComponentKnowledgeEntry(entry, resource)
  }

  private def _identity(knowledge: ResolvedComponentKnowledge, key: String): ComponentResourceLogicalIdentity =
    knowledge.entries.find(_.entry.binding.logicalIdentity.childRole == key).get.resource.logicalIdentity

  private def _development_outcome(knowledge: ResolvedComponentKnowledge): ComponentDevelopmentContextOutcome =
    ComponentDevelopmentContext.composeC(
      knowledge,
      ComponentDevelopmentContextCandidate(
        assignments = Vector(
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Manual, _identity(knowledge, "manual")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Model, _identity(knowledge, "entity")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.API, _identity(knowledge, "api")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Configuration, _identity(knowledge, "configuration")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Example, _identity(knowledge, "example")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Source, _identity(knowledge, "source")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.GeneratedSource, _identity(knowledge, "generated-source")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Scaladoc, _identity(knowledge, "scaladoc")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Test, _identity(knowledge, "test")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.Provenance, _identity(knowledge, "provenance")),
          ComponentDevelopmentResourceAssignment(ComponentDevelopmentResourceCategory.DependencyDocumentation, _identity(knowledge, "dependency-documentation"))
        ),
        requiredLogicalIdentities = Vector(
          "manual", "entity", "api", "configuration", "example", "generated-source", "scaladoc", "test", "provenance", "dependency-documentation"
        ).map(_identity(knowledge, _))
      )
    ).toOption.get

  private def _public_guide(entry: ComponentKnowledgeResourceEntry): PublicDirectiveProjection =
    PublicDirectiveProjection(
      entry = entry,
      directiveId = "phase598-public-ai-guide",
      profileId = "phase598-public-profile",
      ruleId = "phase598-mounted-rule-identity",
      origin = "urn:cncf:directive:phase598:public",
      version = "1.0.0",
      authority = PublicDirectiveAuthority.MountedDirectiveRemainsAuthoritative,
      visibility = PublicMetadataVisibility.Public,
      sourceSha256 = _digest,
      redaction = PublicDirectiveRedaction.SourceAndRuleContentWithheld,
      guideReference = "https://www.simplemodeling.org/framework/guide"
    )

  private def _skill_catalog(entry: ComponentKnowledgeResourceEntry): PublicSkillCatalog =
    PublicSkillCatalog(
      entry = entry,
      catalogId = "phase598-descriptive-skill-catalog",
      owner = "cncf",
      purpose = "descriptive public Skill metadata",
      trigger = "explicit user request",
      requirements = Vector("component knowledge manifest"),
      permissions = Vector("metadata visibility"),
      sideEffects = Vector("none"),
      mcpRequirements = Vector("descriptive only"),
      installationReference = "https://www.simplemodeling.org/framework/skills",
      visibility = PublicMetadataVisibility.Ecosystem,
      version = "1.0.0",
      sourceSha256 = _digest
    )

  private def _framework_profile(
    componentid: ComponentId,
    release: String,
    snapshotavailability: Option[FrameworkPublicationReferenceAvailability]
  ): FrameworkDocumentationProfile = {
    val snapshot = snapshotavailability.map(value => FrameworkDocumentationComponentSnapshot(componentid, release, _digest, value))
    val publication = FrameworkPublicationContext(
      productVersion = FrameworkProductVersion("simplemodeling", "0.1.0"),
      canonicalUrl = "https://www.simplemodeling.org/framework/0.1.0",
      publicationGeneration = "2026-08-27",
      documentId = "https://www.simplemodeling.org/framework/0.1.0/documentation",
      sectionId = Some("https://www.simplemodeling.org/framework/0.1.0/documentation#overview"),
      sha256 = _digest,
      availability = FrameworkPublicationReferenceAvailability.Online,
      generatedFrom = FrameworkPublicationGeneratedFrom("urn:simplemodeling:source:framework:0.1.0", _digest),
      documentationComponentSnapshot = snapshot
    )
    snapshot match {
      case None => FrameworkDocumentationProfile.createC(publication, None).toOption.get
      case Some(_) =>
        val documentation = ComponentKnowledgeResourceEntry(
          binding = ComponentKnowledgeResourceBinding(ComponentResourceLogicalIdentity(componentid, release, None, "FrameworkDocumentation", s"urn:cncf:resource:phase598:framework:${componentid.name}")),
          logicalPath = "framework/documentation.md",
          kind = ComponentKnowledgeResourceKind.FrameworkDocumentation,
          role = ComponentKnowledgeResourceRole.FrameworkDocumentation,
          language = Some("en"),
          mediaType = ComponentKnowledgeMediaType.TextMarkdown,
          size = 42,
          sha256 = _digest,
          metadata = ComponentKnowledgeMetadata(ComponentKnowledgeAuthority.Framework, ComponentKnowledgeStability.Stable, ComponentKnowledgeSource.SuppliedPhase58, "Apache-2.0", ComponentKnowledgeDisclosure.MetadataOnly),
          availability = ComponentResourceAvailability.Available,
          integrity = ComponentResourceIntegrity.Verified,
          authorization = ComponentResourceAuthorization.Granted,
          provenance = ComponentKnowledgeSafeProvenance(ComponentResourceSourceKind.ExpandedCar, "org.goldenport.cncf:phase598-framework:0.1.0", s"urn:cncf:source:phase598:framework:${componentid.name}", "expanded-car:2", false, _digest)
        )
        FrameworkDocumentationProfile.createC(
          publication,
          Some(ComponentKnowledgeManifest(componentid, release, Vector(documentation), frameworkPublication = Some(publication)))
        ).toOption.get
    }
  }

  private def _hub_constituents(first: FrameworkDocumentationProfile): Vector[DocumentationHubSarConstituent] =
    Vector(
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cncf, first),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cml, _framework_profile(ComponentId("org.goldenport.cncf.phase598.HubCml"), "0.1.0-SNAPSHOT", Some(FrameworkPublicationReferenceAvailability.Local))),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cozy, _framework_profile(ComponentId("org.goldenport.cncf.phase598.HubCozy"), "0.1.0-SNAPSHOT", Some(FrameworkPublicationReferenceAvailability.Cached))),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.TextusBok, _framework_profile(ComponentId("org.goldenport.cncf.phase598.HubTextusBok"), "0.1.0-SNAPSHOT", Some(FrameworkPublicationReferenceAvailability.Local))),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.ApprovedRetrievalProvider, _framework_profile(ComponentId("org.goldenport.cncf.phase598.HubProvider"), "0.1.0-SNAPSHOT", Some(FrameworkPublicationReferenceAvailability.Installed)))
    )
}
