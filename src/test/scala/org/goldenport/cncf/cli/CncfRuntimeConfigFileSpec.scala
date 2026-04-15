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
  }
}
