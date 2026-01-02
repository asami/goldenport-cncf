# Configuration Resolution
Local / Runtime Configuration Resolution

This document defines the foundational configuration resolution mechanism
provided by the Cloud-Native Component Framework.

This mechanism is intentionally generic, deterministic, and boring.
Its purpose is to prevent accidental coupling between components,
applications, and runtime environments.

This document is normative.


----------------------------------------------------------------------
1. Scope and Responsibility
----------------------------------------------------------------------

This layer is responsible for configuration resolution only.

It provides:

    - discovery of configuration sources
    - deterministic precedence handling
    - deterministic merge semantics
    - a single evaluated configuration result

It does NOT:

    - define application-specific semantics
    - validate domain rules
    - construct execution or transport context
    - communicate with remote services

Important principle:

    config resolution != config semantics


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
        $HOME/.sie/

    - PROJECT configuration
        ${PROJECT_ROOT}/.sie/

    - CWD configuration
        ${CWD}/.sie/

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

    - a directory containing .sie/
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

Consumers interact with this mechanism through a single entry point.

    object ConfigResolver {
        def resolve(
            cwd: Path,
            args: Map[String, String] = Map.empty,
            env: Map[String, String] = sys.env
        ): Either[ConfigError, ResolvedConfig]
    }

API Principles:

    - no global state
    - caller explicitly supplies cwd and env
    - safe for Docker, CI, tests, and embedded usage
    - errors are explicit and typed


----------------------------------------------------------------------
8. Error Handling
----------------------------------------------------------------------

Configuration resolution may fail due to:

    - invalid file format
    - unreadable configuration files
    - ambiguous project root resolution

Errors are returned as ConfigError.

This layer must not:

    - throw unchecked exceptions
    - terminate the process
    - log application-level messages


----------------------------------------------------------------------
9. Relationship to Consumers (e.g. SIE)
----------------------------------------------------------------------

Consumers (such as Semantic Integration Engine) must:

    - call ConfigResolver
    - receive ResolvedConfig
    - interpret configuration via thin adapters

Consumers must not:

    - bypass discovery logic
    - re-implement merge semantics
    - depend on internal classes of this layer

This layer is not SIE-specific.


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
