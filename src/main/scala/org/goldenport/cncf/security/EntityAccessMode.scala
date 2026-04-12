package org.goldenport.cncf.security

/*
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityAccessMode {
  case UserPermission, ServiceInternal, System
}

object EntityAccessMode {
  def parse(text: String): EntityAccessMode =
    text.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-") match
      case "service-internal" | "internal" | "service" => ServiceInternal
      case "system" => System
      case _ => UserPermission
}
