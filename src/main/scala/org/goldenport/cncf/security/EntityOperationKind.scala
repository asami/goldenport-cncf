package org.goldenport.cncf.security

/*
 * Entity operational kind. This is orthogonal to the application domain:
 * e.g. a business application may have both resource entities and task
 * entities.
 *
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
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
