package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class InMemoryAdapterRegistry[A](
  factories: Vector[AdapterFactory[A]]
) extends AdapterRegistry[A] {
  def register(factory: AdapterFactory[A]): AdapterRegistry[A] =
    copy(factories = factories :+ factory)
}
