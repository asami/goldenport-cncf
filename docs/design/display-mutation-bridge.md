# Internal Display update program design

Document role: settled PHASE-96 / P96-07A architecture decision.
Status: implementation authored; parent verification and focused validation pending.
The [bridge contract](../spec/display-mutation-bridge.md) owns behavior.
The [runtime design](display-model-runtime.md) retains Entity/Aggregate and
transaction authority. Original P96-07 criteria remain OPEN.

## Retained typed registration

The package-private bridge is a program constructor. Its private non-case
EntityUpdate value retains logical selection, explicit Entity owner, canonical
collection, the canonical persistent and a logical-to-storage map admitted
through that persistent. There is no callback writer or Record/Any mutation
facade. Registration rejects aliases because selected logical fields must name
independent storage keys. Existing managed-field policy determines excluded
names; the bridge adds no parallel naming grammar or guessed inverse mapping.

Preparation and program admission remain separate boundaries. Prepared[P]
contains application-typed input and declared target evidence. The bridge checks
that evidence against its server-owned registration and inspects the canonical
changes Record privately. Update.noop[String] seed fields keep partial editing
from writing unselected fields. Unexpected non-unchanged seed fields and managed
overrides fail before a program is returned. Preparation revision comparison
cannot substitute the provider's atomic comparison.

## Canonical UnitOfWork boundary

The bridge retains exact supplied authorization and strict policy in one
observed Entity operation. Reusing this operation preserves the interpreter's
existing authorization extensions, transition/domain validation, revision
comparison, write policy, authoritative snapshot, resident reconciliation and
View invalidation. No new runtime activation or alternate datastore is added.
The program constructor never owns commit, abort, response receipt or reload.

An ordinary Service Action must obtain its authorization from framework Action
admission before using this internal adapter. The adapter checks scope but does
not authenticate a caller or synthesize System permission. Source identity is
logical component-name/resource-type correspondence plus complete definition
and provenance correspondence. Resource collection is compared as the existing
typed canonical value; EntityId is never parsed or rendered to choose an owner.

## Executable fixture separation

The new development fixture reuses the canonical versioned mutation fixture's
construction pattern and actual revision-binding support. A recording wrapper
delegates to the real searchable in-memory provider, counting entries and actual
applied changes while keeping all state in that provider. It is not a fake
writer. Registered resident state and a View cache expose canonical coherence.

The existing pure mutation fixture uses an Aggregate description target. The
new BridgePatch maps both title and description to explicit Entity BridgeItem
targets, with Update[String] fields. The capture-detail definition contributes
static preparation metadata only. Domain rejection and independently configured
canonical authorization/transition rejection prove that successful preparation
does not confer execution authority. Sample values and investigation history
are recorded only in the [dated journal](../journal/2026/10/2026-10-06-phase-96-display-mutation-bridge.md).

## Retained future ownership

This settled internal update boundary does not waive strict atomic delete,
create/delete lifecycle proof, Aggregate integration, ordinary mutation routes,
committed outcomes or actual contextual postcommit reload. Later integration
retains the selected ActionEngine afterCommitResponseC hook and all original
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime).
CLI/HTTP/SPI/MCP/packaging/generator contracts have no new registration, schema
or grammar to consume here. Existing read and pure mutation consumers retain
their accepted source and focused regression obligations.
