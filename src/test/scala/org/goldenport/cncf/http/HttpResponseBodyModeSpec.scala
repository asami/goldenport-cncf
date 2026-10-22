package org.goldenport.cncf.http

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.net.{InetSocketAddress, URL, URLConnection, URLStreamHandler}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.goldenport.bag.{Bag, BagBackend, BagMetadata, BinaryBag}
import org.goldenport.protocol.Property
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
final class HttpResponseBodyModeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import HttpResponseBodyModeSpec.*

  "Native HTTP response body mode" should {
    "materialize original bytes" which {
      for (bounded <- Vector(false, true); mime <- Vector(
        "text/plain; charset=UTF-8", "image/svg+xml; charset=ISO-8859-1",
        "application/json; charset=ISO-8859-1", "image/png", "application/octet-stream")) {
        s"retain exact $mime octets and response metadata with bounded=$bounded after peer shutdown" in {
          Given("a test-owned ephemeral peer returning zero and non-UTF8 octets with its actual MIME and status")
          val payload = Vector[Byte](0, -128, -1, 65, 0)
          _with_peer(Reply(201, mime, payload)) { peer =>
            When("the actual driver selects binary response representation")
            val response = peer.driver.get("/original", properties = _properties(bounded) :+
              Property("HTTP.RESPONSE-BODY-MODE", "binary", None))
            Then("the materialized BinaryBag retains every octet, the status and the original Content-Type header")
            response.code shouldBe 201
            response.mime.value shouldBe mime.takeWhile(_ != ';')
            response.headerValue("Content-Type") shouldBe Some(mime)
            response.headerValue("X-Response") shouldBe Some("original")
            response.getString shouldBe None
            response.bag shouldBe a[BinaryBag]
            _bytes(response.bag) shouldBe payload
            peer.requests.map(_.path) shouldBe Vector("/original")
            peer.stop()
            And("the same returned Bag remains readable with no live peer")
            _bytes(response.bag) shouldBe payload
          }
        }
      }

      for (bounded <- Vector(false, true); mode <- Vector(None, Some("auto"))) {
        s"retain ordinary UTF8 text semantics with mode=$mode and bounded=$bounded" in {
          Given("a UTF8 text response and either an absent or explicit auto selector")
          val text = "original café 資料"
          _with_peer(Reply(200, "text/plain; charset=UTF-8", text.getBytes(StandardCharsets.UTF_8).toVector)) { peer =>
            val properties = _properties(bounded) ++ mode.toVector.map(value => Property("http.response-body-mode", value, None))
            When("the actual driver reads the default representation")
            val response = peer.driver.get("/text", properties = properties)
            Then("the prior text response and UTF8 bytes remain available")
            response.code shouldBe 200
            response.getString shouldBe Some(text)
            response.getBinary shouldBe None
            _bytes(response.bag) shouldBe text.getBytes(StandardCharsets.UTF_8).toVector
            peer.requests.size shouldBe 1
          }
        }
      }

      "preserve bounded generated arbitrary octets including empty bodies with zero discards" in {
        Given("one sequentially reused loopback peer and finite payloads of at most 256 octets")
        val payloads = Gen.choose(0, 256).flatMap(length => Gen.listOfN(length, Gen.choose(-128, 127).map(_.toByte)))
        _with_peer(Reply(200, "text/plain; charset=UTF-8", Vector.empty)) { peer =>
          When("the actual total-timeout-bounded driver reads an empty body and at least one hundred generated binary responses")
          val emptyresponse = peer.driver.get("/empty", properties = _binary_properties(true))
          val emptybytes = _bytes(emptyresponse.bag)
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(payloads) { payload =>
            peer.setResponse(Reply(200, "text/plain; charset=UTF-8", payload.toVector))
            val response = peer.driver.get("/generated", properties = _binary_properties(true))
            response.code == 200 && response.getString.isEmpty && response.bag.isInstanceOf[BinaryBag] &&
              _bytes(response.bag) == payload.toVector
          })
          Then("all finite byte sequences survive as materialized binary content without discarded samples")
          emptybytes shouldBe empty
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
          peer.requests.size shouldBe result.succeeded + 1
        }
      }
    }

    "reject invalid representation before effects" which {
      for (bounded <- Vector(false, true)) {
        s"reject invalid, duplicate and non-string selectors before any GET with bounded=$bounded" in {
          Given("invalid values and duplicate case-insensitive keys containing only synthetic sensitive markers")
          val marker = "synthetic-secret-mode-marker"
          val invalid = Vector[Any]("", " ", "binary ", " auto", "BINARY", "Auto", marker, 1, true, null)
            .map(value => Vector(Property("http.response-body-mode", value, None))) ++ Vector(
              Vector(Property("http.response-body-mode", "binary", None), Property("HTTP.RESPONSE-BODY-MODE", "binary", None)),
              Vector(Property("http.response-body-mode", "auto", None), Property("http.response-body-mode", "binary", None)))
          _with_peer(Reply(200, "text/plain", Vector[Byte](0))) { peer =>
            When("each invalid selector reaches the actual native GET path")
            val failures = invalid.map(properties => intercept[IOException] {
              peer.driver.get("/invalid", properties = _properties(bounded) ++ properties)
            })
            Then("fixed sanitized errors precede all requests and disclose no supplied value")
            failures.foreach { failure =>
              failure.getMessage shouldBe (if (bounded) "Bounded HTTP exchange failed" else "Invalid HTTP response body mode")
              failure.getMessage should not include marker
              Option(failure.getCause) shouldBe None
            }
            peer.requests shouldBe empty
          }
        }
      }

      "reject an invalid mode before opening a Bag PUT body" in {
        Given("a counted request Bag and an invalid response selector")
        val opens = new AtomicInteger
        _with_peer(Reply(200, "text/plain", Vector[Byte](0))) { peer =>
          val body = _counted_bag(Vector[Byte](0, -1), opens)
          When("the actual driver receives the invalid binary PUT request")
          val failure = intercept[IOException] {
            peer.driver.putBag("/invalid", Some(body), Map.empty,
              _properties(false) :+ Property("http.response-body-mode", "synthetic-secret-mode-marker", None))
          }
          Then("mode validation fails before request-body acquisition or peer access")
          failure.getMessage shouldBe "Invalid HTTP response body mode"
          opens.get() shouldBe 0
          peer.requests shouldBe empty
        }
      }
    }

    "preserve native request and response policy" which {
      for (bounded <- Vector(false, true)) {
        s"accept the exact byte ceiling and reject overflow without partial success with bounded=$bounded" in {
          Given("three raw text-classified octets and an exact response ceiling")
          val payload = Vector[Byte](0, -1, -128)
          _with_peer(Reply(200, "text/plain; charset=UTF-8", payload)) { peer =>
            val properties = _binary_properties(bounded).filterNot(_.name == "http.max-response-bytes")
            When("one GET uses the exact ceiling and the next uses a ceiling smaller by one")
            val response = peer.driver.get("/exact", properties = properties :+ Property("http.max-response-bytes", "3", None))
            val failure = intercept[IOException] {
              peer.driver.get("/overflow", properties = properties :+ Property("http.max-response-bytes", "2", None))
            }
            Then("the exact response succeeds and overflow returns only the existing fixed transport failure")
            _bytes(response.bag) shouldBe payload
            failure.getMessage shouldBe (if (bounded) "Bounded HTTP exchange failed" else "HTTP response exceeds configured byte limit")
            peer.requests.map(_.path) shouldBe Vector("/exact", "/overflow")
          }
        }

        s"retain the original 307 without a target GET with bounded=$bounded" in {
          Given("a test-owned peer returning a redirect with raw text-classified bytes")
          val payload = Vector[Byte](0, -1)
          _with_peer(Reply(307, "text/plain; charset=UTF-8", payload)) { peer =>
            When("the binary response request uses the existing no-follow policy")
            val response = peer.driver.get("/redirect", properties = _binary_properties(bounded))
            Then("the original status, Location and bytes return after only the initial GET")
            response.code shouldBe 307
            response.headerValue("Location") shouldBe Some("/target")
            _bytes(response.bag) shouldBe payload
            peer.requests.map(_.path) shouldBe Vector("/redirect")
          }
        }
      }

      "preserve Bag PUT request bytes and one-shot count while selecting raw response bytes" in {
        Given("a binary request Bag and a text-classified raw response")
        val request = Vector[Byte](0, -1, -128, 65)
        val payload = Vector[Byte](0, -128, -1)
        _with_peer(Reply(201, "text/plain; charset=UTF-8", payload)) { peer =>
          When("the actual driver sends one Bag PUT with binary response representation")
          val response = peer.driver.putBag("/upload", Some(Bag.binary(request.toArray)),
            Map("Content-Type" -> "application/octet-stream"), _binary_properties(false))
          Then("the request remains exact and single-shot while the materialized response preserves all octets")
          peer.requests.size shouldBe 1
          peer.requests.head.method shouldBe "PUT"
          peer.requests.head.bytes shouldBe request
          response.code shouldBe 201
          response.bag shouldBe a[BinaryBag]
          _bytes(response.bag) shouldBe payload
        }
      }
    }
  }
}

private object HttpResponseBodyModeSpec {
  final case class Reply(status: Int, contenttype: String, bytes: Vector[Byte])
  final case class Request(method: String, path: String, bytes: Vector[Byte])

  final class Peer(initial: Reply) {
    private val _server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    private val _response = new AtomicReference[Reply](initial)
    private val _requests = new ConcurrentLinkedQueue[Request]
    val driver = new UrlConnectionHttpDriver(s"http://127.0.0.1:${_server.getAddress.getPort}")
    def requests: Vector[Request] = _requests.iterator().asScala.toVector
    def setResponse(response: Reply): Unit = _response.set(response)
    def stop(): Unit = _server.stop(0)

    _server.createContext("/", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        try {
          val in = exchange.getRequestBody
          val bytes = try in.readAllBytes().toVector finally in.close()
          _requests.add(Request(exchange.getRequestMethod, exchange.getRequestURI.getPath, bytes))
          val response = _response.get()
          exchange.getResponseHeaders.set("Content-Type", response.contenttype)
          exchange.getResponseHeaders.set("X-Response", "original")
          if (response.status == 307) exchange.getResponseHeaders.set("Location", "/target")
          exchange.sendResponseHeaders(response.status, if (response.bytes.isEmpty) -1L else response.bytes.size.toLong)
          if (response.bytes.nonEmpty) exchange.getResponseBody.write(response.bytes.toArray)
        } finally exchange.close()
      }
    })
    _server.start()
  }

  private def _with_peer(response: Reply)(body: Peer => Unit): Unit = {
    val peer = new Peer(response)
    try body(peer) finally peer.stop()
  }

  private def _properties(bounded: Boolean): Vector[Property] = Vector(
    Property("http.connect-timeout-ms", "5000", None), Property("http.read-timeout-ms", "5000", None),
    Property("http.max-response-bytes", "256", None), Property("http.follow-redirects", "false", None)) ++
    Option.when(bounded)(Property("http.total-timeout-ms", "5000", None)).toVector

  private def _binary_properties(bounded: Boolean): Vector[Property] =
    _properties(bounded) :+ Property("http.response-body-mode", "binary", None)

  private def _bytes(bag: Bag): Vector[Byte] = {
    val in = bag.openInputStream()
    try in.readAllBytes().toVector finally in.close()
  }

  private def _counted_bag(bytes: Vector[Byte], opens: AtomicInteger): Bag = {
    val handler = new URLStreamHandler {
      override def openConnection(url: URL): URLConnection = new URLConnection(url) {
        def connect(): Unit = ()
        override def getInputStream(): InputStream = {
          opens.incrementAndGet()
          new ByteArrayInputStream(bytes.toArray)
        }
      }
    }
    Bag.Instance(BagBackend.Url(new URL("counted", "input", 0, "/body", handler)), BagMetadata(size = Some(bytes.size.toLong)))
  }
}
