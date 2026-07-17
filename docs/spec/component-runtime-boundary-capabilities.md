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

The runtime binds a tree identity to a provider and enforces unknown-tree,
traversal, symlink, depth, entry-count, per-file-byte, and aggregate-byte
failures before it returns a snapshot. These failures MUST be normal structured
`Consequence.Failure(Conclusion)` values.

## Single-resource Compatibility (R7)

`ResourceAccess` remains a single logical content-read capability. It MUST NOT
gain directory enumeration or resource-tree semantics. A resource tree is a
separate capability and does not change existing `ResourceReference` or
`ResourceContent` semantics.

## Process Execution Tree Input (R8)

`ProcessExecutionResourceTreeInput` represents only an admitted logical tree
snapshot and a WorkArea-relative materialization target. It MUST NOT accept a
component-selected host path. Fixed `ProcessExecutionInputFile` values remain
bounded caller-supplied bytes and are not interchangeable with tree inputs.

Process Execution admission MUST validate the logical tree identity against
the selected program capability before materialization. The runtime performs
materialization inside the owned WorkArea after admission. Request-level limits
may narrow but MUST NOT widen runtime, tree, grant, or program limits.

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
