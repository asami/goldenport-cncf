# TI-02 Instruction (textus-identity Subsystem Runtime Integration)

status=ready
published_at=2026-03-26
owner=textus-identity

## Goal

Implement the runtime integration path for `textus-identity` using the frozen
TI-01 subsystem contract and the completed `textus-user-account` component
baseline.

This work should make the subsystem executable and discoverable through the
existing CNCF runtime/meta surfaces without widening the contract defined in
TI-01.

## Primary Targets

- `/Users/asami/src/dev2026/textus-identity`
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Reference component:

- `/Users/asami/src/dev2026/textus-user-account`

Conditional supporting repos:

- `/Users/asami/src/dev2025/cozy`
- `/Users/asami/src/dev2026/sbt-cozy`

Touch support repos only if TI-02 cannot complete with current
grammar/packaging/runtime capabilities.

## Required Policy Alignment

- TI-01 contract is already frozen and must remain the authority.
- `textus-identity` is a subsystem orchestration/configuration boundary,
  not a duplicate implementation of `textus-user-account`.
- Command/query behavior must stay aligned with the Phase 6 / Phase 8 policy:
  - commands follow the Job async default path
  - queries remain synchronous unless an existing runtime rule requires
    ephemeral-job handling
- Related project updates and `sbt` executions do not require confirmation
  prompts.

## Precondition

Assume the following TI-01 artifacts are present and authoritative:

- `/Users/asami/src/dev2026/textus-identity/docs/contract/ti-01-subsystem-contract.md`
- `/Users/asami/src/dev2026/textus-identity/src/main/cozy/textus-identity-subsystem.cml`

If implementation reveals a mismatch between the frozen contract and runtime
feasibility, report the exact gap first and keep any contract change minimal
and explicit.

## In-Scope

1. Subsystem runtime bootstrap
- Make `textus-identity` loadable/executable as a subsystem runtime target.
- Wire the subsystem to the required `textus-user-account` dependency/binding.
- Keep bootstrap/config explicit and deterministic.

2. Operation routing
- Bind the TI-01 operations to concrete delegated execution paths:
  - `registerIdentity`
  - `authenticateIdentity`
  - `getIdentityProfile`
  - `findIdentityByLoginId`
- Ensure delegated routing lands on the correct service/operation in
  `textus-user-account`.
- Remove or avoid ambiguous duplicate resolution paths.

3. Command/query execution semantics
- Verify command operations use the Job-oriented async path.
- Verify query operations are resolved through the synchronous projection path.
- Ensure secret-bearing inputs remain command-only at runtime boundaries.

4. Meta/projection/help visibility
- Expose the subsystem surface consistently in:
  - `help`
  - `describe`
  - `schema`
  - `meta.*` projections
- Keep naming/order deterministic and aligned with the TI-01 contract.

5. Focused verification
- Add or update only the smallest focused checks needed to prove:
  - subsystem runtime resolution works
  - delegated operation routing is correct
  - command/query semantics are coherent
  - introspection/projection surfaces are stable

## Out of Scope

- Redefining the TI-01 contract surface.
- New identity provider adapters.
- Session/token issuance implementation beyond what is needed to preserve the
  TI-01 runtime route.
- Broad CNCF runtime redesign unrelated to subsystem integration.
- PX-02 full executable-spec closure beyond focused TI-02 checks.

## Suggested File Targets

`textus-identity`:

- `/Users/asami/src/dev2026/textus-identity/src/main/cozy/textus-identity-subsystem.cml`
- `/Users/asami/src/dev2026/textus-identity/src/main/scala/...` if subsystem
  bootstrap/factory classes are needed
- `/Users/asami/src/dev2026/textus-identity/build.sbt` if project wiring is
  currently missing
- `/Users/asami/src/dev2026/textus-identity/src/main/resources/...` if minimal
  config is required

`cloud-native-component-framework`:

- runtime/subsystem resolution files only if framework work is actually needed
- projection/meta/help files only if subsystem visibility is missing

Use actual touched files as discovered and report them exactly.

## Conditional Framework Follow-Up

Only touch CNCF/Cozy/sbt-cozy when one of the following is confirmed:

1. current subsystem bootstrap cannot load the TI-01 model
2. delegated operation binding is not representable with existing runtime hooks
3. subsystem operations do not appear correctly in `help` / `describe` /
   `schema` / `meta.*`
4. command/query semantic routing cannot honor TI-01 using current behavior

Do not preemptively redesign the framework. Close TI-02 with the minimal
cross-repo change set.

## Required Deliverables

1. Executable `textus-identity` subsystem runtime path for the frozen TI-01
   operations.
2. Verified delegated binding to `textus-user-account`.
3. Verified subsystem visibility in `help` / `describe` / `schema` / `meta.*`.
4. Focused validation results with exact commands.
5. Progress updates:
- `/Users/asami/src/dev2026/textus-identity/docs/phase/phase-10-checklist.md`
  - TI-02 `Status: DONE` when satisfied
- `/Users/asami/src/dev2026/textus-identity/docs/phase/phase-10.md`
  - TI-02 checkbox `[x]`
  - next active item updated accordingly
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10-checklist.md`
  - TI-02 `Status: DONE`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10.md`
  - TI-02 checkbox `[x]`
  - next active item updated accordingly
6. Short handoff note describing what PX-01 should validate next.

## Validation

Start with the lightest checks in `textus-identity`, then run focused runtime
checks in the repo(s) actually touched.

Examples:

```bash
cd /Users/asami/src/dev2026/textus-identity
find . -maxdepth 4 -type f
```

If a runnable subsystem main/CLI exists or is added, run the minimum focused
commands needed to prove:

- subsystem loads
- operation routing resolves
- introspection surfaces expose the subsystem operations

If CNCF is touched, run only the focused `sbt` compile/test commands required
by the changed runtime/projection path and report exact commands/results.

## Definition of Done

TI-02 is DONE when:

1. `textus-identity` runtime integration exists for all frozen TI-01
   operations.
2. Delegated routing to `textus-user-account` is explicit and coherent.
3. Command/query runtime behavior matches the established policy.
4. `help` / `describe` / `schema` / `meta.*` expose the subsystem surface
   deterministically.
5. Focused validation is green in every touched repository.
6. Phase 10 docs reflect TI-02 completion consistently.
