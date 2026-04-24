package org.goldenport.cncf.http

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 25, 2026
 * @version Apr. 25, 2026
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
      response.headerValue("X-Textus-Job-Id").getOrElse("") should include ("cncf-job-job")
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
      response.headerValue("X-Textus-Job-Id").getOrElse("") should include ("cncf-job-job")
      response.header.fields.count(_.key.equalsIgnoreCase("X-Textus-Job-Id")) shouldBe 1
    }
  }
}
