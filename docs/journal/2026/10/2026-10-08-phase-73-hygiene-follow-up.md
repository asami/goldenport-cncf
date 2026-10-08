<!-- cncf-phase-finding:HYG-P73-MR02-001:begin -->
HYG-P73-MR02-001: The unchanged supporting sources Component.scala (1295 physical lines) and CncfRuntimeBootstrapPart.scala (1287 physical lines) exceed the shared RULE.md 1000-line source-size debt threshold. Component.scala combines component initialization/runtime facilities with Core and factory/bundle definitions; the bootstrap part combines embedding construction and several CLI/bootstrap responsibilities. No current main-route correctness failure or increase in these files was found. Owner: goldenport-cncf maintainers. Target: a separately bounded source-organization maintenance task after Phase 73 acceptance. Dependency and resume condition: admit complete public, protected, initialization and runtime-entrypoint preservation requirements and focused validation before decomposition. A safe disposition requires coordination with existing callers and lifecycle participants. This is nonblocking inherited supporting-file debt. Prohibited workaround: arbitrary extraction, cosmetic line compression, public API changes or an incidental split during this Step.
<!-- cncf-phase-finding:HYG-P73-MR02-001:end -->

<!-- cncf-phase-finding:HYG-P73-MR02-002:begin -->
HYG-P73-MR02-002: The unchanged supporting ComponentActivationLifecycleSpec.scala declares the method-local helper collect at line 750 without the shared RULE.md leading-and-trailing underscore convention for method-local helpers. Its recursive references share that pre-existing naming debt. Owner: goldenport-cncf maintainers. Target: a separately admitted lifecycle-spec naming maintenance task. Dependency and resume condition: preserve the existing recursive causal-conclusion traversal and its executable coverage when that test file is next intentionally maintained. This is nonblocking supporting-file naming debt and does not invalidate the retained lifecycle validation. Prohibited workaround: changing traversal behavior, weakening redaction expectations or expanding the current main-route repair into historical lifecycle cleanup.
<!-- cncf-phase-finding:HYG-P73-MR02-002:end -->

<!-- cncf-phase-finding:HYG-P73-FULL-001:begin -->
## HYG-P73-FULL-001 — Inherited Subsystem source-size debt

- Evidence: src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala contains 2634 lines at ca3045d5bce3f52abc2d6582900f505fb0387ae7 and is unchanged throughout the PHASE-73 original-base accumulator. It exceeds the shared source-size review thresholds, including 2000 lines. Assembly/admission and component binding, service integration, operation/HTTP execution and shutdown responsibilities coexist in the file.
- Risk: The size and interacting responsibilities increase the cost of inspecting lifecycle and ownership changes and the chance of incidental coupling during future maintenance.
- Owner: goldenport-cncf maintainers.
- Target: A separately admitted Subsystem source-organization maintenance task after PHASE-73.
- Dependency: Preserve existing public/protected APIs, component/context injection, runtime ownership, activation and shutdown contracts; select corresponding executable specifications before extraction.
- Resume condition: Resume when that maintenance task explicitly freezes a responsibility-based extraction boundary and its focused validation.
- Nonblocking reason: This is inherited supporting-file debt. PHASE-73 does not enlarge or alter Subsystem.scala, and the reviewed contribution behavior does not require a split.
- Prohibited workaround: Do not compress formatting, make arbitrary partial extractions, or change assembly/lifecycle/API behavior inside a PHASE-73 blocker repair merely to reduce line count.
<!-- cncf-phase-finding:HYG-P73-FULL-001:end -->

<!-- cncf-phase-finding:HYG-P73-FULL-002:begin -->
## HYG-P73-FULL-002 — Inherited supporting public-parameter naming debt

- Evidence: Unchanged supporting declarations use flatcase canonical public parameters: Subsystem.scala:742 registerEventReception uses componentname with @deprecatedName("componentName", "0.5.1"); KnowledgeModel.scala:199-200 ExternalKnowledgeIdentifier.entity uses entityname/entityid, and lines 205-206 tag uses tagspace/tagid. Shared naming rules require public method parameters to use camelCase and explicitly state that deprecated labels do not make a nonconforming canonical declaration compliant.
- Risk: Inconsistent canonical named-argument labels make source APIs harder to use consistently; careless renaming can break existing named callers.
- Owner: goldenport-cncf maintainers.
- Target: A separately admitted supporting-API naming maintenance task.
- Dependency: Inventory named callers and retain required source compatibility and serialized/domain field contracts. This record concerns method parameter labels, not a schema-field migration.
- Resume condition: Resume when the maintenance task freezes exact declarations, callers and compatibility handling.
- Nonblocking reason: These declarations predate and remain unchanged by PHASE-73. The contribution route neither depends on their label spelling nor requires their modification.
- Prohibited workaround: Do not rename these supporting APIs or serialized fields incidentally in a PHASE-73 repair; do not treat deprecated labels as canonical conformance.
<!-- cncf-phase-finding:HYG-P73-FULL-002:end -->
