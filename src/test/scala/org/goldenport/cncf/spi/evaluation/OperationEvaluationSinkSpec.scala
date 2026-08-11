package org.goldenport.cncf.spi.evaluation

import java.time.{Duration, Instant}

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.operation.evaluation.{CorpusCandidateFact, ExperimentObservationFact, OperationEvaluationAttemptId, OperationEvaluationCorrelation, OperationEvaluationCrossSinkPolicy, OperationEvaluationCrossSinkRoute, OperationEvaluationDeliveryResult, OperationEvaluationDeliveryStatus, OperationEvaluationExecutionId, OperationEvaluationFactId, OperationEvaluationLabel, OperationEvaluationMeasurement, OperationEvaluationName, OperationEvaluationOperationIdentity, OperationEvaluationOutcome, OperationEvaluationSinkIdentity, OperationEvaluationStartFact, OperationEvaluationTerminalFact, OperationEvaluationText}
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiProviderComponent, SpiResolver, SpiSelection, SpiTraceMetadata}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the Phase 48 Corpus and Experiment sink SPI.
 *
 * @since   Jul. 23, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationSinkSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _instant = Instant.parse("2026-07-23T12:00:00Z")

  "Operation Evaluation standard sink SPI" should {
    "resolve provider capabilities" which {
    "provide independent disabled Corpus and Experiment capabilities when no provider is connected" in {
      Given("optional Corpus and Experiment consumer sockets without provider components")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("evaluation_sink_disabled")
      val corpusconsumer = _initialized_component(
        subsystem,
        "org.goldenport.cncf.test.CorpusConsumer",
        CorpusConsumerComponent()
      )
      val experimentconsumer = _initialized_component(
        subsystem,
        "org.goldenport.cncf.test.ExperimentConsumer",
        ExperimentConsumerComponent()
      )
      val start = _start_fact("disabled-start")

      When("the common SPI resolver installs the available component graph")
      val resolution = SpiResolver.resolve(Vector(corpusconsumer, experimentconsumer))
      val corpusresult = corpusconsumer.corpusEvaluationSink.recordStart(start)
      val experimentresult = experimentconsumer.experimentEvaluationSink.recordStart(start)

      Then("both sockets remain uninstalled while their disabled capabilities discard safely")
      resolution shouldBe a[Consequence.Success[_]]
      corpusconsumer.isSpiInstalled shouldBe false
      experimentconsumer.isSpiInstalled shouldBe false
      corpusresult.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Discarded)
      experimentresult.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Discarded)
      corpusresult.toOption.toVector.flatMap(_.limitations).map(_.kind.token) shouldBe Vector("unavailable")
      experimentresult.toOption.toVector.flatMap(_.limitations).map(_.kind.token) shouldBe Vector("unavailable")
    }

    "capture deterministic fake invocations in delivery order" in {
      Given("isolated fake sinks, automatic facts, and domain-specific supplemental facts")
      given ExecutionContext = ExecutionContext.create()
      val corpus = _success(DeterministicCorpusEvaluationSink.createC("catalog", "fake-corpus"))
      val experiment = _success(DeterministicExperimentEvaluationSink.createC("catalog", "fake-experiment"))
      val start = _start_fact("ordered-start")
      val terminal = _terminal_fact("ordered-terminal")
      val candidate = _candidate_fact("candidate summary")
      val observation = _observation_fact("observation label")
      val counts = Gen.choose(1, 24)

      When("the fakes receive the bounded facts")
      val corpusresults = Vector(
        corpus.recordStart(start),
        corpus.recordTerminal(terminal),
        corpus.submitCandidate(candidate)
      )
      val experimentresults = Vector(
        experiment.recordStart(start),
        experiment.recordTerminal(terminal),
        experiment.submitObservation(observation)
      )
      val property = Prop.forAll(counts) { count =>
        val generatedcorpus = _success(DeterministicCorpusEvaluationSink.createC("catalog", "generated-corpus"))
        val generatedexperiment = _success(DeterministicExperimentEvaluationSink.createC("catalog", "generated-experiment"))
        val facts = Vector.tabulate(count)(index => _start_fact(s"generated-$index"))
        val generatedcorpusresults = facts.map(generatedcorpus.recordStart)
        val generatedexperimentresults = facts.map(generatedexperiment.recordStart)

        generatedcorpusresults.forall(_.toOption.exists(_.status == OperationEvaluationDeliveryStatus.Delivered)) &&
          generatedexperimentresults.forall(_.toOption.exists(_.status == OperationEvaluationDeliveryStatus.Delivered)) &&
          generatedcorpus.facts == facts && generatedexperiment.facts == facts
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("both fake implementations return delivered results and preserve exact call order")
      corpusresults.flatMap(_.toOption).map(_.status) shouldBe Vector.fill(3)(OperationEvaluationDeliveryStatus.Delivered)
      experimentresults.flatMap(_.toOption).map(_.status) shouldBe Vector.fill(3)(OperationEvaluationDeliveryStatus.Delivered)
      corpus.facts shouldBe Vector(start, terminal, candidate)
      experiment.facts shouldBe Vector(start, terminal, observation)
      checked.passed shouldBe true
    }

    "preserve the submitted fact identity across repeated provider delivery" in {
      Given("one immutable automatic fact and a deterministic Corpus provider")
      given ExecutionContext = ExecutionContext.create()
      val corpus = _success(DeterministicCorpusEvaluationSink.createC(
        "catalog",
        "idempotent-corpus"
      ))
      val start = _start_fact("repeated-start")

      When("the caller repeats delivery of the same fact")
      val results = Vector(corpus.recordStart(start), corpus.recordStart(start))

      Then("both calls retain one stable fact identity for provider-side deduplication")
      results.flatMap(_.toOption).map(_.factId) shouldBe Vector.fill(2)(start.id)
      corpus.facts.map(_.id) shouldBe Vector.fill(2)(start.id)
      corpus.facts.distinct shouldBe Vector(start)
    }
    }

    "trace and bound installed provider invocation" which {
    "install and trace Corpus and Experiment providers at their calling component sockets" in {
      Given("initialized providers, consumers, and payload-bearing supplemental facts")
      val subsystem = TestComponentFactory.emptySubsystem("evaluation_sink_trace")
      val corpusfake = _success(DeterministicCorpusEvaluationSink.createC("wrong-socket", "wrong-provider", Some("wrong-instance")))
      val experimentfake = _success(DeterministicExperimentEvaluationSink.createC("wrong-socket", "wrong-provider", Some("wrong-instance")))
      val corpusprovider = _initialized_component(
        subsystem,
        "textus_corpus",
        CorpusProviderComponent(MisidentifyingCorpusSink(corpusfake))
      )
      val experimentprovider = _initialized_component(subsystem, "textus_experiment", ExperimentProviderComponent(experimentfake))
      val corpusconsumer = _initialized_component(subsystem, "catalog", CorpusConsumerComponent())
      val experimentconsumer = _initialized_component(subsystem, "pricing", ExperimentConsumerComponent())
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val before = RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.total
      val candidate = _candidate_fact("private corpus evidence")
      val observation = _observation_fact("private experiment label")
      val corpusconsumername = _success(OperationEvaluationName.parseC(corpusconsumer.name)).print
      val corpusprovidername = _success(OperationEvaluationName.parseC(corpusprovider.name)).print
      val experimentconsumername = _success(OperationEvaluationName.parseC(experimentconsumer.name)).print
      val experimentprovidername = _success(OperationEvaluationName.parseC(experimentprovider.name)).print

      When("the resolver installs each provider and the consumer invokes both standard sinks")
      val resolution = SpiResolver.resolve(Vector(corpusprovider, experimentprovider, corpusconsumer, experimentconsumer))
      val corpusresult = corpusconsumer.corpusEvaluationSink.submitCandidate(candidate)
      val experimentresult = experimentconsumer.experimentEvaluationSink.submitObservation(observation)

      Then("caller-side traces identify only structural delivery metadata and omit fact content")
      resolution shouldBe a[Consequence.Success[_]]
      corpusconsumer.isSpiInstalled shouldBe true
      experimentconsumer.isSpiInstalled shouldBe true
      corpusresult.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Delivered)
      experimentresult.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Delivered)
      corpusresult.toOption.map(_.factId) shouldBe Some(candidate.id)
      corpusresult.toOption.map(_.sink.toRecord.getString("socketComponent")) shouldBe Some(Some(corpusconsumername))
      corpusresult.toOption.map(_.sink.toRecord.getString("providerComponent")) shouldBe Some(Some(corpusprovidername))
      experimentresult.toOption.map(_.sink.toRecord.getString("socketComponent")) shouldBe Some(Some(experimentconsumername))
      experimentresult.toOption.map(_.sink.toRecord.getString("providerComponent")) shouldBe Some(Some(experimentprovidername))
      corpusfake.facts shouldBe Vector(candidate)
      experimentfake.facts shouldBe Vector(observation)
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing")).toRecord.print
      calltree should include ("spi:corpus-evaluation-sink.submitCandidate")
      calltree should include ("spi:experiment-evaluation-sink.submitObservation")
      calltree should include ("fact_kind=corpus-candidate")
      calltree should include ("fact_kind=experiment-observation")
      calltree should include ("delivery_status=delivered")
      calltree should not include "private corpus evidence"
      calltree should not include "private experiment label"
      calltree should not include candidate.id.toString
      calltree should not include candidate.correlation.executionId.toString
      RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.total should be >= (before + 2)
    }

    "mark the active provider boundary and suppress same-sink reentry" in {
      Given("a traced Corpus sink and a provider that observes only its execution context")
      val delegate = _success(DeterministicCorpusEvaluationSink.createC("catalog", "textus-corpus"))
      val recording = ContextRecordingCorpusSink(delegate)
      val metadata = SpiTraceMetadata(
        contract = CorpusEvaluationSink.CONTRACT_NAME,
        operation = "",
        socketComponent = "catalog",
        providerComponent = "textus-corpus"
      )
      val traced = CorpusEvaluationSink.traced(recording, metadata)
      val candidate = _candidate_fact("provider-boundary")
      val context = ExecutionContext.create()

      When("the caller invokes the provider and then attempts the same boundary while it is active")
      val delivered = traced.submitCandidate(candidate)(using context)
      val sink = _success(OperationEvaluationSinkIdentity.createC(
        CorpusEvaluationSink.CONTRACT_NAME,
        "catalog",
        "textus-corpus"
      ))
      val active = _success(ExecutionContext.withActiveOperationEvaluationSink(context, sink))
      val suppressed = traced.submitCandidate(candidate)(using active)

      Then("the provider sees its active identity exactly once and recursive delivery is discarded")
      delivered.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Delivered)
      recording.activeSinks shouldBe Vector(Vector(sink))
      suppressed.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Discarded)
      suppressed.toOption.toVector.flatMap(_.limitations).map(_.kind.token) shouldBe Vector("reentrant-suppressed")
      recording.activeSinks shouldBe Vector(Vector(sink))
      delegate.facts shouldBe Vector(candidate)
    }

    "apply the same explicit cross-sink policy at the direct SPI boundary" in {
      Given("a traced Corpus provider reached from an active Experiment provider")
      val delegate = _success(DeterministicCorpusEvaluationSink.createC("catalog", "textus-corpus"))
      val recording = ContextRecordingCorpusSink(delegate)
      val metadata = SpiTraceMetadata(
        contract = CorpusEvaluationSink.CONTRACT_NAME,
        operation = "",
        socketComponent = "catalog",
        providerComponent = "textus-corpus"
      )
      val traced = CorpusEvaluationSink.traced(recording, metadata)
      val candidate = _candidate_fact("cross-sink-provider-boundary")
      val experiment = _success(OperationEvaluationSinkIdentity.createC(
        ExperimentEvaluationSink.CONTRACT_NAME,
        "pricing",
        "textus-experiment"
      ))
      val route = _success(OperationEvaluationCrossSinkRoute.createC(
        ExperimentEvaluationSink.CONTRACT_NAME,
        CorpusEvaluationSink.CONTRACT_NAME
      ))
      val policy = _success(OperationEvaluationCrossSinkPolicy.createC(Vector(route), 2))
      val deniedbase = ExecutionContext.create()
      val deniedcontext = _success(
        ExecutionContext.withActiveOperationEvaluationSink(deniedbase, experiment)
      )
      val allowedbase = ExecutionContext.create()
      val policyscope = ScopeContext.withOperationEvaluationCrossSinkPolicy(
        allowedbase.cncfCore.scope,
        policy
      )
      val allowedcontext = _success(
        ExecutionContext.withActiveOperationEvaluationSink(
          allowedbase.withScope(policyscope),
          experiment
        )
      )
      val corpus = _success(OperationEvaluationSinkIdentity.createC(
        CorpusEvaluationSink.CONTRACT_NAME,
        "catalog",
        "textus-corpus"
      ))

      When("the direct SPI call runs first under default deny and then under the allowlist")
      val denied = traced.submitCandidate(candidate)(using deniedcontext)
      val allowed = traced.submitCandidate(candidate)(using allowedcontext)

      Then("the default route is discarded and the admitted call preserves both causal sinks")
      denied.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Discarded)
      denied.toOption.toVector.flatMap(_.limitations).map(_.kind.token) shouldBe
        Vector("cross-sink-suppressed")
      allowed.toOption.map(_.status) shouldBe Some(OperationEvaluationDeliveryStatus.Delivered)
      recording.activeSinks shouldBe Vector(Vector(experiment, corpus))
      delegate.facts shouldBe Vector(candidate)
    }

    "preserve an installed sink failure without replacing its Conclusion" in {
      Given("a Corpus provider that rejects one delivery with a structured failure")
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val subsystem = TestComponentFactory.emptySubsystem("evaluation_sink_failure")
      val provider = _initialized_component(subsystem, "failing_corpus_provider", FailingCorpusProviderComponent())
      val consumer = _initialized_component(subsystem, "corpus_consumer", CorpusConsumerComponent())
      val recordsbefore = RuntimeDashboardMetrics.spiDiagnosticRecords

      When("the resolved consumer submits a candidate")
      SpiResolver.resolve(Vector(provider, consumer)) shouldBe a[Consequence.Success[_]]
      val result = consumer.corpusEvaluationSink.submitCandidate(_candidate_fact("rejected evidence"))

      Then("the provider Conclusion and safe caller-side failure trace are retained")
      result shouldBe a[Consequence.Failure[_]]
      val display = result match {
        case Consequence.Failure(conclusion) => conclusion.display
        case Consequence.Success(_) => fail("expected corpus sink failure")
      }
      display should include ("rejected evidence")
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing")).toRecord.print
      calltree should include ("spi:corpus-evaluation-sink.submitCandidate")
      calltree should include ("outcome=failure")
      calltree should not include "rejected evidence"
      RuntimeDashboardMetrics.spiDiagnosticRecords shouldBe recordsbefore
    }
    }
  }

  private final case class CorpusConsumerComponent() extends Component with CorpusEvaluationSinkSocket
  private final case class ExperimentConsumerComponent() extends Component with ExperimentEvaluationSinkSocket

  private final case class CorpusProviderComponent(sink: CorpusEvaluationSink)
      extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] = Vector(CorpusProvider(sink))
  }

  private final case class ExperimentProviderComponent(sink: ExperimentEvaluationSink)
      extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] = Vector(ExperimentProvider(sink))
  }

  private final case class FailingCorpusProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] = Vector(CorpusProvider(new CorpusEvaluationSink {
      def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
        Consequence.operationInvalid("planned corpus sink failure")
      def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
        Consequence.operationInvalid("planned corpus sink failure")
      def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
        Consequence.argumentInvalid("candidate", "accepted candidate", fact.toRecord)
    }))
  }

  private final case class MisidentifyingCorpusSink(
    underlying: CorpusEvaluationSink
  ) extends CorpusEvaluationSink {
    def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      underlying.recordStart(fact).flatMap(_misidentify)

    def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      underlying.recordTerminal(fact).flatMap(_misidentify)

    def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      underlying.submitCandidate(fact).flatMap(_misidentify)

    private def _misidentify(result: OperationEvaluationDeliveryResult): Consequence[OperationEvaluationDeliveryResult] =
      OperationEvaluationDeliveryResult.createC(
        _fact_id("provider-substitute"),
        result.sink,
        result.status,
        result.limitations,
        result.confidentiality
      )
  }

  private final case class ContextRecordingCorpusSink(
    underlying: CorpusEvaluationSink
  ) extends CorpusEvaluationSink {
    private var _active_sinks = Vector.empty[Vector[OperationEvaluationSinkIdentity]]

    def activeSinks: Vector[Vector[OperationEvaluationSinkIdentity]] = synchronized(_active_sinks)

    def recordStart(fact: OperationEvaluationStartFact)(using ctx: ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _record(ctx)(underlying.recordStart(fact))

    def recordTerminal(fact: OperationEvaluationTerminalFact)(using ctx: ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _record(ctx)(underlying.recordTerminal(fact))

    def submitCandidate(fact: CorpusCandidateFact)(using ctx: ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _record(ctx)(underlying.submitCandidate(fact))

    private def _record(
      ctx: ExecutionContext
    )(result: => Consequence[OperationEvaluationDeliveryResult]): Consequence[OperationEvaluationDeliveryResult] = {
      synchronized {
        _active_sinks = _active_sinks :+ ctx.operationEvaluation.activeSinks
      }
      result
    }
  }

  private final case class CorpusProvider(sink: CorpusEvaluationSink) extends SpiProvider[CorpusEvaluationSink] {
    def supports(contract: SpiContract[CorpusEvaluationSink], selection: SpiSelection)(using ExecutionContext): Boolean =
      contract.name == CorpusEvaluationSink.CONTRACT_NAME && contract.runtimeClass == classOf[CorpusEvaluationSink]
    def provide(contract: SpiContract[CorpusEvaluationSink], selection: SpiSelection)(using ExecutionContext): Consequence[CorpusEvaluationSink] =
      Consequence.success(sink)
  }

  private final case class ExperimentProvider(sink: ExperimentEvaluationSink) extends SpiProvider[ExperimentEvaluationSink] {
    def supports(contract: SpiContract[ExperimentEvaluationSink], selection: SpiSelection)(using ExecutionContext): Boolean =
      contract.name == ExperimentEvaluationSink.CONTRACT_NAME && contract.runtimeClass == classOf[ExperimentEvaluationSink]
    def provide(contract: SpiContract[ExperimentEvaluationSink], selection: SpiSelection)(using ExecutionContext): Consequence[ExperimentEvaluationSink] =
      Consequence.success(sink)
  }

  private def _initialized_component[A <: Component](
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    name: String,
    component: A
  ): A = {
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = Component.Core.create(
        name = componentid.name,
        componentid = componentid,
        instanceid = ComponentInstanceId.default(componentid),
        protocol = Protocol.empty
      ),
      origin = ComponentOrigin.Builtin
    ))
    component
  }

  private def _start_fact(entropy: String): OperationEvaluationStartFact =
    OperationEvaluationStartFact.create(_fact_id(entropy), _correlation(entropy), _instant)

  private def _terminal_fact(entropy: String): OperationEvaluationTerminalFact =
    _success(OperationEvaluationTerminalFact.createC(
      _fact_id(entropy),
      _correlation(entropy),
      _instant.plusMillis(10),
      OperationEvaluationOutcome.Success,
      Duration.ofMillis(10)
    ))

  private def _candidate_fact(summary: String): CorpusCandidateFact =
    _success(CorpusCandidateFact.createC(
      _fact_id("candidate"),
      _correlation("candidate"),
      _instant,
      summary = Some(_success(OperationEvaluationText.parseC(summary)))
    ))

  private def _observation_fact(labelvalue: String): ExperimentObservationFact =
    _success(ExperimentObservationFact.createC(
      _fact_id("observation"),
      _correlation("observation"),
      _instant,
      Vector(_success(OperationEvaluationMeasurement.createC("latency", BigDecimal("12.5"), Some("ms")))),
      Vector(_success(OperationEvaluationLabel.createC("scenario", labelvalue)))
    ))

  private def _correlation(entropy: String): OperationEvaluationCorrelation =
    OperationEvaluationCorrelation(
      OperationEvaluationExecutionId("spec", "sink", Some(_instant), Some(s"execution-$entropy")),
      OperationEvaluationAttemptId("spec", "sink", Some(_instant), Some(s"attempt-$entropy")),
      _success(OperationEvaluationOperationIdentity.createC("catalog", "pricing", "quote"))
    )

  private def _fact_id(entropy: String): OperationEvaluationFactId =
    OperationEvaluationFactId("spec", "sink", Some(_instant), Some(entropy))

  private def _success[A](result: Consequence[A]): A =
    result.fold(x => fail(x.display), identity)
}
