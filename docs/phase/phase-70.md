# Phase 70 - Post-Assembly Component Activation

status=closed
closed_at=2026-09-08
planned_at=2026-08-16
depends_on=[Phase 55](phase-55.md)
successor=Phase 70.1 (preserved planning files; no acceptance authority)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 70 Checklist](phase-70-checklist.md)
contract=[Component Activation Lifecycle Specification](../spec/component-activation-lifecycle.md)
consumer=textus-bok Phase 8

## Status Update Rule

The `status`, `current_stage`, and `current_slice` fields change only for
evidence-backed stable work-item state changes. No completion may be recorded
before independent review and all required evidence.

## CA70-01A Contract Evidence

CA70-01A is the frozen R1-R9 contract candidate for Phase 70:
`docs/spec/component-activation-lifecycle.md` records the opt-in capability,
framework-owned context, post-assembly placement, deterministic once-only
ordering, component-boundary-only work, mode isolation and controlled-test
admission, timeout/cancellation/failure/readiness/cleanup/redaction,
compatibility boundaries, and the consumer-neutral Textus BoK handoff.

CA70-01A received an independent Step Review PASS for the retained parent
contract and supplier handoff. The exact review receipt is
`/private/tmp/cncf-p70-ca7001a-step-review.md`; its scope is explicitly
`PASS — parent documentation boundary only`. The receipt does not claim a
Phase review, runtime validation, or Textus BoK consumer acceptance. It
confirms that `P70-CA70-02A-VAL-014` remains provenance only and that the
Phase 70 runtime accumulator and separately owned BoK consumer boundaries are
preserved.

The mandatory full review `P70-FULL-REVIEW-001` found and admitted only
`CPB-P70-001` (links from parent documents to uncommitted child artifacts).
Cycle 1 replaced those links with explicit transferred-child references, and
the accepted focused re-review closed the blocker without changing R1--R9,
parent status semantics, or the runtime/BoK non-acceptance boundary.

CA70-01 is complete for the retained parent contract and supplier handoff
only. Its historical non-terminal state was superseded by the completed
CA70-02 / CA70-02A accumulator and the accepted closure review recorded below.
The preserved Phase 70.1 planning files retain no acceptance authority, and no
Textus BoK source or consumer behavior is accepted here.

## Decision P70-CA70-01B-FULL-REVIEW-001

The admitted CA70-01B focused re-review returned `FULL_REVIEW_REQUIRED` for
two incomplete evidence obligations: it did not prove that each exercised
runtime mode had admitted the component before asserting no activation, and it
did not recursively inspect public failure conclusion/diagnostic exception
payloads for raw `Throwable` leakage. The CAR-lint evidence-only exception is
not applicable.

The developer selected `acceptance-change` through a direct user message. The
verified decision binds request identity
`a1985f664ba2c59dc3c27dc0037e752f58230a1120b7914f078536cee1896191`, source
message SHA-256
`f8d3cbb7f577dbadc3e35847645c0b68dde25b5bc1131ad1629c7d25f3047dad`, and
answer-evidence SHA-256
`f2a812c1a5b92d577ced64dbb8b0ec2e33559248a755cbd90215ea45c219e31f`.

This changes the CA70-01B acceptance/review boundary only as follows:

- every runtime-mode executable scenario must retain the selected component
  identity and prove its admission to that fully assembled mode before making a
  no-activation assertion; and
- failure scenarios must inspect the public conclusion/diagnostic exception
  accessors recursively, including structured nested payloads, and prove that
  raw `Throwable` data and private locators are absent.

The R1--R9 lifecycle design, runtime implementation scope, Textus BoK consumer
scope, and the closed repair-cycle budget remain unchanged. This decision does
not authorize another focused or full review, a repair, implementation, test,
or commit. The authorized next state is `PARENT_CAPABILITY_CHECK`, followed by
a fresh CA70-01B plan if that gate permits it.

## Decision P70-CA70-02A-PROTECTED-REPLAN-001

The developer selected `replan-protected-step` through a direct user message.
The decision request identity is
`620f487811f034ca0d1094edf86d90438a9d1c71257633da6fa470067b678758`; its
resolved classifier is `continue-resolved-decision`.

The unaccepted activation delta is therefore one protected Step named
`P70-ACTIVATION-CORE`, with `CA70-02A` as its runtime-activation Slice. It
contains the public `ComponentActivation` capability/context, server-startup
gate, activation ownership, scheduling/cancellation, cleanup, failure
observability, and their executable specifications. No file in that
accumulator is accepted or committed merely by this re-plan.

The protected-Step plan adds these completion obligations without changing the
frozen R1--R9 API or moving Textus BoK source into this Phase:

- prove required activation failure is terminal before managed-server
  readiness or bound-URL publication, while command and client execution
  remain activation-free;
- prove the runtime-visible failure conclusion/diagnostic is structured,
  redacted, bounded, and does not retain a raw `Throwable`; and
- retain a supplier-only Textus BoK handoff: this Phase provides the stable
  component capability and lifecycle evidence, while Phase 8 remains its
  separately owned consumer implementation.

The Step Review Stop Gate remains binding. A public API, lifecycle, scheduler,
and failure-observability delta must not be passed through a stronger
lightweight Step reviewer. The re-plan permits bounded implementation and
validation against these obligations; it does not silently authorize review,
acceptance, or a release path that bypasses the required protected-boundary
decision gates.

## Decision P70-DEC-ACCUMULATOR-ADMISSION-003

The existing component-activation source and executable-specification
accumulator is re-admitted to Phase 70 under `continue-existing-authority`.
Phase 70 owns these ten paths for the active `P70-ACTIVATION-CORE` /
`CA70-02A` Step:

- `docs/phase/phase-70.md`;
- `docs/phase/phase-70-checklist.md`;
- `docs/spec/component-activation-lifecycle.md`;
- `src/main/scala/org/goldenport/cncf/component/ComponentActivation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceLifecyclePart.scala`;
- `src/main/scala/org/goldenport/cncf/cli/ServerOperation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceClientPart.scala`;
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`;
- `src/test/scala/org/goldenport/cncf/component/ComponentActivationLifecycleSpec.scala`; and
- `src/test/scala/org/goldenport/cncf/cli/ServerOperationActivationSpec.scala`.

The earlier Phase 70.1 transfer is superseded for these ten paths. The
untracked Phase 70.1 planning files remain preserved, but they have no source,
implementation, validation, or acceptance authority for this Phase. CA70-02A
remains the only active non-terminal slice. This admission preserves R1--R9
and does not accept Textus BoK source or consumer behavior.

## Decision P70-DEC-R3-LIFECYCLE-DISPATCH-004

The verified direct answer `追加する` selected
`add-lifecycle-dispatch-path`. The decision expands the accumulator to the ten
paths above and makes `CncfRuntimeInstanceLifecyclePart._run` the canonical
Server activation gate: after completed assembly and before a `ServerOperation`
is constructed or dispatched, it obtains the terminal activation result and
renders the ordinary CLI failure on error. The legacy direct `startServer`
entry keeps its explicit compatibility gate. `ServerOperation` no longer owns a
post-dispatch duplicate activation call. This lifecycle-dispatch expansion
preserves R1--R9, introduces no HTTP-server or Textus BoK change, and remains
subject to the protected Step Review Stop Gate.

## Split Record P70-ACTIVATION-SPLIT-001

The user selected `phase-split` and then selected `new-phase-70.1` through
the direct answer `phase 70.1にして。`. The binding target decision is
`P70-ACTIVATION-SPLIT-TARGET-002`, request identity
`fe4ba4860e492c68867d5690dc5776fe5131d665a16e11801623749c6b66173c`,
resolved-request SHA-256
`44868d7a2dda29d352489acc3f5a68cdf2041aa2515546635c4835efe517464c`,
and answer-evidence SHA-256
`9e5f38aa54608d0282774ae43af819ccf4c35e8f0eaf17ee667f74e5eec51b53`.

This historical split record is superseded for the nine paths by
`P70-DEC-ACCUMULATOR-ADMISSION-003`. Phase 70 now owns the active protected
runtime activation accumulator and its executable specifications. The
untracked Phase 70.1 planning files remain preserved without acceptance
authority; the paths in the superseded transfer record are:

- `src/main/scala/org/goldenport/cncf/component/ComponentActivation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/ServerOperation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceClientPart.scala`;
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`;
- `src/test/scala/org/goldenport/cncf/component/ComponentActivationLifecycleSpec.scala`; and
- `src/test/scala/org/goldenport/cncf/cli/ServerOperationActivationSpec.scala`.

The focused validation receipt `P70-CA70-02A-VAL-014` remains provenance only;
it is not an acceptance or release receipt. Textus BoK Phase 8 remains a
separately owned consumer, and no BoK source or consumer behavior is accepted
by this Phase.

## Phase Plan Gate

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: high
- runtime_suitability: re-evaluate in the Phase execution task
- source: P70-DEC-ACCUMULATOR-ADMISSION-003 and the frozen R1--R9 contract

## Purpose

Freeze and deliver the explicit component activation lifecycle contract after
complete Subsystem assembly and runtime-service binding but before managed
server readiness. The admitted protected runtime delivery is owned by Phase
70 under CA70-02 / CA70-02A. Components with startup work that
depends on other installed components must not perform that work during
`Component.initialize`, infer readiness from construction order, or require an
external HTTP call after the server becomes visible.

Textus BoK Phase 8 is the first consumer. It requires SIE and the complete
component graph before it can publish configured KnowledgeSource generations,
and control-center must observe startup failure rather than a ready server with
an empty or degraded catalog.

## Dependency and scheduling

Phase 70 is a separately selectable supplier branch after the closed Phase 55
configuration-binding contract. It does not renumber or block the independent
Phase 63--69 plans. Only one CNCF Phase may be active at a time.

Phase 70 consumes the existing component bootstrap, context injection,
runtime-service binding, typed configuration, execution profile, shutdown, and
structured diagnostic contracts. It does not reopen their accepted semantics.

## Selected direction

- Add one typed opt-in activation capability for a Component. Activation is
  distinct from object construction and `Component.initialize`.
- Assemble, bootstrap, inject, and bind the complete admitted component set
  before invoking any activation callback.
- Invoke activation exactly once in deterministic admitted component order for
  managed server startup and complete every required activation before
  readiness is reported. A bound listener, when required by the host lifecycle,
  must not expose the application as ready or admit ordinary application
  traffic before activation completes.
- Supply an activation context derived from the assembled Subsystem and its
  typed runtime configuration/capabilities. Do not expose CLI parsing,
  control-center internals, credentials, or mutable global configuration.
- Let a component use normal CNCF component/API selection and
  ExecutionContext-aware contracts during activation; do not provide direct
  implementation-object lookup or bypass component boundaries.
- A required activation failure fails startup, prevents readiness, emits one
  structured redacted diagnostic, and enters ordinary Subsystem cleanup.
- Bound activation by explicit timeout/cancellation policy and preserve
  deterministic sequential outcome reporting. No detached background task may
  later convert a failed startup into ready state.
- Direct `command` and `client` modes do not implicitly run server activation.
  Test and emulator use require explicit runtime-test/assembly admission and
  deterministic cleanup.
- Preserve existing components unchanged: absence of the opt-in capability has
  no behavior, readiness, or compatibility effect.

## Scope

1. Freeze activation lifecycle states, callback/API shape, execution modes,
   ordering, timeout, failure, readiness, observability, cleanup, and
   supplier-only consumer contracts.
2. Record the required insertion point after `_prepare_components_c` has
   bootstrapped/injected components and bound runtime services, at the final
   managed assembly point before server readiness.
3. Record the structured activation result/diagnostic constraints and exact
   executable evidence matrix for the active Phase 70 accumulator.
4. Implement and validate the admitted protected runtime source/test
   accumulator and retain focused validation provenance without treating it as
   release or Textus BoK consumer acceptance.
5. Record Textus BoK Phase 8 as the separately owned representative consumer
   and prohibit a source edit or consumer acceptance claim in this parent.

## Non-goals

- A configuration language for invoking arbitrary Operations at startup.
- A general Workflow, Job, scheduler, service-container, health-check, or
  deployment orchestration framework.
- File watching, hot reload, restart control, rolling deployment, distributed
  activation, leader election, or cluster readiness.
- Allowing components to depend on initialization order or access another
  component's implementation class directly.
- Moving application-specific KnowledgeSource, SIE, Glossary, or profile logic
  into CNCF.
- Running managed server activation implicitly for every CLI command.

## Work stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CA70-01 | Contract and supplier handoff | Exact lifecycle point, API, ordering, mode, readiness, timeout, failure, cleanup, diagnostics, and consumer boundary are frozen and independently reviewed for the retained parent handoff. | done |
| CA70-02 | Runtime implementation | Protected runtime activation work converged under the re-admitted accumulator and its accepted same-boundary closure repair. | done |
| CA70-03 | Managed runtime and consumer acceptance | Managed-runtime evidence remains nonterminal; Textus BoK consumer implementation remains separately owned and unaccepted. | transferred |
| CA70-04 | Parent validation and closure | The independent full-review and focused closure-review gates are clean; the distinct final release commit binds final repository validation. | done |

## Acceptance

- The R1--R9 contract and admitted accumulator fix the public
  capability/context boundary, precise post-assembly placement, deterministic
  once-only behavior, execution-mode isolation, terminal failure semantics,
  cleanup, and redaction rules.
- The active accumulator provides the representative dependency, readiness,
  timeout, command/client, controlled-test, and diagnostic evidence required by
  the contract.
- The parent records the re-admitted ten-path accumulator plus the one
  same-boundary internal Http4s test-adapter repair path, and clearly labels
  every inherited validation receipt as non-acceptance provenance.
- The supplier handoff identifies Textus BoK Phase 8 without importing its
  source, configuration, profile selection, or publication ownership.
- The parent contract/handoff receives independent review while the active
  accumulator and separately owned Textus BoK consumer remain unaccepted.
- Any later Phase 70.1 planning remains non-authoritative and cannot accept
  these paths or Textus BoK Phase 8 consumer behavior.

## Closure Record

The protected runtime accumulator completed after the mandatory Phase review
and the accepted focused closure re-review
`Phase 70 / CA70-02A / SBR2 lifecycle and readiness repair / focused re-review 2`.
That re-review sealed the three same-lineage blockers: terminal empty-target
activation, terminal waiter semantics, and real managed-server readiness
before publication. Its focused evidence
`P70-CA70-02A-VAL-SBR2-002` passed all 16 lifecycle and managed-server tests.

The additional
`src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala` path is a
package-private, thread-local controlled-test adapter required to prove the
ordinary managed-server route; it introduces no public API or Textus BoK
consumer behavior. The final repository-wide validation and distinct release
commit bind this closed Phase state. Phase 70.1 remains planned and preserved,
not adopted by this closure.

## Planning references

- [Phase 70 Checklist](phase-70-checklist.md)
- Phase 70.1 Protected Component Activation Lifecycle (preserved untracked
  planning artifact; no acceptance authority)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md)
- [Phase 53 ComponentStyle and Capability Resolution](phase-53.md)
- [Phase 55 Typed Configuration Binding](phase-55.md)
- `src/main/scala/org/goldenport/cncf/component/Component.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala`
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`
- `textus-bok:docs/phase/phase-8.md`
