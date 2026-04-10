package org.goldenport.cncf.subsystem

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.RuntimeConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 26, 2026
 * @version Mar. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextusIdentitySubsystemFactorySpec extends AnyWordSpec with Matchers {
  "TextusIdentitySubsystemFactory" should {
    "discover only the user account component" in {
      val subsystem = TextusIdentitySubsystemFactory.default()
      val names = subsystem.components.map(_.name).sorted

      names shouldBe Vector("UserAccount")
    }

    "honor the development override repository" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.RepositoryDirKey ->
            ConfigurationValue.StringValue("scala-cli:/Users/asami/src/dev2026/textus-user-account")
        )),
        ConfigurationTrace.empty
      )
      val subsystem = TextusIdentitySubsystemFactory.default(configuration = configuration)
      val names = subsystem.components.map(_.name).sorted

      names shouldBe Vector("UserAccount")
    }
  }
}
