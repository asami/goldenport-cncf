# DataStore logical-type mapping: independent Phase allocation

Date: 2026-10-03
Disposition: allocated to [Phase 100](../../../phase/phase-100.md), planned, not started.
Closure ledger: [Phase 100 Checklist](../../../phase/phase-100-checklist.md).

## User direction

The application declares whether an attribute is String, a semi-structured
Record or an array. The storage mapping then determines whether SQL text stays
String or is decoded into the declared logical value. Because the missing
mapping affects existing data and recovery, the user requested one dedicated
Phase rather than another local decoding workaround.

## Observed implementation

Read-only inspection of the current working tree found:

- `DataStore.scala` accepts and returns Record values, but its shown public
  CRUD methods do not accept field-level logical/storage mappings.
- `SqlDataStoreRecordCodec.scala` serializes Record/RecordPresentable and
  Iterable values as JSON text. On read it examines trimmed String delimiters
  and attempts Record/array parsing, otherwise returning the original String.
- The same codec has collection/field-specific native-text preservation for
  Durable Job IDs and canonical JSON, including quota collections. This
  protects selected callers but is not a general declared-type mapping.
- `EntityStoreRecordProjection.scala` exposes `EntityStoreValueKind` with only
  `ScalarString`. Its projection can turn a retrieved Record back into JSON
  text, which cannot prove preservation of the original String representation.
- `SqlDataStoreCanonicalTextSpec` covers these collection-specific exceptions
  and retains JSON inference expectations for other identities. Those existing
  compatibility expectations need an explicit transition, not a global parser
  switch attributed to a bug fix.

The SQL extraction/codec and several planning documents are already locally
modified or untracked by other ongoing work. These observations describe the
working tree, not an independently accepted or released baseline. This task
changes planning documents only and preserves those implementations and ledgers.

## Planning conclusion

Phase 100 includes impact/data inventory, canonical type and mapping contracts,
provider implementation, consumer transition, existing-data recovery rehearsal
and application-level proof. Its minimum logical distinctions are String,
Record and array; their final API names and declaration-producer wiring are
settled in its contract work, reusing existing datatype ownership.

Recovery must separate metadata adoption from data transformation. JSON syntax
does not establish historical application intent. Ambiguous or corrupt rows
require explicit provenance/disposition and preservation of raw evidence.
Previously lost information cannot be recreated by introducing a schema.

The initial vertical proof is workbench write/persist/restart/read; durable
Job canonical text and identity are required compatibility cases. Existing
transaction and OCC boundaries remain intact. This allocation does not start
Phase 100, change Phase 69.7 acceptance or make unrelated UI/shared-transaction
Phases prerequisites. Real data migration requires separate operational scope.

## Verification of this allocation

The original local allocation used Phase 97 after inspecting only the local
Phase inventory. On 2026-10-05, GitHub main confirmed that Phase 97 was already
AI Audit and Phase 98 was already Online Multi-Arm Experiment Routing. Phase 99
was also allocated. The user selected the then-unused Phase 100 for DataStore.
The phase index and strategy now link to the Phase 100 plan and open checklist. No Scala changes,
tests, SBT, database writes, commits or pushes were performed by this task.

## User clarification: OR-mapper ownership

The user clarified the transformation owner after allocation. For text-backed
storage, DataStore writes and reads the physical String. The OR mapper encodes
application values through JSON into that String and performs the reverse
conversion using the declared application type:

- class X: String -> JSON -> X instance;
- semi-structured data: String -> JSON -> Record, including an admitted JSON
  input at write time;
- JSON: String -> JSON, without a further Record conversion;
- String: retain the original String, including JSON-looking text.

This supersedes any wording in the initial plan that placed application-value
reconstruction inside DataStore/provider code. The plan, checklist, index and
strategy now distinguish those responsibilities and include X and JSON as
separate acceptance cases. The canonical persistence design records this
boundary; implementation remains planned, not completed.

## User decision: replace the Named record workaround

Further inspection found an existing Record declaration/generation path in
SimpleModeler: record type names map to `org.goldenport.record.Record`, while
generated runtime schema metadata uses `DataType.Named("record")`. Core has
the Record value type and code uses `io.circe.Json`, but the inspected canonical
schema datatype definitions lack dedicated semi-structured and JSON types.
Thus, the earlier observation must not be read as absence of all Record
declaration support; the missing contract is its formal datatype identity and
connection to storage mapping.

The user explicitly classified `DataType.Named("record")` as an ad hoc
workaround that must ultimately be removed. Phase 100 therefore requires formal,
distinct semi-structured and JSON datatype definitions, propagated from model
declarations through generated Scala/schema metadata into the existing
EntityStore/EntityPersistent mapping. Replacing the spelling with another
name-only workaround does not satisfy this decision. Generated outputs must
be regenerated from corrected producers, not patched by hand.

The plan, checklist and canonical persistence design now record this decision.
At that decision point, concrete API names and the selected cross-repository
implementation scope remained to be frozen; no datatype implementation or
migration was claimed.

## User decision: canonical datatype names and representations

The user selected `XJson` and `XSemiStructuredData` as the formal datatype
names. `XSemiStructure` was a naming candidate, not the adopted API name.

- `XJson`: JSON data model, represented internally by `io.circe.Json` and
  exchanged as JSON.
- `XSemiStructuredData`: format-independent semi-structured data, represented
  internally by Record and exchanged as JSON, YAML or HOCON.

The external format is selected explicitly and is independent of the DB
storage representation. The plan, checklist and persistence design carry these
names and the external-format requirement. Model spellings, detailed
normalization/conversion contracts and cross-repository implementation scope
remain DSM-100-02 work. Implementation and migration have not started here.

## User clarification: explicit conversion versus automatic OR mapping

Applications may intentionally use `toRecord`/`fromRecord` when accessing
DataStore. This remains an application-owned conversion. The OR mapper must
select automatic encoding/decoding from declared logical types such as
`XJson` and `XSemiStructuredData` and their storage mappings, not from runtime
values or the mere availability of conversion methods. A selected codec may
use those methods internally without making them an inference mechanism.

The outer Record row carrier does not declare the logical types of its fields.
The plan, checklist and persistence design now distinguish explicit caller
conversions from automatic typed reconstruction. This also clarifies earlier
wording that required all direct consumers needing application values to use
the OR mapper: that requirement applies to automatic reconstruction, not to
deliberate application-owned conversions.

## User direction: establish the route in the development plan

The user requested the EntityStore -> OR mapper -> DataStore route and then
explicitly limited the current task to development planning. Read-only source
inspection had begun; no implementation or test execution was performed in
response to that route request.

The target route and reverse read path are now explicit in the plan and
persistence design. The checklist covers all EntityStore entry points,
including partial patches, queries/projections and conditional/versioned
readback, rather than accepting only a save/load codec demonstration. Existing
EntityPersistent store/admission hooks must fit the typed stages without
double conversion, while transaction/provider identity and revision/atomicity
guarantees remain intact.

The implementation sequence is inventory -> formal contracts and datatype
definitions -> producer/runtime mapping integration -> EntityStore SQL restart
proof -> consumer transition and recovery rehearsal. All work groups remain
OPEN and Phase 100 remains planned. A subsequent development instruction is
required to begin implementation; no current product changes are attributed to
this planning update.

## User goal: absorb and eliminate the Named record path

The user confirmed that absorbing the processing behind
`DataType.Named("record")` into the formal route and eliminating that form is
itself a Phase goal. The plan now states this explicitly, beyond adding new
datatype names. Inventory and preserve supported behavior in the appropriate
`XSemiStructuredData` declaration, normalization, generation and OR-mapping
stages, then remove active legacy producer/runtime dependencies.

Closure requires regenerated artifacts and regression evidence, with no active
dependency on the legacy form in the selected scope. Merely locating a
compatibility reader or assigning it a future removal date is insufficient for
this goal; it must be retired from the accepted route. Historical documentation
and deliberate rejection fixtures are evidence, not active support. These are
planning updates only; Phase 100 remains planned and implementation is unstarted.

## Renumbering — 2026-10-05

The user directed that GitHub Phase 97 remain authoritative and selected
Phase 100 after the GitHub Phase 98 collision was identified. The local
DataStore plan/checklist and DSM identifiers move to Phase 100; related
index, strategy and persistence-design references follow that allocation.
Local Phase 97 and its checklist are restored from GitHub main.

This changes document identity and navigation only. DataStore requirements,
all OPEN checklist items and the planning-only implementation boundary remain
unchanged. No implementation, SBT, live migration, commit or push is performed.
