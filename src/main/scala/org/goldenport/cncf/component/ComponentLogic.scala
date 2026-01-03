package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.action.{Action, ActionCall}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{JobEngine, JobId, JobResult, JobStatus, JobTask}

/*
 * @since   Jan.  3, 2026
 * @version Jan.  4, 2026
 * @author  ASAMI, Tomoharu
 */
case class ComponentLogic(component: Component) {
  def jobEngine: JobEngine = component.jobEngine

  def makeOperationRequest(args: Array[String]): Consequence[OperationRequest] =
    component.protocolLogic.makeOperationRequest(args)

  def makeOperationRequest(request: org.goldenport.protocol.Request): Consequence[OperationRequest] =
    component.protocolLogic.makeOperationRequest(request)

  def makeStringOperationResponse(res: OperationResponse): Consequence[String] =
    component.protocolLogic.makeStringOperationResponse(res)

  def createActionCall(action: Action): ActionCall =
    component.actionEngine.createActionCall(action)

  def execute(ac: ActionCall): Consequence[OperationResponse] = // ActionResponse
    component.actionEngine.execute(ac)

  def submitJob(tasks: List[JobTask], ctx: ExecutionContext): JobId =
    component.jobEngine.submit(tasks, ctx)

  def getJobStatus(jobId: JobId): Option[JobStatus] =
    component.jobEngine.getStatus(jobId)

  def getJobResult(jobId: JobId): Option[JobResult] =
    component.jobEngine.getResult(jobId)
}
