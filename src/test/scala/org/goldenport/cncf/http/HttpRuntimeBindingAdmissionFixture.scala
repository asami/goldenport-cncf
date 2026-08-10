package org.goldenport.cncf.http

import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.{GlobalRuntimeContext, ScopeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}

/*
 * @since   Aug.  4, 2026
 * @version Aug. 10, 2026
 * @author  ASAMI, Tomoharu
 */
object HttpRuntimeBindingAdmissionFixture {
  def default(
    mode: Option[String] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
  ): Subsystem =
    RuntimeBindingAdmissionFixture.default(mode, configuration)

  def defaultWithScope(
    context: ScopeContext,
    mode: Option[RunMode] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver: AliasResolver = GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)
  ): Subsystem =
    RuntimeBindingAdmissionFixture.defaultWithScope(
      context,
      mode,
      configuration,
      aliasResolver
    )

  def server(
    engine: HttpExecutionEngine,
    operationDispatcherOption: Option[WebOperationDispatcher] = None
  ): Http4sHttpServer = {
    admit(engine.runtimeSubsystem)
    Http4sHttpServer.forEndpoint(
      engine,
      ServerEndpointPolicy.Endpoint(ServerEndpointPolicy.DEFAULT_HOST, Http4sHttpServer.defaultPort),
      operationDispatcherOption
    )
  }

  def admit(subsystem: Subsystem): Unit =
    RuntimeBindingAdmissionFixture.admit(subsystem)
}
