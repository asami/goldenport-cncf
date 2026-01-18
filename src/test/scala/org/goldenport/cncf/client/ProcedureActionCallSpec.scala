package org.goldenport.cncf.client

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, Command, FunctionalActionCall, ResourceAccess}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ExecutionContextId, ObservabilityContext, RuntimeContext, TraceId}
import java.nio.charset.StandardCharsets
import org.goldenport.bag.Bag
import org.goldenport.cncf.http.{FakeHttpDriver, HttpDriver}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.http.{ContentType, HttpRequest, HttpResponse, HttpStatus, MimeType, StringResponse}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 11, 2026
 * @version Jan. 18, 2026
 * @author  ASAMI, Tomoharu
 */
class ProcedureActionCallSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with TableDrivenPropertyChecks {

  "ProcedureActionCall" should {
    "execute immediately without UnitOfWork or CoS hooks" in {
      val table = Table(
        "path",
        "/admin/system/ping",
        "/client/http/post"
      )

      forAll(table) { path =>
        Given("a ProcedureActionCall using http_post_direct")
        val driver = new FakeHttpDriver
        val runtime = new SpyRuntimeContext
        val ctx = _execution_context(runtime.runtime)
        val action = new Command() {
          // val name = "procedure-test"
          val request = Request.ofOperation("procedure-test")
          def createCall(core: ActionCall.Core): ActionCall =
            TestProcedureCall(core, driver, path)
        }
        val call = action.createCall(ActionCall.Core(action, ctx, None))

        When("executeDirect is invoked")
        val result = call.asInstanceOf[TestProcedureCall].executeDirect()

        Then("HttpDriver is called immediately")
        driver.calls shouldBe Vector(
          HttpCall("POST", path, None, Map.empty)
        )

        And("UnitOfWork is not touched and CoS hooks do not fire")
        runtime.unitOfWorkAccessCount shouldBe 0
        runtime.commitCount shouldBe 0
        runtime.abortCount shouldBe 0
        runtime.disposeCount shouldBe 0

        And("Functional DSL is not used")
        call.asInstanceOf[TestProcedureCall].functionalDslUsed shouldBe false

        And("ProcedureActionCall is not composable with FunctionalActionCall")
        call.isInstanceOf[FunctionalActionCall] shouldBe false

        And("the response is an HTTP operation response")
        result shouldBe a[OperationResponse.Http]
      }
    }
  }

  private final case class HttpCall(
    method: String,
    path: String,
    body: Option[String],
    headers: Map[String, String]
  )

  private final class FakeHttpDriver extends HttpDriver {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[HttpCall]

    def calls: Vector[HttpCall] =
      buffer.toVector

    def get(path: String): HttpResponse = {
      buffer += HttpCall("GET", path, None, Map.empty)
      _response()
    }

    def post(
      path: String,
      body: Option[String],
      headers: Map[String, String]
    ): HttpResponse = {
      buffer += HttpCall("POST", path, body, headers)
      _response()
    }

    private def _response(): HttpResponse = {
      val contentType = ContentType(
        MimeType("text/plain"),
        Some(StandardCharsets.UTF_8)
      )
      StringResponse(
        HttpStatus.Ok,
        contentType,
        Bag.text("ok", StandardCharsets.UTF_8)
      )
    }
  }

  private trait ProcedureActionCall extends ActionCall {
    def httpDriver: HttpDriver

    def executeDirect(): OperationResponse

    final override def execute(): Consequence[OperationResponse] =
      Consequence.success(executeDirect())

    protected final def http_post_direct(req: HttpRequest): HttpResponse =
      httpDriver.post(req.path.asString, None, Map.empty)

    protected final def http_post_directC(req: HttpRequest): Consequence[HttpResponse] =
      Consequence.success(http_post_direct(req))
  }

  private final case class TestProcedureCall(
    core: ActionCall.Core,
    driver: HttpDriver,
    path: String
  ) extends ProcedureActionCall {
    var functionalDslUsed: Boolean = false

    def httpDriver: HttpDriver = driver

    def executeDirect(): OperationResponse = {
      val req = HttpRequest.fromPath(HttpRequest.POST, path)
      val res = http_post_direct(req)
      OperationResponse.Http(res)
    }

    private def _functional_http_post(req: HttpRequest): Unit = {
      val _ = req
      functionalDslUsed = true
    }
  }

  private final class SpyRuntimeContext {
    var unitOfWorkAccessCount: Int = 0
    var commitCount: Int = 0
    var abortCount: Int = 0
    var disposeCount: Int = 0

    private val observability = _testObservabilityContext()
    private val driver = FakeHttpDriver.okText("nop")

    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "procedure-action-call-spec-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver)
      ),
      unitOfWorkSupplier = () => {
        unitOfWorkAccessCount += 1
        throw new UnsupportedOperationException("UnitOfWork must not be accessed")
      },
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWorkOp[A]): Id[A] = {
          val _ = fa
          unitOfWorkAccessCount += 1
          throw new UnsupportedOperationException("UnitOfWorkInterpreter must not be used")
        }
      },
      unitOfWorkTryInterpreterFn = new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] = {
          val _ = fa
          unitOfWorkAccessCount += 1
          throw new UnsupportedOperationException("UnitOfWorkTryInterpreter must not be used")
        }
      },
      unitOfWorkEitherInterpreterFn = new (UnitOfWorkOp ~> RuntimeContext.EitherThrowable) {
        def apply[A](op: UnitOfWorkOp[A]): Either[Throwable, A] = {
          val _ = op
          unitOfWorkAccessCount += 1
          Left(new UnsupportedOperationException("UnitOfWorkEitherInterpreter must not be used"))
        }
      },
      commitAction = _ => {
        commitCount += 1
      },
      abortAction = _ => {
        abortCount += 1
      },
      disposeAction = _ => {
        disposeCount += 1
      },
      token = "procedure-action-call-spec-runtime-context"
    )
  }

  private def _testObservabilityContext(): ObservabilityContext = {
    val id = ExecutionContextId.generate()
    ObservabilityContext(
      traceId = TraceId(id),
      spanId = None,
      correlationId = Some(CorrelationId(id))
    )
  }

  private def _execution_context(
    runtime: RuntimeContext
  ): ExecutionContext = {
    val base = ExecutionContext.create()
    ExecutionContext.withRuntimeContext(base, runtime)
  }
}
