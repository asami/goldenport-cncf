# Phase 96 PostgreSQL native persistence authoring

Date: 2026-10-14. Scope: PHASE-96 / P96-07 / P96-07PG-B.
Document role: authoring history and handoff, not specification or acceptance.
The [native design](../../../design/postgresql-phase-96-persistence.md) records
settled implementation decisions, and the
[persistence specification](../../../spec/postgresql-phase-96-persistence.md)
owns the intended behavior.

## Preserved prior work

The existing 17-path atomic-create draft remains preserved and unvalidated.
The seven PostgreSQL prerequisite documents establish documentary requirements
and the primary Knowledge Store decision; their earlier static conformance
is not executable native evidence. Existing creation-failure preservation laws,
prior history, original checklist states and unrelated changes remain intact.

## Initial native authoring

The initial authoring pass adds the production PostgreSQL driver dependency,
native dialect, ordinary direct/managed ComponentDataStore selection, real
catalog/quoted address and field correspondence, native primary-constraint
arbitration, revisions, transactions and acknowledgement cleanup. Existing
memory, SQLite and MySQL contracts remain required. No runtime, test, build,
publication or deployment command was executed by this authoring worker.

Parent coordination during this pass identified a naming-gate discrepancy in
the newly introduced package-private dialect helpers. Their private names and
parameters were corrected in the initial authoring scope, with public required
dialect overrides retained. Parent coordination also made explicit that native
side Save must use the same catalog-admitted primary constraint as address
admission; an additional unique index with different id equality must fail
rather than redirect an update. The corresponding helper and actual database
constraint scenario were authored within the owned paths. Neither coordination
item is a validation or independent review result.

## Authored executable obligations

The new native fixture owns a pinned private PostgreSQL container, observes
actual durability settings, opens independent writers/readers, and delegates
fault proxies to real JDBC connections. Snapshot and hydration observations
read actual storage. Canonical baseline preparation uses existing fixture APIs
and actual server complement policy before scenario snapshots; native SQL null
fields are retained. Deletion fixtures preserve their existing presence-based
physical/logical policy. Property cases share bounded pools and use unique real
targets, nonempty bodies and meaningful bounded shrinking.

The authored suites cover normal CRUD/query/paging and carrier correspondence;
quoted case, native clipping, schemas, primary keys and collations; canonical
create body modes and the retained CFS failure families; actual Entity/UoW
updates and observed physical/logical deletion; conditional Create/Bind,
mismatch and independent races; normal Component/SystemNode selection and
shutdown; native positive/duplicate/no-orphan properties; and actual
PostgreSQL/SQLite/MySQL acknowledgement and rollback-failure behavior.

Commit-loss samples deliberately call the real connection commit before
returning indeterminate, throwing or returning null. They model acknowledgement
loss, not a proved real network outage. Refused rollback samples inspect actual
rollback/abort/auto-commit calls and independent storage, without substituting
a datastore or claiming that unknown acknowledgement means unchanged rows.

## Status and remaining owners

All new source and specifications are authored and unvalidated. There is no
passing receipt, independent native observation, Step acceptance, commit or
Phase closure in this entry. The parent owns complete intended-diff preflight,
the frozen representative and expanded affected commands, their actual
results, independent observation, acceptance and transitions.

All four original P96-07 criteria remain OPEN. Registered Entity AND Aggregate,
ordinary Display mutation/auth/revision routes and real selected contextual
`afterCommitResponseC` reload remain original Slice C obligations. P96-08
through P96-11, independent Step acceptance, the single full Phase review,
final validation/release and the aggregate handoff remain required.

Pre-existing protected-hook and neighboring naming debt, as well as the large
shared SQL provider's source-size debt, retain separate follow-up ownership.
This authoring scope performs no unrelated split, cleanup or upstream change.

## Bounded parent preflight repair

The parent admitted `phase96-p96-07-postgresql-preflight-fix-001` for two
demonstrated source-contract violations. `P96-PG-B-PREFLIGHT-NULL-001` traced
native SQL NULL readback through a full-root guarded patch into
`Presentable.print(null)` and `null.toString`. The bounded correction preserves
raw NULL only for PostgreSQL. The existing PG-E5 field-mapping scenario now
authors clear-at-revision-two followed by a real changed guarded patch, with
independent physical and authoritative readback, revision three and the
retained `Field.Value.Single(null)` audit field.

`P96-PG-B-PREFLIGHT-CLEANUP-001` identified first-error short-circuiting in
fixture store closure and Component/SystemNode teardown. The fixture now
attempts every registered store close before clearing its registry, rethrows
the first nonfatal close error and suppresses later errors. The existing
parameter-precedence scenario protects direct construction and close within
node cleanup, and both-node teardown attempts the second actual shutdown even
if the first fails.

These corrections and regression expectations are authored and unvalidated.
The worker ran no tests, builds or self-review. Parent validation, independent
native observation, all original criteria and later acceptance gates remain
pending; this entry establishes no PASS, acceptance, commit or closure.

## Bounded conditional API compile repair

The actual `phase96-p96-07-postgresql-representative-001` attempt failed during
Compile with four diagnostics in `_pg_effective_conditional`: the expected-field
API exposes `storageField`, and the private Root and Plan constructors also
make their case-class copy methods private. SBT and the wrapper both exited
one with the serial lock released; no test execution was reached. The parent
retains that failed result and its consumed execution permission.

The parent admitted `phase96-p96-07-postgresql-conditional-api-fix-001` for
`P96-PG-B-COMPILE-CONDITIONAL-API-001`. Native field mapping now uses
`storageField` while preserving the expected value. The effective root and
plan are reconstructed through their existing validated `create` factories,
retaining component owner, collection, entry identity, expected and next
revisions, all mapped predicates and changes, side effects and correlation.
Factory validation replaces the redundant standalone plan validation.

Five same-type private Plan copy calls in the native conditional specification
were mechanically replaced with the same validated Plan factory. The existing
authoritative NotMatched scenario retains its absent prospective successor and
side targets. The same-root race retains both distinct successors and body
candidates; the different-roots/shared-successor race retains both root/body
identities and candidates. Their Given/When/Then boundaries, race calls and
authoritative outcome and complete preservation expectations remain intact.

These API consumption corrections are authored and unvalidated. The repair
worker ran no tests, builds or self-review. The parent owns fresh representative
and expanded affected validation on the corrected subject, independent native
observation, original Step and Phase acceptance and transitions. The previous
NULL and cleanup repairs and all earlier history remain preserved; this entry
establishes no PASS, acceptance, commit or closure.

## Bounded actual-runtime causal repair

The actual parent-owned representative attempt
`phase96-p96-07-postgresql-representative-003` reached 343 test cases and
reported 86 failed scenarios. The retained causal diagnosis is
`phase96-p96-07-postgresql-runtime-diagnosis-001`; its raw local execution
evidence is at
`/private/tmp/skill.cncf.d/a-3b8ec645-93f1-4b3a-864f-80ac62ce6bec/stdout.txt`.
That attempt is retained as failed evidence, not acceptance.

The parent admitted native causal repair candidate 4 under
`phase96-p96-07-postgresql-runtime-fix-manifest-001`. PG-RUNTIME-001 corrects
incidental logical tokens to the existing label grammar while preserving
physical quoted-field, schema, clipping and separate JDBC/dialect relation
scenarios. PG-RUNTIME-002 uses lawful EntityStore roots/successors in the
shared conditional fixture without changing its factories or race/ACK
expectations. PG-RUNTIME-003 replaces reserved schema names and quotes the
actual selected search-path schemas. PG-RUNTIME-004 moves name uniqueness to
suite lifetime and keeps literal native ICU collation names.

PG-RUNTIME-005 replaces an existing SQL NULL logical content placeholder with
the actual loaded overflow body while retaining business/audit NULL fields.
PG-RUNTIME-006 replaces prior body-owned metadata for changed content and adds
inline/overflow transitions, repeated preparation and bounded total property
obligations. PG-RUNTIME-007 resolves the actual native scalar guard type on
the transaction connection and casts only its unchanged encoded parameter;
real direct/CAS/physical/logical deletion obligations retain complete snapshots,
revision/readback and excluded-state preservation.

These bounded corrections are authored and unvalidated. This repair worker
ran no test, build, runtime or review command. Parent focused validation,
independent observation, original Step/Phase acceptance and final release
gates remain pending. Prior native/CFS repairs, original bases, PLAN epoch and
review history are retained; no PASS, commit or closure follows from this entry.

## Bounded actual-runtime remainder repair

The parent-owned representative attempt
`phase96-p96-07-postgresql-representative-004` reached 389 cases and retained
six failures. Diagnosis `phase96-p96-07-postgresql-runtime-diagnosis-004`
records the causal refinement; raw local evidence remains at
`/private/tmp/skill.cncf.d/a-ae2747ce-75f8-4f28-bb5c-c843cdaef084/stdout.txt`.
The prior body metadata suspicion narrowed to duplicate name ownership in the
versioned canonical merge. PG-RUNTIME-008-MANAGED-DOMAIN-OVERLAP now excludes
retained exact keys as well as changed keys from the domain partition without
deleting name or changing native duplicate-field rejection. A separate
Embedded/Detached merge specification authors omitted/changed-name cases and
two total bounded shrinking Field correspondence laws; native canonical
updates add independent before/after name retention evidence.

PG-RUNTIME-002-COLLECTION-FIXTURE-REMAINDER corrects the two independent absent
prospective successor fixtures to lawful EntityStore carriers, preserving
validated Plan construction and complete NotMatched/no-materialization oracles.
PG-RUNTIME-009-DELETE-GUARD-CARRIER distinguishes existing exact-value
pre-admission from native DOUBLE PRECISION parameter handling. Physical/logical
deletion uses the existing exact decimal carrier, while direct/CAS retain raw
Double. All original 32 guard cases remain, excluded-state taxonomy is explicit,
and four native raw Float/Double rejection cases preserve the closed exact-value
policy and full schema/root/body/revision/readback state.

These candidate-5 corrections and regression obligations are authored and
unvalidated. The repair editor ran no validation or self-review. Parent fresh
representative/affected validation and independent observation remain required;
original bases, epoch, native/CFS history, four P96-07 criteria and all later
Step/Phase/final gates are unchanged. This entry establishes no passing result,
acceptance, commit or closure.

## Bounded actual-runtime payload and security-fixture repair

The parent-owned `phase96-p96-07-postgresql-representative-005` attempt ran
399 cases across 14 suites, with 391 successes and eight failures. Its causal
record is `phase96-p96-07-postgresql-runtime-diagnosis-005`; raw local evidence
remains at
`/private/tmp/skill.cncf.d/a-1846dd26-22fc-46ac-b05c-c40d23df54a6/stdout.txt`.
The four prior attempt-004 fixture/guard failures now pass, and both canonical
updates reach revision two past duplicate-field admission. Those specific
corrections do not establish native acceptance. All previous failed results
and repair history remain preserved.

The remaining two canonical update failures belong to
PG-RUNTIME-010-CONTENT-WHITESPACE. The rendered values differ by one trailing
ASCII space; the lossy console log does not prove Unicode fidelity. The frozen
candidate-6 repair separates nonblank payload extraction from trimmed
metadata, retaining original text before byte-size/digest/Save and hydration.
The trim-nonempty eligibility predicate, metadata trimming, thresholds,
missing-content overflow preservation, raw SQL NULL and explicit SetNull
intents remain unchanged. Eight inline/overflow payload cases now author
spaces, Japanese/tab/CRLF, Markdown double spaces/newlines and mixed multiline
padding with actual memory plan/save/hydrate/repeat observations. Two
additional total bounded payload laws use companion-owned meaningful shrinking
and require at least 100 successes with zero discards. Twelve input/flag
planning cases retain the existing missing/NULL/empty/blank/SetNull policy and
check actual prior whitespace-bearing overflow/hydration remains unchanged.

The six new merge-specification failures belong to
PG-RUNTIME-011-MERGE-SECURITY-FIXTURE. The earlier fixture supplied created_by
without canonical security, so the existing complement produced four Fields
absent from the full expected vector. The fixture now supplies owner_id,
group_id, privilege_id and independently literal compact permission JSON in
retained-owner order, with nontrivial owner/group/other permissions. All four
deterministic cases and both original total bounded shrinking laws retain full
ordered Field, identity/name/title/NULL and revision oracles and explicitly
check the four security Fields. Exact boolean vector comparisons with safe
key clues avoid NULL printer failure without map/sort/subset weakening.
Production merge, security/default policy and upstream owners are unchanged.

Candidate 6 is authored and unvalidated. The editor ran no validation,
runtime, build or self-review command. Parent fresh representative/affected
validation and independent observation remain required; this entry supplies
no PASS, acceptance, commit or closure. PG1–PG12, original bases and PLAN
epoch, native/CFS history, all four original P96-07 OPEN criteria and every
later Step/Phase/final/aggregate gate remain intact.

Existing Debt (Separate Follow-up): pre-existing body-spec private helper
parameter/naming debt and the previous Sample-law scope remain unchanged.
`HYG-P96-PG-NULL-PRESENTABLE-001` remains read-only upstream
simplemodeling-lib Presentable/Field diagnostic debt, outside this repair.
Earlier protected-hook/source-size debt and P96-CREATE-HYG-001/002/003 retain
their original follow-up ownership and dispositions.

## Bounded SQL comparison and payload-fixture repair

The parent retains the failed
`phase96-p96-07-postgresql-representative-006` attempt and causal diagnosis
`phase96-p96-07-postgresql-runtime-diagnosis-006`. Raw local evidence remains at
`/private/tmp/skill.cncf.d/a-20c3e574-3ac6-4a55-b368-abb43b8e1f27/stdout.txt`.
Both canonical updates now retain original raw body whitespace, name and
revision two; repeated identical updates still advance the managed revision
to three. Payload text/bytes/digest/charset and saved-side correspondence
precede the remaining repeated-inline and side-address assertion failures.
These specific prior corrections do not establish native acceptance, and all
previous failed results and repair history remain retained.

The parent freezes candidate 7 in
`phase96-p96-07-postgresql-comparison-fix-manifest-001` revision 2.
PG-RUNTIME-012-PAYLOAD-REPEAT-FIXTURE corrects only the newly added repeated
payload candidate: existing content/charset Fields retain their keys and
positions while their values are replaced, and absent Fields are appended.
The older metadata-law helper is unchanged. All eight deterministic whitespace
cases, both total bounded shrinking payload laws, twelve input/flag cases and
complete ordered root/hydrated/saved-side/effect and raw NULL oracles remain.
PG-RUNTIME-013-SIDE-ID-EQUALITY replaces comparison to a freshly generated
entropy-bearing CollectionId with the exact Instance variant and required
collection name. Full side Fields, entry/ref, bytes/digest and effects remain
checked; global IDs, routing and equality remain unchanged.

PG-RUNTIME-014-NATIVE-NOOP-COMPARISON addresses SQL's persisted read-carrier
boundary. The existing decoder restores permission JSON to Record, while the
canonical retained candidate carries its JSON String; generic comparison alone
does not equate those carriers. A SQL-local WriteIfChanged wrapper now applies
the unchanged SQL decoder only to String-valued Fields in temporary ordered
comparison Records, then delegates to the unchanged generic comparator.
Non-String Fields, ordinary/scalar/malformed text and significant whitespace
retain their values. Security complements, desired writes, memory/global
codecs, key-only PostgreSQL mapping and clear markers are unchanged. Stale
admission still precedes equality; NoOp returns the original authoritative
Record, while actual permission changes advance revision and publish side
effects under the existing transaction.

The existing ACK specification additionally authors fixed Patch/Replace
samples and one total bounded real-SQL comparison law per actual PostgreSQL,
SQLite and MySQL backend. The companion sample has nonempty bounded safe
labels, raw Long arrays and booleans with strictly reducing shrinking and an
explicit given import. Shared bounded rows/pools are sequentially reseeded to
INITIAL1 for every sample. Independently written JSON candidates require typed
NoOp/full authoritative Fields/eight-target preservation, Stale before
equality, exact permission-only Applied at revision two with complete durable
root/side correspondence, and repeated changed-state NoOp. Five deterministic
raw-text cases per backend retain significant whitespace and malformed/scalar
text, distinguishing exact repeats from genuine changes. Each law requires at
least 100 successes and zero discards; these are authored requirements, not
execution results. Every existing ACK and cleanup scenario remains.

Candidate 7 is authored and unvalidated. The repair editor ran no validation,
runtime, build or self-review. Parent difference inspection, fresh focused
14-suite then 69-suite validation and independent native observation remain
required. PG1–PG12, original bases/PLAN epoch/native and CFS history, all four
P96-07 OPEN criteria and every later Step/Phase/final/aggregate gate remain
intact. This entry supplies no passing result, acceptance, commit or closure.

Existing Debt (Separate Follow-up): pre-existing protected SQL hook/public ABI
naming and source-size debt, old body-spec private naming/parameters and Sample
law scope, upstream HYG-P96-PG-NULL-PRESENTABLE-001 and original
P96-CREATE-HYG-001/002/003 keep their existing ownership and dispositions.
New repair helpers and companion sample fields follow the naming/type-owner
rules at authoring; no unrelated cleanup or upstream edit is included.

## Bounded existing-consumer correspondence repair

The parent retains `phase96-p96-07-postgresql-accumulator-001` and its
source-confirmed causal record
`phase96-p96-07-postgresql-accumulator-diagnosis-001` revision 1. Raw local
evidence remains at
`/private/tmp/skill.cncf.d/a-907ce564-6f1a-4781-a0ef-462fc8ee6e10/stdout.txt`.
The frozen repair is
`phase96-p96-07-postgresql-consumer-fix-manifest-001` revision 1, under the
same original P96-07PG-B boundary and native candidate 8.

PG-ACCUM-001-BRIDGE-CREATE-DELEGATION records a wrapper advertising its real
provider's AtomicCreate without implementing the create port. RecordingProvider
now delegates the atomic port to that same actual in-memory provider, counts
one attempt and one actual successful result, and returns that result unchanged.
The canonical EntityStoreCreate seed and post-seed observation reset remain.
All thirteen deterministic bridge cases and both generated laws retain their
runtime, authorization, transition, View and complete-state observations.

PG-ACCUM-002-GUARDED-CREATE-CAPABILITY records the guarded fixture erasing its
inherited AtomicCreate feature. Its capability override now adds only that
feature to guardedBaseline. The existing ordinary patch remains guarded-only
for UPDATE, with the canonical seed/reset and exact direct/CAS/guarded/load
expectations of 0/0/1/1 retained.
PG-ACCUM-003-ATOMIC-CAPABILITY-EXPECTATION records the installed-provider oracle
omitting AtomicCreate from the actual advertised set. The independently
enumerated exact expectation now includes it; the noop provider still requires
the explicit empty contract.

PG-ACCUM-004-SQLITE-RESERVED-FIXTURE records selected private table tokens
beginning with SQLite's reserved sqlite_ prefix. Only those token prefixes now
use cncf_sqlite_, retaining suffixes and the shared race identity. All twenty-three
ACK, first-use root-after-side, alias/collation, binary, column-admission,
normalized-readback and race scenarios retain their actual operations, faults,
counters and full independent selected/unrelated-state expectations.

Candidate 8 is authored and unvalidated by this editor. No test, build,
runtime or self-review was run. Parent fresh focused five-suite and unchanged
69-suite validation and independent native observation remain required.
PG1–PG12, CA1–CA12, all original P07C obligations, four P96-07 OPEN criteria,
original bases/PLAN epoch/native and CFS history, independent Step acceptance,
the single full Phase review, final validation/release and aggregate handoff
retain their original owners. This entry establishes no passing result,
acceptance, commit or closure and does not change earlier historical status.

Existing Debt (Separate Follow-up): HYG-P96-CONSUMER-LEGACY-FIXTURE-001 remains
separate pre-existing bridge helper naming/scope, UnitOfWork nested concrete
fixture types and provider-contract private policy-parameter debt. Original
P96-CREATE-HYG-001/002/003, prior SQL/body naming and source-size debt and
HYG-P96-PG-NULL-PRESENTABLE-001 retain their existing ownership and dispositions.

## Bounded CA9 reader correspondence repair

The parent retains the private causal record
`phase96-p96-07-postgresql-consumer-diagnosis-008` revision 1 and freezes
candidate 9 in `phase96-p96-07-postgresql-reader-fix-manifest-001` revision 1.
PG-ACCUM-005-SQLITE-READER-CONFIGURATION records the existing CA9 fixture's
configured writer and default reader using different ordinary-column
conventions. The scenario now owns a separate real reader with the same
normalization configuration and uses it for both complete snapshots. Full
returned-root equality and older root/side equality remain. Nested cleanup
closes the reader and retains the outer writer-close attempt even if reader
construction, scenario execution or reader closure fails.

PG-ACCUM-006-SQLITE-PHYSICAL-REVISION-ORACLE records the source-proven latent
lookup mismatch: the SQL reader preserves physical `cncf_revision`, while
the existing assertion requested `cncfRevision`. The corrected exact lookup
retains `EntityRevision.INITIAL`; literal expectations additionally require
ordinary `bodyId` to equal the side-entry reference and require `body_id` and
`cncfRevision` to be absent. These are existing-contract fixture corrections,
with no assertion-map transformation or production policy change.

Candidate 9 is authored and unvalidated by this editor. Parent focused
validation and independent native observation remain required; original
native/CFS history, acceptance owners and later Step/Phase/final gates remain
intact. This entry establishes no passing result, acceptance, commit or closure.

Existing Debt (Separate Follow-up): HYG-P96-CONSUMER-LEGACY-FIXTURE-001,
P96-CREATE-HYG-001/002/003, prior SQL/body naming and source-size debt and
HYG-P96-PG-NULL-PRESENTABLE-001 retain their existing ownership and dispositions.
