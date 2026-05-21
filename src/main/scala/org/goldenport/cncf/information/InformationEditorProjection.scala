package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.record.Record

/*
 * @since   May. 21, 2026
 * @version May. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class InformationFieldMappingDescriptor(
  targetKind: String,
  targetPath: String,
  profileLayer: String,
  description: String
)

final case class InformationFieldDescriptor(
  fieldPath: String,
  label: String,
  description: String,
  example: Option[String],
  requiredness: String,
  validationHint: Option[String],
  resolverAssisted: Boolean,
  mappings: Vector[InformationFieldMappingDescriptor]
)

final case class InformationEditorActionDescriptor(
  name: String,
  label: String,
  enabled: Boolean,
  reason: Option[String] = None
)

final case class InformationEditorFieldProjection(
  descriptor: InformationFieldDescriptor,
  value: Option[String],
  validationIssues: Vector[InformationValidationIssue],
  resolutionCandidates: Vector[InformationResolutionCandidate],
  conflicts: Vector[InformationConflict]
)

final case class InformationEditorRecordProjection(
  recordId: Option[InformationRecordId],
  itemId: Option[InformationItemId],
  domain: String,
  state: InformationLifecycleState,
  title: Option[String],
  fields: Vector[InformationEditorFieldProjection],
  publication: Option[InformationPublicationStatus],
  actions: Vector[InformationEditorActionDescriptor]
)

final case class InformationEditorProjection(
  componentName: String,
  domain: String,
  fields: Vector[InformationFieldDescriptor],
  records: Vector[InformationEditorRecordProjection],
  items: Vector[InformationEditorRecordProjection]
)

final case class InformationEditorProfile(
  domain: String,
  fields: Vector[InformationFieldDescriptor]
)

object InformationEditorProfile {
  val BOOK_DOMAIN = "book"
  val PAPER_DOMAIN = "paper"
  val WEB_RESOURCE_DOMAIN = "web-resource"
  val COMMON_NEIGHBORHOOD = "common-neighborhood"
  val BOOK_PROFILE_EXTENSION = "book-profile-extension"
  val PAPER_PROFILE_EXTENSION = "paper-profile-extension"
  val WEB_RESOURCE_PROFILE_EXTENSION = "web-resource-profile-extension"

  def forDomain(domain: String): Option[InformationEditorProfile] =
    domain match {
      case BOOK_DOMAIN => Some(book)
      case PAPER_DOMAIN => Some(paper)
      case WEB_RESOURCE_DOMAIN => Some(webResource)
      case _ => None
    }

  val book: InformationEditorProfile =
    InformationEditorProfile(
      BOOK_DOMAIN,
      Vector(
        _field(
          "isbn13",
          "ISBN-13",
          "Use the 13-digit ISBN to seed or reconcile book information.",
          Some("9780134685991"),
          "recommended",
          Some("Normalize hyphenated input. Do not use ISBN as a CNCF id."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", BOOK_PROFILE_EXTENSION, "External identifier and lookup key."),
          _mapping("frame", "book.identity-neighborhood", BOOK_PROFILE_EXTENSION, "Book identity evidence in the 1.5hop+ neighborhood.")
        ),
        _field(
          "isbn10",
          "ISBN-10",
          "Preserve ISBN-10 when provided, but prefer ISBN-13 for lookup.",
          Some("0134685997"),
          "optional",
          Some("Keep as an external identifier."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", BOOK_PROFILE_EXTENSION, "External identifier.")
        ),
        _field(
          "doi",
          "DOI",
          "Digital Object Identifier when the book or edition has one.",
          Some("10.1000/example"),
          "optional",
          Some("Normalize only when a resolver requires DOI URI form."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", BOOK_PROFILE_EXTENSION, "External identifier or RDF-like anchor.")
        ),
        _field(
          "openLibraryId",
          "Open Library ID",
          "Open Library work or edition identifier used for lookup and enrichment.",
          Some("OL12345W"),
          "optional",
          Some("Preserve work and edition distinction."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", BOOK_PROFILE_EXTENSION, "External source identifier."),
          _mapping("relationship", "structure.correspondences.sourceAlignments", BOOK_PROFILE_EXTENSION, "Source alignment candidate.")
        ),
        _field(
          "wikidataId",
          "Wikidata QID",
          "Authority-oriented RDF anchor candidate for the book, work, person, publisher, or concept.",
          Some("Q12345"),
          "optional",
          Some("Treat as candidate until confirmed in InformationSpace."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", BOOK_PROFILE_EXTENSION, "External RDF anchor candidate."),
          _mapping("relationship", "structure.correspondences.sameResources", COMMON_NEIGHBORHOOD, "Confirmed same-resource or same-concept link.")
        ),
        _field(
          "dbpediaUri",
          "DBpedia URI",
          "DBpedia resource URI used as an RDF enrichment anchor.",
          Some("http://dbpedia.org/resource/..."),
          "optional",
          Some("DBpedia is an enrichment source, not automatically authoritative."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", BOOK_PROFILE_EXTENSION, "External RDF anchor candidate."),
          _mapping("evidence", "sources.evidenceIds", COMMON_NEIGHBORHOOD, "Resolver evidence for imported labels, abstracts, categories, and links.")
        ),
        _field(
          "title",
          "Title",
          "Main title shown to users and used for candidate matching.",
          Some("Domain-Driven Design"),
          "required",
          Some("Required before confirmation."),
          resolverassisted = false,
          _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Display label for the book node."),
          _mapping("fact", "information.title", COMMON_NEIGHBORHOOD, "Confirmed title fact with evidence.")
        ),
        _field(
          "subtitle",
          "Subtitle",
          "Subtitle or secondary title. Keep separate from the main title.",
          Some("Tackling Complexity in the Heart of Software"),
          "optional",
          None,
          resolverassisted = false,
          _mapping("knowledge-node-section", "presentation.labels", BOOK_PROFILE_EXTENSION, "Additional display title.")
        ),
        _field(
          "localizedTitles",
          "Localized titles",
          "Language-tagged titles or translated title variants.",
          Some("ja: ドメイン駆動設計"),
          "optional",
          Some("Preserve language tags where known."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Localized labels."),
          _mapping("relationship", "structure.correspondences.localizedVersions", COMMON_NEIGHBORHOOD, "Localized version correspondence.")
        ),
        _field(
          "authors",
          "Authors",
          "People or organizations credited as authors. Preserve order when known.",
          Some("Eric Evans"),
          "recommended",
          Some("Author order and role are relationship qualifiers."),
          resolverassisted = true,
          _mapping("relationship", "authored-by", BOOK_PROFILE_EXTENSION, "Canonical authorship relationship."),
          _mapping("frame", "book.contributor-neighborhood", BOOK_PROFILE_EXTENSION, "Author authority nodes and evidence.")
        ),
        _field(
          "editors",
          "Editors",
          "People or organizations credited as editors.",
          None,
          "optional",
          Some("Editor role is explicit relationship metadata."),
          resolverassisted = true,
          _mapping("relationship", "edited-by", BOOK_PROFILE_EXTENSION, "Canonical editor relationship.")
        ),
        _field(
          "publisher",
          "Publisher",
          "Publisher organization or agent.",
          Some("Addison-Wesley"),
          "recommended",
          Some("Resolvable publishers should become relationship targets."),
          resolverassisted = true,
          _mapping("relationship", "published-by", BOOK_PROFILE_EXTENSION, "Canonical publisher relationship.")
        ),
        _field(
          "publicationDate",
          "Publication date",
          "Publication or edition date. Preserve precision when only year or month is known.",
          Some("2003-08-30"),
          "recommended",
          None,
          resolverassisted = true,
          _mapping("knowledge-node-section", "semantics.temporal", COMMON_NEIGHBORHOOD, "Publication temporal value."),
          _mapping("fact", "publication.date", BOOK_PROFILE_EXTENSION, "Publication fact with evidence.")
        ),
        _field(
          "language",
          "Language",
          "Primary language of the book or edition.",
          Some("en"),
          "recommended",
          Some("Affects labels, descriptions, and resolver matching."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "semantics.roles", BOOK_PROFILE_EXTENSION, "Language-related semantic metadata.")
        ),
        _field(
          "summary",
          "Summary",
          "Short human-written overview used for display and retrieval.",
          Some("A practical guide to domain modeling."),
          "recommended",
          None,
          resolverassisted = false,
          _mapping("knowledge-node-section", "presentation.descriptions", COMMON_NEIGHBORHOOD, "Human-facing description."),
          _mapping("knowledge-node-section", "similarity.representations", COMMON_NEIGHBORHOOD, "Search representation source; raw vectors are not stored.")
        ),
        _field(
          "subjects",
          "Subjects",
          "Topics, categories, or concepts connected to the book.",
          Some("Software design"),
          "recommended",
          Some("Subjects may become classification nodes."),
          resolverassisted = true,
          _mapping("relationship", "classified-by", COMMON_NEIGHBORHOOD, "Canonical classification relationship."),
          _mapping("knowledge-node-section", "structure.classifications", COMMON_NEIGHBORHOOD, "Derived classification traversal.")
        ),
        _field(
          "citations",
          "Citations",
          "Works cited by this book or important cited works connected to the book.",
          None,
          "optional",
          Some("Citation context and page range are qualifiers."),
          resolverassisted = false,
          _mapping("relationship", "cites", BOOK_PROFILE_EXTENSION, "Canonical citation relationship."),
          _mapping("frame", "book.citation-neighborhood", BOOK_PROFILE_EXTENSION, "Citation target nodes and evidence.")
        )
      )
    )

  val paper: InformationEditorProfile =
    InformationEditorProfile(
      PAPER_DOMAIN,
      Vector(
        _field(
          "title",
          "Title",
          "Main paper title shown to users and used for resolver matching.",
          Some("Knowledge Editing with InformationSpace"),
          "required",
          Some("Required before confirmation."),
          resolverassisted = false,
          _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Display label for the paper node."),
          _mapping("fact", "information.title", COMMON_NEIGHBORHOOD, "Confirmed title fact with evidence.")
        ),
        _field(
          "authors",
          "Authors",
          "People or organizations credited as paper authors. Preserve order when known.",
          Some("Alice Example; Bob Example"),
          "recommended",
          Some("Author order and role are relationship qualifiers."),
          resolverassisted = true,
          _mapping("relationship", "authored-by", PAPER_PROFILE_EXTENSION, "Canonical authorship relationship."),
          _mapping("frame", "paper.contributor-neighborhood", PAPER_PROFILE_EXTENSION, "Author authority nodes and evidence.")
        ),
        _field(
          "doi",
          "DOI",
          "Digital Object Identifier used as a lookup key and external identifier candidate.",
          Some("10.1000/example"),
          "recommended",
          Some("Store as an external identifier; do not use it as a CNCF id."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", PAPER_PROFILE_EXTENSION, "External identifier and lookup key."),
          _mapping("frame", "paper.identity-neighborhood", PAPER_PROFILE_EXTENSION, "Paper identity evidence in the 1.5hop+ neighborhood.")
        ),
        _field(
          "arxivId",
          "arXiv ID",
          "arXiv identifier used as a local external identifier anchor.",
          Some("2401.00001"),
          "optional",
          Some("Preserve the supplied identifier distinctly from DOI and RDF anchors."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", PAPER_PROFILE_EXTENSION, "External source identifier.")
        ),
        _field(
          "pubmedId",
          "PubMed ID",
          "PubMed identifier used as a local external identifier anchor.",
          Some("12345678"),
          "optional",
          None,
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", PAPER_PROFILE_EXTENSION, "External source identifier.")
        ),
        _field(
          "semanticScholarId",
          "Semantic Scholar ID",
          "Semantic Scholar identifier used as a local external identifier anchor.",
          Some("abc123"),
          "optional",
          None,
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", PAPER_PROFILE_EXTENSION, "External source identifier.")
        ),
        _field(
          "openAlexId",
          "OpenAlex ID",
          "OpenAlex work identifier used as a local external identifier anchor.",
          Some("W123456"),
          "optional",
          None,
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", PAPER_PROFILE_EXTENSION, "External source identifier.")
        ),
        _field(
          "wikidataId",
          "Wikidata QID",
          "External RDF-like knowledge anchor candidate for the paper, venue, author, or concept.",
          Some("Q12345"),
          "optional",
          Some("Treat as a candidate until confirmed in InformationSpace."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", PAPER_PROFILE_EXTENSION, "External RDF anchor candidate."),
          _mapping("relationship", "structure.correspondences.sameResources", COMMON_NEIGHBORHOOD, "Confirmed same-resource or same-concept link.")
        ),
        _field(
          "dbpediaUri",
          "DBpedia URI",
          "DBpedia resource URI used as an RDF enrichment anchor.",
          Some("http://dbpedia.org/resource/..."),
          "optional",
          Some("DBpedia is an enrichment source, not automatically authoritative."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", PAPER_PROFILE_EXTENSION, "External RDF anchor candidate."),
          _mapping("evidence", "sources.evidenceIds", COMMON_NEIGHBORHOOD, "Resolver evidence for imported labels, abstracts, categories, and links.")
        ),
        _field(
          "venue",
          "Venue",
          "Journal, conference, workshop, proceedings, or publication venue.",
          Some("Textus Knowledge Workshop"),
          "recommended",
          Some("Resolvable venues should become relationship targets."),
          resolverassisted = true,
          _mapping("relationship", "published-in", PAPER_PROFILE_EXTENSION, "Canonical venue relationship."),
          _mapping("frame", "paper.venue-neighborhood", PAPER_PROFILE_EXTENSION, "Venue node and evidence.")
        ),
        _field(
          "publicationDate",
          "Publication date",
          "Publication date or year. Preserve precision when only year or month is known.",
          Some("2026"),
          "recommended",
          None,
          resolverassisted = true,
          _mapping("knowledge-node-section", "semantics.temporal", COMMON_NEIGHBORHOOD, "Publication temporal value."),
          _mapping("fact", "publication.date", PAPER_PROFILE_EXTENSION, "Publication fact with evidence.")
        ),
        _field(
          "language",
          "Language",
          "Primary paper language.",
          Some("en"),
          "optional",
          Some("Affects labels, abstracts, and resolver matching."),
          resolverassisted = false,
          _mapping("knowledge-node-section", "semantics.roles", PAPER_PROFILE_EXTENSION, "Language-related semantic metadata.")
        ),
        _field(
          "abstract",
          "Abstract",
          "Paper abstract or short description used for display and retrieval.",
          Some("A paper about editing knowledge as curated information."),
          "recommended",
          None,
          resolverassisted = true,
          _mapping("knowledge-node-section", "presentation.descriptions", COMMON_NEIGHBORHOOD, "Human-facing description."),
          _mapping("knowledge-node-section", "similarity.representations", COMMON_NEIGHBORHOOD, "Search representation source; raw vectors are not stored.")
        ),
        _field(
          "keywords",
          "Keywords",
          "Author keywords or topics connected to the paper.",
          Some("InformationSpace, KnowledgeSpace"),
          "recommended",
          Some("Keywords may become classification nodes."),
          resolverassisted = true,
          _mapping("relationship", "classified-by", COMMON_NEIGHBORHOOD, "Canonical classification relationship."),
          _mapping("knowledge-node-section", "structure.classifications", COMMON_NEIGHBORHOOD, "Derived classification traversal.")
        ),
        _field(
          "citations",
          "Citations",
          "Works cited by this paper or important cited works connected to the paper.",
          None,
          "optional",
          Some("Citation context and page range are qualifiers."),
          resolverassisted = false,
          _mapping("relationship", "cites", PAPER_PROFILE_EXTENSION, "Canonical citation relationship."),
          _mapping("frame", "paper.citation-neighborhood", PAPER_PROFILE_EXTENSION, "Citation target nodes and evidence.")
        ),
        _field(
          "sourceUrl",
          "Source URL",
          "Source page or document URL used as evidence for this paper information.",
          Some("https://example.org/paper"),
          "recommended",
          Some("Store as a source/evidence reference, not as a CNCF id."),
          resolverassisted = true,
          _mapping("evidence", "sources.sourceRefs", COMMON_NEIGHBORHOOD, "Source reference for evidence and provenance."),
          _mapping("provenance", "origin.source", COMMON_NEIGHBORHOOD, "Origin metadata for imported information.")
        )
      )
    )

  val webResource: InformationEditorProfile =
    InformationEditorProfile(
      WEB_RESOURCE_DOMAIN,
      Vector(
        _field(
          "url",
          "URL",
          "Original web resource URL entered by the editor.",
          Some("https://example.org/article"),
          "required-one-of",
          Some("Required before confirmation unless canonical URL is present. Store as an external identifier/source anchor, not a CNCF id."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", WEB_RESOURCE_PROFILE_EXTENSION, "External URL identifier and source anchor."),
          _mapping("evidence", "sources.sourceRefs", COMMON_NEIGHBORHOOD, "Source reference for evidence and provenance.")
        ),
        _field(
          "canonicalUrl",
          "Canonical URL",
          "Canonical URL selected or fetched for the web resource.",
          Some("https://example.org/canonical/article"),
          "required-one-of",
          Some("Required before confirmation unless URL is present. Treat fetched values as reviewable candidates."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", WEB_RESOURCE_PROFILE_EXTENSION, "Canonical external URL identifier."),
          _mapping("relationship", "structure.correspondences.sourceAlignments", COMMON_NEIGHBORHOOD, "Source alignment candidate.")
        ),
        _field(
          "finalUrl",
          "Final URL",
          "Final URL reached by metadata fetch or redirect handling.",
          Some("https://example.org/final/article"),
          "optional",
          Some("Fetched metadata only. Preserve the editor-entered original URL separately."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "identity.externalIdentifiers", WEB_RESOURCE_PROFILE_EXTENSION, "Fetched final URL identifier candidate."),
          _mapping("provenance", "origin.fetch.finalUrl", COMMON_NEIGHBORHOOD, "Fetch provenance and redirect result.")
        ),
        _field(
          "title",
          "Title",
          "Main web resource title shown to users and used for resolver matching.",
          Some("Knowledge editing overview"),
          "required",
          Some("Required before confirmation."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Display label for the web resource node."),
          _mapping("fact", "information.title", COMMON_NEIGHBORHOOD, "Confirmed title fact with evidence.")
        ),
        _field(
          "siteName",
          "Site name",
          "Site, publication, or container name associated with the web resource.",
          Some("Example Knowledge Notes"),
          "recommended",
          Some("Resolvable sites may become relationship targets."),
          resolverassisted = true,
          _mapping("relationship", "published-in", WEB_RESOURCE_PROFILE_EXTENSION, "Canonical site/container relationship."),
          _mapping("frame", "web-resource.site-neighborhood", WEB_RESOURCE_PROFILE_EXTENSION, "Site node and evidence.")
        ),
        _field(
          "publisher",
          "Publisher",
          "Publisher organization or agent.",
          Some("Example Org"),
          "optional",
          Some("Publisher can become a relationship target when confirmed."),
          resolverassisted = true,
          _mapping("relationship", "published-by", WEB_RESOURCE_PROFILE_EXTENSION, "Canonical publisher relationship.")
        ),
        _field(
          "author",
          "Author",
          "Author or creator text as supplied by metadata or an editor.",
          Some("Alice Example"),
          "recommended",
          Some("Author order and role remain relationship qualifiers."),
          resolverassisted = true,
          _mapping("relationship", "authored-by", WEB_RESOURCE_PROFILE_EXTENSION, "Canonical authorship relationship."),
          _mapping("frame", "web-resource.contributor-neighborhood", WEB_RESOURCE_PROFILE_EXTENSION, "Author authority nodes and evidence.")
        ),
        _field(
          "retrievedAt",
          "Retrieved at",
          "Timestamp when metadata was fetched or reviewed.",
          Some("2026-05-22T10:00:00Z"),
          "recommended",
          Some("Use as provenance metadata for fetched values."),
          resolverassisted = false,
          _mapping("provenance", "origin.retrievedAt", COMMON_NEIGHBORHOOD, "Retrieval timestamp for imported metadata.")
        ),
        _field(
          "summary",
          "Summary",
          "Short summary, meta description, or editor-entered description.",
          Some("A page about editable knowledge resources."),
          "recommended",
          None,
          resolverassisted = true,
          _mapping("knowledge-node-section", "presentation.descriptions", COMMON_NEIGHBORHOOD, "Human-facing description."),
          _mapping("knowledge-node-section", "similarity.representations", COMMON_NEIGHBORHOOD, "Search representation source; raw vectors are not stored.")
        ),
        _field(
          "language",
          "Language",
          "Primary language detected or edited for the web resource.",
          Some("en"),
          "optional",
          Some("Affects labels, summaries, and resolver matching."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "semantics.roles", WEB_RESOURCE_PROFILE_EXTENSION, "Language-related semantic metadata.")
        ),
        _field(
          "keywords",
          "Keywords",
          "Keywords, subjects, or tags associated with the web resource.",
          Some("InformationSpace, KnowledgeSpace"),
          "recommended",
          Some("Keywords may become classification nodes."),
          resolverassisted = true,
          _mapping("relationship", "classified-by", COMMON_NEIGHBORHOOD, "Canonical classification relationship."),
          _mapping("knowledge-node-section", "structure.classifications", COMMON_NEIGHBORHOOD, "Derived classification traversal.")
        ),
        _field(
          "links",
          "Links",
          "Selected outbound links retained as reviewable metadata references.",
          Some("https://dbpedia.org/resource/Knowledge_graph"),
          "optional",
          Some("Use selected links as candidates only; do not store crawled bodies."),
          resolverassisted = true,
          _mapping("relationship", "references", WEB_RESOURCE_PROFILE_EXTENSION, "Canonical referenced-resource relationship."),
          _mapping("frame", "web-resource.link-neighborhood", WEB_RESOURCE_PROFILE_EXTENSION, "Linked resource candidates and evidence.")
        ),
        _field(
          "sourceUrl",
          "Source URL",
          "Source URL used as evidence for manually entered or fetched values.",
          Some("https://example.org/article"),
          "recommended",
          Some("Store as a source/evidence reference, not as a CNCF id."),
          resolverassisted = true,
          _mapping("evidence", "sources.sourceRefs", COMMON_NEIGHBORHOOD, "Source reference for evidence and provenance."),
          _mapping("provenance", "origin.source", COMMON_NEIGHBORHOOD, "Origin metadata for imported information.")
        ),
        _field(
          "reviewerNote",
          "Reviewer note",
          "Internal reviewer note for curation and confirmation decisions.",
          None,
          "optional",
          None,
          resolverassisted = false,
          _mapping("provenance", "curation.note", COMMON_NEIGHBORHOOD, "Human curation note.")
        )
      )
    )

  private def _field(
    fieldpath: String,
    label: String,
    description: String,
    example: Option[String],
    requiredness: String,
    validationhint: Option[String],
    resolverassisted: Boolean,
    mappings: InformationFieldMappingDescriptor*
  ): InformationFieldDescriptor =
    InformationFieldDescriptor(
      fieldpath,
      label,
      description,
      example,
      requiredness,
      validationhint,
      resolverassisted,
      mappings.toVector
    )

  private def _mapping(
    targetkind: String,
    targetpath: String,
    profilelayer: String,
    description: String
  ): InformationFieldMappingDescriptor =
    InformationFieldMappingDescriptor(targetkind, targetpath, profilelayer, description)
}

object InformationSpaceEditorProjection {
  def component(
    component: Component,
    domain: String
  ): Consequence[InformationEditorProjection] =
    InformationEditorProfile.forDomain(domain) match {
      case Some(profile) =>
        val snapshot = component.informationSpace.snapshot
        Consequence.success(InformationEditorProjection(
          component.name,
          profile.domain,
          profile.fields,
          _record_projections(profile, snapshot),
          _item_projections(profile, snapshot)
        ))
      case None =>
        Consequence.argumentInvalid(s"information editor profile not found: $domain")
    }

  def profileOption(domain: String): Option[InformationEditorProfile] =
    InformationEditorProfile.forDomain(domain)

  private def _record_projections(
    profile: InformationEditorProfile,
    snapshot: InformationSpaceSnapshot
  ): Vector[InformationEditorRecordProjection] =
    snapshot.records
      .filter(_.domain == profile.domain)
      .sortBy(_.id.print)
      .map { record =>
        InformationEditorRecordProjection(
          recordId = Some(record.id),
          itemId = record.itemId,
          domain = record.domain,
          state = record.state,
          title = _title(record.workingData),
          fields = profile.fields.map(field => _record_field_projection(field, record, snapshot)),
          publication = record.itemId.flatMap(itemid => _publication(snapshot, itemid)),
          actions = _record_actions(record)
        )
      }

  private def _item_projections(
    profile: InformationEditorProfile,
    snapshot: InformationSpaceSnapshot
  ): Vector[InformationEditorRecordProjection] =
    snapshot.items
      .filter(_.domain == profile.domain)
      .sortBy(_.id.print)
      .map { item =>
        InformationEditorRecordProjection(
          recordId = item.sourceRecordId,
          itemId = Some(item.id),
          domain = item.domain,
          state = item.state,
          title = _title(item.data),
          fields = profile.fields.map(field => _item_field_projection(field, item, snapshot)),
          publication = _publication(snapshot, item.id),
          actions = _item_actions(item)
        )
      }

  private def _record_field_projection(
    field: InformationFieldDescriptor,
    record: InformationImportRecord,
    snapshot: InformationSpaceSnapshot
  ): InformationEditorFieldProjection =
    InformationEditorFieldProjection(
      field,
      _value(record.workingData, field.fieldPath),
      snapshot.validationIssues.filter(x => x.recordId == record.id && x.fieldPath == field.fieldPath).sortBy(_.id.print),
      snapshot.resolutionCandidates.filter(x => x.recordId == record.id && x.fieldPath == field.fieldPath).sortBy(_.id.print),
      Vector.empty
    )

  private def _item_field_projection(
    field: InformationFieldDescriptor,
    item: InformationItem,
    snapshot: InformationSpaceSnapshot
  ): InformationEditorFieldProjection =
    InformationEditorFieldProjection(
      field,
      _value(item.data, field.fieldPath),
      Vector.empty,
      item.sourceRecordId.toVector.flatMap(recordid =>
        snapshot.resolutionCandidates.filter(x => x.recordId == recordid && x.fieldPath == field.fieldPath)
      ).sortBy(_.id.print),
      snapshot.conflicts.filter(x => x.itemId == item.id && x.fieldPath == field.fieldPath).sortBy(_.id.print)
    )

  private def _record_actions(record: InformationImportRecord): Vector[InformationEditorActionDescriptor] =
    Vector(
      _action("save", "Save", record.state != InformationLifecycleState.Published, None),
      _action("validate", "Validate", record.state != InformationLifecycleState.Published, None),
      _action("resolve", "Resolve", record.resolutionCandidateIds.nonEmpty && record.state == InformationLifecycleState.NeedsResolution, Some("available when unresolved candidates exist")),
      _action("confirm", "Confirm", record.state == InformationLifecycleState.ReadyForConfirmation || record.state == InformationLifecycleState.Confirmed, Some("requires valid and resolved record")),
      _action("reject", "Reject", record.state != InformationLifecycleState.Rejected && record.state != InformationLifecycleState.Published, None),
      _action("reopen", "Reopen", record.state == InformationLifecycleState.Rejected || record.state == InformationLifecycleState.Confirmed || record.state == InformationLifecycleState.Published, None),
      _action("publish", "Publish", record.state == InformationLifecycleState.Confirmed || record.state == InformationLifecycleState.Published, Some("requires confirmed item")),
      _action("materialize", "Materialize", record.state == InformationLifecycleState.Confirmed || record.state == InformationLifecycleState.Published, Some("requires confirmed item"))
    )

  private def _item_actions(item: InformationItem): Vector[InformationEditorActionDescriptor] =
    Vector(
      _action("save", "Save", item.state != InformationLifecycleState.Published, None),
      _action("validate", "Validate", false, Some("confirmed items are validated through their source records")),
      _action("resolve", "Resolve", item.state == InformationLifecycleState.Conflict, Some("available when conflicts need resolution")),
      _action("confirm", "Confirm", item.state == InformationLifecycleState.ReadyForConfirmation || item.state == InformationLifecycleState.Confirmed, None),
      _action("reject", "Reject", item.state != InformationLifecycleState.Rejected && item.state != InformationLifecycleState.Published, None),
      _action("reopen", "Reopen", item.state == InformationLifecycleState.Rejected || item.state == InformationLifecycleState.Confirmed || item.state == InformationLifecycleState.Published || item.state == InformationLifecycleState.Conflict, None),
      _action("publish", "Publish", item.state == InformationLifecycleState.Confirmed || item.state == InformationLifecycleState.Published, Some("requires confirmed information item")),
      _action("materialize", "Materialize", item.state == InformationLifecycleState.Confirmed || item.state == InformationLifecycleState.Published, Some("creates KnowledgeFrame / KnowledgeSpace projection"))
    )

  private def _action(
    name: String,
    label: String,
    enabled: Boolean,
    reason: Option[String]
  ): InformationEditorActionDescriptor =
    InformationEditorActionDescriptor(name, label, enabled, if (enabled) None else reason)

  private def _publication(
    snapshot: InformationSpaceSnapshot,
    itemid: InformationItemId
  ): Option[InformationPublicationStatus] =
    snapshot.publicationStatuses.find(_.itemId == itemid)

  private def _title(record: Record): Option[String] =
    _value(record, "title")

  private def _value(
    record: Record,
    fieldpath: String
  ): Option[String] =
    record.getString(fieldpath).map(_.trim).filter(_.nonEmpty)
}
