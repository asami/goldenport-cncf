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
final class SecurityDeploymentMarkdownProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "SecurityDeploymentMarkdownProjection" should {
    "render mermaid and provider metadata together from the textus-identity sample descriptor" in {
      Given("the journal sample descriptor for textus-identity")
      val path = Paths.get("docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val subsystem = GenericSubsystemFactory.default(descriptor)

      When("projecting the security deployment specification as Markdown")
      val markdown = SecurityDeploymentMarkdownProjection.project(subsystem)

      Then("the markdown includes subsystem summary, diagram, provider table, and chokepoints")
      markdown should include ("# Security Deployment Specification")
      markdown should include ("- name: `textus-identity`")
      markdown should include ("- authentication convention: `enabled`")
      markdown should include ("- fallback privilege: `disabled`")
      markdown should include ("```mermaid")
      markdown should include ("Component: textus-user-account")
      markdown should include ("| user-account | textus-user-account | human | descriptor | 100 | true | bearer, refresh-token |")
      markdown should include ("## Framework Chokepoints")
      markdown should include ("- `ActionCall`")
      markdown should include ("- `UnitOfWork`")
    }
  }
}
