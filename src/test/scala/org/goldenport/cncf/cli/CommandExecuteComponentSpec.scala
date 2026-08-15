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
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.value.BaseContent
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 *  version Jan. 18, 2026
 *  version May.  2, 2026
 *  version Jun. 29, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class CommandExecuteComponentSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  private val _e22 = afterWord(
    "in spec:phase-56-runtime-identity-projection, example:E22, rules:CID06C-R8,R9, phase:56, slice:CID-06C"
  )
  private val _controlled_test_descriptor_work_dir =
    Path.of("target", "cncf-test", "work", "command-execute-component").toAbsolutePath.normalize

  override protected def afterAll(): Unit = {
    try {
      val path = _controlled_test_descriptor_work_dir.resolve("controlled-test-descriptor.yaml")
      Files.deleteIfExists(path)
      Files.deleteIfExists(_controlled_test_descriptor_work_dir)
    } finally {
      super.afterAll()
    }
  }

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
      val result = CncfRuntime.parseCommandArgs(subsystem, Array(s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
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
      val result = CncfRuntime.parseCommandArgs(subsystem, Array(s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping", "--name", "taro"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
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
      val result = CncfRuntime.parseCommandArgs(subsystem, Array(s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping", "--name=taro"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
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
        Array("org.goldenport.cncf.test.Sample.presentation.validatePresentation", "--presentationDsl", "presentation:")
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
        Array(s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping", "--query.limit", "2", "--query.offset", "1")
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
      val result = CncfRuntime.parseCommandArgs(
        subsystem,
        Array(
          s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.meta.operations",
          s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system"
        )
      )

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("operations")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing forwarded service argument")) shouldBe s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "redirect help alias to meta.help" in {
      Given("the preconditions for redirect help alias to meta.help")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("help", s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing help target argument")) shouldBe s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "strip runtime flags before selector resolution" in {
      Given("the preconditions for strip runtime flags before selector resolution")
      val subsystem = _subsystem_with_domain()
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(
        subsystem,
        Array("org.goldenport.cncf.test.Domain.meta.help", "org.goldenport.cncf.test.Domain", "--no-exit", "--json")
      )

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("org.goldenport.cncf.test.Domain")
          req.service.getOrElse(fail("missing service")).shouldBe("meta")
          req.operation.shouldBe("help")
          req.arguments.headOption.map(_.value.toString).getOrElse(fail("missing component argument")) shouldBe "org.goldenport.cncf.test.Domain"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "resolve component.service omission via PathResolution feature flag in command mode" in {
      Given("the preconditions for resolve component.service omission via PathResolution feature flag in command mode")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      When("the command selector is parsed")
      val result = CncfRuntime.parseCommandArgs(subsystem, Array("--path-resolution", s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.component"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
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
      val result = CncfRuntime.parseCommandArgs(subsystem, Array(s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.meta.describe.json"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
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
      val result = CncfRuntime.parseCommandArgs(subsystem, Array(s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.meta.describe.json", "--format", "yaml"))

      Then("the parsed request has the expected command shape")
      result match {
        case Consequence.Success(req: Request) =>
          req.component shouldBe Some(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
          req.service shouldBe Some("meta")
          req.operation shouldBe "describe"
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }
  }

  "CliOperation.parse_component_service_operation_string" should {
    "parse a qualified component selector through the shared operation parser" in {
      Given("a shared CLI operation parser")
      val parser = new SharedComponentSelectorParser

      When("a qualified component selector is parsed")
      val result = parser.parseComponentServiceOperation("org.goldenport.cncf.Admin.system.ping")

      Then("the component, service, and operation retain their canonical boundaries")
      result match {
        case Consequence.Success((component, service, operation)) =>
          component shouldBe "org.goldenport.cncf.Admin"
          service shouldBe "system"
          operation shouldBe "ping"
        case Consequence.Failure(conclusion) =>
          fail(s"unexpected failure: $conclusion")
      }
    }

    "E22 expose deduplicated compatibility warnings through the runtime-selector assembly report" must _e22 {
      "when the accepted non-Web presentation selector is parsed twice in command mode" in {
        Given("a command subsystem owned by one runtime with the admitted Admin presentation selector")
        val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        val runtime = GlobalRuntimeContext.create(
          "phase56-admin-observability",
          RuntimeConfig.default,
          configuration,
          ExecutionContext.create().observability,
          AliasResolver.empty
        )
        val previous = GlobalRuntimeContext.current
        try {
          GlobalRuntimeContext.current = Some(runtime)
          val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"), configuration)

          When("the presentation selector is parsed twice at the command boundary")
          val first = CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping"))
          val second = CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping"))
          val warnings = runtime.assemblyReport.warnings

          Then("both requests use the canonical Admin identity and one runtime warning is retained")
          first.toOption.flatMap(_.component) shouldBe Some(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
          first.toOption.flatMap(_.service) shouldBe Some("system")
          first.toOption.map(_.operation) shouldBe Some("ping")
          second.toOption.flatMap(_.component) shouldBe Some(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name)
          second.toOption.flatMap(_.service) shouldBe Some("system")
          second.toOption.map(_.operation) shouldBe Some("ping")
          warnings should have size 1
          warnings.head.kind shouldBe "component-identity-compatibility"
          warnings.head.componentName shouldBe org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name
          warnings.head.reason.getOrElse("") should include ("surface=runtime-selector")
          warnings.head.reason.getOrElse("") should include ("alias=admin")
        } finally {
          GlobalRuntimeContext.current = previous
        }
      }
    }

    "execute one unique presentation component selector and reject an ambiguous selector" in {
      Given("a command runtime with one Sanpomap presentation alias and two colliding artifact aliases")
      val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      val runtime = GlobalRuntimeContext.create(
        "command-component-identity-compatibility",
        RuntimeConfig.default,
        configuration,
        ExecutionContext.create().observability,
        AliasResolver.empty
      )
      val previous = GlobalRuntimeContext.current
      try {
        GlobalRuntimeContext.current = Some(runtime)
        val sanpomap = TestComponentFactory.create("sanpomap", Protocol.empty)
        val subsystem = RuntimeBindingAdmissionFixture.default(Seq(sanpomap), Some("command"))
        sanpomap.initialize(ComponentInit(subsystem, sanpomap.core, ComponentOrigin.Main))
        val alpha = TestComponentFactory.create("alpha", Protocol.empty).withArtifactMetadata(
          org.goldenport.cncf.component.Component.ArtifactMetadata(
            sourceType = "test",
            name = "sanpomap",
            version = "0.1.0"
          )
        )
        val beta = TestComponentFactory.create("beta", Protocol.empty).withArtifactMetadata(
          org.goldenport.cncf.component.Component.ArtifactMetadata(
            sourceType = "test",
            name = "sanpomap",
            version = "0.1.0"
          )
        )
        val ambiguoussubsystem = RuntimeBindingAdmissionFixture.default(Seq(alpha, beta), Some("command"))
        alpha.initialize(ComponentInit(ambiguoussubsystem, alpha.core, ComponentOrigin.Main))
        beta.initialize(ComponentInit(ambiguoussubsystem, beta.core, ComponentOrigin.Main))

        When("command mode resolves the presentation selector without path resolution and executes it")
        val legacyrequest = CncfRuntime.parseCommandArgs(subsystem, Array("sanpomap.meta.help", sanpomap.componentId.name))
        val execution = legacyrequest.flatMap(subsystem.execute)
        val ambiguousresult = CncfRuntime.parseCommandArgs(
          ambiguoussubsystem,
          Array("sanpomap.meta.help")
        )
        val warnings = runtime.assemblyReport.warnings

        val runtimewarnings = warnings.filter(_.reason.exists(_.contains("surface=runtime-selector")))
        val helpwarnings = warnings.filter(_.reason.exists(_.contains("surface=help-projection")))

        Then("the unique selector becomes canonical, executes, records each surface warning, and the collision fails")
        legacyrequest.toOption.flatMap(_.component) shouldBe Some(sanpomap.componentId.name)
        execution.isSuccess shouldBe true
        runtimewarnings should have size 1
        runtimewarnings.head.kind shouldBe "component-identity-compatibility"
        runtimewarnings.head.componentName shouldBe sanpomap.componentId.name
        runtimewarnings.head.reason.getOrElse("") should include ("alias=sanpomap")
        helpwarnings should have size 1
        helpwarnings.head.kind shouldBe "component-identity-compatibility"
        helpwarnings.head.reason.getOrElse("") should include ("surface=help-projection")
        ambiguousresult.isFaillure shouldBe true
        ambiguousresult.display should include ("ambiguous selector 'sanpomap'")
      } finally {
        GlobalRuntimeContext.current = previous
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
      val req = CncfRuntime.parseCommandArgs(
        subsystem,
        Array("org.goldenport.cncf.test.Domain.meta.help", "org.goldenport.cncf.test.Domain")
      ).toOption.getOrElse(fail("parse failed"))
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
      val req = CncfRuntime.parseCommandArgs(subsystem, Array(s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.meta.help", "--textus.format", "json")).toOption.getOrElse(fail("parse failed"))
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
      val req = CncfRuntime.parseCommandArgs(
        subsystem,
        Array("org.goldenport.cncf.test.Domain.meta.help", "org.goldenport.cncf.test.Domain", "--no-exit")
      ).toOption.getOrElse(fail("parse failed"))
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

    "resolve component help target with its canonical selector" in {
      Given("the preconditions for resolve component help target with its canonical selector")
      val subsystem = _subsystem_with_domain()
      val req = CncfRuntime.parseCommandArgs(
        subsystem,
        Array("org.goldenport.cncf.test.Domain.meta.help", "org.goldenport.cncf.test.Domain")
      ).toOption.getOrElse(fail("parse failed"))
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
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("org.goldenport.cncf.test.Domain.meta.describe")).toOption.getOrElse(fail("parse failed"))
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
      val req = CncfRuntime.parseCommandArgs(subsystem, Array("org.goldenport.cncf.test.Domain.meta.describe")).toOption.getOrElse(fail("parse failed"))
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
      out.contains("cncf command org.goldenport.cncf.Admin.system.ping") shouldBe true
      out.contains("cncf command meta.help") shouldBe true
      out.contains("cncf client org.goldenport.cncf.Admin.system.ping") shouldBe true
      out.contains("cncf command domain.entity.createPerson") shouldBe false
      out.contains("cncf client domain.entity.createPerson") shouldBe false
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
        out.contains("cncf command org.goldenport.cncf.Admin.system.ping") shouldBe true
        out.contains("cncf command org.goldenport.cncf.Admin.meta.help") shouldBe true
        out.contains("cncf command org.goldenport.cncf.Admin.system.meta.operations") shouldBe true
        out.contains("cncf command domain.entity.createPerson") shouldBe false
        out.contains("cncf command domain.meta.help") shouldBe false
        out.contains("cncf command domain.entity.meta.operations") shouldBe false
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
      out.contains("cncf client org.goldenport.cncf.Admin.system.ping") shouldBe true
      out.contains("cncf client org.goldenport.cncf.Admin.deployment.securityMermaid") shouldBe true
      out.contains("cncf client org.goldenport.cncf.Admin.deployment.securityMarkdown") shouldBe true
      out.contains("cncf client admin.system.ping") shouldBe false
      out.contains("cncf client crud.entity.create-item") shouldBe false
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

    "execute canonical domain.meta.help target" in {
      Given("the preconditions for executing the canonical domain.meta.help target")
      When("the canonical domain help command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array(
            s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
            "org.goldenport.cncf.test.Domain.meta.help",
            "org.goldenport.cncf.test.Domain"
          ),
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

    "execute canonical domain.entity meta.help target" in {
      Given("the preconditions for executing the canonical domain.entity meta.help target")
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

      When("the canonical domain entity help command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime.executeCommand(
          Array(
            s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
            "org.goldenport.cncf.test.Domain.meta.help",
            "org.goldenport.cncf.test.Domain.entity"
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
            "org.goldenport.cncf.test.Domain.meta.describe",
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
            "org.goldenport.cncf.test.Domain.meta.describe",
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
            "org.goldenport.cncf.test.Domain.meta.describe",
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

    "execute run-path with --path-resolution and qualified selector" in {
      Given("the preconditions for execute run-path with --path-resolution and qualified selector")
      When("the path-resolution command is run")
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array(
          "command",
          s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
          "--path-resolution",
          "org.goldenport.cncf.Admin.component"
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
      val parsed = CncfRuntime.parseCommandArgs(subsystem, Array("--path-resolution", "org.goldenport.cncf.Admin/component"))
      val (code, out) = _capture_stdout {
        CncfRuntime().run(Array("command", "--path-resolution", "org.goldenport.cncf.Admin/component"))
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
        Seq("org.goldenport.cncf.Admin", "system", "ping"),
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

    "execute via slash-form input" in {
      Given("the preconditions for execute via slash-form input")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val normalized = CncfRuntime.normalizeServerEmulatorArgs(
        Seq("org.goldenport.cncf.Admin/system/ping"),
        RuntimeConfig.default.serverEmulatorBaseUrl
      )
      val httpreq = normalized.flatMap(HttpRequest.fromCurlLike)
      When("the normalized slash-form request is executed by the subsystem")
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
    val path = _controlled_test_descriptor_work_dir.resolve("controlled-test-descriptor.yaml")
    Files.createDirectories(_controlled_test_descriptor_work_dir)
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

  private final class SharedComponentSelectorParser extends CliOperation {
    override lazy val subsystem = {
      val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      val runtime = GlobalRuntimeContext.create(
        "shared-component-selector-parser",
        RuntimeConfig.default,
        configuration,
        ExecutionContext.create().observability,
        AliasResolver.empty
      )
      RuntimeBindingAdmissionFixture.defaultWithScope(
        runtime,
        mode = Some(RunMode.Command),
        configuration = configuration,
        aliasResolver = AliasResolver.empty
      )
    }
    override val mode = RunMode.Command

    def parseComponentServiceOperation(value: String): Consequence[(String, String, String)] =
      parse_component_service_operation_string(value)
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
