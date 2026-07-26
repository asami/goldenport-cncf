package org.goldenport.cncf.datastore.sql

/*
 * @since   Jul.  6, 2026
 *  version Jul. 15, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
object MySqlDialectDriver extends SqlDialectDriver {
  val name = "mysql"

  def quoteIdentifier(identifier: String): String =
    "`" + identifier.replace("`", "``") + "`"

  def tableExistsSql(table: String): String =
    s"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '${_escape_literal(table)}'"

  def tableColumnsSql(table: String): String =
    s"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '${_escape_literal(table)}'"

  def tableColumnsNameColumn: String = "COLUMN_NAME"

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
      s"$insert ON DUPLICATE KEY UPDATE ${quoteIdentifier("id")} = ${quoteIdentifier("id")}"
    else {
      val assignments = columns.map { column =>
        s"${quoteIdentifier(column)} = VALUES(${quoteIdentifier(column)})"
      }.mkString(", ")
      s"$insert ON DUPLICATE KEY UPDATE $assignments"
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
    s"CAST($identifier AS CHAR) REGEXP '^[1-9][0-9]*$$' AND $identifier >= ? AND $identifier < ?"
  }

  def absentValueSql(column: String): String = {
    val identifier = quoteIdentifier(column)
    s"($identifier IS NULL OR TRIM(CAST($identifier AS CHAR)) = '')"
  }

  def deleteSql(table: String): String =
    s"DELETE FROM ${quoteIdentifier(table)} WHERE ${quoteIdentifier("id")} = ?"

  def selectByIdSql(table: String): String =
    s"SELECT * FROM ${quoteIdentifier(table)} WHERE ${quoteIdentifier("id")} = ?"

  private def _columns_with_id(columns: Vector[(String, Any)]): Vector[String] =
    Vector(_column_def("id", "VARCHAR(255) PRIMARY KEY")) ++ columns.map(_column_def(_))

  private def _column_def(column: (String, Any)): String =
    _column_def(column._1, _datatype(column._2))

  private def _column_def(
    column: String,
    datatype: String
  ): String =
    s"${quoteIdentifier(column)} $datatype"

  private def _datatype(value: Any): String =
    value match {
      case _: Byte | _: Short | _: Int | _: Long | _: Boolean => "BIGINT"
      case _: Float | _: Double | _: BigInt | _: BigDecimal => "DOUBLE"
      case _ => "TEXT"
    }

  private def _escape_literal(value: String): String =
    value.replace("'", "''")
}
