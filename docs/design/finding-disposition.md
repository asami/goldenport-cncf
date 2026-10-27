# Finding Disposition design

Phase 99 separates one portable decision model from its language representation
and from authority. `FindingDispositionV1` contains immutable value objects and
a pure deterministic interpreter. `FindingDispositionRepresentation` adapts
portable properties and runtime-retained annotations to that model. JVM
annotation interfaces provide runtime visibility for handwritten Scala; no
compiler plugin, source rewrite, AI or workflow service is required.

The two inspection snapshots preserve absent old findings so disappearance
cannot accidentally become proof of correction. The explicit coverage and
revision-bound change evidence seam lets each lint/model adapter supply actual
observations. Changed source/model/configuration evidence belongs to that
adapter; permission and proposal admission belong to the external process.
Neither is inferred from annotation placement or a lower warning count.

Rule identity is open data with explicit deprecated aliases. Finding occurrence
identity and target binding remain separate from warning text and physical line
positions. This permits new rules and CML-owned properties without changing
the public annotation ABI or creating a second generated-source authority.

The interpreter reports accepted and deferred findings while separately exposing
unresolved actionable backlog. Deferred follow-up remains visible. Invalid or
ambiguous inputs fail at the boundary instead of becoming implicit suppression.

See [the normative contract](../spec/finding-disposition.md) and the executable
specification `org.goldenport.cncf.finding.FindingDispositionSpec`.
