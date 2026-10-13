# Phase 88 S2: configured Workflow instance bootstrap

Date: 2026-09-29
Status: bootstrap slice validated; Phase 88 remains in progress
Consumer: sm-workflow Phase 2

## Implemented

Added WorkflowInstancePersistenceSource and WorkflowInstancePersistenceBinding
and wired them through Component.Factory, Component and normal
ComponentFactoryEntityPlanPart/ComponentFactoryWorkflowPart bootstrap.
The unchanged WorkflowInstancePersistence SPI remains create/load/append. The
S1 provider/codec and existing Continuation bootstrap contracts are unchanged.

A Component source takes precedence over the Factory, including an explicit
None. No source leaves legacy components opt-out. An admitted declaration
contains exact configuration/policy identity, separately admitted definition
catalog and collection. Canonical component/store identity and durable provider
capabilities must resolve before Component receives a proven binding. Missing
storage or malformed declarations fail closed, without an implicit memory
provider or reinterpretation of persisted metadata as an executable definition.

Managed SqlDataStore views capture a live lease, so the bootstrap view cannot
be retained for later requests. The immutable binding instead retains its
admitted declaration, environment and owning Subsystem. Every SPI call receives
the current ExecutionContext and resolves a fresh managed view and S1 adapter
inside the existing canonical Subsystem lease. Caller datastore scope stays
unchanged; SystemNode owns the shared physical resource and its finalization.
No new public Operation/CLI, resource ownership, command authority, private
engine, scheduler, SQL backend or management registry was introduced.

## Executable specifications

ComponentFactoryWorkflowInstancePersistenceBootstrapSpec has eight examples:

- legacy opt-out, with requiring persistence failing closed;
- Factory binding used from fresh contexts after bootstrap's lease ends;
- Component source precedence without evaluating a competing Factory;
- explicit Component opt-out suppressing Factory fallback;
- rejection of absent or memory-only storage;
- malformed policy/catalog/collection/store rejection before resource creation;
- one successful expected-revision writer across concurrent calls to one binding;
- owning-Subsystem shutdown/reopen with a new admitted catalog, exact history
  recovery, stale rejection and closed old-binding failure.

The final regression also ran all seven S1 provider examples and the existing
Continuation bootstrap examples. This is same-process owner reconstruction,
not a separate-process launcher restart or an atomic joint persistence test.

## Validation

Registered cncf_command_runner, gpt-5.6-luna / medium, used typed receipt-first
intake and the shared serial wrapper. Every invocation reached terminal
completion with lock=released; no source edits occurred while SBT was live.

- 236: 15/16 focused examples succeeded. A negative fixture constructed an
  empty CollectionId and hit its type assertion before bootstrap. Repaired the
  fixture to pass a null collection field into declaration admission.
- 237: all 16 focused examples succeeded (7 bootstrap, 2 Continuation bootstrap,
  7 S1 provider). Receipt SHA-256:
  `687fff418cddc8a86b237ab453c4b205e5111346e5373a8dbbe41f608063cfc3`.
- 238: after adding shared-binding concurrent CAS, component/Workflow/datastore
  regression completed 109 suites: 876 succeeded, 0 failed, 7 canceled, 3 pending.
  The canceled examples are existing opt-in live MySQL checks. Pending examples
  are two existing ClientComponentSpec HTTP cases and the existing
  SqliteDataStoreSpec column case. They are not executed acceptance.

Final logical command:
`sbt --batch "testOnly org.goldenport.cncf.component.* org.goldenport.cncf.workflow.* org.goldenport.cncf.datastore.*"`.

Final runner: `/root/sbt_receipt_c06868b76909f4f5847b`.
Receipt SHA-256:
`036b33d70962b6355a522e5d6d2265e0cc58a24368d3fa765ed404a55613263e`.
Validated complete-tree SHA-256:
`dba72d076bc2dd8436a69004821f7fffa8c2e72dc4a5fdcf1e2e034db677cd6c`.

Receipt:
`/tmp/skill.cncf.d/cncf-command-execution-2b83880b619b73d4d9a9b74d5ecebd02d556375857ab3b97cfd2f3dc2854a56f-e2a52657491e8d1228bd1ebf1025867d/command-execution-sha256-036b33d70962b6355a522e5d6d2265e0cc58a24368d3fa765ed404a55613263e`.

Log:
`/tmp/skill.cncf.d/cncf-sbt-002d3d1505d8c0b0c40e968964df6ab9c3f4cada5ea269904178e1ad9ec10ea9-4e28eef63a9cc3785a75815ec77d8e6f/14927-20260929T024652Z.log`.

Validated S2 source SHA-256:

- Component.scala:
  `a491ccafe33df15333af93e02bd44245690da099d46a3524420a0fdbc28d3e54`
- ComponentFactoryEntityPlanPart.scala:
  `ffcdbcc77dceda5b4c503cefaf49fffcf4c796fdaa332bdd8a7d03041da2a571`
- ComponentFactoryWorkflowPart.scala:
  `52d8f72aed37193e767b0beffaf9e09589a9e7d9db789bf732586ad7dedb4219`
- WorkflowInstancePersistenceBinding.scala:
  `dffa244a83119d698a3be9e51dd1b526a413234cf1db119dd1d6f8a1222e3044`
- ComponentFactoryWorkflowInstancePersistenceBootstrapSpec.scala:
  `8c6ca578f98aa97f8ce843af5b2d0b337dc9954f74521dfc58bdd97a3a19de7a`

S1 source identities remain unchanged. The parent verified the receipt, terminal
marker and source digests. Subsequent Phase/journal edits are documentation-only
and not part of the captured validation tree. This is focused/regression evidence,
not whole-CNCF full-test, release or commit acceptance.

## Remaining boundaries

Next is shared-store atomic instance/Continuation/issued-work/claim/evidence
recovery, using admitted common contracts. WorkflowInstanceAtomicTransitionV1
and AtomicWorkflowSuspensionEngine describe admission/transaction responsibilities,
not an implemented atomic provider. Independent persistence calls cannot be
claimed as one transaction. Migration/retention/delivery policy identities are
validated, not newly executed policy mechanisms.

Production application service/server/CLI/MCP, resident references and Phase 87
canonical visibility remain open. No downstream artifact refresh/consumption,
publishLocal, skill deployment, commit, push or remote publication occurred.
ArtScene and other-owned changes remain untouched. No sm-workflow semantics or
cncf-* skill changes were added to CNCF.

See [Phase 88](../../../phase/phase-88.md).
