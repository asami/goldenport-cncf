# Phase 58.5 Checklist - Resource Authorization, Disclosure, and Integrity

status=in_progress
phase=[Phase 58.5 - Resource Authorization, Disclosure, and Integrity](phase-58.5.md)
predecessor=[Phase 58.4](phase-58.4.md)
successor=[Phase 58.6](phase-58.6.md)

## RSC-06: Authorization, Disclosure, and Integrity

Stage Status:
- Current status: IN PROGRESS
- Owner: CNCF security, repository, and source-policy maintainers
- Entry rule: Phase 58.4 RSC-05 is DONE.
- Completion rule: Resource and child access is authorized, integrity-checked, and non-leaking in every resolution form.
- Update rule: DONE requires the mandatory full Phase review and successful release commit.

- [x] Enforce role and resource access policy before content exposure.
- [x] Represent restricted source without disclosing or indexing it.
- [x] Verify digest, signature, parent, release, and repository evidence.
- [x] Reject path traversal, symlink escape, and archive ambiguity.
- [x] Keep repository credentials and signed access material out of manifests.
- [x] Keep source content, credentials, host paths, and repository secrets out of diagnostics, metrics, and CallTree.
- [x] Verify authorization cannot be granted by a manifest alone.
- [x] Verify a parent registry cannot grant Operation authority, MCP access, or executable-child activation.
- [x] Add hostile archive, corrupt cache, unauthorized source, and disclosure regression tests.

Evidence:
- Failing-first RED: serialized SBT invocation `5044-20260820T220922Z`; only the absent RSC-06 policy API caused the intended compile failures (22 errors); serial lock released.
- Intermediate repair: first focused execution `7469-20260820T221439Z` exposed two policy defects, and bounded fixes repaired parent primary-CAR membership and denial-before-content integrity ordering.
- Final focused GREEN: invocation `9775-20260820T221958Z`, `ComponentResourceAuthorizationSpec`, 1 suite / 10 succeeded / 0 failed / lock released.
- Direct-consumer accumulator GREEN: invocation `10569-20260820T222053Z`, `ComponentResourceAuthorizationSpec`, `ResolvedComponentResourcesSpec`, and `ComponentResourceOperationModePolicySpec`; 3 suites / 34 succeeded / 0 failed / lock released.
- `D-58.5-STEP-REVIEW-STOP: AUTHORIZE_PROTECTED_STEP_REVIEW_EXCEPTION` permits only omission of the otherwise-required lightweight Luna Step review for this protected security boundary. The Step commit, mandatory Terra xhigh full Phase review, frozen-tree full `sbt --batch test`, and release commit remain pending.
