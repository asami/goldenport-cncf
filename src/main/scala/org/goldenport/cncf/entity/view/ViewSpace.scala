package org.goldenport.cncf.entity.view

import scala.collection.mutable
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.TotalCountCapability
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.view.Browser

/*
 * @since   Mar. 16, 2026
 *  version Mar. 17, 2026
 *  version Apr.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class ViewSpace {
  private val _collections: mutable.Map[String, ViewCollection[_]] = mutable.Map.empty
  private val _named_collections: mutable.Map[(String, String), ViewCollection[_]] = mutable.Map.empty
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

  def registerReadSideView[V](
    name: String,
    policy: ViewCachePolicy
  )(
    builder: ViewBuilder[V],
    query: ContextualBrowserQuery[V],
    count: Option[ContextualBrowserCount] = None,
    totalCountCapabilityValue: TotalCountCapability = TotalCountCapability.Unsupported
  ): Unit = {
    val collection = new ViewCollection[V](
      builder,
      cachePolicy = Some(policy)
    )
    register(
      name,
      collection,
      Browser.from(collection, query, count, totalCountCapabilityValue)
    )
  }

  def registerNamedReadSideView[V](
    name: String,
    viewname: String,
    policy: ViewCachePolicy
  )(
    query: ContextualBrowserQuery[V],
    find: Option[ContextualBrowserFind[V]] = None,
    count: Option[ContextualBrowserCount] = None,
    totalCountCapabilityValue: TotalCountCapability = TotalCountCapability.Unsupported
  ): Unit = {
    val key = _view_key(name, viewname)
    if (_named_browsers.contains(key) || _named_collections.contains(key))
      throw new IllegalStateException(s"Browser already registered: ${name}:${viewname}")
    val collection = new ViewCollection[V](
      _contextual_builder(name, viewname, find),
      cachePolicy = Some(policy)
    )
    val browser = find match {
      case Some(f) => Browser.from(f, collection, query, count, totalCountCapabilityValue)
      case None => Browser.from(collection, query, count, totalCountCapabilityValue)
    }
    _named_collections.update(key, collection)
    _named_browsers.update(key, browser)
  }

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

  def findWithContext[V](
    name: String,
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[V] =
    browser[V](name).find_with_context(id)

  def findWithContext[V](
    name: String,
    viewname: String,
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[V] =
    browser[V](name, viewname).find_with_context(id)

  def queryWithContext[V](
    name: String,
    q: Query[_]
  )(using ctx: ExecutionContext): Consequence[Vector[V]] =
    browser[V](name).query_with_context(q)

  def queryWithContext[V](
    name: String,
    viewname: String,
    q: Query[_]
  )(using ctx: ExecutionContext): Consequence[Vector[V]] =
    browser[V](name, viewname).query_with_context(q)

  def countWithContext(
    name: String,
    q: Query[_]
  )(using ctx: ExecutionContext): Consequence[Int] =
    browser[Any](name).count_with_context(q)

  def countWithContext(
    name: String,
    viewname: String,
    q: Query[_]
  )(using ctx: ExecutionContext): Consequence[Int] =
    browser[Any](name, viewname).count_with_context(q)

  def invalidate(name: String): Unit =
    _collections.get(name).foreach(_.invalidateAll())
    _named_collections.foreach {
      case ((n, _), collection) if n == name => collection.invalidateAll()
      case _ =>
    }

  def invalidateAll(): Unit =
    _collections.values.foreach(_.invalidateAll())
    _named_collections.values.foreach(_.invalidateAll())

  private def _view_key(name: String, viewname: String): (String, String) =
    (name, Option(viewname).map(_.trim).getOrElse(""))

  private def _is_default_view_name(name: String): Boolean =
    Option(name).map(_.trim).forall(_.isEmpty)

  private def _contextual_builder[V](
    name: String,
    viewname: String,
    find: Option[ContextualBrowserFind[V]]
  ): ViewBuilder[V] =
    find match {
      case Some(f) =>
        new ContextualViewBuilder[V] {
          def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] =
            f.find_with_context(id)
        }
      case None =>
        new ViewBuilder[V] {
          def build(id: EntityId): Consequence[V] =
            Consequence.operationInvalid(s"${name}:${viewname} does not support direct materialization by id")
        }
    }

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
