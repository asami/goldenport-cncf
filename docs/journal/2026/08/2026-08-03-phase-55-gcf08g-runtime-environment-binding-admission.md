# Phase 55 GCF-08G — Runtime Environment Binding Admission

Date: 2026-08-03

GCF-08G activates the canonical `TEXTUS_BINDING_...` environment boundary in
`CncfRuntime`. The supplied map is partitioned before either compatibility
environment source is constructed. Canonical names decode through the existing
GCF-08B codec into typed references paired with opaque raw values; ordinary
environment entries remain in the residual map. Malformed, noncanonical,
alias, or unadmitted binding names fail structurally with a fixed message that
does not render the supplied name or value. `CncfMain` consumes this bootstrap
`Consequence` directly, so the CLI does not demote that failure to an untyped
exception.

The residual map alone reaches `ConfigurationSource.Env`, so admitted binding
names cannot become `ResolvedConfiguration` keys or legacy trace authority.
Typed values are attached to the existing Environment candidate batch with the
same provenance rank, ordinal, identity, collision domain, and source type.
The normal candidate constructor therefore detects a same-source canonical
collision, while bindings for different targets stay distinct. The same
assignment vector is supplied to both Global bootstrap and final Subsystem
resolution.

This slice changes no file grammar or loader, command-argument behavior,
launcher interface, consumer migration, or diagnostic serializer. Launchers do
not parse binding names or receive parameter semantics.

`Test/compile` plus `CncfConfigurationEnvironmentBindingAdmissionSpec`,
`CncfConfigurationEnvironmentBindingCodecSpec`,
`CncfRuntimeConfigurationProjectionSpec`, `CncfRuntimeSnapshotBootstrapSpec`,
and `Phase55RuntimeConfigurationBoundarySpec` passed 16/16 through serialized
SBT invocation `26634-20260803T105315Z`. Independent review found that an
earlier bootstrap helper had demoted the failure to an untyped exception.
`bootstrapC` and `CncfMain` now retain and render the structured failure; the
review-fix `Test/compile` and focused suite passed 17/17 at
`30880-20260803T105926Z`. Final focused re-review is clean.
