package org.goldenport.cncf.component.capability

import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityPackageSource.*
import ComponentCapabilityProjectionCodecSpec._empty_projection
import ComponentCapabilityPackageLoaderSpec.{_with_directory, _write, _car, _inventory, _descriptor, _package}
import ComponentUsageCapabilityPackageLoaderSpec.*

/*
 * @since   Oct. 15, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentUsageCapabilityPackageLoaderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(id: String) = afterWord(s"in spec:component-usage-capability-package, example:$id, rules:R15, phase:78")
  private def _right[A](result: Either[Diagnostic, A]): A = result.fold(error => fail(error.toString), value => value)
  private def _left[A](result: Either[Diagnostic, A]): Diagnostic = result.fold(value => value, _ => fail("Expected Usage acquisition failure."))
  private def _acquired(result: Either[Diagnostic, Option[ComponentUsageCapabilityPackageLoader.Acquired]]): ComponentUsageCapabilityPackageLoader.Acquired = {
    _right(result).getOrElse(fail("Expected present Usage metadata."))
  }

  "Optional Component Usage package acquisition" should {
    "CUP01 retain raw Directory and CAR witnesses" should _metadata("CUP01") {
      "return identical original UTF-8 without extraction or semantic parsing" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R15; CUP01; canonical Usage-only Directory and CAR containing deliberately non-JSON Unicode text")
        _with_directory { root =>
          val selected = _usage_package(root)
          val car = _car(root)
          val carinput = selected.copy(location = Location.Car(car))
          val before = Files.readAllBytes(car).toVector
          val inventory = _inventory(root)
          When("both selected package locations acquire the optional Usage family")
          val directory = _acquired(ComponentUsageCapabilityPackageLoader.load(selected))
          val archive = _acquired(ComponentUsageCapabilityPackageLoader.load(carinput))
          Then("raw text and canonical path agree while the exact independently selected input and descriptor survive without writes")
          directory.content shouldBe _raw_usage
          archive.content shouldBe directory.content
          directory.resourcePath shouldBe _usage_path
          archive.resourcePath shouldBe _usage_path
          directory.input should be theSameInstanceAs selected
          archive.input should be theSameInstanceAs carinput
          archive.input.location should not be directory.input.location
          directory.descriptor.componentId shouldBe Some(selected.component.componentId)
          archive.descriptor.version shouldBe Some(selected.component.version)
          Files.readAllBytes(car).toVector shouldBe before
          _inventory(root) shouldBe inventory
        }
      }
    }
    "CUP02 coexist with Component contracts and configuration" should _metadata("CUP02") {
      "retain separate families in both package locations" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R14,R15; CUP02; a real Component projection, raw Usage text and ordinary config in one image")
        _with_directory { root =>
          val selected = _package(root, Vector("contract.json" -> _empty_projection))
          _write_usage(root)
          _write(root, "config/default.conf", "service.enabled = true\n")
          val carinput = selected.copy(location = Location.Car(_car(root)))
          When("each metadata family is acquired independently from Directory and CAR")
          val usages = Vector(selected, carinput).map(input => _acquired(ComponentUsageCapabilityPackageLoader.load(input)))
          val contracts = Vector(selected, carinput).map(input => _right(ComponentCapabilityPackageLoader.load(input)))
          Then("Usage remains uninterpreted and the original Component projection and config survive")
          usages.map(_.content) shouldBe Vector.fill(2)(_raw_usage)
          contracts.map(_.projections.map(_.projection)) shouldBe Vector.fill(2)(Vector(_empty_projection))
          Files.readString(root.resolve("config/default.conf")) shouldBe "service.enabled = true\n"
        }
      }
    }
    "CUP03 acquire a genuine Usage-only Component" should _metadata("CUP03") {
      "require neither a Component projection nor native targets" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R15; CUP03; only a canonical Component descriptor and the fixed Usage family")
        _with_directory { root =>
          val selected = _usage_package(root)
          val inventory = _inventory(root)
          When("Usage is acquired without any native provider, contract declaration or runtime Component")
          val acquired = _acquired(ComponentUsageCapabilityPackageLoader.load(selected))
          Then("the Usage witness succeeds with no fabricated Component Capability resource")
          acquired.content shouldBe _raw_usage
          inventory.exists(_.startsWith(metadataDirectory)) shouldBe false
          acquired.descriptor.componentId shouldBe Some(selected.component.componentId)
        }
      }
    }
    "CUP04 distinguish optional absence" should _metadata("CUP04") {
      "return None only for a valid selected package without Usage entries" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R15; CUP04; a canonical root descriptor with no meta directory, then an image with only Component metadata")
        _with_directory { root =>
          _write(root, "component-descriptor.json", _descriptor)
          val selected = _selection(root)
          When("the descriptor-only Directory and CAR are acquired")
          val bare = ComponentUsageCapabilityPackageLoader.load(selected)
          val barecar = ComponentUsageCapabilityPackageLoader.load(selected.copy(location = Location.Car(_car(root))))
          Then("missing meta is successful optional absence rather than an empty Usage model")
          bare shouldBe Right(None)
          barecar shouldBe Right(None)
          Given("R15 CUP04; the same selected Component now supplies only the separate Component family")
          _package(root, Vector.empty)
          When("the existing-family Directory and CAR are acquired for Usage")
          val existing = ComponentUsageCapabilityPackageLoader.load(selected)
          val existingcar = ComponentUsageCapabilityPackageLoader.load(selected.copy(location = Location.Car(_car(root))))
          Then("missing Usage family alone also remains optional absence")
          existing shouldBe Right(None)
          existingcar shouldBe Right(None)
        }
      }
    }
    "CUP05 validate absent-family descriptor and selection" should _metadata("CUP05") {
      "retain actual package failures and known scope context instead of returning None" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R15; CUP05; no Usage family and independently mismatched, malformed or missing package evidence")
        _with_directory { root =>
          _write(root, "component-descriptor.json", _descriptor.replace("Payments", "Different"))
          val selected = _selection(root)
          When("Usage is requested for the mismatched canonical Component")
          val mismatch = _left(ComponentUsageCapabilityPackageLoader.load(selected))
          Then("absence does not hide the selected Component mismatch")
          mismatch.kind shouldBe DiagnosticKind.Incompatible
          mismatch.location shouldBe "/descriptor/component"
          mismatch.scopeId shouldBe Some(selected.scopeId)
          mismatch.component shouldBe Some(selected.component.componentId)
          Given("R15 CUP05; the descriptor is invalid and a separate selection has no package root")
          _write(root, "component-descriptor.json", "{")
          val missing = selected.copy(location = Location.Directory(root.resolve("missing")))
          val invalid = selected.copy(component = null)
          When("invalid descriptor, missing root and invalid or null inputs are acquired")
          val descriptorerror = _left(ComponentUsageCapabilityPackageLoader.load(selected))
          val rooterror = _left(ComponentUsageCapabilityPackageLoader.load(missing))
          val selectionerror = _left(ComponentUsageCapabilityPackageLoader.load(invalid))
          val nullerror = _left(ComponentUsageCapabilityPackageLoader.load(null))
          Then("each failure remains typed at its owning boundary")
          descriptorerror.kind shouldBe DiagnosticKind.Malformed
          descriptorerror.location shouldBe "/descriptor"
          rooterror.kind shouldBe DiagnosticKind.Unavailable
          rooterror.location shouldBe "/location/root"
          selectionerror.location shouldBe "/package/component"
          nullerror.location shouldBe "/package"
        }
      }
    }
    "CUP06 preserve present resource failures" should _metadata("CUP06") {
      "use actual shared-reader diagnostics for missing index, malformed selection and missing payload" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R15; CUP06; independently broken present Usage families using the shared Directory/CAR fixture")
        Vector("index", "selection", "payload").foreach { variant =>
          _with_directory { root =>
            Given(s"R15 CUP06; a present Usage family whose $variant is broken in both physical representations")
            val selected = _usage_package(root)
            variant match {
              case "index" => Files.delete(root.resolve(_usage_index))
              case "selection" => _write(root, _usage_index, "{\"schemaVersion\":1,\"projections\":[\"other.json\"]}")
              case _ => Files.delete(root.resolve(_usage_path))
            }
            val carinput = selected.copy(location = Location.Car(_car(root)))
            When("the broken family is acquired from each selected location")
            val failures = Vector(selected, carinput).map(input => _left(ComponentUsageCapabilityPackageLoader.load(input)))
            Then("a present broken family never becomes optional absence")
            failures.map(_.kind) shouldBe Vector.fill(2)(if (variant == "selection") DiagnosticKind.Malformed else DiagnosticKind.Unavailable)
            failures.map(_.location) shouldBe Vector.fill(2)(variant match {
              case "index" => "/index"
              case "selection" => "/index/projections"
              case _ => "/" + _usage_path
            })
            failures.map(_.scopeId) shouldBe Vector.fill(2)(Some(selected.scopeId))
          }
        }
      }
    }
    "CUP07 retain explicit rejection" should _metadata("CUP07") {
      "forward the exact original diagnostic without wrapping" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R15; CUP07; an original unauthorized package observation")
        val diagnostic = Diagnostic(DiagnosticKind.Unauthorized, "Original selected package failure.", Some("scope:original"), None, None, "/observed/usage")
        When("the explicit rejection reaches Usage acquisition")
        val rejected = _left(ComponentUsageCapabilityPackageLoader.load(PackageInput.Rejected(diagnostic)))
        Then("the same diagnostic object and original pointer survive")
        rejected should be theSameInstanceAs diagnostic
      }
    }
    "CUP08 keep fixed index schema and selection" should _metadata("CUP08") {
      "reject empty Usage selection while retaining future-schema diagnostics" in {
        Given("Spec: docs/spec/component-usage-capability-package.md; R15; CUP08; a present family with an empty selection and a separately authored future integer index schema")
        _with_directory { root =>
          val selected = _usage_package(root)
          _write(root, _usage_index, "{\"schemaVersion\":1,\"projections\":[]}")
          When("the empty Usage index is acquired")
          val empty = _left(ComponentUsageCapabilityPackageLoader.load(selected))
          Then("Usage requires its sole fixed resource rather than accepting an empty family")
          empty.kind shouldBe DiagnosticKind.Malformed
          empty.location shouldBe "/index/projections"
          Given("R15 CUP08; the same present family declares future integer schema 2")
          _write(root, _usage_index, "{\"schemaVersion\":2,\"projections\":[\"usage.json\"]}")
          When("the future Usage index is acquired")
          val future = _left(ComponentUsageCapabilityPackageLoader.load(selected))
          Then("the shared index decoder retains Unsupported at the schema pointer")
          future.kind shouldBe DiagnosticKind.Unsupported
          future.location shouldBe "/index/schemaVersion"
        }
      }
    }
  }
}

object ComponentUsageCapabilityPackageLoaderSpec {
  private[capability] val _usage_index = "meta/component-usage-capability/index.json"
  private[capability] val _usage_path = "meta/component-usage-capability/usage.json"
  private[capability] val _raw_usage = "  original opaque Usage — 利用\nnot a JSON envelope\n"

  private[capability] def _selection(root: Path): PackageInput.Selected = {
    PackageInput.Selected("scope:usage", Location.Directory(root), _empty_projection.component, _empty_projection.packageCoordinate)
  }

  private[capability] def _write_usage(root: Path): Unit = {
    _write(root, _usage_index, "{\"schemaVersion\":1,\"projections\":[\"usage.json\"]}")
    _write(root, _usage_path, _raw_usage)
  }

  private[capability] def _usage_package(root: Path): PackageInput.Selected = {
    _write(root, "component-descriptor.json", _descriptor)
    _write_usage(root)
    _selection(root)
  }
}
