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
import org.goldenport.protocol.Property
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.free.Free
import cats.{Id, ~>}

/*
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 *  version Mar. 12, 2026
 * @version Jul.  3, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWorkHttpSpec extends AnyWordSpec with Matchers with ConsequenceMatchers {

  private final class FakeHttpDriver(
    response: HttpResponse
  ) extends HttpDriver {
    var calls: Vector[String] = Vector.empty
    var properties: Vector[Property] = Vector.empty

    override def get(
      path: String,
      headers: Map[String, String],
      properties: Vector[Property] = Vector.empty
    ): HttpResponse = {
      calls = calls :+ s"GET $path"
      this.properties = properties
      response
    }

    override def post(
      path: String,
      body: Option[String],
      headers: Map[String, String],
      properties: Vector[Property] = Vector.empty
    ): HttpResponse = {
      calls = calls :+ s"POST $path"
      this.properties = properties
      response
    }

    override def put(
      path: String,
      body: Option[String],
      headers: Map[String, String],
      properties: Vector[Property] = Vector.empty
    ): HttpResponse = {
      calls = calls :+ s"PUT $path"
      this.properties = properties
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
      val context = _context(driver)
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
      val context = _context(driver)
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(context, eventengine)

      val _ = uow.execute[HttpResponse](UnitOfWorkOp.HttpPost("/submit", None, Map.empty))

      driver.calls shouldBe Vector("POST /submit")
    }

    "propagate HTTP execution properties to the configured driver" in {
      val driver = new FakeHttpDriver(_response_ok())
      val context = _context(driver)
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(context, eventengine)
      val property = Property("http.timeout-seconds", "180", None)

      val _ = uow.execute[HttpResponse](UnitOfWorkOp.HttpPost("/submit", None, Map.empty, Vector(property)))

      driver.calls shouldBe Vector("POST /submit")
      driver.properties shouldBe Vector(property)
    }
  }

  private def _context(
    driver: HttpDriver
  ): ExecutionContext = {
    val base = ExecutionContext.create()
    val runtime = new RuntimeContext(
      core = RuntimeContext.core(
        name = "unit-of-work-http-spec",
        parent = None,
        observabilitycontext = base.observability,
        httpdriveroption = Some(driver)
      ),
      unitofworksupplier = () => throw new UnsupportedOperationException("unitOfWork is not used in this spec runtime"),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in this spec runtime")
      },
      commitaction = _ => (),
      abortaction = _ => (),
      disposeaction = _ => (),
      token = "unit-of-work-http-spec-runtime"
    )
    ExecutionContext.withRuntimeContext(base, runtime)
  }
}
