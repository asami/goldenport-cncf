package org.goldenport.cncf.phase51.build

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future}

/*
 * @since   Jul. 28, 2026
 * @version Oct. 14, 2026
 */
final class Phase51Cv03BuildIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private implicit val _execution_context: ScalaExecutionContext = ScalaExecutionContext.global

  "Phase 51 CV-03 CNCF generation build integration" should {
    "generation input and invocation semantics" which {
      "resolve one exact ambient-free generator, target, descriptor, and source identity" in {
        Given("the authoritative build versions, generated runtime descriptor, and project CML source")
        _with_temp_dir("cncf-phase51-cv03-resolve") { directory =>
          val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
          val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
          When("the build contract resolves the generation inputs")
          val resolved = CncfGenerationBuildContract.resolve(
            "0.3.3-SNAPSHOT",
            "0.5.2-SNAPSHOT",
            descriptor.toFile,
            directory.toFile,
            source.toFile
          )
          val padding = Gen.chooseNum(0, 8)
          val property = Prop.forAll(padding, padding) { (cozyleftpadding, cncfleftpadding) =>
            CncfGenerationBuildContract.resolve(
              (" " * cozyleftpadding) + "0.3.3-SNAPSHOT ",
              (" " * cncfleftpadding) + "0.5.2-SNAPSHOT ",
              descriptor.toFile,
              directory.toFile,
              source.toFile
            ) == resolved
          }
          val propertyresult = Test.check(
            Test.Parameters.default.withMinSuccessfulTests(100),
            property
          )
          Then("the exact normalized inputs are retained without another version source")
          resolved shouldBe Right(CncfGenerationInputs(
            "0.3.3-SNAPSHOT",
            "0.5.2-SNAPSHOT",
            descriptor.toAbsolutePath.normalize().toFile,
            source.toAbsolutePath.normalize().toFile,
            "src/main/cozy/information.cml"
          ))
          propertyresult.passed shouldBe true
          propertyresult.succeeded should be >= 100
          propertyresult.discarded shouldBe 0
        }
      }

      "reject missing build inputs before launching Cozy" in {
        Given("blank versions and descriptor, project, and CML paths that do not exist")
        _with_temp_dir("cncf-phase51-cv03-missing") { directory =>
          val missingdescriptor = directory.resolve("missing-runtime.yaml")
          val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
          val source = _write(directory.resolve("information.cml"), "# COMPONENT\n")
          val inputs = CncfGenerationBuildContract.resolve(
            "0.3.3-SNAPSHOT",
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
            "0.3.3-SNAPSHOT",
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
            "0.3.3-SNAPSHOT",
            "modeler-scala-value",
            source.toAbsolutePath.normalize().toString,
            "--cncf-version",
            "0.5.2-SNAPSHOT",
            "--cozy-generator-version",
            "0.3.3-SNAPSHOT",
            "--cncf-runtime-descriptor",
            descriptor.toAbsolutePath.normalize().toString,
            "--generation-source-identity",
            "src/main/cozy/information.cml",
            s"--save=${output.toAbsolutePath.normalize()}"
          ))
        }
      }

      "produce identical cold, repeated, and concurrent invocation plans" in {
        Given("one resolved generation input set and initially absent output")
        _with_temp_dir("cncf-phase51-cv03-repeat") { directory =>
          val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
          val source = _write(directory.resolve("information.cml"), "# COMPONENT\n")
          val output = directory.resolve("generated")
          val inputs = CncfGenerationBuildContract.resolve(
            "0.3.3-SNAPSHOT",
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
            "0.3.3-SNAPSHOT",
            "0.5.2-SNAPSHOT",
            descriptor.toFile,
            directory.toFile,
            source.toFile
          ).toOption.get

          When("the build boundary renders the delegated failure")
          val message = CncfGenerationBuildContract.generationFailure(inputs, 1)

          Then("the exact Maven coordinates and deterministic recovery are visible")
          message should include("org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT")
          message should include("org.goldenport:goldenport-cncf_3:0.5.2-SNAPSHOT")
          message should include(
            "recovery=publish-or-select-the-exact-project-owned-Cozy-generator-coordinate"
          )
        }
      }
    }

    "explicit generator-root selection" which {
      "retain the exact artifact commands when neither selector is present" in {
        Given("resolved Information inputs and no property or environment generator selection")
        _with_temp_dir("cncf-phase51-gsel01") { directory =>
          val inputs = _inputs(directory)
          val output = directory.resolve("generated").toFile

          When("the root and both supported commands are selected")
          val selected = CncfGenerationBuildContract.selectedGeneratorRoot(None, None)
          val generation = CncfGenerationBuildContract.command(inputs, output, selected.toOption.get)
          val validation = CncfGenerationBuildContract.validationCommand(inputs, output, selected.toOption.get)

          Then("selection remains absent and both commands keep the exact version artifact defaults")
          selected shouldBe Right(None)
          generation.toOption.get.take(4) shouldBe Vector("cozy", "--runtime", "0.3.3-SNAPSHOT", "modeler-scala-value")
          validation.toOption.get.take(4) shouldBe Vector("cozy", "--runtime", "0.3.3-SNAPSHOT", "generation-provenance-validate")
          generation.toOption.get should not contain "--runtime-dev-dir"
          validation.toOption.get should not contain "--runtime-dev-dir"
        }
      }

      "honor the property root and reject invalid properties without environment fallback" in {
        Given("distinct valid property and environment projects with build.sbt files")
        _with_temp_dir("cncf-phase51-gsel02") { directory =>
          val propertyroot = _generator_root(directory.resolve("property"))
          val environmentroot = _generator_root(directory.resolve("environment"))
          val missingroot = directory.resolve("missing-property").toString

          When("valid, blank, and nonexistent property values are selected alongside the valid environment")
          val selected = CncfGenerationBuildContract.selectedGeneratorRoot(
            Some(s"  $propertyroot  "), Some(environmentroot.toString)
          )
          val blank = CncfGenerationBuildContract.selectedGeneratorRoot(Some("  "), Some(environmentroot.toString))
          val missing = CncfGenerationBuildContract.selectedGeneratorRoot(Some(missingroot), Some(environmentroot.toString))

          Then("the property wins and each invalid property gives its own path and recovery")
          selected shouldBe Right(Some(propertyroot.toFile.getCanonicalFile))
          blank shouldBe Left(Vector(_root_failure("", "is blank")))
          missing shouldBe Left(Vector(_root_failure(missingroot, "must be an existing directory")))
        }
      }

      "select a canonical environment root with spaces and reject unusable explicit roots" in {
        Given("a valid project with spaces and explicit missing, file, and missing-build paths")
        _with_temp_dir("cncf-phase51-gsel03") { directory =>
          val environmentroot = _generator_root(directory.resolve("cozy generator"))
          val selectedpath = environmentroot.resolve("..").resolve("cozy generator").toString
          val missingroot = directory.resolve("missing").toString
          val fileroot = _write(directory.resolve("ordinary-file"), "not a directory\n").toString
          val nobuildroot = Files.createDirectories(directory.resolve("without-build")).toString
          val directorybuildroot = Files.createDirectories(directory.resolve("directory-build"))
          Files.createDirectories(directorybuildroot.resolve("build.sbt"))

          When("the environment is selected and each unusable explicit property is resolved")
          val selected = CncfGenerationBuildContract.selectedGeneratorRoot(None, Some(s" $selectedpath "))
          val rejected = Vector(missingroot, fileroot, nobuildroot, directorybuildroot.toString).map { path =>
            CncfGenerationBuildContract.selectedGeneratorRoot(Some(path), None)
          }

          Then("the canonical environment project is selected and unusable roots fail before launch")
          selected shouldBe Right(Some(environmentroot.toFile.getCanonicalFile))
          rejected shouldBe Vector(
            Left(Vector(_root_failure(missingroot, "must be an existing directory"))),
            Left(Vector(_root_failure(fileroot, "must be an existing directory"))),
            Left(Vector(_root_failure(nobuildroot, "must contain a regular build.sbt file"))),
            Left(Vector(_root_failure(directorybuildroot.toString, "must contain a regular build.sbt file")))
          )
        }
      }

      "use one selected development root for generation and authoritative metadata validation" in {
        Given("resolved Information inputs and an explicitly selected generator project")
        _with_temp_dir("cncf-phase51-gsel04") { directory =>
          val inputs = _inputs(directory)
          val output = directory.resolve("generated").toFile
          val generator = _generator_root(directory.resolve("cozy generator"))
          val selected = CncfGenerationBuildContract.selectedGeneratorRoot(Some(generator.toString), None).toOption.get
          val prefix = Vector("cozy", "--runtime", inputs.cozyGeneratorVersion, "--runtime-dev-dir", generator.toFile.getCanonicalPath)

          When("both commands are built using that same selected root")
          val generation = CncfGenerationBuildContract.command(inputs, output, generatorRoot = selected)
          val validation = CncfGenerationBuildContract.validationCommand(inputs, output, generatorRoot = selected)

          Then("the development selector and all current source, target, identity, and output arguments are retained")
          generation shouldBe Right(prefix ++ Vector(
            "modeler-scala-value", inputs.source.getAbsolutePath,
            "--cncf-version", inputs.cncfTargetVersion,
            "--cozy-generator-version", inputs.cozyGeneratorVersion,
            "--cncf-runtime-descriptor", inputs.runtimeDescriptor.getAbsolutePath,
            "--generation-source-identity", inputs.sourceIdentity,
            s"--save=${output.getAbsolutePath}"
          ))
          validation shouldBe Right(prefix ++ Vector(
            "generation-provenance-validate", inputs.source.getAbsolutePath,
            s"--save=${output.getAbsolutePath}",
            "--cncf-version", inputs.cncfTargetVersion,
            "--cozy-generator-version", inputs.cozyGeneratorVersion,
            "--generation-source-identity", inputs.sourceIdentity
          ))
        }
      }
    }
  }

  private def _inputs(directory: Path): CncfGenerationInputs = {
    val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
    val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
    CncfGenerationBuildContract.resolve(
      "0.3.3-SNAPSHOT", "0.5.2-SNAPSHOT", descriptor.toFile, directory.toFile, source.toFile
    ).toOption.get
  }

  private def _generator_root(directory: Path): Path = {
    _write(directory.resolve("build.sbt"), "// generator project\n")
    directory
  }

  private def _root_failure(path: String, reason: String): String = {
    s"The selected Cozy generator root '$path' $reason. " +
      "recovery=set-cncf.cozyGeneratorRoot-or-CNCF_COZY_GENERATOR_ROOT-to-an-existing-Cozy-project-directory-containing-build.sbt"
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val work = Files.createDirectories(Path.of("target/phase51-cv03-build-integration-spec/work"))
    val directory = Files.createTempDirectory(work, prefix)
    try body(directory)
    finally {
      val stream = Files.walk(directory)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
