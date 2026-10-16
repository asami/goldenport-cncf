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

<!-- cncf-phase-finding:HYG-P96-05-BASELINE-MAINTENANCE-001:begin -->
### HYG-P96-05-BASELINE-MAINTENANCE-001

- Kind: hygiene
- Status: OPEN
- Discovery: independent P96-05 protected-focused whole-Step review, 2026-10-05
- Source reviewer: /root/phase96_p96_05_step_review_001
- Evidence: Component.scala has 1,365 current lines versus 1,320 at accepted P96-04 HEAD625d533, already exceeding the ordinary 1,000-line source-size threshold before this Step. Its historical composition/lifecycle code includes legacy Any/cast surfaces and local/private-parameter naming debt. ComponentFactoryEntityPlanPart.scala retains historical erased store-snapshot handling. Alias.scala retains lower-case Purpose cases and the hidden AliasNormalization._normalize_input declaration. OperationRequestActionDispatcher.scala retains existing serviceDefinition/operationDefinition local binders. These defects precede the admitted delta; no new source erasure or unrelated responsibility expansion was found in the Display registration/read implementation.
- Priority: P3
- Nonblocking reason: current behavior is reviewable and the focused service, lifecycle, resolver, HTTP and alias evidence passes. This is baseline maintenance, not authorization for incidental cleanup.
- Maintenance boundary: a separately admitted CNCF naming/responsibility-maintenance task may inventory and repair this baseline while preserving compatibility and semantic bootstrap behavior. Do not extract Component, redesign storage or expand P96-05 merely to address this record. Existing HYG-P96-02-001 and HYG-P96-03-SPEC-TRACEABILITY-001 remain distinct and are not duplicated here.
<!-- cncf-phase-finding:HYG-P96-05-BASELINE-MAINTENANCE-001:end -->

<!-- cncf-phase-finding:HYG-P96-05-GIVEN-TRACEABILITY-001:begin -->
### HYG-P96-05-GIVEN-TRACEABILITY-001

- Kind: hygiene
- Status: OPEN
- Discovery: independent P96-05 protected-focused whole-Step review, 2026-10-05
- Source reviewer: /root/phase96_p96_05_step_review_001
- Evidence: the new ComponentFactoryDisplayServiceSpec.scala:79, DisplayServiceReadSpec.scala:32, DisplayServiceReadPropertySpec.scala:52, OperationResponseJsonEnvelopeSpec.scala:29 and PathPreNormalizerHttpSuffixSpec.scala:29 have useful semantic Given clauses but omit the specification file path, governing rule identifiers and Example identifier within the Given description required by docs/rules/executable-spec-display-and-tagging-rules.md 4.2. Their outer afterWord metadata already supplies visible spec/example/rules/phase identities. Envelope E4's initial Given at line171 does supply the governing path and identifiers.
- Priority: P3
- Nonblocking reason: all 23 new behavior groups have actual adjacent setup/action/expectation Given/When/Then boundaries, should expectations, visible E identities and meaningful regression behavior. No missing GWT authoring boundary, metadata-only test or unexecuted registered branch was found.
- Maintenance boundary: add the missing descriptive Given references in a separately admitted presentation-maintenance task, preserving scenarios, assertions, generators and execution thresholds. This new record covers the P96-05 suites and does not duplicate HYG-P96-03-SPEC-TRACEABILITY-001's five earlier suites.
<!-- cncf-phase-finding:HYG-P96-05-GIVEN-TRACEABILITY-001:end -->

<!-- cncf-phase-finding:HYG-P96-05-VALIDATION-PROJECTION-001:begin -->
### HYG-P96-05-VALIDATION-PROJECTION-001

- Kind: hygiene
- Status: OPEN
- Discovery: independent P96-05 protected-focused whole-Step review, 2026-10-05
- Source reviewer: /root/phase96_p96_05_step_review_001
- Evidence: the current introductions and P96-05A sections of docs/spec/display-model-runtime.md, docs/spec/display-model-protocol.md, docs/design/display-model-runtime.md and docs/design/display-model-protocol.md still describe focused execution/validation as pending. The runtime specification's concluding correspondence paragraph specifically calls representative012 and accumulator002 pending. Actual subject12 receipts and the current Phase/checklist/journal status establish representative012 14/14 and accumulator002 266/266 success with released locks.
- Priority: P3
- Nonblocking reason: the operational current-status documents correctly distinguish successful validation from still-pending independent acceptance; the selected normative behavior agrees with the implementation and executed specifications. No false closure or invalid behavioral evidence is created.
- Maintenance boundary: reconcile only undated current execution-status projections with the actual successful receipts, while preserving dated authoring/failure/planned history and leaving independent acceptance, commit and Phase closure to their owners. Do not change contract text, criteria or historical results.
<!-- cncf-phase-finding:HYG-P96-05-VALIDATION-PROJECTION-001:end -->

<!-- cncf-phase-finding:HYG-P96-07-REMAINING-BASELINE-001:begin -->
HYG-P96-07-REMAINING-BASELINE-001 OPEN. Owner: CNCF execution/specification maintenance. ObservabilityContext.scala:92,98,107 retains trailing-underscore private members; Subsystem.scala:92,97,110 retains class-owned result models; Subsystem.scala and UnitOfWorkInterpreter.scala remain 2587/2444-line responsibility concentrations; ContentBodyStoragePolicySpec.scala:115 retains clearRecord local naming. These are pre-existing, nonblocking maintenance risks with no established current behavior failure. Admit a separate compatibility-preserving maintenance boundary; exclude already recorded HTTP, ActionCall, datastore and consumer-fixture debt.
<!-- cncf-phase-finding:HYG-P96-07-REMAINING-BASELINE-001:end -->

<!-- cncf-phase-finding:HYG-P96-C4A-BASELINE-MAINTENANCE-001:begin -->
HYG-P96-C4A-BASELINE-MAINTENANCE-001 OPEN. Owner: CNCF. ActionCallRepositoryPart.scala:154,789 retains a 1857-line trait, broad imports, legacy conventions and instance-owned AggregateRootState. This pre-existing responsibility/type-ownership debt risks maintenance and compatibility, but no current behavior defect was established. Follow up through a separately admitted compatibility-preserving maintenance boundary; do not extract unrelated responsibilities during P96-07 repair.
<!-- cncf-phase-finding:HYG-P96-C4A-BASELINE-MAINTENANCE-001:end -->

<!-- cncf-phase-finding:HYG-P96-C4A-SPEC-DISPLAY-001:begin -->
HYG-P96-C4A-SPEC-DISPLAY-001 OPEN. Owner: CNCF executable specifications. AggregateLifecycleMutationCaptureSpec.scala:31, AggregateLifecycleMutationCapturePropertySpec.scala:14 and PostgresqlAggregateLifecycleMutationCaptureAcceptanceSpec.scala:24 have meaningful ALC identities and behavioral GWT but omit canonical afterWord/Given contract metadata. This is nonblocking traceability presentation debt, distinct from missing semantic GWT. Align metadata separately without changing scenarios, generators, assertions or thresholds.
<!-- cncf-phase-finding:HYG-P96-C4A-SPEC-DISPLAY-001:end -->

<!-- cncf-phase-finding:HYG-P96-C4B-FIXTURE-SIZE-001:begin -->
HYG-P96-C4B-FIXTURE-SIZE-001 remains OPEN. C4C auxiliary assurance observed DisplayMutationServiceFixture.scala at 1620 lines, retaining the previously recorded expected-state/provider/assembly/session/adapter/vocabulary responsibility debt. The bounded List additions remain reviewable and introduce no demonstrated behavior failure. Address decomposition in a separately admitted maintenance task with the affected fixture consumers; do not extract unrelated responsibilities during C4C repair.
<!-- cncf-phase-finding:HYG-P96-C4B-FIXTURE-SIZE-001:end -->

<!-- cncf-phase-finding:HYG-P96-C4B-HTTP-DECLARATIONS-001:begin -->
HYG-P96-C4B-HTTP-DECLARATIONS-001 OPEN. Owner: CNCF HTTP. Http4sHttpServer.scala retains pre-existing instance-owned concrete models and tuple/local naming debt. These are compatibility-sensitive maintenance concerns, not newly introduced declaration violations or demonstrated P96-07 behavior failures. Address in a separately admitted naming/type-ownership task preserving route and nested API contracts.
<!-- cncf-phase-finding:HYG-P96-C4B-HTTP-DECLARATIONS-001:end -->

<!-- cncf-phase-finding:HYG-P96-C4B-HTTP-SIZE-001:begin -->
HYG-P96-C4B-HTTP-SIZE-001 OPEN. Owner: CNCF HTTP. Http4sHttpServer.scala has 8154 lines combining routes, forms, jobs, assets, authentication, rendering and transport. The pre-existing responsibility concentration exceeds size thresholds and increases maintenance risk; the bounded ingress change remains reviewable and no resulting behavior defect was established. Plan a separate responsibility-based decomposition with affected consumers; no incidental extraction in P96-07.
<!-- cncf-phase-finding:HYG-P96-C4B-HTTP-SIZE-001:end -->

<!-- cncf-phase-finding:HYG-P96-C4C-DATASTORE-MAINTENANCE-001:begin -->
HYG-P96-C4C-DATASTORE-MAINTENANCE-001 OPEN: DataStore.scala is 1125 lines, above the ordinary 1000-line source-debt threshold, and retains datastore/identifier/memory-provider/query responsibilities plus commented legacy code. Whole-file C4C assurance found no resulting current behavior defect. Plan a separate compatibility-preserving maintenance boundary covering nested public identities and affected consumers; preserve existing provider synchronization and mutation semantics.
<!-- cncf-phase-finding:HYG-P96-C4C-DATASTORE-MAINTENANCE-001:end -->

<!-- cncf-phase-finding:HYG-P96-C4C-SPEC-DISPLAY-001:begin -->
HYG-P96-C4C-SPEC-DISPLAY-001 OPEN: the four C4C List suites have meaningful LST scenario identities, visible governing-document references, adjacent GWT and behavioral assertions, but their afterWord/Given metadata does not follow the canonical spec/example/rules/phase display format. This is nonblocking traceability maintenance. Align presentation in a separate maintenance task without changing scenarios, generators, thresholds or production contracts.
<!-- cncf-phase-finding:HYG-P96-C4C-SPEC-DISPLAY-001:end -->

<!-- cncf-phase-finding:HYG-P96-CONSUMER-LEGACY-FIXTURE-001:begin -->
HYG-P96-CONSUMER-LEGACY-FIXTURE-001 OPEN. Owner: CNCF executable specifications. DisplayMutationUpdateBridgeFixture.scala retains historical helper/local naming; EntityMutationProviderContractSpec retains its historical policy-parameter convention; UnitOfWorkVersionedMutationSpec.scala:1026,1248 retains instance-owned fixture models. These predate the admitted changes and require a separate consumer-aware maintenance boundary. They do not excuse the newly introduced Sample violation or authorize fixture redesign.
<!-- cncf-phase-finding:HYG-P96-CONSUMER-LEGACY-FIXTURE-001:end -->

<!-- cncf-phase-finding:HYG-P96-CREATE-001:begin -->
P96-CREATE-HYG-001 OPEN. Owner: CNCF datastore binding. DataStoreSpace.scala:29 retains the pre-existing class-owned Binding data model. Stable type ownership requires a compatibility-aware change involving its consumers; no new P96-07 behavior defect was established. Preserve the existing candidate and address it in a separately admitted maintenance task, not the observed-delete Sample repair.
<!-- cncf-phase-finding:HYG-P96-CREATE-001:end -->

<!-- cncf-phase-finding:HYG-P96-CREATE-003:begin -->
P96-CREATE-HYG-003 OPEN. Owner: CNCF SQL provider. SqlDataStore.scala retains historical protected-hook naming/API debt and now has 3177 lines of provider, schema, comparison, transaction and lifecycle responsibilities. This requires compatibility-preserving maintenance across provider subclasses; no current behavioral failure or new hook-role naming violation was established. Preserve existing hooks during P96-07 repair and plan decomposition separately.
<!-- cncf-phase-finding:HYG-P96-CREATE-003:end -->
