package org.goldenport.cncf.datastore.sql

import java.sql.Connection
import javax.sql.DataSource
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.goldenport.Consequence
import org.goldenport.text.Presentable
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}

/*
 * @since   Mar. 12, 2026
 * @version Mar. 12, 2026
 * @author  ASAMI, Tomoharu
 */
class SqlDataStore(
  dialect: SqlDialectDriver,
  datasource: DataSource,
  recorder: CommitRecorder = CommitRecorder.noop
) extends DataStore {
  import DataStore.*

  def isAccept(cid: CollectionId): Boolean = true

  def create(
    collection: CollectionId,
    id: EntryId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      val cols = _record_columns(record)
      for {
        _ <- _ensure_table(conn, collection, cols)
        exists <- _exists(conn, collection, id)
        _ <- if (exists) Consequence.DataStoreDuplicate(id.print) else _insert(conn, collection, id, cols)
      } yield ()
    }

  def load(
    collection: CollectionId,
    id: EntryId
  )(using ctx: ExecutionContext): Consequence[Option[Record]] =
    _with_connection { conn =>
      _table_exists(conn, collection).flatMap { exists =>
        if (exists)
          _select(conn, collection, id)
        else
          Consequence.success(None)
      }
    }

  def save(
    collection: CollectionId,
    id: EntryId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      val cols = _record_columns(record)
      for {
        _ <- _ensure_table(conn, collection, cols)
        exists <- _exists(conn, collection, id)
        _ <- if (exists) _update(conn, collection, id, cols) else _insert(conn, collection, id, cols)
      } yield ()
    }

  def update(
    collection: CollectionId,
    id: EntryId,
    changes: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      val cols = _record_columns(changes)
      for {
        _ <- _ensure_table(conn, collection, cols)
        exists <- _exists(conn, collection, id)
        _ <- if (exists) _update(conn, collection, id, cols) else Consequence.DataStoreNotFound(id.print)
      } yield ()
    }

  def delete(
    collection: CollectionId,
    id: EntryId
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      _table_exists(conn, collection).flatMap { exists =>
        if (exists) _delete(conn, collection, id) else Consequence.unit
      }
    }

  def prepare(tx: TransactionContext): PrepareResult =
    record_prepare(recorder)

  def commit(tx: TransactionContext): Unit =
    record_commit(recorder)

  def abort(tx: TransactionContext): Unit =
    record_abort(recorder)

  private def _with_connection[A](
    f: Connection => Consequence[A]
  ): Consequence[A] =
    Consequence {
      val conn = datasource.getConnection()
      try {
        f(conn)
      } finally {
        conn.close()
      }
    }.flatMap(identity)

  private def _table_name(collection: CollectionId): String =
    collection.collectionName

  private def _record_columns(
    record: Record
  ): Vector[(String, String)] =
    record.asMap.toVector.collect {
      case (k, v) if k != "id" => k -> Presentable.print(v)
    }

  private def _table_exists(
    conn: Connection,
    collection: CollectionId
  ): Consequence[Boolean] =
    Consequence {
      val sql = dialect.table_exists_sql(_table_name(collection))
      val stmt = conn.createStatement()
      try {
        val rs = stmt.executeQuery(sql)
        try rs.next()
        finally rs.close()
      } finally {
        stmt.close()
      }
    }

  private def _existing_columns(
    conn: Connection,
    collection: CollectionId
  ): Consequence[Set[String]] =
    Consequence {
      val sql = dialect.table_columns_sql(_table_name(collection))
      val stmt = conn.createStatement()
      try {
        val rs = stmt.executeQuery(sql)
        val buf = scala.collection.mutable.Set.empty[String]
        try {
          while (rs.next()) {
            buf += rs.getString(dialect.table_columns_name_column)
          }
        } finally {
          rs.close()
        }
        buf.toSet
      } finally {
        stmt.close()
      }
    }

  private def _ensure_table(
    conn: Connection,
    collection: CollectionId,
    columns: Vector[(String, String)]
  ): Consequence[Unit] =
    _table_exists(conn, collection).flatMap { exists =>
      if (exists)
        _ensure_columns(conn, collection, columns)
      else
        _create_table(conn, collection, columns)
    }

  private def _create_table(
    conn: Connection,
    collection: CollectionId,
    columns: Vector[(String, String)]
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.create_table_sql(_table_name(collection), columns.map(_._1))
      val stmt = conn.createStatement()
      try {
        stmt.execute(sql)
      } finally {
        stmt.close()
      }
    }

  private def _ensure_columns(
    conn: Connection,
    collection: CollectionId,
    columns: Vector[(String, String)]
  ): Consequence[Unit] =
    _existing_columns(conn, collection).flatMap { existing =>
      val missing = columns.map(_._1).filterNot(existing.contains)
      missing.foldLeft(Consequence.unit) { (z, col) =>
        z.flatMap(_ => _add_column(conn, collection, col))
      }
    }

  private def _add_column(
    conn: Connection,
    collection: CollectionId,
    column: String
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.add_column_sql(_table_name(collection), column)
      val stmt = conn.createStatement()
      try {
        stmt.execute(sql)
      } finally {
        stmt.close()
      }
    }

  private def _exists(
    conn: Connection,
    collection: CollectionId,
    id: EntryId
  ): Consequence[Boolean] =
    Consequence {
      val sql = dialect.select_by_id_sql(_table_name(collection))
      val stmt = conn.prepareStatement(sql)
      try {
        stmt.setString(1, id.print)
        val rs = stmt.executeQuery()
        try rs.next()
        finally rs.close()
      } finally {
        stmt.close()
      }
    }

  private def _insert(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    columns: Vector[(String, String)]
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.insert_sql(_table_name(collection), columns.map(_._1))
      val stmt = conn.prepareStatement(sql)
      try {
        stmt.setString(1, id.print)
        columns.zipWithIndex.foreach { case ((_, v), i) =>
          stmt.setString(i + 2, v)
        }
        stmt.executeUpdate()
      } finally {
        stmt.close()
      }
    }

  private def _update(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    columns: Vector[(String, String)]
  ): Consequence[Unit] =
    if (columns.isEmpty)
      Consequence.unit
    else
      Consequence {
        val sql = dialect.update_sql(_table_name(collection), columns.map(_._1))
        val stmt = conn.prepareStatement(sql)
        try {
          columns.zipWithIndex.foreach { case ((_, v), i) =>
            stmt.setString(i + 1, v)
          }
          stmt.setString(columns.length + 1, id.print)
          stmt.executeUpdate()
        } finally {
          stmt.close()
        }
      }

  private def _delete(
    conn: Connection,
    collection: CollectionId,
    id: EntryId
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.delete_sql(_table_name(collection))
      val stmt = conn.prepareStatement(sql)
      try {
        stmt.setString(1, id.print)
        stmt.executeUpdate()
      } finally {
        stmt.close()
      }
    }

  private def _select(
    conn: Connection,
    collection: CollectionId,
    id: EntryId
  ): Consequence[Option[Record]] =
    Consequence {
      val sql = dialect.select_by_id_sql(_table_name(collection))
      val stmt = conn.prepareStatement(sql)
      try {
        stmt.setString(1, id.print)
        val rs = stmt.executeQuery()
        try {
          if (rs.next()) {
            val md = rs.getMetaData
            val count = md.getColumnCount
            val values = (1 to count).toVector.map { i =>
              val name = md.getColumnLabel(i)
              val value = Option(rs.getString(i)).getOrElse("")
              name -> value
            }
            Some(Record.create(values))
          } else {
            None
          }
        } finally {
          rs.close()
        }
      } finally {
        stmt.close()
      }
    }
}

object SqlDataStore {
  def sqlite(
    path: String,
    recorder: CommitRecorder = CommitRecorder.noop
  ): SqlDataStore = {
    val config = new HikariConfig()
    val jdbcurl =
      if (path == ":memory:")
        "jdbc:sqlite::memory:"
      else
        s"jdbc:sqlite:$path"
    config.setJdbcUrl(jdbcurl)
    config.setDriverClassName("org.sqlite.JDBC")
    config.setMaximumPoolSize(4)
    val datasource = new HikariDataSource(config)
    new SqlDataStore(SqliteDialectDriver, datasource, recorder)
  }
}
