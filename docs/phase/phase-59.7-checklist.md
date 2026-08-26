# Phase 59.7 Checklist - Textus BoK Complementary RAG/MCP Integration

status=in_progress
phase=[Phase 59.7 - Textus BoK Complementary RAG/MCP Integration](phase-59.7.md)
predecessor=[Phase 59.6](phase-59.6.md)
successor=[Phase 59.8](phase-59.8.md)

## DOC-07: Textus BoK Complementary RAG/MCP Integration

Stage Status:
- Current status: IN PROGRESS
- Owner: Textus BoK RAG/MCP maintainers
- Update rule: Record admission, evidence, disclosure, MCP, and CBD-handoff
  evidence before representative profile work begins.
- Entry rule: Phase 59.6 DOC-06 is DONE.
- Completion rule: Component and framework semantic retrieval remain distinct,
  bounded, attributable, and capable of exact CBD/direct-Help handoff.

- [ ] Define admission resource kinds for Component manifests, structured
  framework publication/snapshot, public guidance, and Skill metadata.
- [ ] Admit SmartDox document/section metadata, RDF/JSON-LD, glossary,
  ontology, schema, and catalog without HTML scraping.
- [ ] Resolve Component-local and Documentation/SourceCode resources safely.
- [ ] Define deterministic document/section/chunk/evidence identity and
  preserve Component or framework version, digest, authority, license, path,
  publication generation, canonical URL, and indexed-at data.
- [ ] Preserve Directive/rule/profile or Skill/bundle identity, authority,
  visibility, owner, canonical URL, and digest.
- [ ] Preserve SmartDox structure/schema/API semantics; implement lexical and
  structural retrieval independent of embeddings and optional provider-boundary
  vector retrieval.
- [ ] Return exact Component/resource/section and framework identity/hash
  evidence, distinguish authority classes, and report stale snapshots.
- [ ] Add read-only MCP discovery/search/manifest/resource/section operations
  under explicit readiness; keep framework Operations and mutation/execution
  operations absent.
- [ ] Add bounded public guidance/Skill metadata retrieval and prove it cannot
  override directives, mutate configuration, install/activate Skills, or grant
  MCP authority.
- [ ] Enforce proprietary-source/caller authorization at response time and
  return exact evidence for CBD handoff.
- [ ] Preserve CBD detail/usage/comparison/review ownership and update BoK
  domain/design/spec/strategy/manual contracts.
- [ ] Add no-match, ambiguous-version, stale, forbidden, and bounded-result
  specifications.

Evidence:
- P597-S1 commit `736145992aebf60d74b6af00345328b052f2271b`: digest-bound
  semantic knowledge carrier/producer admission.
- P597-S2 P597-S2A RED receipt `71942-20260826T203708Z` (compile failed on
  absent fields as expected), followed by current GREEN receipts
  `87890-20260826T211323Z` BoK (11/11) and
  `88331-20260826T211425Z` CBD Support (2/2), each through serialized SBT
  with `lock=released`.
- Focused Step review and Phase final validation/release are pending; Phase
  59.7 is not closed.
