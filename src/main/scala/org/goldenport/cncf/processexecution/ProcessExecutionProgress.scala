package org.goldenport.cncf.processexecution

import scala.util.control.NonFatal
import io.circe.Json
import org.goldenport.cncf.workspace.WorkspaceId

/*
 * @since   Oct. 20, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
enum ProcessExecutionProgressState {
  case Launching, Started, Waiting, Terminal, Uncertain
}

/** Metadata observations only: no command arguments, environment or captured output. */
final case class ProcessExecutionProgressEvent(
  operation: SbtOperation,
  workspaceId: WorkspaceId,
  programIdentity: String,
  state: ProcessExecutionProgressState,
  pid: Option[Long] = None,
  termination: Option[ProcessExecutionTermination] = None,
  exitCode: Option[Int] = None,
  elapsedMillis: Option[Long] = None,
  launchTimeoutMillis: Option[Long] = None,
  executionTimeoutMillis: Option[Long] = None,
  terminationGraceMillis: Option[Long] = None
)

/** Supplementary visibility; an observer never supplies an execution decision or result. */
trait ProcessExecutionProgress {
  def report(event: ProcessExecutionProgressEvent): Unit
}

object ProcessExecutionProgress {
  val none: ProcessExecutionProgress = new ProcessExecutionProgress {
    def report(event: ProcessExecutionProgressEvent): Unit = ()
  }

  val stderr: ProcessExecutionProgress = new ProcessExecutionProgress {
    def report(event: ProcessExecutionProgressEvent): Unit =
      System.err.println(jsonLine(event))
  }

  private[processexecution] def jsonLine(event: ProcessExecutionProgressEvent): String = {
    val fields = Vector(
      "operation" -> Json.fromString(event.operation match {
        case SbtOperation.Compile => "compile"
        case SbtOperation.TestCompile => "test-compile"
        case SbtOperation.Test => "test"
        case SbtOperation.TestOnly => "test-only"
        case SbtOperation.FixCheck => "fix-check"
        case SbtOperation.PublishLocal => "publish-local"
      }),
      "workspaceId" -> Json.fromString(event.workspaceId.print),
      "programIdentity" -> Json.fromString(event.programIdentity),
      "state" -> Json.fromString(event.state.toString.toLowerCase)
    ) ++ event.pid.map(x => "pid" -> Json.fromLong(x)) ++
      event.termination.map(x => "termination" -> Json.fromString(x.toString)) ++
      event.exitCode.map(x => "exitCode" -> Json.fromInt(x)) ++
      event.elapsedMillis.map(x => "elapsedMillis" -> Json.fromLong(x)) ++
      event.launchTimeoutMillis.map(x => "launchTimeoutMillis" -> Json.fromLong(x)) ++
      event.executionTimeoutMillis.map(x => "executionTimeoutMillis" -> Json.fromLong(x)) ++
      event.terminationGraceMillis.map(x => "terminationGraceMillis" -> Json.fromLong(x))
    "[cncf-sbt-event] " + Json.obj(fields*).noSpaces
  }

  private[processexecution] def observe(
    observer: ProcessExecutionProgress,
    execution: ResolvedProcessExecution,
    state: ProcessExecutionProgressState,
    process: Option[Process] = None,
    result: Option[ProcessExecutionResult] = None
  ): Unit =
    try {
      execution.definition._sbt_metadata.foreach { metadata =>
        // Failure to inspect a process cannot turn a result into terminal evidence.
        val pid = process.flatMap(p => try Some(p.pid()) catch { case NonFatal(_) => None })
        val exit = process.flatMap(p => try Some(p.exitValue()) catch { case NonFatal(_) => None })
        val confirmed = !result.exists(_.termination == ProcessExecutionTermination.TerminationUnconfirmed) &&
          (result.exists(_.termination == ProcessExecutionTermination.NotStarted) || exit.nonEmpty)
        val effective = if (state == ProcessExecutionProgressState.Terminal && !confirmed)
          ProcessExecutionProgressState.Uncertain else state
        observer.report(ProcessExecutionProgressEvent(metadata.operation, metadata.binding.workspaceId,
          execution.definition.safeProgramIdentity, effective, pid,
          if (effective == ProcessExecutionProgressState.Terminal) result.map(_.termination) else None,
          if (effective == ProcessExecutionProgressState.Terminal) exit else None,
          if (effective == ProcessExecutionProgressState.Terminal) result.map(_.elapsedMillis) else None,
          if (effective == ProcessExecutionProgressState.Launching) execution.effectiveLimits.launchTimeoutMillis else None,
          if (effective == ProcessExecutionProgressState.Launching) execution.effectiveLimits.executionTimeoutMillis else None,
          if (effective == ProcessExecutionProgressState.Launching) execution.effectiveLimits.terminationGraceMillis else None))
      }
    } catch {
      // Observability is best effort and must never alter lifecycle or effect decisions.
      case NonFatal(_) => ()
    }
}
