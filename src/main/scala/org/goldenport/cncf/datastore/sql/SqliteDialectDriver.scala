package org.goldenport.cncf.datastore.sql

/*
 * @since   Mar. 12, 2026
 * @version Apr.  3, 2026
 * @author  ASAMI, Tomoharu
 */
object SqliteDialectDriver extends SqlDialectDriver {
  val name = "sqlite"

  def quote_identifier(identifier: String): String =
    "\"" + identifier.replace("\"", "\"\"") + "\""

  def table_exists_sql(table: String): String =
    s"SELECT name FROM sqlite_master WHERE type='table' AND name='${_escape_literal(table)}'"

  def table_columns_sql(table: String): String =
    s"PRAGMA table_info(${quote_identifier(table)})"

  def table_columns_name_column: String = "name"

  def create_table_sql(
    table: String,
    columns: Vector[(String, Any)]
  ): String = {
    val cols = _columns_with_id(columns)
    s"CREATE TABLE IF NOT EXISTS ${quote_identifier(table)} (${cols.mkString(", ")})"
  }

  def add_column_sql(
    table: String,
    column: (String, Any)
  ): String =
    s"ALTER TABLE ${quote_identifier(table)} ADD COLUMN ${_column_def(column)}"

  def insert_sql(
    table: String,
    columns: Vector[String]
  ): String = {
    val allcols = "id" +: columns
    val names = allcols.map(quote_identifier).mkString(", ")
    val values = List.fill(allcols.length)("?").mkString(", ")
    s"INSERT INTO ${quote_identifier(table)} ($names) VALUES ($values)"
  }

  def update_sql(
    table: String,
    columns: Vector[String]
  ): String = {
    val setcols = columns.map(c => s"${quote_identifier(c)} = ?").mkString(", ")
    s"UPDATE ${quote_identifier(table)} SET $setcols WHERE ${quote_identifier("id")} = ?"
  }

  def delete_sql(table: String): String =
    s"DELETE FROM ${quote_identifier(table)} WHERE ${quote_identifier("id")} = ?"

  def select_by_id_sql(table: String): String =
    s"SELECT * FROM ${quote_identifier(table)} WHERE ${quote_identifier("id")} = ?"

  private def _columns_with_id(columns: Vector[(String, Any)]): Vector[String] =
    Vector(_column_def("id", "TEXT PRIMARY KEY")) ++ columns.map(_column_def(_))

  private def _column_def(column: (String, Any)): String =
    _column_def(column._1, _datatype(column._2))

  private def _column_def(
    column: String,
    datatype: String
  ): String =
    s"${quote_identifier(column)} $datatype"

  private def _datatype(value: Any): String =
    value match {
      case _: Byte | _: Short | _: Int | _: Long | _: Boolean => "INTEGER"
      case _: Float | _: Double | _: BigInt | _: BigDecimal => "REAL"
      case _ => "TEXT"
    }

  private def _escape_literal(value: String): String =
    value.replace("'", "''")
}
