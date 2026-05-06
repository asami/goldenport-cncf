# CNCF-Hosted SPA Boundary Note

This note records the practical boundary for building a SPA using only CNCF's
current Web hosting and REST execution surfaces.

## Current Minimum Shape

CNCF can already host enough static Web content for a minimal same-origin SPA:

```text
/web/{app}/index.html
/web/{app}/assets/app.js
/web/{app}/assets/app.css

app.js -> fetch("/rest/v1/...")
```

In this shape:

- CNCF serves the HTML, JavaScript, CSS, and other app-local assets.
- The SPA calls CNCF REST endpoints from the same origin.
- CORS is not part of the happy path because the browser sees one origin.
- Form API is optional. A hand-written SPA can call REST directly and use Form
  API only when it wants CNCF schema/default/validation metadata.
- Static Form pages, admin/runtime pages, and the SPA can coexist under the same
  runtime.

This is enough for internal testing, demos, operational prototypes, and small
application experiments where the SPA does not need production-grade client
routing, asset lifecycle, or separate gateway policy.

## Routing Boundary

The current hosted SPA shape is naturally compatible with hash routing:

```text
/web/{app}#/posts/123
/web/{app}#/settings
```

Hash routing does not require server fallback. Reloading `/web/{app}` still
loads the same entry page and the browser-side fragment chooses the client view.

History API routing is not yet a first-class CNCF feature:

```text
/web/{app}/posts/123
/web/{app}/settings
```

Those paths require app-scoped fallback to the SPA entry page on reload and deep
link access. CNCF must not add a global `/web` catch-all. If history routing is
supported later, the fallback must be explicit for the selected app/deployment
scope only.

## Runtime Boundary

A CNCF-hosted SPA still uses the ordinary CNCF operation path:

- REST executes domain operations.
- Operation authorization, UnitOfWork, EntityStore, JobEngine, and observability
  stay on the normal runtime path.
- Form API prepares input metadata and validation support; it does not execute
  business logic.
- Admin/system APIs remain protected management surfaces, not ordinary
  application SPA APIs.

The SPA does not get a special application API layer inside CNCF. It is another
Web client for the existing operation-centric runtime.

## What Is Missing For Production-Grade CNCF SPA Mode

The following items are the distance between "CNCF can host a minimal SPA" and
"CNCF has a production SPA mode":

- App-scoped SPA fallback routing for History API routes, with no global `/web`
  catch-all.
- Explicit SPA entry/bundle packaging policy, including cache headers,
  versioned assets, and deploy/rollback behavior.
- Clear separation between Static Form assets, component app assets, and SPA
  bundle assets.
- SPA-oriented authentication/session/CSRF policy. Same-origin cookie sessions
  are plausible, but the production contract must define login, logout, refresh,
  CSRF tokens, and failure behavior.
- REST exposure policy for application operations: which operations are public
  API, which are internal/admin, and how scopes/capabilities are represented.
- Consistent SPA-facing error handling for validation failures, authorization
  failures, optimistic-lock/version conflicts, async job tickets, and unexpected
  server errors.
- User notification and job-result affordances for async commands that continue
  after the originating page has changed.
- Production security headers and frontend policy, such as CSP, frame policy,
  referrer policy, and asset integrity/versioning decisions.
- Developer documentation that shows the recommended CNCF-hosted SPA structure,
  REST execution pattern, optional Form API usage, auth/session handling, and
  deployment checklist.

## Recommended Current Position

For now, CNCF-hosted SPA should be treated as a useful same-origin static Web
client pattern, not as a completed first-class Web mode.

Use it when:

- the app can use hash routing or a single entry page;
- the deployment is internal, experimental, or prototype-level;
- the frontend can call REST directly;
- production-grade gateway/auth/asset lifecycle concerns are not the main
  objective.

Do not treat it as complete when:

- reloadable History API deep links are required;
- separate SPA bundle lifecycle and rollback are required;
- public production auth/session/CSRF policy must be guaranteed;
- API exposure and security policy must be audited as a stable external
  contract.

The long-term target is an explicit CNCF SPA mode that preserves the current
operation-centric runtime while adding app-scoped SPA fallback, packaging,
auth/session, and API exposure policy.
