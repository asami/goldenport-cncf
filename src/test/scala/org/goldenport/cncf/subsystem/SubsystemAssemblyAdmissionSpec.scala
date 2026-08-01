package org.goldenport.cncf.subsystem

import java.nio.file.Path

import org.goldenport.cncf.component.{ComponentDescriptor, ComponentStyleCatalog, ComponentStyleId, SubsystemCapabilityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemAssemblyAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "SubsystemAssemblyAdmission" should {
    "admit a component style requirement with exactly one typed provider" in {
      Given("a component style snapshot and one matching dedicated provider declaration")
      val descriptor = _descriptor(Vector(_provider("runtime-facilities")))

      When("assembly admission checks the descriptor before runtime construction")
      val result = SubsystemAssemblyAdmission.verifyC(descriptor)

      Then("the fully declared authority satisfies every subsystem requirement")
      result.toOption shouldBe Some(())
    }

    "publish deterministic forward and reverse capability assignments" in {
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

    "reject ambiguous typed subsystem capability providers" in {
      Given("two providers for the same required capability")
      val descriptor = _descriptor(Vector(_provider("runtime-facilities-a"), _provider("runtime-facilities-b")))

      When("assembly admission checks the descriptor")
      val result = SubsystemAssemblyAdmission.verifyC(descriptor)

      Then("it reports the descriptor-only ambiguity before component materialization")
      result shouldBe a[org.goldenport.Consequence.Failure[_]]
      result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("ambiguous")
      result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("runtime-facilities-a, runtime-facilities-b")
    }

    "reject a provider that names no assembly component" in {
      Given("a provider declaration that targets an absent component")
      val descriptor = _descriptor(Vector(_provider("runtime-facilities", component = "missing-component")))

      When("assembly admission checks provider ownership")
      val result = SubsystemAssemblyAdmission.verifyC(descriptor)

      Then("it rejects before repository lookup or class loading")
      result shouldBe a[org.goldenport.Consequence.Failure[_]]
      result.asInstanceOf[org.goldenport.Consequence.Failure[_]].conclusion.display should include ("unknown component 'missing-component'")
    }

    "diagnose a provider with an incompatible capability major version" in {
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

    "complete an explicit dependency descriptor closure without runtime materialization" in {
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

    "reject a missing dependency descriptor before repository construction" in {
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
}
