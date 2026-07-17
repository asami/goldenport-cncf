package org.goldenport.cncf.config

import java.nio.charset.StandardCharsets

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecretReferenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _r5_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, rules:R3,R5,R9,R10, phase:36")

  "Opaque secret references" should {
    "resolve a declared secret key to an opaque reference" must _r5_metadata {
      "when component configuration contains a runtime-owned secret locator" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3, R5, R9, R10; secret configuration and a declared secret-reference key")
        val raw = "vault://provider/private-token"
        val access = ComponentConfigurationAccess(
          ComponentConfigurationSources(
            component = _configuration("provider.token" -> raw)
          )
        )

        When("the component resolves the declared secret-reference key")
        val result = access.resolve(ComponentConfigurationKey.requiredSecretReference("provider.token"))

        Then("the component receives only an opaque reference and diagnostics do not reveal the locator")
        val reference = result.toOption.flatMap(_.value).getOrElse(fail("expected secret reference"))
        reference.toString should not include raw
        Record.data("reference" -> reference).show should not include raw
        classOf[SecretReference].getMethods.map(_.getName).toSet should not contain "locator"
        classOf[SecretReference].getMethods.map(_.getName).toSet should not contain "resolveSecret"
      }
    }

    "resolve secret material only through the runtime-owned resolver" must _r5_metadata {
      "when a runtime provider receives an opaque reference" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R5, R9, R10; a deterministic runtime-owned secret resolver")
        val reference = SecretReference.fromConfiguration("test://provider-token").toOption.get
        val raw = "private-token".getBytes(StandardCharsets.UTF_8)
        val resolver = RuntimeSecretResolver.inMemory(Vector(reference -> raw))

        When("the runtime resolver resolves the reference")
        val result = resolver.resolveSecret(reference)

        Then("the runtime receives copied material and failures do not disclose unresolved locators")
        result.toOption.map(_._copy_bytes.toVector) shouldBe Some(raw.toVector)
        result.toOption.map(_.toString) should not contain "private-token"
        raw(0) = 'X'.toByte
        result.toOption.map(_._copy_bytes.toVector) should not contain raw.toVector
        val missing = resolver.resolveSecret(SecretReference.fromConfiguration("test://missing").toOption.get)
        val display = missing match {
          case Consequence.Failure(value) => value.display
          case _ => fail("expected unresolved secret reference failure")
        }
        display should not include "test://missing"
      }
    }
  }

  private def _configuration(entries: (String, String)*): Configuration =
    Configuration(entries.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap)
}
