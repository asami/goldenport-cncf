package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait AdapterRegistry[A] {
  def register(factory: AdapterFactory[A]): AdapterRegistry[A]
  def factories: Vector[AdapterFactory[A]]
}

object AdapterRegistry {
  def empty[A]: AdapterRegistry[A] =
    InMemoryAdapterRegistry(Vector.empty)
}
