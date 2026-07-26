# Job Management Use Case Overview

`overview.svg` is the primary comparison of the canonical managed Command
execution modes.

Theme-specific detail:

- `managed-synchronous-command.svg`: `JobSync`.
- `managed-asynchronous-command.svg`: `JobAsync`.
- `synchronous-primary-async-continuation.svg`: `JobSyncWithAsyncCont`.

Plain `Sync` is shown as a contrast: it returns synchronously without creating a
Job. The three detailed paths are available; executable JCL orchestration is not
implied by them.
