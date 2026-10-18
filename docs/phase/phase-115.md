# Phase 115 - Canonical Record Collection and Null Policy Adoption

Status: CLOSED
Owner: CNCF DataStore / EntityStore integration
Created: 2026-10-18
Checklist: [Phase 115 checklist](phase-115-checklist.md)
Producer: [goldenport-core Phase 3](../../../simplemodeling-lib/docs/phase/phase-3.md)

## Goal

Adopt Core's normalized Record contract through CNCF's actual entity persistence
route: NonEmptyVector values are collections, null-valued attributes are absent,
and empty objects/arrays remain present. Preserve explicit SQL clearing and the
separate raw-JSON contract. Implement one entity save/read/update route before
expanding to other affected consumers.

## Relationship to existing work

[Phase 100](phase-100.md) owns the declared logical-type/physical-storage mapping
foundation. Phase 115 is its separately accepted semantic migration, not an
additional Phase 100 completion condition or a reopening of completed work.
Use the actually delivered Phase 100 APIs and the adopted Core Phase 3 result.
Planning may proceed now; dependent implementation uses those available
baselines without copying or replacing unfinished work.

The working [logical type mapping specification](../spec/datastore-logical-type-mapping.md)
R4/R7, [row mapping specification](../spec/entity-store-row-mapping.md) and
[persistence stages](../spec/entity-store-persistence-stages.md) now describe
Core Phase 3 normalization. Phase 100 test/review receipts remain historical.

Historical Main Step acceptance snapshot — 2026-10-18 (before consumer acceptance):

Accepted Step: **RCP-MAIN / RCP-MAIN-A** implements the connected typed entity
save/read/update/search route and SQL ingress adoption. Selected Core input is
`simplemodeling-lib` commit `53b1612faadf43840a75cf7d1c6d54ea5f248015`;
original CNCF base is `11d4e2843f03a787d5a4f5900e97ed04e4b63276`.
Focused validation passed: 12 representative examples and 39 affected regression
examples across five suites. RCP-MAIN passed independent protected focused Step
acceptance. Consumer migration and final Phase acceptance remain pending.
All RCP-01 through RCP-05 remain OPEN. Generated ordinary Vector/NonEmptyVector
signature evidence and foreign bare-null patch caller migration belong to
RCP-CONSUMERS and are not established by this Step.

Accepted connected Step: **RCP-CONSUMERS — focused-validated; independent complete protected
focused Step review PASS; all four local commits complete**. The connected Step now spans Core,
model, SimpleModeler and CNCF. Core owns generic external conversion, strict
collection readers, canonical Record normalization and the distinct raw Json
contract. The model library owns explicit model-group, nominal scalar,
Update and Condition projections. SimpleModeler emits thin calls selected by
declared type, multiplicity and aliases. CNCF retains storage/update policy
and foreign-input admission; no duplicate Record normalizer or generated-source
hand edit is introduced.

The ordinary CML fixture and
[Phase115GeneratedCollectionPolicySpec](../../src/test/scala/org/goldenport/cncf/entity/Phase115GeneratedCollectionPolicySpec.scala)
exercise generated Vector/declared NonEmptyVector through memory and test-owned
SQLite create/save/load/update/search and reopen, plus actual ordinary
`Query.createC`/Condition application reconstruction and generated `ScalarCode115`
DATATYPE scalar projection. CountryCode/PostalCode VALUE fixtures are not
nominal-scalar evidence. Search containers/page metadata retain their existing
semantics. Raw Json nulls remain document values; SQL clear requires the existing
explicit instruction. HTTP, Durable Job, Information, Blob/Media and native
consumer migrations have selected focused evidence below.

### Selected focused validation — 2026-10-18

These are the selected command results, not an additive test total. Earlier
failed invocations remain failed; later commands validate their affected repairs.
Detailed execution evidence stays in the private Phase run.

| Selected scope | Actual result |
| --- | --- |
| Core conversion repair | 79/79, six suites |
| Model conversion repair | 54/54, ten suites |
| Modeler delegation repair | 33/33, three suites |
| Forced model regeneration | Actual 11 regenerated/compiled thin delegates inspected; 54/54, ten suites, overlapping model coverage |
| Consumer A library integration | 11 representative plus 216 regressions = 227/227, eight suites |
| Original Consumer B1 | Representative 17/17; subsequent regression aborted with exit 255, no final regression total |
| Consumer owner validation | Information 17/17 in three suites and decoder 52/52 passed; the command still failed five HTTP oracle cases |
| Consumer owner continuation | 106/106 in nine suites: HTTP 19, generated 15, six HTTP regression suites 71, native quota witness 1 |
| Native create repair | 61 run, 60 passed, one RP12 producer-wire expectation failed; all original quota bootstrap failures and three measured/direct quota routes passed |
| Native wire repair | Affected payload suite 14/14: original 12 plus two retained V1/V2 cases |
| Model presentation repair | 56/56, ten suites: 26 model conversion specification cases including two added precedence scenarios, plus original nine suites with 30 cases |
| Consumer presentation integration | 44/44, five suites; actual generated Information external and stored status are selected strings |

The parent readiness cross-check found generated Powertype/StateMachine values
with competing Record presentation. The model adapter now selects their existing
`Presentable.print` scalar meaning before generic RecordPresentable, while
NominalScalar remains first and database `dbValue` remains storage-specific.
Throwing or null prints fail without Record fallback. The tested model
0.2.2-SNAPSHOT local refresh succeeded before the actual generated Information
integration. Earlier 54/54 model and forced-regeneration results remain historical,
overlapping scoped evidence; these counts are not summed. This correction adds
no Core, generator or application registry, and changes no storage/wire contract.

The original B2 command also passed decoder 52/52 before five HTTP oracle
failures; its generated and HTTP regression scopes had not run. Its decoder
coverage overlaps owner validation. The original native regression's 58 run,
33 passed and 25 failures narrowed to missing `createEntity` forwarding in two
measured test wrappers. The repair and three-route witness preserve genuine
native SQL and write counts. The unchanged original List golden is retained;
the current producer expectation explicitly uses Vector. Actual old-list native
reads yield Vector while physical bytes, rows and quota remain unchanged.

KnowledgeNodeId/RdfNodeName now use existing NominalScalar and
ExternalKnowledgeIdentifier/KnowledgeEntityBinding use existing RecordPresentable
APIs. Their established scalar/Record wire remains; Core gains no application
classes. All selected dependency local refreshes succeeded at unchanged
development coordinates: Core 0.4.3-SNAPSHOT, model 0.2.2-SNAPSHOT,
Modeler 1.1.26-SNAPSHOT and CNCF 0.5.3-SNAPSHOT. No public publication is claimed.

RCP-MAIN's accepted local commit
`f7af5c79c50f93a6f36f63b1af86cc0086c9d157` and 51/51 history remain unchanged.
All five RCP-01 through RCP-05 obligations are DONE and Phase 115 is CLOSED
under the final acceptance below. Both connected Steps retain independent
protected focused acceptance and local commits. Existing `HYG-CNCF-P115-001`
and `HYG-CNCF-P115-002` stay separate and nonblocking; no successor is activated. The
[adoption journal](../journal/2026/10/2026-10-18-phase-115-record-policy-adoption.md)
records producer ownership and retained-value handling.

### Accepted connected Step commits — 2026-10-18

The complete RCP-CONSUMERS Step passed independent protected focused review and
was committed locally in dependency order. These are Step acceptance commits;
the final Phase acceptance below completes all five obligations.

| Repository | Accepted local commit |
| --- | --- |
| simplemodeling-lib | `51b3a95a8e4048125579e1f0b650cfb784d7ccf4` |
| simplemodeling-model | `b638ba27e269f25e489f1d67a57dc7a479b15d15` |
| simple-modeler | `52aea03a624d723213c2cf54ef18a67fc7e56a31` |
| cloud-native-component-framework | `f4513e79d7ea1dcc3493145538c8f9a9189c9130` |

## Final Phase acceptance — 2026-10-18

Status: CLOSED. All five RCP-01 through RCP-05 obligations are DONE. Closure
takes effect through the local release commit containing
`phase115-final-closure-20261018-01@1`. RCP-MAIN and the connected RCP-CONSUMERS
Step each passed independent protected focused acceptance and were committed
locally; their native commit identities above are retained.

The sole original comprehensive Phase review passed. The Modeler repair1
focused closure and combined repair2–6 independent focused closure also passed;
these continue that original review rather than adding another full review.
The original Goal, authority, start, binding and epoch remain unchanged, with
Step repair count 9, Phase repair count 6, full-review count 1 and consecutive
nonconverging count 0.

Selected final repository-full gates all passed. Each row is its own actual
result; overlapping focused/full scopes are not summed.

| Selected full gate | Completed suites | Succeeded | Failed | Canceled | Ignored | Pending |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Core, repair2 | 159 | 602 | 0 | 0 | 0 | 125 |
| model | 31 | 123 | 0 | 0 | 0 | 0 |
| Modeler, repair1 | 21 | 118 | 0 | 3 | 0 | 0 |
| CNCF, repair6 | 824 | 6716 | 0 | 5 | 1 | 46 |

All four selected gates had zero aborted suites. Existing canceled, ignored and
pending cases remain explicit limits. Earlier Core full validation passed
601 tests in 158 suites with 125 pending; the later selected gate includes the
Conclusion regression. Original Modeler full validation remains a failure
(117 succeeded, 1 failed of 118, 21 suites, 3 canceled). Original CNCF full
validation ended with exit 255 and no final counts. CNCF full repair4 remains
6707 succeeded, 9 failed, 824 suites, 5 canceled, 1 ignored and 46 pending;
it is not a successful gate. Later repair5 focused validation remains 66/67,
with its startup failure followed by the accepted repair6 expectation and
successful selected final full gate. Earlier failed/unknown invocations and
all historical focused results below retain their original outcomes.

Final adoption repairs retain existing contracts: Modeler's delimiter oracle
was corrected without changing generator behavior; known Option producers use
existing `Record.dataAuto`; accepted-job presentation uses raw Json to retain
explicit `data:null` and original metadata. `Conclusion.simple` supplies one
original Message facet. Typed fixture identifiers use `id.print`, and controlled
YAML timestamps are quoted. The Admin representative Option producer and old
Some(None) oracle follow canonical omission. Six existing specifications
adopt canonical null, limit and structured-error expectations; two working
specifications clarify those existing contracts. Startup import regression
observes the existing SystemCorrupted/IllegalStateException wrapper and inner
format-error marker, without changing the production wrapper.

This is a source-only Workbench migration with no existing Workbench database;
test-owned retained values establish read normalization and subsequent saves.
This closes Phase 115 only. Whole Workbench migration and successor execution
are not accepted here. `HYG-CNCF-P115-001` and `HYG-CNCF-P115-002` remain OPEN,
nonblocking, in the separate canonical Hygiene journal. No live rewrite, push,
public publication or deployment is part of this closure.

## Required behavior

- Adopt the [collection representation rule](../rules/collection-and-semi-structured-data.md).
  Public business contracts and generated ordinary multiple-value attributes
  use Vector; declared nonempty values use NonEmptyVector. Internal utility
  input abstractions may express required capabilities without changing that
  representation. Preserve existing search containers and page metadata, empty
  search results and explicit query-order semantics.
- Reassess affected List/Seq declarations and specify model/generator/caller
  migration with the producer owner. Do not add List restoration for historical
  compatibility alone. Keep genuine cons/stack or external-library uses scoped
  to their declared purpose; do not perform a repository-wide token replacement.

- Native null is forbidden in normal application/framework semantic logic.
  JDBC/provider/foreign-API input adapters must convert it to absence, an
  existing typed value or a Consequence failure before entering that logic.
  Output adapters alone materialize native null if the external API requires
  it. Internal optional values use Option; update instructions use their
  existing explicit types, never null sentinels.
- Consume Core's NonEmptyVector support; do not convert collections to Strings
  or implement a second CNCF-owned Record normalizer.
- Normalize Record-valued attributes recursively: omit null-valued properties,
  preserve empty Records/arrays and retained property/element ordering.
- Restore ordinary untyped arrays as Vector. Reconstruct NonEmptyVector only
  where the declared application type/cardinality requires it; reject empty
  values through the existing typed failure route.
- SQL NULL read into an optional semantic Record field yields an absent
  attribute. Required fields still use their established required-field error.
- Missing patch field remains no change; explicit Update.SetNull remains SQL
  clearing. Omission never becomes an implicit clear. Keep the established
  bare-null patch behavior and validate/decode the instruction before any
  semantic Record normalization could discard it.
- Distinguish physical provider rows, update instructions and semantic Record
  values. Audit Field-vector/createFull paths carrying SQL NULL: preserve the
  physical operation through existing provider/update contracts without
  reintroducing stored null properties into the semantic Record model or
  transporting native null through internal row-mapping stages. Translate
  provider null at ingress and explicit SetNull at the final output boundary.
- An empty Record field persists as a present structured `{}`; an empty array
  persists as `[]`. Neither is SQL NULL or an omitted field.
- XJson and present Json.Null retain their JSON value semantics; String
  `"null"` remains text. Do not apply Record omission to raw JSON documents.
  Json.Null is a typed syntax node, not permission to propagate native null
  or use a JSON node as an internal missing-value sentinel. Decode JSON into
  the declared application value/absence before semantic use.
- A null element in a Record-owned sequence fails conversion without changing
  positions. Raw XJson arrays may still contain Json.Null.

## Main acceptance route

Use one representative entity with optional text, a Record-valued payload,
an explicitly declared nonempty collection and a raw XJson field.

1. Admit external input with nested null attributes, an empty child, an empty
   array and NonEmptyVector records. Normalize at the input boundary, then save
   through the ordinary entity/storage mapping route with no native-null values
   in the semantic payload.
2. Read it back and verify normalized values and order, retained empty structures,
   and a nonempty typed result where declared. Verify raw Json.Null separately.
3. Patch an unrelated field and verify omitted fields remain unchanged.
4. Apply Update.SetNull to an optional field, read again, and observe an absent
   semantic attribute. Check required-field failure through the same route.
5. Check empty nonempty-collection input and null sequence elements fail without
   silent defaulting, filtering, stringification or weakening of cardinality.
6. Search zero and multiple matching entities through the existing result
   container. Verify Vector bodies, retained metadata, declared ordering and
   duplicate preservation in ordered attributes. Check generated attribute
   signatures and their reconstruction together; required producer changes
   are an explicit handoff, not edits to generated sources.

## Work plan

1. **RCP-01 - Adopt the producer contract.** Select Core Phase 3 and the actual
   Phase 100 mapping baseline. Update the affected CNCF working contracts and
   define the representative integrated executable specification.
2. **RCP-02 - Complete the main route.** Connect storage and reconstruction to
   the Core conversion and existing row/update stages. Establish the six
   acceptance steps together, rather than reviewing isolated helper changes.
3. **RCP-03 - Migrate affected consumers and documentation.** Review actual
   Record-valued DataStore/EntityStore/Blob/Information/Job usages selected by
   the changed contract. Preserve unrelated behavior; do not refactor every
   Map/List occurrence. Update the collection rule and examples to describe
   intentional null normalization rather than universal lossless retention.
   Replace affected normal-logic null sentinels/checks with existing typed
   contracts and boundary conversion; do not add checks throughout the stack.
4. **RCP-04 - Existing-value handling.** Exercise representative retained JSON
   text containing null members/empty structures on a test-owned store. Specify
   read normalization and subsequent save behavior. Keep XJson data unchanged;
   do not rewrite live databases or introduce an automatic bulk migration.
5. **RCP-05 - Integrated acceptance and release.** Run the relevant mapping,
   entity-update and actual consumer regressions, independent review, and
   repository-required final validation. Record the selected dependency and
   separate consumer handoff before closure.

## Boundaries

Core owns collection/Record meaning; CNCF owns provider mapping and update
instructions. SimpleModeler/Cozy generated contracts are inspected as consumers;
any producer change actually required is assigned to its owner rather than
patched into generated sources or silently added here.

Preserve authorization, native concurrency/revision rules, managed fields,
ContentBody boundaries and exact numeric values. Add no hashes, snapshots,
TTL gates, repeated boundary checks or compatibility branch retaining the old
canonical null-preserving Record policy. Existing data is read through the
new declared normalization contract; raw JSON stays raw JSON.

This Step authorizes no live data migration or remote publication. Core Phase 3 can close independently after its usable
handoff; CNCF Phase 115 closes only against its own checklist and evidence.
