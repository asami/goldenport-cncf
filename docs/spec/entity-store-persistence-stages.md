# EntityStore persistence stages

HISTORICAL Phase 100 prerequisite validation snapshot — 2026-10-05:
Earlier scoped validation/protected-review-pending statements retained in this
document describe their original prerequisite subjects. They are not claims
that those pending reviews subsequently passed; current Phase 115 adoption is
recorded separately below. Normative requirements remain in force.

## Current status

The historical Phase 100 public producer hook/stage ABI is independently accepted and frozen. Its selected focused
validation passed 61/61 tests across five suites, with SBT/wrapper exit 0 and the
serial lock released. Complete eleven-path independent protected accumulator
review `tkw-cncf-persistence-stage-step-review-20261005@1` is PASS. This document
claims no consumer activation, Step closure or Phase closure. The additive
[native scalar binding](entity-store-native-scalar-mapping.md) is authored and
frozen with current eight-suite validation passed 85/85 tests and independent
complete eighteen-path protected review pending; prior stage acceptance
does not establish its behavior.

The pure codec prerequisite has independently accepted 26/26 focused tests and
`tkw-cncf-codec-focused-closure-20261005@1` PASS. The prior field/row prerequisite
has independently recorded 42/42 focused tests, SBT/wrapper exit 0, serial lock
released and `tkw-cncf-row-mapping-step-review-20261005@1` PASS. Those accepted
results remain separate prerequisite evidence. The current five-suite command
validates the new hooks and narrow raw Update entry with the row/value/projection
and collection-identity compatibility accumulator.

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

## Owner

CNCF Phase 100 DSM-100-02; Workbench Phase 1 / Step 2 prerequisite
PREREQ-CNCF-PERSISTENCE-STAGES. The application or generated persistent owns
its exact collection declaration and framework physical whitelist. The stateless
package adapter owns stage composition; the accepted row mapper owns logical to
physical conversion. Identity, revision, tenant, security, authorization,
ContentBody, OCC and transaction policies remain with their existing owners.

Normative dependencies: [DataStore logical type mapping](datastore-logical-type-mapping.md)
R1–R12 and [EntityStore field and row mapping](entity-store-row-mapping.md).
The additive [EntityStore native scalar mapping](entity-store-native-scalar-mapping.md)
retains those row/stage boundaries while selecting native Text/Boolean/Number
storage explicitly under the supplied canonical datatype and captured codec.

## Update rule

Changes to the frozen ABI or value spaces require corresponding executable
specifications and separately selected validation and independent review.
Preserve all legacy physical hooks and collection helpers. Consumer cutover,
generator emission, provider defaults and mutation/readback route coverage
remain required authorized continuation work. They are not deferred candidates
and cannot be accepted through this producer specification alone.

## Exact public trait additions

The following additions coexist with every existing trait method. Public
parameter names are source API. No mapping is inferred by the defaults.

```scala
trait EntityPersistent[E] extends RecordCodex[E] with Identified[E, EntityId] {
  def storeRowMapping: Option[EntityStoreRowMapping] = None
  def toLogicalStoreRecord(e: E): Consequence[Record] = Consequence.success(toRecord(e))
  def admitLogicalStoreRecord(r: Record): Consequence[Record] = Consequence.success(r)
  def decodeAdmittedLogicalStoreRecord(r: Record): Consequence[E] = fromRecord(r)
  def toStoreRecordC(collection: EntityCollectionId, e: E): Consequence[Record]
  def admitStoreRecordC(collection: EntityCollectionId, r: Record): Consequence[Record]
  def decodeAdmittedStoreRecordC(collection: EntityCollectionId, r: Record): Consequence[E]
  def fromStoreRecordC(collection: EntityCollectionId, r: Record): Consequence[E]
}

trait EntityPersistentCreate[E] extends RecordEncoder[E] with Identifiable[E, EntityId] {
  def storeRowMapping: Option[EntityStoreRowMapping] = None
  def toLogicalStoreRecord(e: E): Consequence[Record] = Consequence.success(toRecord(e))
  def toStoreRecordC(collection: EntityCollectionId, e: E): Consequence[Record]
}

trait EntityPersistentUpdate[E] extends RecordCodex[E] {
  def storeRowMapping: Option[EntityStoreRowMapping] = None
  def toLogicalStoreRecord(e: E): Consequence[Record] = Consequence.success(toRecord(e))
  def toStoreChangesRecordC(collection: EntityCollectionId, e: E): Consequence[Record]
}
```

The stateless `private[cncf] object EntityStorePersistenceStage` has package
helpers `_encode(collection, mapping, source)`, `_encode_changes(collection,
mapping, source)`, `_admit(collection, mapping, record)(admit)` and
`_decode_admitted[A](collection, mapping, record)(decode)`. Their declaration
types are `EntityCollectionId`, `Option[EntityStoreRowMapping]`, by-name
`Consequence[Record]` for write sources, `Record` for read input,
`Record => Consequence[Record]` for admission and `Record => Consequence[A]`
for final decoding. The write and admission helpers return `Consequence[Record]`;
final decoding returns `Consequence[A]`. Internal parameters use flatcase.
There is no registry, IO, provider dependency, mutable state, type guessing,
constructor validation or new datatype/ORM hierarchy.

## Strict missing-metadata policy

Every strict operation validates the explicit nonnull collection and complete
mapping recursively before evaluating the by-name logical write source or
calling admission/final decoding. `None`, a null Option, `Some(null)` or null
collection fails `missing-mapping` with `FieldPath(<mapping>)`, `Key(<mapping>)`
and `State(encode|encode-patch|decode|decode-admitted)`. A missing collection
uses `Id(<missing-collection>)`; otherwise Id is `collection.print`.
Recursive binding absence fails even for empty and nested-empty arrays before
captured application code. Inconsistent declarations retain the accepted row
validation's reason and causal context.

Native scalar declarations also validate the supplied canonical datatype and
selected representation before hooks, including nested empty arrays. Missing
datatype fails `missing-mapping`; missing representation/captures fail
`missing-codec-binding`. Native Text/Boolean/Number direct fields and their
recursive array children share one captured application conversion per actual
value. The existing stage composition adds no second conversion.

`storeRowMapping = None` explicitly means no declaration. An adapter may
continue using its old physical API, but a new strict method never falls back
to old hooks or derives metadata from their output. Derived adapters have no
inferred mapping. The explicit collection is mapping and diagnostic context;
these methods neither generate nor repair an EntityId. Exact EntityId ownership
validation remains the existing companion and route responsibility, as for the
old `fromStoreRecord` contract.

## Physical and logical hook table

| Method | Input/output value space | Stage responsibility |
| --- | --- | --- |
| `toRecord` / `fromRecord` | Existing application record contract | Unchanged; new default logical hooks delegate to these |
| `toStoreRecord` | Entity to old physical Record | Existing physical/legacy contract, never a new logical source |
| `fromStoreRecord` | Old physical Record to entity | Existing physical/legacy contract |
| `admitStoreRecord` / `decodeAdmittedStoreRecord` | Existing physical admission/final contract | Unchanged package hooks and collection helpers |
| `toLogicalStoreRecord` | Entity to logical Record | One protected logical write hook, then row encode |
| `admitLogicalStoreRecord` | Logical Record to admitted logical Record | Row decode first, one protected logical admission hook |
| `decodeAdmittedLogicalStoreRecord` | Admitted logical Record to entity | One protected final hook; no row conversion |
| `toStoreRecordC` | Entity to mapped physical Record | Full declared encode |
| `admitStoreRecordC` | Physical Record to admitted logical Record | Declared decode followed by logical admission |
| `decodeAdmittedStoreRecordC` | Admitted logical Record to entity | Declaration validation and logical final decoding only |
| `fromStoreRecordC` | Physical Record to entity | Compose the preceding two read stages |
| `toStoreChangesRecordC` | Raw logical Update Record to physical patch | Checked normalization and partial declared encode |

Full write is logical hook -> row encode -> physical Record. Full read is row
decode -> logical admission -> logical final decode. Split admission/final read
equals composed read; already admitted Record/Json/array/application values are
not parsed again. Each application encoder/decoder and logical hook runs once
in its corresponding stage. Repeated declaration validation is permitted and
does not repeat conversion. No row failure returns a partial entity.

All old `storeFieldName`, view, identity, collection, securityAttributes and
authorizationRecord contracts are unchanged. Query traits are unchanged.

## Runtime typed bridge binding — 2026-10-17

The ComponentFactory adapter for an actual `EntityPersistent` instance must
preserve its explicit `storeRowMapping`, logical write/admission/final decode
hooks and existing physical/view/security hooks. It must not replace that
declaration with the default None while enriching companion metadata.

For a declared mapping, `storeFieldName` uses the matching field's storageName;
unmapped framework names delegate to the persistent's existing method. Generated
`INPUT_KEYS` lists are input aliases and cannot override that declaration.
For a legacy persistent with no row declaration, the existing companion-alias
mapping remains compatibility behavior. This does not infer a typed declaration
for that consumer or activate a provider default. Structural foreign-classloader
compatibility remains a separate legacy route; it is not typed-stage acceptance.

`ComponentFactoryGeneratedSchemaSpec` proves typed declaration/hook retention,
exact query field selection and unchanged undeclared legacy name selection.
Workbench's ordinary proposal fixtures supply downstream query reproduction:
the old adapter queried empty `producer_ref`/`proposal_id` columns while generated
writes populated `producerRef`/`proposalId`. SQL TEXT inference, full EntityStore
stage activation and recovery remain distinct pending work.

## Create and raw Update normalization

`EntityPersistentCreate.fromPersistent` additionally forwards the supplied
`storeRowMapping` and `toLogicalStoreRecord`. Its existing `toRecord` and old
physical `toStoreRecord` forwarding stay unchanged. Its old default field-name
behavior remains identity; it does not newly forward `storeFieldName`.
Runtime Collection and seed adapters remain separate integration owners.

Update's logical hook returns the actual raw logical Update Record. The narrow
`private[entity] EntityStoreRowMapping._encode_update(collection, record)`
validates complete metadata and raw structure/known unique Single keys before
calling actual `Update.toChangesRecord` exactly once, then reuses `encodePatch`.
Only pre-normalization marker rejection is bypassed. Core owns valid field
vectors and Single values; malformed constructed Record/Field fixtures are no
longer semantic inputs. Invalid/duplicate keys and undeclared keys fail before
normalization, even if invalid Noop fields would otherwise disappear.
SetValue(null) fails explicit-clear-required before Update.toChangesRecord.
Nested sequence construction failures are captured as Consequence failures. A null
successful logical hook value is an application-hook failure before row entry.
The package raw row entry itself rejects a null Record as invalid row shape.

| Raw logical input | Physical patch outcome |
| --- | --- |
| `Update.Noop` | Omitted, including required fields |
| `Update.SetValue(value)` | Unwrap once and apply declared binding |
| Nullable `Update.SetNull` | Preserve actual marker under storage name |
| Required `Update.SetNull` | `null-not-allowed` |
| Foreign bare native null | `explicit-clear-required` before semantic construction; caller migration accepted in RCP-CONSUMERS |
| `Update.SetValue(null)` | `explicit-clear-required` at raw Update adapter |
| Optional `None` | Omitted; no change |
| Remaining nested Update marker | Reject under accepted row policy |

Structured empty SetValue remains a present encoded value. Exact String text
is unchanged. The normalization introduces no new Update dialect and does not
change external Update.scala. The later provider stage still owns SQL NULL
materialization and managed-field protection.

## Safe causal failures

Every new logical hook is protected from NonFatal exceptions, null Consequence
and successful null values. These produce `Consequence.valueInvalid` with
`Reason(application-hook-failure)`, `Policy(entity.store.logical-type)`,
`Id(collection.print)`, `FieldPath(<hook>)`, `Key(<hook>)` and
`State(logical-write|logical-admit|logical-decode)`. Fatal failures propagate.
Diagnostics copy no exception message, entity/row rendering or payload.

A returned structured hook failure keeps each original taxonomy, status,
observation/source, facets and causal order. Hook policy/context is appended
without replacing its original Reason. Mapping/row failures retain accepted
Reason and causal facets, including indices and physical names. Independent
row failures aggregate without partial success; dependent parsing/conversion
stays ordered within a field.

## Complete five-field caller

This application-owned declaration uses actual canonical values and explicit
captured codecs. Rows are constructed with `Record.createFull`; field vectors
preserve empty values and row order while Core omits null attributes recursively.
Untyped arrays read as Vector; Core ValueReader reconstructs declared
NonEmptyVector, rejecting empty input. JDBC SQL NULL is absence at ingress;
required absence fails. Typed SetNull reaches native null only at SQL output.
Raw XJson Json.Null remains present JSON. It is a producer example, not provider
or generated route activation.

```scala
import scala.reflect.ClassTag
import io.circe.{Decoder, Encoder, Json}
import org.goldenport.Consequence
import org.goldenport.id.Identified
import org.goldenport.record.{Record, RecordCodex}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString, XJson, XSemiStructuredData}
import org.goldenport.cncf.entity.{EntityPersistent, EntityStoreField, EntityStoreFieldBinding, EntityStoreRowMapping}
import org.goldenport.value.ContentBody
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

object ProposalPersistence {
  final case class Summary(label: String, score: Int)
  final case class Proposal(id: EntityId, originalProposal: String, payload: Json,
    evidence: Record, labels: Vector[String], summary: Summary,
    revision: Long, permission: String, contentBody: ContentBody)

  val collection: EntityCollectionId = EntityCollectionId("textus", "workbench", "candidate")
  private val _summary_encoder: Encoder[Summary] =
    Encoder.forProduct2("label", "score")(x => (x.label, x.score))
  private val _summary_decoder: Decoder[Summary] =
    Decoder.forProduct2("label", "score")(Summary.apply)
  private val _summary_binding = EntityStoreFieldBinding.application[Summary]("summary-v1")(
    using summon[ClassTag[Summary]], _summary_encoder, _summary_decoder)
  val mapping: EntityStoreRowMapping = EntityStoreRowMapping(Vector(
    EntityStoreField("originalProposal", "original_proposal", ValueDomain(XString, Multiplicity.One), EntityStoreFieldBinding.string),
    EntityStoreField("payload", "payload_json", ValueDomain(XJson, Multiplicity.One), EntityStoreFieldBinding.json),
    EntityStoreField("evidence", "evidence_json", ValueDomain(XSemiStructuredData, Multiplicity.ZeroOne), EntityStoreFieldBinding.semiStructuredData),
    EntityStoreField("labels", "labels_json", ValueDomain(XString, Multiplicity.ZeroMore), EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)),
    EntityStoreField("summary", "summary_json", ValueDomain(DataType.Named(summon[ClassTag[Summary]].runtimeClass.getName), Multiplicity.One), _summary_binding)
  ), physicalFields = Set("id", "revision", "permission", "contentBody"))

  object Persistent extends EntityPersistent[Proposal] {
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
    def id(e: Proposal): EntityId = e.id
    def toRecord(e: Proposal): Record = Record.createFull(Vector(
      "id" -> e.id.value, "originalProposal" -> e.originalProposal,
      "payload" -> e.payload, "evidence" -> e.evidence, "labels" -> e.labels,
      "summary" -> e.summary, "revision" -> e.revision,
      "permission" -> e.permission, "contentBody" -> e.contentBody))
    def fromRecord(r: Record): Consequence[Proposal] = EntityId.createC(r).map { id =>
      Proposal(id, _value(r, "originalProposal").asInstanceOf[String],
        _value(r, "payload").asInstanceOf[Json], _value(r, "evidence").asInstanceOf[Record],
        _value(r, "labels").asInstanceOf[Vector[String]], _value(r, "summary").asInstanceOf[Summary],
        _value(r, "revision").asInstanceOf[Long], _value(r, "permission").asInstanceOf[String],
        _value(r, "contentBody").asInstanceOf[ContentBody])
    }
    private def _value(record: Record, key: String): Any =
      record.fields.find(_.key == key).get.value.single
  }

  def roundtrip(entity: Proposal): Consequence[Proposal] = {
    Persistent.toStoreRecordC(collection, entity).flatMap(Persistent.fromStoreRecordC(collection, _))
  }
}
```

Whitelisted framework fields pass through unchanged. This example does not
authorize updating managed fields. Actual entity admission still owns domain
constraints and policies; its reader must handle the declared optional presence
it admits. This complete-row fixture supplies evidence explicitly.

## Executable authoring and retained continuation

[EntityPersistentStoreMappingSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityPersistentStoreMappingSpec.scala)
authors STAGE-01–STAGE-12: five-field typed roundtrip; strict metadata gate;
logical/physical hook separation; split admission/final decode; create forwarding;
raw Update presence; raw structural rejection; causal physical failures;
safe logical-hook failures; legacy collection compatibility; null/empty presence;
and public exact String PBT with at least 200 successful cases requested.
All twelve stage scenarios and the public String property passed in the selected
five-suite command: 61/61 tests, SBT/wrapper exit 0 and serial lock released.
Complete eleven-path independent protected accumulator review
`tkw-cncf-persistence-stage-step-review-20261005@1` is PASS. Prior row and
pure-codec evidence is retained separately. Current native scalar and generation
validation passed 85/85 tests across eight suites; complete eighteen-path
independent protected review remains pending, with
its twelve public behavior groups authored in
[EntityStoreNativeScalarMappingSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityStoreNativeScalarMappingSpec.scala).

Required continuation includes all seven StandardEntityStore create, read/save,
update, record, lifecycle/query, conditional and revision parts; runtime
Collection/seed forwarding; SqlDataStore physical reads/defaults; SimpleModeler
and Cozy generation; generated write/read/restart coverage; both native
Workbench fixtures; recovery; and each project's separate acceptance. Existing
builds, dependencies, launchers, providers, fixtures, Phase records and source
inventory remain outside this producer pass.

## Standard EntityStore activation — 2026-10-18

The migration is source-only; the user confirms no existing Workbench DB is
being transferred. Validation and recovery use retained fixtures and test-owned
stores. No live data cutover or historical Phase 1 acceptance is implied.

StandardEntityStore selects the strict stages whenever the persistent supplies
a row declaration. None retains the explicitly undeclared physical adapter
contract; it never fabricates a typed mapping. Null metadata enters the strict
failure gate. This compatibility branch performs no provider content inference.
Create/full save/full update, raw partial updates, conditional successor/root
writes, ordinary reads, embedded/detached snapshots and authoritative mutation
readback share this selection. Store companion helpers preserve exact collection
checks after typed decode. Already admitted logical rows are not decoded twice.

Encoding precedes managed default/complement, ContentBody preparation and physical
provider mutation. Partial encoding normalizes native Update markers once.
Hydration precedes declared decode/admission; revision carrier interpretation and
final logical decode retain their existing order. Physical internal revision,
authorization and overflow records do not acquire automatic application decoding.

Generated declarations whitelist the known StandardEntityStore management and
ContentBody physical names, excluding names already explicitly mapped. This
closed producer list is independent of incoming rows. Unknown application fields
remain errors; native revision/security/ContentBody ownership remains unchanged.

Mapped search applies access/lifecycle guards on physical rows, hydrates and
admits declared logical rows, applies the original logical query, and finally
decodes admitted matches. User predicates and sorting are not pushed against
serialized structured values. Logical sorting/paging retains the existing
Query semantics and total-count behavior. This chooses correctness over query
optimization, which is outside Phase 100. Undeclared compatibility search keeps
its old physical field-name path.

SQL TEXT readback returns JDBC Strings verbatim for every collection and field.
No payload-shape parser or Job/quota exception participates. Declared codecs
separately parse JSON and restore application/Record/Json/array values. Existing
application-owned physical adapters remain responsible for their explicit codecs.
