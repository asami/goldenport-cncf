package org.goldenport.cncf.composite

import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{ActionId, JobPersistencePolicy, JobTask, TaskFailed, TaskOutcome, TaskSucceeded}
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.operation.OperationResponse

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[composite] final case class CompositeQueryV2TaskResult(
  branchid: String,
  targetid: String,
  response: Option[OperationResponse],
  failure: Option[CompositeQueryV2FailureCode],
  cancelled: Boolean,
  startednanos: Long,
  completednanos: Long
)

private[composite] final class CompositeQueryV2BranchTask(
  branch: CompositeQueryV2Branch,
  targetId: String,
  protocol: CompositeQueryV2Protocol,
  invocation: CompositeQueryV2Invocation,
  onTerminal: CompositeQueryV2TaskResult => Unit,
  onBodyExit: String => Unit
) extends JobTask {
  val actionId: ActionId = ActionId.generate()
  private var _staged = Option.empty[CompositeQueryV2TaskResult]
  private var _cancelled = false

  override def taskKind: String = "composite-query-v2"
  override def targetKind: Option[String] = Some("query")
  override def componentName: Option[String] = None
  override def serviceName: Option[String] = None
  override def operationName: Option[String] = Some("composite-query-v2")
  override def requestSummary: Option[String] = Some("composite-query-v2")
  override def requestParameters: Map[String, String] = Map.empty
  override def defaultPersistence: JobPersistencePolicy = JobPersistencePolicy.Ephemeral

  def cancel(): Unit = synchronized {
    _cancelled = true
  }

  def run(context: ExecutionContext): TaskOutcome = {
    val started = System.nanoTime()
    val result = try {
      if (_is_cancelled(context))
        CompositeQueryV2TaskResult(branch.branchId, targetId, None, Some(CompositeQueryV2FailureCode.Cancelled), true, started, System.nanoTime())
      else if (context.jobContext.cancellationScope.isEmpty)
        CompositeQueryV2TaskResult(branch.branchId, targetId, None, Some(CompositeQueryV2FailureCode.ContextUnavailable), false, started, System.nanoTime())
      else {
        try {
          protocol.invoke(invocation, branch, context) match {
            case Consequence.Success(reply) =>
              CompositeQueryV2TaskResult(branch.branchId, targetId, Some(reply.response), None, false, started, System.nanoTime())
            case Consequence.Failure(conclusion) =>
              CompositeQueryV2TaskResult(branch.branchId, targetId, None, Some(_failure_code(conclusion)), false, started, System.nanoTime())
          }
        } catch {
          case _: InterruptedException =>
            Thread.currentThread.interrupt()
            CompositeQueryV2TaskResult(branch.branchId, targetId, None, Some(CompositeQueryV2FailureCode.Cancelled), true, started, System.nanoTime())
          case NonFatal(_) =>
            CompositeQueryV2TaskResult(branch.branchId, targetId, None, Some(CompositeQueryV2FailureCode.InternalFailure), false, started, System.nanoTime())
        }
      }
    } finally {
      // This is deliberately separate from canonical settlement: a provider may
      // refuse durable settlement after the physical query body has left its worker.
      onBodyExit(branch.branchId)
    }
    synchronized { _staged = Some(result) }
    result.failure match {
      case Some(_) => TaskFailed(Consequence.operationInvalid("composite-query-v2.branch-failed").conclusion)
      case None => TaskSucceeded(OperationResponse.void)
    }
  }

  override def observeCanonicalOutcome(
    outcome: TaskOutcome,
    context: ExecutionContext,
    cancelled: Boolean
  ): Unit = {
    val staged = synchronized {
      _staged.getOrElse {
        CompositeQueryV2TaskResult(
          branch.branchId,
          targetId,
          None,
          Some(if (cancelled) CompositeQueryV2FailureCode.Cancelled else CompositeQueryV2FailureCode.InternalFailure),
          cancelled,
          System.nanoTime(),
          System.nanoTime()
        )
      }
    }
    val result =
      if (cancelled) staged.copy(response = None, failure = Some(CompositeQueryV2FailureCode.Cancelled), cancelled = true)
      else outcome match {
        case _: TaskFailed if staged.failure.isEmpty => staged.copy(response = None, failure = Some(CompositeQueryV2FailureCode.InternalFailure))
        case _ => staged
      }
    onTerminal(result)
  }

  override def observeAdmissionFailure(
    conclusion: Conclusion,
    context: ExecutionContext
  ): Unit =
    onTerminal(CompositeQueryV2TaskResult(
      branch.branchId,
      targetId,
      None,
      Some(CompositeQueryV2FailureCode.SchedulerRejected),
      false,
      System.nanoTime(),
      System.nanoTime()
    ))

  private def _is_cancelled(context: ExecutionContext): Boolean = synchronized {
    _cancelled || context.jobContext.cancellationScope.exists(_.isCancelled)
  }

  private def _failure_code(conclusion: Conclusion): CompositeQueryV2FailureCode = {
    if (conclusion.getException.nonEmpty) CompositeQueryV2FailureCode.InternalFailure
    else {
      val webcode = Conclusion.Status.webCodeOf(conclusion).code
      conclusion.observation.taxonomy.category match {
        case Taxonomy.Category.Security => CompositeQueryV2FailureCode.AccessDenied
        case _ if webcode == 401 || webcode == 403 => CompositeQueryV2FailureCode.AccessDenied
        case Taxonomy.Category.Service | Taxonomy.Category.Network | Taxonomy.Category.DataStore if webcode >= 500 =>
          CompositeQueryV2FailureCode.QueryFailed
        case _ => CompositeQueryV2FailureCode.QueryRejected
      }
    }
  }
}
