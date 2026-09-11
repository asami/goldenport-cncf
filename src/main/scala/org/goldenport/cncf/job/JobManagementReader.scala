package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobManagementReader {
  final case class Snapshot(
    models: Vector[JobQueryReadModel],
    results: Map[JobId, JobResult],
    durableTerminalIds: Set[JobId]
  )

  def queryPage(
    snapshot: Snapshot,
    request: JobManagementQuery,
    policy: JobQueryPolicy
  )(using ctx: ExecutionContext): Consequence[JobManagementPage] =
    request.validate.flatMap { _ =>
      val authorized = _authorized_models(snapshot.models, policy)
        .filter(request.accepts)
        .sortWith(_page_model_precedes)
      val filterfingerprint = request.filterFingerprint
      val callerfingerprint = JobManagementCursor.callerVisibilityFingerprint(ctx)
      val snapshotfingerprint = JobManagementCursor.snapshotFingerprint(authorized)
      request.cursor match {
        case None =>
          _page(
            authorized,
            request.limit,
            0,
            filterfingerprint,
            callerfingerprint,
            snapshotfingerprint
          )
        case Some(cursor) =>
          JobManagementCursor.decode(cursor).flatMap { payload =>
            if (payload.filterFingerprint != filterfingerprint)
              _invalid_query_cursor("request filter mismatch")
            else if (payload.callerVisibilityFingerprint != callerfingerprint)
              _invalid_query_cursor("caller visibility mismatch")
            else if (payload.authorizedSnapshotFingerprint != snapshotfingerprint)
              _expired_query_snapshot()
            else if (payload.nextOffset >= authorized.size)
              _invalid_query_cursor("offset out of range")
            else
              _page(
                authorized,
                request.limit,
                payload.nextOffset,
                filterfingerprint,
                callerfingerprint,
                snapshotfingerprint
              )
          }
      }
    }

  def queryDetail(
    snapshot: Snapshot,
    jobid: JobId,
    policy: JobQueryPolicy
  )(using ExecutionContext): Consequence[Option[JobManagementDetail]] =
    _authorized_model(snapshot.models, jobid, policy).map(_.map { model =>
      JobManagementDetail(
        summary = JobManagementSummary.from(model),
        retry = JobManagementRetrySummary.from(model.retry),
        resultSummary = model.resultSummary,
        taskCount = model.tasks.totalCount,
        timelineCount = model.timeline.totalCount
      )
    })

  def queryResult(
    snapshot: Snapshot,
    jobid: JobId,
    policy: JobQueryPolicy
  )(using ExecutionContext): Consequence[Option[JobManagementResult]] =
    _authorized_model(snapshot.models, jobid, policy).map(_.map { model =>
      snapshot.results.get(model.jobId) match {
        case Some(result) => JobManagementResult.Available(result)
        case None if snapshot.durableTerminalIds.contains(model.jobId) =>
          JobManagementResult.UnavailableAfterRestart(model.resultSummary)
        case None => JobManagementResult.Pending(model.resultSummary)
      }
    })

  def queryTasks(
    snapshot: Snapshot,
    jobid: JobId,
    offset: Int,
    limit: Int,
    policy: JobQueryPolicy
  )(using ExecutionContext): Consequence[Option[JobTaskPage]] =
    _validate_page(offset, limit).flatMap { _ =>
      _authorized_model(snapshot.models, jobid, policy).map(_.map(model =>
        _task_page(model.tasks.tasks, offset, limit)
      ))
    }

  def queryTimeline(
    snapshot: Snapshot,
    jobid: JobId,
    offset: Int,
    limit: Int,
    policy: JobQueryPolicy
  )(using ExecutionContext): Consequence[Option[JobTimelinePage]] =
    _validate_page(offset, limit).flatMap { _ =>
      _authorized_model(snapshot.models, jobid, policy).map(_.map(model =>
        _timeline_page(model.timeline.events, offset, limit)
      ))
    }

  def queryTaskExecutionTree(
    snapshot: Snapshot,
    jobid: JobId,
    policy: JobQueryPolicy
  )(using ExecutionContext): Consequence[Option[JobTraceTree]] =
    _authorized_model(snapshot.models, jobid, policy).map(_.map(_.traceTree))

  def queryTaskDetail(
    snapshot: Snapshot,
    jobid: JobId,
    taskid: TaskId,
    policy: JobQueryPolicy
  )(using ExecutionContext): Consequence[Option[JobTaskDetail]] =
    _authorized_model(snapshot.models, jobid, policy).map(_.flatMap { model =>
      model.tasks.tasks.find(_.taskId == taskid).map { task =>
        JobTaskDetail(
          jobId = jobid,
          task = task,
          events = model.timeline.events.filter(_.taskId.contains(taskid)).sortBy(_.sequence),
          children = model.traceTree.roots.flatMap(_find_children(_, taskid))
        )
      }
    })

  private def _authorized_models(
    models: Vector[JobQueryReadModel],
    policy: JobQueryPolicy
  )(using ctx: ExecutionContext): Vector[JobQueryReadModel] =
    models.flatMap { model =>
      policy.authorizeRead(model) match {
        case Consequence.Success(_) => Some(model)
        case Consequence.Failure(_) => None
      }
    }

  private def _authorized_model(
    models: Vector[JobQueryReadModel],
    jobid: JobId,
    policy: JobQueryPolicy
  )(using ctx: ExecutionContext): Consequence[Option[JobQueryReadModel]] =
    models.find(_.jobId == jobid) match {
      case Some(model) =>
        policy.authorizeRead(model) match {
          case Consequence.Success(_) => Consequence.success(Some(model))
          case Consequence.Failure(_) => Consequence.success(None)
        }
      case None => Consequence.success(None)
    }

  private def _page_model_precedes(
    left: JobQueryReadModel,
    right: JobQueryReadModel
  ): Boolean =
    if (left.updatedAt == right.updatedAt)
      left.jobId.print < right.jobId.print
    else
      left.updatedAt.isAfter(right.updatedAt)

  private def _page(
    authorized: Vector[JobQueryReadModel],
    limit: Int,
    offset: Int,
    filterfingerprint: String,
    callerfingerprint: String,
    snapshotfingerprint: String
  ): Consequence[JobManagementPage] = {
    val entries = authorized.slice(offset, offset + limit).map(JobManagementSummary.from)
    val nextoffset = offset + entries.size
    val next =
      if (nextoffset < authorized.size)
        Some(JobManagementCursor.encode(
          filterfingerprint,
          callerfingerprint,
          snapshotfingerprint,
          nextoffset
        ))
      else
        None
    Consequence.success(JobManagementPage(entries, authorized.size, next))
  }

  private def _invalid_query_cursor[A](detail: String): Consequence[A] =
    Consequence.operationInvalid("job.management-query.cursor", s"invalid cursor: $detail")

  private def _expired_query_snapshot[A](): Consequence[A] =
    Consequence.operationInvalid("job.management-query.cursor", "expired snapshot: authorized source changed")

  private def _validate_page(offset: Int, limit: Int): Consequence[Unit] =
    if (offset < 0)
      Consequence.operationInvalid("job.management-page", "offset must be zero or greater")
    else if (limit <= 0 || limit > JobManagementQuery.MaximumLimit)
      Consequence.operationInvalid(
        "job.management-page",
        s"limit must be in 1..${JobManagementQuery.MaximumLimit}"
      )
    else
      Consequence.unit

  private def _task_page(
    tasks: Vector[JobTaskReadModel],
    offset: Int,
    limit: Int
  ): JobTaskPage = {
    val sorted = tasks.sortBy(task => (task.startedAt.toEpochMilli, task.taskId.print))
    val page = _page_items(sorted, offset, limit)
    JobTaskPage(
      offset = page.offset,
      limit = page.limit,
      totalCount = page.total,
      fetchedCount = page.items.size,
      tasks = page.items
    )
  }

  private def _timeline_page(
    timeline: Vector[JobTimelineEvent],
    offset: Int,
    limit: Int
  ): JobTimelinePage = {
    val sorted = timeline.sortBy(event => (event.sequence, event.occurredAt.toEpochMilli))
    val page = _page_items(sorted, offset, limit)
    JobTimelinePage(
      offset = page.offset,
      limit = page.limit,
      totalCount = page.total,
      fetchedCount = page.items.size,
      events = page.items
    )
  }

  private def _page_items[A](items: Vector[A], offset: Int, limit: Int): PageItems[A] = {
    val safeoffset = math.max(0, offset)
    val safelimit = math.max(0, limit)
    PageItems(safeoffset, safelimit, items.size, items.slice(safeoffset, safeoffset + safelimit))
  }

  private final case class PageItems[A](
    offset: Int,
    limit: Int,
    total: Int,
    items: Vector[A]
  )

  private def _find_children(
    node: JobTraceTaskNode,
    taskid: TaskId
  ): Vector[JobTraceTaskNode] =
    if (node.taskId == taskid) node.children
    else node.children.flatMap(_find_children(_, taskid))
}
