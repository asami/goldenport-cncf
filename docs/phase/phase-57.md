# Phase 57 - Information CML Runtime Canonicalization

status=planned
planned_at=2026-07-26
depends_on=[Phase 56](phase-56.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 57 Checklist](phase-57-checklist.md)

## Purpose

Complete the Information CML migration that began in Phase 27 but did not
replace the hand-written runtime `Information` model.

Phase 57 makes the `src/main/cozy/information.cml` generated Entity family the
single canonical CNCF Information runtime model. `InformationSpace` remains
the component-owned curation boundary, but it operates on the generated,
revision-aware `SimpleEntity` representation instead of a parallel
hand-written case class.

## Dependency

Phase 57 begins after Phase 56 closes.

Technical foundations are Phase 26, Phase 27, Phase 49, and Phase 50.

## Scope

- Freeze the hand-written/generated type split and compatibility surface.
- Complete the canonical Information CML and required generator behavior.
- Adopt the generated Entity/value/powertype family in CNCF runtime.
- Bind InformationSpace to standard Entity persistence, revision, and OCC.
- Preserve curation, authorization, Tag, publication, and Knowledge behavior.
- Migrate DSL, Help, transport, schema, MCP, Web, and editor projections.
- Validate persisted-state and downstream Textus compatibility.
- Remove hand-written duplicates and promote verified design/specification.

## Non-Goals

- Moving CNCF Information into `simplemodeling-model`.
- Making KnowledgeSpace editable.
- Merging Information, Entity, RDF, external, Tag, or Knowledge identities.
- Replacing Information capabilities with generic Entity permissions.
- Exposing provider payloads or managed revision as application input.
- Retaining a parallel Information persistence kernel.
- Redesigning unrelated CML models or generator behavior.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| IC-01 | Inventory and failing-first acceptance | The hand-written/generated split, downstream use, compatibility surface, and exact executable acceptance identities are fixed. | planned |
| IC-02 | Canonical CML and generator contract | Information CML completely describes the canonical Entity/value/lifecycle contract and generates usable revision-aware outputs and transition evidence. | planned |
| IC-03 | Generated type adoption | CNCF runtime source uses one generated Information and generated CML values, with bounded compatibility adapters only where required. | planned |
| IC-04 | InformationSpace Entity persistence and OCC | InformationSpace operates through the standard Entity repository/UnitOfWork boundary with managed revision and atomic stale-write rejection. | planned |
| IC-05 | Curation and Knowledge lifecycle migration | Validation, resolution, confirmation, publication, conflict, Tag, and Knowledge materialization behavior is preserved on the generated model. | planned |
| IC-06 | DSL, transport, Help, and editor projections | Protected DSL, CallTree, HTTP/Web/Help/schema/OpenAPI/MCP, and editor contracts use the canonical generated Entity without leaking managed inputs. | planned |
| IC-07 | Downstream and migration acceptance | Textus Knowledge Editor, Textus SIE, persisted-state migration, and representative domain flows pass with attributable identities and revisions. | planned |
| IC-08 | Duplicate removal and canonical closure | Hand-written duplicates are removed, full validation passes, and design/spec/strategy/phase records describe the verified single-model runtime. | planned |

## Acceptance

- InformationSpace and all operational surfaces use one generated canonical
  Information Entity.
- Generated outputs and inputs obey the Phase 50 managed-revision contract.
- Information mutations use standard Entity persistence and atomic OCC.
- Phase 26/27 curation and Knowledge behavior remains compatible.
- Supported persisted state is migrated or rejected explicitly without silent
  loss.
- Hand-written canonical duplicates are removed.
- Full CNCF and affected downstream validation passes.
- Verified architecture and contracts are promoted to design/specification.

## Planning References

- [Phase 26 - Knowledge Import and InformationSpace](phase-26.md)
- [Phase 27 Checklist](phase-27-checklist.md)
- [Phase 49 - Entity Conflict and Conditional Transition](phase-49.md)
- [Phase 50 - SimpleEntity Revision and OCC Simplification](phase-50.md)
- [Information CML](../../src/main/cozy/information.cml)
- [InformationSpace working model](../journal/2026/05/knowledge-import-information-space-working-model.md)
