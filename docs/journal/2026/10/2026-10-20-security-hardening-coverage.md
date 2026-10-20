# CNCF Security Hardening Coverage

Date: 2026-10-20

## Question

What must CNCF add or strengthen to approach the security strength of a purpose-built, minimal-functionality, compartmentalized HTTPS system while remaining a general-purpose enterprise runtime?

## Conclusion

The major defensive categories are now covered by existing CNCF mechanisms plus a focused set of extensions. CNCF cannot match the physical code-surface and trusted-computing-base reduction available to a dedicated minimal runtime on dedicated infrastructure, but this is an inherent specialization advantage rather than an uncovered application-security category.

## CNCF hardening model

```text
SAR Archetype
  -> Capability Profile + Security Profile + other characteristic Profiles
  -> SAR Capability upper bound
  -> CAR Capability upper bound
  -> signed CAR / trusted Component Repository admission
  -> Safe JVM lint/admission
  -> Authorization / Guard
  -> managed Capability facade / Safety Sandbox
  -> optional per-CAR Docker/process isolation
  -> Security Property Verification
  -> Security Evidence / audit
```

### Least Functionality

CNCF keeps generic facilities in the runtime but removes unnecessary facilities from a particular subsystem's semantic execution world. Capability sets monotonically narrow from runtime to SAR to CAR to Operation/ExecutionContext. Lower levels cannot widen upper boundaries.

### Memory-safety baseline

Normal CARs use managed JVM execution. CAR lint/admission reject JNI, Unsafe/internal low-level access, arbitrary native loading, direct external IO, and comparable escape paths under hardened policy. This covers the principal C/C++-style memory-corruption concerns addressed by safe Rust for ordinary application code. Native/unsafe workloads require Hard Sandbox isolation.

### Physical compartmentalization

When stronger containment is required, each CAR can run in a distinct Docker/process space. The logical CAR Capability Boundary should be projected onto container network, filesystem, volume, secret, endpoint, and process policy. Hardened CAR-to-CAR communication goes through CNCF-managed Component/Service/Operation invocation or a governed bridge, not arbitrary direct networking.

### Supply-chain authenticity

CAR is the trust unit. Electronic signatures cover executable artifacts and security-relevant metadata such as descriptors, Capability declarations, dependencies, configuration/resources, and manifest data. Component Repository verification/provenance and CNCF admission form the trust chain. Signature validity proves authenticity/integrity; it never grants authority beyond the SAR boundary.

### Security Profile

Security Profile selects the technical security posture for the SAR: allowed/forbidden protocols, TLS baseline, crypto/PQC policy, isolation strength, signed-CAR/trusted-publisher requirements, secret handling, and audit/evidence requirements. Capability Profile answers what abilities may exist; Authorization/Guard answers whether a concrete subject/resource/action is allowed.

### Security Property Verification

CNCF should mechanically verify bounded security invariants where feasible, for example: no process Capability means no managed process execution; ReadOnlyWeb means no datastore mutation; hardened CAR isolation means no undeclared direct CAR network path; and only protocols admitted by Security Profile can activate.

### Security Evidence

A hardened SAR should expose inspectable evidence that the selected Archetype/Profile is actually realized: resolved Capability boundaries, CAR signature/publisher/provenance status, Safe JVM lint/admission results, container-isolation realization, active protocol/TLS/crypto policy, security-property results, and capability violation/audit evidence.

## Coverage summary

| Security objective | CNCF response |
| --- | --- |
| Safe application memory model | Managed JVM + Safe JVM lint/admission |
| Eliminate unsafe escape paths | JNI/Unsafe/native restrictions |
| Least functionality | SAR Archetype + Capability Profile |
| Least privilege | SAR/CAR Capability upper bounds + grants |
| Authorization | Capability + Authorization/Guard |
| Compartmentalization | per-CAR process/container isolation |
| Lateral-movement control | governed CAR invocation + network isolation |
| Artifact/config authenticity | signed CAR |
| Ecosystem provenance | Component Repository trust chain |
| Protocol minimization | Security Profile |
| TLS/crypto/PQC policy | Security Profile |
| Pre-execution checking | CAR lint + admission |
| Runtime enforcement | managed Capability facades + Safety Sandbox |
| Mechanical assurance | Security Property Verification |
| Operational assurance | Security Evidence + observability/audit |

## Defense in depth

```text
untrusted input
  -> application/domain validation
  -> Operation Authorization / Guard
  -> CAR Capability Boundary
  -> SAR Capability Boundary
  -> managed Capability facade
  -> Safety Sandbox
  -> optional CAR container boundary
  -> network/protocol/Security Profile policy
  -> host/runtime boundary
```

An application vulnerability should therefore not imply arbitrary process execution, filesystem mutation, network egress, datastore mutation, native access, or lateral CAR access unless the corresponding Capability and physical policy explicitly permit it.

## Residual difference

A dedicated minimal system can physically omit functionality, use a smaller runtime/OS configuration, and potentially dedicated hardware, thereby reducing both attack surface and trusted computing base below what a JVM/container/Linux-based general-purpose CNCF deployment can normally achieve.

CNCF should not attempt to erase that architectural difference by removing general-purpose facilities. Its strategy is layered defense: semantic least-functionality through Capability boundaries, managed-JVM safety, signed supply-chain trust, admission, runtime enforcement, optional physical CAR isolation, Security Profile hardening, mechanical property verification, and inspectable Security Evidence.

Within those constraints, no major security category identified in the comparison remains without a CNCF counterpart or extension direction.
