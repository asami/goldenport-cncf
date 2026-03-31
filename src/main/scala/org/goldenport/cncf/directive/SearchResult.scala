package org.goldenport.cncf.directive

import org.goldenport.record.Record
import org.goldenport.record.Recordable
import org.goldenport.text.Presentable

/*
 * @since   Feb. 22, 2026
 * @version Mar. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SearchResult[T](
  query: Query[?],
  data: Vector[T] = Vector.empty,
  totalCount: Option[Int] = None,
  offset: Option[Int] = None,
  limit: Option[Int] = None,
  fetchedCount: Int = 0
) extends Recordable {
  def size: Int = data.size
  def isEmpty: Boolean = data.isEmpty
  def nonEmpty: Boolean = data.nonEmpty

  def toRecord(): Record =
    Record.dataAuto(
      "query" -> _value(query),
      "data" -> data.map(_value),
      "totalCount" -> totalCount,
      "offset" -> offset,
      "limit" -> limit,
      "fetchedCount" -> fetchedCount
    )

  private def _value(p: Any): Any = p match {
    case null => null
    case m: Record => m
    case m: Recordable => m.toRecord()
    case m: Iterable[?] => m.iterator.map(_value).toVector
    case m: Array[?] => m.toVector.map(_value)
    case m: Option[?] => m.map(_value)
    case m: Presentable => m.print
    case other => other
  }
}

object SearchResult {
  def empty[T](query: Query[?]): SearchResult[T] =
    SearchResult(query, fetchedCount = 0)
}
