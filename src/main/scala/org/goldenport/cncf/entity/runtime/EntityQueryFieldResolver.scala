package org.goldenport.cncf.entity.runtime

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.naming.NamingConventions

/*
 * Resolves entity query field names from EntityRuntimeDescriptor schema and
 * view metadata. Query expressions should use the CML/schema logical name; each
 * DataStore may translate it to its physical representation.
 *
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityQueryFieldResolver(
  component: Component,
  entityName: String
) {
  private lazy val _schema_field_names: Vector[String] =
    component.entityRuntimeDescriptor(entityName).flatMap(_.schema).map(_.columns.map(_.name.value).toVector).getOrElse(Vector.empty)

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
    val source = if (fields.nonEmpty) fields else _schema_field_names
    source.filterNot(_is_platform_field)
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
