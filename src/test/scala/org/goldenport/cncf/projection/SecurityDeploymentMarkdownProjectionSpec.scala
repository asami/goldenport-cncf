package org.goldenport.cncf.projection

import java.nio.file.Paths
import org.goldenport.cncf.security.{AuthorizationResourcePolicies, AuthorizationResourcePolicy, SecurityRoleDefinition}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthorizationBinding, GenericSubsystemDescriptor, GenericSubsystemFactory, GenericSubsystemSecurityBinding}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 * @version Apr. 28, 2026
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

    "render descriptor role and resource policy tables" in {
      Given("a descriptor with explicit authorization role and resource policies")
      val descriptor = GenericSubsystemDescriptor(
        path = Paths.get("<memory>"),
        subsystemName = "security-policy-markdown",
        security = Some(GenericSubsystemSecurityBinding(
          authorization = Some(GenericSubsystemAuthorizationBinding(
            roles = Map(
              "blob_operator" -> SecurityRoleDefinition(
                "blob_operator",
                includes = Vector("support_operator"),
                capabilities = Vector("collection:blob:create")
              )
            ),
            resources = AuthorizationResourcePolicies(
              collections = Map("blob" -> Map("create" -> AuthorizationResourcePolicy(capabilities = Vector("collection:blob:create")))),
              associations = Map("blobattachment" -> Map("search/list" -> AuthorizationResourcePolicy(capabilities = Vector("association:blob_attachment:search/list")))),
              stores = Map("blobstore" -> Map("status" -> AuthorizationResourcePolicy(capabilities = Vector("store:blobstore:status"))))
            )
          ))
        ))
      )
      val subsystem = GenericSubsystemFactory.default(descriptor)

      When("projecting the security deployment Markdown")
      val markdown = SecurityDeploymentMarkdownProjection.project(subsystem)

      Then("the Markdown includes normalized policy rows and role definitions")
      markdown should include ("## Authorization Resource Policies")
      markdown should include ("| collection | blob | create | collection:blob:create | - | descriptor |")
      markdown should include ("| association | blobattachment | search/list | association:blob_attachment:search/list | - | descriptor |")
      markdown should include ("| store | blobstore | status | store:blobstore:status | - | descriptor |")
      markdown should include ("## Role Definitions")
      markdown should include ("| bloboperator | support_operator | collection:blob:create | descriptor |")
    }
  }
}
