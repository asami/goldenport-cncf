package org.goldenport.cncf.observability

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.observation.{Cause, Descriptor}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private object DslChokepointPreservingOutcomeSpec {
  enum Boundary { case Run, Phase }
  enum BodyKind { case Success, Failure }
  final class FatalSignal extends VirtualMachineError("owned harmless sentinel")
  final class LinkageSignal extends LinkageError("owned harmless sentinel")
  final class ControlSignal extends scala.util.control.ControlThrowable
  final case class Event(id: Int, entering: Boolean, context: DslChokepointContext,
    phase: Option[DslChokepointPhase], outcome: Option[DslChokepointOutcome])
  final case class Sample(order: Vector[Int], enterfault: Option[Int], leavefault: Option[Int],
    body: BodyKind, boundary: Boundary)
  final class Log {
    private var _events = Vector.empty[Event]
    def events: Vector[Event] = _events
    def append(event: Event): Unit = { _events :+= event }
  }
  final class Hook(id: Int, log: Log, enterfault: Option[Throwable] = None,
    leavefault: Option[Throwable] = None) extends DslChokepointHook {
    private def _enter(context: DslChokepointContext, phase: Option[DslChokepointPhase]): Unit = {
      log.append(Event(id, true, context, phase, None))
      enterfault.foreach(signal => throw signal)
    }
    private def _leave(context: DslChokepointContext, phase: Option[DslChokepointPhase], outcome: DslChokepointOutcome): Unit = {
      log.append(Event(id, false, context, phase, Some(outcome)))
      leavefault.foreach(signal => throw signal)
    }
    override def enter(ctx: DslChokepointContext)(using ExecutionContext): Unit = _enter(ctx, None)
    override def leave(ctx: DslChokepointContext, outcome: DslChokepointOutcome)(using ExecutionContext): Unit = _leave(ctx, None, outcome)
    override def phaseEnter(ctx: DslChokepointContext, phase: DslChokepointPhase)(using ExecutionContext): Unit = _enter(ctx, Some(phase))
    override def phaseLeave(ctx: DslChokepointContext, phase: DslChokepointPhase,
      outcome: DslChokepointOutcome)(using ExecutionContext): Unit = _leave(ctx, Some(phase), outcome)
  }
  val samples: Gen[Sample] = for {
    size <- Gen.chooseNum(0, 8)
    order <- Gen.pick(size, (0 until 8).toVector).map(_.toVector)
    entering <- if (size == 0) Gen.const(None) else Gen.option(Gen.chooseNum(0, size - 1))
    leaving <- if (size == 0) Gen.const(None) else Gen.option(Gen.chooseNum(0, size - 1))
    body <- Gen.oneOf(BodyKind.values.toVector)
    boundary <- Gen.oneOf(Boundary.values.toVector)
  } yield Sample(order, entering, leaving, body, boundary)
  given Shrink[Sample] = Shrink { sample =>
    val smaller = if (sample.order.isEmpty) Stream.empty else {
      val size = sample.order.size / 2
      Stream(sample.copy(order = sample.order.take(size),
        enterfault = if (size == 0) None else sample.enterfault.map(math.min(_, size - 1)),
        leavefault = if (size == 0) None else sample.leavefault.map(math.min(_, size - 1))))
    }
    val faults = sample.enterfault.toStream.map(_ => sample.copy(enterfault = None)) #:::
      sample.leavefault.toStream.map(_ => sample.copy(leavefault = None))
    val body = if (sample.body == BodyKind.Failure) Stream(sample.copy(body = BodyKind.Success)) else Stream.empty
    smaller #::: faults #::: body
  }
}

final class DslChokepointPreservingOutcomeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DslChokepointPreservingOutcomeSpec.*
  import DslChokepointPreservingOutcomeSpec.given

  private val _context = DslChokepointContext("owned", "preserving")
  private val _phase = DslChokepointPhase.Persistence
  private val _failure = Consequence.operationInvalid("owned-body", Cause.Kind.Inconsistency,
    Vector(Descriptor.Facet.Reason("owned-body-rejected")))
  private def _invoke[A](boundary: Boundary, hooks: Option[Vector[DslChokepointHook]])
    (body: => Consequence[A])(using ExecutionContext): Consequence[A] = boundary match {
    case Boundary.Run => DslChokepointRunner._run_preserving_c(_context, hooks)(body)
    case Boundary.Phase => DslChokepointRunner._phase_preserving_c(_context, _phase, hooks)(body)
  }
  private def _trace(boundary: Boundary, entered: Vector[Int], outcome: DslChokepointOutcome): Vector[Event] = {
    val phase = if (boundary == Boundary.Run) None else Some(_phase)
    entered.map(id => Event(id, true, _context, phase, None)) ++
      entered.reverse.map(id => Event(id, false, _context, phase, Some(outcome)))
  }
  private def _signals: Vector[Throwable] = Vector(new FatalSignal, new LinkageSignal,
    new ControlSignal, new InterruptedException("owned harmless sentinel"))
  private def _caught(body: => Any): Throwable = {
    try { body; fail("the original boundary signal must escape") }
    catch { case signal: Throwable => signal }
  }
  private def _check_conclusion(actual: Conclusion, expected: Conclusion, source: Boolean): Unit = {
    actual.status shouldBe expected.status
    actual.interpretation shouldBe expected.interpretation
    actual.disposition shouldBe expected.disposition
    actual.previous shouldBe expected.previous
    actual.observation.timestamp should not be null
    val facets = actual.observation.cause.descriptor.facets
    val expectedfacets = expected.observation.cause.descriptor.facets
    val compared = if (source) {
      val positions = facets.zipWithIndex.collect { case (pos: Descriptor.Facet.SrcPos, index) => pos -> index }
      val expectedpositions = expectedfacets.zipWithIndex.collect { case (pos: Descriptor.Facet.SrcPos, index) => pos -> index }
      positions should have size 1
      expectedpositions should have size 1
      positions.head._1.pos.file.endsWith("DslChokepoint.scala") shouldBe true
      positions.head._1.pos.line should be > 0
      positions.head._1.pos.column should be > 0
      positions.head._2 shouldBe expectedpositions.head._2
      facets.updated(positions.head._2, expectedpositions.head._1)
    } else facets
    actual.observation.copy(timestamp = expected.observation.timestamp,
      cause = actual.observation.cause.copy(descriptor = actual.observation.cause.descriptor.copy(facets = compared))) shouldBe expected.observation
  }

  "Outcome-preserving DSL boundaries" should {
    "retain body results and deterministic hook order" which {
      Boundary.values.foreach { boundary =>
        s"DSL-PRESERVE-01 preserve exact success and real Failure in $boundary" in {
          Vector[Consequence[String]](Consequence.success("known result"), _failure).foreach { wanted =>
            Given("three ordered actual hooks and one already constructed body result")
            given ExecutionContext = ExecutionContext.create()
            val log = new Log
            val hooks = Vector(2, 0, 1).map(id => new Hook(id, log))
            var calls = 0
            When("the opt-in boundary evaluates that body once")
            val actual = _invoke(boundary, Some(hooks)) { calls += 1; wanted }
            Then("the exact Consequence survives and each hook is released once in reverse with its real outcome")
            (actual eq wanted) shouldBe true
            calls shouldBe 1
            log.events shouldBe _trace(boundary, Vector(2, 0, 1),
              if (wanted.isSuccess) DslChokepointOutcome.Success else DslChokepointOutcome.Failure)
          }
        }
        s"DSL-PRESERVE-01 contain malformed outcomes and ordinary body exceptions in $boundary" in {
          for (malformed <- 0 to 2) {
            Given("an isolated boundary and a deliberately malformed callback result")
            given ExecutionContext = ExecutionContext.create()
            val log = new Log
            val wanted = Consequence.operationInvalid("dsl-chokepoint", Cause.Kind.Inconsistency,
              Vector(Descriptor.Facet.Reason("dsl-chokepoint-outcome-invalid")), None)
            When("the malformed body is evaluated through the actual opt-in runner")
            val actual = _invoke[String](boundary, Some(Vector(new Hook(0, log)))) {
              malformed match {
                case 0 => null
                case 1 => Consequence.Success(null)
                case 2 => Consequence.Failure(null)
              }
            }
            Then("a complete redacted structured boundary failure is supplied to reverse cleanup")
            _check_conclusion(actual.asInstanceOf[Consequence.Failure[?]].conclusion, wanted.conclusion, true)
            log.events shouldBe _trace(boundary, Vector(0), DslChokepointOutcome.Failure)
          }
          Given("a normal NonFatal body exception with the existing Conclusion mapping")
          given ExecutionContext = ExecutionContext.create()
          val error = new IllegalStateException("owned ordinary body error")
          When("the body throws through the opt-in boundary")
          val actual = _invoke[String](boundary, Some(Vector.empty)) { throw error }
          Then("the existing Conclusion.from exception taxonomy is retained")
          _check_conclusion(actual.asInstanceOf[Consequence.Failure[?]].conclusion, Conclusion.from(error), false)
        }
        s"DSL-PRESERVE-03 ignore every NonFatal hook failure in $boundary" in {
          for (entering <- Vector(false, true); position <- 0 to 2; rejected <- Vector(false, true)) {
            Given("one selected throwing telemetry callback among three ordered hooks")
            given ExecutionContext = ExecutionContext.create()
            val log = new Log
            val error = new IllegalStateException("owned telemetry failure")
            val hooks = (0 to 2).toVector.map(id => new Hook(id, log,
              if (entering && id == position) Some(error) else None,
              if (!entering && id == position) Some(error) else None))
            val wanted: Consequence[String] = if (rejected) _failure else Consequence.success("retained")
            When("the body completes despite the selected telemetry failure")
            val actual = _invoke(boundary, Some(hooks))(wanted)
            Then("all remaining hooks run and the exact domain result survives")
            (actual eq wanted) shouldBe true
            log.events shouldBe _trace(boundary, Vector(0, 1, 2),
              if (rejected) DslChokepointOutcome.Failure else DslChokepointOutcome.Success)
          }
        }
      }
      "DSL-PRESERVE-01 retain the same Failure through nested phase and outer run" in {
        Given("actual nested opt-in boundaries sharing a genuine structured Failure")
        given ExecutionContext = ExecutionContext.create()
        val log = new Log
        val hooks = Some(Vector(new Hook(0, log), new Hook(1, log)))
        When("the phase Failure flows through its enclosing runner")
        val actual = DslChokepointRunner._run_preserving_c(_context, hooks) {
          DslChokepointRunner._phase_preserving_c(_context, _phase, hooks)(_failure)
        }
        Then("both boundaries release correctly without reconstructing the Failure")
        (actual eq _failure) shouldBe true
        val outer = _trace(Boundary.Run, Vector(0, 1), DslChokepointOutcome.Failure)
        log.events shouldBe outer.take(2) ++ _trace(Boundary.Phase, Vector(0, 1), DslChokepointOutcome.Failure) ++ outer.drop(2)
      }
    }

    "propagate fatal, control and interruption signals after balanced cleanup" which {
      Boundary.values.foreach { boundary =>
        s"DSL-PRESERVE-02 preserve first signal identity from enter, body and leave in $boundary" in {
          for (signal <- _signals; origin <- Vector("enter", "body", "leave")) {
            Given("three hooks, an exact harmless signal and distinct fatal cleanup signals")
            given ExecutionContext = ExecutionContext.create()
            val log = new Log
            val cleanup = new LinkageSignal
            val hooks = (0 to 2).toVector.map(id => new Hook(id, log,
              if (origin == "enter" && id == 1) Some(signal) else None,
              if (origin == "leave" && id == 2) Some(signal) else if (id == 0) Some(cleanup) else None))
            val interrupted = Thread.currentThread().isInterrupted
            val suppressed = signal.getSuppressed.toVector
            var calls = 0
            When("the actual boundary reaches the selected non-NonFatal signal")
            val escaped = _caught {
              _invoke(boundary, Some(hooks)) { calls += 1; if (origin == "body") throw signal; Consequence.success("known") }
            }
            Then("the first signal escapes unchanged after every attempted hook is released exactly once")
            (escaped eq signal) shouldBe true
            calls shouldBe (if (origin == "enter") 0 else 1)
            log.events shouldBe _trace(boundary, if (origin == "enter") Vector(0, 1) else Vector(0, 1, 2),
              if (origin == "leave") DslChokepointOutcome.Success else DslChokepointOutcome.Failure)
            signal.getSuppressed.toVector shouldBe suppressed
            cleanup.getSuppressed.toVector shouldBe Vector.empty
            Thread.currentThread().isInterrupted shouldBe interrupted
          }
        }
      }
    }

    "select real hooks and preserve legacy compatibility" which {
      "DSL-PRESERVE-03 prefer explicit hooks, then framework hooks, with empty disabling and default fallback" in {
        for (boundary <- Boundary.values.toVector) {
          Given("a source context, a framework hook and a distinct explicit hook")
          val source = ExecutionContext.create()
          val frameworklog = new Log
          val explicitlog = new Log
          val configured = ExecutionContext.withFrameworkDslChokepointHooks(source, Vector(new Hook(1, frameworklog)))
          val wanted = Consequence.success("selected")
          When("the actual selector receives explicit, absent, and explicit-empty hook choices")
          val explicit = _invoke(boundary, Some(Vector(new Hook(2, explicitlog))))(wanted)(using configured)
          val framework = _invoke(boundary, None)(wanted)(using configured)
          val empty = _invoke(boundary, Some(Vector.empty))(wanted)(using configured)
          val default = _invoke(boundary, None)(wanted)(using source)
          Then("only the selected vectors observe calls and the source configuration stays immutable")
          Vector(explicit, framework, empty, default).foreach(value => (value eq wanted) shouldBe true)
          frameworklog.events shouldBe _trace(boundary, Vector(1), DslChokepointOutcome.Success)
          explicitlog.events shouldBe _trace(boundary, Vector(2), DslChokepointOutcome.Success)
          source.framework.dslChokepointHooks shouldBe None
          configured.framework.dslChokepointHooks.get should have size 1
        }
      }
      "DSL-PRESERVE-03 retain legacy Throwable-to-Failure body behavior" in {
        for (signal <- _signals) {
          Given("legacy run and phase callers with hooks explicitly disabled")
          given ExecutionContext = ExecutionContext.create()
          When("the existing public boundaries receive a non-NonFatal body signal")
          val outer = DslChokepointRunner.run[String](_context, Some(Vector.empty)) { throw signal }
          val phase = DslChokepointRunner.phase[String](_context, _phase, Some(Vector.empty)) { throw signal }
          Then("both retain their existing structured Failure interpretation")
          Vector(outer, phase).foreach { value =>
            value shouldBe a[Consequence.Failure[?]]
            _check_conclusion(value.asInstanceOf[Consequence.Failure[?]].conclusion, Conclusion.from(signal), false)
          }
        }
      }
      "DSL-PRESERVE-03 satisfy one total hook-order law with one hundred successes and zero discards" in {
        Given("total hook permutations, valid fault positions, body kinds and meaningful shrinking")
        val property = Prop.forAll(samples) { sample =>
          Given("one independently prescribed ordered hook vector and exact body result")
          given ExecutionContext = ExecutionContext.create()
          val log = new Log
          val fault = new IllegalStateException("owned generated telemetry fault")
          val hooks = sample.order.zipWithIndex.map { case (id, index) =>
            new Hook(id, log, if (sample.enterfault.contains(index)) Some(fault) else None,
              if (sample.leavefault.contains(index)) Some(fault) else None)
          }
          val wanted: Consequence[String] = if (sample.body == BodyKind.Success) Consequence.success("known") else _failure
          val trace = _trace(sample.boundary, sample.order,
            if (sample.body == BodyKind.Success) DslChokepointOutcome.Success else DslChokepointOutcome.Failure)
          var calls = 0
          When("the generated actual boundary interprets its body and telemetry callbacks")
          val actual = _invoke(sample.boundary, Some(hooks)) { calls += 1; wanted }
          Then("exact identity and the predeclared balanced trace hold without body replay")
          (actual eq wanted) shouldBe true
          calls shouldBe 1
          log.events shouldBe trace
          true
        }
        When("ScalaCheck executes the pure boundary law sequentially")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
        Then("the law must pass one hundred successful samples with zero discarded inputs")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }
}
