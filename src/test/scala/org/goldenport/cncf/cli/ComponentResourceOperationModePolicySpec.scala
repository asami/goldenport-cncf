package org.goldenport.cncf.cli

import org.goldenport.cncf.component._
import org.goldenport.cncf.component.repository._
import org.goldenport.cncf.config.OperationMode
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Failing-first executable acceptance specification for
 * RSC05-MODE-COMPOSITION (Phase 58.4 / RSC-05 / RSC05-A).
 *
 * The policy is deliberately referenced before its production implementation.
 * This specification fixes the CLI/runtime boundary while leaving
 * OperationMode out of Component-domain values and APIs.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentResourceOperationModePolicySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _namespace = "org.goldenport.cncf.phase58"
  private val _release = "0.1.0-SNAPSHOT"
  private val _parent_id = ComponentId(s"$_namespace.RscParent")
  private val _documentation_id = ComponentId(s"$_namespace.RscDocumentation")
  private val _source_id = ComponentId(s"$_namespace.RscSourceCode")
  private val _external_platform_id = ComponentId(s"$_namespace.RscWebPresentation")
  private val _sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _signature = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
  private val _repository = "https://repo.example.invalid/cncf/rsc"
  private val _develop_precedence = Vector(
    ComponentResourceSourceKind.DevelopmentDirectory,
    ComponentResourceSourceKind.ExpandedCar,
    ComponentResourceSourceKind.LocalRepository,
    ComponentResourceSourceKind.ManagedCache,
    ComponentResourceSourceKind.OfflineBundle,
    ComponentResourceSourceKind.RemoteRepository
  )

  "RSC05-AC-01 Develop/Test/Demo/Production select deterministic runtime-owned composition policies" should {
    "select each operation mode deterministically from the shared composition and resolution values" in {
      Given("the shared phase58 parent, Documentation, SourceCode, and external-platform composition with expanded-CAR evidence")
      val composition = _composition
      val resolved = _resolved(
        documentationsource = ComponentResourceSourceKind.ExpandedCar,
        sourcesource = ComponentResourceSourceKind.ExpandedCar,
        externalsource = ComponentResourceSourceKind.ExpandedCar
      )

      When("the runtime-owned policy is selected once for Develop, Test, Demo, and Production")
      val selected = OperationMode.values.toVector.map(mode => _select(mode, composition, resolved))

      Then("each result retains the requested mode and produces a deterministic composition")
      selected.map(_.operationMode) shouldBe OperationMode.values.toVector
      selected.foreach(_.resources.map(_.logicalIdentity.componentId) should contain(_parent_id))
      selected.map(_.resources.map(_.logicalIdentity.componentId)) shouldBe
        OperationMode.values.toVector.map(mode => _select(mode, composition, resolved).resources.map(_.logicalIdentity.componentId))
    }

    "honor the complete Develop precedence from explicit-development-directory through development-local, expanded-car, local-repository, managed-cache, offline-bundle, and remote-repository" in {
      Given("one realistic RSC-04 resolved-resource fixture for every registered development source")
      val composition = _composition
      val fixtures = _develop_precedence.map(sourcekind => sourcekind -> _resolved(sourcekind, sourcekind, sourcekind))

      When("Develop policy selects each isolated source fixture")
      val selected = fixtures.map { case (sourcekind, resolved) =>
        sourcekind -> _select(OperationMode.Develop, composition, resolved)
      }

      Then("the selected logical resources retain the source precedence represented by RSC-04 provenance")
      selected.foreach { case (sourcekind, result) =>
        result.resources.filterNot(_.logicalIdentity.componentId == _parent_id).foreach { resource =>
          resource.provenance.sourceKind shouldBe sourcekind
        }
      }
      selected.map(_._1) shouldBe _develop_precedence
    }

    "retain structured readiness records for required missing stale corrupt and incompatible resources" in {
      Given("required RSC-04 resources whose availability outcomes cover Missing, Stale, Corrupt, and Incompatible")
      val resolved = _resolved(
        documentationsource = ComponentResourceSourceKind.ExpandedCar,
        sourcesource = ComponentResourceSourceKind.ExpandedCar,
        externalsource = ComponentResourceSourceKind.ExpandedCar,
        parentavailability = ComponentResourceAvailability.Incompatible,
        documentationavailability = ComponentResourceAvailability.Missing,
        sourceavailability = ComponentResourceAvailability.Stale,
        externalavailability = ComponentResourceAvailability.Corrupt
      )

      When("Develop policy evaluates the required composition readiness")
      val result = _select(OperationMode.Develop, _composition, resolved)

      Then("every non-ready required resource remains an attributable structured readiness record")
      result.readiness.map(record => record.componentId -> record.availability).toMap shouldBe Map(
        _parent_id -> ComponentResourceAvailability.Incompatible,
        _documentation_id -> ComponentResourceAvailability.Missing,
        _source_id -> ComponentResourceAvailability.Stale,
        _external_platform_id -> ComponentResourceAvailability.Corrupt
      )
      result.readiness.foreach(_.required shouldBe true)
    }

    "keep Test local and deterministic while rejecting an implicit remote resource" in {
      Given("a local repository fixture and a remote repository fixture for the same required Documentation resource")
      val local = _resolved(
        documentationsource = ComponentResourceSourceKind.LocalRepository,
        sourcesource = ComponentResourceSourceKind.LocalRepository,
        externalsource = ComponentResourceSourceKind.LocalRepository
      )
      val remote = _resolved(
        documentationsource = ComponentResourceSourceKind.RemoteRepository,
        sourcesource = ComponentResourceSourceKind.LocalRepository,
        externalsource = ComponentResourceSourceKind.LocalRepository
      )

      When("Test policy is selected for the local fixture and then for the remote Documentation fixture")
      val localresult = ComponentResourceOperationModePolicy.resolveC(OperationMode.Test, _composition, local, false)
      val remoteresult = ComponentResourceOperationModePolicy.resolveC(OperationMode.Test, _composition, remote, false)

      Then("the explicit local fixture is accepted and implicit remote retrieval is rejected")
      localresult.toOption should not be empty
      localresult.toOption.get.resources.filterNot(_.logicalIdentity.componentId == _parent_id).foreach { resource =>
        resource.provenance.sourceKind shouldBe ComponentResourceSourceKind.LocalRepository
      }
      remoteresult.toOption shouldBe None
    }

    "permit Demo remote Documentation only when the caller opts in explicitly" in {
      Given("a Demo fixture with remote Documentation and local SourceCode and presentation resources")
      val resolved = _resolved(
        documentationsource = ComponentResourceSourceKind.RemoteRepository,
        sourcesource = ComponentResourceSourceKind.LocalRepository,
        externalsource = ComponentResourceSourceKind.LocalRepository
      )

      When("Demo policy is evaluated without and with the explicit remote-Documentation opt-in")
      val implicitresult = ComponentResourceOperationModePolicy.resolveC(OperationMode.Demo, _composition, resolved, false)
      val explicitresult = ComponentResourceOperationModePolicy.resolveC(OperationMode.Demo, _composition, resolved, true)

      Then("remote Documentation is absent without opt-in and selected only after the explicit opt-in")
      implicitresult.toOption shouldBe None
      explicitresult.toOption should not be empty
      explicitresult.toOption.get.resources.find(_.logicalIdentity.componentId == _documentation_id).map(_.provenance.sourceKind) shouldBe
        Some(ComponentResourceSourceKind.RemoteRepository)
      explicitresult.toOption.get.resources.find(_.logicalIdentity.componentId == _source_id).map(_.provenance.sourceKind) shouldBe
        Some(ComponentResourceSourceKind.LocalRepository)
    }
  }

  "RSC05-AC-02 external-platform activation/deployment is explicit and Production never automatically resolves SourceCode" should {
    "select embedded primary resources in Production without automatically resolving SourceCode" in {
      Given("a production composition with embedded parent and Documentation plus expanded SourceCode and presentation evidence")
      val resolved = _resolved(
        documentationsource = ComponentResourceSourceKind.EmbeddedPrimary,
        sourcesource = ComponentResourceSourceKind.ExpandedCar,
        externalsource = ComponentResourceSourceKind.ExpandedCar
      )

      When("Production policy composes the runtime-owned primary")
      val result = _select(OperationMode.Production, _composition, resolved)

      Then("only embedded primary resources are selected and SourceCode is never auto-selected")
      result.resources.map(_.provenance.sourceKind) should contain only ComponentResourceSourceKind.EmbeddedPrimary
      result.resources.map(_.logicalIdentity.componentId) should contain(_parent_id)
      result.resources.map(_.logicalIdentity.componentId) should not contain _source_id
    }

    "preserve development and packaged logical composition parity" in {
      Given("development-local and packaged RSC-04 fixtures carrying the same phase58 logical identities")
      val development = _select(
        OperationMode.Develop,
        _composition,
        _resolved(
          documentationsource = ComponentResourceSourceKind.DevelopmentDirectory,
          sourcesource = ComponentResourceSourceKind.DevelopmentDirectory,
          externalsource = ComponentResourceSourceKind.DevelopmentDirectory
        )
      )
      val packaged = _select(
        OperationMode.Develop,
        _composition,
        _resolved(
          documentationsource = ComponentResourceSourceKind.ExpandedCar,
          sourcesource = ComponentResourceSourceKind.ExpandedCar,
          externalsource = ComponentResourceSourceKind.ExpandedCar
        )
      )

      When("the policy composes both forms")
      val developmentlogical = development.resources.map(_.logicalIdentity)
      val packagedlogical = packaged.resources.map(_.logicalIdentity)

      Then("logical identity, release, parent membership, role, and resource remain equal across the two forms")
      developmentlogical should contain theSameElementsAs packagedlogical
      developmentlogical.map(identity => identity.componentId) should contain allElementsOf
        Vector(_parent_id, _documentation_id, _source_id, _external_platform_id)
    }

    "return only an explicit external-platform handoff while retaining no runtime authority" in {
      Given("an external-platform WebPresentation child requiring platform-native action")
      val result = _select(
        OperationMode.Develop,
        _composition,
        _resolved(
          documentationsource = ComponentResourceSourceKind.DevelopmentDirectory,
          sourcesource = ComponentResourceSourceKind.DevelopmentDirectory,
          externalsource = ComponentResourceSourceKind.ExpandedCar
        )
      )

      When("the runtime-owned policy composes the external-platform child")
      val external = result.resources.find(_.logicalIdentity.componentId == _external_platform_id).get

      Then("the child appears as one explicit platform handoff and never as an activated or authorized runtime operation")
      result.platformHandoffs.map(_.componentId) shouldBe Vector(_external_platform_id)
      external.provenance.externalDeploymentRequired shouldBe true
      external.activationAuthority shouldBe false
      external.operationAuthority shouldBe false
      external.mcpAuthority shouldBe false
      external.disclosureAuthority shouldBe false
      external.deploymentAuthority shouldBe false
    }
  }

  "RSC05-AC-03 OperationMode does not enter Component-domain APIs or grant authority" should {
    "keep operation mode at the CLI policy boundary while returning runtime-owned values" in {
      Given("a ComponentSubcomponentComposition and its RSC-04 resolved-resource value without a Component mode field")
      val resolved = _resolved(
        documentationsource = ComponentResourceSourceKind.DevelopmentDirectory,
        sourcesource = ComponentResourceSourceKind.DevelopmentDirectory,
        externalsource = ComponentResourceSourceKind.ExpandedCar
      )

      When("the CLI policy is invoked with OperationMode.Develop")
      val result = _select(OperationMode.Develop, _composition, resolved)

      Then("the result identifies the selected mode and carries composition and handoff data without granting authority")
      result.operationMode shouldBe OperationMode.Develop
      result.resources.map(_.logicalIdentity.componentId) should contain allElementsOf
        Vector(_parent_id, _documentation_id, _source_id, _external_platform_id)
      result.platformHandoffs.map(_.componentId) should contain(_external_platform_id)
      result.resources.foreach { resource =>
        resource.activationAuthority shouldBe false
        resource.operationAuthority shouldBe false
        resource.mcpAuthority shouldBe false
        resource.disclosureAuthority shouldBe false
        resource.deploymentAuthority shouldBe false
      }
    }
  }

  private def _select(
    mode: OperationMode,
    composition: ComponentSubcomponentComposition,
    resolved: ResolvedComponentResources
  ) =
    ComponentResourceOperationModePolicy.resolveC(mode, composition, resolved, false).toOption.get

  private def _composition: ComponentSubcomponentComposition =
    ComponentSubcomponentComposition(
      parent = ComponentSubcomponentParent(
        componentId = _parent_id,
        logicalRelease = _release,
        primaryCar = _car("primary", "org.example:rsc-parent-car:0.1.0", "embedded/rsc-parent.car", "composition-registry:rsc-parent", "embedded:primary")
      ),
      members = Vector(
        _member(_documentation_id, "Documentation", "parent-documentation-source", "documentation-guide"),
        _member(_source_id, "SourceCode", "parent-documentation-source", "source-code"),
        _member(_external_platform_id, "presentation", "parent-external-platform", "web-presentation", externaldeployment = true)
      )
    )

  private def _resolved(
    documentationsource: ComponentResourceSourceKind,
    sourcesource: ComponentResourceSourceKind,
    externalsource: ComponentResourceSourceKind,
    parentavailability: ComponentResourceAvailability = ComponentResourceAvailability.Available,
    documentationavailability: ComponentResourceAvailability = ComponentResourceAvailability.Available,
    sourceavailability: ComponentResourceAvailability = ComponentResourceAvailability.Available,
    externalavailability: ComponentResourceAvailability = ComponentResourceAvailability.Available
  ): ResolvedComponentResources =
    ResolvedComponentResources(
      compositionShape = ComponentResourceCompositionShape.MultiComponent,
      resources = Vector(
        _resource(_parent_id, ComponentResourceSourceKind.EmbeddedPrimary, parentavailability),
        _resource(_documentation_id, documentationsource, documentationavailability),
        _resource(_source_id, sourcesource, sourceavailability),
        _resource(_external_platform_id, externalsource, externalavailability)
      ),
      diagnostics = Vector.empty
    )

  private def _resource(
    componentid: ComponentId,
    sourcekind: ComponentResourceSourceKind,
    availability: ComponentResourceAvailability
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
      integrity = ComponentResourceIntegrity.Verified,
      authorization = ComponentResourceAuthorization.Granted,
      activationAuthority = false,
      operationAuthority = false,
      mcpAuthority = false,
      disclosureAuthority = false,
      deploymentAuthority = false
    )
  }

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
