# CML Attribute Confidentiality

`confidentiality` is field-level data classification metadata for CML
attributes and operation parameters.

It is separate from `securityAttributes`, authorization, encryption, and
ownership policy. The v1 runtime uses it for Web form rendering, generated
schema/meta/OpenAPI projection, development diagnostics, CallTree/history
redaction, and client-side debug display.

## Levels

- `public`: ordinary display/debug data. This is the default.
- `internal`: operational/internal data. It is not redacted by default in v1.
- `personal`: personally identifying data. Redacted by default in diagnostics.
- `sensitive`: business-sensitive data. Redacted by default in diagnostics.
- `secret`: credentials, tokens, passwords, session ids, hashes, and API keys.
  Always redacted in diagnostics and rendered as password controls in Web forms.

## CML Syntax

Attribute tables can add a `confidentiality` column.

```text
| name      | type   | multiplicity | confidentiality |
|-----------+--------+--------------+-----------------|
| email     | string | 1            | personal        |
| password  | string | 1            | secret          |
```

Existing CML without the column remains valid and defaults to `public`.

Name-based redaction remains as a compatibility fallback for legacy models, but
new components should declare confidentiality explicitly.
