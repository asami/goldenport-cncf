package org.goldenport.cncf.phase51.build

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

final class Phase51Cv03BuildIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private implicit val _execution_context: ExecutionContext = ExecutionContext.global

  "Phase 51 CV-03 CNCF generation build integration" should {
    "resolve one exact ambient-free generator, target, descriptor, and source snapshot" in {
      Given("the authoritative build versions, generated runtime descriptor, and project CML source")
      _with_temp_dir("cncf-phase51-cv03-resolve") { directory =>
        val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
        When("the build contract resolves the generation inputs")
        val resolved = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          directory.toFile,
          source.toFile
        )
        val padding = Gen.chooseNum(0, 8)
        val property = Prop.forAll(padding, padding) { (cozyleftpadding, cncfleftpadding) =>
          CncfGenerationBuildContract.resolve(
            (" " * cozyleftpadding) + "0.3.1-SNAPSHOT ",
            (" " * cncfleftpadding) + "0.5.2-SNAPSHOT ",
            descriptor.toFile,
            directory.toFile,
            source.toFile
          ) == resolved
        }
        val propertyresult = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(50),
          property
        )
        Then("the exact normalized inputs are retained without another version source")
        resolved shouldBe Right(CncfGenerationInputs(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toAbsolutePath.normalize().toFile,
          _sha256(descriptor),
          source.toAbsolutePath.normalize().toFile,
          "src/main/cozy/information.cml",
          _sha256(source)
        ))
        propertyresult.passed shouldBe true
      }
    }

    "reject missing build inputs before launching Cozy" in {
      Given("blank versions and descriptor, project, and CML paths that do not exist")
      _with_temp_dir("cncf-phase51-cv03-missing") { directory =>
        val missingdescriptor = directory.resolve("missing-runtime.yaml")
        val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("information.cml"), "# COMPONENT\n")
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          directory.toFile,
          source.toFile
        ).toOption.get
        When("the build contract resolves the invalid inputs")
        val resolved = CncfGenerationBuildContract.resolve(
          " ",
          "",
          missingdescriptor.toFile,
          directory.resolve("missing-project").toFile,
          directory.resolve("missing-information.cml").toFile
        )
        Files.delete(source)
        val command = CncfGenerationBuildContract.command(inputs, directory.resolve("generated").toFile)
        Then("deterministic diagnostics identify every missing input")
        resolved shouldBe Left(Vector(
          "The exact Cozy generator version is required.",
          "The exact CNCF target version is required.",
          "The generated CNCF runtime descriptor is required.",
          "The CNCF project directory is required.",
          "The Information CML source is required."
        ))
        command shouldBe Left(Vector("The Information CML source is required."))
      }
    }

    "build the supported Cozy invocation with exact target, descriptor, and source identity" in {
      Given("resolved generation inputs with a project-relative Information CML source")
      _with_temp_dir("cncf-phase51-cv03-command") { directory =>
        val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
        val output = directory.resolve("generated")
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          directory.toFile,
          source.toFile
        ).toOption.get
        When("the build contract constructs the Cozy command")
        val command = CncfGenerationBuildContract.command(inputs, output.toFile)
        Then("the command pins one generator and passes one exact target, descriptor, and identity")
        command shouldBe Right(Vector(
          "cozy",
          "--runtime",
          "0.3.1-SNAPSHOT",
          "modeler-scala-value",
          source.toAbsolutePath.normalize().toString,
          "--cncf-version",
          "0.5.2-SNAPSHOT",
          "--cozy-generator-version",
          "0.3.1-SNAPSHOT",
          "--cncf-runtime-descriptor",
          descriptor.toAbsolutePath.normalize().toString,
          "--cncf-runtime-descriptor-sha256",
          _sha256(descriptor),
          "--generation-source-identity",
          "src/main/cozy/information.cml",
          s"--save=${output.toAbsolutePath.normalize()}"
        ))
      }
    }

    "produce identical cold, repeated, and concurrent invocation plans" in {
      Given("one immutable generation input set and initially absent output")
      _with_temp_dir("cncf-phase51-cv03-repeat") { directory =>
        val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("information.cml"), "# COMPONENT\n")
        val output = directory.resolve("generated")
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          directory.toFile,
          source.toFile
        ).toOption.get
        When("the command is planned cold, repeated after output exists, and concurrently")
        val cold = CncfGenerationBuildContract.command(inputs, output.toFile)
        Files.createDirectories(output)
        _write(output.resolve("existing.scala"), "object Existing\n")
        val repeated = CncfGenerationBuildContract.command(inputs, output.toFile)
        val concurrent = Await.result(
          Future.traverse(Vector.range(0, 32))(_ => Future(CncfGenerationBuildContract.command(inputs, output.toFile))),
          10.seconds
        )
        Then("every evaluation observes the same exact invocation")
        repeated shouldBe cold
        concurrent.distinct shouldBe Vector(cold)
      }
    }

    "report an unavailable exact generator coordinate with recovery" in {
      Given("resolved generation inputs whose delegated process cannot start successfully")
      _with_temp_dir("cncf-phase51-cv07-generator-failure") { directory =>
        val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("information.cml"), "# COMPONENT\n")
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          directory.toFile,
          source.toFile
        ).toOption.get

        When("the build boundary renders the delegated failure")
        val message = CncfGenerationBuildContract.generationFailure(inputs, 1)

        Then("the exact Maven coordinates and deterministic recovery are visible")
        message should include("org.simplemodeling:cozy_2.12:0.3.1-SNAPSHOT")
        message should include("org.goldenport:goldenport-cncf_3:0.5.2-SNAPSHOT")
        message should include(
          "recovery=publish-or-select-the-exact-project-owned-Cozy-generator-coordinate"
        )
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
