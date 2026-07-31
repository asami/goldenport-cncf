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

  "Development CAR runtime admission" should {
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
            conclusion.display should include("sbt cozyPrepareRuntime")
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
          Files.writeString(manifest, Files.readString(manifest, StandardCharsets.UTF_8).replace("\"component\":\"sample\"", "\"component\":\"other\""), StandardCharsets.UTF_8)
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
          result.display should include("sbt cozyPrepareRuntime")
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
              conclusion.display should include("sbt cozyPrepareRuntime")
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
          Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("\"name\":\"sample\"}", "\"name\":\"other\"}"), StandardCharsets.UTF_8)
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("development admission rejects the stale contract with recovery")
          result.display should include("component development ABI does not export component other")
          result.display should include("sbt cozyPrepareRuntime")
        }
      }
    }

    "E10 reject an incomplete schema-v2 component style snapshot" must _e10_metadata {
      "reject a partial development descriptor before runtime activation" in {
        _with_prepared_manifest { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R3,R6; Example: E10; a prepared v2 development descriptor")
          val descriptor = root.resolve(DevelopmentCarRuntimeAdmission.COMPONENT_DESCRIPTOR_IDENTITY)

          When("the required parameters field is removed without regenerating evidence")
          Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("\"parameters\":{},", ""), StandardCharsets.UTF_8)
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("admission rejects the incomplete snapshot with recovery guidance")
          result.display should include("componentStyle must be a complete catalog-matching snapshot")
          result.display should include("sbt cozyPrepareRuntime")
        }
      }
    }

    "E11 admit a digest-valid v1 development directory during migration" must _e11_metadata {
      "preserve the reader-only v1 descriptor identity" in {
        _with_temp_dir { root =>
          Given("Spec: docs/spec/generation-compatibility-contract.md; Rules: R2,R3; Example: E11; a prepared v1 development directory")
          _prepare(root)
          Files.writeString(root.resolve("target/scala-3.3.8/classes/sample.class"), "compiled", StandardCharsets.UTF_8)
          val legacy = root.resolve(DevelopmentCarRuntimeAdmission.LEGACY_COMPONENT_DESCRIPTOR_IDENTITY)
          Files.createDirectories(legacy.getParent)
          Files.writeString(legacy, """{"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample"}""", StandardCharsets.UTF_8)
          _write_manifest(
            root,
            DevelopmentCarRuntimeAdmission.LEGACY_MANIFEST_SCHEMA,
            DevelopmentCarRuntimeAdmission.LEGACY_COMPONENT_DESCRIPTOR_IDENTITY
          )

          When("CNCF admits the explicitly selected development directory")
          val result = DevelopmentCarRuntimeAdmission.validate(root)

          Then("the valid v1 migration contract remains readable")
          result.isSuccess shouldBe true
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
    Files.writeString(descriptor, """{"schemaVersion":2,"name":"sample","version":"0.0.1-SNAPSHOT","component":{"name":"sample"},"componentStyle":{"apiVersion":"cncf.textus/v1","provider":"cncf","id":"full-fledged-with-standalone@1","version":1,"parameterSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false},"parameters":{},"provides":{"bundles":["domain.full@1"],"capabilities":["user.fixed-context-compatible@1","user.multi-user@1"],"effective":["domain.aggregate@1","domain.command@1","domain.domain-event@1","domain.entity@1","domain.optimistic-concurrency@1","domain.persistence@1","domain.projection@1","domain.query@1","domain.transaction@1","user.fixed-context-compatible@1","user.multi-user@1"]},"requires":{"subsystemCapabilities":["datastore.optimistic-concurrency@1","datastore.persistent@1","datastore.transactional@1","user-context.current@1"]}}}""", StandardCharsets.UTF_8)
    Files.writeString(abi, """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample","version":"0.0.1-SNAPSHOT"},"abi":{"exports":{"components":[{"name":"sample"}]}}}""", StandardCharsets.UTF_8)
    Files.writeString(classpath, root.resolve("target/scala-3.3.8/classes").toString, StandardCharsets.UTF_8)
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
    descriptoridentity: String = DevelopmentCarRuntimeAdmission.COMPONENT_DESCRIPTOR_IDENTITY
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
      s"""{"schemaVersion":"$schema","sourceKind":"${DevelopmentCarRuntimeAdmission.SOURCE_KIND}","car":{"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$digest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

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
