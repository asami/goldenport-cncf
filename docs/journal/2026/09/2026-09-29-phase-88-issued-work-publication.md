# Phase 88 S4: atomic issued WorkOrder publication

Date: 2026-09-29
Status: bounded producer slice focused/regression-validated; full joint runtime remains open

## Decision

Extend the S3 canonical compound record transition, not UnitOfWork or an
alternative Workflow engine. Persist the strict WorkflowWorkOrderJsonV1 bytes
as an immutable opaque property of the newly created Continuation Entity.
The root history and the Available Continuation plus issued payload are then
published by the same guarded canonical transaction. Do not use a Save side
effect to emulate immutable create: canonical side-effect Save can overwrite.

Bind explicit component identity, application PayloadCodec, configured store,
admitted definition catalog and distinct EntityStore collections. Admit exact
handle/instance/definition, run, Continuation identity, expected StateMachine
revision, operation/input/result types, context and completion/evidence contract
facts before writing. Require exact codec round-trip. Recover only an exact
issued payload whose instance retains the matching suspension history.

Implement the existing IssuedWorkOrderPersistence read/retry boundary. Its
putIfAbsentC accepts only an already atomically published identical issue; it
must not fill missing issued payload on an independently published Continuation.
Claim/release/complete preserve the immutable property through canonical CAS.
Existing S3/SPIs remain compatible and do not imply issued-work publication.

## Frozen executable specification scope

- exact joint publication and SQLite resource reopen with issued work and a
  retained claim, then token release/completion without payload mutation;
- every mismatched typed binding rejected before publication;
- missing/malformed/drifted persisted issued bytes fail closed without repair;
- independent putIfAbsent cannot publish or retrofit work;
- duplicate/stale suspension and pre-commit failure leave no orphan issue;
- applied-but-unadmittable issued readback is transaction-indeterminate and
  recovery reads committed facts without automatic replay;
- independently bound writers have one guarded winner.

Owned source scope: WorkflowInstanceDataStorePersistence.scala,
WorkflowIssuedWorkDataStorePersistence.scala and
WorkflowIssuedWorkDataStorePersistenceSpec.scala. Existing S2 Factory and S3
Continuation implementation remain untouched. Documentation updates only in
Phase 88 and the corresponding sm-workflow Phase 2 records.

## Still open

This is not AtomicWorkflowSuspensionEngine, active UnitOfWork event/data
participation, retained result/admission evidence atomicity, compound completion,
claim admission policy, production Factory service/launcher/MCP binding,
separate-process restart or downstream artifact consumption. A read does not
claim work. The public sm-* skill contract remains semantic-result-only.

## Implementation and observed validation

Host binding fixes both the Component identity and completion Operation. The
caller cannot substitute a different completion selector. Pure Continuation
codec validation runs before typed work matching, including incomplete nested
metadata rejection. Application codec exceptions/lossy round-trips are typed
pre-publication failures. No SQL, engine, event callback, resource owner or
skill-selected authority is introduced. The unchanged Continuation CAS adapter
preserves the co-located immutable issued property through claim/release/complete.

246 and 247 compiled the implementation and passed all 25 existing S1-S3 tests,
but the new suite aborted during its fixture construction. 246 used an invalid
namespace; 247 corrected that namespace but still had a lower-case local ID.
The fixture now uses the same qualified PascalCase identity convention as
existing successfully executed specs. These are completed SBT failures, not
permission stops and not S4 test acceptance. No failed attempt was reused.

Focused-248 passed all 34/34 tests in four suites: nine S4, ten S3, seven S1 and
eight S2/bootstrap examples, with zero failed/canceled/pending/aborted.
Regression-249 completed 125 suites, 1033 succeeded, 0 failed, 8 canceled and 5 pending. The canceled/pending examples
are existing environmental/placeholder cases and not executed acceptance.
This is selected producer regression, not whole-CNCF full-test or downstream
production service acceptance. All four invocations reached matching terminal
markers with lock=released; both successful receipts bind the same three S4
source identities. Parent verification checked receipt, log/summary and source
hashes. S2 Factory, S3 Continuation/spec and the other-owned SplitPhase document
remain unchanged. No source or document was edited during a live invocation.

All runners: registered custom role cncf_command_runner, gpt-5.6-luna / medium.
Command routing used cncf-command-execution, cncf-agent-execution,
cncf-sbt-serial-execution and immutable cncf-workflow-protocol receipt-first
intakes; normal development caches and the shared serial wrapper were retained.

- initial fixture failure: SMWF-P2-CNCF-P88-S4-FOCUSED-20260929-246; failure / terminal
  runner: /root/sbt_receipt_734714ebc4336e94a443
  receipt SHA-256: 949a142f0c37d18923ef0356af5b38106b1c943cb29973db6a58e84f2429a413
  complete tree SHA-256: 8b0b28cc58db6aa781a3e21dad540b459168bd51ae1c0976bce6076fff0777c0
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-eab996adfc3e6abde58c0df3b9de63fa8d83169fd69a72d151d94b85f4405714-91ce135b45eaf966c42e2d266647c3a3/command-execution-sha256-949a142f0c37d18923ef0356af5b38106b1c943cb29973db6a58e84f2429a413`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S4-FOCUSED-20260929-246 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-a0ccf87fdf0f5327372562666134c740b1d77518864ae1f438c936a0ba0be252-90ac06ebbc3063932bfbfcc570b96adc/93818-20260929T050529Z.log summary=/tmp/skill.cncf.d/cncf-sbt-a0ccf87fdf0f5327372562666134c740b1d77518864ae1f438c936a0ba0be252-90ac06ebbc3063932bfbfcc570b96adc/93818-20260929T050529Z.summary.json`

- second fixture failure: SMWF-P2-CNCF-P88-S4-FOCUSED-20260929-247; failure / terminal
  runner: /root/sbt_receipt_d607aa3e3a37a7d69c10
  receipt SHA-256: 2a2ce01d55a64cc664fb89531c1b37ba1a055e361ec3d29cda26a8398a8f0529
  complete tree SHA-256: ec6d7f06597e8e8c938d9a692bdd8f2872d0d500a3b6f6d354b8a0328f8a511c
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-ad12044ae205f5fd07a3ff134a43cd6f12012db6d6cf75da053b48fdd0796325-aa78c5fa930661f8c17c8cc76a3a3cfd/command-execution-sha256-2a2ce01d55a64cc664fb89531c1b37ba1a055e361ec3d29cda26a8398a8f0529`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S4-FOCUSED-20260929-247 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-7c331899210eedb752fc2c17fc205a2f7bfd72f43741c5479f3c993cc7ebc9e8-adc04a0ccfedcfd57b713d014701f9b8/95850-20260929T050745Z.log summary=/tmp/skill.cncf.d/cncf-sbt-7c331899210eedb752fc2c17fc205a2f7bfd72f43741c5479f3c993cc7ebc9e8-adc04a0ccfedcfd57b713d014701f9b8/95850-20260929T050745Z.summary.json`

- focused: SMWF-P2-CNCF-P88-S4-FOCUSED-20260929-248; success / terminal
  runner: /root/sbt_receipt_a6a5b5a26e5ee6855da7
  receipt SHA-256: 8579355f382996061bcca2e06d9dbc516deb3f4edbefc70bee5492fedaf31dd1
  complete tree SHA-256: 549e183d0da95be17b823bf38685ba2fae03774b51ef1495deda61e23b0ddab5
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-55372b2fc30ef9826d4d7e82f2209c8e14b409dc34d94d27efeca5ba0d32bca4-97cd0f8d90eb0a99ba8512e0b5184dd3/command-execution-sha256-8579355f382996061bcca2e06d9dbc516deb3f4edbefc70bee5492fedaf31dd1`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S4-FOCUSED-20260929-248 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-46f8d58669317735e624ca04666a07f0990319660511bd52b30e1d9ca0dcac8a-73507e6db3260ae4a7e5bd353bfdcdee/98371-20260929T051012Z.log summary=/tmp/skill.cncf.d/cncf-sbt-46f8d58669317735e624ca04666a07f0990319660511bd52b30e1d9ca0dcac8a-73507e6db3260ae4a7e5bd353bfdcdee/98371-20260929T051012Z.summary.json`

- regression: SMWF-P2-CNCF-P88-S4-REGRESSION-20260929-249; success / terminal
  runner: /root/sbt_receipt_3ea75c80822cb0fb2bb5
  receipt SHA-256: 16db26474e79fde3de0785c25d266249111a1a19286687a054627ec50cd8980d
  complete tree SHA-256: 549e183d0da95be17b823bf38685ba2fae03774b51ef1495deda61e23b0ddab5
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-f4bb01683609e3979b699fc413cdecc6826f08d3b75a98ee864b9f57a7bf0f3a-3ae4ba43195ea9ab6d271233c190fc6b/command-execution-sha256-16db26474e79fde3de0785c25d266249111a1a19286687a054627ec50cd8980d`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S4-REGRESSION-20260929-249 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-c54bf3afb737120c8a6601dfe4c55ad799af81f0b398216a4a41f0a689acae48-ae2b14b232cd478afcac682a88bb650e/533-20260929T051217Z.log summary=/tmp/skill.cncf.d/cncf-sbt-c54bf3afb737120c8a6601dfe4c55ad799af81f0b398216a4a41f0a689acae48-ae2b14b232cd478afcac682a88bb650e/533-20260929T051217Z.summary.json`

Validated S4 source SHA-256 values:

- src/main/scala/org/goldenport/cncf/workflow/WorkflowInstanceDataStorePersistence.scala
  1c85b32c0a0c1a6ed875a46751b0a1dee311d23f7b052ad89775a24a20b24eff
- src/main/scala/org/goldenport/cncf/workflow/WorkflowIssuedWorkDataStorePersistence.scala
  442e9bb8a5b1f1abe1fe1ff1f0842ccb616ce069d640a5f1a538d3c6b49f7607
- src/test/scala/org/goldenport/cncf/workflow/WorkflowIssuedWorkDataStorePersistenceSpec.scala
  7eca33d11ecaa326aaee7a8c98338dbcd4d0bf05b019f01a84089720e7a6b8ac

After validation only Phase/notes/journal management text is updated; it was
not part of the captured test tree. No publishLocal, stage, commit, push, remote
publication, skill deployment, private engine/SQL or launcher/server execution
occurred. Phase 88 and sm-workflow Phase 2 remain in progress. P2-U07 closes only
this bounded publication/recovery slice; P2-U05 and P2-D03 remain open.

## Next boundary

Define retained typed result/admission evidence and compound completion against
the exact claimed Continuation plus instance history. Separately provide a real
canonical active UnitOfWork/event/data prepare/commit/abort participant; do not
advertise AtomicWorkflowSuspensionEngine from S4. Require configured production
claim admission before exposing work: this adapter's read is not a claim and
ordinary Continuation SPI calls alone do not validate issued payload. Missing
issued bytes on a legacy pair remain a typed rejection, never a migration repair.
Validations reconstruct SQLite resources within one process; separate-process
launcher restart, management projections, server/MCP and artifact consumption
remain open. Store property immutability is enforced by these adapter APIs, not
a claim of tamper-proof storage against arbitrary direct datastore mutations.
