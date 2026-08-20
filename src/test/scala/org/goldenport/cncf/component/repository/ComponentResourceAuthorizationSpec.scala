package org.goldenport.cncf.component.repository

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.cncf.component._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Failing-first executable acceptance specification for
 * RSC06-AUTHORIZATION-INTEGRITY (Phase 58.5 / RSC-06 / RSC06-A).
 *
 * The authorization policy vocabulary is deliberately referenced before its
 * production implementation.  This specification fixes the repository
 * boundary for role/repository authorization, composition evidence, byte
 * integrity, signature/key admission, archive safety, and non-disclosure.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentResourceAuthorizationSpec
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
  private val _repository = "https://repo.example.invalid/cncf/rsc"
  private val _trusted_signing_key = "phase58-rsc-signing-key-1"

  "RSC06-AC-01 authorization, parent/release evidence, digest, and signature are checked before content exposure" should {
    "require explicit role and repository admission for every registered resolver source kind" in {
      Given("the complete phase58 composition and one independently identified child resource at every current resolver source kind")
      val composition = _composition
      val cases = ComponentResourceSourceKind.values.toVector.flatMap { sourcekind =>
        _component_ids.map { componentid =>
          val resource = _resource(componentid, sourcekind)
          (sourcekind, resource, _content(componentid))
        }
      }

      When("each resource is authorized with matching role, repository, parent/release, digest, signature, and trusted-key evidence")
      val results = cases.map { case (_, resource, content) =>
        _authorize(composition, resource, content)
      }

      Then("every source kind and logical identity returns the exact bytes only after all explicit gates pass")
      results.zip(cases).foreach { case (result, (sourcekind, resource, content)) =>
        result.disposition.toString shouldBe "Granted"
        result.content.map(_.toVector) shouldBe Some(content.toVector)
        _assert_diagnostic(result, resource, "Grant")
        result.diagnostic.sourceKind shouldBe sourcekind
      }
      cases.map(_._1).distinct shouldBe ComponentResourceSourceKind.values.toVector
    }

    "keep every source kind non-content when role or repository admission is absent" in {
      Given("one complete explicit admission fixture for every current resolver source kind")
      val composition = _composition
      val sourcecases = ComponentResourceSourceKind.values.toVector.map { sourcekind =>
        val resource = _resource(_documentation_id, sourcekind)
        val content = _content(_documentation_id)
        val admitted = _request(composition, resource, content)
        (sourcekind, resource, content, admitted)
      }

      When("each source is evaluated once without its role grant and once without its repository admission")
      val results = sourcecases.flatMap { case (sourcekind, resource, content, admitted) =>
        Vector(
          (sourcekind, resource, _authorizeRequest(composition, admitted.copy(grantedRoles = Set.empty)), "missing role"),
          (sourcekind, resource, _authorizeRequest(composition, admitted.copy(admittedRepositories = Set.empty)), "missing repository")
        )
      }

      Then("authorization failures are Restricted or Rejected and never expose bytes")
      results.foreach { case (_, resource, result, _) =>
        _assert_non_content(result, resource, "Unauthorized")
      }
      results.map(_._1).distinct shouldBe ComponentResourceSourceKind.values.toVector
    }

    "require matching parent and logical release evidence in addition to composition membership" in {
      Given("a Documentation resource whose role, repository, digest, signature, and key evidence otherwise match the composition")
      val composition = _composition
      val original = _resource(_documentation_id, ComponentResourceSourceKind.ExpandedCar)
      val content = _content(_documentation_id)
      val parentmismatch = original.copy(
        logicalIdentity = original.logicalIdentity.copy(parentComponentId = Some(ComponentId("org.goldenport.cncf.other.Parent")))
      )
      val releasemismatch = original.copy(
        logicalIdentity = original.logicalIdentity.copy(logicalRelease = "9.9.9")
      )

      When("the policy evaluates parent-mismatched and release-mismatched resource evidence")
      val results = Vector(
        _authorize(composition, parentmismatch, content),
        _authorize(composition, releasemismatch, content)
      )

      Then("neither logical mismatch can expose content")
      _assert_non_content(results(0), parentmismatch, "ParentMismatch")
      _assert_non_content(results(1), releasemismatch, "ReleaseMismatch")
    }

    "verify bytes, the composition CAR signature, and the trusted signing key before granting content" in {
      Given("a valid ExpandedCar resource with composition-derived signature and trusted-key evidence")
      val composition = _composition
      val resource = _resource(_documentation_id, ComponentResourceSourceKind.ExpandedCar)
      val content = _content(_documentation_id)
      val valid = _request(composition, resource, content)
      val digestmismatch = valid.copy(content = "tampered documentation bytes".getBytes(StandardCharsets.UTF_8))
      val signaturemismatch = valid.copy(signature = "phase58-wrong-artifact-signature")
      val keymismatch = valid.copy(signingKeyId = "phase58-untrusted-signing-key")

      When("the policy evaluates a digest mismatch, an unexpected artifact signature, and an untrusted key")
      val results = Vector(
        _authorizeRequest(composition, digestmismatch),
        _authorizeRequest(composition, signaturemismatch),
        _authorizeRequest(composition, keymismatch)
      )

      Then("all integrity and key failures remain non-content")
      _assert_non_content(results(0), resource, "DigestMismatch")
      _assert_non_content(results(1), resource, "SignatureMismatch")
      _assert_non_content(results(2), resource, "SignatureMismatch")
    }
  }

  "RSC06-AC-02 traversal, symlink escape, archive ambiguity, corrupt cache, and unauthorized source reject safely" should {
    "reject traversal, absolute, drive, and symlink-escaping archive entries without returning content" in {
      Given("a valid OfflineBundle resource and locally scoped hostile archive-entry literals")
      val composition = _composition
      val resource = _resource(_documentation_id, ComponentResourceSourceKind.OfflineBundle)
      val content = _content(_documentation_id)
      val hostileentries = Vector(
        Vector(ComponentResourceArchiveEntry("../secret.txt")),
        Vector(ComponentResourceArchiveEntry("/etc/passwd")),
        Vector(ComponentResourceArchiveEntry("C:\\Users\\asami\\secret.txt")),
        Vector(ComponentResourceArchiveEntry("payload/readme.md", Some("../../outside.txt")))
      )

      When("the policy evaluates each hostile archive entry shape")
      val results = hostileentries.map(entries =>
        _authorizeRequest(composition, _request(composition, resource, content).copy(archiveEntries = entries))
      )

      Then("every unsafe path is represented as a non-content UnsafeArchive result")
      results.foreach(result => _assert_non_content(result, resource, "UnsafeArchive"))
    }

    "reject duplicate and ambiguous archive entries as non-content results" in {
      Given("a valid ExpandedCar resource and archive entries that normalize to duplicate or ambiguous logical names")
      val composition = _composition
      val resource = _resource(_documentation_id, ComponentResourceSourceKind.ExpandedCar)
      val content = _content(_documentation_id)
      val duplicate = Vector(
        ComponentResourceArchiveEntry("payload/readme.md"),
        ComponentResourceArchiveEntry("payload/readme.md")
      )
      val ambiguous = Vector(
        ComponentResourceArchiveEntry("payload/readme.md"),
        ComponentResourceArchiveEntry("payload/./readme.md")
      )

      When("the policy evaluates duplicate and normalization-ambiguous archive entries")
      val results = Vector(
        _authorizeRequest(composition, _request(composition, resource, content).copy(archiveEntries = duplicate)),
        _authorizeRequest(composition, _request(composition, resource, content).copy(archiveEntries = ambiguous))
      )

      Then("both archive forms are rejected without content")
      results.foreach(result => _assert_non_content(result, resource, "AmbiguousArchive"))
    }

    "treat an unverified ManagedCache and unauthorized source as safe non-content states" in {
      Given("a ManagedCache resource with cache verification disabled and a separately unauthorized source request")
      val composition = _composition
      val cache = _resource(_documentation_id, ComponentResourceSourceKind.ManagedCache)
      val content = _content(_documentation_id)
      val corruptcache = _request(composition, cache, content).copy(managedCacheVerified = false)
      val unauthorizedresource = _resource(_source_id, ComponentResourceSourceKind.RemoteRepository)
      val unauthorized = _request(composition, unauthorizedresource, _content(_source_id)).copy(
        grantedRoles = Set.empty,
        admittedRepositories = Set.empty
      )

      When("the policy evaluates the corrupt cache and the unauthorized source")
      val results = Vector(
        _authorizeRequest(composition, corruptcache),
        _authorizeRequest(composition, unauthorized)
      )

      Then("cache corruption and authorization failure do not expose content")
      _assert_non_content(results(0), cache, "CorruptCache")
      _assert_non_content(results(1), unauthorizedresource, "Unauthorized")
    }
  }

  "RSC06-AC-03 diagnostics do not leak content/credentials/host paths/secrets and registry membership grants no Operation/MCP/activation authority" should {
    "keep diagnostics limited to stable kind, logical ComponentId, and source kind" in {
      Given("a composition member marked as admitted in its manifest but a request carrying no explicit role or repository grant")
      val composition = _composition.copy(
        members = _composition.members.map(member =>
          member.copy(authorization = member.authorization.copy(state = "granted"))
        )
      )
      val resource = _resource(_documentation_id, ComponentResourceSourceKind.LocalRepository)
      val content = "restricted source content must never enter diagnostics".getBytes(StandardCharsets.UTF_8)
      val request = _request(composition, resource, content).copy(
        grantedRoles = Set.empty,
        admittedRepositories = Set.empty
      )

      When("the policy evaluates membership-only authorization")
      val result = _authorizeRequest(composition, request)

      Then("the diagnostic identifies only the stable failure and logical evidence")
      _assert_non_content(result, resource, "Unauthorized")
      result.diagnostic.toString should not include "restricted source content"
      result.diagnostic.toString should not include "https://repo.example.invalid"
    }

    "redact content credentials user-info host paths signing secrets and archive strings from hostile diagnostics" in {
      Given("a valid resource and malicious literals local to a failing archive request")
      val composition = _composition
      val resource = _resource(_source_id, ComponentResourceSourceKind.RemoteRepository)
      val secretcontent = "source-content:do-not-disclose".getBytes(StandardCharsets.UTF_8)
      val credential = "https://alice:password@example.invalid/private"
      val hostpath = "/Users/asami/private/rsc/cache/source.scala"
      val signingsecret = "-----BEGIN PRIVATE KEY-----phase58-secret-----END PRIVATE KEY-----"
      val archivestring = "../../Users/asami/.ssh/id_rsa"
      val request = _request(composition, resource, secretcontent).copy(
        signature = signingsecret,
        signingKeyId = credential,
        archiveEntries = Vector(ComponentResourceArchiveEntry(archivestring)),
        admittedRepositories = Set(hostpath)
      )

      When("the policy evaluates the hostile archive request")
      val result = _authorizeRequest(composition, request)

      Then("the result is unsafe non-content and its diagnostic contains only stable identity fields")
      _assert_non_content(result, resource, "UnsafeArchive")
      val diagnostic = result.diagnostic.toString
      diagnostic should not include "source-content:do-not-disclose"
      diagnostic should not include credential
      diagnostic should not include hostpath
      diagnostic should not include signingsecret
      diagnostic should not include archivestring
    }

    "deny every activation operation MCP disclosure and deployment authority even when composition membership is present" in {
      Given("a complete composition and one valid resource fixture for each forbidden authority bit")
      val composition = _composition
      val original = _resource(_documentation_id, ComponentResourceSourceKind.DevelopmentDirectory)
      val content = _content(_documentation_id)
      val authorityviolations = Vector(
        original.copy(activationAuthority = true),
        original.copy(operationAuthority = true),
        original.copy(mcpAuthority = true),
        original.copy(disclosureAuthority = true),
        original.copy(deploymentAuthority = true)
      )

      When("the policy evaluates each resource carrying one authority bit")
      val results = authorityviolations.map(resource => _authorize(composition, resource, content))

      Then("authority-bearing resource evidence is rejected without content")
      results.foreach(result => _assert_non_content(result, original, "AuthorityViolation"))
      original.activationAuthority shouldBe false
      original.operationAuthority shouldBe false
      original.mcpAuthority shouldBe false
      original.disclosureAuthority shouldBe false
      original.deploymentAuthority shouldBe false
    }
  }

  private def _authorize(
    composition: ComponentSubcomponentComposition,
    resource: ResolvedComponentResource,
    content: Array[Byte]
  ): ComponentResourceAccessResult =
    _authorizeRequest(composition, _request(composition, resource, content))

  private def _authorizeRequest(
    composition: ComponentSubcomponentComposition,
    request: ComponentResourceAccessRequest
  ): ComponentResourceAccessResult =
    ComponentResourceAuthorizationPolicy.authorizeC(composition, request).toOption.get

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
      trustedSigningKeyIds = Set(_trusted_signing_key),
      archiveEntries = Vector(ComponentResourceArchiveEntry(resource.provenance.normalizedRelativePath)),
      managedCacheVerified = true
    )

  private def _assert_non_content(
    result: ComponentResourceAccessResult,
    resource: ResolvedComponentResource,
    kind: String
  ): Unit = {
    Set("Restricted", "Rejected").contains(result.disposition.toString) shouldBe true
    result.content shouldBe empty
    _assert_diagnostic(result, resource, kind)
  }

  private def _assert_diagnostic(
    result: ComponentResourceAccessResult,
    resource: ResolvedComponentResource,
    kind: String
  ): Unit = {
    result.diagnostic.kind.toString shouldBe kind
    result.diagnostic.componentId shouldBe resource.logicalIdentity.componentId
    result.diagnostic.sourceKind shouldBe resource.provenance.sourceKind
  }

  private def _composition: ComponentSubcomponentComposition =
    ComponentSubcomponentComposition(
      parent = ComponentSubcomponentParent(
        componentId = _parent_id,
        logicalRelease = _release,
        primaryCar = _car(_parent_id, "primary", "embedded-primary/rsc-parent.car")
      ),
      members = Vector(
        _member(_documentation_id, "Documentation", "parent-documentation-source", "documentation-guide"),
        _member(_source_id, "SourceCode", "parent-documentation-source", "source-code"),
        _member(_external_platform_id, "presentation", "parent-external-platform", "web-presentation", externaldeployment = true)
      )
    )

  private def _resource(
    componentid: ComponentId,
    sourcekind: ComponentResourceSourceKind
  ): ResolvedComponentResource = {
    val (role, resource, coordinate, logicalsource, externaldeployment) = componentid match {
      case id if id == _parent_id =>
        ("parent", "urn:cncf:resource:phase58/parent", "org.example:rsc-parent-car:0.1.0", "composition-registry:rsc-parent", false)
      case id if id == _documentation_id =>
        ("Documentation", "urn:cncf:resource:phase58/documentation-guide", "org.example:rsc-documentation-car:0.1.0", "composition-registry:rsc-documentation", false)
      case id if id == _source_id =>
        ("SourceCode", "urn:cncf:resource:phase58/source-code", "org.example:rsc-source-car:0.1.0", "composition-registry:rsc-source", false)
      case _ =>
        ("presentation", "urn:cncf:resource:phase58/web-presentation", "org.example:rsc-web-presentation-car:0.1.0", "composition-registry:rsc-web-presentation", true)
    }
    val token = _component_token(componentid)
    val source = _source_token(sourcekind)
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
        sha256 = _sha256(_content(componentid)),
        normalizedRelativePath = s"$source/$token/resource.bin",
        logicalSource = logicalsource,
        physicalSource = s"$source:$token",
        resolutionStep = _resolution_step(sourcekind),
        childRole = role,
        logicalResource = resource,
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
      subcomponentCar = _car(componentid, "subcomponent", s"expanded/$resourceid.car"),
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

  private def _expected_signature(
    composition: ComponentSubcomponentComposition,
    componentid: ComponentId
  ): String =
    if (componentid == composition.parent.componentId) composition.parent.primaryCar.artifact.signature
    else composition.members.find(_.componentId == componentid).get.subcomponentCar.artifact.signature

  private def _content(componentid: ComponentId): Array[Byte] =
    componentid match {
      case id if id == _parent_id => "phase58 parent resource manifest".getBytes(StandardCharsets.UTF_8)
      case id if id == _documentation_id => "phase58 documentation resource".getBytes(StandardCharsets.UTF_8)
      case id if id == _source_id => "phase58 source resource".getBytes(StandardCharsets.UTF_8)
      case _ => "phase58 external presentation resource".getBytes(StandardCharsets.UTF_8)
    }

  private def _coordinate(componentid: ComponentId): String =
    componentid match {
      case id if id == _parent_id => "org.example:rsc-parent-car:0.1.0"
      case id if id == _documentation_id => "org.example:rsc-documentation-car:0.1.0"
      case id if id == _source_id => "org.example:rsc-source-car:0.1.0"
      case _ => "org.example:rsc-web-presentation-car:0.1.0"
    }

  private def _signature(componentid: ComponentId): String =
    s"phase58-artifact-signature-${_component_token(componentid)}-v1"

  private def _component_token(componentid: ComponentId): String =
    componentid.name.split("\\.").last.stripPrefix("Rsc").toLowerCase

  private def _source_token(sourcekind: ComponentResourceSourceKind): String =
    sourcekind match {
      case ComponentResourceSourceKind.EmbeddedPrimary => "embedded-primary"
      case ComponentResourceSourceKind.DevelopmentDirectory => "development-directory"
      case ComponentResourceSourceKind.ExpandedCar => "expanded-car"
      case ComponentResourceSourceKind.LocalRepository => "local-repository"
      case ComponentResourceSourceKind.ManagedCache => "managed-cache"
      case ComponentResourceSourceKind.OfflineBundle => "offline-bundle"
      case ComponentResourceSourceKind.RemoteRepository => "remote-repository"
    }

  private def _resolution_step(sourcekind: ComponentResourceSourceKind): String =
    sourcekind match {
      case ComponentResourceSourceKind.EmbeddedPrimary => "embedded-primary:0"
      case ComponentResourceSourceKind.DevelopmentDirectory => "development-directory:1"
      case ComponentResourceSourceKind.ExpandedCar => "expanded-car:2"
      case ComponentResourceSourceKind.LocalRepository => "local-repository:3"
      case ComponentResourceSourceKind.ManagedCache => "managed-cache:4"
      case ComponentResourceSourceKind.OfflineBundle => "offline-bundle:5"
      case ComponentResourceSourceKind.RemoteRepository => "remote-repository:6"
    }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
}
