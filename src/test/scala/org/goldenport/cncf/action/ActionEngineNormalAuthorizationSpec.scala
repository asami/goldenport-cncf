package org.goldenport.cncf.action

import java.time.Instant
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import cats.~>
import org.goldenport.{Conclusion, Consequence, ConsequenceException}
import org.goldenport.observation.{Descriptor, SourcePosition}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, RuntimeContext, TraceId}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{EventEngine, EventLane, EventStore}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.security.AuthorizationEngine
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/*
 * @since Aug. 12, 2026
 * @version Oct. 15, 2026
 * @author ASAMI, Tomoharu
 */
final class ActionEngineNormalAuthorizationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e7 = afterWord(
    "in spec:action-execution-semantics, example:E7, rules:R1,R8,R11, phase:57.1, slice:AES-02"
  )

  "ActionEngine normal execute authorization" should {
    "E7 normal ActionCall authorization rejection" must _e7 {
      "return structured authorization failure before execution or observation without an authorization event" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R1,R8,R11; Example: E7; a bound RuntimeContext and an ActionCall whose normal authorize method denies access")
        val recorder = new InMemoryCommitRecorder
        val datastore = DataStore.noop(recorder)
        val eventengine = EventEngine.noop(datastore, recorder)
        val runtimesupport = new TestRuntimeContext
        val basecontext = ExecutionContext.withFrameworkInlineCallTreeEnabled(
          ExecutionContext.create(),
          enabled = true
        )
        val context = ExecutionContext.withRuntimeContext(basecontext, runtimesupport.runtime)
        val uow = new UnitOfWork(context, eventengine, recorder)
        runtimesupport.bind(uow)
        val executed = new AtomicBoolean(false)
        val authorized = new AtomicInteger(0)
        val supplied = _supplied_conclusion("normal authorization rejected", 1)
        val engine = new RecordingActionEngine
        val action = new QueryAction() {
          val request = Request.ofOperation("normal-authorization-rejection")

          override def createCall(core: ActionCall.Core): ActionCall = {
            val captured = core
            new ActionCall {
              override val core: ActionCall.Core = captured

              override def authorize()(using ctx: ExecutionContext): Consequence[Unit] = {
                authorized.incrementAndGet()
                Consequence.Failure(supplied)
              }

              override def execute(): Consequence[OperationResponse] = {
                executed.set(true)
                Consequence.success(OperationResponse.Scalar("must-not-execute"))
              }
            }
          }
        }
        val call = action.createCall(ActionCall.Core(action, context, None, None))

        When("the normal ActionEngine.execute path evaluates the ActionCall")
        val result = engine.execute(call)

        Then("authorization failure is structured and the ActionCall never starts")
        result shouldBe a[Consequence.Failure[?]]
        result match {
          case Consequence.Failure(conclusion) => _check_conclusion(conclusion, supplied)
          case other => fail(s"expected authorization failure, got $other")
        }
        executed.get() shouldBe false
        authorized.get() shouldBe 1
        engine.authorizationCount shouldBe 1
        engine.events shouldBe Vector.empty
        runtimesupport.effects shouldBe Vector.empty
        recorder.entries shouldBe Vector.empty
        val authorizationevents = eventengine.eventStore.query(
          EventStore.Query(
            name = Some(action.name),
            lane = Some(EventLane.Transactional)
          )
        ).toOption.getOrElse(fail("normal authorization rejection event query failed"))
        authorizationevents shouldBe empty

        And("the failure calltree boundary records the diagnostic without an observation pair")
        val calltree = context.runtime.executionMetadata.inlineCallTree.map(_.print).getOrElse("")
        calltree should include("io:error")
        context.runtime.executionMetadata.failure should not be empty
      }
    }

    "supplied structured authorization Conclusions" which {
      "retain authentication, permission and Throwable-bearing Conclusions with their previous cause before any effect" in {
        for (variant <- Vector(0, 1, 2)) {
          Given(s"an independently supplied authorization Conclusion variant $variant with application status, facets, source position and a prior Conclusion")
          val expected = _supplied_conclusion("supplied rejection " + variant, variant)
          val fixture = new AuthorizationFixture(Consequence.Failure(expected))
          When("normal execute delegates once through the existing protected authorization hook")
          val result = fixture.engine.execute(fixture.call)
          Then("the exact original Conclusion and every structured component survive without starting the action")
          _check_rejection(fixture, expected, result)
        }
      }

      "preserve exact supplied authorization Conclusions for at least one hundred total samples" in {
        Given("total bounded message and Conclusion-variant generators with ordinary reducing String and Int shrinkers")
        val samples = for {
          length <- Gen.chooseNum(0, 24)
          letters <- Gen.listOfN(length, Gen.oneOf('a', 'Z', '0', ' ', '日', 'Ω'))
          variant <- Gen.chooseNum(0, 2)
        } yield letters.mkString -> variant
        val parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
        val property = Prop.forAll(samples) { case (message, variant) =>
          Given("a Conclusion declared independently before this generated authorization attempt")
          val expected = _supplied_conclusion(message, variant)
          val fixture = new AuthorizationFixture(Consequence.Failure(expected))
          When("normal execute evaluates the supplied failure through the protected hook")
          val result = fixture.engine.execute(fixture.call)
          Then("identity, full structure, the failure calltree and every zero-effect boundary are preserved")
          _check_rejection(fixture, expected, result)
          true
        }
        When("the sequential supplied-Conclusion law evaluates its total samples")
        val result = Test.check(parameters, property)
        Then("the law passes with at least one hundred successes and no discarded sample")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }

    "protected authorization hook compatibility" which {
      "delegate successful authorization exactly once and retain the normal started-action lifecycle" in {
        Given("a protected-hook override delegating to the ordinary successful ActionCall authorization")
        val fixture = new AuthorizationFixture(Consequence.unit)
        When("normal execute admits the call")
        val result = fixture.engine.execute(fixture.call)
        Then("the same hook and ActionCall authorization each run once before the one execution and observation pair")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        fixture.engine.authorizationCount shouldBe 1
        fixture.authorizationCount shouldBe 1
        fixture.executionCount shouldBe 1
        fixture.engine.events shouldBe Vector("observe_enter", "observe_leave")
        fixture.support.effects shouldBe Vector("commit", "dispose")
      }

      "convert unexpected NonFatal hook failures and non-Failure structured carriers without starting an action" in {
        for (exception <- Vector(new IllegalStateException("unexpected authorization hook"), new ConsequenceException(Consequence.unit))) {
          Given("a protected authorization override that throws a predeclared NonFatal exception instead of delegating")
          val expected = Conclusion.from(exception)
          val fixture = new AuthorizationFixture(Consequence.unit, new RecordingActionEngine(Some(exception)))
          When("normal execute invokes the override once")
          val result = fixture.engine.execute(fixture.call)
          Then("the existing Throwable conversion is retained and no ActionCall, provider, event or commit begins")
          result match {
            case Consequence.Failure(failure) =>
              failure.observation.taxonomy shouldBe expected.observation.taxonomy
              failure.observation.cause shouldBe expected.observation.cause
              failure.status shouldBe expected.status
              failure.interpretation shouldBe expected.interpretation
              failure.disposition shouldBe expected.disposition
              failure.previous shouldBe None
              failure.getException shouldBe Some(exception)
            case other => fail(s"expected hook failure, got $other")
          }
          fixture.engine.authorizationCount shouldBe 1
          fixture.authorizationCount shouldBe 0
          fixture.executionCount shouldBe 0
          _check_no_start(fixture)
        }
      }
    }
  }

  private def _supplied_conclusion(message: String, variant: Int): Conclusion = {
    val base = if (variant == 0) Conclusion.securityAuthenticationRequired(message)
      else Conclusion.securityPermissionDenied(message)
    val prior = Conclusion.securityPermissionDenied("prior authorization context").copy(
      observation = Conclusion.securityPermissionDenied("prior authorization context").observation.copy(timestamp = Instant.EPOCH))
    val cause = base.observation.cause.addFacet(Descriptor.Facet.Reason("declared-policy"))
      .withSourcePosition(SourcePosition("authorization-contract.scala", 17, 3))
    val suppliedcause = if (variant == 2) cause.withException(new IllegalArgumentException("attached original cause")) else cause
    base.copy(status = Conclusion.Status(appCode = Some(7401L + variant), appStatus = Some("authorization-variant-" + variant)),
      observation = base.observation.copy(cause = suppliedcause, timestamp = Instant.EPOCH,
        properties = Map("boundary" -> "pre-execution")), previous = Some(prior))
  }

  private def _check_conclusion(actual: Conclusion, expected: Conclusion): Unit = {
    (actual eq expected) shouldBe true
    actual.observation shouldBe expected.observation
    actual.observation.cause shouldBe expected.observation.cause
    actual.status shouldBe expected.status
    actual.interpretation shouldBe expected.interpretation
    actual.disposition shouldBe expected.disposition
    actual.getException shouldBe expected.getException
    actual.previous shouldBe expected.previous
    expected.previous.foreach(prior => (actual.previous.get eq prior) shouldBe true)
  }

  private def _check_rejection(fixture: AuthorizationFixture, expected: Conclusion, result: Consequence[OperationResponse]): Unit = {
    result match {
      case Consequence.Failure(failure) => _check_conclusion(failure, expected)
      case other => fail(s"expected supplied authorization failure, got $other")
    }
    fixture.engine.authorizationCount shouldBe 1
    fixture.authorizationCount shouldBe 1
    fixture.executionCount shouldBe 0
    _check_no_start(fixture)
  }

  private def _check_no_start(fixture: AuthorizationFixture): Unit = {
    fixture.engine.events shouldBe Vector.empty
    fixture.support.effects shouldBe Vector.empty
    fixture.recorder.entries shouldBe Vector.empty
    val events = fixture.eventengine.eventStore.query(EventStore.Query(
      name = Some(fixture.call.action.name), lane = Some(EventLane.Transactional))).toOption
      .getOrElse(fail("normal authorization event query failed"))
    events shouldBe empty
    fixture.context.runtime.executionMetadata.inlineCallTree.map(_.print).getOrElse("") should include("io:error")
    fixture.context.runtime.executionMetadata.failure should not be empty
  }

  private final class AuthorizationFixture(authorization: Consequence[Unit], val engine: RecordingActionEngine = new RecordingActionEngine) {
    val recorder = new InMemoryCommitRecorder
    private val _datastore = DataStore.noop(recorder)
    val eventengine = EventEngine.noop(_datastore, recorder)
    val support = new TestRuntimeContext
    val context = ExecutionContext.withRuntimeContext(
      ExecutionContext.withFrameworkInlineCallTreeEnabled(ExecutionContext.create(), enabled = true), support.runtime)
    private val _unit_of_work = new UnitOfWork(context, eventengine, recorder)
    support.bind(_unit_of_work)
    private val _authorized = new AtomicInteger(0)
    private val _executed = new AtomicInteger(0)
    private val _action = new QueryAction {
      val request = Request.ofOperation("supplied-authorization-contract")
      def createCall(core: ActionCall.Core): ActionCall = {
        val captured = core
        new ActionCall {
          val core: ActionCall.Core = captured
          override def authorize()(using ctx: ExecutionContext): Consequence[Unit] = {
            _authorized.incrementAndGet()
            authorization
          }
          def execute(): Consequence[OperationResponse] = {
            _executed.incrementAndGet()
            Consequence.success(OperationResponse.Scalar("ok"))
          }
        }
      }
    }
    val call: ActionCall = _action.createCall(ActionCall.Core(_action, context, None, None))
    def authorizationCount: Int = _authorized.get()
    def executionCount: Int = _executed.get()
  }

  private final class RecordingActionEngine(private val _hook_failure: Option[Throwable] = None)
    extends ActionEngine(
      ActionEngine.Config(),
      AuthorizationEngine.create()
    ) {
    private val _events = scala.collection.mutable.ArrayBuffer.empty[String]
    private var _authorizations = 0

    def events: Vector[String] = _events.toVector
    def authorizationCount: Int = _authorizations

    override protected def security_authorize(call: ActionCall, ec: ExecutionContext): Unit = {
      _authorizations += 1
      _hook_failure match {
        case Some(exception) => throw exception
        case None => super.security_authorize(call, ec)
      }
    }

    override protected def observe_enter(call: ActionCall): Unit =
      _events += "observe_enter"

    override protected def observe_leave(
      call: ActionCall,
      result: Consequence[OperationResponse]
    ): Unit =
      _events += "observe_leave"
  }

  private final class TestRuntimeContext {
    private var _unit_of_work: Option[UnitOfWork] = None
    private val _effects = scala.collection.mutable.ArrayBuffer.empty[String]
    def effects: Vector[String] = _effects.toVector
    private val _observability = ObservabilityContext(
      traceId = TraceId("action_engine", "normal_authorization"),
      spanId = None,
      correlationId = Some(CorrelationId("action_engine", "normal_authorization"))
    )
    private val _driver = FakeHttpDriver.okText("noop")

    val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "action-engine-normal-authorization",
        parent = None,
        observabilitycontext = _observability,
        httpdriveroption = Some(_driver)
      ),
      unitofworksupplier = () => _unit_of_work.getOrElse {
        throw new IllegalStateException("UnitOfWork has not been bound")
      },
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          _effects += "provider"
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in normal authorization spec")
        }
      },
      commitaction = _ => { _effects += "commit"; () },
      abortaction = _ => { _effects += "abort"; () },
      disposeaction = _ => { _effects += "dispose"; () },
      token = "action-engine-normal-authorization"
    )

    def bind(uow: UnitOfWork): Unit =
      _unit_of_work = Some(uow)
  }

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val _entries = scala.collection.mutable.ArrayBuffer.empty[String]
    def entries: Vector[String] = _entries.toVector

    def record(message: String): Unit =
      _entries += message
  }
}
