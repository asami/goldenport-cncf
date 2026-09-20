# Composite State-Machine Identity and Derivation

This document is the canonical SWF-02 design contract for Composite-general
semantics. It deliberately defines neither a runtime profile nor an execution
mechanism. A composite workflow remains a later minimal profile.

The accepted [SWF-01 semantic inventory](composite-workflow-semantic-inventory.md)
and the [Phase 64 plan](../phase/phase-64.md) with its [Phase 64
checklist](../phase/phase-64-checklist.md) provide the SWF-02 authority and
closure context. This document freezes the identity and derivation contract
within that boundary.

## 1. Composite definition identity

A composite state-machine definition has an immutable, producer-provided
`CompositeStateMachineDefinitionIdentity` and version. This identity/version
pair identifies the definition supplied by its producer; neither is derived
from a display name, source text, or any other raw source material. A generated
rule also has its own immutable generated definition identity. That identity
identifies the rule definition, not an occurrence that later results from
applying it.

Each composite definition declares stable, unique constituent roles. For every
role it retains a stable, unique definition-local `ConstituentBinding` record:

| Item | Contract |
| --- | --- |
| Role key | A stable unique key within the composite definition. |
| Constituent reference | The immutable identity and version of the admitted constituent definition. |
| Subject reference | An optional typed subject reference. Its type and presence are part of the declared role contract. |
| Transition authority | Local authority retained by the constituent for its own transitions. The composite does not take that authority. |

A role key is not a constituent name, and constituent identity/version is not
inferred from a name or raw source.

## 2. CompositeConfiguration

`CompositeConfiguration` is an immutable, total mapping from every declared
role key to its admitted constituent configuration. Each admitted
`CompositeConfiguration` entry contains its role, the referenced
constituent definition identity and version, an optional typed `subjectRef`
when applicable, and the current typed state identity. It is valid only when
all of the following hold:

Each complete admitted configuration snapshot has one immutable
`CompositeConfigurationIdentity`. This identity is distinct from the
composite definition identity, the generated-rule-definition identity, and the
derived-occurrence identity. It identifies that one complete admitted
configuration snapshot and is carried as before/after configuration evidence
by a derived occurrence.

- every declared role is present exactly once;
- no role is represented more than once;
- no mapping has a role outside the composite definition;
- each mapping references the definition identity and version admitted for that
  role; and
- the mapping satisfies any declared optional typed-subject constraint.

Construction rejects missing, duplicate, foreign, version-inconsistent, and
unadmitted entries. It does not infer entries, roles, or identities from names
or raw source.

### Positive configuration example

Assume composite definition `order-fulfilment@3` declares these roles:

| Role key | Admitted constituent definition |
| --- | --- |
| `payment` | `payment-authorisation@5` |
| `shipping` | `shipment-booking@2` |

The configuration below is total and admitted:

| Role key | Constituent definition | Constituent state identity |
| --- | --- | --- |
| `payment` | `payment-authorisation@5` | `authorised` |
| `shipping` | `shipment-booking@2` | `booked` |

It is not valid to substitute `payment-authorisation@4`, add a `catalogue`
entry, or omit `shipping`.

## 3. Generated-rule derivation

Generated-rule derivation is pure: producer-provided pure ordered rule
identities evaluate one complete admitted `CompositeConfiguration` snapshot
and, when exactly one rule matches, produce the specified
`CompositeStateIdentity`. The ordered rules do not provide a priority fallback:
success requires exactly one generated rule to match. No result may be inferred
from raw CML, names, or status, and derivation does not commit work or cause a
transition.

| Match result | Result |
| --- | --- |
| Exactly one match | Return that generated rule definition. |
| No matches | Reject with structured `UnmappedConfiguration`. |
| More than one match | Reject with structured `AmbiguousConfiguration`. |

The structured rejection identifies the composite definition identity/version,
the configuration, and the candidate set (empty for an unmapped configuration)
needed to diagnose the derivation result. It does not manufacture a fallback
rule.

### Unmapped example

For `order-fulfilment@3`, suppose no generated rule admits the configuration
`payment=declined`, `shipping=booked`. Derivation rejects it as
`UnmappedConfiguration`; it does not select a rule based on the role names or
on event text.

### Ambiguous example

If two distinct generated rule definitions both declare the admitted
configuration `payment=authorised`, `shipping=booked`, derivation rejects it
as `AmbiguousConfiguration` and retains both candidate definition identities.
It does not resolve the conflict by source ordering, a generated name, or any
other incidental value.

## 4. Derived transition occurrences

A derived transition occurrence may be recorded only after the constituent
transition has committed. After exactly one Phase 63.2 `CommittedTransition`
occurrence commits, the composite reconstructs the new `CompositeConfiguration`,
evaluates derivation, and emits a derived occurrence only when the derived
`CompositeStateIdentity` changes. Under sequential-v1 converse cardinality, one
causal `CommittedTransition` occurrence may yield at most one derived
transition occurrence. Its one sequential cause is that Phase 63.2
`CommittedTransition` occurrence. Raw event/status inference, pre-commit
triggering, and construction from an uncommitted transition are forbidden.

The occurrence is post-commit-only and has a stable occurrence identity that
is distinct from the generated definition identity selected by pure derivation.
It records:

- the pinned composite `CompositeStateMachineDefinitionIdentity` and version;
- the generated definition identity;
- the single causal Phase 63.2 `CommittedTransition` occurrence identity;
- the before and after `CompositeConfiguration` identities and composite state
  identities;
- the causal `UnitOfWork` reference;
- correlation; and
- occurrence time.

Recording this occurrence does not transfer a constituent's local transition
authority to the composite.

## 5. Responsibility boundaries

| Concern | Assigned boundary |
| --- | --- |
| CML grammar and producer semantics | Cozy |
| Action planner/interpreter alignment | Phase 64.2 |
| API, SPI, bootstrap, provider runtime | Phase 77 |
| Durable persistence, protocol, continuation | Phase 77 |
| Two-phase commit, compensation, recovery | Phase 85 |

Accordingly, SWF-02 specifies no CML syntax, classes, method signatures, wire
encoding, durable-persistence behaviour, provider runtime, or action set.
