package org.goldenport.cncf.knowledge

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentActivation, ComponentActivationCancellation, ComponentActivationTestProbe}
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.{ComponentKnowledgeFixtures, RuntimeOutputCapture, TestComponentFactory}
import org.goldenport.cncf.testutil.ComponentKnowledgeFixtures.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeHubAssemblyBranchSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e12 = afterWord("in spec:component-knowledge-contribution-hub, example:E12, rules:R1,R2,R4,R5,R9, phase:73")
  private val _e13 = afterWord("in spec:component-knowledge-contribution-hub, example:E13, rules:R1,R4,R5,R9, phase:73")
  private val _e14 = afterWord("in spec:component-knowledge-contribution-hub, example:E14, rules:R4,R5,R9, phase:73")
  private val _e15 = afterWord("in spec:component-knowledge-contribution-hub, example:E15, rules:R3,R4,R9, phase:73")

  "Assembled opaque contribution branches" should {
    "support successive configured concrete suppliers through unchanged consumers" which {
      "E12 replaces one closed Server assembly with a fresh selected supplier" must _e12 {
        "deliver complete A then B snapshots with stable domain and changed provider attribution" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R2,R4,R5,R9; Example: E12; one selected concrete supplier A, standard Server configuration and two fresh hub-only consumers")
          val previousflag = sys.props.get("textus.test")
          val previousbackend = LogBackendHolder.backend
          val previoushome = sys.props.get("user.home")
          val first = new FirstProvider()

          When("ordinary Server A is assembled with only the selected supplier")
          val captured = withAssembly(RunMode.Server, "interchange-first", () => first) { assembly =>
            Then("assembly alone performs no activating supplier callback")
            assembly.owner.controlledTestExecutionEnabled shouldBe false
            first.activationCount shouldBe 0
            first.readCount shouldBe 0

            When("the ordinary production Server coordinator succeeds and both unchanged consumers discover/read")
            val activation = ComponentActivation.activateForServerRuntimeC(assembly.owner)
            val handles = assembly.consumers.map(_.discoverC().toOption.flatten.get)
            val results = assembly.consumers.zip(handles).map { (consumer, handle) => consumer.readC(handle) }
            Then("A supplies exact complete attributed snapshots independently to both consumers")
            activation.toOption shouldBe Some(())
            first.activationCount shouldBe 1
            results.map(_.toOption) shouldBe Vector.fill(2)(Some(ComponentKnowledgeSnapshot(
              ComponentKnowledgeDescriptor(first.instanceId, declaration()), firstPayload
            )))
            assembly.consumers.map(_.lastSnapshot) shouldBe results.map(_.toOption)
            first.readCount shouldBe 2
            (handles.head, results.map(_.toOption.get), assembly.consumers)
          }

          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R2,R4,R5,R9; Example: E12; the owned A scope has shut down, and concrete supplier B is selected with the same domain/schema/configuration and consumer implementation")
          val second = new SecondProvider
          When("fresh ordinary Server B is assembled and its production coordinator succeeds")
          withAssembly(RunMode.Server, "interchange-second", () => second) { assembly =>
            val activation = ComponentActivation.activateForServerRuntimeC(assembly.owner)
            Then("B's ordinary activation succeeds with a distinct actual provider identity")
            activation.toOption shouldBe Some(())
            second.activationCount shouldBe 1
            second.instanceId should not be first.instanceId
            assembly.owner.controlledTestExecutionEnabled shouldBe false

            When("both unchanged consumers discover B using only the public hub")
            val discoveries = assembly.consumers.map(_.discoverC())
            Then("the selected B supplementary capability advertises the same stable domain with B attribution")
            discoveries.foreach(_.toOption.flatten shouldBe defined)
            val handles = discoveries.map(_.toOption.flatten.get)
            handles.foreach { handle =>
              handle.descriptor.knowledgeDomainId shouldBe captured._1.descriptor.knowledgeDomainId
              handle.descriptor.providerComponent shouldBe second.instanceId
              handle.descriptor.declaration shouldBe second.preparedSnapshot.declaration
            }

            When("the two hub-only consumers read the newly captured B contribution")
            val results = assembly.consumers.zip(handles).map { (consumer, handle) => consumer.readC(handle) }
            Then("both receive B's complete opaque snapshot using unchanged consumer code")
            results.map(_.toOption) shouldBe Vector.fill(2)(Some(ComponentKnowledgeSnapshot(
              ComponentKnowledgeDescriptor(second.instanceId, second.preparedSnapshot.declaration), secondPayload
            )))
            second.readCount shouldBe 2

            When("the old A handle is passed to B's facade")
            val foreignread = assembly.hub.readC(captured._1, schema)
            Then("owner isolation rejects it before B's callback and stored A snapshots stay unchanged")
            _require_failure(foreignread, "foreign-contribution")
            second.readCount shouldBe 2
            captured._2 shouldBe Vector.fill(2)(ComponentKnowledgeSnapshot(ComponentKnowledgeDescriptor(first.instanceId, declaration()), firstPayload))
            captured._3.map(_.lastSnapshot) shouldBe captured._2.map(Some(_))
          }
          Then("owned successive assemblies restore the original runtime flag and logging without changing user.home")
          sys.props.get("textus.test") shouldBe previousflag
          LogBackendHolder.backend shouldBe previousbackend
          sys.props.get("user.home") shouldBe previoushome
        }
      }
    }

    "preserve inherited ordinary run-mode readiness" which {
      "E13 distinguishes activating and nonactivating Ready suppliers" must _e13 {
        Vector(RunMode.Command, RunMode.Client, RunMode.Script, RunMode.ServerEmulator).foreach { mode =>
          s"reject an activating Ready supplier read after ordinary ${mode.name} assembly" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R4,R5,R9; Example: E13; a standard ${mode.name} assembly with an activating supplier already advertising Ready")
            val provider = new FirstProvider()
            When("ordinary assembly constructs the supplier and two consumers without calling the Server coordinator")
            withAssembly(mode, "mode-activating", () => provider) { assembly =>
              Then("no automatic activation occurs in this ordinary mode")
              assembly.owner.controlledTestExecutionEnabled shouldBe false
              provider.activationCount shouldBe 0
              provider.readCount shouldBe 0
              When("a public consumer discovers Ready metadata and attempts to read")
              val discovery = assembly.consumers.head.discoverC()
              val result = assembly.consumers.head.readC(discovery.toOption.flatten.get)
              Then("discoverability does not grant activation completion or invoke the supplier callback")
              discovery.toOption.flatten.map(_.descriptor.availability) shouldBe Some(KnowledgeContributionAvailability.Ready)
              _require_failure(result, "activation-not-ready")
              provider.activationCount shouldBe 0
              provider.readCount shouldBe 0
              assembly.consumers.head.lastSnapshot shouldBe empty
            }
          }
        }

        RunMode.values.toVector.foreach { mode =>
          s"deliver a nonactivating Ready supplier after ordinary ${mode.name} assembly" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R4,R5,R9; Example: E13; a nonactivating Ready supplier in a standard ${mode.name} assembly")
            val provider = new MutableSupplier()
            When("ordinary assembly completes without any coordinator call")
            withAssembly(mode, "mode-nonactivating", () => provider) { assembly =>
              Then("assembly has not read the supplier and is ordinary")
              assembly.owner.controlledTestExecutionEnabled shouldBe false
              provider.readCount shouldBe 0
              When("both public consumers discover and read post-assembly")
              val results = assembly.consumers.map { consumer =>
                consumer.readC(consumer.discoverC().toOption.flatten.get)
              }
              Then("nonactivating Ready metadata delivers full bytes without activation in every ordinary mode")
              results.map(_.toOption) shouldBe Vector.fill(2)(Some(ComponentKnowledgeSnapshot(
                ComponentKnowledgeDescriptor(provider.instanceId, declaration()), firstPayload
              )))
              provider.readCount shouldBe 2
            }
          }
        }
      }
    }

    "consume explicit controlled success and isolate ordinary failed terminals" which {
      "E14 preserves existing activation admission and once-only terminality" must _e14 {
        "read only after an explicitly supplied controlled coordinator succeeds once" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R4,R5,R9; Example: E14; an admitted controlled owner with activating Ready supplier and explicit mode/deadline/cancellation/probe")
          RuntimeOutputCapture.capture {
            val owner = TestComponentFactory.admittedEmptySubsystem("knowledge-controlled-branch")
            try {
              val provider = new FirstProvider()
              initialize(owner, "controlled-provider", provider)
              val hub = ComponentKnowledgeHub.createC(owner).toOption.get
              val consumer = new IndependentConsumer(domainId, schema, hub)
              initialize(owner, "controlled-consumer", consumer)
              owner.add(Vector(provider, consumer))
              val mode = RunMode.Server
              val deadline = Instant.now.plusSeconds(10L)
              val cancellation = new ComponentActivationCancellation
              val probe = new ComponentActivationTestProbe
              val handle = consumer.discoverC().toOption.flatten.get

              When("the public consumer attempts a read before explicit controlled activation")
              val early = consumer.readC(handle)
              Then("the controlled owner gains no automatic route from Ready metadata")
              owner.controlledTestExecutionEnabled shouldBe true
              _require_failure(early, "activation-not-ready")
              provider.readCount shouldBe 0
              provider.activationCount shouldBe 0

              When("the existing explicit test seam receives exactly the supplied parameters twice")
              val activation = ComponentActivation.activateForControlledTestC(owner, mode, deadline, cancellation, probe)
              val repeated = ComponentActivation.activateForControlledTestC(owner, mode, deadline, cancellation, probe)
              Then("successful terminality completes once and preserves the supplied callback context and probe")
              activation.toOption shouldBe Some(())
              repeated.toOption shouldBe Some(())
              provider.activationCount shouldBe 1
              provider.lastActivationContext.map(_.runMode) shouldBe Some(mode)
              provider.lastActivationContext.map(_.deadline) shouldBe Some(deadline)
              provider.lastActivationContext.exists(_.cancellation eq cancellation) shouldBe true
              provider.lastActivationContext.exists(_.subsystem eq owner) shouldBe true
              cancellation.isCancelled shouldBe false
              probe.coordinatorCleanupCount shouldBe 0
              probe.diagnostics shouldBe empty

              When("the public consumer reads its captured handle after explicit success")
              val result = consumer.readC(handle)
              Then("the same exact full snapshot is delivered through the public facade")
              result.toOption shouldBe Some(ComponentKnowledgeSnapshot(ComponentKnowledgeDescriptor(provider.instanceId, declaration()), firstPayload))
              consumer.lastSnapshot shouldBe result.toOption
              provider.readCount shouldBe 1
            } finally Subsystem.shutdownOwned(owner)
          }.value
        }

        "reject failed Server captured and rediscovered handles while an equal-identity healthy owner succeeds" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R4,R5,R9; Example: E14; two distinct standard Server owners with equal logical supplier/domain/generation ids, one activation failure and one healthy")
          val failing = new FirstProvider(failActivation = true)
          val healthy = new FirstProvider()
          withAssembly(RunMode.Server, "terminal-equal-provider", () => failing) { failedassembly =>
            val captured = failedassembly.consumers.head.discoverC().toOption.flatten.get
            When("the ordinary failing owner's Server activation reaches its terminal failure")
            val activation = ComponentActivation.activateForServerRuntimeC(failedassembly.owner)
            val repeated = ComponentActivation.activateForServerRuntimeC(failedassembly.owner)
            Then("the failure is retained without retrying the supplier")
            activation shouldBe a[Consequence.Failure[?]]
            repeated shouldBe a[Consequence.Failure[?]]
            failing.activationCount shouldBe 1
            failing.readCount shouldBe 0

            When("the failed owner's captured and rediscovered Ready handles are read")
            val rediscovered = failedassembly.consumers.head.discoverC().toOption.flatten.get
            val capturedread = failedassembly.consumers.head.readC(captured)
            val currentread = failedassembly.consumers.last.readC(rediscovered)
            Then("both handles remain activation-not-ready without any supplier read")
            captured.descriptor.availability shouldBe KnowledgeContributionAvailability.Ready
            rediscovered.descriptor shouldBe captured.descriptor
            _require_failure(capturedread, "activation-not-ready")
            _require_failure(currentread, "activation-not-ready")
            failing.readCount shouldBe 0

            When("an independent healthy ordinary Server assembly activates and reads")
            withAssembly(RunMode.Server, "terminal-equal-provider", () => healthy) { healthyassembly =>
              val healthyactivation = ComponentActivation.activateForServerRuntimeC(healthyassembly.owner)
              val handle = healthyassembly.consumers.head.discoverC().toOption.flatten.get
              val result = healthyassembly.consumers.head.readC(handle)
              Then("equal descriptor values do not share failed terminal state")
              handle.descriptor shouldBe captured.descriptor
              (healthyassembly.owner eq failedassembly.owner) shouldBe false
              healthyactivation.toOption shouldBe Some(())
              healthy.activationCount shouldBe 1
              result.toOption shouldBe Some(ComponentKnowledgeSnapshot(handle.descriptor, firstPayload))
              healthy.readCount shouldBe 1
              failing.readCount shouldBe 0
            }
          }
        }
      }
    }

    "bind handles to exact owner objects and installed supplier scope" which {
      "E15 isolates assemblies even when all descriptor values are equal" must _e15 {
        "reject foreign handles before callback while same-owner facades and consumers remain independent" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R3,R4,R9; Example: E15; two ordinary healthy Server owners with separate supplier objects, equal descriptor values and distinct full opaque bytes")
          val first = new FirstProvider()
          val second = new FirstProvider(snapshot(payload = secondPayload))
          withAssembly(RunMode.Server, "isolation-equal-provider", () => first) { firstassembly =>
            withAssembly(RunMode.Server, "isolation-equal-provider", () => second) { secondassembly =>
              When("both ordinary Server coordinators succeed and each pair of consumers reads its own supplier")
              val firstactivation = ComponentActivation.activateForServerRuntimeC(firstassembly.owner)
              val secondactivation = ComponentActivation.activateForServerRuntimeC(secondassembly.owner)
              val firsthandle = firstassembly.consumers.head.discoverC().toOption.flatten.get
              val secondhandle = secondassembly.consumers.head.discoverC().toOption.flatten.get
              val firstresults = firstassembly.consumers.map(_.readC(firsthandle))
              val secondresults = secondassembly.consumers.map(_.readC(secondhandle))
              Then("owner objects differ even though descriptors match, and both consumers receive only their own complete bytes")
              firstactivation.toOption shouldBe Some(())
              secondactivation.toOption shouldBe Some(())
              (firstassembly.owner eq secondassembly.owner) shouldBe false
              firsthandle.descriptor shouldBe secondhandle.descriptor
              firstresults.map(_.toOption) shouldBe Vector.fill(2)(Some(ComponentKnowledgeSnapshot(firsthandle.descriptor, firstPayload)))
              secondresults.map(_.toOption) shouldBe Vector.fill(2)(Some(ComponentKnowledgeSnapshot(secondhandle.descriptor, secondPayload)))
              first.readCount shouldBe 2
              second.readCount shouldBe 2

              When("A's captured handle is passed to B's public consumer")
              val foreign = secondassembly.consumers.head.readC(firsthandle)
              Then("foreign-contribution prevents a target callback and does not overwrite either consumer's immutable result")
              _require_failure(foreign, "foreign-contribution")
              second.readCount shouldBe 2
              firstassembly.consumers.map(_.lastSnapshot) shouldBe firstresults.map(_.toOption)
              secondassembly.consumers.map(_.lastSnapshot) shouldBe secondresults.map(_.toOption)

              When("a separate B facade reads B's handle and B's consumer reads again after rejection")
              val otherhub = ComponentKnowledgeHub.createC(secondassembly.owner).toOption.get
              val sameread = otherhub.readC(secondhandle, schema)
              val recoveredread = secondassembly.consumers.head.readC(secondhandle)
              Then("same-owner reuse succeeds and foreign rejection contaminates neither supplier nor consumer")
              sameread.toOption shouldBe secondresults.head.toOption
              recoveredread.toOption shouldBe secondresults.head.toOption
              first.readCount shouldBe 2
              second.readCount shouldBe 4
            }
          }
        }

        Vector("null", "unknown").foreach { kind =>
          s"reject a $kind contribution before invoking the supplier" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R3,R4,R9; Example: E15; an ordinary owner and a $kind package-local contribution")
            val provider = new MutableSupplier()
            withAssembly(RunMode.Command, "foreign-input-provider", () => provider) { assembly =>
              val contribution = if (kind == "null") null else new ComponentKnowledgeContribution {
                override val descriptor = ComponentKnowledgeDescriptor(provider.instanceId, declaration())
              }
              When("the public facade attempts to read the unrecognized handle")
              val result = assembly.hub.readC(contribution, schema)
              Then("foreign-contribution precedes every callback")
              _require_failure(result, "foreign-contribution")
              provider.readCount shouldBe 0
            }
          }
        }

        "diagnose adversarial provider scope rebinding without live membership replacement" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R3,R4,R9; Example: E15; a captured installed provider and a separate test owner used only for direct adversarial reinitialization")
          val provider = new MutableSupplier()
          withInstalled(Vector(provider)) { (_, hub) =>
            val handle = hub.discoverC(domainId).toOption.flatten.get
            withInstalled(Vector.empty) { (otherowner, _) =>
              When("the same provider object is directly initialized into another owner scope and its old handle is read")
              initialize(otherowner, "pure-provider-0", provider)
              val result = hub.readC(handle, schema)
              Then("captured supplier scope mismatch fails provider-not-installed before a read callback")
              _require_failure(result, "provider-not-installed")
              provider.readCount shouldBe 0
            }
          }
        }
      }
    }
  }

  private def _require_failure(result: Consequence[?], code: String): Unit = {
    result shouldBe a[Consequence.Failure[?]]
    result.toOption shouldBe empty
    result.asInstanceOf[Consequence.Failure[?]].conclusion.show should include("knowledge-contribution." + code)
  }
}
