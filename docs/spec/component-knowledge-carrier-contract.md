# Component Knowledge Carrier Contract

## Purpose

`cncf.component-knowledge-carrier.v1` is the producer-side declaration for a
Component knowledge consumer contract. It is a value-only descriptor field,
not a consumer, resolver, content route, or publication mechanism.

## Canonical Declaration

The declaration contains exactly these fields:

```json
{
  "carrierSchema": "cncf.component-knowledge-carrier.v1",
  "consumerContractSchema": "cncf.component-knowledge-consumer.v1",
  "logicalPath": "component-knowledge.json",
  "sha256": "<lowercase-64-hex-sha256>"
}
```

Duplicate and unknown JSON fields are rejected. Both schema values are exact
and case-sensitive. `logicalPath` is exactly `component-knowledge.json`;
absolute paths, traversal, normalization aliases, and URL encodings are not
declarations. `sha256` is a lowercase 64-hex SHA-256 over the raw file bytes.

## Producer Locations

The authored source is `src/main/car/component-knowledge.json`. A packaged CAR
contains those raw bytes at the archive logical path `component-knowledge.json`
and places only the declaration in `component-descriptor.json`. Development
preparation copies the raw bytes to `target/cncf.d/component-knowledge.json`
and records that file as development evidence.

The raw resource bytes never become CBD output through this contract. Missing
source preserves the existing descriptor and three-item development-evidence
behavior for legacy CARs.
