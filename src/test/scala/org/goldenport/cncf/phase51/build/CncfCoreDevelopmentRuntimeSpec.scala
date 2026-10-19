package org.goldenport.cncf.phase51.build

import java.io.File
import java.net.{JarURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.yaml.snakeyaml.Yaml

/*
 * @since   Oct. 16, 2026
 * @version Oct. 19, 2026
 */
final class CncfCoreDevelopmentRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _selector = "TEXTUS_GOLDENPORT_CORE_CNCF_SOURCE_ROOT"
  private val _marker = "src/main/scala/org/goldenport/http/HttpResponse.scala"
  private val _expected_version = "0.4.3-SNAPSHOT"
  private val _expected_scala_version = "3.3.8"
  private val _coordinates = Vector("org.goldenport", "goldenport-core", _expected_version, _expected_scala_version)

  "CNCF core development source selection" should {
    "preserve absence and admit only explicit canonical sources" which {
      "CS1a: leaves the unset route independent of source or consumer markers" in {
        Given("no selector and a consumer directory that does not exist")
        _with_workspace("unset") { workspace =>
          val consumer = workspace.resolve("absent-consumer")

          When("the absent core source selection is resolved")
          val result = CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, None)

          Then("the default artifact route requires no source directory")
          result shouldBe Right(None)
        }
      }

      "CS1b: canonicalizes disjoint paths with spaces and dot spelling" in {
        Given("a consumer and a disjoint marked core checkout whose name contains spaces")
        _with_workspace("disjoint") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val source = _source(workspace.resolve("Core source with spaces"))
          val spelling = source.resolve("..").resolve(source.getFileName).resolve(".")
          val expected = source.toFile.getCanonicalFile

          When("the explicit normalized absolute path is selected")
          val result = CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(spelling.toString))

          Then("the selected canonical source is returned without searching for another checkout")
          result shouldBe Right(Some(expected))
        }
      }

      "CS1c: admits a direct lexical and canonical checkout child with a legal leaf" in {
        Given("the sole admitted contained source placement and a normalized dot spelling")
        _with_workspace("checkout-child") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val source = _source(consumer.resolve(".codex-workflow/checkouts/Core_1.2-dev"))
          val spelling = source.getParent.resolve(".").resolve(source.getFileName)
          val expected = source.toFile.getCanonicalFile

          When("the contained source is explicitly selected")
          val result = CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(spelling.toString))

          Then("the narrow checkout placement is admitted as its canonical directory")
          result shouldBe Right(Some(expected))
        }
      }
    }

    "reject invalid explicit paths without artifact fallback" which {
      "CS2a: identifies blank, relative, and uncanonicalizable selectors" in {
        Given("a consumer and explicit values that cannot name an absolute canonical source")
        _with_workspace("invalid-spelling") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val selections = Vector("", " \t ", "relative/core", workspace.resolve("core").toString + "\u0000")

          When("each present selector is resolved")
          val diagnostics = selections.map(selection => _diagnostics(
            CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(selection))
          ).mkString("\n"))

          Then("every rejection names the exact selector and its path failure")
          diagnostics.foreach(_ should include(_selector))
          diagnostics(0) should include("empty or whitespace-only")
          diagnostics(1) should include("empty or whitespace-only")
          diagnostics(2) should include("must be absolute")
          diagnostics(3) should include("cannot canonicalize")
        }
      }

      "CS2b: requires both exact source markers" in {
        Given("disjoint selections missing both markers, the HTTP source, or build.sbt")
        _with_workspace("missing-markers") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val absent = workspace.resolve("absent")
          val buildonly = workspace.resolve("build-only")
          val httponly = workspace.resolve("http-only")
          _write(buildonly.resolve("build.sbt"), "// fixture build\n")
          _write(httponly.resolve(_marker), "package org.goldenport.http\n")

          When("each incomplete source selection is resolved")
          val diagnostics = Vector(absent, buildonly, httponly).map(source => _diagnostics(
            CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))
          ))

          Then("the exact missing markers are reported without another source or artifact route")
          diagnostics.head should have size 2
          diagnostics.head.mkString should include("build.sbt")
          diagnostics.head.mkString should include(_marker)
          diagnostics(1).mkString should include(_marker)
          diagnostics(2).mkString should include("build.sbt")
          diagnostics.flatten.foreach(_ should include(_selector))
        }
      }

      "CS2c: rejects either required marker escaping through a symlink" in {
        Given("two marked source directories whose build or HTTP marker links outside the selected root")
        _with_workspace("marker-escape") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val sources = Vector("build.sbt", _marker).zipWithIndex.map { case (marker, index) =>
            val source = _source(workspace.resolve(s"core-$index"))
            val external = _write(workspace.resolve(s"external-marker-$index"), "// external fixture\n")
            Files.delete(source.resolve(marker))
            Files.createSymbolicLink(source.resolve(marker), external)
            source
          }

          When("the marked sources are explicitly selected")
          val diagnostics = sources.map(source => _diagnostics(
            CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))
          ).mkString("\n"))

          Then("both existing marker files are rejected because their canonical targets escape")
          diagnostics.foreach { diagnostic =>
            diagnostic should include(_selector)
            diagnostic should include("source marker must remain within selected source")
          }
          diagnostics.head should include("build.sbt")
          diagnostics.last should include(_marker)
        }
      }

      "CS2d: rejects self, source ancestors, and ordinary nested roots including aliases" in {
        Given("source-marked self, ancestor, and ordinary nested directories and their disjoint symlink spellings")
        _with_workspace("overlap") { workspace =>
          val ancestor = _source(workspace.resolve("ancestor"))
          val consumer = _source(ancestor.resolve("consumer"))
          val nested = _source(consumer.resolve("ordinary-core"))
          val aliases = Vector(consumer, ancestor, nested).zipWithIndex.map { case (source, index) =>
            Files.createSymbolicLink(workspace.resolve(s"alias-$index"), source)
          }
          val selections = Vector(consumer, ancestor, nested) ++ aliases

          When("each overlapping source or canonical alias is resolved")
          val diagnostics = selections.map(source => _diagnostics(
            CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))
          ).mkString("\n"))

          Then("canonical overlap is rejected regardless of lexical alias spelling")
          Vector(0, 3).foreach(index => diagnostics(index) should include("cannot select itself"))
          Vector(1, 4).foreach(index => diagnostics(index) should include("consumer must not be inside source"))
          Vector(2, 5).foreach(index => diagnostics(index) should include("direct lexical and canonical"))
          diagnostics.foreach(_ should include(_selector))
        }
      }

      "CS2e: rejects deep checkout children and illegal direct leaves" in {
        Given("marked checkout paths with extra depth, hidden names, leading hyphens, or spaces")
        _with_workspace("checkout-shapes") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val checkoutparent = consumer.resolve(".codex-workflow/checkouts")
          val sources = Vector("group/core", ".hidden", "-core", "Core source").map(name =>
            _source(checkoutparent.resolve(name))
          )

          When("each contained selection is resolved")
          val diagnostics = sources.map(source => _diagnostics(
            CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))
          ).mkString("\n"))

          Then("having both markers does not broaden the direct legal-leaf exception")
          diagnostics.foreach { diagnostic =>
            diagnostic should include(_selector)
            diagnostic should include("direct lexical and canonical .codex-workflow/checkouts child")
          }
        }
      }

      "CS2f: rejects a checkout leaf symlink escaping the consumer" in {
        Given("a legal direct checkout leaf that links to an external marked core source")
        _with_workspace("leaf-escape") { workspace =>
          val consumer = Files.createDirectories(workspace.resolve("consumer"))
          val external = _source(workspace.resolve("external-core"))
          val checkoutparent = Files.createDirectories(consumer.resolve(".codex-workflow/checkouts"))
          val alias = Files.createSymbolicLink(checkoutparent.resolve("core"), external)

          When("the consumer-contained lexical source is selected")
          val diagnostics = _diagnostics(CncfCoreDevelopmentBuild.resolveSourceDirectory(
            consumer.toFile, Some(alias.toString)
          )).mkString("\n")

          Then("canonical disjointness cannot conceal the leaf escape")
          diagnostics should include(_selector)
          diagnostics should include("must not canonically escape")
        }
      }

      "CS2g: rejects symlinked checkout namespaces pointing outside or deeper inside the consumer" in {
        Given("two consumers whose checkout namespaces link to different canonical parents")
        _with_workspace("namespace-escape") { workspace =>
          val selections = Vector(false, true).zipWithIndex.map { case (inside, index) =>
            val consumer = Files.createDirectories(workspace.resolve(s"consumer-$index"))
            val actualparent = if (inside) consumer.resolve("nested/checkouts") else workspace.resolve("external-checkouts")
            _source(actualparent.resolve("core"))
            val namespace = Files.createDirectories(consumer.resolve(".codex-workflow"))
            Files.createSymbolicLink(namespace.resolve("checkouts"), actualparent)
            consumer -> namespace.resolve("checkouts/core")
          }

          When("the apparent direct child of each symlinked namespace is selected")
          val diagnostics = selections.map { case (consumer, source) =>
            _diagnostics(CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))).mkString("\n")
          }

          Then("neither an external namespace escape nor an internal deeper namespace gains admission")
          diagnostics.foreach(_ should include(_selector))
          diagnostics.head should include("must not canonically escape")
          diagnostics.last should include("direct lexical and canonical")
        }
      }
    }

    "validate actual loaded coordinate values" which {
      "CS2h: accepts the exact identity and reports every mismatched dimension" in {
        Given("the required loaded organization, moduleName, version, and Scala version")
        val labels = Vector("organization", "moduleName", "version", "scalaVersion")
        val mismatches = _coordinates.indices.map(index => _coordinates.updated(index, _coordinates(index) + "-different")).toVector

        When("the exact identity, each single mismatch, and all mismatches are validated")
        val accepted = _validate(_coordinates)
        val rejected = mismatches.map(values => _diagnostics(_validate(values)))
        val combined = _diagnostics(_validate(_coordinates.map(_ + "-different")))

        Then("only exact loaded coordinates succeed and each rejected dimension names its expected and actual values")
        accepted shouldBe Right(())
        rejected.zipWithIndex.foreach { case (diagnostics, index) =>
          diagnostics should have size 1
          diagnostics.head should include(_selector)
          diagnostics.head should include(labels(index))
          diagnostics.head should include(s"expected '${_coordinates(index)}'")
          diagnostics.head should include(s"actual '${_coordinates(index)}-different'")
        }
        combined should have size 4
        combined.zip(labels).foreach { case (diagnostic, label) => diagnostic should include(label) }
      }

      "CS2i: preserves generated valid sources and rejects generated invalid paths and coordinates" in {
        Given("bounded legal names, disjoint or direct-checkout placements, and invalid selection categories")
        val names = Gen.choose(1, 24).flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(chars => "Core_" + chars.mkString))
        val cases = for {
          name <- names
          contained <- Gen.oneOf(false, true)
          dotted <- Gen.oneOf(false, true)
          invalid <- Gen.choose(0, 4)
          dimension <- Gen.choose(0, 3)
        } yield (name, contained, dotted, invalid, dimension)
        val property = Prop.forAll(cases) { case (name, contained, dotted, invalid, dimension) =>
          _with_workspace("generated") { workspace =>
            Given("one generated valid source, an invalid explicit path, and a changed loaded coordinate")
            val consumer = _source(workspace.resolve("consumer"))
            val source = _source(if (contained) consumer.resolve(".codex-workflow/checkouts").resolve(name)
              else workspace.resolve("Sources with spaces").resolve(name))
            val spelling = if (dotted) source.resolve("..").resolve(name).resolve(".") else source
            val expected = source.toFile.getCanonicalFile
            val invalidselection = invalid match {
              case 0 => " \t "
              case 1 => "relative/" + name
              case 2 => consumer.toString
              case 3 => _source(consumer.resolve("ordinary").resolve(name)).toString
              case _ => workspace.resolve("missing").resolve(name).toString
            }
            val mismatch = _coordinates.updated(dimension, _coordinates(dimension) + "-different")

            When("valid and invalid source paths and loaded coordinates are resolved through the pure helper")
            val admitted = CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(spelling.toString))
            val rejectedpath = _diagnostics(CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(invalidselection)))
            val admittedcoordinates = _validate(_coordinates)
            val rejectedcoordinates = _diagnostics(_validate(mismatch))

            Then("canonical valid selection and exact identity survive while explicit invalid cases never fall back")
            admitted shouldBe Right(Some(expected))
            admittedcoordinates shouldBe Right(())
            rejectedpath should not be empty
            rejectedcoordinates should have size 1
            (rejectedpath ++ rejectedcoordinates).foreach(_ should include(_selector))
            true
          }
        }

        When("ScalaCheck evaluates sixty successful generated helper cases without discards")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), property)

        Then("at least fifty generated cases satisfy both selection and loaded-coordinate contracts")
        result.passed shouldBe true
        result.succeeded should be >= 50
        result.discarded shouldBe 0
      }
    }

    "retain one executing core owner and logical runtime metadata" which {
      "CS3: observes actual selected-source classes, public status selection, and the generated descriptor" in {
        Given("the current explicit selector, the public HttpStatus class, and generated CNCF runtime metadata")
        val selection = sys.env.get(_selector)
        val consumer = new File(".").getCanonicalFile
        val loaded = classOf[HttpStatus]
        val resource = "org/goldenport/http/HttpStatus.class"

        When("the current source route, real class origin, visible resources, and descriptor are observed")
        val selected = CncfCoreDevelopmentBuild.resolveSourceDirectory(consumer, selection)
          .fold(errors => fail(errors.mkString("\n")), identity)
        val resources = loaded.getClassLoader.getResources(resource).asScala.toVector
        val owner = new File(loaded.getProtectionDomain.getCodeSource.getLocation.toURI).getCanonicalFile
        val resourcefiles = resources.filter(_.getProtocol == "file").map(url => new File(url.toURI).getCanonicalFile.toPath)
        val descriptor = _runtime_descriptor()
        val descriptorowner: Option[Path] = Option(descriptor._1).flatMap { url =>
          url.getProtocol match {
            case "file" => Some(new File(url.toURI).getCanonicalFile.toPath)
            case "jar" =>
              val connection = url.openConnection().asInstanceOf[JarURLConnection]
              val jarurl = connection.getJarFileURL
              if (connection.getEntryName == "META-INF/cncf/runtime.yaml" && jarurl.getProtocol == "file")
                Some(new File(jarurl.toURI).getCanonicalFile.toPath)
              else
                None
            case _ => None
          }
        }
        val sourcetarget = selected.map(source => new File(source, "target").getCanonicalFile.toPath)
        val selected204 = selected.map(_ => HttpStatus.fromInt(204).map(_.code))
        val response204 = selected.map(_ => HttpResponse.text(
          HttpStatus.fromInt(204).getOrElse(HttpStatus.InternalServerError), ""
        ).code)
        val named = Vector(200 -> HttpStatus.Ok, 201 -> HttpStatus.Created, 303 -> HttpStatus.SeeOther, 307 -> HttpStatus.TemporaryRedirect)
          .map { case (code, status) => HttpStatus.fromInt(code).map(_ eq status) }

        Then("the public named statuses and logical core baseProvided identity remain available in either mode")
        loaded.getName shouldBe "org.goldenport.http.HttpStatus"
        named shouldBe Vector.fill(4)(Some(true))
        resources should not be empty
        descriptor._2 should contain("org.goldenport:goldenport-core_3")
        selected match {
          case None =>
            selection shouldBe None
            selected204 shouldBe None
            response204 shouldBe None
          case Some(_) =>
            resources should have size 1
            resources.head.getProtocol shouldBe "file"
            owner.isDirectory shouldBe true
            owner.toPath.startsWith(sourcetarget.get) shouldBe true
            resourcefiles shouldBe Vector(owner.toPath.resolve(resource))
            descriptorowner.exists(_.startsWith(new File(consumer, "target").getCanonicalFile.toPath)) shouldBe true
            selected204 shouldBe Some(Some(204))
            response204 shouldBe Some(204)
        }
      }
    }
  }

  private def _validate(coordinates: Vector[String]): Either[Vector[String], Unit] =
    CncfCoreDevelopmentBuild.validateCoordinates(
      coordinates(0), coordinates(1), coordinates(2), coordinates(3), _expected_version, _expected_scala_version
    )

  private def _diagnostics[A](result: Either[Vector[String], A]): Vector[String] =
    result.fold(identity, value => fail(s"Expected explicit rejection, received $value"))

  private def _runtime_descriptor(): (URL, Vector[String]) = {
    val resource = Option(getClass.getClassLoader.getResource("META-INF/cncf/runtime.yaml"))
      .getOrElse(fail("The generated CNCF runtime descriptor must be available"))
    val input = resource.openStream()
    try {
      val document = new Yaml().load[java.util.Map[String, Object]](input)
      val provided = Option(document.get("baseProvided"))
        .map(_.asInstanceOf[java.util.List[String]].asScala.toVector)
        .getOrElse(fail("The generated CNCF runtime descriptor must contain baseProvided"))
      resource -> provided
    } finally input.close()
  }

  private def _source(directory: Path): Path = {
    _write(directory.resolve("build.sbt"), "// fixture build\n")
    _write(directory.resolve(_marker), "package org.goldenport.http\n")
    directory
  }

  private def _write(path: Path, text: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_workspace[A](label: String)(body: Path => A): A = {
    val parent = Files.createDirectories(Paths.get("target/cncf-core-development-runtime-spec/work").toAbsolutePath.normalize())
    val workspace = Files.createTempDirectory(parent, s"$label-").toFile.getCanonicalFile.toPath
    try body(workspace)
    finally {
      val stream = Files.walk(workspace)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
