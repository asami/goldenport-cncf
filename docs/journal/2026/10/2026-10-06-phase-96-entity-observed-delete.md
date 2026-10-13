# Phase 96 P96-07B1 observed-delete provider authoring

Date: 2026-10-06.
Role: implementation history and development fixture/sample provenance.
This journal is not a normative specification or acceptance record.
Manifest: phase96-p96-07b1-implementation-manifest-001 revision 1.
Branch: codex/phase-96-display-service.
Original Phase base: 6cbe877c53c116639feb11fa80546e79c051804c.
Current validation status: CF2 representative003 **PASS** (10/10);
accumulator002 **PASS** (27 suites, 215/215, 1 existing baseline pending).
All 30 new cases and required live MySQL executed positively; SBT/wrapper exit
0 and terminal shared-lock release. Earlier failures remain historical below.
Whole-Step independent acceptance and P96-07 closure remain pending.

## Frozen implementation boundary

P96-07B1 adds typed strict observed deletion to the existing versioned
datastore provider port. The implementation separates physical root removal
with optional bounded Delete-only effects from logical lifecycle patching
without effects. Both policies return the prior authoritative matched
revision; logical storage advances once. The shared support admits shape,
new capabilities and result correspondence, and DataStoreSpace retains the
existing provider/domain dispatch boundary.

Memory uses the existing provider monitor, snapshots, checkpoints and one
collection publication. SQL uses the existing native transaction, selected
root, affected-row update/delete, same-connection side effects and commit/
rollback. SQL normalized read identities are used for the new operation's
existing exclusion admission. No generic unconditional root-delete fallback,
shadow datastore, caller lock or post-delete result reconstruction was added.

The authoring scope is the thirteen manifest paths only. The seven existing
P96-07A bridge files and `.cozy/launcher.yaml` are preserved. No build,
configuration, generator, dependency, shared directive, checklist, workflow
record, main checkout, Cozy/library or application file is part of this pass.

## Development fixture and sample provenance

The new observed-delete fixture is datastore development data, not a
registered production Entity or canonical lifecycle implementation.
Collections use `EntityCollectionId("test", "observed", scenario suffix)`.
Root, side and unrelated sample identities are `root-1`, `side-1` and
`unrelated-1`. Property scenarios additionally create bounded `effect-N` or
generated target identities under those fixture collections.

The illustrative root contains title, unselected business data, `overflow_ref`,
`aliveness`, `deleted_at`, `updated_at` and detached-style `cncf_revision`.
The logical patch uses fixed fixture lifecycle values with a timestamp of
`2026-10-06T00:00:00Z`. These field/value choices document setup and are not
promoted to a universal Entity lifecycle schema. The caller must supply its
admitted server-owned lifecycle patch in future canonical integration.

Memory fixtures extend the actual InMemoryDataStore and route operations
through DataStoreSpace with a real ExecutionContext. Recording and legacy
providers are used only for capability/domain/result admission. Native
checkpoint subclasses inject root, side-effect and prepublication failure;
the paused memory checkpoint holds the actual monitor while ordinary CRUD/
search callers contend for it.

SQLite uses a temporary database beneath `target/entity-observed-delete/work`,
independent SQLiteDataSource/store instances, configured `IMMEDIATE`
transactions and bounded busy timeout. Reopen assertions use actual JDBC
reads. A real `BEFORE DELETE` trigger using `RAISE(IGNORE)` produces a zero
affected-row root mutation for same-transaction diagnosis. Cleanup closes
stores and removes only the fixture's database/sidecar/temp files.

Live MySQL uses Testcontainers `mysql:8.4`, a fixture database and independent
JDBC stores. Ordinary cases use the provider's owning JDBC pool factory;
injected checkpoint/commit cases use the actual SQL provider over its fixture
datasource. The suite closes stores and stops its container. Concurrent
scenarios arrange overlapping calls with bounded waits but retain native
provider/database synchronization. No SQLite/H2/mock substitution supplies
MySQL evidence.

## Authored specification map

The [settled contract](../../../spec/entity-observed-delete.md) and
[design](../../../design/entity-observed-delete.md) link all required suites.
The provider suite authors OD-E1..E7, including exact matched revision,
logical preservation, actual intervening CAS, revision/shape/capability/result/
domain failures, all checkpoint stages, races and ordinary monitor sharing.
The SQLite suite authors OD-S1..S6 with independent visibility, failure
preservation, native rollback, independent callers, normalized managed-column
protection, executed guarded SQL and actual zero-row diagnosis.

The three generated laws author current-match policy/effect semantics,
stale complete-state preservation and exact selected-target scope. Each
requires at least 100 successful cases and zero discards. The live MySQL
suite authors OD-M1..M6, including independent visibility, logical prior/next
revision, the admission matrix, root/side/precommit/commit rollback and one
native concurrent winner.

## Representative validation and CF1 fixture repair

The initial implementation worker's 2026-10-06 authoring pass ran no SBT,
test, validation script, server or network operation and performed no
self-review, staging, commit, push or publication. That no-execution claim
records the initial authoring history; it is not the current validation status.

The parent's representative001 execution of
`testOnly org.goldenport.cncf.datastore.EntityObservedDeleteProviderSpec`
completed normal generation, production compilation and Test compilation
successfully, then ran 13 tests: 11 passed and 2 failed. SBT exit was 1,
wrapper exit was 1 and the shared lock was released. Original evidence:

- Log: `/private/tmp/skill.cncf.d/a-0e6d8c78-e2aa-4267-9421-89e0274b6783/20016-20261005T222119Z.log`.
- Summary: `/private/tmp/skill.cncf.d/a-0e6d8c78-e2aa-4267-9421-89e0274b6783/20016-20261005T222119Z.summary.json`.

CF1 follows frozen Fix Manifest
`phase96-p96-07b1-fixture-fix-manifest-001` revision 1 and addresses:

- `P96-07B1-CF1-LIFECYCLE-FIXTURE`: `Record.++` concatenated duplicate
  lifecycle fields, while `getAny` selected the first original alive/empty
  value. The Provider, SQLite and MySQL dead/timestamp fixtures now use
  `upsertSingle` with the same intended values. Given assertions inspect the
  actual stored snapshot for the selected value and exactly one matching key,
  before the deletion action. NotFound and complete-state preservation
  expectations remain unchanged.
- `P96-07B1-CF1-IDENTITY-CONSTRUCTION`: `CollectionId.Instance(null)` failed
  eagerly during UniversalId label validation, before any plan action. The
  shared matrix now uses the representable `UniversalEntryId(null)` case.
  The Provider malformed-plan scenario separately invokes the impossible
  collection constructor through `Consequence` at When and requires a Failure
  at Then, retaining constructor-boundary evidence and the routed/direct
  plan failures, unchanged snapshots and zero publication checkpoints.

Historical CF1 authoring status: this bounded repair pass performed no
validation or self-review. At that point CF1 revalidation on the fresh source
remained pending; no new passing validation, independent review, provider
readiness or Step acceptance was claimed. The actual subsequent CF1 results
are recorded below.

## Historical CF1 pending parent verification

The frozen parent route selects EntityObservedDeleteProviderSpec first, then
one 27-suite accumulator retaining the four new suites, the P96-07A update
bridge suites and existing Display/UoW/Entity/native/versioned/conditional/
SQL lifecycle/parity regressions. Required live MySQL uses
`CNCF_LIVE_MYSQL_TEST=true`; the parent resolves this in the test execution
session, not by changing project configuration. The existing live conditional
transition suite is retained. Canceled/ignored required MySQL scenarios or
Docker unavailability are not passing evidence. Parent static admission,
registered serialized execution, terminal lock-release evidence and
independent acceptance remain pending.

## Actual CF1 validation and CF2 capability expectation repair

The parent's representative002 execution of
`testOnly org.goldenport.cncf.datastore.EntityObservedDeleteProviderSpec`
passed all 13 tests, with zero failed, canceled, ignored or pending tests.
Receipt: `phase96-p96-07b1-representative-result-002` revision 1.
Summary: `/private/tmp/skill.cncf.d/a-53340c20-0b1e-4436-9f39-dbf52238c720/31112-20261005T224018Z.summary.json`.
SBT exit was 0, wrapper exit was 0 and the shared lock was released.

The actual 27-suite accumulator001 completed 27 suites with zero aborted:
215 tests ran, 214 passed, 1 failed and 1 baseline test remained pending;
zero tests were canceled or ignored. Receipt:
`phase96-p96-07b1-accumulator-result-001` revision 1.

- Log: `/private/tmp/skill.cncf.d/a-e8a31839-921f-4419-8c0c-2e283f192796/33612-20261005T224414Z.log`.
- Summary: `/private/tmp/skill.cncf.d/a-e8a31839-921f-4419-8c0c-2e283f192796/33612-20261005T224414Z.summary.json`.

SBT exit was 1, wrapper exit was 1 and the shared lock was released. All 30
new observed-delete cases passed: 13 Provider, 8 SQLite, 3 generated laws and
6 live MySQL cases. The three laws executed their authored minimum of 100
successful cases with zero discards. Actual Testcontainers `mysql:8.4` and
JDBC executed all six observed-delete cases, plus the existing conditional
transition and UoW parity live MySQL cases. These are actual CF1 results,
not evidence for the subsequently edited CF2 source.

The sole failure was the exact installed-provider capability expectation in
`EntityMutationProviderContractSpec`: the actual native memory provider
advertised the admitted `OptimisticCompareAndDelete` and
`AuthoritativeDeleteResult`, but the expected set omitted both. The unchanged
`guardedBaseline` does not acquire these features. The baseline pending
`SqliteDataStoreSpec` scenario, `store record fields as columns`, was already
pending in accepted HEAD before B1. It remains a disclosed diagnostic and is
not counted as passing proof or repaired here.

CF2 follows `phase96-p96-07b1-capability-fix-manifest-001` revision 1 for
`P96-07B1-CF2-CAPABILITY-EXPECTATION`. Its prescribed correction appends the
two literal features to the existing expected set alongside
`DirectAlwaysWrite` and `OptimisticCompareAndSet`. Strict
`supported shouldBe Consequence.success(...)` equality and the explicit noop
empty-contract equality remain. The scenario retains Given real memory/noop
setup, When both capability queries and Then both consequence expectations;
all other scenarios and the two existing generated laws remain unchanged.
The source header preserves Jul. 26 history and author attribution with one
current Oct. 6 version. This repair is authored but not validated; the CF2
worker executes no validation or self-review. No provider readiness, B1
readiness, independent acceptance or Step/Phase closure is claimed.

## Historical pending parent verification after CF2

At this historical point, the parent route first selected
`EntityMutationProviderContractSpec` (representative003), then, only after
that passes, runs the same 27-suite accumulator (accumulator002) with
`CNCF_LIVE_MYSQL_TEST=true`. The required actual live MySQL cases and existing
regressions remain required; the baseline pending diagnostic remains
disclosed. Parent diff and executable-specification verification, registered
serialized execution and terminal lock-release evidence remain pending.
Whole-Step independent acceptance, the epoch-1 full Phase review after all
Step commits, and final validation/release gates retain their original
obligations.

## Actual CF2 validation supplied by the parent

The actual representative003 EntityMutationProviderContractSpec execution
passed all 10 tests. The subsequent unchanged 27-suite accumulator002 passed
215 of 215 tests with zero failed or aborted suites. One existing baseline
SqliteDataStoreSpec test remained pending; it is disclosed, not passing proof.
All 30 new observed-delete cases passed, including the generated laws and
positive live Testcontainers mysql:8.4/JDBC cases. Required existing live MySQL
regressions also executed. These results supersede the CF2 pending authoring
status above without erasing representative001 or accumulator001 failures.

The parent supplied the actual result summary to
phase96-p96-07b2a-implementation-manifest-001 revision 1: both SBT and wrapper
exits were 0 and the shared lock was released. This journal projection does
not independently reconstruct or execute validation and adds no invented log
path. B1 readiness is a prerequisite receipt, not independent whole-Step or
Phase acceptance. New canonical B2A source still requires its own current
parent-selected validation and review.

## Remaining Step obligations

All four original P96-07 criteria remain OPEN. B1 does not prove canonical
Entity/Aggregate delegation, update-first Detail/Edit/Save/refreshed Detail,
positive create-to-Detail/List or delete-to-List, or committed-outcome
preservation when projection is delayed or fails.

B2 retains canonical typed observed Entity/UoW deletion and positive create/
delete lifecycle, including authorization, policy, identities, overflow,
reconciliation and invalidation. C retains registered ordinary Entity/
Aggregate routes, canonical committed outcomes and actual selected contextual
reload through `ActionEngine.afterCommitResponseC` without replay. Neither a
pure receipt check nor an unsupported-only result supplies those proofs.

B2A authors that canonical delete/read coherence in the
[canonical journal](2026-10-06-phase-96-entity-observed-delete-canonical.md).
Mandatory B2B creation root/overflow/commit failure assessment is not waived
by the create happy path. C also retains explicit ordinary-route authorization
and preservation of acknowledged committed outcome when projection is delayed
or fails, without replay. No outer UnitOfWork abort rollback guarantee is added.

P96-08 through P96-11 and the later Step/Phase validation, review and release
obligations remain open as recorded by the parent; CF2 closes none of them.

Necessary same-file maintenance is limited to current source version headers
with historical entries preserved and conformance of the new declarations,
structured errors and executable-specification boundaries. The four existing
oversized source files remain baseline follow-up debt, not authorization for
refactoring. No new accepted Hygiene finding or empty Development Candidate
journal is created. Step/Phase reviews, transitions and commits belong to the
parent.
