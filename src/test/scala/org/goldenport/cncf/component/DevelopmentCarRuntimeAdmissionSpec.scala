package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import scala.jdk.CollectionConverters._

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate
import org.goldenport.cncf.component.repository.ComponentRepository

/*
 * @since   Jul. 29, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class DevelopmentCarRuntimeAdmissionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E1, rules:R3,R4, phase:51, slice:RE-01")
  private val _e2_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E2, rules:R1,R6,R7, phase:51, slice:RE-01")
  private val _e3_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E3, rules:R2,R6, phase:51, slice:RE-01")
  private val _e4_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E4, rules:R3,R6, phase:51, slice:RE-01")
  private val _e5_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E5, rules:R3,R6, phase:51, slice:RE-01")
  private val _e6_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E6, rules:R1,R6, phase:51, slice:RE-01")
  private val _e7_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E7, rules:R3,R5,R6, phase:51, slice:RE-01")
  private val _e8_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E8, rules:R5,R6,R7, phase:51, slice:RE-01")
  private val _e9_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E9, rules:R3,R6, phase:51, slice:RE-01")
  private val _e10_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E10, rules:R3,R6, phase:53, slice:CS-03")
  private val _e11_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E11, rules:R2,R3, phase:53, slice:CS-03")
  private val _e12_metadata =
    afterWord("in spec:generation-compatibility-contract, example:E12, rules:R3,R6, phase:53, slice:CS-03")

  "Development CAR runtime admission" should {
    "admit stable prepared development evidence" which {
    "E1 admit stable prepared evidence after mutable class recompilation" must _e1_metadata {
    "admit stable prepared evidence" in {
      _with_temp_dir { root =>
        Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R3,R4; Example: E1; a prepared development CAR with descriptor, ABI, and a mutable class directory")
        _prepare(root)
        val classes = root.resolve("target/scala-3.3.8/classes/sample.class")
        Files.writeString(classes, "first compilation", StandardCharsets.UTF_8)
        _write_manifest(root)

        When("compiled class bytes change without changing the prepared contract evidence")
        val before = DevelopmentCarRuntimeAdmission.validate(root)
        Files.writeString(classes, "second compilation", StandardCharsets.UTF_8)
        val after = DevelopmentCarRuntimeAdmission.validate(root)

        Then("both validations admit the development source without archive-integrity semantics")
        before shouldBe Consequence.success(())
        after shouldBe Consequence.success(())
      }
    }
    }
    }

    "reject incomplete, stale, and incompatible development evidence" which {
    "E2 reject incomplete development evidence before classloading" must _e2_metadata {
    "reject incomplete development evidence" in {
      _with_temp_dir { root =>
        Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R1,R6,R7; Example: E2; a development directory with only a prepared runtime classpath")
        _prepare(root)

        When("CNCF evaluates development admission")
        val rejected = DevelopmentCarRuntimeAdmission.validate(root)

        Then("the missing manifest is reported as an admission failure")
        rejected.display should include("development runtime manifest is missing")
        rejected match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.taxonomy.category.name shouldBe "resource"
            conclusion.observation.taxonomy.symptom.name shouldBe "invalid"
            conclusion.display should include("target/cncf.d/car-runtime-manifest.json")
            conclusion.display should include("development-side runtime evidence error")
            conclusion.display should include("target/cncf.d/runtime-classpath.txt")
            conclusion.display should include("cncf launcher")
            conclusion.display should not include("s" + "bt " + "cozyPrepareRuntime")
          case Consequence.Success(_) =>
            fail("missing development evidence must not be admitted")
        }
      }
    }
    }

    "E3 reject a packaged manifest schema" must _e3_metadata {
      "reject a packaged schema" in {
        _with_prepared_manifest { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R2,R6; Example: E3; a prepared development CAR")
          val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

          When("the manifest claims the packaged schema")
          Files.writeString(manifest, Files.readString(manifest, StandardCharsets.UTF_8).replace(DevelopmentCarRuntimeAdmission.MANIFEST_SCHEMA, "cncf.car-runtime-manifest.v1"), StandardCharsets.UTF_8)
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission rejects the cross-route manifest")
          result.display should include("schemaVersion mismatch")
        }
      }
    }

    "E4 reject a manifest coordinate contradiction" must _e4_metadata {
      "reject a coordinate contradiction" in {
        _with_prepared_manifest { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R3,R6; Example: E4; a prepared development CAR")
          val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

          When("the manifest component contradicts the descriptor")
          Files.writeString(manifest, Files.readString(manifest, StandardCharsets.UTF_8).replace("\"component\":\"Sample\"", "\"component\":\"other\""), StandardCharsets.UTF_8)
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission rejects the contradictory coordinate")
          result.display should include("car.component mismatch")
        }
      }
    }

    "E5 reject an incompatible runtime range" must _e5_metadata {
      "reject an incompatible runtime range" in {
        _with_prepared_manifest { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R3,R6; Example: E5; a prepared development CAR")
          val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

          When("the minimum exceeds the executing CNCF runtime")
          Files.writeString(manifest, Files.readString(manifest, StandardCharsets.UTF_8).replace(CncfVersion.current, "99.0.0"), StandardCharsets.UTF_8)
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission rejects the unsupported runtime")
          result.display should include("below development CAR minimum")
        }
      }
    }

    "E6 reject missing classpath evidence" must _e6_metadata {
      "reject a missing classpath evidence file" in {
        _with_prepared_manifest { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R1,R6; Example: E6; a prepared development CAR")

          When("the classpath evidence file is removed")
          Files.delete(root.resolve(DevelopmentCarRuntimeAdmission.RUNTIME_CLASSPATH_IDENTITY))
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission identifies the missing evidence")
          result.display should include("development runtime evidence is missing or empty")
        }
      }
    }

    "E7 reject a deleted classpath entry" must _e7_metadata {
      "reject stale classpath evidence" in {
        _with_prepared_manifest { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R3,R5,R6; Example: E7; a prepared development CAR with one referenced classpath directory")
          val entry = root.resolve("target/scala-3.3.8/classes")

          When("the referenced classpath entry is deleted without changing evidence files")
          Files.delete(entry.resolve("sample.class"))
          Files.delete(entry)
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission identifies the stale classpath entry")
          result.display should include(entry.toString)
          result.display should include("development-side runtime evidence error")
        }
      }
    }

    "E8 normalize an invalid classpath entry" must _e8_metadata {
      "return structured recovery for malformed classpath text" in {
        _with_temp_dir { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R5,R6,R7; Example: E8; a development CAR with malformed prepared classpath text")
          _prepare(root)
          Files.writeString(root.resolve(DevelopmentCarRuntimeAdmission.RUNTIME_CLASSPATH_IDENTITY), "\u0000", StandardCharsets.UTF_8)
          _write_manifest(root)

          When("CNCF admits the malformed classpath evidence")
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("the public boundary returns a resource-invalid recovery consequence")
          result match {
            case Consequence.Failure(conclusion) =>
              conclusion.observation.taxonomy.category.name shouldBe "resource"
              conclusion.observation.taxonomy.symptom.name shouldBe "invalid"
              conclusion.display should include("runtime-classpath.txt")
              conclusion.display should include("development-side runtime evidence error")
            case Consequence.Success(_) =>
              fail("malformed classpath evidence must not be admitted")
          }
        }
      }
    }

    "E9 reject a descriptor changed after preparation" must _e9_metadata {
      "reject stale contract evidence" in {
        _with_prepared_manifest { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R3,R6; Example: E9; a prepared development CAR")
          val descriptor = root.resolve(DevelopmentCarRuntimeAdmission.COMPONENT_DESCRIPTOR_IDENTITY)

          When("the descriptor component changes without regenerating its ABI and runtime evidence")
          Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("\"id\":\"Sample\"", "\"id\":\"Other\""), StandardCharsets.UTF_8)
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission rejects the stale contract with recovery")
          result.display should include("component development ABI id mismatch")
          result.display should include("development-side runtime evidence error")
        }
      }
    }

    "E10 reject a schema-v2 development descriptor" must _e10_metadata {
      "reject legacy descriptor evidence before runtime activation" in {
        _with_temp_dir { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R3,R6; Example: E10; a prepared schema-v2 development descriptor")
          _prepare_schema2(root)
          Files.writeString(root.resolve("target/scala-3.3.8/classes/sample.class"), "compiled", StandardCharsets.UTF_8)
          _write_manifest(root, manifestname = "sample", manifestcomponent = "sample")

          When("CNCF validates the explicitly selected development directory")
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("admission rejects schema 2 with recovery guidance")
          result.display should include("component development descriptor schemaVersion mismatch: expected=3 actual=2")
          result.display should include("development-side runtime evidence error")
        }
      }
    }

    "E11 reject a digest-valid v1 development directory" must _e11_metadata {
      "preserve structured recovery without a packaged fallback" in {
        _with_temp_dir { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R2,R3; Example: E11; a prepared v1 development directory")
          _prepare(root)
          Files.writeString(root.resolve("target/scala-3.3.8/classes/sample.class"), "compiled", StandardCharsets.UTF_8)
          val legacyidentity = "src/main/car/component-descriptor.json"
          val legacy = root.resolve(legacyidentity)
          Files.createDirectories(legacy.getParent)
          Files.writeString(legacy, """{"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample"}""", StandardCharsets.UTF_8)
          Files.writeString(
            root.resolve(DevelopmentCarRuntimeAdmission.ABI_MANIFEST_IDENTITY),
            """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample","version":"0.0.1-SNAPSHOT"},"abi":{"exports":{"components":[{"name":"sample"}]}}}""",
            StandardCharsets.UTF_8
          )
          _write_manifest(
            root,
            "cncf.car-development-runtime-manifest.v1",
            legacyidentity,
            "sample",
            "sample"
          )

          When("CNCF validates the explicitly selected development directory")
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("the v1 development manifest fails closed with recovery guidance")
          result.isSuccess shouldBe false
          result.display should include("development runtime manifest schemaVersion mismatch")
          result.display should include("development-side runtime evidence error")
          result.display should include("will not fall back to a packaged CAR")
        }
      }
    }

    "E12 reject a canonical manifest artifact name mismatch" must _e12_metadata {
      "reject a manifest name that is not projected from canonical descriptor identity" in {
        _with_prepared_manifest { root =>
          Given("Rules: R3,R6; a schema-3 development CAR with a canonically projected artifact name")
          val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

          When("the manifest artifact name no longer matches the canonical identity projection")
          Files.writeString(
            manifest,
            Files.readString(manifest, StandardCharsets.UTF_8).replace(
              s"\"name\":\"$_canonical_artifact_name\"",
              "\"name\":\"sample\""
            ),
            StandardCharsets.UTF_8
          )
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission rejects the manifest coordinate contradiction")
          result.display should include("development runtime manifest car.name mismatch")
        }
      }
    }

    }

    "fail static descriptor resolution before legacy evidence is exposed" which {
    "E13 reject ABI document v1 evidence" in {
      _with_prepared_manifest { root =>
        Given("a canonical schema-3 development directory whose ABI document is changed to v1")
        val abi = root.resolve(DevelopmentCarRuntimeAdmission.ABI_MANIFEST_IDENTITY)
        Files.writeString(
          abi,
          """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample","version":"0.0.1-SNAPSHOT"},"abi":{"version":1,"exports":{"components":[{"name":"sample"}]}}}""",
          StandardCharsets.UTF_8
        )

        When("CNCF validates the explicit development directory")
        val result = DevelopmentCarRuntimeAdmission.validate(root)

        Then("the ABI document v1 is rejected with the standard no-fallback recovery")
        result.display should include("component development ABI manifest format mismatch")
        result.display should include("development-side runtime evidence error")
        result.display should include("will not fall back to a packaged CAR")
      }
    }

    "E14 fail static descriptor resolution closed before legacy evidence is exposed" in {
      _with_temp_dir { root =>
        Given("a v1 development manifest and a source-tree descriptor")
        _prepare(root)
        Files.writeString(root.resolve("target/scala-3.3.8/classes/sample.class"), "compiled", StandardCharsets.UTF_8)
        val legacyidentity = "src/main/car/component-descriptor.json"
        val legacy = root.resolve(legacyidentity)
        Files.createDirectories(legacy.getParent)
        Files.writeString(legacy, """{"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample"}""", StandardCharsets.UTF_8)
        _write_manifest(root, "cncf.car-development-runtime-manifest.v1", legacyidentity, "sample", "sample")
        val specification = ComponentRepository.ComponentDevDirRepository.Specification(root)

        When("static component descriptor resolution is requested")
        val rejected = the[RuntimeException] thrownBy specification.resolveStaticComponentDescriptor(_canonical_component_id.name)

        Then("the recovery diagnostic is raised before any legacy descriptor can be exposed")
        rejected.getMessage should include ("development runtime manifest schemaVersion mismatch")
        rejected.getMessage should include ("development-side runtime evidence error")
      }
    }

    "E15 reject schema-v2 static descriptor evidence" in {
      _with_temp_dir { root =>
        Given("a digest-valid schema-v2 development descriptor")
        _prepare_schema2(root)
        Files.writeString(root.resolve("target/scala-3.3.8/classes/sample.class"), "compiled", StandardCharsets.UTF_8)
        _write_manifest(root)
        val specification = ComponentRepository.ComponentDevDirRepository.Specification(root)

        When("static descriptor resolution is requested for schema-v2 evidence")
        val rejected = the[RuntimeException] thrownBy specification.resolveStaticComponentDescriptor(_canonical_component_id.name)

        Then("the schema-v2 evidence remains unavailable with the recovery diagnostic")
        rejected.getMessage should include ("component development descriptor schemaVersion mismatch")
        rejected.getMessage should include ("development-side runtime evidence error")
      }
    }

    }

    "resolve canonical prepared static descriptors" which {
    "E16 resolve prepared canonical static descriptor evidence" in {
      _with_prepared_manifest { root =>
        Given("a canonical prepared development descriptor")
        val specification = ComponentRepository.ComponentDevDirRepository.Specification(root)

        When("static descriptor resolution is requested")
        val resolved = specification.resolveStaticComponentDescriptor(_canonical_component_id.name)
        val all = specification.resolveStaticComponentDescriptors

        Then("the canonical target descriptor is exposed")
        resolved.flatMap(_.componentId) shouldBe Some(_canonical_component_id)
        all.flatMap(_.componentId) shouldBe Vector(_canonical_component_id)
      }
    }
    }
  }

  private def _prepare(root: Path): Unit = {
    val descriptor = root.resolve(DevelopmentCarRuntimeAdmission.COMPONENT_DESCRIPTOR_IDENTITY)
    val abi = root.resolve(DevelopmentCarRuntimeAdmission.ABI_MANIFEST_IDENTITY)
    val classpath = root.resolve(DevelopmentCarRuntimeAdmission.RUNTIME_CLASSPATH_IDENTITY)
    Files.createDirectories(descriptor.getParent)
    Files.createDirectories(abi.getParent)
    Files.createDirectories(classpath.getParent)
    Files.createDirectories(root.resolve("target/scala-3.3.8/classes"))
    Files.writeString(descriptor, """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Sample","version":"0.0.1-SNAPSHOT"}}""", StandardCharsets.UTF_8)
    Files.writeString(abi, """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example","id":"Sample","version":"0.0.1-SNAPSHOT"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.example","id":"Sample"}],"operations":[],"entities":[]},"dependencies":[]}}""", StandardCharsets.UTF_8)
    Files.writeString(classpath, root.resolve("target/scala-3.3.8/classes").toString, StandardCharsets.UTF_8)
  }

  private def _prepare_schema2(root: Path): Unit = {
    _prepare(root)
    Files.writeString(
      root.resolve(DevelopmentCarRuntimeAdmission.COMPONENT_DESCRIPTOR_IDENTITY),
      """{"schemaVersion":2,"name":"sample","version":"0.0.1-SNAPSHOT","component":{"name":"sample"},"componentStyle":{"apiVersion":"cncf.textus/v1","provider":"cncf","id":"full-fledged-with-standalone@1","version":1,"parameterSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false},"parameters":{},"provides":{"bundles":["domain.full@1"],"capabilities":["user.fixed-context-compatible@1","user.multi-user@1"],"effective":["domain.aggregate@1","domain.command@1","domain.domain-event@1","domain.entity@1","domain.optimistic-concurrency@1","domain.persistence@1","domain.projection@1","domain.query@1","domain.transaction@1","user.fixed-context-compatible@1","user.multi-user@1"]},"requires":{"subsystemCapabilities":["datastore.optimistic-concurrency@1","datastore.persistent@1","datastore.transactional@1","user-context.current@1"]}}}""",
      StandardCharsets.UTF_8
    )
    Files.writeString(
      root.resolve(DevelopmentCarRuntimeAdmission.ABI_MANIFEST_IDENTITY),
      """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample","version":"0.0.1-SNAPSHOT"},"abi":{"exports":{"components":[{"name":"sample"}]}}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _with_prepared_manifest(body: Path => Unit): Unit =
    _with_temp_dir { root =>
      _prepare(root)
      Files.writeString(root.resolve("target/scala-3.3.8/classes/sample.class"), "compiled", StandardCharsets.UTF_8)
      _write_manifest(root)
      body(root)
    }

  private def _write_manifest(
    root: Path,
    schema: String = DevelopmentCarRuntimeAdmission.MANIFEST_SCHEMA,
    descriptoridentity: String = DevelopmentCarRuntimeAdmission.COMPONENT_DESCRIPTOR_IDENTITY,
    manifestname: String = _canonical_artifact_name,
    manifestcomponent: String = _canonical_component_id.localId.value()
  ): Unit = {
    val evidence = Vector(
      DevelopmentCarRuntimeAdmission.RUNTIME_CLASSPATH_IDENTITY,
      descriptoridentity,
      DevelopmentCarRuntimeAdmission.ABI_MANIFEST_IDENTITY
    ).map { identity =>
      val file = root.resolve(identity)
      val logical =
        if (identity == DevelopmentCarRuntimeAdmission.RUNTIME_CLASSPATH_IDENTITY)
          s"project:${root.relativize(root.resolve("target/scala-3.3.8/classes")).toString.replace('\\', '/')}"
        else
          ""
      (identity, _sha256(file), logical)
    }
    val entries = evidence.map { case (path, digest, logical) =>
      val suffix = if (logical.nonEmpty) s"\"logicalSha256\":\"${_sha256(logical.getBytes(StandardCharsets.UTF_8))}\", " else ""
      s"{${suffix}\"path\":\"$path\",\"sha256\":\"$digest\"}"
    }.mkString("[", ",", "]")
    val digest = _sha256(evidence.map { case (path, value, logical) =>
      s"$path\t$value\t${if (logical.nonEmpty) _sha256(logical.getBytes(StandardCharsets.UTF_8)) else ""}"
    }.mkString("\n").getBytes(StandardCharsets.UTF_8))
    val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")
    Files.writeString(
      manifest,
      s"""{"schemaVersion":"$schema","sourceKind":"${DevelopmentCarRuntimeAdmission.SOURCE_KIND}","car":{"name":"$manifestname","version":"0.0.1-SNAPSHOT","component":"$manifestcomponent"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$digest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private val _canonical_component_id = ComponentId("org.example.Sample")
  private val _canonical_artifact_name =
    ComponentReleaseCoordinate.require(_canonical_component_id.sharedIdentity, "0.0.1-SNAPSHOT").mavenArtifactId()

  private def _with_temp_dir[A](body: Path => A): A = {
    val root = Files.createTempDirectory("development-car-runtime-admission")
    try body(root)
    finally {
      val stream = Files.walk(root)
      try stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }
}
