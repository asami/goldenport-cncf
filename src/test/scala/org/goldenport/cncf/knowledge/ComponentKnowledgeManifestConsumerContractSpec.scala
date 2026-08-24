package org.goldenport.cncf.knowledge

import io.circe.Json
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity,
  ComponentResourceSourceKind
}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable acceptance specification for DOC-02D stable, read-only Component
 * knowledge manifest consumer projection and codec.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeManifestConsumerContractSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _component_id = ComponentId("org.goldenport.cncf.phase59.Consumer")
  private val _skill_component_id = ComponentId("org.goldenport.cncf.phase59.ConsumerSkill")
  private val _release = "0.1.0-SNAPSHOT"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _other_digest = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

  "DOC02D-01 stable consumer projection" should {
    "project validated optional evidence into a deterministic value-only consumer contract" in {
      Given("a validated manifest with safe extensions at every matching consumer evidence level and noncanonical resource order")
      val rootextensions = Map("futureRoot" -> Json.obj("value" -> Json.fromString("root")))
      val resourceextensions = Map("futureResource" -> Json.obj("value" -> Json.fromString("resource")))
      val metadataextensions = Map("futureMetadata" -> Json.obj("value" -> Json.fromString("metadata")))
      val provenanceextensions = Map("futureProvenance" -> Json.obj("value" -> Json.fromString("provenance")))
      val frameworkextensions = Map("futureFramework" -> Json.obj("value" -> Json.fromString("framework")))
      val snapshotextensions = Map("futureSnapshot" -> Json.obj("value" -> Json.fromString("snapshot")))
      val modelextensions = Map("futureModel" -> Json.obj("value" -> Json.fromString("model")))
      val modelreferenceextensions = Map("futureModelReference" -> Json.obj("value" -> Json.fromString("model-reference")))
      val diagramextensions = Map("futureDiagram" -> Json.obj("value" -> Json.fromString("diagram")))
      val generatedfromextensions = Map("futureGeneratedFrom" -> Json.obj("value" -> Json.fromString("generated-from")))
      val directiveextensions = Map("futureDirective" -> Json.obj("value" -> Json.fromString("directive")))
      val catalogextensions = Map("futureCatalog" -> Json.obj("value" -> Json.fromString("catalog")))
      val directiveentry = _directive_entry.copy(
        metadata = _directive_entry.metadata.copy(extensions = metadataextensions),
        provenance = _directive_entry.provenance.copy(extensions = provenanceextensions),
        extensions = resourceextensions
      )
      val framework = _framework_publication.copy(
        documentationComponentSnapshot = Some(FrameworkDocumentationComponentSnapshot(_component_id, _release, _digest, FrameworkPublicationReferenceAvailability.Online, snapshotextensions)),
        extensions = frameworkextensions
      )
      val modelresources = _model_resources.copy(
        models = _model_resources.models.map(_.copy(extensions = modelreferenceextensions)),
        diagrams = _model_resources.diagrams.map { diagram =>
          diagram.copy(
            generatedFrom = diagram.generatedFrom.map(_.copy(extensions = generatedfromextensions)),
            extensions = diagramextensions
          )
        },
        extensions = modelextensions
      )
      val manifest = _manifest.copy(
        resources = _resources.map(value => if (value == _directive_entry) directiveentry else value).reverse,
        extensions = rootextensions,
        frameworkPublication = Some(framework),
        modelResources = Some(modelresources),
        publicDirective = Some(_directive.copy(entry = directiveentry, extensions = directiveextensions)),
        skillCatalog = Some(_catalog.copy(extensions = catalogextensions))
      )

      When("the pure projection creates and the strict codec round-trips the consumer value")
      val projected = ComponentKnowledgeManifestConsumerContract.fromManifestC(manifest).toOption
      val encoded = projected.map(ComponentKnowledgeManifestConsumerContractCodec.encode)
      val decoded = encoded.flatMap(value => ComponentKnowledgeManifestConsumerContractCodec.decodeC(value).toOption)

      Then("safe evidence, extensions at every matching level, and canonical resource/reference ordering are preserved without a resolver or runtime action")
      projected should not be empty
      decoded shouldBe projected
      encoded should not be empty
      encoded.get should include ("\"schema\":\"cncf.component-knowledge-consumer.v1\"")
      projected.get.resources.map(_.logicalPath) shouldBe projected.get.resources.map(_.logicalPath).sorted
      projected.get.frameworkPublication.map(_.product) shouldBe Some("simplemodeling")
      projected.get.modelResources.map(_.models.map(_.logicalIdentity.logicalResource)) shouldBe Some(Vector(_entity_entry.binding.logicalIdentity.logicalResource))
      projected.get.publicDirective.map(_.redaction) shouldBe Some(PublicDirectiveRedaction.SourceAndRuleContentWithheld)
      projected.get.skillCatalog.map(_.installationReference) shouldBe Some("https://example.com/skill-installation")
      projected.map(_.extensions) shouldBe Some(rootextensions)
      projected.get.resources.find(_.logicalIdentity == directiveentry.binding.logicalIdentity).map(_.extensions) shouldBe Some(resourceextensions)
      projected.get.resources.find(_.logicalIdentity == directiveentry.binding.logicalIdentity).map(_.metadata.extensions) shouldBe Some(metadataextensions)
      projected.get.resources.find(_.logicalIdentity == directiveentry.binding.logicalIdentity).map(_.provenance.extensions) shouldBe Some(provenanceextensions)
      projected.flatMap(_.frameworkPublication).map(_.extensions) shouldBe Some(frameworkextensions)
      projected.flatMap(_.frameworkPublication).flatMap(_.documentationComponentSnapshot).map(_.extensions) shouldBe Some(snapshotextensions)
      projected.flatMap(_.modelResources).map(_.extensions) shouldBe Some(modelextensions)
      projected.flatMap(_.modelResources).map(_.models.head.extensions) shouldBe Some(modelreferenceextensions)
      projected.flatMap(_.modelResources).map(_.diagrams.head.extensions) shouldBe Some(diagramextensions)
      projected.flatMap(_.modelResources).map(_.diagrams.head.generatedFrom.head.extensions) shouldBe Some(generatedfromextensions)
      projected.flatMap(_.publicDirective).map(_.extensions) shouldBe Some(directiveextensions)
      projected.flatMap(_.skillCatalog).map(_.extensions) shouldBe Some(catalogextensions)
    }

    "canonicalize future-safe consumer extensions for fifty generated value-space cases" in {
      Given("a projected consumer contract carrying only a safe future extension value")
      val base = ComponentKnowledgeManifestConsumerContract.fromManifestC(_manifest).toOption.get
      val property = Prop.forAll(Gen.choose(1, 500)) { number =>
        val candidate = base.copy(extensions = Map("futureConsumer" -> Json.obj("value" -> Json.fromInt(number), "a" -> Json.fromBoolean(true))))
        val encoded = ComponentKnowledgeManifestConsumerContractCodec.encode(candidate)
        ComponentKnowledgeManifestConsumerContractCodec.decodeC(encoded).toOption.contains(candidate) &&
          ComponentKnowledgeManifestConsumerContractCodec.encode(candidate) == encoded
      }

      When("the deterministic consumer codec evaluates the generated extension value space")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      val encoded = ComponentKnowledgeManifestConsumerContractCodec.encode(base.copy(extensions = Map("futureConsumer" -> Json.obj("z" -> Json.fromInt(2), "a" -> Json.fromInt(1)))))

      Then("every generated safe value round-trips and extension object keys are lexical")
      checked.passed shouldBe true
      encoded should include ("\"futureConsumer\":{\"a\":1,\"z\":2}")
    }
  }

  "DOC02D-01 consumer hostile input boundary" should {
    "reject protected aliases, invalid linked evidence, and duplicate JSON keys" in {
      Given("an otherwise valid consumer value and codec input with one hostile extension, invalid reference, or duplicate key")
      val contract = ComponentKnowledgeManifestConsumerContract.fromManifestC(_manifest).toOption.get
      val rootalias = contract.copy(extensions = Map("activationConfiguration" -> Json.fromBoolean(true)))
      val nestedalias = contract.copy(resources = contract.resources.updated(0, contract.resources.head.copy(extensions = Map("future" -> Json.obj("approvalToken" -> Json.fromBoolean(true))))))
      val badsource = contract.copy(publicDirective = contract.publicDirective.map(value => value.copy(sourceSha256 = _other_digest)))
      val badidentity = contract.copy(skillCatalog = contract.skillCatalog.map(value => value.copy(logicalIdentity = _directive_entry.binding.logicalIdentity)))
      val unknownmodel = contract.copy(modelResources = contract.modelResources.map(value => value.copy(models = Vector(ComponentKnowledgeManifestConsumerModelReferenceEvidence(_directive_entry.binding.logicalIdentity)))))
      val canonical = ComponentKnowledgeManifestConsumerContractCodec.encode(contract)
      val duplicate = canonical.replace("\"schema\":\"cncf.component-knowledge-consumer.v1\",", "\"schema\":\"cncf.component-knowledge-consumer.v1\",\"schema\":\"cncf.component-knowledge-consumer.v1\",")
      val hostile = canonical.replace("\"resources\":[", "\"future\":{\"physicalReadPath\":true},\"resources\":[")

      When("contract validation and strict decoding process each candidate")
      val invalid = Vector(rootalias, nestedalias, badsource, badidentity, unknownmodel).map(ComponentKnowledgeManifestConsumerContract.validateC(_).toOption)
      val duplicated = ComponentKnowledgeManifestConsumerContractCodec.decodeC(duplicate).toOption
      val hostiledecoded = ComponentKnowledgeManifestConsumerContractCodec.decodeC(hostile).toOption

      Then("protected alias normalization, exact resource linkage, and duplicate-key rejection keep the consumer contract read-only")
      invalid shouldBe Vector.fill(5)(None)
      duplicated shouldBe None
      hostiledecoded shouldBe None
    }
  }

  private def _manifest: ComponentKnowledgeManifest =
    ComponentKnowledgeManifest(
      componentId = _component_id,
      logicalRelease = _release,
      resources = _resources,
      frameworkPublication = Some(_framework_publication),
      modelResources = Some(_model_resources),
      publicDirective = Some(_directive),
      skillCatalog = Some(_catalog)
    )

  private def _resources: Vector[ComponentKnowledgeResourceEntry] =
    Vector(_directive_entry, _skill_entry, _entity_entry, _diagram_entry)

  private def _framework_publication: FrameworkPublicationContext =
    FrameworkPublicationContext(
      productVersion = FrameworkProductVersion("simplemodeling", "0.1.0"),
      canonicalUrl = "https://www.simplemodeling.org/framework/0.1.0",
      publicationGeneration = "2026-08-24",
      documentId = "https://www.simplemodeling.org/framework/0.1.0/documentation",
      sectionId = None,
      sha256 = _digest,
      availability = FrameworkPublicationReferenceAvailability.Online,
      generatedFrom = FrameworkPublicationGeneratedFrom("urn:simplemodeling:source:framework:0.1.0", _digest)
    )

  private def _model_resources: PortableModelResourceContext =
    PortableModelResourceContext(
      models = Vector(PortableModelResource(_entity_entry)),
      diagrams = Vector(PortableDiagramResource(_diagram_entry, Vector(PortableDiagramGeneratedFrom(_entity_entry.binding.logicalIdentity, _digest))))
    )

  private def _directive: PublicDirectiveProjection =
    PublicDirectiveProjection(
      entry = _directive_entry,
      directiveId = "mounted-directive",
      profileId = "public-profile",
      ruleId = "public-rule-identity",
      origin = "urn:cncf:directive:public",
      version = "1.0.0",
      authority = PublicDirectiveAuthority.MountedDirectiveRemainsAuthoritative,
      visibility = PublicMetadataVisibility.Public,
      sourceSha256 = _digest,
      redaction = PublicDirectiveRedaction.SourceAndRuleContentWithheld,
      guideReference = "https://example.com/directive-guide"
    )

  private def _catalog: PublicSkillCatalog =
    PublicSkillCatalog(
      entry = _skill_entry,
      catalogId = "public-skill-catalog",
      owner = "cncf",
      purpose = "descriptive public catalog metadata",
      trigger = "explicit user request",
      requirements = Vector("component knowledge manifest"),
      permissions = Vector("metadata visibility"),
      sideEffects = Vector("none"),
      mcpRequirements = Vector("descriptive only"),
      installationReference = "https://example.com/skill-installation",
      visibility = PublicMetadataVisibility.Ecosystem,
      version = "1.0.0",
      sourceSha256 = _digest
    )

  private def _directive_entry: ComponentKnowledgeResourceEntry =
    _entry(ComponentKnowledgeResourceKind.Directive, ComponentKnowledgeResourceRole.Directive, ComponentKnowledgeMediaType.ApplicationYaml, _component_id, None, "Directive", "urn:cncf:resource:phase59:consumer-directive", "directive/public.yaml")

  private def _skill_entry: ComponentKnowledgeResourceEntry =
    _entry(ComponentKnowledgeResourceKind.SkillCatalog, ComponentKnowledgeResourceRole.SkillCatalog, ComponentKnowledgeMediaType.ApplicationJson, _skill_component_id, Some(_component_id), "SkillCatalog", "urn:cncf:resource:phase59:consumer-skill-catalog", "skills/catalog.json")

  private def _entity_entry: ComponentKnowledgeResourceEntry =
    _entry(ComponentKnowledgeResourceKind.Entity, ComponentKnowledgeResourceRole.Model, ComponentKnowledgeMediaType.ApplicationJson, _component_id, None, "Entity", "urn:cncf:resource:phase59:consumer-entity", "models/entity.json")

  private def _diagram_entry: ComponentKnowledgeResourceEntry =
    _entry(ComponentKnowledgeResourceKind.ClassDiagram, ComponentKnowledgeResourceRole.Diagram, ComponentKnowledgeMediaType.ImageSvgXml, _component_id, None, "ClassDiagram", "urn:cncf:resource:phase59:consumer-class-diagram", "diagrams/class.svg")

  private def _entry(
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    media: ComponentKnowledgeMediaType,
    componentid: ComponentId,
    parentid: Option[ComponentId],
    childrole: String,
    logicalresource: String,
    logicalpath: String
  ): ComponentKnowledgeResourceEntry =
    ComponentKnowledgeResourceEntry(
      binding = ComponentKnowledgeResourceBinding(ComponentResourceLogicalIdentity(componentid, _release, parentid, childrole, logicalresource)),
      logicalPath = logicalpath,
      kind = kind,
      role = role,
      language = None,
      mediaType = media,
      size = 42,
      sha256 = _digest,
      metadata = ComponentKnowledgeMetadata(ComponentKnowledgeAuthority.Component, ComponentKnowledgeStability.Stable, ComponentKnowledgeSource.SuppliedPhase58, "Apache-2.0", ComponentKnowledgeDisclosure.MetadataOnly),
      availability = ComponentResourceAvailability.Available,
      integrity = ComponentResourceIntegrity.Verified,
      authorization = ComponentResourceAuthorization.Granted,
      provenance = ComponentKnowledgeSafeProvenance(ComponentResourceSourceKind.ExpandedCar, "org.example:phase59-consumer:0.1.0", "component-registry:consumer", "expanded-car:2", false, _digest)
    )
}
