package org.goldenport.cncf.job

import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.yaml.snakeyaml.Yaml

/*
 * @since   Apr. 22, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobWorkflowTarget(
  definition: String,
  registration: String
) {
  def toRecord: Record =
    Record.data(
      "definition" -> definition,
      "registration" -> registration
    )
}

final case class JobTarget(
  action: Option[String] = None,
  workflow: Option[JobWorkflowTarget] = None
) {
  def toRecord: Record =
    Record.data(
      "action" -> action.getOrElse(""),
      "workflow" -> workflow.map(_.toRecord).getOrElse(Record.empty)
    )
}

final case class JobSubmitSpec(
  persistence: JobPersistencePolicy = JobPersistencePolicy.Persistent,
  requestSummary: Option[String] = None
) {
  def toRecord: Record =
    Record.data(
      "persistence" -> persistence.toString,
      "request-summary" -> requestSummary.getOrElse("")
    )
}

final case class JobFailureHook(
  action: String,
  parameters: Map[String, String] = Map.empty
) {
  def toRecord: Record =
    Record.data(
      "action" -> action,
      "parameters" -> parameters.toVector.sortBy(_._1).map { case (k, v) =>
        Record.data(k -> v)
      }
    )
}

final case class JobDefinition(
  name: String,
  target: JobTarget,
  parameters: Map[String, String] = Map.empty,
  submit: JobSubmitSpec = JobSubmitSpec(),
  onFailure: Option[JobFailureHook] = None,
  compensation: Option[JobFailureHook] = None,
  profile: Option[JobDeclaredProfile] = None,
  flow: Option[Record] = None,
  events: Option[Record] = None,
  onEvent: Option[Record] = None,
  jobDefinitionRef: Option[String] = None
) {
  def toRecord: Record =
    Record.data(
      "name" -> name,
      "target" -> target.toRecord,
      "parameters" -> parameters.toVector.sortBy(_._1).map { case (k, v) =>
        Record.data(k -> v)
      },
      "submit" -> submit.toRecord,
      "on-failure" -> onFailure.map(_.toRecord).getOrElse(Record.empty),
      "compensation" -> compensation.map(_.toRecord).getOrElse(Record.empty),
      "profile" -> profile.map(_.toRecord).getOrElse(Record.empty),
      "flow" -> flow.getOrElse(Record.empty),
      "events" -> events.getOrElse(Record.empty),
      "onEvent" -> onEvent.getOrElse(Record.empty),
      "jobDefinitionRef" -> jobDefinitionRef.getOrElse("")
    )
}

enum JobJclRootKind {
  case SingleJob
  case Jobs
}

final case class JobBatchDefinition(
  jobs: Vector[JobDefinition],
  rootKind: JobJclRootKind = JobJclRootKind.Jobs
) {
  def toRecord: Record =
    rootKind match {
      case JobJclRootKind.SingleJob =>
        Record.data("job" -> jobs.headOption.map(_.toRecord).getOrElse(Record.empty))
      case JobJclRootKind.Jobs =>
        Record.data("jobs" -> jobs.map(_.toRecord))
    }
}

final case class JobBatchSubmissionResult(
  submittedJobIds: Vector[JobId],
  success: Boolean,
  stoppedAtIndex: Option[Int] = None,
  stoppedAtName: Option[String] = None,
  failureMessage: Option[String] = None,
  failureHookJobId: Option[JobId] = None,
  failureHookMessage: Option[String] = None
) {
  def toRecord: Record =
    Record.data(
      "success" -> success,
      "submitted-job-ids" -> submittedJobIds.map(_.value),
      "stopped-at-index" -> stoppedAtIndex.getOrElse(""),
      "stopped-at-name" -> stoppedAtName.getOrElse(""),
      "failure-message" -> failureMessage.getOrElse(""),
      "failure-hook-job-id" -> failureHookJobId.map(_.value).getOrElse(""),
      "failure-hook-message" -> failureHookMessage.getOrElse("")
    )
}

object JobBatchDefinition {
  def parseYaml(body: String): Consequence[JobBatchDefinition] =
    try {
      val yaml = new Yaml()
      val loaded = yaml.load[Any](body)
      _parse_root(loaded)
    } catch {
      case e: Exception =>
        Consequence.argumentInvalid(s"invalid JCL YAML: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}")
    }

  private def _parse_root(p: Any): Consequence[JobBatchDefinition] =
    _object_map(p, "JCL root").flatMap { m =>
      val keys = m.keySet
      if (keys == Set("job"))
        _job(m("job"), 0, "job").map(job => JobBatchDefinition(Vector(job), JobJclRootKind.SingleJob))
      else if (keys == Set("jobs"))
        _jobs(m("jobs")).map(jobs => JobBatchDefinition(jobs, JobJclRootKind.Jobs))
      else if (keys.contains("job") && keys.contains("jobs"))
        Consequence.argumentInvalid("JCL root must not contain both job and jobs")
      else
        Consequence.argumentInvalid("JCL root must contain only job or jobs")
    }

  private def _jobs(p: Any): Consequence[Vector[JobDefinition]] =
    _vector(p, "jobs").flatMap { xs =>
      if (xs.isEmpty)
        Consequence.argumentInvalid("jobs must not be empty")
      else
        _sequence(xs.zipWithIndex.toVector.map { case (x, i) => _job(x, i) })
    }

  private def _job(p: Any, index: Int): Consequence[JobDefinition] =
    _job(p, index, s"jobs[$index]")

  private def _job(p: Any, index: Int, path: String): Consequence[JobDefinition] =
        _object_map(p, path).flatMap { m =>
      val allowed = Set("name", "target", "parameters", "submit", "onFailure", "compensation", "profile", "flow", "events", "onEvent", "jobDefinitionRef")
      _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
        for {
          name <- _required_string(m, "name", path)
          target <- _target(m.get("target"), s"$path.target")
          params <- _string_map(m.get("parameters"), s"$path.parameters")
          submit <- _submit(m.get("submit"), s"$path.submit")
          onFailure <- _failure_hook(m.get("onFailure"), s"$path.onFailure")
          compensation <- _failure_hook(m.get("compensation"), s"$path.compensation")
          profile <- _profile(m.get("profile"), s"$path.profile")
          flow <- _inert_record(m.get("flow"), s"$path.flow")
          events <- _inert_record(m.get("events"), s"$path.events")
          onEvent <- _inert_record(m.get("onEvent"), s"$path.onEvent")
          ref <- _optional_string(m.get("jobDefinitionRef"), s"$path.jobDefinitionRef")
        } yield JobDefinition(name, target, params, submit, onFailure, compensation, profile, flow, events, onEvent, ref)
      }
    }

  private def _target(p: Option[Any], path: String): Consequence[JobTarget] =
    p match {
      case None => Consequence.argumentMissing(path)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          if (Set("branch", "loop", "parallel", "wait", "steps", "event").exists(m.contains))
            Consequence.argumentInvalid(s"$path contains unsupported workflow semantics")
          else if (m.keySet == Set("action"))
            _required_string(m, "action", path).map(x => JobTarget(action = Some(x)))
          else if (m.keySet == Set("workflow"))
            _workflow_target(m("workflow"), s"$path.workflow").map(x => JobTarget(workflow = Some(x)))
          else if (m.keySet == Set("action", "workflow"))
            Consequence.argumentInvalid(s"$path.action and $path.workflow are mutually exclusive")
          else
            Consequence.argumentInvalid(s"$path must contain exactly one of action or workflow")
        }
    }

  private def _workflow_target(p: Any, path: String): Consequence[JobWorkflowTarget] =
    _object_map(p, path).flatMap { m =>
      val allowed = Set("definition", "registration")
      _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
        for {
          definition <- _required_string(m, "definition", path)
          registration <- _required_string(m, "registration", path)
        } yield JobWorkflowTarget(definition, registration)
      }
    }

  private def _submit(p: Option[Any], path: String): Consequence[JobSubmitSpec] =
    p match {
      case None => Consequence.success(JobSubmitSpec())
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          val allowed = Set("persistence", "requestSummary")
          _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
            val persistence = m.get("persistence") match {
              case None => Consequence.success(JobPersistencePolicy.Persistent)
              case Some(v) =>
                _string(v, s"$path.persistence").flatMap {
                  case "persistent" => Consequence.success(JobPersistencePolicy.Persistent)
                  case "ephemeral" => Consequence.success(JobPersistencePolicy.Ephemeral)
                  case other => Consequence.argumentInvalid(s"$path.persistence must be persistent or ephemeral: $other")
                }
            }
            val requestsummary = m.get("requestSummary") match {
              case None => Consequence.success(Option.empty[String])
              case Some(v) => _string(v, s"$path.requestSummary").map(Some(_))
            }
            for {
              p1 <- persistence
              s1 <- requestsummary
            } yield JobSubmitSpec(p1, s1)
          }
        }
    }

  private def _failure_hook(p: Option[Any], path: String): Consequence[Option[JobFailureHook]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          val allowed = Set("action", "parameters")
          _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
            for {
              action <- _required_string(m, "action", path)
              parameters <- _string_map(m.get("parameters"), s"$path.parameters")
            } yield Some(JobFailureHook(action, parameters))
          }
        }
    }

  private def _profile(
    p: Option[Any],
    path: String
  ): Consequence[Option[JobDeclaredProfile]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          val allowed = Set("expectedStatus", "eventChain")
          _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
            for {
              status <- _optional_status(m.get("expectedStatus"), s"$path.expectedStatus")
              chain <- _event_chain(m.get("eventChain"), s"$path.eventChain")
            } yield Some(JobDeclaredProfile(status, chain))
          }
        }
    }

  private def _optional_status(
    p: Option[Any],
    path: String
  ): Consequence[Option[JobStatus]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) =>
        _string(value, path).flatMap { s =>
          s.trim.toLowerCase(java.util.Locale.ROOT) match {
            case "submitted" => Consequence.success(Some(JobStatus.Submitted))
            case "running" => Consequence.success(Some(JobStatus.Running))
            case "suspended" => Consequence.success(Some(JobStatus.Suspended))
            case "cancelled" | "canceled" => Consequence.success(Some(JobStatus.Cancelled))
            case "succeeded" | "success" => Consequence.success(Some(JobStatus.Succeeded))
            case "failed" | "failure" => Consequence.success(Some(JobStatus.Failed))
            case other => Consequence.argumentInvalid(s"$path is not a supported Job status: $other")
          }
        }
    }

  private def _event_chain(
    p: Option[Any],
    path: String
  ): Consequence[Vector[JobProfileChainNode]] =
    p match {
      case None => Consequence.success(Vector.empty)
      case Some(value) =>
        _vector(value, path).flatMap { xs =>
          _sequence(xs.zipWithIndex.toVector.map { case (x, i) =>
            _chain_node(x, s"$path[$i]")
          })
        }
    }

  private def _chain_node(
    p: Any,
    path: String
  ): Consequence[JobProfileChainNode] =
    _object_map(p, path).flatMap { m =>
      val allowed = Set("action", "emits")
      _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
        for {
          action <- _required_string(m, "action", path)
          emits <- _events(m.get("emits"), s"$path.emits")
        } yield JobProfileChainNode(action, emits)
      }
    }

  private def _events(
    p: Option[Any],
    path: String
  ): Consequence[Vector[JobProfileEvent]] =
    p match {
      case None => Consequence.success(Vector.empty)
      case Some(value) =>
        _vector(value, path).flatMap { xs =>
          _sequence(xs.zipWithIndex.toVector.map { case (x, i) =>
            _event(x, s"$path[$i]")
          })
        }
    }

  private def _event(
    p: Any,
    path: String
  ): Consequence[JobProfileEvent] =
    _object_map(p, path).flatMap { m =>
      val allowed = Set("event", "occurrence", "receivers")
      _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
        for {
          event <- _required_string(m, "event", path)
          occurrence <- _occurrence(m.get("occurrence"), s"$path.occurrence", JobProfileOccurrence.Required)
          receivers <- _receivers(m.get("receivers"), s"$path.receivers")
        } yield JobProfileEvent(event, occurrence, receivers)
      }
    }

  private def _receivers(
    p: Option[Any],
    path: String
  ): Consequence[Vector[JobProfileReceiver]] =
    p match {
      case None => Consequence.success(Vector.empty)
      case Some(value) =>
        _vector(value, path).flatMap { xs =>
          _sequence(xs.zipWithIndex.toVector.map { case (x, i) =>
            _receiver(x, s"$path[$i]")
          })
        }
    }

  private def _receiver(
    p: Any,
    path: String
  ): Consequence[JobProfileReceiver] =
    _object_map(p, path).flatMap { m =>
      val allowed = Set("action", "guard", "occurrence")
      _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
        for {
          action <- _required_string(m, "action", path)
          guard <- _optional_string(m.get("guard"), s"$path.guard")
          occurrence <- _occurrence(m.get("occurrence"), s"$path.occurrence", JobProfileOccurrence.Required)
        } yield JobProfileReceiver(action, guard, occurrence)
      }
    }

  private def _occurrence(
    p: Option[Any],
    path: String,
    default: JobProfileOccurrence
  ): Consequence[JobProfileOccurrence] =
    p match {
      case None => Consequence.success(default)
      case Some(value) => _string(value, path).flatMap(JobProfileOccurrence.parse(_, path))
    }

  private def _optional_string(
    p: Option[Any],
    path: String
  ): Consequence[Option[String]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) => _string(value, path).map(Some(_))
    }

  private def _inert_record(
    p: Option[Any],
    path: String
  ): Consequence[Option[Record]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) => _any_to_record(value, path).map(Some(_))
    }

  private def _any_to_record(
    p: Any,
    path: String
  ): Consequence[Record] =
    p match {
      case m: java.util.Map[?, ?] =>
        _object_map(m, path).map(m => Record.data(m.toVector.sortBy(_._1)*))
      case m: Map[?, ?] =>
        _object_map(m, path).map(m => Record.data(m.toVector.sortBy(_._1)*))
      case xs: java.util.List[?] =>
        Consequence.success(Record.data("items" -> xs.asScala.toVector.map(_.toString)))
      case xs: Seq[?] =>
        Consequence.success(Record.data("items" -> xs.toVector.map(_.toString)))
      case other =>
        Consequence.success(Record.data("value" -> other.toString))
    }

  private def _reject_unknown_keys(
    actual: Set[String],
    allowed: Set[String],
    path: String
  ): Consequence[Unit] = {
    val unknown = actual.diff(allowed)
    if (unknown.isEmpty)
      Consequence.unit
    else
      Consequence.argumentInvalid(s"$path contains unsupported keys: ${unknown.toVector.sorted.mkString(",")}")
  }

  private def _required_string(
    m: Map[String, Any],
    key: String,
    path: String
  ): Consequence[String] =
    m.get(key) match {
      case Some(v) => _string(v, s"$path.$key")
      case None => Consequence.argumentMissing(s"$path.$key")
    }

  private def _string_map(
    p: Option[Any],
    path: String
  ): Consequence[Map[String, String]] =
    p match {
      case None => Consequence.success(Map.empty)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          _sequence(
            m.toVector.sortBy(_._1).map { case (k, v) =>
              _string(v, s"$path.$k").map(k -> _)
            }
          ).map(_.toMap)
        }
    }

  private def _object_map(
    p: Any,
    path: String
  ): Consequence[Map[String, Any]] =
    p match {
      case m: java.util.Map[?, ?] =>
        Consequence.success(m.asScala.toMap.map { case (k, v) => k.toString -> v })
      case m: Map[?, ?] =>
        Consequence.success(m.map { case (k, v) => k.toString -> v }.asInstanceOf[Map[String, Any]])
      case _ =>
        Consequence.argumentInvalid(s"$path must be a mapping")
    }

  private def _vector(
    p: Any,
    path: String
  ): Consequence[Vector[Any]] =
    p match {
      case xs: java.util.List[?] => Consequence.success(xs.asScala.toVector)
      case xs: Seq[?] => Consequence.success(xs.toVector.asInstanceOf[Vector[Any]])
      case _ => Consequence.argumentInvalid(s"$path must be a list")
    }

  private def _string(
    p: Any,
    path: String
  ): Consequence[String] =
    Option(p).map(_.toString.trim).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentInvalid(s"$path must be a non-empty string")
    }

  private def _sequence[A](xs: Vector[Consequence[A]]): Consequence[Vector[A]] =
    xs.foldLeft(Consequence.success(Vector.empty[A])) { (z, x) =>
      for {
        a <- z
        b <- x
      } yield a :+ b
    }
}
