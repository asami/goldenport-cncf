# EntityStore native scalar mapping

Status: additive ABI and executable specification authored; current focused
validation passed 85/85 tests across eight suites, with SBT/wrapper exit 0 and
serial lock released. Complete eighteen-path independent protected review remains
pending. No Step or Phase acceptance is claimed.
The accepted prerequisite producer stages retain 61/61 focused tests across five
suites, SBT/wrapper exit 0 and serial lock released, plus complete eleven-path
independent protected review `tkw-cncf-persistence-stage-step-review-20261005@1`
PASS. That evidence predates and does not validate this new binding.

Owner: CNCF Phase 100 DSM-100-02; Workbench Phase 1 / Step 2 prerequisite
PREREQ-CNCF-NATIVE-SCALAR.

Normative dependencies: [DataStore logical type mapping](datastore-logical-type-mapping.md)
R1–R12, [EntityStore field and row mapping](entity-store-row-mapping.md), and
[EntityStore persistence stages](entity-store-persistence-stages.md).

## Explicit declaration and exact ABI

The application/generated field declaration selects its authoritative Core
logical datatype, physical scalar representation and captured application codec.
The datatype remains exactly the supplied object, including `DataType.Named("name")`,
`XInt`, `XLong`, `XBoolean` and `XDecimal`. It is not replaced by a class name,
payload shape or SQL column type. Row validation still compares
`domain.datatype` with `binding.datatype`.

```scala
package org.goldenport.cncf.entity

enum EntityStoreScalarRepresentation {
  case Text, Boolean, Number
}

object EntityStoreFieldBinding {
  def nativeScalar[A](
    datatype: org.goldenport.schema.DataType,
    representation: EntityStoreScalarRepresentation,
    codecIdentity: String
  )(using
    classTag: scala.reflect.ClassTag[A],
    encoder: io.circe.Encoder[A],
    decoder: io.circe.Decoder[A]
  ): EntityStoreFieldBinding
}

object EntityStoreValueCodec {
  private[entity] def _native_scalar[A](
    datatype: org.goldenport.schema.DataType,
    representation: EntityStoreScalarRepresentation,
    codecidentity: String
  )(using
    classtag: scala.reflect.ClassTag[A],
    encoder: io.circe.Encoder[A],
    decoder: io.circe.Decoder[A]
  ): EntityStoreValueCodec[A]
}
```

These representations introduce no Core logical datatypes and no new public
ValueCodec method. Existing binding factory signatures and successful pure
codec behavior remain unchanged. A native scalar binding reports its supplied
datatype and captured full `applicationClassName`, `elementBinding = None` and
`_is_array = false`. Its identity is
`nativeScalar(text|boolean|number,<codecIdentity>)`.

Complete recursive binding validation precedes logical and captured application
hooks. Null datatype is `missing-mapping`; null representation, null ClassTag,
Encoder or Decoder, and null/blank identity are `missing-codec-binding`.
Domain inconsistency remains `incompatible-mapping`. Empty and nested-empty
arrays cannot conceal an incomplete native child declaration. Construction has
no validation side effect, IO, registration or reflection-based inference.

## Representation matrix

Each direct write invokes the captured encoder once, validates the selected
scalar Json kind, and extracts its physical value. Each direct read converts an
admitted physical value into scalar Json, validates the selected kind, and
invokes the captured decoder once. ClassTag.unapply checks runtime classes.
NonFatal encoder/decoder exceptions remain sanitized application failures.

| Selection | Required codec JSON root | Physical write | Admitted physical read |
| --- | --- | --- | --- |
| Text | Actual JSON string | Exact unchanged Scala String content | Actual String, passed to Json.fromString |
| Boolean | Actual JSON Boolean | Actual Scala Boolean | Boolean; exact numeric 0/1 from the Number numeric classes; exact String `"0"` or `"1"` |
| Number | Finite JSON number with exact BigDecimal representation | Scala BigDecimal extracted with JsonNumber.toBigDecimal | Byte, Short, Int, Long, Scala BigInt/BigDecimal, Java BigInteger/BigDecimal, finite Float/Double, or explicitly parsed numeric JSON text |

Text never trims, parses or lexically reconstructs content. Empty, Unicode,
whitespace, escaping and numeric/object/array/null/malformed JSON-looking text
remain exact text. Core `Name` can therefore keep its actual captured Codec and
canonical `Named("name")` domain while persisting plaintext.

Number uses no Double intermediate for encode or exact numeric-class decode.
Finite Float/Double input uses Circe's explicit `Json.fromFloat` / `fromDouble`
constructor. It retains the meaning those constructors assign to that already
floating-point value; it cannot recover precision lost before conversion.
NaN/Infinity and a JSON number without an exact BigDecimal representation fail
`unsupported-external-value`. BigDecimal's representable scale/exponent range
still applies; this is not an unlimited-exponent storage contract.

Physical String parsing occurs only under the explicit Number selection. The
parsed root must be a number: invalid text, JSON null, object, array, Boolean
and quoted numeric strings fail `wrong-physical-value`. The captured typed
decoder still enforces integral ranges and rejects fractional Int values. No
numeric conversion uses Presentable, arbitrary toString or a successful String
fallback.

Boolean uses only the admitted exact zero/one convention. Other numeric values,
strings including `"true"`, `"01"`, `"1.0"` and whitespace-padded digits,
structured objects and native null fail conversion. Row presence policy handles
outer native null first. There is no truthiness conversion.

## Recursive array bridge

`array(nativeScalar(...))` retains the native child's canonical datatype and
selected scalar Json bridge. Arrays still store JSON array text; they do not
store vectors of individually stringified physical values. Text children become
JSON strings once, Boolean children JSON booleans and Number children exact JSON
numbers. No child Text is parsed as JSON or serialized/parsed twice.

Nested arrays retain explicit children, order and empty arrays. Each actual
element invokes the captured application encoder/decoder once. Missing child
metadata fails before traversal even when there are no elements. Null or wrong
elements aggregate causal failures in element order, with existing recursive
index FieldPath context; no partial Vector or row succeeds.

## Presence and failures

Existing row and strict-stage owners retain required/optional multiplicity,
absent/None/Some handling, SQL/native outer null, patch markers, framework
passthrough and logical admission. A present native scalar `Json.Null` or
native-null encoder Json result is `wrong-structured-shape`, as are objects,
arrays and a different selected scalar kind. It is not a present native scalar
value. A nullable outer native null continues to use the existing row presence
policy without calling an application codec. Generic `application` and `json`
bindings retain their original explicit JSON-null policies.

Raw Update validation and native normalization occur under the existing strict
stage exactly once: Noop omits, SetValue unwraps once, nullable SetNull remains
the actual marker under the storage name, and bare patch null cannot clear.
Required null/absence and unlisted keys still fail; no framework whitelist is
widened to bypass scalar declarations.

Representation failures use existing `Consequence.valueInvalid` and Descriptor
Reason facets: `wrong-physical-value` for invalid physical inputs,
`wrong-structured-shape` for invalid codec Json kind,
`unsupported-external-value` for nonfinite/unrepresentable numbers,
`missing-codec-binding` for missing captures/representation, and `missing-mapping`
for missing supplied datatype. Original application-codec-failure,
wrong-logical-value, causes, taxonomy and facets survive. Row/stage owners append
collection, logical/storage name and stage context; array owners append indices.
Diagnostics include no payload rendering or exception message.

## Complete native Name/Int/Boolean/BigDecimal caller

```scala
import io.circe.{Codec, Decoder, Encoder}
import scala.reflect.ClassTag
import org.goldenport.Consequence
import org.goldenport.datatype.Name
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XBoolean, XDecimal, XInt}
import org.goldenport.cncf.entity.{EntityPersistent, EntityStoreField, EntityStoreFieldBinding,
  EntityStoreRowMapping, EntityStoreScalarRepresentation}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

object NativeCandidatePersistence {
  final case class Candidate(id: EntityId, name: Name, count: Int,
    approved: Boolean, amount: BigDecimal)
  val collection: EntityCollectionId = EntityCollectionId("textus", "workbench", "candidate")
  private val _name_codec: Codec[Name] = summon[Codec[Name]]
  val mapping: EntityStoreRowMapping = EntityStoreRowMapping(Vector(
    EntityStoreField("name", "candidate_name", ValueDomain(DataType.Named("name"), Multiplicity.One),
      EntityStoreFieldBinding.nativeScalar[Name](DataType.Named("name"), EntityStoreScalarRepresentation.Text,
        "core-name-v1")(using summon[ClassTag[Name]], _name_codec, _name_codec)),
    EntityStoreField("count", "candidate_count", ValueDomain(XInt, Multiplicity.One),
      EntityStoreFieldBinding.nativeScalar[Int](XInt, EntityStoreScalarRepresentation.Number,
        "int-v1")(using summon[ClassTag[Int]], Encoder.encodeInt, Decoder.decodeInt)),
    EntityStoreField("approved", "candidate_approved", ValueDomain(XBoolean, Multiplicity.One),
      EntityStoreFieldBinding.nativeScalar[Boolean](XBoolean, EntityStoreScalarRepresentation.Boolean,
        "boolean-v1")(using summon[ClassTag[Boolean]], Encoder.encodeBoolean, Decoder.decodeBoolean)),
    EntityStoreField("amount", "candidate_amount", ValueDomain(XDecimal, Multiplicity.One),
      EntityStoreFieldBinding.nativeScalar[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number,
        "decimal-v1")(using summon[ClassTag[BigDecimal]], Encoder.encodeBigDecimal, Decoder.decodeBigDecimal))
  ), physicalFields = Set("id"))

  object Persistent extends EntityPersistent[Candidate] {
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
    def id(e: Candidate): EntityId = e.id
    def toRecord(e: Candidate): Record = Record(Vector(
      Field("id", Field.Value.Single(e.id)), Field("name", Field.Value.Single(e.name)),
      Field("count", Field.Value.Single(e.count)), Field("approved", Field.Value.Single(e.approved)),
      Field("amount", Field.Value.Single(e.amount))))
    def fromRecord(r: Record): Consequence[Candidate] = Consequence.success(Candidate(
      _value(r, "id").asInstanceOf[EntityId], _value(r, "name").asInstanceOf[Name],
      _value(r, "count").asInstanceOf[Int], _value(r, "approved").asInstanceOf[Boolean],
      _value(r, "amount").asInstanceOf[BigDecimal]))
    private def _value(record: Record, key: String): Any =
      record.fields.find(_.key == key).get.value.single
  }

  def roundtrip(entity: Candidate): Consequence[Candidate] =
    Persistent.toStoreRecordC(collection, entity).flatMap(Persistent.fromStoreRecordC(collection, _))
}
```

This complete-row caller constructs actual Field vectors so empty/native-null
presence is not omitted. Production entity admission still owns domain constraints
and identity, authorization, revision, ContentBody, OCC and transaction policy.

## Executable authoring and required continuation

[EntityStoreNativeScalarMappingSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityStoreNativeScalarMappingSpec.scala)
authors NATIVE-01–NATIVE-12: actual Core Name, canonical primitives, exact numbers,
numeric physical classes/ranges/nonfinite values, Boolean zero/one, Text versus
Number text, sanitized scalar/application failures, complete recursive captures,
native arrays, presence/Update/framework policy, ordered causal context/domain
mismatch, and public strict-stage exact String PBT requesting at least 200
successful adversarial cases. The current eight-suite generation/native/persistence
accumulator passed 85/85 tests. Fresh complete eighteen-path independent protected
review remains pending.

The current SQL `SqlDataStore._column_value` converts native BigDecimal to Double
and loses native decimal precision. Provider read guessing, JDBC precision,
queries and restart are not established by this pure conversion. Required same-Goal
continuation includes SQL provider cutover, generator logical records/metadata,
all seven native mutation/readback route owners, runtime Collection and seed
forwarding, generated write/read/restart coverage, both Workbench proposal
fixtures, recovery, and separate producer/project/Step/Phase acceptance. Existing
legacy physical hooks are preserved and are not the final integration solution.
