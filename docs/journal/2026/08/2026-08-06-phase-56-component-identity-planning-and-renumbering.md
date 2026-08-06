# Phase 56 Component Identity Planning and Phase Renumbering

date = 2026-08-06
status = accepted-planning

## Decision

Phase 56 is assigned to namespace-qualified Component identity and derived
artifact/runtime coordinates.

The canonical identity inputs are:

```text
namespace + local id
```

For the official Textus User Account CAR:

```text
namespace: org.simplemodeling.textus
id:        UserAccount
```

The canonical metadata term is `namespace`. Maven organization/groupId and
JVM package are projections of it; `package` is not the shared metadata field.

The qualified runtime Component ID is
`org.simplemodeling.textus.UserAccount`. The artifact name
`textus-user-account`, generated class `UserAccountComponent`, JVM package
`org.simplemodeling.textus.useraccount`, normalized path `user-account`, and
CAR/Maven coordinates are deterministic projections rather than separately
authored identities.

`version` remains independent release metadata. Display name, title, summary,
and localization remain presentation metadata.

## CAR Migration Policy

Phase 56 migrates all admitted development CARs whose effective version is
SNAPSHOT at the phase inventory freeze. This is a complete cohort migration,
not only a User Account or representative-sample migration.

CARs that are not currently SNAPSHOT keep their published identity shape for
the current release and migrate when their next version enters development.
CAR lint detects both states: legacy SNAPSHOT identity is an error, while
legacy non-SNAPSHOT identity is a next-version migration warning. Once a
deferred CAR advances beyond the recorded current release version—normally to
the next SNAPSHOT, but also when moving directly to another release—the
warning becomes an error until migration is complete. Canonical/derived
disagreement is always an error.

## Phase Renumbering

The previously planned Phase 56 and later work moves one number later:

| Previous | Current | Work |
| --- | --- | --- |
| Phase 56 | Phase 57 | Component Resource SubComponent Foundation |
| Phase 57 | Phase 58 | Component Documentation and AI Knowledge Integration |
| Phase 58 | Phase 59 | Component Admin and Documentation Visibility |
| Phase 59 | Phase 60 | Information CML Runtime Canonicalization |
| Phase 60 | Phase 61 | Web Session CSRF Unification |

Current phase files, checklists, living strategy entries, and implementation
notes use the new numbering. Existing dated journals retain their filenames
and original historical statements; they are chronological records, not the
current phase ledger. This journal is the authoritative mapping when a dated
journal uses the previous number.

## Rationale

The earlier descriptor proposal retained both artifact `name` and runtime
`component` as independently authored identifiers and lacked a namespace for
Maven/JVM identity. That shape creates avoidable divergence and cannot safely
distinguish equal local IDs owned by different organizations.

A namespace-qualified typed identity removes that split while still allowing
ecosystem-specific projections. The phase is intentionally scheduled before
Resource SubComponent work because repository, packaging, documentation, and
Admin plans all need stable Component/release coordinates.

## Planning Authority

- [Phase 56 Plan](../../../phase/phase-56.md)
- [Phase 56 Checklist](../../../phase/phase-56-checklist.md)
- [Current Work Closeout Handoff](2026-08-06-current-work-closeout-before-phase-56.md)
