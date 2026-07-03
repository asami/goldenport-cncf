package org.goldenport.cncf.embedded

import java.nio.file.Path
import java.sql.{Connection, DriverManager, PreparedStatement}
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.record.Record

/*
 * Component-local embedded datastore support.
 *
 * The public CNCF internal DSL should talk in terms of a user's local
 * embedded datastore, not a concrete database product. The first backend is
 * SQLite because it is small and file based, but component code should depend
 * on EmbeddedDataStore and EmbeddedStatement only.
 *
 * @since   Jul.  3, 2026
 * @version Jul.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EmbeddedDataStore(
  componentName: String,
  name: String,
  path: Path,
  backend: EmbeddedDataStoreBackend = EmbeddedDataStoreBackend.Sqlite
)

enum EmbeddedDataStoreBackend {
  case Sqlite
}

final case class EmbeddedStatement(
  statement: String,
  params: Vector[Any] = Vector.empty
)

final case class EmbeddedUpdateResult(
  affectedRows: Int
)

object EmbeddedDataStoreRunner {
  def read(
    store: EmbeddedDataStore,
    statement: EmbeddedStatement
  ): Consequence[Vector[Record]] =
    _backend(store).read(store, statement)

  def update(
    store: EmbeddedDataStore,
    statement: EmbeddedStatement
  ): Consequence[EmbeddedUpdateResult] =
    _backend(store).update(store, statement)

  def migrate(
    store: EmbeddedDataStore,
    statements: Vector[String]
  ): Consequence[Unit] =
    _backend(store).migrate(store, statements)

  private def _backend(
    store: EmbeddedDataStore
  ): EmbeddedDataStoreBackendRunner =
    store.backend match {
      case EmbeddedDataStoreBackend.Sqlite => SqliteEmbeddedDataStoreBackend
    }
}

private trait EmbeddedDataStoreBackendRunner {
  def read(store: EmbeddedDataStore, statement: EmbeddedStatement): Consequence[Vector[Record]]
  def update(store: EmbeddedDataStore, statement: EmbeddedStatement): Consequence[EmbeddedUpdateResult]
  def migrate(store: EmbeddedDataStore, statements: Vector[String]): Consequence[Unit]
}

private object SqliteEmbeddedDataStoreBackend extends EmbeddedDataStoreBackendRunner {
  def read(
    store: EmbeddedDataStore,
    statement: EmbeddedStatement
  ): Consequence[Vector[Record]] =
    _with_connection(store) { conn =>
      val ps = conn.prepareStatement(statement.statement)
      try {
        _bind(ps, statement.params)
        val rs = ps.executeQuery()
        try {
          val metadata = rs.getMetaData
          val count = metadata.getColumnCount
          val builder = Vector.newBuilder[Record]
          while (rs.next()) {
            val values = (1 to count).map { i =>
              val label = Option(metadata.getColumnLabel(i)).filter(_.nonEmpty).getOrElse(metadata.getColumnName(i))
              label -> rs.getObject(i)
            }
            builder += Record.dataAuto(values*)
          }
          builder.result()
        } finally {
          rs.close()
        }
      } finally {
        ps.close()
      }
    }

  def update(
    store: EmbeddedDataStore,
    statement: EmbeddedStatement
  ): Consequence[EmbeddedUpdateResult] =
    _with_connection(store) { conn =>
      val ps = conn.prepareStatement(statement.statement)
      try {
        _bind(ps, statement.params)
        EmbeddedUpdateResult(ps.executeUpdate())
      } finally {
        ps.close()
      }
    }

  def migrate(
    store: EmbeddedDataStore,
    statements: Vector[String]
  ): Consequence[Unit] =
    _with_connection(store) { conn =>
      val old = conn.getAutoCommit
      conn.setAutoCommit(false)
      try {
        statements.foreach { sql =>
          val stmt = conn.createStatement()
          try {
            stmt.execute(sql)
          } finally {
            stmt.close()
          }
        }
        conn.commit()
        ()
      } catch {
        case e: Throwable =>
          conn.rollback()
          throw e
      } finally {
        conn.setAutoCommit(old)
      }
    }

  private def _with_connection[A](
    store: EmbeddedDataStore
  )(
    body: Connection => A
  ): Consequence[A] =
    try {
      val parent = store.path.toAbsolutePath.normalize.getParent
      if (parent != null)
        java.nio.file.Files.createDirectories(parent)
      val conn = DriverManager.getConnection(_jdbc_url(store))
      try {
        Consequence.success(body(conn))
      } finally {
        conn.close()
      }
    } catch {
      case e: Throwable => Consequence.Failure(Conclusion.from(e))
    }

  private def _jdbc_url(
    store: EmbeddedDataStore
  ): String =
    s"jdbc:sqlite:${store.path.toString}"

  private def _bind(
    ps: PreparedStatement,
    params: Vector[Any]
  ): Unit =
    params.zipWithIndex.foreach { case (value, index) =>
      ps.setObject(index + 1, value)
    }
}
