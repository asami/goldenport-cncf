# WorkArea disk exhaustion and owned cleanup

Date: 2026-10-21

## Incident and observed cause

Docker Desktop reported `no space left on device`. The Data volume had about
300 MiB available. The main observed consumer was the JVM temporary directory's
`cncf/` tree, about 615 GiB: 2,268 flat `car-.d*` and 433 `sar-.d*`
directories. Large expanded CARs contained about 2.8 GiB of generated
`scaladoc` each. Docker's VM storage was about 9.7 GiB at inspection.

`FileWorkArea.dispose()` was a no-op, and `partition()` reused the same base
directory. Enabling recursive deletion of that shared base would delete other
owners' work. CAR discovery also returns lazy classloaders backed by extracted
JARs, so closing every extraction immediately after discovery would break
later class loading.

## Completed bounded space recovery

The user authorized removing unnecessary remnants while retaining active work
and required evidence. A frozen manifest selected the 24 largest old generated
`scaladoc` directories (all dated 2026-10-17), totalling 66.19 GiB. Selection
required current-user ownership, ordinary non-symlink CAR and scaladoc
directories, a cutoff before 2026-10-20, and no observed open file in the CAR.
An open-file inventory was refreshed immediately before deletion, and identity,
mtime, parent contents and containment were checked against the manifest.

Only those 24 `scaladoc` directories were removed. Their parent CARs, JARs,
configuration and every other child were retained and checked after deletion.
The operation completed successfully; available capacity rose to about 67 GiB
(later observation about 66 GiB while other work continued). No user process,
Docker image/volume, source checkout, Goal history or validation evidence was
removed.

The machine-local audit is outside Git:
`~/backup/journal/2026/10/cncf-workarea-disk-recovery-20261021/`
contains `cleanup-manifest.json`, `open-files-before.txt` and
`cleanup-results.jsonl`. Open-file observation is supporting evidence, not a
general guarantee that every unopened path is dispensable; deletion was also
limited to old regenerable documentation. Recent or uncertain areas remain.

## Repair contract

- Allocate under `java.io.tmpdir/cncf/YYYY/MM/workarea-<unique>/`.
  Year and month are derived from creation time; `2028/10` is an example, not
  a fixed directory. Keep shared month buckets and old flat directories intact.
- Track each owner's allocated files, directories and distinct child scopes.
  Cleanup is idempotent, attempts all resources, reports failure and permits
  retry of failed cleanup. Do not follow symlinks into external directories.
  Caller-owned base directories and sibling scopes are not deletion targets.
- Callback-scoped work closes on success, failure and exception. Retained
  CAR/SAR extraction instead transfers its handle to the existing runtime
  resource owner; lazy classloaders keep access until runtime shutdown.
- Register the runtime's WorkAreaSpace with its SystemNode and use the existing
  shutdown path. Component classloaders close before their extracted resources.
  Do not introduce a competing JVM shutdown hook or dispose another runtime's
  area merely because GlobalContext changes.
- Date buckets aid inspection and later selective cleanup. Age alone does not
  authorize deletion; active work and evidence retention still take priority.

## Validation status

The first focused invocation compiled successfully and ran 115 tests: 110
passed and five failed in `ComponentRepositoryCarSpec`. That suite installed a
single global WorkArea in `beforeAll`; an intentionally failing runtime startup
correctly closed its new area, but subsequent tests reused the closed global
context. Its fixture now installs and closes a separate area per test and
restores the prior context. Its two successful embedding cases also close their
owned subsystem in `finally`. Production cleanup was not weakened.

Independent focused review found that `Files.exists` could mistake an
inaccessible tracked path for an absent one and lose retry ownership. Attribute
reading now treats only `NoSuchFileException` as absence, and a permission
restoration test covers retry. The review also corrected Given/When/Then marker
placement inside a generated sibling-isolation scenario. Retained CAR/SAR
lifetime, loader-before-files shutdown and dated placement passed source review.

Second validation passed all **116 tests in seven suites**, including compilation:
`WorkAreaSpec`, `CarRuntimeAdmissionSpec`, `ComponentRepositoryCarSpec`,
`ProcessExecutionWorkAreaSpec`, `CncfRuntimeBootstrapTerminationSpec`,
`CncfRuntimeDataStoreShutdownSpec` and `SystemNodeRuntimeResourceSpec`.
The wrapper reported `sbt_exit=0 wrapper_exit=0 process=terminated
serialization=sbt-native`. Independent correction-only M2 review closed
CB-WA-001/002/003 with PASS. The first failed result and review findings remain
retained; no full/heavy suite was run.

## Local runtime adoption

During repair, new old-style flat CAR directories continued to appear from
parallel runtime activity, and free space decreased to about 48 GiB. The new
dated tree at that observation occupied only 188 KiB. The installed
`sm-workflow` launcher selects
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT` from `ivy2Local`.

After focused validation and closure review, the primary CNCF checkout was
published at that unchanged coordinate with typed `dependency-artifact-refresh`
and exact `--batch publishLocal`. Publication and its parent-side typed
verification both succeeded. No release/version conversion, remote publication,
CAR republishing, source commit or push was performed. Concurrent source changes
were preserved; the local artifact is the current primary checkout build.

The installed `sm-workflow --host <sm-workflow-dev> --help` then exited 0.
Before/during/after observation saw a new dated WorkArea during the invocation,
and that observed directory no longer existed after termination. No new flat
entry appeared during this observation. Another new dated directory was present
afterward and was left untouched because parallel runtime activity may own it;
the observation is not a claim that every process on the machine has stopped.
The help call did not Advance or restart any Goal or Session.

Final observed capacity was approximately **48 GiB available**. Reclaimed
amount remains **66.19 GiB**; concurrent disk activity explains why final free
space differs. Already-running JVMs can retain old loaded code until they end;
this task did not stop user processes. SIGKILL/power loss can still leave
residue, which is why date partitioning remains useful. Cleanup never uses age
alone as deletion authority.

Raw recovery, validation, local publication and installed-help evidence is
retained under the same machine-local backup journal directory named above.
At that observation, code and this journal remained uncommitted. Existing metrics/quota TODOs remain
outside this incident; there are no remaining current-incident blockers.

## Ownership correction during commit preparation

Retained repository extraction must acquire its WorkArea from the same
SystemNode that owns its retained handle. Reading the mutable GlobalContext
could instead allocate beneath another runtime's area and let that runtime's
shutdown remove a still-live loader's files. The existing SystemNode resource
registry now supplies its assembly-owned area, or allocates one through that
same owner for a bare embedding. It refuses acquisition after stopping.
CAR and SAR discovery use this owner binding. This is a runtime ownership
correction; shared generations and cross-process leases remain Phase 116 work.

## Hygiene follow-up

### HYG-PURPOSE-001 — Extractor naming

- Status: OPEN; discovered 2026-10-21 during validated-commit review.
- Owner: goldenport-cncf maintenance.
- Locations: `src/main/scala/org/goldenport/cncf/component/SarExtractor.scala`
  local variables in structure resolution, and sibling `CarExtractor.scala`
  private structure-validation parameters.
- Evidence: these lowerCamel local/private-parameter names predate the current
  changes in HEAD and differ from the flatcase rule.
- Category/risk/priority: naming consistency; maintenance readability; low.
- Boundary: unrelated lexical cleanup is excluded from the ownership repair.
- Proposed task: a separate bounded extractor naming pass preserving public
  signatures and extraction behavior. No task or commit is assigned yet.

### HYG-PURPOSE-002 — Existing responsibility concentrations

- Status: OPEN; discovered 2026-10-21 during validated-commit review.
- Owner: goldenport-cncf maintenance.
- Locations: `component/repository/ComponentRepository.scala`,
  `cli/CncfRuntimeInstanceLifecyclePart.scala` under `src/main/scala/org/goldenport/cncf`,
  and `src/test/scala/org/goldenport/cncf/component/repository/ComponentRepositoryCarSpec.scala`.
- Evidence: these existing files exceed the ordinary source-size guidance and
  combine multiple repository/lifecycle or fixture responsibilities.
- Category/risk/priority: source organization; review and maintenance cost; low.
- Boundary: responsibility-based decomposition is independent of owned cleanup
  and must preserve lifecycle, admission and fixture semantics.
- Proposed task: separately design and validate a responsibility split. No task
  or commit is assigned yet.
- Deduplication: bootstrap size remains recorded as HYG-P73-MR02-001 in
  [Phase 73 follow-up](2026-10-08-phase-73-hygiene-follow-up.md); interpreter size
  remains HYG-P96-07-REMAINING-BASELINE-001 in
  [Phase 96 follow-up](2026-10-03-phase-96-hygiene-follow-up.md).

### HYG-PURPOSE-003 — Existing SBT setting diagnostics

- Status: OPEN; discovered 2026-10-21 during focused validation.
- Owner: goldenport-cncf build maintenance.
- Location: `build.sbt`, `cncfCoreDevelopmentDirectory` and
  `validateCncfCoreDevelopment` settings.
- Evidence: the existing `validateCncfCoreDevelopment := ()` declaration emits
  a deprecated argument-adaptation warning; both settings emit unused-key
  diagnostics during SBT loading.
- Category/risk/priority: build hygiene; diagnostic noise; low. Focused tests
  compile and pass despite these pre-existing diagnostics.
- Boundary: build-setting ownership is separate from the retained-extraction
  repair and current purpose-separated source changes.
- Proposed task: separately reconcile the core-development keys and their
  intentional usage before changing declarations or lint policy. No task or
  commit is assigned yet.

## Agent Usage Summary

Protected lifecycle repair and independent review used `gpt-6.1-sol` / `high`;
registered SBT execution used `gpt-6-luna` / `medium`. Parent model/effort was
not directly observable. Counts describe disclosures, including the initial
repair handoff mismatch, not individual tool calls.

| Agent | Model | Effort | Type | START | RESUME | DIRECT | Role/state and scope | Outcome |
|---|---|---|---|---:|---:|---:|---|---|
| `/root` | unknown | unknown | parent-task | 0 | 0 | 1 | DIAGNOSE / VERIFY / RUNTIME; disk recovery, ownership decisions, installed help and evidence | Completed |
| `/root/workarea_cleanup_fix` | gpt-6.1-sol | high | cncf_fix_worker_terra | 1 | 2 | 0 | FIX; eight owned source/test paths, then three correction paths | Initial no-edit skill-handoff mismatch corrected; repairs completed |
| `/root/workarea_lifecycle_review` | gpt-6.1-sol | high | default; direct-default-review | 1 | 1 | 0 | VERIFY; lifecycle boundary, then CB-WA-001/002/003 only | FINDINGS then PASS |
| `/root/sbt_receipt_8ef8927a3e9c47dbb2b6` | gpt-6-luna | medium | cncf_command_runner | 1 | 0 | 0 | SBT; first seven-suite focused validation | Terminal failure, 110/115 passed |
| `/root/sbt_receipt_32649bca6023419fa1a1` | gpt-6-luna | medium | cncf_command_runner | 1 | 0 | 0 | SBT; corrected seven-suite focused validation | Terminal success, 116/116 passed |
| `/root/sbt_receipt_226ab00e011f411ab284` | gpt-6-luna | medium | cncf_command_runner | 1 | 0 | 0 | SBT; exact local SNAPSHOT dependency refresh | Terminal success; parent publication verifier accepted |
