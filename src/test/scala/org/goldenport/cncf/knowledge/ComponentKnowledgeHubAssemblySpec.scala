package org.goldenport.cncf.knowledge

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentActivation, ComponentActivationContext, ComponentInit, ComponentInstanceId, ComponentKnowledgeProvider, ComponentOrigin}
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.{RuntimeOutputCapture, TestComponentFactory}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeHubAssemblySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _domain = "fixture.domain"
  private val _schema = KnowledgeContributionSchema("fixture.schema", "1")
  private val _provenance = Vector(KnowledgeSourceRef("fixture", "assembled-source", Some("urn:fixture:assembled")))
  private val _payload = Vector[Byte](-128, 0, -1, 127, 53)
  private val _declaration = KnowledgeContributionDeclaration(
    _domain, "g1", _schema, _provenance, KnowledgeContributionAvailability.Ready
  )

  private val _e5 = afterWord("in spec:component-knowledge-contribution-hub, example:E5, rules:R1,R2,R4,R5, phase:73")
  private val _e6 = afterWord("in spec:component-knowledge-contribution-hub, example:E6, rules:R1,R4,R5, phase:73")

  "Ordinary assembled component knowledge exchange" should {
    "place successful consumer reads after Server activation completion" which {
      "E5 delivers one complete attributed snapshot to an independent consumer after ordinary assembly" must _e5 {
        "deliver the full snapshot after ordinary activation completion" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R2,R4,R5; Example: E5; a standard Server descriptor and one opted-in activation provider plus one independent hub-only consumer")
          val previousflag = sys.props.get("textus.test")
          val previousbackend = LogBackendHolder.backend
          _with_assembly(Vector("provider", "first")) { (subsystem, provider, first, _) =>
            subsystem.controlledTestExecutionEnabled shouldBe false
            subsystem.components should contain(provider)
            subsystem.components should contain(first)

            When("the assembled consumer discovers metadata before the production activation coordinator runs")
            val discovery = first.discoverC()

            Then("the unavailable descriptor is discoverable and discovery invokes no provider read")
            discovery.toOption.flatten shouldBe defined
            val before = discovery.toOption.flatten.get
            before.descriptor.availability shouldBe KnowledgeContributionAvailability.Unavailable
            provider.readCount shouldBe 0
            provider.activationCount shouldBe 0

            When("the consumer attempts to read that pre-activation contribution")
            val preread = first.readC(before)

            Then("read fails before readiness without invoking the supplier callback")
            preread shouldBe a[Consequence.Failure[?]]
            provider.readCount shouldBe 0

            When("the public production coordinator completes after ordinary Server assembly")
            val activation = ComponentActivation.activateForServerRuntimeC(subsystem)

            Then("activation succeeds once while the consumer's during-coordinator probe remains unsuccessful")
            activation.toOption shouldBe Some(())
            provider.activationCount shouldBe 1
            first.activationCount shouldBe 1
            first.activationProbe shouldBe defined
            first.activationProbe.get shouldBe a[Consequence.Failure[?]]
            provider.readCount shouldBe 0

            When("the consumer rediscovers the Ready contribution after coordinator success")
            val rediscovery = first.discoverC()

            Then("the descriptor exposes the prepared immutable generation with actual supplier attribution")
            rediscovery.toOption.flatten shouldBe defined
            val contribution = rediscovery.toOption.flatten.get
            contribution.descriptor shouldBe ComponentKnowledgeDescriptor(provider.instanceId, _declaration)

            When("the independent consumer reads through its public hub facade")
            val result = first.readC(contribution)

            Then("the consumer records the complete opaque attributed snapshot")
            result.toOption shouldBe Some(ComponentKnowledgeSnapshot(contribution.descriptor, _payload))
            first.lastSnapshot shouldBe result.toOption
            provider.readCount shouldBe 1
          }

          Then("the ordinary assembly fixture restores the runtime flag and logging backend")
          sys.props.get("textus.test") shouldBe previousflag
          LogBackendHolder.backend shouldBe previousbackend
        }
      }
    }

    "support two independent consumers regardless of callback order" which {
      Vector("provider", "first", "second").permutations.foreach { order =>
        s"E6 preserves the same complete snapshot for admission order ${order.mkString(" -> ")}" must _e6 {
          "deliver equal snapshots to both consumers after completion" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R4,R5; Example: E6; a standard Server assembly admitting the provider and two independent consumers in order ${order.mkString(", ")}")
            val previousflag = sys.props.get("textus.test")
            val previousbackend = LogBackendHolder.backend
            _with_assembly(order) { (subsystem, provider, first, secondoption) =>
              val second = secondoption.get
              subsystem.controlledTestExecutionEnabled shouldBe false
              val participants = Map[String, Component]("provider" -> provider, "first" -> first, "second" -> second)
              subsystem.components.filter(participants.values.toSet.contains) shouldBe order.map(participants.apply)

              When("the ordinary production coordinator runs every admitted activation callback")
              val activation = ComponentActivation.activateForServerRuntimeC(subsystem)

              Then("every during-coordinator consumer probe fails and no supplier read callback runs")
              activation.toOption shouldBe Some(())
              provider.activationCount shouldBe 1
              Vector(first, second).foreach { consumer =>
                consumer.activationCount shouldBe 1
                consumer.activationProbe shouldBe defined
                consumer.activationProbe.get shouldBe a[Consequence.Failure[?]]
              }
              provider.readCount shouldBe 0

              When("the first independent consumer discovers the Ready contribution after coordinator success")
              val discovery = first.discoverC()

              Then("discovery captures the immutable generation and trusted provider descriptor")
              discovery.toOption.flatten shouldBe defined
              val contribution = discovery.toOption.flatten.get
              contribution.descriptor shouldBe ComponentKnowledgeDescriptor(provider.instanceId, _declaration)

              When("the first consumer reads the complete snapshot through its facade")
              val firstresult = first.readC(contribution)

              Then("the first consumer records the exact attributed opaque snapshot")
              firstresult.toOption shouldBe Some(ComponentKnowledgeSnapshot(contribution.descriptor, _payload))
              first.lastSnapshot shouldBe firstresult.toOption
              provider.readCount shouldBe 1

              When("the second independent consumer reads the same handle through its separate facade for the same Subsystem")
              val secondresult = second.readC(contribution)

              Then("both consumers record equal complete immutable snapshots without supplier implementation lookup")
              secondresult.toOption shouldBe firstresult.toOption
              second.lastSnapshot shouldBe first.lastSnapshot
              provider.readCount shouldBe 2
            }

            Then("each permutation restores the runtime flag and logging backend")
            sys.props.get("textus.test") shouldBe previousflag
            LogBackendHolder.backend shouldBe previousbackend
          }
        }
      }
    }
  }

  private def _with_assembly(order: Vector[String])(
    body: (Subsystem, ActivationProvider, IndependentConsumer, Option[IndependentConsumer]) => Unit
  ): Unit = {
    val previous = sys.props.get("textus.test")
    val descriptor = _descriptor_path()
    var assembled = Option.empty[Subsystem]
    var provideroption = Option.empty[ActivationProvider]
    var firstoption = Option.empty[IndependentConsumer]
    var secondoption = Option.empty[IndependentConsumer]
    System.clearProperty("textus.test")
    try {
      RuntimeOutputCapture.capture {
        val subsystem = CncfRuntime.buildSubsystem(
          extraComponents = current => {
            val provider = new ActivationProvider
            val first = new IndependentConsumer(_domain, _schema, ComponentKnowledgeHub.createC(current).toOption.get)
            val second = if (order.contains("second"))
              Some(new IndependentConsumer(_domain, _schema, ComponentKnowledgeHub.createC(current).toOption.get))
            else None
            _initialize(current, "assembled-provider", provider)
            _initialize(current, "assembled-first-consumer", first)
            second.foreach(value => _initialize(current, "assembled-second-consumer", value))
            provideroption = Some(provider)
            firstoption = Some(first)
            secondoption = second
            val participants = Map[String, Component]("provider" -> provider, "first" -> first) ++
              second.map(value => "second" -> (value: Component))
            order.map(participants.apply)
          },
          mode = Some(RunMode.Server),
          args = Array(s"--textus.test.descriptor=$descriptor", "--no-default-components")
        )
        assembled = Some(subsystem)
        body(subsystem, provideroption.get, firstoption.get, secondoption)
      }.value
    } finally {
      try assembled.foreach(Subsystem.shutdownOwned)
      finally {
        try Files.deleteIfExists(descriptor)
        finally {
          previous match {
            case Some(value) => System.setProperty("textus.test", value)
            case None => System.clearProperty("textus.test")
          }
        }
      }
    }
  }

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

  private def _descriptor_path(): Path = {
    val path = Files.createTempFile("component-knowledge-hub-spec-", ".yaml")
    Files.writeString(path,
      """kind: test-descriptor
        |execution:
        |  profile: standard
        |  key: component-knowledge-hub-spec
        |  time:
        |    mode: system
        |  random:
        |    mode: system
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    path
  }

  private final class ActivationProvider extends Component() with ComponentKnowledgeProvider with ComponentActivation {
    private val _snapshot = new AtomicReference[Option[KnowledgeContributionSnapshot]](None)
    private val _read_count = new AtomicInteger(0)
    private val _activation_count = new AtomicInteger(0)

    def readCount: Int = _read_count.get
    def activationCount: Int = _activation_count.get
    override def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration] =
      Vector(_snapshot.get.map(_.declaration).getOrElse(_declaration.copy(availability = KnowledgeContributionAvailability.Unavailable)))

    override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
      _activation_count.incrementAndGet()
      _snapshot.set(Some(KnowledgeContributionSnapshot(_declaration, _payload)))
      Consequence.unit
    }

    override def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot] = {
      _read_count.incrementAndGet()
      _snapshot.get match {
        case Some(snapshot) => Consequence.success(snapshot)
        case None => Consequence.serviceUnavailable("fixture snapshot unavailable")
      }
    }
  }

  private final class IndependentConsumer(
    domainid: String,
    expectedschema: KnowledgeContributionSchema,
    hub: ComponentKnowledgeHub
  ) extends Component() with ComponentActivation {
    private val _activation_count = new AtomicInteger(0)
    private val _activation_probe = new AtomicReference[Option[Consequence[ComponentKnowledgeSnapshot]]](None)
    private val _last_snapshot = new AtomicReference[Option[ComponentKnowledgeSnapshot]](None)

    def activationCount: Int = _activation_count.get
    def activationProbe: Option[Consequence[ComponentKnowledgeSnapshot]] = _activation_probe.get
    def lastSnapshot: Option[ComponentKnowledgeSnapshot] = _last_snapshot.get
    def discoverC(): Consequence[Option[ComponentKnowledgeContribution]] = hub.discoverC(domainid)
    def readC(contribution: ComponentKnowledgeContribution): Consequence[ComponentKnowledgeSnapshot] = {
      val result = hub.readC(contribution, expectedschema)
      _last_snapshot.set(result.toOption)
      result
    }

    override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
      _activation_count.incrementAndGet()
      val result = discoverC().flatMap {
        case Some(contribution) => hub.readC(contribution, expectedschema)
        case None => Consequence.serviceUnavailable("fixture contribution absent")
      }
      _activation_probe.set(Some(result))
      Consequence.unit
    }
  }
}
