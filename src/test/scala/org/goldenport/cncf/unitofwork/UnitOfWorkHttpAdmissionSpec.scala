package org.goldenport.cncf.unitofwork

import java.io.ByteArrayInputStream
import java.net.{InetSocketAddress, URL, URLConnection, URLStreamHandler}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import cats.free.Free
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.bag.{Bag, BagBackend}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod, UrlConnectionHttpDriver}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkHttpAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _owner = ComponentId("org.example.UowHttpOwner")
  private val _other = ComponentId("org.example.UowHttpOther")
  private val _base_url = "http://127.0.0.1:8089/"
  private val _headers = Map("X-Workflow" -> "original")
  private val _body = "original-body"

  private def _take[A](value: Consequence[A]): A = value.getOrElse(fail("HTTP UoW fixture failed"))
  private def _policy[A](value: Consequence[A]): Option[String] = value match {
    case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
    case _ => None
  }
  private def _admission(
    base: String = _base_url,
    origins: Vector[String] = Vector("http://127.0.0.1:8089"),
    methods: Set[HttpExecutionMethod] = HttpExecutionMethod.values.toSet,
    bytes: Long = 4096
  ): HttpExecutionAdmission = _take(HttpExecutionAdmission.fromRuntimeC(base, HttpExecutionLimits(1000, 1000, bytes),
    Map(_owner -> HttpExecutionGrant(origins, methods))))

  private def _context(admission: Option[HttpExecutionAdmission], owner: ComponentId = _owner): ExecutionContext = {
    val base = ExecutionContext.create()
    val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = admission))
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    component.withScopeContext(root)
    base.withScope(component.scopeContext)
  }

  private def _response(): HttpResponse = HttpResponse.Text(HttpStatus.Ok,
    ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)), Bag.text("same-response", StandardCharsets.UTF_8))

  private final class CountedUnitOfWork(context: ExecutionContext, driver: HttpDriver) extends UnitOfWork(context) {
    private val _acquisitions = new AtomicInteger
    def acquisitions: Int = _acquisitions.get()
    override def httpDriver: HttpDriver = {
      _acquisitions.incrementAndGet()
      driver
    }
  }

  private final class CountedDriver(response: HttpResponse) extends HttpDriver {
    var calls: Vector[(String, String, Option[String], Map[String, String], Vector[Property])] = Vector.empty
    private def _record(method: String, path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      calls = calls :+ ((method, path, body, headers, properties))
      response
    }
    def get(path: String, headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
      _record("GET", path, None, headers, properties)
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
      _record("POST", path, body, headers, properties)
    override def postBag(path: String, body: Option[Bag], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse = {
      val text = body.map { bag =>
        val in = bag.openInputStream()
        try new String(in.readAllBytes(), StandardCharsets.UTF_8) finally in.close()
      }
      _record("POST", path, text, headers, properties)
    }
    override def putBag(path: String, body: Option[Bag], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse = {
      val text = body.map { bag =>
        val in = bag.openInputStream()
        try new String(in.readAllBytes(), StandardCharsets.UTF_8) finally in.close()
      }
      _record("PUT", path, text, headers, properties)
    }
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
      _record("PUT", path, body, headers, properties)
  }

  private def _bag(opens: AtomicInteger): Bag = {
    val handler = new URLStreamHandler {
      override def openConnection(url: URL): URLConnection = new URLConnection(url) {
        def connect(): Unit = ()
        override def getInputStream(): java.io.InputStream = {
          opens.incrementAndGet()
          new ByteArrayInputStream(_body.getBytes(StandardCharsets.UTF_8))
        }
      }
    }
    Bag.Instance(BagBackend.Url(new URL("counted", "input", 0, "/body", handler)))
  }

  private def _operations(bag: Bag, path: String = "/selected", properties: Vector[Property] = Vector.empty): Vector[UnitOfWorkOp[HttpResponse]] =
    Vector(UnitOfWorkOp.HttpGet(path, _headers, properties), UnitOfWorkOp.HttpPost(path, Some(_body), _headers, properties),
      UnitOfWorkOp.HttpPostBag(path, Some(bag), _headers, properties), UnitOfWorkOp.HttpPut(path, Some(_body), _headers, properties),
      UnitOfWorkOp.HttpPutBag(path, Some(bag), _headers, properties))

  private def _execute(uow: UnitOfWork, op: UnitOfWorkOp[HttpResponse], free: Boolean): Consequence[HttpResponse] = {
    val interpreter = new UnitOfWorkInterpreter(uow)
    if (free) interpreter.run(ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](op)))
    else interpreter.interpret(op)
  }

  "Actual HTTP UnitOfWork admission" should {
    "deny all five unconfigured operations in direct and Free execution before driver or bag acquisition" in {
      Vector(false, true).foreach { free =>
        Given(s"an unconfigured actual Component and all five passive HTTP operations, Free=$free")
        val opens = new AtomicInteger
        val operations = _operations(_bag(opens))
        val fixtures = operations.map { op =>
          val driver = new CountedDriver(_response())
          (new CountedUnitOfWork(_context(None), driver), driver, op)
        }
        When("the same declared operations reach actual interpretation")
        val outcomes = fixtures.map { case (uow, driver, op) => (_execute(uow, op, free), uow, driver) }
        Then("every method fails unconfigured without acquiring any driver or opening either binary body")
        outcomes.foreach { case (outcome, uow, driver) =>
          _policy(outcome) shouldBe Some("http.execution.unconfigured")
          uow.acquisitions shouldBe 0
          driver.calls shouldBe empty
        }
        opens.get() shouldBe 0
      }
    }

    "refuse wrong current owners, methods, destinations and metadata-only authority before driver acquisition" in {
      Vector(false, true).foreach { free =>
        Given(s"five operations with purported metadata grants and otherwise exact host authority, Free=$free")
        val opens = new AtomicInteger
        val metadata = Vector(Property("componentId", _owner.name, None), Property("http.execution.grant", "purported", None))
        val operations = _operations(_bag(opens), properties = metadata)
        val fixtures = operations.zipWithIndex.flatMap { case (op, index) =>
          val wrongmethod = if (index == 0) Set(HttpExecutionMethod.Post) else Set(HttpExecutionMethod.Get)
          Vector((_context(Some(_admission()), _other), op, "grant"),
            (_context(Some(_admission(methods = wrongmethod))), op, "method"),
            (_context(Some(_admission())), _operations(_bag(opens), "http://127.0.0.1:8090/selected")(index), "destination"),
            (_context(None), op, "unconfigured"))
        }.map { case (context, op, code) =>
          val driver = new CountedDriver(_response())
          (new CountedUnitOfWork(context, driver), driver, op, code)
        }
        When("each operation is interpreted in the actual current UoW context")
        val outcomes = fixtures.map { case (uow, driver, op, code) => (_execute(uow, op, free), uow, driver, code) }
        Then("no request label, driver fixture or body acquires authority")
        outcomes.foreach { case (outcome, uow, driver, code) =>
          _policy(outcome) shouldBe Some(s"http.execution.$code")
          uow.acquisitions shouldBe 0
          driver.calls shouldBe empty
        }
        opens.get() shouldBe 0
      }
    }

    "return the identical driver response for each admitted direct and Free program with original bodies and headers" in {
      Vector(false, true).foreach { free =>
        Given(s"an exact real Component, all three granted methods and the same five HTTP programs, Free=$free")
        val opens = new AtomicInteger
        val response = _response()
        val operations = _operations(_bag(opens))
        val fixtures = operations.map { op =>
          val driver = new CountedDriver(response)
          (new CountedUnitOfWork(_context(Some(_admission())), driver), driver, op)
        }
        When("those admitted operations execute")
        val outcomes = fixtures.map { case (uow, driver, op) => (_execute(uow, op, free), uow, driver) }
        Then("the same concrete response returns to each program with its original request data and finite controls")
        outcomes.zipWithIndex.foreach { case ((outcome, uow, driver), index) =>
          _take(outcome) should be theSameInstanceAs response
          uow.acquisitions shouldBe 1
          driver.calls.size shouldBe 1
          val call = driver.calls.head
          call._1 shouldBe Vector("GET", "POST", "POST", "PUT", "PUT")(index)
          call._2 shouldBe "http://127.0.0.1:8089/selected"
          call._3 shouldBe (if (index == 0) None else Some(_body))
          call._4 shouldBe _headers
          call._5 shouldBe Vector(Property("http.connect-timeout-ms", "1000", None),
            Property("http.read-timeout-ms", "1000", None), Property("http.max-response-bytes", "4096", None),
            Property("http.follow-redirects", "false", None))
        }
        opens.get() shouldBe 2
      }
    }

    "redact sensitive paths, headers and bodies from all five operation displays" in {
      Given("HTTP operations carrying distinct bearer, body and resumable-session markers")
      val path = "https://example.invalid/session-sensitive-path"
      val headers = Map("Authorization" -> "Bearer sensitive-bearer")
      val body = "sensitive-body"
      val operations = Vector[UnitOfWorkOp[HttpResponse]](
        UnitOfWorkOp.HttpGet(path, headers),
        UnitOfWorkOp.HttpPost(path, Some(body), headers),
        UnitOfWorkOp.HttpPostBag(path, Some(Bag.text(body, StandardCharsets.UTF_8)), headers),
        UnitOfWorkOp.HttpPut(path, Some(body), headers),
        UnitOfWorkOp.HttpPutBag(path, Some(Bag.text(body, StandardCharsets.UTF_8)), headers))
      When("the passive operation values are rendered")
      val displays = operations.map(_.toString)
      Then("only the operation name and a fixed redaction marker are visible")
      displays shouldBe Vector("HttpGet(<redacted>)", "HttpPost(<redacted>)", "HttpPostBag(<redacted>)",
        "HttpPut(<redacted>)", "HttpPutBag(<redacted>)")
      displays.foreach { display =>
        Vector("session-sensitive-path", "sensitive-bearer", "sensitive-body", "Authorization").foreach { marker =>
          display should not include marker
        }
      }
    }

    "return real 200 and original 302 responses without following a separately granted loopback origin" in {
      Given("two test-owned ephemeral origins and a redirect whose target is also explicitly granted")
      val source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val destination = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val requests = new AtomicInteger
      try {
        val target = s"http://127.0.0.1:${destination.getAddress.getPort}"
        val origin = s"http://127.0.0.1:${source.getAddress.getPort}"
        source.createContext("/ok", new HttpHandler {
          def handle(exchange: HttpExchange): Unit = _reply(exchange, 200, "same-workflow", None)
        })
        source.createContext("/redirect", new HttpHandler {
          def handle(exchange: HttpExchange): Unit = _reply(exchange, 302, "redirect", Some(s"$target/target"))
        })
        destination.createContext("/target", new HttpHandler {
          def handle(exchange: HttpExchange): Unit = {
            requests.incrementAndGet()
            _reply(exchange, 200, "unexpected", None)
          }
        })
        source.start()
        destination.start()
        val admission = _admission(s"$origin/", Vector(origin, target))
        val driver = new UrlConnectionHttpDriver("http://127.0.0.1:1/unrelated-base")
        When("normal UoW Free programs execute the successful and redirect routes")
        val ok = _execute(new CountedUnitOfWork(_context(Some(admission)), driver), UnitOfWorkOp.HttpGet("/ok"), free = true)
        val redirect = _execute(new CountedUnitOfWork(_context(Some(admission)), driver), UnitOfWorkOp.HttpGet("/redirect"), free = true)
        Then("the workflow receives both original responses and the redirect destination receives no request")
        _take(ok).code shouldBe 200
        _take(ok).getString shouldBe Some("same-workflow")
        _take(redirect).code shouldBe 302
        _take(redirect).getString shouldBe Some("redirect")
        _take(redirect).headerValue("Location") shouldBe Some(s"$target/target")
        requests.get() shouldBe 0
      } finally {
        source.stop(0)
        destination.stop(0)
      }
    }

    "fail during real response reading when the configured byte ceiling is exceeded" in {
      Given("a test-owned ephemeral origin returning a body larger than its finite host ceiling")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val requests = new AtomicInteger
      try {
        server.createContext("/large", new HttpHandler {
          def handle(exchange: HttpExchange): Unit = {
            requests.incrementAndGet()
            _reply(exchange, 200, "oversized", None)
          }
        })
        server.start()
        val origin = s"http://127.0.0.1:${server.getAddress.getPort}"
        val context = _context(Some(_admission(s"$origin/", Vector(origin), bytes = 4)))
        val uow = new CountedUnitOfWork(context, new UrlConnectionHttpDriver(origin))
        When("the normal bounded UoW route reads the actual response")
        val outcome = _execute(uow, UnitOfWorkOp.HttpGet("/large"), free = true)
        Then("the transport fails without returning a truncated accepted body or a policy success")
        outcome.isFaillure shouldBe true
        _policy(outcome) shouldBe None
        outcome.toOption shouldBe None
        uow.acquisitions shouldBe 1
        requests.get() shouldBe 1
      } finally server.stop(0)
    }
  }

  private def _reply(exchange: HttpExchange, status: Int, body: String, location: Option[String]): Unit = {
    try {
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=utf-8")
      location.foreach(value => exchange.getResponseHeaders.set("Location", value))
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }
}
