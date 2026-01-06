package org.goldenport.cncf.datastore

import org.goldenport.id.UniversalId
import org.goldenport.cncf.unitofwork.{CommitParticipant, CommitRecorder, PrepareResult, TransactionContext}

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait DataStore extends CommitParticipant {
  def create(id: UniversalId, record: DataStore.Record): Unit
  def load(id: UniversalId): Option[DataStore.Record]
  def store(id: UniversalId, record: DataStore.Record): Unit
  def update(id: UniversalId, changes: DataStore.Record): Unit
  def delete(id: UniversalId): Unit
}

object DataStore {
  type Record = Map[String, Any]

  def noop(
    recorder: CommitRecorder = CommitRecorder.noop
  ): DataStore =
    new NoopDataStore(recorder)

  def inMemory(
    recorder: CommitRecorder = CommitRecorder.noop
  ): DataStore =
    new InMemoryDataStore(recorder)

  def inMemorySelectable(
    recorder: CommitRecorder = CommitRecorder.noop
  ): SelectableDataStore =
    new InMemorySelectableDataStore(recorder)

  private final class NoopDataStore(
    recorder: CommitRecorder
  ) extends DataStore {
    def create(id: UniversalId, record: DataStore.Record): Unit = {}

    def load(id: UniversalId): Option[DataStore.Record] =
      None

    def store(id: UniversalId, record: DataStore.Record): Unit = {}

    def update(id: UniversalId, changes: DataStore.Record): Unit = {}

    def delete(id: UniversalId): Unit = {}

    def prepare(tx: TransactionContext): PrepareResult = {
      recorder.record("DataStore.prepare")
      PrepareResult.Prepared
    }

    def commit(tx: TransactionContext): Unit =
      recorder.record("DataStore.commit")

    def abort(tx: TransactionContext): Unit =
      recorder.record("DataStore.abort")
  }

  private final class InMemoryDataStore(
    recorder: CommitRecorder
  ) extends DataStore {
    private var _entries: Map[UniversalId, DataStore.Record] = Map.empty

    def create(id: UniversalId, record: DataStore.Record): Unit = {
      if (_entries.contains(id)) {
        throw new IllegalStateException(s"record already exists: ${id}")
      }
      _entries = _entries.updated(id, record)
    }

    def load(id: UniversalId): Option[DataStore.Record] =
      _entries.get(id)

    def store(id: UniversalId, record: DataStore.Record): Unit = {
      _entries = _entries.updated(id, record)
    }

    def update(id: UniversalId, changes: DataStore.Record): Unit = {
      _entries.get(id) match {
        case Some(existing) =>
          _entries = _entries.updated(id, existing ++ changes)
        case None =>
          throw new IllegalStateException(s"record not found: ${id}")
      }
    }

    def delete(id: UniversalId): Unit = {
      _entries = _entries - id
    }

    def prepare(tx: TransactionContext): PrepareResult = {
      recorder.record("DataStore.prepare")
      PrepareResult.Prepared
    }

    def commit(tx: TransactionContext): Unit =
      recorder.record("DataStore.commit")

    def abort(tx: TransactionContext): Unit =
      recorder.record("DataStore.abort")
  }

  private final class InMemorySelectableDataStore(
    recorder: CommitRecorder
  ) extends SelectableDataStore {
    private var _entries: Map[UniversalId, DataStore.Record] = Map.empty
    private var _order: Vector[UniversalId] = Vector.empty

    def create(id: UniversalId, record: DataStore.Record): Unit = {
      if (_entries.contains(id)) {
        throw new IllegalStateException(s"record already exists: ${id}")
      }
      _entries = _entries.updated(id, record)
      _order = _order :+ id
    }

    def load(id: UniversalId): Option[DataStore.Record] =
      _entries.get(id)

    def store(id: UniversalId, record: DataStore.Record): Unit = {
      val exists = _entries.contains(id)
      _entries = _entries.updated(id, record)
      if (!exists) {
        _order = _order :+ id
      }
    }

    def update(id: UniversalId, changes: DataStore.Record): Unit = {
      _entries.get(id) match {
        case Some(existing) =>
          _entries = _entries.updated(id, existing ++ changes)
        case None =>
          throw new IllegalStateException(s"record not found: ${id}")
      }
    }

    def delete(id: UniversalId): Unit = {
      _entries = _entries - id
      _order = _order.filterNot(_ == id)
    }

    def select(directive: QueryDirective): SelectResult = {
      val ordered = _order.flatMap(_entries.get)
      val projected = directive.projection match {
        case QueryProjection.All =>
          ordered
        case QueryProjection.Fields(names) =>
          val keyset = names.toSet
          ordered.map(_.filter { case (k, _) => keyset.contains(k) })
      }
      val sorted = directive.order match {
        case QueryOrder.None =>
          projected
        case QueryOrder.By(field, OrderDirection.Asc) =>
          projected.sortBy(_.get(field).map(_.toString).getOrElse(""))
        case QueryOrder.By(field, OrderDirection.Desc) =>
          projected.sortBy(_.get(field).map(_.toString).getOrElse(""))(Ordering[String].reverse)
      }
      directive.limit match {
        case QueryLimit.Unbounded =>
          SelectResult(sorted, ResultRange.Exact, None)
        case QueryLimit.Limit(n) =>
          SelectResult(sorted.take(n), ResultRange.Limited(n), None)
      }
    }

    def prepare(tx: TransactionContext): PrepareResult = {
      recorder.record("DataStore.prepare")
      PrepareResult.Prepared
    }

    def commit(tx: TransactionContext): Unit =
      recorder.record("DataStore.commit")

    def abort(tx: TransactionContext): Unit =
      recorder.record("DataStore.abort")
  }
}

trait SelectableDataStore extends DataStore {
  def select(directive: QueryDirective): SelectResult
}

final case class QueryDirective(
  query: Query,
  projection: QueryProjection = QueryProjection.All,
  order: QueryOrder = QueryOrder.None,
  limit: QueryLimit = QueryLimit.Unbounded
)

sealed trait Query
object Query {
  case object Empty extends Query
}

sealed trait QueryProjection
object QueryProjection {
  case object All extends QueryProjection
  final case class Fields(names: Vector[String]) extends QueryProjection
}

sealed trait QueryOrder
object QueryOrder {
  case object None extends QueryOrder
  final case class By(field: String, direction: OrderDirection) extends QueryOrder
}

sealed trait OrderDirection
object OrderDirection {
  case object Asc extends OrderDirection
  case object Desc extends OrderDirection
}

sealed trait QueryLimit
object QueryLimit {
  case object Unbounded extends QueryLimit
  final case class Limit(value: Int) extends QueryLimit
}

final case class SelectResult(
  records: Vector[DataStore.Record],
  range: ResultRange,
  cursor: Option[Cursor] = None
)

sealed trait ResultRange
object ResultRange {
  case object Exact extends ResultRange
  final case class Limited(limit: Int) extends ResultRange
}

final case class Cursor(value: String)
