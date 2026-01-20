# Subsystem Hygiene Audit — Demo / Built-in Special-Casing

This document enumerates all known hard-coded or special-cased logic
related to initial demos (helloworld), builtin admin/specification/client
components, and ping/system paths.

The goal is classification, not immediate refactoring.

## 1. Keyword Scan (helloworld / ping / admin / system)

docs/strategy/cncf-development-strategy.md:- Subsystem as a reusable execution unit.
docs/strategy/cncf-development-strategy.md:### Phase 1: HelloWorld Bootstrap
docs/strategy/cncf-development-strategy.md:- Artifact (notes): `docs/notes/helloworld-bootstrap.md`.
docs/strategy/cncf-development-strategy.md:### Phase 1.5: Subsystem Execution Model Fix (Internal)
docs/strategy/cncf-development-strategy.md:- Artifact (notes): `docs/notes/helloworld-step2-subsystem-execution.md`.
docs/strategy/cncf-development-strategy.md:### Phase 2: HelloWorld Demo Strategy
docs/strategy/cncf-development-strategy.md:- Artifact (notes): `docs/notes/helloworld-demo-strategy.md`.
docs/strategy/cncf-development-strategy.md:  - CLI exit code mapping
docs/strategy/cncf-development-strategy.md:  - HTTP status mapping
docs/strategy/cncf-development-strategy.md:- `docs/notes/helloworld-demo-strategy.md`
docs/strategy/cncf-development-strategy.md:- `docs/notes/helloworld-bootstrap.md`
docs/strategy/cncf-development-strategy.md:  - Mapping extracts only operationally relevant information
docs/strategy/cncf-development-strategy.md:  - Mapping normalizes results to valid 8-bit exit codes.
docs/strategy/cncf-development-strategy.md:### Phase 3: CML → CRUD Domain Subsystem
docs/strategy/cncf-development-strategy.md:- Artifact (notes): `docs/notes/cml-crud-domain-subsystem-bootstrap.md`.
docs/strategy/cncf-development-strategy.md:- Goal: introduce a first-class state machine model usable by domain subsystems and components.
docs/strategy/cncf-development-strategy.md:- No skipping phases.
docs/rules/executable-spec-display-and-tagging-rules.md:using higher-level grouping constructs (e.g. `when`, `afterWord`).
docs/rules/executable-spec-display-and-tagging-rules.md:### Rule 2.2 Mapping to Specification Rules
docs/rules/executable-spec-display-and-tagging-rules.md:while keeping the text declarative and free of result-producing code.
docs/rules/executable-spec-display-and-tagging-rules.md:within the SimpleModeling ecosystem.
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:import org.goldenport.cncf.subsystem.Subsystem
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:  "Client admin system ping" should {
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:        Request.ofOperation("system.ping"),
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:        HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:        HttpCall("GET", "/admin/system/ping", None, Map.empty)
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    "route CLI client ping to the client component" in {
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:      Given("a subsystem with the client component wired to a fake HTTP driver")
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:        Array("http", "get", "/admin/system/ping")
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:          HttpCall("GET", s"${ClientConfig.DefaultBaseUrl}/admin/system/ping", None, Map.empty)
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    "route CLI client POST ping to the client component" in {
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:      Given("a subsystem with the client component wired to a fake HTTP driver")
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:        Array("http", "post", "/admin/system/ping", "-d", "pong")
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:          HttpCall("POST", s"${ClientConfig.DefaultBaseUrl}/admin/system/ping", Some("pong"), Map.empty)
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    val subsystem = TestComponentFactory.emptySubsystem("cncf-client-test")
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:                Request.ofOperation("system.ping"),
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:                Request.ofOperation("system.ping"),
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    subsystem: Subsystem,
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    val subsystem = TestComponentFactory.emptySubsystem("cncf-client-test")
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    subsystem.add(Seq(component))
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:    TestHarness(subsystem, component, runtime, interpreter)
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:      core = _runtimeCore("client-admin-system-ping-spec-runtime", driver, observability),
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:      token = "client-admin-system-ping-spec-runtime"
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:      core = _runtimeCore("client-admin-system-ping-spec-bootstrap-runtime", driver, observability),
src/test/scala/org/goldenport/cncf/client/ClientAdminSystemPingSpec.scala:      token = "client-admin-system-ping-spec-bootstrap-runtime"
docs/rules/spec-style.md:not *what a system does at runtime*.
docs/rules/spec-style.md:- Top-level `should` blocks define **sections** describing public responsibilities or contracts of the model or system.
docs/rules/spec-style.md:A high-level use case or scenario representing a user or system goal.
docs/rules/spec-style.md:- Nested `when` / `should` blocks organize scenario variants or system states.  
docs/rules/spec-style.md:Scenario Specs are executable requirements validating externally observable CNCF behavior at the system boundary (Protocol → Component → Job / Result).  
docs/rules/spec-style.md:Scenario Specs are executable requirements validating externally observable CNCF behavior at the system boundary (Protocol → Component → Job / Result).  
src/test/scala/org/goldenport/cncf/client/ProcedureActionCallSpec.scala:        "/admin/system/ping",
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:import org.goldenport.cncf.subsystem.Subsystem
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:        ("/admin/system/ping", "pong", s"${ClientConfig.DefaultBaseUrl}/admin/system/ping")
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:        ("get", "/admin/system/ping", None, HttpCall(
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:          s"${ClientConfig.DefaultBaseUrl}/admin/system/ping",
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:        ("post", "/admin/system/ping", Some("pong"), HttpCall(
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:          s"${ClientConfig.DefaultBaseUrl}/admin/system/ping",
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:              Request.ofOperation("system.ping"),
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:              Request.ofOperation("system.ping"),
src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala:      TestComponentFactory.emptySubsystem("client-component-spec"),
docs/spec/admin-system-status.md:This specification defines the `admin.system.status` operation,
src/test/scala/org/goldenport/cncf/spec/PathResolutionSpec.scala:          CanonicalPath("builtin", "system", "status"),
src/test/scala/org/goldenport/cncf/spec/PathResolutionSpec.scala:          CanonicalPath("builtin", "system", "health")
src/test/scala/org/goldenport/cncf/spec/PathResolutionSpec.scala:        val result = PathResolution.resolve("builtin/system", operations)
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:  private def _build_components(script: Script)(p: Subsystem): Vector[Component] = {
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:import org.goldenport.cncf.subsystem.Subsystem
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:      classOf[Subsystem],
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:      classOf[Subsystem],
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:    aliasConfig("ping" -> "admin.system.ping")
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:    "rewrite ping to admin.system.ping before CanonicalPath resolution" in {
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:      Given("a runtime with ping alias configured")
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        val subsystem = DefaultSubsystemFactory.default(Some("command"))
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          subsystem,
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          Array("ping"),
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:            request.component.value shouldBe "admin"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:            request.service.value shouldBe "system"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          request.operation shouldBe "ping"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:    "apply the same alias metdata and resolve ping to admin.system.ping" in {
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:      Given("a script runtime configured with the ping alias")
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        val subsystem = DefaultSubsystemFactory.default(Some("script"))
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          subsystem,
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          Array("ping")
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:            request.component.value shouldBe "admin"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:            request.service.value shouldBe "system"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          request.operation shouldBe "ping"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:    "strip the alias selector and dispatch to admin.system.ping" in {
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:      Given("an alias table and a request for /ping")
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        val subsystem = DefaultSubsystemFactory.default(Some("server"))
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        val request = HttpRequest.fromPath(HttpRequest.GET, "/ping")
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        val response = subsystem.executeHttp(request)
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          subsystemName = GlobalRuntimeContext.SubsystemName,
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:          subsystemVersion = CncfVersion.current,
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        "ping" -> "admin.system.ping",
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        "ping" -> "admin.system.ping"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:        "bad-alias" -> "admin.system.ping"
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:      subsystemName = GlobalRuntimeContext.SubsystemName,
src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala:      subsystemVersion = CncfVersion.current
docs/spec/config-resolution.md:    - persistent filesystem coupling
docs/spec/path-resolution.md:This specification defines the general path resolution rules used by the system
docs/rules/specification-linking-and-rule-numbering-rules.md:They do not define system behavior, but they constrain
docs/ai/component-initialization.md:  subsystem, core, and origin explicitly.
src/test/scala/org/goldenport/cncf/testutil/TestComponentFactory.scala:import org.goldenport.cncf.subsystem.Subsystem
src/test/scala/org/goldenport/cncf/testutil/TestComponentFactory.scala:  def emptySubsystem(
src/test/scala/org/goldenport/cncf/testutil/TestComponentFactory.scala:  ): Subsystem =
src/test/scala/org/goldenport/cncf/testutil/TestComponentFactory.scala:    Subsystem(
src/test/scala/org/goldenport/cncf/testutil/TestComponentFactory.scala:    val dummy = emptySubsystem("test")
docs/ai/codex-editing-rules.md:- Modify multiple subsystems “to make it work”
docs/ai/codex-editing-rules.md:Stopping is **correct behavior**.
src/test/scala/org/goldenport/cncf/SCENARIO/ArgsToStringScenarioSpec.scala: * This executable spec verifies that a CNCF system can execute
docs/ai/ai-human-collaboration-convention.md:- Question: Did we build the system right?
docs/ai/ai-human-collaboration-convention.md:- Question: Did we build the right system?
docs/spec/glossary.md:    - mapping errors into Consequence / Conclusion
docs/spec/glossary.md:SystemContext represents system-scoped runtime assumptions provided at
docs/spec/output-format.md:scope=system-wide
docs/spec/output-format.md:- `ping.json`
docs/spec/output-format.md:- `/admin/system/status.json`
docs/spec/output-format.md:Typical mappings:
docs/notes/helloworld-step2-subsystem-execution.md:# HelloWorld Step 2: Subsystem Execution Model
docs/notes/helloworld-step2-subsystem-execution.md:- Establish the Subsystem execution model before domain-level expansion.
docs/notes/helloworld-step2-subsystem-execution.md:- HTTP Server → Subsystem → Component → Service → Operation
docs/notes/helloworld-step2-subsystem-execution.md:- `Subsystem.executeHttp` is the only HTTP entrypoint.
docs/notes/helloworld-step2-subsystem-execution.md:## 3. Subsystem Responsibilities
docs/notes/helloworld-step2-subsystem-execution.md:- Delegate execution to Component/Service; Subsystem does not execute actions.
docs/notes/helloworld-step2-subsystem-execution.md:- Subsystem uses Consequence end-to-end.
docs/notes/helloworld-step2-subsystem-execution.md:## 6. HelloWorld Admin Component
docs/notes/helloworld-step2-subsystem-execution.md:- It defines `system` service and `ping` operation.
docs/notes/helloworld-step2-subsystem-execution.md:- No DomainComponent is required for HelloWorld Step 2.
docs/notes/helloworld-step2-subsystem-execution.md:- Step 3 (CML → CRUD Domain Subsystem) can reuse `Subsystem.executeHttp`.
docs/notes/helloworld-step2-subsystem-execution.md:- Subsystem remains the stable entrypoint.
docs/rules/document-boundary.md:contracts, and semantics of the system.
docs/rules/document-boundary.md:  canonical behavior of the system.
docs/notes/omponent-discovery-from-classdir.md:- No production-grade plugin system
docs/notes/omponent-discovery-from-classdir.md:## 9. Stage Mapping
docs/rules/context-and-scope.md:- Shareable across Subsystem, Component, Service, and Action.
docs/rules/context-and-scope.md:ScopeKind is an enum that expresses structural execution boundaries used by the system.
docs/rules/context-and-scope.md:- Subsystem
docs/design/design-consolidated.md:componentlets in cloud-native, event-centered systems. It standardizes:
docs/design/design-consolidated.md:    - Documents how `CncfRuntime` / `ScriptRuntime` entry points feed `Subsystem`, `ComponentLogic`, and `ActionEngine`.
docs/design/design-consolidated.md:  - Establishes the execution model for domain subsystems,
docs/design/design-consolidated.md:  - Defines how system and subsystem configurations are normalized
docs/design/design-consolidated.md:    - Describes how configuration flows from runtime → subsystem → component and how semantic builders consume `ResolvedConfiguration`.
docs/design/client-component-action-api.md:- Keep the existing Request-based behavior intact (used by CLI → Subsystem → Component path).
docs/design/client-component-action-api.md:  - STOP using Request → Subsystem.execute → ClientComponent path.
docs/design/client-component-action-api.md:- Subsystem default -> Component default -> ExecutionContext resolver -> UnitOfWork -> UnitOfWorkInterpreter
docs/design/client-component-action-api.md:- This aligns `run client ...` output with curl/server-emulator output (e.g. the ping runtime text block for `admin system ping`).
docs/design/job-plan-expected-event.md:- Strong typing per DomainEvent subtype
docs/notes/scala-cli/scala-cli-component-runbook.md:- Phase/Stage mapping: Phase 2.6 / Stage 5 / Step 3 (Exact commands included)
docs/notes/scala-cli/scala-cli-component-runbook.md:CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command admin component list --component-repository=scala-cli
docs/notes/scala-cli/scala-cli-component-runbook.md:- admin component list includes `hello` or `demo.DemoComponent`
src/test/scala/org/goldenport/cncf/specification/SCENARIO/OpenApiProjectionScenarioSpec.scala:import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
src/test/scala/org/goldenport/cncf/specification/SCENARIO/OpenApiProjectionScenarioSpec.scala:      val subsystem = DefaultSubsystemFactory.default()
src/test/scala/org/goldenport/cncf/specification/SCENARIO/OpenApiProjectionScenarioSpec.scala:      subsystem.execute(req) match {
src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala:      kind = ScopeKind.Subsystem,
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:package org.goldenport.cncf.subsystem.resolver
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:        Seq("admin.user.find", "admin.user.list")
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:          fqn shouldBe "admin.user.find"
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:        Seq("admin.user.find", "admin.group.list")
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:          fqn shouldBe "admin.user.find"
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:    "not apply to operation-name-only input such as ping" in {
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:        Seq("admin.default.ping")
src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala:      resolver.resolve("ping") match {
docs/design/job-management.md:    - log-based systems
docs/design/job-management.md:the system is already broken.
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:package org.goldenport.cncf.subsystem
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:class SubsystemExecuteHttpSpec extends AnyWordSpec with Matchers with OptionValues {
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:  "Subsystem.executeHttp" should {
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:    "return runtime introspection for admin.system.ping" in {
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:      val subsystem = DefaultSubsystemFactory.default(Some("server"))
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:      val res = subsystem.executeHttp(req)
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:        subsystemName = GlobalRuntimeContext.SubsystemName,
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:        subsystemVersion = CncfVersion.current,
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:      val subsystem = DefaultSubsystemFactory.default()
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/unknown")
src/test/scala/org/goldenport/cncf/subsystem/SubsystemExecuteHttpSpec.scala:      val res = subsystem.executeHttp(req)
src/test/scala/org/goldenport/cncf/observability/ObservabilityEngineSpec.scala:      name = "ping",
src/test/scala/org/goldenport/cncf/observability/ObservabilityEngineSpec.scala:        operation = Some(OperationContext("admin.system.ping")),
src/test/scala/org/goldenport/cncf/observability/ObservabilityEngineSpec.scala:      keys.contains("scope.subsystem") shouldBe true
src/test/scala/org/goldenport/cncf/observability/ObservabilityEngineSpec.scala:        operation = Some(OperationContext("admin.system.ping")),
docs/design/execution-model.md:Each entry point builds a `Subsystem` from resolved configuration and
docs/design/execution-model.md:Ingress → Subsystem
docs/design/execution-model.md:  passes the resulting `ResolvedConfiguration` into `DefaultSubsystemFactory`,
docs/design/execution-model.md:  returning a configured `Subsystem`.
docs/design/execution-model.md:  handlers feed requests to `Subsystem.execute` or `executeHttp`.
docs/design/execution-model.md:- The subsystem owns the resolved configuration snapshot and resolves
docs/design/execution-model.md:- `ComponentLogic.createActionCall` layers subsystem/component HTTP drivers,
docs/design/execution-model.md:- CNCF may adapt `OperationRequest`s for subsystem/component routing without
docs/design/execution-model.md:- It still resolves configuration first, builds the `Subsystem`, and then
docs/design/execution-model.md:- Subsystem and Component scopes are children in the ScopeContext tree.
docs/design/execution-model.md:4. Create Subsystem and Component scopes as children.
docs/design/execution-model.md:- No multi-subsystem runtime assumptions.
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:  val subsystemName: String,
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:  var subsystemVersion: String
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:      subsystemName,
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:      subsystemVersion,
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:  def updateSubsystemVersion(version: String): Unit =
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:    subsystemVersion = version
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:  final val SubsystemName = "goldenport-cncf"
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:    subsystemName: String,
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:    subsystemVersion: String,
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:      s"subsystem: ${subsystemName}\n" +
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:      s"subsystem.version: ${subsystemVersion}"
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:      subsystemName = SubsystemName,
src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala:      subsystemVersion = _defaultRuntimeVersion,
src/main/scala/org/goldenport/cncf/service/Service.scala:    // ExecutionContext.createWithSystem(logic.component.systemContext)
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:  subsystem: Subsystem
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:    subsystem.executeHttp(req)
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:  import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:    def subsystem(): Subsystem =
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:      DefaultSubsystemFactory.default()
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:      new HttpExecutionEngine(subsystem())
src/main/scala/org/goldenport/cncf/CncfMain.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/CncfMain.scala:  ): Subsystem => Seq[Component] = {
src/main/scala/org/goldenport/cncf/CncfMain.scala:      (subsystem: Subsystem) => {
src/main/scala/org/goldenport/cncf/CncfMain.scala:        val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
src/main/scala/org/goldenport/cncf/CncfMain.scala:  ): Subsystem => Seq[Component] = {
src/main/scala/org/goldenport/cncf/CncfMain.scala:    (subsystem: Subsystem) => {
src/main/scala/org/goldenport/cncf/CncfMain.scala:      val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
docs/notes/subsystem-execution-mode.md:# Subsystem Execution Modes (Notes)
docs/notes/subsystem-execution-mode.md:scope = subsystem / cli / execution-model  
docs/notes/subsystem-execution-mode.md:In CNCF, Subsystems are primarily designed to run as long-lived services
docs/notes/subsystem-execution-mode.md:However, during actual system operation, we frequently encounter use cases
docs/notes/subsystem-execution-mode.md:where we want to reuse the *same Subsystem logic* in non-server contexts,
docs/notes/subsystem-execution-mode.md:This note explores how Subsystems can be executed
docs/notes/subsystem-execution-mode.md:If Subsystems are treated as “server-only” artifacts:
docs/notes/subsystem-execution-mode.md:A **Subsystem** is an executable unit whose *logic* is independent
docs/notes/subsystem-execution-mode.md:  - used by applications and external systems
docs/notes/subsystem-execution-mode.md:  - used for operational and administrative tasks
docs/notes/subsystem-execution-mode.md:The same Subsystem definition should be usable in all of these modes.
docs/notes/subsystem-execution-mode.md:## 3.1 Relationship to HelloWorld Bootstrap
docs/notes/subsystem-execution-mode.md:The **HelloWorld Bootstrap** establishes the minimal execution path
docs/notes/subsystem-execution-mode.md:for CNCF Subsystems by focusing exclusively on **Server mode** execution.
docs/notes/subsystem-execution-mode.md:Subsystem definition can later be reused in additional execution modes
docs/notes/subsystem-execution-mode.md:- [HelloWorld Bootstrap](helloworld-bootstrap.md)
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:          Array("http", "get", "/admin/system/ping"),
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:          "/admin/system/ping",
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:          Array("http", "post", "/admin/system/ping", "-d", "pong"),
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:          "/admin/system/ping",
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:        Array("admin", "system", "ping")
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:            Argument("path", "/admin/system/ping", None)
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:          Array("http", "post", "/admin/system/ping", "-d", s"@${file}")
src/test/scala/org/goldenport/cncf/cli/ClientRequestNormalizationSpec.scala:        Array("http", "get", "/admin/system/ping", "--baseurl", "http://example.test")
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:  subsystem: String,
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:  major = subsystem,
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:  subsystem: String,
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:  major   = subsystem,
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:  subsystem: String,
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:  major = subsystem,
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:    val subsystem =
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:      if (kind == ScopeKind.Subsystem) Some(name)
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:      else _find_subsystem_(parent)
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:    val sanitizedSubsystem = subsystem.map(_sanitize_label)
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:      sanitizedSubsystem.map(sub => SpanId(sub, sanitizedName, _span_kind_label_(kind, sanitizedName)))
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:      correlationId.orElse(sanitizedSubsystem.map(sub => CorrelationId(sub, "runtime")))
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:  private def _find_subsystem_(scope: ScopeContext): Option[String] =
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:      case s if s.core.kind == ScopeKind.Subsystem => Some(s.core.name)
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:      case s => s.core.parent.flatMap(p => _find_subsystem_(p))
src/main/scala/org/goldenport/cncf/context/ObservabilityContext.scala:      case ScopeKind.Subsystem => "subsystem"
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:      val subsystem = DefaultSubsystemFactory.default(Some("command"))
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:      CncfRuntime.parseCommandArgs(subsystem, Array("admin", "system", "ping")) match {
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          req.component.getOrElse(fail("missing component")).shouldBe("admin")
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          req.service.getOrElse(fail("missing service")).shouldBe("system")
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          req.operation.shouldBe("ping")
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:      val subsystem = DefaultSubsystemFactory.default(Some("command"))
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:      CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping")) match {
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          req.component.getOrElse(fail("missing component")).shouldBe("admin")
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          req.service.getOrElse(fail("missing service")).shouldBe("system")
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          req.operation.shouldBe("ping")
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:    "execute via Subsystem.executeHttp" in {
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:      val subsystem = DefaultSubsystemFactory.default(Some("server"))
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:        Seq("admin", "system", "ping"),
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          val res = subsystem.executeHttp(req)
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:            subsystemName = GlobalRuntimeContext.SubsystemName,
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:            subsystemVersion = CncfVersion.current,
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:      val subsystem = DefaultSubsystemFactory.default(Some("server"))
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:        Seq("admin.system.ping"),
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:          val res = subsystem.executeHttp(req)
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:            subsystemName = GlobalRuntimeContext.SubsystemName,
src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala:            subsystemVersion = CncfVersion.current,
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:package org.goldenport.cncf.subsystem.resolver
docs/notes/scope-context-design.md:(Subsystem / Component / Service / Action).
docs/notes/scope-context-design.md:  case Subsystem
docs/notes/scope-context-design.md:Each scope owns the deterministic rules that sanitize labels, produce the next `TraceId`/`SpanId` pair, and promote a `CorrelationId` when a subsystem boundary is crossed.
docs/notes/scope-context-design.md:- sanitizes subsystem/service/action names for observability labels,
docs/notes/scope-context-design.md:- and promotes a `CorrelationId` whenever a new subsystem scope is created.
docs/design/variation-and-extension-points.md:## 5. CNCF-Specific Mapping
src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala: * It exists only during runtime and MUST NOT contain application, system, or configuration state.
src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala:              clock = Clock.systemUTC(),
src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala:          clock = Clock.systemUTC(),
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:package org.goldenport.cncf.subsystem
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:import org.goldenport.cncf.component.builtin.admin.AdminComponent
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:object DefaultSubsystemFactory {
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  private val _admin = AdminComponent.Factory
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  private val _subsystem_name = GlobalRuntimeContext.SubsystemName
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  def subsystemName: String = _subsystem_name
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  ): Subsystem =
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:        kind = ScopeKind.Subsystem,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:        name = _subsystem_name,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  ): Subsystem = {
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    val subsystem = default(mode)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:      subsystem.add(extras)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    subsystem
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  ): Subsystem = {
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    val driver = _resolve_http_driver(runtimeConfig, _subsystem_name)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    val subsystem =
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:      Subsystem(
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:        name = _subsystem_name,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:              context.createChildScope(ScopeKind.Subsystem, _subsystem_name)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:            case ScopeKind.Subsystem =>
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:                kind = ScopeKind.Subsystem,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:                name = _subsystem_name,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    val comps = Vector(_admin, _client, _spec)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    subsystem.add(comps)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    subsystemName: String
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:      val ping = GlobalRuntimeContext.current
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:      FakeHttpDriver.okText(ping)
src/main/scala/org/goldenport/cncf/context/ScopeContext.scala:  case Subsystem
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:package org.goldenport.cncf.subsystem
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:final class Subsystem(
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  def add(comps: Seq[Component]): Subsystem = {
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  // def addComponent(name: String, comp: Component): Subsystem = {
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  // TODO SubsystemContext extends ScopeContext
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  private val _subsystem_scope_context: ScopeContext =
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:        kind = ScopeKind.Subsystem,
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:      parent = _subsystem_scope_context,
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  //     val sc = _subsystem_scope_context.createChildScope(
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:        val _ = _subsystem_scope_context.observe_error(
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala://    _ensure_system_context(component)
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  // private def _ensure_system_context(
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  //   val system = component.systemContext
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  //   val snapshot = system.configSnapshot
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  //     val subsystemVersion = version.getOrElse(runtimeVersion)
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  //       "cncf.subsystem" -> name,
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  //       "cncf.subsystem.version" -> subsystemVersion
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  //     component.withSystemContext(system.copy(configSnapshot = updated))
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:object Subsystem {
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:        conf.get[String]("cncf.subsystem.http.driver").flatMap {
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:          case None        => Consequence.failure("cncf.subsystem.http.driver is required")
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:          .get[String]("cncf.subsystem.mode")
docs/notes/audit/demo-hardcoding-audit.md:This report lists hard-coded paths, names, or special-case logic related to initial demos (helloworld), ping, admin, or builtin handling.
docs/notes/audit/demo-hardcoding-audit.md:#### Keyword: helloworld
docs/notes/audit/demo-hardcoding-audit.md:#### Keyword: HelloWorld
docs/notes/audit/demo-hardcoding-audit.md:#### Keyword: ping
docs/notes/audit/demo-hardcoding-audit.md:#### Keyword: admin
docs/notes/audit/demo-hardcoding-audit.md:#### Keyword: system
docs/design/component-factory.md:   on the system/application classpath.
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:package org.goldenport.cncf.subsystem
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:final case class SubsystemModel(
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:  tier: SubsystemTier,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:  kind: SubsystemKind,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:sealed trait SubsystemTier
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:object SubsystemTier {
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:  case object Domain extends SubsystemTier
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:sealed trait SubsystemKind
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:object SubsystemKind {
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:  case object Service extends SubsystemKind
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:object DefaultSubsystemProvider {
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:  def default(): SubsystemModel =
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:    SubsystemModel(
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:      tier = SubsystemTier.Domain,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:      kind = SubsystemKind.Service,
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:object DefaultSubsystemMapping {
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:    subsystem: SubsystemModel
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:        name = "ping",
src/main/scala/org/goldenport/cncf/subsystem/SubsystemModel.scala:      name = subsystem.name,
docs/notes/conclusion-observation-design.md:Typical mapping:
docs/design/component-model.md:*admitted* into the system before execution is coordinated by the Engine.
docs/design/component-model.md:- SecurityEvent is treated as a system-level event distinct from ActionEvent / DomainEvent.
docs/design/component-model.md:the system must continue to function.
docs/notes/error-semantics.md:- Mapping errors to CLI exit codes
docs/notes/error-semantics.md:- Mapping errors to HTTP status codes
docs/notes/error-semantics.md:CNCF defines HTTP status mapping based on error semantics.
docs/notes/error-semantics.md:Exact mappings are defined later in this phase.
src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpSpec.scala:        Free.liftF[UnitOfWorkOp, HttpResponse](UnitOfWorkOp.HttpGet("/ping"))
src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpSpec.scala:      driver.calls shouldBe Vector("GET /ping")
docs/design/component-internal-execution-model.md:- Event: fact produced by execution (domain/system).
docs/design/component-loading.md:while keeping the operational model simple and explicit.
docs/design/component-loading.md:  command demo ping
docs/notes/observability-record-namespace.md:- metrics systems
docs/notes/observability-record-namespace.md:| `scope.subsystem` | Logical subsystem name |
docs/notes/phase-2.8-infrastructure-hygiene.md:- Reorganize CLI structure (HelloWorld CLI positioning)
docs/notes/phase-2.8-infrastructure-hygiene.md:## Subsystem Hygiene
docs/notes/phase-2.8-infrastructure-hygiene.md:- Reorganize HelloWorld subsystem structure
docs/notes/phase-2.8-infrastructure-hygiene.md:- In Phase 2.6, `/spec/current/openapi(.json)` is confirmed to execute correctly via the standard Component / Subsystem execution path.
docs/notes/phase-2.8-infrastructure-hygiene.md:- The current OpenAPI output intentionally includes only minimal domain APIs (e.g., admin/system/ping).
docs/notes/phase-2.8-infrastructure-hygiene.md:     - inclusion/exclusion of admin APIs
docs/notes/phase-2.8-infrastructure-hygiene.md:  - Core / Runtime / Subsystem layers must not call `exit`.
docs/notes/phase-2.8-infrastructure-hygiene.md:- Ensure that all logging configuration is context-aware and can be dynamically adjusted at runtime boundaries (e.g., per subsystem, per component, per request).
docs/notes/phase-2.8-infrastructure-hygiene.md:| Semantic configuration / propagation (Subsystem.Config, Component.Config, runtime helpers) | Phase 2.6 Stage 3/5 deferred list + canonical configuration model | **DONE** | Builders now exist beside their owners, and the `Configuration Propagation Model` section of `configuration-model.md` captures the propagation path, so the semantic layer is established. |
docs/notes/phase-2.8-infrastructure-hygiene.md:- `docs/design/configuration-model.md#configuration-propagation-model` (semantic propagation from system → subsystem → component)
docs/notes/phase-2.8-infrastructure-hygiene.md:  - subsystem-agnostic
docs/notes/phase-2.8-infrastructure-hygiene.md:- Multiple systems (CNCF, SIE, CLI tools, future runtimes) rely on the same
docs/notes/phase-2.8-infrastructure-hygiene.md:applications, subsystems, and components is formally named
docs/notes/phase-2.8-infrastructure-hygiene.md:- Operation name-only inputs (e.g. `ping`, `find`, `test.html`) are
docs/notes/phase-2.8-infrastructure-hygiene.md:  - Inputs such as `ping`, `find`, or file-like strings (e.g. `test.html`)
docs/notes/phase-2.8-infrastructure-hygiene.md:- built-in admin operations
docs/notes/phase-2.8-infrastructure-hygiene.md:(e.g. `admin.default.ping`) are intentionally out of scope for Phase 2.8
docs/notes/phase-2.8-infrastructure-hygiene.md:- The configuration mechanism is runtime-agnostic and subsystem-agnostic.
docs/notes/phase-2.8-infrastructure-hygiene.md:- Multiple runtimes (CNCF, SIE, CLI tools, future systems) depend on identical deterministic resolution.
docs/notes/phase-2.8-infrastructure-hygiene.md:- **configuration**: generic, schema-free, property-based mechanism for application/subsystem/component configuration.
docs/notes/phase-2.8-infrastructure-hygiene.md:- **SubsystemScopeContext**: Inherits from the runtime scope; may override the log level only (no independent logging configuration).
docs/notes/phase-2.8-infrastructure-hygiene.md:- **ComponentScopeContext**: Inherits from the subsystem scope; does not have an independent logging policy.
docs/notes/phase-2.8-infrastructure-hygiene.md:4. Subsystem creation
docs/notes/phase-2.8-infrastructure-hygiene.md:- Only a single subsystem and single runtime are supported.
docs/notes/phase-2.8-infrastructure-hygiene.md:- Multi-subsystem per virtual machine (VM)
docs/notes/phase-2.8-infrastructure-hygiene.md:      - Propagates the driver to subsystems implicitly via context usage.
docs/notes/phase-2.8-infrastructure-hygiene.md:    - **Subsystem / Component**
docs/notes/phase-2.8-infrastructure-hygiene.md:    5. Subsystem creation
docs/notes/phase-2.8-infrastructure-hygiene.md:    - No per-subsystem or per-component override is allowed.
docs/notes/phase-2.8-infrastructure-hygiene.md:    - Multi-runtime or multi-subsystem coexistence
docs/notes/phase-2.8-infrastructure-hygiene.md:    - Does not interact with Subsystem, Component, Protocol, or ActionCall.
docs/notes/phase-2.8-infrastructure-hygiene.md:  - Reorganize CLI structure (including HelloWorld CLI).
docs/notes/phase-2.8-infrastructure-hygiene.md:- [ ] **Subsystem Hygiene**
docs/notes/phase-2.8-infrastructure-hygiene.md:  - Reorganize HelloWorld subsystem structure.
docs/notes/phase-2.8-infrastructure-hygiene.md:  - Subsystem and Component initialization consume prepared runtime context.
docs/notes/phase-2.8-infrastructure-hygiene.md:  - Notes: admin / specification / client are builtin; demo/helloworld are not
docs/notes/phase-2.8-infrastructure-hygiene.md:      (e.g. `ping`, admin paths, alias / shortcut policy)
docs/notes/phase-2.8-infrastructure-hygiene.md:    subsystem creation, and component execution.
docs/notes/phase-2.8-infrastructure-hygiene.md:- Multi-subsystem and multi-runtime coexistence.
docs/notes/phase-2.8-infrastructure-hygiene.md:  (domain APIs vs admin vs meta/spec APIs).
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      subsystemName = GlobalRuntimeContext.SubsystemName,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      subsystemVersion = CncfVersion.current
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        kind = ScopeKind.Subsystem,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        name = DefaultSubsystemFactory.subsystemName,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:  def buildSubsystem(
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component] = _ => Nil,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:  ): Subsystem = {
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val subsystem = DefaultSubsystemFactory.defaultWithScope(
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    GlobalRuntimeContext.current.foreach(_.updateSubsystemVersion(subsystem.version.getOrElse(CncfVersion.current)))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val extras = extraComponents(subsystem)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      subsystem.add(extras)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    //   _apply_system_context(subsystem, label)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    subsystem
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val engine = new HttpExecutionEngine(buildSubsystem(mode = Some(RunMode.Server)))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component]
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val engine = new HttpExecutionEngine(buildSubsystem(extraComponents, Some(RunMode.Server)))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val subsystem = buildSubsystem(mode = Some(RunMode.Client))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val result = _client_component(subsystem).flatMap { component =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      parseClientArgs(args, Some(subsystem.configuration)).flatMap { req =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component]
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Client))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val result = _client_component(subsystem).flatMap { component =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      parseClientArgs(args, Some(subsystem.configuration)).flatMap { req =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val subsystem = buildSubsystem(mode = Some(RunMode.Command))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val result = _to_request(subsystem, args).flatMap { req =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      subsystem.execute(req)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component]
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Command))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val result = _to_request(subsystem, args).flatMap { req =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      subsystem.execute(req)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:            val engine = new HttpExecutionEngine(buildSubsystem(mode = Some(RunMode.ServerEmulator)))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component]
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:            val engine = new HttpExecutionEngine(buildSubsystem(extraComponents, Some(RunMode.ServerEmulator)))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component]
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Script))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    _to_request_script(subsystem, args).flatMap { req =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      subsystem.execute(req)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    parseCommandArgs(subsystem, args, RunMode.Script) match {
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:            _to_request(subsystem, args, RunMode.Script)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:            _to_request(subsystem, xs.toArray, RunMode.Script)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component]
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        // - Consider per-mode defaults (server/client/command/server-emulator/script) while keeping CLI normalization execution-free.
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        // - Consider per-mode defaults (server/client/command/server-emulator/script) while keeping CLI normalization execution-free.
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        |  cncf command admin.system.ping
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    extraComponents: Subsystem => Seq[Component]
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    subsystem.components.collectFirst { case c: ClientComponent => c } match {
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    parseCommandArgs(subsystem, args, mode)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:  // private def _apply_system_context(
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:  //   subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:  //   val system = SystemContext.empty
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:  //   subsystem.components.foreach(_.withSystemContext(system))
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:                // "system.ping", // TODO generic
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:                // "system.ping",
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:      subsystem.resolver.resolve(canonicalSelector, allowPrefix = false, allowImplicit = false) match {
docs/design/job-event-log.md:Storage Mapping (Example)
docs/notes/error-system-candidates.md:  (Subsystem, component, or feature area)
src/main/scala/org/goldenport/cncf/openapi/OpenApiProjector.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/openapi/OpenApiProjector.scala:  def forSubsystem(subsystem: Subsystem): String = {
src/main/scala/org/goldenport/cncf/openapi/OpenApiProjector.scala:      subsystem.components.flatMap { comp =>
src/main/scala/org/goldenport/cncf/observability/ObservabilityEngine.scala:      "scope.subsystem" -> scope.name,
docs/notes/specification-component-design.md:- HelloWorldHttpServer must not own OpenAPI logic directly.
docs/notes/specification-component-design.md:- The current OpenAPI exposure is a stepping stone, not the final taxonomy.
docs/design/memory-first-domain-architecture.md:scope = domain-tier / subsystem-runtime
docs/design/memory-first-domain-architecture.md:Cloud Native Component Framework (CNCF) adopts a component–subsystem–system
docs/design/memory-first-domain-architecture.md:architecture to enable modular, cloud-native systems.
docs/design/memory-first-domain-architecture.md:As a result, systems relied on:
docs/design/memory-first-domain-architecture.md:- Domain decomposition at the *subsystem level* reduces dataset size.
docs/design/memory-first-domain-architecture.md:> **One subsystem per functional boundary**.
docs/design/memory-first-domain-architecture.md:of a domain subsystem in memory.
docs/design/memory-first-domain-architecture.md:The database is no longer the “center of the system”;
docs/design/memory-first-domain-architecture.md:## 6. Subsystem-Level Deployment
docs/design/memory-first-domain-architecture.md:> **One functional capability = one subsystem = one server (by default)**
docs/design/memory-first-domain-architecture.md:- Loaded at subsystem startup
docs/design/memory-first-domain-architecture.md:In an EC system, this typically means:
docs/design/memory-first-domain-architecture.md:Keeping active tasks in memory radically simplifies event handling.
docs/design/memory-first-domain-architecture.md:2. **Subsystem sharding**  
docs/design/memory-first-domain-architecture.md:> enables simple, reliable, and cost-efficient systems,
docs/notes/helloworld-bootstrap.md:# HelloWorld Bootstrap
docs/notes/helloworld-bootstrap.md:scope = cncf-run / subsystem-bootstrap / admin / web / openapi
docs/notes/helloworld-bootstrap.md:The HelloWorld Bootstrap defines the **minimum executable experience**
docs/notes/helloworld-bootstrap.md:- A Subsystem successfully boots
docs/notes/helloworld-bootstrap.md:This document is the single source of truth for the HelloWorld bootstrap,
docs/notes/helloworld-bootstrap.md:The overall demo strategy is documented in `docs/notes/helloworld-demo-strategy.md`.
docs/notes/helloworld-bootstrap.md:- CNCF starts with a built-in default subsystem
docs/notes/helloworld-bootstrap.md:[INFO] No subsystem configuration provided.
docs/notes/helloworld-bootstrap.md:[INFO] Starting default HelloWorld subsystem.
docs/notes/helloworld-bootstrap.md:- Subsystem name, tier, kind
docs/notes/helloworld-bootstrap.md:curl http://localhost:8080/admin/ping
docs/notes/helloworld-bootstrap.md:subsystem: cncf
docs/notes/helloworld-bootstrap.md:- `/admin/meta`
docs/notes/helloworld-bootstrap.md:- `/admin/stats`
docs/notes/helloworld-bootstrap.md:- `/admin/health`
docs/notes/helloworld-bootstrap.md:## 3. Default HelloWorld Subsystem
docs/notes/helloworld-bootstrap.md:the following implicit SubsystemModel:
docs/notes/helloworld-bootstrap.md:SubsystemModel(
docs/notes/helloworld-bootstrap.md:This subsystem exists purely to validate
docs/notes/helloworld-bootstrap.md:## 4. Components in the Bootstrap Subsystem
docs/notes/helloworld-bootstrap.md:- `/admin/ping`
docs/notes/helloworld-bootstrap.md:- `/admin/meta`
docs/notes/helloworld-bootstrap.md:- `/admin/stats`
docs/notes/helloworld-bootstrap.md:- `/admin/health`
docs/notes/helloworld-bootstrap.md:in all subsystems.
docs/notes/helloworld-bootstrap.md:- Visual confirmation that the subsystem is running
docs/notes/helloworld-bootstrap.md:## 6. Running with a User-Defined Subsystem
docs/notes/helloworld-bootstrap.md:Users may provide their own minimal subsystem definition:
docs/notes/helloworld-bootstrap.md:cncf run hello-subsystem.conf
docs/notes/helloworld-bootstrap.md:subsystem {
docs/notes/helloworld-bootstrap.md:- The default subsystem is replaced
docs/notes/helloworld-bootstrap.md:that they can control subsystem definitions safely.
docs/notes/helloworld-bootstrap.md:The HelloWorld Bootstrap intentionally excludes:
docs/notes/helloworld-bootstrap.md:The HelloWorld Bootstrap intentionally focuses on **Server mode** execution only.
docs/notes/helloworld-bootstrap.md:However, CNCF Subsystems are designed to support multiple execution modes
docs/notes/helloworld-bootstrap.md:- [Subsystem Execution Modes](subsystem-execution-modes.md)
docs/notes/helloworld-bootstrap.md:The HelloWorld Bootstrap keeps these modes out of scope for simplicity,
docs/notes/helloworld-bootstrap.md:but its design assumes that Subsystem logic is transport-independent
docs/notes/helloworld-bootstrap.md:Once the HelloWorld Bootstrap is confirmed,
docs/notes/helloworld-bootstrap.md:- Bootstrapping a Domain Subsystem with behavior
docs/notes/helloworld-bootstrap.md:The bootstrap subsystem provides
docs/notes/helloworld-bootstrap.md:> The HelloWorld Bootstrap guarantees that CNCF
docs/notes/helloworld-bootstrap.md:- Subsystem → Component → Service → Action の ScopeContext 伝播を実装した。
docs/notes/helloworld-bootstrap.md:Stage 2 の目的は、command 実行系を Subsystem 実行モデルに接続し、
docs/notes/helloworld-bootstrap.md:- 対象は HelloWorld Bootstrap に限定する。
docs/notes/helloworld-bootstrap.md:- ping のような「必ず成功する Action」を基準にする。
docs/notes/helloworld-bootstrap.md:- command 実行要求を Subsystem.execute に委譲する。
docs/notes/helloworld-bootstrap.md:- `cncf run command admin.system.ping` が
docs/notes/helloworld-bootstrap.md:  - 実行経路（CLI → Subsystem → ActionEngine）
src/main/scala/org/goldenport/cncf/component/builtin/client/ClientComponent.scala:          // "system.ping",
src/main/scala/org/goldenport/cncf/component/builtin/client/ClientComponent.scala:          // "system.ping",
docs/design/event-id-event-type.md:UniversalId Mapping
docs/design/event-id-event-type.md:- object grouping
docs/notes/helloworld-demo-strategy.md:# HelloWorld Demo Strategy
docs/notes/helloworld-demo-strategy.md:This document defines the HelloWorld demo strategy and phase structure.
docs/notes/helloworld-demo-strategy.md:## 9. HelloWorld Demo (Plan)
docs/notes/helloworld-demo-strategy.md:HelloWorld demo before entering CRUD / CML development.
docs/notes/helloworld-demo-strategy.md:- No-setting HelloWorld server startup
docs/notes/helloworld-demo-strategy.md:- First custom HelloWorld Component implementation
docs/notes/helloworld-demo-strategy.md:- Subsystem is the stable execution boundary.
docs/notes/helloworld-demo-strategy.md:#### Stage 1: No-Setting HelloWorld Server
docs/notes/helloworld-demo-strategy.md:- Default Subsystem instantiation
docs/notes/helloworld-demo-strategy.md:- HTTP server delegation to `Subsystem.executeHttp`
docs/notes/helloworld-demo-strategy.md:  - observe_info on Subsystem startup
docs/notes/helloworld-demo-strategy.md:#### Stage 2: Executable HelloWorld API
docs/notes/helloworld-demo-strategy.md:- `ping` operation returning 200 OK
docs/notes/helloworld-demo-strategy.md:- Read-only OpenAPI generation from Subsystem model
docs/notes/helloworld-demo-strategy.md:- Remote Subsystem invocation
docs/notes/helloworld-demo-strategy.md:- Local Subsystem execution as a command
docs/notes/helloworld-demo-strategy.md:- Batch / admin use cases
docs/notes/helloworld-demo-strategy.md:#### Stage 5: First Custom HelloWorld Component Extension
docs/notes/helloworld-demo-strategy.md:- Implement a user-defined custom HelloWorld Component
docs/notes/helloworld-demo-strategy.md:1. Stage 1: No-Setting HelloWorld Server
docs/notes/helloworld-demo-strategy.md:2. Stage 2: Executable HelloWorld API
docs/notes/helloworld-demo-strategy.md:5. Stage 5: First Custom HelloWorld Component Extension
docs/notes/helloworld-demo-strategy.md:The HelloWorld demo is considered complete only after all six stages are finished.
docs/notes/helloworld-demo-strategy.md:- Subsystem execution boundary
docs/design/configuration-model.md:scope = system-dsl / config-dsl / model / platform-compilation
docs/design/configuration-model.md:In CNCF, systems are composed from Components and Subsystems,
docs/design/configuration-model.md:- The **primary deployment and execution unit is `Subsystem`**
docs/design/configuration-model.md:- Configuration resolution, lifecycle, observability, and scaling are all scoped to a Subsystem
docs/design/configuration-model.md:> Therefore, configuration modeling starts from **Subsystem**, not Application.
docs/design/configuration-model.md:Application may later be defined as a **composition of Subsystems**,
docs/design/configuration-model.md:Subsystem.Config
docs/design/configuration-model.md:- Independent of Subsystem count
docs/design/configuration-model.md:#### Subsystem.Config
docs/design/configuration-model.md:**Owner**: `Subsystem`
docs/design/configuration-model.md:- How the subsystem is run
docs/design/configuration-model.md:- Subsystem-level capabilities
docs/design/configuration-model.md:- Lives as `Subsystem.Config`
docs/design/configuration-model.md:- Subsystem is the **root of semantic configuration**
docs/design/configuration-model.md:- Defaults inherited from Subsystem behaviorally (not structurally)
docs/design/configuration-model.md:Subsystem.Config.from(conf: ResolvedConfiguration): Consequence[Subsystem.Config]
docs/design/configuration-model.md:- Subsystem is the semantic root
docs/design/configuration-model.md:Core Config is not intended to describe system architecture.
docs/design/configuration-model.md:used to describe and constrain system and subsystem architecture.
docs/design/configuration-model.md:### 2.4 Subsystem as the Compilation Unit
docs/design/configuration-model.md:The **Subsystem** is the minimal unit for:
docs/design/configuration-model.md:### 3.1 System / Subsystem DSL (Logical)
docs/design/configuration-model.md:system EcommerceSystem {
docs/design/configuration-model.md:  subsystem OrderDomain {
docs/design/configuration-model.md:subsystems.order-domain {
docs/design/configuration-model.md:config.getString("subsystems.order.capabilities.datastore.type")
docs/design/configuration-model.md: └─ SubsystemModel
docs/design/configuration-model.md:### 5.2 Subsystem Tier and Kind
docs/design/configuration-model.md:Each subsystem is defined by two orthogonal axes:
docs/design/configuration-model.md:required by a subsystem.
docs/design/configuration-model.md:### 9.1 Subsystem → Platform Mapping
docs/design/configuration-model.md:This mapping is deterministic and explicit.
docs/design/configuration-model.md:For each SubsystemModel:
docs/design/canonical-alias-suffix-resolution.md:Special rule for minimal systems:
docs/design/free-unitofwork-execution-model.md:Without Free, systems tend to suffer from:
docs/design/free-unitofwork-execution-model.md:- systems that cannot explain their own behavior
docs/design/free-unitofwork-execution-model.md:Such systems are difficult to evolve and unsafe for AI assistance.
src/main/scala/org/goldenport/cncf/component/Component.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/component/Component.scala://  private var _system_context: SystemContext = SystemContext.empty
src/main/scala/org/goldenport/cncf/component/Component.scala:  private var _subsystem: Option[Subsystem] = None
src/main/scala/org/goldenport/cncf/component/Component.scala:    _subsystem = Some(params.subsystem)
src/main/scala/org/goldenport/cncf/component/Component.scala:  def subsystem: Option[Subsystem] = _subsystem
src/main/scala/org/goldenport/cncf/component/Component.scala://  def systemContext: SystemContext = _system_context
src/main/scala/org/goldenport/cncf/component/Component.scala:  //   _system_context = sc
src/main/scala/org/goldenport/cncf/component/Component.scala:      params.subsystem.httpDriver.foreach { driver =>
src/main/scala/org/goldenport/cncf/component/Component.scala:        comp.initialize(ComponentInit(params.subsystem, core, params.origin))
src/main/scala/org/goldenport/cncf/component/Component.scala:  subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/component/Component.scala:    ComponentInit(subsystem, core, origin)
src/main/scala/org/goldenport/cncf/component/Component.scala:  subsystem: Subsystem,
docs/notes/cml-crud-domain-subsystem-bootstrap.md:# CML → CRUD Domain Subsystem Bootstrap (Minimal Pipeline)
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- Bootstrap a CRUD-scoped Domain Subsystem
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- The focus is on bootstrapping a Domain Tier Subsystem as fast as possible
docs/notes/cml-crud-domain-subsystem-bootstrap.md:This document assumes that the HelloWorld Bootstrap
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- A default Subsystem boots successfully
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- Bootstrapping CRUD projections
docs/notes/cml-crud-domain-subsystem-bootstrap.md:startup, lifecycle, admin, or web infrastructure.
docs/notes/cml-crud-domain-subsystem-bootstrap.md:See: docs/notes/helloworld-bootstrap.md
docs/notes/cml-crud-domain-subsystem-bootstrap.md:1) A Domain Subsystem can be started from a single CML file
docs/notes/cml-crud-domain-subsystem-bootstrap.md:2) The subsystem successfully boots and logs startup completion
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- "[INFO] OrderDomainSubsystem started"
docs/notes/cml-crud-domain-subsystem-bootstrap.md:SubsystemModel (tier=domain, kind=service, components/capabilities)
docs/notes/cml-crud-domain-subsystem-bootstrap.md:3. SubsystemModel Generation Rules (Minimal)
docs/notes/cml-crud-domain-subsystem-bootstrap.md:A Domain Subsystem is mechanically generated from the CML DomainModel.
docs/notes/cml-crud-domain-subsystem-bootstrap.md:Default SubsystemModel:
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- A handwritten Subsystem DSL is NOT required initially
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- SubsystemModel must be kept independent so it can later be merged
docs/notes/cml-crud-domain-subsystem-bootstrap.md:  with manually written subsystem definitions
docs/notes/cml-crud-domain-subsystem-bootstrap.md:Example HTTP mapping:
docs/notes/cml-crud-domain-subsystem-bootstrap.md:- "A Domain Subsystem that boots and supports CRUD from CML only"
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:      val subsystem = params.subsystem
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:      val exportService = new DefaultExportSpecificationService(subsystem)
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:  subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:      case "openapi" => OpenApiProjector.forSubsystem(subsystem)
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala: * ComponentLogic owns the system-scoped context and produces action-scoped ExecutionContext.
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:  // system: SystemContext = SystemContext.empty
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:    _ping_action_(request).getOrElse(component.protocolLogic.makeOperationRequest(request))
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:    val base = ExecutionContext.create() // createWithSystem(component.systemContext)
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:      .orElse(component.subsystem.flatMap(_.httpDriver))
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:  private def _ping_action_(
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:    val isping =
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:      request.service.contains("admin.system") && request.operation == "ping"
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala:    if (isping) {
src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala://    def name = "ping"
docs/design/async-model.md:    - failure must not corrupt system state
docs/design/async-model.md:in asynchronous systems.
docs/design/async-model.md:The asynchronous model exists to protect the system
docs/notes/phase-2.6-demo-done-checklist.md:- Phase 2.6 completes remaining demo stages defined in `docs/notes/helloworld-demo-strategy.md`
docs/notes/phase-2.6-demo-done-checklist.md:via Subsystem.executeHttp.
docs/notes/phase-2.6-demo-done-checklist.md:  - command admin system ping
docs/notes/phase-2.6-demo-done-checklist.md:  - command admin.system.ping
docs/notes/phase-2.6-demo-done-checklist.md:    - subsystem: cncf
docs/notes/phase-2.6-demo-done-checklist.md:$ sbt 'run client admin system ping --no-exit'
docs/notes/phase-2.6-demo-done-checklist.md:=> subsystem: cncf
docs/notes/phase-2.6-demo-done-checklist.md:$ sbt -Dcncf.http.driver=fake 'run client admin system ping --no-exit'
docs/notes/phase-2.6-demo-done-checklist.md:=> subsystem: cncf
docs/notes/phase-2.6-demo-done-checklist.md:  - sbt 'run client admin system ping --no-exit'
docs/notes/phase-2.6-demo-done-checklist.md:  - sbt -Dcncf.http.driver=fake 'run client admin system ping --no-exit'
docs/notes/phase-2.6-demo-done-checklist.md:    - subsystem: cncf
docs/notes/phase-2.6-demo-done-checklist.md:  - Client: sbt 'run client admin system ping --no-exit'
docs/notes/phase-2.6-demo-done-checklist.md:  - CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command admin component list --component-repository=scala-cli
docs/notes/phase-2.6-demo-done-checklist.md:- [x] Evidence (verified): demo component appears in admin component list
docs/notes/phase-2.6-demo-done-checklist.md:- Use `ping` to confirm end-to-end connectivity.
docs/notes/phase-2.6-demo-done-checklist.md:- `client admin system ping` returns `ok`.
docs/notes/phase-2.6-demo-done-checklist.md:$ docker run --rm goldenport-cncf command admin system ping
docs/notes/phase-2.6-demo-done-checklist.md:  CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command admin component list --component-repository=scala-cli
docs/notes/phase-2.6-demo-done-checklist.md:- admin component list includes demo component
docs/notes/phase-2.6-demo-done-checklist.md:- [x] Docker command: admin system ping
docs/notes/phase-2.6-demo-done-checklist.md:        - runtime / subsystem names and versions are correct
docs/notes/phase-2.6-demo-done-checklist.md:- [x] HTTP GET /admin/system/ping
docs/notes/phase-2.6-demo-done-checklist.md:        - runtime / subsystem names and versions are correct
docs/notes/phase-2.6-demo-done-checklist.md:- [x] Client ping via real HTTP
docs/notes/phase-2.6-demo-done-checklist.md:- [x] Client ping via fake HTTP
docs/notes/phase-2.6-demo-done-checklist.md:    - ScriptExecutionComponent is visible via `admin component list`.
docs/notes/phase-2.6-demo-done-checklist.md:- [x] ping structured output via suffix (e.g. ping.json, ping.yaml)
docs/notes/phase-2.6-demo-done-checklist.md:         - Tagging and grouping policy
docs/notes/phase-2.6-demo-done-checklist.md:- docs/notes/helloworld-demo-strategy.md
docs/notes/phase-2.6-demo-done-checklist.md:- docs/notes/helloworld-bootstrap.md
docs/design/id.md:This document defines the canonical ID design for this system.
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:package org.goldenport.cncf.component.builtin.admin
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:  val name: String = "admin"
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:        params.subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:        params.subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:        params.subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:        params.subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:        name = "system",
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:        name = "ping",
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      Consequence.success(ComponentListAction(req, subsystem))
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      Consequence.success(VariationListAction(req, subsystem))
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      Consequence.success(ExtensionListAction(req, subsystem))
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      Consequence.success(ConfigShowAction(req, subsystem))
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      ComponentListActionCall(core, subsystem)
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      val comps = subsystem.components
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      ConfigShowActionCall(core, subsystem)
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      VariationListActionCall(core, subsystem)
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      ExtensionListActionCall(core, subsystem)
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:    subsystem: Subsystem
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      val comps = subsystem.components
docs/design/execution-context.md:- SystemContext (system-scoped runtime assumptions)
docs/design/execution-context.md:## Admin system ping (runtime introspection)
docs/design/execution-context.md:The `admin.system.ping` operation provides minimal runtime
docs/design/execution-context.md:- subsystem: <subsystem name>
docs/design/execution-context.md:- subsystem.version: <subsystem version>
docs/design/execution-context.md:- Structured output via suffix (e.g. `ping.json`) is intentionally
docs/design/execution-context.md:- Security policy definitions live in SystemContext as system-scoped policy handles.
docs/design/path-alias.md:Failure at any validation step halts subsystem creation with a clear `IllegalArgumentException` message, so misconfigured aliases never reach runtime.
docs/design/path-alias.md:`PathPreNormalizer.rewriteSelector` and `rewriteSegments` call `AliasResolver.resolve` before the resolver core sees the selector; this keeps alias handling centralized and consistent across CLI, script, and HTTP surfaces. Alias matching is case-insensitive and treats inputs as trimmed tokens (`_alias_normalization._normalize_input`), so `"Ping"`, `"PING"`, and `" ping "` all map to the same alias.
src/main/scala/org/goldenport/cncf/component/repository/ComponentRepository.scala:      ComponentProvider.provide(source, params.subsystem, params.origin) match {
docs/journal/2026/01/phase-2.8-suffix-egress-design.md:    - admin.system.ping → json
docs/notes/phase-2.9-error-realignment.md:- Stable error categories and mapping rules
src/main/scala/org/goldenport/cncf/component/repository/ComponentProvider.scala:import org.goldenport.cncf.subsystem.Subsystem
src/main/scala/org/goldenport/cncf/component/repository/ComponentProvider.scala:    subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/component/repository/ComponentProvider.scala:        _provide_class(componentClass, subsystem, origin, BootstrapLog.stderr)
src/main/scala/org/goldenport/cncf/component/repository/ComponentProvider.scala:    subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/component/repository/ComponentProvider.scala:        _initialize_component(comp, subsystem, core, origin)
src/main/scala/org/goldenport/cncf/component/repository/ComponentProvider.scala:    subsystem: Subsystem,
src/main/scala/org/goldenport/cncf/component/repository/ComponentProvider.scala:      comp.initialize(ComponentInit(subsystem, core, origin))
docs/design/cncf-component.md:- Emit domain and system events  
docs/design/cncf-component.md:## 5. Mapping to UML
docs/design/cncf-component.md:- AI agents (ChatGPT / MCP) can reason about the system reliably  
docs/journal/2026/01/context-rearchitecture-freeze.md:         * Provides hierarchical lookup (runtime → subsystem → component).
docs/journal/2026/01/context-rearchitecture-freeze.md:         * Represents logical scope (runtime / subsystem / component / operation).
docs/journal/2026/01/context-rearchitecture-freeze.md:       - Runtime/system metadata stored in ad-hoc config maps without an owning context.
docs/journal/2026/01/context-rearchitecture-freeze.md:       - componentHttpDriver / subsystemHttpDriver fields on ExecutionContext.
docs/journal/2026/01/context-rearchitecture-freeze.md:- RuntimeMetadata: removed in favor of GlobalRuntimeContext/ping via the runtime chain (closed 2026-01-18)
docs/journal/2026/01/context-rearchitecture-freeze.md:- Subsystem 初期化フェーズの整理（OPEN）
docs/design/component-repository.md:- Components are integrated before Subsystem construction
docs/journal/2026/01/runtime-context-consolidation.md:   4.2 Subsystem initialization phase hygiene (OPEN)
docs/journal/2026/01/runtime-context-consolidation.md:- runtime / subsystem identification
docs/journal/2026/01/runtime-context-consolidation.md:- ping / introspection output
docs/journal/2026/01/runtime-context-consolidation.md:- formats strings for ping/introspection
docs/journal/2026/01/runtime-context-consolidation.md:It does not participate in execution, scoping, or lifecycle.
docs/journal/2026/01/runtime-context-consolidation.md:- subsystem name
docs/journal/2026/01/runtime-context-consolidation.md:- subsystem version
docs/journal/2026/01/runtime-context-consolidation.md:- ping formatting
docs/journal/2026/01/runtime-context-consolidation.md:- subsystem identity
docs/journal/2026/01/runtime-context-consolidation.md:- subsystem version
docs/journal/2026/01/runtime-context-consolidation.md:- ping / introspection output
docs/journal/2026/01/runtime-context-consolidation.md:- GlobalRuntimeContext: authoritative for runtime identity, mode, versions, httpDriver, and ping formatting
docs/journal/2026/01/runtime-context-consolidation.md:- The value returned by `admin.system.ping` reflects
docs/journal/2026/01/runtime-context-consolidation.md:- No change to subsystem/component boundaries
docs/journal/2026/01/phase-2.8-work-a1-path-alias.md:- Several strings such as `admin`, `system`, `default`
docs/design/component-and-application-responsibilities.md:- EntityStore (record ↔ entity mapping)
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:### HTTP surface (`src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`)
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:- `_resolve_spec_route` has its own ad-hoc mapping for `spec`/`openapi` paths, further splitting handling from the canonical path resolver.
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:Introduce `org.goldenport.cncf.subsystem.resolver.OperationResolver` (or similar) as a collaborator owned by `Subsystem`. The subsystem already owns `ComponentSpace` and is the entry point for CLI, script, and HTTP surfaces, so it should create and expose the resolver when components are added. The resolver should rebuild its resolution table whenever `Subsystem.add` changes the component set so that the table is always authoritative.
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:- `selector` is the raw first non-option argument (no suffix stripping). `allowPrefix` controls whether prefix matches are considered (must still follow "unique candidate" rule). `allowImplicit` enables the minimal-system default; selectors containing dots must pass `allowImplicit = false` (and the resolver should treat any dot as explicit intent).
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:- `Subsystem` will hold the resolver instance and expose helpers such as `resolve(selector, allowPrefix, allowImplicit)` for surfaces.
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:- **CLI** (`CncfRuntime`): after parsing options, hand the selector string to `subsystem.resolve`. If the resolver returns `Resolved(fqn)`, split the FQN and build the `Request` (component/service/operation). If `NotFound`/`Ambiguous`/`InvalidSelector`, surface the failure (with stage specificity and candidate list) instead of running `Request.parseArgs`. Suffix parsing might happen before calling the resolver (Phase 2.8 is only about the name portion). CLI should also respect `allowImplicit` when no selector is provided (empty args) but the implicit rule applies.
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:- **Script** (`ScriptRuntime`): convert script args into a selector (possibly empty) and call the resolver with `allowImplicit = true` when the minimal-system rule can kick in; otherwise require explicit selectors. This global resolver removes the need for `_to_request_script` to prepend `SCRIPT DEFAULT RUN` and instead allows a single helper to inspect the `ResolutionResult`.
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:- **HTTP** (`Subsystem.executeHttp`): derive a selector string from the path (concatenating the three segments) and call the resolver with `allowPrefix = false` (HTTP should require explicit names, unless a future flag allows prefix matching). Use the resolver result to look up the exact `Component/Service/Operation` objects. Existing `_resolve_spec_route` can remain for the special `spec` paths, but `OperationResolver` should also include `spec.exports.openapi` entries so that these flows can reuse the same table if desired.
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:1. One builtin component (e.g. `builtin.admin`) to ensure implicit rule excludes it.
docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md:- **Component set changes**: Subsystem allows extra components to be added after construction (`add`). The resolver must be recomputed when new components arrive; failure to rebuild would yield stale tables. Consider making `OperationResolver` immutable and replacing it whenever `add` runs.
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- alias discovery is systematic and complete,
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- Required for correct system behavior.
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:  - Notes: alias is hard-coded in the CLI layer; should be surfaced via configuration or declarative mapping for Phase 2.8.
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (method `_resolve_spec_route`)
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (method `_resolve_spec_route`)
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (method `_resolve_spec_route`)
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- Location: `org/goldenport/cncf/subsystem/SubsystemFactory.scala` (method `_resolve_http_driver`)
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:  - Observed behavior: when `cncf.http.driver` is missing or unspecified via system properties, the factory defaults to the literal `"real"`, creating a `UrlConnectionHttpDriver` even though no configuration key supplies that value.
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- Location: `org/goldenport/cncf/subsystem/SubsystemFactory.scala` (method `defaultWithScope`)
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:  - Observed behavior: subsystem creation defaults the runtime mode label to `"command"` when no mode argument is provided, so `PingRuntime.systemContext` and related context metadata embed that literal without configuration.
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (object `Config`, method `from`)
docs/journal/2026/01/phase-2.8-work-a1-alias-inventory-plan.md:  - Observed behavior: when the `cncf.subsystem.mode` configuration entry is absent, the resolver injects the literal `"normal"` before converting it to a `RunMode`, effectively aliasing missing user input to a concrete mode.

## 2. Built-in Component Wiring

src/main/scala/org/goldenport/cncf/resolver/PathResolution.scala:    } else if (isBuiltin(component, builtinComponents)) {
src/main/scala/org/goldenport/cncf/resolver/PathResolution.scala:  private def isBuiltin(
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        e.origin != ComponentOrigin.Builtin &&
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    val non_builtin_entries = entries.filter(_.origin != ComponentOrigin.Builtin)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    val nonBuiltinEntries = entries.filter(_.origin != ComponentOrigin.Builtin)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    if (nonBuiltinEntries.isEmpty) return None
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    val componentGroup = nonBuiltinEntries.groupBy(_.component.canonical)
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:import org.goldenport.cncf.component.builtin.admin.AdminComponent
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:import org.goldenport.cncf.component.builtin.client.ClientComponent
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:import org.goldenport.cncf.component.builtin.specification.SpecificationComponent
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  private val _admin = AdminComponent.Factory
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  private val _client = ClientComponent.Factory
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:  private val _spec = SpecificationComponent.Factory()
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
src/main/scala/org/goldenport/cncf/CncfMain.scala:        val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
src/main/scala/org/goldenport/cncf/CncfMain.scala:      val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:import org.goldenport.cncf.component.builtin.client.ClientComponent
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:  ): Consequence[ClientComponent] =
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    subsystem.components.collectFirst { case c: ClientComponent => c } match {
src/main/scala/org/goldenport/cncf/component/builtin/client/ClientComponent.scala:package org.goldenport.cncf.component.builtin.client
src/main/scala/org/goldenport/cncf/component/builtin/client/ClientComponent.scala:final class ClientComponent() extends Component {
src/main/scala/org/goldenport/cncf/component/builtin/client/ClientComponent.scala:object ClientComponent {
src/main/scala/org/goldenport/cncf/component/builtin/client/ClientComponent.scala:      ClientComponent()
src/main/scala/org/goldenport/cncf/component/Component.scala:  case object Builtin extends ComponentOrigin {
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:package org.goldenport.cncf.component.builtin.specification
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:final class SpecificationComponent() extends Component {
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:object SpecificationComponent {
src/main/scala/org/goldenport/cncf/component/builtin/specification/SpecificationComponent.scala:      Vector(SpecificationComponent())
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:package org.goldenport.cncf.component.builtin.admin
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:class AdminComponent() extends Component {
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:object AdminComponent {
src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:      Vector(AdminComponent())

## 3. Resolver / Selector Special-Casing

src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:  //     operation = "RUN",
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:      //   "DEFAULT",
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:      //   "RUN",
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:          "DEFAULT",
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:            "RUN",
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        ResolutionResult.NotFound(ResolutionStage.Operation, trimmed)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case nf: ResolutionResult.NotFound if config.mode == Mode.OneOperation =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:          case nf: ResolutionResult.NotFound =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        result.headOption.map(_to_resolved).getOrElse(ResolutionResult.NotFound(ResolutionStage.Operation, selector))
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case MatchOutcome.Ambiguous(result) =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        ResolutionResult.Ambiguous(selector, _candidate_fqns(result))
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case MatchOutcome.NotFound =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        ResolutionResult.NotFound(ResolutionStage.Operation, selector)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:          case MatchOutcome.Ambiguous(operation) => ResolutionResult.Ambiguous(operationSelector, _candidate_fqns(operation))
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:          case MatchOutcome.NotFound => ResolutionResult.NotFound(ResolutionStage.Operation, operationSelector)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case MatchOutcome.Ambiguous(result) =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        ResolutionResult.Ambiguous(serviceSelector, _candidate_fqns(result.flatMap(_.operations)))
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case MatchOutcome.NotFound =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        ResolutionResult.NotFound(ResolutionStage.Service, serviceSelector)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:              case MatchOutcome.Ambiguous(operation) => ResolutionResult.Ambiguous(operationSelector, _candidate_fqns(operation))
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:              case MatchOutcome.NotFound => ResolutionResult.NotFound(ResolutionStage.Operation, operationSelector)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:          case MatchOutcome.Ambiguous(service) =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:            ResolutionResult.Ambiguous(serviceSelector, _candidate_fqns(service.flatMap(_.operations)))
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:          case MatchOutcome.NotFound =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:            ResolutionResult.NotFound(ResolutionStage.Component, componentSelector)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case MatchOutcome.Ambiguous(result) =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        ResolutionResult.Ambiguous(componentSelector, _candidate_fqns(result.flatMap(_.operations)))
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case MatchOutcome.NotFound =>
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:        ResolutionResult.NotFound(ResolutionStage.Component, componentSelector)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      return ResolutionResult.NotFound(ResolutionStage.Operation, trimmed)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case _ => ResolutionResult.NotFound(ResolutionStage.Operation, trimmed)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      if (exact.size > 1) return MatchOutcome.Ambiguous(exact)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      return MatchOutcome.NotFound
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case 0 => MatchOutcome.NotFound
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:      case _ => MatchOutcome.Ambiguous(combined)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    final case class Ambiguous[T](entries: Vector[T]) extends MatchOutcome[T]
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    case object NotFound extends MatchOutcome[Nothing]
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    final case class NotFound(stage: ResolutionStage, input: String) extends ResolutionResult
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    final case class Ambiguous(input: String, candidates: Vector[String]) extends ResolutionResult
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:          case (Some("SCRIPT"), Some("DEFAULT"), Some("RUN")) =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:            val xs = Vector("SCRIPT", "DEFAULT", "RUN") ++ in
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        case ResolutionResult.NotFound(stage, input) =>
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        case ResolutionResult.Ambiguous(input, candidates) =>
src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala:      .withHttpApp(routes.orNotFound)
src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala:      case 404 => HStatus.NotFound
src/main/scala/org/goldenport/cncf/component/Component.scala:  //   s"SCRIPT${_script_number()}"
src/main/scala/org/goldenport/cncf/component/Component.scala:    val name = "SCRIPT" // _create_script_component_name()
src/main/scala/org/goldenport/cncf/http/HttpDriver.scala:    case 404 => HttpStatus.NotFound
src/main/scala/org/goldenport/cncf/component/repository/ComponentFactory.scala:      case e: ClassNotFoundException => _failure(e)

## 4. CLI / Runtime Entry Points

src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:import org.goldenport.cncf.cli.CncfRuntime
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:object ScriptRuntime {
src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala:    CncfRuntime.executeScript(args, _build_components(script))
src/main/scala/org/goldenport/cncf/dsl/script/kernel.scala:  ScriptRuntime.run(args)(body)
src/main/scala/org/goldenport/cncf/dsl/script/kernel.scala:  ScriptRuntime.run(args)(body)
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:import OperationResolver._
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:final class OperationResolver private (
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:object OperationResolver {
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:  def empty: OperationResolver =
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    new OperationResolver(
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:  def build(components: Seq[Component]): OperationResolver = {
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:  ): OperationResolver = {
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:  ): OperationResolver = {
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:  private def _build_from_entries(entries: Vector[OperationEntry]): OperationResolver = {
src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:    new OperationResolver(
src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala:object DefaultSubsystemFactory {
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  private var _resolver: OperationResolver = OperationResolver.empty
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  def resolver: OperationResolver = _resolver
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:  def operationResolver: OperationResolver = _resolver
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:    _resolver = OperationResolver.build(_component_space.components)
src/main/scala/org/goldenport/cncf/CncfMain.scala:import org.goldenport.cncf.cli.CncfRuntime
src/main/scala/org/goldenport/cncf/CncfMain.scala:            CncfRuntime.runWithExtraComponents(rest, extras)
src/main/scala/org/goldenport/cncf/CncfMain.scala:            CncfRuntime.runWithExtraComponents(rest, extras)
src/main/scala/org/goldenport/cncf/CncfMain.scala:        CncfRuntime.runExitCode(rest)
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:object CncfRuntime {
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:        name = DefaultSubsystemFactory.subsystemName,
src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:    val subsystem = DefaultSubsystemFactory.defaultWithScope(
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:  import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
src/main/scala/org/goldenport/cncf/http/HttpExecutionEngine.scala:      DefaultSubsystemFactory.default()

## 5. Classification Checklist (to be filled manually)

For each hit above, classify as one of:

- [ ] Legitimate builtin behavior (infrastructure responsibility)
- [ ] Demo-only shortcut (candidate for removal or isolation)
- [ ] Accidental hard-coding (bug / hygiene violation)

Also record:
- Whether behavior is allowed in Phase 2.8
- Whether it must move to Next Phase Development Items

Audit generated on: Tue Jan 20 10:49:52 JST 2026
