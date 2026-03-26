# Test Data Import Design (DataStore / EntityStore)

## Purpose

Define a minimal and test-oriented mechanism to:

- load initial data from a specified location
- import that data into `DataStore`
- import that data into `EntityStore`

This is intended primarily for test bootstrap and Executable Specifications.

---

## Current State

### Already available

- `DataStoreSpace.inject(collection, record)`
- `DataStoreSpace.inject(seed)`

This means low-level record injection into `DataStore` already exists.

### Missing

- no file-based test data loading entry point
- no `EntityStoreSpace` import/seed API
- no shared test bootstrap path for loading initial data from an external location

---

## Design Direction

Separate the feature into two layers:

1. **Definition / loading layer**
2. **Store import layer**

This keeps file format concerns separate from runtime persistence concerns.

---

## Layer 1: Test Data Definition / Loader

### Goal

Represent test data independently from the storage backend.

### Minimal model

- `TestDataLocation(path, format)`
- `TestDataDefinition`
- `DataStoreSeed`
- `EntityStoreSeed`

### Responsibility

- read test data from a specified path
- decode it into a framework-owned in-memory definition
- do not perform persistence directly

### Initial format scope

Start with one format only:

- YAML

Do not add JSON / HOCON / directory recursion in the first step.

---

## Layer 2: Store Import

### DataStore import

Use `Record` as the import unit.

This is the low-level route for storage-oriented fixtures.

Suggested API:

```scala
def importSeed(
  seed: DataStoreSeed
)(using ExecutionContext): Consequence[Unit]
```

Implementation can delegate to existing `inject(...)`.

### EntityStore import

Use entity values plus `EntityPersistent[T]`.

This is the domain-oriented route for entity fixtures.

Suggested API:

```scala
def importSeed[T](
  seed: EntityStoreSeed[T]
)(using ExecutionContext, EntityPersistent[T]): Consequence[Unit]
```

Implementation should use `EntityStore.create/save`,
not bypass through raw `DataStore` writes.

---

## Why DataStore and EntityStore must stay separate

Although both are used for test initialization, they serve different purposes.

### DataStore route

- low-level
- storage-focused
- record-based
- suitable for direct fixture injection and physical-state tests

### EntityStore route

- domain-facing
- entity-focused
- typed through `EntityPersistent`
- suitable for entity lifecycle and repository/runtime tests

Mixing them would hide responsibility boundaries and make future behavior
such as lifecycle defaults, audit metadata, or validation harder to preserve.

---

## Relationship to Working Set

This feature is **not** the same as working-set initialization.

### Working set

- memory residency
- active runtime entities
- runtime execution concern

### Test data import

- persistent bootstrap data
- test setup concern
- pre-runtime fixture concern

These should remain separate.

If needed later, a test may explicitly:

1. import persistent seed data
2. load selected entities into the working set

But the mechanisms should not be conflated.

---

## File Shape (Initial Proposal)

Single YAML file may contain both sections:

```yaml
datastore:
  - collection: entity:test.1.person
    records:
      - id: p1
        name: taro
        age: 20

entitystore:
  - collection: test.1.person
    records:
      - id: p2
        name: jiro
        age: 30
```

Interpretation:

- `datastore` entries are loaded as `Record`
- `entitystore` entries are converted through `EntityPersistent.fromRecord`

The first implementation may support only the subset needed by specs.

---

## Mini-Low Development Plan

### Phase 1

Add minimal in-memory seed models.

- `DataStoreSeedEntry`
- `DataStoreSeed`
- `EntityStoreSeedEntry[T]`
- `EntityStoreSeed[T]`

### Phase 2

Add import APIs.

- `DataStoreSpace.importSeed(...)`
- `EntityStoreSpace.importSeed(...)`

### Phase 3

Add test loader.

- `TestDataLoader.load(path)`
- YAML only

### Phase 4

Add Executable Specifications.

- import from path into `DataStore`
- import from path into `EntityStore`
- verify through search/load behavior

---

## Acceptance Criteria

The feature is considered complete at the initial level when:

- test data can be loaded from a specified file path
- `DataStore` can be initialized from that definition
- `EntityStore` can be initialized from that definition
- `EntityStore` initialization uses `EntityPersistent`
- Executable Specifications verify the behavior

---

## Explicit Non-Goals (Initial Step)

- working set preload integration
- directory-based recursive loading
- multiple file formats
- clear-before-load policies
- sophisticated upsert modes
- production bootstrap integration

---

## Recommendation

Start with:

1. seed model
2. `EntityStoreSpace.importSeed`
3. `DataStoreSpace.importSeed`
4. Executable Spec
5. file loader last

This keeps the first implementation small, verifiable, and aligned with current CNCF test usage.

## Implementation Note

The minimal first step is now implemented in CNCF.

## Startup Import Boundary Note

`cncf.import.entity.file` is intentionally deferred.

Reason: the framework still lacks a bootstrap decode contract for `Record -> typed entity`.
That contract must be defined explicitly before startup entity import is enabled.

Delivered:

- `DataStoreSeed` / `DataStoreSeedEntry`
- `EntityStoreSeed` / `EntityStoreSeedEntry`
- `DataStoreSpace.importSeed(...)`
- `EntityStoreSpace.importSeed(...)`
- Executable Specifications for both routes

Verification:

```sh
cd /Users/asami/src/dev2025/cloud-native-component-framework
sbt compile
sbt "testOnly org.goldenport.cncf.datastore.DataStoreImportSeedSpec org.goldenport.cncf.entity.EntityStoreImportSeedSpec"
```

Result: passed.

Assumption used in the entity import spec:

- the imported entities are loaded back through the same entity ids supplied in the seed, and the spec uses a distinct in-memory collection so it does not collide with unrelated runtime data.
