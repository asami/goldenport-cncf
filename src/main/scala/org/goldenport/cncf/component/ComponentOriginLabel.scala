package org.goldenport.cncf.component

/*
 * @since   Apr. 11, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentOriginLabel {
  def userLabel(origin: String): String =
    if (origin.startsWith("component-dir:sar:"))
      origin
        .stripPrefix("component-dir:sar:")
        .split(":")
        .toVector match {
          case Vector(subsystem, version, "car", component, componentVersion) =>
            s"bundled sar ${subsystem}@${version} -> ${component}@${componentVersion}"
          case xs =>
            s"bundled sar ${xs.mkString(":")}"
        }
    else if (origin.startsWith("component-dir:car:"))
      origin
        .stripPrefix("component-dir:car:")
        .split(":")
        .toVector match {
          case Vector(component, version) =>
            s"active car ${component}@${version}"
          case xs =>
            s"active car ${xs.mkString(":")}"
        }
    else if (origin == "component-dir")
      "active packaged directory"
    else if (origin == "scala-cli")
      "scala-cli classes"
    else origin
}
