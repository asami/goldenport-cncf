# Help Canonical Name And Selector Direction

Status: Design Note

## Problem

Current help output mixes two different concepts:

- model-level formal names
- runtime selector forms used by CLI and REST

For example, a help result may currently show:

```yaml
name: Crud.entity.searchItemRecord
usage:
  - command Crud.entity.searchItemRecord
```

This is ambiguous because:

- `Crud`, `Entity`, and `searchItemRecord` are the formal model names
- `crud.entity.search-item-record` is the practical CLI/REST selector form

The help output should distinguish them explicitly.

## Direction

### 1. Formal names stay formal

Model-level names should be shown in their original formal form:

- component name: `Crud`
- service name: `Entity`
- operation name: `searchItemRecord`

These should appear in dedicated fields such as:

- `component`
- `service`
- `name`

or equivalent structured fields.

### 2. Runtime selectors are shown separately

CLI and REST selector forms should be displayed as selector information, not as
the operation `name`.

Recommended structure:

```yaml
type: operation
name: searchItemRecord
component: Crud
service: Entity
selector:
  canonical: Crud.Entity.searchItemRecord
  cli: crud.entity.search-item-record
  rest: /crud/entity/search-item-record
```

### 3. Usage should prefer kebab-case

`usage` should prefer the CLI-facing runtime selector form:

```yaml
usage:
  - command crud.entity.search-item-record
```

If the formal selector form is still accepted for compatibility, it may be
shown as an additional accepted form, but it should not be the primary usage.

Example:

```yaml
usage:
  - command crud.entity.search-item-record
accepted-selectors:
  - Crud.Entity.searchItemRecord
```

## Rule

- `name` means formal model name
- `selector.*` means runtime addressing form
- `usage` should prefer CLI-friendly kebab-case

## Scope

This direction applies to:

- command help output
- metadata projections that expose names and selectors
- sample README examples that currently treat mixed-case selectors as primary

This direction does not change:

- Scala class names
- formal component/service/operation names in the model
- compatibility acceptance of existing selector forms
