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
    "resolve values from a config file named by Textus system property" in {
      val path = Files.createTempFile("textus-runtime", ".conf")
      Files.writeString(path, "textus.web.descriptor = config/web-descriptor.yaml\n")
      val previous = Option(System.getProperty("textus.config.file"))

      try {
        System.setProperty("textus.config.file", path.toString)
        val config = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)

        ConfigurationAccess.getString(config, "textus.web.descriptor") shouldBe Some("config/web-descriptor.yaml")
      } finally {
        previous match {
          case Some(value) => System.setProperty("textus.config.file", value)
          case None => System.clearProperty("textus.config.file")
        }
      }
    }

    "resolve nested YAML values from a config file named by legacy CNCF system property" in {
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

    "prefer Textus system config file over legacy CNCF system config file" in {
      val textus = Files.createTempFile("textus-runtime", ".conf")
      val cncf = Files.createTempFile("cncf-runtime", ".conf")
      Files.writeString(textus, "textus.web.descriptor = config/from-textus.yaml\n")
      Files.writeString(cncf, "textus.web.descriptor = config/from-cncf.yaml\n")
      val previousTextus = Option(System.getProperty("textus.config.file"))
      val previousCncf = Option(System.getProperty("cncf.config.file"))

      try {
        System.setProperty("textus.config.file", textus.toString)
        System.setProperty("cncf.config.file", cncf.toString)
        val config = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)

        ConfigurationAccess.getString(config, "textus.web.descriptor") shouldBe Some("config/from-textus.yaml")
      } finally {
        previousTextus match {
          case Some(value) => System.setProperty("textus.config.file", value)
          case None => System.clearProperty("textus.config.file")
        }
        previousCncf match {
          case Some(value) => System.setProperty("cncf.config.file", value)
          case None => System.clearProperty("cncf.config.file")
        }
      }
    }
  }
}
