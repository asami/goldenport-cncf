package org.goldenport.cncf.resolver

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.path.{AliasLoader, AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 20, 2026
 *  version Feb.  1, 2026
 *  version Jul. 30, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminSystemPingResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:admin-system-ping-resolver, example:E1, rules:CID05C-R9, phase:56, slice:CID-05C")

  "Ping selector resolution" should {
    "E1 resolve both canonical and alias selectors to builtin admin.system.ping" must _e1 {
      "when exercising: resolve both canonical and alias selectors to builtin admin.system.ping" in {
        Given("the command runtime and one alias for the canonical Admin ping selector")
        val configuration = _alias_config("ping" -> s"${BuiltinComponentIdentity.ADMIN.name}.system.ping")
        val mode = RunMode.Command

        When("the resolver rewrites and resolves canonical and alias selectors")
        _with_alias_context(mode, configuration) { (aliasresolver, _) =>
          val subsystem = DefaultSubsystemFactory.default(Some("command"))
          val resolver = subsystem.resolver

          Seq(s"${BuiltinComponentIdentity.ADMIN.name}.system.ping", "ping").foreach { selector =>
            val normalized =
              PathPreNormalizer.rewriteSelector(selector, mode, aliasresolver)
            Then("each selector resolves to the canonical builtin Admin operation")
            resolver.resolve(normalized) match {
              case ResolutionResult.Resolved(fqn, component, service, operation) =>
                fqn shouldBe "org.goldenport.cncf.Admin.system.ping"
                component shouldBe "org.goldenport.cncf.Admin"
                service shouldBe "system"
                operation shouldBe "ping"
              case other =>
                fail(s"unexpected resolution for selector ${selector}: $other")
            }
          }
        }
      }
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
      name = "ping-resolver-spec",
      parent = None,
      observabilitycontext = execution.observability,
      httpdriveroption = Some(httpdriver)
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
    val entries = defs.toVector.map { case (input, output) =>
      ConfigurationValue.ObjectValue(
        Map(
          "input" -> ConfigurationValue.StringValue(input),
          "output" -> ConfigurationValue.StringValue(output)
        )
      )
    }
    Configuration(Map(AliasLoader.configKey -> ConfigurationValue.ListValue(entries.toList)))
  }
}
