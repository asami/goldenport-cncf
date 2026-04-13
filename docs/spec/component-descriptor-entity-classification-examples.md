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
