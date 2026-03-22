package org.goldenport.cncf.entity.aggregate

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.model.datatype.EntityId
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.aggregate.Repository

/*
 * @since   Mar. 16, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateSpace {
  private val _collections: mutable.Map[String, AggregateCollection[_]] = mutable.Map.empty
  private val _repositories: mutable.Map[String, Repository[_]] = mutable.Map.empty

  def register[A](name: String, collection: AggregateCollection[A]): Unit =
    if (_collections.contains(name))
      throw new IllegalStateException(s"AggregateCollection already registered: $name")
    else {
      _collections.update(name, collection)
      _repositories.update(name, Repository.from(collection))
    }

  def collection[A](name: String): AggregateCollection[A] =
    _resolve(_collections, name, "AggregateCollection")

  def collectionOption[A](name: String): Option[AggregateCollection[A]] =
    _collections.get(name).map(_.asInstanceOf[AggregateCollection[A]])

  def repository[A](name: String): Repository[A] =
    _resolve(_repositories, name, "Repository")

  def repositoryOption[A](name: String): Option[Repository[A]] =
    _repositories.get(name).map(_.asInstanceOf[Repository[A]])

  def query[A](
    collectionname: String,
    q: Query[?]
  ): Consequence[Vector[A]] =
    repository[A](collectionname).query(q)

  def resolve[A](id: EntityId): Consequence[A] =
    collection[A](id.collection.name).resolve(id)

  // Keep failure semantics centralized:
  // - not found => Success(None)
  // - other failures (e.g. I/O) => Failure
  def resolveOption[A](id: EntityId): Consequence[Option[A]] =
    collectionOption[A](id.collection.name) match {
      case None =>
        Consequence.success(None)
      case Some(c) =>
        c.resolve(id) match {
          case Consequence.Success(value) =>
            Consequence.success(Some(value))
          case Consequence.Failure(conclusion) if _is_not_found(conclusion) =>
            Consequence.success(None)
          case Consequence.Failure(conclusion) =>
            Consequence.Failure(conclusion)
        }
    }

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

  private def _is_not_found(conclusion: Conclusion): Boolean =
    conclusion.show.toLowerCase.contains("not found")
}
