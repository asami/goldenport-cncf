# EntityStore field and row mapping

Status: prior field/row slice accepted independently; selected row/codec/projection
focused validation passed 42/42 tests and
`tkw-cncf-row-mapping-step-review-20261005@1` PASS is recorded independently.
The package raw Update entry and producer stages passed 61/61 focused tests
across five suites, with SBT/wrapper exit 0 and serial lock released. Complete
eleven-path independent protected review
`tkw-cncf-persistence-stage-step-review-20261005@1` is PASS. The additive
[native scalar binding](entity-store-native-scalar-mapping.md) is authored and
frozen; current eight-suite validation passed 85/85 tests. Its independent
complete eighteen-path protected review remains pending.
Consumer integration remains pending.
Owner: CNCF Phase 100, DSM-100-02; Workbench Phase 1 / Step 2 prerequisite
PREREQ-CNCF-ROW-MAPPING.
Rules: [DataStore logical type mapping](datastore-logical-type-mapping.md)
R1, R7, R9, R10 and R12, with the R2–R8 selected value spaces.

This is the normative field/row contract. It installs no provider default or
provider route and closes no Phase or checklist item. The pure codec prerequisite
has independent accepted evidence: 26/26 focused tests and
`tkw-cncf-codec-focused-closure-20261005@1` PASS. Prior row evidence is accepted
separately; the accepted eleven-path stage review validates the package raw
Update path and [persistence stages](entity-store-persistence-stages.md), while
new native scalar acceptance remains pending. Generated
metadata, provider activation, all mutation/query/readback routes and recovery
remain required continuation work. Workbench and each producer/project keep
their separate acceptance gates.

## R1: Exact public declarations and ownership

```scala
package org.goldenport.cncf.entity

final case class EntityStoreField(
  logicalName: String,
  storageName: String,
  domain: org.goldenport.schema.ValueDomain,
  binding: EntityStoreFieldBinding
)

sealed abstract class EntityStoreFieldBinding {
  def datatype: org.goldenport.schema.DataType
  def storageCodecIdentity: String
  def elementBinding: Option[EntityStoreFieldBinding]
  def applicationClassName: Option[String]

  private[entity] def _validate_binding: org.goldenport.Consequence[Unit]
  private[entity] def _encode(value: Any): org.goldenport.Consequence[Any]
  private[entity] def _decode(value: Any): org.goldenport.Consequence[Any]
  private[entity] def _is_array: Boolean
}

object EntityStoreFieldBinding {
  val string: EntityStoreFieldBinding
  val json: EntityStoreFieldBinding
  val semiStructuredData: EntityStoreFieldBinding
  def application[A](codecIdentity: String)(using
    scala.reflect.ClassTag[A], io.circe.Encoder[A], io.circe.Decoder[A]
  ): EntityStoreFieldBinding
  def nativeScalar[A](
    datatype: org.goldenport.schema.DataType,
    representation: EntityStoreScalarRepresentation,
    codecIdentity: String
  )(using
    classTag: scala.reflect.ClassTag[A],
    encoder: io.circe.Encoder[A],
    decoder: io.circe.Decoder[A]
  ): EntityStoreFieldBinding
  def array(element: EntityStoreFieldBinding): EntityStoreFieldBinding
}

final case class EntityStoreRowMapping(
  fields: Vector[EntityStoreField],
  physicalFields: Set[String] = Set.empty
) {
  def validate(collection: org.simplemodeling.model.datatype.EntityCollectionId):
    org.goldenport.Consequence[Unit]
  def encode(collection: org.simplemodeling.model.datatype.EntityCollectionId,
    record: org.goldenport.record.Record): org.goldenport.Consequence[org.goldenport.record.Record]
  def encodePatch(collection: org.simplemodeling.model.datatype.EntityCollectionId,
    record: org.goldenport.record.Record): org.goldenport.Consequence[org.goldenport.record.Record]
  def decode(collection: org.simplemodeling.model.datatype.EntityCollectionId,
    record: org.goldenport.record.Record): org.goldenport.Consequence[org.goldenport.record.Record]

  private[entity] def _encode_update(
    collection: org.simplemodeling.model.datatype.EntityCollectionId,
    record: org.goldenport.record.Record
  ): org.goldenport.Consequence[org.goldenport.record.Record]
}
```

The binding is a storage selection capturing the existing pure codec. Its
concrete alternatives and constructor are private. It introduces no logical
datatype hierarchy. String, Json and semi-structured bindings declare Core
`XString`, `XJson` and `XSemiStructuredData`. Application binding declares
`DataType.Named(classTag.runtimeClass.getName)` and exposes that same full class
name through `applicationClassName`. It captures the supplied ClassTag, Encoder,
Decoder and identity; no registry, payload inspection, simple-name lookup or
method-availability convention supplies one. Array binding retains its explicit
child in `elementBinding`; datatype remains the child's Core datatype, with
outer array shape expressed by the declared multiplicity and recursive binding.
Array bindings expose no outer application class. Selected identities are
`string`, `json`, `semiStructuredData`, `application(<identity>)` and
`array(<child identity>)`.

The additive native scalar alternative retains the explicitly supplied Core
datatype, selected Text/Boolean/Number representation and captured full
application class name. It exposes no element binding and is scalar. Its identity
is `nativeScalar(text|boolean|number,<codecIdentity>)`. Unlike the existing
application factory, it does not replace a supplied canonical domain such as
`Named("name")` by the runtime class name. The exact additive ABI, physical value
matrix and recursive child bridge are frozen in
[EntityStore native scalar mapping](entity-store-native-scalar-mapping.md).

Callers own the exact per-collection field declarations and `physicalFields`
whitelist. Incoming unknown keys never extend it. Whitelisted managed identity,
revision, permission, ContentBody and native patch instructions pass unchanged;
their existing owners still enforce authorization, tenant boundaries, identity,
managed-field protection, OCC, ContentBody and transactions. This contract adds
no new permission or override.

Every public operation validates the complete declaration graph before examining
row values. Constructors have no validation side effect. Null collection,
vectors, sets, fields, domains, bindings and missing metadata return Consequence
failures. Missing nested bindings fail even for empty and nested-empty arrays,
before captured application conversion is invoked. Constraints stay as domain
metadata; authoritative entity admission retains constraint validation.

Names are nonnull, nonblank and already trimmed. Duplicate logical names,
duplicate storage names and physical-whitelist overlap with either namespace
fail. A logical name may equal another field's storage name: each direction
uses only its own namespace. Encode reads exact logical names and emits exact
storage names; decode reads exact storage names and emits exact logical names.
There is no alias fallback. Input keys must be unique, valid and declared in
the chosen direction or explicitly whitelisted. Only actual `Field.Value.Single`
is admitted. Metadata and row-structure failures precede value conversion.

## R6/R7: Datatype, shape and cardinality

`domain.datatype` equals `binding.datatype`. Scalar bindings admit only `One`
and `ZeroOne`. Arrays admit `ZeroMore`, `OneMore`, `Range` and `Ranges`.
Range endpoints must be nonnegative and ordered. Ranges must contain at least
one valid member and each member must be valid; admitted counts are the union
of its declared closed intervals. Unsupported combinations fail
`incompatible-mapping`.

Root-array cardinality is checked before encoding and after decoding.
`ZeroMore` admits every count, `OneMore` admits positive counts, and Range/Ranges
admit only declared counts. The minimum admitted count determines outer
nullability: zero permits absent/native-null full fields; positive minima reject
them. Nested child arrays keep the pure array value space, including empty
children, independently of the outer field cardinality.

Array writes accept Scala Seq. Under a declared array binding only,
`cats.data.NonEmptyVector` adapts to Vector. Adaptation follows the declared
child bindings recursively and preserves every scalar/custom member for its
selected codec. Set, native Array, Map and arbitrary Iterable are not inferred
as arrays. Readback is ordered Vector. `array(json)` retains present Json.Null;
String, Record and array children reject JSON null with index context. Explicit
application codecs own their JSON-null policy and must return a nonnull value
of the captured application class.

Native scalar children require the exact selected non-null scalar Json kind,
including under nested arrays. Their captured conversion runs once per actual
element; outer nullable null stays with this row policy. Null supplied native
datatype fails `missing-mapping`, and missing representation/captures fail
`missing-codec-binding` before any hook, even for empty arrays.

## R7: Presence, native null and normalized patch table

| Input condition | Full encode | Full decode | encodePatch |
| --- | --- | --- | --- |
| Required mapped key absent | missing-required-field | missing-required-field | No change; stays absent |
| Nullable mapped key absent | Stays absent | Stays absent | No change; stays absent |
| Required mapped native null | null-not-allowed | null-not-allowed | explicit-clear-required |
| Nullable mapped native null | Present native-null Field | Present native-null Field | explicit-clear-required |
| `ZeroOne` logical `None` | Omitted field | No Option reconstruction | Omitted field; no change |
| `ZeroOne` logical `Some(value)` | Unwrap once, selected codec | No Option reconstruction | Unwrap once, selected codec |
| Required scalar Option | Selected codec rejects wrong logical value | Selected codec requires its physical representation | Selected codec rejects wrong logical value |
| Mapped `Update.SetNull`, nullable | Reject update marker | Reject update marker | Preserve actual marker under physical key |
| Mapped `Update.SetNull`, required | Reject update marker | Reject update marker | null-not-allowed |
| Mapped `Update.Noop` / `Update.SetValue` wrapper | Reject update marker | Reject update marker | invalid-row-shape; normalize first |
| Json.Null under Json binding | Present text `null` | Text `null` becomes Json.Null | Present text `null` |
| Empty Record under Record binding | Present text `{}` | Text `{}` becomes Record.empty | Present text `{}` |
| Whitelisted physical key | Pass actual Field unchanged | Pass actual Field unchanged | Pass actual Field/instruction unchanged |

`encodePatch` consumes the existing `Update.toChangesRecord` output: Noop has
already disappeared, SetValue has already unwrapped, and SetNull is still the
actual clear marker. Its physical key must survive framework merge; replacing
it with an omission would lose the explicit clear instruction. The later
provider mutation stage materializes SQL NULL. A bare native-null patch cannot
clear a mapped field. Optional None also cannot clear one.

The narrow package `_encode_update` entry admits the raw logical Update Record
from the strict update producer stage. It validates complete metadata and raw
row structure before native `Update.toChangesRecord` runs exactly once, then
reuses `encodePatch`. The pre-normalization flag bypasses only marker rejection:
null rows/field vectors/fields/values, non-Single values, duplicate keys and
undeclared keys still fail before normalization. Duplicate or undeclared Noop
fields cannot be concealed by omission and non-Single fields cannot reach the
native normalizer's partial match. Required Noop omits a field; SetValue unwraps
once; nullable SetNull remains the actual physical marker; remaining nested
markers fail the existing normalized-row policy. No public row signature or
successful value space changes. This package path passed the selected five-suite focused command, 61/61
tests with SBT/wrapper exit 0 and serial lock released, plus complete eleven-path
independent protected review `tkw-cncf-persistence-stage-step-review-20261005@1`
PASS, distinct from prior accepted row evidence and pending native scalar
acceptance. Null successful application hook values fail in the producer adapter
before reaching this raw-row entry.

Full decode returns plain logical values or explicit native-null Fields for
authoritative entity admission; it does not reconstruct Some. Required full
conversion rejects absence or native null. All transformed rows retain input
order using actual Field vectors; `Record.createFull` is the caller-side
construction contract. Omission-oriented accessors/builders must not erase
successful empty Records, empty arrays or explicit nulls.

## R2–R8: Selected values

String conversion accepts only actual String and retains every character,
including empty, whitespace, Unicode, escaped, JSON-looking and malformed text.
Json conversion retains every canonical Json shape with exact numeric meaning.
Record conversion retains an empty root, nested null/empty members and exact
numbers through the accepted canonical Core conversion. Numeric semantics,
rather than a particular numeric wrapper class or JSON lexical formatting, are
the structured roundtrip law. Application conversion uses only its captured
explicit ClassTag/Encoder/Decoder. Unsupported values never call toString as a
success fallback. The original string/json/semiStructuredData/application/array
bindings require actual physical String; Record, Json and numbers fail
`wrong-physical-value` for those bindings on read. The explicitly selected
[native scalar binding](entity-store-native-scalar-mapping.md) additionally uses
unchanged String, Boolean or exact BigDecimal physical storage, without changing
those original successful value spaces. Number-selected physical text is parsed
only as a number; Text is never parsed. SQL/JDBC precision remains a separate
provider obligation.

## R10: Structured failures and dependent conversion

Failures use existing Consequence and Descriptor facets. Independent field
failures aggregate through `Consequence.zipN` in row order, followed by missing
required declarations in declaration order. Existing failing array indices
retain their order and one causal conclusion per failing member. No successful
partial row is returned. Parsing and conversion inside one field stay dependent
flatMap operations; row mapping performs no DataStore mutation or external IO.

Each causal conclusion retains taxonomy, status, source and all existing facets,
and gains `Policy(entity.store.logical-type)`, `Id(collection.print)`,
`FieldPath(logicalName)`, `Key(storageName)`, `State(validate|encode|encode-patch|decode)`
and the selected `Algorithm` where present. `<mapping>` is reserved for whole-map
failures; missing collection/field/name metadata uses known missing placeholders.
Facets contain declaration context, never raw values, exception messages, row
copies or content hashes.

| Reason | Meaning |
| --- | --- |
| missing-mapping | Missing row declaration metadata or an undeclared input key |
| incompatible-mapping | Name/domain/shape/multiplicity inconsistency |
| missing-required-field | Absent required full-row field |
| null-not-allowed | Required native null or forbidden explicit clear |
| explicit-clear-required | Bare native-null patch instead of SetNull |
| cardinality-mismatch | Root array count outside admitted cardinality |
| invalid-row-shape | Null/malformed row, duplicate keys, non-Single values or unnormalized update markers |
| missing-codec-binding | Missing explicit identity, ClassTag, Encoder, Decoder, scalar representation or recursive child codec |
| wrong-physical-value | Mapped physical value is outside its explicitly selected representation |
| unsupported-external-value | Selected native Number is nonfinite or cannot be represented as exact BigDecimal |
| malformed-stored-json | Physical String fails JSON parsing; preserves valueFormatError taxonomy |
| wrong-structured-shape | Parsed JSON root/child fails the selected structured shape |
| wrong-logical-value | Logical kind/class is outside the selected codec value space |
| application-codec-failure | Explicit encoder/decoder rejection, NonFatal exception or invalid decoder success |

Pure Reason facets are assigned at their original failure sites, never inferred
from display text. Pure taxonomies and successful value spaces remain unchanged.
The existing array index helper changes only Message to retain its sanitized
`index[index]` prefix and adds index FieldPath context; every other causal facet
and original taxonomy survives. Field decoration retains the pure Reason.

## R12: Representative five-field caller

```scala
import scala.reflect.ClassTag
import io.circe.{Decoder, Encoder}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString, XJson, XSemiStructuredData}
import org.goldenport.cncf.entity.{EntityStoreField, EntityStoreFieldBinding, EntityStoreRowMapping}

final case class Summary(label: String, score: Int)
val summaryEncoder: Encoder[Summary] = Encoder.forProduct2("label", "score")(x => (x.label, x.score))
val summaryDecoder: Decoder[Summary] = Decoder.forProduct2("label", "score")(Summary.apply)
val summaryBinding = EntityStoreFieldBinding.application[Summary]("summary-v1")(
  using summon[ClassTag[Summary]], summaryEncoder, summaryDecoder)

val mapping = EntityStoreRowMapping(Vector(
  EntityStoreField("originalProposal", "original_proposal",
    ValueDomain(XString, Multiplicity.One), EntityStoreFieldBinding.string),
  EntityStoreField("payload", "payload_json",
    ValueDomain(XJson, Multiplicity.One), EntityStoreFieldBinding.json),
  EntityStoreField("evidence", "evidence_json",
    ValueDomain(XSemiStructuredData, Multiplicity.ZeroOne), EntityStoreFieldBinding.semiStructuredData),
  EntityStoreField("labels", "labels_json",
    ValueDomain(XString, Multiplicity.ZeroMore), EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)),
  EntityStoreField("summary", "summary_json",
    ValueDomain(DataType.Named(summon[ClassTag[Summary]].runtimeClass.getName), Multiplicity.One), summaryBinding)
), physicalFields = Set("id", "revision", "permission"))
```

This caller declares logical datatypes, cardinalities, names and codecs. It does
not install a hook, authorize a managed-field update or derive a whitelist from
incoming row data. A later generated/custom consumer must delegate exactly once
on each selected boundary and supply its exact collection whitelist.

## Executable authoring and retained acceptance work

[EntityStoreRowMappingSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityStoreRowMappingSpec.scala)
authors ROW-01 through ROW-16: the actual five-field caller; public String PBT;
structured presence/exact numbers; pre-conversion metadata failures; recursive
missing bindings; required/optional nulls; optional Record values; normalized
patches; cardinality/representations; nested arrays/null children; physical failure
classification; independent causal aggregation; sanitized class failure; exact
directional names; invalid setup; and explicit framework-field passthrough.

All sixteen authored row scenarios and the two required codec/projection suites
passed 42/42 tests in the parent-selected focused command, with SBT/wrapper exit 0
and the serial lock released. Independent protected review
`tkw-cncf-row-mapping-step-review-20261005@1` is PASS, recorded separately.
The new package raw Update path and linked producer hook stages passed the
selected five-suite command, 61/61 tests with SBT/wrapper exit 0 and serial lock
released. Complete eleven-path independent protected review
`tkw-cncf-persistence-stage-step-review-20261005@1` is PASS. New native scalar
validation passed 85/85 tests across eight suites; complete eighteen-path
independent protected review remains pending. Existing EntityStoreValueCodecSpec and
EntityStoreRecordProjectionSpec remain required focused coverage. The parent owns
validation, diff admission and transitions. EntityStoreRecordProjection,
every StandardEntityStore mutation/readback owner,
EntityStoreSpace, runtime Collection, SqlDataStore and generators are unchanged.
EntityPersistent gains only the linked producer stages and preserves its old
physical hooks and existing companion collection helpers. Consumer integration,
provider reads, generated hooks, write/read/restart coverage,
both Workbench proposal fixtures, recovery and project/Phase acceptance remain
required separate continuation work.
