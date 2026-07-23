package org.goldenport.cncf.spi.evaluation

import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.evaluation.{CorpusCandidateFact, CorpusCaseReference, CorpusEvaluationCorrelation, CorpusRevisionReference, ExperimentArmReference, ExperimentEvaluationCorrelation, ExperimentObservationFact, ExperimentReference, ExperimentRunReference, OperationEvaluationAttemptId, OperationEvaluationCorrelation, OperationEvaluationDeliveryStatus, OperationEvaluationExecutionId, OperationEvaluationFact, OperationEvaluationFactId, OperationEvaluationLabel, OperationEvaluationMeasurement, OperationEvaluationOperationIdentity, OperationEvaluationOutcome, OperationEvaluationStartFact, OperationEvaluationTerminalFact, OperationEvaluationText}
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiProviderComponent, SpiSelection}
import org.scalatest.DoNotDiscover
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Cross-repository acceptance specification. It is intentionally excluded from
 * default discovery because the downstream class directories are explicit
 * Phase 48 acceptance inputs, not CNCF compile-time dependencies.
 *
 * @since   Jul. 24, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
@DoNotDiscover
final class OperationEvaluationDownstreamHandoffSpec
    extends AnyWordSpec with Matchers with GivenWhenThen {
  "Textus downstream operation evaluation adapters" should {
    "preserve one admitted correlation across the Corpus case and Experiment arm handoff" in {
      Given("compiled Textus Corpus and Experiment components loaded outside the CNCF classpath")
      given ExecutionContext = ExecutionContext.create()
      val loader = new URLClassLoader(
        Array(
          _classes_root("textus.corpus.classes").toUri.toURL,
          _classes_root("textus.experiment.classes").toUri.toURL
        ),
        getClass.getClassLoader
      )

      try {
        val corpuscomponent = _component(
          loader,
          "org.simplemodeling.textus.corpus.impl.CorpusPrimaryComponent"
        )
        val experimentcomponent = _component(
          loader,
          "org.simplemodeling.textus.experiment.impl.ExperimentPrimaryComponent"
        )
        val corpusprovider = _provider(
          corpuscomponent,
          SpiContract(CorpusEvaluationSink.CONTRACT_NAME, classOf[CorpusEvaluationSink])
        )
        val experimentprovider = _provider(
          experimentcomponent,
          SpiContract(ExperimentEvaluationSink.CONTRACT_NAME, classOf[ExperimentEvaluationSink])
        )
        val selection = SpiSelection(mode = Some("offline"))
        val corpussink = _success(corpusprovider.provide(
          SpiContract(CorpusEvaluationSink.CONTRACT_NAME, classOf[CorpusEvaluationSink]),
          selection
        ))
        val experimentsink = _success(experimentprovider.provide(
          SpiContract(ExperimentEvaluationSink.CONTRACT_NAME, classOf[ExperimentEvaluationSink]),
          selection
        ))
        val correlation = _correlation()
        val start = OperationEvaluationStartFact.create(
          _fact_id("start"),
          correlation,
          _instant
        )
        val terminal = _success(OperationEvaluationTerminalFact.createC(
          _fact_id("terminal"),
          correlation,
          _instant.plusMillis(25),
          OperationEvaluationOutcome.Success,
          Duration.ofMillis(25)
        ))
        val candidate = _success(CorpusCandidateFact.createC(
          _fact_id("candidate"),
          correlation,
          _instant.plusMillis(10),
          Some(_success(OperationEvaluationText.parseC("candidate accepted"))),
          Vector(_success(OperationEvaluationLabel.createC("decision", "review")))
        ))
        val observation = _success(ExperimentObservationFact.createC(
          _fact_id("observation"),
          correlation,
          _instant.plusMillis(20),
          Vector(_success(OperationEvaluationMeasurement.createC("score", BigDecimal("0.92")))),
          Vector(_success(OperationEvaluationLabel.createC("verdict", "pass")))
        ))

        When("the Textus-owned providers receive common and sink-specific facts")
        val results = Vector(
          _success(corpussink.recordStart(start)),
          _success(experimentsink.recordStart(start)),
          _success(corpussink.recordTerminal(terminal)),
          _success(experimentsink.recordTerminal(terminal)),
          _success(corpussink.submitCandidate(candidate)),
          _success(experimentsink.submitObservation(observation))
        )

        Then("the actual adapters deliver every fact and retain one execution and attempt")
        results.map(_.status) shouldBe Vector.fill(6)(OperationEvaluationDeliveryStatus.Delivered)
        val corpusfacts = _facts(corpussink)
        val experimentfacts = _facts(experimentsink)
        val allfacts = corpusfacts ++ experimentfacts
        allfacts.map(_.correlation.executionId).distinct shouldBe Vector(correlation.executionId)
        allfacts.map(_.correlation.attemptId).distinct shouldBe Vector(correlation.attemptId)
        allfacts.flatMap(_.correlation.corpus.map(_.revision.print)).distinct shouldBe
          Vector("revision-20260723")
        allfacts.flatMap(_.correlation.corpus.flatMap(_.caseReference).map(_.print)).distinct shouldBe
          Vector("case-17")
        allfacts.flatMap(_.correlation.experiment.map(_.experiment.print)).distinct shouldBe
          Vector("experiment-9")
        allfacts.flatMap(_.correlation.experiment.flatMap(_.arm).map(_.print)).distinct shouldBe
          Vector("arm-control")
        allfacts.flatMap(_.correlation.experiment.flatMap(_.run).map(_.print)).distinct shouldBe
          Vector("run-3")

        And("the common framework facts keep identical ids across both adapters")
        corpusfacts.take(2).map(_.id) shouldBe experimentfacts.take(2).map(_.id)
      } finally {
        loader.close()
      }
    }
  }

  private val _instant = Instant.parse("2026-07-24T00:00:00Z")

  private def _classes_root(propertyname: String): Path =
    Option(System.getProperty(propertyname))
      .map(Path.of(_))
      .filter(Files.isDirectory(_))
      .getOrElse(fail(s"required downstream classes directory is missing: -D$propertyname=<path>"))

  private def _component(
    loader: ClassLoader,
    classname: String
  ): Component & SpiProviderComponent =
    loader
      .loadClass(classname)
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[Component & SpiProviderComponent]

  private def _provider[S](
    component: SpiProviderComponent,
    contract: SpiContract[S]
  )(using ExecutionContext): SpiProvider[S] = {
    val selection = SpiSelection(mode = Some("offline"))
    component.spiProviders
      .map(_.asInstanceOf[SpiProvider[S]])
      .find(_.supports(contract, selection))
      .getOrElse(fail(s"offline downstream provider is not exposed: ${contract.name}"))
  }

  private def _facts(sink: Any): Vector[OperationEvaluationFact] =
    sink.getClass
      .getMethod("facts")
      .invoke(sink)
      .asInstanceOf[Vector[OperationEvaluationFact]]

  private def _correlation(): OperationEvaluationCorrelation = {
    val revision = _success(CorpusRevisionReference.parseC("revision-20260723"))
    val corpuscase = _success(CorpusCaseReference.parseC("case-17"))
    val experiment = _success(ExperimentReference.parseC("experiment-9"))
    val arm = _success(ExperimentArmReference.parseC("arm-control"))
    val run = _success(ExperimentRunReference.parseC("run-3"))
    OperationEvaluationCorrelation(
      OperationEvaluationExecutionId("downstream", "execution", Some(_instant), Some("handoff")),
      OperationEvaluationAttemptId("downstream", "attempt", Some(_instant), Some("handoff")),
      _success(OperationEvaluationOperationIdentity.createC("sample", "evaluation", "evaluate")),
      corpus = Some(CorpusEvaluationCorrelation.create(revision, Some(corpuscase))),
      experiment = Some(_success(ExperimentEvaluationCorrelation.createC(
        experiment,
        Some(arm),
        Some(run),
        Some(revision)
      )))
    )
  }

  private def _fact_id(entropy: String): OperationEvaluationFactId =
    OperationEvaluationFactId("downstream", "fact", Some(_instant), Some(entropy))

  private def _success[A](consequence: Consequence[A]): A =
    consequence.toOption.getOrElse(fail(consequence.toString))
}
