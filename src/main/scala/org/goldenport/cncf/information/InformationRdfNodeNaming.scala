package org.goldenport.cncf.information

import java.time.Instant
import domain.statemachine.informationLifecycle
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationBindingStatus,
  InformationConflict,
  InformationConflictState,
  InformationFieldEvent,
  InformationIdentityBinding,
  InformationLifecycleState,
  InformationPublicationState,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationSpaceCounts,
  InformationSpaceSnapshot,
  InformationValidationIssue
}
import org.goldenport.cncf.knowledge.{
  ExternalKnowledgeIdentifier,
  KnowledgeAttributes,
  KnowledgeEvidence,
  KnowledgeEvidenceId,
  KnowledgeFact,
  KnowledgeFactId,
  KnowledgeFactKind,
  KnowledgeFrame,
  KnowledgeFrameId,
  KnowledgeFrameInputRoute,
  KnowledgeFrameKind,
  KnowledgeFrameOrigin,
  KnowledgeNode,
  KnowledgeNodeBindings,
  KnowledgeNodeCategory,
  KnowledgeNodeId,
  KnowledgeNodeIdentity,
  KnowledgeNodePresentation,
  KnowledgeNodeSources,
  KnowledgeRelationship,
  KnowledgeRelationshipId,
  KnowledgeRelationshipKind,
  KnowledgeRelationshipQualifiers,
  KnowledgeProvenance,
  KnowledgeProvenanceId,
  RdfPredicateName,
  RdfNodeName,
  KnowledgeSourceRef,
  KnowledgeTagBinding,
  KnowledgeWorkingSetSnapshot
}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.tag.TaggingWorkflow
import org.goldenport.record.Record

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class InformationRdfNodeNaming(
  prefix: String = InformationRdfNodeNaming.DEFAULT_PREFIX,
  publicBaseUri: Option[String] = None,
  namespaces: Vector[InformationRdfNamespace] = Vector.empty
) {
  def rdfNodeName(
    information: Information
  ): RdfNodeName =
    RdfNodeName(rdfNodeName(information.domain, shortInformationId(information.id)))

  def rdfNodeName(
    domain: String,
    shortid: String
  ): String =
    s"${currentPrefix}:${_rdf_domain(domain)}/${shortid.trim}"

  def publishedRdfNodeUri(
    domain: String,
    shortid: String
  ): Option[String] =
    currentNamespaceUri.map { namespaceuri =>
      Vector(_strip_trailing_slash(namespaceuri), _rdf_domain(domain), shortid.trim).mkString("/")
    }

  def currentPrefix: String =
    _normalize_prefix(prefix)

  def currentNamespaceUri: Option[String] =
    namespaceUri(currentPrefix).orElse(publicBaseUri)

  def namespaceMappings: Vector[InformationRdfNamespace] = {
    val current =
      currentNamespaceUri.map(uri => InformationRdfNamespace(currentPrefix, uri)).toVector
    (current ++ namespaces.map(_.normalized)).foldLeft(Vector.empty[InformationRdfNamespace]) { (z, x) =>
      if (x.prefix.isEmpty || x.namespaceUri.trim.isEmpty || z.exists(_.prefix == x.prefix))
        z
      else
        z :+ x
    }
  }

  def namespaceUri(
    prefix: String
  ): Option[String] = {
    val key = _normalize_prefix(prefix)
    namespaces.map(_.normalized).find(_.prefix == key).map(_.namespaceUri)
  }

  def shortInformationId(informationid: EntityId): String =
    informationid.entropy.getOrElse {
      val normalized = informationid.print.trim
      if (normalized.length <= 20)
        normalized
      else
        normalized.split("-").lastOption.getOrElse(normalized).take(10)
    }

  private def _normalize_prefix(value: String): String = {
    val normalized = value.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
    if (normalized.isEmpty) InformationRdfNodeNaming.DEFAULT_PREFIX else normalized
  }

  private def _rdf_domain(domain: String): String = {
    val replaced = domain.trim.toLowerCase.replace('_', '-')
    val normalized = replaced.replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
    if (normalized.isEmpty) "information" else normalized
  }

  private def _strip_trailing_slash(value: String): String =
    value.trim.replaceAll("/+$", "")
}

final case class InformationRdfNamespace(
  prefix: String,
  namespaceUri: String
) {
  def normalized: InformationRdfNamespace =
    copy(prefix = _normalize_prefix(prefix), namespaceUri = namespaceUri.trim)

  private def _normalize_prefix(value: String): String =
    value.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
}

object InformationRdfNodeNaming {
  val DEFAULT_PREFIX = "test"
  val DEFAULT_NAMESPACE_URI = "https://example.org/textus/test"
  val SM_PREFIX = "sm"
  val SM_NAMESPACE_URI = "https://www.simplemodeling.org"

  val BUILT_IN_NAMESPACES: Vector[InformationRdfNamespace] =
    Vector(
      InformationRdfNamespace(DEFAULT_PREFIX, DEFAULT_NAMESPACE_URI),
      InformationRdfNamespace(SM_PREFIX, SM_NAMESPACE_URI)
    )

  private val _prefix_keys = Vector(
    "textus.knowledge.rdf.current-prefix",
    "textus.knowledge.rdf.current_prefix",
    "textus.knowledge.rdf.currentPrefix",
    "textus.knowledge.rdf.namespace-prefix",
    "textus.knowledge.rdf.namespace_prefix",
    "textus.knowledge.rdf.namespacePrefix",
    "textus.knowledge.rdf.node-prefix",
    "textus.knowledge.rdf.node_prefix",
    "textus.knowledge.rdf.nodePrefix",
    "textus.knowledge.rdf.prefix"
  )
  private val _namespace_uri_keys = Vector(
    "textus.knowledge.rdf.namespace-uri",
    "textus.knowledge.rdf.namespace_uri",
    "textus.knowledge.rdf.namespaceUri"
  )
  private val _public_base_uri_keys = Vector(
    "textus.knowledge.rdf.public-base-uri",
    "textus.knowledge.rdf.public_base_uri",
    "textus.knowledge.rdf.publicBaseUri",
    "textus.knowledge.rdf.public-base-url",
    "textus.knowledge.rdf.public_base_url",
    "textus.knowledge.rdf.publicBaseUrl"
  )

  val default: InformationRdfNodeNaming =
    InformationRdfNodeNaming(
      prefix = DEFAULT_PREFIX,
      publicBaseUri = Some(DEFAULT_NAMESPACE_URI),
      namespaces = BUILT_IN_NAMESPACES
    )

  def fromExecutionContext(using ctx: ExecutionContext): InformationRdfNodeNaming =
    {
      val prefix = _config_string(_prefix_keys).getOrElse(DEFAULT_PREFIX)
      val namespaceuri =
        _namespace_uri_for_prefix(prefix)
          .orElse(_config_string(_namespace_uri_keys))
          .orElse(_config_string(_public_base_uri_keys))
          .orElse(_built_in_namespace_uri(prefix))
      InformationRdfNodeNaming(
        prefix = prefix,
        publicBaseUri = namespaceuri,
        namespaces = _namespace_mappings(prefix, namespaceuri)
      )
    }

  private def _config_string(
    keys: Vector[String]
  )(using ctx: ExecutionContext): Option[String] =
    _config_values(keys).headOption

  private def _config_values(
    keys: Vector[String]
  )(using ctx: ExecutionContext): Vector[String] =
    keys.flatMap { key =>
      _resolved_parameter_values(key).map(_.trim).filter(_.nonEmpty)
    }

  private def _resolved_parameter_values(
    key: String
  )(using ctx: ExecutionContext): Vector[String] =
    ctx.runtime.resolvedParameters.get(key).toVector.flatMap(param => _string_values(param.value))

  private def _string_values(
    value: ConfigurationValue
  ): Vector[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Vector(v)
      case ConfigurationValue.NumberValue(v) => Vector(v.toString)
      case ConfigurationValue.BooleanValue(v) => Vector(v.toString)
      case ConfigurationValue.ListValue(vs) => vs.toVector.flatMap(_string_values)
      case _ => Vector.empty
    }

  private def _namespace_mappings(
    currentprefix: String,
    currenturi: Option[String]
  )(using ExecutionContext): Vector[InformationRdfNamespace] = {
    val current =
      currenturi.map(uri => InformationRdfNamespace(currentprefix, uri)).toVector
    val configured =
      _namespace_prefixes().flatMap { prefix =>
        _namespace_uri_for_prefix(prefix).map(uri => InformationRdfNamespace(prefix, uri))
      }
    (current ++ configured ++ BUILT_IN_NAMESPACES).foldLeft(Vector.empty[InformationRdfNamespace]) { (z, x) =>
      val normalized = x.normalized
      if (normalized.prefix.isEmpty || normalized.namespaceUri.isEmpty || z.exists(_.prefix == normalized.prefix))
        z
      else
        z :+ normalized
    }
  }

  private def _namespace_prefixes()(using ExecutionContext): Vector[String] =
    _config_values(Vector(
      "textus.knowledge.rdf.namespace-prefixes",
      "textus.knowledge.rdf.namespace_prefixes",
      "textus.knowledge.rdf.namespacePrefixes"
    )).flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty)).distinct

  private def _namespace_uri_for_prefix(
    prefix: String
  )(using ExecutionContext): Option[String] = {
    val normalized = prefix.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
    _config_string(Vector(
      s"textus.knowledge.rdf.namespaces.${normalized}",
      s"textus.knowledge.rdf.namespace.${normalized}"
    ))
  }

  private def _built_in_namespace_uri(
    prefix: String
  ): Option[String] = {
    val normalized = InformationRdfNamespace(prefix, "").normalized.prefix
    BUILT_IN_NAMESPACES.find(_.prefix == normalized).map(_.namespaceUri)
  }
}
