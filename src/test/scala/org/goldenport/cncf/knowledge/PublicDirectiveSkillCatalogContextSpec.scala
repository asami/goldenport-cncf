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
 * Executable acceptance specification for DOC-02D public Directive and Skill
 * Catalog descriptive metadata.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class PublicDirectiveSkillCatalogContextSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _component_id = ComponentId("org.goldenport.cncf.phase59.PublicMetadata")
  private val _skill_component_id = ComponentId("org.goldenport.cncf.phase59.PublicSkillCatalog")
  private val _release = "0.1.0-SNAPSHOT"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _other_digest = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

  "DOC02D-01 public Directive and Skill Catalog canonical metadata" should {
    "round-trip deterministically with all visibility values and lexical safe extensions" in {
      Given("a manifest whose Directive and Skill Catalog metadata exactly reference existing safe entries")
      val directive = _directive.copy(extensions = Map("futureDirective" -> Json.obj("z" -> Json.fromInt(2), "a" -> Json.fromInt(1))))
      val catalog = _catalog.copy(extensions = Map("futureCatalog" -> Json.obj("y" -> Json.fromInt(2), "b" -> Json.fromInt(1))))
      val manifest = _manifest.copy(
        extensions = Map("futureRoot" -> Json.obj("z" -> Json.fromInt(2), "a" -> Json.fromInt(1))),
        publicDirective = Some(directive),
        skillCatalog = Some(catalog)
      )
      val visibilities = PublicMetadataVisibility.values.toVector
      val property = Prop.forAll(Gen.choose(1, 500)) { number =>
        val candidate = manifest.copy(publicDirective = Some(directive.copy(version = s"1.$number")))
        val encoded = ComponentKnowledgeManifestCodec.encode(candidate)
        ComponentKnowledgeManifestCodec.decodeC(encoded).toOption.contains(candidate) &&
          ComponentKnowledgeManifestCodec.encode(candidate) == encoded
      }

      When("the duplicate-key-rejecting manifest and context codecs canonicalize the value space")
      val encoded = ComponentKnowledgeManifestCodec.encode(manifest)
      val decoded = ComponentKnowledgeManifestCodec.decodeC(encoded).toOption
      val directivevalues = visibilities.map(value => _directive.copy(visibility = value))
      val catalogvalues = visibilities.map(value => _catalog.copy(visibility = value))
      val directiveroundtrips = directivevalues.map(value => PublicDirectiveSkillCatalogContextCodec.decodeDirectiveC(PublicDirectiveSkillCatalogContextCodec.encodeDirective(value, _resources), _resources).toOption)
      val catalogroundtrips = catalogvalues.map(value => PublicDirectiveSkillCatalogContextCodec.decodeSkillCatalogC(PublicDirectiveSkillCatalogContextCodec.encodeSkillCatalog(value, _resources), _resources).toOption)
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("known fields, safe extensions, all admitted visibility values, and fifty generated canonical cases are preserved")
      decoded shouldBe Some(manifest)
      encoded should include ("\"futureRoot\":{\"a\":1,\"z\":2}")
      encoded should include ("\"futureDirective\":{\"a\":1,\"z\":2}")
      encoded should include ("\"futureCatalog\":{\"b\":1,\"y\":2}")
      encoded.indexOf("\"modelResources\"") should be < 0
      encoded.indexOf("\"publicDirective\"") should be < encoded.indexOf("\"skillCatalog\"")
      encoded.indexOf("\"skillCatalog\"") should be < encoded.indexOf("\"resources\"")
      directiveroundtrips.flatten.map(_.visibility) shouldBe visibilities
      catalogroundtrips.flatten.map(_.visibility) shouldBe visibilities
      checked.passed shouldBe true
    }

    "leave DOC-02A through DOC-02C root output stable when both new roots are absent" in {
      Given("a pre-DOC-02D manifest with no public metadata roots")
      val legacy = _manifest.copy(publicDirective = None, skillCatalog = None)

      When("the manifest codec serializes and decodes the legacy root")
      val encoded = ComponentKnowledgeManifestCodec.encode(legacy)
      val decoded = ComponentKnowledgeManifestCodec.decodeC(encoded).toOption

      Then("the canonical root omits both fields and re-encodes byte-for-byte")
      encoded should not include "publicDirective"
      encoded should not include "skillCatalog"
      encoded should include ("\"logicalRelease\":\"0.1.0-SNAPSHOT\",\"resources\"")
      decoded.map(ComponentKnowledgeManifestCodec.encode) shouldBe Some(encoded)
    }
  }

  "DOC02D-01 exact evidence linkage and public metadata boundary" should {
    "reject wrong referenced evidence, digest, URI, collection, and protected source/profile/rule/prompt extension material" in {
      Given("otherwise valid public metadata values with one invalid linkage, descriptive value, or recursively protected extension alias/prefix")
      val wrongdirectiveentry = _directive.copy(entry = _skill_entry)
      val wrongcatalogentry = _catalog.copy(entry = _directive_entry)
      val unlistedentry = _directive.copy(entry = _directive_entry.copy(logicalPath = "directive/unlisted.yaml"))
      val digest = _directive.copy(sourceSha256 = _other_digest)
      val malformedorigin = _directive.copy(origin = "directive/origin")
      val malformedguide = _directive.copy(guideReference = "https://WWW.example.com/guide")
      val malformedinstallation = _catalog.copy(installationReference = "https://example.com/install?unsafe=true")
      val emptyrequirements = _catalog.copy(requirements = Vector.empty)
      val duplicatepermissions = _catalog.copy(permissions = Vector("network", "network"))
      val emptytrigger = _catalog.copy(trigger = "")
      val directalias = _directive.copy(extensions = Map("installConfig" -> Json.fromBoolean(true)))
      val sourcealias = _directive.copy(extensions = Map("sourceText" -> Json.fromBoolean(true)))
      val profilealias = _directive.copy(extensions = Map("profileText" -> Json.fromBoolean(true)))
      val nestedalias = _catalog.copy(extensions = Map("future" -> Json.obj("Authorization-Approval" -> Json.fromBoolean(true))))
      val nestedmcpalias = _catalog.copy(extensions = Map("future" -> Json.arr(Json.obj("futureMcpExecution" -> Json.fromBoolean(true)))))
      val rulealias = _catalog.copy(extensions = Map("ruleText" -> Json.fromBoolean(true)))
      val promptalias = _catalog.copy(extensions = Map("promptText" -> Json.fromBoolean(true)))
      val nestedsourceprefix = _directive.copy(extensions = Map("future" -> Json.obj("rawSourceMaterial" -> Json.fromBoolean(true))))
      val nestedprofileprefix = _directive.copy(extensions = Map("future" -> Json.obj("rawProfileMaterial" -> Json.fromBoolean(true))))
      val nestedruleprefix = _catalog.copy(extensions = Map("future" -> Json.obj("rawRuleMaterial" -> Json.fromBoolean(true))))
      val nestedpromptprefix = _catalog.copy(extensions = Map("future" -> Json.obj("rawPromptMaterial" -> Json.fromBoolean(true))))

      When("context validation evaluates exact manifest membership, matching SHA-256, safe descriptive values, and normalized protected aliases/prefixes")
      val invaliddirectives = Vector(wrongdirectiveentry, unlistedentry, digest, malformedorigin, malformedguide, directalias, sourcealias, profilealias, nestedsourceprefix, nestedprofileprefix).map(PublicDirectiveProjection.validateC(_, _resources).toOption)
      val invalidcatalogs = Vector(wrongcatalogentry, malformedinstallation, emptyrequirements, duplicatepermissions, emptytrigger, nestedalias, nestedmcpalias, rulealias, promptalias, nestedruleprefix, nestedpromptprefix).map(PublicSkillCatalog.validateC(_, _resources).toOption)

      Then("all values, including source/profile/rule/prompt material aliases, are rejected before public metadata can become a binding, installation, activation, execution, or MCP authority")
      invaliddirectives shouldBe Vector.fill(10)(None)
      invalidcatalogs shouldBe Vector.fill(11)(None)
    }

    "reject duplicate JSON keys and hostile public-context codec input" in {
      Given("canonical metadata JSON with a duplicate known key or one protected nested extension key")
      val directivejson = PublicDirectiveSkillCatalogContextCodec.encodeDirective(_directive, _resources)
      val duplicate = directivejson.replace("\"directiveId\":\"mounted-directive\",", "\"directiveId\":\"mounted-directive\",\"directiveId\":\"mounted-directive\",")
      val hostile = directivejson.replace("\"guideReference\":", "\"future\":{\"physicalReadPath\":true},\"guideReference\":")
      val manifestjson = ComponentKnowledgeManifestCodec.encode(_manifest)
      val duplicateroot = manifestjson.replace("\"publicDirective\":", "\"publicDirective\":null,\"publicDirective\":")

      When("the strict parsers decode each hostile JSON input")
      val duplicatedirective = PublicDirectiveSkillCatalogContextCodec.decodeDirectiveC(duplicate, _resources).toOption
      val hostiledirective = PublicDirectiveSkillCatalogContextCodec.decodeDirectiveC(hostile, _resources).toOption
      val duplicatemanifest = ComponentKnowledgeManifestCodec.decodeC(duplicateroot).toOption

      Then("duplicate keys and recursive content, path, or reader aliases are rejected")
      duplicatedirective shouldBe None
      hostiledirective shouldBe None
      duplicatemanifest shouldBe None
    }
  }

  private def _manifest: ComponentKnowledgeManifest =
    ComponentKnowledgeManifest(
      componentId = _component_id,
      logicalRelease = _release,
      resources = _resources,
      publicDirective = Some(_directive),
      skillCatalog = Some(_catalog)
    )

  private def _resources: Vector[ComponentKnowledgeResourceEntry] = Vector(_directive_entry, _skill_entry)

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
    _entry(
      componentid = _component_id,
      parentid = None,
      childrole = "Directive",
      logicalresource = "urn:cncf:resource:phase59:directive",
      logicalpath = "directive/public.yaml",
      kind = ComponentKnowledgeResourceKind.Directive,
      role = ComponentKnowledgeResourceRole.Directive,
      media = ComponentKnowledgeMediaType.ApplicationYaml
    )

  private def _skill_entry: ComponentKnowledgeResourceEntry =
    _entry(
      componentid = _skill_component_id,
      parentid = Some(_component_id),
      childrole = "SkillCatalog",
      logicalresource = "urn:cncf:resource:phase59:skill-catalog",
      logicalpath = "skills/catalog.json",
      kind = ComponentKnowledgeResourceKind.SkillCatalog,
      role = ComponentKnowledgeResourceRole.SkillCatalog,
      media = ComponentKnowledgeMediaType.ApplicationJson
    )

  private def _entry(
    componentid: ComponentId,
    parentid: Option[ComponentId],
    childrole: String,
    logicalresource: String,
    logicalpath: String,
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    media: ComponentKnowledgeMediaType
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
      provenance = ComponentKnowledgeSafeProvenance(ComponentResourceSourceKind.ExpandedCar, "org.example:phase59-public-metadata:0.1.0", "component-registry:public-metadata", "expanded-car:2", false, _digest)
    )
}
