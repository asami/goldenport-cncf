# Phase 55 GCF-09E: Runtime Component-Development Web Projection

Date: 2026-08-03

GCF-09E projects the Global `textus.component.dev.dir` catalog value through
Subsystem into an admitted, normalized component-development path vector owned
by the runtime HTTP engine. Runtime descriptor merging resolves a directory as
its `web.yaml`; component static Web and manual root lookup use the same vector.

No admitted collection fails structurally. An admitted collection without a
component-development value yields an authoritative empty vector and does not
revive raw `textus.component.dev.dir` or its decode-only aliases. The engine
retains an outer optional descriptor path and component-path vector so that an
admitted absence remains distinct from a legacy direct engine: only the latter
retains the raw compatibility fallback for descriptor, static asset, and manual
roots.

The executable specification covers raw/admitted conflicts, admitted absence,
unadmitted runtime failure, typed static asset/manual roots, and the legacy
compatibility branches. Serialized compile and focused validation passed 10/10
at `30757-20260803T055312Z`; final independent re-review is clean. This record
does not change Phase 53 history, Phase 54 lifecycle design, or Phase 55
deferrals.
