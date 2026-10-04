# Durable Job Offline Backup and Restore

Status: selected-validation-passed, Phase 69.7 / JM69-09D3.
review_status=independent-complete-Step-review-pending
initial_authoring_status=authored_test_pending
Actual selected results and retained history are in the
[canonical D3 journal](../journal/2026/10/2026-10-04-phase-69.7-backup-restore.md#actual-d3-selected-validation-and-retained-execution-history).
The [contract](../spec/durable-job-backup-restore-contract.md) owns BR1..BR16.

The normal runtime adapter retains shutdown ownership. Preparation binds the
original owner, original Subsystem/node and actual routed Hikari-managed resource
while the owner is Ready and its original lease is valid. The opaque source keeps
only native path/layout facts and references to these owners. Backup observes their
completed results; it never initiates shutdown or borrows a closed lease.

Three production files separate the closed policy/layout/source/result model,
bounded filesystem inventory/copy/marker I/O, and operator entry-point integration.
Owner/provider changes bind the actual Subsystem; Subsystem exposes only a
synchronized read-only getter for its existing shutdown result.

The declared failure model covers unsupported native routing, incomplete shutdown,
malformed/current artifacts, unsafe local paths/links/types, tightened capacity
limits, stream size changes/zero reads/faults/interruption, existing destinations
and concurrent source/target attempts. A bounded preflight, exact arithmetic,
8192-byte streaming with EOF sentinel, POSIX create-new permissions, one source
guard and an owned `.incomplete` publication path implement these requirements.
Readers and directory iterators close in finally; safe operation labels hide native
details. Fault injection changes only private copy-reader behavior and cannot
replace enumeration, ownership or admission.

Preflight includes every raw database/Blob/sidecar byte and every file/directory
below the logical data root. Missing blobs produces an empty directory; companion
SQLite journals refuse because dropping them could omit authoritative state.
The adapter does not checkpoint or modify the source. Complete data is published
once within a newly private root; backup marker is created last. Failure leaves
the owned target for operator inspection, with no target auto-cleanup or overwrite
retry. If marker publication itself fails, only its newly owned failed marker is
removed so completed data cannot be consumed as a successful artifact; root/data
remain owned and incomplete.

The marker is a closed bounded five-field UTF-8 completeness format. It carries
store/container identity and counts, with no source location, keys or authority.
It is not cryptographic authentication. Native signed records and original Blob
digests are independently verified through fresh original runtime consumers.

The operator controls an exclusive offline namespace from preparation through
copy and owns real source/destination parents. No unrelated process or later
reopening is certified, and no adversarial external path-replacement guarantee is
claimed. No global/filesystem lock, hash/body ledger, scheduler, thread or retry
protocol is introduced.

Restore validates current marker, layout, types, bounds and independently trusted
store/container before reserving a fresh target. It returns `target/data` in
`RestoredAwaitingStartup`, opening no SQL pool. Fresh node/Subsystem/context/SQL and
LocalBlobStore consume this directory. Trusted signing/access evidence and port
come from the caller's original native authority. Native startup and integrity
must both accept before operational acceptance; any refusal stops the new owner.
V1 migration uses only explicit existing D2 opt-in on a fresh owner.

Executable B1..B10/R1..R10 retain native rows/provider revisions, physical bytes,
metadata, permissions, private marker publication and actual fresh health/integrity
as oracles. Seed 69709705 and 24 successful shrinking cases per suite cover
bounded physical trees and paid native terminal recovery.

D3 implementation authorship cannot accept the original Step or Phase. D2
14-suite/141-test and 92-suite/744-test plus one pending results remain separate,
including zero warnings, 7→1→0 fixture history and two DVL batches. All original
authority/history/epoch, unwaived M2/CPBs, Hygiene and remaining complete-Step,
JM69-10, ancestry, aggregate/review/release obligations stay open.
