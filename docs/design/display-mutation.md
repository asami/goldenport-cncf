# Display Mutation design

Document role: selected PHASE-96 / P96-06 architecture decisions.
Status: complete P96-06 A+B focused validation and independent acceptance of all
five unchanged criteria are recorded in the
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-step-acceptance).
Current subject4 passed 5/5 representative and 98/98 accumulator tests; original
whole-Step review plus independent descriptor-focused PASS support acceptance.
The local Step commit remains PENDING.
The [contract](../spec/display-mutation.md) fixes behavior; the existing
[runtime design](display-model-runtime.md) continues to own semantic authority.

## Pure typed preparation

One immutable generic Binding[P] holds explicit server-owned field registrations
and canonical Entity/Aggregate target identities. P remains the application's
typed canonical mutation input. The development fixture uses Patch(title,
description), with independent all-kind NativePatch candidates; neither is
production Entity admission. There is no erased Any bridge, raw wire Record
fallback, inverse discovery, client property path or new domain language.

Admission compiles registrations into Detail declaration order. Preparation
validates the entire requested set before invoking any converter, then applies
only selected callbacks once and invokes the domain adapter once. Aliases may
exist in the registry, but a request selecting the same canonical target twice
fails before conversion. This avoids last-wins behavior while retaining explicit
application mapping. Unbound fields remain readonly, including derived fields.
Existing WebColumn hints survive as inert editor metadata; domain constructors
and schema validation retain authority even when hints are permissive.

Capabilities, availability and current readonly veto are separate values. They
cannot confer permission. Request shape/selection precede one per-call authorizer;
authorization/domain failures preserve their original Conclusions. Application
callbacks are explicitly required to be pure; the framework cannot certify
arbitrary closure purity. A NonFatal exception, null Consequence or null output
becomes fixed redacted CALLBACK_INVALID and stops later callbacks. Pure fixture
counters observe invocation order only, not writes or transactions.

## Private admitted values

Candidate DTOs may be copied; Binding, Descriptor, Prepared, PreparedDelete,
Metadata, Receipt and Result use private constructors and no case-class copy or
public apply bypass. Descriptor belongs to DisplayMutationBinding so only its
Binding factory creates admitted descriptors. Result factories recheck every
ReloadedDisplay candidate, including copies. Public type probes are lexically
external in the logicalui fixture.

## Existing policy ownership

The ordered-header adapter uses EntityRevisionTransport's canonical parser and
property name and EntityMutationExecutionPolicy.validateC. It retains duplicate
If-Match evidence and requires exact canonical strong-tag round trips. Update
uses Optimistic/WriteIfChanged/ObservedRequired; delete uses
Optimistic/AlwaysWrite/ObservedRequired. No non-Display defaults are changed.

Current resource comparison in preparation is a pure guard, never permission to
perform an atomic write. P96-07 owns authorized canonical comparison/write,
UnitOfWork/provider capability, strict atomic delete, lifecycle and authoritative
receipt acquisition. Delete preparation only checks the explicit capability flag.
No method introduced here invokes View lookup, a writer, datastore or transaction.

## Source-bearing results

Display remains the admitted definition/model pair. ReloadedDisplay(source,
display) adds an explicit server-side candidate witness without changing Display
or read-v1. Logical source comparison uses componentId.name/resourceType; Selection
also compares all definition/provenance/mutation revisions. Source reference
allocation must not alter correspondence. The candidate label is representational:
P96-07 must supply the actual selected canonical contextual source with its read,
and may not stamp the requested source onto arbitrary foreign cached data.

Receipt validates supplied canonical outcome/identity/revision relationships;
it does not execute or prove a commit. Direct createC rejects foreign/null/wrong
Ready candidates structurally. After an admitted supplied Receipt, completeC
reloads once: exact correspondence is Ready; correct source/definition/identity
with a different revision is ViewNotCurrent; other invalid or failed reloads are
ProjectionUnavailable. Deleted reloads zero times. Every committed branch retains
the immutable receipt and retryCommand=false. Canonical indeterminate failure
remains a failure with no invented receipt and no automatic replay.

## Standalone pure serialization boundary

The physical API and admission behavior are fixed by
[R5 of the mutation contract](../spec/display-mutation.md#r5-standalone-p96-06b-physical-schemas-and-codec-admission).
DisplayMutationProtocol names the three standalone schemas; DisplayMutationCodec
is the only public codec facade. Package-private DisplayMutationJson owns strict
shared primitives and UTF16 rendering, while DisplayMutationRequestCodec,
DisplayMutationDescriptorCodec and DisplayMutationResultCodec own their respective
branches. Each production file stays within the frozen 400-line limit.

Request parsing supplies a typed candidate for later Binding preparation.
Descriptor parsing regenerates the observable projection through the supplied
Binding, preserving that Binding's WebColumn objects and callbacks. Availability
reconstruction retains bound readonly veto; intrinsic unbound readonly status
needs no serialized readonly set. Metadata contains no inverse target,
confidentiality override or execution authority. Canonical scalar checks precede
observable comparison, preventing numeric normalization from becoming admission.

Result parsing first uses the existing receipt factory with the original request
and observation, then uses the unchanged read codec for nested Ready. Only a
successfully source-validated inner Display becomes a ReloadedDisplay candidate
for the Result factory. The encoder likewise uses the admitted snapshot's source
and Display through the existing read encoder. Outer and inner source boundaries
are independent; neither relabels unvalidated data. This split changes no
read-v1 bytes, members, revision representation or definition authority.

Behavior is documented by the external
[codec fixture](../../src/test/scala/logicalui/DisplayMutationCodecFixture.scala)
and [request](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationRequestCodecSpec.scala),
[descriptor](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationDescriptorCodecSpec.scala),
[result](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationResultCodecSpec.scala)
and [generated property](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationCodecPropertySpec.scala)
specifications. These pure representations and traces provide no transaction or
production lifecycle evidence.

## Remaining ownership

Complete P96-06 A+B, including nested unchanged read-v1 decoding before
ReloadedDisplay construction, is focused validated and independently accepted,
as recorded in the [Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-step-acceptance).
Its local Step commit remains PENDING. P96-07 freezes ordinary
route/descriptor exposure and proves canonical atomic write/contextual reload.
ESS unsupported create/delete is retained; the positive-contract fixture is pure
and cannot replace P96-07 positive lifecycle proof or actual selected contextual
reload provenance. Phase/application aggregate gates remain
separate. Historical Hygiene records and producer artifacts retain their owners.
