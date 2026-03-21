# AV-01 Cozy Escalation Note (CML/AST Source-of-Truth)

status=issued
date=2026-03-21
owner=cncf-runtime
target=cozy

## Reason

AV-01 requires CML/AST shape freeze for Aggregate/View, but parser/modeler ownership is Cozy.

To avoid forking CML truth in CNCF, AST updates must be implemented in Cozy.

## Required Payload

1. Required AST shape

```scala
EntityDef(
  ...,
  aggregate: Option[AggregateDef],
  view: Option[ViewDef]
)
```

```scala
AggregateDef(commands, state, invariants)
ViewDef(attributes, queries)
```

2. Minimum semantics and constraints
- Aggregate: command input + invariant enforcement + `(new state, events)` output
- View: event-driven projection, rebuildable from event stream
- Event: independent from view definition
- command/read boundary: no cross-side mutation

3. Backward compatibility expectation
- Breaking change is allowed for this workstream.

4. Expected CNCF integration contract
- Cozy outputs deterministic AST for CNCF generator/runtime adapter consumption.
- CNCF does not introduce a parallel CML grammar.

## CNCF Side While Waiting

- Keep semantic boundary fixed in docs/design.
- Keep adapter/runtime boundary notes ready for AV-02.
- Do not implement competing parser/modeler in CNCF.

