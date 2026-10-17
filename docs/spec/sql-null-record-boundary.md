# SQL NULL and Record boundary

Document role: normative PHASE-96 / P96-07-SQLNULL contract. The default SQL
readback attribute-omission direction is settled. Source authoring supplies no
validation, review, Step or Phase acceptance. All four P96-07 criteria remain
OPEN. The [paired design](../design/sql-null-record-boundary.md) owns the
implementation responsibility decisions.

## Read state and row existence

`SqlDataStore._record_from_result_set` owns the SQL read boundary on PostgreSQL,
SQLite and MySQL. It normalizes the complete metadata name/value vector using
the existing configuration and managed-revision exception. PostgreSQL checks
all decoded keys for ambiguity before omitting NULL values, including aliases
whose values are both SQL NULL. It retains stable id/revision-first ordering;
other backends retain metadata order.

The decoder returns metadata ambiguity as a typed `Consequence.Failure`.
Before query column admission or SQL field resolution, PostgreSQL search admits
the complete catalog and directly propagates its typed `configurationInvalid`
Failure. An empty query or an order/projection field cannot bypass this admission
before JDBC traversal.
Load and search propagate its original `configurationInvalid` classification
without converting it to an exception-derived failure. Failed load is distinct
from a missing row. Search stops at the first failed row and returns that
Failure, never a successful partial SearchResult; its existing ResultSet and
statement cleanup applies on success, typed failure and exceptions.

Only JDBC SQL NULL values are omitted. Every non-NULL decoded Field remains,
including empty String, numeric zero, the existing numeric Boolean carrier,
empty Record and empty Vector. Retained Fields construct the Record directly;
an empty-value-removing Record factory is not this boundary. Native columns,
NULL cells, selected and unrelated rows and body state remain present and
unchanged. `Option[Record]` denotes row existence: a missing row is None; a
projection of only NULL business columns still returns the existing row with
the mandatory id projection. IsNull predicates continue to address physical
SQL NULL. Serialized readback has no key for the omitted attribute, rather
than a JSON null key. Existing nested JSON null decoding is unchanged.

## Write command and equality

An omitted update attribute makes no assignment. Exact
`Field.Value.Single(Update.SetNull)` commands explicitly assign SQL NULL.
SQL-local versioned Patch/Replace application recovers these exact Fields from
the original admitted command into a write-only Record while retaining all
other planned root Fields and the next revision. Preparation and update use
that write Record; the actual required SQL readback remains authoritative.
Generic merge, desired-record construction, side intents, transactions,
checkpoints and acknowledgement policy retain their existing owners.

Comparison-only SQL Records omit exact SetNull Fields and retain the existing
String-carrier decoder. Clearing non-NULL state is Applied; repeating a clear
against absent readback at the current revision is WriteIfChanged NoOp with
the complete authoritative Record, unchanged revision and no side publication.
ObservedRequired stale admission precedes equality and preserves complete
state. Native direct/CAS operations retain their existing always-write revision
behavior. Some(SetNull) and raw-null write admission are not expanded.

## Canonical consumers and retained contracts

Strict create, generic load/search and native direct/CAS/versioned/conditional
readback share this decoder. Entity raw outcomes retain the exact provider
Record reference and actual branch/revision, including every ordered non-NULL
Field, body pointer, audit and security value. Hydration stays distinct from
raw capture. Physical guard columns remain present and NULL while their
logical fields are absent. SQL-modeled merge/save fixtures use this absence
and retain non-NULL empty values. Supplied invalid boundary values and pure
legacy ContentBody raw-null inputs retain their existing contracts.

PG1–PG12, VP1–VP5, VO1–VO5, identity, security, body, ACK and UnitOfWork
obligations remain authoritative. Registered Entity/Aggregate/Display and
contextual committed response reload remain required P07 work. No public
signature, core type, nullable Option model or dedicated SQL NULL type changes.

## Executable correspondence

- [SQL NULL Record read/write boundary](../../src/test/scala/org/goldenport/cncf/datastore/SqlMutationAcknowledgementSafetySpec.scala): real PostgreSQL/SQLite/MySQL, both normalization conventions, complete ordered logical and independent physical state, generic/native clear and omission, strict create, repeated NoOp/stale and one sequential total bounded 100-success/zero-discard law per backend.
- [Native PostgreSQL persistence](../../src/test/scala/org/goldenport/cncf/datastore/PostgresqlNativePersistenceAcceptanceSpec.scala): PG-E5 absent audit readback with physical NULL; noncolliding Foo/foo readback under both conventions with one/both NULL and unrelated non-NULL rows; actual foo_bar/fooBar decoded collision rejection before omission across neither/left-only/right-only/both NULL masks in normalized load and search, preserving configurationInvalid without partial search success, with exact-reader complete ordered raw Fields for both rows in id order and complete physical preservation. Strict-create Foo/foo write-alias rejection remains required.
- [Producer-port fixture](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationPortFixture.scala): absent logical guard and complete present physical NULL guard on native SQL schemas.
- [Versioned Record merge/save](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedRecordMergeSpec.scala): exact ordered domain/security correspondence, explicit SQL-null attribute absence and retained non-NULL empties in fixed and generated cases.

Native positive gates, existing property laws and full-state oracles remain
required. Authoring does not establish executed success.
