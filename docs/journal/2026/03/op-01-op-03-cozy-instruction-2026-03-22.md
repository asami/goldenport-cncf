# Phase 8 Cozy-Led Instruction (OP-01 to OP-04 Slice)

status=ready
published_at=2026-03-22
owner=cozy-led-cross-repo

## Goal

Implement Phase 8 work in the Cozy development thread, including CNCF-side integration changes.

This is a Cozy-led cross-repository instruction.

## Background

Phase 8 defines first-class `operation` in CML with these constraints:

- Canonical form: `operation xxx(input: SomeType): ResultType`
- Convenience form: `operation xxx(arg1: T1, arg2: T2): ResultType`
- Both forms must normalize to canonical single-input model.
- Input value typing is explicit as `Command` or `Query`.
- Validation must enforce:
  - operation kind (`command` / `query`) and input value type match
  - dual definition (`input + parameter`) field consistency

Design basis:

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-arg-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-input-command-query-value-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8-checklist.md`

## In-Scope (Cozy-Led Cross-Repo)

1. CML parsing/modeling acceptance on Cozy route
- Accept `operation` declarations defined in OP-01 contract.
- Preserve operation kind (`command` / `query`) and input/output declarations.

2. Canonicalization at generator boundary
- Normalize convenience parameter form to canonical single-input representation.
- Preserve deterministic field ordering in normalized input model.

3. Metadata emission
- Emit operation metadata from Cozy modeler into downstream model structures.
- Include enough metadata for SimpleModeler/CNCF to keep:
  - operation name
  - operation kind
  - normalized input type/value kind
  - output shape reference

4. Validation (Cozy-owned checks)
- Reject incompatible definitions:
  - `command` operation with `Query` input value type
  - `query` operation with `Command` input value type
- Reject inconsistent dual declarations (`input + parameter` mismatch).

5. Regression coverage (Cozy tests)
- Add/update generation tests that prove:
  - canonical form is emitted correctly
  - convenience form normalizes identically
  - invalid combinations fail deterministically

6. SimpleModeler propagation
- Propagate operation metadata through model transformation layers.
- Preserve normalized input/value-kind semantics into generated Scala model.
- Keep deterministic ordering and stable output contract.

7. CNCF integration (implemented from Cozy-led thread)
- Add/extend CNCF metadata hook to receive operation definitions.
- Wire operation metadata into runtime/meta/projection visibility path.
- Align operation kind to runtime execution baseline:
  - `Command`: Job async default path
  - `Query`: sync read path (or Ephemeral Job path where applicable)
- Keep existing architecture boundaries and do not introduce parallel public APIs.

## Out of Scope

- Final projection/help/openapi integration.
- Non-operation CML grammar redesign.

## Suggested File Targets

- `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/Modeler.scala`
- `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`
- Related CML parser/model files in Cozy repo if operation grammar hooks are hosted there.
- `/Users/asami/src/dev2025/simple-modeler/src/main/scala/...` (operation model/transformer/generator path)
- `/Users/asami/src/dev2025/simple-modeler/src/test/scala/...` (operation propagation specs)
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/...` (operation metadata/runtime integration)
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/...` (runtime/meta/projection specs)

## Required Deliverables

1. Cozy implementation for operation parsing/normalization/emission path.
2. SimpleModeler propagation implementation for operation metadata.
3. CNCF runtime/meta/projection integration for generated operation metadata.
4. Tests demonstrating canonical + convenience equivalence and validation failures.
5. Cross-repo execution report including:
- changed files
- normalization rules implemented
- accepted/rejected examples
- exact test commands and pass results
- compatibility notes and remaining risks

## Validation

Run focused tests per repository, then run impacted CNCF suite.

Example:

```bash
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"
sbt --batch "testOnly org.simplemodeling.*Operation*"
sbt --batch "testOnly org.goldenport.cncf.*Operation* org.goldenport.cncf.*meta*"
```

Add concrete spec class names if wildcard selection is noisy.

## Definition of Done (Cozy-Led Cross-Repo Slice)

This instruction is complete when:

1. Cozy accepts OP-01 operation grammar contract.
2. Convenience form normalizes to canonical single-input form.
3. Command/Query value typing mismatch is rejected deterministically.
4. Dual definition inconsistency is rejected deterministically.
5. SimpleModeler propagates operation metadata without semantic loss.
6. CNCF receives metadata and exposes operation runtime/meta path deterministically.
7. Focused cross-repo tests are green and reported with command/result.
8. Final handoff report lists all cross-repo commits and residual issues.
