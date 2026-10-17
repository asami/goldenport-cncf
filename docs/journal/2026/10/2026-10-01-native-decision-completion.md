# Native managed Decision completion

sm-workflow Phase 2's explicitly admitted CNCF Phase 88 producer slice adds
`WorkflowManagedDecisionCompletion` and the Component storage
`bindDecisionCompletionC` facade. Structural Decision result admission is
separate from human verification and command permission. Private host-selected
kind binding retains the exact native Decision wire; default public WorkOrder
bindings remain WorkOrder-only and never normalize or infer a Decision.

The existing managed claim, pure closing-program preparation, same physical
DataStoreSpace/provider, atomic instance/history/result/claim transition and
verified readback remain the only completion path. Owned claims are released
only for proven pre-native failure. No private pool, direct SQL, additional
grant, public claim token or human-proof inference is introduced.

Two new actual managed SQLite executable examples verify rejection before
closing execution, exact typed answer and adjacent history retention, replay
refusal, physical reopen without interpretation, and owned claim release after
wrong closing result type followed by successful valid completion. Full-record
comparisons use canonical storage encoding, not decoded DefinitionBinding
object identity.

Invocation 593 compiled but failed those two identity-based assertions
(128/130 passed). Three new assertions were repaired without product-code
changes. Invocation 594 passed 130/130 tests in nine suites, zero failures,
aborted/canceled/pending/ignored tests. Real CML generation and provenance
validation ran. SBT and wrapper exits 0; shared lock released. Saved summary
and the two native Decision scenarios were inspected; diff check clean.

Log: `/tmp/skill.cncf.d/a-8be12fff-a855-466a-9cf0-4dc2c62301e1/4038-20261001T113054Z.log`.
Summary: `/tmp/skill.cncf.d/a-8be12fff-a855-466a-9cf0-4dc2c62301e1/4038-20261001T113054Z.summary.json`.

The parent executed under user-directed limited recovery, with fresh permission
metadata, first-attempt scoped escalation and the canonical serial wrapper.
Earlier Cozy prerequisite failures were resolved by externally revised actual
Cozy sources and standard runtime classpath export; this task did not edit
Cozy source. The unsuccessful temporary packaged-runtime selector was removed.
No agent, commit, push, publication, artifact refresh or Phase acceptance ran.

Next: provide a separately bound declared public Decision completion Operation,
then refresh the producer artifact and consume it in sm-workflow. Real human
issuer/verifier, application pending/resume and bounded command authority are
not established by these structural fixtures. Full Phase validation remains open.
