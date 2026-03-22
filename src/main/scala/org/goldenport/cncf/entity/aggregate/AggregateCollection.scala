package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.goldenport.model.datatype.EntityId
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 14, 2026
 * @version Mar. 17, 2026
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

final class AggregateCollection[A](
  builder: AggregateBuilder[A],
  queryfn: Query[?] => Consequence[Vector[A]] = _ => Consequence.failure("AggregateCollection.query is not supported")
) extends Collection[A] {
  def resolve(id: EntityId): Consequence[A] =
    builder.build(id)

  def query(q: Query[?]): Consequence[Vector[A]] =
    queryfn(q)
}
