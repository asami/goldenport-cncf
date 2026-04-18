package org.goldenport.cncf.config

import org.goldenport.cncf.cli.RunMode
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
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
  }
}
