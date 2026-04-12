package org.goldenport.cncf.security

/*
 * Coarse entity usage classification used to derive default security and
 * authorization behavior. Fine-grained permission/relation rules may still
 * override this policy at operation or component level.
 *
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityUsageKind {
  case BusinessRecord, PublicContent, Executable, SystemInternal
}

object EntityUsageKind {
  val default: EntityUsageKind = EntityUsageKind.BusinessRecord

  def parse(text: String): EntityUsageKind =
    text.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-") match
      case "public-content" | "content" | "cms" | "cms-content" => PublicContent
      case "executable" | "executable-entity" | "job" | "workflow" => Executable
      case "system-internal" | "internal" | "system" => SystemInternal
      case _ => BusinessRecord
}
