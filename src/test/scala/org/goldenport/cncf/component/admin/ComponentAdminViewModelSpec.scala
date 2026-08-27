package org.goldenport.cncf.component.admin

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity

/*
 * Executable specification for ADM02-IDENTITY-VIEW-MODEL: an internal,
 * value-only Component Admin identity and strict-codec boundary.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentAdminViewModelSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "ADM02-IDENTITY-VIEW-MODEL Component Admin view model" should {
    "round-trip all six identity axes, field provenance, and available state canonically" in {
      Given("one value-only view with distinct class, release, loaded instance, Subsystem, implicit Subsystem, and canonical Phase 58 logical provenance")
      val view = _view(ComponentAdminResourceState.Available)

      When("the strict v1 codec encodes and decodes the view")
      val encoded = ComponentAdminViewModelCodec.encode(view)
      val decoded = ComponentAdminViewModelCodec.decodeC(encoded).toOption

      Then("every axis, selected/candidate distinction, provenance, and typed state remains exact")
      decoded shouldBe Some(view)
      encoded should startWith("{\"schema\":\"cncf.component-admin-view.v1\",")
      decoded.map(_.selectedLogicalRelease.value.release) shouldBe Some("2.0.0")
      decoded.map(_.logicalReleaseCandidates.map(_.value.release)) shouldBe Some(Vector("1.0.0", "2.0.0"))
      decoded.map(_.selectedLoadedInstance.value.instanceId.instance) shouldBe Some("blue")
      decoded.map(_.loadedInstanceCandidates.map(_.value.instanceId.instance)) shouldBe Some(Vector("green", "blue"))
      decoded.map(_.subsystemInstance.value.subsystemClass) shouldBe decoded.map(_.subsystemClass.value)
      decoded.map(_.implicitComponentSubsystem.value.componentClass) shouldBe decoded.map(_.componentClass.value)
      decoded.map(_.resourceState.value) shouldBe Some(ComponentAdminResourceState.Available)
      decoded.map(_.resourceState.provenance.logicalIdentity) should not be Some(None)
      decoded.map(_.resourceState.provenance.logicalIdentity.map(_.logicalResource)) shouldBe Some(Some("urn:cncf:resource:phase58/documentation-guide"))
    }

    "reject alternate schema, unknown fields, duplicate keys, malformed provenance source kinds, and malformed logical identities" in {
      Given("one canonical v1 encoding and hostile schema, source, bare-resource, invalid-role, and self-parent variants")
      val canonical = ComponentAdminViewModelCodec.encode(_view(ComponentAdminResourceState.Available))
      val hostile = Vector(
        canonical.replace("cncf.component-admin-view.v1", "cncf.component-admin-view.v2"),
        canonical.replace("\"schema\":", "\"unknown\":true,\"schema\":"),
        canonical.replace("\"schema\":", "\"schema\":\"cncf.component-admin-view.v1\",\"schema\":"),
        canonical.replace("runtime-fact", "physical-source"),
        canonical.replace("\"logicalResource\":\"urn:cncf:resource:phase58/documentation-guide\"", "\"logicalResource\":\"admin-view\""),
        canonical.replace("\"childRole\":\"primary\"", "\"childRole\":\"role:bad\""),
        canonical.replace("\"parentComponentId\":null", "\"parentComponentId\":\"org.goldenport.cncf.admin.Sample\"")
      )

      When("the strict duplicate-key-rejecting parser decodes every hostile value")
      val decoded = hostile.map(ComponentAdminViewModelCodec.decodeC(_).toOption)

      Then("each unsupported or malformed source or logical identity representation is a Consequence failure")
      decoded shouldBe Vector.fill(hostile.size)(None)
    }

    "preserve multiple releases and loaded instances only through explicit selections" in {
      Given("two logical releases and two loaded instances with non-first selections")
      val view = _view(ComponentAdminResourceState.Available)

      When("the model is created and canonically round-tripped")
      val created = ComponentAdminViewModel.createC(
        view.componentClass,
        view.selectedLogicalRelease,
        view.logicalReleaseCandidates,
        view.selectedLoadedInstance,
        view.loadedInstanceCandidates,
        view.subsystemClass,
        view.subsystemInstance,
        view.implicitComponentSubsystem,
        view.resourceState
      )
      val decoded = created.toOption.map(ComponentAdminViewModelCodec.encode).flatMap(ComponentAdminViewModelCodec.decodeC(_).toOption)

      Then("the chosen values remain declared values instead of a first, nearest, or default fallback")
      created.isSuccess shouldBe true
      decoded.map(_.selectedLogicalRelease.value.release) shouldBe Some("2.0.0")
      decoded.map(_.selectedLoadedInstance.value.instanceId.instance) shouldBe Some("blue")
      decoded.map(_.logicalReleaseCandidates.head.value.release) shouldBe Some("1.0.0")
      decoded.map(_.loadedInstanceCandidates.head.value.instanceId.instance) shouldBe Some("green")
    }

    "reject absent, duplicate, and mismatched selection identities through Consequence failure" in {
      Given("a valid view and mutations that remove selection membership or violate retained ownership")
      val view = _view(ComponentAdminResourceState.Available)
      val otherclass = ComponentAdminComponentClass(ComponentId("org.goldenport.cncf.admin.Other"))
      val absentrelease = view.copy(logicalReleaseCandidates = Vector(view.logicalReleaseCandidates.head))
      val duplicaterelease = view.copy(logicalReleaseCandidates = Vector(view.selectedLogicalRelease, view.selectedLogicalRelease))
      val absentinstance = view.copy(loadedInstanceCandidates = Vector(view.loadedInstanceCandidates.head))
      val duplicateinstance = view.copy(loadedInstanceCandidates = Vector(view.selectedLoadedInstance, view.selectedLoadedInstance))
      val mismatchedinstance = view.copy(selectedLoadedInstance = _field(ComponentAdminLoadedInstance(otherclass, ComponentInstanceId(otherclass.componentId, "other")), ComponentAdminSourceKind.RuntimeFact))
      val mismatchedsubsystem = view.copy(subsystemInstance = _field(ComponentAdminSubsystemInstance(ComponentAdminSubsystemClass("other-subsystem"), "main"), ComponentAdminSourceKind.RuntimeFact))
      val mismatchedimplicit = view.copy(implicitComponentSubsystem = _field(ComponentAdminImplicitComponentSubsystem(otherclass, "implicit"), ComponentAdminSourceKind.RuntimeFact))

      When("each mutated view is validated through the model constructor convention")
      val results = Vector(absentrelease, duplicaterelease, absentinstance, duplicateinstance, mismatchedinstance, mismatchedsubsystem, mismatchedimplicit).map(ComponentAdminViewModel.validateC)

      Then("no missing, duplicate, or cross-axis value is silently selected or normalized")
      results.map(_.isSuccess) shouldBe Vector.fill(results.size)(false)
    }

    "retain every non-ready resource state as distinct observational data with provenance" in {
      Given("one valid view for each unavailable or failed resource observation")
      val states = Vector(
        ComponentAdminResourceState.Unavailable,
        ComponentAdminResourceState.Forbidden,
        ComponentAdminResourceState.Stale,
        ComponentAdminResourceState.Incompatible,
        ComponentAdminResourceState.Corrupt
      )
      val views = states.map(_view)

      When("each state is encoded and decoded without a readiness substitution")
      val decoded = views.map(view => ComponentAdminViewModelCodec.decodeC(ComponentAdminViewModelCodec.encode(view)).toOption)

      Then("each exact typed state and its safe provenance remain present")
      decoded.map(_.map(_.resourceState.value)) shouldBe states.map(Some(_))
      decoded.map(_.flatMap(_.resourceState.provenance.logicalIdentity)) should not contain None
      decoded.map(_.map(_.resourceState.provenance.sourceKind)) shouldBe Vector.fill(states.size)(Some(ComponentAdminSourceKind.ResolvedResource))
    }

    "deterministically round-trip generated canonical valid values" in {
      Given("arbitrary canonical release and instance labels with a closed typed resource state")
      val labels = Gen.choose(1, 9999)
      val states = Gen.oneOf(
        ComponentAdminResourceState.Available,
        ComponentAdminResourceState.Unavailable,
        ComponentAdminResourceState.Forbidden,
        ComponentAdminResourceState.Stale,
        ComponentAdminResourceState.Incompatible,
        ComponentAdminResourceState.Corrupt
      )
      val property = Prop.forAll(labels, labels, states) { (release, instance, state) =>
        val view = _generated_view(release, instance, state)
        val encoded = ComponentAdminViewModelCodec.encode(view)
        ComponentAdminViewModelCodec.decodeC(encoded).toOption.contains(view) &&
          ComponentAdminViewModelCodec.encode(view) == encoded
      }

      When("the codec evaluates at least fifty independently generated valid views")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("every generated value retains deterministic canonical v1 JSON")
      checked.passed shouldBe true
    }
  }

  private def _view(state: ComponentAdminResourceState): ComponentAdminViewModel = {
    val componentclass = ComponentAdminComponentClass(ComponentId("org.goldenport.cncf.admin.Sample"))
    val provenance = ComponentAdminSafeProvenance(
      ComponentAdminSourceKind.ResolvedResource,
      Some(ComponentResourceLogicalIdentity(componentclass.componentId, "2.0.0", None, "primary", "urn:cncf:resource:phase58/documentation-guide"))
    )
    val componentfield = ComponentAdminViewField(componentclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val firstrelease = ComponentAdminViewField(ComponentAdminLogicalRelease(componentclass, "1.0.0"), provenance)
    val selectedrelease = ComponentAdminViewField(ComponentAdminLogicalRelease(componentclass, "2.0.0"), provenance)
    val firstinstance = ComponentAdminViewField(ComponentAdminLoadedInstance(componentclass, ComponentInstanceId(componentclass.componentId, "green")), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val selectedinstance = ComponentAdminViewField(ComponentAdminLoadedInstance(componentclass, ComponentInstanceId(componentclass.componentId, "blue")), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val subsystemclass = ComponentAdminViewField(ComponentAdminSubsystemClass("component-subsystem"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.Descriptor, None))
    val subsysteminstance = ComponentAdminViewField(ComponentAdminSubsystemInstance(subsystemclass.value, "main"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val implicitsubsystem = ComponentAdminViewField(ComponentAdminImplicitComponentSubsystem(componentclass, "implicit"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.KnowledgeManifest, None))
    val statefield = ComponentAdminViewField(state, provenance)
    ComponentAdminViewModel.createC(
      componentfield,
      selectedrelease,
      Vector(firstrelease, selectedrelease),
      selectedinstance,
      Vector(firstinstance, selectedinstance),
      subsystemclass,
      subsysteminstance,
      implicitsubsystem,
      statefield
    ).toOption.get
  }

  private def _generated_view(release: Int, instance: Int, state: ComponentAdminResourceState): ComponentAdminViewModel = {
    val componentclass = ComponentAdminComponentClass(ComponentId(s"org.goldenport.cncf.admin.Generated$release"))
    val provenance = ComponentAdminSafeProvenance(
      ComponentAdminSourceKind.ResolvedResource,
      Some(ComponentResourceLogicalIdentity(componentclass.componentId, s"1.$release.0", None, "primary", s"urn:cncf:resource:phase58/resource-$instance"))
    )
    val componentfield = ComponentAdminViewField(componentclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val selectedrelease = ComponentAdminViewField(ComponentAdminLogicalRelease(componentclass, s"1.$release.0"), provenance)
    val alternate = ComponentAdminViewField(ComponentAdminLogicalRelease(componentclass, s"0.$release.0"), provenance)
    val selectedinstance = ComponentAdminViewField(ComponentAdminLoadedInstance(componentclass, ComponentInstanceId(componentclass.componentId, s"node$instance")), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val alternateinstance = ComponentAdminViewField(ComponentAdminLoadedInstance(componentclass, ComponentInstanceId(componentclass.componentId, s"other$instance")), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val subsystemclass = ComponentAdminViewField(ComponentAdminSubsystemClass("component-subsystem"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.Descriptor, None))
    val subsysteminstance = ComponentAdminViewField(ComponentAdminSubsystemInstance(subsystemclass.value, "main"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val implicitsubsystem = ComponentAdminViewField(ComponentAdminImplicitComponentSubsystem(componentclass, "implicit"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.KnowledgeManifest, None))
    ComponentAdminViewModel.createC(
      componentfield,
      selectedrelease,
      Vector(alternate, selectedrelease),
      selectedinstance,
      Vector(alternateinstance, selectedinstance),
      subsystemclass,
      subsysteminstance,
      implicitsubsystem,
      ComponentAdminViewField(state, provenance)
    ).toOption.get
  }

  private def _field[A](value: A, sourcekind: ComponentAdminSourceKind): ComponentAdminViewField[A] =
    ComponentAdminViewField(value, ComponentAdminSafeProvenance(sourcekind, None))
}
