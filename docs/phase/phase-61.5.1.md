# Phase 61.5.1 - Information Downstream Runtime and Consumer Acceptance

status=active
planned_at=2026-09-01
split_from=[Phase 61.5](phase-61.5.md)
depends_on=[Phase 61.5](phase-61.5.md)
successor=[Phase 61.6](phase-61.6.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 61.5.1 Checklist](phase-61.5.1-checklist.md)
consumes_handoff=accepted IC-07A persisted-state migration dossier, focused evidence, and Phase 61.5 release closure

## Goal

Complete IC-07B: accept the canonical generated Information model across
downstream runtime-coordinate, generated dependency identity, packaged
CAR/catalog, and representative Textus consumer boundaries.

Phase Plan Gate: PROCEED
- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted IC-07A persisted migration and
  Phase 61.5 release closure
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6--7h
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: this distinct downstream closure avoids repeating the
  IC-07A persisted-state review and keeps generated runtime/CAR compatibility
  independently testable
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 61.5

## Scope

- Align current Cozy, CNCF, Textus Knowledge Editor, and Textus SIE development
  coordinates without conflating generation compatibility and runtime
  compatibility.
- Normalize generated CAR dependency identity assertions to the canonical
  namespace/id/version representation.
- Validate generated source and packaged CAR/catalog runtime admission.
- Validate Knowledge Editor and SIE representative Information flows, domain
  profiles, Tag/Knowledge materialization, and Help/API compatibility.

## Closure

The complete IC-07 downstream acceptance evidence proves that generated source
and packaged CAR execution consume the canonical Information model with no
silent coordinate, identity, or compatibility ambiguity. Phase 61.6 consumes
that complete IC-07 handoff for duplicate removal and canonical closure.

## Non-Goals

Persisted-shape migration policy, new Information capability, generator
redesign, duplicate removal, canonical design/specification promotion, or
Phase 62 work.

## Current Status

Active. Slice A (Textus Knowledge Editor) and Slice B (Textus SIE development
metadata and representative consumers) are accepted into the IC-07B Step
accumulator. Cozy remains an IC-07B validation input at its current
`0.3.3-SNAPSHOT`/`0.5.3-SNAPSHOT` coordinates; its unrelated dirty worktree is
preserved and is not owned by this Phase. Textus SIE `3d4c402` remains the
packaged-admission checkpoint input, while the current one-file Slice B repair
is uncommitted accepted Step evidence. C3 — Sealed Launcher and Offline
Component Dependency Closure — is allocated in IMPLEMENT for the isolated
packaged CAR/catalog runtime pathway. This Phase started after Phase 61.5
closed and the user explicitly invoked `$cncf-goal-phase 61.5.1`.

## Decision Resolution — Validation Worktree Collision

- `decision_id=D-P61.5.1-IC07B-VALIDATION-001`
- `resolved_by=user`, `resolved_at=2026-09-01`
- `answer=The unrelated Cozy Logical UI compilation errors are fixed; resume
  Phase 61.5.1 validation.`
- `affected_phase=61.5.1`, `affected_step=IC-07B-01`,
  `affected_slice=IC-07B-01A`
- `prior_blocker=Untracked Cozy Logical UI source had prevented focused Cozy
  compilation before IC-07B acceptance could execute.`
- `current_evidence=The prior five-error source locations are no longer present
  in the current working tree; rerun the same frozen focused Cozy command with
  a fresh single-use authorization attempt.`
- `authorized_next_state=IMPLEMENT`
- `consumed=true`

## Decision Resolution — Packaged CAR Admission Boundary

- `decision_id=D-P61.5.1-IC07B-PACKAGED-CAR-001`
- `resolved_by=user`, `resolved_at=2026-09-01`
- `answer=推奨案で進めて (Proceed with the recommended isolated packaged
  admission boundary.)`
- `affected_phase=61.5.1`, `affected_step=IC-07B-01`,
  `affected_slice=IC-07B-01C`
- `decision=Create isolated clean worktrees only for the compatible BoK and
  Scraper inputs; preserve their current user worktrees. Publish only the
  exact local warehouse CARs through serialized, bound SBT execution, then
  run a runner-safe packaged runtime smoke.`
- `prohibited=No mutation of current BoK/Scraper worktrees, no remote
  publication, no successor Phase work, and no unbound raw SBT execution.`
- `authorized_next_state=PLAN`
- `consumed=true`

## Decision Resolution — Packaged Runtime Launcher Boundary

- `decision_id=D-P61.5.1-IC07B-PACKAGED-RUNTIME-LAUNCHER-002`
- `resolved_by=user`, `resolved_at=2026-09-02`, `answer=Aを承認する`
- `affected_phase=61.5.1`, `affected_step=IC-07B-01`,
  `affected_slice=IC-07B-01C`
- `decision=Add cncf-launcher as a Phase 61.5.1 mutation repository and add
  a formal prebuilt runtime-bundle input. Textus SIE must use that input for
  packaged admission; it must not present a bundle as a development checkout
  or introduce a source-classpath fallback.`
- `frozen_baseline=cloud-native-component-framework d1076017ef2be03bfa541b5cd22e4e09d163fbc4;
  cncf-launcher 548f2eedb4d98ef3b9aa07cc93484126b352b813 with seven pre-existing
  unowned modified files (build.sbt, CncfLauncherServerSupport.scala,
  CncfLocalServerEvidence.scala, CncfTextusControlCenterStandaloneLocator.scala,
  CncfLauncherRuntimeSpec.scala, CncfLauncherRuntimeSpecSupport.scala, and
  CncfLauncherSpecSupport.scala).`
- `preservation=The existing cncf-launcher changes are not Phase-owned and
  must be retained. Exact owned paths, public contract, validation, and
  consumer boundary remain subject to the required capability check and
  replanning.`
- `authorized_next_state=PARENT_CAPABILITY_CHECK`
- `consumed=true`

## Decision Resolution — Packaged SAR Identity Migration

- `decision_id=D-P61.5.1-IC07B-PACKAGED-SAR-IDENTITY-003`
- `resolved_by=user`, `resolved_at=2026-09-02`, `answer=変更対象に追加して`
- `affected_phase=61.5.1`, `affected_step=IC-07B-01`,
  `affected_slice=IC-07B-01C2`
- `decision=Add textus-bok as a Phase 61.5.1 mutation and commit repository.
  Migrate only examples/bok-codex-sar/subsystem-descriptor.yaml from the
  legacy component/coordinate representation to the canonical
  namespace/id/version representation, regenerate the task-private SAR, and
  rerun the same isolated prebuilt packaged-runtime session.`
- `preservation=All pre-existing textus-bok worktree changes remain unowned and
  must be retained. The exact owned BoK production input is only
  examples/bok-codex-sar/subsystem-descriptor.yaml; task-private warehouses,
  SARs, runtime bundles, containers, and session outputs are validation
  artifacts, not repository-owned source changes.`
- `authorized_next_state=PARENT_CAPABILITY_CHECK`
- `consumed=true`

## Decision Resolution — Closed Runtime Inputs

- `decision_id=D-P61.5.1-IC07B-CLOSED-RUNTIME-INPUT-004`
- `resolved_by=user`, `resolved_at=2026-09-02`
- `affected_phase=61.5.1`, `affected_step=IC-07B-01`,
  `affected_slice=IC-07B-01C3`
- `approval=The user approved cloud-native-component-framework,
  cncf-launcher, textus-semantic-integration-engine, and textus-bok as the
  four mutation/commit repositories for the sealed-launcher and offline
  component-cache design.`
- `root_cause=The thin launcher and runtime jsoup resolution consumed the
  packaged readiness observation budget and timed out.`
- `selected_design=Prepare a sealed direct cncf executable closure from the
  task-private local-Ivy/channel source, prepare a separate task-private
  org.jsoup:jsoup:1.18.1 Coursier cache, bind both root manifests by
  SHA-256, and execute the closure directly with component resolution in
  offline mode. Runtime receives neither a launcher installation path nor a
  dependency-resolution fallback.`
- `authorized_next_state=IMPLEMENT`
- `consumed=true`

## Slice C3 — Sealed Launcher and Offline Component Dependency Closure

- `state=REPLAN`; `acceptance=NOT_YET_CLAIMED`; `repair_class=M2`
- `scope=Framework offline command construction, sealed launcher preparation,
  task-private Scraper jsoup cache, SIE preflight/compose runtime, and the
  corresponding static executable specifications.`
- `supersession=C3 supersedes C2 only for the packaged runtime pathway. C2's
  static source input boundary remains preserved and uses local Ivy, not
  Maven.`
- `preservation=The BoK canonical standalone/security descriptor remains the
  phase-owned validation input with no C3 content change. C1 bundle behavior,
  C2 static input boundary, and all unrelated dirty paths remain preserved.`
- `transparency=The prior PLAN agent-use disclosure omitted required fields.
  That disclosure is incomplete and is not used as authority, acceptance, or
  validation evidence for C3.`
- `review_blocker_ledger=CB-P61.5.1-IC07B-C3-001 (thin launcher closure),
  CB-P61.5.1-IC07B-C3-002 (incomplete component-cache sealing),
  CB-P61.5.1-IC07B-C3-003 (asymmetric input-root exclusion), and
  CB-P61.5.1-IC07B-C3-004 (non-semantic executable-probe scenarios), and
  CB-P61.5.1-IC07B-C3-RR2-001 (prebuilt runtime bundle path escape),
  CB-P61.5.1-IC07B-C3-FR-001 (runtime-classpath JAR preflight),
  CB-P61.5.1-IC07B-C3-FR-002 (inherited BoK source-fixture mount), and
  CB-P61.5.1-IC07B-C3-FR-003 (warehouse source/target overlap),
  CB-P61.5.1-IC07B-C3-RR3-001 (runtime-bundle root provenance), and
  CB-P61.5.1-IC07B-C3-RR3-002 (derived warehouse CAR containment).`
- `re_review_outcome=The focused C3 re-review sealed CB-P61.5.1-IC07B-C3-001
  through -004, then found that a runtime-bundle lib symlink can escape the
  bundle and that SIE preflight does not contain component.d CAR/SAR symlink
  targets. This directly violates the sealed-runtime no-source/shared-fallback
  invariant and requires a full Phase review after repair.`
- `repair_boundary=The repair is limited to CncfRuntimeBundle containment and
  its executable specification, plus SIE preflight containment and its
  executable preflight scenarios. These are existing C1/C3 interfaces in the
  already approved framework, launcher, and SIE repositories; no API,
  coordinate, repository, or design expansion is admitted.`
- `full_review=The required full review found that preflight does not contain
  classpath JAR entries before Docker, inherited compose still mounts a BoK
  source checkout, and the task-private warehouse can overlap a source or
  target root. C3 remains unaccepted.`
- `replan=The BoK fixture is a sealed child of the already admitted
  task-private runtime bundle, not a new mount root or source-checkout runtime
  input. Preparation copies it into `bok-fixture/` and emits a complete
  fixture manifest whose SHA-256 is an explicit prebuilt admission input.
  Preflight verifies every classpath JAR, the fixture manifest, warehouse
  source/target exclusion, and the merged compose replacement before Docker.
  The prebuilt compose route mounts only the bundle-contained fixture at
  `/workspace/bok-fixture`; it never mounts the BoK checkout.`
- `cycle_1_result=The full-review repair sealed FR-001 through FR-003, but the
  required focused closure re-review found two local SIE provenance gaps:
  `TEXTUS_SIE_PREBUILT_RUNTIME_BUNDLE` was not subjected to the same
  task-private source/shared-root exclusion, and project metadata could derive
  a CAR path outside the canonical task-private warehouse. The C3 baseline,
  interfaces, repositories, and public contract are unchanged; no additional
  full review is required.`
- `cycle_2_replan=Apply one bounded SIE-only repair. Canonicalize and admit the
  runtime-bundle root with the prebuilt-input-root policy before any compose
  mount. Canonicalize the project-metadata-derived CAR path and require it to
  remain under the canonical warehouse root. Extend the static contract probe
  with shared-root/runtime-bundle rejection and metadata-path traversal
  rejection scenarios.`
- `next=Run the focused static validation, diff verification, and CAR lint for
  the exact cycle-2 repair, then perform one typed focused closure re-review.
  Do not start a packaged runtime session until that review passes. No passing
  acceptance evidence is claimed by this record.`
- `cycle_2_result=The typed focused closure re-review passed. Runtime-bundle
  roots now use task-private source/shared-root exclusion before bundle
  consumption, and metadata-derived CAR paths are canonicalized and contained
  under `repository/car` before checksum access. The static probe, `bash -n`,
  `git diff --check`, and CAR lint have no C3 failure; C3-external CAR lint
  warnings remain preserved.`
- `next_runtime_gate=The local-recursive workflow may now create its own
  task-private packages and run the prebuilt-only packaged-runtime admission
  session. This static closure is not itself a live runtime receipt, and it
  authorizes neither publication nor commit.`

## Packaged Admission Preflight — Runner-Safe Smoke Required

- `evidence_id=P61.5.1-IC07B-C-PREFLIGHT-001`
- `recorded_at=2026-09-02`
- `workflow=cncf-car-publish-local-recursive`
- `result=PACKAGED_SMOKE_ENTRYPOINT_NOT_RUNNER_SAFE`
- `entrypoint=textus-semantic-integration-engine/docker/ks-14/scripts/run-packaged-car-mcp-demo.sh`
- `entrypoint_sha256=bf13996f1cde426720aa49c2096195c8b4d5ad297dc44b1426a39d02c85dd258`
- `reason=The only packaged smoke candidate invokes SBT for CAR publication and
  runtime-classpath preparation, so it cannot be delegated as a prebuilt-only
  runtime session.`
- `preservation=No isolated worktree, warehouse, build output, runtime,
  container, port, session, or dataset was created; only the rejection receipt
  was retained.`
- `next_slice=IC-07B-01C adds a project-owned prebuilt/runtime-only smoke
  boundary. It must use only task-private packaged inputs and reject all
  source-classpath and shared/public-warehouse fallback.`

## Slice C Acceptance — Prebuilt Packaged Smoke Boundary

- `evidence_id=P61.5.1-IC07B-01C-ACCEPT-001`
- `accepted_at=2026-09-02`
- `scope=textus-semantic-integration-engine packaged MCP smoke --prebuilt`
- `result=ACCEPTED_STATIC_BOUNDARY`
- `contract=The zero-argument legacy flow remains isolated. The `--prebuilt`
  flow requires the seven task-private inputs, including the launcher
  warehouse, canonicalizes every accepted
  warehouse/bundle/artifact path, rejects shared warehouse and SIE/Scraper/BoK
  source-target provenance (including nested runtime-classpath aliases), and
  completes checksum/byte-equality admission before Docker ownership checks.`
- `validation=bash -n, git diff --check, and
  SIE_PREBUILT_PACKAGED_CONTRACT_OK; no SBT, Docker, network, publication, or
  runtime session was started.`
- `review=Sol full review plus two typed Luna xhigh focused re-reviews; final
  cycle=PASS.`
- `remaining=The local-recursive workflow may now perform its own isolated
  package/build preparation and then invoke this prebuilt-only smoke. Static
  acceptance is not a packaged-runtime admission receipt.`
- `runtime_supersession=C3 replaces the C2 launcher-install runtime input only.
  This C static-boundary receipt remains historical admission evidence and does
  not authorize the obsolete launcher warehouse at runtime.`

## Slice C1 Acceptance — Formal Prebuilt Runtime-Bundle Interface

- `evidence_id=P61.5.1-IC07B-01C1-ACCEPT-001`
- `accepted_at=2026-09-02`
- `scope=cncf-launcher direct execute --runtime-bundle-dir interface`
- `result=ACCEPTED_FOCUSED_INTERFACE_EVIDENCE`
- `contract=The launcher accepts the bundle only for direct execution, rejects
  ambiguous runtime-selection inputs, derives its classpath solely from
  target/cncf.d/runtime-classpath.txt, admits only real JARs contained below
  bundle lib/, and requires exactly one schemaVersion=1 cncf runtime
  descriptor before component compatibility and invocation. It does not
  require build.sbt, development runtime evidence, a resolver, or a source
  classpath fallback.`
- `validation=P61.5.1-IC07B-01C1-VAL-004: 5 succeeded, 0 failed;
  cncf.launcher.CncfRuntimeBundleSpec; SBT/wrapper exit 0 and lock released.
  The focused repair lineage was limited to its new executable specification.`
- `preservation=Seven pre-existing cncf-launcher dirty paths, including
  build.sbt, remain unowned and unchanged by this Phase.`
- `remaining=C2 preserves its static local-Ivy source boundary. C3 now owns
  the runtime executable closure and offline component-cache path that must
  obtain the later live packaged runtime receipt.`

## Slice C2 Static Acceptance — Packaged Launcher Consumer Route

- `evidence_id=P61.5.1-IC07B-01C2-STATIC-ACCEPT-001`
- `accepted_at=2026-09-02`
- `scope=Textus SIE --prebuilt packaged runtime launcher composition`
- `result=ACCEPTED_STATIC_CONSUMER_EVIDENCE`
- `contract=At C2 static acceptance, prebuilt SIE required a canonical task-private launcher
  warehouse containing local Ivy and the textus Coursier channel.
  Only prebuilt mode composes the launcher override. The override resolves
  cncf from that local channel, invokes its absolute installed executable,
  and supplies --runtime-bundle-dir; it contains no SBT, textus executable,
  runtime-dev-dir, or source-checkout fallback. Legacy compose and image paths
  remain unchanged.`
- `validation=SIE_PREBUILT_PACKAGED_CONTRACT_OK, bash -n, and git diff --check
  all passed. No Docker session was started by this static gate.`
- `remaining=C3 supersedes this C2 installation route only for the runtime
  pathway. C2 remains the preserved static source boundary; its prior
  task-private local-Ivy artifact attempt is excluded from acceptance because
  the command runner did not propagate its declared local publish destination
  and wrote existing default local outputs instead. Those outputs are
  preserved, uncommitted, and never used as Phase evidence.`

## Slice B Acceptance — SIE Development Metadata and Consumer Boundary

- `evidence_id=P61.5.1-IC07B-01B-ACCEPT-001`
- `accepted_at=2026-09-02`
- `scope=Textus SIE current development coordinate, canonical Scraper CAR
  dependency identity, generated MCP consumer catalog, and typed Information
  provider acceptance.`
- `result=ACCEPTED_STEP_ACCUMULATOR_EVIDENCE`
- `validation=P61.5.1-IC07B-B-VAL-006 and -007 each ran the three focused SIE
  specifications; final result 37 succeeded, 0 failed, SBT/wrapper exit 0,
  and lock released. Current-tree CAR lint had no FAIL.`
- `review=One independent Luna-high lightweight Step review found and froze two
  P2 spec-only blockers. The one-file M2 repair received a typed Luna-high
  focused re-review, which passed with both blockers resolved and no new
  findings.`
- `preservation=Existing CAR lint warnings (ABI baseline, development
  sbt-cozy, nominal CML wrappers, and manual/user-guide gaps), the stale
  README coordinate note, and all successor planning paths remain separate and
  unmodified by this Slice.`
- `remaining=Slice C isolated packaged CAR/catalog admission is still required;
  this acceptance does not claim a packaged runtime execution receipt.`

## Split Provenance

`D-P61.5-IC07B-SPLIT-001` and the explicit `$cncf-split-phase Phase 61.5`
invocation moved every unfinished IC-07B item here. The move preserves the
accepted IC-07A migration history in Phase 61.5 and does not authorize this
Phase's implementation, validation, review, or commit.
