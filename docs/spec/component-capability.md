# Component Capability Specification

Status: normative semantic contract

## Authority and scope

This specification defines Component Capability meaning, identity,
declarations, versions, provenance, grounding, realization, compatibility,
traceability, and admission/query outcome boundaries. R1 through R10 are the
self-contained normative semantic contract.

The [Component Capability design](../design/component-capability.md)
provides non-normative structural context. The existing Phase 56
[Component Identity Specification](component-identity.md) remains the
authority for Component identity.

This semantic specification does not establish an implemented projection,
codec, ABI support set, admission API, runtime discovery surface, generated
fixture, or producer/consumer integration. E1 through E10 are semantic
acceptance expectations, not executed evidence. Implementation and executable
proof belong to CAP-78-02 through CAP-78-05 as identified below.

## Rules

### R1 Ownership and non-instantiated contract

Capability MUST be a non-instantiated model contract. Cozy owns syntax, IR,
normalization, and production. CNCF owns the Component Capability semantic
contract, version admission, and discovery. Textus CBD Support owns ingestion,
catalog, and query.

Application Capability MUST remain upstream and MUST NOT become Component ABI
lifecycle data. Runtime invocation, availability, authorization, permission,
and guards MUST remain separate concerns.

### R2 Exact qualified identity

Stable Capability identity MUST be the exact qualified upstream
`ModelElementId(modelId, elementId)`, tagged as Component Capability.
`contractVersion` MUST be a separate, explicitly supplied opaque value.

Model identity components MUST retain exact case, Unicode, and delimiters
under the established Cozy foundation validity rules: nonempty, unpadded,
and free of ISO control characters. Identity MUST NOT be issued as a runtime
`EntityId` or `CanonicalId`. Business meaning MUST NOT be parsed from IDs.
Names, digests, and positions MUST NOT derive or substitute for identity.

The owning runtime Component MUST retain the existing Phase 56
namespace/local-ID `ComponentId` authority and its separate release version.
Capability identity MUST NOT replace or reinterpret Component identity.

### R3 Provided and required declarations

A declaration MUST bind one exact Component identity, a `PROVIDED` or
`REQUIRED` direction, and a versioned Capability reference comprising the
R2 identity and explicitly supplied contract version.

Several Components MAY provide the same reference. One Component MAY both
provide and require that reference. The uniqueness key MUST be the complete
`(Component identity, direction, Capability reference)` tuple.
Duplicate or conflicting declarations MUST fail admission; first-wins or
last-wins collection behavior MUST NOT resolve them.

Definitions and links MUST retain their declaring model/source ownership.
Presentation labels MUST NOT merge identities.

### R4 Distinct version domains

Projection schema version, Capability `contractVersion`, generated CNCF ABI
version, producer version, Component/package release, and source revision
MUST be distinct explicit fields. The first projection semantic generation
is version 1. An unsupported future version MUST produce a typed unsupported
result and MUST NOT be decoded as version 1.

CAP-78-02 MUST freeze concrete wire keys, the codec, and the exact ABI support
set before executable admission. CAP-78-01 freezes no existing serialization
implementation or invented ABI. The field domains here describe semantics
and do not prescribe wire keys or syntax.

### R5 Independent provenance

Provenance MUST independently retain producer identity/version, declaring
model authority, exact Component identity, package coordinate/release,
explicit source revision, and project-relative source location. A supplied
source line MUST be positive; line information is retained when available.

Revisions MUST be upstream-authored data and MUST NOT be computed content
identities. Empty or unknown source acquisition MUST NOT be admitted as
provenance. This contract MUST NOT introduce hashes or byte identities.
Existing Cozy foundation `sha256` MUST NOT become Capability identity,
revision, permission, or admission authority and MUST NOT be promoted into
this contract.

### R6 Admitted semantic grounding

Admitted semantic grounding MUST preserve qualified
`ModelElementId(modelId, elementId)` or `TermId(vocabularyId, termId)` targets,
relation/profile identities when supplied, the explicit local/external
boundary, declared/derived origin and its evidence, and edge-specific
source/revision.

Actual Domain Concept, Glossary, and BoK references MUST be preserved without
name/IRI equivalence or ontology-completeness requirements. Unknown external
targets MUST remain explicitly external. A declared local target MUST resolve
uniquely within the admitted source scope. Missing grounding MUST remain
explicit absence and MUST NOT manufacture a term.

### R7 Explicit realization edges

Realization MUST be a separate explicit edge from a versioned Capability
declaration to exactly one `OPERATION`, `WORKFLOW`, or `STATE_MACHINE` target
reference per edge. Each edge MUST retain the target-owning Component/model
scope, target contract/ABI revision, and edge source/revision.

References MUST bind already admitted target contracts exactly. Unknown,
ambiguous, or incompatible declared targets MUST fail admission. Explicitly
empty realization relationships MAY be admitted as non-executable
architectural facts. They yield a downstream `MissingRealization` gap when
needed and MUST NOT fabricate a mapping.

Realization edges MUST NOT embed ordering, retry, compensation,
authorization, or guard state.

### R8 Directed contract compatibility

Provider matching MUST require equal qualified Capability identity and either
exact `contractVersion` equality or an explicit admitted directed
compatibility edge from the required version to the provided version of that
same identity.

Matching MUST NOT assume SemVer, use name similarity or cross-identity
equivalence, select a provider by ordering, infer undeclared transitivity,
or invent implicit compatibility.

A realization MAY change without changing stable Capability identity only
while the explicitly declared Capability contract remains satisfied.
Target ABI compatibility MUST be checked separately, and its provenance
changes. A semantic contract change MUST have an explicit `contractVersion`
change and declaration; a revision MUST NOT be guessed.

### R9 Specification and Evidence traceability

Specification and execution Evidence MUST be separate explicit qualified
traceability references with their own source/revision and edge ownership.
Missing references MUST remain absent.

Reference presence MUST NOT prove successful execution, specification
coverage, availability, or authorization. Edges MUST be preserved
independently; unrelated reference lists MUST NOT be cross-producted.

### R10 Admission and scoped query outcomes

Admission and query facts MUST distinguish an admitted empty relationship or
catalog from unsupported, unavailable, unauthorized, stale, ambiguous,
malformed, or incompatible input. Rejection MUST preserve the typed
diagnostic kind and exact owning scope/location.

`NoProvider` MAY be reported only over an explicitly selected, successfully
acquired, admitted catalog scope. Failed source acquisition MUST NOT prove
nonexistence. Application/Use Case demand projection availability MUST remain
separate from required Component Capability input availability.

## Semantic acceptance matrix

These cases specify expected interpretations of the rules. They do not
record executed tests, admitted real fixtures, or Step acceptance.

| ID | Case | Expected semantic outcome | Rules |
| --- | --- | --- | --- |
| E1 | Two distinct exact Components provide one qualified Capability/version. | Retain both distinct Component declaration edges; do not select by enumeration order. | R2, R3, R8 |
| E2 | One Component provides and requires the same reference; the complete tuple is then duplicated. | Retain distinct PROVIDED/REQUIRED declarations; reject the duplicated complete tuple. | R3 |
| E3 | Required and provided contract versions differ. | Match only through a supplied admitted directed edge from required to provided version of the same identity; different identities never match. | R2, R8 |
| E4 | Realization changes while the Capability contract remains explicitly satisfied. | Retain stable identity with explicit target/source revision; reject incompatible target ABI separately. | R5, R7, R8 |
| E5 | Admitted model/term grounding includes local/external and declared/derived provenance, with grounding absent where not supplied. | Preserve exact qualified relations, absence, and edge provenance without ontology synthesis. | R5, R6 |
| E6 | Equal labels have different qualified identities, or declarations are duplicate/conflicting. | Do not merge semantics; report duplicate/ambiguity diagnostics rather than resolve by collection order. | R2, R3 |
| E7 | A declared realization target dangles, compared with an explicitly empty realization set. | Reject the dangling declaration; retain the admitted empty set and allow a distinct MissingRealization gap when needed. | R7, R10 |
| E8 | Source is unavailable, unauthorized, or stale, compared with an admitted empty scoped catalog. | Preserve separate failure/availability diagnostics; permit NoProvider only from the successfully acquired admitted scope. | R5, R10 |
| E9 | Specification/Evidence references are present without execution evidence. | Reference presence does not assert execution, coverage, availability, or authorization; preserve independent edges. | R9 |
| E10 | Projection version is unsupported, or version domains are confused. | Return typed unsupported/inconsistent admission; do not downgrade or default ABI/source revision. | R4, R5, R10 |

## Implementation ownership

The work boundaries in [Phase 78](../phase/phase-78.md) own the forthcoming
implementation and executable evidence:

| Step | Responsibility |
| --- | --- |
| CAP-78-02 | Freeze concrete wire keys, codec, and exact ABI support set; implement external provided/required projection and executable admission. |
| CAP-78-03 | Implement exact binding to admitted Operation, Workflow, and StateMachine target contracts and its executable proof. |
| CAP-78-04 | Implement runtime-independent ComponentFactory and package/CAR exposure and discovery with executable proof. |
| CAP-78-05 | Prove a real Cozy-generated fixture through CNCF admission/discovery and Textus CBD Support consumption with exact provenance. |

Cozy Phase 64 owns syntax, IR, normalization, and generated producer output.
Textus CBD Support Phase 11 owns consumer ingestion, catalog, and queries.
Their Step and Phase acceptance remain separately owned. This specification
selects no resource filename, canonical package surface, running API, or
implementation syntax.
