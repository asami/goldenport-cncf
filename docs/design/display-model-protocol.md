# Display Model protocol design

Document role: selected PHASE-96 physical read boundary.
Status: pure P96-04 codec accepted at local Step commit `625d533`; P96-05A
typed adapter focused execution and independent acceptance are complete in the
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance).
P96-05 local Step commit remains PREPARED/PENDING; later Phase gates remain pending.

The authoritative behavior is [display-model-protocol.md](../spec/display-model-protocol.md)
R1..R9, paired with the [example suite](../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecSpec.scala)
and [property suite](../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecPropertySpec.scala).
[Runtime design](display-model-runtime.md) retains contextual source and projection
responsibility. [Generated ABI](../spec/logical-ui-generated-abi.md) retains static
semantic admission.

## Trusted restoration boundary

The application supplies an admitted immutable Source and the already-adopted
component definitions. The codec transports a resource-specific snapshot and
restores it through Display.createC. Received identifiers and metadata describe
that trusted selection; they do not reconstruct a provider, select a semantic
View, grant authorization, or execute an inert Action. This keeps semantic data,
generated definition authority, contextual source access and client realization
separate. No domain objects or Flutter objects cross the physical boundary.

E1 uses the existing actual generated consumer through normal bootstrap/query/read.
The component-package Test-only bridge returns the stored Option directly, so
restoration demonstrates original Option, wrapper, vector and selected definition
identity without changing production visibility. Provider counts and Browser call
vectors observe that bridge and codec calls stay idle.

## Representation rationale

Precise decimal strings preserve coefficient and signed scale independently of
client floating-point capabilities. Explicit sign/mantissa and BigInt scale
construction covers the full Int scale domain without assuming platform parser
limits or expanding large exponents to plain decimal text. Instant text retains
the original instant and nanos. Canonical JSON escaping acts on Java UTF-16
units, including lone surrogates, so representation is deterministic without
Unicode normalization. EntityId/EntityRevision remain owned by their existing
datatypes; the codec does not invent an identity grammar.

The five-kind fixture is independently admitted with its own provenance. It
extends behavior proof without attributing number/boolean/timestamp/missing
definitions to the actual Cozy text-only fixture. Actual goldens retain the
delivered producer's provenance and are physical test resources. Samples and
receipt history belong to the [protocol journal](../journal/2026/10/2026-10-04-phase-96-protocol-contract.md).

## Failure precedence

Strict Jawn duplicate-key parsing precedes trusted compatibility checks. The
frozen sequential restoration order makes the first structural or compatibility
defect deterministic; this pure parser dependency does not change applicative
semantic request validation elsewhere. Fixed typed redacted diagnostics protect
the public rejection boundary from parser or input text. Successful structural
construction delegates semantic correspondence to Display.createC, retaining
its original structured failure rather than masking it.

## Transport and future responsibility

The codec owns the business payload only. Existing canonical REST selection,
JSON format selection and CNCF envelope/error behavior remain authoritative.
P96-05 owns normal Operation registration and the formatter/adaptor that inserts
the payload as an object into data; this pass supplies no live HTTP proof.
TFC owns client transport/runtime, TFAF realization, Editing Studio Phase 2
application semantics and app Phase 5 aggregate/E2E acceptance.

The first read snapshot subset does not decide later mutation, Business
Operation descriptor or context artifact schemas. P96-06/08/10 retain those
explicit schema/version decisions and P96-11 retains final handoff and closure.

## P96-05A: Typed business JSON and framework metadata

[Protocol R9](../spec/display-model-protocol.md#r9-pure-typed-json-and-framework-envelope-adapter)
shares the existing pure guarded payload builder between encodeJsonC and canonical
encodeC. Returning typed Json avoids text-as-Scalar quoting and generic semantic
source-to-Record conversion. The unchanged v1 schema, trusted source, semantic
metadata and canonical text renderer remain authoritative; there is no second
payload encoder or re-admission route.

The existing formatter honors output-format precedence using the recognized suffix
of the retained HTTP source's final path segment: explicit request format first,
then HTTP suffix, then configured/mode fallback. This transport responsibility
preserves canonical route/domain/schema ownership and the existing `.json` fixture
contract without adding a format property or changing fixture RunMode. Absent or
unsupported suffixes and non-HTTP sources retain ordinary selection. The selected
JSON envelope/inline-debug branch uses existing helpers for
execution/job/continuation/debug metadata, encoded
as framework-only Record with null placeholder data. Parsing that trusted metadata
once and replacing data with the original Json preserves framework root contracts
without sending business JSON through locale/property-name Record transforms.
AcceptedJob replaces data with Json.Null only from explicit runtime response kind.
Generated metadata parse failure is a fixed redacted internal IllegalStateException;
a fallback success or caller-input error would conceal a framework defect.

Circe Printer.noSpaces with escapeNonAscii=true escapes every UTF16 unit,
including lone surrogates. This prevents UTF8 replacement from changing a legal
Java String at HTTP body encoding. It preserves business semantics; it does not
replace the separate P96-04 exact canonical-text renderer. Data-only Json and
non-json selection keep baseline behavior, explicit format precedence stays
ordinary, and no alternate format conversion is designed here.

[Envelope E1..E4](../../src/test/scala/org/goldenport/cncf/protocol/OperationResponseJsonEnvelopeSpec.scala),
[public Read E2/E3/E8](../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadSpec.scala)
and [public property E1](../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadPropertySpec.scala)
bind this boundary to R3/R4/R7/R9 and runtime SR3; Envelope E4 also proves existing
output-format 6/7 and path-resolution R4 precedence and fallback compatibility.
Actual in-process Http4s routing has focused execution and independent acceptance,
recorded in the
[journal acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance).
Current subject13 passed 14/14 tests in two suites after the internal naming
correction; subject12's 266/266 tests in 32 suites remain the retained baseline,
not a fresh rerun on renamed source. Whole-Step review and fresh independent
focused PASS support the scoped read adapter acceptance in ledger revision 28.
This is not socket/live-server E2E proof. P96-05 local Step commit is
PREPARED/PENDING; planned commands, failed checkpoints and permission history
retain their historical role, and later Phase/Cozy/app gates remain pending.
