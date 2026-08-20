package org.goldenport.cncf.component.repository

import org.goldenport.Consequence
import org.goldenport.cncf.component._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Failing-first executable acceptance specification for
 * RSC04-RESOLUTION-PROVENANCE (Phase 58.3 / RSC-04 / RSC-04A).
 *
 * The resolver vocabulary exercised here is successor-owned inside the
 * frozen RSC-01 invariants.  This specification intentionally precedes the
 * production resolver and therefore remains RED until that vocabulary exists.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResolvedComponentResourcesSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _namespace = "org.goldenport.cncf.phase58"
  private val _release = "0.1.0-SNAPSHOT"
  private val _parent_id = ComponentId(s"$_namespace.RscParent")
  private val _documentation_id = ComponentId(s"$_namespace.RscDocumentation")
  private val _source_id = ComponentId(s"$_namespace.RscSourceCode")
  private val _external_platform_id = ComponentId(s"$_namespace.RscWebPresentation")
  private val _component_ids = Vector(_parent_id, _documentation_id, _source_id, _external_platform_id)
  private val _sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _signature = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
  private val _repository = "https://repo.example.invalid/cncf/rsc"
  private val _source_order = Vector(
    ComponentResourceSourceKind.EmbeddedPrimary,
    ComponentResourceSourceKind.DevelopmentDirectory,
    ComponentResourceSourceKind.ExpandedCar,
    ComponentResourceSourceKind.LocalRepository,
    ComponentResourceSourceKind.ManagedCache,
    ComponentResourceSourceKind.OfflineBundle,
    ComponentResourceSourceKind.RemoteRepository
  )
  private val _terminal_availability = Vector(
    ComponentResourceAvailability.Restricted,
    ComponentResourceAvailability.Stale,
    ComponentResourceAvailability.Incompatible,
    ComponentResourceAvailability.Corrupt
  )

  "RSC04-AC-01 embedded primary plus each independently identified child" should {
    "which source matrix resolves every shared Component through all seven source kinds" in {
      Given("the frozen phase58 parent, Documentation, SourceCode, and external-platform memberships")
      val composition = _composition(_members)
      val evidence = _evidence(_source_order.flatMap(sourcekind => _component_ids.map(_entry(_, sourcekind))))

      When("resolution is requested from the complete source matrix")
      val resolved = _resolved(ResolvedComponentResources.resolveC(composition, evidence))

      Then("one resource-evidence entry remains for the primary and every independently identified child")
      resolved.resources.map(_.logicalIdentity.componentId) should contain allElementsOf _component_ids
      resolved.resources should have size _component_ids.size
      resolved.resources.foreach { entry =>
        entry.logicalIdentity.logicalRelease shouldBe _release
        entry.activationAuthority shouldBe false
        entry.operationAuthority shouldBe false
        entry.mcpAuthority shouldBe false
        entry.disclosureAuthority shouldBe false
        entry.deploymentAuthority shouldBe false
      }
    }

    "which deterministic precedence is independent of evidence permutation" in {
      Given("the same logical resources with one available candidate at each registered source kind")
      val composition = _composition(_members)
      val entries = _source_order.flatMap(sourcekind => _component_ids.map(_entry(_, sourcekind)))
      val forward = _evidence(entries)
      val permuted = _evidence(entries.reverse)
      val expected = _component_ids.map(_ -> ComponentResourceSourceKind.EmbeddedPrimary).toMap

      When("the resolver receives the candidates in forward and reverse permutations")
      val forwardresolved = _resolved(ResolvedComponentResources.resolveC(composition, forward))
      val permutedresolved = _resolved(ResolvedComponentResources.resolveC(composition, permuted))

      Then("the selected source for every logical identity follows the frozen precedence order")
      _source_order.head shouldBe ComponentResourceSourceKind.EmbeddedPrimary
      forwardresolved.resources.map(entry => entry.logicalIdentity.componentId -> entry.provenance.sourceKind).toMap shouldBe
        permutedresolved.resources.map(entry => entry.logicalIdentity.componentId -> entry.provenance.sourceKind).toMap
      forwardresolved.resources.foreach(_.provenance.sourceKind shouldBe ComponentResourceSourceKind.EmbeddedPrimary)
      val property = Prop.forAll(Gen.choose(1, entries.size - 1)) { rotation =>
        val rotated = _evidence(entries.drop(rotation) ++ entries.take(rotation))
        _resolved(ResolvedComponentResources.resolveC(composition, rotated)).resources.map(entry => entry.logicalIdentity.componentId -> entry.provenance.sourceKind).toMap == expected
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)
      checked.passed shouldBe true
    }

    "which selects each registered source kind when that kind is isolated" in {
      Given("one complete composition-evidence set for each source kind without higher-precedence alternatives")
      val composition = _composition(_members)

      When("the resolver receives each source kind in isolation")
      val selected = _source_order.map { sourcekind =>
        val evidence = _evidence(_component_ids.map(_entry(_, sourcekind)))
        sourcekind -> _resolved(ResolvedComponentResources.resolveC(composition, evidence))
      }

      Then("every isolated source kind is retained as the selected provenance kind")
      selected.foreach { case (sourcekind, resolved) =>
        resolved.resources.map(_.provenance.sourceKind).distinct shouldBe Vector(sourcekind)
      }
    }

    "which resolves same-precedence evidence deterministically and reports its conflict" in {
      Given("two expanded-CAR candidates for one child whose repository provenance orders deterministically")
      val composition = _composition(_members.take(1))
      val original = _entry(_documentation_id, ComponentResourceSourceKind.ExpandedCar)
      val first = original.copy(provenance = original.provenance.copy(repository = "https://a.example.invalid/cncf/rsc"))
      val second = original.copy(provenance = original.provenance.copy(repository = "https://z.example.invalid/cncf/rsc"))
      val forward = _evidence(Vector(_entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary), second, first))
      val reverse = _evidence(Vector(_entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary), first, second))

      When("the resolver receives the equally-precedent candidates in both orders")
      val forwardresolved = _resolved(ResolvedComponentResources.resolveC(composition, forward))
      val reverseresolved = _resolved(ResolvedComponentResources.resolveC(composition, reverse))

      Then("the same canonical candidate wins and a source-specific conflict diagnostic is retained")
      forwardresolved.resources.find(_.logicalIdentity.componentId == _documentation_id).map(_.provenance.repository) shouldBe Some("https://a.example.invalid/cncf/rsc")
      reverseresolved.resources.find(_.logicalIdentity.componentId == _documentation_id).map(_.provenance.repository) shouldBe Some("https://a.example.invalid/cncf/rsc")
      forwardresolved.diagnostics.filter(_.kind == ComponentResourceDiagnosticKind.Conflict).map(_.sourceKind) should contain only Some(ComponentResourceSourceKind.ExpandedCar)
      reverseresolved.diagnostics.filter(_.kind == ComponentResourceDiagnosticKind.Conflict).map(_.sourceKind) should contain only Some(ComponentResourceSourceKind.ExpandedCar)
    }

    "which logical and physical provenance is retained on selected child evidence" in {
      Given("a parent member with distinct logical identity and physical source evidence")
      val composition = _composition(_members)
      val evidence = _evidence(Vector(_entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary), _entry(_documentation_id, ComponentResourceSourceKind.ExpandedCar)))

      When("the Documentation child is selected from its expanded CAR evidence")
      val resolved = _resolved(ResolvedComponentResources.resolveC(composition, evidence))
      val documentation = resolved.resources.find(_.logicalIdentity.componentId == _documentation_id).get

      Then("logical identity, role, resource, access, license, and physical provenance remain distinguishable")
      documentation.logicalIdentity.componentId shouldBe _documentation_id
      documentation.logicalIdentity.logicalRelease shouldBe _release
      documentation.logicalIdentity.parentComponentId shouldBe Some(_parent_id)
      documentation.provenance.sourceKind shouldBe ComponentResourceSourceKind.ExpandedCar
      documentation.provenance.repository shouldBe _repository
      documentation.provenance.artifactCoordinate shouldBe "org.example:rsc-documentation-car:0.1.0"
      documentation.provenance.sha256 shouldBe _sha256
      documentation.provenance.normalizedRelativePath shouldBe "expanded/rsc-documentation/web/index.md"
      documentation.provenance.logicalSource shouldBe "composition-registry:rsc-documentation"
      documentation.provenance.physicalSource shouldBe "expanded-car:rsc-documentation"
      documentation.provenance.resolutionStep shouldBe "expanded-car:2"
      documentation.provenance.childRole shouldBe "Documentation"
      documentation.provenance.logicalResource shouldBe "urn:cncf:resource:phase58/documentation-guide"
      documentation.provenance.access shouldBe "described"
      documentation.provenance.license shouldBe "Apache-2.0"
    }
  }

  "RSC04-AC-02 availability, integrity, and authorization outcomes" should {
    "which missing and unavailable candidates permit lower-precedence lookup" in {
      Given("a primary candidate that is missing, an unavailable development candidate, and an available expanded candidate")
      val composition = _composition(_members.take(1))
      val evidence = _evidence(Vector(
        _entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary),
        _entry(_documentation_id, ComponentResourceSourceKind.EmbeddedPrimary, ComponentResourceAvailability.Missing),
        _entry(_documentation_id, ComponentResourceSourceKind.DevelopmentDirectory, ComponentResourceAvailability.Unavailable),
        _entry(_documentation_id, ComponentResourceSourceKind.ExpandedCar)
      ))

      When("the resolver searches the lower-precedence candidates")
      val documentation = _resolved(ResolvedComponentResources.resolveC(composition, evidence)).resources.find(_.logicalIdentity.componentId == _documentation_id).get

      Then("the available expanded evidence is selected after missing and unavailable outcomes")
      documentation.provenance.sourceKind shouldBe ComponentResourceSourceKind.ExpandedCar
      documentation.availability shouldBe ComponentResourceAvailability.Available
    }

    "which unavailable evidence remains visible when no candidate qualifies" in {
      Given("a missing embedded candidate and an unavailable development candidate without any available fallback")
      val composition = _composition(_members.take(1))
      val evidence = _evidence(Vector(
        _entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary),
        _entry(_documentation_id, ComponentResourceSourceKind.EmbeddedPrimary, ComponentResourceAvailability.Missing),
        _entry(_documentation_id, ComponentResourceSourceKind.DevelopmentDirectory, ComponentResourceAvailability.Unavailable)
      ))

      When("the resolver exhausts the candidates that can qualify")
      val resolved = _resolved(ResolvedComponentResources.resolveC(composition, evidence))
      val documentation = resolved.resources.find(_.logicalIdentity.componentId == _documentation_id).get

      Then("the unavailable outcome and its diagnostic are retained instead of synthesizing Missing")
      documentation.availability shouldBe ComponentResourceAvailability.Unavailable
      documentation.provenance.sourceKind shouldBe ComponentResourceSourceKind.DevelopmentDirectory
      resolved.diagnostics.map(_.kind) should contain (ComponentResourceDiagnosticKind.Unavailable)
      resolved.diagnostics.filter(_.kind == ComponentResourceDiagnosticKind.Unavailable).map(_.sourceKind) should contain only Some(ComponentResourceSourceKind.DevelopmentDirectory)
    }

    "which restricted stale incompatible and corrupt outcomes are terminal" in {
      Given("one terminal higher-precedence outcome for each registered terminal availability")
      val composition = _composition(_members.take(1))
      val selected = _terminal_availability.map { availability =>
        val evidence = _evidence(Vector(
          _entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary),
          _entry(_documentation_id, ComponentResourceSourceKind.EmbeddedPrimary, availability),
          _entry(_documentation_id, ComponentResourceSourceKind.DevelopmentDirectory, ComponentResourceAvailability.Available)
        ))

        When(s"the resolver receives a $availability candidate")
        val documentation = _resolved(ResolvedComponentResources.resolveC(composition, evidence)).resources.find(_.logicalIdentity.componentId == _documentation_id).get
        availability -> documentation
      }

      Then("no terminal outcome silently falls through to the lower-precedence available candidate")
      selected.foreach { case (availability, documentation) =>
        documentation.availability shouldBe availability
        documentation.provenance.sourceKind shouldBe ComponentResourceSourceKind.EmbeddedPrimary
      }
    }

    "which a terminal candidate wins over Available evidence from the same source" in {
      Given("an available and a stale candidate for one child from the same expanded-CAR source")
      val composition = _composition(_members.take(1))
      val evidence = _evidence(Vector(
        _entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary),
        _entry(_documentation_id, ComponentResourceSourceKind.ExpandedCar),
        _entry(_documentation_id, ComponentResourceSourceKind.ExpandedCar, ComponentResourceAvailability.Stale)
      ))

      When("the resolver selects evidence from the shared source")
      val resolved = _resolved(ResolvedComponentResources.resolveC(composition, evidence))
      val documentation = resolved.resources.find(_.logicalIdentity.componentId == _documentation_id).get

      Then("the terminal evidence is selected and retained as a Terminal diagnostic")
      documentation.availability shouldBe ComponentResourceAvailability.Stale
      documentation.provenance.sourceKind shouldBe ComponentResourceSourceKind.ExpandedCar
      resolved.diagnostics.filter(_.kind == ComponentResourceDiagnosticKind.Terminal).map(_.sourceKind) should contain only Some(ComponentResourceSourceKind.ExpandedCar)
    }

    "which orthogonal outcomes remain distinct in selected evidence" in {
      Given("an available resource with unverified integrity and denied authorization")
      val composition = _composition(_members.take(1))
      val evidence = _evidence(Vector(
        _entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary),
        _entry(
          _documentation_id,
          ComponentResourceSourceKind.ExpandedCar,
          availability = ComponentResourceAvailability.Available,
          integrity = ComponentResourceIntegrity.Unverified,
          authorization = ComponentResourceAuthorization.Denied
        )
      ))

      When("the resolver selects the evidence without applying an authorization or integrity policy")
      val documentation = _resolved(ResolvedComponentResources.resolveC(composition, evidence)).resources.find(_.logicalIdentity.componentId == _documentation_id).get

      Then("availability, integrity, and authorization retain their independent values")
      documentation.availability shouldBe ComponentResourceAvailability.Available
      documentation.integrity shouldBe ComponentResourceIntegrity.Unverified
      documentation.authorization shouldBe ComponentResourceAuthorization.Denied
    }
  }

  "RSC04-AC-03 discovery and activation boundary" should {
    "which primary-only composition reports one Component without activation" in {
      Given("a direct primary-only composition and embedded primary evidence")
      val composition = _composition(Vector.empty)
      val evidence = _evidence(Vector(_entry(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary)))

      When("discovery resolves the primary-only composition")
      val resolved = _resolved(ResolvedComponentResources.resolveC(composition, evidence))

      Then("the result is SingleComponent evidence and does not carry activation or deployment authority")
      resolved.compositionShape shouldBe ComponentResourceCompositionShape.SingleComponent
      resolved.diagnostics.map(_.kind) should contain (ComponentResourceDiagnosticKind.SingleComponent)
      resolved.resources should have size 1
      val primary = resolved.resources.head
      primary.activationAuthority shouldBe false
      primary.operationAuthority shouldBe false
      primary.mcpAuthority shouldBe false
      primary.disclosureAuthority shouldBe false
      primary.deploymentAuthority shouldBe false
    }

    "which parent membership reports MultiComponent diagnostics without activating children" in {
      Given("a parent composition with Documentation, SourceCode, and external-platform members")
      val composition = _composition(_members)
      val evidence = _evidence(_component_ids.map(_entry(_, ComponentResourceSourceKind.EmbeddedPrimary)))

      When("discovery resolves the complete parent composition")
      val resolved = _resolved(ResolvedComponentResources.resolveC(composition, evidence))

      Then("the result is MultiComponent evidence and external deployment remains explicit")
      resolved.compositionShape shouldBe ComponentResourceCompositionShape.MultiComponent
      resolved.diagnostics.map(_.kind) should contain (ComponentResourceDiagnosticKind.MultiComponent)
      resolved.resources should have size 4
      resolved.resources.filter(_.logicalIdentity.parentComponentId.nonEmpty) should have size 3
      resolved.resources.foreach { entry =>
        entry.activationAuthority shouldBe false
        entry.operationAuthority shouldBe false
        entry.mcpAuthority shouldBe false
        entry.disclosureAuthority shouldBe false
        entry.deploymentAuthority shouldBe false
      }
      resolved.resources.find(_.logicalIdentity.componentId == _external_platform_id).get.provenance.externalDeploymentRequired shouldBe true
    }
  }

  "RSC04 direct composition integrity" should {
    "which rejects a duplicate child role supplied without codec decoding" in {
      Given("a direct composition whose two distinct child identities claim the Documentation role")
      val composition = _composition(Vector(
        _member(_documentation_id, "Documentation", "parent-documentation-source", "documentation-guide"),
        _member(_source_id, "Documentation", "parent-documentation-source", "source-code")
      ))

      When("the resolver receives the public composition value")
      val result = ResolvedComponentResources.resolveC(composition, _evidence(Vector.empty))

      Then("the duplicate child role is rejected before evidence resolution")
      result.toOption shouldBe None
    }

    "which rejects a duplicate logical resource supplied without codec decoding" in {
      Given("a direct composition whose two distinct child identities claim one logical resource")
      val composition = _composition(Vector(
        _member(_documentation_id, "Documentation", "parent-documentation-source", "documentation-guide"),
        _member(_source_id, "SourceCode", "parent-documentation-source", "documentation-guide")
      ))

      When("the resolver receives the public composition value")
      val result = ResolvedComponentResources.resolveC(composition, _evidence(Vector.empty))

      Then("the duplicate logical resource is rejected before evidence resolution")
      result.toOption shouldBe None
    }

    "which rejects an invalid integrity state supplied without codec decoding" in {
      Given("a direct composition whose child integrity state is stale")
      val composition = _composition(Vector(
        _member(_documentation_id, "Documentation", "parent-documentation-source", "documentation-guide").copy(integrity = ComponentSubcomponentState("stale"))
      ))

      When("the resolver receives the public composition value")
      val result = ResolvedComponentResources.resolveC(composition, _evidence(Vector.empty))

      Then("the integrity state is rejected before evidence resolution")
      result.toOption shouldBe None
    }
  }

  private def _resolved(result: Consequence[ResolvedComponentResources]): ResolvedComponentResources =
    result.toOption.get

  private def _evidence(entries: Vector[ResolvedComponentResource]): ComponentResourceEvidence =
    ComponentResourceEvidence(entries)

  private def _entry(
    componentid: ComponentId,
    sourcekind: ComponentResourceSourceKind,
    availability: ComponentResourceAvailability = ComponentResourceAvailability.Available,
    integrity: ComponentResourceIntegrity = ComponentResourceIntegrity.Verified,
    authorization: ComponentResourceAuthorization = ComponentResourceAuthorization.Granted
  ): ResolvedComponentResource = {
    val (role, resource, path, coordinate, logicalsource, physicalsource, externaldeployment) = componentid match {
      case id if id == _parent_id =>
        ("parent", "urn:cncf:resource:phase58/parent", "embedded/rsc-parent.car", "org.example:rsc-parent-car:0.1.0", "composition-registry:rsc-parent", "embedded:primary", false)
      case id if id == _documentation_id =>
        ("Documentation", "urn:cncf:resource:phase58/documentation-guide", "expanded/rsc-documentation/web/index.md", "org.example:rsc-documentation-car:0.1.0", "composition-registry:rsc-documentation", "expanded-car:rsc-documentation", false)
      case id if id == _source_id =>
        ("SourceCode", "urn:cncf:resource:phase58/source-code", "expanded/rsc-source/source/main.scala", "org.example:rsc-source-car:0.1.0", "composition-registry:rsc-source", "expanded-car:rsc-source", false)
      case _ =>
        ("presentation", "urn:cncf:resource:phase58/web-presentation", "expanded/rsc-web/web/index.html", "org.example:rsc-web-presentation-car:0.1.0", "composition-registry:rsc-web-presentation", "expanded-car:rsc-web-presentation", true)
    }
    val step = sourcekind match {
      case ComponentResourceSourceKind.EmbeddedPrimary => "embedded-primary:0"
      case ComponentResourceSourceKind.DevelopmentDirectory => "development-directory:1"
      case ComponentResourceSourceKind.ExpandedCar => "expanded-car:2"
      case ComponentResourceSourceKind.LocalRepository => "local-repository:3"
      case ComponentResourceSourceKind.ManagedCache => "managed-cache:4"
      case ComponentResourceSourceKind.OfflineBundle => "offline-bundle:5"
      case ComponentResourceSourceKind.RemoteRepository => "remote-repository:6"
    }
    ResolvedComponentResource(
      logicalIdentity = ComponentResourceLogicalIdentity(
        componentId = componentid,
        logicalRelease = _release,
        parentComponentId = if (componentid == _parent_id) None else Some(_parent_id),
        childRole = role,
        logicalResource = resource
      ),
      provenance = ComponentResourceProvenance(
        sourceKind = sourcekind,
        repository = _repository,
        artifactCoordinate = coordinate,
        sha256 = _sha256,
        normalizedRelativePath = path,
        logicalSource = logicalsource,
        physicalSource = physicalsource,
        resolutionStep = step,
        childRole = role,
        logicalResource = resource,
        access = "described",
        license = "Apache-2.0",
        externalDeploymentRequired = externaldeployment
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
  }

  private def _composition(members: Vector[ComponentSubcomponentMember]): ComponentSubcomponentComposition =
    ComponentSubcomponentComposition(
      parent = ComponentSubcomponentParent(
        componentId = _parent_id,
        logicalRelease = _release,
        primaryCar = _car("primary", "org.example:rsc-parent-car:0.1.0", "embedded/rsc-parent.car", "composition-registry:rsc-parent", "embedded:primary")
      ),
      members = members
    )

  private def _members: Vector[ComponentSubcomponentMember] =
    Vector(
      _member(_documentation_id, "Documentation", "parent-documentation-source", "documentation-guide"),
      _member(_source_id, "SourceCode", "parent-documentation-source", "source-code"),
      _member(_external_platform_id, "presentation", "parent-external-platform", "web-presentation", externaldeployment = true)
    )

  private def _member(
    componentid: ComponentId,
    role: String,
    profile: String,
    resourceid: String,
    externaldeployment: Boolean = false
  ): ComponentSubcomponentMember =
    ComponentSubcomponentMember(
      componentId = componentid,
      logicalRelease = _release,
      required = true,
      role = role,
      implementationTechnology = if (externaldeployment) "Web" else "Markdown",
      logicalResource = s"urn:cncf:resource:phase58/$resourceid",
      logicalPath = s"logical/$resourceid",
      subcomponentCar = _car("subcomponent", s"org.example:${resourceid.replace('-', '_')}-car:0.1.0", s"expanded/$resourceid.car", s"composition-registry:${componentid.toString}", s"expanded:$resourceid"),
      payload = ComponentSubcomponentPayload(authoritative = false, executable = false),
      authorization = ComponentSubcomponentState("not-granted"),
      integrity = ComponentSubcomponentState("verified"),
      availability = ComponentSubcomponentState("available"),
      deployment = ComponentSubcomponentDeployment(
        platform = if (externaldeployment) "external-web-platform" else "documentation-delivery",
        mode = if (externaldeployment) "external" else "metadata",
        requiresExplicitPlatformAction = externaldeployment,
        authority = ComponentSubcomponentAuthority(false, false, false, false, false)
      ),
      access = ComponentSubcomponentAccess("described"),
      disclosure = ComponentSubcomponentDisclosure("metadata-only"),
      license = ComponentSubcomponentLicense("Apache-2.0"),
      media = ComponentSubcomponentMedia(if (externaldeployment) "text/html" else "text/markdown"),
      profile = ComponentSubcomponentProfile(profile)
    )

  private def _car(
    classification: String,
    coordinate: String,
    physicalpath: String,
    logicalsource: String,
    physicalsource: String
  ): ComponentSubcomponentCar =
    ComponentSubcomponentCar(
      classification = classification,
      artifact = ComponentSubcomponentArtifact(
        coordinate = coordinate,
        sha256 = _sha256,
        signature = _signature,
        repository = _repository,
        physicalPath = physicalpath
      ),
      provenance = ComponentSubcomponentProvenance(
        logicalSource = logicalsource,
        physicalSource = physicalsource,
        physicalPath = physicalpath
      )
    )
}
