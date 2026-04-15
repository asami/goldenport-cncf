package org.goldenport.cncf.config

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.provisional.observation.Taxonomy
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeFileConfigLoaderSpec extends AnyWordSpec with Matchers {
  "RuntimeFileConfigLoader" should {
    "load flat HOCON and key-value configuration files" in {
      val path = Files.createTempFile("cncf-runtime", ".conf")
      Files.writeString(path, "textus.web.descriptor = config/web-descriptor.yaml\n")

      val config = new RuntimeFileConfigLoader().load(path).toOption.get

      config.string("textus.web.descriptor") shouldBe Some("config/web-descriptor.yaml")
    }

    "load nested YAML configuration files" in {
      val path = Files.createTempFile("cncf-runtime", ".yaml")
      Files.writeString(
        path,
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )

      val config = new RuntimeFileConfigLoader().load(path).toOption.get

      config.values.get("textus").isDefined shouldBe true
      config.values("textus").asInstanceOf[ConfigurationValue.ObjectValue]
        .values("web").asInstanceOf[ConfigurationValue.ObjectValue]
        .values("descriptor") shouldBe ConfigurationValue.StringValue("config/web-descriptor.yaml")
    }

    "load XML configuration files" in {
      val path = Files.createTempFile("cncf-runtime", ".xml")
      Files.writeString(
        path,
        """<config>
          |  <textus>
          |    <web>
          |      <descriptor>config/web-descriptor.yaml</descriptor>
          |    </web>
          |  </textus>
          |</config>
          |""".stripMargin
      )

      val config = new RuntimeFileConfigLoader().load(path).toOption.get

      config.string("textus.web.descriptor") shouldBe Some("config/web-descriptor.yaml")
    }

    "load properties configuration files as conf-compatible inputs" in {
      val path = Files.createTempFile("cncf-runtime", ".properties")
      Files.writeString(path, "textus.web.descriptor = config/web-descriptor.yaml\n")

      val config = new RuntimeFileConfigLoader().load(path).toOption.get

      config.string("textus.web.descriptor") shouldBe Some("config/web-descriptor.yaml")
    }

    "fail on scalar YAML roots because runtime configuration must be an object" in {
      val path = Files.createTempFile("cncf-runtime", ".yaml")
      Files.writeString(path, "plain-string\n")

      new RuntimeFileConfigLoader().load(path) match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy shouldBe Taxonomy.resourceInvalid
          c.observation.cause.descriptor.facets.collect {
            case Descriptor.Facet.Properties(properties) => properties
          }.headOption.get should contain allOf (
            "fileType" -> "yaml",
            "path" -> path.toString
          )
        case other => fail(s"expected failure, got $other")
      }
    }
  }
}
