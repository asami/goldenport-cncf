# Interrupt Ticket — Phase-Crossing Work Rule

status = draft

## 1. Purpose

This rule defines an explicit control mechanism for interrupt work.
Interrupt work is exceptional and must be declared and bounded.

## 2. Definition

**Interrupt Work** is any work that temporarily violates phase order
or suspends the current phase to address work in a different phase
or conceptual area.

## 3. Mandatory Ticket Fields

Every interrupt MUST include all of the following fields.

### 3.1 Interrupt Reason
- Current Phase / Stage
- Why normal sequencing is blocked

### 3.2 Target Phase / Scope
- Which phase or conceptual area is temporarily entered
- Explicit allowed scope
- Explicit forbidden scope

### 3.3 Expected Outcome
- Concrete, minimal deliverable
- No vague terms (e.g., “cleanup”, “improve”, “refactor”)

### 3.4 Return Point (Mandatory)
- Exact Phase / Stage to return to
- First task after return

### 3.5 Completion Rule
- When interrupt work is considered complete
- Interrupt completion does not mean the original phase is complete

## 4. Prohibited Practices

The following are prohibited:

- Declaring a new phase complete due to interrupt work
- Leaving the original phase without a defined return
- Starting interrupt work without a declared return point

## 5. Enforcement Rule

Any phase-crossing work without an Interrupt Ticket is invalid.
AI assistants must refuse to proceed without this ticket.

## 6. Rationale (Non-Normative)

This rule exists to prevent structural phase drift
and to keep phase order explicit and stable.

## Appendix A. Example (Informative) — Phase 2 → Phase 2.5 Interrupt

NON-NORMATIVE / Informative

Original phase:
- Phase 2.0 demo completion was in progress (Stage 3–6 remained).

Interrupt work:
- Phase 2.5 platform contract freeze work was performed
  (error semantics and observability model stabilization).

Return point requirement:
- Return to Phase 2.6 to complete Phase 2.0 safely
  (do not resume Phase 2.0 directly).

Mandatory ticket fields (filled):

Interrupt Reason
- Current Phase / Stage: Phase 2.0, stages 3–6 incomplete
- Why normal sequencing is blocked: platform contracts needed freezing

Target Phase / Scope
- Target phase: Phase 2.5
- Allowed scope: error semantics and observability contracts
- Forbidden scope: demo completion work

Expected Outcome
- Frozen contracts for error semantics and observability (minimal artifacts)

Return Point
- Phase 2.6: Demo Completion on Frozen Platform
- First task after return: Stage 3 OpenAPI projection completion

Completion Rule
- Interrupt completion does not imply Phase 2.0 completion
END
