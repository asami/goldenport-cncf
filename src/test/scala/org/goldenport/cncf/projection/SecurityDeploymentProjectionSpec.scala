package org.goldenport.cncf.projection

import java.nio.file.Paths
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecurityDeploymentProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "SecurityDeploymentProjection" should {
    "render the minimal deployment diagram from the in-memory textus-identity descriptor" in {
      Given("an in-memory textus-identity descriptor")
      val path = Paths.get("<memory-textus-identity>")
      val descriptor = GenericSubsystemDescriptor(
        path = path,
        subsystemName = "textus-identity",
        version = Some("0.1.0-SNAPSHOT"),
        componentBindings = Vector(GenericSubsystemComponentBinding(
          componentName = "textus-user-account",
          version = Some("0.1.0-SNAPSHOT"),
          coordinate = Some("org.textus:textus-user-account:0.1.0-SNAPSHOT"),
          componentId = Some(ComponentId("org.textus.UserAccount"))
        )),
        security = Some(GenericSubsystemSecurityBinding(
          authentication = Some(GenericSubsystemAuthenticationBinding(
            convention = Some("enabled"),
            fallbackPrivilege = Some("disabled"),
            providers = Vector(GenericSubsystemAuthenticationProviderBinding(
              name = "user-account",
              component = "textus-user-account",
              kind = Some("human"),
              enabled = Some(true),
              priority = Some(100),
              schemes = Vector("bearer", "refresh-token"),
              isDefault = Some(true)
            ))
          ))
        ))
      )
      val subsystem = Subsystem(
        descriptor.subsystemName,
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      ).withDescriptor(descriptor)

      When("projecting the security deployment diagram as Mermaid")
      val mermaid = SecurityDeploymentProjection.projectMermaid(subsystem)

      Then("the diagram includes the subsystem, component, provider, and common chokepoints")
      mermaid should include ("flowchart LR")
      mermaid should include ("Subsystem: textus-identity")
      mermaid should include ("Component: textus-user-account")
      mermaid should include ("Auth Provider: user-account")
      mermaid should include ("kind=human")
      mermaid should include ("schemes=bearer,refresh-token")
      mermaid should include ("ExecutionContext(SecurityContext)")
      mermaid should include ("ActionCall")
      mermaid should include ("UnitOfWork")
      mermaid should include ("Ingress")
    }
  }
}
