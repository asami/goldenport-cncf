# Component Runtime Boundary Capabilities

Status: normative static contract

Architectural context is described in
`docs/design/component-runtime-boundary-capabilities.md`.

## Scope

This specification defines the component-visible runtime capability boundary
for execution time, declared configuration, secrets, read-only resource trees,
and Process Execution tree inputs. It complements, and does not replace,
`docs/spec/resource-reference-dsl.md` and
`docs/spec/process-execution-runtime.md`.

## Capability Source (R1)

Every component-visible operational capability MUST be supplied through the
bound `ExecutionContext` or a protected internal DSL operation derived from
it. A reusable component MUST NOT use ambient JVM or operating-system state to
obtain time, configuration, credentials, filesystem paths, resource roots,
network handles, executable locations, or process handles.

## Execution Time (R2)

Component-observable execution time MUST be sourced from the clock bound to
`ExecutionContext`. The protected internal DSL is the component access path.
An implementation MUST NOT fall back to a host clock when an execution context
is bound. Monotonic duration measurement remains distinct from business time.

## Declared Typed Configuration (R3)

Component configuration MUST be declared by a typed
`ComponentConfigurationKey[A]` and obtained through
`ComponentConfigurationAccess`. The declaration includes key identity,
decoder, required-or-optional semantics, and confidentiality classification.
Undeclared lookup is not a component runtime configuration operation.

A required missing value, malformed value, or policy-denied value MUST return
a structured `Consequence.Failure(Conclusion)`. A component configuration
result MUST provide a provenance category without exposing an unrelated raw
configuration source or confidential value.

The canonical component access path is the protected ActionCall internal DSL.
It accepts only a declared key and returns the decoded optional-or-required
result with `Component`, `Subsystem`, `Runtime`, or `Absent` provenance. It
does not expose a raw configuration map or arbitrary key lookup operation.

## Component Initialization Parameters (R3a)

Initialization parameter resolution MUST occur after CNCF has selected the
component type and `ComponentInstanceId` and before component-specific
construction or initialization consumes parameter values. A component factory
or equivalent component definition MUST own the parameter declaration and its
component-domain initialization projection. CNCF MUST own context selection,
admitted layer precedence, typed decoding, required-or-optional handling, safe
provenance, structured failure, and bootstrap delivery.

The parameter declaration MUST be available before component-specific
construction. A required missing value, malformed value, ambiguous
component-instance context, policy-rejected value, or invalid domain
projection MUST produce `Consequence.Failure(Conclusion)` and MUST prevent the
partially initialized component from becoming visible in the subsystem.

The canonical factory contract MUST declare keys through
`initializationParameterDeclarations` and create participants through
`createPrimaryC` or `createComponentletC`. CNCF MAY allocate a neutral
`Component` and `Component.Core` first to establish final identity, but that
allocation MUST NOT consume declared initialization values. Resolution MUST
complete before `initialize_component_c` or an equivalent component-domain
projection runs. Successful resolution MUST be delivered through
`ComponentInit.initializationParameters` and retained as the component's
immutable `initializationParameters` snapshot.

Factories with no declarations MUST receive
`ComponentInitializationParameters.empty` without requiring descriptor or
instance metadata. Componentlets MUST resolve against their own runtime
identity and the owning descriptor/assembly-instance context. Special-component
initialization MUST receive the same snapshot. Consequence-aware factory,
bootstrap, and subsystem admission paths MUST preserve a failed `Conclusion`
and MUST NOT install the failed participant.

One successful resolution MUST produce an immutable initialization snapshot
bound to exactly one `ComponentInstanceId`. Component initialization code MUST
NOT receive `ResolvedConfiguration`, a raw or untyped configuration map, a
configuration source reader, a physical source location, or an arbitrary-key
lookup API. Request parameters, action properties, ambient environment or
system-property access, and operation-time configuration lookup MUST NOT
override or mutate the snapshot.

`ResolvedConfiguration` MUST remain a raw source-resolution result without
component declaration or domain semantics. `ComponentConfigurationAccess`
MUST remain the separate operation-time protected DSL boundary. Neither
initialization nor operation-time access may act as an implicit fallback for
the other.

Initialization provenance MAY expose only bounded logical layer and parameter
identity. Default results, diagnostics, and observability MUST NOT expose
parameter values, secret references or material, credentials, physical source
locations, or unrelated configuration keys. Secret material MUST NOT cross the
component initialization boundary; only an opaque secret reference admitted by
the initialization parameter contract may be delivered.

`ComponentParameterKey[A]` MUST be distinct from the operation-time
`ComponentConfigurationKey[A]`. It MUST carry a typed decoder and
required-or-optional semantics. `ComponentParameterResolver` MUST expose only
typed key resolution publicly; source and context lookup MUST remain protected
CNCF runtime behavior. Its result MUST be
`Consequence[ComponentParameterResolution[A]]`.

`ComponentParameterProvenance` MUST be a finite, non-physical vocabulary for
packaged default, assembly default, subsystem instance, runtime configuration,
test overlay, and absence. It MUST NOT carry a path, environment variable,
argument spelling, provider handle, credential, or raw value.

`ComponentInitializationParameters` MUST contain only successfully resolved
declarations and MUST resolve only the exact `ComponentParameterKey` identities
used to construct that snapshot. Duplicate declaration names MUST fail before
snapshot construction. The public snapshot surface MAY expose typed resolution
and bounded counts; it MUST NOT expose entries, raw values, a map conversion,
arbitrary name lookup, `ConfigurationValue`, or `ResolvedConfiguration`.

Initialization parameter layers have this fixed low-to-high precedence:

1. packaged component defaults;
2. component-local assembly defaults;
3. subsystem/SAR component-instance settings;
4. the component-context projection of resolved runtime configuration; and
5. an explicitly selected test descriptor overlay.

The resolver MUST search these fixed layers from highest to lowest. A present
higher-precedence value MUST be decoded or rejected; malformed or explicit
null values MUST NOT fall through to a lower layer. The runtime layer MUST
retain resolved values only and MUST NOT deliver `ResolvedConfiguration` trace
or physical source metadata. The test overlay MUST remain separate from the
runtime layer and MUST exist only when an explicit test descriptor is selected.

The admitted layers MUST be represented by fixed CNCF-owned positions, not a
caller-supplied ordered collection. Packaged defaults, assembly defaults, and
subsystem-instance settings MUST have distinct CNCF-private source types so
their provenance cannot be exchanged positionally. Runtime and explicitly
selected test values MUST be projected atomically; keys owned by the test
descriptor MUST be removed from the runtime layer and retained only in the
test-overlay layer. Request/action properties, operation-time configuration,
system properties, ambient environment variables, arbitrary configuration
readers, and implicit test-file discovery MUST NOT participate.

Every resolver MUST be bound to one validated `ComponentParameterContext`.
The context MUST identify exactly one `ComponentId`, one coherent
`ComponentInstanceId`, one packaged `ComponentDescriptor` that owns that
runtime component, and one admitted assembly `ComponentInstanceMetadata`
record for the requested instance. Descriptor ownership MAY include a declared
componentlet; in that case the componentlet retains its own runtime component
identity while using the owning assembly component's instance name and
settings.

`ComponentDescriptor.componentName` MUST be authoritative for runtime
component ownership when present. `ComponentDescriptor.name` MAY be used as a
fallback only when `componentName` is absent; an artifact name MUST NOT claim a
different runtime component or its instance settings.

Missing or multiple matching descriptors, missing or multiple matching
instance metadata records, and a `ComponentId`/`ComponentInstanceId` mismatch
MUST fail structurally before parameter lookup. Only the selected instance
metadata MAY construct the subsystem-instance layer. That layer MUST NOT have
an independent arbitrary-configuration constructor, and settings admitted for
one `ComponentInstanceId` MUST NOT be visible to a resolver bound to another
instance. `ComponentParameterContext` and its descriptor/assembly inputs MUST
remain CNCF-owned and MUST NOT be exposed through the component-visible
initialization snapshot.

## Configuration Precedence (R4)

The runtime resolves declared configuration according to explicit component,
subsystem, and runtime precedence. Operation parameters, request properties,
and arbitrary properties MUST NOT override a protected declared value. A
component may not re-resolve configuration from system properties, environment
variables, or configuration files.

## Opaque Secret References (R5)

`SecretReference` is an opaque component-visible value. It MUST NOT expose a
secret value accessor, provider handle, physical location, credential bytes, or
credential text. Only an authorized runtime-owned provider or driver may
resolve a secret value, and that value MUST NOT be returned through the normal
component runtime API or diagnostics.

`ComponentConfigurationKey.requiredSecretReference` and
`optionalSecretReference` are the only declared-configuration constructors for
this category. They decode a configured locator into an opaque reference; a
component cannot combine a secret classification with a raw-string decoder.
`Confidential` configuration remains unavailable from the component API. The
runtime-internal resolver and its material type are not bound into
`ExecutionContext`, `ActionCall`, or an SPI socket for component consumption.

## Admitted Read-only Resource Trees (R6)

`ResourceTreeReference` identifies a named logical resource tree.
`ResourceTreeAccess` returns an immutable `ResourceTreeSnapshot` with
deterministically ordered `ResourceTreeEntry` values subject to declared
`ResourceTreeLimits`. The public values MUST NOT expose a physical root, host
`Path`, provider handle, credential, or mutable directory operation.
`ResourceTreeSnapshot` construction MUST remain runtime-private; component
code may retain and submit an admitted snapshot but MUST NOT construct one
from arbitrary paths or bytes.

The runtime binds a tree identity to a provider and enforces unknown-tree,
traversal, symlink, depth, entry-count, per-file-byte, and aggregate-byte
failures before it returns a snapshot. These failures MUST be normal structured
`Consequence.Failure(Conclusion)` values.

The initial local runtime binding key is
`textus.resource.tree.file-roots`, whose values are
`<logical-tree-name>=<absolute-path>` bindings. Runtime and CNCF aliases MAY
provide the same setting. A component-visible value, protected DSL result,
provider metadata value, CallTree record, metric, or diagnostic MUST NOT
contain the bound physical path. The initial local-provider symlink policy is
deny-all. A future policy extension MUST remain root-confined and explicitly
specified.

## Bounded Resource Tree Query (R6a)

`ResourceTreeQuery` MUST remain distinct from `ResourceTreeAccess.snapshot`.
It returns only a bounded immutable result for selected entries and MUST NOT
construct, expose, or imply a complete `ResourceTreeSnapshot`.

The initial selector vocabulary MUST contain only
`ResourceTreeEntrySelector.ExactLeafName`. The selected name MUST be a safe
bare file name: it contains neither a path separator, a control character, nor
`.` or `..` as a path segment. CNCF MUST NOT assign application semantics to a
selected name; a component may select `project.yaml`, but that is not a
CNCF-special filename.

`ResourceTreeQueryLimits` MUST bound traversal depth, visited directories,
matched entries, matched-entry byte size, and aggregate matched-entry bytes.
Provider/runtime policy establishes the maximum admitted query limits. A
component request MAY narrow those limits and MUST fail when it attempts to
broaden them. Returned entries MUST be regular files and MUST be sorted by
logical relative path. A result MAY expose only the logical tree reference,
logical relative paths for returned entries, entry bytes, effective limits, and
safe count/size metadata.

The standard runtime policy MUST remain finite. Its default query ceiling is
depth 32, 100,000 visited directories, 1,000 matched entries, 16 MiB per
matched entry, and 64 MiB of matched bytes. Runtime configuration MAY set each
finite provider ceiling through `textus.resource.tree.query.max-depth`,
`textus.resource.tree.query.max-visited-directories`,
`textus.resource.tree.query.max-entries`,
`textus.resource.tree.query.max-entry-bytes`, and
`textus.resource.tree.query.max-total-bytes`, including their standard runtime
and CNCF aliases. Invalid or negative values MUST fail runtime configuration
deterministically.

The query is complete within its admitted tree or fails. CNCF MUST NOT silently
return a partial result when a directory cannot be evaluated because the depth
or visited-directory limit has been exhausted; it MUST return a structured
limit failure instead.

The local provider MUST reject a symbolic configured root and MUST NOT follow
a nested symbolic link. It MUST skip a non-matching symbolic link. A symbolic
link or non-regular entry that matches the selector MUST return a structured
failure rather than being followed, represented as a regular file, or silently
accepted. Unknown trees, invalid selectors, unsafe roots, depth/visit/match
limits, per-entry size, and aggregate size failures MUST be normal
`Consequence.Failure(Conclusion)` values. Their diagnostics MUST NOT expose a
physical root, host path, skipped entry path, or entry content.

The component-visible query path MUST be the bound
`ExecutionContext.resourceTrees` capability and its protected internal DSL.
CallTree and metrics MAY expose logical tree identity, selector kind, provider
family, effective limits, visited/matched counts, outcome, and structured
diagnostics. They MUST NOT expose physical roots, returned logical paths, or
entry bytes.

### Generic Tree IR Compatibility (R6b)

The core `org.goldenport.tree.Tree[A]` is a generic structural IR and MUST NOT
be treated as a ResourceTree admission token, provider handle, authorization
grant, or proof of snapshot completeness. CNCF `ResourceTree*` values are the
runtime capability and admission model and MUST NOT expose generic tree
mutation or unrestricted traversal as a way to bypass that boundary.

An admitted complete `ResourceTreeSnapshot` MAY be projected into a generic
`Tree[A]` by runtime-owned code. The projection MUST preserve validated logical
paths and deterministic entry order, and the resulting `Tree[A]` MUST NOT be
accepted back as an admitted ResourceTree without reapplying ResourceTree
validation and limits.

A `ResourceTreeQueryResult` is a sparse selected-entry result. It MUST NOT be
implicitly converted to or described as a complete generic tree, and the
absence of a node from that result MUST NOT be interpreted as evidence that
the node is absent from the admitted source tree. Domain-specific tree read
models, including Tag, Job, and introspection trees, remain outside this
resource capability contract.

## Single-resource Compatibility (R7)

`ResourceAccess` remains a single logical content-read capability. It MUST NOT
gain directory enumeration or resource-tree semantics. A resource tree is a
separate capability and does not change existing `ResourceReference` or
`ResourceContent` semantics.

## Process Execution Tree Input (R8)

`ProcessExecutionResourceTreeInput` represents only an opaque admitted logical
tree snapshot, a WorkArea-relative materialization target, and limits that are
no broader than the snapshot. It MUST NOT accept a component-selected host
path. Fixed `ProcessExecutionInputFile` values remain bounded caller-supplied
bytes and are not interchangeable with tree inputs.

Process Execution admission MUST validate the logical tree identity against
the selected program capability before materialization. A configured
per-tree execution grant MAY further restrict the accepted identity and
limits. The runtime MUST revalidate the snapshot under the effective source,
request, program, and grant limits before materialization. The
`UnitOfWorkInterpreter` MUST materialize fixed inputs and admitted trees inside
the owned WorkArea before `ProcessExecutionDriver.startC`; a driver MUST NOT
be responsible for this admission step. Request-level limits may narrow but
MUST NOT widen runtime, tree, grant, or program limits.

## Lifecycle And Diagnostics (R9)

Materialized trees are WorkArea resources. UnitOfWork terminal cleanup MUST
reclaim them on success, failure, timeout, cancellation, abort, rollback, and
explicit dispose according to `docs/spec/process-execution-runtime.md`.

Diagnostics MAY contain safe logical identities, provenance categories, count,
size, limit, and WorkArea-relative target metadata. Diagnostics MUST NOT
contain configuration values, secret references or values, physical roots,
resource-tree content, host paths, provider handles, or credentials.

## Deterministic Executable Evidence (R10)

Each capability implementation MUST have deterministic executable
specification coverage using an explicit in-memory or fake runtime provider.
The evidence MUST prove that component code cannot bypass the capability
boundary through an ambient host dependency. Live external tools, production
secret managers, and host-specific directories are not required evidence.

Bounded resource-tree query evidence MUST cover deterministic exact-name
selection, result ordering, limit narrowing, symbolic-root rejection,
non-matching symbolic-link skipping, matching symbolic-link rejection, and
payload-safe diagnostics.

## Examples

### E1: Bound ExecutionContext Clock

A component ActionCall created with a fixed clock in its bound
`ExecutionContext` observes that clock, its current instant, and its
context-bound timezone through the protected internal DSL.

### E2: Runtime-controlled ActionCall Clock

A component ActionCall created from a controlled runtime profile observes the
configured runtime instant through the normal
`GlobalRuntimeContext -> RuntimeContext -> ExecutionContext` construction
path.

### E3: Bound Behavior Internal DSL

Behavior created with a bound `ExecutionContext` observes the selected clock
through protected internal DSL helpers and rejects invalid delay input before
waiting.

### E4: Controlled Behavior Delay

Behavior under a controlled manual execution profile advances only the
execution-context-owned clock when it requests a bounded delay.

### E5: Declared Component Configuration

A component declares `ComponentConfigurationKey[String]("provider.mode",
...)` and resolves it only through its protected ActionCall internal DSL. The
runtime selects component configuration before subsystem configuration and
subsystem configuration before runtime configuration. A `test.yaml` assembly
override may supply the component binding's `config` for deterministic
executable specifications. Request properties do not participate in this
resolution. Missing required, malformed, and policy-denied declarations remain
structured failures; optional absence returns `Absent` provenance.

### E6: Opaque Secret Reference

A declared `requiredSecretReference("provider.token")` converts a runtime
configuration locator into a `SecretReference`. The component can retain or
pass that reference to a runtime-owned operation but cannot inspect its locator
or resolve it to bytes. A deterministic in-memory runtime resolver is
executable-specification evidence only; concrete Vault, cloud, and provider SPI
integrations are explicitly deferred.

### E7: Admitted Resource Tree

A component parses the logical `review-target` ResourceTreeReference through
its validated factory and requests it through the protected ActionCall
resource-tree DSL. The runtime resolves the named local root from
`textus.resource.tree.file-roots`, constructs a deterministic immutable
snapshot under configured limits, and records only logical/provider metadata.
Unknown names, unsafe traversal entries, symbolic links, and limit violations
return normal structured failures without exposing physical paths or payload
content.
