# Phase 55 GCF-04 Source Candidate Construction

GCF-04 completed the bounded decoding path from one admitted physical source
to unresolved typed candidates.

- `simplemodeling-lib` now uses development coordinate `0.4.2-SNAPSHOT` for
  this source change; CNCF consumes that exact development coordinate rather
  than reusing published `0.4.1`.
- Generic documents preserve ordered mapping occurrences. Runtime-owned source
  admission assigns rank and ordinal and captures each loader once.
- Candidate construction retains exact type witnesses, strict codec decoding,
  original input path/spelling, source provenance, default evidence, and
  confidentiality classification. Same collision-domain canonical
  parameter/target duplicates fail structurally before resolution.
- CNCF decodes the consolidated target tree and admitted split locations into
  Global, ComponentClass, SubsystemInstance, and qualified ComponentInstance.
  A request-scoped schema supplies fixture witnesses and aliases without
  creating the GCF-07 production catalog. External-document SPI reports are
  backed by actual decoding rather than a scenario-name placeholder.

Validation evidence:

- generic `Test/compile`: serial invocation `57928-20260802T141913Z`, passed;
- generic focused specs: serial invocation `64576-20260802T143255Z`, 17
  succeeded, 0 failed, 3 intentional pending;
- CNCF `Test/compile`: serial invocation `62940-20260802T142930Z`, passed;
- CNCF focused specs: serial invocation `67143-20260802T143723Z`, 9 succeeded,
  0 failed, 3 intentional pending;
- CNCF compile classpath: serial invocation `66496-20260802T143616Z` recorded
  `goldenport-core_3:0.4.2-SNAPSHOT` and no `0.4.1` entry.

GCF-05 remains responsible for target selection, precedence, winner creation,
and immutable override chains. GCF-07 remains responsible for the closed
Textus/CNCF production catalog and Phase 53 admission integration.
