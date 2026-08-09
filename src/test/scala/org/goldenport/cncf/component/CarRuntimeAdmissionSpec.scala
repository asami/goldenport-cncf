package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters._
import scala.util.Try

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.testutil.LegacyDeferredReleaseCarFixture
import org.goldenport.cncf.workarea.WorkAreaSpace

final class CarRuntimeAdmissionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Packaged CAR runtime admission" should {
    "admit compatible independent runtime evidence" which {
      "activates compatible runtime, ABI, and integrity evidence without Cozy" in {
        _with_temp_dir { root =>
          Given("a packaged CAR with compatible CNCF range, ABI export, opaque provenance, and exact digests")
          val content = root.resolve("content")
          _prepare_root(
            content,
            abicomponent = "Sample"
          )
          Files.writeString(
            content.resolve("generation-provenance.json"),
            """{"schemaVersion":"opaque-and-not-evaluated"}""",
            StandardCharsets.UTF_8
          )
          _write_runtime_manifest(
            content,
            minimum = _compatible_minimum,
            excluded = Vector.empty
          )
          val archive = _zip(content, root.resolve("sample.car"))
          val workarea = WorkAreaSpace.create(RuntimeConfig.default)

          When("CNCF extracts and admits the packaged CAR")
          val admitted = CarExtractor.withExtracted(archive, workarea) { extracted =>
            Consequence.success(extracted.descriptor)
          }

          Then("activation succeeds while no Cozy implementation is available to load")
          admitted.toOption.flatMap(_.componentName) shouldBe Some(_componentid.name)
          Try(Class.forName("cozy.archive.CozyArchivePackager")).isFailure shouldBe true
        }
      }

      "admits generated compatible range evidence deterministically" in {
        Given("runtime minima below the current development runtime and arbitrary opaque payloads")
        val cases = for {
          patch <- Gen.choose(0, 1)
          payload <- Gen.nonEmptyListOf(Gen.alphaNumChar)
        } yield (s"0.5.${patch}", payload.mkString)

        When("the CNCF admission authority validates each generated CAR directory")
        val property = Prop.forAll(cases) { case (minimum, payload) =>
          _with_temp_dir { root =>
            _prepare_root(root, abicomponent = "Sample")
            Files.createDirectories(root.resolve("manual"))
            Files.writeString(
              root.resolve("manual/payload.txt"),
              payload,
              StandardCharsets.UTF_8
            )
            _write_runtime_manifest(
              root,
              minimum = minimum,
              excluded = Vector("0.4.0")
            )
            CarRuntimeAdmission.validate(_extracted(root)).toOption.nonEmpty
          }
        }

        Then("at least fifty generated compatible archives pass the same deterministic gate")
        val result = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(50),
          property
        )
        result.passed shouldBe true
      }
    }

    "admit exact deferred-release legacy ABI evidence" which {
      "admits the registered release without a runtime manifest" in {
        _with_temp_dir { root =>
          Given("the exact registered Corpus release packaged with legacy ABI v1 and no runtime manifest")
          val registry = ComponentIdentityDeferredReleaseRegistry.default
          val entry = registry.entries.find(_.legacylocalid == "Corpus").get
          val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(
            root.resolve("expanded"),
            entry
          )
          val archive = LegacyDeferredReleaseCarFixture.pack(
            expanded,
            root.resolve("textus-corpus-0.1.0.car")
          )
          val workarea = WorkAreaSpace.create(RuntimeConfig.default)

          When("CNCF extracts and validates the exact deferred-release CAR")
          val admitted = CarExtractor.withExtracted(archive, workarea) { extracted =>
            extracted.requireEffectiveIdentityC
          }

          Then("the registered canonical identity is admitted through the legacy ABI boundary")
          admitted.toOption shouldBe Some(entry.componentid -> entry.release)
        }
      }
    }

    "reject contradictory runtime evidence" which {
      "rejects an explicit packaged CAR without a runtime manifest" in {
        _with_temp_dir { root =>
          Given("an explicit CAR with a descriptor and ABI evidence but no runtime manifest")
          val content = root.resolve("content")
          _prepare_root(content, abicomponent = "Sample")
          val archive = _zip(content, root.resolve("missing-runtime-manifest.car"))
          val workarea = WorkAreaSpace.create(RuntimeConfig.default)

          When("CNCF extracts the explicit packaged CAR")
          val rejected = CarExtractor.withExtracted(archive, workarea) { _ =>
            Consequence.success(())
          }

          Then("packaged admission still rejects the missing runtime manifest")
          _failure_message(rejected) should include("CAR runtime manifest is missing")
        }
      }

      "rejects incompatible CNCF range before component loading" in {
        _with_temp_dir { root =>
          Given("a structurally valid CAR whose minimum runtime is above the executing CNCF")
          val content = root.resolve("content")
          _prepare_root(content, abicomponent = "Sample")
          _write_runtime_manifest(
            content,
            minimum = "99.0.0",
            excluded = Vector.empty
          )
          val archive = _zip(content, root.resolve("incompatible.car"))
          val workarea = WorkAreaSpace.create(RuntimeConfig.default)

          When("CNCF evaluates runtime admission")
          val rejected = CarExtractor.withExtracted(archive, workarea) { _ =>
            Consequence.success(())
          }

          Then("the runtime range fails before the caller can use extracted component code")
          _failure_message(rejected) should include("below CAR minimum")
        }
      }

      "rejects archive tampering independently of generation provenance semantics" in {
        _with_temp_dir { root =>
          Given("a CAR whose component bytes change after the runtime manifest is generated")
          val content = root.resolve("content")
          _prepare_root(
            content,
            abicomponent = "Sample"
          )
          _write_runtime_manifest(
            content,
            minimum = _compatible_minimum,
            excluded = Vector.empty
          )
          Files.writeString(
            content.resolve("component/main.jar"),
            "tampered",
            StandardCharsets.UTF_8
          )
          val archive = _zip(content, root.resolve("tampered.car"))
          val workarea = WorkAreaSpace.create(RuntimeConfig.default)

          When("CNCF verifies the packaged file set")
          val rejected = CarExtractor.withExtracted(archive, workarea) { _ =>
            Consequence.success(())
          }

          Then("the digest mismatch rejects the CAR before classloading")
          _failure_message(rejected) should include(
            "CAR integrity digest mismatch: component/main.jar"
          )
        }
      }

      "rejects ABI evidence that does not export the packaged component" in {
        _with_temp_dir { root =>
          Given("a digest-valid CAR whose ABI sidecar exports another component")
          val content = root.resolve("content")
          _prepare_root(
            content,
            abicomponent = "Other"
          )
          _write_runtime_manifest(
            content,
            minimum = _compatible_minimum,
            excluded = Vector.empty
          )
          val archive = _zip(content, root.resolve("abi-mismatch.car"))
          val workarea = WorkAreaSpace.create(RuntimeConfig.default)

          When("CNCF evaluates ABI admission")
          val rejected = CarExtractor.withExtracted(archive, workarea) { _ =>
            Consequence.success(())
          }

          Then("component identity mismatch fails independently of runtime range")
          _failure_message(rejected) should include(
            "CAR ABI manifest does not export packaged component org.goldenport.fixture.Sample"
          )
        }
      }

      "rejects runtime evidence whose artifact name differs from the canonical descriptor projection" in {
        _with_temp_dir { root =>
          Given("a digest-valid CAR with canonical descriptor and ABI evidence but a different runtime car.name")
          val content = root.resolve("content")
          _prepare_root(
            content,
            abicomponent = "Sample"
          )
          val wrongartifactname = s"${_artifactname}-wrong"
          _write_runtime_manifest(
            content,
            minimum = _compatible_minimum,
            excluded = Vector.empty,
            artifactname = wrongartifactname
          )
          val archive = _zip(content, root.resolve("artifact-name-mismatch.car"))
          val workarea = WorkAreaSpace.create(RuntimeConfig.default)

          When("CNCF evaluates the packaged runtime coordinate")
          val rejected = CarExtractor.withExtracted(archive, workarea) { _ =>
            Consequence.success(())
          }

          Then("the canonical Maven artifact projection rejects the mismatched runtime car.name")
          _failure_message(rejected) should include(
            s"CAR runtime manifest car.name mismatch: expected=${_artifactname}, actual=${wrongartifactname}"
          )
        }
      }
    }
  }

  private def _compatible_minimum: String = {
    val parts = CncfVersion.current.split("[.\\-]").toVector
    if (parts.length >= 2)
      s"${parts(0)}.${parts(1)}.0"
    else
      CncfVersion.current
  }

  private def _prepare_root(
    root: Path,
    abicomponent: String
  ): Unit = {
    val componentid = _componentid
    val release = _release
    val artifactname = _artifactname
    Files.createDirectories(root.resolve("component"))
    Files.writeString(
      root.resolve("component/main.jar"),
      "component",
      StandardCharsets.UTF_8
    )
    Files.writeString(
      root.resolve("component-descriptor.json"),
      s"""{"schemaVersion":3,"component":{"namespace":"${componentid.namespace.value()}","id":"${componentid.localId.value()}","version":"$release"}}""",
      StandardCharsets.UTF_8
    )
    Files.writeString(
      root.resolve(CarRuntimeAdmission.ABI_MANIFEST_FILE),
      s"""{
         |  "format": "${CarRuntimeAdmission.ABI_MANIFEST_FORMAT}",
         |  "component": {
         |    "namespace": "${componentid.namespace.value()}",
         |    "id": "${componentid.localId.value()}",
         |    "version": "$release"
         |  },
         |  "abi": {
         |    "version": 1,
         |    "exports": {"components": [{"namespace": "${componentid.namespace.value()}", "id": "${abicomponent}"}]},
         |    "dependencies": []
         |  }
         |}
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
  }

  private def _write_runtime_manifest(
    root: Path,
    minimum: String,
    excluded: Vector[String],
    artifactname: String = _artifactname
  ): Unit = {
    val entries = _regular_files(root)
      .filterNot(_._1 == CarRuntimeAdmission.MANIFEST_FILE)
      .map { case (relative, path) =>
        s"""{"path":${_json_string(relative)},"sha256":${_json_string(_sha256(path))}}"""
      }
      .mkString("[", ",", "]")
    val excludedjson = excluded.map(_json_string).mkString("[", ",", "]")
    Files.writeString(
      root.resolve(CarRuntimeAdmission.MANIFEST_FILE),
      s"""{
         |  "schemaVersion": "${CarRuntimeAdmission.MANIFEST_SCHEMA}",
         |  "car": {
         |    "name": "${artifactname}",
         |    "version": "${_release}",
         |    "component": "${_componentid.name}"
         |  },
         |  "runtime": {
         |    "cncf": {
         |      "minimum": ${_json_string(minimum)},
         |      "maximum": null,
         |      "excluded": ${excludedjson},
         |      "tested": [${_json_string(CncfVersion.current)}]
         |    }
         |  },
         |  "integrity": {
         |    "algorithm": "SHA-256",
         |    "entries": ${entries}
         |  }
         |}
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
  }

  private def _extracted(root: Path): CarExtracted =
    CarExtracted(
      root = root,
      descriptor = ComponentDescriptor(
        schemaVersion = Some(3),
        version = Some(_release),
        componentId = Some(_componentid)
      ),
      componentMain = root.resolve("component/main.jar"),
      componentLibs = Vector.empty,
      componentApiJars = Vector.empty,
      collaboratorMain = None,
      collaboratorLibs = Vector.empty
    )

  private def _zip(root: Path, archive: Path): Path = {
    val out = new ZipOutputStream(Files.newOutputStream(archive))
    try {
      _regular_files(root).foreach { case (relative, path) =>
        out.putNextEntry(new ZipEntry(relative))
        Files.copy(path, out)
        out.closeEntry()
      }
    } finally {
      out.close()
    }
    archive
  }

  private def _regular_files(root: Path): Vector[(String, Path)] = {
    val stream = Files.walk(root)
    try {
      stream.iterator().asScala.toVector.collect {
        case path if Files.isRegularFile(path) =>
          root.relativize(path).toString.replace('\\', '/') -> path
      }.sortBy(_._1)
    } finally {
      stream.close()
    }
  }

  private def _failure_message[A](result: Consequence[A]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.display
      case Consequence.Success(_) => fail("expected CAR runtime admission failure")
    }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).
      map(byte => f"${byte & 0xff}%02x").mkString

  private def _json_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private val _componentid = ComponentId("org.goldenport.fixture.Sample")
  private val _release = "0.0.1"
  private val _artifactname =
    ComponentReleaseCoordinate.require(_componentid.sharedIdentity, _release).mavenArtifactId()

  private def _with_temp_dir[A](body: Path => A): A = {
    val root = Files.createTempDirectory("cncf-car-runtime-admission")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(
          Files.deleteIfExists(_)
        )
      } finally {
        stream.close()
      }
    }
  }
}
