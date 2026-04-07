package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AdapterContext(
  attributes: Map[String, String] = Map.empty
) {
  def get(name: String): Option[String] =
    attributes.get(name)

  def +(kv: (String, String)): AdapterContext =
    copy(attributes = attributes + kv)
}
