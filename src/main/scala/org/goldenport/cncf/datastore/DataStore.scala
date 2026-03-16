package org.goldenport.cncf.datastore

import scala.collection.immutable.VectorMap
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.text.Presentable
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{CommitParticipant, CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.cncf.datatype.EntityCollectionId
import scala.util.control.NonFatal

/*
 * @since   Jan.  6, 2026
 *  version Jan. 10, 2026
 *  version Feb. 25, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
trait DataStore extends CommitParticipant {
  import DataStore.*

  def isAccept(cid: CollectionId): Boolean

  def create(
    collection: CollectionId,
    id: EntryId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit]
  def load(
    collection: CollectionId,
    id: EntryId
  )(using ctx: ExecutionContext): Consequence[Option[Record]]
  def save(
    collection: CollectionId,
    id: EntryId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit]
  def update(
    collection: CollectionId,
    id: EntryId,
    changes: Record
  )(using ctx: ExecutionContext): Consequence[Unit]
  def delete(
    collection: CollectionId,
    id: EntryId
  )(using ctx: ExecutionContext): Consequence[Unit]

  protected final def record_prepare(
    recorder: CommitRecorder
  ): PrepareResult = {
    recorder.record("DataStore.prepare")
    PrepareResult.Prepared
  }

  protected final def record_commit(
    recorder: CommitRecorder
  ): Unit =
    recorder.record("DataStore.commit")

  protected final def record_abort(
    recorder: CommitRecorder
  ): Unit =
    recorder.record("DataStore.abort")
}

object DataStore {
  sealed trait CollectionId extends Presentable {
    def collectionName: String
  }
  object CollectionId {
    case class Instance(name: String) extends
        UniversalId("sys", "sys", "datastore", name, Instant.EPOCH)
        with CollectionId {
      def collectionName = name
    }
    case class EntityStore(id: EntityCollectionId) extends CollectionId {
      def collectionName = id.name
      def print = id.print
    }

    def apply(name: String): CollectionId = Instance(name)
  }

  sealed trait EntryId extends Presentable
  object EntryId {
    def apply(uid: UniversalId): EntryId = UniversalEntryId(uid)

    def parse(p: String): Consequence[EntryId] =
      Consequence(_parse_universal_id(p).getOrElse(StringEntryId(p)))

    private def _parse_universal_id(p: String): Option[UniversalEntryId] = {
      val parts = p.split("-").toVector
      val (major, minor, kind, subkindopt, timestampstr, entropy) = parts.length match {
        case 5 =>
          (parts(0), parts(1), parts(2), Option.empty[String], parts(3), parts(4))
        case 6 =>
          (parts(0), parts(1), parts(2), Some(parts(3)), parts(4), parts(5))
        case _ =>
          return None
      }
      try {
        val timestamp = Instant.ofEpochMilli(timestampstr.toLong)
        val parsedparts = UniversalId.Parts(
          major,
          minor,
          kind,
          subkindopt,
          timestamp,
          entropy
        )
        val uid = subkindopt match {
          case Some(sk) =>
            new UniversalId(major, minor, kind, sk) {
              override def parts = parsedparts
              override def value = parsedparts.value
            }
          case None =>
            new UniversalId(major, minor, kind) {
              override def parts = parsedparts
              override def value = parsedparts.value
            }
        }
        Some(UniversalEntryId(uid))
      } catch {
        case NonFatal(_) => None
      }
    }
  }
  case class StringEntryId(id: String) extends EntryId {
    def print = Presentable.print(id)
  }
  case class UniversalEntryId(id: UniversalId) extends EntryId {
    def print = id.print
  }
  case class DataStoreEntryId(
    major: String,
    minor: String,
    collection: String
  ) extends UniversalId(major, minor, "datastore", collection) with EntryId

  def noop(
    recorder: CommitRecorder = CommitRecorder.noop
  ): DataStore =
    new NoopDataStore(recorder)

  def inMemory(
    recorder: CommitRecorder = CommitRecorder.noop
  ): DataStore =
    new InMemoryDataStore(recorder)

  def inMemorySearchable(
    recorder: CommitRecorder = CommitRecorder.noop
  ): SearchableDataStore =
    new InMemorySearchableDataStore(recorder)

  private final class NoopDataStore(
    recorder: CommitRecorder
  ) extends DataStore {
    def isAccept(cid: CollectionId): Boolean = true

    def create(
      collection: CollectionId,
      id: EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] = Consequence.unit

    def load(
      collection: CollectionId,
      id: EntryId
    )(using ctx: ExecutionContext): Consequence[Option[Record]] =
      Consequence(None)

    def save(
      collection: CollectionId,
      id: EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] = Consequence.unit

    def update(
      collection: CollectionId,
      id: EntryId,
      changes: Record
    )(using ctx: ExecutionContext): Consequence[Unit] = Consequence.unit

    def delete(
      collection: CollectionId,
      id: EntryId
    )(using ctx: ExecutionContext): Consequence[Unit] = Consequence.unit

    def prepare(tx: TransactionContext): PrepareResult =
      record_prepare(recorder)

    def commit(tx: TransactionContext): Unit =
      record_commit(recorder)

    def abort(tx: TransactionContext): Unit =
      record_abort(recorder)
  }

  class InMemoryDataStore(
    recorder: CommitRecorder
  ) extends DataStore {
    def isAccept(cid: CollectionId): Boolean = true

    private var _collections: VectorMap[CollectionId, InMemoryDataStore.Collection] = VectorMap.empty

    private def _ensure_collection(collection: CollectionId): Consequence[InMemoryDataStore.Collection] =
      _collections.get(collection) match {
        case Some(c) =>
          Consequence.success(c)
        case None =>
          val c = new InMemoryDataStore.Collection(collection)
          _collections = _collections.updated(collection, c)
          Consequence.success(c)
      }

    protected def take_collection(collection: CollectionId): Consequence[InMemoryDataStore.Collection] =
      _collections.get(collection) match {
        case Some(c) =>
          Consequence.success(c)
        case None =>
          Consequence.DataStoreNotFound(collection.print)
      }

    def create(
      collection: CollectionId,
      id: EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      _ensure_collection(collection).flatMap(_.create(id, record))

    def load(
      collection: CollectionId,
      id: EntryId
    )(using ctx: ExecutionContext): Consequence[Option[Record]] =
      take_collection(collection).flatMap(_.load(id))

    def save(
      collection: CollectionId,
      id: EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      take_collection(collection).flatMap(_.save(id, record))

    def update(
      collection: CollectionId,
      id: EntryId,
      changes: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      take_collection(collection).flatMap(_.update(id, changes))

    def delete(
      collection: CollectionId,
      id: EntryId
    )(using ctx: ExecutionContext): Consequence[Unit] =
      take_collection(collection).flatMap(_.delete(id))

    def prepare(tx: TransactionContext): PrepareResult =
      record_prepare(recorder)

    def commit(tx: TransactionContext): Unit =
      record_commit(recorder)

    def abort(tx: TransactionContext): Unit =
      record_abort(recorder)
  }
  object InMemoryDataStore {
    class Collection(val id: CollectionId) {
      def collectionName = id.collectionName

      private var _entries: VectorMap[String, Record] = VectorMap.empty

      protected def to_entry_key(p: EntryId): Consequence[String] =
        Consequence.success(p.print)

      def create(id: EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
        for {
          key <- to_entry_key(id)
          _ <- _check_duplicate(key)
          _ <- _create_entry(key, record)
        } yield {}

      private def _check_duplicate(key: String): Consequence[Unit] =
        if (_entries.contains(key))
          Consequence.DataStoreDuplicate(key)
        else
          Consequence.unit

      private def _create_entry(key: String, rec: Record): Consequence[Unit] =
        Consequence {
          _entries = _entries.updated(key, rec)
        }

      def load(id: EntryId): Consequence[Option[Record]] =
        to_entry_key(id).map(_entries.get)

      def save(id: EntryId, record: Record): Consequence[Unit] =
        to_entry_key(id).map { key =>
          _entries = _entries.updated(key, record)
        }

      def update(id: EntryId, changes: Record): Consequence[Unit] = {
        for {
          key <- to_entry_key(id)
          _ <- {
            _entries.get(key) match {
              case Some(existing) => Consequence {
                _entries = _entries.updated(key, existing ++ changes)
              }
              case None =>
                Consequence.DataStoreNotFound(key)
            }
          }
        } yield ()
      }

      def delete(id: EntryId): Consequence[Unit] = {
        for {
          key <- to_entry_key(id)
        } yield _entries = _entries - key
      }

      def search(directive: QueryDirective): Consequence[SearchResult] = Consequence {
        val ordered = _entries.values.toVector
        val projected = directive.projection match {
          case QueryProjection.All =>
            ordered
          case QueryProjection.Fields(names) =>
            val keyset = names.toSet
            ordered.map(_.filterKeys(keyset))
        }
        val sorted = directive.order match {
          case QueryOrder.None =>
            projected
          case QueryOrder.By(field, OrderDirection.Asc) =>
            projected.sortBy(_.getString(field).getOrElse(""))
          case QueryOrder.By(field, OrderDirection.Desc) =>
            projected.sortBy(_.getString(field).getOrElse(""))(Ordering[String].reverse)
        }
        directive.limit match {
          case QueryLimit.Unbounded =>
            SearchResult(sorted, ResultRange.Exact, None)
          case QueryLimit.Limit(n) =>
            SearchResult(sorted.take(n), ResultRange.Limited(n), None)
        }
      }
    }
  }

  final class InMemorySearchableDataStore(
    recorder: CommitRecorder
  ) extends InMemoryDataStore(recorder) with SearchableDataStore {
    // private var _entries: Map[UniversalId, Record] = Map.empty
    // private var _order: Vector[UniversalId] = Vector.empty

    // def create(id: UniversalId, record: Record): Unit = {
    //   if (_entries.contains(id)) {
    //     throw new IllegalStateException(s"record already exists: ${id}")
    //   }
    //   _entries = _entries.updated(id, record)
    //   _order = _order :+ id
    // }

    // def load(id: UniversalId): Option[Record] =
    //   _entries.get(id)

    // def store(id: UniversalId, record: Record): Unit = {
    //   val exists = _entries.contains(id)
    //   _entries = _entries.updated(id, record)
    //   if (!exists) {
    //     _order = _order :+ id
    //   }
    // }

    // def update(id: UniversalId, changes: Record): Unit = {
    //   _entries.get(id) match {
    //     case Some(existing) =>
    //       _entries = _entries.updated(id, existing ++ changes)
    //     case None =>
    //       throw new IllegalStateException(s"record not found: ${id}")
    //   }
    // }

    // def delete(id: UniversalId): Unit = {
    //   _entries = _entries - id
    //   _order = _order.filterNot(_ == id)
    // }

    def search(
      collection: CollectionId,
      directive: QueryDirective
    ): Consequence[SearchResult] = 
      take_collection(collection).flatMap(_.search(directive))

  //   def prepare(tx: TransactionContext): PrepareResult = {
  //     recorder.record("DataStore.prepare")
  //     PrepareResult.Prepared
  //   }

  //   def commit(tx: TransactionContext): Unit =
  //     recorder.record("DataStore.commit")

  //   def abort(tx: TransactionContext): Unit =
  //     recorder.record("DataStore.abort")
  }
}

trait SearchableDataStore extends DataStore {
  def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult]
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

final case class SearchResult(
  records: Vector[Record],
  range: ResultRange,
  cursor: Option[Cursor] = None
)

sealed trait ResultRange
object ResultRange {
  case object Exact extends ResultRange
  final case class Limited(limit: Int) extends ResultRange
}

final case class Cursor(value: String)
