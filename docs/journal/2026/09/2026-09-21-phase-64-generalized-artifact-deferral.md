# Phase 64 Generalized Composite Artifact Deferral

Date: 2026-09-21
Status: accepted planning decision

## Context

Phase 64 SWF-07/08 initially pinned the closed Cozy Phase 62.3 Workflow fixture
and ABI as the producer evidence needed by Phase 77 and `sm-workflow`. A later
Phase-wide review correctly found that this Workflow ABI does not itself carry
the complete generalized Composite StateMachine configuration, derivation,
causal/action provenance, definition-version pins, and producer diagnostics
described by the broad SWF-06 design.

The initial handoff document therefore overstated what its pinned ABI proved.
Cozy was not modified, and accepted SWF-01 through SWF-06 design records were
not rerun.

## Decision

Separate the immediate minimum from the future generalized contract.

- Phase 64 accepts only the released Cozy Phase 62.3 Workflow subset needed by
  Phase 77 and the first `sm-workflow` vertical slice.
- The broad SWF-06 generalized artifact remains a valid future design target,
  but its production and admission are not Phase 64 completion requirements.
- Cozy Phase 66 owns future artifact production.
- CNCF Phase 89 owns future schema admission, compatibility diagnostics,
  ComponentFactory discovery, and runtime projection.
- Both future Phases start only after the first `sm-workflow` vertical slice
  and a concrete consumer requirement.
- Neither future Phase blocks Phase 64, Phase 64.2, Phase 77, or
  `sm-workflow` Phase 1.

## Correction to the Phase 64 fixture handoff

The released fixture proves Workflow identity/version/source, ordered Actions,
the `ReviewChange` Required SPI, and its typed service operation. It does not
prove the complete generalized Composite artifact. The Phase 64 handoff is
therefore accepted only for the Phase 77 / `sm-workflow` minimum.

CNCF must not compensate for the absent generalized fields by reparsing CML,
inferring semantics from names, or writing a handwritten canonical contract.

## Consequence

The critical path remains:

```text
Cozy Phase 62.3 (closed)
  -> CNCF Phase 64
  -> CNCF Phase 64.2
  -> CNCF Phase 77
  -> sm-workflow Phase 1
```

The independent future path is:

```text
stable first sm-workflow vertical slice
  + concrete consumer requirement
  -> Cozy Phase 66
  -> CNCF Phase 89
```

Commit `c41aef9` remains in history. This decision and the corrected handoff
document supersede only its overbroad evidence claim; they do not discard its
valid pinned Workflow fixture evidence.

## Closure exception

Phase 64 was closed with `outcome=success`, `release_disposition=forced`, and
`assurance=exceptions-recorded` under the user's explicit 2026-09-21
direction. A successful V1 force-release commit makes the resulting baseline
eligible for successor continuation without representing the missing assurance
as complete.
The normal release adapter required a fresh repository-full SBT receipt because
Scala and test commits followed the older Phase 63.2 receipt. Re-running the
full suite would have restarted heavyweight closure processing after all Phase
64 Steps and `P64-FULL-REVIEW-001` had already been accepted.

The exception is limited to `fresh-full-suite-not-refreshed`. It does not
reinterpret or delete prior evidence, claim that the older receipt covers later
bytes, waive the accepted review, or authorize future reuse. The forced release
contains only the eight Phase 64 closure/planning documents, including the
future Phase 89 plan; it contains no Scala or test change.
