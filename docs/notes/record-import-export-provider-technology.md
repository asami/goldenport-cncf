# Record Import / Export Provider Technology

## Summary

This note records the CNCF-side technology boundary for Record import and
export. Format decoding and rendering belong to `simplemodeling-lib`; CNCF owns
the runtime provider boundary, observability, configuration, authorization, and
component-facing execution flow.

The exchange unit is `Record` / `Vector[Record]`. A `Record` may represent a
nested tree, not only a flat row.

## Import Provider Boundary

CNCF exposes import through `RecordImportProvider`.

The provider contract should be:

- input: source reference, source bytes/text, declared or detected format,
  optional schema/profile, and component/domain context;
- output: `Vector[Record]`, import metadata, and import issues;
- execution: `ProviderCall` through `ProviderEngine`;
- observability: provider span, source summary, format, record count, issue
  count, elapsed time, and failure classification;
- payload policy: do not put raw uploaded files, provider JSON, spreadsheet
  payloads, or raw HTML bodies into CallTree spans.

Provider execution should use CNCF runtime configuration through resolved
parameters. Provider code should not read `sys.props` or global configuration
directly.

## Export Provider Boundary

CNCF exposes export through `RecordExportProvider`.

The provider contract should be:

- input: `Record` or `Vector[Record]`, requested format, optional schema/profile,
  and output options;
- output: bytes or text plus content type and filename suggestion;
- execution: `ProviderCall` through `ProviderEngine`;
- observability: format, record count, byte count, elapsed time, and failure
  classification.

Export is not limited to browser downloads. The same provider boundary can later
support service destinations such as Google Spreadsheet.

## TKE Usage

`textus-knowledge-editor` is the current driver application.

For import:

- book, paper, and web resource import flows should call the import provider;
- file import returns `Vector[Record]`;
- TKE applies domain schema/profile shaping after generic decoding;
- shaped records become editable `Information` in `InformationSpace`.

For export:

- list pages may display summary fields only;
- download/export must use a full Information projection, not the summary rows
  shown in the table;
- export output should support CSV, TSV, LTSV, JSON, YAML, XML, HOCON, and
  Excel when the corresponding renderer exists.
- book import currently uses the Record import provider for text and Excel
  inputs.
- Static Form table downloads use the Record export technology for `.xlsx`
  output.

## Google Spreadsheet

Google Spreadsheet is an external service import/export source. It must not be
implemented in CNCF core or `simplemodeling-lib`.

The intended boundary is:

- CNCF defines the provider SPI and runtime execution;
- `textus-google` implements Google Spreadsheet import/export providers;
- TKE enables Google Spreadsheet choices only when the provider is available;
- provider output remains `Vector[Record]` for import and accepts
  `Vector[Record]` for export.

This keeps Google API dependencies out of CNCF core and preserves the same
observability and configuration model as file-based provider calls.

## Implemented v1

- `RecordImportProvider` and `RecordExportProvider` execute through
  `ProviderEngine`.
- Provider spans record format, record count, issue count, and content type
  summary only.
- Static Form download accepts `xlsx` / `excel` and returns an Excel attachment
  without storing workbook bytes in observability payloads.

## References

- `docs/design/record-purpose-taxonomy.md`
- `docs/journal/2026/05/phase-27-ke-09-publish-materialize-feedback.md`
- `docs/journal/2026/05/phase-27-book-resolver-import-external-candidate-flow.md`
