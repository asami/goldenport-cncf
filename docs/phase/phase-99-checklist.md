# Phase 99 Checklist: Finding Disposition ABI

status=planned

- [ ] Freeze normalized Finding Disposition model.
- [ ] Freeze stable rule/finding identity and target binding.
- [ ] Define CNCF public Scala annotation ABI.
- [ ] Define disposition values and reason semantics.
- [ ] Define ABI compatibility/deprecation rules.
- [ ] Provide deterministic CAR-lint-facing interpretation API.
- [ ] Prove Accept and Defer fixtures.
- [ ] Prove source/model correction removes the underlying finding without suppression.
- [ ] Prove unknown/new rule IDs do not require ABI change.
- [ ] Document CML-property mapping boundary for Cozy.
- [ ] Document that Human Admission is external to the annotation ABI.
- [ ] Validate that annotation insertion cannot silently become the default auto-fix policy.
