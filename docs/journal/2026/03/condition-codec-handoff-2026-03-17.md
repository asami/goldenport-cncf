# Condition Codec Handoff (2026-03-17)

## Status

Pending implementation in CNCF (`cloud-native-component-framework`).

## Background

`simple-modeler` was updated so auto-generated Query entities use `Condition[T]` fields.

Example generated shape:

```scala
case class Person(
  id: Condition[EntityId],
  name: Condition[Name],
  age: Condition[Age]
) extends EntityPersistable derives Codec.AsObject
```

With current CNCF, compile fails because `Condition[A]` has no Circe codec instance.

## Reproduction

1. Generate code from cozy:

```bash
sbt "run modeler-scala /Users/asami/src/dev2025/cozy/codex-test.d/test.dox --save=/Users/asami/src/dev2025/cozy/codex-test.d/x-query-condition-20260317c.d"
```

2. Compile generated project:

```bash
cd /Users/asami/src/dev2025/cozy/codex-test.d/x-query-condition-20260317c.d
sbt compile
```

3. Error (summary):

- file: `target/scala-3.3.7/src_managed/main/scala/domain/query/Person.scala`
- line: `derives Codec.AsObject` on `case class Person(...)`
- message: cannot summon Circe codec for `Condition[EntityId]` (and other `Condition[T]`)

## Requested CNCF Change

Add Circe codec support for `org.goldenport.cncf.directive.Condition`.

Target file:

- `src/main/scala/org/goldenport/cncf/directive/Condition.scala`

Expected outcome:

1. `Condition[A]` can participate in Circe encode/decode when `A` has codec support.
2. Query case classes deriving `Codec.AsObject` compile without extra per-model code.

## Design Notes

Current ADT:

- `Condition.Any`
- `Condition.Is[A](expected: A)`
- `Condition.In[A](candidates: Set[A])`
- `Condition.Predicate[A](f: A => Boolean)`

`Predicate` cannot be faithfully serialized/deserialized as data.
Please define codec behavior explicitly. Recommended options:

1. Support data-serializable constructors only (`Any`, `Is`, `In`) and fail on `Predicate`.
2. Introduce a separate serializable query-condition ADT and map from/to `Condition`.

Either approach is acceptable if behavior is explicit and stable.

## Acceptance Criteria

1. Generated query model with `Condition[T]` fields compiles with `derives Codec.AsObject`.
2. Round-trip behavior for `Any`/`Is`/`In` is covered by tests.
3. `Predicate` behavior is documented and tested (reject or mapped alternative).

