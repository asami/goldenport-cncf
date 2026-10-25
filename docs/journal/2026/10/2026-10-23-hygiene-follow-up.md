# Configuration hygiene follow-up

Date: 2026-10-23

Ordinary review of the reason-based uncommitted changes found no current
boundary blocker. The following inherited maintenance items remain outside
that accepted behavioral scope. They do not change Phase 116 acceptance or
authorize an incidental refactor.

## HYG-RUNTIME-CONFIG-PUBLIC-PARAM-001

- Status: OPEN
- Priority: P3
- Category: naming
- Discovered: 2026-10-23, independent ordinary acceptance review
- Owner: goldenport-cncf configuration maintainers
- Location: `src/main/scala/org/goldenport/cncf/config/RuntimeConfig.scala:201`
- Evidence: the pre-existing public `DebugAuthConfig.validationError` parameter
  is named `operationmode`; the repository convention calls for `operationMode`.
  The same declaration exists in the reviewed baseline.
- Risk: source-API naming conformance; no current resource-limit defect was found.
- Disposition: defer to a separately selected configuration naming task. Assess
  named-argument callers and compatibility before changing the public label.
  No public-label change or refactor is admitted by this review.

## HYG-RUNTIME-CONFIG-SPEC-GWT-001

- Status: OPEN
- Priority: P3
- Category: executable-specification authoring
- Discovered: 2026-10-23, independent ordinary acceptance review
- Owner: goldenport-cncf configuration specification maintainers
- Location: `src/test/scala/org/goldenport/cncf/config/RuntimeConfigSpec.scala`,
  lines 135, 236 and the feature examples beginning at line 407
- Evidence: unchanged operation-mode, demo-assist, debug-auth, observability,
  BlobStore and renderer examples omit semantic Given/When/Then clauses. Many
  feature areas remain under one broad heading. The changed resource-read
  scenarios have semantic clauses at their actual execution boundaries.
- Risk: specification readability; no current correctness or validation-trust
  defect was found.
- Disposition: defer to a separately selected executable-specification
  documentation task. Add semantic clauses and cohesive grouping while
  preserving assertions and behavior. No incidental test rewrite is admitted.

## Existing configuration-size record

The review also re-observed `HYG-RUNTIME-CONFIG-SIZE-001`. Its canonical record
remains in [the managed-cache planning journal](2026-10-21-car-sar-managed-cache-plan.md#runtime-prerequisite-review-observation).
`RuntimeConfig.scala` has 1,168 lines in both the reviewed baseline and current
tree. Preserve that record and its separately selected configuration-role split
assessment; this reference does not create a second canonical item.
