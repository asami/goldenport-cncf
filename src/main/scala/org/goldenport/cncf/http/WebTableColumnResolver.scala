package org.goldenport.cncf.http

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.schema.Schema
import org.goldenport.util.StringUtils

/*
 * Resolves table presentation columns from CML/generated schema metadata.
 *
 * @since   Apr. 16, 2026
 * @version Apr. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object WebTableColumnResolver {
  val defaultViewName: String = "summary"

  def resolveEntity(
    component: Component,
    entityName: String,
    viewName: String = defaultViewName
  ): Vector[StaticFormAppRenderer.TableColumn] = {
    val schemaFields = _entity_schema(component, entityName).map(WebSchemaResolver.fromSchema).getOrElse(Vector.empty)
    val byName = schemaFields.map(x => x.name -> x).toMap
    val names = _view_field_names(component, entityName, viewName)
      .filter(_.nonEmpty)
      .getOrElse(_default_field_names(schemaFields.map(_.name)))
    names.flatMap { name =>
      val schemaName = byName.keys.find(k => NamingConventions.equivalentByNormalized(k, name)).getOrElse(name)
      val jsonName = StringUtils.camelToSnake(schemaName)
      val label = byName.get(schemaName).flatMap(_.label).getOrElse(_default_label(schemaName))
      Some(StaticFormAppRenderer.TableColumn(jsonName, label))
    }
  }

  private def _entity_schema(
    component: Component,
    entityName: String
  ): Option[Schema] =
    component.entityRuntimeDescriptor(entityName).flatMap(_.schema)

  private def _view_field_names(
    component: Component,
    entityName: String,
    viewName: String
  ): Option[Vector[String]] =
    component.viewDefinitions
      .find(d =>
        NamingConventions.equivalentByNormalized(d.entityName, entityName) ||
          NamingConventions.equivalentByNormalized(d.name, entityName)
      )
      .flatMap(_.fieldsFor(viewName))

  private def _default_field_names(
    fields: Vector[String]
  ): Vector[String] =
    fields.filterNot(_is_platform_field)

  private def _is_platform_field(name: String): Boolean =
    Set(
      "id",
      "nameAttributes",
      "descriptiveAttributes",
      "lifecycleAttributes",
      "publicationAttributes",
      "securityAttributes",
      "resourceAttributes",
      "auditAttributes",
      "mediaAttributes",
      "contextualAttribute"
    ).contains(name)

  private def _default_label(
    name: String
  ): String = {
    val spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ')
    spaced.split("\\s+").toVector.filter(_.nonEmpty).map { token =>
      token.head.toUpper + token.drop(1)
    }.mkString(" ")
  }
}
