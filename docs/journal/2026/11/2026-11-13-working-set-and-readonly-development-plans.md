# Working Set and Read-only Development Plan Registration

Date: 2026-11-13
Status: planning registration only; implementation not started.

## Decision

Register activity-scoped Entity Working Sets as Phase 123, extending strategy item 9.11, and Read-only operation for failure and planned maintenance as new strategy item 9.70 / Phase 124. The user requested both formal planning updates after reviewing the HA guide's residency and Read-only explanations.

The GitHub main phase inventory was checked read-only on registration. Phase 122 was already allocated to typed SBT qualification and shared Scala lint integration, so the local additions use 123 and 124. This check neither synchronizes the repositories nor imports/reassigns Phase 122.

## Scope

Phase 123 includes active transactional state, active-user carts and needed user/address data, shared memberships, bounded residency, authoritative persistence and per-user authorization. Phase 124 includes common mutation admission, admitted-work transition, qualified resident reads under impaired persistence, eligible cache retention, maintenance entry and explicit recovery.

The first Read-only route can use existing resident capabilities. Activity-scoped and multi-runtime qualification consume actual Phase 123/108 capabilities when available. No new dependency or completion gate is added to Phase 108, and no accepted Phase is reopened.

## Records and limits

- [Phase 123](../../../phase/phase-123.md) / [checklist](../../../phase/phase-123-checklist.md)
- [Phase 124](../../../phase/phase-124.md) / [checklist](../../../phase/phase-124-checklist.md)
- [Planning input](../../../notes/activity-scoped-working-set-and-readonly-development-scope.md)

All checklist items are OPEN; execution_started=false. Existing journals and unrelated work are preserved. This registration starts no runtime/Session, runs no SBT or implementation tests, creates no PDF, and performs no commit, publication or synchronization. Documentation consistency/link checks are separate from implementation acceptance.
