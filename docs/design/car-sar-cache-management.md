# CAR/SAR Cache Lifecycle

Status: accepted design; implementation planned.

The [specification](../spec/car-sar-cache-management.md) owns observable behavior.
This design separates repository selection, cache storage and runtime lifetime.

## Ownership and integration

ComponentRepository keeps artifact selection and existing admission boundaries.
A managed archive cache provides a protected immutable source generation and a
managed extraction cache provides a typed lease over its completed tree.
CarExtractor/SarExtractor supply existing extraction/structure validation logic.
They no longer allocate a fresh retained WorkArea for every reusable archive.
WorkArea retains ordinary mutable execution and unrelated temporary-file ownership.

Integrate leases with the existing SystemNode/runtime resource owner. Preserve
the current shutdown ordering: dependent subsystem/ClassLoaders close before
their backing resources. Cache release follows that ordering: ordinary reusable
entries remain cached, while release of a superseded SNAPSHOT schedules a bounded
collection attempt after usage protection is released. No second shutdown
hook or separate runtime owner registry is needed.

The existing ComponentResourceLifecycleStore models resource lifecycle/owners;
it is not a cross-process cache lock. Reuse its semantic boundary where useful,
without presenting an in-memory owner set as persistent filesystem protection.

## Acquire, publish, use, release

1. Resolve the artifact normally and acquire its stable source generation.
2. Derive the extraction key. Acquire shared entry protection and inspect the
   completed publication. On a hit, retain protection and return a lease.
3. On a miss, release shared protection and acquire exclusive build protection;
   recheck because another builder may already have completed the entry.
4. Otherwise extract into owned dated staging and atomically publish the complete
   tree and its generation metadata as one entry. Failed staging is never exposed.
5. Transition to shared usage by releasing exclusive and reacquiring shared
   protection, then recheck existence/generation before exposing the path. A
   collector can win that gap; retry resolution, never return a deleted path.
6. Release the source lease when extraction no longer depends on its backing
   storage. Retain all SAR parent or child leases needed by the returned resources.
7. Runtime close shuts down consumers and releases leases idempotently. The entry
   remains reusable until the collector can obtain exclusive protection.

Lock acquisition order follows source/parent before extracted child. The collector
locks one candidate at a time and never waits for a parent while holding a child.
Avoid lock upgrades and nested waits that deadlock a builder and collector.

## SNAPSHOT replacement and retirement

Represent a SNAPSHOT coordinate's current selection separately from immutable
generation storage. Keep the current pointer and retirement metadata consistent
at the switch boundary; recovery must be able to derive pending retirement from
committed selection state if interrupted. A failed switch preserves the old
selection and leaves only an unselected prepared candidate for normal recovery.

Use a stable coordinate update lock for short selection/switch transactions and
stable generation locks for actual source/tree usage. The acquisition transaction
selects and pins the generation before releasing the update lock. Prepare new
bytes/expansion outside it; recheck the observed selection before switching so a
concurrent refresh cannot commit stale work blindly. On contention, release and
retry; do not hold a coordinate lock while downloading, extracting or waiting for
a generation build. Readers already holding old leases keep using the old tree.

At switch, mark the old selection retired and attempt nonblocking collection.
After each retired payload's last release, attempt collection again. Failed or
interrupted collection remains pending for the next bounded managed operation;
there is no idle grace period or permanent SNAPSHOT history. Retain current
originals for offline reconstruction; obsolete source/tree payloads have separate
use counts and may disappear independently. A generation still current elsewhere
is not eligible. Reference tracking and collection must be synchronized with all
coordinate switches that can select that generation; CC-01 freezes the internal
protocol, including deterministic ordering if multiple coordinate locks are needed.

The collector checks current references under the same coordination, takes
exclusive generation protection nonblockingly and rechecks before deletion.
Never acquire coordinate locks while holding an exclusive generation lock.
The JVM registry applies to both lock roles; lock files are outside deleted
payloads and are retained. Reusing identical content reuses its generation and
does not create an extra retained version or duplicate expansion.

## JVM and filesystem coordination

Use one process-owned lock channel per stable cache entry with a JVM-local
reference count and synchronized acquisition/release. Do not take overlapping
FileLocks for every runtime in the same JVM. Cross-JVM shared usage and exclusive
collection are coordinated through the stable lock file, not through PIDs.
Keep the lock registry outside payload directories; ordinary eviction does not
delete registry files. Empty lock-file reclamation is outside the first delivery.

The initial supported scope is local filesystems with required locking and atomic
publication behavior. Detect unsupported locking behavior and return a clear
unsupported result rather than silently losing protection or holding an exclusive
usage lock that prevents ordinary concurrent runtimes. Network filesystems and
distributed-host cache sharing are outside Phase 116.

This follows Java's [FileLock contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html):
locks are JVM-wide, overlap is restricted, shared support is platform-dependent,
and native locks should be treated as advisory. The channel registry must account
for platform behavior where closing another channel can release process locks.

## Collection and interruption

Last-use/size metadata selects candidates cheaply; exclusive lock acquisition and
state recheck decide whether deletion is safe. Last-use writes may be coalesced;
stale timestamps may affect priority but must not affect usage protection.
After partial deletion, mark/unpublish the entry while exclusive protection is
held so the next reader rebuilds instead of accepting damaged contents. Preserve
retryable failure details. Journal/evidence are not cache metadata or GC targets.

Process death releases native locks. The next managed operation can reclaim
abandoned staging or finish failed collection after acquiring its exclusive
protection. SIGKILL cannot run compensating code; correctness must not depend on
a shutdown hook. Recovery should inspect owned candidate state, not hash every
tree or scan every process on each access.

Original archives and expansions have independent budgets and leases. Start with
unused expansion reclamation and current/reusable original retention; retire
unused superseded SNAPSHOT originals under C2.1. Include staged bytes in
capacity decisions and return a structured capacity failure if all reclaimable
space is exhausted. Expose disk usage and skipped-in-use counts through existing
operation/diagnostic conventions, with no new AI approval loop.

## Delivery boundaries

The first connected route includes a CAR hit/miss, two concurrent consumers,
shutdown ordering and actual safe collection. SAR/nested backing lifetime and
policy-driven recovery follow. Internal source-generation storage, typed results,
operation/configuration names and bounded default limits are fixed in CC-01
against existing APIs before code is added; they are not new public coordinate
formats. No automatic migration of old temporary trees is implied.

Phase 112 remains owner of Documentation Subcomponent/ScalaDoc projection.
This cache phase neither requires its completion nor silently excludes ScalaDoc
from an archive. Reuse immutable generations first; optional selective extraction
requires its own declared extraction profile and resource-contract alignment.
