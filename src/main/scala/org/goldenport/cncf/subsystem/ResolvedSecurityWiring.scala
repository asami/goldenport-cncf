package org.goldenport.cncf.subsystem

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.security.AuthenticationProvider

/*
 * Runtime-resolved security wiring for a subsystem.
 *
 * - Descriptor entries are explicit deployment intent.
 * - Convention entries are inferred from deployed components.
 * - This resolved model becomes the runtime selection source and later the
 *   projection source for deployment diagrams/specifications.
 *
 * @since   Apr.  9, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResolvedAuthenticationProviderBinding(
  name: String,
  componentName: String,
  kind: Option[String] = None,
  enabled: Boolean = true,
  priority: Int = 0,
  schemes: Vector[String] = Vector.empty,
  isDefault: Boolean = false,
  source: ResolvedAuthenticationProviderBinding.Source = ResolvedAuthenticationProviderBinding.Source.Convention,
  provider: Option[AuthenticationProvider] = None
)

object ResolvedAuthenticationProviderBinding {
  enum Source {
    case Descriptor, Convention
  }
}

final case class ResolvedAuthenticationWiring(
  conventionEnabled: Boolean = true,
  fallbackPrivilegeEnabled: Boolean = true,
  providers: Vector[ResolvedAuthenticationProviderBinding] = Vector.empty
) {
  def enabledProviders: Vector[ResolvedAuthenticationProviderBinding] =
    providers.filter(_.enabled)
}

final case class ResolvedSecurityWiring(
  authentication: ResolvedAuthenticationWiring = ResolvedAuthenticationWiring()
)

object ResolvedSecurityWiring {
  val empty: ResolvedSecurityWiring = ResolvedSecurityWiring()

  def resolve(
    descriptor: Option[GenericSubsystemDescriptor],
    components: Vector[Component]
  ): ResolvedSecurityWiring = {
    val explicit = descriptor.toVector.flatMap(_.security.toVector).flatMap(_.authentication.toVector).flatMap { auth =>
      auth.providers.map { p =>
        ResolvedAuthenticationProviderBinding(
          name = p.name,
          componentName = GenericSubsystemDescriptor.runtimeComponentName(p.component),
          kind = p.kind,
          enabled = p.enabled.getOrElse(true),
          priority = p.priority.getOrElse(0),
          schemes = p.schemes,
          isDefault = p.isDefault.getOrElse(false),
          source = ResolvedAuthenticationProviderBinding.Source.Descriptor,
          provider = _find_provider(components, GenericSubsystemDescriptor.runtimeComponentName(p.component), p.name)
        )
      }
    }
    val authOpt = descriptor.flatMap(_.security).flatMap(_.authentication)
    val conventionEnabled = authOpt.flatMap(_.convention).forall(_is_enabled)
    val fallbackPrivilegeEnabled = authOpt.flatMap(_.fallbackPrivilege).forall(_is_enabled)
    val explicitKeys = explicit.map(x => (_normalize(x.componentName), _normalize(x.name))).toSet
    val convention =
      if (!conventionEnabled) Vector.empty
      else components.flatMap { component =>
        component.authenticationProviders.map { provider =>
          ResolvedAuthenticationProviderBinding(
            name = provider.name,
            componentName = _component_runtime_name(component),
            source = ResolvedAuthenticationProviderBinding.Source.Convention,
            provider = Some(provider)
          )
        }
      }.filterNot(x => explicitKeys.contains((_normalize(x.componentName), _normalize(x.name))))
    ResolvedSecurityWiring(
      authentication = ResolvedAuthenticationWiring(
        conventionEnabled = conventionEnabled,
        fallbackPrivilegeEnabled = fallbackPrivilegeEnabled,
        providers = (explicit ++ convention).sortBy(x => (-x.priority, _normalize(x.componentName), _normalize(x.name)))
      )
    )
  }

  private def _find_provider(
    components: Vector[Component],
    componentName: String,
    providerName: String
  ): Option[AuthenticationProvider] =
    components.find(c => _normalize(_component_runtime_name(c)) == _normalize(componentName)).flatMap(_.authenticationProviders.find(p => _normalize(p.name) == _normalize(providerName)))

  private def _component_runtime_name(component: Component): String =
    component.artifactMetadata.flatMap(_.component)
      .map(GenericSubsystemDescriptor.runtimeComponentName)
      .orElse(scala.util.Try(component.name).toOption)
      .getOrElse(component.getClass.getSimpleName.stripSuffix("$"))

  private def _is_enabled(value: String): Boolean =
    value.trim.toLowerCase match {
      case "enabled" | "enable" | "true" | "yes" | "on" | "1" => true
      case _ => false
    }

  private def _normalize(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase.replace("_", "").replace("-", "")
}
