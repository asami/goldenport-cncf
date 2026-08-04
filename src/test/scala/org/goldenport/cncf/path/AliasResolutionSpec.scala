package org.goldenport.cncf.path

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import java.lang.reflect.Method

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.path.AliasLoader
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Request
import org.scalatest.GivenWhenThen
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 19, 2026
 *  version Feb.  1, 2026
 *  version Mar. 28, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class AliasResolutionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with OptionValues {

  private lazy val _parse_command_args_method: Method = {
    val method = CncfRuntime.getClass.getDeclaredMethod(
      "parseCommandArgs",
      classOf[Subsystem],
      classOf[Array[String]],
      classOf[RunMode]
    )
    method.setAccessible(true)
    method
  }

  private def _canonical_alias_config: Configuration =
    _alias_config("ping" -> "admin.system.ping")

  "Alias-enabled CLI parsing" should {
    "rewrite ping to admin.system.ping before CanonicalPath resolution" in {
      Given("a runtime with ping alias configured")
      _with_alias_context(RunMode.Command, _canonical_alias_config) { (aliasResolver, _) =>
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val result = _parse_command_args_method.invoke(
          CncfRuntime,
          subsystem,
          Array("ping"),
          RunMode.Command
        ).asInstanceOf[Consequence[Request]]

        When("the alias is parsed as a command selector")
        val parsed = result

        Then("the canonical selector is resolved")
        parsed match {
        case Consequence.Success(request) =>
            request.component.value shouldBe "admin"
            request.service.value shouldBe "system"
          request.operation shouldBe "ping"
          case other =>
            fail(s"expected success but got $other")
        }
      }
    }
  }

  "Alias-enabled script invocation" should {
    "apply the same alias metdata and resolve ping to admin.system.ping" in {
      Given("a script runtime configured with the ping alias")
      _with_alias_context(RunMode.Script, _canonical_alias_config) { (aliasResolver, _) =>
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
        val result = _parse_command_args_method.invoke(
          CncfRuntime,
          subsystem,
          Array("ping"),
          RunMode.Script
        ).asInstanceOf[Consequence[Request]]

        When("the alias is parsed as a script selector")
        val parsed = result

        Then("the canonical selector is resolved")
        parsed match {
        case Consequence.Success(request) =>
            request.component.value shouldBe "admin"
            request.service.value shouldBe "system"
          request.operation shouldBe "ping"
          case other =>
            fail(s"expected success but got $other")
        }
      }
    }
  }

  "HTTP routing" should {
    "strip the alias selector and dispatch to admin.system.ping" in {
      Given("an alias table and a request for /ping")
      _with_alias_context(RunMode.Command, _canonical_alias_config) { (aliasResolver, context) =>
        val subsystem = RuntimeBindingAdmissionFixture.defaultWithScope(
          context = context,
          mode = Some(RunMode.Command),
          aliasResolver = aliasResolver
        )
        val request = HttpRequest.fromPath(HttpRequest.GET, "/ping")
        When("the alias route is executed")
        val response = subsystem.executeHttp(request)
        val expectedping = GlobalRuntimeContext.formatPingValue(
          mode = RunMode.Command,
          subsystemName = GlobalRuntimeContext.SubsystemName,
          subsystemVersion = CncfVersion.current,
          runtimeVersion = CncfVersion.current
        )

        Then("the route dispatches to the canonical ping operation")
        response.code shouldBe 200
        response.getString.value shouldBe expectedping
      }
    }
  }

  "Alias loader validation" should {
    "prefer textus.path.aliases over legacy cncf.path.aliases" in {
      Given("canonical and legacy alias definitions for the same selector")
      val textus = _alias_entries("ping" -> "admin.system.ping")
      val cncf = _alias_entries("ping" -> "legacy.system.ping")
      val config = Configuration(
        Map(
          AliasLoader.ConfigKey -> ConfigurationValue.ListValue(textus.toList),
          AliasLoader.CompatibilityConfigKey -> ConfigurationValue.ListValue(cncf.toList)
        )
      )

      When("the alias table is loaded")
      val resolver = AliasLoader.load(config)

      Then("the canonical alias definition wins")
      resolver.resolve("ping", RunMode.Command).value shouldBe "admin.system.ping"
    }

    "reject duplicate alias inputs" in {
      Given("duplicate canonical alias inputs")
      val config = _alias_config(
        "ping" -> "admin.system.ping",
        "ping" -> "admin.system.ping"
      )
      When("the alias table is loaded")
      val exception = intercept[IllegalArgumentException] {
        AliasLoader.load(config)
      }
      Then("the duplicate input is rejected")
      exception.getMessage should not be empty
    }

    "reject alias cycles" in {
      Given("a cyclic alias definition")
      val config = _alias_config(
        "a" -> "b",
        "b" -> "a"
      )
      When("the alias table is loaded")
      val exception = intercept[IllegalArgumentException] {
        AliasLoader.load(config)
      }
      Then("the cycle is rejected")
      exception.getMessage should not be empty
    }

    "enforce identifier pattern restrictions" in {
      Given("an alias input outside the identifier pattern")
      val config = _alias_config(
        "bad-alias" -> "admin.system.ping"
      )
      When("the alias table is loaded")
      val exception = intercept[IllegalArgumentException] {
        AliasLoader.load(config)
      }
      Then("the invalid identifier is rejected")
      exception.getMessage should not be empty
    }
  }

  private def _with_alias_context[T](
    mode: RunMode,
    configuration: Configuration
  )(body: (AliasResolver, GlobalRuntimeContext) => T): T = {
    val resolver = AliasLoader.load(configuration)
    val execution = ExecutionContext.create()
    val httpdriver = FakeHttpDriver.okText("noop")
    val runtimeconfig = RuntimeConfig.default.copy(
      httpDriver = httpdriver,
      mode = mode
    )
    val core = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "alias-test",
      parent = None,
      observabilityContext = execution.observability,
      httpDriverOption = Some(httpdriver)
    ).core
    val context = new GlobalRuntimeContext(
      core = core,
      config = runtimeconfig,
      aliasResolver = resolver,
      runtimeMode = mode,
      commandExecutionMode = None,
      runtimeVersion = CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = CncfVersion.current
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(context)
    try body(resolver, context)
    finally GlobalRuntimeContext.current = previous
  }

  private def _alias_config(defs: (String, String)*): Configuration = {
    Configuration(Map(AliasLoader.ConfigKey -> ConfigurationValue.ListValue(_alias_entries(defs: _*).toList)))
  }

  private def _alias_entries(defs: (String, String)*): Vector[ConfigurationValue] = {
    val entries = defs.toVector.map { case (input, output) =>
      ConfigurationValue.ObjectValue(
        Map(
          "input" -> ConfigurationValue.StringValue(input),
          "output" -> ConfigurationValue.StringValue(output)
        )
      )
    }
    entries
  }
}
