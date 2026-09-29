# DisplayService and Standard Display Mutation

Date: 2026-09-29
Status: architectural direction

Display Model is extended from a read-only presentation projection into a UI-facing protocol with a component-scoped DisplayService.

For the initial design, Aggregate and View access are runtime-internal. Each application component may expose its Display boundary through DisplayService. DisplayService owns Display Object read/query and standard resource mutation: create, update and delete.

Standard Display Mutation is deliberately paired with Display Object metadata. Editable fields, datatypes, constraints, identity, revision and mutation capability allow a generic client to construct standard Create/Edit/Delete interactions without application-specific operation code.

Business Operations such as purchase, approve, requestReview and publish remain ordinary CNCF Operations. A UI invokes their existing REST Operation interface directly; DisplayService does not tunnel or wrap them.

Initial synchronization after a Business Operation is client-initiated reload: after the REST Operation succeeds, the smartphone/client reads the affected Display again through DisplayService. Event invalidation, push/subscription and Service Bus integration are future extensions.

This creates two explicit update paths:

1. standard resource mutation: UI -> DisplayService -> Display Mutation -> Entity/Aggregate -> View -> Display Projection;
2. business behavior: UI -> REST Business Operation -> Entity/Aggregate; on success UI -> DisplayService reload.

The design intentionally enables TFAF to standardize List/Detail/Create/Edit/Delete/Confirmation interaction from configuration while preserving CNCF Business Operations as independent domain/application APIs.
