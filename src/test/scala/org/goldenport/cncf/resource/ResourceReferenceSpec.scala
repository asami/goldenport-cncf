package org.goldenport.cncf.resource

import java.nio.charset.StandardCharsets
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 33 resource-reference values and content.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceReferenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _nids =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(12))

  "ResourceReference" should {
    "parse absolute URLs and generic URNs as distinct logical reference forms" in {
      Given("an absolute HTTPS URL and a generic URN")
      val url = ResourceReference.parseC("https://example.test/catalog/item-1")
      val urn = ResourceReference.parseC("urn:Example:catalog:item-1")

      When("the references are parsed")
      val parsedurl = url.toOption.get
      val parsedurn = urn.toOption.get

      Then("the URL remains a URL while the URN canonicalizes only its NID")
      parsedurl shouldBe a[ResourceReference.Url]
      parsedurl.print shouldBe "https://example.test/catalog/item-1"
      parsedurn shouldBe ResourceReference.Urn("example", "catalog:item-1")
      parsedurn.print shouldBe "urn:example:catalog:item-1"
    }

    "canonicalize generated URN NIDs without changing opaque NSS values" in {
      Given("generated valid NIDs and opaque namespace-specific strings")
      val property = Prop.forAll(_nids) { nid =>
        val source = s"urn:${nid.toUpperCase}:catalog:item-1"
        ResourceReference.parseC(source).toOption.exists {
          case urn: ResourceReference.Urn =>
            urn.nid == nid && urn.nss == "catalog:item-1"
          case _ => false
        }
      }

      When("the generated URNs are parsed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("NID normalization is deterministic")
      checked.passed shouldBe true
    }

    "reject relative, malformed, and incomplete references without selecting a provider" in {
      Given("references outside the RR-01 logical-reference grammar")
      val values = Vector("relative/file", "urn:textus", "urn::item", "https://exa mple.test/a")

      When("they are parsed before provider configuration exists")
      val results = values.map(ResourceReference.parseC)

      Then("each input returns a structured failure")
      results.forall(_.isFaillure) shouldBe true
    }

    "decode text strictly and preserve the declared charset as the default" in {
      Given("ISO-8859-1 resource bytes with a declared charset")
      val reference = ResourceReference.parseC("urn:example:message").toOption.get
      val content = ResourceContent(
        reference,
        "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1).toVector,
        declaredCharset = Some(StandardCharsets.ISO_8859_1)
      )
      val access = new ResourceAccess {
        def read(value: ResourceReference) =
          org.goldenport.Consequence.success(content)
      }
      val invalid = ResourceContent(reference, Vector(0xc3.toByte))

      When("text is decoded with its declared charset and malformed UTF-8 is requested")
      val decoded = content.textC()
      val readtext = access.readText(reference)
      val rejected = invalid.textC()

      Then("the valid text is stable and malformed content is not silently replaced")
      decoded.toOption shouldBe Some("caf\u00e9")
      readtext.toOption shouldBe Some("caf\u00e9")
      rejected.isFaillure shouldBe true
    }
  }
}
