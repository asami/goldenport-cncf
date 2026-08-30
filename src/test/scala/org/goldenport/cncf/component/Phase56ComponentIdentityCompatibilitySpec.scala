package org.goldenport.cncf.component

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, SubsystemAssemblyAdmission}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentIdentityCompatibilitySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E1, rules:CID06-R1,R2, phase:56, slice:CID-06A"
  )
  private val _e2 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E2, rules:CID06-R1,R3, phase:56, slice:CID-06A"
  )
  private val _e3 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E3, rules:CID06-R2,R4, phase:56, slice:CID-06A"
  )
  private val _e4 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E4, rules:CID06-R1,R4, phase:56, slice:CID-06A"
  )
  private val _e5 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E5, rules:CID06-R2,R3, phase:56, slice:CID-06A"
  )
  private val _e6 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E6, rules:CID06-R1,R2, phase:56, slice:CID-06A"
  )
  private val _e7 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E7, rules:CID06-R2,R4, phase:56, slice:CID-06A"
  )
  private val _e8 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E8, rules:CID06-R2,R4, phase:56, slice:CID-06A"
  )
  private val _e9 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E9, rules:CID06-R1,R3, phase:56, slice:CID-06A"
  )
  private val _e10 = afterWord(
    "in spec:phase-56-component-identity-compatibility, example:E10, rules:CID06-R1,R3, phase:56, slice:CID-06A"
  )

  "Phase 56 Component identity compatibility" should {
    "direct adapter behavior (E1-E4)" which {
      "E1 retain an exact qualified candidate without a compatibility notice" must _e1 {
      "when an assembly selector names one admitted canonical ComponentId" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R1,R2; Example: E1 exact qualified selection")
        val alpha = ComponentId("org.alpha.UserAccount")
        val beta = ComponentId("org.beta.UserAccount")
        When("the compatibility adapter resolves the exact qualified selector")
        val result = ComponentIdentityCompatibilityAdapter.resolve(
          alpha.name,
          Vector(beta, alpha),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        )
        Then("the canonical result carries no compatibility notice")
        result.toConsequence match {
          case Consequence.Success(admission) =>
            admission.componentid shouldBe alpha
            admission.notice shouldBe None
          case Consequence.Failure(conclusion) =>
            fail(conclusion.show)
        }
      }
    }

      "E2 adapt one unique bare alias to its canonical candidate" must _e2 {
      "when exactly one candidate has the requested local ID" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R1,R3; Example: E2 unique bare selection")
        val alpha = ComponentId("org.alpha.UserAccount")
        When("the compatibility adapter resolves bare UserAccount")
        val result = ComponentIdentityCompatibilityAdapter.resolve(
          "UserAccount",
          Vector(alpha),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        )
        Then("the unique bare selector resolves canonically with a typed compatibility notice")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].componentid shouldBe alpha
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice.surface shouldBe
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice.aliaskind shouldBe
          ComponentIdentityCompatibilityAdapter.AliasKind.Bare
      }
    }

      "adapt a lowercase local selector to the canonical Sanpomap identity" must _e2 {
      "when the admitted canonical local ID uses UpperCamelCase" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R1,R3; Example: lowercase Sanpomap selector")
        val sanpomap = ComponentId("org.simplemodeling.textus.Sanpomap")
        When("the compatibility adapter resolves lowercase sanpomap")
        val result = ComponentIdentityCompatibilityAdapter.resolve(
          "sanpomap",
          Vector(sanpomap),
          ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector
        )
        Then("the selector adapts uniquely and retains the raw alias in a bare notice")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        val adapted = result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted]
        adapted.componentid shouldBe sanpomap
        adapted.notice.aliaskind shouldBe ComponentIdentityCompatibilityAdapter.AliasKind.Bare
        adapted.notice.alias shouldBe "sanpomap"
      }
    }

      "adapt a lowercase runtime selector to the canonical Sanpomap identity" must _e2 {
      "when runtime aliases contain one admitted UpperCamelCase local ID" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R1,R3; Example: runtime lowercase Sanpomap selector")
        val sanpomap = ComponentId("org.simplemodeling.textus.Sanpomap")
        When("the runtime compatibility adapter resolves lowercase sanpomap")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "sanpomap",
          Vector(ComponentIdentityCompatibilityAdapter.AliasCandidate(sanpomap, Vector.empty)),
          ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector
        )
        Then("the selector adapts uniquely with a bare compatibility notice")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        val adapted = result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted]
        adapted.componentid shouldBe sanpomap
        adapted.notice.aliaskind shouldBe ComponentIdentityCompatibilityAdapter.AliasKind.Bare
        adapted.notice.alias shouldBe "sanpomap"
      }
    }

      "E3 reject a bare alias shared by distinct canonical identities" must _e3 {
      "when two namespaces admit the same local ID" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R2,R4; Example: E3 ambiguous bare selection")
        val alpha = ComponentId("org.alpha.UserAccount")
        val beta = ComponentId("org.beta.UserAccount")
        When("the compatibility adapter resolves bare UserAccount")
        val result = ComponentIdentityCompatibilityAdapter.resolve(
          "UserAccount",
          Vector(beta, alpha),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        )
        Then("the shared bare alias remains rejected as ambiguous")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Rejected].rejection shouldBe
          a[ComponentIdentityCompatibilityAdapter.Ambiguous]
      }
    }

      "reject normalized local-ID collisions as ambiguous" must _e3 {
      "when distinct namespaces admit the same canonical Sanpomap local ID" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R2,R4; Example: normalized local-ID collision")
        val alpha = ComponentId("org.alpha.Sanpomap")
        val beta = ComponentId("org.beta.Sanpomap")
        When("the runtime compatibility adapter resolves lowercase sanpomap")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "sanpomap",
          Vector(
            ComponentIdentityCompatibilityAdapter.AliasCandidate(beta, Vector.empty),
            ComponentIdentityCompatibilityAdapter.AliasCandidate(alpha, Vector.empty)
          ),
          ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector
        )
        Then("the normalized local-ID collision remains rejected with sorted canonical candidates")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        val rejection = result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Rejected].rejection
        rejection shouldBe a[ComponentIdentityCompatibilityAdapter.Ambiguous]
        rejection.asInstanceOf[ComponentIdentityCompatibilityAdapter.Ambiguous].candidates.map(_.name) shouldBe
          Vector(alpha.name, beta.name)
      }
    }

      "E4 reject an unknown qualified identity without alias fallback" must _e4 {
      "when a qualified selector is absent from the admitted candidate set" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R1,R4; Example: E4 unknown qualified selection")
        val alpha = ComponentId("org.alpha.UserAccount")
        When("the compatibility adapter resolves an unknown qualified selector")
        val result = ComponentIdentityCompatibilityAdapter.resolve(
          "org.beta.UserAccount",
          Vector(alpha),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        )
        Then("the qualified selector is rejected without bare fallback")
        result.toConsequence match {
          case Consequence.Failure(conclusion) =>
            conclusion.show should include (
              "component.identity.compatibility.unsupported: surface=assembly-binding; alias-kind=qualified; alias=org.beta.UserAccount"
            )
          case Consequence.Success(_) =>
            fail("unknown qualified ComponentId was admitted")
        }
      }
      }
    }

    "assembly admission behavior (E5-E8)" which {
      "E5 adapt one bare assembly binding through a canonical override" must _e5 {
      "when the actual assembly-admission boundary has one canonical candidate" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R2,R3; Example: E5 unique bare assembly admission")
        val alpha = ComponentId("org.alpha.UserAccount")
        val descriptor = _assembly_descriptor(
          Vector(GenericSubsystemComponentBinding("UserAccount", version = Some("0.1.0"), instance = Some("primary"))),
          Vector(_canonical_descriptor(alpha))
        )
        When("SubsystemAssemblyAdmission resolves the bare binding against canonical override authority")
        val result = SubsystemAssemblyAdmission.resolveWithNoticesC(descriptor, Vector.empty)
        Then("the binding becomes canonical and retains one bare-alias compatibility notice")
        result.toOption.map(_.descriptor.componentBindings.head.componentName) shouldBe Some(alpha.name)
        result.toOption.map(_.descriptor.componentBindings.head.componentId) shouldBe Some(Some(alpha))
        result.toOption.map(_.notices.map(_.aliaskind)) shouldBe
          Some(Vector(ComponentIdentityCompatibilityAdapter.AliasKind.Bare))
      }
    }

      "E6 preserve one exact qualified assembly binding" must _e6 {
      "when the actual assembly-admission boundary receives an already typed binding" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R1,R2; Example: E6 exact qualified assembly admission")
        val alpha = ComponentId("org.alpha.UserAccount")
        val binding = GenericSubsystemComponentBinding(alpha.name, version = Some("0.1.0"), componentId = Some(alpha))
        When("SubsystemAssemblyAdmission resolves the typed binding")
        val result = SubsystemAssemblyAdmission.resolveC(
          _assembly_descriptor(Vector(binding), Vector.empty),
          Vector.empty
        )
        Then("the exact canonical binding is preserved")
        result.toOption.map(_.componentBindings) shouldBe Some(Vector(binding))
      }
    }

      "E7 reject one unsupported legacy assembly binding without downstream fallback" must _e7 {
      "when the actual assembly-admission boundary has no canonical candidate" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R2,R4; Example: E7 unsupported legacy assembly admission")
        val binding = GenericSubsystemComponentBinding("LegacyWidget")
        When("SubsystemAssemblyAdmission resolves the unsupported legacy binding")
        val result = SubsystemAssemblyAdmission.resolveC(
          _assembly_descriptor(Vector(binding), Vector.empty),
          Vector.empty
        )
        Then("the binding is rejected at admission and cannot reach a later surface")
        result shouldBe a[Consequence.Failure[_]]
        result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("component assembly binding requires canonical namespace/id/version")
      }
    }

      "E8 reject one ambiguous bare assembly binding before descriptor discovery" must _e8 {
      "when the actual assembly-admission boundary has two canonical overrides with one local ID" in {
        Given("Spec: docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md; Rules: CID06-R2,R4; Example: E8 ambiguous bare assembly admission")
        val alpha = ComponentId("org.alpha.UserAccount")
        val beta = ComponentId("org.beta.UserAccount")
        val descriptor = _assembly_descriptor(
          Vector(GenericSubsystemComponentBinding("UserAccount")),
          Vector(_canonical_descriptor(beta), _canonical_descriptor(alpha))
        )
        When("SubsystemAssemblyAdmission resolves the ambiguous bare binding")
        val result = SubsystemAssemblyAdmission.resolveC(descriptor, Vector.empty)
        Then("the shared bare binding remains rejected with sorted canonical candidates")
        result shouldBe a[Consequence.Failure[_]]
        result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include (
          "component.identity.compatibility.ambiguous: surface=assembly-binding; alias-kind=bare; alias=UserAccount; candidates=org.alpha.UserAccount,org.beta.UserAccount"
        )
      }
      }
    }

    "runtime componentlet compatibility behavior (E9)" which {
      "E9 adapt one unique owning component presentation alias" must _e9 {
      "when an admitted componentlet reports its owning component presentation name in instance metadata" in {
        Given("Spec: docs/spec/component-identity.md; Rules: 1,8,9; Example: E9 componentlet owning alias")
        val participantid = ComponentId("org.example.TextusScraperAi")
        val metadata = ComponentInstanceMetadata("textus-scraper", "static-default").copy(
          componentId = Some(participantid)
        )
        val component = new Component() {}
        component.initialize(ComponentInit(
          subsystem = org.goldenport.cncf.testutil.TestComponentFactory.emptySubsystem("phase-56-cid06-componentlet"),
          core = Component.Core.create(
            participantid.name,
            participantid,
            metadata.instanceId,
            Protocol.empty
          ),
          origin = ComponentOrigin.Builtin,
          participantRole = Component.ParticipantRole.Componentlet,
          instanceMetadata = Some(metadata)
        ))
        When("the runtime compatibility adapter resolves the unique owning component presentation alias")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "textus-scraper",
          ComponentIdentityCompatibilityAdapter.runtimeAliasCandidates(Vector(component)),
          ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector
        )
        Then("the runtime selector adapts to the canonical participant identity without changing metadata presentation")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].componentid shouldBe participantid
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice.surface shouldBe
          ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice.aliaskind shouldBe
          ComponentIdentityCompatibilityAdapter.AliasKind.Presentation
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice.alias shouldBe "textus-scraper"
        component.componentId shouldBe participantid
        component.instanceMetadata.map(_.componentName) shouldBe Some("textus-scraper")
      }
      }
    }

    "runtime artifact alias compatibility behavior (E10)" which {
      "resolve a schema-v3 artifact alias when no explicit presentation alias matches" must _e10 {
        "when the runtime candidate retains its versioned discovery alias" in {
          Given("a canonical UserNotification candidate with its schema-v3 discovery aliases")
          val componentid = ComponentId("org.simplemodeling.textus.UserNotification")
          val candidates = Vector(
            ComponentIdentityCompatibilityAdapter.AliasCandidate(
              componentid,
              Vector("textus-user-notification-0.6.0", componentid.name)
            )
          )

          When("the WebPath adapter resolves the unversioned artifact alias")
          val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
            "textus-user-notification",
            candidates,
            ComponentIdentityCompatibilityAdapter.Surface.WebPath
          )

          Then("the alias adapts to the canonical identity as an artifact alias")
          result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
          val adapted = result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted]
          adapted.componentid shouldBe componentid
          adapted.notice.aliaskind shouldBe ComponentIdentityCompatibilityAdapter.AliasKind.Artifact
          adapted.notice.surface shouldBe ComponentIdentityCompatibilityAdapter.Surface.WebPath
        }
      }
    }
  }

  private def _assembly_descriptor(
    bindings: Vector[GenericSubsystemComponentBinding],
    overrides: Vector[ComponentDescriptor]
  ): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      path = Path.of("phase-56-cid06a"),
      subsystemName = "component-identity-compatibility",
      componentBindings = bindings,
      componentDescriptorOverrides = overrides
    )

  private def _canonical_descriptor(componentid: ComponentId): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some("0.1.0"),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    )
}
