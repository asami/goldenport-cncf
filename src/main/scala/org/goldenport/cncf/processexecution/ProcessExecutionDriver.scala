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
