package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait AdapterResolver[A] {
  def resolve(
    key: AdapterKey,
    context: AdapterContext = AdapterContext()
  ): Either[AdapterResolutionError, A]
}
