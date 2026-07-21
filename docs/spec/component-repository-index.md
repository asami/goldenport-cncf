# Component Repository Index Contract

## Canonical Resource

```text
repository/catalog/index.json
```

The document MUST be UTF-8 JSON and MUST use schema version
`cncf.component-repository-index.v1`.

## Determinism

A producer MUST order entries by `(kind, artifactId)`. A consumer MAY accept a
different source order but MUST normalize it before deterministic rendering or
snapshot comparison. The pair `(kind, artifactId)` MUST be unique.

## Entry Validation

`kind` MUST be `car` or `sar`. `artifactId` MUST match
`[A-Za-z0-9][A-Za-z0-9._-]*`. `status` MUST be `active`, `deprecated`, or
`disabled`.

`catalog` MUST be a normalized relative path of the form
`<kind>/<artifactId>.yaml`, `.yml`, or `.json`. Absolute paths, backslashes,
traversal, nested paths, query strings, and identity mismatch MUST be rejected.

Optional selector values MUST be non-empty. A consumer MUST treat selectors as
summaries and validate them against the referenced detailed catalog before
artifact resolution.

## Compatibility

A repository without the canonical index does not support global discovery.
Consumers MAY continue resolving a known artifact through its existing detail
catalog. They MUST NOT emulate an index by crawling a remote repository.

Unknown schema versions MUST fail explicitly. A failed refresh MUST preserve a
previous valid cache as stale provenance rather than replacing it with invalid
content.

## Availability Boundary

An index reports repository availability only. It MUST NOT imply that a CAR or
SAR is installed, registered, running, reachable, or healthy.
