# Component Repository Index Contract

## Canonical Resource

```text
repository/catalog/index.json
```

The document MUST be UTF-8 JSON and MUST use schema version
`cncf.component-repository-index.v2`.

## Determinism

A producer MUST order CAR entries by `(kind, namespace, id)` and SAR entries
by `(kind, "", artifactId)`. A consumer MAY accept a different source order but
MUST normalize it before deterministic rendering or snapshot comparison.
Canonical identities MUST be unique. Equal CAR artifact IDs and filenames are
permitted when namespaces differ.

## Entry Validation

`kind` MUST be `car` or `sar`. `artifactId` MUST match
`[A-Za-z0-9][A-Za-z0-9._-]*`. `status` MUST be `active`, `deprecated`, or
`disabled`. A CAR MUST carry exact `namespace` and local `id`, admitted through
the shared identity ABI. Its `artifactId` and catalog path are verified derived
projections; they are not independent inputs. A SAR MUST NOT carry `namespace`
or `id` and retains its artifact-keyed contract.

CAR `catalog` MUST equal the shared
`car/<namespace-group-path>/<derived-artifact>.yaml` projection. SAR `catalog`
MUST be a normalized relative path of the form
`sar/<artifactId>.yaml`, `.yml`, or `.json`. Absolute paths, backslashes,
traversal, query strings, unknown fields, wrong types, explicit null optional
values, malformed JSON, duplicate object keys, and identity/projection mismatch
MUST be rejected.

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
