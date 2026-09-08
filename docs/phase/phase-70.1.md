# Phase 70.1 - Superseded Documentation Retirement

status=in_progress
disposition=retire-superseded
current_stage=ACT70.1-04
current_slice=superseded-documentation closure
planned_at=2026-09-08
split_from=[Phase 70](phase-70.md)
depends_on=[Phase 70](phase-70.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 70.1 Checklist](phase-70.1-checklist.md)
contract=[Component Activation Lifecycle Specification](../spec/component-activation-lifecycle.md)
consumer=textus-bok Phase 8

## Retirement Disposition

The user selected `retire-superseded` for this documentation-only Phase 70.1
boundary. Phase 70 accepted and released the protected component activation
implementation at commit
`a7ce382245e20d52b517b323200805927e8045c0`. Phase 70.1 therefore owns no
source, executable specification, test, runtime, validation, or consumer
implementation. Its former implementation plan is retained here only as a
historical retirement and closure record.

Phase 70 remains the sole accepted CNCF activation implementation authority;
its [Phase 70](phase-70.md) and [Phase 70 Checklist](phase-70-checklist.md)
are closed historical anchors and are not edited by this Phase. Textus BoK
Phase 8 remains a separately owned consumer and is neither implemented nor
accepted by this documentation boundary.

This Phase remains `status=in_progress` while its own documentation boundary
awaits mandatory independent full review and release closure. No final Phase
70.1 closure is claimed by this record.

## Historical Split Authority

This child was created by split record `P70-ACTIVATION-SPLIT-001`. The user
selected `new-phase-70.1` in a direct request; the binding target decision is
`P70-ACTIVATION-SPLIT-TARGET-002`, request identity
`fe4ba4860e492c68867d5690dc5776fe5131d665a16e11801623749c6b66173c`, and
resolved-request SHA-256
`44868d7a2dda29d352489acc3f5a68cdf2041aa2515546635c4835efe517464c`.

The historical split authority is preserved as provenance only. It was
superseded by Phase 70's accepted re-admission and release of the protected
runtime accumulator at `a7ce382245e20d52b517b323200805927e8045c0`. The
transferred implementation paths consequently have no Phase 70.1 authority:

- `src/main/scala/org/goldenport/cncf/component/ComponentActivation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/ServerOperation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceClientPart.scala`;
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`;
- `src/test/scala/org/goldenport/cncf/component/ComponentActivationLifecycleSpec.scala`; and
- `src/test/scala/org/goldenport/cncf/cli/ServerOperationActivationSpec.scala`.

The focused validation receipt `P70-CA70-02A-VAL-014` remains provenance only.
The accepted Phase 70 implementation and release evidence are authoritative
for CNCF; no parent receipt is treated as Textus BoK Phase 8 acceptance.

## Closure Boundary

The only remaining Phase 70.1 work is documentation reconciliation and its
independent closure gate:

- preserve the `ACT70.1-01` through `ACT70.1-03` identities and ledger entries
  as explicit `NOT APPLICABLE — superseded by Phase 70` dispositions;
- record the exact separation between Phase 70's accepted supplier
  implementation and the separately owned Textus BoK Phase 8 consumer; and
- keep `ACT70.1-04` in progress until the mandatory independent full review,
  any admitted repair/re-review, and this Phase's release closure are complete.

No source, test, runtime, validation, ABI, configuration, or consumer change
is in scope. No Phase 70 historical anchor, lifecycle specification, or Textus
BoK artifact is edited by this Phase.

## Acceptance and Exit Conditions

Phase 70.1 can close only after its four owned documents agree that the former
implementation plan is superseded, all transferred implementation entries are
explicitly not applicable, the separate Textus BoK boundary is preserved, and
the mandatory independent full review and release evidence for this
documentation boundary are complete. Until then, this Phase is visibly on the
`retire-superseded` path and remains pre-release.

## References

- [Phase 70](phase-70.md)
- [Phase 70 Checklist](phase-70-checklist.md)
- [Phase 70.1 Checklist](phase-70.1-checklist.md)
- [Component Activation Lifecycle Specification](../spec/component-activation-lifecycle.md)
- `textus-bok:docs/phase/phase-8.md`
