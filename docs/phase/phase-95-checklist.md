# Phase 95 Checklist: UI-Facing View Model and Aggregate Contract

status=planned
phase=[Phase 95](phase-95.md)

This ledger is nonblocking for the Editing Studio Flutter application's fake,
local, and provisional CRUD-adapter development.

## UIV-95-01: UI-facing View Model descriptor

Stage Status:
- Current status: OPEN
- Owner: CNCF View/Operation contract owners
- Update rule: Close only when a versioned descriptor and executable fixtures cover declared collection/detail Views, identity, revision, query capabilities, actions, and result states. The checklist below is the closure basis.

- [ ] Inventory the existing `ViewSpace`, View definitions, ActionCall read methods, OpenAPI metadata, and Admin View read boundary.
- [ ] Define stable declared View identity and typed collection/detail projection descriptors without duplicating canonical View storage.
- [ ] Specify query, paging, filtering, sorting, identity, revision, and safe failure representations.
- [ ] Prove descriptor compatibility and rejection of undeclared or incompatible Views.

## UIV-95-02: UI-facing Aggregate command interface

Stage Status:
- Current status: OPEN
- Owner: CNCF Aggregate/Operation contract owners
- Update rule: Close only when a declared command can be authorized, revision-checked, dispatched through the existing Aggregate boundary, and reported with structured outcomes. The checklist below is the closure basis.

- [ ] Define typed command identity, input, expected revision, authorized subject, and result contract.
- [ ] Reuse protected Aggregate dispatch and preserve validation/invariant ownership in the Aggregate.
- [ ] Distinguish accepted, stale/conflict, validation, forbidden, and indeterminate outcomes.
- [ ] Specify projection refresh/read-after-write behavior without claiming synchronous visibility.

## UIV-95-03: Runtime binding and authorization

Stage Status:
- Current status: OPEN
- Owner: CNCF Component/View runtime owners
- Update rule: Close only when application-facing reads and commands use declared bindings and caller-scoped authorization without exposing Admin or arbitrary runtime names. The checklist below is the closure basis.

- [ ] Bind declared UI Views to component-local `ViewSpace` find/query/count and its cache policy.
- [ ] Bind declared UI actions to Aggregate commands and existing committed-mutation View invalidation.
- [ ] Reject undeclared View names, undeclared commands, and unauthorized cross-principal reads.
- [ ] Prove no Admin Operation or raw Aggregate mutation is required by an ordinary UI client.

## UIV-95-04: REST v1 projection

Stage Status:
- Current status: OPEN
- Owner: CNCF HTTP/Operation projection owners
- Update rule: Close only when REST carries the same versioned contract and machine-readable metadata with tested serialization and failures. The checklist below is the closure basis.

- [ ] Project declared View queries and Aggregate commands through existing REST v1 Operation routing.
- [ ] Publish sufficient typed metadata for a client adapter without relying on Admin OpenAPI extensions alone.
- [ ] Test pagination, filtering, revision conflict, authorization, response decoding, and compatibility failure.
- [ ] Record gRPC as a deferred transport projection, not an acceptance requirement.

## UIV-95-05: Reference-consumer handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF and Editing Studio integration owners
- Update rule: Close only when a non-admin reference consumer proves the generic contract and the Flutter migration handoff is explicit. The checklist below is the closure basis.

- [ ] Exercise one non-Candidate fixture through the authorized REST View/Command path.
- [ ] Record the mapping to Flutter's app-session View space and `ResourceDataSource`/action ports.
- [ ] Verify the Flutter app can continue fake/local/provisional CRUD work without Phase 95 completion.
- [ ] Complete focused and repository-appropriate validation, independent review, and Phase release evidence.
