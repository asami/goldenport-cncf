package org.goldenport.cncf.action

import java.util.concurrent.atomic.AtomicBoolean

import cats.~>
import org.goldenport.Consequence
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

/*
 * @since Aug. 12, 2026
 * @version Aug. 12, 2026
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
        val engine = new RecordingActionEngine
        val action = new QueryAction() {
          val request = Request.ofOperation("normal-authorization-rejection")

          override def createCall(core: ActionCall.Core): ActionCall = {
            val captured = core
            new ActionCall {
              override val core: ActionCall.Core = captured

              override def authorize()(using ctx: ExecutionContext): Consequence[Unit] =
                Consequence.securityPermissionDenied("normal authorization rejected")

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
          case Consequence.Failure(conclusion) => conclusion.show should include("normal authorization rejected")
          case other => fail(s"expected authorization failure, got $other")
        }
        executed.get() shouldBe false
        engine.events shouldBe Vector.empty
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
  }

  private final class RecordingActionEngine
    extends ActionEngine(
      ActionEngine.Config(),
      AuthorizationEngine.create()
    ) {
    private val _events = scala.collection.mutable.ArrayBuffer.empty[String]

    def events: Vector[String] = _events.toVector

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
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in normal authorization spec")
      },
      commitaction = _ => (),
      abortaction = _ => (),
      disposeaction = _ => (),
      token = "action-engine-normal-authorization"
    )

    def bind(uow: UnitOfWork): Unit =
      _unit_of_work = Some(uow)
  }

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val _entries = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      _entries += message
  }
}
