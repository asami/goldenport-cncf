package org.goldenport.cncf.datastore.sql

import java.sql.Connection
import javax.sql.DataSource
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.goldenport.Consequence
import org.goldenport.convert.StringEncodable
import org.goldenport.text.Presentable
import org.goldenport.record.Record
import org.goldenport.record.Recordable
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}

/*
 * @since   Mar. 12, 2026
 *  version Mar. 19, 2026
 *  version Mar. 31, 2026
 * @version Apr.  3, 2026
 * @author  ASAMI, Tomoharu
 */
class SqlDataStore(
  dialect: SqlDialectDriver,
  datasource: DataSource,
  recorder: CommitRecorder = CommitRecorder.noop,
  config: SqlDataStore.Config = SqlDataStore.Config()
) extends DataStore {
  import DataStore.*
  private val _record_decoder = new RecordDecoder()

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
      case (k, v) if k != "id" =>
        val key = if (config.normalizeColumnNames) _to_column_name(k) else k
        key -> _column_value(v)
    }
      .foldLeft(Vector.empty[(String, String)]) { case (z, (k, v)) =>
        z.indexWhere(_._1 == k) match {
          case -1 => z :+ (k -> v)
          case i => z.updated(i, k -> v)
        }
      }

  private def _column_value(
    value: Any
  ): String =
    value match {
      case m: StringEncodable =>
        given org.goldenport.context.ExecutionContext = org.goldenport.convert.StringEncoder.storageExecutionContext
        m.encode
      case m: Record =>
        m.toJsonString
      case m: Recordable =>
        m.toRecord().toJsonString
      case xs: Iterable[?] if xs.forall(_is_record_like) =>
        xs.iterator.map(_record_like_json).mkString("[", ",", "]")
      case other =>
        Presentable.print(other)
    }

  private def _is_record_like(
    value: Any
  ): Boolean =
    value match {
      case _: Record => true
      case _: Recordable => true
      case _ => false
    }

  private def _record_like_json(
    value: Any
  ): String =
    value match {
      case m: Record => m.toJsonString
      case m: Recordable => m.toRecord().toJsonString
      case other => Presentable.print(other)
    }

  private def _decode_column_value(
    value: String
  ): Any = {
    val trimmed = value.trim
    if (trimmed.startsWith("{") && trimmed.endsWith("}"))
      _record_decoder.json(trimmed).toOption.getOrElse(value)
    else if (trimmed.startsWith("[") && trimmed.endsWith("]"))
      _record_decoder.jsonAutoRecords(trimmed).toOption.getOrElse(value)
    else
      value
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
              val rawname = md.getColumnLabel(i)
              val name = if (config.normalizeColumnNames) _to_property_name(rawname) else rawname
              val value = _decode_column_value(Option(rs.getString(i)).getOrElse(""))
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

  private def _to_column_name(name: String): String = {
    val b = new StringBuilder(name.length + 8)
    var i = 0
    var prevUnderscore = false
    while (i < name.length) {
      val c = name.charAt(i)
      if (c == '-' || c == '.' || c == ' ') {
        if (!prevUnderscore && b.nonEmpty) {
          b.append('_')
          prevUnderscore = true
        }
      } else if (c.isUpper) {
        if (b.nonEmpty && !prevUnderscore)
          b.append('_')
        b.append(c.toLower)
        prevUnderscore = false
      } else if (c == '_') {
        if (!prevUnderscore && b.nonEmpty) {
          b.append('_')
          prevUnderscore = true
        }
      } else {
        b.append(c.toLower)
        prevUnderscore = false
      }
      i += 1
    }
    val raw = b.result()
    raw.dropWhile(_ == '_').reverse.dropWhile(_ == '_').reverse
  }

  private def _to_property_name(name: String): String = {
    val lower = name.toLowerCase(java.util.Locale.ROOT)
    if (!name.contains("_"))
      name
    else {
      val b = new StringBuilder(lower.length)
      var upper = false
      var i = 0
      while (i < lower.length) {
        val c = lower.charAt(i)
        if (c == '_') {
          upper = true
        } else if (upper) {
          b.append(c.toUpper)
          upper = false
        } else {
          b.append(c)
        }
        i += 1
      }
      b.result()
    }
  }
}

object SqlDataStore {
  final case class Config(
    normalizeColumnNames: Boolean = false
  )

  def sqlite(
    path: String,
    recorder: CommitRecorder = CommitRecorder.noop,
    config: Config = Config()
  ): SqlDataStore = {
    val hikariconfig = new HikariConfig()
    val jdbcurl =
      if (path == ":memory:")
        "jdbc:sqlite::memory:"
      else
        s"jdbc:sqlite:$path"
    hikariconfig.setJdbcUrl(jdbcurl)
    hikariconfig.setDriverClassName("org.sqlite.JDBC")
    hikariconfig.setMaximumPoolSize(4)
    val datasource = new HikariDataSource(hikariconfig)
    new SqlDataStore(SqliteDialectDriver, datasource, recorder, config)
  }
}
