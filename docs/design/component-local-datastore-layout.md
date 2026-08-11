# Component Local Datastore Layout Design Rationale

Status: non-normative design rationale
Date: 2026-08-10

Normative datastore identity, configuration precedence, source exclusions, and
migration responsibilities are defined in
`docs/spec/component-local-datastore-layout.md`.

## Intent

Component-local state needs a namespace-preserving operational location so
components that share a local ID do not accidentally share persistence. The
`components` and `datastores` path segments distinguish runtime-managed
databases from configuration, artifacts, and other component-owned files.

The design deliberately keeps the canonical Component identity visible in the
directory hierarchy rather than flattening it into a second persistence naming
convention. This preserves the identity boundary readers use elsewhere in CNCF.

## Consequences

Existing local data can require an operator-managed move when a runtime adopts
canonical component identity. CNCF does not take ownership of automatic backup,
copy, merge, deletion, or rollback. The normative specification records the
safe migration checklist and required verification steps.

This design does not introduce instance-scoped storage, infer a directory from
process/port/catalog metadata, or change an explicitly configured datastore
path.
