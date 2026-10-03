# Logical UI generated ABI v1 proposal

Document role: non-normative P96-01B contract proposal.
Status: historical proposal; promoted design/spec subset accepted for P96-01; implementation pending.
Date: 2026-10-03
Owners: CNCF Phase 96 consumer/runtime; Cozy Phase 74 semantic authority;
Cozy Phase 76 producer.

## P96-01C promotion and navigation

The selected ownership, static ABI/admission/provider contract and runtime
behavior below are promoted to [runtime design](../design/display-model-runtime.md),
[generated ABI spec](../spec/logical-ui-generated-abi.md) and
[runtime spec](../spec/display-model-runtime.md), with history in the
[promotion journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md).
Earlier candidate/no-promotion statements are superseded only for that exact
subset. The P96-01B text remains historical proposal input; unpromoted wire
examples, mutation physical schemas and context details remain non-normative.
Independent P96-01C review and parent admission accept the promoted documentary
subset and close P96-01. Implementation/executable verification, Phase acceptance
and all three actual LUI handoffs remain pending.

This note records the parent-selected candidate contract for the existing
Phase 96. It is input to later P96-01 acceptance and P96-02/03 implementation,
not an accepted public API, executable proof or LUI-ABI-01 receipt. The
[boundary note](logical-ui-generated-abi-display-runtime-boundary.md),
[inventory](display-model-phase-96-contract-inventory.md) and
[checklist](../phase/phase-96-checklist.md) retain the wider scope. Concrete
sample data, source inspection provenance and unexecuted acceptance matrix
belong to the [proposal journal](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md).

## 1. Ownership, naming and independent version spaces

The candidate Scala 3 package is `org.goldenport.cncf.display`. Candidate
object `GeneratedLogicalUiAbi` owns the static ABI values and admission;
auxiliary capability trait `GeneratedLogicalUiMetadataProvider` exposes the
public method:

```text
generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition]
```

The candidate ABI identity is `cncf.logical-ui-generated.v1`, integer version
`1`. Its identity/version is independent of the exact Phase 74 semantic
descriptor, each definition's revision, generated-source provenance revision,
Display wire version and runtime Entity/source snapshot revision. These are
different compatibility and ownership spaces. Provenance revisions are
nonempty opaque strings; neither screen/resource IDs nor clocks derive them.

`SemanticContract` preserves exactly this provisional Phase 74 descriptor:

| Member | Exact value |
| --- | --- |
| `schema` | `cozy.logical-ui-runtime.v1` |
| `version` | `1` |
| `semanticAuthoritySchema` | `cozy.logical-ui-semantics.v1` |
| `assurance` | `provisional-scenario` |
| `driver.repository` | `KnowledgeHubProject/nict-editing-studio-app` |
| `driver.revision` | `48eb43b8eb839137e17ef4c944b3db4a73d0cec3` |
| `driver.path` | `docs/phase/phase-2.md` |
| `driver.scenarioId` | `resource-list-detail` |

That historical driver provenance does not relabel current app Phase 4
prototype or Phase 5 integration ownership. `SourceProvenance` carries
`producerId`, `sourceId`, `revision`; all three identify source provenance as
opaque text. ABI admission accepts any valid provenance strings. It does not
hard-code an approved producer commit or fixture hash; a handoff receipt
separately pins the actual revisions and artifacts used in that proof.

CNCF owns the consumer ABI and runtime. Cozy owns semantic modeling and
deterministic generation against it. No copied Cozy implementation object
graph, `cozy_2.12` linkage, special shared cross-build, hash-pinned runtime
fixture identity or second semantic authority is introduced.

## 2. Static definitions without runtime data

The following is constructor-shape documentation, not a compilable source
artifact. All members are immutable and public names use camelCase.

```text
Definition(abiIdentity, abiVersion, semanticContract, provenance,
  screenId, definitionRevision, viewBindingId, screen)

sealed abstract class ScreenDefinition
  ListDefinition(title, itemFields, detailTarget, actions, itemActions)
  DetailDefinition(title, sections, actions)

DetailTarget(screenId)
SectionDefinition(id, title: Option[String], fields)
FieldDefinition(id, label, role, value: ValueDefinition)
ValueDefinition(bindingId, allowedKinds: Vector[ValueKind])
ActionDefinition(id, label, bindingId)

SemanticContract(schema, version, semanticAuthoritySchema, assurance, driver)
DriverReference(repository, revision, path, scenarioId)
SourceProvenance(producerId, sourceId, revision)
```

`ScreenDefinition` is a sealed abstract conceptual trunk with exactly List
and Detail alternatives. The provider remains an auxiliary mixin trait.
`PresentationRole` has closed tags `content`, `title`, `subtitle`,
`description`, `status`; `ValueKind` has closed tags `text`, `number`,
`boolean`, `timestamp`, `missing`. Ordered definitions, sections, fields,
actions and allowed-kind sets use `Vector` and retain input order.

`Definition.screenId` is the sole screen identity; ListDefinition and
DetailDefinition do not duplicate it. IDs, revisions and binding references
are opaque validated strings at this minimum ABI boundary. They do not issue
CanonicalId or domain EntityId values. A definition revision is opaque, not
EntityRevision.

The static ABI emits no resource values, source Entity revision, permission,
capability result, Operation execution, function/callback, endpoint,
widget/style or datastore reference. Existing entity schema/constraints and
mutation target binding remain application/runtime-owned. It contains neither
an untyped metadata map nor a second validation language.

`viewBindingId`, `value.bindingId` and `action.bindingId` are component
registration keys. They are not semantic property expressions, class names,
routes or guessed setters. Explicit field bindings may combine or derive
values; a derived or combined field has no inferred inverse write mapping.

## 3. Closed admission and deterministic diagnostics

The proposed public gate is:

```text
admitC(definitions: Vector[Definition]): Consequence[AdmittedDefinitions]
```

Raw Definition case classes are untrusted input. `AdmittedDefinitions` is an
immutable validated wrapper with no public constructor or copy capable of
bypassing admission. Structural admission fetches no data, executes no
converters, authorizes no action, parses no CML and mutates no component
registration. Failure uses structured Consequence/Conclusion diagnostics,
not an uncaught NPE or string-only failure.

The proposed diagnostic shape is `Diagnostic(code, path, reason)`, with a
closed code vocabulary:

| Code | Rejection boundary |
| --- | --- |
| `LUI96_ABI_INCOMPATIBLE` | Null or incompatible ABI identity/version descriptor fields |
| `LUI96_CONTRACT_INCOMPATIBLE` | Null or nonidentical semantic contract |
| `LUI96_PROVENANCE_INVALID` | Missing or malformed provenance |
| `LUI96_DEFINITIONS_MISSING` | Present selected provider with an empty vector |
| `LUI96_ELEMENT_INVALID` | Null vector/root/element/Option container or Some(null) |
| `LUI96_ID_INVALID` | Malformed ordinary ID, binding reference or definition revision |
| `LUI96_TEXT_INVALID` | Malformed label/title text |
| `LUI96_VALUE_KIND_INVALID` | Unknown/null/duplicate/empty allowed-kind members |
| `LUI96_ROLE_INVALID` | Invalid role or role-kind mismatch |
| `LUI96_DUPLICATE_ID` | Duplicate ID within its declared scope |
| `LUI96_DUPLICATE_ROLE` | Repeated non-content role in a field container |
| `LUI96_DETAIL_TARGET_INVALID` | Invalid or unresolved same-component Detail target |

No provider is not an ABI failure. A null provider vector is element-invalid,
while an empty vector from a present provider is definitions-missing. Reject
nested nulls before dereferencing them; descriptor/provenance failures use
their specific codes above. No silent sorting, trimming, repair, kind
coercion or unknown-kind fallback occurs.

For IDs, bindings, revisions and provenance, use Phase 74's concrete predicate
`value != null && value.nonEmpty && value == value.trim` and preserve supplied
bytes. Labels/titles use `value != null && value.trim.nonEmpty`, retaining
supplied display text unchanged. These reference predicates do not introduce
a Unicode normalizer or weaken stronger domain input validation.

`allowedKinds` is nonempty, contains only supported nonnull values and has no
duplicates. A null collection is element-invalid; other invalid set members
are value-kind-invalid. Non-content roles require exactly `Vector(text)`;
content permits any nonempty unique subset, including text plus missing.
Runtime title-role text must be nonblank, but the static ABI has no runtime
title field value to validate at admission.

Identity and uniqueness scopes are:

| Identity or role | Scope |
| --- | --- |
| Screen ID | Selected component provider vector, irrespective of definition revision |
| Section ID | One Detail definition |
| Field ID | One List item template or one Detail section |
| Action ID | One List screen, List item template or Detail screen |
| Non-content role | One field container; multiple content fields are allowed |

IDs may repeat across scopes. Empty nested vectors and `None` section titles
are valid. Explicit bindings may be reused; binding IDs are not globally
unique field identities. A List DetailTarget resolves to an admitted Detail
in the same selected vector. Missing, self, wrong-kind and cross-component
targets fail even when their text is valid. A Detail need not be referenced
by a List; no automatic cross-component import occurs.

First-error order is fixed: top vector shape/empty; each Definition in vector
order (shape, ABI, semantic descriptor, provenance, scalar IDs/title, nested
structure and scoped duplicate IDs/roles in declaration order, then nested
fields/actions); duplicate screen IDs; then List Detail target referential
integrity. Code/path/reason order is stable. Paths use named members and
numeric indexes, for example `definitions[i].screen.sections[j].fields[k].role`.
Diagnostic text must not dump runtime secrets or values. Closed enums exclude
fabricated future variants at compile time; later decoding rejects unknown
tags instead of converting them to text or missing.

## 4. Provider selection and binding join

Follow the bounded ComponentFactoryWorkflowPart discovery pattern. The
component provider wins. Only when it is absent is the component.factory
provider considered. Both present means explicit component override; the
shadowed factory is not read. Providers are not concatenated, the classpath is
not scanned and Cozy classes are not reflected.

No provider means no definitions and no Display capability, preserving old
components. An empty or invalid selected provider fails construction without
fallback to another provider. A selected-provider exception becomes a
structured component bootstrap failure, not a successful empty registry.
Admit the complete selected vector atomically; never publish a partially
admitted prefix. Registry/lookup is component-owned, with no JVM global
mutable registry.

Structural admission and runtime binding resolution are distinct gates.
First validate static ABI; then join view/value/action registration keys to
explicit application bindings before Display lookup becomes usable. Missing
binding or incompatible typed source/converter is structured bootstrap
failure, not reflection or late best-effort field inference. This proposal
does not freeze a new generic DI engine.

Existing bootstrap admits Workflow metadata before entity/View creation.
Logical UI structural admission can occur there, but its later binding join
must wait for the required View/binding registration and finish before Display
exposure. Existing Workflow/StateMachine admission policy is unchanged.

## 5. Runtime projection, value seam and Operation choice

Semantic View remains `V`, `Vector[V]` or `SearchResult[V]` through canonical
contextual View facilities. The application registers typed source and
converter bindings; generated ABI carries their IDs only. Runtime data keeps
EntityId ownership and EntityRevision through existing CNCF types, without
parsing or branching on opaque Display IDs.

A Display instance is distinct from both Definition and semantic `V`. It
realizes Phase 74 List/Detail/Section/Field/Action semantics using text,
BigDecimal numbers, Boolean, Instant timestamps and explicit missing, with
supplied order and scoped identities preserved. Converters may reshape,
combine or emit several fields, but each result must be one of that field's
admitted kinds. Incidental `toString`, raw JSON and Any fallbacks are excluded.
Missing is an explicit typed value, never null or a dropped field. Invalid
runtime role/value, duplicate resource identity, missing binding or wrong
kind fails projection.

Actions are inert definitions. Registered runtime bindings decide hidden,
disabled or enabled presence from the current authorized context. Displayed
enabled presence never authorizes execution or introduces a generated callback.

The proposed service selector is `display`; canonical read Operations are
`query` and `read`, with no list/get aliases. Component ownership and ordinary
Operation/REST selector resolution remain canonical. Register exactly one
explicit component Display service. A conflicting display service or duplicate
Operation is a bootstrap conflict, never last-wins replacement. Components
without declared Display definitions expose no Display service.

The client selects admitted screen/resource/query input, not View, converter
or Aggregate implementation names. Physical HTTP route, media type, codec and
mutation input schemas remain P96-04..07 decisions; these proposed names do
not silently fix them. Business Operations execute directly through ordinary
Operations, followed by client Display reload after success. There is no
generic action tunnel, push/subscription or Service Bus refresh.

## 6. Fixture provenance and future acceptance

The [journal](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md)
maps the unchanged development-only ESS2-01 sample to explicit binding keys,
scoped definitions and later typed runtime identity ownership. Static
definition provenance identifies its definition source/revision, not a
per-resource revision. Dynamic Detail/List metadata separately preserves the
source revision/snapshot. Raw sample values remain in the existing sample
journal. Writable targets, readonly derived values and unsupported create/delete
remain fixture-owned; a separate positive create/delete fixture is required.

The named future proof matrix is P96-01B-ABI-v1. Its unexecuted cases are
ABI-01..06, BOOT-01..02, PROJ-01..03 and ROUNDTRIP-01, recorded concretely in
the journal. When implemented, use Given/When/Then and property-based checks
for laws. This documentary Slice adds no tests asserting Markdown wording,
checklist closure or source text.

LUI-ABI-01 requires actual P96-02/03 ABI implementation, external expected-
generated fixture compilation, normal admission and focused projection
evidence. This note is not that handoff. LUI-GEN-01 belongs to Cozy Phase 76
and remains pending. LUI-ACCEPT-01 requires the actual generated files through
the same CNCF path, not a handwritten substitute. Each receipt identifies
exact Git revisions/source coordinates and commands/results. Intermediate
handoffs require neither full tests nor public publication.

## 7. Remaining work and document lifecycle

This Slice supplies a proposal for review only. Later P96-01 promotion must
establish accepted design/spec and reconcile runtime, transport and mutation
scope before Step closure. P96-02/03 implement and prove the accepted
contracts; Cozy P76-01 consumes the candidate mapping through its own
acceptance. P96-04/05 consume proposed service names after normal contract
acceptance. Production application bindings remain outside this edit.

The [checklist](../phase/phase-96-checklist.md) retains every obligation:
strict atomic observed-revision delete (P96-06/07); committed-write/Display-
pending results and read-after-write correspondence (P96-06/07); If-Match
adapter (P96-06/07); codec (P96-04); context/Phase 98 hook (P96-10); positive
create/delete and reference fixture (P96-07/09); consumer and final release
validation (P96-11). No requirement disappears. No new Phase, product root,
runtime implementation or accepted public contract is created. There is no
notes-to-design/spec promotion in this Slice.
