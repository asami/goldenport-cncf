# CCL Constraint Execution Model

## Status
Initial runtime note. CCL itself is owned by CML/Cozy. CNCF concerns begin where a resolved CML Constraint must be evaluated or used to control execution. This note intentionally records only the first execution-side implications.

## Boundary
```
CCL: expression and semantic condition
  -> CML: Constraint context and model meaning
  -> CNCF: evaluation and execution behavior
```

CNCF must not redefine CCL syntax or the CML type system.

## Evaluation Context
Runtime evaluation needs a generalized context populated from the executing model element. Candidate bindings include `self`, operation parameters, runtime/model variables, `result`, pre-state/snapshot references, workflow variables/state, state-machine transition context, and action results where exposed by the CML contract.

The evaluator should receive an already resolved semantic expression plus an evaluation context. Runtime should not reconstruct the CML type system ad hoc.

## Execution Uses
- Operation precondition: contract condition required before execution.
- Operation postcondition: condition evaluated with inputs/result and, where needed, pre-state.
- StateMachine guard: transition eligibility; semantically distinct from an operation precondition even when both evaluate Boolean.
- Workflow condition: condition evaluated against workflow instance/state/variables as defined by CML.
- Admission condition: CCL expresses the condition; Candidate-Admission execution interprets failure/success, including Admission Gap semantics.
- Capability satisfaction: Capability remains first-class; CCL may express a satisfaction condition where appropriate.

## Pre-State
OCL `@pre` is a useful surface precedent. CNCF runtime semantics should define it explicitly in terms of operation execution and snapshot boundaries rather than importing OCL runtime semantics wholesale.

## Evaluation Result
The runtime should not import the complete OCL `null` / `invalid` value model. At minimum it must distinguish successful evaluation producing a CML value (normally Boolean for Constraint) from evaluation failure. The concrete CNCF error/result model remains open.

## Evidence
Constructive-logic ideas may be used above ordinary Boolean evaluation:

```
CCL Constraint -> Boolean evaluation -> Validation / Admission / Guard -> optional Evidence
```

Evidence is not required for every Constraint and is not part of CCL Core. Scala implementations may later use refined values, typed evidence, given values, or domain-specific result types where useful.

## Multiplicity
Resolved CCL expressions retain CML multiplicity (`1`, `?`, `+`, `*`, `[m..n]`). Runtime/projection should consume this semantic information rather than create an independent collection/optional interpretation.

For Scala projection it may determine direct access versus map/flatMap and operations such as forall, exists, and filter.

## Diagnostics and CAR Lint
Static semantic validation should happen before runtime whenever possible. CAR lint initially handles basic CCL/CML semantic errors; advanced reasoning can be added later.

Source/model references should survive into executable metadata where practical so runtime failures can be diagnosed against the originating constraint.

## Open Issues
- evaluator API/SPI
- pre-state snapshot boundary and representation
- evaluation failure model
- integration with Guard and Workflow runtime APIs
- Candidate-Admission integration
- generic versus model-specific Evidence production
- observability/event representation for constraint evaluation
- coexistence of interpreted evaluation and compiled projection

These are follow-up topics. This note establishes the runtime boundary, not a complete runtime specification.
