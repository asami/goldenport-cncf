package org.goldenport.cncf.phase51.build

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  6, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfCozyDevelopmentRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  // Spec: docs/spec/generation-compatibility-contract.md, R9, examples E12-E22.
  "CNCF Cozy development runtime selection" should {
    "preserve the unset route" which {
      "E12: requires no development directory and preserves both two-argument commands" in {
        Given("project-owned Information inputs and no explicit Cozy directory selection")
        _with_workspace("unset") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val inputs = _inputs(consumer)
          val output = consumer.resolve("target/generated").toFile
          val expectedgeneration = _generation_arguments(inputs, output)
          val expectedvalidation = _validation_arguments(inputs, output)
          When("the absent selector and existing two-argument invocation builders are resolved")
          val selection = CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(
            workspace.resolve("absent-consumer").toFile, None
          )
          val generation = CncfGenerationBuildContract.command(inputs, output)
          val validation = CncfGenerationBuildContract.validationCommand(inputs, output)
          Then("absence adds no checks and neither command gains a directory selector")
          selection shouldBe Right(None)
          generation shouldBe Right(Vector("cozy", "--runtime", "0.3.3-SNAPSHOT") ++ expectedgeneration)
          validation shouldBe Right(Vector("cozy", "--runtime", "0.3.3-SNAPSHOT") ++ expectedvalidation)
        }
      }
    }

    "admit explicit disjoint sources" which {
      "E13: uses one canonical directory argument for generation and authoritative validation" in {
        Given("a disjoint Cozy checkout with spaces and unchanged project-owned inputs")
        _with_workspace("disjoint") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val source = _cozy_source(workspace.resolve("Cozy source with spaces"))
          val spelling = source.resolve(".").toString
          val canonical = source.toFile.getCanonicalFile
          val inputs = _inputs(consumer)
          val output = consumer.resolve("target/generated").toFile
          val prefix = Vector("cozy", "--runtime", "0.3.3-SNAPSHOT", "--runtime-dev-dir", canonical.getPath)
          val expectedgeneration = prefix ++ _generation_arguments(inputs, output)
          val expectedvalidation = prefix ++ _validation_arguments(inputs, output)
          When("the explicit directory is admitted and passed to both invocation builders")
          val selection = CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(spelling))
          val generation = CncfGenerationBuildContract.command(inputs, output, selection.toOption.get)
          val validation = CncfGenerationBuildContract.validationCommand(inputs, output, selection.toOption.get)
          Then("the same canonical execution location precedes each operation without changing logical inputs")
          selection shouldBe Right(Some(canonical))
          generation shouldBe Right(expectedgeneration)
          validation shouldBe Right(expectedvalidation)
        }
      }

      "E14: returns descriptive rejection for blank, relative and uncanonicalizable selections" in {
        Given("an owning consumer and explicit selectors that cannot name an absolute canonical source")
        _with_workspace("invalid-spelling") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val selections = Vector("", " \t ", "relative/cozy", workspace.resolve("cozy").toString + "\u0000")
          When("each explicit selector is admitted")
          val results = selections.map(selection =>
            CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(selection))
          )
          Then("every invalid explicit selector fails without an unset or ambient fallback")
          results.foreach(_.isLeft shouldBe true)
          results(0).left.toOption.get.mkString should include("must not be blank")
          results(1).left.toOption.get.mkString should include("must not be blank")
          results(2).left.toOption.get.mkString should include("absolute path")
          results(3).left.toOption.get.mkString should include("Cannot canonicalize")
        }
      }

      "E15: requires both Cozy source markers" in {
        Given("disjoint directories missing both markers or one of the two Cozy markers")
        _with_workspace("markers") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val absent = workspace.resolve("absent")
          val buildonly = workspace.resolve("build-only")
          val cozyonly = workspace.resolve("cozy-only")
          _write(buildonly.resolve("build.sbt"), "version := \"0.3.3-SNAPSHOT\"\n")
          _write(cozyonly.resolve("src/main/scala/cozy/Cozy.scala"), "package cozy\nobject Cozy\n")
          When("the explicit directories are admitted")
          val results = Vector(absent, buildonly, cozyonly).map(source =>
            CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(source.toString))
          )
          Then("diagnostics identify the missing markers instead of starting another generator")
          results.foreach(_.isLeft shouldBe true)
          results(0).left.toOption.get should have size 2
          results(1).left.toOption.get.mkString should include("src/main/scala/cozy/Cozy.scala")
          results(2).left.toOption.get.mkString should include("build.sbt")
        }
      }

      "E16: rejects self, ancestor and ordinary nested source overlap" in {
        Given("source-marked consumer, ancestor and ordinary nested directories")
        _with_workspace("overlap") { workspace =>
          val ancestor = _cozy_source(workspace)
          val consumer = _cozy_source(workspace.resolve("consumer"))
          val nested = _cozy_source(consumer.resolve("ordinary-cozy"))
          When("the overlapping directories are explicitly selected")
          val results = Vector(consumer, ancestor, nested).map(source =>
            CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(source.toString))
          )
          Then("canonical self and ancestor overlap and ordinary nesting are rejected")
          results.foreach(_.isLeft shouldBe true)
          results.take(2).foreach(_.left.toOption.get.mkString should include("must not equal or contain"))
          results(2).left.toOption.get.mkString should include("canonical direct .codex-workflow/checkouts child")
        }
      }
    }

    "bound the dedicated checkout exception" which {
      "E17: admits only a canonical direct child with the same normalized lexical parent" in {
        Given("a direct dedicated checkout with legal name and normalized dot path spelling")
        _with_workspace("direct-child") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val source = _cozy_source(consumer.resolve(".codex-workflow/checkouts/Cozy_1.2-dev"))
          val canonical = source.toFile.getCanonicalFile
          val spelling = source.getParent.resolve(".").resolve(source.getFileName).toString
          When("the dedicated directory is selected")
          val selection = CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(spelling))
          Then("the narrowly admitted source is returned as its canonical directory")
          selection shouldBe Right(Some(canonical))
        }
      }

      "E18: rejects deep children and invalid direct checkout names" in {
        Given("source-marked deep, hidden and space-containing checkout children")
        _with_workspace("nested-shapes") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val checkoutparent = consumer.resolve(".codex-workflow/checkouts")
          val sources = Vector("group/cozy", ".hidden", "Cozy source").map(name => _cozy_source(checkoutparent.resolve(name)))
          When("the directories are admitted as explicit selections")
          val results = sources.map(source =>
            CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(source.toString))
          )
          Then("each directory is rejected despite having both required source markers")
          results.foreach { result =>
            result.isLeft shouldBe true
            result.left.toOption.get.mkString should include("matching lexical parent and legal checkout name")
          }
        }
      }

      "E19: rejects a selected child symlink escaping the consumer" in {
        Given("a direct checkout-name symlink to a disjoint marked Cozy source")
        _with_workspace("leaf-escape") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val external = _cozy_source(workspace.resolve("external-cozy"))
          val checkoutparent = Files.createDirectories(consumer.resolve(".codex-workflow/checkouts"))
          val alias = Files.createSymbolicLink(checkoutparent.resolve("cozy"), external)
          When("the lexical consumer child is selected")
          val selection = CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(alias.toString))
          Then("canonical disjointness cannot conceal a lexical symlink escape")
          selection.isLeft shouldBe true
          selection.left.toOption.get.mkString should include("must not escape")
        }
      }

      "E20: never canonicalizes a checkout namespace into an escape or ordinary nested exception" in {
        Given("checkout parent symlinks to disjoint and ordinary nested marked sources")
        _with_workspace("parent-escape") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val otherconsumer = Files.createDirectories(workspace.resolve("other-consumer"))
          val externalparent = Files.createDirectories(workspace.resolve("external"))
          val nestedparent = Files.createDirectories(otherconsumer.resolve("ordinary"))
          _cozy_source(externalparent.resolve("cozy"))
          _cozy_source(nestedparent.resolve("cozy"))
          Files.createDirectories(consumer.resolve(".codex-workflow"))
          Files.createDirectories(otherconsumer.resolve(".codex-workflow"))
          Files.createSymbolicLink(consumer.resolve(".codex-workflow/checkouts"), externalparent)
          Files.createSymbolicLink(otherconsumer.resolve(".codex-workflow/checkouts"), nestedparent)
          val selections = Vector(consumer, otherconsumer).map(project =>
            project -> project.resolve(".codex-workflow/checkouts/cozy")
          )
          When("each source is selected through its symlinked namespace")
          val results = selections.map { case (project, source) =>
            CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(project.toFile, Some(source.toString))
          }
          Then("the lexical namespace grants neither an escape nor a relocated nested exception")
          results.foreach(_.isLeft shouldBe true)
          results(0).left.toOption.get.mkString should include("must not escape")
          results(1).left.toOption.get.mkString should include("canonical direct")
        }
      }

      "E21: rejects canonical consumer overlap and nonmatching lexical parents through aliases" in {
        Given("external aliases to the consumer, its ancestor, an ordinary child and a dedicated child")
        _with_workspace("aliases") { workspace =>
          val ancestor = _cozy_source(workspace)
          val consumer = _cozy_source(workspace.resolve("consumer"))
          val ordinary = _cozy_source(consumer.resolve("ordinary"))
          val dedicated = _cozy_source(consumer.resolve(".codex-workflow/checkouts/cozy"))
          val sources = Vector(consumer, ancestor, ordinary, dedicated).zipWithIndex.map { case (source, index) =>
            Files.createSymbolicLink(workspace.resolve(s"source-alias-$index"), source)
          } :+ dedicated
          val consumeralias = Files.createSymbolicLink(workspace.resolve("consumer-alias"), consumer)
          When("canonical overlaps and a dedicated source through an unrelated lexical parent are selected")
          val results = sources.map(source =>
            CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumeralias.toFile, Some(source.toString))
          )
          Then("aliases preserve overlap rejection and cannot broaden the direct-child exception")
          results.foreach(_.isLeft shouldBe true)
          results.take(2).foreach(_.left.toOption.get.mkString should include("must not equal or contain"))
          results.drop(2).foreach(_.left.toOption.get.mkString should include("matching lexical parent"))
        }
      }
    }

    "preserve logical generation inputs across legal paths" which {
      "E22: propagates canonical dedicated and spaced disjoint paths as one argv argument for at least fifty cases" in {
        Given("ASCII legal checkout names and plain, dot and parent-normalized path spellings")
        val firstchars = ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ Vector('_')
        val restchars = firstchars ++ Vector('.', '-')
        val names = for {
          first <- Gen.oneOf(firstchars)
          length <- Gen.chooseNum(0, 20)
          rest <- Gen.listOfN(length, Gen.oneOf(restchars))
        } yield first.toString + rest.mkString
        val spellings = Gen.oneOf("plain", "dot", "parent")
        When("the path space is checked through admission and both invocation builders")
        val property = Prop.forAll(names, spellings) { (name, spelling) =>
          _with_workspace("property") { workspace =>
            Given("one dedicated child and one disjoint checkout under a parent containing spaces")
            val consumer = Files.createDirectories(workspace.resolve("consumer"))
            val sources = Vector(
              _cozy_source(consumer.resolve(".codex-workflow/checkouts").resolve(name)),
              _cozy_source(workspace.resolve("disjoint Cozy sources").resolve(name))
            )
            val inputs = _inputs(consumer)
            val output = consumer.resolve("target/generated").toFile
            val generationarguments = _generation_arguments(inputs, output)
            val validationarguments = _validation_arguments(inputs, output)
            sources.foreach { source =>
              val selected = spelling match {
                case "dot" => source.getParent.resolve(".").resolve(source.getFileName)
                case "parent" => source.resolve("..").resolve(source.getFileName)
                case _ => source
              }
              val canonical = source.toFile.getCanonicalFile
              val prefix = Vector("cozy", "--runtime", "0.3.3-SNAPSHOT", "--runtime-dev-dir", canonical.getPath)
              When("the selected spelling is resolved and supplied unchanged to both builders")
              val selection = CncfGenerationBuildContract.resolveCozyDevelopmentDirectory(consumer.toFile, Some(selected.toString))
              val generation = CncfGenerationBuildContract.command(inputs, output, selection.toOption.get).toOption.get
              val validation = CncfGenerationBuildContract.validationCommand(inputs, output, selection.toOption.get).toOption.get
              Then("both commands retain their exact pins, descriptor, digests, source and output semantics")
              selection shouldBe Right(Some(canonical))
              generation shouldBe prefix ++ generationarguments
              validation shouldBe prefix ++ validationarguments
              generation.count(_ == "--runtime-dev-dir") shouldBe 1
              validation.count(_ == "--runtime-dev-dir") shouldBe 1
              generation.count(_ == canonical.getPath) shouldBe 1
              validation.count(_ == canonical.getPath) shouldBe 1
            }
            true
          }
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
        Then("at least fifty generated cases preserve both routes without changing any logical input")
        result.passed shouldBe true
        result.succeeded should be >= 50
      }
    }
  }

  private def _inputs(consumer: Path): CncfGenerationInputs = {
    val descriptor = _write(consumer.resolve("target/runtime.yaml"), "version: 0.5.3-SNAPSHOT\n")
    val source = _write(consumer.resolve("src/main/cozy/information.cml"), "# COMPONENT\n")
    CncfGenerationBuildContract.resolve("0.3.3-SNAPSHOT", "0.5.3-SNAPSHOT", descriptor.toFile, consumer.toFile, source.toFile).toOption.get
  }

  private def _generation_arguments(inputs: CncfGenerationInputs, output: File): Vector[String] =
    Vector(
      "modeler-scala-value", inputs.source.getAbsolutePath,
      "--cncf-version", "0.5.3-SNAPSHOT",
      "--cozy-generator-version", "0.3.3-SNAPSHOT",
      "--cncf-runtime-descriptor", inputs.runtimeDescriptor.getAbsolutePath,
      "--generation-source-identity", "src/main/cozy/information.cml",
      s"--save=${output.getAbsolutePath}"
    )

  private def _validation_arguments(inputs: CncfGenerationInputs, output: File): Vector[String] =
    Vector(
      "generation-provenance-validate", inputs.source.getAbsolutePath,
      s"--save=${output.getAbsolutePath}",
      "--cncf-version", "0.5.3-SNAPSHOT",
      "--cozy-generator-version", "0.3.3-SNAPSHOT",
      "--generation-source-identity", "src/main/cozy/information.cml"
    )

  private def _cozy_source(directory: Path): Path = {
    _write(directory.resolve("build.sbt"), "version := \"0.3.3-SNAPSHOT\"\n")
    _write(directory.resolve("src/main/scala/cozy/Cozy.scala"), "package cozy\nobject Cozy\n")
    directory
  }

  private def _write(path: Path, text: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_workspace[A](label: String)(body: Path => A): A = {
    val root = Files.createDirectories(Paths.get("target/cncf-cozy-development-runtime-spec/work").toAbsolutePath.normalize())
    val workspace = Files.createTempDirectory(root, s"$label-")
    try body(workspace)
    finally {
      val stream = Files.walk(workspace)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
