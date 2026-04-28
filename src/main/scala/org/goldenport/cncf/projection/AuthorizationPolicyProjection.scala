package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.security.{AuthorizationResourcePolicies, AuthorizationResourcePolicy, SecurityRoleDefinition, SecuritySubject}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthorizationBinding, Subsystem}

/*
 * @since   Apr. 28, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object AuthorizationPolicyProjection {
  def project(base: Component): Record =
    project(MetaProjectionSupport.components(base), base.subsystem.map(_.name).getOrElse(base.name))

  def project(
    components: Vector[Component],
    subsystemName: String
  ): Record = {
    val authorization = _authorization(components)
    Record.data(
      "subsystem" -> subsystemName,
      "roleDefinitions" -> authorization.map(_.roles).map(_role_records).getOrElse(Vector.empty),
      "resourcePolicies" -> authorization.map(_.resources).map(_resource_policy_records).getOrElse(Vector.empty),
      "blobOperationRequirements" -> _blob_requirement_records(components)
    )
  }

  def hasVisiblePolicy(record: Record): Boolean =
    record.getAny("roleDefinitions").exists(_non_empty_seq) ||
      record.getAny("resourcePolicies").exists(_non_empty_seq) ||
      record.getAny("blobOperationRequirements").exists(_non_empty_seq)

  private def _authorization(
    components: Vector[Component]
  ): Option[GenericSubsystemAuthorizationBinding] =
    _subsystem(components)
      .flatMap(_.descriptor)
      .flatMap(_.security)
      .flatMap(_.authorization)

  private def _subsystem(
    components: Vector[Component]
  ): Option[Subsystem] =
    components.iterator.flatMap(_.subsystem).toVector.headOption

  private def _role_records(
    roles: Map[String, SecurityRoleDefinition]
  ): Vector[Record] =
    roles.toVector.sortBy(x => SecuritySubject.normalize(x._1)).map {
      case (name, role) =>
        Record.data(
          "name" -> SecuritySubject.normalize(name),
          "displayName" -> name,
          "includes" -> role.includes.sorted,
          "capabilities" -> role.capabilities.sorted,
          "normalizedCapabilities" -> role.capabilities.map(SecuritySubject.normalize).sorted,
          "source" -> "descriptor"
        )
    }

  private def _resource_policy_records(
    policies: AuthorizationResourcePolicies
  ): Vector[Record] =
    _family_records("collection", policies.collections) ++
      _family_records("association", policies.associations) ++
      _family_records("store", policies.stores)

  private def _family_records(
    family: String,
    table: Map[String, Map[String, AuthorizationResourcePolicy]]
  ): Vector[Record] =
    table.toVector.sortBy(x => SecuritySubject.normalize(x._1)).flatMap {
      case (resource, actions) =>
        actions.toVector.sortBy(x => SecuritySubject.normalize(x._1)).map {
          case (action, policy) =>
            Record.data(
              "family" -> family,
              "resource" -> SecuritySubject.normalize(resource),
              "displayResource" -> resource,
              "action" -> SecuritySubject.normalize(action),
              "displayAction" -> action,
              "requiredCapabilities" -> policy.capabilities.sorted,
              "normalizedCapabilities" -> policy.normalizedCapabilities.sorted,
              "permissionOverride" -> policy.permission.getOrElse(""),
              "source" -> "descriptor"
            )
        }
    }

  private def _blob_requirement_records(
    components: Vector[Component]
  ): Vector[Record] =
    if (_has_blob_component(components))
      _blob_requirements.map {
        case (operation, family, resource, action, requirement) =>
          Record.data(
            "operation" -> operation,
            "family" -> family,
            "resource" -> SecuritySubject.normalize(resource),
            "displayResource" -> resource,
            "action" -> SecuritySubject.normalize(action),
            "displayAction" -> action,
            "requirement" -> requirement,
            "source" -> "builtin-blob-contract"
          )
      }
    else
      Vector.empty

  private def _has_blob_component(
    components: Vector[Component]
  ): Boolean =
    components.exists(c => SecuritySubject.normalize(c.name) == SecuritySubject.normalize("blob"))

  private val _blob_requirements: Vector[(String, String, String, String, String)] = Vector(
    ("register_blob", "collection", "blob", "create", "Blob metadata collection create before payload write"),
    ("read_blob", "collection", "blob", "read", "Blob metadata read and managed payload resolution"),
    ("resolve_blob_url", "collection", "blob", "read", "Blob metadata read for URL resolution"),
    ("get_blob_metadata", "collection", "blob", "read", "Blob metadata read"),
    ("attach_blob_to_entity", "domain", "sourceEntityId", "update", "Source entity update"),
    ("attach_blob_to_entity", "collection", "blob", "read", "Target Blob metadata read"),
    ("attach_blob_to_entity", "association", "blob_attachment", "create", "Blob attachment association create"),
    ("detach_blob_from_entity", "domain", "sourceEntityId", "update", "Source entity update"),
    ("detach_blob_from_entity", "association", "blob_attachment", "delete", "Blob attachment association delete"),
    ("list_entity_blobs", "domain", "sourceEntityId", "read", "Source entity read"),
    ("list_entity_blobs", "association", "blob_attachment", "search/list", "Blob attachment association search/list"),
    ("list_entity_blobs", "collection", "blob", "read", "Target Blob read filtering"),
    ("admin_delete_blob", "operation", "blob.admin_delete_blob", "invoke", "Admin operation gate"),
    ("admin_delete_blob", "collection", "blob", "delete", "Blob metadata collection delete"),
    ("admin_blob_store_status", "operation", "blob.admin_blob_store_status", "invoke", "Admin operation gate"),
    ("admin_blob_store_status", "store", "blobstore", "status", "BlobStore status diagnostics")
  )

  private def _non_empty_seq(value: Any): Boolean =
    value match {
      case Some(x) => _non_empty_seq(x)
      case xs: Seq[?] => xs.nonEmpty
      case _ => false
    }
}
