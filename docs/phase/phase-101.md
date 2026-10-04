# Phase 101: Component Project Resource and Workspace API

Status: planned
Planned: 2026-10-05
Driven by: sm-workflow Phase 5 for the minimum resource API; sm-workflow Phase 7 for the RepositorySync/worktree expansion

## Driven-development sequencing

sm-workflow Phase 5 is allowed to start before this phase is complete. When Phase 5 reaches its project-resource integration boundary, it explicitly drives this Phase 101 and requires a minimum accepted slice before Phase 5 may close.

The minimum Phase-5 slice MUST establish the logical Component resource contract and at least the runtime-state/DataStore binding needed to prove that sm-workflow does not construct physical project paths. Workspace semantics may be introduced in that slice when implementation cohesion warrants it, but the dedicated multi-repository worktree scenario is driven and hardened by sm-workflow Phase 7.

Phase 101 remains a CNCF-owned phase: sm-workflow supplies the driver requirements and acceptance scenario but MUST NOT copy or locally emulate the missing CNCF abstraction.

## Goal

Provide a CNCF-level logical API/DSL for component-owned project resources so application components can use configuration, DataStore-backed runtime state, workspaces/worktrees, working files, and other project-local resources without depending on physical directory layout.

sm-workflow is the first driver application. The API is generic CNCF/Textus infrastructure and MUST NOT encode sm-workflow-specific repository or Git semantics.

## Existing configuration boundary

CNCF already resolves Component configuration across the current-directory, project-root, home, and system hierarchy and returns the merged configuration to the consumer. Phase 101 MUST reuse that mechanism rather than introduce a second configuration resolver.

Configuration and runtime resources are related by Component identity but have different lifecycle semantics:

- configuration: existing hierarchical lookup/merge;
- runtime state: logical DataStore/resource binding;
- workspace/worktree: project-owned mutable work area;
- working file: bounded execution-time file/resource;
- cache: optional regenerable resource.

## Logical API direction

Consumers should express intent, not paths. The concrete API names are to be reconciled with existing CNCF naming, but the semantic shape is equivalent to:

```scala
componentContext.config
componentContext.dataStore("state")
componentContext.workspace("dependency-cncf")
componentContext.workingFile("...")
componentContext.cache("...")
```

The consumer MUST NOT need to know whether the local provider maps these resources below `$PROJECT/.textus/<component>/`, a platform data directory, an external store, or another configured backend.

## Default local project mapping

For project-local operation, the default provider SHOULD map component-owned project resources under the project Textus area:

```text
$PROJECT/.textus/<component>/
```

For sm-workflow this permits a local representation conceptually containing runtime state and dedicated worktrees below `$PROJECT/.textus/sm-workflow/`. This is a provider mapping, not an application contract.

Home/system Component configuration remains under the existing CNCF configuration mechanism. Phase 101 does not redefine its physical search rules.

## Resource metadata and lifecycle

The API MUST distinguish resource semantics sufficiently for providers and tooling to determine lifecycle and version-control policy without filename heuristics. At minimum evaluate:

- PROJECT_CONFIGURATION — existing configuration mechanism, normally versionable when project-local;
- MANAGED_RESOURCE — durable project resource that may be version-controlled;
- RUNTIME_STATE — durable runtime state, normally not version-controlled;
- WORKSPACE — mutable working area/worktree, not version-controlled as contained files;
- WORKING_FILE — bounded temporary/intermediate work product;
- CACHE — regenerable and disposable.

Do not force these exact names if existing CNCF abstractions provide a better vocabulary. Preserve the semantic distinctions.

## DataStore integration

Runtime state MUST use the CNCF DataStore abstraction. A local SQLite binding may resolve to project-local storage through this resource mechanism, but domain/application code MUST NOT construct SQLite paths, JDBC URLs, table names, or filesystem persistence rules.

The design MUST remain compatible with non-SQLite providers such as PostgreSQL for server/multi-host profiles.

## Workspace integration

A Component can request named project workspaces without constructing directories. The local provider owns creation and physical placement.

Git worktree/checkouts are an sm-workflow use case, not a CNCF primitive requirement. CNCF supplies a generic workspace resource; sm-workflow decides that a workspace is used as a repository worktree.

## Version-control policy

Phase 101 MUST make the distinction between versionable project resources and runtime/work resources explicit enough that project tooling can derive or validate ignore policy. It MUST NOT silently edit arbitrary repository files as a side effect merely to enforce ignore rules.

The initial sm-workflow expectation is:

- project configuration: versionable;
- runtime DataStore state: ignored;
- workspaces/worktrees: ignored;
- transient work/cache: ignored.

## sm-workflow driver scenario

Project X uses project-specific CNCF and Cozy branches. sm-workflow Phase 7 asks CNCF for two named workspaces, materializes/uses Git worktrees there, synchronizes them, and runs dependency-aware validation. sm-workflow never asks for or constructs a physical directory path except through the resource handle/provider boundary.

The same project also binds sm-workflow runtime state through DataStore without knowing the SQLite file location.

This scenario is the acceptance driver, but the resulting API MUST be reusable by other components.

## Executable Specification requirements

Demonstrate at least:

1. a component obtains merged configuration through the existing CNCF configuration mechanism;
2. a component obtains a project-local runtime DataStore without constructing a filesystem path;
3. a component obtains two distinct named workspaces without constructing their directories;
4. the local provider maps project resources under the project Textus component area by default;
5. changing provider mapping does not require application/workflow changes;
6. runtime/workspace resources are distinguishable from versionable project configuration/resources;
7. a non-SQLite DataStore binding remains possible without changing the consumer API;
8. the sm-workflow RepositorySync driver can use the API for dedicated CNCF/Cozy worktrees.

## Non-goals

- Reimplementing CNCF hierarchical configuration merge.
- Encoding sm-workflow, Git, sbt, CNCF-repository, or Cozy-repository semantics in the generic API.
- Requiring SQLite as the universal persistence provider.
- Exposing physical `.textus` paths as Workflow/domain semantics.
- Adding content hashes, integrity ledgers, rollback copies, or defensive contamination machinery.
