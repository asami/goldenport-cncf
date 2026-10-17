# Display Presentation Context

Document role: normative P96-10 contract for optional client-safe presentation
context. Connected P96-10 A+B, including pure values/wire and normal Operation
admission, restoration, projection and continuation, is independently accepted
on all four original Step criteria through original whole-Step review
`phase96-p96-10-step-review-001@1` and fresh focused closure
`phase96-p10-step-repair-focused-result-001@1` in review ledger revision 40.
The [Step acceptance record](../journal/2026/10/2026-10-17-phase-96-presentation-context.md#p96-10-step-acceptance-2026-10-17)
supersedes the historical authoring/pending-acceptance correspondence below.
This records Step acceptance only, not whole-Phase or consumer acceptance;
R1..R8, examples, schema and ABI semantics remain unchanged.

This is the explicit optional-context amendment to
[Display protocol v1](display-model-protocol.md) R2/R8. The sole added Display
root member is `presentationContext`, after `model` when present. The protocol
schema/version, generated ABI, Phase 74 semantic descriptor, source, provenance,
definition and model contracts are unchanged. Earlier read-subset exclusions
remain historical scope statements; this document governs the new optional
member only. [Design](../design/display-presentation-context.md) owns the
responsibility boundary; [history](../journal/2026/10/2026-10-17-phase-96-presentation-context.md)
records the decision chronology. The
[proposal](../notes/display-protocol-presentation-context-proposal.md) remains
non-normative history.

## R1: Pure inert values

`PresentationContext` is a final immutable value with a private constructor and
no case-class copy bypass. Its public getters are `locale: Option[String]`,
`variant: Option[String]`, and `flow: Option[PresentationContext.Flow]`.
`createC(locale = None, variant = None, flow = None)` returns a validated
`Consequence[PresentationContext]`. Explicit empty context is valid support
negotiation. Absence is a different state; it is neither JSON null nor an
automatically inserted empty context.

Companion-owned final immutable `Flow` has a private constructor and getters
`displayInstance: String`, `correlation: String`, `expiresAt: Instant`.
`Flow.createC(displayInstance, correlation, expiresAt)` returns a validated
`Consequence[Flow]`. Factories reject null Option containers, Some(null), null
strings and null Instant. No clock, I/O, lookup, evaluation or assignment occurs
in these values. Past and future Instants are equally representable. Claimed
expiry is not authorization proof. A variant without flow remains inert.

| Value | Required representation |
| --- | --- |
| locale | Exact canonical BCP47 language tag, including `und`, at most 128 UTF8 bytes; canonicalization must equal the supplied text |
| variant | Nonempty, exactly trimmed, control-free, well-formed Unicode, at most 128 UTF8 bytes |
| displayInstance / correlation | Nonempty, exactly trimmed, control-free, well-formed Unicode, each at most 256 UTF8 bytes; opaque and never parsed as another ID family |
| expiresAt | Nonnull Instant, transported as its exact canonical `Instant.toString`, preserving seconds and nanos |

No locale-sensitive system default, Unicode normalization, trimming, coercion
or reference interpretation is performed. Boundary whitespace and ISO control
characters are rejected. Validation failures carry a redacted typed
`PresentationContext.Diagnostic` through `ValidationException` and structured
`Consequence` with `Taxonomy.argumentInvalid`; invalid values are not thrown.

## R2: Exact physical wire

`PresentationContextCodec.encodeC/encodeJsonC/decodeC/decodeJsonC` are pure and
return Consequence values. Text encoding is compact deterministic JSON with
no trailing LF. The root is exactly `{version: 1, families: [...]}` in that
member order. Each family envelope is exactly `{name, version, data}` in that
member order, with string name, positive integral numeric version and object
data. All supported families are optional; the canonical encoder orders them
as follows regardless of input family order:

| Order | Name / family version | Closed ordered data members |
| --- | --- | --- |
| 1 | `cncf.locale` / 1 | `languageTag: string` |
| 2 | `cncf.presentation` / 1 | `variant: string` |
| 3 | `cncf.display-flow` / 1 | `displayInstance: string`, `correlation: string`, `expiresAt: string` |

Each supported v1 payload is strict: no missing/extra members, nulls, wrong
primitives, malformed strings or noncanonical Instant. A malformed supported
family fails the entire context; it cannot downgrade to optional absence.
There are no Experiment, Run, Arm, allocation, execution-plan, credentials,
security, RuntimeContext, UnitOfWork, provider or configuration members.

Context root version and family versions are independent of Display protocol
version, generated ABI, Phase 74 semantic contract, UI definition revision and
resource revision. An unsupported integral root version is a structured
compatibility failure. Wrong root-version primitive or nonintegral number is
malformed. Numerical representation does not turn a string into a version.

## R3: Bounded optional extensions and diagnostics

At most eight input envelopes are accepted, including discarded extensions.
Names must be namespaced lower-case ASCII dot-separated segments, each starting
with a letter and continuing with letters/digits or internal hyphen-separated
nonempty portions, at most 64 UTF8 bytes. Duplicate names are rejected before
supported-payload interpretation or filtering, including unknown names and
different positive versions of one name.

Unknown names and unsupported positive family versions are bounded inert
optional extensions: discard their data, never echo, execute or reinterpret it
as a supported version. Every such envelope must still be closed and valid,
with positive integral version and object data. Arbitrarily large integral
version magnitude must not cause exponent expansion; it remains unsupported
optional data. Nested unknown data remains subject to both limits below.

The complete standalone context is at most 8192 UTF8 bytes and eight nested
object/array containers, with the root object counting as depth one. The raw
string entry checks size and lexical container depth before parsing, honoring
string quoting and escapes. Strict Jawn parsing rejects duplicate object
members at every depth and malformed JSON. The decoded Json entry accounts
for compact JSON byte size and depth iteratively, without unbounded recursive
pre-rendering. Unknown data cannot bypass these limits. API null inputs fail
with a typed argument diagnostic.

`PresentationContextCodec.Diagnostic(code, path, reason)` is carried by
`CodecException`, returned through structured `Consequence.fail` with
`Taxonomy.argumentInvalid`. Reasons are fixed, redacted and contain no input,
parser message or nested Throwable. Paths start at `$` for standalone wire
members, or name the invalid API argument. Display delegates to this codec,
retaining its standalone context paths. No validation failure throws or uses a
string-only failure.

| Code prefix `DSP96_CONTEXT_` suffix | Meaning |
| --- | --- |
| INPUT_INVALID | Null API input |
| JSON_INVALID | Invalid syntax or duplicate JSON members |
| MEMBER_INVALID | Wrong closed member set, null, primitive or integral-version shape |
| VERSION_INCOMPATIBLE | Unsupported integral context root version |
| LIMIT_EXCEEDED | Byte/depth/family-count bound |
| NAME_INVALID | Invalid bounded namespaced name |
| DUPLICATE_FAMILY | Repeated family name before filtering |
| VALUE_INVALID | Invalid canonical supported payload scalar |

## R4: Display preservation

`Display.createC(definitions, model, presentationContext = None)` retains its
source-compatible first two parameters and original semantic validation. It
rejects null context Option/Some(null), and stores only a validated inert value.
`Display.withPresentationContextC(display, context)` attaches or removes that
data while preserving the exact original definition and model references,
without re-admitting definitions or performing runtime lookup.

The Display codec adds context only for Some; None retains byte-identical
existing no-context encoding and goldens. The old canonical Display text
renderer, including its UTF16-unit escaping and exact decimal handling, is
unchanged. Received context is strictly decoded and retained while definitions
are still supplied by the trusted caller. Existing source, metadata,
provenance, model and semantic failures retain their original diagnostics.
Null `presentationContext` is rejected, and all other Display root members
remain closed. The existing pure mutation-ready projection codec transitively
preserves nested Display context. This is supplied-value fidelity, not proof
of runtime continuation or mutation lifecycle execution.

## R5: Sources, authority, lifetime and propagation

The following mapping is the frozen normal-route contract for Slice B. Slice
A's inert codec does not implement or prove these runtime transitions.

| Family | Authoritative source / producer | Permitted readers / updater | Version / lifetime / propagation |
| --- | --- | --- | --- |
| cncf.locale | `ExecutionContext.core.locale`; CNCF safe projection | Client may render; runtime projection is the only authoritative updater; client input is a candidate | v1; one resolved invocation/flow presentation snapshot; only explicit validated continuation, never overwrite core authority from wire |
| cncf.presentation | Server-resolved immutable presentation flow; CNCF safe projection | Client/TFAF may select registered realization; only server resolver updates; app does not allocate/branch on experiments | v1; bound flow until server expiry; reproject from resolved state, never trust incoming variant |
| cncf.display-flow | Server-resolved Display Instance/correlation and server expiry | Client/Core retain and return exact opaque references; server validates/restores; no parsing into another ID family | v1; exact component/principal/session-bound flow until server expiry; retry retains accepted flow, unrelated/nested/cross-component calls require their own admission |

An opt-in client retains typed context and returns its canonical JSON as the
reserved Request property `cncf.presentationContext` or HTTP metadata
`X-CNCF-Presentation-Context`, never business payload. HTTP ingress must preserve
raw duplicate candidates across physical locations and reject duplicates;
`asMap`/last-wins or primitive `toString` coercion is not admission. Explicit
empty context requests support. Absence invokes no context provider and emits
no context. Missing provider/state, unknown or expired flow, or unsupported
optional flow clears presentation state and keeps the ordinary path, including
restart. Wire syntax/known-payload failures and provider exception/malformed
returns remain structured failures, before business execution.

After ordinary Operation authorization, normal canonical Request, constructed
Action and prepared ActionTask preparation must restore context before existing
evaluation admission and before `makeOperationRequest`/business execution. A
DisplayService-only hook or second dispatcher is insufficient. Authorization
denial calls neither presentation provider, evaluation resolver nor business.
Only server lookup, bound to server component, authenticated principal, session,
exact opaque references and server expiry, determines resolved output.
Incoming locale, variant and expiry cannot create authority. Inherited
ScopeContext capability defaults disabled. Immutable CncfCore presentation
state contains only safe resolved data, with no provider/store handles or app
configuration.

Each subsequent Operation has its own logical execution and attempt. Existing
[evaluation admission R1..R9](operation-evaluation-capture.md#explicit-admission-contract)
is unchanged: declaration-free calls never invoke the resolver; declared
admission remains one-time, before attempt, with complete Experiment/assignment
pair; required admission must still succeed or fail. The existing resolver may
receive only a server-resolved continuation reference. Retry retains admitted
assignment and flow without reallocation. Nested/cross-component calls gain no
authority from parent context alone. Restart cannot reconstruct assignment
from client data.

Resolved immutable context reaches source View callbacks and committed reload.
Read/List, ready refreshed Display and mutation acknowledgement, including
pending/delete without a Display, preserve the safe projected flow. Receipt,
resource revision, `retryCommand:false` and committed projection-failure
semantics remain unchanged; never replay a successful mutation for failed
projection. Ordinary direct Business Operations use the same admission path
and continue to require explicit Display reload, without proxying or response
rewriting. Phase 98 owns Experiment/Run/Arm allocation, persistence,
observations and rewards. This optional boundary does not depend on Phase 98
implementation for the P96-01..P96-09 slice.

## R6: Server capability and immutable runtime values

`PresentationContextResolver` is an auxiliary inherited ScopeContext capability.
Its `resolve(request)(using ctx: ExecutionContext)` returns
`Consequence[PresentationContextResolver.Result]`. Companion-owned `Request`
contains exactly `binding: PresentationContextRuntime.Binding`,
`continuation: Option[PresentationContextRuntime.References]` and `now: Instant`.
It contains no client locale, variant or expiry. `Result.Unavailable` differs
from `Result.Supported(resolution)`, including supported locale-only output.
The disabled resolver returns Unavailable with no I/O.

Runtime `Binding`, `References` and `Resolution` are final immutable values
with private constructors and no case-class copy. Their companion `createC`
factories reject null interop values and nullable Option containers/Some(null).
Binding contains an exact typed `ComponentInstanceId`, `PrincipalId` and
nonempty exactly trimmed control-free session identifier. Principal text obeys
the same identifier constraints. References contain only the existing bounded
opaque displayInstance/correlation pair. Resolution contains binding, optional
variant and optional validated Flow; a variant requires a flow. No allocation,
store, Experiment/Run/Arm, provider handle or configuration is retained.

`ScopeContext.Core` adds last default-None `presentationContextResolverOption`.
The inherited option accessor and disabled accessor, last optional camelCase
apply parameter and `withPresentationContextResolver` wrapper preserve existing
scope ancestry, including typed Component.Context. Both runtime rebound paths
preserve the inherited provider alongside the existing cross-sink policy.
`ExecutionContext.CncfCore` adds last default-None `presentationContext`, exposed
by its Holder. Its package-private companion setter copies only the immutable
safe resolution and preserves core, runtime and the response cell.

`PresentationContextRuntime.restoreC(componentInstanceId, candidate, ctx)` clears
inherited presentation before every new Operation, including absence. A present
candidate first crosses the strict bounded codec. Only after successful decode
may bounded Json inspection distinguish an unsupported positive
`cncf.display-flow` family version from explicit support-only input. Unsupported
flow clears without provider; it must never become support negotiation merely
because the pure codec discarded it. Supported flow contributes only its exact
reference pair; locale, variant and incoming expiry are inert syntax-checked
claims. A context without supported flow requests support, never allocation.

Trusted binding derives only from route component instance and `ctx.security`.
User, Service and Subsystem subjects require nonempty principal and session
identifiers. A present server session expiry must be strictly later than the
server clock. Anonymous, Unspecified, missing or expired session clears optional
context without provider. Ordinary absence adds no authentication requirement.

Unavailable provider/state, unknown references returned as Unavailable and
server flow expiry at or before now succeed with cleared context. Supported
results must match the entire trusted binding. Continuation also requires a
nonempty server flow and exact requested references. Any mismatch, null or
malformed result/Consequence/Option is fixed redacted `stateInvalid` before
evaluation or business. Provider Failure and exceptions become fixed redacted
`serviceUnavailable` with no provider Throwable, original Conclusion history,
identifiers or input in diagnostics. Safe factories revalidate server results
before storage. Support-only may yield locale-only support or an existing
server-created flow; the kernel allocates nothing.

`PresentationContextRuntime.project(ctx)` is provider-free. It rechecks current
trusted binding, the actual nearest typed Component.Context through scope
parents, server flow expiry and current core locale. It emits
`ctx.core.locale.toLanguageTag`, including `und`, with server variant/flow only.
Inapplicable or invalid state projects None. Component names/ID parsing, wire
claims and parent presentation state cannot establish authority.

## R7: Physical origin and ordinary preparation ordering

`PresentationContextIngress.requestC` returns `Consequence[Option[String]]`.
It admits only exact canonical Request property `cncf.presentationContext`,
exact query/form key of that name and ASCII-case-insensitive HTTP header
`X-CNCF-Presentation-Context`. Exactly one physical candidate must be an actual
String. Null, Some, collection and numeric values cannot be coerced. Multiple
candidates fail even when equal. Arguments, switches, payload and punctuation
aliases are not metadata sources. Records are inspected through ordered
`fields` and `field.value.single`, never map/getString/toString conversion.

Raw originalUri and structured query represent one physical query. Raw context
keys/values are percent-decoded exactly once as UTF8 after authorization,
preserving every duplicate. When raw context exists, a present structured
mirror must have the same String value (or ordered duplicate Strings); a raw
duplicate already rejects even when the Record collapsed its multiplicity.
Contradictory or non-String mirrors reject. Without raw context, structured
query is authoritative. Form and every matching header field contribute
independent physical candidates. Malformed escapes are fixed argumentInvalid
after authorization. Codec syntax and bounds remain Runtime responsibilities.

Generic framework enrichment excludes the reserved context key before parsing
its raw query value, including percent-encoded exact-key spellings. It retains
all other existing metadata behavior. The real HTTP route privately passes
typed Origin.HttpIngress containing the original HttpRequest through ordinary
dispatch, so double enrichment/property mirrors never count twice. Canonical
Request plus independent HTTP counts its Properties separately. Request.source
Http identifies the physical origin of Action/Task and internally reconstructed
HTTP Requests; its generated Properties are mirrors. Source.None ordinary
Properties remain raw evidence. Origin is private transport state, never a
wire flag, public admission argument, or CncfCore field.

All three existing preparation routes authorize first, then extract/restore,
then prepare and resolve existing evaluation admission. Canonical Request
construction flatMaps presentation failure before makeOperationRequest.
ActionTask first chooses its existing target component Action scope. Supplied
Actions retain their original canonical metadata-bearing Request and are never
rewritten. Framework stripping keeps reserved metadata out of domain Request.
Denied Operations decode no presentation and invoke no presentation provider,
evaluation resolver or business. Existing task decoration, evaluation failure
capture, datastore lease and declaration-free/required admission semantics
remain unchanged. The unchanged evaluation API reads safe server resolution
through implicit ExecutionContext. New nested/cross-component Operations need
explicit continuation and create their own execution/attempt; retry of an
already prepared accepted task retains exact resolution/assignment and does
not restore or allocate again.

## R8: Safe Display and mutation acknowledgement projection

Registered DisplayRuntime read/query callbacks receive resolved ExecutionContext.
After the Browser and pure projector, the registry replaces/removes any supplied
context with provider-free Runtime projection. The attachment helper preserves
exact definitions/model references. Old absent-context bytes remain unchanged.

`DisplayMutationResult.Result.presentationContext` is optional.
`createC(receipt, projection, presentationContext = None)` adds a source-compatible
last parameter and rejects null Option/Some(null). A root Some with Ready
requires matching nested Some by semantic locale, variant and complete flow
values, never hashes. Root None may retain the earlier inert nested supplied
context contract. `withPresentationContextC` copies receipt/projection without
command or reload, attaching the same safe context to root and Ready Display
while preserving exact receipt, definitions and model references. Pending and
Deleted carry optional root context even though they have no Display.

The closed result codec adds only optional root `presentationContext`, appended
after `retryCommand`. It uses the existing strict context codec; null, mismatch
and unknown root members fail structurally. None preserves prior exact bytes
and root members. Receipt/resource revision, reloadRequired, retryCommand:false,
Pending/Delete projection shape and no-replay semantics are unchanged.

Normal Updated, Unchanged, Created and Deleted completion attaches safe projected
acknowledgement after the existing canonical receipt and one-shot reload and
before encoding. Committed projection failure remains Pending with safe context;
it never replays the command or calls the provider again. Ordinary direct
Business Operations keep their own response, consume the same admission and
require explicit Display reload with retained context. Storage, capture,
coherence, identity, revision and transaction ownership do not change.

## Executable correspondence and evidence boundary

The [scenario suite](../../src/test/scala/org/goldenport/cncf/display/PresentationContextCodecSpec.scala)
authors eleven Given/When/Then behaviors. The
[property suite](../../src/test/scala/org/goldenport/cncf/display/PresentationContextCodecPropertySpec.scala)
authors two laws, each requiring at least 100 successes, zero discards and
lawful reducing shrinkers. This is source correspondence; validation execution
and independent connected whole-Step acceptance remain parent-owned.

| Group | Behaviors | Contract |
| --- | --- | --- |
| C1 | Optional combinations/order; canonical locale/Unicode/Instant limits | R1,R2 |
| C2 | Factory null/value rejection; all closed object shapes/duplicates; known scalar/root versions | R1,R2,R3 |
| C3 | Unknown/version discard; duplicate/name/version/count rejection; raw/tree byte/depth bounds | R3 |
| C4 | Exact Display attachment; absent goldens/transitive ready mutation; safe key exclusion | R1,R2,R4,R5 |
| P1 | Varying lawful optional Unicode/locale/nanos combinations, permutation and trusted Display round trip | R1..R4 |
| P2 | Varying single defects with stable diagnostics and unchanged trusted Display sources | R2..R4 |

Slice B authors the following actual-route correspondence. These statements
describe source, not executed results or acceptance.

| Suite / cases | Given / When / Then correspondence | Contract |
| --- | --- | --- |
| Runtime B1..B4 | inherited or explicit optional claims / restore and project / absent, locale-only, server claims, unknown-expired-unsupported-restart clearing | R5,R6 |
| Runtime B5..B9 | trusted binding or malformed provider/factory values / rebind or restore / isolation, redacted failures, exact references, inherited provider and factory rejection | R6 |
| Property BP1,BP2 | total varying Unicode/locale/nanos or single defect / restore/project under generated server reply / authoritative output or rejection-clearing; 100 successes and zero discards per authored law | R5,R6 |
| Admission BA1..BA3 | actual canonical Request, supplied Action or ActionTask / normal dispatch or prepared retry / authorization-provider-evaluation-business ordering and immutable flow/assignment | R5,R7 |
| Admission BA4..BA7 | denied, required-unavailable, undeclared, nested/cross-component or defective-provider calls / existing preparation routes / denial and failure ordering, explicit-only continuation and independent execution/attempt | R5,R6,R7 |
| Ingress BI1,BI2 | raw Properties or business/alias fields / physical extraction / one actual String or rejection/absence | R7 |
| Ingress BI3..BI8 | real HTTP/Http4s, raw URI and Record mirrors, independent canonical inputs / ordinary double enrichment and authorized extraction / one provider call, lossless duplicates/type/escape rejection and authorization-first behavior | R7 |
| Scenario BS1..BS3 | registered Detail/List and canonical mutations / normal read/update/unchanged/create/delete plus one-shot reload / safe root/Ready flow and exact committed receipt with no replay | R5,R8 |
| Scenario BS4,BS5 | direct Business or optional missing state / ordinary execution and explicit Display reload / original response, fresh revision, continuation or ordinary context-free success | R5,R7,R8 |
| Scenario BS6 | supplied receipt/Ready and optional root values / pure attachment/codec / semantic correspondence, null rejection, trusted identity and absent bytes | R4,R8 |

All four original P96-10 criteria remain unaccepted until B validation and
the parent-owned whole-Step conformance review. P96-11 retains consumer
artifacts, Phase full review, final validation or admitted aggregate handoff,
release and closure.
