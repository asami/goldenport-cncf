package org.goldenport.cncf.security

/*
 * Legacy entity operation kind used by authorization compatibility.
 *
 * Current use is intentionally narrow: this is a resource/task compatibility
 * label carried into UnitOfWork authorization and ABAC context as
 * application.entityOperationKind. Canonical runtime/modeling classification
 * lives in entity.runtime.EntityKind. Keep this type available for a future
 * operation-specific meaning, but do not use it as the source of Entity
 * runtime defaults.
 *
 * @since   Apr. 13, 2026
 *  version Apr. 13, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityOperationKind {
  case Resource, Task
}

object EntityOperationKind {
  val default: EntityOperationKind = EntityOperationKind.Resource

  def parse(text: String): EntityOperationKind =
    text.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-") match
      case "task" | "job" | "workflow" | "process" => Task
      case _ => Resource
}
