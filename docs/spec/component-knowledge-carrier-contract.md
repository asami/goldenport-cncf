# Component Knowledge Carrier Contract

## Purpose

`cncf.component-knowledge-carrier.v1` is the producer-side declaration for a
Component knowledge consumer contract. It is a value-only descriptor field,
not a consumer, resolver, content route, or publication mechanism.

## Canonical Declaration

The declaration contains exactly these fields:

```json
{
  "carrierSchema": "cncf.component-knowledge-carrier.v1",
  "consumerContractSchema": "cncf.component-knowledge-consumer.v1",
  "logicalPath": "component-knowledge.json"
}
```

Duplicate and unknown JSON fields are rejected. Both schema values are exact
and case-sensitive. `logicalPath` is exactly `component-knowledge.json`;
absolute paths, traversal, normalization aliases, and URL encodings are not
declarations. `ComponentKnowledgeCarrier.createC()` constructs the canonical
value without consumer content. Encoding is deterministic; malformed JSON,
missing fields, non-string field values, and invalid schema/path declarations
produce structured argument-invalid failures when decoded.

## Producer and Consumer Ownership

CNCF owns the value model and strict JSON codec. Producers own consumer-content
schema, Component identity, and logical-release checks and place the resulting
declaration in the descriptor. Consumers own reading and interpreting the
selected consumer contract. The carrier neither retains nor projects consumer
bytes and introduces no resolver or alternative authority.

## Producer Locations

The authored source is `src/main/car/component-knowledge.json`. A packaged CAR
contains those raw bytes at the archive logical path `component-knowledge.json`
and places only the declaration in `component-descriptor.json`. Development
preparation copies the raw bytes to `target/cncf.d/component-knowledge.json`
and records that file as development evidence.

The raw resource bytes never become CBD output through this contract. Missing
source preserves the existing descriptor and three-item development-evidence
behavior for legacy CARs.

## Initial Implementation Handoff — 2026-10-09

PHASE-71.5 / P715-02 / S38 implements this three-field CNCF declaration.
Focused validation and independent review are pending. Cozy producer/archive
adoption and Textus knowledge readers/projections still use the earlier digest
contract and remain pending under E715-01; their present implementation does
not yet match this library contract. No connected compatibility, dependency
refresh, publication, or Phase acceptance is claimed.

Executable specification: [ComponentKnowledgeCarrierSpec](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeCarrierSpec.scala).

## Actual S38 Focused Validation — 2026-10-09

**P715-02-S38-CNCF-KNOWLEDGE-CARRIER** is now
**implemented-focused-validated-unreviewed**. The initial unvalidated handoff
above remains dated history. The [parent-accepted actual result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s38-focused-validation-result.json)
records registered `cncf_command_runner` execution of
`testOnly org.goldenport.cncf.knowledge.ComponentKnowledgeCarrierSpec`:
**one suite completed, zero aborted; two tests succeeded, zero failed,
canceled, ignored or pending**. Implicit main/Test compilation passed.
The accepted result records **zero compiler/runtime warnings and errors**,
**SBT exit 0**, **wrapper exit 0**, terminal completion and **serial lock
released**. This is CNCF carrier-core validation only.

Exact retained execution evidence:

- [SBT log](/private/tmp/skill.cncf.d/a-404ea4a1-27ac-4d6d-a3e3-2154ac00ce2f/73360-20261009T070524Z.log)
- [Invocation summary](/private/tmp/skill.cncf.d/a-404ea4a1-27ac-4d6d-a3e3-2154ac00ce2f/73360-20261009T070524Z.summary.json)
- [Runner stdout](/private/tmp/skill.cncf.d/a-2771abc1-cff9-4148-b631-0b51ecad912f/stdout.txt)
- [Runner stderr](/private/tmp/skill.cncf.d/a-2771abc1-cff9-4148-b631-0b51ecad912f/stderr.txt)
- [Frozen factual documentation supplement](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s38-factual-documentation-manifest.json)

Independent review remains pending. **E715-01 connected Cozy/Textus adoption
and E715-02..05 remain OPEN**; this result establishes no connected compatibility,
dependency refresh, publication or Step/Phase acceptance. All original
independent review/acceptance, exact Step commits and distinct release
obligations remain OPEN. Seven Stage Status triples and acceptance checkboxes
retain their state. Repair accounting remains **13 cumulative / 0 nonconverging**,
with **M2 cycles 6/7/8/10/11/13 OPEN**. Earlier results, refusals and reviews
remain preserved. No further SBT or playback was run for this factual projection;
S36's native generation, audible smooth playback, end card and EOF evidence is
reused for the unchanged movie. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**.
