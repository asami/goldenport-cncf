# SWF-06: Composite Workflow Generated Semantic Handoff Contract

## Purpose and ownership

This contract is the closed, CML-first handoff from one producer generation
occurrence to one generated composite-workflow semantic contract.  It carries
the typed semantic facts needed by downstream consumers without requiring them
to parse CML again, infer semantics from names or source text, or substitute a
handwritten canonical contract.

The producer owns generation of this handoff.  A consumer owns only consumption
of the already-admitted handoff.  This document defines neither generated
source nor an implementation ABI.

## One-source, one-generated-contract identity

Each handoff has a declared schema identifier and schema version.  It also has
a stable generator identity, a generator version, and the identity of its
single source generation occurrence.  That occurrence is represented by:

- the CML source identity;
- the model identity and model version; and
- exact CML location provenance for every source-declared element represented
  by the handoff.

One source generation occurrence produces exactly one generated semantic
contract for the declared composite workflow definition version.  A generated
contract identifies exactly that one source occurrence.  A consumer must not
merge contracts, split a source occurrence into implicit contracts, select one
by filename or name convention, or replace an absent generated contract with a
handwritten equivalent.

## Closed typed payload

The handoff payload is typed and contains all of the following semantic data:

- the exact `CompositeStateMachineDefinitionIdentity` and pinned definition
  version;
- the exact `WorkflowDefinitionIdentity` and pinned definition
  version;
- typed constituent state-machine and workflow identities, their pinned
  versions, and the composite configuration that declares their relation;
- each admitted generated rule descriptor, including its identity, admitted
  rule kind, declaration order, source/model/location provenance, and the
  pinned definition identities and versions to which it applies;
- each action descriptor admitted for the composite occurrence, including its
  stable logical action identity, declaration phase and order, owning
  transition or machine role and level, causal and correlation identity, and
  source/model/location provenance; and
- the minimal workflow-profile metadata needed to identify the generated
  composite workflow profile and its declared constituent configuration.

Every identity and version pin in these records is explicit.  The contract does
not carry opaque blobs in place of typed rule, action, constituent, or profile
facts.  It does not permit a consumer to derive a missing pin from a current
model, an ambient registry, a name, a source location, or generator defaults.

## Admission and rejection

A consumer admits a handoff only when its schema/version is supported, its
generator and source occurrence are identified, all required provenance is
present, every definition and constituent is explicitly pinned, and the
one-source/one-generated-contract correspondence is structurally demonstrable.
The rule and action descriptors must be typed, complete, and attributable to
the declared composite definition and configuration.

Malformed, foreign, unpinned, incomplete, non-one-to-one, or opaque contracts
are structurally rejected.  Rejection has no fallback to CML reparsing, name or
source inference, partial admission, handwritten canonical substitution, or
best-effort reconstruction.

## Consumer boundary

Phase 64.2 owns planning and interpreter behavior over an admitted contract;
this handoff does not specify that behavior.  SWF-07 owns fixture consumption
of the admitted contract.  Phase 77 owns any later API/SPI admission boundary.
`ComponentFactory`, `Provider`, and durable continuation or protocol ownership
remain outside this contract.

This handoff does not define generated source, ABI surface, factory behavior,
runtime behavior, persistence, protocol, retry, two-phase commit,
compensation, or recovery.  Those concerns cannot be inferred from this
semantic handoff and require their separately owned contracts.
