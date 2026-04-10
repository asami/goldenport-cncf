# Component Repository Specification

Status: Historical Draft  
Scope: Phase 2.6 / Stage 5 -> Stage 6+

Current note:

- this document describes the earlier repository-only model
- current runtime distinguishes:
  - search repositories
  - active packaged component sources
- current CLI direction is:
  - `--repository-dir <path>`
    - search packaged `CAR` / `SAR`
  - `--component-dir <path>`
    - activate packaged `CAR` / `SAR`
  - `--textus.runtime.component=<name>`
    - select a packaged component by name from search repositories
  - `--textus.runtime.subsystem=<name>`
    - select a subsystem by name
- `--repository-dir` is the packaged search-oriented option

## 1. Concept

A Component Repository is an abstract source from which CNCF discovers and loads
executable Components.

CNCF may use multiple Component Repositories simultaneously.
Each repository is defined by:
- a repository type
- an optional base directory

The repository type determines how directory structures are interpreted and
how Components are discovered and loaded.

This mechanism unifies all Component discovery paths under a single abstraction.

## 2. CLI Interface

### Legacy Option

    --repository-dir <path>

Current primary options:

    --repository-dir <path>
    --component-dir <path>

### spec syntax

    <repository-type>[:<directory>]

- repository-type is required
- directory is optional
  - If omitted, the default directory defined by the repository type is used

## 3. Repository Types

### scala-cli

- repository-type: scala-cli
- default directory: .scala-build

Interpretation:

    .scala-build/**/classes

Purpose:
- Use Components compiled by scala-cli
- Primary mechanism for Stage 5 demos

### sbt

- repository-type: sbt
- default directory: target

Interpretation:

    target/scala-*/classes
    target/scala-*/test-classes (optional)

Purpose:
- Use Components built via sbt

### component-dir

- repository-type: component-dir
- default directory: component.dir

Interpretation example:

    component.dir/
    |- classes/
    |- lib/
    |- meta/

Purpose:
- Integrates the existing component.dir mechanism
- Treated as one concrete implementation of Component Repository
- No special-case handling in the runtime

### 3.4 component-dir Repository Layout (Normative)

The component-dir repository follows a conventional directory layout
for hosting locally installed Components.

    component.dir/
    |- classes/   # compiled component classes
    |- lib/       # jar-based components
    |- meta/      # descriptors / manifests (future)

This layout is interpreted exclusively by the component-dir repository
implementation. Other repository types are not required to follow
this structure.

## 4. Multiple Repositories

Multiple repositories may be specified using a comma-separated list in the legacy form.

Example:

    --repository-dir repository.d

Current preferred shape is:

    --repository-dir /path/to/repository.d
    --component-dir /path/to/component.d

Priority and override rules are defined separately.

## 5. Relationship to Existing Design

- component.dir is not a special mechanism
- It is redefined as a directory-based Component Repository
- All discovery and registration flows are unified under the repository mechanism
- Components are integrated before Subsystem construction

## 6. Future Repository Types (Directional)

The design allows future extension without CLI changes:

- official   (SimpleModeling.org official Component Repository)
- project    (Project BoK Component Repository)
- jar
- remote

These repository types are intended to support future integration
with SimpleModeling.org and project-specific Bodies of Knowledge (BoK).

An official repository provides curated, versioned Components
maintained and published by SimpleModeling.org as part of the
platform's canonical knowledge base.

A project repository exposes Components derived from a project's BoK,
including domain-specific logic, rules, and generated Components.
This allows CNCF to execute Components that are directly aligned
with a project's documented knowledge and models.

## 7. Stage 5 Positioning

Stage 5 implementations cover:
- scala-cli
- sbt (optional)
- component-dir

official and project are specification-only at this stage.

## 8. Summary

- the earlier single discovery mechanism has been removed
- Repositories are specified as repository-type[:directory]
- Default directories are repository-type specific
- current runtime also distinguishes active packaged directories from search repositories
- This specification remains valid beyond Stage 5
