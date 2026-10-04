package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.{WorkflowDefinitionId, WorkflowInstance}
import org.goldenport.protocol.operation.OperationResponse

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
/** Source-bound observations after the native producer's normal return (C6 R1..R7). */
private[cncf] object JobProducerOperationalAudit {
  def workflow(definitionId: WorkflowDefinitionId, instance: WorkflowInstance,
    jobId: JobId, actionId: ActionId)(using ExecutionContext): Unit =
    try {
      val jobid = _reference(jobId.value)
      val actionid = _reference(actionId.value)
      val instanceid = _reference(instance.id.value)
      val definitionid = _reference(definitionId.value)
      if (jobid.nonEmpty && actionid.nonEmpty && instanceid.nonEmpty && definitionid.nonEmpty)
        JobOperationalAuditTransport.producer(Vector(
          "producer" -> "workflow", "job-id" -> jobid.get,
          "operation-action-id" -> actionid.get, "workflow-instance-id" -> instanceid.get,
          "workflow-definition-id" -> definitionid.get, "workflow-status" -> "submitted",
          "occurred-at" -> instance.updatedAt.toString
        ))
    } catch { case NonFatal(_) => () }

  def jcl(jobId: JobId, actionId: ActionId, definitionSnapshot: Option[JobDefinitionSnapshot],
    response: Consequence[OperationResponse])(using ExecutionContext): Unit =
    try {
      val jobid = _reference(jobId.value)
      val actionid = _reference(actionId.value)
      if (jobid.nonEmpty && actionid.nonEmpty) {
        val definition: Vector[(String, String | Int | Long | Boolean)] = definitionSnapshot match {
          case None => Vector("job-definition-present" -> false)
          case Some(snapshot) =>
            val id = Option(snapshot).flatMap(x => _reference(x.id))
            Vector("job-definition-present" -> true) ++ id.fold(
              Vector[(String, String | Int | Long | Boolean)]("job-definition-id-omitted" -> true)
            )(value => Vector("job-definition-id" -> value))
        }
        val outcome = response match {
          case Consequence.Success(_) => "succeeded"
          case Consequence.Failure(_) => "failed"
        }
        JobOperationalAuditTransport.producer(Vector(
          "producer" -> "jcl-action", "job-id" -> jobid.get,
          "operation-action-id" -> actionid.get
        ) ++ definition ++ Vector("result-outcome" -> outcome))
      }
    } catch { case NonFatal(_) => () }

  // The native final ID constructors keep .value nonempty; retain this defensive guard
  // for null observation inputs and the reachable optional snapshot String ID (R5/PA2).
  private def _reference(value: String): Option[String] =
    Option(value).filter(x => x.nonEmpty && !x.exists(c => Character.isISOControl(c)) && x.getBytes(UTF_8).length <= 512)
}
