# SQL NULL Record responsibility

Document role: settled PHASE-96 / P96-07-SQLNULL design. The
[paired specification](../spec/sql-null-record-boundary.md) owns behavior.
Validation, independent review and Step acceptance remain with the parent;
all four P96-07 criteria remain OPEN.

## One SQL-owned correspondence boundary

The existing `SqlDataStore` decoder is the common owner of generic and native
readback. SQL NULL denotes absent logical attribute. Row existence remains
the existing Option of Record. Full metadata is decoded before PostgreSQL
ambiguity admission, so a NULL alias cannot conceal an invalid decoded key.
After admission only SQL NULL pairs are removed, and retained Fields directly
construct Record. PostgreSQL retains id/revision-first stable ordering;
SQLite/MySQL retain metadata order. Empty decoded structured values must not
pass through the broader empty-value-removing Record factory.

The private decoder returns `Consequence[Record]`, so ambiguity remains the
existing structured `configurationInvalid` Failure. Load and search use
`Consequence.run` around their typed JDBC bodies to retain returned failures
and the existing nonfatal exception boundary. PostgreSQL search first admits
the complete catalog through typed composition, before query column admission
and SQL field resolution. This returns catalog ambiguity directly before an
order/projection callback can take a failed field resolution or JDBC traversal
can begin; decoder failure propagation remains required for traversed rows.
Load maps successful decoding to
Some(record), reserving successful None for an actually missing row. Search
keeps its Record builder and a local `Consequence[Unit]` status, stops cursor
traversal on the first decoder Failure, and builds SearchResult only on success.
The existing nested finally blocks close ResultSet and statement on every
exit; no exception adapter or shared failure-policy change is needed.

The choice changes logical readback, never physical table/column/cell state.
The ordinary Record encoder consequently omits the absent key without a new
wire codec. Under Phase100 physical activation dated 2026-10-18, nested JSON
null and empty objects/arrays remain inside exact physical text; explicit
caller or declared mapper codecs own their logical reconstruction. This
supersedes only earlier inferred Record/Vector correspondence, preserving
SQL NULL omission and physical schema/cell ownership.

## Command markers stay in the SQL write input

Generic versioned Patch merge removes exact SetNull Fields from its desired
logical state. SQL still needs that command to assign physical NULL. The
SQL apply owner therefore extracts exact markers from the admitted original
Patch/Replace command, replaces same-key Fields in the planned root with those
markers and retains the planned next revision and all other root Fields.
Only preparation and root update receive that write-only Record. The outcome
uses the real required readback after the existing checkpoints and ACK path.

The comparison owner constructs temporary Records, removes exact SetNull
Fields and retains exact physical Strings under Phase100 R2/R8, including
valid JSON and lexical whitespace. It performs no inferred JSON equivalence.
An absent readback attribute
equals a repeated explicit clear. This permits current WriteIfChanged NoOp
while stale admission continues to precede equality. Direct/CAS keep their
existing always-write behavior. No generic merge, transaction, side intent,
revision, body, identity or security responsibility moves.

## Consumer evidence and deferred proposals

Canonical raw outcomes capture the exact provider Record reference before
projection. Their complete non-NULL ordered Fields and full physical/body/
unrelated-state oracles stay required. Native port guards are logically absent
and physically present SQL NULL. SQL-modeled merge/save fixtures reflect that
read state, retaining non-NULL empty values. Invalid input guards and pure
legacy ContentBody raw-null planning keep their existing semantics.

Broad nullable-attribute Option and a dedicated SQL NULL type are deferred
proposals. Core types, public signatures, encoders and other repository owners
are unchanged. The [executable correspondence](../spec/sql-null-record-boundary.md#executable-correspondence)
defines the evidence; registered Entity/Aggregate/Display and committed reload
remain later original P07 obligations. The [journal](../journal/2026/10/2026-10-15-phase-96-sql-null-record-boundary.md)
records the direction's history without defining behavior.
