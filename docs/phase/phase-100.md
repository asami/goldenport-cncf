# Phase 100: DataStore Logical Types, Storage Mapping and Recovery

status=in-progress
resumed_at=2026-10-17
planned_at=2026-10-03
renumbered_at=2026-10-05
driver=textus-knowledge-workbench
checklist=[Phase 100 Checklist](phase-100-checklist.md)
producer_handoff=asami/cozy Phase 78

Renumbered on 2026-10-05 from the local, uncommitted DataStore Phase 97 plan.
GitHub Phase 97 (AI Audit) is authoritative; GitHub Phases 98 and 99 retain
their existing allocations. The DataStore requirements and OPEN status are unchanged.

## Current migration execution — 2026-10-17

The user requests Phase 100 completion within the Workbench mac mini ->
asamiair migration and reports Phase 96 complete/pushed. Canonical CNCF main
and fetched origin/main select `e455716c`; Phase 96's CLOSED acceptance remains
intact. The planning-only instruction below is historical and superseded by
this development instruction, without changing the original Phase requirements.

The migration WIP is integrated into the canonical CNCF/Core/Cozy main and
SimpleModeler scala12 working trees, with integration commits pending. Core
logical types, producer declarations and generated persistence hooks are the
selected prerequisites. Current provider/consumer validation, independent
comparison review and Phase acceptance remain required. Live command evidence
stays in private ignored workflow storage under the shared retention policy.

The Workbench migration ledger is
`/Users/asami/src/dev2026/textus-knowledge-workbench/docs/phase/workbench-asamiair-migration.md`.
On 2026-10-18 the user confirmed source-only migration with no existing Workbench
DB. No source-host access or original production database is required. Diagnose
and rehearse restart/recovery on retained inputs and test-owned isolated stores.
Original private Workbench Phase 1 workflow history remains separate; its identity
and counters are not recreated. No live database changes or Phase acceptance are
claimed. The owning checklist retains all unsatisfied criteria.

## Phase 100 / Phase 115 boundary — 2026-10-18

The user-selected [collection rule](../rules/collection-and-semi-structured-data.md)
and [Phase 115](phase-115.md) assign canonical collection and Core Phase 3
Record adoption to Phase 115 after Phase 100 acceptance. Phase 100 retains its
declared logical/storage mappings, selected generated consumers, exact original
text, SQL restart and recovery requirements. Phase 115 is a separately accepted
migration; it adds no Phase 100 completion condition.

The static generated object List Query finding is retained with its original
comparison invalidation. Its canonical producer/model/caller migration belongs
to Phase 115; an old List declaration alone does not authorize a restoration
branch. This ownership disposition is not a source repair or comparison closure.
NICT's direct downstream checks and failed CAR preparation are recorded separately;
its application runtime adoption remains with NICT. See the
[boundary assessment and evidence limits](../journal/2026/10/2026-10-18-phase100-phase115-collection-boundary.md).
Original comparison closure, the sole recovery-full review, all owning checklist
requirements and release records remain required. No closure is claimed here.

## Current checklist projection — 2026-10-18

The owning checklist now records 44 of 47 items satisfied from the completed
independent assessment and actual final validation. Impact inventory, provider
integration, consumer transition and isolated recovery groups are CLOSED.
The historical preimplementation ABI handoff criterion and final review/release
items remain OPEN pending focused review and release. No current test proves
the historical ordering. The user explicitly replaced that historical condition
on 2026-10-18 with current frozen ABI/generated-fixture conformance, completed
functional validation and independent assessment, retaining the old order as
UNPROVEN in the checklist and dated journal. Earlier planning-only OPEN statements below
describe the original allocation, not the current checklist projection.

Core, Cozy, SimpleModeler and Workbench source integrations are committed.
CNCF's merge remains pending after the registered integrity check rejected final
blank lines in four unchanged producer-generated fixtures. The user subsequently
approved a persistent exact-path Git convention, now applied without changing
fixture bytes. Both parent-relative checks pass under that changed policy;
the new CNCF-only integration is pending. Existing successful commits and
CNCF's staged tree are retained. No Phase closure or Phase 115 start
is claimed. The sole full-review use and original review/repair history remain.

## Goal

Make OR-mapper reconstruction follow declared application logical types and
their storage mappings. DataStore returns the physical String from a TEXT
column; the OR mapper owns JSON parsing and application-value reconstruction.
A SQL TEXT value must not change type merely because it looks like JSON.
Deliver the consumer transition and a
recoverable treatment of existing data together with the mapping contract.

A separate required goal is to absorb the supported behavior currently carried
by `DataType.Named("record")` into the formal datatype, generation and
OR-mapping route, and eliminate that workaround from the active route.
Preserving its behavior through `XSemiStructuredData` is required; deleting or
renaming the marker alone is not completion.

This allocates one independent Phase. It does not start implementation, accept
the current working tree, reopen an existing Phase or authorize a live database
migration. All work groups remain OPEN in the checklist.

The plan was initially prepared without implementation authority. On
2026-10-05 the user instructed dedicated-checkout development of CNCF Phase 100
and Cozy Phase 78 for Workbench, then explicitly added dedicated Core and
SimpleModeler checkouts. That subsequent direction now authorizes the
coordinated prerequisites and implementation. Core canonical types are being
implemented first. The routes below remain the target; separate CNCF and Cozy
Phase acceptance and all OPEN checklist requirements are retained.

## Requirement baseline

| Declared application type | OR-mapper write conversion | DataStore/DB value | OR-mapper read conversion |
| --- | --- | --- | --- |
| String | Preserve String | String / SQL TEXT | Preserve original String |
| Class X | X instance -> JSON value -> JSON text | String / SQL TEXT | String -> JSON value -> X instance |
| Semi-structured data | Record or admitted JSON value -> JSON text | String / SQL TEXT | String -> JSON value -> Record |
| JSON | JSON value -> JSON text | String / SQL TEXT | String -> JSON value |
| Array | Declared array -> JSON array -> JSON text | String / SQL TEXT | String -> JSON array -> declared array/element values |

JSON here means a structured JSON value, not its serialized String. A JSON
application value remains JSON after readback; it is not automatically made
into Record. Class X reconstruction requires its declared application codec,
not just successful JSON parsing. The semi-structured contract restores Record
even when an admitted input was supplied as JSON. Define the accepted JSON
shapes for that conversion explicitly; do not coerce arbitrary JSON into Record.

These are the mappings for a backend that stores these values as text. Backend
native JSON support can change the physical adapter, not the declared
application type or ownership of application reconstruction.

The application declaration is the source of meaning. SQL column metadata and
the payload's spelling cannot substitute for that declaration. The Phase must
decide and specify null/absence, nested values, number precision, malformed
payloads, incompatible mappings and schema evolution before implementation.
JSON text intentionally modeled as String remains String.

### Formal datatype route

`DataType.Named("record")` is a temporary workaround, not the accepted final
declaration of semi-structured data. Phase 100 must establish canonical,
distinct semi-structured and JSON datatype definitions in the existing type
system, including their value representations, normalization and structured
failure contracts. The approved datatype names are `XSemiStructuredData` and
`XJson`.

- `XJson` represents the JSON data model, using `io.circe.Json` internally
  and JSON as its external representation.
- `XSemiStructuredData` represents format-independent semi-structured data,
  using `Record` internally and supporting JSON, YAML and HOCON as external
  representations. External format selection is explicit and independent of
  the physical storage mapping.

DSM-100-02 fixes model spellings, normalization, accepted shapes and
format-specific conversion rules. Do not infer a format from payload spelling
or silently discard values that cannot be represented by Record.

Carry those identities through model declarations, generated Scala types and
schema metadata into the existing EntityStore/EntityPersistent OR-mapping
boundary. Reuse that boundary rather than introduce a parallel ORM. Dedicated
type names alone do not close the gap: generated metadata must select the
corresponding storage codec and readback type.

Applications may explicitly call `toRecord`/`fromRecord` when using DataStore;
that conversion is application-owned. Automatic OR-mapper encoding/decoding
must instead select its codec from the declared logical datatype and storage
mapping: `XJson` restores JSON and `XSemiStructuredData` restores Record.
A runtime Record/Json value, a `RecordPresentable` implementation or the
availability of `toRecord`/`fromRecord` does not establish that declaration.
Those methods may implement an explicitly selected codec, but must not act as
an implicit codec-selection fallback. The outer Record carrying a DataStore
row does not declare its individual fields as `XSemiStructuredData`.

Inventory what the existing `DataType.Named("record")` path actually does and
assign each supported behavior to the formal declaration, normalization,
generation or OR-mapping stage, with regression evidence. Remove
`DataType.Named("record")` from the final producer/runtime route and
regenerate affected artifacts from their authoritative models. Do not replace
it with `Named("json")`, string-name dispatch or hand-edited generated code.
Any compatibility reader for legacy declarations must have an explicit removal
criterion and must not remain the canonical producer output. This requirement
does not prohibit unrelated legitimate uses of `DataType.Named`.
Closure requires no active producer output, generated schema, runtime branch
or executable consumer dependency on `DataType.Named("record")` in the
selected scope. Temporary compatibility readers must be retired from that
route before closure. Historical documents and deliberate rejection fixtures
may retain the spelling as evidence, not as supported runtime behavior.

## Target route: EntityStore -> OR mapper -> DataStore

The automatic persistence route is:

```text
Write: EntityStore -> OR mapper (declared types + storage mappings)
                  -> DataStore (physical row values) -> DB
Read:  DB -> DataStore (physical row values)
          -> OR mapper (declared types + storage mappings) -> EntityStore
```

- EntityStore owns entity operations, identity, lifecycle and concurrency
  policy. It supplies the authoritative entity/field declarations to the
  OR-mapping boundary.
- The OR mapper resolves declared types and storage mappings, encodes field
  values before physical writes, and decodes physical values before entity
  reconstruction. It builds on EntityPersistent and its existing hooks;
  `toStoreRecord`/`fromStoreRecord` must have an explicit relationship to the
  typed stages so existing custom conversions are not applied twice.
- DataStore owns physical row access and provider operations. Its outer Record
  remains a row carrier. It does not choose application decoders.

Establish one mapping contract for create/save/update, partial patches,
load/search/projection, conditional/versioned mutations and their authoritative
readback. Inventory each current direct DataStore call in StandardEntityStore
parts and classify it as physical internal work or an application-value boundary;
the latter must pass through the OR mapper. Preserve transaction participation,
provider identity, revision checks and atomic side effects while inserting the
conversion stages. Missing typed mappings must not fall back to runtime-type or
payload-shape inference.

### Planned implementation order

1. Inventory those entry points and existing generated/custom codecs, including
   migration/admission hooks and managed-field handling (DSM-100-01).
2. Freeze the route, stage input/output types, field declarations, codec
   selection, storage mapping and compatibility activation in canonical specs;
   define `XJson` and `XSemiStructuredData` contracts in the existing Core type
   system (DSM-100-02).
3. Freeze the producer-facing fixture/contract, hand the required CML/Cozy generation work to Cozy Phase 78, accept its generated fixture, then implement the Core/runtime integration and connect
   generated metadata and existing EntityPersistent hooks to the OR mapper
   and physical provider operations (DSM-100-03/04). Do not hand-patch generated
   artifacts or add a competing type system.
4. Prove an EntityStore write -> OR mapper -> SQL TEXT -> restart -> DataStore
   read -> OR mapper -> EntityStore roundtrip for String, XJson and
   XSemiStructuredData, then cover the complete mutation/query matrix and
   remaining declared X/array contracts (DSM-100-03/06).
5. Transition consumers, remove admitted legacy inference and rehearse data
   recovery before Phase closure (DSM-100-04/05/06). A successful first
   roundtrip alone does not close the Phase.

## Work stack

| ID | Outcome | Dependency |
| --- | --- | --- |
| DSM-100-01 | Inventory actual logical-type producers, DataStore consumers, existing encodings and affected data; retain reproduction and original recovery inputs. | None |
| DSM-100-02 | Freeze the application-type / OR-mapper codec / physical-storage contract, declaration ownership, missing-mapping behavior and compatibility matrix. | DSM-100-01 |
| DSM-100-03 | Bind admitted mappings in the OR mapper over physical DataStore operations, consistently across writes, reads, queries and mutation readback; consume the Cozy Phase 78 generated producer fixture for generated-declaration coverage. | DSM-100-02 and Cozy Phase 78 producer handoff |
| DSM-100-04 | Migrate affected callers and replace heuristic decoding and collection/field-specific exceptions with declarations, including regenerated Cozy Phase 78 outputs in the selected scope. | DSM-100-03 and Cozy Phase 78 |
| DSM-100-05 | Implement and rehearse diagnosis, explicit migration and recovery for existing records on isolated copies. | DSM-100-02, DSM-100-04 |
| DSM-100-06 | Prove provider parity and restart roundtrips with workbench and existing durable consumers; explicitly accept the Cozy Phase 78 generated fixture; complete review and release evidence. | DSM-100-03 through DSM-100-05 and Cozy Phase 78 handoff |

## Ownership and integration

- Applications/models own field declarations in the canonical type system.
  The OR mapper consumes those declarations and owns mapping selection,
  application-value <-> JSON conversion and JSON <-> storage-String conversion.
  Class-specific codecs own X construction and validation. DataStore/provider
  code owns physical storage access and returns String for the text mapping;
  it must not infer or construct X, Record or JSON from payload spelling.
- CNCF owns the integration of that mapping boundary with DataStore and
  EntityStore, structured failures and provider-independent mapped semantics.
- Reuse the canonical Record/datatype vocabulary in goldenport and
  SimpleModeling. Inventory how Entity metadata and Cozy-generated declarations
  reach runtime; avoid introducing a competing application type system.
- A direct DataStore consumer receives physical values and may explicitly own
  its `toRecord`/`fromRecord` conversions. Consumers requiring automatic
  application reconstruction use an explicitly bound OR-mapping path; do not
  insert that reconstruction back into the low-level provider. An
  EntityStore-only repair cannot close this Phase.
- Select the producer and downstream repository/path scope at PLAN. The required Cozy/model producer work is allocated to `asami/cozy` Phase 78. DSM-100-02 freezes the producer-facing logical datatype/storage-mapping contract and expected fixture; Cozy Phase 78 implements CML -> semantic datatype -> generated Scala/schema/storage metadata; DSM-100-03/04 consume that output; DSM-100-06 is the downstream acceptance authority. This handoff is not permission for either repository to edit the other's owned implementation.
- workbench supplies the concrete failing consumer scenario. Durable Job and
  quota/canonical-text consumers supply compatibility regressions, including
  opaque IDs and exact String content.
- Respect ongoing Phase 69.7 and other local work. Capture the actual accepted
  prerequisite revisions and retained temporary safeguards before replacing
  them; this allocation does not change their frozen acceptance scope/status.
- This is independent of Phase 94's shared transaction redesign and Phases
  95/96's UI contracts. Existing transaction, OCC and ownership guarantees are
  preserved; no new shared transaction domain is required.

## Transition and recovery requirements

Inventory every affected collection before changing defaults. Specify an
explicit compatibility/activation matrix for mapped and not-yet-migrated
consumers. Missing mapping in a typed operation must produce a defined outcome;
it cannot trigger content-based type inference or silently change an old caller.
Any temporary legacy mode must be explicitly selected, isolated, observable,
and have a recorded removal criterion. It is not the final typed default.

Classify existing data using trusted application declarations and provenance.
Unchanged JSON-in-TEXT bytes may need only metadata adoption; changed storage
formats need a versioned migration. Valid JSON syntax alone cannot distinguish
a String from a serialized object. Ambiguous or malformed records are reported
for explicit disposition without guessed types, silent coercion or overwrite.

Rehearse backups, dry-run inventory, per-collection cutover, interruption/resume,
idempotent retry and restoration on isolated copies. Distinguish metadata-only
rollback from rewritten-data recovery. If earlier conversions lost formatting
or information, report the limitation and require retained originals/backups;
do not promise reconstruction of lost bytes. Live data changes require the
separately selected target, authority and operational procedure.

## Acceptance

The linked checklist is the closure authority. Required evidence includes:

- declared String stays String even for valid JSON object/array syntax;
- declared X, semi-structured Record, JSON and array roundtrip through the OR
  mapper, SQL and process restart to their distinct application types;
- physical DataStore text reads remain String; JSON parsing and X/Record
  reconstruction are independently tested OR-mapper stages;
- formal semi-structured/JSON datatype identities survive declaration,
  generation, schema metadata and OR-mapper binding; the accepted route no
  longer emits or depends on `DataType.Named("record")` or an equivalent
  name-only workaround;
- `XSemiStructuredData` admits the declared JSON/YAML/HOCON representations
  through explicit format codecs and normalizes them to Record; `XJson`
  retains JSON values, independently of the selected DB storage format;
- create/save/update/load/search/projection and authoritative mutation readback
  agree on the same mapping, including direct and EntityStore consumers;
- the mapped API over in-memory and SQL providers agrees on declared logical semantics; existing
  revision, identity, tenant, transaction and partial-update behavior is retained;
- unknown/incompatible/malformed data yields structured, attributable failures;
- existing canonical text and opaque IDs remain exact, and current temporary
  exception fixtures are replaced only by proven declaration-based coverage;
- migration/recovery succeeds on retained fixtures and exposes unresolved data
  without destroying it;
- the selected workbench scenario and durable consumers pass focused regression,
  required full-suite/downstream validation and independent review.

## Non-goals

General ORM replacement, arbitrary JSON-path querying/index optimization,
distributed/shared-transaction redesign, a new Record implementation, automatic
type guessing for historical rows, or unapproved production data rewriting.

## References

- [Logical type mapping contract proposal](../spec/datastore-logical-type-mapping.md): DSM-100-02 behavior and fixture inputs; implementation and exact producer ABI remain pending.
- [Source inventory](../journal/2026/10/2026-10-05-phase100-source-inventory.md): selected branch evidence and canonical source owners.

- [Allocation and evidence](../journal/2026/10/2026-10-03-datastore-logical-type-mapping-phase.md)
- [DataStore and Aggregate persistence model](../design/datastore-and-aggregate-persistence-model.md)
- [SimpleEntity storage-shape contract](../spec/simpleentity-storage-shape-policy.md)
- `src/main/scala/org/goldenport/cncf/datastore/DataStore.scala`
- `src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStoreRecordCodec.scala`
- `src/main/scala/org/goldenport/cncf/entity/EntityStoreRecordProjection.scala`

During DSM-100-02, update the affected canonical spec/design documents and link
the executable specifications there. This work plan is not a substitute for the
frozen public contract or proof that the planned behavior already exists.

## Availability / fallback compatibility handoff

Textus Availability Architecture and Phase 108 introduce a Deployment
Transaction with a configurable Fallback Retention Window. DataStore evolution
must preserve the semantic truth of that promise.

A new CAR may switch to probationary Active only with declared/admitted
DataStore compatibility appropriate to its release policy. While the previous
CAR is advertised as fallback-ready, migration must not perform a destructive
contract step that prevents that retained CAR from operating on authoritative
state.

Plan/follow-up implementation should support expand/migrate/contract-style
evolution where the logical/physical change requires it, and expose enough
compatibility state for release admission to distinguish switch-back-safe from
breaking migrations.

This requirement does not turn Phase 100 into a generic schema-migration
platform or require every logical-type change to use multi-step migration. It
adds the compatibility contract needed by immutable CAR deployment.

## DataStore ABI

The canonical contract is [Textus DataStore ABI](../design/datastore-abi.md).
Phase 100 logical-type declarations, storage mappings and existing-data
recovery are producer inputs to that ABI.

Implementation/planning must make compatibility assessable at the logical
persistent contract, not merely by comparing physical SQL schema. Relevant
changes include logical type/codec, required property/collection, storage
mapping and identity/key semantics.

Normal Update requires backward compatibility with the retained previous CAR.
A detected incompatible persistent change is not repaired by inventing generic
rollback machinery; it is classified as Maintenance Update and handed to an
explicit migration operation.

The earlier availability handoff's expand/migrate/contract mechanism is an
available technique for a migration that chooses to preserve compatibility, not
a mandatory ceremony for every Normal Update. The default Normal Update should
remain simple because DataStore ABI compatibility is already an admission
precondition.
