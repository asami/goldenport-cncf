# Collection and Semi-Structured Data Representation (CNCF)

## Principle

CNCF preserves the semantic shape and ordering of data instead of reducing data
to convenient generic Scala containers.

Goldenport `Record` is the standard representation for semi-structured data in
CNCF. Hierarchical semi-structured data is represented by nested Records, with
ordered sequences represented by Vector-family values.

## Standard choices

- Use `Vector[A]` as the default general-purpose ordered sequence.
- Use Cats `NonEmptyVector[A]` actively when one-or-more is part of the model contract.
- Avoid `List[A]` as a default representation because its performance profile is
  undesirable for CNCF general data handling. Use it only where list-specific
  head/tail or recursive semantics are genuinely required.
- Use Goldenport `Record` for properties, attributes, metadata and other
  semi-structured named data.
- Represent nested semi-structured structures by nesting `Record`; collections
  of records normally use `Vector[Record]` or `NonEmptyVector[Record]`.
- Do not use `Map[String, X]` merely because data consists of names and values.
- Use `Map[K, V]` when keyed lookup/index performance is an actual requirement.
  A Map used as an index is an implementation structure, not the authoritative
  ordered property representation.
- Do not round-trip an ordered property list through Map and reconstruct it from
  Map iteration. Property order can be meaningful for CML/configuration,
  serialization, display, diffs and human-readable artifacts.
- Prefer Record's existing property/navigation/conversion/merge facilities over
  reimplementing equivalent generic Map utilities.
- Use `Set[A]` only when set semantics themselves are intended.
- Cats collection/data types may be used for their semantic or computational
  purpose; they do not replace the CNCF representation model.
- CNCF validation/result handling uses Goldenport `Consequence`; do not adopt
  `ValidatedNel`/similar Cats validation containers as the default validation model.

## Canonical semi-structured shape

```text
Record
|- property: Value
|- child: Record
|- items: Vector[Record]
\- requiredItems: NonEmptyVector[Record]
```

External JSON/YAML/property-oriented input should normally enter CNCF semantic
processing as Record/nested Record rather than becoming a Map-based internal model.

## Map boundary

Map is appropriate for registries, indexes, caches and other keyed lookup
structures where lookup behavior/performance is the point of the structure.

If an ordered Record also needs fast lookup, derive or maintain an internal Map
index without making that index the source from which property order is reconstructed.

## Review guidance

Code review should question Map use when the value represents properties,
attributes, parameters, metadata, configuration or fields. The question is not
"could this be a Map?" but "is keyed lookup/indexing the semantic or performance
reason for this Map?" If not, Record or an ordered Vector-family representation
is normally preferred.
