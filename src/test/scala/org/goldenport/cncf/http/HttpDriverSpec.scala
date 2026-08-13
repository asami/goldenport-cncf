package org.goldenport.cncf.http

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.{CommandExecutionMode, CommandInterfaceMode}
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.Try

/*
 * @since   Apr. 25, 2026
 *  version Jul. 21, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpDriverSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _e9 = afterWord(
    "in spec:action-execution-semantics, example:E9, rules:R5,R6,R13, phase:57.2, slice:AES-05A"
  )

  "UrlConnectionHttpDriver" should {
    "preserve response headers from the server" in {
      Given("an HTTP server that returns a debug job header")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/ping", new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          val bytes = "pong".getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "text/plain; charset=utf-8")
          exchange.getResponseHeaders.add("X-Textus-Job-Id", "job-url-1")
          exchange.sendResponseHeaders(200, bytes.length)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      })
      server.start()

      try {
        When("the URL connection driver executes the request")
        val port = server.getAddress.getPort
        val driver = new UrlConnectionHttpDriver(s"http://127.0.0.1:${port}")
        val response = driver.get("/ping")

        Then("the protocol-level response exposes the header case-insensitively")
        response.code shouldBe 200
        response.getString shouldBe Some("pong")
        response.headerValue("x-textus-job-id") shouldBe Some("job-url-1")
      } finally {
        server.stop(0)
      }
    }

    "preserve a successful 201 Created response" in {
      Given("an HTTP server that creates RDF data")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/data", new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          val bytes = "{\"count\":2}".getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(201, bytes.length)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      })
      server.start()

      try {
        When("the URL connection driver posts a provider dataset")
        val port = server.getAddress.getPort
        val driver = new UrlConnectionHttpDriver(s"http://127.0.0.1:$port")
        val response = driver.post("/data", Some("{}"), Map("Content-Type" -> "application/json"))

        Then("the successful Created status is preserved")
        response.code shouldBe 201
        response.getString shouldBe Some("{\"count\":2}")
      } finally {
        server.stop(0)
      }
    }

    "preserve non-text responses as binary bodies" in {
      Given("an HTTP server that returns a PNG payload")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val payload = Array[Byte](0x89.toByte, 0x50.toByte, 0x4e.toByte, 0x47.toByte)
      server.createContext("/map.png", new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          exchange.getResponseHeaders.add("Content-Type", "image/png")
          exchange.sendResponseHeaders(200, payload.length)
          exchange.getResponseBody.write(payload)
          exchange.close()
        }
      })
      server.start()

      try {
        When("the URL connection driver executes the request")
        val port = server.getAddress.getPort
        val driver = new UrlConnectionHttpDriver(s"http://127.0.0.1:${port}")
        val response = driver.get("/map.png")

        Then("the protocol-level response exposes the payload as binary")
        response.code shouldBe 200
        response.getString shouldBe None
        val in = response.getBinary.get.openInputStream()
        try
          in.readAllBytes() shouldBe payload
        finally
          in.close()
      } finally {
        server.stop(0)
      }
    }

    "return redirects without following them when request policy disables redirect handling" in {
      Given("an HTTP server whose first response redirects to a second resource")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      var targetcalls = 0
      server.createContext("/start", new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          exchange.getResponseHeaders.add("Location", "/target")
          exchange.sendResponseHeaders(307, -1)
          exchange.close()
        }
      })
      server.createContext("/target", new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          targetcalls += 1
          exchange.sendResponseHeaders(204, -1)
          exchange.close()
        }
      })
      server.start()

      try {
        When("the request disables automatic redirect handling")
        val port = server.getAddress.getPort
        val driver = new UrlConnectionHttpDriver(s"http://127.0.0.1:${port}")
        val response = driver.get(
          "/start",
          properties = Vector(Property("http.follow-redirects", "false", None))
        )

        Then("the redirect remains observable and the target receives no request")
        response.code shouldBe 307
        response.headerValue("Location") shouldBe Some("/target")
        targetcalls shouldBe 0
      } finally {
        server.stop(0)
      }
    }

    "stop materializing a response at the configured byte ceiling" in {
      Given("an HTTP server whose body is larger than the admitted response budget")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val payload = "oversized".getBytes(StandardCharsets.UTF_8)
      server.createContext("/large", new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          exchange.getResponseHeaders.add("Content-Type", "text/plain; charset=utf-8")
          exchange.sendResponseHeaders(200, payload.length)
          exchange.getResponseBody.write(payload)
          exchange.close()
        }
      })
      server.start()

      try {
        When("the driver reads with a smaller explicit maximum")
        val port = server.getAddress.getPort
        val driver = new UrlConnectionHttpDriver(s"http://127.0.0.1:${port}")

        Then("the transport aborts before returning a partially accepted response")
        an[java.io.IOException] shouldBe thrownBy {
          driver.get(
            "/large",
            properties = Vector(Property("http.max-response-bytes", "4", None))
          )
        }
      } finally {
        server.stop(0)
      }
    }

    "recheck public-network policy at the transport boundary" in {
      Given("a loopback endpoint and a runtime-owned public-network-only property")
      val driver = new UrlConnectionHttpDriver("http://127.0.0.1:9")

      When("the request reaches URL connection admission")
      val thrown = Try(driver.get(
        "/internal",
        properties = Vector(Property("http.public-network-only", "true", None))
      )).failed.toOption

      Then("the private target is rejected before a connection is opened")
      thrown should not be empty
      thrown.get shouldBe a[java.io.IOException]
    }
  }

  "LoopbackHttpDriver" should {
    "E9 execution response transport metadata" must _e9 {
    "project explicit Direct metadata headers without a Job identifier" in {
      Given("a loopback server backed by the default subsystem and a direct debug query")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val driver = new LoopbackHttpDriver(
        LoopbackHttpServer.fromEngine(new HttpExecutionEngine(subsystem))
      )

      When("the query is executed without trace-job admission")
        val response = driver.get("/org.goldenport.cncf.Debug/http/echo")

      Then("the direct execution headers are explicit and no Job header is inferred")
      response.code shouldBe 200
      response.headerValue("X-Textus-Execution-Mode") shouldBe Some("Sync")
      response.headerValue("X-Textus-Execution-Result") shouldBe Some("direct")
      response.headerValue("X-Textus-Job-Id") shouldBe None
    }

    "remove a stale Job header when explicit Direct metadata has no Job identifier" in {
      Given("a loopback server whose direct query response carries a stale Job header")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val driver = new LoopbackHttpDriver(
        LoopbackHttpServer.fromEngine(new HttpExecutionEngine(subsystem))
      )

      When("the direct query is executed without trace-job admission")
        val response = driver.get("/org.goldenport.cncf.Debug/http/echo?x-textus-job-id=stale-job")

      Then("the authoritative Direct metadata removes every stale Job header")
      response.code shouldBe 200
      response.headerValue("X-Textus-Execution-Result") shouldBe Some("direct")
      response.headerValue("X-Textus-Job-Id") shouldBe None
      response.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Job-Id")) shouldBe 0
    }

    "apply the kind-first Job header matrix for every explicit state" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R13; Example: E9; every response kind, identifier presence, and duplicate stale Job headers")
      val accepted = RuntimeContext.ExecutionResponseMetadata(
        admittedMode = CommandExecutionMode.JobAsync.toString,
        effectiveMode = CommandExecutionMode.JobAsync,
        interfaceMode = CommandInterfaceMode.Async,
        managedByJob = true,
        responseKind = RuntimeContext.ExecutionResponseKind.AcceptedJob
      )
      val result = RuntimeContext.ExecutionResponseMetadata(
        admittedMode = CommandExecutionMode.JobSync.toString,
        effectiveMode = CommandExecutionMode.JobSync,
        interfaceMode = CommandInterfaceMode.Sync,
        managedByJob = true,
        responseKind = RuntimeContext.ExecutionResponseKind.JobResult
      )
      val cases = Vector(
        ("direct", RuntimeContext.ExecutionResponseMetadata.direct, None, None),
        ("accepted-with-id", accepted, Some("accepted-job-42"), Some("accepted-job-42")),
        ("accepted-without-id", accepted, None, None),
        ("result-with-id", result, Some("result-job-42"), Some("result-job-42")),
        ("result-without-id", result, None, None)
      )
      val stale = HttpResponse.Text(
        HttpStatus.Ok,
        ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
        Bag.text("ok", StandardCharsets.UTF_8)
      ).withHeader(Record.data(
        "X-Textus-Job-Id" -> "stale-job",
        "x-textus-job-id" -> "stale-job-duplicate"
      ))

      When("each case projects over duplicate stale Job headers")
      cases.foreach { case (label, executionresponse, jobid, expectedjobid) =>
        val projected = HttpExecutionResponseProjector.project(
          stale,
          RuntimeContext.ExecutionMetadata(responseJobId = jobid),
          Some(executionresponse)
        )

        Then(s"$label retains only its kind-authorized Job header outcome")
        projected.headerValue("X-Textus-Job-Id") shouldBe expectedjobid
        projected.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Job-Id")) shouldBe expectedjobid.fold(0)(_ => 1)
      }
    }

    "preserve legacy execution and Job headers without explicit metadata" in {
      Given("a legacy response with execution-result, execution-mode, and duplicate Job headers")
      val legacy = HttpResponse.Text(
        HttpStatus.Ok,
        ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
        Bag.text("ok", StandardCharsets.UTF_8)
      ).withHeader(Record.data(
        "X-Textus-Execution-Mode" -> "LegacyMode",
        "X-Textus-Execution-Result" -> "legacy-result",
        "X-Textus-Job-Id" -> "legacy-job",
        "x-textus-job-id" -> "legacy-job-duplicate"
      ))

      When("the loopback-compatible execution projector receives no additive response metadata")
      val projected = HttpExecutionEnvelope(
        legacy,
        RuntimeContext.ExecutionMetadata.empty,
        None
      ).toLegacy.response

      Then("every legacy execution and Job header remains exactly observable")
      projected.header.fields shouldBe legacy.header.fields
      projected.headerValue("X-Textus-Execution-Mode") shouldBe Some("LegacyMode")
      projected.headerValue("X-Textus-Execution-Result") shouldBe Some("legacy-result")
      projected.headerValue("X-Textus-Job-Id") shouldBe Some("legacy-job")
      projected.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Job-Id")) shouldBe 2
    }

    "preserve debug job metadata as a response header" in {
      Given("a loopback server backed by the default subsystem")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val driver = new LoopbackHttpDriver(
        LoopbackHttpServer.fromEngine(new HttpExecutionEngine(subsystem))
      )

      When("the request asks to run through a debug trace job")
      val response = driver.get("/org.goldenport.cncf.Debug/http/echo?textus.debug.trace-job=true")

      Then("the response exposes the retained job id as an HTTP header")
      response.code shouldBe 200
      response.headerValue("X-Textus-Job-Id").flatMap(JobId.parse(_).toOption) should not be empty
      response.headerValue("X-Textus-Execution-Mode") shouldBe Some("JobSync")
      response.headerValue("X-Textus-Execution-Result") shouldBe Some("job-result")
    }

    "let debug job metadata override an existing job header case-insensitively" in {
      Given("a loopback server whose operation response already carries a stale job header")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val driver = new LoopbackHttpDriver(
        LoopbackHttpServer.fromEngine(new HttpExecutionEngine(subsystem))
      )

      When("the request asks to run through a debug trace job")
      val response = driver.get(
        "/org.goldenport.cncf.Debug/http/echo?textus.debug.trace-job=true&x-textus-job-id=stale-job&x-textus-execution-mode=stale&x-textus-execution-result=stale"
      )

      Then("the metadata job id is the observable job header")
      response.headerValue("X-Textus-Job-Id").flatMap(JobId.parse(_).toOption) should not be empty
      response.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Job-Id")) shouldBe 1
      response.headerValue("X-Textus-Execution-Mode") shouldBe Some("JobSync")
      response.headerValue("X-Textus-Execution-Result") shouldBe Some("job-result")
      response.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Execution-Mode")) shouldBe 1
      response.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Execution-Result")) shouldBe 1
    }
    }
  }
}
