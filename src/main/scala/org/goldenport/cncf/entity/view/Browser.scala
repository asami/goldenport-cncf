package org.goldenport.cncf.entity.view

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.datastore.TotalCountCapability
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 15, 2026
 *  version Mar. 24, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
trait Browser[V] {
  // Runtime paths should prefer *_with_context.
  // Context-free methods are for simple manual implementations, tests, and fixed-capability adapters.
  def find(id: EntityId): Consequence[V]
  def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] =
    find(id)
  def query(q: Query[_]): Consequence[Vector[V]]
  def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[V]] =
    query(q)
  def totalCountCapability: TotalCountCapability = TotalCountCapability.Unsupported
  def totalCountCapabilityWithContext(using ctx: ExecutionContext): Consequence[TotalCountCapability] =
    Consequence.success(totalCountCapability)
  def count_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Int] =
    totalCountCapabilityWithContext.flatMap {
      case m if m.supportsTotalCount => count(q)
      case m => Consequence.operationInvalid(s"View total count is not supported: ${m}")
    }
  def count(q: Query[_]): Consequence[Int] =
    Consequence.operationInvalid(s"View total count is not supported: ${totalCountCapability}")
}

object Browser {
  def from[V](collection: Collection[V]): Browser[V] =
    new Browser[V] {
      def find(id: EntityId): Consequence[V] =
        collection.resolve(id)

      override def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] =
        collection match {
          case m: ViewCollection[V @unchecked] => m.resolve_with_context(id)
          case _ => find(id)
        }

      def query(q: Query[_]): Consequence[Vector[V]] =
        Consequence.notImplemented("Browser.query is not supported")
    }

  def from[V](
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Browser[V] =
    from(collection, queryfn, None, TotalCountCapability.Unsupported)

  def from[V](
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]],
    countfn: Option[Query[_] => Consequence[Int]],
    totalCountCapabilityValue: TotalCountCapability
  ): Browser[V] =
    from(collection, queryfn, countfn, totalCountCapabilityValue, None)

  def from[V](
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]],
    countfn: Option[Query[_] => Consequence[Int]],
    totalCountCapabilityValue: TotalCountCapability,
    totalCountCapabilityWithContextFn: Option[ExecutionContext => Consequence[TotalCountCapability]]
  ): Browser[V] =
    new Browser[V] {
      def find(id: EntityId): Consequence[V] =
        collection.resolve(id)

      override def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] =
        collection match {
          case m: ViewCollection[V @unchecked] => m.resolve_with_context(id)
          case _ => find(id)
        }

      def query(q: Query[_]): Consequence[Vector[V]] =
        collection match {
          case m: ViewCollection[V @unchecked] => m.query(q)(queryfn)
          case _ => queryfn(q)
        }

      override def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[V]] =
        queryfn match {
          case m: ContextualBrowserQuery[V @unchecked] =>
            collection match {
              case vc: ViewCollection[V @unchecked] => vc.query(q)(qq => m.query_with_context(qq))
              case _ => m.query_with_context(q)
            }
          case _ => query(q)
        }

      override def totalCountCapability: TotalCountCapability =
        if (countfn.isDefined) totalCountCapabilityValue else TotalCountCapability.Unsupported

      override def totalCountCapabilityWithContext(using ctx: ExecutionContext): Consequence[TotalCountCapability] =
        totalCountCapabilityWithContextFn.map(_(ctx)).getOrElse(super.totalCountCapabilityWithContext)

      override def count_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Int] =
        totalCountCapabilityWithContext.flatMap {
          case m if m.supportsTotalCount =>
            countfn match {
              case Some(m: ContextualBrowserCount @unchecked) => m.count_with_context(q)
              case Some(f) => f(q)
              case None => super.count(q)
            }
          case m =>
            Consequence.operationInvalid(s"View total count is not supported: ${m}")
        }

      override def count(q: Query[_]): Consequence[Int] =
        if (!totalCountCapability.supportsTotalCount)
          Consequence.operationInvalid(s"View total count is not supported: ${totalCountCapability}")
        else
          countfn.map(_(q)).getOrElse(super.count(q))
    }

  def from[V](
    loadfn: EntityId => Consequence[V],
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Browser[V] =
    from(loadfn, collection, queryfn, None, TotalCountCapability.Unsupported)

  def from[V](
    loadfn: EntityId => Consequence[V],
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]],
    countfn: Option[Query[_] => Consequence[Int]],
    totalCountCapabilityValue: TotalCountCapability
  ): Browser[V] =
    from(loadfn, collection, queryfn, countfn, totalCountCapabilityValue, None)

  def from[V](
    loadfn: EntityId => Consequence[V],
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]],
    countfn: Option[Query[_] => Consequence[Int]],
    totalCountCapabilityValue: TotalCountCapability,
    totalCountCapabilityWithContextFn: Option[ExecutionContext => Consequence[TotalCountCapability]]
  ): Browser[V] =
    new Browser[V] {
      def find(id: EntityId): Consequence[V] =
        loadfn(id)

      override def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] =
        loadfn match {
          case m: ContextualBrowserFind[V @unchecked] => m.find_with_context(id)
          case _ => find(id)
        }

      def query(q: Query[_]): Consequence[Vector[V]] =
        collection match {
          case m: ViewCollection[V @unchecked] => m.query(q)(queryfn)
          case _ => queryfn(q)
        }

      override def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[V]] =
        queryfn match {
          case m: ContextualBrowserQuery[V @unchecked] =>
            collection match {
              case vc: ViewCollection[V @unchecked] => vc.query(q)(qq => m.query_with_context(qq))
              case _ => m.query_with_context(q)
            }
          case _ => query(q)
        }

      override def totalCountCapability: TotalCountCapability =
        if (countfn.isDefined) totalCountCapabilityValue else TotalCountCapability.Unsupported

      override def totalCountCapabilityWithContext(using ctx: ExecutionContext): Consequence[TotalCountCapability] =
        totalCountCapabilityWithContextFn.map(_(ctx)).getOrElse(super.totalCountCapabilityWithContext)

      override def count_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Int] =
        totalCountCapabilityWithContext.flatMap {
          case m if m.supportsTotalCount =>
            countfn match {
              case Some(m: ContextualBrowserCount @unchecked) => m.count_with_context(q)
              case Some(f) => f(q)
              case None => super.count(q)
            }
          case m =>
            Consequence.operationInvalid(s"View total count is not supported: ${m}")
        }

      override def count(q: Query[_]): Consequence[Int] =
        if (!totalCountCapability.supportsTotalCount)
          Consequence.operationInvalid(s"View total count is not supported: ${totalCountCapability}")
        else
          countfn.map(_(q)).getOrElse(super.count(q))
    }
}

trait ContextualBrowserFind[V] extends (EntityId => Consequence[V]) {
  // Generated/default browsers use this to prevent accidental context loss.
  def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V]

  override def apply(id: EntityId): Consequence[V] =
    Consequence.operationInvalid("ExecutionContext is required for contextual browser find")
}

trait ContextualBrowserQuery[V] extends (Query[_] => Consequence[Vector[V]]) {
  // Generated/default browsers use this to preserve runtime EntityStore/DataStore routing.
  def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[V]]

  override def apply(q: Query[_]): Consequence[Vector[V]] =
    Consequence.operationInvalid("ExecutionContext is required for contextual browser query")
}

trait ContextualBrowserCount extends (Query[_] => Consequence[Int]) {
  // Generated/default browsers use this so total count can be gated by runtime capability.
  def count_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Int]

  override def apply(q: Query[_]): Consequence[Int] =
    Consequence.operationInvalid("ExecutionContext is required for contextual browser count")
}
