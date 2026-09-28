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
private[jobcontrol] trait JobControlRuntimeSupport { self: JobControlComponent.type =>
  import JobControlComponent.*

  private[jobcontrol] final class DefaultJobService(component: Component) extends JobService {
    private val _definitions: TrieMap[String, JobDefinitionEntity] =
      TrieMap.empty
    private val _runtime_bridge = new JclRuntimeBridge(component)

    private final case class Submission(
      jobids: Vector[JobId],
      response: Consequence[OperationResponse]
    )

    def listManagementJobs(request: JobManagementQuery)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobManagementPage] =
      component.jobEngine.queryPage(request)

    def getManagementJobDetail(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobManagementDetail] =
      component.jobEngine.queryManagementDetail(jobId).flatMap(
        _.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"job:$jobId"))
      )

    def getManagementJobResult(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobManagementResult] =
      component.jobEngine.queryManagementResult(jobId).flatMap(
        _.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"job:$jobId"))
      )

    def listManagementJobTasks(jobId: JobId, offset: Int, limit: Int)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTaskPage] =
      component.jobEngine.queryManagementTasks(jobId, offset, limit).flatMap(
        _.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"job:$jobId"))
      )

    def listManagementJobTimeline(jobId: JobId, offset: Int, limit: Int)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTimelinePage] =
      component.jobEngine.queryManagementTimeline(jobId, offset, limit).flatMap(
        _.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"job:$jobId"))
      )

    def getManagementJobTaskExecutionTree(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTraceTree] =
      component.jobEngine.queryManagementTaskExecutionTree(jobId).flatMap(
        _.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"job:$jobId"))
      )

    def getManagementJobTaskDetail(jobId: JobId, taskId: TaskId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTaskDetail] =
      component.jobEngine.queryManagementTaskDetail(jobId, taskId).flatMap(
        _.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"job:$jobId/$taskId"))
      )

    def getJobStatus(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobQueryReadModel] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(model) => Consequence.success(model)
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def loadJobHistory(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTimelinePage] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.jobEngine.queryTimeline(jobId) match {
            case Some(page) => Consequence.success(page)
            case None => Consequence.operationNotFound(s"job history:${jobId.value}")
          }
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def getJobCalltree(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record] =
      component.jobEngine.queryVisible(jobId).map {
        case Some(model) =>
          _job_calltree_record(model)
        case None =>
          _job_calltree_not_found_record(jobId)
      }

    def getTaskExecutionTree(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTraceTree] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.jobEngine.queryTaskExecutionTree(jobId) match {
            case Some(tree) => Consequence.success(tree)
            case None => Consequence.operationNotFound(s"job task tree:${jobId.value}")
          }
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def getTaskDetail(jobId: JobId, taskId: TaskId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTaskDetail] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.jobEngine.queryTaskDetail(jobId, taskId) match {
            case Some(detail) => Consequence.success(detail)
            case None => Consequence.operationNotFound(s"job task:${jobId.value}/${taskId.value}")
          }
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def getJobResult(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobResult] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.logic.getJobResult(jobId) match {
            case Some(result) => Consequence.success(result)
            case None => Consequence.operationNotFound(s"job result:${jobId.value}")
          }
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def awaitJobResult(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[OperationResponse] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) => component.logic.awaitJobResult(jobId)
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def describeJobDefinition(body: String, format: RecordFormat): Consequence[JobBatchDefinition] =
      JobBatchDefinition.parse(body, format)

    def submitJobDefinition(body: String, format: RecordFormat)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobBatchSubmissionResult] =
      _submit_definition_ref(body) match {
        case Some(ref) =>
          _definition_by_ref(ref).flatMap { definition =>
            _submit_definition_entity(definition)
          }
        case None =>
          JobBatchDefinition.parse(body, format).flatMap { batch =>
            if (batch.jobs.size != 1)
              Consequence.argumentInvalid(
                "submit_job_definition requires exactly one job in jobs[]"
              )
            else
              _submit_batch(batch, None)
          }
      }

    def submitJobBatch(body: String, format: RecordFormat)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobBatchSubmissionResult] =
      JobBatchDefinition.parse(body, format).flatMap(_submit_batch(_, None))

    def compareJobProfile(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(model) => Consequence.success(JobProfileComparison.compare(model).toRecord)
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def reconstructJobProfile(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(model) =>
          Consequence.success(JobBatchDefinition(
            Vector(JobProfileReconstructor.reconstruct(model)),
            org.goldenport.cncf.job.JobJclRootKind.SingleJob
          ).toRecord)
        case None =>
          Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def createJobDefinition(
      key: String,
      body: String,
      format: RecordFormat,
      status: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _existing_definition_by_ref(key).flatMap {
        case Some(_) =>
          Consequence.stateConflict(s"JobDefinition already exists: $key")
        case None =>
          _definition_entity(key, body, format, status.getOrElse("draft")).flatMap { entity =>
            _create_definition(entity).map(_.toRecord())
          }
      }

    def updateJobDefinition(
      key: String,
      body: String,
      format: RecordFormat,
      status: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _definition_snapshot_by_ref(key).flatMap { snapshot =>
        val current = snapshot.entity
        for {
          parsed <- _definition_payload(key, body, format, status)
          updated = JobDefinitionEntity.updated(
            current = current,
            jclSource = body,
            jclformat = JobBatchDefinition.formatName(format),
            profile = parsed._1.profile,
            flowSource = parsed._1.flow.map(_.show),
            eventsSource = parsed._1.events.map(_.show),
            onEventSource = parsed._1.onEvent.map(_.show),
            status = parsed._2,
            targetAction = parsed._1.target.action,
            now = summon[org.goldenport.cncf.context.ExecutionContext].clock.instant()
          )
          saved <- _save_definition(
            updated,
            snapshot.revision
          )
        } yield saved.toRecord()
      }

    def activateJobDefinition(key: String)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record] =
      _change_definition_status(key, JobDefinitionStatus.Active)

    def retireJobDefinition(key: String)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record] =
      _change_definition_status(key, JobDefinitionStatus.Retired)

    def getJobDefinition(key: String)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record] =
      _definition_by_ref(key).map(_.toRecord())

    def searchJobDefinitions()(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record] =
      Consequence.success(
        Record.data(
          "jobDefinitions" -> _definitions.values.toVector.sortBy(_.key).map(_.toRecord())
        )
      )

    private def _submit_batch(
      batch: JobBatchDefinition,
      snapshot: Option[JobDefinitionSnapshot]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      _submit_jobs(batch.jobs, Vector.empty, snapshot)

    private def _submit_jobs(
      jobs: Vector[JobDefinition],
      submitted: Vector[JobId],
      snapshot: Option[JobDefinitionSnapshot],
      index: Int = 0
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      jobs.headOption match {
        case None =>
          Consequence.success(JobBatchSubmissionResult(submitted, success = true))
        case Some(job) =>
          _submit_one(job, snapshot).flatMap { submission =>
            val updated = submitted ++ submission.jobids
            submission.response match {
              case Consequence.Success(_) =>
                _submit_jobs(jobs.drop(1), updated, snapshot, index + 1)
              case Consequence.Failure(conclusion) =>
                _run_failure_hook(job.onFailure).map { hook =>
                  JobBatchSubmissionResult(
                    submittedJobIds = updated,
                    success = false,
                    stoppedAtIndex = Some(index),
                    stoppedAtName = Some(job.name),
                    failureMessage = Some(conclusion.show),
                    failureHookJobId = hook._1,
                    failureHookMessage = hook._2
                  )
                }
            }
          }
      }

    private def _run_failure_hook(
      hook: Option[JobFailureHook]
    )(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[(Option[JobId], Option[String])] =
      hook match {
        case None => Consequence.success((None, None))
        case Some(h) =>
          _runtime_bridge.submitAction(
            selector = h.action,
            parameters = h.parameters,
            requestSummary = Some(s"jcl.failure-hook:${h.action}"),
            persistence = JobPersistencePolicy.Persistent,
            declaredProfile = None
          ).map { case (jobid, response) =>
            response match {
              case Consequence.Success(_) => (Some(jobid), None)
              case Consequence.Failure(conclusion) => (Some(jobid), Some(conclusion.show))
            }
          }
      }

    private def _submit_one(
      job: JobDefinition,
      snapshot: Option[JobDefinitionSnapshot]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Submission] =
      job.semanticPlan.flatMap { plan =>
        job.target match {
          case x if x.action.nonEmpty =>
            _runtime_bridge.submitAction(
              selector = x.action.get,
              parameters = job.parameters,
              requestSummary = job.submit.requestSummary.orElse(Some(job.name)),
              persistence = job.submit.persistence,
              declaredProfile = job.profile,
              definitionSnapshot = snapshot,
              compensation = job.compensation,
              plan = plan
            ).map { case (jobid, response) =>
              Submission(Vector(jobid), response)
            }
          case x if x.workflow.nonEmpty =>
            _runtime_bridge.submitWorkflow(
              entry = x.workflow.get,
              parameters = job.parameters,
              requestSummary = job.submit.requestSummary.orElse(Some(job.name)),
              declaredProfile = job.profile,
              definitionSnapshot = snapshot
            ).map { case (jobids, response) =>
              Submission(jobids, response)
            }
          case _ =>
            Consequence.argumentInvalid("JCL target must contain action or workflow")
        }
      }

    private def _submit_definition_entity(
      entity: JobDefinitionEntity
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      if (!entity.isActive)
        Consequence.argumentInvalid(s"JobDefinition is not active: ${entity.key}")
      else
        JobBatchDefinition.parse(entity.jclSource, _entity_format(entity)).flatMap { batch =>
          if (batch.jobs.size != 1)
            Consequence.argumentInvalid(
              s"JobDefinition must contain exactly one job: ${entity.key}"
            )
          else
            _submit_batch(batch, Some(JobDefinitionSnapshot.from(entity)))
        }

    private def _definition_entity(
      key: String,
      body: String,
      format: RecordFormat,
      status: String
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobDefinitionEntity] =
      _definition_payload(key, body, format, Some(status)).map { case (job, parsedstatus) =>
        JobDefinitionEntity.create(
          id = JobDefinitionId.issue(summon[org.goldenport.cncf.context.ExecutionContext].idGeneration),
          key = key,
          jclSource = body,
          jclformat = JobBatchDefinition.formatName(format),
          profile = job.profile,
          flowSource = job.flow.map(_.show),
          eventsSource = job.events.map(_.show),
          onEventSource = job.onEvent.map(_.show),
          status = parsedstatus.getOrElse(JobDefinitionStatus.Draft),
          targetAction = job.target.action,
          now = summon[org.goldenport.cncf.context.ExecutionContext].clock.instant()
        )
      }

    private def _definition_payload(
      key: String,
      body: String,
      format: RecordFormat,
      status: Option[String]
    )(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[(JobDefinition, Option[JobDefinitionStatus])] =
      for {
        parsedstatus <- status.map(s => JobDefinitionStatus.parse(s).map(Some(_))).getOrElse(
          Consequence.success(None)
        )
        batch <- JobBatchDefinition.parse(body, format)
        _ <- if (batch.jobs.size == 1) Consequence.unit
        else Consequence.argumentInvalid(s"JobDefinition must contain exactly one job: $key")
      } yield (batch.jobs.head, parsedstatus)

    private def _create_definition(
      entity: JobDefinitionEntity
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobDefinitionEntity] = {
      val store      = EntityStore.standard()
      val persistent = JobDefinitionEntity.entityPersistent
      store.create(
        entity
      )(using
        EntityPersistentCreate.fromPersistent(persistent),
        summon[org.goldenport.cncf.context.ExecutionContext]
      )
        .map { _ =>
        _definitions.put(entity.key, entity)
        entity
      }
    }

    private def _save_definition(
        entity: JobDefinitionEntity,
        expectedrevision: EntityRevision
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobDefinitionEntity] = {
      val store      = EntityStore.standard()
      val persistent = JobDefinitionEntity.entityPersistent
      store.saveDetached(
        entity,
        Some(expectedrevision),
        EntityMutationExecutionPolicy.default
      )(using persistent, summon[org.goldenport.cncf.context.ExecutionContext])
        .map { carrier =>
          val saved = carrier.entity
          _definitions.put(saved.key, saved)
          saved
        }
    }

    private def _change_definition_status(
      key: String,
      status: JobDefinitionStatus
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _definition_snapshot_by_ref(key).flatMap { snapshot =>
        val current = snapshot.entity
        val updated = current.copy(
          status = status,
          updatedAt = ctx.clock.instant()
        )
        _save_definition(
          updated,
          snapshot.revision
        ).map(_.toRecord())
      }

    private def _definition_snapshot_by_ref(
      ref: String
    )(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[EntitySnapshot[JobDefinitionEntity]] =
      _load_definition_snapshot_by_ref(ref).flatMap {
        case Some(snapshot) =>
          _cache_definition(snapshot.entity)
          Consequence.success(snapshot)
        case None =>
          Consequence.operationNotFound(s"JobDefinition:$ref")
      }

    private def _definition_by_ref(
      ref: String
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobDefinitionEntity] =
      _cached_definition_by_ref(ref) match {
        case Some(entity) =>
          Consequence.success(entity)
        case None =>
          _load_definition_snapshot_by_ref(ref).flatMap {
            case Some(snapshot) =>
              _cache_definition(snapshot.entity)
              Consequence.success(snapshot.entity)
            case None =>
              Consequence.operationNotFound(s"JobDefinition:$ref")
          }
      }

    private def _existing_definition_by_ref(
      ref: String
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Option[JobDefinitionEntity]] =
      _cached_definition_by_ref(ref) match {
        case Some(entity) => Consequence.success(Some(entity))
        case None =>
          _load_definition_snapshot_by_ref(ref).map(_.map { snapshot =>
            _cache_definition(snapshot.entity)
            snapshot.entity
          })
      }

    private def _cached_definition_by_ref(ref: String): Option[JobDefinitionEntity] = {
      val requested = _normalize_definition_key(ref)
      _definitions.get(requested).filter(_has_definition_key(_, requested))
    }

    private def _load_definition_snapshot_by_ref(
      ref: String
    )(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Option[EntitySnapshot[JobDefinitionEntity]]] = {
      val requested = _normalize_definition_key(ref)
      _load_definition_snapshot_by_key(requested)
    }

    private def _load_definition_snapshot(
      id: org.simplemodeling.model.datatype.EntityId,
      requested: String
    )(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Option[EntitySnapshot[JobDefinitionEntity]]] =
      EntityStore.standard().loadDetached[JobDefinitionEntity](id)(
        using
        JobDefinitionEntity.entityPersistent,
        summon[org.goldenport.cncf.context.ExecutionContext]
      ).map(_.map(carrier =>
        EntitySnapshot(carrier.entity, carrier.revision)
      ).filter(snapshot => _has_definition_key(snapshot.entity, requested)))

    private def _load_definition_snapshot_by_key(
      requested: String
    )(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Option[EntitySnapshot[JobDefinitionEntity]]] =
      EntityStore.standard()
        .search[JobDefinitionEntity](EntityQuery(
          collection = JobEntityCollections.JobDefinition,
          query = Query.plan(Record.empty, where = Query.Eq("key", requested)),
          scope = EntitySearchScope.Store
        ))
        .flatMap { results =>
          results.data.find(_has_definition_key(_, requested)) match {
            case Some(entity) => _load_definition_snapshot(entity.id, requested)
            case None => Consequence.success(None)
          }
        }

    private def _has_definition_key(
      entity: JobDefinitionEntity,
      requested: String
    ): Boolean =
      _normalize_definition_key(entity.key) == _normalize_definition_key(requested)

    private def _cache_definition(entity: JobDefinitionEntity): Unit =
      _definitions.put(_normalize_definition_key(entity.key), entity)

    private def _submit_definition_ref(body: String): Option[String] =
      "(?m)^\\s*jobDefinitionRef\\s*:\\s*([^\\s#]+)\\s*$".r
        .findFirstMatchIn(body)
        .map(_.group(1).trim)

    private def _normalize_definition_key(key: String): String =
      key.trim

  }

  private[jobcontrol] final class DefaultJobAdminService(component: Component) extends JobAdminService {
    def cancelJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Cancel))

    def suspendJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Suspend))

    def resumeJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Resume))

    def retryJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Retry))

    def loadJobEvents(jobId: JobId): Consequence[Vector[Record]] =
      component.eventStore match {
        case Some(store) =>
          store.query(EventStore.Query()).map { records =>
            records.filter { record =>
              record.payload.get("job-id").contains(jobId.value) ||
              record.attributes.get("job-id").contains(jobId.value)
            }.map(_event_record)
          }
        case None =>
          Consequence.serviceUnavailable("event store is not available")
      }
  }

}
