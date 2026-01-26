# AI Document Handling

This governance rule complements `docs/rules/document-lifecycle.md`. It prescribes what AI agents are and are not permitted to do within each document layer.

- **Notes**: AI may summarize, restructure, and deepen exploratory notes, but must never over-complete the content or pretend that speculative outcomes are approved. Notes remain exploratory; AI should mark anything it adds as provisional and avoid describing the outcome as decided. Deep edits that clarify intent are acceptable so long as they do not fabricate commitments.
- **Design**: AI may assist by editing for clarity, consistency, and formatting, but must not invent requirements, decisions, or features. Any additions must be traceable to explicit human intent. AI must flag uncertainties for human review rather than guessing missing content.
- **Spec**: AI may clean up wording and align the text with existing rules, but inventing new requirements, behaviors, or acceptance criteria is forbidden. Spec content must remain grounded in existing executable specifications or human direction.
- **Journal**: AI can record events, summarize actions, and help keep entries concise; no binding decisions should be introduced.

Because notes are explicitly non-binding, the risk of over-completion is highest there; AI must always treat notes as provisional and never present synthetic conclusions as approved. This rule is normative governance; it does not offer optional guidance but declares what AI-assisted edits are allowed across the documentation layers.
