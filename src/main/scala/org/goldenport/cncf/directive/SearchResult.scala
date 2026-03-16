package org.goldenport.cncf.directive

/*
 * @since   Feb. 22, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SearchResult[T](
  query: Query[?],
  data: Vector[T] = Vector.empty,
  totalCount: Option[Int] = None,
  offset: Option[Int] = None,
  limit: Option[Int] = None,
  fetchedCount: Int = 0
) {
  def size: Int = data.size
  def isEmpty: Boolean = data.isEmpty
  def nonEmpty: Boolean = data.nonEmpty
}

object SearchResult {
  def empty[T](query: Query[?]): SearchResult[T] =
    SearchResult(query, fetchedCount = 0)
}
