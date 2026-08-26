package org.goldenport.cncf.knowledge

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.cncf.component._
import org.goldenport.cncf.component.repository._
import org.goldenport.cncf.projection.ComponentResourceConsumer
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable acceptance specification for P595-DOC05-A Component Help and
 * Direct-AI transport descriptors over caller-supplied resolved knowledge.
 * No HTTP endpoint, CLI parser, resolver, or raw-content projection is used.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeHelpContractSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private type ResourceState = (ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceAuthorization)

  private val _e1 = afterWord(
    "in spec:component-knowledge-help-contract, example:E1, rules:DOC05-A-AC01,DOC05-A-AC06, phase:59.5, slice:DOC05-A-S01"
  )
  private val _e2 = afterWord(
    "in spec:component-knowledge-help-contract, example:E2, rules:DOC05-A-AC02,DOC05-A-AC05, phase:59.5, slice:DOC05-A-S01"
  )
  private val _e1b = afterWord(
    "in spec:component-knowledge-help-contract, example:E1, rules:DOC05-A-AC01, phase:59.5, slice:DOC05-A-S01"
  )
  private val _e3 = afterWord(
    "in spec:component-knowledge-help-contract, example:E3, rules:DOC05-A-AC03,DOC05-A-AC04, phase:59.5, slice:DOC05-A-S02"
  )
  private val _e4 = afterWord(
    "in spec:component-knowledge-help-contract, example:E4, rules:DOC05-A-AC05,DOC05-A-AC07, phase:59.5, slice:DOC05-A-S03"
  )
  private val _e3b = afterWord(
    "in spec:component-knowledge-help-contract, example:E3, rules:DOC05-A-AC04, phase:59.5, slice:DOC05-A-S02"
  )
  private val _e4b = afterWord(
    "in spec:component-knowledge-help-contract, example:E4, rules:DOC05-A-AC04, phase:59.5, slice:DOC05-A-S02"
  )

  private val _component_id = ComponentId("org.goldenport.cncf.phase595.Help")
  private val _release = "0.1.0-SNAPSHOT"
  private val _repository = "https://repo.example.invalid/private-phase58"
  private val _signing_key = "phase595-help-signing-key"
  private val _signature = "phase595-help-signature"

  "P595-DOC05-A-AC01 deterministic operator and Direct-AI discovery" should {
    "E1 expose one describedby manifest link and an identical canonical resource inventory" must _e1 {
      "expose one describedby manifest link and an identical canonical resource inventory" in {
        Given("Spec: docs/spec/component-knowledge-help-contract.md; Rules: DOC05-A-AC01,DOC05-A-AC06; Example: E1; caller-supplied resolved knowledge in noncanonical order with a Ready development outcome")
        val knowledge = _knowledge(states = Vector.fill(3)(_ready_state))
        val contract = _contract(knowledge, _outcome(knowledge, Vector(knowledge.entries.head.resource.logicalIdentity)))

        When("the value-only contract derives Help, Direct-AI, HTTP, and CLI descriptors from the existing manifest consumer contract")
        val human = contract.humanNavigation
        val directai = contract.directAiNavigation

        Then("both navigation channels have the exact same ordered typed resource entries and the discovery link has the canonical relation, media type, release, and route")
        human.resources shouldBe directai.resources
        human.resources.map(_.logicalPath) shouldBe Vector("guides/resource-0.md", "guides/resource-1.md", "guides/resource-2.md")
        human.discovery.relation shouldBe "describedby"
        human.discovery.mediaType shouldBe "application/vnd.cncf.component-knowledge+json;version=1"
        human.manifestIdentity shouldBe ComponentKnowledgeHelpManifestIdentity(_component_id, _release)
        human.discovery.manifestRoute.path shouldBe "/help/org.goldenport.cncf.phase595.Help/knowledge/0.1.0-SNAPSHOT/manifest.json"
        human.resources.foreach { resource =>
          resource.route.path should startWith ("/help/org.goldenport.cncf.phase595.Help/knowledge/0.1.0-SNAPSHOT/resources/")
          resource.route.path should endWith (resource.logicalPath)
          resource.productElementNames.toVector should not contain "physicalSource"
          resource.productElementNames.toVector should not contain "repository"
          resource.productElementNames.toVector should not contain "parentComponentId"
        }
        directai.manifestConsumerContract.resources.map(_.logicalPath) shouldBe human.resources.map(_.logicalPath)
        contract.httpRoutes.discovery shouldBe human.discovery
        contract.httpRoutes.resources shouldBe human.resources.map(_.route)
        contract.cliInspection.command shouldBe Vector("component-knowledge", "inspect", "--component", _component_id.name, "--logical-release", _release)
        contract.cliInspection.manifestPath shouldBe human.discovery.manifestRoute.path
        contract.cliInspection.resourcePaths shouldBe human.resources.map(_.route.path)
        human.developmentContext shouldBe ComponentKnowledgeHelpDevelopmentContextEvidence.Ready
      }
    }

    "E1 retain canonical inventory order when supplied manifest and resolved-pair order is rotated" must _e1b {
      "retain canonical inventory order when supplied manifest and resolved-pair order is rotated" in {
      Given("Spec: docs/spec/component-knowledge-help-contract.md; Rules: DOC05-A-AC01; Example: E1; the same caller-supplied resolved knowledge with generated synchronized rotations")
      val knowledge = _knowledge(states = Vector.fill(4)(_ready_state))
      val rotations = Gen.chooseNum(0, knowledge.entries.size - 1)
      val property = Prop.forAll(rotations) { offset =>
        val pairs = knowledge.entries.drop(offset) ++ knowledge.entries.take(offset)
        val rotated = knowledge.copy(manifest = knowledge.manifest.copy(resources = pairs.map(_.entry)), entries = pairs)
        val outcome = _outcome(rotated, Vector(rotated.entries.head.resource.logicalIdentity))
        val result = ComponentKnowledgeHelpContract.createC(rotated, outcome).toOption
        result.exists(value =>
          value.humanNavigation.resources == value.directAiNavigation.resources &&
            value.humanNavigation.resources.map(_.logicalPath) == Vector(
              "guides/resource-0.md",
              "guides/resource-1.md",
              "guides/resource-2.md",
              "guides/resource-3.md"
            )
        )
      }

      When("ScalaCheck exercises the pure descriptor factory for every generated rotation")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("canonical identity ordering, not caller order, determines the equal Help and Direct-AI resource inventory")
      checked.passed shouldBe true
      }
    }
  }

  "P595-DOC05-A-AC02 state and development-context evidence" should {
    "E2 preserve all Phase 58 availability values and project the existing Ready or Incomplete outcome without a new readiness rule" must _e2 {
      "preserve all Phase 58 availability values and project the existing Ready or Incomplete outcome without a new readiness rule" in {
        Given("Spec: docs/spec/component-knowledge-help-contract.md; Rules: DOC05-A-AC02,DOC05-A-AC05; Example: E2; all seven caller-supplied Phase 58 availability states plus independent integrity and authorization evidence")
        val states = Vector(
          (ComponentResourceAvailability.Available, ComponentResourceIntegrity.Unverified, ComponentResourceAuthorization.Granted),
          (ComponentResourceAvailability.Restricted, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied),
          (ComponentResourceAvailability.Unavailable, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
          (ComponentResourceAvailability.Missing, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
          (ComponentResourceAvailability.Stale, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
          (ComponentResourceAvailability.Incompatible, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
          (ComponentResourceAvailability.Corrupt, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted)
        )
        val knowledge = _knowledge(states = states)
        val required = knowledge.entries.map(_.resource.logicalIdentity)
        val outcome = _outcome(knowledge, required)
        val contract = _contract(knowledge, outcome)

        When("the Help contract projects the consumer manifest and the supplied development-context outcome")
        val resources = contract.humanNavigation.resources
        val development = contract.humanNavigation.developmentContext

        Then("availability, integrity, and authorization remain independent and the exact existing incomplete failure code and issue facts are retained")
        resources.map(resource => (resource.availability, resource.integrity, resource.authorization)) shouldBe states
        development shouldBe a [ComponentKnowledgeHelpDevelopmentContextEvidence.Incomplete]
        val incomplete = development.asInstanceOf[ComponentKnowledgeHelpDevelopmentContextEvidence.Incomplete]
        incomplete.code shouldBe "development-resource-incomplete"
        incomplete.issues.map(issue => (issue.availability, issue.integrity, issue.authorization)) shouldBe states
        contract.directAiNavigation.developmentContext shouldBe development
      }
    }
  }

  "P595-DOC05-A-AC03 exact Direct-AI resource access" should {
    "E3 fail closed for a foreign release, another exact resource, and hostile route values while delegating an admitted request to Phase 58 authorization" must _e3 {
      "fail closed for a foreign release, another exact resource, and hostile route values while delegating an admitted request to Phase 58 authorization" in {
        Given("Spec: docs/spec/component-knowledge-help-contract.md; Rules: DOC05-A-AC03,DOC05-A-AC04; Example: E3; exact caller-supplied v1 and v2 knowledge values, canonical resource routes, and a Phase 58 access request")
        val knowledge = _knowledge(states = Vector.fill(2)(_ready_state))
        val versiontwo = _knowledge(release = "0.2.0-SNAPSHOT", states = Vector.fill(2)(_ready_state))
        val contract = _contract(knowledge, _outcome(knowledge, Vector(knowledge.entries.head.resource.logicalIdentity)))
        val other = _contract(versiontwo, _outcome(versiontwo, Vector(versiontwo.entries.head.resource.logicalIdentity)))
        val target = contract.humanNavigation.resources.head
        val next = contract.humanNavigation.resources(1)
        val resource = knowledge.entries.find(_.entry.logicalPath == target.logicalPath).get.resource
        val request = _request(resource)
        val composition = _composition(resource)

        When("Direct-AI access is requested through the exact route, a second admitted resource, a foreign logical release, or a traversal-like route")
        val granted = contract.accessC(composition, target.route, request).toOption
        val otherresource = knowledge.entries.find(_.entry.logicalPath == next.logicalPath).get.resource
        val wrongresource = contract.accessC(composition, target.route, _request(otherresource)).toOption
        val foreignrelease = contract.accessC(composition, other.humanNavigation.resources.head.route, request).toOption
        val traversal = contract.accessC(composition, target.route.copy(logicalPath = "../private/phase58.bin", path = "/help/other"), request).toOption

        Then("only the exact supplied resource and canonical component/release/logical-path route reaches the existing policy, which records DirectAi and grants no new authority")
        granted.map(_.consumer) shouldBe Some(ComponentResourceConsumer.DirectAi)
        granted.map(_.disposition.toString) shouldBe Some("Granted")
        granted.flatMap(_.content).map(_.toVector) shouldBe Some(_content(resource).toVector)
        wrongresource shouldBe None
        foreignrelease shouldBe None
        traversal shouldBe None
        contract.directAiNavigation.manifestConsumerContract.productElementNames.toVector should not contain "content"
        contract.directAiNavigation.manifestConsumerContract.productElementNames.toVector should not contain "physicalSource"
        contract.directAiNavigation.productElementNames.toVector should not contain "admin"
      }
    }

    "E3 delegate a restricted exact resource to the existing authorization result without exposing raw content" must _e3b {
      "delegate a restricted exact resource to the existing authorization result without exposing raw content" in {
      Given("Spec: docs/spec/component-knowledge-help-contract.md; Rules: DOC05-A-AC04; Example: E3; a caller-supplied exact resource whose authorization evidence is Denied")
      val knowledge = _knowledge(states = Vector((ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied)))
      val contract = _contract(knowledge, _outcome(knowledge, Vector(knowledge.entries.head.resource.logicalIdentity)))
      val route = contract.humanNavigation.resources.head.route
      val resource = knowledge.entries.head.resource

      When("the Direct-AI access gate delegates the exact admitted request")
      val result = contract.accessC(_composition(resource), route, _request(resource)).toOption

      Then("the established structured policy result is Restricted and carries no content")
      result.map(_.consumer) shouldBe Some(ComponentResourceConsumer.DirectAi)
      result.map(_.disposition.toString) shouldBe Some("Restricted")
      result.flatMap(_.content) shouldBe None
      }
    }
  }

  "P595-DOC05-A-AC04 framework developer-toolchain navigation" should {
    "E4 retain supplied framework publication availability and distinguish exact expected version, mismatch, absent snapshot, and online evidence without making Component Help conditional" must _e4 {
      "retain supplied framework publication availability and distinguish exact expected version, mismatch, absent snapshot, and online evidence without making Component Help conditional" in {
        Given("Spec: docs/spec/component-knowledge-help-contract.md; Rules: DOC05-A-AC05,DOC05-A-AC07; Example: E4; an online caller-supplied framework publication with an absent snapshot and exact or mismatched expected product/version evidence")
        val knowledge = _knowledge()
        val outcome = _outcome(knowledge, Vector(knowledge.entries.head.resource.logicalIdentity))
        val profile = FrameworkDocumentationProfile.createC(_framework_publication, None).toOption.get
        val exact = _contract(knowledge, outcome, ComponentKnowledgeHelpFrameworkEvidence(Some(profile), Some(FrameworkProductVersion("simplemodeling", "0.1.0"))))
        val mismatch = _contract(knowledge, outcome, ComponentKnowledgeHelpFrameworkEvidence(Some(profile), Some(FrameworkProductVersion("simplemodeling", "0.2.0"))))
        val absent = _contract(knowledge, outcome)

        When("the Help contract projects optional framework evidence separately from the Component operator inventory")
        val exactnavigation = exact.frameworkDeveloperToolchainNavigation.get
        val mismatchnavigation = mismatch.frameworkDeveloperToolchainNavigation.get

        Then("publication product/version/availability remain caller supplied, version evidence is exact or mismatch, and no snapshot leaves Component Help available")
        exactnavigation.frameworkPublication shouldBe Some(_framework_publication)
        exactnavigation.expectation shouldBe ComponentKnowledgeHelpFrameworkExpectation.Exact(FrameworkProductVersion("simplemodeling", "0.1.0"))
        mismatchnavigation.expectation shouldBe ComponentKnowledgeHelpFrameworkExpectation.Mismatch(FrameworkProductVersion("simplemodeling", "0.2.0"), FrameworkProductVersion("simplemodeling", "0.1.0"))
        exactnavigation.snapshot shouldBe ComponentKnowledgeHelpFrameworkSnapshotNavigation.Absent
        exact.humanNavigation.resources should not be empty
        exact.humanNavigation.resources.map(_.kind) should not contain ComponentKnowledgeResourceKind.FrameworkDocumentation
        absent.frameworkDeveloperToolchainNavigation shouldBe None
      }
    }

    "E4 reject a malformed expected framework product token as a typed validation failure" must _e4b {
      "reject a malformed expected framework product token as a typed validation failure" in {
        Given("Spec: docs/spec/component-knowledge-help-contract.md; Rules: DOC05-A-AC04; Example: E4; valid resolved knowledge and framework publication evidence with an expected product token containing a route separator")
        val knowledge = _knowledge()
        val outcome = _outcome(knowledge, Vector(knowledge.entries.head.resource.logicalIdentity))
        val profile = FrameworkDocumentationProfile.createC(_framework_publication, None).toOption.get
        val evidence = ComponentKnowledgeHelpFrameworkEvidence(Some(profile), Some(FrameworkProductVersion("simple/modeling", "0.1.0")))

        When("ComponentKnowledgeHelpContract.createC validates the supplied framework expected product/version evidence")
        val result = ComponentKnowledgeHelpContract.createC(knowledge, outcome, evidence).toOption

        Then("the malformed product token is rejected as a typed validation failure")
        result shouldBe None
      }
    }
  }

  private val _ready_state: ResourceState = (
    ComponentResourceAvailability.Available,
    ComponentResourceIntegrity.Verified,
    ComponentResourceAuthorization.Granted
  )

  private def _contract(
    knowledge: ResolvedComponentKnowledge,
    outcome: ComponentDevelopmentContextOutcome,
    frameworkevidence: ComponentKnowledgeHelpFrameworkEvidence = ComponentKnowledgeHelpFrameworkEvidence()
  ): ComponentKnowledgeHelpContract =
    ComponentKnowledgeHelpContract.createC(knowledge, outcome, frameworkevidence).toOption.get

  private def _outcome(
    knowledge: ResolvedComponentKnowledge,
    required: Vector[ComponentResourceLogicalIdentity]
  ): ComponentDevelopmentContextOutcome = {
    val categories = ComponentDevelopmentResourceCategory.values.toVector.take(required.size)
    ComponentDevelopmentContext.composeC(
      knowledge,
      ComponentDevelopmentContextCandidate(
        assignments = categories.zip(required).map { case (category, identity) =>
          ComponentDevelopmentResourceAssignment(category, identity)
        },
        requiredLogicalIdentities = required
      )
    ).toOption.get
  }

  private def _knowledge(
    release: String = _release,
    states: Vector[ResourceState] = Vector(_ready_state)
  ): ResolvedComponentKnowledge = {
    val pairs = states.zipWithIndex.map { case (state, index) => _pair(release, index, state) }.reverse
    ResolvedComponentKnowledge(
      manifest = ComponentKnowledgeManifest(_component_id, release, pairs.map(_.entry)),
      entries = pairs
    )
  }

  private def _pair(
    release: String,
    index: Int,
    state: ResourceState
  ): ResolvedComponentKnowledgeEntry = {
    val (availability, integrity, authorization) = state
    val logicalresource = s"urn:cncf:resource:phase595:help:$index"
    val identity = ComponentResourceLogicalIdentity(_component_id, release, None, "parent", logicalresource)
    val content = _content(logicalresource)
    val digest = _sha256(content)
    val resource = ResolvedComponentResource(
      logicalIdentity = identity,
      provenance = ComponentResourceProvenance(
        sourceKind = ComponentResourceSourceKind.ExpandedCar,
        repository = _repository,
        artifactCoordinate = "org.goldenport.cncf:phase595-help:0.1.0",
        sha256 = digest,
        normalizedRelativePath = s"private/phase58/resource-$index.md",
        logicalSource = s"urn:cncf:source:phase595:help:$index",
        physicalSource = s"expanded-car:private-phase58-resource-$index",
        resolutionStep = "expanded-car:2",
        childRole = "parent",
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
      logicalPath = s"guides/resource-$index.md",
      kind = ComponentKnowledgeResourceKind.Documentation,
      role = ComponentKnowledgeResourceRole.Documentation,
      language = Some("en"),
      mediaType = ComponentKnowledgeMediaType.TextMarkdown,
      size = content.length.toLong,
      sha256 = digest,
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
        "org.goldenport.cncf:phase595-help:0.1.0",
        s"urn:cncf:source:phase595:help:$index",
        "expanded-car:2",
        false,
        digest
      )
    )
    ResolvedComponentKnowledgeEntry(entry, resource)
  }

  private def _composition(resource: ResolvedComponentResource): ComponentSubcomponentComposition =
    ComponentSubcomponentComposition(
      parent = ComponentSubcomponentParent(
        componentId = _component_id,
        logicalRelease = resource.logicalIdentity.logicalRelease,
        primaryCar = ComponentSubcomponentCar(
          classification = "primary",
          artifact = ComponentSubcomponentArtifact(
            coordinate = resource.provenance.artifactCoordinate,
            sha256 = resource.provenance.sha256,
            signature = _signature,
            repository = resource.provenance.repository,
            physicalPath = "private/phase58/help.car"
          ),
          provenance = ComponentSubcomponentProvenance(
            logicalSource = "urn:cncf:source:phase595:help",
            physicalSource = "expanded-car:private-phase58-help",
            physicalPath = "private/phase58/help.car"
          )
        )
      ),
      members = Vector.empty
    )

  private def _request(resource: ResolvedComponentResource): ComponentResourceAccessRequest =
    ComponentResourceAccessRequest(
      resource = resource,
      content = _content(resource),
      signature = _signature,
      signingKeyId = _signing_key,
      grantedRoles = Set("parent"),
      admittedRepositories = Set(_repository),
      trustedSignaturesByKeyId = Map(_signing_key -> Set(_signature)),
      archiveEntries = Vector(ComponentResourceArchiveEntry(resource.provenance.normalizedRelativePath)),
      managedCacheVerified = true
    )

  private def _content(resource: ResolvedComponentResource): Array[Byte] =
    _content(resource.logicalIdentity.logicalResource)

  private def _content(logicalresource: String): Array[Byte] =
    s"phase595 private resource $logicalresource".getBytes(StandardCharsets.UTF_8)

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _framework_publication: FrameworkPublicationContext =
    FrameworkPublicationContext(
      productVersion = FrameworkProductVersion("simplemodeling", "0.1.0"),
      canonicalUrl = "https://www.simplemodeling.org/framework/0.1.0",
      publicationGeneration = "2026-08-26",
      documentId = "https://www.simplemodeling.org/framework/0.1.0/documentation",
      sectionId = None,
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      availability = FrameworkPublicationReferenceAvailability.Online,
      generatedFrom = FrameworkPublicationGeneratedFrom(
        "urn:simplemodeling:source:framework:0.1.0",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      )
    )
}
