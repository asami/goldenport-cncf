package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResolvedSecurityWiringSpec extends AnyWordSpec with Matchers {
  "ResolvedSecurityWiring" should {
    "prefer descriptor bindings over duplicate convention providers and keep explicit priority" in {
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<memory>"),
        subsystemName = "textus-identity",
        componentBindings = Vector(
          GenericSubsystemComponentBinding("textus-user-account")
        ),
        security = Some(
          GenericSubsystemSecurityBinding(
            authentication = Some(
              GenericSubsystemAuthenticationBinding(
                convention = Some("enabled"),
                fallbackPrivilege = Some("enabled"),
                providers = Vector(
                  GenericSubsystemAuthenticationProviderBinding(
                    name = "user-account",
                    component = "textus-user-account",
                    kind = Some("human"),
                    enabled = Some(true),
                    priority = Some(100),
                    schemes = Vector("bearer"),
                    isDefault = Some(true)
                  )
                )
              )
            )
          )
        )
      )
      val component = _component("UserAccount", Vector(_provider("user-account"), _provider("other-provider")))

      val wiring = ResolvedSecurityWiring.resolve(Some(descriptor), Vector(component))

      wiring.authentication.providers.map(x => (x.componentName, x.name, x.source.toString)) shouldBe Vector(
        ("UserAccount", "user-account", "Descriptor"),
        ("UserAccount", "other-provider", "Convention")
      )
      wiring.authentication.providers.head.priority shouldBe 100
      wiring.authentication.providers.head.isDefault shouldBe true
      wiring.authentication.providers.head.provider.map(_.name) shouldBe Some("user-account")
    }

    "order providers by priority descending then normalized component and provider names" in {
      val providers = Vector(
        ResolvedAuthenticationProviderBinding(name = "z-provider", componentName = "b-component", priority = 10),
        ResolvedAuthenticationProviderBinding(name = "a-provider", componentName = "b-component", priority = 10),
        ResolvedAuthenticationProviderBinding(name = "c-provider", componentName = "a-component", priority = 20),
        ResolvedAuthenticationProviderBinding(name = "b-provider", componentName = "a-component", priority = 10)
      )

      val ordered = ResolvedAuthenticationWiring(providers = providers).enabledProviders
        .sortBy(x => (-x.priority, x.componentName.toLowerCase.replace("_", "").replace("-", ""), x.name.toLowerCase.replace("_", "").replace("-", "")))

      ordered.map(x => (x.priority, x.componentName, x.name)) shouldBe Vector(
        (20, "a-component", "c-provider"),
        (10, "a-component", "b-provider"),
        (10, "b-component", "a-provider"),
        (10, "b-component", "z-provider")
      )
    }

    "drop convention providers when convention is disabled" in {
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<memory>"),
        subsystemName = "textus-identity",
        componentBindings = Vector(
          GenericSubsystemComponentBinding("textus-user-account")
        ),
        security = Some(
          GenericSubsystemSecurityBinding(
            authentication = Some(
              GenericSubsystemAuthenticationBinding(
                convention = Some("disabled"),
                fallbackPrivilege = Some("disabled"),
                providers = Vector.empty
              )
            )
          )
        )
      )
      val component = _component("UserAccount", Vector(_provider("user-account")))

      val wiring = ResolvedSecurityWiring.resolve(Some(descriptor), Vector(component))

      wiring.authentication.conventionEnabled shouldBe false
      wiring.authentication.fallbackPrivilegeEnabled shouldBe false
      wiring.authentication.providers shouldBe empty
    }
  }

  private def _component(name: String, providers: Vector[AuthenticationProvider]): Component =
    new Component() {
      override def authenticationProviders: Vector[AuthenticationProvider] = providers
    }.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "spec",
        name = name,
        version = "0.0.0",
        component = Some("textus-user-account")
      )
    )

  private def _provider(pname: String): AuthenticationProvider =
    new AuthenticationProvider {
      override val name: String = pname
      def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
        Consequence.success(None)
    }
}
