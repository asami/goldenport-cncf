package org.goldenport.cncf.knowledge

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity,
  ComponentResourceSourceKind
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable acceptance specification for P594-DOC04-B-S01 framework
 * documentation snapshot/profile composition. Fixtures are caller-supplied
 * values only; this specification does not read resources or invoke a resolver.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class FrameworkDocumentationProfileSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _e1 = afterWord(
    "in spec:framework-documentation-profile, example:E1, rules:DOC04B-S01-AC01, phase:59.4, slice:DOC04-B-S01"
  )
  private val _e2 = afterWord(
    "in spec:framework-documentation-profile, example:E2, rules:DOC04B-S01-AC02, phase:59.4, slice:DOC04-B-S01"
  )
  private val _e3 = afterWord(
    "in spec:framework-documentation-profile, example:E3, rules:DOC04B-S01-AC03, phase:59.4, slice:DOC04-B-S01"
  )
  private val _e4 = afterWord(
    "in spec:framework-documentation-profile, example:E4, rules:DOC04B-S01-AC04, phase:59.4, slice:DOC04-B-S01"
  )
  private val _e5 = afterWord(
    "in spec:framework-documentation-profile, example:E5, rules:DOC04B-S01-AC05, phase:59.4, slice:DOC04-B-S01"
  )

  private val _snapshot_id = ComponentId("org.goldenport.cncf.phase594.FrameworkDocumentation")
  private val _release = "0.1.0-SNAPSHOT"
  private val _other_release = "0.2.0-SNAPSHOT"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

  "P594-DOC04B-S01-AC01 absent framework snapshot" should {
    "E1 succeed as an online canonical framework profile without target or runtime evidence" must _e1 {
      "succeed as an online canonical framework profile without target or runtime evidence" in {
        Given("Spec: docs/spec/framework-documentation-profile.md; Rules: DOC04B-S01-AC01; Example: E1; an Online canonical framework publication with no Documentation Component snapshot and no manifest evidence")
        val publication = _publication(snapshot = None)

        When("the profile factory receives only the caller-supplied framework publication and absent snapshot evidence")
        val profile = FrameworkDocumentationProfile.createC(publication, None).toOption

        Then("it accepts the absent state without a target Component, development context, resolver, operation mode, Help service, path, or runtime input")
        profile shouldBe Some(
          FrameworkDocumentationProfile(publication, FrameworkDocumentationSnapshotProfile.Absent)
        )
      }
    }
  }

  "P594-DOC04B-S01-AC02 installed framework snapshot" should {
    "E2 retain framework identity and accepted public Guide and Skill Catalog metadata" must _e2 {
      "retain framework identity and accepted public Guide and Skill Catalog metadata" in {
        Given("Spec: docs/spec/framework-documentation-profile.md; Rules: DOC04B-S01-AC02; Example: E2; matching installed Documentation Component snapshot evidence with Framework documentation, a Public AI Guide, and an Ecosystem Skill Catalog")
        val publication = _publication(snapshot = Some(_snapshot))
        val manifest = _manifest(publication)

        When("the profile validates the exact supplied framework publication and snapshot manifest")
        val profile = FrameworkDocumentationProfile.createC(publication, Some(manifest)).toOption

        Then("the profile keeps product, version, generation, canonical URL, and digest as framework identity while exposing only descriptive framework snapshot content")
        profile should not be empty
        profile.get.frameworkPublication.productVersion shouldBe publication.productVersion
        profile.get.frameworkPublication.publicationGeneration shouldBe publication.publicationGeneration
        profile.get.frameworkPublication.canonicalUrl shouldBe publication.canonicalUrl
        profile.get.frameworkPublication.sha256 shouldBe publication.sha256
        profile.get.snapshot shouldBe FrameworkDocumentationSnapshotProfile.Present(
          Vector(_framework_documentation_entry),
          Some(_public_ai_guide),
          Some(_public_skill_catalog)
        )
      }
    }
  }

  "P594-DOC04B-S01-AC03 snapshot identity and framework linkage" should {
    "E3 reject missing, mismatched Component identity or release, and different framework publication evidence" must _e3 {
      "reject missing, mismatched Component identity or release, and different framework publication evidence" in {
        Given("Spec: docs/spec/framework-documentation-profile.md; Rules: DOC04B-S01-AC03; Example: E3; otherwise valid snapshot manifests with absent evidence, a different Documentation Component identity or release, or a different framework publication")
        val publication = _publication(snapshot = Some(_snapshot))
        val missingmanifest = None
        val identitymanifest = _manifest(
          publication,
          componentid = ComponentId("org.goldenport.cncf.phase594.OtherDocumentation")
        )
        val releasemanifest = _manifest(publication, release = _other_release)
        val frameworkmanifest = _manifest(_publication(snapshot = Some(_snapshot), generation = "2026-08-25"))
        val missingframeworkmanifest = _manifest(publication).copy(frameworkPublication = None)
        val withoutcontextmanifest = _manifest(publication)
        val withoutcontextpublication = _publication(snapshot = None)

        When("the profile factory checks the exact snapshot and framework context equality boundaries")
        val missing = FrameworkDocumentationProfile.createC(publication, missingmanifest).toOption
        val identity = FrameworkDocumentationProfile.createC(publication, Some(identitymanifest)).toOption
        val release = FrameworkDocumentationProfile.createC(publication, Some(releasemanifest)).toOption
        val framework = FrameworkDocumentationProfile.createC(publication, Some(frameworkmanifest)).toOption
        val missingframework = FrameworkDocumentationProfile.createC(
          publication,
          Some(missingframeworkmanifest)
        ).toOption
        val withoutcontext = FrameworkDocumentationProfile.createC(
          withoutcontextpublication,
          Some(withoutcontextmanifest)
        ).toOption
        val rejected = Vector(missing, identity, release, framework, missingframework, withoutcontext)

        Then("every missing or mismatched identity, release, or framework context is rejected without choosing a fallback Component or publication")
        rejected shouldBe Vector(None, None, None, None, None, None)
      }
    }
  }

  "P594-DOC04B-S01-AC04 framework documentation evidence" should {
    "E4 reject a snapshot without Framework framework-documentation text/markdown evidence" must _e4 {
      "reject a snapshot without Framework framework-documentation text/markdown evidence" in {
        Given("Spec: docs/spec/framework-documentation-profile.md; Rules: DOC04B-S01-AC04; Example: E4; matching snapshot manifests with no Framework documentation entry or with documentation metadata owned by a Component")
        val publication = _publication(snapshot = Some(_snapshot))
        val absentmanifest = _manifest(publication, includedocumentation = false)
        val componentauthoritymanifest = _manifest(
          publication,
          documentationauthority = ComponentKnowledgeAuthority.Component
        )

        When("the profile requires at least one Framework framework-documentation / framework-documentation / text/markdown resource")
        val absent = FrameworkDocumentationProfile.createC(publication, Some(absentmanifest)).toOption
        val componentauthority = FrameworkDocumentationProfile.createC(
          publication,
          Some(componentauthoritymanifest)
        ).toOption
        val rejected = Vector(absent, componentauthority)

        Then("both a missing resource and non-Framework documentation authority are rejected before a snapshot can be profiled")
        rejected shouldBe Vector(None, None)
      }
    }
  }

  "P594-DOC04B-S01-AC05 public snapshot metadata" should {
    "E5 reject Guide and Skill Catalog metadata without Framework authority or Public/Ecosystem visibility" must _e5 {
      "reject Guide and Skill Catalog metadata without Framework authority or Public/Ecosystem visibility" in {
        Given("Spec: docs/spec/framework-documentation-profile.md; Rules: DOC04B-S01-AC05; Example: E5; matching snapshot evidence whose otherwise valid Guide or Skill Catalog resource has non-Framework authority or Project or Restricted visibility")
        val publication = _publication(snapshot = Some(_snapshot))
        val guideauthoritymanifest = _manifest(
          publication,
          guideauthority = ComponentKnowledgeAuthority.Component
        )
        val catalogauthoritymanifest = _manifest(
          publication,
          catalogauthority = ComponentKnowledgeAuthority.External
        )
        val guidevisibilitymanifest = _manifest(
          publication,
          guidevisibility = PublicMetadataVisibility.Project
        )
        val catalogvisibilitymanifest = _manifest(
          publication,
          catalogvisibility = PublicMetadataVisibility.Restricted
        )

        When("the profile validates optional Guide and Skill Catalog metadata as descriptive framework snapshot content")
        val guideauthority = FrameworkDocumentationProfile.createC(
          publication,
          Some(guideauthoritymanifest)
        ).toOption
        val catalogauthority = FrameworkDocumentationProfile.createC(
          publication,
          Some(catalogauthoritymanifest)
        ).toOption
        val guidevisibility = FrameworkDocumentationProfile.createC(
          publication,
          Some(guidevisibilitymanifest)
        ).toOption
        val catalogvisibility = FrameworkDocumentationProfile.createC(
          publication,
          Some(catalogvisibilitymanifest)
        ).toOption
        val rejected = Vector(guideauthority, catalogauthority, guidevisibility, catalogvisibility)

        Then("neither non-Framework nor non-public Guide or Skill Catalog metadata is admitted as framework snapshot content")
        rejected shouldBe Vector(None, None, None, None)
      }
    }
  }

  private def _publication(
    snapshot: Option[FrameworkDocumentationComponentSnapshot],
    generation: String = "2026-08-26"
  ): FrameworkPublicationContext =
    FrameworkPublicationContext(
      productVersion = FrameworkProductVersion("simplemodeling", "0.1.0"),
      canonicalUrl = "https://www.simplemodeling.org/framework/0.1.0",
      publicationGeneration = generation,
      documentId = "https://www.simplemodeling.org/framework/0.1.0/documentation",
      sectionId = Some("https://www.simplemodeling.org/framework/0.1.0/documentation#overview"),
      sha256 = _digest,
      availability = FrameworkPublicationReferenceAvailability.Online,
      generatedFrom = FrameworkPublicationGeneratedFrom(
        "urn:simplemodeling:source:framework:0.1.0",
        _digest
      ),
      documentationComponentSnapshot = snapshot
    )

  private def _snapshot: FrameworkDocumentationComponentSnapshot =
    FrameworkDocumentationComponentSnapshot(
      _snapshot_id,
      _release,
      _digest,
      FrameworkPublicationReferenceAvailability.Installed
    )

  private def _manifest(
    publication: FrameworkPublicationContext,
    componentid: ComponentId = _snapshot_id,
    release: String = _release,
    includedocumentation: Boolean = true,
    documentationauthority: ComponentKnowledgeAuthority = ComponentKnowledgeAuthority.Framework,
    guideauthority: ComponentKnowledgeAuthority = ComponentKnowledgeAuthority.Framework,
    catalogauthority: ComponentKnowledgeAuthority = ComponentKnowledgeAuthority.Framework,
    guidevisibility: PublicMetadataVisibility = PublicMetadataVisibility.Public,
    catalogvisibility: PublicMetadataVisibility = PublicMetadataVisibility.Ecosystem
  ): ComponentKnowledgeManifest = {
    val documentation = _framework_documentation_entry(componentid, release, documentationauthority)
    val guideentry = _guide_entry(componentid, release, guideauthority)
    val catalogentry = _skill_catalog_entry(componentid, release, catalogauthority)
    val resources = Vector(guideentry, catalogentry) ++ Option.when(includedocumentation)(documentation)
    ComponentKnowledgeManifest(
      componentId = componentid,
      logicalRelease = release,
      resources = resources,
      frameworkPublication = Some(publication),
      publicDirective = Some(_public_ai_guide(guideentry, guidevisibility)),
      skillCatalog = Some(_public_skill_catalog(catalogentry, catalogvisibility))
    )
  }

  private def _framework_documentation_entry: ComponentKnowledgeResourceEntry =
    _framework_documentation_entry(_snapshot_id, _release, ComponentKnowledgeAuthority.Framework)

  private def _framework_documentation_entry(
    componentid: ComponentId,
    release: String,
    authority: ComponentKnowledgeAuthority
  ): ComponentKnowledgeResourceEntry =
    _entry(
      componentid,
      release,
      "FrameworkDocumentation",
      "urn:cncf:resource:phase594:framework-documentation",
      "framework/documentation.md",
      ComponentKnowledgeResourceKind.FrameworkDocumentation,
      ComponentKnowledgeResourceRole.FrameworkDocumentation,
      ComponentKnowledgeMediaType.TextMarkdown,
      authority
    )

  private def _guide_entry(
    componentid: ComponentId,
    release: String,
    authority: ComponentKnowledgeAuthority = ComponentKnowledgeAuthority.Framework
  ): ComponentKnowledgeResourceEntry =
    _entry(
      componentid,
      release,
      "Directive",
      "urn:cncf:resource:phase594:public-ai-guide",
      "framework/public-ai-guide.yaml",
      ComponentKnowledgeResourceKind.Directive,
      ComponentKnowledgeResourceRole.Directive,
      ComponentKnowledgeMediaType.ApplicationYaml,
      authority
    )

  private def _skill_catalog_entry(
    componentid: ComponentId,
    release: String,
    authority: ComponentKnowledgeAuthority = ComponentKnowledgeAuthority.Framework
  ): ComponentKnowledgeResourceEntry =
    _entry(
      componentid,
      release,
      "SkillCatalog",
      "urn:cncf:resource:phase594:public-skill-catalog",
      "framework/public-skill-catalog.json",
      ComponentKnowledgeResourceKind.SkillCatalog,
      ComponentKnowledgeResourceRole.SkillCatalog,
      ComponentKnowledgeMediaType.ApplicationJson,
      authority
    )

  private def _entry(
    componentid: ComponentId,
    release: String,
    childrole: String,
    logicalresource: String,
    logicalpath: String,
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    media: ComponentKnowledgeMediaType,
    authority: ComponentKnowledgeAuthority
  ): ComponentKnowledgeResourceEntry =
    ComponentKnowledgeResourceEntry(
      binding = ComponentKnowledgeResourceBinding(
        ComponentResourceLogicalIdentity(componentid, release, None, childrole, logicalresource)
      ),
      logicalPath = logicalpath,
      kind = kind,
      role = role,
      language = None,
      mediaType = media,
      size = 42,
      sha256 = _digest,
      metadata = ComponentKnowledgeMetadata(
        authority,
        ComponentKnowledgeStability.Stable,
        ComponentKnowledgeSource.SuppliedPhase58,
        "Apache-2.0",
        ComponentKnowledgeDisclosure.MetadataOnly
      ),
      availability = ComponentResourceAvailability.Available,
      integrity = ComponentResourceIntegrity.Verified,
      authorization = ComponentResourceAuthorization.Granted,
      provenance = ComponentKnowledgeSafeProvenance(
        ComponentResourceSourceKind.ExpandedCar,
        "org.example:phase594-framework-documentation:0.1.0",
        "component-registry:framework-documentation",
        "expanded-car:2",
        false,
        _digest
      )
    )

  private def _public_ai_guide: PublicDirectiveProjection =
    _public_ai_guide(_guide_entry(_snapshot_id, _release), PublicMetadataVisibility.Public)

  private def _public_ai_guide(
    entry: ComponentKnowledgeResourceEntry,
    visibility: PublicMetadataVisibility
  ): PublicDirectiveProjection =
    PublicDirectiveProjection(
      entry = entry,
      directiveId = "framework-public-ai-guide",
      profileId = "framework-public-profile",
      ruleId = "framework-public-rule-identity",
      origin = "urn:cncf:directive:framework-public",
      version = "1.0.0",
      authority = PublicDirectiveAuthority.MountedDirectiveRemainsAuthoritative,
      visibility = visibility,
      sourceSha256 = _digest,
      redaction = PublicDirectiveRedaction.SourceAndRuleContentWithheld,
      guideReference = "https://www.simplemodeling.org/framework/guide"
    )

  private def _public_skill_catalog: PublicSkillCatalog =
    _public_skill_catalog(_skill_catalog_entry(_snapshot_id, _release), PublicMetadataVisibility.Ecosystem)

  private def _public_skill_catalog(
    entry: ComponentKnowledgeResourceEntry,
    visibility: PublicMetadataVisibility
  ): PublicSkillCatalog =
    PublicSkillCatalog(
      entry = entry,
      catalogId = "framework-public-skill-catalog",
      owner = "cncf",
      purpose = "descriptive framework snapshot catalog",
      trigger = "explicit user request",
      requirements = Vector("framework publication"),
      permissions = Vector("metadata visibility"),
      sideEffects = Vector("none"),
      mcpRequirements = Vector("descriptive only"),
      installationReference = "https://www.simplemodeling.org/framework/skills",
      visibility = visibility,
      version = "1.0.0",
      sourceSha256 = _digest
    )
}
