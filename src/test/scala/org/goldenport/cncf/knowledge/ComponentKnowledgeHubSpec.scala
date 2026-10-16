package org.goldenport.cncf.knowledge

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentActivation, ComponentActivationContext, ComponentInit, ComponentInstanceId, ComponentKnowledgeProvider, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeHubSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _schema = KnowledgeContributionSchema("fixture.schema", "1")
  private val _provenance = Vector(KnowledgeSourceRef("fixture", "opaque-source", Some("urn:fixture:source")))
  private val _payload = Vector[Byte](-128, 0, 127, -1, 42)

  private val _e1 = afterWord("in spec:component-knowledge-contribution-hub, example:E1, rules:R1,R2,R3,R4, phase:73")
  private val _e2 = afterWord("in spec:component-knowledge-contribution-hub, example:E2, rules:R4,R6, phase:73")
  private val _e3 = afterWord("in spec:component-knowledge-contribution-hub, example:E3, rules:R2,R4, phase:73")
  private val _e4 = afterWord("in spec:component-knowledge-contribution-hub, example:E4, rules:R1,R3, phase:73")

  "Component knowledge contribution discovery and read" should {
    "deliver one attributed immutable contribution" which {
      "E1 preserves complete opaque bytes and supplier metadata through the public hub" must _e1 {
        "deliver the discovered provider's complete opaque snapshot" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R2,R3,R4; Example: E1; an admitted Command Subsystem with one installed nonactivating Ready opaque provider")
          val subsystem = TestComponentFactory.admittedEmptySubsystem("knowledge-hub-e1")
          val declaration = _declaration("g1")
          val provider = new OpaqueProvider(declaration, KnowledgeContributionSnapshot(declaration, _payload))
          _initialize(subsystem, "opaque-provider", provider)
          try {
            subsystem.addC(Vector(provider)).toOption shouldBe Some(subsystem)
            val hub = ComponentKnowledgeHub.createC(subsystem).toOption.get

            When("a consumer discovers the exact domain using the public facade")
            val discovery = hub.discoverC("fixture.domain")

            Then("discovery binds the full declaration to the actual installed provider without reading payload")
            discovery.toOption.flatten shouldBe defined
            val contribution = discovery.toOption.flatten.get
            contribution.descriptor shouldBe ComponentKnowledgeDescriptor(provider.instanceId, declaration)
            contribution.descriptor.knowledgeDomainId shouldBe declaration.knowledgeDomainId
            contribution.descriptor.generation shouldBe "g1"
            contribution.descriptor.schema shouldBe _schema
            contribution.descriptor.provenance shouldBe _provenance
            contribution.descriptor.availability shouldBe KnowledgeContributionAvailability.Ready
            contribution.descriptor.contractVersion shouldBe 1
            provider.readCount shouldBe 0

            When("the consumer reads the discovered contribution with its exact schema")
            val result = hub.readC(contribution, _schema)

            Then("the public result delivers every byte and the trusted descriptor unchanged")
            result.toOption shouldBe Some(ComponentKnowledgeSnapshot(contribution.descriptor, _payload))
            provider.readCount shouldBe 1
            provider.capturedDeclaration shouldBe Some(declaration)
          } finally Subsystem.shutdownOwned(subsystem)
        }
      }

      "E3 preserves arbitrary signed byte vectors and opaque generation labels in 50 generated cases" must _e3 {
        "pass at least 50 generated exact-delivery cases" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R4; Example: E3; nonempty arbitrary byte vectors over the entire signed-byte range and opaque generation labels")
          val bytes = Gen.nonEmptyListOf(Gen.chooseNum(-128, 127).map(_.toByte)).map(_.toVector)
          val generations = Gen.nonEmptyListOf(Gen.chooseNum(0, 65535).map(_.toChar)).map(chars => "opaque:" + chars.mkString)
          val property = Prop.forAll(bytes, generations) { (payload, generation) =>
            Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R4; Example: E3; each generated case owns an admitted Command Subsystem and an installed Ready opaque provider")
            val subsystem = TestComponentFactory.admittedEmptySubsystem("knowledge-hub-e3")
            val declaration = _declaration(generation)
            val provider = new OpaqueProvider(declaration, KnowledgeContributionSnapshot(declaration, payload))
            _initialize(subsystem, "generated-provider", provider)
            try {
              subsystem.addC(Vector(provider)).toOption shouldBe Some(subsystem)
              val hub = ComponentKnowledgeHub.createC(subsystem).toOption.get

              When("the generated contribution is discovered and read through the public hub")
              val result = hub.discoverC("fixture.domain").flatMap {
                case Some(contribution) => hub.readC(contribution, _schema)
                case None => Consequence.serviceUnavailable("fixture contribution absent")
              }

              Then("each successful generated result preserves all opaque bytes, metadata and actual attribution")
              result.toOption shouldBe Some(ComponentKnowledgeSnapshot(ComponentKnowledgeDescriptor(provider.instanceId, declaration), payload))
              provider.capturedDeclaration shouldBe Some(declaration)
              provider.readCount shouldBe 1
              true
            } finally Subsystem.shutdownOwned(subsystem)
          }

          When("ScalaCheck actively evaluates at least 50 successful generated cases")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

          Then("the opaque-delivery property passes with at least 50 successful cases")
          result.passed shouldBe true
          result.succeeded should be >= 50
        }
      }
    }

    "preserve captured generation and absence semantics" which {
      "E2 rejects a supplier snapshot from a different generation without a latest fallback" must _e2 {
        "fail the read when the supplier returns another generation" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R4,R6; Example: E2; one installed provider declares g1 but returns a complete g2 snapshot")
          val subsystem = TestComponentFactory.admittedEmptySubsystem("knowledge-hub-e2")
          val declaration = _declaration("g1")
          val replacement = declaration.copy(generation = "g2")
          val provider = new OpaqueProvider(declaration, KnowledgeContributionSnapshot(replacement, _payload))
          _initialize(subsystem, "generation-provider", provider)
          try {
            subsystem.addC(Vector(provider)).toOption shouldBe Some(subsystem)
            val hub = ComponentKnowledgeHub.createC(subsystem).toOption.get

            When("the consumer captures the admitted g1 contribution")
            val discovery = hub.discoverC("fixture.domain")

            Then("the discovered handle exists and retains exactly the admitted declaration")
            discovery.toOption.flatten shouldBe defined
            val contribution = discovery.toOption.flatten.get
            contribution.descriptor.declaration shouldBe declaration

            When("the consumer reads the captured contribution after the supplier returns g2")
            val result = hub.readC(contribution, _schema)

            Then("the provider receives captured g1 and the incompatible snapshot is a structured binding failure")
            provider.capturedDeclaration shouldBe Some(declaration)
            provider.readCount shouldBe 1
            result shouldBe a[Consequence.Failure[?]]
            result match {
              case Consequence.Failure(conclusion) => conclusion.display should include("knowledge-contribution.snapshot-binding-invalid")
              case _ => fail("a changed supplier generation must fail")
            }
          } finally Subsystem.shutdownOwned(subsystem)
        }
      }

      "E4 reports a missing domain without invoking suppliers or unrelated activation" must _e4 {
        "return successful empty discovery without callbacks" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R3; Example: E4; an admitted Command Subsystem with one unrelated provider and one unrelated activation capability")
          val subsystem = TestComponentFactory.admittedEmptySubsystem("knowledge-hub-e4")
          val declaration = _declaration("g1")
          val provider = new OpaqueProvider(declaration, KnowledgeContributionSnapshot(declaration, _payload))
          val callbackcount = new AtomicInteger(0)
          val ordinary = new Component() with ComponentActivation {
            override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
              callbackcount.incrementAndGet()
              Consequence.unit
            }
          }
          _initialize(subsystem, "unrelated-provider", provider)
          _initialize(subsystem, "unrelated-component", ordinary)
          try {
            subsystem.addC(Vector(provider, ordinary)).toOption shouldBe Some(subsystem)
            val hub = ComponentKnowledgeHub.createC(subsystem).toOption.get

            When("the consumer discovers a domain without any opted-in installed supplier")
            val result = hub.discoverC("absent.domain")

            Then("absence is successful empty discovery and no supplier read or unrelated callback runs")
            result.toOption shouldBe Some(None)
            provider.readCount shouldBe 0
            provider.capturedDeclaration shouldBe None
            callbackcount.get shouldBe 0
          } finally Subsystem.shutdownOwned(subsystem)
        }
      }
    }
  }

  private def _declaration(generation: String): KnowledgeContributionDeclaration =
    KnowledgeContributionDeclaration("fixture.domain", generation, _schema, _provenance, KnowledgeContributionAvailability.Ready)

  private def _initialize(subsystem: Subsystem, name: String, component: Component): Unit = {
    val componentid = TestComponentFactory.componentId(name)
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
    ()
  }

  private final class OpaqueProvider(
    declaration: KnowledgeContributionDeclaration,
    snapshot: KnowledgeContributionSnapshot
  ) extends Component() with ComponentKnowledgeProvider {
    private val _read_count = new AtomicInteger(0)
    private val _captured_declaration = new AtomicReference[Option[KnowledgeContributionDeclaration]](None)

    def readCount: Int = _read_count.get
    def capturedDeclaration: Option[KnowledgeContributionDeclaration] = _captured_declaration.get
    override def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration] = Vector(declaration)
    override def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot] = {
      _read_count.incrementAndGet()
      _captured_declaration.set(Some(declaration))
      Consequence.success(snapshot)
    }
  }
}
