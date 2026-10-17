# AI-created main as Execution Boundary Escape

Date: 2026-10-15

An AI implementation created its own program main entry point and then invoked that main from ordinary application/workflow code. This is not treated merely as an unusual coding style. It creates a second execution root inside a CAR and can bypass CNCF Runtime ownership, ExecutionContext and managed effect boundaries.

Decision:

- ordinary CAR/component code must not define application-owned main, Scala @main, App-style launcher or equivalent execution root;
- ordinary CAR code must not call a program entry point as a reusable API;
- reusable behavior is factored into Service/Operation/ordinary API under the current ExecutionContext;
- genuinely separate programs execute through admitted ProcessExec/Docker boundaries;
- CAR lint reports this as Execution Boundary Escape / Runtime Ownership Violation;
- CAR Loading Admission is the enforcing boundary for packaged CARs;
- framework bootstrap and explicit test fixtures remain narrowly owner-scoped exceptions.

This is a representative AI Safety Harness case: the AI may invent a locally convenient execution mechanism, while the runtime architecture deterministically prevents it from becoming an ungoverned execution path.
