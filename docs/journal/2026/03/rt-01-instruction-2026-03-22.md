# RT-01 Instruction (CNCF Runtime Alignment for CAR/SAR)

status=ready
published_at=2026-03-22
owner=cncf-runtime

## Goal

Complete Phase 9 RT-01 by aligning CNCF runtime loading and introspection
with CAR/SAR packaging contracts.

## Background

Phase 9 current status:

- CS-01: DONE
- CS-02: DONE
- PK-01: DONE
- PK-02: DONE
- RT-01: ACTIVE

Available upstream results:

- Cozy grammar/model integration for Component/Subsystem
- `sbt-cozy` CAR/SAR packaging tasks and tests
- CAR/SAR model contract note (`SAR > CAR` precedence)

## In-Scope

1. Runtime intake path
- Define/implement runtime intake path for `.car` and `.sar` artifacts.
- Preserve deterministic load order and failure behavior.

2. CAR/SAR metadata interpretation
- Read and validate `meta/manifest.json` contract fields needed by runtime.
- Reject invalid or incomplete manifest deterministically.

3. Resolution and precedence
- Apply extension/config precedence policy:
  - extension: `SAR > CAR`
  - config: `SAR > CAR`
- Keep Component execution boundary consistent with existing CNCF model.

4. Introspection/projection visibility
- Expose loaded artifact metadata in `meta/help/describe/schema` where required.
- Keep deterministic ordering in projection outputs.

5. Regression safety
- Preserve existing `.car` loading behavior.
- Do not introduce parallel competing public loading APIs.

6. Documentation alignment
- Reflect RT-01 completion in Phase 9 tracking docs.

## Out of Scope

- New domain features (`textus-user-account` / `textus-identity`) implementation.
- Packaging implementation changes in `sbt-cozy` unless strictly required.
- Security model redesign outside CAR/SAR integration needs.

## Suggested File Targets

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/repository/ComponentRepository.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/repository/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/projection/...`

Use concrete touched files in the final report.

## Required Deliverables

1. Runtime code changes for CAR/SAR intake + metadata handling.
2. Deterministic precedence application (`SAR > CAR`) for extension/config.
3. Executable specs for:
- valid CAR load
- valid SAR + CAR composition
- manifest invalid/missing error behavior
- precedence behavior and deterministic projection visibility
4. Progress updates:
- `docs/phase/phase-9-checklist.md`:
  - RT-01 `Status: DONE`
  - RT-01 detailed tasks `[x]`
- `docs/phase/phase-9.md`:
  - RT-01 checkbox `[x]`
  - Work stack E updated from ACTIVE

## Validation

Run focused suites first.

Example command pattern:

```bash
sbt --batch "testOnly org.goldenport.cncf.component.repository.*Car* org.goldenport.cncf.*Sar* org.goldenport.cncf.projection.*"
```

If wildcard selection is noisy, list concrete spec classes explicitly.

## Definition of Done

RT-01 is DONE when:

1. CAR/SAR artifacts are intake-able via runtime path.
2. Manifest validation and error behavior are deterministic.
3. `SAR > CAR` precedence is enforced for extension/config.
4. Introspection/projection visibility is deterministic and covered by specs.
5. Focused tests pass and commands/results are reported.
6. Phase 9 docs are updated consistently.
