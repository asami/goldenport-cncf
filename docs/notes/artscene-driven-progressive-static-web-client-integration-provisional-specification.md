# ArtScene-driven Progressive Static Web Client Integration - Provisional Specification

Status: provisional planning contract for CNCF Phase 62.1 and ArtScene Phase
13. Exact JavaScript names and wire fields remain subject to Phase 62
verification; this note does not override a promoted `docs/spec` contract.

The formal candidate meanings and hierarchy of **Progressive Static Web
Application**, **Progressive Static Web Site**, and **Progressive Static Web
Page** are defined in
`docs/notes/progressive-static-web-architecture-provisional-specification.md`.
This integration note applies that vocabulary: ArtScene is the Application,
its visible ArtScene Web surface is the Site, and Timeline/List responses are
Pages with bounded interaction regions. The terms are not interchangeable.

## 1. Sequencing Contract

1. CNCF Phase 62 closes using CNCF-owned fixtures and publishes a consumable
   browser-operation baseline.
2. ArtScene Phase 13 starts against that closed baseline.
3. ArtScene Stage 13C records reproducible gaps and classifies each as a Phase
   62 defect, reusable CNCF extension, ArtScene-local behavior, or named future
   candidate.
4. CNCF Phase 62.1 implements only admitted reusable extensions.
5. ArtScene adopts the final producer contract and supplies consumer evidence.
6. Phase 62.1 closes before ArtScene Phase 13 when a mandatory extension was
   admitted; optional candidates may be relocated without blocking ArtScene.

Phase 62 is not reopened by this sequence.

## 2. CNCF Client Boundary

The CNCF browser client owns:

- canonical same-origin CSRF token projection and attachment;
- unsafe-method and credential transport rules;
- Form API definition and optional Web input admission requests;
- REST v1 query/command Operation execution;
- content-type-aware success and structured-error decoding;
- a stable public error projection containing the available HTTP status,
  `detailCode`, `appCode`, `appStatus`, and safe message;
- distinct HTTP/Operation, abort, and browser-network outcomes; and
- caller-supplied cancellation without hidden retries or page mutation.

The client presents these as distinct logical responsibilities even when they
share a low-level transport implementation. New component JavaScript does not
use direct `POST /form-api/{component}/{service}/{operation}` for execution;
that route remains compatibility-only.

The CNCF client does not own:

- DOM replacement, busy indicators, focus, history, or navigation;
- latest-request-wins policy for a page region;
- application localization copy;
- Timeline, exhibition review, facility subscription, or notification policy;
- authorization, workspace, persistence, or StateMachine/Workflow policy; or
- initial page data retrieval.

## 3. Promotion Rule

An ArtScene observation is promoted to CNCF only when:

- it is reproducible through a CNCF-owned fixture or neutral contract test;
- its public surface contains no ArtScene domain vocabulary;
- server-rendered and no-JavaScript behavior remains complete;
- it preserves Phase 62 security and non-leakage rules;
- its producer and consumer compatibility can be versioned and tested; and
- it is smaller than adopting the deferred Island Runtime contract.

A visual or lifecycle behavior with only one ArtScene use remains app-local.
A reusable island name/props/registry/lifecycle requirement is recorded under
strategy item 9.21 rather than implemented implicitly.

## 4. Failure Contract

A received non-success HTTP response is decoded from CNCF's structured error
envelope when available. Public client errors preserve safe status and codes but
do not expose production diagnostics or raw response bodies. A fetch rejection
without an HTTP response is a browser transport failure, not a fabricated
server `Conclusion`. An abort remains distinct from network unavailability so
ArtScene can suppress obsolete-request feedback.

## 5. ArtScene Interaction Contract

ArtScene owns one controller per bounded Page region. It may use caller-owned
abort or request-generation state to ensure only the newest applicable response
updates that region. Initial enhancement uses the embedded page View without a
network call. Review and follow actions continue through authoritative
REST v1 Operations, and ordinary links/forms remain the no-JavaScript fallback.
Timeline/List refresh also uses REST Operation execution. Form API is called
only when ArtScene needs dynamic Web input definition or optional admission
validation; it is not an obligatory preflight for every REST action.

ArtScene's exhibition interaction history is also retained as a negative
acceptance case. Browser REST hydration that constructs the initial Timeline or
List is not a Progressive Static Web Page. Conversely, replacing the required
direct per-exhibition planning-state controls only with a detail-page `Review`
link may preserve a minimal Page fallback but still regress the Progressive
Static Web Application. The accepted interaction must preserve both the
server-rendered first document and the direct, bounded JavaScript-enhanced
planning-state journey.

## 6. Evidence Contract

Required evidence spans both producer and consumer:

- CNCF unit/property and real HTTP/browser security evidence;
- a CNCF-owned representative Static Web consumer;
- ArtScene Timeline/List/review/follow integration;
- development-directory and packaged-CAR asset consumption;
- reordered response, abort, network failure, structured rejection, recovery,
  reload, back/forward, and JavaScript-disabled cases; and
- exact artifact/version identities with no source-project coupling.
