# 2026-10-17 — Editing Studio reference authoring

Document role: non-normative sample, provenance and authoring history.
Status: authored/unvalidated. No command execution, focused PASS, review,
Step acceptance, Phase acceptance or application acceptance is recorded here.
The frozen P96-09A implementation manifest is
`phase96-p96-09-reference-implementation-manifest-001@1`; its Step input is
`3ee7bdc1607e977060f64f9b051789430788ad20` on
`codex/phase-96-display-service`. Original Phase authority, base, epoch and
ledger-37 accepted predecessors remain unchanged.

## Declared sample and parameterized trace

The [fixture contract](../../../spec/display-editing-studio-reference.md) and
[design](../../../design/display-editing-studio-reference.md) describe only
the generic development fixture. It consumes admitted read, mutation, lifecycle
and ordinary Business APIs. ESS2-01's earlier
[sample](../../../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md)
remains a non-normative application proposal; this authoring does not promote
it to production semantics.

For a session's declared collection `EntityCollectionId("sys","sys",token)`,
the existing canonical fixture independently declares these actual typed IDs
before requests:

- selected: `EntityIdFixtureBridge.fromParts("normal", "selected", collectionId, entropy="same_selected")`;
- unrelated: `EntityIdFixtureBridge.fromParts("normal", "unrelated", collectionId, entropy="same_unrelated")`.

`token` is the existing per-session namespace. The parameterized traces below
refer to the declared IDs' exact `.value`, never to `capture:book-1`, an inferred
ID grammar or claimed literal cross-run equality. These are authored expected
traces, not captured execution output.

| Position | Ordinary request or observation | Independently declared expectation |
| --- | --- | --- |
| Initial | query `normal-update-reference` / `capture-list` | selected and unrelated, ascending actual ID values; revision 1; seven values; no actions/snapshot/eager Detail |
| Select | decoded selected item ID plus decoded logical target -> `display.read` | same selected resource, `Before`, `retained 日本 Ω`, draft, revision 1 |
| Update | `display.update`, actual returned ID/revision, scoped raw title/description | title `Updated:日本 Ω\n\u0000`, description `日本 Ω\n\u0000`, draft; actual Updated receipt/fresh Detail at 2 |
| Explicit List | `display.query` only | selected exact updated text and revision 2; unrelated unchanged |
| Equal/stale | actual current observation, then independently old revision 1 with equal text | current no-op at 2; stale structured conflict; full durable state unchanged |
| Business | direct `business.requestReview` with actual current Detail ID and String revision `2` | ordinary response outcome `review-requested`, selected ID and numeric revision 3; no Display/find/query |
| Explicit reloads | `display.read`, then `display.query` | exact supplied title/description, raw `review-requested` stage, revision 3; old JSON unchanged |
| Separate Crud | declared selected absent -> supplied create -> Detail/List -> Business -> reload -> observed delete -> List | actual Created ID equals declared selected before read; revisions 1 then 2; delete returns inert reload-list; explicit List shows absence |
| Read failure | committed update projection or later explicit Business read fails, then only explicit read retries | committed state/response retained, actual provider attempt remains one; no writer replay |

The deterministic generated control starts with `Initial:\n\u0000` at selected
revision 16, applies `Updated:\n\u0000` and raw description, then reaches 17
and 18. The literal notation above escapes newline/NUL for readable sample
history; executable inputs contain the actual code units.

## Every ESS2-01 difference has explicit provenance

| Earlier ESS2-01 proposal | Generic CNCF reference authored here |
| --- | --- |
| `collecting-reference` selector | unchanged base selector `normal-update-reference` |
| `capture:book-1` / `capture:record-2` | independently declared canonical typed selected/unrelated IDs in a per-session collection namespace |
| `日本の民話` / `地域資料目録` and proposed descriptions | initial `Before` / `Unrelated`; description `retained 日本 Ω`; raw generated/sample update values explicitly supplied |
| Collecting Resources / Capture Detail and three projected fields | Normal list / Normal update, seven ordered existing fields: title/description/number/flag/instant/nullable/stage |
| workflowState `collecting` / `review_pending`, mapped Japanese stage labels | semantic/read-only raw stage `draft` / `review-requested`; no workflowState or label inversion |
| source revision changes definition provenance | constant immutable producer `cncf.development.editing-studio-reference`, profile source ID, revision `fixture-1`; actual managed Resource revision remains independent |
| snapshot-1 / snapshot-2 | absent optional List snapshot |
| action removed after review | fixed displayed Enabled Request review action remains after transition |
| Unicode-whitespace nonblank title contract | existing `String.trim` constraint; description may be empty, raw text is not normalized |
| Business identity-only input, revision proposed as transport-only | existing ordinary Operation String Properties `resourceId` and canonical positive `revision`; standard mutation still uses its existing observation metadata |
| first slice supports read/query/update only | exact UpdateOnly profile; separate explicitly selected Crud supports all five capabilities |
| implicit server application semantics and production path remain unadmitted | normal generic development component, real canonical persistence, controlled owner context, no production Studio authority |

The normal six typed editable bindings and readonly stage reuse the existing
converters and constraints. List is action-free; Detail alone retains the
Business descriptor. This provenance records differences rather than silently
claiming ESS2-01 literal equivalence.

## Scenario and original criterion correspondence

| Original P09 criterion (all remain unchecked) | Authored executable source |
| --- | --- |
| Declared development-only resource/values/revisions/bindings/constraints/availability and explicit ESS2 differences | [REF-E1/E5/E6](../../../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferenceSpec.scala), this declaration/difference record, contract R1/R2/R3/R5/R7 |
| Semantic List -> service -> selection/Detail -> update -> canonical mutation -> refreshed Detail | [REF-E1/E2/E3](../../../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferenceSpec.scala), [REF-P1/P2](../../../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferencePropertySpec.scala), R2/R3/R4/R6 |
| Create/Detail/List, delete/List, ordinary direct Business/reload with failures | [REF-E3..E8](../../../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferenceSpec.scala), [REF-PG1..PG3](../../../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayEditingStudioReferenceAcceptanceSpec.scala), R4..R8 |
| Generic server proof without product/Flutter/app acceptance claims | all three suites and contract non-goals; parent focused execution and independent acceptance still pending |

Native cases require managed PostgreSQL 17.11, durable settings, real provider
attempts, complete independent native tables/types/rows/body/security/audit/NULL
and finally cleanup. Portable execution cannot substitute. Parent-selected
representative validation is the two new portable suites; the accumulator
retains the prior forty suites plus these three, with native acceptance mandatory.
This record contains no execution logs or review report.

P09 is authored/in-progress; its four criteria remain unchecked. All 54 Phase
criterion texts and 41 checked/13 pending outcomes remain, P08 acceptance/history
is retained, and P10/P11 remain OPEN. Existing fixture-size debt
`HYG-P96-C4B-FIXTURE-SIZE-001` remains a separate follow-up. No application,
Cozy/parser dependency, public contract, context/Phase98, main reflection,
publication, deployment or successor phase work is included.

## Subsequent Step acceptance — 2026-10-17

The parent accepts the generic development reference on all four original
P96-09 criteria through independent whole-Step review
`phase96-p96-09-step-review-001@1`, admitted in review ledger revision 38.
The criterion/source correspondence above remains the basis; it is no longer
pending. P96-09 is CLOSED, with all 54 original texts preserved and 45 checked/9
pending outcomes. P96-10/P96-11 remain OPEN. Earlier authored/unvalidated and
unchecked statements in this sample record describe its initial authoring
checkpoint and are retained as history, not current status.

`HYG-P96-C4B-FIXTURE-SIZE-001` remains OPEN in its original canonical journal;
the reobservation creates no blocker, new finding or cleanup scope. No Phase,
production Studio, Flutter or app acceptance is inferred. The Step commit and
remaining Phase gates are separate. Detailed execution/review evidence stays
in private local workflow storage, not in this sample journal.
