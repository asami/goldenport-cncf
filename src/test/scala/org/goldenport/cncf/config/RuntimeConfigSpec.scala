package org.goldenport.cncf.config

import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.log.LogBackend
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeConfigSpec extends AnyWordSpec with Matchers {
  "RuntimeConfig" should {
    "use develop operation mode and anonymous admin enabled by default" in {
      val config = RuntimeConfig.from(ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))

      config.operationMode shouldBe OperationMode.Develop
      config.webDevelopAnonymousAdmin shouldBe true
    }

    "parse operation mode independently from run mode" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"),
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("production")
        )),
        ConfigurationTrace.empty
      )

      val config = RuntimeConfig.from(configuration)

      config.mode shouldBe RunMode.Server
      config.operationMode shouldBe OperationMode.Production
    }

    "honor operation mode aliases and runtime config aliases" in {
      OperationMode.from("prod") shouldBe Some(OperationMode.Production)
      OperationMode.from("dev") shouldBe Some(OperationMode.Develop)

      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.RuntimeOperationModeKey -> ConfigurationValue.StringValue("test"),
          RuntimeConfig.RuntimeWebDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        )),
        ConfigurationTrace.empty
      )

      RuntimeConfig.getString(configuration, RuntimeConfig.OperationModeKey) shouldBe Some("test")
      RuntimeConfig.getString(configuration, RuntimeConfig.WebDevelopAnonymousAdminKey) shouldBe Some("false")
      val config = RuntimeConfig.from(configuration)
      config.operationMode shouldBe OperationMode.Test
      config.webDevelopAnonymousAdmin shouldBe false
    }

    "limit develop anonymous admin support to develop and test operation modes" in {
      OperationMode.Production.allowsDevelopAnonymousAdmin shouldBe false
      OperationMode.Demo.allowsDevelopAnonymousAdmin shouldBe false
      OperationMode.Develop.allowsDevelopAnonymousAdmin shouldBe true
      OperationMode.Test.allowsDevelopAnonymousAdmin shouldBe true
    }

    "suppress console log backends during executable specs while preserving file logging" in {
      val serverConfiguration = ResolvedConfiguration(
        Configuration(Map(RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"))),
        ConfigurationTrace.empty
      )
      val stderrConfiguration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"),
          RuntimeConfig.LogBackendKey -> ConfigurationValue.StringValue("stderr")
        )),
        ConfigurationTrace.empty
      )
      val fileConfiguration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"),
          RuntimeConfig.LogBackendKey -> ConfigurationValue.StringValue("file"),
          RuntimeConfig.LogFilePathKey -> ConfigurationValue.StringValue("/tmp/textus-runtime-test.log")
        )),
        ConfigurationTrace.empty
      )

      _with_system_property("textus.test", None) {
        RuntimeConfig.from(serverConfiguration).logBackend shouldBe LogBackend.StdoutBackend
      }
      _with_system_property("textus.test", Some("true")) {
        RuntimeConfig.from(serverConfiguration).logBackend shouldBe LogBackend.NopLogBackend
        RuntimeConfig.from(stderrConfiguration).logBackend shouldBe LogBackend.NopLogBackend
        RuntimeConfig.from(fileConfiguration).logBackend shouldBe LogBackend.FileLogBackend("/tmp/textus-runtime-test.log")
      }
    }
  }

  private def _with_system_property[A](
    key: String,
    value: Option[String]
  )(body: => A): A = {
    val previous = sys.props.get(key)
    value match {
      case Some(v) => System.setProperty(key, v)
      case None => System.clearProperty(key)
    }
    try {
      body
    } finally {
      previous match {
        case Some(v) => System.setProperty(key, v)
        case None => System.clearProperty(key)
      }
    }
  }
}
