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
precedence, and compatibility behavior. Typed binding authority is specified by
the Phase 55 catalog/binding boundary below; this raw resolver is not that
authority.


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

`ResolvedConfiguration` is retained only as a pre-admission compatibility
projection. CNCF MUST decode the retained per-source snapshot through its
closed catalog and pass component initialization a narrow value-only typed
snapshot. The configuration source resolver defined by this specification MUST
NOT:

    - select a component or component instance
    - interpret a component parameter declaration
    - decode a component-domain value
    - merge assembly metadata into `ResolvedConfiguration`
    - expose its raw map, sources, candidates, aliases, provenance, or trace
      authority to component initialization code

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
10. Standalone Fixed-User Identity Safety
----------------------------------------------------------------------

Canonical Textus HOME and compatibility CNCF HOME `StandaloneUserProfile`
sources are admitted in that order and projected into CNCF typed bindings before
Subsystem binding. A final fixed-user binding history with more than one
distinct id is rejected as structured invalid configuration. The diagnostic
requires explicit operator-owned data migration or an isolated datastore and
does not reveal identities, profile paths, or `local_subject` values.

Resolution never moves data, selects a datastore, writes an identity marker, or
performs automatic migration. A clean isolated datastore with one effective new
identity is admissible. The descriptor `local_subject` is capability wiring,
not a fallback fixed-user identity authority.


----------------------------------------------------------------------
10. Testing Expectations
----------------------------------------------------------------------

Executable specifications must cover:

    - one-load-per-source snapshot behavior and source rank/ordinal precedence
    - typed canonical parameter decoding, target admission, collisions, and
      override history
    - resolved collection and sanitized trace derivation from the same effective
      binding authority
    - empty selected-source-set handling and structural configuration failure

Executable evidence:

- [Phase55ConfigurationBindingContractSpec](../../src/test/scala/org/goldenport/cncf/config/Phase55ConfigurationBindingContractSpec.scala)
- [CncfConfigurationTargetSpec](../../src/test/scala/org/goldenport/cncf/config/CncfConfigurationTargetSpec.scala)
- [CncfConfigurationParameterCatalogSpec](../../src/test/scala/org/goldenport/cncf/config/CncfConfigurationParameterCatalogSpec.scala)
- [CncfConfigurationCandidateDecoderSpec](../../src/test/scala/org/goldenport/cncf/config/CncfConfigurationCandidateDecoderSpec.scala)
- [CncfRuntimeConfigurationProjectionSpec](../../src/test/scala/org/goldenport/cncf/config/CncfRuntimeConfigurationProjectionSpec.scala)
- [CncfConfigurationBindingResolverSpec](../../src/test/scala/org/goldenport/cncf/config/CncfConfigurationBindingResolverSpec.scala)
- [CncfConfigurationBindingTraceSpec](../../src/test/scala/org/goldenport/cncf/config/CncfConfigurationBindingTraceSpec.scala)
- [CncfConfigurationBindingDiagnosticCodecSpec](../../src/test/scala/org/goldenport/cncf/config/CncfConfigurationBindingDiagnosticCodecSpec.scala)

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
12. Phase 55 typed binding boundary
----------------------------------------------------------------------

After one immutable `ConfigurationResolutionSnapshot` is available, CNCF
admission is normative under the following local rule registry. The `R<n>` and
`E<n>` identifiers are local to `config-resolution`; historical `slice:` tags
remain contextual and are not normative identifiers.

### Normative rule registry

#### R1 Closed catalog admission

The closed catalog maps canonical `textus.*` identities to the original generic
parameter witnesses. Registered `textus.runtime.*`, `cncf.*`, and
`cncf.runtime.*` forms are decode-only aliases. An unknown catalog member
presented inside a catalog-owned/consolidated document, malformed values, wrong
target scopes, and canonical-plus-alias collisions fail structurally. At a
generic flat source boundary, unrelated or unregistered keys are filtered or
preserved outside CNCF binding admission rather than being reinterpreted as
catalog members or causing catalog rejection.

#### R2 Semantic targets

The four targets are exactly `Global`, `ComponentClass`, `SubsystemInstance`,
and fully qualified `ComponentInstance`. Unqualified external syntax receives
its target from document location and is not a semantic target.

#### R3 Retained physical batches

Nonempty retained physical sources decode without reload into immutable typed
candidates that preserve source provenance, rank, and ordinal. Candidate
admission rejects duplicate canonical parameter/target identities. Deterministic
resolution selects one most-specific target per source, rejects same-source
ties, folds source winners by rank and ordinal, and yields one effective
collection.

#### R4 Supplemental admission

Already-admitted argv, environment, and profile bindings join the matching
retained source/candidate graph before one resolution. Same-source canonical
collisions fail, while distinct target bindings preserve their provenance.

#### R5 Native typed values

Retained native values remain typed through decoding and resolution and preserve
source precedence and override history without a `String` round-trip.

#### R6 Consolidated hierarchy

Only reserved consolidated file snapshots receive global/subsystem hierarchy
interpretation. Generic non-file flat sources do not gain hierarchy or target
semantics.

#### R7 Equivalent-form collision

Consolidated and canonical split forms in one layer that define the same
canonical parameter/target are structural duplicates, not overrides.

#### R8 Source precedence

Separate conventional Textus and CNCF sources retain normal source-precedence
override semantics.

#### R9 Split path binding

A canonical split document remains bound to its path-selected target;
hierarchy-looking content cannot retarget it.

#### R10 Raw member multiplicity

Retained raw YAML preserves duplicate member evidence, and candidate admission
rejects a duplicate canonical binding without rereading the physical file.

#### R11 Empty selected-source set

After supplemental-source validation, a genuinely empty admitted physical-source
batch yields canonical empty candidates and an empty effective collection. A
nonempty admitted batch retains decoder validation. This rule does not cover
missing or unreadable files.

### Executable example registry

#### E1 resolve catalog candidates from the same single-load runtime snapshots

Rules: R3. The projection resolves baseline and override snapshots without
reloading a physical source and retains the higher-precedence binding's
override history.

#### E2 compose retained runtime and already-admitted HOME profile candidates before final resolution

Rules: R4. The projection composes runtime and admitted HOME profile candidates
before one final resolution while preserving their typed witnesses.

#### E3 accept native boolean runtime values and retain their higher-precedence winner

Rules: R5. Native boolean values decode as typed values and the higher-
precedence source remains the winner with its override history.

#### E4 attach typed argv bindings to the one existing argument source batch

Rules: R4. Typed argv bindings join the retained argument source;
same-target collisions fail and distinct targets retain shared argv provenance.

#### E5 attach typed environment bindings to the one existing environment source batch

Rules: R4. Typed environment bindings join the retained environment source;
same-target collisions fail and distinct targets retain environment provenance.

#### E6 decode one already-loaded consolidated file document into Global and selected Subsystem bindings

Rules: R2, R3, R6. A consolidated file snapshot supplies Global and selected
Subsystem bindings with one-load provenance and reserved hierarchy
interpretation.

#### E7 reserve consolidated hierarchy interpretation for file snapshots

Rules: R1, R6. A non-file resource or ordinary argument key does not acquire
consolidated hierarchy or target semantics.

#### E8 reject same-layer canonical duplicates across consolidated and split file forms

Rules: R7. Consolidated and canonical split forms defining one same-layer
binding are rejected as structural duplicates.

#### E9 retain normal CNCF override semantics beside a canonical Textus consolidated file

Rules: R8. Separate Textus and CNCF consolidated sources retain their normal
precedence, with CNCF overriding Textus.

#### E10 keep a canonical split file path-bound when its content looks consolidated

Rules: R9. A canonical split path retains its selected target even when its
content looks like a foreign hierarchy.

#### E11 reject duplicate canonical bindings from one retained raw YAML split-file document

Rules: R10. Duplicate raw YAML members remain observable and candidate
admission rejects the duplicate without rereading the file.

#### E12 handle an empty runtime source snapshot gracefully at the CNCF projection boundary

Rules: R11. An empty admitted source set yields canonical empty candidates and
an empty effective collection, while missing or unreadable files remain outside
this rule.

- `ResolvedConfiguration` remains only the raw compatibility projection of the
  source snapshot. Components and runtime consumers receive value-only
  projections and no raw sources, candidates, aliases, provenance, or trace
  authority.
- Trace and serialized diagnostics are derived only from the effective
  collection and bounded override chain. Confidential values/history are
  redacted and diagnostics cannot reload or open resources.

The owning CNCF catalog and runtime design define parameter names, aliases,
defaults, and value-only projections. This generic source specification owns
neither those semantics nor source/resource ownership.

----------------------------------------------------------------------
13. Final Note
----------------------------------------------------------------------

This configuration mechanism exists to be:

    - boring
    - reliable
    - invisible

If it becomes interesting,
it is probably doing too much.
