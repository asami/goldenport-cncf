# CML StateMachine Normalization Contract

Status: normative static specification

Architectural responsibility is described in
`docs/design/statemachine-boundary-contract.md`. This specification covers the
non-Workflow CML StateMachine declaration after parsing and before generated
ABI or CNCF UnitOfWork execution. Workflow DSL grammar remains owned by the
separate Cozy workflow workstream.

## Normalization boundary (SMN-1)

One already-parsed non-Workflow CML StateMachine declaration MUST normalize to
one closed `NormalizedStateMachine` value or return deterministic structural
diagnostics. Normalization MUST NOT execute an action, invoke a resolver,
mutate an Entity, access a provider, or start a Workflow.

During Phase 63, that value is carried by
`MComponent.StateMachineDefinition.normalization` as either an accepted value
or structured diagnostics. The pre-existing `StateMachineTransitionRule`
remains a legacy generation carrier only; Phase 63.1 owns generated-ABI
propagation and CNCF execution. This is one explicit compatibility bridge, not
a second selection engine.

The normalized value MUST retain explicit machine version, initial state,
terminal states, direct composite-parent relation, named shallow-history
metadata, transition source and target, trigger, priority, declaration order,
guard, action phase, and source location.

The current parsed non-Workflow AST does not retain physical file offsets.
`StateMachineSourceLocation` therefore carries a required, deterministic AST
declaration path. It MUST NOT invent a line or column. A future parser may add
physical spans to that value without changing any semantic identity.

## Typed semantic identity (SMN-2)

Machine, state, event/trigger, transition, guard, action, and trigger-context
identities MUST be distinct nominal value types. They are semantic declaration
identities, not persisted Entity IDs, `UniversalId` values, hashes, or keys used
to create a persistent entity.

A machine identity is its declared qualified name. A state identity contains
its machine identity and its declared state path. A trigger identity contains
its machine identity and declared event name; a trigger-context identity
contains that trigger identity. A transition identity contains its machine
identity and its explicit declaration order. An action identity contains its
transition identity and phase/order. These values are stable for an unchanged
machine version; an authored reordering is an intentional
declaration-order and transition-identity change.

The current CML syntax normalizes to machine version `1` unless an explicitly
admitted version declaration is added by a later compatible syntax change.
Identity construction MUST be direct and typed; it MUST NOT derive a
surrogate identifier by concatenation-and-hash or by fabricating an Entity ID.
The value types reject empty declaration names, empty state paths, and negative
declaration/action order. A normalized transition rejects source, target,
trigger, action, or history metadata from a different machine.

## Candidate ordering and selection (SMN-3)

The pure selection contract is exactly:

1. retain transitions matching machine, current source state, and trigger;
2. order them by `(priority asc, declarationOrder asc)`;
3. evaluate the guard of each candidate;
4. continue after `false`;
5. stop and return the guard failure on evaluation or binding failure; and
6. return the first matching transition or the structured no-match result.

The pure core represents a match as `TransitionSelectionOutcome.Selected` with
one `TransitionPlan`: its candidate state is exactly the selected target and
its effect vector is declaration metadata only. Selecting a plan MUST NOT run
an effect or mutate the input state. `NoMatch` is a distinct successful
selection outcome; guard/binding failure remains the enclosing failure.

Normalization MUST reject duplicate transition identities and duplicate
`(machine, source-state, trigger, priority, declarationOrder)` tuples. It MUST
not invent a global order to compensate for an omitted declaration order.

For the current AST, the explicit declaration order is the normalizer's fixed
structural traversal: each declared state in source order, its call transitions
then global transitions in source order, the rule-owned transitions in source
order, then each one-level composite in source order by the same rule. The
normalizer records this structural path in `StateMachineSourceLocation` and
derives the ordinal only from that fixed traversal; it never uses an incidental
caller collection position.

The currently admitted non-Workflow CML syntax has no priority clause.
Normalization therefore emits the typed default priority `0`; a negative or
otherwise malformed priority has no representable normalized value. A later
priority syntax must validate before it can create a transition declaration.

## Predicate and trigger context (SMN-4)

An executable normalized expression guard MUST be a closed, side-effect-free
`PredicateProgram`. It has an explicit version and a closed predicate tree.
Version 1 admits only boolean literals, equality and
inequality of typed scalar values, boolean `all`/`any`/`not`, and presence
tests over one declared trigger-context field.

The version-1 trigger context contains only declared scalar fields for event
name, target identifier, current state, and candidate state. A program MUST
name one of those fields; arbitrary object traversal, ambient `state`,
untyped `ctx`, service access, datastore access, filesystem, process,
network, clock, randomness, reflection, class loading, and scripting are not
admitted.

Programs are bounded to depth 8, 64 nodes, and 256 UTF-8 bytes for any literal
or declared binding name. Missing fields, incompatible scalar types, an
unsupported node, or a limit breach MUST be a structured normalization or
evaluation failure, never a dynamic lookup fallback.

## Named bindings and actions (SMN-5)

A declared named guard normalizes to `StateMachineGuardProgram.Named`, which
is distinct from `PredicateProgram`. A `StateMachineGuardBindingResolver` MAY
evaluate that reference only through the declared trigger-context schema. A
missing, ambiguous, or failed binding is a terminal failure, not a false guard.
One normalized transition has exactly one trigger: a disjunction containing an
event-trigger guard is rejected as `ambiguous-trigger`, rather than selecting
the first event or lowering that event to `Always`.

Exit, transition, and entry actions normalize to ordered named local-action
references. Absence means no action for that phase; a normalizer MUST NOT
invent an empty or no-op action to stand in for a required declaration. This
Phase defines action identity and ordering only; generated action ABI and local
effect execution are Phase 63.1 work.

## State topology (SMN-6)

Initial state and terminal transition status MUST be explicit in the normalized
model. `StateMachineTopology` holds one-level composite structure and direct
leaf membership; named shallow-history targets retain their fallback leaf.
A topology direct leaf MUST have a state path formed by exactly one additional
segment after its composite path. A declared state from elsewhere in the same
machine MUST NOT be admitted as a direct leaf, shallow-history fallback, or
history-write leaf merely by appearing in a topology collection.
The normalized declaration also retains its optional history field and required
typed history writes. The normalizer MUST NOT flatten a composite state or
discard parent/leaf identity. Deep history, orthogonal regions, and arbitrary
nesting are not admitted.

When a declaration is owned by an Entity, a named history field MUST resolve to
that Entity's declared attribute during normalization. An unresolved history
field is an `invalid-history-field` diagnostic, not deferred generated-code
behavior.

## Legacy raw-expression admission (SMN-7)

`MComponent.RuleGuard.Expression` and CNCF `ExpressionGuard` are legacy
surfaces. A raw expression MUST NOT be placed in `PredicateProgram` or silently
executed by MVEL on the new contract path.

A compatibility normalizer MAY translate a recognized version-1 predicate
shape into `PredicateProgram`. Every other raw expression MUST produce the
deterministic `legacy-raw-expression-not-admitted` diagnostic together with
the transition identity and source location. Generated ABI compatibility and
any migration of existing generated components are explicitly deferred to
Phase 63.1.

## Required diagnostics (SMN-8)

Normalization MUST distinguish at least: duplicate identity/order; missing,
ambiguous, or invalid trigger; invalid source/target state; invalid priority; invalid initial
or terminal state; invalid composite/history relation; unsupported predicate;
predicate bound/type failure; missing or ambiguous binding; and unadmitted
legacy raw expression. Diagnostics MUST contain semantic identity and source
location, but MUST NOT expose raw expression text or evaluated trigger values.

## Executable evidence (SMN-9)

The focused specifications for this contract are:

- `TransitionDeciderSpec` for ordering, false-guard continuation, and failure;
- CNCF `TransitionSelectorPropertySpec` and `GuardRuntimeSpec` for adapter
  conformance and rejection of legacy raw execution on the new path; and
- Cozy `ModelerStateMachineProjectionSpec` for non-Workflow normalization,
  source order, typed guard output, topology, and diagnostics.

`PredicateProgramSpec` adds typed evaluation, missing-field, structural-limit,
and ScalaCheck UTF-8-limit evidence. `GuardRuntimeSpec` remains evidence for
the explicitly retained legacy runtime path; it does not make raw MVEL part of
the normalized contract.

These focused specifications do not constitute generated ABI, UnitOfWork, or
aggregate full-suite evidence.
