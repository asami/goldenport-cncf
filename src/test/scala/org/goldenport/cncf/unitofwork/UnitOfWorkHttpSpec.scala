package org.goldenport.cncf.unitofwork

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.bag.Bag
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.free.Free
import cats.{Id, ~>}

/*
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 * @version Mar. 12, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWorkHttpSpec extends AnyWordSpec with Matchers with ConsequenceMatchers {

  private final class FakeHttpDriver(
    response: HttpResponse
  ) extends HttpDriver {
    var calls: Vector[String] = Vector.empty

    override def get(path: String): HttpResponse = {
      calls = calls :+ s"GET $path"
      response
    }

    override def post(
      path: String,
      body: Option[String],
      headers: Map[String, String]
    ): HttpResponse = {
      calls = calls :+ s"POST $path"
      response
    }

    override def put(
      path: String,
      body: Option[String],
      headers: Map[String, String]
    ): HttpResponse = {
      calls = calls :+ s"PUT $path"
      response
    }
  }

  private def _response_ok(): HttpResponse = {
    val contentType = ContentType(
      MimeType("text/plain"),
      Some(StandardCharsets.UTF_8)
    )
    HttpResponse.Text(
      HttpStatus.Ok,
      contentType,
      Bag.text("ok", StandardCharsets.UTF_8)
    )
  }

  "UnitOfWork HTTP wiring" should {
    "execute HTTP ops via Free/ConsequenceT path" in {
      val driver = new FakeHttpDriver(_response_ok())
      val context = _context_(driver)
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(context, eventengine)

      val program = ConsequenceT.liftF(
        Free.liftF[UnitOfWorkOp, HttpResponse](UnitOfWorkOp.HttpGet("/ping"))
      )
      val result = new UnitOfWorkInterpreter(uow).run(program)

      result should be_success
      driver.calls shouldBe Vector("GET /ping")
    }

    "execute HTTP ops via direct path" in {
      val driver = new FakeHttpDriver(_response_ok())
      val context = _context_(driver)
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(context, eventengine)

      val _ = uow.execute[HttpResponse](UnitOfWorkOp.HttpPost("/submit", None, Map.empty))

      driver.calls shouldBe Vector("POST /submit")
    }
  }

  private def _context_(
    driver: HttpDriver
  ): ExecutionContext = {
    val base = ExecutionContext.create()
    val runtime = new RuntimeContext(
      core = RuntimeContext.core(
        name = "unit-of-work-http-spec",
        parent = None,
        observabilityContext = base.observability,
        httpDriverOption = Some(driver)
      ),
      unitOfWorkSupplier = () => throw new UnsupportedOperationException("unitOfWork is not used in this spec runtime"),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in this spec runtime")
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "unit-of-work-http-spec-runtime"
    )
    ExecutionContext.withRuntimeContext(base, runtime)
  }
}
