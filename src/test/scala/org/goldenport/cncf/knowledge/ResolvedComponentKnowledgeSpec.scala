package org.goldenport.cncf.knowledge

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceCompositionShape,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity,
  ComponentResourceProvenance,
  ComponentResourceSourceKind,
  ResolvedComponentResource,
  ResolvedComponentResources
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable acceptance specification for P594-DOC04A-S01 resolved-knowledge
 * composition. The fixtures are value-only Phase 58 evidence.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResolvedComponentKnowledgeSpec
    extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e1 = afterWord(
    "in spec:component-development-context, example:E1, rules:DOC04A-S01-AC01, phase:59.4, slice:DOC04-A-S01"
  )
  private val _e2 = afterWord(
    "in spec:component-development-context, example:E2, rules:DOC04A-S01-AC02, phase:59.4, slice:DOC04-A-S01"
  )
  private val _e3 = afterWord(
    "in spec:component-development-context, example:E3, rules:DOC04A-S01-AC03, phase:59.4, slice:DOC04-A-S01"
  )

  private val _component_id = ComponentId("org.goldenport.cncf.phase594.ResolvedKnowledge")
  private val _release = "0.1.0-SNAPSHOT"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _other_digest = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
  private val _availability_states = Vector(
    ComponentResourceAvailability.Available,
    ComponentResourceAvailability.Restricted,
    ComponentResourceAvailability.Unavailable,
    ComponentResourceAvailability.Missing,
    ComponentResourceAvailability.Stale,
    ComponentResourceAvailability.Incompatible,
    ComponentResourceAvailability.Corrupt
  )

  "P594-DOC04A-S01-AC01 resolved knowledge" should {
    "E1 preserve every supplied Phase 58 state and exact resolver evidence" must _e1 {
      "preserve every supplied Phase 58 state and exact resolver evidence" in {
      Given("Spec: docs/spec/component-development-context.md; Rules: DOC04A-S01-AC01; Example: E1; one caller-supplied resource for every Phase 58 availability state, with full physical provenance and false authority flags")
      val supplied = _supplied_resources
      val manifest = _manifest(supplied.resources.reverse)

      When("the read-only composition binds the already supplied manifest and resolver evidence")
      val composed = ResolvedComponentKnowledge.composeC(supplied, manifest).toOption

      Then("each admitted entry has its exact logical identity, full provenance, states, and false authority flags without a second selection")
      composed should not be empty
      val entries = composed.get.entries
      entries.map(_.entry) shouldBe manifest.resources
      entries.map(_.resource.availability) shouldBe _availability_states.reverse
      entries.foreach { paired =>
        val suppliedresource = supplied.resources.find(_.logicalIdentity == paired.entry.binding.logicalIdentity).get
        paired.entry.binding.logicalIdentity shouldBe suppliedresource.logicalIdentity
        paired.resource.logicalIdentity shouldBe suppliedresource.logicalIdentity
        paired.resource.provenance shouldBe suppliedresource.provenance
        paired.resource.availability shouldBe suppliedresource.availability
        paired.resource.integrity shouldBe suppliedresource.integrity
        paired.resource.authorization shouldBe suppliedresource.authorization
        paired.resource.activationAuthority shouldBe false
        paired.resource.operationAuthority shouldBe false
        paired.resource.mcpAuthority shouldBe false
        paired.resource.disclosureAuthority shouldBe false
        paired.resource.deploymentAuthority shouldBe false
      }
      }
    }
  }

  "P594-DOC04A-S01-AC02 manifest binding" should {
    "E2 reject stale or mismatched manifest evidence without a fallback resource" must _e2 {
      "reject stale or mismatched manifest evidence without a fallback resource" in {
      Given("Spec: docs/spec/component-development-context.md; Rules: DOC04A-S01-AC02; Example: E2; a supplied Available resource plus candidate entries that falsely report Stale state or a different digest")
      val supplied = _supplied_resources
      val manifest = _manifest(supplied.resources)
      val first = manifest.resources.head
      val stale = manifest.copy(resources = manifest.resources.updated(0, first.copy(availability = ComponentResourceAvailability.Stale)))
      val mismatched = manifest.copy(resources = manifest.resources.updated(0, first.copy(
        sha256 = _other_digest,
        provenance = first.provenance.copy(matchingDigest = _other_digest)
      )))

      When("composition delegates candidate binding to ComponentKnowledgeManifest.createC before pairing")
      val stalecomposed = ResolvedComponentKnowledge.composeC(supplied, stale).toOption
      val mismatchedcomposed = ResolvedComponentKnowledge.composeC(supplied, mismatched).toOption

      Then("both stale and mismatched claims are rejected rather than selecting another supplied resource")
      stalecomposed shouldBe None
      mismatchedcomposed shouldBe None
      }
    }
  }

  "P594-DOC04A-S01-AC03 provenance boundary" should {
    "E3 keep public safe provenance distinct from retained internal physical resolver evidence" must _e3 {
      "keep public safe provenance distinct from retained internal physical resolver evidence" in {
      Given("Spec: docs/spec/component-development-context.md; Rules: DOC04A-S01-AC03; Example: E3; a composed resource with physical repository and source evidence plus its safe manifest projection")
      val supplied = _supplied_resources
      val manifest = _manifest(supplied.resources)

      When("resolved knowledge retains the original resource while the existing consumer contract projects the manifest")
      val composed = ResolvedComponentKnowledge.composeC(supplied, manifest).toOption.get
      val consumer = ComponentKnowledgeManifestConsumerContract.fromManifestC(manifest).toOption.get
      val internal = composed.entries.head.resource
      val public = consumer.resources.find(_.logicalIdentity == internal.logicalIdentity).get

      Then("the internal value retains physical evidence while the public value exposes only matching safe provenance")
      internal.provenance.repository shouldBe "https://repo.example.invalid/private-phase58"
      internal.provenance.normalizedRelativePath shouldBe "private/phase58/available.md"
      internal.provenance.physicalSource shouldBe "expanded-car:private-phase58-available"
      public.provenance.sourceKind shouldBe internal.provenance.sourceKind
      public.provenance.artifactCoordinate shouldBe internal.provenance.artifactCoordinate
      public.provenance.logicalSource shouldBe internal.provenance.logicalSource
      public.provenance.resolutionStep shouldBe internal.provenance.resolutionStep
      public.provenance.externalDeploymentRequired shouldBe internal.provenance.externalDeploymentRequired
      public.provenance.matchingDigest shouldBe internal.provenance.sha256
      public.provenance.toString should not include internal.provenance.repository
      public.provenance.toString should not include internal.provenance.normalizedRelativePath
      public.provenance.toString should not include internal.provenance.physicalSource
      }
    }
  }

  private def _supplied_resources: ResolvedComponentResources =
    ResolvedComponentResources(
      compositionShape = ComponentResourceCompositionShape.MultiComponent,
      resources = _availability_states.zipWithIndex.map { case (availability, index) =>
        _resource(availability, index + 1)
      },
      diagnostics = Vector.empty
    )

  private def _resource(
    availability: ComponentResourceAvailability,
    index: Int
  ): ResolvedComponentResource = {
    val identity = ComponentResourceLogicalIdentity(
      componentId = _component_id,
      logicalRelease = _release,
      parentComponentId = None,
      childRole = "parent",
      logicalResource = s"urn:cncf:resource:phase594:resolved-knowledge:$index"
    )
    ResolvedComponentResource(
      logicalIdentity = identity,
      provenance = ComponentResourceProvenance(
        sourceKind = ComponentResourceSourceKind.ExpandedCar,
        repository = "https://repo.example.invalid/private-phase58",
        artifactCoordinate = "org.goldenport.cncf:resolved-knowledge:0.1.0",
        sha256 = _digest,
        normalizedRelativePath = s"private/phase58/${availability.toString.toLowerCase}.md",
        logicalSource = s"urn:cncf:source:phase594:resolved-knowledge:$index",
        physicalSource = s"expanded-car:private-phase58-${availability.toString.toLowerCase}",
        resolutionStep = "expanded-car:2",
        childRole = identity.childRole,
        logicalResource = identity.logicalResource,
        access = "restricted-to-resolver",
        license = "Apache-2.0",
        externalDeploymentRequired = false
      ),
      availability = availability,
      integrity = _integrity(availability),
      authorization = _authorization(availability),
      activationAuthority = false,
      operationAuthority = false,
      mcpAuthority = false,
      disclosureAuthority = false,
      deploymentAuthority = false
    )
  }

  private def _manifest(resources: Vector[ResolvedComponentResource]): ComponentKnowledgeManifest =
    ComponentKnowledgeManifest(
      componentId = _component_id,
      logicalRelease = _release,
      resources = resources.zipWithIndex.map { case (resource, index) =>
        ComponentKnowledgeResourceEntry(
          binding = ComponentKnowledgeResourceBinding(resource.logicalIdentity),
          logicalPath = s"knowledge/${index + 1}.md",
          kind = ComponentKnowledgeResourceKind.Documentation,
          role = ComponentKnowledgeResourceRole.Documentation,
          language = Some("en"),
          mediaType = ComponentKnowledgeMediaType.TextMarkdown,
          size = 1L,
          sha256 = resource.provenance.sha256,
          metadata = ComponentKnowledgeMetadata(
            authority = ComponentKnowledgeAuthority.Component,
            stability = ComponentKnowledgeStability.Stable,
            source = ComponentKnowledgeSource.SuppliedPhase58,
            license = resource.provenance.license,
            disclosure = ComponentKnowledgeDisclosure.MetadataOnly
          ),
          availability = resource.availability,
          integrity = resource.integrity,
          authorization = resource.authorization,
          provenance = ComponentKnowledgeSafeProvenance(
            sourceKind = resource.provenance.sourceKind,
            artifactCoordinate = resource.provenance.artifactCoordinate,
            logicalSource = resource.provenance.logicalSource,
            resolutionStep = resource.provenance.resolutionStep,
            externalDeploymentRequired = resource.provenance.externalDeploymentRequired,
            matchingDigest = resource.provenance.sha256
          )
        )
      }
    )

  private def _integrity(availability: ComponentResourceAvailability): ComponentResourceIntegrity =
    availability match {
      case ComponentResourceAvailability.Available => ComponentResourceIntegrity.Verified
      case ComponentResourceAvailability.Restricted => ComponentResourceIntegrity.NotEvaluated
      case ComponentResourceAvailability.Unavailable => ComponentResourceIntegrity.NotEvaluated
      case ComponentResourceAvailability.Missing => ComponentResourceIntegrity.Unverified
      case ComponentResourceAvailability.Stale => ComponentResourceIntegrity.Verified
      case ComponentResourceAvailability.Incompatible => ComponentResourceIntegrity.Unverified
      case ComponentResourceAvailability.Corrupt => ComponentResourceIntegrity.Verified
    }

  private def _authorization(availability: ComponentResourceAvailability): ComponentResourceAuthorization =
    availability match {
      case ComponentResourceAvailability.Available => ComponentResourceAuthorization.Granted
      case ComponentResourceAvailability.Restricted => ComponentResourceAuthorization.Denied
      case ComponentResourceAvailability.Unavailable => ComponentResourceAuthorization.NotEvaluated
      case ComponentResourceAvailability.Missing => ComponentResourceAuthorization.NotEvaluated
      case ComponentResourceAvailability.Stale => ComponentResourceAuthorization.Granted
      case ComponentResourceAvailability.Incompatible => ComponentResourceAuthorization.Denied
      case ComponentResourceAvailability.Corrupt => ComponentResourceAuthorization.Denied
    }
}
