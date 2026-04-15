package org.goldenport.cncf.cli

import java.nio.file.Files
import org.goldenport.cncf.config.RuntimeConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeConfigFileSpec extends AnyWordSpec with Matchers {
  "CncfRuntime" should {
    "resolve an explicit YAML config file passed as a CLI framework option" in {
      val cwd = Files.createTempDirectory("cncf-runtime-config")
      val config = cwd.resolve("runtime.yaml")
      Files.writeString(
        config,
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )

      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", s"--cncf.config.file=${config}", "server")
      )

      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WebDescriptorKey) shouldBe Some("config/web-descriptor.yaml")
      bootstrap.invocation.actualArgs.toVector should contain (s"--cncf.config.file=${config}")
    }

    "resolve a standard .cncf config.yaml file before command-line domain execution" in {
      val cwd = Files.createTempDirectory("cncf-standard-yaml")
      val configdir = cwd.resolve(".cncf")
      Files.createDirectories(configdir)
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )

      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WebDescriptorKey) shouldBe Some("config/web-descriptor.yaml")
    }

    "prefer standard config.yaml over config.conf in the same .cncf scope" in {
      val cwd = Files.createTempDirectory("cncf-standard-yaml-precedence")
      val configdir = cwd.resolve(".cncf")
      Files.createDirectories(configdir)
      Files.writeString(configdir.resolve("config.conf"), "textus.web.descriptor = config/from-conf.yaml\n")
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-yaml.yaml
          |""".stripMargin
      )

      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WebDescriptorKey) shouldBe Some("config/from-yaml.yaml")
    }

    "resolve standard configuration files in conf properties json yaml xml order" in {
      val cwd = Files.createTempDirectory("cncf-standard-order")
      val configdir = cwd.resolve(".cncf")
      Files.createDirectories(configdir)
      Files.writeString(configdir.resolve("config.conf"), "textus.web.descriptor = config/from-conf.yaml\n")
      Files.writeString(configdir.resolve("config.properties"), "textus.web.descriptor = config/from-properties.yaml\n")
      Files.writeString(configdir.resolve("config.json"), """{"textus":{"web":{"descriptor":"config/from-json.yaml"}}}""")
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-yaml.yaml
          |""".stripMargin
      )
      Files.writeString(
        configdir.resolve("config.xml"),
        """<config>
          |  <textus>
          |    <web>
          |      <descriptor>config/from-xml.yaml</descriptor>
          |    </web>
          |  </textus>
          |</config>
          |""".stripMargin
      )

      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WebDescriptorKey) shouldBe Some("config/from-xml.yaml")
    }
  }
}
