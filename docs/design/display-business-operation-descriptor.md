# Business Operation descriptor boundary

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

Status: decided P96-08A/B design. A is independently accepted through
`phase96-p96-08a-protected-review-001@1`, retaining the original 23-suite/168-test
PASS. B representative001 failed Test compilation; representative002 compiled
and completed with 2 passes and 8 failures; representative003 completed with
9 passes and 1 failure. Representative004 passed 10/10 tests in two suites,
resolving the POST form-carrier cause. Accumulator001 completed 40 suites/332
tests with 331 passes and one BRP1 failure. Unit 4's lawful-title source repair
passed property001 (1 suite/2 tests) and the same accumulator002 (40 suites/332
tests) on the prior assertions. Independent whole-Step review
`phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`: missing exact initial/reloaded semantic
Title comparisons, without alleging production corruption. First formal Step
repair 1's first property attempt completed 1 suite/2 tests with 1 PASS/1 FAIL;
its observation-source correction is now focused-validated in the same repair.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure and exact Step acceptance/commit remain pending. The
[descriptor specification](../spec/display-business-operation-descriptor.md)
defines behavior; the existing [runtime design](display-model-runtime.md)
retains semantic View, Display and ordinary Operation ownership.

## Ownership

`DisplayBusinessOperation` owns stable, instance-independent data types for
scope, binding, canonical Operation reference, descriptive input parameter,
Action descriptor, descriptor and diagnostic. Public candidate case classes
contain no executable handle. Only successful package-scoped admission can
construct the immutable per-screen `Registry`; it has no public constructor
or copy bypass.

Application registration explicitly joins one admitted scoped Action to one
existing same-component Operation. The generated Action owns its label and
binding identity. Component protocol definitions own target identity and
ordered request metadata. Neither presentation state nor binding text is
interpreted as a route, capability, callback or supplied schema.

## Atomic preparation

The existing ComponentFactory Display preparation sequence remains the sole
publication boundary. Business provider selection follows established
component-first/factory-fallback precedence and reads the selected capability
once. Failure never activates a competing provider.

Resource/screen correspondence and every binding/target/input are admitted
before constructing a Registry. Descriptor assembly then walks declarations
to preserve stable output order. The original component protocol is the
target catalog; the prepared Display service is excluded. The optional
registry passes through an additive `DisplayService._bind_c` overload before
the existing complete service publication. No new mutable global registry,
bootstrap lifecycle, rollback mechanism or partial publication is introduced.

## Serialization and read responsibility

`DisplayBusinessOperationCodec` owns one standalone closed schema. Its strict
Jawn parser retains duplicate-key rejection; canonical correspondence checks
the full object/array/scalar structure against the immutable Registry. The
decoder returns admitted metadata rather than reconstructing executable
objects. Existing deterministic escaped JSON rendering preserves Unicode
without changing the Display or mutation codecs.

`DisplayBusinessOperationIngress` implements only the ordinary
`describeBusiness` query. Exact selectors choose admitted metadata; an inert
QueryAction/ProcedureActionCall returns the descriptor JSON. Raw HTTP selector
evidence is checked at this Operation boundary, and existing framework
authorization and JSON envelope remain authoritative. Explicit
`OpenApiOperationProjection` selects GET for this Operation. HTTP router,
dispatcher, security policy, datastore and UnitOfWork implementations are not
part of this change.

The input descriptor deliberately exposes only canonical name, kind,
datatype name and multiplicity mark. This avoids duplicating target
validation rules or disclosing defaults/credentials. Full constraints and
normalization remain with the ordinary target Operation.

## Compatibility and consumer boundary

Absence/empty Business registration preserves the old service selector set.
The new optional metadata Operation is additive. Generated Logical UI ABI,
`DisplayModel.Action`, projection, existing Display read/mutation schemas and
their request/output semantics remain unchanged. Descriptor incompatibility
requires an explicit new schema/version.

P96-08A establishes no Business execution or reload acceptance. P96-08B owns
ordinary direct invocation, canonical domain state transition and explicit
reload proof. A Business success response is not a Display response and does
not promise fresh presentation. Presentation availability never grants
authority. No generic action tunnel, arbitrary URL, nested dispatch,
push/subscription or Service Bus refresh is introduced. Editing Studio,
TFC/TFAF and app acceptance remain separately owned.

## Ordinary development Business/reload proof

The parent-selected normal-update development fixture is strictly opt-in via
immutable `DisplayBusinessReviewFixture.Setup`. With no setup, the final normal
component retains its original protocol, metadata, registration, storage and
lifecycle behavior. Business setup rejects lifecycle/custom-registration/shared-
session combinations; it is not a general extension or callback framework.
Only this Business opt-in also uses the existing trusted lifecycle-schema
preparation before seeding. The independently observed baseline already includes
the declared guard columns; rejected actions cannot conceal later schema changes.
SQL NULL omission from logical Records is preserved.

The setup adds the real `business.requestReview` Operation before bootstrap,
one exact scoped descriptor binding, authenticated CML command metadata and a
Detail Action whose availability reads only the immutable setup. Existing field
definitions and mutation registrations remain, with stage readonly. Provenance
is `cncf.development.display-business` / `canonical-review-<token>` / `fixture-1`.
Raw `stage` is this development workflow's existing storage. This establishes
no ESS2 `workflowState` field mapping; P96-09 retains that responsibility.

The ordinary Operation validates its two required single String Properties,
parses the canonical EntityId and admits only exact positive decimal revision
spelling. Its synchronous CommandAction creates one FunctionalActionCall.
After binding the normal component application datastore, the call uses the
existing observed Entity patch APIs for Embedded/Detached, or the existing
registered Aggregate/root/alias observed snapshot API with a stage-only root
copy. The Aggregate branch first lifts the existing typed
`UnitOfWorkOp.Authorize` into that same program. Its canonical
`AggregateAuthorization.Request` selects the same identity and component,
`command:requestReview`, no supplied access policy, and the default
BusinessService/UserPermission model. This supplies the application's existing
owner/ACL requirement before the observed snapshot command; CML
`authenticated_only`, canonical APIs and Entity native authorization remain
unchanged. UserPermission, canonical concurrency, audit and persistence own effects.
The ordinary command lifecycle owns commit and `afterCommitResponseC`; no
DisplayMutation proxy, nested dispatch, manual commit or framework edit is used.

Complete ordered logical fields and native table columns/types/rows are
specified from pre-state independently of Business/Display output. Only stage,
managed revision and declared update audit/trace fields may differ. Fixed-clock
direct requests require exact owner/time/trace values. Production uses independent
before/after time bounds for the selected update timestamp and exact owner and
every other field/table value. Observer wrappers delegate to the real provider.
Native compare-and-set plans and full-save versioned plans have separate typed
observations; the HTTP assertions require their exact combined provider-attempt
count and zero generic writes. Observation adds no acknowledgement or capture.
Warmed selected/unrelated Views and build counts are observed before the action;
no resident helper that resolves Views is called between Business and explicit
read. Canonical Entity stale reconciliation may evict the selected resident;
it neither rebuilds a View nor changes durable data.

Production setup selects Production mode, standard/system execution and
multi-user authenticated provider convention with fallback disabled. Only two
known session identities admit owner/foreign principals. Actual Http4s requests
carry `x-textus-session`; authentication/owner/admin query facts cannot manufacture
trust. Trusted owner positive execution and explicit read accompany denied and
malformed requests. ControlledTest direct and HTTP cases remain test-only.
Ordinary Business POST supplies its exact Property pairs through the standard
UrlForm body, preserving omitted/duplicate/invalid values and omitting those
selectors from query for this branch. GET read/metadata and non-Business query
behavior, framework/test/forged query facts and the actual session header remain
unchanged.
Only actual accepted AuthenticationResult PrincipalIds are recorded and reset
with the ordinary fixture log; no session or credential is recorded. Foreign
default permission denial precedes the factory hook, so provider identity and
factory authorization are separate evidence. Successful owner execution still
requires actual canonical authorization and committed shared UnitOfWork.

Business JSON contains its canonical outcome, ID and revision, with no Display
result or Browser call. The client explicitly reads selected Detail afterward.
A failed read leaves the original Business response and committed state intact;
retrying the read does not replay the writer. Representative001 failed Test
compilation; representative002 compiled and ran BR1–BR7/BRPG1–BRPG3 with 2
passes and 8 failures. Representative003 completed with 9 passes and 1 failure:
the legitimate owner POST returned 400 for missing `resourceId` before Business
execution because ordinary POST reads form Properties and the fixture supplied
query-only selectors. That positive is not a Business success, and preceding
production negatives were not accepted proof at that checkpoint. Subsequently,
representative004 passed 10/10 tests in two suites, resolving the form-carrier
cause. Accumulator001 completed 40 suites and 332 tests: 331 passed, BRP1 failed,
and zero suites aborted or tests canceled/ignored/pending. Its generated
trim-empty Title failed before mutation with `DSP96_ROLE_INVALID`, rather than
at permission or persistence. The unchanged Title contract requires nonblank
text. Unit 4 (`VAL-P96-08B-LAWFUL-TITLE-DOMAIN-004`) already authors a fixed `T`
prefix around the exact arbitrary payload, retained through every shrink,
including empty payload. The original newline/NUL payload at revision 16 uses
the same complete commit/reload oracle across all six target/representation
combinations. Independent pre-state/reloaded-title equality, all complete state
oracles, at least 100 successes per law and zero discards remain required; no
filtering, trimming, exception catching or expected-failure substitution is
introduced. The source repair passed
`phase96-p96-08b-business-review-property-001` (1 suite/2 tests) and
`phase96-p96-08b-business-review-accumulator-002` (the same 40 suites/332 tests).
Both completed with all tests passing, zero failed/aborted/canceled/ignored/pending,
SBT0/wrapper0 and released serial lock; parent typed result readers accepted
the results for the prior assertions; they do not prove the newly added exact
Title comparisons. Independent whole-Step review
`phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`: the initial logical/Display and reloaded
Display semantic Title were not compared to the independently supplied sample.
No production corruption is alleged. First formal Step repair 1's
`phase96-p96-08-step-title-property-001` completed 1 suite/2 tests with 1 PASS/
1 FAIL, SBT1/wrapper1 and serial lock released. BRP1 failed before Business at
the first newline/NUL revision-16 initial assertion; BRP2 passed. The prescribed
assertion selected raw DataStore `utf16:0054000a0000`, not the decoded semantic
View. This observation-layer error does not demonstrate production corruption.
The same consumed Step repair 1's correction is now focused-validated: BRP1
observes the already captured `before.resident.views.selected.record` title
against independent `sample.title`; initial Display and the already fetched
reload JSON retain their exact comparisons with no additional read.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure remains pending before exact Step acceptance/commit. This makes no List/query lifecycle, whole-Step, Phase or consumer
acceptance claim. All 54 criterion texts and 37 checked/17 pending outcomes
remain unchanged. P96-08 stays IN_PROGRESS with four unchecked criteria;
P96-09 through P96-11 remain OPEN and the full Phase review count remains zero.
