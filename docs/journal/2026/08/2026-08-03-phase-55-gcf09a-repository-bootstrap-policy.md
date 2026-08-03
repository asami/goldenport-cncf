# Phase 55 GCF-09A: Repository Bootstrap Policy

Date: 2026-08-03

GCF-09A moves the first admitted direct-consumer family: repository bootstrap
selection. The existing runtime source map is consumed before a Subsystem
exists, so the policy is explicitly Global. This does not introduce a new
Subsystem configuration authority or expose configuration, candidates,
sources, provenance, trace, or bindings to repository consumers.

The closed catalog now owns repository search, repository component-development,
component directory/development/CAR/file, and Subsystem development/SAR values.
Canonical Textus spellings are authoritative; runtime and CNCF forms remain
decode-only aliases. `RepositoryBootstrapPolicy` is a value-only projection.
`CncfRuntime` resolves it from retained runtime snapshots, assembly defaults,
and admitted pre-sentinel binding envelopes before it selects repositories.

Auto-CAR discovery treats a typed activation as explicit. It therefore cannot
add a cwd archive when a Global typed component or Subsystem repository route
has already been selected. Command-tail text remains outside this policy.

Validation:

- serialized `Test/compile`: `41778-20260803T031027Z`;
- focused catalog, Global policy, and runtime snapshot regression: 12/12 at
  `49715-20260803T032426Z`;
- independent review found and the focused repair closed the typed
  activation/auto-CAR discovery path; final independent re-review is clean.

Remaining GCF-09 work retains the legacy compatibility overload and other
direct configuration consumer families for separate migration slices. This
record does not change Phase 53 historical decisions, Phase 54 lifecycle
design, or Phase 55 deferrals.
