# Phase 63 Checklist - CML StateMachine Contract and Normalization

status=complete
phase=[Phase 63 - CML StateMachine Contract and Normalization](phase-63.md)

This ledger owns only `SMR-01` through `SMR-03`. Phase 63.1 owns `SMR-04`
and `SMR-05`; Phase 63.2 owns `SMR-06` through `SMR-08`. Only one stage may
be `IN_PROGRESS` at a time.

Closure: the earlier repository-labelled `phase63_closure_review_001` is
historical evidence. The current closure authority is
`P63-FULL-REVIEW-20260918-001`, bound to the four-repository V3 Phase-base
record `04f17c44852ef61471ce05be518a25bc48b6a56219326771c3650906ad3e30d3`.
It found no Current Boundary Blocker and retained two nonblocking Hygiene
records in the canonical Phase 63 Hygiene journal. The repository-full suite
remains Phase 63.2's explicit aggregate responsibility and is not claimed
here.

## SMR-01: Inventory and Semantic Freeze

Stage Status:
- Current status: DONE
- Owner: Cozy, SimpleModeler, SimpleModeling, CNCF, StateMachine, Aggregate,
  UnitOfWork, and observability maintainers
- Entry rule: Phase 63 is authorized to start.
- Update rule: Reopen before changing the frozen non-Workflow semantic
  inventory, compatibility boundary, or failing-first acceptance identities.
- Completion rule: Current semantics, bypasses, retained boundaries, and exact
  failing-first acceptance identities are frozen.

- [x] Inventory CML StateMachine grammar/AST and existing generated transition
  rules, including state/event/transition identity, priority, guards, actions,
  initial/final states, and one-level composite/named shallow history.
- [x] Inventory core `StateMachine`, `TransitionDecider`, `Guard`, and `Effect`
  semantics and every CNCF planning/provider/hook entry that consumes them.
- [x] Freeze duplicate-selection, raw-MVEL, missing named-binding, defaulted
  priority, no-op action, and identity-loss facts without designing a second
  transition engine.
- [x] Freeze the minimal `pattern:state-machine` / `pattern:state-transition`
  Aggregate implementation boundary and retain all other implementation kinds.
- [x] Register failing-first specifications for contract, normalization,
  diagnostics, compatibility, and downstream generation consumers.

Evidence:
- `docs/spec/cml-statemachine-normalization-contract.md` fixes the non-Workflow
  normalization boundary, semantic (non-EntityId) identity, strict ordering,
  predicate, action, topology, legacy-expression, and diagnostic rules.
- `docs/phase/phase-63-execution-plan.md` freezes the core/CNCF/Cozy source
  inventory and expressly excludes Cozy's active Workflow grammar workstream.
- The first focused specifications are
  `TransitionDeciderSpec` and `ModelerStateMachineProjectionSpec`; no aggregate
  suite is claimed in this Phase.

## SMR-02: Canonical Transition and Predicate Contract

Stage Status:
- Current status: DONE
- Owner: core StateMachine, CML semantic-model, and CNCF adapter maintainers
- Entry rule: SMR-01 is DONE.
- Update rule: Reopen before changing the closed predicate grammar, canonical
  selection semantics, nominal identities, or unexecuted effect-plan contract.
- Completion rule: Pure selection, guard, effect-plan, result, ordering, and
  failure semantics are fixed without parallel engines.

- [x] Fix core StateMachine as canonical pure transition-selection owner and
  ordering by `(priority asc, declarationOrder asc)`.
- [x] Preserve guard false as candidate non-match and guard failure as terminal
  selection failure.
- [x] Define the closed, typed, versioned, side-effect-free `PredicateProgram`
  and explicit named `GuardBindingResolver` contract.
- [x] Define stable machine, transition, state, event, guard, action, and
  trigger-context identities, versions, limits, and failure vocabulary.
- [x] Define `TransitionPlan`, candidate state, admitted local effect plan, and
  structured success/failure results while prohibiting ambient effects.
- [x] Preserve explicit initial/final states and existing one-level
  composite/named shallow-history semantics.
- [x] Add determinism, malformed predicate, overlap, limit, and
  failure-priority specifications.

Evidence:
- `TransitionDeciderSpec` passed as `P63-SMR02-LIB-VAL-002`, receipt
  `15acdaa9dc906c70ffa8cef114ece6dc7fdb417c5620b83a29d53d9e20bca5a0`, and
  fixes strict ordering, false-guard continuation, terminal guard failure,
  identity, and unexecuted `TransitionPlan` behavior.
- `PredicateProgramSpec` passed as `P63-SMR02-MODEL-VAL-003`, receipt
  `a8a2564433f6ca15e273ef92c8c0ec01958f4b47878341851df0067b256d6af4`, and
  covers the closed v1 predicate/trigger-context contract plus the composite-error
  and normalized action/diagnostic repairs.
- `PredicateProgramSpec` passed as `P63-SMR02-MODEL-VAL-006`, receipt
  `a2491166c13e97b7aef041792cf5f63e3bb660d3da021e20433c4eb034658db7`, before
  the later normalization-boundary repair; it additionally rejects a
  same-machine unrelated state claimed as a composite direct leaf,
  shallow-history fallback, or history-write leaf.
- `PredicateProgramSpec` passed on the repaired current SimpleModeler tree as
  `P63-SMR02-NW-FIX-MODEL-VAL-002`, receipt
  `a21cb9bc3821a6e18e9507281106f1511a7a761b7958c837dd5ff2a111f66d50`
  (`validated_tree_sha256`
  `c2a44b7142178c3678867998f41c27f5551ab82ba9071a88daaf800583e88873`).
  It rejects an absent explicit initial state, a composite declared beneath a
  composite direct leaf, raw or oversized action-binding references, and
  retains a valid dotted action-binding reference.
- `GuardRuntimeSpec` and `TransitionSelectorPropertySpec` passed as
  `P63-SMR02-CNCF-VAL-003`, receipt
  `fa45ec2cbb800158960a174b923cc0465559167d81ddacbde12e019984776dd8`.

## SMR-03: Already-parsed CML StateMachine Normalization

Stage Status:
- Current status: DONE
- Owner: Cozy modeler maintainers
- Coordination boundary: Cozy's separate Multi-CML provenance and current CML
  Workflow grammar workstreams own Workflow DSL syntax, parser admission,
  `CompositeStateMachineCml`, `WorkflowCmlSpec`, and workflow evidence. This
  Stage owns only the already-parsed non-Workflow StateMachine projection bridge
  needed for the canonical transition contract.
- Entry rule: SMR-02 is DONE.
- Update rule: Reopen before changing non-Workflow projection, its deterministic
  diagnostics, or the explicit legacy-expression compatibility admission.
- Completion rule: CML declarations normalize to the canonical closed model
  with deterministic diagnostics and explicit legacy admission.

- [x] Normalize machine/state/event/transition identity, source location,
  initial/final states, one-level hierarchy/history, typed trigger context,
  priority, declaration order, guards, and action phases.
- [x] Admit closed typed predicates and named guards/actions as explicit
  binding references; reject legacy raw expressions deterministically.
- [x] Reject duplicate identities, missing references, invalid targets or
  priorities, unsupported predicates, and ambiguous bindings.
- [x] Define compatibility admission for legacy CML and generated
  raw-expression rules without silently preserving raw execution.
- [x] Add Given/When/Then and ScalaCheck normalization and diagnostic specs.

Evidence:
- `P63-SMR03-MODEL-REFRESH-004` refreshed the earlier changed
  `simplemodeler_2.12:1.1.26-SNAPSHOT` locally, receipt
  `899bd5599b4b30f3f89aa6711bf42244ecc954866834c9ff83a3c0e64a404142`.
- `ModelerStateMachineProjectionSpec` passed as `P63-SMR03-COZY-VAL-009`,
  receipt `57723b5cff86d847f0cc7445c9a2c45ad2152b023a97abc1e444424e9806e57e`,
  before the later producer repair. The retained terminal
  `P63-SMR03-COZY-VAL-008` compile failure preceded the explicit optional
  initial-state repair and is superseded by that receipt.
- The repaired current producer was refreshed locally as
  `P63-SMR03-NW-FIX-MODEL-REFRESH-001`, receipt
  `13f83c9576787bc90d9c52fc0b27772d24608c706bea59e99d3b73588191e77c`
  (`validated_tree_sha256`
  `c2a44b7142178c3678867998f41c27f5551ab82ba9071a88daaf800583e88873`).
  `ModelerStateMachineProjectionSpec` then passed as
  `P63-SMR03-NW-FIX-COZY-VAL-001`, receipt
  `392b87fa6b2ae464be49d33f3580b7e2cbee46f8fb98141ba58af30a60c4aef7`
  (`validated_tree_sha256`
  `d492eedaa1c9ddb4ed848fffebebfbead56fc49739059fe81318f199869e7118`).
  These receipts cover the non-Workflow bridge only; they do not run or claim
  Cozy Workflow grammar or Multi-CML provenance validation.
- On 2026-09-18, the current non-Workflow source trees were rerun through
  isolated repository-local serial SBT attempts: `TransitionDeciderSpec` (15
  succeeded), `GuardRuntimeSpec` plus `TransitionSelectorPropertySpec` (13
  succeeded), `PredicateProgramSpec` (31 succeeded), and
  `ModelerStateMachineProjectionSpec` (23 succeeded). Every attempt completed
  with `lock=released`; this is focused evidence, not Phase 63.2's deferred
  repository-full validation and not evidence for Cozy's separate Workflow
  grammar workstream.
- The resulting review-passed direct local Step commits are simplemodeling-lib
  `2647ee8`, SimpleModeler `eb056a8`, CNCF `d78b80c0`, and Cozy `b00bbdf`
  plus `6619adc` and `c27b957`. `c27b957` adds the direct-AST fixture required
  to exercise normalizer ownership of an undeclared event after parser
  admission. The isolated post-commit attempt
  `P63-SMR03-POSTCOMMIT-VAL-005` passed
  `ModelerStateMachineProjectionSpec` with 23 succeeded and `lock=released`.
  The final Cozy Step makes the normalizer a tracked component, retains
  entity-aware `HISTORY-FIELD` validation, and rejects missing history fields
  and undeclared triggers deterministically. These commits are intentionally
  repository-labelled evidence rather than a shared Phase-63 state, so CNCF's
  StateMachine work cannot consume or mutate Cozy's independently numbered
  Multi-CML or Workflow workstreams.
