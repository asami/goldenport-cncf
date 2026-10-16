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
profile references. Missing mappings have no inferred default, and ambiguous,
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

## Additional executable specification

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
