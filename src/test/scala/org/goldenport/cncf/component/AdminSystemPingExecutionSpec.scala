package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.path.{AliasLoader, AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.CncfVersion
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 20, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminSystemPingExecutionSpec
  extends AnyWordSpec
  with Matchers {

  "ComponentLogic" should {
    "execute resolver-normalized admin.system.ping requests" in {
      withAliasContext(RunMode.Command, aliasConfig("ping" -> "admin.system.ping")) {
        (aliasResolver, context) =>
          val versionedPing = context.formatPing
          val subsystem = DefaultSubsystemFactory.default(Some("command"))
          val adminComponent = subsystem.components
            .collectFirst { case comp if comp.name == "admin" => comp }
            .getOrElse(fail("admin component not found"))
          val resolver = subsystem.resolver

          Seq("admin.system.ping", "ping").foreach { selector =>
            val normalized =
              PathPreNormalizer.rewriteSelector(selector, RunMode.Command, aliasResolver)
            val request = buildRequest(resolver, normalized)
            val response = executePing(adminComponent, request)

            response match {
              case Consequence.Success(OperationResponse.Scalar(output)) =>
                output shouldBe versionedPing
              case other =>
                fail(s"expected ping scalar but got $other")
            }
          }
      }
    }
  }

  private def executePing(
    component: Component,
    request: Request
  ): Consequence[OperationResponse] = {
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = component.logic.createActionCall(action)
        component.logic.execute(call)
      case other =>
        Consequence.failure(s"unexpected OperationRequest type: ${other.getClass.getName}")
    }
  }

  private def buildRequest(
    resolver: OperationResolver,
    selector: String
  ): Request = {
    resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Request.of(
          component = component,
          service = service,
          operation = operation
        )
      case other =>
        fail(s"resolver failed for $selector: $other")
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
      name = "ping-execution-spec",
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
