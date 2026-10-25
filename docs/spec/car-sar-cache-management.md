# CAR/SAR Cache Management

Status: accepted specification; CC-01 through CC-07 accepted and committed; Phase 116 CLOSED on 2026-10-23.

Revision 2026-10-22: capacity reservations are withdrawn. The revised C2/C5
contract was accepted in CC-04 commit `a8676706c1062a5e09da6c87ae4371747cd8bba8`
after native validation and independent review. Final repository-wide validation,
integrated review and native Phase completion are now accepted; see
[Phase 116 acceptance](../phase/phase-116.md#final-phase-acceptance--2026-10-23).
Earlier commits and validation evidence retain their actual scope.

## Scope and authority

CAR/SAR source archives and reusable extracted contents are managed caches.
Ordinary mutable execution files remain WorkArea resources. This specification
defines cache behavior, not a new repository resolver or admission authority.
The existing [Component/Subcomponent contract](component-subcomponent-architecture.md)
continues to own identity, resource authorization, mode policy and resolution.

## C1 Storage and ownership

The default cache root is `~/.cncf/cache`; an explicitly configured repository
cache root must be respected through the existing configuration boundary.
Do not hardcode a user's home directory or a `dev????` checkout path.

```text
~/.cncf/cache/
  car/                    original CAR repository cache (existing layout)
  sar/                    original SAR repository cache (existing layout)
  extracted/car/<key>/     completed immutable CAR expansion
  extracted/sar/<key>/     completed immutable SAR expansion
  staging/YYYY/MM/<id>/    incomplete download or extraction
  locks/                  stable per-entry coordination files
```

Keep existing archive coordinate paths and repository lookup semantics.
Internal generation metadata belongs to the managed cache; consumers receive
resolved resources and must not scan or construct paths under these directories.
Mutable source ingestion must supply a stable byte generation before expansion;
its internal storage mapping is finalized in CC-01 without changing public
repository coordinates. An active generation must never be overwritten in place.

`~/.cncf/local`, caller-supplied archives/directories, Git checkouts, journals
and evidence are not eviction targets. An external archive may be ingested as
a cache-owned generation, but its original file remains caller-owned.
Ordinary WorkArea files stay under `java.io.tmpdir/cncf/YYYY/MM/workarea-<id>/`.
Date folders aid inspection of unfinished work; age alone grants no deletion right.

## C2 Generation identity and publication

An extraction key identifies artifact kind, exact source-byte generation and
extraction format/profile version. Coordinates or filenames alone are insufficient:
two local SNAPSHOT archives at the same coordinate can contain different bytes.
Use a content digest computed during controlled source ingestion, reusing trusted
ingestion metadata for immutable sources. This digest identifies cache content;
it is not a workflow approval hash, an authenticity assertion or a requirement
to rehash every expanded directory on each access.

For a protected, cache-managed immutable generation, reuse its established
identity and completed payload without recopying or rehashing it. External or
changed mutable inputs still pass through controlled ingestion; a pathname or
timestamp alone must not establish content identity. A no-change acquisition
does not rewrite identical reference/retirement records or force them to disk.
Recovery uses actual publication state; do not compute a separate full-content
hash whose only consumer checks that a field exists.

Build in uniquely owned staging on the destination filesystem. Only a complete,
successfully extracted generation may be atomically published as reusable.
Partial/failed output is never a hit. Coordinate replacement and completed-entry
publication must be protected against readers; an unsupported atomic publication
mechanism yields an explicit failure rather than exposing a partial tree.
Concurrent requests for one generation converge on one published result; different
entries remain independent. Existing archive path-safety checks still apply.

### C2.1 SNAPSHOT current-generation switch

A managed SNAPSHOT coordinate selects one current immutable generation. Prepare
the replacement in staging, publish its complete generation, then atomically
switch the current selection; never overwrite an active generation's bytes.
Before the switch, acquisition selects the old generation; after the switch,
new acquisition selects the replacement. Existing leases continue to use their
original generation without a forced reload. Failed preparation/publication leaves
the previous current selection intact. Identical bytes and extraction profile
reuse the same generation rather than creating another history entry.

Superseded SNAPSHOT generations are not retained as version history. Attempt
collection at replacement; keep any backing payload still in use, then attempt
collection after its final lease release. If interrupted or deletion fails,
retain a retryable pending-collection record for the next managed collection.
Do not wait for the ordinary idle-retention period. Source archives and extracted
trees are independently protected and reclaimed when their own use ends. An
object still selected by another coordinate is not obsolete and must survive.
The original-retention default in C5 applies to current/reusable originals, not
to otherwise unused superseded SNAPSHOT originals. Caller-owned originals remain
outside collection under C1.

## C3 Usage protection

Returning an extracted path requires an acquired typed lease. The lease must
cover lazy class loading and resource access, not merely discovery or startup.
Runtime shutdown closes dependent ClassLoaders/resources before releasing leases.
The last release makes an entry eligible for collection. Current/reusable entries
follow the cache policy; superseded SNAPSHOT entries trigger collection under
C2.1 rather than historical retention. Exceptional startup releases acquired resources.

Protection applies across runtimes in one JVM and across JVM processes on the
same host. Usage holds shared protection; construction/publication and eviction
use entry-scoped exclusive coordination. An in-memory reference count alone is
not sufficient. Acquisition and eviction recheck state while protected: no path
may be exposed in an acquire/delete race. Missing entries are rebuilt under the
same protocol. There is no machine-wide lock or process-scan prerequisite.

Two lock roles are distinct:

| Lock | Scope and duration | Responsibility |
| --- | --- | --- |
| Update lock | One SNAPSHOT coordinate, short critical section | Serialize current-generation switch and selection-to-lease acquisition |
| Usage lock | One immutable source or extraction generation, actual use lifetime | Shared use; exclusive construction/publication or collection protects that payload |

Acquire the coordinate update lock to select the current generation and acquire
its usage protection before releasing the update lock. Never return an unleased
selected path. Download, extraction and long waits occur outside the update lock;
if generation protection cannot be acquired promptly, release and retry selection.
Publication rechecks current state under the update lock so competing refreshes
cannot blindly overwrite a newer selection with stale prepared work.
Collectors coordinate current-reference checks with switches, then obtain
exclusive generation protection nonblockingly and recheck eligibility before
deletion. Maintain coordinate-before-generation ordering; release paths must not
wait for an update lock while holding an exclusive generation lock.

Coordination files live outside evicted payloads and retain stable identities;
ordinary collection must not unlink/recreate an in-use lock file. OS release on
process termination enables later recovery. PID existence, timestamps and stale
owner metadata are not substitutes for acquiring the appropriate lock.
File locks are advisory: protection applies to cooperating CNCF operations and
does not promise to prevent manual `rm -rf`.

## C4 CAR/SAR composition

A source archive is protected while read, fingerprinted or expanded. Its lease
may be released after an independently backed extraction is published. Each live
runtime retains leases for every backing tree it still uses. A CAR used directly
inside an expanded SAR therefore retains that SAR generation. A packed CAR inside
a SAR can acquire its own expansion lease; keep the parent lease during reading.
Do not repeatedly extract the same packed CAR for each consumer. External
development directories remain caller-owned and are never collected as cache.

## C5 Collection and capacity

Provide a CNCF-managed inspection/collection operation with a dry-run projection.
Report source versus extracted bytes, last use, candidate/reclaimed/skipped entries
and failure reasons. Collection uses configured soft byte targets and last-use/idle
policy, preferring unused extracted results before removing reusable originals.
Keep current/reusable originals for offline re-extraction by default; ordinary
source eviction is a separate explicit policy. Superseded SNAPSHOT originals
instead follow C2.1 and are reclaimed after their own use ends. Report retained
source capacity separately rather than claiming
that an extraction budget bounds the entire cache.

Before removing a candidate, acquire exclusive protection without interrupting
holders and recheck eligibility. Skip entries in use; do not terminate users or
force-release leases. Deletion failure remains visible and retryable. Incomplete
staging is reclaimable only after its owner protection is acquired; neither its
date folder nor a missing PID is sufficient. Never sweep an entire month blindly.

Capacity targets guide reclamation; they are not strict shared quotas, disk-space
reservations or write-admission gates. Do not create reservation files, slot
claims, reservation handles, capacity locks or a persistent policy-equality gate.
Normal stream reads/writes and cache hits do not scan reservations, total cache
size or expanded trees. Changing a collection target must not make an existing
cache unusable merely because its old policy differs.

Use ordinary file I/O. Disk-full and other write/publication failures return
structured I/O failures preserving the cause; cleanup failure must not hide the
primary failure. Clean owned incomplete outputs where possible, otherwise retain
recoverable staging. Preserve existing current/active generations and publish
only complete results. Preflight free-space checks are not required and cannot
replace this behavior. No fallback to another unbounded temporary area is added.
Existing authoritative archive-safety checks remain at their established boundary;
do not invent cache-specific per-entry quotas as a replacement reservation system.

Explicit collection and targeted retirement/recovery perform bounded exploration
as well as bounded reclamation. An automatic checkpoint must not enumerate/sort
the entire cache before taking its first N candidates. A cache hit uses only
its target's necessary metadata and protection; it does not trigger global
inventory or recovery. Defer remaining exploration with explicit truncation.
Report scoped/measured totals as such, never as exact whole-cache totals. Prefer
stored completion size for selection; any necessary physical recount belongs to
the selected diagnostic/recovery operation, not every acquisition. No AI polling
or new quota ledger is required to manage collection.

## C6 Existing guarantees and adoption

A cache hit does not grant CAR runtime admission or resource authorization.
Reuse their existing authoritative checks at their established boundaries; do
not add duplicate permission gates, repeated full-content verification or a
parallel resolver. Cache ownership cannot widen a resolved resource's authority.

Old flat temporary extractions are not automatically adopted or deleted: older
runtimes do not participate in cache leases. Their cleanup remains a separate,
explicitly scoped operation preserving active/uncertain work. Existing WorkArea
cleanup and evidence remain valid for their original scope.

The main acceptance is two runtimes sharing one CAR expansion, continued lazy
access after one closes, rejection of collection while the other holds it, and
successful collection after its final release. Exercise the same behavior across
two JVMs, then SAR composition, interrupted staging and injected write failures.
Use local fixtures; Docker and external services are not required.

Design: [cache lifecycle](../design/car-sar-cache-management.md).
Delivery: [Phase 116](../phase/phase-116.md) and its checklist.

## CC-01 contract selection

The design's [concrete runtime integration contract](../design/car-sar-cache-management.md#cc-01-concrete-runtime-integration-contract)
fixes the existing API attachment points, typed source/extraction leases, internal
immutable source mapping, current/reference/retirement publication protocol,
nonblocking lock order, inspection/collection results, capacity keys/defaults
and supported filesystem outcomes. It preserves C1–C6 and the public repository
coordinate layout. The 2026-10-22 revision supersedes its former strict capacity
reservation/peak-space contract. Report retained coordinate bodies and staged
bytes within the diagnostic scope; do not promise a filesystem-wide hard bound.
Default reusable originals remain retained.

This contract was accepted for CC-02 onward through CC-01's ordinary compile,
independent review and Step commit. That acceptance establishes no implemented
shared cache, cross-JVM protection or Phase completion.
