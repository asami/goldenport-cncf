package org.goldenport.cncf.job

import java.time.{Clock, Duration, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.operation.evaluation.{CorpusCaseReference, CorpusEvaluationCorrelation, CorpusRevisionReference, ExperimentArmReference, ExperimentEvaluationCorrelation, ExperimentReference, ExperimentRunReference, OperationEvaluationAssignment, OperationEvaluationCorrelation, OperationEvaluationName, OperationEvaluationOperationIdentity, OperationEvaluationSinkIdentity, OperationEvaluationText}
import org.goldenport.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for operation-evaluation correlation across Job attempts.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationJobContextSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with JobEngineTestFixture {
  private val _instant = Instant.parse("2026-07-23T14:00:00Z")

  "Operation evaluation Job correlation" should {
    "retain logical execution identity and renew attempt identity after retry rehydration" in {
      Given("a delayed-retry Job submitted with one prepared logical invocation")
      val state = InMemoryJobEngine.State()
      val schedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60L)))
      val clock = new ManualJobTimeSource(_instant)
      val timer1 = new InMemoryJobEngine.ManualJobTimer(clock)
      val attempts = new AtomicInteger(0)
      val correlations = ArrayBuffer.empty[OperationEvaluationCorrelation]
      val activesinks = ArrayBuffer.empty[Vector[OperationEvaluationSinkIdentity]]
      val assignments = ArrayBuffer.empty[Option[OperationEvaluationAssignment]]
      val task = CorrelationTask(
        attempts = attempts,
        correlations = correlations,
        activesinks = activesinks,
        assignments = assignments
      )
      val operation = _success(OperationEvaluationOperationIdentity.createC("catalog", "pricing", "quote"))
      val (corpus, experiment) = _admitted_correlations()
      val assignment = OperationEvaluationAssignment(
        _success(OperationEvaluationName.parseC("variant-b")),
        Some(_success(OperationEvaluationText.parseC("execution-plan-b")))
      )
      val prepared0 = _success(ExecutionContext.prepareOperationEvaluation(
        _execution_context(clock.now()),
        operation,
        Some(corpus),
        Some(experiment),
        Some(assignment)
      ))
      val sink = _success(OperationEvaluationSinkIdentity.createC(
        "corpus-evaluation-sink",
        "catalog",
        "textus-corpus"
      ))
      val prepared = _success(ExecutionContext.withActiveOperationEvaluationSink(prepared0, sink))
      val engine1 = createManualInMemoryJobEngine(state, schedule, clock, timer1)
      val jobid = _success(engine1.submit(List(task), prepared))

      When("the first attempt requests a delayed retry and a new engine rehydrates it")
      engine1.drainOne() shouldBe true
      engine1.query(jobid).flatMap(_.retry.nextRetryDueAt) should not be empty
      engine1.shutdown()
      val timer2 = new InMemoryJobEngine.ManualJobTimer(clock)
      val engine2 = createManualInMemoryJobEngine(state, schedule, clock, timer2)
      try {
        timer2.pendingCount shouldBe 1
        timer2.advanceBy(Duration.ofMillis(60L)) shouldBe 1
        engine2.drainAll()

        Then("both attempts correlate to one logical execution and Job but have distinct attempt and Task ids")
        engine2.query(jobid).map(_.status) shouldBe Some(JobStatus.Succeeded)
        val captured = correlations.synchronized(correlations.toVector)
        captured should have size 2
        captured.map(_.executionId).distinct should have size 1
        captured.map(_.attemptId).distinct should have size 2
        captured.flatMap(_.jobId).distinct shouldBe Vector(jobid)
        captured.flatMap(_.taskId).distinct should have size 2
        captured.map(_.operation).distinct shouldBe Vector(operation)
        captured.forall(_.parentExecutionId.isEmpty) shouldBe true
        captured.map(_.corpus).distinct shouldBe Vector(Some(corpus))
        captured.map(_.experiment).distinct shouldBe Vector(Some(experiment))
        activesinks.synchronized(activesinks.toVector) shouldBe Vector(Vector(sink), Vector(sink))
        assignments.synchronized(assignments.toVector) shouldBe Vector(
          Some(assignment),
          Some(assignment)
        )
      } finally {
        engine2.shutdown()
      }
    }
  }

  private final case class CorrelationTask(
    attempts: AtomicInteger,
    correlations: ArrayBuffer[OperationEvaluationCorrelation],
    activesinks: ArrayBuffer[Vector[OperationEvaluationSinkIdentity]],
    assignments: ArrayBuffer[Option[OperationEvaluationAssignment]],
    actionid: ActionId = ActionId.generate()
  ) extends JobTask {
    def actionId: ActionId = actionid

    def run(ctx: ExecutionContext): TaskOutcome = {
      val attempted = _success(ExecutionContext.beginOperationEvaluationAttempt(ctx))
      val correlation = attempted.operationEvaluation.correlation.getOrElse(
        throw new IllegalStateException("operation evaluation attempt correlation missing")
      )
      correlations.synchronized {
        correlations += correlation
      }
      activesinks.synchronized {
        activesinks += attempted.operationEvaluation.activeSinks
      }
      assignments.synchronized {
        assignments += attempted.operationEvaluation.invocation.flatMap(_.assignment)
      }
      if (attempts.incrementAndGet() == 1)
        TaskFailed(Conclusion.simple("planned operation evaluation retry").copy(
          disposition = Disposition(Disposition.UserAction.RetryLater)
        ))
      else
        TaskSucceeded(OperationResponse.Scalar("ok"))
    }
  }

  private def _execution_context(instant: Instant): ExecutionContext = {
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val ids = IdGenerationContext.deterministic(
      IdGenerationContext.IdNamespace("operation_evaluation", "job"),
      clock,
      "retry-resume"
    )
    ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
  }

  private def _admitted_correlations(): (CorpusEvaluationCorrelation, ExperimentEvaluationCorrelation) = {
    val revision = _success(CorpusRevisionReference.parseC("revision-9"))
    val corpus = CorpusEvaluationCorrelation.create(
      revision,
      Some(_success(CorpusCaseReference.parseC("case-17")))
    )
    val experiment = _success(ExperimentEvaluationCorrelation.createC(
      _success(ExperimentReference.parseC("experiment-17")),
      Some(_success(ExperimentArmReference.parseC("arm-b"))),
      Some(_success(ExperimentRunReference.parseC("run-4"))),
      Some(revision)
    ))
    corpus -> experiment
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(result.toString))
}
