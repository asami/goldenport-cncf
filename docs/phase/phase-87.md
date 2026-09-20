# Phase 87: Generalized Composite StateMachine Artifact Admission

status=planned
execution_priority=deferred_until_sm_workflow_vertical_slice
entry_condition=concrete_consumer_requirement
planned_at=2026-09-21
producer=[Cozy Phase 66](https://github.com/asami/cozy/blob/main/docs/phase/phase-66.md)
runtime_baseline=[Phase 77](phase-77.md)
checklist=[Phase 87 Checklist](phase-87-checklist.md)

## Purpose

Admit the generalized Composite StateMachine semantic artifact produced by
Cozy Phase 66, validate its schema and compatibility fail closed, expose it
through normal CNCF discovery, and project its admitted semantics into the
Composite StateMachine runtime without parsing CML or inferring missing facts.

Phase 87 is the CNCF consumer half of a future producer/consumer sequence:

```text
Cozy Phase 66
  generalized Composite StateMachine semantic artifact
        -> CNCF Phase 87
             admission
             compatibility diagnostics
             ComponentFactory discovery
             runtime projection
```

This sequence is not on the Phase 64 -> Phase 64.2 -> Phase 77 ->
`sm-workflow` Phase 1 critical path.

## Entry condition

Phase 87 must not start until all of the following hold:

1. the first `sm-workflow` vertical slice has reached a stable result;
2. a concrete consumer requirement identifies semantic data not supplied by
   the current released Workflow/Composite artifacts;
3. Cozy Phase 66 has produced an accepted versioned artifact and handoff for
   that requirement; and
4. the then-current CNCF runtime baseline and compatibility policy have been
   inventoried.

The existence of the broad Phase 64 SWF-06 design is not sufficient entry
evidence. Re-estimate and split this Phase only after the concrete requirement
and Cozy handoff are available.

## Input contract

The admitted Cozy artifact is expected to carry, as required by the concrete
consumer:

- artifact schema and generator identity/version;
- Composite StateMachine definition identity and pinned version;
- exact constituent roles, referenced definition versions, subjects, and
  configuration;
- complete typed derivation rules with stable identity, exact inputs, outputs,
  declaration order, and provenance;
- typed logical action and occurrence descriptors with ownership, order,
  correlation/causation, execution metadata, and provenance;
- source/model/location provenance; and
- typed producer diagnostics for reachability, coverage, overlap, ambiguity,
  and incompleteness.

The Cozy Value Objects and semantic artifact are upstream authority. CNCF owns
admission and runtime projection, not CML syntax or producer reconstruction.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| GCSA-87-01 | Inventory the accepted Cozy Phase 66 handoff and freeze supported schema/generator/definition compatibility. | planned |
| GCSA-87-02 | Implement typed fail-closed admission and structured rejection diagnostics. | planned |
| GCSA-87-03 | Bind admitted artifacts to ComponentFactory discovery without name inference or handwritten substitution. | planned |
| GCSA-87-04 | Project admitted constituent/configuration/rule/action/provenance semantics into the existing Composite StateMachine runtime. | planned |
| GCSA-87-05 | Prove positive, incompatible, incomplete, ambiguous, foreign, stale-version, and provenance rejection fixtures. | planned |
| GCSA-87-06 | Freeze the concrete consumer handoff and record all still-deferred runtime extensions. | planned |

## Admission rules

- Unknown or incompatible artifact/schema/generator versions fail closed.
- Definition and constituent versions are exact; CNCF does not substitute the
  current registry version.
- Missing rules, diagnostics, action occurrences, or required provenance are
  not reconstructed from CML, names, source text, or handwritten definitions.
- Producer diagnostics remain attributable and are not replaced by a
  CNCF-local second analysis language.
- ComponentFactory exposes only admitted artifacts.
- Runtime projection preserves identity, configuration, derivation,
  correlation/causation, ordering, and provenance without redefining them.

## Non-goals

- Blocking or reopening Phase 64, Phase 77, or `sm-workflow` Phase 1.
- Parsing CML inside CNCF.
- Defining Cozy syntax, IR, generator behavior, or producer diagnostics.
- Reimplementing the Phase 77 Workflow API/SPI, Provider, Continuation, or
  persistence runtime.
- Retry, timeout, scheduling, compensation, recovery, orchestration, REST,
  MCP, UI, or Flutter expansion.
- Admitting unrelated Cozy worktree changes merely because they are near the
  producer implementation.

## Completion

Completion requires an accepted Cozy Phase 66 handoff, versioned fail-closed
admission, ComponentFactory discovery, runtime projection, focused and
repository-appropriate validation, independent review, and the exact concrete
consumer handoff. No completion is implied by Phase 64 documentation alone.

## References

- [Phase 87 Checklist](phase-87-checklist.md)
- [Phase 64 minimum/future split](phase-64.md)
- [Phase 77 runtime baseline](phase-77.md)
- [Phase 64 generalized-artifact deferral decision](../journal/2026/09/2026-09-21-phase-64-generalized-artifact-deferral.md)
- [Future Cozy Phase 66 producer](https://github.com/asami/cozy/blob/main/docs/phase/phase-66.md)
