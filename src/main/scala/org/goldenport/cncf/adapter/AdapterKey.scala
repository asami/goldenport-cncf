package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AdapterKey(value: String) {
  require(value != null, "AdapterKey value must not be null")
  require(value.trim.nonEmpty, "AdapterKey value must not be empty")

  def normalized: String = value.trim.toLowerCase
}
