# Progressive Static Web Architecture - Provisional Specification

Status: provisional specification candidate. This note fixes the proposed
terminology and acceptance boundary before implementation. It does not yet
override the promoted `docs/spec/static-web-application.md` contract.

## 1. Purpose

CNCF uses **Progressive Static Web** for a server-first Web architecture in
which each primary document is complete when delivered and JavaScript enhances
bounded interactions without becoming the authority for the page, application,
or domain state. A **Progressive Static Web Application is a kind of Static Web
Application**. It satisfies the complete Static Web Application contract first
and then adds progressive enhancements.

`Static` describes the delivered document baseline. It does not require files
to be pre-generated, immutable, or served without an application server. A
document rendered dynamically for the current locale, subject, workspace, and
read model is Static Web when the response already contains its meaningful
content and usable navigation.

`Progressive` means that the baseline is independently usable and that a
JavaScript-capable browser receives a better interaction without creating a
second domain contract. If the enhancement script is unavailable, blocked,
unsupported, or fails at runtime, the underlying Static Web Application still
performs its basic operations through ordinary Pages, links, and forms. This
independence is why the architecture remains a Static Web Application.
Progressive does not mean browser hydration of an empty shell, client-side
reconstruction of content already available to the server, or an SPA described
with different terminology.

## 2. Normative Vocabulary

The following three terms form one explicit hierarchy. They are not synonyms.

### 2.1 Progressive Static Web Page (PSWP)

A **Progressive Static Web Page** is a Static Web Page that is independently
meaningful and operational, with one or more optional bounded progressive
enhancements. It is one addressable HTML document response for one route and
resolved request context.

A conforming Page:

- arrives as meaningful, locale-correct, semantic HTML;
- contains the authorized primary content and current server read model needed
  for its first presentation;
- retains usable links and forms when JavaScript is unavailable or fails;
- may embed a typed, safely escaped copy of already-rendered page data for
  bounded enhancement;
- performs no initial browser request merely to reconstruct primary content
  that the server already had;
- limits JavaScript replacement to a named page region and preserves the
  document, navigation, and fallback boundary around that region; and
- continues its basic Page operation when enhancement loading or execution
  fails; and
- treats the server response, validation, authorization, operation result, and
  persisted state as authoritative.

Page conformance is evaluated at the document boundary. It does not by itself
prove that the enclosing Site has coherent navigation or that the enclosing
Application preserves its required user journeys.

### 2.2 Progressive Static Web Site (PSWS)

A **Progressive Static Web Site** is a Static Web Site whose coherent,
addressable collection of Static Web Pages includes progressive enhancements.
It owns their navigation, routes, assets, and shared Web presentation policy.

A conforming Site:

- gives each primary state or resource a stable server-resolvable URL;
- composes primary navigation from server-known application and authorization
  state;
- preserves ordinary document navigation, reload, bookmarking, back/forward,
  locale selection, and safe return paths;
- applies consistent accessibility, localization, cache, asset, error, and
  enhancement-loading policy across its Pages;
- does not require a client router or browser state store to make its primary
  routes meaningful; and
- remains navigable and operational when progressive assets cannot be loaded;
  and
- does not expose dependency or support applications as visible Site content
  merely because their routes or assets are assembled.

The Site is the publication and navigation boundary. It may contain Pages that
offer different bounded enhancements, but those enhancements do not jointly
become an implicit SPA runtime.

### 2.3 Progressive Static Web Application (PSWA)

A **Progressive Static Web Application** is a subtype of Static Web Application
that adds progressive enhancements to one or more of its Pages. Its normal Web
delivery is one or more Progressive Static Web Sites, and its user journeys are
realized through Progressive Static Web Pages plus server-authoritative
operations.

A conforming Application:

- owns its business capabilities, user journeys, operation vocabulary, and
  required interaction outcomes independently of rendering technology;
- resolves authorization, validation, domain transitions, persistence, locale,
  and workspace policy on the server;
- provides every supported business transition through an ordinary Web path,
  normally a link or aggregate command form with Post/Redirect/Get;
- preserves its basic reading, navigation, and business operations when every
  progressive enhancement is disabled or fails;
- may define a more direct JavaScript-enhanced interaction as part of its normal
  product experience, while retaining the underlying business capability—not
  necessarily the identical interaction shape—through the Static Web baseline;
- executes enhanced queries and commands through the same canonical Operations
  used by non-Web and fallback paths; and
- settles enhanced UI state from an authoritative server outcome rather than
  treating optimistic browser state as committed domain state.

The Application is the behavioral and ownership boundary. Application
conformance therefore includes product-level journeys that cannot be proven by
checking individual Pages in isolation.

Static baseline conformance and declared enhancement conformance are evaluated
separately. A failure of an enhancement must not prevent basic operation, but a
missing or broken enhancement still fails the declared Progressive product
contract when that enhancement is part of the supported normal experience.

## 3. Composition Model

```text
Static Web Application
  -> Progressive Static Web Application (subtype)
       -> one or more Progressive Static Web Sites
            -> addressable Progressive Static Web Pages
                 -> one or more bounded progressive enhancements
```

The nesting expresses responsibility, not necessarily one deployment artifact.
A CNCF component application commonly exposes one default Site, while an
assembled application may also expose support routes owned by dependencies.
Those routes do not become part of the visible Site unless the application
composition contract admits them.

## 4. Baseline and Enhanced Behavior

The two modes share domain semantics but may use different interaction shapes.

| Concern | Baseline document behavior | Enhanced behavior |
| --- | --- | --- |
| Initial content | Server renders the complete Page in one document response. | JavaScript starts from delivered HTML or embedded typed page data; it does not refetch the initial model. |
| Navigation | Ordinary links and GET forms resolve stable URLs. | Bounded controls may update a region and URL state while preserving reload and history behavior. |
| Mutation | An ordinary server-owned command form completes through Post/Redirect/Get. | A control may invoke the same canonical Operation and update only its owned region from the authoritative result. |
| Failure | The server returns an accessible localized response. | Structured HTTP/Operation, abort, and network outcomes remain distinct and the fallback remains reachable. |
| Authority | Server authorization, validation, context, transition, and persistence are final. | Browser state is presentation state only. |

Progressive enhancement is additive at the capability level and may be
transformative at the presentation level. Canvas, timeline visualization, or
direct in-list controls may replace a simpler baseline presentation inside one
bounded region, provided the Page remains meaningful and the same business
capability remains reachable without JavaScript.

Enhancement failure is therefore a degraded mode, not loss of the
Application's basic operation. The baseline remains usable without retrying or
repairing JavaScript first.

## 5. Non-Conforming Patterns

The following are not Progressive Static Web:

- returning an empty or placeholder application shell and fetching the primary
  model after load;
- server-rendering navigation while browser REST calls independently assemble
  the primary list, counters, filters, or authorization-sensitive content;
- making Form API or REST hydration an obligatory preflight for ordinary page
  display;
- implementing the same domain transition separately in browser code and in a
  server form;
- removing stable routes or fallback forms because the enhanced path works;
- calling a server-rendered read-only page conforming when the Application's
  required interactive journey has regressed; or
- allowing a page-local enhancement to choose locale, authorization,
  workspace, validation, or persisted state.

## 6. Preserved ArtScene Counterexample

ArtScene provides a useful negative example because its exhibition Timeline
and List have crossed both sides of the boundary during development.

First, browser-owned list rendering fetched records and constructed primary
cards with `innerHTML`. When used to establish the first meaningful screen,
that is hydration and is not a Progressive Static Web Page, even if the server
also returned an HTML shell and navigation.

Second, the server-rendering refactor was at one point interpreted as authority
to remove the direct per-exhibition planning-state controls from Timeline and
List and leave only a `Review` link to another page. Such a result can satisfy
the minimum no-JavaScript Page baseline, but it violates the ArtScene
Application requirement that a JavaScript-capable user change planning state
directly on the displayed exhibition. Page-level server-first conformance does
not authorize an Application-level product regression.

At runtime, however, failure of those direct controls must leave the `Review`
link and server-owned detail/form path operational. That fallback is what makes
ArtScene a Static Web Application. The direct controls are the Progressive
extension: their failure degrades the experience but must not remove the basic
planning-state operation.

The conforming ArtScene shape is:

1. Timeline and List arrive with exhibition content and current planning state
   in server-rendered HTML.
2. An ordinary detail/review command form keeps every supported transition
   reachable without JavaScript.
3. With JavaScript enabled, direct per-exhibition controls execute the canonical
   server Operation without a detail-page round trip.
4. Only the affected bounded region is reconciled from the persisted server
   result.

This counterexample is retained as design evidence. It must not be rewritten as
an endorsement of browser-owned primary rendering or as permission to weaken
ArtScene's required enhanced interaction.

## 7. Proposed CNCF Contract Placement

Before implementation, this vocabulary remains a specification candidate in
`docs/notes`. When promoted:

- this terminology and composition model should become the opening conceptual
  contract of `docs/spec/static-web-application.md`;
- existing SWA delivery, context, View, form, progressive-enhancement, caching,
  and security clauses should become the detailed Application/Page rules;
- Site-level route, navigation, visible/support composition, and asset rules
  should be identified explicitly rather than inferred from individual Page
  clauses; and
- executable specifications should prove Page baseline, Site navigation, and
  Application journey conformance separately.

## 8. Related Drafts

- `docs/spec/static-web-application.md`
- `docs/spec/static-web-execution-context-projection.md`
- `docs/design/web-layer.md`
- `docs/notes/artscene-driven-progressive-static-web-client-integration-provisional-specification.md`
- `docs/phase/phase-62.1.md`
