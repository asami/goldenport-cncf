# Observation/Descriptor-Based Error Notification Guidelines (Draft)

Status: draft

## 1. Purpose

In CNCF, error notification and decision-making should be structured around
`Conclusion -> Observation -> Descriptor (Facets)`, instead of matching `message` strings.

Goals:
- Be resilient to implementation changes (tests should not break on wording changes)
- Improve classification, aggregation, and observability
- Share the same failure semantics across API/CLI/logs

## 2. Basic Policy

1. Prioritize `Observation` and `Descriptor` for failure decisions
2. Treat `message` as display support (human-facing)
3. Do not use `message` as a primary specification key
4. Return failures as `Consequence.Failure(Conclusion)`
5. Use `Conclusion.status` for HTTP/external boundary mapping

## 2.1 Consequence Helper-First Policy

For recurring patterns, always use existing `Consequence` helper functions first.
Do not hand-write equivalent `match` branches unless a helper is unavailable.

Priority:
1. Existing helper (`Consequence.fromOption`, `Consequence.successOrEntityNotFound`, etc.)
2. Standard combinators (`map`, `flatMap`, `fold`)
3. Explicit pattern match (`Success` / `Failure`) only when necessary

## 2.2 Frequently Used Consequence Helpers (Current CNCF Codebase)

### `Consequence.fromOption`

Use when converting `Option[A]` into `Consequence[A]`.

```scala
Consequence.fromOption(actionProperty, "Property not found: name")
```

### `Consequence.successOrEntityNotFound`

Use for entity resolution where `None` means canonical not-found.

```scala
Consequence.successOrEntityNotFound(entityOption)(id)
```

### `Consequence.successOrServiceProviderByKeyNotFound`

Use when resolving keyed providers/registries.

```scala
Consequence.successOrServiceProviderByKeyNotFound(providerOption, key)
```

### `Consequence.unit`

Use as canonical success value for `Consequence[Unit]`.

```scala
plan.fold(Consequence.unit)(executePlan)
```

### `Consequence.failUninitializedState.RAISE`

Use for required runtime/component wiring that must exist.

```scala
component.map(_.aggregateSpace).getOrElse(Consequence.failUninitializedState.RAISE)
```

### `Consequence.success` / `Consequence.failure`

Use these constructors only when there is no more specific helper.

```scala
Consequence.success(value)
Consequence.failure("invalid selector")
```

### `c.toOption.flatten` (instance-side extraction)

Use to extract optional config values from `Consequence[Option[A]]`
without manual `Success`/`Failure` matching.

```scala
configuration.get[String](key).toOption.flatten
```

## 3. Decision Priority (at Runtime/Test)

When branching by failure reason in tests and implementation:

1. `Conclusion.status`
2. `Observation.phenomenon` / `Observation.taxonomy` (`category`, `symptom`)
3. `Descriptor facets` (`resource`, `operation`, `parameter`, `state`, etc.)
4. `Observation.cause` (structured cause)
5. `message` (final fallback aid)

## 4. Descriptor Design Rules

### 4.1 Facet First

Facets should separate machine-decision information into minimal units.

Examples:
- resource: `entity`, `collection`, `operation`
- identifier: `entityId`, `component`, `service`, `operation`
- constraint: `required`, `type-mismatch`, `unsupported`, `ambiguous`
- lifecycle: `postStatus`, `aliveness`

### 4.2 Message Is Narrative

Keep `message` for user/operator explanation,
but do not use it for branch conditions or primary spec assertions.

### 4.3 Stable Keys

Facet key/value names should be stable for backward compatibility.
Do not vary key names for the same failure semantics.

## 5. Error Notification Templates

### 5.1 Missing

- status: 404 / 400 (context-dependent)
- taxonomy.category: `argument` or `resource`
- taxonomy.symptom: `missing`
- facets:
  - `resource` = target type
  - `identifier` = target ID/selector

### 5.2 Ambiguous

- status: 400
- taxonomy.category: `argument`
- taxonomy.symptom: `ambiguous`
- facets:
  - `resource` = selector type
  - `candidates` = candidate count or candidate set

### 5.3 Not Allowed / Policy

- status: 403
- taxonomy.category: `policy`
- taxonomy.symptom: `forbidden`
- facets:
  - `capability` or `privilege`
  - `resource`

### 5.4 Runtime Evaluation Failure (Guard/Action)

- status: 400 or 500 (separate input-caused vs internal-caused)
- taxonomy.category: `evaluation` or `runtime`
- taxonomy.symptom: `guard-failure` / `action-failure`
- facets:
  - `guard` or `action` name
  - `phase` = `exit|transition|entry`

## 6. StateMachine Application Guidelines

### 6.1 guard=false

- Do not treat as Failure
- Treat as no-match and continue to the next candidate

### 6.2 Guard Evaluation Failure

- Return `Consequence.Failure(Conclusion)`
- Make `guard-failure` identifiable in taxonomy
- Include `guard name` / `phase` in facets

### 6.3 Binding Resolution Failure

- Separate `missing` and `ambiguous` by taxonomy/facets
- Use `message` only as supplemental explanation

## 7. Testing Guidelines

### 7.1 Recommended Assertions

- `status` match
- taxonomy (`category`, `symptom`) match
- facet key/value match (minimum required set)

### 7.2 Discouraged Assertions

- `message == "exact full sentence"`

### 7.3 Acceptable Supplemental Assertions

- `message contains` can be used as a supplemental operational text check

## 8. Incremental Adoption Steps

1. Require taxonomy/facets for new failures
2. Add facets to existing message-only failures
3. Migrate primary spec assertions from message-based to facet-based
4. Aggregate monitoring/observability using taxonomy/facets

## 9. Non-Goals

- Replacing all existing failures at once
- Eliminating `message`

## 10. Open Questions

1. Where to define the canonical taxonomy vocabulary
2. Where to standardize facet key naming (`snake_case` vs `camelCase`)
3. How strict to make status vs taxonomy responsibility boundaries

## 11. Items to Finalize Before Design Promotion

### 11.1 Canonical Taxonomy Vocabulary

Define and freeze allowed `category/symptom` combinations for major failure classes.
At minimum, explicitly define:
- missing
- not-found
- ambiguous/conflict
- invalid
- forbidden/policy

This prevents drift such as using different categories for the same semantic failure.

### 11.2 Required Facet Set per Failure Class

Define a minimum required facet set per class.

Recommended baseline:
- missing/not-found:
  - `Facet.Operation` or `Facet.Component`/`Facet.Service`/`Facet.Resource`
  - identifier facet (`Facet.Id` / `Facet.Key` / equivalent)
- ambiguous/conflict:
  - target facet (operation/resource)
  - candidate size or candidate identifier (`Facet.Value` etc.)
- invalid:
  - target facet
  - reason facet (`Facet.Value`, `Facet.Message`, or typed detail facet)

### 11.3 Consequence Helper Selection Rules

Promote current practice into explicit rules:
- MUST use `Consequence.fromOption` for `Option[A] -> Consequence[A]`
- MUST use `Consequence.successOrEntityNotFound` for entity load `Option` resolution
- SHOULD use `Consequence.successOrServiceProviderByKeyNotFound` for keyed provider resolution
- SHOULD avoid hand-written `Success/Failure` pattern matching when helper/combinator exists

### 11.4 Message Boundary

Clarify message usage:
- `message` is human-facing narrative/log context
- tests MUST NOT rely on full-string equality
- tests MAY use `contains` only as supplemental assertion
- primary assertions MUST be taxonomy + facets

### 11.5 Official Observation Access Path

Standardize the structured access path in code and tests:
- `conclusion.observation.taxonomy`
- `conclusion.observation.cause.descriptor.facets`

Avoid non-structured fallback (`toString`, rendered text) for core assertions.

### 11.6 Executable Spec Template

Define minimal reusable test template for each failure family:
- Given/When/Then structure
- assert:
  - `taxonomy.category`
  - `taxonomy.symptom`
  - required facet presence/value
- optional:
  - `message contains` for operational readability

Recommended first canonical templates:
1. Missing/not-found
2. Ambiguous/conflict
3. Invalid
