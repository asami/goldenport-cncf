package org.goldenport.cncf.component.capability

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityPackageSource.*
import ComponentCapabilityProjection.*
import ComponentCapabilityProjectionCodecSpec._empty_projection
import ComponentCapabilityPackageLoaderSpec.*

/*
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentCapabilityPackageLoaderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(id: String) = afterWord(s"in spec:R14, example:$id, rules:R14, phase:78")
  private def _right[A](result: Either[Diagnostic, A]): A = result.fold(error => fail(error.toString), value => value)
  private def _left[A](result: Either[Diagnostic, A]): Diagnostic = result.fold(value => value, _ => fail("Expected package rejection."))

  "Canonical package acquisition" should {
    "CPL17 coexist with optional Usage and configuration" should _metadata("CPL17") {
      "keep Component admission independent of raw Usage content" in {
        Given("Spec: docs/spec/component-capability.md; R14,R15; CPL17; an original Component projection alongside deliberately opaque Usage and config")
        _with_directory { root =>
          val selected = _package(root, Vector("first.json" -> _empty_projection))
          ComponentUsageCapabilityPackageLoaderSpec._write_usage(root)
          _write(root, "config/default.conf", "component.enabled = true\n")
          val carinput = selected.copy(location = Location.Car(_car(root)))
          When("the Component family is acquired from Directory and CAR")
          val results = Vector(selected, carinput).map(input => _right(ComponentCapabilityPackageLoader.load(input)))
          Then("only the original indexed Component projection is decoded and admitted to acquisition")
          results.map(_.index) shouldBe Vector.fill(2)(Vector("first.json"))
          results.map(_.projections.map(_.projection)) shouldBe Vector.fill(2)(Vector(_empty_projection))
          Files.readString(root.resolve("config/default.conf")) shouldBe "component.enabled = true\n"
        }
      }
    }
    "CPL01 preserve Directory projection provenance" should _metadata("CPL01") {
      "retain original values independently of resource spelling" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL01; a canonical directory and literal R11 input")
        _with_directory { root =>
          val projection = _empty_projection
          val input = _package(root, Vector("arbitrary-name.json" -> projection))
          val text = Files.readString(root.resolve(metadataDirectory).resolve("arbitrary-name.json"))
          When("the selected package is acquired")
          val acquired = _right(ComponentCapabilityPackageLoader.load(input))
          Then("the selected input, original source and raw JSON survive")
          acquired.input should be theSameInstanceAs input
          acquired.projections.head.projection shouldBe projection
          acquired.projections.head.input shouldBe SourceInput.Acquired(input.scopeId, text)
          acquired.projections.head.resourcePath shouldBe metadataDirectory + "/arbitrary-name.json"
          acquired.projections.head.projection.source.path shouldBe "models/test.cml"
        }
      }
    }
    "CPL02 read CAR metadata" should _metadata("CPL02") {
      "leave archive bytes and directory inventory unchanged" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL02; the same layout in a real ZIP CAR")
        _with_directory { root =>
          val selected = _package(root, Vector("first.json" -> _empty_projection))
          val car = _car(root)
          val before = Files.readAllBytes(car).toVector
          val inventory = _inventory(root)
          When("the CAR location is acquired without extraction")
          val acquired = _right(ComponentCapabilityPackageLoader.load(selected.copy(location = Location.Car(car))))
          Then("semantic metadata is read while archive and surrounding files stay intact")
          acquired.projections.head.projection shouldBe _empty_projection
          Files.readAllBytes(car).toVector shouldBe before
          _inventory(root) shouldBe inventory
        }
      }
    }
    "CPL03 distinguish acquired empty" should _metadata("CPL03") {
      "accept the explicit empty index" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL03; a canonical descriptor and empty index")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          When("the empty selection is acquired")
          val result = _right(ComponentCapabilityPackageLoader.load(selected))
          Then("empty is retained as successful acquisition")
          result.index shouldBe Vector.empty
          result.projections shouldBe Vector.empty
        }
      }
    }
    "CPL04 preserve explicit rejection" should _metadata("CPL04") {
      "forward original stale and unauthorized observations" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL04; independently rejected package observations")
        val diagnostics = Vector(DiagnosticKind.Stale, DiagnosticKind.Unauthorized).map(kind => Diagnostic(kind, "Observed failure.", Some("scope:observed"), None, Some(_empty_projection.source), "/observed"))
        When("rejected packages reach the loader and discovery with null provider")
        val results = diagnostics.map(value => (_left(ComponentCapabilityPackageLoader.load(PackageInput.Rejected(value))), _left(ComponentCapabilityDiscovery.discover(PackageInput.Rejected(value), null))))
        Then("the exact original diagnostic wins in both facades")
        results.zip(diagnostics).foreach { (pair, original) =>
          pair._1 should be theSameInstanceAs original
          pair._2 should be theSameInstanceAs original
        }
      }
    }
    "CPL05 preserve missing-resource outcomes" should _metadata("CPL05") {
      "reject absent root, index and listed resource" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL05; three independently missing selections")
        _with_directory { root =>
          val selected = _package(root, Vector("first.json" -> _empty_projection))
          val missingroot = selected.copy(location = Location.Directory(root.resolve("missing")))
          Files.delete(root.resolve(indexPath))
          When("root and index absence are observed, then the listed payload is missing")
          val first = Vector(_left(ComponentCapabilityPackageLoader.load(missingroot)), _left(ComponentCapabilityPackageLoader.load(selected)))
          _write(root, indexPath, _index(Vector("first.json")))
          Files.delete(root.resolve(metadataDirectory).resolve("first.json"))
          val payload = _left(ComponentCapabilityPackageLoader.load(selected))
          Then("each absence is Unavailable rather than an acquired empty batch")
          (first :+ payload).map(_.kind) shouldBe Vector.fill(3)(DiagnosticKind.Unavailable)
          payload.location shouldBe "/meta/component-capability/first.json"
        }
      }
    }
    "CPL06 enforce the closed index grammar" should _metadata("CPL06") {
      "reject malformed keys and types separately from a future integer version" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL06; real index files with invalid grammar")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          val bad = Vector("{}", "null", "{", "{\"schemaVersion\":1,\"projections\":null}", "{\"schemaVersion\":true,\"projections\":[]}", "{\"schemaVersion\":1.0,\"projections\":[]}", "{\"schemaVersion\":1,\"projections\":[],\"unknown\":0}", "{\"schemaVersion\":1,\"schemaVersion\":1,\"projections\":[]}", "{\"schemaVersion\":1,\"projections\":[null]}")
          When("each file is acquired through the loader")
          val failures = bad.map { text => _write(root, indexPath, text); _left(ComponentCapabilityPackageLoader.load(selected)) }
          _write(root, indexPath, "{\"schemaVersion\":2,\"projections\":[]}")
          val future = _left(ComponentCapabilityPackageLoader.load(selected))
          Then("malformed grammar is rejected and the future schema stays Unsupported")
          failures.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
          future.kind shouldBe DiagnosticKind.Unsupported
          future.location shouldBe "/index/schemaVersion"
        }
      }
    }
    "CPL07 refuse unsafe filenames" should _metadata("CPL07") {
      "reject aliases before trying any named resource" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL07; unsafe basenames in actual index files")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          val names = Vector("../escape.json", "x/y.json", "x\\y.json", "/absolute.json", "index.json", ".hidden.json", "https:remote.json")
          When("each literal index is acquired")
          val errors = names.map { name => _write(root, indexPath, _index(Vector(name))); _left(ComponentCapabilityPackageLoader.load(selected)) }
          Then("every alias fails at the index entry as Malformed")
          errors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
          errors.map(_.location).distinct shouldBe Vector("/index/projections/0")
          Given("R14 CPL07; actual CAR entries that alias the metadata prefix or reside entirely outside it")
          _write(root, indexPath, _index(Vector.empty))
          val aliases = Vector("meta/./component-capability/hidden.json", "meta/component-capability/../hidden.json", "meta\\component-capability\\hidden.json", "/meta/component-capability/hidden.json")
          When("each metadata alias is acquired and unrelated outside-prefix assets are ignored")
          val archiveerrors = aliases.map { name =>
            val car = _entry_car(root, name)
            _left(ComponentCapabilityPackageLoader.load(selected.copy(location = Location.Car(car))))
          }
          val outside = _entry_car(root, "unrelated/component-capability/broken.json")
          val accepted = _right(ComponentCapabilityPackageLoader.load(selected.copy(location = Location.Car(outside))))
          Then("unsafe canonical aliases are Malformed and outside-prefix JSON does not enter inventory")
          archiveerrors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
          accepted.projections shouldBe Vector.empty
        }
      }
    }
    "CPL08 refuse repeated metadata" should _metadata("CPL08") {
      "reject repeated names and duplicate exact ZIP entries" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL08; a repeated index name and duplicate index/payload CARs")
        _with_directory { root =>
          val selected = _package(root, Vector("first.json" -> _empty_projection))
          _write(root, indexPath, _index(Vector("first.json", "first.json")))
          When("the repeated index and both duplicate-entry archives are acquired")
          val repeated = _left(ComponentCapabilityPackageLoader.load(selected))
          _write(root, indexPath, _index(Vector("first.json")))
          val ziperrors = Vector(indexPath, metadataDirectory + "/first.json").map { name =>
            val car = _duplicate_car(root, name)
            _left(ComponentCapabilityPackageLoader.load(selected.copy(location = Location.Car(car))))
          }
          Then("second index and archive entries are Ambiguous, never deduplicated")
          repeated.kind shouldBe DiagnosticKind.Ambiguous
          repeated.location shouldBe "/index/projections/1"
          ziperrors.map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Ambiguous)
        }
      }
    }
    "CPL09 require strict bytes and original R11" should _metadata("CPL09") {
      "reject UTF-8, JSON, schema and ABI failures in a complete batch" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL09; valid first resource and independently invalid later bytes")
        _with_directory { root =>
          val second = _empty_projection.copy(source = _empty_projection.source.copy(sourceRevision = "second"))
          val selected = _package(root, Vector("first.json" -> _empty_projection, "later.json" -> second))
          val json = _json(second)
          val bytes = Vector(Array(0xc3.toByte, 0x28.toByte), "{".getBytes(StandardCharsets.UTF_8), json.replace("\"schemaVersion\":1", "\"schemaVersion\":2").getBytes(StandardCharsets.UTF_8), json.replace("cncf.component-capability.v1", "other-abi").getBytes(StandardCharsets.UTF_8))
          When("the complete package is acquired for each later resource")
          val errors = bytes.map { value => Files.write(root.resolve(metadataDirectory).resolve("later.json"), value); _left(ComponentCapabilityPackageLoader.load(selected)) }
          Then("no partial batch survives and original header failures retain resource context")
          errors.map(_.kind) shouldBe Vector(DiagnosticKind.Malformed, DiagnosticKind.Malformed, DiagnosticKind.Unsupported, DiagnosticKind.Unsupported)
          errors.last.location shouldBe "/meta/component-capability/later.json/generatedAbiVersion"
        }
      }
    }
    "CPL10 refuse filesystem aliases" should _metadata("CPL10") {
      "reject symlink roots, metadata components and resources and nonfiles" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL10; canonical packages with independent symlink or directory mutations")
        val cases = Vector("root", "meta", "metadata", "index", "payload", "directory", "car")
        val errors = cases.map { variant => _with_directory { root =>
          Given(s"R14 CPL10; a package whose $variant path has an independently authored alias or kind mismatch")
          val selected = _package(root, Vector("first.json" -> _empty_projection))
          val path = variant match {
            case "meta" => root.resolve("meta")
            case "metadata" => root.resolve(metadataDirectory)
            case "index" => root.resolve(indexPath)
            case _ => root.resolve(metadataDirectory).resolve("first.json")
          }
          val input = variant match {
            case "root" =>
              val alias = root.resolve("alias")
              Files.createSymbolicLink(alias, root.toAbsolutePath)
              selected.copy(location = Location.Directory(alias))
            case "car" => selected.copy(location = Location.Car(root))
            case "directory" => Files.delete(path); Files.createDirectory(path); selected
            case _ =>
              val moved = root.resolve("original-" + variant)
              Files.move(path, moved)
              Files.createSymbolicLink(path, moved.toAbsolutePath)
              selected
          }
          When("this selected filesystem location is acquired")
          val error = _left(ComponentCapabilityPackageLoader.load(input))
          Then("the alias or nonfile is refused without following it")
          error.kind shouldBe DiagnosticKind.Malformed
          error
        } }
        Then("no alias is followed or directory parsed as a file")
        errors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
        Vector("meta/component-descriptor.json", indexPath, metadataDirectory + "/first.json").foreach { name => _with_directory { root =>
          Given(s"Spec: docs/spec/component-capability.md; R14; CPL10; a CAR whose canonical $name resource has Unix symbolic-link mode")
          val selected = _package(root, Vector("first.json" -> _empty_projection))
          val car = _symlink_car(root, name)
          When("the selected CAR metadata is acquired")
          val error = _left(ComponentCapabilityPackageLoader.load(selected.copy(location = Location.Car(car))))
          Then("the archive link remains a typed Malformed resource without following its bytes")
          error.kind shouldBe DiagnosticKind.Malformed
          error.location shouldBe "/" + name
        } }
      }
    }
    "CPL11 require canonical descriptor identity" should _metadata("CPL11") {
      "refuse legacy or invalid descriptors and exact-coordinate mismatches" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL11; independently varied actual canonical descriptors")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          val bad = Vector("{", "{\"name\":\"Payments\",\"version\":\"3.7.0-SNAPSHOT\"}", "{\"schemaVersion\":3,\"component\":{\"namespace\":\"org.example\",\"id\":\"Payments\"}}", "{\"schemaVersion\":3,\"component\":{\"namespace\":\"bad namespace\",\"id\":\"Payments\",\"version\":\"3.7.0-SNAPSHOT\"}}")
          When("each descriptor and then valid conflicting identities are acquired")
          val malformed = bad.map { text => _write(root, "meta/component-descriptor.json", text); _left(ComponentCapabilityPackageLoader.load(selected)) }
          val mismatch = Vector(_descriptor.replace("Payments", "Different"), _descriptor.replace("3.7.0-SNAPSHOT", "3.8.0-SNAPSHOT")).map { text => _write(root, "meta/component-descriptor.json", text); _left(ComponentCapabilityPackageLoader.load(selected)) }
          Files.delete(root.resolve("meta/component-descriptor.json"))
          val absent = _left(ComponentCapabilityPackageLoader.load(selected))
          Then("invalid or absent evidence is Malformed and exact mismatches are Incompatible")
          (malformed :+ absent).map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
          mismatch.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Incompatible)
        }
      }
    }
    "CPL12 compare independently selected coordinates" should _metadata("CPL12") {
      "reject component and package envelope differences" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL12; valid envelopes differing from selected observed coordinates")
        _with_directory { root =>
          val variants = Vector(_empty_projection.copy(component = _empty_projection.component.copy(version = "3.8.0-SNAPSHOT")), _empty_projection.copy(component = _empty_projection.component.copy(componentId = org.goldenport.cncf.component.ComponentId("org.example.Other"))), _empty_projection.copy(packageCoordinate = PackageCoordinate("package:other", "package-v21")), _empty_projection.copy(packageCoordinate = PackageCoordinate("package:test", "other")))
          When("each valid R11 envelope is acquired against the original selection")
          val errors = variants.map { value =>
            val selected = _package(root, Vector("first.json" -> value)).copy(component = _empty_projection.component, packageCoordinate = _empty_projection.packageCoordinate)
            _left(ComponentCapabilityPackageLoader.load(selected))
          }
          Then("coordinate mismatches retain the resource envelope pointer")
          errors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Incompatible)
          errors.map(_.location) shouldBe Vector.fill(2)("/meta/component-capability/first.json/component") ++ Vector.fill(2)("/meta/component-capability/first.json/package")
        }
      }
    }
    "CPL13 retain source snapshots and author order" should _metadata("CPL13") {
      "allow disjoint literal snapshots and reject an equal complete key" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL13; two source snapshots in reverse filename order")
        _with_directory { root =>
          val second = _empty_projection.copy(source = _empty_projection.source.copy(path = "models/second.cml", sourceRevision = "second"))
          val selected = _package(root, Vector("z.json" -> second, "a.json" -> _empty_projection))
          When("the ordered package and then a duplicate snapshot differing only in line are acquired")
          val acquired = _right(ComponentCapabilityPackageLoader.load(selected))
          _write(root, metadataDirectory + "/a.json", _json(second.copy(source = second.source.copy(line = Some(4)))))
          val error = _left(ComponentCapabilityPackageLoader.load(selected))
          Then("index order survives and line cannot distinguish a duplicate snapshot")
          acquired.index shouldBe Vector("z.json", "a.json")
          acquired.projections.map(_.projection) shouldBe Vector(second, _empty_projection)
          error.kind shouldBe DiagnosticKind.Ambiguous
          error.location shouldBe "/meta/component-capability/a.json/source"
        }
      }
    }
    "CPL14 bound acquisition resources" should _metadata("CPL14") {
      "reject index, resource, count and aggregate bounds before provider access" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL14; independently oversized actual resources")
        _with_directory { root =>
          var reads = 0
          val provider = new ComponentCapabilityMetadataProvider {
            def componentCapabilityMetadata: ComponentCapabilityMetadataProvider.NativeInput = { reads += 1; null }
          }
          val selected = _package(root, Vector("first.json" -> _empty_projection))
          When("each bound is exceeded in selected bytes and discovery attempts acquisition")
          _write(root, indexPath, " " * (1024 * 1024 + 1))
          val indexerror = _left(ComponentCapabilityDiscovery.discover(selected, provider))
          _write(root, indexPath, _index(Vector("first.json")))
          _write(root, metadataDirectory + "/first.json", " " * (16 * 1024 * 1024 + 1))
          val resourceerror = _left(ComponentCapabilityDiscovery.discover(selected, provider))
          _write(root, indexPath, _index((0 to 256).map(index => s"p$index.json").toVector))
          val counterror = _left(ComponentCapabilityDiscovery.discover(selected, provider))
          Files.delete(root.resolve(metadataDirectory).resolve("first.json"))
          val names = (0 until 5).map(index => s"p$index.json").toVector
          _write(root, indexPath, _index(names))
          names.zipWithIndex.foreach { (name, index) =>
            val text = _json(_empty_projection.copy(source = _empty_projection.source.copy(sourceRevision = s"revision-$index")))
            _write(root, metadataDirectory + "/" + name, text + " " * (14 * 1024 * 1024 - text.length))
          }
          val totalerror = _left(ComponentCapabilityDiscovery.discover(selected, provider))
          Then("all bounds are Malformed and no native provider is read")
          Vector(indexerror, resourceerror, counterror, totalerror).map(_.kind) shouldBe Vector.fill(4)(DiagnosticKind.Malformed)
          counterror.location shouldBe "/index/projections"
          reads shouldBe 0
        }
      }
    }
    "CPL15 require complete metadata inventory" should _metadata("CPL15") {
      "reject unlisted and nested JSON while ignoring unrelated assets" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL15; a valid package plus unrelated and canonical-prefix assets")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          _write(root, "unrelated/broken.json", "{")
          _write(root, metadataDirectory + "/readme.txt", "unrelated")
          When("the unrelated assets and then extra direct/nested JSON are acquired")
          val valid = _right(ComponentCapabilityPackageLoader.load(selected))
          val errors = Vector("extra.json", "nested/extra.json").map { name =>
            _write(root, metadataDirectory + "/" + name, "{")
            val result = _left(ComponentCapabilityPackageLoader.load(selected))
            Files.delete(root.resolve(metadataDirectory).resolve(name))
            result
          }
          Then("unrelated assets are ignored and every extra metadata JSON fails inventory")
          valid.projections shouldBe Vector.empty
          errors.map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Malformed)
        }
        _with_directory { root =>
          Given("Spec: docs/spec/component-capability.md; R14; CPL15; an empty Directory package with an unrelated dangling non-JSON metadata link")
          val selected = _package(root, Vector.empty)
          Files.createSymbolicLink(root.resolve(metadataDirectory).resolve("readme.txt"), root.resolve("missing-readme.txt").toAbsolutePath)
          When("the selected Directory package is acquired")
          val acquired = _right(ComponentCapabilityPackageLoader.load(selected))
          Then("the dangling non-JSON asset is ignored and the empty catalog is acquired")
          acquired.index shouldBe Vector.empty
          acquired.projections shouldBe Vector.empty
        }
        _with_directory { root =>
          Given("Spec: docs/spec/component-capability.md; R14; CPL15; an empty CAR package whose unrelated non-JSON readme has Unix symbolic-link mode")
          val selected = _package(root, Vector.empty)
          _write(root, metadataDirectory + "/readme.txt", "unrelated")
          val car = _symlink_car(root, metadataDirectory + "/readme.txt")
          When("the selected CAR package is acquired")
          val acquired = _right(ComponentCapabilityPackageLoader.load(selected.copy(location = Location.Car(car))))
          Then("the non-JSON archive link is ignored and the empty catalog is acquired")
          acquired.index shouldBe Vector.empty
          acquired.projections shouldBe Vector.empty
        }
      }
    }
    "CPL16 preserve literal safe-index permutations" should _metadata("CPL16") {
      "actively check at least one hundred actual package acquisitions" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPL16; safe ordered filename permutations and independent revisions")
        val names = Vector("Z-1.json", "a.b.json", "Third_9.json")
        val samples = Gen.oneOf(names.permutations.toVector)
        When("ScalaCheck acquires an actual directory for every sample")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(samples) { (order: Vector[String]) =>
          _with_directory { root =>
            val values = order.zipWithIndex.map { (name, index) => name -> _empty_projection.copy(source = _empty_projection.source.copy(sourceRevision = s"literal-$index")) }
            val result = ComponentCapabilityPackageLoader.load(_package(root, values))
            result.exists(value => value.index == order && value.projections.map(_.filename) == order && value.projections.map(_.projection) == values.map(_._2))
          }
        })
        Then("actual successful and discard counts meet the property contract")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }
}

object ComponentCapabilityPackageLoaderSpec {
  private val _work_root = Path.of("target/cncf-test/work/component-capability-packages")
  private[capability] val _descriptor: String = "{\"schemaVersion\":3,\"component\":{\"namespace\":\"org.example\",\"id\":\"Payments\",\"version\":\"3.7.0-SNAPSHOT\"}}"

  private[capability] def _with_directory[A](body: Path => A): A = {
    Files.createDirectories(_work_root)
    val root = Files.createTempDirectory(_work_root, "package-")
    try body(root) finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path)) finally stream.close()
    }
  }

  private[capability] def _write(root: Path, resource: String, text: String): Unit = {
    val path = root.resolve(resource)
    Files.createDirectories(path.getParent)
    Files.writeString(path, text, StandardCharsets.UTF_8)
    ()
  }

  private[capability] def _index(names: Vector[String]): String = {
    io.circe.Json.obj("schemaVersion" -> io.circe.Json.fromInt(1), "projections" -> io.circe.Json.fromValues(names.map(io.circe.Json.fromString))).noSpaces
  }

  private[capability] def _json(projection: Projection): String = ComponentCapabilityProjectionCodec.encode(projection).fold(error => throw new IllegalArgumentException(error.toString), identity)

  private[capability] def _package(root: Path, values: Vector[(String, Projection)]): PackageInput.Selected = {
    _write(root, "meta/component-descriptor.json", _descriptor)
    _write(root, indexPath, _index(values.map(_._1)))
    values.foreach { (name, projection) => _write(root, metadataDirectory + "/" + name, _json(projection)) }
    PackageInput.Selected("scope:actual-targets", Location.Directory(root), _empty_projection.component, _empty_projection.packageCoordinate)
  }

  private[capability] def _inventory(root: Path): Vector[String] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.map(root.relativize(_).toString).toVector.sorted finally stream.close()
  }

  private[capability] def _car(root: Path): Path = {
    val car = root.resolve("selected.car")
    val stream = Files.walk(root)
    val paths = try stream.iterator().asScala.filter(Files.isRegularFile(_)).filterNot(_.toString.endsWith(".car")).toVector finally stream.close()
    val zip = new ZipOutputStream(Files.newOutputStream(car))
    try paths.foreach { path =>
      zip.putNextEntry(new ZipEntry(root.relativize(path).toString.replace('\\', '/')))
      zip.write(Files.readAllBytes(path))
      zip.closeEntry()
    } finally zip.close()
    car
  }

  private def _duplicate_car(root: Path, name: String): Path = {
    val car = root.resolve("duplicate.car")
    val alias = name.dropRight(1) + "X"
    val zip = new ZipOutputStream(Files.newOutputStream(car))
    try Vector("meta/component-descriptor.json" -> _descriptor, indexPath -> _index(Vector("first.json")), metadataDirectory + "/first.json" -> _json(_empty_projection), alias -> "{}").foreach { (entry, text) =>
      zip.putNextEntry(new ZipEntry(entry))
      zip.write(text.getBytes(StandardCharsets.UTF_8))
      zip.closeEntry()
    } finally zip.close()
    // Replace only equal-length ZIP filename bytes, retaining both central entries.
    val bytes = new String(Files.readAllBytes(car), StandardCharsets.ISO_8859_1).replace(alias, name).getBytes(StandardCharsets.ISO_8859_1)
    Files.write(car, bytes)
    car
  }

  private def _entry_car(root: Path, name: String): Path = {
    val car = root.resolve("entry.car")
    val zip = new ZipOutputStream(Files.newOutputStream(car))
    try Vector("meta/component-descriptor.json" -> _descriptor, indexPath -> _index(Vector.empty), name -> "{").foreach { (entry, text) =>
      zip.putNextEntry(new ZipEntry(entry))
      zip.write(text.getBytes(StandardCharsets.UTF_8))
      zip.closeEntry()
    } finally zip.close()
    car
  }

  private def _symlink_car(root: Path, name: String): Path = {
    val car = _car(root)
    val bytes = Files.readAllBytes(car)
    val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val offsets = (0 until bytes.length - 46).filter { index =>
      buffer.getInt(index) == 0x02014b50 && {
        val length = buffer.getShort(index + 28).toInt & 0xffff
        index + 46 + length <= bytes.length && new String(bytes, index + 46, length, StandardCharsets.UTF_8) == name
      }
    }
    offsets.foreach { index =>
      buffer.putShort(index + 4, (3 << 8 | 20).toShort)
      buffer.putInt(index + 38, 0xa1ff0000)
    }
    Files.write(car, bytes)
    car
  }
}
