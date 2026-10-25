# Workflow mechanical receipt capacity

## R1 — Finite immutable policy

`WorkflowMechanicalReceiptLimits` selects the maximum size of one complete
mechanical receipt as an integer number of UTF-8 bytes. Its public `createC`
factory accepts the inclusive range **1–67,108,864 bytes (64 MiB)**. CNCF's
`default` is **1,048,576 bytes (1 MiB)**. The ceiling is a finite retention policy; it does
not promise retention of arbitrary histories.

`WorkflowInstancePersistence.Configuration` carries standard derivable data:
`mechanicalReceiptMaxBytes: Option[Int] = None` alongside its existing five
policies. Existing five-argument constructor and companion `apply` calls retain
`None`, which `mechanicalReceiptLimitsC` resolves to the 1 MiB CNCF default.
`Some(bytes)` resolves through the validated limits factory. Configuration
validation rejects a null Option container and explicit integers outside the
same inclusive range with the existing missing-persistence-policy diagnostic.
Resolution returns typed failures rather than throwing or coercing values.

Ordinary aggregate codec derivation remains sufficient; no dedicated downstream
Configuration encoder or decoder is required. Historical JSON containing only
the original five required fields decodes the missing optional field as `None`;
ordinary optional JSON null also denotes `None`. Passive integer decoding is
data transport, while current operational configuration validation owns range
admission. These passive Option semantics do not change the strict normal
Component property decoder below. Current SNAPSHOT consumers must be rebuilt;
this addition does not promise full binary compatibility for case-class
extractors or `copy`, or upgrading old binaries without rebuilding.

## R2 — Normal optional Component parameter

The public optional Component declaration is exactly
`workflow.mechanical-receipt.max-bytes`. It accepts an integer
`ConfigurationValue.NumberValue` or an exact ASCII decimal string. Fractions,
overflow, zero, negative or above-ceiling values, whitespace, signs, exponent
strings, Boolean, null and structured values fail as typed configuration
errors. `WorkflowMechanicalReceiptParameters.resolveC` returns `Option[Limits]`;
the consumer chooses its absence default. An undeclared snapshot is an error.

Declare `WorkflowMechanicalReceiptParameters.maxBytes` in the Component's
normal initialization declarations. For example, add this property to the
host's normally loaded JSON configuration alongside its existing settings:

```json
{
  "workflow.mechanical-receipt.max-bytes": 16777216
}
```

The existing Component parameter resolver chooses, from highest to lowest:
test overlay, runtime configuration, subsystem instance, assembly default,
packaged default. Host home/project configuration sources have already been
resolved upstream into runtime configuration. Receipt policy adds no home or
project search, system-property/environment reread, override registry or new
project customization mechanism. The property is configuration, not a Workflow
Operation argument. Independent Component snapshots retain their own values.

## R3 — Bound the actual complete envelope

The writer counts the actual compact JSON's UTF-8 bytes after encoding, and the
reader counts the supplied JSON's UTF-8 bytes before parsing. The exact limit
is inclusive. Public default encode/decode overloads retain 1 MiB; explicit
overloads and the internal unchecked writer use the supplied limits.

The count includes every schema field, owner/operation/reference, typed payload,
both complete current and next instance histories, JSON syntax and escaping.
Repeated history therefore contributes twice, and quotes, backslashes and
multibyte text affect the actual encoded size. Aggregate receipt capacity must
accommodate all of these together. Neither 16 MiB nor the ceiling guarantees
space for arbitrary history or several maximum-sized captures.

Process output capture behavior and its own bounds remain unchanged. Physical
storage text prefixes and database framing are outside the JSON byte count.
This policy is not a heap quota and does not bound aggregate storage or parsing
allocations.

## R4 — Preserve schema and fail without data loss

The receipt schema remains `cncf.workflow-mechanical-transition.v1`, with the
same required keys, owner, type and instance/history checks. Exceeding the
selected policy is a typed failure. No truncation, compression, history
backfill, stored-policy inference or automatic policy relaxation occurs.
Lower settings and older 1 MiB readers reject larger retained rows, even if a
writer previously admitted them with a larger policy. Reopen must select a
policy sufficient for those rows through normal configuration.

## R5 — Use one policy for preparation and retained readback

The borrowed DataStore adapter validates Configuration through the existing
instance binding and resolves `configuration.mechanicalReceiptLimitsC` before
construction. It captures that validated limits value once and uses it for
encoding before conditional-root preparation, postcommit decoding and `loadRetainedC`.
Managed/ComponentStorage wiring supplies that same Configuration. Existing
atomic root/checkpoint guards and ownership remain unchanged. Capacity refusal
before preparation changes neither the native root nor its checkpoint.

## Executable specifications

- `WorkflowMechanicalReceiptParametersSpec`, E1: optional absence, independent
  canonical Component resolution, generated integer settings, inclusive policy
  endpoints and malformed settings.
- `WorkflowMechanicalTransitionSpec`, E2: retained 1 MiB default refusal,
  explicit 16 MiB roundtrip, generated escaped/multibyte full-envelope exact
  boundaries, lower-reader refusal, five-argument construction, optional default
  and endpoint resolution, and null/out-of-range refusal before adapter effects.
- `WorkflowMechanicalTransitionSpec`, E3: actual SQLite refusal without root or
  checkpoint mutation, larger-policy commit and physical reopen, and lower
  reader refusal after reopen.
