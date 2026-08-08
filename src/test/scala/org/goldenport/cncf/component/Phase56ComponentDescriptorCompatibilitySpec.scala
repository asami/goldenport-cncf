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
 * @version Aug.  8, 2026
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
    "direct projection behavior (E1-E4)" which {
      "E1 project every accepted legacy spelling through one expected ComponentId" must _e1 {
      "when a legacy descriptor carries two compatible presentation spellings" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R2,R9,R10; Example: E1 expected-bound descriptor projection")
        val spellings = Vector(_component_id.name, "UserAccount", "user-account", "textus-user-account")
        When("the central adapter projects each accepted descriptor spelling against its owning canonical identity")
        val projections = spellings.map { alias =>
          val source = _legacy_descriptor(alias, alias)
          val projection = ComponentIdentityCompatibilityAdapter.projectDescriptorC(source, _component_id).toOption.get
          alias -> (source, projection)
        }
        Then("only in-memory identity fields change while every source metadata field is preserved")
        projections.foreach { case (_, (source, projection)) =>
          projection.descriptor.name shouldBe Some(_component_id.name)
          projection.descriptor.componentName shouldBe Some(_component_id.name)
          projection.descriptor.componentId shouldBe Some(_component_id)
          projection.descriptor.copy(
            name = source.name,
            componentName = source.componentName,
            componentId = source.componentId
          ) shouldBe source
        }
      }
    }

      "E2 reject disagreement between legacy descriptor identity fields" must _e2 {
      "when only one field denotes the expected canonical component" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R4,R9; Example: E2 descriptor-field disagreement")
        val source = _legacy_descriptor("textus-user-account", "Other")
        When("the expected-bound adapter evaluates every legacy field")
        val result = ComponentIdentityCompatibilityAdapter.projectDescriptorC(source, _component_id)
        Then("the disagreeing componentName is identified without namespace inference")
        _assert_failure(
          result,
          "component.identity.compatibility.descriptor-field.rejected: field=componentName; expected=org.simplemodeling.textus.UserAccount; actual=Other"
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
      "E5 project one legacy descriptor override at the actual assembly boundary" must _e5 {
      "when a typed binding owns the expected canonical identity" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R2,R9,R10; Example: E5 assembly override projection")
        val descriptor = _assembly_descriptor(Vector(_legacy_descriptor("textus-user-account", "UserAccount")))
        When("SubsystemAssemblyAdmission resolves the static override closure")
        val result = SubsystemAssemblyAdmission.resolveC(descriptor, Vector.empty)
        Then("the override enters assembly state with one typed canonical identity")
        val projected = result.toOption.get.componentDescriptorOverrides.head
        projected.componentId shouldBe Some(_component_id)
        projected.name shouldBe Some(_component_id.name)
        projected.componentName shouldBe Some(_component_id.name)
        projected.schemaVersion shouldBe Some(2)
        projected.componentStyleSnapshot shouldBe Some(_component_style_snapshot)
      }
    }

      "E6 project one legacy configured-repository descriptor at the actual assembly boundary" must _e6 {
      "when static lookup starts from one exact typed ComponentId" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R2,R9; Example: E6 repository static projection")
        val root = _work_root.resolve("legacy-repository")
        val descriptorpath = root.resolve("target/cncf.d/component-descriptor.json")
        Files.createDirectories(descriptorpath.getParent)
        Files.writeString(
          descriptorpath,
          """{"name":"textus-user-account","version":"0.1.0","component":{"name":"UserAccount"}}""",
          StandardCharsets.UTF_8
        )
        val repositories = Vector(ComponentRepository.ComponentDevDirRepository.Specification(root))
        When("SubsystemAssemblyAdmission asks the configured repository for bounded descriptor aliases")
        val result = SubsystemAssemblyAdmission.resolveC(_assembly_descriptor(Vector.empty), repositories)
        Then("the discovered legacy descriptor is projected to the exact expected identity")
        val projected = result.toOption.flatMap(_.componentDescriptorOverrides.headOption)
        projected.flatMap(_.name) shouldBe Some(_component_id.name)
        projected.flatMap(_.componentName) shouldBe Some(_component_id.name)
        projected.flatMap(_.componentId) shouldBe Some(_component_id)
      }
    }

      "E8 discover a canonical schema-3 dev descriptor from one bare assembly binding" must _e8 {
      "when static candidate discovery starts from one bare binding" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R2,R9; Example: E8 repository static candidate discovery")
        val root = _work_root.resolve("canonical-candidate-repository")
        val descriptorpath = root.resolve("target/cncf.d/component-descriptor.json")
        Files.createDirectories(descriptorpath.getParent)
        Files.writeString(
          descriptorpath,
          """{"name":"org.simplemodeling.textus.UserAccount","version":"0.1.0","schemaVersion":3,"component":{"namespace":"org.simplemodeling.textus","id":"UserAccount","version":"0.1.0"}}""",
          StandardCharsets.UTF_8
        )
        val repositories = Vector(ComponentRepository.ComponentDevDirRepository.Specification(root))
        val assembly = GenericSubsystemDescriptor(
          path = Path.of("phase-56-cid06b-static-repository"),
          subsystemName = "component-descriptor-compatibility",
          componentBindings = Vector(GenericSubsystemComponentBinding("UserAccount", instance = Some("primary")))
        )
        When("SubsystemAssemblyAdmission resolves the configured dev repository through static candidates")
        val result = SubsystemAssemblyAdmission.resolveC(assembly, repositories)
        Then("the binding, instance, and discovered descriptor retain the canonical identity")
        val resolved = result.toOption.get
        val binding = resolved.componentBindings.head
        binding.componentName shouldBe _component_id.name
        binding.componentId shouldBe Some(_component_id)
        binding.canonicalInstanceId shouldBe Some(ComponentInstanceId(_component_id, "primary"))
        val discovered = resolved.componentDescriptorOverrides.headOption
        discovered.flatMap(_.schemaVersion) shouldBe Some(3)
        discovered.flatMap(_.name) shouldBe Some(_component_id.name)
        discovered.flatMap(_.componentName) shouldBe Some(_component_id.name)
        discovered.flatMap(_.componentId) shouldBe Some(_component_id)
        discovered.flatMap(_.version) shouldBe Some("0.1.0")
      }
    }
    }

    "decoder behavior (E7)" which {
      "E7 keep unbound legacy decoding untyped" must _e7 {
      "when no owning boundary supplies an expected ComponentId" in {
        Given("Spec: docs/notes/phase-56-cid06b-component-descriptor-compatibility-plan.md; Rules: CID06-R5,R9; Example: E7 no decoder inference")
        val record = Record.data(
          "name" -> "textus-user-account",
          "version" -> "0.1.0",
          "component" -> Record.data("name" -> "UserAccount")
        )
        When("the ordinary legacy RecordDecoder reads the descriptor")
        val result = summon[RecordDecoder[ComponentDescriptor]].fromRecord(record)
        Then("presentation fields survive but no namespace-qualified identity is invented")
        result.toOption.flatMap(_.componentId) shouldBe None
        result.toOption.flatMap(_.name) shouldBe Some("textus-user-account")
        result.toOption.flatMap(_.componentName) shouldBe Some("UserAccount")
      }
      }
    }
  }

  private def _assembly_descriptor(overrides: Vector[ComponentDescriptor]): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      path = Path.of("phase-56-cid06b"),
      subsystemName = "component-descriptor-compatibility",
      componentBindings = Vector(
        GenericSubsystemComponentBinding(_component_id.name, componentId = Some(_component_id))
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
