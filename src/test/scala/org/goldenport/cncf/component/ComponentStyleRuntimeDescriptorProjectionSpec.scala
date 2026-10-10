package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Using
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentStyleRuntimeDescriptorProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The generated CNCF runtime descriptor" should {
    "carry the exact packaged ComponentStyle catalog bytes" in {
      Given("the generated runtime descriptor and the packaged catalog resource")
      val descriptor = _resource_text("META-INF/cncf/runtime.yaml")
      val catalog = _resource_bytes(ComponentStyleCatalog.RESOURCE_PATH)

      When("the descriptor catalog carrier is decoded")
      val carrier = descriptor.drop(descriptor.indexOf("componentStyleCatalog:"))
      val schema = _field(carrier, "schemaVersion")
      val resource = _field(carrier, "resource")
      val encoding = _field(carrier, "encoding")
      val payload = _field(carrier, "bytes").stripPrefix("\"").stripSuffix("\"")
      val decoded = Base64.getDecoder.decode(payload)

      Then("schema, resource identity, encoding, and exact bytes agree with the packaged catalog")
      schema shouldBe "cncf.component-style-catalog-carrier.v1"
      resource shouldBe ComponentStyleCatalog.RESOURCE_PATH
      encoding shouldBe "base64"
      decoded shouldBe catalog
    }
  }

  private def _resource_text(path: String): String =
    new String(_resource_bytes(path), StandardCharsets.UTF_8)

  private def _resource_bytes(path: String): Array[Byte] =
    Option(getClass.getClassLoader.getResourceAsStream(path)).map { stream =>
      Using.resource(stream)(_.readAllBytes())
    }.getOrElse(fail(s"resource is missing: $path"))

  private def _field(text: String, key: String): String =
    text.linesIterator.find(_.trim.startsWith(s"$key:")).map(_.trim.stripPrefix(s"$key:").trim).getOrElse(fail(s"runtime descriptor field is missing: $key"))
}
