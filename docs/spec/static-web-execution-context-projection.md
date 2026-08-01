# Static Web Execution-Context Projection Specification

## Status

This specification is the normative contract for Phase 38 Static Web
execution-context projection.

The design rationale is
`docs/design/static-web-execution-context-projection.md`.

## SWEP-1: Projection Boundary

CNCF MUST build a framework-owned `WebExecutionProjection` after request
security and formatting have produced the effective `ExecutionContext`, and
before Static Web template rendering.

CNCF MUST NOT serialize `ExecutionContext`, `RuntimeContext`,
`SecurityContext`, `SecuritySubject`, or an authentication session into the
page.

`WebPageContextProvider` output, query parameters, and arbitrary request or
session attributes MUST NOT create or override the execution projection.

## SWEP-2: Public Shape

The JSON object under `pageContext.execution` MUST have this public shape:

```yaml
locale: ja-JP
timezone: Asia/Tokyo
format:
  date: localized-medium
  dateTime: application-default
applicationMode: standalone
subject:
  authenticated: false
  displayName: null
capabilities: []
```

Field rules:

- `locale` MUST be a canonical BCP 47 language tag.
- `timezone` MUST be a canonical Java `ZoneId`/IANA identifier.
- `format.date` and `format.dateTime` MUST be stable public policy identifiers,
  not formatter implementation strings.
- `applicationMode` MUST be `standalone` or `multi-user`.
- `subject.authenticated` MUST reflect the effective request subject.
- `subject.displayName` MUST be public text or null/absent.
- `capabilities` MUST be a deterministic ordered array of explicitly public
  capability names.

Unknown fields MUST NOT be copied from runtime or session objects.

## SWEP-3: Application Mode

`applicationMode` MUST be derived from the owning Subsystem's resolved
`SubsystemUserMode` (`textus.subsystem.user-mode`).  Web presentation policy
MUST NOT select or override that mode.

CNCF MUST NOT infer application mode from request authentication state,
`OperationMode`, or page layout/display mode.

When the owning Subsystem has no canonical mode value, its existing
direct-Component compatibility rule determines whether `standalone` is
available; it is not a Web-owned fallback.

## SWEP-4: Locale and Timezone Resolution

For `standalone`, resolution order MUST be:

1. an explicit display override when Web policy enables it;
2. configured application/startup value;
3. CNCF execution/runtime default.

For `multi-user`, resolution order MUST be:

1. an explicit display override when Web policy enables it;
2. authenticated-user public preference;
3. configured application fallback;
4. CNCF execution/runtime default.

An invalid candidate MUST produce a structured configuration/request failure
or be ignored according to the owning source's existing validation contract;
it MUST NOT silently become a different locale or timezone.

`Accept-Language` MAY participate only when explicit HTTP language negotiation
policy is enabled and no execution-owned value has resolved locale. It MUST NOT
override a configured standalone locale or authenticated-user preference.

Browser `navigator.language` and `localStorage` MUST NOT participate in server
resolution.

## SWEP-5: Display Format Policy

`format.date` and `format.dateTime` MUST identify application-facing formatting
policies selected by the effective execution/Web formatting policy.

The projection MUST NOT expose `DateTimeFormatter.toString`, JVM-specific
pattern internals, or log/debug formatter state.

Application-facing date/time formatting MAY be localized. Diagnostic and log
timestamps MUST retain their existing ISO policy.

The initial identifier vocabulary and fallback mapping MUST be deterministic
and executable before SW-03 is complete.

The initial public format vocabulary is:

- `localized-medium` for the localized application date or date/time policy;
- `application-default` for the CNCF application display default; and
- another lowercase hyphenated identifier explicitly selected by Web policy.

Execution policy `default` maps to `application-default`. Execution policy
`localized` and `localized-medium` map to `localized-medium`. Formatter object
names, patterns, and `toString` output are invalid public identifiers.

`applicationMode` in the public projection MUST be derived from the owning
Subsystem's `SubsystemUserMode`, resolved using the exact canonical
`textus.subsystem.user-mode` value `standalone` or `multi-user`. A
configuration-only Web policy decoder MUST reject a present user-mode value:
it lacks the owning Subsystem authority. Web-specific mode spellings MUST NOT
select or override the projection mode.

The canonical Web presentation configuration keys are:

```text
textus.web.execution.locale
textus.web.execution.timezone
textus.web.execution.date-format
textus.web.execution.date-time-format
textus.web.execution.display-override.enabled
textus.web.execution.http-language-negotiation.enabled
textus.web.execution.public-capabilities
```

The corresponding `textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` forms are
compatibility aliases for presentation keys only. Display override and HTTP
language negotiation both default to disabled. Invalid configured user-mode,
locale, timezone, format ID, or boolean policy MUST produce a structured
failure rather than silently selecting a fallback.

## SWEP-6: Subject Safety

`subject.authenticated` MUST be derived from the effective authenticated
request subject.

`subject.displayName` MUST be derived only from an explicit public typed
authentication/session field. CNCF MUST NOT use any of these as fallback:

- internal principal or subject ID;
- session ID;
- token or credential;
- security level, role, scope, privilege, or capability;
- arbitrary authentication/session attribute.

When no public display name exists, the projection MUST use null/absence.

## SWEP-7: Capability Safety

Capability projection MUST be default-deny.

CNCF MUST intersect effective normalized subject capabilities with an explicit
Web-public capability allowlist, deduplicate the result, and order it
deterministically.

Roles, scopes, privileges, and security levels MUST NOT be projected as
capability substitutes.

Projected capabilities are presentation metadata only. Server-side
authorization remains authoritative.

## SWEP-8: HTML Rendering

Static Web rendering MUST make the projection available during first HTML
generation.

The rendered document MUST use the projection locale for `html[lang]` and any
standard locale semantic attribute.

The renderer MUST emit compound page context once in:

```html
<script id="textus-page-context" type="application/json"></script>
```

The script content MUST be generated by JSON serialization of the typed
projection and escaped for HTML script-data context. Values containing
`</script`, `<`, `>`, `&`, Unicode line separator, or Unicode paragraph
separator MUST NOT escape or alter the script element.

Application templates MUST NOT construct this JSON by string concatenation.

`html[lang]`, semantic attributes, and embedded JSON MUST come from the same
projection instance.

`textus-page-context` is a framework-reserved script-data element identifier.
If an application template contains a script element with that identifier,
the renderer MUST replace it with the framework projection rather than
duplicate or trust application-supplied JSON. Extensible `WebPageContext`
provider values MUST NOT replace the typed execution projection.

## SWEP-9: Excluded Data

The execution projection MUST NOT contain:

- session identifiers, tokens, cookies, or authentication headers;
- internal principal/subject identifiers;
- roles, scopes, privileges, security levels, or non-public capabilities;
- component/runtime configuration, secret references, or secret values;
- datastore connections, provider topology, or deployment details;
- CallTree, trace, metrics payloads, or debug diagnostics; or
- arbitrary request, query, session, or provider attributes.

The presence of any excluded value in another internal page-rendering
structure MUST NOT make it eligible for execution projection.

## SWEP-10: First-render Contract

Application JavaScript MUST be able to read locale, timezone, application
mode, safe subject state, public capabilities, and display-format identifiers
from the first HTML response without an additional REST operation.

`DescribeApplication` and other application operations MUST remain business
state APIs and MUST NOT be required for initial execution-context discovery.

Client code MUST locate the framework value through the stable
`#textus-page-context` selector and parse its text as JSON. It MUST read the
execution projection from the stable `execution` member and tolerate unknown
additive members. It MUST NOT depend on the script element's physical sibling
position.

The framework MUST place `#textus-page-context` after leading head metadata
that must remain early, including `meta[charset]`, and before the first
application script in `head`. A Static Web application MUST NOT hide the page,
wait for a business REST operation, inspect browser locale, or consult browser
storage merely to discover the initial execution locale.

The execution projection is not a substitute for server-side localization of a
normal page. The Static Web Application contract requires locale messages and
the primary page View to be rendered before first paint; JavaScript may read
this projection only for bounded progressive enhancement. See
`docs/spec/static-web-application.md`.

## Required Executable Examples

### E1: Standalone Configuration Wins

Given a configured Japanese standalone application and conflicting English
`Accept-Language`, the first HTML response uses `ja-JP` in both `html[lang]`
and `pageContext.execution.locale`.

### E2: Authenticated-user Preference Wins

Given a multi-user application, an authenticated user with Japanese locale,
and conflicting English `Accept-Language`, the first HTML response uses the
authenticated-user preference.

### E3: Runtime Default

Given no configured application value and no authenticated-user preference,
the first HTML response uses the deterministic CNCF execution/runtime default.

### E4: Hostile Public Text

Given a public display name containing script terminators and HTML-significant
characters, the page-context JSON remains parseable and cannot terminate the
script-data element.

### E5: Redaction

Given a session with internal IDs, tokens, configuration, provider data, and
debug context, none of those values or keys occur in the execution projection.

### E6: Public Capability Allowlist

Given public and internal effective capabilities, only explicitly allowlisted
public capabilities appear, in normalized deterministic order.

## Related Contracts

- `docs/design/static-web-execution-context-projection.md`
- `docs/design/web-layer.md`
- `docs/design/execution-context.md`
- `docs/design/execution-determinism.md`
- `docs/phase/phase-38.md`
- `docs/spec/static-web-application.md`
