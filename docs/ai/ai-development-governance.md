# AI Development Governance Convention

This document defines the mandatory governance rules for AI–Human collaborative development.
It unifies progress decision rules, validation recovery procedures, and requirement agreement baselines.

==================================================
Part 1: Step / Phase Progress Decision Convention
==================================================

## Purpose

Prevent incorrect advancement of development phases based solely on internal verification.
Phase and step progression MUST be controlled by validation against human-agreed requirements.

## Definitions

- Verification:
  Internal consistency between design and implementation.
  AI-internal and not sufficient for progress decisions.

- Validation:
  Alignment between observed behavior and human-agreed requirements.
  REQUIRED for any step or phase progression.

## Mandatory Progress Decision Report

Any suggestion to advance a Step or Phase MUST include the following report:

[Progress Decision Report]

Target:
- Phase:
- Step:

1. Verification Status
- Result: PASS | FAIL
- Basis:

2. Validation Status
- Result: PASS | FAIL | NOT EVALUATED
- Requirement Reference:
- Observations:

3. Design–Requirement Drift
- Exists: YES | NO
- Description:

4. Progress Decision
- Recommendation: ADVANCE | DO NOT ADVANCE
- Reason:

## Advancement Rule

Advancement is permitted ONLY when:

- Verification = PASS
- Validation = PASS

All other combinations forbid advancement.

==================================================
Part 2: Validation Failure Recovery & Restart Convention
==================================================

## Trigger

This section applies when Validation = FAIL.

## Mandatory Recovery Report

[Validation Failure Recovery Report]

1. Expected Behavior:
2. Observed Behavior:
3. Mismatch Summary:

4. Root Cause Classification:
   - Requirement misunderstanding
   - Internal design assumption
   - Implementation defect
   - Missing or implicit specification

5. Recovery Options:
   1) Adjust design to meet requirement
   2) Amend requirement (requires human approval)
   3) Roll back to earlier step and redesign
   4) Abort current approach and propose alternative

6. Recommended Option:
   - Option:
   - Rationale:

## Prohibited Responses

- “Working as designed”
- “Verification passed, therefore correct”
- Any justification that bypasses validation

==================================================
Part 3: Requirement Agreement & Validation Baseline Convention
==================================================

## Purpose

Establish a minimal, explicit baseline for validation without over-specifying internal design.

## Requirement Agreement Snapshot

Validation MUST be evaluated against an explicit requirement snapshot:

[Requirement Agreement Snapshot]

- Scope:
- Intent:
- Expected Observable Behavior:
- Non-goals / Out of Scope:
- Acceptance Criteria:

This snapshot defines the sole authority for validation.

## Responsibility Split

- Human:
  Owns intent, acceptance criteria, and final validation judgment.

- AI:
  Owns internal design, implementation, and verification.
  Must report drift immediately when detected.

==================================================
Enforcement
==================================================

- AI MUST NOT advance steps based on verification alone.
- AI MUST surface requirement/design drift explicitly.
- Validation failure MUST trigger recovery reporting, not justification.

==================================================
End of Convention
==================================================
