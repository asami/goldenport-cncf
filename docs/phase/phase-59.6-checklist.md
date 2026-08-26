# Phase 59.6 Checklist - Textus CBD Support Primary Integration

status=in_progress
phase=[Phase 59.6 - Textus CBD Support Primary Integration](phase-59.6.md)
predecessor=[Phase 59.5](phase-59.5.md)
successor=[Phase 59.7](phase-59.7.md)

## DOC-06: Textus CBD Support Primary Integration

Stage Status:
- Current status: IN_PROGRESS
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
- [ ] Preserve catalog/source identity and require exact Component/version
  selection before detailed retrieval.
- [ ] Resolve embedded and Documentation/SourceCode SubComponent resources
  safely and project configuration, Operations, schemas, manuals, examples,
  Scaladoc, source availability, and provenance.
- [ ] Return explicit absence when a source has no Component knowledge.
- [ ] Make getUsage cite exact contract/manual/example/source evidence and
  distinguish inference.
- [ ] Provide bounded read-only manifest/resource retrieval through CBD MCP.
- [ ] Add CAR Review checks for manual completeness, integrity, Scaladoc,
  source policy, Help discovery, and BoK publication readiness.
- [ ] Enforce origin, digest, size, license, authorization, and disclosure.
- [ ] Keep CBD Support independently useful without BoK and preserve BoK
  semantic evidence as separately attributable input.
- [x] Update CBD design/spec/strategy/manual contracts for the implemented
  local-source carrier boundary.  The catalog transport extension will amend
  the same documents before phase closure.

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
- `p596.catalog.cfc.focused.019`: 3 CFC carrier-codec tests passed, including
  fifty generated valid digest declarations; lock released.
