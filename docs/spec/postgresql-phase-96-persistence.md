# PostgreSQL Phase 96 persistence

Document role: normative intended native acceptance contract for PHASE-96 /
P96-07 / P96-07PG-A and P96-07PG-B. Status: documentary requirements, native
runtime source and executable specifications authored; parent validation and
independent native observation are pending. The existing
17-path atomic-create draft is preserved and unvalidated; it is not native
PostgreSQL evidence. All four original
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN.

The [primary Knowledge Store decision](../design/postgresql-primary-knowledge-store.md)
owns selection rationale. The [shared rules](../../RULE.md),
[repository rules](../rules/repository-rules.md) and
[document lifecycle](../../ai/directive/core/document-lifecycle.md) govern this
specification. The [native persistence design](../design/postgresql-phase-96-persistence.md)
records the settled implementation decisions; source authoring establishes no
execution or acceptance result.

## Scope and retained contracts

Native PostgreSQL persistence is mandatory for the current Phase 96 mutation
and reference handoffs. Real memory, SQLite and MySQL regression obligations
remain required. PostgreSQL identity grammar, an unsupported-capability result,
test-only JDBC, a substitute provider or prior MySQL results cannot satisfy
this contract.

The following owners remain authoritative; PG requirements apply their existing
semantics to PostgreSQL without redefining them:

- [Entity revision, conflict, versioned mutation and conditional transition](entity-conflict-and-conditional-transition.md).
- [Observed-delete provider](entity-observed-delete.md) and
  [canonical observed deletion](entity-observed-delete-canonical.md).
- [Strict-create CA1–CA12](entity-create-atomic-provider.md) and
  [provider ownership/design](../design/entity-create-atomic-provider.md).
- [Display mutation bridge and UB13](display-mutation-bridge.md#ub13-remaining-original-obligations)
  and its [design](../design/display-mutation-bridge.md).
- [SystemNode pool identity, lease and shutdown lifecycle](system-node-datastore-pool-lifecycle.md).
- [SQL NULL Record boundary](sql-null-record-boundary.md): SQL readback omits NULL attributes, retains all non-NULL empty Fields and physical NULL state, and preserves explicit SetNull write correspondence.

## Required behavior

### PG1: Native runtime availability

The runtime MUST provide a real PostgreSQL dialect and production JDBC driver,
PostgreSQL JDBC URL inference, explicit normal ComponentDataStore `kind` or
`type = postgresql`, `sql.dialect = postgresql` and existing generic JDBC paths.
Caller-owned construction and SystemNode-managed resolution MUST reach real
PostgreSQL. Existing selection policies, configuration precedence, security,
canonical resource identity and lease semantics remain authoritative; the
backend addition MUST NOT globally alter fallback/default policy or substitute
memory, SQLite or MySQL.

### PG2: Storage correspondence

Existing Record/scalar and ContentBody encoded carriers and normalization MUST
retain compatible authoritative readback. Generic structured carrier storage
MUST NOT imply a new native JSONB, full-text or vector-query API. Address
admission MUST use quoted identifiers, the effective search_path, actual
physical relations/columns and native id collation to reject root/side and
repeated-target aliases. Blanket case folding and rejection of legitimate
case-distinct PostgreSQL names are forbidden.

### PG3: Canonical positive create

Canonical positive creation MUST cover Embedded and Detached managed
representations and admitted unmanaged collections, generated and supplied
opaque IDs, INITIAL1, server complements and existing authorization. It MUST
return authoritative CreateResult and preserve Overflow, Inline, Absent and
SetNull body modes. Root and required side effects MUST share one actual
PostgreSQL transaction; hydration and independent durable observation MUST
show the corresponding root/body pair.

### PG4: Strict duplicates and races

Actual unique root-id arbitration MUST preserve the old root/body and unrelated
state. The native id-targeted `ON CONFLICT DO NOTHING` insert's count zero MUST
produce structured `DataStoreDuplicate` and roll back the whole unit, including
side writes. Other SQL constraint failures MUST NOT be mislabeled as root
duplicates. Preflight alone, root upsert, dummy revision, caller locks,
compensation and replay cannot establish strict creation.

### PG5: Direct, CAS and versioned update

Native direct/CAS/versioned update MUST retain AlwaysWrite, WriteIfChanged,
Optimistic, ObservedRequired and stale-before-equality semantics, exclusion
guards, the admitted managed field and positive Long revision rules.
Invalid/missing/exhausted revision admission MUST be structured. Root and
overflow effects MUST be atomic and readback authoritative. Revision zero,
implicit migration and last-write-wins fallback are forbidden.

### PG6: Observed canonical deletion

Observed physical/logical deletion MUST retain expected revision, exclusion
guards, capabilities and typed result shape. Success records the matched prior
revision. Logical deletion stores the next revision and preserves body state;
physical deletion removes the root and admitted body effects atomically.
Stale, notfound, invalid/exhausted revision and failure MUST leave complete
selected and unrelated data unchanged.

### PG7: Concurrency and conditional lifecycle

Existing-root native `FOR UPDATE` or the qualified atomic SQL equivalent MUST
preserve the existing conditional contract. Guarded successor Create/Bind,
root and side effects MUST share one PostgreSQL transaction with authoritative
outcomes. Independent concurrent attempts MUST NOT publish partial pairs or
bypass expected source/revision. Application locks and shadow providers are
forbidden.

### PG8: Schema and address lifetime

Strict-create schema preparation, metadata resolution and rows MUST use the
same actual transaction connection, with preparation before row effects.
Native quoted physical relation, column, primary key, id collation and
search_path determine both admission and writes. Inaccessible, ambiguous or
unsupported schema MUST fail structurally before effects. Schema preparation
MUST NOT invent identity grammar, implicit managed revisions or migration of a
user database. MySQL DDL preparation remains outside its data transaction.

### PG9: Known failure preservation

Known side/root/before-publish/SQL-execution/precommit failures and verifiably
rejected commit MUST roll back the complete unit. Evidence MUST include an
actual root-after-side failure, exact fault markers/counters and full
independent PostgreSQL snapshots of selected and unrelated state. Applicable
total bounded preservation properties MUST require at least 100 successes and
zero discards. Existing CFS laws MUST NOT be removed or inverted to accept
observed corruption.

### PG10: Commit acknowledgment boundary

After real connection commit, explicit indeterminate return, escaped throw or
null acknowledgment MUST NOT become a known unchanged-state failure or a
success receipt. Evidence MUST show exactly one attempt, the correct durable
committed pair and no replay. Unknown rollback retains indeterminate semantics.
Postcommit projection pending/failure MUST preserve the actual canonical
committed outcome; provider acknowledgment MUST NOT be fabricated into a
whole-UoW or Display receipt.

### PG11: Task-owned acceptance and resource lifecycle

Actual positive PostgreSQL execution is mandatory. Disabled/unavailable native
execution MUST fail closed as missing acceptance, never produce skip-based
PASS through pending, ignore or cancel. Evidence MUST use independent
pools/readers and actual normal ComponentDataStore plus SystemNode acquisition,
borrow, lease and close boundaries, with no leak or user-owned ops/development
database writes. The later parent native plan MUST freeze the pinned baseline,
dependencies, task-private database/container and allowed effects. No remote
or public publication is authorized.

### PG12: Original integration and closure

Actual registered Entity AND Aggregate, ordinary Display mutations,
authorization/revision transport, create-to-Detail/List and delete-to-List MUST
execute on native PostgreSQL. The selected source MUST actually reload through
contextual `afterCommitResponseC`, preserving committed pending/failure without
replay. The required representative and exact expanded affected accumulator
MUST include native PostgreSQL and retained MySQL/SQLite/memory. Original four
P96-07 criteria, P96-08 through P96-11, independent Step review, the single full
Phase review for the admitted epoch, final validation/release and the aggregate
handoff remain unchanged.

## Required future behavior and evidence matrix

Each row is a future executable obligation, not a current result. All native
rows require real, positively executing PostgreSQL with no substitution or
skip. Snapshots compare complete selected and unrelated data; durable SQL
observations use independent connections rather than fixture maps.

| Evidence ID | Contract | Required behavior and evidence |
| --- | --- | --- |
| PG-E1 | PG1 | Given each admitted JDBC/explicit kind/type/dialect route, when caller-owned and SystemNode-managed normal resolution executes, then real PostgreSQL performs storage operations with existing policy/precedence/security/identity admission. |
| PG-E2 | PG2 | Record/scalar/ContentBody round trips retain exact normalized authoritative values; native quoted case, search_path and id collation reject actual aliases while admitting legitimate distinct addresses. |
| PG-E3 | PG3 | Embedded/Detached and admitted unmanaged positive create, generated/supplied ID and all body modes yield correct authoritative initialization, hydration and an independently durable root/body pair. |
| PG-E4 | PG4 | Existing-root duplicates and independent absent-root racers preserve complete old/unrelated state; native id-conflict count zero rolls back side writes, while other constraint errors retain their proper structured failure. |
| PG-E5 | PG5 | Direct/CAS/versioned changed, no-op, stale-before-equality, exclusion, invalid/missing/exhausted revision cases prove admitted policy, managed-field correspondence, atomic body effects and independent readback. |
| PG-E6 | PG6 | Canonical physical/logical delete proves prior matched receipt, logical next revision/body preservation or physical pair removal; every rejected case preserves complete independent snapshots. |
| PG-E7 | PG7 | Independent concurrent connections exercise existing-root locking/qualified mutation and guarded successor Create/Bind; one authoritative winner and admitted losers never publish partial pairs or bypass source/revision. |
| PG-E8 | PG8 | Same-connection native schema/catalog/address preparation precedes effects; quoted relation/column/PK/collation/search_path admission and transaction rollback preserve schema/row correspondence; unsupported/ambiguous/inaccessible schema rejects before effects. |
| PG-E9 | PG9 | Exact side/root/before-publish/SQL/precommit/rejected-commit markers and counters establish full rollback, including actual root failure after side work; total bounded preservation laws pass >=100 successes and zero discards where applicable. |
| PG-E10 | PG10 | Actual commit followed separately by explicit indeterminate, escaped throw and null ACK leaves the correct durable pair, exactly one attempt and no false unchanged claim/success receipt; unknown rollback and committed projection failure retain original semantics. |
| PG-E11 | PG11 | Task-private positive native execution proves independent pools/readers, ordinary Component/SystemNode acquisition/borrow/lease/close and cleanup; disabled or unavailable PostgreSQL supplies no acceptance credit. |
| PG-E12 | PG12 | Registered Entity AND Aggregate, ordinary routes/auth/revision transport, real update/create/delete Display lifecycles and selected contextual committed reload execute; representative plus exact expanded native/retained-backend accumulator and original later gates retain their owners. |

## Authored executable correspondence

The existing logical collection grammar remains unchanged. Ordinary fixture
tokens MUST be lawful case-preserving ASCII labels with suite-lifetime unique
counters surviving 63-byte clipping. Conditional roots/successors MUST use
the existing validated EntityStore carrier; side records retain their admitted
carrier. Arbitrary native relation names MUST be exercised through the
JDBC/dialect layer without claiming a new CollectionId capability. Native
quote/backslash/dot/Unicode columns and separately quoted dot/quote/Unicode
search-path schemas MUST retain independent physical/catalog observations.
Task-private schema names MUST NOT use the reserved pg_ prefix. ICU collation
names retain their literal physical characters and suite-lifetime uniqueness.

ContentBody hydration MUST replace previous normalized logical content fields
with one authoritative loaded body while retaining nonowned Field values.
SQL readback omits NULL attributes under the SQL NULL boundary. Supplied pure
legacy raw-null inputs retain their existing ContentBody compatibility contract.
A changed inline or overflow preparation MUST replace
previous normalized byte-size/digest/charset/storage/ref fields with exactly
one current set and retain the established side-effect intent. Changed and
repeated preparation MUST preserve every nonowned business/audit field.
Encoded bytes, digest, charset, inline/overflow threshold and reference MUST
correspond to the current body. Missing-content preservation and explicit
SetNull remain governed by existing policy. The bounded metadata ownership law
requires at least 100 successful ScalaCheck cases and zero discards.

Eligible nonblank ContentBody text MUST preserve its original leading,
trailing and interior whitespace through planning, actual saving and
hydration. The existing trim-nonempty eligibility predicate MUST NOT trim the
accepted payload. Encoded byte size and SHA-256 MUST be independently derived
from that exact text and resolved charset. Charset metadata MAY retain its
existing padding normalization; storage/ref/id metadata trimming is unchanged.
Forced-inline (65536 bytes) and forced-overflow (1 byte) cases MUST exercise
leading/trailing spaces, Japanese with tab/CRLF, Markdown internal double
spaces/newlines, and mixed tab/CRLF padding around multiline text. Every case
MUST check actual planForVersionedSave, prepareForSave and hydrate, repeated
preparation of the same text, charset/storage/ref/side intent, complete ordered
Fields/effects, and every nonowned Field, including supplied legacy raw-null
inputs distinct from SQL readback absence.
The repeated payload candidate MUST retain existing normalized content and
charset Field keys and positions, replacing only their values and appending
either Field only if absent. Repeated equality MUST remain complete ordered
Field/effect equality. The overflow address oracle MUST require the exact
CollectionId.Instance variant and cncf_content_body_overflow collection name,
not equality to a newly constructed entropy-bearing Instance. Exact side
entry/ref, complete independently specified saved-side Fields, root Fields,
bytes/digest/charset and effects remain required.

Two total bounded actual-memory payload laws, one per forced mode, MUST
require at least 100 successes and zero discards. Companion-owned
PayloadSample(core,prefix,suffix,charset) MUST generate a meaningful 1–40
character ASCII/Japanese/Greek core, nonempty 1–6 character whitespace prefix
and suffix, and UTF-8/UTF-16BE. Expected text is prefix+core+suffix without
trimming. Shrinking MUST strictly reduce core/prefix/suffix lengths while
retaining each nonempty; the law MUST NOT use implication, suchThat or discard
filtering. The previous metadata Sample law and shrinker remain required.

The existing missing-content policy MUST retain twelve cases: missing content,
supplied legacy raw null, empty text, ASCII-whitespace-only text, SetNull and Some(SetNull),
each under both preservation flags on an already saved overflow body. The
first four inputs with preservation enabled MUST keep the complete input
Fields with no effects; with preservation disabled they MUST remove only
storage/ref and retain the existing Delete intent. Both explicit SetNull
forms MUST remove normalized content/storage/ref and plan Delete regardless
of the flag. Pure planning MUST leave actual overflow storage and the prior
raw-whitespace hydrated body unchanged. This defines no new blank, carrier or
NULL policy.

Native EqualTo guard parameters MUST use same-transaction actual relation-OID
and column type evidence from pg_attribute joined to pg_type/pg_namespace.
Only actual pg_catalog base scalar text/varchar/bpchar, int2/int4/int8,
float4/float8, numeric and bool are admitted. CAST applies to the parameter
without typmod/length, with separately quoted native names and unchanged
encoded setObject binding. Missing/ambiguous/unsupported type evidence MUST
produce the existing structured schema failure before row effects. Guard
ordering, NULL and Present behavior, revisions and existing-record admission
are unchanged. Actual TEXT/VARCHAR numeric guards, BIGINT numeric guards and
matching floating guards MUST cover direct/CAS and observed physical/logical
deletion, full independent snapshots, revision/readback and excluded-state
preservation. Original Embedded/Detached canonical deletion remains required.

Versioned Record merge MUST give the sanitized patch precedence over retained
exact keys and exclude both patch and retained owners from untouched domain
Fields. Embedded and Detached candidates MUST contain exactly one omitted or
explicitly changed name, the changed title, the existing identity/managed
values and every untouched non-NULL business/audit Field, with SQL NULL
attributes absent under the readback boundary, in their
existing order. The representation's managed revision MUST be absent. Two
total bounded merge laws, one per representation, MUST require at least 100
successes and zero discards with meaningful strictly reducing Sample shrinking.
The two native canonical update cases MUST independently read name before and
after mutation and retain their original body/revision/no-op/stale and
unrelated-state oracles.

All four deterministic merge cases and both generated laws MUST start with
explicit canonical owner_id=existing_owner, group_id=existing_group,
privilege_id=existing_privilege and compact permission JSON after published_by
and before title. The independent fixture MUST encode owner(read=true,
write=true,execute=false), group(read=true,write=false,execute=false) and
other(all false), ordered owner/group/other and read/write/execute. Expected
permission MUST NOT be derived by a production complement or formatter.
Every deterministic case and generated law MUST explicitly check exact
ordered retention of these four security Fields while keeping full ordered
Field equality, identity/name/title, explicit absent SQL NULL attributes,
retained non-NULL empty Fields and managed-revision expectations.
Deterministic full-Field diagnostics MUST use exact boolean
equality with safe key/primitive clues; supplied legacy raw-null inputs still
MUST NOT invoke Field.toString on NULL. Map
collapse, sorting and subset comparison MUST NOT replace complete ordered
correspondence. Production security complements and owner tables are unchanged.

The two source-mismatch prospective successor cases MUST use the existing
lawful EntityStore/EntityCollectionId carrier, return authoritative NotMatched
and materialize neither successor nor side relations.

All 32 native TEXT/VARCHAR(4)/BIGINT/DOUBLE PRECISION guard scenarios MUST
remain. DOUBLE PRECISION physical/logical deletion uses the existing exact
BigDecimal("0.0") carrier; raw Double(0.0) remains in direct/CAS scenarios.
Excluded-state results MUST match existing entityNotFound taxonomy and retain
complete snapshots/readback. Four native raw Float(0)/Double(0) physical/logical
deletion rejection cases MUST match DataStoreConditionalValue.from(value)
taxonomy, include policy entity-conditional-transition.exact-value and preserve
complete native schema/root/body/revision/readback. Native scalar type admission
MUST NOT imply raw floating admission into the closed exact-value algebra.

The Phase100 physical-provider activation dated 2026-10-18 supersedes the
earlier Phase96 inferred Record/Vector correspondence at this boundary.
Under [R2/R8](datastore-logical-type-mapping.md#r2-physical-text-and-exact-string),
plain SQL writers receive explicitly encoded text and readers retain every
String verbatim, including valid JSON objects/arrays and their significant
lexical whitespace. WriteIfChanged temporary comparison Records retain original
Field keys/order and values, omitting only exact Single(Update.SetNull) Fields
as absent attributes. They MUST NOT infer logical JSON equivalence. Declared
logical reconstruction belongs to the selected mapper or explicit caller codec. The generic comparison,
revision/exclusion rules, security complements, memory behavior and all global
encoding/decoding contracts MUST remain unchanged. Desired writes and clear
markers MUST NOT be normalized by this comparison boundary. ObservedRequired
stale admission MUST precede equality, ordinary Optimistic ordering MUST remain
intact, and NoOp MUST return the full original authoritative existing Record
without advancing its revision or publishing side candidates.

The existing SqlMutationAcknowledgementSafetySpec MUST additionally exercise
actual PostgreSQL, SQLite and MySQL storage correspondence using plain
SqlDataStore writers, MutationSession lawful root/side/successor targets and
independent readers. Fixed samples MUST cover both Patch and Replace. One
total bounded real-SQL law per backend MUST require at least 100 successes and
zero discards on shared bounded rows/pools with sequential INITIAL1 reseeding.
Companion-owned SqlComparisonSample(label,ownerwrite,numbers,replace) MUST
generate nonempty 1–12 character safe ASCII/Japanese/Greek labels, 1–4 Long
values in -10–10, and both booleans. Its explicitly imported shrinker MUST
reduce nonempty label/array lengths, nonzero arrays to all zero, or true
booleans to false; generated inputs MUST NOT be filtered or discarded.

Each sample MUST explicitly encode its known nested permission Record with
owner(read=true,write=ownerwrite,execute=false), group(read=true,write=false,
execute=false) and other(all false), its attributes Record(label,count) and
its Long Vector to physical JSON text before the plain SQL writer. Independently
specified String expectations retain the exact readback. Exact whitespace-padded
note and malformed opaque text remain unchanged. This is fixture-owned encoding,
not an automatic provider or runtime-shape conversion. Candidates MUST carry
independently written permission/attributes/numbers JSON strings. Replace MUST
retain all loaded root Fields in key/order, replace only those three values
and remove the managed revision; Patch MUST supply exactly those values.
Every candidate MUST attach a real existing-side Save with deliberately
different content. Equivalent state at expected one MUST yield typed NoOp,
full original authoritative Fields, revision one and unchanged complete
eight-target snapshots. Equivalent state at expected two while actual one
MUST yield typed Stale(expected two, actual one) and the same unchanged state.

Flipping only owner write permission at expected one MUST yield Applied and
revision two, with independently specified changed permission and all original
attributes/numbers/note/opaque/identity/domain keys retained. Independently
observed Record/Seq carriers MAY be recursively converted to fixture Map/Vector
values for the oracle; production equality/formatter/decoder utilities MUST NOT
derive expected values. The complete durable root map MUST equal the baseline
with only permission and revision changed, and the complete side map MUST
equal the baseline with only prescribed content changed. Every other snapshot
target MUST remain unchanged. The authoritative raw revision carrier MAY be
used only after a separate exact EntityRevision two assertion. Repeating the
changed state at expected two with another different side candidate MUST yield
typed NoOp, full authoritative Fields, revision two and the full unchanged
committed snapshot.

The original five deterministic raw-text cases per backend MUST cover
whitespace-padded non-JSON text, malformed object text, malformed array text,
true/false-looking strings and 1/2-looking strings. Two additional cases MUST
cover valid JSON object/array text with equal JSON contents but different
lexical whitespace, proving the physical String rather than inferred JSON
comparison contract. Repeating exact text with a different side
candidate MUST yield NoOp at revision one with complete preservation; genuinely
different text MUST yield Applied at revision two with exact changed text and
side content, all other baseline keys and all unrelated targets unchanged.
Given/When/Then MUST be adjacent to setup/action/expectation in every fixed,
generated and helper stage. Every original ACK/action/mode/rollback case and
container cleanup obligation remains required. These backend correspondence
laws establish no registered Entity/Aggregate/Display acceptance.

The [SQL NULL Record boundary](sql-null-record-boundary.md) additionally requires
both normalization conventions on each actual backend, full ordered non-NULL
readback including empty scalar/structured values, NULL-only business
projection with mandatory id, IsNull and absent serialized key, independently
observed complete physical columns/rows and unrelated/body preservation.
Generic update/save, native direct/CAS, strict create and versioned Patch/Replace
retain explicit clear versus omission. First clear is Applied; repeated current
WriteIfChanged clear is NoOp with no side publication; stale precedes equality.
The same SQL suite authors one additional sequential total bounded clear/repeat/
omit law per backend, at least 100 successes and zero discards, reusing bounded
rows/pools with a reducing companion-owned nonempty multilingual sample.
PostgreSQL readback preserves noncolliding separator-free Foo/foo case
distinction under both conventions for one/both NULL and unrelated non-NULL
rows. Actual foo_bar/fooBar aliases decode to fooBar and MUST fail with
configurationInvalid before omission across all four NULL masks: neither,
left only, right only and both. Exact readers retain complete ordered raw-name
non-NULL Fields, including the existing id-only row for both NULL. Complete
independent selected/unrelated physical snapshots MUST remain unchanged.
Strict-create Foo/foo write-alias rejection, physical NULL columns/cells,
PG1–PG12 and every existing native gate remain required.

The following source files are authored and remain pending actual parent
validation. Their presence supplies no PG-E passing evidence. The frozen
native plan owns the exact source/build paths, unchanged APIs, pinned versions
and focused commands. Executable specifications place Given/When/Then at
setup/action/expectation boundaries, use should/which/in grouping and require
total bounded PBT where applicable.

| Evidence IDs | Authored executable specification |
| --- | --- |
| PG-E1, PG-E11 | [PostgresqlComponentDataStoreAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/datastore/PostgresqlComponentDataStoreAcceptanceSpec.scala): real ordinary direct and SystemNode-managed routes, precedence, matching explicit-driver aliases, non-owning views, released borrows and actual node shutdown. |
| PG-E2, PG-E4, PG-E5, PG-E8 | [PostgresqlNativePersistenceAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/datastore/PostgresqlNativePersistenceAcceptanceSpec.scala): actual carriers, CRUD/query/paging, case/clipping/field correspondence, schema/search_path/primary-id collation, extra unique constraint isolation, native revisions and independent CAS. |
| PG-E3, PG-E4, PG-E8, PG-E9, PG-E10; CA1–CA12 and retained CFS families | [PostgresqlEntityCreateAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/entity/PostgresqlEntityCreateAcceptanceSpec.scala): canonical representations and body modes, generated/supplied identity, INITIAL and complements, duplicates, real checkpoints/acknowledgement, first-use transactional DDL, permission/resident/View and native id races. CA11's enclosing Display lifecycle remains pending. |
| PG-E5, PG-E6, PG-E9 | [PostgresqlCanonicalMutationAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/entity/PostgresqlCanonicalMutationAcceptanceSpec.scala): actual canonical update/body hydration, no-op/stale and authorization; typed prior-match physical/logical observed deletion, revision/guard/failure preservation and resident/View behavior. |
| PG-E7, PG-E9, PG-E10 | [PostgresqlConditionalTransitionAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/datastore/PostgresqlConditionalTransitionAcceptanceSpec.scala): actual source predicates, authoritative Create/Bind/NotMatched, collision/rollback/acknowledgement and independent same-root or shared-successor races. |
| PG-E3, PG-E4, PG-E9 | [PostgresqlEntityCreatePropertySpec](../../src/test/scala/org/goldenport/cncf/datastore/PostgresqlEntityCreatePropertySpec.scala): positive pair, duplicate preservation and real no-orphan laws; each requires at least 100 successes and zero discards on bounded shared pools. |
| PG-E2, PG-E5, PG-E9, PG-E10 | [SqlMutationAcknowledgementSafetySpec](../../src/test/scala/org/goldenport/cncf/datastore/SqlMutationAcknowledgementSafetySpec.scala): actual PostgreSQL, SQLite and MySQL create/versioned/conditional wrappers, direct/CAS/delete/read-only outcomes, known rejection, real commit then indeterminate/throw/null, rollback refusal and borrowed-connection discard; fixed Patch/Replace carrier correspondence and three total bounded shrinking real-SQL laws requiring >=100 successes and zero discards each, complete no-op/stale/permission-change root/side/revision snapshots, and seven exact raw-text repeat/change cases per backend (the original five plus two JSON lexical-whitespace cases). |
| PG-E2, PG-E3, PG-E5 | [ContentBodyStoragePolicySpec](../../src/test/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicySpec.scala): supplied pure legacy raw-null hydration and metadata law, distinct from SQL readback omission; eight exact-whitespace forced inline/overflow save/hydrate/repeat cases with position-preserving repeat Fields and exact Instance/name storage-address assertions, two additional total bounded shrinking actual-memory payload laws requiring >=100 successes and zero discards each, and twelve unchanged missing/NULL/empty/blank/SetNull planning cases preserving actual prior overflow state. |
| PG-E5 | [EntityVersionedRecordMergeSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedRecordMergeSpec.scala): all four Embedded/Detached omitted/changed-name cases and two retained total bounded shrinking laws requiring >=100 successes and zero discards; complete independently specified canonical security, exact unique ordered non-NULL Field correspondence, explicit SQL-null attribute absence and retained non-NULL empty Fields with safe diagnostics, identity/name/title retention and managed revision removal. |
| PG-E1–PG-E11 supporting fixture | [PostgresqlPersistenceFixture](../../src/test/scala/org/goldenport/cncf/datastore/PostgresqlPersistenceFixture.scala): task-owned actual database and durability settings, independent pools/readers, canonical fixture delegation, real JDBC fault proxies and complete snapshots. |
| PG-E12 | [PostgresqlDisplayMutationServiceListAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayMutationServiceListAcceptanceSpec.scala): LST-PG1 and LST-PG2 require real managed registered Entity AND Aggregate/root/alias, both representations, native CUD/Detail/dynamic List, ordinary HTTP, authorization/no-op/stale/physical/logical delete and committed pending/read-failure/no-replay with complete independent native/body/resident/View/witness state. LST-PG-P1 separately requires >=100 total shrinking complete lifecycle sequences, one worker, zero discards and <=4 owned sessions with exact root/body/witness cleanup. Execution and original P96-07 acceptance remain pending parent gates; native kernel suites alone do not complete this row. |

### C4C List integration evidence boundary

The [ordinary Display mutation contract](display-mutation-service.md#c4c-01c4c-04-canonical-list-after-mutation)
requires the registered Browser to use the selected provider's real canonical
durable search and actual current Embedded/Detached revision carriers. Native
Aggregate/root/alias queries use their registered contextual collection query
and full-root builder. Owner/foreign/empty and arbitrary third-ID witnesses
establish dynamic membership rather than fixed selected/unrelated enumeration.
UserPermission, normal factory authorization, full ordered typed fields,
source/provenance and complete independent native catalog/type/row/NULL/body
state remain mandatory. Ordinary query selectors remain resourceType/screenId.

Fresh explicit List after create/update/delete supplements the retained C3/C4B
Detail, native kernel concurrency/rollback/acknowledgement and other backend
evidence. A committed pending/read failure retains its real outcome and cannot
replay a command. The new suite requires real PostgreSQL 17.11, exact
server_version_num 170011 and fsync/synchronous_commit on, and fails when native
execution is unavailable. Its finite owned pool closes witness/root/body rows
and owned sessions, including evictions, without tuning shared/server pools.
The new source mapping records a required executable obligation, not a result.

## Acceptance status and limits

No new execution, passing result, Step acceptance, commit or Phase closure is
established by documentary or native source authoring. Historical 41-suite and proposed
56-suite accumulators and earlier backend evidence retain their original
subjects; they MUST NOT be rebound as PostgreSQL evidence. The current
17-path draft and new native subject still require their actual selected
validation and independent observation. The frozen native class-C handoff
requires its representative and exact expanded affected commands, with
PostgreSQL and MySQL positively enabled and no skipped required cases. There
is no passing receipt from this authoring work, and original acceptance and
release gates remain unchanged.

Broader JSONB/full-text/recursive-query/pgvector/hybrid-search and RDF products
are outside this Phase minimum. Adoption provenance is recorded in the
[journal](../journal/2026/10/2026-10-14-postgresql-primary-knowledge-store.md);
the journal independently defines no behavior.
