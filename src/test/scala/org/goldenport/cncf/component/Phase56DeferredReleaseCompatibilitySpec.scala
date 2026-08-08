package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentIdentityDeferredReleaseRegistry.*
import org.goldenport.cncf.component.testutil.LegacyDeferredReleaseCarFixture
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, GlobalRuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}

/*
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56DeferredReleaseCompatibilitySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  private def _metadata(example: String) =
    afterWord(s"in spec:phase-56-deferred-release-compatibility, example:$example, rules:CID06-R5,R8,R9,R10, phase:56, slice:CID-06D")

  override def beforeAll(): Unit =
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))

  "Phase 56 exact deferred releases" should {
    "registry authority and descriptor classification" which {
    "E1 load exactly four registry entries and prove registry/ledger parity" must _metadata("E1") {
      "when the executable authority is compared with the CID-01 inventory" in {
        Given("the packaged deferred-release registry and the frozen CID-01 migration ledger")
        val ledgerpath = Path.of("docs", "notes", "phase-56-cid01-car-migration-ledger.yaml")

        When("the registry is loaded and its four deferred entries are matched to ledger blocks")
        val registry = ComponentIdentityDeferredReleaseRegistry.loadC().toOption.get
        val ledger = Files.readString(ledgerpath, StandardCharsets.UTF_8)
        val deferredblocks = ledger
          .split("\\n  - repository_name:")
          .toVector
          .filter(_.contains("classification: current-release-deferred"))
        val emptyregistry = ComponentIdentityDeferredReleaseRegistry.parseC(
          s"""{"schemaVersion":"${ComponentIdentityDeferredReleaseRegistry.SCHEMA_VERSION}","entries":[]}"""
        )
        val wrongschema = ComponentIdentityDeferredReleaseRegistry.parseC(
          """{"schemaVersion":"wrong","entries":[]}"""
        )

        Then("the four runtime entries have exact ledger parity and malformed registries fail closed")
        registry.entries should have size 4
        registry.entries.map(x => (x.componentid.name, x.release, x.legacyartifact, x.legacylocalid, x.migrationowner)) shouldBe Vector(
          ("org.simplemodeling.textus.Corpus", "0.1.0", "textus-corpus", "Corpus", "textus-corpus"),
          ("org.simplemodeling.textus.Experiment", "0.1.0", "textus-experiment", "Experiment", "textus-experiment"),
          ("org.simplemodeling.textus.GeoResolver", "0.2.1", "textus-georesolver", "GeoResolver", "textus-georesolver"),
          ("org.simplemodeling.textus.Sanpomap", "0.2.1", "textus-sanpomap", "Sanpomap", "textus-sanpomap")
        )
        deferredblocks should have size 4
        registry.entries.foreach { entry =>
          val block = deferredblocks.find(_.startsWith(s" ${entry.migrationowner}\n"))
            .getOrElse(fail(s"missing deferred ledger block for ${entry.migrationowner}"))
          block should include(s"qualified_id: ${entry.componentid.name}")
          block should include(s"current_release: ${entry.release}")
          block should include(s"owner: ${entry.migrationowner}")
          block should include(s"project_name: ${entry.legacyartifact}")
        }
        emptyregistry.toOption shouldBe empty
        wrongschema.toOption shouldBe empty
      }
    }

    "E2 project all exact descriptors without changing non-identity metadata" must _metadata("E2") {
      "when each exact legacy descriptor is classified" in {
        Given("one schema-2 legacy descriptor for every exact registry entry")
        val registry = ComponentIdentityDeferredReleaseRegistry.default
        val inputs = registry.entries.map { entry =>
          entry -> LegacyDeferredReleaseCarFixture.legacyDescriptor(entry).copy(
            subsystemName = Some("textus"),
            extensions = Map("fixture" -> "legacy"),
            config = Map("limit" -> "17")
          )
        }

        When("each descriptor is classified and projected against its registry identity")
        val projections = inputs.map { case (entry, raw) =>
          (entry, raw, registry.classifyC(raw).toOption.get)
        }

        Then("only identity fields are canonicalized and all other descriptor evidence is preserved")
        projections.foreach { case (entry, raw, classification) =>
          classification match {
            case ExactDeferred(actualentry, effective) =>
              actualentry shouldBe entry
              effective.schemaVersion shouldBe raw.schemaVersion
              effective.name shouldBe Some(entry.componentid.name)
              effective.componentName shouldBe Some(entry.componentid.name)
              effective.componentId shouldBe Some(entry.componentid)
              effective.subsystemName shouldBe raw.subsystemName
              effective.extensions shouldBe raw.extensions
              effective.config shouldBe raw.config
            case other => fail(s"expected exact deferred projection but got $other")
          }
        }
      }
    }

    "E3 classify the complete coordinate matrix deterministically" must _metadata("E3") {
      "when exact, advanced, invalid, missing, and partial coordinates are compared" in {
        Given("the Corpus entry and descriptors spanning every release and inventory class")
        val registry = ComponentIdentityDeferredReleaseRegistry.default
        val corpus = registry.entries.find(_.legacylocalid == "Corpus").get
        val legacy = LegacyDeferredReleaseCarFixture.legacyDescriptor(corpus)

        When("the descriptors are classified through the single registry decision surface")
        val exact = registry.classifyC(legacy).toOption.get
        val greater = registry.classifyC(legacy.copy(version = Some("0.1.1"))).toOption.get
        val snapshot = registry.classifyC(legacy.copy(version = Some("0.1.1-SNAPSHOT"))).toOption.get
        val lower = _inventory_reason(registry, legacy.copy(version = Some("0.0.9")))
        val malformed = _inventory_reason(registry, legacy.copy(version = Some("broken")))
        val incomparable = _inventory_reason(registry, legacy.copy(version = Some("0.1.0-RC1")))
        val missingrelease = _inventory_reason(registry, legacy.copy(version = None))
        val partial = _inventory_reason(registry, legacy.copy(name = Some("wrong-artifact")))
        val unregistered = registry.classifyC(_unregistered_legacy_descriptor()).toOption.get
        val missingschema = _inventory_reason(registry, legacy.copy(schemaVersion = None))
        val schemaone = _inventory_reason(registry, legacy.copy(schemaVersion = Some(1)))
        val schemafour = _inventory_reason(registry, legacy.copy(schemaVersion = Some(4)))
        val schemathree = registry.classifyC(legacy.copy(schemaVersion = Some(3))).toOption.get

        Then("only exact schema-2 releases defer, schema 3 stays strict, and unsupported schemas fail with one stable inventory diagnostic")
        exact shouldBe a[ExactDeferred]
        greater shouldBe a[MigrationRequired]
        snapshot shouldBe a[MigrationRequired]
        lower should include("lower-release")
        malformed should include("malformed-release")
        incomparable should include("incomparable-release")
        missingrelease should include("release-missing")
        partial should include("partial-match")
        unregistered shouldBe Strict
        Vector(missingschema, schemaone, schemafour).foreach { diagnostic =>
          diagnostic should include("reason=descriptor-schema-version")
          diagnostic should include("expected=2-or-3")
        }
        missingschema should include("actual=missing")
        schemaone should include("actual=1")
        schemafour should include("actual=4")
        schemathree shouldBe Strict
      }
    }
    }

    "scoped generated identity" which {
    "E4 adapt only the exact local ID in its expected scope" must _metadata("E4") {
      "when ComponentId and ComponentInstanceId parse inside and outside the scope" in {
        Given("the exact Corpus registry entry with no ambient compatibility scope")
        val entry = _corpus_entry

        When("the exact local ID and neighboring spellings are parsed outside and inside its expected scope")
        val outsidebefore = ComponentId.parseC("Corpus").toOption
        val throwingoutside = Try(ComponentId("Corpus"))
        val inside = ComponentIdentityDeferredReleaseScope.withExpected(entry) {
          val rejected = Vector("corpus", "textus-corpus", "CORPUS", "Other").map { value =>
            ComponentId.parseC(value).toOption
          }
          (
            ComponentId("Corpus"),
            ComponentId.parseC("Corpus").toOption,
            ComponentInstanceId("Corpus", "blue").componentId,
            ComponentId.parseC(entry.componentid.name).toOption,
            rejected
          )
        }
        val outsideafter = ComponentId.parseC("Corpus").toOption

        Then("only the exact local ID maps to Corpus within the scope and strict behavior is restored afterward")
        outsidebefore shouldBe empty
        throwingoutside.isFailure shouldBe true
        inside._1 shouldBe entry.componentid
        inside._2 shouldBe Some(entry.componentid)
        inside._3 shouldBe entry.componentid
        inside._4 shouldBe Some(entry.componentid)
        inside._5.foreach(_ shouldBe empty)
        outsideafter shouldBe empty
      }
    }

    "E5 restore nested scopes and isolate concurrent threads" must _metadata("E5") {
      "when nested, exceptional, and concurrent scopes complete" in {
        Given("distinct Corpus and Experiment entries plus two synchronized worker threads")
        val corpus = _corpus_entry
        val experiment = ComponentIdentityDeferredReleaseRegistry.default.entries.find(_.legacylocalid == "Experiment").get
        val ready = new CountDownLatch(2)
        val proceed = new CountDownLatch(1)
        val left = new AtomicReference[Vector[Boolean]]()
        val right = new AtomicReference[Vector[Boolean]]()

        When("nested scopes, exceptional cleanup, and concurrent thread-local scopes are exercised")
        val nested = ComponentIdentityDeferredReleaseScope.withExpected(corpus) {
          val outerbefore = ComponentId("Corpus")
          val innerresult = ComponentIdentityDeferredReleaseScope.withExpected(experiment) {
            val inner = ComponentId("Experiment")
            val hiddenouter = ComponentId.parseC("Corpus").toOption
            (inner, hiddenouter)
          }
          (outerbefore, innerresult, ComponentId("Corpus"))
        }
        val exceptional = Try {
          ComponentIdentityDeferredReleaseScope.withExpected(corpus) {
            throw new IllegalStateException("fixture")
          }
        }
        val leftthread = new Thread(() =>
          ComponentIdentityDeferredReleaseScope.withExpected(corpus) {
            ready.countDown()
            proceed.await()
            left.set(Vector(
              ComponentId.parseC("Corpus").toOption.contains(corpus.componentid),
              ComponentId.parseC("Experiment").toOption.isEmpty
            ))
          }
        )
        val rightthread = new Thread(() =>
          ComponentIdentityDeferredReleaseScope.withExpected(experiment) {
            ready.countDown()
            proceed.await()
            right.set(Vector(
              ComponentId.parseC("Experiment").toOption.contains(experiment.componentid),
              ComponentId.parseC("Corpus").toOption.isEmpty
            ))
          }
        )
        leftthread.start()
        rightthread.start()
        ready.await()
        proceed.countDown()
        leftthread.join()
        rightthread.join()

        Then("nesting restores the prior entry, failures clean the scope, and threads see only their own expected identity")
        nested._1 shouldBe corpus.componentid
        nested._2._1 shouldBe experiment.componentid
        nested._2._2 shouldBe empty
        nested._3 shouldBe corpus.componentid
        exceptional.isFailure shouldBe true
        ComponentId.parseC("Corpus").toOption shouldBe empty
        left.get() shouldBe Vector(true, true)
        right.get() shouldBe Vector(true, true)
      }
    }
    }

    "archive and repository admission" which {
    "E6 admit a packed legacy CAR through factory and canonical metadata" must _metadata("E6") {
      "when a runtime-manifest-free packed fixture is discovered" in {
        _with_temp_dir("packed") { root =>
          Given("an exact packed Corpus CAR without a runtime manifest and a packed variant whose manifest path is a directory")
          val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(root.resolve("expanded"), _corpus_entry)
          val packed = LegacyDeferredReleaseCarFixture.pack(expanded, root.resolve("textus-corpus-0.1.0.car"))
          val packednonregular = LegacyDeferredReleaseCarFixture.pack(
            expanded,
            root.resolve("textus-corpus-0.1.0-nonregular.car"),
            runtimeManifestDirectory = true
          )

          When("the valid CAR is discovered and the non-regular packed manifest path is admitted")
          val (staticdescriptor, components, warningcount) = _with_runtime("cid06d-packed") { runtime =>
            val staticdescriptor = ComponentRepository.ComponentFileRepository
              .Specification(packed)
              .resolveStaticComponentDescriptor(_corpus_entry.componentid.name)
              .getOrElse(fail("exact deferred static descriptor was not projected"))
            val components = _discover_file(packed)
            (
              staticdescriptor,
              components,
              runtime.assemblyReport.warnings.count(_.reason.contains("exact-deferred-release"))
            )
          }
          val packednonregularresult = _extract_packed(packednonregular)

          Then("the exact archive reaches canonical Core metadata while the packed directory path fails as non-regular")
          staticdescriptor.componentId shouldBe Some(_corpus_entry.componentid)
          components should have size 1
          components.head.core.componentId shouldBe _corpus_entry.componentid
          components.head.artifactMetadata.flatMap(_.componentId) shouldBe Some(_corpus_entry.componentid)
          components.head.artifactMetadata.map(_.version) shouldBe Some("0.1.0")
          warningcount shouldBe 1
          _failure_display(packednonregularresult) should include("CAR runtime manifest is not a regular file")

          Given("the expanded archive with an existing directory at the runtime-manifest path")
          Files.createDirectory(expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE))

          When("expanded runtime admission inspects that non-regular path")
          val expandednonregularresult = CarExtractor.resolveDirectory(expanded)

          Then("the expanded directory path fails with the same stable non-regular diagnostic")
          _failure_display(expandednonregularresult) should include("CAR runtime manifest is not a regular file")
          Files.delete(expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE))

          Given("the same expanded archive with corrupt present runtime JSON")
          Files.writeString(
            expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE),
            "{}",
            StandardCharsets.UTF_8
          )

          When("runtime admission reads the corrupt present manifest")
          val corruptruntimeresult = CarExtractor.resolveDirectory(expanded)

          Then("the exact deferral does not bypass corrupt present runtime evidence")
          corruptruntimeresult.toOption shouldBe empty
          Files.delete(expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE))

          Given("the runtime-manifest-free expanded archive with corrupt ABI JSON")
          Files.writeString(
            expanded.resolve(CarRuntimeAdmission.ABI_MANIFEST_FILE),
            "{}",
            StandardCharsets.UTF_8
          )

          When("runtime admission validates the required ABI evidence")
          val corruptabiresult = CarExtractor.resolveDirectory(expanded)

          Then("the missing-runtime-manifest exception still rejects corrupt ABI evidence")
          corruptabiresult.toOption shouldBe empty
        }
      }
    }

    "E7 admit the same legacy boundary from an expanded CAR directory" must _metadata("E7") {
      "when the expanded fixture is discovered" in {
        _with_temp_dir("expanded") { root =>
          Given("an exact expanded Corpus CAR without a runtime manifest")
          val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(root.resolve("textus-corpus-0.1.0.car.d"), _corpus_entry)

          When("the expanded directory is discovered through the component repository")
          val components = _with_runtime("cid06d-expanded") { _ =>
            _discover_directory(expanded)
          }

          Then("one canonical Corpus component is admitted with expanded-CAR metadata")
          components should have size 1
          components.head.core.componentId shouldBe _corpus_entry.componentid
          components.head.artifactMetadata.map(_.sourceType) shouldBe Some("car-dir")
        }
      }
    }

    "E8 reject non-exact legacy archive inputs without fallback" must _metadata("E8") {
      "when higher, SNAPSHOT, unregistered, wrong-artifact, and wrong-local descriptors are extracted" in {
        Given("legacy Corpus archives outside the exact registry coordinate and identity")
        val corpus = _corpus_entry
        val invalid = Vector(
          "higher" -> LegacyDeferredReleaseCarFixture.legacyDescriptor(corpus).copy(version = Some("0.1.1")),
          "snapshot" -> LegacyDeferredReleaseCarFixture.legacyDescriptor(corpus).copy(version = Some("0.1.1-SNAPSHOT")),
          "unregistered" -> _unregistered_legacy_descriptor(),
          "wrong-artifact" -> LegacyDeferredReleaseCarFixture.legacyDescriptor(corpus).copy(name = Some("wrong-artifact")),
          "wrong-local" -> LegacyDeferredReleaseCarFixture.legacyDescriptor(corpus).copy(componentName = Some("corpus"))
        )
        When("each archive is extracted without an exact deferred-release scope")
        val results = _with_temp_dir("negative") { root =>
          invalid.map { case (name, descriptor) =>
            val cardir = LegacyDeferredReleaseCarFixture.writeDirectory(root.resolve(name), corpus, Some(descriptor))
            name -> CarExtractor.resolveDirectory(cardir)
          }
        }

        Then("every non-exact archive is rejected and no legacy identity scope leaks")
        results.foreach { case (name, result) =>
          withClue(name) { result.toOption shouldBe empty }
        }
        ComponentId.parseC("Corpus").toOption shouldBe empty
      }
    }

    "E9 preserve ordinary strict schema-3 admission and CID-06 behavior" must _metadata("E9") {
      "when a canonical descriptor and ordinary bare IDs are evaluated" in {
        _with_temp_dir("strict") { root =>
          Given("a canonical schema-3 descriptor and one ordinary CID-06 assembly alias candidate")
          val descriptor = root.resolve("component-descriptor.json")
          Files.writeString(
            descriptor,
            """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Strict","version":"1.0.0"}}""",
            StandardCharsets.UTF_8
          )
          val candidate = ComponentId("org.example.Strict")

          When("strict descriptor loading and the bounded assembly alias adapter are evaluated")
          val canonicalresult = ComponentDescriptorLoader.loadArchive(root)
          val bareoutside = ComponentId.parseC("Strict").toOption
          val aliasresult = ComponentIdentityCompatibilityAdapter.resolve(
            "Strict",
            Vector(candidate),
            ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
          )

          Then("schema 3 remains canonical, bare ComponentId parsing remains strict, and CID-06 alias adaptation remains explicit")
          canonicalresult.toOption.get.componentId.map(_.name) shouldBe Some("org.example.Strict")
          bareoutside shouldBe empty
          aliasresult shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        }
      }
    }
    }

    "deferred-release observability" which {
    "E10 emit one deduplicated warning through the Admin report surface" must _metadata("E10") {
      "when the same admitted deferred release is observed repeatedly" in {
        Given("one runtime AssemblyReport and the exact Corpus deferred-release entry")
        _with_runtime("cid06d-report") { runtime =>
          When("the same admitted entry is observed twice and the Admin record is projected")
          ComponentIdentityCompatibilityObserver.observeDeferredRelease(runtime.assemblyReport, _corpus_entry)
          ComponentIdentityCompatibilityObserver.observeDeferredRelease(runtime.assemblyReport, _corpus_entry)
          val warning = runtime.assemblyReport.warnings.head
          val adminrecord = runtime.assemblyReport.toRecord

          Then("one complete exact-deferred-release warning is exposed through the Admin report surface")
          runtime.assemblyReport.warnings should have size 1
          warning.kind shouldBe "component-identity-deferred-release"
          warning.componentName shouldBe _corpus_entry.componentid.name
          warning.reason shouldBe Some("exact-deferred-release")
          warning.message should include("release=0.1.0")
          warning.message should include("legacy-artifact=textus-corpus")
          warning.message should include("migration-owner=textus-corpus")
          adminrecord.getString("status") shouldBe Some("warning")
          adminrecord.getInt("warningCount") shouldBe Some(1)
        }
      }
    }
    }
  }

  private def _corpus_entry: ComponentIdentityDeferredReleaseEntry =
    ComponentIdentityDeferredReleaseRegistry.default.entries.find(_.legacylocalid == "Corpus").get

  private def _unregistered_legacy_descriptor(): ComponentDescriptor =
    ComponentDescriptor(
      name = Some("textus-unregistered"),
      version = Some("0.1.0"),
      componentName = Some("Unregistered"),
      schemaVersion = Some(2)
    )

  private def _inventory_reason(
    registry: ComponentIdentityDeferredReleaseRegistry,
    descriptor: ComponentDescriptor
  ): String =
    registry.classifyC(descriptor).toOption.get match {
      case InventoryError(diagnostic) => diagnostic
      case other => fail(s"expected inventory error but got $other")
    }

  private def _discover_file(path: Path): Vector[Component] = {
    val subsystem = _subsystem("packed")
    val repository = new ComponentRepository.ComponentFileRepository(
      path,
      ComponentCreate(subsystem, ComponentOrigin.Repository("component-file")),
      Vector.empty
    )
    repository.discover().toVector
  }

  private def _discover_directory(path: Path): Vector[Component] = {
    val subsystem = _subsystem("expanded")
    val repository = new ComponentRepository.ComponentDirRepository(
      path,
      ComponentCreate(subsystem, ComponentOrigin.Repository("component-dir")),
      Vector.empty
    )
    repository.discover().toVector
  }

  private def _subsystem(name: String): Subsystem =
    new Subsystem(
      name = s"phase56-cid06d-$name",
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    )

  private def _with_runtime[A](name: String)(body: GlobalRuntimeContext => A): A = {
    val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    val runtime = GlobalRuntimeContext.create(
      name,
      RuntimeConfig.default,
      configuration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(runtime)
    try body(runtime)
    finally GlobalRuntimeContext.current = previous
  }

  private def _extract_packed(path: Path): Consequence[CarExtracted] =
    CarExtractor.withExtracted(path, GlobalContext.globalContext.workAreaSpace) { extracted =>
      Consequence.success(extracted)
    }

  private def _failure_display(result: Consequence[?]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.display
      case Consequence.Success(value) => fail(s"expected failure but got $value")
    }

  private def _with_temp_dir[A](name: String)(body: Path => A): A = {
    val base = Files.createDirectories(
      Path.of("target", "phase56-cid06d-spec", "work").toAbsolutePath.normalize
    )
    val root = Files.createTempDirectory(base, s"$name-")
    try body(root)
    finally _delete_recursively(root)
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path))
      Using.resource(Files.walk(path)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      }
}
