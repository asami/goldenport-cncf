package org.goldenport.cncf.entity.view

import scala.collection.mutable
import org.goldenport.cncf.entity.view.Browser

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ViewSpace {
  private val _collections: mutable.Map[String, ViewCollection[_]] = mutable.Map.empty
  private val _browsers: mutable.Map[String, Browser[_]] = mutable.Map.empty

  def register[V](name: String, collection: ViewCollection[V]): Unit =
    if (_collections.contains(name))
      throw new IllegalStateException(s"ViewCollection already registered: $name")
    else {
      _collections.update(name, collection)
      _browsers.update(name, Browser.from(collection))
    }

  def register[V](name: String, collection: ViewCollection[V], browser: Browser[V]): Unit =
    if (_collections.contains(name) || _browsers.contains(name))
      throw new IllegalStateException(s"ViewCollection already registered: $name")
    else {
      _collections.update(name, collection)
      _browsers.update(name, browser)
    }

  def collection[V](name: String): ViewCollection[V] =
    _resolve(_collections, name, "ViewCollection")

  def collectionOption[V](name: String): Option[ViewCollection[V]] =
    _collections.get(name).map(_.asInstanceOf[ViewCollection[V]])

  def browser[V](name: String): Browser[V] =
    _resolve(_browsers, name, "Browser")

  def browserOption[V](name: String): Option[Browser[V]] =
    _browsers.get(name).map(_.asInstanceOf[Browser[V]])

  private def _resolve[A](
    map: mutable.Map[String, _],
    name: String,
    kind: String
  ): A =
    map.get(name)
      .map(_.asInstanceOf[A])
      .getOrElse(
        throw new IllegalStateException(s"$kind not found: $name")
      )
}
