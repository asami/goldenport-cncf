# DataStore logical type mapping

HISTORICAL Phase 100 prerequisite validation snapshot — 2026-10-05:
Earlier scoped validation/protected-review-pending statements retained in this
document describe their original prerequisite subjects. They are not claims
that those pending reviews subsequently passed; current Phase 115 adoption is
recorded separately below. Normative requirements remain in force.

Status: Historical Phase 100 pure value-codec slice accepted independently: 26/26 focused tests
and `tkw-cncf-codec-focused-closure-20261005@1` PASS.
Field/row ABI is frozen in [EntityStore field and row mapping](entity-store-row-mapping.md).
Its prior selected row/codec/projection focused validation passed 42/42 tests;
`tkw-cncf-row-mapping-step-review-20261005@1` PASS is recorded independently.
The producer hook/stage ABI is authored and frozen in
[EntityStore persistence stages](entity-store-persistence-stages.md), with new
selected validation passed 61/61 tests across five suites with SBT/wrapper exit 0
and serial lock released; complete eleven-path independent protected review
`tkw-cncf-persistence-stage-step-review-20261005@1` is PASS. The additive
[native scalar binding](entity-store-native-scalar-mapping.md) is authored and
frozen, with current focused validation passed 85/85 tests across eight suites
and complete eighteen-path independent protected review pending. Generator/provider
integration and independent project Phase acceptance remain pending.
Owner: Phase 100 DSM-100-02.
Rules: R1 through R12.

Phase 115 adoption is CLOSED with all five RCP obligations DONE, on the
[final Phase acceptance](../phase/phase-115.md#final-phase-acceptance--2026-10-18)
and [checklist](../phase/phase-115-checklist.md). Both connected Steps were
independently reviewed and committed locally; the sole original Phase full
review, retained Modeler repair1 focused closure and combined repair2–6 focused
closure passed. The selected CNCF full gate passed 6716 tests in 824 suites,
zero failed/aborted, with 5 canceled, 1 ignored and 46 pending. The Phase ledger
records all four selected full gates separately and retains earlier failures.
Closure takes effect through the local release commit containing
`phase115-final-closure-20261018-01@1`.

This specifies the behavior to implement at the existing EntityPersistent /
EntityStore mapping boundary. It does not claim that the current SQL provider
or generated codecs implement these rules. Canonical datatype and generator
source owners remain subject to the recorded repository-scope decision.
Architectural context, non-normative:
[DataStore and aggregate persistence](../design/datastore-and-aggregate-persistence-model.md).
Source evidence:
[Phase 100 inventory](../journal/2026/10/2026-10-05-phase100-source-inventory.md).

## R1: Declaration selects conversion

Mapping selection uses the authoritative field declaration and physical storage
mapping. Each generated field carries its logical datatype, logical and storage
names, multiplicity, and selected storage codec. Arrays also carry their element
declaration; application classes carry their declared class codec identity.
External format selection is separate from storage codec selection.

The additive native scalar declaration explicitly captures the authoritative
Core datatype, Text/Boolean/Number physical representation and ClassTag/Encoder/
Decoder identity. It retains a canonical `Named("name")` or primitive datatype
instead of substituting the runtime class name. Its exact ABI and value matrix
are frozen in [EntityStore native scalar mapping](entity-store-native-scalar-mapping.md).
This storage selection adds no Core datatype and changes no original factory
signature or successful pure codec contract.

Payload shape, SQL column type, runtime Record/Json instances, RecordPresentable,
or the presence of toRecord/fromRecord methods never supplies missing metadata.
Missing or inconsistent metadata fails the typed operation before mutation.

## R2: Physical text and exact String

SQL TEXT reads return the stored String. They do not trim, parse, compact,
reformat, or reconstruct application values. A declared String mapping accepts
and returns String with exact character content, including whitespace, Unicode,
JSON object/array syntax, empty text and malformed JSON-looking text.

SQL NULL is distinct from the String `"null"`; native binary fields retain their
own physical representation. In-memory mapped tests consume the same encoded
physical representation as SQL, rather than bypassing the mapping boundary.

## R3: XJson

`org.goldenport.schema.XJson` is the canonical JSON logical datatype. Its
application value is io.circe.Json. All JSON shapes, including scalar and JSON
null, remain JSON values. The text storage codec writes JSON text and reads
String -> parsed Json. Successful JSON parsing never implies Record conversion.

JSON semantic equality is the roundtrip law; lexical JSON formatting is not
part of this logical value's contract. Text needing lexical preservation is
declared String instead. JSON number values must not be reduced to Double.

## R4: XSemiStructuredData

`org.goldenport.schema.XSemiStructuredData` is the canonical semi-structured
logical datatype. Its normalized application value is Record. Accepted JSON
input has an object at the root. Nested objects, arrays, strings, booleans, exact decimal numbers and empty
objects/arrays are retained. Null-valued object attributes are omitted recursively.
A null sequence element fails conversion rather than changing element positions.
Top-level arrays/scalars/null fail with a wrong-shape outcome; they do not
normalize to an empty Record.

An empty root object normalizes to Record.empty and persists as a present
`{}` value, not absent data. Core Phase 3 owns Record construction and normalization, including
`Record.createC` / `Record.fromFieldsC`. Empty objects and arrays stay present;
untyped arrays are Vector. Declared NonEmptyVector is reconstructed by the
existing Core ValueReader and rejects empty input. Constructed malformed Record
or Field fixtures are no longer valid semantic inputs; do not weaken Core
constructors or introduce a CNCF normalizer.

## R5: Explicit external formats

XSemiStructuredData external decoding selects JSON, YAML or HOCON explicitly.
All normalize into the R4 value space. The physical text mapping independently
uses JSON serialization of the normalized value, so a model's YAML input format
does not make its SQL storage value YAML.

YAML requires a string-keyed root mapping. HOCON requires an object root and
retains object nesting. Inputs with unsupported tags/keys, nonfinite numbers,
cyclic values or unresolved substitutions fail explicitly rather than stringify
or omit those values. Format selection does not inspect a filename or content
to choose another decoder. The storage codec must not resolve external files,
environment values or includes while reconstructing a stored JSON value.

Use the adopted Core XSemiStructuredData APIs for these explicit formats.
Conversion follows R4; external loader behavior must not redefine the value space
or resolve external resources while reading stored JSON.

## R6: Class and array mappings

An application class mapping has an explicitly bound application codec:
X -> Json -> text on write and text -> Json -> X on read. Missing codec or a
class decode failure is distinct from malformed JSON. Method availability
does not automatically bind a codec.

An explicitly selected native scalar class/primitive binding instead bridges
X -> selected scalar Json -> native String/Boolean/BigDecimal, and reverses that
bridge on read. Text uses Json string content unchanged; Boolean selects only
Boolean or canonical physical zero/one; Number preserves exact big numeric
meaning and parses physical numeric text only under that explicit selection.
Its array child uses the same selected scalar Json directly inside the existing
JSON array text, without parsing/stringifying each Text element twice.

A declared array uses JSON array storage with element conversions selected
from its declared element type. Element order and empty arrays are retained.
An object or scalar in place of the declared root array fails. Present Json.Null
elements are accepted only where the selected child declaration permits JSON
null. Native-null sequence elements fail at Core Record construction; they are
not nullable semantic elements. Nested arrays retain element declarations at
every level. Readback is Vector; typed entity admission uses Core ValueReader
for explicitly declared NonEmptyVector and rejects empty input.

## R7: Presence, null and patches

An absent patch field means no change. An explicit native Update.SetNull means
SQL NULL and follows the existing nullability and managed-field policies.
Required fields reject SQL NULL or absence during entity reconstruction.
Provider SQL NULL becomes Option absence immediately at JDBC ingress. Optional
semantic fields are absent; required absence fails missing-required-field.

Json.Null is a present XJson value stored as JSON text `null`, not SQL NULL.
An R4 null-valued member is omitted inside its present object and does not clear
the outer field. Empty Record and array values remain present. Noop and optional
None omit the patch field. Bare-null patch input must fail explicit-clear-required
at foreign ingress before semantic Record construction can omit it; complete
foreign-caller migration is accepted in RCP-CONSUMERS. SetValue(null)
is rejected at the raw Update adapter before Update.toChangesRecord. The mapping does not
reinterpret omission as Update.SetNull or bypass managed-field protection.

## R8: Numeric fidelity and invalid values

Structured values retain exact integral/decimal meaning without an intermediate
Double conversion. A value outside the admitted exact representation produces
an explicit failure. The storage codec must reject unsupported application
objects rather than use Presentable.print or toString as a success fallback.
String fields themselves do not undergo numeric or JSON validation.

Native Number encode extracts JsonNumber.toBigDecimal without a Double
intermediate. Exact integral and Scala/Java big numeric physical inputs remain
exact. Finite physical Float/Double uses explicit Circe numeric constructors;
precision already lost in such inputs cannot be recovered. Nonfinite values and
JSON numbers outside exact BigDecimal representation fail
`unsupported-external-value`. The text backend transports Scala BigInt and
BigDecimal as exact decimal text, never via Double. The declared Number binding
parses that physical numeric text; a String binding still preserves it verbatim.
This physical transport is independent of JSON/application codec selection.
The SQL transport admits native scalar/text/binary values, the explicit Core
StringEncodable storage-scalar protocol, and existing framework SmEnum/native
time/URI/UUID/charset scalars. It does not use Record, RecordPresentable,
Iterable, arbitrary Presentable or toString as a structured-writer fallback.
Applications explicitly encode structured fields at their declared mapping or
owned physical adapter before this boundary; unsupported values fail with the
structured reason unsupported-physical-value. Native SQL NULL stays NULL under
both dialect policies.
Provider acceptance requires actual EntityStore/provider roundtrip coverage.

## R9: One typed stage per boundary

Write: entity/store hook -> declared logical-field conversion -> physical row
-> DataStore. Read: DataStore physical row -> declared logical-field conversion
-> entity admission/decode hook -> application entity.

The implementation must preserve the existing managed-field, ContentBody,
identity, revision, tenant, authorization and transaction stages. Its producer
ABI must distinguish hooks that consume physical values from those consuming
logical values; a generated or custom hook must not encode/decode the same field
twice. Each create/save/patch/query/projection/conditional/versioned readback
path uses the same field mapping. Framework-only physical rows are explicitly
classified in the consumer inventory.

## R10: Structured failures

Failures identify collection, logical/storage field and stage. Distinguish
missing mapping, incompatible mapping, wrong physical value, malformed stored
JSON, wrong structured shape, unsupported external value, application codec
failure and schema-version incompatibility. Use existing Consequence and
Descriptor facets. Do not return an empty value, fallback String, guessed type
or successful partially reconstructed entity. Diagnostics need no raw payload
copy, content hash, comparison ledger or independent receipt mechanism.

## R11: Consumer transition and recovery

Before changing provider defaults, inventory affected direct and EntityStore
consumers. Record explicit per-collection mappings and source/generator versions.
Canonical permission text and opaque IDs remain String; ContentBody overflow
rows retain their explicit framework policy. Remove active Named("record")
producer output and heuristic reader branches only with the corresponding
declared consumer coverage.

Recovery uses trusted declarations and retained originals. Valid JSON syntax
alone cannot classify historical String versus structured data. Metadata-only
adoption does not rewrite unchanged bytes. Rewritten formats need explicit
migration and isolated backup/dry-run/interruption/resume/restoration evidence.
Lost String formatting cannot be recovered from a reserialized Record without
the original input. This contract authorizes no live database mutation.

The source-only Workbench decision, exact diagnosis classifications and
metadata-only cutover/recovery procedure are specified in
[declared mapping recovery](datastore-logical-type-recovery.md). In this scope
there is no existing production database or physical format rewrite to perform.

## R12: Producer fixture and acceptance

Cozy Phase 78 hands actual generated source/schema/storage metadata to CNCF
Phase 100. The representative model contains String originalProposal, XJson
payload, XSemiStructuredData evidence, an array and an application-class field.
The producer API must compile against the selected Core/CNCF dependency versions.
The pure value-codec signatures below are frozen. The exact field/row signatures
are frozen in [EntityStore field and row mapping](entity-store-row-mapping.md).
Producer logical hooks and strict mapped C-method signatures are frozen in
[EntityStore persistence stages](entity-store-persistence-stages.md). Generated
native scalar binding emission is additionally frozen in
[EntityStore native scalar mapping](entity-store-native-scalar-mapping.md).
Emission and consumer activation of those signatures remain required integration
work with separate acceptance.

Executable coverage must separately prove physical text read, logical codec
conversion, generated EntityStore write/read and process-restart readback.
Cover R2 JSON-looking Strings; R3 scalar/object/array/null; R4 empty/nested/null
values; R5 three selected formats and unsupported values; R6 declared array and
class reconstruction; R7 patch omission/null; R8 exact numbers; R9 all inventoried
mutation/query/readback paths and existing transaction/OCC behavior; R10 failures;
R11 selected consumers/recovery; and both Workbench proposal fixtures.

No checklist item closes solely because this proposal exists. Required generated
ABI, executable specifications, actual validation, independent review and each
project's separate Phase acceptance remain pending.

## Frozen pure EntityStore value-codec ABI

The authored pure conversion stage is
`org.goldenport.cncf.entity.EntityStoreValueCodec[A]`. Its complete public
signatures are:

```scala
package org.goldenport.cncf.entity

sealed trait EntityStoreValueCodec[A] {
  def identity: String
  def encode(value: Any): org.goldenport.Consequence[String]
  def decode(value: Any): org.goldenport.Consequence[A]

  private[entity] def _validate_binding: org.goldenport.Consequence[Unit]
  private[entity] def _to_json(value: Any): org.goldenport.Consequence[io.circe.Json]
  private[entity] def _from_json(value: io.circe.Json): org.goldenport.Consequence[A]
}

object EntityStoreValueCodec {
  val string: EntityStoreValueCodec[String]
  val json: EntityStoreValueCodec[io.circe.Json]
  val semiStructuredData: EntityStoreValueCodec[org.goldenport.record.Record]

  def application[A](codecIdentity: String)(using
    scala.reflect.ClassTag[A],
    io.circe.Encoder[A],
    io.circe.Decoder[A]
  ): EntityStoreValueCodec[A]

  def array[A](element: EntityStoreValueCodec[A]): EntityStoreValueCodec[Vector[A]]
}
```

The package-visible binding validation checks every captured recursive codec,
including empty/nested-empty arrays; String succeeds and JSON-text codecs use
their existing binding validation. The package-visible JSON conversions compose declared array elements. They do
not infer a mapping or make String parsing part of the public String contract.
Codec identities are `string`, `json`, `semiStructuredData`,
`application(<codecIdentity>)` and `array(<child identity>)`. They identify the
selected conversion for diagnostics; Core logical datatypes remain XJson and
XSemiStructuredData.

String encode and decode require an actual String and preserve it exactly.
Structured decode requires a physical String and parses JSON explicitly.
XJson normalization on encode accepts Json only and retains every JSON shape,
including Json.Null. XSemiStructuredData encode uses its canonical normalizer
and `toJson`; decode uses `fromJson`, retaining Record as the result. Canonical
Record or JSON-object inputs omit null attributes recursively and preserve nested
empty values, retained member order and exact numbers; unsupported members and
null sequence elements fail. Core owns these conversions.

An application declaration captures ClassTag, Encoder and Decoder explicitly.
The encoder receives only a nonnull compatible application value, and decoding
must return a nonnull compatible value. A present Json.Null representation is
permitted when selected by the explicit application codec: it is stored as text
`null`, and the selected decoder determines whether that JSON shape is valid.
Native null application values and native-null Json references fail. A
null/blank codec identity or missing binding returns an invalid
outcome on encode/decode before captured application code is invoked. Thrown
NonFatal codec exceptions and valid JSON rejected by the decoder are application
codec failures; malformed physical JSON is a separate format failure. Method
availability, including toRecord/fromRecord/RecordPresentable, supplies no codec.

Arrays accept Scala Seq values on encode and return ordered Vector values on
decode. Every element passes through the explicitly selected child conversion;
nesting uses `array(array(element))`. Empty arrays and nested empty arrays remain
present. Objects/scalars do not become arrays, and native arrays do not supply
an inferred Scala Seq mapping. Missing element bindings fail explicitly.
Independent element failures aggregate through Consequence.zipN with their
indices; no failed element is omitted and no partial Vector succeeds.

Array null semantics follow the selected child: `array(json)` accepts present
Json.Null and reads JSON null as Json.Null. Application children use their
explicit codec's JSON-null policy and must decode to a nonnull compatible class.
String, semi-structured and nested-array children reject JSON null. Native null
logical inputs fail their selected codec. Optional field presence and outer SQL NULL remain part
of the required field-domain stage; this slice introduces no nullable wrapper.

Malformed JSON text uses Consequence.valueFormatError. Wrong physical values,
root shapes, runtime classes, element values, missing bindings and application
codec failures use Consequence.valueInvalid. Explicit Reason facets distinguish
missing-codec-binding, wrong-physical-value, malformed-stored-json,
wrong-structured-shape, wrong-logical-value and application-codec-failure at their
original failure sites. Array indexing preserves causal taxonomy and all facets,
replacing only Message with its existing sanitized index prefix and adding index
context. Diagnostics never copy raw payloads or exception messages. Collection,
logical/storage field and stage facets are authored in the linked field/row stage;
that behavior passed the selected 42/42 focused command and independently
recorded `tkw-cncf-row-mapping-step-review-20261005@1` PASS. The new producer
stages and package raw Update entry passed the selected five-suite focused
command, 61/61 tests with SBT/wrapper exit 0 and serial lock released; complete
eleven-path independent protected review
`tkw-cncf-persistence-stage-step-review-20261005@1` is PASS. New native scalar
validation passed 85/85 tests across eight suites; complete eighteen-path
independent protected review remains pending.

### Representative declared consumer values

The following shows pure conversion selection for the R12 producer fixture.
It does not declare field metadata or install an EntityStore hook.

```scala
import scala.reflect.ClassTag
import io.circe.{Decoder, Encoder, Json}
import org.goldenport.record.Record
import org.goldenport.cncf.entity.EntityStoreValueCodec

final case class ProposalSummary(label: String, score: Int)

val summaryEncoder: Encoder[ProposalSummary] =
  Encoder.forProduct2("label", "score")(value => (value.label, value.score))
val summaryDecoder: Decoder[ProposalSummary] =
  Decoder.forProduct2("label", "score")(ProposalSummary.apply)

val originalProposal: EntityStoreValueCodec[String] = EntityStoreValueCodec.string
val payload: EntityStoreValueCodec[Json] = EntityStoreValueCodec.json
val evidence: EntityStoreValueCodec[Record] = EntityStoreValueCodec.semiStructuredData
val labels: EntityStoreValueCodec[Vector[String]] =
  EntityStoreValueCodec.array(EntityStoreValueCodec.string)
val summary: EntityStoreValueCodec[ProposalSummary] =
  EntityStoreValueCodec.application[ProposalSummary]("proposal-summary-v1")(
    using summon[ClassTag[ProposalSummary]], summaryEncoder, summaryDecoder)
val summaries: EntityStoreValueCodec[Vector[ProposalSummary]] =
  EntityStoreValueCodec.array(summary)
val nestedLabels: EntityStoreValueCodec[Vector[Vector[String]]] =
  EntityStoreValueCodec.array(labels)

val stored = originalProposal.encode("  {malformed JSON-looking original} 雪  ")
val restored = stored.flatMap(originalProposal.decode)
val presentJsonNull = payload.decode("null")
val presentEmptyEvidence = evidence.encode(Record.empty)
```

### Stage boundaries and outstanding acceptance

The conversion executable specification is
[EntityStoreValueCodecSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityStoreValueCodecSpec.scala).
It exercises public factories and public encode/decode behavior, including
generated-consumer-style typed values and a property-based text roundtrip.
The existing EntityStoreRecordProjection compatibility specification remains
required in parent-selected focused validation.

This slice consumes accepted Core conversion declarations from the dedicated
goldenport-core-types checkout. Its unchanged `goldenport-core_3`
`0.4.3-SNAPSHOT` local artifact refresh completed successfully. Focused
`EntityStoreValueCodecSpec` and `EntityStoreRecordProjectionSpec` validation
passed all 26 tests with SBT/wrapper exit 0 and the serial lock released.
Independent focused closure `tkw-cncf-codec-focused-closure-20261005@1` is PASS,
recorded independently. This is accepted prerequisite evidence for the pure
slice. The touched codec diagnostics and authored field/row stage passed the
parent-selected focused command, 42/42 tests with SBT/wrapper exit 0 and the serial
lock released, and independently recorded protected review
`tkw-cncf-row-mapping-step-review-20261005@1` PASS. New producer stages passed the selected five-suite focused command, 61/61 tests
with SBT/wrapper exit 0 and serial lock released. Complete eleven-path independent
protected review `tkw-cncf-persistence-stage-step-review-20261005@1` is PASS.
Current native scalar/generation validation passed 85/85 tests across eight suites;
independent protected review and downstream acceptance remain pending.

EntityStoreRecordProjection and SqlDataStore are unchanged by these producer
slices. Existing EntityPersistent physical hooks and collection helpers remain
unchanged; new logical hooks and strict methods are frozen separately below.
Field declarations/domains and the row ABI have prior accepted 42/42 focused
tests and independent protected review. New stages passed the selected 61/61
focused tests and complete eleven-path independent protected review
`tkw-cncf-persistence-stage-step-review-20261005@1` PASS. New native scalar
validation passed 85/85 tests across eight suites; independent protected review
and consumer integration remain pending. Required next slices
still include physical provider
reads, generated metadata, consumer hook-stage wiring and all R9
mutation/query/readback paths. SQL NULL/patch rules, managed fields, ContentBody,
identity, revisions, tenant, authorization, transactions and recovery retain
their existing boundaries. Storage conversion is JSON only; this stage performs
no YAML/HOCON format inference, configuration resolution, external IO or live
database mutation.

New native scalar independent accumulator review, consumer integration,
SimpleModeler/Cozy generation, provider integration, generated
write/read/restart coverage, both Workbench fixtures and each project's separate
Phase acceptance remain pending. Authored pure codecs alone do not close
Workbench Step 2, CNCF Phase 100, Cozy Phase 78 or the Workbench Phase.

## Frozen producer logical hook/stage ABI

[EntityStore persistence stages](entity-store-persistence-stages.md) specifies
the exact new public trait additions and complete five-field caller.
`EntityPersistent` owns `storeRowMapping`, `toLogicalStoreRecord`,
`admitLogicalStoreRecord`, `decodeAdmittedLogicalStoreRecord`,
`toStoreRecordC`, `admitStoreRecordC`, `decodeAdmittedStoreRecordC` and
`fromStoreRecordC`. Create adds the declaration, logical write hook and strict
`toStoreRecordC`; Update adds the declaration, raw logical write hook and
`toStoreChangesRecordC`. The existing fromPersistent create factory forwards
the explicit declaration and logical write hook while preserving old physical
and field-name behavior. Query and collection helpers are unchanged.

Every new strict method requires explicit nonnull collection and Some(mapping)
and validates complete recursive declarations before application hooks.
`None`, null and Some(null) fail without old physical fallback or guessing.
Full write uses logical hook -> row encode; full read uses row decode -> logical
admission -> logical final decode. Final decode of an admitted logical Record
does not row-decode it again. Raw updates are structurally checked before
native Update normalization exactly once and accepted partial-row encoding.
Safe causal hook failures append context without replacing original reasons,
taxonomy, source or ordered causes. This freezes the producer responsibility;
all R1–R12 consumer transition, provider, generation, mutation/readback, recovery
and separate project acceptance obligations remain in force. The linked new
stage executable specification passed the selected 61/61 focused tests across
five suites and complete eleven-path independent protected review
`tkw-cncf-persistence-stage-step-review-20261005@1` PASS. The additive native
scalar binding is authored and frozen in
[EntityStore native scalar mapping](entity-store-native-scalar-mapping.md), with
current validation passed 85/85 tests across eight suites; complete eighteen-path
independent protected review remains pending. Prior stage acceptance is retained separately and does not establish
native scalar, SQL/JDBC, queries, restart or Workbench acceptance.


## Built-in Blob and Media metadata owner

BlobRecordCodec and MediaRecordCodec explicitly own their existing `attributes`
column, whose physical representation is JSON object text containing String
values. Their storage writers serialize that column; their readers decode only
that named column. String contents (including whitespace, Unicode and JSON-looking
text) remain exact. Missing attributes and an empty object retain the empty Map
meaning; existing native Record/Map application input remains compatible.
Malformed JSON, nonobject roots and nonstring JSON members fail with a structured
Consequence rather than silently losing metadata. Other columns and native
identity, revision, transaction and quota ownership retain their existing rules.
The SQL provider neither parses this payload nor infers its meaning.
`BlobMetadataSqlPersistenceSpec` exercises actual repositories across SQLite reopen.


## Built-in Job management projections

JobEntity and JobDefinitionEntity supply fixed row declarations through their
existing EntityPersistent hooks. This is the R2/R8/R9 consumer activation for
the built-in management projections, separate from signed canonical Job/quota
records and the JobEngine execution authority.

JobEntity declares optional XSemiStructuredData for input, debugParameters,
declaredProfile, jobDefinitionSnapshot, lineage, continuation, retry,
taskSummary and timelineSummary. continuationTaskIds is an ordered String
array; continuationTasks is an ordered XSemiStructuredData array. The
JobDefinition normalizedProfile is optional XSemiStructuredData. These selected
codecs encode physical JSON text and reconstruct their declared logical values.
Missing optional values and empty arrays keep the existing row-mapping rules;
Record null attributes are omitted recursively, while empty members remain
present and raw XJson null remains JSON; none implies SQL clearing.

Every other literal projection field has a fixed physical scalar declaration,
with the established managed/security/ContentBody aliases. The whitelist is
independent of incoming records and payloads. Unknown application fields,
malformed encoded JSON and incompatible known shapes fail under the existing
structured stage contract; collection names or runtime values supply no codec.

The logical toRecord/fromRecord models, exact identity/revision/authorization,
transactions and side writes remain owned by their existing stages. In
particular, JobDefinition retains its existing JCL-source interpretation;
stored normalized-profile metadata does not replace that parser. JobEngine
sync-failure diagnostics remain enabled. Native staged and ordinary submission
must preserve their original metadata without hiding failed projections.

Executable correspondence is Phase100JobEntityStoreMappingSpec and the existing
JobUnitOfWorkCompatibilitySpec JC01/JC04 native detached projection checks.
Authoring these declarations supplies no execution, review or Phase acceptance.
