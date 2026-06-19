package org.goldenport.cncf.http

/*
 * @since   Jun. 19, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */

sealed trait WebUxProfile {
  def name: String
}

object WebUxProfile {
  case object Bootstrap extends WebUxProfile {
    val name = "bootstrap"
  }
  case object Material extends WebUxProfile {
    val name = "material"
  }
  case object Compact extends WebUxProfile {
    val name = "compact"
  }
  case object Admin extends WebUxProfile {
    val name = "admin"
  }

  val default: WebUxProfile = Bootstrap

  val values: Vector[WebUxProfile] = Vector(
    Bootstrap,
    Material,
    Compact,
    Admin
  )

  def parse(value: String): Option[WebUxProfile] =
    values.find(_.name == value.trim.toLowerCase(java.util.Locale.ROOT))
}
