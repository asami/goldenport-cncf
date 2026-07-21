# Component Repository Discovery

## Responsibility

CNCF owns the transport-neutral contract used to enumerate CAR and SAR
catalogs. The canonical public index is
`repository/catalog/index.json`. It is a bounded discovery resource and does
not replace per-artifact catalogs.

Cozy publishes and validates the index. Textus Launcher retrieves configured
public indexes and combines them with cache and local repositories. CNCF
Launcher projects admitted development/local artifacts to the same identity.
Applications may consume the normalized snapshot, but must not infer process
state from repository availability.

## Contract Boundary

The v1 index uses `schemaVersion` value
`cncf.component-repository-index.v1`, an RFC 3339 `generatedAt`, and explicit
`artifacts`. Every artifact has:

- `kind`: `car` or `sar`;
- `artifactId`: canonical artifact identity;
- `catalog`: safe relative detail path such as `car/textus-blog.yaml`;
- `status`: `active`, `deprecated`, or `disabled`;
- optional `recommended`, `latestStable`, and `latestSnapshot` summaries.

The detail path must remain directly below its kind directory, and its filename
stem must equal `artifactId`. Consumers validate the referenced detail catalog
before using versions or archive locations. Index selectors are summaries;
the detail catalog remains authoritative.

## Safety

Discovery reads an explicit index. It does not use HTTP directory listing,
recursive repository crawling, archive inspection, process discovery, or
implicit download. A missing index means global enumeration is unavailable;
known-artifact catalog resolution remains valid.

Index state is availability knowledge. Runtime registration, endpoint, process,
and health are separate contracts. Deprecated and disabled entries remain
valid repository facts.
