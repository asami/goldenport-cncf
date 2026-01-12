# Verification and Validation

This document clarifies how AI agents must separate verification from validation
and how Phase/Stage labels are used in project reporting.

Related:
- docs/ai/ai-development-governance.md
- docs/ai/ai-human-collaboration-convention.md

## Problem Statement

AI-assisted work can drift when verification (internal design consistency) is
reported as validation (alignment with human requirements). This project
requires explicit separation so progress decisions and correctness claims are
grounded in requirements, not internal checks.

## Why Naive Approaches Fail

- Treating verification as validation hides requirement mismatches.
- Using the term "correct" without an axis obscures responsibility and scope.
- Advancing a Phase or Stage based only on internal checks skips human
  validation.
- Implicit assumptions about requirements create untraceable drift.

## Adopted Design

- Verification and validation are distinct axes and must be labeled explicitly.
- Humans own requirement intent and validation judgment.
- AI owns internal design, implementation, and verification.
- Phase/Stage are labels for progress tracking, not correctness claims.
- Progression is allowed only when validation passes, per
  docs/ai/ai-development-governance.md.
- Validation failure requires drift reporting, per
  docs/ai/ai-human-collaboration-convention.md.

## Operational Implications

- Reports must state whether a result is verification, validation, or not
  evaluated.
- Avoid phrases like "works as designed" or "correct" without an axis.
- When validation fails, include the required drift report fields.
- If observations rely on bootstrap logging only, state that explicitly.
