CNCF Test Vocabulary
====================

Status: approved design decision

This document records why CNCF exposes executable-spec matcher vocabulary from
the main `goldenport-cncf` artifact.


Decision
--------

CNCF keeps shared executable-spec vocabulary, such as
`org.goldenport.cncf.test.CncfSpecVocabulary`, in `src/main`.

The package is intended for downstream component test suites. Production code
must not depend on `org.goldenport.cncf.test`.


Rationale
---------

Downstream CNCF component projects need common matcher vocabulary for generated
operation definitions, MCP payloads, Records, JSON responses, and other CNCF
contracts. If the vocabulary lived in CNCF `src/test`, it would not be published
and could not be reused by those projects.

A separate `goldenport-cncf-testkit` artifact would make the dependency
boundary cleaner, but it would also add another published library and another
version/repository coordination point. For now, CNCF chooses the simpler
single-artifact rule: the vocabulary is published from `goldenport-cncf`
itself, under an explicit `.test` package.


Constraints
-----------

- The vocabulary must stay small and focused on executable specification
  readability.
- The vocabulary may depend on ScalaTest matcher types because its only
  supported consumers are test suites.
- CNCF runtime and production code must not import `org.goldenport.cncf.test`.
- If the vocabulary grows into a larger testing framework, revisit a dedicated
  testkit artifact.


Consequences
------------

`goldenport-cncf` exposes a small test-support API from its main artifact. This
is intentional and preferable to adding another dependency library while the
shared vocabulary remains lightweight.


Test Descriptor
---------------

CNCF component integration tests may also use an explicit test descriptor file
such as `test.yaml` or `test.json`. This is a startup configuration surface, not
an automatically discovered file. Tests should pass it explicitly, for example:

```bash
cncf dev command ... --textus.test.descriptor=./test.yaml
```

The intended descriptor shape combines runtime test parameters and assembly
overrides:

```yaml
kind: test-descriptor

config:
  textus.some.runtime.key: value

runtime:
  datastore:
    type: local
    path: target/cncf.d/runtime.db

components:
  target-component:
    datastore:
      application:
        type: local
        path: target/cncf.d/target-component/application.db

assembly:
  spi:
    bindings:
      - socket:
          component: target-component
          contract: ai-runner
        provider:
          component: target-component
```

The first target use case is selecting a test SPI provider that is already
packaged in the CAR under test. This keeps the production CAR shape intact and
avoids creating a separate test CAR only to change provider wiring.

The descriptor must not be treated as a way to add Scala traits, JVM methods,
or compiled component APIs at runtime. It is a test-only runtime overlay for
configuration, assembly wiring, and provider selection.

The `config` block remains the canonical escape hatch for runtime properties.
The `runtime.datastore` and `components.<component>.datastore` blocks are
standard shorthand for test-owned datastore replacement. They are normalized to
ordinary runtime configuration before `RuntimeConfig` is created. Tests should
prefer the logical `type: local` and `path` keys rather than SQLite-specific
property names. The current local implementation may use SQLite internally,
but the descriptor contract is a CNCF datastore contract.

For direct CNCF executable specifications that only need resource-read
replacement, `ResourceAccessTestProfile` is the narrower test vocabulary. It
installs deterministic in-memory URL, Textus URN, and external URN providers on
an `ExecutionContext`; it is not an assembly descriptor and does not add
provider classes to a CAR/runtime.

Provider matching currently uses provider component plus the ordinary SPI
contract and `provider` / `mode` / `engine` selection. `provider.service` is
reserved for future service-level matching and is rejected when specified.


Test Home
---------

Most integration tests should use runtime overlay mode: keep the normal CNCF
home and component repositories, pass an explicit test descriptor, and replace
only test-owned resources such as the target component datastore.

Some tests require a separate CNCF home. CNCF supports this as an explicit
test runtime surface rather than by changing the JVM `user.home` property:

```bash
cncf test --test-config test.yaml server
cncf test --home target/cncf.d/stage-home server
cncf test --temporary-home server
```

The `test` wrapper is not a runtime mode. It is normalized before runtime
dispatch into ordinary `server`, `client`, `command`, or `script` execution
with test-only configuration keys. `--test-config` maps to
`textus.test.descriptor`. `--home` maps to an isolated test home path.
`--temporary-home` creates a target-owned temporary home below `target/cncf.d`.

The test descriptor may also declare home behavior:

```yaml
kind: test-descriptor

home:
  mode: isolated
  path: target/cncf.d/stage-home
  inherit:
    runtime: true
    repositories: true
    credentials: false
    local-data: false
```

When a test home is active, CNCF reads test-home configuration files from the
test home as an overlay. By default, runtime configuration and component
repositories are inherited, while local application data is not inherited.
`textus.local-data.root` is redirected to the test home unless
`inherit.local-data: true` is specified. This keeps tests away from the user's
ordinary component application datastores while still allowing normal assembly
dependencies to resolve.

`inherit.repositories: false` disables default repository inheritance. Tests
that choose this mode must provide explicit repository or component activation
arguments. Credentials are not a separate runtime source yet; tests that need
strict credential isolation should avoid inheriting base runtime configuration
and provide only explicit test configuration.
