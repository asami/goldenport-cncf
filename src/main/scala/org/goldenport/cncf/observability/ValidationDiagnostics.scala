package org.goldenport.cncf.observability

import org.goldenport.Conclusion
import org.goldenport.observation.Descriptor
import org.goldenport.provisional.observation.{Cause, Taxonomy}
import org.goldenport.record.Record

/*
 * Common diagnostics for structured validation failures.
 *
 * Components should use this to project ordinary Consequence/Conclusion
 * validation details into metrics and observability, instead of inspecting
 * human-readable messages or defining component-local error structures.
 *
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object ValidationDiagnostics {
  final case class Classification(
    failureKind: String,
    causeKind: Option[String],
    parameter: Option[String],
    fieldPath: Option[String],
    policy: Option[String]
  ) {
    def toRecord: Record = Record.dataAuto(
      "failureKind" -> failureKind,
      "causeKind" -> causeKind,
      "parameter" -> parameter,
      "fieldPath" -> fieldPath,
      "policy" -> policy
    )
  }

  def classify(conclusion: Conclusion): Classification = {
    val facets = conclusion.observation.cause.descriptor.facets
    val parameter = facets.collectFirst {
      case Descriptor.Facet.Parameter(_, name) => name
    }
    val fieldpath = facets.collectFirst {
      case Descriptor.Facet.FieldPath(path) => path
    }
    val policy = facets.collectFirst {
      case Descriptor.Facet.Policy(name) => name
    }
    val policies = facets.collect {
      case Descriptor.Facet.Policy(name) => name
    }.toSet
    val parameters = facets.collect {
      case Descriptor.Facet.Parameter(_, name) => name
    }.toSet
    val fieldpaths = facets.collect {
      case Descriptor.Facet.FieldPath(path) => path
    }.toSet
    val causekind = conclusion.observation.cause.kind
    Classification(
      failureKind = _failure_kind(conclusion, causekind, parameters, fieldpaths, policies),
      causeKind = causekind.map(_.name),
      parameter = parameter,
      fieldPath = fieldpath,
      policy = policy
    )
  }

  def isValidation(conclusion: Conclusion): Boolean =
    conclusion.observation.taxonomy.category == Taxonomy.Category.Argument ||
      conclusion.observation.cause.kind.exists {
        case Cause.Kind.Format | Cause.Kind.Policy | Cause.Kind.Limit | Cause.Kind.Inconsistency => true
        case _ => false
      }

  private def _failure_kind(
    conclusion: Conclusion,
    causekind: Option[Cause.Kind],
    parameters: Set[String],
    fieldpaths: Set[String],
    policies: Set[String]
  ): String =
    causekind match {
      case Some(Cause.Kind.Format) => "format"
      case Some(Cause.Kind.Policy) =>
        if (_is_mime_kind(parameters, policies))
          "mime_kind"
        else
          "policy"
      case Some(Cause.Kind.Limit) =>
        if (_is_payload_size(fieldpaths))
          "payload_size"
        else
          "limit"
      case Some(Cause.Kind.Inconsistency) => "inconsistency"
      case Some(Cause.Kind.Conflict) => "conflict"
      case Some(Cause.Kind.Exhaustion) => "exhaustion"
      case Some(Cause.Kind.Timeout) => "timeout"
      case Some(Cause.Kind.Corruption) => "corruption"
      case Some(Cause.Kind.Unknown) => "unknown"
      case None =>
        val taxonomy = conclusion.observation.taxonomy
        if (taxonomy.category == Taxonomy.Category.Argument)
          "argument"
        else if (conclusion.status.webCode.code == 400)
          "argument"
        else
          "unknown"
    }

  private def _is_payload_size(fieldpaths: Set[String]): Boolean =
    fieldpaths.exists {
      case "payload.byteSize" => true
      case "payload.size" => true
      case _ => false
    }

  private def _is_mime_kind(
    parameters: Set[String],
    policies: Set[String]
  ): Boolean =
    parameters.contains("contentType") &&
      policies.exists {
        case "mime-kind" => true
        case "content-type-kind" => true
        case _ => false
      }
}
