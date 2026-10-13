# Workflow multi-guard transaction (Phase 88 S12)

Date: 2026-09-30
Status: pure input, internal same-owner memory, default-deny policy, Subsystem host construction and internal admitted counting-provider route focused tests validated (through focused-299); no public provider atomicity/readback or S12 acceptance

`DataStoreConditionalTransitionPlan` currently guards one Workflow instance
root and one Continuation successor. Its remaining effects are Create-only.
An EventStore-wide sequence cursor is a third independently revised record;
neither an unguarded side-effect Save nor a subsequent `EventStore.append` can
make its allocation atomic with the two Workflow records and event Creates.

S12 introduces a separate opt-in canonical capability for a bounded set of
additional revision/field-guarded updates in the same physical transaction.
The existing two-record method remains unchanged. Each new guard has an exact
target, expected and next revision, checked fields and typed changes. The
provider rejects target overlap with any root, successor, guard or Create,
unsupported provider domains and malformed input before publication. In-memory
publication uses one staged state; SQL uses one connection and one commit.
Stale guards, Create collisions and precommit faults roll back every record;
post-acknowledgement missing/divergent readback is indeterminate, not retry
authority. No arbitrary closure, AI action or external command executes in
that transaction.

`DataStoreSpace` currently checks root/successor component-owner equality and
one provider identity across their collections and side effects. S12 must
extend provider-domain admission to every additional guard. A supplied owner
label alone must not authorize a Workflow-to-EventStore cross-component write:
the trusted host must bind and admit permitted owners and exact collections
before exposing this capability. This authority contract is unresolved in
the current API and must be implemented with the primitive, not deferred to a
Skill or caller convention.

## Required public route before another provider implementation

The S12 caller route is **Workflow → `DataStoreSpace` → one physical
`DataStore` provider**. Workflow may prepare a pure plan, but must not obtain
or call a multi-guard-capable memory/SQL provider directly. The existing
`WorkflowPreparedDataStoreTransition` two-record path remains unchanged;
S12 needs a separate adapter that holds `DataStoreSpace`, not a borrowed raw
provider. A skill, AI action, plan field or `DataStoreComponentOwner` label
cannot mint permission.

`DataStoreSpace` must expose one S12 operation with these admission gates,
all before invoking the provider:

1. Revalidate the pure plan, including unique record targets, bounded guards
   and Create-only effects. The plan's current package-limited scope is an
   assertion to compare with host authority, not authority itself.
2. Require a host-installed, default-deny binding for the exact Workflow
   owner/root and successor collections, each additional owner/guard
   collection, and every permitted side-Create collection. Binding creation
   belongs to trusted runtime/component assembly from canonical component
   descriptors, not to the operation caller or the plan. It is immutable and
   bound to one call-scoped `DataStoreSpace` view inside the managed
   `Subsystem`/`SystemNode` lease; no public runtime mutator or
   caller-provided copy can install it. No wildcard owner or collection
   permission is inferred. Preserve the existing no-grant `DataStoreSpace`
   construction path for compatibility; add a host-only factory path that
   injects the immutable binding and one borrowed provider for that call.
   The ordinary global space is mutable and its thread-local binding may
   change, so a construction-time lookup there is not continuing authority.
3. Resolve root, successor, every additional guard and every side-Create
   through one call-local binding snapshot of this `DataStoreSpace` and
   require the **same provider object** for all collections. `isAccept`
   alone is not proof of one physical transaction; split providers fail
   closed before invocation.
4. Require an explicit opt-in multi-guard provider capability. The provider
   receives only the admitted closed plan and host binding; it cannot select
   another store, run callbacks or promote a side Save/Delete.

The provider checks all revisions and fields and either publishes all guarded
updates and Creates in one physical commit or none. A stale root may retain
the existing `NotMatched` meaning; a stale additional guard is a structured
conflict. Create collisions, precommit faults and provider errors must leave
all rows unchanged or report an indeterminate outcome, never a partial
success. A commit acknowledgement is followed by authoritative loads of the
root, successor, every additional guard and every Create from that same
provider. Missing or divergent readback yields transaction-indeterminate
before Workflow projects success; it does not authorize automatic replay.

The executable specification must call the S12 **Workflow adapter and
`DataStoreSpace` operation**, not a concrete SQLite method. It must cover
default-deny and mismatched host bindings, wrong owner/collection or side
collection, split provider domain, unsupported capability, successful
three-guard plus multiple-Create commit in memory and SQLite, stale third
guard, Create collision, injected precommit failure, independent SQLite
writers, and missing/divergent post-commit readback for every record class.
The first route/admission tests may use a counting fake provider, but the
atomicity/readback cases require real memory and SQLite. No check of P2-U15
is allowed until both route and provider suites pass against the same tree.

The focused-287 same-owner memory method is an internal staging experiment,
not this public route or a cross-owner grant. An unverified direct SQLite
method was deliberately withdrawn before testing when the route requirement
was clarified. No SQL implementation is accepted by this note.

The first public `DataStoreSpace.conditionalMultiGuard` entry now revalidates
the plan and rejects it with `host-admission-required` before provider lookup.
Its executable spec checks that rejection for a valid cross-owner plan and a
missing plan without a provider lookup. This is a fail-closed route scaffold,
not an admitted transaction: the call-scoped host grant, positive route,
atomic providers and readback remain to be implemented and validated.

The exact host-policy comparison is now a separate candidate value: it checks
Workflow owner/root/successor, each additional guard owner/collection, and
each side-Create collection. Its construction does not create an invocation
grant, and `DataStoreSpace` still denies even when the comparison succeeds.
The current `EventStore` is a `Subsystem` service without a canonical
datastore declaration, so trusted runtime assembly must first define where
its cross-owner identity and collection permissions come from. Inventing an
owner label in the plan or accepting a caller-created policy would bypass the
required authority boundary.

An authority-source audit found the current `Subsystem` holds a fixed
`EventStore.inMemory`, and `ComponentFactoryWorkflowPart` shares it with
components. No canonical persisted EventStore cursor/event/index collection
declaration exists. Before a positive cross-owner admission, choose either
an opt-in Subsystem-owned EventStore persistence declaration (preserving the
in-memory default) or a real EventStore component with canonical entity-space
ownership and a migration plan. The former is the smaller compatible route;
neither model is implemented by the pure host-policy candidate.

Focused-288 started SBT but stopped before specs because the Cozy development
runtime classpath was stale. Cozy compile/classpath refresh-289 passed, then
CNCF focused-290 passed 20/20 tests with the serial SBT lock released. These
receipts validate the fail-closed comparison candidate only; they do not
close the positive route, atomicity or readback requirements above.

The Subsystem-owned EventStore authority model has now been selected. Its
typed construction declaration and managed-lease host view passed focused-293
alongside selected-event policy and regression specs (45/45). The view admits
one managed physical resource but still rejects the unsupported multi-guard
capability. These are host-construction/rejection examples, not positive
provider transaction acceptance. The declaration means storage permissions,
not persistence of all events: Workflow first evaluates the separate
[selected-event policy](workflow-event-persistence-selection.md).

The global cursor, delivery-key index and active UnitOfWork integration are
follow-on producer slices. They must use the S12 primitive for cursor CAS,
contiguous overflow-checked sequence allocation, event and internal index
Creates, with authoritative readback. Delivery keys do not enter the S9
`event_json`. A stale precommit plan can be freshly prepared; an uncertain
commit cannot be blindly replayed. S12 alone closes neither P2-U05 nor
production EventStore acceptance.

Executable acceptance requires memory and SQLite three-guard success, stale
third-guard/duplicate-Create/fault rollback, independent-writer exclusivity,
target/authority/provider rejection, indeterminate readback of any committed
record, and S3-S11 regression on one accepted source tree. The consumer's
P2-U15 tracks this open prerequisite; this note records the CNCF boundary.

## Internal admitted provider route (2026-09-30)

WorkflowPreparedMultiGuardTransition retains a DataStoreSpace, not a raw
provider. DataStoreSpace revalidates the plan, checks host policy and live
issuing-thread lease before lookup, and requires every collection to resolve
to the same admitted provider object. It alone constructs an opaque invocation;
the internal EntityConditionalMultiGuardDataStore seam rechecks provider
identity, policy and lease after lookup, immediately before provider dispatch.
A throwing route is classified transaction-indeterminate, with no automatic
replay or successful Workflow completion projection.

DataStoreMultiGuardRouteSpec supplies an opt-in counting fixture returning
NotMatched without writes. Its ten examples cover one admitted invocation,
unadmitted/null input, owner/collection/guard mismatch before lookup, split or
rebound providers, unsupported providers, released/escaped leases, release
during lookup, cross-thread use and provider exception without replay. No
production memory or SQLite provider implements the seam. This is internal
route admission evidence only, not cross-owner commit, rollback, readback,
selected-event dispatch or public provider capability acceptance.

Attempt 296 stopped before compilation on stale Cozy classpath; prerequisite
297 completed compile/classpath export. Attempt 298 compiled production code
but failed three test line-break expressions; its receipt was invalidated by
repository-tree drift and is not acceptance evidence. After correcting the
expressions, settled-tree attempt 299 passed 60/60 tests in seven suites,
with accepted receipt, exits 0/0 and lock released. S12, P2-U15, P2-U16 and
P2-U05 remain open. Full-suite validation was not performed.

Focused-301 then passed 65/65 in eight suites, including five test-only real
same-owner memory staging examples through the admitted route. Three guards
and two Creates update together; stale third guards, second Create collisions
and four pre-publication failures preserve every record. Cross-owner remains
rejected. Diagnostic fixture reads are not admitted Workflow readback or
delivery authority. Before public opt-in, the closed result/readback contract
must cover every additional guard and Create, not merely the existing two
Transitioned records. See the
[memory route journal](../journal/2026/09/2026-09-30-phase-88-s12-memory-route.md).

## Native memory acknowledgement and complete readback (2026-09-30)

The private verified route and EventStore-owned selection passed focused-309
(107/107 in 14 suites). Memory post-images were then moved from fixture-side
preload reconstruction into the provider's actual staged state. The internal
same-owner acknowledgement captures root, successor, additional guards and
Creates before publication and exposes Committed only after state replacement.
Existing two-record callers retain their result; no production opt-in is added.

Focused-310 passed 144/144 in 18 suites, including nine complete readback
examples and existing conditional transition regressions. New cases retain
prepared post-images after later storage mutation and reject all four
pre-publication checkpoint failures without acknowledgement or writes.
Exit 0/0 and lock=released were observed. These are focused tests, not typed
Phase/PLAN receipts or production delivery acceptance. Cross-owner memory,
SQL native transaction/snapshot, host bootstrap and selected cursor/index/event
commit/dispatch remain open; S12/P2-U15/P2-U16/P2-U05 are not complete.

## SQLite native candidate and logical storage representation (2026-09-30)

Focused-316 passed 164/164 tests in 20 suites. The internal same-owner SQLite
candidate uses one write transaction for guards, Creates and schema expansion,
retains precommit full post-images, and reads all targets in one native snapshot.
Eleven test-only route cases cover rollback (including added columns), independent
writers, reopen, uncertainty and rejected authority. No production opt-in is added.

Physical SQL id is checked against the target, then removed from the logical
Record; business writes to that reserved column reject before staging. Logical
field order alone is ignored during pure update/Create validation. Duplicate
fields are rejected, not collapsed, and values and complete ordered targets
remain exact. SQL acknowledgement and readback use the same sorted logical
representation, preserving exact full-vector equality. Existing row/schema APIs
are not globally changed. Snapshot-phase competing-writer evidence passed
focused-317 (165/165 tests in 20 suites): native SQLITE_BUSY during paused
readback, a successful explicit later write, and retained post-image revision.
Managed host/cross-owner activation and selected cursor/index/event dispatch
remain open.

## Managed host authority boundary (2026-09-30)

Focused-318 passed 169/169 tests in 20 suites. Real Subsystem-managed SQLite
resolution now has verified-route denial/lifecycle examples for exact declaration
permissions, issuing thread and lease lifetime, caller binding preservation,
provider replacement, and exception cleanup followed by a fresh lease.
The host-issued view still reaches an unsupported verified capability; this
is not a successful cross-owner transaction or production activation. The next
implementation boundary must preserve that authority in a managed verified
provider, without widening legacy or same-owner SqlDataStore entry points.

## Explicit managed verified provider (2026-09-30)

Focused-319 passed 173/173 tests in 20 suites. A separate internal Subsystem
verified route checks shared managed resource identity and borrows a non-owning
SQLite view. Its opaque invocation is validated through native staging and
precommit, then complete snapshot readback. Cross-owner Workflow records and
EventStore cursor with event/index fixture Creates commit and recover exactly;
stale cursor and second Create collision roll back. Ordinary SQL/raw host views
and same-owner entry points are unchanged. This supersedes the previously open
managed connection step, not the remaining concurrency/revocation or canonical
selected-event sequence/record/dispatch runtime integration gates.
