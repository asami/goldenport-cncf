package org.goldenport.cncf.http

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.net.{InetSocketAddress, URL, URLConnection, URLStreamHandler}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.goldenport.bag.{Bag, BagBackend, BagMetadata}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
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
final class HttpPutBagSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import HttpPutBagSpec.*

  "Native binary PUT driver" should {
    "stream request bytes" which {
      "preserves exact octets, method, length, headers and materialized 200/201 responses" in {
        Vector(200, 201).foreach { status =>
          Given(s"a test-owned ephemeral peer returning $status and binary input including zero and non-UTF8 octets")
          val payload = Array[Byte](0, -128, -1, 65, 0)
          val opens = new AtomicInteger
          val closes = new AtomicInteger
          _with_peer(status) { peer =>
            val body = _counted_bag(payload, Some(payload.length.toLong), opens, closes)
            When("the actual URL connection driver performs a fixed-length binary PUT")
            val response = peer.driver.putBag("/upload", Some(body), Map("Content-Type" -> "application/octet-stream", "X-Trace" -> "same"), _properties)
            val received = peer.requests
            Then("the original bytes and headers arrive once and input closes exactly once")
            response.code shouldBe status
            received.size shouldBe 1
            received.head.method shouldBe "PUT"
            received.head.length shouldBe Some(payload.length.toString)
            received.head.transfer shouldBe None
            received.head.trace shouldBe Some("same")
            received.head.contenttype shouldBe Some("application/octet-stream")
            received.head.bytes shouldBe payload.toVector
            opens.get() shouldBe 1
            closes.get() shouldBe 1
            response.headerValue("X-Response") shouldBe Some("original")
            peer.stop()
            _bytes(response.bag).toVector shouldBe Vector[Byte](0, -1, -128)
          }
        }
      }

      "uses chunked streaming for unknown or negative size and zero-length mode for absent bodies" in {
        Vector(None, Some(-1L)).foreach { size =>
          Given(s"a counted binary Bag with size observation $size")
          val opens = new AtomicInteger
          val closes = new AtomicInteger
          _with_peer() { peer =>
            val payload = Array[Byte](0, -1)
            val bag = _counted_bag(payload, size, opens, closes)
            When("the driver sends the body without a usable fixed length")
            val response = peer.driver.putBag("/upload", Some(bag), Map.empty, _properties)
            Then("the peer receives chunked exact bytes and the borrowed stream closes")
            response.code shouldBe 200
            peer.requests.head.transfer shouldBe Some("chunked")
            peer.requests.head.length shouldBe None
            peer.requests.head.bytes shouldBe payload.toVector
            opens.get() shouldBe 1
            closes.get() shouldBe 1
          }
        }
        Given("a binary PUT with absent content")
        _with_peer() { peer =>
          When("the driver sends the absent body")
          val response = peer.driver.putBag("/upload", None, Map.empty, _properties)
          Then("the request has zero fixed length and no content")
          response.code shouldBe 200
          peer.requests.head.length shouldBe Some("0")
          peer.requests.head.bytes shouldBe empty
        }
      }

      "preserves bounded generated in-memory payloads on the actual loopback wire without discards" in {
        Given("a loopback peer and bounded payloads of arbitrary octets including empty input")
        val payloads = Gen.choose(0, 256).flatMap(length => Gen.listOfN(length, Gen.choose(-128, 127).map(_.toByte)))
        _with_peer() { peer =>
          When("at least one hundred in-memory Bags are streamed through the actual driver")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(payloads) { payload =>
            val response = peer.driver.putBag("/upload", Some(Bag.binary(payload.toArray)), Map.empty, _properties)
            val request = peer.requests.last
            response.code == 200 && request.method == "PUT" && request.bytes == payload.toVector &&
              request.length.contains(payload.length.toString)
          })
          Then("all generated byte sequences survive with zero discards")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }

    "preserve finite HTTP policy" which {
      "returns the original redirect without replaying the PUT at its target" in {
        Given("a peer whose upload response redirects to another test-owned route")
        _with_peer(307) { peer =>
          When("binary PUT uses the explicit no-follow policy")
          val response = peer.driver.putBag("/upload", Some(Bag.binary(Array[Byte](0, -1))), Map.empty, _properties)
          Then("the original redirect returns and only the initial PUT is observed")
          response.code shouldBe 307
          response.headerValue("Location") shouldBe Some("/target")
          peer.requests.map(_.path) shouldBe Vector("/upload")
        }
      }

      "rejects an oversized response after closing the upload stream" in {
        Given("a peer response of three bytes and an admitted response ceiling of two")
        val opens = new AtomicInteger
        val closes = new AtomicInteger
        _with_peer() { peer =>
          val bag = _counted_bag(Array[Byte](0, -1), Some(2), opens, closes)
          When("the actual driver consumes the response")
          val failure = intercept[IOException] {
            peer.driver.putBag("/upload", Some(bag), Map.empty, _properties :+ Property("http.max-response-bytes", "2", None))
          }
          Then("the finite response ceiling fails without returning a truncated response and upload input is closed once")
          failure.getMessage shouldBe "HTTP response exceeds configured byte limit"
          peer.requests.size shouldBe 1
          opens.get() shouldBe 1
          closes.get() shouldBe 1
        }
      }

      "rejects explicit total-timeout policy before connection or body acquisition" in {
        Given("a valid total-timeout declaration unsupported by binary PUT")
        val opens = new AtomicInteger
        val closes = new AtomicInteger
        _with_peer() { peer =>
          val body = _counted_bag(Array[Byte](1), Some(1), opens, closes)
          val properties = _properties ++ Vector(Property("http.total-timeout-ms", "1000", None), Property("http.max-response-bytes", "100", None))
          When("the driver receives the explicitly bounded binary PUT")
          val failure = intercept[IOException](peer.driver.putBag("/upload", Some(body), Map.empty, properties))
          Then("the fixed unsupported error precedes all peer and input access")
          failure.getMessage shouldBe "Bounded HTTP method is unsupported"
          Option(failure.getCause) shouldBe None
          peer.requests shouldBe empty
          opens.get() shouldBe 0
          closes.get() shouldBe 0
        }
      }
    }

    "close borrowed inputs on failure" which {
      "closes an opened input when connection/output acquisition fails" in {
        Given("a stopped test-owned peer and a counted Bag opened before output acquisition")
        val opens = new AtomicInteger
        val closes = new AtomicInteger
        _with_peer() { peer =>
          val body = _counted_bag(Array[Byte](0, -1), Some(2), opens, closes)
          peer.stop()
          When("the driver cannot acquire the request output stream")
          intercept[IOException](peer.driver.putBag("/upload", Some(body), Map.empty, _properties))
          Then("the borrowed input opened once and closed exactly once")
          opens.get() shouldBe 1
          closes.get() shouldBe 1
        }
      }

      "closes an input whose copy fails after output acquisition" in {
        Given("a counted input that throws during copying to a live peer")
        val opens = new AtomicInteger
        val closes = new AtomicInteger
        _with_peer() { peer =>
          val body = _counted_bag(Array[Byte](0, -1), Some(2), opens, closes, broken = true)
          When("the actual driver attempts to stream the body")
          intercept[IOException](peer.driver.putBag("/upload", Some(body), Map.empty, _properties))
          Then("the failed borrowed input is closed exactly once")
          opens.get() shouldBe 1
          closes.get() shouldBe 1
        }
      }
    }

    "keep injected driver compatibility" which {
      "allows legacy drivers and returns the configured fake response without opening the Bag" in {
        Given("a legacy driver implementing only the original abstract methods and a configured fake")
        val response = HttpResponse.Binary(HttpStatus.Ok, ContentType(MimeType("application/octet-stream"), None), Bag.binary(Array[Byte](0)))
        val legacy = new HttpDriver {
          def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = response
          def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = response
          def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = response
        }
        val fake = new FakeHttpDriver(response)
        val opens = new AtomicInteger
        val closes = new AtomicInteger
        val body = _counted_bag(Array[Byte](0), Some(1), opens, closes)
        When("binary PUT is requested from both injected drivers")
        val unsupported = intercept[UnsupportedOperationException](legacy.putBag("/upload", Some(body), Map.empty))
        val actual = fake.putBag("/upload", Some(body), Map.empty)
        Then("the default fails with a fixed message while the fake preserves response identity without body I/O")
        unsupported.getMessage shouldBe "HttpDriver.putBag is not implemented by this driver"
        actual should be theSameInstanceAs response
        opens.get() shouldBe 0
        closes.get() shouldBe 0
      }
    }
  }
}

private object HttpPutBagSpec {
  private val _properties = Vector(Property("http.connect-timeout-ms", "1000", None),
    Property("http.read-timeout-ms", "1000", None), Property("http.follow-redirects", "false", None))

  final case class Request(method: String, path: String, length: Option[String], transfer: Option[String],
    trace: Option[String], contenttype: Option[String], bytes: Vector[Byte])

  final class Peer(status: Int) {
    private val _server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    private val _requests = new ConcurrentLinkedQueue[Request]
    val driver = new UrlConnectionHttpDriver(s"http://127.0.0.1:${_server.getAddress.getPort}")
    def requests: Vector[Request] = _requests.iterator().asScala.toVector
    def stop(): Unit = _server.stop(0)

    _server.createContext("/", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        try {
          val headers = exchange.getRequestHeaders
          val in = exchange.getRequestBody
          val bytes = try in.readAllBytes().toVector finally in.close()
          _requests.add(Request(exchange.getRequestMethod, exchange.getRequestURI.getPath,
            Option(headers.getFirst("Content-Length")), Option(headers.getFirst("Transfer-Encoding")),
            Option(headers.getFirst("X-Trace")), Option(headers.getFirst("Content-Type")), bytes))
          exchange.getResponseHeaders.set("Content-Type", "application/octet-stream")
          exchange.getResponseHeaders.set("X-Response", "original")
          if (status == 307) exchange.getResponseHeaders.set("Location", "/target")
          val response = Array[Byte](0, -1, -128)
          exchange.sendResponseHeaders(status, response.length.toLong)
          exchange.getResponseBody.write(response)
        } catch {
          case _: IOException => () // A deliberately failed upload can terminate the peer input.
        } finally exchange.close()
      }
    })
    _server.start()
  }

  private def _with_peer(status: Int = 200)(body: Peer => Unit): Unit = {
    val peer = new Peer(status)
    try body(peer) finally peer.stop()
  }

  private def _counted_bag(bytes: Array[Byte], size: Option[Long], opens: AtomicInteger, closes: AtomicInteger, broken: Boolean = false): Bag = {
    val handler = new URLStreamHandler {
      override def openConnection(url: URL): URLConnection = new URLConnection(url) {
        def connect(): Unit = ()
        override def getInputStream(): InputStream = {
          opens.incrementAndGet()
          new ByteArrayInputStream(bytes) {
            override def read(buffer: Array[Byte], offset: Int, length: Int): Int = {
              if (broken) throw new IOException("Fixture copy failure")
              super.read(buffer, offset, length)
            }
            override def close(): Unit = {
              closes.incrementAndGet()
              super.close()
            }
          }
        }
      }
    }
    Bag.Instance(BagBackend.Url(new URL("counted", "input", 0, "/body", handler)), BagMetadata(size = size))
  }

  private def _bytes(bag: Bag): Array[Byte] = {
    val in = bag.openInputStream()
    try in.readAllBytes() finally in.close()
  }
}
