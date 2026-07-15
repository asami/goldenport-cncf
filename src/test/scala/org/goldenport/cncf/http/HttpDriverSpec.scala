package org.goldenport.cncf.http

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.protocol.Property
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 25, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpDriverSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

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
  }

  "LoopbackHttpDriver" should {
    "preserve debug job metadata as a response header" in {
      Given("a loopback server backed by the default subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val driver = new LoopbackHttpDriver(
        LoopbackHttpServer.fromEngine(new HttpExecutionEngine(subsystem))
      )

      When("the request asks to run through a debug trace job")
      val response = driver.get("/debug/http/echo?textus.debug.trace-job=true")

      Then("the response exposes the retained job id as an HTTP header")
      response.code shouldBe 200
      response.headerValue("X-Textus-Job-Id").flatMap(JobId.parse(_).toOption) should not be empty
    }

    "let debug job metadata override an existing job header case-insensitively" in {
      Given("a loopback server whose operation response already carries a stale job header")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val driver = new LoopbackHttpDriver(
        LoopbackHttpServer.fromEngine(new HttpExecutionEngine(subsystem))
      )

      When("the request asks to run through a debug trace job")
      val response = driver.get("/debug/http/echo?textus.debug.trace-job=true&x-textus-job-id=stale-job")

      Then("the metadata job id is the observable job header")
      response.headerValue("X-Textus-Job-Id").flatMap(JobId.parse(_).toOption) should not be empty
      response.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Job-Id")) shouldBe 1
    }
  }
}
