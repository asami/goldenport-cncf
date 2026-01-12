status = draft
scope = Phase 2.6 / Stage 5
nature = executable runbook

# scala-cli Custom Component Runbook (Stage 5)

## Purpose

This runbook provides a reproducible, copy-pasteable procedure that proves a
user can develop and run a custom Component via scala-cli. This is a runbook
only, not a normative specification or design contract.

## Scope and Positioning

- Phase/Stage mapping: Phase 2.6 / Stage 5 / Step 3 (Exact commands included)
- This runbook documents executable steps and expected outputs only.
- Bootstrap logging persistence / ops integration is out of scope.

## Minimal Level-1 DemoComponent (class, no-arg constructor)

Note: core is injected via initialize(ComponentInitParams); do not bypass
initialization or attempt to infer core in user code.

Create `DemoComponent.scala` in the current directory:

```scala
package demo

import org.goldenport.cncf.dsl.DslComponent
import org.goldenport.cncf.dsl.result_success

class DemoComponent() extends DslComponent("hello") {
  operation("world", "greeting") { _ =>
    result_success("ok")
  }
}
```

## Commands and Expected Outputs

### (Optional) Compile

```sh
scala-cli compile .
```

Expected output (example):
- Successful compilation message from scala-cli

### Admin component list (with bootstrap log)

```sh
CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command admin component list --component-repository=scala-cli
```

Expected output:
- admin component list includes `hello` or `demo.DemoComponent`

### Demo command execution (with bootstrap log)

```sh
CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command hello world greeting --component-repository=scala-cli
```

Expected output:
- `ok`

## Notes

- This runbook does not establish or change platform contracts.
- Bootstrap log persistence / ops integration is out of scope.
- It is intended to satisfy Stage 5 / Step 3 reproducibility evidence.
