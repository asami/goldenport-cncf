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
 * Executable acceptance specification for P594-DOC04-B-S02 closed-network
 * Documentation Hub SAR profile composition. All evidence is caller supplied.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class ClosedNetworkDocumentationHubSarProfileSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _e1 = afterWord(
    "in spec:closed-network-documentation-hub-sar-profile, example:E1, rules:DOC04B-S02-AC01, phase:59.4, slice:DOC04-B-S02"
  )
  private val _e2 = afterWord(
    "in spec:closed-network-documentation-hub-sar-profile, example:E2, rules:DOC04B-S02-AC02, phase:59.4, slice:DOC04-B-S02"
  )
  private val _e3 = afterWord(
    "in spec:closed-network-documentation-hub-sar-profile, example:E3, rules:DOC04B-S02-AC03, phase:59.4, slice:DOC04-B-S02"
  )
  private val _e4 = afterWord(
    "in spec:closed-network-documentation-hub-sar-profile, example:E4, rules:DOC04B-S02-AC04, phase:59.4, slice:DOC04-B-S02"
  )

  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

  "P594-DOC04B-S02-AC01 complete closed-network Hub composition" should {
    "E1 accept all five deterministic roles with Local, Installed, and Cached snapshots" must _e1 {
      "accept all five deterministic roles with Local, Installed, and Cached snapshots" in {
        Given("Spec: docs/spec/closed-network-documentation-hub-sar-profile.md; Rules: DOC04B-S02-AC01; Example: E1; five ordered constituents with unique local, installed, or cached Documentation Component snapshots")
        val constituents = _constituents

        When("the Hub factory receives only SAR metadata and the complete ordered typed snapshot composition")
        val profile = ClosedNetworkDocumentationHubSarProfile.createC(
          "cncf.documentation-hub",
          "0.1.0-SNAPSHOT",
          constituents
        ).toOption

        Then("it retains the exact role order and S01 framework product identities without constructing or looking up a SAR runtime artifact")
        profile shouldBe Some(
          ClosedNetworkDocumentationHubSarProfile(
            "cncf.documentation-hub",
            "0.1.0-SNAPSHOT",
            constituents
          )
        )
        profile.get.constituents.map(_.role) shouldBe DocumentationHubSarConstituentRole.values.toVector
        profile.get.constituents.map(_.profile.frameworkPublication.productVersion.product) shouldBe Vector(
          "cncf",
          "cml",
          "cozy",
          "textus-bok",
          "approved-retrieval-provider"
        )
      }
    }
  }

  "P594-DOC04B-S02-AC02 deterministic role and snapshot identity conditions" should {
    "E2 reject missing, duplicate, reordered roles and a duplicate snapshot identity" must _e2 {
      "reject missing, duplicate, reordered roles and a duplicate snapshot identity" in {
        Given("Spec: docs/spec/closed-network-documentation-hub-sar-profile.md; Rules: DOC04B-S02-AC02; Example: E2; otherwise valid constituents that omit, duplicate, reorder a role, or reuse one Documentation Component snapshot identity")
        val constituents = _constituents
        val missingroles = constituents.dropRight(1)
        val duplicateroles = constituents.updated(
          4,
          DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cozy, _profile(6, FrameworkPublicationReferenceAvailability.Cached))
        )
        val reorderedroles = constituents.updated(0, constituents(1)).updated(1, constituents(0))
        val duplicatesnapshot = constituents.updated(
          4,
          DocumentationHubSarConstituent(
            DocumentationHubSarConstituentRole.ApprovedRetrievalProvider,
            constituents(1).profile
          )
        )

        When("the factory checks the exact declared role sequence and cross-slot Documentation Component snapshot identities")
        val rejected = Vector(missingroles, duplicateroles, reorderedroles, duplicatesnapshot).map { candidate =>
          ClosedNetworkDocumentationHubSarProfile.createC("cncf.documentation-hub", "0.1.0", candidate).toOption
        }

        Then("each composition is rejected without choosing a replacement role or snapshot")
        rejected shouldBe Vector(None, None, None, None)
      }
    }
  }

  "P594-DOC04B-S02-AC03 closed-network snapshot and SAR metadata conditions" should {
    "E3 reject absent, Online, Unavailable snapshots and malformed SAR metadata" must _e3 {
      "reject absent, Online, Unavailable snapshots and malformed SAR metadata" in {
        Given("Spec: docs/spec/closed-network-documentation-hub-sar-profile.md; Rules: DOC04B-S02-AC03; Example: E3; complete roles with an absent, online, unavailable, or hand-constructed invalid Present snapshot, or malformed SAR artifact identity or logical release")
        val constituents = _constituents
        val absent = constituents.updated(
          0,
          DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cncf, _absent_profile)
        )
        val online = constituents.updated(
          0,
          DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cncf, _profile(1, FrameworkPublicationReferenceAvailability.Online))
        )
        val unavailable = constituents.updated(
          0,
          DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cncf, _profile(1, FrameworkPublicationReferenceAvailability.Unavailable))
        )
        val handconstructed = constituents.updated(
          0,
          DocumentationHubSarConstituent(
            DocumentationHubSarConstituentRole.Cncf,
            constituents(0).profile.copy(
              snapshot = FrameworkDocumentationSnapshotProfile.Present(Vector.empty, None, None)
            )
          )
        )

        When("the factory applies Present-only and Local/Installed/Cached snapshot rules before accepting safe SAR descriptive metadata")
        val invalidsnapshots = Vector(absent, online, unavailable, handconstructed).map { candidate =>
          ClosedNetworkDocumentationHubSarProfile.createC("cncf.documentation-hub", "0.1.0", candidate).toOption
        }
        val invalidmetadata = Vector("", "cncf documentation hub", "-cncf").map { artifactid =>
          ClosedNetworkDocumentationHubSarProfile.createC(artifactid, "0.1.0", constituents).toOption
        } ++ Vector("", " 0.1.0", "0.1.0 ", "0.1.0\n").map { release =>
          ClosedNetworkDocumentationHubSarProfile.createC("cncf.documentation-hub", release, constituents).toOption
        }

        Then("no absent, remote/unavailable, or hand-constructed invalid snapshot and no malformed descriptive identity is admitted as a closed-network Hub profile")
        invalidsnapshots shouldBe Vector(None, None, None, None)
        invalidmetadata shouldBe Vector(None, None, None, None, None, None, None)
      }
    }
  }

  "P594-DOC04B-S02-AC04 pure composition input boundary" should {
    "E4 create a Hub solely from caller-supplied SAR metadata and typed S01 snapshot profiles" must _e4 {
      "create a Hub solely from caller-supplied SAR metadata and typed S01 snapshot profiles" in {
        Given("Spec: docs/spec/closed-network-documentation-hub-sar-profile.md; Rules: DOC04B-S02-AC04; Example: E4; only SAR metadata and caller-supplied retained S01 snapshot profiles, with no descriptor, operation, resolver, reader, path, URI, or runtime evidence")
        val constituents = _constituents

        When("the pure factory receives the three declared composition values")
        val profile = ClosedNetworkDocumentationHubSarProfile.createC("cncf.documentation-hub", "0.1.0", constituents).toOption

        Then("it succeeds without a runtime descriptor, operation, resolver, resource reader, network client, or activation input")
        profile should not be empty
        profile.get.constituents shouldBe constituents
      }
    }
  }

  private def _constituents: Vector[DocumentationHubSarConstituent] =
    Vector(
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cncf, _profile(1, FrameworkPublicationReferenceAvailability.Local)),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cml, _profile(2, FrameworkPublicationReferenceAvailability.Installed)),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.Cozy, _profile(3, FrameworkPublicationReferenceAvailability.Cached)),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.TextusBok, _profile(4, FrameworkPublicationReferenceAvailability.Local)),
      DocumentationHubSarConstituent(DocumentationHubSarConstituentRole.ApprovedRetrievalProvider, _profile(5, FrameworkPublicationReferenceAvailability.Installed))
    )

  private def _absent_profile: FrameworkDocumentationProfile = {
    val publication = _publication("absent", None)
    FrameworkDocumentationProfile.createC(publication, None).toOption.get
  }

  private def _profile(
    index: Int,
    availability: FrameworkPublicationReferenceAvailability
  ): FrameworkDocumentationProfile = {
    val componentid = ComponentId(s"org.goldenport.cncf.phase594.HubDocumentation$index")
    val release = s"0.$index.0-SNAPSHOT"
    val snapshot = FrameworkDocumentationComponentSnapshot(componentid, release, _digest, availability)
    val publication = _publication(index.toString, Some(snapshot))
    val documentation = _entry(
      componentid,
      release,
      "FrameworkDocumentation",
      s"urn:cncf:resource:phase594:hub:$index:framework-documentation",
      s"framework/$index/documentation.md",
      ComponentKnowledgeResourceKind.FrameworkDocumentation,
      ComponentKnowledgeResourceRole.FrameworkDocumentation,
      ComponentKnowledgeMediaType.TextMarkdown
    )
    val guideentry = _entry(
      componentid,
      release,
      "Directive",
      s"urn:cncf:resource:phase594:hub:$index:guide",
      s"framework/$index/guide.yaml",
      ComponentKnowledgeResourceKind.Directive,
      ComponentKnowledgeResourceRole.Directive,
      ComponentKnowledgeMediaType.ApplicationYaml
    )
    val catalogentry = _entry(
      componentid,
      release,
      "SkillCatalog",
      s"urn:cncf:resource:phase594:hub:$index:catalog",
      s"framework/$index/catalog.json",
      ComponentKnowledgeResourceKind.SkillCatalog,
      ComponentKnowledgeResourceRole.SkillCatalog,
      ComponentKnowledgeMediaType.ApplicationJson
    )
    val guide = PublicDirectiveProjection(
      guideentry,
      s"hub-public-guide-$index",
      s"hub-public-profile-$index",
      s"hub-public-rule-$index",
      s"urn:cncf:directive:hub:$index",
      "1.0.0",
      PublicDirectiveAuthority.MountedDirectiveRemainsAuthoritative,
      PublicMetadataVisibility.Public,
      _digest,
      PublicDirectiveRedaction.SourceAndRuleContentWithheld,
      s"https://www.simplemodeling.org/framework/$index/guide"
    )
    val catalog = PublicSkillCatalog(
      catalogentry,
      s"hub-public-catalog-$index",
      "cncf",
      "descriptive framework snapshot catalog",
      "explicit user request",
      Vector("framework publication"),
      Vector("metadata visibility"),
      Vector("none"),
      Vector("descriptive only"),
      s"https://www.simplemodeling.org/framework/$index/skills",
      PublicMetadataVisibility.Ecosystem,
      "1.0.0",
      _digest
    )
    val manifest = ComponentKnowledgeManifest(
      componentid,
      release,
      Vector(documentation, guideentry, catalogentry),
      frameworkPublication = Some(publication),
      publicDirective = Some(guide),
      skillCatalog = Some(catalog)
    )
    FrameworkDocumentationProfile.createC(publication, Some(manifest)).toOption.get
  }

  private def _publication(
    suffix: String,
    snapshot: Option[FrameworkDocumentationComponentSnapshot]
  ): FrameworkPublicationContext =
    FrameworkPublicationContext(
      FrameworkProductVersion(_product(suffix), "0.1.0"),
      s"https://www.simplemodeling.org/framework/$suffix",
      "2026-08-26",
      s"https://www.simplemodeling.org/framework/$suffix/documentation",
      Some(s"https://www.simplemodeling.org/framework/$suffix/documentation#overview"),
      _digest,
      FrameworkPublicationReferenceAvailability.Online,
      FrameworkPublicationGeneratedFrom(s"urn:simplemodeling:source:framework:$suffix", _digest),
      snapshot
    )

  private def _product(suffix: String): String =
    suffix match {
      case "1" => "cncf"
      case "2" => "cml"
      case "3" => "cozy"
      case "4" => "textus-bok"
      case "5" => "approved-retrieval-provider"
      case _ => "hub"
    }

  private def _entry(
    componentid: ComponentId,
    release: String,
    childrole: String,
    logicalresource: String,
    logicalpath: String,
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    mediatype: ComponentKnowledgeMediaType
  ): ComponentKnowledgeResourceEntry =
    ComponentKnowledgeResourceEntry(
      ComponentKnowledgeResourceBinding(
        ComponentResourceLogicalIdentity(componentid, release, None, childrole, logicalresource)
      ),
      logicalpath,
      kind,
      role,
      None,
      mediatype,
      42,
      _digest,
      ComponentKnowledgeMetadata(
        ComponentKnowledgeAuthority.Framework,
        ComponentKnowledgeStability.Stable,
        ComponentKnowledgeSource.SuppliedPhase58,
        "Apache-2.0",
        ComponentKnowledgeDisclosure.MetadataOnly
      ),
      ComponentResourceAvailability.Available,
      ComponentResourceIntegrity.Verified,
      ComponentResourceAuthorization.Granted,
      ComponentKnowledgeSafeProvenance(
        ComponentResourceSourceKind.ExpandedCar,
        s"org.example:phase594-hub-documentation:$release",
        "component-registry:hub-documentation",
        "expanded-car:2",
        false,
        _digest
      )
    )
}
