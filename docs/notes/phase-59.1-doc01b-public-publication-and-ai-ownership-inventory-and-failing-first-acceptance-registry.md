# Phase 59.1 DOC-01B — Public Publication and AI Ownership Inventory

status = pre-review accepted-handoff-candidate
date = 2026-08-23
phase = Phase 59.1 — Public Documentation and AI Ownership Inventory
step = DOC-01B handoff
slice = DOC-01B inventory note
kind = non-normative inventory and failing-first acceptance registry

This note is the sole DOC-01B public-publication and AI ownership handoff.
It consumes [Phase 59 DOC-01A](phase-59-doc01a-source-to-package-inventory-and-failing-first-acceptance-registry.md)
read-only and adds only DOC-01B decisions. It freezes current
SimpleModeling.org publication, `ai-directive`, Skill/Cozy/Launcher, Textus
CBD Support, and Textus BoK ownership and authority boundaries, and registers
future failing-first executable-specification identities. It implements no
behavior and does not update a status ledger.

The [Phase 59.1 authority](../phase/phase-59.1.md), its
[checklist](../phase/phase-59.1-checklist.md), and the closed Phase 59 DOC-01A
handoff remain the governing inputs. This candidate is not a review result:
review, validation, and transition decisions remain with the parent workflow.

## Evidence and qualification

The following snapshot is read-only evidence. A dirty working tree is not
adopted, edited, accepted, or released authority. Clean specifications,
closed phases, current paths, and dirty documentation only as non-accepted
corroboration are eligible inputs; unrelated user content is not reproduced.

| Repository role | Repository | Exact HEAD | Worktree | Qualification |
| --- | --- | --- | --- | --- |
| DOC-01B handoff owner and sole mutation repository | `cloud-native-component-framework` | `17ea4bb6595084a0cf1a592056b1dd9331a01063` | clean | Authoritative DOC-01B registry and handoff. |
| Public-site publication evidence | `simplemodeling-org` | `efa979b5fb5ae04f28756eff18db5f3e8ebd3b48` | dirty (10) | Current publication paths only; dirty bytes are non-accepted corroboration. |
| Shared directive authority evidence | `ai-directive` | `6dea97adbaa4ae7b78db0a6504a0ee66d1ac7fed` | clean | External, versioned directive contract. |
| Packaging and projection evidence | `cozy` | `238a078869b2d5ceffe0bf3677355b7e9bff3c83` | clean | CAR content, lint, archive, and projection evidence. |
| Packaging and projection evidence | `sbt-cozy` | `4af1446a96e181cf86860533d00957e6aff3b87c` | dirty (1) | SBT task and publication wiring only; dirty bytes are non-accepted corroboration. |
| Planned development Skill installation evidence | `cncf-launcher` | `ed6c3f9eb99f9a6824d16fcd805ae8937f613476` | clean | Planned development/default-project installation boundary. |
| Planned published Skill installation evidence | `textus-launcher` | `22d4bf792eaf0c721555234e9ea233ddaf2d8524` | clean | Planned published/local/cache default-user installation boundary. |
| Exact Component detail/usage/MCP/CAR Review evidence | `textus-cbd-support` | `92cb370232e3578fbe9b6187cb8d07f895df5b74` | dirty (19) | Clean specs/current paths and non-accepted corroboration only. |
| Terminology, existence, SIE federation, profile, and MCP evidence | `textus-bok` | `068f7332065a94ad285db1dae4546970f6b63d87` | dirty (32) | Clean specs/current paths and non-accepted corroboration only. |

No dirty bytes in an external repository are authority for a contract, and no
external repository is mutated by this slice.

## SimpleModeling.org publication inventory

SimpleModeling.org is the public framework information surface. Its current
publication inventory is a projection of attributable source material, not a
replacement for Component-specific ownership or runtime resolution.

| Source/output path or family | Logical identity | Public URL/IRI | Version | Digest and authority boundary |
| --- | --- | --- | --- | --- |
| `src/main/website/en` and `src/main/website/ja` localized HTML | Localized framework publication document/section | Canonical `https://www.simplemodeling.org/...` URL/IRI for the published projection | Publication version, when present in the source metadata | Record the SHA-256 of the exact published bytes; locale, source, and output remain distinct. |
| `src/main/website/site.ttl` and `src/main/website/site.jsonld` | Framework publication graph and its JSON-LD projection | Canonical site IRIs | Publication/schema version as declared by each artifact | RDF/JSON-LD bytes are attributable projections; equal URLs do not make either artifact the sole logical authority. |
| Stable `simplemodelingorg` ontology/schema | Site vocabulary and schema identities | Stable ontology/schema IRIs | Stable family version plus versioned projections | Ontology/schema identity is distinct from a page, snapshot, or Component release. |
| `*/ontology/0.1-SNAPSHOT` and `*/schema/0.1-SNAPSHOT` | Versioned development ontology/schema projections | Versioned ontology/schema IRIs | `0.1-SNAPSHOT` | Snapshot status is explicit; it is not a released public authority. |
| `src/main/website/metadata/glossary/terms.json` | Glossary term records | Canonical site URL/IRI projection | Metadata version, if declared | Preserve source path, commit/version, visibility, and SHA-256; glossary guidance does not grant execution or Component authority. |
| `src/main/website/metadata/cncf/knowledge-source.json` | CNCF framework knowledge-source metadata | Canonical site URL/IRI projection | Metadata version, if declared | Records the admitted source identity and visibility; metadata is not a resolver or runtime source. |
| `src/main/website/en/catalog/index.html` and `src/main/website/metadata/catalog/projects/*.json` | Public project catalog and project records | Canonical catalog/project URLs/IRIs | Catalog/project version, if declared | Catalog identity, project identity, source identity, and content digest remain separate. |

For every publication, source path, output path, logical identity, public
URL/IRI, version, and content SHA-256 byte digest are separately recorded.
URL/IRI equality does not imply byte or logical authority. Digest equality does
not collapse logical authorities. A source or installed snapshot cannot claim
official status without an explicit publication binding. WIP `/catalog`,
`/repository`, and richer deployment proposals are direction, not proof of a
current publication contract.

A framework Documentation Component is an optional, versioned, attributable
snapshot of framework information. It is never a silent replacement for
SimpleModeling.org public authority, and it does not transfer ownership of
Component-specific content.

## Identity taxonomy

The inventory keeps the following identities distinct:

| Identity | Meaning | Non-equivalence rule |
| --- | --- | --- |
| Component/release | Component identity and its released version | A publication page, archive, or snapshot does not become the Component release merely by mentioning it. |
| Framework product/version | SimpleModeling framework product and version identity | It is not a Component, document, or section identity. |
| Logical document | A document as a stable semantic unit | It is not interchangeable with a physical source/output file. |
| Section/fragment | A bounded document fragment or anchor | A URL location or fragment does not become the complete document authority. |
| Phase 58 logical resource/subcomponent | The Phase 58 resolved logical resource/subcomponent identity | Publication and knowledge projections must consume this identity and must not create a second resolver. |
| Public URL/IRI projection/location | A public address or RDF/JSON-LD location | URL equality does not prove byte equality or authority equality. |
| Content SHA-256 byte integrity/equivalence | Digest of exact bytes used for integrity/equivalence claims | Digest equality does not merge distinct logical authorities or establish publication binding. |

## ai-directive authority and visibility

`ai-directive` is the authoritative external, versioned contract when mounted
in a consuming project as `ai/directive`. The authority order is the shared
directive core, then the selected profile. Samples are non-authoritative
examples. A consuming project exposes the contract through the
`ai/directive` submodule and root symlink visibility (`AGENT.md`, `AGENTS.md`,
and `RULE.md` where applicable).

Project-local rules, guidance, and explicit exceptions remain outside the
shared submodule. They may add detail but must not silently contradict the
shared contract. A consuming project receives a shared-directive change by
updating the submodule pointer; it does not edit the mounted submodule as a
local override.

Public HTML, BoK, and Skill material is guidance or discovery only. It retains
the source commit or version, content digest, and visibility so a consumer can
distinguish a published projection, an installed snapshot, a development
proposal, and an example. None of those projections silently grants directive
authority, runtime execution, or mutation authority.

## Skill, Cozy, and Launcher ownership

The planned Skill boundary is a handoff between owners, not an activation
mechanism:

| Boundary | Planned owner/contract | Frozen meaning |
| --- | --- | --- |
| `CSB` | CNCF Phase 66 | Plans the `SkillBundleManifest` contract and its source/archive identity, digest, compatibility, and non-activation semantics. |
| `SK24` / `SK24-02` | Cozy Phase 24 | Plans validation, lint, projection, and provenance of the CAR Skill content; it does not take ownership of Component content. |
| `CL-SK` | CNCF Launcher Phase 1 | Plans development Skill installation with a default project scope. |
| `TL-SK` | Textus Launcher Phase 1 | Plans published/local/cache Skill installation with a default user scope. |
| Component/CAR | Component author | Owns Skill content and its source identity. |

Skill metadata and MCP requirements can describe identity, compatibility, and
read requirements. They do not grant execution, dependency activation,
configuration, start, contact, invoke, authority, or Codex mutation. The
acceptance registry uses the `CSB`, `SK24`, `CL-SK`, and `TL-SK` identities
without replacing any Phase, repository, or launcher ledger.

## Textus CBD Support primary route

Textus CBD Support is the primary exact Component-use route. It owns the
admitted Component detail and usage view covering versions, compatibility,
dependencies, services, operations, artifacts, documentation, and usage. The
route is attributable to explicit sources and reports ambiguity or absence
without inventing facts.

`CbdRetrieval` MCP is read-only retrieval evidence; administrative surfaces
remain private. Exact retrieval applies the same identity, organization, kind,
version, and optional `catalogId` constraints to every candidate: zero
candidates is `no-match`, one is `matched`, and multiple candidates is
`ambiguous` with no selected profile. An ambiguous result reports the complete
`candidateCount` and at most 20 attributable alternatives. Catalog priority
orders presentation only and never chooses among exact candidates; an explicit
`catalogId` or stricter identity is required to reduce ambiguity.

Development-directory, local-warehouse, and cache observations retain distinct
`working`, `local-published`, and `cached` provenance. They are admitted only
from explicit or canonical bounded roots, are not promoted into catalog
profiles, and do not imply remote publication, compatibility, or
recommendation. There is no ambient archive, repository, cache, development-
directory, home, or sibling-repository scan. CBD Support owns the CAR Review
report, gate, and attestation. BoK and SIE evidence cannot fill missing CBD
detail facts or complete a CAR Review on CBD's behalf.

## Textus BoK complementary route

Textus BoK owns terminology, source interpretation, and existence-only
`ComponentReference` evidence. SIE owns provider-neutral federation through a
public component contract. An exact existence handoff emits identity and
existence evidence only; it does not emit Component detail or usage facts.

Four read operations are MCP-ready. Mutation, replacement, and Knowledge Map
operations remain outside this read contract. The closed selector vocabulary
is `official`, `development`, and `project`; an omitted `profile` normalizes to
`official`, and `project` requires an explicit valid `projectId`. Unknown
profiles fail as `invalid-selection`; a missing project identity fails as
`project-identity-required`; and a `projectId` attached to
`official`/`development` fails as `conflicting-selection` rather than choosing
another profile. After resolution, optional `datasetId` and `sourceId` values
can only assert or narrow the resolved tuple; a mismatch is
`conflicting-selection` and cannot switch profiles. There is no ambient
inference, fallback, or union of profiles, and a profile cannot silently
promote a stale, restricted, or absent source.

BoK is the complementary semantic/RAG route. It hands exact Component
references to CBD Support for independent detail/usage selection; it does not
become a second resolver, a Component detail source, an installer, an
activation path, or a CAR Review authority.

## Cross-consumer boundaries

- Component-specific content retains Component ownership. Public framework,
  Directive, Skill, CBD, and BoK identities remain attributable to their
  respective owners and source commits/versions/digests.
- Help and direct-AI consumers, and the later Phase 60 consumer, consume only
  admitted resources. They do not independently scan archives, repositories,
  caches, or development directories and do not infer authority from a URL,
  metadata record, or installed presence.
- Phase 58 logical/physical resolution, provenance, integrity, authorization,
  availability, and runtime operation-mode policy remain unchanged. DOC-01B
  does not create a second resolver, runtime renderer, or `OperationMode`
  domain.
- DOC-01A identities remain unchanged and non-duplicated. Phase 59.2 and
  later phases are not started by this note.

These boundaries preserve the no-second-resolver, no-independent-scan,
no-runtime-renderer, and no-`OperationMode`-domain invariants. Membership,
metadata, MCP-readiness, publication, and Skill installation do not by
themselves grant activation, operation, disclosure, deployment, or authority.

## DOC-01B failing-first acceptance registry

All eight groups below are future identities. Their files are intentionally
unmaterialized in DOC-01B. Each successor must materialize the exact path and
suite/script, record an attributable RED, and make that same identity GREEN;
renaming or replacing an identity requires an explicit registry update rather
than silent substitution. A pending, ignored, source-text-only, metadata-only,
or unattributed result is not acceptance evidence.

| Group ID | Owner phase | Repository / owner | Exact future path(s) | Exact suite/script | Required acceptance focus |
| --- | --- | --- | --- | --- | --- |
| `DOC03-PUBLIC-FRAMEWORK-PROJECTION` | Phase 59.3 | `simplemodeling-org` | `scripts/test/check-cncf-framework-publication-contract.sh` | script `check-cncf-framework-publication-contract` | Localized and machine-readable families have exact bindings; identities are distinct and reproducible; dirty/WIP bytes cannot claim equivalence. |
| `DOC03-AI-DIRECTIVE-PROJECTION` | Phase 59.3 | `ai-directive` | `scripts/test/check-cncf-public-directive-projection.sh` | script `check-cncf-public-directive-projection` | Core/profile/submodule evidence is attributable; samples and local extensions remain separate; projection grants no authority. |
| `DOC01B-SKILL-BUNDLE-OWNERSHIP` | External `CSB`/`SK24`/`CL-SK`/`TL-SK` | `cloud-native-component-framework`, `cozy`, `cncf-launcher`, `textus-launcher` | CNCF `src/test/scala/org/goldenport/cncf/skill/SkillBundleManifestSpec.scala`; Cozy `src/test/scala/cozy/skill/CozySkillBundleProjectionSpec.scala`; CNCF Launcher `src/test/scala/cncf/launcher/DevelopmentSkillBundleInstallationSpec.scala`; Textus Launcher `src/test/scala/textus/launcher/PublishedSkillBundleInstallationSpec.scala` | `org.goldenport.cncf.skill.SkillBundleManifestSpec`; `cozy.skill.CozySkillBundleProjectionSpec`; `cncf.launcher.DevelopmentSkillBundleInstallationSpec`; `textus.launcher.PublishedSkillBundleInstallationSpec` | Source/archive identity, digest, and compatibility are deterministic; staged installation/refusal is explicit; manifest metadata and validation never activate content or dependencies, while an explicit install activates only the selected validated bundle. |
| `DOC06-CBD-PRIMARY-COMPONENT-USE` | Phase 59.6 | `textus-cbd-support` | `src/test/scala/org/simplemodeling/textus/cbdsupport/ComponentKnowledgeIntegrationSpec.scala` | `org.simplemodeling.textus.cbdsupport.ComponentKnowledgeIntegrationSpec` | Exact admission/detail/usage is attributable; ambiguity and absence are explicit; Review/MCP ownership remains CBD-owned; no semantic completion or ambient scan. |
| `DOC07-BOK-COMPLEMENTARY-KNOWLEDGE` | Phase 59.7 | `textus-bok` | `src/test/scala/org/simplemodeling/textus/bok/ComponentKnowledgeRetrievalSpec.scala` | `org.simplemodeling.textus.bok.ComponentKnowledgeRetrievalSpec` | Profiles and exact evidence are explicit; semantic stale/absence is reported; no mutation, installation, activation, or discovery. |
| `DOC07-BOK-CBD-REFERENCE-HANDOFF` | Phase 59.7 | `textus-bok` plus `textus-cbd-support` | BoK `src/test/scala/org/simplemodeling/textus/bok/BokCbdComponentReferenceHandoffSpec.scala`; CBD `src/test/scala/org/simplemodeling/textus/cbdsupport/ComponentReferenceHandoffSpec.scala` | BoK `org.simplemodeling.textus.bok.BokCbdComponentReferenceHandoffSpec`; CBD `org.simplemodeling.textus.cbdsupport.ComponentReferenceHandoffSpec` | BoK emits exact existence evidence; CBD independently selects detail/usage; mismatch and absence are bounded without completion. |
| `DOC08-FRAMEWORK-DOCUMENTATION-SNAPSHOT` | Phase 59.8 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/knowledge/FrameworkDocumentationProfileSpec.scala` | `org.goldenport.cncf.knowledge.FrameworkDocumentationProfileSpec` | Public and installed snapshot views are distinct; identities and disclosure are exact; Help and AI share one admitted view without granting authority. |
| `DOC09-PUBLIC-AI-NO-SCAN` | Phase 59.9 | `sbt-cozy` | `src/sbt-test/cozy/component-public-ai-no-scan` | script `component-public-ai-no-scan` | Only admitted resources are consumed; scans are absent; hostile, stale, restricted, and online failures are structured without escalation. |

### Common RED then GREEN rule

Every group follows one protocol: materialize the exact registered identity,
run it failing first with a named, attributable missing or contradicted
contract, then implement the smallest scoped behavior and make that same
identity GREEN. The future file and suite/script remain unmaterialized here;
DOC-01B records identities only.

## Handoff and non-goals

Phase 59.2 consumes DOC-01A and DOC-01B together without rediscovery. Neither
handoff starts Phase 59.2, implements a registered specification, or closes
Phase 59.1.

This slice does not change phase/checklist/README/strategy ledgers; implement
specifications, scripts, behavior, manifests, publication, packaging, Help or
AI routes; run SBT, Cozy, Dox, runtime, or launcher commands; perform
publication, deployment, review, validation, staging, commit, or external
cleanup; or reopen Phase 58 or DOC-01A. No MCP configuration or invocation,
Skill activation, authority grant, new resolver/scanner/runtime renderer, or
`OperationMode` domain is introduced.
