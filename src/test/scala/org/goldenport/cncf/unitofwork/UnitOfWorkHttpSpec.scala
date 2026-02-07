package org.goldenport.cncf.unitofwork

import java.nio.charset.StandardCharsets
import org.goldenport.ConsequenceT
import org.goldenport.bag.Bag
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.free.Free

/*
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 * @version Feb.  7, 2026
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
      val ctx = ExecutionContext.create()
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(ctx, datastore, eventengine)
      val driver = new FakeHttpDriver(_response_ok())

      val program = ConsequenceT.liftF(
        Free.liftF[UnitOfWorkOp, HttpResponse](UnitOfWorkOp.HttpGet("/ping"))
      )
      val result = new UnitOfWorkInterpreter(uow, driver).run(program)

      result should be_success
      driver.calls shouldBe Vector("GET /ping")
    }

    "execute HTTP ops via direct path" in {
      val ctx = ExecutionContext.create()
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(ctx, datastore, eventengine)
      val driver = new FakeHttpDriver(_response_ok())

      given HttpDriver = driver
      val _ = uow.execute[HttpResponse](UnitOfWorkOp.HttpPost("/submit", None, Map.empty))

      driver.calls shouldBe Vector("POST /submit")
    }
  }
}
