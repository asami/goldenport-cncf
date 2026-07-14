# Predefined Result Catalog

## Decision

CNCF owns the runtime classes and payload schemas for predefined CML operation
Results. Cozy resolves CML output type names against the catalog selected by the
target CNCF runtime; it does not maintain an independent open-ended Result list.

The initial catalog schema is `cncf.predefined-result.v1` and contains:

| Result | Runtime payload |
|--------|-----------------|
| `OperationResult` | base Result contract |
| `UnitResult` | no payload |
| `IntResult` | `value: int` |

The catalog is exact and case-sensitive. Unknown names are not inferred. CML
operation outputs refer to Result objects, so raw scalar output types such as
`int` and `string` are not aliases for predefined Results.

## Ownership Boundary

CNCF owns:

- runtime Result classes;
- canonical predefined Result names;
- payload field names, datatypes, and multiplicities;
- catalog schema versioning.

Cozy owns:

- resolving CML output references against the selected CNCF catalog;
- rejecting unknown predefined names and raw scalar outputs;
- projecting catalog payload fields into generated operation metadata.

The Scala catalog and runtime classes establish the initial runtime boundary.
Transporting the selected catalog to Cozy generation is a separate integration
step; Cozy must not copy these entries into a second authoritative catalog.
