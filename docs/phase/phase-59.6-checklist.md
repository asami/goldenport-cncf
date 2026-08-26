# Phase 59.6 Checklist - Textus CBD Support Primary Integration

status=closed
phase=[Phase 59.6 - Textus CBD Support Primary Integration](phase-59.6.md)
predecessor=[Phase 59.5](phase-59.5.md)
successor=[Phase 59.7](phase-59.7.md)

## DOC-06: Textus CBD Support Primary Integration

Stage Status:
- Current status: DONE
- Owner: Textus CBD Support and CNCF Component knowledge maintainers
- Update rule: Record exact source, digest, authorization, MCP, and CAR Review
  evidence before BoK integration begins.
- Entry rule: Phase 59.5 DOC-05 is DONE.
- Completion rule: CBD Support uses exact manifests/resources as primary
  evidence for Component detail, usage, MCP, and CAR Review.

- [x] Admit manifest identity/location/digest from catalog, development
  directory, warehouse CAR, cache, and exact Component observations.
  Development, warehouse CAR, and cache CAR are implemented.  Catalog carriage
  uses a version-scoped explicit consumer-contract sidecar and passed focused
  producer, BOK, parser, and CBD admission validation under
  `P596-CATALOG-CARRIER-001`.
- [x] Preserve catalog/source identity and require exact Component/version
  selection before detailed retrieval.
- [x] Resolve embedded and Documentation/SourceCode SubComponent resources
  safely and project configuration, Operations, schemas, manuals, examples,
  Scaladoc, source availability, and provenance.
- [x] Return explicit absence when a source has no Component knowledge.
- [x] Make getUsage cite exact contract/manual/example/source evidence and
  distinguish inference.
- [x] Provide bounded read-only manifest/resource retrieval through CBD MCP.
- [x] Add CAR Review checks for manual completeness, integrity, Scaladoc,
  source policy, Help discovery, and BoK publication readiness.
- [x] Enforce origin, digest, size, license, authorization, and disclosure.
- [x] Keep CBD Support independently useful without BoK and preserve BoK
  semantic evidence as separately attributable input.
- [x] Update CBD design/spec/strategy/manual contracts for the implemented
  local-source and catalog-transport carrier boundary.

Evidence:
- `p596.a2.cbd.carrier-digest-fix.green.001`: 30 focused CBD carrier tests
  passed, lock released.
- `p596.phase.final.cncf.test.001`: 3,405 CNCF tests passed, lock released.
- `p596.phase.final.cbd.test.001`: 304 CBD Support tests passed, lock
  released.
- `p596.phase.final.cozy.test.001`: 1,461 Cozy tests passed and four unrelated
  video tests failed; see `P596-COZY-VALIDATION-001` in the phase document.
- `p596.catalog.cbd.focused.009`: 31 catalog-parser and CBD-admission focused
  tests passed, lock released.
- `p596.catalog.cozy.focused.012`: 15 Cozy publisher-sidecar focused tests
  passed, lock released.
- `p596.catalog.cozy.focused.016`: 8 Cozy BOK transport focused tests passed,
  lock released.
- `p596.catalog.cbd.focused.017`: 6 CBD catalog carrier-projection tests
  passed, including explicit public absence/rejection projection; lock
  released.
- CNCF core carrier-codec focused validation: 3 tests passed, including
  fifty generated valid digest declarations; lock released.
- Repair cycle 1 focused validation: Cozy producer/BOK suites passed 29/29
  (`89508-20260826T072949Z`); CBD local carrier/action suites passed 18/18
  (`91167-20260826T073155Z`).  Both wrapper locks were released.
- The mandatory Phase review found `CPB-P596-001` and `CPB-P596-002`; repair
  cycle 1 and its independent closure review verified both code blockers as
  fixed.  User decision `D-P596-RR-CAR-LINT-GATE-001` retains normal CBD CAR
  lint at the final release gate and does not authorize a second full review.
- Final release evidence is normal CBD CAR lint and one full `sbt --batch test`
  suite in CNCF core, Cozy, and CBD Support.  The distinct closure binding is
  `phase59.6-clb-doc06-20260826`.
- Final release receipts: normal CBD CAR lint reported no failure (two
  pre-existing warnings: missing ABI baseline and development `sbt-cozy`
  SNAPSHOT); full suites passed 3,406 CNCF core tests
  (`6576-20260826T080423Z`), 1,469 Cozy tests
  (`7836-20260826T080749Z`), and 308 CBD Support tests
  (`10121-20260826T081049Z`).  Every wrapper reported `lock=released`.
- `HYG-P596-001` and `DEV-P596-001` are persisted separately; neither expands
  DOC-06 acceptance.
