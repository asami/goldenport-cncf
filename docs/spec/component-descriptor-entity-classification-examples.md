# Component Descriptor Entity Classification Examples

Date: 2026-04-13

This document records generated-style component descriptor examples for entity
authorization classification.

## Business Entity

`SalesOrder` is a business resource entity. The default authorization profile is
business/private unless an operation or component factory overrides it.

```json
{
  "name": "sales-component",
  "version": "0.1.0-SNAPSHOT",
  "component": "sales-component",
  "entities": [
    {
      "entity": "SalesOrder",
      "usageKind": "business-object",
      "operationKind": "resource",
      "applicationDomain": "business"
    }
  ],
  "extensions": {},
  "config": {}
}
```

## CMS Entity

`Notice` is a CMS/public-content resource entity. The runtime create-default
derivation treats it as a public-read publication entity.

```json
{
  "name": "notice-board",
  "version": "0.1.0-SNAPSHOT",
  "component": "notice-board",
  "entities": [
    {
      "entity": "Notice",
      "usageKind": "public-content",
      "operationKind": "resource",
      "applicationDomain": "cms"
    }
  ],
  "extensions": {},
  "config": {}
}
```

For this classification, runtime create defaults are resolved per entity, not as
a global component-wide public fallback. A `Notice` record is expected to
receive public-read-compatible `securityAttributes`, while unrelated
business/private entities in the same runtime remain private by default unless
they declare their own public-content/public-read classification.

## Projection And Authorization Surface

Entity classification affects the canonical raw record written by the runtime.
That raw record is the authorization source of truth for framework-owned
defaults such as:

- `securityAttributes`;
- `postStatus`;
- `aliveness`.

Projected entity values and working-set resident entities may expose only the
application-facing subset of fields. Therefore runtime search/list visibility
must evaluate the canonical raw record when projected entity data does not carry
the full authorization metadata.
