package org.goldenport.cncf.knowledge

import io.circe.Json
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
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable acceptance specification for DOC02-KNOWLEDGE-MANIFEST-CODEC
 * (Phase 59.2 / DOC-02 / DOC-02A).
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeManifestSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _schema = "cncf.component-knowledge.v1"
  private val _release = "0.1.0-SNAPSHOT"
  private val _parent_id = ComponentId("org.goldenport.cncf.phase59.KnowledgeParent")
  private val _documentation_id = ComponentId("org.goldenport.cncf.phase59.KnowledgeDocumentation")
  private val _source_id = ComponentId("org.goldenport.cncf.phase59.KnowledgeSourceCode")
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _other_digest = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

  "DOC02-AC-01 deterministic identity, role, path, digest, authority, and provenance codec" should {
    "round-trip the canonical contract while retaining forward unknown fields" in {
      Given("a v1 manifest with Documentation and SourceCode entries plus unknown root and binding fields")
      val canonical = ComponentKnowledgeManifestCodec.encode(_manifest)
      val supplied = canonical
        .replace("\"resources\":[", "\"futureRoot\":{\"z\":1,\"a\":2},\"resources\":[")
        .replace("\"binding\":{", "\"binding\":{\"futureBinding\":\"retained\",")

      When("the duplicate-key-rejecting codec decodes and canonically re-encodes the manifest")
      val decoded = ComponentKnowledgeManifestCodec.decodeC(supplied).toOption
      val encoded = decoded.map(ComponentKnowledgeManifestCodec.encode)
      val reversed = _manifest.copy(resources = _manifest.resources.reverse)

      Then("identity, safe evidence, typed role, and unknown extensions survive in byte-stable canonical JSON")
      decoded should not be empty
      encoded should not be empty
      encoded.get should include (s"\"schema\":\"$_schema\"")
      encoded.get should include (s"\"sha256\":\"$_digest\"")
      encoded.get should include ("\"authority\":\"component\"")
      encoded.get should include ("\"sourceKind\":\"expanded-car\"")
      encoded.get should include ("\"futureRoot\":{\"a\":2,\"z\":1}")
      encoded.get should include ("\"futureBinding\":\"retained\"")
      encoded.get should not include ("normalizedRelativePath")
      encoded.get should not include ("physicalSource")
      encoded.get should not include ("repository")
      ComponentKnowledgeManifestCodec.encode(decoded.get) shouldBe encoded.get
      ComponentKnowledgeManifestCodec.encode(reversed) shouldBe ComponentKnowledgeManifestCodec.encode(_manifest)
    }

    "accept omitted or null optional fields and reject non-string optional values" in {
      Given("a valid primary binding with absent, null, and non-string optional field encodings")
      val canonical = ComponentKnowledgeManifestCodec.encode(_primary_manifest)
      val omitted = canonical
        .replace("\"parentComponentId\":null,", "")
        .replace("\"language\":null,", "")
      val nonstringparent = canonical.replace("\"parentComponentId\":null", "\"parentComponentId\":false")
      val nonstringlanguage = canonical.replace("\"language\":null", "\"language\":42")

      When("the codec decodes each optional field representation")
      val decodednull = ComponentKnowledgeManifestCodec.decodeC(canonical).toOption
      val decodedomitted = ComponentKnowledgeManifestCodec.decodeC(omitted).toOption
      val rejectedparent = ComponentKnowledgeManifestCodec.decodeC(nonstringparent).toOption
      val rejectedlanguage = ComponentKnowledgeManifestCodec.decodeC(nonstringlanguage).toOption

      Then("null and omission become None while non-string non-null values are rejected")
      decodednull shouldBe Some(_primary_manifest)
      decodedomitted shouldBe Some(_primary_manifest)
      rejectedparent shouldBe None
      rejectedlanguage shouldBe None
    }
  }

  "DOC02-AC-02 duplicate, unsafe, malformed, incompatible, and duplicate-key inputs" should {
    "reject invalid identity, path, digest, role/media, and recursively protected extension aliases" in {
      Given("otherwise valid manifests containing one prohibited value or protected Phase 58 extension alias at a time")
      val duplicateidentity = _manifest.copy(resources = Vector(_documentation_entry, _documentation_entry.copy(logicalPath = "docs/duplicate.md")))
      val unsafepath = _manifest.copy(resources = Vector(_documentation_entry.copy(logicalPath = "docs/../secret.md")))
      val malformeddigest = _manifest.copy(resources = Vector(_documentation_entry.copy(sha256 = "not-a-digest")))
      val incompatible = _manifest.copy(resources = Vector(_documentation_entry.copy(role = ComponentKnowledgeResourceRole.SourceCode)))
      val protectedextension = _manifest.copy(extensions = Map("physicalPath" -> Json.fromString("private/phase58/resource.bin")))
      val authorizationextension = _manifest.copy(extensions = Map("AuThOrIzAtIoN" -> Json.fromBoolean(true)))
      val repositoryurl = _manifest.copy(extensions = Map("repositoryUrl" -> Json.fromString("https://repo.example.invalid/private")))
      val credentialtoken = _manifest.copy(extensions = Map("credential-token" -> Json.fromBoolean(true)))
      val credentialtokencompound = _manifest.copy(extensions = Map("credentialtoken" -> Json.fromBoolean(true)))
      val normalizedpath = _manifest.copy(extensions = Map("normalized_relative_path" -> Json.obj("value" -> Json.fromString("private/resource.bin"))))
      val nestedaliases = _manifest.copy(extensions = Map("futureRoot" -> Json.obj("futureList" -> Json.arr(Json.obj("physicalLocation" -> Json.fromString("/private/resource.bin"))), "futureObject" -> Json.obj("credentialToken" -> Json.fromBoolean(true)))))
      val duplicatekeyjson = ComponentKnowledgeManifestCodec.encode(_manifest).replace(
        s"\"schema\":\"$_schema\"",
        s"\"schema\":\"$_schema\",\"schema\":\"$_schema\""
      )
      val codecrepositoryurl = ComponentKnowledgeManifestCodec.encode(_manifest).replace("\"resources\":[", "\"repositoryUrl\":\"https://repo.example.invalid/private\",\"resources\":[")
      val codeccredentialtoken = ComponentKnowledgeManifestCodec.encode(_manifest).replace("\"resources\":[", "\"credential-token\":true,\"resources\":[")
      val codeccredentialtokencompound = ComponentKnowledgeManifestCodec.encode(_manifest).replace("\"resources\":[", "\"credentialtoken\":true,\"resources\":[")
      val codecnormalizedpath = ComponentKnowledgeManifestCodec.encode(_manifest).replace("\"resources\":[", "\"normalized_relative_path\":{\"value\":\"private/resource.bin\"},\"resources\":[")
      val codecnestedaliases = ComponentKnowledgeManifestCodec.encode(_manifest).replace("\"resources\":[", "\"futureRoot\":{\"futureList\":[{\"physicalLocation\":\"/private/resource.bin\"}],\"futureObject\":{\"credentialToken\":true}},\"resources\":[")

      When("model validation and codec decoding normalize aliases through nested JSON object and array values")
      val duplicates = ComponentKnowledgeManifest.validateC(duplicateidentity).toOption
      val path = ComponentKnowledgeManifest.validateC(unsafepath).toOption
      val digest = ComponentKnowledgeManifest.validateC(malformeddigest).toOption
      val rolemedia = ComponentKnowledgeManifest.validateC(incompatible).toOption
      val extension = ComponentKnowledgeManifest.validateC(protectedextension).toOption
      val authorization = ComponentKnowledgeManifest.validateC(authorizationextension).toOption
      val repository = ComponentKnowledgeManifest.validateC(repositoryurl).toOption
      val credential = ComponentKnowledgeManifest.validateC(credentialtoken).toOption
      val credentialcompound = ComponentKnowledgeManifest.validateC(credentialtokencompound).toOption
      val normalized = ComponentKnowledgeManifest.validateC(normalizedpath).toOption
      val nested = ComponentKnowledgeManifest.validateC(nestedaliases).toOption
      val json = ComponentKnowledgeManifestCodec.decodeC(duplicatekeyjson).toOption
      val decodedaliases = Vector(codecrepositoryurl, codeccredentialtoken, codeccredentialtokencompound, codecnormalizedpath, codecnestedaliases).map(ComponentKnowledgeManifestCodec.decodeC(_).toOption)

      Then("each invalid value and normalized direct or nested protected alias is rejected through the manifest argument-validation boundary")
      duplicates shouldBe None
      path shouldBe None
      digest shouldBe None
      rolemedia shouldBe None
      extension shouldBe None
      authorization shouldBe None
      repository shouldBe None
      credential shouldBe None
      credentialcompound shouldBe None
      normalized shouldBe None
      nested shouldBe None
      json shouldBe None
      decodedaliases shouldBe Vector(None, None, None, None, None)
    }

    "reject binding identities outside the manifest release and declared Component tree" in {
      Given("otherwise valid entries with a wrong release, unrelated primary identity, or foreign parent")
      val wrongrelease = _manifest.copy(resources = Vector(_documentation_entry.copy(binding = _documentation_entry.binding.copy(logicalIdentity = _documentation_entry.binding.logicalIdentity.copy(logicalRelease = "0.2.0-SNAPSHOT")))))
      val unrelatedprimary = _manifest.copy(resources = Vector(_documentation_entry.copy(binding = _documentation_entry.binding.copy(logicalIdentity = _documentation_entry.binding.logicalIdentity.copy(componentId = ComponentId("org.goldenport.cncf.phase59.Unrelated"), parentComponentId = None)))))
      val foreignparent = _manifest.copy(resources = Vector(_documentation_entry.copy(binding = _documentation_entry.binding.copy(logicalIdentity = _documentation_entry.binding.logicalIdentity.copy(parentComponentId = Some(ComponentId("org.goldenport.cncf.phase59.ForeignParent")))))))

      When("model validation evaluates the binding relationship to the manifest")
      val release = ComponentKnowledgeManifest.validateC(wrongrelease).toOption
      val primary = ComponentKnowledgeManifest.validateC(unrelatedprimary).toOption
      val parent = ComponentKnowledgeManifest.validateC(foreignparent).toOption

      Then("only the manifest primary identity or a child declared by that Component can bind")
      release shouldBe None
      primary shouldBe None
      parent shouldBe None
    }

    "reject hostile canonical-path value-space cases as a property" in {
      Given("a generator covering absolute, drive, backslash, traversal, empty-segment, control, and empty paths")
      val hostilepaths = Gen.oneOf(
        "/absolute/path.md",
        "C:\\work\\guide.md",
        "docs\\guide.md",
        "./guide.md",
        "docs/../guide.md",
        "docs//guide.md",
        "docs/\u0000/guide.md",
        ""
      )
      val property = Prop.forAll(hostilepaths) { path =>
        ComponentKnowledgeManifest.validateC(_manifest.copy(resources = Vector(_documentation_entry.copy(logicalPath = path)))).toOption.isEmpty
      }

      When("the hostile path property is evaluated for fifty generated cases")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("every hostile path is rejected before it can become manifest evidence")
      checked.passed shouldBe true
    }
  }

  "DOC02-AC-03 supplied Phase 58 binding without another resolver or identity input" should {
    "accept matching supplied evidence and reject digest or provenance mismatch" in {
      Given("only supplied Phase 58 resolved resources and a candidate with their safe identities and evidence")
      val supplied = _resolved_resources
      val candidate = _manifest
      val digestmismatch = candidate.copy(resources = candidate.resources.map { entry =>
        if (entry.binding.logicalIdentity.componentId == _documentation_id)
          entry.copy(sha256 = _other_digest, provenance = entry.provenance.copy(matchingDigest = _other_digest))
        else
          entry
      })
      val provenancemismatch = candidate.copy(resources = candidate.resources.map { entry =>
        if (entry.binding.logicalIdentity.componentId == _documentation_id)
          entry.copy(provenance = entry.provenance.copy(logicalSource = "component-registry:other"))
        else
          entry
      })

      When("the creation adapter binds each candidate entry against that supplied value alone")
      val accepted = ComponentKnowledgeManifest.createC(suppliedResources = supplied, candidate = candidate).toOption
      val digestrejected = ComponentKnowledgeManifest.createC(suppliedResources = supplied, candidate = digestmismatch).toOption
      val provenancerejected = ComponentKnowledgeManifest.createC(suppliedResources = supplied, candidate = provenancemismatch).toOption

      Then("matching evidence is accepted, mismatches are rejected, and the adapter call has no resolver or replacement identity input")
      accepted shouldBe Some(candidate)
      digestrejected shouldBe None
      provenancerejected shouldBe None
    }
  }

  private def _manifest: ComponentKnowledgeManifest =
    ComponentKnowledgeManifest(
      componentId = _parent_id,
      logicalRelease = _release,
      resources = Vector(_documentation_entry, _source_entry)
    )

  private def _documentation_entry: ComponentKnowledgeResourceEntry =
    _entry(
      componentid = _documentation_id,
      childrole = "Documentation",
      logicalresource = "urn:cncf:resource:phase59/documentation",
      logicalpath = "docs/guide.md",
      kind = ComponentKnowledgeResourceKind.Documentation,
      role = ComponentKnowledgeResourceRole.Documentation,
      language = None,
      media = ComponentKnowledgeMediaType.TextMarkdown,
      coordinate = "org.example:phase59-documentation:0.1.0",
      logicalsource = "component-registry:documentation"
    )

  private def _primary_manifest: ComponentKnowledgeManifest =
    ComponentKnowledgeManifest(
      componentId = _parent_id,
      logicalRelease = _release,
      resources = Vector(
        _entry(
          componentid = _parent_id,
          parentid = None,
          childrole = "Documentation",
          logicalresource = "urn:cncf:resource:phase59/primary-documentation",
          logicalpath = "docs/primary-guide.md",
          kind = ComponentKnowledgeResourceKind.Documentation,
          role = ComponentKnowledgeResourceRole.Documentation,
          language = None,
          media = ComponentKnowledgeMediaType.TextMarkdown,
          coordinate = "org.example:phase59-primary-documentation:0.1.0",
          logicalsource = "component-registry:primary-documentation"
        )
      )
    )

  private def _source_entry: ComponentKnowledgeResourceEntry =
    _entry(
      componentid = _source_id,
      childrole = "SourceCode",
      logicalresource = "urn:cncf:resource:phase59/source-code",
      logicalpath = "src/main/scala/Knowledge.scala",
      kind = ComponentKnowledgeResourceKind.SourceCode,
      role = ComponentKnowledgeResourceRole.SourceCode,
      language = Some("scala"),
      media = ComponentKnowledgeMediaType.TextXScala,
      coordinate = "org.example:phase59-source:0.1.0",
      logicalsource = "component-registry:source-code"
    )

  private def _entry(
    componentid: ComponentId,
    parentid: Option[ComponentId] = Some(_parent_id),
    childrole: String,
    logicalresource: String,
    logicalpath: String,
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    language: Option[String],
    media: ComponentKnowledgeMediaType,
    coordinate: String,
    logicalsource: String
  ): ComponentKnowledgeResourceEntry =
    ComponentKnowledgeResourceEntry(
      binding = ComponentKnowledgeResourceBinding(
        ComponentResourceLogicalIdentity(componentid, _release, parentid, childrole, logicalresource)
      ),
      logicalPath = logicalpath,
      kind = kind,
      role = role,
      language = language,
      mediaType = media,
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
        artifactCoordinate = coordinate,
        logicalSource = logicalsource,
        resolutionStep = "expanded-car:2",
        externalDeploymentRequired = false,
        matchingDigest = _digest
      )
    )

  private def _resolved_resources: ResolvedComponentResources =
    ResolvedComponentResources(
      compositionShape = ComponentResourceCompositionShape.MultiComponent,
      resources = Vector(
        _resolved_resource(_documentation_entry),
        _resolved_resource(_source_entry)
      ),
      diagnostics = Vector.empty
    )

  private def _resolved_resource(entry: ComponentKnowledgeResourceEntry): ResolvedComponentResource =
    ResolvedComponentResource(
      logicalIdentity = entry.binding.logicalIdentity,
      provenance = ComponentResourceProvenance(
        sourceKind = entry.provenance.sourceKind,
        repository = "https://repo.example.invalid/phase59",
        artifactCoordinate = entry.provenance.artifactCoordinate,
        sha256 = _digest,
        normalizedRelativePath = "phase59/private/resource.bin",
        logicalSource = entry.provenance.logicalSource,
        physicalSource = "expanded-car:private-phase59",
        resolutionStep = entry.provenance.resolutionStep,
        childRole = entry.binding.logicalIdentity.childRole,
        logicalResource = entry.binding.logicalIdentity.logicalResource,
        access = "described",
        license = "Apache-2.0",
        externalDeploymentRequired = entry.provenance.externalDeploymentRequired
      ),
      availability = entry.availability,
      integrity = entry.integrity,
      authorization = entry.authorization,
      activationAuthority = false,
      operationAuthority = false,
      mcpAuthority = false,
      disclosureAuthority = false,
      deploymentAuthority = false
    )
}
