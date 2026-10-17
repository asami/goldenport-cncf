# PostgreSQL Phase 96 native persistence design

Document role: settled implementation decisions for PHASE-96 / P96-07 /
P96-07PG-B. Native source and executable specifications are authored, with
parent validation and independent observation pending. This document supplies
no execution or acceptance result. The
[persistence specification](../spec/postgresql-phase-96-persistence.md) owns
PG1–PG12; the [primary Knowledge Store decision](postgresql-primary-knowledge-store.md)
owns selection rationale. The [strict-create design](entity-create-atomic-provider.md)
and existing revision, conditional-transition, observed-delete and SystemNode
contracts retain their responsibilities.

## Baseline and ownership

The production runtime uses `org.postgresql:postgresql:42.7.14` in Compile.
Native acceptance uses `org.testcontainers:testcontainers-postgresql:2.0.5`
in Test and `postgres:17.11-bookworm`. The private fixture explicitly enables
`fsync=on` and `synchronous_commit=on`, and reads the actual server version and
settings through JDBC. PostgreSQL acceptance requires
`CNCF_LIVE_POSTGRESQL_TEST=true`; unavailable or disabled execution fails and
cannot receive acceptance credit. The all-backend acknowledgement suite also
requires positively enabled actual MySQL.

Each suite owns its container, one monotonically increasing fixture-name
counter and all direct resources. Every Database wrapper, including baseline
inspection, receives that same suite counter. Legal logical tokens retain case
and put the unique ASCII counter before the descriptive label so native
63-byte clipping cannot discard uniqueness. Intentional physical identifiers
are constructed separately and retain their literal characters. Generated cases share
bounded pools, receive distinct real storage targets, and close resources at
their owning boundary. Fixtures never contact operator or development
databases. Fixed private fixture credentials remain in test source and are
not documentation or diagnostic payloads. No production provisioning,
migration, deployment or publication follows from this baseline.

## Native selection and SQL carriers

`PostgreSqlDialectDriver` implements the existing public dialect interface.
`SqlDataStore.Postgresql` admits PostgreSQL JDBC URLs and Auto recognizes the
same scheme. An incompatible explicitly selected URL rejects before pool
construction with fixed nonsensitive diagnostics. The production factory and
normal ComponentDataStore paths select `org.postgresql.Driver` unless the
existing explicit driver precedence supplies another value.

Direct and SystemNode-managed `kind`/`type = postgresql`, explicit
`sql.dialect = postgresql`, generic JDBC Auto and URL inference retain existing
selection policies, current/basic/legacy prefixes and parameters-before-config
precedence. Existing HMAC identity, explicit driver identity properties,
aliases, leases and registry ownership remain unchanged.

Identifiers use escaped double quotes. Existing CollectionId.Instance and
EntityCollectionId labels retain their existing ASCII label grammar; neither
admits arbitrary dots, quotes, backslashes, Unicode or hyphens. Provider
acceptance therefore uses legal case-distinct or long ASCII collection labels.
Arbitrary quoted physical relations are exercised separately through the
existing JDBC/dialect interface, without adding a public logical-ID capability.
Literal dot/quote/Unicode schema names and quote/backslash/dot/Unicode columns
exercise native addressing through legal collections. Private schemas start
with cncf_, never PostgreSQL's reserved pg_ prefix, and currentSchema receives
the separately quoted schema value. Metadata literals use escaped PostgreSQL E
strings; row values use JDBC parameters. Default identity storage is
`TEXT PRIMARY KEY`; integral/boolean encoded carriers use BIGINT, floating
carriers use DOUBLE PRECISION, and the remaining existing carriers use TEXT.
Existing Record, sequence, JSON-in-TEXT and ContentBody encoding remains the
owner of values. This introduces no native JSONB or query API. Unbounded
PostgreSQL paging uses OFFSET directly.

## Physical address and field correspondence

Each transaction resolves the requested relation under that connection's
effective search_path using `to_regclass` and the real pg_catalog schema,
relation OID, names and columns. Native `pg_catalog.name` casts determine
identifier clipping, including UTF-8 boundaries. Existing ordinary tables
receive ROW EXCLUSIVE relation locks and are re-resolved before effects.
SQL joins separately quoted schema and relation identifiers from that actual
address. An absent relation uses the real current schema and canonical name;
transactional preparation is followed by actual re-resolution.

There is no address cache or persistent registry. Native case-distinct names
remain distinct. Root, successor and side aliases compare actual physical
relation identity and native primary-id equality, including differently typed
logical collections and clipped names. Unsupported or inaccessible relations
reject structurally. Delete-only absent side targets need no DDL.

Physical SQL column names and decoded Record read keys are separate views.
Raw input aliases are checked before any last-wins collapse. Existing column
metadata and native clipping resolve requested fields, revision fields,
exclusions and conditional expected fields. Normalization rejects ambiguous
decoded keys; disabling it preserves legitimate quoted case distinctions.
Mapped fields retain their original Field value, including clear markers.
Authoritative PostgreSQL readback checks all decoded names before NULL
omission, then retains every non-NULL decoded Field directly, including empty
Record/Vector/String and numeric zero. SQL NULL attributes are absent while
physical columns and NULL cells remain. Exact id and framework revision keys
are ordered first. The [SQL NULL design](sql-null-record-boundary.md) and
[specification](../spec/sql-null-record-boundary.md) own this shared SQL boundary.
The managed `cncf_revision` readback exception remains intact.

ContentBody owns logical content and its byte-size, digest, charset, storage
and reference metadata. Overflow hydration removes previous normalized content
fields, including a supplied legacy raw-null placeholder, before appending exactly one loaded
body. Changed-content planning computes charset/bytes/digest before removing
previous normalized body metadata and appending the current set. Inline keeps
content; overflow removes content and carries its existing Save intent. These
filters reconstruct Record from Field values, preserving every nonowned
business/audit Fields. SQL readback NULL attributes are already absent;
supplied pure legacy raw-null inputs keep their existing behavior.
Missing-content overflow preservation, explicit SetNull,
thresholds, identity and Delete intents retain their existing semantics.

Nonblank text payload extraction keeps the original leading, trailing and
interior whitespace. The existing `text.trim.nonEmpty` predicate determines
eligibility without normalizing the eligible payload. Charset, storage marker,
reference and identity metadata keep their existing trimming behavior. Byte
size and SHA-256 describe the original text encoded with the resolved charset;
inline content and overflow Save/hydration retain that same text. Repeated
preparation of identical content retains complete ordered root Fields and side
intent. The repeated payload fixture replaces existing normalized content and
charset values in their original Field positions and keys, appending either
only when absent. It does not move existing inline content behind business or
audit Fields. Overflow storage-address evidence checks the exact
CollectionId.Instance variant and cncf_content_body_overflow collection name;
constructing another entropy-bearing Instance is not an equality oracle for
the existing address. Exact entry/ref, complete saved-side/root Fields,
payload bytes/digest/charset and effects remain independently checked.
Missing content, supplied legacy raw null, empty text and ASCII-whitespace-only
text retain the existing missing-content policy: preservation enabled on an
existing overflow keeps the complete input and produces no effects;
preservation disabled removes only storage/ref and plans Delete. Explicit
SetNull, including Some(SetNull), removes normalized content/storage/ref and
plans Delete under either preservation flag. Pure planning does not execute
that intent or change an already saved overflow body.

Conditional fixture roots and successors use the existing EntityStore carrier
with lawful EntityCollectionId labels; side collections remain Instances.
Absent prospective successor fixtures use that same EntityStore carrier before
validated Plan construction. An authoritative source mismatch leaves both
prospective successor and side relations absent.
Validated Root/Plan construction, successor Create/Bind, source conditions,
revisions and the original race/failure/acknowledgement matrix are unchanged.

Versioned canonical Record merge composes disjoint changed, retained-managed
and untouched-domain partitions. The sanitized patch owns changed keys; the
retained partition owns its existing exact keys, including an omitted name.
The domain partition excludes both owners. An explicitly changed name takes
patch precedence, and an omitted name appears exactly once. Both revision
representations remove their managed revision from the candidate while keeping
identity, retained managed values and untouched non-NULL business/audit Field
order, with explicit SQL-readback NULL absence and non-NULL empty retention.
Sibling merge paths and storage-owner tables retain their
existing responsibilities; native duplicate-field rejection is unchanged.

The versioned merge specification supplies a complete canonical security
fixture in retained-owner order after published_by and before title:
owner_id=existing_owner, group_id=existing_group,
privilege_id=existing_privilege and independently written compact permission
JSON. Owner permissions are read/write true and execute false; group read is
true with write/execute false; other permissions are all false. JSON order is
owner/group/other with read/write/execute within each role. This fixture
expresses the established security contract; it introduces no new complement
or default behavior. Full ordered Field equality and explicit absence uses boolean
equality with key/primitive clues so failure diagnostics do not invoke the
upstream NULL Field printer. It retains exact values and order rather than
collapsing Fields into maps, sorting or accepting subsets.

## Native primary arbiter and transactions

Every extant Entity mutation target must have the admitted immediate,
nondeferrable, valid/ready single-column text/varchar primary key on exact
`id`. Catalog type and index/column collation correspondence reject unsupported
composite, deferred, domain, citext, bpchar or nonprimary schemas without
migrating them. Equality binds two TEXT values under the actual separately
quoted catalog collation, including ICU nondeterministic collations.

Strict root and successor creation target the actual primary constraint with
`ON CONFLICT ON CONSTRAINT ... DO NOTHING`. Count one admits authoritative
readback; count zero means the existing strict-create duplicate or conditional
successor-collision result. Other constraint errors remain failures; no query
runs through a transaction aborted by such an error. Side Save uses that same
actual primary constraint as its merge arbiter. Additional unique indexes
with different equality cannot redirect Save to an unadmitted id.

Strict PostgreSQL create resolves, prepares and revalidates schema on the
single transaction connection before row work. It guards absence, checks each
original side checkpoint, performs real side effects, reaches BeforeRootWrite,
strictly inserts and validates actual root readback, reaches BeforePublish and
attempts commit once. First-use DDL and rows roll back together. MySQL retains
its separate pre-transaction DDL boundary; SQLite retains its existing native
transaction behavior.

Guarded versioned and conditional roots use native FOR UPDATE. The established
observed-stale-before-equality and ordinary WriteIfChanged no-op ordering remain
authoritative. Prospective side schema is prepared only for an admitted change.
Root, side and successor writes share the transaction and return real stored
records.

The common SQL WriteIfChanged comparison boundary reconstructs comparison-only
Records from the original ordered Fields, omitting exact Single(Update.SetNull)
as absent logical attributes. Only Single(String) values pass
through the existing SQL column decoder: admitted JSON object/array text
corresponds to its persisted Record/Vector read carrier. Every non-String Field
retains its original value, key and position. Ordinary, scalar-looking and
malformed JSON text retains the exact original String, including whitespace.
The unchanged generic business-state comparison receives those temporary
Records and keeps its existing revision/exclusion policy. This is no global
JSON meaning, codec or security-policy change. Desired writes, PostgreSQL's
key-only effective mapping, clear markers, revisions and schema retain their
owners. SQL application recovers exact original Patch/Replace clear Fields in
a write-only Record, preserving other planned Fields and next revision, for
existing preparation/update. Actual required readback remains authoritative.
No generic merge or side-intent policy changes. Repeated clear now corresponds
to absent readback and yields current WriteIfChanged NoOp with no side work.
ObservedRequired stale admission still precedes equality, ordinary Optimistic
ordering remains intact, and NoOp returns the original authoritative existing
Record without publishing a side candidate. Real permission changes remain
changes and advance the root revision with their prescribed side Save.

Native direct/CAS and deletion statements use a CASE-protected
numeric revision expression for both advancement and expected/range guards.
Malformed text is never cast outside the CASE. Missing, invalid and exhausted
revisions retain original structured diagnostics rather than being reset.

EqualTo exclusion parameters resolve the actual guard column through the same
field mapping and relation OID on the transaction connection. pg_attribute,
pg_type and pg_namespace admit only pg_catalog base scalar text, varchar,
bpchar, int2/int4/int8, float4/float8, numeric and bool. The parameter is cast to
that separately quoted native type without a typmod or length; its existing
encoded value is still bound with setObject. The column is not converted to
TEXT. Missing, ambiguous or unsupported type evidence returns the existing
structured schema failure before row effects. Native update and delete share
this parameter decision while retaining guard order, NULL/Present behavior,
existing-record admission, revisions, locks and acknowledgement boundaries.

Physical SQL type and observed-delete exact-value carrier admission are
separate contracts. DOUBLE PRECISION deletion exclusion scenarios use the
existing exact BigDecimal("0.0") carrier; direct/CAS retain raw Double(0.0).
The closed DataStoreConditionalValue algebra continues to reject raw Float
and Double for observed deletion before provider effects. Native SQL casts
and unchanged setObject encoding do not expand that algebra. Rejection
evidence must retain its structured exact-value policy and complete native
schema/root/body/revision state, while admitted excluded-state cases require
the existing entityNotFound taxonomy.

## Acknowledgement and cleanup

The existing create, versioned-mutation and conditional-transition wrappers
retain protected hook names and record transaction start, publication start
and known transaction completion. Known body failures, throws and missing
precommit outcomes normalize and attempt rollback. Only actual successful
commit or rollback establishes transaction completion. Explicit commit rejection
retains its taxonomy after verified rollback.

After publication starts, escaped or missing acknowledgement is transaction
indeterminate, including when actual commit already published the pair. It is
never provider rejection, success or replay. Failed rollback also remains
indeterminate. An unfinished borrowed connection skips auto-commit restoration,
receives best-effort abort with a synchronous executor, and closes. This
discards only that connection. Completed transactions restore the original
auto-commit setting and close normally. Datastore, SystemNode and other
connections retain their owners.

## Evidence and limits

The [native fixture](../../src/test/scala/org/goldenport/cncf/datastore/PostgresqlPersistenceFixture.scala)
uses actual independent pools/readers, JDBC fault proxies and real canonical
fixtures. Native baselines are populated before the scenario with actual
server complements and nullable carriers, retaining full snapshot comparison.
Canonical deletion scenarios preserve the existing presence-based deletion
policy while preparing their other required storage fields.

The [specification's executable correspondence](../spec/postgresql-phase-96-persistence.md#authored-executable-correspondence)
maps the source to native, canonical, conditional, resource, property and
all-backend acknowledgement suites. They are authored obligations, not results.
ContentBody payload correspondence additionally authors eight forced
inline/overflow whitespace cases, two total bounded actual-memory
preparation/hydration laws with meaningful companion-owned shrinking, and the
twelve input/preservation-flag planning cases. Each payload law requires at
least 100 successes and zero discards. The merge specification retains all
four deterministic cases and both shrinking laws while checking the four
canonical security Fields explicitly. Parent validation and independent
observation remain required for this authored evidence.
The existing all-backend acknowledgement specification additionally authors
SQL carrier comparison on actual PostgreSQL, SQLite and MySQL with plain
SqlDataStore writers and independent durable readers. Fixed Patch/Replace
cases and one total bounded law per backend use nested permission/attribute
Records, raw Long Vectors and independently written JSON-string candidates.
Each law requires at least 100 successes and zero discards with meaningful
companion shrinking and sequential INITIAL1 reseeding on shared bounded
rows/pools. Equivalent state requires complete authoritative NoOp Fields,
revision and eight-target preservation; expected revision two against actual
one requires Stale before equality. Flipping only owner write permission
requires revision two and exact complete root/side correspondence, with every
other target unchanged; repeating that changed state again requires NoOp.
Five raw-text cases per backend cover significant whitespace, malformed
objects/arrays, boolean-looking text and numeric-looking text, requiring exact
repeat preservation and genuine changed-text publication. These authored
provider laws do not complete registered Entity/Aggregate/Display acceptance.
Original registered Entity AND Aggregate, ordinary Display routes and actual
contextual committed reload remain original Slice C work. All four P96-07
criteria, P96-08–P96-11, independent Step acceptance, the full Phase review,
final validation/release and aggregate handoff remain pending.
