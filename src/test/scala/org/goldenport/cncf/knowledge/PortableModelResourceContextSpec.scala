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
 * Executable acceptance specification for DOC02C-01 portable model and
 * diagram resource context references.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class PortableModelResourceContextSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _release = "0.1.0-SNAPSHOT"
  private val _component_id = ComponentId("org.goldenport.cncf.phase59.PortableModel")
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _other_digest = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

  "DOC02C-01 optional portable model-resource root codec" should {
    "retain legacy output and canonically round-trip safe nested context extensions" in {
      Given("a legacy manifest and an optional portable context with recursively safe extensions")
      val legacy = _manifest
      val context = _context.copy(
        models = _context.models.map(_.copy(extensions = Map("futureModel" -> Json.obj("z" -> Json.fromInt(2), "a" -> Json.fromInt(1))))),
        extensions = Map("futureRoot" -> Json.obj("z" -> Json.arr(Json.obj("d" -> Json.fromInt(4), "c" -> Json.fromInt(3))), "a" -> Json.fromBoolean(true)))
      )
      val contextual = legacy.copy(modelResources = Some(context))

      When("the root codec encodes and decodes both forms")
      val legacyencoded = ComponentKnowledgeManifestCodec.encode(legacy)
      val encoded = ComponentKnowledgeManifestCodec.encode(contextual)
      val decoded = ComponentKnowledgeManifestCodec.decodeC(encoded).toOption

      Then("the absent root stays legacy while the optional context is ordered and canonical")
      legacyencoded should not include "modelResources"
      encoded should include ("\"futureRoot\":{\"a\":true,\"z\":[{\"c\":3,\"d\":4}]}")
      encoded should include ("\"futureModel\":{\"a\":1,\"z\":2}")
      encoded.indexOf("\"modelResources\"") should be < encoded.indexOf("\"resources\"")
      decoded shouldBe Some(contextual.copy(resources = _resources.sortBy(_resource_order), modelResources = Some(_canonical_context(context))))
      decoded.map(ComponentKnowledgeManifestCodec.encode) shouldBe Some(encoded)
    }
  }

  "DOC02C-01 portable model and diagram evidence" should {
    "preserve all admitted model kinds and both diagram kinds in deterministic JSON" in {
      Given("six admitted model resource entries plus class and state diagrams with matching generated-from evidence")
      val manifest = _manifest.copy(resources = _resources.reverse, modelResources = Some(_context))
      val modelproperty = Prop.forAll(Gen.oneOf(_models)) { entry =>
        PortableModelResourceContext.validateC(
          PortableModelResourceContext(Vector(PortableModelResource(entry)), Vector.empty),
          _resources
        ).toOption.contains(PortableModelResourceContext(Vector(PortableModelResource(entry)), Vector.empty))
      }

      When("the root codec canonically encodes and decodes the complete context")
      val encoded = ComponentKnowledgeManifestCodec.encode(manifest)
      val decoded = ComponentKnowledgeManifestCodec.decodeC(encoded).toOption
      val canonicalcontext = _canonical_context(_context)
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), modelproperty)

      Then("all typed entries survive as exact manifest evidence with stable ordering")
      decoded shouldBe Some(manifest.copy(resources = _resources.sortBy(_resource_order), modelResources = Some(canonicalcontext)))
      encoded should include ("\"kind\":\"entity\"")
      encoded should include ("\"kind\":\"relationship\"")
      encoded should include ("\"kind\":\"class-diagram\"")
      encoded should include ("\"kind\":\"state-diagram\"")
      checked.passed shouldBe true
      ComponentKnowledgeManifestCodec.encode(manifest) shouldBe encoded
    }
  }

  "DOC02C-01 generated-from and hostile input boundary" should {
    "reject unknown sources, digest mismatch, duplicates, incompatible evidence, hostile aliases and paths, and duplicate JSON keys" in {
      Given("otherwise valid contexts and manifests each containing one invalid context boundary")
      val diagram = _context.diagrams.head
      val unknownidentity = diagram.generatedFrom.head.sourceIdentity.copy(logicalResource = "urn:cncf:resource:phase59:unknown")
      val unknown = _context.copy(diagrams = Vector(diagram.copy(generatedFrom = Vector(diagram.generatedFrom.head.copy(sourceIdentity = unknownidentity)))))
      val mismatch = _context.copy(diagrams = Vector(diagram.copy(generatedFrom = Vector(diagram.generatedFrom.head.copy(sourceSha256 = _other_digest)))))
      val duplicate = _context.copy(models = _context.models :+ _context.models.head)
      val inappropriate = _context.copy(models = Vector(PortableModelResource(_class_diagram)))
      val hostilealias = _context.copy(extensions = Map("future" -> Json.obj("ReSoLvEr-Input" -> Json.fromBoolean(true))))
      val hostilepathentry = _entity.copy(logicalPath = "models/../private.json")
      val hostilepath = _manifest.copy(resources = _resources.map(entry => if (entry == _entity) hostilepathentry else entry), modelResources = Some(_context.copy(models = Vector(PortableModelResource(hostilepathentry)) ++ _context.models.tail)))
      val duplicatejson = ComponentKnowledgeManifestCodec.encode(_manifest.copy(modelResources = Some(_context))).replace("\"models\":[", "\"models\":[],\"models\":[")

      When("validation and duplicate-key-rejecting decode evaluate every invalid boundary")
      val invalid = Vector(unknown, mismatch, duplicate, inappropriate, hostilealias).map(PortableModelResourceContext.validateC(_, _resources).toOption)
      val path = ComponentKnowledgeManifest.validateC(hostilepath).toOption
      val duplicatekey = ComponentKnowledgeManifestCodec.decodeC(duplicatejson).toOption

      Then("all invalid values fail through the argument-invalid validation boundary without resolver or resource I/O")
      invalid shouldBe Vector.fill(5)(None)
      path shouldBe None
      duplicatekey shouldBe None
    }
  }

  private def _context: PortableModelResourceContext =
    PortableModelResourceContext(
      models = _models.map(PortableModelResource(_)),
      diagrams = Vector(
        PortableDiagramResource(
          _class_diagram,
          Vector(
            PortableDiagramGeneratedFrom(_entity.binding.logicalIdentity, _digest),
            PortableDiagramGeneratedFrom(_relationship.binding.logicalIdentity, _digest)
          )
        ),
        PortableDiagramResource(
          _state_diagram,
          Vector(PortableDiagramGeneratedFrom(_state_machine.binding.logicalIdentity, _digest))
        )
      )
    )

  private def _manifest: ComponentKnowledgeManifest =
    ComponentKnowledgeManifest(_component_id, _release, _resources)

  private def _resources: Vector[ComponentKnowledgeResourceEntry] =
    _models ++ Vector(_class_diagram, _state_diagram)

  private def _models: Vector[ComponentKnowledgeResourceEntry] =
    Vector(
      _entity,
      _entry(ComponentKnowledgeResourceKind.Powertype, "Powertype"),
      _state_machine,
      _entry(ComponentKnowledgeResourceKind.Value, "Value"),
      _entry(ComponentKnowledgeResourceKind.Datatype, "Datatype"),
      _relationship
    )

  private def _entity: ComponentKnowledgeResourceEntry = _entry(ComponentKnowledgeResourceKind.Entity, "Entity")
  private def _state_machine: ComponentKnowledgeResourceEntry = _entry(ComponentKnowledgeResourceKind.StateMachine, "StateMachine")
  private def _relationship: ComponentKnowledgeResourceEntry = _entry(ComponentKnowledgeResourceKind.Relationship, "Relationship")
  private def _class_diagram: ComponentKnowledgeResourceEntry = _diagram(ComponentKnowledgeResourceKind.ClassDiagram, "ClassDiagram")
  private def _state_diagram: ComponentKnowledgeResourceEntry = _diagram(ComponentKnowledgeResourceKind.StateDiagram, "StateDiagram")

  private def _entry(kind: ComponentKnowledgeResourceKind, childrole: String): ComponentKnowledgeResourceEntry =
    _resource(kind, ComponentKnowledgeResourceRole.Model, ComponentKnowledgeMediaType.ApplicationJson, childrole, s"models/${kind.code}.json")

  private def _diagram(kind: ComponentKnowledgeResourceKind, childrole: String): ComponentKnowledgeResourceEntry =
    _resource(kind, ComponentKnowledgeResourceRole.Diagram, ComponentKnowledgeMediaType.ImageSvgXml, childrole, s"diagrams/${kind.code}.svg")

  private def _resource(
    kind: ComponentKnowledgeResourceKind,
    role: ComponentKnowledgeResourceRole,
    media: ComponentKnowledgeMediaType,
    childrole: String,
    path: String
  ): ComponentKnowledgeResourceEntry =
    ComponentKnowledgeResourceEntry(
      binding = ComponentKnowledgeResourceBinding(ComponentResourceLogicalIdentity(_component_id, _release, None, childrole, s"urn:cncf:resource:phase59:${kind.code}")),
      logicalPath = path,
      kind = kind,
      role = role,
      language = None,
      mediaType = media,
      size = 42,
      sha256 = _digest,
      metadata = ComponentKnowledgeMetadata(ComponentKnowledgeAuthority.Component, ComponentKnowledgeStability.Stable, ComponentKnowledgeSource.SuppliedPhase58, "Apache-2.0", ComponentKnowledgeDisclosure.MetadataOnly),
      availability = ComponentResourceAvailability.Available,
      integrity = ComponentResourceIntegrity.Verified,
      authorization = ComponentResourceAuthorization.Granted,
      provenance = ComponentKnowledgeSafeProvenance(ComponentResourceSourceKind.ExpandedCar, "org.example:phase59-portable-model:0.1.0", "component-registry:portable-model", "expanded-car:2", false, _digest)
    )

  private def _resource_order(entry: ComponentKnowledgeResourceEntry): (String, String, String, String, String, String) = {
    val identity = entry.binding.logicalIdentity
    (identity.componentId.name, identity.logicalRelease, identity.parentComponentId.map(_.name).getOrElse(""), identity.childRole, identity.logicalResource, entry.logicalPath)
  }

  private def _identity_order(identity: ComponentResourceLogicalIdentity): (String, String, String, String, String) =
    (identity.componentId.name, identity.logicalRelease, identity.parentComponentId.map(_.name).getOrElse(""), identity.childRole, identity.logicalResource)

  private def _canonical_context(context: PortableModelResourceContext): PortableModelResourceContext =
    context.copy(
      models = context.models.sortBy(value => _resource_order(value.entry)),
      diagrams = context.diagrams.sortBy(value => _resource_order(value.entry)).map { diagram =>
        diagram.copy(generatedFrom = diagram.generatedFrom.sortBy(value => _identity_order(value.sourceIdentity)))
      }
    )
}
