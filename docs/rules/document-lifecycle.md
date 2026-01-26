# Document Lifecycle

This rule defines how the various documentation layers interrelate. Each layer carries a distinct semantic weight and intended use:

- **Notes** capture exploratory thinking, incident summaries, and hypotheses. They carry no binding force and may change or be discarded freely; they are explicitly non-normative.
- **Design** records decisions that must remain stable and defines the intended boundaries, responsibilities, and invariants of a feature or subsystem.
- **Spec** elaborates the behavior that the implementation must satisfy; it references rules, examples, and testable properties.
- **Journal** records chronological events (e.g., outages, releases, diary entries) and does not alter behavior.

Promotion follows the sequence **notes → design → spec**: an idea can start in notes, mature into a design statement, and finally be codified as a spec. At each hand-off the material should be reviewed for completeness, clarity, and alignment with upstream rules. Because notes are exploratory, they remain unbounded and do not impose requirements on implementation. No portion of this rule describes executable code or implementation-level steps; it only governs documentation placement and expectations.
