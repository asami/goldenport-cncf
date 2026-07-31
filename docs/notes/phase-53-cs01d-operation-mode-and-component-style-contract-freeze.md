# Phase 53 CS-01D - OperationMode and ComponentStyle Contract Freeze

Status: CS-01D in progress.  This non-normative record freezes semantic
boundaries and failing-first evidence only.  It does not introduce a wire
format, public implementation API, or production behavior.

## OperationMode classification

`RuntimeConfig` defines runtime posture; `RuntimeContext` carries it.
Runtime/Web/security/inspection consumers retain that role: execution-profile
activation, HTTP production redaction and debug controls, observability,
operator inspection, and posture-sensitive framework authorization are not
Component domain policy.

The prohibited path is the transit of that posture through Component-facing
surfaces.  `ExecutionContext.CncfCore.Holder` currently exposes both
`operationMode` and `runtime`; `ComponentLogic` copies mode into a Component
runtime context; `ComponentCreate` and `ComponentInit` expose `Subsystem`;
factory hooks and ActionCall code can therefore reach runtime policy.  Phase 53
freezes their removal from the permanent Component-facing contract.  CS-04
chooses the replacement mode-free context and preserves valid framework
security behavior.

`WebApplicationMode` remains Web-only.  Its current source is
`WebExecutionResolution` with `textus.web.execution.application-mode` and an
unconditional standalone default.  Phase 53 selects
`textus.web.application-mode`, resolved before FixedUserProfile with a
conditional, traceable standalone default.  Launchers remain forwarding
adapters: their command `mode` is command/server/client routing, not this
runtime or Web mode authority.

## ComponentStyle semantic contract

The built-in CNCF catalog is immutable.  Each catalog entry is versioned and
distinct from provider and metadata-schema identities; the identity encoding
itself remains open.  The initial selection is
`full-fledged-with-standalone`; it provides `domain.full@1`,
`user.multi-user@1`, and `user.fixed-context-compatible@1`, and requires
`user-context.current@1`, `datastore.persistent@1`,
`datastore.transactional@1`, and
`datastore.optimistic-concurrency@1`.

`domain.full@1` expands deterministically into Entity, Aggregate, Command,
Query, domain-event, projection, persistence, transaction, and optimistic
concurrency capabilities.  Duplicate built-in identities, missing styles,
unknown/cyclic/incompatible bundle expansion, and generator/runtime catalog
disagreement fail structurally.  Catalog metadata and generated descriptors
contain no Component mode, WebApplicationMode, OperationMode, fixed user,
locale, datastore policy/provider, or principal-origin discriminator.

Cozy generation and CNCF runtime consume semantically identical catalog data.
The generated descriptor snapshots style/provider/schema identity, declared
bundles, effective expansion, required capabilities, mode-free parameters if
any, and implementation evidence.  A future Metadata Factory cannot silently
replace a CNCF built-in; its discovery/loading/packaging/conflict behavior is
future work.

## Proven transport and pending evidence

Cozy owns packaged descriptor encoding through `CozyArchivePackager`; CNCF
owns packaged decoding through `ComponentDescriptor` and
`ComponentDescriptorLoader`; development evidence hashes
`src/main/car/component-descriptor.json` through
`CozyDevelopmentRuntimeManifest`.  `PredefinedResultCatalog` and
`McpToolCatalog` demonstrate explicit validation, deterministic order, and
duplicate rejection, but are not ComponentStyle APIs.

CS-01 pending evidence consists of the two CML source-selection fixtures,
Component-facing ExecutionContext/factory boundary, and canonical Web mode
selection.  `WebExecutionResolutionPolicy.fromConfiguration` has no input for
style eligibility or source trace, so the conditional, traceable standalone
contribution is deferred to CS-05 rather than represented by a false
configuration-only test.  Likewise, the current `ComponentDescriptor` exposes
no structured style/capability projection: a scalar scan would neither prove
the graph nor remain independent of the future representation.  CS-02 creates
that public observation boundary and registers descriptor semantic acceptance
there.  These contracts name required behavior without naming a future JSON
key, resource path, Scala type, accessor, constructor, provider spelling, or
schema version.

## Explicitly not frozen

`apiVersion`, descriptor `schemaVersion`, serialization nesting/order, catalog
resource path, and Scala API remain CS-02 implementation decisions.  The
future exact replacement for Component-facing `RuntimeContext` access remains
CS-04 work.  This slice changes no production source, launcher, ArtScene
behavior, normative design, or specification.
