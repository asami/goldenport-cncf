# Phase 55 GCF-09C: Runtime Web Descriptor

Date: 2026-08-03

GCF-09C registers `textus.web.descriptor` as an optional Subsystem-scoped
catalog witness. A runtime Subsystem exposes only the admitted normalized path;
it never re-reads the raw resolved configuration after admission.

`WebDescriptorResolver.resolveForRuntimeSubsystem` and
`HttpExecutionEngine.Factory.forRuntime` require the admitted binding
collection. An admitted collection may omit the optional descriptor value and
then resolves no configured path; server, server-emulator, and Static Web
construction propagate a structured failure only when the collection itself was
not admitted. The HTTP descriptor and static-resource root receive the same
admitted path, preventing a raw descriptor from changing either one after
admission.

`RuntimeWebDescriptorProjectionSpec` proves that conflicting raw and admitted
descriptor paths select the admitted descriptor and its static asset root, and
that a runtime engine cannot be created without admission. Existing Static Web
integration fixtures now use the runtime engine factory.

Serialized compile and focused validation passed 54/54 at
`87960-20260803T043441Z`; final independent re-review is clean.

Component-development Web descriptor/root paths retain their explicit raw
compatibility boundary and remain deferred to a later GCF-09 consumer slice.
This record does not change Phase 53 history, Phase 54 lifecycle design, or
Phase 55 deferrals.
