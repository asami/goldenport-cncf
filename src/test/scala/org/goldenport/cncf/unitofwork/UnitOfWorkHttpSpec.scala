package org.goldenport.cncf.unitofwork

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.bag.Bag
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import cats.free.Free
import cats.{Id, ~>}

/*
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 *  version Mar. 12, 2026
 *  version Jul.  3, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWorkHttpSpec extends AnyWordSpec with Matchers with ConsequenceMatchers with GivenWhenThen {

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
    val contenttype = ContentType(
      MimeType("text/plain"),
      Some(StandardCharsets.UTF_8)
    )
    HttpResponse.Text(
      HttpStatus.Ok,
      contenttype,
      Bag.text("ok", StandardCharsets.UTF_8)
    )
  }

  "UnitOfWork HTTP wiring" should {
    "execute HTTP ops via Free/ConsequenceT path" in {
      Given("an actual Component context with an exact host GET and POST grant")
      val driver = new FakeHttpDriver(_response_ok())
      val context = _context(driver)
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(context, eventengine)

      val program = ConsequenceT.liftF(
        Free.liftF[UnitOfWorkOp, HttpResponse](UnitOfWorkOp.HttpGet("/ping"))
      )
      When("the declared HTTP program runs through Free and ConsequenceT")
      val result = new UnitOfWorkInterpreter(uow).run(program)

      Then("the admitted absolute destination reaches the driver and returns success")
      result should be_success
      driver.calls shouldBe Vector("GET http://localhost:8080/ping")
    }

    "execute HTTP ops via direct path" in {
      Given("an actual owning Component and the configured HTTP driver")
      val driver = new FakeHttpDriver(_response_ok())
      val context = _context(driver)
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(context, eventengine)

      When("the direct UnitOfWork executes POST")
      val result = uow.execute[HttpResponse](UnitOfWorkOp.HttpPost("/submit", None, Map.empty))

      Then("the absolute destination reaches the driver and its response is returned")
      result.code shouldBe 200
      driver.calls shouldBe Vector("POST http://localhost:8080/submit")
    }

    "propagate HTTP execution properties to the configured driver" in {
      Given("a finite host ceiling and caller properties including an unrelated value")
      val driver = new FakeHttpDriver(_response_ok())
      val context = _context(driver)
      val datastore = DataStore.noop()
      val eventengine = EventEngine.noop(datastore)
      val uow = new UnitOfWork(context, eventengine)
      val property = Property("http.timeout-seconds", "180", None)
      val unrelated = Property("application.trace", "keep", None)

      When("POST requests a timeout above the host ceiling")
      val result = uow.execute[HttpResponse](UnitOfWorkOp.HttpPost("/submit", None, Map.empty, Vector(property, unrelated)))

      Then("the request succeeds with canonical host bounds and its unrelated property intact")
      result.code shouldBe 200
      driver.calls shouldBe Vector("POST http://localhost:8080/submit")
      driver.properties shouldBe Vector(unrelated,
        Property("http.connect-timeout-ms", "1000", None), Property("http.read-timeout-ms", "2000", None),
        Property("http.max-response-bytes", "4096", None), Property("http.follow-redirects", "false", None))
    }
  }

  private def _context(
    driver: HttpDriver
  ): ExecutionContext = {
    val base = ExecutionContext.create()
    val owner = ComponentId("org.example.HttpWiring")
    val admission = HttpExecutionAdmission.fromRuntimeC("http://localhost:8080/", HttpExecutionLimits(1000, 2000, 4096),
      Map(owner -> HttpExecutionGrant(Vector("http://localhost:8080"), Set(HttpExecutionMethod.Get, HttpExecutionMethod.Post))))
      .getOrElse(fail("host HTTP setup failed"))
    val runtime = new RuntimeContext(
      core = RuntimeContext.core(
        name = "unit-of-work-http-spec",
        parent = None,
        observabilitycontext = base.observability,
        httpdriveroption = Some(driver)
      ).copy(httpExecutionAdmissionOption = Some(admission)),
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
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    component.withScopeContext(runtime)
    ExecutionContext.withRuntimeContext(base, runtime).withScope(component.scopeContext)
  }
}
