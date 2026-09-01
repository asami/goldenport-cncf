# Phase 61.5 Checklist - Information Persisted-State Migration Admission

status=closed
phase=[Phase 61.5 - Information Persisted-State Migration Admission](phase-61.5.md)
predecessor=[Phase 61.4 Checklist](phase-61.4-checklist.md)
successor=[Phase 61.5.1 Checklist](phase-61.5.1-checklist.md)

## IC-07A: Persisted-State Migration Admission

Stage Status:
- Current status: DONE
- Owner: CNCF persisted Information maintainers
- Update rule: Update only from accepted IC-07A evidence; preserve IC-06
  public projection and managed-input contracts. Phase 61.5.1 owns every
  deferred IC-07B item.
- Entry rule: IC-06 is DONE in Phase 61.4.
- Completion rule: Supported persisted Information shapes use the canonical
  generated model with deterministic migration or explicit incompatibility and
  Phase 61.5 closure evidence.

- [x] Define supported legacy persisted Information shapes.
- [x] Implement deterministic migration or explicit incompatibility
  diagnostics.
- [x] Preserve ids, lifecycle, working/raw data, candidates, bindings,
  publications, conflicts, events, audit, and revision provenance.
- [x] Add migration preview and rollback-safe failure behavior.
- [x] Complete IC-07A PMR-D design/compatibility review against a frozen
  migration dossier.
- [x] Complete IC-07A PMR-I implementation review against the accepted
  migration dossier and current focused evidence.
- [x] Complete IC-07A PMR-R focused re-review for every byte-changing PMR-I
  correction, when applicable (not required: PMR-I was clean).
- [x] Defer IC-07B downstream runtime-coordinate admission, including current
  CNCF `0.5.3-SNAPSHOT` metadata and packaged-CAR runtime admission, to Phase
  61.5.1.
- [x] Defer Textus Knowledge Editor list/detail/edit/lifecycle acceptance to
  Phase 61.5.1.
- [x] Defer Textus SIE authority resolution, publication, and materialization
  acceptance to Phase 61.5.1.
- [x] Defer book, paper, web-resource, Person, Organization, and textual
  work/edition/series/volume profile acceptance to Phase 61.5.1.
- [x] Defer Tag filtering and local Knowledge materialization acceptance to
  Phase 61.5.1.
- [x] Defer Help/API compatibility for development source and packaged CAR
  execution to Phase 61.5.1.
- [x] Defer focused downstream suites and representative end-to-end smoke
  tests to Phase 61.5.1.

Evidence:
- IC-07A migration admission: PMR-D, typed PMR-D re-review, and PMR-I PASS;
  migration focused spec 7/0 (`15623-20260901T010636Z`) and physical
  EntityStore focused spec 16/0 (`16405-20260901T010811Z`).
- `D-P61.5-IC07B-SPLIT-001` assigns every unfinished IC-07B item exactly once
  to Phase 61.5.1. No IC-07B source, test, CAR, catalog, or consumer work is
  accepted by this checklist.

## CB-P61.5-RR-001: Codec Dispatch Repair

Stage Status:
- Current status: REPAIR IMPLEMENTED; focused validation and one focused
  re-review required.
- Authority: `D-P61.5-RR-001`, repair cycle 2, Phase 61.5 only.
- Finding: revision-bound readers admitted the stored record and then selected
  the default presentation decoder, bypassing an overridden `fromStoreRecord`
  physical-store codec.
- Repair route: keep admission before revision validation; decode that admitted
  record through `fromStoreRecord` without re-admission. Information public
  reads admit and physically decode once; admitted reads physically decode once.

- [x] Preserve generic `fromStoreRecord` compatibility dispatch after admission.
- [x] Preserve revision validation before domain decoding.
- [x] Add E1 custom physical-store-codec regression for revision-bound load.
- [x] Preserve the Information legacy physical-store no-rewrite scenario.
- [x] Run selected focused validations: `P61.5-B01-VAL-004`
  (`22517-20260901T053932Z`; 5 suites / 70 succeeded / 0 failed; exit 0;
  serialized SBT lock released).
- [x] Complete the required focused re-review: PASS, manifest
  `090118be98e3b06dbe92016da5234337addd203591542dccaaeaaeb928fe6aa1`;
  `CB-P61.5-RR-001` is sealed.

Acceptance: R1 in `phase-61.5.md` holds; no physical Information record is
rewritten on read; no public API, supported shape, generated/reflection
contract, repository, or successor-Phase scope changes.

## CB-P61.5-RR-002: Admission Hook API Visibility

Stage Status:
- Current status: DONE
- Authority: `D-P61.5-RR-002`, repair cycle 3, Phase 61.5 only.
- Finding: the cycle-2 repair exposed admission hooks as new public persistence
  APIs although admission belongs to the internal EntityStore boundary.
- Repair route: restrict both admission hooks and the Information override to
  `private[cncf]`; preserve public `fromStoreRecord`, migration, revision, and
  codec-dispatch behavior.

- [x] Restrict `EntityPersistent.admitStoreRecord` to `private[cncf]`.
- [x] Restrict `EntityPersistent.decodeAdmittedStoreRecord` to
  `private[cncf]`.
- [x] Restrict both Information overrides to `private[cncf]`.
- [x] Run `P61.5-B01-VAL-006` (`49698-20260901T064628Z`; 5 suites /
  70 succeeded / 0 failed; compilation succeeded; shared SBT lock released).
- [x] Complete the final focused re-review: PASS with no Current Phase Blocker.

Acceptance: R2 in `phase-61.5.md` holds; admission remains internal and no new
public API, persisted shape, generated/reflection contract, repository, or
successor-Phase scope is introduced.

## Phase Release Gate

- [x] Complete the IC-07A protected design/compatibility and implementation
  review route.
- [x] Complete the mandatory Phase full review exactly once.
- [x] Close every Phase-review finding within three monotonically narrowing
  repair cycles and the required focused re-reviews.
- [x] Persist `HYG-P61.5-RR3-001` and `HYG-P61.5-RR3-002` in the canonical
  Phase Hygiene journal.
- [x] Accept no Development Candidate; leave its canonical journal absent.
- [x] Preserve Phase 61.5.1 and Phase 61.6 as planned/not-started successor
  work without executing them.
- [x] Run the one repository-full validation as `P61.5-RELEASE-VAL-001`,
  invocation `59047-20260901T070547Z`: 476 suites / 3,528 tests passed,
  0 failed, compilation succeeded, and the shared SBT lock was released.
- [x] Bind the distinct final release commit to
  `phase61.5-clb-ic07a-20260901`.

Closure: Phase 61.5 accepts only IC-07A persisted-state migration admission.
Every IC-07B downstream runtime/CAR/consumer item remains owned by Phase
61.5.1 and requires a fresh explicit invocation.
