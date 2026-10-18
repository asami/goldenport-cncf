package org.goldenport.cncf.job

import org.goldenport.cncf.entity.{EntityStoreField, EntityStoreFieldBinding, EntityStoreRowMapping, SimpleEntityStorageShapePolicy}
import org.goldenport.schema.{Multiplicity, ValueDomain, XString}

/*
 * Closed physical text declarations for the durable Job and quota stores.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
private[job] object DurableJobStoreMapping {
  def text(fields: Vector[String]): EntityStoreRowMapping = EntityStoreRowMapping(
    fields.map(name => EntityStoreField(name, name, ValueDomain(XString, Multiplicity.One), EntityStoreFieldBinding.string)),
    (SimpleEntityStorageShapePolicy.managementLogicalFields ++
      SimpleEntityStorageShapePolicy.securityIdentityLogicalFields ++ Vector("name", "permission", "revision"))
      .flatMap(name => Vector(name, SimpleEntityStorageShapePolicy.targetName(name))).toSet -- fields.toSet)
}
