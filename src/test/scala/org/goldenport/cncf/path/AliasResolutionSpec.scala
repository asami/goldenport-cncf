package org.goldenport.cncf.path

import java.lang.reflect.Method

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.path.AliasLoader
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
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
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class AliasResolutionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with OptionValues {

  private lazy val parseCommandArgsMethod: Method = {
    val method = CncfRuntime.getClass.getDeclaredMethod(
      "parseCommandArgs",
      classOf[Subsystem],
      classOf[Array[String]],
      classOf[RunMode]
    )
    method.setAccessible(true)
    method
  }

  private lazy val toRequestScriptMethod: Method = {
    val method = CncfRuntime.getClass.getDeclaredMethod(
      "_to_request_script",
      classOf[Subsystem],
      classOf[Array[String]]
    )
    method.setAccessible(true)
    method
  }

  private def canonicalAliasConfig: Configuration =
    aliasConfig("ping" -> "admin.system.ping")

  "Alias-enabled CLI parsing" should {
    "rewrite ping to admin.system.ping before CanonicalPath resolution" in {
      Given("a runtime with ping alias configured")
      withAliasContext(RunMode.Command, canonicalAliasConfig) { (aliasResolver, _) =>
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        val result = parseCommandArgsMethod.invoke(
          CncfRuntime,
          subsystem,
          Array("ping"),
          RunMode.Command
        ).asInstanceOf[Consequence[Request]]

        result match {
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
      withAliasContext(RunMode.Script, canonicalAliasConfig) { (aliasResolver, _) =>
        val subsystem = DefaultSubsystemFactory.default(Some("script"))
        val result = toRequestScriptMethod.invoke(
          CncfRuntime,
          subsystem,
          Array("ping")
        ).asInstanceOf[Consequence[Request]]

        result match {
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
      withAliasContext(RunMode.Command, canonicalAliasConfig) { (aliasResolver, _) =>
        val subsystem = DefaultSubsystemFactory.default(Some("server"))
        val request = HttpRequest.fromPath(HttpRequest.GET, "/ping")
        val response = subsystem.executeHttp(request)
        val expectedPing = GlobalRuntimeContext.formatPingValue(
          mode = RunMode.Command,
          subsystemName = GlobalRuntimeContext.SubsystemName,
          subsystemVersion = CncfVersion.current,
          runtimeVersion = CncfVersion.current
        )

        response.code shouldBe 200
        response.getString.value shouldBe expectedPing
      }
    }
  }

  "Alias loader validation" should {
    "reject duplicate alias inputs" in {
      val config = aliasConfig(
        "ping" -> "admin.system.ping",
        "ping" -> "admin.system.ping"
      )
      intercept[IllegalArgumentException] {
        AliasLoader.load(config)
      }
    }

    "reject alias cycles" in {
      val config = aliasConfig(
        "a" -> "b",
        "b" -> "a"
      )
      intercept[IllegalArgumentException] {
        AliasLoader.load(config)
      }
    }

    "enforce identifier pattern restrictions" in {
      val config = aliasConfig(
        "bad-alias" -> "admin.system.ping"
      )
      intercept[IllegalArgumentException] {
        AliasLoader.load(config)
      }
    }
  }

  private def withAliasContext[T](
    mode: RunMode,
    configuration: Configuration
  )(body: (AliasResolver, GlobalRuntimeContext) => T): T = {
    val resolver = AliasLoader.load(configuration)
    val execution = ExecutionContext.create()
    val httpDriver = FakeHttpDriver.okText("noop")
    val runtimeConfig = RuntimeConfig.default.copy(
      httpDriver = httpDriver,
      mode = mode
    )
    val core = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "alias-test",
      parent = None,
      observabilityContext = execution.observability,
      httpDriverOption = Some(httpDriver)
    ).core
    val context = new GlobalRuntimeContext(
      core = core,
      config = runtimeConfig,
      httpDriver = httpDriver,
      aliasResolver = resolver,
      runtimeMode = mode,
      runtimeVersion = CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = CncfVersion.current
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(context)
    try body(resolver, context)
    finally GlobalRuntimeContext.current = previous
  }

  private def aliasConfig(defs: (String, String)*): Configuration = {
    val entries = defs.toVector.map { case (input, output) =>
      ConfigurationValue.ObjectValue(
        Map(
          "input" -> ConfigurationValue.StringValue(input),
          "output" -> ConfigurationValue.StringValue(output)
        )
      )
    }
    Configuration(Map(AliasLoader.ConfigKey -> ConfigurationValue.ListValue(entries.toList)))
  }
}
