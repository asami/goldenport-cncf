package org.goldenport.cncf.config

import java.nio.file.Files
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ConfigurationAccessSpec extends AnyWordSpec with Matchers {
  "ConfigurationAccess" should {
    "resolve values from a config file named by system property" in {
      val path = Files.createTempFile("cncf-runtime", ".conf")
      Files.writeString(path, "textus.web.descriptor = config/web-descriptor.yaml\n")
      val previous = Option(System.getProperty("cncf.config.file"))

      try {
        System.setProperty("cncf.config.file", path.toString)
        val config = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)

        ConfigurationAccess.getString(config, "textus.web.descriptor") shouldBe Some("config/web-descriptor.yaml")
      } finally {
        previous match {
          case Some(value) => System.setProperty("cncf.config.file", value)
          case None => System.clearProperty("cncf.config.file")
        }
      }
    }

    "resolve nested YAML values from a config file named by system property" in {
      val path = Files.createTempFile("cncf-runtime", ".yaml")
      Files.writeString(
        path,
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )
      val previous = Option(System.getProperty("cncf.config.file"))

      try {
        System.setProperty("cncf.config.file", path.toString)
        val config = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)

        ConfigurationAccess.getString(config, "textus.web.descriptor") shouldBe Some("config/web-descriptor.yaml")
      } finally {
        previous match {
          case Some(value) => System.setProperty("cncf.config.file", value)
          case None => System.clearProperty("cncf.config.file")
        }
      }
    }
  }
}
