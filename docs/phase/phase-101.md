# Phase 101: Component Project Resource and Workspace API

Status: planned
Planned: 2026-10-05
Driven by: sm-workflow Phase 5 for the minimum resource API; sm-workflow Phase 7 for the RepositorySync/worktree expansion

## Driven-development sequencing

sm-workflow Phase 5 is allowed to start before this phase is complete. When Phase 5 reaches its project-resource integration boundary, it explicitly drives this Phase 101 and requires a minimum accepted slice before Phase 5 may close.

The minimum Phase-5 slice MUST establish the logical Component resource contract and at least the runtime-state/DataStore binding needed to prove that sm-workflow does not construct physical project paths. Workspace semantics may be introduced in that slice when implementation cohesion warrants it, but the dedicated multi-repository worktree scenario is driven and hardened by sm-workflow Phase 7.

Phase 101 remains a CNCF-owned phase: sm-workflow supplies the driver requirements and acceptance scenario but MUST NOT copy or locally emulate the missing CNCF abstraction.

### Closure sequencing

Phase 101 has two explicit acceptance milestones:

1. **Minimum slice accepted (sm-workflow Phase 5 gate).** The logical Component resource contract and required runtime-state/DataStore binding are implemented and accepted through sm-workflow Phase 5. Reaching this milestone allows sm-workflow Phase 5 to close, but CNCF Phase 101 remains OPEN.
2. **Full acceptance (sm-workflow Phase 7 gate).** sm-workflow Phase 7 exercises the API with real dependency-aware RepositorySync and dedicated named CNCF/Cozy worktrees, including resource lifecycle and project-local version-control policy. Only after this driver acceptance succeeds may CNCF Phase 101 close.

Closure order for the final milestone is strict: sm-workflow Phase 7 reaches its upstream acceptance gate -> CNCF Phase 101 records full acceptance and closes -> sm-workflow Phase 7 records the closed upstream dependency and may close. Phase 101 MUST NOT be closed merely because its API compiles or because the Phase 5 minimum slice passed.

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

For project-local operation, the standard Component area is:

```text
$PROJECT/.textus/<component>/
  config.yaml                 # definition/configuration, version-controlled
  <other-definition-files>    # definition files, version-controlled
  resources/                  # durable project resources, version-controlled
  work.d/                     # runtime/work area, NOT version-controlled
    state/                    # local DataStore files where applicable
    worktrees/                # repository/worktree materialization where applicable
    tmp/                      # bounded temporary work
    cache/                    # regenerable cache
```

The root of the Component area is reserved for configuration/definition files. Durable non-definition project assets belong under `resources/`. All project-local resources that are runtime state, mutable workspace, temporary work, cache, or otherwise not intended for Git belong under `work.d/`.

This physical layout is a CNCF/Textus local-provider convention. Application/domain code requests logical resources and MUST NOT construct `.textus`, `resources`, or `work.d` paths.

For sm-workflow, local SQLite state and dedicated CNCF/Cozy worktrees therefore map beneath `work.d/`, while project definitions/configuration remain at the Component-area root and any durable version-controlled resources belong beneath `resources/`.

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

The standard layout makes Git policy structural and stable:

- Component-area root definition/configuration files are version-controlled;
- `resources/` is version-controlled;
- `work.d/` is not version-controlled.

A Textus/CNCF project generator MUST generate a standard ignore rule equivalent to:

```gitignore
.textus/*/work.d/
```

This rule is intended to remain valid as new Components and new work-resource kinds are added. Components and runtime providers MUST NOT edit `.gitignore` during normal execution, and MUST NOT add component-specific ignore entries for DataStore files, worktrees, caches, or temporary files. New non-versioned project-local resource kinds belong under `work.d/` so the project-generation-time rule remains sufficient.

Project-specific exceptions, if ever required, are explicit project policy and are not inferred or silently rewritten by CNCF runtime.

## sm-workflow driver scenario

Project X uses project-specific CNCF and Cozy branches. sm-workflow Phase 7 asks CNCF for two named workspaces, materializes/uses Git worktrees there, synchronizes them, and runs dependency-aware validation. sm-workflow never asks for or constructs a physical directory path except through the resource handle/provider boundary.

The same project also binds sm-workflow runtime state through DataStore without knowing the SQLite file location.

This scenario is the acceptance driver, but the resulting API MUST be reusable by other components.

## Executable Specification requirements

Demonstrate at least:

1. a component obtains merged configuration through the existing CNCF configuration mechanism;
2. a component obtains a project-local runtime DataStore without constructing a filesystem path;
3. a component obtains two distinct named workspaces without constructing their directories;
4. the local provider maps version-controlled resources beneath `resources/` and non-versioned runtime/work resources beneath `work.d/` by default;
5. changing provider mapping does not require application/workflow changes;
6. runtime/workspace resources are distinguishable from versionable project configuration/resources;
7. a project-generation-time `.textus/*/work.d/` ignore rule covers newly created Component work areas without runtime `.gitignore` mutation;
8. a non-SQLite DataStore binding remains possible without changing the consumer API;
9. the sm-workflow RepositorySync driver can use the API for dedicated CNCF/Cozy worktrees.

## Closure criteria

CNCF Phase 101 closes only when all Phase 101 executable specifications pass and the sm-workflow Phase 7 driver has demonstrated the real multi-repository workspace/worktree scenario without application-owned physical path construction. Phase 5 minimum-slice acceptance alone is explicitly insufficient for closure.

## Non-goals

- Reimplementing CNCF hierarchical configuration merge.
- Encoding sm-workflow, Git, sbt, CNCF-repository, or Cozy-repository semantics in the generic API.
- Requiring SQLite as the universal persistence provider.
- Exposing physical `.textus` paths as Workflow/domain semantics.
- Adding content hashes, integrity ledgers, rollback copies, or defensive contamination machinery.
