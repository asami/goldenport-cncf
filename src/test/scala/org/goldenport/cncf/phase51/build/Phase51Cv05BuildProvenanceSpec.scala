package org.goldenport.cncf.phase51.build

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator

/*
 * @since   Jul. 28, 2026
 * @version Oct. 14, 2026
 */
final class Phase51Cv05BuildProvenanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Phase 51 CV-05 CNCF build provenance" should {
    "preserve Cozy generation notice severity when the build consumes process output" in {
      Given("a structured Cozy development-pair notice and ordinary stderr diagnostics")
      val notice =
        "[cozy.generation.acceptance] boundary=cli level=notice code=MutableDevelopmentPairAccepted message=accepted"
      val failure =
        "[cozy.generation.acceptance] boundary=cli code=MissingEvidence message=missing"

      When("the CNCF build classifies Cozy process output")
      val noticeclassification = CncfGenerationBuildContract.isGenerationNotice(notice)
      val failureclassification = CncfGenerationBuildContract.isGenerationNotice(failure)
      val ordinaryclassification = CncfGenerationBuildContract.isGenerationNotice("unexpected stderr")

      Then("only the explicitly structured notice is eligible for info-level logging")
      noticeclassification shouldBe true
      failureclassification shouldBe false
      ordinaryclassification shouldBe false
    }

    "bind generation and validation commands to the current source path and exact declared versions" in {
      Given("a pinned generator, CNCF target, runtime descriptor, and project CML source")
      _with_temp_dir("cncf-phase51-cv05-command") { directory =>
        val descriptor = _write(directory.resolve("target/runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
        val output = directory.resolve("target/generated")
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.3-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          directory.toFile,
          source.toFile
        ).toOption.get
        When("the build plans generation followed by authoritative Cozy validation")
        val generation = CncfGenerationBuildContract.command(inputs, output.toFile)
        val validation = CncfGenerationBuildContract.validationCommand(inputs, output.toFile)
        Then("both commands retain the exact generator, target, source path, and identity")
        generation shouldBe Right(Vector(
          "cozy",
          "--runtime",
          "0.3.3-SNAPSHOT",
          "modeler-scala-value",
          source.toString,
          "--cncf-version",
          "0.5.2-SNAPSHOT",
          "--cozy-generator-version",
          "0.3.3-SNAPSHOT",
          "--cncf-runtime-descriptor",
          descriptor.toString,
          "--generation-source-identity",
          "src/main/cozy/information.cml",
          s"--save=${output.toAbsolutePath.normalize()}"
        ))
        validation shouldBe Right(Vector(
          "cozy",
          "--runtime",
          "0.3.3-SNAPSHOT",
          "generation-provenance-validate",
          source.toString,
          s"--save=${output.toAbsolutePath.normalize()}",
          "--cncf-version",
          "0.5.2-SNAPSHOT",
          "--cozy-generator-version",
          "0.3.3-SNAPSHOT",
          "--generation-source-identity",
          "src/main/cozy/information.cml"
        ))
      }
    }

    "reject a source outside its owning project" in {
      Given("a CNCF project and a CML source outside that project")
      _with_temp_dir("cncf-phase51-cv05-boundary") { directory =>
        val project = Files.createDirectories(directory.resolve("project"))
        val descriptor = _write(project.resolve("target/runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val external = _write(directory.resolve("external/information.cml"), "# COMPONENT\n")

        When("the build resolves the external source")
        val outside = CncfGenerationBuildContract.resolve(
          "0.3.3-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toFile,
          project.toFile,
          external.toFile
        )

        Then("source containment fails before Cozy launches")
        outside shouldBe Left(Vector(
          "The Information CML source must be inside the CNCF project directory."
        ))
      }
    }

    "consume the selected current source after edits and reject its deletion" in {
      Given("resolved Information inputs and a source whose content can be edited")
      _with_temp_dir("cncf-phase51-cv05-current") { directory =>
        val descriptor = _write(directory.resolve("target/runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
        val output = directory.resolve("generated").toFile
        val inputs = CncfGenerationBuildContract.resolve(
          "0.3.3-SNAPSHOT", "0.5.2-SNAPSHOT", descriptor.toFile, directory.toFile, source.toFile
        ).toOption.get
        val expectedgeneration = Right(Vector(
          "cozy", "--runtime", "0.3.3-SNAPSHOT", "modeler-scala-value", source.toString,
          "--cncf-version", "0.5.2-SNAPSHOT",
          "--cozy-generator-version", "0.3.3-SNAPSHOT",
          "--cncf-runtime-descriptor", descriptor.toString,
          "--generation-source-identity", "src/main/cozy/information.cml",
          s"--save=${output.getAbsolutePath}"
        ))
        val expectedvalidation = Right(Vector(
          "cozy", "--runtime", "0.3.3-SNAPSHOT", "generation-provenance-validate", source.toString,
          s"--save=${output.getAbsolutePath}",
          "--cncf-version", "0.5.2-SNAPSHOT",
          "--cozy-generator-version", "0.3.3-SNAPSHOT",
          "--generation-source-identity", "src/main/cozy/information.cml"
        ))
        val changedtext = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

        When("arbitrary edited CML content is consumed through the resolved path")
        val property = Prop.forAll(changedtext) { text =>
          _write(source, s"changed-$text\n")
          CncfGenerationBuildContract.command(inputs, output) == expectedgeneration &&
            CncfGenerationBuildContract.validationCommand(inputs, output) == expectedvalidation
        }
        val propertyresult = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(100),
          property
        )

        Then("both commands retain the current source path and project-relative identity for every edit")
        propertyresult.passed shouldBe true
        propertyresult.succeeded should be >= 100
        propertyresult.discarded shouldBe 0

        When("the selected source is deleted")
        Files.delete(source)
        val generation = CncfGenerationBuildContract.command(inputs, output)
        val validation = CncfGenerationBuildContract.validationCommand(inputs, output)

        Then("both command preparations report the missing source")
        generation shouldBe Left(Vector("The Information CML source is required."))
        validation shouldBe Left(Vector("The Information CML source is required."))
      }
    }
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val work = Files.createDirectories(Path.of("target/phase51-cv05-build-provenance-spec/work"))
    val directory = Files.createTempDirectory(work, prefix)
    try body(directory)
    finally {
      val stream = Files.walk(directory)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
