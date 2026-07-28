package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.record.{Field, Record}

/*
 * @since   Jul. 28, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityStoreValueKind {
  case ScalarString
}

final case class EntityStoreAttribute(
  logicalName: String,
  storageName: String,
  valueKind: EntityStoreValueKind
)

object EntityStoreAttribute {
  def scalarString(
    logicalName: String,
    storageName: String
  ): EntityStoreAttribute =
    EntityStoreAttribute(
      logicalName = logicalName,
      storageName = storageName,
      valueKind = EntityStoreValueKind.ScalarString
    )
}

object EntityStoreRecordProjection {
  def project(
    record: Record,
    attributes: Vector[EntityStoreAttribute]
  ): Consequence[Record] =
    attributes.foldLeft(Consequence.success(record)) { (z, attribute) =>
      z.flatMap(_project_attribute(_, attribute))
    }

  private def _project_attribute(
    record: Record,
    attribute: EntityStoreAttribute
  ): Consequence[Record] =
    record.fields
      .find(_.key == attribute.storageName)
      .orElse(record.fields.find(_.key == attribute.logicalName)) match {
      case Some(Field(_, Field.Value.Single(value))) =>
        attribute.valueKind match {
          case EntityStoreValueKind.ScalarString =>
            value match {
              case storedrecord: Record =>
                storedrecord.toJsonStringC.map(
                  record.upsertSingle(attribute.logicalName, _)
                )
              case other =>
                Consequence.success(
                  record.upsertSingle(attribute.logicalName, other)
                )
            }
        }
      case _ =>
        Consequence.success(record)
    }
}
