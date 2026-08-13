package org.goldenport.cncf.mcp.client

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ExtensionPoint, Port, ServiceContract, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.mcp.McpToolCatalog
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.observation.calltree.{CallTree, CallTreeNode}
import org.goldenport.tree.{TreeDir, TreeLeaf, TreeNode}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for runtime-owned MCP client Port wiring.
 *
 * @since   Jul. 21, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpClientPortSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "MCP client Port" should {
    "install a consumer socket atomically from logical server-set requirements" in {
      Given("a component declaring two logical MCP server sets without transport configuration")
      given ExecutionContext = ExecutionContext.create()
      val alpha = _server_set("alpha", "alpha-server")
      val beta = _server_set("beta", "beta-server")
      val socket = McpClientSocket.createC(Vector(
        McpClientRequirement(beta.id),
        McpClientRequirement(alpha.id)
      )).toOption.get
      val component = new Component() {}.withPort(Component.Port.input(socket))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(beta, alpha),
        _transport_binding(Map(
          alpha.id -> new _FakeTransport(Map(_server_id("alpha-server") -> Vector.empty)),
          beta.id -> new _FakeTransport(Map(_server_id("beta-server") -> Vector.empty))
        ))
      ).toOption.get

      When("runtime assembly installs the component input socket")
      val result = registry.install(component)

      Then("the socket exposes only both admitted services in normalized logical order")
      result.toOption shouldBe Some(component)
      socket.serverSetIds.map(_.print) shouldBe Vector("alpha", "beta")
      socket.isInstalled shouldBe true
      socket.service(alpha.id).toOption.map(_.serverSetId) shouldBe Some(alpha.id)
      socket.service(beta.id).toOption.map(_.serverSetId) shouldBe Some(beta.id)
    }

    "leave a consumer socket empty when one declared server set is unavailable" in {
      Given("a component whose complete MCP requirement set cannot be admitted")
      given ExecutionContext = ExecutionContext.create()
      val admitted = _server_set("admitted", "catalog")
      val missing = _server_set_id("missing")
      val socket = McpClientSocket.createC(Vector(
        McpClientRequirement(admitted.id),
        McpClientRequirement(missing)
      )).toOption.get
      val component = new Component() {}.withPort(Component.Port.input(socket))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(admitted),
        _transport_binding(
          new _FakeTransport(Map(_server_id("catalog") -> Vector.empty)),
          Set(admitted.id)
        )
      ).toOption.get

      When("runtime assembly attempts atomic socket installation")
      val result = registry.install(component)

      Then("installation fails and even the admitted service remains unavailable through the socket")
      result.isFaillure shouldBe true
      socket.isInstalled shouldBe false
      socket.service(admitted.id).isFaillure shouldBe true
      socket.service(missing).isFaillure shouldBe true
    }

    "install one server-set-bound service and invoke only its typed catalog" in {
      Given("a runtime server set and deterministic fake transport ExtensionPoint")
      given ExecutionContext = ExecutionContext.create()
      val serverset = _server_set("research", "catalog")
      val tool = _tool("catalog", "paper.search")
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get
      val component = new Component() {}

      When("the canonical Port binding installs the admitted MCP client service")
      val installed = registry.binding.install(component, McpClientRequirement(serverset.id))
      val service = installed.toOption.get.port.get[McpClientService].get
      val catalog = service.catalog
      val result = service.withInvocation(_.invoke(_call(tool.identity, "paper")))

      Then("the consumer sees typed catalog and result values without transport configuration")
      catalog.toOption.map(_.tools.map(_.identity.print)) shouldBe Some(Vector("catalog/paper.search"))
      result.toOption.flatMap(_.structuredContent) shouldBe Some(McpValue.StringValue("found:paper"))
      fake.events.toVector shouldBe Vector(
        "initialize:catalog",
        "list:catalog",
        "call:catalog/paper.search"
      )
    }

    "keep installed remote tool identities outside internal Operation publication" in {
      Given("an admitted remote tool whose name equals one builtin Operation identity")
      given ExecutionContext = ExecutionContext.create()
      val serverset = _server_set(
        "research",
        "catalog",
        Set(_tool_name("tool.time.now"))
      )
      val remotetool = _tool("catalog", "tool.time.now")
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(
          new _FakeTransport(Map(_server_id("catalog") -> Vector(remotetool))),
          Set(serverset.id)
        )
      ).toOption.get
      val socket = McpClientSocket.createC(Vector(McpClientRequirement(serverset.id))).toOption.get
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val consumer = TestComponentFactory
        .create("mcp_consumer", Protocol.empty, subsystem = subsystem)
        .withPort(Component.Port.input(socket))

      When("the runtime installs the remote catalog and the MCP server projector reads both component surfaces")
      val evidence = try {
        val installed = registry.install(consumer)
        val remotecatalog = socket.service(serverset.id).flatMap(_.catalog)
        val consumerpublication = McpToolCatalog.toolsForComponent(consumer)
        val internalpublication = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TOOL)
          .map(McpToolCatalog.toolsForComponent)
          .getOrElse(Vector.empty)
        (installed, remotecatalog, consumerpublication, internalpublication)
      } finally {
        registry.close()
        subsystem.shutdown()
      }

      Then("remote server/tool identity remains distinct and is never projected as a CNCF Operation")
      evidence._1.isSuccess shouldBe true
      evidence._2.toOption.map(_.tools.map(_.identity.print)) shouldBe
        Some(Vector("catalog/tool.time.now"))
      evidence._3 shouldBe Vector.empty
      evidence._4.map(_.name) should contain ("tool.time.now")
    }

    "reject infrastructure variation at the consumer Port boundary" in {
      Given("a valid runtime-owned registry and a caller-supplied provider selector")
      given ExecutionContext = ExecutionContext.create()
      val serverset = _server_set("research", "catalog")
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(new _FakeTransport(Map(_server_id("catalog") -> Vector.empty)), Set(serverset.id))
      ).toOption.get

      When("the caller tries to override transport selection")
      val result = registry.binding.bind(
        McpClientRequirement(serverset.id),
        VariationSelection(provider = Some("caller-http"))
      )

      Then("binding fails before any consumer service is installed")
      result.isFaillure shouldBe true
    }

    "reject an unbound server set before transport invocation" in {
      Given("a registry containing one differently named server set")
      given ExecutionContext = ExecutionContext.create()
      val admitted = _server_set("admitted", "catalog")
      val requested = _server_set_id("missing")
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector.empty))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(admitted),
        _transport_binding(fake, Set(admitted.id))
      ).toOption.get

      When("a component resolves a non-admitted server-set requirement")
      val result = registry.binding.bind(McpClientRequirement(requested))

      Then("the Port has no matching ExtensionPoint and transport stays untouched")
      result.isFaillure shouldBe true
      fake.events shouldBe empty
    }

    "reject a stale tool identity before callTool" in {
      Given("an admitted catalog and a call naming another tool")
      given ExecutionContext = ExecutionContext.create()
      val serverset = _server_set("research", "catalog")
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(_tool("catalog", "paper.search"))))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get
      val service = registry.resolve(serverset.id).toOption.get

      When("the typed call carries a stale non-catalog tool identity")
      val result = service.withInvocation(_.invoke(_call(
        McpToolIdentity(_server_id("catalog"), _tool_name("paper.delete")),
        "paper"
      )))

      Then("catalog discovery occurs but the fake call boundary is not crossed")
      result.isFaillure shouldBe true
      fake.events.toVector shouldBe Vector("initialize:catalog", "list:catalog")
    }

    "publish and invoke only tools named by the runtime allowlist" in {
      Given("one server reporting one admitted and one unlisted tool")
      given ExecutionContext = ExecutionContext.create()
      val admitted = _tool("catalog", "paper.search")
      val denied = _tool("catalog", "paper.delete")
      val serverset = _server_set("research", "catalog", Set(admitted.identity.toolName))
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(denied, admitted)))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get
      val service = registry.resolve(serverset.id).toOption.get

      When("the consumer discovers the catalog and attempts the unlisted identity")
      val catalog = service.catalog
      val result = service.withInvocation(_.invoke(_call(denied.identity, "paper")))

      Then("only the exact admitted identity is visible and denial occurs before callTool")
      catalog.toOption.map(_.tools.map(_.identity)) shouldBe Some(Vector(admitted.identity))
      result.isFaillure shouldBe true
      fake.events.toVector shouldBe Vector("initialize:catalog", "list:catalog")
    }

    "validate required declared input fields before callTool" in {
      Given("an admitted tool requiring a string query and rejecting additional fields")
      given ExecutionContext = ExecutionContext.create()
      val tool = _tool("catalog", "paper.search")
      val serverset = _server_set("research", "catalog", Set(tool.identity.toolName))
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get
      val service = registry.resolve(serverset.id).toOption.get
      val missing = McpClientCall.createC(
        tool.identity,
        McpValue.objectC(Vector.empty).toOption.get
      ).toOption.get
      val wrongtype = McpClientCall.createC(
        tool.identity,
        McpValue.objectC(Vector(_field_name("query") -> McpValue.IntegerValue(1))).toOption.get
      ).toOption.get
      val additional = McpClientCall.createC(
        tool.identity,
        McpValue.objectC(Vector(
          _field_name("query") -> McpValue.StringValue("paper"),
          _field_name("rawEndpoint") -> McpValue.StringValue("https://foreign.example")
        )).toOption.get
      ).toOption.get

      When("missing wrong-kind and additional inputs are invoked")
      val results = service.withInvocation { invocation =>
        Consequence.success(Vector(missing, wrongtype, additional).map(invocation.invoke))
      }.toOption.get

      Then("each fails structurally before the transport call boundary")
      results.forall(_.isFaillure) shouldBe true
      fake.events.toVector shouldBe Vector("initialize:catalog", "list:catalog")
    }

    "bound calls within one invocation and reset the budget for the next invocation" in {
      Given("one admitted tool and a one-call invocation limit")
      given ExecutionContext = ExecutionContext.create()
      val tool = _tool("catalog", "paper.search")
      val limits = McpClientLimits.createC(30000L, 1, 1024L, 1024L, 1).toOption.get
      val serverset = _server_set("research", "catalog", limits = limits)
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)))
      val service = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get.resolve(serverset.id).toOption.get

      When("two calls share one invocation and another call uses a fresh invocation")
      val within = service.withInvocation { invocation =>
        val first = invocation.invoke(_call(tool.identity, "first"))
        val second = invocation.invoke(_call(tool.identity, "second"))
        Consequence.success(first -> second)
      }.toOption.get
      val fresh = service.withInvocation(_.invoke(_call(tool.identity, "fresh")))

      Then("only the second shared call is rejected with structured limit diagnostics")
      within._1.isSuccess shouldBe true
      within._2.isFaillure shouldBe true
      _failure_kind(within._2) shouldBe Some(Cause.Kind.Limit)
      _failure_facets(within._2) should contain allOf (
        Descriptor.Facet.Reason("maximum-calls"),
        Descriptor.Facet.Policy("mcp-client.limits"),
        Descriptor.Facet.Limit(1L),
        Descriptor.Facet.Actual(2L)
      )
      fresh.isSuccess shouldBe true
      fake.events.count(_.startsWith("call:")) shouldBe 2
    }

    "reject concurrent calls beyond the invocation limit before transport execution" in {
      Given("one blocking transport call and a single-concurrency invocation")
      given context: ExecutionContext = ExecutionContext.create()
      val tool = _tool("catalog", "paper.search")
      val limits = McpClientLimits.createC(30000L, 4, 1024L, 1024L, 1).toOption.get
      val serverset = _server_set("research", "catalog", limits = limits)
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)), Some(entered -> release))
      val service = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get.resolve(serverset.id).toOption.get

      When("a second call is attempted while the first one owns the transport slot")
      val results = service.withInvocation { invocation =>
        val first = new AtomicReference[Consequence[McpClientResult]]()
        val worker = new Thread(() => first.set(invocation.invoke(_call(tool.identity, "first"))(using context)))
        worker.start()
        val enteredtransport = entered.await(5L, TimeUnit.SECONDS)
        val second = invocation.invoke(_call(tool.identity, "second"))
        release.countDown()
        worker.join(5000L)
        Consequence.success((first.get(), second, enteredtransport, worker.isAlive))
      }.toOption.get

      Then("the first call completes and the second receives concurrency diagnostics")
      results._3 shouldBe true
      results._4 shouldBe false
      results._1.isSuccess shouldBe true
      results._2.isFaillure shouldBe true
      _failure_kind(results._2) shouldBe Some(Cause.Kind.Limit)
      _failure_facets(results._2) should contain (Descriptor.Facet.Reason("maximum-concurrency"))
      fake.events.count(_.startsWith("call:")) shouldBe 1
    }

    "record payload-safe catalog and invocation observability in the caller context" in {
      Given("an admitted service, enabled caller CallTree, and payload text that must remain private")
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val tool = _tool("catalog", "paper.search")
      val serverset = _server_set("research", "catalog", Set(tool.identity.toolName))
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)))
      val service = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get.resolve(serverset.id).toOption.get
      val before = RuntimeDashboardMetrics.mcpClientInvocationSnapshot.summary.cumulative
      val privatepayload = "private-paper-query"

      When("the caller discovers, invokes, and attempts one non-admitted tool")
      val catalog = service.catalog
      val success = service.withInvocation(_.invoke(_call(tool.identity, privatepayload)))
      val denied = service.withInvocation(_.invoke(_call(
        McpToolIdentity(_server_id("catalog"), _tool_name("paper.delete")),
        privatepayload
      )))

      Then("logical identities and structured outcomes are visible without arguments, results, or transport data")
      catalog.isSuccess shouldBe true
      success.isSuccess shouldBe true
      denied.isFaillure shouldBe true
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing")).toRecord.print
      calltree should include ("mcp-client:catalog")
      calltree should include ("mcp-client:invoke")
      calltree should include ("calltree_kind=mcp-client")
      calltree should include ("server_set=research")
      calltree should include ("server=catalog")
      calltree should include ("tool=paper.search")
      calltree should include ("outcome=success")
      calltree should include ("outcome=failure")
      calltree should not include privatepayload
      calltree should not include "endpoint"
      calltree should not include "Authorization"
      val after = RuntimeDashboardMetrics.mcpClientInvocationSnapshot.summary.cumulative
      after.total shouldBe before.total + 3L
      after.errors shouldBe before.errors + 1L
      val points = RuntimeDashboardMetrics.runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared).points
      points.exists(point =>
        point.scope == "mcp-client.invocation" &&
          point.labels.get("operation").contains("invoke") &&
          point.labels.get("server_set").contains("research") &&
          point.labels.get("server").contains("catalog") &&
          point.labels.get("tool").contains("paper.search") &&
          !point.labels.values.exists(_.contains(privatepayload))
      ) shouldBe true
      RuntimeDashboardMetrics.mcpClientDiagnosticRecords.values.map(_.print).mkString(" ") should not include privatepayload
    }

    "record concurrent invocations without sharing open CallTree stack frames" in {
      Given("one caller CallTree and two admitted calls that overlap in the transport")
      given context: ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val tool = _tool("catalog", "paper.search")
      val limits = McpClientLimits.createC(30000L, 4, 1024L, 1024L, 2).toOption.get
      val serverset = _server_set("concurrent", "catalog", Set(tool.identity.toolName), limits)
      val entered = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)), Some(entered -> release))
      val service = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get.resolve(serverset.id).toOption.get
      service.catalog.isSuccess shouldBe true
      val before = RuntimeDashboardMetrics.mcpClientInvocationSnapshot.summary.cumulative.total

      When("both calls complete against the same invocation and caller observability context")
      val results = service.withInvocation { invocation =>
        val first = new AtomicReference[Consequence[McpClientResult]]()
        val second = new AtomicReference[Consequence[McpClientResult]]()
        val firstworker = new Thread(() => first.set(invocation.invoke(_call(tool.identity, "first"))(using context)))
        val secondworker = new Thread(() => second.set(invocation.invoke(_call(tool.identity, "second"))(using context)))
        firstworker.start()
        secondworker.start()
        val bothoverlapped = entered.await(5L, TimeUnit.SECONDS)
        release.countDown()
        firstworker.join(5000L)
        secondworker.join(5000L)
        Consequence.success(Vector(first.get(), second.get()) -> bothoverlapped)
      }.toOption.get

      Then("both outcomes become independent completed spans and leave no open MCP frame")
      results._2 shouldBe true
      results._1.forall(_.isSuccess) shouldBe true
      val calltreecontext = summon[ExecutionContext].observability.callTreeContext
      calltreecontext.currentLabels shouldBe empty
      val calltree = calltreecontext.build().getOrElse(fail("calltree missing"))
      _calltree_enter_count(calltree, "mcp-client:invoke") shouldBe 2
      calltree.toRecord.print should include ("duration_ms")
      RuntimeDashboardMetrics.mcpClientInvocationSnapshot.summary.cumulative.total shouldBe before + 2L
    }

    "stop admission, drain an interrupted call, and close its transport exactly once" in {
      Given("one admitted call blocked inside an interruption-aware transport")
      given context: ExecutionContext = ExecutionContext.create()
      val tool = _tool("catalog", "paper.search")
      val serverset = _server_set("lifecycle", "catalog", Set(tool.identity.toolName))
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)), Some(entered -> release))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get
      val service = registry.resolve(serverset.id).toOption.get
      service.catalog.isSuccess shouldBe true
      val result = new AtomicReference[Consequence[McpClientResult]]()
      val worker = new Thread(() => result.set(service.withInvocation(_.invoke(_call(tool.identity, "blocking")))(using context)))
      worker.start()
      entered.await(5L, TimeUnit.SECONDS) shouldBe true

      When("the registry closes while the tool call remains in flight")
      registry.close()
      registry.close()
      worker.join(5000L)

      Then("the call leaves the tracked set before one transport close and later admission is rejected")
      worker.isAlive shouldBe false
      result.get().isFaillure shouldBe true
      fake.events.takeRight(2) shouldBe Vector("call-interrupted:catalog/paper.search", "close")
      fake.closeCount shouldBe 1
      val bodyexecuted = new AtomicBoolean(false)
      val rejected = service.withInvocation { _ =>
        bodyexecuted.set(true)
        Consequence.unit
      }
      rejected.isFaillure shouldBe true
      bodyexecuted.get() shouldBe false
      _failure_facets(rejected) should contain (Descriptor.Facet.Policy("mcp-client.lifecycle"))
      registry.resolve(serverset.id).isFaillure shouldBe true
      fake.closeCount shouldBe 1
    }

    "close every server-set transport in normalized order despite an earlier cleanup failure" in {
      Given("two independently owned transports declared in reverse order with the first close failing")
      given ExecutionContext = ExecutionContext.create()
      val closeorder = ArrayBuffer.empty[String]
      val alpha = _server_set("alpha", "alpha-server")
      val zeta = _server_set("zeta", "zeta-server")
      val alphatransport = new _FakeTransport(
        Map(_server_id("alpha-server") -> Vector.empty),
        onclose = () => {
          closeorder.synchronized(closeorder += "alpha")
          throw new IllegalStateException("expected alpha cleanup failure")
        }
      )
      val zetatransport = new _FakeTransport(
        Map(_server_id("zeta-server") -> Vector.empty),
        onclose = () => closeorder.synchronized(closeorder += "zeta")
      )
      val registry = McpClientRuntimeRegistry.createC(
        Vector(zeta, alpha),
        _transport_binding(Map(alpha.id -> alphatransport, zeta.id -> zetatransport))
      ).toOption.get

      When("the runtime registry closes")
      val failure = intercept[IllegalStateException](registry.close())

      Then("the first failure is retained while every transport closes once in normalized order")
      failure.getMessage shouldBe "expected alpha cleanup failure"
      closeorder.toVector shouldBe Vector("alpha", "zeta")
      alphatransport.closeCount shouldBe 1
      zetatransport.closeCount shouldBe 1
    }

    "rollback acquired transports when registry assembly fails" in {
      Given("one acquired alpha transport followed by an unavailable zeta binding")
      given ExecutionContext = ExecutionContext.create()
      val alpha = _server_set("alpha", "alpha-server")
      val zeta = _server_set("zeta", "zeta-server")
      val alphatransport = new _FakeTransport(Map(_server_id("alpha-server") -> Vector.empty))

      When("registry assembly fails while binding the second normalized server set")
      val result = McpClientRuntimeRegistry.createC(
        Vector(zeta, alpha),
        _transport_binding(Map(alpha.id -> alphatransport))
      )

      Then("the registry failure is retained and the previously acquired transport is closed")
      result.isFaillure shouldBe true
      alphatransport.closeCount shouldBe 1
      alphatransport.events shouldBe Vector("close")
    }
  }

  private def _transport_binding(
    transport: McpClientTransport,
    admitted: Set[McpServerSetId]
  ): Component.Binding[McpClientTransportRequirement, McpClientTransport] =
    Component.Binding(Port(
      api = McpClientTransportPortApi,
      spi = Vector(new ExtensionPoint[McpClientTransport] {
        def supports(
          contract: ServiceContract[McpClientTransport],
          variation: VariationSelection
        )(using ExecutionContext): Boolean =
          variation == VariationSelection() &&
            McpClientTransportPortApi.serverSetId(contract).exists(admitted.contains)

        def provide(
          contract: ServiceContract[McpClientTransport],
          variation: VariationSelection
        )(using ExecutionContext): Consequence[McpClientTransport] =
          Consequence.success(transport)
      }),
      variation = McpClientTransportSelectionPoint
    ))

  private def _transport_binding(
    transports: Map[McpServerSetId, McpClientTransport]
  ): Component.Binding[McpClientTransportRequirement, McpClientTransport] =
    Component.Binding(Port(
      api = McpClientTransportPortApi,
      spi = Vector(new ExtensionPoint[McpClientTransport] {
        def supports(
          contract: ServiceContract[McpClientTransport],
          variation: VariationSelection
        )(using ExecutionContext): Boolean =
          variation == VariationSelection() &&
            McpClientTransportPortApi.serverSetId(contract).exists(transports.contains)

        def provide(
          contract: ServiceContract[McpClientTransport],
          variation: VariationSelection
        )(using ExecutionContext): Consequence[McpClientTransport] =
          McpClientTransportPortApi.serverSetId(contract).flatMap(transports.get) match {
            case Some(transport) => Consequence.success(transport)
            case None => Consequence.serviceUnavailable("MCP test transport is unavailable")
          }
      }),
      variation = McpClientTransportSelectionPoint
    ))

  private final class _FakeTransport(
    catalogs: Map[McpServerId, Vector[McpClientTool]],
    blocking: Option[(CountDownLatch, CountDownLatch)] = None,
    onclose: () => Unit = () => ()
  ) extends McpClientTransport {
    private val _events = ArrayBuffer.empty[String]
    private var _close_count = 0

    def events: Vector[String] = synchronized {
      _events.toVector
    }

    def closeCount: Int = synchronized {
      _close_count
    }

    def initialize(
      server: McpClientServer,
      limits: McpClientLimits
    )(using ExecutionContext): Consequence[Unit] = {
      _record_event(s"initialize:${server.id.print}")
      Consequence.unit
    }

    def listTools(
      server: McpClientServer,
      limits: McpClientLimits
    )(using ExecutionContext): Consequence[Vector[McpClientTool]] = {
      _record_event(s"list:${server.id.print}")
      Consequence.success(catalogs.getOrElse(server.id, Vector.empty))
    }

    def callTool(
      server: McpClientServer,
      call: McpClientCall,
      limits: McpClientLimits
    )(using ExecutionContext): Consequence[McpClientResult] = {
      _record_event(s"call:${call.toolIdentity.print}")
      val interrupted = blocking.flatMap { case (entered, release) =>
        entered.countDown()
        try {
          release.await(5L, TimeUnit.SECONDS)
          None
        }
        catch {
          case _: InterruptedException =>
            _record_event(s"call-interrupted:${call.toolIdentity.print}")
            Thread.currentThread().interrupt()
            Some(Consequence.serviceUnavailable(
              "MCP test transport call interrupted",
              Cause.Kind.Exhaustion,
              Vector(
                Descriptor.Facet.Reason("interrupted"),
                Descriptor.Facet.Policy("mcp-client.lifecycle")
              )
            ))
        }
      }
      interrupted.getOrElse {
        val query = call.arguments.fields.collectFirst {
          case (name, McpValue.StringValue(value)) if name.print == "query" => value
        }.getOrElse("")
        Consequence.success(McpClientResult(
          Vector.empty,
          Some(McpValue.StringValue(s"found:${query}"))
        ))
      }
    }

    private def _record_event(event: String): Unit = synchronized {
      _events += event
    }

    override def close(): Unit = synchronized {
      if (_close_count == 0) {
        _close_count += 1
        _events += "close"
        onclose()
      }
    }
  }

  private def _server_set(
    name: String,
    server: String,
    admittedtools: Set[McpToolName] = Set(_tool_name("paper.search")),
    limits: McpClientLimits = McpClientLimits.default
  ): McpClientServerSet =
    McpClientServerSet.createC(
      _server_set_id(name),
      Vector(McpClientServer.createC(_server_id(server), admittedtools).toOption.get),
      limits
    ).toOption.get

  private def _server_set_id(value: String): McpServerSetId =
    McpServerSetId.parseC(value).toOption.get

  private def _server_id(value: String): McpServerId =
    McpServerId.parseC(value).toOption.get

  private def _tool_name(value: String): McpToolName =
    McpToolName.parseC(value).toOption.get

  private def _field_name(value: String): McpFieldName =
    McpFieldName.parseC(value).toOption.get

  private def _tool(server: String, name: String): McpClientTool =
    McpClientTool.createC(
      McpToolIdentity(_server_id(server), _tool_name(name)),
      McpInputSchema.objectC(Vector(
        McpInputField.createC(_field_name("query"), McpInputSchema.StringValue, required = true).toOption.get
      )).toOption.get
    ).toOption.get

  private def _call(identity: McpToolIdentity, query: String): McpClientCall =
    McpClientCall.createC(
      identity,
      McpValue.objectC(Vector(
        _field_name("query") -> McpValue.StringValue(query)
      )).toOption.get
    ).toOption.get

  private def _failure_kind[A](result: Consequence[A]): Option[Cause.Kind] =
    result match {
      case Consequence.Failure(conclusion) => conclusion.observation.cause.kind
      case _ => fail("failure is required")
    }

  private def _failure_facets[A](result: Consequence[A]): Vector[Descriptor.Facet] =
    result match {
      case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets
      case _ => fail("failure is required")
    }

  private def _calltree_enter_count(calltree: CallTree, label: String): Int = {
    def _count(node: TreeNode[CallTreeNode]): Int =
      node match {
        case TreeLeaf(CallTreeNode.Enter(candidate, _), _) if candidate == label => 1
        case TreeLeaf(_, _) => 0
        case TreeDir(children) => children.map(entry => _count(entry.node)).sum
      }
    _count(calltree.tree.root)
  }
}
