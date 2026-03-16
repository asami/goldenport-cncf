package org.goldenport.cncf.entity.view

import scala.collection.mutable
import org.goldenport.cncf.entity.view.Browser

/*
 * @since   Mar. 16, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ViewSpace {
  private val _collections: mutable.Map[String, ViewCollection[_]] = mutable.Map.empty
  private val _browsers: mutable.Map[String, Browser[_]] = mutable.Map.empty
  private val _named_browsers: mutable.Map[(String, String), Browser[_]] = mutable.Map.empty

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

  def register[V](name: String, viewname: String, browser: Browser[V]): Unit =
    registerView(name, viewname, browser)

  def registerView[V](name: String, viewname: String, browser: Browser[V]): Unit = {
    if (_collections.contains(name) == false)
      throw new IllegalStateException(s"ViewCollection not found: $name")
    else {
      val key = _view_key(name, viewname)
      if (_named_browsers.contains(key))
        throw new IllegalStateException(s"Browser already registered: ${name}:${viewname}")
      _named_browsers.update(key, browser)
    }
  }

  def collection[V](name: String): ViewCollection[V] =
    _resolve(_collections, name, "ViewCollection")

  def collectionOption[V](name: String): Option[ViewCollection[V]] =
    _collections.get(name).map(_.asInstanceOf[ViewCollection[V]])

  def browser[V](name: String): Browser[V] =
    _resolve(_browsers, name, "Browser")

  def browser[V](name: String, viewname: String): Browser[V] =
    if (_is_default_view_name(viewname))
      browser(name)
    else
      _resolve(_named_browsers, _view_key(name, viewname), "Browser")

  def browserOption[V](name: String): Option[Browser[V]] =
    _browsers.get(name).map(_.asInstanceOf[Browser[V]])

  def browserOption[V](name: String, viewname: String): Option[Browser[V]] =
    if (_is_default_view_name(viewname))
      browserOption(name)
    else
      _named_browsers.get(_view_key(name, viewname)).map(_.asInstanceOf[Browser[V]])

  private def _view_key(name: String, viewname: String): (String, String) =
    (name, Option(viewname).map(_.trim).getOrElse(""))

  private def _is_default_view_name(name: String): Boolean =
    Option(name).map(_.trim).forall(_.isEmpty)

  private def _resolve[K, A](
    map: mutable.Map[K, _],
    key: K,
    kind: String
  ): A =
    map.get(key)
      .map(_.asInstanceOf[A])
      .getOrElse(
        throw new IllegalStateException(s"$kind not found: $key")
      )
}
