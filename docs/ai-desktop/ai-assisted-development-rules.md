# AI-Assisted Development Rules
## Separation of design, instructions, and implementation

status = draft
scope = all projects maintained by the author
audience = maintainers / contributors / AI-assisted development users

---

## 1. Purpose

This document defines mandatory rules for AI-assisted software development to prevent:
- accidental edits during design discussions,
- confusion between “instructions” and “implementation,” and
- terminology mismatch (especially around “patch” vs “change”).

These rules apply across all repositories maintained by the author.

This document governs AI execution behavior and execution responsibility boundaries.

---

## 2. Definitions (Strict)

### 2.1 direct edit
A *direct modification* of repository files performed immediately (e.g., an editor-integrated change).
- The repository state changes right away.
- Undo/diff-based review is assumed.
- Allowed only when explicitly requested by the user.

### 2.2 patch (project-local meaning)
In these projects, the term **patch** is reserved for the **result of direct edit** only.
- Do **not** use “patch” to describe codex implementation results.

### 2.3 codex implementation
Implementation work performed by a dedicated implementer (codex).
- ChatGPT produces design and instructions.
- codex performs implementation and outputs a change result (diff/commit equivalent).

### 2.4 change / modification
A neutral term meaning “something changes,” without implying the execution method.

---

## 3. Workflow Phases

### Phase A: Design (text only)
Goal: clarify intent, boundaries, and non-goals.
Deliverable: prose only (no direct edit, no implementation output).

### Phase B: Instructions for codex (text-only instruction sheet)
Goal: provide implementation instructions that are precise and reproducible.
Deliverable: instruction text only, including:
- target files,
- exact modifications,
- constraints (what must not be changed),
- test expectations.

No direct edit is performed in this phase.

### Phase C: Implementation by codex
Goal: implement Phase B instructions.
Executor: codex (not ChatGPT).
Deliverable: codex change result (diff/commit equivalent).
ChatGPT’s role: review and design corrections only.

---

## 4. Prohibited Actions (No implicit execution)

The following are prohibited unless explicitly requested:
- performing direct edit while the user requested “instructions,”
- generating implementation output without the user requesting codex implementation,
- using the word “patch” for codex change results,
- mixing design discussion with direct modifications.

---

## 5. Trigger Phrases (Operational)

Interpret user requests as follows:

- “Design it” / “Summarize the design” → Phase A
- “Write codex instructions” → Phase B
- “Implement with codex” → Phase C
- “Direct edit” / “Edit the files now” → direct edit allowed
- “Review” → read-only review, no edits
- “codexで” / “codexに任せる” → Phase B (write codex instructions)

---

## 6. Codex vs Direct-Edit Contract

### 6.1 Codex Declaration Is Binding

If ChatGPT explicitly declares that a task will be handled via **codex**
(e.g. “I will write codex instructions”, “Handled by codex”):

- ChatGPT MUST NOT perform direct edits.
- ChatGPT MUST NOT generate patches or diffs.
- ChatGPT MUST ONLY produce:
  - textual codex execution instructions,
  - design constraints,
  - preconditions and expected outcomes.

Once declared, execution responsibility is considered transferred to codex.

---

### 6.2 Direct Edit Requires Explicit User Intent

Direct edit (immediate file modification) is allowed ONLY when:

- the user explicitly requests it (e.g. “direct edit”, “apply patch”, “edit the file now”), AND
- the current phase is clearly implementation-oriented.

Generic confirmations such as:
- “yes”
- “go ahead”
- numeric selections (e.g. “1”)

MUST NOT trigger direct edit.

---

### 6.3 Choice Consistency Rule

When ChatGPT presents execution choices, each option MUST have a fixed execution meaning.

Examples:
- “A) Write codex instructions (no patch will be generated)”
- “B) Apply direct edit (patch will be generated)”

After the user selects an option, ChatGPT MUST NOT change the execution mode.

---

### 6.4 No Implicit Confirmation Requirement

ChatGPT MUST NOT request additional confirmation after a user selects an option.

Execution semantics MUST be fully determined by the selected option itself.

---

### 6.5 Codex Instruction Purity Rule

Codex execution instructions MUST be written exclusively from the perspective of the codex executor.

Therefore, codex instructions MUST NOT include:
- human-facing operational terms (e.g., "direct edit", "ChatGPT will", "the user will review"),
- execution-mode negotiation language (e.g., "choose", "confirm", "if you want"),
- references to ChatGPT behavior or UI features.

Codex instructions MUST be expressed only as:
- explicit imperatives (e.g., "Implement", "Do not modify", "Generate"),
- clear constraints and invariants,
- expected outcomes and validation criteria.

Rationale:
Including human operational vocabulary in codex instructions causes ambiguity about execution responsibility and may lead to unintended execution mode switching.

---

## 7. Scope and Evolution

This document is intended to be shared across all repositories.
If a repository needs stricter rules, add a small repository-local addendum that references this document.

---

## 8. Quick Checklist

Before doing anything:
- Are we in Phase A, B, or C?
- Did the user explicitly request direct edit or codex implementation?
- Are we using “patch” only for direct edit results?
