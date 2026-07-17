# CBD Support Runtime Boundary Handoff (2026-07-17)

status=handoff
updated_at=2026-07-17
tag=cbd-support, runtime-boundary, configuration, resource-access, process-execution

## Position of This Record

This journal records framework-extension candidates discovered while planning
CBD Support's `FUTURE-CBD-RUNTIME-BOUNDARY-01`. It is non-normative. A
selected extension must be promoted to CNCF design and specification documents
and receive executable specifications before it is treated as available to a
component.

The request concerns CBD Support's own runtime implementation. It does not
add a CAR Review rule, alter CBD-owned canonical Review Reports, or make Cozy,
sbt-cozy, or a reviewed CAR a framework dependency.

## Driver

CBD Support currently has runtime code that obtains wall-clock time, process
roles and source configuration from the ambient host environment, reads local
development/CAR directories directly, and launches the bounded Cozy evidence
provider through a direct JVM process transport. CAR lint correctly identifies
these as ambient clock, environment, filesystem, and shell/framework-boundary
warnings.

CBD Support can remove those direct host accesses only if CNCF supplies or
confirms explicit component-facing capabilities for each authority. The
component must receive logical, admitted values and resources rather than a
general host `Clock`, environment map, `Path`, process handle, or executable
path.

## Confirmed Current Direction

`UnitOfWorkOp.ProcessExec` and the normative
`docs/spec/process-execution-runtime.md` already define CNCF's process
execution direction. CBD Support must use that capability rather than add a
parallel `ProcessBuilder` wrapper or request a Cozy-specific framework API.

The existing Process Execution contract also establishes useful constraints:

- a component submits logical execution intent, not an executable or shell
  string;
- program identity, environment policy, Working Area, limits, and driver are
  selected by runtime admission;
- input and output artifacts are WorkArea-confined and bounded; and
- a deterministic fake driver is available for executable specifications.

This record therefore asks for integration and adjacent runtime capabilities,
not a second process abstraction.

## Extension Candidates

### 1. Context-provided execution time

CNCF should confirm or define a scoped execution-time capability available
through `ExecutionContext`/`RuntimeContext`. It must provide an explicit
instant/clock to component runtime services and an overridable deterministic
test implementation. Component code must not construct `Clock.systemUTC()`
for operational decisions.

The capability should distinguish stable logical input from observability time.
It must not silently place volatile timestamps into stable content digests.

### 2. Typed runtime configuration and secret-reference resolution

CNCF should provide a declared configuration resolver for component runtime
configuration. A component requests a known logical key and receives a typed
value, absence, or structured configuration failure. It must not read
`sys.env`, `System.getenv`, or an unrestricted environment map.

The contract needs provenance and confidentiality categories:

- public configuration values suitable for diagnostics;
- secret references that are resolved only at the authorized runtime boundary;
- redacted/unavailable outcomes; and
- deterministic test configuration independent of the developer host.

CBD Support consumers include information-source declarations, allowed origins,
source-authentication references, and local CLI process roles. Actual
credential values must remain outside component-visible configuration and
ordinary diagnostics.

### 3. Admitted read-only local resource trees

CNCF should provide a logical resource-reference capability for a bounded,
read-only local directory tree. Runtime configuration resolves a named resource
reference to an authorized root; component code can inspect or materialize only
that admitted tree under limits for depth, file count, individual/total bytes,
and symlink behavior.

The component must not receive an arbitrary host `Path` or infer a root from a
request. For external-tool execution, the capability should materialize an
admitted bounded snapshot as a Process Execution WorkArea input. The provider
then receives the WorkArea-relative project root rather than an unrestricted
developer directory.

This is required for CBD Support's registered development directory, local CAR
cache, and admitted Cozy Review target. It is not a request for server-side
inspection of arbitrary client paths.

### 4. CBD Support use of Process Execution

CNCF should document and verify the component integration pattern for a named
external-tool capability such as a CBD-configured Cozy evidence provider:

- runtime registration supplies the executable, fixed command template,
  allowed arguments, empty/inherited environment policy, limits, and grant;
- CBD Support submits only the provider request, admitted WorkArea input,
  declared output artifact, and permitted request-level tightening;
- response bytes are read from the declared bounded artifact and converted by
  CBD Support into its existing provider result/failure vocabulary; and
- timeout, cancellation, launch, output-limit, and non-zero-exit outcomes
  remain distinct until the CBD provider adapter maps them.

The framework contract must remain provider-neutral: it must not name Cozy,
CAR Review, or CBD Support types.

## Required Executable Evidence

Before CBD Support migrates, CNCF should demonstrate:

1. a component sees a deterministic scoped clock and cannot fall back to a
   host clock through the normal runtime path;
2. known configuration keys resolve through the runtime while unknown, secret,
   or unavailable values produce safe structured outcomes without leaking
   values;
3. an admitted local tree refuses traversal, symlink escape, and over-limit
   reads, and supports a deterministic fake/in-memory implementation;
4. only a runtime-admitted tree can be materialized into a Process Execution
   WorkArea; arbitrary component/request paths cannot become tool inputs; and
5. a fake Process Execution driver proves fixed-vector execution, bounded
   artifact collection, timeout/cancellation, and confidentiality without a
   live Cozy installation.

## Non-Goals

- No generic component access to host environment variables, paths, shell
  text, process handles, or executable paths.
- No Cozy-specific, CAR-specific, or Review-specific type in CNCF.
- No automatic network, credential, deployment, or publication capability.
- No reopening of the settled `ProcessExec` model merely to preserve a legacy
  direct-process API.

## Promotion Order

1. Audit existing `ExecutionContext`, `RuntimeContext`, resource-reference,
   and Process Execution APIs to identify reusable contracts.
2. Promote only genuine gaps to design and static specification documents,
   keeping configuration, local-resource access, and Process Execution
   responsibilities separate.
3. Add deterministic fake-runtime executable specifications for the selected
   APIs.
4. Migrate CBD Support in its separately scoped runtime-hardening phase.
5. Re-run CBD Support CAR lint and record which ambient-boundary warnings are
   resolved; the first released CAR ABI baseline remains a separate task.

## Planning Annotation (Jul. 17, 2026)

Phase 37, `Downstream Runtime Boundary Adoption`, is now the planned CNCF
coordination record for the migration described here. It starts only after
Phase 36 closes and is owned by the CBD Support downstream implementation. The
phase does not change this handoff's non-normative status or add CBD Support,
Cozy, CAR Review, or CAR ABI types to CNCF.

## References

- `textus-cbd-support/docs/strategy/textus-cbd-support-development-strategy.md`
- `textus-cbd-support/docs/journal/2026/07/car-review-p5-66-final-closure-2026-07-16.md`
- `docs/design/process-execution-runtime.md`
- `docs/spec/process-execution-runtime.md`
- `docs/journal/2026/07/managed-process-codex-provider-handoff-2026-07-17.md`
