package org.goldenport.cncf.phase51.build

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator

/*
 * @since   Jul. 28, 2026
 * @version Oct.  9, 2026
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

    "bind generation and validation commands to one selected source and exact versions" in {
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
        Then("both commands carry the exact generator, target, and source identity; generation selects the descriptor path and validation selects the current source and output paths")
        generation.toOption.get should contain allOf (
          "0.3.1-SNAPSHOT",
          "0.5.2-SNAPSHOT",
          descriptor.toAbsolutePath.normalize().toString,
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
          "--cozy-generator-version",
          "0.3.1-SNAPSHOT",
          "--generation-source-identity",
          "src/main/cozy/information.cml"
        ))
      }
    }

    "reject a source outside the project" in {
      Given("an owning project, its runtime descriptor, and an external CML source")
      _with_temp_dir("cncf-phase51-cv05-boundary") { directory =>
        val project = Files.createDirectories(directory.resolve("project"))
        val descriptor = _write(project.resolve("target/runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
        val external = _write(directory.resolve("external/information.cml"), "# COMPONENT\n")
        When("the build resolves the external source as a project generation input")
        val outside = CncfGenerationBuildContract.resolve(
          "0.3.1-SNAPSHOT",
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

  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target/phase51-cv05-build-provenance-spec/work").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, prefix)
    try body(directory)
    finally {
      val stream = Files.walk(directory)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
