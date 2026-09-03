# Progressive Static Web Vocabulary

Date: 2026-08-29
Status: specification proposal recorded before implementation

## Context

CNCF already specified a `Static Web Application` delivery model and a bounded
`Progressive Enhancement` clause. ArtScene planning additionally used
`Progressive Static Web Site`, while the Phase 62.1 integration note described
a progressive Static Web interaction contract. The repository did not define
`Application`, `Site`, and `Page` as distinct formal concepts.

This ambiguity allowed two incompatible readings. A browser-hydrated primary
list could be called progressive merely because an HTML shell existed, while a
server-rendered read-only list could be called complete even after a required
direct interaction disappeared. Neither reading expresses the intended
architecture.

## Decision

The latest pre-implementation specification proposal is recorded in
`docs/notes/progressive-static-web-architecture-provisional-specification.md`.
It defines the following hierarchy:

- **Progressive Static Web Application (PSWA)**: a subtype of Static Web
  Application that adds progressive enhancements while retaining independently
  working basic operations; it is the behavioral and ownership boundary for
  user journeys, domain capabilities, and server-authoritative operations;
- **Progressive Static Web Site (PSWS)**: the publication and navigation
  boundary containing coherent routes, Pages, assets, and shared Web policy;
  and
- **Progressive Static Web Page (PSWP)**: one addressable, meaningful HTML
  document response with optional bounded enhancement.

The composition direction is Application to Site to Page to bounded
enhancement. These terms are formal candidate vocabulary and are not synonyms.

`Static` means that the initial document already carries its meaningful,
authorized, locale-correct primary content. It does not mean pre-generated or
immutable files. `Progressive` means that JavaScript improves a working Static
Web Application baseline without creating a second domain contract or taking
authority for initial content, authorization, validation, or persisted state.
If every Progressive extension is unavailable or fails, ordinary Pages, links,
and forms still provide the Application's basic operation. This independent
fallback is the defining reason it remains a Static Web Application.

No-JavaScript and JavaScript-enabled modes must preserve the same business
capability, but they need not provide the same interaction shape. An ordinary
detail/form/PRG path may be the fallback while the normal enhanced experience
offers a direct in-list action.

Baseline and enhancement acceptance remain distinct. Enhancement failure is a
degraded experience rather than loss of basic operation; nevertheless, a
declared normal enhancement that is absent or broken is still a Progressive
product-contract failure and may not be dismissed merely because the Static
fallback works.

## Preserved Negative Evidence: ArtScene

ArtScene is intentionally retained as the counterexample that motivated the
distinction.

- A Timeline or List whose initial primary cards are fetched and constructed
  by browser JavaScript is not a Progressive Static Web Page. It is hydration,
  even if navigation and a shell were server-rendered.
- A server-rendered Timeline or List that removes direct per-exhibition
  planning-state controls and leaves only a `Review` detail-page link may meet
  the minimum Page fallback, but it violates ArtScene's Progressive Static Web
  Application requirement for direct state change on the displayed
  exhibition.

The correct ArtScene contract combines a complete server-rendered list, a
no-JavaScript detail/review command path, direct JavaScript-enhanced controls,
canonical server Operations, and bounded reconciliation from the authoritative
persisted result.

If the direct controls fail at runtime, the detail/review command path must
continue to work. The latter is ArtScene's Static Web Application baseline; the
former is its Progressive extension.

This example must remain negative design evidence. The server-first rule does
not authorize removal of an application-level required interaction, and the
product requirement does not authorize browser ownership of primary page
rendering.

## Consequences

- Page, Site, and Application conformance require separate evidence.
- Existing `Static Web Application Specification` remains the promoted
  normative contract until the proposal is reviewed and promoted from notes.
- The ArtScene integration draft now references the vocabulary proposal and
  records the negative case explicitly.
- No source, runtime, generated contract, or phase status changes are admitted
  by this journal decision.
- Future implementation should add executable specifications for the Page
  baseline, Site navigation/route behavior, and complete Application journeys
  rather than treating one server-rendering assertion as proof of all three.
