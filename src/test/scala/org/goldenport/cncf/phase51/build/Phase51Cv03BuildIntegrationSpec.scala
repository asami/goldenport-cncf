package org.goldenport.cncf.phase51.build

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future}

/*
 * @since   Jul. 28, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase51Cv03BuildIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private implicit val _execution_context: ScalaExecutionContext = ScalaExecutionContext.global

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

    "explicit Cozy development runtime selection" which {
      "GENDEV-01 E1 preserve exact versions and arguments with a normalized selector" should afterWord(
        "in spec:cncf-generation-runtime-selection, example:E1, rules:R1,R2,R3, phase:100"
      ) {
        "select the same absolute directory in generation and validation" in {
          Given("docs/spec/cncf-generation-runtime-selection.md R1-R3 E1: exact build inputs and a Cozy directory with spaces and dot segments")
          _with_temp_dir("cncf-phase51-cv03-gendev01") { directory =>
            val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
            val source = _write(directory.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
            val runtime = Files.createDirectories(directory.resolve("cozy runtime"))
            _write(runtime.resolve("build.sbt"), "// explicit development runtime\n")
            Files.createDirectories(runtime.resolve("branch"))
            val selected = runtime.resolve("./branch/../.").toFile
            val output = directory.resolve("generated").toFile
            val descriptorhash = _sha256(descriptor)
            val sourcehash = _sha256(source)

            When("the public contract resolves ordinary and explicit inputs and plans both operations")
            val ordinary = CncfGenerationBuildContract.resolve(
              "0.3.1-SNAPSHOT", "0.5.2-SNAPSHOT", descriptor.toFile,
              directory.toFile, source.toFile
            ).toOption.get
            val explicit = CncfGenerationBuildContract.resolve(
              "0.3.1-SNAPSHOT", "0.5.2-SNAPSHOT", descriptor.toFile,
              directory.toFile, source.toFile, Some(selected)
            ).toOption.get
            val ordinarygeneration = CncfGenerationBuildContract.command(ordinary, output)
            val ordinaryvalidation = CncfGenerationBuildContract.validationCommand(ordinary, output)
            val explicitgeneration = CncfGenerationBuildContract.command(explicit, output)
            val explicitvalidation = CncfGenerationBuildContract.validationCommand(explicit, output)

            Then("one normalized argv element selects both operations while exact versions, snapshots, and operation arguments remain unchanged")
            ordinary.cozyRuntimeDevDirectory shouldBe None
            explicit shouldBe ordinary.copy(cozyRuntimeDevDirectory = Some(runtime.toAbsolutePath.normalize().toFile))
            ordinarygeneration.toOption.get.take(3) shouldBe Vector("cozy", "--runtime", "0.3.1-SNAPSHOT")
            ordinaryvalidation shouldBe Right(Vector(
              "cozy", "--runtime", "0.3.1-SNAPSHOT",
              "generation-provenance-validate", source.toAbsolutePath.normalize().toString,
              s"--save=${output.getAbsolutePath}", "--cncf-version", "0.5.2-SNAPSHOT",
              "--cncf-runtime-descriptor-sha256", descriptorhash,
              "--cozy-generator-version", "0.3.1-SNAPSHOT",
              "--generation-source-identity", "src/main/cozy/information.cml",
              "--generation-source-sha256", sourcehash
            ))
            val selector = Vector("--runtime-dev-dir", runtime.toAbsolutePath.normalize().toString)
            explicitgeneration shouldBe Right(
              ordinarygeneration.toOption.get.take(3) ++ selector ++ ordinarygeneration.toOption.get.drop(3)
            )
            explicitvalidation shouldBe Right(
              ordinaryvalidation.toOption.get.take(3) ++ selector ++ ordinaryvalidation.toOption.get.drop(3)
            )
          }
        }
      }

      "GENDEV-02 E2 reject invalid explicit selections without fallback" should afterWord(
        "in spec:cncf-generation-runtime-selection, example:E2, rules:R1,R2, phase:100"
      ) {
        "report the same selector diagnostic for every invalid supplied directory" in {
          Given("docs/spec/cncf-generation-runtime-selection.md R1-R2 E2: valid build inputs and missing, relative, file, incomplete, or null selectors")
          _with_temp_dir("cncf-phase51-cv03-gendev02") { directory =>
            val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
            val source = _write(directory.resolve("information.cml"), "# COMPONENT\n")
            val regularfile = _write(directory.resolve("cozy-file"), "not a directory\n")
            val incomplete = Files.createDirectories(directory.resolve("cozy-without-build"))
            val selectors: Vector[Option[File]] = Vector(
              Some(directory.resolve("missing-cozy").toFile),
              Some(new File("relative-cozy")),
              Some(regularfile.toFile),
              Some(incomplete.toFile),
              Some(null),
              null
            )

            When("the public contract resolves every supplied selector and an ordinary absent selector")
            val rejected = selectors.map { selected =>
              CncfGenerationBuildContract.resolve(
                "0.3.1-SNAPSHOT", "0.5.2-SNAPSHOT", descriptor.toFile,
                directory.toFile, source.toFile, selected
              )
            }
            val ordinary = CncfGenerationBuildContract.resolve(
              "0.3.1-SNAPSHOT", "0.5.2-SNAPSHOT", descriptor.toFile,
              directory.toFile, source.toFile, None
            )

            Then("each explicit failure retains the deterministic diagnostic and only ordinary None resolves without a selector")
            rejected shouldBe Vector.fill(selectors.size)(Left(Vector(
              "The selected Cozy runtime development directory must be an absolute directory containing build.sbt."
            )))
            ordinary.toOption.get.cozyRuntimeDevDirectory shouldBe None
          }
        }
      }

      "GENDEV-03 E3 reject a checkout that loses its build after resolution" should afterWord(
        "in spec:cncf-generation-runtime-selection, example:E3, rules:R2,R3,R4, phase:100"
      ) {
        "retain selector, source, and output diagnostics in both operation plans" in {
          Given("docs/spec/cncf-generation-runtime-selection.md R2-R4 E3: successfully resolved explicit inputs whose selected build.sbt is then removed")
          _with_temp_dir("cncf-phase51-cv03-gendev03") { directory =>
            val descriptor = _write(directory.resolve("runtime.yaml"), "version: 0.5.2-SNAPSHOT\n")
            val source = _write(directory.resolve("information.cml"), "# COMPONENT\n")
            val runtime = Files.createDirectories(directory.resolve("cozy runtime"))
            val build = _write(runtime.resolve("build.sbt"), "// selected runtime\n")
            val output = directory.resolve("generated").toFile
            val inputs = CncfGenerationBuildContract.resolve(
              "0.3.1-SNAPSHOT", "0.5.2-SNAPSHOT", descriptor.toFile,
              directory.toFile, source.toFile, Some(runtime.toFile)
            ).toOption.get
            Files.delete(build)

            When("generation and validation are planned against the now incomplete selected checkout")
            val generation = CncfGenerationBuildContract.command(inputs, output)
            val validation = CncfGenerationBuildContract.validationCommand(inputs, output)

            Then("both plans fail on the explicit selector without producing an ordinary fallback command")
            generation shouldBe Left(Vector(
              "The selected Cozy runtime development directory must be an absolute directory containing build.sbt."
            ))
            validation shouldBe generation

            Given("the source is also removed and the output directory is absent")
            Files.delete(source)

            When("both operations are planned with all three invalid inputs")
            val missinggeneration = CncfGenerationBuildContract.command(inputs, null)
            val missingvalidation = CncfGenerationBuildContract.validationCommand(inputs, null)

            Then("the original source and output diagnostics precede the selector diagnostic in both plans")
            missinggeneration shouldBe Left(Vector(
              "The Information CML source is required.",
              "The generation output directory is required.",
              "The selected Cozy runtime development directory must be an absolute directory containing build.sbt."
            ))
            missingvalidation shouldBe missinggeneration
          }
        }
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
