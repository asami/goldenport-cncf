# Workflow execution disposition and observation

Status: implementation in progress; not provider-integration acceptance
Owner: CNCF Phase 80 minimum slice driven by sm-workflow Phase 5

## R1 — Generic requirement

The existing `WorkflowProtocolV1.ExecutionRequirement` may carry an explicit
runtime disposition: INLINE or DELEGATED placement, OPTIONAL or REQUIRED
independence, and the requesting execution context identity. An optional opaque
ReasoningClass reference identifies application semantic work, not a model or
provider-specific reasoning control. Application work classification and
concrete profile configuration are outside Workflow semantics.

## R2 — Observation and admission

The owning execution adapter supplies actual placement, origin and executor
context identities and selected profile identity in ExecutionEvidence. These
are observations, not permissions. For an issued disposition, normal result
admission requires its matching observation. INLINE runs in the origin context;
DELEGATED runs in a distinct context. REQUIRED independence requires a distinct
context even when concrete model/effort would match. Missing evidence must not
be replaced with an inferred or fabricated identity.

## R3 — Wire and history

Both WorkOrder requirements and result dispatch requirements preserve the same
extension. Result JSON also preserves the actual execution observation. Legacy
records lacking optional extension fields remain readable and retain absence;
they do not acquire new placement/independence authority. New declared fields
must have a convertible typed representation; unsupported enum values and
malformed field types are conversion errors. Issued WorkOrder readers retain
their declared strict input contract. Returned results preserve harmless extra
annotations and blank profile diagnostics without comparing copied dispatch
requirements or attesting execution. The owning completion consumes its actual
issued disposition and executor relations; a blank actual executor cannot
establish independence merely by differing from the origin. Conversion creates
no execution evidence, hash, expiry or whole-body equality certificate.

## R4 — Provider request

`ProviderExecutionRequest` may carry that same typed execution requirement.
The active UnitOfWork interpreter validates it before provider invocation and
passes the admitted request to the existing Required-SPI resolver/program
provider route. A direct effectful provider remains prohibited. Carrying the
requirement is not itself proof that concrete profile mapping or a real AI
executor is installed.

## R5 — Progression ownership

The existing ActionExecution and continuation/completion semantics remain
unchanged. Execution observations cannot advance a Workflow, accept an
application result, waive independent review or authorize commands. The normal
completion Operation retains those responsibilities. There is no semantic
ORCHESTRATION/CONTINUATION switch or second completion writer.

## Executable specification

## R6 — Canonical Component profile configuration

The optional `workflow.execution.profiles` Component parameter carries an explicit
`cncf.workflow-execution-profile-mapping.v1`: mapping identity/revision, concrete
profiles and semantic-class bindings. CNCF standard Component bootstrap owns
all resolution and precedence. Profiles contain identity, provider, model and
provider-specific control strings; semantic bindings contain only class and
profile references. An optional `fallbacks` array uses the same
`{reasoningClass,profile}` binding shape; absence means no alternate. Each class
may have at most one alternate, must already have a primary, and must reference
an existing profile. The alternate must differ in provider, model or controls;
a different identity naming the same concrete profile is rejected. Existing
strict declared fields, Component precedence and first-issue resolution remain
unchanged. Missing mappings have no inferred default, and ambiguous,
unknown or dangling declared input is a configuration error. Legacy absence
remains absence, not a successful mapped invocation. New component assembly
may supply a changed mapping for subsequent work; never rewrite earlier issued
requirements/evidence or resolve an already issued disposition again.

## R7 — Invocation-time placement

An owning runtime supplies its actual current executor descriptor. A bounded
matching-profile request may resolve INLINE; a differing profile or explicit
context isolation resolves DELEGATED. A TRIVIAL implementation preference may
force INLINE despite a different default profile. Independent review always
resolves DELEGATED/REQUIRED, including when model and controls match. Forced
inline review/isolation is a contradictory request, not a reduced review gate.
Profile identity aliases are not model/controls differences. Comparison here
is the actual profile-selection algorithm, not an artifact integrity proof.

## R8 — Actual executor observation

A resolved execution accepts an observation only from the owning runtime's
actual executor descriptor, matching the concrete profile and selected
placement/independence. Semantic result content must not construct that
descriptor. The mapping and resolver alone neither invoke a provider nor prove
that an implementation was applied, and they grant no command authority.
Supported provider execution and normal application continuation are still
connected acceptance requirements.

## R9 — Finite pure host session

`WorkflowExecutionHarness.prepareIssuedC` consumes the actual issued requirement,
typed WorkReference (WorkflowHandle, run, continuation and expected revision),
original canonical mapping snapshot and actual origin descriptor.
`prepareIssuedJsonC` uses the existing issued requirement decoder. It never
calls `mapping.resolveC` for issued work. The unchanged disposition must identify
the actual origin; reviewRequired retains DELEGATED/REQUIRED. INLINE captures
the actual current profile with no alternate; DELEGATED captures the configured
primary and optional single alternate. The private-construction Session retains
those selections, mapping identity/revision and ordered observations outside
Workflow business state. Later configuration affects new issues only. A missing
original session/snapshot is a host gap, not permission to select a later mapping
for pending work. The kernel performs no storage, process, network or provider IO.

## R10 — Actual outcomes and bounded alternate

`Session.nextSelection` identifies the sole current attempt. `recordC` consumes
it once and returns a new Session. Closed statuses/reasons are:

| Status | Reason | Progression of host selection |
| --- | --- | --- |
| COMPLETED | Completed | Stop; actual matching executor required |
| UNAVAILABLE | ProviderUnavailable | First DELEGATED attempt may select its configured alternate; no executor |
| DECLINED | CapabilityDeclined | Same bounded alternate rule |
| BLOCKED | AuthorityBlocked or ResourceBlocked | Stop |
| FAILED | SemanticFailure or UncertainExecution | Stop |

Every outcome has honest nonblank detail. Host transport/callback uncertainty is
FAILED/UncertainExecution. No second alternate, retry, learned ordering, policy
escalation or INLINE-to-DELEGATED conversion exists. Noncompleted outcomes may
omit their executor; any supplied descriptor must satisfy the selected concrete
profile and issued context relation through `WorkflowResolvedExecution.observeC`.
UNAVAILABLE always omits an executor. Absence of an actual attempt is not an
execution record. COMPLETED means the provider returned work, not that semantic
validation or review accepted it. Provider BLOCKED/DECLINED is not an ordinary
semantic decision-needed result. A valid semantic decision-needed payload keeps
its existing business meaning and independent Human route.

## R11 — Resource observations and completion

Optional elapsedMillis, inputTokens, outputTokens, monetaryCost with currency,
and machineIdentity record only actual host observations. Known numbers must be
nonnegative; cost/currency are paired. Missing values remain explicit unknown,
never zero, estimated prices, rankings or limits. `completionObservationC`
succeeds only for a final COMPLETED attempt and uses its actual final executor.
Neither observations nor completion evidence authenticate an executor, provide
consent, establish independent reviewer provenance or grant effect authority.
Normal owning result admission and separate independent review still apply.

## R12 — Host artifact retention and restoration

`encodeC`/`decodeC` use `cncf.workflow-execution-harness.v1` for genuine host
inputs/records: typed work reference, unchanged requirement, origin, selected
profiles, mapping provenance and ordered outcomes/resources. Restore validates
the finite zero/one/two-attempt path, status/reason relation and actual completed
descriptors without consulting current configuration. Harmless diagnostics add
no authority. This is a host artifact under its existing logical/task resource
binding, not Workflow storage, a full WorkOrder/private-payload copy, hash,
signature, expiry or immutability certificate. Creating/restoring a Session
establishes neither provider availability nor execution permission.

## R13 — Normal wire input and public selected-attempt codecs

`decodeIssuedRequirementC` accepts the declared requirement names and the normal
formatter's consumed aliases `reviewRequired`/`review_required`,
`reasoningClass`/`reasoning_class`, and the disposition's
`originContextIdentity`/`origin_context_identity`. Equal aliases collapse to the
declared name; contradictory JSON values reject. Only those names normalize
before the existing strict issued decoder. Unknown fields, missing required
fields and malformed Boolean/types still reject; missing optional disposition
or semantic class stays absent. No recursive conversion of context or profile
control names occurs. Session `decodeC` continues to consume only its canonical
versioned artifact; this wire adapter is no storage migration or fallback.

`encodeSelectionC(Selection)` exposes the current finite selection as canonical
JSON text with `attempt` (1 or 2) and the existing concrete `profile` object.
The selection and profile must validate. `decodeAttemptC(Json)` consumes the
existing flat attempt representation: `attempt`, `profile`, `status`, `reason`,
`detail`, optional/null `executor`, and `resources` with the optional metrics
from R11. It reuses the Session attempt decoder. Decoding an observation does
not record it or choose an executor: `Session.recordC` still owns current
selection, legal outcomes, resources and actual executor/profile/context
relations. No codec invokes providers, authenticates descriptors or grants
execution permission.

## Additional executable specification

`WorkflowExecutionHarnessSpec` covers E1 issued INLINE/DELEGATED selection,
E2 one unavailable/declined alternate, E3 stopped/exhausted/uncertain outcomes,
E4 concrete independent observations, E5 honest resources and finite artifact
restoration, E6 retained selection across later configuration replacement,
E7 declared/normal-wire alias equivalence and strict refusal, and E8 public
selection/attempt consumption through unavailable/declined, alternate completion
and canonical Session restoration with stale selection/resource refusal.
Mapping E9/E10 cover optional and contradictory configured alternates through
the actual Component Factory bootstrap.

`WorkflowExecutionProfileMappingSpec` uses the normal Component Factory and
parameter bootstrap for declared/instance configuration and changed assembly,
plus table-driven placement and observation contract cases. It does not stand
in for real Codex execution or sm-workflow end-to-end acceptance.

`src/test/scala/org/goldenport/cncf/workflow/WorkflowExecutionDispositionSpec.scala`
covers requirement/result round trips, context separation, legacy absence and
typed input contradictions. `UnitOfWorkStateMachineProviderDispatchSpec` covers
the request reaching the actual program-provider interpreter. Concrete profile
configuration, dispatch and normal sm-workflow completion/restart integration
remain required connected work; these protocol specifications do not close
Phase 80 or sm-workflow Phase 5.

Non-normative context: `docs/phase/phase-80.md` preserves Phase 77 semantics;
`docs/design/cncf-architecture-overview.md` defines runtime ownership.
