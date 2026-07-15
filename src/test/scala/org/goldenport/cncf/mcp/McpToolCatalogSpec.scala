package org.goldenport.cncf.mcp

import cats.data.NonEmptyVector
import io.circe.parser.parse
import org.goldenport.cncf.component.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec
import org.goldenport.value.BaseContent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpToolCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "MCP subsystem tool catalog" should {
    "publish stable identities in deterministic order" in {
      Given("two primary components added in reverse lexical order")
      val subsystem = TestComponentFactory.emptySubsystem("mcp-tool-order")
      subsystem.add(Vector(
        _component(subsystem, "zeta-search", "default"),
        _component(subsystem, "alpha-search", "default")
      ))

      When("the shared subsystem catalog is projected")
      val catalog = McpToolCatalog.consequenceToolsForSubsystem(subsystem)

      Then("complete component service operation identities are sorted independently of SAR order")
      catalog.isSuccess shouldBe true
      catalog.toOption.map(_.map(_.name)) shouldBe Some(Vector(
        "alpha-search.Query.find",
        "zeta-search.Query.find"
      ))
    }

    "reject colliding identities without selecting a component instance" in {
      Given("two primary instances of one component publishing the same operation")
      val forward = _collision_subsystem(Vector("blue", "green"))
      val reverse = _collision_subsystem(Vector("green", "blue"))

      When("both SAR declaration orders are projected")
      val forwardcatalog = McpToolCatalog.consequenceToolsForSubsystem(forward)
      val reversecatalog = McpToolCatalog.consequenceToolsForSubsystem(reverse)

      Then("both catalogs fail with the same attributable identity conflict")
      forwardcatalog.isSuccess shouldBe false
      reversecatalog.isSuccess shouldBe false
      forwardcatalog.display should include ("duplicate MCP tool identities: shared-search.Query.find")
      reversecatalog.display shouldBe forwardcatalog.display
    }

    "fail JSON-RPC discovery and invocation closed while a collision exists" in {
      Given("a shared MCP route whose primary component instances collide")
      val subsystem = _collision_subsystem(Vector("blue", "green"))
      val adapter = new McpJsonRpcAdapter(subsystem)

      When("the catalog is listed and the colliding identity is called")
      val listed = _json(adapter.handle(
        """{"jsonrpc":"2.0","id":"list","method":"tools/list","params":{}}"""
      ))
      val called = _json(adapter.handle(
        """{"jsonrpc":"2.0","id":"call","method":"tools/call","params":{"name":"shared-search.Query.find","arguments":{}}}"""
      ))

      Then("neither request can observe a hidden winner")
      listed.hcursor.downField("error").get[Int]("code") shouldBe Right(-32603)
      called.hcursor.downField("error").get[Int]("code") shouldBe Right(-32603)
      listed.hcursor.downField("error").get[String]("message").toOption.getOrElse("") should include (
        "duplicate MCP tool identities: shared-search.Query.find"
      )
      called.hcursor.downField("error").get[String]("message") shouldBe
        listed.hcursor.downField("error").get[String]("message")
    }
  }

  private def _collision_subsystem(instances: Vector[String]): Subsystem = {
    val subsystem = TestComponentFactory.emptySubsystem("mcp-tool-collision")
    subsystem.add(instances.map(_component(subsystem, "shared-search", _)))
  }

  private def _component(
    subsystem: Subsystem,
    name: String,
    instance: String
  ): Component = {
    val operation = spec.OperationDefinition(
      content = BaseContent.simple("find"),
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )
    val service = spec.ServiceDefinition(
      name = "Query",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
    )
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
    val componentid = ComponentId(name.replace('-', '_'))
    val factory = new Component.SinglePrimaryBundleFactory {
      override protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      override protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          name,
          componentid,
          ComponentInstanceId.default(componentid),
          protocol,
          this
        )
    }
    factory.create(
      ComponentCreate(subsystem, ComponentOrigin.Main)
        .withInstanceMetadata(ComponentInstanceMetadata(name, instance))
    ).primary.withMcpReadyServices(Set("Query"))
  }

  private def _json(value: String) =
    parse(value).fold(
      error => fail(s"response is not valid JSON: ${error.getMessage}"),
      identity
    )
}
