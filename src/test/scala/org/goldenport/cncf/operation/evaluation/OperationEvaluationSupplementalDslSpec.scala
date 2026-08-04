package org.goldenport.cncf.operation.evaluation

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.collection.mutable.ArrayBuffer
import cats.data.NonEmptyVector
import cats.syntax.flatMap.*
import cats.syntax.functor.*

import org.goldenport.Consequence
import org.goldenport.cncf.action.{
  ActionCall,
  ActionEngine,
  CommandAction,
  CommandExecutionMode,
  FunctionalActionCall,
  ProcedureActionCall,
  QueryAction
}
import org.goldenport.cncf.component.{
  Component,
  ComponentId,
  ComponentInit,
  ComponentInstanceId,
  ComponentOrigin
}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.job.{
  ActionId,
  ActionTask,
  JobCommandMode,
  JobControlCommand,
  JobControlOption,
  JobControlRequest,
  JobId,
  JobResult,
  TaskFailed
}
import org.goldenport.cncf.spi.evaluation.{
  CorpusEvaluationSink,
  CorpusEvaluationSinkSocket,
  DeterministicCorpusEvaluationSink,
  DeterministicExperimentEvaluationSink,
  ExperimentEvaluationSink,
  ExperimentEvaluationSinkSocket
}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.observation.Cause
import org.goldenport.schema.DataConfidentiality
import org.goldenport.cncf.unitofwork.{UnitOfWorkResource, UnitOfWorkTermination}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for transaction-aware supplemental evaluation DSL.
 *
 * @since   Jul. 23, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationSupplementalDslSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterEach {
  private val _subsystems = ArrayBuffer.empty[Subsystem]

  override protected def afterEach(): Unit =
    try {
      _subsystems.foreach(_.shutdown())
      _subsystems.clear()
    } finally {
      super.afterEach()
    }

  "Supplemental operation evaluation DSL" should {
    "publish application evidence only after canonical success" which {
      "release a FunctionalActionCall corpus candidate after the terminal fact" in {
        Given("a FunctionalActionCall and an installed deterministic Corpus sink")
        val fixture = _corpus_fixture()

        When("the operation stages a bounded corpus candidate and succeeds")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusFunctional")
        )

        Then("the business result is unchanged and the candidate follows the terminal fact")
        result shouldBe Consequence.success(OperationResponse.Scalar("corpus-functional"))
        fixture.sink.facts.map(_.factKind.token) shouldBe
          Vector("operation-start", "operation-terminal", "corpus-candidate")
        val candidate = fixture.sink.facts.last.asInstanceOf[CorpusCandidateFact]
        candidate.source shouldBe OperationEvaluationFactSource.Application
        candidate.summary.map(_.print) shouldBe Some("accepted training example")
        candidate.labels.map(_.toRecord.getString("name")) shouldBe Vector(Some("quality"))
        candidate.correlation.attemptId shouldBe fixture.sink.facts.head.correlation.attemptId
        fixture.commitstates.get() shouldBe Vector(true)
      }

      "release a ProcedureActionCall experiment observation with bounded measurements" in {
        Given("a ProcedureActionCall and an installed deterministic Experiment sink")
        val fixture = _experiment_fixture()

        When("the operation stages one measurement and one label")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "experimentProcedure")
        )

        Then("the observation is application-owned and shares the automatic correlation")
        result shouldBe Consequence.success(OperationResponse.Scalar("experiment-procedure"))
        fixture.sink.facts.map(_.factKind.token) shouldBe
          Vector("operation-start", "operation-terminal", "experiment-observation")
        val observation = fixture.sink.facts.last.asInstanceOf[ExperimentObservationFact]
        observation.source shouldBe OperationEvaluationFactSource.Application
        observation.measurements.map(_.toRecord.getString("name")) shouldBe Vector(Some("score"))
        observation.labels.map(_.toRecord.getString("name")) shouldBe Vector(Some("variant"))
        observation.correlation.executionId shouldBe fixture.sink.facts.head.correlation.executionId
      }
    }

    "isolate evidence by operation attempt within a shared UnitOfWork" which {
      "release candidates from every Task in one multi-task Job" in {
        Given("two captured operation Tasks that share one Job execution context")
        val fixture = _corpus_fixture()
        val context = fixture.component.logic.executionContext()
        val first = _prepared_task(fixture, "corpusProcedure", context)
        val second = _prepared_task(fixture, "corpusProcedure", context)

        When("JobEngine executes and settles both Tasks")
        val jobid = _success(
          fixture.component.jobEngine.submit(List(first._1, second._1), first._2)
        )
        val result = fixture.component.jobEngine.awaitResult(jobid, 3000L)

        Then("each attempt releases its own candidate after its own terminal fact")
        result shouldBe Consequence.success(
          JobResult.Success(OperationResponse.Scalar("corpus-procedure"))
        )
        _await_fact_count(fixture.sink, 6) shouldBe true
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
          "operation-start",
          "operation-start",
          "operation-terminal",
          "corpus-candidate",
          "operation-terminal",
          "corpus-candidate"
        )
        fixture.sink.facts.collect {
          case candidate: CorpusCandidateFact => candidate.correlation.attemptId
        }.distinct should have size 2
      }

      "discard a failed attempt and admit supplemental evidence on Job retry" in {
        Given("a Job operation that fails its first attempt after staging and succeeds on retry")
        val attempts = new AtomicInteger(0)
        val operation = SupplementalRetryOperation("corpusRetry", attempts)
        val fixture = _corpus_fixture(Vector(operation))

        When("the failed Job is retried")
        val submitted = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusRetry")
        )
        val jobid = _job_id(submitted)
        fixture.component.jobEngine.awaitResult(jobid, 3000L) shouldBe a[Consequence.Success[_]]
        given ExecutionContext =
          ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
        fixture.component.jobEngine.control(
          jobid,
          JobControlRequest(JobControlCommand.Retry, JobControlOption(mode = JobCommandMode.Async))
        ) shouldBe a[Consequence.Success[_]]
        val retried = fixture.component.jobEngine.awaitResult(jobid, 3000L)

        Then("only the successful retry publishes its candidate under a new attempt identity")
        retried shouldBe Consequence.success(
          JobResult.Success(OperationResponse.Scalar("retry-success"))
        )
        _await_fact_count(fixture.sink, 5) shouldBe true
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
          "operation-start",
          "operation-terminal",
          "operation-start",
          "operation-terminal",
          "corpus-candidate"
        )
        fixture.sink.facts.collect {
          case terminal: OperationEvaluationTerminalFact => terminal.outcome
        } shouldBe Vector(
          OperationEvaluationOutcome.Failure,
          OperationEvaluationOutcome.Success
        )
        fixture.sink.facts.map(_.id).distinct should have size 5
        val automatic = fixture.sink.facts.collect {
          case fact: OperationEvaluationStartFact => fact.correlation.attemptId -> fact.factKind.token
          case fact: OperationEvaluationTerminalFact => fact.correlation.attemptId -> fact.factKind.token
        }
        automatic.groupMap(_._1)(_._2).values.toVector should contain only (
          Vector("operation-start", "operation-terminal")
        )
        automatic.map(_._1).distinct should have size 2
        fixture.sink.facts.collect {
          case candidate: CorpusCandidateFact => candidate.correlation.attemptId
        } shouldBe Vector(automatic.last._1)
      }

      "keep nested same-context operation evidence separate from its caller" in {
        Given("an outer operation that stages evidence and invokes another captured operation")
        val fixture = _corpus_fixture()

        When("both operations complete through the same execution context")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusNested")
        )

        Then("the child and parent candidates are released by their own canonical outcomes")
        result shouldBe Consequence.success(OperationResponse.Scalar("nested-success"))
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
          "operation-start",
          "operation-start",
          "operation-terminal",
          "corpus-candidate",
          "operation-terminal",
          "corpus-candidate",
          "corpus-candidate"
        )
        val candidates = fixture.sink.facts.collect {
          case candidate: CorpusCandidateFact =>
            candidate.summary.map(_.print) -> candidate.correlation.attemptId
        }
        candidates.map(_._1) shouldBe Vector(
          Some("procedure candidate"),
          Some("nested outer candidate"),
          Some("nested outer candidate after child")
        )
        candidates.map(_._2).distinct should have size 2
      }
    }

    "discard evidence when the canonical operation does not succeed" which {
      "discard a staged candidate when the ActionCall returns a structured failure" in {
        Given("an operation that stages a candidate before returning a failure")
        val fixture = _corpus_fixture()

        When("the ActionCall aborts")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusFailure")
        )

        Then("only automatic failure evidence reaches the sink")
        result shouldBe a[Consequence.Failure[_]]
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
        fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
          OperationEvaluationOutcome.Failure
      }

      "discard a committed candidate when framework response binding fails" in {
        Given("a successful staged candidate and a failing framework response binding")
        val fixture = _corpus_fixture()
        val runtime = new OperationEvaluationDeliveryRuntime()
        try {
          val operation = _success(OperationEvaluationOperationIdentity.createC(
            fixture.component.name,
            "operation",
            "corpusProcedure"
          ))
          val prepared = _success(ExecutionContext.prepareOperationEvaluation(
            fixture.component.logic.executionContext(),
            operation
          ))
          val action = SupplementalQueryAction(
            _request(fixture.component.name, "corpusProcedure"),
            SupplementalMode.CorpusProcedure
          )
          val task = ActionTask(
            ActionId.generate(),
            action,
            ActionEngine.create(),
            Some(fixture.component)
          )
          val evaluated = new OperationEvaluationActionTask(
            task,
            new OperationEvaluationAttemptCapture(operation, runtime),
            (_, _) => Consequence.argumentInvalid("planned supplemental binding failure")
          )

          When("business execution commits before the binding fails")
          val outcome = evaluated.run(prepared)

          Then("the candidate remains discarded")
          outcome shouldBe a[TaskFailed]
          fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
        } finally {
          runtime.close()
        }
      }

      "discard a candidate when UnitOfWork commit fails" in {
        Given("an operation whose post-commit callback fails after staging")
        val fixture = _corpus_fixture()

        When("the UnitOfWork reports commit failure")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusCommitFailure")
        )

        Then("the commit failure is canonical and no supplemental provider call occurs")
        result shouldBe a[Consequence.Failure[_]]
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
        fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
          OperationEvaluationOutcome.Failure
      }

      "retain cleanup diagnostics when an ActionCall throws during abort" in {
        Given("an ActionCall that registers a failing abort resource before throwing")
        val fixture = _corpus_fixture()

        When("ActionEngine aborts the failed call")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusThrowCleanup")
        )

        Then("the structured failure retains both primary and cleanup evidence")
        result shouldBe a[Consequence.Failure[_]]
        val conclusion = result match {
          case Consequence.Failure(value) => value
          case Consequence.Success(response) => fail(s"failure expected: $response")
        }
        conclusion.causes.exists(_.display.contains("planned thrown primary failure")) shouldBe true
        conclusion.causes.exists(_.display.contains("planned abort cleanup failure")) shouldBe true
      }

      "preserve interruption and fatal control flow after abort cleanup" in {
        Given("operations that raise interruption and a fatal linkage error")
        val fixture = _corpus_fixture()

        When("ActionEngine aborts each operation")
        val interrupted = intercept[InterruptedException] {
          fixture.subsystem.executeOperationResponse(
            _request(fixture.component.name, "corpusInterrupted")
          )
        }
        val interruptstatus = Thread.currentThread.isInterrupted
        val _ = Thread.interrupted()
        val fatal = intercept[LinkageError] {
          fixture.subsystem.executeOperationResponse(
            _request(fixture.component.name, "corpusFatal")
          )
        }

        Then("both control-flow exceptions propagate and interruption remains visible")
        interrupted.getMessage shouldBe "planned interruption"
        interruptstatus shouldBe true
        fatal.getMessage shouldBe "planned fatal linkage failure"
      }

      "discard a candidate when the canonical failure is a timeout" in {
        Given("an operation that stages a candidate before returning a structured timeout")
        val fixture = _corpus_fixture()

        When("the timeout settles the operation")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusTimeout")
        )

        Then("only automatic timeout evidence reaches the sink")
        result shouldBe a[Consequence.Failure[_]]
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
        fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
          OperationEvaluationOutcome.Timeout
      }

      "retain a Job candidate until settlement and discard it on cancellation" in {
        Given("a JobAsync operation that stages a candidate before blocking")
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)
        val operation = SupplementalBlockingOperation("corpusCancelable", entered, release)
        val fixture = _corpus_fixture(Vector(operation))

        When("the Job is cancelled after staging but before canonical settlement")
        val submitted = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusCancelable")
        )
        val jobid = _job_id(submitted)
        entered.await(3L, TimeUnit.SECONDS) shouldBe true
        given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
        try {
          fixture.component.jobEngine.control(
            jobid,
            JobControlRequest(JobControlCommand.Cancel, JobControlOption(mode = JobCommandMode.Async))
          ) shouldBe a[Consequence.Success[_]]
          release.countDown()

          Then("the cancellation terminal is emitted without the staged candidate")
          _await_fact_count(fixture.sink, 2) shouldBe true
          fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
          fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
            OperationEvaluationOutcome.Cancellation
        } finally {
          release.countDown()
        }
      }
    }

    "enforce bounded evidence at the UnitOfWork boundary" which {
      "reject confidentiality above the default evidence policy" in {
        Given("an operation that marks a supplemental candidate as secret")
        val fixture = _corpus_fixture()

        When("the DSL stages the candidate")
        val result = fixture.subsystem.executeOperationResponse(
          _request(fixture.component.name, "corpusSecret")
        )

        Then("a structured policy failure aborts the operation without publishing the candidate")
        result shouldBe a[Consequence.Failure[_]]
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      }

      "reject count and byte overflow without mutating committed evidence" in {
        Given("one active attempt and a deliberately narrow supplemental buffer")
        val fixture = _corpus_fixture()
        val context = _attempt_context(fixture.component)
        val first = _candidate_intent(context, "first")
        val second = _candidate_intent(context, "second", Some("count-second"))
        val countbuffer = new OperationEvaluationSupplementalBuffer(
          OperationEvaluationSupplementalPolicy(
            maximumIntentCount = 1,
            maximumIntentBytes = 64 * 1024,
            maximumAggregateBytes = 64 * 1024
          )
        )
        val bytebuffer = new OperationEvaluationSupplementalBuffer(
          OperationEvaluationSupplementalPolicy(
            maximumIntentCount = 4,
            maximumIntentBytes = 256,
            maximumAggregateBytes = 256
          )
        )

        When("a second intent and an oversized serialized intent are staged")
        countbuffer.stageC(first) shouldBe Consequence.unit
        val countresult = countbuffer.stageC(second)
        val byteresult = bytebuffer.stageC(_candidate_intent(context, "x" * 200))

        Then("both violations remain structured argument failures")
        countresult shouldBe a[Consequence.Failure[_]]
        byteresult shouldBe a[Consequence.Failure[_]]
      }

      "partition bounded state for generated independent attempts" in {
        Given("a generated number of operation attempts sharing one supplemental buffer")
        val fixture = _corpus_fixture()
        val property = Prop.forAll(Gen.choose(2, 12)) { attemptcount =>
          val buffer = new OperationEvaluationSupplementalBuffer(
            OperationEvaluationSupplementalPolicy(
              maximumIntentCount = 1,
              maximumIntentBytes = 64 * 1024,
              maximumAggregateBytes = 64 * 1024
            )
          )
          val context = _attempt_context(fixture.component)
          val intents = Vector.tabulate(attemptcount) { index =>
            _candidate_intent(
              context,
              s"attempt-$index",
              Some(s"property-$index")
            )
          }

          intents.map(_.fact.correlation.attemptId).distinct.size == attemptcount &&
          intents.forall { intent =>
            val attemptid = intent.fact.correlation.attemptId
            val staged = buffer.stageC(intent).isSuccess
            buffer.markCommitted(attemptid)
            staged && buffer.releaseCommitted(attemptid) == Vector(intent)
          }
        }

        When("the generated lifecycle is checked")
        val checked = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(32),
          property
        )

        Then("each attempt independently admits, commits, and releases one bounded intent")
        checked.passed shouldBe true
      }
    }
  }

  private final case class CorpusFixture(
    subsystem: Subsystem,
    sink: DeterministicCorpusEvaluationSink,
    component: SupplementalCorpusComponent,
    commitstates: AtomicReference[Vector[Boolean]]
  )

  private final case class ExperimentFixture(
    subsystem: Subsystem,
    sink: DeterministicExperimentEvaluationSink,
    component: SupplementalExperimentComponent
  )

  private def _corpus_fixture(
    additionaloperations: Vector[spec.OperationDefinition] = Vector.empty
  ): CorpusFixture = {
    val subsystem = _track(TestComponentFactory.admittedEmptySubsystem("evaluation-supplemental-corpus"))
    val sink = _success(DeterministicCorpusEvaluationSink.createC("evaluation_corpus", "test-corpus"))
    val commitstates = new AtomicReference(Vector.empty[Boolean])
    val observingsink = CommitObservingCorpusEvaluationSink(sink, commitstates)
    val component = _initialize_component(
      subsystem,
      new SupplementalCorpusComponent,
      "evaluation_corpus",
      Vector(
        SupplementalOperation("corpusFunctional", SupplementalMode.CorpusFunctional),
        SupplementalOperation("corpusProcedure", SupplementalMode.CorpusProcedure),
        SupplementalOperation("corpusFailure", SupplementalMode.CorpusFailure),
        SupplementalOperation("corpusCommitFailure", SupplementalMode.CorpusCommitFailure),
        SupplementalOperation("corpusSecret", SupplementalMode.CorpusSecret),
        SupplementalOperation("corpusTimeout", SupplementalMode.CorpusTimeout),
        SupplementalOperation("corpusNested", SupplementalMode.CorpusNested),
        SupplementalOperation("corpusThrowCleanup", SupplementalMode.CorpusThrowCleanup),
        SupplementalOperation("corpusInterrupted", SupplementalMode.CorpusInterrupted),
        SupplementalOperation("corpusFatal", SupplementalMode.CorpusFatal)
      ) ++ additionaloperations
    )
    component.installSpi(observingsink)
    subsystem.add(component)
    CorpusFixture(subsystem, sink, component, commitstates)
  }

  private def _experiment_fixture(): ExperimentFixture = {
    val subsystem = _track(TestComponentFactory.admittedEmptySubsystem("evaluation-supplemental-experiment"))
    val sink = _success(DeterministicExperimentEvaluationSink.createC(
      "evaluation_experiment",
      "test-experiment"
    ))
    val component = _initialize_component(
      subsystem,
      new SupplementalExperimentComponent,
      "evaluation_experiment",
      Vector(SupplementalOperation("experimentProcedure", SupplementalMode.ExperimentProcedure))
    )
    component.installSpi(sink)
    subsystem.add(component)
    ExperimentFixture(subsystem, sink, component)
  }

  private def _initialize_component[C <: Component](
    subsystem: Subsystem,
    component: C,
    componentname: String,
    operations: Vector[spec.OperationDefinition]
  ): C = {
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(
      spec.ServiceDefinition(
        "operation",
        spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations))
      )
    )))
    val componentid = ComponentId(componentname)
    val core = Component.Core.create(
      componentname,
      componentid,
      ComponentInstanceId.default(componentid),
      protocol
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Main)).asInstanceOf[C]
  }

  private def _request(component: String, operation: String): Request =
    Request.of(component = component, service = "operation", operation = operation)

  private def _prepared_task(
    fixture: CorpusFixture,
    operation: String,
    context: ExecutionContext
  ) = {
    val action = SupplementalQueryAction(
      _request(fixture.component.name, operation),
      SupplementalMode.CorpusProcedure
    )
    val task = ActionTask(
      ActionId.generate(),
      action,
      fixture.component.actionEngine,
      Some(fixture.component)
    )
    _success(fixture.subsystem._prepare_operation_task(action, task, context))
  }

  private def _job_id(
    response: Consequence[OperationResponse]
  ): JobId =
    response.toOption.collect {
      case OperationResponse.Scalar(value) => JobId.parse(value.toString).toOption
    }.flatten.getOrElse(fail(s"JobId missing: $response"))

  private def _attempt_context(component: Component): ExecutionContext = {
    val operation = _success(OperationEvaluationOperationIdentity.createC(
      component.name,
      "operation",
      "buffer"
    ))
    val prepared = _success(ExecutionContext.prepareOperationEvaluation(
      component.logic.executionContext(),
      operation
    ))
    _success(ExecutionContext.beginOperationEvaluationAttempt(prepared))
  }

  private def _candidate_intent(
    context: ExecutionContext,
    summary: String,
    attempttoken: Option[String] = None
  ): OperationEvaluationSupplementalIntent = {
    val occurredat = context.clock.instant()
    val basecorrelation =
      context.operationEvaluation.correlation.getOrElse(fail("correlation missing"))
    val correlation = attempttoken.fold(basecorrelation) { token =>
      basecorrelation.copy(
        attemptId = basecorrelation.attemptId.copy(entropy = Some(token))
      )
    }
    val text = _success(OperationEvaluationText.parseC(summary))
    val fact = _success(CorpusCandidateFact.createC(
      OperationEvaluationFactId.create("buffer", occurredat, context.idGeneration),
      correlation,
      occurredat,
      Some(text)
    ))
    OperationEvaluationSupplementalIntent(
      OperationEvaluationIntentId.create("buffer", occurredat, context.idGeneration),
      fact,
      occurredat
    )
  }

  private def _track(subsystem: Subsystem): Subsystem = {
    _subsystems += subsystem
    subsystem
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

private enum SupplementalMode {
  case CorpusFunctional
  case CorpusProcedure
  case CorpusFailure
  case CorpusCommitFailure
  case CorpusSecret
  case CorpusTimeout
  case CorpusNested
  case CorpusThrowCleanup
  case CorpusInterrupted
  case CorpusFatal
  case ExperimentProcedure
}

private final class SupplementalCorpusComponent
    extends Component
    with CorpusEvaluationSinkSocket

private final class SupplementalExperimentComponent
    extends Component
    with ExperimentEvaluationSinkSocket

private final case class CommitObservingCorpusEvaluationSink(
  underlying: CorpusEvaluationSink,
  commitstates: AtomicReference[Vector[Boolean]]
) extends CorpusEvaluationSink {
  override val sinkIdentityOption = underlying.sinkIdentityOption

  def recordStart(
    fact: OperationEvaluationStartFact
  )(using context: ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    underlying.recordStart(fact)

  def recordTerminal(
    fact: OperationEvaluationTerminalFact
  )(using context: ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    underlying.recordTerminal(fact)

  def submitCandidate(
    fact: CorpusCandidateFact
  )(using context: ExecutionContext): Consequence[OperationEvaluationDeliveryResult] = {
    val committed = context.runtime.unitOfWork.lastCommitResult.exists(_.isSuccess)
    commitstates.updateAndGet(_ :+ committed)
    underlying.submitCandidate(fact)
  }
}

private final case class SupplementalOperation(
  operationname: String,
  mode: SupplementalMode
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.success(SupplementalQueryAction(request, mode))
}

private final case class SupplementalBlockingOperation(
  operationname: String,
  entered: CountDownLatch,
  release: CountDownLatch
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.success(SupplementalBlockingAction(request, entered, release))
}

private final case class SupplementalRetryOperation(
  operationname: String,
  attempts: AtomicInteger
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.success(SupplementalRetryAction(request, attempts))
}

private final case class SupplementalQueryAction(
  request: Request,
  mode: SupplementalMode
) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall =
    mode match {
      case SupplementalMode.CorpusFunctional =>
        SupplementalFunctionalActionCall(core)
      case _ =>
        SupplementalProcedureActionCall(core, mode)
    }
}

private final case class SupplementalBlockingAction(
  request: Request,
  entered: CountDownLatch,
  release: CountDownLatch
) extends CommandAction {
  override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.JobAsync

  def createCall(core: ActionCall.Core): ActionCall =
    SupplementalBlockingActionCall(core, entered, release)
}

private final case class SupplementalRetryAction(
  request: Request,
  attempts: AtomicInteger
) extends CommandAction {
  override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.JobAsync

  def createCall(core: ActionCall.Core): ActionCall =
    SupplementalRetryActionCall(core, attempts)
}

private final case class SupplementalFunctionalActionCall(
  core: ActionCall.Core
) extends FunctionalActionCall {
  protected def build_Program = for {
    label <- operation_evaluation_label("quality", "accepted")
    _ <- corpus_candidate(
      summary = Some("accepted training example"),
      labels = Vector(label)
    )
  } yield OperationResponse.Scalar("corpus-functional")
}

private final case class SupplementalProcedureActionCall(
  core: ActionCall.Core,
  mode: SupplementalMode
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    mode match {
      case SupplementalMode.CorpusProcedure =>
        executeProgram(for {
          _ <- corpus_candidate(summary = Some("procedure candidate"))
        } yield OperationResponse.Scalar("corpus-procedure"))
      case SupplementalMode.CorpusFailure =>
        executeProgram(for {
          _ <- corpus_candidate(summary = Some("must be discarded"))
          _ <- exec_from[Unit](Consequence.argumentInvalid("planned supplemental failure"))
        } yield OperationResponse.Scalar("unreachable"))
      case SupplementalMode.CorpusCommitFailure =>
        execution_context.runtime.unitOfWork.stagePostCommit(
          throw new IllegalStateException("planned supplemental commit failure")
        )
        executeProgram(for {
          _ <- corpus_candidate(summary = Some("must not survive commit failure"))
        } yield OperationResponse.Scalar("unreachable"))
      case SupplementalMode.CorpusSecret =>
        executeProgram(for {
          _ <- corpus_candidate(
            summary = Some("secret evidence"),
            confidentiality = DataConfidentiality.Secret
          )
        } yield OperationResponse.Scalar("unreachable"))
      case SupplementalMode.CorpusTimeout =>
        executeProgram(for {
          _ <- corpus_candidate(summary = Some("must not survive timeout"))
          _ <- exec_from[Unit](Consequence.serviceUnavailable(
            "planned supplemental timeout",
            Cause.Kind.Timeout,
            Seq.empty
          ))
        } yield OperationResponse.Scalar("unreachable"))
      case SupplementalMode.CorpusNested =>
        executeProgram(for {
          _ <- corpus_candidate(summary = Some("nested outer candidate"))
        } yield ()).flatMap { _ =>
          val nested = component match {
            case Some(owner) =>
              owner.subsystem
                .map(_.executeOperationResponseInContext(
                  Request.of(
                    component = owner.name,
                    service = "operation",
                    operation = "corpusProcedure"
                  ),
                  execution_context
                ))
                .getOrElse(Consequence.serviceUnavailable("nested subsystem unavailable"))
            case None =>
              Consequence.serviceUnavailable("nested component unavailable")
          }
          nested.flatMap { _ =>
            executeProgram(for {
              _ <- corpus_candidate(summary = Some("nested outer candidate after child"))
            } yield OperationResponse.Scalar("nested-success"))
          }
        }
      case SupplementalMode.CorpusThrowCleanup =>
        val _ = execution_context.runtime.unitOfWork.registerResourceC(
          SupplementalFailingAbortResource
        )
        throw new IllegalStateException("planned thrown primary failure")
      case SupplementalMode.CorpusInterrupted =>
        throw new InterruptedException("planned interruption")
      case SupplementalMode.CorpusFatal =>
        throw new LinkageError("planned fatal linkage failure")
      case SupplementalMode.ExperimentProcedure =>
        executeProgram(for {
          measurement <- operation_evaluation_measurement("score", BigDecimal("0.875"), Some("ratio"))
          label <- operation_evaluation_label("variant", "candidate")
          _ <- experiment_observation(Vector(measurement), Vector(label))
        } yield OperationResponse.Scalar("experiment-procedure"))
      case SupplementalMode.CorpusFunctional =>
        Consequence.stateInvalid("functional supplemental mode reached procedure call")
    }
}

private final case class SupplementalRetryActionCall(
  core: ActionCall.Core,
  attempts: AtomicInteger
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    executeProgram(for {
      _ <- corpus_candidate(summary = Some("retry candidate"))
    } yield ()).flatMap { _ =>
      if (attempts.getAndIncrement() == 0)
        Consequence.stateInvalid("planned retry failure")
      else
        Consequence.success(OperationResponse.Scalar("retry-success"))
    }
}

private final case class SupplementalBlockingActionCall(
  core: ActionCall.Core,
  entered: CountDownLatch,
  release: CountDownLatch
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    executeProgram(for {
      _ <- corpus_candidate(summary = Some("must not survive cancellation"))
    } yield ()).flatMap { _ =>
      entered.countDown()
      release.await()
      Consequence.success(OperationResponse.Scalar("cancelled-after-stage"))
    }
}

private object SupplementalFailingAbortResource extends UnitOfWorkResource {
  def releaseC(termination: UnitOfWorkTermination): Consequence[Unit] =
    termination match {
      case UnitOfWorkTermination.Aborted =>
        Consequence.stateInvalid("planned abort cleanup failure")
      case _ =>
        Consequence.unit
    }
}
