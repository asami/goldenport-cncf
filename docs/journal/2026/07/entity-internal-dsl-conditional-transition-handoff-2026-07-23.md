# Entity internal DSL: conditional transition handoff

date=2026-07-23
requester=textus-cbd-support Phase 8 P8-43
status=accepted-as-future-development-item

> Strategy disposition (2026-07-24): accepted as
> `9.39 Entity Conditional Transition Internal DSL` in
> `docs/strategy/cncf-development-strategy.md`. This annotation records the
> later planning decision without rewriting the original handoff.

CBD Support must retain a failed/cancelled/expired/incompatible Review Run and
then allow exactly one successor owner for the same exact reuse key. A normal
Entity update can race: two callers could both replace the active Run pointer.

Add a protected Entity DSL conditional transition primitive with these rules:

- atomically load a stable Entity identity and verify an explicit immutable
  predicate (for CBD: current state is one specified terminal state and active
  Review ID is the expected terminal Run);
- create or bind the successor Run and update only the active-run/root state
  in one datastore-native transaction;
- return either `Transitioned` or `NotMatched(existing)`, never overwrite a
  nonmatching record;
- preserve Entity authorization, CallTree, audit, view invalidation, component
  datastore scope, and no component SQL/JDBC/raw DataStore access;
- prove simultaneous attempts yield exactly one successor owner on SQLite and
  a shared datastore profile.

This is not a public CRUD operation. CBD needs it only through a protected
`ServiceInternal` workflow after the former terminal Run snapshot has been
retained.

## Delivery boundary

This is a CNCF framework capability, not a small CBD Support follow-up.  It
requires datastore-native atomicity, a typed internal-DSL contract, security
and observability preservation, and concurrency proof across supported store
profiles.  It therefore belongs to a dedicated CNCF phase; CBD Support P8-43
keeps its terminal-history contract and resumes successor ownership only after
that phase supplies the verified primitive.

CBD Support must not substitute process-local locking, raw SQLite/JDBC, a raw
DataStore call, or a review-local cache while this handoff remains planned.
