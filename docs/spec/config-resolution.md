# Configuration Resolution
Local / Runtime Configuration Resolution

This document defines the Textus runtime configuration resolution mechanism.
Current production path is `org.goldenport.configuration.*`.

The product-facing name is CozyTextus. Runtime configuration therefore uses
`textus` as the primary namespace and directory name. Existing `cncf` names are
compatibility aliases and remain supported where explicitly described.

This mechanism is intentionally generic, deterministic, and boring.
Its purpose is to prevent accidental coupling between components,
applications, and runtime environments.

This document is normative for runtime configuration source discovery,
precedence, and compatibility behavior.


----------------------------------------------------------------------
1. Scope and Responsibility
----------------------------------------------------------------------

This layer is responsible for configuration resolution only.

It provides:

    - deterministic precedence handling for provided sources
    - deterministic merge semantics
    - a single evaluated configuration result

Source discovery belongs to source assembly (`ConfigSources.standard`)
rather than resolver execution.

It does NOT:

    - define application-specific semantics
    - validate domain rules
    - construct execution or transport context
    - communicate with remote services

Important principle:

config resolution != config semantics

Status note:

    - `org.goldenport.cncf.config.ConfigResolver` is deprecated (Phase 2.8).
    - Runtime uses `org.goldenport.configuration.ConfigurationResolver`.
    - The primary product namespace is `textus`.
    - `cncf` remains as a compatibility namespace.


----------------------------------------------------------------------
2. Design Goals
----------------------------------------------------------------------

The configuration resolution mechanism must be:

    - reusable across components and applications
    - environment-agnostic (local / container / cloud)
    - deterministic (same inputs -> same result)
    - explicit (no hidden globals)
    - testable without network or external dependencies

This layer prioritizes predictability over convenience.


----------------------------------------------------------------------
3. Configuration Sources
----------------------------------------------------------------------

Configuration values are discovered from the following sources.

3.1 Supported Sources

    - HOME configuration
        $HOME/.textus/
        $HOME/.cncf/        (compatibility)

    - PROJECT configuration
        ${PROJECT_ROOT}/.textus/
        ${PROJECT_ROOT}/.cncf/   (compatibility)

    - CWD configuration
        ${CWD}/.textus/
        ${CWD}/.cncf/      (compatibility)

    - Environment variables

    - Explicit arguments provided by the caller

All sources are optional.
Absence of configuration must be handled gracefully.

3.2 Standard File Names

Within each configuration directory, standard files are evaluated in this order:

    1. config.conf
    2. config.props
    3. config.properties
    4. config.json
    5. config.yaml
    6. config.xml

Later files in the same directory overwrite earlier files by key.
`props` and `properties` are treated as `conf`-compatible inputs.

YAML and XML object structures also expose dot-path keys for runtime lookup.
For example:

    textus:
      web:
        descriptor: config/web-descriptor.yaml

must be available as:

    textus.web.descriptor

3.3 Primary and Compatibility Directories

For a given HOME / PROJECT / CWD scope, compatibility `.cncf` files are loaded
before primary `.textus` files.

This means:

    .cncf/config.yaml      compatibility fallback
    .textus/config.yaml    primary value

If both define the same key, `.textus` wins.


----------------------------------------------------------------------
4. Project Root Resolution (Legacy Effective Behavior)
----------------------------------------------------------------------

The deprecated CNCF-local resolver does not perform upward project-root
detection.

4.1 Effective Rule (as implemented)

`org.goldenport.cncf.config.ConfigSource.project(cwd)` and
`ConfigSource.cwd(cwd)` both resolve to:

    ${CWD}/.cncf/config.conf

No `.git` or parent-directory probing is executed in this legacy path.
Project source and CWD source are distinct origins with the same location.

The current runtime path uses `org.goldenport.configuration.ProjectRootFinder`.
For Textus runtime configuration, project discovery uses the primary
application name `textus` and keeps `cncf` as a compatibility application name.

4.2 Design Rationale

    - keep legacy behavior deterministic and backward-compatible
    - avoid introducing new root-discovery semantics in deprecated layer
    - move richer discovery policy to current configuration package

This mechanism must never guess.


----------------------------------------------------------------------
5. Precedence Order
----------------------------------------------------------------------

Configuration sources are merged using a fixed precedence order.

From weakest to strongest:

    1. HOME
    2. PROJECT
    3. CWD
    4. ENV
    5. ARGS

Later sources overwrite earlier ones.

This order is foundational and must remain stable.

Within HOME, PROJECT, and CWD, `.cncf` compatibility sources are weaker than
`.textus` primary sources.

Explicit config file arguments use the following compatibility rule:

    --cncf.config.file(s)      compatibility
    --textus.config.file(s)    primary

When both are present, the Textus arguments are loaded after CNCF arguments and
therefore win on duplicate keys.


----------------------------------------------------------------------
6. Merge Semantics
----------------------------------------------------------------------

6.1 Key-Based Overwrite

    - configuration values are merged by key
    - later values overwrite earlier values

6.2 Arrays

    - arrays are replaced, not merged
    - partial array merging is explicitly unsupported

6.3 Explicit Null / Disabled Values

    - explicit null or disabled values overwrite previous values
    - allows higher-precedence sources to actively disable settings

6.4 Unknown Keys

    - unknown keys are preserved as-is
    - this layer does not enforce schema validation

Schema validation belongs to higher layers.


----------------------------------------------------------------------
7. Public API Contract
----------------------------------------------------------------------

Consumers interact with this mechanism through source-based entry points.

    trait ConfigResolver {
      def resolve(
        sources: Seq[ConfigSource]
      ): Consequence[ResolvedConfig]

      def resolve(
        sources: ConfigSources
      ): Consequence[ResolvedConfig]
    }

Typical source assembly:

    ConfigSources.standard(
      cwd = cwd,
      args = args,
      env = env
    )

API Principles:

    - no global mutable state in resolver
    - caller supplies sources explicitly (or via `ConfigSources.standard`)
    - safe for Docker, CI, tests, and embedded usage
    - errors are explicit (`Consequence.Failure`)


----------------------------------------------------------------------
8. Error Handling
----------------------------------------------------------------------

Configuration resolution may fail due to:

    - invalid file format
    - unreadable configuration files
    - ambiguous project root resolution

Errors are returned as `Consequence.Failure` with conclusion.

This layer must not:

    - throw unchecked exceptions
    - terminate the process
    - log application-level messages


----------------------------------------------------------------------
9. Relationship to Consumers (e.g. SIE)
----------------------------------------------------------------------

Consumers must:

    - call the current configuration API (`org.goldenport.configuration.*`)
    - treat this legacy resolver as compatibility-only

Consumers must not:

    - bypass discovery logic
    - re-implement merge semantics
    - depend on internal classes of this layer

This layer is not application-specific.


----------------------------------------------------------------------
10. Testing Expectations
----------------------------------------------------------------------

Unit tests should cover:

    - fixed-path project/cwd source behavior
    - precedence ordering
    - merge behavior
    - empty and missing configuration handling

Tests must avoid:

    - network dependencies
    - SIE-specific assumptions
    - persistent filesystem coupling

Temporary directories are acceptable.

Legacy executable-check note (2026-03-21):

    - `src/test/scala/org/goldenport/cncf/config/source/**` remains pending-only.
    - Owner: cncf-runtime
    - TODO: replace pending specs with concrete checks for fixed-path
      `ConfigSource.project(cwd)` / `ConfigSource.cwd(cwd)` behavior.


----------------------------------------------------------------------
11. What Is Intentionally Deferred
----------------------------------------------------------------------

This document does not define:

    - configuration schema
    - execution context construction
    - transport-level propagation
    - MCP / REST integration

Those concerns belong to higher layers.


----------------------------------------------------------------------
12. Final Note
----------------------------------------------------------------------

This configuration mechanism exists to be:

    - boring
    - reliable
    - invisible

If it becomes interesting,
it is probably doing too much.
