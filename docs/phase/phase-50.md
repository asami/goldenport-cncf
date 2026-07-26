# Phase 50 - SimpleEntity Revision and OCC Simplification

status=in-progress
planned_at=2026-07-24
started_at=2026-07-25
depends_on=[Phase 49](phase-49.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 50 Checklist](phase-50-checklist.md)

## Purpose

Make revision management a standard `SimpleEntity` facility and simplify CNCF
optimistic concurrency control around that single authoritative value.

Every persisted `SimpleEntity` receives a framework-managed revision whether
or not the application enables ordinary OCC. Applications select ordinary OCC
declaratively at Entity or collection scope. Atomic Conditional Transition
always compares the authoritative revision.

For Entity models that do not extend `SimpleEntity`, Phase 50 retains the
useful Phase 49 separation capability as an explicit detached-revision
extension. The extension uses the same `EntityRevision` and provider-native
atomic mutation kernel. It is not a compatibility layer and is never an
alternative representation for an ordinary `SimpleEntity`.

## Dependency

Phase 50 begins after Phase 49 closes.

Phase 49 owns the provider-native atomic Conditional Transition and
exactly-one-winner behavior. Phase 50 retains those semantics while replacing
the concurrency representation and ordinary mutation API with
`SimpleEntity.revision` on the standard path and one explicit detached
representation for non-`SimpleEntity` models.

Phase 49's provisional token names and public API are not compatibility
commitments. Its revision-field-independent datastore operation, atomic
compare-and-advance behavior, and domain-codec separation are retained as
implementation assets.

## Selected Direction

- `revision` is the only new standard `SimpleEntity` attribute.
- The model-layer datatype is validated rather than represented as an
  application-owned raw number.
- CNCF assigns the initial revision and advances it exactly once with every
  successful persistent mutation.
- CNCF maintains revision when ordinary OCC is disabled.
- Application code may read revision for diagnostics, but normal application
  logic neither receives nor supplies revision as a business parameter.
- Generated Create, Save, Update, and Query operation requests use application
  input models and never flatten the managed `SimpleEntity.revision` into
  request schema or decoder parameters.
- The omission is inheritance-aware: a non-`SimpleEntity` domain attribute
  independently named `revision` remains ordinary application data.
- CNCF acquires and propagates the revision through the Entity load,
  UnitOfWork, EntityStore, and datastore boundaries.
- Ordinary OCC is selected by a declarative Entity or collection concurrency
  policy.
- `EntityConcurrencyPolicy.Optimistic` is the deterministic default.
  `EntityConcurrencyPolicy.None` is an explicit last-write-wins selection.
- An optimistic mutation always reaches the datastore with an expected
  revision, but CNCF normally obtains that revision as managed metadata rather
  than requiring application logic to pass it.
- `RevisionPreconditionPolicy.Managed` uses the CNCF-managed current revision.
  `RevisionPreconditionPolicy.ObservedRequired` uses a revision observed by an
  ingress adapter and rejects a missing or stale observation.
- `EntityWritePolicy.AlwaysWrite` is the core mutation default and advances
  revision even when normalized business state is unchanged.
- `EntityWritePolicy.WriteIfChanged` is selected by generated Web/Form updates
  and idempotent REST routes. Equal normalized business state is a successful
  no-op that does not change revision, `updatedAt`, or mutation audit state.
- An observed revision is checked before `WriteIfChanged` equality, so a stale
  strict edit does not become a no-op success.
- `Managed + WriteIfChanged` checks authoritative business-state equality
  before managed revision conflict, so concurrent duplicate writes converge to
  one write and later no-op successes.
- `None + ObservedRequired` is invalid because required revision comparison
  contradicts explicit last-write-wins semantics.
- Write and precondition policies resolve from explicit route binding,
  operation declaration, adapter profile default, then the framework
  `AlwaysWrite + Managed` fallback.
- Revision values range from `1` through `Long.MaxValue`; an advancing mutation
  at the upper bound fails structurally without changing state.
- Conditional Transition requires `expectedRevision` under every ordinary
  concurrency policy.
- `createdAt` and `updatedAt` retain their current lifecycle roles.
- No OCC-specific timestamp is added and `updatedAt` is not used as the OCC
  token.
- The canonical API does not contain a separate
  `EntityConcurrencyToken`, `EntityMutationExpectation(token)`, or
  `EntitySnapshot[A](entity, token)` on the `SimpleEntity` path.
- Entity models that do not extend `SimpleEntity` may explicitly select a
  detached revision representation that carries the same `EntityRevision`
  beside the domain value.
- Revision representation is fixed at Entity or collection registration. It
  cannot be selected per request.
- Generated Entity metadata carries explicit `revisionModelKind` evidence and
  `revisionRepresentation`. `SimpleEntity` is forced to `Embedded`; only a
  proven non-`SimpleEntity` may explicitly select `Detached`. Missing model-kind
  evidence and conflicting declarations fail assembly instead of using
  precedence or absence-based inference.
- One Entity has exactly one authoritative revision representation:
  `Embedded` for `SimpleEntity`, or explicit `Detached` for a
  non-`SimpleEntity` model.
- Compatibility aliases, adapters, duplicate storage fields, and implicit
  legacy revision synthesis are not implemented.

## Scope

- Add `EntityRevision` and the standard `SimpleEntity.revision` attribute to
  `simplemodeling-model`.
- Reuse `simplemodeling-lib` generic datatype, schema, `ValueReader`,
  `Consequence`, and record facilities; extend core only if a genuinely
  reusable primitive is proven missing.
- Define one CNCF revision kernel shared by embedded and detached
  representations.
- Define the explicit revision-representation binding:
  `SimpleEntity` uses embedded revision, while only a non-`SimpleEntity` model
  may opt into detached revision.
- Fix the canonical initial revision and successful-mutation advancement
  rules.
- Add managed revision behavior to Entity creation, loading, mutation, soft
  deletion, restoration, and returned values.
- Reject application attempts to write the managed revision.
- Define the ordinary Entity/collection concurrency-policy model, declaration,
  precedence, and deterministic default.
- Bind `concurrencyPolicy` from generated Entity metadata and explicit
  collection runtime override into `EntityRuntimePlan`; collection override
  wins over Entity declaration, then `Optimistic` is the default.
- Define the operation/route-level write policy and revision-precondition
  policy independently from Entity concurrency policy, including declaration
  surfaces, precedence, adapter defaults, and invalid combinations.
- Keep expected revision out of normal application operation parameters.
  Transport adapters carry an observed revision only for strict edit routes.
- Define canonical business-state equality for `WriteIfChanged`, excluding
  revision, lifecycle timestamps, audit fields, and other managed metadata.
- Compare and advance revision atomically in the authoritative datastore
  mutation.
- Ensure EntitySpace and Working Set values cannot bypass the datastore
  comparison.
- Replace Phase 49's separate token type throughout EntityStore, UnitOfWork,
  protected DSL, Conditional Transition, result models, and diagnostics with
  the common `EntityRevision`.
- Refactor the Phase 49 snapshot behavior into an explicitly named detached
  revision carrier available only through the non-`SimpleEntity` extension.
- Project revision as read-only managed metadata through the REST, Form, Web,
  View, Aggregate, and generated-client surfaces that support later mutation.
- Let generated Web/Form updates retain observed revision as hidden framework
  metadata, and let strict REST routes use transport validators rather than a
  domain operation parameter.
- Add in-memory, SQLite, and one shared-provider profile with equivalent
  concurrency, rollback, restart, and admission behavior for both admitted
  representations.
- Define explicit schema/data admission or migration behavior for persisted
  records without revision.
- Update downstream users of the provisional Phase 49 API.
- After implementation and acceptance evidence pass, replace the provisional
  OCC contract in canonical design and specification documents with the
  verified `SimpleEntity.revision` contract.

## Boundaries

- Phase 50 does not add another OCC timestamp.
- Revision is framework-managed metadata, not application business data.
- Embedded revision is the canonical `SimpleEntity` representation.
- Detached revision is an opt-in extension for an Entity model that does not
  extend `SimpleEntity`; generated and ordinary `SimpleEntity` models cannot
  select it.
- Embedded and detached revision cannot coexist, mirror, dual-write, or fall
  back to one another for one Entity.
- Policy `None` means no expected-revision comparison for ordinary mutation;
  it does not stop revision maintenance.
- Policy `Optimistic` has no application-request opt-out. The framework chooses
  the admitted revision source from declared route/operation semantics.
- Core mutation defaults to `AlwaysWrite`; `WriteIfChanged` is an explicit
  route/operation policy and is the generated Web/Form and idempotent REST
  adapter selection.
- `WriteIfChanged` is state deduplication for one Entity mutation. General REST
  request replay, idempotency keys, external side effects, and multi-resource
  idempotency are not part of Phase 50. REST request idempotency and generated
  Web Form resubmission protection are tracked independently by strategy item
  `9.43 REST and Web Form Transport Idempotency`.
- `WriteIfChanged` equality and write admission are decided inside the
  authoritative provider mutation boundary. A preloaded in-memory comparison
  alone is insufficient.
- `None + ObservedRequired` is rejected during operation/route assembly.
- Revision exhaustion never wraps or saturates; an advancing mutation at
  `Long.MaxValue` fails before changing state.
- Conditional Transition is never weakened by ordinary policy `None`.
- Missing revision is not silently interpreted as zero and is not derived from
  timestamps or resident state.
- No best-effort load-check-save fallback may emulate an atomic datastore
  comparison.
- Force overwrite, merge, repair, and conflict-resolution UI remain in
  strategy item 9.40.
- Distributed consensus, leases, fencing tokens, multi-region ownership, and
  cross-provider transactions remain outside this phase.
- Phase 50 does not reopen application-specific successor or conflict policy.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SE-01 | Contract decisions and executable acceptance | Datatype ownership, embedded/detached representation matrix, policy declaration/default, initial revision, no-op behavior, migration/admission, projection, and API replacement decisions are fixed as executable expectations before implementation. | done |
| SE-02 | SimpleEntity revision model | `simplemodeling-model` provides `EntityRevision` and one standard revision attribute; generated Entity outputs consume it as managed metadata while application input variants omit it. | done |
| SE-03 | Common revision kernel and binding | Phase 49's atomic provider kernel is generalized around `EntityRevision`, and one deterministic embedded/detached binding is selected per Entity model. Registration-time binding, provider-kernel typing, and upper concurrency API replacement passed clean re-review and full release validation. | done |
| SE-04 | Embedded SimpleEntity lifecycle and OCC | CNCF initializes, loads, advances, returns, and protects embedded revision; declarative policy controls ordinary expected-revision enforcement. Clean re-review and full release validation passed. | done |
| SE-05 | Detached non-SimpleEntity extension | Explicitly admitted non-`SimpleEntity` models can use a detached revision carrier without token compatibility, dual managed representation, or implicit fallback. Clean re-review passed. | done |
| SE-06 | Conditional Transition integration | Both admitted representations use the common revision kernel and retain Phase 49 exactly-one-winner semantics. | done |
| SE-07 | Projection and transport | Standard surfaces expose embedded `SimpleEntity.revision`; detached revision appears only on explicitly revision-aware extension surfaces. | done |
| SE-08 | Provider, migration, downstream, and regression acceptance | In-memory, SQLite, MySQL, migration rules, and downstream consumers prove both representations and the standard-path simplification. | done |
| SE-09 | Confirmed design/specification contract | Verified behavior is reflected in canonical design/spec; provisional token/snapshot rules are replaced by the embedded standard and detached extension, and executable evidence references are exact. | done |
| SE-10 | Verification and closure | Full validation, clean review, strategy/history alignment, and closure evidence complete Phase 50. | done |

## Acceptance

- `SimpleEntity` gains only one new standard attribute: `revision`.
- Every newly persisted `SimpleEntity` receives the canonical initial
  revision.
- Every successful persistent mutation advances revision exactly once in the
  same atomic operation as the state change.
- `AlwaysWrite` advances revision for every admitted persistent mutation,
  including one whose normalized business state is unchanged.
- `WriteIfChanged` returns the current Entity without a datastore write when
  normalized business state is unchanged.
- A `WriteIfChanged` no-op does not advance revision, `updatedAt`, or mutation
  audit state.
- Concurrent identical `Managed + WriteIfChanged` mutations produce at most
  one write; later attempts return the authoritative equal Entity as no-op
  successes.
- Failed, rejected, conflicted, and rolled-back mutations do not advance
  revision.
- Reads do not advance revision.
- Application input cannot write the managed revision.
- Existing `createdAt` and `updatedAt` behavior remains unchanged.
- No code uses timestamps as the authoritative OCC token.
- Ordinary policy `None` records revisions without requiring
  `expectedRevision`.
- Ordinary policy `Optimistic` uses CNCF-managed expected revision without
  exposing it to normal application logic.
- `ObservedRequired` rejects missing or stale transport revision with
  structured `Consequence`/`Conclusion` failures before no-op detection.
- `None + ObservedRequired` fails deterministic assembly rather than silently
  ignoring the observed revision or strengthening the declared concurrency
  policy.
- A mutation that would advance `Long.MaxValue` fails structurally without
  changing business state, lifecycle metadata, revision, or audit state.
- Two or more simultaneous optimistic mutations using one expected revision
  produce at most one winner.
- Working Set or EntitySpace state cannot bypass the authoritative datastore
  comparison.
- Conditional Transition requires expected revision under every ordinary
  concurrency policy and retains exactly-one-winner semantics.
- Returned `SimpleEntity` values contain the authoritative resulting revision;
  detached extension results carry it in their explicit revision carrier.
- A `SimpleEntity` always uses embedded revision and never requires a detached
  carrier.
- A non-`SimpleEntity` Entity uses detached revision only after explicit
  Entity/collection admission.
- Embedded and detached revision cannot both be present for one Entity.
- Detached revision uses `EntityRevision`, the same atomic datastore kernel,
  and the same authorization, UnitOfWork, diagnostics, and observability
  boundaries as embedded revision.
- Required generated and transport surfaces round-trip observed revision as
  framework metadata without adding it to business operation parameters or
  admitting direct revision mutation.
- The runtime contains no separate concurrency-token type, implicit snapshot
  compatibility API, or duplicate managed revision field.
- Persisted data without revision follows one explicit verified migration or
  deterministic admission-failure rule.
- In-memory, SQLite, and one shared-provider profile produce equivalent
  externally observable results.
- Final design and static specification describe the implemented
  embedded `SimpleEntity.revision` contract and detached non-`SimpleEntity`
  extension, and no longer prescribe the provisional token/snapshot model.

## Verification

Phase 50 closure requires:

- failing-first Executable Specifications for each SE-01 acceptance rule;
- property-based revision lifecycle and concurrent-attempt evidence;
- focused model, EntityStore, UnitOfWork, datastore, authorization,
  observability, Working Set, View, projection, Form, and REST specifications;
- SQLite transaction/restart evidence;
- one shared-provider concurrency profile;
- downstream Conditional Transition acceptance;
- model-library and CNCF full test suites;
- relevant downstream test suites;
- `sbt --batch Test/compile`;
- `git diff --check`;
- read-only review, review-fix where required, and clean re-review;
- final reconciliation of implementation, Executable Specifications,
  `docs/design`, and `docs/spec`; and
- strategy and phase closure records aligned with the verified result.

## Final Documentation Gate

SE-09 is mandatory and occurs only after the implementation and provider
evidence are stable.

It must:

- update `docs/design/entity-conflict-and-conditional-transition.md`;
- update `docs/spec/entity-conflict-and-conditional-transition.md`;
- update `docs/design/simpleentity-storage-shape-policy.md`;
- update other canonical Entity persistence or API documents discovered by
  the implementation;
- replace the separate-token requirement with `EntityRevision`;
- define embedded `SimpleEntity.revision` as the standard and detached revision
  as the explicit non-`SimpleEntity` extension;
- record the verified concurrency-policy declaration and transport contract;
- link every normative behavior to exact Executable Specification evidence;
  and
- leave no contradictory current OCC contract.

Phase 50 cannot close with the accepted behavior present only in notes,
journal, phase documents, or source code.

## Repository Responsibility

Phase 50 spans these repositories:

| Repository | Phase 50 responsibility |
| --- | --- |
| `/Users/asami/src/dev2025/simplemodeling-lib` | Generic reusable datatype/schema/decoding support only when existing core facilities are insufficient |
| `/Users/asami/src/dev2026/simplemodeling-model` | `EntityRevision`, `SimpleEntity.revision`, model shape, and model serialization |
| `/Users/asami/src/dev2025/simple-modeler` | Generated Entity output/input family projection for the managed revision contract |
| `/Users/asami/src/dev2025/cozy` | CML generation defaults and generated-contract integration evidence |
| `/Users/asami/src/dev2025/cloud-native-component-framework` | Revision lifecycle, OCC policy, atomic persistence, DSL, projection, transport, and provider evidence |

`simplemodeling-lib` must remain independent of `SimpleEntity`, CNCF, and OCC.
An Entity-specific datatype belongs to `simplemodeling-model`.

## Planning References

- `docs/notes/simpleentity-revision-occ-simplification-proposal.md`
- `docs/journal/2026/07/2026-07-24-simpleentity-revision-occ-consideration.md`
- `docs/phase/phase-49.md`
- `docs/notes/entity-conflict-conditional-transition-implementation.md`
- `docs/design/entity-conflict-and-conditional-transition.md`
- `docs/spec/entity-conflict-and-conditional-transition.md`
- `docs/design/simpleentity-storage-shape-policy.md`

## Closure Record

Phase 50 is closed and Phase 49 remains closed. SE-01 and SE-02 established the model
and generator slices provide one validated embedded revision on generated
Entity output variants while Create, Update, and Query inputs omit revision.
Cozy defaults generated projects to
`simplemodeling-model_3:0.2.0-SNAPSHOT`, and CNCF's generated Information
family compiles and consumes the same contract. No `simplemodeling-lib`
extension was required.

SE-03B registration-time revision binding is implemented and passed clean
re-review and full release validation. `ComponentFactory` resolves every Entity declaration
before registering any collection, stores the resulting optional binding on
`EntityDescriptor`, and leaves an undeclared non-`SimpleEntity` outside
revision management. Binding sources remain component-scoped even when
different components use the same Entity name, including assembly ownership
through componentlets. Missing model-kind evidence, incomplete `SimpleEntity`
representation metadata, conflicting declarations, and
`SimpleEntity + Detached` fail before metadata mutation or partial
registration. Existing ComponentFactory specifications now use the public
consequence-aware bootstrap boundary rather than reflecting a private helper.
The isolated staged-slice full suite passed 2450 tests in 350 suites with 7
canceled, 1 ignored, and 59 pending.

SE-03C provider revision kernel typing is implemented and passed independent
read-only review and clean re-review. The versioned mutation and Conditional
Transition provider plans now carry `EntityRevision` directly, provider records
persist only its scalar value, and physically absent revision is a structured
admission failure rather than virtual revision zero. The clean re-review
focused provider/EntityStore/UnitOfWork run passed 59 tests in 10 suites with
5 opt-in MySQL tests canceled. The isolated staged-slice full release
validation passed 2450 tests in 350 suites, with 7 canceled, 1 ignored, and 59
pending. The earlier implementation run and `Test/compile` also passed with the
development Cozy runtime selected explicitly.

SE-03D upper concurrency API replacement is implemented. Independent read-only
review found private-helper parameter naming debt and two phase-ledger
consistency issues. Review-fix removed the naming debt without changing the
public/protected `expectedRevision` API, aligned the SE-03D compliance ledger,
and recorded the Conditional Transition token removal as SE-06 preparatory
evidence. EntityStore, UnitOfWork, protected ActionCall DSL, Conditional
Transition, admin/job callers, and observability now carry `EntityRevision`
directly. The obsolete `EntityConcurrencyToken` and
`EntityMutationExpectation` types are removed without compatibility aliases.
Review-fix validation passed 144 tests in 14 focused suites and
`Test/compile`. Clean re-review found no remaining actionable finding, passed
the same 144 focused tests, and passed the affected Static Form stale-update
specification. Full release validation passed 2452 tests in 351 suites, with
7 canceled, 1 ignored, and 59 pending.

SE-03 and SE-04 are complete. SE-04 embedded `SimpleEntity` lifecycle and
declarative OCC passed implementation, review-fix, clean re-review, and full
release validation. Independent review found and the review-fix closed
managed-field alias admission, System-admitted mutation revision maintenance,
and stale expected/actual diagnostic defects. The first release-validation
full run then exposed a missing-Entity regression in System-admitted save:
managed-save composition had accidentally changed save from create-or-replace
to update-only. The review-fix now initializes revision one through provider
create when no Entity exists and retains managed revision advancement for
replacement. Focused SE-04, related subsystem, and complete Static Form
renderer suites pass. The final full suite passed 2478 tests in 356 suites,
with 7 canceled, 1 ignored, and 59 pending.

SE-05 detached non-`SimpleEntity` implementation, review-fix, and clean
re-review are complete.
Independent review found representation-blind UnitOfWork working-set hydration,
incomplete detached persisted-revision admission, and overflow loss in
unmanaged partial upsert. Review-fix separates persisted and domain working-set
records, validates detached revision values on ordinary reads, and preserves
overflow content on partial upsert. The repaired 12-suite boundary passed 83
tests and `Test/compile`; clean re-review found no remaining actionable
finding.

SE-06 and SE-07 are complete. Conditional Transition returns the admitted
Embedded or Detached value shape, requires one expected revision under both
ordinary concurrency policies, and retains bounded simultaneous-attempt
exactly-one-winner/no-orphan behavior. Read/search/View/Aggregate projection,
Static Form metadata, idempotent REST PUT, strong `If-Match`, response ETag,
and JSON/YAML/XML projection are covered by dedicated executable
specifications. The combined Phase 50 focused run passed 336 tests, including
the complete 320-test Static Form renderer suite.

SE-08 is complete. The ordinary mutation matrix passes for Embedded and
Detached representations against in-memory and SQLite, including restart,
rollback, missing revision, exhaustion, and independent-caller concurrency.
The live MySQL acceptance profile passed all 6 tests. The local
provider/migration regression boundary passed 65 tests with 1 pending;
`simplemodeling-model` passed 56 tests, `simple-modeler` passed 42, Cozy passed
662 with 2 canceled, and the generated/Aggregate/DSL/UnitOfWork CNCF boundary
passed 25 tests.

SE-09 is complete. Canonical design and static specification describe the
verified Embedded standard, explicit Detached extension, mutation policy,
projection/transport, provider boundary, and exact executable evidence. A
current design/spec contradiction scan found no remaining separate-token,
timestamp-token, or `updatedAt`-derived OCC contract.

SE-10 is complete. The final CNCF full suite ran with a 4 GB heap and passed
2516 tests in 363 suites, with 8 canceled, 1 ignored, and 59 pending.
`Test/compile`, `git diff --check`, the live MySQL conditional-transition
profile, downstream `simplemodeling-lib`, `simplemodeling-model`,
`simple-modeler`, and Cozy validation all passed. Clean read-only re-review
found no remaining actionable implementation, naming, or executable
specification finding. Force, merge, repair, and conflict-resolution UI remain
visible as future strategy item 9.40.

## Post-Close Specification Correction

Correction identified: 2026-07-26

Status: implementation in progress

The ArtScene development-toolchain compile exposed a Phase 50 specification
bug in the application-facing Aggregate mutation boundary. Phase 50 correctly
made revision lifecycle framework-managed, but its final contract also made
the existing ordinary `aggregate_update` method require an
application-supplied revision and retained `Optimistic` as the ordinary
concurrency default.

The corrected direction is:

- retain `aggregate_update` and `aggregate_command` as separate mutation
  semantics;
- keep both ordinary methods revision-transparent;
- make `EntityConcurrencyPolicy.None` the ordinary default and
  `Optimistic` an explicit application opt-in;
- keep revision initialization and advancement framework-managed under both
  policies;
- expose observed revision only through an explicitly named strict route; and
- retain unconditional authoritative revision comparison for Conditional
  Transition.

This is a correction to the Phase 50 specification, not an ArtScene
compatibility overload. The original Phase 50 execution and closure record
above remains historical evidence and is not rewritten.

The correction is complete only after:

- CNCF Executable Specifications establish the update/command and OCC policy
  matrix;
- CNCF protected DSL and admission behavior implement the corrected contract;
- Cozy generation selects update or command from operation semantics without
  revision business parameters;
- ArtScene regenerates, compiles, and passes the selected smoke boundary
  against one aligned CNCF/Cozy toolchain;
- canonical design/specification and the Component developer guide reflect the
  verified behavior; and
- the correction ledger in `phase-50-checklist.md` records exact evidence.

Planning and investigation record:

- `docs/journal/2026/07/2026-07-26-artscene-managed-revision-api-boundary-handoff.md`

Verified implementation evidence and the remaining managed-behavior boundary
are tracked in the appended `PC-01` section of
`docs/phase/phase-50-checklist.md`.


## PC-02 Plain Mutation Fast Path

Status: implementation complete; final verification pending

The SimpleEntity plain-mutation fast path remains part of Phase 50 as the
second post-close correction stage. It does not rewrite the historical
SE-01 through SE-10 closure ledger.

PC-02 separates provider execution paths after PC-01 corrected the public
mutation contract:

- ordinary `None + AlwaysWrite` should use a direct provider update without
  target-record pre-read or mandatory authoritative readback;
- explicit `Optimistic` should use provider-native compare-and-set;
- `WriteIfChanged`, strict observed revision, and unsupported provider
  capabilities remain on explicitly safe paths;
- managed revision still advances atomically; and
- Conditional Transition remains the multi-record transactional path.

The authoritative work and closure ledger is the appended `PC-02` section of
`docs/phase/phase-50-checklist.md`. Phase 51 remains planned and does not start
until PC-02 returns Phase 50 to CLOSED.

Provider-native direct and compare-and-set execution, EntityStore/UnitOfWork
routing, cache reconciliation, in-memory/SQLite/live-MySQL parity, and stable
SQLite statement-budget evidence are implemented. Remaining work is the
downstream validation and exact Phase 50 closure record. The final CNCF
read-only review/review-fix cycle is clean, and the 4 GB full suite passes.
