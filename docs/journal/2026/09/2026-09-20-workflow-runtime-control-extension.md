# Workflow Runtime Control Extension

Date: 2026-09-20
Status: design direction

Workflow の実用制御として Retry、Timeout、Deadline、Timer / Wait、Cancellation、FailurePolicy、Idempotency、Iteration を CNCF runtime の後続拡張に位置づける。

sm-workflow の早期実運用を最優先とし、最初に Retry と Timeout だけを提供する。Retry は fixed maximum attempts / fixed delay、Timeout は Action / Participant invocation の execution timeout とする。

その後、scheduling/lifecycle、failure/execution safety、Goal-oriented iteration の順に独立 Phase として扱えるよう分離する。Retry と Iteration は異なる。Retry は同一 logical action の技術的再実行、Iteration は Goal 達成のための意味的反復である。

Cozy が宣言と generated ABI を所有し、CNCF が durable execution semantics を所有する。sm-workflow は必要な contract を利用するだけであり、これらの roadmap の owner ではない。
