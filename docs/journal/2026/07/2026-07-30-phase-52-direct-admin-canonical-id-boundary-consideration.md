# Phase 52 - Direct Admin Canonical Entity ID Boundary Consideration

Status: decided

## Context

Phase 52 establishes `EntityId` as a universal ID with one complete canonical
String format. A canonical ID therefore carries the exact
`EntityCollectionId`; an ID that carries another collection is not an
alternative spelling of a local entity, even when its entropy portion matches
a local short identifier.

The browser-rendered Admin routes already rejected unresolved and foreign
instance locators. Review found that the direct Admin operations
`/admin/view/read` and `/admin/aggregate/read` parsed a supplied ID but passed
it to their view or aggregate implementation without proving that it belonged
to the surface's backing entity collection. Test fixtures could consequently
return a local result for a foreign canonical ID with colliding entropy.

## Alternatives Considered

1. Trust any syntactically canonical `EntityId` at the view or aggregate
   boundary.
   This treats parsing as sufficient authorization and leaves the collection
   ownership encoded by the ID unenforced.
2. Rebind the foreign ID to the selected entity collection by short ID or
   entropy.
   This recreates the deprecated scalar/context reconstruction path and loses
   the universal-ID ownership guarantee.
3. Resolve the surface's backing `EntityCollection` and require exact
   collection equality before invoking the view or aggregate implementation.
   This preserves the supplied canonical ID only when it already names the
   selected runtime collection.

## Decision

Option 3 was selected on 2026-07-30 JST. Direct Admin `view/read` and
`aggregate/read` parse their `id` argument and apply `_exact_entity_id` against
the backing entity collection before dispatch. The backing collection is
resolved only from the selected surface definition's declared `entityName`; a
same-base surface name is not a fallback. A malformed scalar ID, a well-formed
foreign canonical ID, or a definition whose declared collection is absent all
fail at the boundary. No short-ID, entropy, route, context, or surface-name
fallback is permitted for these operation inputs.

The regression specification sends a foreign canonical ID whose entropy is
`notice_1`, matching a local fixture, to both endpoints and requires HTTP 400.
It requires HTTP 400 when the declared backing collection is missing. An
ambiguous declared owner is a server-side surface-configuration failure and
therefore remains the structured `stateInvalid` result mapped to HTTP 500. This
keeps rejection observable at the public operation boundary rather than
relying on individual view/aggregate builders to implement identity validation.
It prevents a misconfigured definition from silently resolving an unrelated
collection with a similar surface name or arbitrarily selecting one of several
same-name collections.

## Consequence for Future Work

Any later Admin surface that accepts an entity `id` must identify its backing
`EntityCollection` and use exact collection equality before resolution. A
proposal to accept a scalar locator, to rebind a foreign canonical ID, or to
use entropy as a fallback reopens this decision and requires explicit
replacement evidence; it is not a compatibility-preserving implementation
detail.

Candidate Triage: COMPLETED
Canonical ID: DEV-004
Disposition: MERGED_EXISTING_PHASE
Strategy Record: docs/strategy/cncf-development-strategy.md#9-development-item-status
Target Phase: docs/phase/phase-60.md
Triaged On: 2026-08-19
