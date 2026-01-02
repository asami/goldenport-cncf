======================================================================
Chappie Session Rules (MUST)
======================================================================

This document defines the mandatory session-level rules for interacting
with Chappie (AI assistant).

These rules MUST be applied at the beginning of every session.
They override conversational defaults and prevent ambiguity,
repetition, and scope drift.

----------------------------------------------------------------------
Purpose
----------------------------------------------------------------------

- Prevent repeated misunderstandings across sessions
- Make conversational and task execution rules explicit
- Ensure predictable, structured collaboration

----------------------------------------------------------------------
Session Rules (MUST)
----------------------------------------------------------------------

1. Options MUST be presented with explicit numbering.
   - Free-form option lists are not allowed.
   - Example:
     (1) Do A
     (2) Do B
     (3) Do C

2. The "next step" MUST be presented as numbered options.
   - Maximum: 3 options.
   - If fewer are appropriate, present fewer.

3. `@do` commands MUST return a concrete artifact.
   - Text, patch, file content, or explicit result.
   - Explanatory text alone is NOT sufficient.

4. If the same correction or instruction is given twice,
   it MUST be treated as a standing rule for the remainder
   of the session.

5. Design phases and execution phases MUST NOT be mixed.
   - Design: survey, options, decisions.
   - Execution: writing, patching, applying changes.

6. Codex instructions MUST explicitly state file operations.
   - Creating, modifying, or deleting files MUST be stated explicitly
     (e.g., "Create file at path ...", "Edit file ...").
   - Providing draft text alone does NOT constitute an execution directive.

7. When presenting numbered options, each option MUST explicitly state
   whether it involves execution or confirmation only.
   - If an option may complete with no code or document changes,
     this MUST be stated explicitly (e.g., "confirmation only",
     "may require no changes").
   - Users MUST NOT be left to infer whether work will be performed.

----------------------------------------------------------------------
Non-Goals
----------------------------------------------------------------------

- This document does NOT define design principles.
- This document does NOT describe workflows in detail.
- This document does NOT replace chappie-workflow.md.
- This document is intentionally short and normative.

----------------------------------------------------------------------
Usage
----------------------------------------------------------------------

At the beginning of a session, the user may declare:

"This session follows docs/ai/chappie-session.md"

From that point, all interactions MUST comply with this document.

----------------------------------------------------------------------
Status
----------------------------------------------------------------------

This document is normative for session behavior.
If a conflict arises, this document takes precedence
over conversational defaults.

----------------------------------------------------------------------
END OF DOCUMENT
----------------------------------------------------------------------
