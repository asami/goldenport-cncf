# Phase 62.1 - ArtScene-driven Progressive Static Web Client Integration

status=planned
planned_at=2026-08-12
depends_on=[Phase 62](phase-62.md)
driver=ArtScene Phase 13
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 62.1 Checklist](phase-62.1-checklist.md)

## Purpose

Use ArtScene Phase 13 as the first full application driver for CNCF's completed
Phase 62 browser-operation baseline, then promote only demonstrated reusable
gaps into bounded CNCF Web client capabilities.

Phase 62.1 begins after Phase 62 closes. It does not reopen Phase 62: defects in
the published Phase 62 contract are maintenance fixes, while genuinely new and
reusable client capabilities are owned here. ArtScene-specific presentation and
interaction remain in ArtScene.

## Dependency and Coordination

ArtScene Phase 13 starts only after Phase 62 closes and a consumable CNCF
development artifact is available. Stage 13C adopts the baseline and produces a
typed gap ledger. Mandatory reusable gaps enter this Phase; optional candidates
may be relocated to strategy item 9.21 or another named owner.

Phase 62.1 closes before ArtScene Phase 13 closes only when the ArtScene
acceptance path depends on a capability implemented here. ArtScene can close
with an optional candidate relocated to a named future owner. This Web stream
does not gate the independent Phase 63 StateMachine, Phase 64 Workflow, or Phase
65 executable DbC sequence.

## Selected Direction

- CNCF owns same-origin request security, Form API Web input adaptation, REST v1
  Operation execution, structured response/error decoding, and stable
  browser-client contracts.
- New ArtScene query and command requests execute through REST v1. Form API is
  used only when dynamic Web input definition or optional admission validation
  is required.
- Direct Form API execution POST remains compatibility-only and is not adopted
  by ArtScene Phase 13.
- The browser client accepts caller-owned cancellation and does not own page
  rendering, focus, history, busy state, or domain policy.
- ArtScene owns Timeline/List rendering, latest-request-wins coordination,
  review/follow presentation, URL state, and localized application feedback.
- A Phase 62 promise that fails is repaired as a compatibility defect; Phase
  62.1 is not a vehicle for weakening the closed security baseline.
- A new framework abstraction requires evidence that it is independent of
  ArtScene domain names and preserves server-rendered and no-JavaScript paths.
- A reusable JavaScript component runtime, registry, props schema, and lifecycle
  remain strategy item 9.21 until separately selected.
- An implicit SAR created for CAR-only execution selects the root/Primary CAR's
  Web application as its default application surface. Dependency CAR Web
  applications remain component-owned support routes and do not enter the
  application navigation or default-entry candidates unless the SAR explicitly
  opts them in.
- SAR Web participation distinguishes `support` routes from `visible`
  application composition. Authentication flows may use UserAccount support
  routes without making UserAccount a visible application; UserNotification or
  another dependency becomes visible only through an explicit SAR opt-in.
- `apps[].entry: true` remains accepted as a deprecated compatibility input
  during migration. Phase 62.1 defines and proves the replacement before any
  removal, and a dependency CAR's legacy entry flag never overrides the
  root/Primary CAR selected by an implicit SAR.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| PSI-01 | Baseline handoff and gap taxonomy | The exact Phase 62 artifact/API and defect, reusable-extension, app-local, and future-candidate classifications are fixed. | planned |
| PSI-02 | ArtScene consumer trial | ArtScene Timeline, List, review, and follow paths exercise the Phase 62 client contract and produce reproducible gaps. | planned |
| PSI-03 | Bounded reusable extensions and implicit-SAR Web participation | Only admitted generic form-adaptation, REST execution, request, cancellation, decoding, error, packaging, or root/dependency Web participation capabilities are implemented without DOM or domain ownership. | planned |
| PSI-04 | Producer/consumer compatibility | CNCF fixtures and ArtScene use the same published contract with stable asset, source, and ABI compatibility. | planned |
| PSI-05 | Real browser and failure acceptance | Real HTTP/browser evidence covers fallback, authorization, CSRF, races, failures, retry, non-leakage, and bounded requests. | planned |
| PSI-06 | Promotion and closure | Verified contracts are promoted and remaining optional candidates receive named owners without reopening Phase 62. | planned |

## Acceptance

- ArtScene consumes the completed Phase 62 browser-operation contract without
  copying CNCF token selection or verification policy.
- ArtScene executes Timeline/List queries and review/follow commands through
  REST v1; it uses Form API only for dynamic definition or optional Web input
  admission validation.
- HTTP/operation structured failures retain status and available Conclusion
  codes; browser network and abort failures remain distinct transport outcomes.
- Caller-owned cancellation and ArtScene-owned latest-request-wins logic prevent
  stale responses from replacing newer page state.
- JavaScript-disabled forms and server-rendered first documents remain complete.
- CNCF client code contains no ArtScene Timeline, exhibition, review,
  subscription, locale, authorization, or workspace policy.
- Development and packaged consumption use the same admitted CNCF asset and
  public contract.
- CAR-only execution publishes the root/Primary CAR Web application as the
  implicit SAR default even when dependency CARs package their own Web apps.
- Dependency Web apps are support-only by default; SAR opt-in deterministically
  promotes selected component apps to visible composition without changing
  component ownership, authorization, canonical routes, or assets.
- UserAccount authentication support can remain non-navigable, while
  UserNotification and similar application UI appears only when explicitly
  selected. Deprecated `entry: true` remains bounded compatibility input and
  is not the new participation contract.
- Mandatory admitted gaps pass CNCF and ArtScene producer/consumer evidence;
  optional candidates are relocated explicitly.

## Non-Goals

- Reopening or weakening Phase 62 CSRF/security behavior.
- Implementing ArtScene presentation or domain transitions in CNCF.
- Creating a client-side router, state store, SPA runtime, or implicit hydration.
- Implementing the full strategy item 9.21 Island Architecture Runtime.
- Automatically exposing every dependency CAR Web app in application
  navigation, or allowing a dependency-local entry flag to select the implicit
  SAR default.
- Removing `apps[].entry: true` before the replacement participation contract
  and migration evidence are complete.
- Blocking the independent StateMachine, Workflow, or executable DbC phases.

## Planning References

- [Phase 62 - Web Session CSRF Unification](phase-62.md)
- [Phase 62.1 Checklist](phase-62.1-checklist.md)
- [Progressive Static Web Client Integration Provisional Specification](../notes/artscene-driven-progressive-static-web-client-integration-provisional-specification.md)
- [Design Journal](../journal/2026/08/2026-08-12-artscene-driven-progressive-static-web-integration.md)
- [Form API and REST Web Boundary](../journal/2026/08/2026-08-12-form-api-rest-web-boundary.md)
- [Static Web Application Specification](../spec/static-web-application.md)
- [Web Layer Design](../design/web-layer.md)
