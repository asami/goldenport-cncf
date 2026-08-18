# CML StateMachine Runtime Completion Provisional Specification

status = proposed, non-normative
date = 2026-08-12
target_phase = 63

## Status and Authority

This note records the provisional contract for Phase 63. It does not override
current source, generated ABI, design/specification, or Executable
Specifications. Accepted behavior must be promoted to `docs/design` and
`docs/spec` after implementation and verification.

## Problem

CML, SimpleModeler, SimpleModeling, and CNCF already contain StateMachine
layers, but the end-to-end execution contract is incomplete. The current
surface includes canonical core primitives and deterministic ordering,
generated transition rules, CNCF planning and pre-persistence hooks, and
transition observability. Investigation also found risks or gaps around
generated no-op actions, named guard resolution, priority propagation, raw
MVEL expression evaluation, duplicate core/CNCF selection logic, bypassing
execution routes, and failure visibility after rollback.

Phase 63 makes the existing model genuinely executable before Workflow or DbC
depends on it.

## Ownership

| Layer | Owns | Must not own |
| --- | --- | --- |
| CML/Cozy | Source syntax, parsing, normalization, source diagnostics | Runtime state mutation or provider effects |
| SimpleModeler | Typed generated definitions, bindings, ABI, metadata | A separate transition algorithm |
| Core StateMachine | Pure candidate selection, guard semantics, deterministic ordering | CNCF context, persistence, Workflow, external I/O |
| CNCF StateMachine adapter | Binding resolution, candidate-state/effect planning, UnitOfWork integration | Redefinition of core selection rules |
| Aggregate/UnitOfWork | Atomic local mutation, persistence, commit/rollback | External workflow progression before commit |
| Event/observability | Committed envelope and durable diagnostic visibility | Claiming a rolled-back transition succeeded |

## Provisional Closed Model

The initial model is conceptual; names remain subject to SMR-01/SMR-02.

```text
StateMachineDefinition(
  id,
  version,
  entityType,
  statePath,
  initialState,
  finalStates,
  states,
  events,
  triggerContexts,
  transitions
)

StateDefinition(
  id,
  parentState?,
  kind = leaf | composite,
  initialDirectLeaf?,
  shallowHistory?
)

TriggerContextDefinition(
  id,
  version,
  fields
)

TransitionRule(
  id,
  sourceStates,
  targetState,
  trigger,
  priority,
  declarationOrder,
  guard,
  localEffects,
  sourceLocation
)

GuardProgram = PredicateProgram | NamedGuardRef
LocalEffect = ClosedLocalEffect | NamedLocalEffectRef
```

Every stable identity above must survive parsing, generation, execution,
Record/JSON projection, and diagnostics.

The canonical model preserves the current one-level composite-state and named
shallow-history contract. It does not flatten a composite and its direct leaves
into unrelated state names when that would lose entry, history, or diagnostic
meaning. Deep history, orthogonal regions, and arbitrary-depth nesting are not
introduced by Phase 63.

Initial and final-state semantics must be explicit in the canonical model.
Legacy declarations that inferred the initial state from declaration order need
one versioned admission rule; absence must not produce repository-dependent or
generator-dependent behavior.

## Predicate Contract

Expression guards use one closed, typed, versioned, side-effect-free
`PredicateProgram`. The initial subset should be limited to literals,
typed field/path selection, equality/order comparison, boolean composition,
and bounded empty/non-empty/size predicates.

The evaluator must define null/missing/unknown behavior, type errors, limits,
and version incompatibility deterministically. It must not expose service,
database, file, process, network, environment, clock, random, reflection,
class-loading, scripting, or arbitrary-function access.

Each predicate evaluates against one bounded, typed, versioned trigger context.
CML event payload, Operation trigger input, generated metadata, runtime
`TransitionEvent`, replay, and diagnostics must agree on that context schema.
Missing or incompatible trigger fields are structured admission/evaluation
failures rather than dynamic lookup accidents.

Named guards remain explicit runtime bindings. Missing, ambiguous, or failed
bindings are failures, not `false`.

Legacy raw MVEL guards require an explicit compatibility/migration decision.
New required programs must not silently fall back to MVEL.

## Transition Decision

The canonical pure decision is:

1. obtain transitions matching machine, source state, and trigger;
2. order by `priority asc`, then `declarationOrder asc`;
3. evaluate each guard;
4. continue on guard `false`;
5. stop with failure on guard/evaluator failure;
6. select the first admitted transition; or
7. return a structured no-match outcome.

CNCF may adapt runtime values into this decision. It must not implement a
second rule with different ordering or failure semantics.

## Candidate State and Effect Boundary

An admitted transition first creates a `TransitionPlan`:

```text
TransitionPlan(
  machineId,
  transitionId,
  entityId,
  sourceState,
  targetState,
  triggerId,
  candidateEntity,
  localEffects,
  correlation
)
```

Local effects may update the candidate entity or other resources explicitly
admitted to the same UnitOfWork. They must be deterministic under retry or
carry an explicit idempotency contract.

External HTTP, service, process, message, notification, or unmanaged database
effects are not local transition actions. They run only after commit from a
`CommittedTransition`/domain event, normally through an Operation or Job.

## Provisional Execution Order

For an existing entity:

1. authenticate/authorize and admit the triggering Operation/event;
2. load entity and version;
3. resolve the explicit machine/trigger binding;
4. select one transition using core semantics;
5. build the candidate entity and local effect plan;
6. execute exit, transition, and entry local effects in the frozen order;
7. validate target-state applicability;
8. stage persistence and the successful transition outcome;
9. commit the UnitOfWork atomically; and
10. expose/publish the `CommittedTransition` envelope.

Create, update/save, patch, command, retry/replay, direct/unversioned, and
legacy compatibility paths must be covered. A path that cannot honor the
machine must reject explicitly instead of bypassing it.

The canonical acceptance path is not satisfied by wiring a hand-written
`CollectionTransitionRuleProvider` directly into a test. At least one
representative slice must start from CML source, pass through Cozy and
SimpleModeler generation, publish the generated provider, be automatically
bootstrapped by ComponentFactory, and cross the real UnitOfWork persistence and
post-commit event boundary.

## Committed Transition Envelope

Only a successful UnitOfWork commit produces this envelope:

```text
CommittedTransition(
  occurrenceId,
  componentId,
  entityType,
  entityId,
  machineId,
  machineVersion,
  transitionId,
  sourceState,
  targetState,
  triggerId,
  operationId?,
  commitId,
  occurredAt,
  correlationId,
  traceId?,
  spanId?
)
```

`occurrenceId` is the downstream idempotency/replay identity. The envelope
contains identity, not arbitrary entity or event payload. A no-match, failure,
rejection, cancellation, or rollback cannot produce it.

## Failure Semantics

The structured outcome vocabulary must keep at least these cases distinct:

- invalid/unknown source state;
- no matching transition;
- ambiguous or invalid transition definition;
- missing/ambiguous named guard or action binding;
- predicate admission/evaluation failure;
- local action/effect failure;
- invalid target/candidate state;
- concurrency or persistence failure;
- cancellation/interruption; and
- rollback/commit failure.

Safe facets include machine, transition, entity type/id, source/target, event
or trigger, operation, guard/action phase, component, trace/span, job/task,
exception class, and bounded safe cause. Raw state, input/event payload,
credentials, secrets, expression source, and evaluated values are excluded.

A false guard is selection non-match, not evaluator failure. Interruption and
fatal Throwable behavior retain their existing priority.

## Observability

The transition attempt and outcome should be one correlated CallTree path with
bounded metrics/audit projection. Domain rollback must remove staged domain
changes and success events, but not the canonical diagnostic needed to explain
the failure. SMR-01 must freeze whether that diagnostic is persisted outside
the UnitOfWork or captured by an already durable parent execution record.

Record, JSON, DetailCode, HTTP, and shell/CLI projections must carry the same
semantic identity and redaction rules.

## Workflow and DbC Boundaries

Phase 63 stops at a committed local transition.

- Phase 64 Workflow consumes `CommittedTransition` and chooses a next
  Operation; it does not change the transition algorithm.
- Phase 65 DbC reuses `PredicateProgram` and wraps Operation/Aggregate
  checkpoints; it does not become the StateMachine executor.
- A guard false, transition rejection, evaluator failure, and DbC violation
  remain distinct even when correlated in one execution trace.

## Development Candidate Alignment

Phase 63 absorbs no broad future candidate implicitly.

- Strategy 9.2 retains generic transaction outcome lanes, reception policy,
  same-job continuation, source overrides, and JCL event orchestration. Phase
  63 defines only the StateMachine-specific committed envelope.
- Strategy 9.4 retains platform retention, cleanup, authorization, exporters,
  dashboards, durable metrics, and operationalization. Phase 63 supplies only
  its required transition/rollback diagnostic evidence.
- Strategy 9.7 retains general taxonomy hardening, catalog generation,
  application/CLI codes, and trace UX. Phase 63 adds only closed transition
  outcomes and facets required for execution.
- Strategy 9.10 retains compensation-of-compensation and human recovery
  events. Phase 63 only ensures external effects occur after commit.
- Strategy 9.53 retains general ComponentFactory purity/capability-evidence
  policy. A Phase 63 named guard/action binding may consume a minimal compatible
  evidence adapter but cannot settle the generic Factory contract.
- The Aggregate method implementation note contributes only its
  `pattern:state-machine`/`pattern:state-transition` slice. General
  implementation kinds, inline/external Scala, other built-in patterns, and
  broad factory/Operation reuse remain provisional outside Phase 63.

## Compatibility

- Existing core StateMachine semantics remain canonical.
- Generated legacy components require explicit version/admission behavior.
- Legacy raw MVEL and no-op action behavior must be identified and migrated or
  rejected; it cannot be silently represented as the Phase 63 contract.
- Existing lifecycle event names may be adapted, but only a post-commit event
  may satisfy `CommittedTransition` semantics.
- Direct/unversioned compatibility routes must not create an enforcement hole.

## Executable Specification Matrix

Phase 63 evidence should cover:

- deterministic priority/declaration order;
- multiple candidates and guard false/failure;
- typed predicate totality, limits, and malformed programs;
- named guard/action success, missing, ambiguity, and failure;
- generated priority, identity, guard, action, and ABI preservation;
- explicit initial/final semantics, one-level composite structure, named
  shallow history, and typed trigger-context preservation;
- candidate-state construction and exit/transition/entry order;
- create/update/save/patch/command and compatibility paths;
- action/persistence/concurrency/cancellation/interruption rollback;
- success-envelope commit ordering, uniqueness, duplicate, and replay;
- failure observability after rollback and public non-leakage; and
- a generated `SalesOrder`/`SalesStatus` slice through the real CNCF runtime,
  without substituting a manually injected transition provider for automatic
  generated-component bootstrap.

## Open Decisions

1. Exact CML syntax for explicit initial/final states, typed predicates, typed
   trigger contexts, and named/local effects.
2. Exact initial predicate node and built-in set.
3. Whether local effects can update multiple same-UnitOfWork entities.
4. Canonical core/CNCF adapter API and retirement of duplicate selectors.
5. Legacy declaration-order initial-state and MVEL admission periods/version
   boundaries.
6. Generated action ABI and dependency-injection/binding scope.
7. Exact success-envelope persistence/outbox mechanism.
8. Canonical durable diagnostic path for rolled-back attempts.
9. Unversioned/direct compatibility behavior and migration deadline.

## Deferred Scope

- Workflow progression and WorkflowInstance lifecycle.
- DbC pre/postconditions and Aggregate invariants.
- Timers, parallelism, human tasks, compensation, and connectors.
- Distributed transaction coordination.
- Arbitrary scripting or rules-engine integration.

## Related Documents

- `docs/phase/phase-63.md`
- `docs/phase/phase-63-checklist.md`
- `docs/notes/statemachine-workflow-alignment-provisional-specification.md`
- `docs/notes/cml-executable-design-by-contract-provisional-specification.md`
- `docs/notes/aggregate-method-implementation-strategy.md`
- `docs/design/statemachine-boundary-contract.md`
- `docs/design/execution-platform-boundary.md`
- `docs/journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md`
