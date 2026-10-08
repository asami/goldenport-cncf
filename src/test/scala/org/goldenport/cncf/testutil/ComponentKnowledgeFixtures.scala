package org.goldenport.cncf.testutil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentActivation, ComponentActivationContext, ComponentInit, ComponentInstanceId, ComponentKnowledgeProvider, ComponentOrigin}
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.knowledge.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
/** Only Phase 73 opaque suppliers, hub-only consumers and owned assembly scopes. */
object ComponentKnowledgeFixtures {
  val domainId = "sentinel-domain-73"
  val schema = KnowledgeContributionSchema("sentinel-schema-73", "1")
  val firstGeneration = "sentinel-generation-g1"
  val secondGeneration = "sentinel-generation-g2"
  val firstProvenance = Vector(KnowledgeSourceRef("fixture", "sentinel-source-a", Some("urn:fixture:sentinel-a")))
  val secondProvenance = Vector(KnowledgeSourceRef("fixture", "sentinel-source-b", Some("urn:fixture:sentinel-b")))
  val firstPayload = Vector[Byte](-128, 0, -1, 127, 53)
  val secondPayload = Vector[Byte](64, -32, 0, 12, -127, 7)
  val failureMarkers = Vector(
    domainId, firstGeneration, secondGeneration, schema.id,
    "sentinel-payload-73", "sentinel-exception-73",
    "https://fixture.invalid/sentinel-private-73", "/private/fixture/sentinel-73",
    "textus://bok/sentinel-73", "sentinel-source-conclusion-73",
    "sentinel-changed-domain", "sentinel-changed-schema",
    "sentinel-source-a", "sentinel-source-b", "urn:fixture:sentinel-a", "urn:fixture:sentinel-b"
  )
  val failureText = failureMarkers.mkString(" ")

  def declaration(
    generation: String = firstGeneration,
    availability: KnowledgeContributionAvailability = KnowledgeContributionAvailability.Ready,
    provenance: Vector[KnowledgeSourceRef] = firstProvenance
  ): KnowledgeContributionDeclaration =
    KnowledgeContributionDeclaration(domainId, generation, schema, provenance, availability)

  def snapshot(
    declaration: KnowledgeContributionDeclaration = ComponentKnowledgeFixtures.declaration(),
    payload: Vector[Byte] = firstPayload
  ): KnowledgeContributionSnapshot = KnowledgeContributionSnapshot(declaration, payload)

  private final case class PublishedState(
    advertised: KnowledgeContributionDeclaration,
    retained: Vector[KnowledgeContributionSnapshot]
  )

  /** One atomic supplier-owned publication, never a hub cache or latest fallback. */
  final class MutableSupplier(
    initialSnapshot: KnowledgeContributionSnapshot = snapshot(),
    declarationsOverride: Option[() => Vector[KnowledgeContributionDeclaration]] = None,
    readOverride: Option[KnowledgeContributionDeclaration => Consequence[KnowledgeContributionSnapshot]] = None
  ) extends Component() with ComponentKnowledgeProvider {
    private val _state = new AtomicReference(PublishedState(initialSnapshot.declaration, Vector(initialSnapshot)))
    private val _read_count = new AtomicInteger(0)

    def readCount: Int = _read_count.get
    override def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration] =
      declarationsOverride.fold(Vector(_state.get.advertised))(_.apply())

    override def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot] = {
      _read_count.incrementAndGet()
      readOverride match {
        case Some(callback) => callback(declaration)
        case None =>
          _state.get.retained.find(_.declaration == declaration) match {
            case Some(value) => Consequence.success(value)
            case None => Consequence.serviceUnavailable(failureText)
          }
      }
    }

    def publishC(candidate: KnowledgeContributionSnapshot, failPreparation: Boolean = false): Consequence[Unit] = {
      if (failPreparation)
        Consequence.serviceUnavailable(failureText)
      else {
        _state.updateAndGet(previous => PublishedState(
          candidate.declaration,
          previous.retained.filterNot(_.declaration == candidate.declaration) :+ candidate
        ))
        Consequence.unit
      }
    }

    def retire(generation: String): Unit = {
      _state.updateAndGet(previous => previous.copy(
        retained = previous.retained.filterNot(_.declaration.generation == generation)
      ))
      ()
    }
  }

  final class FirstProvider(
    initialSnapshot: KnowledgeContributionSnapshot = snapshot(),
    failActivation: Boolean = false
  ) extends Component() with ComponentKnowledgeProvider with ComponentActivation {
    private val _state = new AtomicReference(PublishedState(initialSnapshot.declaration, Vector(initialSnapshot)))
    private val _read_count = new AtomicInteger(0)
    private val _activation_count = new AtomicInteger(0)
    private val _activation_context = new AtomicReference[Option[ComponentActivationContext]](None)

    def readCount: Int = _read_count.get
    def activationCount: Int = _activation_count.get
    def lastActivationContext: Option[ComponentActivationContext] = _activation_context.get
    override def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration] = Vector(_state.get.advertised)

    override def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot] = {
      _read_count.incrementAndGet()
      _state.get.retained.find(_.declaration == declaration) match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.serviceUnavailable(failureText)
      }
    }

    override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
      _activation_count.incrementAndGet()
      _activation_context.set(Some(context))
      if (failActivation) Consequence.serviceUnavailable(failureText)
      else Consequence.unit
    }
  }

  /** Independent concrete supplier of the complete B snapshot through the supplementary capability. */
  final class SecondProvider extends Component() with ComponentKnowledgeProvider with ComponentActivation {
    private val _state = new AtomicReference(PublishedState(
      declaration(provenance = secondProvenance),
      Vector(snapshot(declaration(provenance = secondProvenance), secondPayload))
    ))
    private val _read_count = new AtomicInteger(0)
    private val _activation_count = new AtomicInteger(0)

    def preparedSnapshot: KnowledgeContributionSnapshot = _state.get.retained.head
    def readCount: Int = _read_count.get
    def activationCount: Int = _activation_count.get
    override def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration] = Vector(_state.get.advertised)

    override def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot] = {
      _read_count.incrementAndGet()
      _state.get.retained.find(_.declaration == declaration) match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.serviceUnavailable(failureText)
      }
    }

    override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
      _activation_count.incrementAndGet()
      Consequence.unit
    }
  }

  /** Consumer code receives only domain, schema and facade, with no supplier access. */
  final class IndependentConsumer(
    domainId: String,
    expectedSchema: KnowledgeContributionSchema,
    hub: ComponentKnowledgeHub
  ) extends Component() {
    private val _last_snapshot = new AtomicReference[Option[ComponentKnowledgeSnapshot]](None)

    def lastSnapshot: Option[ComponentKnowledgeSnapshot] = _last_snapshot.get
    def discoverC(): Consequence[Option[ComponentKnowledgeContribution]] = hub.discoverC(domainId)
    def readC(contribution: ComponentKnowledgeContribution): Consequence[ComponentKnowledgeSnapshot] = {
      val result = hub.readC(contribution, expectedSchema)
      result.toOption.foreach(value => _last_snapshot.set(Some(value)))
      result
    }
  }

  final case class Assembly(
    owner: Subsystem,
    provider: Component,
    consumers: Vector[IndependentConsumer],
    hub: ComponentKnowledgeHub
  )

  def initialize(owner: Subsystem, name: String, component: Component): Unit = {
    val componentid = TestComponentFactory.componentId(name)
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    component.initialize(ComponentInit(owner, core, ComponentOrigin.Builtin))
    ()
  }

  def withInstalled[A](providers: Vector[Component])(body: (Subsystem, ComponentKnowledgeHub) => A): A =
    RuntimeOutputCapture.capture {
      val owner = TestComponentFactory.admittedEmptySubsystem("knowledge-pure-branches")
      try {
        providers.zipWithIndex.foreach { (provider, index) => initialize(owner, s"pure-provider-$index", provider) }
        owner.add(providers)
        body(owner, ComponentKnowledgeHub.createC(owner).toOption.get)
      } finally Subsystem.shutdownOwned(owner)
    }.value

  def withAssembly[A](mode: RunMode, providerName: String, providerFactory: () => Component)(body: Assembly => A): A = {
    val previous = sys.props.get("textus.test")
    val previouscontext = GlobalRuntimeContext.current
    var descriptor = Option.empty[java.nio.file.Path]
    var owners = Vector.empty[Subsystem]
    var assembly = Option.empty[Assembly]
    RuntimeOutputCapture.capture {
      try {
        val directory = Files.createDirectories(Path.of("target/component-knowledge-hub/config/branches"))
        val path = Files.createTempFile(directory, "knowledge-branch-standard-", ".yaml")
        descriptor = Some(path)
        Files.writeString(path,
          """kind: test-descriptor
            |execution:
            |  profile: standard
            |  key: knowledge-branch-standard
            |  time:
            |    mode: system
            |  random:
            |    mode: system
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        System.clearProperty("textus.test")
        val owner = CncfRuntime.buildSubsystem(
          extraComponents = current => {
            owners = owners :+ current
            val provider = providerFactory()
            val hub = ComponentKnowledgeHub.createC(current).toOption.get
            val consumers = Vector.fill(2)(new IndependentConsumer(domainId, schema, hub))
            initialize(current, providerName, provider)
            consumers.zipWithIndex.foreach { (consumer, index) => initialize(current, s"branch-consumer-$index", consumer) }
            assembly = Some(Assembly(current, provider, consumers, hub))
            Vector(provider) ++ consumers
          },
          mode = Some(mode),
          args = Array(s"--textus.test.descriptor=$path", "--no-default-components")
        )
        if (!owners.exists(_ eq owner)) owners = owners :+ owner
        body(assembly.get)
      } finally {
        try owners.reverse.foreach(Subsystem.shutdownOwned)
        finally {
          try descriptor.foreach(path => Files.deleteIfExists(path))
          finally {
            try {
              previous match {
                case Some(value) => System.setProperty("textus.test", value)
                case None => System.clearProperty("textus.test")
              }
            } finally GlobalRuntimeContext.current = previouscontext
          }
        }
      }
    }.value
  }
}
