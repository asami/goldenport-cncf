# KS-14 Docker SIE Live Validation

KS-14 validates the Phase 25 knowledge-structure work against a concrete
`textus-sie` driver runtime. It is not a CNCF provider implementation slice:
Fuseki, Chroma, and embedding behavior remain owned by `textus-sie`.

## Runtime Shape

- CNCF and `textus-sie` run on the local JVM.
- Fuseki runs in Docker and exposes RDF DB access at
  `http://127.0.0.1:9030/ds`.
- Chroma is used through the legacy-style `sie-embedding` Docker adapter at
  `http://127.0.0.1:8081`.
- CNCF core does not depend on Fuseki, Chroma, or Python embedding libraries.

## Data Import

RDF and vector data loading are explicit validation steps.

- RDF seed data is loaded into Fuseki through `/ds/data` using Graph Store
  Protocol POST requests.
- The Fuseki import is idempotent: if the dataset already contains triples, the
  init container skips reload.
- Vector seed data is loaded through the SIE-compatible Chroma adapter source
  and document endpoints.
- Chroma database files are not edited directly.

## Validation Path

The live smoke must confirm:

- `SemanticRetrieval.status` reports `graph=fuseki` and `vector=chroma`.
- `SemanticRetrieval.query` returns RDF-oriented results and a CNCF
  KnowledgeFrame projection.
- `registerKnowledgeSpace=true` stores the projected snapshot in component
  `KnowledgeSpace`.
- `SemanticRetrieval.explain` returns Fuseki-derived explanation chunks.
- CNCF MCP discovery and `/mcp tools/call` can invoke `sie.query`,
  `sie.explain`, and `sie.status`.

## Recorded KS-14 Smoke

The KS-14 live validation used the CNCF developer launcher rather than the old
project-local shell scripts for the main runtime path.

- `cncf dev check --project .` confirmed the `textus-sie` dev project shape.
- `cncf dev classpath --project .` generated the project runtime classpath.
- `cncf dev server --project . --runtime-dev-dir
  /Users/asami/src/dev2025/cloud-native-component-framework --port 19532
  --no-default-components` started CNCF with the local CNCF runtime APIs and the
  `textus-sie` component development directory.
- `SemanticRetrieval.status` reported `overall=healthy`, `graph=fuseki`,
  `vector=chroma`, and `embedding=lexical`.
- `SemanticRetrieval.query` returned both RDF-oriented results and a CNCF
  `KnowledgeFrame`. With `registerKnowledgeSpace=true`, the component
  `KnowledgeSpace` became `ready` and reported non-zero node, relationship,
  evidence, provenance, frame, and fact counts.
- `/web/system/admin/knowledge` showed the `SemanticIntegrationEngine`
  component as `ready` after registration.
- `SemanticRetrieval.explain` returned Fuseki-derived explanation chunks and an
  explanation `KnowledgeFrame`.
- `Mcp.callTool` was exercised through normal CNCF operation dispatch for
  `sie.status`, `sie.query`, and `sie.explain`.

The live smoke validates the SIE MCP facade and normal CNCF operation dispatch
path. CNCF core remains provider-neutral and does not acquire Fuseki or Chroma
runtime dependencies.

## Future Work

Production hardening is intentionally outside Phase 25:

- secured Fuseki/Chroma deployment;
- persistent production data lifecycle;
- import source governance;
- larger corpus indexing;
- authorization around operational import/rebuild commands;
- provider-level observability and retry policy.
