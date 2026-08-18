# ArtScene-driven Progressive Static Web Integration

Date: 2026-08-12

## Context

ArtScene Phase 13 had already restored useful progressive Timeline, List,
review, and follow behavior, but its browser code manually selected a hidden
CSRF field, submitted Form API requests, decoded failures, and controlled page
updates. The remaining phase plan also mixed server-rendered Textus widgets,
application-local enhancement, and the future CNCF Island Runtime.

CNCF Phase 62 was separately planned to unify Web-session CSRF across Form API,
Web REST, and browser JavaScript and already named ArtScene as a possible
acceptance consumer. That arrangement risked a cycle if ArtScene Phase 13 could
not start until Phase 62 closed while Phase 62 required ArtScene Phase 13 for
closure.

## Decision

Phase 62 will close independently with a CNCF-owned representative component
and fixture. It establishes the minimum secure browser-operation baseline and
does not depend on an ArtScene checkout or phase state.

ArtScene Phase 13 starts only after that baseline is closed and consumable. It
becomes the first full application driver and records gaps found in Timeline,
List, review, and follow flows. A gap is classified before work begins:

- a broken Phase 62 promise is a maintenance defect;
- a new domain-neutral browser-client capability belongs to Phase 62.1;
- presentation and page lifecycle specific to ArtScene remain in Phase 13; and
- a reusable JavaScript component runtime remains strategy item 9.21.

Phase 62.1 is therefore a producer/consumer integration phase, not a reopening
of Phase 62 and not an Island Runtime phase. If ArtScene requires an admitted
62.1 capability, Phase 62.1 closes before ArtScene Phase 13. Optional candidates
may be relocated to a named future owner without delaying ArtScene.

The subsequent API-boundary review fixed a further distinction. Business
capabilities are defined once as Operations and use REST v1 as the canonical
JSON execution surface. Form API adapts the same model/Operation input metadata
to Web forms and supplies optional admission validation. It does not become a
second canonical execution API. The existing direct Form API execution POST is
retained only for compatibility; ArtScene Phase 13 uses REST for Timeline/List
queries and review/follow commands. The separate rationale is recorded in
`2026-08-12-form-api-rest-web-boundary.md`.

## Consequences

- The dependency graph is one-directional and both repositories retain their
  own public contracts and evidence ledgers.
- CNCF security behavior can be released and tested without a downstream
  source checkout.
- ArtScene drives framework ergonomics with real use rather than speculative
  widget abstraction.
- Phase 62 remains immutable after closure.
- Phase 63 StateMachine, Phase 64 Workflow, and Phase 65 executable DbC remain
  an independent sequence and are not gated by this Web integration stream.
- ArtScene Phase 14 waits for Phase 13 behavior closure, not for the complete
  future Island Architecture roadmap.
