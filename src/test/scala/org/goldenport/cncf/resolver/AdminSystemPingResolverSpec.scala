package org.goldenport.cncf.resolver

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.path.{AliasLoader, AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 20, 2026
 * @version Jan. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminSystemPingResolverSpec extends AnyWordSpec with Matchers {

  "Ping selector resolution" should {
    "resolve both canonical and alias selectors to builtin admin.system.ping" in {
      withAliasContext(RunMode.Command, aliasConfig("ping" -> "admin.system.ping")) { (aliasResolver, _) =>
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        val resolver = subsystem.resolver

        Seq("admin.system.ping", "ping").foreach { selector =>
          val normalized =
            PathPreNormalizer.rewriteSelector(selector, RunMode.Command, aliasResolver)
          resolver.resolve(normalized) match {
            case ResolutionResult.Resolved(fqn, component, service, operation) =>
              fqn shouldBe "admin.system.ping"
              component shouldBe "admin"
              service shouldBe "system"
              operation shouldBe "ping"
            case other =>
              fail(s"unexpected resolution for selector ${selector}: $other")
          }
        }
      }
    }
  }

  private def withAliasContext[T](
    mode: RunMode,
    configuration: Configuration
  )(body: (AliasResolver, GlobalRuntimeContext) => T): T = {
    val resolver = AliasLoader.load(configuration)
    val execution = ExecutionContext.create()
    val context = new GlobalRuntimeContext(
      name = "ping-resolver-spec",
      observabilityContext = execution.observability,
      httpDriver = FakeHttpDriver.okText("noop"),
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
