# SimpleEntity Plain Mutation Fast Path Implementation

date = 2026-07-26
status = pc-02d-implementation-complete-review-pending
phase = 50
stage = PC-02

## Purpose

This note records the implementation sequence for the provider-native
SimpleEntity plain mutation path. Normative behavior remains in:

- `docs/spec/simpleentity-storage-shape-policy.md`; and
- `docs/design/simpleentity-storage-shape-policy.md`.

## Current Call Sequences

Before PC-02 provider execution work, managed save/update/update-by-id reaches
the provider through these shared EntityStore steps:

1. UnitOfWork may load the current record when target authorization requires
   object state.
2. EntityStore resolves the collection and entry id.
3. EntityStore `_raw_record` loads the target.
4. EntityStore derives the expected revision when optimistic policy requires
   it, merges the mutation, preserves overflow content state, and constructs an
   `EntityVersionedMutationPlan`.
5. DataStoreSpace admits the root and side effects to one provider.

The provider sequence then depends on the requested semantics:

| Mutation semantics | In-memory provider sequence | SQL provider sequence |
| --- | --- | --- |
| `None + AlwaysWrite` | synchronized map lookup, revision read, next-revision calculation, root replacement, side effects, publish authoritative root | transaction, lock/select root, revision read, next-revision calculation, root update, side effects, select authoritative root |
| explicit `Optimistic` | the ordinary sequence plus expected/actual revision comparison before publication | the ordinary SQL sequence plus expected/actual revision comparison while the root is locked |
| `WriteIfChanged` | build desired record and compare normalized business state before revision advancement | build desired record from the locked root and compare normalized business state before revision advancement |
| strict `ObservedRequired` | compare the supplied observed revision before write-policy and ordinary optimistic handling | compare the supplied observed revision against the locked root before write-policy and ordinary optimistic handling |
| side-effect-bearing mutation | stage root and all side effects from snapshots, then publish the complete collection set once | update root and apply side effects in the same provider transaction before authoritative readback |

MySQL schema preparation may perform a separate preflight record read before
the transactional sequence when dynamic column preparation is required.

Conditional Transition does not use the plain mutation sequence:

1. UnitOfWork/ActionCall supplies the already authorized current root and
   explicit transition expectation.
2. EntityStore prepares the root delta, successor, and side effects without an
   additional `_raw_record` load.
3. The provider loads/locks the root, evaluates revision and field guards,
   prepares the successor, updates the root, applies side effects, and publishes
   atomically.
4. SQL reads the authoritative root and successor for the transition result;
   in-memory publishes and returns the staged records.

Recording-provider and SQLite SQL-trace specifications now keep these
source-derived sequences as stable executable call/statement counts.

An authorization or transition-validation load is policy work. It is not the
provider concurrency check and remains permitted when the operation requires
current object state.

PC-02 removes mandatory EntityStore/provider reads performed merely to
maintain managed attributes. It does not bypass authorization, transition
validation, content overflow safety, `WriteIfChanged`, strict observed
revision, or Conditional Transition.

## Capability Contract

Provider features are explicit:

- guarded versioned mutation;
- direct `AlwaysWrite`;
- optimistic compare-and-set;
- business-state comparison;
- atomic side effects; and
- authoritative record result.

Readback is an independent requirement. A caller that does not request
authoritative readback may use direct acknowledgment. A caller that requires
an authoritative record may use a direct provider only when that provider
declares the result feature; otherwise the planner selects a safe guarded
fallback when available.

Provider success also has an explicit result contract:

- `Applied(Omitted)` and `NoOp(Omitted)` are acknowledgments with no
  authoritative record;
- `Applied(Authoritative(record))` and
  `NoOp(Authoritative(record))` carry provider-authoritative readback; and
- `Stale(expectedRevision, actualRevision)` represents compare-and-set
  rejection as a provider result.

An omitted result is invalid when the caller required authoritative readback.

The in-memory and SQL implementations now expose the guarded baseline plus
`DirectAlwaysWrite` and `OptimisticCompareAndSet`. The native operations are
separate typed provider calls:

- `mutateEntityDirect(EntityDirectMutationPlan)`; and
- `compareAndSetEntity(EntityCompareAndSetMutationPlan)`.

Both operations advance revision inside the provider mutation. They return
acknowledgment without readback by default and perform authoritative readback
only when requested. Providers that do not declare the corresponding feature
remain on the guarded fallback and cannot receive these native plans through
`DataStoreSpace`. Dispatch admits the complete capability set, including
`AuthoritativeRecordResult` when requested, before invoking the provider.
Unsupported readback therefore cannot fail after a mutation has already been
published.

## Execution Path Selection

The first PC-02 implementation slice fixes these path rules:

| Request | Required provider path |
| --- | --- |
| `None + AlwaysWrite`, direct feature available | direct ordinary mutation |
| `None + AlwaysWrite`, direct feature absent | guarded versioned fallback |
| explicit `Optimistic`, native feature available | optimistic compare-and-set |
| explicit `Optimistic`, native feature absent | guarded versioned fallback |
| `WriteIfChanged` | guarded business-state comparison |
| mutation with atomic side effects | atomic side-effect mutation |

Missing strong capabilities fail structurally. The planner never silently
weakens optimistic, comparison, side-effect, or readback semantics. A pure
optimistic request prefers native compare-and-set and otherwise uses the
guarded provider contract. When comparison or side effects are also requested,
the guarded path requires the union of their provider features and preserves
optimistic handling inside the provider transaction.

When `Optimistic + Managed` does not carry an observed revision, EntityStore
loads the authoritative target once, extracts the CNCF-managed revision, and
passes it to provider-native compare-and-set. An observed optimistic request
does not perform that managed-revision load.

## Provider-Native Statement Contract

PC-02B fixes the following SQLite target-record statement sequences:

| Provider operation | Successful target statements | Failure diagnostics |
| --- | --- | --- |
| direct acknowledgment | one revision-advancing `UPDATE`; no target `SELECT` | zero-row application performs one diagnostic `SELECT` |
| direct authoritative result | one revision-advancing `UPDATE`; one target `SELECT` | zero-row application performs one diagnostic `SELECT` |
| compare-and-set acknowledgment | one revision-qualified `UPDATE`; no target `SELECT` | stale/zero-row application performs one diagnostic `SELECT` |
| guarded mutation | target `SELECT`, `UPDATE`, target `SELECT` | existing structured guarded behavior |

The SQL mutation admits only a persisted positive integral revision below
`Long.MaxValue`. A zero-row direct or matching compare-and-set mutation reads
only for deterministic not-found, stale, invalid-revision, or
revision-exhaustion classification. Successful acknowledgment does not read
the target.

Native schema admission requires the managed revision column to exist before
mutation. It may add ordinary change columns according to the existing SQL
provider policy, but it never creates or repairs the managed revision column.
Normalized aliases that resolve to the managed revision column are rejected
before SQL execution. Column-name normalization preserves the reserved
detached managed field `cncf_revision` in provider records so Entity revision
binding, authoritative readback, and zero-row diagnostics share one storage
contract.

SQLite requires integer storage class in the mutation predicate. MySQL uses a
decimal-digit representation guard plus the same positive range bounds, so a
fractional, negative, null, malformed, exhausted, or missing revision cannot
be advanced by the native path.

The protected `sql_statement` observation hook records prepared SQL text for
Executable Specifications without introducing a proxy datasource or changing
provider semantics.

## EntityStore and UnitOfWork Integration

PC-02C routes provider-native paths from EntityStore and UnitOfWork:

- ordinary `None + AlwaysWrite` patch uses direct provider mutation;
- explicit optimistic and observed-revision patch uses provider-native
  compare-and-set;
- `WriteIfChanged`, content-bearing mutation, and providers without matching
  native capability retain the guarded path;
- authorization loads current object state only for `UserPermission`;
- transition validation loads current object state only when a non-noop
  transition hook is installed; and
- ServiceInternal/System paths do not pre-read solely for authorization.

Record- and snapshot-returning routes request one provider-authoritative
readback. UnitOfWork evicts an existing resident value, installs that
authoritative record directly, and invalidates View state only after provider
success. It does not reload the datastore after successful mutation.

The internal unversioned patch route returns only acknowledgment. It requests
`EntityMutationReadbackRequirement.None`, so a capable SQL provider can
complete it with one revision-advancing `UPDATE` and no successful target
`SELECT`.

Native mutation carries typed equality and presence exclusion guards.
In-memory and SQL providers enforce the authoritative logical-deletion rule by
rejecting a root whose `deletedAt` value is present, including an inconsistent
root whose `aliveness` still says alive. The additional `aliveness=dead`
equality guard remains defensive. A stale compare-and-set evicts resident
Entity state, while unsuccessful mutations do not invalidate View state.

Conditional Transition remains on its existing guarded atomic provider
contract and is not routed through the plain-mutation fast path.

## Verification State

The PC-02C focused validation passed 93 tests across seven EntityStore,
UnitOfWork, native-provider, SQLite statement-trace, authorization, and
Conditional Transition suites, followed by `Test/compile` with a 4 GB maximum
heap.

PC-02D adds UnitOfWork-level provider parity for in-memory, file-backed SQLite,
and opt-in live MySQL. The same scenario proves:

- direct ordinary mutation advances revision from one to two and reconciles
  resident state;
- explicit observed/provider-native compare-and-set advances revision from two
  to three and reconciles resident state;
- a stale observed revision is rejected and evicts resident state; and
- committed datastore state remains the successful value at revision three.

The accumulated live-provider focused validation passed the provider-parity,
SQLite statement-trace, MySQL native acceptance, revision parity,
Entity/Working Set coherence, and UnitOfWork mutation suites with a 4 GB
maximum heap. The direct parity spec passed all three providers with no
canceled live test.

The representative SQLite workload performs 64 mutations per path. Stable
statement evidence is:

- direct acknowledgment: 64 target `UPDATE` statements and no target
  `SELECT`, for 64 total target statements; and
- guarded mutation: 64 target `UPDATE` statements and 128 target `SELECT`
  statements, for 192 total target statements.

Elapsed time, latency per mutation, and throughput are printed as informational
samples only. They are not asserted and do not establish a wall-clock
performance guarantee. Review and final Phase 50 full/downstream validation
remain separate stages.

Accumulated review found two routing/coherence defects and one Executable
Specification organization debt. Review-fix now routes managed optimistic
updates through one authoritative revision load plus native compare-and-set,
annotates provider mutation-target not-found failures structurally so
UnitOfWork evicts stale resident state without View invalidation, and groups
the large provider/SQLite/UnitOfWork specifications by semantic behavior.

The subsequent clean re-review exposed a time-of-check/time-of-use gap:
authorization or transition validation could resolve one current state, while
EntityStore reloaded a newer state before managed compare-and-set. The fix
introduces an internal three-state managed mutation base: unresolved, resolved
present, and resolved missing. UnitOfWork now passes the exact validation base
to EntityStore. A resolved record supplies its revision, a resolved missing
target fails without reloading, and only an unresolved base permits the single
managed optimistic load.
