package org.goldenport.cncf.resource

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 33 RR-04 generic external URN resolution.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class UrnResourceAccessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _nids =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(12))

  "UrnResourceProvider" should {
    "resolve an explicitly configured external NID without changing the component resource DSL" in {
      Given("a runtime configuration binding example to an external provider class")
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.resourceUrnProvidersKey -> ConfigurationValue.StringValue(
            s"example=${classOf[ExampleUrnResourceProvider].getName}"
          )
        )),
        ConfigurationTrace.empty
      )
      val config = RuntimeConfig.from(configuration)
      val access = ResourceAccess.standard(
        ResourceUrlPolicy(),
        TextusUrnResourcePolicy(),
        config.urnResourceProviders,
        FakeHttpDriver.okText("unused")
      )
      val reference = ResourceReference.parseC("urn:example:catalog-1").toOption.get

      When("the component resource DSL reads the logical external URN")
      val result = access.readText(reference)

      Then("the configured NID provider supplies content without exposing a provider endpoint")
      result.toOption shouldBe Some("external:catalog-1")
      reference.print shouldBe "urn:example:catalog-1"
    }

    "normalize each generated provider NID before generic URN dispatch" in {
      Given("generated valid external NIDs and provider implementations")
      val property = Prop.forAll(_nids) { nid =>
        val rawnid = nid
        val provider = new UrnResourceProvider {
          val nid: String = rawnid.toUpperCase

          def read(reference: ResourceReference.Urn): Consequence[ResourceContent] =
            Consequence.success(ResourceContent(reference, Vector.empty))
        }
        val reference = ResourceReference.parseC(s"urn:${nid}:catalog-1").toOption.get
        ResourceAccess.urn(Vector(provider)).read(reference).isSuccess
      }

      When("the generic providers and URNs are normalized")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("external NID dispatch remains case-insensitive at the provider boundary")
      checked.passed shouldBe true
    }

    "reject unconfigured and malformed external URNs without selecting a provider" in {
      Given("the default resource profile without external URN providers")
      val access = ResourceAccess.standard(
        ResourceUrlPolicy(),
        TextusUrnResourcePolicy(),
        FakeHttpDriver.okText("unused")
      )
      val unconfigured = ResourceReference.parseC("urn:example:catalog-1").toOption.get
      val malformed = ResourceReference.parseC("urn:invalid nid:catalog-1")

      When("component code reads an unconfigured reference and parses a malformed reference")
      val unconfiguredresult = access.read(unconfigured)

      Then("both paths fail before an external provider can return content")
      unconfiguredresult.isFaillure shouldBe true
      malformed.isFaillure shouldBe true
    }

    "reserve Textus URNs for the dedicated standard provider" in {
      Given("an attempted generic provider that claims the reserved Textus NID")
      val shadow = new ShadowTextusUrnResourceProvider
      val access = ResourceAccess.standard(
        ResourceUrlPolicy(),
        TextusUrnResourcePolicy(),
        Vector(shadow),
        FakeHttpDriver.okText("unused")
      )
      val reference = ResourceReference.parseC("urn:textus:book:catalog-1").toOption.get

      When("the component resource DSL reads a Textus URN")
      val result = access.read(reference)

      Then("the generic provider is never invoked and the unconfigured standard route fails")
      result.isFaillure shouldBe true
      shadow.called shouldBe false
    }

    "reject reserved duplicate and mismatched provider bindings during runtime configuration" in {
      Given("invalid external provider binding values")
      val providerclass = classOf[ExampleUrnResourceProvider].getName
      val values = Vector(
        s"textus=${providerclass}",
        s"example=${providerclass},example=${providerclass}",
        s"other=${providerclass}"
      )

      When("each binding set is resolved through RuntimeConfig")
      val results = values.map { value =>
        intercept[IllegalArgumentException] {
          RuntimeConfig.from(ResolvedConfiguration(
            Configuration(Map(RuntimeConfig.resourceUrnProvidersKey -> ConfigurationValue.StringValue(value))),
            ConfigurationTrace.empty
          ))
        }
      }

      Then("the invalid configurations fail deterministically before resource access is installed")
      results should have size 3
      results.forall(_.getMessage.nonEmpty) shouldBe true
    }
  }
}

final class ExampleUrnResourceProvider extends UrnResourceProvider {
  val nid: String = "example"

  def read(reference: ResourceReference.Urn): Consequence[ResourceContent] =
    Consequence.success(ResourceContent(
      reference,
      s"external:${reference.nss}".getBytes(StandardCharsets.UTF_8).toVector
    ))
}

final class ShadowTextusUrnResourceProvider extends UrnResourceProvider {
  val nid: String = "textus"
  var called: Boolean = false

  def read(reference: ResourceReference.Urn): Consequence[ResourceContent] = {
    called = true
    Consequence.success(ResourceContent(reference, Vector.empty))
  }
}
