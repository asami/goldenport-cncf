package org.goldenport.cncf.job

/*
 * Closed task projection. V1 retains exact descriptor correspondence; V2
 * distinguishes a normal original's recovery aggregate from its actual companion.
 *
 * @since   Oct. 2, 2026
 * @version Oct. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobTaskProjection {
  def project(
    live: Vector[JobTaskReadModel],
    supplied: Vector[DurableTaskDescriptor],
    status: JobStatus
  ): Either[String, Vector[DurableTaskDescriptor]] = {
    val liveids = live.map(_.taskId.value)
    val suppliedids = supplied.map(_.taskId)
    for {
      _ <- Either.cond(live.nonEmpty, (), "durable projection requires task read-model evidence")
      _ <- _unique(liveids, "live task")
      _ <- _unique(suppliedids, "durable task")
      _ <- Either.cond(liveids.toSet == suppliedids.toSet, (), "task descriptor ids do not match the live task read-model")
      _ <- _sequence(live.map { model =>
        supplied.find(_.taskId == model.taskId.value) match {
          case Some(task) => _task(model, task, _compensation(model))
          case None => Left(s"missing durable descriptor for task ${model.taskId.value}")
        }
      })
      _ <- _task_statuses(live, status)
    } yield supplied
  }

  def projectV2(
    live: Vector[JobTaskReadModel],
    supplied: Vector[DurableTaskDescriptor],
    status: JobStatus,
    retry: JobRetryState,
    timeline: Vector[JobTimelineEvent]
  ): Either[String, Vector[DurableTaskDescriptor]] = {
    val liveids = live.map(_.taskId.value)
    val suppliedids = supplied.map(_.taskId)
    if (live.isEmpty) {
      Either.cond(
        (status == JobStatus.Submitted || status == JobStatus.Running) && supplied.nonEmpty,
        supplied,
        "non-terminal durable projection requires closed task descriptor evidence"
      )
    } else {
      for {
        _ <- _unique(liveids, "live task")
        _ <- _unique(suppliedids, "durable task")
        _ <- Either.cond(liveids.toSet == suppliedids.toSet, (), "task descriptor ids do not match the live task read-model")
        _ <- _sequence(live.map { model =>
          supplied.find(_.taskId == model.taskId.value) match {
            case Some(task) => _task(model, task, _compensation_v2(model, live, retry, status))
            case None => Left(s"missing durable descriptor for task ${model.taskId.value}")
          }
        })
        _ <- if (JobStatus.isTerminal(status))
          _terminal_task_statuses_v2(live, status, retry, timeline)
        else
          _task_statuses_v2(live)
      } yield supplied
    }
  }

  private def _task(
    live: JobTaskReadModel,
    supplied: DurableTaskDescriptor,
    compensation: Either[String, Option[DurableCompensationDescriptor]]
  ): Either[String, Unit] = {
    val liveparent = live.parentTaskId.map(_.value)
    val taskkind = DurableTaskKind.parse(live.taskKind)
    val targetkind = live.targetKind.filter(_.trim.nonEmpty).toRight(
      s"task ${live.taskId.value} has no target kind"
    )
    val component = live.component.filter(_.trim.nonEmpty).toRight(
      s"task ${live.taskId.value} has no component target"
    )
    val operation = live.operation.filter(_.trim.nonEmpty).toRight(
      s"task ${live.taskId.value} has no operation target"
    )
    val relation = _relation(live)
    val transaction = _transaction(live)
    for {
      _ <- Either.cond(supplied.taskId == live.taskId.value, (), s"task id mismatch for ${live.taskId.value}")
      _ <- Either.cond(supplied.parentTaskId == liveparent, (), s"parent task mismatch for ${live.taskId.value}")
      kind <- taskkind
      _ <- Either.cond(supplied.kind == kind, (), s"task kind mismatch for ${live.taskId.value}")
      target <- targetkind
      _ <- Either.cond(supplied.target.targetKind == target, (), s"target kind mismatch for ${live.taskId.value}")
      _ <- _recognized_target(target, live.taskId.value)
      _ <- _task_kind_target(kind, target, live.taskId.value)
      componentid <- component
      operationid <- operation
      _ <- Either.cond(supplied.target.operation.componentId == componentid, (), s"component target mismatch for ${live.taskId.value}")
      _ <- Either.cond(supplied.target.operation.operationId == operationid, (), s"operation target mismatch for ${live.taskId.value}")
      _ <- Either.cond(supplied.target.operation.serviceId == live.service.filter(_.trim.nonEmpty), (), s"service target mismatch for ${live.taskId.value}")
      expectedrelation <- relation
      _ <- Either.cond(supplied.relation == expectedrelation, (), s"task relation mismatch for ${live.taskId.value}")
      expectedtransaction <- transaction
      _ <- Either.cond(supplied.transaction == expectedtransaction, (), s"transaction mismatch for ${live.taskId.value}")
      expectedcompensation <- compensation
      _ <- Either.cond(supplied.compensation == expectedcompensation, (), s"compensation mismatch for ${live.taskId.value}")
    } yield ()
  }

  private def _recognized_target(target: String, taskid: String): Either[String, Unit] =
    Either.cond(
      Set("action", "operation", "aggregate", "event", "continuation").contains(target),
      (),
      s"unknown target kind for task $taskid: $target"
    )

  private def _task_kind_target(
    kind: DurableTaskKind,
    target: String,
    taskid: String
  ): Either[String, Unit] = {
    val valid = kind match {
      case DurableTaskKind.Action => target == "action" || target == "aggregate"
      case DurableTaskKind.Operation => target == "operation"
      case DurableTaskKind.Aggregate => target == "aggregate"
      case DurableTaskKind.Event => target == "event"
      case DurableTaskKind.Continuation => target == "continuation"
    }
    Either.cond(valid, (), s"task kind and target kind diverge for task $taskid")
  }

  private def _relation(model: JobTaskReadModel): Either[String, DurableTaskRelation] =
    for {
      text <- model.relation.filter(_.trim.nonEmpty).toRight(s"task ${model.taskId.value} has no relation")
      kind <- DurableTaskRelationKind.parse(text)
      reference <- kind match {
        case DurableTaskRelationKind.Root =>
          Either.cond(
            model.parentTaskId.isEmpty,
            Option.empty[String],
            s"root task ${model.taskId.value} must not have a parent"
          )
        case _ => Right(model.parentTaskId.map(_.value))
      }
    } yield DurableTaskRelation(kind, reference)

  private def _transaction(model: JobTaskReadModel): Either[String, DurableTransactionDescriptor] =
    for {
      roletext <- model.transactionRole.filter(_.trim.nonEmpty).toRight(s"task ${model.taskId.value} has no transaction role")
      role <- DurableTransactionRole.parse(roletext)
      scopetext <- model.transactionScope.filter(_.trim.nonEmpty).toRight(s"task ${model.taskId.value} has no transaction scope")
      scope <- DurableTransactionScope.parse(scopetext)
      outcometext <- model.transactionOutcome.filter(_.trim.nonEmpty).toRight(s"task ${model.taskId.value} has no transaction outcome")
      outcome <- _transaction_outcome(outcometext, model.taskId.value)
      descriptor = DurableTransactionDescriptor(role, scope, outcome)
      _ <- Either.cond(
        role != DurableTransactionRole.None ||
          (scope == DurableTransactionScope.None && outcome == DurableTransactionOutcome.Pending),
        (),
        s"non-transactional task ${model.taskId.value} must use none/none/running"
      )
    } yield descriptor

  private def _transaction_outcome(
    text: String,
    taskid: String
  ): Either[String, DurableTransactionOutcome] =
    text match {
      case "running" => Right(DurableTransactionOutcome.Pending)
      case "committed" => Right(DurableTransactionOutcome.Committed)
      case "failed" => Right(DurableTransactionOutcome.Failed)
      case "compensation-committed" | "compensation-failed" => Right(DurableTransactionOutcome.Compensated)
      case other => Left(s"unknown transaction outcome for task $taskid: $other")
    }

  private def _compensation(model: JobTaskReadModel): Either[String, Option[DurableCompensationDescriptor]] = {
    val fields = Vector(
      model.compensationActionRef,
      model.compensatesTaskId.map(_.value),
      model.compensationStatus,
      model.compensationFailureSummary
    ).flatten
    if (fields.isEmpty)
      Right(None)
    else
      for {
        action <- model.compensationActionRef.filter(_.trim.nonEmpty).toRight(s"task ${model.taskId.value} has incomplete compensation action")
        target <- model.compensatesTaskId.map(_.value).filter(_.trim.nonEmpty).toRight(s"task ${model.taskId.value} has incomplete compensation target")
        statustext <- model.compensationStatus.filter(_.trim.nonEmpty).toRight(s"task ${model.taskId.value} has incomplete compensation status")
        status <- DurableCompensationStatus.parse(statustext)
        failure <- model.compensationFailureSummary.map { summary =>
          if (summary.trim.nonEmpty)
            Right(Some(DurableFailureSummary("compensation", "failed", summary, retryable = false)))
          else
            Left(s"task ${model.taskId.value} has a blank compensation failure summary")
        }.getOrElse(Right(None))
      } yield Some(DurableCompensationDescriptor(
        DurableOperationReference(
          model.component.getOrElse(""),
          model.service,
          action,
          None
        ),
        target,
        status,
        failure
      ))
  }

  private def _compensation_v2(
    model: JobTaskReadModel,
    models: Vector[JobTaskReadModel],
    retry: JobRetryState,
    status: JobStatus
  ): Either[String, Option[DurableCompensationDescriptor]] =
    if (model.relation.contains("compensation")) {
      _compensation(model).flatMap(value =>
        Either.cond(value.nonEmpty, value, "companion requires complete compensation evidence"))
    } else if (model.compensatesTaskId.nonEmpty) {
      _compensation(model)
    } else {
      val normal = model.relation.exists(value => value == "root" || value == "child")
      val nonblank = Vector(model.compensationActionRef, model.compensationFailureSummary)
        .flatten.forall(value => value != null && value.trim.nonEmpty)
      val companions = models.filter(value =>
        value.relation.contains("compensation") && value.compensatesTaskId.contains(model.taskId))
      val originalclosed = model.status == JobTaskStatus.Succeeded &&
        model.finishedAt.exists(value => !value.isBefore(model.startedAt)) &&
        model.result.success && model.transactionOutcome.contains("committed")
      val recovery = model.recoveryRequired && retry.recoveryRequired
      val aggregate = model.compensationStatus match {
        case None => model.compensationFailureSummary.isEmpty
        case Some("running") => originalclosed && !JobStatus.isTerminal(status)
        case Some("succeeded") =>
          originalclosed && companions.size == 1 && companions.forall(value =>
            value.status == JobTaskStatus.Succeeded && value.finishedAt.nonEmpty &&
              value.compensationStatus.contains("succeeded"))
        case Some("failed") =>
          originalclosed && recovery && companions.size == 1 && companions.forall(value =>
            value.status == JobTaskStatus.Failed && value.finishedAt.nonEmpty &&
              value.compensationStatus.contains("failed"))
        case Some("missing") =>
          originalclosed && recovery && companions.isEmpty &&
            model.compensationFailureSummary.exists(value => value != null && value.trim.nonEmpty)
        case _ => false
      }
      Either.cond(normal && nonblank && aggregate, None,
        "normal compensation aggregate is not a closed recovery form")
    }

  private def _task_statuses(
    models: Vector[JobTaskReadModel],
    status: JobStatus
  ): Either[String, Unit] = {
    val valid = models.forall { model =>
      model.status match {
        case JobTaskStatus.Running => model.finishedAt.isEmpty && model.result.success
        case JobTaskStatus.Succeeded =>
          model.finishedAt.nonEmpty &&
            model.finishedAt.forall(!_.isBefore(model.startedAt)) &&
            model.result.success
        case JobTaskStatus.Failed =>
          model.finishedAt.nonEmpty &&
            model.finishedAt.forall(!_.isBefore(model.startedAt)) &&
            !model.result.success
        case null => false
      }
    }
    val closed = models.forall(_.status != JobTaskStatus.Running)
    val statusok = status match {
      case JobStatus.Succeeded => models.forall(_.status == JobTaskStatus.Succeeded)
      case JobStatus.Failed => models.exists(_.status == JobTaskStatus.Failed)
      case JobStatus.Cancelled => true
      case _ => false
    }
    Either.cond(valid && closed && statusok, (), "task read-model status is not a closed terminal form")
  }

  private def _task_statuses_v2(
    models: Vector[JobTaskReadModel]
  ): Either[String, Unit] = {
    val valid = models.forall { model =>
      model.status match {
        case JobTaskStatus.Running => model.finishedAt.isEmpty && model.result.success
        case JobTaskStatus.Succeeded =>
          model.finishedAt.nonEmpty &&
            model.finishedAt.forall(!_.isBefore(model.startedAt)) &&
            model.result.success
        case JobTaskStatus.Failed =>
          model.finishedAt.nonEmpty &&
            model.finishedAt.forall(!_.isBefore(model.startedAt)) &&
            !model.result.success
        case null => false
      }
    }
    val transactionconsistent = models.forall { model =>
      model.status match {
        case JobTaskStatus.Running => model.transactionOutcome.contains("running")
        case JobTaskStatus.Succeeded =>
          model.transactionOutcome.exists(value =>
            value == "committed" || value == "compensation-committed"
          )
        case JobTaskStatus.Failed =>
          model.transactionOutcome.exists(value =>
            value == "failed" || value == "compensation-failed"
          )
        case null => false
      }
    }
    Either.cond(
      valid && transactionconsistent,
      (),
      "task read-model status or transaction outcome is not a structurally valid form"
    )
  }

  private def _terminal_task_statuses_v2(
    models: Vector[JobTaskReadModel],
    status: JobStatus,
    retry: JobRetryState,
    timeline: Vector[JobTimelineEvent]
  ): Either[String, Unit] =
    status match {
      case JobStatus.Succeeded =>
        _succeeded_terminal_task_statuses_v2(models, retry, timeline)
      case JobStatus.Failed | JobStatus.Cancelled =>
        _task_statuses(models, status)
      case _ =>
        Left("terminal task status validation requires a terminal JobStatus")
    }

  private def _succeeded_terminal_task_statuses_v2(
    models: Vector[JobTaskReadModel],
    retry: JobRetryState,
    timeline: Vector[JobTimelineEvent]
  ): Either[String, Unit] = {
    val valid = models.forall { model =>
      model.status match {
        case JobTaskStatus.Running => model.finishedAt.isEmpty && model.result.success
        case JobTaskStatus.Succeeded =>
          model.finishedAt.nonEmpty &&
            model.finishedAt.forall(!_.isBefore(model.startedAt)) &&
            model.result.success
        case JobTaskStatus.Failed =>
          model.finishedAt.nonEmpty &&
            model.finishedAt.forall(!_.isBefore(model.startedAt)) &&
            !model.result.success
        case null => false
      }
    }
    val closed = models.forall(_.status != JobTaskStatus.Running)
    val ordinarysuccess = models.forall(_.status == JobTaskStatus.Succeeded)
    val provenretryhistory =
      retry.attemptCount > 0 &&
        models.lastOption.exists(_.status == JobTaskStatus.Succeeded) &&
        models.exists(_.status == JobTaskStatus.Failed) &&
        _has_durable_task_pairs(models, timeline)
    Either.cond(
      valid && closed && (ordinarysuccess || provenretryhistory),
      (),
      "succeeded terminal task read-models require all success or proven durable retry history"
    )
  }

  private def _has_durable_task_pairs(
    models: Vector[JobTaskReadModel],
    timeline: Vector[JobTimelineEvent]
  ): Boolean =
    models.filterNot(_.relation.contains("compensation")).forall { model =>
      val events = timeline.filter(_.taskId.contains(model.taskId))
      val starts = events.count(_.kind == "task.durable-start-intent")
      val outcomes = events.count(_.kind == "task.durable-outcome-checkpoint")
      starts == 1 && outcomes == 1
    }

  private def _unique(values: Vector[String], name: String): Either[String, Unit] =
    Either.cond(values.distinct.size == values.size, (), s"duplicate $name identity")

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) {
      case (Right(acc), Right(value)) => Right(acc :+ value)
      case (Left(message), _) => Left(message)
      case (_, Left(message)) => Left(message)
    }
}
