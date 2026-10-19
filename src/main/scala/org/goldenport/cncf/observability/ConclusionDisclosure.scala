package org.goldenport.cncf.observability

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.util.Try
import io.circe.Json
import org.goldenport.Conclusion
import org.goldenport.cncf.display.*
import org.goldenport.http.HttpRequest
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.protocol.Request
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.DataConfidentiality

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
/** Pure internal disclosure projection; neither amount nor transport metadata grants authority. */
private[cncf] object ConclusionDisclosure {
  enum Detail(val name: String) {
    case Standard extends Detail("standard")
    case Full extends Detail("full")
  }

  def detail(request: HttpRequest): Detail = {
    val query = _query(request)
    val values = query.toVector.flatMap(_selection_values) ++ _selection_values(request.form)
    if (query.isEmpty) Detail.Standard
    else values match {
      case Vector("full") => Detail.Full
      case _ => Detail.Standard
    }
  }

  def http(
    conclusion: Conclusion,
    detail: Detail = Detail.Standard,
    confidentiality: Map[String, DataConfidentiality] = Map.empty,
    credentials: Vector[String] = Vector.empty
  ): Record =
    _conclusion(conclusion, detail == Detail.Full, false, confidentiality, credentials, 0)

  def log(
    conclusion: Conclusion,
    confidentiality: Map[String, DataConfidentiality] = Map.empty,
    credentials: Vector[String] = Vector.empty
  ): Record =
    _conclusion(conclusion, true, true, confidentiality, credentials, 0)

  def message(conclusion: Conclusion, credentials: Vector[String] = Vector.empty,
    confidentiality: Map[String, DataConfidentiality] = Map.empty): String = {
    val secrets = credentials ++ _facet_credentials(conclusion.cause, confidentiality)
    sanitizeText(conclusion.cause.getMessage.orElse(conclusion.getException.flatMap(_typed_diagnostic).map { diagnostic =>
      Vector("code", "path", "reason").flatMap(diagnostic.getString).mkString(": ")
    }).getOrElse {
      if (conclusion.getException.nonEmpty) _redacted
      else {
        var sensitive = false
        val facets = conclusion.cause.descriptor.facets.map { facet =>
          facet match {
            case Descriptor.Facet.Parameter(_, name) => sensitive = DiagnosticPayloadSummary.isSensitiveKey(name, confidentiality)
            case _ => ()
          }
          val projected = _record(facet.toRecord, false, confidentiality, secrets, 0, sensitive)
          facet match {
            case Descriptor.Facet.Value(_) => Descriptor.Facet.Value(projected.getAny("value").getOrElse(_redacted))
            case Descriptor.Facet.Expected(_) => Descriptor.Facet.Expected(projected.getAny("expected").getOrElse(_redacted))
            case Descriptor.Facet.Actual(_) => Descriptor.Facet.Actual(projected.getAny("actual").getOrElse(_redacted))
            case Descriptor.Facet.Limit(_) => Descriptor.Facet.Limit(projected.getAny("limit").getOrElse(_redacted))
            case Descriptor.Facet.RecordData(record) => Descriptor.Facet.RecordData(_record(record, false, confidentiality, secrets, 0, false))
            case Descriptor.Facet.Properties(properties) => Descriptor.Facet.Properties(properties.map { case (key, value) =>
              key -> (if (sensitive || DiagnosticPayloadSummary.isSensitiveKey(key, confidentiality)) _redacted else sanitizeText(value, secrets))
            })
            case Descriptor.Facet.Input(name, value) => Descriptor.Facet.Input(name,
              value.map(text => if (sensitive) _redacted else sanitizeText(text, secrets)))
            case _: Descriptor.Facet.ExceptionLegacy | _: Descriptor.Facet.SrcPos | _: Descriptor.Facet.Endpoint |
                _: Descriptor.Facet.File | _: Descriptor.Facet.Database | _: Descriptor.Facet.Resource |
                _: Descriptor.Facet.ClassName | _: Descriptor.Facet.HttpUrl => Descriptor.Facet.Message(_redacted)
            case other => other
          }
        }
        conclusion.observation.copy(cause = conclusion.cause.copy(descriptor = Descriptor(facets))).show
      }
    }, secrets)
  }

  def sanitizeText(text: String, credentials: Vector[String] = Vector.empty): String = {
    val literal = credentials.filter(_.nonEmpty).distinct.sortBy(value => -value.length)
      .foldLeft(Option(text).getOrElse(""))((value, secret) => value.replace(secret, _redacted))
    val authorization = _authorization.replaceAllIn(literal, matched =>
      java.util.regex.Matcher.quoteReplacement(matched.group(1) + " " + _redacted))
    _labeled.replaceAllIn(authorization, matched =>
      java.util.regex.Matcher.quoteReplacement(matched.group(1) + _redacted))
  }

  def credentials(
    httpRequest: Option[HttpRequest],
    request: Option[Request] = None,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Vector[String] = {
    val physical = httpRequest.orElse(request.flatMap(_.source.collect { case Request.Source.Http(http) => http }))
    val headers = physical.toVector.flatMap(_.header.fields).flatMap { field =>
      field.value.single match {
        case value: String if field.key.equalsIgnoreCase("Authorization") =>
          Vector(value) ++ value.split("\\s+", 2).drop(1).toVector
        case value: String if field.key.equalsIgnoreCase("Cookie") =>
          Vector(value) ++ value.split(";").toVector.flatMap(part => part.split("=", 2).drop(1).map(_.trim))
        case value: String if DiagnosticPayloadSummary.isSensitiveKey(field.key, confidentiality) => Vector(value)
        case _ => Vector.empty
      }
    }
    val fields = physical.toVector.flatMap(http => _query(http).getOrElse(http.query).fields ++ http.form.fields)
      .filter(field => DiagnosticPayloadSummary.isSensitiveKey(field.key, confidentiality))
      .flatMap(field => _credential_strings(field.value.single, 0))
    val properties = request.toVector.flatMap(_.properties).filter(property =>
      DiagnosticPayloadSummary.isSensitiveKey(property.name, confidentiality))
      .flatMap(property => _credential_strings(property.value, 0))
    (headers ++ fields ++ properties).filter(_.nonEmpty).distinct
  }

  private val _redacted = "[redacted]"
  private val _depth_marker = "[redacted:depth-limit]"
  private val _selection_names = Set("textus.output.conclusion", "cncf.output.conclusion")
  private val _associated = Set("value", "expected", "actual", "input", "properties")
  private val _http_hidden = Set("exception", "exceptionlegacy", "classname", "stack", "stacktrace", "source",
    "origin", "environment", "endpoint", "database", "file", "calltree", "httpurl", "resource")
  private val _authorization = "(?i)\\b(Bearer|Basic)\\s+[^\\s\\\"',;<>]+".r
  private val _labeled = ("(?i)((?:[\\\"']?)(?:[a-z0-9_.-]*(?:password|passwd|secret|token|session|authorization|cookie|credential|api[_-]?key|private[_-]?key)[a-z0-9_.-]*)" +
    "(?:[\\\"']?)\\s*[:=]\\s*)(?:\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'[^']*'|[^\\r\\n&,;{}\\[\\]]+)").r

  private def _selection_values(record: Record): Vector[Any] =
    record.fields.filter(field => _selection_names(field.key)).map(_.value.single)

  // A raw query and its structured mirror are one physical source. Retain raw
  // duplicates, and reject contradictory mirrors without failing execution.
  private def _query(request: HttpRequest): Option[Record] = {
    val raw = request.context.originalUri.flatMap { uri =>
      val index = uri.indexOf('?')
      Option.when(index >= 0)(uri.substring(index + 1).takeWhile(_ != '#'))
    }
    raw match {
      case None => Some(request.query)
      case Some(value) =>
        Try(value.split("&", -1).toVector.filter(_.nonEmpty).map { part =>
          val pair = part.split("=", 2)
          URLDecoder.decode(pair(0), StandardCharsets.UTF_8) ->
            URLDecoder.decode(pair.lift(1).getOrElse(""), StandardCharsets.UTF_8)
        }).toOption.flatMap { pairs =>
          val selected = pairs.filter(pair => _selection_names(pair._1))
          val mirror = _selection_values(request.query)
          val values = selected.map(_._2)
          val compatible = mirror.isEmpty || mirror == values ||
            (mirror.size == 1 && mirror.head == values && values.size > 1)
          if (selected.isEmpty) Some(request.query)
          else if (compatible) Some(Record(pairs.map { case (key, item) => Field(key, Field.Value.Single(item)) }))
          else None
        }
    }
  }

  private def _conclusion(conclusion: Conclusion, history: Boolean, logging: Boolean,
    confidentiality: Map[String, DataConfidentiality], credentials: Vector[String], depth: Int): Record = {
    if (depth >= 64) Record.data("redaction" -> _depth_marker)
    else {
      val cause = conclusion.cause
      val secrets = credentials ++ _facet_credentials(cause, confidentiality)
      var sensitive = false
      val facets = cause.descriptor.facets.map { facet =>
        facet match {
          case Descriptor.Facet.Parameter(_, name) =>
            sensitive = DiagnosticPayloadSummary.isSensitiveKey(name, confidentiality)
          case _ => ()
        }
        val record = facet match {
          case Descriptor.Facet.Exception(exception) =>
            Record.data("exception" -> _exception(exception, logging, secrets)) ++
              _typed_diagnostic(exception).map(diagnostic => Record.data("diagnostic" -> diagnostic)).getOrElse(Record.empty)
          case Descriptor.Facet.ExceptionLegacy(classname, _, _) =>
            Record.data("exception-legacy" -> (if (logging) Record.data("class-name" -> classname,
              "message" -> _redacted, "stack-trace" -> _redacted) else _redacted))
          case other => other.toRecord
        }
        _record(record, logging, confidentiality, secrets, 0, sensitive)
      }
      val canonicalfields = (Record.dataOption("kind" -> cause.kind.map(_.name)).fields ++
        facets.flatMap(_.fields)).distinctBy(_.key)
      val canonicalcause = Record(canonicalfields) ++ Record.data("facets" -> facets)
      // Use the existing record vocabulary without asking a Throwable for its
      // message or recursively materializing previous before the safety bound.
      val observation = _record(conclusion.observation.copy(cause = Cause.empty).toRecord,
        logging, confidentiality, secrets, 0, false).upsertSingle("cause", canonicalcause)
      val current = Record.data("status" -> _record(conclusion.status.toRecord, logging, confidentiality, secrets, 0, false),
        "observation" -> observation,
        "interpretation" -> _record(conclusion.interpretation.toRecord, logging, confidentiality, secrets, 0, false),
        "disposition" -> _record(conclusion.disposition.toRecord, logging, confidentiality, secrets, 0, false))
      current ++ Record.dataOption("previous" -> Option.when(history)(conclusion.previous).flatten.map(previous =>
        _conclusion(previous, true, logging, confidentiality, secrets, depth + 1)))
    }
  }

  private def _record(record: Record, logging: Boolean, confidentiality: Map[String, DataConfidentiality],
    credentials: Vector[String], depth: Int, associated: Boolean): Record = {
    val sensitive = associated || record.fields.exists { field =>
      field.key == "parameter" && (field.value.single match {
        case parameter: Record => parameter.fields.exists { member =>
          member.key == "name" && (member.value.single match {
            case name: String => DiagnosticPayloadSummary.isSensitiveKey(name, confidentiality)
            case _ => false
          })
        }
        case _ => false
      })
    }
    Record(record.fields.map { field =>
      val key = DiagnosticPayloadSummary.normalizeKey(field.key)
      val value = if (DiagnosticPayloadSummary.isSensitiveKey(field.key, confidentiality) ||
        (!logging && _http_hidden(key)) || (sensitive && _associated(key))) _redacted
      else _value(field.value.single, logging, confidentiality, credentials, depth + 1)
      Field(sanitizeText(field.key, credentials), Field.Value.Single(value))
    })
  }

  private def _value(value: Any, logging: Boolean, confidentiality: Map[String, DataConfidentiality],
    credentials: Vector[String], depth: Int): Any = {
    if (depth >= 64) _depth_marker
    else value match {
      case null => null
      case text: String => sanitizeText(text, credentials)
      case value: Boolean => value
      case value: Byte => value
      case value: Short => value
      case value: Int => value
      case value: Long => value
      case value: Float if value.isFinite => value
      case value: Double if value.isFinite => value
      case value: BigInt => value
      case value: BigDecimal => value
      case value: java.math.BigInteger => value
      case value: java.math.BigDecimal => value
      case value: Char => sanitizeText(value.toString, credentials)
      case value: java.time.Instant => value.toString
      case record: Record => _record(record, logging, confidentiality, credentials, depth, false)
      case values: scala.collection.Map[?, ?] =>
        val fields = values.toVector.map {
          case (key: String, item) => Field(key, Field.Value.Single(item))
          case _ => Field("redaction", Field.Value.Single(_redacted))
        }
        _record(Record(fields), logging, confidentiality, credentials, depth, false)
      case values: Seq[?] => values.map(item => _value(item, logging, confidentiality, credentials, depth + 1)).toVector
      case values: Array[?] => values.toVector.map(item => _value(item, logging, confidentiality, credentials, depth + 1))
      case Some(item) => _value(item, logging, confidentiality, credentials, depth + 1)
      case None => None
      case json: Json => json.fold[Any](null, identity, number => number.toBigDecimal.getOrElse(_redacted),
        text => sanitizeText(text, credentials),
        values => _value(values, logging, confidentiality, credentials, depth + 1),
        fields => _value(fields.toMap, logging, confidentiality, credentials, depth + 1))
      case _: Throwable => _redacted
      case _ => _redacted
    }
  }

  private def _exception(exception: Throwable, logging: Boolean, credentials: Vector[String]): Any =
    if (!logging || exception == null) _redacted
    else Record.data("class" -> sanitizeText(exception.getClass.getName, credentials), "message" -> _redacted,
      "stack" -> exception.getStackTrace.toVector.map(location => Record.data(
        "class" -> sanitizeText(location.getClassName, credentials),
        "method" -> sanitizeText(location.getMethodName, credentials),
        "line" -> location.getLineNumber)))

  private def _typed_diagnostic(exception: Throwable): Option[Record] = {
    def _record_(code: String, path: String, reason: String): Option[Record] =
      Some(Record.data("code" -> code, "path" -> path, "reason" -> reason))
    exception match {
      case value: DisplayMutation.MutationException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: DisplayService.ServiceException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: Display.ValidationException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: DisplayProjection.ProjectionException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: DisplayProtocol.CodecException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: DisplayRuntime.BindingException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: DisplayBusinessOperation.DescriptorException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: GeneratedLogicalUiAbi.AdmissionException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: PresentationContext.ValidationException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case value: PresentationContextCodec.CodecException => _record_(value.diagnostic.code, value.diagnostic.path, value.diagnostic.reason)
      case _ => None
    }
  }

  private def _credential_strings(value: Any, depth: Int): Vector[String] =
    if (depth >= 64) Vector.empty
    else value match {
      case value: String => Vector(value)
      case values: Seq[?] => values.toVector.flatMap(value => _credential_strings(value, depth + 1))
      case values: Array[?] => values.toVector.flatMap(value => _credential_strings(value, depth + 1))
      case value: Record => value.fields.flatMap(field => _credential_strings(field.value.single, depth + 1))
      case values: scala.collection.Map[?, ?] => values.values.toVector.flatMap(value => _credential_strings(value, depth + 1))
      case Some(value) => _credential_strings(value, depth + 1)
      case _ => Vector.empty
    }

  private def _facet_credentials(cause: Cause, confidentiality: Map[String, DataConfidentiality]): Vector[String] = {
    var sensitive = false
    cause.descriptor.facets.flatMap { facet =>
      facet match {
        case Descriptor.Facet.Parameter(_, name) =>
          sensitive = DiagnosticPayloadSummary.isSensitiveKey(name, confidentiality)
          Vector.empty
        case Descriptor.Facet.Value(value) if sensitive => _credential_strings(value, 0)
        case Descriptor.Facet.Expected(value) if sensitive => _credential_strings(value, 0)
        case Descriptor.Facet.Actual(value) if sensitive => _credential_strings(value, 0)
        case Descriptor.Facet.Input(_, value) if sensitive => value.toVector
        case Descriptor.Facet.Properties(value) if sensitive => value.values.toVector
        case _ => Vector.empty
      }
    }
  }
}
