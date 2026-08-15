package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentDescriptor.given
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, SubsystemAssemblyAdmission}
import org.goldenport.record.{Record, RecordDecoder}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentDescriptorCompatibilitySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  private val _work_root = Path.of("target/cncf-test/work/phase56-component-descriptor-compatibility-spec")
  private val _component_id = ComponentId("org.simplemodeling.textus.UserAccount")
  private val _component_style_snapshot = ComponentStyleSnapshot(
    apiVersion = "cncf.textus/v1",
    provider = ComponentStyleProviderId.parseC("cncf").toOption.get,
    id = ComponentStyleId.parseC("textus.application@1").toOption.get,
    version = 1,
    parameterSchema = ComponentStyleParameterSchema(),
    parameters = Map.empty,
    bundles = Vector.empty,
    capabilities = Vector.empty,
    effectiveCapabilities = Vector.empty,
    subsystemCapabilities = Vector.empty
  )
  private val _e1 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E1, rules:CID06-R2,R9, phase:56, slice:CID-06B"
  )
  private val _e2 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E2, rules:CID06-R4,R9, phase:56, slice:CID-06B"
  )
  private val _e3 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E3, rules:CID06-R1,R5, phase:56, slice:CID-06B"
  )
  private val _e4 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E4, rules:CID06-R1,R5, phase:56, slice:CID-06B"
  )
  private val _e5 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E5, rules:CID06-R2,R9,R10, phase:56, slice:CID-06B"
  )
  private val _e6 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E6, rules:CID06-R2,R9, phase:56, slice:CID-06B"
  )
  private val _e7 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E7, rules:CID06-R5,R9, phase:56, slice:CID-06B"
  )
  private val _e8 = afterWord(
    "in spec:phase-56-component-descriptor-compatibility, example:E8, rules:CID06-R2,R9, phase:56, slice:CID-06B"
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    _delete_tree(_work_root)
  }

  override protected def afterAll(): Unit = {
    try {
      _delete_tree(_work_root)
    } finally {
      super.afterAll()
    }
  }

  "Phase 56 Component descriptor compatibility" should {
    "descriptor projection behavior (E1-E4)" which {
      "E1 adapt every legacy spelling to the expected canonical identity" must _e1 {
      "when a legacy descriptor carries two compatible presentation spellings" in {
        Given("legacy schema descriptors carrying canonical, local, artifact, and presentation spellings")
        val spellings = Vector(_component_id.name, "UserAccount", "user-account", "textus-user-account")
        When("the central adapter evaluates each non-schema-3 descriptor")
        val projections = spellings.map { alias =>
          val source = _legacy_descriptor(alias, alias)
          val projection = ComponentIdentityCompatibilityAdapter.projectDescriptorC(source, _component_id)
          alias -> (source, projection)
        }
        Then("each legacy descriptor projects to canonical identity fields with a notice for noncanonical spelling")
        projections.foreach { case (alias, (source, projection)) =>
          projection shouldBe a[Consequence.Success[_]]
          val value = projection.toOption.get
          value.descriptor.name shouldBe Some(_component_id.name)
          value.descriptor.componentName shouldBe Some(_component_id.name)
          value.descriptor.componentId shouldBe Some(_component_id)
          value.descriptor.copy(
            name = source.name,
            componentName = source.componentName,
            componentId = source.componentId
          ) shouldBe source
          value.notices.map(_.alias) shouldBe
            (if (alias == _component_id.name) Vector.empty else Vector(alias))
        }
      }
    }

      "E2 reject a conflicting legacy descriptor field after compatibility comparison" must _e2 {
      "when only one field denotes the expected canonical component" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R4,R9; Example: E2 descriptor-field disagreement")
        val source = _legacy_descriptor("textus-user-account", "Other")
        When("the expected-bound adapter evaluates every legacy field")
        val result = ComponentIdentityCompatibilityAdapter.projectDescriptorC(source, _component_id)
        Then("the conflicting descriptor field is rejected after the compatible field is admitted")
        _assert_failure(
          result,
          "component.identity.compatibility.descriptor-field.rejected: field=componentName; expected=org.simplemodeling.textus.UserAccount; actual=Other"
        )
        result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include (
          "component.identity.compatibility.unsupported"
        )
      }
    }

      "E3 preserve an exact strict schema-3 descriptor" must _e3 {
      "when its authored identity equals the owning expected identity" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R1,R5; Example: E3 strict canonical preservation")
        val source = _canonical_descriptor(_component_id)
        When("the adapter revalidates rather than rewrites schema 3")
        val result = ComponentIdentityCompatibilityAdapter.projectDescriptorC(source, _component_id)
        Then("the exact descriptor is preserved without a compatibility notice")
        result.toOption shouldBe Some(ComponentIdentityCompatibilityAdapter.DescriptorProjection(source, Vector.empty))
      }
    }

      "E4 reject a foreign strict schema-3 descriptor" must _e4 {
      "when its canonical identity differs from the owning expected identity" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R1,R5; Example: E4 strict canonical disagreement")
        val source = _canonical_descriptor(ComponentId("org.other.UserAccount"))
        When("the adapter compares exact canonical identities")
        val result = ComponentIdentityCompatibilityAdapter.projectDescriptorC(source, _component_id)
        Then("the foreign namespace is rejected")
        _assert_failure(
          result,
          "component.identity.compatibility.descriptor-id.mismatch: expected=org.simplemodeling.textus.UserAccount; actual=org.other.UserAccount"
        )
      }
      }
    }

    "assembly and static repository behavior (E5-E6,E8)" which {
      "E5 admit one legacy descriptor override at the actual assembly boundary" must _e5 {
      "when a typed binding owns the expected canonical identity" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R2,R9,R10; Example: E5 assembly override projection")
        val descriptor = _assembly_descriptor(Vector(_legacy_descriptor("textus-user-account", "UserAccount")))
        When("SubsystemAssemblyAdmission resolves the static override closure")
        val result = SubsystemAssemblyAdmission.resolveWithNoticesC(descriptor, Vector.empty)
        Then("the override enters assembly state through canonical descriptor projection")
        result shouldBe a[Consequence.Success[_]]
        val admitted = result.toOption.get
        val projected = admitted.descriptor.componentDescriptorOverrides.head
        projected.name shouldBe Some(_component_id.name)
        projected.componentName shouldBe Some(_component_id.name)
        projected.componentId shouldBe Some(_component_id)
        projected.schemaVersion shouldBe Some(2)
        projected.componentStyleSnapshot shouldBe Some(_component_style_snapshot)
        admitted.descriptor.componentDescriptorOverrides should have size 1
        admitted.notices.map(_.alias) shouldBe Vector("textus-user-account", "UserAccount")
      }
    }

      "E6 ignore one legacy configured-repository descriptor at the actual assembly boundary" must _e6 {
      "when static lookup starts from one exact typed ComponentId" in {
        Given("a packaged expanded CAR directory containing a schema-2 legacy descriptor")
        val root = _work_root.resolve("legacy-repository")
        val descriptorpath = root.resolve("legacy.car.d/component-descriptor.json")
        Files.createDirectories(descriptorpath.getParent)
        val componentdir = descriptorpath.getParent.resolve("component")
        Files.createDirectories(componentdir)
        Files.write(componentdir.resolve("main.jar"), Array.emptyByteArray)
        Files.writeString(
          descriptorpath,
          """{"name":"org.simplemodeling.textus.UserAccount","version":"0.1.0","component":{"name":"org.simplemodeling.textus.UserAccount"}}""",
          StandardCharsets.UTF_8
        )
        val repositories = Vector(ComponentRepository.ComponentDirRepository.Specification(root))
        When("SubsystemAssemblyAdmission asks the packaged static repository for descriptor aliases")
        val result = SubsystemAssemblyAdmission.resolveC(_assembly_descriptor(Vector.empty), repositories)
        Then("the packaged static repository ignores the schema-2 descriptor without projecting it into canonical closure")
        result shouldBe a[Consequence.Success[_]]
        result.toOption shouldBe defined
        val resolved = result.toOption.get
        resolved.componentDescriptorOverrides shouldBe empty
        resolved.componentBindings.head.componentId shouldBe Some(_component_id)
        resolved.componentBindings.head.componentVersion shouldBe Some("0.1.0")
      }
    }

      "E8 adapt a bare assembly binding from a packaged schema-3 repository" must _e8 {
      "when static candidate discovery starts from one bare binding" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R2,R9; Example: E8 repository static candidate discovery")
        val root = _work_root.resolve("canonical-candidate-repository")
        val cardir = root.resolve("user-account.car")
        val descriptorpath = cardir.resolve("component-descriptor.json")
        Files.createDirectories(cardir.resolve("component"))
        Files.writeString(
          descriptorpath,
          """{"name":"org.simplemodeling.textus.UserAccount","version":"0.1.0","schemaVersion":3,"component":{"namespace":"org.simplemodeling.textus","id":"UserAccount","version":"0.1.0"}}""",
          StandardCharsets.UTF_8
        )
        Files.write(cardir.resolve("component").resolve("main.jar"), Array.emptyByteArray)
        val repositories = Vector(ComponentRepository.ComponentDirRepository.Specification(root))
        val assembly = GenericSubsystemDescriptor(
          path = Path.of("phase-56-cid06b-static-repository"),
          subsystemName = "component-descriptor-compatibility",
          componentBindings = Vector(GenericSubsystemComponentBinding("UserAccount", version = Some("0.1.0"), instance = Some("primary")))
        )
        When("SubsystemAssemblyAdmission resolves the packaged repository through static candidates")
        val result = SubsystemAssemblyAdmission.resolveWithNoticesC(assembly, repositories)
        Then("the unique bare binding becomes canonical and retains one compatibility notice")
        result.toOption.map(_.descriptor.componentBindings.head.componentName) shouldBe
          Some(_component_id.name)
        result.toOption.map(_.descriptor.componentBindings.head.componentId) shouldBe
          Some(Some(_component_id))
        result.toOption.map(_.notices.map(_.alias)) shouldBe Some(Vector("UserAccount"))
        result.toOption.map(_.descriptor.componentDescriptorOverrides.head.requireCanonicalIdentityC.toOption.map(_._1)) shouldBe
          Some(Some(_component_id))
      }
    }
    }

    "decoder behavior (E7)" which {
      "E7 adapt legacy decoder output at canonical projection" must _e7 {
      "when legacy decoder output reaches an expected identity boundary" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R5,R9; Example: E7 expected-bound decoder projection")
        val record = Record.data(
          "name" -> "textus-user-account",
          "version" -> "0.1.0",
          "component" -> Record.data("name" -> "UserAccount")
        )
        When("the ordinary legacy RecordDecoder reads the descriptor")
        val result = summon[RecordDecoder[ComponentDescriptor]].fromRecord(record)
        Then("ordinary decoding may preserve data while canonical projection admits the expected identity")
        val projection = result.flatMap(ComponentIdentityCompatibilityAdapter.projectDescriptorC(_, _component_id))
        projection shouldBe a[Consequence.Success[_]]
        val value = projection.toOption.get
        value.descriptor.name shouldBe Some(_component_id.name)
        value.descriptor.componentName shouldBe Some(_component_id.name)
        value.descriptor.componentId shouldBe Some(_component_id)
        value.notices.map(_.aliaskind) shouldBe Vector(
          ComponentIdentityCompatibilityAdapter.AliasKind.Artifact,
          ComponentIdentityCompatibilityAdapter.AliasKind.Bare
        )
      }
      }
    }
  }

  private def _assembly_descriptor(overrides: Vector[ComponentDescriptor]): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      path = Path.of("phase-56-cid06b"),
      subsystemName = "component-descriptor-compatibility",
      componentBindings = Vector(
        GenericSubsystemComponentBinding(
          _component_id.name,
          version = Some("0.1.0"),
          componentId = Some(_component_id)
        )
      ),
      componentDescriptorOverrides = overrides
    )

  private def _legacy_descriptor(name: String, componentname: String): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(name),
      version = Some("0.1.0"),
      componentName = Some(componentname),
      componentlets = Vector(ComponentletDescriptor(
        name = "user-account-admin",
        kind = Some("componentlet"),
        isPrimary = Some(false),
        archiveScope = Some("car-bundled"),
        implementationClass = Some("org.simplemodeling.textus.UserAccountAdmin"),
        factoryObject = Some("org.simplemodeling.textus.UserAccountAdmin"),
        extensions = Map("ui" -> "admin"),
        config = Map("mode" -> "readonly")
      )),
      entityRuntimeDescriptors = Vector(EntityRuntimeDescriptor(
        entityName = "UserAccount",
        collectionId = EntityCollectionId("sys", "identity", "UserAccount"),
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 64,
        maxEntitiesPerPartition = 10000
      )),
      extensionBindings = Record.data("admin" -> "user-account-admin"),
      extensions = Map("extension" -> "identity"),
      config = Map("provider.mode" -> "compatibility"),
      schemaVersion = Some(2),
      componentStyleSnapshot = Some(_component_style_snapshot)
    )

  private def _canonical_descriptor(componentid: ComponentId): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some("0.1.0"),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    )

  private def _assert_failure[A](result: Consequence[A], message: String): Unit =
    result match {
      case Consequence.Failure(conclusion) => conclusion.show should include (message)
      case Consequence.Success(_) => fail(s"expected failure containing: $message")
    }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_))
      } finally {
        stream.close()
      }
    }
}
