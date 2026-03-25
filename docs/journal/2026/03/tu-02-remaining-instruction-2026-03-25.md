# TU-02 Remaining Instruction (Operation Metadata / Runtime Alignment)

status=ready
published_at=2026-03-25
owner=textus-user-account

## Goal

Complete the remaining TU-02 scope for `textus-user-account`.

Already done:

- generated Component package-name support
- EntityValue package layout `${package}/entity/*`
- project compile/test green

Remaining scope is limited to:

1. operation metadata integration
2. command/query execution policy alignment
3. runtime/projection visibility alignment
4. regression-proof verification

## Primary Target

- `/Users/asami/src/dev2026/textus-user-account`

Conditional follow-up targets:

- `/Users/asami/src/dev2025/cozy`
- `/Users/asami/src/dev2026/sbt-cozy`
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Only touch dependency repos if the component project cannot complete TU-02 with current behavior.

## In-Scope

1. Operation metadata integration
- Confirm generated `operationDefinitions` are present and semantically correct.
- Ensure service/operation naming matches CML contract.
- Ensure metadata includes command/query distinction required by runtime/projection.

2. Command/query execution alignment
- Confirm command operations follow Job async default path.
- Confirm query operations remain synchronous unless explicitly handled otherwise.
- Remove or fix any mismatch between generated operation kind and actual execution path.

3. Runtime/projection visibility
- Confirm practical visibility in:
  - `meta.help`
  - `meta.describe`
  - `meta.schema`
  - command help path where relevant
- Ensure outputs are deterministic and aligned with the generated model.

4. Regression safety
- Add or update focused tests/scripted checks for:
  - operation metadata exposure
  - command/query execution path expectations
  - package/layout assumptions already completed

## Out of Scope

- TI-* identity subsystem work
- Full PX-* phase closure
- Broad redesign of command/runtime architecture

## Suggested File Targets

- textus-user-account:
  - `/Users/asami/src/dev2026/textus-user-account/src/main/cozy/user-account.cml`
  - `/Users/asami/src/dev2026/textus-user-account/src/main/scala/org/simplemodeling/textus/useraccount/ComponentFactory.scala`
  - `/Users/asami/src/dev2026/textus-user-account/src/main/scala/org/simplemodeling/textus/useraccount/cli/*.scala`
  - generated outputs under `target/scala-3.3.7/src_managed/main/...` for inspection only
- CNCF (conditional):
  - runtime/projection files only if a framework gap is confirmed

## Required Deliverables

1. Verified operation metadata path for `textus-user-account`.
2. Verified command/query execution behavior consistent with TU-01 contract.
3. Verified `meta/help/describe/schema` exposure for relevant operations.
4. Focused test/scripted results with exact commands.
5. Progress updates:
- `docs/phase/phase-10-checklist.md`
  - mark additional TU-02 subtasks DONE when satisfied
  - mark TU-02 `DONE` only if all remaining subtasks are closed
- `docs/phase/phase-10.md`
  - sync TU-02 checkbox/work stack when appropriate

## Validation

Run from component repo first.

```bash
cd /Users/asami/src/dev2026/textus-user-account
sbt --batch compile
sbt --batch test
```

Then add practical runtime checks, for example through existing command mains or scripted flows.

If a framework repo is touched, run the minimal focused suite there and report it.

## Definition of Done

This instruction is DONE when:

1. Operation metadata is exposed and matches the component contract.
2. Command/query runtime behavior is verified and coherent.
3. Practical projection/help visibility is verified.
4. Relevant regression checks are green.
5. Phase 10 docs reflect the new TU-02 state accurately.
