# Git execution route correction

Date: 2026-10-26
Status: in progress for the existing repository route repair; subsequent items open.
Owner: SimpleModeling development (CNCF producer).
Contract: [Git defaults and diagnostics](../spec/git-execution-defaults-and-diagnostics.md).
Related work: [Phase 110 P110-04](phase-110.md); no new numbered Phase.

## Delivery order

1. GIT-C1: Finish the current repository Git route repair with default PATH Git
   and optional executable override. Reuse the in-progress plan/codec/DSL work;
   do not require per-operation runtime program registrations. Validate and
   deliver through the existing normal runtime route, then continue the same
   synchronization Session. Do not wait for all recovery work to finish.
2. GIT-C2: Correct actual normal-environment/authentication blockers and useful
   failure diagnostics. If either blocks C1's real route, include the necessary
   part in that delivery; otherwise deliver it as the next bounded correction.
3. GIT-C3: Hand the usable producer version and verification to sm-workflow for
   fetch-state adoption and commit/push/fetch reconciliation. Reuse producer
   guarantees instead of adding a second executable or parser implementation.
4. GIT-C4: Support final connected acceptance and removal of redundant checks.
   This does not reopen Phase 116 or require completion of all Phase 110 work.

## Checklist

- [ ] GIT-C1a: Omitted executable resolves normal PATH Git; no per-operation
  configuration is needed for supported typed operations.
- [ ] GIT-C1b: Explicit executable precedence, unavailable default executable and
  invalid explicit override have appropriate behavior; target/permission scope
  remains effective and future operations do not silently gain authority.
- [ ] GIT-C1c: Relevant producer verification, normal runtime delivery and actual
  same-Session continuation are reported separately with retained evidence.
- [ ] GIT-C2a: Normal configured authentication works; no blanket credential
  helper/global-configuration disabling is required by the production route.
- [ ] GIT-C2b: Exit/termination, stderr or evidence link, and parser reason remain
  available to the consumer; failed parsing is not proof of no Git mutation.
- [ ] GIT-C3: sm-workflow receives a usable version, actual results and remaining
  limitations; no duplicate compilation/test/publication just to recreate a report.
- [ ] GIT-C4: Connected normal synchronization and consumer recovery results are
  linked without claiming this producer alone completes the consumer contract.

## Continuity

Keep Session `8f27ea7624b104a81e0b97e8ebe5eb50`, accepted work, original scope,
history, counters, failures and all validation evidence. No re-Start, baseline
reset, fabricated success or internal-store rewriting. Reuse unaffected results;
new behavior needs relevant new evidence. This document does not launch commands,
alter grants, publish artifacts or mark implementation complete.

See the [English handoff](../journal/2026/10/2026-10-26-git-execution-and-recovery-handoff.md).
