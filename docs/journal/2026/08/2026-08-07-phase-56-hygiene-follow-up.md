# Phase 56 Hygiene Follow-up

status=open
date=2026-08-07

The following accepted hygiene records are outside the frozen CID-04 identity
and repository behavior boundary and remain separate follow-up work.

| ID | Status | Discovered | Repository / path | Evidence | Category / risk | Boundary and proposed follow-up |
| --- | --- | --- | --- | --- | --- | --- |
| HYG-P56-001 | OPEN | 2026-08-07, CID-02/04 review | `cncf-collaborator-api`; `docs/spec/test-policy.md` | Repository has no `docs` directory or executable-test policy document. | documentation/test-policy, P2 | Outside CID-04 identity/repository behavior; collaborator-API documentation hygiene. |
| HYG-P56-002 | OPEN | 2026-08-07, CID-04C review | `cozy`; `src/main/scala/cozy/archive/CozyArchivePackager.scala`, URLConnection around line 482 | Direct HTTP read lacks separate runtime-integration evidence. | runtime/network integration, P2 | Outside frozen descriptor/publication repair; Cozy HTTP runtime integration hygiene. |
| HYG-P56-003 | OPEN | 2026-08-07, CID-04D review | `sbt-cozy`; `CarComponentIdentityAdapter.scala` `projectRelease`/`carfilename`/`mavencoordinate`, callsites `CozyProjectIdentityContract.scala` lines 83, 99-100 | Naming cleanup crosses the frozen target boundary. | naming/API cleanup, P3 | Outside CID-04D settled public behavior; coordinated adapter/callsite hygiene. External Textus two-argument `CarDependency` migration remains CID-08 future-phase work. |
| HYG-P56-004 | OPEN | 2026-08-07, CID-04C re-review | `cozy`; `src/main/scala/cozy/archive/CozySarPublisher.scala`, system `Files.createTempFile("cozy-publish-sar-", ".sar")` around line 25 | Unchanged SAR path outside CAR transaction scope. | SAR publication staging, P3 | SAR staging hygiene. |
