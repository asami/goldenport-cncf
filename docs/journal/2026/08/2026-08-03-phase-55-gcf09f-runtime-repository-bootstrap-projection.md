# Phase 55 GCF-09F: Runtime Repository/Bootstrap Projection

Date: 2026-08-03

GCF-09F closes the runtime repository/bootstrap projection around the
value-only `RepositoryBootstrapPolicy`. The policy owns the eight repository
source families—repository, repository component-development, component
directory/development/CAR/file, and Subsystem development/SAR—and carries the
normalized bootstrap directory for relative values.

Legacy repository CLI forms are decoded once at the bootstrap boundary into
that policy and removed from the later runtime argument stream. Later runtime
consumers interpret no repository value flags: the only retained repository
control is pre-sentinel `--no-default-components`. Compatibility component-file
arguments are projected from the admitted policy, never copied from raw argv.

Default, Generic, and Textus Identity runtime factories require the admitted
policy and fail structurally without it. Component discovery receives the
policy-derived active specifications directly, so it does not recreate a
repository authority from `ResolvedConfiguration`. Admitted relative values
resolve from `policy.baseDirectory`; only default repository discovery uses the
caller-provided working directory.

The executable specifications cover every legacy argv family, missing and
empty policy, raw/admitted conflicts, Textus Identity, relative repository and
component paths, and distinct policy/caller directories. Serialized compile
and focused validation passed 56/56 at `1531-20260803T075931Z`; one existing
Textus Identity default-repository case was canceled because its local default
CAR fixture is absent. Independent re-review closed the raw component-discovery
and post-bootstrap argv findings. This record does not alter Phase 53 history,
Phase 54 lifecycle work, or Phase 55 deferrals.
