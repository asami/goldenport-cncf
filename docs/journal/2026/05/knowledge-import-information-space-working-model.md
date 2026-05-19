# Knowledge Import and InformationSpace Working Model

Date: 2026-05-19

This journal entry records the current working model for importing
`KnowledgeNode` / `KnowledgeRelationship` data, staging domain-specific
knowledge in RDB-backed structures, and materializing knowledge into
`KnowledgeSpace`.

This is not yet a normative runtime specification. It captures the current
design direction and the decisions that still need to be made.

## Architectural Review Summary

Review date: 2026-05-19

The current direction is internally coherent and should be preserved as an
operational knowledge management architecture, not only as RDF import tooling,
graph storage abstraction, AI retrieval wrapper, or semantic CRUD layer.

The most important separation is:

```text
InformationSpace
  = curated/editable authoritative information

KnowledgeSpace
  = operational semantic runtime
```

This separation avoids several common failure modes:

- RDF ORM collapse;
- graph DB facade architecture;
- giant semantic entity store;
- triple-centric runtime APIs;
- backend-driven runtime design.

The current architecture defines a full operational knowledge lifecycle:

```text
authoring
  -> validation
  -> resolution
  -> confirmation
  -> publication
  -> semantic materialization
  -> runtime traversal
```

This lifecycle should remain the core design frame.

### Confirmed Architectural Strengths

- Domain-specific authoring models are first-class. Data-entry users should not
  author RDF predicates, graph normalization, identity bindings, or CNCF
  runtime structures directly.
- Staging RDB is an Information curation runtime/workbench, not only a
  temporary import buffer.
- Resolution is central to import. Knowledge import is not just parse and
  store.
- Field/predicate-aware authority policy is necessary and should remain more
  precise than item-wide authority.
- Identity separation is correct. `KnowledgeNodeId` must not become RDF
  subject identity.
- Confirmation and publication must stay separate lifecycle stages.
- `textus-sie` should remain a backend/provider execution implementation
  behind CNCF SPI boundaries.
- KnowledgeSpace should remain non-editable. Editing belongs to
  InformationSpace.
- The capability model should preserve separate edit, resolve, confirm,
  publish, conflict, and audit capabilities.
- Builtin baseline domains (`web page`, `book`, `paper`) are useful because
  they stress different semantic/runtime concerns.

### Stability Rules

The following rules should be preserved during implementation:

- InformationSpace remains the editing/curation boundary.
- KnowledgeSpace remains the operational semantic runtime.
- Providers remain backend execution abstractions.
- RDF triples are publication artifacts, not runtime programming primitives.
- `KnowledgeNodeId` must never be treated as RDF identity.
- Publication remains separate from confirmation.
- Resolution remains central to import.
- Field-level authority policy remains supported.
- Provenance/evidence remains first-class.
- Backend/provider details must not leak into InformationSpace item models.
- Authoring formats remain domain-oriented rather than graph-oriented.

### Future Architectural Pressure Points

These are not current problems, but they are likely future design areas:

- InformationSpace item vs Entity/Aggregate/View relationship. Do not
  prematurely force InformationSpace items into Entity semantics.
- `KnowledgeFrame` may become central as a task-oriented semantic projection
  for AI context assembly, bounded semantic views, explanation generation, and
  runtime workflows.
- Conflict handling may eventually justify an independent `ConflictSpace` or
  equivalent abstraction if conflicts expand beyond RDB/RDF into RDF/RDF,
  authority, AI extraction, and temporal contradictions.
- Publication profiles are likely core architecture elements. Subject URI,
  predicate mapping, named graph, provenance, deletion, and retraction policies
  will shape long-term semantic stability.
- Embedding profiles should stay publication/profile-driven and should use
  generated semantic text rather than raw input.
- Knowledge engine SPI should keep publication, search, resolution, and
  embedding separate rather than collapsing them into one provider abstraction.

## Background

The initial import requirement was to support YAML, JSON, and XML input for
`KnowledgeNode` and `KnowledgeRelationship`.

Directly asking data-entry users to write a normalized internal
`KnowledgeNode` / `KnowledgeRelationship` format is too hard. It exposes too
much of the internal graph model and requires users to know RDF predicates,
identity resolution, relationship semantics, and CNCF knowledge model details.

The current direction is therefore:

```text
domain-specific authoring input
  -> staging RDB / InformationSpace
  -> validation, editing, resolution, confirmation
  -> RDF / vector publication
  -> RDF search
  -> KnowledgeSpace materialization
```

The normalized knowledge model remains the internal target. The user-facing
input format should be domain-specific and easier to author.

## Current Decisions

### Domain-Specific Authoring Formats

YAML, JSON, and XML remain valid serialization formats, but their structure
should be domain-oriented.

Examples:

- paper metadata input:
  - title
  - authors
  - publication venue
  - publication date
  - DOI / ISBN / external identifiers
  - abstract
  - keywords
  - citations
- museum collection input:
  - object title
  - creator / artist
  - collection id
  - material
  - period
  - provenance
  - location
  - classification
  - external authority ids

These inputs are not the canonical storage shape. They are authoring models
that normalize into internal knowledge structures.

### Resolution During Import

Import is not only parsing. It performs resolution and enrichment.

For example, a paper author list should be resolved against authority data or
RDF-backed entities. The result should link to the identified author node or
create a resolution candidate that a data-entry user can confirm.

Typical import stages:

```text
1. parse authoring input
2. validate required fields
3. normalize field names and values
4. resolve persons, organizations, venues, concepts, creators, and identifiers
5. create resolution candidates
6. let the data-entry user confirm or correct the resolution
7. publish confirmed data to RDF and vector indexes
```

The import process should create predicates / relationships only after the
input is structurally valid and resolution decisions are confirmed.

### Missing Information Handling

Incomplete records should be rejected at validation time.

The rejection must be actionable. It should identify:

- the failing field or path;
- the required information;
- the reason the record cannot be imported;
- possible resolution or editing actions when available.

This is especially important for domain-specific formats where the data-entry
user should not need to understand the normalized graph model.

### Staging RDB as an Import Workbench

The staging RDB is not a temporary buffer only. It is the workbench where
import records can be inspected, edited, resolved, confirmed, rejected, and
published.

Candidate staging responsibilities:

- keep the raw input record immutable;
- maintain an editable working record;
- store validation issues;
- store resolution candidates;
- store the selected candidate or manual resolution;
- track confirmation status;
- keep audit/revision history;
- track publish status to RDF and vector indexes.

Confirmation is an explicit state. RDF expansion and embedding should happen
after confirmation.

### InformationSpace and KnowledgeSpace

The current boundary is:

```text
InformationSpace = RDB-managed, curated, editable, authoritative information
KnowledgeSpace   = RDF/graph/semantic runtime space materialized for domain logic
```

`InformationSpace` owns confirmed RDB-managed information and staging/editing
state. It is authoritative when an RDB-managed item exists.

`KnowledgeSpace` owns the runtime knowledge model used by domain logic. It
contains `KnowledgeNode`, `KnowledgeRelationship`, evidence, provenance, and
frames materialized from RDF search results and enriched by InformationSpace
when possible.

This separates the curation/editing model from the operational semantic graph
model.

### RDB and RDF Authority Rules

The current source-of-truth policy is:

- If RDB-managed information exists, the RDB side is authoritative.
- If only RDF information exists, RDF is authoritative.
- If RDB and RDF conflict, the conflict is reported and both sides remain
  editable through their appropriate management surfaces.

In this model, RDF is often an index/projection for confirmed RDB-managed
information. It is not always the canonical source.

### Typed Domain Objects

Domain logic may need typed objects, not only generic graph nodes.

The working direction is:

- RDB-managed curated information lives in `InformationSpace` as typed domain
  information objects, such as paper information or museum object information.
- RDF search results are materialized into `KnowledgeSpace` as
  `KnowledgeNode` / `KnowledgeRelationship`.
- When RDF results identify a domain type, CNCF can restore a typed
  `KnowledgeNode` view such as paper or museum object node.
- If corresponding InformationSpace data exists, it enriches or overrides the
  RDF-derived node according to the authority policy.

This avoids forcing all authoring/staging concerns into `KnowledgeSpace`, while
still allowing domain logic to operate on typed knowledge after materialization.

## Provisional Data Flow

### Import Flow

```text
YAML / JSON / XML domain input
  -> domain authoring parser
  -> staging RDB raw record
  -> validation
  -> editable working record
  -> authority/RDF resolution candidates
  -> user confirmation
  -> InformationSpace confirmed item
  -> RDF triples
  -> vector embeddings
```

### Search and Materialization Flow

```text
RDF DB / vector search
  -> RDF subject and predicate result set
  -> KnowledgeNode / KnowledgeRelationship materialization
  -> InformationSpace lookup by identity mapping
  -> RDB-managed data overlays RDF-derived data when present
  -> KnowledgeFrame in KnowledgeSpace
  -> domain logic
```

### Conflict Flow

```text
RDF fact + InformationSpace item
  -> compare by mapped field / predicate
  -> conflict record if values disagree
  -> notify operator or data-entry workflow
  -> edit InformationSpace or RDF-side source
  -> re-publish / re-index as needed
```

## Working Vocabulary

### Authoring Model

A user-facing domain-specific input model. It should be easy for data-entry
users to create and review.

### Staging Record

An RDB row or aggregate that stores raw input, editable working data,
validation issues, resolution state, confirmation state, and publication state.

### InformationSpace

The RDB-managed space for curated and confirmed information. It is the source
of truth when an RDB-managed item exists.

### KnowledgeSpace

The runtime semantic space that contains materialized knowledge nodes,
relationships, evidence, provenance, and frames used by domain logic.

### Authority Resolution

The process that identifies whether an input value such as an author name,
creator, institution, venue, or concept corresponds to an existing RDF subject,
authority record, CNCF entity, or new item.

### Publication

The process that expands confirmed InformationSpace records into RDF triples
and vector embeddings.

## Open Decisions

This section is the discussion tracker for the current design work. It is not
an implementation checklist. Status values are intentionally lightweight:

- `decided`: current discussion has a working decision.
- `open`: direction exists, but the decision is not fixed.
- `planned`: not yet discussed enough to decide.
- `deferred`: intentionally left for later.

| ID | Topic | Status | Current Direction | Next Decision | Dependency |
| --- | --- | --- | --- | --- | --- |
| KI-01 | User-facing import format | decided | Domain-specific authoring formats normalize into internal knowledge structures. | Define first concrete paper and museum schemas. | none |
| KI-02 | Direct normalized import | decided | Normalized `KnowledgeNode` / `KnowledgeRelationship` import is internal-oriented, not the primary data-entry format. | Decide whether to keep an expert/admin normalized import path. | KI-01 |
| KI-03 | Staging RDB role | decided | Staging RDB is an editable import workbench, not only temporary storage. | Define staging record shape and state transitions. | KI-01 |
| KI-04 | Confirmation before publish | decided | RDF publication and embedding happen after user confirmation. | Define confirmation authority and audit requirements. | KI-03 |
| KI-05 | InformationSpace vs KnowledgeSpace | decided | InformationSpace is an independent Space for RDB-managed curated information; KnowledgeSpace owns runtime semantic materialization. | Define the minimal InformationSpace runtime API. | KI-03 |
| KI-06 | RDB/RDF authority rule | decided | RDB wins when managed information exists; RDF wins only when no RDB-managed information exists. | Decide whether this is item-wide or field-level. | KI-05 |
| KI-07 | Domain object type model | decided | Use domain-specific InformationSpace item types plus typed KnowledgeSpace materialized views. | Define first concrete paper and museum type shapes. | KI-05 |
| KI-08 | KnowledgeNode subtype policy | decided | Typed knowledge nodes are materialized views over KnowledgeNode data, enriched by InformationSpace when present. | Decide implementation shape: subtype, wrapper, or projection class. | KI-07 |
| KI-09 | Identity mapping | decided | Use explicit identity bindings across staging id, InformationSpace item id, RDF subject, external id, entity id, and `KnowledgeNodeId`. | Define concrete storage location and executable spec scope. | KI-05 |
| KI-10 | Field-level authority policy | decided | RDB authority is field/predicate-profile aware; item-wide RDB wins is only the default fallback. | Define authority profile syntax and default profile. | KI-06, KI-09 |
| KI-11 | Conflict model | open | RDB/RDF conflicts are field/predicate-profile conflicts surfaced through InformationSpace editing. | Define conflict schema, severity, status, and resolution actions. | KI-10 |
| KI-12 | Paper authoring schema | decided | Paper input uses a bibliographic authoring schema with title, authors, publication identity, venue/date, abstract, keywords, and citation/resolution hooks. | Define executable validation examples and first parser profile. | KI-01, KI-07 |
| KI-13 | Museum object authoring schema | open | Museum input should expose collection-friendly fields and resolver targets, not graph internals. | Finalize required fields and first YAML/JSON shape. | KI-01, KI-07 |
| KI-14 | Authority resolver SPI | decided | Import resolves domain values through a resolver registry into authority/RDF candidates with evidence, confidence, and manual confirmation state. | Define executable resolver specs and first mock resolvers. | KI-09 |
| KI-15 | RDF publication contract | open | Confirmed InformationSpace data publishes to RDF using a publication profile. | Define subject URI policy, predicate vocabulary, named graph policy, and provenance. | KI-04, KI-09, KI-10 |
| KI-16 | Embedding policy | open | Embedding runs after confirmation and uses publication/profile-generated text. | Decide text generation profile and versioning. | KI-15 |
| KI-17 | XML mapping policy | deferred | YAML/JSON are primary; XML support should be profile-driven only when a concrete source appears. | Decide profile-driven XPath vs domain XML schema when an XML source appears. | KI-12, KI-13 |
| KI-18 | InformationSpace editing API/UI | decided | InformationSpace exposes component operations first; UI is built on operation metadata later. | Define request/response shapes and first admin/data-entry page grouping. | KI-03, KI-05, KI-11 |
| KI-19 | InformationSpace capability model | decided | InformationSpace uses separate canonical capabilities for read/import/edit/validate/resolve/confirm/reject/publish/conflict/audit. | Define descriptor syntax and executable authorization specs. | KI-18 |
| KI-20 | First implementation slice | decided | Start with KnowledgeComponent foundation, InformationSpace skeleton, domain registry, Knowledge engine SPI, embedded provider, and a thin paper validation slice. | Open a phase/slice plan with executable spec scope. | KI-05, KI-12, KI-14, KI-21, KI-22 |
| KI-21 | Builtin baseline information domains | decided | CNCF builtin/reference domains should include web page, book, and paper; DBpedia-backed web page lookup is a baseline external knowledge backend use case. | Define minimal schemas and backend/provider boundaries for each baseline domain. | KI-05, KI-07, KI-14 |
| KI-22 | Knowledge engine SPI and textus-sie boundary | decided | CNCF owns InformationSpace lifecycle and defines Knowledge engine SPI; textus-sie is used through provider implementations for publication/search/resolution/embedding. | Define SPI request/result types and embedded provider behavior. | KI-05, KI-14, KI-15, KI-16 |

### Discussion Status Summary

Decided:

- domain-specific authoring formats are the primary data-entry interface;
- direct normalized `KnowledgeNode` / `KnowledgeRelationship` import is not
  the primary data-entry interface;
- staging RDB is an editable import workbench;
- confirmation happens before RDF publication and embedding;
- InformationSpace is an independent CNCF Space;
- RDB-managed information wins by default when it exists;
- domain-specific RDB-managed data belongs to InformationSpace item types;
- typed KnowledgeSpace nodes are materialized views over KnowledgeNode data;
- RDB/RDF authority should be field/predicate-profile aware;
- authority resolution produces candidates with confidence, evidence, and
  manual confirmation state.

Still open:

- identity binding storage location and executable spec scope;
- concrete conflict schema and resolution workflow;
- paper executable validation examples and parser profile;
- museum object authoring schema details;
- RDF publication profile;
- embedding text generation and versioning profile;
- InformationSpace request/response shapes and UI page grouping;
- InformationSpace descriptor syntax and authorization specs;
- phase/slice plan name and execution scope.
- minimal web page/book/paper schema and DBpedia provider boundary.
- Knowledge engine SPI request/result shapes and embedded provider behavior.

Deferred:

- XML support until a concrete XML source appears.

Recommended next discussion order:

1. Phase/slice planning for the first KnowledgeComponent foundation.
2. Executable spec scope for InformationSpace skeleton, domain registry, SPI,
   embedded provider, and paper thin vertical slice.
3. Follow-up design for conflict schema and RDF publication profile.

### InformationSpace Core Model

Decision: InformationSpace is an independent CNCF Space.

InformationSpace owns RDB-managed curated information and the editing workflow
around that information. It should provide operations for importing, editing,
validating, confirming, rejecting, publishing, and conflict review.

The storage implementation can still use Entity or SimpleEntity infrastructure
where appropriate, but that is an implementation detail. The conceptual and
runtime boundary is InformationSpace.

Remaining design work:

- minimal InformationSpace runtime API;
- staging and confirmed item lifecycle;
- edit operation model;
- authorization model for editing and confirmation;
- audit/revision model;
- integration point with RDF publication and KnowledgeSpace materialization.

### InformationSpace Editing Model

InformationSpace should provide the editing functions for RDB-managed
knowledge. Editing is not a KnowledgeSpace responsibility. KnowledgeSpace
receives materialized knowledge after import, confirmation, publication, or RDF
search.

The first InformationSpace editing model should cover both imported staging
records and confirmed curated information.

Candidate lifecycle states:

- `imported`: raw input has been registered.
- `invalid`: required information is missing or malformed.
- `needs_resolution`: authority/RDF matching has unresolved candidates.
- `ready_for_confirmation`: validation and resolution are sufficient for
  human confirmation.
- `confirmed`: data-entry or curator confirmation is complete.
- `published`: confirmed data has been expanded to RDF and embeddings.
- `rejected`: record is intentionally not imported.
- `conflict`: RDB-managed data and RDF-side data disagree.

Candidate operation groups:

- import:
  - register an import batch;
  - store immutable raw records;
  - create editable working records;
- validation:
  - validate a record;
  - list validation issues;
  - mark a record as invalid or ready for resolution;
- editing:
  - update a working record;
  - record revisions;
  - preserve the raw input separately from edited values;
- resolution:
  - list authority/RDF candidates;
  - select a candidate;
  - create a manual resolution;
  - request re-resolution;
- confirmation:
  - confirm a record;
  - reject a record;
  - reopen a confirmed record when policy allows it;
- publication:
  - publish confirmed information to RDF;
  - publish embeddings;
  - record publish result and version;
  - retry failed publication;
- conflict handling:
  - record RDB/RDF conflicts;
  - show conflicting field/predicate values;
  - select the authoritative side;
  - trigger re-publication or RDF-side correction.

The editing API should be authorized and audited. Editing, confirmation,
publication, and conflict resolution are different capabilities and should not
be collapsed into a single write permission without an explicit policy
decision.

Open API/UI questions:

- whether all editing operations are component operations or Space-native
  operations;
- how much of the editing workflow should be exposed through generic admin
  pages;
- whether data-entry users and curators have distinct roles/capabilities;
- whether publish is synchronous, job-backed, or event-backed;
- how to show RDF/RDB conflicts without forcing users to understand raw RDF.

### Minimal InformationSpace Runtime API

The first InformationSpace runtime API should be small and oriented around the
editing lifecycle. It should not expose RDF-specific implementation details as
the primary programming model.

Candidate runtime capabilities:

```text
InformationSpace
  registerImportBatch(input)
  getImportBatch(batchId)
  listImportRecords(batchId, filter)

  getInformationItem(itemId)
  searchInformationItems(query)

  validateInformationItem(itemId)
  updateInformationItem(itemId, patch)
  listValidationIssues(itemId)

  listResolutionCandidates(itemId, fieldPath)
  selectResolutionCandidate(itemId, fieldPath, candidateId)
  setManualResolution(itemId, fieldPath, value)
  requestResolution(itemId)

  confirmInformationItem(itemId)
  rejectInformationItem(itemId, reason)
  reopenInformationItem(itemId)

  publishInformationItem(itemId)
  getPublicationStatus(itemId)

  listConflicts(filter)
  getConflict(conflictId)
  resolveConflict(conflictId, decision)
```

The names above are discussion placeholders. The important boundary is that
InformationSpace owns curated information editing and lifecycle transitions.
RDF publication and KnowledgeSpace materialization are downstream effects.

Runtime API design constraints:

- raw input and edited working data must remain distinguishable;
- validation must be repeatable after edits;
- resolution decisions must be auditable;
- confirmation must be explicit;
- publication must record version/result information;
- conflict resolution must not silently overwrite either RDB or RDF data;
- KnowledgeSpace should consume materialized information, not own editing
  state.

### InformationSpace Capability Model

Decision: InformationSpace editing is protected by separate canonical
capabilities. It should not be represented as one broad write permission by
default.

Canonical capability groups:

| Capability | Purpose |
| --- | --- |
| `information:read` | read staged and confirmed information visible to the subject |
| `information:import` | register import batches and raw records |
| `information:edit` | edit working records before or after confirmation, subject to policy |
| `information:validate` | run or inspect validation results |
| `information:resolve` | select authority/RDF resolution candidates or set manual resolutions |
| `information:confirm` | confirm curated information for publication |
| `information:reject` | reject an import/staging record |
| `information:publish` | publish confirmed information to RDF/vector indexes |
| `information:conflict:read` | inspect RDB/RDF conflicts |
| `information:conflict:resolve` | choose an authoritative side or resolution action |
| `information:audit:read` | inspect revision/audit history |

Domain-specific scoping:

The canonical capability name is global, but it should be scopeable by
InformationSpace domain or item type through policy/resource metadata.

Examples:

```text
information:edit
  resource: paper

information:confirm
  resource: museum_object
```

This avoids inventing many capability names such as
`paper_information:edit`, while still allowing authorization policies to grant
different rights by domain.

Potential role bundles:

- data-entry user:
  - `information:read`
  - `information:import`
  - `information:edit`
  - `information:validate`
- curator:
  - data-entry capabilities
  - `information:resolve`
  - `information:confirm`
  - `information:reject`
- publisher/operator:
  - `information:publish`
  - `information:conflict:read`
- knowledge administrator:
  - all InformationSpace capabilities
  - `information:audit:read`
  - `information:conflict:resolve`

Default role bundle direction:

| Role | Capabilities |
| --- | --- |
| `information_data_entry` | `information:read`, `information:import`, `information:edit`, `information:validate` |
| `information_curator` | data-entry capabilities plus `information:resolve`, `information:confirm`, `information:reject`, `information:conflict:read` |
| `information_publisher` | `information:read`, `information:publish`, `information:conflict:read` |
| `information_admin` | all InformationSpace capabilities |

Separation-of-duty direction:

- confirmation may require a different subject from the last editor when a
  domain policy enables that rule;
- publication should be separate from confirmation by default;
- conflict resolution should require explicit capability, not just edit;
- audit read should be explicit because it may expose historical rejected or
  sensitive data.

Open authorization questions:

- whether confirmation requires a different subject from the last editor;
- whether publication is an operator-only capability;
- how InformationSpace capabilities compose with existing entity read/write
  permissions if SimpleEntity is used as a storage implementation detail.

Current direction for those questions:

- capability names are global, resource scope is policy-driven;
- same-editor confirmation is allowed by default, but policy can require
  separation of duty;
- publication is a separate capability and can be operator-only by deployment
  policy;
- if SimpleEntity is used internally, InformationSpace capabilities are the
  public authorization boundary and SimpleEntity permissions are storage-layer
  implementation detail unless explicitly surfaced.

### Domain Object Type Model

Decision: domain-specific RDB-managed data belongs to InformationSpace as
typed information objects. KnowledgeSpace may expose typed materialized
knowledge views for domain logic.

The working split is:

- `PaperInformation`, `MuseumObjectInformation`, and similar types are
  InformationSpace item types.
- `PaperKnowledgeNode`, `MuseumObjectKnowledgeNode`, and similar types are
  KnowledgeSpace materialized views or typed node projections.
- InformationSpace is authoritative for editable curated data when an item
  exists.
- KnowledgeSpace is authoritative for runtime graph traversal and semantic
  materialization.

This is intentionally not a pure subclass-only model. A paper may appear in
InformationSpace, RDF, or both. The typed KnowledgeSpace node must be able to
represent:

- RDB-managed paper information with RDF/index projection;
- RDF-only paper knowledge with no RDB-managed record;
- mixed records where InformationSpace overrides or enriches RDF-derived data;
- conflict records where RDB and RDF disagree.

Candidate mapping:

```text
PaperInformation
  -> InformationSpace item
  -> RDF triples / vector embeddings after confirmation
  -> RDF search result
  -> KnowledgeNode + KnowledgeRelationship materialization
  -> PaperKnowledgeNode typed view for domain logic
```

For RDF-only records:

```text
RDF subject typed as paper
  -> KnowledgeNode + KnowledgeRelationship materialization
  -> PaperKnowledgeNode typed view
  -> no InformationSpace item unless later imported/curated
```

Implementation options for typed KnowledgeSpace views:

- real `KnowledgeNode` subclasses;
- wrapper classes around a base `KnowledgeNode`;
- projection classes built from `KnowledgeNode`, relationships, and
  InformationSpace overlay data.

The decision is conceptual for now: InformationSpace owns editable typed
information; KnowledgeSpace owns runtime typed knowledge views.

### Identity Mapping

Decision: identity mapping must be explicit and must not rely on parsing or
equating IDs from different spaces.

Identity mapping connects:

- staging record id;
- InformationSpace item id;
- RDF subject URI or blank node;
- external authority id;
- CNCF entity id, when applicable;
- `KnowledgeNodeId`.

`KnowledgeNodeId` should remain CNCF-local and must not be treated as RDF
subject identity.

The mapping should be explicit because the same real-world object can have
multiple identifiers across systems.

Candidate identity mapping record:

```text
KnowledgeIdentityBinding
  bindingId
  informationSpaceId
  informationItemId
  informationItemType
  rdfSubject
  rdfGraph
  externalIdentifiers
  entityBindings
  knowledgeNodeId
  authority
  confidence
  status
  provenance
```

Important rules:

- `KnowledgeNodeId` is a runtime/materialization id inside KnowledgeSpace.
- RDF subject URI is RDF identity/content, not the CNCF runtime id.
- InformationSpace item id is the RDB-managed curated item id.
- external identifiers belong in structured bindings, not in parsed ids.
- entity bindings are optional and should not force Entity id to equal RDF
  subject or KnowledgeNodeId.
- one InformationSpace item may produce or enrich more than one
  KnowledgeNode when a materialization profile requires it.
- one KnowledgeNode may aggregate RDF statements from multiple RDF subjects
  only if a mapping/profile explicitly accepts that merge.

Open identity questions:

- whether identity bindings are stored directly in InformationSpace or in a
  shared identity registry;
- whether RDF subject generation is stable from InformationSpace item id or
  external authority id;
- how to represent unconfirmed candidate mappings;
- how to version identity mappings when curation decisions change.

### Identity Binding Lifecycle

Identity bindings should have their own lifecycle because candidate resolution
and confirmed identity are different states.

Candidate binding statuses:

- `candidate`: resolver produced a possible mapping.
- `selected`: a user or policy selected the mapping for this record.
- `confirmed`: the mapping is accepted as the current identity binding.
- `rejected`: the candidate was reviewed and rejected.
- `superseded`: the mapping was replaced by a newer confirmed mapping.
- `conflict`: multiple sources disagree about the identity mapping.

Lifecycle rules:

- import may create candidate bindings;
- exact trusted external id match may auto-select a binding by policy;
- name-only or low-confidence matches should remain candidates;
- confirmation should promote selected bindings to confirmed bindings;
- publication should use confirmed bindings only;
- rejected bindings remain available for audit and resolver feedback;
- superseded bindings preserve history for re-publication and conflict review.

### Identity Binding Lookup

Lookup should support both curation and materialization.

Required lookup directions:

- InformationSpace item -> confirmed RDF subject;
- RDF subject -> InformationSpace item;
- external identifier -> InformationSpace item candidate or confirmed item;
- Entity binding -> InformationSpace item;
- KnowledgeNodeId -> source RDF subject and InformationSpace overlay, when
  materialized;
- staging record -> candidate and selected bindings.

`KnowledgeNodeId` lookup is materialization-scoped. It should not become the
primary persistent identity key for RDB or RDF data.

### Identity Binding Storage Direction

The current direction is to store confirmed and candidate bindings with
InformationSpace, because InformationSpace owns curation, confirmation, and
audit. KnowledgeSpace can cache or project binding information into
materialized frames, but it should not own the authoritative identity decision.

A later shared identity registry may be useful if multiple Spaces need to
coordinate identity decisions, but it should not be introduced before the first
InformationSpace use case proves the need.

### RDF Subject Policy Direction

RDF subject generation should be stable and profile-driven.

Candidate priority:

1. trusted external authority URI, when the authority profile accepts it;
2. CNCF-controlled URI derived from InformationSpace item id;
3. source-local URI only when no curated InformationSpace item exists;
4. blank node only for RDF-only data that has no stable subject policy.

Rules:

- do not use `KnowledgeNodeId` as RDF subject;
- do not use staging record id as final RDF subject;
- preserve previous RDF subject when republishing the same confirmed item;
- changing RDF subject is an explicit identity migration, not a normal edit.

### Field-Level Authority Policy

Decision: RDB authority should be field/predicate-profile aware.

The earlier rule "RDB wins when managed information exists" remains the
default fallback, but it should not prevent more precise authority decisions.
Some fields may be curated in RDB, while other predicates may remain RDF-owned
or authority-source-owned.

The authority decision should be made by an authority profile:

```text
InformationAuthorityProfile
  informationItemType
  defaultAuthority
  fieldRules
  predicateRules
  sourceRules
  conflictPolicy
```

Candidate authority modes:

- `rdb`: InformationSpace value is authoritative.
- `rdf`: RDF-side value is authoritative.
- `external_authority`: an external authority source is authoritative.
- `manual`: curator must select the authoritative value.
- `merge`: values can be combined under a domain-specific rule.

Candidate rule examples:

```text
PaperInformation.title                 -> rdb
PaperInformation.authors               -> rdb with authority resolution
PaperInformation.doi                   -> external_authority
PaperInformation.citations             -> rdf or merge
MuseumObjectInformation.collectionId   -> rdb
MuseumObjectInformation.creator        -> rdb with authority resolution
MuseumObjectInformation.location       -> rdb
MuseumObjectInformation.sameAs         -> rdf
```

This allows InformationSpace to be authoritative without incorrectly treating
every RDF statement as stale whenever an RDB item exists.

Operational rules:

- If a field has an explicit RDB authority rule, InformationSpace value wins.
- If a predicate has an RDF authority rule, RDF value wins unless explicitly
  overridden.
- If a field maps to an external authority, resolver output must be preserved
  with evidence.
- If the profile says `manual`, the item cannot be fully confirmed/published
  until a curator resolves it.
- If no field or predicate rule exists, item-wide RDB authority is the fallback
  when an InformationSpace item exists.

Open authority questions:

- exact profile syntax;
- where profiles are stored;
- whether profiles are component-specific, InformationSpace-specific, or
  domain-specific;
- whether profile changes trigger conflict re-evaluation;
- how authority decisions appear in admin/manual projections.

### Conflict Model

RDB/RDF conflicts should be recorded as InformationSpace-managed conflicts.
They are not KnowledgeSpace editing state, although KnowledgeSpace may expose
conflict markers in materialized views.

Candidate conflict record:

```text
InformationConflict
  conflictId
  informationSpaceId
  informationItemId
  informationItemType
  fieldPath
  rdfSubject
  rdfPredicate
  rdfGraph
  rdbValue
  rdfValue
  authorityRule
  severity
  status
  resolutionDecision
  detectedAt
  resolvedAt
  provenance
```

Candidate conflict statuses:

- `open`: conflict is detected and unresolved.
- `needs_review`: conflict requires curator review.
- `rdb_selected`: RDB value was selected.
- `rdf_selected`: RDF value was selected.
- `external_selected`: external authority value was selected.
- `merged`: values were merged.
- `ignored`: conflict is accepted as non-blocking.
- `resolved`: conflict is complete and no longer blocks publication.

Candidate severity levels:

- `blocking`: confirmation or publication must stop.
- `warning`: publication can proceed, but the conflict is visible.
- `informational`: recorded for traceability only.

Resolution actions:

- select RDB value;
- select RDF value;
- select external authority value;
- edit RDB working record;
- request RDF-side correction;
- merge values;
- ignore with reason.

Conflict handling should preserve both values and evidence. It should not
silently mutate either side. If a resolution action changes RDB data, that must
go through InformationSpace editing and audit. If it changes RDF data, that
must go through the RDF publication/correction path.

### Domain-Specific Required Fields

Define minimum valid authoring schemas for the first target domains.

Candidate first schemas:

- paper;
- museum collection object.

Each schema should define required fields, optional fields, validation rules,
and resolution targets.

### Paper Authoring Schema

Decision: the first paper authoring schema is a bibliographic schema designed
for data-entry users and import maintainers. It is not a graph authoring
format.

The paper authoring schema should be comfortable for bibliographic data-entry
work. It should not require users to write RDF predicates or normalized
KnowledgeRelationship rows.

Candidate YAML shape:

```yaml
kind: paper
schemaVersion: 1
title: "Example Paper Title"
authors:
  - name: "Ada Lovelace"
    role: author
    order: 1
    affiliation: "Example University"
    identifiers:
      orcid: "0000-0000-0000-0000"
venue:
  name: "Example Journal"
  type: journal
published:
  year: 2026
  date: "2026-05-19"
identifiers:
  doi: "10.0000/example"
abstract: "..."
language: en
keywords:
  - knowledge graph
  - semantic search
citations:
  - doi: "10.0000/reference"
sources:
  - url: "https://example.org/paper"
```

Minimum required fields:

- `title`;
- at least one author;
- at least one publication identity field:
  - DOI;
  - stable external id;
  - or venue + year + title fallback accepted by profile.

Candidate optional fields:

- abstract;
- keywords;
- venue details;
- affiliation;
- citation list;
- language;
- license;
- source URL;
- PDF or source document reference.

Required validation rules:

- `kind` must be `paper`;
- `schemaVersion` must be supported;
- `title` must be non-empty;
- `authors` must contain at least one entry;
- each author must have `name` or a trusted external identifier;
- author `order`, when present, must be positive and unique;
- DOI, when present, must be syntactically valid enough for resolver lookup;
- `published.year`, when present, must be valid;
- if DOI/stable external id is absent, `venue.name` and `published.year` are
  required by the fallback identity profile.

Resolution targets:

- authors -> person authority / RDF subject;
- affiliations -> organization authority / RDF subject;
- venue -> publication venue authority / RDF subject;
- DOI / ISBN / external identifiers -> external authority;
- keywords -> concept/classification nodes;
- citations -> paper identity bindings.

Resolution policy:

- DOI/external identifier exact matches can be auto-selected when the resolver
  source is trusted.
- Author name-only matches require confirmation.
- Venue name-only matches require confirmation unless a trusted venue id is
  present.
- Keyword/concept matches can remain optional unless the import profile marks
  them required.
- Citation resolution should not block the first paper import slice unless the
  citation is explicitly required by the profile.

Validation issues should be path-oriented:

```text
authors[0].name missing
identifiers.doi invalid
venue.name missing when no DOI exists
published.year invalid
```

Candidate `PaperInformation` fields:

```text
PaperInformation
  id
  title
  authors
  venue
  published
  identifiers
  abstract
  language
  keywords
  citations
  sources
  resolutionState
  confirmationState
  publicationState
```

Candidate RDF publication mapping:

- paper item -> RDF subject;
- title -> display/name/title predicate selected by publication profile;
- authors -> ordered author relationships;
- venue -> publication venue relationship;
- DOI/external ids -> identity bindings;
- keywords -> concept/classification relationships;
- citations -> citation relationships;
- source URL/PDF reference -> source/evidence relationship.

Open paper-schema questions:

- whether `authors` require ordered authorship;
- how to model corresponding author;
- whether citation resolution is required before confirmation;
- how to handle preprints and later published versions;
- whether PDF/source document ingestion belongs in this schema or another
  content import flow.

### Builtin Baseline Information Domains

Decision: CNCF should include a small builtin/reference domain set that is
useful enough to validate InformationSpace, KnowledgeSpace, resolver, RDF, and
embedding behavior without requiring an external domain component.

Baseline domains:

- web page;
- book;
- paper.

These are builtin/reference domains, not a claim that CNCF owns every detailed
ontology for those domains. Domain components can extend or replace the
profiles later.

This baseline set is intentionally balanced:

```text
web page = web/source/document knowledge
book     = bibliographic/catalog knowledge
paper    = research/citation knowledge
```

Each domain stresses a different part of the InformationSpace / KnowledgeSpace
model:

| Domain | Identity axis | Relationship axis | External authority axis | Embedding axis |
| --- | --- | --- | --- | --- |
| web page | URL / canonical URL | topic / author / source | DBpedia / URL canonicalization | title + description + body summary |
| book | ISBN / edition | author / publisher / edition / subject | ISBN / library authority | title + description + subjects |
| paper | DOI / venue / title / year | author / venue / citation / keyword | DOI / ORCID / venue | title + abstract + keywords |

This makes the set suitable for CNCF builtin/reference coverage. It is broad
enough to validate identity binding, resolver candidates, RDF publication,
embedding text generation, source/evidence, and KnowledgeSpace materialization
without introducing a highly specialized domain first.

Important boundary notes:

- web page support should not imply crawling or full content extraction in the
  first slice. The baseline should start with URL metadata, canonical URL,
  RDF/DBpedia authority binding, and source references.
- book support should avoid a full FRBR/work-expression-manifestation model in
  the first slice. Start with a simple `BookInformation` and leave detailed
  edition modeling for later.
- paper support should not attempt full author disambiguation or citation
  graph management immediately. Start with DOI, author candidates, venue, and
  compact citation hooks.

Implementation driver:

Paper remains the first implementation driver. Web page and book are baseline
reference domains to keep the design honest, but their full implementation can
follow after the InformationSpace skeleton and paper import path are stable.

#### Web Page Information

Web page information is required because many knowledge workflows start from
URLs and web documents. It also gives CNCF a concrete bridge from external RDF
knowledge such as DBpedia into InformationSpace / KnowledgeSpace.

Candidate `WebPageInformation` fields:

```text
WebPageInformation
  id
  url
  canonicalUrl
  title
  description
  language
  siteName
  published
  modified
  authors
  topics
  externalIdentifiers
  sources
  resolutionState
  confirmationState
  publicationState
```

Minimum required fields:

- `url`;
- `title` or accepted title-missing policy;
- source/crawl/import context.

Resolver/backend targets:

- URL -> canonical URL;
- page title/topic -> DBpedia/RDF subject candidates;
- authors -> person/organization resolver;
- site/domain -> organization/source resolver.

DBpedia backend positioning:

- CNCF owns the abstract resolver/search/publication boundary and
  `WebPageInformation` model.
- DBpedia access should be a provider/backend behind the resolver or semantic
  retrieval SPI.
- CNCF builtin can include a DBpedia-backed profile only if it remains
  provider-abstract or optional.
- Provider-specific DBpedia client details should not leak into
  InformationSpace item shape.

#### Book Information

Book information is a baseline bibliographic domain distinct from paper.

Candidate `BookInformation` fields:

```text
BookInformation
  id
  title
  subtitle
  authors
  editors
  publisher
  published
  identifiers
  language
  description
  subjects
  editions
  sources
  resolutionState
  confirmationState
  publicationState
```

Minimum required fields:

- `title`;
- at least one author/editor or accepted anonymous/unknown policy;
- at least one identity field:
  - ISBN;
  - stable external id;
  - or title + author/editor + publisher/year fallback accepted by profile.

Resolver targets:

- ISBN/external ids -> external authority;
- authors/editors -> person authority;
- publisher -> organization authority;
- subjects -> concept/classification nodes;
- editions -> book identity bindings or related book items.

#### Paper Information

Paper remains the first implementation driver because it exercises author
resolution, DOI/external id matching, venue resolution, citation hooks, RDF
publication, and embedding text generation in a compact domain.

First implementation scope remains paper-focused. Web page and book should be
registered as builtin/reference baseline domains after the InformationSpace
skeleton is stable, unless implementation planning decides to include their
minimal schemas in the same phase.

### Museum Object Authoring Schema

The museum object authoring schema should match collection-management
language. It should not require data-entry users to author RDF graph edges
directly.

Candidate YAML shape:

```yaml
kind: museum_object
collectionId: "OBJ-0001"
title: "Example Object"
creator:
  name: "Example Artist"
  identifiers:
    ulan: "500000000"
classification:
  category: painting
  concepts:
    - portrait
material:
  - oil
  - canvas
period:
  label: "Edo period"
dates:
  created: "1800"
location:
  institution: "Example Museum"
  gallery: "Gallery 1"
provenance:
  - description: "Acquired from Example Collection"
identifiers:
  local: "OBJ-0001"
  wikidata: "Q000000"
description: "..."
```

Candidate required fields:

- `collectionId` or equivalent local inventory id;
- `title` or accepted untitled policy;
- at least one classification/category;
- holding institution or collection context.

Candidate optional fields:

- creator/artist;
- material/technique;
- period/date;
- dimensions;
- location;
- provenance;
- acquisition details;
- images/media references;
- external authority identifiers.

Resolution targets:

- creator -> person/artist authority / RDF subject;
- institution -> organization authority / RDF subject;
- classification concepts -> concept authority / RDF subject;
- material and technique -> controlled vocabulary;
- period -> time period authority;
- location -> place or collection-location authority;
- external ids -> authority bindings.

Validation issues should be path-oriented:

```text
collectionId missing
classification.category missing
creator.name unresolved and no manual resolution selected
location.institution missing
```

Open museum-schema questions:

- how to represent unknown creator without rejecting the record;
- whether location is current location, owning institution, or both;
- whether images/media references should use Blob attachment workflow;
- how to represent dimensions and measurements;
- whether provenance entries need structured event types from the first slice.

### Authority Resolver SPI

Decision: import should resolve domain values into authority/RDF candidates.
The resolver does not directly publish RDF. It returns candidates and
explanations that InformationSpace can present to data-entry users or curators.

Resolver targets:

- person/author;
- organization;
- publication venue;
- museum creator;
- location;
- concept/classification;
- external identifier.

Resolvers should return candidates with confidence, evidence, and required
manual confirmation state.

Candidate resolver SPI:

```text
AuthorityResolver
  targetType
  supportedDomains
  supportedItemTypes
  resolve(request): ResolutionResult

ResolutionRequest
  requestId
  fieldPath
  rawValue
  normalizedValue
  domain
  itemType
  itemId
  context
  knownIdentifiers
  locale

ResolutionResult
  status
  candidates
  issues
  resolverMetadata

ResolutionCandidate
  candidateId
  label
  targetType
  rdfSubject
  externalIdentifiers
  confidence
  evidence
  matchExplanation
  requiresConfirmation
```

Candidate resolver registry:

```text
AuthorityResolverRegistry
  register(resolver)
  find(targetType, domain, itemType)
  resolve(request)
```

Resolution should be profile-driven. The authoring schema or
InformationAuthorityProfile should decide which resolver target is used for
each field path.

Example field-to-resolver mapping:

```text
paper.authors[].name             -> person
paper.authors[].affiliation      -> organization
paper.venue.name                 -> publication_venue
paper.keywords[]                 -> concept
paper.identifiers.doi            -> external_identifier
museum_object.creator.name       -> person_or_artist
museum_object.location.institution -> organization
museum_object.classification.category -> concept
```

Candidate result statuses:

- `resolved`: one accepted candidate can be selected automatically by policy.
- `candidates`: multiple candidates require user/curator selection.
- `not_found`: no candidate was found.
- `ambiguous`: candidates exist but confidence is insufficient.
- `invalid`: input value cannot be resolved because it is malformed.
- `deferred`: resolution is intentionally postponed.

Resolution policy should decide whether a candidate can be auto-selected or
must be confirmed manually.

Auto-selection should be conservative:

- exact external identifier match can be auto-selected if the authority source
  is trusted;
- name-only match should normally require confirmation;
- low-confidence matches should remain candidates;
- unresolved required fields block confirmation;
- unresolved optional fields may be allowed with warnings.

Open resolver questions:

- confidence model;
- evidence shape;
- resolver timeout and failure handling;
- whether resolver calls are synchronous, job-backed, or cached;
- how to expose candidate explanations in UI/API.

### Resolver Issue Handling

Resolver failures should not be hidden as generic import errors. They should
be attached to the field path and surfaced through InformationSpace validation
or resolution state.

Candidate resolver issue shape:

```text
ResolutionIssue
  fieldPath
  targetType
  severity
  code
  message
  evidence
```

Candidate issue severities:

- `blocking`: confirmation cannot proceed.
- `warning`: confirmation can proceed, but the issue remains visible.
- `informational`: trace-only issue.

Candidate issue examples:

```text
authors[0].name unresolved
authors[1].name ambiguous
identifiers.doi malformed
venue.name resolver_unavailable
keywords[2] no_candidate
```

Resolver unavailability should be distinguishable from a true no-match result.
This matters operationally: a temporary resolver outage should not be treated
as evidence that the entity does not exist.

### Resolver Confirmation Policy

Resolver output should carry a policy decision:

```text
ResolutionDecision
  autoSelectable
  requiresConfirmation
  reason
```

Auto-selection is allowed only when the profile trusts the resolver result.
The first implementation should keep this conservative.

Default confirmation rules:

- exact trusted external identifier match: auto-select allowed;
- exact name match without external id: confirmation required;
- multiple candidates: confirmation required;
- no candidate for required field: blocking issue;
- no candidate for optional field: warning or informational issue;
- resolver unavailable: warning or blocking based on field requirement.

### Resolver Caching and Jobs

The first implementation can run resolvers synchronously for small records, but
the model should not require synchronous execution.

Resolver execution modes:

- synchronous for local/in-memory/mock resolvers;
- cached for authority lookups that are stable;
- job-backed for slow external resolvers or batch imports.

Resolution state should be stored with the InformationSpace item so that UI
and operators can inspect candidates without re-running resolvers.

### RDF Publication Contract

Confirmed InformationSpace records publish to RDF through a publication
profile. Publication should be deterministic and versioned.

Publication is not the same as confirmation:

```text
confirmation = curator accepts InformationSpace data
publication  = confirmed data is expanded to RDF and vector indexes
```

Candidate publication profile:

```text
InformationPublicationProfile
  informationItemType
  subjectPolicy
  predicateMappings
  namedGraphPolicy
  provenancePolicy
  deletionPolicy
  conflictPolicy
  embeddingProfile
```

Define how InformationSpace records become RDF:

- subject URI policy;
- predicate vocabulary;
- named graph policy;
- provenance statements;
- re-publication behavior;
- deletion / tombstone behavior.

Candidate subject URI policy:

- use external authority URI when one is selected and trusted;
- otherwise mint CNCF-controlled URI from InformationSpace item id;
- keep generated URI stable across re-publication;
- do not use `KnowledgeNodeId` as RDF subject.

Candidate named graph policy:

- one graph per InformationSpace;
- one graph per domain/item type;
- one graph per publication batch;
- or one graph per source/provider.

Candidate publication result:

```text
PublicationResult
  publicationId
  informationItemId
  publicationVersion
  rdfSubject
  namedGraph
  tripleCount
  embeddingCount
  status
  issues
  publishedAt
```

Open RDF publication questions:

- first predicate vocabulary for paper and museum object;
- publication transaction semantics;
- how to retract or tombstone old triples;
- whether conflict warnings block publication by default;
- whether publication should be a Job by default;
- how publication results appear in InformationSpace admin pages.

### Knowledge Engine SPI and textus-sie Boundary

Decision: CNCF should define the Knowledge engine SPI. `textus-sie` should be
used through SPI implementations, especially for backend publication/registration
into RDF/vector/search providers.

Terminology:

```text
register = store/import data into CNCF InformationSpace
publish  = reflect confirmed InformationSpace data into a knowledge backend
```

These terms should not be collapsed. InformationSpace registration is a CNCF
lifecycle operation. Backend publication is provider execution.

Recommended split:

```text
CNCF InformationSpace
  register import batch
  validate
  resolve
  confirm
  generate publication request
  record publication result

Knowledge engine provider
  publish triples / embeddings / indexes
  search RDF/vector/hybrid backends
  perform authority lookup
  return backend diagnostics
```

Candidate SPI set:

```text
KnowledgePublicationProvider
  publish(request): KnowledgePublicationResult
  retract(request): KnowledgeRetractionResult

KnowledgeSearchProvider
  search(request): KnowledgeSearchResult

KnowledgeResolutionProvider
  resolve(request): KnowledgeResolutionResult

KnowledgeEmbeddingProvider
  embed(request): KnowledgeEmbeddingResult
```

`textus-sie` may implement these together:

```text
TextusSieKnowledgeProvider
  extends KnowledgePublicationProvider
  extends KnowledgeSearchProvider
  extends KnowledgeResolutionProvider
  extends KnowledgeEmbeddingProvider
```

CNCF should also provide an embedded/reference implementation so that
InformationSpace and KnowledgeSpace executable specs do not require
`textus-sie`.

Candidate publication request:

```text
KnowledgePublicationRequest
  informationSpaceId
  informationItemId
  informationItemType
  publicationVersion
  rdfSubject
  namedGraph
  triples
  embeddingDocuments
  identityBindings
  provenance
```

Candidate publication result:

```text
KnowledgePublicationResult
  status
  publicationVersion
  rdfResult
  embeddingResult
  vectorIndexResult
  diagnostics
  publishedAt
```

CNCF responsibilities before calling the provider:

- confirm the item is publishable;
- enforce authorization;
- apply identity binding;
- apply authority profile;
- generate RDF triples;
- generate embedding source text;
- assign publication version;
- create audit/provenance context.

Provider responsibilities:

- write RDF triples;
- generate embeddings if delegated;
- write vector index entries;
- run backend-specific validation;
- return backend diagnostics;
- avoid owning CNCF lifecycle state.

Boundary rules:

- textus-sie must not become the source of truth for InformationSpace item
  lifecycle.
- provider-specific result models should be normalized into CNCF SPI
  result records.
- CNCF item shapes should not leak DBpedia/SPARQL/vector backend details.
- embedded provider should favor determinism over retrieval quality.
- textus-sie provider should own scale, ranking, retrieval quality, and
  external backend integration.

### Embedding Policy

Embedding should run after confirmation and as part of publication or a
publication-adjacent job.

Define which text is embedded:

- raw input text;
- normalized working record;
- confirmed display text;
- RDF-derived text;
- domain-specific generated summary.

Embedding should happen after confirmation, not before validation.

The current direction is to use profile-generated text, not raw records
directly. This keeps embeddings stable and explainable.

Candidate embedding profile:

```text
InformationEmbeddingProfile
  informationItemType
  textTemplate
  fields
  relationshipSummaries
  languagePolicy
  chunkingPolicy
  versionPolicy
```

Example paper embedding text source:

```text
Title: <title>
Authors: <resolved author labels>
Venue: <venue>
Abstract: <abstract>
Keywords: <keywords>
```

Example museum object embedding text source:

```text
Title: <title>
Creator: <resolved creator label>
Classification: <classification>
Material: <material>
Period: <period>
Description: <description>
Provenance: <summarized provenance>
```

Open embedding questions:

- whether embeddings are regenerated on every publication;
- how to version embedding source text;
- whether embeddings include unresolved candidate labels;
- how to handle multilingual fields;
- whether large documents/media use a separate content embedding pipeline.

### XML Mapping

YAML and JSON are the primary authoring formats for the first slice.

XML support is deferred until a concrete source appears. XML should not drive
the core model prematurely.

When XML is needed, the likely options are:

- domain-specific XML schemas;
- generic XML-to-authoring conversion;
- or profile-driven XPath mapping.

Current direction:

- avoid a generic XML graph import model for the first slice;
- accept XML only through a domain authoring profile;
- keep XML output normalized into the same authoring model used by YAML/JSON;
- do not make RDF predicate authoring the user-facing XML model.

Candidate XML mapping profile:

```text
InformationXmlMappingProfile
  itemType
  namespaceMappings
  fieldMappings
  listMappings
  identifierMappings
  validationProfile
```

Open XML questions:

- whether first XML support should target paper metadata formats, museum
  collection exports, or both;
- how to report XML path errors in the same validation issue model;
- whether XML schema validation is required before authoring-model mapping.

### Editing and UI/API

InformationSpace should expose component operations first. Browser/admin or
data-entry UI can be built on top after the operation contract is stable.

Decision: InformationSpace editing uses component operations as the canonical
mutation/query boundary. Web/admin/data-entry pages should call these
operations and render operation metadata instead of adding private mutation
routes.

Canonical operation groups:

```text
register_information_import_batch
get_information_import_batch
list_information_import_records

get_information_item
search_information_items
update_information_item
validate_information_item
list_information_item_issues

list_information_resolution_candidates
select_information_resolution_candidate
set_information_manual_resolution
request_information_resolution

confirm_information_item
reject_information_item
reopen_information_item

publish_information_item
get_information_publication_status

list_information_conflicts
get_information_conflict
resolve_information_conflict
```

Operation naming notes:

- use verb-first names to match existing operation naming style;
- keep `information` in the operation name because these are InformationSpace
  operations;
- keep import batch, item, resolution, publication, and conflict operations
  separate;
- avoid exposing RDF-specific verbs in the editing API;
- publication operations may internally create jobs, but the public operation
  remains InformationSpace-oriented.

Candidate operation response categories:

- import batch summary;
- information item record;
- validation issue list;
- resolution candidate list;
- confirmation/rejection result;
- publication status/result;
- conflict record/list.

Open request/response questions:

- patch format for `update_information_item`;
- pagination and filtering shape for list/search operations;
- validation issue record shape;
- whether `publish_information_item` returns immediately or returns a job id;
- whether conflict resolution request allows edit+select in one operation or
  requires separate edit and resolve operations.

Candidate first UI surfaces:

- import batch list/detail;
- staging record edit form;
- validation issue list;
- resolver candidate selection form;
- confirmation/rejection actions;
- publication status;
- conflict review list/detail.

Open UI/API questions:

- whether data-entry pages are separate from admin pages;
- whether generic SimpleEntity admin widgets are reusable for the first edit
  form;
- how to show resolver evidence and confidence without overwhelming users;
- how to handle bulk confirmation/publishing safely;
- whether publication should expose job status if it is asynchronous.

## Near-Term Implementation Slices

Decision: the first implementation slice should build the
KnowledgeComponent/InformationSpace foundation and use paper only as a thin
vertical validation driver. It should not be a paper-specific implementation
slice.

First slice scope:

1. Builtin KnowledgeComponent foundation:
   - CNCF embedded/builtin component entry point;
   - InformationSpace / KnowledgeSpace integration boundary;
   - operation surface for first InformationSpace flows.
2. InformationSpace skeleton:
   - runtime object / service boundary;
   - in-memory or lightweight repository for executable specs;
   - item id and staging record identity;
   - minimal lifecycle state transitions.
3. Information domain registry:
   - `web_page`, `book`, and `paper` registered as builtin/reference domains;
   - `InformationDomainDescriptor`;
   - authoring profile metadata;
   - resolver mapping metadata;
   - validation profile metadata.
4. Knowledge engine SPI:
   - publication/search/resolution/embedding provider interfaces;
   - request/result model skeletons;
   - provider selection boundary.
5. Embedded provider:
   - deterministic local/reference implementation;
   - no external RDF/vector/backend dependency;
   - enough behavior for executable specs.
6. Paper thin vertical slice:
   - YAML/JSON parse shape;
   - `PaperInformation` staging item;
   - required validation rules;
   - path-oriented validation issues.
7. Resolver candidate model:
   - resolver registry;
   - mock/local resolvers;
   - candidate, confidence, evidence, and confirmation-required fields;
   - unresolved/ambiguous issue handling.
8. InformationSpace operations:
   - register import batch;
   - get/list import records;
   - get/update/validate information item;
   - list/select resolution candidates;
   - confirm/reject item.
9. Authorization visibility:
   - map initial operations to InformationSpace capabilities;
   - keep descriptor/policy syntax minimal or test-local if full integration
     is too large for the first slice.

Explicit first-slice non-goals:

- RDF publication;
- vector embedding;
- KnowledgeSpace materialization overlay;
- Web/admin/data-entry UI;
- XML mapping;
- museum object parser;
- full book import;
- full web page import or DBpedia integration;
- full conflict workflow;
- external authority provider integrations;
- durable production RDB migration.

Executable spec targets:

- builtin KnowledgeComponent exposes InformationSpace foundation operations;
- domain registry contains `web_page`, `book`, and `paper` descriptors;
- Knowledge engine SPI can use an embedded provider without `textus-sie`;
- paper import accepts valid YAML/JSON authoring input;
- missing required fields produce path-oriented validation issues;
- invalid DOI/year/author order produce validation issues;
- resolver candidates are stored and visible from InformationSpace item state;
- exact trusted identifier match can be auto-selected by policy;
- name-only match requires confirmation;
- confirmation is blocked when required unresolved fields remain;
- confirmed item records selected identity bindings;
- KnowledgeSpace is not required for first-slice edit/confirm behavior.

Follow-up slices:

1. durable RDB-backed InformationSpace repository;
2. InformationSpace authorization descriptor syntax and policy integration;
3. conflict schema and RDB/RDF conflict review;
4. RDF publication profile for paper;
5. embedding text profile and publication job;
6. KnowledgeSpace materialization overlay from InformationSpace;
7. data-entry/admin UI;
8. museum object authoring schema and resolver profile;
9. XML mapping profile when a concrete source appears.

## Non-Goals For The First Slice

- full RDF ontology design;
- automatic conflict resolution;
- complete XML profile language;
- vector embedding optimization;
- admin UI polish;
- generic RDF editor;
- replacing RDF DB with RDB storage.
