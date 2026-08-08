package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters._

import org.goldenport.cncf.component.{ComponentDescriptor, ComponentStyleCatalog, ComponentStyleId, SubsystemCapabilityId}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.component.repository.ComponentRepositoryStaticIdentityCandidates
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemAssemblyAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _cid06c_metadata(exampleid: String) =
    afterWord(s"in spec:subsystem-assembly-admission, example:$exampleid, rules:CID06C-R2,R3, phase:56, slice:CID-06C")

  "SubsystemAssemblyAdmission" should {
    "canonical static repository candidates" which {
      "E56-CID06C resolve a bare binding from one packed CAR file" must _cid06c_metadata("E56-CID06C-file-bare") {
        "when the repository specification points at a canonical schema-3 CAR" in {
          Given("one packed CAR whose descriptor is available without component activation")
          _with_work_dir { root =>
            val componentid = "org.simplemodeling.textus.Catalog"
            val descriptorpath = root.resolve("component-descriptor.json")
            Files.writeString(descriptorpath, _canonical_descriptor_json(componentid, "0.1.0"), StandardCharsets.UTF_8)
            val carpath = root.resolve("catalog.car")
            CarArchiveFixture.write(carpath, Seq("component-descriptor.json" -> descriptorpath))
            val descriptor = _assembly_descriptor("Catalog")

            When("assembly admission resolves the bare binding through static CAR metadata")
            val result = SubsystemAssemblyAdmission.resolveC(
              descriptor,
              Vector(ComponentRepository.ComponentFileRepository.Specification(carpath))
            )

            Then("the binding and descriptor closure carry the exact canonical identity")
            val admitted = result.toOption.get
            admitted.componentBindings.head.componentId.map(_.name) shouldBe Some(componentid)
            admitted.componentDescriptorOverrides.head.componentId.map(_.name) shouldBe Some(componentid)
          }
        }
      }

      "E56-CID06C resolve an artifact binding from one expanded CAR directory" must _cid06c_metadata("E56-CID06C-directory-artifact") {
        "when the repository specification enumerates packed and expanded CAR descriptors" in {
          Given("one expanded CAR directory with a canonical descriptor and component archive marker")
          _with_work_dir { root =>
            val componentid = "org.simplemodeling.textus.Catalog"
            val cardir = root.resolve("catalog.car")
            Files.createDirectories(cardir.resolve("component"))
            Files.writeString(
              cardir.resolve("component-descriptor.json"),
              _canonical_descriptor_json(componentid, "0.1.0"),
              StandardCharsets.UTF_8
            )
            Files.write(cardir.resolve("component").resolve("main.jar"), Array.emptyByteArray)
            val descriptor = _assembly_descriptor("textus-catalog")
            val repository = ComponentRepository.ComponentDirRepository.Specification(root)

            ComponentRepositoryStaticIdentityCandidates
              .resolve(repository, "textus-catalog")
              .map(_.name) shouldBe Vector(componentid)

            When("assembly admission resolves the artifact binding through static CAR-directory metadata")
            val result = SubsystemAssemblyAdmission.resolveC(
              descriptor,
              Vector(repository)
            )

            Then("the artifact alias is adapted to the canonical identity before closure")
            val admitted = result.toOption.get
            admitted.componentBindings.head.componentName shouldBe componentid
            admitted.componentBindings.head.componentId.map(_.name) shouldBe Some(componentid)
            admitted.componentDescriptorOverrides.head.componentId.map(_.name) shouldBe Some(componentid)
          }
        }
      }
    }

    "typed subsystem capability admission" which {
      "E56-CID06C admit a component style requirement with exactly one typed provider" must _cid06c_metadata("E56-CID06C-typed-provider") {
        "when assembly admission checks the descriptor before runtime construction" in {
          Given("a component style snapshot and one matching dedicated provider declaration")
          val descriptor = _descriptor(Vector(_provider("runtime-facilities")))

          When("assembly admission checks the descriptor before runtime construction")
          val result = SubsystemAssemblyAdmission.verifyC(descriptor)

          Then("the fully declared authority satisfies every subsystem requirement")
          result.toOption shouldBe Some(())
        }
      }

      "E56-CID06C publish deterministic forward and reverse capability assignments" must _cid06c_metadata("E56-CID06C-deterministic-assignments") {
        "when descriptor admission is evaluated" in {
          Given("one provider that satisfies the complete style requirement set")
          val descriptor = _descriptor(Vector(_provider("runtime-facilities")))

          When("descriptor admission is evaluated")
          val report = SubsystemAssemblyAdmission.evaluateC(descriptor).toOption.get

          Then("the report exposes every requirement and its sole provider")
          report.assignments.map(_.provider).distinct shouldBe Vector("runtime-facilities")
          report.assignments.map(_.requirement.canonical) shouldBe Vector(
            "datastore.optimistic-concurrency@1",
            "datastore.persistent@1",
            "datastore.transactional@1",
            "user-context.current@1"
          )
          report.providerRequirements("runtime-facilities").map(_.canonical) shouldBe report.assignments.map(_.requirement.canonical)
        }
      }

      "E56-CID06C reject ambiguous typed subsystem capability providers" must _cid06c_metadata("E56-CID06C-ambiguous-provider") {
        "when two providers satisfy the same required capability" in {
          Given("two providers for the same required capability")
          val descriptor = _descriptor(Vector(_provider("runtime-facilities-a"), _provider("runtime-facilities-b")))

          When("assembly admission checks the descriptor")
          val result = SubsystemAssemblyAdmission.verifyC(descriptor)

          Then("it reports the descriptor-only ambiguity before component materialization")
          result shouldBe a[org.goldenport.Consequence.Failure[_]]
          result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("ambiguous")
          result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("runtime-facilities-a, runtime-facilities-b")
        }
      }

      "E56-CID06C reject a provider that names no assembly component" must _cid06c_metadata("E56-CID06C-unknown-owner") {
        "when provider ownership names an absent component" in {
          Given("a provider declaration that targets an absent component")
          val descriptor = _descriptor(Vector(_provider("runtime-facilities", component = "missing-component")))

          When("assembly admission checks provider ownership")
          val result = SubsystemAssemblyAdmission.verifyC(descriptor)

          Then("it rejects before repository lookup or class loading")
          result shouldBe a[org.goldenport.Consequence.Failure[_]]
          result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("unknown component 'missing-component'")
        }
      }

      "E56-CID06C diagnose a provider with an incompatible capability major version" must _cid06c_metadata("E56-CID06C-incompatible-major") {
        "when a provider supplies the required capability family at another major" in {
          Given("a provider that supplies the required capability family at another major")
          val incompatible = GenericSubsystemCapabilityProviderBinding(
            "runtime-facilities",
            "application",
            Vector(
              SubsystemCapabilityId.parseC("datastore.optimistic-concurrency@2").toOption.get,
              SubsystemCapabilityId.parseC("datastore.persistent@1").toOption.get,
              SubsystemCapabilityId.parseC("datastore.transactional@1").toOption.get,
              SubsystemCapabilityId.parseC("user-context.current@1").toOption.get
            )
          )
          val descriptor = _descriptor(Vector(incompatible))

          When("assembly admission compares the typed requirements")
          val result = SubsystemAssemblyAdmission.verifyC(descriptor)

          Then("the diagnostic distinguishes an incompatible major from a missing provider")
          result shouldBe a[org.goldenport.Consequence.Failure[_]]
          result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("incompatible major version")
          result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("datastore.optimistic-concurrency@2")
        }
      }
    }

    "descriptor closure admission" which {
      "E56-CID06C complete an explicit dependency descriptor closure without runtime materialization" must _cid06c_metadata("E56-CID06C-descriptor-closure") {
        "when a root and dependency descriptor are supplied as static metadata" in {
          Given("a root descriptor and a dependency descriptor already supplied as static metadata")
          val dependency = ComponentDescriptor(name = Some("dependency"), componentName = Some("dependency"))
          val root = _descriptor(Vector(_provider("runtime-facilities")))
          val descriptor = root.copy(
            componentBindings = Vector(
              GenericSubsystemComponentBinding("application"),
              GenericSubsystemComponentBinding("dependency")
            ),
            componentDescriptorOverrides = root.componentDescriptorOverrides :+ dependency
          )

          When("the complete descriptor closure is resolved")
          val result = SubsystemAssemblyAdmission.resolveC(descriptor, Vector.empty)

          Then("the root snapshot and dependency metadata are both available before materialization")
          result.toOption.map(_.toComponentDescriptors.map(_.componentName)) shouldBe Some(Vector(Some("application"), Some("dependency")))
        }
      }

      "E56-CID06C reject a missing dependency descriptor before repository construction" must _cid06c_metadata("E56-CID06C-missing-descriptor") {
        "when descriptor closure is resolved without a repository candidate" in {
          Given("a style-bearing root descriptor with an unresolved dependency")
          val descriptor = _descriptor(Vector(_provider("runtime-facilities"))).copy(
            componentBindings = Vector(
              GenericSubsystemComponentBinding("application"),
              GenericSubsystemComponentBinding("missing-dependency")
            )
          )

          When("descriptor closure is resolved without a repository candidate")
          val result = SubsystemAssemblyAdmission.resolveC(descriptor, Vector.empty)

          Then("admission reports the missing static binding")
          result shouldBe a[org.goldenport.Consequence.Failure[_]]
          result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("missing binding 'missing-dependency'")
        }
      }
    }
  }

  private def _descriptor(
    providers: Vector[GenericSubsystemCapabilityProviderBinding]
  ): GenericSubsystemDescriptor = {
    val snapshot = ComponentStyleCatalog.default
      .resolveC(ComponentStyleId.parseC("full-fledged-with-standalone@1").toOption.get)
      .flatMap(ComponentStyleCatalog.default.expandC)
      .toOption
      .get
    val component = ComponentDescriptor(
      name = Some("application"),
      componentName = Some("application"),
      schemaVersion = Some(2),
      componentStyleSnapshot = Some(snapshot)
    )
    GenericSubsystemDescriptor(
      path = Path.of("assembly-admission"),
      subsystemName = "application",
      componentBindings = Vector(GenericSubsystemComponentBinding("application")),
      subsystemCapabilityProviders = providers,
      componentDescriptorOverrides = Vector(component)
    )
  }

  private def _provider(
    name: String,
    component: String = "application"
  ): GenericSubsystemCapabilityProviderBinding =
    GenericSubsystemCapabilityProviderBinding(
      name,
      component,
      Vector(
        SubsystemCapabilityId.parseC("datastore.optimistic-concurrency@1").toOption.get,
        SubsystemCapabilityId.parseC("datastore.persistent@1").toOption.get,
        SubsystemCapabilityId.parseC("datastore.transactional@1").toOption.get,
        SubsystemCapabilityId.parseC("user-context.current@1").toOption.get
      )
    )

  private def _assembly_descriptor(componentname: String): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      path = Path.of("cid06c-static-candidate-assembly.yaml"),
      subsystemName = "cid06c-static-candidate-assembly",
      componentBindings = Vector(GenericSubsystemComponentBinding(componentname))
    )

  private def _canonical_descriptor_json(
    componentid: String,
    release: String
  ): String = {
    val segments = componentid.split("\\.").toVector
    val namespace = segments.dropRight(1).mkString(".")
    val localid = segments.last
    s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$localid","version":"$release"}}"""
  }

  private def _with_work_dir(body: Path => Unit): Unit = {
    val root = Path.of("target/cncf-test/work/subsystem-assembly-admission").toAbsolutePath.normalize
    _delete_tree(root)
    Files.createDirectories(root)
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(root: Path): Unit = {
    if (Files.exists(root)) {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala.toVector
          .sortWith((left, right) => left.getNameCount > right.getNameCount)
          .foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
  }
}
