package org.goldenport.cncf.job

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

import scala.jdk.CollectionConverters.*
import com.typesafe.config.{ConfigException, ConfigFactory, ConfigIncludeContext, ConfigIncluder, ConfigIncluderClasspath, ConfigIncluderFile, ConfigIncluderURL, ConfigObject, ConfigParseOptions, ConfigValue}
import io.circe.Json
import io.circe.parser.parse as parseJson
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.RecordFormat
import org.goldenport.record.io.RecordSourceLoader
import org.xml.sax.{EntityResolver, InputSource, SAXException}
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor

/*
 * @since   Apr. 22, 2026
 *  version May.  7, 2026
 *  version Jul.  1, 2026
 * @version Sep. 28, 2026
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
  flow: Option[JobFlow] = None,
  events: Option[JobEvents] = None,
  onEvent: Option[JobOnEvent] = None,
  jobDefinitionRef: Option[String] = None
) {
  def semanticPlan: Consequence[JobSemanticPlan] =
    JobSemanticCompiler.compile(this)

  def compile: Consequence[JobSemanticPlan] =
    semanticPlan

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
      "flow" -> flow.map(_.toRecord).getOrElse(Record.empty),
      "events" -> events.map(_.toRecord).getOrElse(Record.empty),
      "onEvent" -> onEvent.map(_.toRecord).getOrElse(Record.empty),
      "jobDefinitionRef" -> jobDefinitionRef.getOrElse("")
    )
}

enum JobJclRootKind {
  case SingleJob
  case Jobs
}
