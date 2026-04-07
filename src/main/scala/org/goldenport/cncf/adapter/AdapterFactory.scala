package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait AdapterFactory[A] {
  def key: AdapterKey

  def supports(requested: AdapterKey, context: AdapterContext): Boolean =
    key.normalized == requested.normalized

  def create(context: AdapterContext): A
}
