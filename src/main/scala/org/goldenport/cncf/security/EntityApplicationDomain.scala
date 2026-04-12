package org.goldenport.cncf.security

/*
 * Entity application domain. Domain presets derive security/default behavior;
 * applications may still override the derived policy at entity/service level.
 *
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityApplicationDomain {
  case Business, Cms, Generic
}

object EntityApplicationDomain {
  val default: EntityApplicationDomain = EntityApplicationDomain.Business

  def parse(text: String): EntityApplicationDomain =
    text.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-") match
      case "cms" | "content" | "public-content" => Cms
      case "generic" | "default" => Generic
      case _ => Business
}
