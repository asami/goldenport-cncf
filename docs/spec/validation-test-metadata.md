# Validation/Test Metadata V1

This specification defines declaration admission and normalized operation-level
interpretation. The public API is
[`ValidationMetadataV1`](../../src/main/scala/org/goldenport/cncf/validation/ValidationMetadataV1.scala);
the behavior contract is
[`ValidationMetadataV1Spec`](../../src/test/scala/org/goldenport/cncf/validation/ValidationMetadataV1Spec.scala).
The [design](../design/validation-test-metadata.md) records responsibility boundaries.

## Public declaration and compatibility

`ABI_VERSION` is the integer `1`. `Declaration` carries `abiVersion`, optional
`purposes`, a `features` set, optional `expectedDuration`, optional `durationClass`,
optional `executionRequirement` and a `requiredSemantics` set. All fields have
empty/absent defaults except `abiVersion`, which defaults to `ABI_VERSION`.
`Declaration.validateC` admits the declaration without rewriting its source facts.
`resolveC(specification, operation)` returns `Consequence[Metadata]` and performs
pure deterministic validation and normalization. Both declarations are validated
before composition; an operation override cannot hide an invalid class default.

Only ABI version `1` is supported. Required semantic names are exactly
`purposes`, `features`, `expectedDuration`, `durationClass` and
`executionRequirement`. A required semantic name declares a compatibility need;
it does not require a value for an optional field. Unknown names, including case
variants, fail admission. There is no inferred minor version or silent fallback.

Purpose values are `SMOKE`, `FOCUSED`, `ADMISSION`, `FULL`, `HEAVY`. Each is an
independent membership: no purpose implies another, including `HEAVY`.
Duration classes are `NORMAL` and `LONG_RUNNING`.

`FeatureReference(scope, identity)` preserves exact opaque scoped data. Both
strings must be nonempty, already trimmed, without control characters and at
most 512 characters. `validateC` does not normalize labels or infer relationships
to Components, Slices or Capabilities. Equal identities with different scopes
remain distinct. New scopes and feature identities are data and do not change
the ABI version. Explicit target feature populations remain consumer input;
the feature set declared by tests is not a complete application inventory.

## Operation normalization

`Metadata` carries purpose and feature sets, optional expected duration, a duration
class, an optional existing execution aggregate and `Origins(specification,
operation)`. Origins record declaration presence, including an explicit empty
declaration; they do not supply source/artifact revision provenance.

| Field | Resolution |
| --- | --- |
| `purposes` | Operation `Some(set)` replaces the specification value, including an empty set; `None` inherits; otherwise empty. |
| `features` | Set union of specification and operation references. |
| `expectedDuration` | Operation value when supplied, otherwise specification value, otherwise absent. |
| `durationClass` | Operation value when supplied, otherwise specification value, otherwise `NORMAL`. |
| `executionRequirement` | Validated canonical aggregate inherited or composed as below. |
| `origins` | Presence of the two admitted declaration options. |

`None`/`None` gives empty purposes/features, absent duration/requirement, `NORMAL`
and `Origins(false, false)`. An explicit empty operation declaration inherits
class fields whose values it does not supply. Only `purposes = Some(Set.empty)`
clears inherited purpose membership; empty feature sets do not clear features.

Expected duration is a `scala.concurrent.duration.FiniteDuration`, strictly
positive and representable as a signed `Long` count of nanoseconds. Resolution
returns a nanosecond-unit `FiniteDuration`, with no saturation or rounding.
Zero and negative values fail. The interpreter checks representability using
exact arithmetic. Duration class and duration override independently.
Scala `FiniteDuration` construction excludes values outside its nanosecond range;
such values are outside this typed ABI's constructible input space. The
Executable Specification exercises `Long.MaxValue` nanoseconds and positive
unit normalization, and rejects constructible zero/negative durations. An
upstream constructor exception is not a CNCF domain rejection.
Duration describes each resolved operation. A class default inherited by several
operations does not become aggregate class/query time. Measured runtime,
queue/setup/body boundaries, attempts, timeout and observations belong to
sm-workflow evidence and are absent from this declaration model.

## Existing execution requirement composition

The singular `executionRequirement` is
`WorkflowProtocolV1.ExecutionRequirement`, an existing aggregate of capability,
risk, reasoning, review and optional execution facts. It is not a second
requirement model or a provider selector. Its existing `validateC` is reused.
Capabilities are canonicalized by exact identity, distinct and sorted.

An absent requirement inherits the other validated canonical requirement. When
both declarations supply requirements:

- Capabilities form a distinct identity-sorted union.
- Reasoning takes the strongest existing value in order `Routine`, `Standard`,
  `Deep`, `Critical`; review is the Boolean OR of `reviewRequired`.
- Opaque `risk` values must equal. No risk ordering is invented.
- Optional `disposition` and `reasoningClass` inherit when only one is present.
  When both are present they must equal in full, including disposition origin.
- An incompatible risk, disposition or reasoning class fails interpretation;
  operation metadata cannot weaken the specification requirement.

## Admission failures

Explicit nulls are malformed values, never absence: boundary options, `Some`
members, declaration sets, set elements, enum members, durations, feature
references and existing execution requirement facts are admitted without leaking
`NullPointerException` or `MatchError`. Optional absent values are valid.
Independent validation uses Consequence's applicative facilities so both
declarations are checked before any merge.

Known failures use `Consequence.argumentPolicyViolation` with the relevant
parameter/field and a stable policy identifier:

| Policy suffix under `validation.metadata.` | Meaning |
| --- | --- |
| `null` | Malformed option or explicit null option member. |
| `declaration` | Null declaration at internal declaration admission. |
| `abi` | Unsupported `abiVersion`. |
| `required-semantics` | Null required-semantic set or unsupported required name. |
| `purposes` | Null purpose member. |
| `feature` | Null feature set/reference or invalid scoped feature text. |
| `duration` | Nonpositive or unrepresentable nanosecond duration. |
| `execution-requirement` | Existing requirement validator rejects the supplied aggregate. |
| `execution-requirement.risk` | Incompatible opaque risk values. |
| `execution-requirement.disposition` | Incompatible optional disposition values. |
| `execution-requirement.reasoningClass` | Incompatible optional reasoning classes. |

Failure diagnostics are structured domain outcomes, with no string-only error
envelope, thrown declaration-validation exception or extension of the core API.

## Phase boundary

Step P103-S1 supplies only the callable declaration model and interpretation
contract. It supplies no annotation syntax, framework adapter, discovery,
operation inventory or invocation support. Scoped executable identities,
source/artifact revisions and provenance, COMPLETE/PARTIAL/UNKNOWN enumeration,
unannotated-operation visibility, real scenario discovery, parameterized
invocation, CML mapping and concrete runtime/static consumer fixtures remain
[Phase 103](../phase/phase-103.md) obligations. Origins alone does not satisfy them.

No pass/fail, actual runtime, Candidate state, hashes, executor arguments, KPI,
suite registry or sm-workflow time-budget policy belongs to this ABI.
