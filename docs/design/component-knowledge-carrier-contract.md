# Component Knowledge Carrier Design

The carrier separates a value-only schema/path declaration from producer and
consumer content behavior. CNCF owns the three-field model and strict codec.
The producer checks the consumer contract's declared schema, Component identity,
and logical release, then injects the generated declaration; an authored
`componentKnowledge` descriptor field is not a competing authority. Consumers
own reading and interpreting the selected content. The carrier stores no
consumer bytes and creates no resource resolver.

Archive and development flows preserve one raw logical resource:

- Source: `src/main/car/component-knowledge.json`
- Archive: `component-knowledge.json`
- Development evidence: `target/cncf.d/component-knowledge.json`

A declaration is optional for CARs without knowledge content; when present,
its two schemas and logical path are exact. Archive admission retains ordinary
resource selection and consumer-content semantics. A downstream repository
catalog may publish the selected raw file at one declared, version-scoped
sidecar route; that transport is outside the core carrier format. This design
introduces no transport encoding, CML API, or CBD consumer behavior.

## Initial Implementation Handoff — 2026-10-09

PHASE-71.5 / P715-02 / S38 implements the three-field CNCF model/codec and
updates its existing executable specification. It remains unvalidated and
unreviewed. Cozy producer/archive handling and Textus knowledge integration,
projections, local-source and catalog readers still use the earlier digest
contract. Their connected adoption is pending under E715-01; this change does
not establish their compatibility or refresh their dependencies.

See the [carrier specification](../spec/component-knowledge-carrier-contract.md).

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
