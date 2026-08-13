package org.goldenport.cncf.projection

import java.nio.file.Paths
import org.goldenport.cncf.security.{AuthorizationResourcePolicies, AuthorizationResourcePolicy, SecurityRoleDefinition}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemAuthorizationBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecurityDeploymentMarkdownProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "SecurityDeploymentMarkdownProjection" should {
    "render mermaid and provider metadata together from the in-memory textus-identity descriptor" in {
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
      val subsystem = Subsystem(
        descriptor.subsystemName,
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      ).withDescriptor(descriptor)
      subsystem.add(DefaultSubsystemFactory.builtinComponents(subsystem))

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
