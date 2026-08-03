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

Source discovery belongs to runtime source assembly. The generic resolver
receives ordered `org.goldenport.configuration.source.ConfigurationSource`
instances rather than discovering them itself.

It does NOT:

    - define application-specific semantics
    - validate domain rules
    - construct execution or transport context
    - communicate with remote services

Important principle:

config resolution != config semantics

The authoritative runtime path is `org.goldenport.configuration.*`:

    - `ConfigurationResolver.resolveSnapshot` loads each selected physical
      source once and returns an immutable `ConfigurationResolutionSnapshot`.
    - `ResolvedConfiguration` is the compatibility map projection of that
      snapshot, not a second resolution authority.
    - CNCF decodes retained per-source values into typed configuration binding
      candidates, then resolves those candidates for their declared targets.
    - The primary product namespace is `textus`; `cncf` is admitted only as an
      explicitly registered compatibility spelling.


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

For a given HOME / PROJECT / CWD scope, primary `.textus` files are assembled
before compatibility `.cncf` files. Because later sources overwrite earlier
values, `.cncf` is the established compatibility override in the current
runtime.

This means:

    .textus/config.yaml    primary baseline
    .cncf/config.yaml      compatibility override

If both define the same key, `.cncf` wins. This precedence is compatibility
behavior, not a recommendation for new configuration spellings.


----------------------------------------------------------------------
4. Project Root Resolution
----------------------------------------------------------------------

The runtime source assembler uses
`org.goldenport.configuration.source.ProjectRootFinder` to determine the
project root before constructing Project and CWD source entries. Textus uses
`textus` as its primary application directory and evaluates `cncf` only as the
documented compatibility application directory.

The source assembler records origins and ranks; the generic resolver does not
guess roots or mutate discovery decisions while resolving values.

4.1 Design Rationale

    - keep discovery explicit and reproducible
    - make project-root policy independent from resolution and binding semantics
    - retain the established compatibility-directory override order without
      treating it as a new canonical vocabulary

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

Within HOME, PROJECT, and CWD, `.cncf` compatibility sources are assembled
after `.textus` primary sources and therefore override duplicate values.

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

6.5 Component Initialization Projection Boundary

`ResolvedConfiguration` MAY be supplied as one explicit input to CNCF
component initialization parameter resolution. The configuration source
resolver defined by this specification MUST NOT:

    - select a component or component instance
    - interpret a component parameter declaration
    - decode a component-domain value
    - merge assembly metadata into `ResolvedConfiguration`
    - expose its raw map to component initialization code

Assembly, subsystem component-instance settings, packaged defaults, and
explicit test overlays remain separate admitted inputs to the higher-level
initialization resolver. Their parameter precedence MUST NOT alter or
reinterpret the source precedence defined in Section 5.

Component initialization parameter resolution is specified by
`docs/spec/component-runtime-boundary-capabilities.md`. This source resolver
remains responsible only for the deterministic raw runtime configuration
result.


----------------------------------------------------------------------
7. Public API Contract
----------------------------------------------------------------------

Consumers interact with the generic source and snapshot APIs.

    trait ConfigurationResolver {
      def resolve(
        sources: Seq[ConfigurationSource]
      ): Consequence[ResolvedConfiguration]

      def resolveSnapshot(
        sources: Seq[ConfigurationSource]
      ): Consequence[ConfigurationResolutionSnapshot]
    }

The runtime may make a preliminary generic resolution pass to locate a test
descriptor. It then assembles the final ordered source list and invokes
`ConfigurationResolver.resolveSnapshot` once for that final list. CNCF
configuration admission decodes the final snapshot's retained source values
through the closed catalog into typed `ConfigurationBindingCandidate` values.
The target-aware `ConfigurationBindingResolver` is the sole authority for a
final typed binding collection. Components receive resolved values or narrow
capabilities, never raw source maps, aliases, candidates, or trace authority.

API Principles:

    - no global mutable state in resolver
    - caller supplies already selected sources explicitly
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

    - call the current generic configuration API
      (`org.goldenport.configuration.*`)
    - perform target-specific CNCF admission through the closed parameter
      catalog and typed binding resolver

Consumers must not:

    - bypass discovery logic
    - re-implement merge semantics
    - create a second local resolver, map merge, source discovery, or trace
      authority

This layer is not application-specific.


----------------------------------------------------------------------
10. Testing Expectations
----------------------------------------------------------------------

Executable specifications must cover:

    - one-load-per-source snapshot behavior and source rank/ordinal precedence
    - typed canonical parameter decoding, target admission, collisions, and
      override history
    - resolved collection and sanitized trace derivation from the same effective
      binding authority
    - empty/missing source handling and structural configuration failure

Tests must avoid:

    - network dependencies
    - SIE-specific assumptions
    - persistent filesystem coupling

Temporary test fixtures must be target-owned and cleaned after use.


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
