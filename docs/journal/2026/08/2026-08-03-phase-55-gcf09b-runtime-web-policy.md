# Phase 55 GCF-09B: Runtime Web Policy

Date: 2026-08-03

GCF-09B removes the admitted runtime Web execution policy's fallback to
`ResolvedConfiguration`. `resolveForRuntimeSubsystem` receives only the owning
Subsystem and requires its final typed collection. An unadmitted runtime
Subsystem now fails structurally instead of re-reading legacy Web values.

Both HTTP runtime call paths use this API. Public/pre-admission configuration
decoding remains an explicit compatibility surface and is not runtime
authority. Static Web integration fixtures admit typed values before rendering.

Independent review also found a profile-order defect: assembly candidates were
previously omitted when choosing the execution profile. Admission now resolves
runtime and assembly candidates together before standalone-profile admission,
then resolves the final collection with profile candidates.

Serialized focused validation passed 49/49 at `57779-20260803T033921Z`.
The final independent re-review is clean. This slice leaves the remaining
direct configuration consumer families for later GCF-09 work.
