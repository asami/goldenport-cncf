package org.goldenport.cncf.entity.runtime

import org.goldenport.cncf.security.EntityOperationKind

/*
 * Runtime defaults derived from canonical EntityKind.
 *
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityKindRuntimePolicy(
  entityKind: EntityKind,
  legacyOperationKind: EntityOperationKind,
  defaultWorkingSetPolicy: Option[WorkingSetPolicy],
  workingSetDefaultNote: Option[String] = None
)

object EntityKindRuntimePolicy {
  def forKind(kind: EntityKind): EntityKindRuntimePolicy =
    kind match {
      case EntityKind.Master =>
        EntityKindRuntimePolicy(kind, EntityOperationKind.Resource, Some(WorkingSetPolicy.ResidentAll))
      case EntityKind.Document =>
        EntityKindRuntimePolicy(kind, EntityOperationKind.Resource, Some(WorkingSetPolicy.Disabled))
      case EntityKind.Workflow =>
        EntityKindRuntimePolicy(
          kind,
          EntityOperationKind.Task,
          None,
          Some("active-only candidate; requires explicit state-field policy")
        )
      case EntityKind.Task =>
        EntityKindRuntimePolicy(kind, EntityOperationKind.Task, Some(WorkingSetPolicy.Disabled))
      case EntityKind.Actor =>
        EntityKindRuntimePolicy(kind, EntityOperationKind.Resource, Some(WorkingSetPolicy.Disabled))
      case EntityKind.Asset =>
        EntityKindRuntimePolicy(kind, EntityOperationKind.Resource, Some(WorkingSetPolicy.Disabled))
    }
}
