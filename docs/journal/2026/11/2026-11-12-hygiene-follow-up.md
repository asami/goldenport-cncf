# Hygiene follow-up — 2026-11-12

## HYG-CNCF-UOW-CONTEXT-01-LEGACY-SPEC

- Status: OPEN
- Discovered: 2026-11-12, focused independent review of the Session candidate Procedure context correction.
- Owner: goldenport-cncf.
- Location: `src/test/scala/org/goldenport/cncf/workspace/SessionCandidateWorkspaceSpec.scala`, class declaration and method-local `prepare` helper.
- Evidence: the unchanged specification has no standard source-history header or semantic Given/When/Then clauses; its method-local helper lacks the prescribed `_prepare_` form.
- Category: executable-specification authoring conformance.
- Priority: low. The omission reduces maintenance clarity and convention consistency.
- Current boundary: this supporting specification was not modified by the three-file context correction. Its historical authoring maintenance is separate from the Procedure binding defect.
- Proposed task: add the source-history header from reliable introduction history, place semantic GWT clauses at the existing setup/action/expectation boundaries, and rename the local helper mechanically without changing behavioral assertions.
- Follow-up task/commit: not assigned.
