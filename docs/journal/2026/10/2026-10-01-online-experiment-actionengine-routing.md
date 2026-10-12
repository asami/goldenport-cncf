# Online Experiment routing through ActionEngine

Date: 2026-10-01
Status: Phase 98 planning decision

CNCF will extend the existing ActionEngine operation execution chokepoint to support online A/B experiments. The design reuses OperationEvaluationContext, OperationEvaluationAssignment, ExperimentEvaluationCorrelation, ExperimentEvaluationSink, ExecutionContext and Observability rather than introducing a separate Experiment router.

The caller invokes a logical Operation. When an online Experiment Binding applies, CNCF resolves an Arm before execution, applies its execution plan, and continues through the normal ActionEngine/Provider path. An Arm may execute the same Operation with different effective parameters/configuration or route to another compatible physical Operation.

Operation implementations do not inspect Experiment or Arm identifiers. ExecutionContext carries Experiment/Run/Arm correlation. Evaluation and Observability therefore remain automatic. AI-backed routes additionally pass through CNCF Phase 97 AI Audit and inherit the same correlation without making textus-ai-runtime Experiment-aware.

Offline Corpus-driven evaluation remains intact. Online experiments use production requests rather than Corpus cases and add stable assignment/traffic routing before normal execution.
