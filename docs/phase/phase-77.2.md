# Phase 77.2 - Generic Skill Workflow Projection, Typed Protocol, and sm-workflow Handoff

status=closed
closed_at=2026-09-25
execution_priority=first_sm_workflow_vertical_slice
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-77.2
aggregate_validation_sequence=["PHASE-77","PHASE-77.1","PHASE-77.2"]
split_from=[Phase 77](phase-77.md)
predecessor=[Phase 77.1](phase-77.1.md)
planned_at=2026-09-21
depends_on=[Phase 77.1](phase-77.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-statemachine-api-spi-runtime-and-skill-driven-workflow)
checklist=[Phase 77.2 Checklist](phase-77.2-checklist.md)
consumer_handoff=[Released sm-workflow Handoff](phase-77.2-sm-workflow-handoff.md)
closure_journal=[Manual closure evidence](../journal/2026/09/2026-09-25-phase-77.2-manual-closure.md)

## Purpose

Phase 77.2 is the final child of the Phase 77 sequence. It consumes the
accepted `CWF77-RUNTIME` handoff to project externally claimable Continuation
work into Generic Skill WorkOrders, define the minimum typed
Start/Continuation/WorkOrder/Terminal protocol and schema-versioned fail-closed
Skill/Codex JSON encoding, prove the real Cozy fixture vertical slice, and
freeze the `sm-workflow` consumer handoff.

The first `sm-workflow` vertical slice is the priority. It uses Phase 77.1's
explicitly loose, post-commit Continuation baseline: external claim follows
successful continuation persistence, while WorkflowInstance, Continuation,
and UnitOfWork effects are not represented as one atomic commit. The durable
shared-transaction upgrade is [Phase 94](phase-94.md), outside this Phase's
entry and closure criteria.

Application-specific payloads and software-development policy remain owned by
`sm-workflow`. CNCF owns the generic Workflow envelope, identity, revision,
ContextSnapshot, Evidence, execution requirements, projection boundary, and
resume rules.

## Incoming authority handoff

Input: Phase 77.1's accepted canonical ActionExecution/ExecProgram/Provider
and durable Continuation-resume contract. Action: close Phase 77.1. Output:
the frozen Skill projection and reference-fixture execution boundary. Owner:
CNCF Phase 77.1. The handoff is invalidated by changed Action-outcome, Provider,
or resume-admission semantics; this Phase must stop instead of creating a
parallel protocol or Workflow runtime.

## Scope

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-07 | Project an externally claimable Continuation SPI request into Generic Skill WorkOrder semantics without making Skill canonical. | closed |
| CWF-77-08 | Prove the real Cozy fixture's internal -> suspended external -> resumed -> internal path through the IoC boundary. | closed |
| CWF-77-09 | Freeze reproducible CML-first evidence and the exact `sm-workflow` consumer handoff. | closed |
| CWF-77-10 | Define the minimum typed Workflow protocol and schema-versioned fail-closed Skill/Codex JSON encoding. | closed |

## Current implementation slice

`WorkflowProtocolV1` now defines a stable Handle, profile-owned typed Start
request, the closed Continuation forms, execution requirements, minimal
Presentation, and a claimed-Continuation-to-WorkOrder projection. The claim
token stays internal. A separate-turn `ContinuationResult` carries typed
result, ContextSnapshot, completion facts, and execution evidence; admission
checks it against the issued WorkOrder before the existing one-shot runtime
resume. `WorkflowWorkOrderJsonV1` and `WorkflowResultJsonV1` provide strict,
schema-versioned round trips for the external WorkOrder/result exchange.
Focused `WorkflowProtocolV1Spec` passed 3/3 using the serialized SBT runner
(`P772-PROTOCOL-FINAL-VAL-014`, receipt
`31efa482fbbb530227910b02f671ee3c205d8e09858548cf45d2581e38a34cc7`).

`WorkflowStartJsonV1` adds strict, schema-versioned wire
round trips for profile-selected `WorkflowStartRequest`. Its bound decode
rejects a Start Operation or Workflow identity/revision different from the
selected profile; unknown fields, schema, type, and payload shape fail closed.
`P772-START-JSON-VAL-015` passed 5/5 focused protocol tests with the shared
SBT lock released. Phase 77.1 is now closed; these focused receipts were
preparatory until the Phase 77.2 full-suite closure and do not create a public Start Operation.
The profile still owns payload/authority admission and transport mapping.

The CNCF test resource pins the actual Cozy 62.3 generated Workflow ABI
(canonical JSON body SHA-256, excluding the resource file's trailing newline:
`c3f11c6160691dc747ab9b614ab040cb288d25bd83196b205891e1b6e365c013`).
`GeneratedWorkflowAbiSpec` checks the declared Workflow, states, Actions,
Required SPI, and source locations against it; focused test
`P772-FIXTURE-TEST-001` passed 14/14 with the shared SBT lock released. A
subsequent focused test (`P772-FIXTURE-TEST-002`, 15/15, lock released) lowers
the four ABI-correlated Actions into an ExecutionPlan through ComponentFactory:
BuildProject and RunTests complete, the bound ReviewChange Provider suspends,
and a typed WorkOrder/result round trip resumes CommitChanges in a fresh
UnitOfWork. The internal Action programs in this test are fixture test programs;
it does not claim generated executable code or a production progression engine.
The next focused test (`P772-FIXTURE-TEST-003`, 15/15, lock released) persists a
WorkflowInstance suspension before publishing the Continuation, stores the
issued WorkOrder, rejects a Result when the saved suspension is absent, and
resumes CommitChanges once the saved boundary is restored. This preserves the
explicitly loose post-commit transaction boundary. The public Service test now
uses the same four ABI-correlated fixture test programs: an explicitly declared
Start runs BuildProject and RunTests before ReviewChange suspension; typed
Result submission runs CommitChanges and records a Completed WorkflowInstance
exactly once (`P772-FIXTURE-TEST-004`, 15/15, lock released). Its Start/complete
Provided Operations and Action programs are test bindings, not generated
executable implementation. `projectTerminalC` now requires the matching
persisted Completed record, and the public completion Service returns typed
`TERMINAL` JSON only after the Completed append. The strict Terminal codec
rejects unknown fields/schema, incompatible result type/payload, and incomplete
Presentation (`P772-TERMINAL-TEST-005`, 21/21, lock released). This wire output
is a projection, not progression authority. `WorkflowWaitJsonV1` now provides
strict `WAIT` status JSON with Handle, expected revision, ContextSnapshot, and
minimal Presentation. It rejects unknown fields/schema/kind, stale snapshots,
and incomplete state references; it carries no resume authority. The focused
protocol retest passed 7/7 (`P772-WAIT-RETEST-007`, lock released) after fixing
a test that had changed both the Handle and snapshot revisions.
`WorkflowDecisionJsonV1` now round-trips the shared typed ContinuationRequest
and minimal Presentation without a WorkOrder execution requirement. Unknown
fields/schema/kind and incompatible input fail closed. A Decision document is
not admitted as a WorkOrder result or resume authority; focused protocol tests
passed 8/8 (`P772-DECISION-TEST-008`, lock released). The closed Continuation
wire variants are now covered individually. A generic
`SkillWorkResultNormalizerV1` now turns a Host-dispatched completion into a
typed `ContinuationResult` through the existing Continuation SPI adapter. Its
`ExecutionEvidence.skillDispatch` records the requested abstract requirement,
selected worker profile, and mapping-policy version; strict Result JSON carries
these fields. Missing or mismatched requirement/evidence fails admission, while
changing only the valid Host profile/model does not change the admitted
result (`P772-SKILL-TEST-009`, 24/24, lock released). This proves a local
typed normalization boundary, not a real Codex invocation, Host dispatch
policy, or `sm-workflow` application integration.
An explicit control-plane fixture invokes declared advance/status/submit
Provided Operations through ComponentFactory and the active UnitOfWork with
no registered child AI Provider (`P772-REVIEW-FIX-TEST-027`, 26/26 across the
focused CNCF suites, lock released). This proves direct invocation, not broad
public API/CLI expansion.
The ABI-correlated fixture now also binds that normalizer through the
Continuation SPI adapter against the persisted issued WorkOrder and recovered
claim. It rejects stale revision and missing policy version before submitting
the normalized Result through strict JSON and the fresh-UnitOfWork closing
path (`P772-SKILL-FIXTURE-TEST-010`, 24/24, lock released). Its Host output and
Action programs remain test fixtures; no actual Codex dispatch is claimed.
Decision submission and execution semantics are outside this Phase's minimum
typed projection/JSON contract: Decision wire input is not WorkOrder result or
resume authority. The production consumer path remains owned by `sm-workflow`.
The public Service fixture also simulates a Continuation store that writes an
`Available` record but returns failure. The Start response fails before
post-commit projection and issues no WorkOrder, while the persisted record
remains for reconciliation. The same focused spec passed 15/15
(`P772-INDETERMINATE-TEST-011`, lock released). This proves the loose failure
response boundary, not atomic rollback or global claim exclusion.
`WorkflowInteractionJsonV1` now gives a consumer one closed response entry
point for `WORK_ORDER`, `DECISION`, `WAIT`, and `TERMINAL`. It delegates full
validation to the existing variant codecs and does not turn a wire kind into
progression authority. Focused protocol tests passed 10/10
(`P772-INTERACTION-TEST-012`, lock released). This is a CNCF wire boundary,
not a production `sm-workflow` adapter or actual Codex dispatch.
The public Service fixture now routes both its WorkOrder and Terminal
responses through that common entry point (`P772-PUBLIC-INTERACTION-TEST-013`,
15/15, lock released). The consumer boundary and its exclusions are recorded
in the [sm-workflow handoff](phase-77.2-sm-workflow-handoff.md).

Cozy 62.3's frozen CML fixture declares four logical `ACTION`s but no
`CONSTITUENT-ACTION` or `DERIVED-ACTION` placement. Its Workflow ABI exposes
descriptors, not an executable order. An additive Cozy fixture now places the
same four logical Actions on a constituent transition, and the Cozy generator
includes nested WORKFLOW Composite StateMachines in its separate
`CompositeStateMachineActionProgram` output. Its focused producer test passed
(`P772-COZY-PLACED-TEST-003`, 1/1, lock released); the generated occurrence
order is BuildProject -> RunTests -> ReviewChange -> CommitChanges. The new
source fixture has SHA-256 `88a4221e40ad07189af01992334c17682c237048c6e17bbf549ca65d9331a6f4`,
Workflow ABI `4011da8dff663dc369101d1d54d892e71fb26b97970867f8adbed63e2c15d992`,
and ActionProgram ABI `baac46d5d355f03aca909ab6f10098c7da6708b4667dc67731f6f790191afa5d`.
The old Workflow producer, the standalone ActionProgram producer, and the new
fixture also passed together (`P772-COZY-PLACED-REGRESSION-004`, 7/7, lock
released); the old generated Workflow ABI retains its pinned SHA-256.
CNCF now pins the new Workflow ABI and ActionProgram sidecars. Its receiver
cross-checks definition/Action identity, Operation and source provenance,
occurrence ordinal/placement, and effect metadata, then binds the generated
occurrences to `ExecutionPlan` without taking order from the Action descriptor
array (`P772-CNCF-PLACED-RECEIVER-TEST-017`, 3/3, lock released). At that stage
the public Service vertical slice used test-owned Action programs and the
frozen 62.3 Workflow ABI. A separate executor fixture runs the new plan to a durable
ReviewChange suspension and executes CommitChanges from the generated entry
binding in a fresh UnitOfWork after recovery
(`P772-CNCF-PLACED-EXECUTION-TEST-018`, 4/4, lock released). That test alone did
not connect the new plan to the public Service and persisted WorkflowInstance
path. CNCF also composes the admitted plan as a provenance-checked
`StateMachineProvidedApiProgram`: it preserves the final completed result,
stops on ReviewChange suspension before CommitChanges, and rejects a different
Workflow revision (`P772-CNCF-ADAPTER-RETEST-020`, 5/5, lock released).
The public Service fixture now exercises both the frozen `WorkflowProducer`
definition and the new `WorkflowProducerPlaced` plan.
The placed fixture's exact Workflow identity/revision/source SHA is now an
additional closed admission profile. Its typed definition binds and revalidates
through `WorkflowInstancePersistence`, while the old profile remains admitted
(`P772-CNCF-PLACED-ADMISSION-TEST-021`, 30/30 across three suites, lock
released). This separately proves definition persistence admission. The
additive CML fixture now explicitly declares
`beginReview` and `submitReview`. Cozy generates their matching Provided API
sidecar without changing the Workflow or ActionProgram ABI hashes
(`P772-COZY-PLACED-PROVIDED-TEST-005`, 1/1, lock released). CNCF pins and
admits all three sidecars together (`P772-CNCF-PROVIDED-ADMISSION-TEST-022`,
30/30 across three suites, lock released). The placed generated plan now also
passes the public Service and persisted WorkflowInstance path: Start stops at
the durable ReviewChange suspension, and submitted Result executes the
generated CommitChanges entry Action in a fresh UnitOfWork before Completed
history and typed Terminal projection (`P772-CNCF-PUBLIC-PLACED-TEST-023`,
15/15, lock released). This proves the generated vertical slice; actual Codex
dispatch and the `JudgmentAction` specialization are still separate work.
The placed fixture also passes with either of two registered ReviewChange
Provider identities without changing its Workflow definition, Action trace,
or terminal history (`P772-CNCF-PROVIDER-REPLACE-TEST-024`, 15/15, lock
released). CNCF's deterministic fixture and replaceable Provider boundary
passed the final full-suite validation recorded below. Application-specific
`JudgmentAction` metadata and a real Codex worker belong to `sm-workflow`
Phase 1, not the CNCF Phase 77.2 closure gate.

## Closure contract

- `ContinuationRequest` projects only a durable externally claimable suspended
  Required SPI operation. Internal deterministic Actions remain in the runtime
  and never become Skill WorkOrders.
- The generic typed model includes `WorkflowStartRequest`, `WorkflowStartResult`,
  `WorkflowHandle`, and closed `Continuation = WORK_ORDER | DECISION | WAIT |
  TERMINAL`; application payloads remain consumer-owned extension points.
- WorkOrder requirements use typed capability/risk data and abstract
  `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL` reasoning levels. Host model or
  provider selection is evidence, not StateMachine control.
- Fail-closed JSON preserves identity, expected revision, ContextSnapshot,
  typed input/result, Completion/Evidence, and rejects unknown schema,
  incompatible payload, stale revision/snapshot, missing evidence, and
  duplicate/incompatible result.
- The accepted Cozy Phase 62.3 fixture proves internal Action programs,
  durable external ReviewChange suspension, after-commit claimability,
  typed fresh-UnitOfWork resume, closing Action execution, and terminal output.
- A committed UnitOfWork followed by failed or indeterminate continuation
  persistence remains an explicit incomplete outcome; the runtime must not
  hand out work from that failed result or describe it as atomic rollback.
  An indeterminate write can nevertheless leave an `Available` record in the
  store; this loose boundary requires reconciliation and does not prove
  global claim exclusion after a failure response.
- The CML-first evidence pins Cozy source/ABI/fixture, CNCF revision, protocol
  compatibility, and a stable `sm-workflow` consumer handoff without claiming
  consumer datastore, migration, retention, or public CLI completion.

## Planning and validation

Estimated duration is 330 minutes (270-420), within the six-hour target and
eight-hour ceiling. Reconciled common-contract and JSON-boundary decisions make
this bounded-settled execution with recommended parent profile
`gpt-5.6-terra / high` and standard reasoning-mode policy.

Phase 77.2 is the `aggregate-final-owner`: after every predecessor's deferred
release handoff is verified, it runs the serial sequence's one repository-wide
`sbt --batch test` on its frozen release tree, in addition to its focused
validation, review, closure ledger, and release commit.

This later aggregate test does not gate or retroactively substitute for
Phase 77.1's own focused validation, review, closure ledger, and release
commit. Phase 77.2 final acceptance consumes the closed Phase 77.1 handoff.

## Closure evidence

The user-directed manual closure accepted Cozy producer commit
`8d70aff3bc4b6e311bd7de38c209f08a7ad72bf0` and CNCF implementation
commit `74aa88f4c4e4a8b2e4d681fe285cfe57bca9ba49`. The CNCF aggregate
`sbt --batch test` passed 3,823 tests with zero failures, 510 completed suites,
and the shared SBT lock released (`P772-MANUAL-FULL-001`). The final manual
review found no remaining blocker. The exact scope, test result, non-goals,
and explicit absence of a formal Phase Full Review Manifest are recorded in
the [manual closure journal](../journal/2026/09/2026-09-25-phase-77.2-manual-closure.md).

## Non-goals

- ABI admission, ComponentFactory discovery, and persistence SPI ownership
  from Phase 77; or Action/Provider/Continuation runtime implementation from
  Phase 77.1.
- A second Workflow DSL or runtime, direct Worker transition control, concrete
  model-selection policy, default datastore provider, broad Start/API expansion,
  rich Presentation/UI, parent/child composition, orchestration, REST/MCP/UI,
  transport, or Flutter implementation.
- `sm-workflow` application payload schemas, SQLite/datastore, migration,
  retention, lease policy, or public CLI implementation.
- The shared EventStore/DataStore/WorkflowInstance/Continuation/UnitOfWork
  transaction domain; [Phase 94](phase-94.md) owns that upgrade and must not
  block the first `sm-workflow` vertical slice.

## References

- [Phase 77](phase-77.md), [Phase 77.1](phase-77.1.md), [Phase 77.2 Checklist](phase-77.2-checklist.md), and [Phase 94](phase-94.md)
- [Phase 77 common contract decision](../journal/2026/09/2026-09-20-phase-77-common-contract-reconciliation-decision.md)
- [Phase 77 minimum Skill continuation JSON contract](../journal/2026/09/2026-09-20-phase-77-minimum-skill-continuation-json-contract.md)
- [Released Workflow producer fixture handoff](../design/composite-workflow-released-producer-fixture-handoff.md)
