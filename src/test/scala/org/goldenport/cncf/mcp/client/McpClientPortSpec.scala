package org.goldenport.cncf.mcp.client

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ExtensionPoint, Port, ServiceContract, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
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
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpClientPortSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "MCP client Port" should {
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

  private final class _FakeTransport(
    catalogs: Map[McpServerId, Vector[McpClientTool]],
    blocking: Option[(CountDownLatch, CountDownLatch)] = None
  ) extends McpClientTransport {
    private val _events = ArrayBuffer.empty[String]

    def events: Vector[String] = synchronized {
      _events.toVector
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
      blocking.foreach { case (entered, release) =>
        entered.countDown()
        release.await(5L, TimeUnit.SECONDS)
      }
      val query = call.arguments.fields.collectFirst {
        case (name, McpValue.StringValue(value)) if name.print == "query" => value
      }.getOrElse("")
      Consequence.success(McpClientResult(
        Vector.empty,
        Some(McpValue.StringValue(s"found:${query}"))
      ))
    }

    private def _record_event(event: String): Unit = synchronized {
      _events += event
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
