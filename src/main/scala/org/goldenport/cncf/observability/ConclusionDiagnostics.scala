package org.goldenport.cncf.observability

import org.goldenport.Conclusion
import org.goldenport.observation.Descriptor
import org.goldenport.provisional.observation.{Cause, Taxonomy}
import org.goldenport.record.Record

/*
 * Common diagnostics projected from Consequence/Conclusion structure.
 *
 * Metrics, dashboards, and observability should use this projection rather
 * than message parsing, Status.detailCodes, or component-local error taxonomies.
 *
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object ConclusionDiagnostics {
  final case class Classification(
    diagnosticKey: String,
    taxonomy: String,
    causeKind: Option[String],
    parameter: Option[String],
    fieldPath: Option[String],
    policy: Option[String],
    capability: Option[String],
    permission: Option[String],
    guard: Option[String],
    relation: Option[String]
  ) {
    def toRecord: Record = Record.dataAuto(
      "diagnosticKey" -> diagnosticKey,
      "taxonomy" -> taxonomy,
      "causeKind" -> causeKind,
      "parameter" -> parameter,
      "fieldPath" -> fieldPath,
      "policy" -> policy,
      "capability" -> capability,
      "permission" -> permission,
      "guard" -> guard,
      "relation" -> relation
    )
  }

  val unknown: Classification = Classification(
    diagnosticKey = "unknown",
    taxonomy = "unknown",
    causeKind = Some(Cause.Kind.Unknown.name),
    parameter = None,
    fieldPath = None,
    policy = None,
    capability = None,
    permission = None,
    guard = None,
    relation = None
  )

  def classify(conclusion: Conclusion): Classification = {
    val facets = conclusion.observation.cause.descriptor.facets
    val parameters = facets.collect { case Descriptor.Facet.Parameter(_, name) => name }.toSet
    val fieldpaths = facets.collect { case Descriptor.Facet.FieldPath(path) => path }.toSet
    val policies = facets.collect { case Descriptor.Facet.Policy(name) => name }.toSet
    val capabilities = facets.collect { case Descriptor.Facet.Capability(name) => name }.toSet
    val permissions = facets.collect { case Descriptor.Facet.Permission(name) => name }.toSet
    val guards = facets.collect { case Descriptor.Facet.Guard(name) => name }.toSet
    val relations = facets.collect { case Descriptor.Facet.Relation(name) => name }.toSet
    val algorithms = facets.collect { case Descriptor.Facet.Algorithm(name) => name.toLowerCase(java.util.Locale.ROOT) }.toSet
    val causekind = conclusion.observation.cause.kind
    Classification(
      diagnosticKey = _diagnostic_key(conclusion, causekind, parameters, fieldpaths, policies, capabilities, permissions, guards, relations, algorithms),
      taxonomy = conclusion.observation.taxonomy.print,
      causeKind = causekind.map(_.name),
      parameter = parameters.toVector.sorted.headOption,
      fieldPath = fieldpaths.toVector.sorted.headOption,
      policy = policies.toVector.sorted.headOption,
      capability = capabilities.toVector.sorted.headOption,
      permission = permissions.toVector.sorted.headOption,
      guard = guards.toVector.sorted.headOption,
      relation = relations.toVector.sorted.headOption
    )
  }

  def isValidation(conclusion: Conclusion): Boolean =
    conclusion.observation.taxonomy.category == Taxonomy.Category.Argument ||
      conclusion.observation.cause.kind.exists {
        case Cause.Kind.Format | Cause.Kind.Policy | Cause.Kind.Limit | Cause.Kind.Inconsistency => true
        case _ => false
      }

  private def _diagnostic_key(
    conclusion: Conclusion,
    causekind: Option[Cause.Kind],
    parameters: Set[String],
    fieldpaths: Set[String],
    policies: Set[String],
    capabilities: Set[String],
    permissions: Set[String],
    guards: Set[String],
    relations: Set[String],
    algorithms: Set[String]
  ): String = {
    if (_is_cross_component(guards))
      "cross_component"
    else if (_is_abac(guards))
      "abac"
    else
      causekind match {
        case Some(Cause.Kind.Capability) => "capability"
        case Some(Cause.Kind.Permission) => "permission"
        case Some(Cause.Kind.Guard) => "guard"
        case Some(Cause.Kind.Relation) => "relation"
        case Some(Cause.Kind.Format) => _format_key(parameters)
        case Some(Cause.Kind.Policy) => _policy_key(parameters, policies)
        case Some(Cause.Kind.Limit) => _limit_key(parameters, fieldpaths)
        case Some(Cause.Kind.Inconsistency) => _inconsistency_key(parameters, algorithms)
        case Some(Cause.Kind.Conflict) => "conflict"
        case Some(Cause.Kind.Exhaustion) => "exhaustion"
        case Some(Cause.Kind.Timeout) => "timeout"
        case Some(Cause.Kind.Corruption) => "corruption"
        case Some(Cause.Kind.Unknown) => "unknown"
        case None => _taxonomy_key(conclusion)
      }
  }

  private def _format_key(parameters: Set[String]): String =
    if (parameters.contains("contentType"))
      "content_type"
    else if (parameters.contains("expectedByteSize"))
      "expected_size"
    else if (parameters.contains("expectedDigest"))
      "digest"
    else
      "format"

  private def _policy_key(parameters: Set[String], policies: Set[String]): String =
    if (_is_mime_kind(parameters, policies))
      "mime_kind"
    else if (policies.contains("blob.external-url-safety"))
      "external_url"
    else
      "policy"

  private def _limit_key(parameters: Set[String], fieldpaths: Set[String]): String =
    if (_is_payload_size(fieldpaths))
      "payload_size"
    else if (parameters.contains("expectedByteSize"))
      "expected_size"
    else
      "limit"

  private def _inconsistency_key(parameters: Set[String], algorithms: Set[String]): String =
    if (algorithms.contains("sha-256"))
      "digest"
    else if (parameters.contains("expectedByteSize"))
      "expected_size"
    else
      "inconsistency"

  private def _taxonomy_key(conclusion: Conclusion): String = {
    val taxonomy = conclusion.observation.taxonomy
    if (taxonomy.category == Taxonomy.Category.Argument || conclusion.status.webCode.code == 400)
      "argument"
    else if (taxonomy.symptom == Taxonomy.Symptom.PermissionDenied || conclusion.status.webCode.code == 403)
      "authorization"
    else if (taxonomy.symptom == Taxonomy.Symptom.NotFound || conclusion.status.webCode.code == 404)
      "not_found"
    else if (taxonomy.symptom == Taxonomy.Symptom.Conflict || conclusion.status.webCode.code == 409)
      "conflict"
    else
      "unknown"
  }

  private def _is_payload_size(fieldpaths: Set[String]): Boolean =
    fieldpaths.exists {
      case "payload.byteSize" => true
      case "payload.size" => true
      case _ => false
    }

  private def _is_mime_kind(parameters: Set[String], policies: Set[String]): Boolean =
    parameters.contains("contentType") &&
      policies.exists {
        case "mime-kind" => true
        case "content-type-kind" => true
        case _ => false
      }

  private def _is_abac(guards: Set[String]): Boolean =
    guards.contains("abac") || guards.contains("abac-condition")

  private def _is_cross_component(guards: Set[String]): Boolean =
    guards.contains("cross-component") || guards.contains("cross-component-service-grant")

}
