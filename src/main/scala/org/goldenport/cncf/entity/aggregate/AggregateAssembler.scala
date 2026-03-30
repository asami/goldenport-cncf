package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Mar. 30, 2026
 * @version Mar. 30, 2026
 * @author  ASAMI, Tomoharu
 */
trait AggregateAssembler[A] {
  def create_from_record(record: Record): Consequence[A]
  def attach_member(
    aggregate: A,
    member_name: String,
    members: Vector[Any]
  ): Consequence[A]
}
