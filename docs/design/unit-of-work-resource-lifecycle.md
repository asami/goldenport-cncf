# UnitOfWork Resource Lifecycle

Status: normative design

## Purpose

`UnitOfWork` owns the lifecycle of non-transactional runtime resources acquired
while it executes an operation. It must release those resources when the work
terminates successfully, fails, is aborted, or is explicitly disposed.

This design applies to operational resources such as Process Execution handles,
execution WorkAreas, temporary streams, and future managed runtime leases. It
does not replace the commit protocol for transaction participants.

## Ownership Boundary

```text
ActionCall / Task
  -> UnitOfWork
     -> transaction participants: CommitProtocol
     -> operational resources: UnitOfWork resource registry
```

Transaction participants use prepare/commit/abort. Operational resources use a
separate `UnitOfWorkResource.releaseC` lifecycle callback. A resource must not
be registered in both mechanisms for the same responsibility.

## Resource Contract

An operational resource implements:

```scala
trait UnitOfWorkResource {
  def releaseC(termination: UnitOfWorkTermination): Consequence[Unit]
}
```

The termination reason is one of `Committed`, `Aborted`, or `Disposed`.
`releaseC` must be idempotent or otherwise tolerate a race with its own normal
terminal completion. It must not assume that it is called from the operation
thread.

`registerResourceC` returns a registration. Closing the registration means the
resource has already reached its own terminal state and prevents ordinary later
UnitOfWork cleanup. Closing a registration does not release the resource.

## Terminal Semantics

Normative rules:

- R1: The first terminal action drains registered resources exactly once in
  reverse registration order.
- R2: Every release is attempted after an earlier release failure. When a
  primary operation and cleanup both fail, the primary `Conclusion` remains
  authoritative and the cleanup `Conclusion` is retained in its causal chain.
- R3: A registration may be closed only after its resource reaches a confirmed
  terminal state.
- R4: Registration after UnitOfWork termination immediately releases the
  resource using the recorded termination reason.

The first UnitOfWork terminal action drains its registered resources in reverse
registration order. Every resource is attempted even if an earlier release
fails. A subsequent terminal action is a no-op for the registry.

Late registration after terminal state performs immediate release with the
recorded termination reason. This prevents a launch/termination race from
leaving an unowned resource.

`commit`, `abort`, and `rollback` drain the registry. `dispose` is an idempotent
safety net for a caller that ends a UnitOfWork outside the ordinary transaction
path; it releases resources but does not choose a transaction outcome.

If a transaction operation has already failed, that original structured
`Conclusion` remains authoritative and cleanup failures are retained as its
preceding causes. If transaction completion succeeds but a
resource release fails, the release failure is returned as a normal
`Consequence.Failure(Conclusion)`. Implementations must not use application
`Status.detailCodes` for lifecycle failures.

## Process Execution

After `ProcessExecutionDriver.startC` returns a handle, the interpreter
registers one UnitOfWork resource covering both the process handle and its
WorkArea. On forced UnitOfWork cleanup the resource:

1. calls idempotent `cancelC`;
2. calls `awaitC` to reap the process, descendants, and stream readers; and
3. closes the WorkArea.

Normal process completion unregisters that resource before the UnitOfWork
commits. Therefore a completed process is not cancelled by later cleanup.

Job cancellation remains a separate mechanism: it promptly signals `cancelC`
through `JobCancellationScope`. UnitOfWork lifecycle cleanup is responsible for
reaping and releasing resources if the owning UnitOfWork terminates first.
Drivers must support repeated or concurrent cancellation/await calls and expose
a stable terminal result.

## Observability

Resource cleanup must use normal structured `Conclusion` diagnostics. It may
record safe resource category, termination reason, and structural outcome. It
must not expose confidential process input/output, credentials, environment
values, or raw host paths.

## Non-Goals

- making external side effects transactional;
- replacing Job cancellation or Task lifecycle;
- retaining completed process output or WorkAreas;
- defining a generic retry policy for failed cleanup;
- reinterpreting legacy shell execution.
