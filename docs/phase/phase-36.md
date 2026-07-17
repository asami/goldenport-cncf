# Phase 36 - Component Runtime Boundary Capabilities

Stage Status:
- Current status: CLOSED
- Current step: CLOSED
- Owner: Phase 36 Component Runtime Boundary Capabilities
- Update rule: Update this block and `phase-36-checklist.md` whenever a stable
  work-item state changes. Close the phase only when every in-scope checklist
  item is complete or explicitly relocated.

status = closed

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
- C (DONE): RB-03 - Implement declared typed component configuration.
- D (DONE): RB-04 - Implement the opaque secret-reference boundary.
- E (DONE): RB-05 - Implement admitted read-only resource trees.
- F (DONE): RB-06 - Materialize admitted trees into Process WorkAreas.
- G (DONE): RB-07 - Verify the provider-neutral external-tool pattern.
- H (DONE): RB-08 - Update design/spec/developer documentation.
- I (DONE): RB-09 - Run full verification, prepare the CBD handoff, and close
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
`docs/spec/component-runtime-boundary-capabilities.md`. The static contract
reflects only APIs backed by executable specifications.

RB-02 has confirmed the existing clock boundary through
`ExecutionClockDslSpec`: fixed and controlled runtime clocks reach component
ActionCalls only through the bound `ExecutionContext` internal DSL.

RB-03 has established `ComponentConfigurationKey[A]`, typed decoders,
optional-or-required resolution, provenance, and protected ActionCall access.
Public values resolve `component > subsystem > runtime`; malformed, missing,
and denied values remain structured `Consequence` failures. `test.yaml`
assembly component config is executable evidence for deterministic test
configuration, while action/request properties cannot override a declared key.

RB-04 has established opaque `SecretReference` values and runtime-internal
secret resolution. Secret configuration keys return only references, while
confidential values remain denied from the component boundary. Deterministic
in-memory resolution proves redaction and copied material behavior without
claiming a concrete secret-provider integration.

RB-05 has established `ResourceTreeReference`, bounded
`ResourceTreeSnapshot`, and a separate `ResourceTreeAccess` capability. Named
local roots are bound only by runtime configuration, while the in-memory
provider supplies deterministic executable evidence. The local provider denies
symbolic links and returns structured failures for unknown trees, unsafe
entries, and depth/count/byte limits. DSL chokepoints and the
`resource-tree.snapshot` metric retain only logical/provider metadata.

RB-06 has established opaque `ProcessExecutionResourceTreeInput` values with
validated WorkArea-relative targets. Process program and optional grant policy
admit tree identities and narrow source limits before the
`UnitOfWorkInterpreter` materializes inputs. Drivers receive an already
prepared WorkArea, never a component-selected host path. Existing UnitOfWork
cleanup reclaims materialized trees on all terminal paths.

RB-07 has established the provider-neutral external-tool evidence pattern.
Runtime program definitions own fixed command templates and validated fixed
environment bindings; component requests remain logical and bounded. A
deterministic fake driver and test-local adapter prove that all Process terminal
results, including non-zero exits, remain distinct until provider conversion.

RB-08 has promoted the component runtime-boundary use path to execution,
configuration, resource-reference, Process Execution, developer, and test
policy documentation. `ProcessExecutionResourceTreeInput.createC` is the
documented composition point from an opaque admitted tree to an admitted
WorkArea-relative tool input. Phase 37 records the separately owned CBD Support
adoption handoff without changing CNCF provider neutrality.

RB-09 closed Phase 36 on Jul. 17, 2026 with the following evidence:

- 68 focused executable specifications passed across execution-clock,
  declared-configuration, opaque-secret, resource-tree, Process Execution,
  WorkArea, provider-adapter, and UnitOfWork lifecycle boundaries;
- `sbt --batch Test/compile` passed;
- a fresh `sbt --batch test` pass completed successfully, including the suite
  after `AdminSystemPingExecutionSpec`; and
- scoped review found no actionable implementation or documentation findings.

Production secret providers, remote/container Process Execution, general shell
execution, CAR ABI publication, deployment automation, and CBD Support
adoption remain outside this closed phase. Phase 37 owns the downstream CBD
Support adoption work; future provider capabilities remain separately scoped
strategy work.
