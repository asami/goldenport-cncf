# Business Operation action descriptor

## Current P96-08 acceptance — 2026-10-16

P96-08 is independently accepted on all four original criteria. The complete
Step review `phase96-p96-08-step-review-001@1` and independent focused
closure `phase96-p96-08-step-title-focused-result-001@1`, admitted in review
ledger revision 37, resolve `CPB-P96-08-TITLE-CORRESPONDENCE-001`.
The corrected property check passed 2/2 tests in one suite; the same focused
40-suite accumulator passed 332/332, with terminal SBT0/wrapper0 and released
serial locks. Formal Step repair 1 is closed accepted; nonconverging count 0.
Known nonblocking Hygiene remains separately recorded, with no new finding.

The checklist now has 41 checked/13 pending outcomes with all 54 criterion
texts unchanged. P96-09 through P96-11 remain OPEN/not started. Phase 96
remains in-progress: its sole full Phase review, final validation or admitted
aggregate handoff, consumer acceptance and release are not established here.
Step acceptance does not itself prove a completed Git commit. Earlier P96-08
authoring/validation checkpoints below retain their historical pending status;
this current acceptance record supersedes those pending-status projections.

## Historical pre-acceptance status

Document role: normative P96-08A descriptor and P96-08B ordinary Business/reload
contract. A is independently accepted through
`phase96-p96-08a-protected-review-001@1`, with the original 23-suite/168-test
PASS retained. B representative001 failed Test compilation; representative002
compiled and completed with 2 passes and 8 failures; representative003 completed
with 9 passes and 1 failure. Representative004 then passed 10/10 tests in two
suites, resolving the POST form-carrier cause. Accumulator001 completed 40 suites
and 332 tests with 331 passes and one BRP1 failure. Unit 4's lawful-title source
repair passed property001 (1 suite/2 tests) and the same 40-suite accumulator002
(332/332 tests) on the prior assertions. Independent whole-Step review
`phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`: missing exact initial/reloaded semantic
Title comparisons, without alleging production corruption. First formal Step
repair 1's first property attempt completed 1 suite/2 tests with 1 PASS/1 FAIL;
its observation-source correction is now focused-validated in the same repair.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure and exact Step acceptance/commit remain pending.
This specification refines [DSP96-R08](display-model-runtime.md#dsp96-r08-business-operations-and-context)
and the [decided design](../design/display-business-operation-descriptor.md).
P96-08B direct Business execution and explicit reload remain required.

## BO1: Inert scoped registration

`DisplayBusinessOperation.Binding` contains `screenId`, `scope`, `actionId`,
`serviceName` and `operationName`. Scope is the closed `Screen` / `Item`
vocabulary. Screen selects List `actions` or Detail `actions`; Item selects
only List `itemActions`. Detail has no Item scope. A locator is the exact
tuple `(screenId, scope, actionId)` in admitted generated definitions.

The declared Action supplies the descriptor's ID, label and opaque `bindingId`.
The binding identity is never parsed as an Operation path. Equal action IDs
in different scopes are independent; repeated locators fail. Reusing one
application binding identity is valid only when every occurrence selects the
same canonical target. Legacy inert and standard mutation actions require no
Business registration. Output follows definition declaration order, with
List screen actions before item actions, independently of binding order.

Admission requires complete nonnull inputs and an exact complete resource-to-
screen correspondence. List and its Detail target use the same resource type.
Unknown screens/actions, null scopes, Item-on-Detail, empty/untrimmed names,
duplicate resource mappings and conflicting shared targets fail atomically.
No prefix registry is published. In an active catalog, an otherwise valid
declared screen without selected Business actions has an empty `actions` array.

## BO2: Canonical target and descriptive input

The selected target is exactly one service and exactly one existing Operation
definition in the owning component's original protocol, excluding `display`.
There is no default service, prefix/alias fallback, foreign component, arbitrary
URL, supplied callback or supplied input schema. Admission reads definitions
only; it never calls `createOperationRequest`, `createCall` or a Browser.

The target reference contains the actual owning `ComponentId`, service name
and Operation name. Parameters preserve the actual
`OperationDefinition.Specification.request.parameters` order. Each parameter
contains exactly its canonical name, `ParameterDefinition.Kind`,
`datatype.name` and `multiplicity.mark`. Null/malformed/duplicate names,
null parameter kinds/domains/datatypes/multiplicities fail rather than being
silently omitted, deduplicated or replaced with inferred types.

This is a descriptive minimum, not a complete client validator. Defaults,
credentials, callbacks, constraints and serialized `ValueDomain` are absent.
The ordinary target Operation owns full normalization, constraints,
authorization, transactions and response semantics.

## BO3: Provider and publication

`DisplayBusinessOperationBindingProvider` declares
`displayBusinessOperationBindings(component: Component): Vector[Binding]`.
Component capability wins; the factory is used only when that capability is
absent. Read the selected provider once. A null result or `NonFatal` exception
produces a redacted structured failure without fallback.

ComponentFactory joins the Business registry against the original protocol
while preparing the existing Display service. Only the complete registration
is published. The additive optional registry preserves old `DisplayService`
overloads and read/mutation semantics. An absent provider or empty binding
vector adds no metadata Operation.

## BO4: Standalone closed wire contract

Schema is `cncf.display-business-operation-descriptor.v1`; version is integer
`1`. Future incompatible changes require a new explicit schema/version.
This does not widen the existing closed Display or mutation JSON schemas.

| Object | Exact members |
| --- | --- |
| Root | `schema`, `version`, `componentId`, `resourceType`, `definition`, `provenance`, `actions` |
| `definition` | `screenId`, `revision` |
| `provenance` | `producerId`, `sourceId`, `revision` |
| Action | `scope`, `id`, `label`, `bindingId`, `operation`, `input` |
| `operation` | `componentId`, `service`, `operation` |
| Input parameter | `name`, `kind`, `datatype`, `multiplicity` |

`actions` and `input` are ordered arrays; other listed values are strings
except integer `version`. Scope tags are `screen` / `item`; parameter kind
tags are `argument` / `property` / `switch`. Definition revision and producer
provenance remain independent of Entity revision. No resource revision or
execution capability is invented for the descriptor.

`encodeJsonC(registry, resourceType, screenId)` and `encodeC(...)` select only
the exact admitted source/screen. `decodeC(registry, json)` uses a strict
duplicate-rejecting Jawn parser and returns the canonical immutable descriptor
only when every closed member, primitive, array order and value corresponds to
that registry. Unknown/missing/duplicate members, incompatible versions and
forged source/definition/provenance/target/input metadata fail. Decoding never
constructs executable Operations or resolves actual parameter values.

Failures use `Consequence.fail(Taxonomy.argumentInvalid, DescriptorException)`
with typed `Diagnostic(code, path, reason)`. Codes have the
`DSP96_BUSINESS_` prefix, paths use fixed member names and indices, and reasons
are redacted. Input JSON, URLs, defaults, credentials and throwable details
are never copied into diagnostics.

## BO5: Ordinary authorized metadata read

An active registry adds only `component.display.describeBusiness`. It is an
ordinary read-only Operation, with `ResponseDefinition` result
`Named("OperationResponse")` and explicit OpenAPI GET projection. Existing
ordinary HTTP routing exposes GET
`/rest/v1/{component}/display/describeBusiness.json`, retaining the framework's
normal JSON/envelope binding (`data` is the descriptor object).

The request requires exactly one String Property each for `resourceType` and
`screenId`, with exact nonempty trimmed identities. Positional arguments,
switches, unknown properties, duplicate selectors, wrong primitives and nulls
fail without coercion. Only existing framework transport/security metadata is
tolerated; its presence is not an authorization grant. HTTP-origin requests
also retain their raw selector evidence so normalization cannot conceal
duplicate or non-String input. The metadata Operation uses `QueryAction` and
`ProcedureActionCall` only to return immutable admitted JSON. It invokes no
Business target, Browser, nested dispatcher or callback.

Ordinary component/Operation authorization precedes successful execution.
Production-style unauthenticated access is denied before effects. Displayed
enabled/disabled/hidden state cannot select a target or grant permission.

## BO6: Required executable evidence and remaining boundary

The authored admission suite covers BA1 scoped ordering/canonical input,
BA2 atomic rejection and BA3 provider precedence/absence/publication. The
codec suite covers BA4 strict standalone roundtrip and forgery rejection.
The ingress suite covers BA5 ordinary Operation/actual HTTP retrieval and
unchanged explicit Detail reads, plus BA6 malformed input and production-style
authorization denial. The property suite covers BA7 registration permutations,
shared-key consistency, scope separation and codec roundtrips, with total
bounded generators, reducing domain-valid shrinkers, at least 100 successes
per law and no discard-based implications.

A's original 23 suites/168 tests passed and independent protected review
`phase96-p96-08a-protected-review-001@1` accepted the descriptor boundary.
That evidence does not establish B execution. P96-08B must separately
demonstrate a direct ordinary Business Operation with its
actual canonical committed-state transition, denied/malformed requests that
preserve state, a non-Display success response, no implicit source read and a
fresh Display only after explicit `display.read/query`. Persistent proof uses
the actual Entity/Aggregate APIs and PostgreSQL where required. No mock
transport substitutes for that execution. All four P96-08 criteria remain
unchecked until the parent admits complete independent evidence.

Consumers use the descriptor reference with ordinary Operation transport and
explicit reload. TFC/TFAF, Editing Studio Phase 2 and app Phase 5 retain their
own integration responsibilities. No Flutter, production application,
push/subscription, Service Bus refresh or second invocation protocol is
admitted here.

## BR1: Ordinary request-review input and canonical mutation

The distinct development fixture binds the Detail screen's `request-review`
Action to the same component's ordinary `business.requestReview` Operation,
with explicit OpenAPI POST and ordered required single String Properties
`resourceId`, then `revision`. The Operation owns EntityId parsing, positive
Long revision admission and exact canonical decimal spelling. Missing,
malformed, duplicate or non-String inputs fail before the CommandAction's
domain mutation. Display descriptors and If-Match are not Business input.

Ordinary Business POST transports these supplied Properties in the standard
`application/x-www-form-urlencoded` body. The fixture retains omitted,
duplicate and invalid supplied values without adding defaults or duplicating
these selectors in query. GET read/metadata selectors remain in query;
framework/test/forged query facts and the actual session header retain their
existing meanings.

One synchronous CommandAction/FunctionalActionCall binds the component's
application datastore and uses ordinary UserPermission Entity observed patch
update, or the registered Aggregate observed snapshot command for root/alias.
Before the Aggregate/root/alias command, the application explicitly supplies
the existing typed `UnitOfWorkOp.Authorize` with the canonical
`AggregateAuthorization.Request` for `command:requestReview`, its selected
identity and same-component source/target, using default BusinessService /
UserPermission and no supplied access policy. This enforces the application's
owner/ACL requirement through the ordinary interpreter and factory hook.
The unchanged CML `authenticated_only` policy alone does not impose that
instance requirement. Entity retains its existing native authorization.
The stage-only patch changes the existing raw `stage` from `draft` to
`review-requested`; canonical mutation owns managed revision, audit, permission
and persistence. The ordinary enclosing command owns commit and response
publication. There is no nested dispatcher, internal authorization bypass,
DisplayMutationActionCall or manual commit.

The fixture provenance is producer `cncf.development.display-business`, source
`canonical-review-<token>`, revision `fixture-1`. This is a normal-update
development workflow-stage field, not an ESS2 `workflowState` mapping. P96-09
retains that reference fixture's provenance and mapping responsibility.

## BR2: Explicit Detail reload and independent evidence

Business success returns only ordinary JSON with `outcome: review-requested`,
the actual canonical `resourceId` and authoritative `revision`. It performs
zero Display Browser find/query calls or projections; required canonical
Entity/Aggregate reads remain permitted. Only a subsequent explicit ordinary
`display.read` supplies the fresh selected Detail. A previously returned
Display and the Business JSON remain separate values. A failing later read
cannot replace that response, roll back the committed command or replay it.

Hidden/Disabled/Enabled presentation neither grants nor revokes ordinary
Business authority. Production evidence uses standard/system execution,
multi-user authenticated provider sessions and actual Http4s ingress; forged
authentication, owner or admin request facts grant no trust. A real trusted
owner success and explicit read accompany missing, rejected and foreign-session
denials. ControlledTest direct requests provide test-only correspondence.
The provider records only the typed PrincipalId of an actual accepted
AuthenticationResult: owner, foreign or no accepted principal. A foreign
instance denial precedes the factory hook, whose authorization observation
therefore remains empty. Owner success must retain real canonical
authorization, shared UnitOfWork and commit evidence.

The opt-in Business fixture prepares its declared native schema using the
existing trusted `prepareLifecycleSchema()` before seeding and before any
baseline. SQL NULL attributes remain omitted from logical Records. Complete
ordered logical fields and native table/column/type/row equality remain exact;
no schema effects are hidden after a rejected action's baseline. Typed
observers independently record actual native `compareAndSetEntity` and
full-save `mutateVersionedEntity` calls while delegating unchanged. Their
combined attempt count is exactly one for successful HTTP mutation and zero
for denial; generic provider writes remain zero. No acknowledgement or capture
is synthesized by these observations.

Required B scenarios BR1–BR7, BRP1–BRP2, BRH1–BRH3 and BRPG1–BRPG3 cover
Entity/Aggregate/alias and both managed revision representations, complete
independent ordered logical/native tables, body/unrelated/security/audit
preservation, stale/malformed/denied requests and committed-read failure.
Native PostgreSQL 17.11 with fsync/synchronous_commit enabled is mandatory;
generated laws require at least 100 successes and zero discards. The original
representative001 failed Test compilation; representative002 compiled and ran
BR1–BR7 and BRPG1–BRPG3, with 2 passes and 8 failures. Representative003
completed with 9 passes and 1 failure: the legitimate owner POST returned 400
for missing `resourceId` before Business execution because required selectors
were query-only. That positive is not a Business success, and preceding
production negatives were not accepted proof at that checkpoint. Subsequently,
representative004 passed all 10 tests in two suites, resolving that form-carrier
cause. Accumulator001 completed all 40 suites: 332 tests, 331 succeeded, one
failed, zero aborted/canceled/ignored/pending. BRP1 generated a trim-empty Title
and failed before mutation with `DSP96_ROLE_INVALID`; this was not a permission
or persistence failure. Unchanged `Display.scala` requires nonblank Title text.
For `VAL-P96-08B-LAWFUL-TITLE-DOMAIN-004`, the already-authored unit 4 source
retains each exact raw Unicode/control payload behind a fixed `T` title prefix,
including every shrink witness and empty payload. The original newline/NUL
payload at revision 16 uses the same complete commit/reload oracle across all
six target/representation combinations. No trimming, filtering, discards,
exception catching or weakened state/title oracle is admitted; each property
still requires at least 100 successes and zero discards. Unit 4 passed
`phase96-p96-08b-business-review-property-001` (1 suite/2 tests) and
`phase96-p96-08b-business-review-accumulator-002` (the same 40 suites/332 tests).
Both completed with all tests passing, zero failed/aborted/canceled/ignored/pending,
SBT0/wrapper0 and released serial lock; parent typed result readers accepted
the results for the prior assertions; they do not prove the newly added exact
Title comparisons. Independent whole-Step review
`phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`, the missing comparison of initial logical,
initial Display and reloaded Display semantic Title to the independently
supplied sample. No production corruption is alleged. First formal Step
repair 1's `phase96-p96-08-step-title-property-001` completed 1 suite/2 tests:
1 PASS/1 FAIL, SBT1/wrapper1 and serial lock released. BRP1 failed at the first
newline/NUL revision-16 initial assertion before Business; BRP2 passed. The
parent-frozen assertion observed raw DataStore `utf16:0054000a0000` instead of
the decoded semantic View; this is an observation-layer error, not demonstrated
production corruption. The same consumed Step repair 1's correction is now
focused-validated: observe the already captured
`before.resident.views.selected.record` title, still exactly against
`sample.title`. Initial Display and the already fetched reload JSON retain
their independent exact comparisons without another read.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure remains pending before exact Step acceptance/commit. This Detail
proof makes no List/query or P96-09–P96-11 acceptance claim. All 54 checklist
texts, 37 checked/17 pending outcomes, four unchecked P96-08 criteria and zero
full Phase reviews remain unchanged.

## Executable specifications

- [Admission and providers](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationSpec.scala)
- [Strict codec](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationCodecSpec.scala)
- [Generated laws](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationPropertySpec.scala)
- [Ordinary ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessOperationIngressSpec.scala)
- [Direct Business and explicit Detail](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessReviewSpec.scala)
- [Business preservation and reload laws](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessReviewPropertySpec.scala)
- [Actual HTTP and production provider evidence](../../src/test/scala/org/goldenport/cncf/display/DisplayBusinessReviewIngressSpec.scala)
- [Native PostgreSQL Business acceptance](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayBusinessReviewAcceptanceSpec.scala)
