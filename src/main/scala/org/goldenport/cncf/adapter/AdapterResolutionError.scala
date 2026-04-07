package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait AdapterResolutionError {
  def message: String
}

final case class AdapterNotFound(key: AdapterKey) extends AdapterResolutionError {
  def message: String =
    s"Adapter not found for key: ${key.value}"
}

final case class AmbiguousAdapterResolution(
  key: AdapterKey,
  matchedFactoryKeys: Vector[String]
) extends AdapterResolutionError {
  def message: String =
    s"Multiple adapters matched key ${key.value}: ${matchedFactoryKeys.mkString(", ")}"
}
