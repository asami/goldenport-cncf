# Direct upload and selected-provider integration

This source integrates accepted App direct-upload source
`58bbbb6a795746cf3585c26fcac84886d65b7754` with accepted provider-attribution
source `377f882e2c107547534a364a0e2ab706fcfb3e27` in a dedicated checkout.
The adopted storage-only merge-tree proposal supplies the optional direct and
reserved upload contracts; current bounded child reads and their reference,
response and credential guards remain. Authenticated-provider source is untouched.

GoogleDriveFileStore exposes ReservedDirectUploadFileStore together with
FileStoreChildren. Preparation retains server-generated new-file IDs, and
completion independently inspects the reserved file. Optional provider SHA-256
and storage-root metadata remain observations, not authorization or Material
registration. The original four FileStore operations remain the base SPI;
optional metadata constructor defaults preserve existing source calls, without
claiming binary or composed-consumer compatibility before qualification.

The App's accepted completion and earlier canonical planning history are retained
in the producer plan. This integration is source-ready only; compile, eight
focused suites, unchanged full validation, genuine independent integrated review,
acceptance commit, normal local publication and rebuilt TKL/Studio consumer
qualification remain separate parent-owned gates. No real Drive transfer, ACL
change, App/device work, external publication or consumer Session operation was
performed. Existing accepted histories/counters and old artifacts remain intact.
The new development source selects `0.5.6-SNAPSHOT`.
