package org.goldenport.cncf.knowledge

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity,
  ComponentResourceProvenance,
  ComponentResourceSourceKind,
  ResolvedComponentResource
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable acceptance specification for P594-DOC04A-S02 typed
 * development-context composition. Fixtures are value-only S01/Phase 58
 * evidence; this specification does not invoke a resolver or read content.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentDevelopmentContextSpec
    extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e1 = afterWord(
    "in spec:component-development-context, example:E1, rules:DOC04A-S02-AC01, phase:59.4, slice:DOC04-A-S02"
  )
  private val _e2 = afterWord(
    "in spec:component-development-context, example:E2, rules:DOC04A-S02-AC02, phase:59.4, slice:DOC04-A-S02"
  )
  private val _e3 = afterWord(
    "in spec:component-development-context, example:E3, rules:DOC04A-S02-AC03, phase:59.4, slice:DOC04-A-S02"
  )
  private val _e4 = afterWord(
    "in spec:component-development-context, example:E4, rules:DOC04A-S02-AC04, phase:59.4, slice:DOC04-A-S02"
  )

  import ComponentDevelopmentContextOutcome.{Incomplete, Ready}

  private val _component_id = ComponentId("org.goldenport.cncf.phase594.DevelopmentContext")
  private val _release = "0.1.0-SNAPSHOT"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _categories = ComponentDevelopmentResourceCategory.values.toVector

  "P594-DOC04A-S02-AC01 ready typed context" should {
    "E1 retain exact ready evidence in every declared development category" must _e1 {
      "retain exact ready evidence in every declared development category" in {
      Given("Spec: docs/spec/component-development-context.md; Rules: DOC04A-S02-AC01; Example: E1; value-only S01 knowledge containing one Available, Verified, and Granted resource for each of the eleven categories")
      val knowledge = _knowledge()
      val candidate = _candidate(required = knowledge.entries.map(_.resource.logicalIdentity))

      When("the typed development context composes the exact admitted category and identity assignments")
      val outcome = ComponentDevelopmentContext.composeC(knowledge, candidate).toOption

      Then("the outcome is Ready with deterministic classifications that retain every exact S01 and Phase 58 pair")
      outcome should not be empty
      outcome.get shouldBe a [Ready]
      val context = outcome.get.asInstanceOf[Ready].context
      context.classifications.map(_.category) shouldBe _categories
      context.classifications.map(_.pair) shouldBe knowledge.entries
      context.requiredLogicalIdentities shouldBe knowledge.entries.map(_.resource.logicalIdentity).sortBy(_.logicalResource)
      context.classifications.foreach { classification =>
        classification.pair.resource.availability shouldBe ComponentResourceAvailability.Available
        classification.pair.resource.integrity shouldBe ComponentResourceIntegrity.Verified
        classification.pair.resource.authorization shouldBe ComponentResourceAuthorization.Granted
      }
      }
    }
  }

  "P594-DOC04A-S02-AC02 incomplete required evidence" should {
    "E2 return one exact typed issue for every unavailable, unverified, or unauthorized required resource" must _e2 {
      "return one exact typed issue for every unavailable, unverified, or unauthorized required resource" in {
      Given("Spec: docs/spec/component-development-context.md; Rules: DOC04A-S02-AC02; Example: E2; required value-only S01 pairs representing all six non-ready availability states plus independent integrity and authorization deficiencies")
      val states = Map(
        ComponentDevelopmentResourceCategory.Manual -> (ComponentResourceAvailability.Restricted, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        ComponentDevelopmentResourceCategory.Model -> (ComponentResourceAvailability.Unavailable, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        ComponentDevelopmentResourceCategory.API -> (ComponentResourceAvailability.Missing, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        ComponentDevelopmentResourceCategory.Configuration -> (ComponentResourceAvailability.Stale, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        ComponentDevelopmentResourceCategory.Example -> (ComponentResourceAvailability.Incompatible, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        ComponentDevelopmentResourceCategory.Source -> (ComponentResourceAvailability.Corrupt, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        ComponentDevelopmentResourceCategory.GeneratedSource -> (ComponentResourceAvailability.Available, ComponentResourceIntegrity.Unverified, ComponentResourceAuthorization.Granted),
        ComponentDevelopmentResourceCategory.Scaladoc -> (ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Denied)
      )
      val knowledge = _knowledge(states)
      val required = states.keys.toVector.map(category => _identity(category))
      val candidate = _candidate(required)

      When("the context evaluates only the preserved readiness facts of each required exact pair")
      val outcome = ComponentDevelopmentContext.composeC(knowledge, candidate).toOption

      Then("a successful structured Incomplete outcome exposes the stable code and exact unavailable evidence without state rewriting")
      outcome should not be empty
      outcome.get shouldBe a [Incomplete]
      val failure = outcome.get.asInstanceOf[Incomplete].failure
      failure.code shouldBe "development-resource-incomplete"
      failure.issues.map(_.logicalIdentity) shouldBe required.sortBy(_.logicalResource)
      failure.issues.foreach { issue =>
        val supplied = knowledge.entries.find(_.resource.logicalIdentity == issue.logicalIdentity).get
        issue.pair shouldBe supplied
        issue.availability shouldBe supplied.resource.availability
        issue.integrity shouldBe supplied.resource.integrity
        issue.authorization shouldBe supplied.resource.authorization
      }
      }
    }
  }

  "P594-DOC04A-S02-AC03 optional incomplete evidence" should {
    "E3 retain optional non-ready evidence while allowing the required view to be Ready" must _e3 {
      "retain optional non-ready evidence while allowing the required view to be Ready" in {
      Given("Spec: docs/spec/component-development-context.md; Rules: DOC04A-S02-AC03; Example: E3; one optional Missing, Unverified, and Denied value-only S01 pair alongside ready required category assignments")
      val optional = ComponentDevelopmentResourceCategory.DependencyDocumentation
      val knowledge = _knowledge(Map(optional -> (ComponentResourceAvailability.Missing, ComponentResourceIntegrity.Unverified, ComponentResourceAuthorization.Denied)))
      val required = knowledge.entries.map(_.resource.logicalIdentity).filterNot(_ == _identity(optional))
      val candidate = _candidate(required)

      When("the context composes all classifications but evaluates readiness only for required logical identities")
      val outcome = ComponentDevelopmentContext.composeC(knowledge, candidate).toOption

      Then("the outcome is Ready and the optional exact resource evidence remains visible without falsely blocking development")
      outcome should not be empty
      outcome.get shouldBe a [Ready]
      val context = outcome.get.asInstanceOf[Ready].context
      val optionalclassification = context.classifications.find(_.category == optional).get
      optionalclassification.pair shouldBe knowledge.entries.find(_.resource.logicalIdentity == _identity(optional)).get
      optionalclassification.pair.resource.availability shouldBe ComponentResourceAvailability.Missing
      optionalclassification.pair.resource.integrity shouldBe ComponentResourceIntegrity.Unverified
      optionalclassification.pair.resource.authorization shouldBe ComponentResourceAuthorization.Denied
      }
    }
  }

  "P594-DOC04A-S02-AC04 invalid candidate references" should {
    "E4 reject duplicate, unknown, and unassigned required references through Consequence without a fallback" must _e4 {
      "reject duplicate, unknown, and unassigned required references through Consequence without a fallback" in {
      Given("Spec: docs/spec/component-development-context.md; Rules: DOC04A-S02-AC04; Example: E4; a value-only admitted S01 knowledge set and candidates with a duplicate category pair, duplicate requirement, unknown identity, or required identity lacking an assignment")
      val knowledge = _knowledge()
      val valid = _candidate(knowledge.entries.map(_.resource.logicalIdentity))
      val first = valid.assignments.head
      val unknown = first.logicalIdentity.copy(logicalResource = "urn:cncf:resource:phase594:development-context:unknown")
      val duplicateassignment = valid.copy(assignments = valid.assignments :+ first)
      val duplicaterequired = valid.copy(requiredLogicalIdentities = valid.requiredLogicalIdentities :+ first.logicalIdentity)
      val unknownassignment = valid.copy(assignments = valid.assignments.updated(0, first.copy(logicalIdentity = unknown)))
      val unknownrequired = valid.copy(requiredLogicalIdentities = valid.requiredLogicalIdentities.updated(0, unknown))
      val unassignedrequired = valid.copy(assignments = valid.assignments.tail)

      When("the value-only factory validates every candidate against only the supplied S01 logical identities")
      val outcomes = Vector(duplicateassignment, duplicaterequired, unknownassignment, unknownrequired, unassignedrequired).map(
        ComponentDevelopmentContext.composeC(knowledge, _).toOption
      )

      Then("each malformed candidate is rejected through Consequence and no alternate resource, source, or fallback is selected")
      outcomes shouldBe Vector.fill(5)(None)
      }
    }
  }

  private def _candidate(required: Vector[ComponentResourceLogicalIdentity]): ComponentDevelopmentContextCandidate =
    ComponentDevelopmentContextCandidate(
      assignments = _categories.map(category => ComponentDevelopmentResourceAssignment(category, _identity(category))).reverse,
      requiredLogicalIdentities = required.reverse
    )

  private def _knowledge(
    overrides: Map[ComponentDevelopmentResourceCategory, (ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceAuthorization)] = Map.empty
  ): ResolvedComponentKnowledge = {
    val entries = _categories.map { category =>
      val (availability, integrity, authorization) = overrides.getOrElse(
        category,
        (ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted)
      )
      _entry(category, availability, integrity, authorization)
    }
    ResolvedComponentKnowledge(
      manifest = ComponentKnowledgeManifest(_component_id, _release, entries.map(_.entry)),
      entries = entries
    )
  }

  private def _entry(
    category: ComponentDevelopmentResourceCategory,
    availability: ComponentResourceAvailability,
    integrity: ComponentResourceIntegrity,
    authorization: ComponentResourceAuthorization
  ): ResolvedComponentKnowledgeEntry = {
    val identity = _identity(category)
    val resource = ResolvedComponentResource(
      logicalIdentity = identity,
      provenance = ComponentResourceProvenance(
        sourceKind = ComponentResourceSourceKind.ExpandedCar,
        repository = "https://repo.example.invalid/private-phase58",
        artifactCoordinate = "org.goldenport.cncf:development-context:0.1.0",
        sha256 = _digest,
        normalizedRelativePath = s"private/phase58/${category.toString.toLowerCase}.md",
        logicalSource = s"urn:cncf:source:phase594:development-context:${category.toString.toLowerCase}",
        physicalSource = s"expanded-car:private-phase58-${category.toString.toLowerCase}",
        resolutionStep = "expanded-car:2",
        childRole = identity.childRole,
        logicalResource = identity.logicalResource,
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
      logicalPath = s"knowledge/${category.toString.toLowerCase}.md",
      kind = ComponentKnowledgeResourceKind.Documentation,
      role = ComponentKnowledgeResourceRole.Documentation,
      language = Some("en"),
      mediaType = ComponentKnowledgeMediaType.TextMarkdown,
      size = 1L,
      sha256 = _digest,
      metadata = ComponentKnowledgeMetadata(
        authority = ComponentKnowledgeAuthority.Component,
        stability = ComponentKnowledgeStability.Stable,
        source = ComponentKnowledgeSource.SuppliedPhase58,
        license = resource.provenance.license,
        disclosure = ComponentKnowledgeDisclosure.MetadataOnly
      ),
      availability = availability,
      integrity = integrity,
      authorization = authorization,
      provenance = ComponentKnowledgeSafeProvenance(
        sourceKind = resource.provenance.sourceKind,
        artifactCoordinate = resource.provenance.artifactCoordinate,
        logicalSource = resource.provenance.logicalSource,
        resolutionStep = resource.provenance.resolutionStep,
        externalDeploymentRequired = resource.provenance.externalDeploymentRequired,
        matchingDigest = resource.provenance.sha256
      )
    )
    ResolvedComponentKnowledgeEntry(entry, resource)
  }

  private def _identity(category: ComponentDevelopmentResourceCategory): ComponentResourceLogicalIdentity =
    ComponentResourceLogicalIdentity(
      componentId = _component_id,
      logicalRelease = _release,
      parentComponentId = None,
      childRole = "parent",
      logicalResource = s"urn:cncf:resource:phase594:development-context:${category.toString.toLowerCase}"
    )
}
