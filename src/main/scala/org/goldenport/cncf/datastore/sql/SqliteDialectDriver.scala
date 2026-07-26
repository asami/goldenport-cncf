package org.goldenport.cncf.datastore.sql

/*
 * @since   Mar. 12, 2026
 *  version Apr.  3, 2026
 *  version Jul. 15, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
object SqliteDialectDriver extends SqlDialectDriver {
  val name = "sqlite"

  def quoteIdentifier(identifier: String): String =
    "\"" + identifier.replace("\"", "\"\"") + "\""

  def tableExistsSql(table: String): String =
    s"SELECT name FROM sqlite_master WHERE type='table' AND name='${_escape_literal(table)}'"

  def tableColumnsSql(table: String): String =
    s"PRAGMA table_info(${quoteIdentifier(table)})"

  def tableColumnsNameColumn: String = "name"

  def createTableSql(
    table: String,
    columns: Vector[(String, Any)]
  ): String = {
    val cols = _columns_with_id(columns)
    s"CREATE TABLE IF NOT EXISTS ${quoteIdentifier(table)} (${cols.mkString(", ")})"
  }

  def addColumnSql(
    table: String,
    column: (String, Any)
  ): String =
    s"ALTER TABLE ${quoteIdentifier(table)} ADD COLUMN ${_column_def(column)}"

  def insertSql(
    table: String,
    columns: Vector[String]
  ): String = {
    val allcols = "id" +: columns
    val names = allcols.map(quoteIdentifier).mkString(", ")
    val values = List.fill(allcols.length)("?").mkString(", ")
    s"INSERT INTO ${quoteIdentifier(table)} ($names) VALUES ($values)"
  }

  def upsertSql(
    table: String,
    columns: Vector[String]
  ): String = {
    val insert = insertSql(table, columns)
    if (columns.isEmpty)
      s"$insert ON CONFLICT (${quoteIdentifier("id")}) DO NOTHING"
    else {
      val assignments = columns.map { column =>
        s"${quoteIdentifier(column)} = excluded.${quoteIdentifier(column)}"
      }.mkString(", ")
      s"$insert ON CONFLICT (${quoteIdentifier("id")}) DO UPDATE SET $assignments"
    }
  }

  def updateSql(
    table: String,
    columns: Vector[String]
  ): String = {
    val setcols = columns.map(c => s"${quoteIdentifier(c)} = ?").mkString(", ")
    s"UPDATE ${quoteIdentifier(table)} SET $setcols WHERE ${quoteIdentifier("id")} = ?"
  }

  def entityRevisionGuardSql(column: String): String = {
    val identifier = quoteIdentifier(column)
    s"typeof($identifier) = 'integer' AND $identifier >= ? AND $identifier < ?"
  }

  def absentValueSql(column: String): String = {
    val identifier = quoteIdentifier(column)
    s"($identifier IS NULL OR trim(CAST($identifier AS TEXT)) = '')"
  }

  def deleteSql(table: String): String =
    s"DELETE FROM ${quoteIdentifier(table)} WHERE ${quoteIdentifier("id")} = ?"

  def selectByIdSql(table: String): String =
    s"SELECT * FROM ${quoteIdentifier(table)} WHERE ${quoteIdentifier("id")} = ?"

  private def _columns_with_id(columns: Vector[(String, Any)]): Vector[String] =
    Vector(_column_def("id", "TEXT PRIMARY KEY")) ++ columns.map(_column_def(_))

  private def _column_def(column: (String, Any)): String =
    _column_def(column._1, _datatype(column._2))

  private def _column_def(
    column: String,
    datatype: String
  ): String =
    s"${quoteIdentifier(column)} $datatype"

  private def _datatype(value: Any): String =
    value match {
      case _: Byte | _: Short | _: Int | _: Long | _: Boolean => "INTEGER"
      case _: Float | _: Double | _: BigInt | _: BigDecimal => "REAL"
      case _ => "TEXT"
    }

  private def _escape_literal(value: String): String =
    value.replace("'", "''")
}
