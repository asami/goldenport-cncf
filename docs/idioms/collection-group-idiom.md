Collection / Group Idiom
========================

**Idiom:** CollectionGroup

---

## Intent

Provide a shared design idiom for aggregation types that are common
across core and cncf, while preserving binary compatibility and
clarifying when aggregation is semantic vs structural.

This is a **design guide**, not a rule.

---

## Conceptual Distinction

- **Collection**: structural aggregation of elements
- **Group**: semantic aggregation with meaning and possible invariants

A Collection primarily provides container behavior.
A Group primarily provides domain meaning and may enforce constraints
(e.g., non-empty, invariant ordering, or validation).

---

## Collection Idiom (Canonical Pattern)

### Characteristics

- `final case class`
- internal `Vector`
- `empty` is provided
- zero-arg `apply()` is provided for binary compatibility

### Canonical Form

```scala
final case class XCollection(
  items: Vector[X]
)

object XCollection {
  val empty: XCollection = XCollection(Vector.empty)
  def apply(): XCollection = empty
}
```

### Notes

- The zero-arg `apply()` supports binary compatibility with
  previously compiled code and improves Java/DSL friendliness.
- `empty` provides a stable named instance so call sites avoid
  referencing `Vector.empty` directly.

---

## Group Idiom

### Characteristics

- semantic aggregation of elements
- may enforce invariants or non-empty constraints
- may provide domain-specific operations

### Example Sketch

```scala
final case class XGroup(
  items: Vector[X]
) {
  require(items.nonEmpty, "XGroup must not be empty")
}
```

---

## Choosing Collection vs Group

Prefer **Collection** when:
- aggregation is structural only
- empty state is valid
- callers need easy composition

Prefer **Group** when:
- aggregation carries semantic meaning
- invariants must be enforced
- empty state is invalid or ambiguous

---

## Binary Compatibility Guidance

Collection idioms are binary-compatibility friendly because they
stabilize construction patterns:

- named `empty`
- zero-arg `apply()`

These patterns reduce breakage when constructor shapes evolve.

---

## Known Uses

- Protocol handler collections (IngressCollection, EgressCollection)
- ProjectionCollection

---

## Related Rules

- `docs/rules/binary-compatibility.md`

---

## Summary

Use Collection for structural aggregation and Group for semantic
aggregation. Keep construction patterns stable to preserve binary
compatibility and improve API clarity.
