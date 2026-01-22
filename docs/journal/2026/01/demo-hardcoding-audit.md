# Demo / Built-in Special-Casing Audit

This report lists hard-coded paths, names, or special-case logic related to initial demos (helloworld), ping, admin, or builtin handling.

## Source Scan Results
### Keyword-based search

#### Keyword: helloworld

#### Keyword: HelloWorld

#### Keyword: ping

#### Keyword: admin

#### Keyword: system

#### Keyword: builtin

#### Keyword: DEFAULT

#### Keyword: SCRIPT

#### Keyword: RUN

### Resolver / Selector special-casing

### CLI / Runtime entry points

## Next Step
- Review each hit and classify as:
  - legitimate builtin behavior
  - demo-only shortcut
  - accidental hard-coding

Audit completed: Tue Jan 20 10:48:05 JST 2026
## OpenAPI Projection Hardcoding (Phase 2.85)

### Current Behavior

During Phase 2.85, OpenAPI export exposes all discovered components and operations,
and HTTP methods are assigned using a simplified rule.

### GET-only Policy for Demo

For the demo phase, all operations are effectively treated as **GET**.
This is intentional:

- Demo scenarios only require query-style access
- Write semantics are not demonstrated
- Simplicity and predictability are prioritized

### Temporary Nature of HTTP Inference

Any remaining HTTP method inference is explicitly temporary.
It exists only to support demo visibility and will be removed once
OperationDefinition-driven projection is in place.

### Trace Instrumentation

Trace-level logging was added to OpenApiProjector to make projection behavior observable.
These logs are marked and intended for easy removal after Phase 2.85.

### Removal Plan

This hardcoded behavior will be removed when:

- OperationDefinition exposes CQRS roles and attributes
- OpenApiProjector reads those attributes directly
- component.yaml / CML become the authoritative metadata sources

