package org.goldenport.cncf.testutil

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemDescriptor, GenericSubsystemFactory, Subsystem}

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
object SubsystemTestFixture {
  val emptyConfiguration: ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration.empty,
      ConfigurationTrace.empty
    )

  final case class Params(
    name: String = "test",
    version: Option[String] = None,
    configuration: ResolvedConfiguration = emptyConfiguration,
    aliasResolver: AliasResolver = AliasResolver.empty,
    runMode: RunMode = RunMode.Command
  )

  sealed trait Startup {
    def create(params: Params): Subsystem
  }

  object Startup {
    case object Empty extends Startup {
      def create(params: Params): Subsystem =
        Subsystem(
          name = params.name,
          version = params.version,
          configuration = params.configuration,
          aliasResolver = params.aliasResolver,
          runMode = params.runMode
        )
    }

    final case class Default(mode: Option[String] = Some("command")) extends Startup {
      def create(params: Params): Subsystem =
        DefaultSubsystemFactory.default(
          mode = mode,
          configuration = params.configuration
        )
    }

    final case class Generic(descriptor: GenericSubsystemDescriptor, mode: Option[String] = Some("command")) extends Startup {
      def create(params: Params): Subsystem =
        GenericSubsystemFactory.default(
          descriptor = descriptor,
          mode = mode,
          configuration = params.configuration
        )
    }

    final case class Custom(createSubsystem: Params => Subsystem) extends Startup {
      def create(params: Params): Subsystem =
        createSubsystem(params)
    }
  }

  def withSubsystem[A](
    startup: Startup = Startup.Empty,
    params: Params = Params()
  )(body: Subsystem => A): A = {
    val subsystem = startup.create(params)
    try {
      body(subsystem)
    } finally {
      subsystem.shutdown()
    }
  }

  def subsystemScope(name: String): ScopeContext =
    ScopeContext(
      kind = ScopeKind.Subsystem,
      name = name,
      parent = None,
      observabilityContext = ExecutionContext.create().observability
    )
}
