# Display Mutation contract

Document role: normative PHASE-96 / P96-06 contract.
Status: complete P96-06 A+B focused validation and independent acceptance of all
five unchanged criteria are recorded in the
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-step-acceptance).
Current subject4 passed 5/5 representative and 98/98 accumulator tests; original
whole-Step review plus independent descriptor-focused PASS support acceptance.
The local Step commit remains PENDING.
P96-07 canonical write/route proofs remain required.

Architectural context is described in the [mutation design](../design/display-mutation.md).
That reference provides context; this contract owns behavior.

## R1: Typed API and admission

The package is `org.goldenport.cncf.display`. `DisplayMutation` declares closed
`Operation(Create, Update, Delete)`, `Support(Supported, Unsupported)`,
`AvailabilityState(Enabled, Disabled, Hidden)` and `FieldEditability(Editable,
ReadOnly)`. `Capabilities` and `Availability` have read/query/create/update/delete
members; Availability also has `readonlyFields: Set[FieldLocator]` and `enabled`.
`DefinitionReference(screenId, definitionRevision, mutationRevision, provenance)`
and `Selection(source: DisplayProtocol.Source, definition)` distinguish all
identity/revision spaces. `FieldLocator(sectionId, fieldId)` is screen-scoped.
`FieldInput(location, value: DisplayValue)` carries one of the existing five
native branches. Request is closed: `Create(selection, fields)`,
`Update(selection, resourceId: EntityId, changes)`, `Delete(selection, resourceId)`.
Observed/expected revision is never a Request business field.

`DisplayMutationBinding.UpdateTarget.Entity(ownerId, fieldId)` and
`Aggregate(ownerId, fieldId)` are opaque server registration identities.
`FieldBinding[P](location, bindingId, target, acceptedKinds, web, convert)` has
`convert: (P, DisplayValue) => Consequence[P]`. `admitC[P](source, definitions,
screenId, mutationRevision, capabilities, fields, managedTargets, validateDomain)`
returns a privately constructed immutable `Binding[P]`. The domain validator is
`P => Consequence[P]`; P remains application-owned canonical typed input.
Admission selects a declared Detail, checks every registration and container
before callbacks, validates unique scoped locations/binding IDs, explicit targets
outside managedTargets, and nonempty distinct acceptedKinds within declared kinds.
Readonly/system WebColumn cannot register an editable field. Hints retain their
values/order and valid option/range shape; they grant neither permission nor
domain validity. Read binding IDs never infer inverse mappings.

Binding exposes selection, capabilities, definitions and:

```scala
descriptorC(availability): Consequence[DisplayMutationBinding.Descriptor]
prepareCreateC(request: Request.Create, seed: P, availability, authorize): Consequence[Prepared[P]]
prepareUpdateC(request: Request.Update, seed: P, currentResource, transport, availability, authorize): Consequence[Prepared[P]]
prepareDeleteC(request: Request.Delete, currentResource, transport, availability, atomicDeleteAvailable, authorize): Consequence[PreparedDelete]
```

Descriptor, Prepared and PreparedDelete are final non-case classes with private
construction and no copy/apply bypass. Descriptor preserves every declared field
in section/field order. Unbound fields are ReadOnly with no bindingId, declared
kinds and `WebColumn.empty.copy(readonly = true)`; current readonlyFields veto
bound editability. Unknown/null readonly locations reject. Prepared contains
selection, operation, input, optional resourceId/observedRevision/executionPolicy,
and declaration-ordered targets/bindingIds. PreparedDelete contains selection,
resourceId, observedRevision and strict executionPolicy.

Shape/selection checks precede exactly one authorization callback; original
authorization failures survive. Support and current availability are distinct.
Update/delete require matching transport operation and strict policy, matching
resource identity and current observed revision before conversion. This is only
a preparation guard: P96-07 must repeat comparison atomically in the canonical
transaction/provider. Entire field vectors are checked before converters: create
may be empty, update must be nonempty; duplicate/unknown/readonly/managed fields,
aliased targets selected twice, wrong kinds and null native payloads reject.
Converters run once in declaration order, then the domain validator once.
Text UTF16 units, decimal coefficient/scale, Instant nanos and explicitly admitted
Missing survive without coercion. Structured callback failures are preserved;
NonFatal/null result/null output yields fixed redacted CALLBACK_INVALID and stops
later callbacks. Delete runs no converter/domain callback and requires explicit
atomicDeleteAvailable. No preparation calls a store, View, writer or transaction.

## R2: Logical source and definition correspondence

Guard all shapes before comparison. Source correspondence compares
`componentId.name` and `resourceType`, not Source reference/equality. Selection
also compares screenId, definitionRevision, mutationRevision and every provenance
field (producerId/sourceId/revision). Independently allocated equal logical
Sources correspond. Different component/resource values reject even with otherwise
identical definitions/resources. No identity grammar or normalization is added.

## R3: Transport and failures

`DisplayMutationTransport.bindC(operation, headers: Vector[(String,String)])`
returns immutable private Metadata(operation, observedRevision, properties,
executionPolicy). Ordered real-header tuples retain duplicate evidence. Header
names obey HTTP token shape; matching If-Match is case insensitive. Create rejects
any If-Match and returns None/Record.empty/None. Update/delete require exactly one
strong canonical `"revision-N"`, parsed only by EntityRevisionTransport, with exact
entityTag round-trip after surrounding HTTP whitespace. Missing uses argumentMissing;
duplicates, malformed/weak/wildcard/list/leading-zero/nonpositive/overflow tags use
argumentFormatError. One existing observedRevisionPropertyName is upserted.
Update policy is Optimistic/WriteIfChanged/ObservedRequired/Some(observed); delete
is Optimistic/AlwaysWrite/ObservedRequired/Some(observed), validated by the existing
policy. Other adapters/defaults and live HTTP integration remain unchanged.

`inputInvalidC[A](code,path)` owns argumentInvalid and MutationException(Diagnostic)
with DSP96_MUTATION_ prefix and fixed redacted reasons. Codes are ELEMENT_INVALID,
ID_INVALID, SELECTION_MISMATCH, DEFINITION_MISMATCH, FIELD_DUPLICATE, FIELD_UNKNOWN,
FIELD_READ_ONLY, VALUE_KIND_INVALID, VALUE_INVALID, BINDING_DUPLICATE,
TARGET_DUPLICATE, TARGET_MANAGED and CALLBACK_INVALID. Unsupported/unavailable/
atomic-delete utilities use operationInvalid with Reason/Capability facets.
revisionConflictC uses operationConflict with expected/actual facets. Input values
and throwable messages never appear in diagnostics; domain Conclusions survive.

## R4: Receipts and projection

`DisplayMutationResult` declares OutcomeKind(Created, Updated, Unchanged, Deleted),
PendingReason(ViewNotCurrent, ProjectionUnavailable), candidate
`ReloadedDisplay(source, display)`, and ProjectionState.Ready(snapshot)/Pending(reason).
`receiptC(expectedSelection, request, kind, resource, observedRevision)` admits a
private immutable Receipt(selection, kind, resource) from supplied authoritative
canonical values. It does not prove a commit. Created requires no observation and
INITIAL1; Updated requires requested ID and observed.nextC; Unchanged/Deleted use
the same ID and observed revision. Deleted carries the last atomically matched
revision, never a guessed/tombstone increment. Empty framework markers are no proof.

`createC(receipt, projection)` returns private Result with retryCommand=false;
reloadRequired is true for pending/deleted. Deleted requires None; other outcomes
require Some Ready/Pending. Direct Ready candidates require nonnull snapshot/source/
display, logical source correspondence first, then exact Detail definition/provenance,
ID and committed revision. Foreign source rejects with SELECTION_MISMATCH at
projection.snapshot.source.componentId/resourceType; nulls use ELEMENT_INVALID at
their exact static paths. Existing Display/read-v1 carry no added source member.

`completeC(receipt, reload: () => Consequence[ReloadedDisplay])` checks callback even
for Deleted, then reloads zero times for Deleted or exactly once otherwise.
Exact correspondence yields Ready; correct source/definition/ID with revision lag
yields ViewNotCurrent; foreign source, wrong definition/shape/ID, Failure, NonFatal
or null yields ProjectionUnavailable. Every postcommit branch preserves Receipt
and forbids replay. A candidate source label proves no query provenance: P96-07
must supply the actual selected canonical contextual reload Source, never relabel
foreign data. `indeterminateC[A]()` delegates existing transactionIndeterminate,
produces no Receipt and requires authoritative read without automatic replay.

## R5: Standalone P96-06B physical schemas and codec admission

All schemas have version 1 and strict duplicate-reject Jawn parsing, exact member
sets, explicit canonical scalars, no nulls/unknown members/automatic derivation.
`cncf.display-mutation.v1` common members: schema,version,resourceType,definition,
provenance. definition is screenId,revision,mutationRevision; provenance is
producerId,sourceId,revision. Trusted route Source supplies componentId; route
selects operation. Create adds fields; Update adds resourceId,changes (nonempty);
Delete adds resourceId only. Entries are sectionId,fieldId,value. No observed or
expected revision, raw target/path/converter/Operation is allowed. EntityId uses
its canonical factory and exact encoded equality. Values retain existing read
encoding: text/text, number/canonical decimal string preserving scale,
boolean/JSON Boolean, timestamp/canonical Instant text preserving nanos, missing
with no payload. Arrays preserve caller order; preparation uses declaration order.

`cncf.display-mutation-descriptor.v1` root: schema,version,componentId,resourceType,
definition,provenance,capabilities,availability,fields. Capability tags are
supported/unsupported; availability tags enabled/disabled/hidden, each with
read/query/create/update/delete. Fields are declaration-ordered sectionId,fieldId,
editability,bindingId (optional explicit registration only),acceptedKinds,web.
Editability tags are editable/read-only; kinds are the existing five tags.
Web required members: hidden,system,readonly,multiple,values,validation; optional
controlType,required,placeholder,help. Validation optional members: min,max,step
canonical decimal strings, minLength,maxLength nonnegative integral values,pattern.
Encoder validates the supplied Descriptor's logical selection, capabilities and
fields against `binding.descriptorC(descriptor.availability)`. Decoder checks
every canonical metadata primitive and member set, reconstructs Availability
with `readonlyFields` containing only bound fields marked read-only, then calls
the supplied `binding.descriptorC` and compares every observable ordered field,
kind, bindingId, WebColumn property and capability. It never invokes authorization,
converter or domain callbacks. Bound read-only fields retain explicit bindingId;
unbound fields have None and intrinsic read-only status. Redundant unbound
readonlyFields membership is not an observable wire distinction. Supplied
WebColumn objects, including confidentiality, remain Binding-owned; confidentiality
is neither exposed nor overwritten. Metadata cannot expose targets, callbacks,
context or runtime authorization grants. Hint relationships remain the admitted
Binding's responsibility, rather than a new public schema validator.

`cncf.display-mutation-result.v1` root: schema,version,componentId,resourceType,
definition,provenance,outcome,resource,projection,retryCommand(false). Outcome tags
created/updated/unchanged/deleted; resource is entityId,revision (positive integral).
Ready projection is type=ready,display (unchanged nested read-v1); pending is
type=pending,reason (view-not-current/projection-unavailable); deleted is
type=reload-list only. Failure/indeterminate use existing failure envelope.
`DisplayMutationProtocol` exposes `requestSchema`, `descriptorSchema`,
`resultSchema` and `version=1`. Only `DisplayMutationCodec` is a new public codec
facade; all methods preserve typed inputs and Consequence results:

```scala
encodeRequestC(request: Request): Consequence[String]
decodeRequestC(expectedSelection: Selection, operation: Operation, json: String): Consequence[Request]
encodeDescriptorC[P](binding: Binding[P], descriptor: Descriptor): Consequence[String]
decodeDescriptorC[P](binding: Binding[P], json: String): Consequence[Descriptor]
encodeResultC(result: Result): Consequence[String]
decodeResultC(expectedSelection: Selection, definitions: AdmittedDefinitions,
  request: Request, observedRevision: Option[EntityRevision], json: String): Consequence[Result]
```

Private package objects DisplayMutationJson, DisplayMutationRequestCodec,
DisplayMutationDescriptorCodec and DisplayMutationResultCodec split strict shared
primitives and the three branches. No public Json, Record, Any or writer API is
introduced. Request admission verifies nonnull selection/native payload shape,
unique scoped locations, nonempty Update, exact definition/provenance/mutation
revision and canonical EntityId before returning a candidate. Preparation still
owns writable registration and domain admission; shape admission supplies no
binding or authorization.

JSON version is exactly the integer literal `1`; `1.0`, `1e0`, string and Boolean
forms reject. Outer result revision is a canonical positive Long integer literal
through Long.MaxValue, with no fractional, exponent, string or overflow form.
Hint lengths are canonical nonnegative Int integer literals. Hint decimal values
remain canonical strings. The nested unchanged read-v1 resource revision remains
a string. Number admission verifies lexical canonical spelling, not numeric
equality or `toBigIntExact` alone. Decimal strings preserve coefficient and scale
via java.math.BigDecimal.toString and integer coefficient/scale construction,
including full Int scale endpoints. Instant strings round trip through canonical
Instant.toString with exact nanos. Deterministic rendering escapes every UTF16
unit, including non-ASCII and isolated surrogates, without charset normalization.

Result decode checks outer source and complete selection, canonical resource,
closed outcome and exactly Boolean false retryCommand before calling the existing
receiptC with the supplied original Request and observedRevision. It then admits
the projection branch and calls createC; Deleted requires exact reload-list/None,
and other outcomes require Ready/Pending. It never executes a writer or reload.
Decode nested Ready through unchanged DisplayProtocolCodec.decodeC with trusted
Source and definitions before constructing ReloadedDisplay. Foreign nested source
must reject even with identical definition/ID/revision; encoding uses admitted
snapshot.source/display. No read-v1 member or version changes.

Strict duplicate-reject parsing applies at every applicable object level.
Malformed/null/missing/unknown/incorrect primitives and unsupported tags reject
through existing DisplayMutation.inputInvalidC categories with fixed redacted
member/index paths. Parser/scalar NonFatal exceptions become structured fixed
diagnostics; downstream structured failures, including nested read codec failures,
retain their taxonomy and cause. No new failure envelope is introduced.

P96-07 owns ordinary route registration and descriptor exposure alongside reads,
to be frozen at its PLAN boundary without changing read-v1 bytes or member sets.
These standalone objects are no proof of production write, contextual reload
provenance or positive create/delete lifecycle.

## R6: Executable specification map

ContractSpec M1..M7/TYPE, TransportSpec T1..T3, ResultSpec R1..R4 and
ContractPropertySpec P1..P4 document this contract. Properties require at least
100 successes and zero discards. ESS typed Patch is development-only; its
read/query/update support does not imply create/delete lifecycle proof. Separate
positive-contract fixtures also remain pure. A authoring alone closes no Step;
P96-07 write proof remains required. Historical A readiness
`phase96-p96-06a-readiness-001@1` records
accepted representative004 8/8 and accumulator002 57/57 focused results on subject
revision 4, including executed P1..P4 minimum-100 and zero-discard assertions.
Those results are A readiness evidence and do not accept the Step or B.

The authored B specifications are:

| Example / matrix | Executable specification | Behavior |
| --- | --- | --- |
| E9 / RQ1..RQ4, TYPE | [DisplayMutationRequestCodecSpec](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationRequestCodecSpec.scala) | External operation, exact native request/scalar/selection admission and external typed/private probes |
| E10 / D1..D3 | [DisplayMutationDescriptorCodecSpec](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationDescriptorCodecSpec.scala) | Binding-authoritative ordered inert metadata and current bound readonly veto |
| E11 / RS1..RS4 | [DisplayMutationResultCodecSpec](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationResultCodecSpec.scala) | Original-request receipts, Ready inner source admission, Pending/Deleted and no fabricated failure success |
| E12 / BP1..BP4 | [DisplayMutationCodecPropertySpec](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationCodecPropertySpec.scala) | Total generated native request, descriptor, receipt/projection and independent tampering properties |

Every B behavior has adjacent Given/When/Then and should matchers. BP1..BP4 select
ScalaCheck default.withMinSuccessfulTests(100), require passed=true,
succeeded>=100 and discarded=0, and use total bounded generators without filters
or an invalid maximum-discard override. The
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-step-acceptance)
records current subject4 5/5 representative and 98/98 fourteen-suite accumulator
success, including executed P1..P4/BP1..BP4 minimum-100/zero-discard gates.
Original whole A+B review and independent descriptor-focused PASS in ledger
revision 31 support all five unchanged criteria; the local Step commit remains
PENDING. B subject3 8/8 plus 97/97 and A readiness retain their historical subjects.
