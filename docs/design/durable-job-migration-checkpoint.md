# Durable Job Migration Checkpoint Design

status=selected-validation-passed
review_status=independent-complete-Step-review-pending
phase=69.7
step=JM69-09
slice=JM69-09D2
manifest=PHASE-69.7-migration-checkpoints-implementation-revision1
document_revision=1

The [normative contract](../spec/durable-job-migration-checkpoint-contract.md) owns M1..M16. These decisions are frozen for the explicit native checkpoint boundary. The original value codec validates and signs an unchanged V1 body into V2; the original Store requires a contiguous semantic revision before its optimistic native checkpoint. The migration owner therefore first performs value migration, then signs the same body with revision advanced by one. Created/updated times, TaskIds, descriptors, authorization, legal hold, history and payload bindings stay unchanged.

## Preparation and ownership

The original provider enters the engine startup barrier and holds its actual SystemNode binding lease. Capability probes precede the one trusted source fetch. A default-false internal `migrateV1OnStartup` input adds no configuration or discovery surface. Only a trusted provider can explicitly enable it with an independently constructed Operator context. Default and explicit false retain the original fetch, bridge bind and recovery order (M1, M2, M3, M11).

The helper admits the whole cached vector through the original coordinator and original 512-byte ID bound. It freezes source snapshots, destination values and footprints in candidate order. Every candidate must pass native canonical access, Active retention, format, original terminal projection for V1, pre-existing Published lifetime authority and physical payload verification before any checkpoint. V2 uses original footprint/prepare without a write (M4, M5, M6, M8).

`prepareMigration` authenticates exact V1 source storage and the genuinely Published lifetime snapshot. One factored pure guard checks destination executed/paid tasks, history, normalids, compensation claims, cancellation and remaining live credit. Existing `prepare` retains its V2 recoverySnapshot ordering and Prepared/Binding result; V1 returns Unit only. There is no reserve/recover/publish root, reconstructed payment or new lifetime root (M7, M12).

One operation-local byte budget consumes original owned Blob metadata and strict Published ledger proof. Its whole-target reservation includes a sentinel per distinct owned payload within each Job; separate Jobs are not deduplicated. Original 8 MiB payload / 16 MiB aggregate bounds, 8192-byte streaming buffer and finally-close remain. Physical evidence is point-in-time, with no second stream after checkpoint and no atomic backup certificate (M8).

## Sequential checkpoint and ambiguity

After complete preflight, each V1 source is reread for exact record and provider revision. The original Store checkpoint is called once, preserving its storage quota reserve/confirm and Optimistic/ObservedRequired policy. Acknowledged destination must equal the prepared value, then pass original V2 footprint/prepare and final exact startup load. No retry or lost-ack reconstruction occurs (M9, M10).

A race, failed suffix, ambiguous acknowledgement or failed post-proof returns the fixed migration refusal. Genuinely committed prefix/current records and conservative storage reservations remain. There is no batch transaction, rollback, refund, success report or paid reconstruction. A fresh explicitly enabled attempt reads actual V2 and validates its authority without a second migration checkpoint (M10).

## Original startup and shutdown

Only complete migration success permits original bridge binding and full terminal/runtime recovery from the same cached vector. Both recovery passes must independently satisfy original registration before the final engine Ready/scheduler publication. Source, physical stream, migration and recovery have no new enclosing monitor. The original real lease keeps the node-owned pool alive during drain; original close flags prevent final Ready. Failure retains native writes and invokes existing owner stop, lease/gate close and admission failure cleanup. Interrupt restores the flag and refuses; NonFatal failure text is redacted (M2, M11).

## Traceability and remaining work

MC1..MC10 and MI1..MI6 are the sixteen native executable examples linked by the contract. Their new fixture composes actual managed runtime/integrity fixtures, positive roots use original reserve/create/publish authority, and fault SQL is confined to deliberate negative corruption. MC10 and MI6 shrink ordinary seeded 24-case native properties (M12, M13).

At initial authoring, specification/design preceded code. Runtime owner R3/R7 delimit opt-in preparation without changing the original barrier. The migration journal records the codec/checkpoint gap, paid ownership, partial refusal semantics and [current D2 selected-validation-passed evidence](../journal/2026/10/2026-10-04-phase-69.7-migration-checkpoints.md#actual-d2-selected-validation-and-retained-execution-history). Independent complete-Step review remains pending. D1C selected-validation-passed / independent-complete-Step-review-pending status is a historical receipt projection, never migration validation (M14, M15).

JM69-09 remains IN_PROGRESS and JM69-10 OPEN. All 17 native correlations, real backup/restore, remaining C/D security/retention/hostile fixtures, both CPBs and unwaived M2 require original complete-Step review. Actual two-process JM69-10/CBD acceptance, 69.4–69.6 ancestry audit, independent Step reviews/local commits, aggregate final/downstream validation, sole epoch-1 full-Phase review and final local version/release remain required. Original goal/base/binding/ledger and Step 1 / Phase 0 repair counters remain unchanged (M1, M16).


## Historical D1C evidence boundary

D1C is selected-validation-passed / independent-complete-Step-review-pending:
REP-005 completed 14 suites/132 successes/zero failures/pending/warnings; ACC-001
completed 90 suites/728 successes/zero failures/warnings and one original pending.
Both reader/SBT/wrapper exits were 0 and final serial locks released. Exact actual
result/log/summary paths and the retained REP-001 23 compile errors, REP-002
119/13, REP-003/004 131/1, all four frozen FIX manifests, parent one-token DIRECT
correction, predecessor three/successor two attempts and
DVL-PH697-D1C-START-FIELDS-01 are projected in the
[integrity journal](../journal/2026/10/2026-10-03-phase-69.7-integrity-operations.md).
Those immutable integrity subjects are not D2 validation. Step1/3, Phase0/3,
epoch1, pending-not-waived M2 and both native CPBs remain; no original Step or
Phase acceptance is claimed.
