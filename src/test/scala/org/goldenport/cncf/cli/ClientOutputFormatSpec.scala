package org.goldenport.cncf.cli

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.{InetSocketAddress, URLDecoder}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.http.{HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod, UrlConnectionHttpDriver}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ClientOutputFormatSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ClientOutputFormatSpec.*

  private val _route = "/rest/v1/org-goldenport-cncf-admin/system/ping"
  private val _selector = s"${BuiltinComponentIdentity.ADMIN.name}/system/ping"
  private val _json = """{"result":"pong","note":"日本語 空白+%&=#?"}"""
  private val _yaml = "result: pong\nnote: 日本語 空白+%&=#?"
  private val _default = "server-selected default text"

  "E1 explicit HTTP JSON format" must afterWord(
    "in spec:output-format, example:E1, rules:R8, phase:sm-workflow-6"
  ) {
    "preserve GET and named-input POST across the normal client boundary" in {
      Given("Spec: docs/spec/output-format.md; Rules: R8; Example: E1; explicit HTTP requests and a receiver selecting only the decoded canonical format")
      val input = """{"phaseIdentity":"phase-6","note":"日本語 空白+%&=#?"}"""
      val cases = Vector(
        ("GET", Vector("--json", "http", "get", _route), Vector.empty[(String, String)]),
        ("POST", Vector("--json", "http", "post", _route, s"input=${input}"), Vector("input" -> input))
      )
      _with_receiver { (subsystem, baseurl, captures) =>
        cases.foreach { case (method, args, inputs) =>
          When("the caller requests the canonical JSON response through the normal HTTP client")
          val result = _execute_client(subsystem, args ++ Vector("--baseurl", baseurl))

          Then("R8 preserves the selected method, route and original named input and exposes the receiver's strict JSON Record")
          withClue(s"client exit=${result.exit}, stdout=${result.stdout}, stderr=${result.stderr}: ") {
            result.exit shouldBe 0
          }
          val captured = _capture(captures)
          captured.method shouldBe method
          captured.path shouldBe _route
          captured.query shouldBe (inputs :+ ("textus.format" -> "json"))
          if (method == "GET")
            captured.body shouldBe ""
          else
            _decode_parameters(captured.body) shouldBe (inputs :+ ("textus.format" -> "json"))
          result.exit shouldBe 0
          result.stdout shouldBe (_json + System.lineSeparator())
          new RecordDecoder().json(result.stdout).toOption shouldBe Some(
            Record.data("result" -> "pong", "note" -> "日本語 空白+%&=#?")
          )
          captures.isEmpty shouldBe true
        }
      }
    }
  }

  "E2 operation format precedence and aliases" must afterWord(
    "in spec:output-format, example:E2, rules:R7,R8, phase:sm-workflow-6"
  ) {
    "forward one canonical format while retaining the resolved Admin route" in {
      Given("Spec: docs/spec/output-format.md; Rules: R7,R8; Example: E2; the factory-defined Admin/system/ping operation and explicit format variants")
      val cases = Vector(
        (Vector("--json"), "json", _json),
        (Vector("--json", "--format", "yaml"), "yaml", _yaml),
        (Vector("--textus.format", "json"), "json", _json),
        (Vector("--cncf.format", "json"), "json", _json)
      )
      _with_receiver { (subsystem, baseurl, captures) =>
        cases.foreach { case (options, format, response) =>
          When("the caller selects the operation response format through the normal FQN client")
          val result = _execute_client(subsystem, options ++ Vector(_selector, "--baseurl", baseurl))

          Then("R7 and R8 retain explicit-format precedence, canonicalize aliases and preserve the receiver's response")
          withClue(s"client exit=${result.exit}, stdout=${result.stdout}, stderr=${result.stderr}: ") {
            result.exit shouldBe 0
          }
          val captured = _capture(captures)
          captured.method shouldBe "GET"
          captured.path shouldBe _route
          captured.query shouldBe Vector("textus.format" -> format)
          captured.body shouldBe ""
          result.exit shouldBe 0
          result.stdout shouldBe (response + System.lineSeparator())
          captures.isEmpty shouldBe true
        }
      }
    }
  }

  "E3 unspecified client format" must afterWord(
    "in spec:output-format, example:E3, rules:R8, phase:sm-workflow-6"
  ) {
    "retain server selection and debug forwarding without leaking client controls" in {
      Given("Spec: docs/spec/output-format.md; Rules: R8; Example: E3; Admin/system/ping without explicit format and with no-exit, direct execution-mode and debug trace-job")
      _with_receiver { (subsystem, baseurl, captures) =>
        When("the caller requests the operation while leaving response format selection to the server")
        val result = _execute_client(subsystem, Vector(
          "--no-exit", "textus.command.execution-mode=direct", "--debug.trace-job",
          _selector, "--baseurl", baseurl
        ))

        Then("R8 forwards only the existing debug query and preserves the server-default response with successful exit")
        withClue(s"client exit=${result.exit}, stdout=${result.stdout}, stderr=${result.stderr}: ") {
          result.exit shouldBe 0
        }
        val captured = _capture(captures)
        captured.method shouldBe "GET"
        captured.path shouldBe _route
        captured.query shouldBe Vector("textus.debug.trace-job" -> "true")
        captured.query.map(_._1) should not contain "textus.format"
        captured.query.map(_._1) should not contain "cncf.format"
        captured.query.map(_._1) should not contain "textus.no-exit"
        captured.query.map(_._1) should not contain "textus.command.execution-mode"
        captured.body shouldBe ""
        result.exit shouldBe 0
        result.stdout shouldBe (_default + System.lineSeparator())
        captures.isEmpty shouldBe true
      }
    }
  }

  "E4 operation selector suffixes and precedence" must afterWord(
    "in spec:output-format, example:E4, rules:R7,R8, phase:sm-workflow-6"
  ) {
    "retain requested slash, dotted and split suffixes across normal client resolution" in {
      Given("Spec: docs/spec/output-format.md; Rules: R7,R8; Example: E4; ordinary Admin/system/ping selectors with explicit suffix and option variants")
      val cases = Vector(
        (Vector(_selector + ".json"), "json", _json),
        (Vector(BuiltinComponentIdentity.ADMIN.name + ".system.ping.yaml"), "yaml", _yaml),
        (Vector(BuiltinComponentIdentity.ADMIN.localId.value(), "system", "ping.text"), "text", _default),
        (Vector("--format", "yaml", _selector + ".json"), "yaml", _yaml),
        (Vector("--json", _selector + ".yaml"), "json", _json)
      )
      _with_receiver { (subsystem, baseurl, captures) =>
        cases.foreach { case (args, format, response) =>
          When("the caller selects the operation format through its selector or an overriding option")
          val result = _execute_client(subsystem, args ++ Vector("--baseurl", baseurl))

          Then("R7 and R8 preserve the resolved GET route and exactly one canonical format with the selected receiver response")
          withClue(s"client exit=${result.exit}, stdout=${result.stdout}, stderr=${result.stderr}: ") {
            result.exit shouldBe 0
          }
          val captured = _capture(captures)
          captured.method shouldBe "GET"
          captured.path shouldBe _route
          captured.query shouldBe Vector("textus.format" -> format)
          captured.body shouldBe ""
          result.stdout shouldBe (response + System.lineSeparator())
          captures.isEmpty shouldBe true
        }
      }
    }
  }

  private def _decode_parameters(value: String): Vector[(String, String)] =
    if (value.isEmpty)
      Vector.empty
    else
      value.split("&").toVector.map { pair =>
        val parts = pair.split("=", 2)
        URLDecoder.decode(parts(0), StandardCharsets.UTF_8) ->
          URLDecoder.decode(parts.lift(1).getOrElse(""), StandardCharsets.UTF_8)
      }

  private def _with_receiver(body: (Subsystem, String, ArrayBlockingQueue[Capture]) => Unit): Unit = {
    val captures = new ArrayBlockingQueue[Capture](1)
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      val _ = server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          try {
            val query = _decode_parameters(Option(exchange.getRequestURI.getRawQuery).getOrElse(""))
            val requestbody = exchange.getRequestBody.readNBytes(65537)
            val _ = captures.add(Capture(
              exchange.getRequestMethod, exchange.getRequestURI.getPath,
              query, new String(requestbody, StandardCharsets.UTF_8)
            ))
            val (response, contenttype) = query.find(_._1 == "textus.format").map(_._2) match {
              case Some("json") => (_json, "application/json")
              case Some("yaml") => (_yaml, "application/yaml")
              case _ => (_default, "text/plain")
            }
            val bytes = response.getBytes(StandardCharsets.UTF_8)
            exchange.getResponseHeaders.set("Content-Type", s"${contenttype}; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.length.toLong)
            exchange.getResponseBody.write(bytes)
          } finally {
            exchange.close()
          }
        }
      })
      server.start()
      val baseurl = s"http://127.0.0.1:${server.getAddress.getPort}"
      val subsystem = TestComponentFactory.admittedEmptySubsystem("client-output-format")
      val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
      val component = ClientComponent.Factory.create(params).primary
      val _ = component.withApplicationConfig(
        Component.ApplicationConfig(httpDriver = Some(new UrlConnectionHttpDriver(baseurl)))
      )
      val _ = subsystem.add(component)
      val admin = AdminComponent.Factory.create(params).primary
      val _ = subsystem.add(admin)
      val admission = HttpExecutionAdmission.fromRuntimeC(baseurl, HttpExecutionLimits(10000, 10000, 1048576),
        Map(component.componentId -> HttpExecutionGrant(Vector(baseurl), Set(HttpExecutionMethod.Get, HttpExecutionMethod.Post))))
        .getOrElse(fail("client output host HTTP setup failed"))
      val base = ExecutionContext.create()
      component.withScopeContext(ScopeContext.Instance(base.cncfCore.scope.core.copy(
        httpExecutionAdmissionOption = Some(admission))))
      body(subsystem, baseurl, captures)
    } finally {
      server.stop(0)
    }
  }

  private def _execute_client(subsystem: Subsystem, args: Vector[String]): ClientResult = {
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val stdout = new PrintStream(out, true, StandardCharsets.UTF_8)
    val stderr = new PrintStream(err, true, StandardCharsets.UTF_8)
    try {
      val exit = Console.withOut(stdout) {
        Console.withErr(stderr) {
          new CncfRuntime().executeClient(subsystem, args.toArray)
        }
      }
      ClientResult(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
    } finally {
      stdout.close()
      stderr.close()
    }
  }

  private def _capture(captures: ArrayBlockingQueue[Capture]): Capture =
    Option(captures.poll()).getOrElse(fail("R8 receiver did not capture a client request"))
}

private object ClientOutputFormatSpec {
  final case class Capture(method: String, path: String, query: Vector[(String, String)], body: String)
  final case class ClientResult(exit: Int, stdout: String, stderr: String)
}
