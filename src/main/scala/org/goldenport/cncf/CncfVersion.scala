package org.goldenport.cncf

/*
 * @since   Jan. 13, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfVersion {
  def current: String = {
    val pkg = getClass.getPackage
    Option(pkg.getImplementationVersion)
      .orElse(Option(pkg.getSpecificationVersion))
      .getOrElse(CncfBuildInfo.version)
  }
}
