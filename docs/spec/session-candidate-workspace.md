# Session candidate workspace

This contract describes the managed candidate workspace and its typed execution binding.

The host must explicitly designate a managed parent workspace and an eligible
candidate alias for the actual component. Both require the component's Manage
grant. The parent is an existing admitted directory; it is not inferred from a
repository path. The alias's existing typed process declarations and grants are
the ceiling on candidate execution. Missing policy or grants is a setup error.

The runtime provisions a deterministic child identified by the actual component,
Session and candidate. Creation is exclusive. An already occupied child is not
adopted by provision. Reacquisition requires the runtime's retained typed
candidate reference and known effect state; an unavailable, foreign, occupied,
or unknown selection fails closed. Neither provision nor reacquisition removes
or resets a directory. The runtime owns the association and effect history.

The result supplies a candidate-scoped execution context and opaque reference.
The returned candidate and observation contexts are self-bound to their native
Action/UnitOfWork interpreter for ordinary `ProcedureActionCall.executeProgram`
usage. They use the existing synchronous framework Action binding, retaining
lifecycle facilities and sharing execution metadata with the owning context.
The caller uses the returned context directly and must not construct an
application UnitOfWork or interpreter to make it usable.
That context retains the original source workspace binding and substitutes only
the eligible candidate alias. Its typed process definitions use the same alias
binding while preserving the original capabilities, grants, arguments, limits,
and environment. Materialization selects the candidate alias as destination;
the ordinary typed SBT target selects the same alias in the same scoped context.
Ordinary unscoped calls and existing public target constructors retain their
existing behavior. No raw process command or new execution authority is exposed.

## Runtime sequence

The host configures the V7 `sessionCandidateWorkspaces` entry with `componentId`,
`parent`, and `alias`, alongside existing workspace bindings, Manage grants, and
typed Git and SBT definitions and grants for the relevant aliases. The parent
must be separate from the source. A missing parent, policy, grant, or typed
program remains a concrete host setup requirement; no permission is inferred.

The owning runtime interprets `UnitOfWorkOp.SessionCandidatePrepare(source,
parent, alias, session, candidate)` as an external effect and retains the returned
reference record and effect state in its existing canonical Session/candidate
state. The resulting context is used with the existing typed Git materialization
operation and typed SBT operation targeted to `alias`. The source remains bound
to its original workspace.

After reconstructing a reference with `SessionCandidateWorkspaceReference.fromRecordC`,
the runtime calls `reacquireC(context, reference, expectedRecord, state)` using
the independently retained expected logical selection and known effect state.
For a materialized candidate it first uses `observationContextC(context, reference,
expectedRecord)` for the host-granted typed Git Root and Head observations, and
supplies those results with the retained materialization result. Unknown effect
state, a mismatched selection, an occupied supposedly empty directory, or failed
Git evidence is refused. The record is data, not authentication or proof that an
effect occurred: the trusted owning runtime is responsible for the canonical
association and truthful retained effect history. No directory is removed or reset.
