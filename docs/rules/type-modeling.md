# Type Modeling Rules: abstract class vs trait

## Summary

Use `abstract class` to represent a core conceptual trunk in the domain model,
even if it has no shared implementation. Use `trait` only for supplementary,
mix-in style capabilities. This rule prioritizes modeling semantics over
technical minimalism (e.g. “everything is a trait”).

## Core Rule

- Use `abstract class` for core conceptual entities that define a model trunk.
- Use `trait` only for auxiliary, composable behavior.

## Rationale

- “Everything is a trait” optimizes for technical flexibility, not modeling
  intent.
- Modeling clarity is prioritized so that type hierarchies communicate domain
  meaning directly.

## Examples

- Projection as an abstract class
- Ingress / Egress as abstract classes
- Traits as mix-in capabilities (e.g. optional behaviors)

```scala
abstract class Projection[Out] { /* ... */ }
```

## Non-goals

- This is not a naming rule.
- This does not mandate inheritance depth.
- This does not forbid traits for utilities.

---

## CNCF perspective

- CNCF types model execution roles, not just data shapes.
- Action and ActionCall are execution roles in the CNCF runtime.
- ActionCall is a control object, not a domain model.
- Message / Event types may be introduced later with explicit execution roles.
