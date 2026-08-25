# Framework Documentation Profile Specification

Status: normative DOC04-B/S01 contract

## Contract

`FrameworkDocumentationProfile.createC(frameworkPublication, snapshotManifest)`
shall accept only a caller-supplied `FrameworkPublicationContext` and optional
`ComponentKnowledgeManifest` snapshot evidence. It shall validate the framework
publication with the existing canonical URL, publication-generation, and digest
rules, and validate a supplied manifest with the existing manifest resource
SHA-256/provenance matching and Guide/Skill digest-linkage rules. It shall add
no codec, schema, serialization, resolver, or physical-access behavior.

The framework publication is the only retained framework identity: its product,
version, publication generation, canonical URL, and digest remain distinct from
any Documentation Component snapshot identity. No target Component ID,
`ComponentDevelopmentContext`, Help service, operation mode, resolver, path,
or runtime/activation input shall be accepted by this contract.

## Snapshot Semantics

If both the publication Documentation Component snapshot and manifest evidence
are absent, creation shall succeed with `FrameworkDocumentationSnapshotProfile.Absent`.
This absence shall never be a profile failure or a startup or Component-specific
Help/manual precondition; this contract implements none of those later runtime
behaviors.

If either snapshot reference or manifest evidence is present, both shall be
present. The manifest Component ID and logical release shall exactly match the
publication snapshot reference. The manifest `frameworkPublication` shall
exactly equal the supplied framework publication. At least one manifest resource
shall have kind `FrameworkDocumentation`, role `FrameworkDocumentation`, media
type `TextMarkdown`, and `Framework` metadata authority.

Optional `publicDirective` and `skillCatalog` are descriptive framework
snapshot metadata only. Each present value shall retain existing manifest
membership and digest-linkage validation, reference a `Framework`-authority
resource, and have `Public` or `Ecosystem` visibility. No raw source, rule,
profile, prompt, content, credential, path, installation, activation,
execution, MCP, disclosure-authority, or resolver input is introduced.

## Exclusions

This slice shall not scan or read resources; access a filesystem, archive,
cache, repository, or network; choose a Component fallback; select an operation
mode; activate runtime behavior; implement Help, HTTP, CLI, CBD, BoK, or SAR;
or alter existing public structures, codecs, or schemas. Closed-network
Documentation Hub SAR composition is deferred to DOC04-B/S02.

## Executable Specification

`org.goldenport.cncf.knowledge.FrameworkDocumentationProfileSpec` specifies:

- `P594-DOC04B-S01-AC01`: absent online-canonical snapshot success without
  target or runtime coupling.
- `P594-DOC04B-S01-AC02`: accepted installed snapshot with framework identity
  and Public/Ecosystem Guide and Skill Catalog metadata.
- `P594-DOC04B-S01-AC03`: rejection of missing or mismatched snapshot identity,
  logical release, and framework publication context.
- `P594-DOC04B-S01-AC04`: rejection of missing Framework documentation
  resource evidence or non-Framework authority.
- `P594-DOC04B-S01-AC05`: rejection of Guide or Skill Catalog metadata outside
  Public or Ecosystem visibility.
