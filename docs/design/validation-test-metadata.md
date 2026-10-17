# Validation/Test Metadata Interpretation

The [V1 specification](../spec/validation-test-metadata.md) promotes declaration
semantics from the [planning note](../notes/validation-test-metadata-abi-proposal.md).
The note remains history; the spec and Executable Specification define behavior.

## Stable public data and a pure interpreter

`org.goldenport.cncf.validation.ValidationMetadataV1` owns instance-independent
types for declarations, scoped feature references, normalized metadata and
declaration origins. Purpose and duration class are closed enums; feature
references remain extensible data. The public resolver delegates to the
package-private `ValidationMetadataResolution`. Neither layer uses discovery,
runtime state, providers or ScalaTest production dependencies.

The interpreter first admits both declarations with applicative Consequence
composition. It normalizes duration and capabilities into temporary validated
values before merging. Public declaration validation preserves source values;
resolution returns canonical values and does not mutate inputs. Unsupported or
malformed defaults remain failures even when an operation supplies overrides.

Purpose replacement is deliberately distinct from feature union. An optional
purpose set represents inheritance versus an explicit replacement, including
empty replacement. Origins preserves absent versus explicit empty declarations.
Duration and duration class remain independent fields. Exact nanosecond
arithmetic prevents truncation/saturation in normalization.

## Reuse the execution aggregate

The singular execution requirement is the existing
`WorkflowProtocolV1.ExecutionRequirement`, validated through its actual API.
Metadata adds a composition binding, not another provider or execution model.
Capability union, strongest existing reasoning and review OR preserve both
declarations' requirements. Risk is opaque and equality-constrained. Optional
disposition and reasoning class inherit or must agree. Structured policy
failures reject incompatible aggregates without weakening class requirements.

## Producer and consumer responsibilities

CNCF owns normalized declaration semantics and the later stable discovery and
typed operation binding contract. sm-workflow owns purpose/feature selection,
Slice validation plans, execution policy and observations. textus-cbd-support
owns static analysis, coverage comparison and KPI presentation. Cozy owns CML
syntax and generation; equivalent normalized mapping fixtures belong to the
producer handoff, without making generated Scala an independent authority.

Feature scope and identity are exact declarations. Neither labels nor observed
test features supply inferred application mappings or a target feature inventory.
Declared duration is operation-level data. Runtime observation scopes and their
comparability are separate consumer evidence.

## Remaining connected delivery

P103-S1 implements declaration interpretation only. It does not install
annotations, discover classes/scenarios, enumerate artifacts or invoke tests.
Declaration origins are presence facts, not revision provenance or inventory
completeness. Stable scoped operation identity, source/revision references,
COMPLETE/PARTIAL/UNKNOWN enumeration, unannotated and invalid entries, real
scenario invocation, CML mapping and concrete consumer fixtures remain Phase
103 work. The [phase checklist](../phase/phase-103-checklist.md) owns delivery
status; this design does not claim Phase acceptance.
