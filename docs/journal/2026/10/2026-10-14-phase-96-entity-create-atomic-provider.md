# Phase 96 strict Entity create atomic-provider contract

Document role: October 14, 2026 history and intentional fixture sample choices.
This entry introduces no normative behavior and records no successful product
implementation, validation or acceptance. The
[specification](../../../spec/entity-create-atomic-provider.md) defines intended
strict-create behavior; the [design](../../../design/entity-create-atomic-provider.md)
records settled ownership and algorithms.

## From reproduction to settled contract

P96-07B2B1 introduced the preserved canonical create failure-safety reproduction
frontier documented in the
[earlier journal](2026-10-14-phase-96-entity-create-failure-safety.md). Its actual
independent observations established two product failures: a duplicate candidate
could alter an existing overflow body, and fresh root rejection after a side
save could leave an orphan body. Narrow fixture corrections made canonical
scenarios reachable without weakening their desired preservation laws. Failed
property examples remain counterexamples; they do not establish passing PBT.

The current canonical create source prepares ContentBody effects eagerly before
a separate generic root create. The parent therefore settled absent-root
creation as one real provider-owned atomic operation with pure canonical
preparation, explicit capability and same-provider admission, authoritative
stored readback, rollback and indeterminate-acknowledgment distinctions.
Existing generic create, upsert and guarded successor contracts were not
reinterpreted as this strict-create operation.

P96-07B2B2A records that settled contract as specification and design, under the
original PHASE-96/P96-07 authority. The selected corrected manifest fixes the
future protected hook label to `create_EntityCheckpoint` in correspondence with
the shared override-expected naming rule; public createEntity and public/protected
parameter labels retain camelCase. The previous terminal no-edit manifest is
historical. That label correction changed no existing source or behavior.

## Intentional fixture samples

These are development fixture roles, not production identifiers or execution
results. The existing
[create fixture](../../../../src/test/scala/org/goldenport/cncf/entity/EntityCreateFailureSafetyFixture.scala)
reuses canonical registered collections, real providers, resident state and View.

| Sample role | Intentional value or interpretation |
| --- | --- |
| Selected existing Entity | Existing managed revision 7; duplicate attempts target its canonical identity and preserve its complete root/body maps. |
| Fresh Entity | Typed CanonicalId with fixture-local `freshcreate`, in the same admitted collection and distinct from seeded identities. |
| Embedded | Framework-managed physical `revision`, initialized to INITIAL1 for successful fresh creation. |
| Detached | Explicitly admitted non-SimpleEntity with managed physical `cncf_revision`; domain data does not become the framework revision. |
| Overflow input | Default overflow-sized canonical candidate text; pure preparation supplies its exact side Save collection/EntryId/Record. |
| Inline input | Small candidate content; the prepared root remains inline and the policy supplies its typed side Delete. |
| Absent input | No content field; pure preparation retains the existing policy's root shape and side Delete. |
| SetNull input | Explicit Update.SetNull content marker; policy prepares cleared root content and side Delete. |
| Unrelated retention | Two seeded overflow entities contribute their complete root/body maps to the preservation oracle. |

Both managed representations keep their assembled policy and unchanged fixture
grants. Root-prewrite and side-prewrite fault samples select exact targets from
the pure prepared plan. Future adapters use the actual typed provider checkpoint;
they retain side-before-root rejection observations and full-state comparisons
while replacing generic create/save interception for strict create.

## Internal sequence and retained work

The parent selected A then B: P96-07B2B2A is class-D contract documentation;
P96-07B2B2B is mandatory real provider implementation planning. The next executable
PLAN must inspect target programs and consumers and freeze exact atomic port,
DataStoreSpace, built-in memory, SQL, canonical create, actual fault fixtures,
admission and MySQL paths before implementation, focused execution and independent
conformance. Documentation alone does not establish provider safety.

All four original
[P96-07 criteria](../../../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN, and both duplicate-body corruption and fresh-root orphan blockers
remain unresolved. Original Slice C retains actual registered Entity AND
Aggregate, ordinary mutation routes, committed-outcome acquisition and selected
contextual afterCommitResponseC reload/projection without replay. P96-08 through
P96-11, original whole CNCF96 and studio Phase 2/app Phase 5 obligations, final
validation/review/release and original repository bases/history remain required.
The existing reproduction files, checkpoint ef12a02 and launcher are preserved.
