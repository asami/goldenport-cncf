# TI-01 Instruction (textus-identity Subsystem Contract)

status=ready
published_at=2026-03-26
owner=textus-identity

## Goal

Define and freeze the integration contract for `textus-identity` subsystem
on top of the completed `textus-user-account` component baseline.

## Primary Target

- `/Users/asami/src/dev2026/textus-identity`

Reference component:

- `/Users/asami/src/dev2026/textus-user-account`

Conditional supporting repos:

- `/Users/asami/src/dev2025/cozy`
- `/Users/asami/src/dev2026/sbt-cozy`
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Touch support repos only if the subsystem contract cannot be expressed with current grammar/packaging/runtime capabilities.

## Required Policy Alignment

- `textus-user-account` is already the reusable component baseline.
- `textus-identity` must define subsystem responsibility, not duplicate component internal logic.
- Related project updates and `sbt` executions do not require confirmation prompts.

## In-Scope

1. Subsystem responsibility boundary
- Define what belongs to subsystem composition vs component implementation.
- Clarify that identity orchestration/configuration lives in subsystem,
  while account behavior remains in component.

2. Identity operation set
- Define the initial subsystem-level use cases and external entry points.
- Identify which operations are composed from `textus-user-account` and which are subsystem-owned.

3. Command/query boundary
- Define command/query semantics for subsystem-level operations.
- Keep consistency with Phase 6/8 execution policy.

4. Integration contract with `textus-user-account`
- Define required component coordinates/dependencies.
- Define extension/config binding requirements.
- Define event/job interaction needs if applicable.

5. Contract artifacts
- Provide canonical examples for subsystem declaration.
- Provide invalid-case constraints and expected failure behavior.

## Out of Scope

- TI-02 runtime implementation.
- Full identity provider adapter implementation.
- Broad auth/security redesign outside contract definition.

## Suggested File Targets

- `/Users/asami/src/dev2026/textus-identity/docs/...`
- `/Users/asami/src/dev2026/textus-identity/src/main/cozy/...` if the contract is captured directly as executable subsystem model
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10-checklist.md`

If `textus-identity` repo has no project skeleton yet, create only the minimal contract/document/model files needed for TI-01.

## Required Deliverables

1. Frozen `textus-identity` subsystem contract.
2. Clear mapping between subsystem responsibilities and `textus-user-account` responsibilities.
3. Initial subsystem declaration example(s).
4. Progress updates:
- `docs/phase/phase-10-checklist.md`
  - TI-01 `Status: DONE`
- `docs/phase/phase-10.md`
  - TI-01 checkbox `[x]`
  - next active item updated accordingly
5. Short handoff note describing what TI-02 should implement.

## Validation

Use the lightest validation that proves the contract is coherent.

Examples:

```bash
cd /Users/asami/src/dev2026/textus-identity
find . -maxdepth 3 -type f
```

If executable subsystem CML is added and current tooling supports it, run only the minimum focused generation/build command needed.

## Definition of Done

TI-01 is DONE when:

1. Subsystem contract and boundaries are explicit and internally consistent.
2. Integration expectations with `textus-user-account` are documented.
3. Canonical declaration/example artifacts exist.
4. Phase 10 docs reflect TI-01 completion consistently.
