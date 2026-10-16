# Candidate-Admission submission retention

Status: bounded Phase 88 producer contract

The application binds an explicit semantic result type and candidate, evidence
and scope PayloadCodecs. CandidateAdmissionSubmissionJsonV1 preserves the
existing AdmissionSubmission model without interpreting application scope.
Its exact versioned JSON envelope contains submission identity, typed candidate
snapshot and ordered evidence records using CandidateAdmissionEvidencePersistence.JsonV1.
Nested submission identities must match; duplicate evidence identities,
undeclared fields, incompatible types and non-exact/throwing codecs fail closed.

WorkflowCompletionDataStorePersistence may consume this codec as its declared
result payload. The existing canonical guarded transaction retains that exact
result, ExecutionEvidence, Completed claim and instance history together.
Recovery is read-only, requires the original admitted catalog and codecs,
and never grants admission, progression, a claim or effect replay.

Shape-valid wrong-target, stale or insufficient-scope evidence must be preserved.
Only CandidateAdmissionModel.evaluateC with application EvidencePolicy decides
whether recovered evidence satisfies requirements. Stored completion is not
positive admission or external-command authority.

Executable specifications: CandidateAdmissionSubmissionJsonV1Spec and
CandidateAdmissionCompletionPersistenceSpec. Existing S5 revision/token,
rollback and transaction-indeterminate rules are unchanged. This contract does
not assert active UnitOfWork event/data enlistment, production host/Operation
composition or separate-process recovery.
