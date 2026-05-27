package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.tag.{Tag, TaggingWorkflow}
import org.goldenport.record.Record

/*
 * @since   May. 21, 2026
 * @version May. 27, 2026
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

final case class InformationTagProjection(
  tagSpace: String,
  key: String,
  path: String,
  title: Option[String],
  description: Option[String]
)

object InformationTagProjection {
  def from(tag: Tag): InformationTagProjection =
    InformationTagProjection(
      tag.tagSpace,
      tag.key,
      tag.path,
      tag.title,
      tag.description
    )
}

final case class InformationEditorFieldProjection(
  descriptor: InformationFieldDescriptor,
  value: Option[String],
  validationIssues: Vector[InformationValidationIssue],
  resolutionCandidates: Vector[InformationResolutionCandidate],
  conflicts: Vector[InformationConflict],
  status: Option[InformationFieldEvent] = None,
  events: Vector[InformationFieldEvent] = Vector.empty
)

final case class InformationEditorRecordProjection(
  informationId: InformationId,
  domain: String,
  state: InformationLifecycleState,
  title: Option[String],
  updatedAt: Instant,
  tags: Vector[InformationTagProjection] = Vector.empty,
  fields: Vector[InformationEditorFieldProjection],
  publication: Option[InformationPublicationStatus],
  actions: Vector[InformationEditorActionDescriptor]
) {
  def informationIdString: String =
    informationId.print
}

final case class InformationEditorProjection(
  componentName: String,
  domain: String,
  fields: Vector[InformationFieldDescriptor],
  information: Vector[InformationEditorRecordProjection]
)

final case class InformationEditorProfile(
  domain: String,
  fields: Vector[InformationFieldDescriptor]
)

object InformationEditorProfile {
  val BOOK_DOMAIN = "book"
  val PAPER_DOMAIN = "paper"
  val WEB_RESOURCE_DOMAIN = "web-resource"
  val PERSON_DOMAIN = "person"
  val ORGANIZATION_DOMAIN = "organization"
  val COMMON_NEIGHBORHOOD = "common-neighborhood"
  val BOOK_PROFILE_EXTENSION = "book-profile-extension"
  val PAPER_PROFILE_EXTENSION = "paper-profile-extension"
  val WEB_RESOURCE_PROFILE_EXTENSION = "web-resource-profile-extension"
  val PERSON_PROFILE_EXTENSION = "person-profile-extension"
  val ORGANIZATION_PROFILE_EXTENSION = "organization-profile-extension"

  def forDomain(domain: String): Option[InformationEditorProfile] =
    domain match {
      case BOOK_DOMAIN => Some(book)
      case PAPER_DOMAIN => Some(paper)
      case WEB_RESOURCE_DOMAIN => Some(webResource)
      case PERSON_DOMAIN => Some(person)
      case ORGANIZATION_DOMAIN => Some(organization)
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
          "People or organizations credited as authors. Preserve order when known. Resolved names become Person or Organization knowledge candidates around the book.",
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
          "People or organizations credited as editors. Resolved names become Person or Organization knowledge candidates around the book.",
          None,
          "optional",
          Some("Editor role is explicit relationship metadata."),
          resolverassisted = true,
          _mapping("relationship", "edited-by", BOOK_PROFILE_EXTENSION, "Canonical editor relationship.")
        ),
        _field(
          "publisher",
          "Publisher",
          "Publisher organization, imprint, or agent. Resolved names become Organization knowledge candidates around the book.",
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
          "description",
          "Description",
          "Imported or resolver-provided description retained for review before it becomes a confirmed summary.",
          Some("Imported source description."),
          "optional",
          Some("Review before copying into a curated summary."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "presentation.descriptions", COMMON_NEIGHBORHOOD, "Reviewable source description."),
          _mapping("evidence", "sources.evidenceIds", COMMON_NEIGHBORHOOD, "Resolver evidence for imported descriptions.")
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
        ),
        _field(
          "sourceUrl",
          "Source URL",
          "Source page or catalog URL used as evidence for imported book information.",
          Some("https://openlibrary.org/books/OL31838212M"),
          "recommended",
          Some("Store as a source/evidence reference, not as a CNCF id."),
          resolverassisted = true,
          _mapping("evidence", "sources.sourceRefs", COMMON_NEIGHBORHOOD, "Source reference for evidence and provenance."),
          _mapping("provenance", "origin.source", COMMON_NEIGHBORHOOD, "Origin metadata for imported information.")
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

  val person: InformationEditorProfile =
    InformationEditorProfile(
      PERSON_DOMAIN,
      Vector(
        _field(
          "name",
          "Name",
          "Canonical person name used for display, authority lookup, and relationship targets.",
          Some("Murasaki Shikibu"),
          "required",
          Some("Required before confirmation."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Display label for the Person node."),
          _mapping("relationship", "authority.same-person", PERSON_PROFILE_EXTENSION, "Authority correspondence candidate.")
        ),
        _field("sortName", "Sort name", "Name form used for sorting and authority matching.", Some("Shikibu, Murasaki"), "optional", None, true, _mapping("knowledge-node-section", "presentation.names", PERSON_PROFILE_EXTENSION, "Sortable or canonical name form.")),
        _field("localizedNames", "Localized names", "Language-tagged or script-specific person name variants.", Some("ja: 紫式部"), "optional", Some("Preserve language tags where known."), true, _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Localized labels.")),
        _field("roles", "Roles", "Book-related roles such as author, editor, translator, commentator, or contributor.", Some("author, translator"), "recommended", Some("Relationship role/order qualifiers are edited in KE-13."), false, _mapping("knowledge-node-section", "semantics.roles", PERSON_PROFILE_EXTENSION, "Person role hints for relationship materialization.")),
        _field("wikidataId", "Wikidata QID", "External authority id for the person.", Some("Q12345"), "optional", Some("External id only; do not use as a CNCF id."), true, _mapping("knowledge-node-section", "identity.externalIdentifiers", PERSON_PROFILE_EXTENSION, "External identifier.")),
        _field("dbpediaUri", "DBpedia URI", "DBpedia resource URI used as an RDF enrichment anchor for the person.", Some("http://dbpedia.org/resource/Murasaki_Shikibu"), "optional", Some("Treat as candidate until confirmed in InformationSpace."), true, _mapping("knowledge-node-section", "identity.externalIdentifiers", PERSON_PROFILE_EXTENSION, "External RDF anchor candidate."), _mapping("evidence", "sources.evidenceIds", COMMON_NEIGHBORHOOD, "Resolver evidence.")),
        _field("viafId", "VIAF ID", "VIAF authority identifier.", Some("123456"), "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", PERSON_PROFILE_EXTENSION, "External authority identifier.")),
        _field("isniId", "ISNI ID", "ISNI authority identifier.", Some("0000 0001 2345 6789"), "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", PERSON_PROFILE_EXTENSION, "External authority identifier.")),
        _field("orcidId", "ORCID ID", "ORCID researcher identifier.", Some("0000-0002-1825-0097"), "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", PERSON_PROFILE_EXTENSION, "External authority identifier.")),
        _field("lccn", "LCCN", "Library of Congress authority identifier.", None, "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", PERSON_PROFILE_EXTENSION, "External authority identifier.")),
        _field("ndl", "NDL authority ID", "National Diet Library authority identifier.", None, "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", PERSON_PROFILE_EXTENSION, "External authority identifier.")),
        _field("sourceUrl", "Source URL", "Source page or authority record URL used as evidence.", None, "recommended", None, true, _mapping("evidence", "sources.sourceRefs", COMMON_NEIGHBORHOOD, "Source reference.")),
        _field("reviewerNote", "Reviewer note", "Human editor note about this person authority candidate.", None, "optional", None, false, _mapping("provenance", "curation.note", COMMON_NEIGHBORHOOD, "Human curation note."))
      )
    )

  val organization: InformationEditorProfile =
    InformationEditorProfile(
      ORGANIZATION_DOMAIN,
      Vector(
        _field(
          "name",
          "Name",
          "Canonical organization name used for display, authority lookup, and relationship targets.",
          Some("Iwanami Shoten"),
          "required",
          Some("Required before confirmation."),
          resolverassisted = true,
          _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Display label for the Organization node."),
          _mapping("relationship", "authority.same-organization", ORGANIZATION_PROFILE_EXTENSION, "Authority correspondence candidate.")
        ),
        _field("localizedNames", "Localized names", "Language-tagged or script-specific organization name variants.", Some("ja: 岩波書店"), "optional", Some("Preserve language tags where known."), true, _mapping("knowledge-node-section", "presentation.labels", COMMON_NEIGHBORHOOD, "Localized labels.")),
        _field("organizationType", "Organization type", "Organization category such as publisher, imprint, institution, authority provider, or series owner.", Some("publisher"), "recommended", None, false, _mapping("knowledge-node-section", "semantics.roles", ORGANIZATION_PROFILE_EXTENSION, "Organization role hints.")),
        _field("wikidataId", "Wikidata QID", "External authority id for the organization.", Some("Q12345"), "optional", Some("External id only; do not use as a CNCF id."), true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External identifier.")),
        _field("dbpediaUri", "DBpedia URI", "DBpedia resource URI used as an RDF enrichment anchor for the organization.", Some("http://dbpedia.org/resource/Iwanami_Shoten"), "optional", Some("Treat as candidate until confirmed in InformationSpace."), true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External RDF anchor candidate.")),
        _field("viafId", "VIAF ID", "VIAF authority identifier.", None, "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External authority identifier.")),
        _field("isniId", "ISNI ID", "ISNI authority identifier.", None, "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External authority identifier.")),
        _field("rorId", "ROR ID", "Research Organization Registry identifier.", Some("https://ror.org/..."), "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External authority identifier.")),
        _field("lccn", "LCCN", "Library of Congress authority identifier.", None, "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External authority identifier.")),
        _field("ndl", "NDL authority ID", "National Diet Library authority identifier.", None, "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External authority identifier.")),
        _field("publisherId", "Publisher ID", "Publisher-local or catalog-specific organization identifier.", None, "optional", None, true, _mapping("knowledge-node-section", "identity.externalIdentifiers", ORGANIZATION_PROFILE_EXTENSION, "External source identifier.")),
        _field("sourceUrl", "Source URL", "Source page or authority record URL used as evidence.", None, "recommended", None, true, _mapping("evidence", "sources.sourceRefs", COMMON_NEIGHBORHOOD, "Source reference.")),
        _field("reviewerNote", "Reviewer note", "Human editor note about this organization authority candidate.", None, "optional", None, false, _mapping("provenance", "curation.note", COMMON_NEIGHBORHOOD, "Human curation note."))
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
  val InformationTagSpace: String = "information"
  val InformationTagRole: String = "information-tag"

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
          _information_projections(profile, snapshot, Map.empty)
        ))
      case None =>
        Consequence.argumentInvalid(s"information editor profile not found: $domain")
    }

  def componentWithTags(
    component: Component,
    domain: String
  )(using ExecutionContext): Consequence[InformationEditorProjection] =
    InformationEditorProfile.forDomain(domain) match {
      case Some(profile) =>
        val snapshot = component.informationSpace.snapshot
        _information_tags(snapshot).map { tags =>
          InformationEditorProjection(
            component.name,
            profile.domain,
            profile.fields,
            _information_projections(profile, snapshot, tags)
          )
        }
      case None =>
        Consequence.argumentInvalid(s"information editor profile not found: $domain")
    }

  def informationTagSourceIds(
    tagref: String,
    tagspace: String = InformationTagSpace
  )(using ExecutionContext): Consequence[Set[String]] =
    TaggingWorkflow(tagSpace = tagspace).searchSourceIds(tagref, includeDescendants = true, Some(InformationTagRole))

  def profileOption(domain: String): Option[InformationEditorProfile] =
    InformationEditorProfile.forDomain(domain)

  private def _information_tags(
    snapshot: InformationSpaceSnapshot
  )(using ExecutionContext): Consequence[Map[String, Vector[InformationTagProjection]]] =
    snapshot.information.foldLeft(Consequence.success(Map.empty[String, Vector[InformationTagProjection]])) { (z, information) =>
      z.flatMap { xs =>
        TaggingWorkflow(tagSpace = InformationTagSpace)
          .listEntityTags(information.id.print, Some(InformationTagRole))
          .map(summary => xs + (information.id.print -> summary.tags.map(InformationTagProjection.from)))
      }
    }

  private def _information_projections(
    profile: InformationEditorProfile,
    snapshot: InformationSpaceSnapshot,
    tags: Map[String, Vector[InformationTagProjection]]
  ): Vector[InformationEditorRecordProjection] =
    snapshot.information
      .filter(_.domain == profile.domain)
      .sortBy(_.updatedAt.toEpochMilli)
      .reverse
      .map { information =>
        InformationEditorRecordProjection(
          informationId = information.id,
          domain = information.domain,
          state = information.state,
          title = _title(information.workingData),
          updatedAt = information.updatedAt,
          tags = tags.getOrElse(information.id.print, Vector.empty),
          fields = profile.fields.map(field => _information_field_projection(field, information)),
          publication = information.publicationStatuses.headOption,
          actions = _information_actions(information)
        )
      }

  private def _information_field_projection(
    field: InformationFieldDescriptor,
    information: Information
  ): InformationEditorFieldProjection =
    InformationEditorFieldProjection(
      field,
      _value(information.workingData, field.fieldPath),
      information.validationIssues.filter(_.fieldPath == field.fieldPath),
      information.resolutionCandidates.filter(_.fieldPath == field.fieldPath).sortBy(_.candidateKey),
      information.conflicts.filter(_.fieldPath == field.fieldPath).sortBy(_.conflictKey),
      _field_events(information, field).headOption,
      _field_events(information, field)
    )

  private def _field_events(
    information: Information,
    field: InformationFieldDescriptor
  ): Vector[InformationFieldEvent] =
    information.fieldEvents.filter(_.fieldPath == field.fieldPath).sortBy(_.occurredAt.toEpochMilli).reverse

  private def _information_actions(information: Information): Vector[InformationEditorActionDescriptor] =
    Vector(
      _action("save", "Save", information.state != InformationLifecycleState.Published, None),
      _action("validate", "Validate", information.state != InformationLifecycleState.Published, None),
      _action("resolve", "Resolve", information.resolutionCandidates.nonEmpty && information.state == InformationLifecycleState.NeedsResolution, Some("available when unresolved candidates exist")),
      _action("confirm", "Confirm", information.state == InformationLifecycleState.ReadyForConfirmation || information.state == InformationLifecycleState.Confirmed, Some("requires valid and resolved information")),
      _action("reject", "Reject", information.state != InformationLifecycleState.Rejected && information.state != InformationLifecycleState.Published, None),
      _action("reopen", "Reopen", information.state == InformationLifecycleState.Rejected || information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published || information.state == InformationLifecycleState.Conflict, None),
      _action("publish", "Publish", information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published, Some("requires confirmed information")),
      _action("materialize", "Materialize", information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published, Some("creates KnowledgeFrame / KnowledgeSpace projection"))
    )

  private def _action(
    name: String,
    label: String,
    enabled: Boolean,
    reason: Option[String]
  ): InformationEditorActionDescriptor =
    InformationEditorActionDescriptor(name, label, enabled, if (enabled) None else reason)

  private def _title(record: Record): Option[String] =
    _value(record, "title").orElse(_value(record, "name"))

  private def _value(
    record: Record,
    fieldpath: String
  ): Option[String] =
    record.getString(fieldpath).map(_.trim).filter(_.nonEmpty)
}
