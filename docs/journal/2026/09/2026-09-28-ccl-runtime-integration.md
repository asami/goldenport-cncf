# CCL Runtime Integration: Initial Direction

Date: 2026-09-28

## Context
CCL (CML Constraint Language) is being designed in Cozy/CML as an OCL-derived constraint expression language. Its types are the existing CML semantic types, including CML DataTypes and multiplicities `1`, `?`, `+`, `*`, and `[m..n]`.

The CNCF discussion is intentionally less mature than the CCL language design. This journal records only the execution-side consequences identified so far.

## Separation of Responsibilities
The current direction is:

```
CCL
  expresses the condition

CML
  defines the Constraint and its semantic context

CNCF
  evaluates/uses the condition during execution
```

This prevents the runtime from creating a second constraint language or type system.

## Evaluation Context
A common evaluation-context mechanism appears useful. Different CML contexts can bind different names while using the same CCL evaluator.

Operation contexts may provide `self`, parameters, `result`, and pre-state. Workflow contexts may provide workflow instance/state/variables and relevant action results. StateMachine guards provide transition context. Admission provides candidate/admission context.

The exact API/SPI remains open.

## Runtime Semantics Identified So Far
Preconditions, postconditions, guards, workflow conditions, and admission conditions may all use CCL but have different execution meanings. Failure handling must therefore belong to the owning runtime concept rather than CCL itself.

A precondition failure is an operation-contract concern. A guard false result means a transition is not eligible. An admission condition participates in Candidate-Admission semantics and may expose an Admission Gap. These should not be collapsed merely because their expressions all return Boolean.

## Pre-State
Postconditions may require an operation-start snapshot analogous to OCL `@pre`. The snapshot boundary should be defined by CNCF/CML operation execution semantics. No concrete representation has been selected yet.

## Evidence
The original discussion came from intuitionistic/constructive logic. A useful runtime idea is to optionally turn successful constraint validation into evidence or a refined value.

This is deliberately optional:

```
constraint evaluation -> Boolean
                     -> optional higher-level Evidence
```

CCL is not a theorem prover, and CNCF does not need formal-proof guarantees. Scala exceptions, effects, external systems, and other practical runtime concerns remain normal engineering concerns.

## CAR Lint Boundary
CAR lint should perform basic semantic checks as soon as CCL is available. More ambitious static reasoning is desirable but can wait.

The important near-term requirement is that semantic type, multiplicity, source location, and model references are not discarded, so later lint can reason about impossible cardinalities, contradictions, redundant constraints, and always-true/false expressions.

## Follow-Up
Future CNCF work should decide the evaluator API/SPI, execution-context representation, pre-state snapshot semantics, evaluation failure model, Guard/Workflow/Admission integration, evidence policy, and observability.

No phase closure dependency is introduced by this journal yet; this is an initial handoff from the CCL design work.


## Consequence and Absence Decision
Two runtime decisions were added.

First, CCL excludes `null`. Absence is represented by CML multiplicity and therefore arrives at runtime as part of the resolved CML/CCL semantics rather than as a special CCL value.

Second, CCL evaluation failure uses the existing Consequence abstraction.

For Constraint execution this gives three materially different outcomes:

```
success(true)  -> satisfied
success(false) -> unsatisfied
failure        -> evaluation failed
```

The owning runtime concept must preserve this distinction. In particular, a false guard is not a guard-evaluation failure, and an unsatisfied Admission condition is not an Admission evaluator failure.

This removes the need for an OCL-style `invalid` value or a new CCL-specific result hierarchy.


## ConstraintContext Schema to Runtime Instance
The CCL design now treats ConstraintContext as the binding contract between CML semantics and CNCF execution.

CML derives a ConstraintContextSchema from the placement of a Constraint. CCL uses that schema for static name/type resolution, including implicit self. CNCF later creates a ConstraintContextInstance containing actual runtime values for the same bindings.

This means runtime evaluation receives a resolved semantic expression and bound context; it does not redo source-level implicit-self or name-resolution rules.

The owning placement also carries execution semantics: a Constraint referenced by Transition.guard is interpreted as transition eligibility, while one referenced by Operation.preconditions is an operation contract condition. Constraint itself can remain small and shared.

Pre-state for postconditions is consequently viewed as a temporal view of context bindings; the concrete snapshot implementation remains open.
