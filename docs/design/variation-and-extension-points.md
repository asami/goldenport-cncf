# Variation and Extension Points

Status: Fixed  
Scope: CNCF core design

## 1. Purpose

CNCF separates value variation from behavior replacement.
This distinction preserves architecture clarity, evidence integrity, and
predictable runtime boundaries.

Variation Points describe controlled value choices within a fixed behavior.
Extension Points describe explicit replacement or addition of behavior.

Conflating them hides architectural intent and makes observability and
evidence ambiguous.

## 2. Definitions

### Variation Point

Definition:
- A controlled choice of values within a fixed behavior boundary.

Characteristics:
- Behavior is fixed; values vary.
- Stable interfaces; changes are traceable as evidence.
- Suitable for configuration and policy inputs.
- Does not change the component graph.

Examples:
- Configuration values
- Policy thresholds
- Feature flags (value-only, not behavioral replacement)

### Extension Point

Definition:
- An explicit boundary where behavior can be replaced or added.

Characteristics:
- Behavior is substitutable or pluggable.
- Explicit lifecycle and ownership.
- Changes alter the component graph or execution path.
- Must be explicit and inspectable.

Examples:
- Components
- Drivers
- External services or adapters

## 3. Comparison Table

| Aspect | Variation Point | Extension Point |
|---|---|---|
| Primary form | Value | Behavior |
| Side effects | None by definition | Possible and expected |
| Lifecycle | Configuration lifecycle | Component lifecycle |
| Evidence suitability | Strong (traceable) | Requires explicit registration |
| Observability | Value trace | Dependency graph trace |

## 4. Relation to Dependency Injection

Dependency Injection is a technique, not the concept.
It is commonly used to realize Extension Points by wiring explicit behavior
boundaries at composition time.

Variation Points may be provided via DI, but DI alone does not define whether
the choice is a value or a behavioral extension.

## 5. CNCF-Specific Mapping

Variation Point:
- Configuration values plus evidence (SIE-aligned).

Extension Point:
- Components plus Component Repository registration (CNCF core boundary).

## 6. Design Rules

- Do not mix variation and extension.
- Do not encode extension choice as configuration flags.
- Keep extension boundaries explicit and inspectable.

## 7. Future Directions

- Admin introspection for variation and extension listings.
- Evidence expansion for configuration and repository sources.
- Official / Project Component Repositories.
- SimpleModeling.org BoK integration.
