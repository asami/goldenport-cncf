# Fixed-Profile Service Ingress Specification

Status: normative

## Rules

### R1 Provider-authenticated Service admission

When a fixed execution profile receives authentication ingress material, CNCF
admits the request only when an admitted authentication provider resolves a
subject whose kind is `Service`. The resolved provider identity and canonical
runtime bindings remain authoritative.

### R2 User rejection

A provider-authenticated subject whose kind is not `Service` is rejected for a
fixed execution profile.

### R3 Unmatched rejection

Authentication ingress material that no admitted provider resolves is rejected
for a fixed execution profile. Privilege fallback and request-provided user
identity do not convert it into fixed-profile admission.

## Example

### E1 Fixed service ingress

A service token is admitted while an ordinary-user token and an unmatched token
are rejected. Executable coverage: `IngressSecurityResolverSpec` E1.
