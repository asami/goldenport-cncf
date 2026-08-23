# Phase 59.3 Checklist - Documentation Authoring and Content Packaging Toolchain

status=planned
phase=[Phase 59.3 - Documentation Authoring and Content Packaging Toolchain](phase-59.3.md)
predecessor=[Phase 59.2](phase-59.2.md)
successor=[Phase 59.4](phase-59.4.md)

## DOC-03: Authoring and Content Packaging Toolchain

Stage Status:
- Current status: PLANNED
- Owner: Cozy/sbt-cozy and SmartDox maintainers
- Update rule: Record owning-toolchain focused evidence and source/package
  equivalence before handing resources to Phase 59.4.
- Entry rule: Phase 59.2 DOC-02 is DONE.
- Completion rule: Required manuals, projections, Scaladoc, and filtered
  release source are validated and handed through the already accepted closed
  Phase 58 packaging/resource contract to Phase 59.4, without reopening or
  modifying Phase 58.

- [ ] Define User Guide/Reference entry points and optional manual roles.
- [ ] Validate SmartDox and admitted Markdown parsing; decide compatible
  treatment of existing Asciidoc/HTML and mandatory HTML/PDF profiles.
- [ ] Generate stable framework document/section metadata, SimpleModeling.org
  HTML, RDF/JSON-LD/catalog, public AI Development Guide, and public Skill
  Catalog from admitted projections only.
- [ ] Generate Component model metadata and deterministic Mermaid class/state
  diagrams from CML/generated metadata rather than reflection.
- [ ] Generate/package Component Scaladoc, selected symbol/search index, and
  public/internal exposure policy.
- [ ] Generate/publish CNCF/Cozy/SmartDox Scaladoc with optional framework
  Documentation Component projection.
- [ ] Implement source include/exclude/license/disclosure/restricted-access
  policy with no source-omitted logical release.
- [ ] Package authored/generated release source, CML, build definitions, tests,
  dependency evidence, and generation provenance while excluding secrets,
  local configuration, cache, raw target, class files, incremental state,
  temporary files, logs, downloads, and host-specific state.
- [ ] Collect admitted Compile/Test managed sources into normalized
  generated-source main/test resources with inputs, options, identities,
  dependency evidence, digests, and provenance.
- [ ] Add release-readiness checks for stale/missing debugging evidence and
  generated-source/input digest mismatch.
- [ ] Generate Documentation/SourceCode inventories and prove publication,
  Directive/Skill, source-tree, and packaged-CAR identity/digest parity.
- [ ] Extend normal and strict Cozy CAR documentation lint.

Evidence:
- Pending.
