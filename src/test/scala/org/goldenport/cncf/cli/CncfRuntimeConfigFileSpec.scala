package org.goldenport.cncf.cli

import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipOutputStream}
import org.goldenport.cncf.config.RuntimeConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 15, 2026
 * @version Apr. 25, 2026
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

    "detect a component CAR from a component project directory for plain server startup" in {
      val cwd = Files.createTempDirectory("textus-component-only-server")
      val target = cwd.resolve("component").resolve("target")
      Files.createDirectories(target)
      val car = target.resolve("notice-board-0.1.0.car")
      Files.writeString(car, "placeholder")

      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.ComponentFileKey) shouldBe Some(car.toString)
    }

    "resolve a named component from the standard repository for server startup" in {
      val cwd = Files.createTempDirectory("textus-component-name-repo")
      val repository = cwd.resolve("repository")
      val artifactdir = repository.resolve("org").resolve("simplemodeling").resolve("car").resolve("cwitter").resolve("0.0.1-SNAPSHOT")
      Files.createDirectories(artifactdir)
      val car = artifactdir.resolve("cwitter-0.0.1-SNAPSHOT.car")
      _write_zip(
        car,
        Map(
          "component-descriptor.json" ->
            """{"component":{"name":"cwitter"},"version":"0.0.1-SNAPSHOT"}""",
          "assembly-descriptor.yaml" ->
            """subsystem: cwitter
              |version: 0.0.1-SNAPSHOT
              |components:
              |  - name: cwitter
              |    version: 0.0.1-SNAPSHOT
              |  - name: textus-user-account
              |    version: 0.1.0-SNAPSHOT
              |""".stripMargin
        )
      )
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--repository-dir", repository.toString, "--textus.component=cwitter", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.ComponentFileKey}=${car}")
    }

    "not append a repository component file when component file is already explicit" in {
      val cwd = Files.createTempDirectory("textus-component-file-explicit")
      val repository = cwd.resolve("repository")
      val artifactdir = repository.resolve("org").resolve("simplemodeling").resolve("car").resolve("cwitter").resolve("0.0.1-SNAPSHOT")
      Files.createDirectories(artifactdir)
      val repoCar = artifactdir.resolve("cwitter-0.0.1-SNAPSHOT.car")
      _write_zip(
        repoCar,
        Map("component-descriptor.json" -> """{"component":{"name":"cwitter"},"version":"0.0.1-SNAPSHOT"}""")
      )
      val explicitCar = cwd.resolve("explicit-cwitter.car")
      _write_zip(
        explicitCar,
        Map("component-descriptor.json" -> """{"component":{"name":"cwitter"},"version":"local"}""")
      )
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--repository-dir", repository.toString, s"--${RuntimeConfig.ComponentFileKey}=${explicitCar}", "--textus.component=cwitter", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      resolved.actualArgs.toVector.count(_.startsWith(s"--${RuntimeConfig.ComponentFileKey}=")) shouldBe 1
      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.ComponentFileKey}=${explicitCar}")
    }

    "resolve a named subsystem SAR from the standard repository for server startup" in {
      val cwd = Files.createTempDirectory("textus-subsystem-name-repo")
      val repository = cwd.resolve("repository")
      val artifactdir = repository.resolve("org").resolve("simplemodeling").resolve("sar").resolve("cwitter").resolve("0.0.1-SNAPSHOT")
      Files.createDirectories(artifactdir)
      val sar = artifactdir.resolve("cwitter-0.0.1-SNAPSHOT.sar")
      _write_zip(
        sar,
        Map(
          "subsystem-descriptor.yaml" ->
            """subsystem: cwitter
              |version: 0.0.1-SNAPSHOT
              |components:
              |  - name: cwitter
              |    version: 0.0.1-SNAPSHOT
              |  - name: textus-user-account
              |    version: 0.1.0-SNAPSHOT
              |""".stripMargin
        )
      )
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--repository-dir", repository.toString, "--textus.subsystem=cwitter", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.SubsystemFileKey}=${sar}")
    }
  }

  private def _write_zip(path: java.nio.file.Path, entries: Map[String, String]): Unit = {
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach { case (name, content) =>
        out.putNextEntry(new ZipEntry(name))
        out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        out.closeEntry()
      }
    } finally {
      out.close()
    }
  }
}
