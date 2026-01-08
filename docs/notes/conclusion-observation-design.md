# Conclusion & Observation Design — Typed Failure Semantics with Descriptive Records

status = draft
since = 2026-01-08

## Purpose

This note defines a strict boundary between **logic-bearing failure semantics**
and **descriptive observational data** in CNCF.

The goals are to:

- Preserve clear execution logic based on typed outcomes
- Enable rich observability without contaminating control flow
- Maintain explainability for logs, events, and AI/RAG projections

## Core Types and Roles

### Conclusion (Logic-bearing)

`Conclusion` represents the **semantic outcome** of an execution step.

- It may be used for control-flow decisions
- It is typed and stable
- It carries minimal, structured failure semantics

Typical conceptual fields include:

- causeKind (Defect / Fault / Anomaly)
- status or error code
- strategy (retry, escalate, etc.)

Conclusion participates directly in execution logic.

### Observation (Descriptive)

`Observation` represents **what was observed** during execution.

- It must not participate in control-flow decisions
- It may be rich, verbose, and contextual
- It is safe to project to logs, events, traces, or records

Observation may include:

- human-readable messages
- correlation identifiers
- timestamps
- stack traces (optional)
- attributes / records

Observation explains. It does not decide.

## Logic Boundary Rule

### Allowed

Execution logic may branch on:

- the presence or absence of Conclusion
- Conclusion.causeKind
- Conclusion.status or equivalent typed codes

Example:

```scala
outcome match {
  case Left(conclusion) if conclusion.isRetryable => retry()
  case Left(_)                                    => fail()
  case Right(_)                                   => succeed()
}
```

### Forbidden

Execution logic must not branch on data contained in Observation.

```scala
// NOT allowed
if (observation.message.contains("timeout")) retry()
```

Observations are descriptive and unstable;
they are not a reliable logic boundary.

## Canonical Rule

If a condition must affect execution logic,
it must be represented in Conclusion, not inferred from Observation.

Bad:

```scala
Observation(message = "timeout occurred")
```

Good:

```scala
Conclusion(
  causeKind = CauseKind.Fault,
  status = Status.Timeout
)
```

Observation may still include explanatory messages,
but logic depends only on typed Conclusion data.

## Relationship to ObservabilityEngine

- ObservabilityEngine reads both Conclusion and Observation
- It builds descriptive Records only
- It never performs branching or control logic

Typical mapping:

- Conclusion → error.kind, error.code
- Observation → messages, attributes, context

## Summary

- Conclusion is logic-bearing
- Observation is descriptive only
- Logic depends on typed data
- Descriptive data explains but never decides

This boundary ensures CNCF remains
deterministic, explainable, and observable.

See also:
- observability-engine-build-emit-design.md
- observability-record-namespace.md

CONSTRAINTS
- Do NOT modify any existing files
- Do NOT add links or indexes
- Do NOT change wording or structure

EXPECTED RESULT
- New design note exists at docs/notes/conclusion-observation-design.md
- No code changes

END
