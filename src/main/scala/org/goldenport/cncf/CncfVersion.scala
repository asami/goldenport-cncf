package org.goldenport.cncf

/*
 * @since   Jan. 13, 2026
 * @version Jan. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfVersion {
  private val _fallback = "0.3.0-SNAPSHOT"

  def current: String = {
    val pkg = getClass.getPackage
    Option(pkg.getImplementationVersion)
      .orElse(Option(pkg.getSpecificationVersion))
      .getOrElse(_fallback)
  }
}
