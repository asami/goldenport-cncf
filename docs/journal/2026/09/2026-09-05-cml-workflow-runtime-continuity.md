# CML Workflow Runtime Continuity

Date: 2026-09-05
Status: design decision / Phase 64 planning adjustment

## Context

Phase 63 already plans the StateMachine path as a complete CML-to-runtime path:

```text
CML StateMachine
 -> parse / normalize
 -> SimpleModeler generation
 -> typed generated definition
 -> ComponentFactory automatic bootstrap
 -> CNCF StateMachine runtime
 -> CommittedTransition
```

The earlier Phase 64 plan correctly separated StateMachine local lifecycle
semantics from Workflow orchestration, but its wording still made CML Workflow
look partly like a binding layer around a CNCF-owned WorkflowDefinition.

CML now has Workflow as a modeling construct. Therefore Workflow should follow
the same ownership and generation pattern as StateMachine rather than acquiring
an independent CNCF modeling language.

## Decision

Workflow is CML-first.

```text
CML Workflow
 -> parse / normalize
 -> SimpleModeler generation
 -> typed generated definition
 -> ComponentFactory automatic bootstrap
 -> CNCF Workflow runtime
 -> WorkflowInstance
 -> Operation / Job
```

CML owns Workflow declaration semantics. CNCF owns execution semantics.

CNCF must not compensate for missing CML semantics through name inference,
status matching, raw runtime strings, or a second Workflow DSL.

Focused unit tests may construct runtime definitions directly, but end-to-end
acceptance must start from CML source.

## Continuity with StateMachine

The two runtime paths are deliberately symmetric:

```text
CML
 +-- StateMachine -> generated SM -> CNCF SM runtime -> CommittedTransition
 |
 +-- Workflow     -> generated WF -> CNCF WF runtime -> WorkflowInstance
```

Their runtime responsibilities remain different:

- StateMachine decides and commits valid local domain transitions.
- Workflow advances durable cross-Operation progression.
- Workflow never directly writes the domain state controlled by StateMachine.
- A Workflow-selected Operation that changes domain state re-enters the normal
  StateMachine boundary.

This yields the execution cycle:

```text
StateMachine
 -> CommittedTransition
 -> Workflow
 -> Operation / Job
 -> StateMachine
 -> CommittedTransition
 -> ...
```

## Cozy coordination

Phase 64 is explicitly cross-repository.

Cozy/SimpleModeler must carry Workflow from CML source to generated runtime
metadata with stable identities for Workflow, trigger/entry, step, condition,
StateMachine/transition binding, Operation reference, terminal outcome, source
location, and ABI/version where required.

CNCF should consume those generated contracts through the same style of
ComponentFactory bootstrap used for StateMachine integration.

The precise accepted CML Workflow syntax and current implementation state must
be inventoried in Cozy before Phase 64 freezes its canonical runtime model.
CNCF must not invent syntax in advance of that inventory.

## Runtime scope

CNCF implements the executable semantics represented by the accepted CML
Workflow model. Runtime capabilities should evolve with CML semantics rather
than forming a separate feature language.

Durability, idempotency, recovery, concurrency, authorization integration,
Operation/Job invocation, and observability are CNCF runtime concerns even when
they are not surface-language constructs.

Specialist engines remain appropriate for orchestration semantics outside the
accepted CML/CNCF built-in Workflow contract.

## Acceptance principle

The representative SalesOrder scenario must prove:

```text
CML SalesOrder / SalesStatus / SalesOrderWorkflow
 -> Cozy parse/normalize
 -> SimpleModeler generation
 -> generated Workflow metadata/provider
 -> ComponentFactory automatic bootstrap
 -> CNCF StateMachine commit
 -> CommittedTransition
 -> CNCF WorkflowInstance progression
 -> next Operation / Job
 -> Phase 63 StateMachine enforcement
```

A manually injected Workflow provider or hand-written WorkflowDefinition is not
sufficient acceptance evidence.

## Documents adjusted

- `docs/phase/phase-64.md`
- `docs/phase/phase-64-checklist.md`

A corresponding Cozy journal records the producer-side coordination decision.
