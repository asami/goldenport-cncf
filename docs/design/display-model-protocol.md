# Display Model protocol design

Document role: selected PHASE-96 physical read boundary.
Status: accepted for implementation; codec execution and independent acceptance pending.

The authoritative behavior is [display-model-protocol.md](../spec/display-model-protocol.md)
R1..R8, paired with the [example suite](../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecSpec.scala)
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
