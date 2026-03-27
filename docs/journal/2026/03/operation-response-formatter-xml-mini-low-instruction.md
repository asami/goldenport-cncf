# Operation Response Formatter XML Mini-Low Instruction

Status: Active Instruction

## Goal

Add XML output support to `OperationResponseFormatter`.

This task is limited to response formatting. Do not redesign protocol
semantics or help rendering.

## Read First

- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/protocol/OperationResponseFormatter.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/protocol/OperationResponseFormatter.scala)
- [/Users/asami/src/dev2025/simplemodeling-lib/src/main/scala/org/goldenport/protocol/Response.scala](/Users/asami/src/dev2025/simplemodeling-lib/src/main/scala/org/goldenport/protocol/Response.scala)
- [/Users/asami/src/dev2025/simplemodeling-lib/src/main/scala/org/goldenport/protocol/operation/OperationResponse.scala](/Users/asami/src/dev2025/simplemodeling-lib/src/main/scala/org/goldenport/protocol/operation/OperationResponse.scala)
- [/Users/asami/src/dev2026/cncf-samples/samples/02.a-crud-seed-import-lab/README.md](/Users/asami/src/dev2026/cncf-samples/samples/02.a-crud-seed-import-lab/README.md)

## Required Outcome

### 1. XML becomes a supported output format

At minimum, these request properties should be accepted:

- `textus.format=xml`
- `textus.output.format=xml`
- `cncf.format=xml`
- `cncf.output.format=xml`

### 2. Record responses can be rendered as XML

`OperationResponse.RecordResponse(record)` should be able to produce an XML
protocol response.

### 3. Envelope mode should also work with XML

If `textus.output.shape=envelope` is used together with XML format, the envelope
should still be rendered correctly.

## Work Steps

1. Check whether an existing XML encoder already exists in the supporting libraries.
2. If it exists, use it instead of inventing a new XML serializer.
3. Extend `OperationResponseFormatter` so XML is part of the supported formats.
4. Return an XML protocol response in a transport-consistent way.
5. Verify both plain data and envelope shapes.

## Do Not

- Do not redesign `Response` unless it is truly required.
- Do not add unrelated output metadata.
- Do not change JSON/YAML/TEXT behavior.
- Do not implement a large custom XML stack if a library encoder already exists.

## Minimum Verification

Verify at least these commands:

```bash
sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --textus.format xml Crud.entity.search-item-record --name alpha"
```

and

```bash
sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --textus.output.shape envelope --textus.output.format xml test-sync.item.create-item --name beta --title Beta"
```

Confirm:

- the command succeeds
- XML is returned
- envelope mode still works

## Report Back Only

- what files you changed
- whether you reused an existing XML encoder
- what commands you used to verify XML output
- what remains unfinished, if anything
