package org.goldenport.cncf.cli

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import cats.data.NonEmptyVector
import org.goldenport.cncf.CncfVersion
import org.goldenport.record.Record
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentInit, ComponentOrigin}
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.value.BaseContent
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 *  version Jan. 18, 2026
 *  version May.  2, 2026
 *  version Jun. 29, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
class CommandExecuteComponentSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "CncfRuntime.parseCommandArgs" should {
    "reject component service operation token form" in {
      Given("the preconditions for reject component service operation token form")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("admin", "system", "ping"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          fail(s"unexpected success: ${req}")
        case Consequence.Failure(_) =>
          succeed
      }
    }

    "parse component.service.operation form" in {
      Given("the preconditions for parse component.service.operation form")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("org.goldenport.cncf.Admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "parse selector followed by option-like arguments" in {
      Given("the preconditions for parse selector followed by option-like arguments")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping", "--name", "taro"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("org.goldenport.cncf.Admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
          req.properties.exists(p => p.name == "name" && p.value == "taro") shouldBe true
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "parse selector followed by option-like key=value arguments" in {
      Given("the preconditions for parse selector followed by option-like key=value arguments")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping", "--name=taro"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("org.goldenport.cncf.Admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
          req.properties.exists(p => p.name == "name" && p.value == "taro") shouldBe true
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "parse operation leaf followed by property arguments" in {
      Given("the preconditions for parse operation leaf followed by property arguments")
      val subsystem = _subsystem_with_presentation_ops()
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(
        subsystem,
        Array("validate-presentation", "--presentationDsl", "presentation:")
      )

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("org.goldenport.cncf.test.Sample")
          req.service.getOrElse(fail("missing service")).shouldBe("presentation")
          req.operation.shouldBe("validatePresentation")
          req.properties.exists(p => p.name == "presentationDsl" && p.value == "presentation:") shouldBe true
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "preserve dotted query control properties in the request record" in {
      Given("the preconditions for preserve dotted query control properties in the request record")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(
        subsystem,
        Array("admin.system.ping", "--query.limit", "2", "--query.offset", "1")
      )

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.properties.exists(p => p.name == "query.limit" && p.value == "2") shouldBe true
          req.properties.exists(p => p.name == "query.offset" && p.value == "1") shouldBe true
          req.toRecord.getRecord("query").flatMap(_.getInt("limit")) shouldBe Some(2)
          req.toRecord.getRecord("query").flatMap(_.getInt("offset")) shouldBe Some(1)
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "resolve subsystem meta selector" in {
      Given("the preconditions for resolve subsystem meta selector")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("meta.help"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "resolve service meta selector with service argument forwarding" in {
      Given("the preconditions for resolve service meta selector with service argument forwarding")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.meta.operations"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("org.goldenport.cncf.Admin")
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("operations")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing forwarded service argument")) shouldBe "admin.system"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "redirect help alias to meta.help" in {
      Given("the preconditions for redirect help alias to meta.help")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("help", "admin.system.ping"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing help target argument")) shouldBe "admin.system.ping"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "strip runtime flags before selector resolution" in {
      Given("the preconditions for strip runtime flags before selector resolution")
      val subsystem = _subsystem_with_domain()
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.help", "--no-exit", "--json"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("org.goldenport.cncf.test.Domain")
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing component argument")) shouldBe "domain"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "resolve component.service omission via PathResolution feature flag in command mode" in {
      Given("the preconditions for resolve component.service omission via PathResolution feature flag in command mode")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("--path-resolution", "admin.component"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some("org.goldenport.cncf.Admin")
          req.service shouldBe Some("component")
          req.operation shouldBe "list"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "derive output format from selector suffix when format property is absent" in {
      Given("the preconditions for derive output format from selector suffix when format property is absent")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("admin.meta.describe.json"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some("org.goldenport.cncf.Admin")
          req.service shouldBe Some("meta")
          req.operation shouldBe "describe"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "prefer explicit --format over selector suffix format" in {
      Given("the preconditions for prefer explicit --format over selector suffix format")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("admin.meta.describe.json", "--format", "yaml"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some("org.goldenport.cncf.Admin")
          req.service shouldBe Some("meta")
          req.operation shouldBe "describe"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }
  }

  "meta introspection rendering" should {
    "return YAML for meta.help by default" in {
      Given("the preconditions for return YAML for meta.help by default")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.help")).toOption.getOrElse(fail("parse failed"))
      When("the meta.help request is executed")
      val result = subsystem.execute(req)

      Then("the subsystem help is rendered as YAML")
      result match {
        case Consequence.Success(res) =>
          val body = res.print
          body.contains("type: subsystem") shouldBe true
          body.contains("components:") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return component YAML for domain.meta.help" in {
      Given("the preconditions for return component YAML for domain.meta.help")
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.help")).toOption.getOrElse(fail("parse failed"))
      When("the domain meta.help request is executed")
      val result = subsystem.execute(req)

      Then("the component help is rendered as YAML")
      result match {
        case Consequence.Success(res) =>
          val body = res.print
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
      Given("the preconditions for return JSON for meta.help when --json is specified")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.help", "--json")).toOption.getOrElse(fail("parse failed"))
      req.properties.exists(p => p.name == "textus.format" && p.value == "json") shouldBe true
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(res) =>
          res.print.trim should startWith("{")
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return JSON for meta.help when textus.format is specified" in {
      Given("the preconditions for return JSON for meta.help when textus.format is specified")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("admin.meta.help", "--textus.format", "json")).toOption.getOrElse(fail("parse failed"))
      req.properties.exists(p => p.name == "textus.format" && p.value == "json") shouldBe true
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(res) =>
          res.print.trim should startWith("{")
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return component YAML for domain.meta.help with --no-exit" in {
      Given("the preconditions for return component YAML for domain.meta.help with --no-exit")
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.help", "--no-exit")).toOption.getOrElse(fail("parse failed"))
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(res) =>
          val body = res.print
          withClue(s"body=\n$body\n") {
            body.contains("type: component") shouldBe true
            body.contains("name: domain") shouldBe true
          }
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return subsystem hierarchy for meta.tree" in {
      Given("the preconditions for return subsystem hierarchy for meta.tree")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.tree")).toOption.getOrElse(fail("parse failed"))
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
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
      Given("the preconditions for return JSON for meta.tree when --json is specified")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.tree", "--json")).toOption.getOrElse(fail("parse failed"))
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(Response.Scalar(value)) =>
          value.toString.nonEmpty shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "return statemachine projection for meta.statemachine" in {
      Given("the preconditions for return statemachine projection for meta.statemachine")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("meta.statemachine")).toOption.getOrElse(fail("parse failed"))
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(Response.Yaml(value)) =>
          val body = value.toString
          body.contains("type: statemachine") shouldBe true
          body.contains("transitions:") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "redirect help domain to component help" in {
      Given("the preconditions for redirect help domain to component help")
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("help", "domain")).toOption.getOrElse(fail("parse failed"))
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(res) =>
          val body = res.print
          body.contains("type: component") shouldBe true
          body.contains("name: domain") shouldBe true
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "render RecordResponse as YAML by default for command mode" in {
      Given("the preconditions for render RecordResponse as YAML by default for command mode")
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.describe")).toOption.getOrElse(fail("parse failed"))
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(Response.Yaml(value)) =>
          value.contains("type:") shouldBe true
        case other =>
          fail(s"expected yaml response but got: $other")
      }
    }

    "render an address-like RecordResponse for generated VO smoke" in {
      Given("the preconditions for render an address-like RecordResponse for generated VO smoke")
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("domain.meta.describe")).toOption.getOrElse(fail("parse failed"))
      When("the projection request is executed")
      val result = subsystem.execute(req)

      Then("the rendered projection has the expected representation")
      result match {
        case Consequence.Success(Response.Yaml(value)) =>
          val body = value.toString
          body.contains("type: component") shouldBe true
          body.contains("name: domain") shouldBe true
        case other =>
          fail(s"expected yaml component payload but got: $other")
      }
    }
  }

  "CLI help system" should {
    "print top-level help for run help" in {
      Given("the preconditions for print top-level help for run help")
      When("the help command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("help"))
      }
      Then("the top-level help succeeds and describes the CLI")
      code shouldBe 0
      out.contains("CNCF Command Line Interface") shouldBe true
      out.contains("cncf <command> [arguments]") shouldBe true
    }

    "print top-level help for run --help" in {
      Given("the preconditions for print top-level help for run --help")
      When("the --help command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("--help"))
      }
      Then("the top-level help succeeds and describes the CLI")
      code shouldBe 0
      out.contains("CNCF Command Line Interface") shouldBe true
      out.contains("cncf <command> [arguments]") shouldBe true
    }

    "print top-level help for run -h" in {
      Given("the preconditions for print top-level help for run -h")
      When("the -h command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("-h"))
      }
      Then("the top-level help succeeds and describes the CLI")
      code shouldBe 0
      out.contains("CNCF Command Line Interface") shouldBe true
      out.contains("cncf <command> [arguments]") shouldBe true
    }

    "print command protocol help for run command help" in {
      Given("the preconditions for print command protocol help for run command help")
      When("the command help route is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", s"--textus.test.descriptor=${_controlled_test_descriptor_path}", "help"))
      }
      Then("the command protocol help is rendered")
      withClue(out) {
        code shouldBe 0
      }
      withClue(out) {
        out.contains("CNCF Command Help") shouldBe true
        out.contains("cncf command <selector> [args...]") shouldBe true
        out.contains("cncf command meta.mcp") shouldBe true
        out.contains("cncf command spec.export.mcp") shouldBe true
        out.contains("AI/MCP Navigation") shouldBe true
      }
    }

    "print subsystem help for run command meta.help" in {
      Given("the preconditions for print subsystem help for run command meta.help")
      When("the command meta.help route is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", s"--textus.test.descriptor=${_controlled_test_descriptor_path}", "meta.help"))
      }
      Then("the subsystem help is rendered")
      withClue(out) {
        code shouldBe 0
      }
      out.contains("type: subsystem") shouldBe true
      out.contains("components:") shouldBe true
    }

    "print command protocol help for run command --help" in {
      Given("the preconditions for print command protocol help for run command --help")
      When("the command --help route is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", s"--textus.test.descriptor=${_controlled_test_descriptor_path}", "--help"))
      }
      Then("the command protocol help is rendered")
      withClue(out) {
        code shouldBe 0
      }
      withClue(out) {
        out.contains("CNCF Command Help") shouldBe true
        out.contains("cncf command <selector> [args...]") shouldBe true
      }
    }

    "print server command help for run server help" in {
      Given("the preconditions for print server command help for run server help")
      When("the server help route is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("server", "help"))
      }
      Then("the server help is rendered")
      code shouldBe 0
      out.contains("CNCF Server Command Help") shouldBe true
      out.contains("cncf server") shouldBe true
    }

    "print server command help for run server --help" in {
      Given("the preconditions for print server command help for run server --help")
      When("the server --help route is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("server", "--help"))
      }
      Then("the server help is rendered")
      code shouldBe 0
      out.contains("CNCF Server Command Help") shouldBe true
      out.contains("cncf server") shouldBe true
    }

    "print client command help for run client help" in {
      Given("the preconditions for print client command help for run client help")
      When("the client help route is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("client", "help"))
      }
      Then("the client help is rendered")
      code shouldBe 0
      out.contains("CNCF Client Command Help") shouldBe true
      out.contains("cncf client <args...>") shouldBe true
    }

    "print client command help for run client --help" in {
      Given("the preconditions for print client command help for run client --help")
      When("the client --help route is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("client", "--help"))
      }
      Then("the client help is rendered")
      code shouldBe 0
      out.contains("CNCF Client Command Help") shouldBe true
      out.contains("cncf client <args...>") shouldBe true
    }

    "rewrite run command domain.entity.createPerson --help to command help selector" in {
      Given("the preconditions for rewrite run command domain.entity.createPerson --help to command help selector")
      val op = spec.OperationDefinition(
        content = BaseContent.simple("createPerson"),
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )
      val service = spec.ServiceDefinition(
        name = "entity",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(op))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))

      When("the generated domain help command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.runWithExtraComponents(
          Array(
            s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
            "command",
            "domain.entity.createPerson",
            "--help"
          ),
          subsystem => {
            val domain = TestComponentFactory.create("domain", protocol)
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      Then("the generated domain help command succeeds")
      code shouldBe 0
    }

    "rewrite run command help domain to domain.meta.help" in {
      Given("the preconditions for rewrite run command help domain to domain.meta.help")
      When("the domain help alias command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array(s"--textus.test.descriptor=${_controlled_test_descriptor_path}", "help", "domain"),
          subsystem => {
            val domain = TestComponentFactory.create("domain", Protocol.empty)
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      Then("the component help projection is rendered")
      code shouldBe 0
      out.contains("type: component") shouldBe true
      out.contains("name: domain") shouldBe true
    }

    "rewrite run command help domain.entity to domain.entity.meta.help" in {
      Given("the preconditions for rewrite run command help domain.entity to domain.entity.meta.help")
      val op = spec.OperationDefinition(
        content = BaseContent.simple("createPerson"),
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )
      val service = spec.ServiceDefinition(
        name = "entity",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(op))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))

      When("the domain entity help alias command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array(
            s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
            "help",
            "domain.entity"
          ),
          subsystem => {
            val domain = TestComponentFactory.create("domain", protocol)
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      Then("the service help projection is rendered")
      code shouldBe 0
      out.contains("type: service") shouldBe true
      out.contains("name: entity") shouldBe true
    }

    "render command output as YAML for --format yaml" in {
      Given("the preconditions for render command output as YAML for --format yaml")
      When("the YAML format command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array(
            s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
            "domain.meta.describe",
            "--format",
            "yaml"
          ),
          subsystem => {
            val domain = TestComponentFactory.create("domain", Protocol.empty)
            domain.withArtifactMetadata(
              org.goldenport.cncf.component.Component.ArtifactMetadata(
                sourceType = "test",
                name = "domain",
                version = "0.1.0"
              )
            )
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      Then("the YAML command output is rendered")
      code shouldBe 0
      out.contains("type:") shouldBe true
    }

    "render command output as JSON for --format json" in {
      Given("the preconditions for render command output as JSON for --format json")
      When("the JSON format command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array(
            s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
            "domain.meta.describe",
            "--format",
            "json"
          ),
          subsystem => {
            val domain = TestComponentFactory.create("domain", Protocol.empty)
            domain.withArtifactMetadata(
              org.goldenport.cncf.component.Component.ArtifactMetadata(
                sourceType = "test",
                name = "domain",
                version = "0.1.0"
              )
            )
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      Then("the JSON command output is rendered")
      code shouldBe 0
      out.nonEmpty shouldBe true
    }

    "render command output as text for --format text" in {
      Given("the preconditions for render command output as text for --format text")
      When("the text format command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array(
            s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
            "domain.meta.describe",
            "--format",
            "text"
          ),
          subsystem => {
            val domain = TestComponentFactory.create("domain", Protocol.empty)
            domain.withArtifactMetadata(
              org.goldenport.cncf.component.Component.ArtifactMetadata(
                sourceType = "test",
                name = "domain",
                version = "0.1.0"
              )
            )
            domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
            Seq(domain)
          }
        )
      }
      Then("the text command output is rendered")
      code shouldBe 0
      out.trim.nonEmpty shouldBe true
      out.trim.startsWith("{") shouldBe false
    }

    "execute run-path with --path-resolution and dot selector" in {
      Given("the preconditions for execute run-path with --path-resolution and dot selector")
      When("the path-resolution command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array(
          "command",
          s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
          "--path-resolution",
          "admin.component"
        ))
      }
      Then("the path-resolution command succeeds")
      withClue(out) {
        code shouldBe 0
      }
      out.toLowerCase.contains("path-resolution failed") shouldBe false
    }

    "keep parse/run contract consistent for --path-resolution with slash selector" in {
      Given("the preconditions for keep parse/run contract consistent for --path-resolution with slash selector")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the slash selector is parsed and run")
      val parsed = CncfRuntime.parseCommandArgs(subsystem, Array("--path-resolution", "admin/component"))
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "--path-resolution", "admin/component"))
      }
      Then("the parse and run outcomes remain consistent")
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
      Given("the preconditions for execute via Subsystem.executeHttp")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val normalized = CncfRuntime.normalizeServerEmulatorArgs(
        Seq("admin", "system", "ping"),
        RuntimeConfig.default.serverEmulatorBaseUrl
      )
      val httpreq = normalized.flatMap(HttpRequest.fromCurlLike)
      When("the normalized HTTP request is executed by the subsystem")
      val result = httpreq.map(subsystem.executeHttp)

      Then("the ping response contains the runtime identity")
      result match {
        case Consequence.Success(res) =>
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
      Given("the preconditions for execute via dot-form input")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val normalized = CncfRuntime.normalizeServerEmulatorArgs(
        Seq("admin.system.ping"),
        RuntimeConfig.default.serverEmulatorBaseUrl
      )
      val httpreq = normalized.flatMap(HttpRequest.fromCurlLike)
      When("the normalized dot-form request is executed by the subsystem")
      val result = httpreq.map(subsystem.executeHttp)

      Then("the ping response contains the runtime identity")
      result match {
        case Consequence.Success(res) =>
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

  private lazy val _controlled_test_descriptor_path: Path = {
    val path = Files.createTempFile("cncf-command-execute-component-", ".yaml")
    Files.writeString(
      path,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: command-execute-component-spec
        |  time:
        |    mode: manual
        |    start-at: 2026-08-11T00:00:00Z
        |  random:
        |    mode: seeded
        |    seed: command-execute-component-spec
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    path
  }

  private def _subsystem_with_domain() = {
    val domain = TestComponentFactory.create("domain", Protocol.empty)
    domain.withArtifactMetadata(
      org.goldenport.cncf.component.Component.ArtifactMetadata(
        sourceType = "test",
        name = "domain",
        version = "0.1.0"
      )
    )
    val subsystem = RuntimeBindingAdmissionFixture.default(Seq(domain), Some("command"))
    domain.initialize(ComponentInit(subsystem, domain.core, ComponentOrigin.Main))
    subsystem
  }

  private def _subsystem_with_presentation_ops() = {
    val validate = spec.OperationDefinition(
      content = BaseContent.simple("validatePresentation"),
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            content = BaseContent.simple("presentationDsl"),
            kind = spec.ParameterDefinition.Kind.Property
          )
        )
      ),
      response = spec.ResponseDefinition.void
    )
    val summarize = spec.OperationDefinition(
      content = BaseContent.simple("summarizePresentation"),
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )
    val service = spec.ServiceDefinition(
      name = "presentation",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.of(validate, summarize))
    )
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
    val sample = TestComponentFactory.create("sample", protocol)
    val subsystem = RuntimeBindingAdmissionFixture.default(Seq(sample), Some("command"))
    sample.initialize(ComponentInit(subsystem, sample.core, ComponentOrigin.Main))
    subsystem
  }

  private def _capture_stdout(body: => Int): (Int, String) = {
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val outps = new PrintStream(out)
    val errps = new PrintStream(err)
    val code =
      Console.withOut(outps) {
        Console.withErr(errps) {
          body
        }
      }
    outps.flush()
    errps.flush()
    (code, out.toString("UTF-8") + err.toString("UTF-8"))
  }
}
