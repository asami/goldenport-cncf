package org.goldenport.cncf.phase51.build

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator

final class Phase51Cv05BuildProvenanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Phase 51 CV-05 CNCF build provenance" should {
    "bind generation and validation commands to one exact source snapshot" in {
      Given("a pinned generator, CNCF target, runtime descriptor, and project CML source")
      _with_temp_dir("cncf-phase51-cv05-command") { directory =>
        val descriptor = _write(directory.resolve("target/runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
        val output = directory.resolve("target/generated")
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          directory.toFile,
          source.toFile
        ).toOption.get
        When("the build plans generation followed by authoritative Cozy validation")
        val generation = CncfGenerationBuildContract.command(inputs, output.toFile)
        val validation = CncfGenerationBuildContract.validationCommand(inputs, output.toFile)
        Then("both commands carry the same generator, target, descriptor digest, identity, and source digest")
        generation.toOption.get should contain allOf (
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          _sha256(descriptor),
          "src/main/cozy/information.cml"
        )
        validation shouldBe Right(Vector(
          "cozy",
          "--runtime",
          "0.3.1-SNAPSHOT",
          "generation-provenance-validate",
          source.toString,
          s"--save=${output.toAbsolutePath.normalize()}",
          "--cncf-version",
          "0.5.2-SNAPSHOT",
          "--cncf-runtime-descriptor-sha256",
          _sha256(descriptor),
          "--cozy-generator-version",
          "0.3.1-SNAPSHOT",
          "--generation-source-identity",
          "src/main/cozy/information.cml",
          "--generation-source-sha256",
          _sha256(source)
        ))
      }
    }

    "reject a source outside the project and a source changed after input resolution" in {
      Given("one external CML source and one captured project source")
      _with_temp_dir("cncf-phase51-cv05-boundary") { directory =>
        val project = Files.createDirectories(directory.resolve("project"))
        val descriptor = _write(project.resolve("target/runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(project.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
        val external = _write(directory.resolve("external/information.cml"), "# COMPONENT\n")
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          project.toFile,
          source.toFile
        ).toOption.get
        When("the build resolves the external source and later observes arbitrary changed project bytes")
        val outside = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          project.toFile,
          external.toFile
        )
        val changedtext = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
        val property = Prop.forAll(changedtext) { text =>
          _write(source, s"changed-$text\n")
          CncfGenerationBuildContract.command(inputs, project.resolve("target/generated").toFile) ==
            Left(Vector("The Information CML source changed after generation inputs were resolved."))
        }
        val propertyresult = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(50),
          property
        )
        Then("both machine-relative ambiguity and source drift fail before Cozy launches")
        outside shouldBe Left(Vector(
          "The Information CML source must be inside the CNCF project directory."
        ))
        propertyresult.passed shouldBe true
      }
    }

    "include provenance bytes with generated Scala in deterministic snapshots" in {
      Given("generated Scala output whose provenance is initially missing")
      _with_temp_dir("cncf-phase51-cv05-snapshot") { directory =>
        val output = directory.resolve("generated")
        _write(output.resolve("target/example/Information.scala"), "object Information\n")
        When("provenance is added and then its bytes are tampered")
        val missing = CncfGenerationBuildContract.snapshot(output.toFile)
        val provenance = _write(
          output.resolve(CncfGenerationBuildContract.GENERATION_PROVENANCE_PATH),
          """{"schemaVersion":"cozy.generation-provenance.v1","evidenceDigest":"first"}""" + "\n"
        )
        val original = CncfGenerationBuildContract.snapshot(output.toFile)
        _write(
          provenance,
          """{"schemaVersion":"cozy.generation-provenance.v1","evidenceDigest":"tampered"}""" + "\n"
        )
        val tampered = CncfGenerationBuildContract.snapshot(output.toFile)
        Then("missing provenance is rejected and changed evidence changes the snapshot")
        missing shouldBe Left(Vector(
          s"Generation provenance is required at ${CncfGenerationBuildContract.GENERATION_PROVENANCE_PATH}."
        ))
        original.toOption.get.scalaArtifacts shouldBe tampered.toOption.get.scalaArtifacts
        original.toOption.get.provenanceSha256 should not be tampered.toOption.get.provenanceSha256
      }
    }
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").
      digest(Files.readAllBytes(path)).
      map(byte => f"${byte & 0xff}%02x").
      mkString

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val directory = Files.createTempDirectory(prefix)
    try body(directory)
    finally {
      val stream = Files.walk(directory)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
