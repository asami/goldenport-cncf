# Collection and Semi-Structured Data Representation (CNCF)

## Principle

CNCF preserves the semantic shape and ordering of data instead of reducing data
to convenient generic Scala containers.

Goldenport `Record` is the standard representation for semi-structured data in
CNCF. Hierarchical semi-structured data is represented by nested Records, with
ordered sequences represented by Vector-family values.

Choose types from cardinality and semantic/access requirements, not from a menu
of interchangeable Scala collections. This rule governs public Operation inputs
and outputs, generated CML models, domain attributes and persistence boundaries.
It is the target contract for migration, not a claim that existing generators
and consumers already implement every requirement.

## Cardinality and semantic contract

| Meaning | Standard representation |
| --- | --- |
| Required single value (`1`) | `A` |
| Optional single value (`0..1`) | `Option[A]` |
| Ordered values (`0..*`) | `Vector[A]` |
| Ordered nonempty values (`1..*`) | `cats.data.NonEmptyVector[A]` |
| Semi-structured named attributes | Goldenport `Record` |
| Key/value association or lookup index | `scala.collection.immutable.Map[K, V]` |
| Set semantics, with no duplicate members | `scala.collection.immutable.Set[A]` |

Cardinality alone does not declare uniqueness, key identity or ordering semantics.
Ordinary multiple values use Vector and retain order and duplicates. Do not
convert them to Set or Map for convenience. Bounded multiplicities retain their
declared constraints through the existing admission contract; neither Vector
nor NonEmptyVector alone encodes arbitrary minimum/maximum bounds.

## Stored/returned values versus internal inputs

Standard domain values, public business inputs and outputs, generated model
fields and materialized database result bodies MUST use the selected concrete
representation. Being an input parameter does not make a public business
contract exempt from this rule.

An internal utility MAY accept `scala.collection.immutable.IndexedSeq[A]` when
it needs efficient indexed access without fixing the implementation. It MAY
accept immutable Seq or Iterable when only those capabilities are needed.
Use IterableOnce only for an explicitly single-pass contract. Do not convert
an already suitable argument merely to match the standard storage representation.

IndexedSeq includes Vector and ArraySeq, but not List. It does not promise
Vector's persistent append/update characteristics. LinearSeq expresses efficient
head/tail access; use it exceptionally when that access structure is relevant.
List is appropriate for an explicitly cons/stack/recursive structure, not the
default database or generated-model representation. Do not select it simply
because it existed in an earlier declaration. Use immutable abstractions when
the contract requires immutable values.

## Database and generated-model boundaries

- Keep the existing DataStore/EntityStore SearchResult containers and their
  range, cursor, total-count, offset, limit and fetched-count semantics. Their
  result bodies remain Vector; zero matches means an empty Vector, not failure.
- An optional single lookup returns Option within the existing effect/failure
  model. A required single lookup returns the value or its established failure.
  Neither storage failure nor an unexpected duplicate may be hidden as None
  or an arbitrary first match.
- Vector preserves the retrieved order; it does not create a stable SQL order.
  Stable paging requires the query's explicit ordering/tie-breaking contract.
- Generate ordinary CML multiple-value attributes as Vector and nonempty
  attributes as NonEmptyVector. Decode the declared nonempty type at its owning
  boundary and reject empty input through the existing failure model. A normal
  search page is not nonempty merely because some domain attribute is nonempty.
- Record is the canonical semi-structured object representation. Omit native
  null-valued properties at the owning input boundary; retain empty objects and
  arrays. Do not drop null sequence elements and change their positions. Raw
  XJson/typed Json.Null follows its separate document contract. Phase 115 owns
  this normalization migration and explicit SQL-clear preservation.
- A lower-level codec accepting Seq does not establish Seq/List as the generated
  domain type. Keep the materialized path Vector-based and reconstruct
  NonEmptyVector only where required by the model. Do not add List restoration
  merely to preserve an old generated declaration.
- Build result collections directly with an appropriate builder where useful.
  Avoid repeated List/Vector conversions and repeated List tail appends. Large
  reads use explicit paging or resource-managed streaming; LinearSeq/LazyList
  is not a substitute for a database cursor lifecycle contract.

## Migration and acceptance

Reassess existing List/Seq declarations by their actual meaning. For ordinary
multiple-value declarations, update the model/generation specification, generator,
affected callers and executable specifications together under their owning
projects. Do not hand-edit generated sources or add a competing reconstruction
path. Preserve List or another representation only for an explicit semantic or
external-library contract; perform necessary external conversion at that boundary.

Demonstrate empty and nonempty searches, result metadata, ordered values and
duplicates, optional/required single lookup, declared nonempty reconstruction,
and empty nonempty-input failure through the actual integrated route. Check
generated signatures as well as runtime behavior. Do not add per-layer concrete
class assertions, copied-state checks or a repository-wide ban on List tokens.
This rule does not authorize live-data rewriting or imply measured speedups.

Implementation adoption: [Phase 115](../phase/phase-115.md). Existing Phase 100
acceptance/history is preserved; producer changes remain with Core,
SimpleModeler or Cozy as appropriate.

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
