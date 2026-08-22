# Phase 58.8 - Component-Composition End-to-End Validation

status=planned
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.7](phase-58.7.md)
successor=[Phase 58.9](phase-58.9.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.8 Checklist](phase-58.8-checklist.md)
consumes_handoff=RSC-08 common Help/Admin consumer contract and acceptance fixtures

## Goal

Validate every admitted Component-composition profile across its owning
repositories and downstream fixtures before canonical documentation promotion.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-08 common consumer contract and profile fixtures
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3–5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Exercise embedded, Documentation/Source, external-platform, restricted,
  development, repository, offline, and primary-only profiles.
- Exercise unavailable, incompatible, duplicate, unsafe, stale, corrupt,
  lifecycle, restart, multi-instance, and concurrency outcomes.
- Run the frozen focused/full repository and representative CAR-lint evidence.

## Decision Resolution Record

- decision_id: `D-58.8-RSC09-LOCAL-CACHE-001`
- answer_identity: developer decision in this Phase 58.8 execution task
- selected_option: `AUTHORIZE_LOCAL_SNAPSHOT_CACHE_CAPTURE`
- affected_phase_and_tree_identities: Phase 58.8; CNCF `3961ee315374`;
  sbt-cozy `a1e239abafd4`; cncf-samples `c601252b23b2`; textus-sample-apps
  `3202c0204e57`
- authorized_next_state: RSC09 local fixture capture, source-removal, and
  cache-only consumer-resolution validation; no remote publication,
  deployment, or fabricated provider fallback
- consumed: true

### D-58.8-RSC09-CONSUMER-DEP-001

- answer_identity: developer decision in this Phase 58.8 execution task
- selected_option:
  `AUTHORIZE_LOCAL_CNCF_0.5.3_SNAPSHOT_PUBLICATION_AND_EPHEMERAL_CWITTER_OVERRIDE`
- affected_phase_and_tree_identities: Phase 58.8; CNCF `3961ee315374`;
  textus-sample-apps `3202c0204e57`
- authorized_next_state: publish the current CNCF `0.5.3-SNAPSHOT` only to
  the local development artifact cache, then run the registered Cwitter
  acceptance with the process-local `CNCF_VERSION=0.5.3-SNAPSHOT` override;
  no remote publication, deploy, push, or persistent Cwitter dependency change
- consumed: true

### D-58.8-RSC09-CWITTER-ENTITY-ACCESS-001

- answer_identity: developer decision in this Phase 58.8 execution task:
  `受け入れテストは保留して、コンポーネントの対応が終わってからがよいと思う`
- selected_option: `DEFER_CWITTER_CONSUMER_ACCEPTANCE`
- affected_phase_and_tree_identities: Phase 58.8; RSC09-01C Cwitter consumer
  acceptance; deferred Sanpomap replacement; CNCF `3961ee315374`;
  textus-sample-apps `3202c0204e57`; sbt-cozy `a1e239abafd4`;
  cncf-samples `c601252b23b2`
- authorized_next_state: return to `PARENT_CAPABILITY_CHECK` for a material
  Phase replan. In line with the same decision's explicit rationale, do not
  migrate Cwitter, substitute Sanpomap, run downstream CAR acceptance, delete
  the preserved Cwitter worktree draft, or treat its evidence as Phase
  acceptance before component migration is complete.
- consumed: true

### D-58.8-RSC09-ARTSCENE-CONSUMER-001

- answer_identity: developer instruction in this Phase 58.8 execution task:
  `art-sceneがpublishされたので、これを使って作業を進めて。`
- selected_option: `ADOPT_PUBLISHED_ARTSCENE_AS_ADMITTED_RSC09_CONSUMER_CANDIDATE`
- affected_phase_and_tree_identities: Phase 58.8 RSC09 consumer replan;
  CNCF `3961ee315374`; ArtScene published CAR `textus-art-scene:0.1.2.1`;
  ArtScene `24e9671db6f3`
- authorized_next_state: return to `PARENT_CAPABILITY_CHECK` for a material
  Phase replan that may admit ArtScene consumer acceptance. Preserve the
  deferred Cwitter/Sanpomap boundary until a frozen ArtScene acceptance map,
  dependency coordinate, and executable entrypoint prove the replacement or
  complementary consumer role.
- consumed: true

## RSC09-01D ArtScene Complementary Consumer Boundary

RSC09-01D admits the published ArtScene CAR only as complementary release
consumer evidence: `org.simplemodeling.textus:ArtScene:0.1.2.1` at the
workspace-local CAR repository path has the frozen archive SHA-256
`366fb3d3d75683e55cc6b73ee72f2ed1ac60937079f8a7141bf086441fbe1b66`. The
acceptance asserts the real release archive is returned directly from that
coordinate-relative path and that the same archive yields the real
`component/main.jar` under the canonical runtime-dependency path. ArtScene
does not replace the canonical synthetic `RscParent` plus its three-child
fixture, and it makes no composition or provider-API claim. Cwitter and the
Sanpomap substitute remain deferred under
`D-58.8-RSC09-CWITTER-ENTITY-ACCESS-001`.

## Closure

Focused and full validation proves the agreed embedded, split, restricted,
development, repository, offline, primary-only, failure, lifecycle, and
consumer profiles. Phase 58.9 consumes the exact evidence ledger only.

## Non-Goals

New architecture, behavior, APIs, or scope expansion discovered during final
validation; those require the appropriate decision or follow-up workflow.
