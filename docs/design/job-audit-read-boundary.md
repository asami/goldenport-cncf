# Job Audit Read Boundary

CC4-S11 preserves configured Production Web jobs access for `audit_viewer`
through a dedicated `JobExperienceScope.Audit`. Operator continues to require
`job_admin` or `content_admin`. Audit is authority for bounded reads within the
existing subsystem, with no new Job source or management capability.

`JobExperiencePolicy` and `JobExperienceService` accept an explicit optional
runtime operation security policy. Their original one-argument JVM constructors
delegate to absent policy. Audit requires Production on both supplied policy
and actual execution context and delegates to the existing jobs admin rule and
`OperationAuthorization`; it never infers an ambient policy or identity.

The Http4s server supplies its resolved policy and selects Audit only after
actual context resolution for an Operator Web read lacking the two Operator
capabilities. The same resolved scope drives codec, service and rendering.
Outer Web descriptor/authentication checks remain in force. Default REST and
protocol construction has no Audit authority.

Audit reuses `JobManagementReader` with a distinct visibility key and existing
subject/filter/snapshot cursor binding. Detail queries only bounded management
detail. An absent optional management result projects unavailable, empty
controls and no polling; no synthetic Job result or state is created. Audit
never admits diagnostics or control, including mixed-capability subjects.
Web links retain existing system admin jobs paths, opaque denial, no-store and
manual refresh behavior. Rendering and reader ownership remain unchanged.

The normative contract is
[Job User and Operator Experience](../spec/job-user-operator-experience.md),
with executable evidence in `JobAuditReadSpec` and `JobExperienceHttpSpec`.
