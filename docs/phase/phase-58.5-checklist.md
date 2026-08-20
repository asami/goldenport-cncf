# Phase 58.5 Checklist - Resource Authorization, Disclosure, and Integrity

status=planned
phase=[Phase 58.5 - Resource Authorization, Disclosure, and Integrity](phase-58.5.md)
predecessor=[Phase 58.4](phase-58.4.md)
successor=[Phase 58.6](phase-58.6.md)

## RSC-06: Authorization, Disclosure, and Integrity

Stage Status:
- Current status: PLANNED
- Owner: CNCF security, repository, and source-policy maintainers
- Entry rule: Phase 58.4 RSC-05 is DONE.
- Completion rule: Resource and child access is authorized, integrity-checked, and non-leaking in every resolution form.

- [ ] Enforce role and resource access policy before content exposure.
- [ ] Represent restricted source without disclosing or indexing it.
- [ ] Verify digest, signature, parent, release, and repository evidence.
- [ ] Reject path traversal, symlink escape, and archive ambiguity.
- [ ] Keep repository credentials and signed access material out of manifests.
- [ ] Keep source content, credentials, host paths, and repository secrets out of diagnostics, metrics, and CallTree.
- [ ] Verify authorization cannot be granted by a manifest alone.
- [ ] Verify a parent registry cannot grant Operation authority, MCP access, or executable-child activation.
- [ ] Add hostile archive, corrupt cache, unauthorized source, and disclosure regression tests.

Evidence:
- Pending.
