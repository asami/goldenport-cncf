package org.goldenport.cncf.job

import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.yaml.snakeyaml.Yaml

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobTarget(
  action: String
) {
  def toRecord: Record =
    Record.data(
      "action" -> action
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
  onFailure: Option[JobFailureHook] = None
) {
  def toRecord: Record =
    Record.data(
      "name" -> name,
      "target" -> target.toRecord,
      "parameters" -> parameters.toVector.sortBy(_._1).map { case (k, v) =>
        Record.data(k -> v)
      },
      "submit" -> submit.toRecord,
      "on-failure" -> onFailure.map(_.toRecord).getOrElse(Record.empty)
    )
}

final case class JobBatchDefinition(
  jobs: Vector[JobDefinition]
) {
  def toRecord: Record =
    Record.data(
      "jobs" -> jobs.map(_.toRecord)
    )
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
      if (keys != Set("jobs"))
        Consequence.argumentInvalid("JCL root must contain only jobs")
      else
        _jobs(m("jobs")).map(JobBatchDefinition.apply)
    }

  private def _jobs(p: Any): Consequence[Vector[JobDefinition]] =
    _vector(p, "jobs").flatMap { xs =>
      if (xs.isEmpty)
        Consequence.argumentInvalid("jobs must not be empty")
      else
        _sequence(xs.zipWithIndex.toVector.map { case (x, i) => _job(x, i) })
    }

  private def _job(p: Any, index: Int): Consequence[JobDefinition] =
    _object_map(p, s"jobs[$index]").flatMap { m =>
      val allowed = Set("name", "target", "parameters", "submit", "onFailure")
      _reject_unknown_keys(m.keySet, allowed, s"jobs[$index]").flatMap { _ =>
        for {
          name <- _required_string(m, "name", s"jobs[$index]")
          target <- _target(m.get("target"), s"jobs[$index].target")
          params <- _string_map(m.get("parameters"), s"jobs[$index].parameters")
          submit <- _submit(m.get("submit"), s"jobs[$index].submit")
          onFailure <- _failure_hook(m.get("onFailure"), s"jobs[$index].onFailure")
        } yield JobDefinition(name, target, params, submit, onFailure)
      }
    }

  private def _target(p: Option[Any], path: String): Consequence[JobTarget] =
    p match {
      case None => Consequence.argumentMissing(path)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          if (m.contains("workflow"))
            Consequence.argumentInvalid(s"$path.workflow is not supported in JCL-01")
          else if (Set("branch", "loop", "parallel", "wait", "steps", "event").exists(m.contains))
            Consequence.argumentInvalid(s"$path contains unsupported workflow semantics")
          else if (m.keySet != Set("action"))
            Consequence.argumentInvalid(s"$path must contain only action")
          else
            _required_string(m, "action", path).map(JobTarget.apply)
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
