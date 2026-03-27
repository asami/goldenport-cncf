# Request To Record Core Boundary Mini-Low Instruction

Status: Active Instruction

## Goal

Refactor `Request.toRecord(...)` so core `Request` does not know any product or runtime prefix such as:

- `textus.`
- `cncf.`

The exclusion rule for framework/query parameters must be provided from outside
the core type.

## Problem

Current behavior drifted in the wrong direction:

- `org.goldenport.protocol.Request` is core
- but it learned product/runtime prefixes
- that makes the protocol layer depend on CNCF/Textus naming

This must be reversed.

## Read First

- [/Users/asami/src/dev2025/simplemodeling-lib/src/main/scala/org/goldenport/protocol/Request.scala](/Users/asami/src/dev2025/simplemodeling-lib/src/main/scala/org/goldenport/protocol/Request.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/directive/Query.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/directive/Query.scala)
- [/Users/asami/src/dev2026/cncf-samples/samples/02.a-crud-seed-import-lab/README.md](/Users/asami/src/dev2026/cncf-samples/samples/02.a-crud-seed-import-lab/README.md)

## Required Outcome

### 1. Core stays neutral

`Request` must not hard-code:

- `textus.`
- `cncf.`
- `query.`
- any product/runtime namespace

### 2. `toRecord(...)` accepts an external rule

Add a small API so callers can control which properties are excluded from the
record.

Acceptable forms:

```scala
request.toRecord(excludeProperty = name => ...)
```

or another small equivalent API.

The important rule is:

- the caller decides
- core `Request` does not know the product/runtime vocabulary

### 3. CNCF/Textus passes the rule explicitly

The CNCF side must explicitly exclude at least:

- `textus.*`
- `cncf.*`
- `query.*`

when building domain/query records from requests.

## Work Steps

1. Refactor `Request.scala` so framework/query prefix knowledge is removed from core.
2. Add an external exclusion hook to `toRecord(...)`.
3. Update CNCF call sites so the correct exclusion rule is passed in.
4. Keep current runtime behavior working for:
   - `--textus.format yaml`
   - `--cncf.format yaml`
5. Verify that framework/query properties no longer appear in domain `Query(...)`.

## Do Not

- Do not leave product-specific prefix logic inside `Request`.
- Do not add a large compatibility wrapper.
- Do not change sample semantics.
- Do not remove `cncf.*` compatibility from CNCF/Textus.

## Minimum Verification

Verify with at least these commands:

```bash
sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --textus.format yaml Crud.entity.searchItemRecord --name alpha"
```

and

```bash
sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --cncf.format yaml Crud.entity.searchItemRecord --name alpha"
```

The result should show:

- the command succeeds
- the domain query is effectively `Query(name=alpha)`
- framework format parameters do not leak into the domain query

## Report Back Only

- what files you changed
- how `Request.toRecord(...)` changed
- where the exclusion logic now lives
- what commands you used to verify the behavior
- what remains unfinished, if anything
