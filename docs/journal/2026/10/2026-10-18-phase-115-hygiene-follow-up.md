<!-- cncf-phase-finding:HYG-CNCF-P115-001:begin -->
### HYG-CNCF-P115-001 — Remove a literal patch marker from row-mapping prose
Status: OPEN
Owner: CNCF documentation maintenance.
Evidence: docs/spec/entity-store-row-mapping.md:214 begins
"+The historical Phase 100 package path..." in the RCP-MAIN delta.
Risk: A stray character appears in rendered specification prose;
the stated contract and historical evidence remain understandable.
Disposition: Nonblocking presentation maintenance.
Follow-up boundary: Remove only the leading "+" in a documentation
maintenance task; preserve the paragraph's meaning and evidence.
<!-- cncf-phase-finding:HYG-CNCF-P115-001:end -->

<!-- cncf-phase-finding:HYG-CNCF-P115-002:begin -->
### HYG-CNCF-P115-002 — Existing executable-specification conformance debt
Status: OPEN
Owner: /Users/asami/src/dev2025/cloud-native-component-framework
Evidence: CanonicalRuntimeStorageInitializationSpec.scala:20–41,68 retains comment-only Given/When/Then and nonconforming private/local names; CncfRuntimeStorageInitializationSpec.scala:23–41 retains nonconforming private helpers; StartupDataImportSpec.scala:365 lacks a Then clause in its unchanged no-input scenario.
Risk: Reduced specification readability and consistency. These issues predate the admitted repairs; the controlled-YAML fixture changes introduce none of them.
Disposition: Nonblocking Hygiene.
Follow-up boundary: A separate specification-maintenance task preserving existing behavior and expectations.
<!-- cncf-phase-finding:HYG-CNCF-P115-002:end -->
