package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 14, 2026
 *  version Mar. 30, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
// NOTE:
// The builder currently receives only EntityId.
// A future version may introduce AggregateBuilderContext
// to allow builders to access EntityCollection/ViewCollection
// for application-level joins.
trait AggregateBuilder[A] {
  def build(id: EntityId): Consequence[A]
}

trait ContextualAggregateBuilder[A] extends AggregateBuilder[A] {
  def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[A]

  override def build(id: EntityId): Consequence[A] =
    Consequence.operationInvalid("ExecutionContext is required for contextual aggregate builder")
}

final class AggregateCollection[A](
  builder: AggregateBuilder[A],
  queryfn: Query[?] => Consequence[Vector[A]] = _ => Consequence.notImplemented("AggregateCollection.query is not supported")
) extends Collection[A] {
  def resolve_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[A] =
    builder match {
      case m: ContextualAggregateBuilder[A @unchecked] => m.build_with_context(id)
      case _ => builder.build(id)
    }

  def resolve(id: EntityId): Consequence[A] =
    builder.build(id)

  def query_with_context(q: Query[?])(using ctx: ExecutionContext): Consequence[Vector[A]] =
    queryfn match {
      case m: ContextualAggregateQuery[A @unchecked] => m.query_with_context(q)
      case _ => queryfn(q)
    }

  def query(q: Query[?]): Consequence[Vector[A]] =
    queryfn(q)
}

trait ContextualAggregateQuery[A] extends (Query[?] => Consequence[Vector[A]]) {
  def query_with_context(q: Query[?])(using ctx: ExecutionContext): Consequence[Vector[A]]

  override def apply(q: Query[?]): Consequence[Vector[A]] =
    Consequence.operationInvalid("ExecutionContext is required for contextual aggregate query")
}
