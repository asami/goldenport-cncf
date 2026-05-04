package org.goldenport.cncf.security

/*
 * Legacy entity operation kind used by authorization compatibility. Canonical
 * runtime/modeling classification lives in entity.runtime.EntityKind.
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
