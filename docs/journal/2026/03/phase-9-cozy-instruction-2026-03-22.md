# Phase 9 Cozy Instruction (CS-01 / CS-02 / PK-02)

status=ready
published_at=2026-03-22
owner=cozy-modeler

## Execution Rule

- Related project updates and `sbt` executions do not require confirmation prompts.
- Proceed directly and report executed commands/results in the execution report.

## Goal

Execute the Cozy-side slice of Phase 9:

- CML grammar freeze for Component/Subsystem family
- Cozy parser/model/generator implementation
- CAR/SAR packaging implementation in `sbt-cozy`

This instruction is for the Cozy development thread.

## Scope

In scope:

1. CS-01: CML grammar contract freeze
- `Component`
- `Componentlet`
- `ExtensionPoint`
- `Subsystem`
- Extension binding and component coordinate syntax

2. CS-02: Cozy implementation
- Parser acceptance
- AST/model extension
- Generator output alignment for downstream packaging/runtime
- Deterministic behavior and invalid-case validation

3. PK-02: `sbt-cozy` packaging implementation
- CAR output
- SAR output
- Manifest/meta output
- Packaging verification

Out of scope:

- CNCF runtime intake implementation (RT-01; CNCF thread scope)
- Textus domain feature implementation (`textus-user-account` / `textus-identity`)

## Base References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-9.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-9-checklist.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/cml-component-subsystem-grammar-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/car-sar-model-note.md`

## Target Repositories

- Cozy: `/Users/asami/src/dev2025/cozy`
- sbt plugin: `/Users/asami/src/dev2026/sbt-cozy`

## Required Deliverables

1. Grammar contract finalization
- Final accepted grammar examples
- Invalid-case matrix and expected failure behavior

2. Cozy implementation results
- Changed parser/model/generator files
- AST/model contract summary for Component/Subsystem constructs
- Deterministic output rule summary

3. `sbt-cozy` packaging results
- Added/updated tasks/settings for CAR/SAR generation
- Artifact structure conformance:
  - CAR: `component/ lib/ spi/ config/ docs/ meta/`
  - SAR: `subsystem/ extension/ config/ meta/`
- Precedence contract documentation:
  - extension: `SAR > CAR`
  - config: `SAR > CAR`

4. Verification report
- Exact test/build commands and results
- Produced artifact examples (path + tree summary)
- Remaining gaps for CNCF RT-01 handoff

## Suggested Work Items

1. Grammar freeze (CS-01)
- Confirm current grammar parser behavior against handoff proposal.
- Fix ambiguity points and deterministic ordering rules.
- Lock minimum viable syntax for this phase.

2. Cozy implementation (CS-02)
- Implement parser + model updates for new declarations.
- Ensure generator emits metadata needed for CAR/SAR manifests.
- Add focused parser/model/generation specs.

3. `sbt-cozy` packaging (PK-02)
- Add CAR/SAR packaging tasks.
- Produce manifest and metadata files from generated model.
- Add plugin-level verification (tests or scripted checks).
- Update plugin README with concrete usage commands.

## Validation

Run focused checks in each repository.

Example command pattern:

```bash
cd /Users/asami/src/dev2025/cozy
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"

cd /Users/asami/src/dev2026/sbt-cozy
sbt --batch test
sbt --batch scripted
```

If `scripted` scope is noisy, run concrete scripted cases and list them.

## Definition of Done

This instruction is DONE when all conditions hold:

1. CS-01 grammar contract is finalized and documented.
2. CS-02 parser/model/generator path is implemented with focused green specs.
3. PK-02 CAR/SAR packaging path is implemented and verified in `sbt-cozy`.
4. Execution report includes changed files, commands, results, and artifact output summary.
5. CNCF handoff notes for RT-01 are posted with no ambiguity in contract boundaries.
