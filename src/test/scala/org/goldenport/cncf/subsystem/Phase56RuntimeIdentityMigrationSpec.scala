package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentDescriptorLoader, ComponentId, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.component.ComponentDescriptor.given
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.config.ComponentParameterContext
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.record.{Record, RecordDecoder}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  7, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56RuntimeIdentityMigrationSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E1, rules:CID05-B1, phase:56, slice:CID-05B"
  )
  private val _e2 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E2, rules:CID05-B1, phase:56, slice:CID-05B"
  )
  private val _e3 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E3, rules:CID05-B2, phase:56, slice:CID-05B"
  )
  private val _e4 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E4, rules:CID05-B3, phase:56, slice:CID-05B"
  )
  private val _e5 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E5, rules:CID05-B4,B5, phase:56, slice:CID-05B"
  )
  private val _e6 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E6, rules:CID05-B6, phase:56, slice:CID-05B"
  )
  private val _e7 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E7, rules:CID05-B4, phase:56, slice:CID-05B"
  )
  private val _e8 = afterWord(
    "in spec:phase-56-runtime-identity-migration, example:E8, rules:CID05D-R5, phase:56, slice:CID-05D"
  )

  "Phase 56 runtime identity migration" should {
    "E1 decode schema 3 as the exact qualified ComponentId and release" must _e1 {
      "when a descriptor supplies only its canonical component object" in {
        Given("a schema 3 descriptor for org.alpha.textus.Shared release 2026.08-rc_1+portable")
        val record = _canonical_record("org.alpha.textus", "Shared", "2026.08-rc_1+portable")

        When("the component descriptor decoder materializes its identity")
        val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(record).toOption

        Then("the namespace-qualified ComponentId and exact release are retained without legacy roots")
        descriptor.flatMap(_.componentId) shouldBe Some(ComponentId("org.alpha.textus.Shared"))
        descriptor.flatMap(_.version) shouldBe Some("2026.08-rc_1+portable")
        descriptor.exists(_.isCanonicalIdentity) shouldBe true
      }

      "when a manually constructed schema 3 descriptor supplies an invalid opaque release token" in {
        Given("a typed canonical identity with a slash-bearing release")
        val descriptor = _canonical_descriptor(ComponentId("org.alpha.textus.Shared"))
          .copy(version = Some("invalid/release"))

        When("archive identity admission revalidates the constructor values")
        val result = descriptor.requireCanonicalIdentityC

        Then("the shared portable release contract rejects the token")
        _assert_failure(
          result,
          "component.identity.release.format: release must be a portable opaque release token: invalid/release"
        )
      }

      "when direct and copied canonical descriptors retain their legacy projections" in {
        Given("a direct schema 3 descriptor and copies with independently divergent legacy projections")
        val canonical = _canonical_descriptor(ComponentId("org.alpha.textus.Shared"))
        val wrongname = canonical.copy(name = Some("Other"))
        val wrongcomponentname = canonical.copy(componentName = Some("Other"))

        When("canonical admission validates direct construction and copy values")
        val valid = canonical.requireCanonicalIdentityC
        val nameresult = wrongname.requireCanonicalIdentityC
        val componentnameresult = wrongcomponentname.requireCanonicalIdentityC

        Then("the valid descriptor is admitted and each divergence retains its stable diagnostic")
        valid.toOption.map(_._1) shouldBe Some(ComponentId("org.alpha.textus.Shared"))
        _assert_failure(nameresult, "canonical component identity disagrees with legacy root name")
        _assert_failure(componentnameresult, "canonical component identity disagrees with legacy root componentName")
      }
    }

    "E2 reject noncanonical schema 3 identity shapes with stable diagnostics" must _e2 {
      "reject a missing component object" in {
        Given("a schema 3 descriptor without component")
        val record = Record.data("schemaVersion" -> 3)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the canonical object requirement is reported")
        _assert_failure(result, "canonical component identity must be an object")
      }

      "reject a null component object" in {
        Given("the Record representation collapses a null component field before decoder admission")
        val record = Record.data("schemaVersion" -> 3, "component" -> null)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the canonical object requirement is reported")
        _assert_failure(result, "canonical component identity must be an object")
      }

      "reject a string component object" in {
        Given("a schema 3 descriptor with a bare component string")
        val record = Record.data("schemaVersion" -> 3, "component" -> "org.alpha.textus.Shared")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the canonical object requirement is reported")
        _assert_failure(result, "canonical component identity must be an object")
      }

      "reject a missing canonical namespace" in {
        Given("a canonical component object without namespace")
        val record = _canonical_record_without("namespace")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the missing namespace diagnostic is stable")
        _assert_failure(result, "canonical component identity must declare namespace")
      }

      "reject a null canonical namespace" in {
        Given("the Record representation collapses a null namespace field before decoder admission")
        val record = _canonical_record_with("namespace", null)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the namespace type diagnostic is stable")
        _assert_failure(result, "canonical component identity must declare namespace")
      }

      "reject a non-string canonical namespace" in {
        Given("a canonical component object with a numeric namespace")
        val record = _canonical_record_with("namespace", 1)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the namespace type diagnostic is stable")
        _assert_failure(result, "canonical component identity namespace must be a non-empty string")
      }

      "reject surrounding whitespace in each canonical schema 3 identity field" in {
        Given("otherwise valid canonical records with one whitespace-bearing identity field at a time")
        val namespace = _canonical_record_with("namespace", " org.alpha.textus")
        val localid = _canonical_record_with("id", "Shared ")
        val version = _canonical_record_with("version", " 0.6.0")

        When("each record is decoded without canonical normalization")
        val namespaceresult = _decode(namespace)
        val idresult = _decode(localid)
        val versionresult = _decode(version)

        Then("each field is rejected by its field-specific canonical diagnostic")
        _assert_failure(namespaceresult, "canonical component identity namespace must be a non-empty string")
        _assert_failure(idresult, "canonical component identity id must be a non-empty string")
        _assert_failure(versionresult, "canonical component identity version must be a non-empty string")
      }

      "reject surrounding whitespace in an assembly component declaration" in {
        Given("a real assembly descriptor with one whitespace-bearing component name")
        _with_scenario_directory("assembly-whitespace") { directory =>
          val path = directory.resolve("assembly.yaml")
          Files.writeString(path, "subsystem: whitespace\ncomponents:\n  - name: ' org.alpha.textus.Shared'\n", StandardCharsets.UTF_8)

          When("the generic subsystem descriptor loader decodes the assembly binding")
          val result = GenericSubsystemDescriptor.load(path)

          Then("the binding identity is rejected without trimming")
          _assert_failure(result, "component binding name must be a nonempty string without surrounding whitespace")
        }
      }

      "reject a missing canonical id" in {
        Given("a canonical component object without id")
        val record = _canonical_record_without("id")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the missing id diagnostic is stable")
        _assert_failure(result, "canonical component identity must declare id")
      }

      "reject a null canonical id" in {
        Given("the Record representation collapses a null id field before decoder admission")
        val record = _canonical_record_with("id", null)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the id type diagnostic is stable")
        _assert_failure(result, "canonical component identity must declare id")
      }

      "reject a non-string canonical id" in {
        Given("a canonical component object with a numeric id")
        val record = _canonical_record_with("id", 1)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the id type diagnostic is stable")
        _assert_failure(result, "canonical component identity id must be a non-empty string")
      }

      "reject a missing canonical version" in {
        Given("a canonical component object without version")
        val record = _canonical_record_without("version")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the missing version diagnostic is stable")
        _assert_failure(result, "canonical component identity must declare version")
      }

      "reject a null canonical version" in {
        Given("the Record representation collapses a null version field before decoder admission")
        val record = _canonical_record_with("version", null)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the version type diagnostic is stable")
        _assert_failure(result, "canonical component identity must declare version")
      }

      "reject a non-string canonical version" in {
        Given("a canonical component object with a numeric version")
        val record = _canonical_record_with("version", 1)
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the version type diagnostic is stable")
        _assert_failure(result, "canonical component identity version must be a non-empty string")
      }

      "reject an invalid canonical namespace" in {
        Given("a canonical component object with an invalid namespace segment")
        val record = _canonical_record("org.alpha.123", "Shared", "0.6.0")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the qualified namespace diagnostic is stable")
        _assert_failure(result, "component.identity.namespace.segment-format: invalid namespace segment: 123")
      }

      "reject an invalid canonical id" in {
        Given("a canonical component object with a lowercase local id")
        val record = _canonical_record("org.alpha.textus", "shared", "0.6.0")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the qualified local id diagnostic is stable")
        _assert_failure(result, "component.identity.local-id.format: invalid component local ID: shared")
      }

      "reject an invalid canonical version" in {
        Given("a canonical component object with a non-portable opaque release token")
        val record = _canonical_record("org.alpha.textus", "Shared", "invalid/release")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the release diagnostic is stable")
        _assert_failure(result, "component.identity.release.format: release must be a portable opaque release token: invalid/release")
      }

      "reject an unknown canonical identity field" in {
        Given("a canonical component object with an undeclared alias")
        val record = _canonical_record_with("alias", "Shared")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the unknown field diagnostic is stable")
        _assert_failure(result, "canonical component identity has unknown field: alias")
      }

      "reject legacy root name disagreement" in {
        Given("a canonical component identity and a different legacy root name")
        val record = _canonical_record_with_root("name", "Other")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the name disagreement diagnostic is stable")
        _assert_failure(result, "canonical component identity disagrees with legacy root name")
      }

      "reject legacy root componentName disagreement" in {
        Given("a canonical component identity and a different legacy root componentName")
        val record = _canonical_record_with_root("componentName", "Other")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the componentName disagreement diagnostic is stable")
        _assert_failure(result, "canonical component identity disagrees with legacy root componentName")
      }

      "reject legacy root version disagreement" in {
        Given("a canonical component identity and a different legacy root version")
        val record = _canonical_record_with_root("version", "0.7.0")
        When("the descriptor is decoded")
        val result = _decode(record)
        Then("the version disagreement diagnostic is stable")
        _assert_failure(result, "canonical component identity disagrees with legacy root version")
      }
    }

    "E8 select a typed assembly binding through its exact ComponentId" must _e8 {
      "when presentation and canonical component identities differ" in {
        Given("a typed Shared binding, a legacy presentation repository, and a canonical alpha repository")
        _with_scenario_directory("typed-binding-lookup") { directory =>
          val alphaid = ComponentId("org.alpha.textus.Shared")
          val legacyroot = directory.resolve("legacy")
          val alpharoot = directory.resolve("alpha")
          Files.createDirectories(legacyroot.resolve("target/cncf.d"))
          Files.createDirectories(alpharoot.resolve("target/cncf.d"))
          Files.writeString(
            legacyroot.resolve("target/cncf.d/component-descriptor.json"),
            """{"name":"Shared","version":"0.6.0"}""",
            StandardCharsets.UTF_8
          )
          Files.writeString(
            alpharoot.resolve("target/cncf.d/component-descriptor.json"),
            _canonical_json,
            StandardCharsets.UTF_8
          )
          val binding = GenericSubsystemComponentBinding(componentName = "Shared", componentId = Some(alphaid))
          val descriptor = GenericSubsystemDescriptor(
            path = directory.resolve("assembly-descriptor.yaml"),
            subsystemName = "typed-binding",
            componentBindings = Vector(binding),
            subsystemCapabilityProviders = Vector(GenericSubsystemCapabilityProviderBinding("provider", alphaid.name, Vector.empty))
          )
          val repositories = Vector(
            ComponentRepository.ComponentDevDirRepository.Specification(legacyroot),
            ComponentRepository.ComponentDevDirRepository.Specification(alpharoot)
          )

          When("the actual assembly-admission boundary resolves descriptor closure")
          val result = SubsystemAssemblyAdmission.resolveC(descriptor, repositories)

          Then("the exact qualified lookup skips the presentation descriptor and admits canonical alpha")
          result.toOption.flatMap(_.componentDescriptorOverrides.headOption).flatMap(_.componentId) shouldBe Some(alphaid)
        }
      }
    }

    "E3 admit only canonical CAR descriptors through archive loading" must _e3 {
      "admit a canonical CAR-style directory" in {
        _with_scenario_directory("e3-canonical") { directory =>
          Given("a CAR-style directory with one canonical component descriptor")
          val descriptorpath = directory.resolve("component-descriptor.json")
          Files.writeString(descriptorpath, _canonical_json, StandardCharsets.UTF_8)

          When("ComponentDescriptorLoader loads the CAR-style directory")
          val admitted = ComponentDescriptorLoader.loadArchive(directory)

          Then("the typed canonical identity and release are admitted")
          admitted.toOption.flatMap(_.componentId) shouldBe Some(ComponentId("org.alpha.textus.Shared"))
          admitted.toOption.flatMap(_.version) shouldBe Some("0.6.0")
        }
      }

      "reject a legacy-only CAR-style directory" in {
        _with_scenario_directory("e3-legacy-only") { directory =>
          Given("a CAR-style directory with a legacy-only component descriptor")
          val descriptorpath = directory.resolve("component-descriptor.json")
          Files.writeString(descriptorpath, "{\"name\":\"Shared\",\"component\":\"Shared\",\"version\":\"0.6.0\"}", StandardCharsets.UTF_8)

          When("ComponentDescriptorLoader applies CAR identity admission")
          val rejected = ComponentDescriptorLoader.loadArchive(directory)

          Then("the archive contract reports its exact canonical identity diagnostic")
          _assert_failure(
            rejected,
            s"CAR component descriptor admission failed: archive=$directory; reason=canonical component descriptor requires schemaVersion 3 namespace, id, and version"
          )
        }
      }

      "reject a CAR-style directory whose legacy root disagrees with canonical identity" in {
        _with_scenario_directory("e3-root-disagreement") { directory =>
          Given("a CAR-style descriptor whose root name disagrees with component identity")
          val descriptorpath = directory.resolve("component-descriptor.json")
          Files.writeString(
            descriptorpath,
            "{\"schemaVersion\":3,\"name\":\"Other\",\"component\":{\"namespace\":\"org.alpha.textus\",\"id\":\"Shared\",\"version\":\"0.6.0\"}}",
            StandardCharsets.UTF_8
          )

          When("ComponentDescriptorLoader loads the CAR-style directory")
          val rejected = ComponentDescriptorLoader.loadArchive(directory)

          Then("the exact root disagreement diagnostic retains descriptor provenance")
          _assert_failure(
            rejected,
            s"CAR component descriptor admission failed: archive=$directory; reason=canonical component identity disagrees with legacy root name"
          )
        }
      }
    }

    "E4 propagate a canonical descriptor into an exact default instance binding" must _e4 {
      "when a canonical component descriptor becomes an implicit subsystem descriptor" in {
        Given("a canonical component descriptor")
        val source = _decode(_canonical_record("org.alpha.textus", "Shared", "0.6.0")).toOption.get

        When("the subsystem descriptor is derived")
        val binding = GenericSubsystemDescriptor.fromComponentDescriptor(Path.of("shared.car"), source).toOption.get.componentBindings.head

        Then("the binding and default instance retain the exact qualified identity")
        binding.componentId shouldBe Some(ComponentId("org.alpha.textus.Shared"))
        binding.canonicalInstanceId shouldBe Some(ComponentInstanceId("org.alpha.textus.Shared", "default"))
      }

      "when canonical and legacy declarations each repeat one instance identity" in {
        Given("duplicate canonical bindings and normalized legacy compatibility bindings")
        val alphaid = ComponentId("org.alpha.textus.Shared")
        val canonical = Vector(
          GenericSubsystemComponentBinding(alphaid.name, instance = Some("default"), componentId = Some(alphaid)),
          GenericSubsystemComponentBinding(alphaid.name, instance = Some("default"), componentId = Some(alphaid))
        )
        val legacy = Vector(
          GenericSubsystemComponentBinding("textus-scraper", instance = Some("dynamic-playwright")),
          GenericSubsystemComponentBinding("textus_scraper", instance = Some("dynamic_playwright"))
        )

        When("descriptor decoding validates each duplicate set")
        val canonicalresult = GenericSubsystemDescriptor._validate_component_bindings_c(canonical)
        val legacyresult = GenericSubsystemDescriptor._validate_component_bindings_c(legacy)

        Then("canonical keys remain qualified and legacy keys remain bounded compatibility keys")
        _assert_failure(canonicalresult, "duplicate component instance id: org.alpha.textus.Shared@default")
        _assert_failure(legacyresult, "duplicate component instance id: legacy:textusscraper@dynamicplaywright")
      }
    }

    "E5 reject foreign namespace candidates even when local and presentation names collide" must _e5 {
      "when a canonical binding selects prototypes by Core and artifact identity" in {
        Given("alpha and beta prototypes with the same local id and presentation filename")
        val alphaid = ComponentId("org.alpha.textus.Shared")
        val betaid = ComponentId("org.beta.textus.Shared")
        val subsystem = TestComponentFactory.emptySubsystem("phase56-e5")
        val params = org.goldenport.cncf.component.ComponentCreate(subsystem, ComponentOrigin.Repository("spec"))
        val descriptor = GenericSubsystemDescriptor(
          Path.of("phase56-e5.yaml"),
          "phase56-e5",
          componentBindings = Vector(GenericSubsystemComponentBinding(alphaid.name, componentId = Some(alphaid)))
        )
        val alpha = _prototype(subsystem, alphaid, alphaid)
        val betacore = _prototype(subsystem, betaid, betaid)
        val betaartifact = _prototype(subsystem, alphaid, betaid)

        When("materializeComponentInstancesC evaluates each real prototype")
        val admitted = GenericSubsystemFactory.materializeComponentInstancesC(Vector(alpha), descriptor, params)
        val foreigncore = GenericSubsystemFactory.materializeComponentInstancesC(Vector(betacore), descriptor, params)
        val foreignartifact = GenericSubsystemFactory.materializeComponentInstancesC(Vector(betaartifact), descriptor, params)

        Then("only exact Core and artifact identity admit the alpha binding")
        admitted.toOption.map(_.map(_.core.componentId)) shouldBe Some(Vector(alphaid))
        _assert_failure(foreigncore, "canonical component binding has no exact Core/artifact identity match: org.alpha.textus.Shared")
        _assert_failure(foreignartifact, "canonical component binding has no exact Core/artifact identity match: org.alpha.textus.Shared")
      }
    }

    "E6 keep configuration target identities namespace-qualified" must _e6 {
      "when normalized and artifact aliases or a mismatched ComponentInstanceId are presented" in {
        Given("one canonical alpha descriptor and aliases that omit or change its qualified identity")
        val alphaid = ComponentId("org.alpha.textus.Shared")
        val betaid = ComponentId("org.beta.textus.Shared")
        val alpha = ComponentInstanceId.default(alphaid)
        val beta = ComponentInstanceId.default(betaid)
        val descriptor = _canonical_descriptor(alphaid)
        val exactmetadata = ComponentInstanceMetadata(alphaid.name, "default", componentId = Some(alphaid))
        val normalizedalias = ComponentInstanceMetadata("shared", "default")
        val artifactalias = ComponentInstanceMetadata(alphaid.name, "default")

        When("configuration context selection evaluates each identity source")
        val selected = ComponentParameterContext.select(alphaid, alpha, Vector(descriptor), Vector(exactmetadata))
        val normalizedrejected = ComponentParameterContext.select(alphaid, alpha, Vector(descriptor), Vector(normalizedalias))
        val artifactrejected = ComponentParameterContext.select(alphaid, alpha, Vector(descriptor), Vector(artifactalias))
        val mismatchedinstance = ComponentParameterContext.select(alphaid, beta, Vector(descriptor), Vector(exactmetadata))

        Then("only the exact qualified identity is selected")
        selected.toOption.map(_.componentInstanceId) shouldBe Some(alpha)
        _assert_failure(normalizedrejected, "component parameter context instance metadata is missing: component=org.alpha.textus.Shared, instance=default")
        _assert_failure(artifactrejected, "component parameter context instance metadata is missing: component=org.alpha.textus.Shared, instance=default")
        _assert_failure(mismatchedinstance, "component parameter context identity mismatch: component=org.alpha.textus.Shared, instance=default, expected=org.alpha.textus.Shared@default, actual=org.beta.textus.Shared@default")
      }
    }

    "E7 retain typed artifact identity independently from filename presentation" must _e7 {
      "when repository CAR admission compares canonical descriptor Core and metadata" in {
        Given("a canonical descriptor and an unrelated artifact filename")
        val alphaid = ComponentId("org.alpha.textus.Shared")
        val betaid = ComponentId("org.beta.textus.Shared")
        val descriptor = _decode(_canonical_record("org.alpha.textus", "Shared", "0.6.0")).toOption.get
        val archive = Path.of("phase56-unrelated-presentation.car")
        val metadata = Component.ArtifactMetadata(
          sourceType = "car",
          name = "presentation-does-not-name-identity.car",
          version = "0.6.0",
          componentId = Some(alphaid)
        )
        val subsystem = TestComponentFactory.emptySubsystem("phase56-e7")

        When("the repository canonical archive admission is applied")
        val admitted = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          metadata,
          Vector(_prototype(subsystem, alphaid, alphaid))
        )
        val rejected = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          metadata,
          Vector(_prototype(subsystem, betaid, betaid))
        )

        Then("the descriptor release and exact ComponentId govern admission rather than the filename")
        admitted.toOption.flatMap(_.headOption).flatMap(_.artifactMetadata).map(_.componentId) shouldBe Some(Some(alphaid))
        admitted.toOption.flatMap(_.headOption).flatMap(_.artifactMetadata).map(_.version) shouldBe Some("0.6.0")
        _assert_failure(
          rejected,
          "CAR component identity mismatch: descriptor=org.alpha.textus.Shared, primary=org.beta.textus.Shared, archive=phase56-unrelated-presentation.car"
        )
      }

      "when archive metadata and participant cardinality are invalid" in {
        Given("one canonical descriptor, a stable archive coordinate, and pre-populated participant metadata")
        val alphaid = ComponentId("org.alpha.textus.Shared")
        val betaid = ComponentId("org.beta.textus.Shared")
        val descriptor = _decode(_canonical_record("org.alpha.textus", "Shared", "0.6.0")).toOption.get
        val archive = Path.of("phase56-unrelated-presentation.car")
        val subsystem = TestComponentFactory.emptySubsystem("phase56-e7-cardinality")
        val canonicalmetadata = _artifact_metadata(alphaid, "0.6.0")
        val oldmetadata = _artifact_metadata(betaid, "old-release")
        val missingprimary = _prototype(subsystem, betaid, betaid, Component.ParticipantRole.Componentlet)
        val firstprimary = _prototype(subsystem, alphaid, betaid)
        val secondprimary = _prototype(subsystem, alphaid, betaid)
        val foreignprimary = _prototype(subsystem, betaid, betaid)
        val bundleprimary = _prototype(subsystem, alphaid, betaid)
        val bundlecomponentlet = _prototype(subsystem, betaid, betaid, Component.ParticipantRole.Componentlet)
        val metadataidparticipant = _prototype(subsystem, alphaid, betaid)
        val metadatareleaseparticipant = _prototype(subsystem, alphaid, betaid)

        When("repository admission evaluates metadata before mutating and then validates primary participants")
        val metadataid = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          _artifact_metadata(betaid, "0.6.0"),
          Vector(metadataidparticipant)
        )
        val metadatarelease = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          _artifact_metadata(alphaid, "0.6.1"),
          Vector(metadatareleaseparticipant)
        )
        val zero = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          canonicalmetadata,
          Vector(missingprimary)
        )
        val ambiguous = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          canonicalmetadata,
          Vector(firstprimary, secondprimary)
        )
        val foreign = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          canonicalmetadata,
          Vector(foreignprimary)
        )
        val bundled = ComponentRepository.admitCanonicalArchiveComponentsC(
          descriptor,
          archive,
          canonicalmetadata,
          Vector(bundleprimary, bundlecomponentlet)
        )

        Then("each failed admission preserves pre-existing metadata with exact diagnostics")
        _assert_failure(metadataid, "CAR component metadata component ID mismatch: descriptor=org.alpha.textus.Shared, metadata=org.beta.textus.Shared, archive=phase56-unrelated-presentation.car")
        _assert_failure(metadatarelease, "CAR component metadata release mismatch: descriptor=0.6.0, metadata=0.6.1, archive=phase56-unrelated-presentation.car")
        _assert_failure(zero, "CAR component primary participant is missing: descriptor=org.alpha.textus.Shared, archive=phase56-unrelated-presentation.car")
        _assert_failure(ambiguous, "CAR component primary participant is ambiguous: descriptor=org.alpha.textus.Shared, primary-count=2, archive=phase56-unrelated-presentation.car")
        _assert_failure(foreign, "CAR component identity mismatch: descriptor=org.alpha.textus.Shared, primary=org.beta.textus.Shared, archive=phase56-unrelated-presentation.car")
        metadataidparticipant.artifactMetadata shouldBe Some(oldmetadata)
        metadatareleaseparticipant.artifactMetadata shouldBe Some(oldmetadata)
        missingprimary.artifactMetadata shouldBe Some(oldmetadata)
        firstprimary.artifactMetadata shouldBe Some(oldmetadata)
        secondprimary.artifactMetadata shouldBe Some(oldmetadata)
        foreignprimary.artifactMetadata shouldBe Some(oldmetadata)

        And("a foreign componentlet remains an allowed bundle participant after primary validation")
        bundled.toOption.map(_.map(_.core.componentId)) shouldBe Some(Vector(alphaid, betaid))
        bundleprimary.artifactMetadata shouldBe Some(canonicalmetadata)
        bundlecomponentlet.artifactMetadata shouldBe Some(canonicalmetadata)
      }
    }
  }

  private def _decode(record: Record): Consequence[ComponentDescriptor] =
    summon[RecordDecoder[ComponentDescriptor]].fromRecord(record)

  private def _canonical_record(namespace: String, id: String, version: String): Record =
    Record.data(
      "schemaVersion" -> 3,
      "component" -> Record.data(
        "namespace" -> namespace,
        "id" -> id,
        "version" -> version
      )
    )

  private def _canonical_record_without(field: String): Record =
    Record.data(
      "schemaVersion" -> 3,
      "component" -> Record.create(
        Map[String, Any](
          "namespace" -> "org.alpha.textus",
          "id" -> "Shared",
          "version" -> "0.6.0"
        ) - field
      )
    )

  private def _canonical_record_with(field: String, value: Any): Record = {
    val identity = Map[String, Any](
      "namespace" -> "org.alpha.textus",
      "id" -> "Shared",
      "version" -> "0.6.0"
    ) + (field -> value)
    Record.data("schemaVersion" -> 3, "component" -> Record.create(identity))
  }

  private def _canonical_record_with_root(field: String, value: String): Record =
    Record.data(
      "schemaVersion" -> 3,
      field -> value,
      "component" -> Record.data(
        "namespace" -> "org.alpha.textus",
        "id" -> "Shared",
        "version" -> "0.6.0"
      )
    )

  private def _canonical_descriptor(componentid: ComponentId): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some("0.6.0"),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    )

  private def _prototype(
    subsystem: Subsystem,
    coreid: ComponentId,
    artifactid: ComponentId,
    participantrole: Component.ParticipantRole = Component.ParticipantRole.Primary
  ): Component = {
    val component = new Component() {}
    component.initialize(
      ComponentInit(
        subsystem,
        Component.Core.create(
          coreid.name,
          coreid,
          ComponentInstanceId.default(coreid),
          Protocol.empty
        ),
        ComponentOrigin.Repository("spec"),
        Vector.empty,
        participantrole
      )
    )
    component.withArtifactMetadata(
      _artifact_metadata(artifactid, "old-release")
    )
  }

  private def _artifact_metadata(
    componentid: ComponentId,
    release: String
  ): Component.ArtifactMetadata =
    Component.ArtifactMetadata(
      sourceType = "spec",
      name = "shared.car",
      version = release,
      component = Some("Shared"),
      componentId = Some(componentid)
    )

  private def _assert_failure[A](result: Consequence[A], expected: String): Unit =
    result match {
      case Consequence.Failure(conclusion) => conclusion.display shouldBe expected
      case Consequence.Success(_) => fail(s"expected failure: $expected")
    }

  private def _with_scenario_directory[A](scenario: String)(f: Path => A): A = {
    val directory = Path.of(
      "target",
      "cncf-test",
      "work",
      "phase56-runtime-identity-migration-spec",
      scenario
    )
    _delete_tree(directory)
    Files.createDirectories(directory)
    try f(directory)
    finally _delete_tree(directory)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
      finally stream.close()
    }

  private val _canonical_json =
    """{"schemaVersion":3,"component":{"namespace":"org.alpha.textus","id":"Shared","version":"0.6.0"}}"""
}
