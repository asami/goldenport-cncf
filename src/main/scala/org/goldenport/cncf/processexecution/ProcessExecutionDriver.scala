package org.goldenport.cncf.processexecution

import java.util.concurrent.atomic.AtomicBoolean
import org.goldenport.Consequence
import org.goldenport.cncf.context.ScopeContext

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Runtime boundary for an already admitted Process Execution request.
 * Implementations belong to the runtime or a trusted provider, never to a
 * component operation.
 */
trait ProcessExecutionDriver {
  def safeIdentity: String
  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle]

  /**
   * Receives a UnitOfWork-owned execution WorkArea. Drivers that do not touch
   * host files retain the ordinary deterministic implementation.
   */
  def startC(
    execution: ResolvedProcessExecution,
    workArea: ProcessExecutionWorkArea
  ): Consequence[ProcessExecutionHandle] =
    startC(execution)
}

trait ProcessExecutionHandle {
  def awaitC: Consequence[ProcessExecutionResult]
  def cancelC: Consequence[Unit]
}

object ProcessExecutionDriver {
  def resolveC(scope: ScopeContext): Consequence[ProcessExecutionDriver] =
    scope.processExecutionDriverOption
      .map(Consequence.success)
      .getOrElse(Consequence.serviceUnavailable("Process Execution driver is not configured"))
}

/**
 * Explicit test-only Process Execution fixture. It records admitted intents
 * and returns predeclared terminal results without opening a host process.
 */
final case class ProcessExecutionTestProfile(
  results: Map[ProcessCapabilityId, ProcessExecutionResult]
) {
  lazy val driver: DeterministicProcessExecutionDriver =
    new DeterministicProcessExecutionDriver(results)
}

object ProcessExecutionTestProfile {
  /**
   * Builds an admitted test fixture without exposing runtime executable
   * metadata to component-level executable specifications.
   */
  def admittedC(
    capability: ProcessCapabilityId,
    result: ProcessExecutionResult,
    permittedarguments: Set[String] = Set.empty,
    allowedinputfiles: Set[ProcessArtifactName] = Set.empty
  ): Consequence[ProcessExecutionTestFixture] = {
    val profile = ProcessExecutionTestProfile(Map(capability -> result))
    for {
      definition <- ProcessProgramDefinition.fromRuntimeC(
        capability,
        "deterministic-test-program",
        "test-runtime-owned-location",
        Vector.empty,
        ProcessArgumentPolicy(Vector.empty, permittedarguments),
        _limits,
        Set.empty,
        allowedinputfiles = allowedinputfiles
      )
      policy <- ProcessExecutionPolicy.createC(Vector(definition))
      admission <- ProcessExecutionAdmission.createC(policy, Vector(ProcessExecutionGrant(capability)))
      execution <- admission.admitC(ProcessExecutionRequest(capability))
    } yield ProcessExecutionTestFixture(profile, policy, admission, execution)
  }

  private val _limits = ProcessExecutionLimits(
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L),
    Some(100L)
  )
}

final case class ProcessExecutionTestFixture(
  profile: ProcessExecutionTestProfile,
  policy: ProcessExecutionPolicy,
  admission: ProcessExecutionAdmission,
  execution: ResolvedProcessExecution
)

final class DeterministicProcessExecutionDriver private[processexecution] (
  results: Map[ProcessCapabilityId, ProcessExecutionResult]
) extends ProcessExecutionDriver {
  private var _executions = Vector.empty[ResolvedProcessExecution]

  val safeIdentity: String = "deterministic-test"

  def executions: Vector[ResolvedProcessExecution] = synchronized(_executions)

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] =
    results.get(execution.request.capability) match {
      case Some(result) =>
        synchronized {
          _executions = _executions :+ execution
        }
        Consequence.success(new _deterministic_handle(result))
      case None =>
        Consequence.serviceUnavailable("Process Execution test result is not configured")
    }
}

private final class _deterministic_handle(
  result: ProcessExecutionResult
) extends ProcessExecutionHandle {
  private val _cancelled = new AtomicBoolean(false)

  def awaitC: Consequence[ProcessExecutionResult] =
    if (_cancelled.get)
      Consequence.success(result.copy(termination = ProcessExecutionTermination.Cancelled))
    else
      Consequence.success(result)

  def cancelC: Consequence[Unit] = {
    _cancelled.set(true)
    Consequence.unit
  }
}
