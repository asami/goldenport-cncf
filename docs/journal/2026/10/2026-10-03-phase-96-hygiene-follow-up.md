<!-- cncf-phase-finding:HYG-P96-DOC-001:begin -->
Status: OPEN
Discovery state/date: REVIEW / 2026-10-03
Source review: phase96-p96-01a-step-review-001 / revision 1
Category/priority: presentation / P3

HYG-P96-DOC-001 — P96-01 Stage Status placement. Owner: goldenport-cncf. Evidence: docs/phase/phase-96-checklist.md:59–63 places the inventory paragraph between the P96-01 Stage header and Stage Status, contrary to ai/directive/core/phase-subphase-checklist.md §7.1. Nonblocking reason: all status and closure fields remain explicit and truthful; this is presentation maintenance with no behavior or contract impact. Follow-up boundary: relocate only the existing inventory paragraph below the unchanged Stage Status block, preserving every link, status and unchecked requirement.
<!-- cncf-phase-finding:HYG-P96-DOC-001:end -->

<!-- cncf-phase-finding:HYG-P96-02-001:begin -->
HYG-P96-02-001 — Static ABI executable-specification display traceability. Owner: cloud-native-component-framework. GeneratedLogicalUiAbiSpec.scala:62/65 and GeneratedLogicalUiAbiPropertySpec.scala:59/62 omit afterWord spec/example/rules/phase metadata, visible E<n> identities and Given contract references required by executable-spec-display-and-tagging-rules.md A.1/A.3, 1.1 and 4.2. The existing adjacent semantic Given/When/Then flows and all 23 static scenarios/laws pass; this is nonblocking presentation maintenance, not missing behavior proof. Follow-up scope: add stable display identities and governing generated-ABI references to the existing descriptions in these two suites without changing their behavior, coverage, production API or contract.
<!-- cncf-phase-finding:HYG-P96-02-001:end -->
