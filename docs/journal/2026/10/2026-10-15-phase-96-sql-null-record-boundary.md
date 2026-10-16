# Phase 96 SQL NULL Record boundary

On 2026-10-15, the human direction selected default omission of SQL NULL
attributes from Record readback:

> 専用のSQL NULL型を用意するか、NULLの場合はRecordに属性を入れない。のどちらかだね。
> デフォルトは後者がよいと思う。SQL NULL型はニーズがあってから。

The additional nullable Option proposal remained an idea:

> Optionを返すてもあるか。でも、これをする場合は、nullableである属性は全部こうしないといけないので、現在のところは案レベル。

The preceding two compilation errors concerned logical JVM-null expectations.
That exposed a modeling smell: PostgreSQL readback constructed Single(null),
while SQLite's Record factory already omitted SQL NULL. The factory also
removed non-NULL empty Record values, so it could not define the intended
shared SQL boundary. The settled choice omits only SQL NULL and retains
ordered non-NULL empty scalar/structured Fields and full physical state.

Source inspection also identified a write correspondence issue. Versioned
Patch merge removes SetNull from its desired logical state; updating only
remaining Fields then loses the physical clear assignment. SQL application
now retains exact original clear markers in a write-only Record, and temporary
comparison treats those markers as absent logical attributes. Actual SQL
readback continues to own outcomes. First clear, current repeated NoOp,
stale-before-equality, omitted updates and native direct/CAS behavior keep
their original revision, transaction, side-effect and acknowledgement owners.

The [normative specification](../../../spec/sql-null-record-boundary.md) and
[settled design](../../../design/sql-null-record-boundary.md) record the chosen
contract. A dedicated SQL NULL type is deferred until a concrete need; broad
nullable-attribute Option representation remains a proposal. Invalid boundary
nulls and pure legacy ContentBody inputs retain their existing contracts.

Code and executable specifications are authored obligations pending parent
focused validation, independent review and Step acceptance. This journal
establishes no passing result or acceptance. All four P96-07 criteria and the
original later integration, Phase and release gates remain open.

The readback collision fixture incorrectly reused Foo/foo from write-normalization
admission. The existing read transform preserves separator-free case, so both
readers retain those distinct keys while omitting SQL NULL attributes. Actual
snake/camel aliases foo_bar/fooBar both decode to fooBar; their four finite
NULL masks now specify ambiguity rejection before omission with exact-reader
ordered raw Fields and complete physical preservation. Strict-create Foo/foo
write rejection and product naming/readback remain unchanged. Focused
validation and independent review are pending.

The throwing `.take` in decoded-key ambiguity admission lost the intended
structured `configurationInvalid` classification: the surrounding plain
Consequence rebuilt the thrown failure from its exception. The private decoder
now returns its typed Failure directly, and the existing load/search owners
carry it through typed JDBC bodies. Search stops at the first failed decode
without returning partial success, while the existing finally cleanup remains.
The four snake/camel NULL masks now specify actual normalized and exact search
alongside both loads, retaining the original classification expectation and
complete ordered logical and independent physical state. These authored
obligations remain pending parent validation, independent review and acceptance.

The subsequent representative004 validation still failed all four snake/camel
NULL-mask scenarios. Decoder propagation alone left an earlier ordered-search
path: Query.Empty bypassed query-column admission, and SQL construction resolved
the order field through `_pg_field(...).take` before a ResultSet existed. The
complete PostgreSQL catalog now enters typed admission before query-column
admission and SQL construction, preserving its `configurationInvalid` Failure
without an exception adapter. Diagnostic labels distinguish selected load,
unrelated load and ordered search while retaining both the Failure-type and exact
status expectations. The existing exact-reader ordered Fields and independent
physical-state oracles remain. This source repair establishes no passing result
or acceptance; parent validation, independent review and all original gates
remain pending.

CB-P96-SQLNULL-VP-ORDER-OBS-001 identified an Applied-state oracle gap: the
inherited retained-Field comparison sorted both vectors, so a permutation such
as owner_id/group_id could escape the independently prescribed order obligation.
The producer-port fixture now adds a complete ordered Record expectation from
its independently prepared baseline and explicit changed values. Memory typed
save, guarded patch and native direct/CAS patch each prescribe their full
24-field order; SQL retains the prepared baseline order. The existing inherited
checks, C2A fixture, raw provider reference, physical state and prior scopes
remain intact. This authored predicate establishes no executed success or
acceptance; parent focused validation, independent review and the original
Step and Phase gates remain pending.
