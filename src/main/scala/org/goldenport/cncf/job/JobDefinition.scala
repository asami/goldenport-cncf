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
 * @version Sep. 13, 2026
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

final case class JobBatchDefinition(
  jobs: Vector[JobDefinition],
  rootKind: JobJclRootKind = JobJclRootKind.Jobs
) {
  def semanticPlans: Consequence[Vector[JobSemanticPlan]] =
    jobs.foldLeft(Consequence.success(Vector.empty[JobSemanticPlan])) { (result, job) =>
      for {
        plans <- result
        plan <- job.semanticPlan
      } yield plans :+ plan
    }

  def compile: Consequence[Vector[JobSemanticPlan]] =
    semanticPlans

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
  val DefaultFormat: RecordFormat =
    RecordFormat.Yaml

  val DefaultFormatName: String =
    formatName(DefaultFormat)

  def parse(body: String, format: RecordFormat = DefaultFormat): Consequence[JobBatchDefinition] =
    _validate_source_boundary(body, format).flatMap { _ =>
      RecordSourceLoader.load(body, format) match {
        case Consequence.Success(record) =>
          _parse_root(record)
        case Consequence.Failure(conclusion) =>
          Consequence.argumentInvalid(s"invalid JCL ${formatName(format)}: ${conclusion.show}")
      }
    }

  def parseYaml(body: String): Consequence[JobBatchDefinition] =
    parse(body, RecordFormat.Yaml)

  def parseFormat(value: String): Consequence[RecordFormat] = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
    val format = normalized match {
      case "json" => Some(RecordFormat.Json)
      case "yaml" | "yml" => Some(RecordFormat.Yaml)
      case "xml" => Some(RecordFormat.Xml)
      case "hocon" | "conf" => Some(RecordFormat.Hocon)
      case other => RecordFormat.fromSuffix(s".$other")
    }
    format.filter(_supported_formats.contains) match {
      case Some(format) => Consequence.success(format)
      case None => Consequence.argumentInvalid(s"unsupported JCL format: $value")
    }
  }

  def parseFormat(value: Option[String]): Consequence[RecordFormat] =
    value.map(parseFormat).getOrElse(Consequence.success(DefaultFormat))

  def formatName(format: RecordFormat): String =
    format match {
      case RecordFormat.Json => "json"
      case RecordFormat.Yaml => "yaml"
      case RecordFormat.Xml => "xml"
      case RecordFormat.Hocon => "hocon"
      case other => other.toString.toLowerCase(java.util.Locale.ROOT)
    }

  private val _supported_formats: Set[RecordFormat] =
    Set(RecordFormat.Json, RecordFormat.Yaml, RecordFormat.Xml, RecordFormat.Hocon)

  private val _executable_section_names: Vector[String] =
    Vector("flow", "events", "onEvent")

  private val _yaml_source_loader_options: LoaderOptions = {
    val options = new LoaderOptions()
    options.setMaxAliasesForCollections(50)
    options.setAllowRecursiveKeys(false)
    options.setNestingDepthLimit(50)
    options.setCodePointLimit(3 * 1024 * 1024)
    options
  }

  private val _yaml_source_parser: Yaml =
    new Yaml(new SafeConstructor(_yaml_source_loader_options))

  private object HoconIncludeRejector extends ConfigIncluder
      with ConfigIncluderClasspath with ConfigIncluderFile with ConfigIncluderURL {
    override def withFallback(fallback: ConfigIncluder): ConfigIncluder = this

    override def include(context: ConfigIncludeContext, what: String): ConfigObject =
      _reject_hocon_include(what)

    override def includeResources(context: ConfigIncludeContext, what: String): ConfigObject =
      _reject_hocon_include(what)

    override def includeFile(context: ConfigIncludeContext, what: java.io.File): ConfigObject =
      _reject_hocon_include(what.getPath)

    override def includeURL(context: ConfigIncludeContext, what: java.net.URL): ConfigObject =
      _reject_hocon_include(what.toExternalForm)

    private def _reject_hocon_include(what: String): Nothing =
      throw new ConfigException.BadValue(
        "include",
        s"JCL HOCON includes are not allowed: $what"
      )
  }

  private val _safe_hocon_parse_options: ConfigParseOptions =
    ConfigParseOptions.defaults().setIncluder(HoconIncludeRejector)

  private def _secure_source_xml_factory: Consequence[DocumentBuilderFactory] =
    Consequence {
      val factory = DocumentBuilderFactory.newInstance()
      factory.setNamespaceAware(true)
      factory.setXIncludeAware(false)
      factory.setExpandEntityReferences(false)
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
      factory
    }

  private val _rejecting_xml_entity_resolver: EntityResolver = new EntityResolver {
    override def resolveEntity(publicId: String, systemId: String): InputSource =
      throw new SAXException("JCL XML external entities are not allowed")
  }

  private def _validate_source_boundary(
    body: String,
    format: RecordFormat
  ): Consequence[Unit] =
    format match {
      case RecordFormat.Xml | RecordFormat.Hocon =>
        _source_shape(body, format).flatMap(root => _reject_empty_executable_mappings(root))
      case _ =>
        _reject_empty_executable_mappings(body, format)
    }

  private def _reject_empty_executable_mappings(
    body: String,
    format: RecordFormat
  ): Consequence[Unit] =
    _source_shape(body, format).flatMap(_reject_empty_executable_mappings)

  private def _reject_empty_executable_mappings(root: Any): Consequence[Unit] =
    _source_object(root) match {
      case None => Consequence.unit
      case Some(mapping) =>
        val singlejob = mapping.get("job").toVector.flatMap { value =>
          _source_object(value).map("job" -> _)
        }
        val batchjobs = mapping.get("jobs").toVector
          .flatMap(_source_values)
          .zipWithIndex
          .flatMap { case (value, index) =>
            _source_object(value).map(s"jobs[$index]" -> _)
          }
        val jobmaps = singlejob ++ batchjobs
        val violation = jobmaps.iterator.flatMap { case (path, job) =>
          _executable_section_names.iterator.collectFirst {
            case key if job.get(key).exists(_is_empty_source_mapping) => s"$path.$key"
          }
        }.toSeq.headOption
        violation match {
          case Some(path) => Consequence.argumentInvalid(s"$path must not be empty")
          case None => Consequence.unit
        }
    }

  private def _source_shape(
    body: String,
    format: RecordFormat
  ): Consequence[Any] =
    format match {
      case RecordFormat.Json =>
        Consequence {
          parseJson(body) match {
            case Right(value) => _json_source_value(value)
            case Left(error) => throw error
          }
        }
      case RecordFormat.Yaml =>
        Consequence {
          _yaml_source_parser.load[Any](body)
        }
      case RecordFormat.Xml =>
        _secure_source_xml_factory.flatMap { factory =>
          Consequence {
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver(_rejecting_xml_entity_resolver)
            val document = builder.parse(new InputSource(new StringReader(body)))
            _reject_xml_external_constructs(document.getDocumentElement)
            _xml_source_value(document.getDocumentElement)
          }
        }
      case RecordFormat.Hocon =>
        Consequence {
          _hocon_source_value(
            ConfigFactory.parseString(body, _safe_hocon_parse_options).root()
          )
        }
      case _ =>
        Consequence.success(())
    }

  private def _json_source_value(json: Json): Any =
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = number => number.toBigDecimal.getOrElse(BigDecimal(number.toDouble)),
      jsonString = identity,
      jsonArray = values => values.toVector.map(_json_source_value),
      jsonObject = objectvalue =>
        objectvalue.toIterable.iterator.map { case (key, value) =>
          key -> _json_source_value(value)
        }.toMap
    )

  private def _hocon_source_value(value: ConfigValue): Any =
    value match {
      case objectvalue: ConfigObject =>
        objectvalue.keySet.asScala.iterator.map { key =>
          key -> _hocon_source_value(objectvalue.get(key))
        }.toMap
      case values: java.util.Collection[?] =>
        values.asScala.iterator.map {
          case configvalue: ConfigValue => _hocon_source_value(configvalue)
          case other => other
        }.toVector
      case other =>
        other.unwrapped()
    }

  private def _xml_source_value(element: org.w3c.dom.Element): Any = {
    val children = _xml_source_child_elements(element)
    val text = _xml_source_text(element)
    val attributes = _xml_source_attributes(element)
    if (children.isEmpty) {
      if (attributes.isEmpty && text.nonEmpty)
        text
      else if (text.nonEmpty)
        attributes.updated("#text", text)
      else
        attributes
    } else {
      children.map(_xml_source_local_name).distinct.iterator.map { name =>
        val elements = children.filter(_xml_source_local_name(_) == name)
        val value =
          if (elements.size == 1)
            _xml_source_value(elements.head)
          else
            elements.map(_xml_source_value)
        name -> value
      }.toMap
    }
  }

  private def _reject_xml_external_constructs(element: org.w3c.dom.Element): Unit = {
    val isxinclude =
      element.getNamespaceURI == "http://www.w3.org/2001/XInclude" &&
        element.getLocalName == "include"
    val hasexternalschema = {
      val attributes = element.getAttributes
      (0 until attributes.getLength).exists { index =>
        val attribute = attributes.item(index)
        attribute.getNamespaceURI == "http://www.w3.org/2001/XMLSchema-instance" &&
          Set("schemaLocation", "noNamespaceSchemaLocation").contains(attribute.getLocalName)
      }
    }
    if (isxinclude)
      throw new SAXException("JCL XML XInclude is not allowed")
    else if (hasexternalschema)
      throw new SAXException("JCL XML external schema is not allowed")
    else
      _xml_source_child_elements(element).foreach(_reject_xml_external_constructs)
  }

  private def _xml_source_child_elements(
    element: org.w3c.dom.Element
  ): Vector[org.w3c.dom.Element] = {
    val nodes = element.getChildNodes
    (0 until nodes.getLength).toVector.map(index => nodes.item(index)).collect {
      case child: org.w3c.dom.Element => child
    }
  }

  private def _xml_source_text(element: org.w3c.dom.Element): String = {
    val nodes = element.getChildNodes
    (0 until nodes.getLength).toVector.map(index => nodes.item(index)).collect {
      case child: org.w3c.dom.CDATASection => child.getData
      case child: org.w3c.dom.Text => child.getWholeText
    }.mkString.trim
  }

  private def _xml_source_attributes(
    element: org.w3c.dom.Element
  ): Map[String, Any] = {
    val attributes = element.getAttributes
    (0 until attributes.getLength).iterator.map { index =>
      val node = attributes.item(index)
      _xml_source_local_name(node) -> node.getNodeValue
    }.toMap
  }

  private def _xml_source_local_name(node: org.w3c.dom.Node): String =
    Option(node.getLocalName).getOrElse(node.getNodeName)

  private def _source_object(value: Any): Option[Map[String, Any]] =
    value match {
      case mapping: java.util.Map[?, ?] =>
        Some(mapping.asScala.iterator.map { case (key, entry) =>
          key.toString -> entry
        }.toMap)
      case mapping: Map[?, ?] =>
        Some(mapping.iterator.map { case (key, entry) =>
          key.toString -> entry
        }.toMap)
      case _ => None
    }

  private def _source_values(value: Any): Vector[Any] =
    value match {
      case values: java.util.Collection[?] =>
        values.asScala.iterator.map(entry => entry).toVector
      case values: Iterable[?] =>
        values.iterator.map(entry => entry).toVector
      case _ => Vector.empty
    }

  private def _is_empty_source_mapping(value: Any): Boolean =
    _source_object(value).exists(_.isEmpty)

  private def _parse_root(p: Any): Consequence[JobBatchDefinition] =
    _object_map(p, "JCL root").flatMap { m =>
      val keys = m.keySet
      if (keys == Set("job"))
        _job(m("job"), 0, "job", iscanonicalroot = true).map(job => JobBatchDefinition(Vector(job), JobJclRootKind.SingleJob))
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
        _sequence(xs.zipWithIndex.toVector.map { case (x, i) => _job(x, i, s"jobs[$i]", iscanonicalroot = false) })
    }

  private def _job(
    p: Any,
    index: Int,
    path: String,
    iscanonicalroot: Boolean
  ): Consequence[JobDefinition] =
    _object_map(p, path).flatMap { m =>
      val allowed = Set("name", "target", "parameters", "submit", "onFailure", "compensation", "profile", "flow", "events", "onEvent", "jobDefinitionRef")
      _reject_unknown_keys(m.keySet, allowed, path).flatMap { _ =>
        val executablepresent = Set("flow", "events", "onEvent").exists(m.contains)
        if (executablepresent && !iscanonicalroot)
          Consequence.argumentInvalid(
            s"$path executable sections require the canonical single-job root"
          )
        else for {
          name <- _required_string(m, "name", path)
          target <- _target(m.get("target"), s"$path.target")
          params <- _string_map(m.get("parameters"), s"$path.parameters")
          submit <- _submit(m.get("submit"), s"$path.submit")
          onFailure <- _failure_hook(m.get("onFailure"), s"$path.onFailure")
          compensation <- _failure_hook(m.get("compensation"), s"$path.compensation")
          profile <- _profile(m.get("profile"), s"$path.profile")
          flow <- _flow(m.get("flow"), s"$path.flow")
          events <- _event_definitions(m.get("events"), s"$path.events")
          onEvent <- _on_event(m.get("onEvent"), s"$path.onEvent")
          ref <- _optional_string(m.get("jobDefinitionRef"), s"$path.jobDefinitionRef")
          definition = JobDefinition(name, target, params, submit, onFailure, compensation, profile, flow, events, onEvent, ref)
          _ <- definition.semanticPlan
        } yield definition
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

  private def _flow(
    p: Option[Any],
    path: String
  ): Consequence[Option[JobFlow]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          _reject_unknown_keys(m.keySet, Set("steps"), path).flatMap { _ =>
            m.get("steps") match {
              case None => Consequence.argumentMissing(s"$path.steps")
              case Some(steps) =>
                _vector(steps, s"$path.steps").flatMap { xs =>
                  if (xs.isEmpty)
                    Consequence.argumentInvalid(s"$path.steps must not be empty")
                  else if (xs.size > JobSemanticCompiler.MAX_FLOW_STEPS)
                    Consequence.argumentInvalid(
                      s"$path.steps must contain at most ${JobSemanticCompiler.MAX_FLOW_STEPS} steps"
                    )
                  else
                    _sequence(xs.zipWithIndex.toVector.map { case (step, i) =>
                      _flow_step(step, s"$path.steps[$i]")
                    }).flatMap { parsed =>
                      if (parsed.exists(_.id == "root"))
                        Consequence.argumentInvalid(s"$path step id must not be root")
                      else if (_duplicate_ids(parsed.map(_.id)))
                        Consequence.argumentInvalid(s"$path step ids must be unique")
                      else
                        Consequence.success(Some(JobFlow(parsed)))
                    }
                }
            }
          }
        }
    }

  private def _flow_step(
    p: Any,
    path: String
  ): Consequence[JobFlowStep] =
    _object_map(p, path).flatMap { m =>
      _reject_unknown_keys(m.keySet, Set("id", "action", "parameters"), path).flatMap { _ =>
        for {
          id <- _required_string(m, "id", path)
          action <- _required_string(m, "action", path)
          parameters <- _string_map(m.get("parameters"), s"$path.parameters")
        } yield JobFlowStep(id, action, parameters)
      }
    }

  private def _event_definitions(
    p: Option[Any],
    path: String
  ): Consequence[Option[JobEvents]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          _reject_unknown_keys(m.keySet, Set("emit"), path).flatMap { _ =>
            m.get("emit") match {
              case None => Consequence.argumentMissing(s"$path.emit")
              case Some(emissions) =>
                _vector(emissions, s"$path.emit").flatMap { xs =>
                  if (xs.isEmpty)
                    Consequence.argumentInvalid(s"$path.emit must not be empty")
                  else if (xs.size > JobSemanticCompiler.MAX_EMITTED_EVENTS)
                    Consequence.argumentInvalid(
                      s"$path.emit must contain at most ${JobSemanticCompiler.MAX_EMITTED_EVENTS} emissions"
                    )
                  else
                    _sequence(xs.zipWithIndex.toVector.map { case (event, i) =>
                      _event_emission(event, s"$path.emit[$i]")
                    }).flatMap { parsed =>
                      if (_duplicate_ids(parsed.map(_.id)))
                        Consequence.argumentInvalid(s"$path.emit ids must be unique")
                      else
                        Consequence.success(Some(JobEvents(parsed)))
                    }
                }
            }
          }
        }
    }

  private def _event_emission(
    p: Any,
    path: String
  ): Consequence[JobEventEmission] =
    _object_map(p, path).flatMap { m =>
      _reject_unknown_keys(m.keySet, Set("id", "after", "name", "kind", "persistent"), path).flatMap { _ =>
        for {
          id <- _required_string(m, "id", path)
          after <- _required_string(m, "after", path)
          name <- _required_string(m, "name", path)
          kind <- _optional_string(m.get("kind"), s"$path.kind")
          persistent <- _optional_boolean(m.get("persistent"), s"$path.persistent")
        } yield JobEventEmission(id, after, name, kind, persistent)
      }
    }

  private def _on_event(
    p: Option[Any],
    path: String
  ): Consequence[Option[JobOnEvent]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) =>
        _object_map(value, path).flatMap { m =>
          _reject_unknown_keys(m.keySet, Set("handlers"), path).flatMap { _ =>
            m.get("handlers") match {
              case None => Consequence.argumentMissing(s"$path.handlers")
              case Some(handlers) =>
                _vector(handlers, s"$path.handlers").flatMap { xs =>
                  if (xs.isEmpty)
                    Consequence.argumentInvalid(s"$path.handlers must not be empty")
                  else if (xs.size > JobSemanticCompiler.MAX_EVENT_HANDLERS)
                    Consequence.argumentInvalid(
                      s"$path.handlers must contain at most ${JobSemanticCompiler.MAX_EVENT_HANDLERS} handlers"
                    )
                  else
                    _sequence(xs.zipWithIndex.toVector.map { case (handler, i) =>
                      _event_handler(handler, s"$path.handlers[$i]")
                    }).flatMap { parsed =>
                      if (_duplicate_ids(parsed.map(_.id)))
                        Consequence.argumentInvalid(s"$path.handlers ids must be unique")
                      else
                        Consequence.success(Some(JobOnEvent(parsed)))
                    }
                }
            }
          }
        }
    }

  private def _event_handler(
    p: Any,
    path: String
  ): Consequence[JobEventHandler] =
    _object_map(p, path).flatMap { m =>
      _reject_unknown_keys(m.keySet, Set("id", "event", "action", "parameters"), path).flatMap { _ =>
        for {
          id <- _required_string(m, "id", path)
          event <- _required_string(m, "event", path)
          action <- _required_string(m, "action", path)
          parameters <- _string_map(m.get("parameters"), s"$path.parameters")
        } yield JobEventHandler(id, event, action, parameters)
      }
    }

  private def _optional_boolean(
    p: Option[Any],
    path: String
  ): Consequence[Option[Boolean]] =
    p match {
      case None => Consequence.success(None)
      case Some(value) =>
        value match {
          case b: Boolean => Consequence.success(Some(b))
          case _ => Consequence.argumentInvalid(s"$path must be a Boolean")
        }
    }

  private def _duplicate_ids(values: Vector[String]): Boolean =
    values.distinct.size != values.size

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
      case r: Record =>
        Consequence.success(r.fields.map(field => field.key -> field.value.single).toMap)
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
      case xs: Iterable[?] => Consequence.success(xs.toVector.asInstanceOf[Vector[Any]])
      case _ => Consequence.argumentInvalid(s"$path must be a list")
    }

  private def _string(
    p: Any,
    path: String
  ): Consequence[String] =
    p match {
      case value: String =>
        value.trim match {
          case "" => Consequence.argumentInvalid(s"$path must be a non-empty string")
          case normalized => Consequence.success(normalized)
        }
      case _ => Consequence.argumentInvalid(s"$path must be a non-empty string")
    }

  private def _sequence[A](xs: Vector[Consequence[A]]): Consequence[Vector[A]] =
    xs.foldLeft(Consequence.success(Vector.empty[A])) { (z, x) =>
      for {
        a <- z
        b <- x
      } yield a :+ b
    }
}
