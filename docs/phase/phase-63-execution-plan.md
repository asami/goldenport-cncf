# Phase 63 Execution Plan — CML StateMachine Contract and Normalization

status=in-progress
phase=[Phase 63](phase-63.md)
checklist=[Phase 63 Checklist](phase-63-checklist.md)

## Scope and Coexistence

This Phase freezes and normalizes the non-Workflow StateMachine contract. It
does not start Phase 63.1 or Phase 63.2, and does not run the repository full
suite; that suite remains owned by Phase 63.2.

Cozy's separate Multi-CML provenance and current Phase 63 CML Workflow grammar
workstreams create no handoff or validation dependency. The CML Workflow
workstream owns its grammar, parser admission, `CompositeStateMachineCml`,
`WorkflowCmlSpec`, and workflow-state evidence. This Phase never edits those
surfaces. Its only possible Cozy surface is the
already-parsed non-Workflow StateMachine projection bridge:
`ModelStateMachineProjector` and its StateMachine-specific specification.

## Frozen Current Facts

- Core `TransitionDecider` retains legacy collection-order compatibility while
  the new strict canonical API requires explicit `TransitionIdentity` and
  declaration order, continues after a false guard, and stops on guard failure.
- CNCF has a second `TransitionSelector`, raw `ExpressionGuard`/MVEL execution,
  and a planner that compensates for repeated declaration positions by adding a
  vector index.
- Cozy's non-Workflow StateMachine projector assigns every transition priority
  and local declaration order as `0`, then applies a global index; it lowers
  expression guards to raw text.
- SimpleModeler's `MComponent.RuleGuard.Expression` and its Scala generator
  preserve the same raw-expression route. `StateMachineDefinition` now carries
  either the closed `StateMachineNormalization` result or its deterministic
  diagnostic; the legacy carrier remains untouched. Generation/ABI changes
  remain Phase 63.1 work.
- The closed model separates `PredicateProgram` from named guard bindings,
  gives trigger context its own nominal identity, validates version-one depth,
  node, and UTF-8 bounds, and carries an AST declaration path rather than a
  fabricated file offset.
- Non-Workflow normalization retains one-level composite topology, immediate
  path-child leaves, named shallow-history fallback, terminal-transition
  metadata, and typed required history writes. Nested composite declarations
  are deliberately rejected by this contract while the pre-existing legacy
  projection remains unchanged.

## Slices and Commit Boundaries

### P63-SMR01 — Semantic Freeze

Own the design/specification contract, exact source inventory, compatibility
boundary, and failing-first target identities. Update only CNCF documentation
and phase status. Validate Markdown links and `git diff --check`; no SBT run is
needed for this documentation slice.

### P63-SMR02 — Canonical Contract

Introduce the closed typed predicate and identity model at the pure core/model
boundary, preserve the existing selection semantics, and make CNCF reject raw
execution on the new contract path. Intended targets are the core
StateMachine model/decider and their specs, SimpleModeler model declarations
only where required to represent the contract, and CNCF contract adapters and
their focused specs. Do not change generated Scala output or UnitOfWork
execution.

### P63-SMR03 — CML Normalization

Normalize already parsed non-Workflow CML StateMachine declarations to the
closed contract with source order, stable identities, typed predicates, named
bindings, and deterministic diagnostics. The Cozy target is limited to
`ModelStateMachineProjector` and a StateMachine-specific test. The Slice also
adds the corresponding model/CNCF focused specifications. It does not modify
Workflow grammar or generated ABI.

The parser's current AST has no physical span, so the contract requires the
normalizer's stable AST declaration path as its source location. This is
intentional provenance, not an invented line number; physical source spans are
a future parser enrichment rather than a reason to weaken diagnostic identity.

Each slice is separately reviewed and accepted with only its owned paths.
Focused tests run serially through the CNCF SBT runner when source code changes;
the Phase 63.2 aggregate full suite is explicitly excluded.

## Completion Evidence

Phase 63 closes only when the design/specification, pure contract, and CML
normalization agree on stable identities, deterministic selection, typed and
bounded predicates, explicit legacy admission, diagnostics, initial/final and
one-level history preservation, and their focused executable specifications.
The focused receipts are `P63-SMR02-LIB-VAL-002`,
`P63-SMR02-NW-FIX-MODEL-VAL-002`
(`a21cb9bc3821a6e18e9507281106f1511a7a761b7958c837dd5ff2a111f66d50`,
the repaired current SimpleModeler tree), `P63-SMR02-CNCF-VAL-003`,
`P63-SMR03-NW-FIX-MODEL-REFRESH-001`
(`13f83c9576787bc90d9c52fc0b27772d24608c706bea59e99d3b73588191e77c`),
and `P63-SMR03-NW-FIX-COZY-VAL-001`
(`392b87fa6b2ae464be49d33f3580b7e2cbee46f8fb98141ba58af30a60c4aef7`);
repository-full validation remains Phase 63.2's aggregate responsibility.

On 2026-09-18, repository-local focused validation was rerun through isolated
serial SBT attempts rather than the shared Phase-state route: the core
`TransitionDeciderSpec` (`cncf-sm-focused-test-20260918-002`, 15 succeeded),
the CNCF `GuardRuntimeSpec` and `TransitionSelectorPropertySpec`
(`phase63-cncf-focused-test-20260918-002`, 13 succeeded), the SimpleModeler
`PredicateProgramSpec` (`phase63-model-focused-test-20260918-002`, 31
succeeded), and Cozy's non-Workflow `ModelerStateMachineProjectionSpec`
(`cncf-cozy-focused-test-20260918-001`, 16 succeeded). Each completed with
`lock=released`. The accepted implementation steps are respectively
`2647ee8`, `d78b80c0`, `eb056a8`, and `b00bbdf`; every commit contains only
its reviewed source/test paths. These are repository-labelled direct evidence,
not a shared Phase-63 authority: they avoid the collision with Cozy's separate
Phase 63 workstream. They do not claim a Phase release, a repository-full
suite, or ownership of Cozy's separate Workflow grammar workstream.
