package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.Consequence
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
  KnowledgeProvenance,
  KnowledgeProvenanceId,
  RdfNodeName,
  KnowledgeSourceRef,
  KnowledgeTagBinding,
  KnowledgeWorkingSetSnapshot
}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.tag.TaggingWorkflow
import org.goldenport.record.Record

/*
 * @since   May. 20, 2026
 * @version May. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpace {
  private var _snapshot: InformationSpaceSnapshot = InformationSpaceSnapshot()

  def snapshot: InformationSpaceSnapshot =
    _snapshot

  def counts: InformationSpaceCounts =
    InformationSpaceCounts(
      informationCount = _snapshot.information.size,
      validationIssueCount = _snapshot.information.map(_.validationIssues.size).sum,
      resolutionCandidateCount = _snapshot.information.map(_.resolutionCandidates.size).sum,
      identityBindingCount = _snapshot.information.map(_.identityBindings.size).sum,
      publicationStatusCount = _snapshot.information.map(_.publicationStatuses.size).sum,
      conflictCount = _snapshot.information.map(_.conflicts.size).sum
    )

  def clear(): Unit =
    _snapshot = InformationSpaceSnapshot()

  def registerInformation(
    domain: String,
    records: Vector[Record]
  ): Consequence[Vector[Information]] =
    if (domain.trim.isEmpty)
      Consequence.argumentInvalid("information domain is required")
    else if (records.isEmpty)
      Consequence.argumentInvalid("information records are required")
    else {
      val base = _snapshot.information.size
      val values = records.zipWithIndex.map { case (record, index) =>
        Information(
          id = InformationId(_next_id("information", base + index + 1)),
          domain = domain,
          rawData = record,
          workingData = record
        )
      }
      _snapshot = _snapshot.copy(information = _snapshot.information ++ values)
      Consequence.success(values)
    }

  def getInformation(id: InformationId): Option[Information] =
    _snapshot.information.find(_.id == id)

  def updateInformation(
    informationid: InformationId,
    workingdata: Record
  ): Consequence[Information] =
    _update_information(informationid) { information =>
      information.copy(
        workingData = workingdata,
        state = InformationLifecycleState.Imported,
        validationIssues = Vector.empty,
        updatedAt = Instant.now()
      )
    }

  def validateInformation(informationid: InformationId): Consequence[Information] =
    getInformation(informationid) match {
      case Some(information) =>
        val issues = InformationSpace.validate(information)
        val state =
          if (issues.nonEmpty)
            InformationLifecycleState.Invalid
          else if (information.resolutionCandidates.exists(!_.selected))
            InformationLifecycleState.NeedsResolution
          else
            InformationLifecycleState.ReadyForConfirmation
        val updated = information.copy(
          state = state,
          validationIssues = issues,
          updatedAt = Instant.now()
        )
        _replace_information(updated)
        Consequence.success(updated)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def validationIssues(informationid: InformationId): Vector[InformationValidationIssue] =
    getInformation(informationid).map(_.validationIssues).getOrElse(Vector.empty)

  def addResolutionCandidate(
    informationid: InformationId,
    fieldpath: String,
    label: String,
    binding: InformationIdentityBinding,
    confidence: Option[Double] = None,
    evidence: Option[String] = None
  ): Consequence[InformationResolutionCandidate] =
    getInformation(informationid) match {
      case Some(information) =>
        val key = _next_key("candidate", information.resolutionCandidates.size + 1)
        val nextbinding = binding.copy(status = InformationBindingStatus.Candidate)
        val candidate = InformationResolutionCandidate(key, fieldpath, label, nextbinding, confidence, evidence)
        val updated = information.copy(
          state = InformationLifecycleState.NeedsResolution,
          resolutionCandidates = information.resolutionCandidates :+ candidate,
          identityBindings = information.identityBindings :+ nextbinding,
          updatedAt = Instant.now()
        )
        _replace_information(updated)
        Consequence.success(candidate)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def resolutionCandidates(informationid: InformationId): Vector[InformationResolutionCandidate] =
    getInformation(informationid).map(_.resolutionCandidates).getOrElse(Vector.empty)

  def selectResolutionCandidate(
    informationid: InformationId,
    candidatekey: String
  ): Consequence[InformationResolutionCandidate] =
    getInformation(informationid) match {
      case Some(information) =>
        information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
          case Some(candidate) =>
            val selectedbinding = candidate.binding.copy(status = InformationBindingStatus.Selected)
            val selected = candidate.copy(binding = selectedbinding, selected = true)
            val candidates = information.resolutionCandidates.map(x => if (x.candidateKey == candidatekey) selected else x)
            val bindings = information.identityBindings.map { binding =>
              if (_same_binding(binding, candidate.binding)) selectedbinding else binding
            }
            val state = _state_after_candidate_update(information.copy(resolutionCandidates = candidates))
            val updated = information.copy(
              state = state,
              resolutionCandidates = candidates,
              identityBindings = bindings,
              updatedAt = Instant.now()
            )
            _replace_information(updated)
            Consequence.success(selected)
          case None =>
            Consequence.argumentInvalid(s"information resolution candidate not found: $candidatekey")
        }
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def clearResolutionCandidate(
    informationid: InformationId,
    candidatekey: String
  ): Consequence[InformationResolutionCandidate] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        Consequence.argumentInvalid(s"information is already confirmed: ${informationid.print}")
      case Some(information) =>
        information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
          case Some(candidate) =>
            val candidates = information.resolutionCandidates.filterNot(_.candidateKey == candidatekey)
            val bindings = information.identityBindings.filterNot(_same_binding(_, candidate.binding))
            val updated = information.copy(
              state = _state_after_candidate_update(information.copy(resolutionCandidates = candidates)),
              resolutionCandidates = candidates,
              identityBindings = bindings,
              updatedAt = Instant.now()
            )
            _replace_information(updated)
            Consequence.success(candidate)
          case None =>
            Consequence.argumentInvalid(s"information resolution candidate not found: $candidatekey")
        }
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def confirmInformation(informationid: InformationId): Consequence[Information] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Invalid =>
        Consequence.argumentInvalid(s"information is invalid: ${informationid.print}")
      case Some(information) if information.state != InformationLifecycleState.ReadyForConfirmation && information.state != InformationLifecycleState.Confirmed =>
        Consequence.argumentInvalid(s"information is not ready for confirmation: ${informationid.print}")
      case Some(information) =>
        val bindings = information.identityBindings.map(_.copy(status = InformationBindingStatus.Confirmed))
        val confirmed = information.copy(
          state = InformationLifecycleState.Confirmed,
          identityBindings = bindings,
          confirmedAt = information.confirmedAt.orElse(Some(Instant.now())),
          updatedAt = Instant.now()
        )
        _replace_information(confirmed)
        Consequence.success(confirmed)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def searchInformation(domain: Option[String] = None): Vector[Information] =
    domain match {
      case Some(value) => _snapshot.information.filter(_.domain == value)
      case None => _snapshot.information
    }

  def rejectInformation(
    informationid: InformationId,
    reason: String
  ): Consequence[Information] =
    _update_information(informationid)(_.copy(state = InformationLifecycleState.Rejected, updatedAt = Instant.now()))

  def reopenInformation(informationid: InformationId): Consequence[Information] =
    _update_information(informationid)(_.copy(state = InformationLifecycleState.ReadyForConfirmation, updatedAt = Instant.now()))

  def publishInformation(
    informationid: InformationId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): Consequence[InformationPublicationStatus] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        val key = information.publicationStatuses.headOption.map(_.publicationKey).getOrElse(_next_key("publication", 1))
        val publication = InformationPublicationStatus(
          publicationKey = key,
          state = InformationPublicationState.Published,
          target = target,
          message = message,
          knowledgeFrameId = knowledgeframeid,
          publishedAt = Some(Instant.now())
        )
        val published = information.copy(
          state = InformationLifecycleState.Published,
          publicationStatuses = information.publicationStatuses.filterNot(_.publicationKey == key) :+ publication,
          updatedAt = Instant.now()
        )
        _replace_information(published)
        Consequence.success(publication)
      case Some(_) =>
        Consequence.argumentInvalid(s"information is not confirmed: ${informationid.print}")
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def failInformationPublication(
    informationid: InformationId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): Consequence[InformationPublicationStatus] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        val key = information.publicationStatuses.headOption.map(_.publicationKey).getOrElse(_next_key("publication", 1))
        val publication = InformationPublicationStatus(
          publicationKey = key,
          state = InformationPublicationState.Failed,
          target = target,
          message = message,
          knowledgeFrameId = knowledgeframeid,
          publishedAt = Some(Instant.now())
        )
        val failed = information.copy(
          publicationStatuses = information.publicationStatuses.filterNot(_.publicationKey == key) :+ publication,
          updatedAt = Instant.now()
        )
        _replace_information(failed)
        Consequence.success(publication)
      case Some(_) =>
        Consequence.argumentInvalid(s"information is not confirmed: ${informationid.print}")
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def publicationStatusOption(
    informationid: InformationId,
    publicationkey: String
  ): Option[InformationPublicationStatus] =
    getInformation(informationid).flatMap(_.publicationStatuses.find(_.publicationKey == publicationkey))

  def recordConflict(
    informationid: InformationId,
    fieldpath: String,
    informationvalue: String,
    rdfvalue: String,
    severity: String = "warning"
  ): Consequence[InformationConflict] =
    getInformation(informationid) match {
      case Some(information) =>
        val conflict = InformationConflict(
          conflictKey = _next_key("conflict", information.conflicts.size + 1),
          fieldPath = fieldpath,
          informationValue = informationvalue,
          rdfValue = rdfvalue,
          severity = severity
        )
        val updated = information.copy(
          state = InformationLifecycleState.Conflict,
          conflicts = information.conflicts :+ conflict,
          updatedAt = Instant.now()
        )
        _replace_information(updated)
        Consequence.success(conflict)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def conflicts(filterstate: Option[InformationConflictState] = None): Vector[InformationConflict] = {
    val values = _snapshot.information.flatMap(_.conflicts)
    filterstate match {
      case Some(value) => values.filter(_.state == value)
      case None => values
    }
  }

  def resolveConflict(
    informationid: InformationId,
    conflictkey: String,
    decision: String
  ): Consequence[InformationConflict] =
    getInformation(informationid) match {
      case Some(information) =>
        information.conflicts.find(_.conflictKey == conflictkey) match {
          case Some(conflict) =>
            val resolved = conflict.copy(
              state = InformationConflictState.Resolved,
              resolution = Some(decision)
            )
            val conflicts = information.conflicts.map(x => if (x.conflictKey == conflictkey) resolved else x)
            val state =
              if (conflicts.forall(_.state == InformationConflictState.Resolved))
                information.publicationStatuses.find(_.state == InformationPublicationState.Published).fold(InformationLifecycleState.Confirmed)(_ => InformationLifecycleState.Published)
              else
                InformationLifecycleState.Conflict
            _replace_information(information.copy(state = state, conflicts = conflicts, updatedAt = Instant.now()))
            Consequence.success(resolved)
          case None =>
            Consequence.argumentInvalid(s"information conflict not found: $conflictkey")
        }
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def materializeInformation(informationid: InformationId): Consequence[KnowledgeWorkingSetSnapshot] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        Consequence.success(InformationToKnowledgeProjection.materialize(information))
      case Some(_) =>
        Consequence.argumentInvalid(s"information is not knowledge-ready: ${informationid.print}")
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  private def _update_information(
    informationid: InformationId
  )(f: Information => Information): Consequence[Information] =
    getInformation(informationid) match {
      case Some(information) =>
        val updated = f(information)
        _replace_information(updated)
        Consequence.success(updated)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  private def _replace_information(information: Information): Unit =
    _snapshot = _snapshot.copy(
      information = _snapshot.information.map(x => if (x.id == information.id) information else x)
    )

  private def _next_id(
    prefix: String,
    index: Int
  ): String =
    s"$prefix-$index"

  private def _next_key(
    prefix: String,
    index: Int
  ): String =
    s"$prefix-$index"

  private def _state_after_candidate_update(information: Information): InformationLifecycleState =
    if (information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published)
      information.state
    else if (information.validationIssues.nonEmpty)
      InformationLifecycleState.Invalid
    else if (information.resolutionCandidates.isEmpty)
      InformationLifecycleState.Imported
    else if (information.resolutionCandidates.forall(_.selected))
      InformationLifecycleState.ReadyForConfirmation
    else
      InformationLifecycleState.NeedsResolution

  private def _same_binding(
    lhs: InformationIdentityBinding,
    rhs: InformationIdentityBinding
  ): Boolean =
    lhs.rdfSubject == rhs.rdfSubject &&
      lhs.externalIdentifiers == rhs.externalIdentifiers &&
      lhs.entityBindings == rhs.entityBindings &&
      lhs.knowledgeNodeId == rhs.knowledgeNodeId &&
      lhs.authority == rhs.authority
}

object InformationSpace {
  def validate(information: Information): Vector[InformationValidationIssue] =
    information.domain match {
      case "paper" => _validate_paper(information)
      case "book" => _validate_book(information)
      case "web-resource" => _validate_web_resource(information)
      case _ => Vector.empty
    }

  def materializeInformation(information: Information): KnowledgeWorkingSetSnapshot =
    InformationToKnowledgeProjection.materialize(information)

  def materializeInformationWithTags(information: Information)(using ExecutionContext): Consequence[KnowledgeWorkingSetSnapshot] =
    InformationTagging.knowledgeTagBindings(information.id).map { tagbindings =>
      InformationToKnowledgeProjection.materialize(information, tagbindings, InformationRdfNodeNaming.fromExecutionContext)
    }

  private def _validate_paper(information: Information): Vector[InformationValidationIssue] = {
    val paper = PaperInformation.from(information.workingData)
    val base = Vector.newBuilder[InformationValidationIssue]
    if (paper.title.isEmpty)
      base += InformationValidationIssue("title", "error", "title is required")
    base.result()
  }

  private def _validate_book(information: Information): Vector[InformationValidationIssue] = {
    val title = information.workingData.getString("title").getOrElse("").trim
    val base = Vector.newBuilder[InformationValidationIssue]
    if (title.isEmpty)
      base += InformationValidationIssue("title", "error", "title is required")
    base.result()
  }

  private def _validate_web_resource(information: Information): Vector[InformationValidationIssue] = {
    val webresource = WebResourceInformation.from(information.workingData)
    val base = Vector.newBuilder[InformationValidationIssue]
    if (webresource.title.isEmpty)
      base += InformationValidationIssue("title", "error", "title is required")
    if (webresource.url.isEmpty && webresource.canonicalUrl.isEmpty)
      base += InformationValidationIssue("url", "error", "url or canonicalUrl is required")
    base.result()
  }
}

object InformationTagging {
  val TagSpace: String = InformationSpaceEditorProjection.InformationTagSpace
  val Role: String = InformationSpaceEditorProjection.InformationTagRole

  def workflow(tagspace: String = TagSpace): TaggingWorkflow =
    TaggingWorkflow(tagSpace = tagspace)

  def knowledgeTagBindings(
    informationid: InformationId,
    tagspace: String = TagSpace
  )(using ExecutionContext): Consequence[Vector[KnowledgeTagBinding]] =
    workflow(tagspace).listEntityTags(informationid.print, Some(Role)).map { summary =>
      summary.tags.map(tag => KnowledgeTagBinding(tag.tagSpace, tag.id.value))
    }
}

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

  def shortInformationId(informationid: InformationId): String =
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

object InformationToKnowledgeProjection {
  def materialize(information: Information): KnowledgeWorkingSetSnapshot =
    materialize(information, Vector.empty)

  def materialize(
    information: Information,
    tagbindings: Vector[KnowledgeTagBinding],
    naming: InformationRdfNodeNaming = InformationRdfNodeNaming.default
  ): KnowledgeWorkingSetSnapshot = {
    val provenance = KnowledgeProvenance(
      KnowledgeProvenanceId(s"prov-${information.id.print}"),
      origin = "information-space",
      generatedBy = Some("information.materialize")
    )
    val evidence = KnowledgeEvidence(
      KnowledgeEvidenceId(s"ev-${information.id.print}"),
      "information",
      KnowledgeSourceRef("information", information.id.print),
      information.data.getString("title"),
      Some(provenance.id)
    )
    val node = KnowledgeNode(
      id = KnowledgeNodeId(s"information-${information.id.print}"),
      category = KnowledgeNodeCategory(information.domain),
      identity = KnowledgeNodeIdentity(
        rdfNode = Some(naming.rdfNodeName(information)),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("cncf.information", information.id.print, Some(information.domain)))
      ),
      presentation = KnowledgeNodePresentation.label(information.data.getString("title").getOrElse(information.id.print)),
      sources = KnowledgeNodeSources(
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id)
      ),
      bindings = KnowledgeNodeBindings(tagBindings = tagbindings),
      attributes = KnowledgeAttributes("information_domain" -> information.domain)
    )
    val fact = KnowledgeFact(
      id = KnowledgeFactId(s"fact-${information.id.print}-title"),
      kind = KnowledgeFactKind.EntityDerived,
      subjectNodeId = Some(node.id),
      predicate = Some("information.title"),
      value = information.data.getString("title"),
      evidenceIds = Vector(evidence.id),
      provenanceId = Some(provenance.id)
    )
    val frame = KnowledgeFrame(
      id = KnowledgeFrameId(s"frame-${information.id.print}"),
      kind = KnowledgeFrameKind.Curated,
      focusNodeIds = Vector(node.id),
      nodeIds = Vector(node.id),
      factIds = Vector(fact.id),
      evidenceIds = Vector(evidence.id),
      provenanceIds = Vector(provenance.id),
      origin = KnowledgeFrameOrigin(
        KnowledgeFrameInputRoute.BatchImport,
        provider = Some("cncf-information"),
        operation = Some("information.materialize"),
        provenanceId = Some(provenance.id)
      ),
      sourceRefs = Vector(KnowledgeSourceRef("information", information.id.print)),
      materializedAt = Some(Instant.now())
    )
    KnowledgeWorkingSetSnapshot(
      nodes = Vector(node),
      evidence = Vector(evidence),
      provenance = Vector(provenance),
      frames = Vector(frame),
      facts = Vector(fact)
    )
  }
}
