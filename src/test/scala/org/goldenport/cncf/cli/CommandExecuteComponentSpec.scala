package org.goldenport.cncf.cli

import java.io.{ByteArrayOutputStream, PrintStream}
import org.goldenport.Consequence
import cats.data.NonEmptyVector
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentInit, ComponentOrigin}
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 *  version Jan. 18, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
class CommandExecuteComponentSpec extends AnyWordSpec with Matchers {

  "CncfRuntime.parseCommandArgs" should {
    "parse component service operation form" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin", "system", "ping")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "parse component.service.operation form" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "parse selector followed by option-like arguments" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping", "--name", "taro")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
          req.properties.exists(p => p.name == "name" && p.value == "taro") shouldBe true
          req.properties.exists(p => p.name == "format" && p.value == "yaml") shouldBe true
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "parse selector followed by option-like key=value arguments" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping", "--name=taro")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
          req.properties.exists(p => p.name == "name" && p.value == "taro") shouldBe true
          req.properties.exists(p => p.name == "format" && p.value == "yaml") shouldBe true
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "resolve subsystem meta selector" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("meta.help")) match {
        case Consequence.Success(req: Request) =>
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "resolve service meta selector with service argument forwarding" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.meta.operations")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("admin")
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("operations")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing forwarded service argument")) shouldBe "admin.system"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "redirect help alias to meta.help" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("help", "admin.system.ping")) match {
        case Consequence.Success(req: Request) =>
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing help target argument")) shouldBe "admin.system.ping"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "strip runtime flags before selector resolution" in {
      val subsystem = _subsystem_with_domain()
      CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.help", "--no-exit", "--json")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("domain")
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing component argument")) shouldBe "domain"
          req.properties.exists(_.name == "format") shouldBe true
          req.properties.exists(_.name == "no-exit") shouldBe true
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "resolve component.service omission via PathResolution feature flag in command mode" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("--path-resolution", "admin.component")) match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some("admin")
          req.service shouldBe Some("component")
          req.operation shouldBe "list"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "derive output format from selector suffix when format property is absent" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin.meta.describe.json")) match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some("admin")
          req.service shouldBe Some("meta")
          req.operation shouldBe "describe"
          req.properties.find(_.name == "format").map(_.value.toString) shouldBe Some("json")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "prefer explicit --format over selector suffix format" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin.meta.describe.json", "--format", "yaml")) match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some("admin")
          req.service shouldBe Some("meta")
          req.operation shouldBe "describe"
          req.properties.reverseIterator.find(_.name == "format").map(_.value.toString) shouldBe Some("yaml")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }
  }

  "meta introspection rendering" should {
    "return YAML for meta.help by default" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.help")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          val body = value.toString
          body.contains("type: subsystem") shouldBe true
          body.contains("components:") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return component YAML for domain.meta.help" in {
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.help")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          val body = value.toString
          withClue(s"body=\n$body\n") {
            body.contains("type: component") shouldBe true
            body.contains("name: domain") shouldBe true
            body.contains("type: subsystem") shouldBe false
          }
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return JSON for meta.help when --json is specified" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.help", "--json")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          val body = value.toString
          body.startsWith("{") shouldBe true
          body.contains("\"type\":\"subsystem\"") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return component YAML for domain.meta.help with --no-exit" in {
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.help", "--no-exit")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          val body = value.toString
          withClue(s"body=\n$body\n") {
            body.contains("type: component") shouldBe true
            body.contains("name: domain") shouldBe true
          }
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return subsystem hierarchy for meta.tree" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.tree")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          val body = value.toString
          body.contains("subsystem:") shouldBe true
          body.contains("components:") shouldBe true
          body.contains("services:") shouldBe true
          body.contains("operations:") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return JSON for meta.tree when --json is specified" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.tree", "--json")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          val body = value.toString
          body.startsWith("{") shouldBe true
          body.contains("\"subsystem\"") shouldBe true
          body.contains("\"components\"") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return statemachine projection for meta.statemachine" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.statemachine")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Yaml(value)) =>
          val body = value.toString
          body.contains("type: statemachine") shouldBe true
          body.contains("transitions:") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "redirect help domain to component help" in {
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("help", "domain")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          val body = value.toString
          body.contains("type: component") shouldBe true
          body.contains("name: domain") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "render RecordResponse as YAML by default for command mode" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.describe")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Yaml(value)) =>
          value.contains("type:") shouldBe true
        case other =>
          fail(s"expected yaml response but got: $other")
      }
    }

    "render RecordResponse as JSON when --format json is specified" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.describe", "--format", "json")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Json(value)) =>
          value.startsWith("{") shouldBe true
        case other =>
          fail(s"expected json response but got: $other")
      }
    }

    "render RecordResponse as text when --format text is specified" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.describe", "--format", "text")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Scalar(value)) =>
          value.toString.nonEmpty shouldBe true
        case other =>
          fail(s"expected scalar text response but got: $other")
      }
    }

    "fallback to YAML when --format has invalid value" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.describe", "--format", "bogus")).toOption.getOrElse(fail("parse failed"))
      subsystem.execute(req) match {
        case Consequence.Success(Response.Yaml(value)) =>
          value.contains("type:") shouldBe true
        case other =>
          fail(s"expected yaml fallback response but got: $other")
      }
    }
  }

  "CLI help system" should {
    "print top-level help for run help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("help"))
      }
      code shouldBe 0
      out.contains("CNCF Command Line Interface") shouldBe true
      out.contains("cncf <command> [arguments]") shouldBe true
    }

    "print top-level help for run --help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("--help"))
      }
      code shouldBe 0
      out.contains("CNCF Command Line Interface") shouldBe true
      out.contains("cncf <command> [arguments]") shouldBe true
    }

    "print top-level help for run -h" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("-h"))
      }
      code shouldBe 0
      out.contains("CNCF Command Line Interface") shouldBe true
      out.contains("cncf <command> [arguments]") shouldBe true
    }

    "print command protocol help for run command help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "help"))
      }
      code shouldBe 0
      out.contains("CNCF Command Help") shouldBe true
      out.contains("cncf command <selector> [args...]") shouldBe true
      out.contains("cncf command meta.mcp") shouldBe true
      out.contains("cncf command spec.export.mcp") shouldBe true
      out.contains("AI/MCP Navigation") shouldBe true
    }

    "print command protocol help for run command --help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "--help"))
      }
      code shouldBe 0
      out.contains("CNCF Command Help") shouldBe true
      out.contains("cncf command <selector> [args...]") shouldBe true
    }

    "print server command help for run server help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("server", "help"))
      }
      code shouldBe 0
      out.contains("CNCF Server Command Help") shouldBe true
      out.contains("cncf server") shouldBe true
    }

    "print server command help for run server --help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("server", "--help"))
      }
      code shouldBe 0
      out.contains("CNCF Server Command Help") shouldBe true
      out.contains("cncf server") shouldBe true
    }

    "print client command help for run client help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("client", "help"))
      }
      code shouldBe 0
      out.contains("CNCF Client Command Help") shouldBe true
      out.contains("cncf client <args...>") shouldBe true
    }

    "print client command help for run client --help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("client", "--help"))
      }
      code shouldBe 0
      out.contains("CNCF Client Command Help") shouldBe true
      out.contains("cncf client <args...>") shouldBe true
    }

    "rewrite run command domain.entity.createPerson --help to command help selector" in {
      val op = spec.OperationDefinition(
        name = "createPerson",
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition()
      )
      val service = spec.ServiceDefinition(
        name = "entity",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(op))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))

      val (code, out) = _capture_stdout {
        CncfRuntime.runWithExtraComponents(
          Array("command", "domain.entity.createPerson", "--help"),
          subsystem => {
            val domain = TestComponentFactory.create("domain", protocol)
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      code shouldBe 0
      out.contains("type: operation") shouldBe true
      out.contains("name: domain.entity.createPerson") shouldBe true
    }

    "rewrite run command help domain to domain.meta.help" in {
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array("help", "domain"),
          subsystem => {
            val domain = TestComponentFactory.create("domain", Protocol.empty)
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      code shouldBe 0
      out.contains("type: component") shouldBe true
      out.contains("name: domain") shouldBe true
    }

    "rewrite run command help domain.entity to domain.entity.meta.help" in {
      val op = spec.OperationDefinition(
        name = "createPerson",
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition()
      )
      val service = spec.ServiceDefinition(
        name = "entity",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(op))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))

      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array("help", "domain.entity"),
          subsystem => {
            val domain = TestComponentFactory.create("domain", protocol)
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      code shouldBe 0
      out.contains("type: service") shouldBe true
      out.contains("name: domain.entity") shouldBe true
    }

    "render command output as YAML for --format yaml" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "meta.describe", "--format", "yaml"))
      }
      code shouldBe 0
      out.contains("type:") shouldBe true
    }

    "render command output as JSON for --format json" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "meta.describe", "--format", "json"))
      }
      code shouldBe 0
      out.trim.startsWith("{") shouldBe true
    }

    "render command output as text for --format text" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "meta.describe", "--format", "text"))
      }
      code shouldBe 0
      out.trim.nonEmpty shouldBe true
      out.trim.startsWith("{") shouldBe false
    }

    "execute run-path with --path-resolution and dot selector" in {
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "--path-resolution", "admin.component"))
      }
      code shouldBe 0
      out.toLowerCase.contains("path-resolution failed") shouldBe false
    }

    "keep parse/run contract consistent for --path-resolution with slash selector" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val parsed = CncfRuntime.parseCommandArgs(subsystem, Array("--path-resolution", "admin/component"))
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "--path-resolution", "admin/component"))
      }
      parsed match {
        case Consequence.Success(_) =>
          code shouldBe 0
          out.toLowerCase.contains("path-resolution failed") shouldBe false
        case Consequence.Failure(_) =>
          code should be > 0
      }
    }
  }

  "server-emulator normalization" should {
    "execute via Subsystem.executeHttp" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val normalized = CncfRuntime.normalizeServerEmulatorArgs(
        Seq("admin", "system", "ping"),
        RuntimeConfig.default.serverEmulatorBaseUrl
      )
      val httpReq = normalized.flatMap(HttpRequest.fromCurlLike)
      httpReq match {
        case Consequence.Success(req: HttpRequest) =>
          val res = subsystem.executeHttp(req)
          val expected = GlobalRuntimeContext.formatPingValue(
            mode = RunMode.Command,
            subsystemName = GlobalRuntimeContext.SubsystemName,
            subsystemVersion = CncfVersion.current,
            runtimeVersion = CncfVersion.current
          )
          res.code.shouldBe(200)
          res.getString.getOrElse("").shouldBe(expected)
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "execute via dot-form input" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val normalized = CncfRuntime.normalizeServerEmulatorArgs(
        Seq("admin.system.ping"),
        RuntimeConfig.default.serverEmulatorBaseUrl
      )
      val httpReq = normalized.flatMap(HttpRequest.fromCurlLike)
      httpReq match {
        case Consequence.Success(req: HttpRequest) =>
          val res = subsystem.executeHttp(req)
          val expected = GlobalRuntimeContext.formatPingValue(
            mode = RunMode.Command,
            subsystemName = GlobalRuntimeContext.SubsystemName,
            subsystemVersion = CncfVersion.current,
            runtimeVersion = CncfVersion.current
          )
          res.code.shouldBe(200)
          res.getString.getOrElse("").shouldBe(expected)
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }
  }

  private def _subsystem_with_domain() = {
    val domain = TestComponentFactory.create("domain", Protocol.empty)
    val subsystem = DefaultSubsystemFactory.default(Seq(domain), Some("command"))
    domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
    subsystem
  }

  private def _capture_stdout(body: => Int): (Int, String) = {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos)
    val code =
      Console.withOut(ps) {
        body
      }
    ps.flush()
    (code, baos.toString("UTF-8"))
  }
}
