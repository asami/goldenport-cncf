# Phase 59.5 Checklist - Unified Help and Direct AI Access

status=closed
phase=[Phase 59.5 - Unified Help and Direct AI Access](phase-59.5.md)
predecessor=[Phase 59.4](phase-59.4.md)
successor=[Phase 59.6](phase-59.6.md)

## DOC-05: Unified Help and Direct AI Access

Stage Status:
- Current status: DONE
- Owner: CNCF Help, HTTP, CLI, Web, and security maintainers
- Update rule: Preserve accepted route, authorization, compatibility, and
  hostile-content evidence; record later maintenance separately.
- Entry rule: Phase 59.4 DOC-04 is DONE.
- Completion rule: Humans and AI reach the same resolved resources and the
  stable consumer contract is ready for later Phase 60 consumption.

- [x] Define canonical Help manifest discovery and advertise it with stable
  relation/media type.
- [x] Navigate manuals, configuration, Operations, schemas, OpenAPI, Scaladoc,
  source, troubleshooting, provenance, model types, and diagrams.
- [x] Build manifest-based development context and consume Develop
  Documentation only through admitted Phase 58 policy without a second fetch.
- [x] Project mounted/local/remote/restricted/unavailable/incompatible/stale/
  corrupt Phase 58 states in Help and later consumer contract.
- [x] Provide structured manifest/resource HTTP retrieval and CLI inspection
  descriptors without endpoint/parser wiring.
- [x] Reconcile Help, man, OpenAPI, Web, and compatibility route descriptors; make
  help/system runtime-specific and man/system versioned local-or-online.
- [x] Separate CML/Cozy developer navigation from operator Help.
- [x] Show directive version/profile/digest/public-guide reference and Skill
  metadata/availability/compatibility/install state without restricted content
  or installation/activation.
- [x] Show framework installed/cached/online/unavailable/mismatch state without
  changing Component manual resolution or confusing latest with evidence URL.
- [x] Apply authorization/production visibility and hide physical SubComponent
  boundaries from ordinary navigation.
- [x] Publish exact read-only knowledge/model/resource contract for Phase 60
  without Admin views/actions and verify exact multi-version selection.
- [x] Add hostile content/type/cache/disclosure and online-failure
  specifications.

Evidence:
- DOC-05 is accepted in Step commit `19957d3c`: the Help/Direct-AI focused
  specifications passed before the mandatory Phase review.
- Mandatory Phase full review `P595-PHASE-FULL-REVIEW-001` found
  `CPB-P595-PHASE-001` through `003`. The user-selected A route policy,
  bounded repair, and `P595-PHASE-CLOSURE-RR-001` focused re-review converge
  with no Current Phase Blocker or Development Candidate.
- `HYG-P595-PHASE-001` is persisted as a nonblocking follow-up; it does not
  alter DOC-05 behavior or closure.
- The final `sbt --batch test` suite is the final-gate evidence for the
  distinct Phase release commit bound by `phase59.5-clb-doc05a-20260826`.
