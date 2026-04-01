package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.cli.renderer.{CliHelpJsonRenderer, CliHelpYamlRenderer}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 25, 2026
 * @author  ASAMI, Tomoharu
 *  version Mar. 28, 2026
 * @version Apr.  1, 2026
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
      val componentHelp = HelpProjection.projectModel(component, Some("domain"))
      val serviceHelp = HelpProjection.projectModel(component, Some("domain.address"))
      val operationHelp = HelpProjection.projectModel(component, Some("domain.address.lookupAddress"))

      Then("the component target resolves and exposes the generated service")
      componentHelp.`type` shouldBe "component"
      componentHelp.name shouldBe "domain"
      componentHelp.children should contain allOf ("address", "meta", "system")
      componentHelp.details("services") should contain ("address")
      componentHelp.usage shouldBe Vector("command help domain.address")

      And("the service help uses the generated summary and description metadata")
      serviceHelp.`type` shouldBe "service"
      serviceHelp.name shouldBe "address"
      serviceHelp.summary shouldBe "Address service for postal address support."
      serviceHelp.children shouldBe Vector("lookupAddress")
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

      val json = CliHelpJsonRenderer.render(operationHelp)
      json should include ("\"type\":\"operation\"")
      json should include ("\"name\":\"lookupAddress\"")
      json should include ("\"summary\":\"Look up an address by postal code.\"")
      json should include ("\"returns\":[\"LookupAddressResult\"]")
    }
  }
}
