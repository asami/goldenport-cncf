# Phase 36 - Component Runtime Boundary Capabilities

Stage Status:
- Current status: IN_PROGRESS
- Current step: RB-03 declared typed component configuration
- Owner: Phase 36 Component Runtime Boundary Capabilities
- Update rule: Update this block and `phase-36-checklist.md` whenever a stable
  work-item state changes. Close the phase only when every in-scope checklist
  item is complete or explicitly relocated.

status = active

## 1. Purpose

Phase 36 implements strategy item
`9.31 Component Runtime Boundary Capabilities`. It removes the need for
reusable components to obtain operational time, runtime configuration, local
resource trees, secrets, or external-tool inputs from ambient JVM/OS state.

CBD Support is the first implementation driver. The resulting contracts are
CNCF platform capabilities and must remain independent of CBD Support, Cozy,
CAR Review, and application-specific models.

## 2. Reused Foundations

- Phase 31 already binds execution time and deterministic test control through
  `ExecutionContext`; Phase 36 does not create another clock abstraction.
- Phase 33 already defines logical single-resource reads through
  `ResourceReference` and `ResourceAccess`; Phase 36 adds a distinct bounded
  tree contract rather than changing a single read into ambient directory
  access.
- Phase 34 already defines admitted `ProcessExec`, WorkArea confinement,
  limits, fake/local drivers, and artifact collection.
- Phase 35 already makes UnitOfWork responsible for terminal process and
  WorkArea reclamation.

## 3. Scope

- Promote context-provided execution time as the canonical component runtime
  contract and add component-facing executable evidence.
- Define declared typed component configuration with deterministic precedence,
  provenance, confidentiality, and structured failures.
- Represent secrets as opaque references; components do not receive resolved
  credential values.
- Define named, admitted, read-only resource trees with deterministic bounded
  snapshots and an in-memory test provider.
- Integrate admitted resource-tree materialization with Process Execution
  WorkAreas without accepting arbitrary component/request host paths.
- Preserve Process Execution admission, request-level narrowing,
  authorization, observability, and UnitOfWork cleanup.
- Publish component developer guidance and a downstream CBD Support migration
  handoff.

## 4. Boundaries

- No generic component access to `System.getenv`, `sys.env`, `Clock.system*`,
  host `Path`, shell text, process handles, or executable locations.
- Existing `config_string` helpers are not the canonical declared typed
  configuration contract and must not define Phase 36 security semantics.
- A secret reference may cross the component boundary; the secret value may
  only be resolved by an authorized runtime-owned provider or driver.
- A component may request a logical resource tree but may not select its
  physical root or convert an arbitrary request path into a WorkArea input.
- Fixed bounded Process Execution input files and admitted resource-tree
  provenance are separate concepts.
- This phase does not migrate or release CBD Support itself.

## 5. Active Work Stack

- A (DONE): RB-01 - Audit existing contracts and freeze Phase 36 scope.
- B (DONE): RB-02 - Confirm the component execution-time contract.
- C (IN PROGRESS): RB-03 - Implement declared typed component configuration.
- D (OPEN): RB-04 - Implement the opaque secret-reference boundary.
- E (OPEN): RB-05 - Implement admitted read-only resource trees.
- F (OPEN): RB-06 - Materialize admitted trees into Process WorkAreas.
- G (OPEN): RB-07 - Verify the provider-neutral external-tool pattern.
- H (OPEN): RB-08 - Update design/spec/developer documentation.
- I (OPEN): RB-09 - Run full verification, prepare the CBD handoff, and close
  Phase 36.

## 6. Candidate Public Contracts

The contract audit must finalize names before implementation. Initial model
responsibilities are:

- typed declared configuration keys, decoders, resolution results, and access;
- opaque secret references without a component-visible value resolver;
- resource-tree identity, logical reference, limits, entries, snapshots, and
  access;
- Process Execution tree input carrying logical admitted provenance and a
  WorkArea-relative materialization target.

Protected internal DSL entry points must cover required/optional typed
configuration, resource-tree snapshot access, and ordinary `process_exec`
without adding direct provider or host APIs.

## 7. Completion Conditions

Phase 36 closes only when:

- component runtime time is demonstrably sourced from the bound
  `ExecutionContext` capability;
- declared configuration resolves typed values and structured failures without
  request/property privilege escalation;
- components cannot obtain raw secret values through the normal runtime API;
- resource trees reject unknown roots, traversal, symlink escape, and all
  configured limit violations;
- only an admitted logical tree can be materialized into a Process Execution
  WorkArea;
- fake/in-memory providers prove the path without host-specific fixtures or a
  live external tool;
- diagnostics expose only safe structural metadata;
- focused and full CNCF regressions pass;
- normative documents and component developer guidance describe the contract;
  and
- remaining CBD Support migration and production-provider work is explicitly
  relocated.

## 8. Source Record

The non-normative source handoff is
`docs/journal/2026/07/cbd-support-runtime-boundary-handoff-2026-07-17.md`.
Settled Phase 36 decisions must be promoted to design/spec documents and
executable specifications before they are treated as available framework
contracts.

RB-01 has promoted the Phase 36 capability boundary to
`docs/design/component-runtime-boundary-capabilities.md` and
`docs/spec/component-runtime-boundary-capabilities.md`. RB-02 through RB-06
remain implementation work; the new static contract does not claim those APIs
are available before their executable specifications are completed.

RB-02 has confirmed the existing clock boundary through
`ExecutionClockDslSpec`: fixed and controlled runtime clocks reach component
ActionCalls only through the bound `ExecutionContext` internal DSL.
