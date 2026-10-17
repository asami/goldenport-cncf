# Hygiene follow-up — Job test-selection commit

Document role: non-normative maintenance dispositions.
Discovery date: 2026-10-13.
Workflow: ordinary reasoned commit of the Job test-selection policy.
Repository: cloud-native-component-framework.

These inherited items remain outside the test-selection implementation boundary.
They do not authorize edits to runtime behavior, existing scenario bodies or Phase status.

## HYG-JOB-TEST-SELECTION-20261013-BRIDGE-001

- Status: OPEN.
- Priority: P3.
- Discovery state: commit preparation and independent focused review.
- Scheduling/resolution references: none.

HYG-JOB-TEST-SELECTION-20261013-BRIDGE-001 — Pre-existing version-update bridge drift. Owner: cloud-native-component-framework. docs/ai/shared-directive-bridge.md:34 and :43 require unconditional preservation of existing history lines and confirmation that none was removed. The bridge also declares the mounted shared directive authoritative and prohibits locally forking it. ai/directive/samples/version-update-instruction.md explicitly requires removal of history entries matching @since exactly or sharing the final latest @version month/year. The current Job test-selection headers follow that explicit canonical instruction and preserve every other history entry in its original order. Risk: future agents may follow contradictory operational guidance. Classification: nonblocking Hygiene; the conflict predates this change and supplies no current behavioral or validation defect. Maintenance boundary: a separately bounded documentation update qualifying the bridge's preservation checks with the shared instruction's explicit cleanup exceptions, without changing shared directives, Scala behavior, accepted header dates, test selection or Phase authority.

## HYG-JOB-TEST-SELECTION-20261013-NAMING-001

- Status: OPEN.
- Priority: P3.
- Discovery state: commit preparation and independent focused review.
- Scheduling/resolution references: none.

HYG-JOB-TEST-SELECTION-20261013-NAMING-001 — Inherited Job specification naming debt. Owner: cloud-native-component-framework. Complete-target scans identify unchanged local lowerCamel names in src/test/scala/org/goldenport/cncf/job/JobControlCommandSpec.scala:116-117, JobEntityCollectionIdentitySpec.scala:36-37, JobTerminalStateRecordOnlySpec.scala:76 and JobWaitResourceBoundsSpec.scala:651; unchanged local names and leading-underscore private type declarations in JobRetryStateTransitionSpec.scala:39,207,223,237 and JobRetryTimingSpec.scala:42,251,267,281; and unchanged private fixture terms/local names in src/test/scala/org/goldenport/cncf/job/SCENARIO/JobLifecycleScenarioSpec.scala:57,129-130,351. Examples include retryEntered, decodedJob, errorEventType, closeAttempts, _CountingTask and serviceFactory. These declarations and their uses are identical to HEAD outside version/tag metadata. Risk: naming conformance and specification readability only. Classification: nonblocking Hygiene. Maintenance boundary: separately freeze the exact private/local identifiers and rename their internal references, preserving required overrides, public/protected parameter labels, serialized fields, fixtures, assertions, test coverage and Job behavior. No broad naming sweep or current acceptance repair is authorized.

## HYG-JOB-TEST-SELECTION-20261013-GWT-001

- Status: OPEN.
- Priority: P3.
- Discovery state: commit preparation and independent focused review.
- Scheduling/resolution references: none.

HYG-JOB-TEST-SELECTION-20261013-GWT-001 — Inherited retry specification narrative debt. Owner: cloud-native-component-framework. The five existing in blocks in src/test/scala/org/goldenport/cncf/job/JobRetryStateTransitionSpec.scala:29,52,91,125,160 and six existing in blocks in JobRetryTimingSpec.scala:30,59,111,150,172,203 contain no semantic Given/When/Then clauses although both specifications mix in GivenWhenThen. Their complete scenario bodies, fixtures and should assertions are unchanged from HEAD; the current delta changes only version metadata and suite selection. Risk: execution output does not expose the setup/action/expectation narrative required by executable-document style. Classification: nonblocking Hygiene, not a current AUTHORING_GATE_FAILURE. Maintenance boundary: a separately bounded executable-spec maintenance task adding adjacent semantic clauses at the actual boundaries in these eleven scenarios while preserving timing selection, synchronization, assertions and Job behavior. Do not weaken tests or absorb runtime redesign into this maintenance.

## HYG-JOB-TEST-SELECTION-20261013-GROUPING-001

- Status: OPEN.
- Priority: P3.
- Discovery state: commit preparation and independent focused review.
- Scheduling/resolution references: none.

HYG-JOB-TEST-SELECTION-20261013-GROUPING-001 — Inherited multi-area Job specification organization. Owner: cloud-native-component-framework. src/test/scala/org/goldenport/cncf/http/JobExperienceHttpSpec.scala:34 has 21 existing examples across Web/REST list, detail, control and HTTP security behavior; src/test/scala/org/goldenport/cncf/job/JobExperienceSpec.scala:19 has 20 existing experience examples; and JobEngineResourceBoundsSpec.scala:26 has 25 existing resource examples. These chapters retain a flat example surface without which feature-area subdivisions. Their complete organization and scenario bodies predate the current version/tag delta. Risk: executable-document navigation and maintenance clarity only; no current behavior or selection defect was identified. Classification: nonblocking Hygiene. Maintenance boundary: separately freeze a behavior-preserving specification organization task using which or equivalent feature-area grouping, preserving test names where compatibility matters, all GWT clauses, assertions, property counts, fixtures and Job contracts. This item does not authorize product changes, file splitting or current acceptance repairs.
