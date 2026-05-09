package org.goldenport.cncf.entity.aggregate

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.aggregate.Repository
import org.goldenport.cncf.observability.CallTreeValueSummary

/*
 * @since   Mar. 16, 2026
 *  version Mar. 30, 2026
 * @version May. 10, 2026
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
  )(using ctx: ExecutionContext): Consequence[Vector[A]] =
    query_with_context[A](collectionname, q)

  def query_with_context[A](
    collectionname: String,
    q: Query[?]
  )(using ctx: ExecutionContext): Consequence[Vector[A]] =
    _with_calltree("space:aggregate:query", _aggregate_space_attributes("query", collectionname)) {
      collection[A](collectionname).query_with_context(q)
    }

  def query[A](
    collectionname: String,
    q: Query[?]
  ): Consequence[Vector[A]] =
    repository[A](collectionname).query(q)

  def resolve[A](id: EntityId)(using ctx: ExecutionContext): Consequence[A] =
    resolve_with_context[A](id)

  def resolve_with_context[A](id: EntityId)(using ctx: ExecutionContext): Consequence[A] =
    _with_calltree("space:aggregate:resolve", _aggregate_space_attributes("resolve", id.collection.name) + ("entity_id" -> id.print)) {
      collection[A](id.collection.name).resolve_with_context(id)
    }

  def resolve[A](id: EntityId): Consequence[A] =
    collection[A](id.collection.name).resolve(id)

  // Keep failure semantics centralized:
  // - not found => Success(None)
  // - other failures (e.g. I/O) => Failure
  def resolveOption[A](id: EntityId)(using ctx: ExecutionContext): Consequence[Option[A]] =
    _with_calltree("space:aggregate:resolve-option", _aggregate_space_attributes("resolve-option", id.collection.name) + ("entity_id" -> id.print)) {
      collectionOption[A](id.collection.name) match {
        case None =>
          Consequence.success(None)
        case Some(c) =>
          c.resolve_with_context(id) match {
            case Consequence.Success(value) =>
              Consequence.success(Some(value))
            case Consequence.Failure(conclusion) if _is_not_found(conclusion) =>
              Consequence.success(None)
            case Consequence.Failure(conclusion) =>
              Consequence.Failure(conclusion)
          }
      }
    }

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

  private def _with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "space"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
          case other =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other))
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave()
          throw e
      }
    } else {
      body
    }
  }

  private def _aggregate_space_attributes(
    operation: String,
    collectionname: String
  ): Map[String, String] =
    Map(
      "space" -> "aggregate",
      "operation" -> operation,
      "collection" -> collectionname
    )

  private def _is_not_found(conclusion: Conclusion): Boolean =
    conclusion.show.toLowerCase.contains("not found")
}
