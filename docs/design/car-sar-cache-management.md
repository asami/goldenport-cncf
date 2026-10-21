# CAR/SAR Cache Lifecycle

Status: accepted target design; CC-01 and CC-02 accepted and committed; CC-03 implementation underway.

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

## CC-01: concrete runtime integration contract

This section records the integration boundary frozen at CC-01. That contract
acceptance did not establish implemented behavior. CC-02 has since delivered
and accepted the CAR route; SAR/SNAPSHOT, recovery and capacity retain their
separate CC-03/CC-04 validation and acceptance obligations.

### Existing ownership and authoritative checks

| Existing entry | Managed-cache connection |
| --- | --- |
| `ComponentRepository.StandardRepository.Specification(kind, baseUrl, cacheRoot)` | Its explicitly selected `cacheRoot` remains authoritative for standard repository bodies and their managed cache. |
| `ComponentCreate.configuration` | Resolve policy through the existing `ResolvedConfiguration`; for nonstandard/caller archives use the configured root or existing `defaultStandardRepositoryDir`. |
| `ComponentRepository._discover_component_from_car_c` | Acquire a stable source and completed CAR extraction, then call existing `_discover_component_from_car_common` with the leased `CarExtracted`. |
| `ComponentRepository._discover_component_from_sar_c` and `_discover_component_from_sar_extracted` | Acquire the SAR tree and thread that parent ownership through packed/directory child discovery. |
| `CarExtractor` / `SarExtractor` | Factor their current unzip and structure-resolution code into destination-supplied extraction; preserve ZIP path safety, structure checks and existing `CarRuntimeAdmission` at its established boundary. |
| `SystemNode.ownRuntimeResourceC(AutoCloseable)` | Register successful backing leases with the same runtime that owns the component; release a lease immediately if discovery or registration fails. |
| `Subsystem.shutdownOwned` | Keep the existing `subsystem.shutdownC()` then `systemNode.shutdownC()` ordering; dependent ClassLoaders close before backing leases. |

The real owner lives under `subsystem/SystemNode.scala`, not a new `system/`
package. Its existing registration API is sufficient; no second shutdown hook,
resource-owner registry or resolver is introduced. `workAreaSpaceC` and the
retained WorkArea extractor APIs retain their ordinary temporary-work meaning.

Static descriptor/default lookup and assembly-API JAR discovery are also source
readers. Their managed-body reads must use the same source protection before
source eviction is enabled; a short metadata read does not justify an unleased
managed path. A cache hit continues through the current identity, mode-policy,
canonical archive, resource and assembly admission boundaries. Cache code neither
reimplements those checks nor manufactures authority from a filesystem hit.

### Types and responsibilities to implement

The package is `org.goldenport.cncf.component.cache`. Public means package-facing
runtime API here, not a new externally owned Component or repository resolver.

| Type/API | Contract |
| --- | --- |
| `ArchiveKind` | Closed `Car` / `Sar` kind. |
| `SourceGeneration` | Kind plus SHA-256 of captured source bytes; cache identity only. |
| `ExtractionGeneration` | Source generation plus closed profile `car-v1` / `sar-v1`; changing extraction meaning requires a new profile. |
| `SourceLease` | `AutoCloseable` over one independently immutable, cache-owned source body; contains generation and source path. |
| `ExtractionLease[A]` | `AutoCloseable` over a published typed `CarExtracted` / `SarExtracted`; exposes its path only while held. |
| `ManagedArchiveCache.acquireSourceC` | Capture/resolve protected source bytes through the selected repository context; return `Consequence[SourceLease]`. |
| `ManagedArchiveCache.acquireCarC` / `acquireSarC` | Reuse destination-supplied extractor logic and return the typed extraction lease. |
| `ManagedCachePolicy.fromC` | Parse existing resolved configuration and optional explicit repository root; reject invalid/conflicting policy. |
| `CacheInspection` / `CacheCollectionResult` | Typed original/extraction/staging totals, reserved bytes, candidate/reclaimed/in-use/failed entries and bounded per-entry reasons. |
| `CacheFailure` | Closed reasons including `UnsupportedFilesystem`, `CapacityExceeded`, `ContentionExceeded`, `SourceChanged`, `PublicationFailed`, `DeletionFailed`, `PolicyConflict`, with operation/kind/generation/required/available details as applicable. Project into existing `Consequence` diagnostics. |

Acquisition and inspection do not return a bare reusable path. Failed acquisition
closes its partial leases in reverse order. Close is idempotent. Ordinary
reusable generations remain after close; retired-generation release attempts
bounded collection only after native shared protection has been released.

### Source placement and publication metadata

Keep existing `car/` and `sar/` coordinate paths and repository lookup. Add:

```text
sources/car/<digest>/archive.car
sources/sar/<digest>/archive.sar
extracted/car/<digest>/car-v1/          complete entry: tree/ and entry.json
extracted/sar/<digest>/sar-v1/          complete entry: tree/ and entry.json
metadata/coordinates/<coordinate-key>/current.json
metadata/generations/<generation-key>/references/<coordinate-key>
metadata/generations/<generation-key>/retirement.json
metadata/policy.json
quota/<pool>/<slot>/                   bounded persistent reservation claims
staging/YYYY/MM/<uuid>/                unpublished payload and staging metadata
locks/coordinates/<coordinate-key>.lock
locks/generations/<generation-key>.lock
locks/staging/<uuid>.lock
locks/quota/<pool>/<slot>.lock
```

The digest is a content identifier, never an approval or authenticity token.
Generation keys include kind and, for extraction, profile. A coordinate key is
derived from the existing resolved repository/coordinate identity; an explicitly
supplied external source uses its normalized source URI as its alias identity.
These internal keys do not change CAR/SAR public coordinates.

`entry.json` has schema `cncf.managed-cache-entry.v1`, kind, source digest,
profile, actual byte count and completion/last-use metadata. It and `tree/` are
built in one uniquely owned staging directory, flushed, and atomically moved
as a complete entry. A directory without valid completion metadata is not a hit.
The source generation is independently published before extraction reads it.

`current.json` has schema `cncf.managed-cache-selection.v1`, a unique revision,
source generation and extraction profile. The pointer is authoritative for a
managed coordinate. Existing coordinate archive files remain repository inputs,
not mutable backing files handed to long-lived consumers. Capture them into
independent immutable source files before use. Account for both a retained
coordinate body and its independent captured copy; never hide duplicate bytes
through a hard link to a caller-writable file. Caller archives are inputs only.

Repository downloads still use the existing fetcher. It writes protected staging
and replaces a coordinate body atomically through the coordinate update protocol;
it does not overwrite a leased generation. Managed source readers participate
in that protocol; external replacement is captured through an open handle and
validated before publication. A detected changing/invalid capture is retried
within the bounded acquisition policy or returns `SourceChanged`; it is never
treated as a trusted prior immutable generation.

### Short transactions and complete reference checks

Maintain one JVM registry per normalized stable lock filename, sharing its open
channel and native lock among local holders. Source/parent acquisition precedes
child acquisition. Never upgrade overlapping native locks. Registry entries with
active locks are not replaced, and ordinary collection never deletes lock files.

Selection performs: coordinate update lock, nonblocking shared generation pin,
then release the coordinate lock before returning the lease. Miss preparation,
hashing, download, extraction and waiting run outside the coordinate lock.
Hold prepared source/tree shared pins across the final switch. Under the update
lock compare the actual pointer revision observed before preparation; a different
revision requires reselection/retry, even if content has changed away and back.
Identical current bytes/profile reuse the current generation without retirement.

Reference registration follows coordinate-before-generation ordering: pin each
prepared generation, atomically publish its coordinate reference, then atomically
replace `current.json`. Keep the old reference as a conservative retirement
candidate until reconciled. A crash before pointer replacement leaves only an
unselected candidate; a crash after replacement leaves enough old/new reference
information to derive pending retirement. Recovery checks the pointer under its
coordinate lock and never interprets a reference file alone as a live selection.

A collector first reads a candidate's reference set without a generation lock,
then tries its coordinate locks in deterministic key order, followed by exclusive
generation protection. Reread the reference set and pointers under those locks.
If a previously unknown coordinate appears, release all locks and retry within
the bound. A switch cannot add a new reference while the collector holds native
generation exclusion because reference publication requires a shared pin first.
For retirement, delete only if the now-complete protected pointer check finds no
current reference and the payload's own use is zero. Never wait for or acquire a
coordinate lock while holding generation exclusion. Different generations and
unrelated coordinates require no common lock or whole-cache transaction.

Current reusable entries may be evicted by policy only after exclusive
protection and pointer revalidation; retain their source for offline rebuild.
For a retired source/tree, check its own references and usage independently,
without an idle grace period. Mark/unpublish an entry before partial deletion;
failure remains a non-hit pending candidate. A generation still current for
another coordinate survives retirement of the first.

### Policy keys, concrete defaults and peak space

These keys resolve through `ComponentCreate.configuration`; an explicit
`StandardRepository.cacheRoot` takes precedence for that repository. Persist
the capacity policy for a cache root and reject incompatible concurrent policy
definitions instead of silently sharing reservations with different bounds.

| Key | Default |
| --- | --- |
| `cncf.component.cache.root` | Existing `defaultStandardRepositoryDir()` |
| `cncf.component.cache.extracted.max-bytes` | 8 GiB (8,589,934,592 bytes) |
| `cncf.component.cache.source.max-bytes` | 16 GiB (17,179,869,184 bytes), including managed coordinate bodies |
| `cncf.component.cache.staging.max-bytes` | 2 GiB (2,147,483,648 bytes), a subset limit on in-progress bytes |
| `cncf.component.cache.source-entry.max-bytes` | 512 MiB (536,870,912 bytes) |
| `cncf.component.cache.extracted-entry.max-bytes` | 2 GiB (2,147,483,648 bytes) |
| `cncf.component.cache.extracted.idle-millis` | 86,400,000 (24 hours) |
| `cncf.component.cache.source.evict-reusable` | `false`; current originals remain available offline |
| `cncf.component.cache.collection.max-entries` | 128 per explicit invocation; automatic checkpoint at most 32 |
| `cncf.component.cache.collection.max-bytes` | 268,435,456 (256 MiB) per invocation, unless a single already-selected entry is larger; report the actual overshoot |
| `cncf.component.cache.acquisition.max-attempts` | 64, then structured contention failure; no unbounded spin |
| `cncf.component.cache.acquisition.max-wait-millis` | 120,000 total; bounded per-entry waits happen outside coordinate locks |
| `cncf.component.cache.reservation.quantum-bytes` | 1,048,576 (1 MiB); a smaller positive test policy is allowed for its isolated root |

Budgets and single-entry limits are positive integral byte counts; idle duration
may be zero. Reject overflow, negative values and internally incompatible limits.
On lock contention, wait on the entry's ordinary local/native coordination
outside the coordinate critical section, then reselect; do not consume retries
in a tight loop. The attempt and elapsed-wait bounds both apply and produce
`ContentionExceeded`, without weakening another holder's lease.
Reservations round up to a quantum; reported physical payload bytes remain exact.
Each source/extracted pool includes completed payloads plus allocations under
construction. The staging limit additionally bounds their in-progress subset,
so staging does not create an unaccounted extra allowance. Diagnostics separately
show actual bytes and conservative rounded reservations. Filesystem metadata and
other applications' disk use are not claimed to be bounded by these payload pools.

Use bounded per-pool slot claims, acquired atomically and independently, to reserve
capacity before writing each additional chunk. There is no root-wide lock.
Claims survive process death and identify their protected entry/staging owner;
only recovery holding that owner's exclusive protection may release abandoned
claims. An incomplete claim stays protected while its claimant lives and is
reconciled conservatively after exclusive acquisition. Publication transfers
reservation ownership to the completed payload before releasing staging claims;
it never temporarily drops the payload charge. Partial deletion keeps its charge
until bytes are actually removed. A failure to reserve attempts bounded safe
collection, then returns `CapacityExceeded`; it neither forces a holder out nor
falls back to WorkArea. Retained originals can exhaust the source pool even if
the extraction pool is empty; report that condition rather than silently evicting.

The logical diagnostic operations are `inspect`, `collect(dryRun = true)` and
`collect(dryRun = false)` on the same cache owner. Projection through existing
CNCF operational/diagnostic conventions belongs to CC-04. Dry-run obtains the
same protection and rechecks but changes no payload, pointer or reservation.
Acquisition checkpoints run bounded collection for pressure/idle/retirement;
there is no AI polling task. Stable locks, journal/evidence, caller directories
and old unleased flat temporary trees are always outside collection selection.

### Supported filesystem boundary

The initial target is a same-host local filesystem providing native cross-JVM
shared/exclusive file locks and same-filesystem atomic rename. Verify actual
shared lock grants (`FileLock.isShared`) and atomic publication at use; a provider
that returns an exclusive usage lock or cannot atomically publish returns
`UnsupportedFilesystem`. Do not silently serialize normal holders or copy a
partly visible tree as a fallback. Cross-JVM fixtures qualify the actual local
provider; they do not certify all network/distributed filesystems. Process death
must release native usage protection without a callback. Caller-owned originals,
noncooperating writers/deleters and manual `rm -rf` remain outside advisory-lock
protection. Unsupported or unverified providers receive an explicit diagnostic.
