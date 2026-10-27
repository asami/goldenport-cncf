# Phase 99 Checklist: Finding Disposition ABI

status=in-progress

- [x] Freeze normalized Finding Disposition model.
- [x] Freeze stable rule/finding identity and target binding.
- [x] Define CNCF public Scala annotation ABI.
- [x] Define disposition values and reason semantics.
- [x] Define ABI compatibility/deprecation rules.
- [x] Provide deterministic CAR-lint-facing interpretation API.
- [ ] Prove Accept and Defer fixtures.
- [ ] Prove source/model correction removes the underlying finding without suppression.
- [ ] Prove unknown/new rule IDs do not require ABI change.
- [x] Document CML-property mapping boundary for Cozy.
- [x] Document that Human Admission is external to the annotation ABI.
- [ ] Validate that annotation insertion cannot silently become the default auto-fix policy.

Implementation: [ABI contract](../spec/finding-disposition.md),
[design](../design/finding-disposition.md), `FindingDispositionV1`,
`FindingDispositionRepresentation` and public `finding.annotation` interfaces.
Fixtures are authored in `FindingDispositionSpec`; execution and independent
review remain pending. Checked implementation/documentation items do not claim
test success or Phase closure.
