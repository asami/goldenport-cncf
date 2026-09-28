package org.goldenport.cncf.component.builtin.jobcontrol

import java.time.Duration
import scala.collection.concurrent.TrieMap
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{
  ActionCall,
  CommandAction,
  ProcedureActionCall,
  QueryAction
}
import org.goldenport.cncf.component.{
  Component,
  ComponentCreate,
  ComponentDescriptor,
  ComponentId,
  ComponentInstanceId,
  EntityRuntimePlanProvider
}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{
  EntityPersistentCreate,
  EntityMutationExecutionPolicy,
  EntityQuery,
  EntityRevisionModelKind,
  EntityRevisionRepresentation,
  EntitySearchScope,
  EntitySnapshot,
  EntityStore
}
import org.simplemodeling.model.datatype.EntityRevision
import org.goldenport.cncf.entity.runtime.{
  EntityKind,
  EntityMemoryPolicy,
  EntityRuntimeDescriptor,
  EntityRuntimePlan,
  PartitionStrategy,
  WorkingSetPolicy,
  WorkingSetPolicyEvaluator,
  WorkingSetPolicySource
}
import org.goldenport.cncf.job.{
  JobBatchDefinition,
  JobBatchSubmissionResult,
  JobControlCommand,
  JobControlRequest,
  JobDefinition,
  JobDefinitionEntity,
  JobDefinitionId,
  JobDefinitionSnapshot,
  JobDefinitionStatus,
  JobFailureHook,
  JobId,
  JobPersistencePolicy,
  JobProfileComparison,
  JobProfileReconstructor,
  JobResult,
  JobTaskDetail,
  JobTraceTree,
  TaskId
}
import org.goldenport.cncf.job.{
  JobDataOrigin,
  JobEntityCollections,
  JobManagementDetail,
  JobManagementPage,
  JobManagementQuery,
  JobManagementResult,
  JobManagementSummary,
  JobQueryReadModel,
  JobStatus,
  JobTaskPage,
  JobTimelinePage
}
import org.goldenport.cncf.event.EventStore
import org.goldenport.cncf.openapi.{OpenApiHttpMethod, OpenApiOperationProjection}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.RecordFormat
import org.goldenport.schema.DataType
import org.goldenport.value.BaseContent

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[jobcontrol] trait JobControlRecordSupport { self: JobControlComponent.type =>
  import JobControlComponent.*

  private[jobcontrol] def _jcl_submission_response(
    core: ActionCall.Core,
    f: JobService => Consequence[JobBatchSubmissionResult]
  ): Consequence[OperationResponse] =
    core.component match {
      case Some(component) =>
        component.port.get[JobService].map(f) match {
          case Some(result) => result.map(x => OperationResponse.RecordResponse(x.toRecord))
          case None => Consequence.serviceUnavailable("job service is not available")
        }
      case None =>
        Consequence.serviceUnavailable("component is not initialized")
    }

  private[jobcontrol] def _job_profile_response(
    core: ActionCall.Core,
    f: JobService => Consequence[Record]
  ): Consequence[OperationResponse] =
    core.component match {
      case Some(component) =>
        component.port.get[JobService].map(f) match {
          case Some(result) => result.map(OperationResponse.RecordResponse.apply)
          case None => Consequence.serviceUnavailable("job service is not available")
        }
      case None =>
        Consequence.serviceUnavailable("component is not initialized")
    }

  private[jobcontrol] def _job_result_response(
    core: ActionCall.Core,
      jobid: JobId,
    f: JobService => Consequence[JobResult]
  ): Consequence[OperationResponse] =
    core.component match {
      case Some(component) =>
        component.port.get[JobService].map(f) match {
          case Some(result) =>
            result.flatMap {
              case JobResult.Success(response) => Consequence.success(response)
              case JobResult.Failure(conclusion) => Consequence.Failure(conclusion)
            }
          case None =>
            Consequence.serviceUnavailable("job service is not available")
        }
      case None =>
        Consequence.serviceUnavailable("component is not initialized")
    }


  private[jobcontrol] def _job_id(req: Request): Consequence[JobId] =
    req.arguments.find(_.name == "id") match {
      case Some(arg) => JobId.parse(arg.value.toString)
      case None =>
        req.properties.find(_.name == "id") match {
          case Some(prop) => JobId.parse(prop.value.toString)
          case None => Consequence.argumentMissing("id")
        }
    }

  private[jobcontrol] def _task_id(req: Request): Consequence[TaskId] =
    _string_argument(req, "taskId")
      .orElse(_string_argument(req, "task-id")) match {
        case Some(value) => TaskId.parse(value)
        case None => Consequence.argumentMissing("taskId")
      }

  private[jobcontrol] def _body(req: Request): Consequence[String] =
    req.arguments.find(_.name == "body").map(_.value.toString).filter(_.trim.nonEmpty)
      .orElse(req.properties.find(_.name == "body").map(_.value.toString).filter(_.trim.nonEmpty))
      .orElse(
        req.properties.find(_.name == "http.body").map(_.value.toString).filter(_.trim.nonEmpty)
      ) match {
      case Some(body) => Consequence.success(body)
      case None => Consequence.argumentMissing("body")
    }

  private[jobcontrol] def _key(req: Request): Consequence[String] =
    _string_argument(req, "key")
      .orElse(_string_argument(req, "jobDefinitionRef")) match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.argumentMissing("key")
      }

  private[jobcontrol] def _status(req: Request): Option[String] =
    _string_argument(req, "status")

  private[jobcontrol] def _jcl_format(req: Request): Consequence[RecordFormat] =
    JobBatchDefinition.parseFormat(
      _string_argument(req, "jclFormat")
        .orElse(_string_argument(req, "jcl-format"))
        .orElse(_string_argument(req, "format"))
    )

  private[jobcontrol] def _entity_format(entity: JobDefinitionEntity): RecordFormat =
    JobBatchDefinition.parseFormat(entity.jclFormat).toOption.getOrElse(
      JobBatchDefinition.DefaultFormat
    )

  private def _string_argument(req: Request, name: String): Option[String] =
    req.arguments.find(_.name == name).map(_.value.toString).filter(_.trim.nonEmpty)
      .orElse(req.properties.find(_.name == name).map(_.value.toString).filter(_.trim.nonEmpty))
      .map(_.trim)

  private def _job_matches(
    record: org.goldenport.cncf.event.EventRecord,
      jobid: JobId
  ): Boolean =
    record.payload.get("job-id").exists(_.toString == jobid.value) ||
      record.attributes.get("job-id").exists(_ == jobid.value)

  private[jobcontrol] def _job_record(model: JobQueryReadModel): Record =
    Record.data(
      "job-id" -> model.jobId.value,
      "status" -> model.status.toString,
      "persistence" -> model.persistence.toString,
      "origin" -> model.origin.toString,
      "submitter-principal-id" -> model.submitter.principalId,
      "submitter-subject-kind" -> model.submitter.subjectKind,
      "submitter-session-id" -> model.submitter.sessionId.getOrElse(""),
      "created-at" -> model.createdAt.toString,
      "updated-at" -> model.updatedAt.toString,
      "scheduled-start-at" -> model.scheduledStartAt.map(_.toString).getOrElse(""),
      "result-success" -> model.resultSummary.success,
      "result-message" -> model.resultSummary.message.getOrElse(""),
      "result" -> model.result.map(_.print).getOrElse(""),
      "event-triggered" -> model.lineage.eventTriggered,
      "event-name" -> model.lineage.eventName.getOrElse(""),
      "event-kind" -> model.lineage.eventKind.getOrElse(""),
      "parent-job-id" -> model.lineage.parentJobId.getOrElse(""),
      "correlation-id" -> model.lineage.correlationId.getOrElse(""),
      "saga-id" -> model.lineage.sagaId.getOrElse(""),
      "causation-id" -> model.lineage.causationId.getOrElse(""),
      "source-subsystem" -> model.lineage.sourceSubsystem.getOrElse(""),
      "source-component" -> model.lineage.sourceComponent.getOrElse(""),
      "target-subsystem" -> model.lineage.targetSubsystem.getOrElse(""),
      "target-component" -> model.lineage.targetComponent.getOrElse(""),
      "reception-rule" -> model.lineage.receptionRule.getOrElse(""),
      "reception-policy" -> model.lineage.receptionPolicy.getOrElse(""),
      "policy-source" -> model.lineage.policySource.getOrElse(""),
      "job-relation" -> model.lineage.jobRelation.getOrElse(""),
      "task-relation" -> model.lineage.taskRelation.getOrElse(""),
      "transaction-relation" -> model.lineage.transactionRelation.getOrElse(""),
      "saga-relation" -> model.lineage.sagaRelation.getOrElse(""),
      "failure-policy" -> model.lineage.failurePolicy.getOrElse(""),
      "failure-disposition" -> model.lineage.failureDisposition.print,
      "continuation-task-ids" -> model.continuation.taskIds.map(_.value),
      "continuation-tasks" -> model.continuation.tasks.map(_continuation_task_record),
      "continuation-mode" -> model.continuation.mode.getOrElse(""),
      "continuation-policy" -> model.continuation.policy.getOrElse(""),
      "retry-kind" -> model.retry.kind.print,
      "retry-attempt-count" -> model.retry.attemptCount,
      "retry-max-attempts" -> model.retry.maxAttempts,
      "retry-next-due-at" -> model.retry.nextRetryDueAt.map(_.toString).getOrElse(""),
      "retry-exhausted" -> model.retry.exhausted,
      "recovery-required" -> model.retry.recoveryRequired,
      "dead-letter" -> model.retry.deadLetter,
      "poison" -> model.retry.poison,
      "retry-user-action" -> model.retry.lastFailureUserAction.getOrElse(""),
      "calltree" -> model.calltree.map(_.show).getOrElse(""),
      "calltree-saved" -> model.debug.calltreeSaved,
      "calltree-storage" -> model.debug.calltreeStorage.getOrElse(""),
      "calltree-serialized-bytes" -> model.debug.calltreeSerializedBytes.getOrElse(0),
      "calltree-drop-reason" -> model.debug.calltreeDropReason.getOrElse(""),
      "task-count" -> model.tasks.totalCount,
      "tasks" -> model.tasks.tasks.map { task =>
        _task_record(task)
      },
      "timeline" -> model.timeline.events.map(_timeline_event_record),
      "debug-request-summary" -> model.debug.requestSummary.getOrElse(""),
      "debug-execution-notes" -> model.debug.executionNotes,
      "debug-parameters" -> model.debug.parameters.toVector.sortBy(_._1).map { case (k, v) =>
        s"$k=$v"
      }
    )

  private def _continuation_task_record(
    task: org.goldenport.cncf.job.JobContinuationTaskRef
  ): Record =
    Record.dataAuto(
      "task-id" -> task.taskId.value,
      "status" -> task.status,
      "action" -> task.action,
      "parent-task-id" -> task.parentTaskId.map(_.value)
    )

  private[jobcontrol] def _timeline_record(
      jobid: JobId,
    page: JobTimelinePage
  ): Record =
    Record.data(
      "job-id"        -> jobid.value,
      "offset" -> page.offset,
      "limit" -> page.limit,
      "total-count" -> page.totalCount,
      "fetched-count" -> page.fetchedCount,
      "events" -> page.events.map(_timeline_event_record)
    )

  private[jobcontrol] def _job_calltree_record(
    model: JobQueryReadModel
  ): Record =
    Record.data(
      "job-id" -> model.jobId.value,
      "calltree-saved" -> model.debug.calltreeSaved,
      "calltree-storage" -> model.debug.calltreeStorage.getOrElse(""),
      "calltree-serialized-bytes" -> model.debug.calltreeSerializedBytes.getOrElse(0),
      "calltree-payload-reference" -> model.debug.calltreePayloadReference,
      "calltree-drop-reason" -> model.debug.calltreeDropReason.getOrElse(""),
      "calltree" -> model.calltree.getOrElse(Record.empty)
    )

  private[jobcontrol] def _job_calltree_not_found_record(
      jobid: JobId
  ): Record =
    Record.data(
      "job-id"               -> jobid.value,
      "calltree-saved" -> false,
      "calltree-drop-reason" -> "job_not_found",
      "calltree" -> Record.empty
    )

  private[jobcontrol] def _task_tree_record(
    tree: JobTraceTree
  ): Record =
    Record.data(
      "job-id" -> tree.jobId.value,
      "roots" -> tree.roots.map(_task_node_record)
    )

  private[jobcontrol] def _task_detail_record(
    detail: JobTaskDetail
  ): Record =
    Record.data(
      "job-id" -> detail.jobId.value,
      "task" -> _task_record(detail.task),
      "events" -> detail.events.map(_timeline_event_record),
      "children" -> detail.children.map(_task_node_record)
    )

  private def _task_node_record(
    node: org.goldenport.cncf.job.JobTraceTaskNode
  ): Record =
    Record.data(
      "task-id" -> node.taskId.value,
      "parent-task-id" -> node.parentTaskId.map(_.value).getOrElse(""),
      "task-kind" -> node.taskKind,
      "relation" -> node.relation.getOrElse(""),
      "transaction-role" -> node.transactionRole.getOrElse(""),
      "transaction-scope" -> node.transactionScope.getOrElse(""),
      "status" -> node.status.toString,
      "transaction-outcome" -> node.transactionOutcome.getOrElse(""),
      "compensation-status" -> node.compensationStatus.getOrElse(""),
      "recovery-required" -> node.recoveryRequired,
      "events" -> node.events.map(_timeline_event_record),
      "children" -> node.children.map(_task_node_record)
    )

  private[jobcontrol] def _task_record(
    task: org.goldenport.cncf.job.JobTaskReadModel
  ): Record =
    Record.data(
      "task-id" -> task.taskId.value,
      "parent-task-id" -> task.parentTaskId.map(_.value).getOrElse(""),
      "status" -> task.status.toString,
      "success" -> task.result.success,
      "message" -> task.result.message.getOrElse(""),
      "component" -> task.component.getOrElse(""),
      "service" -> task.service.getOrElse(""),
      "operation" -> task.operation.getOrElse(""),
      "task-kind" -> task.taskKind,
      "target-kind" -> task.targetKind.getOrElse(""),
      "relation" -> task.relation.getOrElse(""),
      "transaction-role" -> task.transactionRole.getOrElse(""),
      "transaction-scope" -> task.transactionScope.getOrElse(""),
      "transaction-outcome" -> task.transactionOutcome.getOrElse(""),
      "compensation-action-ref" -> task.compensationActionRef.getOrElse(""),
      "compensates-task-id" -> task.compensatesTaskId.map(_.value).getOrElse(""),
      "compensation-status" -> task.compensationStatus.getOrElse(""),
      "compensation-failure-summary" -> task.compensationFailureSummary.getOrElse(""),
      "recovery-required" -> task.recoveryRequired,
      "started-at" -> task.startedAt.toString,
      "finished-at" -> task.finishedAt.map(_.toString).getOrElse("")
    )

  private def _timeline_event_record(
    event: org.goldenport.cncf.job.JobTimelineEvent
  ): Record =
    Record.data(
      "sequence" -> event.sequence,
      "occurred-at" -> event.occurredAt.toString,
      "kind" -> event.kind,
      "task-id" -> event.taskId.map(_.value).getOrElse(""),
      "parent-task-id" -> event.parentTaskId.map(_.value).getOrElse(""),
      "note" -> event.note.getOrElse("")
    )

  private[jobcontrol] def _event_record(
    event: org.goldenport.cncf.event.EventRecord
  ): Record =
    Record.data(
      "event-id" -> event.id.value,
      "name" -> event.name,
      "kind" -> event.kind,
      "sequence" -> event.sequence,
      "created-at" -> event.createdAt.toString,
      "lane" -> event.lane.value,
      "persistent" -> event.persistent,
      "payload" -> event.payload.toVector.sortBy(_._1).map { case (k, v) =>
        Record.data(k -> v)
      }
    )
}
