# Phase 58.7 Checklist - Resolved-Resource Downstream Consumer Contract

status=planned
phase=[Phase 58.7 - Resolved-Resource Downstream Consumer Contract](phase-58.7.md)
predecessor=[Phase 58.6.1](phase-58.6.1.md)
successor=[Phase 58.8](phase-58.8.md)

## RSC-08: Downstream Consumer Contract

Stage Status:
- Current status: VALIDATED_PENDING_STEP_REVIEW
- Owner: CNCF resolver, Help, and Component Admin maintainers
- Entry rule: Phase 58.6.1 RSC-07 is DONE.
- Completion rule: Help and Admin fixtures consume the same resolved resources, child identities, and provenance without physical-artifact scans.

- [x] Define read-only resource inventory and content-access APIs.
- [x] Define safe availability, role, identity, version, digest, source, and provenance projections.
- [x] Prove a Help fixture resolves Documentation through the common API.
- [x] Prove an Admin fixture displays Documentation/SourceCode state through the same API.
- [x] Prove Help and Admin distinguish parent, Documentation/Source, external-platform, and other Subcomponent Component identities plus Subsystem identities.
- [x] Prove Help and Admin report identical physical availability and integrity.
- [x] Prevent Help/Admin from walking CAR, SubComponent, cache, repository, or development directories independently.
- [x] Preserve authorization differences between inventory visibility and content access.

Evidence:
- Intentional RED invocation `24004-20260821T230619Z`: 10 missing projection API symbols, compile failure, and lock released.
- GREEN primary invocation `25125-20260821T230912Z`: exact consumer spec, 1 suite / 4 tests passed, and lock released.
- Affected accumulator invocation `25547-20260821T231004Z`: consumer, resolver, and authorization suites, 3 suites / 29 tests passed, and lock released.
- Static boundary evidence: the new source accepts supplied `ResolvedComponentResources`, delegates access to the existing `ComponentResourceAuthorizationPolicy`, exposes no physical path, repository, or content in the inventory view, and imports no direct filesystem, archive, cache, repository, or development scanner.
- Remaining status: Step REVIEW and STEP_COMMIT are pending; Phase-level full review, full test, and release have not yet run.
