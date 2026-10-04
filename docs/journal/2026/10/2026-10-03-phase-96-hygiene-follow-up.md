<!-- cncf-phase-finding:HYG-P96-DOC-001:begin -->
Status: OPEN
Discovery state/date: REVIEW / 2026-10-03
Source review: phase96-p96-01a-step-review-001 / revision 1
Category/priority: presentation / P3

HYG-P96-DOC-001 — P96-01 Stage Status placement. Owner: goldenport-cncf. Evidence: docs/phase/phase-96-checklist.md:59–63 places the inventory paragraph between the P96-01 Stage header and Stage Status, contrary to ai/directive/core/phase-subphase-checklist.md §7.1. Nonblocking reason: all status and closure fields remain explicit and truthful; this is presentation maintenance with no behavior or contract impact. Follow-up boundary: relocate only the existing inventory paragraph below the unchanged Stage Status block, preserving every link, status and unchecked requirement.
<!-- cncf-phase-finding:HYG-P96-DOC-001:end -->

<!-- cncf-phase-finding:HYG-P96-02-001:begin -->
HYG-P96-02-001 — Static ABI executable-specification display traceability. Owner: cloud-native-component-framework. GeneratedLogicalUiAbiSpec.scala:62/65 and GeneratedLogicalUiAbiPropertySpec.scala:59/62 omit afterWord spec/example/rules/phase metadata, visible E<n> identities and Given contract references required by executable-spec-display-and-tagging-rules.md A.1/A.3, 1.1 and 4.2. The existing adjacent semantic Given/When/Then flows and all 23 static scenarios/laws pass; this is nonblocking presentation maintenance, not missing behavior proof. Follow-up scope: add stable display identities and governing generated-ABI references to the existing descriptions in these two suites without changing their behavior, coverage, production API or contract.
<!-- cncf-phase-finding:HYG-P96-02-001:end -->

<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001:begin -->
### HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cozy-phase76
- Status: OPEN
- Evidence: src/main/scala/cozy/modeler/Modeler.scala:215-216 binds and uses ordinary local resourceId instead of flatcase resourceid. Both lines are unchanged from accepted HEAD d9e106ca7d154402487274e189063f4276d0d94a.
- Risk: inconsistent declaration-scope naming in historical StateMachine guard rendering; no Logical UI behavior or validation risk was found.
- Maintenance boundary: a later Cozy naming-maintenance task may rename only the local binder and its interpolation use in _guard_mark_, preserving generated guard semantics. This review does not admit that cleanup into P76-03A.
<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001:end -->

<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001:begin -->
### HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cozy-phase76
- Status: OPEN
- Evidence: Modeler.scala is 860 lines versus 836 at accepted HEAD d9e106ca7d154402487274e189063f4276d0d94a, above the 800-line split-evaluation trigger. The file already combines StateMachine projection, generation routes, Explain, Help, Linkage and ModelBuilder; ModelBuilder at line 755 carries seventeen constructor inputs and projector wiring at lines 774-778.
- Review disposition: nonblocking historical responsibility-maintenance follow-up. P76-03A adds bounded two-route preflight and delegates rendering to a separate 127-line LogicalUiScalaGenerator. No current correctness or reviewability failure was found.
- Maintenance boundary: evaluate decomposition in a separately admitted Cozy maintenance task alongside ModelBuildContext, projector dependencies and callers, preserving package visibility, public API and construction semantics. Do not split unrelated responsibilities merely to reduce line count during P76-03A.
<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001:end -->

<!-- cncf-phase-finding:HYG-COZY76-P7603-README-CLI-PROJECTION-001:begin -->
### HYG-COZY76-P7603-README-CLI-PROJECTION-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cozy-phase76
- Status: OPEN
- Evidence: unchanged README.md:107 says CLI tooling will be provided in future releases; line 115 shows cozy generate --input model.cml --out src/generated. Current Cozy.scala:311-312 implements ordinary modeler-scala and modeler-scala-value commands. The README statements are present at accepted HEAD d9e106ca7d154402487274e189063f4276d0d94a.
- Risk: the general overview can direct readers toward an outdated generation workflow. The P76-03A owned producer documents correctly identify the ordinary routes.
- Maintenance boundary: reconcile CLI availability and the generation example in a separately admitted README maintenance task, using current command/help syntax. README.md remains outside this Step's owned mutation scope.
<!-- cncf-phase-finding:HYG-COZY76-P7603-README-CLI-PROJECTION-001:end -->

<!-- cncf-phase-finding:HYG-P96-03-SPEC-TRACEABILITY-001:begin -->
### HYG-P96-03-SPEC-TRACEABILITY-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cloud-native-component-framework-phase96
- Status: OPEN
- Discovery state/date: independent P96-03 Step REVIEW / 2026-10-04
- Source reviewer: /root/phase96_p96_03_step_review_001
- Category/priority: executable-specification display traceability / P3
- Evidence: src/test/scala/org/goldenport/cncf/display/DisplayProjectionSpec.scala:88-91, DisplayProjectionPropertySpec.scala:130-132, DisplayRuntimeSpec.scala:93-96, src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayRuntimeSpec.scala:131-134 and ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala:33-36 omit afterWord spec/example/rules/phase metadata. Their Given descriptions do not carry the governing specification path, rule identifiers and Example identity required by docs/rules/executable-spec-display-and-tagging-rules.md A.1/A.3 and 4.2. The P/R/B/GC property/scenario labels also differ from the prescribed E<n> identity format; DisplayProjectionSpec already has visible E1..E11 labels.
- Nonblocking reason: each actual behavior has adjacent semantic Given/When/Then, observable should expectations, useful behavior grouping and real regression value. The focused executable behavior proof passes. This is presentation/traceability maintenance, not a missing GWT authoring boundary or missing behavioral validation.
- Follow-up boundary: add stable canonical specification/example/rule/phase display references to these five existing suites in a separately admitted presentation-maintenance task, retaining their scenario correspondence, assertions, generators, minimum success thresholds, production API and contracts. Do not create closure-only tests or expand P96-03 production scope.
<!-- cncf-phase-finding:HYG-P96-03-SPEC-TRACEABILITY-001:end -->

<!-- cncf-phase-finding:HYG-P96-PARSER-SIZE-001:begin -->
### HYG-P96-PARSER-SIZE-001

- Status: OPEN
- Discovery state: QUOTE-01A PLAN candidate, independently confirmed by protected-focused Step REVIEW.
- Discovery date: 2026-10-04
- Project: goldenport-scala-library
- Relative path: src/main/scala/org/goldenport/parser/LogicalLines.scala
- Symbol: LogicalLines and its nested parser-state hierarchy.
- Evidence: The original source has 1,333 lines and the reviewed source has 1,336 lines, exceeding the ordinary 1,000-line source-size debt threshold. LogicalLines exposes nested state classes and companions; sealed AwakeningLogicalLinesParseState and AdvancedAwakeningLogicalLinesParseState constrain subclass placement. A safe extraction must preserve nested public identities, hierarchy, transition construction and initialization, so it is not a mechanical four-path lexical repair.
- Category: Existing source-size debt; Hygiene follow-up.
- Priority: P2
- Nonblocking reason: QUOTE-01A adds one parity guard and a version-history line without adding a responsibility, changing API shapes or making the existing structure unsafe or unreviewable. No correctness defect was found in the admitted boundary.
- Proposed boundary: A separately planned goldenport-scala-library parser-maintenance task that first establishes a compatibility-preserving extraction design and its affected callers/specifications; retain current nested API and sealed-hierarchy contracts until that task is admitted.
<!-- cncf-phase-finding:HYG-P96-PARSER-SIZE-001:end -->
