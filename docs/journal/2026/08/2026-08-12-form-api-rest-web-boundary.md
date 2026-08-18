# Form API and REST Web Boundary

Date: 2026-08-12

## Context

The ArtScene Phase 13 progressive-integration design initially allowed explicit
browser actions to call either Form API or REST. CNCF already described Form API
as the JSON counterpart of Static Form input preparation, while REST v1 was the
canonical JSON execution surface. The implementation nevertheless retained
`POST /form-api/{component}/{service}/{operation}`, which accepts form data and
executes the Operation for compatibility.

This overlap raised an architectural choice:

1. use Form API as the sole browser API for definition, validation, and
   execution; or
2. use Form API and REST with distinct responsibilities behind one browser
   facade.

## Considered Alternatives

### Form API-only browser execution

One route family would simplify application-visible transport selection and
could behave as a generic Web backend-for-frontend. It would, however, make
Form API both a Web-schema adapter and a second canonical Operation execution
API. REST and Form API would then require parallel versioning, success/error
envelopes, OpenAPI/SDK treatment, idempotency, authorization, StateMachine,
Workflow, DbC, and observability acceptance.

That duplication does not follow CNCF's existing operation-centric Web design.
If CNCF later needs a true Web backend-for-frontend, it should receive an
explicit contract and name rather than emerge implicitly from Form API.

### Form API and REST responsibility split

Form API keeps the responsibilities that are genuinely Web-input-specific:

- form/control metadata, labels, help, candidate values, and presentation hints;
- resolved Web schema and field ordering; and
- optional syntax, datatype, multiplicity, and other admission validation that
  does not dispatch the Operation.

REST v1 remains the canonical JSON query/command Operation execution surface.
Normal `/form` submission remains the JavaScript-free HTML/PRG adapter to the
same Operation authority. A common CNCF browser facade hides CSRF, credentials,
encoding, decoding, cancellation, and structured errors while exposing form
definition, form validation, and Operation execution as different logical
methods.

## Decision

CNCF selects the responsibility split.

```text
/web       -> server-rendered human-facing page
/form      -> browser-native HTML Operation execution and PRG
/form-api  -> Web input definition and optional admission validation
/rest/v1   -> canonical JSON query/command Operation execution
```

Using both Form API and REST does not mean two requests per action. Form API is
called only when a client needs dynamic definition or optional pre-validation.
REST execution always performs authoritative Operation validation.

The existing direct Form API execution POST remains compatibility-only. It must
continue through the same authorization, Operation, UnitOfWork, result-envelope,
structured-error, and CSRF authorities, but new browser integrations do not use
it as their execution surface.

CSRF policy remains based on the effective ingress authentication profile, not
the route family. Unsafe REST called with a Web session requires the same common
CSRF protection as unsafe Form API requests. Explicit admitted non-cookie
external REST keeps its separate security profile.

## ArtScene Consequence

ArtScene Phase 13 uses:

- SSR Page View for its complete initial document;
- embedded View data for initial enhancement and source-only filtering;
- REST v1 for Timeline/List refresh queries and review/follow commands;
- Form API only when dynamic Web input definition or optional admission
  validation is required; and
- `/form` plus PRG for JavaScript-disabled fallback.

The CNCF browser facade owns transport and security mechanics. ArtScene owns
bounded DOM updates, latest-request-wins, busy/focus/history behavior,
localized messages, and application/domain presentation.

## Consequences

- There is one canonical JSON Operation execution surface.
- Web-specific schema and input concerns remain isolated from domain execution.
- Browser, SPA, CLI, service, StateMachine, Workflow, DbC, and observability
  evidence converge on the same Operation semantics.
- The compatibility Form API execution route can be maintained without
  directing new consumers to it.
- Phase 62 must protect both route families and publish a browser facade with
  distinct logical responsibilities.
- Phase 62.1 and ArtScene Phase 13 validate the producer/consumer split through
  real browser and packaged-CAR evidence.

## References

- `docs/design/web-layer.md`
- `docs/design/web-form-api-schema.md`
- `docs/phase/phase-62.md`
- `docs/phase/phase-62.1.md`
- `docs/notes/artscene-driven-progressive-static-web-client-integration-provisional-specification.md`
- `textus-art-scene: docs/phase/phase-13.md`
