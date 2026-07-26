package org.goldenport.cncf.datastore.sql

/*
 * @since   Mar. 12, 2026
 *  version Apr.  3, 2026
 *  version Jul. 15, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
trait SqlDialectDriver {
  def name: String

  def quoteIdentifier(identifier: String): String

  def tableExistsSql(table: String): String
  def tableColumnsSql(table: String): String
  def tableColumnsNameColumn: String

  def createTableSql(
    table: String,
    columns: Vector[(String, Any)]
  ): String

  def addColumnSql(
    table: String,
    column: (String, Any)
  ): String

  def insertSql(
    table: String,
    columns: Vector[String]
  ): String

  def upsertSql(
    table: String,
    columns: Vector[String]
  ): String

  def updateSql(
    table: String,
    columns: Vector[String]
  ): String

  def entityRevisionGuardSql(column: String): String
  def absentValueSql(column: String): String

  def deleteSql(table: String): String

  def selectByIdSql(table: String): String
}
