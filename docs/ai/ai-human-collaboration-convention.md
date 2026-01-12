======================================================================
AI–HUMAN COLLABORATION CONVENTION
======================================================================

Purpose
-------
This document defines the working agreement between humans and AI agents
in this repository.

The goal is to maximize AI-driven design and implementation speed while
maintaining correctness by clearly separating verification and validation.

This convention is normative for all AI agents interacting with this codebase.

----------------------------------------------------------------------
1. Responsibility Separation
----------------------------------------------------------------------

Human:
- Provides REQUIREMENTS (validation targets).
- Judges validation outcomes (OK / NG).

AI:
- Performs INTERNAL DESIGN autonomously.
- Implements based on that design.
- Performs VERIFICATION automatically.
- Reports VALIDATION results explicitly.

Design approval by humans is not required.

----------------------------------------------------------------------
2. Verification vs Validation
----------------------------------------------------------------------

Verification (VERIFY):
- Question: Did we build the system right?
- Scope: Internal design ↔ implementation consistency.

Validation (VALIDATION):
- Question: Did we build the right system?
- Scope: Requirements ↔ observable behavior.

The term "correct" MUST NOT be used without specifying the axis.

----------------------------------------------------------------------
3. Drift Reporting (Mandatory)
----------------------------------------------------------------------

If validation fails, AI MUST report:

1. Validation Target
2. Internal Design Assumptions
3. Verification Result
4. Validation Result
5. Drift Delta (minimal mismatch description)
6. Rollback Point (architectural layer or Git point)
7. Restart Plan (up to two options)

----------------------------------------------------------------------
4. Restart Semantics
----------------------------------------------------------------------

Restarting from an earlier point due to validation failure is expected
and considered a successful outcome.

Git is assumed as a safety mechanism.

----------------------------------------------------------------------
5. Logging and Bootstrap Phases
----------------------------------------------------------------------

Initialization and bootstrap phases may not have full logging available.
AI must explicitly state when observations rely on bootstrap logging only.

----------------------------------------------------------------------
6. Language Rules
----------------------------------------------------------------------

Forbidden:
- "The behavior is correct." (without axis)
- "Works as designed." (without validation context)

Required:
- "Verification succeeded, validation failed."
- "Internal design assumptions diverged from requirements."

======================================================================
END OF DOCUMENT
======================================================================
