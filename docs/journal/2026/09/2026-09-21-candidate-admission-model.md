# Candidate-Admission Model adoption

- Date: 2026-09-21
- Status: Design direction

The sm-workflow Step-close discussion exposed a more general AI/StateMachine collaboration pattern. A Skill can autonomously implement, update management information and perform review. It then presents the state it believes is complete. The Workflow independently decides whether that candidate satisfies its admission/closure contract.

This is named the **Candidate-Admission Model (CAM)**.

The key formulation is:

> AI/Skill constructs a candidate. StateMachine admits it. Runtime commits it.

Missing evidence becomes an Admission Gap. A Continuation requests semantic work needed to fill that gap. Existing fresh evidence can be reused, so the Workflow need not force duplicate review merely because a fixed procedural sequence says review comes next.

CNCF should treat CAM as an upper-level principle for JudgmentAction and Workflow/Continuation. sm-workflow is the first concrete proving ground, especially evidence-driven Step closure. Cozy should express enough typed Action/Result/Evidence/Continuation metadata to generate an ABI capable of preserving this separation.

CAM is not a new parallel Workflow engine or transition algebra. It is a design interpretation of the existing StateMachine/Workflow boundary and should be introduced without duplicating current Phase 64/77 contracts.
