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
 * Executable acceptance specification for DOC-02B-01 framework-publication
 * context and projection evidence.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class FrameworkPublicationContextSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _other_digest = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
  private val _component_id = ComponentId("org.goldenport.cncf.phase59.FrameworkPublicationParent")
  private val _snapshot_id = ComponentId("org.goldenport.cncf.phase59.FrameworkDocumentation")
  private val _release = "0.1.0-SNAPSHOT"

  "DOC02B-01 deterministic framework-publication context codec" should {
    "round-trip recursively safe extensions and retain every reference availability code" in {
      Given("a framework context with direct and recursively nested safe thread fields plus safe unknown fields at context, generated-from, and snapshot levels")
      val context = _context.copy(
        generatedFrom = _context.generatedFrom.copy(
          extensions = Map("futureGenerated" -> Json.obj("z" -> Json.fromInt(3), "a" -> Json.fromInt(1)))
        ),
        documentationComponentSnapshot = Some(_snapshot.copy(extensions = Map("futureSnapshot" -> Json.obj("y" -> Json.fromInt(2), "b" -> Json.fromInt(1))))),
        extensions = Map(
          "futureRoot" -> Json.obj("z" -> Json.fromInt(2), "a" -> Json.arr(Json.obj("d" -> Json.fromInt(4), "c" -> Json.fromInt(3)))),
          "thread" -> Json.obj("nested" -> Json.obj("thread" -> Json.fromString("safe")))
        )
      )
      val contexts = FrameworkPublicationReferenceAvailability.values.toVector.map(availability => context.copy(availability = availability))

      When("the deterministic context codec canonicalizes and decodes each admitted availability")
      val encoded = FrameworkPublicationContextCodec.encode(context)
      val decoded = FrameworkPublicationContextCodec.decodeC(encoded).toOption
      val availabilities = contexts.map(value => FrameworkPublicationContextCodec.decodeC(FrameworkPublicationContextCodec.encode(value)).toOption)

      Then("known fields remain fixed while direct and recursive safe thread extensions round-trip lexically and all availability values survive")
      decoded shouldBe Some(context)
      encoded should include ("\"futureRoot\":{\"a\":[{\"c\":3,\"d\":4}],\"z\":2}")
      encoded should include ("\"thread\":{\"nested\":{\"thread\":\"safe\"}}")
      encoded should include ("\"futureGenerated\":{\"a\":1,\"z\":3}")
      encoded should include ("\"futureSnapshot\":{\"b\":1,\"y\":2}")
      availabilities.flatten.map(_.availability) shouldBe FrameworkPublicationReferenceAvailability.values.toVector
    }

    "retain an absent manifest context byte-for-byte and place a present context before resources" in {
      Given("the same valid manifest both without and with optional framework publication evidence")
      val legacy = _manifest
      val contextual = legacy.copy(frameworkPublication = Some(_context))

      When("the manifest codec encodes and decodes both forms")
      val legacyencoded = ComponentKnowledgeManifestCodec.encode(legacy)
      val contextualencoded = ComponentKnowledgeManifestCodec.encode(contextual)
      val legacydecoded = ComponentKnowledgeManifestCodec.decodeC(legacyencoded).toOption
      val contextualdecoded = ComponentKnowledgeManifestCodec.decodeC(contextualencoded).toOption

      Then("the legacy shape omits the optional field and the context is deterministically rooted before resources")
      legacyencoded should not include "frameworkPublication"
      legacydecoded.map(ComponentKnowledgeManifestCodec.encode) shouldBe Some(legacyencoded)
      contextualencoded should include ("\"logicalRelease\":\"0.1.0-SNAPSHOT\",\"frameworkPublication\":")
      contextualencoded.indexOf("\"frameworkPublication\"") should be < contextualencoded.indexOf("\"resources\"")
      contextualencoded should include ("\"documentationComponentSnapshot\":{")
      contextualdecoded shouldBe Some(contextual)
    }
  }

  "DOC02B-01 caller-supplied projection freshness" should {
    "report current only for exact source identity and digest, and stale for each generated mismatch" in {
      Given("recorded generated-from evidence and sources with an identity or digest difference")
      val current = _context.generatedFrom
      val staleidentity = current.copy(sourceIdentity = "urn:simplemodeling:source:framework:0.1.1")
      val staledigest = current.copy(sourceSha256 = _other_digest)
      val mismatches = Gen.oneOf(staleidentity, staledigest, staleidentity.copy(sourceSha256 = _other_digest))
      val property = Prop.forAll(mismatches) { source =>
        _context.projectionFreshnessFor(source) == FrameworkPublicationProjectionFreshness.Stale
      }

      When("freshness compares only the supplied value with generated-from evidence")
      val exact = _context.projectionFreshnessFor(current)
      val identity = _context.projectionFreshnessFor(staleidentity)
      val digest = _context.projectionFreshnessFor(staledigest)
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("exact caller evidence is current, every mismatch is stale, and no resource or resolver input is involved")
      exact shouldBe FrameworkPublicationProjectionFreshness.Current
      identity shouldBe FrameworkPublicationProjectionFreshness.Stale
      digest shouldBe FrameworkPublicationProjectionFreshness.Stale
      checked.passed shouldBe true
    }
  }

  "DOC02B-01 descriptive snapshot and hostile input boundary" should {
    "accept an optional snapshot as evidence and reject a snapshot digest mismatch without a Phase 58 binding" in {
      Given("a standalone context snapshot containing only Component/release/publication evidence")
      val accepted = _context.copy(documentationComponentSnapshot = Some(_snapshot))
      val rejected = accepted.copy(documentationComponentSnapshot = Some(_snapshot.copy(publicationSha256 = _other_digest)))

      When("context validation evaluates the optional descriptive snapshot")
      val valid = FrameworkPublicationContext.validateC(accepted).toOption
      val invalid = FrameworkPublicationContext.validateC(rejected).toOption

      Then("matching evidence is accepted and mismatch is rejected without a Component resource binding or resolver dependency")
      valid shouldBe Some(accepted)
      invalid shouldBe None
    }

    "reject noncanonical URL, identity, digest, normalized hostile aliases, and duplicate JSON keys" in {
      Given("otherwise valid framework contexts and JSON each containing one malformed or protected value")
      val upperhost = _context.copy(canonicalUrl = "https://WWW.simplemodeling.org/framework/0.1.0")
      val credentialurl = _context.copy(canonicalUrl = "https://user@www.simplemodeling.org/framework/0.1.0")
      val queryurl = _context.copy(canonicalUrl = "https://www.simplemodeling.org/framework/0.1.0?private=true")
      val porturl = _context.copy(canonicalUrl = "https://www.simplemodeling.org:8443/framework/0.1.0")
      val relativeidentity = _context.copy(documentId = "framework/documentation")
      val relativesection = _context.copy(sectionId = Some("documentation#overview"))
      val relativesource = _context.copy(generatedFrom = _context.generatedFrom.copy(sourceIdentity = "source/framework"))
      val baddigest = _context.copy(sha256 = _digest.toUpperCase)
      val bindingalias = _context.copy(extensions = Map("FrameworkResourceBinding" -> Json.fromBoolean(true)))
      val resolveralias = _context.copy(generatedFrom = _context.generatedFrom.copy(extensions = Map("future" -> Json.obj("ReSoLvEr-Input" -> Json.fromString("forbidden")))))
      val scanalias = _context.copy(documentationComponentSnapshot = Some(_snapshot.copy(extensions = Map("scan_roots" -> Json.fromBoolean(true)))))
      val hostilejson = FrameworkPublicationContextCodec.encode(_context).replace("\"generatedFrom\":{", "\"generatedFrom\":{\"future\":{\"readIdentity\":true},")
      val duplicatejson = FrameworkPublicationContextCodec.encode(_context).replace(
        "\"canonicalUrl\":\"https://www.simplemodeling.org/framework/0.1.0\",",
        "\"canonicalUrl\":\"https://www.simplemodeling.org/framework/0.1.0\",\"canonicalUrl\":\"https://www.simplemodeling.org/framework/0.1.0\","
      )

      When("validation and duplicate-key-rejecting decode evaluate each boundary value")
      val invalid = Vector(upperhost, credentialurl, queryurl, porturl, relativeidentity, relativesection, relativesource, baddigest, bindingalias, resolveralias, scanalias).map(FrameworkPublicationContext.validateC(_).toOption)
      val hostile = FrameworkPublicationContextCodec.decodeC(hostilejson).toOption
      val duplicate = FrameworkPublicationContextCodec.decodeC(duplicatejson).toOption

      Then("all malformed values and normalized aliases are rejected through the argument-invalid codec boundary")
      invalid shouldBe Vector.fill(11)(None)
      hostile shouldBe None
      duplicate shouldBe None
    }
  }

  private def _context: FrameworkPublicationContext =
    FrameworkPublicationContext(
      productVersion = FrameworkProductVersion("simplemodeling", "0.1.0"),
      canonicalUrl = "https://www.simplemodeling.org/framework/0.1.0",
      publicationGeneration = "2026-08-24",
      documentId = "https://www.simplemodeling.org/framework/0.1.0/documentation",
      sectionId = Some("https://www.simplemodeling.org/framework/0.1.0/documentation#overview"),
      sha256 = _digest,
      availability = FrameworkPublicationReferenceAvailability.Online,
      generatedFrom = FrameworkPublicationGeneratedFrom(
        sourceIdentity = "urn:simplemodeling:source:framework:0.1.0",
        sourceSha256 = _digest
      ),
      documentationComponentSnapshot = Some(_snapshot)
    )

  private def _snapshot: FrameworkDocumentationComponentSnapshot =
    FrameworkDocumentationComponentSnapshot(
      componentId = _snapshot_id,
      logicalRelease = _release,
      publicationSha256 = _digest,
      availability = FrameworkPublicationReferenceAvailability.Online
    )

  private def _manifest: ComponentKnowledgeManifest =
    ComponentKnowledgeManifest(
      componentId = _component_id,
      logicalRelease = _release,
      resources = Vector(
        ComponentKnowledgeResourceEntry(
          binding = ComponentKnowledgeResourceBinding(
            ComponentResourceLogicalIdentity(
              componentId = _component_id,
              logicalRelease = _release,
              parentComponentId = None,
              childRole = "Documentation",
              logicalResource = "urn:cncf:resource:phase59/framework-publication"
            )
          ),
          logicalPath = "docs/framework-publication.md",
          kind = ComponentKnowledgeResourceKind.Documentation,
          role = ComponentKnowledgeResourceRole.Documentation,
          language = None,
          mediaType = ComponentKnowledgeMediaType.TextMarkdown,
          size = 42,
          sha256 = _digest,
          metadata = ComponentKnowledgeMetadata(
            authority = ComponentKnowledgeAuthority.Component,
            stability = ComponentKnowledgeStability.Stable,
            source = ComponentKnowledgeSource.SuppliedPhase58,
            license = "Apache-2.0",
            disclosure = ComponentKnowledgeDisclosure.MetadataOnly
          ),
          availability = ComponentResourceAvailability.Available,
          integrity = ComponentResourceIntegrity.Verified,
          authorization = ComponentResourceAuthorization.Granted,
          provenance = ComponentKnowledgeSafeProvenance(
            sourceKind = ComponentResourceSourceKind.ExpandedCar,
            artifactCoordinate = "org.example:phase59-framework-publication:0.1.0",
            logicalSource = "component-registry:framework-publication",
            resolutionStep = "expanded-car:2",
            externalDeploymentRequired = false,
            matchingDigest = _digest
          )
        )
      )
    )
}
