package org.goldenport.cncf.operation.evaluation

import java.util.concurrent.TimeUnit
import cats.data.NonEmptyVector

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.spi.evaluation.DeterministicCorpusEvaluationSink
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Job worker correlation in automatic capture.
 *
 * @since   Jul. 23, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationJobCaptureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Job-managed automatic operation evaluation capture" should {
    "correlate the worker attempt with the JobId returned by JobAsync" in {
      Given("a JobAsync command component with a deterministic Corpus sink")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("evaluation_job")
      val sink = _success(DeterministicCorpusEvaluationSink.createC("evaluation_job", "test-corpus"))
      val operation = EvaluationJobOperation("jobAsync")
      val service = spec.ServiceDefinition(
        "operation",
        spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val component = new EvaluationComponent
      val componentid = ComponentId("org.goldenport.cncf.test.EvaluationJob")
      val core = Component.Core.create(
        componentid.name,
        componentid,
        ComponentInstanceId.default(componentid),
        protocol
      )
      component.installSpi(sink)
      val initialized = component.initialize(
        ComponentInit(subsystem, core, ComponentOrigin.Main)
      ).asInstanceOf[EvaluationComponent]
      subsystem.add(initialized)

      try {
        When("the operation returns a JobId and the worker result is awaited")
        val submitted = subsystem.executeOperationResponse(Request.of(
          component = "org.goldenport.cncf.test.EvaluationJob",
          service = "operation",
          operation = "jobAsync"
        ))
        val jobid = submitted.toOption.collect {
          case OperationResponse.Scalar(value) => JobId.parse(value.toString).toOption
        }.flatten.getOrElse(fail(s"JobId missing: $submitted"))
        initialized.logic.awaitJobResult(jobid) shouldBe
          Consequence.success(OperationResponse.Scalar("job-success"))

        Then("start and terminal share one attempt correlated to the worker Job")
        _await_fact_count(sink, 2) shouldBe true
        sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
        val correlations = sink.facts.map(_.correlation)
        correlations.map(_.executionId).distinct.size shouldBe 1
        correlations.map(_.attemptId).distinct.size shouldBe 1
        correlations.flatMap(_.jobId).distinct shouldBe Vector(jobid)
      } finally {
        subsystem.shutdown()
      }
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(result.toString))

  private def _await_fact_count(
    sink: DeterministicCorpusEvaluationSink,
    count: Int
  ): Boolean = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L)
    while (sink.facts.size < count && System.nanoTime() < deadline)
      Thread.sleep(5L)
    sink.facts.size >= count
  }
}
