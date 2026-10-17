# Phase 114 - Hybrid Domain Rule and Evidence Demonstrator Checklist

Status: PLANNED

## HDR-01 Evidence prototype

- [ ] Define phase-local Evidence = Fact | Measure | Estimate | Judgment.
- [ ] Preserve Defined/State/Observed/Declared/Derived Fact distinctions needed by the fixture.
- [ ] Preserve EmbeddingSimilarity as Measure, not Estimate.
- [ ] Define minimal provenance, validity/version, producer, and input-Evidence links.
- [ ] Map existing Phase 32 Fact/WorkingMemory to the prototype.
- [ ] Reconcile naming/semantics with Candidate-Admission Evidence; do not create two generic Evidence authorities.

## HDR-02 Entity Space StateFact path

- [ ] Define the Rule-facing Fact projection boundary.
- [ ] Read fixture Entity/Aggregate state through Entity Space/EntityRealm.
- [ ] Exercise existing DataStore/PostgreSQL path or a faithful deterministic fixture.
- [ ] Produce at least two StateFacts.
- [ ] Verify Rule-facing code contains no SQL/table/row dependency.

## HDR-03 Symbolic inference

- [ ] Produce at least one DerivedFact using Phase 32 deterministic inference.
- [ ] Evaluate whether an external expert-system adapter is needed for the demo.
- [ ] If used, isolate Drools or another engine behind an adapter/provider boundary.
- [ ] Preserve rule/input provenance on DerivedFact.
- [ ] Verify symbolic provider cannot mutate Entity/DataStore state directly.

## HDR-04 SIE semantic evidence

- [ ] Locate and reuse SIE RDF DB / Vector DB and SemanticRetrieval boundaries.
- [ ] Map one RDF/Knowledge inference result to DerivedFact.
- [ ] Map one vector similarity result to EmbeddingSimilarity Measure.
- [ ] Preserve provider/model/index/source provenance needed by explanation.
- [ ] Keep Fuseki/Chroma/SPARQL/vector payloads outside Domain Rule types.
- [ ] Provide deterministic SIE test doubles/recorded responses for the core suite.

## HDR-05 Analytics evidence

- [ ] Define one set-oriented PostgreSQL analytical query/projection.
- [ ] Produce one Measure or Estimate.
- [ ] Record query/model/version/time provenance appropriate to its semantics.
- [ ] Verify analytics does not masquerade as Entity StateFact.

## HDR-06 Generative judgment

- [ ] Define one semantic judgment required by the scenario.
- [ ] Reuse JudgmentAction/Continuation boundary where practical.
- [ ] Invoke Codex only behind a provider-neutral adapter.
- [ ] Normalize result to AIJudgment with rationale and evidence references.
- [ ] Do not map LLM self-confidence to calibrated Estimate.
- [ ] Supply deterministic test provider/recorded result.

## HDR-07 Domain Rule and decision

- [ ] Define one Domain Rule consuming multiple Evidence kinds.
- [ ] Return a typed Domain Decision.
- [ ] Produce explanation/provenance showing contributing Evidence.
- [ ] Show at least one DerivedFact chain.
- [ ] Show the EmbeddingSimilarity Measure in the explanation.
- [ ] Show the AIJudgment as Judgment, not Fact.
- [ ] Keep evaluation pure with respect to persistent Domain state.

## HDR-08 End-to-end demonstrator

- [ ] Deterministic vertical-slice executable specification passes.
- [ ] Live PostgreSQL integration demonstrated.
- [ ] Live SIE RDF integration demonstrated.
- [ ] Live SIE vector/similarity integration demonstrated.
- [ ] Live Codex judgment integration demonstrated.
- [ ] External symbolic engine demonstrated if HDR-03 determined it useful.
- [ ] Existing Phase 32 focused regression passes.
- [ ] Architecture review confirms no provider-specific leakage.
- [ ] Record ABI/productization recommendation for a successor phase.

## Closure Gate

- [ ] All required HDR work packages are complete or explicitly relocated.
- [ ] Required deterministic acceptance scenario is green.
- [ ] Live integrations required above have evidence, with environmental failures distinguished from semantic failures.
- [ ] Notes and journal reflect implementation findings rather than only the original proposal.
- [ ] Phase review completed.
- [ ] Phase 114 marked CLOSED only after the above gates are satisfied.
