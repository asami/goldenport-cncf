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
    "resolve an explicit YAML config file passed as a Textus CLI framework option" in {
      val cwd = Files.createTempDirectory("textus-runtime-config")
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
        Array("--discover=classes", s"--textus.config.file=${config}", "server")
      )

      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WebDescriptorKey) shouldBe Some("config/web-descriptor.yaml")
      bootstrap.invocation.actualArgs.toVector should contain (s"--textus.config.file=${config}")
    }

    "resolve a legacy explicit YAML config file passed as a CNCF CLI framework option" in {
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

    "resolve a standard .textus config.yaml file before command-line domain execution" in {
      val cwd = Files.createTempDirectory("textus-standard-yaml")
      val configdir = cwd.resolve(".textus")
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

    "resolve a legacy standard .cncf config.yaml file as compatibility input" in {
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

    "prefer standard .textus configuration over legacy .cncf configuration" in {
      val cwd = Files.createTempDirectory("textus-over-cncf")
      val legacydir = cwd.resolve(".cncf")
      val configdir = cwd.resolve(".textus")
      Files.createDirectories(legacydir)
      Files.createDirectories(configdir)
      Files.writeString(
        legacydir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-cncf.yaml
          |""".stripMargin
      )
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-textus.yaml
          |""".stripMargin
      )

      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WebDescriptorKey) shouldBe Some("config/from-textus.yaml")
    }

    "prefer standard config.yaml over config.conf in the same .textus scope" in {
      val cwd = Files.createTempDirectory("textus-standard-yaml-precedence")
      val configdir = cwd.resolve(".textus")
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
      val cwd = Files.createTempDirectory("textus-standard-order")
      val configdir = cwd.resolve(".textus")
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
