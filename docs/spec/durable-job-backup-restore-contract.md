# Durable Job Backup and Restore Contract

Status: selected-validation-passed.
review_status=independent-complete-Step-review-pending
initial_authoring_status=authored_test_pending
Phase 69.7, JM69-09D3; JM69-09 and Phase
remain IN_PROGRESS and JM69-10 remains OPEN.

The package-private offline adapter copies the complete native SQLite database
and LocalBlobStore namespace. It preserves signed records, non-Job rows,
native provider revisions, Published lifetime payments, storage quotas, retention,
Blob metadata and payload sidecars as physical data.

## Operational procedure

An operator owns an exclusive offline namespace and its source/destination parents
for the whole procedure. Prepare the original Ready owner, successfully stop its
actual Subsystem and SystemNode using the normal runtime adapter, then back up
before any writer restarts. Restore into a new namespace. Bootstrap a separately
constructed normal runtime against the returned data directory, supplying trusted
signing/access context, startup evidence and rehydration port independently.
Inspect native integrity and stop the new runtime if readiness or integrity is
refused. Physical restore alone has state `RestoredAwaitingStartup`.

The adapter cannot certify absence of unrelated processes or later reopening of
the source. Exclusive operator-controlled parents exclude adversarial external
path replacement. There is no filesystem lock or global writer proof.

## Rules

| Rule | Required behavior |
| --- | --- |
| BR1 | Copy complete native physical database and Blob tree without logical replay, re-payment, migration, canonicalization or key export. |
| BR2 | `DurableJobBackupOperations.prepare(owner, policy = default)`, `backup(source, destination)` and `restore(artifactRoot, destination, expectedBlobStore, expectedContainer, policy = default)` return Consequence and first authorize Operator through JobExperiencePolicy using caller ExecutionContext. Ordinary and job_view callers cause no provider/path/file work. No route or public engine API is added. |
| BR3 | Only a genuine initialized original Ready owner mints an opaque source. Original engine identity, storage/startup/execution availability and healthy maintenance are required. Under its original lease/context, all Job, lifetime, storage, owned Blob quota and Blob metadata routes resolve to the same managed SqlDataStore resource with its original Hikari datasource. Its URL is native `jdbc:sqlite:` followed by an absolute normalized file path; memory, URI, query and unsupported forms refuse. An actual LocalBlobStore supplies its original location/name/container. |
| BR4 | Copy requires original owner Closed with completed Success, original Subsystem completed Success, original SystemNode Stopped with successful shutdown replay, then captured resource not open with successful close replay. Live/Closing/absent/failed/forced-drain/physical-close/rebound sources refuse before target creation. No live shutdown or closed-lease borrow occurs in backup. |
| BR5 | Successful artifact root contains exactly `backup.properties` and `data`; data contains `jobs.db` and `blobs`, including all containers and metadata sidecars. Missing Blob root becomes empty blobs; missing/nonregular database or remaining `-wal`, `-shm`, `-journal` companions refuse. No COPY_ATTRIBUTES or source modification occurs. |
| BR6 | Marker is at most 4096 UTF-8 bytes, with exactly five lines: `version=1`, `blob.store`, `blob.container`, `file.count`, `byte.count`. Duplicate/unknown/missing keys, invalid names, controls, traversal, invalid/overflow integers and invalid version refuse. Count all regular data files, including the database and zero-byte files; byte count may be zero. Data has exactly the two expected typed children. Marker establishes layout/completeness, never authenticity or unchanged-in-transit certification. No new digest/HMAC/hash/CRC/body ledger is created. |
| BR7 | Positive policies only tighten ceilings: bytes 536870912, files 16384, entries 32768, depth 16, relative UTF-8 path bytes 4096, segment bytes 255. Source preflight is operation-local and complete before reservation. Entries include files/directories but exclude data root. Limits are inclusive, additions exact with overflow refusal. Bounded visitor inventory and streams with buffers at most 8192 bytes count actual bytes and check an EOF sentinel against each declared size. Growth, shortening, zero-read and interruption refuse. |
| BR8 | Roots are absolute normalized local paths; destination parent exists and is real. No ancestor/tree symlink, special file, traversal, database inside Blob tree, or equal/ancestor/descendant overlap. No link following, missing-parent creation, existing destination replacement or source overwrite. POSIX private creation is mandatory: dirs 0700/files 0600; unsupported permissions refuse. |
| BR9 | Atomically create a fresh target root and private `.incomplete` child; copy there, publish once as `data`, then CREATE_NEW marker only for successful backup. Failure retains owned incomplete target without usable marker or success; use another fresh destination for retry. Restore independently checks current marker/layout/counts/bounds and trusted expected store/container before reservation. Restore opens no pool and never makes an engine Ready or mutates the artifact. |
| BR10 | Operational acceptance uses fresh actual node/Subsystem/context/managed SQL/LocalBlobStore and trusted closed startup evidence/port. Ordinary native startup and original integrity decide acceptance. Wrong authority, invalid signature, missing paid singleton and damaged physical payload never become healthy acceptance or automatic repair. Original V1 migration requires explicit existing D2 opt-in. |
| BR11 | One copy per opaque source at a time; guard releases in finally without holding the engine lock during I/O. Create-new destinations admit at most one concurrent winner. No new production scheduler/thread/global cache, business clock/ID/body/port/Blob put/delete/quota mutation occurs in physical operations. |
| BR12 | All expected and nonfatal backend/I/O refusals have constant typed stateInvalid labels `durable job backup refused` / `durable job restore refused`, with no path, URL, payload, credentials, subject, provider or exception text. Interruption retains its thread flag and prevents later publication. Readers/streams/iterators close in finally. Private deterministic copy-reader faults use genuine enumeration and admission. |
| BR13 | Exactly B1..B10 and R1..R10 use visible afterWord identity and adjacent actual Given/When/Then/should clauses. B10/R10 use ordinary shrinking ScalaCheck, seed 69709705, at least 24 successes each, with physical/native oracles. |
| BR14 | Fixture composes real JobDurableRuntimeFixtures managedSql, owned observed StandardEntityStore, actual LocalBlobStore, independent actual providers and contexts, genuine paid publication/association/checkpoint. Pools successfully stop before physical inspection/copy and cleanup; restored originals are read under fresh leases. |
| BR15 | Contract/design precede code. Initial D3 authorship was authored_test_pending; current selected validation passed, with independent complete-Step review pending. The [canonical D3 journal](../journal/2026/10/2026-10-04-phase-69.7-backup-restore.md#actual-d3-selected-validation-and-retained-execution-history) retains actual results and execution history. D2 receipts remain separate: representative 14/141, accumulator 92/744 plus one pending, zero warnings, fixture history 7→1→0 and two DVL batches. No original aggregate checkbox or acceptance closes. |
| BR16 | Scope is the 17 frozen owned paths only. Preserve concurrent work, all original authority/history/epoch/recovery and unwaived CPBs/C1-C4 M2/Hygiene. No public routes/config/build/dependency/schema/signature/quota redesign, hot backup, merge restore, repair, remote publication/deployment or successor work. |

## Executable examples

Each row is one executable leaf in the linked suite.

| Example | Rules | Observable clause |
| --- | --- | --- |
| B1 | BR2,BR12 | Ordinary/job_view denial before effects and safe labels. |
| B2 | BR3,BR4 | Genuine Ready preparation; live/Closing copy refusal. |
| B3 | BR1,BR4 | Actual completed shutdown, complete private native copy, unchanged paid rows/revisions. |
| B4 | BR4,BR12 | Actual failed/absent completion refuses despite terminal states. |
| B5 | BR3,BR5 | Unsupported/mismatched routes, nonlocal store, missing DB, links/companions refuse. |
| B6 | BR7,BR8 | Inclusive exact limits pass; one-over/overflow/invalid policy refuse. |
| B7 | BR8,BR9 | Existing/missing-parent/overlap/link targets refuse without alteration. |
| B8 | BR7,BR9 | Real midstream fault/interruption retains incomplete unmarked target and closes reader. |
| B9 | BR9,BR11 | Latch-controlled same-source/target races, released guard, no native mutation. |
| B10 | BR1,BR7 | Seeded shrinking physical-tree and tightened-capacity property. |
| R1 | BR1,BR10 | Paid terminal V2, multiple payloads, fresh native Ready/integrity and preserved facts. |
| R2 | BR5,BR10 | Empty/missing blobs and native zero-byte empty database, fresh Ready. |
| R3 | BR6,BR9 | Hostile/missing/oversize/count/identity markers refuse before target creation. |
| R4 | BR8,BR9 | Links/traversal/extra/special/existing/overlap refusal without alteration. |
| R5 | BR10,BR12 | Native signature/access/key/policy/missing-payment faults refuse healthy acceptance. |
| R6 | BR10,BR12 | Actual corrupt/missing payload/sidecar refuses native integrity; fresh owner stops. |
| R7 | BR1,BR10 | Genuine paid V1: default refuses; explicit D2 migration preserves payment. |
| R8 | BR7,BR9 | Restore fault/interruption retains incomplete, unconsumable, non-overwritable target. |
| R9 | BR9,BR11 | Two latch-controlled restores admit one target winner without artifact/effect changes. |
| R10 | BR1,BR10 | Seeded shrinking paid-record/payload property with fresh native integrity. |

Executable sources:
[backup](../../src/test/scala/org/goldenport/cncf/job/DurableJobBackupSpec.scala),
[restore](../../src/test/scala/org/goldenport/cncf/job/JobDurableRestoreIntegrationSpec.scala).
Design: [offline physical adapter](../design/durable-job-backup-restore.md).

Original remaining work includes all 17 correlations/security/retention/hostile
coverage, complete independent JM69-09 review/local acceptance, actual JM69-10
two-process/CBD acceptance, Phase 69.4–69.6 ancestry audit, aggregate final full
validation, sole epoch-1 Phase review and final local release.
