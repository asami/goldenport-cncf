# Display consumer handoff

Document role: P96-11A consolidation and index of accepted source contracts.
This document adds no protocol authority or amendment. The linked ABI, runtime,
wire, mutation, Business and context specifications govern their own contracts.
Status: P96-11A consumer handoff independently accepted; exact local delivery
Step commit pending. Its first three original P96-11 outcomes are accepted,
with 52 checked/2 pending across 54 unchanged checklist texts. Stage P96-11
remains IN_PROGRESS; whole-Phase acceptance remains owed. See the
[sample/provenance journal](../journal/2026/10/2026-10-17-phase-96-consumer-handoff.md)
and [unchanged checklist](../phase/phase-96-checklist.md#p96-11-consumer-handoff-and-phase-closure).

## R1: Independent versions and provenance

The [read protocol](display-model-protocol.md) is `cncf.display-model.v1`,
version 1. The [generated ABI](logical-ui-generated-abi.md) is
`cncf.logical-ui-generated.v1`, version 1. Its exact semantic descriptor retains
`cozy.logical-ui-runtime.v1` / 1, authority `cozy.logical-ui-semantics.v1`,
`provisional-scenario` assurance and the original driver reference. None of
these is the UI definition revision, producer provenance revision, resource
EntityRevision, query snapshot, or [context](display-presentation-context.md)
root/family version. Context root and supported families currently have version
1 independently. Mutation request, descriptor, result and Business descriptor
each retain their own named v1 schema. A matching number across spaces implies
no shared version or admission authority.

Actual Cozy List/Detail use producer `cozy.logical-ui-generated.v1`, source
`ess2-01`, revision `ess2-01-cml-1`. Pure mutation examples use the hand-written
expected-generated producer `cncf.expected-generated-fixture`, source `ess2-01`,
revision `fixture-1`. Both use `ess-definition-1`; that coincidence does not
make the expected-generated samples actual Cozy output or native lifecycle proof.
The Business fixture has its own source, described in R3.

## R2: Exact ordinary transport boundary

[Runtime SR1..SR4](display-model-runtime.md),
[ordinary mutation](display-mutation-service.md) and
[Business BO5/BR1](display-business-operation-descriptor.md) govern transport.
The service is component-scoped `display`; the existing route is
`/rest/v1/{component}/display/{operation}.json`. No `list`/`get` alias or new
endpoint is introduced. Exact operations and business selectors are:

| Operation | HTTP | Required selectors / content |
| --- | --- | --- |
| `display.query` | GET | one String each `resourceType`, `screenId`; no body |
| `display.read` | GET | one String each `resourceType`, `screenId`, `resourceId`; no body |
| `display.describeMutation` | GET | one String each `resourceType`, `screenId`; no body |
| `display.create` | POST | one String each `resourceType`, `screenId`; raw mutation JSON body; If-Match forbidden |
| `display.update` | PUT | same selectors and raw JSON; exactly one strong canonical If-Match |
| `display.delete` | DELETE | same selectors and raw Delete JSON; exactly one strong canonical If-Match |
| `display.describeBusiness` | GET | one String each `resourceType`, `screenId`; no body |

HTTP GET/selectors use query parameters; mutation bodies are raw
`application/json` UTF8 decoded with REPORT and strict duplicate-preserving
admission. `.json` uses the existing JSON egress; consumers requesting the
normal envelope use `textus.output.shape=envelope`, with the business object in
`data`. This is not a new result envelope or permission default. CLI/MCP/SPI
mutation inputs carry the raw JSON as a String `payload`, with String selectors;
update/delete additionally carry the canonical positive-long observation under
`EntityRevisionTransport.observedRevisionPropertyName`. Create forbids it.
MCP must retain String kind at the actual marked Operation boundary before
generic normalization. JSON maps, query payloads, duplicate selectors or forged
normalized metadata cannot replace the physical admitted carrier.

Update/delete physical If-Match is exactly one strong `"revision-N"`, with
canonical positive N; missing, duplicate/case-variant duplicate, weak, wildcard,
list, leading-zero or overflow observations fail. Observation is transport
metadata, never a mutation business member. Current authorization, capability,
domain/lifecycle checks and atomic comparison remain mandatory.

Business Actions name ordinary canonical targets. Invoke that target's existing
Operation endpoint and its own input contract directly, then explicitly call
`display.read` or `display.query`. The inert descriptor fixture below names
`business.review`; the accepted persistent reference separately invokes
`business.requestReview`. Their names and parameter contracts must not be
substituted. Neither a descriptor nor an Enabled Action grants permission.

## R3: Reproducible consumer inputs

All JSON files are intentional consumer inputs: compact canonical codec text
plus exactly one LF. They are not execution-evidence packages. The new
[typed fixture](../../src/test/scala/logicalui/DisplayConsumerHandoffFixture.scala)
uses public factory/codec contracts; no generator, command/store/commit/reload
callback or fixture writeback is part of consumer interpretation.

| Input | Typed recipe and source |
| --- | --- |
| [Actual List](../../src/test/resources/display/protocol/v1/ess2-01-list.json), [actual Detail](../../src/test/resources/display/protocol/v1/ess2-01-detail.json) | Unchanged actual Cozy goldens, `DomainLogicalUiDefinitions` and `DisplayProtocolSpecFixture.source`; component `org.goldenport.cncf.test.GeneratedLogicalUiConsumer`, resourceType `collecting-reference`; resources 1/7 and 2/19 in List, 1/7 in Detail |
| [Create request](../../src/test/resources/display/consumer/v1/mutation-create-request.json) | `bindingC(positive=true)` selection; ordered `main.title = Created title 日本`, `main.description = Created description` |
| [Update request](../../src/test/resources/display/consumer/v1/mutation-update-request.json) | same selection, resource 1, ordered `Updated title 日本`, `Updated description` |
| [Delete request](../../src/test/resources/display/consumer/v1/mutation-delete-request.json) | same selection and resource 1, no field vector |
| [Mutation descriptor](../../src/test/resources/display/consumer/v1/mutation-descriptor.json) | the same binding's `descriptorC(Availability.enabled)` |
| [Created Ready](../../src/test/resources/display/consumer/v1/mutation-created-ready.json) | Create request, observation None, supplied Created receipt resource 1/revision 1, matching created values |
| [Updated Ready](../../src/test/resources/display/consumer/v1/mutation-updated-ready.json) | Update request, observed revision 7, Updated receipt revision 8, matching updated values |
| [Unchanged Ready](../../src/test/resources/display/consumer/v1/mutation-unchanged-ready.json) | same Update request, observed 7, Unchanged receipt revision 7, matching updated values |
| [Deleted](../../src/test/resources/display/consumer/v1/mutation-deleted.json) | Delete request, observed 7, Deleted receipt revision 7, projection None / reload-list |
| [Updated Pending](../../src/test/resources/display/consumer/v1/mutation-updated-pending.json) | Update request, observed 7, Updated receipt revision 8, Pending(ViewNotCurrent) |
| [Standalone context](../../src/test/resources/display/consumer/v1/presentation-context.json) | `PresentationContext.createC` recipe below |
| [Contextual Updated Ready](../../src/test/resources/display/consumer/v1/mutation-updated-ready-context.json) | Updated Ready with the same fixed context on nested Display and root Result |
| [Business descriptor](../../src/test/resources/display/consumer/v1/business-descriptor.json) | existing `DisplayBusinessOperationFixture.open()`, `registry(session.component)`, `DisplayBusinessOperationCodec.encodeC(registry, resourceType, "capture-detail")`; always close session |

Mutation binding is `DisplayMutationContractFixture.bindingC(positive=true)`:
source `org.goldenport.fixture.Mutation` / `Capture`, screen `capture-detail`,
definition `ess-definition-1`, mutation revision `positive-contract-1`, with
the expected-generated provenance in R1. Resource is exactly
`TypedDisplayProjectionFixture.resourceC(1, revision)`; its opaque EntityId is
never reconstructed by parsing a screen or sample alias. Ready is the actual
pure `detailC(binding.definitions).projectC(captureC(1, resultRevision).copy(
title=matchingRequestTitle, description=matchingRequestDescription))`, paired
with the exact `binding.selection.source` in `ReloadedDisplay`, then admitted
with `receiptC` and `createC`. Draft stage and other typed values/actions retain
their fixture defaults. Supplied receipts demonstrate codec coherence only.

Fixed inert context has locale `en-US`, variant `server-variant`, and
`Flow.createC("consumer-display-1", "consumer-correlation-1",
Instant.parse("2026-10-17T00:30:00.123456789Z"))`. No clock, trusted binding or
assignment lookup is implied. Plain results have None; contextual Ready uses
`Display.withPresentationContextC` and root `createC` with the same context.

Business source is `org.goldenport.cncf.test.BusinessDescriptor` /
`collecting-reference`. The existing Detail `request-review` Action selects
ordinary `business.review`, with the original ordered parameter vector:
`resource` argument/string/1, `comment` property/string/?, `notify`
switch/boolean/*. Defaults and executable objects are excluded. Route
component and external Operation supply trusted selection; received JSON does
not authorize transport, choose a dispatcher or register a source.

## R4: Editor semantics and exact scalar forms

[Mutation R1..R5](display-mutation.md) separates schema/hints, operation Support,
current Availability and actual authority. Title and description have explicit
`write.title` / `write.description` bindings to registered Entity/Aggregate
targets; stage has no inverse binding and is intrinsically readonly. Read
bindings never imply write mappings. Server/domain validation remains canonical.

Native kinds are closed Text, Number, Boolean, Timestamp and Missing. UTF16 code
units are exact, including NUL, composed/decomposed text and isolated surrogates;
there is no normalization. Decimal wire is a String exactly equal to
`java.math.BigDecimal.toString`, preserving unscaled coefficient and scale,
including trailing zeros/signed scales. Timestamp is canonical `Instant.toString`
with exact seconds/nanos. Missing has no payload, distinct from SQL NULL and
omission. Read resource revision is a canonical positive decimal String;
outer mutation-result revision is a canonical positive Long integer literal.
Mutation version is literal integer 1; fractional/exponent/string versions and
noncanonical revision spellings reject. Context root/family compatibility is
governed independently by the context contract. Canonical renderer escapes
non-ASCII/control UTF16 units; files add one LF, codecs add none.

## R5: Context compatibility and no replay

[Presentation context R1..R8](display-presentation-context.md) is the sole
optional-context amendment. Absent context stays absent. Known supported
payloads are strict; malformed known data cannot downgrade to absence. Unknown
bounded family names/unsupported positive family versions are inert extensions
discarded without echo or interpretation. An incompatible root version rejects.
Existing bounds, duplicate rejection and UTF8/depth limits apply before use.

Ordinary authentication/authorization precedes optional restoration. Current
server component instance, principal/session and clock establish the binding;
client locale/variant/expiry are claims, never assignment or allocation authority.
Server restoration validates continuation and clears unavailable, expired or
inapplicable context. Runtime handles, credentials and security capabilities
never cross the wire. Fixed examples above are not live continuations.

Ready must correspond to the exact supplied receipt source, definition,
provenance, ID and revision. Pending and Deleted require authoritative reload;
every result has `retryCommand=false`. Projection failure after a committed
mutation does not authorize replay or imply rollback. Indeterminate
acknowledgment requires authoritative read without automatic command retry.
Direct Business success still requires an explicit Display reload.

## R6: Consumer and platform ownership

TFC owns protocol transport, typed decoding and generic presentation runtime;
TFAF owns visual realization. Editing Studio server Phase 2 owns domain
projection, explicit update-target mapping and application semantics. App Phase
4 owns fixture/prototype feedback; app Phase 5 owns real-server aggregate/E2E
acceptance. CNCF retains its independent repository closure gates. Cozy owns
semantic modeling and generation against CNCF's Scala 3 ABI, without a
`cozy_2.12` runtime dependency. Phase 98 owns Experiment/Run/Arm allocation,
persistence, observation and rewards; context support requires no Phase 98
implementation and no client experiment logic.

The accepted [reference](display-editing-studio-reference.md) and canonical
mutation/Business contracts require actual native PostgreSQL evidence, including
durability and concurrency where declared. PostgreSQL is the primary native
acceptance platform; SQLite/memory/MySQL regressions are complementary, not
substitutes. [SQL NULL boundary](sql-null-record-boundary.md) preserves physical
SQL NULL while omitting that logical Record attribute; no nullable Option API
or new raw-null domain value is introduced.

## R7: Accepted-source matrix and remaining Phase obligations

This is an index of original Step evidence, not a new test result. The
[Phase/checklist](../phase/phase-96.md) and original acceptance journals own
acceptance and chronology. P96-10 is accepted at actual local commit
`53ecdfadb00003b995b81ab9548a54b0a4880cab`; all P01..10 accepted source remains.

| Step | Contract | Existing executable specification matrix |
| --- | --- | --- |
| P96-01 | [ABI](logical-ui-generated-abi.md), [runtime](display-model-runtime.md) inventory and promotion | [ABI spec](../../src/test/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbiSpec.scala), original inventory/promotion journals linked by checklist |
| P96-02 | [ABI](logical-ui-generated-abi.md) and [runtime](display-model-runtime.md) model/value admission | [ABI property](../../src/test/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbiPropertySpec.scala), [model](../../src/test/scala/org/goldenport/cncf/display/DisplayModelSpec.scala), [model property](../../src/test/scala/org/goldenport/cncf/display/DisplayModelPropertySpec.scala) |
| P96-03 | [runtime](display-model-runtime.md) provider/bootstrap/projection/generated consumer | [bootstrap](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryLogicalUiBootstrapSpec.scala), [runtime factory](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayRuntimeSpec.scala), [generated acceptance](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala), [projection](../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionSpec.scala), [projection property](../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionPropertySpec.scala), [runtime](../../src/test/scala/org/goldenport/cncf/display/DisplayRuntimeSpec.scala) |
| P96-04 | [wire](display-model-protocol.md) | [codec](../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecSpec.scala), [codec property](../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecPropertySpec.scala) |
| P96-05 | [ordinary read SR1..SR4](display-model-runtime.md) | [read](../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadSpec.scala), [read property](../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadPropertySpec.scala), [service registration](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayServiceSpec.scala) |
| P96-06 | [mutation](display-mutation.md) typed request/editor/result and transport | [contract](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationContractSpec.scala), [contract property](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationContractPropertySpec.scala), [transport](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationTransportSpec.scala), [result](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationResultSpec.scala), [request codec](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationRequestCodecSpec.scala), [descriptor codec](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationDescriptorCodecSpec.scala), [result codec](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationResultCodecSpec.scala), [codec property](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationCodecPropertySpec.scala) |
| P96-07 | [bridge](display-mutation-bridge.md), [ordinary service](display-mutation-service.md) | [bridge](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationUpdateBridgeSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationUpdateBridgePropertySpec.scala), [update](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceUpdateSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceUpdatePropertySpec.scala), [ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceIngressSpec.scala), [lifecycle](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceLifecycleSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceLifecyclePropertySpec.scala)/[ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceLifecycleIngressSpec.scala), [List](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceListSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceListPropertySpec.scala)/[ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceListIngressSpec.scala), native PostgreSQL [update](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayMutationServiceUpdateAcceptanceSpec.scala)/[lifecycle](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayMutationServiceLifecycleAcceptanceSpec.scala)/[List](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayMutationServiceListAcceptanceSpec.scala) |
| P96-08 | [Business metadata/direct execution/explicit reload](display-business-operation-descriptor.md) | [metadata](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationSpec.scala)/[codec](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationCodecSpec.scala)/[ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationIngressSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationPropertySpec.scala), [review](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessReviewSpec.scala)/[ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessReviewIngressSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessReviewPropertySpec.scala)/[PostgreSQL](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayBusinessReviewAcceptanceSpec.scala) |
| P96-09 | [reference](display-editing-studio-reference.md) | [reference](../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferenceSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferencePropertySpec.scala)/[PostgreSQL](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayEditingStudioReferenceAcceptanceSpec.scala) |
| P96-10 | [context](display-presentation-context.md) | [codec](../../src/test/scala/org/goldenport/cncf/display/PresentationContextCodecSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/PresentationContextCodecPropertySpec.scala), [ingress](../../src/test/scala/org/goldenport/cncf/display/PresentationContextIngressSpec.scala), [admission](../../src/test/scala/org/goldenport/cncf/display/PresentationContextOperationAdmissionSpec.scala), [runtime](../../src/test/scala/org/goldenport/cncf/display/PresentationContextRuntimeSpec.scala)/[property](../../src/test/scala/org/goldenport/cncf/display/PresentationContextRuntimePropertySpec.scala), [continuation scenario](../../src/test/scala/org/goldenport/cncf/display/DisplayPresentationContextScenarioSpec.scala) |

The thirteen original Phase acceptance criteria correspond as follows. This
mapping does not check or waive any criterion:

| Original acceptance criterion | P01..10 evidence owners |
| --- | --- |
| semantic View Model, Logical UI definition and Display Model are distinct public concepts | P01/P02/P03/P09 |
| CNCF exposes the versioned Scala 3 Logical UI generated ABI and ComponentFactory admission/discovery route | P01/P02/P03 |
| a real Cozy Phase 76 generated producer fixture compiles against CNCF, is admitted through ComponentFactory and yields the same List/Detail projection as the hand-written fixture, without CNCF depending on `cozy_2.12` | P03, retained LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01 |
| Display Projection produces Cozy Phase 74-conformant runtime models | P01/P02/P03 |
| the minimum List/Detail/Value/Action protocol is versioned and deterministic | P04/P06/P10 |
| no Flutter-specific Widget semantics enter CNCF | P01/P02/P04/P09 |
| semantic View consumers remain unaffected | P03/P05/P09 |
| the reference fixture proves List, Detail and standard Display Mutation | P05/P06/P07/P09 |
| DisplayService is component-scoped and exposes only the Display access/mutation boundary | P03/P05/P07 |
| Aggregate/View runtime access remains internal in this phase | P01/P03/P05/P07 |
| Business Operations remain direct CNCF Operations and are not wrapped by DisplayService | P08/P09 |
| the initial post-Business-Operation consistency rule is client-initiated Display reload | P08/P09 |
| the protocol is sufficiently frozen for textus-flutter-core client/runtime implementation | P04/P06/P08/P10 inputs; P11A handoff accepted, final Phase acceptance remains pending |

Unsupported additions include Date/Choice/Reference as new closed value kinds,
Flutter Widgets/layout, push/subscriptions, assignment/allocation authority,
runtime handles, custom database implementations and production app semantics.
Explicit conversions to admitted native kinds retain server ownership.

The new [consumer executable specification](../../src/test/scala/org/goldenport/cncf/display/DisplayConsumerHandoffSpec.scala)
registers exactly CH1 actual read, CH2 request, CH3 editor, CH4 result, CH5
Business, CH6 context, CH7 tampering and CHP1 native fidelity. CHP1 uses total
bounded generators and reducing valid shrinkers, minimum 100 successes, one
worker and zero discards. Actual focused attempt
`phase96-p96-11a-consumer-handoff-focused-001` passed one suite/8 tests with zero
failed, aborted, canceled, ignored or pending; Test compilation/generation was
covered, SBT0/wrapper0 and serial lock released. CHP1 passed 100 generated cases
with lawful reducing shrinkers. One unspecified deprecation warning is
nonblocking. Fresh independent Sol medium ordinary Step review
`phase96-p96-11a-step-review-001@1` returned PASS with zero Current Boundary
Blockers, Hygiene or Development Candidates, parent-admitted in typed review
ledger 40 to 41. Exact local P11A Step commit remains pending.

Prior actual P10 focused 2-suite/21-test and same 50-suite/407-test evidence,
including all five mandatory native PostgreSQL 17.11 suites, durability and SQL
NULL logical omission, was independently assessed for reuse for unchanged
product scope, not rerun for these fixtures/docs. The new examples establish
supplied-value/codec fidelity, not native persistence, live continuation
authority or consumer application completion.

Original 54 checklist texts retain 52 checked/2 pending; P11A's first three
outcomes are accepted, P11B's final two remain open, Stage P96-11 IN_PROGRESS.
After the actual ordinary local P11A commit, the epoch's sole full Phase review
is frozen Sol high across original base through all Step commits, followed by
final repository-full validation, distinct Phase release and ledger closure.
Whole-Phase full review count remains 0; those final gates remain owed.
