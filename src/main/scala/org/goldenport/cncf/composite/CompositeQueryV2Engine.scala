package org.goldenport.cncf.composite

import java.util.concurrent.TimeUnit
import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.JobEngine
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeQueryV2Engine(
  subsystem: Subsystem,
  jobEngine: JobEngine,
  targets: CompositeQueryV2Targets
) {
  def executeBlocking(
    request: CompositeQueryV2Request,
    cancellation: CompositeQueryV2Cancellation = CompositeQueryV2Cancellation.fresh
  )(using context: ExecutionContext): Consequence[CompositeQueryV2Response] = {
    val entrynanos = System.nanoTime()
    context match {
      case _: ExecutionContext.Instance => _preflight(request).flatMap { admitted =>
      if (context.jobContext.jobId.nonEmpty)
        Consequence.operationInvalid("composite-query-v2.nested-job-admission")
      else if (context.framework.traceJob || admitted.branches.exists(_has_trace_job))
        Consequence.operationInvalid("composite-query-v2.trace-job")
      else if (cancellation.isCancelled)
        Consequence.success(_cancelled(admitted))
      else
        new CompositeQueryV2Coordinator(
          subsystem,
          jobEngine,
          targets,
          admitted,
          cancellation,
          entrynanos + TimeUnit.MILLISECONDS.toNanos(admitted.policy.timeoutMillis)
        ).execute(context)
      }
      case _ => Consequence.operationInvalid("composite-query-v2.context-unavailable")
    }
  }

  private def _preflight(request: CompositeQueryV2Request): Consequence[CompositeQueryV2Request] =
    request.validate.flatMap { admitted =>
      admitted.branches.foldLeft(Consequence.success(())) { (result, branch) =>
        result.flatMap(_ => targets.resolve(branch.target, subsystem).map(_ => ()))
      }.map(_ => admitted)
    }

  private def _has_trace_job(branch: CompositeQueryV2Branch): Boolean = {
    val keys = Set(
      RuntimeConfig.debugTraceJobKey,
      RuntimeConfig.runtimeDebugTraceJobKey,
      "cncf.debug.trace-job",
      "cncf.runtime.debug.trace-job",
      "x-textus-debug-trace-job"
    ).map(_.toLowerCase(java.util.Locale.ROOT))
    branch.request.properties.exists { property =>
      keys.contains(property.name.toLowerCase(java.util.Locale.ROOT)) &&
        _truthy(property.value.toString)
    }
  }

  private def _truthy(value: String): Boolean =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => true
      case _ => false
    }

  private def _cancelled(request: CompositeQueryV2Request): CompositeQueryV2Response = {
    val results = request.branches.map { branch =>
      CompositeQueryV2BranchResult(
        branch.branchId,
        branch.required,
        CompositeQueryV2Outcome.Cancelled,
        None,
        Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.Cancelled))
      )
    }
    val diagnostics = request.branches.map { branch =>
      val targetid = branch.target match {
        case CompositeQueryV2Target.Local => "local"
        case CompositeQueryV2Target.Subsystem(id) => id
      }
      CompositeQueryV2Diagnostic(
        branch.branchId,
        targetid,
        CompositeQueryV2Outcome.Cancelled,
        Some(CompositeQueryV2FailureCode.Cancelled),
        0L,
        0L,
        true,
        false,
        None
      )
    }
    CompositeQueryV2Response(results, diagnostics, CompositeQueryV2AggregateStatus.Cancelled)
  }
}

object CompositeQueryV2Engine {
  def apply(
    subsystem: Subsystem,
    jobEngine: JobEngine,
    targets: CompositeQueryV2Targets
  ): CompositeQueryV2Engine =
    new CompositeQueryV2Engine(subsystem, jobEngine, targets)
}
