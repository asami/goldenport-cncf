package org.goldenport.cncf.projection

import java.nio.file.Paths
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, GenericSubsystemFactory}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecurityDeploymentProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "SecurityDeploymentProjection" should {
    "render the minimal deployment diagram from the textus-identity sample descriptor" in {
      Given("the journal sample descriptor for textus-identity")
      val path = Paths.get("docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val subsystem = GenericSubsystemFactory.default(descriptor)

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
