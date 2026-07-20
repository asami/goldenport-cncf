# Component Runtime Boundary Capabilities

Status: normative design

## Scope

Reusable CNCF components consume operational inputs only through capabilities
bound to `ExecutionContext`. This design covers component-observable execution
time, declared configuration, opaque secret references, admitted read-only
resource trees, and Process Execution tree materialization.

The behavioral contract is defined by
`docs/spec/component-runtime-boundary-capabilities.md`.

## Capability Ownership

`ExecutionContext` is the component-facing capability boundary. A component
MUST NOT obtain operational values from ambient JVM, operating-system,
filesystem, network, or process state.

The runtime owns:

- configuration source selection, precedence, parsing, and provenance;
- secret value resolution and authorization;
- physical resource-tree root selection and provider binding;
- resource-tree admission, bounded snapshot construction, and WorkArea
  materialization; and
- Process Execution capability admission, driver selection, WorkArea lifecycle,
  and cleanup.

A component owns only its declared configuration keys, logical resource-tree
requests, process capability request, and domain interpretation of returned
values.

## Existing Capability Foundations

The canonical component time authority is the clock already bound to
`ExecutionContext`. Components use the protected internal DSL time operations;
they do not construct or select host clocks.

`ResourceAccess` remains the canonical boundary for one logical resource
content read. It does not enumerate directories or represent a resource tree.

Process Execution retains its admitted `ProcessExecutionRequest`, bounded
fixed input files, WorkArea-relative paths, declared outputs, runtime-owned
driver, and UnitOfWork resource lifecycle. A fixed input file is supplied
bytes. It is not a resource-tree capability.

## Declared Configuration And Secrets

The component configuration contract consists of declared typed keys,
component-scoped resolution access, resolved-value provenance, and structured
`Consequence` failures. Runtime, subsystem, and component configuration are
resolved by the runtime before component access. Request properties, operation
parameters, and arbitrary property lookup do not override a protected declared
configuration value.

Configuration classification is explicit:

- public values may be returned as typed component configuration values;
- confidential values may be consumed only through a runtime-approved safe
  use; and
- secrets cross the component boundary only as opaque `SecretReference`
  values.

No component-facing API resolves a `SecretReference` to credential bytes or
text. Authorized runtime providers and Process Execution drivers resolve secret
values only at their owned execution boundary.

### Initial Typed Configuration Contract

RB-03 establishes `ComponentConfigurationKey[A]`,
`ComponentConfigurationDecoder[A]`, `ComponentConfigurationResolution[A]`, and
`ComponentConfigurationAccess` as the initial public configuration model.
Components consume this model only through the protected ActionCall internal
DSL. Public declarations resolve `component > subsystem > runtime`; their
result carries only the selected provenance category. The implementation does
not publish raw configuration maps or an arbitrary-key lookup API.

`test.yaml` retains its existing assembly overlay role. A test may declare
`assembly.components[].config` to create a deterministic component binding;
normal component construction then merges that binding into the component
application configuration. This does not create a request-parameter override
path. Confidential declarations are deliberately denied from this public value
API until RB-04 supplies an authorized opaque-reference or safe-use boundary.

### Opaque Secret Reference Boundary

RB-04 adds `SecretReference` as a non-product opaque value: it has no public
locator or value accessor and always renders as redacted. The declared
configuration model separates three classifications: `Public` values are typed
component values, `Confidential` values are denied at the component boundary,
and `Secret` values are available only as `SecretReference` through dedicated
key constructors.

`RuntimeSecretResolver` and `SecretMaterial` remain CNCF runtime internals.
They are neither available from `ExecutionContext` nor installed into a
component socket. The in-memory resolver exists solely for deterministic
executable evidence. Production secret-provider selection, authorization, and
driver integration remain downstream runtime work and must not be replaced by
component-visible resolution APIs.

## Resource Trees

A resource tree is a named, admitted, read-only logical capability. Its public
contract consists of a logical identity, a reference, limits, deterministic
entries, and an immutable snapshot. It never exposes a physical root, host
`Path`, provider handle, credential, or ambient directory traversal API.

The runtime binds logical tree identities to providers and physical roots.
Providers enforce traversal, symlink, depth, file-count, per-file, and
aggregate-byte policy before a snapshot becomes visible to a component.
Deterministic ordering is part of the snapshot contract.

### Initial Runtime Binding

RB-05 provides `ResourceTreeReference`, `ResourceTreeLimits`,
`ResourceTreeEntry`, `ResourceTreeSnapshot`, and `ResourceTreeAccess` as the
separate component-facing read-only tree capability. Components construct only
a validated logical reference and request a bounded immutable snapshot through
the protected ActionCall internal DSL. `ResourceAccess` remains a
single-resource read capability. Snapshot construction is runtime-private; a
component can pass an admitted snapshot onward but cannot synthesize one from
arbitrary bytes or paths.

The initial local provider is bound only by runtime configuration:

```text
textus.resource.tree.file-roots=<logical-tree-name>=<absolute-path>,...
```

`textus.runtime.*` and `cncf.*` aliases follow the normal runtime
configuration alias policy. The physical binding is retained by the runtime
provider and is never returned by `ExecutionContext`, the ActionCall DSL,
provider metadata, CallTree, metrics, or structured diagnostics.

The initial local-provider symlink policy is deny-all, including a symbolic
configured root. A future provider may add an explicitly specified policy, but
it must never allow an entry to escape the admitted root. The deterministic
in-memory provider is executable-specification support and does not inspect
the host filesystem.

### Bounded Named-entry Queries

`ResourceTreeQuery` is a distinct read-only capability for discovering a
bounded set of entries in an admitted tree. It does not construct a
`ResourceTreeSnapshot`, and it does not weaken complete-snapshot admission
rules. The initial selector vocabulary is
`ResourceTreeEntrySelector.ExactLeafName`; it selects a validated bare file
name independently of the caller's physical directory layout.

`ResourceTreeQueryLimits` separately bound traversal depth, visited directory
count, matched entry count, one matched-entry byte size, and aggregate matched
entry bytes. Runtime/provider limits are the admission ceiling. A component
request may narrow but never broaden those limits. A query result is immutable
and deterministically ordered by logical relative path. It exposes only the
logical reference, selected entries, effective limits, and safe counts; it
does not expose a physical root, host `Path`, skipped path, provider handle, or
unmatched tree content.

The local provider preserves complete-discovery semantics. If an admitted
depth or directory-visit bound prevents evaluation of a directory whose
descendants would otherwise be in scope, it fails the query rather than
returning a partial result whose missing selected entries cannot be known.

The local provider rejects a symbolic configured root. During query traversal
it never follows symbolic links. A nested symbolic link that does not match the
selector is skipped; a matching symbolic link or matching non-regular entry is
a structured query failure, because it cannot be returned as an admitted
regular-file result. This preserves safe discovery in a development workspace
without treating the workspace as a complete immutable snapshot.

Components access the query through the same bound resource-tree capability
and protected internal DSL as snapshots. Observability records only logical
tree identity, selector kind, provider family, effective limits, visited and
matched counts, outcome, and structured diagnostics. It never records a
physical root, logical entry path, or entry content.

## Process Execution Materialization

An admitted resource-tree snapshot may be represented only through
`ProcessExecutionResourceTreeInput`: an opaque snapshot, a validated
WorkArea-relative target, and optional narrower limits. It is distinct from
`ProcessExecutionInputFile`, whose bounded bytes are caller-supplied fixed
files. Neither type accepts a component-selected host path.

Tree admission and Process Execution admission are separate checks. The
selected Process program declares permitted logical tree identities and their
maximum limits; an optional execution grant may further restrict both. A
request may narrow its snapshot limits but cannot broaden them. The resolved
tree is revalidated under the effective tree/program/grant cap before a
filesystem write occurs.

The `UnitOfWorkInterpreter`, not an individual Process driver, materializes
fixed files and admitted trees and prepares declared output parents before
calling `ProcessExecutionDriver.startC`. Every trusted driver consequently
receives the same prepared runtime-owned WorkArea. UnitOfWork cleanup applies
equally to fixed input files and materialized tree inputs on every terminal
path.

## Provider-neutral External-tool Pattern

A runtime Process program definition supplies the executable location, fixed
argument template, validated fixed environment bindings, limits, input/tree
policy, output policy, and capability grant. A component/provider submits only
the logical request, admitted WorkArea inputs, declared outputs, and narrower
limits through the protected Process Execution DSL. Components never supply a
shell string, executable location, host environment map, or host path.

`ProcessExecutionResult` remains a neutral lifecycle result. A provider adapter
maps explicit terminal outcomes, including non-zero exits, to its own result or
failure vocabulary after the runtime has completed. CNCF keeps no provider,
CBD Support, CAR Review, Cozy, or application-specific adapter type.

## Observability And Testability

Diagnostics record only safe structural metadata: declared configuration key
identity and provenance category, secret classification, logical tree identity,
snapshot counts and limits, Process Execution capability, and WorkArea-relative
materialization target. They do not record configuration values, secret
references or values, physical roots, tree content, or host paths.

Every capability has deterministic in-memory or fake runtime support for
executable specifications. Production secret providers, physical-tree
providers, and external Process Execution drivers remain runtime-owned
implementations of the same contracts.

RB-05 records tree snapshot success/failure through the standard DSL
chokepoint and a `resource-tree.snapshot` runtime metric. These diagnostics
contain only the logical tree name, provider family, configured limits, and a
structured diagnostic projection on failure. They never contain a physical
root, entry path, or resource-tree payload.

RB-07 uses `ProcessExecutionTestProfile` and a test-local provider adapter to
prove fixed-template admission, fixed runtime environment, bounded inputs and
outputs, and distinct terminal-result conversion without a live external tool.
