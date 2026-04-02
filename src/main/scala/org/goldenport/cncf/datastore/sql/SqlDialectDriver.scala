package org.goldenport.cncf.datastore.sql

/*
 * @since   Mar. 12, 2026
 * @version Apr.  3, 2026
 * @author  ASAMI, Tomoharu
 */
trait SqlDialectDriver {
  def name: String

  def quote_identifier(identifier: String): String

  def table_exists_sql(table: String): String
  def table_columns_sql(table: String): String
  def table_columns_name_column: String

  def create_table_sql(
    table: String,
    columns: Vector[(String, Any)]
  ): String

  def add_column_sql(
    table: String,
    column: (String, Any)
  ): String

  def insert_sql(
    table: String,
    columns: Vector[String]
  ): String

  def update_sql(
    table: String,
    columns: Vector[String]
  ): String

  def delete_sql(table: String): String

  def select_by_id_sql(table: String): String
}
