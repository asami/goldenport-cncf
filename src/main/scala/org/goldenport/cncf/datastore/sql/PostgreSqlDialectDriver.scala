package org.goldenport.cncf.datastore.sql

/*
 * @since   Oct. 14, 2026
 *  version Oct. 14, 2026
 * @version Oct. 19, 2026
 */
object PostgreSqlDialectDriver extends SqlDialectDriver {
  val name = "postgresql"

  def quoteIdentifier(identifier: String): String =
    "\"" + identifier.replace("\"", "\"\"") + "\""

  private[sql] def _literal(value: String): String =
    "E'" + value.replace("\\", "\\\\").replace("'", "''") + "'"

  def tableExistsSql(table: String): String =
    s"SELECT oid FROM pg_catalog.pg_class WHERE oid = pg_catalog.to_regclass(${_literal(quoteIdentifier(table))}) AND relkind = 'r'"

  def tableColumnsSql(table: String): String =
    s"SELECT attname FROM pg_catalog.pg_attribute WHERE attrelid = pg_catalog.to_regclass(${_literal(quoteIdentifier(table))}) AND attnum > 0 AND NOT attisdropped ORDER BY attnum"

  def tableColumnsNameColumn: String = "attname"

  def createTableSql(table: String, columns: Vector[(String, Any)]): String =
    _create_table_at_sql(quoteIdentifier(table), columns)

  private[sql] def _create_table_at_sql(tablesql: String, columns: Vector[(String, Any)]): String = {
    val definitions = s"${quoteIdentifier("id")} TEXT PRIMARY KEY" +: columns.map(_column_def)
    s"CREATE TABLE IF NOT EXISTS $tablesql (${definitions.mkString(", ")})"
  }

  def addColumnSql(table: String, column: (String, Any)): String =
    _add_column_at_sql(quoteIdentifier(table), column)

  private[sql] def _add_column_at_sql(tablesql: String, column: (String, Any)): String =
    s"ALTER TABLE $tablesql ADD COLUMN IF NOT EXISTS ${_column_def(column)}"

  def insertSql(table: String, columns: Vector[String]): String =
    _insert_at_sql(quoteIdentifier(table), columns)

  private[sql] def _insert_at_sql(tablesql: String, columns: Vector[String]): String = {
    val names = "id" +: columns
    s"INSERT INTO $tablesql (${names.map(quoteIdentifier).mkString(", ")}) VALUES (${Vector.fill(names.size)("?").mkString(", ")})"
  }

  private[sql] def _strict_insert_at_sql(tablesql: String, columns: Vector[String], primarykey: String): String =
    s"${_insert_at_sql(tablesql, columns)} ON CONFLICT ON CONSTRAINT ${quoteIdentifier(primarykey)} DO NOTHING"

  def upsertSql(table: String, columns: Vector[String]): String =
    _upsert_at_sql(quoteIdentifier(table), columns)

  private[sql] def _upsert_at_sql(tablesql: String, columns: Vector[String], primarykey: Option[String] = None): String = {
    val insert = _insert_at_sql(tablesql, columns)
    val arbiter = primarykey.map(name => s"ON CONSTRAINT ${quoteIdentifier(name)}").getOrElse(s"(${quoteIdentifier("id")})")
    if (columns.isEmpty) s"$insert ON CONFLICT $arbiter DO NOTHING"
    else {
      val assignments = columns.map(column => s"${quoteIdentifier(column)} = EXCLUDED.${quoteIdentifier(column)}").mkString(", ")
      s"$insert ON CONFLICT $arbiter DO UPDATE SET $assignments"
    }
  }

  def updateSql(table: String, columns: Vector[String]): String =
    _update_at_sql(quoteIdentifier(table), columns)

  private[sql] def _update_at_sql(tablesql: String, columns: Vector[String]): String =
    s"UPDATE $tablesql SET ${columns.map(column => s"${quoteIdentifier(column)} = ?").mkString(", ")} WHERE ${quoteIdentifier("id")} = ?"

  private[sql] def _revision_value_sql(column: String): String = {
    val identifier = quoteIdentifier(column)
    s"CASE WHEN CAST($identifier AS TEXT) ~ '^[1-9][0-9]*$$' THEN CAST($identifier AS NUMERIC) ELSE NULL END"
  }

  def entityRevisionGuardSql(column: String): String = {
    val revision = _revision_value_sql(column)
    s"($revision) >= ? AND ($revision) < ?"
  }

  def absentValueSql(column: String): String = {
    val identifier = quoteIdentifier(column)
    s"($identifier IS NULL OR TRIM(CAST($identifier AS TEXT)) = '')"
  }

  def deleteSql(table: String): String = _delete_at_sql(quoteIdentifier(table))

  private[sql] def _delete_at_sql(tablesql: String): String =
    s"DELETE FROM $tablesql WHERE ${quoteIdentifier("id")} = ?"

  def selectByIdSql(table: String): String = _select_by_id_at_sql(quoteIdentifier(table))

  private[sql] def _select_by_id_at_sql(tablesql: String): String =
    s"SELECT * FROM $tablesql WHERE ${quoteIdentifier("id")} = ?"

  private def _column_def(column: (String, Any)): String = {
    val datatype = column._2 match {
      case _: Short => "SMALLINT"
      case _: Int => "INTEGER"
      case _: Byte | _: Long | _: Boolean => "BIGINT"
      case _: Float | _: Double | _: BigInt | _: BigDecimal => "DOUBLE PRECISION"
      case _ => "TEXT"
    }
    s"${quoteIdentifier(column._1)} $datatype"
  }
}
