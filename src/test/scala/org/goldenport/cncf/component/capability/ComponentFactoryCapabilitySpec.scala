package org.goldenport.cncf.component.capability {
  import java.nio.file.Files
  import org.scalatest.GivenWhenThen
  import org.scalatest.matchers.should.Matchers
  import org.scalatest.wordspec.AnyWordSpec
  import org.goldenport.cncf.component.ComponentFactory
  import ComponentCapabilityAdmissionScope.*
  import ComponentCapabilityPackageSource.*
  import ComponentCapabilityMetadataProvider.*
  import ComponentCapabilityProjectionCodecSpec._empty_projection
  import ComponentCapabilityPackageLoaderSpec.{_with_directory, _package, _write, _car, _descriptor, _json}
  import ComponentCapabilityDiscoverySpec.{_native, _projection, _snapshots, CountingProvider}
  import ComponentUsageCapabilityPackageLoaderSpec.{_usage_package, _raw_usage, _usage_path}

  /*
   * @since   Oct. 14, 2026
   * @version Oct. 15, 2026
   * @author  ASAMI, Tomoharu
   */
  final class ComponentFactoryCapabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
    private def _metadata(id: String) = afterWord(s"in spec:R14, example:$id, rules:R14, phase:78")
    private def _right[A](result: Either[Diagnostic, A]): A = result.fold(error => fail(error.toString), value => value)
    private def _left[A](result: Either[Diagnostic, A]): Diagnostic = result.fold(value => value, _ => fail("Expected Factory metadata rejection."))

    "ComponentFactory static metadata entry" should {
      "CPF07 expose public opaque Usage acquisition" should afterWord("in spec:component-usage-capability-package, example:CPF07, rules:R15, phase:78") {
        "let an external namespace consume the exact public acquired type" in {
          Given("Spec: docs/spec/component-usage-capability-package.md; R15; CPF07; a Usage-only CAR with opaque non-JSON content, poison runtime names and invalid JAR bytes")
          _with_directory { root =>
            val selected = _usage_package(root)
            val poison = _descriptor.dropRight(1) + ",\"componentlets\":[{\"name\":\"Poison\",\"implementationClass\":\"missing.must.not.Load\",\"factoryObject\":\"missing.must.not.Initialize\"}]}"
            _write(root, "component-descriptor.json", poison)
            _write(root, "component/poison.jar", "Not a runtime JAR.")
            val input = selected.copy(location = Location.Car(_car(root)))
            When("the external client invokes the public companion Usage API")
            val result: Option[ComponentUsageCapabilityPackageLoader.Acquired] = _right(_root_.capability.client.PublicCapabilityClient.acquireUsage(input))
            Then("the public carrier exposes exact raw transport without Usage parsing or native activation")
            val acquired = result.getOrElse(fail("Expected public Usage witness."))
            acquired.input should be theSameInstanceAs input
            _root_.capability.client.PublicCapabilityClient.usageContent(acquired) shouldBe _raw_usage
            acquired.resourcePath shouldBe _usage_path
            acquired.descriptor.componentlets.head.implementationClass shouldBe Some("missing.must.not.Load")
          }
        }
      }
      "CPF08 expose runtime-independent instance Usage acquisition" should afterWord("in spec:component-usage-capability-package, example:CPF08, rules:R15, phase:78") {
        "delegate directly despite poison runtime dependencies" in {
          Given("Spec: docs/spec/component-usage-capability-package.md; R15; CPF08; a Usage-only Directory and a Factory with null runtime repository and collaborators")
          _with_directory { root =>
            val input = _usage_package(root)
            val factory = new ComponentFactory(_component_repository_space = null, _collaborators = null, workingsetclock = java.time.Clock.systemUTC())
            When("the public instance acquires Usage without a native provider")
            val result = _right(factory.acquireUsageCapabilities(input))
            Then("the same selected package and uninterpreted content are available independently of runtime bootstrap")
            val acquired = result.getOrElse(fail("Expected instance Usage witness."))
            acquired.input should be theSameInstanceAs input
            acquired.content shouldBe _raw_usage
            acquired.resourcePath shouldBe _usage_path
          }
        }
      }
      "CPF01 expose companion discovery" should _metadata("CPF01") {
        "return nonempty admitted metadata without a Factory instance" in {
          Given("Spec: docs/spec/component-capability.md; R14; CPF01; an actual selected package and pure native provider")
          _with_directory { root =>
            val nativeinput = _native
            val projection = _projection(nativeinput.bindings, "first")
            val selected = _package(root, Vector("first.json" -> projection))
            val provider = new CountingProvider(nativeinput)
            When("the public companion discovers supplied metadata")
            val result = _right(ComponentFactory.discoverCapabilities(selected, provider))
            Then("the original admitted projection and native target objects are available")
            result.entries.map(_.admitted.projection) shouldBe Vector(projection)
            result.entries.head.realizations.size shouldBe 3
            result.nativeInput should be theSameInstanceAs nativeinput
            provider.reads shouldBe 1
          }
        }
      }
      "CPF02 expose independent instance discovery" should _metadata("CPF02") {
        "consume metadata with poison runtime dependencies" in {
          Given("Spec: docs/spec/component-capability.md; R14; CPF02; a Factory with null runtime repository/collaborator dependencies")
          _with_directory { root =>
            val nativeinput = _native
            val projection = _projection(nativeinput.bindings, "first")
            val selected = _package(root, Vector("first.json" -> projection))
            val factory = new ComponentFactory(_component_repository_space = null, _collaborators = null, workingsetclock = java.time.Clock.systemUTC())
            When("the public instance discovers selected static metadata")
            val result = _right(factory.discoverCapabilities(selected, new CountingProvider(nativeinput)))
            Then("discovery succeeds without repository or bootstrap access")
            result.entries.head.admitted.projection shouldBe projection
            result.entries.head.realizations.map(_.binding) shouldBe nativeinput.bindings
          }
        }
      }
      "CPF03 avoid CAR runtime loading" should _metadata("CPF03") {
        "accept static metadata despite poison class names and invalid JAR contents" in {
          Given("Spec: docs/spec/component-capability.md; R14; CPF03; canonical CAR descriptor metadata and unusable runtime payloads")
          _with_directory { root =>
            val nativeinput = _native
            val selected = _package(root, Vector("first.json" -> _projection(nativeinput.bindings, "first")))
            val poison = _descriptor.dropRight(1) + ",\"componentlets\":[{\"name\":\"Poison\",\"implementationClass\":\"missing.must.not.Load\",\"factoryObject\":\"missing.must.not.Initialize\"}]}"
            _write(root, "meta/component-descriptor.json", poison)
            _write(root, "component/poison.jar", "This is deliberately not a runtime JAR.")
            val car = _car(root)
            val before = Files.readAllBytes(car).toVector
            When("the companion acquires and discovers this CAR's static metadata")
            val result = _right(ComponentFactory.discoverCapabilities(selected.copy(location = Location.Car(car)), new CountingProvider(nativeinput)))
            Then("metadata succeeds and the poison descriptor/payload remain data")
            result.entries.head.realizations.size shouldBe 3
            result.acquired.descriptor.componentlets.head.implementationClass shouldBe Some("missing.must.not.Load")
            result.acquired.descriptor.componentlets.head.factoryObject shouldBe Some("missing.must.not.Initialize")
            Files.readAllBytes(car).toVector shouldBe before
          }
        }
      }
      "CPF04 preserve typed Factory failures" should _metadata("CPF04") {
        "forward acquisition and original admission failure without retry" in {
          Given("Spec: docs/spec/component-capability.md; R14; CPF04; explicit package rejection and a dangling target edge")
          _with_directory { root =>
            val diagnostic = Diagnostic(DiagnosticKind.Unauthorized, "Selected package denied.", None, None, None, "/observed/package")
            val nativeinput = _native
            val projection = _projection(nativeinput.bindings, "first")
            val selected = _package(root, Vector("first.json" -> projection))
            val provider = new CountingProvider(nativeinput.copy(bindings = Vector.empty))
            When("the companion receives rejection and a package whose target catalog is empty")
            val rejected = _left(ComponentFactory.discoverCapabilities(PackageInput.Rejected(diagnostic), null))
            val admission = _left(ComponentFactory.discoverCapabilities(selected, provider))
            Then("original diagnostics remain typed and the provider has no retry")
            rejected should be theSameInstanceAs diagnostic
            admission.kind shouldBe DiagnosticKind.Malformed
            admission.location shouldBe "/declarations/0/realizations/value/0/target"
            provider.reads shouldBe 1
          }
        }
      }
      "CPF05 support public external consumers" should _metadata("CPF05") {
        "let a client outside org.goldenport.cncf create a provider and consume original definitions" in {
          Given("Spec: docs/spec/component-capability.md; R14; CPF05; only public package/native values supplied to capability.client")
          _with_directory { root =>
            val nativeinput = _native
            val selected = _package(root, Vector("first.json" -> _projection(nativeinput.bindings, "first")))
            When("the external client constructs its pure provider and consumes the public Factory catalog")
            val result = _right(_root_.capability.client.PublicCapabilityClient.discover(selected, nativeinput))
            val operations = _root_.capability.client.PublicCapabilityClient.operationDefinitions(result)
            val resources = _root_.capability.client.PublicCapabilityClient.resourcePaths(result)
            Then("the external namespace reads admitted metadata and the original native object without private APIs")
            operations.head should be theSameInstanceAs _snapshots(nativeinput).head.operations.head
            resources shouldBe Vector("meta/component-capability/first.json")
            result.entries.head.admitted.projection.component shouldBe _empty_projection.component
          }
        }
      }
      "CPF06 keep per-call acquisition independent" should _metadata("CPF06") {
        "retain changed package sources and independent native inputs across calls" in {
          Given("Spec: docs/spec/component-capability.md; R14; CPF06; one selected location observed on two calls with independent native revisions")
          _with_directory { root =>
            val firstnative = _native
            val secondnative = _native.copy(bindings = _native.bindings.reverse)
            val firstprojection = _projection(firstnative.bindings, "first")
            val secondprojection = _projection(secondnative.bindings, "second")
            val selected = _package(root, Vector("first.json" -> firstprojection))
            val firstprovider = new CountingProvider(firstnative)
            val secondprovider = new CountingProvider(secondnative)
            When("the first call acquires metadata and a subsequent call acquires explicitly changed package bytes")
            val first = _right(ComponentFactory.discoverCapabilities(selected, firstprovider))
            _write(root, metadataDirectory + "/first.json", _json(secondprojection))
            val second = _right(ComponentFactory.discoverCapabilities(selected, secondprovider))
            Then("each returned catalog owns its original input and source without cache leakage")
            first.entries.head.admitted.projection shouldBe firstprojection
            second.entries.head.admitted.projection shouldBe secondprojection
            first.nativeInput should be theSameInstanceAs firstnative
            second.nativeInput should be theSameInstanceAs secondnative
            first.entries.head.realizations.map(_.binding) shouldBe firstnative.bindings
            second.entries.head.realizations.map(_.binding) shouldBe secondnative.bindings
            firstprovider.reads shouldBe 1
            secondprovider.reads shouldBe 1
          }
        }
      }
    }
  }
}

package capability.client {
  import org.goldenport.cncf.component.ComponentFactory
  import org.goldenport.cncf.component.capability.{ComponentCapabilityDiscovery, ComponentCapabilityMetadataProvider, ComponentUsageCapabilityPackageLoader}
  import org.goldenport.cncf.component.capability.ComponentCapabilityAdmissionScope.Diagnostic
  import org.goldenport.cncf.component.capability.ComponentCapabilityPackageSource.PackageInput
  import org.goldenport.cncf.component.capability.ComponentCapabilityMetadataProvider.{NativeInput, NativeContract}
  import org.goldenport.cncf.operation.CmlOperationDefinition

  object PublicCapabilityClient {
    def acquireUsage(input: PackageInput): Either[Diagnostic, Option[ComponentUsageCapabilityPackageLoader.Acquired]] = {
      ComponentFactory.acquireUsageCapabilities(input)
    }

    def usageContent(acquired: ComponentUsageCapabilityPackageLoader.Acquired): String = acquired.content

    def discover(input: PackageInput, nativeInput: NativeInput): Either[Diagnostic, ComponentCapabilityDiscovery.Discovered] = {
      val provider = new ComponentCapabilityMetadataProvider {
        def componentCapabilityMetadata: NativeInput = nativeInput
      }
      ComponentFactory.discoverCapabilities(input, provider)
    }

    def operationDefinitions(discovered: ComponentCapabilityDiscovery.Discovered): Vector[CmlOperationDefinition] = {
      discovered.entries.flatMap(_.realizations).map(_.contract).collect {
        case NativeContract.Operation(definition) => definition
      }
    }

    def resourcePaths(discovered: ComponentCapabilityDiscovery.Discovered): Vector[String] = {
      discovered.entries.map(_.loaded.resourcePath)
    }
  }
}
