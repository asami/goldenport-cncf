# Display Model runtime design

Document role: selected Phase 96 architecture decisions.
Status: implementation contract; implementation and executable verification pending;
independent P96-01C review passed and the documentary contract is accepted for
P96-01. Phase acceptance remains pending.
Scope: PHASE-96 / P96-01 / P96-01C.

The behavior contracts are [Logical UI generated ABI](../spec/logical-ui-generated-abi.md)
and [Display Model runtime](../spec/display-model-runtime.md). These decisions
extend the existing [Aggregate/View semantic boundary](aggregate-view-semantic-boundary.md),
without changing its authority. Promotion history belongs to the
[promotion journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md).

## Semantic and presentation ownership

Entity remains the source of truth; Aggregate remains the canonical write
boundary. A semantic View is independently consumable as application-typed
`V`, `Vector[V]` or `SearchResult[V]`. Display projection adds neither a second
store nor a public generic Aggregate/View runtime gateway. Domain and semantic
clients need no Display representation to consume the same View.

Static Logical UI definitions, contextual semantic View values and runtime
Display instances have separate ownership and lifetime. Cozy Phase 74 owns
the target-neutral semantics. Cozy Phase 76 produces deterministic Scala 3
source against CNCF's consumer ABI; CNCF owns admission and runtime projection.
There is no `cozy_2.12` runtime dependency, special integration cross-build,
copied Cozy semantic authority, runtime CML parser or reflection-based
generator/runtime bridge.

## Component admission and explicit bindings

The Component owns definitions and lookup. Bootstrap selects exactly one
provider, atomically admits its complete static vector, registers required
Views and application bindings, joins explicit typed bindings, and only then
exposes Display lookup/service. Component-first discovery and the no-fallback
policy are specified in [LUI96-S06/S07](../spec/logical-ui-generated-abi.md#lui96-s06-provider-selection).
Static admission may precede entity/View creation; binding join must wait for
those registrations. No partial registry or JVM-global mutable registry is
permitted, and existing Workflow/StateMachine admission remains unchanged.

Stable view/value/action binding IDs name application registration keys only.
They do not name properties, classes, routes or guessed setters. Applications
register typed sources and converters; no speculative field-name discovery,
generic DI engine or expression language is introduced. Explicit conversions
may reshape, combine or fan out, but only into declared fields and admitted
kinds, preserving deterministic order and all scoped constraints. Mutation
target registration is distinct from read conversion. A derived or combined
value has no inverse write unless separately and explicitly registered.

## Service and Operation boundary

One explicitly registered, component-scoped service has selector `display`.
Its canonical read Operations are `query` and `read`, without `list/get`
aliases. A conflicting service or duplicate Operation fails bootstrap rather
than replacing an earlier registration. With no declared definitions there
is no Display service. Ordinary canonical Operation selection, path resolution
and authorization remain authoritative; physical binding belongs to P96-04.

Runtime determines read/query/create/update/delete capability and current
availability under current authorization. Presentation values and inert
Actions cannot grant permission. Hidden, disabled and enabled action presence
is contextual data. Standard create/update/delete use the explicit canonical
mutation bridge; Business Operations execute directly through ordinary
Operations with their own input and authorization, followed by client-initiated
Display reload. Display is not a generic action tunnel, push/subscription
channel or Service Bus refresh mechanism.

## Canonical mutation authority and outcomes

Existing domain schema/constraints, Entity/Aggregate lifecycle, authorization,
managed revision, UnitOfWork, transaction and provider capability contracts
remain authoritative. Display adapters resolve admitted application mappings;
they never mutate datastores directly, patch Views or implement local-lock
concurrency. Unsupported atomic capability fails before effects. Strict
observed-revision delete requires authorized comparison and deletion inside
the canonical atomic boundary, not a check followed by ordinary delete.

The [runtime contract](../spec/display-model-runtime.md#dsp96-r06-canonical-mutation-and-concurrency)
fixes update's `WriteIfChanged + ObservedRequired`, stale-before-equality and
no-op policy, and separates rejection, committed-ready, committed-pending
projection and indeterminate acknowledgment. A committed projection failure
does not roll back the write or authorize replay. Existing non-Display APIs
and defaults remain unchanged.

## Identity and compatibility spaces

Version generated ABI, the exact Phase 74 semantic descriptor, each definition,
generated-source provenance, Display wire schema/version, Entity revision and
snapshot/correlation independently. Screen IDs are opaque presentation IDs;
runtime resource identity remains typed EntityId. Neither screen IDs nor
provenance issue resource identity. Definition/provenance revision cannot
substitute for observed EntityRevision, and a query snapshot is not a mutation
precondition. Transport encoding is a separate P96-04 decision.

## Producer/consumer handoffs

The existing [boundary handoff table](../notes/logical-ui-generated-abi-display-runtime-boundary.md#cross-repository-handoff-records)
defines this ordered development chain and its owners:

| Handoff | Owner -> consumer | Required evidence |
| --- | --- | --- |
| LUI-ABI-01 | CNCF -> Cozy | Fixed consumer revision and usable source/artifact coordinates, ABI version, public types/provider signature, semantic mapping and unsupported subset, hand-written expected-generated fixture, admission diagnostics and focused projection proof |
| LUI-GEN-01 | Cozy -> CNCF | Fixed producer revision and generator command, Phase 74 source fixture/provenance, target LUI-ABI-01 identity, actual generated Scala/provider files and deterministic producer evidence |
| LUI-ACCEPT-01 | CNCF -> both ledgers | Exact LUI-GEN-01 source compilation against the matching ABI, normal ComponentFactory admission, equal List/Detail values/IDs/order, negative admission cases and runtime-classpath boundary evidence |

Every receipt must identify exact revisions, source/artifact coordinates and
actual commands/results. Documentary promotion issues no receipt. A proposal
or hand-written fixture alone cannot prove actual generated acceptance.
LUI-ABI-01 does not wait for whole Phase 96 closure or public publication;
LUI-ACCEPT-01 is shared focused P96-03/Cozy P76-05 evidence. There is no mutual
whole-Phase closure dependency. Repository release validation remains separate.

## Consumer scope

Editing Studio Phase 2 owns application semantics; app Phase 4 owns prototype
feasibility and app Phase 5 owns real-server aggregate/E2E acceptance. They are
separately owned from CNCF and Cozy acceptance. This documentary slice admits
no production app/Entity contract or additional product root. ESS2-01 remains
a development-only reference with unsupported create/delete; separate positive
create/delete proof remains required. Context details remain P96-10, and
Phase 98 retains experiment allocation, observation and reward ownership.
