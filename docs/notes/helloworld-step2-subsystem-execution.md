# HelloWorld Step 2: Subsystem Execution Model

## 1. Purpose
- Establish the Subsystem execution model before domain-level expansion.
- Fix the HTTP execution path as a stable boundary for later CML → CRUD work.

## 2. Final Architecture Overview
- HTTP Server → Subsystem → Component → Service → Operation
- `Subsystem.executeHttp` is the only HTTP entrypoint.
- The HTTP server performs no routing or execution.

## 3. Subsystem Responsibilities
- Resolve HTTP path to component / service / operation using `/component/service/operation`.
- Convert `HttpRequest` to `Request` via Ingress.
- Convert `Response` to `HttpResponse` via Egress.
- Delegate execution to Component/Service; Subsystem does not execute actions.

## 4. Ingress / Egress Design
- Ingress/Egress are owned by `protocol.handler`.
- Lookup is type-based (input/output class), not kind-based.
- `findByInput` / `findByOutput` represent the canonical wiring points.
- HTTP is treated as a transport type, not a special case.

## 5. Consequence-based Execution Pipeline
- Subsystem uses Consequence end-to-end.
- Option values are lifted at the boundary using `Consequence.fromOption`.
- Missing Ingress or Egress is treated as Failure.

Example (simplified):
```scala
val r: Consequence[Response] = for {
  ingress <- Consequence.fromOption(
    component.protocol.handler.ingresses
      .findByInput(classOf[HttpRequest]),
    "HTTP ingress not configured"
  )
  request <- ingress.encode(operation, req)
  response <- component.service.invokeRequest(request)
} yield response
```

## 6. HelloWorld Admin Component
- AdminComponent is the implicit minimal Component for Step 2.
- It defines `system` service and `ping` operation.
- No DomainComponent is required for HelloWorld Step 2.

## 7. What This Enables Next
- Step 3 (CML → CRUD Domain Subsystem) can reuse `Subsystem.executeHttp`.
- CRUD, Memory-First, and Event wiring will be added below Component/Service.
- Subsystem remains the stable entrypoint.

## 8. Non-Goals
- No REST adapter is introduced.
- No OpenAPI/UI generation at this step.
- No authentication or persistence wiring in Step 2.
