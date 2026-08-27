package org.goldenport.cncf.component.admin

import io.circe.Json
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{ComponentResourceAuthorization, ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceLogicalIdentity, ComponentResourceSourceKind}
import org.goldenport.cncf.knowledge.{ComponentKnowledgeAuthority, ComponentKnowledgeDisclosure, ComponentKnowledgeManifest, ComponentKnowledgeMetadata, ComponentKnowledgeResourceBinding, ComponentKnowledgeResourceEntry, ComponentKnowledgeResourceKind, ComponentKnowledgeResourceRole, ComponentKnowledgeSafeProvenance, ComponentKnowledgeSource, ComponentKnowledgeStability, ComponentKnowledgeMediaType, PortableDiagramGeneratedFrom, PortableDiagramResource, PortableModelResource, PortableModelResourceContext}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for ADM04-CONTRACT-MODEL: a package-private,
 * value-only Component Admin projection of the authoritative Phase 59
 * knowledge contract and portable model evidence.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentAdminContractModelProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord("in spec:component-admin-contract-model-projection, example:E1, rules:ADM04-R1, phase:60.3, slice:ADM-04A")
  private val _e2 = afterWord("in spec:component-admin-contract-model-projection, example:E2, rules:ADM04-R2, phase:60.3, slice:ADM-04A")
  private val _e3 = afterWord("in spec:component-admin-contract-model-projection, example:E3, rules:ADM04-R3, phase:60.3, slice:ADM-04A")
  private val _e4 = afterWord("in spec:component-admin-contract-model-projection, example:E4, rules:ADM04-R4, phase:60.3, slice:ADM-04A")

  "ADM04-CONTRACT-MODEL Component Admin projection" should {
    "E1 retain authoritative model, diagram, and generated-from evidence" must _e1 {
      "when retaining authoritative model and evidence" in {
        Given("Spec: src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminContractModelProjectionSpec.scala; Rules: ADM04-R1; Example: E1; a valid Admin identity and a Phase 59 manifest containing every admitted model kind and safe evidence")
        val manifest = _manifest()

        When("the package-private projection derives its consumer contract")
        val projected = ComponentAdminContractModelProjection.projectC(_identity_view, manifest).toOption

        Then("identity, resource metadata, model and diagram identities, and generated-from digests remain canonical")
        projected should not be empty
        projected.map(_.identityview) shouldBe Some(_identity_view)
        projected.map(_.knowledgecontract.componentId) shouldBe Some(_component_id)
        projected.map(_.knowledgecontract.logicalRelease) shouldBe Some(_release)
        projected.flatMap(_.knowledgecontract.modelResources).map(_.models.map(_.logicalIdentity.logicalResource)) shouldBe Some(
          _model_entries.take(6).sortBy(_.binding.logicalIdentity.childRole).map(_.binding.logicalIdentity.logicalResource)
        )
        projected.flatMap(_.knowledgecontract.modelResources).map(_.diagrams.map(_.logicalIdentity.logicalResource)) shouldBe Some(
          _model_entries.drop(6).map(_.binding.logicalIdentity.logicalResource)
        )
        projected.flatMap(_.knowledgecontract.modelResources).map(_.diagrams.flatMap(_.generatedFrom).map(source => (source.sourceIdentity.logicalResource, source.sourceSha256))) shouldBe Some(
          Vector(
            (_model_entries.head.binding.logicalIdentity.logicalResource, _digest),
            (_model_entries(2).binding.logicalIdentity.logicalResource, _digest)
          )
        )
        projected.map(_.knowledgecontract.resources.forall(_.metadata.disclosure == ComponentKnowledgeDisclosure.MetadataOnly)) shouldBe Some(true)
        projected.map(_.knowledgecontract.resources.forall(_.provenance.matchingDigest == _digest)) shouldBe Some(true)
      }
    }

    "E2 reject mismatched identity, release, and null manifest inputs" must _e2 {
      "when validating each boundary input" in {
        Given("Spec: src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminContractModelProjectionSpec.scala; Rules: ADM04-R2; Example: E2; a valid Admin identity and independently valid manifests with mismatched component or logical-release identity")
        val othercomponent = ComponentId("org.goldenport.cncf.admin.Other")

        When("the projection validates each boundary input")
        val mismatchedcomponent = ComponentAdminContractModelProjection.projectC(_identity_view, _manifest(othercomponent, _release))
        val mismatchedrelease = ComponentAdminContractModelProjection.projectC(_identity_view, _manifest(_component_id, "2.0.0"))
        val nullmanifest = ComponentAdminContractModelProjection.projectC(_identity_view, null)

        val results = Vector(mismatchedcomponent, mismatchedrelease, nullmanifest)

        Then("every identity disagreement or absent manifest is rejected as an invalid argument")
        results.map(_.isSuccess) shouldBe Vector(false, false, false)
        results.map(_.display).exists(_.contains("component identity")) shouldBe true
        results.map(_.display).exists(_.contains("logical release")) shouldBe true
        results.map(_.display).exists(_.contains("manifest is required")) shouldBe true
      }
    }

    "E3 reject operational and resolver extension attempts" must _e3 {
      "when deriving a consumer contract from hostile manifests" in {
        Given("Spec: src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminContractModelProjectionSpec.scala; Rules: ADM04-R3; Example: E3; a valid Phase 59 manifest with blocked operational and resolver extension aliases")
        val operational = _manifest().copy(extensions = Map("operationAuthority" -> Json.fromBoolean(true)))
        val resolver = _manifest().copy(extensions = Map("resolver" -> Json.fromBoolean(true)))

        When("the projection derives the consumer contract from each hostile manifest")
        val results = Vector(operational, resolver).map(manifest => ComponentAdminContractModelProjection.projectC(_identity_view, manifest).toOption)

        Then("the established manifest and consumer contracts reject the extension instead of granting authority")
        results shouldBe Vector(None, None)
      }
    }

    "E4 retain canonical contract evidence across fifty generated finite variations" must _e4 {
      "when evaluating the generated metadata value space" in {
        Given("Spec: src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminContractModelProjectionSpec.scala; Rules: ADM04-R4; Example: E4; a generator of safe root metadata variations over one valid Phase 59 manifest")
        val property = Prop.forAll(Gen.choose(1, 500)) { number =>
          val manifest = _manifest().copy(extensions = Map("futureMetadata" -> Json.obj("variation" -> Json.fromInt(number))))
          ComponentAdminContractModelProjection.projectC(_identity_view, manifest).toOption.exists { projected =>
            projected.knowledgecontract.componentId == _component_id &&
              projected.knowledgecontract.logicalRelease == _release &&
              projected.knowledgecontract.modelResources.exists { models =>
                models.models.map(_.logicalIdentity) == _model_entries.take(6).sortBy(_.binding.logicalIdentity.childRole).map(_.binding.logicalIdentity) &&
                  models.diagrams.flatMap(_.generatedFrom).map(_.sourceSha256) == Vector(_digest, _digest)
              } &&
              projected.knowledgecontract.extensions == manifest.extensions
          }
        }

        When("the pure projection evaluates the generated metadata value space")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every generated safe variation retains the same authoritative model and contract identities")
        checked.passed shouldBe true
      }
    }
  }

  private val _component_id = ComponentId("org.goldenport.cncf.admin.Model")
  private val _release = "1.0.0"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

  private def _identity_view: ComponentAdminViewModel = {
    val componentclass = ComponentAdminComponentClass(_component_id)
    val identity = ComponentResourceLogicalIdentity(_component_id, _release, None, "model", "urn:cncf:resource:phase60/admin-model")
    val provenance = ComponentAdminSafeProvenance(ComponentAdminSourceKind.KnowledgeManifest, Some(identity))
    val componentfield = ComponentAdminViewField(componentclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val release = ComponentAdminLogicalRelease(componentclass, _release)
    val releasefield = ComponentAdminViewField(release, provenance)
    val instance = ComponentAdminLoadedInstance(componentclass, org.goldenport.cncf.component.ComponentInstanceId(_component_id, "default"))
    val instancefield = ComponentAdminViewField(instance, ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val subsystemclass = ComponentAdminSubsystemClass("component-subsystem")
    val subsystemfield = ComponentAdminViewField(subsystemclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.Descriptor, None))
    val subsysteminstance = ComponentAdminViewField(ComponentAdminSubsystemInstance(subsystemclass, "main"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val implicitsubsystem = ComponentAdminViewField(ComponentAdminImplicitComponentSubsystem(componentclass, "implicit"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.KnowledgeManifest, None))
    _take(ComponentAdminViewModel.createC(
      componentfield,
      releasefield,
      Vector(releasefield),
      instancefield,
      Vector(instancefield),
      subsystemfield,
      subsysteminstance,
      implicitsubsystem,
      ComponentAdminViewField(ComponentAdminResourceState.Available, provenance)
    ))
  }

  private def _manifest(componentid: ComponentId = _component_id, release: String = _release): ComponentKnowledgeManifest = {
    val entries = _resources(componentid, release)
    ComponentKnowledgeManifest(
      componentId = componentid,
      logicalRelease = release,
      resources = entries,
      extensions = Map("safeMetadata" -> Json.obj("label" -> Json.fromString("phase59"))),
      modelResources = Some(PortableModelResourceContext(
        models = entries.take(6).map(PortableModelResource(_)),
        diagrams = Vector(
          PortableDiagramResource(entries(6), Vector(PortableDiagramGeneratedFrom(entries.head.binding.logicalIdentity, _digest))),
          PortableDiagramResource(entries(7), Vector(PortableDiagramGeneratedFrom(entries(2).binding.logicalIdentity, _digest)))
        ),
        extensions = Map("safeModelMetadata" -> Json.fromString("retained"))
      ))
    )
  }

  private def _resources(componentid: ComponentId, release: String): Vector[ComponentKnowledgeResourceEntry] =
    Vector(
      _entry(componentid, release, ComponentKnowledgeResourceKind.Entity, "Entity", "entity"),
      _entry(componentid, release, ComponentKnowledgeResourceKind.Powertype, "Powertype", "powertype"),
      _entry(componentid, release, ComponentKnowledgeResourceKind.StateMachine, "StateMachine", "state-machine"),
      _entry(componentid, release, ComponentKnowledgeResourceKind.Value, "Value", "value"),
      _entry(componentid, release, ComponentKnowledgeResourceKind.Datatype, "Datatype", "datatype"),
      _entry(componentid, release, ComponentKnowledgeResourceKind.Relationship, "Relationship", "relationship"),
      _entry(componentid, release, ComponentKnowledgeResourceKind.ClassDiagram, "ClassDiagram", "class-diagram"),
      _entry(componentid, release, ComponentKnowledgeResourceKind.StateDiagram, "StateDiagram", "state-diagram")
    )

  private def _model_entries: Vector[ComponentKnowledgeResourceEntry] = _resources(_component_id, _release)

  private def _entry(
    componentid: ComponentId,
    release: String,
    kind: ComponentKnowledgeResourceKind,
    childrole: String,
    resourceid: String
  ): ComponentKnowledgeResourceEntry = {
    val identity = ComponentResourceLogicalIdentity(componentid, release, None, childrole, s"urn:cncf:resource:phase60/$resourceid")
    ComponentKnowledgeResourceEntry(
      binding = ComponentKnowledgeResourceBinding(identity),
      logicalPath = s"models/$resourceid.json",
      kind = kind,
      role = if (kind == ComponentKnowledgeResourceKind.ClassDiagram || kind == ComponentKnowledgeResourceKind.StateDiagram) ComponentKnowledgeResourceRole.Diagram else ComponentKnowledgeResourceRole.Model,
      language = None,
      mediaType = if (kind == ComponentKnowledgeResourceKind.ClassDiagram || kind == ComponentKnowledgeResourceKind.StateDiagram) ComponentKnowledgeMediaType.ImageSvgXml else ComponentKnowledgeMediaType.ApplicationJson,
      size = 42,
      sha256 = _digest,
      metadata = ComponentKnowledgeMetadata(ComponentKnowledgeAuthority.Component, ComponentKnowledgeStability.Stable, ComponentKnowledgeSource.SuppliedPhase58, "Apache-2.0", ComponentKnowledgeDisclosure.MetadataOnly),
      availability = ComponentResourceAvailability.Available,
      integrity = ComponentResourceIntegrity.Verified,
      authorization = ComponentResourceAuthorization.Granted,
      provenance = ComponentKnowledgeSafeProvenance(ComponentResourceSourceKind.ExpandedCar, "org.example:phase60-admin:0.1.0", "component-registry:phase60", "expanded-car:2", false, _digest)
    )
  }

  private def _take[A](value: org.goldenport.Consequence[A]): A = value.toOption.get
}
