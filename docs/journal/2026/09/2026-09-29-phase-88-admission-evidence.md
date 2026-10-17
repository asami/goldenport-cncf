# Phase 88 S6: Retained Candidate-Admission submission

Date: 2026-09-29
Status: bounded producer slice focused/regression-validated; full joint runtime remains open
Work A: CNCF Phase 88 joint recovery prerequisite for sm-workflow Phase 2

## Bounded contract

Reuse the existing CandidateAdmissionModel.AdmissionSubmission as the declared
semantic result payload. Add a strict versioned PayloadCodec composition for the
candidate snapshot, evidence payload and application-owned scope. The application
explicitly binds its result type and all three payload codecs; CNCF neither
infers their meaning nor invents a result type from a storage path.

Compose that codec with S5 WorkflowCompletionDataStorePersistence unchanged.
All AdmissionEvidence values live in the exact retained typed result on the
claimed Continuation Entity. That Entity and the instance history already share
one canonical guarded two-record transaction. No separate evidence write, third
store, SQL backend, callback or alternate completion service is necessary.

Reuse CandidateAdmissionEvidencePersistence.JsonV1 for each evidence record.
The submission envelope must bind each nested record to the same submission
identity, preserve evidence order and reject duplicates. Keep candidate identity/
revision, payload/scope types and values, requirement identity and provenance exact.
Lossy/throwing codecs fail before the completion write. Recovery uses the admitted
definition catalog and same codec bindings and grants no claim or execution authority.

Storage preserves shape-valid wrong-target, stale and insufficient coverage
evidence. CandidateAdmissionModel.evaluateC and the explicit application
EvidencePolicy decide reuse/admission after recovery; persistence is not a positive
judgment, route selection, human approval, or Git/SBT authority.

## Executable acceptance

- Strict typed JSON round-trip, Unicode/escape/value property coverage, exact
  fields/schema/nested identity and duplicate rejection, codec failure closure.
- Memory and SQLite completion retain payload/scope together with generic
  ExecutionEvidence, host history and exact claim.
- SQLite resource close/reopen recovers exact submission and re-evaluates it
  under application-owned freshness/coverage policy.
- Wrong target, stale/missing/narrow evidence remain typed Pending gaps.
- Invalid/lossy input leaves both records unchanged; stale/replay errors cannot
  overwrite the retained submission.
- Injected pre-commit failure rolls back all records; unadmittable applied
  readback is transaction-indeterminate and recovery never replays.
- Existing S1-S5 source/specs and legacy CandidateAdmission LocalJson/InMemory
  remain unchanged.

## Ownership and non-claims

Only new CandidateAdmissionSubmissionJsonV1.scala,
CandidateAdmissionSubmissionJsonV1Spec.scala and
CandidateAdmissionCompletionPersistenceSpec.scala are product/spec mutation scope.
The paired Markdown contract is docs/spec/candidate-admission-submission-retention.md
in CNCF. Existing S5 product code is reused without edits or an extra adapter.
The Phase 88 and sm-workflow Phase 2 journals/notes/checklist carry execution evidence.
CNCF stays at its existing 0.5.3-SNAPSHOT; no source publication/commit is selected.
Cozy is validation-only if its exported development classpath requires refresh.

This closes a bounded application payload/scope retention prerequisite, not
P2-U05 as a whole. Active UnitOfWork event/data/closing-program enlistment needs
a proven shared transaction-domain EventEngine and remains open. Separate-process
launcher recovery, production Factory/claim/Operation binding, server/MCP,
artifact consumption and management also remain open.

## Implementation and observed validation

CandidateAdmissionSubmissionJsonV1 is a pure declared PayloadCodec composition,
not a new datastore/provider. Strict encoding verifies exact model and canonical
byte round-trip; decoding checks exact fields, schema, payload types and nested
submission identity before existing model validation. Application codec failures
are contained as typed boundary failures. The unchanged S5 adapter also requires
exact canonical bytes during read-only recovery.

CandidateAdmissionSubmissionJsonV1Spec supplies five executable examples, including
50 generated payload/scope round-trips per property check.
CandidateAdmissionCompletionPersistenceSpec supplies nine executable examples.
They prove exact memory/SQLite retention, resource close/reopen, explicit policy
re-evaluation, Pending gaps, codec failure closure, claim/replay guards, five
pre-commit rollback variants, committed-but-unadmittable readback recovery and
incompatible recovery without rewriting stored facts. The host completes a
review.Collected entry, not a fabricated positive Candidate-Admission entry.

Invocation 256 stopped in Test compilation: an empty evidence vector in the new
replay fixture inferred Nothing for evidence/scope types. No tests ran. The
fixture was repaired by explicitly declaring AdmissionEvidence[String, Int];
product invariance and old S5 sources were not relaxed. That terminal failure
receipt is retained below rather than represented as acceptance.

Focused-257 passed 36/36 tests in five suites, zero failed/canceled/pending/aborted,
including all fourteen new S6 examples and twenty-two existing admission/S5 examples.
Selected regression-258 passed 1068 tests in 129 suites, zero failed/aborted;
eight existing opt-in live MySQL examples were canceled and five existing
placeholders pending. Canceled/pending examples are not executed acceptance.
Both successful commands ended with SBT/wrapper exits 0/0 and lock=released,
and bind the same complete SM/CNCF/Cozy tree:
0bac2e696daf047f4d8d5d1b546345903de77bcfce1b82eeeefcaee80ad933ef.

All three S6 Scala source identities match both accepted receipts. All nine
previously frozen S5 product/spec identities are unchanged. Existing
CandidateAdmissionEvidencePersistence, Factory, S3/S4 and unrelated dirty work
are preserved. Cozy was not edited or separately invoked this turn.
This is selected producer-source regression, not whole-CNCF full testing,
published-artifact consumption, production host/Operation/CLI/server/MCP,
separate-process restart or live MySQL acceptance. SQLite reopen reconstructs
resources in the same process; rollback proof concerns records, not schema DDL.

P2-U09 records only retained application payload/scope composition. P2-U05 stays
unchecked because active UnitOfWork event/data/closing-program shared transaction
enlistment remains absent. Production claim admission and runtime composition
remain open. Shape-valid evidence still requires the current application policy;
a retained completion never supplies approval, progression, claim or command
authority. No AI or external command runs inside the record transaction.
Phase 88 and sm-workflow Phase 2 remain in progress.

Routing used cncf-command-execution, cncf-agent-execution,
cncf-sbt-serial-execution and cncf-workflow-protocol with immutable receipt-first
intakes and original native authority history. All three runners were registered
custom role cncf_command_runner, gpt-5.6-luna / medium. The parent independently
verified receipt identity, terminal markers, summaries/logs and source hashes.
No stage, commit, publishLocal, release, push or deployment was selected.
Documentation updated after validation is not part of the captured test tree.

## Invocation evidence

- SMWF-P2-CNCF-P88-S6-FOCUSED-20260929-256; failure / terminal
  runner: /root/sbt_receipt_e493d927b625e3e2fb58
  receipt SHA-256: 19bc0ba741992bd13688ea7672d60c456bc3bdf45c9be514128c9db14729e89f
  complete tree SHA-256: 15bb7007732e220d5943f005f62cde7647373a05fb8026940198a1d2cbfb97b2
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-50042710abd0773e58d9304b18e0595f57258bb353d9605881621ae325a2850b-1f74a52be996a30960eb4c19e0431a52/command-execution-sha256-19bc0ba741992bd13688ea7672d60c456bc3bdf45c9be514128c9db14729e89f`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S6-FOCUSED-20260929-256 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-cfe1c58f6656fe1435bd618c0716b45a2add95f754f8add714e0d0ec9840fb27-5adabb992100e71aaf739578cfc310e6/90676-20260929T080754Z.log summary=/tmp/skill.cncf.d/cncf-sbt-cfe1c58f6656fe1435bd618c0716b45a2add95f754f8add714e0d0ec9840fb27-5adabb992100e71aaf739578cfc310e6/90676-20260929T080754Z.summary.json`
  observed counts: {"tests":{"total":null,"succeeded":null,"failed":null,"canceled":null,"ignored":null,"pending":null},"suites":{"completed":null,"aborted":null},"compile_failed":true}

- SMWF-P2-CNCF-P88-S6-FOCUSED-20260929-257; success / terminal
  runner: /root/sbt_receipt_c6b65d03fe01fccc744a
  receipt SHA-256: 5cf3e92b3ccdc3b9c73e693c413d773d50ca27b9a6e4406dd7972c7ee134de54
  complete tree SHA-256: 0bac2e696daf047f4d8d5d1b546345903de77bcfce1b82eeeefcaee80ad933ef
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-85988a7c6f2d8bb58595b52f38dbb8aba1efbb6bb11148ab842db3927ddc6dc9-b7e19c21fe473a114e8ac8d14a28de1b/command-execution-sha256-5cf3e92b3ccdc3b9c73e693c413d773d50ca27b9a6e4406dd7972c7ee134de54`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S6-FOCUSED-20260929-257 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-079e3b6ed1bccf29492b1cfc31a6608ada87d952ca023b7f78c046da4c5c4ac3-07c38fc6d85ab742b2bbef53dc349a7e/93081-20260929T081058Z.log summary=/tmp/skill.cncf.d/cncf-sbt-079e3b6ed1bccf29492b1cfc31a6608ada87d952ca023b7f78c046da4c5c4ac3-07c38fc6d85ab742b2bbef53dc349a7e/93081-20260929T081058Z.summary.json`
  observed counts: {"tests":{"total":36,"succeeded":36,"failed":0,"canceled":0,"ignored":0,"pending":0},"suites":{"completed":5,"aborted":0},"compile_failed":false}

- SMWF-P2-CNCF-P88-S6-REGRESSION-20260929-258; success / terminal
  runner: /root/sbt_receipt_2847a7a47ea6ec6412a5
  receipt SHA-256: c6ac9c43576f157cbb5d760cd37e2ce7d37d876774c22b1c4b8a245dcf09bb15
  complete tree SHA-256: 0bac2e696daf047f4d8d5d1b546345903de77bcfce1b82eeeefcaee80ad933ef
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-bb2c10961a277c5b40e676503afe1a8fe2bd985d8c8066d2634f56f1ab47e949-9a1b306bd48af2c902eadec1ba6ee54d/command-execution-sha256-c6ac9c43576f157cbb5d760cd37e2ce7d37d876774c22b1c4b8a245dcf09bb15`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S6-REGRESSION-20260929-258 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-b8493e618e1f61928e20fc67a564702c8e8e71b3a737161e9c011800e95d26c8-291863d7521f3415fd9e63fee74587c6/95627-20260929T081416Z.log summary=/tmp/skill.cncf.d/cncf-sbt-b8493e618e1f61928e20fc67a564702c8e8e71b3a737161e9c011800e95d26c8-291863d7521f3415fd9e63fee74587c6/95627-20260929T081416Z.summary.json`
  observed counts: {"tests":{"total":1068,"succeeded":1068,"failed":0,"canceled":8,"ignored":0,"pending":5},"suites":{"completed":129,"aborted":0},"compile_failed":false}

## Accepted owned source identities

- src/main/scala/org/goldenport/cncf/workflow/CandidateAdmissionSubmissionJsonV1.scala: 657f68de1b5292f9ca858d823eab9270e415d2c714de60822f8a938defbbb20c
- src/test/scala/org/goldenport/cncf/workflow/CandidateAdmissionSubmissionJsonV1Spec.scala: 2dce97dd882e81a9308b5977d1db2e425e2dd6d53829716c678ff3559c61affc
- src/test/scala/org/goldenport/cncf/workflow/CandidateAdmissionCompletionPersistenceSpec.scala: 1fcd6bd412bff74c9b43c0b3221fff8500be1faa2a00e97b986114b39b679681

Unchanged S5 WorkflowCompletionDataStorePersistence.scala: 3bb48c3b7b76dbb9a82b1e81fcc17f5bff80e445583ba96f495f14de265a2181.
Unchanged CandidateAdmissionEvidencePersistence.scala: d8b5de65daa570d62f6ad2fc0d93ce4bb9974ea9efd8fdfffd772b2f51b6568d.
