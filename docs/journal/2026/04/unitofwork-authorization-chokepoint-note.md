# UnitOfWork Authorization Chokepoint Note

Date: 2026-04-07

## Summary

The preferred authorization mechanism for CNCF is a two-stage chokepoint model:

1. `ActionCall` execution is the outer chokepoint.
2. `UnitOfWorkInterpreter` execution is the inner chokepoint.

The operational flow remains:

1. `ActionCall` exposes protected internal DSL methods.
2. Those protected methods issue `UnitOfWork` operations.
3. `UnitOfWorkInterpreter` becomes the inner authorization chokepoint.
4. The same inner chokepoint also remains responsible for observability and actual
   execution.

This gives a cleaner and safer design than relying on `SERVICE` / `OPERATION`
metadata alone.

## Two Chokepoints

### ActionCall Execution

`ActionCall` execution is the outer chokepoint.

Its role is:

- coarse-grained action admission;
- metadata-driven pre-checks;
- service/operation-level exception handling;
- action-level observability.

This is where CNCF should decide whether an action may enter the execution path at
all.

### UnitOfWork Execution

`UnitOfWorkInterpreter` execution is the inner chokepoint.

Its role is:

- fine-grained resource/system authorization;
- protected resource access enforcement;
- execution-time observability;
- actual execution against stores and engines.

This is where CNCF should decide whether a concrete domain/system access may really
be executed.

## Internal DSL Candidate

The first candidate for the internal DSL is the protected method set already
provided by `ActionCall`.

This is the natural place to guide handwritten application logic.

`ActionCall` should be allowed to orchestrate behavior, but ordinary domain/system
resource access should go through protected APIs that are designed to issue
`UnitOfWork` requests.

## Why UnitOfWorkInterpreter

`UnitOfWorkInterpreter` is the best current chokepoint because it already sits on
the route to real execution.

It is also a natural place for:

- authorization;
- observability;
- execution against framework-owned stores and engines.

If CNCF enforces authorization there, then:

- application entry points share the same protection;
- handwritten `ActionCall` logic is less likely to become a security hole;
- authorization and observability are aligned on the same execution route.

## Authorization Subject

The authorization subject should be provided by `ExecutionContext`, specifically by
its `SecurityContext`.

This means the principal/caller information used for authorization should not be
invented or carried separately by individual actions.

The intended structure is:

- `ExecutionContext`
  - runtime-scoped execution state;
- `SecurityContext`
  - auth subject;
  - principal;
  - role;
  - privilege;
  - permission-related identity information.

## Mechanism

The intended mechanism is:

1. `ActionCall` decides what to do.
2. Protected internal DSL methods express the intent as `UnitOfWork`.
3. `UnitOfWorkInterpreter` receives:
   - the `UnitOfWork`;
   - the `ExecutionContext`;
   - therefore the `SecurityContext`.
4. `UnitOfWorkInterpreter` performs authorization.
5. If authorization succeeds, the interpreter performs:
   - observability handling;
   - actual store/engine execution.

This makes `ExecutionContext(SecurityContext)` the provider of the auth subject, and
`UnitOfWorkInterpreter` the inner enforcement point within the two-stage model.

## Consequence for SERVICE and OPERATION

Under this model, `SERVICE` and `OPERATION` authorization declarations are not the
ordinary place where normal permissions are defined.

They become:

- coarse-grained admission hints;
- metadata for generated help/introspection;
- exception declarations;
- override hooks for promotion, degradation, or bypass.

Ordinary resource access control should live below them.

## Consequence for ActionCall.authorize()

`ActionCall.authorize()` remains useful, but it is no longer the complete story.

It should be interpreted as:

- action admission;
- metadata-driven pre-check;
- exceptional policy hook.

It should not be the only enforcement point for ordinary resource access.

In the two-stage model, `ActionCall.authorize()` belongs to the outer chokepoint.

## Design Implication

This mechanism implies the following architectural rule:

- direct raw store/engine access from application logic should become non-canonical;
- the canonical path should be protected `ActionCall` methods that emit
  `UnitOfWork`;
- authorization should be evaluated in the interpreter using
  `ExecutionContext(SecurityContext)`.

This means CNCF should explicitly treat:

- `ActionCall` execution;
- `UnitOfWork` execution;

as the two main security and observability chokepoints.

## Transitional Status

Current CNCF is still transitional:

- `ActionCall.authorize()` exists;
- `ENTITY` / `ACCESS` metadata exists;
- `OperationAccessPolicy` helpers exist;
- ordinary action code can still stay close to store access.

The target direction recorded here is to preserve both chokepoints while moving
ordinary resource authorization toward the inner `UnitOfWorkInterpreter` chokepoint
without losing existing metadata-based capabilities.
