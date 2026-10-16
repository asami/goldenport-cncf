package org.goldenport.cncf.http

import java.io.{IOException, InterruptedIOException}
import java.net.{InetSocketAddress, SocketTimeoutException}
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.{Callable, ConcurrentLinkedQueue, CountDownLatch, Executors, Future, ScheduledFuture, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import cats.~>
import cats.free.Free
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.bag.Bag
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Property
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*
import scala.util.Try

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class BoundedHttpDriverSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "UrlConnectionHttpDriver bounded policy" should {
    "retain legacy dispatch (BH1)" which {
      "preserves ordinary GET, string POST, PUT and Bag POST without a total policy" in {
        Given("a real peer and the existing driver without the opt-in property")
        _with_fixture { fixture =>
          fixture.register("/legacy") { exchange =>
            _respond(exchange, 200, _utf8("legacy"), None)
          }
          val properties = Vector(
            Property("http.connect-timeout-ms", "1000", None),
            Property("http.read-timeout-ms", "1000", None),
            Property("http.max-response-bytes", "malformed-legacy-limit", None)
          )

          When("all four existing methods use the legacy branch")
          val responses = Vector(
            fixture.driver.get("/legacy", properties = properties),
            fixture.driver.post("/legacy", Some("post-é"), Map.empty, properties),
            fixture.driver.put("/legacy", Some("put-é"), Map.empty, properties),
            fixture.driver.postBag("/legacy", Some(Bag.text("bag-é", StandardCharsets.UTF_8)), Map.empty, properties)
          )

          Then("the methods, UTF-8 bodies and default text conversion remain observable")
          responses.foreach { response =>
            response.code shouldBe 200
            response.getString shouldBe Some("legacy")
          }
          fixture.requests.map(_.method) shouldBe Vector("GET", "POST", "PUT", "POST")
          fixture.requests.map(request => new String(request.body, StandardCharsets.UTF_8)) shouldBe
            Vector("", "post-é", "put-é", "bag-é")
        }
      }

      "preserves default and explicit legacy redirect policy" in {
        Given("a legacy redirect and a separate target resource")
        _with_fixture { fixture =>
          fixture.register("/redirect") { exchange =>
            _respond(exchange, 307, Array.emptyByteArray, headers = Vector("Location" -> "/target"))
          }
          fixture.register("/target") { exchange => _respond(exchange, 200, _utf8("target")) }

          When("legacy requests use their default and explicit false policies")
          val followed = fixture.driver.get("/redirect")
          val direct = fixture.driver.get("/redirect", properties = Vector(Property("http.follow-redirects", "false", None)))

          Then("the default follows once and explicit false returns the redirect")
          followed.code shouldBe 200
          followed.getString shouldBe Some("target")
          direct.code shouldBe 307
          fixture.hits("/target") shouldBe 1
        }
      }
    }

    "admit explicit policy before I/O (BH2)" which {
      "rejects invalid budgets, ceilings and redirect declarations for each actual method" in {
        Given("a peer hit counter, malformed present policies and non-openable unsupported Bag input")
        _with_fixture { fixture =>
          fixture.register("/admission") { exchange => _respond(exchange, 200, _utf8("unexpected")) }
          val invalidtotals = Vector("", " ", "fixture-secret", "0", "-1", "2147483648")
          val invalidlimits = Vector("", "fixture-secret", "-1", "2147483648")
          val policies = invalidtotals.map(_policy(_, "10")) ++
            invalidlimits.map(_policy("1000", _)) ++ Vector(
              Vector(Property("http.total-timeout-ms", "1000", None)),
              _policy() :+ Property("http.follow-redirects", "true", None),
              _policy() :+ Property("http.follow-redirects", "fixture-secret", None),
              _policy("0") :+ Property("http.total-timeout-ms", "1000", None)
            )

          policies.foreach { properties =>
            val operations = Vector[() => HttpResponse](
              () => fixture.driver.get("/admission", properties = properties),
              () => fixture.driver.post("/admission", Some("fixture-secret"), Map.empty, properties),
              () => fixture.driver.put("/admission", Some("fixture-secret"), Map.empty, properties),
              () => fixture.driver.postBag("/admission", Some(null.asInstanceOf[Bag]), Map.empty, properties)
            )
            operations.foreach { operation =>
              When("a present invalid policy reaches the runtime driver")
              val failure = intercept[IOException](operation())

              Then("it fails safely before any request or unsupported body access")
              failure.getMessage shouldBe "Invalid bounded HTTP policy"
              _safe_failure(failure, Vector("fixture-secret"))
              fixture.requests shouldBe empty
            }
          }
        }
      }

      "rejects admitted bounded PUT and Bag POST without falling back" in {
        Given("valid bounded properties and unsupported existing methods")
        _with_fixture { fixture =>
          fixture.register("/unsupported") { exchange => _respond(exchange, 200, _utf8("unexpected")) }
          val operations = Vector[() => HttpResponse](
            () => fixture.driver.put("/unsupported", Some("body"), Map.empty, _policy()),
            () => fixture.driver.postBag("/unsupported", Some(null.asInstanceOf[Bag]), Map.empty, _policy())
          )

          operations.foreach { operation =>
            When("the actual driver receives an unsupported bounded method")
            val failure = intercept[IOException](operation())

            Then("it reports the unsupported method before touching network or Bag")
            failure.getMessage shouldBe "Bounded HTTP method is unsupported"
            failure.getCause shouldBe null
            fixture.requests shouldBe empty
          }
        }
      }

      "retains initial public-network-only admission for bounded GET and POST" in {
        Given("a loopback peer excluded by existing public-network admission")
        _with_fixture { fixture =>
          fixture.register("/private") { exchange => _respond(exchange, 200, _utf8("unexpected")) }
          val properties = _policy() :+ Property("http.public-network-only", "true", None)
          val operations = Vector[() => HttpResponse](
            () => fixture.driver.get("/private", properties = properties),
            () => fixture.driver.post("/private", Some("body"), Map.empty, properties)
          )

          operations.foreach { operation =>
            When("the bounded runtime driver admits the initial destination")
            val failure = intercept[IOException](operation())

            Then("the destination is rejected without hitting the server")
            _safe_failure(failure, Vector(fixture.baseUrl, "/private"))
            fixture.requests shouldBe empty
          }
        }
      }
    }

    "use the selected runtime driver (BH3)" which {
      "round-trips actual UoW GET and POST with unchanged wire and response semantics" in {
        Given("a selected real driver, relative and absolute URLs and a UTF-8 echo peer")
        _with_fixture { fixture =>
          fixture.register("/wire") { exchange =>
            _respond(exchange, if (exchange.getRequestMethod == "POST") 201 else 200,
              _utf8("résultat-猫"), headers = Vector("X-Correlation" -> "wire-case"))
          }
          val properties = Vector(
            Property("HTTP.TOTAL-TIMEOUT-MS", " 2000 ", None),
            Property("HTTP.MAX-RESPONSE-BYTES", " 100 ", None),
            Property("http.follow-redirects", "false", None),
            Property("http.total-timeout-ms", "invalid-second-match", None)
          )
          val headers = Map("X-Correlation" -> "wire-case", "Content-Type" -> "text/plain; charset=utf-8")

          When("ordinary Free/ConsequenceT UoW operations reach the selected driver")
          val get = _dispatch(fixture.driver, UnitOfWorkOp.HttpGet("/wire", headers, properties)).TAKE
          val post = _dispatch(fixture.driver,
            UnitOfWorkOp.HttpPost(s"${fixture.baseUrl}/wire", Some("entrée-猫"), headers, properties)).TAKE

          Then("the wire method, body, headers, status and case-insensitive response headers survive")
          get.code shouldBe 200
          post.code shouldBe 201
          get.getString shouldBe Some("résultat-猫")
          post.getString shouldBe Some("résultat-猫")
          post.headerValue("x-CORRELATION") shouldBe Some("wire-case")
          fixture.requests.map(_.method) shouldBe Vector("GET", "POST")
          fixture.requests.map(_.header("x-correlation")) shouldBe Vector(Some("wire-case"), Some("wire-case"))
          fixture.requests.last.body shouldBe _utf8("entrée-猫")
        }
      }

      "passes the exact properties to a configured fake without creating transport" in {
        Given("a recording fake selected in RuntimeContext and deliberately invalid transport inputs")
        val response = FakeHttpDriver.okText("selected-fake").get("unused")
        val driver = new RecordingDriver(response)
        val properties = _policy("0", "-1") :+ Property("http.follow-redirects", "true", None)
        val headers = Map("Authorization" -> "fixture-secret")
        val path = "http://malformed fixture-secret/private"

        When("normal UoW GET and POST are interpreted with that fake selected")
        val get = _dispatch(driver, UnitOfWorkOp.HttpGet(path, headers, properties)).TAKE
        val post = _dispatch(driver, UnitOfWorkOp.HttpPost(path, Some("fixture-body"), headers, properties)).TAKE

        Then("the fake receives the original arguments and supplies both responses")
        get shouldBe response
        post shouldBe response
        driver.calls shouldBe Vector(
          RecordedCall("GET", path, None, headers, properties),
          RecordedCall("POST", path, Some("fixture-body"), headers, properties)
        )
      }
    }

    "bound the whole exchange (BH4)" which {
      "terminates while a real peer withholds response headers" in {
        Given("an observed request whose peer withholds headers behind a bounded release latch")
        _with_fixture { fixture =>
          val observed = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          val stopped = new CountDownLatch(1)
          fixture.register("/headers") { exchange =>
            observed.countDown()
            try {
              release.await(4, TimeUnit.SECONDS)
              _respond(exchange, 200, _utf8("late"))
            } finally {
              exchange.close()
              stopped.countDown()
            }
          }

          try {
            When("the driver waits with one 200 millisecond exchange budget")
            val waiting = fixture.submit(() => Try(fixture.driver.get("/headers", properties = _policy("200"))))
            observed.await(2, TimeUnit.SECONDS) shouldBe true
            val started = System.nanoTime()
            val result = waiting.get(2, TimeUnit.SECONDS)
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            Then("it times out before the peer is released and within a generous elapsed bound")
            result.failed.get shouldBe a[SocketTimeoutException]
            elapsed should be < 2000L
            release.getCount shouldBe 1L
            fixture.hits("/headers") shouldBe 1
          } finally {
            release.countDown()
            stopped.await(2, TimeUnit.SECONDS) shouldBe true
          }
        }
      }

      "cancels a multi-second trickle despite frequent body chunks" in {
        Given("a finite four-second peer stream with a chunk every 50 milliseconds")
        _with_fixture { fixture =>
          val observed = new CountDownLatch(1)
          val stopped = new CountDownLatch(1)
          val chunks = new AtomicInteger(0)
          fixture.register("/trickle") { exchange => fixture.trickle(exchange, observed, stopped, chunks) }

          When("the complete body must fit a single 250 millisecond budget")
          val waiting = fixture.submit(() => Try(fixture.driver.get("/trickle", properties = _policy("250"))))
          observed.await(2, TimeUnit.SECONDS) shouldBe true
          val started = System.nanoTime()
          val result = waiting.get(2, TimeUnit.SECONDS)
          val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
          val peerstopped = stopped.await(2, TimeUnit.SECONDS)

          Then("body arrival never resets the deadline and cancellation stops peer writes early")
          result.failed.get shouldBe a[SocketTimeoutException]
          elapsed should be < 2000L
          peerstopped shouldBe true
          chunks.get should be < 80
          fixture.hits("/trickle") shouldBe 1
        }
      }
    }

    "enforce raw-byte ceilings (BH5)" which {
      "accepts exact UTF-8 and binary ceilings and rejects a one-byte excess" in {
        Given("real complete UTF-8 and binary responses with exact raw-byte limits")
        _with_fixture { fixture =>
          val text = _utf8("é猫")
          val binary = Array[Byte](0, 0xff.toByte, 1, 2, 3)
          fixture.register("/text") { exchange => _respond(exchange, 200, text) }
          fixture.register("/binary") { exchange => _respond(exchange, 200, binary, Some("image/png")) }

          When("the driver accepts bodies exactly at each byte ceiling")
          val textresponse = fixture.driver.get("/text", properties = _policy(maxbytes = text.length.toString))
          val binaryresponse = fixture.driver.get("/binary", properties = _policy(maxbytes = binary.length.toString))

          Then("text is decoded after counting raw bytes and binary remains binary")
          textresponse.getString shouldBe Some("é猫")
          binaryresponse.getString shouldBe None
          _binary_bytes(binaryresponse) shouldBe binary

          Vector("/text" -> text.length, "/binary" -> binary.length).foreach { case (path, size) =>
            When("the peer sends one byte beyond the admitted maximum")
            val failure = intercept[IOException](fixture.driver.get(path, properties = _policy(maxbytes = (size - 1).toString)))

            Then("the exchange fails instead of exposing a partial response")
            _safe_failure(failure, Vector("é猫", fixture.baseUrl))
          }
        }
      }

      "admits empty 200 at zero and rejects one byte at zero" in {
        Given("an empty 200 response and a one-byte 200 response with zero admitted bytes")
        _with_fixture { fixture =>
          fixture.register("/empty200") { exchange => _respond(exchange, 200, Array.emptyByteArray) }
          fixture.register("/one") { exchange => _respond(exchange, 200, Array[Byte](1), Some("image/png")) }

          When("the actual driver consumes each 200 response under a zero ceiling")
          val empty = fixture.driver.get("/empty200", properties = _policy(maxbytes = "0"))
          val failure = intercept[IOException](fixture.driver.get("/one", properties = _policy(maxbytes = "0")))

          Then("empty success preserves 200 while the first excess byte fails")
          empty.code shouldBe 200
          empty.getString shouldBe Some("")
          _safe_failure(failure, Vector(fixture.baseUrl))
        }
      }

      "admits empty 204 at zero with the selected core status contract" in {
        Given("an empty 204 response and the explicit core source selection or default artifact")
        _with_fixture { fixture =>
          fixture.register("/empty204") { exchange => _respond(exchange, 204, Array.emptyByteArray) }
          val source = sys.env.get("TEXTUS_GOLDENPORT_CORE_CNCF_SOURCE_ROOT")

          When("the actual driver consumes the 204 response under a zero ceiling")
          val response = fixture.driver.get("/empty204", properties = _policy(maxbytes = "0"))

          Then("the body is empty and the status follows the selected core contract")
          response.getString shouldBe Some("")
          source match {
            case Some(_) => response.code shouldBe 204
            case None => response.code shouldBe HttpStatus.fromInt(204).getOrElse(HttpStatus.InternalServerError).code
          }
        }
      }

      "counts separate body deliveries against one aggregate ceiling and cancels excess" in {
        Given("a real multi-batch stream and a ceiling smaller than its eventual body")
        _with_fixture { fixture =>
          val observed = new CountDownLatch(1)
          val stopped = new CountDownLatch(1)
          val chunks = new AtomicInteger(0)
          fixture.register("/batches") { exchange => fixture.trickle(exchange, observed, stopped, chunks) }

          When("successive one-byte deliveries exceed a three-byte aggregate ceiling")
          val result = Try(fixture.driver.get("/batches", properties = _policy("2000", "3")))
          val peerstopped = stopped.await(2, TimeUnit.SECONDS)

          Then("the size failure cancels before the full stream or deadline")
          result.failed.get shouldBe a[IOException]
          result.failed.get should not be a[SocketTimeoutException]
          peerstopped shouldBe true
          chunks.get should be >= 4
          chunks.get should be < 80
        }
      }
    }

    "isolate redirects (BH6)" which {
      "returns a token-bearing GET or POST redirect without contacting its target" in {
        Given("a redirecting origin and a distinct server counting credential receipt")
        _with_fixture { target =>
          target.register("/credential-target") { exchange => _respond(exchange, 200, _utf8("unexpected")) }
          _with_fixture { origin =>
            val location = s"${target.baseUrl}/credential-target"
            origin.register("/redirect") { exchange =>
              _respond(exchange, 307, Array.emptyByteArray, headers = Vector("Location" -> location))
            }
            val headers = Map("Authorization" -> "Bearer fixture-secret")

            When("bounded GET and POST receive the redirect")
            val get = origin.driver.get("/redirect", headers, _policy())
            val post = origin.driver.post("/redirect", Some("fixture-body"), headers, _policy())

            Then("both redirects remain observable and the target receives no request or token")
            Vector(get, post).foreach { response =>
              response.code shouldBe 307
              response.headerValue("location") shouldBe Some(location)
            }
            origin.requests.map(_.header("authorization")) shouldBe Vector.fill(2)(Some("Bearer fixture-secret"))
            target.requests shouldBe empty
          }
        }
      }
    }

    "respect caller interruption (BH7)" which {
      "cancels an exchange waiting at a real peer and restores the caller flag" in {
        Given("an owned caller thread and an observed request stalled at the peer")
        _with_fixture { fixture =>
          val observed = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          val stopped = new CountDownLatch(1)
          fixture.register("/interrupt") { exchange =>
            observed.countDown()
            try {
              release.await(4, TimeUnit.SECONDS)
              _respond(exchange, 200, _utf8("late"))
            } finally {
              exchange.close()
              stopped.countDown()
            }
          }
          val failure = new AtomicReference[Throwable]()
          val interrupted = new AtomicBoolean(false)
          val finished = new CountDownLatch(1)
          val caller = new Thread(() => {
            try fixture.driver.get("/interrupt", properties = _policy("3000"))
            catch { case error: IOException => failure.set(error) }
            finally {
              interrupted.set(Thread.currentThread().isInterrupted)
              finished.countDown()
            }
            ()
          }, "bounded-http-interrupted-caller")

          try {
            When("the waiting caller is interrupted after the peer sees the request")
            caller.start()
            observed.await(2, TimeUnit.SECONDS) shouldBe true
            caller.interrupt()
            val completed = finished.await(2, TimeUnit.SECONDS)
            caller.join(2000)

            Then("safe interruption ends the caller and retains its interrupt flag")
            completed shouldBe true
            caller.isAlive shouldBe false
            failure.get shouldBe a[InterruptedIOException]
            failure.get should not be a[SocketTimeoutException]
            interrupted.get shouldBe true
            _safe_failure(failure.get, Vector(fixture.baseUrl, "/interrupt"))
          } finally {
            release.countDown()
            caller.interrupt()
            caller.join(2000)
            stopped.await(2, TimeUnit.SECONDS) shouldBe true
            caller.isAlive shouldBe false
          }
        }
      }

      "rejects an already interrupted caller before sending" in {
        Given("an owned thread already marked interrupted and an idle real peer")
        _with_fixture { fixture =>
          fixture.register("/preinterrupted") { exchange => _respond(exchange, 200, _utf8("unexpected")) }
          val failure = new AtomicReference[Throwable]()
          val interrupted = new AtomicBoolean(false)
          val caller = new Thread(() => {
            Thread.currentThread().interrupt()
            try fixture.driver.get("/preinterrupted", properties = _policy())
            catch { case error: IOException => failure.set(error) }
            finally interrupted.set(Thread.currentThread().isInterrupted)
            ()
          }, "bounded-http-preinterrupted-caller")

          try {
            When("that caller enters bounded transport")
            caller.start()
            caller.join(2000)

            Then("the failure is safe, the flag remains set and no request is sent")
            caller.isAlive shouldBe false
            failure.get shouldBe a[InterruptedIOException]
            interrupted.get shouldBe true
            fixture.requests shouldBe empty
          } finally {
            caller.interrupt()
            caller.join(2000)
            caller.isAlive shouldBe false
          }
        }
      }
    }

    "preserve HTTP and safe transport outcomes (BH8)" which {
      "returns authentication and server error statuses without application retries" in {
        Given("actual 401, 403 and 500 responses through normal UoW dispatch")
        _with_fixture { fixture =>
          Vector(401, 403, 500).foreach { code =>
            fixture.register(s"/status/$code") { exchange => _respond(exchange, code, _utf8(s"status-$code")) }
          }

          Vector(401, 403, 500).foreach { code =>
            When("the selected bounded driver receives the remote HTTP outcome")
            val result = _dispatch(fixture.driver, UnitOfWorkOp.HttpGet(s"/status/$code", properties = _policy()))

            Then("UoW preserves a response and its exact status and body with one request")
            result.isSuccess shouldBe true
            result.TAKE.code shouldBe code
            result.TAKE.getString shouldBe Some(s"status-$code")
            fixture.hits(s"/status/$code") shouldBe 1
          }
        }
      }

      "sanitizes abrupt transport and malformed endpoint, header and response metadata failures" in {
        Given("secret-bearing inputs, an abruptly closed POST peer and invalid response charset")
        _with_fixture { fixture =>
          val secret = "bounded-fixture-secret"
          fixture.register("/abrupt") { exchange => exchange.close() }
          fixture.register("/metadata") { exchange =>
            _respond(exchange, 200, _utf8(secret), Some(s"text/plain; charset=$secret"))
          }
          val operations = Vector[() => HttpResponse](
            () => fixture.driver.post("/abrupt", Some(secret), Map("Authorization" -> secret), _policy()),
            () => fixture.driver.get(s"http://127.0.0.1:$secret/private", properties = _policy()),
            () => fixture.driver.post("/never", Some(secret), Map(s"X-$secret\n" -> secret), _policy()),
            () => fixture.driver.get("/metadata", Map("Authorization" -> secret), _policy())
          )

          operations.foreach { operation =>
            When("the actual bounded transport or response conversion fails")
            val failure = intercept[IOException](operation())

            Then("message, rendering and the entire cause chain exclude private inputs")
            _safe_failure(failure, Vector(secret, fixture.baseUrl, "/private", "/metadata", "/abrupt"))
          }
          fixture.hits("/abrupt") shouldBe 1
          fixture.hits("/never") shouldBe 0
          fixture.hits("/metadata") shouldBe 1

          When("normal UoW dispatch captures a malformed bounded request")
          val result = _dispatch(fixture.driver,
            UnitOfWorkOp.HttpGet(s"http://127.0.0.1:$secret/private", properties = _policy()))

          Then("the ordinary Consequence failure remains free of the raw input")
          result.isSuccess shouldBe false
          result.toString should not include secret
        }
      }
    }

    "round-trip generated admitted inputs (BH9)" which {
      "checks at least fifty successful real GET and POST exchanges with zero discarded cases" in {
        Given("finite generated non-ASCII payloads, method choices and exact or larger byte ceilings")
        val inputs = for {
          length <- Gen.choose(0, 24)
          suffix <- Gen.listOfN(length, Gen.oneOf('a', 'Z', '0', 'é', '猫'))
          post <- Gen.oneOf(true, false)
          extra <- Gen.choose(0, 8)
        } yield ("é猫" + suffix.mkString, post, extra)
        _with_fixture { fixture =>
          fixture.register("/roundtrip/") { exchange =>
            val request = fixture.requests.last
            val bytes = if (request.method == "POST") request.body
              else Base64.getDecoder.decode(request.header("x-payload").get)
            _respond(exchange, if (request.method == "POST") 201 else 200, bytes,
              headers = Vector("X-Correlation" -> request.header("x-correlation").get))
          }
          val correlation = new AtomicInteger(0)
          val property = Prop.forAll(inputs) { case (payload, post, extra) =>
            val bytes = _utf8(payload)
            val key = correlation.incrementAndGet().toString
            val path = s"/roundtrip/$key"
            val headers = Map("X-Correlation" -> key, "X-Payload" -> Base64.getEncoder.encodeToString(bytes))
            val properties = _policy(maxbytes = (bytes.length + extra).toString)

            When("a generated case crosses the real bounded driver boundary")
            val response = if (post) fixture.driver.post(path, Some(payload), headers, properties)
              else fixture.driver.get(path, headers, properties)

            Then("the accepted complete wire bytes, status and correlation match the generated case")
            response.code shouldBe (if (post) 201 else 200)
            response.getString shouldBe Some(payload)
            response.headerValue("x-correlation") shouldBe Some(key)
            fixture.requests.last.method shouldBe (if (post) "POST" else "GET")
            if (post) fixture.requests.last.body shouldBe bytes

            When("the same generated body is offered one byte above a narrower ceiling")
            val narrowed = _policy(maxbytes = (bytes.length - 1).toString)
            val failure = intercept[IOException] {
              if (post) fixture.driver.post(path, Some(payload), headers, narrowed)
              else fixture.driver.get(path, headers, narrowed)
            }

            Then("raw-byte admission rejects the complete response without a partial value")
            _safe_failure(failure, Vector(payload, fixture.baseUrl))
            true
          }

          When("ScalaCheck evaluates sixty successful generated real exchanges")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), property)

          Then("all generated cases succeed without discards and each uses actual HTTP")
          checked.passed shouldBe true
          checked.succeeded should be >= 50
          checked.discarded shouldBe 0
          fixture.requests.size shouldBe checked.succeeded * 2
        }
      }
    }
  }

  private def _policy(timeout: String = "2000", maxbytes: String = "4096"): Vector[Property] =
    Vector(Property("http.total-timeout-ms", timeout, None), Property("http.max-response-bytes", maxbytes, None))

  private def _utf8(value: String): Array[Byte] = value.getBytes(StandardCharsets.UTF_8)

  private def _binary_bytes(response: HttpResponse): Array[Byte] = {
    val stream = response.getBinary.get.openInputStream()
    try stream.readAllBytes()
    finally stream.close()
  }

  private def _safe_failure(failure: Throwable, secrets: Vector[String]): Unit = {
    failure shouldBe a[IOException]
    failure.getCause shouldBe null
    failure.getSuppressed shouldBe empty
    secrets.foreach { secret => failure.toString should not include secret }
  }

  private def _dispatch(driver: HttpDriver, operation: UnitOfWorkOp[HttpResponse]): Consequence[HttpResponse] = {
    val base = ExecutionContext.create()
    val runtime = new RuntimeContext(
      core = RuntimeContext.core(name = "bounded-http-spec", parent = None,
        observabilitycontext = base.observability, httpdriveroption = Some(driver)),
      unitofworksupplier = () => throw new UnsupportedOperationException("Unused specification supplier"),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("Unused specification interpreter")
      },
      commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (), token = "bounded-http-spec"
    )
    val context = ExecutionContext.withRuntimeContext(base, runtime)
    val datastore = DataStore.noop()
    val uow = new UnitOfWork(context, EventEngine.noop(datastore))
    val program = ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](operation))
    new UnitOfWorkInterpreter(uow).run(program)
  }

  private def _respond(
    exchange: HttpExchange,
    status: Int,
    bytes: Array[Byte],
    contenttype: Option[String] = Some("text/plain; charset=utf-8"),
    headers: Vector[(String, String)] = Vector.empty
  ): Unit = {
    try {
      contenttype.foreach(exchange.getResponseHeaders.add("Content-Type", _))
      headers.foreach { case (key, value) => exchange.getResponseHeaders.add(key, value) }
      exchange.sendResponseHeaders(status, if (bytes.isEmpty) -1L else bytes.length.toLong)
      if (bytes.nonEmpty) exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }

  private def _with_fixture(f: Fixture => Unit): Unit = {
    val fixture = new Fixture
    try {
      fixture.start()
      f(fixture)
    } finally fixture.close()
  }

  private final case class CapturedRequest(
    method: String, path: String, body: Array[Byte], headers: Vector[(String, String)]
  ) {
    def header(name: String): Option[String] =
      headers.collectFirst { case (key, value) if key.equalsIgnoreCase(name) => value }
  }

  private final case class RecordedCall(
    method: String, path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]
  )

  private final class RecordingDriver(response: HttpResponse) extends HttpDriver {
    private var _calls = Vector.empty[RecordedCall]
    def calls: Vector[RecordedCall] = _calls
    def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _calls :+= RecordedCall("GET", path, None, headers, properties)
      response
    }
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _calls :+= RecordedCall("POST", path, body, headers, properties)
      response
    }
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _calls :+= RecordedCall("PUT", path, body, headers, properties)
      response
    }
  }

  private final class Fixture {
    private val _executor = Executors.newCachedThreadPool()
    private val _scheduler = Executors.newSingleThreadScheduledExecutor()
    private val _server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    private val _requests = new ConcurrentLinkedQueue[CapturedRequest]()
    private val _exchanges = new ConcurrentLinkedQueue[HttpExchange]()
    private val _tasks = new ConcurrentLinkedQueue[ScheduledFuture[?]]()
    private val _driver = new UrlConnectionHttpDriver(baseUrl)

    def baseUrl: String = s"http://127.0.0.1:${_server.getAddress.getPort}"
    def driver: UrlConnectionHttpDriver = _driver
    def requests: Vector[CapturedRequest] = _requests.iterator().asScala.toVector
    def hits(path: String): Int = requests.count(_.path == path)

    def start(): Unit = {
      _server.setExecutor(_executor)
      _server.start()
    }

    def register(path: String)(handler: HttpExchange => Unit): Unit = {
      _server.createContext(path, new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          _exchanges.add(exchange)
          val input = exchange.getRequestBody
          val body = try input.readAllBytes() finally input.close()
          val headers = exchange.getRequestHeaders.entrySet().asScala.toVector.flatMap { entry =>
            entry.getValue.asScala.toVector.map(entry.getKey -> _)
          }
          _requests.add(CapturedRequest(exchange.getRequestMethod, exchange.getRequestURI.getPath, body, headers))
          try handler(exchange)
          catch { case _: IOException => exchange.close() }
        }
      })
      ()
    }

    def submit[A](task: () => A): Future[A] =
      _executor.submit(new Callable[A] { def call(): A = task() })

    def trickle(
      exchange: HttpExchange, observed: CountDownLatch, stopped: CountDownLatch, chunks: AtomicInteger
    ): Unit = {
      exchange.getResponseHeaders.add("Content-Type", "text/plain; charset=utf-8")
      exchange.sendResponseHeaders(200, 0)
      val output = exchange.getResponseBody
      output.write(65)
      output.flush()
      chunks.incrementAndGet()
      observed.countDown()
      val terminal = new AtomicBoolean(false)
      val scheduled = new AtomicReference[ScheduledFuture[?]]()
      def _finish_(): Unit = {
        terminal.set(true)
        exchange.close()
        stopped.countDown()
        Option(scheduled.get).foreach(_.cancel(false))
      }
      val task = _scheduler.scheduleAtFixedRate(new Runnable {
        def run(): Unit = {
          if (!terminal.get) {
            try {
              output.write(65)
              output.flush()
              if (chunks.incrementAndGet() >= 80) _finish_()
            } catch { case _: IOException => _finish_() }
          }
        }
      }, 50, 50, TimeUnit.MILLISECONDS)
      scheduled.set(task)
      _tasks.add(task)
      if (terminal.get) task.cancel(false)
    }

    def close(): Unit = {
      _tasks.iterator().asScala.foreach(_.cancel(true))
      _exchanges.iterator().asScala.foreach(_.close())
      _server.stop(0)
      _scheduler.shutdownNow()
      _executor.shutdownNow()
      _scheduler.awaitTermination(2, TimeUnit.SECONDS) shouldBe true
      _executor.awaitTermination(2, TimeUnit.SECONDS) shouldBe true
    }
  }
}
