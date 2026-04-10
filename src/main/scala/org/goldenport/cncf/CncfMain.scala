package org.goldenport.cncf

import java.nio.file.{Path, Paths}
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 23, 2026
 *  version Feb.  1, 2026
 *  version Mar. 26, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfMain extends GlobalObservable {
  final class CliFailed(val code: Int)
      extends RuntimeException(s"Command failed (exit=$code)")
      with scala.util.control.NoStackTrace

  private final case class LaunchParameters(
    activeRepositories: Either[String, Vector[ComponentRepository.Specification]],
    searchRepositories: Either[String, Vector[ComponentRepository.Specification]],
    front: CncfRuntime.RuntimeFrontParameters,
    invocation: CncfRuntime.RuntimeInvocationParameters
  )

  def main(args: Array[String]): Unit = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val bootstrap = CncfRuntime.bootstrap(cwd, args)
    val launch = _launch_parameters(bootstrap, cwd, args)

    val code: Int =
      try {
        (launch.activeRepositories, launch.searchRepositories) match {
          case (Left(message), _) =>
            Console.err.println(message)
            2
          case (_, Left(message)) =>
            Console.err.println(message)
            2
          case (Right(activeSpecs), Right(searchSpecs)) =>
            val extras = CncfRuntime.componentExtraFunction(activeSpecs, launch.front)
            val invocation = CncfRuntime.resolveSubsystemInvocation(launch.invocation, searchSpecs)
            CncfRuntime.runWithExtraComponents(invocation.actualArgs, extras)
        }
      } catch {
        case e: CliFailed => e.code
      }

    if (launch.front.forceExit) {
      sys.exit(code) // CLI adapter may exit only when --force-exit is requested.
    } else if (launch.front.noExit && code != 0) {
      throw new CliFailed(code)
    } else {
      ()
    }
  }

  private def _launch_parameters(
    bootstrap: CncfRuntime.RuntimeBootstrap,
    cwd: Path,
    args: Array[String]
  ): LaunchParameters = {
    val configuration = bootstrap.configuration
    val (reposResult, args1, noDefaultComponents) =
      _take_component_repository(configuration, args, cwd)
    val front =
      if (args1.sameElements(args)) bootstrap.front
      else CncfRuntime.frontParameters(configuration, args1)
    val invocation =
      if (front.residualArgs.sameElements(args)) bootstrap.invocation
      else CncfRuntime.canonicalInvocationParameters(configuration, front.residualArgs)
    LaunchParameters(
      activeRepositories = reposResult,
      searchRepositories = ComponentRepositorySpace.appendDefaultComponentRepository(reposResult, cwd, noDefaultComponents),
      front = front,
      invocation = invocation
    )
  }

  private val _no_default_components_flag = "--no-default-components"

  private def _take_component_repository(
    configuration: ResolvedConfiguration,
    args: Array[String],
    cwd: Path
  ): (Either[String, Vector[ComponentRepository.Specification]], Array[String], Boolean) = {
    val (specsresult, rest, nodefault) =
      ComponentRepositorySpace.extractArgs(configuration, args)
    val parsed = ComponentRepositorySpace.resolveSpecifications(specsresult, cwd, nodefault)
    (parsed, rest, nodefault)
  }
}
