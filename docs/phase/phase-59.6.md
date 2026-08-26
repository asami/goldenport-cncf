# Phase 59.6 - Textus CBD Support Primary Integration

status=in_progress
split_from=[Phase 59](phase-59.md)
depends_on=[Phase 59.5](phase-59.5.md)
successor=[Phase 59.7](phase-59.7.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 59.6 Checklist](phase-59.6-checklist.md)
consumes_handoff=accepted DOC-05 manifest/resource discovery and read-only consumer contract

## Goal

Implement DOC-06: make Textus CBD Support the primary exact Component
detail, usage, MCP, and CAR Review integration over the accepted manifest and
resource contract.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted DOC-05 manifest/resource discovery and read-only consumer contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 59

## Decision Record

- Decision ID: `D-P596-MANIFEST-CARRIER-001`
- Date: 2026-08-26
- Authority: user-selected carrier approach in the Phase 59.6 execution task
- Decision: CNCF and Cozy/sbt-cozy provide an explicit Component knowledge
  carrier.  The carrier declares the consumer-contract schema, logical path,
  and SHA-256 digest; CBD Support consumes only that declared evidence.
- Boundary: CBD Support does not ambiently scan for manifests, construct a
  second resolver, or return raw resource bytes.  Its read-only detail, usage,
  MCP, and CAR Review projections use the admitted, value-only resource
  evidence and preserve its authorization, disclosure, license, origin, size,
  and digest metadata.
- Repository roles: `cloud-native-component-framework`, `cozy`, and
  `sbt-cozy` are carrier mutation/commit repositories; `textus-cbd-support`
  is the carrier consumer mutation/commit repository.  Existing unrelated
  working-tree changes in all repositories remain preserved.
- Authorized next state: `PARENT_CAPABILITY_CHECK`
- Consumed: true

## Decision Resolution Record

- Decision ID: `D-P596-PHASE-SPLIT-001`
- Date: 2026-08-26
- Authority: user-authored Phase 59.6 execution instruction: 「分割しないでまとめて実行して。」
- Selected option: keep Phase 59.6 as an explicit oversized Phase and deliver
  the approved carrier, CBD Support consumer, and CAR Review integration as
  one coherent release boundary.
- Affected identity: Phase 59.6, decision record
  `D-P596-MANIFEST-CARRIER-001`, current carrier-contract planning tree.
- Authorized next state: `PARENT_CAPABILITY_CHECK`
- Consumed: true

- Decision ID: `D-P596-COZY-PRODUCER-001`
- Date: 2026-08-26
- Authority: user-authored Phase 59.6 execution instruction: `publishLocalしよいよ`.
- Selected option: publish the current Cozy `0.3.3-SNAPSHOT` to the normal
  local artifact repository, without external upload, so the existing
  Coursier-based CAR resolver can validate the carrier-producing runtime.
- Affected identity: Phase 59.6 Step 1 `carrier-publication`, failing gate
  `P596-S01-VAL-SBTCOZY-001`, Cozy runtime provider, and the sbt-cozy
  development-runtime-evidence scripted fixture.
- Authorized next state: `IMPLEMENT`
- Consumed: true

- Decision ID: `D-P596-STEP-REVIEW-GATE-001`
- Date: 2026-08-26
- Authority: user-authored Phase 59.6 execution instruction: `re-planして`.
- Selected option: treat the carrier as a protected Phase contract.  Re-plan
  the carrier accumulator, its integration edges, and its commit boundary so
  that they receive the one mandatory full Phase review rather than an invalid
  lightweight Step review.
- Affected identity: Phase 59.6 Step 1 `carrier-publication`, its public
  carrier contract, Cozy/sbt-cozy producer integration, and the final Phase
  review/commit plan.
- Authorized next state: `PARENT_CAPABILITY_CHECK`
- Consumed: true

- Decision ID: `D-P596-CATALOG-CARRIER-001`
- Date: 2026-08-26
- Authority: Phase 59.6 re-plan after inspection of the existing Cozy
  repository/BOK catalog flow.
- Selected transport: Cozy publishes the raw consumer contract as a
  version-scoped sidecar under the repository catalog.  The BOK builder checks
  those bytes against the archive descriptor's explicit carrier and publishes
  exactly one public contract URI alongside the carrier declaration in the
  selected version metadata.  CBD Support fetches that URI only after exact
  profile selection, admits it by Component/release/digest, and retains values
  only.
- Rejected alternatives: archive unpacking by CBD Support, generic sidecars,
  ambient filename scans, arbitrary URI input, a second resolver, and raw
  resource output.
- Affected identity: Phase 59.6 catalog admission, published BOK metadata,
  `getComponent`, `getUsage`, MCP detail, and CAR Review value input.
- Authorized next state: IMPLEMENT
- Consumed: true

## Scope

- Admit exact manifest identity/location/digest from supported observations and
  resolve permitted embedded and SubComponent resources safely.
- Provide detail, usage, bounded read-only MCP, and CAR Review evidence with
  origin, disclosure, license, and authorization enforcement.
- Preserve CBD Support independence from BoK and keep BoK evidence separately
  attributable.

## Progress Evidence

- CNCF carrier codec and Cozy producer-side archive/development evidence are
  implemented.  The carrier is explicit, fixed to `component-knowledge.json`,
  and digest-bound.
- CBD Support accepts descriptor-declared development, local-warehouse, cache
  CAR, and selected published-catalog carrier evidence as a value-only
  contract.  The catalog transport passed focused producer, BOK, parser, and
  CBD-admission tests; carrier absence or rejection is explicit in the public
  component and usage responses.  It projects exact
  detail, usage references, read-only MCP records, and deterministic CAR
  Review metadata without raw resource output or resolver authority.
- 2026-08-26 focused CBD carrier validation passed 30 tests.  The final CNCF
  full test passed 3,405 tests and the final CBD Support full test passed 304
  tests.  The strict carrier codec and its generated valid-digest property
  validation passed three focused tests.
- The focused carrier re-review passed: the core format remains declaration
  only; Cozy/BOK preserves the checked version-scoped transport; and CBD has
  no archive fallback, ambient scan, arbitrary endpoint, or raw-content
  projection path.

## Open Closure Conditions

- `P596-PHASE-RELEASE-VALIDATION-001`: the current carrier tree needs the
  normal final full CFC and CBD Support suites immediately before phase
  release.  Focused results are not a replacement for that release gate.
- `P596-COZY-VALIDATION-001`: the final Cozy full test currently has four
  failures in the separately dirty video storyboard/profile work.  Each fails
  direct-path validation for a target or temporary directory; the Phase 59.6
  carrier specs are not among the failures.  The phase must not close until
  the shared Cozy worktree passes its full test suite.

## Closure

CBD Support has accepted exact-detail, usage, MCP, and CAR Review behavior.
Phase 59.7 consumes the documented CBD handoff boundary.

## Non-Goals

BoK semantic retrieval, Phase 60 behavior, unrelated catalog/runtime changes,
and final cross-repository release closure.
