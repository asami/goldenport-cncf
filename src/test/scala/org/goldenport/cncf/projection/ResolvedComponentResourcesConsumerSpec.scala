package org.goldenport.cncf.projection

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.component._
import org.goldenport.cncf.component.repository._
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Failing-first executable acceptance specification for
 * RSC08-CONSUMER-PROJECTION (Phase 58.7 / RSC-08 / RSC-08A RED).
 *
 * The consumer projection vocabulary is deliberately referenced before its
 * production implementation.  This specification fixes the shared Help and
 * Admin inventory/access boundary while keeping resolution and authorization
 * owned by their existing repository contracts.
 *
 * @since   Aug. 22, 2026
 * @version Aug. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResolvedComponentResourcesConsumerSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _namespace = "org.goldenport.cncf.phase58"
  private val _release = "0.1.0-SNAPSHOT"
  private val _subsystem_name = "phase58-rsc08-help-admin"
  private val _parent_id = ComponentId(s"$_namespace.RscParent")
  private val _documentation_id = ComponentId(s"$_namespace.RscDocumentation")
  private val _source_id = ComponentId(s"$_namespace.RscSourceCode")
  private val _external_platform_id = ComponentId(s"$_namespace.RscWebPresentation")
  private val _ordinary_subcomponent_id = ComponentId(s"$_namespace.RscOrdinarySubcomponent")
  private val _component_ids = Vector(
    _parent_id,
    _documentation_id,
    _source_id,
    _external_platform_id,
    _ordinary_subcomponent_id
  )
  private val _repository = "https://repo.example.invalid/cncf/rsc"
  private val _trusted_signing_key = "phase58-rsc-signing-key-1"
  private val _safe_view_fields = Vector(
    "componentId",
    "logicalRelease",
    "parentComponentId",
    "childRole",
    "logicalResource",
    "availability",
    "integrity",
    "authorization",
    "sourceKind",
    "artifactCoordinate",
    "sha256",
    "logicalSource",
    "resolutionStep",
    "externalDeploymentRequired"
  )

  "RSC08-AC-01 Help and Admin consumer projections" should {
    "which preserve identical safe identity, state, and provenance views for the complete fixture" in {
      Given("a named Subsystem and supplied resolved resources for the parent, Documentation, SourceCode, external-platform, and ordinary Subcomponent fixtures")
      val composition = _composition
      val resolved = _resolved

      When("Help and Admin inventory projections consume the same resolved-resource value")
      SubsystemTestFixture.withSubsystem(
        params = SubsystemTestFixture.Params(name = _subsystem_name)
      ) { subsystem =>
        val help = ResolvedComponentResourcesConsumerProjection.inventory(
          resolved,
          ComponentResourceConsumer.Help,
          subsystem.name
        )
        val admin = ResolvedComponentResourcesConsumerProjection.inventory(
          resolved,
          ComponentResourceConsumer.Admin,
          subsystem.name
        )
        val expected = resolved.resources.map(_view)

        Then("both consumers retain deterministic fixture order and the same consumer-neutral fields")
        help.consumer shouldBe ComponentResourceConsumer.Help
        admin.consumer shouldBe ComponentResourceConsumer.Admin
        help.subsystemIdentity shouldBe _subsystem_name
        admin.subsystemIdentity shouldBe _subsystem_name
        help.resources shouldBe expected
        admin.resources shouldBe expected
        help.resources shouldBe admin.resources
        help.resources.map(_.componentId) shouldBe _component_ids
        help.resources.foreach { view =>
          view.productElementNames.toVector shouldBe _safe_view_fields
        }
        composition.parent.componentId shouldBe _parent_id
        composition.members.map(_.componentId) shouldBe _component_ids.tail
      }
    }

    "which preserve supplied order and consumer-neutral views across resource rotations" in {
      Given("a named Subsystem and supplied resolved resources with a generator of rotations")
      val supplied = _resolved
      val rotations = Gen.chooseNum(0, supplied.resources.size - 1)
      val property = Prop.forAll(rotations) { offset =>
        val rotated = supplied.copy(
          resources = supplied.resources.drop(offset) ++ supplied.resources.take(offset)
        )
        val expected = rotated.resources.map(_view)
        SubsystemTestFixture.withSubsystem(
          params = SubsystemTestFixture.Params(name = _subsystem_name)
        ) { subsystem =>
          val help = ResolvedComponentResourcesConsumerProjection.inventory(
            rotated,
            ComponentResourceConsumer.Help,
            subsystem.name
          )
          val admin = ResolvedComponentResourcesConsumerProjection.inventory(
            rotated,
            ComponentResourceConsumer.Admin,
            subsystem.name
          )
          help.resources == expected &&
          admin.resources == expected &&
          help.resources == admin.resources
        }
      }

      When("Help and Admin inventory projections are exercised for generated rotations")
      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(20),
        property
      )

      Then("each consumer preserves the exact supplied order and equal consumer-neutral views")
      checked.passed shouldBe true
    }
  }

  "RSC08-AC-02 supplied resolved-resource boundary" should {
    "which keeps inventory independent of archives repositories caches and development-tree scans" in {
      Given("only an explicitly supplied ResolvedComponentResources value in deterministic input order and a named Subsystem identity")
      val resolved = _resolved

      When("the Help consumer projects inventory from that value")
      SubsystemTestFixture.withSubsystem(
        params = SubsystemTestFixture.Params(name = _subsystem_name)
      ) { subsystem =>
        val inventory = ResolvedComponentResourcesConsumerProjection.inventory(
          resolved,
          ComponentResourceConsumer.Help,
          subsystem.name
        )

        Then("the projection exposes only safe views in supplied order without physical-source or content fields")
        inventory.resources.map(_.componentId) shouldBe _component_ids
        inventory.resources.map(_.logicalRelease).distinct shouldBe Vector(_release)
        inventory.resources.foreach { view =>
          view.productElementNames.toVector shouldBe _safe_view_fields
          view.productElementNames.toVector should not contain "repository"
          view.productElementNames.toVector should not contain "normalizedRelativePath"
          view.productElementNames.toVector should not contain "physicalSource"
          view.productElementNames.toVector should not contain "content"
          view.productElementNames.toVector should not contain "license"
        }
      }
    }
  }

  "RSC08-AC-03 inventory visibility and authorized content access" should {
    "which keeps a restricted resource visible while delegating access through the authorization policy" in {
      Given("a visible restricted Documentation resource and a request carrying its supplied content")
      val composition = _composition
      val restricted = _resource(_documentation_id).copy(
        authorization = ComponentResourceAuthorization.Denied
      )
      val resolved = _resolved.copy(resources = _resolved.resources.map { resource =>
        if resource.logicalIdentity.componentId == _documentation_id then restricted else resource
      })
      val content = _content(_documentation_id)
      val request = _request(composition, restricted, content)

      When("the Help consumer inventories and requests the restricted resource")
      val inventory = ResolvedComponentResourcesConsumerProjection.inventory(
        resolved,
        ComponentResourceConsumer.Help,
        _subsystem_name
      )
      val access = _access(
        ResolvedComponentResourcesConsumerProjection.access(
          composition,
          request,
          ComponentResourceConsumer.Help
        )
      )

      Then("inventory retains the identity but access returns Restricted with no content")
      inventory.resources.map(_.componentId) should contain (_documentation_id)
      inventory.resources.find(_.componentId == _documentation_id).map(_.authorization) shouldBe
        Some(ComponentResourceAuthorization.Denied)
      access.consumer shouldBe ComponentResourceConsumer.Help
      access.disposition.toString shouldBe "Restricted"
      access.content shouldBe empty
      access.diagnostic.toString should not include new String(content, StandardCharsets.UTF_8)
    }

    "which returns only defensively isolated content after an authorized request" in {
      Given("an Available, Verified, Granted Documentation resource and matching composition authorization evidence")
      val composition = _composition
      val resource = _resource(_documentation_id)
      val content = _content(_documentation_id)
      val request = _request(composition, resource, content)

      When("the Admin consumer requests content through the consumer projection")
      val access = _access(
        ResolvedComponentResourcesConsumerProjection.access(
          composition,
          request,
          ComponentResourceConsumer.Admin
        )
      )
      val exposed = access.content.get
      exposed(0) = (exposed(0) ^ 1).toByte

      Then("the result is Granted and mutating exposed bytes does not mutate supplied request content")
      access.consumer shouldBe ComponentResourceConsumer.Admin
      access.disposition.toString shouldBe "Granted"
      access.content.map(_.toVector) should not be empty
      exposed.toVector should not be content.toVector
      request.content.toVector shouldBe content.toVector
    }
  }

  private def _access(
    result: Consequence[ResolvedComponentResourceConsumerAccess]
  ): ResolvedComponentResourceConsumerAccess =
    result.toOption.get

  private def _view(
    resource: ResolvedComponentResource
  ): ResolvedComponentResourceConsumerView =
    ResolvedComponentResourceConsumerView(
      componentId = resource.logicalIdentity.componentId,
      logicalRelease = resource.logicalIdentity.logicalRelease,
      parentComponentId = resource.logicalIdentity.parentComponentId,
      childRole = resource.logicalIdentity.childRole,
      logicalResource = resource.logicalIdentity.logicalResource,
      availability = resource.availability,
      integrity = resource.integrity,
      authorization = resource.authorization,
      sourceKind = resource.provenance.sourceKind,
      artifactCoordinate = resource.provenance.artifactCoordinate,
      sha256 = resource.provenance.sha256,
      logicalSource = resource.provenance.logicalSource,
      resolutionStep = resource.provenance.resolutionStep,
      externalDeploymentRequired = resource.provenance.externalDeploymentRequired
    )

  private def _resolved: ResolvedComponentResources =
    ResolvedComponentResources(
      compositionShape = ComponentResourceCompositionShape.MultiComponent,
      resources = _component_ids.map(_resource),
      diagnostics = Vector(
        ComponentResourceDiagnostic(
          kind = ComponentResourceDiagnosticKind.MultiComponent,
          componentId = None,
          sourceKind = None,
          message = "supplied phase58 RSC08 fixture"
        )
      )
    )

  private def _resource(componentid: ComponentId): ResolvedComponentResource = {
    val (role, resourceid, coordinate, externaldeployment) = componentid match {
      case id if id == _parent_id =>
        ("parent", "parent", "org.example:rsc-parent-car:0.1.0", false)
      case id if id == _documentation_id =>
        ("Documentation", "documentation-guide", "org.example:rsc-documentation-car:0.1.0", false)
      case id if id == _source_id =>
        ("SourceCode", "source-code", "org.example:rsc-source-car:0.1.0", false)
      case id if id == _external_platform_id =>
        ("presentation", "web-presentation", "org.example:rsc-web-presentation-car:0.1.0", true)
      case _ =>
        ("Subcomponent", "ordinary-subcomponent", "org.example:rsc-ordinary-subcomponent-car:0.1.0", false)
    }
    val content = _content(componentid)
    val sourcepath = s"expanded/$resourceid/resource.bin"
    ResolvedComponentResource(
      logicalIdentity = ComponentResourceLogicalIdentity(
        componentId = componentid,
        logicalRelease = _release,
        parentComponentId = if componentid == _parent_id then None else Some(_parent_id),
        childRole = role,
        logicalResource = s"urn:cncf:resource:phase58/$resourceid"
      ),
      provenance = ComponentResourceProvenance(
        sourceKind = ComponentResourceSourceKind.ExpandedCar,
        repository = _repository,
        artifactCoordinate = coordinate,
        sha256 = _sha256(content),
        normalizedRelativePath = sourcepath,
        logicalSource = s"composition-registry:$resourceid",
        physicalSource = s"expanded-car:$resourceid",
        resolutionStep = "expanded-car:2",
        childRole = role,
        logicalResource = s"urn:cncf:resource:phase58/$resourceid",
        access = "described",
        license = "Apache-2.0",
        externalDeploymentRequired = externaldeployment
      ),
      availability = ComponentResourceAvailability.Available,
      integrity = ComponentResourceIntegrity.Verified,
      authorization = ComponentResourceAuthorization.Granted,
      activationAuthority = false,
      operationAuthority = false,
      mcpAuthority = false,
      disclosureAuthority = false,
      deploymentAuthority = false
    )
  }

  private def _composition: ComponentSubcomponentComposition =
    ComponentSubcomponentComposition(
      parent = ComponentSubcomponentParent(
        componentId = _parent_id,
        logicalRelease = _release,
        primaryCar = _car(_parent_id, "primary", "embedded/rsc-parent.car")
      ),
      members = _members
    )

  private def _members: Vector[ComponentSubcomponentMember] =
    Vector(
      _member(_documentation_id, "Documentation", "documentation-guide"),
      _member(_source_id, "SourceCode", "source-code"),
      _member(_external_platform_id, "presentation", "web-presentation", externaldeployment = true),
      _member(_ordinary_subcomponent_id, "Subcomponent", "ordinary-subcomponent")
    )

  private def _member(
    componentid: ComponentId,
    role: String,
    resourceid: String,
    externaldeployment: Boolean = false
  ): ComponentSubcomponentMember =
    ComponentSubcomponentMember(
      componentId = componentid,
      logicalRelease = _release,
      required = true,
      role = role,
      implementationTechnology = if externaldeployment then "Web" else "Markdown",
      logicalResource = s"urn:cncf:resource:phase58/$resourceid",
      logicalPath = s"logical/$resourceid",
      subcomponentCar = _car(
        componentid,
        "subcomponent",
        s"expanded/$resourceid.car"
      ),
      payload = ComponentSubcomponentPayload(authoritative = false, executable = false),
      authorization = ComponentSubcomponentState("not-granted"),
      integrity = ComponentSubcomponentState("verified"),
      availability = ComponentSubcomponentState("available"),
      deployment = ComponentSubcomponentDeployment(
        platform = if externaldeployment then "external-web-platform" else "documentation-delivery",
        mode = if externaldeployment then "external" else "metadata",
        requiresExplicitPlatformAction = externaldeployment,
        authority = ComponentSubcomponentAuthority(false, false, false, false, false)
      ),
      access = ComponentSubcomponentAccess("described"),
      disclosure = ComponentSubcomponentDisclosure("metadata-only"),
      license = ComponentSubcomponentLicense("Apache-2.0"),
      media = ComponentSubcomponentMedia(if externaldeployment then "text/html" else "text/markdown"),
      profile = ComponentSubcomponentProfile("help-admin-consumer")
    )

  private def _car(
    componentid: ComponentId,
    classification: String,
    physicalpath: String
  ): ComponentSubcomponentCar =
    ComponentSubcomponentCar(
      classification = classification,
      artifact = ComponentSubcomponentArtifact(
        coordinate = _coordinate(componentid),
        sha256 = _sha256(_content(componentid)),
        signature = _signature(componentid),
        repository = _repository,
        physicalPath = physicalpath
      ),
      provenance = ComponentSubcomponentProvenance(
        logicalSource = s"composition-registry:${_component_token(componentid)}",
        physicalSource = s"embedded:${_component_token(componentid)}",
        physicalPath = physicalpath
      )
    )

  private def _request(
    composition: ComponentSubcomponentComposition,
    resource: ResolvedComponentResource,
    content: Array[Byte]
  ): ComponentResourceAccessRequest =
    ComponentResourceAccessRequest(
      resource = resource,
      content = content,
      signature = _expected_signature(composition, resource.logicalIdentity.componentId),
      signingKeyId = _trusted_signing_key,
      grantedRoles = Set(resource.logicalIdentity.childRole),
      admittedRepositories = Set(resource.provenance.repository),
      trustedSignaturesByKeyId = Map(
        _trusted_signing_key -> Set(_expected_signature(composition, resource.logicalIdentity.componentId))
      ),
      archiveEntries = Vector(ComponentResourceArchiveEntry(resource.provenance.normalizedRelativePath)),
      managedCacheVerified = true
    )

  private def _expected_signature(
    composition: ComponentSubcomponentComposition,
    componentid: ComponentId
  ): String =
    if componentid == composition.parent.componentId then composition.parent.primaryCar.artifact.signature
    else composition.members.find(_.componentId == componentid).get.subcomponentCar.artifact.signature

  private def _content(componentid: ComponentId): Array[Byte] =
    s"phase58 RSC08 content for ${_component_token(componentid)}".getBytes(StandardCharsets.UTF_8)

  private def _coordinate(componentid: ComponentId): String =
    componentid match {
      case id if id == _parent_id => "org.example:rsc-parent-car:0.1.0"
      case id if id == _documentation_id => "org.example:rsc-documentation-car:0.1.0"
      case id if id == _source_id => "org.example:rsc-source-car:0.1.0"
      case id if id == _external_platform_id => "org.example:rsc-web-presentation-car:0.1.0"
      case _ => "org.example:rsc-ordinary-subcomponent-car:0.1.0"
    }

  private def _signature(componentid: ComponentId): String =
    s"phase58-artifact-signature-${_component_token(componentid)}-v1"

  private def _component_token(componentid: ComponentId): String =
    componentid.name.split("\\.").last.stripPrefix("Rsc").toLowerCase

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
}
