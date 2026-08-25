# Phase 59.3 Checklist - Documentation Authoring and Content Packaging Toolchain

status=closed
phase=[Phase 59.3 - Documentation Authoring and Content Packaging Toolchain](phase-59.3.md)
predecessor=[Phase 59.2.3](phase-59.2.3.md)
successor=[Phase 59.4](phase-59.4.md)

## DOC-03: Authoring and Content Packaging Toolchain

Stage Status:
- Current status: DONE
- Owner: Cozy/sbt-cozy and SmartDox maintainers
- Update rule: Record owning-toolchain focused evidence and source/package
  equivalence before handing resources to Phase 59.4.
- Entry rule: Phase 59.2.3 DOC-02D is DONE.
- Completion rule: Required manuals, projections, Scaladoc, and filtered
  release source are validated and handed through the already accepted closed
  Phase 58 packaging/resource contract to Phase 59.4, without reopening or
  modifying Phase 58.

- [x] Define User Guide/Reference entry points and optional manual roles.
- [x] Validate SmartDox and admitted Markdown parsing; decide compatible
  treatment of existing Asciidoc/HTML and mandatory HTML/PDF profiles.
- [x] Generate stable framework document/section metadata, SimpleModeling.org
  HTML, RDF/JSON-LD/catalog, public AI Development Guide, and public Skill
  Catalog from admitted projections only.
- [x] Generate Component model metadata and deterministic Mermaid class/state
  diagrams from CML/generated metadata rather than reflection.
- [x] Generate/package Component Scaladoc, selected symbol/search index, and
  public/internal exposure policy.
- [x] Generate/publish CNCF/Cozy/SmartDox Scaladoc with optional framework
  Documentation Component projection.
- [x] Implement source include/exclude/license/disclosure/restricted-access
  policy with no source-omitted logical release.
- [x] Package authored/generated release source, CML, build definitions, tests,
  dependency evidence, and generation provenance while excluding secrets,
  local configuration, cache, raw target, class files, incremental state,
  temporary files, logs, downloads, and host-specific state.
- [x] Collect admitted Compile/Test managed sources into normalized
  generated-source main/test resources with inputs, options, identities,
  dependency evidence, digests, and provenance.
- [x] Add release-readiness checks for stale/missing debugging evidence and
  generated-source/input digest mismatch.
- [x] Generate Documentation/SourceCode inventories and prove publication,
  Directive/Skill, source-tree, and packaged-CAR identity/digest parity.
- [x] Extend normal and strict Cozy CAR documentation lint.

Evidence:
- Accepted implementation steps: source archive `7dca56b8`, Component
  Scaladoc `5b5663f9`, verified release source `7d94d402`, SmartDox projection
  `f0396f6f`, canonical Directive projection `3154cc7e`, and the CAR-lint
  decision record `fb0fbec6`.
- Full validation passes: Cozy `sbt --batch test` 1415 succeeded/0 failed;
  sbt-cozy `sbt --batch test` 149 succeeded/0 failed/7 canceled, serialized
  invocation `75987-20260825T102631Z`; SmartDox `sbt --batch test` 282
  succeeded/0 failed. The SimpleModeling.org publication-contract check and
  ai-directive public-projection check pass.
- Mandatory Phase full review `P593-DOC03-PHASE-FULL-REVIEW-001` is PASS with
  no Current Phase Blocker and no Development Candidate. Its sole nonblocking
  follow-up is persisted as `HYG-P593-DOC03-SMARTDOX-001`.
- The final closure binding is
  `phase59.3-clb-1de8591e8f385db16c57ffe200b32fd8ac77c9115feff3515f60cd4d1bd9696e`.
