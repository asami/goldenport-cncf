# Configuration Resolution
Local / Runtime Configuration Resolution (Legacy CNCF ConfigResolver)

This document defines the legacy CNCF-local configuration resolution mechanism.
Current production path is `org.goldenport.configuration.*`.

This mechanism is intentionally generic, deterministic, and boring.
Its purpose is to prevent accidental coupling between components,
applications, and runtime environments.

This document is normative for the legacy CNCF resolver only.


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

3.1 Supported Sources (legacy resolver)

    - HOME configuration
        $HOME/.cncf/

    - PROJECT configuration
        ${PROJECT_ROOT}/.cncf/

    - CWD configuration
        ${CWD}/.cncf/

    - Environment variables

    - Explicit arguments provided by the caller

All sources are optional.
Absence of configuration must be handled gracefully.


----------------------------------------------------------------------
4. Project Root Resolution
----------------------------------------------------------------------

Project root resolution is part of this framework.

4.1 Detection Rules (v0)

Starting from the provided cwd, search upward for:

    - a directory containing .cncf/
    - OR a directory containing .git/

The first directory that matches is considered the project root.

If no directory matches:

    - project root is considered absent
    - project-level configuration is skipped

4.2 Design Rationale

    - keep the rule simple and predictable
    - avoid tool- or application-specific heuristics
    - allow higher layers to override via explicit options

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

    - project root detection
    - precedence ordering
    - merge behavior
    - empty and missing configuration handling

Tests must avoid:

    - network dependencies
    - SIE-specific assumptions
    - persistent filesystem coupling

Temporary directories are acceptable.


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
