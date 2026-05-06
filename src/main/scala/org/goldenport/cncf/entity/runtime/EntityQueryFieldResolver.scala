package org.goldenport.cncf.entity.runtime

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.schema.{DataType, XClob, XString}

/*
 * Resolves entity query field names from EntityRuntimeDescriptor schema and
 * view metadata. Query expressions should use the CML/schema logical name; each
 * DataStore may translate it to its physical representation.
 *
 * @since   Apr. 18, 2026
 * @version May.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityQueryFieldResolver(
  component: Component,
  entityName: String
) {
  private lazy val _schema_field_names: Vector[String] =
    component.entityRuntimeDescriptor(entityName).flatMap(_.schema).map(_.columns.map(_.name.value).toVector).getOrElse(Vector.empty)

  private lazy val _schema_text_field_names: Vector[String] =
    component.entityRuntimeDescriptor(entityName).flatMap(_.schema).map { schema =>
      schema.columns.collect {
        case column if _is_text_like(column.domain.datatype) => column.name.value
      }.toVector
    }.getOrElse(Vector.empty)

  def resolve(name: String): String =
    _schema_field_names.find(NamingConventions.equivalentByNormalized(_, name)).getOrElse(name)

  def resolveAll(names: Iterable[String]): Vector[String] =
    names.toVector.map(resolve).distinct

  def rewrite(query: Query[?]): Query[?] =
    Query.mapPaths(query)(resolve)

  def viewFields(viewName: String): Vector[String] =
    component.viewDefinitions
      .find(d =>
        NamingConventions.equivalentByNormalized(d.entityName, entityName) ||
          NamingConventions.equivalentByNormalized(d.name, entityName)
      )
      .flatMap(_.fieldsFor(viewName))
      .map(resolveAll)
      .getOrElse(Vector.empty)

  def defaultSearchFields(viewName: String = "summary"): Vector[String] = {
    val fields = viewFields(viewName)
    val source =
      if (fields.nonEmpty)
        fields.filter(name => _schema_text_field_names.isEmpty || _schema_text_field_names.exists(NamingConventions.equivalentByNormalized(_, name)))
      else
        _schema_text_field_names
    source.filterNot(_is_platform_field)
  }

  def filterFields(viewName: String = "summary"): Vector[String] =
    viewFields(viewName).filterNot(_is_platform_field) match {
      case xs if xs.nonEmpty => xs
      case _ => _schema_field_names.filterNot(_is_platform_field)
    }

  def sortableFields(viewName: String = "summary"): Vector[String] =
    filterFields(viewName)

  private def _is_text_like(datatype: DataType): Boolean =
    datatype match {
      case XString | XClob => true
      case other =>
        val name = other.name.toLowerCase(java.util.Locale.ROOT)
        name.contains("string") || name.contains("text") || name.contains("name") || name.contains("title") || name.contains("summary") || name.contains("description")
    }

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
}

object EntityQueryFieldResolver {
  def apply(
    component: Option[Component],
    entityName: String
  ): EntityQueryFieldResolver =
    EntityQueryFieldResolver(component.getOrElse(throw new IllegalStateException("Component context is required.")), entityName)
}
