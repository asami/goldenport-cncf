# Phase 115 - Canonical Record Collection and Null Policy Adoption

Status: IN_PROGRESS
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

Current Step: **RCP-MAIN / RCP-MAIN-A** implements the connected typed entity
save/read/update/search route and SQL ingress adoption. Selected Core input is
`simplemodeling-lib` commit `53b1612faadf43840a75cf7d1c6d54ea5f248015`;
original CNCF base is `11d4e2843f03a787d5a4f5900e97ed04e4b63276`.
Focused validation passed: 12 representative examples and 39 affected regression
examples across five suites. RCP-MAIN passed independent protected focused Step
acceptance. Consumer migration and final Phase acceptance remain pending.
All RCP-01 through RCP-05 remain OPEN. Generated ordinary Vector/NonEmptyVector
signature evidence and foreign bare-null patch caller migration belong to
RCP-CONSUMERS and are not established by this Step.

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
