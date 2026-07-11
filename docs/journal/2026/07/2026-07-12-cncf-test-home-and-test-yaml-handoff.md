# Handoff: CNCF Test Home and `test.yaml` Runtime Isolation

## Context

ArtScene Web smoke tests currently protect the user's local datastore by replacing the JVM home:

```text
-Duser.home=target/cncf.d/<stage>-home
```

This keeps the test away from `~/.cncf/art-scene/application.db`, but it also hides the normal CNCF home, component repository, assembly configuration, and dependency CARs.

The Stage 3D timeline smoke exposed the resulting failure:

```text
assembly component dependency not resolved:
  textus-ai-runtime:0.2.0-SNAPSHOT
  textus-scraper:0.1.0-SNAPSHOT
```

The timeline JavaScript harness passed. The server failure was caused by whole-home replacement, not by the ArtScene timeline behavior.

## Existing Direction

CNCF already has a `test.yaml` mechanism for test-specific runtime composition. It should be the standard way to replace only test-owned resources while retaining the normal assembly and component resolution path.

Typical `test.yaml` responsibilities are:

- replace one component's application datastore;
- replace the runtime datastore when necessary;
- install test or stub SPI providers;
- provide test-only component config and rules;
- preserve the normal runtime, assembly, and dependency repository unless explicitly isolated.

ArtScene smoke scripts have not yet migrated to this mechanism.

## Two Required Test Modes

### 1. Runtime Overlay Mode

Use the normal CNCF home and assembly, but apply `test.yaml` overrides.

This is appropriate for most adapter and Web smoke tests:

- normal CNCF runtime and CAR repository;
- normal `textus-ai-runtime` and `textus-scraper` assembly dependencies;
- target-owned ArtScene datastore;
- optional stub AI/SPI provider;
- no changes to the user's application data.

Conceptual configuration:

```yaml
components:
  art-scene:
    datastore:
      application:
        type: local
        path: target/cncf.d/stage3d/application.db

runtime:
  datastore:
    path: target/cncf.d/stage3d/runtime.db
```

The final key names must follow the CNCF `test.yaml` specification. CAR-specific datastore override properties should not be introduced.

### 2. Isolated CNCF Test Home Mode

Some tests need complete CNCF home isolation. CNCF should provide this as an explicit runtime/launcher feature instead of requiring shell scripts to mutate `user.home`.

Suggested command surfaces:

```text
cncf test --test-config test.yaml server
cncf test --home target/cncf.d/stage3d-home server
cncf test --temporary-home server
```

A test home should support controlled inheritance:

```yaml
home:
  mode: isolated
  path: target/cncf.d/stage3d-home
  inherit:
    runtime: true
    repositories: true
    credentials: false
    local-data: false
```

This is an overlay model, not a JVM `user.home` replacement:

```text
Base CNCF home
  runtime catalog
  CAR repositories
  shared configuration
          |
          | read-only inheritance
          v
Test CNCF home
  test configuration
  stub components
  test datastore
  test cache
```

## Required CNCF Work

1. Confirm and document how `test.yaml` is supplied through the CNCF launcher and runtime.
2. Define component-instance datastore override syntax in `test.yaml`.
3. Ensure test datastore binding works independently of SQLite so the logical datastore contract remains portable.
4. Add explicit CNCF test-home support with a target-owned or temporary home.
5. Separate these inheritance domains:
   - runtime selection;
   - component/CAR repositories;
   - assembly descriptors;
   - runtime and component configuration;
   - credentials;
   - application and runtime datastores;
   - caches and generated artifacts.
6. Make the test home writable while inherited base repositories/configuration remain read-only.
7. Ensure assembly dependencies are resolved normally in both overlay and isolated modes.
8. Provide cleanup/retention behavior for temporary test homes.

## ArtScene Follow-up

After CNCF behavior is fixed and documented, migrate ArtScene smoke scripts that currently set `-Duser.home`.

Initial target:

```text
scripts/check-stage3d-timeline-visualization.sh
```

Then migrate other adapter/Web smoke scripts incrementally.

The Stage 3D timeline implementation itself has already been verified in the normal runtime:

- timeline window is one month before through three months after the selected date;
- current exhibitions are ordered by nearest end date;
- upcoming exhibitions are shown concurrently and ordered by nearest start date;
- recently ended exhibitions remain visible inside the window;
- the live ArtScene page rendered 75 timeline records using the shared application datastore.

## Acceptance Criteria

- A smoke test can replace only ArtScene's datastore through `test.yaml`.
- The test does not modify `~/.cncf/art-scene/application.db`.
- Normal assembly dependencies such as `textus-ai-runtime` and `textus-scraper` remain resolvable.
- Stub SPI providers can be installed through `test.yaml` without application-side wiring.
- Fully isolated tests can use a CNCF-managed test home without changing JVM `user.home`.
- Test-home inheritance is explicit and does not expose credentials or local application data by default.
- Test artifacts remain under `target/` or another explicitly selected test directory.
- ArtScene Stage 3D packaged Web/server smoke completes through the standard CNCF route.

## Constraints

- Do not standardize `-Duser.home` as the CNCF test-isolation mechanism.
- Do not manually copy dependency CARs into each smoke-test home.
- Do not bypass assembly with ad hoc `--component-file` or component-directory wiring.
- Do not add ArtScene-specific datastore selection properties.
- Keep datastore-only overlay and fully isolated test-home modes distinct.
