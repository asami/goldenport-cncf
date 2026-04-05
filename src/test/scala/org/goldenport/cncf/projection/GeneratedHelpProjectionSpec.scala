package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.cli.renderer.{CliHelpJsonRenderer, CliHelpYamlRenderer}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 25, 2026
 *  version Mar. 28, 2026
 * @version Apr.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedHelpProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "HelpProjection for generated component metadata" should {
    "project service and operation help from generated specification metadata" in {
      Given("a generated-style component with help metadata on the service and operation")
      val component = GeneratedHelpProjectionFixture.component()

      When("projecting component, service, and operation help")
      val subsystemHelp = HelpProjection.projectModel(component, None)
      val componentHelp = HelpProjection.projectModel(component, Some("domain"))
      val serviceHelp = HelpProjection.projectModel(component, Some("domain.address"))
      val operationHelp = HelpProjection.projectModel(component, Some("domain.address.lookupAddress"))

      Then("the subsystem target resolves top-level requirement metadata")
      subsystemHelp.`type` shouldBe "subsystem"
      subsystemHelp.name shouldBe "domain"
      subsystemHelp.children should contain ("domain")
      subsystemHelp.domainVisions.map(_.name) shouldBe Vector("trusted_postal_foundation")
      subsystemHelp.domainUseCases.map(_.name) shouldBe Vector("postal_address_lifecycle")
      subsystemHelp.domainUseCases.head.primaryActor shouldBe Some("EndUser")

      Then("the component target resolves and exposes the generated service")
      componentHelp.`type` shouldBe "component"
      componentHelp.name shouldBe "domain"
      componentHelp.children should contain allOf ("address", "meta", "system")
      componentHelp.details("services") should contain ("address")
      componentHelp.domainVisions shouldBe Vector.empty
      componentHelp.domainUseCases shouldBe Vector.empty
      componentHelp.useCases.map(_.name) shouldBe Vector("component_postal_support")
      componentHelp.useCases.head.primaryActor shouldBe Some("EndUser")
      componentHelp.useCases.head.goal shouldBe Some("Expose reusable postal lookup behavior through the component boundary.")
      componentHelp.useCases.head.precondition shouldBe Some("The address service is configured in the component.")
      componentHelp.useCases.head.postcondition shouldBe Some("The component can answer postal lookup requests through its public service.")
      componentHelp.usage shouldBe Vector("command help domain.address")

      And("the service help uses the generated summary and description metadata")
      serviceHelp.`type` shouldBe "service"
      serviceHelp.name shouldBe "address"
      serviceHelp.summary shouldBe "Address service for postal address support."
      serviceHelp.children shouldBe Vector("lookupAddress")
      serviceHelp.useCases.map(_.name) shouldBe Vector("postal_lookup")
      serviceHelp.useCases.head.primaryActor shouldBe Some("EndUser")
      serviceHelp.useCases.head.goal shouldBe Some("Resolve a postal code into a normalized address representation.")
      serviceHelp.useCases.head.precondition shouldBe Some("A resolvable postal code is provided.")
      serviceHelp.useCases.head.postcondition shouldBe Some("A normalized address projection is returned.")
      serviceHelp.usage shouldBe Vector("command help domain.address.lookup-address")

      And("the operation help uses the generated summary and description metadata")
      operationHelp.`type` shouldBe "operation"
      operationHelp.name shouldBe "lookupAddress"
      operationHelp.summary shouldBe "Look up an address by postal code."
      operationHelp.details("description") shouldBe Vector(
        "Look up an address by postal code.Returns a normalized address representation."
      )
      operationHelp.details("arguments") shouldBe Vector.empty
      operationHelp.details("returns") shouldBe Vector("LookupAddressResult")
      operationHelp.usage shouldBe Vector("command domain.address.lookup-address")

      And("the CLI renderers can emit meta.help output from the same model")
      val yaml = CliHelpYamlRenderer.render(serviceHelp)
      yaml should include ("type: service")
      yaml should include ("name: address")
      yaml should include ("summary: Address service for postal address support.")
      yaml should include ("structuredUseCases:")
      yaml should include ("name: postal_lookup")
      yaml should include ("primaryActor: EndUser")
      yaml should include ("goal: Resolve a postal code into a normalized address representation.")
      yaml should include ("precondition: A resolvable postal code is provided.")
      yaml should include ("postcondition: A normalized address projection is returned.")

      val json = CliHelpJsonRenderer.render(operationHelp)
      json should include ("\"type\":\"operation\"")
      json should include ("\"name\":\"lookupAddress\"")
      json should include ("\"summary\":\"Look up an address by postal code.\"")
      json should include ("\"returns\":[\"LookupAddressResult\"]")

      val subsystemYaml = CliHelpYamlRenderer.render(subsystemHelp)
      subsystemYaml should include ("type: subsystem")
      subsystemYaml should include ("structuredDomainUseCases:")
      subsystemYaml should include ("name: postal_address_lifecycle")
    }
  }
}
